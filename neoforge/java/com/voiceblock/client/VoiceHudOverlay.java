package com.voiceblock.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 实时显示语音识别结果的 HUD overlay。
 * 在屏幕上方居中显示当前识别到的文本和匹配状态。
 */
@EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class VoiceHudOverlay {

    private static volatile String displayText = "";
    private static volatile String matchInfo = "";
    private static volatile long displayUntil = 0;
    private static final long DISPLAY_DURATION_MS = 5000;

    public static void showRecognition(String text, String matched) {
        displayText = text;
        matchInfo = matched;
        displayUntil = System.currentTimeMillis() + DISPLAY_DURATION_MS;
    }

    public static void clear() {
        displayText = "";
        matchInfo = "";
        displayUntil = 0;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (displayText.isEmpty() || System.currentTimeMillis() > displayUntil) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int y = 30;

        // 绘制识别文本
        String text = "识别: " + displayText;
        int textWidth = mc.font.width(text);
        int x = (screenWidth - textWidth) / 2;
        gui.drawString(mc.font, Component.literal(text), x, y, 0xFFFFFF, true);

        // 绘制匹配结果
        if (matchInfo != null && !matchInfo.isEmpty()) {
            y += 14;
            int matchWidth = mc.font.width(matchInfo);
            int matchX = (screenWidth - matchWidth) / 2;
            int color = matchInfo.startsWith("匹配:") ? 0x55FF55 : 0xFF5555;
            gui.drawString(mc.font, Component.literal(matchInfo), matchX, y, color, true);
        }
    }
}
