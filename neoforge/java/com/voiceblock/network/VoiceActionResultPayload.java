package com.voiceblock.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端的取物结果回包。
 *
 * <p>服务端执行完 {@code SourceManager.takeAny} 后把结果序列化回包：</p>
 * <ul>
 *   <li>success — 是否成功取了至少 0 个</li>
 *   <li>amountTaken — 实际取到的数量（0 表示未找到）</li>
 *   <li>reason — 失败原因（"未找到该物品"、"身上已满" 等，可空）</li>
 *   <li>displayName — 玩家友好的中文名（用于 HUD / 聊天显示），可空</li>
 * </ul>
 *
 * <p>客户端收到这个包后再做 HUD overlay / 聊天 / 音效反馈。识别和取物
 * 都在服务端，意味着视障玩家在自己键鼠开关 + 服务器联机的情况下，
 * 同样的体验统一可访问。</p>
 */
public record VoiceActionResultPayload(
        boolean success,
        int amountTaken,
        String reason,
        String displayName) implements CustomPacketPayload {

    public static final Type<VoiceActionResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("voiceblock", "voice_action_result"));

    /**
     * 可空 UTF-8 字符串编解码器。
     *
     * <p><b>为什么必须自己实现</b>：NeoForge 自带的 {@link ByteBufCodecs#STRING_UTF8}
     * 不接受 null —— 它内部调用 {@code FriendlyByteBuf.writeUtf(null)}，直接抛
     * {@link NullPointerException}。这个 NPE 会让 netty 抛
     * {@code EncoderException: Failed to encode packet 'custom_payload'}，
     * 服务器随即强制断开该玩家的连接（表现为"松键就被踢出服务器"）。</p>
     *
     * <p>取物<b>成功</b>时 {@code reason} 就是 null（没有失败原因），所以这里必须用
     * 可空编码器，用一个 boolean 标志位标记 null，否则每次成功取物都会踢人。</p>
     */
    private static final StreamCodec<FriendlyByteBuf, String> NULLABLE_UTF8 = new StreamCodec<>() {
        @Override
        public String decode(FriendlyByteBuf buf) {
            return buf.readBoolean() ? buf.readUtf() : null;
        }

        @Override
        public void encode(FriendlyByteBuf buf, String value) {
            if (value == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeUtf(value);
            }
        }
    };

    public static final StreamCodec<FriendlyByteBuf, VoiceActionResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, VoiceActionResultPayload::success,
                    ByteBufCodecs.VAR_INT, VoiceActionResultPayload::amountTaken,
                    NULLABLE_UTF8, VoiceActionResultPayload::reason,
                    NULLABLE_UTF8, VoiceActionResultPayload::displayName,
                    VoiceActionResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
