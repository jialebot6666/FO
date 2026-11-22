package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 修复会话停止条件。 */
class RepairSessionLogicTest {

    @Test
    void 两件都到目标就结束() {
        assertEquals(RepairSessionLogic.Next.DONE,
            RepairSessionLogic.decide(true, true, 10, 128, 50, true, true));
    }

    @Test
    void 低于安全线优先停止() {
        assertEquals(RepairSessionLogic.Next.STOP_SAFETY,
            RepairSessionLogic.decide(false, true, 10, 128, 50, false, true));
        assertEquals(RepairSessionLogic.Next.STOP_SAFETY,
            RepairSessionLogic.decide(true, false, 10, 128, 50, true, false));
    }

    @Test
    void 达到本次上限停止() {
        assertEquals(RepairSessionLogic.Next.STOP_LIMIT,
            RepairSessionLogic.decide(false, false, 128, 128, 500, true, true));
    }

    @Test
    void 瓶子用完停止() {
        assertEquals(RepairSessionLogic.Next.STOP_NO_BOTTLES,
            RepairSessionLogic.decide(false, false, 10, 128, 0, true, true));
    }

    @Test
    void 其余情况继续投瓶() {
        assertEquals(RepairSessionLogic.Next.CONTINUE,
            RepairSessionLogic.decide(false, false, 10, 128, 1, true, true));
    }
}
