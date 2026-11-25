package com.fo.addon.elytra.hell;

import com.fo.addon.elytra.hell.HellFlightLogic.Food;
import com.fo.addon.elytra.hell.HellFlightStateMachine.Phase;
import com.fo.addon.elytra.hell.HellFlightStateMachine.RepairStep;
import com.fo.addon.elytra.hell.HellFlightStateMachine.SupplyStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HellFlightUiTextTest {
    private static boolean hasChinese(String value) {
        return value.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private static boolean hasAsciiLetter(String value) {
        return value.chars().anyMatch(cp -> cp >= 'a' && cp <= 'z' || cp >= 'A' && cp <= 'Z');
    }

    @Test
    void allVisibleEnumsAreChinese() {
        for (Phase phase : Phase.values()) {
            assertTrue(hasChinese(phase.toString()), phase.name());
            assertFalse(hasAsciiLetter(phase.toString()), phase.name());
        }
        for (SupplyStep step : SupplyStep.values()) {
            assertTrue(hasChinese(step.toString()), step.name());
            assertFalse(hasAsciiLetter(step.toString()), step.name());
        }
        for (RepairStep step : RepairStep.values()) {
            assertTrue(hasChinese(step.toString()), step.name());
            assertFalse(hasAsciiLetter(step.toString()), step.name());
        }
        for (Food food : Food.values()) {
            assertTrue(hasChinese(food.toString()), food.name());
            assertFalse(hasAsciiLetter(food.toString()), food.name());
        }
    }

    @Test
    void attachmentSpeedDefaultIsPreserved() {
        assertEquals(1.5, HellFlightLogic.DEFAULT_FIREWORK_SPEED);
        assertEquals(6, Food.values().length);
    }
}
