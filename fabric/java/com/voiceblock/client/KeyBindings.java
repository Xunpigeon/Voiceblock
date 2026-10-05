package com.voiceblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String CATEGORY = "key.categories.voiceblock";
    public static KeyMapping voiceBlockKey;

    public static void register() {
        voiceBlockKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voiceblock.use",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_5,
                CATEGORY
        ));
    }
}