package com.fo.addon;

import com.fo.addon.utils.threec.ThreeCCacheStore;
import com.fo.addon.utils.threec.ThreeCPlayerService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeCCacheStoreTest {
    private static ThreeCCacheStore.Entry entry(String name, long seconds, Long month, long at) {
        return new ThreeCCacheStore.Entry(new ThreeCPlayerService.PlayerTime(name, seconds, month), at);
    }

    @Test
    void roundTripKeepsAllFields() {
        Map<String, ThreeCCacheStore.Entry> source = new LinkedHashMap<>();
        source.put("alice", entry("Alice", 3600L, 120L, 1_700_000_000_000L));
        source.put("bob_2", entry("bob_2", 0L, null, 1_600_000_000_000L));

        Map<String, ThreeCCacheStore.Entry> loaded = new LinkedHashMap<>();
        ThreeCCacheStore.readInto(ThreeCCacheStore.toJson(source), loaded);

        assertEquals(2, loaded.size());
        ThreeCCacheStore.Entry alice = loaded.get("alice");
        assertEquals("Alice", alice.data().name());
        assertEquals(3600L, alice.data().totalSeconds());
        assertEquals(120L, alice.data().monthSeconds());
        assertEquals(1_700_000_000_000L, alice.queriedAtMillis());
        assertNull(loaded.get("bob_2").data().monthSeconds());
    }

    @Test
    void expiryHonoursTtl() {
        ThreeCCacheStore.Entry e = entry("Alice", 100L, null, 1_000L);
        // ttl=0 表示永不过期
        assertFalse(e.isExpired(0, 10_000_000L));
        // ttl=1 小时，查询于 1 秒，2 小时后过期
        assertFalse(e.isExpired(1, 1_000L + 3_599_999L));
        // 满 1 小时即视为过期
        assertTrue(e.isExpired(1, 1_000L + 3_600_000L));
    }

    @Test
    void corruptEntryIsSkipped() {
        String json = "{\"version\":1,\"entries\":{\"good\":{\"name\":\"Good\",\"duration\":60,\"month\":null,\"at\":5},"
            + "\"bad\":{\"name\":\"Bad\"}}}";
        Map<String, ThreeCCacheStore.Entry> loaded = new LinkedHashMap<>();
        ThreeCCacheStore.readInto(json, loaded);
        assertEquals(1, loaded.size());
        assertEquals(60L, loaded.get("good").data().totalSeconds());
    }
}
