package com.voiceblock.network;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端的取物结果回包（Forge 1.20.1）。
 *
 * <ul>
 *   <li>{@code success} — 是否取到物品</li>
 *   <li>{@code amountTaken} — 实际取到的数量（0 表示未找到）</li>
 *   <li>{@code reason} — 失败原因（"未找到该物品"、"身上已满"，可空）</li>
 *   <li>{@code displayName} — 玩家友好的中文名，可空</li>
 * </ul>
 *
 * <p><b>可空字段必须单独编码</b>：成功时 {@code reason} 就是 null，直接
 * {@code writeUtf(null)} 会抛 NPE → netty {@code EncoderException} → 服务器踢人。
 * 所以这里用一个 boolean 标志位标记 null。</p>
 */
public class VoiceActionResultPacket {

    private final boolean success;
    private final int amountTaken;
    private final String reason;
    private final String displayName;

    public VoiceActionResultPacket(boolean success, int amountTaken, String reason, String displayName) {
        this.success = success;
        this.amountTaken = amountTaken;
        this.reason = reason;
        this.displayName = displayName;
    }

    public static void encode(VoiceActionResultPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.success);
        buf.writeVarInt(msg.amountTaken);
        writeNullableUtf(buf, msg.reason);
        writeNullableUtf(buf, msg.displayName);
    }

    public static VoiceActionResultPacket decode(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        int amountTaken = buf.readVarInt();
        String reason = readNullableUtf(buf);
        String displayName = readNullableUtf(buf);
        return new VoiceActionResultPacket(success, amountTaken, reason, displayName);
    }

    public static void handle(VoiceActionResultPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // S2C 包只在客户端处理；服务端收到（不该发生）直接忽略，不触碰任何客户端类
        if (ctx.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
            ctx.enqueueWork(() -> {
                try {
                    String name = msg.displayName == null ? "" : msg.displayName;
                    Component line;
                    if (msg.success) {
                        line = Component.literal("已获取: " + name + " x" + msg.amountTaken);
                    } else if (msg.reason != null) {
                        line = Component.literal(msg.reason + ": " + name);
                    } else {
                        line = Component.literal("未找到: " + name);
                    }
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(line, true);
                    }
                    VoiceBlockMod.LOGGER.info("[VoiceBlock] client got result: success={} amount={} reason={} name={}",
                            msg.success, msg.amountTaken, msg.reason, name);
                } catch (Throwable t) {
                    VoiceBlockMod.LOGGER.error("[VoiceBlock] client result handling failed", t);
                }
            });
        }
        ctx.setPacketHandled(true);
    }

    private static void writeNullableUtf(FriendlyByteBuf buf, String value) {
        if (value == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(value);
        }
    }

    private static String readNullableUtf(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
