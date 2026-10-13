package com.fo.addon;

import com.fo.addon.utils.RepairLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RepairLogic（FO 自动LogPlus 护甲耐久判定）单元测试。 */
public class RepairLogicTest {

    // ========== undamagedPercent ==========

    @Test
    void undamagedPercent_满耐久为100() {
        assertEquals(100.0, RepairLogic.undamagedPercent(100, 0), 1e-9);
    }

    @Test
    void undamagedPercent_损失30为70() {
        assertEquals(70.0, RepairLogic.undamagedPercent(100, 30), 1e-9);
    }

    @Test
    void undamagedPercent_不可损坏为100() {
        assertEquals(100.0, RepairLogic.undamagedPercent(0, 0), 1e-9);
        assertEquals(100.0, RepairLogic.undamagedPercent(-1, 5), 1e-9);
    }

    @Test
    void undamagedPercent_损失超过最大耐久不为负() {
        assertEquals(0.0, RepairLogic.undamagedPercent(50, 100), 1e-9);
    }

    // ========== armorTooDamaged ==========

    @Test
    void armorTooDamaged_低于阈值需下线() {
        assertTrue(RepairLogic.armorTooDamaged(100, 97, true, 5.0)); // 未损坏3% < 5%
    }

    @Test
    void armorTooDamaged_高于阈值不需下线() {
        assertFalse(RepairLogic.armorTooDamaged(100, 90, true, 5.0)); // 未损坏10% >= 5%
    }

    @Test
    void armorTooDamaged_阈值边界等于阈值不触发() {
        assertFalse(RepairLogic.armorTooDamaged(100, 95, true, 5.0)); // 未损坏5% == 5%，不低于
    }

    @Test
    void armorTooDamaged_不可损坏物品不触发() {
        assertFalse(RepairLogic.armorTooDamaged(0, 0, false, 5.0));
        assertFalse(RepairLogic.armorTooDamaged(100, 97, false, 5.0));
    }
}
