package com.fo.addon.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 远古城市定位（纯逻辑，可单测）。
 *
 * <p>复刻 cubiomes 的 getFeaturePos / getVariant（参考 zychen027/AncientCityLootPredictor 的
 * 纯 Python 实现，其对 MC 反编译的 Jigsaw/RNG 复刻经 557/557 验证）：
 * <ul>
 *   <li>每 24×24 区块为一个区域，每个区域恰好产生一座古城候选</li>
 *   <li>区域种子 = regX*341873128712 + regZ*132897987541 + worldSeed + 20083232，
 *       经 Java LCG 掷两次 nextInt(16) 得区域内偏移</li>
 *   <li>变体（旋转 + city_center_1/2/3 起始件）：setLargeFeatureSeed 后 nextInt(4) / 1+nextInt(3)</li>
 * </ul>
 *
 * <p>注意：不含生物群系（Deep Dark）验证——候选可能因群系不符而实际不生成，
 * 运行时需按实际方块核对（对齐 Python 原版行为，它同样不验证）。
 */
public final class AncientCityLocatorLogic {

    public static final long ANCIENT_CITY_SALT = 20083232L;
    public static final int REGION_SIZE = 24;   // 每区域 24×24 区块
    public static final int CHUNK_RANGE = 16;   // 区域内偏移 nextInt(16)

    private AncientCityLocatorLogic() {
    }

    /** 一座古城候选：原点方块坐标、旋转、起始件、原点区块坐标 */
    public record City(int blockX, int blockZ, int rotation, int startPiece, int chunkX, int chunkZ) {
        /** 与玩家位置的距离平方（排序用） */
        public long distanceSq(int px, int pz) {
            long dx = blockX - px;
            long dz = blockZ - pz;
            return dx * dx + dz * dz;
        }
    }

    /**
     * java.util.Random 精确复刻（48 位 LCG），与游戏 LegacyRandomSource 同源。
     * 仅供定位计算使用；拼图/掷骰阶段使用 MC 本体 RandomSource。
     */
    public static final class Lcg {
        private static final long MULT = 0x5DEECE66DL;
        private static final long ADD = 0xBL;
        private static final long MASK = (1L << 48) - 1;

        private long state;

        public Lcg(long seed) {
            setSeed(seed);
        }

        public void setSeed(long seed) {
            state = (seed ^ MULT) & MASK;
        }

        public int next(int bits) {
            state = (state * MULT + ADD) & MASK;
            return (int) (state >>> (48 - bits));
        }

        /** java.util.Random.nextInt(int) 精确复刻（含 2 的幂快速分支与 rejection 循环） */
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound 必须为正");
            int r = next(31);
            int m = bound - 1;
            if ((bound & m) == 0) {  // 2 的幂
                return (int) ((bound * (long) r) >> 31);
            }
            int u = r;
            while (true) {
                int val = u % bound;
                if (u - val + m < Integer.MAX_VALUE) return val;
                u = next(31);
            }
        }

        /** java.util.Random.nextLong() 精确复刻 */
        public long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }

        /** ChunkRandom.setLargeFeatureSeed 复刻（古城/要塞等大型结构变体推导用） */
        public void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
            setSeed(worldSeed);
            long xs = nextLong();
            long zs = nextLong();
            setSeed(xs * chunkX ^ zs * chunkZ ^ worldSeed);
        }
    }

    /** 区域 (regX, regZ) 内古城原点，返回 [blockX, blockZ] */
    public static int[] locateInRegion(long worldSeed, int regX, int regZ) {
        Lcg rng = new Lcg(0);
        // Java long 溢出自然回绕，与 Python 大整数 & 0xFFFFFFFFFFFFFFFF 模 2^64 同余
        long val = regX * 341873128712L + regZ * 132897987541L + worldSeed + ANCIENT_CITY_SALT;
        rng.setSeed(val);
        int cx = rng.nextInt(CHUNK_RANGE);
        int cz = rng.nextInt(CHUNK_RANGE);
        return new int[]{(regX * REGION_SIZE + cx) * 16, (regZ * REGION_SIZE + cz) * 16};
    }

    /** 变体：返回 [rotation(0-3), startPiece(1-3)]，startPiece 对应 city_center_1/2/3 */
    public static int[] variant(long worldSeed, int blockX, int blockZ) {
        Lcg rng = new Lcg(0);
        rng.setLargeFeatureSeed(worldSeed, blockX >> 4, blockZ >> 4);
        return new int[]{rng.nextInt(4), 1 + rng.nextInt(3)};
    }

    /**
     * 搜索中心附近 radiusChunks 区块半径内的全部古城候选（不含生物群系验证），
     * 按与中心距离升序返回。
     */
    public static List<City> findNearby(long worldSeed, int centerX, int centerZ, int radiusChunks) {
        int regionBlocks = REGION_SIZE * 16;
        int centerRegX = Math.floorDiv(centerX, regionBlocks);
        int centerRegZ = Math.floorDiv(centerZ, regionBlocks);
        int regionRange = Math.max(1, radiusChunks / REGION_SIZE + 1);
        long radiusBlocks = (long) radiusChunks * 16;
        long radiusSq = radiusBlocks * radiusBlocks;

        List<City> out = new ArrayList<>();
        for (int drx = -regionRange; drx <= regionRange; drx++) {
            for (int drz = -regionRange; drz <= regionRange; drz++) {
                int[] pos = locateInRegion(worldSeed, centerRegX + drx, centerRegZ + drz);
                int bx = pos[0];
                int bz = pos[1];
                long dx = bx - centerX;
                long dz = bz - centerZ;
                long dSq = dx * dx + dz * dz;
                if (dSq > radiusSq) continue;
                int[] v = variant(worldSeed, bx, bz);
                out.add(new City(bx, bz, v[0], v[1], bx >> 4, bz >> 4));
            }
        }
        out.sort(Comparator.comparingLong(c -> c.distanceSq(centerX, centerZ)));
        return out;
    }
}
