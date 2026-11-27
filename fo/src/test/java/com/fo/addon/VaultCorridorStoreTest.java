package com.fo.addon;

import com.fo.addon.vault.VaultCorridorStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 宝库连廊进度存档测试（7.0p1 新增）。
 *
 * <p>锁死三条行为：
 * ① 已连 / 已放弃按「服务器+维度」分作用域，互不串；
 * ② JSON 往返不丢数据，空存档解析成空；
 * ③ 脏标记只在真有写入时置位，坏文件不炸模块。
 */
public class VaultCorridorStoreTest {

    @Test
    void markAndQueryWithinScope() {
        VaultCorridorStore store = new VaultCorridorStore();
        assertTrue(store.markConnected("srv|dim", 10, 64, 20));
        assertTrue(store.isConnected("srv|dim", 10, 64, 20));
        assertFalse(store.isConnected("srv|dim", 11, 64, 20));
        assertTrue(store.markAbandoned("srv|dim", 30, 70, 40));
        assertTrue(store.isAbandoned("srv|dim", 30, 70, 40));
        assertFalse(store.isConnected("srv|dim", 30, 70, 40), "放弃不等于已连");
    }

    @Test
    void scopesDoNotMix() {
        VaultCorridorStore store = new VaultCorridorStore();
        store.markConnected("serverA|dim1", 1, 2, 3);
        assertFalse(store.isConnected("serverB|dim1", 1, 2, 3));
        assertFalse(store.isConnected("serverA|dim2", 1, 2, 3));
        assertEquals(1, store.connectedCount("serverA|dim1"));
        assertEquals(0, store.connectedCount("serverB|dim1"));
    }

    @Test
    void duplicateMarkIsNotNew() {
        VaultCorridorStore store = new VaultCorridorStore();
        assertTrue(store.markConnected("s|d", 5, 6, 7));
        assertFalse(store.markConnected("s|d", 5, 6, 7), "重复标记返回 false");
        assertEquals(1, store.connectedCount("s|d"));
    }

    @Test
    void jsonRoundTripKeepsBothSets() {
        VaultCorridorStore store = new VaultCorridorStore();
        store.markConnected("s|d", 1, 2, 3);
        store.markConnected("s|d", 4, 5, 6);
        store.markAbandoned("s|d", 7, 8, 9);
        String json = store.toJson();

        VaultCorridorStore parsed = VaultCorridorStore.fromJson(json);
        assertTrue(parsed.isConnected("s|d", 1, 2, 3));
        assertTrue(parsed.isConnected("s|d", 4, 5, 6));
        assertTrue(parsed.isAbandoned("s|d", 7, 8, 9));
        assertEquals(2, parsed.connectedCount("s|d"));
        assertEquals(1, parsed.abandonedCount("s|d"));
        assertFalse(parsed.isDirty(), "刚解析出来的存档不是脏的");
    }

    @Test
    void emptyAndBrokenJsonAreSafe() {
        assertFalse(VaultCorridorStore.fromJson(null).isConnected("s|d", 0, 0, 0));
        assertFalse(VaultCorridorStore.fromJson("").isConnected("s|d", 0, 0, 0));
        assertFalse(VaultCorridorStore.fromJson("{坏掉的").isConnected("s|d", 0, 0, 0));
    }

    @Test
    void dirtyOnlyWhenChanged() {
        VaultCorridorStore store = new VaultCorridorStore();
        assertFalse(store.isDirty());
        store.markConnected("s|d", 1, 1, 1);
        assertTrue(store.isDirty());
    }
}
