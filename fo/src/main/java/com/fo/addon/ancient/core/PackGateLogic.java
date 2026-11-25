package com.fo.addon.ancient.core;

/**
 * 存盒门槛（纯逻辑，可单测）。
 *
 * <p>攒够独立设置的门槛才停靠存盒，与「目标苹果数」解耦。
 */
public final class PackGateLogic {

    private PackGateLogic() {
    }

    public static boolean shouldPack(int apples, int threshold) {
        return apples >= Math.max(1, threshold);
    }
}
