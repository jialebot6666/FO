package com.fo.addon;

import com.fo.addon.utils.StashMoveLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分区判定 / 传送检测 / 双箱配对（V6.7 自动搬运三号模式）。 */
class StashMoveLogicTest {

    @Test
    void 离待搬运箱近判待搬运箱区() {
        assertEquals(StashMoveLogic.Zone.SOURCE,
            StashMoveLogic.nearerZone(0, 0, 10, 10, 100, 100));
    }

    @Test
    void 离存储箱近判存储箱区() {
        assertEquals(StashMoveLogic.Zone.STORE,
            StashMoveLogic.nearerZone(0, 0, 100, 100, 10, 10));
    }

    @Test
    void 等距判待搬运箱区() {
        assertEquals(StashMoveLogic.Zone.SOURCE,
            StashMoveLogic.nearerZone(0, 0, 10, 0, -10, 0));
    }

    @Test
    void 只有一个坐标时判到已知那侧() {
        assertEquals(StashMoveLogic.Zone.STORE,
            StashMoveLogic.nearerZone(0, 0, Double.NaN, Double.NaN, 5, 5));
        assertEquals(StashMoveLogic.Zone.SOURCE,
            StashMoveLogic.nearerZone(0, 0, 5, 5, Double.NaN, Double.NaN));
        assertEquals(StashMoveLogic.Zone.UNKNOWN,
            StashMoveLogic.nearerZone(0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    }

    @Test
    void 位置突跳判定被拉走() {
        // 一 tick 内位移 40 格 > 阈值 32
        assertTrue(StashMoveLogic.teleportJumped(0, 0, 40, 0, 32));
        // 位移 10 格不算
        assertFalse(StashMoveLogic.teleportJumped(0, 0, 10, 0, 32));
        // 阈值边界：正好 32 格不算（严格大于）
        assertFalse(StashMoveLogic.teleportJumped(0, 0, 32, 0, 32));
    }

    @Test
    void 双箱另一半_左半顺时针() {
        // 朝北(0,-1) 的左半 → 顺时针 → 东(+1,0)（与原版 ChestBlock 字节码一致）
        int[] off = StashMoveLogic.chestOtherHalfOffset(true, 0, -1);
        assertEquals(1, off[0]);
        assertEquals(0, off[1]);
    }

    @Test
    void 双箱另一半_右半逆时针() {
        // 朝北(0,-1) 的右半 → 逆时针 → 西(-1,0)
        int[] off = StashMoveLogic.chestOtherHalfOffset(false, 0, -1);
        assertEquals(-1, off[0]);
        assertEquals(0, off[1]);
    }

    @Test
    void 双箱配对四个朝向都自洽() {
        // 朝向 (dx,dz)：北(0,-1) 东(1,0) 南(0,1) 西(-1,0)
        int[][] facings = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] f : facings) {
            int[] left = StashMoveLogic.chestOtherHalfOffset(true, f[0], f[1]);
            int[] right = StashMoveLogic.chestOtherHalfOffset(false, f[0], f[1]);
            // 左右两半互为相反方向
            assertEquals(-left[0], right[0]);
            assertEquals(-left[1], right[1]);
            // 偏移必须落在轴向上（不能是斜对角）
            assertEquals(1, Math.abs(left[0]) + Math.abs(left[1]));
        }
    }

    @Test
    void 区名中文用于界面() {
        assertEquals("待搬运箱区", StashMoveLogic.Zone.SOURCE.toString());
        assertEquals("存储箱区", StashMoveLogic.Zone.STORE.toString());
        assertEquals("未判定", StashMoveLogic.Zone.UNKNOWN.toString());
    }

    // ==================== 卸货过滤（V6.7p1 修复） ====================

    @Test
    void 末影珍珠任何时候都不入库() {
        assertFalse(StashMoveLogic.shouldUnload("minecraft:ender_pearl", false));
        assertFalse(StashMoveLogic.shouldUnload("minecraft:ender_pearl", true));
    }

    @Test
    void 只拿潜影盒时只存潜影盒() {
        assertTrue(StashMoveLogic.shouldUnload("minecraft:shulker_box", true));
        assertTrue(StashMoveLogic.shouldUnload("minecraft:red_shulker_box", true));
        assertFalse(StashMoveLogic.shouldUnload("minecraft:diamond", true));
        assertFalse(StashMoveLogic.shouldUnload("minecraft:golden_carrot", true));
    }

