package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 古城目标箱子记录（本地持久化）的防回归测试。
 *
 * <p>守住四件事：落盘/读回不丢数据、同一箱子不重复记、作用域之间不串、
 * 以及「按半径筛出该高亮的箱子」这条判定。
 */
class AncientChestStoreTest {

    private static final String SCOPE_A = "单人|minecraft:overworld";
    private static final String SCOPE_B = "play.example.com|minecraft:overworld";

    /** 模块与 Xaero 路径点都用这三个中文标签，改文案会同时影响两处，所以钉死。 */
    @Test
    void 三种标签的中文文案() {
        assertEquals("附魔金", AncientChestStore.LABEL_GOLD);
        assertEquals("迅捷3", AncientChestStore.LABEL_SWIFT);
        assertEquals("两者", AncientChestStore.LABEL_BOTH);
    }

    @Test
    void 落盘后读回内容不变() {
        AncientChestStore store = new AncientChestStore();
        store.add(SCOPE_A, new AncientChestStore.Mark(12383, -48, -9504, AncientChestStore.LABEL_GOLD));
        store.add(SCOPE_A, new AncientChestStore.Mark(12390, -47, -9496, AncientChestStore.LABEL_SWIFT));
        store.add(SCOPE_B, new AncientChestStore.Mark(100, 64, 200, AncientChestStore.LABEL_BOTH));

        AncientChestStore back = AncientChestStore.fromJson(store.toJson());

        assertEquals(2, back.size(SCOPE_A));
        assertEquals(1, back.size(SCOPE_B));
        List<AncientChestStore.Mark> a = back.marks(SCOPE_A);
        assertEquals(new AncientChestStore.Mark(12383, -48, -9504, AncientChestStore.LABEL_GOLD), a.get(0));
        assertEquals(AncientChestStore.LABEL_SWIFT, a.get(1).label());
        assertEquals(new AncientChestStore.Mark(100, 64, 200, AncientChestStore.LABEL_BOTH),
            back.marks(SCOPE_B).get(0));
    }

    @Test
    void 同一箱子不会重复记录但会更新标签() {
        AncientChestStore store = new AncientChestStore();
        assertTrue(store.add(SCOPE_A, new AncientChestStore.Mark(1, 2, 3, AncientChestStore.LABEL_GOLD)));
        // 完全相同：不算新增
        assertFalse(store.add(SCOPE_A, new AncientChestStore.Mark(1, 2, 3, AncientChestStore.LABEL_GOLD)));
        assertEquals(1, store.size(SCOPE_A));
        // 同 (x,z) 但标签变了：仍是一条，标签被更新
        assertFalse(store.add(SCOPE_A, new AncientChestStore.Mark(1, 2, 3, AncientChestStore.LABEL_BOTH)));
        assertEquals(1, store.size(SCOPE_A));
        assertEquals(AncientChestStore.LABEL_BOTH, store.marks(SCOPE_A).get(0).label());
    }

    @Test
    void 按水平坐标删除忽略Y() {
        AncientChestStore store = new AncientChestStore();
        store.add(SCOPE_A, new AncientChestStore.Mark(10, -50, 20, AncientChestStore.LABEL_GOLD));
        // 箱子坐标唯一，删除时只看 (x,z)；传错 y 也要能删掉
        assertTrue(store.remove(SCOPE_A, 10, 20));
        assertTrue(store.isEmpty(SCOPE_A));
        // 再删一次返回 false，且不影响别的记录
        assertFalse(store.remove(SCOPE_A, 10, 20));
        store.add(SCOPE_A, new AncientChestStore.Mark(11, -50, 21, AncientChestStore.LABEL_GOLD));
        assertFalse(store.remove(SCOPE_A, 10, 20));
        assertEquals(1, store.size(SCOPE_A));
    }

    @Test
    void 作用域之间互不串数据() {
        AncientChestStore store = new AncientChestStore();
        store.add(SCOPE_A, new AncientChestStore.Mark(1, 2, 3, AncientChestStore.LABEL_GOLD));
        store.add(SCOPE_B, new AncientChestStore.Mark(9, 9, 9, AncientChestStore.LABEL_SWIFT));

        assertEquals(1, store.size(SCOPE_A));
        assertEquals(1, store.size(SCOPE_B));
        // 在 A 服删掉同名坐标，不能动到 B 服
        assertTrue(store.remove(SCOPE_A, 1, 3));
        assertEquals(0, store.size(SCOPE_A));
        assertEquals(1, store.size(SCOPE_B));
        // 从未出现过的作用域
        assertEquals(0, store.size("别的服|minecraft:overworld"));
        assertTrue(store.marks("别的服|minecraft:overworld").isEmpty());
    }

    @Test
    void 半径筛选用的是三维距离() {
        List<AncientChestStore.Mark> marks = List.of(
            new AncientChestStore.Mark(0, 0, 0, AncientChestStore.LABEL_GOLD),      // 距离 0
            new AncientChestStore.Mark(64, 0, 0, AncientChestStore.LABEL_SWIFT),   // 距离 64（贴边，算在内）
            new AncientChestStore.Mark(65, 0, 0, AncientChestStore.LABEL_GOLD),    // 距离 65（出圈）
            new AncientChestStore.Mark(0, 100, 0, AncientChestStore.LABEL_GOLD));  // 垂直方向出圈

        List<AncientChestStore.Mark> near = AncientChestStore.within(marks, 0, 0, 0, 64);
        assertEquals(2, near.size());
        assertEquals(0, near.get(0).x());
        assertEquals(64, near.get(1).x());

        // 负数坐标也要正确（古城常在负象限）
        List<AncientChestStore.Mark> neg = List.of(
            new AncientChestStore.Mark(-12500, -50, -9619, AncientChestStore.LABEL_GOLD));
        assertEquals(1, AncientChestStore.within(neg, -12480, -50, -9619, 64).size());
        assertEquals(0, AncientChestStore.within(neg, -12300, -50, -9619, 64).size());
    }

    @Test
    void 文件损坏或空内容时退化成空记录而不抛异常() {
        assertTrue(AncientChestStore.fromJson(null).marks(SCOPE_A).isEmpty());
        assertTrue(AncientChestStore.fromJson("").marks(SCOPE_A).isEmpty());
        assertTrue(AncientChestStore.fromJson("   ").marks(SCOPE_A).isEmpty());
        assertTrue(AncientChestStore.fromJson("这不是 json").marks(SCOPE_A).isEmpty());
        assertTrue(AncientChestStore.fromJson("{\"version\":1}").marks(SCOPE_A).isEmpty());
        assertTrue(AncientChestStore.fromJson("{\"scopes\":\"错类型\"}").marks(SCOPE_A).isEmpty());
    }

    @Test
    void 空记录序列化后仍能读回() {
        AncientChestStore store = new AncientChestStore();
        AncientChestStore back = AncientChestStore.fromJson(store.toJson());
        assertTrue(back.marks(SCOPE_A).isEmpty());
    }

    @Test
    void 作用域名里的特殊字符不会破坏JSON() {
        String tricky = "服\"务\\器|minecraft:overworld";
        AncientChestStore store = new AncientChestStore();
        store.add(tricky, new AncientChestStore.Mark(1, 2, 3, AncientChestStore.LABEL_GOLD));

        AncientChestStore back = AncientChestStore.fromJson(store.toJson());
        assertEquals(1, back.size(tricky));
        assertEquals(1, back.marks(tricky).get(0).x());
        assertNull(store.marks("服务器|minecraft:overworld").stream().findFirst().orElse(null));
    }
}
