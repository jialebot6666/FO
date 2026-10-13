package com.fo.addon.ancient.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointSession;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.testkit.OriginKey;

/**
 * Xaero 路径点桥的联调测试。
 *
 * <p>测试类路径上放了一套<b>仿冒的 Xaero 类</b>（包名、类名、方法签名都按真实 jar 抄），
 * 这样能把反射管线完整跑一遍 —— 而不是只测「没装 Xaero 时安全返回 false」这种空转。
 *
 * <p>之所以值得这么测：V7.0 之前这段代码就是因为要求了
 * {@code xaero.common.minimap.waypoints.WaypointSet}（26.x 已删除该路径）和
 * {@code xaero.map.mods.SupportMods}（属于世界地图模组）而全线失效，
 * 却只在游戏里表现成一句「Xaero 地图模组未安装」，靠单测是抓不住的。
 */
class XaeroWaypointBridgeTest {

    private static final int X = 100;
    private static final int Y = -40;
    private static final int Z = 200;

    private MinimapSession session;
    private WaypointSet playerSet;
    private ThirdPartyWaypoints officialView;

    @BeforeEach
    void setUp() {
        session = new MinimapSession();
        MinimapWorld world = new MinimapWorld();
        session.getWorldManager().setCurrentWorldForTest(world);
        BuiltInHudModules.MINIMAP.setCurrentSessionForTest(session);

        playerSet = world.getCurrentWaypointSet();
        ThirdPartyWaypointManager manager = world.getContainer().getThirdPartyWaypointManager();
        officialView = manager.get(OriginKey.of("fo", "ancient_city_chests"));
    }

    @Test
    void 两条通道都可用且自动优先官方() {
        assertTrue(XaeroWaypointBridge.isXaeroPresent(), "装了 Xaero 就该认出来");
        assertTrue(XaeroWaypointBridge.officialSupported(), "官方第三方接口应可用");
        assertTrue(XaeroWaypointBridge.waypointSetSupported(), "玩家路径点集通道应可用");
        assertTrue(XaeroWaypointBridge.statusText().contains("两条通道"), "状态文案应说明两条都可用");

        assertEquals(XaeroWaypointBridge.Channel.OFFICIAL,
            XaeroWaypointBridge.pick(XaeroWaypointBridge.Preference.AUTO), "自动模式应优先官方接口");
        assertEquals(XaeroWaypointBridge.Channel.OFFICIAL,
            XaeroWaypointBridge.pick(XaeroWaypointBridge.Preference.OFFICIAL));
        assertEquals(XaeroWaypointBridge.Channel.WAYPOINT_SET,
            XaeroWaypointBridge.pick(XaeroWaypointBridge.Preference.WAYPOINT_SET));
    }

    @Test
    void 自动模式写进官方来源不碰玩家路径点() {
        assertTrue(XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO,
            X, Y, Z, "附魔金 #1", "附魔金", "GOLD"));

        assertEquals(1, officialView.getAll().size(), "官方来源下应有 1 个点");
        assertEquals(0, playerSet.size(), "不应污染玩家自己的路径点集");

