package com.fo.addon;

import com.fo.addon.utils.PickupTimeoutLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PickupTimeoutLogic 纯逻辑测试：拾取超时决策（潜影盒断开 / 其他重试 / 未超时等待） */
public class PickupTimeoutLogicTest {

    @Test
    void waitsBeforeTimeout() {
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(true, 0));
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(false, 0));
        // 临界点：200 tick 未超时
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(true, 200));
        assertEquals(PickupTimeoutLogic.Action.WAIT, PickupTimeoutLogic.decide(false, 200));
    }

    @Test
    void disconnectsOnlyForShulkerAfterTimeout() {
        // 潜影盒超时 → 断开连接（核心资产防丢）
        assertEquals(PickupTimeoutLogic.Action.DISCONNECT, PickupTimeoutLogic.decide(true, 201));
        // 工作台/末影箱超时 → 回挖矿重试
        assertEquals(PickupTimeoutLogic.Action.RETRY, PickupTimeoutLogic.decide(false, 201));
    }
}
