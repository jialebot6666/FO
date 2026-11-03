package com.fo.addon;

import com.fo.addon.modules.StashMover;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 自动搬运界面文本守护（前端全中文，枚举走 toString）。 */
class StashMoverUiTextTest {

    private static boolean hasChinese(String s) {
        for (char c : s.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    private static boolean hasAsciiLetter(String s) {
        for (char c : s.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    @Test
    void 模式名全中文() {
        for (StashMover.Mode m : StashMover.Mode.values()) {
            assertTrue(hasChinese(m.toString()), m + " 缺中文");
            assertFalse(hasAsciiLetter(m.toString()), m + " 含英文: " + m);
        }
    }

    @Test
    void 待搬运方式全中文() {
        for (StashMover.ChestMode m : StashMover.ChestMode.values()) {
            assertTrue(hasChinese(m.toString()), m + " 缺中文");
            assertFalse(hasAsciiLetter(m.toString()), m + " 含英文: " + m);
        }
    }

    @Test
    void 自杀方式全中文() {
        for (StashMover.SuicideMode m : StashMover.SuicideMode.values()) {
            assertTrue(hasChinese(m.toString()), m + " 缺中文");
            assertFalse(hasAsciiLetter(m.toString()), m + " 含英文: " + m);
        }
    }

    @Test
    void 私聊指令显示正确() {
        // 这一项本身就是服务器指令，允许 ASCII
        assertEquals("/m", StashMover.ChatCommand.SLASH_M.toString());
        assertEquals("/tell", StashMover.ChatCommand.TELL.toString());
    }

    @Test
    void 模式项齐全() {
        assertEquals(3, StashMover.Mode.values().length);
        assertEquals(3, StashMover.ChestMode.values().length);
        assertEquals(2, StashMover.SuicideMode.values().length);
    }
}
