package com.voiceblock;

import com.voiceblock.network.VoiceNetwork;
import com.voiceblock.source.SourceBootstrap;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VoiceBlockMod.MOD_ID)
public class VoiceBlockMod {
    public static final String MOD_ID = "voiceblock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    static {
        // 修复 Vosk(JNA) 在中文 Windows 上用 GBK 解码 UTF-8 字节导致中文乱码
        System.setProperty("jna.encoding", "UTF-8");
    }

    public VoiceBlockMod() {
        // 网络通道必须在 mod 构造期（网络注册表上锁前）注册一次；两端都要注册
        VoiceNetwork.register();
        SourceBootstrap.registerAll();
        VoiceBlockMod.LOGGER.info("VoiceBlock mod initialized (forge 1.20.1, v2.0.8)");
    }
}