package com.voiceblock.network;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.SourceManager;
import com.voiceblock.source.SourceResult;
import com.voiceblock.util.ItemResolver;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端的语音取物指令包（Forge 1.20.1）。
 *
 * <p>客户端识别 + 匹配后，把目标物品 ID 与数量发到服务端；服务端拿到真实的
 * {@link ServerPlayer} 后调用 {@link SourceManager#takeAny} 执行取物，再把
 * {@link VoiceActionResultPacket} 回发。整条链跑在服务端线程，权威状态唯一。</p>
 */
public class VoiceActionPacket {

    private final String id;
    private final int amount;

    public VoiceActionPacket(String id, int amount) {
        this.id = id;
        this.amount = amount;
    }

    public static void encode(VoiceActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id == null ? "" : msg.id);
        buf.writeVarInt(msg.amount);
    }

    public static VoiceActionPacket decode(FriendlyByteBuf buf) {
        return new VoiceActionPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(VoiceActionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sp = ctx.getSender();
        if (sp == null) {
            // C2S 包在服务端处理时必定有 sender；为空直接丢弃，避免 NPE 崩服
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] voice action without sender, dropped");
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            try {
                String id = msg.id;
                int amount = msg.amount;
                VoiceBlockMod.LOGGER.info("[VoiceBlock] server got voice action from '{}': '{}' x{} (creative={})",
                        sp.getGameProfile().getName(), id, amount, sp.isCreative());

                int requested = sp.isCreative() ? 1 : amount;
                Item item = ItemResolver.resolve(id);
                if (item == null) {
                    reply(sp, new VoiceActionResultPacket(false, 0, "未找到物品: " + id, id));
                    return;
                }

                String displayName = Component.translatable(item.getDescriptionId()).getString();
                SourceResult result = SourceManager.getInstance().takeAny(sp, id, requested);
                VoiceBlockMod.LOGGER.info("[VoiceBlock] server takeAny '{}' x{} -> success={} amount={} reason={}",
                        id, requested, result.success(), result.amountTaken(), result.reason());

                reply(sp, new VoiceActionResultPacket(
                        result.success(), result.amountTaken(), result.reason(), displayName));
            } catch (Throwable t) {
                // 取物链路任意异常都不能让服务端崩，降级为「未找到」回包
                VoiceBlockMod.LOGGER.error("[VoiceBlock] server voice action failed: " + msg.id, t);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void reply(ServerPlayer sp, VoiceActionResultPacket packet) {
        try {
            VoiceNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), packet);
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] failed to send result packet", t);
        }
    }
}
