package com.voiceblock.source.impl;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SourceResult;
import com.voiceblock.source.SourceServerAccess;
import com.voiceblock.util.ClassLookup;
import com.voiceblock.util.InventoryPlacer;
import com.voiceblock.util.ItemResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * 身上的潜影盒源（vanilla，默认开，优先级 4）。
 * <p>
 * 遍历玩家 Inventory + 副手里的 ShulkerBoxItem 物品（潜影盒）。
 * 通过 1.21 Component (DataComponents.CONTAINER) 读取/写入内部物品。
 * <p>
 * <b>跨版本兼容</b>：1.21+ 用 Component；1.20.1 没这些 API，整个源在 1.20.1 上自动不可用。
 * 所有 DataComponents / ItemContainerContents 操作都通过反射完成，
 * 这样本类可以在 1.20.1 和 1.21+ 都通过编译。
 */
public class SourceShulkerBox implements ItemSource {

    // ===== 反射缓存：1.21+ 才有；1.20.1 这些字段都是 null，整源退化为不可用 =====

    private static final boolean HAS_DATA_COMPONENTS;
    private static final Object DATA_COMPONENT_CONTAINER;   // DataComponents.CONTAINER
    private static final Object ITEM_CONTAINER_CONTENTS_EMPTY;
    private static final Method GET_OR_DEFAULT;
    private static final Method SET_COMPONENT;
    private static final Method CONTENTS_STREAM;
    private static final Method CONTENTS_FROM_ITEMS;
    private static final Class<?> DATA_COMPONENT_TYPE_CLS;
    private static final Class<?> DATA_COMPONENTS_CLS;

    static {
        boolean ok = false;
        Object containerCap = null;
        Object iccEmpty = null;
        Method god = null, setc = null, stream = null, fromItems = null;
        Class<?> dct = null, dc = null;
        try {
            dc = ClassLookup.forName("net.minecraft.core.component.DataComponents", "net.minecraft.class_9334");
            dct = ClassLookup.forName("net.minecraft.core.component.DataComponentType", "net.minecraft.class_9331");
            Class<?> icc = ClassLookup.forName("net.minecraft.world.item.component.ItemContainerContents",
                    "net.minecraft.class_9288");
            Field containerField = ClassLookup.field(dc, "CONTAINER", "field_49622");
            if (containerField == null) throw new NoSuchFieldException("CONTAINER");
            containerCap = containerField.get(null);
            Field emptyField = ClassLookup.field(icc, "EMPTY", "field_49334");
            if (emptyField == null) throw new NoSuchFieldException("EMPTY");
            iccEmpty = emptyField.get(null);
            god = ClassLookup.method(ItemStack.class, new String[]{"getOrDefault", "method_57825"}, dct, Object.class);
            setc = ClassLookup.method(ItemStack.class, new String[]{"set", "method_57379"}, dct, Object.class);
            stream = ClassLookup.method(icc, new String[]{"stream", "method_57489"});
            // fromItems 接受 Stream<ItemStack>
            fromItems = ClassLookup.method(icc, new String[]{"fromItems", "method_57493"}, Stream.class);
            if (god == null || setc == null || stream == null || fromItems == null) {
                throw new NoSuchMethodException("shulkerBox 反射成员缺失");
            }
            ok = true;
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.debug("[VoiceBlock] shulkerBox: DataComponents 反射失败（1.20.1?），整源不可用");
        }
        HAS_DATA_COMPONENTS = ok;
        DATA_COMPONENTS_CLS = dc;
        DATA_COMPONENT_TYPE_CLS = dct;
        DATA_COMPONENT_CONTAINER = containerCap;
        ITEM_CONTAINER_CONTENTS_EMPTY = iccEmpty;
        GET_OR_DEFAULT = god;
        SET_COMPONENT = setc;
        CONTENTS_STREAM = stream;
        CONTENTS_FROM_ITEMS = fromItems;
    }

    @Override
    public String name() { return "shulkerBox"; }

    @Override
    public Component displayName() { return Component.literal("身上的潜影盒"); }

    @Override
    public boolean isAvailable() { return HAS_DATA_COMPONENTS; }

