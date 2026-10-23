package com.voiceblock.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * UI 音效播放工具。统一走 SoundManager + SimpleSoundInstance.forUI 通道，
 * 不绕 player.playSound 的 3D 位置衰减逻辑，按钮提示音能稳定出声。
 *
 * <p><b>跨映射兼容性</b>：
 * <ul>
 *   <li>NeoForge / Forge（Mojang 映射）：{@code SoundEvents.UI_BUTTON_CLICK} 是直接的 SoundEvent。</li>
 *   <li>Fabric（Yarn 映射）：字段是 Holder/Reference，会通过 unwrap 自动解包。</li>
 * </ul>
 */
public class SoundUtil {

    public static void playSound(Player player, String soundFieldName, float volume, float pitch) {
        SoundEvent sound = resolveSound(soundFieldName);
        if (sound == null) return;
        playUiSound(sound, volume, pitch);
    }

    /** 没 player 上下文时也能调（如命令触发音效） */
    public static void playUiSound(SoundEvent sound, float volume, float pitch) {
        if (sound == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static SoundEvent resolveSound(String fieldName) {
        if (fieldName == null) return null;
        for (String name : candidates(fieldName)) {
            try {
                Object raw = SoundEvents.class.getField(name).get(null);
                SoundEvent se = unwrap(raw);
                if (se != null) return se;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** 官方字段名 → SRG 名（Forge reobf）/ intermediary 名（Fabric 生产环境）。 */
    private static String[] candidates(String fieldName) {
        switch (fieldName) {
            case "UI_BUTTON_CLICK": return new String[]{"UI_BUTTON_CLICK", "f_12490_", "field_15015"};
            case "EXPERIENCE_ORB_PICKUP": return new String[]{"EXPERIENCE_ORB_PICKUP", "f_11871_", "field_14627"};
            default: return new String[]{fieldName};
        }
    }

    /** 解包可能的 Holder/Reference 包装（Mojang 1.21+/Fabric Yarn 上会用到） */
    private static SoundEvent unwrap(Object obj) {
        if (obj == null) return null;
        if (obj instanceof SoundEvent se) return se;
        // Holder.value()：官方 value / Forge SRG m_203334_ / Fabric intermediary comp_349
        for (String name : new String[]{"value", "m_203334_", "comp_349", "get"}) {
            try {
                Object r = obj.getClass().getMethod(name).invoke(obj);
                if (r instanceof SoundEvent se) return se;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
