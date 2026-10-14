package com.fo.addon;

import com.fo.addon.utils.ReturnPathLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 返回起飞点判定逻辑单测（对齐 Ying StorageReturn 语义） */
class ReturnPathLogicTest {

    // ===== shouldRepath =====

    @Test
    void shouldRepath_whenIntervalElapsed() {
        // 距上次重发 >= 20 tick → 重发
        assertTrue(ReturnPathLogic.shouldRepath(20, 0, true));
        assertTrue(ReturnPathLogic.shouldRepath(40, 20, true));
        assertTrue(ReturnPathLogic.shouldRepath(100, 80, true));
    }

    @Test
    void shouldRepath_whenNotPathing() {
        // 即使间隔未到，只要当前没在寻路（上次寻路失败/结束）→ 重发
        assertTrue(ReturnPathLogic.shouldRepath(5, 0, false));
        assertTrue(ReturnPathLogic.shouldRepath(19, 0, false));
        assertTrue(ReturnPathLogic.shouldRepath(0, 0, false));
    }

    @Test
    void shouldRepath_whenWithinIntervalAndPathing() {
        // 间隔未到且仍在寻路 → 不重发（避免刷屏寻路请求）
        assertFalse(ReturnPathLogic.shouldRepath(0, 0, true));
        assertFalse(ReturnPathLogic.shouldRepath(10, 0, true));
        assertFalse(ReturnPathLogic.shouldRepath(19, 0, true));
    }

    // ===== timedOut =====

    @Test
    void timedOut_after600Ticks() {
        assertTrue(ReturnPathLogic.timedOut(601));
        assertTrue(ReturnPathLogic.timedOut(1200));
        assertTrue(ReturnPathLogic.timedOut(ReturnPathLogic.TIMEOUT_TICKS + 1));
    }

    @Test
    void notTimedOut_atOrBefore600Ticks() {
        assertFalse(ReturnPathLogic.timedOut(0));
        assertFalse(ReturnPathLogic.timedOut(599));
        assertFalse(ReturnPathLogic.timedOut(600));
        assertFalse(ReturnPathLogic.timedOut(ReturnPathLogic.TIMEOUT_TICKS));
    }

    // ===== 常量一致性 =====

    @Test
    void constantsMatchYingSemantics() {
        // Ying StorageReturn: REPATH_INTERVAL=20, TIMEOUT_TICKS=600
        assertEquals(20, ReturnPathLogic.REPATH_INTERVAL);
        assertEquals(600, ReturnPathLogic.TIMEOUT_TICKS);
    }

    @Test
    void defaultReturnDistanceMatchesYing() {
        // Ying leaveShipNow 用的就是 2.5 格
        assertEquals(2.5, ReturnPathLogic.DEFAULT_RETURN_DISTANCE, 1e-9);
    }

    // ===== shouldReturnBeforeTakeoff（起飞前先回降落点，防原地起飞撞墙）=====

    @Test
    void returnsWhenGroundedFarFromLanding() {
        assertTrue(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 3.0, 2.5));
        assertTrue(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 50.0, 2.5));
    }

    @Test
    void doesNotReturnWhenWithinThreshold() {
        // 严格大于才回：正好等于阈值算"已在降落点"，直接起飞
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 2.5, 2.5));
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 2.4, 2.5));
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 0.0, 2.5));
    }

    @Test
    void switchOffRestoresYingBehaviour() {
        // 关掉开关 = Ying 采集完成路径的原行为：不管多远都直接起飞
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(false, true, true, 100.0, 2.5));
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(false, true, true, 3.0, 0.0));
    }

    @Test
    void doesNotReturnWhenNotGrounded() {
        // 在空中本来就不需要走回去（直接起飞离开即可）
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, false, true, 100.0, 2.5));
    }

    @Test
    void doesNotReturnWithoutWaypoints() {
        // 起飞前触发的存储会话可能还没有当前船 → 没有降落点可比，直接起飞
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, false, 0.0, 2.5));
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, false, 100.0, 2.5));
    }

    @Test
    void thresholdIsConfigurable() {
        // 阈值 0：只要不在降落点上就回去
        assertTrue(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 0.1, 0.0));
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 0.0, 0.0));
        // 阈值调到 10：10 格以内就直接起飞
        assertFalse(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 10.0, 10.0));
        assertTrue(ReturnPathLogic.shouldReturnBeforeTakeoff(true, true, true, 10.1, 10.0));
    }
}
