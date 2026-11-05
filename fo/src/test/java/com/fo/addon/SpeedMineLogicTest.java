package com.fo.addon;

import com.fo.addon.utils.SpeedMineLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FO 发包挖掘纯逻辑测试。
 *
 * <p>锚点来自 Hachimi V2 SpeedMine 实战配置（cheng.json：Speed=0.7）：
 * vanilla 进度累计到 0.7 即发包破坏；一击即碎（delta&gt;=1）单 tick 挖满。
 * 6.8p11 起这里测的是模块<b>真正走的那条</b>累加+钳制路径——此前的 {@code shouldBreak}/{@code accumulate}
 * 两个函数只有测试在引用、模块从不叫，所以钳制漏掉 /Speed 导致提速整个失效也没被测出来。</p>
 */
class SpeedMineLogicTest {
    // ── 进度累加 + 理想钳制（上游 xs L239-241 与 L259-263）──

    @Test
    void nextProgress_按Speed缩放并封顶1() {
        // Speed=0.7：单 tick 涨 delta/0.7（原版 10 tick 的活 7 tick 干完）
        assertEquals(0.142857f, SpeedMineLogic.nextProgress(0f, 0.1f, 0.7f), 1e-5f);
        assertEquals(1.0f, SpeedMineLogic.nextProgress(0.95f, 0.2f, 0.7f), 1e-6f);
        // Speed=1：与原版进度一致
        assertEquals(0.1f, SpeedMineLogic.nextProgress(0f, 0.1f, 1.0f), 1e-6f);
    }

    @Test
    void idealProgressBound_除数是Speed_不能漏() {
        // 上游 f3 = oQ(...) / this.b * this.l。6.8p10 漏掉 / b，进度恒被压回原版进度 → Speed 设置整个失效
        assertEquals(0.142857f, SpeedMineLogic.idealProgressBound(0.1f, 1, 0.7f), 1e-5f);
        assertEquals(1.428571f, SpeedMineLogic.idealProgressBound(0.1f, 10, 0.7f), 1e-5f);
        assertEquals(1.0f, SpeedMineLogic.idealProgressBound(0.1f, 10, 1.0f), 1e-6f);
    }

    @Test
    void Speed零点七_在原版七成的tick数挖满() {
        // 回归锁：6.8p10 无论 Speed 调多少，都要花满原版 tick（提速根本没生效）
        assertEquals(7, ticksToFull(0.1f, 0.7f));    // 原版 10 tick → ceil(10*0.7)
        assertEquals(14, ticksToFull(0.05f, 0.7f));  // 原版 20 tick → 14
        assertEquals(3, ticksToFull(0.25f, 0.7f));   // 原版 4 tick → ceil(2.8)
        assertEquals(10, ticksToFull(0.1f, 1.0f));   // Speed=1 保持原版耗时
        assertEquals(2, ticksToFull(0.5f, 0.7f));    // 原版 2 tick → ceil(2*0.7)
        assertEquals(1, ticksToFull(0.5f, 0.5f));    // 原版 2 tick → ceil(1) = 1
        assertEquals(1, ticksToFull(0.1f, 0.1f));    // Speed 再低也至少 1 tick
    }

    /**
     * 复刻 {@code Session.progressTick()} 的「累加 + 理想钳制」两步，返回进度挖满所需的 tick 数。
     * 与模块内的实现同序：先累加、再取 maxDelta、最后按上限钳制。
     */
    private static int ticksToFull(float delta, float speedScale) {
        float progress = 0f;
        float maxDelta = 0f;
        for (int ticks = 1; ticks <= 2000; ticks++) {
            progress = SpeedMineLogic.nextProgress(progress, delta, speedScale);
            maxDelta = Math.max(maxDelta, delta);
            float ideal = SpeedMineLogic.idealProgressBound(maxDelta, ticks, speedScale);
            if (SpeedMineLogic.clampToIdealBound(ideal, progress, false)) progress = ideal;
            if (progress >= 1.0f) return ticks;
        }
        return -1;
    }

