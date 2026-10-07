package com.fo.addon.utils;

/**
 * 拾取超时决策（纯逻辑，可单元测试）。
 *
 * <p>V4.39 对齐 misaka：200 tick 超时后，潜影盒是核心资产 → 直接断开连接防丢；
 * 工作台/末影箱超时 → 回挖矿重试；未超时 → 继续等待。
 *
 * <p>V4.54 对齐 misaka x0016（PICKING_UP_ITEM）：进入拾取即设 200 tick 倒计时，
 * 每 tick 递减（不看掉落物是否可见）；剩余 ≤ 0 → 超时处理。
 */
public final class PickupTimeoutLogic {

    /** 超时倒计时初值（tick）：200（对齐 misaka x0021=200） */
    public static final int MAX_TIMEOUT_TICKS = 200;

    public enum Action {
        /** 未超时，继续等待 */
        WAIT,
        /** 超时且非潜影盒：回挖矿重试 */
        RETRY,
        /** 超时且为潜影盒：断开连接 */
        DISCONNECT
    }

    private PickupTimeoutLogic() {}

    /**
     * 决策（倒计时语义，对齐 misaka x0016）：剩余 > 0 → WAIT；剩余 ≤ 0 → 超时。
     * 超时 + 潜影盒 → DISCONNECT；超时 + 其他 → RETRY。
     */
    public static Action decide(boolean isShulkerPickup, int remainingTicks) {
        if (remainingTicks > 0) return Action.WAIT;
        return isShulkerPickup ? Action.DISCONNECT : Action.RETRY;
    }
}
