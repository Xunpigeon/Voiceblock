package com.voiceblock;


import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VoiceBlockMod.MOD_ID)
public class VoiceBlockMod {
    public static final String MOD_ID = "voiceblock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    static {
        // 修复 Vosk(JNA) 在中文 Windows 上用 GBK 解码 UTF-8 字节导致中文乱码
        // 必须在 Vosk 原生库加载之前设置
        System.setProperty("jna.encoding", "UTF-8");
    }

    public VoiceBlockMod() {
        LOGGER.info("VoiceBlock mod initializing...");
        // KeyBindings 与 ClientEvents 均通过 @EventBusSubscriber 自动注册
    }
}
