package com.voiceblock.source;

import com.voiceblock.VoiceBlockMod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 软依赖探测缓存：首次探测后缓存结果。
 * 通过 Class.forName(...) 判断 mod 是否安装：
 * - mod 装了 → 目标类在 classpath → true
 * - mod 没装 → ClassNotFoundException → false
 *
 * 不需要 build.gradle 加 compileOnly，纯反射调用兼容有无依赖。
 *
 * <p>NeoForge / Forge / Fabric 在某些情况下 Class.forName 默认会失败
 * （模块化 classloader），所以这里依次尝试：</p>
 * <ol>
 *   <li>Class.forName(name)（调用者的 classloader）</li>
 *   <li>Class.forName(name, true, Thread.currentThread().getContextClassLoader())</li>
 *   <li>Class.forName(name, true, ClassLoader.getSystemClassLoader())</li>
 *   <li>Class.forName(name, true, SoftDependCache.class.getClassLoader())</li>
 * </ol>
 */
public class SoftDependCache {

    private static final ConcurrentMap<String, Boolean> BOOL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    public static boolean isLoaded(String className) {
        return loadClass(className) != null;
    }

    public static Class<?> loadClass(String className) {
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) return cached;
        Class<?> loaded = tryLoad(className);
        if (loaded != null) CLASS_CACHE.put(className, loaded);
        return loaded;
    }

    private static Class<?> tryLoad(String name) {
        // 1. 默认（调用方 classloader，NeoForge/Fabric 在大多数场景下能用）
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {}
        catch (Throwable t) {
            // SecurityException 等也要继续尝试
        }

        // 2. 线程上下文 classloader
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) return Class.forName(name, true, cl);
        } catch (Throwable ignored) {}

        // 3. 系统 classloader
        try {
            return Class.forName(name, true, ClassLoader.getSystemClassLoader());
        } catch (Throwable ignored) {}

        // 4. 本类的 classloader
        try {
            return Class.forName(name, true, SoftDependCache.class.getClassLoader());
        } catch (Throwable ignored) {}

        VoiceBlockMod.LOGGER.debug("[VoiceBlock] SoftDependCache: 找不到类 {}", name);
        return null;
    }
}