package com.voiceblock.match;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.voiceblock.VoiceBlockMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 方块数据：包含所有 Minecraft 方块的中文名称、别名。
 * 数据文件：assets/voiceblock/blocks.json
 *
 * 1.3.0 起：自动发现扩展到 ITEM 注册表（覆盖仅物品、无对应方块的物品，如牛排、应用能源物品等）。
 * Block 优先（已收录的 id 跳过），无中文名跳过（避免英文物品名称污染词典）。
 */
public class BlockData {

    private static BlockData instance;

    public record BlockEntry(
            String id,              // minecraft:oak_stairs
            String name,            // 橡木楼梯
            List<String> aliases    // 别名：橡木台阶、楼梯
    ) {}

    private final List<BlockEntry> entries;

    private BlockData(List<BlockEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public static synchronized BlockData getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static BlockData load() {
        List<BlockEntry> list = new ArrayList<>();
        try (InputStream in = BlockData.class.getClassLoader()
                .getResourceAsStream("assets/voiceblock/blocks.json")) {
            if (in == null) {
                VoiceBlockMod.LOGGER.error("blocks.json not found in resources");
                return new BlockData(list);
            }
            Gson gson = new Gson();
            Type type = new TypeToken<List<BlockEntry>>() {}.getType();
            List<BlockEntry> loaded = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
            if (loaded != null) {
                for (BlockEntry entry : loaded) {
                    List<String> aliases = entry.aliases() != null ? entry.aliases() : Collections.emptyList();
                    list.add(new BlockEntry(entry.id(), entry.name(), aliases));
                }
            }
            VoiceBlockMod.LOGGER.info("Loaded {} block entries from blocks.json", list.size());
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to load blocks.json", e);
        }

        Set<String> knownIds = new HashSet<>();
        for (BlockEntry e : list) knownIds.add(e.id());

        // 先扫 BLOCK 注册表（旧行为）
        int fromBlocks = discoverBlocks(list, knownIds);
        // 再扫 ITEM 注册表（1.3.0 新增）：覆盖纯物品，Block 优先 + 中文名检查
        int fromItems = discoverItems(list, knownIds);

        VoiceBlockMod.LOGGER.info("Total entries: {} ({} from JSON, {} from BLOCK registry, {} from ITEM registry)",
                list.size(), list.size() - fromBlocks - fromItems, fromBlocks, fromItems);

        return new BlockData(list);
    }

    /**
     * 扫描 BLOCK 注册表，将 blocks.json 中未收录的方块添加到列表中。
     * 自动发现方块的 name 使用游戏本地化名称，aliases 为空。
     */
    private static int discoverBlocks(List<BlockEntry> list, Set<String> knownIds) {
        int added = 0;
        try {
            for (Block block : BuiltInRegistries.BLOCK) {
                ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
                if (rl == null) continue;
                String id = rl.getNamespace() + ":" + rl.getPath();
                if (!knownIds.contains(id)) {
                    String name = block.getName().getString();
                    list.add(new BlockEntry(id, name, Collections.emptyList()));
                    knownIds.add(id);
                    added++;
                }
            }
            if (added > 0) {
                VoiceBlockMod.LOGGER.info("Auto-discovered {} blocks from BLOCK registry", added);
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to auto-discover blocks from registry", e);
        }
        return added;
    }

    /**
     * 1.3.0 新增：扫描 ITEM 注册表，补充纯物品（无对应方块的物品）。
     * - Block 优先：已被 BLOCK 注册表收录的 id 跳过
     * - 中文优先：拿不到中文本地化的物品跳过，避免英文名称污染词典
     */
    private static int discoverItems(List<BlockEntry> list, Set<String> knownIds) {
        int added = 0;
        try {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
                if (rl == null) continue;
                String id = rl.getNamespace() + ":" + rl.getPath();
                if (knownIds.contains(id)) continue;
                String name = item.getDefaultInstance().getHoverName().getString();
                if (!hasChinese(name)) continue;
                list.add(new BlockEntry(id, name, Collections.emptyList()));
                knownIds.add(id);
                added++;
            }
            if (added > 0) {
                VoiceBlockMod.LOGGER.info("Auto-discovered {} items from ITEM registry", added);
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to auto-discover items from registry", e);
        }
        return added;
    }

    private static boolean hasChinese(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    public List<BlockEntry> getEntries() {
        return entries;
    }
}
