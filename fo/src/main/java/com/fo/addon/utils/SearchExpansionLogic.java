package com.fo.addon.utils;

/**
 * 搜船范围扩展逻辑（对齐 Ying ElytraSearchFallback）：
 * 搜索结果为空时按"环形带"外扩，不重扫旧区；上限对齐 Ying MAX_EXPANDED_RANGE = 50000 格。
 * 纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class SearchExpansionLogic {
    /** 与 Ying ElytraSearchFallback.MAX_EXPANDED_RANGE 一致：回退搜索最大范围（格） */
    public static final int MAX_EXPANDED_RANGE = 50000;

    private SearchExpansionLogic() {
    }

    /**
     * 指定区块范围（格）对应的"城市间距环数"（region 下标半径）。
     * 与 Ying: n14 = max(1, range/(spacing*16)+1) 一致。
     *
     * @param rangeBlocks   搜索范围（格）
     * @param spacingBlocks 城市间距（格）= spacing * 16
     */
    public static int ringCount(int rangeBlocks, int spacingBlocks) {
        return Math.max(1, rangeBlocks / spacingBlocks + 1);
    }

    /**
     * 回退搜索的最大环数：范围扩展到 50000 格所需环数。
     * 与 Ying: n15 = max(n14, 50000/(spacing*16)+1) 一致。
     *
     * @param spacingBlocks 城市间距（格）
     * @param baseRing      初始环数（取大者兜底）
     */
    public static int maxRing(int spacingBlocks, int baseRing) {
        return Math.max(baseRing, MAX_EXPANDED_RANGE / spacingBlocks + 1);
    }

    /**
     * 该 (drx, drz) 偏移是否位于第 ring 环的边界上（外扩时只扫新环带，不重扫旧区）。
     * 与 Ying scanRing 一致：|drx|==ring 或 |drz|==ring。
     */
    public static boolean isRingBoundary(int drx, int drz, int ring) {
        if (ring <= 0) return false;
        int ax = Math.abs(drx);
        int az = Math.abs(drz);
        return ax == ring || az == ring;
    }
}
