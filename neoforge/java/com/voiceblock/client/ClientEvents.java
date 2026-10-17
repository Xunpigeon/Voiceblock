package com.voiceblock.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.voiceblock.VoiceBlockMod;
import com.voiceblock.debug.DebugCommands;
import com.voiceblock.match.BlockMatcher;
import com.voiceblock.match.MatchResult;
import com.voiceblock.voice.MicrophoneRecorder;
import com.voiceblock.voice.RecognitionResult;
import com.voiceblock.voice.VoskRecognizer;
import com.voiceblock.util.InventoryUtil;
import com.voiceblock.util.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT)
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
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("voiceblock")
            .executes(c -> { DebugCommands.handleCommand("/voiceblock"); return 1; })
            .then(Commands.literal("debug").executes(c -> { DebugCommands.handleCommand("/voiceblock debug"); return 1; }))
            .then(Commands.literal("bind")
                .then(Commands.argument("idx", IntegerArgumentType.integer())
                    .then(Commands.argument("id", StringArgumentType.string())
                        .executes(c -> {
                            DebugCommands.handleCommand("/voiceblock bind "
                                + IntegerArgumentType.getInteger(c, "idx") + " "
                                + StringArgumentType.getString(c, "id"));
                            return 1;
                        }))))
            .then(Commands.literal("list").executes(c -> { DebugCommands.handleCommand("/voiceblock list"); return 1; }))
            .then(Commands.literal("remove")
                .then(Commands.argument("pinyin", StringArgumentType.string())
                    .executes(c -> {
                        DebugCommands.handleCommand("/voiceblock remove " + StringArgumentType.getString(c, "pinyin"));
                        return 1;
                    })))
            .then(Commands.literal("dedup").executes(c -> { DebugCommands.handleCommand("/voiceblock dedup"); return 1; }))
            .then(Commands.literal("reload").executes(c -> { DebugCommands.handleCommand("/voiceblock reload"); return 1; }))
        );
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KeyBindings.voiceBlockKey == null) return;

        boolean isDown = KeyBindings.voiceBlockKey.isDown();

        if (isDown && !wasKeyDown && !processing) {
            startRecording(mc);
        }

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

        Thread recognizeThread = new Thread(() -> {
            try {
                RecognitionResult result = recognizer.recognize(audioData);
                VoiceBlockMod.LOGGER.info("Recognized: primary={}, alternatives={}",
                        result.primary(), result.alternatives());
                DebugCommands.setLastResult(result);

                String primary = result.primary();
                if (primary == null || primary.trim().isEmpty()) {
                    mc.execute(() -> {
                        VoiceHudOverlay.showRecognition("(空)", "未识别到语音");
                        mc.player.displayClientMessage(Component.literal("未识别到语音"), true);
                    });
                    return;
                }

                MatchResult matched = BlockMatcher.match(result);
                mc.execute(() -> {
                    if (matched != null) {
                        VoiceHudOverlay.showRecognition(primary, "匹配: " + matched.displayName());
                        if (mc.player.isCreative()) {
                            InventoryUtil.replaceSelectedItem(mc.player, matched.itemId(), matched.displayName());
                            mc.player.displayClientMessage(Component.literal("已获取: " + matched.displayName()), true);
                        } else {
                            boolean swapped = InventoryUtil.swapWithInventory(mc.player, matched.itemId());
                            if (swapped) {
                                mc.player.displayClientMessage(Component.literal("已切换到: " + matched.displayName()), true);
                            } else {
                                mc.player.displayClientMessage(Component.literal("背包中没有: " + matched.displayName()), true);
                            }
                        }
                    } else {
                        VoiceHudOverlay.showRecognition(primary, "未匹配到方块");
                        mc.player.displayClientMessage(Component.literal("未匹配到方块: " + primary), true);
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
