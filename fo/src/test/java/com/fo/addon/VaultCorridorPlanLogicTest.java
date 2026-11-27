package com.fo.addon;

import com.fo.addon.vault.VaultCorridorPlanLogic;
import com.fo.addon.vault.VaultCorridorPlanLogic.Route;
import com.fo.addon.vault.VaultCorridorPlanLogic.Step;
import com.fo.addon.vault.VaultCorridorPlanLogic.Vertical;
import com.fo.addon.vault.VaultCorridorPlanLogic.VerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 宝库连廊路线规划测试（7.0p1 新增）。
 *
 * <p>锁死五条行为：
 * ① 最近目标按三维距离选，跳过已连 / 已放弃，半径外不选；
 * ② 路线是 L 形、先长边后短边，同长时固定先 X；
 * ③ 停靠点 = 保护距离 + 1，另一轴与宝库对齐；
 * ④ 高度差 0 平走、±1~16 台阶、超过 16 不支持；
 * ⑤ 台阶逐格 45°、均匀摊开、最后一定落在终点高度。
 */
public class VaultCorridorPlanLogicTest {

    @Test
    void selectNearestBy3DDistance() {
        List<int[]> vaults = List.of(
                new int[]{10, 64, 0},
                new int[]{0, 64, 6},
                new int[]{0, 70, 0});
        // 玩家在原点：三维距离分别是 10、6、6 → 后两个并列，取先出现的下标 1
        assertEquals(1, VaultCorridorPlanLogic.selectNearest(vaults, 0, 64, 0, 500, Set.of()));
        // 玩家抬到 y=70：到 (0,70,0) 距离 0，应选下标 2
        assertEquals(2, VaultCorridorPlanLogic.selectNearest(vaults, 0, 70, 0, 500, Set.of()));
    }

    @Test
    void selectNearestSkipsExcluded() {
        List<int[]> vaults = List.of(new int[]{5, 64, 0}, new int[]{9, 64, 0});
        assertEquals(1, VaultCorridorPlanLogic.selectNearest(vaults, 0, 64, 0, 500, Set.of(0)),
                "最近的已连，应顺延到下一个");
        assertEquals(-1, VaultCorridorPlanLogic.selectNearest(vaults, 0, 64, 0, 500, Set.of(0, 1)),
                "全被排除 → 没有目标");
    }

    @Test
    void selectNearestRespectsRadius() {
        List<int[]> vaults = List.of(new int[]{600, 64, 0}, new int[]{100, 64, 0});
        assertEquals(1, VaultCorridorPlanLogic.selectNearest(vaults, 0, 64, 0, 500, Set.of()),
                "600 格超出半径 500，只能选 100");
        assertEquals(-1, VaultCorridorPlanLogic.selectNearest(vaults, 0, 64, 0, 50, Set.of()),
                "两个都超出半径 → 没有目标");
    }

    @Test
    void selectNearestHandlesEmptyAndNull() {
        assertEquals(-1, VaultCorridorPlanLogic.selectNearest(List.of(), 0, 64, 0, 500, Set.of()));
        assertEquals(-1, VaultCorridorPlanLogic.selectNearest(null, 0, 64, 0, 500, null));
    }

    @Test
    void lShapeLongAxisFirstX() {
        Route route = VaultCorridorPlanLogic.lShape(0, 0, 10, 3);
        assertEquals(10, route.cornerX(), "X 是长边，先走 X");
        assertEquals(0, route.cornerZ());
        assertEquals(13, route.length());
    }

    @Test
    void lShapeLongAxisFirstZ() {
        Route route = VaultCorridorPlanLogic.lShape(0, 0, 3, 10);
        assertTrue(Math.abs(route.endZ() - route.startZ()) > Math.abs(route.endX() - route.startX()));
        assertEquals(0, route.cornerX(), "Z 是长边，先走 Z");
        assertEquals(10, route.cornerZ());
        assertEquals(13, route.length());
    }

    @Test
    void lShapeDegenerateWhenOneAxisZero() {
        Route route = VaultCorridorPlanLogic.lShape(0, 0, 0, 8);
        assertEquals(0, route.cornerX());
        assertEquals(8, route.cornerZ());
        assertEquals(8, route.length());
        assertEquals(8, route.endZ());
    }

    @Test
    void endpointStopsWallOneOutside() {
        // 宝库 (100, z=20)，玩家从 x=0 来，保护 1 → 停在 x=98，z 对齐 20
        int[] end = VaultCorridorPlanLogic.endpoint(100, 20, 0, 30, 1);
        assertEquals(98, end[0], "停靠点 = 宝库 - (保护 1 + 1)");
        assertEquals(20, end[1], "另一轴对齐宝库");
    }

    @Test
    void endpointAlongZAxis() {
        // 玩家主要在 Z 方向接近（dz 大于 dx）→ 主轴走 Z
        int[] end = VaultCorridorPlanLogic.endpoint(10, 50, 20, 0, 1);
        assertEquals(10, end[0]);
        assertEquals(48, end[1]);
    }

