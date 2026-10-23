package com.voiceblock.source.impl;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SourceConfig;
import com.voiceblock.source.SourceManager;
import com.voiceblock.source.SourceResult;
import com.voiceblock.source.SourceServerAccess;
import com.voiceblock.util.InventoryPlacer;
import com.voiceblock.util.ItemResolver;
import com.voiceblock.util.ServerThreadExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 周围箱子源（默认关，优先级 7，半径可配默认 16 格）。
 * 扫描范围内 ChestBlockEntity + BarrelBlockEntity + 任何实现 Container 的 BE（兜底），按距离近→远取物。
 *
 * <p><b>跨线程</b>：语音识别在 {@code VoiceBlock-Recognize} 线程，BE 扫描必须
 * 通过 {@link ServerThreadExecutor} 分发到 {@link MinecraftServer} 主线程，
 * 否则 {@code ServerLevel.getBlockEntity()} 在客户端线程调用 chunk 系统拿不到正确数据。</p>
 */
public class SourceNearbyChest implements ItemSource {

    @Override
    public String name() { return "nearbyChest"; }

    @Override
    public Component displayName() { return Component.literal("周围箱子"); }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public SourceResult take(Player player, String itemId, int desiredAmount) {
        Item target = ItemResolver.resolve(itemId);
        if (target == null) return SourceResult.notHandled();

        ServerPlayer sp = SourceServerAccess.getServerPlayer(player);
        if (sp == null) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] nearbyChest: 服务端玩家不可用（多人模式？），跳过");
            return SourceResult.notHandled();
        }
        MinecraftServer server = sp.getServer();
        ServerLevel level = sp.serverLevel();
        BlockPos center = sp.blockPosition();

        SourceConfig cfg = SourceManager.getInstance().configs().get(name());
        final int radius = (cfg != null && cfg.radius() != null) ? cfg.radius() : 16;

        // ★ 关键：BE 扫描必须放到服务端线程
        // 扫描结果：含 target 的 BEs + 诊断计数（总 BE 数、Container 数、找到的几个）
        record ScanResult(List<FoundBE> found, int totalBes, int containerBes, int chestBes, int barrelBes) {}

        ScanResult result = ServerThreadExecutor.run(server, () -> {
            List<FoundBE> found = new ArrayList<>();
            AtomicInteger total = new AtomicInteger();
            AtomicInteger containers = new AtomicInteger();
            AtomicInteger chests = new AtomicInteger();
            AtomicInteger barrels = new AtomicInteger();

            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius))) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null) continue;
                total.incrementAndGet();
                if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) chests.incrementAndGet();
                if (be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) barrels.incrementAndGet();
                if (be instanceof Container) {
                    Container container = (Container) be;
                    containers.incrementAndGet();
                    if (inventoryContains(container, target)) {
                        found.add(new FoundBE(container, pos.distSqr(center)));
                    }
                }
            }
            return new ScanResult(found, total.get(), containers.get(), chests.get(), barrels.get());
        }, new ScanResult(List.of(), 0, 0, 0, 0));

        // 诊断：扫了多少 BE、里面多少是 Container/箱子/桶
        if (result.totalBes() == 0) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] nearbyChest: 半径 {} 内一个 BE 都没扫到（chunk 没加载？服务端线程问题？）", radius);
        } else {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] nearbyChest: 半径 {} 扫到 {} 个 BE（{} ChestBlockEntity / {} BarrelBlockEntity / {} Container）",
                    radius, result.totalBes(), result.chestBes(), result.barrelBes(), result.containerBes());
        }

        if (result.found().isEmpty()) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] nearbyChest: 半径 {} 内未找到含 {} 的容器", radius, target);
            return SourceResult.notHandled();
        }

        // 取物必须在服务端线程（修改 inventory）
        List<FoundBE> foundSorted = new ArrayList<>(result.found());
        foundSorted.sort(Comparator.comparingDouble(FoundBE::distSqr));
        VoiceBlockMod.LOGGER.info("[VoiceBlock] nearbyChest: 找到 {} 个含 {} 的容器，开始取物", foundSorted.size(), target);

        AtomicInteger totalTaken = new AtomicInteger();
        StringBuilder failReason = new StringBuilder();

        Boolean ok = ServerThreadExecutor.run(server, () -> {
            for (FoundBE fc : foundSorted) {
                if (totalTaken.get() >= desiredAmount) break;
                for (int i = 0; i < fc.inv.getContainerSize() && totalTaken.get() < desiredAmount; i++) {
                    ItemStack slot = fc.inv.getItem(i);
                    if (!slot.isEmpty() && slot.is(target)) {
                        int canTake = Math.min(slot.getCount(), desiredAmount - totalTaken.get());
                        ItemStack extracted = fc.inv.removeItem(i, canTake);
                        if (extracted.isEmpty()) continue;
                        int placed = InventoryPlacer.placeIntoPlayer(sp, extracted);
                        totalTaken.addAndGet(placed);
                        if (placed < extracted.getCount()) {
                            int back = extracted.getCount() - placed;
                            if (back > 0) {
                                ItemStack toReturn = extracted.copy();
                                toReturn.setCount(back);
                                fc.inv.setItem(i, toReturn);
                            }
                            failReason.append("身上已满");
                            return false;
                        }
                    }
                }
            }
            return true;
        }, Boolean.FALSE);

        int total = totalTaken.get();
        VoiceBlockMod.LOGGER.info("[VoiceBlock] nearbyChest: 实际取出 {}x{}", total, target);
        if (total > 0 && ok) return SourceResult.ok(total);
        if (failReason.length() > 0) return SourceResult.fail(failReason.toString());
        return SourceResult.notHandled();
    }

    private boolean inventoryContains(Container inv, Item target) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(target)) return true;
        }
        return false;
    }

    private record FoundBE(Container inv, double distSqr) {}
}