package com.voiceblock.match;

/**
 * 方块匹配结果。
 *
 * @param itemId      Minecraft 物品 ID，如 "minecraft:oak_stairs"
 * @param displayName 中文显示名，如 "橡木楼梯"
 * @param score       匹配置信度 (0.0 ~ 1.0)
 * @param method      匹配方法描述
 */
public record MatchResult(String itemId, String displayName, float score, String method) {
}
