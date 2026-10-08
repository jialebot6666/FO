package com.fo.addon.elytra.core;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.IMineProcess;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import com.fo.addon.elytra.core.FOElytraLog;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class BaritoneHook {
    private static Boolean available;
    private static boolean missingWarned;
    private static final int SEG_FAIL_WINDOW = 6;
    private static int btTick;
    private static int segFailCount;
    private static int segFailLastTick;
    private static boolean segResetRequested;
    private static Consumer<Object> previousLogger;
    private static Consumer<Object> segFailLogger;

    private BaritoneHook() {
    }

    public static boolean available() {
        if (Boolean.TRUE.equals(available)) {
            return true;
        }
        try {
            Class.forName("baritone.api.BaritoneAPI");
            available = Boolean.TRUE;
            return true;
        }
        catch (Throwable t) {
            available = Boolean.FALSE;
            if (!missingWarned) {
                missingWarned = true;
                FOElytraLog.warn("\u672a\u68c0\u6d4b\u5230\u53ef\u7528\u7684 Baritone\uff1a%s", String.valueOf(t));
            }
            return false;
        }
    }

    public static IBaritone baritone() {
        if (!BaritoneHook.available()) {
            return null;
        }
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        }
        catch (Throwable t) {
            return null;
        }
    }

    public static boolean ready() {
        return BaritoneHook.available() && BaritoneHook.baritone() != null;
    }

    public static boolean pathTo(int x, int z) {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            FOElytraLog.detail("Baritone pathTo(%d,%d)\uff1a\u62ff\u4e0d\u5230 IBaritone \u5b9e\u4f8b\uff0c\u653e\u5f03", x, z);
            return false;
        }
        try {
            IElytraProcess proc = b.getElytraProcess();
            if (proc == null) {
                FOElytraLog.detail("Baritone pathTo(%d,%d)\uff1a\u62ff\u4e0d\u5230\u9798\u7fc5\u8fdb\u7a0b\uff0c\u653e\u5f03", x, z);
                return false;
            }
            proc.pathTo(new BlockPos(x, 0, z));
            FOElytraLog.detail("Baritone pathTo(%d,%d) \u5df2\u4e0b\u53d1\uff08\u4e0b\u53d1\u540e isActive=%s\uff09", x, z, proc.isActive());
            return true;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("Baritone pathTo(" + x + "," + z + ")", t);
            FOElytraLog.warn("Baritone pathTo(%d,%d) \u5931\u8d25: %s", x, z, t);
            return false;
        }
    }

    public static boolean isFlying() {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            return false;
        }
        try {
            IElytraProcess proc = b.getElytraProcess();
            return proc != null && proc.isActive();
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static void resetState() {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            return;
        }
        try {
            IElytraProcess proc = b.getElytraProcess();
            if (proc != null) {
                proc.resetState();
                FOElytraLog.detail("Baritone resetState() \u5df2\u8c03\u7528", new Object[0]);
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("Baritone resetState", t);
        }
    }

    public static void repackChunks() {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            return;
        }
        try {
            IElytraProcess proc = b.getElytraProcess();
            if (proc != null) {
                proc.repackChunks();
                FOElytraLog.detail("Baritone repackChunks() \u5df2\u8c03\u7528", new Object[0]);
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("Baritone repackChunks", t);
        }
    }

    public static void command(String cmd) {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            FOElytraLog.detail("Baritone \u547d\u4ee4 %s\uff1a\u6ca1\u6709\u5b9e\u4f8b\uff0c\u8df3\u8fc7", cmd);
            return;
        }
        try {
            b.getCommandManager().execute(cmd);
            FOElytraLog.detail("Baritone \u547d\u4ee4 %s \u5df2\u6267\u884c", cmd);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("Baritone \u547d\u4ee4 " + cmd, t);
            FOElytraLog.debug("Baritone \u547d\u4ee4 %s \u6267\u884c\u5931\u8d25: %s", cmd, t);
        }
    }

    public static void stop() {
        BaritoneHook.command("stop");
    }

    public static void pause() {
        BaritoneHook.command("p");
    }

    public static void resume() {
        BaritoneHook.command("r");
    }

    public static boolean mine(int count, Block block) {
        IBaritone b = BaritoneHook.baritone();
        if (b == null || block == null) {
            FOElytraLog.detail("Baritone mine(%d, %s)\uff1a\u5b9e\u4f8b\u6216\u65b9\u5757\u4e3a\u7a7a\uff0c\u653e\u5f03", count, block);
            return false;
        }
        try {
            IMineProcess mine = b.getMineProcess();
            if (mine == null) {
                return false;
            }
            mine.mine(count, new Block[]{block});
            FOElytraLog.detail("Baritone mine(%d, %s) \u5df2\u4e0b\u53d1\uff08isActive=%s\uff09", count, block, mine.isActive());
            return true;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("Baritone mine(" + count + ", " + String.valueOf(block) + ")", t);
            FOElytraLog.warn("Baritone mine \u8c03\u7528\u5931\u8d25: %s", t);
            return false;
        }
    }

    public static boolean isMining() {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            return false;
        }
        try {
            IMineProcess mine = b.getMineProcess();
            return mine != null && mine.isActive();
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean builderClearArea(BlockPos from, BlockPos to) {
        IBaritone b = BaritoneHook.baritone();
        if (b == null || from == null || to == null) {
            return false;
        }
        try {
            IBuilderProcess builder = b.getBuilderProcess();
            if (builder == null) {
                return false;
            }
            builder.clearArea(from, to);
            FOElytraLog.detail("Baritone clearArea(%s \u2192 %s) \u5df2\u4e0b\u53d1\uff08isActive=%s\uff09", from.toShortString(), to.toShortString(), builder.isActive());
            return true;
        }
        catch (Throwable t) {
            FOElytraLog.warn("Baritone clearArea(%s -> %s) \u5931\u8d25: %s", from, to, t);
            return false;
        }
    }

    public static boolean builderActive() {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            return false;
        }
        try {
            IBuilderProcess builder = b.getBuilderProcess();
            return builder != null && builder.isActive();
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static WaypointRoute waypointRoute() {
        IBaritone b = BaritoneHook.baritone();
        if (b == null) {
            return null;
        }
        try {
            double d;
            BlockPos dest;
            IElytraProcess elytra;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                return null;
            }
            IPathingBehavior pathing = b.getPathingBehavior();
            if (pathing != null) {
                List positions;
                IPath path;
                IPathExecutor executor = pathing.getCurrent();
                IPath iPath = path = executor != null ? executor.getPath() : (IPath)pathing.getPath().orElse(null);
                if (path != null && (positions = path.positions()) != null && !positions.isEmpty()) {
                    int from = executor == null ? 0 : executor.getPosition() + 1;
                    int start = Math.max(0, Math.min(from, positions.size() - 1));
                    double d2 = 0.0;
                    BetterBlockPos head = (BetterBlockPos)positions.get(start);
                    d2 += BaritoneHook.dist(mc.player.getX(), mc.player.getY(), mc.player.getZ(), head.getX(), head.getY(), head.getZ(), true);
                    int i = start;
                    while (i + 1 < positions.size()) {
                        BetterBlockPos a = (BetterBlockPos)positions.get(i);
                        BetterBlockPos c = (BetterBlockPos)positions.get(i + 1);
                        d2 += BaritoneHook.dist(a.getX(), a.getY(), a.getZ(), c.getX(), c.getY(), c.getZ(), true);
                        ++i;
                    }
                    double ticks = pathing.estimatedTicksToGoal().orElse(path.ticksRemainingFrom(start));
                    return new WaypointRoute(d2 += BaritoneHook.goalGap(path.getGoal(), (BetterBlockPos)positions.get(positions.size() - 1)), positions.size() - start, ticks, BaritoneHook.goalText(path.getGoal()), false);
                }
            }
            if ((elytra = b.getElytraProcess()) != null && elytra.isActive() && (dest = elytra.currentDestination()) != null && (d = BaritoneHook.dist(mc.player.getX(), mc.player.getY(), mc.player.getZ(), dest.getX(), dest.getY(), dest.getZ(), false)) > 0.5) {
                return new WaypointRoute(d, -1, -1.0, dest.toShortString(), true);
            }
            return null;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("BaritoneHook waypointRoute", t);
            return null;
        }
    }

    private static double dist(double x1, double y1, double z1, double x2, double y2, double z2, boolean withY) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        double dy = withY ? y1 - y2 : 0.0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double goalGap(Goal goal, BetterBlockPos last) {
        if (goal == null || last == null) {
            return 0.0;
        }
        try {
            if (goal instanceof GoalBlock) {
                GoalBlock gb = (GoalBlock)goal;
                double d = BaritoneHook.dist(last.getX(), last.getY(), last.getZ(), gb.x, gb.y, gb.z, true);
                return d > 1.0 ? d : 0.0;
            }
            if (goal instanceof GoalXZ) {
                GoalXZ gxz = (GoalXZ)goal;
                double d = BaritoneHook.dist(last.getX(), 0.0, last.getZ(), gxz.getX(), 0.0, gxz.getZ(), false);
                return d > 1.0 ? d : 0.0;
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("BaritoneHook goalGap", t);
        }
        return 0.0;
    }

    private static String goalText(Goal goal) {
        return goal == null ? "" : goal.toString();
    }

    private static Settings settings() {
        if (!BaritoneHook.available()) {
            return null;
        }
        try {
            return BaritoneAPI.getSettings();
        }
        catch (Throwable t) {
            return null;
        }
    }

    public static void setBool(Consumer<Settings> setter) {
        Settings s = BaritoneHook.settings();
        if (s == null) {
            return;
        }
        try {
            setter.accept(s);
        }
        catch (Throwable t) {
            FOElytraLog.debug("\u4fee\u6539 Baritone \u8bbe\u7f6e\u5931\u8d25: %s", t);
        }
    }

    public static boolean termsAccepted() {
        Settings s = BaritoneHook.settings();
        if (s == null) {
            return true;
        }
        try {
            return (Boolean)s.elytraTermsAccepted.value;
        }
        catch (Throwable t) {
            return true;
        }
    }

    public static void acceptTerms() {
        BaritoneHook.setBool(s -> {
            s.elytraTermsAccepted.value = true;
        });
    }

    public static void applyFlightSettings(boolean autoJump, double fireworkSpeed, boolean allowEmergencyLand) {
        BaritoneHook.setBool(s -> {
            s.elytraAutoJump.value = autoJump;
            s.elytraFireworkSpeed.value = fireworkSpeed;
            s.elytraAllowEmergencyLand.value = allowEmergencyLand;
        });
    }

    public static Settings.Setting<?> btSetting(String name) {
        Settings s = BaritoneHook.settings();
        if (s == null || name == null) {
            return null;
        }
        try {
            return (Settings.Setting)s.byLowerName.get(name.toLowerCase(Locale.ROOT));
        }
        catch (Throwable t) {
            return null;
        }
    }

    public static boolean btBool(String name, boolean fallback) {
        boolean bl;
        Object object;
        Settings.Setting<?> st = BaritoneHook.btSetting(name);
        if (st != null && (object = st.value) instanceof Boolean) {
            Boolean b = (Boolean)object;
            bl = b;
        } else {
            bl = fallback;
        }
        return bl;
    }

    public static double btDouble(String name, double fallback) {
        double d;
        Object object;
        Settings.Setting<?> st = BaritoneHook.btSetting(name);
        if (st != null && (object = st.value) instanceof Double) {
            Double d2 = (Double)object;
            d = d2;
        } else {
            d = fallback;
        }
        return d;
    }

    public static int btInt(String name, int fallback) {
        int n;
        Object object;
        Settings.Setting<?> st = BaritoneHook.btSetting(name);
        if (st != null && (object = st.value) instanceof Integer) {
            Integer i = (Integer)object;
            n = i;
        } else {
            n = fallback;
        }
        return n;
    }

    public static boolean btDefaultBool(String name, boolean fallback) {
        boolean bl;
        Object object;
        Settings.Setting<?> st = BaritoneHook.btSetting(name);
        if (st != null && (object = st.defaultValue) instanceof Boolean) {
            Boolean b = (Boolean)object;
            bl = b;
        } else {
            bl = fallback;
        }
        return bl;
    }

    public static double btDefaultDouble(String name, double fallback) {
        double d;
        Object object;
        Settings.Setting<?> st = BaritoneHook.btSetting(name);
        if (st != null && (object = st.defaultValue) instanceof Double) {
            Double d2 = (Double)object;
            d = d2;
        } else {
            d = fallback;
        }
        return d;
    }

    public static int btDefaultInt(String name, int fallback) {
        int n;
        Object object;
        Settings.Setting<?> st = BaritoneHook.btSetting(name);
        if (st != null && (object = st.defaultValue) instanceof Integer) {
            Integer i = (Integer)object;
            n = i;
        } else {
            n = fallback;
        }
        return n;
    }

    public static boolean btSet(String name, Object value) {
        Settings.Setting<?> setting = BaritoneHook.btSetting(name);
        if (setting == null || value == null) {
            return false;
        }
        Object current = setting.value;
        if (current != null && !current.getClass().isInstance(value)) {
            FOElytraLog.debug("Baritone \u8bbe\u7f6e %s \u7c7b\u578b\u4e0d\u5339\u914d\uff1a\u671f\u671b %s\uff0c\u6536\u5230 %s", name, current.getClass().getSimpleName(), value.getClass().getSimpleName());
            return false;
        }
        try {
            ((Settings.Setting)setting).value = value;
            return true;
        }
        catch (Throwable t) {
            FOElytraLog.debug("\u5199\u5165 Baritone \u8bbe\u7f6e %s \u5931\u8d25\uff1a%s", name, t);
            return false;
        }
    }

    public static boolean saveBaritone() {
        if (!BaritoneHook.available()) {
            return false;
        }
        try {
            SettingsUtil.save((Settings)BaritoneAPI.getSettings());
            return true;
        }
        catch (Throwable t) {
            FOElytraLog.err("\u4fdd\u5b58 Baritone \u8bbe\u7f6e\u6587\u4ef6\u5931\u8d25\uff1a%s", String.valueOf(t));
            return false;
        }
    }

    public static void markTick() {
        ++btTick;
    }

    public static int segFailCount() {
        return btTick - segFailLastTick > 6 ? 0 : segFailCount;
    }

    public static void clearSegFailCounter() {
        segFailCount = 0;
        segFailLastTick = -1073741824;
        segResetRequested = false;
    }

    public static boolean consumeSegResetRequest() {
        if (!segResetRequested) {
            return false;
        }
        segResetRequested = false;
        return true;
    }

    private static void accumulateSegFail() {
        segFailCount = btTick - segFailLastTick > 6 ? 1 : ++segFailCount;
        segFailLastTick = btTick;
    }

    public static boolean installSegFailLogger() {
        if (!BaritoneHook.available()) {
            return false;
        }
        Settings s = BaritoneHook.settings();
        if (s == null) {
            return false;
        }
        try {
            Consumer<Object> c;
            Settings.Setting<?> st = BaritoneHook.btSetting("logger");
            if (st == null) {
                return false;
            }
            Object current = st.value;
            if (current == segFailLogger) {
                return true;
            }
            Consumer<Object> consumer = previousLogger = current instanceof Consumer ? (c = (Consumer<Object>)current) : null;
            if (segFailLogger == null) {
                segFailLogger = msg -> {
                    String text;
                    if (previousLogger != null) {
                        try {
                            previousLogger.accept(msg);
                        }
                        catch (Throwable throwable) {
                            // empty catch block
                        }
                    }
                    if ((text = BaritoneHook.messageText(msg)).contains("Failed to compute path to destination") || text.contains("Failed to recompute segment") || text.contains("Failed to compute next segment")) {
                        BaritoneHook.accumulateSegFail();
                        segResetRequested = true;
                    }
                };
            }
            ((Settings.Setting)st).value = segFailLogger;
            FOElytraLog.detail("\u5df2\u5b89\u88c5 SegFailed \u8ba1\u6570\u5668\uff08\u62e6 Baritone \u7684\u6bb5\u5931\u8d25\u6d88\u606f\uff0c%d tick \u7a97\u53e3\uff09", 6);
            return true;
        }
        catch (Throwable t) {
            FOElytraLog.debug("\u5b89\u88c5 SegFailed \u8ba1\u6570\u5668\u5931\u8d25\uff1a%s", t);
            return false;
        }
    }

    public static void removeSegFailLogger() {
        if (segFailLogger == null) {
            return;
        }
        try {
            Settings.Setting<?> st = BaritoneHook.btSetting("logger");
            if (st != null && st.value == segFailLogger) {
                ((Settings.Setting)st).value = previousLogger;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        segFailLogger = null;
        previousLogger = null;
        BaritoneHook.clearSegFailCounter();
    }

    private static String messageText(Object msg) {
        if (msg == null) {
            return "";
        }
        try {
            Method m = msg.getClass().getMethod("getString", new Class[0]);
            Object out = m.invoke(msg, new Object[0]);
            return out == null ? "" : out.toString();
        }
        catch (Throwable t) {
            return String.valueOf(msg);
        }
    }

    public static int modifiedCount() {
        if (!BaritoneHook.available()) {
            return -1;
        }
        try {
            return SettingsUtil.modifiedSettings((Settings)BaritoneAPI.getSettings()).size();
        }
        catch (Throwable t) {
            return -1;
        }
    }

    static {
        segFailLastTick = -1073741824;
    }

    public record WaypointRoute(double distance, int waypoints, double baritoneTicks, String goal, boolean elytraStraight) {
    }
}

