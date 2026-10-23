package com.voiceblock.source;

import com.voiceblock.source.impl.SourceAE2;
import com.voiceblock.source.impl.SourceCurio;
import com.voiceblock.source.impl.SourceEnderChest;
import com.voiceblock.source.impl.SourceInventory;
import com.voiceblock.source.impl.SourceNearbyChest;
import com.voiceblock.source.impl.SourceShulkerBox;
import com.voiceblock.source.impl.SourceTomsStorage;

/**
 * 物品源注册引导器：4 个 mod 平台的 ClientEvents 在启动时调用 registerAll()。
 *
 * v2.0 6 个源：
 * - inventory / enderchest / nearbyChest / shulkerBox：无依赖
 * - curio（Sophisticated Backpacks）/ ae2 / tomsstorage：反射软依赖，仅 Neo/Forge 生效
 */
public class SourceBootstrap {

    public static void registerAll() {
        SourceManager mgr = SourceManager.getInstance();
        mgr.register(new SourceInventory());
        mgr.register(new SourceEnderChest());
        mgr.register(new SourceCurio());
        mgr.register(new SourceShulkerBox());
        mgr.register(new SourceAE2());
        mgr.register(new SourceTomsStorage());
        mgr.register(new SourceNearbyChest());

        // 启动日志：每个源的 [开/关] [可用/未装] + 优先级，方便发给开发者排查
        for (var st : mgr.getStatusList()) {
            com.voiceblock.VoiceBlockMod.LOGGER.info(
                "[VoiceBlock] Source: name={} enabled={} available={} priority={}",
                st.name(), st.enabled(), st.available(),
                st.config() != null ? st.config().priority() : "n/a");
        }
    }
}