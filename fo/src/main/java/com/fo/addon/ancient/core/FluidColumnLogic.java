package com.fo.addon.ancient.core;

/**
 * 箱子正上方整列的水/岩浆检测（纯逻辑，可单测）。
 *
 * <p>方案口径：
 * <ul>
 *   <li>从箱子 Y+1 到地表整列都被水淹 → 水下古城/水域，整城或整箱拉黑；</li>
 *   <li>只有半途或顶部有一小段水 → 只是小水塘，换竖井列即可；</li>
 *   <li>任何一格是岩浆/气泡柱也按流体处理。</li>
 * </ul>
 */
public final class FluidColumnLogic {

    private FluidColumnLogic() {
    }

    /** 整列是否存在流体。 */
    public static boolean hasFluid(boolean[] samples) {
        if (samples == null) return false;
        for (boolean b : samples) {
            if (b) return true;
        }
        return false;
    }

    /** 整列是否全部是流体（空数组不算）。 */
    public static boolean fullySubmerged(boolean[] samples) {
        if (samples == null || samples.length == 0) return false;
        for (boolean b : samples) {
            if (!b) return false;
        }
        return true;
    }

    /** 第一个流体的下标；没有返回 -1。 */
    public static int firstFluidIndex(boolean[] samples) {
        if (samples == null) return -1;
        for (int i = 0; i < samples.length; i++) {
            if (samples[i]) return i;
        }
        return -1;
    }

    /**
     * 是否判为「水下古城」：从古城顶部到地表整列都是流体。
     *
     * <p>这是比 {@link #fullySubmerged} 更保守的口径：至少要有 3 格采样，
     * 避免只采到 1~2 格时把普通水塘误判成整城水淹。
     */
    public static boolean isUnderwaterCity(boolean[] samples) {
        return samples != null && samples.length >= 3 && fullySubmerged(samples);
    }
}
