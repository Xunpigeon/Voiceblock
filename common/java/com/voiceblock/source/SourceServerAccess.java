package com.voiceblock.source;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 把客户端 Player 转成服务端 ServerPlayer。
 *
 * <p>Minecraft 单玩家下，客户端的 LocalPlayer 和服务端的 ServerPlayer 是两个对象，
 * 各持有各的容器副本。客户端的末影箱、AE2 网络等数据，只有玩家在游戏中**实际打开**
 * 对应方块/界面后，服务端才会推送过来。如果用客户端 Player 直接读，往往是空副本，
 * 看不到真实的容器内容。</p>
 *
 * <p><b>本类绝对不能出现任何 {@code net.minecraft.client.*} 类型。</b>
 * 它位于 common、会被专用服务器加载：方法体里只要出现客户端类型的符号引用，
 * JVM 在链接/校验该方法时就会去加载那个类，Forge 的 {@code RuntimeDistCleaner}
 * 会直接抛</p>
 * <pre>RuntimeException: Attempted to load class net/minecraft/client/... for invalid dist DEDICATED_SERVER</pre>
 *
 * <p>这是一次<b>运行时异常</b>，不是编译错误。结果就是所有调用本类的容器源
 * （末影箱 / 潜行盒 / 精妙背包 / AE2 / 汤姆储存 / 周围箱子）在专用服务器上全部变成
 * {@code (err)}，只有不依赖本类的 {@code inventory} 源还能用 ——
 * 这正是"单人和局域网正常、专用服务器上只能拿身上物品"的根因。</p>
 *
 * <p>所以客户端分支改成字符串反射进 {@link ClientIntegratedServerLookup}：
 * 类名字符串不产生符号引用，服务端永远不会因为本类而触发客户端类加载。</p>
 */
public final class SourceServerAccess {

    /** 只在客户端加载的桥接类；这里只留类名字符串，不产生编译期引用。 */
    private static final String CLIENT_LOOKUP_CLASS = "com.voiceblock.source.ClientIntegratedServerLookup";

    private SourceServerAccess() {}

    public static ServerPlayer getServerPlayer(Player clientPlayer) {
        if (clientPlayer == null) return null;
        // 已经在服务端线程：直接用（专用服务器走这条）
        if (clientPlayer instanceof ServerPlayer sp) return sp;

        // 客户端（单人 / 局域网主机）：通过集成服务器拿到同名 ServerPlayer。
        // 反射进入，保证本类字节码里不出现 client-only 类型。
        try {
            Class<?> lookup = Class.forName(CLIENT_LOOKUP_CLASS);
            return (ServerPlayer) lookup.getMethod("lookup", Player.class).invoke(null, clientPlayer);
        } catch (Throwable t) {
            // 专用服务器（客户端类不存在）或反射失败：按"没有服务端玩家"处理
            return null;
        }
    }
}
