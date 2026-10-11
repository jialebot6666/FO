package com.fo.addon.ancient.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析（纯逻辑，可单测，零依赖）。
 *
 * <p>只覆盖拼图池 JSON 所需子集：对象、数组、字符串、整数、小数、布尔、null。
 * 返回 {@code Map<String,Object>} / {@code List<Object>} / {@code String} /
 * {@code Long}（无小数点）/ {@code Double} / {@code Boolean} / {@code null}。
 */
public final class AncientJson {

    private final String s;
    private int i;

    private AncientJson(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        AncientJson p = new AncientJson(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        return v;
    }

    private void skipWs() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else break;
        }
    }

    private Object parseValue() {
        skipWs();
        char c = s.charAt(i);
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseString();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return parseNumber();
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++; // {
        skipWs();
        if (s.charAt(i) == '}') { i++; return map; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            i++; // :
            Object val = parseValue();
            map.put(key, val);
            skipWs();
            char c = s.charAt(i++);
            if (c == '}') break;
            // 否则是 ','
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        i++; // [
        skipWs();
        if (s.charAt(i) == ']') { i++; return list; }
        while (true) {
            list.add(parseValue());
            skipWs();
            char c = s.charAt(i++);
            if (c == ']') break;
            // 否则是 ','
        }
        return list;
    }

    private String parseString() {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object parseNumber() {
        int start = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-'
            || s.charAt(i) == '+' || s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
        }
        String num = s.substring(start, i);
        if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
            return Long.parseLong(num);
        }
        return Double.parseDouble(num);
    }
}
