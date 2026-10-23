package com.voiceblock.net;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 客户端：把服务端回包变成玩家能感知的反馈（聊天栏 + HUD）。
 *
 * <p>视障玩家主要靠声音和聊天播报，所以成功/失败都必须说话，
 * 不能只在 HUD 上闪一下。</p>
 *
 * <p>只在客户端加载（引用 {@link Minecraft}），服务端不会碰这个类。</p>
 */
public final class VoiceResultHandler {

    private VoiceResultHandler() {}

    /**
     * @param success     是否取到
     * @param amountTaken 实际数量
     * @param reason      失败原因（可空）
     * @param displayName 物品中文名（可空）
     */
    public static void accept(boolean success, int amountTaken, String reason, String displayName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        String name = displayName == null ? "" : displayName;
        Component line;
        if (success) {
            line = Component.literal("\u5df2\u83b7\u53d6: " + name + " x" + amountTaken);
        } else if (reason != null && !reason.isEmpty()) {
            line = Component.literal(reason + ": " + name);
        } else {
            line = Component.literal("\u672a\u627e\u5230: " + name);
        }

        mc.player.displayClientMessage(line, true);
        try {
            com.voiceblock.client.VoiceHudOverlay.showRecognition(name, line.getString());
        } catch (Throwable ignored) {
            // HUD 是锦上添花，失败不影响主流程
        }

        VoiceBlockMod.LOGGER.info("[VoiceBlock] client got result: success={} amount={} reason={} name={}",
                success, amountTaken, reason, name);
    }
}
