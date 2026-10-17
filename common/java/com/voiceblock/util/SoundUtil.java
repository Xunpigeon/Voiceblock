package com.voiceblock.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 音效播放工具（NeoForge/Forge 专用，使用 Mojang 映射字段反射）。
 * Fabric 端请直接在 ClientEvents 中用 SoundEvents.XXX 字段访问。
 */
public class SoundUtil {

    public static void playSound(Player player, String soundFieldName, float volume, float pitch) {
        if (player == null) return;
        try {
            Field field = SoundEvents.class.getField(soundFieldName);
            Object soundObj = field.get(null);
            SoundEvent soundEvent = unwrap(soundObj);
            if (soundEvent != null) {
                player.playSound(soundEvent, volume, pitch);
            }
        } catch (Exception ignored) {
        }
    }

    private static SoundEvent unwrap(Object obj) {
        if (obj == null) return null;
        if (obj instanceof SoundEvent) return (SoundEvent) obj;
        // 1.21+: Holder<SoundEvent>，尝试 value() 或 get()
        try {
            Method m = obj.getClass().getMethod("value");
            Object r = m.invoke(obj);
            if (r instanceof SoundEvent) return (SoundEvent) r;
        } catch (Exception ignored) {}
        try {
            Method m = obj.getClass().getMethod("get");
            Object r = m.invoke(obj);
            if (r instanceof SoundEvent) return (SoundEvent) r;
        } catch (Exception ignored) {}
        return null;
    }
}