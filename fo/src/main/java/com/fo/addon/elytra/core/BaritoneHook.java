package com.fo.addon.elytra.core;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Baritone 桥接（纯反射实现，不依赖 baritone-api 编译期类）。
 *
 * <p>与 FO 既有的 {@code com.fo.addon.pathing.BaritonePathManager} 保持同一约定：Baritone 缺失、
 * 版本不符、方法签名变化时，任何一步失败都只返回安全的默认值（false / null / -1），绝不抛到调用方，
 * 更不会因为类加载失败把整个插件带崩。</p>
 *
 * <p>为什么不用 import：FO 的构建只依赖 Meteor 的 maven 仓库，不为 Baritone 额外引入编译期 jar；
 * 玩家自己的 Baritone（baritone-meteor / baritone-api-fabric）在运行期提供。</p>
 */
public final class BaritoneHook {
    private static Boolean available;
    private static boolean missingWarned;
    private static Class<?> cSettings;
    private static Class<?> cSettingsUtil;

    private BaritoneHook() {
    }

    // ------------------------------------------------------------------ 可用性

    public static boolean available() {
        if (Boolean.TRUE.equals(available)) return true;
        try {
            cSettings = Class.forName("baritone.api.Settings");
            Class.forName("baritone.api.BaritoneAPI");
            available = Boolean.TRUE;
            return true;
        } catch (Throwable t) {
            available = Boolean.FALSE;
            if (!missingWarned) {
                missingWarned = true;
                FOElytraLog.warn("未检测到可用的 Baritone：%s", String.valueOf(t));
            }
            return false;
        }
    }

    /** 拿 IBaritone 实例；拿不到返回 null。 */
    public static Object baritone() {
        if (!available()) return null;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean ready() {
        return available() && baritone() != null;
    }

    // ------------------------------------------------------------------ 通用反射工具

    /** 调无参方法，失败返回 null。 */
    private static Object call(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable t) {
            FOElytraLog.debug("Baritone 反射调用 %s() 失败：%s", name, t);
            return null;
        }
    }

    private static boolean callBool(Object target, String name, boolean fallback) {
        Object v = call(target, name);
        return v instanceof Boolean b ? b : fallback;
    }

