package com.fo.addon;

import com.fo.addon.utils.TeleportFloodDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 卡脚检测器（V6.7p9）：5 秒内 6 次位置校正包 = 卡住。 */
class TeleportFloodDetectorTest {

    @Test
    void 五次不触发第六次触发() {
        TeleportFloodDetector d = new TeleportFloodDetector(5000, 6);
        long t = 1_000_000L;
        for (int i = 0; i < 5; i++) {
            assertFalse(d.record(t + i * 100), "第 " + (i + 1) + " 次不该触发");
        }
        assertTrue(d.record(t + 500), "第 6 次应触发");
    }

    @Test
    void 超出窗口的旧记录会被丢弃() {
        TeleportFloodDetector d = new TeleportFloodDetector(5000, 6);
        long t = 1_000_000L;
        // 先来 5 次（会触发前的临界状态）
        for (int i = 0; i < 5; i++) d.record(t);
        assertEquals(5, d.count(t));
        // 6 秒后再来一次：前面 5 次都过期了，只剩 1 次，不该触发
        assertFalse(d.record(t + 6000));
        assertEquals(1, d.count(t + 6000));
    }

    @Test
    void 刚好等于窗口边界仍算窗口内() {
        TeleportFloodDetector d = new TeleportFloodDetector(5000, 2);
        long t = 1_000_000L;
        d.record(t);
        // 正好 5000ms 后：边界点保留（只有 > window 才丢）
        assertTrue(d.record(t + 5000));
    }

    @Test
    void clear之后重新计数() {
        TeleportFloodDetector d = new TeleportFloodDetector(5000, 2);
        long t = 1_000_000L;
        d.record(t);
        d.clear();
        assertEquals(0, d.count(t));
        assertFalse(d.record(t + 1));
    }

    @Test
    void count不会新增记录() {
        TeleportFloodDetector d = new TeleportFloodDetector(5000, 3);
        long t = 1_000_000L;
        d.record(t);
        d.count(t);
        d.count(t);
        assertEquals(1, d.count(t));
    }

    @Test
    void 参数非法直接抛异常() {
        assertThrows(IllegalArgumentException.class, () -> new TeleportFloodDetector(0, 6));
        assertThrows(IllegalArgumentException.class, () -> new TeleportFloodDetector(5000, 0));
    }
}
