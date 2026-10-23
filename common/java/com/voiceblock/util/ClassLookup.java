package com.voiceblock.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 跨平台反射查找工具。
 *
 * <p><b>为什么需要它</b>：Fabric 生产包（Loom {@code remapJar}）只重映射字节码，
 * <b>不重映射字符串常量</b> —— 类名会变成 {@code net.minecraft.class_xxxx}，
 * 成员名会变成 {@code method_xxxxx} / {@code field_xxxxx}。而 Forge reobf 后成员名是 SRG
 * （{@code m_xxxxx_} / {@code f_xxxxx_}），NeoForge 与开发环境则是官方映射名。</p>
 *
 * <p>因此所有按“名字字符串”做的反射查找都必须同时提供三类候选：
 * 官方映射名 / Forge SRG 名 / Fabric intermediary 名。本类把这套「依次尝试」逻辑统一封装，
 * 调用方只需一次调用，且对 Forge / NeoForge 完全无副作用。</p>
 */
public final class ClassLookup {

    private ClassLookup() {}

    /** 依次尝试各类名候选，全部失败抛 {@link ClassNotFoundException}。 */
    public static Class<?> forName(String... names) throws ClassNotFoundException {
        for (String n : names) {
            try {
                return Class.forName(n);
            } catch (Throwable ignored) {}
        }
        throw new ClassNotFoundException(String.join(" / ", names));
    }

    /** 依次尝试各字段名候选，全部失败返回 null。 */
    public static Field field(Class<?> cls, String... names) {
        if (cls == null) return null;
        for (String n : names) {
            try {
                return cls.getField(n);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** 依次尝试各方法名候选，全部失败返回 null。 */
    public static Method method(Class<?> cls, String[] names, Class<?>... params) {
        if (cls == null) return null;
        for (String n : names) {
            try {
                return cls.getMethod(n, params);
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
