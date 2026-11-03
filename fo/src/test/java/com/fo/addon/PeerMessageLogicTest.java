package com.fo.addon;

import com.fo.addon.utils.PeerMessageLogic;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 私信协议解析 / 计数配对（V6.7 自动搬运）。 */
class PeerMessageLogicTest {

    private static final Pattern P = PeerMessageLogic.compileRegex(PeerMessageLogic.DEFAULT_REGEX);

    @Test
    void 默认正则解析3C私信() {
        var parsed = PeerMessageLogic.parsePrivate("📨 张三 ➡ 拉我5", P);
        assertNotNull(parsed);
        assertEquals("张三", parsed.sender());
        assertEquals("拉我5", parsed.content());
    }

    @Test
    void 带时间戳前缀也能解析() {
        var parsed = PeerMessageLogic.parsePrivate("<12:34> 📨 李四 ➡ 收到3", P);
        assertNotNull(parsed);
        assertEquals("李四", parsed.sender());
        assertEquals("收到3", parsed.content());
    }

    @Test
    void 非私信返回null() {
        assertNull(PeerMessageLogic.parsePrivate("张三: 你好", P));
        assertNull(PeerMessageLogic.parsePrivate("", P));
        assertNull(PeerMessageLogic.parsePrivate(null, P));
    }

    @Test
    void 自定义正则生效() {
        var custom = PeerMessageLogic.compileRegex("^(\\w+) whispers: (.+)$");
        var parsed = PeerMessageLogic.parsePrivate("Steve whispers: 拉我7", custom);
        assertNotNull(parsed);
        assertEquals("Steve", parsed.sender());
        assertEquals("拉我7", parsed.content());
    }

    @Test
    void 非法正则会回退默认() {
        var fallback = PeerMessageLogic.compileRegex("[");
        assertNotNull(PeerMessageLogic.parsePrivate("📨 A ➡ 拉我1", fallback));
    }

    @Test
    void 关键字带数字能取出N() {
        var m = PeerMessageLogic.matchKeyword("拉我5", "拉我");
        assertTrue(m.matched());
        assertEquals(5, m.number());
        assertTrue(m.hasNumber());
    }

    @Test
    void 关键字带空格也能取出N() {
        var m = PeerMessageLogic.matchKeyword("拉我 12", "拉我");
        assertTrue(m.matched());
        assertEquals(12, m.number());
    }

    @Test
    void 关键字不带数字() {
        var m = PeerMessageLogic.matchKeyword("拉我", "拉我");
        assertTrue(m.matched());
        assertFalse(m.hasNumber());
        assertEquals(-1, m.number());
    }

    @Test
    void 关键字不匹配() {
        assertFalse(PeerMessageLogic.matchKeyword("你好", "拉我").matched());
        assertFalse(PeerMessageLogic.matchKeyword("", "拉我").matched());
        assertFalse(PeerMessageLogic.matchKeyword("拉我", "").matched());
    }

    @Test
    void 确认按N配对() {
        var ack = PeerMessageLogic.matchKeyword("收到5", "收到");
        assertTrue(PeerMessageLogic.ackAccepts(ack, 5));
        assertFalse(PeerMessageLogic.ackAccepts(ack, 4));
    }

    @Test
    void 确认不带数字也放行_兼容机器人() {
        var ack = PeerMessageLogic.matchKeyword("收到", "收到");
        assertTrue(PeerMessageLogic.ackAccepts(ack, 9));
    }

    @Test
    void 不匹配的确认不接受() {
        var ack = PeerMessageLogic.matchKeyword("正在路上", "收到");
        assertFalse(PeerMessageLogic.ackAccepts(ack, 1));
    }

    @Test
    void 组装请求正文() {
        assertEquals("拉我3", PeerMessageLogic.buildRequest("拉我", 3));
    }
}
