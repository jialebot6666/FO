package com.fo.addon.ancient.core;

/**
 * 「只挖肥箱」过滤（纯逻辑，可单测）。
 */
public final class FatFilterLogic {

    private FatFilterLogic() {
    }

    public static boolean keep(int goldCount, boolean onlyFat, int threshold) {
        if (!onlyFat) return true;
        return goldCount >= Math.max(1, threshold);
    }
}
