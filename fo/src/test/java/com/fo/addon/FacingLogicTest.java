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

    @Test
    void yawMatchesSlimefunHelperFormula() {
        // SlimefunHelper PlayerStateManager.setPlayerRotationSafe: yaw = toDegrees(atan2(-x, z))
        // FO FacingLogic: yaw = toDegrees(atan2(-dx, dz)) —— 移植对齐验证
        double x = 3.0, y = 1.0, z = -2.0;
        float expected = (float) Math.toDegrees(Math.atan2(-x, z));
        float[] fp = FacingLogic.yawPitchTo(x, y, z, 0, 0, 0);
        assertEquals(expected, fp[0], 0.01f);
    }

    @Test
    void pitchMatchesYingFormula() {
        // Ying ElytraCollector: pitch = -toDegrees(atan2(dy, sqrt(dx^2+dz^2)))
        double dx = 3.0, dy = 2.0, dz = -2.0;
        float expected = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float[] fp = FacingLogic.yawPitchTo(dx, dy, dz, 0, 0, 0);
        assertEquals(expected, fp[1], 0.01f);
    }

    // ========== 六面检测（V4.16）：开盒/交互不再固定 UP，按玩家视线选正对的面 ==========

    // 玩家在盒子正东 → 选 EAST（5）
    @Test
    void bestFace_east() {
        assertEquals(5, FacingLogic.bestFaceIndex(10, 2, 0, 0, 1, 0));
    }

    // 玩家在盒子正西 → 选 WEST（4）
    @Test
    void bestFace_west() {
        assertEquals(4, FacingLogic.bestFaceIndex(-10, 2, 0, 0, 1, 0));
    }

    // 玩家在盒子正南 → 选 SOUTH（3）
    @Test
    void bestFace_south() {
        assertEquals(3, FacingLogic.bestFaceIndex(0, 2, 10, 0, 1, 0));
    }

    // 玩家在盒子正北 → 选 NORTH（2）
    @Test
    void bestFace_north() {
        assertEquals(2, FacingLogic.bestFaceIndex(0, 2, -10, 0, 1, 0));
    }

    // 玩家在盒子正上方（顶面被盖住时玩家俯视盒子）→ 选 UP（1）
    @Test
    void bestFace_up() {
        assertEquals(1, FacingLogic.bestFaceIndex(0, 8, 0, 0, 1, 0));
    }

    // 玩家在盒子正下方（盒子在坑底/脚下）→ 选 DOWN（0）
    @Test
    void bestFace_down() {
        assertEquals(0, FacingLogic.bestFaceIndex(0, -5, 0, 0, 1, 0));
    }

    // 玩家站盒子侧面（眼睛略高于盒子中心，dy 小）→ 必须选水平面而不是 UP
    @Test
    void bestFace_sideBeatsUp() {
        // 眼睛 (3, 2, 0) vs 盒子中心 (0, 1, 0)：dx=3 主导 → EAST
        assertEquals(5, FacingLogic.bestFaceIndex(3, 2, 0, 0, 1, 0));
    }

    // 斜向站位：dx 与 dz 接近时，按水平主导轴选（这里 dx=-4 > dz=3 → WEST）
    @Test
    void bestFace_diagonalPrefersDominantHorizontalAxis() {
        assertEquals(4, FacingLogic.bestFaceIndex(-4, 2, 3, 0, 1, 0));
    }

    // 返回面 ID 与 Direction.ID 语义一致（0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST）
    @Test
    void bestFace_idsMatchDirectionIds() {
        assertEquals(1, FacingLogic.bestFaceIndex(0, 9, 0, 0, 1, 0)); // UP
        assertEquals(0, FacingLogic.bestFaceIndex(0, -9, 0, 0, 1, 0)); // DOWN
        assertEquals(5, FacingLogic.bestFaceIndex(9, 2, 0, 0, 1, 0)); // EAST
        assertEquals(4, FacingLogic.bestFaceIndex(-9, 2, 0, 0, 1, 0)); // WEST
        assertEquals(3, FacingLogic.bestFaceIndex(0, 2, 9, 0, 1, 0)); // SOUTH
        assertEquals(2, FacingLogic.bestFaceIndex(0, 2, -9, 0, 1, 0)); // NORTH
    }
}
