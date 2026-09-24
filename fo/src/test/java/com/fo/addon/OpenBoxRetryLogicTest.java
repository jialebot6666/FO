package com.fo.addon;

import com.fo.addon.utils.OpenBoxRetryLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 开盒重试策略防回归测试：打不开的盒子不能无限重试（会导致人物抽搐/自转）。
 */
public class OpenBoxRetryLogicTest {

    @Test
    void farAwayAlwaysMoveTo() {
        assertEquals(OpenBoxRetryLogic.Action.MOVE_TO, OpenBoxRetryLogic.decide(false, 0, 3));
        assertEquals(OpenBoxRetryLogic.Action.MOVE_TO, OpenBoxRetryLogic.decide(false, 10, 3));
    }

    @Test
    void nearAndBelowLimitTryOpen() {
        assertEquals(OpenBoxRetryLogic.Action.TRY_OPEN, OpenBoxRetryLogic.decide(true, 0, 3));
        assertEquals(OpenBoxRetryLogic.Action.TRY_OPEN, OpenBoxRetryLogic.decide(true, 2, 3));
    }

    @Test
    void nearAndAtLimitSkip() {
        assertEquals(OpenBoxRetryLogic.Action.SKIP_BOX, OpenBoxRetryLogic.decide(true, 3, 3));
        assertEquals(OpenBoxRetryLogic.Action.SKIP_BOX, OpenBoxRetryLogic.decide(true, 5, 3));
    }

    @Test
    void customLimitWorks() {
        assertEquals(OpenBoxRetryLogic.Action.SKIP_BOX, OpenBoxRetryLogic.decide(true, 2, 2));
        assertEquals(OpenBoxRetryLogic.Action.TRY_OPEN, OpenBoxRetryLogic.decide(true, 1, 2));
    }
}
