package com.fo.addon.elytra.core;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayDeque;
import java.util.Deque;
public final class SpeedTracker {
    private record Sample(long timeMs, double x, double y, double z) {
    }
    public record SegmentReport(int index, double seconds, double path3d, double pathHorizontal, double pathVertical,
                                double averageHorizontal, double average3d, double directDistance, double deltaHorizontal) {
    }
    private static final long MIN_GAP_MS = 20;
    private static final double TELEPORT_JUMP = 32.0;
    private static final int MAX_SAMPLES = 4096;
    private static final Deque<Sample> window = new ArrayDeque<>();
    private static long windowMs = 5000;
    private static boolean started;
    private static long startMs;
    private static long lastMs;
    private static double lastX, lastY, lastZ;
    private static Object lastWorld;
    private static double totalPath3d;
    private static double totalPathHorizontal;
    private static double totalPathVertical;
    private static double netFromX, netFromY, netFromZ;
    private static int sampleCount;
    private static int teleportCount;
    private static double winPath3d;
    private static double winPathHorizontal;
    private static double winPathVertical;
    private static double peakHorizontal;
    private static double instantHorizontal;
    private static double instant3d;
    private static int segmentTicks;
    private static int segmentTargetTicks = 600;
    private static int segmentCount;
    private static int segmentLastAge = -1;
    private static double segmentPath3d;
    private static double segmentPathHorizontal;
    private static double segmentPathVertical;
    private static double segmentFromX, segmentFromY, segmentFromZ;
    private static double segmentPrevAverageHorizontal = -1;
    private static boolean segmentChat = true;
    private static SegmentReport lastSegment;
    private SpeedTracker() {
    }
    public static boolean sample() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return false;
        long now = System.currentTimeMillis();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        Object world = mc.world;
        if (lastWorld != null && lastWorld != world) {
            reset();
            lastWorld = world;
            startMs = now;
            lastMs = now;
            lastX = x;
            lastY = y;
            lastZ = z;
            netFromX = x;
            netFromY = y;
            netFromZ = z;
            segmentFromX = x;
            segmentFromY = y;
            segmentFromZ = z;
            segmentLastAge = mc.player.age;
            started = true;
            return false;
        }
        lastWorld = world;
        if (!started) {
            started = true;
            startMs = now;
            lastMs = now;
            lastX = x;
            lastY = y;
            lastZ = z;
            netFromX = x;
            netFromY = y;
            netFromZ = z;
            segmentFromX = x;
            segmentFromY = y;
            segmentFromZ = z;
            segmentLastAge = mc.player.age;
            window.addLast(new Sample(now, x, y, z));
            return true;
        }
        if (now - lastMs < MIN_GAP_MS) return false;
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        double d3 = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double dh = Math.sqrt(dx * dx + dz * dz);
        double dv = Math.abs(dy);
        if (d3 > TELEPORT_JUMP) {
            teleportCount++;
            FOElytraLog.detail("检测到传送/位置跳变 %.1f 格（第 %d 次），速度窗口已重置", d3, teleportCount);
            clearWindow();
            lastMs = now;
            lastX = x;
            lastY = y;
            lastZ = z;
            return false;
        }
        double dtSec = (now - lastMs) / 1000.0;
        if (dtSec > 0) {
            instantHorizontal = dh / dtSec;
            instant3d = d3 / dtSec;
            if (instantHorizontal > peakHorizontal) peakHorizontal = instantHorizontal;
        }
        totalPath3d += d3;
        totalPathHorizontal += dh;
        totalPathVertical += dv;
        winPath3d += d3;
        winPathHorizontal += dh;
        winPathVertical += dv;
        sampleCount++;
        tickSegment(mc, d3, dh, dv, x, y, z);
        window.addLast(new Sample(now, x, y, z));
        trimWindow(now);
        lastMs = now;
        lastX = x;
        lastY = y;
        lastZ = z;
        return true;
    }
    private static void trimWindow(long now) {
        long cutoff = now - windowMs;
        while (window.size() > 2) {
            Sample first = window.peekFirst();
            Sample second = null;
            var it = window.iterator();
            it.next();
            if (it.hasNext()) second = it.next();
            if (first == null || second == null || second.timeMs() >= cutoff) break;
            double dx = second.x() - first.x();
            double dy = second.y() - first.y();
            double dz = second.z() - first.z();
            double d3 = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d3 <= TELEPORT_JUMP) {
                winPath3d -= d3;
                winPathHorizontal -= Math.sqrt(dx * dx + dz * dz);
                winPathVertical -= Math.abs(dy);
            }
            window.removeFirst();
        }
        while (window.size() > MAX_SAMPLES) window.removeFirst();
    }
    private static void clearWindow() {
        window.clear();
        winPath3d = winPathHorizontal = winPathVertical = 0;
        instantHorizontal = instant3d = 0;
    }
    public static void reset() {
        clearWindow();
        started = false;
        startMs = 0;
        lastMs = 0;
        totalPath3d = totalPathHorizontal = totalPathVertical = 0;
        sampleCount = 0;
        teleportCount = 0;
        peakHorizontal = 0;
        netFromX = netFromY = netFromZ = 0;
        segmentTicks = 0;
        segmentCount = 0;
        segmentLastAge = -1;
        segmentPath3d = segmentPathHorizontal = segmentPathVertical = 0;
        segmentFromX = segmentFromY = segmentFromZ = 0;
        segmentPrevAverageHorizontal = -1;
        lastSegment = null;
    }
    private static void tickSegment(MinecraftClient mc, double d3, double dh, double dv, double x, double y, double z) {
        int age = mc.player.age;
        boolean newTick = age != segmentLastAge;
        segmentLastAge = age;
        if (!newTick) return;
        segmentTicks++;
        segmentPath3d += d3;
        segmentPathHorizontal += dh;
        segmentPathVertical += dv;
        if (segmentTicks >= segmentTargetTicks) settleSegment(x, y, z);
    }
    private static void settleSegment(double x, double y, double z) {
        double seconds = segmentTicks / 20.0;
        double avgHorizontal = seconds <= 0 ? 0 : segmentPathHorizontal / seconds;
        double avg3d = seconds <= 0 ? 0 : segmentPath3d / seconds;
        double dx = x - segmentFromX;
        double dy = y - segmentFromY;
        double dz = z - segmentFromZ;
        double direct = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean hasPrev = segmentPrevAverageHorizontal >= 0;
        double delta = hasPrev ? avgHorizontal - segmentPrevAverageHorizontal : 0;
        segmentCount++;
        lastSegment = new SegmentReport(segmentCount, seconds, segmentPath3d, segmentPathHorizontal, segmentPathVertical,
            avgHorizontal, avg3d, direct, delta);
        String line = hasPrev
            ? String.format("[时段 %d] %.1f 秒 / %.1f 格 / 水平平均 %.1f 格每秒（比上一段 %+.1f）",
                segmentCount, seconds, segmentPath3d, avgHorizontal, delta)
            : String.format("[时段 %d] %.1f 秒 / %.1f 格 / 水平平均 %.1f 格每秒",
                segmentCount, seconds, segmentPath3d, avgHorizontal);
        if (segmentChat) {
            FOElytraLog.info("%s", line);
        } else {
            FOElytraLog.detail("%s", line);
        }
        FOElytraLog.detail("[时段 %d] 三维平均 %.1f｜垂直 %.1f 格｜净位移 %.1f 格（绕路 %+.1f）",
            segmentCount, avg3d, segmentPathVertical, direct, segmentPath3d - direct);
        segmentPrevAverageHorizontal = avgHorizontal;
        segmentTicks = 0;
        segmentPath3d = segmentPathHorizontal = segmentPathVertical = 0;
        segmentFromX = x;
        segmentFromY = y;
        segmentFromZ = z;
    }
    public static void setSegmentSeconds(double seconds) {
        segmentTargetTicks = (int) Math.round(Math.max(5.0, Math.min(600.0, seconds)) * 20.0);
    }
    public static double segmentLengthSeconds() {
        return segmentTargetTicks / 20.0;
    }
    public static void setSegmentChat(boolean on) {
        segmentChat = on;
    }
    public static double segmentElapsedSeconds() {
        return segmentTicks / 20.0;
    }
    public static int segmentCount() {
        return segmentCount;
    }
    public static double segmentAverageHorizontal() {
        return segmentTicks <= 0 ? 0 : segmentPathHorizontal / (segmentTicks / 20.0);
    }
    public static double segmentAverage3d() {
        return segmentTicks <= 0 ? 0 : segmentPath3d / (segmentTicks / 20.0);
    }
    public static double segmentPath3d() {
        return segmentPath3d;
    }
    public static double segmentPathHorizontal() {
        return segmentPathHorizontal;
    }
    public static double segmentDirectDistance() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || segmentTicks <= 0) return 0;
        double dx = mc.player.getX() - segmentFromX;
        double dy = mc.player.getY() - segmentFromY;
        double dz = mc.player.getZ() - segmentFromZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    public static SegmentReport lastSegment() {
        return lastSegment;
    }
    public static double lastSegmentAverageHorizontal() {
        SegmentReport report = lastSegment;
        return report == null ? -1 : report.averageHorizontal();
    }
    public static double segmentRemainingSeconds() {
        return Math.max(0, segmentTargetTicks - segmentTicks) / 20.0;
    }
    public static String timeText(double seconds) {
        if (seconds < 0) return "-";
        int total = (int) Math.round(seconds);
        if (total < 60) return total + " 秒";
        int m = total / 60;
        int s = total % 60;
        if (m < 60) return m + " 分 " + s + " 秒";
        return (m / 60) + " 时 " + (m % 60) + " 分";
    }
    public static void setWindowSeconds(double seconds) {
        windowMs = (long) Math.max(500, Math.min(120_000, seconds * 1000));
    }
    public static double windowSeconds() {
        return windowMs / 1000.0;
    }
    private static double windowActualSeconds() {
        if (window.size() < 2) return Math.max(0.05, windowMs / 1000.0);
        long first = window.peekFirst().timeMs();
        long last = window.peekLast().timeMs();
        double s = (last - first) / 1000.0;
        return s <= 0.05 ? 0.05 : s;
    }
    public static double horizontalSpeed() {
        return winPathHorizontal / windowActualSeconds();
    }
    public static double verticalSpeed() {
        return winPathVertical / windowActualSeconds();
    }
    public static double threeDSpeed() {
        return winPath3d / windowActualSeconds();
    }
    public static double instantHorizontalSpeed() {
        return instantHorizontal;
    }
    public static double instantThreeDSpeed() {
        return instant3d;
    }
    public static double peakHorizontalSpeed() {
        return peakHorizontal;
    }
    public static double totalPath() {
        return totalPath3d;
    }
    public static double totalHorizontalPath() {
        return totalPathHorizontal;
    }
    public static double netDisplacement() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || !started) return 0;
        double dx = mc.player.getX() - netFromX;
        double dy = mc.player.getY() - netFromY;
        double dz = mc.player.getZ() - netFromZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    public static double elapsedSeconds() {
        if (!started || startMs == 0) return 0;
        return (System.currentTimeMillis() - startMs) / 1000.0;
    }
    public static double totalAverageSpeed() {
        double t = elapsedSeconds();
        return t <= 0 ? 0 : totalPathHorizontal / t;
    }
    public static double etaSeconds(double distance) {
        double v = horizontalSpeed();
        if (v < 0.1) return -1;
        return distance / v;
    }
    public static int sampleCount() {
        return sampleCount;
    }
    public static int teleportCount() {
        return teleportCount;
    }
    public static double convert(double blocksPerSecond, int unit) {
        return switch (unit) {
            case 1 -> blocksPerSecond * 3.6;
            case 2 -> blocksPerSecond / 20.0;
            default -> blocksPerSecond;
        };
    }
    public static String unitName(int unit) {
        return switch (unit) {
            case 1 -> "公里/小时";
            case 2 -> "格/刻";
            default -> "格/秒";
        };
    }
}
