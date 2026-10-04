package com.fo.addon;

import com.fo.addon.utils.CraftingSlotMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CraftingSlotMath 纯逻辑测试：背包索引 → 合成台屏幕槽换算。
 *
 * <p>锁定 MC CraftingScreenHandler 布局（0输出/1-9合成格/10-36主背包/37-45热键）：
 * 热键 i(0-8)→i+37，主背包 i(9-35)→i+1。防止误写成 +46/+10 导致拖拽拿错槽。
 */
public class CraftingSlotMathTest {

    @Test
    void hotbarMapping() {
        // 热键栏（背包索引 0-8）→ 屏幕槽 37-45
        assertEquals(37, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(0));
        assertEquals(40, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(3));
        assertEquals(45, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(8));
    }

    @Test
    void mainInventoryMapping() {
        // 主背包（背包索引 9-35）→ 屏幕槽 10-36
        assertEquals(10, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(9));
        assertEquals(19, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(18));
        assertEquals(36, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(35));
    }

    @Test
    void boundaryContiguity() {
        // 边界连续：8→45（热键末格），9→10（主背包首格）；合成格 1-9 与输出 0 不被打到
        assertEquals(45, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(8));
        assertEquals(10, CraftingSlotMath.inventoryIndexToCraftingScreenSlot(9));
        for (int i = 0; i < 36; i++) {
            int screen = CraftingSlotMath.inventoryIndexToCraftingScreenSlot(i);
            // 必须落在主背包/热键区域（10-45），不得进入输出槽 0 或合成格 1-9
            org.junit.jupiter.api.Assertions.assertTrue(screen >= 10 && screen <= 45,
                "背包索引 " + i + " 换算越界: " + screen);
        }
    }
}
