package com.voiceblock.source;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 物品源：把目标物品放到玩家身上。
 * <p>
 * v2.0 共 6 个实现：inventory / curio / enderchest / ae2 / tomsstorage / nearbyChest。
 * 各个实现按自己的规则尝试取物，由 SourceManager 按优先级组合调用。
 */
public interface ItemSource {

    /** 源标识（用于配置和命令），如 "inventory" / "curio" */
    String name();

    /** 给玩家看的名字，如 "背包" / "精妙背包" */
    Component displayName();

    /** 该源在当前环境中是否可用（软依赖 mod 是否已装等）。 */
    boolean isAvailable();

    /**
     * 尝试把目标物品放到玩家身上。
     *
     * @param player        玩家
     * @param itemId        目标物品 ID，如 "minecraft:stone"
     * @param desiredAmount 期望数量（创造模式固定 1，生存模式为 maxStack）
     * @return 取物结果
     * <ul>
     *   <li>{@link SourceResult#ok(int)} — 成功</li>
     *   <li>{@link SourceResult#notHandled()} — 此源不处理，请 SourceManager 试下一个</li>
     *   <li>{@link SourceResult#fail(String)} — 明确失败，停止后续尝试</li>
     * </ul>
     */
    SourceResult take(Player player, String itemId, int desiredAmount);
}