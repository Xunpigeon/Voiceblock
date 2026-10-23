package com.voiceblock.source;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 物品源管理器（单例）。
 * <ul>
 *   <li>{@link #register(ItemSource)} — 各个源静态注册</li>
 *   <li>{@link #takeAny(Player, String, int)} — 主入口，按优先级尝试所有启用的源</li>
 *   <li>{@link #getStatusList()} — /voiceblock source list 用</li>
 * </ul>
 */
public class SourceManager {

    private static SourceManager instance;
    private final List<ItemSource> sources = new CopyOnWriteArrayList<>();
    private final SourceConfigs configs = SourceConfigs.load();

    public static synchronized SourceManager getInstance() {
        if (instance == null) instance = new SourceManager();
        return instance;
    }

    /** 各个 ItemSource 在静态初始化阶段调用此方法注册自己。 */
    public void register(ItemSource source) {
        if (source == null) return;
        for (ItemSource s : sources) {
            if (Objects.equals(s.name(), source.name())) return;
        }
        sources.add(source);
        VoiceBlockMod.LOGGER.info("Registered ItemSource: {}", source.name());
    }

    /**
     * 主入口：依次尝试所有启用的源（按 priority 升序），第一个成功的源返回结果。
     * 失败语义：
     * <ul>
     *   <li>success → 立即返回</li>
     *   <li>fail(reason) → 立即返回（明确失败）</li>
     *   <li>notHandled() → 继续下一个源</li>
     * </ul>
     */
    public SourceResult takeAny(Player player, String itemId, int desiredAmount) {
        List<ItemSource> ordered = getOrderedEnabledSources();
        boolean isServerSide = player instanceof net.minecraft.server.level.ServerPlayer;
        String sideTag = isServerSide ? "SERVER" : "CLIENT-only";
        VoiceBlockMod.LOGGER.info("[VoiceBlock] takeAny '{}' x{} side={} via {} sources: {}",
                itemId, desiredAmount, sideTag, ordered.size(),
                ordered.stream().map(ItemSource::name).toList());

        StringBuilder trace = new StringBuilder();
        for (ItemSource source : ordered) {
            try {
                SourceResult r = source.take(player, itemId, desiredAmount);
                if (r.success()) {
                    VoiceBlockMod.LOGGER.info("[VoiceBlock] '{}' succeeded via {} x{}",
                            itemId, source.name(), r.amountTaken());
                    return r;
                }
                if (!r.shouldContinue()) {
                    VoiceBlockMod.LOGGER.info("[VoiceBlock] '{}' hard-fail at {}: {}",
                            itemId, source.name(), r.reason());
                    return r;
                }
                VoiceBlockMod.LOGGER.debug("[VoiceBlock] '{}' notHandled at {}, trying next", itemId, source.name());
                if (trace.length() > 0) trace.append(",");
                trace.append(source.name());
            } catch (Exception e) {
                VoiceBlockMod.LOGGER.error("[VoiceBlock] Source '{}' threw exception for '{}'",
                        source.name(), itemId, e);
                if (trace.length() > 0) trace.append(",");
                trace.append(source.name()).append("(err)");
            }
        }
        // 把尝试过的源列表写进 reason（保留"未找到"作为前缀，方便过滤）
        String reason = "未找到该物品（已尝试: " + trace + "）";
        VoiceBlockMod.LOGGER.info("[VoiceBlock] '{}' miss in all {} sources", itemId, ordered.size());
        return SourceResult.fail(reason);
    }

    /** 按优先级排序的启用源列表（数字越小越优先；不可用的源自动跳过）。 */
    public List<ItemSource> getOrderedEnabledSources() {
        List<ItemSource> result = new ArrayList<>();
        for (ItemSource s : sources) {
            SourceConfig c = configs.get(s.name());
            if (c != null && c.enabled() && s.isAvailable()) {
                result.add(s);
            }
        }
        result.sort(Comparator.comparingInt(s -> configs.get(s.name()).priority()));
        return result;
    }

    /** /voiceblock source list 用：返回每个源的状态（包含 enabled/available）。 */
    public List<SourceStatus> getStatusList() {
        List<SourceStatus> list = new ArrayList<>();
        for (ItemSource s : sources) {
            SourceConfig c = configs.get(s.name());
            boolean enabled = c != null && c.enabled();
            boolean available = s.isAvailable();
            list.add(new SourceStatus(s, enabled, available));
        }
        list.sort(Comparator.comparingInt(s -> {
            SourceConfig c = configs.get(s.name());
            return c != null ? c.priority() : Integer.MAX_VALUE;
        }));
        return list;
    }

    public SourceConfigs configs() {
        return configs;
    }

    /** 给命令 UI 显示的状态（包含源 + 配置 + 可用性）。 */
    public record SourceStatus(ItemSource source, boolean enabled, boolean available) {
        public String name() { return source.name(); }
        public SourceConfig config() {
            return SourceManager.getInstance().configs.get(source.name());
        }
    }
}