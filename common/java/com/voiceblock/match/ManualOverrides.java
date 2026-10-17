package com.voiceblock.match;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 手动绑定：玩家可以把"我的发音"硬绑定到指定方块。
 * 存储在 config/voiceblock/manual_overrides.json，可手动编辑/分享。
 *
 * 匹配规则：识别候选中任一项的拼音等于 rule.triggerPinyin，直接返回 rule.targetId，不参与评分。
 */
public class ManualOverrides {

    public record Rule(String triggerPinyin, String targetId, String note) {}

    private static ManualOverrides instance;
    private List<Rule> rules = new ArrayList<>();
    private Path configFile;

    public static synchronized ManualOverrides getInstance() {
        if (instance == null) {
            instance = new ManualOverrides();
            instance.load();
        }
        return instance;
    }

    public void load() {
        configFile = Paths.get("config", "voiceblock", "manual_overrides.json");
        if (!Files.exists(configFile)) {
            rules = new ArrayList<>();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<Rule>>(){}.getType();
            List<Rule> loaded = new Gson().fromJson(reader, type);
            rules = loaded != null ? loaded : new ArrayList<>();
            VoiceBlockMod.LOGGER.info("Loaded {} manual override rule(s) from {}", rules.size(), configFile);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to load manual_overrides.json", e);
            rules = new ArrayList<>();
        }
    }

    /**
     * 重新加载（调试命令用）。
     */
    public void reload() {
        load();
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                new Gson().toJson(rules, writer);
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to save manual_overrides.json", e);
        }
    }

    /**
     * 在候选拼音集合中查找绑定规则。命中返回硬绑定结果，否则 null。
     */
    public MatchResult findByPinyin(Set<String> candidatePinyins) {
        if (rules.isEmpty() || candidatePinyins == null || candidatePinyins.isEmpty()) return null;
        for (Rule rule : rules) {
            if (rule.triggerPinyin != null && candidatePinyins.contains(rule.triggerPinyin)) {
                String displayName = findDisplayName(rule.targetId);
                if (displayName == null) displayName = rule.targetId;
                return new MatchResult(rule.targetId, displayName, 1.0f, "manual");
            }
        }
        return null;
    }

    private String findDisplayName(String id) {
        if (id == null) return null;
        for (BlockData.BlockEntry e : BlockData.getInstance().getEntries()) {
            if (e.id().equals(id)) return e.name();
        }
        return null;
    }

    public List<Rule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * 添加或覆盖规则。返回 true 表示新增，false 表示覆盖已有。
     */
    public boolean addOrUpdate(String triggerPinyin, String targetId, String note) {
        if (triggerPinyin == null || triggerPinyin.isBlank() || targetId == null || targetId.isBlank()) return false;
        for (int i = 0; i < rules.size(); i++) {
            if (triggerPinyin.equals(rules.get(i).triggerPinyin)) {
                rules.set(i, new Rule(triggerPinyin, targetId, note));
                save();
                return false;
            }
        }
        rules.add(new Rule(triggerPinyin, targetId, note));
        save();
        return true;
    }

    public boolean remove(String triggerPinyin) {
        boolean removed = rules.removeIf(r -> triggerPinyin.equals(r.triggerPinyin));
        if (removed) save();
        return removed;
    }
}
