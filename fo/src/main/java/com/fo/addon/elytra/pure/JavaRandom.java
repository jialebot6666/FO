package com.fo.addon.elytra.pure;

/**
 * Java Random 的 48 位状态实现，用于离线复刻 cubiomes 的随机序列。
 * 与 java.util.Random 语义一致，但保留内部状态（cubiomes 的 chunkGenerateRnd 返回状态而非原始种子）。
 */
public final class JavaRandom {

    private static final long MASK = (1L << 48) - 1L;
    private static final long MULT = 0x5deece66dL;
    private static final long ADD = 0xbL;

    private long state;

    public JavaRandom(long seed) {
        setSeed(seed);
    }

    public JavaRandom() {
        this(0L);
    }

    public void setSeed(long seed) {
        this.state = (seed ^ MULT) & MASK;
    }

    /** 直接设置 48 位内部状态（chunkGenerateRnd 的返回值）。 */
    public void setState(long state) {
        this.state = state & MASK;
    }

    public long state() {
        return state;
    }

    public int next(int bits) {
        state = (state * MULT + ADD) & MASK;
        return (int) (state >>> (48 - bits));
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int m = bound - 1;
        if ((m & bound) == 0) {
            long x = bound * (long) next(31);
            return (int) (x >> 31);
        }
        int bits;
        int val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + m < 0);
        return val;
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    public float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public double nextDouble() {
        long x = (long) next(26) << 27;
        x += next(27);
        return x / (double) (1L << 53);
    }

    /** 跳过 n 次 next 调用（快速幂）。 */
    public void skip(long n) {
        if (n <= 0) {
            return;
        }
        long m = 1L;
        long a = 0L;
        long im = MULT;
        long ia = ADD;
        for (long k = n; k != 0; k >>= 1) {
            if ((k & 1L) != 0L) {
                m *= im;
                a = im * a + ia;
            }
            ia = (im + 1L) * ia;
            im *= im;
        }
        state = (state * m + a) & MASK;
    }

    /** chunkGenerateRnd：返回已设置好的 48 位状态。 */
    public static long chunkGenerateRnd(long worldSeed, int chunkX, int chunkZ) {
        JavaRandom r = new JavaRandom(worldSeed);
        long rnd = (r.nextLong() * (long) chunkX) ^ (r.nextLong() * (long) chunkZ) ^ worldSeed;
        r.setSeed(rnd);
        return r.state();
    }
}
