package com.voiceblock.net;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.action.VoiceActionService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Fabric 1.21（{@link net.minecraft.network.protocol.common.custom.CustomPacketPayload} 新 API）的服务端通道。
 *
 * <p>payload 类型必须在两端都注册，而且要在注册 handler 之前完成，
 * 否则握手阶段会报 "unregistered payload"。</p>
 *
 * <p><b>本类不得引用客户端类型</b>（{@code net.minecraft.client.*} / {@code api.client.*}）。</p>
 */
public final class VoiceNetwork {

    private VoiceNetwork() {}

    public static void registerCommon() {
        // 先注册 payload 类型（两端都要），再注册接收器
        PayloadTypeRegistry.playC2S().register(VoiceActionPayload.TYPE, VoiceActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VoiceResultPayload.TYPE, VoiceResultPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(VoiceActionPayload.TYPE, (payload, context) -> {
            // 取物必须回主线程
            context.server().execute(() -> {
                VoiceActionService.Outcome outcome =
                        VoiceActionService.execute(context.player(), payload.id(), payload.amount());
                try {
                    context.responseSender().sendPacket(VoiceResultPayload.of(
                            outcome.success(), outcome.amountTaken(), outcome.reason(), outcome.displayName()));
                } catch (Throwable t) {
                    VoiceBlockMod.LOGGER.error("[VoiceBlock] failed to send voice result payload", t);
                }
            });
        });

        VoiceBlockMod.LOGGER.info("[VoiceBlock] fabric network registered (1.21): c2s={}",
                VoiceActionPayload.TYPE.id());
    }
}
