package com.fo.addon.ancient.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Xaero 路径点桥（纯反射，零编译期依赖）。
 *
 * <p>现行 Xaero（24.7.1 起的 {@code xaero.hud.*} 新架构）提供两条写入通道，本类在运行时自动挑选：
 * <ol>
 *   <li>{@link Channel#OFFICIAL} —— Xaero 26.x 起为第三方模组提供的
 *       {@code xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints} 接口。
 *       路径点挂在独立来源下，<b>不写进玩家自己的路径点文件</b>。</li>
 *   <li>{@link Channel#WAYPOINT_SET} —— 直接写进玩家当前路径点集
 *       {@code xaero.hud.minimap.waypoint.set.WaypointSet}，等同于玩家自己按 B 加的路径点：
 *       会进玩家的路径点列表，可管理、可传送、跟着存档走。24.7.1 ~ 26.x 都可用。</li>
 * </ol>
 *
 * <p><b>历史教训</b>（V7.1 修正）：旧实现要求 {@code xaero.common.minimap.waypoints.WaypointSet}
 * （该路径在 26.x 已被删除，现为 {@code xaero.hud.minimap.waypoint.set.WaypointSet}）与
 * {@code xaero.map.mods.SupportMods}（属于「Xaero 的世界地图」这个独立模组，小地图并不自带），
 * 而且是「缺一个就整体判定未安装」的全有或全无写法 —— 两个类分属新旧两代架构，
 * 拼不出任何能成立的版本组合，因此在所有现行 Xaero 上都误报「未安装」。
 *
 * <p>现在改为分层探测：先看 Xaero 在不在（{@code xaero.common.minimap.waypoints.Waypoint}
 * 从 23.x 到 26.x 一直存在），再看两条通道各自能不能用，并把
 * 「没装 Xaero」与「装了但版本对不上」区分开，不再把两者都混成一句「未安装」。
 *
 * <h2>⚠️ 线程约束（V6.11 血的教训）</h2>
 *
 * <b>本类的所有写操作只能在客户端线程（= 渲染线程）调用。</b>
 *
 * <p>Xaero 的路径点容器是普通 {@code HashMap}，它自己的渲染线程每帧都会遍历；如果从后台线程
 * 调 {@link #addWaypoint}，渲染线程就会抛 {@code ConcurrentModificationException} 把整局游戏崩掉。
 * V6.10 的实机崩溃正是如此：搜索跑在 {@code CompletableFuture} 后台线程，
 * 一路直接调到了 Xaero 的 {@code ThirdPartyWaypoints.add(...)}。
 *
 * <p>这里刻意<b>不做内部加锁</b> —— 加锁也没用：Xaero 遍历时并不取我们的锁，
 * 我们锁住的只是自己这一侧。唯一的解法是把写入放到渲染线程上做，
 * 调用方（{@code FOAncientCitySearch}）用「队列 + TickEvent 出队」保证这一点。
 */
public final class XaeroWaypointBridge {

    /** 路径点写入通道。 */
    public enum Channel {
        /** Xaero 官方第三方模组接口（26.x 起）。 */
        OFFICIAL,
        /** 玩家自己的路径点集（24.7.1 起）。 */
        WAYPOINT_SET,
        /** 没有可用通道。 */
        NONE
    }

    /** 通道偏好（对应用户设置）。 */
    public enum Preference {
        AUTO("自动（优先官方接口）"),
        OFFICIAL("仅官方第三方接口"),
        WAYPOINT_SET("仅玩家路径点集");

        private final String label;

        Preference(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ==================== 关键类名（全部实测自真实 Xaero jar） ====================

    /** 23.x ~ 26.x 都存在，用它判定「Xaero 在不在」。 */
    private static final String CLS_WAYPOINT = "xaero.common.minimap.waypoints.Waypoint";
    /** 新架构入口（24.7.1 起）。 */
    private static final String CLS_BUILT_IN_HUD = "xaero.hud.minimap.BuiltInHudModules";
    private static final String CLS_SESSION = "xaero.hud.minimap.module.MinimapSession";
    private static final String CLS_WORLD = "xaero.hud.minimap.world.MinimapWorld";
    private static final String CLS_CONTAINER = "xaero.hud.minimap.world.container.MinimapWorldContainer";
    /** 玩家路径点集（26.x 的正确路径；旧路径 xaero.common.minimap.waypoints.WaypointSet 已删除）。 */
    private static final String CLS_WAYPOINT_SET = "xaero.hud.minimap.waypoint.set.WaypointSet";
    /** 官方第三方接口（26.x 才有）。 */
    private static final String CLS_THIRD_PARTY_MANAGER =
        "xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager";
    private static final String CLS_THIRD_PARTY = "xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints";
    /** 颜色枚举：按名字取色，不用猜索引。 */
    private static final String CLS_WAYPOINT_COLOR = "xaero.hud.minimap.waypoint.WaypointColor";

    /** 官方通道里我们这组路径点的来源标识（决定 Xaero 侧的分组与开关）。 */
    private static final String ORIGIN_NAMESPACE = "fo";
    private static final String ORIGIN_PATH = "ancient_city_chests";

    // ==================== 探测结果 ====================

    private static final Class<?> WAYPOINT_CLS = cls(CLS_WAYPOINT);
    private static final Class<?> BUILT_IN_HUD_CLS = cls(CLS_BUILT_IN_HUD);
    private static final Class<?> SESSION_CLS = cls(CLS_SESSION);
    private static final Class<?> WORLD_CLS = cls(CLS_WORLD);
    private static final Class<?> CONTAINER_CLS = cls(CLS_CONTAINER);
    private static final Class<?> WAYPOINT_SET_CLS = cls(CLS_WAYPOINT_SET);
    private static final Class<?> THIRD_PARTY_CLS = cls(CLS_THIRD_PARTY);
    private static final Class<?> WAYPOINT_COLOR_CLS = cls(CLS_WAYPOINT_COLOR);

    /** Xaero 是否装了。 */
    private static final boolean XAERO_PRESENT = WAYPOINT_CLS != null;

    /** 新架构是否可用（通道共同前提）。 */
    private static final boolean NEW_ARCH =
        BUILT_IN_HUD_CLS != null && SESSION_CLS != null && WORLD_CLS != null;

    /** 官方第三方通道是否可用。 */
    private static final boolean OFFICIAL_OK = NEW_ARCH
        && THIRD_PARTY_CLS != null
        && CONTAINER_CLS != null
        && namedMethod(CONTAINER_CLS, "getThirdPartyWaypointManager", 0) != null;

    /** 玩家路径点集通道是否可用。 */
    private static final boolean WAYPOINT_SET_OK = NEW_ARCH && WAYPOINT_SET_CLS != null;

    private XaeroWaypointBridge() {
    }

    // ==================== 对外状态查询 ====================

    /** 是否装了 Xaero（不保证我们接得上）。 */
    public static boolean isXaeroPresent() {
        return XAERO_PRESENT;
    }

    /** 官方第三方通道在本地 Xaero 版本上是否支持。 */
    public static boolean officialSupported() {
        return OFFICIAL_OK;
    }

    /** 玩家路径点集通道在本地 Xaero 版本上是否支持。 */
    public static boolean waypointSetSupported() {
        return WAYPOINT_SET_OK;
    }

    /** 按偏好挑通道；都不可用返回 {@link Channel#NONE}。 */
    public static Channel pick(Preference pref) {
        if (!XAERO_PRESENT || !NEW_ARCH) {
            return Channel.NONE;
        }
        if (pref == Preference.OFFICIAL) {
            return OFFICIAL_OK ? Channel.OFFICIAL : Channel.NONE;
        }
        if (pref == Preference.WAYPOINT_SET) {
            return WAYPOINT_SET_OK ? Channel.WAYPOINT_SET : Channel.NONE;
        }
        if (OFFICIAL_OK) {
            return Channel.OFFICIAL;
        }
        return WAYPOINT_SET_OK ? Channel.WAYPOINT_SET : Channel.NONE;
    }

    /**
     * 人类可读的状态说明，供模块输出到聊天栏 —— 关键是**让两种情况区分开**：
     * 「没装 Xaero」和「装了但版本对不上」以前都报「未安装」，这是误导用户的根源。
     */
    public static String statusText() {
        if (!XAERO_PRESENT) {
            return "未检测到 Xaero 小地图";
        }
        if (!NEW_ARCH) {
            return "检测到 Xaero，但版本过旧（需要 24.7.1 或更高）";
        }
        if (!OFFICIAL_OK && !WAYPOINT_SET_OK) {
            return "检测到 Xaero，但没有可用的路径点写入通道（可能需要更新 FO）";
        }
        if (!OFFICIAL_OK) {
            return "Xaero 版本较旧，只有「玩家路径点集」通道（缺少官方第三方接口）";
        }
        if (!WAYPOINT_SET_OK) {
            return "Xaero 版本较新，只有「官方第三方接口」通道";
        }
        return "两条通道都可用";
    }

    // ==================== 写入 / 删除 ====================

    /**
     * 添加一个路径点。
     *
     * @param pref      通道偏好
     * @param colorName Xaero 颜色枚举名：{@code GOLD} / {@code BLUE} / {@code PURPLE}
     * @return 路径点是否已经在图上（新增成功或本来就在都算 true；通道不可用/会话未就绪为 false）
     */
    public static boolean addWaypoint(Preference pref, int x, int y, int z, String name, String group,
                                      String colorName) {
        Channel ch = pick(pref);
        if (ch == Channel.NONE) {
            return false;
        }
        try {
            Object world = currentWorld();
            if (world == null) {
                return false;
            }
            String id = waypointId(x, z);
            boolean ok = ch == Channel.OFFICIAL
                ? addOfficial(world, x, y, z, name, group, colorName, id)
                : addToPlayerSet(world, x, y, z, name, group, colorName, id);
            if (ok && ch == Channel.WAYPOINT_SET) {
                notifySetChanged();
            }
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 删除 (x,z) 上的路径点（箱子被开掉/被挖掉时用）。
     *
     * <p><b>两条通道都试一遍</b>、命中即止 —— 用户可能中途改过「路径点写入方式」，
     * 或者同一个点两种方式都写过，所以不能只看当前偏好的那条。
     *
     * @return 是否真的删掉了
     */
    public static boolean removeWaypointAt(int x, int z) {
        if (!XAERO_PRESENT || !NEW_ARCH) {
            return false;
        }
        try {
            Object world = currentWorld();
            if (world == null) {
                return false;
            }
            boolean removed = false;
            // 两条通道各自兜异常：一条失败不能吞掉另一条已经删成功的成果
            if (OFFICIAL_OK) {
                try {
                    if (removeOfficial(world, waypointId(x, z))) {
                        removed = true;
                    }
                } catch (Throwable ignored) {
                    // 换下一条通道
                }
            }
            try {
                if (removeFromPlayerSet(world, x, z)) {
                    removed = true;
                    notifySetChanged();
                }
            } catch (Throwable ignored) {
                // 官方通道的结果仍然有效
            }
            return removed;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 官方通道用的稳定 id：由水平坐标决定，删除时不用再扫一遍。 */
    private static String waypointId(int x, int z) {
        return "fo-ancient-" + x + "-" + z;
    }

    // ==================== 通道一：官方第三方接口 ====================

    private static boolean addOfficial(Object world, int x, int y, int z, String name, String group,
                                       String colorName, String id) throws Exception {
        Object thirdParty = thirdParty(world);
        if (thirdParty == null) {
            return false;
        }
        Method add = namedMethod(thirdParty.getClass(), "add", 2);
        if (add == null) {
            return false;
        }
        add.invoke(thirdParty, id, newWaypoint(x, y, z, name, group, colorName));
        return true;
    }

    private static boolean removeOfficial(Object world, String id) throws Exception {
        Object thirdParty = thirdParty(world);
        if (thirdParty == null) {
            return false;
        }
        // Xaero 的 remove(String) 返回 void，删不存在的 id 也是静默无操作，
        // 所以先查一下在不在，才能如实回答「到底删掉了没有」。
        Method get = namedMethod(thirdParty.getClass(), "get", 1);
        if (get != null && get.invoke(thirdParty, id) == null) {
            return false;
        }
        Method remove = namedMethod(thirdParty.getClass(), "remove", 1);
        if (remove == null) {
            return false;
        }
        remove.invoke(thirdParty, id);
        return true;
    }

    /** 取到我们这个来源（fo）的第三方路径点容器；{@code get} 内部是 computeIfAbsent，会按需创建。 */
    private static Object thirdParty(Object world) throws Exception {
        if (!OFFICIAL_OK) {
            return null;
        }
        Method getContainer = namedMethod(world.getClass(), "getContainer", 0);
        if (getContainer == null) {
            return null;
        }
        Object container = getContainer.invoke(world);
        if (container == null) {
            return null;
        }
        Method getMgr = namedMethod(container.getClass(), "getThirdPartyWaypointManager", 0);
        if (getMgr == null) {
            return null;
        }
        Object manager = getMgr.invoke(container);
        if (manager == null) {
            return null;
        }
        Method get = namedMethod(manager.getClass(), "get", 1);
        if (get == null) {
            return null;
        }
        // 参数类型是 Identifier（intermediary 下叫 class_2960），
        // 直接写类名会在生产环境（intermediary 命名空间）失效，所以从方法签名反推类型再构造。
        Object identifier = makeIdentifier(get.getParameterTypes()[0]);
        if (identifier == null) {
            return null;
        }
        return get.invoke(manager, identifier);
    }

    /**
     * 造一个 Identifier 实例，**不依赖任何 MC 类名**（生产环境用的是 intermediary 命名空间，
     * 硬编码 {@code net.minecraft.util.Identifier} 会在正式游戏里找不到类）。
     * 优先挑名为 {@code of} 的静态工厂（避免误用 {@code ofVanilla} 那种固定命名空间的工厂）。
     */
    private static Object makeIdentifier(Class<?> idCls) {
        if (idCls == null) {
            return null;
        }
        Method fallback = null;
        try {
            for (Method m : idCls.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (!idCls.isAssignableFrom(m.getReturnType())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[0] != String.class || p[1] != String.class) {
                    continue;
                }
                if ("of".equals(m.getName())) {
                    return m.invoke(null, ORIGIN_NAMESPACE, ORIGIN_PATH);
                }
                if (fallback == null) {
                    fallback = m;
                }
            }
            if (fallback != null) {
                return fallback.invoke(null, ORIGIN_NAMESPACE, ORIGIN_PATH);
            }
            for (Constructor<?> c : idCls.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1] == String.class) {
                    return c.newInstance(ORIGIN_NAMESPACE, ORIGIN_PATH);
                }
            }
        } catch (Throwable ignored) {
            // 落到 null，调用方会退化为「通道不可用」
        }
        return null;
    }

    // ==================== 通道二：玩家路径点集 ====================

    private static boolean addToPlayerSet(Object world, int x, int y, int z, String name, String group,
                                          String colorName, String id) throws Exception {
        Object set = currentWaypointSet(world);
        if (set == null) {
            return false;
        }
        if (findInSet(set, x, z) != null) {
            return true;
        }
        Method add = exactMethod(set.getClass(), "add", WAYPOINT_CLS);
        if (add == null) {
            return false;
        }
        add.invoke(set, newWaypoint(x, y, z, name, group, colorName));
        return true;
    }

    private static boolean removeFromPlayerSet(Object world, int x, int z) throws Exception {
        Object set = currentWaypointSet(world);
        if (set == null) {
            return false;
        }
        Object waypoint = findInSet(set, x, z);
        if (waypoint == null) {
            return false;
        }
        Method remove = exactMethod(set.getClass(), "remove", WAYPOINT_CLS);
        if (remove == null) {
            return false;
        }
        remove.invoke(set, waypoint);
        return true;
    }

    /** 玩家当前路径点集。 */
    private static Object currentWaypointSet(Object world) throws Exception {
        Method getSet = namedMethod(world.getClass(), "getCurrentWaypointSet", 0);
        return getSet == null ? null : getSet.invoke(world);
    }

    /** 在路径点集里按水平坐标找已有的点（避免重复添加）。 */
    private static Object findInSet(Object set, int x, int z) throws Exception {
        Method getWaypoints = namedMethod(set.getClass(), "getWaypoints", 0);
        if (getWaypoints == null) {
            return null;
        }
        Object iterable = getWaypoints.invoke(set);
        if (!(iterable instanceof Iterable<?> it)) {
            return null;
        }
        Method getX = namedMethod(WAYPOINT_CLS, "getX", 0);
        Method getZ = namedMethod(WAYPOINT_CLS, "getZ", 0);
        if (getX == null || getZ == null) {
            return null;
        }
        for (Object wp : it) {
            Object wx = getX.invoke(wp);
            Object wz = getZ.invoke(wp);
            if (wx instanceof Integer ix && wz instanceof Integer iz && ix == x && iz == z) {
                return wp;
            }
        }
        return null;
    }

    /** 通知 Xaero：这条路径点集变了（Xaero 自己切换路径点集时用的同一套机制）。 */
    private static void notifySetChanged() {
        try {
            Object session = currentSession();
            if (session == null) {
                return;
            }
            Method getWaypointSession = namedMethod(session.getClass(), "getWaypointSession", 0);
            if (getWaypointSession == null) {
                return;
            }
            Object waypointSession = getWaypointSession.invoke(session);
            if (waypointSession == null) {
                return;
            }
            Method setChanged = namedMethod(waypointSession.getClass(), "setSetChangedTime", 1);
            if (setChanged != null) {
                setChanged.invoke(waypointSession, System.currentTimeMillis());
            }
        } catch (Throwable ignored) {
            // 刷新通知失败不影响路径点本身
        }
    }

    // ==================== Waypoint 构造 ====================

    /**
     * 造一个 {@code Waypoint}。优先用命名的颜色枚举常量
     * （{@code WaypointColor.GOLD / BLUE / PURPLE}），**不再靠数字索引猜颜色**；
     * 枚举取不到时才退回整数索引。
     */
    private static Object newWaypoint(int x, int y, int z, String name, String group, String colorName)
        throws Exception {
        Object color = colorConstant(colorName);
        if (color != null) {
            try {
                Constructor<?> c = WAYPOINT_CLS.getConstructor(
                    int.class, int.class, int.class, String.class, String.class, WAYPOINT_COLOR_CLS);
                return c.newInstance(x, y, z, name, group, color);
            } catch (NoSuchMethodException ignored) {
                // 落到下面的整数索引构造
            }
        }
        return WAYPOINT_CLS
            .getConstructor(int.class, int.class, int.class, String.class, String.class, int.class, int.class,
                boolean.class)
            .newInstance(x, y, z, name, group, colorIndexFallback(colorName), 0, false);
    }

    /** 颜色枚举常量；取不到返回 null。 */
    private static Object colorConstant(String colorName) {
        if (WAYPOINT_COLOR_CLS == null || colorName == null || colorName.isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class<Enum>) WAYPOINT_COLOR_CLS, colorName);
            return value;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 枚举不可用时的整数索引兜底。Xaero 的 {@code WaypointColor} 枚举声明顺序即原版 16 色顺序，
     * 所以 6=GOLD、9=BLUE、13=PURPLE（已对着真实 jar 的枚举常量表核对）。
     */
    private static int colorIndexFallback(String colorName) {
        if ("GOLD".equals(colorName)) {
            return 6;
        }
        if ("BLUE".equals(colorName)) {
            return 9;
        }
        return 13;
    }

    // ==================== 会话 / 世界 ====================

    private static Object currentSession() throws Exception {
        if (BUILT_IN_HUD_CLS == null) {
            return null;
        }
        Object module = BUILT_IN_HUD_CLS.getField("MINIMAP").get(null);
        if (module == null) {
            return null;
        }
        Method getCurrentSession = namedMethod(module.getClass(), "getCurrentSession", 0);
        return getCurrentSession == null ? null : getCurrentSession.invoke(module);
    }

    private static Object currentWorld() throws Exception {
        Object session = currentSession();
        if (session == null) {
            return null;
        }
        Method getWorldManager = namedMethod(session.getClass(), "getWorldManager", 0);
        if (getWorldManager == null) {
            return null;
        }
        Object worldManager = getWorldManager.invoke(session);
        if (worldManager == null) {
            return null;
        }
        Method getCurrentWorld = namedMethod(worldManager.getClass(), "getCurrentWorld", 0);
        return getCurrentWorld == null ? null : getCurrentWorld.invoke(worldManager);
    }

    // ==================== 反射小工具 ====================

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 只按名字和参数个数找方法（参数类型在编译期未知时用）。
     *
     * <p>同名同参数个数的重载（例如 {@code WaypointSet.remove(Waypoint)} 与 {@code remove(int)}）
     * 优先挑参数里<b>没有基本类型</b>的那个，避免把 int 版删点方法当成对象版；
     * 但若是唯一候选则照收（{@code setSetChangedTime(long)} 就是基本类型参数）。
     */
    private static Method namedMethod(Class<?> start, String name, int paramCount) {
        if (start == null) {
            return null;
        }
        Method primitive = null;
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != paramCount) {
                    continue;
                }
                if (!hasPrimitiveParam(m)) {
                    return accessible(m);
                }
                if (primitive == null) {
                    primitive = m;
                }
            }
        }
        if (primitive != null) {
            return accessible(primitive);
        }
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            for (Method m : c.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
        }
        return null;
    }

    /** 按精确签名找方法（重载靠参数类型区分，例如 {@code remove(Waypoint)} 与 {@code remove(int)}）。 */
    private static Method exactMethod(Class<?> start, String name, Class<?>... params) {
        if (start == null) {
            return null;
        }
        try {
            return accessible(start.getMethod(name, params));
        } catch (Throwable ignored) {
            // 继续用下面的声明扫描兜底
        }
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && matches(m.getParameterTypes(), params)) {
                    return accessible(m);
                }
            }
        }
        return null;
    }

    private static boolean matches(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!actual[i].equals(expected[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasPrimitiveParam(Method m) {
        for (Class<?> p : m.getParameterTypes()) {
            if (p.isPrimitive()) {
                return true;
            }
        }
        return false;
    }

    private static Method accessible(Method m) {
        try {
            m.setAccessible(true);
        } catch (Throwable ignored) {
            // 关闭访问检查失败也无妨，公开方法照样能调
        }
        return m;
    }
}
