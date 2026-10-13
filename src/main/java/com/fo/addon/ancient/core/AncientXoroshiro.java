package com.fo.addon.ancient.core;

/**
 * Xoroshiro128++ 随机数（纯逻辑，可单测）。
 *
 * <p>复刻 MC 的 {@code Xoroshiro128PlusPlus} / {@code XoroshiroRandomSource} 与
 * {@code WorldgenRandom}（decoration 阶段），用于推导箱子 LootTableSeed。
 */
public final class AncientXoroshiro {

    private AncientXoroshiro() {
    }

    // ==================== Xoroshiro128++ 核心 ====================
    public static final class Rng {
        private long seedLo, seedHi;

        public Rng(long lo, long hi) {
            this.seedLo = lo;
            this.seedHi = hi;
            if (lo == 0 && hi == 0) {
                this.seedLo = -7046029254386353131L; // 0x9E3779B97F4A7C15
                this.seedHi = 7640891576956012809L;  // 0x6A09E667F3BCC909
            }
        }

        public long nextLong() {
            long s0 = seedLo, s1 = seedHi;
            long result = Long.rotateLeft(s0 + s1, 17) + s0;
            s1 ^= s0;
            seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
            seedHi = Long.rotateLeft(s1, 28);
            return result;
        }
    }

    // ==================== RandomSupport ====================
    public static long mixStafford13(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long[] upgradeSeedTo128bit(long legacySeed) {
        long low = legacySeed ^ 0x6A09E667F3BCC909L;
        long high = low + 0x9E3779B97F4A7C15L;
        return new long[]{mixStafford13(low), mixStafford13(high)};
    }

    // ==================== XoroshiroRandomSource ====================
    public static final class Source {
        private Rng rng;

        public Source(long seed) {
            long[] lh = upgradeSeedTo128bit(seed);
            rng = new Rng(lh[0], lh[1]);
        }

        public Source(long lo, long hi) {
            rng = new Rng(lo, hi);
        }

        public void setSeed(long seed) {
            long[] lh = upgradeSeedTo128bit(seed);
            rng = new Rng(lh[0], lh[1]);
        }

        public long nextLong() {
            return rng.nextLong();
        }
    }

    // ==================== WorldgenRandom（decoration 阶段） ====================
    /**
     * WorldgenRandom 包装 XoroshiroRandomSource：一次 WorldgenRandom.nextLong() 消耗两次 Xoroshiro.nextLong()。
     */
    public static final class WorldgenRandom {
        private Source random = new Source(0L);

        public void setSeed(long seed) {
            random.setSeed(seed);
        }

        private int nextBits(int bits) {
            long raw = random.nextLong();
            long val = raw >>> (64 - bits);
            return (int) val; // Java int 截断 = 带符号 32 位
        }

        public long nextLong() {
            int high = nextBits(32);
            int low = nextBits(32);
            return ((long) high << 32) + low;
        }

        /** WorldgenRandom.setDecorationSeed 复刻。 */
        public long setDecorationSeed(long seed, int chunkX, int chunkZ) {
            setSeed(seed);
            long xScale = nextLong() | 1L;
            long zScale = nextLong() | 1L;
            long result = (chunkX * xScale + chunkZ * zScale) ^ seed;
            setSeed(result);
            return result;
        }

        /** WorldgenRandom.setFeatureSeed 复刻。 */
        public void setFeatureSeed(long decorationSeed, int index, int step) {
            long result = decorationSeed + index + 10000L * step;
            setSeed(result);
        }
    }
}
