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
}
