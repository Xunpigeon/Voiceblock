package com.voiceblock.source.impl;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SourceResult;
import com.voiceblock.source.SourceServerAccess;
import com.voiceblock.util.InventoryPlacer;
import com.voiceblock.util.ItemResolver;
import com.voiceblock.util.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 精妙背包源（Sophisticated Backpacks，软依赖，默认开，优先级 2）。
 *
 * <p><b>跨平台实现</b>：SB 的 wrapper / IBackpackWrapper / fromStack / getInventoryHandler 在所有平台
 * 都是 {@code net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.*}，路径一致。
 * 唯一区别是 InventoryHandler 接口层：
 * <ul>
 *   <li><b>Forge / NeoForge</b>：handler 实现 NeoForge {@code IItemHandler}，
 *       提供 {@code extractItem(int,int,boolean)} / {@code insertItem(int,ItemStack,boolean)}</li>
 *   <li><b>Fabric 1.20.1+</b>：handler 继承 porting_lib {@code ItemStackHandler}，
 *       extract/insert 走 Fabric Transfer API；本类用 {@code setStackInSlot} 手工 split 替代</li>
 * </ul>
 */
public class SourceCurio implements ItemSource {

    /** 用物品 ID 检测 SB 是否加载（class 名跨版本不稳） */
    private static final String[] BACKPACK_ITEM_IDS = {
            "sophisticatedbackpacks:backpack",
            "sophisticatedbackpacks:copper_backpack",
            "sophisticatedbackpacks:iron_backpack",
            "sophisticatedbackpacks:gold_backpack",
            "sophisticatedbackpacks:diamond_backpack",
            "sophisticatedbackpacks:netherite_backpack"
    };

    /** SB API 反射缓存（构造一次，全局复用） */
    private static BackpackRefs REFS;
    private static boolean REFS_INIT_TRIED = false;

    @Override
    public String name() { return "curio"; }

    @Override
    public Component displayName() { return Component.literal("精妙背包"); }

    @Override
    public boolean isAvailable() {
        for (String id : BACKPACK_ITEM_IDS) {
            if (ItemResolver.resolve(id) != null) return true;
        }
        return false;
    }

