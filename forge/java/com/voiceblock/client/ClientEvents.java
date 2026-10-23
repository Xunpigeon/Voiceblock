package com.voiceblock.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.voiceblock.VoiceBlockMod;
import com.voiceblock.debug.DebugCommands;
import com.voiceblock.debug.SourceCommands;
import com.voiceblock.match.BlockMatcher;
import com.voiceblock.match.ManualOverrides;
import com.voiceblock.match.MatchResult;
import com.voiceblock.match.PinyinUtil;
import com.voiceblock.network.VoiceActionPacket;
import com.voiceblock.network.VoiceNetwork;
import com.voiceblock.source.HintManager;
import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SourceBootstrap;
import com.voiceblock.source.SourceManager;
import com.voiceblock.voice.MicrophoneRecorder;
import com.voiceblock.voice.RecognitionResult;
import com.voiceblock.voice.VoskRecognizer;
import com.voiceblock.util.InventoryUtil;
import com.voiceblock.util.ItemResolver;
import com.voiceblock.util.RegistryAccess;
import com.voiceblock.util.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasKeyDown = false;
    private static boolean wasLearnKeyDown = false;
    private static boolean learning = false;
    private static String learnItemId = null;
    private static MicrophoneRecorder recorder;
    private static VoskRecognizer recognizer;
    private static volatile boolean processing = false;
    private static volatile boolean voiceInitFailed = false;

    /**
     * 延迟初始化语音组件。
     *
     * <p><b>不能放在 static 初始化块里</b>：Vosk 依赖原生库，任何加载失败（缺 native、
     * 平台不支持、jar 被裁剪）抛的是 {@link NoClassDefFoundError} / {@link UnsatisfiedLinkError}，
     * 属于 Error 而不是 Exception。在 static 块里抛 Error 会让整个 {@code ClientEvents} 类初始化失败，
     * FML 直接判为 broken mod state —— 玩家的整个客户端崩在启动阶段，连不进游戏。</p>
     *
     * <p>改成"第一次要用的时候再初始化 + 兜住 Throwable"：最坏结果只是语音取物不可用，
     * 玩家仍然能正常进入游戏并用其它功能（对依赖本模组的视障玩家尤其重要）。</p>
     */
    private static void ensureVoiceComponents() {
        if ((recognizer != null && recorder != null) || voiceInitFailed) return;
        try {
            recognizer = VoskRecognizer.getInstance();
            recorder = new MicrophoneRecorder();
            VoiceBlockMod.LOGGER.info("Voice components initialized.");
        } catch (Throwable t) {
            voiceInitFailed = true;
            VoiceBlockMod.LOGGER.error("语音组件初始化失败，语音取物已禁用（其它功能正常）", t);
        }
    }

    /**
     * 诊断用：不经过麦克风，直接按「语音识别完成」后的结果走一次客户端→服务端取物链路。
     *
     * <p>用途：联机时如果怀疑"服务端没装模组 / 通道没通 / 某个物品源在服务端报错"，
     * 用 {@code /voiceblock test <物品ID> [数量]} 就能复现，不必对着麦克风说话。
     * 走的是和真实语音完全相同的 {@link VoiceActionPacket} 通道，所以结果可信。</p>
     */
    public static void testRequest(String itemId, int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ResourceLocation rl = ResourceLocation.tryParse(itemId == null ? "" : itemId.trim());
        if (rl == null) {
            mc.player.displayClientMessage(Component.literal("\u00a7c\u7269\u54c1 ID \u4e0d\u5408\u6cd5: " + itemId), true);
            return;
        }
        int desired = mc.player.isCreative() ? 1 : Math.max(1, amount);
        VoiceBlockMod.LOGGER.info("[VoiceBlock] /voiceblock test -> request '{}' x{} (creative={})",
                rl, desired, mc.player.isCreative());
        try {
            VoiceNetwork.CHANNEL.sendToServer(new VoiceActionPacket(rl.toString(), desired));
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] test request send failed", t);
            mc.player.displayClientMessage(
                    Component.literal("\u00a7c\u53d1\u9001\u5931\u8d25\uff1a\u670d\u52a1\u7aef\u672a\u88c5 VoiceBlock?"), true);
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        SourceBootstrap.registerAll();
        // 整合包里的 mixin/transformer 可能重写 Mojang 公开 API，导致 brigadier 注册链抛错。
        // 整体兜底：命令注册失败也不能拖垮客户端启动（功能降级 > 崩溃）。
        try {
            CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
            d.register(Commands.literal("voiceblock")
            .executes(c -> { DebugCommands.handleCommand("/voiceblock"); return 1; })
            .then(Commands.literal("debug").executes(c -> { DebugCommands.handleCommand("/voiceblock debug"); return 1; }))
            .then(Commands.literal("bind")
                .then(Commands.argument("idx", IntegerArgumentType.integer())
                    .executes(c -> {
                        // 不带 id：用当前手持
                        DebugCommands.handleCommand("/voiceblock bind " + IntegerArgumentType.getInteger(c, "idx"));
                        return 1;
                    })
                    .then(Commands.argument("id", StringArgumentType.string())
                        .executes(c -> {
                            // 带 id
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
            .then(Commands.literal("test")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(c -> {
                        // 用 greedyString 收整段再自己切，避免 brigadier 在 "id + 可选数量"
                        // 两段式参数上把 "minecraft:stone 64" 判成 trailing data。
                        String raw = StringArgumentType.getString(c, "args").trim();
                        String[] parts = raw.split("\\s+");
                        int count = 64;
                        if (parts.length > 1) {
                            try {
                                count = Integer.parseInt(parts[1]);
                            } catch (NumberFormatException ignored) {
                                count = 64;
                            }
                        }
                        testRequest(parts[0], count);
                        return 1;
                    })))
            .then(Commands.literal("source")
                .executes(c -> { SourceCommands.handleCommand("/voiceblock source"); return 1; })
                .then(Commands.literal("list").executes(c -> { SourceCommands.handleCommand("/voiceblock source list"); return 1; }))
                .then(Commands.literal("enable")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(SOURCE_NAMES_SUGGESTER)
                        .executes(c -> {
                            SourceCommands.handleCommand("/voiceblock source enable " + StringArgumentType.getString(c, "name"));
                            return 1;
                        })))
                .then(Commands.literal("disable")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(SOURCE_NAMES_SUGGESTER)
                        .executes(c -> {
                            SourceCommands.handleCommand("/voiceblock source disable " + StringArgumentType.getString(c, "name"));
                            return 1;
                        })))
                .then(Commands.literal("priority")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(SOURCE_NAMES_SUGGESTER)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                            .executes(c -> {
                                SourceCommands.handleCommand("/voiceblock source priority " + StringArgumentType.getString(c, "name") + " " + IntegerArgumentType.getInteger(c, "value"));
                                return 1;
                            }))))
                .then(Commands.literal("radius")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(SOURCE_NAMES_SUGGESTER)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                            .executes(c -> {
                                SourceCommands.handleCommand("/voiceblock source radius " + StringArgumentType.getString(c, "name") + " " + IntegerArgumentType.getInteger(c, "value"));
                                return 1;
                            }))))
                .then(Commands.literal("reset")
                    .executes(c -> { SourceCommands.handleCommand("/voiceblock source reset"); return 1; }))
            )
            .then(Commands.literal("_learn")
                .then(Commands.argument("action", StringArgumentType.word())
                    .then(Commands.argument("pinyin", StringArgumentType.string())
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                            .executes(c -> {
                                handleLearnChoice(
                                        StringArgumentType.getString(c, "action"),
                                        StringArgumentType.getString(c, "pinyin"),
                                        StringArgumentType.getString(c, "id"));
                                return 1;
                            })))))
            );
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] client command registration failed; /voiceblock commands disabled", t);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KeyBindings.voiceBlockKey == null) return;

        // 第一次进游戏：弹一个提示（默认关 nearbyChest 解释）
        if (HintManager.get().hasShown("nearbyChestHint") == false) {
            HintManager.get().markShown("nearbyChestHint");
            mc.player.displayClientMessage(Component.literal(
                "\u00a7e[VoiceBlock] \u63d0\u793a\uff1a\u00a7r\"\u5468\u56f4\u7bb1\u5b50\" \u9ed8\u8ba4\u5173\uff08\u907f\u514d\u8bef\u541e\u522b\u4eba\u7684\u7bb1\u5b50\uff09\u3002\u8981\u6253\u5f00\u8bf7\u7528 /voiceblock source enable nearbyChest"), false);
        }

        boolean isDown = KeyBindings.voiceBlockKey.isDown();

        if (isDown && !wasKeyDown && !processing && !learning) {
            startRecording(mc);
        }

        if (!isDown && wasKeyDown && !processing && !learning) {
            stopAndRecognize(mc);
        }

        wasKeyDown = isDown;

        // 学习模式：手持物品按住学习键说话 → 把该物品名字记进 manual_overrides.json
        boolean learnDown = KeyBindings.voiceBlockLearnKey.isDown();

        if (learnDown && !wasLearnKeyDown && !processing && !learning) {
            startLearnRecording(mc);
        }

        if (!learnDown && wasLearnKeyDown && !processing && learning) {
            stopAndLearn(mc);
        }

        wasLearnKeyDown = learnDown;
    }

    private static void startRecording(Minecraft mc) {
        ensureVoiceComponents();
        if (recorder == null) {
            mc.player.displayClientMessage(Component.literal("\u8bed\u97f3\u7ec4\u4ef6\u672a\u521d\u59cb\u5316"), true);
            return;
        }
        try {
            recorder.start();
            mc.player.displayClientMessage(Component.literal("\u6b63\u5728\u804c\u542c..."), true);
            SoundUtil.playSound(mc.player, "UI_BUTTON_CLICK", 0.4f, 1.5f);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to start recording", e);
            mc.player.displayClientMessage(Component.literal("\u5f55\u97f3\u542f\u52a8\u5931\u8d25"), true);
        }
    }

    private static void stopAndRecognize(Minecraft mc) {
        if (recorder == null || recognizer == null) {
            mc.player.displayClientMessage(Component.literal("\u8bed\u97f3\u7ec4\u4ef6\u672a\u521d\u59cb\u5316"), true);
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
                        VoiceHudOverlay.showRecognition("(\u7a7a)", "\u672a\u8bc6\u522b\u5230\u8bed\u97f3");
                        mc.player.displayClientMessage(Component.literal("\u672a\u8bc6\u522b\u5230\u8bed\u97f3"), true);
                    });
                    return;
                }

                VoiceBlockMod.LOGGER.info("[VoiceBlock] matcher running for primary='{}', alts={}", primary, result.alternatives());
                MatchResult matched;
                try {
                    matched = BlockMatcher.match(result);
                } catch (Throwable t) {
                    VoiceBlockMod.LOGGER.error("[VoiceBlock] BlockMatcher threw for primary='" + primary + "'", t);
                    return;
                }
                VoiceBlockMod.LOGGER.info("[VoiceBlock] BlockMatcher result: {}", matched == null ? "null" : matched.itemId());
                final MatchResult finalMatched = matched;
                mc.execute(() -> {
                    if (finalMatched != null) {
                        VoiceHudOverlay.showRecognition(primary, "\u5339\u914d: " + finalMatched.displayName());
                        int desiredAmount;
                        if (mc.player.isCreative()) {
                            desiredAmount = 1;
                        } else {
                            Item item = ItemResolver.resolve(finalMatched.itemId());
                            desiredAmount = item != null ? InventoryUtil.getDefaultMaxStackSize(item) : 1;
                        }
                        VoiceBlockMod.LOGGER.info("[VoiceBlock] creative={}, sending voice action '{}' x{} to server",
                                mc.player.isCreative(), finalMatched.itemId(), desiredAmount);
                        // 把请求发给服务端执行（单人集成服务器同样命中 ServerPlayer 路径），
                        // 专用服务器 / 联机场景统一由服务端权威取物。
                        try {
                            VoiceNetwork.CHANNEL.sendToServer(new VoiceActionPacket(
                                    finalMatched.itemId(), desiredAmount));
                        } catch (Throwable t) {
                            VoiceBlockMod.LOGGER.error("[VoiceBlock] failed to send voice action to server", t);
                            mc.player.displayClientMessage(Component.literal("\u53d1\u9001\u5931\u8d25\uff1a\u670d\u52a1\u7aef\u672a\u88c5 VoiceBlock?"), true);
                        }
                    } else {
                        VoiceHudOverlay.showRecognition(primary, "\u672a\u5339\u914d\u5230\u65b9\u5757");
                        mc.player.displayClientMessage(Component.literal("\u672a\u5339\u914d\u5230\u65b9\u5757: " + primary), true);
                    }
                });
            } catch (Exception e) {
                VoiceBlockMod.LOGGER.error("Recognition failed", e);
                mc.execute(() -> {
                    VoiceHudOverlay.showRecognition("(\u5f02\u5e38)", "\u8bc6\u522b\u5931\u8d25: " + e.getMessage());
                    mc.player.displayClientMessage(Component.literal("\u8bc6\u522b\u5931\u8d25"), true);
                });
            } finally {
                processing = false;
            }
        }, "VoiceBlock-Recognize");
        recognizeThread.setDaemon(true);
        recognizeThread.start();
    }

    /**
     * 学习模式开始：记录当前手持物品 ID 并启动录音。
     */
    private static void startLearnRecording(Minecraft mc) {
        ensureVoiceComponents();
        if (recorder == null) {
            mc.player.displayClientMessage(Component.literal("\u8bed\u97f3\u7ec4\u4ef6\u672a\u521d\u59cb\u5316"), true);
            return;
        }
        ItemStack held = mc.player.getInventory().getItem(mc.player.getInventory().selected);
        if (held.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("\u00a7e[\u5b66\u4e60\u6a21\u5f0f] \u8bf7\u5148\u624b\u6301\u8981\u8bb0\u5f55\u540d\u5b57\u7684\u7269\u54c1"), true);
            return;
        }
        ResourceLocation rl = RegistryAccess.getItemKey(held.getItem());
        if (rl == null) {
            mc.player.displayClientMessage(Component.literal("\u00a7c[\u5b66\u4e60\u6a21\u5f0f] \u65e0\u6cd5\u89e3\u6790\u624b\u6301\u7269\u54c1 ID"), true);
            return;
        }
        learnItemId = rl.toString();
        try {
            recorder.start();
            learning = true;
            mc.player.displayClientMessage(Component.literal("\u00a7d[\u5b66\u4e60\u6a21\u5f0f] \u8bf7\u8bf4\u51fa\u8be5\u7269\u54c1\u7684\u540d\u5b57\u2026"), true);
            SoundUtil.playSound(mc.player, "UI_BUTTON_CLICK", 0.4f, 1.5f);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to start learn recording", e);
            learning = false;
            learnItemId = null;
            mc.player.displayClientMessage(Component.literal("\u5f55\u97f3\u542f\u52a8\u5931\u8d25"), true);
        }
    }

    /**
     * 学习模式结束：停止录音 → 后台识别 → 拼音写入 manual_overrides.json。
     */
    private static void stopAndLearn(Minecraft mc) {
        if (recorder == null || recognizer == null) {
            mc.player.displayClientMessage(Component.literal("\u8bed\u97f3\u7ec4\u4ef6\u672a\u521d\u59cb\u5316"), true);
            learning = false;
            return;
        }
        processing = true;
        learning = false;
        final String itemId = learnItemId;
        learnItemId = null;
        byte[] audioData = recorder.stop();
        SoundUtil.playSound(mc.player, "EXPERIENCE_ORB_PICKUP", 0.4f, 1.0f);

        Thread thread = new Thread(() -> {
            try {
                RecognitionResult result = recognizer.recognize(audioData);
                String primary = result.primary();
                if (primary == null || primary.trim().isEmpty()) {
                    mc.execute(() -> mc.player.displayClientMessage(Component.literal("\u00a7c[\u5b66\u4e60\u6a21\u5f0f] \u672a\u8bc6\u522b\u5230\u8bed\u97f3"), false));
                    return;
                }
                String pinyin = PinyinUtil.toPinyin(primary.replaceAll("\\s+", ""));
                if (pinyin.isEmpty()) {
                    mc.execute(() -> mc.player.displayClientMessage(Component.literal("\u00a7c[\u5b66\u4e60\u6a21\u5f0f] \u8bc6\u522b\u7ed3\u679c\u4e0d\u542b\u53ef\u8f6c\u62fc\u97f3\u7684\u4e2d\u6587"), false));
                    return;
                }
                final String heard = primary;
                mc.execute(() -> applyLearn(mc, pinyin, itemId, heard));
            } catch (Exception e) {
                VoiceBlockMod.LOGGER.error("Learn recognition failed", e);
                mc.execute(() -> mc.player.displayClientMessage(Component.literal("\u00a7c[\u5b66\u4e60\u6a21\u5f0f] \u8bc6\u522b\u5931\u8d25"), false));
            } finally {
                processing = false;
            }
        }, "VoiceBlock-Learn");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 写入学习结果：
     * - 该物品还没有任何记录 → 直接写入（第一次记录）。
     * - 该物品已有记录 → 弹聊天提示，让玩家选择「覆盖旧发音」或「加为模糊名称（两者都保留）」。
     */
    private static void applyLearn(Minecraft mc, String pinyin, String itemId, String heard) {
        if (mc.player == null || itemId == null) return;
        List<ManualOverrides.Rule> existing = ManualOverrides.getInstance().findByTarget(itemId);
        if (existing.isEmpty()) {
            ManualOverrides.getInstance().addOrUpdate(pinyin, itemId, "learn");
            mc.player.displayClientMessage(Component.literal(
                    "\u00a7a[\u5b66\u4e60\u6a21\u5f0f] \u5df2\u8bb0\u5f55\uff1a\u00a7f" + pinyin + " \u00a77(\u542c\u5230: " + heard + ") \u00a77-> \u00a7f" + itemId), false);
            return;
        }
        String prev = existing.get(0).triggerPinyin();
        Component msg = Component.literal("\u00a7d[\u5b66\u4e60\u6a21\u5f0f] \u00a7f" + itemId + " \u00a7e\u5df2\u6709\u53d1\u97f3 \u00a7f" + prev
                        + "\u00a7e\uff0c\u672c\u6b21\u542c\u5230 \u00a7f" + pinyin + "\u00a7e\uff0c\u8bf7\u9009\u62e9\uff1a")
                .append(Component.literal(" "))
                .append(learnButton("\u00a7a[\u8986\u76d6]", "/voiceblock _learn replace " + pinyin + " " + itemId))
                .append(Component.literal("   "))
                .append(learnButton("\u00a7b[\u52a0\u4e3a\u6a21\u7cca\u540d\u79f0]", "/voiceblock _learn alias " + pinyin + " " + itemId));
        mc.player.displayClientMessage(msg, false);
    }

    /** 生成一个「点击即执行命令」的聊天按钮。 */
    private static Component learnButton(String label, String command) {
        return Component.literal(label).withStyle(style ->
                style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** 处理学习模式聊天按钮的选择（覆盖 / 加为模糊名称）。 */
    private static void handleLearnChoice(String action, String pinyin, String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if ("replace".equalsIgnoreCase(action)) {
            ManualOverrides.getInstance().replaceForTarget(pinyin, itemId, "learn");
            mc.player.displayClientMessage(Component.literal("\u00a7a[\u5b66\u4e60\u6a21\u5f0f] \u5df2\u8986\u76d6\u53d1\u97f3\uff1a\u00a7f" + pinyin), false);
        } else if ("alias".equalsIgnoreCase(action)) {
            ManualOverrides.getInstance().addOrUpdate(pinyin, itemId, "fuzzy");
            mc.player.displayClientMessage(Component.literal("\u00a7a[\u5b66\u4e60\u6a21\u5f0f] \u5df2\u52a0\u4e3a\u6a21\u7cca\u540d\u79f0\uff1a\u00a7f" + pinyin), false);
        }
    }

    /**
     * Tab 补全：玩家输入 /voiceblock source enable <TAB> 时弹出所有源名。
     * 大小写不敏感：玩家输 "AE" 也能补出 "ae2"。
     */
    private static final SuggestionProvider<CommandSourceStack> SOURCE_NAMES_SUGGESTER =
        (CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) -> {
            String typed = builder.getRemaining().toLowerCase();
            for (ItemSource s : SourceManager.getInstance().getOrderedEnabledSources()) {
                String name = s.name();
                if (name != null && name.toLowerCase().startsWith(typed)) {
                    builder.suggest(name);
                }
            }
            // 也补全已注册但被禁用的源（玩家可能要 enable 一个关掉的）
            for (var status : SourceManager.getInstance().getStatusList()) {
                if (status.name().toLowerCase().startsWith(typed)) {
                    builder.suggest(status.name());
                }
            }
            return builder.buildFuture();
        };
}
