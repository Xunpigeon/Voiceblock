package com.voiceblock.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.voiceblock.VoiceBlockMod;
import com.voiceblock.debug.DebugCommands;
import com.voiceblock.debug.SourceCommands;
import com.voiceblock.match.BlockMatcher;
import com.voiceblock.match.ManualOverrides;
import com.voiceblock.match.MatchResult;
import com.voiceblock.match.PinyinUtil;
import com.voiceblock.network.VoiceActionPayload;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasKeyDown = false;
    private static boolean wasLearnKeyDown = false;
    private static boolean learning = false;
    private static String learnItemId = null;
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
        SourceBootstrap.registerAll();
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
                        // 诊断用：不说话，直接走一次取物链路（联机排查服务端通道 / 物品源）
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
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KeyBindings.voiceBlockKey == null) return;

        // 第一次进游戏：弹一个提示（默认关 nearbyChest 解释）
        if (HintManager.get().hasShown("nearbyChestHint") == false) {
            HintManager.get().markShown("nearbyChestHint");
            mc.player.displayClientMessage(Component.literal(
                "§e[VoiceBlock] 提示：§r\"周围箱子\" 默认关（避免误吞别人的箱子）。要打开请用 /voiceblock source enable nearbyChest"), false);
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
                        VoiceHudOverlay.showRecognition(primary, "匹配: " + finalMatched.displayName());
                        int desiredAmount;
                        if (mc.player.isCreative()) {
                            desiredAmount = 1;
                        } else {
                            Item item = ItemResolver.resolve(finalMatched.itemId());
                            desiredAmount = item != null ? InventoryUtil.getDefaultMaxStackSize(item) : 1;
                        }
                        VoiceBlockMod.LOGGER.info("[VoiceBlock] creative={}, sending voice action '{}' x{} to server",
                                mc.player.isCreative(), finalMatched.itemId(), desiredAmount);
                        // v2.0.x：联机模式下把请求发给服务端执行，单人模式下服务端回包逻辑直接复用客户端 takeAny 路径
                        // （集成服务器走 LocalServerPlayer 时也是 ServerPlayer，分支自然命中 ServerInventoryUtil）
                        PacketDistributor.sendToServer(new VoiceActionPayload(
                                finalMatched.itemId(), desiredAmount));
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

    /**
     * 诊断用：不经过麦克风，直接按「语音识别完成」后的结果走一次客户端 → 服务端取物链路。
     *
     * <p>联机时怀疑"服务端没装 / 通道没通 / 某个物品源在服务端报错"，
     * 用 {@code /voiceblock test <物品ID> [数量]} 就能复现，不必对着麦克风说话。</p>
     */
    public static void testRequest(String itemId, int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ResourceLocation rl = ResourceLocation.tryParse(itemId == null ? "" : itemId.trim());
        if (rl == null) {
            mc.player.displayClientMessage(Component.literal("§c物品 ID 不合法: " + itemId), true);
            return;
        }
        int desired = mc.player.isCreative() ? 1 : Math.max(1, amount);
        VoiceBlockMod.LOGGER.info("[VoiceBlock] /voiceblock test -> request '{}' x{} (creative={})",
                rl, desired, mc.player.isCreative());
        try {
            PacketDistributor.sendToServer(new VoiceActionPayload(rl.toString(), desired));
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] test request send failed", t);
            mc.player.displayClientMessage(Component.literal("§c发送失败：服务端未装 VoiceBlock?"), true);
        }
    }

    /**
     * 学习模式开始：记录当前手持物品 ID 并启动录音。
     */
    private static void startLearnRecording(Minecraft mc) {
        if (recorder == null) {
            mc.player.displayClientMessage(Component.literal("语音组件未初始化"), true);
            return;
        }
        ItemStack held = mc.player.getInventory().getItem(mc.player.getInventory().selected);
        if (held.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("§e[学习模式] 请先手持要记录名字的物品"), true);
            return;
        }
        ResourceLocation rl = RegistryAccess.getItemKey(held.getItem());
        if (rl == null) {
            mc.player.displayClientMessage(Component.literal("§c[学习模式] 无法解析手持物品 ID"), true);
            return;
        }
        learnItemId = rl.toString();
        try {
            recorder.start();
            learning = true;
            mc.player.displayClientMessage(Component.literal("§d[学习模式] 请说出该物品的名字…"), true);
            SoundUtil.playSound(mc.player, "UI_BUTTON_CLICK", 0.4f, 1.5f);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Failed to start learn recording", e);
            learning = false;
            learnItemId = null;
            mc.player.displayClientMessage(Component.literal("录音启动失败"), true);
        }
    }

    /**
     * 学习模式结束：停止录音 → 后台识别 → 拼音写入 manual_overrides.json。
     */
    private static void stopAndLearn(Minecraft mc) {
        if (recorder == null || recognizer == null) {
            mc.player.displayClientMessage(Component.literal("语音组件未初始化"), true);
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
                    mc.execute(() -> mc.player.displayClientMessage(Component.literal("§c[学习模式] 未识别到语音"), false));
                    return;
                }
                String pinyin = PinyinUtil.toPinyin(primary.replaceAll("\\s+", ""));
                if (pinyin.isEmpty()) {
                    mc.execute(() -> mc.player.displayClientMessage(Component.literal("§c[学习模式] 识别结果不含可转拼音的中文"), false));
                    return;
                }
                final String heard = primary;
                mc.execute(() -> applyLearn(mc, pinyin, itemId, heard));
            } catch (Exception e) {
                VoiceBlockMod.LOGGER.error("Learn recognition failed", e);
                mc.execute(() -> mc.player.displayClientMessage(Component.literal("§c[学习模式] 识别失败"), false));
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
                    "§a[学习模式] 已记录：§f" + pinyin + " §7(听到: " + heard + ") §7-> §f" + itemId), false);
            return;
        }
        String prev = existing.get(0).triggerPinyin();
        Component msg = Component.literal("§d[学习模式] §f" + itemId + " §e已有发音 §f" + prev
                        + "§e，本次听到 §f" + pinyin + "§e，请选择：")
                .append(Component.literal(" "))
                .append(learnButton("§a[覆盖]", "/voiceblock _learn replace " + pinyin + " " + itemId))
                .append(Component.literal("   "))
                .append(learnButton("§b[加为模糊名称]", "/voiceblock _learn alias " + pinyin + " " + itemId));
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
            mc.player.displayClientMessage(Component.literal("§a[学习模式] 已覆盖发音：§f" + pinyin), false);
        } else if ("alias".equalsIgnoreCase(action)) {
            ManualOverrides.getInstance().addOrUpdate(pinyin, itemId, "fuzzy");
            mc.player.displayClientMessage(Component.literal("§a[学习模式] 已加为模糊名称：§f" + pinyin), false);
        }
    }

    /**
     * Tab 补全：玩家输入 /voiceblock source enable <TAB> 时弹出所有源名。
     * 大小写不敏感：玩家输 "AE" 也能补出 "ae2"。
     */
    private static final SuggestionProvider<net.minecraft.commands.CommandSourceStack> SOURCE_NAMES_SUGGESTER =
        (CommandContext<net.minecraft.commands.CommandSourceStack> ctx, SuggestionsBuilder builder) -> {
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
