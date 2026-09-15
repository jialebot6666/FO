package com.fo.addon.utils;

/**
 * FO 自动LogPlus 的纯逻辑判定（不依赖 MC 运行时，可单元测试）。
 */
public class RepairLogic {

    private RepairLogic() {
    }

    /** 未损坏百分比 = 100 - 已损失 / 最大耐久 * 100（护甲耐久判定用）。maxDamage <= 0 视为 100。 */
    public static double undamagedPercent(int maxDamage, int damage) {
        if (maxDamage <= 0) return 100.0;
        double pct = 100.0 - (damage / (double) maxDamage) * 100.0;
        return Math.max(0, pct);
    }

    /** 护甲是否低于阈值需要下线：可损坏 + 未损坏百分比 < 阈值。 */
    public static boolean armorTooDamaged(int maxDamage, int damage, boolean damageable, double thresholdPercent) {
        if (!damageable || maxDamage <= 0) return false;
        return undamagedPercent(maxDamage, damage) < thresholdPercent;
    }
}
