package com.fo.addon;

import com.fo.addon.utils.AncientStealLogic;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AncientStealLogic 纯逻辑测试：古城模块「自动偷取」的判定。
 *
 * <p>锁定两条语义：① 物品在白名单里就偷（与彗星端一致）；
 * ② 附魔书按迅捷潜行等级判定 —— 这是彗星端原版做不到的（它只按 Item 匹配）。
 */
public class AncientStealLogicTest {

    private static Map<String, Integer> enchants(String id, int level) {
        Map<String, Integer> m = new HashMap<>();
        m.put(id, level);
        return m;
    }

    // ---------- 迅捷潜行等级读取 ----------

    @Test
    void readsSwiftSneakLevel() {
        assertEquals(3, AncientStealLogic.swiftSneakLevel(enchants("minecraft:swift_sneak", 3)));
        assertEquals(1, AncientStealLogic.swiftSneakLevel(enchants("minecraft:swift_sneak", 1)));
    }

    @Test
    void otherEnchantGivesZero() {
        assertEquals(0, AncientStealLogic.swiftSneakLevel(enchants("minecraft:mending", 1)));
        assertEquals(0, AncientStealLogic.swiftSneakLevel(enchants("minecraft:protection", 4)));
    }

    @Test
    void emptyOrNullMapGivesZero() {
        assertEquals(0, AncientStealLogic.swiftSneakLevel(new HashMap<>()));
        assertEquals(0, AncientStealLogic.swiftSneakLevel(null));
    }

    /** 多附魔书（附魔台合并出来的）：只看迅捷潜行那一条的等级。 */
    @Test
    void mixedBookPicksSwiftSneakLevel() {
        Map<String, Integer> m = new HashMap<>();
        m.put("minecraft:mending", 1);
        m.put("minecraft:swift_sneak", 2);
        m.put("minecraft:efficiency", 5);
        assertEquals(2, AncientStealLogic.swiftSneakLevel(m));
    }

    // ---------- 该不该偷 ----------

    @Test
    void stealsWhenInItemList() {
        // 附魔金苹果：走物品列表这条线（不是附魔书，迅捷潜行等级为 0）
        assertTrue(AncientStealLogic.shouldSteal(true, 0, 3));
        // 即使门槛设成 0（不识别附魔书），物品列表命中照样偷
        assertTrue(AncientStealLogic.shouldSteal(true, 0, 0));
    }

    @Test
    void stealsSwiftSneakBookAtOrAboveThreshold() {
        assertTrue(AncientStealLogic.shouldSteal(false, 3, 3));
        assertTrue(AncientStealLogic.shouldSteal(false, 3, 1));   // 门槛调低到 1 → 也偷
    }

    /** 用户选的默认语义：只偷满级（3 级），1~2 级不拿。 */
    @Test
    void ignoresSwiftSneakBelowThreshold() {
        assertFalse(AncientStealLogic.shouldSteal(false, 1, 3));
        assertFalse(AncientStealLogic.shouldSteal(false, 2, 3));
        assertFalse(AncientStealLogic.shouldSteal(false, 0, 3));
    }

    @Test
    void thresholdZeroMeansIgnoreBooks() {
        assertFalse(AncientStealLogic.shouldSteal(false, 3, 0));
    }

    @Test
    void plainItemNotInListIsNotStolen() {
        assertFalse(AncientStealLogic.shouldSteal(false, 0, 3));
        assertFalse(AncientStealLogic.shouldSteal(false, 0, 0));
    }

    // ---------- 间隔计算 ----------

    @Test
    void delayWithoutRandom() {
        assertEquals(2, AncientStealLogic.delayTicks(2, 0, 7));    // randomBound=0 → 忽略随机值
        assertEquals(2, AncientStealLogic.delayTicks(2, -1, 7));
    }

    @Test
    void delayWithRandom() {
        assertEquals(2, AncientStealLogic.delayTicks(2, 3, 0));
        assertEquals(4, AncientStealLogic.delayTicks(2, 3, 2));
        // 随机值越界要被夹到 [0, randomBound-1]
        assertEquals(4, AncientStealLogic.delayTicks(2, 3, 99));
    }

    @Test
    void negativeBaseClampsToZero() {
        assertEquals(0, AncientStealLogic.delayTicks(-5, 0, 0));
        assertEquals(1, AncientStealLogic.delayTicks(-5, 2, 1));
    }
}
