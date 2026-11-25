package com.fo.addon.ancient;

import com.fo.addon.ancient.core.TakeoffClearanceLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 起飞净空 3×3×4。 */
class TakeoffClearanceLogicTest {

    private static boolean[] all(boolean v) {
        boolean[] b = new boolean[36];
        java.util.Arrays.fill(b, v);
        return b;
    }

    @Test
    void allAirIsClear() {
        assertTrue(TakeoffClearanceLogic.canTakeOff(all(true)));
    }

    @Test
    void oneBlockedIsNotClear() {
        boolean[] b = all(true);
        b[13] = false;
        assertFalse(TakeoffClearanceLogic.canTakeOff(b));
    }

    @Test
    void wrongSizeIsNotClear() {
        assertFalse(TakeoffClearanceLogic.canTakeOff(new boolean[27]));
        assertFalse(TakeoffClearanceLogic.canTakeOff(new boolean[37]));
        assertFalse(TakeoffClearanceLogic.canTakeOff(null));
    }
}
