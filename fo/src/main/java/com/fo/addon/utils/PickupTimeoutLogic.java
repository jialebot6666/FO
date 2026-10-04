package com.fo.addon.utils;

/**
 * 拾取超时决策（纯逻辑，可单元测试）。
 *
 * <p>V4.39 对齐 misaka：200 tick 超时后，潜影盒是核心资产 → 直接断开连接防丢；
 * 工作台/末影箱超时 → 回挖矿重试；未超时 → 继续等待。
 */
public final class PickupTimeoutLogic {

    /** 超时阈值（tick）：200 */
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

    /** 决策：超时 + 潜影盒 → DISCONNECT；超时 + 其他 → RETRY；未超时 → WAIT */
    public static Action decide(boolean isShulkerPickup, int timeoutTicks) {
        if (timeoutTicks <= MAX_TIMEOUT_TICKS) return Action.WAIT;
        return isShulkerPickup ? Action.DISCONNECT : Action.RETRY;
    }
}
