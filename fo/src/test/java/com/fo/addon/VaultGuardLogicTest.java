package com.fo.addon;

import com.fo.addon.vault.VaultGuardLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 宝库连廊保护判定测试（7.0p1 新增）。
 *
 * <p>锁死四条行为：
 * ① 宝库本体 / 试炼刷怪笼 / 容器 / 结构方块永不挖；
 * ② 保护距离按切比雪夫距离算（1 格就是整个 3×3×3 立方体，斜角也挡住）；
 * ③ 黑名单优先于保护区，方便日志区分原因；
 * ④ 停靠点 = 保护距离 + 1，默认 1 → 离宝库 2 格（墙外 1 格）。
 */
public class VaultGuardLogicTest {

    @Test
    void vaultBlockNeverMined() {
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:vault"), "普通宝库也不能挖");
        assertTrue(VaultGuardLogic.isForbiddenBlock("vault"), "不带命名空间也要认");
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:Vault") == false, "大小写不匹配按未命中处理");
    }

    @Test
    void trialSpawnerNeverMined() {
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:trial_spawner"));
    }

    @Test
    void containersNeverMined() {
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:chest"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:trapped_chest"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:barrel"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:dispenser"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:dropper"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:hopper"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:shulker_box"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:red_shulker_box"), "染色潜影盒也要认");
    }

    @Test
    void structureBlocksNeverMined() {
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:bedrock"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:barrier"));
        assertTrue(VaultGuardLogic.isForbiddenBlock("minecraft:reinforced_deepslate"));
    }

    @Test
    void ordinaryBlocksAllowed() {
        assertFalse(VaultGuardLogic.isForbiddenBlock("minecraft:stone"));
        assertFalse(VaultGuardLogic.isForbiddenBlock("minecraft:deepslate"));
        assertFalse(VaultGuardLogic.isForbiddenBlock("minecraft:tuff_bricks"));
        assertFalse(VaultGuardLogic.isForbiddenBlock("minecraft:copper_grate"));
        assertFalse(VaultGuardLogic.isForbiddenBlock(null));
        assertFalse(VaultGuardLogic.isForbiddenBlock(""));
    }

    @Test
    void protectionZoneIsChebyshevCube() {
        // 保护距离 1：相邻 26 格全挡，含三个轴都偏 1 的斜角
        assertTrue(VaultGuardLogic.inProtectionZone(1, 0, 0, 1));
        assertTrue(VaultGuardLogic.inProtectionZone(0, 1, 0, 1));
        assertTrue(VaultGuardLogic.inProtectionZone(0, 0, 1, 1));
        assertTrue(VaultGuardLogic.inProtectionZone(1, 1, 0, 1), "斜角擦墙必须挡住");
        assertTrue(VaultGuardLogic.inProtectionZone(1, 1, 1, 1), "体对角线也要挡住");
        assertTrue(VaultGuardLogic.inProtectionZone(0, 0, 0, 1), "宝库自己当然挡");
    }

    @Test
    void protectionZoneBoundary() {
        assertFalse(VaultGuardLogic.inProtectionZone(2, 0, 0, 1), "偏 2 格就该放行");
        assertFalse(VaultGuardLogic.inProtectionZone(2, 2, 2, 1));
        assertTrue(VaultGuardLogic.inProtectionZone(2, 0, 0, 2));
        assertFalse(VaultGuardLogic.inProtectionZone(3, 0, 0, 2));
    }

    @Test
    void protectionZoneCanBeDisabled() {
        assertFalse(VaultGuardLogic.inProtectionZone(0, 0, 0, 0));
        assertFalse(VaultGuardLogic.inProtectionZone(0, 0, 0, -1));
    }

    @Test
    void judgePrefersBlockBlacklist() {
        assertEquals(VaultGuardLogic.Verdict.BLOCK,
                VaultGuardLogic.judge("minecraft:chest", 50, 50, 50, 1), "黑名单优先，离得远也是 BLOCK");
        assertEquals(VaultGuardLogic.Verdict.ZONE,
                VaultGuardLogic.judge("minecraft:stone", 1, 0, 0, 1));
        assertEquals(VaultGuardLogic.Verdict.ALLOW,
                VaultGuardLogic.judge("minecraft:stone", 2, 0, 0, 1));
        assertEquals(VaultGuardLogic.Verdict.BLOCK,
                VaultGuardLogic.judge("minecraft:vault", 0, 0, 0, 1), "宝库本体永远 BLOCK");
    }

    @Test
    void arrivalIsProtectPlusOne() {
        assertTrue(VaultGuardLogic.isArrival(2, 0, 0, 0, 1), "保护 1 → 末端离宝库 2 格");
        assertFalse(VaultGuardLogic.isArrival(1, 0, 0, 0, 1), "离 1 格还在保护区里，不算抵达");
        assertFalse(VaultGuardLogic.isArrival(3, 0, 0, 0, 1), "还没走到");
        assertTrue(VaultGuardLogic.isArrival(0, 5, 0, 0, 4), "保护 4 → 末端离宝库 5 格");
        assertTrue(VaultGuardLogic.isArrival(-2, 0, 0, 0, 1), "负方向同样成立");
    }

    @Test
    void endpointCoordFollowsApproachDirection() {
        assertEquals(-2, VaultGuardLogic.endpointCoord(0, -10, 1), "从负方向来 → 停在 -2");
        assertEquals(2, VaultGuardLogic.endpointCoord(0, 10, 1), "从正方向来 → 停在 +2");
        assertEquals(5, VaultGuardLogic.endpointCoord(0, 0, 4), "同坐标按正方向");
        assertEquals(6, VaultGuardLogic.endpointCoord(0, 10, 5));
    }
}
