package com.voiceblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 键位绑定。默认鼠标侧键5 (GLFW_MOUSE_BUTTON_5)。
 */
@EventBusSubscriber(modid = "voiceblock", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class KeyBindings {
    public static final String CATEGORY = "key.categories.voiceblock";
    public static KeyMapping voiceBlockKey;

    static {
        // GLFW: GLFW_MOUSE_BUTTON_1=0, _2=1, _3=2(中键), _4=3, _5=4(侧键5)
        voiceBlockKey = new KeyMapping(
                "key.voiceblock.use",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_5,
                CATEGORY
        );
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(voiceBlockKey);
    }
}
