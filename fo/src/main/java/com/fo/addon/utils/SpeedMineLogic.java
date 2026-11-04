package com.fo.addon.utils;

/**
 * FO 发包挖掘——纯逻辑（可单测，不依赖 Minecraft 运行时状态）。
 *
 * <p>移植自 Hachimi V2 SpeedMine（源码类 {@code BlockPosBoxMultitask}，1083 行）的
 * 可确认行为：挖掘进度判定与渲染颜色渐变。挖掘动作本体走 Meteor 客户端线程直接发包。</p>
 */
public final class SpeedMineLogic {
    private SpeedMineLogic() {
    }

    /**
     * 判定本 tick 结束后是否应该发包破坏：
     * Speed 语义与原版一致——进度阈值，挖到 vanilla 进度的 Speed 倍（默认 0.7）即发包破坏。
     *
     * @param progress 已累计的破坏进度（0~1）
     * @param delta    本 tick 的 vanilla 破坏增量（{@code calcBlockBreakingDelta}）
     * @param speed    进度阈值（Speed 设置）
     * @return true = 应立即发包破坏
     */
    public static boolean shouldBreak(float progress, float delta, float speed) {
        if (delta <= 0) return false;
        // delta >= 1 = 徒手/工具一击即碎：vanilla 本来就瞬间破坏，直接发
        if (delta >= 1.0f) return true;
        return progress + delta >= speed;
    }

    /** 累计进度，封顶 1.0。 */
    public static float accumulate(float progress, float delta) {
        return Math.min(1.0f, progress + Math.max(0, delta));
    }

    /**
     * 会话冷却判定（上游 alP()）：NewDisabler/时间校验模式下，
     * 距离上次发 STOP 未满冷却时长时不开新会话。
     */
    public static boolean inSessionCooldown(long now, long lastStopTime, long cooldownMs) {
        return now - lastStopTime <= cooldownMs;
    }

    /**
     * 状态旗标修正（上游 CheckGround/CheckFly 语义）。
     *
     * <p>1.21.11 原版对离地挖掘一律 /5 惩罚（反汇编确认，无飞行豁免）。上游语义：
     * {@code CheckGround=false} 表示无视空中惩罚（默认，实战值）；
     * {@code CheckFly=true} 表示鞘翅滑翔时补上惩罚（实战值）。</p>
     *
     * @param vanillaDelta 原版实时计算的破坏增量（已含真实状态惩罚）
     * @param onGround     玩家是否在地面
     * @param gliding      玩家是否鞘翅滑翔
     * @param checkGround  地面检测设置
     * @param checkFly     飞行检测设置
     * @return 修正后的破坏增量
     */
    public static float applyStateFlags(float vanillaDelta, boolean onGround, boolean gliding,
                                        boolean checkGround, boolean checkFly) {
        boolean penalty = (!onGround && checkGround) || (gliding && checkFly);
        if (!onGround && !penalty) {
            // 还原被原版扣掉的空中惩罚（无视空中惩罚 = 按在地面算）
            return vanillaDelta * 5f;
        }
        return vanillaDelta;
    }

    /**
     * 时间校验模式的 STOP 放行判定（上游 xt() 的 FX 分支）：
     * 期望挖掘耗时（ceil(1/最大单tick进度)*50ms）与实际已耗时之差小于放行余量才发 STOP。
     */
    public static boolean timeModeStopAllowed(double expectedMs, double elapsedMs, double slackMs) {
        return expectedMs - elapsedMs < slackMs;
    }

    /**
     * 渲染颜色渐变：start（刚开始挖，红）→ end（即将破坏，绿），
     * 对齐原版 StartSide k(255,0) / EndSide k(0,255) 的语义。
     *
     * @return {r, g, b, a}
     */
    public static int[] lerpColor(int[] start, int[] end, double progress) {
        double t = Math.max(0, Math.min(1, progress));
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = (int) Math.round(start[i] + (end[i] - start[i]) * t);
        }
        return out;
    }
}
