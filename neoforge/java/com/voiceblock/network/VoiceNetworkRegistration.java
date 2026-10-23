package com.voiceblock.network;

import com.voiceblock.VoiceBlockMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 注册语音取物包路径。
 *
 * <p>通过 {@code playBidirectional} 同时注册服务端（C2S）和客户端（S2C）处理器，
 * 服务端实例负责运行 {@link VoiceActionHandler#handleDataOnServer}，
 * 客户端实例负责运行 {@link VoiceActionHandler#handleDataOnClient}。</p>
 */
@EventBusSubscriber(modid = VoiceBlockMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class VoiceNetworkRegistration {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("voiceblock");
        registrar.playBidirectional(
                VoiceActionPayload.TYPE,
                VoiceActionPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    // 根据 side 路由
                    if (ctx.flow().isServerbound()) {
                        VoiceActionHandler.handleDataOnServer(payload, ctx);
                    } else {
                        // Should not happen for the request packet
                        VoiceBlockMod.LOGGER.warn("[VoiceBlock] received request on client side?");
                    }
                });
        registrar.playBidirectional(
                VoiceActionResultPayload.TYPE,
                VoiceActionResultPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.flow().isClientbound()) {
                        VoiceActionHandler.handleDataOnClient(payload, ctx);
                    } else {
                        // Server should never receive result packets
                        VoiceBlockMod.LOGGER.warn("[VoiceBlock] received result on server side?");
                    }
                });
    }
}
