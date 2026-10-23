package com.voiceblock.source;

/**
 * 单个物品源的配置。
 * 存储于 config/voiceblock/sources.json
 *
 * @param name     源标识，如 "inventory" / "curio"
 * @param enabled  是否启用
 * @param priority 优先级（数字越小越优先）
 * @param radius   搜索半径（仅 nearbyChest 用，其他源为 null）
 */
public record SourceConfig(
        String name,
        boolean enabled,
        int priority,
        Integer radius
) {}