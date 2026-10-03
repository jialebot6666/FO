package com.fo.addon;

import com.fo.addon.utils.SupplyFingerprintLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyFingerprintLogicTest {

    @Test
    void fingerprintContainsDiamondAndNetheriteShovel() {
        // 用户拍板：补给盒内容指纹 = 盒内必须有钻石铲或下界合金铲（硬性标准）
        assertTrue(SupplyFingerprintLogic.FINGERPRINT_SHOVEL_IDS.contains("minecraft:diamond_shovel"));
        assertTrue(SupplyFingerprintLogic.FINGERPRINT_SHOVEL_IDS.contains("minecraft:netherite_shovel"));
    }

    @Test
    void fingerprintExcludesLowTierShovels() {
        // 石铲/木铲/铁铲不算——防低级误判（石铲在默认补给物资里）
        assertFalse(SupplyFingerprintLogic.FINGERPRINT_SHOVEL_IDS.contains("minecraft:stone_shovel"));
        assertFalse(SupplyFingerprintLogic.FINGERPRINT_SHOVEL_IDS.contains("minecraft:wooden_shovel"));
        assertFalse(SupplyFingerprintLogic.FINGERPRINT_SHOVEL_IDS.contains("minecraft:iron_shovel"));
    }

    @Test
    void fingerprintIsExactlyTwoItems() {
        assertEquals(2, SupplyFingerprintLogic.FINGERPRINT_SHOVEL_IDS.size(),
            "指纹标准应恰好钻石+合金铲两种");
    }
}
