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

    // ---- V4.31 方案B：工具策略（挖矿锁时运 / 挖末影箱精准采集）----

    @Test
    void miningOreAlwaysFortune() {
        // 挖矿（钻石/残骸/石英）：无论有没有精准采集镐，必须时运（防钻石被挖成原矿）
        assertEquals(AutoMining.ToolStrategy.FORTUNE, AutoMining.pickaxeStrategy(true, false, false));
        assertEquals(AutoMining.ToolStrategy.FORTUNE, AutoMining.pickaxeStrategy(true, false, true));
    }

    @Test
    void enderChestPrefersSilkTouchWhenAvailable() {
        // 挖末影箱：有精准采集镐 → 精准回收本体
        assertEquals(AutoMining.ToolStrategy.SILK_TOUCH, AutoMining.pickaxeStrategy(false, true, true));
    }

    @Test
    void enderChestFallsBackToFortuneWithoutSilk() {
        // 挖末影箱：没有精准采集镐 → 消耗式（时运挖，掉 8 黑曜石）
        assertEquals(AutoMining.ToolStrategy.FORTUNE, AutoMining.pickaxeStrategy(false, true, false));
    }

    @Test
    void nonOreNonEnderChestAnyTool() {
        // 挖潜影盒/工作台：什么工具都掉本体，不挑
        assertEquals(AutoMining.ToolStrategy.ANY, AutoMining.pickaxeStrategy(false, false, false));
        assertEquals(AutoMining.ToolStrategy.ANY, AutoMining.pickaxeStrategy(false, false, true));
    }

    // ---- V4.54 对齐 misaka x0005：深暗逃离目标计算（质心反向 128 格）----

    @Test
    void escapeTargetAwayFromDeepDarkCentroid() {
        // 玩家 (0,64,0)，深暗采样单点 (100,0)：质心 (100,0) → 方向 (−100,0) 归一化 ×128 → 目标 (−128,64,0)
        int[] t = AutoMining.computeEscapeTarget(0, 64, 0, new int[]{100}, new int[]{0}, 1);
        assertArrayEquals(new int[]{-128, 64, 0}, t);
    }

    @Test
    void escapeTargetMultiPointCentroid() {
        // 深暗点 (64,0) (96,0)：质心 (80,0) → 方向 (−80,0) ×128 → (−128,10,0)，Y = max(10,0)
        int[] t = AutoMining.computeEscapeTarget(0, 10, 0, new int[]{64, 96}, new int[]{0, 0}, 2);
        assertArrayEquals(new int[]{-128, 10, 0}, t);
    }

    @Test
    void escapeTargetFallsBackWhenDirectionTiny() {
        // 玩家恰在质心 → 方向长度 < 1 → 兜底 (1,0) 方向 ×128：X +128，Z 不变；Y = max(−10,0) = 0
        int[] t = AutoMining.computeEscapeTarget(50, -10, 50, new int[]{50}, new int[]{50}, 1);
        assertEquals(50 + 128, t[0]);
        assertEquals(0, t[1]);
        assertEquals(50, t[2]);
    }

    @Test
    void escapeTargetNullWithoutDeepDarkSamples() {
        assertNull(AutoMining.computeEscapeTarget(0, 64, 0, new int[0], new int[0], 0));
    }

    // ---- V4.54 对齐 misaka x0092：满盒槽判定（含目标物 + 27 格满堆叠）----

    @Test
    void fullTargetBoxSlotRequiresFullStackAndTarget() {
        // 槽 3：满堆叠 + 含目标物 → 命中
        boolean[] full = {false, false, false, true, false};
        boolean[] hasTarget = {false, false, false, true, false};
        assertEquals(3, AutoMining.findFullTargetBoxSlotPure(full, hasTarget));
    }

    @Test
    void fullStackWithoutTargetIsSkipped() {
        // 满堆叠但无目标物（如杂物满盒）→ 不存进末影箱
        boolean[] full = {true, false};
        boolean[] hasTarget = {false, false};
        assertEquals(-1, AutoMining.findFullTargetBoxSlotPure(full, hasTarget));
    }

    @Test
    void targetBoxNotFullStackIsSkipped() {
        // 含目标物但未满堆叠（还能继续装）→ 不存
        boolean[] full = {false};
        boolean[] hasTarget = {true};
        assertEquals(-1, AutoMining.findFullTargetBoxSlotPure(full, hasTarget));
    }
}
