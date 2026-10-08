package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

public final class SpeedTracker {
    private static final long MIN_GAP_MS = 20L;
    private static final double TELEPORT_JUMP = 32.0;
    private static final int MAX_SAMPLES = 4096;
    private static final Deque<Sample> window = new ArrayDeque<Sample>();
    private static long windowMs = 5000L;
    private static boolean started;
    private static long startMs;
    private static long lastMs;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static Object lastWorld;
    private static double totalPath3d;
    private static double totalPathHorizontal;
    private static double totalPathVertical;
    private static double netFromX;
    private static double netFromY;
    private static double netFromZ;
    private static int sampleCount;
    private static int teleportCount;
    private static double winPath3d;
    private static double winPathHorizontal;
    private static double winPathVertical;
    private static double peakHorizontal;
    private static double instantHorizontal;
    private static double instant3d;
    private static int segmentTicks;
    private static int segmentTargetTicks;
    private static int segmentCount;
    private static int segmentLastAge;
    private static double segmentPath3d;
    private static double segmentPathHorizontal;
    private static double segmentPathVertical;
    private static double segmentFromX;
    private static double segmentFromY;
    private static double segmentFromZ;
    private static double segmentPrevAverageHorizontal;
    private static boolean segmentChat;
    private static SegmentReport lastSegment;

    private SpeedTracker() {
    }

