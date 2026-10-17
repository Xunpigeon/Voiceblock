package com.voiceblock.debug;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.match.DedupScanner;
import com.voiceblock.match.ManualOverrides;
import com.voiceblock.match.PinyinUtil;
import com.voiceblock.voice.RecognitionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.List;

/**
 * 调试命令系统。所有平台共用，命令前缀 /voiceblock。
 * 各平台 ClientEvents 在命令事件中调用 handleCommand。
 *
 * 子命令:
 *   /voiceblock                       显示帮助
 *   /voiceblock debug                 显示最近一次识别的 N-best 候选
 *   /voiceblock bind <序号> <方块ID>   把候选绑到指定方块（写 manual_overrides.json）
 *   /voiceblock list                  列出所有手动绑定
 *   /voiceblock remove <拼音>         删除一条绑定
 *   /voiceblock dedup                 扫冲突并输出报告
 */
public class DebugCommands {

    private static volatile RecognitionResult lastResult;
    private static volatile long lastResultTimestamp;

    /** 由各平台 ClientEvents 在识别完成后调用，记录最近一次的 N-best 候选。 */
    public static void setLastResult(RecognitionResult r) {
        lastResult = r;
        lastResultTimestamp = System.currentTimeMillis();
    }

    /**
     * 处理完整命令字符串。
     * @return true 表示已处理（Fabric 等事件可据此拦截/不发送到聊天）
     */
    public static boolean handleCommand(String fullCommand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        String stripped = fullCommand.trim();
        if (!stripped.startsWith("/voiceblock")) return false;
        stripped = stripped.substring("/voiceblock".length()).trim();

        if (stripped.isEmpty()) {
            sendFeedback(mc, "§6=== VoiceBlock 命令 ===");
            sendFeedback(mc, "/voiceblock debug   显示最近一次 N-best 候选");
            sendFeedback(mc, "/voiceblock bind <序号> [方块ID]   绑定候选到方块（不写 ID 用当前手持）");
            sendFeedback(mc, "/voiceblock list   列出所有手动绑定");
            sendFeedback(mc, "/voiceblock remove <拼音>   删除一条绑定");
            sendFeedback(mc, "/voiceblock dedup   扫冲突 + 输出报告");
            return true;
        }

        String[] parts = stripped.split("\\s+", 3);
        String sub = parts[0].toLowerCase();

        switch (sub) {
            case "debug" -> handleDebug(mc);
            case "bind" -> {
                if (parts.length < 2) {
                    sendFeedback(mc, "§c用法: /voiceblock bind <候选序号> [方块ID]");
                    return true;
                }
                String targetId = parts.length >= 3 ? parts[2] : null;
                handleBind(mc, parts[1], targetId);
            }
            case "list" -> handleList(mc);
            case "remove" -> {
                if (parts.length < 2) {
                    sendFeedback(mc, "§c用法: /voiceblock remove <拼音>");
                    return true;
                }
                handleRemove(mc, parts[1]);
            }
            case "dedup" -> handleDedup(mc);
            case "reload" -> {
                ManualOverrides.getInstance().reload();
                sendFeedback(mc, "§a已重新加载 manual_overrides.json");
            }
            default -> sendFeedback(mc, "§c未知子命令: " + sub + "（输入 /voiceblock 查看帮助）");
        }
        return true;
    }

    private static void handleDebug(Minecraft mc) {
        RecognitionResult r = lastResult;
        if (r == null || r.alternatives().isEmpty()) {
            sendFeedback(mc, "§e没有可用的识别结果，请先按住触发键说一句话");
            return;
        }
        long age = (System.currentTimeMillis() - lastResultTimestamp) / 1000;
        sendFeedback(mc, "§6=== 最近一次识别（" + age + " 秒前）===");
        List<String> alts = r.alternatives();
        for (int i = 0; i < alts.size(); i++) {
            String text = alts.get(i);
            String pinyin = PinyinUtil.toPinyin(text.replaceAll("\\s+", ""));
            sendFeedback(mc, "§f  #" + (i + 1) + " §7" + text + " §8(拼音: " + pinyin + ")");
        }
    }

    private static void handleBind(Minecraft mc, String indexStr, String targetId) {
        RecognitionResult r = lastResult;
        if (r == null || r.alternatives().isEmpty()) {
            sendFeedback(mc, "§c没有可用的识别结果，请先按住触发键说一句话");
            return;
        }
        int idx;
        try {
            idx = Integer.parseInt(indexStr) - 1;
        } catch (NumberFormatException e) {
            sendFeedback(mc, "§c候选序号必须是数字");
            return;
        }
        if (idx < 0 || idx >= r.alternatives().size()) {
            sendFeedback(mc, "§c候选序号超出范围 (1-" + r.alternatives().size() + ")");
            return;
        }
        String text = r.alternatives().get(idx);
        String pinyin = PinyinUtil.toPinyin(text.replaceAll("\\s+", ""));
        if (pinyin.isEmpty()) {
            sendFeedback(mc, "§c该候选为空或不含中文，无法生成拼音");
            return;
        }

        // 解析 targetId：不写或空白时用当前手持物品的 ID
        String resolvedId;
        if (targetId == null || targetId.isBlank()) {
            ItemStack held = mc.player.getInventory().getItem(mc.player.getInventory().selected);
            if (held.isEmpty()) {
                sendFeedback(mc, "§c请手持一个物品（不写 ID 时使用当前手持作为目标）");
                return;
            }
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(held.getItem());
            if (rl == null) {
                sendFeedback(mc, "§c无法解析手持物品 ID");
                return;
            }
            resolvedId = rl.toString();
        } else {
            resolvedId = targetId;
        }

        boolean added = ManualOverrides.getInstance().addOrUpdate(pinyin, resolvedId, null);
        if (added) {
            sendFeedback(mc, "§a已添加绑定: §f" + pinyin + " §7-> §f" + resolvedId);
        } else {
            sendFeedback(mc, "§a已覆盖绑定: §f" + pinyin + " §7-> §f" + resolvedId);
        }
    }

    private static void handleList(Minecraft mc) {
        List<ManualOverrides.Rule> rules = ManualOverrides.getInstance().getRules();
        if (rules.isEmpty()) {
            sendFeedback(mc, "§e暂无手动绑定");
            return;
        }
        sendFeedback(mc, "§6=== 手动绑定 (" + rules.size() + " 条) ===");
        for (ManualOverrides.Rule r : rules) {
            sendFeedback(mc, "§7  " + r.triggerPinyin() + " §f-> §7" + r.targetId());
        }
    }

    private static void handleRemove(Minecraft mc, String pinyin) {
        if (ManualOverrides.getInstance().remove(pinyin)) {
            sendFeedback(mc, "§a已删除: §f" + pinyin);
        } else {
            sendFeedback(mc, "§c未找到: " + pinyin);
        }
    }

    private static void handleDedup(Minecraft mc) {
        sendFeedback(mc, "§6开始扫描冲突...");
        List<DedupScanner.ConflictGroup> conflicts = DedupScanner.scan();
        Path reportFile = DedupScanner.writeReport(conflicts);
        sendFeedback(mc, "§a扫描完成，发现 §f" + conflicts.size() + " §a处冲突");
        if (reportFile != null) {
            sendFeedback(mc, "§a报告已写入: §7" + reportFile.toAbsolutePath());
        } else {
            sendFeedback(mc, "§c报告写入失败，查看日志");
        }
    }

    private static void sendFeedback(Minecraft mc, String msg) {
        mc.player.displayClientMessage(Component.literal(msg), false);
    }
}
