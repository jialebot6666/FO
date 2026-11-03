package com.fo.addon.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 私信协议纯逻辑（可单测，不碰 MC 运行时）。
 *
 * <p>3C3U 玩法约定：
 * <ul>
 *   <li>拉珍珠请求：{@code /m 玩家名 拉我N}（N = 第几次拉，防服务器屏蔽重复消息）</li>
 *   <li>对方确认：{@code /m 玩家名 收到N}（N 原样镜像）</li>
 * </ul>
 *
 * <p>收信显示格式默认取 miku 珍珠大管家的 3C 正则
 * {@code ^📨\s+(发送者)\s+➡\s+(内容)$}，可在设置里自定义。
 * 兼容 ZenithProxy 机器人：确认消息允许不带数字（只按关键字判）。
 */
public final class PeerMessageLogic {

    /** 3C3U 私信显示格式（miku 珍珠大管家默认值）。 */
    public static final String DEFAULT_REGEX = "^📨\\s+([^➡]+?)\\s+➡\\s+(.+)$";

    /** 部分服务器在私信前带 <时:分> 时间戳，解析前剥掉。 */
    private static final Pattern TIMESTAMP = Pattern.compile("<\\d{2}:\\d{2}>\\s*");
    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");

    private PeerMessageLogic() {
    }

    /** 一条私信：发送者 + 内容。 */
    public record Parsed(String sender, String content) {
    }

    /** 关键字命中结果：matched=是否命中；number=紧跟其后的数字（无数字为 -1）。 */
    public record Match(boolean matched, int number) {
        public static Match none() {
            return new Match(false, -1);
        }

        public boolean hasNumber() {
            return number >= 0;
        }
    }

    /** 编译自定义正则，非法时回退默认（设置页随便填也不会崩）。 */
    public static Pattern compileRegex(String regex) {
        try {
            if (regex != null && !regex.isBlank()) return Pattern.compile(regex);
        } catch (Exception ignored) {
        }
        return Pattern.compile(DEFAULT_REGEX);
    }

    /**
     * 解析一条聊天消息为私信；不是私信 / 格式不匹配返回 null。
     */
    public static Parsed parsePrivate(String raw, Pattern pattern) {
        if (raw == null || pattern == null) return null;
        String cleaned = TIMESTAMP.matcher(raw).replaceFirst("").trim();
        Matcher m = pattern.matcher(cleaned);
        if (!m.matches() || m.groupCount() < 2) return null;
        String sender = m.group(1) == null ? "" : m.group(1).trim();
        String content = m.group(2) == null ? "" : m.group(2).trim();
        if (sender.isEmpty() || content.isEmpty()) return null;
        return new Parsed(sender, content);
    }

    /**
     * 判断消息内容是否含关键字，并取出紧跟其后的数字。
     * 例：关键字「拉我」+ 内容「拉我5」→ matched=true, number=5；
     * 内容「拉我」→ matched=true, number=-1。
     */
    public static Match matchKeyword(String content, String keyword) {
        if (content == null || keyword == null) return Match.none();
        String k = keyword.trim();
        if (k.isEmpty()) return Match.none();
        int idx = content.indexOf(k);
        if (idx < 0) return Match.none();
        String rest = content.substring(idx + k.length()).trim();
        Matcher m = LEADING_DIGITS.matcher(rest);
        if (m.find()) {
            try {
                return new Match(true, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                return new Match(true, -1);
            }
        }
        return new Match(true, -1);
    }

    /**
     * 确认消息是否接受：
     * 命中关键字即接受；带数字时必须与期望 N 相等（防历史消息串号），
     * 不带数字也接受（兼容 ZenithProxy / miku 风格回复）。
     */
    public static boolean ackAccepts(Match ack, int expectedNumber) {
        if (ack == null || !ack.matched()) return false;
        return !ack.hasNumber() || ack.number() == expectedNumber;
    }

    /** 组装请求正文：关键字 + N。 */
    public static String buildRequest(String keyword, int number) {
        return keyword + number;
    }
}
