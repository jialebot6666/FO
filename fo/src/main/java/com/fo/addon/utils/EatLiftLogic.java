package com.fo.addon.utils;

/**
 * 进食拉升的三段状态机（纯逻辑，可单测）。
 *
 * <p>移植自 eat-lift（作者 dev.hyh，Fabric 1.21.11）的 {@code ClientTickMixin} 状态机，
 * 语义保持：
 * <ul>
 *   <li>只有「从没吃 → 开始吃」的上升沿才触发，延迟结束后按下，按住设定时长再释放；</li>
 *   <li>按住期间即使已经吃完，也按满时长才释放；</li>
 *   <li>释放时如果还在吃，记下抑制位，同一段进食不会立刻二次触发；</li>
 *   <li>等待期间不吃了就回空闲，抑制位不动；</li>
 *   <li>门禁（例如鞘翅采集没开）关闭时立即释放。</li>
 * </ul>
 */
public final class EatLiftLogic {

    public enum Phase {
        IDLE,
        WAITING,
        HOLDING
    }

    public enum Action {
        NONE,
        PRESS,
        RELEASE
    }

    private Phase phase = Phase.IDLE;
    private int remaining;
    private boolean suppressed;

    /**
     * 每 tick 调用一次。
     *
     * @param eating     当前是否正在吃东西
     * @param gateOpen   门禁是否放行
     * @param delayTicks 开始吃之后等多少 tick 才按
     * @param holdTicks  按住多少 tick
     * @return 本 tick 需要执行的动作
     */
    public Action tick(boolean eating, boolean gateOpen, int delayTicks, int holdTicks) {
        if (!gateOpen) {
            boolean wasHolding = phase == Phase.HOLDING;
            reset();
            suppressed = eating;
            return wasHolding ? Action.RELEASE : Action.NONE;
        }

        switch (phase) {
            case IDLE:
                if (!eating) {
                    suppressed = false;
                    return Action.NONE;
                }
                if (suppressed) {
                    return Action.NONE;
                }
                phase = Phase.WAITING;
                remaining = Math.max(0, delayTicks);
                return Action.NONE;

            case WAITING:
                if (!eating) {
                    reset();
                    return Action.NONE;
                }
                remaining--;
                if (remaining <= 0) {
                    phase = Phase.HOLDING;
                    remaining = Math.max(0, holdTicks);
                    return Action.PRESS;
                }
                return Action.NONE;

            case HOLDING:
                remaining--;
                if (remaining <= 0) {
                    phase = Phase.IDLE;
                    suppressed = eating;
                    return Action.RELEASE;
                }
                return Action.NONE;

            default:
                reset();
                return Action.NONE;
        }
    }

    public Phase phase() {
        return phase;
    }

    public boolean suppressed() {
        return suppressed;
    }

    public void reset() {
        phase = Phase.IDLE;
        remaining = 0;
    }
}
