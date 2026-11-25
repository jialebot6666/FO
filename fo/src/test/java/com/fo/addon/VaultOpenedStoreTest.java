package com.fo.addon;

import com.fo.addon.utils.VaultOpenedStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「已打开的不祥宝库」本地记录测试（V6.9p25 新增）。
 *
 * <p>锁死：右键记录只记一次、按作用域隔离、清理只清指定的/全部、
 * JSON 存取往返不丢数据、坏文件当空记录。
 */
public class VaultOpenedStoreTest {

    private static final String SCOPE = "3c3u.example.com|minecraft:overworld";
    private static final String SCOPE_END = "单人|minecraft:the_end";

    @Test
    void markOnlyOnce() {
        VaultOpenedStore store = new VaultOpenedStore();
        assertTrue(store.mark(SCOPE, 10, -20, 30), "第一次右键应记为新增");
        assertFalse(store.mark(SCOPE, 10, -20, 30), "同一个宝库再右键不算新增");
        assertEquals(1, store.count(SCOPE));
    }

    @Test
    void scopesAreIsolated() {
        VaultOpenedStore store = new VaultOpenedStore();
        store.mark(SCOPE, 1, 2, 3);
        assertTrue(store.isOpened(SCOPE, 1, 2, 3));
        assertFalse(store.isOpened(SCOPE_END, 1, 2, 3), "别的维度/服务器不应串记录");
        assertEquals(1, store.totalCount());
        store.mark(SCOPE_END, 1, 2, 3);
        assertEquals(2, store.totalCount());
    }

    @Test
    void clearScopeOnlyRemovesThatScope() {
        VaultOpenedStore store = new VaultOpenedStore();
        store.mark(SCOPE, 1, 2, 3);
        store.mark(SCOPE, 4, 5, 6);
        store.mark(SCOPE_END, 7, 8, 9);
        store.clearScope(SCOPE);
        assertEquals(0, store.count(SCOPE));
        assertEquals(1, store.count(SCOPE_END));
        assertEquals(1, store.totalCount());
    }

    @Test
    void clearAll() {
        VaultOpenedStore store = new VaultOpenedStore();
        store.mark(SCOPE, 1, 2, 3);
        store.mark(SCOPE_END, 4, 5, 6);
        store.clearAll();
        assertEquals(0, store.totalCount());
        assertFalse(store.isOpened(SCOPE, 1, 2, 3));
    }

    @Test
    void jsonRoundTrip() {
        VaultOpenedStore store = new VaultOpenedStore();
        store.mark(SCOPE, 10, -20, 30);
        store.mark(SCOPE, -1, -54, 2);
        store.mark(SCOPE_END, 7, 8, 9);

        VaultOpenedStore back = VaultOpenedStore.fromJson(store.toJson());
        assertEquals(3, back.totalCount());
        assertTrue(back.isOpened(SCOPE, 10, -20, 30));
        assertTrue(back.isOpened(SCOPE, -1, -54, 2));
        assertTrue(back.isOpened(SCOPE_END, 7, 8, 9));
        assertFalse(back.isOpened(SCOPE, 11, -20, 30));
    }

    @Test
    void brokenJsonBecomesEmpty() {
        assertFalse(VaultOpenedStore.fromJson("{not json").isOpened(SCOPE, 1, 2, 3));
        assertEquals(0, VaultOpenedStore.fromJson("{\"version\":1}").totalCount());
        assertEquals(0, VaultOpenedStore.fromJson("").totalCount());
        assertEquals(0, VaultOpenedStore.fromJson(null).totalCount());
    }

    @Test
    void negativeCoordinatesSurviveRoundTrip() {
        VaultOpenedStore store = new VaultOpenedStore();
        store.mark(SCOPE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);
        VaultOpenedStore back = VaultOpenedStore.fromJson(store.toJson());
        assertTrue(back.isOpened(SCOPE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE));
    }
}
