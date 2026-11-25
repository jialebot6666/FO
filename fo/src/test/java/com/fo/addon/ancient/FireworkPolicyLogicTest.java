package com.fo.addon.ancient;

import com.fo.addon.ancient.core.FireworkPolicyLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 补烟花条件。 */
class FireworkPolicyLogicTest {

    @Test
    void boostsWhileClimbingAndTooSlow() {
        assertTrue(FireworkPolicyLogic.shouldBoost(true, 260, 230, 300, 100, -0.4, 0));
    }

    @Test
    void noBoostWhenCooldown() {
        assertFalse(FireworkPolicyLogic.shouldBoost(true, 260, 230, 300, 100, -0.4, 5));
    }

    @Test
    void noBoostWhenCloseToTarget() {
        assertFalse(FireworkPolicyLogic.shouldBoost(true, 260, 230, 300, 20, -0.4, 0));
        assertFalse(FireworkPolicyLogic.shouldBoost(true, 260, 230, 300, 10, -0.4, 0));
    }

    @Test
    void noBoostAtMaxHeight() {
        assertFalse(FireworkPolicyLogic.shouldBoost(true, 300, 230, 300, 100, -0.9, 0));
    }

    @Test
    void noBoostWhileDiving() {
        assertFalse(FireworkPolicyLogic.shouldBoost(false, 260, 230, 300, 100, -0.4, 0));
    }

    @Test
    void boostsWhenBelowMinimumEvenDiving() {
        assertTrue(FireworkPolicyLogic.shouldBoost(false, 220, 230, 300, 100, 0.5, 0));
    }
}
