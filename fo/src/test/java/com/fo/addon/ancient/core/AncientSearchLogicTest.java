package com.fo.addon.ancient.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class AncientSearchLogicTest {

    @Test
    void 数字种子直接解析() {
        assertEquals(-7346913998703726680L, AncientSearchLogic.parseSeedInput("-7346913998703726680"));
        assertEquals(12345L, AncientSearchLogic.parseSeedInput("12345"));
        assertEquals(0L, AncientSearchLogic.parseSeedInput("0"));
    }

    @Test
    void 非数字种子回退hashCode() {
        assertEquals("abc".hashCode(), AncientSearchLogic.parseSeedInput("abc"));
        assertEquals("我的世界".hashCode(), AncientSearchLogic.parseSeedInput("我的世界"));
    }

    @Test
    void 区域半径按除24加1() {
        // 与 misaka 真实实现一致：半径 / 24 + 1（旧源码 /25 是错误）
        assertEquals(1000 / 24 + 1, AncientSearchLogic.regionRadius(1000));
        assertEquals(1000 / 24 + 1, AncientSearchLogic.regionRadius(1000));
        assertEquals(24 / 24 + 1, AncientSearchLogic.regionRadius(24));
        assertEquals(25 / 24 + 1, AncientSearchLogic.regionRadius(25));
    }

    @Test
    void 枚举中文标签() {
        assertEquals("附魔金苹果", LootTarget.ENCHANTED_GOLDEN_APPLE.toString());
        assertEquals("迅捷潜行3", LootTarget.SWIFT_SNEAK_3.toString());
        assertEquals("两者都搜索", LootTarget.BOTH.toString());
        assertEquals("< 26.1", GameVersionOption.BELOW_26_1.toString());
        assertEquals(">= 26.1", GameVersionOption.FROM_26_1.toString());
    }

    @Test
    void 搜索结果按距离排序() {
        List<FoundChest> list = new ArrayList<>();
        list.add(new FoundChest(null, 500.0, "附魔金"));
        list.add(new FoundChest(null, 12.0, "迅捷3"));
        list.add(new FoundChest(null, 300.0, "两者"));
        list.sort(Comparator.comparingDouble(fc -> fc.distance));
        assertEquals(12.0, list.get(0).distance);
        assertEquals(300.0, list.get(1).distance);
        assertEquals(500.0, list.get(2).distance);
    }

    @Test
    void foundChest字段完整() {
        FoundChest fc = new FoundChest(null, 42.5, "附魔金");
        assertEquals(42.5, fc.distance);
        assertEquals("附魔金", fc.label);
    }

    // ---- 坐标空间回归锁：方块坐标与区块坐标不得混用 ----

    @Test
    void 方块转区块向下取整且负数正确() {
        assertEquals(0, AncientSearchLogic.blockToChunk(0));
        assertEquals(15 >> 4, AncientSearchLogic.blockToChunk(15));
        assertEquals(1, AncientSearchLogic.blockToChunk(16));
        // 算术右移对负数向负无穷取整，和 Minecraft 的区块归属一致
        assertEquals(-1, AncientSearchLogic.blockToChunk(-1));
        assertEquals(-1, AncientSearchLogic.blockToChunk(-16));
        assertEquals(-2, AncientSearchLogic.blockToChunk(-17));
    }

    @Test
    void 古城与玩家距离按区块单位计算() {
        assertEquals(500.0, AncientSearchLogic.chunkDistance(500, 0, 0, 0), 1e-9);
        assertEquals(5.0, AncientSearchLogic.chunkDistance(3, 4, 0, 0), 1e-9);
        assertEquals(0.0, AncientSearchLogic.chunkDistance(-7, 9, -7, 9), 1e-9);
    }

    @Test
    void 半径内的古城必须被判定为命中() {
        // 半径单位是区块：距玩家 500 区块的古城，在 1000 区块半径内应当被收。
        // 修复前这里用的是「方块坐标差 vs 区块半径」，500 区块会被算成 8000 > 1000 而误判超圈。
        assertTrue(withinSearch(500, 0, 1000));
        assertTrue(withinSearch(-499, 300, 1000));
        // 边界：正好等于半径算在内，超出则排除（与模块中 dist > radius 才跳过的写法一致）
        assertTrue(withinSearch(1000, 0, 1000));
        assertFalse(withinSearch(1001, 0, 1000));
    }

    @Test
    void 方块距离判定会误杀所以必须走区块() {
        int cityChunkX = 500;
        int blockX = (cityChunkX << 4) + 8;          // 方块坐标只服务于 isViableAncientCity
        // 旧写法拿方块坐标差当距离：玩家在同区块时也有 8000，远大于 1000 的半径 → 误判超圈
        assertEquals(8000.0, Math.abs(blockX - 8), 1e-9);
        // 新写法：区块距离 500 <= 1000 → 正确命中
        assertTrue(withinSearch(cityChunkX, 0, 1000));
        // 换算可逆：方块坐标转回区块应与原值一致
        assertEquals(cityChunkX, AncientSearchLogic.blockToChunk(blockX));
    }

    /** 复刻模块的圈内判定：距离（区块单位）不超过半径即算命中。 */
    private static boolean withinSearch(int cityChunkX, int cityChunkZ, int radiusChunks) {
        return AncientSearchLogic.chunkDistance(cityChunkX, cityChunkZ, 0, 0) <= radiusChunks;
    }
}
