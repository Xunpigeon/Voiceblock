package com.voiceblock.net;

import com.voiceblock.VoiceBlockMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Fabric 1.21 客户端侧通道。
 *
 * <p>只在客户端加载（引用 {@code api.client.*}）。</p>
 */
public final class VoiceClientNetwork {

    private VoiceClientNetwork() {}

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(VoiceResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> VoiceResultHandler.accept(
                        payload.success(),
                        payload.amount(),
                        payload.reason() == null || payload.reason().isEmpty() ? null : payload.reason(),
                        payload.displayName() == null || payload.displayName().isEmpty() ? null : payload.displayName())));
    }

    public static boolean canSend() {
        try {
            return ClientPlayNetworking.canSend(VoiceActionPayload.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void sendAction(String itemId, int amount) {
        ClientPlayNetworking.send(new VoiceActionPayload(itemId, amount));
    }
}
