package com.fo.addon.utils;

/**
 * 自动挖矿启动必需品检查（纯逻辑，可单元测试）。
 *
 * <p>V4.40：背包只有满盒（无空盒）但持有末影箱时允许启动——运行中 PLACING_SHULKER
 * 无空盒会自动走末影箱取盒流程。判定返回缺失物品文案，null 表示通过。
 */
public final class StartupCheckLogic {

    private StartupCheckLogic() {}

    /** 判定缺失物品。返回 null 表示必需品齐全；否则返回缺失物品名称（用于错误提示）。 */
    public static String missingItem(boolean hasFortunePickaxe, boolean hasEmptyShulker, boolean hasEnderChest,
                                     boolean diamondMode, boolean hasCraftingTable) {
        if (!hasFortunePickaxe) return "时运镐";
        // 无空盒时必须有末影箱（取盒兜底）；末影箱本身是必需品
        if (!hasEmptyShulker && !hasEnderChest) return "空潜影盒或末影箱";
        if (!hasEnderChest) return "末影箱";
        if (diamondMode && !hasCraftingTable) return "工作台";
        return null;
    }
}
