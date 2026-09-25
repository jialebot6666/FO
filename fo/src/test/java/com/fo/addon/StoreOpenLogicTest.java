package com.fo.addon;

import com.fo.addon.utils.StoreOpenLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 存沙/补给开盒状态机决策防回归测试（V4.14）。
 * 核心修复：开盒超时后必须原地重试（RETRY_OPEN），禁止回退 MINING 造成
 * "goToStore→重新指路→转头开盒→失败→MINING" 死循环（全程自转+存不进沙的根因）。
 */
public class StoreOpenLogicTest {

    // 界面刚开、服务器内容还没同步完 → 等同步（每次开盒都重新等，storeTickCounter 重置后生效）
    @Test
    void screenOpenButNotSyncedWaits() {
        assertEquals(StoreOpenLogic.Action.SYNC_WAIT,
            StoreOpenLogic.decide(true, 0, 3, true, 0, 40, 0, 3));
        assertEquals(StoreOpenLogic.Action.SYNC_WAIT,
            StoreOpenLogic.decide(true, 2, 3, true, 0, 40, 0, 3));
    }

    // 界面开了且同步完成 → 执行存/取
    @Test
    void screenOpenAndSyncedProceeds() {
        assertEquals(StoreOpenLogic.Action.PROCEED,
            StoreOpenLogic.decide(true, 3, 3, true, 0, 40, 0, 3));
        assertEquals(StoreOpenLogic.Action.PROCEED,
            StoreOpenLogic.decide(true, 10, 3, true, 0, 40, 0, 3));
    }

    // 没开界面、还在等待期内 → 继续等
    @Test
    void waitingOpenNotTimedOutWaits() {
        assertEquals(StoreOpenLogic.Action.WAIT_OPEN,
            StoreOpenLogic.decide(false, 0, 3, true, 10, 40, 0, 3));
        assertEquals(StoreOpenLogic.Action.WAIT_OPEN,
            StoreOpenLogic.decide(false, 0, 3, true, 40, 40, 0, 3));
    }

    // 核心回归：超时且失败次数未到上限 → 原地重试开盒（不是 GIVE_UP！）
    @Test
    void timeoutRetriesOpenInPlace() {
        assertEquals(StoreOpenLogic.Action.RETRY_OPEN,
            StoreOpenLogic.decide(false, 0, 3, true, 41, 40, 0, 3));
        assertEquals(StoreOpenLogic.Action.RETRY_OPEN,
            StoreOpenLogic.decide(false, 0, 3, true, 100, 40, 1, 3));
        assertEquals(StoreOpenLogic.Action.RETRY_OPEN,
            StoreOpenLogic.decide(false, 0, 3, true, 41, 40, 2, 4));
    }

    // 超时且失败次数达到上限 → 才放弃（由模块决定换盒/回挖矿）
    @Test
    void timeoutAtFailLimitGivesUp() {
        assertEquals(StoreOpenLogic.Action.GIVE_UP,
            StoreOpenLogic.decide(false, 0, 3, true, 41, 40, 2, 3));
        assertEquals(StoreOpenLogic.Action.GIVE_UP,
            StoreOpenLogic.decide(false, 0, 3, true, 50, 40, 5, 3));
    }

    // 不在等待状态且界面没开（刚进入状态）→ 发起开盒
    @Test
    void notWaitingAndScreenClosedTriesOpen() {
        assertEquals(StoreOpenLogic.Action.RETRY_OPEN,
            StoreOpenLogic.decide(false, 0, 3, false, 0, 40, 0, 3));
    }
}
