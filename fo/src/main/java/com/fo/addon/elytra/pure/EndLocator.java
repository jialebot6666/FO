package com.fo.addon.elytra.pure;

/**
 * 纯 Java 末地城定位器（替代原 CubiomesEndJNI 原生桥）。
 * 对外 API 与旧 JNI 保持一致，便于鞘翅采集模块平滑切换。
 */
public final class EndLocator {

    public static final int MC_1_13 = EndNoisePure.MC_1_13;
    public static final int MC_1_18 = EndNoisePure.MC_1_18;
    public static final int MC_1_21 = EndNoisePure.MC_1_21;
    public static final int MC_1_26_3 = EndNoisePure.MC_1_26_3;

    private final int mc;
    private final long seed;
    private final EndNoisePure.SurfaceNoise surfaceNoise = new EndNoisePure.SurfaceNoise();
    private final EndNoisePure.EndNoise endNoise = new EndNoisePure.EndNoise();

    public EndLocator(int mc, long seed) {
        this.mc = mc;
        this.seed = seed;
        EndNoisePure.setEndSeed(endNoise, mc, seed);
        EndNoisePure.initSurfaceNoise(surfaceNoise, 1, seed);
    }

    public static EndLocator create(int mc, long seed) {
        return new EndLocator(mc, seed);
    }

    /**
     * 候选城位置。
     *
     * @param out 输出 {x, z}（块坐标）
     * @return 1 表示存在候选，0 表示没有
     */
    public int getEndCityAt(int regX, int regZ, int[] out) {
        int[] pos = EndCityPure.getEndCityAt(seed, regX, regZ);
        if (pos == null) {
            return 0;
        }
        out[0] = pos[0];
        out[1] = pos[1];
        return 1;
    }

    /** 原版 EndCityStructure 的 Y >= 60 地形判定。 */
    public boolean isViableEndCity(int blockX, int blockZ) {
        return EndNoisePure.isViableEndCityTerrain(seed, mc, surfaceNoise, endNoise, blockX, blockZ);
    }

    /**
     * 末地城船 piece。
     *
     * @param out 输出 {bb0.x, bb0.y, bb0.z, bb1.x, bb1.y, bb1.z, rot}
     * @return 1 表示有船，0 表示无船
     */
    public int getEndCityShip(int chunkX, int chunkZ, int[] out) {
        int[] ship = EndCityPure.getEndCityShip(seed, chunkX, chunkZ);
        if (ship == null) {
            return 0;
        }
        System.arraycopy(ship, 0, out, 0, 7);
        return 1;
    }

    public void free() {
        // 纯 Java 无需释放
    }
}
