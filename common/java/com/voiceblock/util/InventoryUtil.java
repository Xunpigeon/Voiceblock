package com.voiceblock.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
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
     * 创造模式：替换玩家当前选中快捷栏格子的物品。
     *
     * @param player         玩家
     * @param blockOrItemId  方块或物品 ID
     * @param displayName    中文显示名
     * @return 是否替换成功
     */
    public static boolean replaceSelectedItem(Player player, String blockOrItemId, String displayName) {
        if (player == null) return false;

        Item item = resolveItem(blockOrItemId);
        if (item == null) return false;

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
     * 生存模式：在背包中查找目标物品，并通过容器点击(SWAP)与当前选中快捷栏格子交换。
     * 使用 ClickType.SWAP 会向服务器发送交换包，避免幽灵方块。
     *
     * @param player         玩家
     * @param blockOrItemId  方块或物品 ID
     * @return 是否交换成功
     */
    public static boolean swapWithInventory(Player player, String blockOrItemId) {
        if (player == null) return false;

        Item item = resolveItem(blockOrItemId);
        if (item == null) return false;

        Inventory inv = player.getInventory();
        int selected = inv.selected;
        int foundSlot = -1;

        // 在快捷栏(0-8)和主背包(9-35)中查找目标物品
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                foundSlot = i;
                break;
            }
        }

        if (foundSlot == -1) {
            return false; // 背包中没有该物品
        }

        if (foundSlot == selected) {
            return true; // 已经在手上了
        }

        // 将背包索引转换为容器菜单索引：
        //   背包索引 0-8 (快捷栏) -> 容器索引 36-44
        //   背包索引 9-35 (主背包) -> 容器索引 9-35
        int foundContainerSlot = (foundSlot < 9) ? (36 + foundSlot) : foundSlot;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) {
            // ClickType.SWAP：将点击的容器槽与指定的快捷栏索引(selected)交换
            // 服务器会处理交换并同步回客户端，避免幽灵方块
            mc.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                foundContainerSlot,
                selected,
                ClickType.SWAP,
                player
            );
        }

        return true;
    }

    /**
     * 将方块/物品 ID 解析为 Item 对象。
     */
    private static Item resolveItem(String blockOrItemId) {
        ResourceLocation rl = ResourceLocation.tryParse(blockOrItemId);
        if (rl == null) return null;

        // 优先通过方块注册表获取对应物品
        Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
        if (blockOpt.isPresent()) {
            Item blockItem = blockOpt.get().asItem();
            if (blockItem != null && blockItem != Items.AIR) {
                return blockItem;
            }
        }

        // 方块没有对应物品时，直接查物品注册表
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
        return itemOpt.orElse(null);
    }

    /**
     * 跨版本设置物品显示名。
     * 1.20.5+ 使用 DataComponents.CUSTOM_NAME，1.20.1-1.20.4 使用 setHoverName。
     */
    private static void setItemDisplayName(ItemStack stack, String name) {
        try {
            Class<?> dcClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object customName = dcClass.getField("CUSTOM_NAME").get(null);
            Class<?> dcType = Class.forName("net.minecraft.core.component.DataComponentType");
            Method setMethod = ItemStack.class.getMethod("set", dcType, Object.class);
            setMethod.invoke(stack, customName, Component.literal(name));
        } catch (Exception e) {
            try {
                Method hoverMethod = ItemStack.class.getMethod("setHoverName", Component.class);
                hoverMethod.invoke(stack, Component.literal(name));
            } catch (Exception ignored) {}
        }
    }
}