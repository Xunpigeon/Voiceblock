package com.voiceblock.net;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.SourceManager;
import com.voiceblock.source.SourceResult;
import com.voiceblock.util.InventoryUtil;
import com.voiceblock.util.ItemResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * 客户端取物入口：优先走服务端权威通道，服务端没装本模组时降级为本地取物。
 *
 * <p>为什么必须优先走服务端：末影箱 / 周围箱子 / 精妙背包 / AE2 / 汤姆储存的真实内容
 * 只在服务端存在，客户端拿到的是"空壳副本"，读了也是空的。降级路径只能取玩家自己背包，
 * 但至少不会让纯客户端用户完全没法用。</p>
 */
public final class VoiceRequest {

    private VoiceRequest() {}

    /** 按当前游戏模式自动决定数量（创造 1 个，生存取该物品一整组）。 */
    public static void request(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        int desired;
        if (mc.player.isCreative()) {
            desired = 1;
        } else {
            Item item = ItemResolver.resolve(itemId);
            desired = item != null ? InventoryUtil.getDefaultMaxStackSize(item) : 1;
        }
        request(itemId, desired);
    }

    /** 指定数量（创造模式仍会被服务端压成 1）。 */
    public static void request(String itemId, int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || itemId == null || itemId.isEmpty()) return;

        int desired = mc.player.isCreative() ? 1 : Math.max(1, amount);

        if (VoiceClientNetwork.canSend()) {
            try {
                VoiceClientNetwork.sendAction(itemId, desired);
                VoiceBlockMod.LOGGER.info("[VoiceBlock] sent voice action '{}' x{} to server", itemId, desired);
                return;
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.error("[VoiceBlock] failed to send voice action, falling back to local", t);
            }
        }

        // 降级：服务端没装 VoiceBlock（纯净服 / 只装客户端）
        // 本机能拿到的只有玩家自己的背包；容器类物品源在这里拿不到真实内容。
        VoiceBlockMod.LOGGER.warn("[VoiceBlock] server has no voiceblock channel; local fallback for '{}'", itemId);
        SourceResult sr;
        try {
            sr = SourceManager.getInstance().takeAny(mc.player, itemId, desired);
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] local fallback failed", t);
            mc.player.displayClientMessage(Component.literal("\u53d6\u7269\u5931\u8d25: " + t.getClass().getSimpleName()), true);
            return;
        }

        if (sr.success()) {
            mc.player.displayClientMessage(Component.literal("\u5df2\u83b7\u53d6: " + itemId + " x" + sr.amountTaken()), true);
        } else {
            String hint = sr.reason() == null ? "\u672a\u627e\u5230: " + itemId : sr.reason();
            mc.player.displayClientMessage(
                    Component.literal(hint + "\uff08\u670d\u52a1\u5668\u672a\u88c5 VoiceBlock\uff0c\u53ea\u80fd\u53d6\u8eab\u4e0a\u7269\u54c1\uff09"), true);
        }
    }
}
