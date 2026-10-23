package com.voiceblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * 键位绑定。
 *
 * <ul>
 *   <li>鼠标侧键 5：按住说话 → 松开取物（默认）</li>
 *   <li>鼠标侧键 4：学习模式 → 手持物品按住说话，把该物品的发音记进 manual_overrides.json</li>
 * </ul>
 *
 * <p>学习模式此前只在 Forge / NeoForge 有，Fabric 缺失（见交接文档第 8 节）。这里补齐，
 * 逻辑与 Forge 版一致，方便视障玩家在任意加载器上都能自定义发音。</p>
 */
public class KeyBindings {
    public static final String CATEGORY = "key.categories.voiceblock";
    public static KeyMapping voiceBlockKey;
    public static KeyMapping voiceBlockLearnKey;

    public static void register() {
        voiceBlockKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voiceblock.use",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_5,
                CATEGORY
        ));
        // 学习模式：手持物品按住此键说话，即可把该物品的名字记进 manual_overrides.json
        voiceBlockLearnKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voiceblock.learn",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4,
                CATEGORY
        ));
    }
}
