package com.voiceblock.match;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 中文转拼音工具（无声调，小写）。
 */
public class PinyinUtil {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    /**
     * 把字符串转成拼音（无声调），非中文字符保留原样。
     * 例如 "橡木楼梯" -> "xiangmulouti"
     */
    public static String toPinyin(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.toString(c).matches("[\\u4E00-\\u9FA5]")) {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        sb.append(pinyinArray[0]);
                    } else {
                        sb.append(c);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Levenshtein 编辑距离。
     */
    public static int editDistance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    /**
     * 归一化相似度 (0~1)，1 表示完全相同。
     */
    public static float similarity(String a, String b) {
        int dist = editDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0f;
        return 1.0f - (float) dist / maxLen;
    }
}
