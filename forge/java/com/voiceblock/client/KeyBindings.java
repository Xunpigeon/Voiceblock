package com.voiceblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 键位绑定。默认鼠标侧键5 (GLFW_MOUSE_BUTTON_5) 触发取物；
 * 鼠标侧键4 (GLFW_MOUSE_BUTTON_4) 触发学习模式。
 */
@Mod.EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
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