package com.fo.addon.ancient.core;

/**
 * Xaero 地图路径点桥（移植自 misaka 古城模块）。
 * 通过反射调用 Xaero's Minimap / Xaero's World Map 的公开 API 添加路径点。
 * 路径点颜色组：金=14（黄色系）、书=3（蓝色系）、星=7（紫色，独立组，区别于源实现的 14）。
 */
public class XaeroWaypointBridge {
    private static Class<?> WAYPOINT;
    private static Class<?> WAYPOINT_SET;
    private static Class<?> BUILT_IN_HUD_MODULES;
    private static Class<?> MINIMAP_SESSION;
    private static Class<?> MINIMAP_WORLD;
    private static Class<?> SUPPORT_MODS;
    private static boolean loaded;

    static {
        loaded = true;
        try {
            WAYPOINT = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            WAYPOINT_SET = Class.forName("xaero.common.minimap.waypoints.WaypointSet");
            BUILT_IN_HUD_MODULES = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            MINIMAP_SESSION = Class.forName("xaero.hud.minimap.module.MinimapSession");
            MINIMAP_WORLD = Class.forName("xaero.hud.minimap.world.MinimapWorld");
            SUPPORT_MODS = Class.forName("xaero.map.mods.SupportMods");
        } catch (ClassNotFoundException e) {
            loaded = false;
        }
    }

    public static boolean isLibraryLoaded() {
        return loaded;
    }

    /** 获取当前世界路径点集合（WaypointSet），获取失败返回 null。 */
    public static Object getCurrentWaypointSet() {
        if (!loaded) {
            return null;
        }
        try {
            Object minimap = BUILT_IN_HUD_MODULES.getField("MINIMAP").get(null);
            if (minimap == null) {
                return null;
            }
            Object session = minimap.getClass().getMethod("getCurrentSession").invoke(minimap);
            if (session == null) {
                return null;
            }
            Object worldManager = MINIMAP_SESSION.getMethod("getWorldManager").invoke(session);
            if (worldManager == null) {
                return null;
            }
            Object world = worldManager.getClass().getMethod("getCurrentWorld").invoke(worldManager);
            if (world == null) {
                return null;
            }
            return MINIMAP_WORLD.getMethod("getCurrentWaypointSet").invoke(world);
        } catch (Exception e) {
            return null;
        }
    }

    /** 按 X/Z 查找已有路径点；不存在返回 null。 */
    private static Object findByLocation(int x, int z) {
        if (!loaded) {
            return null;
        }
        try {
            Object set = getCurrentWaypointSet();
            if (set == null) {
                return null;
            }
            for (Object wp : (Iterable<?>) WAYPOINT_SET.getMethod("getWaypoints").invoke(set)) {
                int wx = (Integer) WAYPOINT.getMethod("getX").invoke(wp);
                int wz = (Integer) WAYPOINT.getMethod("getZ").invoke(wp);
                if (wx == x && wz == z) {
                    return wp;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    /** 添加路径点；同名坐标已存在则跳过。 */
    public static void addWaypoint(int x, int y, int z, String name, String group, int colorIndex) {
        if (!loaded) {
            return;
        }
        try {
            Object set = getCurrentWaypointSet();
            if (set == null) {
                return;
            }
            if (findByLocation(x, z) != null) {
                return;
            }
            Object waypoint = WAYPOINT
                .getConstructor(int.class, int.class, int.class, String.class, String.class, int.class, int.class, boolean.class)
                .newInstance(x, y, z, name, group, colorIndex, 0, false);
            WAYPOINT_SET.getMethod("add", WAYPOINT).invoke(set, waypoint);
            Object support = SUPPORT_MODS.getField("xaeroMinimap").get(null);
            if (support != null) {
                support.getClass().getMethod("requestWaypointsRefresh").invoke(support);
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}