    public static boolean sample() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        ClientWorld world = mc.world;
        if (lastWorld != null && lastWorld != world) {
            SpeedTracker.reset();
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
        if (now - lastMs < 20L) {
            return false;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        double d3 = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double dh = Math.sqrt(dx * dx + dz * dz);
        double dv = Math.abs(dy);
        if (d3 > 32.0) {
            FOElytraLog.detail("\u68c0\u6d4b\u5230\u4f20\u9001/\u4f4d\u7f6e\u8df3\u53d8 %.1f \u683c\uff08\u7b2c %d \u6b21\uff09\uff0c\u901f\u5ea6\u7a97\u53e3\u5df2\u91cd\u7f6e", d3, ++teleportCount);
            SpeedTracker.clearWindow();
            lastMs = now;
            lastX = x;
            lastY = y;
            lastZ = z;
            return false;
        }
        double dtSec = (double)(now - lastMs) / 1000.0;
        if (dtSec > 0.0) {
            instantHorizontal = dh / dtSec;
            instant3d = d3 / dtSec;
            if (instantHorizontal > peakHorizontal) {
                peakHorizontal = instantHorizontal;
            }
        }
        totalPath3d += d3;
        totalPathHorizontal += dh;
        totalPathVertical += dv;
        winPath3d += d3;
        winPathHorizontal += dh;
        winPathVertical += dv;
        ++sampleCount;
        SpeedTracker.tickSegment(mc, d3, dh, dv, x, y, z);
        window.addLast(new Sample(now, x, y, z));
        SpeedTracker.trimWindow(now);
        lastMs = now;
        lastX = x;
        lastY = y;
        lastZ = z;
        return true;
    }

    private static void trimWindow(long now) {
        long cutoff = now - windowMs;
        while (window.size() > 2) {
            double dz;
            double dy;
            Sample first = window.peekFirst();
            Sample second = null;
            Iterator<Sample> it = window.iterator();
            it.next();
            if (it.hasNext()) {
                second = it.next();
            }
            if (first == null || second == null || second.timeMs() >= cutoff) break;
            double dx = second.x() - first.x();
            double d3 = Math.sqrt(dx * dx + (dy = second.y() - first.y()) * dy + (dz = second.z() - first.z()) * dz);
            if (d3 <= 32.0) {
                winPath3d -= d3;
                winPathHorizontal -= Math.sqrt(dx * dx + dz * dz);
                winPathVertical -= Math.abs(dy);
            }
            window.removeFirst();
        }
        while (window.size() > 4096) {
            window.removeFirst();
        }
    }

    private static void clearWindow() {
        window.clear();
        winPathVertical = 0.0;
        winPathHorizontal = 0.0;
        winPath3d = 0.0;
        instant3d = 0.0;
        instantHorizontal = 0.0;
    }

    public static void reset() {
        SpeedTracker.clearWindow();
        started = false;
        startMs = 0L;
        lastMs = 0L;
        totalPathVertical = 0.0;
        totalPathHorizontal = 0.0;
        totalPath3d = 0.0;
        sampleCount = 0;
        teleportCount = 0;
        peakHorizontal = 0.0;
        netFromZ = 0.0;
        netFromY = 0.0;
        netFromX = 0.0;
        segmentTicks = 0;
        segmentCount = 0;
        segmentLastAge = -1;
        segmentPathVertical = 0.0;
        segmentPathHorizontal = 0.0;
        segmentPath3d = 0.0;
        segmentFromZ = 0.0;
        segmentFromY = 0.0;
        segmentFromX = 0.0;
        segmentPrevAverageHorizontal = -1.0;
        lastSegment = null;
    }

    private static void tickSegment(MinecraftClient mc, double d3, double dh, double dv, double x, double y, double z) {
        int age = mc.player.age;
        boolean newTick = age != segmentLastAge;
        segmentLastAge = age;
        if (!newTick) {
            return;
        }
        segmentPath3d += d3;
        segmentPathHorizontal += dh;
        segmentPathVertical += dv;
        if (++segmentTicks >= segmentTargetTicks) {
            SpeedTracker.settleSegment(x, y, z);
        }
    }

    private static void settleSegment(double x, double y, double z) {
        String line;
        double seconds = (double)segmentTicks / 20.0;
        double avgHorizontal = seconds <= 0.0 ? 0.0 : segmentPathHorizontal / seconds;
        double avg3d = seconds <= 0.0 ? 0.0 : segmentPath3d / seconds;
        double dx = x - segmentFromX;
        double dy = y - segmentFromY;
        double dz = z - segmentFromZ;
        double direct = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean hasPrev = segmentPrevAverageHorizontal >= 0.0;
        double delta = hasPrev ? avgHorizontal - segmentPrevAverageHorizontal : 0.0;
        lastSegment = new SegmentReport(++segmentCount, seconds, segmentPath3d, segmentPathHorizontal, segmentPathVertical, avgHorizontal, avg3d, direct, delta);
        String string = line = hasPrev ? String.format("[\u65f6\u6bb5 %d] %.1f \u79d2 / %.1f \u683c / \u6c34\u5e73\u5e73\u5747 %.1f \u683c\u6bcf\u79d2\uff08\u6bd4\u4e0a\u4e00\u6bb5 %+.1f\uff09", segmentCount, seconds, segmentPath3d, avgHorizontal, delta) : String.format("[\u65f6\u6bb5 %d] %.1f \u79d2 / %.1f \u683c / \u6c34\u5e73\u5e73\u5747 %.1f \u683c\u6bcf\u79d2", segmentCount, seconds, segmentPath3d, avgHorizontal);
        if (segmentChat) {
            FOElytraLog.info("%s", line);
        } else {
            FOElytraLog.detail("%s", line);
        }
        FOElytraLog.detail("[\u65f6\u6bb5 %d] \u4e09\u7ef4\u5e73\u5747 %.1f\uff5c\u5782\u76f4 %.1f \u683c\uff5c\u51c0\u4f4d\u79fb %.1f \u683c\uff08\u7ed5\u8def %+.1f\uff09", segmentCount, avg3d, segmentPathVertical, direct, segmentPath3d - direct);
        segmentPrevAverageHorizontal = avgHorizontal;
        segmentTicks = 0;
        segmentPathVertical = 0.0;
        segmentPathHorizontal = 0.0;
        segmentPath3d = 0.0;
        segmentFromX = x;
        segmentFromY = y;
        segmentFromZ = z;
    }

    public static void setSegmentSeconds(double seconds) {
        segmentTargetTicks = (int)Math.round(Math.max(5.0, Math.min(600.0, seconds)) * 20.0);
    }

    public static double segmentLengthSeconds() {
        return (double)segmentTargetTicks / 20.0;
    }

    public static void setSegmentChat(boolean on) {
        segmentChat = on;
    }

    public static double segmentElapsedSeconds() {
        return (double)segmentTicks / 20.0;
    }

    public static int segmentCount() {
        return segmentCount;
    }

    public static double segmentAverageHorizontal() {
        return segmentTicks <= 0 ? 0.0 : segmentPathHorizontal / ((double)segmentTicks / 20.0);
    }

    public static double segmentAverage3d() {
        return segmentTicks <= 0 ? 0.0 : segmentPath3d / ((double)segmentTicks / 20.0);
    }

    public static double segmentPath3d() {
        return segmentPath3d;
    }

    public static double segmentPathHorizontal() {
        return segmentPathHorizontal;
    }

    public static double segmentDirectDistance() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || segmentTicks <= 0) {
            return 0.0;
        }
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
        return report == null ? -1.0 : report.averageHorizontal();
    }

    public static double segmentRemainingSeconds() {
        return (double)Math.max(0, segmentTargetTicks - segmentTicks) / 20.0;
    }

    public static String timeText(double seconds) {
        if (seconds < 0.0) {
            return "-";
        }
        int total = (int)Math.round(seconds);
        if (total < 60) {
            return total + " \u79d2";
        }
        int m = total / 60;
        int s = total % 60;
        if (m < 60) {
            return m + " \u5206 " + s + " \u79d2";
        }
        return m / 60 + " \u65f6 " + m % 60 + " \u5206";
    }

    public static void setWindowSeconds(double seconds) {
        windowMs = (long)Math.max(500.0, Math.min(120000.0, seconds * 1000.0));
    }

    public static double windowSeconds() {
        return (double)windowMs / 1000.0;
    }

    private static double windowActualSeconds() {
        if (window.size() < 2) {
            return Math.max(0.05, (double)windowMs / 1000.0);
        }
        long first = window.peekFirst().timeMs();
        long last = window.peekLast().timeMs();
        double s = (double)(last - first) / 1000.0;
        return s <= 0.05 ? 0.05 : s;
    }

    public static double horizontalSpeed() {
        return winPathHorizontal / SpeedTracker.windowActualSeconds();
    }

    public static double verticalSpeed() {
        return winPathVertical / SpeedTracker.windowActualSeconds();
    }

    public static double threeDSpeed() {
        return winPath3d / SpeedTracker.windowActualSeconds();
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
        if (mc == null || mc.player == null || !started) {
            return 0.0;
        }
        double dx = mc.player.getX() - netFromX;
        double dy = mc.player.getY() - netFromY;
        double dz = mc.player.getZ() - netFromZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double elapsedSeconds() {
        if (!started || startMs == 0L) {
            return 0.0;
        }
        return (double)(System.currentTimeMillis() - startMs) / 1000.0;
    }

    public static double totalAverageSpeed() {
        double t = SpeedTracker.elapsedSeconds();
        return t <= 0.0 ? 0.0 : totalPathHorizontal / t;
    }

    public static double etaSeconds(double distance) {
        double v = SpeedTracker.horizontalSpeed();
        if (v < 0.1) {
            return -1.0;
        }
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
            case 1 -> "km/h";
            case 2 -> "\u683c/tick";
            default -> "\u683c/\u79d2";
        };
    }

    static {
        segmentTargetTicks = 600;
        segmentLastAge = -1;
        segmentPrevAverageHorizontal = -1.0;
        segmentChat = true;
    }

    private record Sample(long timeMs, double x, double y, double z) {
    }

    public record SegmentReport(int index, double seconds, double path3d, double pathHorizontal, double pathVertical, double averageHorizontal, double average3d, double directDistance, double deltaHorizontal) {
    }
}

