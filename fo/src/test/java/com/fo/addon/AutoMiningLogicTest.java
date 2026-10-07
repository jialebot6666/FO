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

    // ---- V4.57：放置格跳过玩家整个碰撞箱（脚底+头顶，防放盒/放末影箱撞头回弹）----

    @Test
    void placeableSlotExcludesPlayerBody() {
        assertTrue(AutoMining.isPlaceableSlot(true, false));    // 可放且不被玩家身体阻挡 → 选中
        assertFalse(AutoMining.isPlaceableSlot(true, true));    // 可放但是脚底/头顶格 → 排除
        assertFalse(AutoMining.isPlaceableSlot(false, false));  // 不可放 → 排除
        assertFalse(AutoMining.isPlaceableSlot(false, true));   // 不可放且是身体格 → 排除
    }

    @Test
    void holeFloorStillHasSolutionAfterExcludingPlayerBody() {
        // bug 场景：玩家站洞里（脚踩洞底实体、头占洞底层），洞底 9 格中 1 格被玩家身体占据，
        // 其余 8 格仍可用 → 第一轮必然有解（不会撞头回弹、不会报错关闭）
        int usable = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean isBody = dx == 0 && dz == 0; // 洞底中心格 = 玩家头所在
                if (AutoMining.isPlaceableSlot(true, isBody)) usable++;
            }
        }
        assertEquals(8, usable);
    }

    // ---- V4.58：打开容器 20 秒超时（400 tick）判定 ----

    @Test
    void openContainerTimeoutBoundary() {
        assertEquals(400, AutoMining.OPEN_CONTAINER_TIMEOUT_TICKS); // 20 秒 × 20 tick
        assertFalse(AutoMining.isOpenContainerTimeout(399));         // 未超时
        assertTrue(AutoMining.isOpenContainerTimeout(400));          // 恰好超时 → 断开
        assertTrue(AutoMining.isOpenContainerTimeout(500));          // 超时
    }

    // ---- V4.59：启动检查强制物品（时运镐 + 末影箱 + 工作台 + 精准采集镐）----

    @Test
    void startupCheckAllPresent() {
        assertNull(AutoMining.firstMissingStartupItem(true, true, true, true)); // 全齐 → 可启动
    }

    @Test
    void startupCheckMissingEachInOrder() {
        assertEquals("时运镐", AutoMining.firstMissingStartupItem(false, true, true, true));
        assertEquals("末影箱", AutoMining.firstMissingStartupItem(true, false, true, true));
        assertEquals("工作台", AutoMining.firstMissingStartupItem(true, true, false, true));
        assertEquals("精准采集镐", AutoMining.firstMissingStartupItem(true, true, true, false));
    }

    @Test
    void startupCheckOnlyFirstMissingReported() {
        // 多缺时只报第一个（顺序：时运 → 末影箱 → 工作台 → 精准）
        assertEquals("时运镐", AutoMining.firstMissingStartupItem(false, false, false, false));
    }

    // ---- V4.60：残骸模式挖掘 Y 范围（限制开 8~22 / 关 6~256）----

    @Test
    void debrisYRangeLimited() {
        int[] r = AutoMining.debrisYRange(true);
        assertEquals(8, r[0]);   // 残骸生成下限
        assertEquals(22, r[1]);  // 残骸生成上限
    }

    @Test
    void debrisYRangeUnlimited() {
        int[] r = AutoMining.debrisYRange(false);
        assertEquals(6, r[0]);   // 仅防挖穿下界基岩
        assertEquals(256, r[1]); // 无上限
    }

    // ---- V4.61：Y 范围变化才弹通知判定 ----

    @Test
    void yRangeChangedFirstTime() {
        // 首次应用（appliedKey=null）→ 变化 → 弹
        assertTrue(AutoMining.isYRangeChanged(null, 8, 22));
    }

    @Test
    void yRangeChangedOnSwitch() {
        // 从残骸 8~22 切到石英 10~117 → 变化 → 弹
        assertTrue(AutoMining.isYRangeChanged("8~22", 10, 117));
        // 切回钻石 6~256 → 变化 → 弹
        assertTrue(AutoMining.isYRangeChanged("10~117", 6, 256));
    }

    @Test
    void yRangeUnchangedNoNotify() {
        // 每 100 tick 重启挖掘，范围相同 → 不弹（防刷屏）
        assertFalse(AutoMining.isYRangeChanged("8~22", 8, 22));
        assertFalse(AutoMining.isYRangeChanged("6~256", 6, 256));
    }
}
