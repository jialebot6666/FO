package com.fo.addon;

import com.fo.addon.ancient.core.AncientSeedStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AncientSeedStore 纯逻辑测试：古城模块「已应用的种子」持久化（V6.28）。
 *
 * <p>锁定：存得下、读得回、坏文件当没存过、手动清除后不再自动应用。
 */
public class AncientSeedStoreTest {

    @Test
    void emptyByDefault() {
        AncientSeedStore s = new AncientSeedStore();
        assertNull(s.seed());
        assertFalse(s.hasSeed());
    }

    @Test
    void jsonRoundTrip() {
        AncientSeedStore a = new AncientSeedStore();
        a.setSeed(-7346913998703726680L);

        AncientSeedStore b = AncientSeedStore.fromJson(a.toJson());
        assertEquals(-7346913998703726680L, b.seed());
        assertTrue(b.hasSeed());
        assertEquals(a.toJson(), b.toJson());
    }

    /** 清除之后要写回 null，且读回来仍是"没存过"。 */
    @Test
    void clearedSeedStaysCleared() {
        AncientSeedStore a = new AncientSeedStore();
        a.setSeed(123L);
        a.setSeed(null);

        AncientSeedStore b = AncientSeedStore.fromJson(a.toJson());
        assertNull(b.seed());
        assertFalse(b.hasSeed());
    }

    @Test
    void jsonShapeIsStable() {
        AncientSeedStore s = new AncientSeedStore();
        s.setSeed(42L);
        assertEquals("{\"version\":1,\"seed\":42}", s.toJson());
    }

    /** 坏文件/空文件一律当"没存过"，不能抛出去把模块拖挂。 */
    @Test
    void badJsonFallsBackToEmpty() {
        assertNull(AncientSeedStore.fromJson(null).seed());
        assertNull(AncientSeedStore.fromJson("").seed());
        assertNull(AncientSeedStore.fromJson("   ").seed());
        assertNull(AncientSeedStore.fromJson("{不是 JSON").seed());
        assertNull(AncientSeedStore.fromJson("[1,2,3]").seed());
        assertNull(AncientSeedStore.fromJson("{}").seed());
        assertNull(AncientSeedStore.fromJson("{\"seed\":\"abc\"}").seed());
    }
}
