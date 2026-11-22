package com.fo.addon.utils;

import com.seedfinding.mccore.util.pos.CPos;

/**
 * 26.1 结构放置的纯 Java 实现（random_spread）。
 *
 * <p>算法来源：Mojang 26.1 服务端 {@code RandomSpreadStructurePlacement#getPotentialStructureChunk}，
 * 已用官方 jar 逐行对拍验证。
 *
 * <p>为什么不直接用 seedfinding 的 {@code RegionStructure#getInRegion}：它的版本配置表有误，
 * 例如 {@code Village(MCVersion.v1_21)} 的间距是 32，而 26.1 的 {@code villages.json} 是 34，
 * 导致所有村庄位置落在错误的区域。这里改为由调用方按 26.1 数据传入配置。
 */
public final class StructurePlacementPure {

    private StructurePlacementPure() {
    }

    /**
     * @param spacing     区域边长（区块）
     * @param separation  区域内的最小间隔（区块）
     * @param salt        盐值
     * @param triangular  true = 三角分布，false = 线性分布
     */
    public record Config(int spacing, int separation, int salt, boolean triangular) {
    }

    private static long state;

    private static void setSeed(long seed) {
        state = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
    }

    private static int next(int bits) {
        state = (state * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        return (int) (state >>> (48 - bits));
    }

    private static int nextInt(int bound) {
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) next(31)) >> 31);
        }
        int bits;
        int val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }

    /** 在指定区域里算出结构起点区块。 */
    public static CPos getInRegion(long seed, int regionX, int regionZ, Config config) {
        int spacing = config.spacing();
        int limit = spacing - config.separation();
        if (limit <= 0) {
            return new CPos(regionX * spacing, regionZ * spacing);
        }

        long regionSeed = (long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + config.salt();
        setSeed(regionSeed);

        int offsetX;
        int offsetZ;
        if (config.triangular()) {
            offsetX = (nextInt(limit) + nextInt(limit)) / 2;
            offsetZ = (nextInt(limit) + nextInt(limit)) / 2;
        } else {
            offsetX = nextInt(limit);
            offsetZ = nextInt(limit);
        }
        return new CPos(regionX * spacing + offsetX, regionZ * spacing + offsetZ);
    }

    /**
     * 埋藏的宝藏：每个区块 1% 概率，直接把区块自身当区域。
     */
    public static boolean isBuriedTreasureAt(long seed, int chunkX, int chunkZ, int salt) {
        long s = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + seed + salt;
        setSeed(s);
        return nextFloat() < 0.01f;
    }

    /**
     * 废弃矿井：每个区块 0.4% 概率。
     */
    public static boolean isMineshaftAt(long seed, int chunkX, int chunkZ, int salt) {
        long s = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + seed + salt;
        setSeed(s);
        return nextDouble() < 0.004;
    }

    private static double nextDouble() {
        return (((long) next(26) << 27) + next(27)) * 0x1.0p-53;
    }

    private static float nextFloat() {
        return next(24) / (float) (1 << 24);
    }
}