    @Test
    void endpointMatchesArrivalRule() {
        int vaultX = 100;
        int endX = com.fo.addon.vault.VaultGuardLogic.endpointCoord(vaultX, 0, 1);
        assertTrue(com.fo.addon.vault.VaultGuardLogic.isArrival(endX, 20, vaultX, 20, 1),
                "规划出来的终点必须同时满足抵达判定");
        int end4 = com.fo.addon.vault.VaultGuardLogic.endpointCoord(vaultX, 0, 4);
        assertTrue(com.fo.addon.vault.VaultGuardLogic.isArrival(end4, 20, vaultX, 20, 4),
                "保护 4 时同样成立");
    }

    @Test
    void verticalFlatOnlyWhenSameLevel() {
        Vertical flat = VaultCorridorPlanLogic.vertical(64, 64);
        assertEquals(VerticalMode.FLAT, flat.mode());
        assertEquals(0, flat.steps());
        assertTrue(flat.supported());
    }

    @Test
    void verticalStairsUpAndDown() {
        Vertical up = VaultCorridorPlanLogic.vertical(64, 70);
        assertEquals(VerticalMode.STAIRS_UP, up.mode());
        assertEquals(6, up.steps());
        Vertical down = VaultCorridorPlanLogic.vertical(70, 64);
        assertEquals(VerticalMode.STAIRS_DOWN, down.mode());
        assertEquals(6, down.steps());
    }

    @Test
    void verticalSupportsUpTo16() {
        Vertical up16 = VaultCorridorPlanLogic.vertical(64, 80);
        assertEquals(VerticalMode.STAIRS_UP, up16.mode(), "±16 是 p1 上限，必须支持");
        assertTrue(up16.supported());
        Vertical up17 = VaultCorridorPlanLogic.vertical(64, 81);
        assertEquals(VerticalMode.UNSUPPORTED, up17.mode(), "超过 16 格判不支持");
        assertFalse(up17.supported());
        Vertical down20 = VaultCorridorPlanLogic.vertical(90, 70);
        assertEquals(VerticalMode.UNSUPPORTED, down20.mode());
    }

    @Test
    void stairStepsAscendEveryBlock() {
        Route route = VaultCorridorPlanLogic.lShape(0, 0, 6, 0);
        List<Step> steps = VaultCorridorPlanLogic.stairSteps(route, 64, 70);
        assertEquals(7, steps.size(), "6 格水平距离 → 7 个落脚点");
        assertEquals(64, steps.get(0).footY());
        for (int i = 1; i < steps.size(); i++) {
            int dy = steps.get(i).footY() - steps.get(i - 1).footY();
            assertTrue(dy >= 0 && dy <= 1, "每格最多抬 1 格，不能跳");
        }
        assertEquals(70, steps.get(steps.size() - 1).footY(), "最后一定落在终点高度");
        assertEquals(6, steps.get(steps.size() - 1).x());
    }

    @Test
    void stairStepsDescendEveryBlock() {
        Route route = VaultCorridorPlanLogic.lShape(0, 0, 0, 3);
        List<Step> steps = VaultCorridorPlanLogic.stairSteps(route, 70, 67);
        assertEquals(4, steps.size());
        assertEquals(67, steps.get(steps.size() - 1).footY());
        for (int i = 1; i < steps.size(); i++) {
            int dy = steps.get(i).footY() - steps.get(i - 1).footY();
            assertTrue(dy <= 0 && dy >= -1, "下降同样每格最多 1 格");
        }
        assertEquals(3, steps.get(steps.size() - 1).z(), "Z 轴走到位");
    }

    @Test
    void stairStepsSpreadWhenSlopeShallow() {
        // 9 格水平、只抬 3 格：均匀摊开，不能一口气抬完再平推
        Route route = VaultCorridorPlanLogic.lShape(0, 0, 9, 0);
        List<Step> steps = VaultCorridorPlanLogic.stairSteps(route, 64, 67);
        assertEquals(10, steps.size());
        assertEquals(64, steps.get(0).footY());
        assertEquals(67, steps.get(9).footY());
        int last = 64;
        for (Step s : steps) {
            assertTrue(s.footY() >= last, "整体只升不降");
            last = s.footY();
        }
        // 第 3 格时应该已经抬了一格（摊开而不是最后 3 格才抬）
        assertEquals(65, steps.get(3).footY());
        assertEquals(66, steps.get(6).footY());
    }

    @Test
    void stairStepsZeroLengthJumpsToEndHeight() {
        Route route = VaultCorridorPlanLogic.lShape(5, 5, 5, 5);
        List<Step> steps = VaultCorridorPlanLogic.stairSteps(route, 64, 66);
        assertEquals(1, steps.size());
        assertEquals(66, steps.get(0).footY());
        assertEquals(5, steps.get(0).x());
        assertEquals(5, steps.get(0).z());
    }

    @Test
    void cornerAndHeadroomSizes() {
        assertEquals(8, VaultCorridorPlanLogic.cornerVolume(), "拐角清 2×2×2 = 8 格");
        assertEquals(1, VaultCorridorPlanLogic.stepHeadroom(), "台阶每步多清 1 层头");
    }
}
