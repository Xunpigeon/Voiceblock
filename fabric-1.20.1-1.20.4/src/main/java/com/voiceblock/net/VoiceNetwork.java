package com.voiceblock.net;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.action.VoiceActionService;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric 1.20.1（旧版 networking API：ResourceLocation + FriendlyByteBuf）的服务端通道。
 *
 * <p>C2S {@code voiceblock:voice_action} 带 {@code id + amount}，服务端在主线程执行
 * {@link VoiceActionService#execute}，再通过 {@code voiceblock:voice_result} 回包。</p>
 *
 * <p><b>本类只在服务端/公共侧加载</b>：不要在这里引用 {@code net.minecraft.client.*}
 * 或 fabric 的 {@code api.client.*}。客户端那一半在 {@link VoiceClientNetwork}。</p>
 */
public final class VoiceNetwork {

    public static final ResourceLocation CHANNEL_ACTION = new ResourceLocation("voiceblock", "voice_action");
    public static final ResourceLocation CHANNEL_RESULT = new ResourceLocation("voiceblock", "voice_result");

    private VoiceNetwork() {}

    /** 服务端接收器：两端都注册，实际只有服务端会收到包。 */
    public static void registerCommon() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL_ACTION, (server, player, handler, buf, responseSender) -> {
            String id;
            int amount;
            try {
                id = buf.readUtf();
                amount = buf.readVarInt();
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.error("[VoiceBlock] malformed voice action packet", t);
                return;
            }
            // 取物必须回主线程：容器 / 方块实体都不是线程安全的
            server.execute(() -> {
                VoiceActionService.Outcome outcome = VoiceActionService.execute(player, id, amount);
                sendResult(player, outcome);
            });
        });
        VoiceBlockMod.LOGGER.info("[VoiceBlock] fabric network registered (1.20.1): c2s={}", CHANNEL_ACTION);
    }

    /** 服务端 -> 客户端回包。 */
    public static void sendResult(ServerPlayer player, VoiceActionService.Outcome outcome) {
        try {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(outcome.success());
            buf.writeVarInt(outcome.amountTaken());
            buf.writeUtf(outcome.reason() == null ? "" : outcome.reason());
            buf.writeUtf(outcome.displayName() == null ? "" : outcome.displayName());
            ServerPlayNetworking.send(player, CHANNEL_RESULT, buf);
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] failed to send voice result packet", t);
        }
    }
}
