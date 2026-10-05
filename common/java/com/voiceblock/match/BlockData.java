package com.voiceblock.match;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.voiceblock.VoiceBlockMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 方块数据：包含所有 Minecraft 方块的中文名称、别名。
 * 数据文件：assets/voiceblock/blocks.json
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
                    // 确保 aliases 不为 null（blocks.json 中部分条目无 aliases 字段）
                    List<String> aliases = entry.aliases() != null ? entry.aliases() : Collections.emptyList();
                    list.add(new BlockEntry(entry.id(), entry.name(), aliases));
                }
            }
            VoiceBlockMod.LOGGER.info("Loaded {} block entries", list.size());
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to load blocks.json", e);
        }
        // 自动发现 blocks.json 中没有的方块（包括其他 Mod 添加的方块）
        int discovered = discoverBlocks(list);
        VoiceBlockMod.LOGGER.info("Total block entries: {} ({} from JSON, {} auto-discovered)",
                list.size(), list.size() - discovered, discovered);

        return new BlockData(list);
    }

    /**
     * 扫描游戏注册表，将 blocks.json 中未收录的方块添加到列表中。
     * 自动发现方块的 name 使用游戏本地化名称，aliases 为空。
     */
    private static int discoverBlocks(List<BlockEntry> list) {
        Set<String> knownIds = new HashSet<>();
        for (BlockEntry entry : list) {
            knownIds.add(entry.id());
        }

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
                VoiceBlockMod.LOGGER.info("Auto-discovered {} blocks from registry", added);
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to auto-discover blocks from registry", e);
        }
        return added;
    }

    public List<BlockEntry> getEntries() {
        return entries;
    }
}
