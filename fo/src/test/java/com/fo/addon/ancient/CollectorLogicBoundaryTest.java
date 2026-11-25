package com.fo.addon.ancient;

import com.fo.addon.ancient.core.FatFilterLogic;
import com.fo.addon.ancient.core.FireworkPolicyLogic;
import com.fo.addon.ancient.core.FlightHysteresisLogic;
import com.fo.addon.ancient.core.LandingPitchLogic;
import com.fo.addon.ancient.core.PackGateLogic;
import com.fo.addon.ancient.core.TakeoffClearanceLogic;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** 采集纯逻辑的边界补充（230/300 正好、dy 正负、阈值 3/4）。 */
class CollectorLogicBoundaryTest {

    @Test
    void exactlyAtMinKeepsClimbing() {
        assertTrue(FlightHysteresisLogic.nextClimbing(230.0, 230, 300, false));
    }

    @Test
    void exactlyAtMaxStopsClimbing() {
        assertFalse(FlightHysteresisLogic.nextClimbing(300.0, 230, 300, true));
    }

    @Test
    void oneAboveMinKeepsPreviousFalse() {
        assertFalse(FlightHysteresisLogic.nextClimbing(231.0, 230, 300, false));
    }

    @Test
    void divingNeverBoostsInMidAir() {
        assertFalse(FireworkPolicyLogic.shouldBoost(false, 250, 230, 300, 80, -0.5, 0));
    }

    @Test
    void belowMinHeightBoosts() {
        assertFalse(FireworkPolicyLogic.shouldBoost(false, 230.0, 230, 300, 80, 0.2, 0));
        assertTrue(FireworkPolicyLogic.shouldBoost(false, 229.9, 230, 300, 80, 0.2, 0));
    }

    @Test
    void singleCornerBlockedFailsTakeoff() {
        boolean[] b = new boolean[36];
        Arrays.fill(b, true);
        b[0] = false;
        assertFalse(TakeoffClearanceLogic.canTakeOff(b));
        b[0] = true;
        assertTrue(TakeoffClearanceLogic.canTakeOff(b));
    }

    @Test
    void landingNegativeDyClampsToFive() {
        assertEquals(5.0f, LandingPitchLogic.pitchFor(-30, 10));
    }

    @Test
    void landingExactlyThreeIsLevel() {
        assertEquals(0.0f, LandingPitchLogic.pitchFor(-30, 3.0));
    }

    @Test
    void packThresholdFourIsInclusive() {
        assertTrue(PackGateLogic.shouldPack(4, 4));
        assertFalse(PackGateLogic.shouldPack(3, 4));
    }

    @Test
    void fatThresholdFourIsInclusive() {
        assertTrue(FatFilterLogic.keep(4, true, 4));
        assertFalse(FatFilterLogic.keep(3, true, 4));
    }
}
