package com.fo.addon;

import com.fo.addon.utils.FacingLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 朝向计算防回归测试（开潜影盒/交互必须视线对准，角度算错盒子就打不开）。
 */
public class FacingLogicTest {

    @Test
    void targetEast() {
        float[] fp = FacingLogic.yawPitchTo(1, 0, 0, 0, 0, 0);
        assertEquals(-90.0f, fp[0], 0.01f); // 正东 = yaw -90°
        assertEquals(0.0f, fp[1], 0.01f);   // 水平
    }

    @Test
    void targetSouth() {
        float[] fp = FacingLogic.yawPitchTo(0, 0, 1, 0, 0, 0);
        assertEquals(0.0f, fp[0], 0.01f);   // 正南 = yaw 0°
        assertEquals(0.0f, fp[1], 0.01f);
    }

    @Test
    void targetAbove() {
        float[] fp = FacingLogic.yawPitchTo(0, 1, 0, 0, 0, 0);
        assertEquals(-90.0f, fp[1], 0.01f); // 正上方 = pitch -90°
    }

    @Test
    void targetNorthWestUp() {
        float[] fp = FacingLogic.yawPitchTo(-1, 1, -1, 0, 0, 0);
        assertEquals(135.0f, fp[0], 0.01f); // 西北 = yaw 135°
        assertTrue(fp[1] < 0);              // 上方 → pitch 为负
    }

    @Test
    void eyeOffsetShiftsAngles() {
        // 眼睛抬高后看同一目标，pitch 应该变小（更平视）
        float[] low = FacingLogic.yawPitchTo(0, 1, 5, 0, 0, 0);
        float[] high = FacingLogic.yawPitchTo(0, 1, 5, 0, 4, 0);
        assertTrue(high[1] > low[1]);
    }
}
