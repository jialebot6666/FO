package com.fo.addon;

import com.fo.addon.utils.NukerMoveLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核爆没沙时移动决策防回归测试：
 * 核爆全程开启；掉落物用 Baritone pickup 收集（不等捡完），没掉落物才走去沙块。
 */
public class NukerMoveLogicTest {

    @Test
    void pathingAlwaysWait() {
        // 正在寻路/pickup 途中不重复指路
        assertEquals(NukerMoveLogic.Action.WAIT, NukerMoveLogic.decide(true, true, true));
        assertEquals(NukerMoveLogic.Action.WAIT, NukerMoveLogic.decide(false, true, true));
    }

    @Test
    void dropsTakePriority() {
        // 有沙掉落物优先 pickup 去捡
        assertEquals(NukerMoveLogic.Action.PICKUP, NukerMoveLogic.decide(true, false, false));
        assertEquals(NukerMoveLogic.Action.PICKUP, NukerMoveLogic.decide(true, true, false));
    }

    @Test
    void noDropsButSand_moveToSand() {
        // 没掉落物但大范围还有沙 → 走去沙块
        assertEquals(NukerMoveLogic.Action.MOVE_TO_SAND, NukerMoveLogic.decide(false, true, false));
    }

    @Test
    void nothing_wait() {
        // 没有掉落物也没沙 → 等
        assertEquals(NukerMoveLogic.Action.WAIT, NukerMoveLogic.decide(false, false, false));
    }
}
