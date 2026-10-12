package com.fo.addon;

import com.fo.addon.ancient.core.AncientStealStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AncientStealStats 纯逻辑测试：古城模块「自动偷取」的累计统计（V6.27）。
 *
 * <p>锁定三件事：① 计数按累加、不互相覆盖；② 附魔书按等级分开计；
 * ③ 展示文案与序列化（跨重启要能读回来）。
 */
public class AncientStealStatsTest {

    private static final String APPLE = AncientStealStats.GOLDEN_APPLE_ID;
    private static final String SWIFT = AncientStealStats.SWIFT_SNEAK_ID;

    // ---------- 计数 ----------

    @Test
    void countsAccumulate() {
        AncientStealStats s = new AncientStealStats();
        s.addItem(APPLE, 2);
        s.addItem(APPLE, 3);
        assertEquals(5, s.itemCount(APPLE));
        assertEquals(1, s.distinctItems());
    }

    @Test
    void unknownItemIsZero() {
        AncientStealStats s = new AncientStealStats();
        assertEquals(0, s.itemCount(APPLE));
        assertEquals(0, s.bookCount(SWIFT, 3));
    }

    /** 非法入参不该产生负数或幽灵条目。 */
    @Test
    void ignoresIllegalInput() {
        AncientStealStats s = new AncientStealStats();
        s.addItem(null, 5);
        s.addItem(APPLE, 0);
        s.addItem(APPLE, -3);
        s.addBook(SWIFT, 0, 4);      // 等级 0 = 不是附魔书
        s.addBook(null, 3, 4);
        assertEquals(0, s.itemCount(APPLE));
        assertEquals(0, s.bookCount(SWIFT, 3));
        assertEquals(0, s.distinctItems());
    }

    @Test
    void bookCountsSplitByLevel() {
        AncientStealStats s = new AncientStealStats();
        s.addBook(SWIFT, 3, 2);
        s.addBook(SWIFT, 1, 7);
        assertEquals(2, s.bookCount(SWIFT, 3));
        assertEquals(7, s.bookCount(SWIFT, 1));
        assertEquals(0, s.bookCount(SWIFT, 2));
    }

    // ---------- 展示文案 ----------

    /** 默认情况（只偷满级书）= 用户要求的三行。 */
    @Test
    void displayLinesDefaultThreeLines() {
        AncientStealStats s = new AncientStealStats();
        s.addItem(APPLE, 12);
        s.addBook(SWIFT, 3, 5);
        List<String> lines = s.displayLines();
        assertEquals(3, lines.size());
        assertEquals("您已使用本插件共计获取了", lines.get(0));
        assertEquals("12 个附魔金苹果", lines.get(1));
        assertEquals("5 迅捷潜行III", lines.get(2));
    }

    /** 展示时抬头当"框"的标题、内容行放框里，所以两者必须分得干净（V6.29）。 */
    @Test
    void headerAndBodySplitCleanly() {
        AncientStealStats s = new AncientStealStats();
        s.addItem(APPLE, 12);
        s.addBook(SWIFT, 3, 5);

        assertEquals("您已使用本插件共计获取了", s.displayHeader());
        // 抬头里不能混进数字
        assertFalse(s.displayHeader().matches(".*\\d.*"));
        // 内容行里不能重复抬头
        for (String line : s.displayBody()) {
            assertFalse(line.contains("您已使用本插件共计获取了"));
        }
        List<String> all = s.displayLines();
        assertEquals(s.displayHeader(), all.get(0));
        assertEquals(s.displayBody(), all.subList(1, all.size()));
    }

    @Test
    void displayLinesEmptyStatsStillThreeLines() {
        List<String> lines = new AncientStealStats().displayLines();
        assertEquals(3, lines.size());
        assertEquals("0 个附魔金苹果", lines.get(1));
        assertEquals("0 迅捷潜行III", lines.get(2));
    }

    /** 只有下调过门槛、真的偷到过低级书，才补出 II / I 两行。 */
    @Test
    void displayLinesAddsLowerLevelsOnlyWhenStolen() {
        AncientStealStats s = new AncientStealStats();
        s.addBook(SWIFT, 3, 1);
        s.addBook(SWIFT, 2, 4);
        List<String> lines = s.displayLines();
        assertEquals(4, lines.size());
        assertTrue(lines.contains("4 迅捷潜行II"));
        assertFalse(lines.contains("0 迅捷潜行I"));

        s.addBook(SWIFT, 1, 2);
        assertEquals(5, s.displayLines().size());
        assertTrue(s.displayLines().contains("2 迅捷潜行I"));
    }

    // ---------- 序列化（跨重启） ----------

    @Test
    void jsonRoundTrip() {
        AncientStealStats a = new AncientStealStats();
        a.addItem(APPLE, 42);
        a.addItem("minecraft:diamond", 3);
        a.addBook(SWIFT, 3, 9);
        a.addBook(SWIFT, 1, 1);

        AncientStealStats b = AncientStealStats.fromJson(a.toJson());
        assertEquals(42, b.itemCount(APPLE));
        assertEquals(3, b.itemCount("minecraft:diamond"));
        assertEquals(9, b.bookCount(SWIFT, 3));
        assertEquals(1, b.bookCount(SWIFT, 1));
        assertEquals(a.toJson(), b.toJson());
    }

    /** 坏文件/空文件一律当空统计，不能把模块拖挂。 */
    @Test
    void badJsonFallsBackToEmpty() {
        assertEquals(0, AncientStealStats.fromJson(null).distinctItems());
        assertEquals(0, AncientStealStats.fromJson("").distinctItems());
        assertEquals(0, AncientStealStats.fromJson("   ").distinctItems());
        assertEquals(0, AncientStealStats.fromJson("{不是 JSON").distinctItems());
        assertEquals(0, AncientStealStats.fromJson("[1,2,3]").distinctItems());
        // 结构对但键不认识 → 空统计，不抛
        assertEquals(0, AncientStealStats.fromJson("{\"items\":\"oops\"}").distinctItems());
    }

    @Test
    void replaceWithCopiesEverything() {
        AncientStealStats from = new AncientStealStats();
        from.addItem(APPLE, 7);
        from.addBook(SWIFT, 3, 2);

        AncientStealStats to = new AncientStealStats();
        to.addItem("minecraft:dirt", 99);
        to.replaceWith(from);

        assertEquals(0, to.itemCount("minecraft:dirt"));   // 被整体覆盖
        assertEquals(7, to.itemCount(APPLE));
        assertEquals(2, to.bookCount(SWIFT, 3));

        to.replaceWith(null);                              // null = 清空
        assertEquals(0, to.itemCount(APPLE));
    }
}
