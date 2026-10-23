package com.voiceblock.util;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 服务端侧物品栏操作工具（跨 1.20.1 / 1.21.x）。
 *
 * <p>从 {@link InventoryUtil} 里分出来跑在服务端线程上。联机时由
 * {@code VoiceActionHandler}（Forge 1.20.1 SimpleChannel / NeoForge 1.21 CustomPacketPayload）
 * 在服务端调用，所有写入直接调 ServerPlayer API。</p>
 *
 * <p><b>跨版本策略</b>：
 * <ul>
 *   <li>1.20.1 / 1.21 都有 {@code BuiltInRegistries}，物品解析用它。</li>
 *   <li>1.20.1 走 {@code stack.setHoverName(Component)}；1.21+ 也能用（虽然不是新的 DataComponents 路径）。</li>
 *   <li>1.20.1 的 {@code ClientboundContainerSetSlotPacket} 是 3 参构造，没有 stateId；
 *       这里不显式发包，统一靠 {@code containerMenu.broadcastChanges()} 让服务端同步。</li>
 * </ul>
 */
public final class ServerInventoryUtil {

    /**
     * 创造模式：替换玩家当前选中快捷栏格子的物品，数量固定为 1。
     */
    public static boolean replaceSelectedItem(ServerPlayer player, String blockOrItemId, String displayName) {
        if (player == null) return false;

        Item resolved = resolveItem(blockOrItemId);
        if (resolved == null) return false;

        ItemStack stack = new ItemStack(resolved, 1);
        if (displayName != null && !displayName.isBlank()) {
            applyDisplayName(stack, displayName);
        }

        int slot = player.getInventory().selected;
        player.getInventory().setItem(slot, stack);
        // 只改真实槽位，然后让 vanilla 自己做差分同步。
        // ⚠️ 千万不要在这里调 containerMenu.setRemoteSlot(...)：remoteSlots 是"客户端已知状态"的缓存，
        // broadcastChanges() 靠 (真实槽位 != remoteSlots) 判断要不要发包。提前把缓存改成新值，
        // 差分永远是 0，这个格子的同步包就永远不发 → 客户端手上显示空的（幽灵方块），
        // 但服务端认为东西在手上，右键还能放下去。
        syncInventory(player);
        VoiceBlockMod.LOGGER.debug("[VoiceBlock] server creative: gave {}x {} into slot {}",
                stack.getCount(), blockOrItemId, slot);
        return true;
    }

