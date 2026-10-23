package com.voiceblock.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * 物品 ID 解析工具：跨平台、跨版本共用。
 * 把 "minecraft:stone" / "create:andesite_casing" 解析为 Item 对象。
 *
 * <p>通过 {@link RegistryAccess} 反射访问注册表，规避 Forge 1.20.1 AT 重命名字段导致的崩溃。</p>
 */
public class ItemResolver {

    public static Item resolve(String blockOrItemId) {
        ResourceLocation rl = ResourceLocations.tryParse(blockOrItemId);
        if (rl == null) return null;

        Optional<Block> blockOpt = RegistryAccess.getOptionalBlock(rl);
        if (blockOpt.isPresent()) {
            Item blockItem = blockOpt.get().asItem();
            if (blockItem != null && blockItem != Items.AIR) {
                return blockItem;
            }
        }
        return RegistryAccess.getOptionalItem(rl).orElse(null);
    }
}