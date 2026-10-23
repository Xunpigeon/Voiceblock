package com.voiceblock.client;

import com.voiceblock.VoiceBlockMod;
import com.voiceblock.net.VoiceClientNetwork;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric 客户端入口（{@code client} entrypoint）：只在客户端加载。
 *
 * <p>按键 / HUD / 客户端回包都挂在这里，主入口 {@link VoiceBlockMod}
 * 保持"服务端也能加载"的洁净状态。</p>
 */
public class VoiceBlockClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        VoiceBlockMod.LOGGER.info("VoiceBlock initializing (fabric, client side)...");
        KeyBindings.register();
        ClientEvents.register();
        VoiceHudOverlay.register();
        // 接收服务端回包（取物结果）
        VoiceClientNetwork.registerClient();
    }
}
