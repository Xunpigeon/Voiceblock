package com.voiceblock.client;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.match.BlockMatcher;
import com.voiceblock.match.MatchResult;
import com.voiceblock.voice.MicrophoneRecorder;
import com.voiceblock.voice.VoskRecognizer;
import com.voiceblock.util.InventoryUtil;
import com.voiceblock.util.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasKeyDown = false;
    private static MicrophoneRecorder recorder;
    private static VoskRecognizer recognizer;
    private static volatile boolean processing = false;

    static {
        try {
            recognizer = VoskRecognizer.getInstance();
            recorder = new MicrophoneRecorder();
            VoiceBlockMod.LOGGER.info("Voice components initialized.");
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to initialize voice components", e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KeyBindings.voiceBlockKey == null) return;

        boolean isDown = KeyBindings.voiceBlockKey.isDown();

        // 按键刚按下：开始录音
        if (isDown && !wasKeyDown && !processing) {
            startRecording(mc);
        }

        // 按键刚松开：停止录音并识别
        if (!isDown && wasKeyDown && !processing) {
            stopAndRecognize(mc);
        }

        wasKeyDown = isDown;
    }

    private static void startRecording(Minecraft mc) {
        if (recorder == null) {
            mc.player.displayClientMessage(Component.literal("语音组件未初始化"), true);
            return;
        }
        try {
            recorder.start();
            mc.player.displayClientMessage(Component.literal("正在聆听..."), true);
            SoundUtil.playSound(mc.player, "UI_BUTTON_CLICK", 0.4f, 1.5f);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to start recording", e);
            mc.player.displayClientMessage(Component.literal("录音启动失败"), true);
        }
    }

    private static void stopAndRecognize(Minecraft mc) {
        if (recorder == null || recognizer == null) {
            mc.player.displayClientMessage(Component.literal("语音组件未初始化"), true);
            return;
        }

        processing = true;
        byte[] audioData = recorder.stop();
        SoundUtil.playSound(mc.player, "EXPERIENCE_ORB_PICKUP", 0.4f, 1.0f);

        // 在新线程中识别，避免卡顿
        Thread recognizeThread = new Thread(() -> {
            try {
                String text = recognizer.recognize(audioData);
                VoiceBlockMod.LOGGER.info("Recognized text: {}", text);

                if (text == null || text.trim().isEmpty()) {
                    mc.execute(() -> {
                        VoiceHudOverlay.showRecognition("(空)", "未识别到语音");
                        mc.player.displayClientMessage(Component.literal("未识别到语音"), true);
                    });
                    return;
                }

                // 匹配方块
                MatchResult result = BlockMatcher.match(text);
                mc.execute(() -> {
                    if (result != null) {
                        VoiceHudOverlay.showRecognition(text, "匹配: " + result.displayName());
                        // 根据游戏模式选择不同逻辑
                        if (mc.player.isCreative()) {
                            InventoryUtil.replaceSelectedItem(mc.player, result.itemId(), result.displayName());
                            mc.player.displayClientMessage(Component.literal("已获取: " + result.displayName()), true);
                        } else {
                            boolean swapped = InventoryUtil.swapWithInventory(mc.player, result.itemId());
                            if (swapped) {
                                mc.player.displayClientMessage(Component.literal("已切换到: " + result.displayName()), true);
                            } else {
                                mc.player.displayClientMessage(Component.literal("背包中没有: " + result.displayName()), true);
                            }
                        }
                    } else {
                        VoiceHudOverlay.showRecognition(text, "未匹配到方块");
                        mc.player.displayClientMessage(Component.literal("未匹配到方块: " + text), true);
                    }
                });
            } catch (Exception e) {
                VoiceBlockMod.LOGGER.error("Recognition failed", e);
                mc.execute(() -> {
                    VoiceHudOverlay.showRecognition("(异常)", "识别失败: " + e.getMessage());
                    mc.player.displayClientMessage(Component.literal("识别失败"), true);
                });
            } finally {
                processing = false;
            }
        }, "VoiceBlock-Recognize");
        recognizeThread.setDaemon(true);
        recognizeThread.start();
    }
}