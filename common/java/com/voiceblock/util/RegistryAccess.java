package com.voiceblock.util;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 反射访问 BuiltInRegistries 的安全封装。
 *
 * <p><b>为什么需要反射</b>：Forge 1.20.1 的访问转换器（Access Transformer, AT）会在类加载时
 * 重命名 {@code BuiltInRegistries.BLOCK/ITEM} 这类静态字段名（SRG 映射 → MCP / 官方映射），
 * 不同 Forge 版本映射规则不一致。直接 {@code BuiltInRegistries.BLOCK} 在编译期没问题，
 * 但运行期若 AT 与项目映射不一致，会抛 {@link NoSuchFieldError} 导致整个 mod 加载失败。</p>
 *
 * <p>视障群体社区不能容许崩溃，所以这里用反射按字段名查找（兼容 SRG / 官方映射双方案），
 * 缓存结果，错误一律吞掉降级为 {@link Optional#empty()}。</p>
 */
public final class RegistryAccess {

    private static volatile Object ITEM_REGISTRY;    // Registry<Item>
    private static volatile Object BLOCK_REGISTRY;   // Registry<Block>
    private static volatile Method GET_KEY;
    private static volatile Method GET_OPTIONAL;
    private static volatile Method GET;
    private static volatile Method KEY_SET;

    private RegistryAccess() {}

    static {
        try {
            // 类名候选：官方映射名 / Fabric intermediary 名（remapJar 只重映射字节码，不重映射字符串常量）
            Class<?> cls = ClassLookup.forName("net.minecraft.core.registries.BuiltInRegistries",
                    "net.minecraft.class_7923");
            // 字段名候选：官方 ITEM/BLOCK；Forge reobf 后 SRG f_257033_ / f_256975_；Fabric 生产环境 field_41178 / field_41175
            ITEM_REGISTRY = tryReadField(cls, "ITEM", "f_257033_", "field_41178");
            BLOCK_REGISTRY = tryReadField(cls, "BLOCK", "f_256975_", "field_41175");
            // 方法名候选：官方名 / SRG 名（reobf）/ Fabric intermediary 名
            GET_KEY = findMethod(Registry.class, new String[]{"getKey", "m_7981_", "method_10221"}, Object.class);
            GET_OPTIONAL = findMethod(Registry.class, new String[]{"getOptional", "m_6612_", "method_17966"}, ResourceLocation.class);
            GET = findMethod(Registry.class, new String[]{"get", "m_7745_", "method_10223"}, ResourceLocation.class);
            KEY_SET = findMethod(Registry.class, new String[]{"keySet", "m_6566_", "method_10235"});
            VoiceBlockMod.LOGGER.info("[VoiceBlock] RegistryAccess: ITEM={} BLOCK={} getKey={} getOptional={} get={} keySet={}",
                    ITEM_REGISTRY != null, BLOCK_REGISTRY != null,
                    GET_KEY != null, GET_OPTIONAL != null, GET != null, KEY_SET != null);
        } catch (Throwable t) {
            VoiceBlockMod.LOGGER.error("[VoiceBlock] RegistryAccess init failed", t);
        }
    }

    /** 按候选名（官方映射名 → SRG 名）查找公开方法，全部失败返回 null。 */
    private static Method findMethod(Class<?> cls, String[] names, Class<?>... params) {
        for (String name : names) {
            try {
                return cls.getMethod(name, params);
            } catch (Throwable ignored) {}
        }
        VoiceBlockMod.LOGGER.error("[VoiceBlock] {} method not found (tried {})",
                cls.getSimpleName(), String.join(", ", names));
        return null;
    }

    private static Object tryReadField(Class<?> cls, String... names) {
        for (String name : names) {
            try {
                Field f = cls.getField(name);
                return f.get(null);
            } catch (Throwable ignored) {}
        }
        VoiceBlockMod.LOGGER.error("[VoiceBlock] BuiltInRegistries field not found (tried {})", String.join(", ", names));
        return null;
    }

    // ============ Item ============

    /** 反射版 {@code BuiltInRegistries.ITEM.getOptional(rl)}。 */
    public static Optional<Item> getOptionalItem(ResourceLocation rl) {
        if (ITEM_REGISTRY == null || GET_OPTIONAL == null) return Optional.empty();
        try {
            Object r = GET_OPTIONAL.invoke(ITEM_REGISTRY, rl);
            if (r instanceof Optional<?> opt) {
                @SuppressWarnings("unchecked")
                Optional<Item> cast = (Optional<Item>) opt;
                return cast;
            }
        } catch (Throwable ignored) {}
        return Optional.empty();
    }

    /** 反射版 {@code BuiltInRegistries.ITEM.getKey(item)}。 */
    public static ResourceLocation getItemKey(Item item) {
        if (ITEM_REGISTRY == null || GET_KEY == null) return null;
        try {
            Object k = GET_KEY.invoke(ITEM_REGISTRY, item);
            if (k instanceof ResourceLocation rl) return rl;
        } catch (Throwable ignored) {}
        return null;
    }

    /** 反射版 {@code BuiltInRegistries.ITEM.get(rl)}（不存在返回 null）。 */
    public static Item getItem(ResourceLocation rl) {
        if (ITEM_REGISTRY == null || GET == null) return null;
        try {
            Object r = GET.invoke(ITEM_REGISTRY, rl);
            if (r instanceof Item it) return it;
        } catch (Throwable ignored) {}
        return null;
    }

    // ============ Block ============

    /** 反射版 {@code BuiltInRegistries.BLOCK.getOptional(rl)}。 */
    public static Optional<Block> getOptionalBlock(ResourceLocation rl) {
        if (BLOCK_REGISTRY == null || GET_OPTIONAL == null) return Optional.empty();
        try {
            Object r = GET_OPTIONAL.invoke(BLOCK_REGISTRY, rl);
            if (r instanceof Optional<?> opt) {
                @SuppressWarnings("unchecked")
                Optional<Block> cast = (Optional<Block>) opt;
                return cast;
            }
        } catch (Throwable ignored) {}
        return Optional.empty();
    }

    public static ResourceLocation getBlockKey(Block block) {
        if (BLOCK_REGISTRY == null || GET_KEY == null) return null;
        try {
            Object k = GET_KEY.invoke(BLOCK_REGISTRY, block);
            if (k instanceof ResourceLocation rl) return rl;
        } catch (Throwable ignored) {}
        return null;
    }

    public static Block getBlock(ResourceLocation rl) {
        if (BLOCK_REGISTRY == null || GET == null) return null;
        try {
            Object r = GET.invoke(BLOCK_REGISTRY, rl);
            if (r instanceof Block b) return b;
        } catch (Throwable ignored) {}
        return null;
    }

    // ============ 遍历 ============

    /**
     * 遍历所有已注册 Item。
     * 实现：{@code Registry.keySet()} 拿到 ResourceLocation 集合，再按 id 查 item。
     * 相比直接 {@code for (Item i : registry)} 更安全（keySet 是 Set<ResourceLocation>，无强类型）。
     */
    public static Iterable<Item> allItems() {
        return () -> {
            List<Item> list = new ArrayList<>();
            if (ITEM_REGISTRY == null || KEY_SET == null) return list.iterator();
            try {
                Object set = KEY_SET.invoke(ITEM_REGISTRY);
                if (set instanceof java.util.Set<?> s) {
                    for (Object o : s) {
                        if (o instanceof ResourceLocation rl) {
                            Item it = getItem(rl);
                            if (it != null) list.add(it);
                        }
                    }
                }
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.error("[VoiceBlock] allItems failed", t);
            }
            return list.iterator();
        };
    }

    public static Iterable<Block> allBlocks() {
        return () -> {
            List<Block> list = new ArrayList<>();
            if (BLOCK_REGISTRY == null || KEY_SET == null) return list.iterator();
            try {
                Object set = KEY_SET.invoke(BLOCK_REGISTRY);
                if (set instanceof java.util.Set<?> s) {
                    for (Object o : s) {
                        if (o instanceof ResourceLocation rl) {
                            Block b = getBlock(rl);
                            if (b != null) list.add(b);
                        }
                    }
                }
            } catch (Throwable t) {
                VoiceBlockMod.LOGGER.error("[VoiceBlock] allBlocks failed", t);
            }
            return list.iterator();
        };
    }
}