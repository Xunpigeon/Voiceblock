package com.voiceblock.debug;

import com.voiceblock.source.SourceConfig;
import com.voiceblock.source.SourceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * /voiceblock source 命令家族：跨平台共用，命令前缀 /voiceblock source。
 * 各平台 ClientEvents 在命令事件中调用 handleCommand。
 *
 * 子命令:
 *   /voiceblock source                          显示帮助
 *   /voiceblock source list                     列出所有源 + 状态
 *   /voiceblock source enable <名字>            启用
 *   /voiceblock source disable <名字>           禁用
 *   /voiceblock source priority <名字> <数字>   设置优先级
 *   /voiceblock source radius <名字> <数字>     设置搜索半径（仅 nearbyChest）
 */
public class SourceCommands {

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

        if (!stripped.toLowerCase().startsWith("source")) return false;
        stripped = stripped.substring("source".length()).trim();

        if (stripped.isEmpty()) {
            sendHelp(mc);
            return true;
        }

        String[] parts = stripped.split("\\s+", 3);
        String sub = parts[0].toLowerCase();

        switch (sub) {
            case "list" -> handleList(mc);
            case "enable" -> {
                if (parts.length < 2) {
                    sendFeedback(mc, "§c用法: /voiceblock source enable <名字>");
                    return true;
                }
                handleEnable(mc, parts[1], true);
            }
            case "disable" -> {
                if (parts.length < 2) {
                    sendFeedback(mc, "§c用法: /voiceblock source disable <名字>");
                    return true;
                }
                handleEnable(mc, parts[1], false);
            }
            case "priority" -> {
                if (parts.length < 3) {
                    sendFeedback(mc, "§c用法: /voiceblock source priority <名字> <数字>");
                    return true;
                }
                handlePriority(mc, parts[1], parts[2]);
            }
            case "radius" -> {
                if (parts.length < 3) {
                    sendFeedback(mc, "§c用法: /voiceblock source radius <名字> <数字>");
                    return true;
                }
                handleRadius(mc, parts[1], parts[2]);
            }
            case "reset" -> handleReset(mc);
            default -> sendFeedback(mc, "§c未知子命令: " + sub + "（输入 /voiceblock source 查看帮助）");
        }
        return true;
    }

    private static void sendHelp(Minecraft mc) {
        sendFeedback(mc, "§6=== /voiceblock source 命令 ===");
        sendFeedback(mc, "/voiceblock source list                     列出所有源 + 状态");
        sendFeedback(mc, "/voiceblock source enable <名字>            启用");
        sendFeedback(mc, "/voiceblock source disable <名字>           禁用");
        sendFeedback(mc, "/voiceblock source priority <名字> <数字>   设置优先级");
        sendFeedback(mc, "/voiceblock source radius <名字> <数字>     设置搜索半径（仅 nearbyChest）");
        sendFeedback(mc, "/voiceblock source reset                    重置全部为默认值");
    }

    private static void handleList(Minecraft mc) {
        var statuses = SourceManager.getInstance().getStatusList();
        sendFeedback(mc, "§6=== 物品源 (" + statuses.size() + ") ===");
        for (var s : statuses) {
            SourceConfig c = s.config();
            String name = s.name();
            String status = s.enabled() ? "§a开" : "§c关";
            String avail = s.available() ? "§a可用" : "§8未装";
            String extra = (c != null && c.radius() != null) ? " 半径=" + c.radius() : "";
            int pri = c != null ? c.priority() : 0;
            sendFeedback(mc, String.format("§f  %-12s §7[%s] §7[%s] pri=%d%s", name, status, avail, pri, extra));
        }
    }

    private static void handleEnable(Minecraft mc, String name, boolean enabled) {
        boolean ok = SourceManager.getInstance().configs().setEnabled(name, enabled);
        if (ok) {
            sendFeedback(mc, "§a已" + (enabled ? "启用" : "禁用") + ": §f" + name);
        } else {
            sendFeedback(mc, "§c未找到源: " + name);
        }
    }

    private static void handlePriority(Minecraft mc, String name, String value) {
        int p;
        try {
            p = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            sendFeedback(mc, "§c优先级必须是数字");
            return;
        }
        boolean ok = SourceManager.getInstance().configs().setPriority(name, p);
        if (ok) {
            sendFeedback(mc, "§a已设置 " + name + " 优先级 = §f" + p);
        } else {
            sendFeedback(mc, "§c未找到源: " + name);
        }
    }

    private static void handleRadius(Minecraft mc, String name, String value) {
        int r;
        try {
            r = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            sendFeedback(mc, "§c半径必须是数字");
            return;
        }
        boolean ok = SourceManager.getInstance().configs().setRadius(name, r);
        if (ok) {
            sendFeedback(mc, "§a已设置 " + name + " 半径 = §f" + r);
        } else {
            sendFeedback(mc, "§c未找到源: " + name);
        }
    }

    /** 重置所有源配置到默认值（写文件）。需要重启 mod 才能完全生效。 */
    private static void handleReset(Minecraft mc) {
        try {
            java.nio.file.Path cfg = java.nio.file.Paths.get("config", "voiceblock", "sources.json");
            if (java.nio.file.Files.exists(cfg)) {
                java.nio.file.Files.delete(cfg);
            }
            sendFeedback(mc, "§a已删除 sources.json，下次启动 mod 时将使用默认值重建。");
            sendFeedback(mc, "§7提示: 退到主菜单再进游戏一次即可生效。");
        } catch (Exception e) {
            sendFeedback(mc, "§c重置失败: " + e.getMessage());
        }
    }

    private static void sendFeedback(Minecraft mc, String msg) {
        mc.player.displayClientMessage(Component.literal(msg), false);
    }
}