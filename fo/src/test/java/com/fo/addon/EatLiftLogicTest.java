package com.fo.addon;

import com.fo.addon.utils.EatLiftLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 进食拉升状态机（移植自 eat-lift）。 */
class EatLiftLogicTest {

    @Test
    void 无延迟时下一tick按下() {
        EatLiftLogic logic = new EatLiftLogic();
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 45));
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 0, 45));
    }

    @Test
    void 延迟两tick后按下() {
        EatLiftLogic logic = new EatLiftLogic();
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 2, 45));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 2, 45));
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 2, 45));
    }

    @Test
    void 按满设定时长才释放() {
        EatLiftLogic logic = new EatLiftLogic();
        logic.tick(true, true, 0, 3);
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 0, 3));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 3));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 3));
        assertEquals(EatLiftLogic.Action.RELEASE, logic.tick(true, true, 0, 3));
    }

    @Test
    void 按住期间停止吃也按满() {
        EatLiftLogic logic = new EatLiftLogic();
        logic.tick(true, true, 0, 3);
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 0, 3));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(false, true, 0, 3));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(false, true, 0, 3));
        assertEquals(EatLiftLogic.Action.RELEASE, logic.tick(false, true, 0, 3));
    }

    @Test
    void 释放时仍在吃则本段不再触发() {
        EatLiftLogic logic = new EatLiftLogic();
        logic.tick(true, true, 0, 2);
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 0, 2));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 2));
        assertEquals(EatLiftLogic.Action.RELEASE, logic.tick(true, true, 0, 2));
        // 同一段进食继续，不应再次按下
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 2));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 2));
        assertTrue(logic.suppressed());
    }

    @Test
    void 停止进食后重新吃会再次触发() {
        EatLiftLogic logic = new EatLiftLogic();
        logic.tick(true, true, 0, 2);
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 0, 2));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 2));
        assertEquals(EatLiftLogic.Action.RELEASE, logic.tick(true, true, 0, 2));
        // 松口
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(false, true, 0, 2));
        assertFalse(logic.suppressed());
        // 再吃
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 2));
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 0, 2));
    }

    @Test
    void 等待中停止吃则放弃触发() {
        EatLiftLogic logic = new EatLiftLogic();
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 5, 10));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(false, true, 5, 10));
        assertEquals(EatLiftLogic.Phase.IDLE, logic.phase());
        assertFalse(logic.suppressed());
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 5, 10));
    }

    @Test
    void 门禁关闭时按住中立即释放() {
        EatLiftLogic logic = new EatLiftLogic();
        logic.tick(true, true, 0, 100);
        assertEquals(EatLiftLogic.Action.PRESS, logic.tick(true, true, 0, 100));
        assertEquals(EatLiftLogic.Action.RELEASE, logic.tick(true, false, 0, 100));
        assertEquals(EatLiftLogic.Phase.IDLE, logic.phase());
        assertTrue(logic.suppressed());
    }

    @Test
    void 门禁关闭时等待中不按下() {
        EatLiftLogic logic = new EatLiftLogic();
        logic.tick(true, true, 0, 100);
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, false, 0, 100));
        assertEquals(EatLiftLogic.Action.NONE, logic.tick(true, true, 0, 100));
        assertEquals(EatLiftLogic.Phase.IDLE, logic.phase());
    }
}
