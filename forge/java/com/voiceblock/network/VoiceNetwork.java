package com.voiceblock.network;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 语音取物包通道（Forge 1.20.1 实现）。
 *
 * <p>NeoForge 用 {@code RegisterPayloadHandlersEvent} + {@code CustomPacketPayload}，
 * Forge 1.20.1 没有这套 API，改用经典的 {@link SimpleChannel} + 手写 encode/decode。
 * 两条包：</p>
 * <ul>
 *   <li>{@link VoiceActionPacket}：客户端 → 服务端，带上识别到的方块/物品 ID 与数量；</li>
 *   <li>{@link VoiceActionResultPacket}：服务端 → 客户端，回传取物结果。</li>
 * </ul>
 *
 * <p>{@link #register()} 必须在 mod 构造期（网络注册表上锁前）调用一次。</p>
 */
public final class VoiceNetwork {

    /** 协议版本：两端不一致时 Forge 会拒绝连接，避免新旧版本混用导致包解析错乱。 */
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VoiceBlockMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, VoiceActionPacket.class,
                VoiceActionPacket::encode, VoiceActionPacket::decode, VoiceActionPacket::handle);
        CHANNEL.registerMessage(nextId++, VoiceActionResultPacket.class,
                VoiceActionResultPacket::encode, VoiceActionResultPacket::decode, VoiceActionResultPacket::handle);
        VoiceBlockMod.LOGGER.info("[VoiceBlock] network channel registered (forge, protocol {})", PROTOCOL_VERSION);
    }

    private VoiceNetwork() {}
}
