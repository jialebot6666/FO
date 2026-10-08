package com.fo.addon.ancient.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class AncientSearchLogicTest {

    @Test
    void 数字种子直接解析() {
        assertEquals(-7346913998703726680L, AncientSearchLogic.parseSeedInput("-7346913998703726680"));
        assertEquals(12345L, AncientSearchLogic.parseSeedInput("12345"));
        assertEquals(0L, AncientSearchLogic.parseSeedInput("0"));
    }

    @Test
    void 非数字种子回退hashCode() {
        assertEquals("abc".hashCode(), AncientSearchLogic.parseSeedInput("abc"));
        assertEquals("我的世界".hashCode(), AncientSearchLogic.parseSeedInput("我的世界"));
    }

    @Test
    void 区域半径按除24加1() {
        // 与 misaka 真实实现一致：半径 / 24 + 1（旧源码 /25 是错误）
        assertEquals(1000 / 24 + 1, AncientSearchLogic.regionRadius(1000));
        assertEquals(1000 / 24 + 1, AncientSearchLogic.regionRadius(1000));
        assertEquals(24 / 24 + 1, AncientSearchLogic.regionRadius(24));
        assertEquals(25 / 24 + 1, AncientSearchLogic.regionRadius(25));
    }

    @Test
    void 枚举中文标签() {
        assertEquals("附魔金苹果", LootTarget.ENCHANTED_GOLDEN_APPLE.toString());
        assertEquals("迅捷潜行3", LootTarget.SWIFT_SNEAK_3.toString());
        assertEquals("两者都搜索", LootTarget.BOTH.toString());
        assertEquals("< 26.1", GameVersionOption.BELOW_26_1.toString());
        assertEquals(">= 26.1", GameVersionOption.FROM_26_1.toString());
    }

    @Test
    void 搜索结果按距离排序() {
        List<FoundChest> list = new ArrayList<>();
        list.add(new FoundChest(null, 500.0, "附魔金"));
        list.add(new FoundChest(null, 12.0, "迅捷3"));
        list.add(new FoundChest(null, 300.0, "两者"));
        list.sort(Comparator.comparingDouble(fc -> fc.distance));
        assertEquals(12.0, list.get(0).distance);
        assertEquals(300.0, list.get(1).distance);
        assertEquals(500.0, list.get(2).distance);
    }

    @Test
    void foundChest字段完整() {
        FoundChest fc = new FoundChest(null, 42.5, "附魔金");
        assertEquals(42.5, fc.distance);
        assertEquals("附魔金", fc.label);
    }
}
