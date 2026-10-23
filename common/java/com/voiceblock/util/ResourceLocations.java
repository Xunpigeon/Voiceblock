package com.voiceblock.util;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;

/**
 * 跨版本兼容的 ResourceLocation 工具。
 *
 * <p>不同 MC 版本的 {@link ResourceLocation} 构造器签名不同：</p>
 * <ul>
 *   <li>1.20.1: {@code new ResourceLocation(String)}（单参数）</li>
 *   <li>1.20.3+: 没有单参数构造器，但有静态 {@code tryParse(String)}</li>
 *   <li>1.21+: 没有单参数构造器，但有静态 {@code parse(String)} 和
 *       {@code fromNamespaceAndPath(String, String)}</li>
 * </ul>
 *
 * <p>为了在 common 代码里跨版本兼容，统一走反射 + try-catch 顺序：</p>
 * <ol>
 *   <li>{@code ResourceLocation.tryParse(String)} 反射（1.20.3+）</li>
 *   <li>{@code ResourceLocation.parse(String)} 反射（1.21+）</li>
 *   <li>{@code ResourceLocation.fromNamespaceAndPath(String, String)} 反射（1.21+），手动拆分冒号</li>
 *   <li>{@code new ResourceLocation(String)} 反射（1.20.1）</li>
 * </ol>
 */
public final class ResourceLocations {
    private ResourceLocations() {}

    /**
     * 跨版本解析 ResourceLocation，失败返回 null。
     *
     * @param id namespaced id 字符串（如 "minecraft:stone"）；null/空/非法格式返回 null
     */
    public static ResourceLocation tryParse(String id) {
        if (id == null || id.isBlank()) return null;
        // 1. 反射 tryParse(String) — 1.20.3+；Fabric 生产环境为 intermediary 名 method_12829
        for (String mn : new String[]{"tryParse", "method_12829"}) {
            try {
                Object r = ResourceLocation.class.getMethod(mn, String.class).invoke(null, id);
                if (r instanceof ResourceLocation rl) return rl;
            } catch (Throwable ignored) {}
        }
        // 2. 反射 parse(String) — 1.21+
        try {
            Object r = ResourceLocation.class.getMethod("parse", String.class).invoke(null, id);
            if (r instanceof ResourceLocation rl) return rl;
        } catch (Throwable ignored) {}
        // 3. 反射 fromNamespaceAndPath(String, String) — 1.21+（需要拆分）
        try {
            int colon = id.indexOf(':');
            if (colon > 0 && colon < id.length() - 1) {
                Object r = ResourceLocation.class
                    .getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, id.substring(0, colon), id.substring(colon + 1));
                if (r instanceof ResourceLocation rl) return rl;
            }
        } catch (Throwable ignored) {}
        // 4. 反射 new ResourceLocation(String) — 1.20.1
        try {
            Constructor<ResourceLocation> ctor =
                ResourceLocation.class.getConstructor(String.class);
            ResourceLocation rl = ctor.newInstance(id);
            return rl;
        } catch (Throwable ignored) {
            return null;
        }
    }
}