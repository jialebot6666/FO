package com.fo.addon.ancient;

import com.fo.addon.ancient.core.FlightNavLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 飞行寻路纯数学：分层、偏差、冲过头、螺旋。 */
class FlightNavLogicTest {

    @Test
    void layerByDistanceUsesTerminalFirst() {
        assertEquals(FlightNavLogic.Layer.TERMINAL,
            FlightNavLogic.layerByDistance(30, 120, 40));
        assertEquals(FlightNavLogic.Layer.APPROACH,
            FlightNavLogic.layerByDistance(100, 120, 40));
        assertEquals(FlightNavLogic.Layer.CRUISE,
            FlightNavLogic.layerByDistance(500, 120, 40));
    }

    @Test
    void layerBoundaryIsInclusive() {
        assertEquals(FlightNavLogic.Layer.TERMINAL,
            FlightNavLogic.layerByDistance(40, 120, 40));
        assertEquals(FlightNavLogic.Layer.APPROACH,
            FlightNavLogic.layerByDistance(120, 120, 40));
    }

    @Test
    void crossTrackZeroOnLine() {
        // A(0,0) → B(100,0)，点在线上
        assertEquals(0.0, FlightNavLogic.crossTrackError(50, 0, 0, 0, 100, 0), 1e-6);
    }

    @Test
    void crossTrackIsPerpendicularDistance() {
        // 航线沿 x 轴，点在 z=20
        assertEquals(20.0, FlightNavLogic.crossTrackError(50, 20, 0, 0, 100, 0), 1e-6);
    }

    @Test
    void crossTrackDegenerateLineIsDistanceToPoint() {
        assertEquals(5.0, FlightNavLogic.crossTrackError(3, 4, 0, 0, 0, 0), 1e-6);
    }

    @Test
    void overshootNeedsThreeSeconds() {
        assertFalse(FlightNavLogic.overshoot(59));
        assertTrue(FlightNavLogic.overshoot(60));
    }

    @Test
    void spiralRadiusShrinksAndClamps() {
        assertEquals(12.0, FlightNavLogic.spiralRadius(12, 2, 0, 2), 1e-6);
        assertEquals(6.0, FlightNavLogic.spiralRadius(12, 2, 3, 2), 1e-6);
        assertEquals(2.0, FlightNavLogic.spiralRadius(12, 2, 99, 2), 1e-6);
    }

    @Test
    void spiralPitchHasThreeBands() {
        assertEquals(10.0f, FlightNavLogic.spiralPitch(6), 1e-3);
        assertTrue(FlightNavLogic.spiralPitch(12) <= 20.0f);
        assertTrue(FlightNavLogic.spiralPitch(60) <= 55.0f);
        assertTrue(FlightNavLogic.spiralPitch(60) >= 5.0f);
    }

    @Test
    void cruisePitchClimbsWhenLow() {
        assertEquals(-54.77f, FlightNavLogic.cruisePitch(200, 230, 300), 1e-3);
        assertEquals(30.0f, FlightNavLogic.cruisePitch(310, 230, 300), 1e-3);
        assertEquals(37.72f, FlightNavLogic.cruisePitch(260, 230, 300), 1e-3);
    }

    @Test
    void fireworkBudgetGrowsWithDistance() {
        assertEquals(13, FlightNavLogic.fireworkBudget(40));
        assertEquals(37, FlightNavLogic.fireworkBudget(1000));
    }

    @Test
    void stepTowardTakesShortestWayAcrossZero() {
        assertEquals(358.0f, FlightNavLogic.stepToward(350f, 10f, 8f), 1e-3);
        assertEquals(352.0f, FlightNavLogic.stepToward(350f, 10f, 2f), 1e-3);
        assertEquals(10.0f, FlightNavLogic.stepToward(2f, 10f, 8f), 1e-3);
    }
}
