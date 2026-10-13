package com.fo.addon;

import com.fo.addon.utils.PickupTimeoutLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PickupTimeoutLogic 纯逻辑测试（V4.54 倒计时语义，对齐 misaka x0016）：
 *  剩余 > 0 → WAIT；剩余 ≤ 0 → 超时（潜影盒断开 / 其他重试） */
public class PickupTimeoutLogicTest {

    @Test
    void waitsWhileRemainingTicksPositive() {
        // 进入拾取即 200 tick 倒计时：剩余 > 0 一律等待（不看掉落物是否可见）
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(true, 200));
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(false, 200));
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(true, 1));
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(false, 1));
    }

    @Test
    void timesOutWhenRemainingHitsZero() {
        // 剩余 0 / 负数 → 超时
        assertEquals(PickupTimeoutLogic.Action.DISCONNECT, PickupTimeoutLogic.decide(true, 0));
        assertEquals(PickupTimeoutLogic.Action.RETRY, PickupTimeoutLogic.decide(false, 0));
        assertEquals(PickupTimeoutLogic.Action.DISCONNECT, PickupTimeoutLogic.decide(true, -1));
        assertEquals(PickupTimeoutLogic.Action.RETRY, PickupTimeoutLogic.decide(false, -5));
    }
}
