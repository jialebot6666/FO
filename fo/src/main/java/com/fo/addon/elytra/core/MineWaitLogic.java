package com.fo.addon.elytra.core;

/**
 * 「交给 Baritone 挖方块 → 等方块进背包」的等待判定（纯逻辑，不依赖 MC 运行时，可单元测试）。
 *
 * <h2>为什么需要这个类（V5.1 修掉的误报）</h2>
 * Baritone 的 {@code mine()} 是一个<b>进程</b>：它要先寻路走到方块、把方块挖掉、再走过去把掉落物捡起来，
 * 整个过程 {@code isActive()} 一直是 {@code true}。旧代码把活交给它之后只等 40 tick（2 秒）就判定
 * 「挖掘异常」并把进程 {@code stop()} 掉 —— 而 2 秒通常连「走过去 + 挖掉」都不够，
 * 所以<b>每一次都会误报并打断挖掘</b>。
 *
 * <h2>正确的等待分两段</h2>
 * <ol>
 *   <li><b>方块还没消失</b>：Baritone 正常在挖，继续等（由外层超时兜底，不要插手中断）；</li>
 *   <li><b>方块已经消失</b>：挖掘这一步已经完成，剩下的只是「走过去捡掉落物」，
 *       再给一个拾取窗口就够了；窗口到了就主动停掉挖掘进程，避免 Baritone 为了凑数量跑去挖别的同类方块。</li>
 * </ol>
 */
public final class MineWaitLogic {
    /** 方块消失后，额外留给掉落物的拾取窗口（tick）。40 tick = 2 秒。 */
    public static final int PICKUP_WINDOW_TICKS = 40;

    /** 挖潜影盒的整体超时（tick）。120 tick = 6 秒。 */
    public static final int SHULKER_TIMEOUT_TICKS = 120;

    /** 挖末影箱的整体超时（tick）。200 tick = 10 秒（黑曜石很硬，要留够）。 */
    public static final int ENDER_CHEST_TIMEOUT_TICKS = 200;

    private MineWaitLogic() {
    }

    /** 「等方块进背包」的判定结果。 */
    public enum Outcome {
        /** 继续等。 */
        WAITING,
        /** 方块没了、东西也进背包了 —— 成功。 */
        COLLECTED,
        /** 等超时了，放弃。 */
        TIMED_OUT
    }

    /**
     * 评估当前该继续等、还是收工。
     *
     * <p>顺序很重要：<b>先判成功，再判超时</b>。否则「恰好在超时那一 tick 把东西捡起来」会被误判成失败。</p>
     *
     * @param blockGone    目标方块是否已经消失
     * @param gotItem      掉落物是否已经进背包
     * @param waitedTicks  已经等了多少 tick
     * @param timeoutTicks 超时阈值
     */
    public static Outcome evaluate(boolean blockGone, boolean gotItem, int waitedTicks, int timeoutTicks) {
        if (blockGone && gotItem) return Outcome.COLLECTED;
        if (waitedTicks > timeoutTicks) return Outcome.TIMED_OUT;
        return Outcome.WAITING;
    }

    /**
     * 方块消失之后，是否该停掉 Baritone 的挖掘进程。
     *
     * <p>掉落物还在往玩家这边飞的时候不要打断；给满 {@link #PICKUP_WINDOW_TICKS} 之后再停。</p>
     *
     * @param blockGone      目标方块是否已经消失
     * @param pickupWaitTicks 方块消失后已经等了多少 tick
     * @param windowTicks    拾取窗口长度
     * @param stillMining    Baritone 是否还在挖（已经停了就不用再停一次）
     */
    public static boolean shouldStopMining(boolean blockGone, int pickupWaitTicks, int windowTicks, boolean stillMining) {
        return blockGone && stillMining && pickupWaitTicks >= windowTicks;
    }

    /**
     * 是否算「方块还在、迟迟挖不掉」需要放弃。
     *
     * <p>只有<b>方块仍然存在</b>时才算异常；方块已经没了的情况由 {@link #shouldStopMining} 正常收尾，
     * 不应该报「挖掘异常」。</p>
     */
    public static boolean isStuck(boolean blockGone, int waitedTicks, int timeoutTicks) {
        return !blockGone && waitedTicks > timeoutTicks;
    }
}
