package com.voiceblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 键位绑定。默认鼠标侧键5 (GLFW_MOUSE_BUTTON_5)。
 */
@EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class KeyBindings {
    public static final String CATEGORY = "key.categories.voiceblock";
    public static KeyMapping voiceBlockKey;
    public static KeyMapping voiceBlockLearnKey;

    static {
        voiceBlockKey = new KeyMapping(
                "key.voiceblock.use",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_5,
                CATEGORY
        );
        // 学习模式：手持物品按住此键说话，即可把该物品的名字记进 manual_overrides.json
        voiceBlockLearnKey = new KeyMapping(
                "key.voiceblock.learn",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4,
                CATEGORY
        );
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(voiceBlockKey);
        event.register(voiceBlockLearnKey);
    }
}