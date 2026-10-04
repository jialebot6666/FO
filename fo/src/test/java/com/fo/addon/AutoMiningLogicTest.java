package com.fo.addon;

import com.fo.addon.modules.AutoMining;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FO 自动挖矿（AutoMining）纯逻辑判定单测：
 * 背包满 / 镐耐久阈值 / 合成条件 / 容器槽位换算（移植 misaka AutoMining 状态机的核心判定）。
 */
public class AutoMiningLogicTest {

    // ---- 背包满（主背包 9-35 全非空）----

    @Test
    void inventoryNotFullWhenMainSlotEmpty() {
        int[] counts = new int[36];
        for (int i = 9; i < 36; i++) counts[i] = 1;
        counts[20] = 0; // 主背包有一个空格
        assertFalse(AutoMining.isInventoryFull(counts));
    }

    @Test
    void inventoryFullWhenAllMainSlotsNonEmpty() {
        int[] counts = new int[36];
        for (int i = 9; i < 36; i++) counts[i] = 64;
        assertTrue(AutoMining.isInventoryFull(counts));
    }

    @Test
    void hotbarEmptyStillCountsAsFull() {
        int[] counts = new int[36];
        for (int i = 9; i < 36; i++) counts[i] = 1;
        // 热键栏（0-8）全空不影响"背包满"判定
        assertTrue(AutoMining.isInventoryFull(counts));
    }

    // ---- 镐子耐久阈值 ----

    @Test
    void pickaxeDurabilityBelowThreshold() {
        // 最大 1561（下界合金镐），已损 1500 → 剩余 61 < 200
        assertTrue(AutoMining.isPickaxeDurabilityLow(1561, 1500, 200));
    }

    @Test
    void pickaxeDurabilityAboveThreshold() {
        assertFalse(AutoMining.isPickaxeDurabilityLow(1561, 1000, 200)); // 剩余 561
    }

    @Test
    void pickaxeDurabilityExactlyAtThresholdNotLow() {
        assertFalse(AutoMining.isPickaxeDurabilityLow(1561, 1361, 200)); // 剩余 200
    }

    // ---- 合成条件 ----

    @Test
    void diamondCraftRequiresNine() {
        assertFalse(AutoMining.canCraftDiamondBlock(8));
        assertTrue(AutoMining.canCraftDiamondBlock(9));
        assertTrue(AutoMining.canCraftDiamondBlock(64));
    }

    @Test
    void quartzCraftRequiresFour() {
        assertFalse(AutoMining.canCraftQuartzBlock(3));
        assertTrue(AutoMining.canCraftQuartzBlock(4));
        assertTrue(AutoMining.canCraftQuartzBlock(64));
    }

    // ---- 容器槽位换算 ----

    @Test
    void craftingScreenSlotMapping() {
        // CraftingScreenHandler：热键 0-8 → 屏幕 37-45；主背包 9-35 → 屏幕 10-36
        assertEquals(37, AutoMining.invSlotToCraftingScreen(0));
        assertEquals(45, AutoMining.invSlotToCraftingScreen(8));
        assertEquals(10, AutoMining.invSlotToCraftingScreen(9));
        assertEquals(36, AutoMining.invSlotToCraftingScreen(35));
    }

    @Test
    void containerScreenSlotMapping() {
        // ShulkerBox/GenericContainer：热键 0-8 → 屏幕 54-62；主背包 9-35 → 屏幕 27-53
        assertEquals(54, AutoMining.invSlotToContainerScreen(0));
        assertEquals(62, AutoMining.invSlotToContainerScreen(8));
        assertEquals(27, AutoMining.invSlotToContainerScreen(9));
        assertEquals(53, AutoMining.invSlotToContainerScreen(35));
    }
}