    @Test
    void clampToIdealBound_上限更低才钳_挖空气会话一律取上限() {
        assertFalse(SpeedMineLogic.clampToIdealBound(0.5f, 0.3f, false)); // 上限高于进度 → 不动
        assertTrue(SpeedMineLogic.clampToIdealBound(0.3f, 0.5f, false));  // 上限更低 → 钳下去
        assertTrue(SpeedMineLogic.clampToIdealBound(9f, 0.1f, true));     // 空气会话：把空转的 tick 折算成进度
        assertFalse(SpeedMineLogic.clampToIdealBound(9f, 0.1f, false));
    }

    @Test
    void 进度恒定时钳制不绑_Speed提速留得住() {
        // 稳态下（每 tick delta 相同）累加值恰等于上限，钳制不该把 Speed 的提速吃掉
        float progress = 0f;
        float maxDelta = 0f;
        for (int ticks = 1; ticks <= 7; ticks++) {
            progress = SpeedMineLogic.nextProgress(progress, 0.1f, 0.7f);
            maxDelta = Math.max(maxDelta, 0.1f);
            float ideal = SpeedMineLogic.idealProgressBound(maxDelta, ticks, 0.7f);
            assertFalse(SpeedMineLogic.clampToIdealBound(ideal, progress, false),
                "第 " + ticks + " tick 不该被钳制");
            if (SpeedMineLogic.clampToIdealBound(ideal, progress, false)) progress = ideal;
        }
        assertTrue(progress >= 1.0f);
    }

    // ── 保持换持切回时机（上游 xB L542-555）──

    @Test
    void shouldKeepHolding_方块还在就一直持着() {
        // 上游：block 未消失 → (未消失 || n<HoldTicks) 直接成立，哪怕早已超过 HoldTicks
        assertTrue(SpeedMineLogic.shouldKeepHolding(true, true, 999, 2, true));
    }

    @Test
    void shouldKeepHolding_方块没了要持满HoldTicks才切回() {
        // 6.8p10 写的是「方块一没就切回」，比上游早；这里锁死上游的 n < HoldTicks 严格小于
        assertTrue(SpeedMineLogic.shouldKeepHolding(true, false, 1, 2, true));
        assertFalse(SpeedMineLogic.shouldKeepHolding(true, false, 2, 2, true));
        assertFalse(SpeedMineLogic.shouldKeepHolding(true, false, 3, 2, true));
    }

    @Test
    void shouldKeepHolding_主会话没了直接切回() {
        assertFalse(SpeedMineLogic.shouldKeepHolding(false, true, 0, 2, true));
    }

    @Test
    void shouldKeepHolding_目标不可挖或跑出范围直接切回() {
        assertFalse(SpeedMineLogic.shouldKeepHolding(true, true, 0, 2, false));
        assertFalse(SpeedMineLogic.shouldKeepHolding(true, false, 0, 2, false));
    }

    @Test
    void applyStateFlags_离地且无视惩罚_还原五倍() {
        // CheckGround=false（实战值）：离地时还原原版扣掉的 /5 惩罚
        assertEquals(0.5f, SpeedMineLogic.applyStateFlags(0.1f, false, false, false, true), 1e-6);
    }

    @Test
    void applyStateFlags_在地面_不变() {
        assertEquals(0.1f, SpeedMineLogic.applyStateFlags(0.1f, true, false, false, true), 1e-6);
    }

    @Test
    void applyStateFlags_开启地面检测_离地保留惩罚() {
        // CheckGround=true：离地惩罚保留
        assertEquals(0.1f, SpeedMineLogic.applyStateFlags(0.1f, false, false, true, true), 1e-6);
    }

    @Test
    void applyStateFlags_鞘翅滑翔且开启飞行检测_保留惩罚() {
        assertEquals(0.1f, SpeedMineLogic.applyStateFlags(0.1f, false, true, false, true), 1e-6);
    }

    @Test
    void applyStateFlags_鞘翅但关闭飞行检测_无视惩罚() {
        assertEquals(0.5f, SpeedMineLogic.applyStateFlags(0.1f, false, true, false, false), 1e-6);
    }

    @Test
    void 一击即碎_单tick就挖满() {
        // delta >= 1（徒手挖豆腐/工具一击即碎）：第一 tick 进度就满，与 Speed 无关
        assertEquals(1, ticksToFull(1.0f, 0.7f));
        assertEquals(1, ticksToFull(2.0f, 1.0f));
    }

