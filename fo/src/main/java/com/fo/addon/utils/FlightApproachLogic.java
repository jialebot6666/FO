package com.fo.addon.utils;

/**
 * 对齐 Ying ElytraApproachSafety22：末地城接近防撞塔——动态预测滑翔轨迹，
 * 预测会撞（到达时高度低于安全高度）就朝船直接拉升，到安全高度后恢复接近。
 * 纯计算 + 状态类，不依赖 MC 运行时，可单元测试。
 */
public class FlightApproachLogic {
    /** 介入距离：距降落点 ≤200 格才开始预测 */
    public static final double TRIGGER_DISTANCE = 200.0;
    /** 降落交接距离：距降落点 ≤80 格且已确认鞘翅 → 转 LANDING */
    public static final double LANDING_HANDOFF_DISTANCE = 80.0;
    /** 安全高度 = 降落点 Y + 30 */
    public static final double SAFE_MARGIN_ABOVE_TARGET = 30.0;
    /** 可直接接近的余量：预测高度 ≥ 安全高度 + 8 才算安全 */
    public static final double SAFE_EXTRA_ON_RETURN = 8.0;

    public enum Mode {
        /** 正常接近（从船上方滑翔过去） */
        NORMAL(0),
        /** 朝船直接拉升（预测会撞） */
        CLIMB(1),
        /** 已到安全高度，恢复接近 */
        REAPPROACH(2);

        public final int id;
        Mode(int id) { this.id = id; }

        public static Mode fromId(int id) {
            return switch (id) {
                case 1 -> CLIMB;
                case 2 -> REAPPROACH;
                default -> NORMAL;
            };
        }
    }

    /** 防撞塔会话状态（对齐 ElytraApproachSafety22.Data） */
    public static class State {
        public String targetKey = null;   // 当前目标 (降落点坐标键)，目标变化时重置
        public Mode mode = Mode.NORMAL;
        public int ticks = 0;
        public boolean announced = false; // 是否已完成首次介入决策
        public float awayYaw = 0f;        // 拉升时保持的朝向
        public double safeY = 0;          // 安全高度 (降落点Y+30)
        public double predictedY = 0;     // 预测到达高度
    }

    /** 接近俯冲角：|滑翔角| 夹到 [3, 12] 度（对齐 Ying d10） */
    public static double approachPitch(double glideAngleDeg) {
        return Math.max(3.0, Math.min(12.0, Math.abs(glideAngleDeg)));
    }

    /** 预测到达高度：playerY - 水平距离 × tan(接近俯冲角) */
    public static double predictedY(double playerY, double hDist, double glideAngleDeg) {
        return playerY - hDist * Math.tan(Math.toRadians(approachPitch(glideAngleDeg)));
    }

    /** 安全高度：降落点 Y + 30 */
    public static double safeY(double landingY) {
        return landingY + SAFE_MARGIN_ABOVE_TARGET;
    }

    /** 拉升目标高度：安全高度 + max(35, min(90, 水平距离×tan(俯冲角) + 12))（对齐 Ying d14） */
    public static double neededY(double hDist, double glideAngleDeg, double landingY) {
        double tanTerm = hDist * Math.tan(Math.toRadians(approachPitch(glideAngleDeg))) + 12.0;
        return safeY(landingY) + Math.max(35.0, Math.min(90.0, tanTerm));
    }

    /** 首次介入决策：预测高度 ≥ 安全高度+8 → 正常接近，否则拉升（对齐 Ying） */
    public static Mode decideMode(double predictedY, double safeY) {
        return predictedY >= safeY + SAFE_EXTRA_ON_RETURN ? Mode.NORMAL : Mode.CLIMB;
    }

    /** 距离与状态是否应介入：距>200 且正常接近态时不介入（对齐 Ying 两个 return false） */
    public static boolean shouldIntervene(double hDist, Mode mode) {
        return hDist <= TRIGGER_DISTANCE || mode != Mode.NORMAL;
    }

    /** 接近中预测高度不足（Y<安全+3 且距离>95）→ 再次拉升（对齐 Ying 行154-160） */
    public static boolean shouldClimbAgain(double predictedY, double safeY, double hDist) {
        return predictedY < safeY + 3.0 && hDist > 95.0;
    }

    /** 距≤80、已确认展示框、框里有鞘翅 → 交接降落（对齐 Ying LANDING_HANDOFF_DISTANCE 分支） */
    public static boolean shouldHandoffToLanding(double hDist, boolean frameChecked, boolean frameEmpty) {
        return hDist <= LANDING_HANDOFF_DISTANCE && frameChecked && !frameEmpty;
    }
}
