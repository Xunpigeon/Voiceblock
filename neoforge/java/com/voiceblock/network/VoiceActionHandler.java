package com.voiceblock.network;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.SourceManager;
import com.voiceblock.source.SourceResult;
import com.voiceblock.util.ItemResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 双向网络包处理器（NeoForge 1.21.1 实现）。
 *
 * <ul>
 *   <li>{@code handleDataOnServer}：服务端收到客户端识别结果后调
 *       {@code SourceManager.takeAny(serverPlayer, id, amount)}，把 SourceResult
 *       回发到客户端。</li>
 *   <li>{@code handleDataOnClient}：客户端收到服务端回包后做 HUD / 聊天 / 音效反馈。
 *       （当前主要在聊天显示；HUD 显示由 ClientEvents 已经做好的逻辑触发。）</li>
 * </ul>
 *
 * <p>关键：{@code ctx.player().asServerPlayer()} 在客户端会抛错，所以两个方向必须分开。</p>
 */
public final class VoiceActionHandler {

    public static void handleDataOnServer(final VoiceActionPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // ctx.player() 对服务端包来说就是发送方 ServerPlayer，可直接强转
            net.minecraft.world.entity.player.Player p = ctx.player();
            if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) {
                VoiceBlockMod.LOGGER.warn("[VoiceBlock] voice action without server player, dropped");
                return;
            }

            String id = payload.id();
            int amount = payload.amount();
            VoiceBlockMod.LOGGER.info("[VoiceBlock] server got voice action from '{}': '{}' x{} (creative={})",
                    sp.getGameProfile().getName(), id, amount, sp.isCreative());

            int requested = sp.isCreative() ? 1 : amount;
            Item item = ItemResolver.resolve(id);
            if (item == null) {
                ctx.reply(new VoiceActionResultPayload(false, 0, "未找到物品: " + id, id));
                return;
            }

            String displayName = Component.translatable(item.getDescriptionId()).getString();

            SourceResult result = SourceManager.getInstance().takeAny(sp, id, requested);
            VoiceBlockMod.LOGGER.info("[VoiceBlock] server takeAny '{}' x{} -> success={} amount={} reason={}",
                    id, requested, result.success(), result.amountTaken(), result.reason());

            ctx.reply(new VoiceActionResultPayload(
                    result.success(),
                    result.amountTaken(),
                    result.reason(),
                    displayName));
        });
    }

    public static void handleDataOnClient(final VoiceActionResultPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            String name = payload.displayName() == null ? "" : payload.displayName();
            Component line;
            if (payload.success()) {
                line = Component.literal("已获取: " + name + " x" + payload.amountTaken());
            } else if (payload.reason() != null) {
                line = Component.literal(payload.reason() + ": " + name);
            } else {
                line = Component.literal("未找到: " + name);
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(line, true);
            }
            VoiceBlockMod.LOGGER.info("[VoiceBlock] client got result: success={} amount={} reason={} name={}",
                    payload.success(), payload.amountTaken(), payload.reason(), name);
        });
    }

    private VoiceActionHandler() {}
}
