package com.voiceblock.voice;

import java.util.List;

/**
 * 语音识别结果：包含 Vosk 返回的 N-best 候选文本。
 * 第一个候选（primary）是 Vosk 置信度最高的，作为主要显示文本。
 * 其余候选用于多候选重排序，提升识别准确率。
 */
public record RecognitionResult(List<String> alternatives) {

    public String primary() {
        return alternatives.isEmpty() ? "" : alternatives.get(0);
    }

    public static RecognitionResult empty() {
        return new RecognitionResult(List.of());
    }
}