    @Test
    void nextProgress_增量非正时不涨进度() {
        // 模块在 progressTick 里对 vanillaDelta <= 0 直接早退；这里锁死「不会倒涨」
        assertEquals(0.5f, SpeedMineLogic.nextProgress(0.5f, 0.0f, 0.7f), 1e-6f);
    }

    @Test
    void lerpColor_端点和中点() {
        int[] red = {255, 60, 60, 80};
        int[] green = {60, 255, 60, 120};
        int[] s = SpeedMineLogic.lerpColor(red, green, 0);
        assertEquals(255, s[0]);
        int[] e = SpeedMineLogic.lerpColor(red, green, 1);
        assertEquals(255, e[1]);
        int[] m = SpeedMineLogic.lerpColor(red, green, 0.5);
        assertEquals(158, m[0]); // (255+60)/2
        assertEquals(158, m[1]);
    }

    @Test
    void lerpColor_越界裁剪() {
        int[] red = {255, 60, 60, 80};
        int[] green = {60, 255, 60, 120};
        // t>1 裁剪到 t=1 → 终点色；t<0 裁剪到 t=0 → 起点色
        assertEquals(60, SpeedMineLogic.lerpColor(red, green, 2)[0]);
        assertEquals(255, SpeedMineLogic.lerpColor(red, green, -1)[0]);
    }

    // ── Grim 绕过时序（上游 alP() / xt() FX 分支）──

    @Test
    void inSessionCooldown_280ms内_冷却() {
        // 上游常量：上次 STOP 后 280ms 内不开新会话
        assertTrue(SpeedMineLogic.inSessionCooldown(1000, 800, 280));
        assertTrue(SpeedMineLogic.inSessionCooldown(1280, 1000, 280)); // 恰好等于边界：仍算冷却
        assertFalse(SpeedMineLogic.inSessionCooldown(1281, 1000, 280));
        // 很久没发过 STOP（lastStopTime=0）：不冷却
        assertFalse(SpeedMineLogic.inSessionCooldown(5000, 0, 280));
    }

    @Test
    void timeModeStopAllowed_差值小于25ms才放行() {
        // 期望耗时 1000ms，已挖 990ms：差 10 < 25 → 放行
        assertTrue(SpeedMineLogic.timeModeStopAllowed(1000, 990, 25));
        // 已挖 960ms：差 40 >= 25 → 等待
        assertFalse(SpeedMineLogic.timeModeStopAllowed(1000, 960, 25));
        // 超时（挖过头）：放行
        assertTrue(SpeedMineLogic.timeModeStopAllowed(1000, 1100, 25));
    }

    // ── 静默换持选槽（6.8p8，上游 xw）──

    @Test
    void fastestScreenSlot_没有快于徒手的工具_返回负一() {
        // 全是空手/杂项（速度 ≤1.0）：不换持
        assertEquals(-1, SpeedMineLogic.fastestScreenSlot(java.util.List.of(1.0f, 1.0f, 0.8f)));
        assertEquals(-1, SpeedMineLogic.fastestScreenSlot(java.util.List.of()));
    }

    @Test
    void fastestScreenSlot_选最快的槽位() {
        // playerScreenHandler 布局：0=合成结果 1~4=合成 5~8=盔甲 9~35=主背包 36~44=快捷栏 45=副手
        // 挖黑曜石：快捷栏 40 号（下标 40）的钻石镐 8.0 最快
        int best = SpeedMineLogic.fastestScreenSlot(
            java.util.List.of(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 8f));
        assertEquals(12, best);
    }

    @Test
    void fastestScreenSlot_并列取先出现的() {
        // 两把同为 6.0 的镐：取先遍历到的（上游 > 严格比较，后到的并列不替换）
        assertEquals(1, SpeedMineLogic.fastestScreenSlot(java.util.List.of(1f, 6f, 6f)));
    }

    @Test
    void fastestScreenSlot_徒手速度被忽略() {
        // 徒手对泥土 1.0 以下也常见；速度恰为 1.0 不算"快于徒手"
        assertEquals(-1, SpeedMineLogic.fastestScreenSlot(java.util.List.of(1f, 1.0f, 1f)));
    }
}
