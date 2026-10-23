package com.voiceblock.source;

import com.voiceblock.VoiceBlockMod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 玩家一次性提示状态：保存到 config/voiceblock/hints.json。
 * 用法：hintManager.hasShown("nearbyChestHint") → 第一次 false、之后 true。
 */
public class HintManager {

    private final Map<String, Boolean> shown = new HashMap<>();
    private final Path file = Paths.get("config", "voiceblock", "hints.json");

    private static HintManager instance;

    public static synchronized HintManager get() {
        if (instance == null) instance = new HintManager();
        return instance;
    }

    private HintManager() {
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Boolean> loaded = new com.google.gson.Gson().fromJson(r, Map.class);
                if (loaded != null) shown.putAll(loaded);
            } catch (Exception e) {
                VoiceBlockMod.LOGGER.debug("[VoiceBlock] 加载 hints.json 失败", e);
            }
        }
    }

    public boolean hasShown(String key) {
        return shown.getOrDefault(key, false);
    }

    public void markShown(String key) {
        shown.put(key, true);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new com.google.gson.Gson().toJson(shown, w);
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] 写 hints.json 失败", e);
        }
    }
}