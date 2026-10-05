package com.voiceblock.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 物品栏操作工具。
 */
public class InventoryUtil {

    /**
     * 替换玩家当前选中快捷栏格子的物品。
     *
     * @param player      玩家
     * @param blockOrItemId 方块或物品 ID
     * @param displayName 中文显示名
     * @return 是否替换成功
     */
    public static boolean replaceSelectedItem(Player player, String blockOrItemId, String displayName) {
        if (player == null) return false;

        ResourceLocation rl = ResourceLocation.tryParse(blockOrItemId);
        if (rl == null) return false;
        Item item = null;

        // 优先通过方块注册表获取对应物品（方块 ID 与物品 ID 可能不同）
        Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
        if (blockOpt.isPresent()) {
            Item blockItem = blockOpt.get().asItem();
            if (blockItem != null && blockItem != Items.AIR) {
                item = blockItem;
            }
        }

        // 方块没有对应物品时，直接查物品注册表
        if (item == null) {
            Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
            if (itemOpt.isEmpty()) {
                return false;
            }
            item = itemOpt.get();
        }

        ItemStack stack = new ItemStack(item, 64);
        setItemDisplayName(stack, displayName);

        int slot = player.getInventory().selected;

        Minecraft mc = Minecraft.getInstance();
        player.getInventory().setItem(slot, stack);
        if (mc.gameMode != null) {
            mc.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        }
        player.inventoryMenu.broadcastChanges();

        return true;
    }

    /**
     * 跨版本设置物品显示名。
     * 1.20.5+ 使用 DataComponents.CUSTOM_NAME，1.20.1-1.20.4 使用 setHoverName。
     */
    private static void setItemDisplayName(ItemStack stack, String name) {
        try {
            // 1.20.5+: DataComponents.CUSTOM_NAME
            Class<?> dcClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object customName = dcClass.getField("CUSTOM_NAME").get(null);
            Class<?> dcType = Class.forName("net.minecraft.core.component.DataComponentType");
            Method setMethod = ItemStack.class.getMethod("set", dcType, Object.class);
            setMethod.invoke(stack, customName, Component.literal(name));
        } catch (Exception e) {
            // 1.20.1-1.20.4: setHoverName
            try {
                Method hoverMethod = ItemStack.class.getMethod("setHoverName", Component.class);
                hoverMethod.invoke(stack, Component.literal(name));
            } catch (Exception ignored) {}
        }
    }
}