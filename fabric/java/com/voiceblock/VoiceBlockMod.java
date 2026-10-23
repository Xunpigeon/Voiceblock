package com.voiceblock;

import com.voiceblock.net.VoiceNetwork;
import com.voiceblock.source.SourceBootstrap;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 主入口（{@code main} entrypoint）：<b>专用服务器也会执行这里</b>。
 *
 * <p>v2.0.x 之前本类只实现 {@link net.fabricmc.api.ClientModInitializer}，
 * {@code fabric.mod.json} 里写的是 {@code "environment": "client"}，
 * 于是专用服务器根本不加载本模组 —— 客户端只能就地读自己的容器副本，
 * 末影箱 / 周围箱子 / 精妙背包 / AE2 / 汤姆储存全部读不到东西。
 * 现在拆成两个入口：</p>
 * <ul>
 *   <li>{@link VoiceBlockMod}（main）：注册物品源 + 服务端 C2S 接收器，两端都跑；</li>
 *   <li>{@link com.voiceblock.client.VoiceBlockClient}（client）：按键 / HUD / 客户端回包，只在客户端跑。</li>
 * </ul>
 *
 * <p><b>本类及其调用链不得出现任何 {@code net.minecraft.client.*} 类型</b>：
 * 否则专用服务器加载时直接 {@code NoClassDefFoundError}。</p>
 */
public class VoiceBlockMod implements ModInitializer {
    public static final String MOD_ID = "voiceblock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    static {
        // 修复 Vosk(JNA) 在中文 Windows 上用 GBK 解码 UTF-8 字节导致中文乱码
        System.setProperty("jna.encoding", "UTF-8");
    }

    @Override
    public void onInitialize() {
        LOGGER.info("VoiceBlock initializing (fabric, common side)...");
        // 服务端必须能跑 SourceManager.takeAny，否则收到请求也没东西可取
        SourceBootstrap.registerAll();
        // 注册服务端接收器；客户端实例上注册也无害（只有真的收到包才会执行）
        VoiceNetwork.registerCommon();
    }
}