        Waypoint wp = officialView.getAll().get("fo-ancient-100-200");
        assertNotNull(wp, "路径点 id 应由水平坐标决定，删除时才不用再扫一遍");
        assertEquals(X, wp.getX());
        assertEquals(Y, wp.getY());
        assertEquals(Z, wp.getZ());
        assertEquals("附魔金 #1", wp.getName());
        assertEquals("附魔金", wp.getSymbol());
        assertEquals(WaypointColor.GOLD, wp.getWaypointColor(), "应走颜色枚举，而指数值索引");
    }

    @Test
    void 来源标识用的是of而不是ofVanilla() {
        XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO,
            X, Y, Z, "x", "y", "BLUE");
        // 真实 Identifier 有 of 与 ofVanilla 两个同形工厂，挑错就会落到 minecraft 命名空间
        assertEquals("fo", officialView.getOrigin().getNamespace(),
            "命名空间被改成了 minecraft，说明挑成了 ofVanilla");
        assertEquals("ancient_city_chests", officialView.getOrigin().getPath());
    }

    @Test
    void 官方通道同一坐标重复添加不会重复() {
        for (int i = 0; i < 3; i++) {
            XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO,
                X, Y, Z, "附魔金 #" + i, "附魔金", "GOLD");
        }
        assertEquals(1, officialView.getAll().size(), "同一个 id 反复写应只留一个");
    }

    @Test
    void 强制玩家路径点集通道并触发刷新通知() {
        WaypointSession waypointSession = session.getWaypointSession();
        assertEquals(0L, waypointSession.getSetChangedTime(), "初始未变更");

        assertTrue(XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.WAYPOINT_SET,
            X, Y, Z, "迅捷3 #1", "迅捷3", "BLUE"));

        assertEquals(1, playerSet.size());
        assertEquals(0, officialView.getAll().size(), "不应写进官方来源");
        assertTrue(waypointSession.getSetChangedTime() > 0L, "写入后应通知 Xaero 路径点集已变更");
    }

    @Test
    void 玩家路径点集通道按坐标去重() {
        for (int i = 0; i < 3; i++) {
            XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.WAYPOINT_SET,
                X, Y, Z, "两者 #" + i, "两者", "PURPLE");
        }
        assertEquals(1, playerSet.size(), "同 x/z 只该保留一个点");
        assertEquals(WaypointColor.PURPLE, playerSet.get(0).getWaypointColor());
    }

    @Test
    void 写入的路径点可见性为只在世界地图本地() {
        assertTrue(XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.WAYPOINT_SET,
            100, 64, 200, "附魔金 #1", "附魔金", "GOLD"));
        assertEquals(1, playerSet.size());
        // 26.x 的 WaypointVisibilityType.WORLD_MAP_LOCAL：小地图与世界内渲染器跳过，只有世界地图显示
        assertEquals(xaero.hud.minimap.waypoint.WaypointVisibilityType.WORLD_MAP_LOCAL,
            playerSet.get(0).getVisibility(), "写入的路径点应是「只在世界地图（当前世界）」");

        // 官方通道同样生效
        assertTrue(XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.OFFICIAL,
            300, 64, 400, "迅捷3 #1", "迅捷3", "BLUE"));
        assertEquals(xaero.hud.minimap.waypoint.WaypointVisibilityType.WORLD_MAP_LOCAL,
            officialView.getAll().get("fo-ancient-300-400").getVisibility());

        // 重复写入已存在的点（幂等分支）也要补设可见性
        assertTrue(XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.WAYPOINT_SET,
            100, 64, 200, "附魔金 #1", "附魔金", "GOLD"));
        assertEquals(xaero.hud.minimap.waypoint.WaypointVisibilityType.WORLD_MAP_LOCAL,
            playerSet.get(0).getVisibility());
    }

    @Test
    void 删除会同时清理两条通道() {
        // 先两条通道都写一个（模拟用户中途换过写入方式）
        XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO, X, Y, Z, "a", "b", "GOLD");
        XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.WAYPOINT_SET, X, Y, Z, "a", "b", "GOLD");
        assertEquals(1, officialView.getAll().size());
        assertEquals(1, playerSet.size());

        assertTrue(XaeroWaypointBridge.removeWaypointAt(X, Z), "应报告删掉了");

        assertEquals(0, officialView.getAll().size(), "官方来源应清掉");
        assertEquals(0, playerSet.size(), "玩家路径点集应清掉");
        // WaypointSet 有 remove(Waypoint) 与 remove(int) 两个同参数个数的重载，
        // 若桥按参数个数而不是参数类型选方法，上面 playerSet 就不会变成 0。
    }

    @Test
    void 别的坐标不会被误删() {
        XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO, X, Y, Z, "a", "b", "GOLD");
        assertFalse(XaeroWaypointBridge.removeWaypointAt(X + 16, Z), "没有这个点，不该报删掉");
        assertEquals(1, officialView.getAll().size(), "无关的点必须留着");
    }

    @Test
    void 会话未就绪时安全返回false不抛异常() {
        BuiltInHudModules.MINIMAP.setCurrentSessionForTest(null);

        assertFalse(XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO,
            X, Y, Z, "a", "b", "GOLD"), "没有会话应返回 false");
        assertFalse(XaeroWaypointBridge.removeWaypointAt(X, Z), "没有会话应返回 false");
        // 通道本身仍然是「支持」的，只是此刻拿不到会话 —— 模块据此提示「先进入世界再重试」
        assertTrue(XaeroWaypointBridge.waypointSetSupported());
    }

    @Test
    void 颜色名对不上时退回整数索引() {
        assertTrue(XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO,
            X, Y, Z, "a", "b", "NOT_A_REAL_COLOR"));

        Waypoint wp = officialView.getAll().get("fo-ancient-100-200");
        assertNotNull(wp);
        assertNull(wp.getWaypointColor(), "取不到枚举时应走整数索引构造");
        assertEquals(13, wp.getColorIndex(), "未知颜色名兜底为紫（13）");
    }
}
