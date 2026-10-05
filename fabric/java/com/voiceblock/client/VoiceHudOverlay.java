package com.voiceblock.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class VoiceHudOverlay {

    private static volatile String displayText = "";
    private static volatile String matchInfo = "";
    private static volatile long displayUntil = 0;
    private static final long DISPLAY_DURATION_MS = 5000;

    public static void register() {
        HudRenderCallback.EVENT.register((gui, deltaTracker) -> render(gui));
    }

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

    private static void render(GuiGraphics gui) {
        if (displayText.isEmpty() || System.currentTimeMillis() > displayUntil) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int y = 30;

        String text = "识别: " + displayText;
        int textWidth = mc.font.width(text);
        int x = (screenWidth - textWidth) / 2;
        gui.drawString(mc.font, Component.literal(text), x, y, 0xFFFFFF, true);

        if (matchInfo != null && !matchInfo.isEmpty()) {
            y += 14;
            int matchWidth = mc.font.width(matchInfo);
            int matchX = (screenWidth - matchWidth) / 2;
            int color = matchInfo.startsWith("匹配:") ? 0x55FF55 : 0xFF5555;
            gui.drawString(mc.font, Component.literal(matchInfo), matchX, y, color, true);
        }
    }
}