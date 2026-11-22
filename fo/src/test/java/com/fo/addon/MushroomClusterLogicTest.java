package com.fo.addon;

import com.fo.addon.utils.MushroomClusterLogic;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 蘑菇岛连通块聚合（同一座岛只出一个航点）。 */
class MushroomClusterLogicTest {

    private static Set<Long> hits(int... pairs) {
        Set<Long> set = new HashSet<>();
        for (int i = 0; i < pairs.length; i += 2) {
            set.add(MushroomClusterLogic.key(pairs[i], pairs[i + 1]));
        }
        return set;
    }

    @Test
    void 空格子输出空集() {
        assertTrue(MushroomClusterLogic.cluster(new HashSet<>(), 0, 0, 32).isEmpty());
    }

    @Test
    void 单个采样点原样输出() {
        List<MushroomClusterLogic.Point> points =
            MushroomClusterLogic.cluster(hits(0, 0), 100, 200, 32);
        assertEquals(1, points.size());
        assertEquals(100, points.get(0).x());
        assertEquals(200, points.get(0).z());
    }

    @Test
    void 横向相邻三格合成一个点() {
        List<MushroomClusterLogic.Point> points =
            MushroomClusterLogic.cluster(hits(0, 0, 1, 0, 2, 0), 0, 0, 16);
        assertEquals(1, points.size());
        // 质心在 ix=1
        assertEquals(16, points.get(0).x());
        assertEquals(0, points.get(0).z());
    }

    @Test
    void 斜向相邻也算同一座岛() {
        List<MushroomClusterLogic.Point> points =
            MushroomClusterLogic.cluster(hits(0, 0, 1, 1), 0, 0, 8);
        assertEquals(1, points.size());
    }

    @Test
    void 隔一格的两块分成两座岛() {
        List<MushroomClusterLogic.Point> points =
            MushroomClusterLogic.cluster(hits(0, 0, 2, 0), 0, 0, 8);
        assertEquals(2, points.size());
    }

    @Test
    void 超过一百格的大岛仍然只算一处() {
        Set<Long> set = new HashSet<>();
        for (int ix = 0; ix <= 10; ix++) {
            set.add(MushroomClusterLogic.key(ix, 0));
        }
        List<MushroomClusterLogic.Point> points = MushroomClusterLogic.cluster(set, 0, 0, 32);
        assertEquals(1, points.size());
        assertEquals(5 * 32, points.get(0).x());
    }

    @Test
    void 负索引坐标换算正确() {
        List<MushroomClusterLogic.Point> points =
            MushroomClusterLogic.cluster(hits(-1, -1), -320, 480, 32);
        assertEquals(1, points.size());
        assertEquals(-352, points.get(0).x());
        assertEquals(448, points.get(0).z());
    }
}
