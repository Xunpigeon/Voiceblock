package com.voiceblock.source.impl;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.source.ItemSource;
import com.voiceblock.source.SoftDependCache;
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

import java.lang.reflect.Method;

/**
 * AE2 源（appliedenergistics2，软依赖，默认开，优先级 5）。
 * <p>
 * 取物前置：玩家身上必须有无线终端（服务端 inventory 里有）。
 * 跨维度：由 AE2 自己处理（无线终端支持跨维度）。
 * <p>
 * 反射链：AEApi → registries → players → findNetworking → getGrid → IStorageService → extract。
 * AE2 API 跨版本可能变化，try/catch 失败一律 fallback fail。
 *
 * <p><b>服务端访问</b>：AE2 网络数据完全在服务端，必须用 {@link ServerPlayer}（在单玩家下通过
 * {@link SourceServerAccess} 把客户端 Player 转换为服务端 ServerPlayer）。直接传客户端 Player
 * 会让 AE2 找不到玩家在哪个网络里。</p>
 */
public class SourceAE2 implements ItemSource {

    private static final String[] AE_API_CLASSES = {
            // 经典 + Unofficial 19.x 早期
            "appeng.api.AEApi",
            // Unofficial 19.2+ 内部 API
            "appeng.api.AEApiInternal",
            // Mod 主类（永远存在，用于检测）
            "appeng.core.AppEng",
            // 老 1.16 兼容
            "appeng.AE2Container",
            // 部分 fork (AE2 Things / AppliedFX 等)
            "de.mari_023.ae2wtlib.AE2wtlib"
    };
    private static final String[] WIRELESS_TERMINALS = {
            // 经典版（1.19 及之前）
            "appliedenergistics2:wireless_terminal",
            "appliedenergistics2:wireless_crafting_terminal",
            // AE2 Unofficial 1.20+（mod ID 改名）
            "ae2:wireless_terminal",
            "ae2:wireless_crafting_terminal",
            // 端口
            "ae2things:wireless_terminal",
            "ae2things:wireless_crafting_terminal",
            // 无线收发器/库等也可能作为入口
            "appliedenergistics2:wireless_access_terminal",
            "ae2:wireless_access_terminal"
    };

    @Override
    public String name() { return "ae2"; }

    @Override
    public Component displayName() { return Component.literal("AE2"); }

    @Override
    public boolean isAvailable() {
        // AE2 19.2.x class 名经常变，class 检测不可靠。
        // 改用：class 检测 OR 物品 ID 检测（无线终端注册了说明 mod 装了）
        for (String cls : AE_API_CLASSES) {
            if (SoftDependCache.isLoaded(cls)) return true;
        }
        for (String id : WIRELESS_TERMINALS) {
            if (ItemResolver.resolve(id) != null) return true;
        }
        return false;
    }

    @Override
    public SourceResult take(Player player, String itemId, int desiredAmount) {
        Item target = ItemResolver.resolve(itemId);
        if (target == null) return SourceResult.notHandled();

        // ★ 关键：AE2 网络数据在服务端，必须拿到 ServerPlayer
        ServerPlayer sp = SourceServerAccess.getServerPlayer(player);
        if (sp == null) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: 服务端玩家不可用（多人模式？），跳过");
            return SourceResult.notHandled();
        }

        // 无线终端检测：用服务端 inventory（避免客户端副本滞后）
        if (!hasWirelessTerminal(sp)) {
            VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: 玩家身上没有无线终端，跳过");
            return SourceResult.notHandled();
        }

