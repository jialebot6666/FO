package com.fo.addon;

import com.fo.addon.utils.NukerSphericalLogic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SlimefunHelper MineBot SPHERICAL 偏移表防回归测试。
 */
public class NukerSphericalLogicTest {

    @Test
    void range1Has27PointsAndFirstIsOrigin() {
        List<int[]> offsets = NukerSphericalLogic.offsets(1);
        assertEquals(27, offsets.size());
        assertArrayEquals(new int[]{0, 0, 0}, offsets.get(0));
    }

    @Test
    void range4Has729Points() {
        assertEquals(729, NukerSphericalLogic.offsets(4).size());
    }

    @Test
    void offsetsSortedByDistanceAscending() {
        List<int[]> offsets = NukerSphericalLogic.offsets(3);
        long prev = -1;
        for (int[] v : offsets) {
            long dist = (long) v[0] * v[0] + (long) v[1] * v[1] + (long) v[2] * v[2];
            assertTrue(dist >= prev, "偏移表未按距离升序: " + v[0] + "," + v[1] + "," + v[2]);
            prev = dist;
        }
        // 第一个必须是中心，第二个必须是距离 1 的六个邻居之一
        assertArrayEquals(new int[]{0, 0, 0}, offsets.get(0));
        assertEquals(1, offsets.get(1)[0] * offsets.get(1)[0]
            + offsets.get(1)[1] * offsets.get(1)[1] + offsets.get(1)[2] * offsets.get(1)[2]);
    }

    @Test
    void allOffsetsWithinRange() {
        for (int[] v : NukerSphericalLogic.offsets(5)) {
            assertTrue(Math.abs(v[0]) <= 5 && Math.abs(v[1]) <= 5 && Math.abs(v[2]) <= 5);
        }
    }
}
