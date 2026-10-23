package com.voiceblock.net;

import com.voiceblock.VoiceBlockMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Fabric 1.20.1 客户端侧通道：发请求 + 收回包。
 *
 * <p>只在客户端加载（引用 {@code api.client.*}）。服务端不会碰这个类。</p>
 */
public final class VoiceClientNetwork {

    private VoiceClientNetwork() {}

    /** 注册 S2C 回包接收器。 */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(VoiceNetwork.CHANNEL_RESULT, (client, handler, buf, responseSender) -> {
            boolean success;
            int amount;
            String reason;
            String displayName;
            try {
                success = buf.readBoolean();
                amount = buf.readVarInt();
                reason = buf.readUtf();
                displayName = buf.readUtf();
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.error("[VoiceBlock] malformed voice result packet", t);
                return;
            }
            client.execute(() -> VoiceResultHandler.accept(
                    success, amount,
                    reason == null || reason.isEmpty() ? null : reason,
                    displayName == null || displayName.isEmpty() ? null : displayName));
        });
    }

    /** 服务端是否装了本模组并注册了通道。 */
    public static boolean canSend() {
        try {
            return ClientPlayNetworking.canSend(VoiceNetwork.CHANNEL_ACTION);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把「取物请求」发给服务端。 */
    public static void sendAction(String itemId, int amount) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(itemId);
        buf.writeVarInt(amount);
        ClientPlayNetworking.send(VoiceNetwork.CHANNEL_ACTION, buf);
    }
}