    @Test
    void 未开只拿潜影盒时非珍珠物品都存() {
        assertTrue(StashMoveLogic.shouldUnload("minecraft:diamond", false));
        assertTrue(StashMoveLogic.shouldUnload("minecraft:shulker_box", false));
    }

    @Test
    void 空ID不卸() {
        assertFalse(StashMoveLogic.shouldUnload(null, false));
    }

    // ==================== 容器识别 / 停滞判定（V6.7p3） ====================

    @Test
    void 箱子与潜影盒都算容器() {
        assertTrue(StashMoveLogic.isSupportedContainer(true, false));
        assertTrue(StashMoveLogic.isSupportedContainer(false, true));
        assertTrue(StashMoveLogic.isSupportedContainer(true, true));
        assertFalse(StashMoveLogic.isSupportedContainer(false, false));
    }

    @Test
    void 走不动判定() {
        // 位移 0.01 格（被边沿挡住，几乎不动）→ 停滞
        assertTrue(StashMoveLogic.isStalled(0, 0, 0.01, 0, 0.05));
        // 位移 0.06 格（还在走）→ 不算
        assertFalse(StashMoveLogic.isStalled(0, 0, 0.06, 0, 0.05));
        // 位移 1 格 → 不算
        assertFalse(StashMoveLogic.isStalled(0, 0, 1, 0, 0.05));
        // 斜向位移 0.04/0.02（合计约 0.045）→ 仍算停滞
        assertTrue(StashMoveLogic.isStalled(0, 0, 0.04, 0.02, 0.05));
    }

    @Test
    void 后退距离判定() {
        // 离气泡柱中心不到 2.5 格 → 继续退
        assertTrue(StashMoveLogic.needMoreBackoff(1.0, 2.5));
        assertTrue(StashMoveLogic.needMoreBackoff(2.49, 2.5));
        // 已经 2.5 格及以上 → 可以停
        assertFalse(StashMoveLogic.needMoreBackoff(2.5, 2.5));
        assertFalse(StashMoveLogic.needMoreBackoff(4.0, 2.5));
    }

    // ==================== 点击选面（V6.7p5） ====================

    @Test
    void 站斜上方优先点侧面() {
        // 眼睛在方块东侧 +X 2 格、高 2 格：水平 2 >= 2*0.6 → 选东面
        assertEquals(StashMoveLogic.FACE_EAST, StashMoveLogic.pickClickFace(2, 2, 0, false));
        assertEquals(StashMoveLogic.FACE_WEST, StashMoveLogic.pickClickFace(-2, 2, 0, false));
        assertEquals(StashMoveLogic.FACE_SOUTH, StashMoveLogic.pickClickFace(0, 2, 2, false));
        assertEquals(StashMoveLogic.FACE_NORTH, StashMoveLogic.pickClickFace(0, 2, -2, false));
    }

    @Test
    void 顶面被漏斗挡住时不选顶面() {
        // 正上方、顶面空 → 可以点顶面
        assertEquals(StashMoveLogic.FACE_UP, StashMoveLogic.pickClickFace(0, 2, 0, false));
        // 正上方但顶上被漏斗占住 → 必须走侧面（旧实现会选顶面然后点不动）
        int face = StashMoveLogic.pickClickFace(0, 2, 0, true);
        assertNotEquals(StashMoveLogic.FACE_UP, face);
    }

    @Test
    void 站在正下方永远不选底面() {
        // 眼睛低于方块中心：水平 0、dy 为负 → 只能侧面
        int face = StashMoveLogic.pickClickFace(0.2, -2, 0.1, false);
        assertNotEquals(StashMoveLogic.FACE_UP, face);
    }

    @Test
    void 水平方向取偏移更大的轴() {
        // X 偏移更大 → 东/西
        assertEquals(StashMoveLogic.FACE_EAST, StashMoveLogic.pickClickFace(3, 0.5, 1, false));
        // Z 偏移更大 → 南/北
        assertEquals(StashMoveLogic.FACE_SOUTH, StashMoveLogic.pickClickFace(0.5, 0.5, 3, false));
    }
}
