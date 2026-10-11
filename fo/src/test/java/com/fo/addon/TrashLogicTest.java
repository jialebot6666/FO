package com.fo.addon;

import com.fo.addon.utils.TrashLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrashLogicTest {

    @Test
    void blacklistDropsListedItems() {
        assertTrue(TrashLogic.shouldDrop(TrashLogic.Mode.BLACKLIST, true, true));
    }

    @Test
    void blacklistKeepsUnlistedItems() {
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.BLACKLIST, true, false));
    }

    @Test
    void whitelistKeepsListedItems() {
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, true, true));
    }

    @Test
    void whitelistDropsUnlistedItems() {
        assertTrue(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, true, false));
    }

    @Test
    void emptyListNeverDropsAnything() {
        // 安全保护：列表为空时无论模式如何都不丢（防止白名单误清空背包）
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.BLACKLIST, false, true));
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, false, false));
        assertFalse(TrashLogic.shouldDrop(TrashLogic.Mode.WHITELIST, false, true));
    }

    @Test
    void modeToStringIsChineseForUi() {
        // 前端汉化：下拉框/UI 走 EnumSetting 的 toString
        assertEquals("白名单", TrashLogic.Mode.WHITELIST.toString());
        assertEquals("黑名单", TrashLogic.Mode.BLACKLIST.toString());
    }

    // ==================== 附魔书判定 ====================

    @Test
    void 附魔书_全部丢弃模式一律丢() {
        assertEquals(Boolean.TRUE, TrashLogic.decideBook(TrashLogic.BookMode.DROP_ALL, true, 3, 3));
        assertEquals(Boolean.TRUE, TrashLogic.decideBook(TrashLogic.BookMode.DROP_ALL, true, 0, 3));
    }

    @Test
    void 附魔书_全部保留模式一律留() {
        assertEquals(Boolean.FALSE, TrashLogic.decideBook(TrashLogic.BookMode.KEEP_ALL, true, 1, 3));
    }

    @Test
    void 附魔书_保等级模式_达标留不达标丢() {
        assertEquals(Boolean.FALSE, TrashLogic.decideBook(TrashLogic.BookMode.KEEP_LEVEL, true, 3, 3));
        assertEquals(Boolean.FALSE, TrashLogic.decideBook(TrashLogic.BookMode.KEEP_LEVEL, true, 5, 3));
        assertEquals(Boolean.TRUE, TrashLogic.decideBook(TrashLogic.BookMode.KEEP_LEVEL, true, 2, 3));
        assertEquals(Boolean.TRUE, TrashLogic.decideBook(TrashLogic.BookMode.KEEP_LEVEL, true, 0, 1));
    }

    @Test
    void 非附魔书或跟随模式回落名单逻辑() {
        assertNull(TrashLogic.decideBook(TrashLogic.BookMode.FOLLOW_LIST, true, 3, 3));
        assertNull(TrashLogic.decideBook(TrashLogic.BookMode.KEEP_LEVEL, false, 0, 3));
        assertNull(TrashLogic.decideBook(TrashLogic.BookMode.DROP_ALL, false, 0, 3));
    }
}
