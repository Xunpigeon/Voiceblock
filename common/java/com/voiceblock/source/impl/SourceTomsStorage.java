package com.voiceblock.source.impl;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SoftDependCache;
import com.voiceblock.source.SourceConfig;
import com.voiceblock.source.SourceManager;
import com.voiceblock.source.SourceResult;
import com.voiceblock.source.SourceServerAccess;
import com.voiceblock.util.ClassLookup;
import com.voiceblock.util.InventoryPlacer;
import com.voiceblock.util.ItemResolver;
import com.voiceblock.util.ServerThreadExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tom's Storage 源（tomsstorage，软依赖，默认开，优先级 6）。
 *
 * <p><b>半径</b>：从 {@code sources.json} 配置读，默认 8 格（与 nearbyChest 独立）。
 * 如果配置没设，{@link SourceManager#getDefaultRadius} 给 8。</p>
 *
 * <p><b>跨线程</b>：BE 扫描必须通过 {@link ServerThreadExecutor} 分发到服务端线程，
 * 否则 {@code ServerLevel.getBlockEntity()} 在客户端线程访问 chunk 系统拿不到数据。</p>
 *
 * <p><b>Tom's Storage 2.4.2 (1.21) 类路径是 {@code com.tom.storagemod.block.entity.*}</b>，老版本
 * 的 {@code com.tom.storagemod.tile.*} 也保留兜底。</p>
 */
public class SourceTomsStorage implements ItemSource {

    private static final String[] MOD_CLASSES = {
            // 1.21+ (Tom's Simple Storage - NEW) - com.tom.storagemod.StorageMod
            "com.tom.storagemod.StorageMod",
            // 老版本保底
            "com.tom.storagemod.TomsMod",
            "com.tom.storagemod.TomsStorageModLoader"
    };

    private static final String[] BE_CLASSES = {
            // 1.20.1 (Tom's Simple Storage 旧 = 1.7.x) 真实路径：com.tom.storagemod.tile.*
            "com.tom.storagemod.tile.OpenCrateBlockEntity",
            "com.tom.storagemod.tile.StorageTerminalBlockEntity",
            "com.tom.storagemod.tile.CraftingTerminalBlockEntity",
            "com.tom.storagemod.tile.InventoryConnectorBlockEntity",
            "com.tom.storagemod.tile.InventoryProxyBlockEntity",
            "com.tom.storagemod.tile.InventoryCableConnectorBlockEntity",
            "com.tom.storagemod.tile.AbstractInventoryHopperBlockEntity",
            "com.tom.storagemod.tile.AbstractInventoryCableConnectorBlockEntity",
            "com.tom.storagemod.tile.LevelEmitterBlockEntity",
            // 1.21+ (Tom's Simple Storage - NEW) com.tom.storagemod.block.entity.*
            "com.tom.storagemod.block.entity.OpenCrateBlockEntity",
            "com.tom.storagemod.block.entity.StorageTerminalBlockEntity",
            "com.tom.storagemod.block.entity.CraftingTerminalBlockEntity",
            "com.tom.storagemod.block.entity.InventoryConnectorBlockEntity",
            "com.tom.storagemod.block.entity.InventoryProxyBlockEntity",
            "com.tom.storagemod.block.entity.InventoryCableConnectorBlockEntity",
            "com.tom.storagemod.block.entity.InventoryInterfaceBlockEntity",
            "com.tom.storagemod.block.entity.AbstractInventoryHopperBlockEntity",
            "com.tom.storagemod.block.entity.LevelEmitterBlockEntity"
    };

    private static final String[] ITEM_IDS = {
            "toms_storage:storage_terminal",
            "toms_storage:trim",
            "toms_storage:inventory_cable",
            "toms_storage:storage_access_point",
            "toms_storage:storage_observer",
            "toms_storage:open_crate"
    };

    private static final int DEFAULT_RADIUS = 8;
    private static final String GET_PLATFORM_HANDLER = "getPlatformHandler";
    private static final String GET_MERGED_HANDLER = "getMergedHandler";
    private static final String GET_INVENTORY_ACCESS = "getInventoryAccess";

    @Override
    public String name() { return "tomsstorage"; }

    @Override
    public Component displayName() { return Component.literal("汤姆的简易储存"); }

    @Override
    public boolean isAvailable() {
        // 必须同时满足：mod class 存在 + 至少一个 BE class 能加载。
        // 否则 isAvailable=true 但 take() 直接被 SoftDependCache.loadClass 拒掉，
        // 给用户误导性的「Available=是/实际=跳过」对不上。
        boolean modLoaded = false;
        for (String cls : MOD_CLASSES) {
            if (SoftDependCache.isLoaded(cls)) { modLoaded = true; break; }
        }
        if (!modLoaded) {
            for (String id : ITEM_IDS) {
                if (ItemResolver.resolve(id) != null) { modLoaded = true; break; }
            }
        }
        if (!modLoaded) return false;

        for (String cls : BE_CLASSES) {
            if (SoftDependCache.loadClass(cls) != null) return true;
        }
        return false;
    }

    @Override
    public SourceResult take(Player player, String itemId, int desiredAmount) {
        Item target = ItemResolver.resolve(itemId);
        if (target == null) return SourceResult.notHandled();

        ServerPlayer sp = SourceServerAccess.getServerPlayer(player);
        if (sp == null) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 服务端玩家不可用（多人模式？），跳过");
            return SourceResult.notHandled();
        }
        MinecraftServer server = sp.getServer();
        ServerLevel level = sp.serverLevel();
        BlockPos center = sp.blockPosition();

        // === 生存模式 + 手持绑定过的 Tom 无线终端：无视距离，直接走绑定终端的存储网络 ===
        // 创造模式保持原有半径逻辑不变。返回 null 表示不适用，继续往下走半径扫描。
        if (!sp.isCreative()) {
            SourceResult wireless = tryWirelessTerminal(server, sp, target, desiredAmount);
            if (wireless != null) return wireless;
        }

        // 半径从配置读（默认 8）
        SourceConfig cfg = SourceManager.getInstance().configs().get(name());
        final int radius = (cfg != null && cfg.radius() != null) ? cfg.radius() : DEFAULT_RADIUS;

        List<Class<?>> beClasses = new ArrayList<>();
        for (String cls : BE_CLASSES) {
            Class<?> c = SoftDependCache.loadClass(cls);
            if (c != null) beClasses.add(c);
        }
        if (beClasses.isEmpty()) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 找不到 Tom's Storage 的 TE class，跳过");
            return SourceResult.notHandled();
        }

        // 把 BE 扫描放到服务端线程
        record ScanResult(List<FoundTE> found, int totalBes, int tomBes) {}
        ScanResult result = ServerThreadExecutor.run(server, () -> {
            List<FoundTE> found = new ArrayList<>();
            AtomicInteger total = new AtomicInteger();
            AtomicInteger tom = new AtomicInteger();

            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius))) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null) continue;
                total.incrementAndGet();
                boolean isTom = false;
                for (Class<?> c : beClasses) {
                    if (c.isInstance(be)) { isTom = true; break; }
                }
                if (!isTom) continue;
                tom.incrementAndGet();
                TomInv inv = openTomInventory(be);
                if (inv == null) continue;
                if (invContains(inv, target)) {
                    found.add(new FoundTE(inv, pos.distSqr(center)));
                }
            }
            return new ScanResult(found, total.get(), tom.get());
        }, new ScanResult(List.of(), 0, 0));

        if (result.totalBes() == 0) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 半径 {} 内一个 BE 都没扫到（chunk 没加载？）", radius);
        } else {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 半径 {} 扫到 {} 个 BE（{} Tom TE）",
                    radius, result.totalBes(), result.tomBes());
        }

        if (result.found().isEmpty()) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 半径 {} 内未找到含 {} 的 Tom 容器", radius, target);
            return SourceResult.notHandled();
        }

        List<FoundTE> foundSorted = new ArrayList<>(result.found());
        foundSorted.sort(Comparator.comparingDouble(FoundTE::distSqr));
        VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 找到 {} 个含 {} 的 Tom TE，开始取物", foundSorted.size(), target);

        // 取物也要在服务端线程
        AtomicInteger totalTaken = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(desiredAmount);
        StringBuilder failReason = new StringBuilder();

        Boolean ok = ServerThreadExecutor.run(server, () -> {
            for (FoundTE fc : foundSorted) {
                if (remaining.get() <= 0) break;
                ItemStack extracted = takeFromTomInv(fc.inv, target, remaining.get());
                if (extracted.isEmpty()) continue;
                int placed = InventoryPlacer.placeIntoPlayer(sp, extracted);
                totalTaken.addAndGet(placed);
                remaining.addAndGet(-placed);
                if (placed < extracted.getCount()) {
                    int back = extracted.getCount() - placed;
                    if (back > 0) {
                        ItemStack toReturn = extracted.copy();
                        toReturn.setCount(back);
                        putBackTomInv(fc.inv, toReturn);
                    }
                    failReason.append("身上已满");
                    return false;
                }
            }
            return true;
        }, Boolean.FALSE);

        int total = totalTaken.get();
        VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 实际取出 {}x{}", total, target);
        if (total > 0 && ok) return SourceResult.ok(total);
        if (failReason.length() > 0) return SourceResult.fail(failReason.toString());
        return SourceResult.notHandled();
    }

    // ============= TomInv 适配层 =============

    private interface TomInv {
        int slotCount();
        ItemStack getStackInSlot(int i);
        ItemStack extract(int i, int amount);
        void insert(int i, ItemStack stack);
    }

    private TomInv openContainer(BlockEntity be) {
        if (!(be instanceof Container c)) return null;
        return new TomInv() {
            @Override public int slotCount() { return c.getContainerSize(); }
            @Override public ItemStack getStackInSlot(int i) { return c.getItem(i); }
            @Override public ItemStack extract(int i, int amount) { return c.removeItem(i, amount); }
            @Override public void insert(int i, ItemStack s) { c.setItem(i, s); }
        };
    }

    private TomInv openViaIInventoryAccess(BlockEntity be) {
        try {
            Object access = null;
            for (String methodName : new String[]{GET_MERGED_HANDLER, GET_INVENTORY_ACCESS}) {
                try {
                    Method m = be.getClass().getMethod(methodName);
                    access = m.invoke(be);
                    if (access != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (access == null) return null;

            Method getPlatform = access.getClass().getMethod(GET_PLATFORM_HANDLER);
            Object handler = getPlatform.invoke(access);
            if (handler == null) return null;

            try {
                Class<?> iih = Class.forName("net.neoforged.neoforge.items.IItemHandler");
                if (iih.isInstance(handler)) {
                    final Object h = handler;
                    Method getSlots = iih.getMethod("getSlots");
                    Method getStack = iih.getMethod("getStackInSlot", int.class);
                    Method extract = iih.getMethod("extractItem", int.class, int.class, boolean.class);
                    Method insert = iih.getMethod("insertItem", int.class, ItemStack.class, boolean.class);
                    return new TomInv() {
                        @Override public int slotCount() { try { return (int) getSlots.invoke(h); } catch (Exception e) { return 0; } }
                        @Override public ItemStack getStackInSlot(int i) { try { return (ItemStack) getStack.invoke(h, i); } catch (Exception e) { return ItemStack.EMPTY; } }
                        @Override public ItemStack extract(int i, int amount) { try { return (ItemStack) extract.invoke(h, i, amount, false); } catch (Exception e) { return ItemStack.EMPTY; } }
                        @Override public void insert(int i, ItemStack s) { try { insert.invoke(h, i, s, false); } catch (Exception ignored) {} }
                    };
                }
            } catch (Throwable ignored) {}

            if (handler instanceof Container c) {
                final Container cc = c;
                return new TomInv() {
                    @Override public int slotCount() { return cc.getContainerSize(); }
                    @Override public ItemStack getStackInSlot(int i) { return cc.getItem(i); }
                    @Override public ItemStack extract(int i, int amount) { return cc.removeItem(i, amount); }
                    @Override public void insert(int i, ItemStack s) { cc.setItem(i, s); }
                };
            }
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.debug("[VoiceBlock] tomsstorage: openViaIInventoryAccess 失败", e);
        }
        return null;
    }

    private TomInv openTomInventory(BlockEntity be) {
        TomInv c = openContainer(be);
        if (c != null) return c;
        return openViaIInventoryAccess(be);
    }

    private boolean invContains(TomInv inv, Item target) {
        int slots = inv.slotCount();
        for (int i = 0; i < slots; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.is(target)) return true;
        }
        return false;
    }

    private ItemStack takeFromTomInv(TomInv inv, Item target, int amount) {
        ItemStack extracted = ItemStack.EMPTY;
        int remaining = amount;
        int slots = inv.slotCount();
        for (int i = 0; i < slots && remaining > 0; i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty() || !slot.is(target)) continue;
            int canTake = Math.min(slot.getCount(), remaining);
            ItemStack took = inv.extract(i, canTake);
            if (took.isEmpty()) continue;
            remaining -= took.getCount();
            if (extracted.isEmpty()) extracted = took.copy();
            else extracted.grow(took.getCount());
        }
        return extracted;
    }

    private void putBackTomInv(TomInv inv, ItemStack stack) {
        if (stack.isEmpty()) return;
        int slots = inv.slotCount();
        for (int i = 0; i < slots && !stack.isEmpty(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (!slot.isEmpty() && slot.getItem() == stack.getItem()) {
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space > 0) {
                    int toMove = Math.min(space, stack.getCount());
                    ItemStack merged = slot.copy();
                    merged.grow(toMove);
                    inv.insert(i, merged);
                    stack.shrink(toMove);
                }
            }
        }
        for (int i = 0; i < slots && !stack.isEmpty(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty()) {
                inv.insert(i, stack);
                stack = ItemStack.EMPTY;
                return;
            }
        }
    }

    private record FoundTE(TomInv inv, double distSqr) {}

    // ============= 无线终端（生存模式，无视距离）=============

    /**
     * 生存模式下若玩家身上（主背包 / 快捷栏 / 盔甲栏 / 副手）携带「已绑定到某个存储终端」的
     * Tom 无线终端，则直接通过绑定终端的 {@code StorageTerminalBlockEntity.pullStack(...)}
     * 从整张存储网络取物 —— 不用关心玩家离终端/箱子多远。
     *
     * <p>返回 {@code null} 表示「不适用 / 未命中」，调用方应继续走半径扫描：
     * 未装 Tom、身上没有绑定过的无线终端、绑定方块不是存储终端、绑定区块未加载、
     * 网络里没有该物品、或反射异常。返回非 null 表示已处理（成功 / 身上已满）。</p>
     *
     * <p>仅作用于生存模式；创造模式仍走原有半径逻辑。</p>
     */
    private SourceResult tryWirelessTerminal(MinecraftServer server, ServerPlayer sp,
                                             Item target, int desiredAmount) {
        WirelessRefs refs = WirelessRefs.getOrInit();
        if (refs == null) return null;

        ItemStack terminal = refs.findTerminalOnPerson(sp);
        if (terminal == null) return null;

        return ServerThreadExecutor.run(server, () -> {
            WirelessRefs.Bound bound = refs.readBound(terminal);
            if (bound == null) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 身上的无线终端未绑定存储终端，回退半径扫描");
                return null;
            }
            BlockEntity be = refs.resolveBlockEntity(server, bound);
            if (be == null || !refs.terminalCls.isInstance(be)) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 无线终端绑定的存储终端不在已加载区块（维度={} 坐标={}），回退半径扫描",
                        bound.dimStr(), bound.pos());
                return null;
            }
            try {
                Map<?, ?> stacks = (Map<?, ?>) refs.getStacks.invoke(be);
                Object matched = null;
                for (Object key : stacks.keySet()) {
                    Object st = refs.storedGetStack.invoke(key);
                    if (st instanceof ItemStack is && !is.isEmpty() && is.getItem() == target) {
                        matched = key;
                        break;
                    }
                }
                if (matched == null) {
                    VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 绑定网络中没有 {}，回退半径扫描", target);
                    return null;
                }

                Object pulled = refs.pullStack.invoke(be, matched, (long) desiredAmount);
                if (pulled == null) return null;
                Object actual = refs.storedGetActualStack.invoke(pulled);
                if (!(actual instanceof ItemStack pulledStack) || pulledStack.isEmpty()) return null;

                int placed = InventoryPlacer.placeIntoPlayer(sp, pulledStack);
                if (placed < pulledStack.getCount()) {
                    int back = pulledStack.getCount() - placed;
                    refs.pushStackItemStack.invoke(be, pulledStack.copyWithCount(back));
                    if (placed <= 0) return SourceResult.fail("身上已满");
                }
                VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 无线终端从绑定网络取出 {}x{}", placed, target);
                return placed > 0 ? SourceResult.ok(placed) : null;
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.warn("[VoiceBlock] tomsstorage: 无线终端取物异常，回退半径扫描", t);
                return null;
            }
        }, (SourceResult) null);
    }

    /**
     * 无线终端反射缓存（Tom's Storage）。
     * 任意一步失败（Tom 未装 / 版本不支持）都返回 null，功能自动降级为半径扫描，不报错。
     *
     * <p><b>绑定位置的两套存储方式</b>：
     * <ul>
     *   <li><b>1.7.x（1.20.1）</b>：绑定信息直接写在物品 NBT 里 ——
     *       {@code BindX}(int) / {@code BindY}(int) / {@code BindZ}(int) / {@code BindDim}(string)。</li>
     *   <li><b>1.8+（1.21）</b>：绑定信息走数据组件
     *       {@code Content.boundPosComponent}（{@code DataComponentType<WorldPos>}）。</li>
     * </ul>
     * 两条路径都会尝试，命中即用，互不影响。</p>
     */
    private static final class WirelessRefs {
        private static boolean INIT_TRIED = false;
        private static WirelessRefs INSTANCE;

        final Class<?> terminalCls;              // StorageTerminalBlockEntity（tile.* / block.entity.*）
        final Method getStacks;                  // StorageTerminalBlockEntity.getStacks()
        final Method pullStack;                  // pullStack(StoredItemStack, long) → StoredItemStack
        final Method pushStackItemStack;         // pushStack(ItemStack) → ItemStack
        final Method storedGetStack;             // StoredItemStack.getStack() → ItemStack
        final Method storedGetActualStack;       // StoredItemStack.getActualStack() → ItemStack
        final Method itemGetTag;                 // ItemStack.getTag()（1.20.x 有，1.21 已移除 → 可能为 null）
        // === 1.8+ 组件式绑定（可选，命中即为 1.21）===
        final Object boundPosComponent;          // Content.boundPosComponent → DataComponentType<WorldPos>
        final Method stackGet;                   // ItemStack.get(DataComponentType)
        final Method worldPosDim;                // WorldPos.dim() → ResourceKey<Level>
        final Method worldPosPos;                // WorldPos.pos() → BlockPos

        private WirelessRefs(Class<?> terminalCls, Method getStacks, Method pullStack,
                             Method pushStackItemStack, Method storedGetStack, Method storedGetActualStack,
                             Method itemGetTag, Object boundPosComponent, Method stackGet,
                             Method worldPosDim, Method worldPosPos) {
            this.terminalCls = terminalCls;
            this.getStacks = getStacks;
            this.pullStack = pullStack;
            this.pushStackItemStack = pushStackItemStack;
            this.storedGetStack = storedGetStack;
            this.storedGetActualStack = storedGetActualStack;
            this.itemGetTag = itemGetTag;
            this.boundPosComponent = boundPosComponent;
            this.stackGet = stackGet;
            this.worldPosDim = worldPosDim;
            this.worldPosPos = worldPosPos;
        }

        static synchronized WirelessRefs getOrInit() {
            if (INIT_TRIED) return INSTANCE;
            INIT_TRIED = true;
            try {
                // 终端类路径：1.7.x 为 com.tom.storagemod.tile.*，1.8+ 为 com.tom.storagemod.block.entity.*
                Class<?> termCls = loadFirst(
                        "com.tom.storagemod.tile.StorageTerminalBlockEntity",
                        "com.tom.storagemod.block.entity.StorageTerminalBlockEntity");
                // StoredItemStack：1.7.x 为 com.tom.storagemod.util.*，1.8+ 为 com.tom.storagemod.inventory.*
                Class<?> storedCls = loadFirst(
                        "com.tom.storagemod.util.StoredItemStack",
                        "com.tom.storagemod.inventory.StoredItemStack");
                if (termCls == null || storedCls == null) {
                    throw new ClassNotFoundException("StorageTerminalBlockEntity / StoredItemStack");
                }

                Method getStacks = termCls.getMethod("getStacks");
                Method pullStack = termCls.getMethod("pullStack", storedCls, long.class);
                Method pushStackItemStack = termCls.getMethod("pushStack", ItemStack.class);
                Method storedGetStack = storedCls.getMethod("getStack");
                Method storedGetActualStack = storedCls.getMethod("getActualStack");

                // 1.8+ 组件式绑定（可选；1.7.x 上这些类不存在，取不到就只走 NBT）
                Object boundPosComponent = null;
                Method stackGet = null, worldPosDim = null, worldPosPos = null;
                try {
                    Class<?> contentType = ClassLookup.forName("net.minecraft.core.component.DataComponentType",
                            "net.minecraft.class_9331");
                    Object go = Class.forName("com.tom.storagemod.Content").getField("boundPosComponent").get(null);
                    Object compType = go.getClass().getMethod("get").invoke(go);
                    Class<?> worldPosCls = Class.forName("com.tom.storagemod.components.WorldPos");
                    stackGet = ClassLookup.method(ItemStack.class, new String[]{"get", "method_57824"}, contentType);
                    worldPosDim = worldPosCls.getMethod("dim");
                    worldPosPos = worldPosCls.getMethod("pos");
                    boundPosComponent = compType;
                } catch (Throwable ignored) {
                    boundPosComponent = null;
                    stackGet = null;
                    worldPosDim = null;
                    worldPosPos = null;
                }

                // 1.7.x NBT 式绑定：ItemStack.getTag() 返回 CompoundTag。
                // 方法名：官方 getTag / Forge SRG m_41783_ / Fabric intermediary method_7969
                // （注意 method_7954 是 getDisplayName，写错会导致功能静默失效）。
                // 1.21 无此方法 → 自动为 null，只走组件式。
                Method itemGetTag = null;
                for (String n : new String[]{"getTag", "m_41783_", "method_7969"}) {
                    try {
                        itemGetTag = ItemStack.class.getMethod(n);
                        break;
                    } catch (Throwable ignored) {}
                }

                INSTANCE = new WirelessRefs(termCls, getStacks, pullStack, pushStackItemStack,
                        storedGetStack, storedGetActualStack, itemGetTag,
                        boundPosComponent, stackGet, worldPosDim, worldPosPos);
                VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 无线终端支持已就绪（终端类={}，绑定方式={}）",
                        termCls.getName(),
                        boundPosComponent != null ? "组件+可能的NBT" : "NBT");
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] tomsstorage: 无线终端支持不可用（Tom 未装或版本不支持）：{}", t.toString());
                INSTANCE = null;
            }
            return INSTANCE;
        }

        private static Class<?> loadFirst(String... names) {
            for (String n : names) {
                Class<?> c = SoftDependCache.loadClass(n);
                if (c != null) return c;
            }
            return null;
        }

        /**
         * 玩家身上（主背包 + 快捷栏 + 盔甲栏 + 副手）是否持有「已绑定的 Tom 无线终端」。
         * <p>判定方式：物品是否带有绑定信息（1.7.x 为 NBT {@code BindX}，1.8+ 为
         * {@code boundPosComponent} 数据组件）—— 只有对着存储终端绑定过的无线终端才会有。
         * 出现在身上任意位置即可，不必手持。</p>
         */
        ItemStack findTerminalOnPerson(ServerPlayer sp) {
            Inventory inv = sp.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && readBound(stack) != null) return stack;
            }
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
                ItemStack stack = sp.getItemBySlot(slot);
                if (!stack.isEmpty() && readBound(stack) != null) return stack;
            }
            return null;
        }

        /** 绑定信息：维度 id 字符串 + 方块坐标。 */
        record Bound(String dimStr, BlockPos pos) {}

        /** 读取无线终端绑定位置；未绑定返回 null。组件式优先，其次 1.7.x 的 NBT 式。 */
        Bound readBound(ItemStack terminal) {
            // 1.8+：数据组件 WorldPos
            if (boundPosComponent != null && stackGet != null && worldPosDim != null && worldPosPos != null) {
                try {
                    Object worldPos = stackGet.invoke(terminal, boundPosComponent);
                    if (worldPos != null) {
                        Object dimKey = worldPosDim.invoke(worldPos);
                        Object posObj = worldPosPos.invoke(worldPos);
                        if (dimKey instanceof ResourceKey<?> rk && posObj instanceof BlockPos bp) {
                            return new Bound(rk.location().toString(), bp);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // 1.7.x：物品 NBT BindX / BindY / BindZ / BindDim
            if (itemGetTag != null) {
                try {
                    Object tagObj = itemGetTag.invoke(terminal);
                    if (tagObj instanceof CompoundTag tag && tag.contains("BindX")) {
                        BlockPos pos = new BlockPos(tag.getInt("BindX"), tag.getInt("BindY"), tag.getInt("BindZ"));
                        String dim = tag.getString("BindDim");
                        if (!dim.isEmpty()) return new Bound(dim, pos);
                    }
                } catch (Throwable ignored) {}
            }
            return null;
        }

        /**
         * 解析绑定坐标对应的 BlockEntity；维度未加载 / 方块无 BE 返回 null。
         * 用 {@code server.getAllLevels()} 按维度 id 字符串匹配，避免依赖 Registries.DIMENSION 字段。
         */
        BlockEntity resolveBlockEntity(MinecraftServer server, Bound bound) {
            if (server == null || bound == null || bound.dimStr() == null) return null;
            ServerLevel sl = null;
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension().location().toString().equals(bound.dimStr())) {
                    sl = l;
                    break;
                }
            }
            return sl == null ? null : sl.getBlockEntity(bound.pos());
        }
    }
}