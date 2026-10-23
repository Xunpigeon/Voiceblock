package com.voiceblock.source;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端专用桥接：把 LocalPlayer 映射到集成服务器（单人 / 局域网主机）上的 ServerPlayer。
 *
 * <p><b>只有这个类允许引用 {@code net.minecraft.client.*}</b>。它由
 * {@link SourceServerAccess} 用字符串反射调用，所以专用服务器永远不会加载到它 ——
 * 否则 Forge 的 RuntimeDistCleaner 会以
 * "Attempted to load class net/minecraft/client/... for invalid dist DEDICATED_SERVER" 抛错。</p>
 *
 * <p>必须是 {@code public} 且方法 {@code public}：反射穿过包边界调用。</p>
 */
public final class ClientIntegratedServerLookup {

    public static ServerPlayer lookup(Player clientPlayer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return null; // 真正的多人游戏：客户端没有集成服务器
        return server.getPlayerList().getPlayer(clientPlayer.getUUID());
    }

    private ClientIntegratedServerLookup() {}
}
