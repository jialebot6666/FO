package com.fo.addon.ancient;

import com.fo.addon.ancient.core.LandingPitchLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 进近俯仰 5–45。 */
class LandingPitchLogicTest {

    @Test
    void withinThreeBlocksLevelsOut() {
        assertEquals(0.0f, LandingPitchLogic.pitchFor(50, 3.0));
        assertEquals(0.0f, LandingPitchLogic.pitchFor(0, 1.0));
    }

    @Test
    void clampsToFiveToFortyFive() {
        assertEquals(5.0f, LandingPitchLogic.pitchFor(0, 10));
        assertEquals(5.0f, LandingPitchLogic.pitchFor(-100, 10));
        assertEquals(45.0f, LandingPitchLogic.pitchFor(100, 10));
        assertEquals(8.0f, LandingPitchLogic.pitchFor(10, 10), 0.001f);
    }

    @Test
    void justOutsideThreeStillDives() {
        assertTrue(LandingPitchLogic.pitchFor(20, 3.01) > 0);
    }
}
