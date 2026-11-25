package com.fo.addon.ancient;

import com.fo.addon.ancient.core.FlightHysteresisLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 巡航高度滞回与俯仰。 */
class FlightHysteresisLogicTest {

    @Test
    void stopsClimbingAtTop() {
        assertFalse(FlightHysteresisLogic.nextClimbing(300, 230, 300, true));
        assertFalse(FlightHysteresisLogic.nextClimbing(301, 230, 300, true));
    }

    @Test
    void startsClimbingAtBottom() {
        assertTrue(FlightHysteresisLogic.nextClimbing(230, 230, 300, false));
        assertTrue(FlightHysteresisLogic.nextClimbing(200, 230, 300, false));
    }

    @Test
    void keepsPreviousStateInMiddle() {
        assertTrue(FlightHysteresisLogic.nextClimbing(260, 230, 300, true));
        assertFalse(FlightHysteresisLogic.nextClimbing(260, 230, 300, false));
        assertTrue(FlightHysteresisLogic.nextClimbing(231, 230, 300, true));
        assertFalse(FlightHysteresisLogic.nextClimbing(299, 230, 300, false));
    }

    @Test
    void pitchFollowsClimbing() {
        assertEquals(-54.77f, FlightHysteresisLogic.pitch(true));
        assertEquals(37.72f, FlightHysteresisLogic.pitch(false));
    }
}
