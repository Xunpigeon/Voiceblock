package com.voiceblock.source;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.voiceblock.VoiceBlockMod;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 物品源配置管理。
 * 存储于 config/voiceblock/sources.json
 *
 * v2.0 默认配置：
 *   inventory     开  pri=1
 *   curio         开  pri=2
 *   enderchest    关  pri=3
 *   shulkerBox    开  pri=4
 *   ae2           开  pri=5
 *   tomsstorage   开  pri=6
 *   nearbyChest   关  pri=7  radius=16
 */
public class SourceConfigs {

    /** 默认配置（首次启动或配置文件不存在时使用）
     *
     * <p>v2.0.8（视障玩家整合包）：默认开启 nearbyChest。
     * 原版 v2.0.7 是默认关闭（避免误吞别人的箱子），但视障群体的整合包里大家共用
     * 资源，从周围 Iron Chest / 木桶 / 箱子取物是核心需求。装了服务端 mod 后由
     * 服务端执行取物，不会出现幽灵方块，所以默认开。</p>
     */
    private static final List<SourceConfig> DEFAULTS = List.of(
            new SourceConfig("inventory",   true,  1, null),
            new SourceConfig("curio",       true,  2, null),
            // 末影箱默认保持关闭：每个玩家私有，玩家单独决定要不要打开
            new SourceConfig("enderchest",  false, 3, null),
            new SourceConfig("shulkerbox",  true,  4, null),
            new SourceConfig("ae2",         true,  5, null),
            new SourceConfig("tomsstorage", true,  6, null),
            // v2.0.8 起默认开：视障玩家整合包用 Iron Chest:restocked（继承 vanilla ChestBlockEntity 实现 Container），
            // 由 SourceNearbyChest 自动扫到。装了服务端 mod 后由服务端执行取物，无幽灵方块问题
            new SourceConfig("nearbychest", true,  7, 16)
    );

    private final Map<String, SourceConfig> configs = new LinkedHashMap<>();
    private Path configFile;

    public static synchronized SourceConfigs load() {
        SourceConfigs sc = new SourceConfigs();
        sc.loadFromFile();
        return sc;
    }

    private SourceConfigs() {
        // 全部 key 用 lowercase（与 norm() 一致），保证 configs.get("shulkerBox") 找得到
        for (SourceConfig c : DEFAULTS) configs.put(c.name(), c);
    }

    private void loadFromFile() {
        configFile = Paths.get("config", "voiceblock", "sources.json");
        if (!Files.exists(configFile)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, SourceConfig>>() {}.getType();
            Map<String, SourceConfig> loaded = new Gson().fromJson(r, type);
            if (loaded != null) {
                // 规范化 key：老配置文件里如果是大写或混合大小写，归一为 lowercase
                for (var e : loaded.entrySet()) {
                    configs.put(norm(e.getKey()), e.getValue());
                }
                VoiceBlockMod.LOGGER.info("Loaded {} source configs from {}", loaded.size(), configFile);
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to load sources.json, using defaults", e);
        }
    }

    public void save() {
        try {
            if (configFile == null) configFile = Paths.get("config", "voiceblock", "sources.json");
            Files.createDirectories(configFile.getParent());
            try (Writer w = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                new Gson().toJson(configs, w);
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to save sources.json", e);
        }
    }

    public SourceConfig get(String name) {
        return configs.get(norm(name));
    }

    public Collection<SourceConfig> all() {
        return configs.values();
    }

    public boolean setEnabled(String name, boolean enabled) {
        SourceConfig c = configs.get(norm(name));
        if (c == null) return false;
        String key = norm(name);
        configs.put(key, new SourceConfig(c.name(), enabled, c.priority(), c.radius()));
        save();
        return true;
    }

    public boolean setPriority(String name, int priority) {
        SourceConfig c = configs.get(norm(name));
        if (c == null) return false;
        String key = norm(name);
        configs.put(key, new SourceConfig(c.name(), c.enabled(), priority, c.radius()));
        save();
        return true;
    }

    public boolean setRadius(String name, int radius) {
        SourceConfig c = configs.get(norm(name));
        if (c == null) return false;
        String key = norm(name);
        configs.put(key, new SourceConfig(c.name(), c.enabled(), c.priority(), radius));
        save();
        return true;
    }

    /** 大小写不敏感查找：玩家输入 "EnderChest" 也能匹配 "enderchest"。 */
    private static String norm(String name) {
        return name == null ? null : name.toLowerCase();
    }
}