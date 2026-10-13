package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「开过的箱子」判定策略的回归测试。
 *
 * <p>关键用例是 {@link #未确认下发时null判不了_宁可不删}：部分服务端会剥离箱子的
 * LootTable NBT，那时所有箱子都是 null —— 如果直接判「已开」会把整批记录误删光。
 * 自适应策略要求先确认服务端确实下发该字段，才能凭 null 判「已开」。
 */
class AncientOpenedChestPolicyTest {

    @Test
    void lootTable字段还在时视为未开箱() {
        assertEquals(AncientOpenedChestPolicy.Decision.UNOPENED,
            AncientOpenedChestPolicy.decide(false, true));
        assertEquals(AncientOpenedChestPolicy.Decision.UNOPENED,
            AncientOpenedChestPolicy.decide(true, true));
    }

    @Test
    void 确认服务端下发字段后null即已开箱() {
        assertEquals(AncientOpenedChestPolicy.Decision.OPENED,
            AncientOpenedChestPolicy.decide(true, false));
    }

    @Test
    void 未确认下发时null判不了_宁可不删() {
        assertEquals(AncientOpenedChestPolicy.Decision.UNKNOWN,
            AncientOpenedChestPolicy.decide(false, false));
    }
}
