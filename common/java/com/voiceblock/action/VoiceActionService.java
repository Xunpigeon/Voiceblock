package com.voiceblock.action;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.SourceManager;
import com.voiceblock.source.SourceResult;
import com.voiceblock.util.ItemResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * 服务端权威取物执行器：客户端只发「物品 ID + 数量」，真正的取物在这里发生。
 *
 * <p>抽到 common 是为了让 Forge / NeoForge / Fabric 三条网络实现共用同一段逻辑，
 * 避免各平台各写一份、行为漂移（以前 Fabric 就是因为根本没有服务端通道，
 * 才只能在客户端读容器副本，联机时读不到东西）。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>只能在服务端主线程调用（方块 / 方块实体 / 容器都不是线程安全的）。</li>
 *   <li>任何异常都必须吞掉并返回失败，视障玩家不能因为一次取物把服务器搞崩。</li>
 * </ul>
 */
public final class VoiceActionService {

    /** 取物结果，字段与网络回包一一对应。 */
    public record Outcome(boolean success, int amountTaken, String reason, String displayName) {}

    private VoiceActionService() {}

    /**
     * @param sp        发起请求的服务端玩家
     * @param id        物品 / 方块 ID，如 {@code minecraft:chest}
     * @param requested 客户端希望的数量；创造模式会被强制为 1（与原逻辑一致）
     */
    public static Outcome execute(ServerPlayer sp, String id, int requested) {
        if (sp == null) {
            return new Outcome(false, 0, "服务端玩家不可用", id);
        }
        try {
            int amount = sp.isCreative() ? 1 : Math.max(1, requested);

            Item item = ItemResolver.resolve(id);
            if (item == null) {
                VoiceBlockMod.LOGGER.warn("[VoiceBlock] action: unknown item id '{}'", id);
                return new Outcome(false, 0, "未找到物品: " + id, id);
            }

            String displayName;
            try {
                displayName = Component.translatable(item.getDescriptionId()).getString();
            } catch (Throwable t) {
                displayName = id;
            }

            SourceResult result = SourceManager.getInstance().takeAny(sp, id, amount);
            VoiceBlockMod.LOGGER.info(
                    "[VoiceBlock] action '{}' x{} -> success={} amount={} reason={}",
                    id, amount, result.success(), result.amountTaken(), result.reason());

            return new Outcome(result.success(), result.amountTaken(), result.reason(), displayName);
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] action failed for '" + id + "'", t);
            return new Outcome(false, 0, "取物失败: " + t.getClass().getSimpleName(), id);
        }
    }
}
