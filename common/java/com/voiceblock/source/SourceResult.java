package com.voiceblock.source;

/**
 * 物品源取物结果。
 * <pre>
 *   ok(int)         成功，拿到了 amount 个
 *   notHandled()    此源不处理，请 SourceManager 试下一个源
 *   fail(String)    明确失败，停止后续尝试（如"目标在别的维度"、"需要无线终端"、"身上已满"）
 * </pre>
 */
public record SourceResult(boolean success, String reason, int amountTaken) {

    public static SourceResult ok(int amount) {
        return new SourceResult(true, null, amount);
    }

    public static SourceResult notHandled() {
        return new SourceResult(false, null, 0);
    }

    public static SourceResult fail(String reason) {
        return new SourceResult(false, reason, 0);
    }

    /** 是否应该继续尝试其他源（未成功且无失败原因）。 */
    public boolean shouldContinue() {
        return !success && reason == null;
    }
}