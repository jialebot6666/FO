package com.fo.addon;

import com.fo.addon.utils.AncientCityLocatorLogic;
import com.fo.addon.utils.AncientCityLocatorLogic.City;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 远古城市定位防回归测试（V4.16 新模块「FO 远古城宝箱预测」阶段 1）。
 *
 * <p>基准来源：zychen027/AncientCityLootPredictor 的纯 Python 定位实现
 * （其对 MC 反编译的 Jigsaw/RNG 复刻经 557/557 验证）。
 * 本类断言值取自该实现用种子 -7346913998703726680 + 中心 (12314,-9631) + 半径 200 区块
 * 的产出（222 座），Java 版已与之逐座全量对照通过（222/222）。
 */
public class AncientCityLocatorLogicTest {

    private static final long SEED = -7346913998703726680L;

    /** LCG 必须与 JDK 自带 java.util.Random 完全一致（后者与游戏 LegacyRandomSource 同源）。 */
    @Test
    void lcgMatchesJavaUtilRandom() {
        Random oracle = new Random(12345L);
        AncientCityLocatorLogic.Lcg lcg = new AncientCityLocatorLogic.Lcg(12345L);
        for (int i = 0; i < 200; i++) {
            assertEquals(oracle.nextInt(1000), lcg.nextInt(1000));
            assertEquals(oracle.nextLong(), lcg.nextLong());
            assertEquals(oracle.nextInt(16), lcg.nextInt(16));
        }
    }

    /** 区域定位：region(32,-25) 命中已知古城原点。 */
    @Test
    void locateInRegionKnown() {
        assertArrayEquals(new int[]{12464, -9520}, AncientCityLocatorLogic.locateInRegion(SEED, 32, -25));
    }

    /** 变体推导：旋转 0-3、起始件 1-3 与基准一致。 */
    @Test
    void variantKnown() {
        assertArrayEquals(new int[]{2, 2}, AncientCityLocatorLogic.variant(SEED, 12464, -9520));
        assertArrayEquals(new int[]{1, 1}, AncientCityLocatorLogic.variant(SEED, 0, 160));
    }

    /** 附近搜索数量与基准一致。 */
    @Test
    void findNearbyCount() {
        assertEquals(222, AncientCityLocatorLogic.findNearby(SEED, 12314, -9631, 200).size());
    }

    /** 精确距离最近前 5（Java 用平方距离，比 Python 的 int(sqrt) 截断更精确）。 */
    @Test
    void nearestFive() {
        List<City> list = AncientCityLocatorLogic.findNearby(SEED, 12314, -9631, 200);
        int[][] expect = {
            {12464, -9520, 2, 2},
            {12368, -9952, 1, 1},
            {11904, -9568, 3, 1},
            {12432, -9216, 1, 3},
            {12000, -9936, 2, 1},
        };
        for (int i = 0; i < expect.length; i++) {
            City c = list.get(i);
            assertEquals(expect[i][0], c.blockX(), "blockX#" + i);
            assertEquals(expect[i][1], c.blockZ(), "blockZ#" + i);
            assertEquals(expect[i][2], c.rotation(), "rotation#" + i);
            assertEquals(expect[i][3], c.startPiece(), "startPiece#" + i);
        }
    }

    /** 结果全部落在半径内，且按距离非降序。 */
    @Test
    void radiusAndOrdering() {
        List<City> list = AncientCityLocatorLogic.findNearby(SEED, 12314, -9631, 200);
        long maxSq = 200L * 16 * 200L * 16;
        for (int i = 0; i < list.size(); i++) {
            assertTrue(list.get(i).distanceSq(12314, -9631) <= maxSq, "超出半径 #" + i);
            if (i > 0) {
                assertTrue(list.get(i - 1).distanceSq(12314, -9631)
                    <= list.get(i).distanceSq(12314, -9631), "未按距离升序 #" + i);
            }
        }
    }

    /** 小半径也必须至少扫描中心所在的那圈区域，能命中中心处的城（验证 regionRange 下限）。 */
    @Test
    void smallRadiusHitsCenterCity() {
        List<City> list = AncientCityLocatorLogic.findNearby(SEED, 12464, -9520, 1);
        assertFalse(list.isEmpty());
        assertEquals(12464, list.get(0).blockX());
        assertEquals(-9520, list.get(0).blockZ());
    }

    /**
     * 半径语义锁定（V6.7 起生效）：半径单位是「区块」，但距离按「方块距离」算，
     * 基准点是<b>城市原点方块坐标</b>。
     *
     * <p>旧实现（V6.6 及以前）走的是另一套：距离按<b>区块</b>距离算，基准点是「城市原点 + 8」
     * （`(chunkX &lt;&lt; 4) + 8`）。两者在半径边界会差半个区块，所以这里把新语义钉死，
     * 避免以后被误「修」回旧行为。
     */
    @Test
    void 城市原点是区块起点而不是加八() {
        List<City> list = AncientCityLocatorLogic.findNearby(SEED, 12314, -9631, 200);
        assertFalse(list.isEmpty());
        for (City c : list) {
            assertEquals(c.chunkX() * 16, c.blockX(), "blockX 必须是区块起点（旧实现会再 +8）");
            assertEquals(c.chunkZ() * 16, c.blockZ(), "blockZ 必须是区块起点（旧实现会再 +8）");
            assertEquals(c.blockX() >> 4, c.chunkX(), "blockX 与 chunkX 必须自洽");
            assertEquals(c.blockZ() >> 4, c.chunkZ(), "blockZ 与 chunkZ 必须自洽");
        }
    }

    /** 半径按区块给出，但换算成方块后再判距离：半径 200 区块 = 3200 方块。 */
    @Test
    void 半径按区块换算成方块判距离() {
        int radiusChunks = 200;
        long maxSq = (long) radiusChunks * 16 * radiusChunks * 16;
        List<City> list = AncientCityLocatorLogic.findNearby(SEED, 12314, -9631, radiusChunks);
        assertEquals(222, list.size());
        for (City c : list) {
            assertTrue(c.distanceSq(12314, -9631) <= maxSq, "超圈的城不该出现在结果里");
        }

        // 新语义与旧语义的差别就在这里：方块距离 ≠「区块距离 × 16」。
        // 旧实现按区块距离判圈、基准点是「城市原点 + 8」，新实现按方块距离判圈、基准点是城市原点，
        // 两者在半径边界会差半个区块。若某天两者相等，说明语义被改回去了。
        City nearest = list.get(0);
        int centerChunkX = 12314 >> 4;
        int centerChunkZ = -9631 >> 4;
        double blockDist = Math.hypot((double) nearest.blockX() - 12314, (double) nearest.blockZ() + 9631);
        double chunkDistTimes16 = Math.hypot((double) (nearest.chunkX() - centerChunkX),
            (double) (nearest.chunkZ() - centerChunkZ)) * 16.0;
        assertTrue(Math.abs(blockDist - chunkDistTimes16) > 1e-6,
            "方块距离与「区块距离 × 16」相等了，半径语义可能被改回旧实现");
    }
}
