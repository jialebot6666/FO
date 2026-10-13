package com.fo.addon.ancient.core;

/**
 * 世界生成随机数（纯逻辑，可单测）。
 *
 * <p>精确复刻 MC 的 LegacyRandomSource / java.util.Random（48 位 LCG），
 * 并提供 ChunkRandom.setLargeFeatureSeed（大型结构变体推导）。
 */
public final class AncientRandom {

    private static final long MULT = 0x5DEECE66DL;
    private static final long ADD = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private long state;

    public AncientRandom(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        state = (seed ^ MULT) & MASK;
    }

    public int next(int bits) {
        state = (state * MULT + ADD) & MASK;
        return (int) (state >>> (48 - bits));
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound 必须为正");
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            return (int) ((bound * (long) r) >> 31);
        }
        int u = r;
        while (true) {
            int val = u % bound;
            if (u - val + m < Integer.MAX_VALUE) return val;
            u = next(31);
        }
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    public float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    /** ChunkRandom.setLargeFeatureSeed 复刻。 */
    public void setLargeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
        setSeed(worldSeed);
        long xs = nextLong();
        long zs = nextLong();
        setSeed(xs * chunkX ^ zs * chunkZ ^ worldSeed);
    }
}
