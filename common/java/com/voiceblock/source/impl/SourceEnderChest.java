package com.voiceblock.source.impl;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SourceResult;
import com.voiceblock.source.SourceServerAccess;
import com.voiceblock.util.InventoryPlacer;
import com.voiceblock.util.ItemResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 末影箱源（vanilla 个人存储，默认开，优先级 3）。
 * 跨维度：末影箱永远同玩家，无需判断。
 *
 * <p>读取的是 <b>ServerPlayer</b> 的容器（不是 LocalPlayer 的客户端副本），
 * 这样 /give 或者 UI 之外的物品都能看到。</p>
 */
public class SourceEnderChest implements ItemSource {

    @Override
    public String name() { return "enderchest"; }

    @Override
    public Component displayName() { return Component.literal("末影箱"); }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public SourceResult take(Player player, String itemId, int desiredAmount) {
        Item target = ItemResolver.resolve(itemId);
        if (target == null) return SourceResult.notHandled();

        ServerPlayer sp = SourceServerAccess.getServerPlayer(player);
        if (sp == null) {
            VoiceBlockMod.LOGGER.debug("[VoiceBlock] enderchest: 服务端玩家不可用（多人模式？）");
            return SourceResult.notHandled();
        }

        Container chest = sp.getEnderChestInventory();
        int size = chest.getContainerSize();
        int matchedSlots = 0;
        int total = 0;

        for (int i = 0; i < size; i++) {
            if (total >= desiredAmount) break;
            ItemStack slot = chest.getItem(i);
            if (!slot.isEmpty() && slot.is(target)) {
                matchedSlots++;
                int canTake = Math.min(slot.getCount(), desiredAmount - total);
                ItemStack extracted = chest.removeItem(i, canTake);
                if (extracted.isEmpty()) {
                    VoiceBlockMod.LOGGER.warn("[VoiceBlock] enderchest: removeItem returned empty for slot {}", i);
                    continue;
                }
                int placed = InventoryPlacer.placeIntoPlayer(sp, extracted);
                total += placed;
                VoiceBlockMod.LOGGER.info("[VoiceBlock] enderchest: slot {} had {}x{}, extracted {}, placed {}",
                        i, slot.getCount(), target, extracted.getCount(), placed);
                if (placed < extracted.getCount()) {
                    int back = extracted.getCount() - placed;
                    if (back > 0) {
                        ItemStack toReturn = extracted.copy();
                        toReturn.setCount(back);
                        chest.setItem(i, toReturn);
                    }
                    return SourceResult.fail("身上已满");
                }
            }
        }
        VoiceBlockMod.LOGGER.info("[VoiceBlock] enderchest: scanned {} slots, found {} matches, took {}x{}",
                size, matchedSlots, total, target);
        return total > 0 ? SourceResult.ok(total) : SourceResult.notHandled();
    }
}