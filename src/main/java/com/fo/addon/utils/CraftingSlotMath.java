package com.fo.addon.utils;

/**
 * CraftingScreenHandler 槽位换算（纯逻辑，可单元测试）。
 *
 * <p>MC 3x3 合成台界面槽布局（Yarn）：0=输出槽，1-9=合成格，
 * 10-36=主背包（PlayerInventory 索引 9-35），37-45=热键栏（PlayerInventory 索引 0-8）。
 * 因此背包索引 i（0-35）到屏幕槽的换算：i&lt;9（热键）→ i+37；i≥9（主背包）→ i+1。
 *
 * <p>佐证：misaka AutoMiningModule.x0005 直接在屏幕槽 10-36 / 37-45 内扫描钻石，
 * 与本换算一致。曾因把换算误写成 +46/+10 导致拖拽拿错槽、合成产物异常，故抽为纯函数 + 单测防回归。
 */
public final class CraftingSlotMath {

    private CraftingSlotMath() {}

    /** 背包索引 → 合成台界面屏幕槽（热键 i<9 → i+37，主背包 i≥9 → i+1） */
    public static int inventoryIndexToCraftingScreenSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? inventoryIndex + 37 : inventoryIndex + 1;
    }
}
