package com.fo.addon;

import com.fo.addon.elytra.modules.AutoElytraFlight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 补给降落转向与可达性判定单测。
 * 方案 B（对齐 RustElytraClient）：降落时每 tick 水平转向降落点、pitch 锁 0，直线滑翔不绕圈；
 * 可达性不足时放弃降落，且必须清理未结束的补给任务（防"第二次补给发现第一次未停止"）。
 */
class ElytraFlightLandingTest {

    // ===== landingYawTo：水平转向降落点 =====

    @Test
    void yawFacesTargetFromEast() {
        // 玩家在目标正东 → 应转向西（MC yaw +90° = 西/-X）
        float yaw = AutoElytraFlight.landingYawTo(100, 100, 200, 100);
        assertEquals(90.0f, yaw, 0.01f);
    }

    @Test
    void yawFacesTargetFromSouth() {
        // 玩家在目标正南 → 应转向北（MC yaw ±180°，负零取负时返回 -180° 属同一朝向）
        float yaw = AutoElytraFlight.landingYawTo(100, 100, 100, 200);
        assertTrue(Math.abs(Math.abs(yaw) - 180.0f) < 0.01f);
    }

    @Test
    void yawFacesTargetFromNorth() {
        // 玩家在目标正北 → 应转向南（MC yaw 0°）
        float yaw = AutoElytraFlight.landingYawTo(100, 100, 100, 0);
        assertEquals(0.0f, yaw, 0.01f);
    }

    @Test
    void yawFacesTargetFromWest() {
        // 玩家在目标正西 → 应转向东（MC yaw -90° = 东/+X）
        float yaw = AutoElytraFlight.landingYawTo(100, 100, 0, 100);
        assertEquals(-90.0f, yaw, 0.01f);
    }

    @Test
    void yawFacesTargetDiagonal() {
        // 玩家在目标东南 → 应转向西北（135°）
        float yaw = AutoElytraFlight.landingYawTo(100, 100, 200, 200);
        assertEquals(135.0f, yaw, 0.01f);
    }

    @Test
    void yawZeroWhenAlreadyAtTarget() {
        float yaw = AutoElytraFlight.landingYawTo(100, 100, 100, 100);
        assertEquals(0.0f, yaw, 0.01f);
    }

    // ===== glideReachable：滑翔高度是否足以到达降落点 =====

    @Test
    void reachableWhenRatioLow() {
        // 平距 500 / 高度 100 = 5 ≤ 10 → 可达，不放弃
        assertTrue(AutoElytraFlight.glideReachable(500, 100));
        assertTrue(AutoElytraFlight.glideReachable(1000, 100));
    }

    @Test
    void unreachableWhenRatioHigh() {
        // 平距 2000 / 高度 100 = 20 > 10 → 不可达，放弃降落并清理补给
        assertFalse(AutoElytraFlight.glideReachable(2000, 100));
        assertFalse(AutoElytraFlight.glideReachable(1500, 50));
    }

    @Test
    void reachableAtLowAltitudeAlways() {
        // 离地 ≤ 4 格一律可达（反正要落地了）
        assertTrue(AutoElytraFlight.glideReachable(5000, 4));
        assertTrue(AutoElytraFlight.glideReachable(5000, 0));
        assertTrue(AutoElytraFlight.glideReachable(5000, -10));
    }
}
