package com.voiceblock.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 取物结果（Fabric 1.21）。
 *
 * <p>可空字段统一编码为空串，解码后由客户端还原成 {@code null} ——
 * 直接写 null 会让 codec 抛异常并踢人。</p>
 */
public record VoiceResultPayload(boolean success, int amount, String reason, String displayName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VoiceResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("voiceblock", "voice_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, VoiceResultPayload::success,
            ByteBufCodecs.VAR_INT, VoiceResultPayload::amount,
            ByteBufCodecs.STRING_UTF8, VoiceResultPayload::reason,
            ByteBufCodecs.STRING_UTF8, VoiceResultPayload::displayName,
            VoiceResultPayload::new);

    public static VoiceResultPayload of(boolean success, int amount, String reason, String displayName) {
        return new VoiceResultPayload(success, amount, reason == null ? "" : reason,
                displayName == null ? "" : displayName);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
