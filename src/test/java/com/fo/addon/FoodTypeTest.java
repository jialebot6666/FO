package com.fo.addon;

import com.fo.addon.utils.FoodType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodTypeTest {

    @Test
    void hasExactlyFourKinds() {
        // 用户拍板：食物四选一——面包/金苹果/牛排/金胡萝卜
        assertEquals(4, FoodType.values().length);
        Set<String> ids = new HashSet<>();
        for (FoodType f : FoodType.values()) ids.add(f.itemId);
        assertEquals(4, ids.size(), "四种食物 ID 不能重复");
    }

    @Test
    void itemIdsAreValidMinecraftIds() {
        for (FoodType f : FoodType.values()) {
            assertNotNull(f.itemId, "itemId 为空");
            assertTrue(f.itemId.startsWith("minecraft:"), "非法物品 ID: " + f.itemId);
            assertFalse(f.itemId.endsWith(":"), "物品 ID 缺少具体项: " + f.itemId);
        }
    }

    @Test
    void labelsAreChineseAndNonEmpty() {
        for (FoodType f : FoodType.values()) {
            assertNotNull(f.label, "label 为空: " + f);
            assertFalse(f.label.isEmpty(), "label 为空: " + f);
            assertTrue(f.label.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF),
                "label 不是中文: " + f.label);
        }
    }

    @Test
    void toStringReturnsChineseLabel() {
        // V4.24: Meteor EnumSetting 下拉框走 toString——必须中文（否则显示 BREAD 等英文枚举名，违反前端全汉化）
        assertEquals("面包", FoodType.BREAD.toString());
        assertEquals("金苹果", FoodType.GOLDEN_APPLE.toString());
        assertEquals("牛排", FoodType.COOKED_BEEF.toString());
        assertEquals("金胡萝卜", FoodType.GOLDEN_CARROT.toString());
    }
}
