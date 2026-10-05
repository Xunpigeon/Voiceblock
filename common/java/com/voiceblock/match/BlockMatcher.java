package com.voiceblock.match;

import com.voiceblock.VoiceBlockMod;

import java.util.List;

/**
 * 方块匹配器，采用两层 fallback 策略：
 *   第1层：精确匹配 + 别名字典（零延迟）
 *   第2层：拼音 + 编辑距离（处理同音字、近音字）
 */
public class BlockMatcher {

    private static final float PINYIN_THRESHOLD = 0.6f;

    public static MatchResult match(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        // Vosk 中文识别结果每个字之间常有空格，需去除所有空白字符
        String text = input.replaceAll("\\s+", "").toLowerCase();
        if (text.isEmpty()) return null;
        List<BlockData.BlockEntry> entries = BlockData.getInstance().getEntries();
        if (entries.isEmpty()) return null;

        // 第1层：精确匹配 + 别名
        MatchResult exact = matchExact(text, entries);
        if (exact != null) {
            VoiceBlockMod.LOGGER.info("Layer1 exact match: {} -> {}", text, exact.displayName());
            return exact;
        }

        // 第2层：拼音 + 编辑距离
        MatchResult pinyin = matchByPinyin(text, entries);
        if (pinyin != null && pinyin.score() >= PINYIN_THRESHOLD) {
            VoiceBlockMod.LOGGER.info("Layer2 pinyin match: {} -> {} (score={})", text, pinyin.displayName(), pinyin.score());
            return pinyin;
        }

        // 拼音匹配结果低于阈值时，仍返回最接近的结果
        if (pinyin != null) {
            VoiceBlockMod.LOGGER.info("Layer2 pinyin best effort: {} -> {} (score={})", text, pinyin.displayName(), pinyin.score());
            return pinyin;
        }

        return null;
    }

    /**
     * 第1层：精确名称或别名匹配。
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
     * 第2层：拼音编辑距离匹配。
     */
    private static MatchResult matchByPinyin(String text, List<BlockData.BlockEntry> entries) {
        String textPinyin = PinyinUtil.toPinyin(text);
        String bestId = null;
        String bestName = null;
        float bestScore = 0;

        for (BlockData.BlockEntry entry : entries) {
            // 匹配名称拼音
            float score = PinyinUtil.similarity(textPinyin, PinyinUtil.toPinyin(entry.name()));
            if (score > bestScore) {
                bestScore = score;
                bestId = entry.id();
                bestName = entry.name();
            }
            // 匹配别名拼音
            for (String alias : entry.aliases()) {
                float aliasScore = PinyinUtil.similarity(textPinyin, PinyinUtil.toPinyin(alias));
                if (aliasScore > bestScore) {
                    bestScore = aliasScore;
                    bestId = entry.id();
                    bestName = entry.name();
                }
            }
        }

        if (bestId == null) return null;
        return new MatchResult(bestId, bestName, bestScore, "pinyin");
    }
}