    /**
     * 生存模式：在玩家背包中找到目标物品，并把它的整组堆移到选中快捷栏槽。
     * 用直接的 setItem + broadcastChanges，绕开 SWAP clicktype。
     */
    public static boolean swapWithInventory(ServerPlayer player, String blockOrItemId) {
        if (player == null) return false;
        Item resolved = resolveItem(blockOrItemId);
        if (resolved == null) return false;

        Inventory inv = player.getInventory();
        int selected = inv.selected;
        int foundSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(resolved)) { foundSlot = i; break; }
        }
        if (foundSlot == -1) return false;
        if (foundSlot == selected) return true;

        // 先各拷一份再交换，避免 setItem 之后读到已变的引用
        ItemStack fromInv = inv.getItem(foundSlot).copy();
        ItemStack fromSelected = inv.getItem(selected).copy();

        // 一次性改两个槽位（原位置和选中格），然后让 vanilla 一次性差分同步。
        // 这里同样不能预先 setRemoteSlot：会导致选中格的同步包被永久吞掉（幽灵方块）。
        inv.setItem(foundSlot, fromSelected);
        inv.setItem(selected, fromInv);

        syncInventory(player);
        VoiceBlockMod.LOGGER.debug("[VoiceBlock] server survival swap: slot {} <-> slot {}",
                foundSlot, selected);
        return true;
    }

    /**
     * 把服务端背包改动立刻同步给客户端。
     *
     * <p>vanilla 的 {@code broadcastChanges()} 只发"真实槽位与 remoteSlots 缓存不一致"的格子，
     * 所以调用前不能手动改 remoteSlots（见上面两处注释）。</p>
     *
     * <p>另外 {@code containerMenu} 在某些状态下（比如玩家开着别的容器）可能不是 inventoryMenu，
     * 这里统一用 {@code player.inventoryMenu} 兜底，保证 36 个背包格 + 快捷栏都会同步。</p>
     */
    private static void syncInventory(ServerPlayer player) {
        try {
            player.containerMenu.broadcastChanges();
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.warn("[VoiceBlock] broadcastChanges failed: {}", t.toString());
        }
        // 如果玩家此刻开着别的容器（箱子/背包），inventoryMenu 的 remoteSlots 也需要对齐，
        // 否则关闭容器后会拿着旧数据渲染。
        try {
            if (player.containerMenu != player.inventoryMenu) {
                player.inventoryMenu.broadcastChanges();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 跨端取入口：如果 player 是 ServerPlayer 走这里，否则让客户端 InventoryUtil 处理
     * （单玩家模式也走这里，因为 LocalPlayer 的逻辑也已经迁到服务端）。
     */
    public static boolean replaceSelectedItemAnySide(Player player, String blockOrItemId, String displayName) {
        if (player instanceof ServerPlayer sp) return replaceSelectedItem(sp, blockOrItemId, displayName);
        return InventoryUtil.replaceSelectedItem(player, blockOrItemId, displayName);
    }

    public static boolean swapWithInventoryAnySide(Player player, String blockOrItemId) {
        if (player instanceof ServerPlayer sp) return swapWithInventory(sp, blockOrItemId);
        return InventoryUtil.swapWithInventory(player, blockOrItemId);
    }

    /**
     * 通过 ResourceLocation 解析 Item。优先 ITEM 注册表，找不到再尝试 BLOCK 的 asItem。
     * 反射方式（{@link RegistryAccess}）避免 Forge 1.20.1 访问转换器重命名字段导致的 NoSuchFieldError。
     */
    private static Item resolveItem(String blockOrItemId) {
        ResourceLocation rl = ResourceLocations.tryParse(blockOrItemId);
        if (rl == null) return null;
        var itemOpt = RegistryAccess.getOptionalItem(rl);
        if (itemOpt.isPresent()) return itemOpt.get();
        var blockOpt = RegistryAccess.getOptionalBlock(rl);
        if (blockOpt.isPresent()) {
            Item blockItem = blockOpt.get().asItem();
            if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR) return blockItem;
        }
        return null;
    }

    /**
     * 跨版本设置物品显示名：
     * <ul>
     *   <li>1.20.5+ 走 DataComponents.CUSTOM_NAME（ItemStack.set(DataComponentType, Object)）</li>
     *   <li>1.20.1-1.20.4 走 setHoverName(Component)</li>
     * </ul>
     * 用反射兜底，避免在 common 中同时依赖两套 API。
     */
    private static void applyDisplayName(ItemStack stack, String name) {
        Component cmp = Component.literal(name);
        // 1.20.5+ DataComponents 路径（类名/成员名均带 Fabric intermediary 候选）
        try {
            Class<?> dcClass = ClassLookup.forName("net.minecraft.core.component.DataComponents", "net.minecraft.class_9334");
            Field customNameField = ClassLookup.field(dcClass, "CUSTOM_NAME", "field_49631");
            Class<?> dcType = ClassLookup.forName("net.minecraft.core.component.DataComponentType", "net.minecraft.class_9331");
            Method setMethod = ClassLookup.method(ItemStack.class, new String[]{"set", "method_57379"}, dcType, Object.class);
            if (customNameField == null || setMethod == null) throw new NoSuchFieldException("CUSTOM_NAME/set");
            setMethod.invoke(stack, customNameField.get(null), cmp);
            return;
        } catch (Throwable ignored) {
            // 1.20.5 之前没 DataComponents，回退 setHoverName
        }
        // 1.20.1 官方名为 setHoverName；Forge reobf 后为 SRG m_41714_；Fabric 生产环境为 intermediary method_7977
        for (String methodName : new String[]{"setHoverName", "m_41714_", "method_7977"}) {
            try {
                ItemStack.class.getMethod(methodName, Component.class).invoke(stack, cmp);
                return;
            } catch (Throwable ignored) {}
        }
        VoiceBlockMod.LOGGER.warn("[VoiceBlock] applyDisplayName: neither DataComponents nor setHoverName available, name skipped");
    }

    private ServerInventoryUtil() {}
}