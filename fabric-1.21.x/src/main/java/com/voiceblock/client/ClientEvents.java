package com.voiceblock.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.voiceblock.VoiceBlockMod;
import com.voiceblock.debug.DebugCommands;
import com.voiceblock.match.BlockMatcher;
import com.voiceblock.match.MatchResult;
import com.voiceblock.voice.MicrophoneRecorder;
import com.voiceblock.voice.RecognitionResult;
import com.voiceblock.voice.VoskRecognizer;
import com.voiceblock.util.InventoryUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class ClientEvents {

    private static boolean wasKeyDown = false;
    private static MicrophoneRecorder recorder;
    private static VoskRecognizer recognizer;
    private static volatile boolean processing = false;

    public static void register() {
        try {
            recognizer = VoskRecognizer.getInstance();
            recorder = new MicrophoneRecorder();
            VoiceBlockMod.LOGGER.info("Voice components initialized.");
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to initialize voice components", e);
        }

        ClientTickEvents.END_CLIENT_TICK.register(mc -> onClientTick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> registerCommands(dispatcher));
    }

    /** fabric-api 0.100.6 的 v2 用 FabricClientCommandSource。 */
    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("voiceblock")
            .executes(c -> { DebugCommands.handleCommand("/voiceblock"); return 1; })
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("debug")
                .executes(c -> { DebugCommands.handleCommand("/voiceblock debug"); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bind")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("idx", IntegerArgumentType.integer())
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("id", StringArgumentType.string())
                        .executes(c -> {
                            DebugCommands.handleCommand("/voiceblock bind "
                                + IntegerArgumentType.getInteger(c, "idx") + " "
                                + StringArgumentType.getString(c, "id"));
                            return 1;
                        }))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
                .executes(c -> { DebugCommands.handleCommand("/voiceblock list"); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("pinyin", StringArgumentType.string())
                    .executes(c -> {
                        DebugCommands.handleCommand("/voiceblock remove " + StringArgumentType.getString(c, "pinyin"));
                        return 1;
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dedup")
                .executes(c -> { DebugCommands.handleCommand("/voiceblock dedup"); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload")
                .executes(c -> { DebugCommands.handleCommand("/voiceblock reload"); return 1; }))
        );
    }

    private static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KeyBindings.voiceBlockKey == null) return;

        boolean isDown = KeyBindings.voiceBlockKey.isDown();

        if (isDown && !wasKeyDown && !processing) startRecording(mc);
        if (!isDown && wasKeyDown && !processing) stopAndRecognize(mc);

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
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.5f)
            );
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
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f)
        );

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
