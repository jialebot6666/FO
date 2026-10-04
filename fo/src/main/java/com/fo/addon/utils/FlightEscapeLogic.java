package com.fo.addon.utils;

/**
 * 对齐 Ying ElytraSafeEscape：落地恢复安全复飞三段式——
 * 阶段0 水平背离末地城 90 格 → 阶段1 船外爬升到降落点上方 30 格 → 阶段2 重新接近 (≤28 格且高度足够 → 转降落)。
 * 纯计算 + 状态类，不依赖 MC 运行时，可单元测试。
 */
public class FlightEscapeLogic {
    /** 背离距离：水平离开船体 90 格后才开始爬升 */
    public static final double ESCAPE_DISTANCE = 90.0;
    /** 重新接近交接距离：距降落点 ≤28 格 */
    public static final double REAPPROACH_DISTANCE = 28.0;
    /** 爬升目标高度 = 降落点 Y + 30 */
    public static final int SAFE_HEIGHT_ABOVE_LANDING = 30;
    /** 重新接近的最低高度 = 降落点 Y + 10 */
    public static final int MIN_REAPPROACH_HEIGHT_ABOVE_LANDING = 10;
    /** 接近中高度不足时的背离触发距离 (>35 格且高度不足才再次背离) */
    public static final double RETREAT_DISTANCE = 35.0;

    public enum Stage {
        /** 水平背离 */
        ESCAPE(0),
        /** 船外爬升 */
        CLIMB(1),
        /** 重新接近 */
        REAPPROACH(2);

        public final int id;
        Stage(int id) { this.id = id; }

        public static Stage fromId(int id) {
            return switch (id) {
                case 1 -> CLIMB;
                case 2 -> REAPPROACH;
                default -> ESCAPE;
            };
        }
    }

    /** 安全复飞状态（对齐 ElytraSafeEscape 的 cr/bd/bc 字段组） */
    public static class State {
        public Stage stage = Stage.ESCAPE;
        public int ticks = 0;
        public float awayYaw = 0f;
    }

    /** 角度归一化到 [-180, 180) */
    public static float normalizeYaw(float yaw) {
        yaw %= 360f;
        if (yaw >= 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        return yaw;
    }

    /** 背离方向 = 朝向降落点的方向 + 180（背对降落点） */
    public static float escapeYaw(float yawToTarget) {
        return normalizeYaw(yawToTarget + 180f);
    }

    /** 阶段0 → 阶段1：水平离开 ≥90 格 */
    public static boolean shouldStartClimb(double hDist) {
        return hDist >= ESCAPE_DISTANCE;
    }

    /** 阶段1 → 阶段2：已爬升到降落点上方 30 格 */
    public static boolean shouldStartReapproach(double playerY, double landingY) {
        return playerY >= landingY + SAFE_HEIGHT_ABOVE_LANDING;
    }

    /** 阶段2 高度不足且距离>35 → 回阶段0 再次背离 */
    public static boolean shouldRetreat(double playerY, double landingY, double hDist) {
        return playerY < landingY + MIN_REAPPROACH_HEIGHT_ABOVE_LANDING && hDist > RETREAT_DISTANCE;
    }

    /** 阶段2 完成：距≤28 且高度≥降落点+10 → 转降落 */
    public static boolean shouldHandoffToLanding(double playerY, double landingY, double hDist) {
        return hDist <= REAPPROACH_DISTANCE && playerY >= landingY + MIN_REAPPROACH_HEIGHT_ABOVE_LANDING;
    }

    /** 阶段0 烟花节奏：ticks≥60 且每 max(烟花间隔, 40) tick 一次 */
    public static boolean escapeFireworkDue(int ticks, int fireworkIntervalTicks) {
        return ticks >= 60 && ticks % Math.max(fireworkIntervalTicks, 40) == 0;
    }

    /** 阶段1 烟花节奏：首 tick 或每间隔 tick */
    public static boolean climbFireworkDue(int ticks, int fireworkIntervalTicks) {
        return ticks == 1 || ticks % fireworkIntervalTicks == 0;
    }

    /** 烟花间隔换算成 tick（秒 × 20，至少 1） */
    public static int fireworkIntervalTicks(double intervalSec) {
        return Math.max(1, (int) Math.round(intervalSec * 20.0));
    }
}