        // 找 API class（按版本历史顺序尝试）
        Class<?> apiCls = null;
        for (String cls : AE_API_CLASSES) {
            apiCls = SoftDependCache.loadClass(cls);
            if (apiCls != null) break;
        }
        if (apiCls == null) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] AE2: 无法找到 AEApi class（已尝试 {} 种命名）", AE_API_CLASSES.length);
            return SourceResult.notHandled();
        }

        try {
            // 1. AEApi.instance()
            Object api = apiCls.getMethod("instance").invoke(null);

            // 2. .registries() → IRegistries
            Object registries = api.getClass().getMethod("registries").invoke(api);

            // 3. .players() → IPlayerRegistry
            Object playersReg = registries.getClass().getMethod("players").invoke(registries);

            // 4. findNetworking(serverPlayer) → Optional<IPlayerRegistry.PlayerRegistration>
            Object opt = playersReg.getClass().getMethod("findNetworking", Player.class).invoke(playersReg, sp);
            if (opt == null) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: findNetworking 返回 null");
                return SourceResult.notHandled();
            }
            boolean isPresent = (boolean) opt.getClass().getMethod("isPresent").invoke(opt);
            if (!isPresent) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: 该玩家没有挂在任何 AE2 网络上");
                return SourceResult.notHandled();
            }
            Object registration = opt.getClass().getMethod("get").invoke(opt);

            // 5. .getGrid() → IGrid
            Object grid = registration.getClass().getMethod("getGrid").invoke(registration);
            if (grid == null) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: getGrid 返回 null（玩家联网但 grid 还没建好？）");
                return SourceResult.notHandled();
            }

            // 6. grid.getService(IStorageService.class)
            Class<?> storageCls = Class.forName("appeng.api.storage.IStorageService");
            Object storageService = grid.getClass().getMethod("getService", Class.class).invoke(grid, storageCls);
            if (storageService == null) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: IStorageService 不可用");
                return SourceResult.notHandled();
            }

            // 7. storageService.getInventory() → MEStorage
            Object meStorage = storageService.getClass().getMethod("getInventory").invoke(storageService);
            if (meStorage == null) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: MEStorage 不可用");
                return SourceResult.notHandled();
            }

            // 8. AEItemKey.of(target)
            Class<?> itemKeyCls = Class.forName("appeng.api.storage.AEItemKey");
            Object aeItemKey = itemKeyCls.getMethod("of", Item.class).invoke(null, target);
            if (aeItemKey == null) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: AEItemKey.of 返回 null for {}", target);
                return SourceResult.notHandled();
            }

            // 9. PlayerAction.source(serverPlayer) → IActionSource
            Class<?> playerActionCls = Class.forName("appeng.helpers.PlayerAction");
            Object actionSource = playerActionCls.getMethod("source", Player.class).invoke(null, sp);

            // 10. Actionable.MODULATE
            Class<?> actionableCls = Class.forName("appeng.api.storage.Actionable");
            Object actionable = actionableCls.getField("MODULATE").get(null);

            // 11. meStorage.extract(aeItemKey, amount, actionSource, actionable) → long
            Class<?> actionSrcCls = Class.forName("appeng.api.networking.security.IActionSource");
            Method extractM = meStorage.getClass().getMethod("extract",
                    itemKeyCls, long.class, actionSrcCls, actionableCls);
            long extracted = (long) extractM.invoke(meStorage, aeItemKey, (long) desiredAmount, actionSource, actionable);
            if (extracted <= 0) {
                VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: extract 返回 {}（网络里没有 {} 或权限不足）", extracted, target);
                return SourceResult.notHandled();
            }

            int taken = (int) Math.min(extracted, Integer.MAX_VALUE);
            ItemStack stack = new ItemStack(target, taken);
            // ★ 用服务端玩家放回身上（服务端 inventory 修改会自动同步客户端）
            int placed = InventoryPlacer.placeIntoPlayer(sp, stack);
            if (placed < taken) {
                // 身上满了：剩余部分留在 AE2 不回收（简化）
                return SourceResult.fail("身上已满（剩余 " + (taken - placed) + " 已留在 AE2）");
            }
            VoiceBlockMod.LOGGER.info("[VoiceBlock] ae2: 从 ME 网络取出 {}x{}", placed, target);
            return SourceResult.ok(placed);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] AE2 reflection failed: {}", e.toString());
            return SourceResult.notHandled();
        }
    }

    private boolean hasWirelessTerminal(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isWirelessTerminal(inv.getItem(i))) return true;
        }
        return isWirelessTerminal(player.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    private boolean isWirelessTerminal(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation rl = RegistryAccess.getItemKey(stack.getItem());
        if (rl == null) return false;
        String id = rl.toString();
        for (String term : WIRELESS_TERMINALS) {
            if (id.equals(term)) return true;
        }
        return false;
    }
}