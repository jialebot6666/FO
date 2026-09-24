package com.fo.addon;

import com.fo.addon.utils.NukerMoveLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核爆没沙时移动决策防回归测试：
 * 核爆全程开启，不停下等捡掉落物；大范围还有沙且没在寻路 → 走去最近沙块。
 */
public class NukerMoveLogicTest {

    @Test
    void hasSandAndNotPathing_moveToSand() {
        assertEquals(NukerMoveLogic.Action.MOVE_TO_SAND, NukerMoveLogic.decide(true, false));
    }

    @Test
    void hasSandButPathing_wait() {
        // 正在寻路（走去沙块途中）不重复指路
        assertEquals(NukerMoveLogic.Action.WAIT, NukerMoveLogic.decide(true, true));
    }

    @Test
    void noSandNear_wait() {
        // 大范围也没沙：不掉队等待
        assertEquals(NukerMoveLogic.Action.WAIT, NukerMoveLogic.decide(false, false));
        assertEquals(NukerMoveLogic.Action.WAIT, NukerMoveLogic.decide(false, true));
    }
}
