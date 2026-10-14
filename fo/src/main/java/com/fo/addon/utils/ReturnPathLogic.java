package com.fo.addon.utils;

/**
 * 采集完成后"返回起飞点"的寻路判定（对齐 Ying StorageReturn 语义）。
 * 纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class ReturnPathLogic {
    /** 与 Ying StorageReturn.REPATH_INTERVAL 一致：每 20 tick 重发一次寻路 */
    public static final int REPATH_INTERVAL = 20;
    /** 与 Ying StorageReturn.TIMEOUT_TICKS 一致：30 秒（600 tick）回不到起飞点即超时 */
    public static final int TIMEOUT_TICKS = 600;

    private ReturnPathLogic() {
    }

    /**
     * 是否需要重新发起寻路：
     * - 距离上次重发超过 REPATH_INTERVAL tick → 重发（保持目标持续有效）
     * - 当前没有在寻路（上一次寻路失败/已结束）→ 重发（Ying: !isPathing 时也重发）
     */
    public static boolean shouldRepath(int stateTick, int lastGotoTick, boolean isPathing) {
        return stateTick - lastGotoTick >= REPATH_INTERVAL || !isPathing;
    }

    /**
     * 是否超过"返回起飞点"时限（600 tick = 30 秒）。
     * 超时后对齐 Ying StorageReturn：宁可停止任务，也不原地起飞撞墙。
     */
    public static boolean timedOut(int stateTick) {
        return stateTick > TIMEOUT_TICKS;
    }

    /** 「起飞前先回降落点」的默认判定距离（格）：与 Ying `leaveShipNow` 的 2.5 一致。 */
    public static final double DEFAULT_RETURN_DISTANCE = 2.5;

    /**
     * 起飞前是否该先回降落点（防原地起飞撞墙）：
     * 开关已开 + 已落地 + 有目标点 + 离降落点距离 **大于** 阈值。
     *
     * <p>⚠️ 背景：Ying 原版只在「跳过没鞘翅的船」路径有这个判定，**采集完成后是直接起飞的**；
     * FO 在 4.45 把判定也加到了采集完成路径，用于修「采集完立马起飞撞墙死掉」。所以关掉开关
     * 等于回到 Ying 采集完成路径的原行为（有撞墙风险）。
     *
     * @param switchOn          「起飞前先回降落点」开关
     * @param onGround          玩家是否在地面（空中本来就不需要走回去）
     * @param hasWaypoints      是否有当前船的目标点（起飞前触发的存储会话可能还没有船）
     * @param distanceToLanding 到降落点的水平距离（{@code hasWaypoints=false} 时不参与判定）
     * @param threshold         判定距离（格）
     */
    public static boolean shouldReturnBeforeTakeoff(boolean switchOn, boolean onGround,
                                                    boolean hasWaypoints, double distanceToLanding,
                                                    double threshold) {
        if (!switchOn) return false;
        if (!onGround) return false;
        if (!hasWaypoints) return false;
        return distanceToLanding > threshold;
    }
}
