package com.fo.addon.utils;

import java.util.List;

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
     * 上游 {@code xs()} 的进度累加（BlockPosPlayerHelper L239-240）：
     * 本 tick 的 vanilla 增量按 Speed 缩放后累加，封顶 1.0。
     *
     * <p>Speed 语义 = 只挖原版耗时的 Speed 倍（0.7 → 七成时间进度即满）。
     * 进度归一到 1.0 是 FO 的表示法（渲染直接把 progress 当 0~1 用），
     * 上游是「raw 累加 + progress ≥ Speed 判定」，两者等价。</p>
     */
    public static float nextProgress(float progress, float delta, float speedScale) {
        return Math.min(1.0f, progress + delta / speedScale);
    }

    /**
     * 上游 {@code xs()} 尾部的「理想进度上限」（BlockPosPlayerHelper L259）：
     * {@code 理想单tick进度 / Speed * 已进行tick数}。
     *
     * <p>⚠️ <b>除数必须是 Speed</b>。6.8p10 漏了它，累加值除以 Speed 涨、上限不除 Speed，
     * 于是进度恒被压回原版进度 —— 「挖掘进度」调 0.7 与调 1.0 行为完全一致，提速根本没生效（6.8p11 修复）。</p>
     *
     * <p>与上游的第二点差异（刻意保留）：上游这一项用「无视地面/飞行/水中惩罚的理想进度」
     * （{@code oQ(..., true, false, false)}），FO 用会话内实际进度用过的最大值
     * {@code maxDelta}（即上游的 {@code p}）。差别只在挖到一半状态变好时：
     * FO 的上限更紧 → STOP 偏晚不偏早，方向保守，不会因此被 Grim 判超速。</p>
     */
    public static float idealProgressBound(float idealDelta, int ticks, float speedScale) {
        return idealDelta * ticks / speedScale;
    }

    /**
     * 理想钳制是否生效（上游 L260 的 {@code if (f3 < d || o)}）：
     * 上限低于当前进度，或本会话是挖空气会话（空气期不攒进度，落回上限折算）。
     */
    public static boolean clampToIdealBound(float idealBound, float progress, boolean miningAir) {
        return idealBound < progress || miningAir;
    }

    /**
     * 保持换持是否该继续持着工具（上游 {@code xB}，BlockPosPlayerHelper L542-555）。
     *
     * <p>上游语义：{@code HoldTicks} 是<b>最短</b>持握时长，不是上限——
     * ① 主会话已经没了（方块挖完、会话被清）直接切回；② 否则「方块还在」或「持握未满 HoldTicks」
     * 二者成立其一就继续持着，且目标仍可挖、仍在范围内。</p>
     *
     * <p>⚠️ 6.8p10 写成了 {@code blockGone || ticks > holdTicks}（方块一没就切回 + HoldTicks 当上限），
     * 比上游早一 tick 切回（6.8p11 修复；实战 Hold 是开着的）。</p>
     *
     * @param mainSessionAlive   主会话是否还在
     * @param blockStillThere    会话那一格是否仍有方块（未变成空气）
     * @param heldTicks          会话累计 tick 数（上游的 {@code n}，发过 STOP 且工具已对时归零）
     * @param holdTicks          最短持握 tick 数（HoldTicks 设置）
     * @param targetStillMineable 目标仍可挖且在挖掘范围内
     */
    public static boolean shouldKeepHolding(boolean mainSessionAlive, boolean blockStillThere,
                                            int heldTicks, int holdTicks, boolean targetStillMineable) {
        if (!mainSessionAlive) return false;
        return (blockStillThere || heldTicks < holdTicks) && targetStillMineable;
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

    /**
     * 静默换持的选槽逻辑（上游 xw 的纯逻辑版）：
     * 从整表挖掘速度里选最快且快于徒手（&gt;1.0）的槽位下标。
     *
     * <p>静默换持遍历整个 ScreenHandler（无界面时 = playerScreenHandler，
     * 覆盖背包 36 格 + 副手；有界面时还包括已打开容器的全部槽位），
     * 与快捷栏直切只看 1~9 号栏不同。</p>
     *
     * @param speeds 每个槽位的挖掘速度（列表顺序即 ScreenHandler 槽位顺序）
     * @return 最快槽位下标；没有任何槽位快于徒手返回 -1
     */
    public static int fastestScreenSlot(List<Float> speeds) {
        int best = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < speeds.size(); i++) {
            float speed = speeds.get(i);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        return best;
    }
}
