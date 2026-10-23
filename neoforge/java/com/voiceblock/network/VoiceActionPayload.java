package com.voiceblock.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端的语音取物指令包。
 *
 * <p>客户端识别到语音并完成 BlockMatcher 匹配后，把目标方块 / 物品 ID 装到这里发到服务端，
 * 服务端的 {@code VoiceActionHandler} 拿到 {@code ServerPlayer} 后调用
 * {@code SourceManager.takeAny(...)} 执行取物。整条链跑在服务端线程，权威状态唯一。</p>
 *
 * <p>字段尽量小：</p>
 * <ul>
 *   <li>id — 来自匹配器的 ResourceLocation-like 字符串（{@code minecraft:chest}）</li>
 *   <li>amount — 想要的数量。创意模式强制为 1，生存模式按物品单组最大堆叠数取。</li>
 * </ul>
 */
public record VoiceActionPayload(String id, int amount) implements CustomPacketPayload {

    public static final Type<VoiceActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("voiceblock", "voice_action"));

    public static final StreamCodec<FriendlyByteBuf, VoiceActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, VoiceActionPayload::id,
                    ByteBufCodecs.VAR_INT, VoiceActionPayload::amount,
                    VoiceActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
