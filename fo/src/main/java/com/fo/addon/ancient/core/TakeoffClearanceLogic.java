package com.fo.addon.ancient.core;

/**
 * 起飞净空判定（纯逻辑，可单测）。
 *
 * <p>头顶 3×3×4 共 36 格；只要正上方那一列和四周有一格被挡，就不算净空。
 */
public final class TakeoffClearanceLogic {

    private TakeoffClearanceLogic() {
    }

    /** blocks 按「3×3×4」展平，true = 空气。 */
    public static boolean canTakeOff(boolean[] blocks) {
        if (blocks == null || blocks.length != 36) return false;
        for (boolean air : blocks) {
            if (!air) return false;
        }
        return true;
    }
}
