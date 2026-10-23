package com.voiceblock.source.impl;

import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SourceResult;
import com.voiceblock.util.InventoryUtil;
import com.voiceblock.util.ItemResolver;
import com.voiceblock.util.ServerInventoryUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * 玩家自身背包/快捷栏源（默认开，优先级 1）。
 * 创造模式：1 个直接放选中格（按 v1.3.0 拍板）。
 * 生存模式：背包中找并交换到选中格。
 * 背包中没有时返回 notHandled()，让 SourceManager 尝试下一个源。
 *
 * <p>在多人服务器上（palyer 是 ServerPlayer），走 {@link ServerInventoryUtil}；
 * 在单玩家下走原 {@link InventoryUtil}。两端共用这一份实现。</p>
 */
public class SourceInventory implements ItemSource {

    @Override
    public String name() { return "inventory"; }

    @Override
    public Component displayName() { return Component.literal("背包"); }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public SourceResult take(Player player, String itemId, int desiredAmount) {
        if (player.isCreative()) {
            boolean ok = ServerInventoryUtil.replaceSelectedItemAnySide(player, itemId, null);
            return ok ? SourceResult.ok(1) : SourceResult.fail("未找到物品: " + itemId);
        }
        boolean swapped = ServerInventoryUtil.swapWithInventoryAnySide(player, itemId);
        if (swapped) {
            Item item = ItemResolver.resolve(itemId);
            int amount = item != null ? InventoryUtil.getDefaultMaxStackSize(item) : 1;
            return SourceResult.ok(amount);
        }
        return SourceResult.notHandled();
    }
}