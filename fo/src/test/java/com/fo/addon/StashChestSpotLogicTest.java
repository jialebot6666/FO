package com.fo.addon;

import com.fo.addon.utils.StashChestSpotLogic;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 搬运专用落脚点逻辑的单测（V6.7p25）。 */
public class StashChestSpotLogicTest {

    @Test
    void 候选_不含箱子自身那一列_也不含箱子正下方() {
        List<int[]> cands = StashChestSpotLogic.candidates(100, 64, 200);
        assertFalse(cands.isEmpty());
        for (int[] c : cands) {
            assertFalse(c[0] == 100 && c[2] == 200,
                "箱子自身那一列（含正上方）绝不能当落脚点");
            assertTrue(c[1] == 64 || c[1] == 65,
                "Y 只允许 chestY / chestY+1，实际 " + c[1]);
        }
    }

    @Test
    void 候选_按圈外扩且总数正确() {
        List<int[]> cands = StashChestSpotLogic.candidates(0, 64, 0);
        // 每圈 8r 个位置 × 2 层：r=1/2/3 → 16+32+48 = 96
        assertEquals(96, cands.size());

        // 前 16 个必须是最内圈（水平切比雪夫距离 = 1）
        for (int i = 0; i < 16; i++) {
            int[] c = cands.get(i);
            assertEquals(1, Math.max(Math.abs(c[0]), Math.abs(c[2])), "前 16 个应是最内圈");
        }
        // 最后一个必须是 3 圈
        int[] last = cands.get(cands.size() - 1);
        assertEquals(3, Math.max(Math.abs(last[0]), Math.abs(last[2])));
    }

    @Test
    void 候选_没有重复坐标() {
        List<int[]> cands = StashChestSpotLogic.candidates(5, 70, -3);
        Set<String> seen = new HashSet<>();
        for (int[] c : cands) {
            assertTrue(seen.add(c[0] + "," + c[1] + "," + c[2]), "候选不应重复");
        }
    }

    @Test
    void 评分_有视线压过距离() {
        double farButVisible = StashChestSpotLogic.score(true, 400.0, 1);   // 20 格外，有视线
        double nearButBlind = StashChestSpotLogic.score(false, 1.0, 0);     // 贴脸，但没视线
        assertTrue(farButVisible < nearButBlind, "用户口径：优先有视线的箱子");
    }

    @Test
    void 评分_同视线下近的优先() {
        double near = StashChestSpotLogic.score(true, 4.0, 0);
        double far = StashChestSpotLogic.score(true, 36.0, 0);
        assertTrue(near < far);
    }

    @Test
    void 评分_同样的距离同层优先于上一层() {
        double sameLevel = StashChestSpotLogic.score(true, 9.0, 0);
        double oneUp = StashChestSpotLogic.score(true, 9.0, 1);
        assertTrue(sameLevel < oneUp, "能不往上爬就不往上爬");
    }
}
