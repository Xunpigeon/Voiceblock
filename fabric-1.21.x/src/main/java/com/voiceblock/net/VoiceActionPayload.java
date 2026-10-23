package com.voiceblock.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S 取物请求（Fabric 1.21，{@link CustomPacketPayload} 新 API）。
 *
 * <p>客户端识别并匹配出物品 ID 后，只把 {@code id + amount} 发到服务端；
 * 服务端拿到真正的 ServerPlayer 后执行取物。客户端不参与容器读取。</p>
 */
public record VoiceActionPayload(String id, int amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VoiceActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("voiceblock", "voice_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VoiceActionPayload::id,
            ByteBufCodecs.VAR_INT, VoiceActionPayload::amount,
            VoiceActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
