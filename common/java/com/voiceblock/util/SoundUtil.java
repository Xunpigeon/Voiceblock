package com.voiceblock.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 跨版本音效播放工具。
 * 1.20.x: SoundEvents.XXX 直接返回 SoundEvent
 * 1.21+:  SoundEvents.XXX 返回 Holder<SoundEvent>，需 .value() 解包
 */
public class SoundUtil {

    /**
     * 播放指定音效。
     *
     * @param player          玩家
     * @param soundFieldName  SoundEvents 中的字段名，如 "UI_BUTTON_CLICK"
     * @param volume          音量 (0.0 ~ 1.0)
     * @param pitch           音调 (0.5 ~ 2.0)
     */
    public static void playSound(Player player, String soundFieldName, float volume, float pitch) {
        if (player == null) return;
        try {
            Field field = SoundEvents.class.getField(soundFieldName);
            Object soundObj = field.get(null);
            SoundEvent soundEvent = unwrap(soundObj);
            if (soundEvent != null) {
                player.playSound(soundEvent, volume, pitch);
            }
        } catch (Exception e) {
            // 静默失败，不影响核心功能
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