    @Override
    public SourceResult take(Player player, String itemId, int desiredAmount) {
        if (!HAS_DATA_COMPONENTS) return SourceResult.notHandled();

        Item target = ItemResolver.resolve(itemId);
        if (target == null) return SourceResult.notHandled();

        ServerPlayer sp = SourceServerAccess.getServerPlayer(player);
        if (sp == null) {
            VoiceBlockMod.LOGGER.debug("[VoiceBlock] shulkerBox: 服务端玩家不可用");
            return SourceResult.notHandled();
        }

        List<ItemStack> shulkers = findPlayerShulkers(sp);
        if (shulkers.isEmpty()) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] shulkerBox: 身上没有潜影盒，跳过");
            return SourceResult.notHandled();
        }
        VoiceBlockMod.LOGGER.info("[VoiceBlock] shulkerBox: 发现 {} 个潜影盒，开始取物", shulkers.size());

        int total = 0;
        for (ItemStack shulker : shulkers) {
            if (total >= desiredAmount) break;
            ItemStack extracted = takeFromShulker(shulker, target, desiredAmount - total);
            if (extracted.isEmpty()) continue;
            int placed = InventoryPlacer.placeIntoPlayer(sp, extracted);
            total += placed;
            if (placed < extracted.getCount()) {
                int back = extracted.getCount() - placed;
                if (back > 0) {
                    ItemStack toReturn = extracted.copy();
                    toReturn.setCount(back);
                    putBack(shulker, toReturn);
                }
                return SourceResult.fail("身上已满");
            }
        }
        return total > 0 ? SourceResult.ok(total) : SourceResult.notHandled();
    }

    private List<ItemStack> findPlayerShulkers(Player player) {
        List<ItemStack> result = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isShulker(s)) result.add(s);
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isShulker(offhand)) result.add(offhand);
        return result;
    }

    private boolean isShulker(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * 反射读取 shulker 内的 ItemContainerContents，转成 List<ItemStack>。
     */
    private List<ItemStack> readContents(ItemStack shulker) {
        try {
            Object contents = GET_OR_DEFAULT.invoke(shulker, DATA_COMPONENT_CONTAINER, ITEM_CONTAINER_CONTENTS_EMPTY);
            if (contents == null) return new ArrayList<>();
            Stream<ItemStack> stream = (Stream<ItemStack>) CONTENTS_STREAM.invoke(contents);
            List<ItemStack> list = new ArrayList<>();
            stream.forEach(list::add);
            return list;
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] shulkerBox: readContents 失败", t);
            return new ArrayList<>();
        }
    }

    /**
     * 反射把 List<ItemStack> 写回 shulker 的 CONTAINER Component。
     */
    private void writeContents(ItemStack shulker, List<ItemStack> items) {
        try {
            Stream<ItemStack> stream = items.stream();
            Object newContents = CONTENTS_FROM_ITEMS.invoke(null, stream);
            SET_COMPONENT.invoke(shulker, DATA_COMPONENT_CONTAINER, newContents);
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] shulkerBox: writeContents 失败", t);
        }
    }

    private ItemStack takeFromShulker(ItemStack shulker, Item target, int amount) {
        List<ItemStack> current = readContents(shulker);
        ItemStack extracted = ItemStack.EMPTY;
        int remaining = amount;
        for (int i = 0; i < current.size() && remaining > 0; i++) {
            ItemStack slot = current.get(i);
            if (!slot.isEmpty() && slot.is(target)) {
                int canTake = Math.min(slot.getCount(), remaining);
                ItemStack took = slot.split(canTake);
                remaining -= took.getCount();
                if (extracted.isEmpty()) extracted = took.copy();
                else extracted.grow(took.getCount());
            }
        }
        if (!extracted.isEmpty()) {
            writeContents(shulker, current);
        }
        return extracted;
    }

    private void putBack(ItemStack shulker, ItemStack stack) {
        if (stack.isEmpty()) return;
        List<ItemStack> current = readContents(shulker);
        // 合并到同种
        for (int i = 0; i < current.size() && !stack.isEmpty(); i++) {
            ItemStack slot = current.get(i);
            if (!slot.isEmpty() && slot.getItem() == stack.getItem()) {
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space > 0) {
                    int toMove = Math.min(space, stack.getCount());
                    slot.grow(toMove);
                    stack.shrink(toMove);
                }
            }
        }
        // 第一个空槽
        for (int i = 0; i < current.size() && !stack.isEmpty(); i++) {
            if (current.get(i).isEmpty()) {
                current.set(i, stack);
                stack = ItemStack.EMPTY;
                break;
            }
        }
        if (!stack.isEmpty()) current.add(stack);
        writeContents(shulker, current);
    }
}