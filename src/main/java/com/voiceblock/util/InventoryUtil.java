package com.voiceblock.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 物品栏操作工具。
 */
public class InventoryUtil {

    /**
     * 替换玩家当前选中快捷栏格子的物品。
     * 使用 Minecraft 内部的创造模式物品添加逻辑，确保客户端与服务端完全同步，避免"幽灵方块"。
     *
     * @param player 玩家
     * @param itemId 物品 ID，如 "minecraft:oak_stairs"
     * @return 是否替换成功
     */
    public static boolean replaceSelectedItem(Player player, String itemId, String displayName) {
        if (player == null) return false;

        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId));
        if (itemOpt.isEmpty()) {
            return false;
        }

        Item item = itemOpt.get();
        ItemStack stack = new ItemStack(item, 64);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));

        int slot = player.getInventory().selected;  // 0~8 快捷栏

        Minecraft mc = Minecraft.getInstance();
        // 原版创造模式的正确同步方式（参照 CreativeModeInventoryScreen 加载快捷栏的逻辑）：
        // 1. 先设置客户端物品栏，让图标立即显示
        player.getInventory().setItem(slot, stack);
        // 2. 发送创造模式槽包给服务端，slot 必须是容器槽位索引（快捷栏 = 36~44）
        if (mc.gameMode != null) {
            mc.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        }
        // 3. 让物品栏菜单重新广播变更
        player.inventoryMenu.broadcastChanges();

        return true;
    }
}