    @Override
    public SourceResult take(Player player, String itemId, int desiredAmount) {
        Item target = ItemResolver.resolve(itemId);
        if (target == null) return SourceResult.notHandled();

        BackpackRefs refs = getOrInitRefs();
        if (refs == null) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] curio: SB BackpackWrapper 反射失败（SB 未装？），跳过");
            return SourceResult.notHandled();
        }

        ServerPlayer sp = SourceServerAccess.getServerPlayer(player);
        if (sp == null) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] curio: 服务端玩家不可用（多人模式？），跳过");
            return SourceResult.notHandled();
        }

        int total = 0;
        int scannedContainers = 0;

        Inventory inv = sp.getInventory();
        for (int slot = 0; slot < inv.getContainerSize() && total < desiredAmount; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || !isBackpackStack(stack)) continue;
            Object handler = refs.openBackpack(stack);
            if (handler == null) continue;
            scannedContainers++;
            int added = extractFromHandler(refs, handler, target, sp, desiredAmount - total);
            if (added < 0) return SourceResult.fail("身上已满");
            total += added;
        }

        // 副手
        ItemStack offhand = sp.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.isEmpty() && isBackpackStack(offhand) && total < desiredAmount) {
            Object handler = refs.openBackpack(offhand);
            if (handler != null) {
                scannedContainers++;
                int added = extractFromHandler(refs, handler, target, sp, desiredAmount - total);
                if (added < 0) return SourceResult.fail("身上已满");
                total += added;
            }
        }

        VoiceBlockMod.LOGGER.info("[VoiceBlock] curio ({}) 扫了玩家身上 {} 个 SB 背包容器，实际取出 {}x{}",
                refs.platform, scannedContainers, total, target);
        return total > 0 ? SourceResult.ok(total) : SourceResult.notHandled();
    }

    /**
     * 判断某个 ItemStack 是否是 SB 背包。
     *
     * <p>SB 3.26+ 移除了静态工厂 {@code BackpackWrapper.fromStack(ItemStack)}，改用 public 构造器
     * {@code BackpackWrapper(ItemStack)} —— 而该构造器对任意物品都不会拒绝。因此必须先用物品 ID
     * 过滤，只对真正的背包做 wrap，避免把玩家背包里每个物品都包一遍（副作用/性能）。</p>
     *
     * <p>判定条件：物品注册名 namespace 为 {@code sophisticatedbackpacks} 且 path 以 {@code backpack}
     * 结尾，覆盖 backpack / copper / iron / gold / diamond / netherite 全部等级。</p>
     */
    private static boolean isBackpackStack(ItemStack stack) {
        ResourceLocation rl = RegistryAccess.getItemKey(stack.getItem());
        return rl != null
                && "sophisticatedbackpacks".equals(rl.getNamespace())
                && rl.getPath().endsWith("backpack");
    }

    /**
     * 从单个 SB handler 里抽 target 物品，返回成功抽取并放入玩家身上的数量。
     * -1 表示身上满了（已放回剩余）。
     */
    private int extractFromHandler(BackpackRefs refs, Object handler, Item target,
                                   ServerPlayer player, int remaining) {
        int taken = 0;
        try {
            int slots = refs.getSlotCount(handler);
            for (int i = 0; i < slots && taken < remaining; i++) {
                ItemStack current = (ItemStack) refs.getStackInSlot.invoke(handler, i);
                if (current.isEmpty() || !current.is(target)) continue;

                int canTake = Math.min(current.getCount(), remaining - taken);

                // === extract：Forge 走 IItemHandler.extractItem；Fabric 走 setStackInSlot 手工 split ===
                ItemStack extracted;
                ItemStack leftoverInSlot;
                if (refs.useIItemHandlerExtract) {
                    extracted = (ItemStack) refs.extractItem.invoke(handler, i, canTake, false);
                    if (extracted.isEmpty()) continue;
                    // 槽位已被 extractItem 自动写好；新值在原位
                } else {
                    // Fabric：手动 split
                    extracted = current.copy();
                    extracted.setCount(canTake);
                    ItemStack newSlot = current.copy();
                    newSlot.shrink(canTake);
                    if (newSlot.getCount() <= 0) newSlot = ItemStack.EMPTY;
                    refs.setStackInSlot.invoke(handler, i, newSlot);
                }

                int placed = InventoryPlacer.placeIntoPlayer(player, extracted);
                taken += placed;

                if (placed < extracted.getCount()) {
                    int back = extracted.getCount() - placed;
                    if (back > 0) {
                        ItemStack toReturn = extracted.copyWithCount(back);
                        // === insert：把这个还回去 ===
                        if (refs.useIItemHandlerExtract) {
                            // Forge：IItemHandler.insertItem 会自动合并现有槽位
                            refs.insertItem.invoke(handler, i, toReturn, false);
                        } else {
                            // Fabric：槽位刚才已被 setStackInSlot 清空，直接放回同槽即可
                            refs.setStackInSlot.invoke(handler, i, toReturn);
                        }
                    }
                    return -1;
                }
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] curio extract exception", e);
        }
        return taken;
    }

    /**
     * 一次性反射初始化 SB API。
     * wrapper / IBackpackWrapper / fromStack / getInventoryHandler 在所有平台都是 {@code p3pp3rf1y}，
     * 唯一区别是 handler 接口类型。
     */
    private static BackpackRefs getOrInitRefs() {
        if (REFS_INIT_TRIED) return REFS;
        REFS_INIT_TRIED = true;

        // === 共用部分：找 SB wrapper 类（跨平台同名）===
        Class<?> wrapperCls;
        Method fromStack = null;                 // 旧版 SB 静态工厂
        Constructor<?> wrapperCtor = null;       // 新版 SB（3.26+）public 构造器
        Method getInventoryHandler;
        try {
            wrapperCls = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
            // 旧版 SB（≤3.2x）：静态工厂 BackpackWrapper.fromStack(ItemStack)
            try {
                fromStack = wrapperCls.getMethod("fromStack", ItemStack.class);
            } catch (NoSuchMethodException ignored) {}
            // 新版 SB（3.26+）：静态工厂已被移除，只剩 public 构造器 BackpackWrapper(ItemStack)
            try {
                wrapperCtor = wrapperCls.getConstructor(ItemStack.class);
            } catch (NoSuchMethodException ignored) {}
            if (fromStack == null && wrapperCtor == null) {
                throw new NoSuchMethodException("BackpackWrapper 既无 fromStack 也无 (ItemStack) 构造器");
            }
            getInventoryHandler = wrapperCls.getMethod("getInventoryHandler");
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] curio: SB BackpackWrapper 反射初始化失败（找不到 p3pp3rf1y 包，SB 未装？）", t);
            return null;
        }

        // === 路径 1：NeoForge / Forge — handler 实现 IItemHandler ===
        // 注意：setStackInSlot 声明在子接口 IItemHandlerModifiable 上，IItemHandler 本身没有。
        // 之前直接对 IItemHandler 取 setStackInSlot 会抛 NoSuchMethodException，
        // 导致整条路径被静默跳过（curio 源在服务端永远不可用）。
        // 类名候选：NeoForge 用 net.neoforged.neoforge.items.*，Forge 1.20.1 用 net.minecraftforge.items.*
        String[][] iihCandidates = {
                {"net.neoforged.neoforge.items.IItemHandler",
                        "net.neoforged.neoforge.items.IItemHandlerModifiable", "NeoForge"},
                {"net.minecraftforge.items.IItemHandler",
                        "net.minecraftforge.items.IItemHandlerModifiable", "Forge"},
        };
        for (String[] cand : iihCandidates) {
            try {
                Class<?> iItemHandlerCls = Class.forName(cand[0]);
                Class<?> modifiableCls = Class.forName(cand[1]);
                Method getSlots = iItemHandlerCls.getMethod("getSlots");
                Method getStackInSlot = iItemHandlerCls.getMethod("getStackInSlot", int.class);
                Method setStackInSlot = modifiableCls.getMethod("setStackInSlot", int.class, ItemStack.class);
                Method extractItem = iItemHandlerCls.getMethod("extractItem", int.class, int.class, boolean.class);
                Method insertItem = iItemHandlerCls.getMethod("insertItem", int.class, ItemStack.class, boolean.class);

                REFS = new BackpackRefs(cand[2], fromStack, wrapperCtor, getInventoryHandler,
                        getSlots, getStackInSlot, setStackInSlot,
                        extractItem, insertItem, true);
                VoiceBlockMod.LOGGER.info("[VoiceBlock] curio: 用 {} IItemHandler 路径反射成功", cand[2]);
                return REFS;
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] curio: {} IItemHandler 路径不可用: {}", cand[2], t.toString());
            }
        }

        // === 路径 2：Fabric 1.20.1+ — handler 继承 porting_lib 的 ItemStackHandler ===
        // porting_lib 没有 NeoForge IItemHandler 那套 extractItem/insertItem，
        // 只能用 getStackInSlot + setStackInSlot 手工 split/merge。
        try {
            Class<?> itemStackHandlerCls = Class.forName(
                    "io.github.fabricators_of_create.porting_lib.transfer.item.ItemStackHandler");
            // getSlots() 返回 java.util.List<ItemStackHandlerSlot>（不是 int！）
            Method getSlots = itemStackHandlerCls.getMethod("getSlots");
            Method getStackInSlot = itemStackHandlerCls.getMethod("getStackInSlot", int.class);
            Method setStackInSlot = itemStackHandlerCls.getMethod("setStackInSlot", int.class, ItemStack.class);

            REFS = new BackpackRefs("Fabric", fromStack, wrapperCtor, getInventoryHandler,
                    getSlots, getStackInSlot, setStackInSlot,
                    null /* 无 extractItem */, null /* 无 insertItem */, false);
            VoiceBlockMod.LOGGER.info("[VoiceBlock] curio: 用 Fabric 路径反射成功（porting_lib ItemStackHandler 接口）");
            return REFS;
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] curio: Fabric porting_lib ItemStackHandler 反射也失败，curio 源不可用", t);
        }
        return null;
    }

    /**
     * SB 反射缓存。
     * useIItemHandlerExtract = true 时走 IItemHandler.extractItem/insertItem（Forge/NeoForge），
     * = false 时用 setStackInSlot 手工 split/merge（Fabric）。
     */
    private static final class BackpackRefs {
        final String platform;
        final Method fromStack;          // 旧版 SB：BackpackWrapper.fromStack(ItemStack) → IBackpackWrapper（可能为 null）
        final Constructor<?> wrapperCtor; // 新版 SB：BackpackWrapper(ItemStack) 构造器（可能为 null）
        final Method getInventoryHandler; // BackpackWrapper.getInventoryHandler() → handler
        final Method getSlots;           // () → int（Forge）或 List（Fabric）
        final Method getStackInSlot;     // (int) → ItemStack
        final Method setStackInSlot;     // (int, ItemStack) → void
        final Method extractItem;        // (int, int, boolean) → ItemStack, 仅 Forge
        final Method insertItem;         // (int, ItemStack, boolean) → ItemStack, 仅 Forge
        final boolean useIItemHandlerExtract;

        BackpackRefs(String platform, Method fromStack, Constructor<?> wrapperCtor,
                     Method getInventoryHandler,
                     Method getSlots, Method getStackInSlot, Method setStackInSlot,
                     Method extractItem, Method insertItem, boolean useIItemHandlerExtract) {
            this.platform = platform;
            this.fromStack = fromStack;
            this.wrapperCtor = wrapperCtor;
            this.getInventoryHandler = getInventoryHandler;
            this.getSlots = getSlots;
            this.getStackInSlot = getStackInSlot;
            this.setStackInSlot = setStackInSlot;
            this.extractItem = extractItem;
            this.insertItem = insertItem;
            this.useIItemHandlerExtract = useIItemHandlerExtract;
        }

        /**
         * 给一个 ItemStack 拿到它的 SB inventory handle；非 SB 物品返回 null。
         * 旧版 SB 走 {@code fromStack(ItemStack)} 静态工厂；新版 SB（3.26+）走 public 构造器。
         * 拿到 wrapper 后统一调 {@code getInventoryHandler()}。调用方已按物品 ID 过滤为真背包。
         */
        Object openBackpack(ItemStack stack) {
            try {
                Object wrapper = (fromStack != null)
                        ? fromStack.invoke(null, stack)
                        : wrapperCtor.newInstance(stack);
                if (wrapper == null) return null;
                return getInventoryHandler.invoke(wrapper);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * 拿 handler 的槽位数。兼容 getSlots 返回 int（Forge）或 List（Fabric）。
         */
        int getSlotCount(Object handler) throws Exception {
            Object r = getSlots.invoke(handler);
            if (r instanceof Integer) return (Integer) r;
            if (r instanceof List) return ((List<?>) r).size();
            return 0;
        }
    }
}
