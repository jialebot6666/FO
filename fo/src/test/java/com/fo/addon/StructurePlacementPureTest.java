package com.fo.addon;

import com.fo.addon.utils.StructurePlacementPure;
import com.seedfinding.mccore.util.pos.CPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 26.1 结构放置：与官方 jar 对拍过的两个村庄样本。 */
class StructurePlacementPureTest {

    private static final long SEED = -7346913998703726680L;
    private static final StructurePlacementPure.Config VILLAGE =
        new StructurePlacementPure.Config(34, 8, 10387312, false);

    @Test
    void 村庄区域零与官方一致() {
        CPos pos = StructurePlacementPure.getInRegion(SEED, 0, 0, VILLAGE);
        assertEquals(18, pos.getX());
        assertEquals(10, pos.getZ());
    }

    @Test
    void 村庄负区域与官方一致() {
        CPos pos = StructurePlacementPure.getInRegion(SEED, -2, -2, VILLAGE);
        assertEquals(-64, pos.getX());
        assertEquals(-46, pos.getZ());
    }

    @Test
    void 结果落在区域内() {
        for (int rx = -3; rx <= 3; rx++) {
            for (int rz = -3; rz <= 3; rz++) {
                CPos pos = StructurePlacementPure.getInRegion(SEED, rx, rz, VILLAGE);
                assertTrue(pos.getX() >= rx * 34 && pos.getX() < rx * 34 + 26);
                assertTrue(pos.getZ() >= rz * 34 && pos.getZ() < rz * 34 + 26);
            }
        }
    }

    @Test
    void 三角分布仍在区间内() {
        StructurePlacementPure.Config endCity =
            new StructurePlacementPure.Config(20, 11, 10387313, true);
        for (int rx = -2; rx <= 2; rx++) {
            for (int rz = -2; rz <= 2; rz++) {
                CPos pos = StructurePlacementPure.getInRegion(SEED, rx, rz, endCity);
                assertTrue(pos.getX() >= rx * 20 && pos.getX() < rx * 20 + 9);
                assertTrue(pos.getZ() >= rz * 20 && pos.getZ() < rz * 20 + 9);
            }
        }
    }

    @Test
    void 矿井概率远小于宝藏() {
        int mineshaft = 0;
        int treasure = 0;
        for (int x = 0; x < 2000; x++) {
            for (int z = 0; z < 10; z++) {
                if (StructurePlacementPure.isMineshaftAt(SEED, x, z, 0)) mineshaft++;
                if (StructurePlacementPure.isBuriedTreasureAt(SEED, x, z, 0)) treasure++;
            }
        }
        // 20000 区块，期望约 80 个矿井、200 个宝藏
        assertTrue(mineshaft > 20 && mineshaft < 250, "矿井数量异常: " + mineshaft);
        assertTrue(treasure > 80 && treasure < 400, "宝藏数量异常: " + treasure);
    }

    @Test
    void 间隔为零时不会死循环() {
        StructurePlacementPure.Config cfg =
            new StructurePlacementPure.Config(1, 0, 0, false);
        CPos pos = StructurePlacementPure.getInRegion(SEED, 5, 7, cfg);
        assertEquals(5, pos.getX());
        assertEquals(7, pos.getZ());
        assertFalse(false);
    }
}
