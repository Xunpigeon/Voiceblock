package com.voiceblock.util;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * 物品放置工具：v2.0.7 物品流转规则（玩家指定新顺序）。
 *
 * <p><b>新放置优先级</b>：
 * <ol>
 *   <li><b>当前选中的快捷栏槽</b>：如果空就放进去；如果已有同种就合并；如果已有异种，把
 *       旧物品让位，让位顺序也是「背包空位 → 副手合并/空 → 提示包满了」</li>
 *   <li>主背包合并（9-35）</li>
 *   <li>主背包空槽（9-35）</li>
 *   <li>快捷栏合并（0-8）</li>
 *   <li>快捷栏空槽（0-8）</li>
 *   <li>副手</li>
 * </ol>
 *
 * <p>剩下的物品由调用方处理（通常 fail("背包满了")）。</p>
 */
public class InventoryPlacer {

    public static int placeIntoPlayer(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemStack remaining = stack.copy();
        int original = remaining.getCount();
        Inventory inv = player.getInventory();

        // === Step 1: 优先放到当前选中的快捷栏 ===
        placeIntoSelectedHotbar(player, inv, remaining);

        // === Step 2: 剩余部分按"主背包合并 → 主背包空槽 → 快捷栏合并 → 快捷栏空槽 → 副手" ===
        if (!remaining.isEmpty()) {
            remaining = mergeIntoSlots(remaining, inv, 9, 36);
            remaining = placeIntoEmptySlots(remaining, inv, 9, 36);
            remaining = mergeIntoSlots(remaining, inv, 0, 9);
            remaining = placeIntoEmptySlots(remaining, inv, 0, 9);

            if (!remaining.isEmpty()) {
                remaining = placeIntoOffhand(player, remaining);
            }
        }

        return original - remaining.getCount();
    }

    /**
     * 把 remaining 放进玩家当前选中的快捷栏槽。
     * 处理三种情况：
     * <ul>
     *   <li>槽空：直接放</li>
     *   <li>槽已有同种：合并</li>
     *   <li>槽已有异种：把旧物品让位（背包空位 → 副手 → 失败），新物品放进去</li>
     * </ul>
     */
    private static void placeIntoSelectedHotbar(Player player, Inventory inv, ItemStack remaining) {
        if (remaining.isEmpty()) return;
        int selectedIdx = inv.selected; // 0..8 (Inventory.selected 是 public 字段)
        ItemStack selected = inv.getItem(selectedIdx);

        if (selected.isEmpty()) {
            // 空槽 → 直接放
            int toPlace = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            inv.setItem(selectedIdx, remaining.split(toPlace));
            return;
        }

        if (isSameStack(selected, remaining) && selected.getCount() < selected.getMaxStackSize()) {
            // 同种 → 合并
            int space = selected.getMaxStackSize() - selected.getCount();
            int toAdd = Math.min(space, remaining.getCount());
            selected.grow(toAdd);
            remaining.shrink(toAdd);
            return;
        }

        // 异种 → 让位：把 selected 移到 背包空位 → 副手 → 放弃
        ItemStack displaced = selected.copy();
        boolean moved = false;

        // 1) 背包空位（9-35）
        int emptyIdx = findFirstEmptyInRange(inv, 9, 36);
        if (emptyIdx >= 0) {
            inv.setItem(emptyIdx, displaced);
            inv.setItem(selectedIdx, ItemStack.EMPTY);
            moved = true;
        } else {
            // 2) 副手（空 或 可合并）
            ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
            if (offhand.isEmpty()) {
                player.setItemSlot(EquipmentSlot.OFFHAND, displaced);
                inv.setItem(selectedIdx, ItemStack.EMPTY);
                moved = true;
            } else if (isSameStack(offhand, displaced) && offhand.getCount() < offhand.getMaxStackSize()) {
                int space = offhand.getMaxStackSize() - offhand.getCount();
                int toAdd = Math.min(space, displaced.getCount());
                offhand.grow(toAdd);
                displaced.shrink(toAdd);
                if (displaced.isEmpty()) {
                    inv.setItem(selectedIdx, ItemStack.EMPTY);
                    moved = true;
                }
                // 还剩 displaced 没塞下 → moved=false（提示包满了）
            }
        }

        if (moved) {
            int toPlace = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            inv.setItem(selectedIdx, remaining.split(toPlace));
        }
        // moved == false → 不动 remaining 和 selected，由调用方处理剩余
    }

    private static int findFirstEmptyInRange(Inventory inv, int start, int end) {
        for (int i = start; i < end; i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static ItemStack mergeIntoSlots(ItemStack items, Inventory inv, int start, int end) {
        for (int i = start; i < end; i++) {
            if (items.isEmpty()) break;
            ItemStack existing = inv.getItem(i);
            if (!existing.isEmpty() && isSameStack(existing, items)) {
                int space = Math.min(existing.getMaxStackSize(), inv.getMaxStackSize()) - existing.getCount();
                if (space > 0) {
                    int toMove = Math.min(space, items.getCount());
                    existing.grow(toMove);
                    items.shrink(toMove);
                }
            }
        }
        return items;
    }

    private static ItemStack placeIntoEmptySlots(ItemStack items, Inventory inv, int start, int end) {
        for (int i = start; i < end; i++) {
            if (items.isEmpty()) break;
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                int toMove = Math.min(items.getMaxStackSize(), items.getCount());
                inv.setItem(i, items.split(toMove));
            }
        }
        return items;
    }

    private static ItemStack placeIntoOffhand(Player player, ItemStack remaining) {
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.isEmpty()) {
            int toMove = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            ItemStack placed = remaining.split(toMove);
            player.setItemSlot(EquipmentSlot.OFFHAND, placed);
            return remaining;
        }
        if (isSameStack(offhand, remaining)) {
            int space = offhand.getMaxStackSize() - offhand.getCount();
            if (space > 0) {
                int toMove = Math.min(space, remaining.getCount());
                offhand.grow(toMove);
                remaining.shrink(toMove);
            }
        }
        return remaining;
    }

    /** 跨版本比较两个 ItemStack 是否同种。 */
    private static boolean isSameStack(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        // 1.20.5+ 官方名 isSameItemSameComponents；1.20.1 官方名 isSameItemSameTags（Forge 正式版为 SRG m_150942_）
        for (String name : new String[]{"isSameItemSameComponents", "isSameItemSameTags", "m_150942_", "method_31577"}) {
            try {
                return (boolean) ItemStack.class.getMethod(name, ItemStack.class).invoke(null, a, b);
            } catch (Exception ignored) {}
        }
        try {
            Method m = ItemStack.class.getMethod("isSame", net.minecraft.world.item.Item.class);
            return (boolean) m.invoke(a, b.getItem());
        } catch (Exception ignored) {}
        return a.getItem() == b.getItem();
    }
}