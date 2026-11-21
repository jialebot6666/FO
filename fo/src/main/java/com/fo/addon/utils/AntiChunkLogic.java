package com.fo.addon.utils;

/**
 * 防卡区块爬升判定（内嵌自 Ying 0.7.2 Pitch40Fly 的纯逻辑部分，便于单测）。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code ahead} = 面向方向上前方最近未加载区块的距离（格），{@code -1} 表示前方全部已加载；</li>
 *   <li>触发拉升：{@code ahead >= 0 && ahead <= lookahead}；</li>
 *   <li>结束拉升：前方足够远（{@code ahead < 0 || ahead > lookahead + resumeMargin}）且连续
 *       {@code clearStreak >= 3} tick 满足、并达到 {@code minClimbTicks} 最短时长。</li>
 * </ul>
 */
public final class AntiChunkLogic {
    private AntiChunkLogic() {}

    /** 前方最近未加载区块是否近到需要触发拉升 */
    public static boolean shouldTrigger(double ahead, int lookahead) {
        return ahead >= 0 && ahead <= lookahead;
    }

    /** 前方是否已经足够安全（可以结束拉升） */
    public static boolean isClear(double ahead, int lookahead, int resumeMargin) {
        return ahead < 0 || ahead > lookahead + resumeMargin;
    }

    /** 爬升是否可以结束：达到最短 tick 且连续安全 tick 数 >= 3 */
    public static boolean canEndClimb(int climbTicks, int clearStreak, int minClimbTicks) {
        return climbTicks >= minClimbTicks && clearStreak >= 3;
    }

    /** 角速度限幅逼近目标角（不会绕圈） */
    public static float stepToward(float current, float target, float speed) {
        float d = target - current;
        if (Math.abs(d) <= speed) return target;
        return current + Math.signum(d) * speed;
    }
}
