package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 黑名单：作用域隔离、城市半径、箱子坐标、序列化。 */
class EnchantedGoldStoreTest {

    private static final String A = "单人|minecraft:overworld";
    private static final String B = "play.example.com|minecraft:overworld";

    @Test
    void 城市黑名单按半径命中() {
        EnchantedGoldStore store = new EnchantedGoldStore();
        store.blacklistCity(A, 0, 0, EnchantedGoldStore.REASON_UNDERWATER_CITY);
        assertTrue(store.isCityBlacklisted(A, 10, 10));
        assertTrue(store.isCityBlacklisted(A, 90, 0));
        assertFalse(store.isCityBlacklisted(A, 200, 0));
    }

    @Test
    void 作用域之间不串() {
        EnchantedGoldStore store = new EnchantedGoldStore();
        store.blacklistCity(A, 0, 0, EnchantedGoldStore.REASON_MANUAL);
        store.blacklistChest(A, 5, 60, 5, EnchantedGoldStore.REASON_SHAFT_FLUID);
        assertTrue(store.isCityBlacklisted(A, 0, 0));
        assertFalse(store.isCityBlacklisted(B, 0, 0));
        assertFalse(store.isChestBlacklisted(B, 5, 60, 5));
    }

    @Test
    void 箱子按水平坐标命中且忽略Y() {
        EnchantedGoldStore store = new EnchantedGoldStore();
        store.blacklistChest(A, 5, 60, 5, EnchantedGoldStore.REASON_TUNNEL_FLUID);
        assertTrue(store.isChestBlacklisted(A, 5, 60, 5));
        assertTrue(store.isChestBlacklisted(A, 5, -50, 5));
        assertFalse(store.isChestBlacklisted(A, 5, 60, 6));
    }

    @Test
    void 序列化往返不丢数据() {
        EnchantedGoldStore store = new EnchantedGoldStore();
        store.blacklistCity(A, 100, -200, EnchantedGoldStore.REASON_FAKE_CITY);
        store.blacklistChest(A, 10, -50, 20, EnchantedGoldStore.REASON_POND_ABOVE);
        store.blacklistChest(B, 1, 2, 3, EnchantedGoldStore.REASON_NO_SAFE_MOUTH);

        EnchantedGoldStore back = EnchantedGoldStore.fromJson(store.toJson());
        assertEquals(1, back.cityCount(A));
        assertEquals(1, back.chestCount(A));
        assertEquals(1, back.chestCount(B));
        assertTrue(back.isCityBlacklisted(A, 100, -200));
        assertTrue(back.isChestBlacklisted(A, 10, -50, 20));
        assertEquals(EnchantedGoldStore.REASON_POND_ABOVE, back.chests(A).get(0).reason());
    }

    @Test
    void 清空只影响当前作用域() {
        EnchantedGoldStore store = new EnchantedGoldStore();
        store.blacklistCity(A, 0, 0, EnchantedGoldStore.REASON_MANUAL);
        store.blacklistCity(B, 0, 0, EnchantedGoldStore.REASON_MANUAL);
        store.blacklistChest(A, 0, 0, 0, EnchantedGoldStore.REASON_MANUAL);
        store.clearCities(A);
        assertFalse(store.isCityBlacklisted(A, 0, 0));
        assertTrue(store.isCityBlacklisted(B, 0, 0));
        assertEquals(1, store.chestCount(A));
        store.clearChests(A);
        assertEquals(0, store.chestCount(A));
    }

    @Test
    void 坏文件当空处理() {
        EnchantedGoldStore store = EnchantedGoldStore.fromJson("{broken");
        assertEquals(0, store.cityCount(A));
        assertEquals(0, store.chestCount(A));
    }
}