    private static int callInt(Object target, String name, int fallback) {
        Object v = call(target, name);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    private static double callDouble(Object target, String name, double fallback) {
        Object v = call(target, name);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    /** Optional 拆包；不是 Optional 或为空都返回 null。 */
    private static Object optional(Object maybeOptional) {
        if (maybeOptional instanceof Optional<?> o) return o.orElse(null);
        return null;
    }

    private static Integer boxedInt(Object src, String method, String field) {
        if (src == null) return null;
        Object v = call(src, method);
        if (v instanceof Number n) return n.intValue();
        return fieldInt(src, field);
    }

    private static Integer fieldInt(Object src, String field) {
        if (src == null) return null;
        try {
            Field f = src.getClass().getField(field);
            Object v = f.get(src);
            return v instanceof Number n ? n.intValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** BlockPos / BetterBlockPos 的坐标，拿不到返回 Integer.MIN_VALUE。 */
    private static int posX(Object pos) {
        Integer v = boxedInt(pos, "getX", "x");
        return v == null ? Integer.MIN_VALUE : v;
    }

    private static int posY(Object pos) {
        Integer v = boxedInt(pos, "getY", "y");
        return v == null ? Integer.MIN_VALUE : v;
    }

    private static int posZ(Object pos) {
        Integer v = boxedInt(pos, "getZ", "z");
        return v == null ? Integer.MIN_VALUE : v;
    }

    private static String shortString(Object pos) {
        if (pos == null) return "?";
        Object s = call(pos, "toShortString");
        if (s != null) return String.valueOf(s);
        return posX(pos) + ", " + posY(pos) + ", " + posZ(pos);
    }

    // ------------------------------------------------------------------ 鞘翅进程

    public static boolean pathTo(int x, int z) {
        Object b = baritone();
        if (b == null) {
            FOElytraLog.detail("Baritone pathTo(%d,%d)：拿不到 IBaritone 实例，放弃", x, z);
            return false;
        }
        try {
            Object proc = call(b, "getElytraProcess");
            if (proc == null) {
                FOElytraLog.detail("Baritone pathTo(%d,%d)：拿不到鞘翅进程，放弃", x, z);
                return false;
            }
            proc.getClass().getMethod("pathTo", BlockPos.class).invoke(proc, new BlockPos(x, 0, z));
            FOElytraLog.detail("Baritone pathTo(%d,%d) 已下发（下发后 isActive=%s）", x, z,
                callBool(proc, "isActive", false));
            return true;
        } catch (Throwable t) {
            FOElytraLog.detailError("Baritone pathTo(" + x + "," + z + ")", t);
            FOElytraLog.warn("Baritone pathTo(%d,%d) 失败: %s", x, z, t);
            return false;
        }
    }

    public static boolean isFlying() {
        Object proc = call(baritone(), "getElytraProcess");
        return proc != null && callBool(proc, "isActive", false);
    }

    public static void resetState() {
        Object proc = call(baritone(), "getElytraProcess");
        if (proc == null) return;
        if (invokeNoArg(proc, "resetState")) {
            FOElytraLog.detail("Baritone resetState() 已调用");
        }
    }

    public static void repackChunks() {
        Object proc = call(baritone(), "getElytraProcess");
        if (proc == null) return;
        if (invokeNoArg(proc, "repackChunks")) {
            FOElytraLog.detail("Baritone repackChunks() 已调用");
        }
    }

    private static boolean invokeNoArg(Object target, String name) {
        try {
            target.getClass().getMethod(name).invoke(target);
            return true;
        } catch (Throwable t) {
            FOElytraLog.detailError("Baritone " + name, t);
            return false;
        }
    }

    // ------------------------------------------------------------------ 命令

    public static void command(String cmd) {
        Object b = baritone();
        if (b == null) {
            FOElytraLog.detail("Baritone 命令 %s：没有实例，跳过", cmd);
            return;
        }
        try {
            Object mgr = call(b, "getCommandManager");
            if (mgr == null) {
                FOElytraLog.detail("Baritone 命令 %s：拿不到命令管理器", cmd);
                return;
            }
            mgr.getClass().getMethod("execute", String.class).invoke(mgr, cmd);
            FOElytraLog.detail("Baritone 命令 %s 已执行", cmd);
        } catch (Throwable t) {
            FOElytraLog.detailError("Baritone 命令 " + cmd, t);
            FOElytraLog.debug("Baritone 命令 %s 执行失败: %s", cmd, t);
        }
    }

    public static void stop() {
        command("stop");
    }

    public static void pause() {
        command("p");
    }

    public static void resume() {
        command("r");
    }

    // ------------------------------------------------------------------ 挖掘 / 建造

    public static boolean mine(int count, Block block) {
        Object b = baritone();
        if (b == null || block == null) {
            FOElytraLog.detail("Baritone mine(%d, %s)：实例或方块为空，放弃", count, block);
            return false;
        }
        try {
            Object mineProc = call(b, "getMineProcess");
            if (mineProc == null) return false;
            Method m = mineProc.getClass().getMethod("mine", int.class, Block[].class);
            m.invoke(mineProc, count, new Block[]{block});
            FOElytraLog.detail("Baritone mine(%d, %s) 已下发（isActive=%s）", count, block,
                callBool(mineProc, "isActive", false));
            return true;
        } catch (Throwable t) {
            FOElytraLog.detailError("Baritone mine(" + count + ", " + block + ")", t);
            FOElytraLog.warn("Baritone mine 调用失败: %s", t);
            return false;
        }
    }

    public static boolean isMining() {
        Object mineProc = call(baritone(), "getMineProcess");
        return mineProc != null && callBool(mineProc, "isActive", false);
    }

    public static boolean builderClearArea(BlockPos from, BlockPos to) {
        Object b = baritone();
        if (b == null || from == null || to == null) return false;
        try {
            Object builder = call(b, "getBuilderProcess");
            if (builder == null) return false;
            builder.getClass().getMethod("clearArea", BlockPos.class, BlockPos.class).invoke(builder, from, to);
            FOElytraLog.detail("Baritone clearArea(%s → %s) 已下发（isActive=%s）", from.toShortString(),
                to.toShortString(), callBool(builder, "isActive", false));
            return true;
        } catch (Throwable t) {
            FOElytraLog.warn("Baritone clearArea(%s -> %s) 失败: %s", from, to, t);
            return false;
        }
    }

    public static boolean builderActive() {
        Object builder = call(baritone(), "getBuilderProcess");
        return builder != null && callBool(builder, "isActive", false);
    }

    // ------------------------------------------------------------------ 航点 / 剩余路程

    public record WaypointRoute(double distance, int waypoints, double baritoneTicks, String goal, boolean elytraStraight) {
    }

    public static WaypointRoute waypointRoute() {
        Object b = baritone();
        if (b == null) return null;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return null;

            Object pathing = call(b, "getPathingBehavior");
            if (pathing != null) {
                Object executor = call(pathing, "getCurrent");
                Object path = executor != null ? call(executor, "getPath") : optional(call(pathing, "getPath"));
                if (path != null) {
                    Object raw = call(path, "positions");
                    if (raw instanceof List<?> positions && !positions.isEmpty()) {
                        int from = executor == null ? 0 : callInt(executor, "getPosition", -1) + 1;
                        int start = Math.max(0, Math.min(from, positions.size() - 1));
                        double d = 0;
                        Object head = positions.get(start);
                        d += dist(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            posX(head), posY(head), posZ(head), true);
                        for (int i = start; i + 1 < positions.size(); i++) {
                            Object a = positions.get(i);
                            Object c = positions.get(i + 1);
                            d += dist(posX(a), posY(a), posZ(a), posX(c), posY(c), posZ(c), true);
                        }
                        Object goal = call(path, "getGoal");
                        d += goalGap(goal, positions.get(positions.size() - 1));

                        double ticks = -1;
                        Object est = call(pathing, "estimatedTicksToGoal");
                        if (est instanceof Optional<?> o && o.isPresent() && o.get() instanceof Number n) {
                            ticks = n.doubleValue();
                        }
                        if (ticks < 0) ticks = ticksRemainingFrom(path, start);

                        return new WaypointRoute(d, positions.size() - start, ticks, goalText(goal), false);
                    }
                }
            }

            Object elytra = call(b, "getElytraProcess");
            if (elytra != null && callBool(elytra, "isActive", false)) {
                Object dest = call(elytra, "currentDestination");
                if (dest != null) {
                    double d = dist(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        posX(dest), posY(dest), posZ(dest), false);
                    if (d > 0.5) return new WaypointRoute(d, -1, -1, shortString(dest), true);
                }
            }
            return null;
        } catch (Throwable t) {
            FOElytraLog.detailError("BaritoneHook waypointRoute", t);
            return null;
        }
    }

    private static double ticksRemainingFrom(Object path, int start) {
        if (path == null) return -1;
        try {
            Object v = path.getClass().getMethod("ticksRemainingFrom", int.class).invoke(path, start);
            return v instanceof Number n ? n.doubleValue() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static double dist(double x1, double y1, double z1, double x2, double y2, double z2, boolean withY) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        double dy = withY ? y1 - y2 : 0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double goalGap(Object goal, Object last) {
        if (goal == null || last == null) return 0;
        try {
            String simple = goal.getClass().getSimpleName();
            if ("GoalBlock".equals(simple)) {
                Integer gx = boxedInt(goal, "getX", "x");
                Integer gy = boxedInt(goal, "getY", "y");
                Integer gz = boxedInt(goal, "getZ", "z");
                if (gx == null || gy == null || gz == null) return 0;
                double d = dist(posX(last), posY(last), posZ(last), gx, gy, gz, true);
                return d > 1.0 ? d : 0;
            }
            if ("GoalXZ".equals(simple)) {
                Integer gx = boxedInt(goal, "getX", "x");
                Integer gz = boxedInt(goal, "getZ", "z");
                if (gx == null || gz == null) return 0;
                double d = dist(posX(last), 0, posZ(last), gx, 0, gz, false);
                return d > 1.0 ? d : 0;
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("BaritoneHook goalGap", t);
        }
        return 0;
    }

    private static String goalText(Object goal) {
        return goal == null ? "" : String.valueOf(goal);
    }

    // ------------------------------------------------------------------ 设置读写

    private static Object settings() {
        if (!available()) return null;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            return api.getMethod("getSettings").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按小写名取 Baritone 设置项；拿不到返回 null。 */
    @SuppressWarnings("unchecked")
    public static Object btSetting(String name) {
        Object s = settings();
        if (s == null || name == null) return null;
        String key = name.toLowerCase(Locale.ROOT);
        try {
            Field f = s.getClass().getField("byLowerName");
            Object map = f.get(s);
            if (map instanceof Map<?, ?> m) {
                Object v = m.get(key);
                if (v != null) return v;
            }
        } catch (Throwable t) {
            FOElytraLog.debug("读取 Baritone byLowerName 失败：%s", t);
        }
        // 兜底：遍历全部设置项按 name / getName() 匹配
        try {
            Field f = s.getClass().getField("settings");
            Object list = f.get(s);
            if (list instanceof List<?> l) {
                for (Object item : l) {
                    Object n = call(item, "getName");
                    String ns = n == null ? fieldString(item, "name") : String.valueOf(n);
                    if (ns != null && ns.toLowerCase(Locale.ROOT).equals(key)) return item;
                }
            }
        } catch (Throwable t) {
            FOElytraLog.debug("遍历 Baritone settings 失败：%s", t);
        }
        return null;
    }

    private static String fieldString(Object src, String field) {
        if (src == null) return null;
        try {
            Field f = src.getClass().getField(field);
            Object v = f.get(src);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object settingValue(Object setting, boolean defaultValue) {
        if (setting == null) return null;
        try {
            Field f = setting.getClass().getField(defaultValue ? "defaultValue" : "value");
            return f.get(setting);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean btBool(String name, boolean fallback) {
        Object v = settingValue(btSetting(name), false);
        return v instanceof Boolean b ? b : fallback;
    }

    public static double btDouble(String name, double fallback) {
        Object v = settingValue(btSetting(name), false);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public static int btInt(String name, int fallback) {
        Object v = settingValue(btSetting(name), false);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    public static boolean btDefaultBool(String name, boolean fallback) {
        Object v = settingValue(btSetting(name), true);
        return v instanceof Boolean b ? b : fallback;
    }

    public static double btDefaultDouble(String name, double fallback) {
        Object v = settingValue(btSetting(name), true);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public static int btDefaultInt(String name, int fallback) {
        Object v = settingValue(btSetting(name), true);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    /** 写入 Baritone 设置项；类型不匹配或写失败返回 false。 */
    public static boolean btSet(String name, Object value) {
        Object setting = btSetting(name);
        if (setting == null || value == null) return false;
        Object current = settingValue(setting, false);
        if (current != null && !current.getClass().isInstance(value)) {
            FOElytraLog.debug("Baritone 设置 %s 类型不匹配：期望 %s，收到 %s",
                name, current.getClass().getSimpleName(), value.getClass().getSimpleName());
            return false;
        }
        try {
            Field f = setting.getClass().getField("value");
            f.set(setting, value);
            return true;
        } catch (Throwable t) {
            FOElytraLog.debug("写入 Baritone 设置 %s 失败：%s", name, t);
            return false;
        }
    }

    /** 是否已接受 Baritone 鞘翅使用条款（读不到就当作已接受，不阻拦玩家）。 */
    public static boolean termsAccepted() {
        return btBool("elytraTermsAccepted", true);
    }

    public static void acceptTerms() {
        btSet("elytraTermsAccepted", true);
    }

    /** 一次性下发 FO 关心的三个鞘翅飞行设置。 */
    public static void applyFlightSettings(boolean autoJump, double fireworkSpeed, boolean allowEmergencyLand) {
        btSet("elytraAutoJump", autoJump);
        btSet("elytraFireworkSpeed", fireworkSpeed);
        btSet("elytraAllowEmergencyLand", allowEmergencyLand);
    }

    public static boolean saveBaritone() {
        Object s = settings();
        if (s == null || cSettings == null) return false;
        try {
            if (cSettingsUtil == null) cSettingsUtil = Class.forName("baritone.api.utils.SettingsUtil");
            cSettingsUtil.getMethod("save", cSettings).invoke(null, s);
            return true;
        } catch (Throwable t) {
            FOElytraLog.err("保存 Baritone 设置文件失败：%s", String.valueOf(t));
            return false;
        }
    }

    /** 当前与出厂默认值不同的设置项数量；拿不到返回 -1。 */
    public static int modifiedCount() {
        Object s = settings();
        if (s == null || cSettings == null) return -1;
        try {
            if (cSettingsUtil == null) cSettingsUtil = Class.forName("baritone.api.utils.SettingsUtil");
            Object out = cSettingsUtil.getMethod("modifiedSettings", cSettings).invoke(null, s);
            return out instanceof Collection<?> c ? c.size() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ 段失败计数

    /** 段失败计数窗口（纯逻辑，见 {@link SegFailWindow}，可单测）。 */
    private static final SegFailWindow SEG_FAIL = new SegFailWindow();

    private static Consumer<Object> previousLogger;
    private static Consumer<Object> segFailLogger;

    public static void markTick() {
        SEG_FAIL.markTick();
    }

    public static int segFailCount() {
        return SEG_FAIL.count();
    }

    public static void clearSegFailCounter() {
        SEG_FAIL.clear();
    }

    public static boolean consumeSegResetRequest() {
        return SEG_FAIL.consumeResetRequest();
    }

    /** 拦截 Baritone 的段失败日志，统计 6 tick 窗口内的失败条数。 */
    @SuppressWarnings("unchecked")
    public static boolean installSegFailLogger() {
        Object setting = btSetting("logger");
        if (setting == null) return false;
        try {
            Object current = settingValue(setting, false);
            if (current == segFailLogger) return true;
            previousLogger = (current instanceof Consumer<?> c) ? (Consumer<Object>) c : null;
            if (segFailLogger == null) {
                segFailLogger = msg -> {
                    if (previousLogger != null) {
                        try {
                            previousLogger.accept(msg);
                        } catch (Throwable ignored) {
                        }
                    }
                    String text = messageText(msg);
                    if (text.contains("Failed to compute path to destination")
                        || text.contains("Failed to recompute segment")
                        || text.contains("Failed to compute next segment")) {
                        SEG_FAIL.recordFailure();
                    }
                };
            }
            Field f = setting.getClass().getField("value");
            f.set(setting, segFailLogger);
            FOElytraLog.detail("已安装 SegFailed 计数器（拦 Baritone 的段失败消息，%d tick 窗口）", SegFailWindow.WINDOW);
            return true;
        } catch (Throwable t) {
            FOElytraLog.debug("安装 SegFailed 计数器失败：%s", t);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static void removeSegFailLogger() {
        if (segFailLogger == null) return;
        try {
            Object setting = btSetting("logger");
            if (setting != null && settingValue(setting, false) == segFailLogger) {
                Field f = setting.getClass().getField("value");
                f.set(setting, previousLogger);
            }
        } catch (Throwable ignored) {
        }
        segFailLogger = null;
        previousLogger = null;
        clearSegFailCounter();
    }

    private static String messageText(Object msg) {
        if (msg == null) return "";
        try {
            Method m = msg.getClass().getMethod("getString");
            Object out = m.invoke(msg);
            return out == null ? "" : out.toString();
        } catch (Throwable t) {
            return String.valueOf(msg);
        }
    }
}
