package com.fo.addon;

import com.fo.addon.modules.AutoMining;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 存储目标盒选择测试（V4.52 对齐 misaka x0014）：
 *  优先"盒内全为目标物 + 目标物最多"，其次第一个全空盒，都没有 → -1 */
public class StorageBoxLogicTest {

    @Test
    public void prefersAllTargetBoxOverEmptyBox() {
        // 槽0：全目标盒（10 个钻石块）；槽1：空盒 → 选全目标盒
        int[] counts = {10, 0};
        boolean[] empty = {false, true};
        boolean[] allTarget = {true, false};
        assertEquals(0, AutoMining.pickStorageBox(counts, empty, allTarget));
    }

    @Test
    public void prefersMostTargets() {
        // 两个全目标盒：槽0 有 5 个，槽1 有 12 个 → 选目标物更多的槽1
        int[] counts = {5, 12};
        boolean[] empty = {false, false};
        boolean[] allTarget = {true, true};
        assertEquals(1, AutoMining.pickStorageBox(counts, empty, allTarget));
    }

    @Test
    public void skipsMixedBoxWithForeignItems() {
        // 槽0 混入杂物（非全目标，即使目标多）；槽1 全目标 3 个 → 选槽1
        int[] counts = {50, 3};
        boolean[] empty = {false, false};
        boolean[] allTarget = {false, true};
        assertEquals(1, AutoMining.pickStorageBox(counts, empty, allTarget));
    }

    @Test
    public void fallsBackToFirstEmptyBox() {
        // 无全目标盒：槽2 第一个空盒 → 选槽2
        int[] counts = {7, 9, 0, 0};
        boolean[] empty = {false, false, true, true};
        boolean[] allTarget = {false, false, false, false};
        assertEquals(2, AutoMining.pickStorageBox(counts, empty, allTarget));
    }

    @Test
    public void returnsMinusOneWhenNothingAvailable() {
        // 全是非空、非全目标（如全混杂物盒）→ -1，调用方走末影箱
        int[] counts = {4, 6};
        boolean[] empty = {false, false};
        boolean[] allTarget = {false, false};
        assertEquals(-1, AutoMining.pickStorageBox(counts, empty, allTarget));
    }

    @Test
    public void nonShulkerSlotsIgnored() {
        // 非潜影盒槽（counts=0, empty=false, allTarget=false）不参与 → 全目标盒槽1胜出
        int[] counts = {0, 16, 0};
        boolean[] empty = {false, false, false};
        boolean[] allTarget = {false, true, false};
        assertEquals(1, AutoMining.pickStorageBox(counts, empty, allTarget));
    }

    // ===== V4.53 对齐 misaka x0034/x0230/x0005：满堆叠判定 + 末影箱可取盒 + 交换取盒 =====

    @Test
    public void shulkerFullWhenAllSlotsFullStacks() {
        // 27 格全非空且全满堆叠 → 满
        int[] counts = new int[27];
        int[] maxs = new int[27];
        java.util.Arrays.fill(counts, 64);
        java.util.Arrays.fill(maxs, 64);
        assertEquals(true, AutoMining.isShulkerFull(counts, maxs));
    }

    @Test
    public void shulkerNotFullWhenLastSlotPartial() {
        // 本次 bug 场景：26 格满堆叠 + 最后一格 13 个（未满堆叠）→ 未满，应继续填充
        int[] counts = new int[27];
        int[] maxs = new int[27];
        java.util.Arrays.fill(counts, 64);
        java.util.Arrays.fill(maxs, 64);
        counts[26] = 13;
        assertEquals(false, AutoMining.isShulkerFull(counts, maxs));
    }

    @Test
    public void shulkerNotFullWhenEmptySlotExists() {
        // 存在空格 → 未满
        int[] counts = {64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 0};
        int[] maxs = new int[27];
        java.util.Arrays.fill(maxs, 64);
        assertEquals(false, AutoMining.isShulkerFull(counts, maxs));
    }

    @Test
    public void takablePrefersEmptyBoxInOrder() {
        // 槽0 空盒 → 优先取空盒（misaka x0230 遍历顺序语义）
        boolean[] empty = {true, false};
        boolean[] allTarget = {false, true};
        int[] filled = {0, 20};
        assertEquals(0, AutoMining.pickTakableShulker(empty, allTarget, filled));
    }

    @Test
    public void takableAcceptsPartialAllTargetBox() {
        // 无空盒：槽1 是全目标物未满盒（filled<27）→ 可取
        boolean[] empty = {false, false, false};
        boolean[] allTarget = {false, true, false};
        int[] filled = {5, 20, 0};
        assertEquals(1, AutoMining.pickTakableShulker(empty, allTarget, filled));
    }

    @Test
    public void takableSkipsFullAndMixedBoxes() {
        // 槽0 满盒（filled=27 全目标）、槽1 混杂物 → 均不可取 → -1
        boolean[] empty = {false, false, false};
        boolean[] allTarget = {true, false, false};
        int[] filled = {27, 10, 0};
        assertEquals(-1, AutoMining.pickTakableShulker(empty, allTarget, filled));
    }

    @Test
    public void tradeSlotFindsFirstTarget() {
        // 背包 36 槽：槽5 是目标物 → 返回 5
        boolean[] matches = new boolean[36];
        matches[5] = true;
        assertEquals(5, AutoMining.findTradeSlot(matches));
    }

    @Test
    public void tradeSlotMinusOneWhenNoTarget() {
        // 背包没有目标物 → -1（调用方断开）
        assertEquals(-1, AutoMining.findTradeSlot(new boolean[36]));
    }
}
