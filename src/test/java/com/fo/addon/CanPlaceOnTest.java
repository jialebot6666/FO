package com.fo.addon;

import com.fo.addon.modules.AutoMining;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 放置判定纯逻辑单测（V4.50 对齐 misaka x0023：目标格空气 + 下方非空气/非液体） */
class CanPlaceOnTest {

    @Test
    void airOverSolidCanPlace() {
        // 目标格空气 + 下方实心方块 → 可放置（放末影箱/潜影盒/工作台的标准场景）
        assertTrue(AutoMining.canPlaceOn(true, false, true));
    }

    @Test
    void airOverAirCannotPlace() {
        // 下方也是空气（如 3x3x3 挖穿/悬空）→ 不可放置
        assertFalse(AutoMining.canPlaceOn(true, true, true));
    }

    @Test
    void airOverFluidCannotPlace() {
        // 下方是水/岩浆 → 不可放置（misaka 语义：避开水域）
        assertFalse(AutoMining.canPlaceOn(true, false, false));
    }

    @Test
    void occupiedTargetCannotPlace() {
        // 目标格不是空气（方块占位，如工作台已占格）→ 不可放置
        assertFalse(AutoMining.canPlaceOn(false, false, true));
        assertFalse(AutoMining.canPlaceOn(false, true, true));
    }
}
