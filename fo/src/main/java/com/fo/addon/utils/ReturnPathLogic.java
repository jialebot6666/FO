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
}
