package com.fo.addon.ancient.core;

/**
 * 飞行寻路的纯数学部分（可单测）：高度分层、航段检查点、横向偏差、冲过头、终端螺旋。
 *
 * <p>交互与时序留在模块类；这里只算「该不该切层 / 该转多少度 / 该用多大半径」，
 * 方便在没有 Minecraft 运行时的环境里跑断言。
 */
public final class FlightNavLogic {

    /** 四层高度。 */
    public enum Layer {
        /** 爬升到提升高度。 */
        CLIMB,
        /** 巡航层。 */
        CRUISE,
        /** 接近层：目标地表 + approachAbove。 */
        APPROACH,
        /** 终端层：目标地表 + terminalAbove → 螺旋落地。 */
        TERMINAL
    }

    private FlightNavLogic() {
    }

    /**
     * 距离驱动的分层（只按水平距离判断，调用方负责记住「是否已经进过接近/终端」）。
     *
     * @param horizontalDistance 距目标的水平距离
     * @param approachDistance   接近层触发距离
     * @param terminalDistance   终端层触发距离
     */
    public static Layer layerByDistance(double horizontalDistance, double approachDistance,
                                        double terminalDistance) {
        if (horizontalDistance <= Math.max(1, terminalDistance)) return Layer.TERMINAL;
        if (horizontalDistance <= Math.max(1, approachDistance)) return Layer.APPROACH;
        return Layer.CRUISE;
    }

    /** 是否该插一个航向校正点：剩余距离还够一个间隔。 */
    public static boolean needCheckpoint(double remaining, int interval) {
        return interval > 0 && remaining > interval;
    }

    /** 到航线的垂直距离（横向偏差），航线是 A→B 的直线。 */
    public static double crossTrackError(double px, double pz, double ax, double az,
                                         double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            double ex = px - ax;
            double ez = pz - az;
            return Math.sqrt(ex * ex + ez * ez);
        }
        // 叉积的绝对值 = 平行四边形面积，除底边长度 = 高
        double cross = dx * (pz - az) - dz * (px - ax);
        return Math.abs(cross) / len;
    }

    /** 冲过头：距离连续这么多次变大。 */
    public static boolean overshoot(int growStreak) {
        return growStreak >= 20 * 3; // 3 秒
    }

    /** 巡航俯仰：低于下限抬头爬，高于上限低头滑，区间内保持小俯角前进。 */
    public static float cruisePitch(double y, double minY, double maxY) {
        if (y < minY) return -54.77f;
        if (y > maxY) return 30.0f;
        return 37.72f;
    }

    /**
     * 终端螺旋的目标朝向：沿「圆心 → 自己」的切向（+90°），绕圈下降。
     */
    public static float spiralYaw(double centerX, double centerZ, double px, double pz) {
        double dx = px - centerX;
        double dz = pz - centerZ;
        // 切向 = 径向逆时针转 90°：(dx,dz) -> (dz,-dx)
        double tx = dz;
        double tz = -dx;
        return (float) Math.toDegrees(Math.atan2(-tx, tz));
    }

    /** 当前应在的螺旋半径：每转半圈缩 step，最小 minRadius。 */
    public static double spiralRadius(double startRadius, double minRadius, int halfTurns, double step) {
        double r = startRadius - Math.max(0, halfTurns) * Math.max(0, step);
        return Math.max(minRadius, r);
    }

    /** 螺旋俯仰：离地越高俯角越大，最后几格转平缓。 */
    public static float spiralPitch(double heightAboveGround) {
        double dy = heightAboveGround - 6.0;
        double pitch = dy * 1.2;
        double clamped = Math.min(55.0, Math.max(5.0, pitch));
        if (heightAboveGround <= 6.0) return 10.0f;
        if (heightAboveGround <= 15.0) return (float) Math.min(20.0, clamped);
        return (float) clamped;
    }

    /** 起飞前烟花预算：距离/40 + 爬到 300 约 12 发。 */
    public static int fireworkBudget(double distance) {
        return (int) Math.round(Math.max(1, distance) / 40.0) + 12;
    }

    /** 水平速度换算成「够不够快」，用于判断是否补烟花。 */
    public static boolean needsThrust(double horizontalSpeed, double minSpeed) {
        return horizontalSpeed < minSpeed;
    }

    /** 视角每 tick 的最大步进（带符号，最短方向）。 */
    public static float stepToward(float current, float target, float maxStep) {
        float diff = ((target - current + 540.0f) % 360.0f) - 180.0f;
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }
}
