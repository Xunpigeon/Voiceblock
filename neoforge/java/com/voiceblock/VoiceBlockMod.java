package com.voiceblock;

import com.voiceblock.source.SourceBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VoiceBlockMod.MOD_ID)
public class VoiceBlockMod {
    public static final String MOD_ID = "voiceblock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    static {
        // 修复 Vosk(JNA) 在中文 Windows 上用 GBK 解码 UTF-8 字节导致中文乱码
        // 必须在 Vosk 原生库加载之前设置（服务端也走 Vosk jar，无害）
        System.setProperty("jna.encoding", "UTF-8");
    }

    public VoiceBlockMod(IEventBus modEventBus) {
        // v2.0.x：服务端要能跑 SourceManager.takeAny，必须先注册源
        // 客户端那边 ClientEvents.onRegisterClientCommands 也会再调一次，
        // SourceManager.register 有去重，无副作用
        SourceBootstrap.registerAll();
        LOGGER.info("VoiceBlock mod initializing on {} — {} sources registered",
                net.neoforged.fml.loading.FMLLoader.getDist(),
                net.neoforged.fml.loading.FMLLoader.getDist().isClient() ? "client" : "server-only");
        // KeyBindings / ClientEvents / VoiceHudOverlay 通过 @EventBusSubscriber(value=Dist.CLIENT) 自动注册
        // VoiceNetworkRegistration 通过 MOD 总线自动注册（C2S/S2C 两端都跑）
    }
}
