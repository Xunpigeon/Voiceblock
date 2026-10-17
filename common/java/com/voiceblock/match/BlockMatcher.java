package com.voiceblock.match;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.voice.RecognitionResult;

import java.util.*;

/**
 * 方块匹配器，三层策略：
 *   第1层：manual_overrides 硬绑定（拼音匹配，命中直接返回，不评分）
 *   第2层：精确匹配 + 别名（按候选顺序遍历，命中即返回）
 *   第3层：N-best 拼音评分（每个候选对每个 entry 打分，按 entry 取最高分，最后取全局最高）
 *
 * 1.3.0 新增：支持 Vosk N-best 候选重排序，对识别容错性大幅提升。
 */
public class BlockMatcher {

    private static final float PINYIN_THRESHOLD = 0.6f;

    public static MatchResult match(RecognitionResult result) {
        if (result == null) return null;
        List<String> alts = result.alternatives();
        if (alts == null || alts.isEmpty()) return null;

        // 清洗：去空白 + 过滤空
        List<String> cleaned = new ArrayList<>();
        for (String s : alts) {
            if (s == null) continue;
            String c = s.replaceAll("\\s+", "").toLowerCase();
            if (!c.isEmpty()) cleaned.add(c);
        }
        if (cleaned.isEmpty()) return null;

        List<BlockData.BlockEntry> entries = BlockData.getInstance().getEntries();
        if (entries.isEmpty()) return null;

        // 第1层：manual override 硬绑定
        Set<String> pinyinSet = new HashSet<>();
        for (String c : cleaned) pinyinSet.add(PinyinUtil.toPinyin(c));
        MatchResult manual = ManualOverrides.getInstance().findByPinyin(pinyinSet);
        if (manual != null) {
            VoiceBlockMod.LOGGER.info("Layer1 manual override hit: {} -> {}", pinyinSet, manual.itemId());
            return manual;
        }

        // 第2层：精确名称/别名匹配
        for (String c : cleaned) {
            MatchResult exact = matchExact(c, entries);
            if (exact != null) {
                VoiceBlockMod.LOGGER.info("Layer2 exact match: {} -> {}", c, exact.displayName());
                return exact;
            }
        }

        // 第3层：N-best 拼音评分，按 entry 取最高分
        Map<String, Float> bestScore = new HashMap<>();
        Map<String, BlockData.BlockEntry> entryById = new HashMap<>();
        for (String c : cleaned) {
            String cPinyin = PinyinUtil.toPinyin(c);
            for (BlockData.BlockEntry entry : entries) {
                float score = bestPinyinScore(c, cPinyin, entry);
                float prev = bestScore.getOrDefault(entry.id(), 0f);
                if (score > prev) {
                    bestScore.put(entry.id(), score);
                    entryById.put(entry.id(), entry);
                }
            }
        }
        if (bestScore.isEmpty()) return null;

        String bestId = null;
        float top = 0;
        for (var e : bestScore.entrySet()) {
            if (e.getValue() > top) {
                top = e.getValue();
                bestId = e.getKey();
            }
        }
        if (bestId == null) return null;
        BlockData.BlockEntry entry = entryById.get(bestId);
        String method = top >= PINYIN_THRESHOLD ? "pinyin" : "pinyin-best-effort";
        VoiceBlockMod.LOGGER.info("Layer3 N-best pinyin: best={} score={} from {} candidates",
                entry.name(), top, cleaned.size());
        return new MatchResult(bestId, entry.name(), top, method);
    }

    /**
     * 兼容旧 API：单个字符串的便捷重载。
     */
    public static MatchResult match(String text) {
        if (text == null) return null;
        return match(new RecognitionResult(List.of(text)));
    }

    /**
     * 第2层：精确名称或别名匹配。
     */
    private static MatchResult matchExact(String text, List<BlockData.BlockEntry> entries) {
        for (BlockData.BlockEntry entry : entries) {
            if (entry.name().equalsIgnoreCase(text)) {
                return new MatchResult(entry.id(), entry.name(), 1.0f, "exact");
            }
            for (String alias : entry.aliases()) {
                if (alias.equalsIgnoreCase(text)) {
                    return new MatchResult(entry.id(), entry.name(), 1.0f, "alias");
                }
            }
        }
        return null;
    }

    /**
     * 对单个 entry 计算当前候选的最佳拼音分。
     */
    private static float bestPinyinScore(String text, String textPinyin, BlockData.BlockEntry entry) {
        float best = PinyinUtil.similarity(textPinyin, PinyinUtil.toPinyin(entry.name()));
        for (String alias : entry.aliases()) {
            best = Math.max(best, PinyinUtil.similarity(textPinyin, PinyinUtil.toPinyin(alias)));
        }
        return best;
    }
}
