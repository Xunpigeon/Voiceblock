package com.voiceblock.match;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.util.RegistryAccess;
import com.voiceblock.util.ResourceLocations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 方块去重扫描器：检测四类冲突，输出 Markdown 报告。
 * 仅报告不修改，决策由人做。
 */
public class DedupScanner {

    private static final float HIGH_SIM_THRESHOLD = 0.85f;

    public static class ConflictGroup {
        public final String type; // name_collision / same_item / same_pinyin / high_similarity
        public final BlockData.BlockEntry a;
        public final BlockData.BlockEntry b;
        public final float score;
        public final String detail;

        public ConflictGroup(String type, BlockData.BlockEntry a, BlockData.BlockEntry b, float score, String detail) {
            this.type = type;
            this.a = a;
            this.b = b;
            this.score = score;
            this.detail = detail;
        }
    }

    public static List<ConflictGroup> scan() {
        List<ConflictGroup> conflicts = new ArrayList<>();
        List<BlockData.BlockEntry> entries = BlockData.getInstance().getEntries();
        Set<String> reported = new HashSet<>();

        // 1. name/alias 完全撞名
        Map<String, List<BlockData.BlockEntry>> byName = new HashMap<>();
        for (BlockData.BlockEntry e : entries) {
            if (e.name() != null) {
                byName.computeIfAbsent(e.name().toLowerCase(), k -> new ArrayList<>()).add(e);
            }
            for (String alias : e.aliases()) {
                if (alias == null || alias.isBlank()) continue;
                byName.computeIfAbsent(alias.toLowerCase(), k -> new ArrayList<>()).add(e);
            }
        }
        for (var e : byName.entrySet()) {
            if (e.getValue().size() <= 1) continue;
            List<BlockData.BlockEntry> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    String key = pairKey(list.get(i), list.get(j));
                    if (reported.add(key)) {
                        conflicts.add(new ConflictGroup("name_collision", list.get(i), list.get(j), 1.0f, "撞名: " + e.getKey()));
                    }
                }
            }
        }

        // 2/3. 拼音高相似 / 同音
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            BlockData.BlockEntry a = entries.get(i);
            String aPinyin = a.name() == null ? "" : PinyinUtil.toPinyin(a.name());
            if (aPinyin.isEmpty()) continue;
            for (int j = i + 1; j < n; j++) {
                BlockData.BlockEntry b = entries.get(j);
                String bPinyin = b.name() == null ? "" : PinyinUtil.toPinyin(b.name());
                if (bPinyin.isEmpty()) continue;
                if (aPinyin.equals(bPinyin)) {
                    String key = pairKey(a, b);
                    if (reported.add(key)) {
                        conflicts.add(new ConflictGroup("same_pinyin", a, b, 1.0f, "同音: " + aPinyin));
                    }
                    continue;
                }
                float sim = PinyinUtil.similarity(aPinyin, bPinyin);
                if (sim >= HIGH_SIM_THRESHOLD) {
                    String key = pairKey(a, b);
                    if (reported.add(key)) {
                        conflicts.add(new ConflictGroup("high_similarity", a, b, sim, String.format("相似度 %.2f", sim)));
                    }
                }
            }
        }

        // 4. id 反查同 Item（两个不同 id 但实际是同一 Item）
        Map<Item, List<BlockData.BlockEntry>> byItem = new HashMap<>();
        for (BlockData.BlockEntry e : entries) {
            ResourceLocation rl = ResourceLocations.tryParse(e.id());
            if (rl == null) continue;
            try {
                Item item = RegistryAccess.getItem(rl);
                if (item == null) continue;
                byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(e);
            } catch (Exception ignored) {}
        }
        for (var e : byItem.entrySet()) {
            List<BlockData.BlockEntry> list = e.getValue();
            if (list.size() <= 1) continue;
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    String key = pairKey(list.get(i), list.get(j));
                    if (reported.add(key)) {
                        conflicts.add(new ConflictGroup("same_item", list.get(i), list.get(j), 1.0f, "同一物品"));
                    }
                }
            }
        }

        conflicts.sort((x, y) -> {
            int t = typeOrder(x.type) - typeOrder(y.type);
            if (t != 0) return t;
            return Float.compare(y.score, x.score);
        });
        return conflicts;
    }

    private static int typeOrder(String type) {
        return switch (type) {
            case "name_collision" -> 0;
            case "same_item" -> 1;
            case "same_pinyin" -> 2;
            case "high_similarity" -> 3;
            default -> 4;
        };
    }

    private static String pairKey(BlockData.BlockEntry a, BlockData.BlockEntry b) {
        return a.id().compareTo(b.id()) < 0 ? a.id() + "|" + b.id() : b.id() + "|" + a.id();
    }

    public static Path writeReport(List<ConflictGroup> conflicts) {
        Path file = Paths.get("config", "voiceblock", "duplicates_report.md");
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write("# VoiceBlock 方块去重扫描报告\n\n");
                w.write("总冲突数: " + conflicts.size() + "\n\n");
                w.write("> 此报告由 /voiceblock dedup 自动生成，仅报告不修改。请人工决定如何处理。\n\n");

                Map<String, List<ConflictGroup>> byType = new LinkedHashMap<>();
                byType.put("name_collision", new ArrayList<>());
                byType.put("same_item", new ArrayList<>());
                byType.put("same_pinyin", new ArrayList<>());
                byType.put("high_similarity", new ArrayList<>());
                for (ConflictGroup c : conflicts) byType.get(c.type).add(c);

                writeSection(w, "## 1. name/alias 完全撞名（必查）", byType.get("name_collision"));
                writeSection(w, "## 2. 同一 Item（id 反查命中）", byType.get("same_item"));
                writeSection(w, "## 3. 拼音完全一致（同音字）", byType.get("same_pinyin"));
                writeSection(w, "## 4. 拼音高相似（≥ 0.85）", byType.get("high_similarity"));
            }
            return file;
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to write dedup report", e);
            return null;
        }
    }

    private static void writeSection(BufferedWriter w, String title, List<ConflictGroup> list) throws IOException {
        w.write(title + "\n\n");
        if (list.isEmpty()) {
            w.write("（无）\n\n");
            return;
        }
        w.write("| name A | name B | 物品A | 物品B | 详情 |\n");
        w.write("|--------|--------|------|------|------|\n");
        for (ConflictGroup c : list) {
            w.write("| " + esc(c.a.name()) + " | " + esc(c.b.name()) + " | " + esc(c.a.id()) + " | " + esc(c.b.id()) + " | " + esc(c.detail) + " |\n");
        }
        w.write("\n");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
