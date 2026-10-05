package com.voiceblock;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoiceBlockMod implements ClientModInitializer {
    public static final String MOD_ID = "voiceblock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    static {
        System.setProperty("jna.encoding", "UTF-8");
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("VoiceBlock mod initializing...");
        com.voiceblock.client.KeyBindings.register();
        com.voiceblock.client.ClientEvents.register();
        com.voiceblock.client.VoiceHudOverlay.register();
    }
}