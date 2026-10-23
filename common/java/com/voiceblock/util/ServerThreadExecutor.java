package com.voiceblock.util;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 把一段任务放到 MinecraftServer 主线程上跑，并阻塞等待结果。
 * <p>
 * <b>为什么需要这个</b>：VoiceBlock 的语音识别在 {@code VoiceBlock-Recognize} 线程，
 * 玩家的 {@code SourceServerAccess.getServerPlayer()} 拿到的是 ServerPlayer 对象。
 * 但 ServerLevel 的 chunk 系统（{@code getBlockEntity(pos)}）不是线程安全的，
 * 在客户端线程直接调会拿不到数据（BE 可能为 null，或者 BE 的内容是空壳）。
 * 所有需要扫世界 BE 的源（nearbyChest / tomsStorage）都必须把扫描 dispatch 到服务端线程。
 *
 * <p>单玩家下 MinecraftServer 是 always available；多人下如果 server 为 null 就走 fallback。</p>
 */
public final class ServerThreadExecutor {

    private static final long TIMEOUT_SECONDS = 5;

    private ServerThreadExecutor() {}

    public static <T> T run(MinecraftServer server, Callable<T> task, T fallback) {
        if (server == null) {
            VoiceBlockMod.LOGGER.debug("[VoiceBlock] ServerThreadExecutor: server is null");
            return fallback;
        }

        // 已经在服务端线程上就直接跑
        if (Thread.currentThread() == server.getRunningThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                VoiceBlockMod.LOGGER.warn("[VoiceBlock] ServerThreadExecutor task failed", e);
                return fallback;
            }
        }

        // 跨线程：dispatch 到服务端线程，阻塞等结果（带超时）
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.warn("[VoiceBlock] ServerThreadExecutor task failed", t);
                future.complete(fallback);
            }
        });
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] ServerThreadExecutor timeout after {}s", TIMEOUT_SECONDS);
            return fallback;
        }
    }
}