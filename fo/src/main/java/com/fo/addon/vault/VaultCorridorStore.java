package com.fo.addon.vault;

import com.fo.addon.ancient.core.AncientJson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宝库连廊的进度存档（7.0p1 新增）：记「已连上」「已放弃」的宝库坐标。
 *
 * <p>按「服务器地址 + 维度」分作用域，换服 / 换维度不串；坐标存成 {@code x,y,z} 字符串。
 * 纯逻辑（{@link #toJson()} / {@link #fromJson(String)}）与文件读写分开，前者可单测。
 */
public final class VaultCorridorStore {

    private static final String RELATIVE_PATH = "fo/vault-corridor.json";
    private static final int FORMAT_VERSION = 1;

    private final Map<String, Set<String>> connected = new LinkedHashMap<>();
    private final Map<String, Set<String>> abandoned = new LinkedHashMap<>();
    private boolean dirty;

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public boolean isConnected(String scope, int x, int y, int z) {
        return contains(connected, scope, key(x, y, z));
    }

    public boolean isAbandoned(String scope, int x, int y, int z) {
        return contains(abandoned, scope, key(x, y, z));
    }

    private static boolean contains(Map<String, Set<String>> map, String scope, String key) {
        if (scope == null) return false;
        Set<String> set = map.get(scope);
        return set != null && set.contains(key);
    }

    /** 标记已连上；返回 true 表示新记录。 */
    public boolean markConnected(String scope, int x, int y, int z) {
        return mark(connected, scope, key(x, y, z));
    }

    /** 标记放弃；返回 true 表示新记录。 */
    public boolean markAbandoned(String scope, int x, int y, int z) {
        return mark(abandoned, scope, key(x, y, z));
    }

    private boolean mark(Map<String, Set<String>> map, String scope, String key) {
        if (scope == null) return false;
        boolean added = map.computeIfAbsent(scope, k -> new LinkedHashSet<>()).add(key);
        if (added) dirty = true;
        return added;
    }

    public int connectedCount(String scope) {
        Set<String> set = scope == null ? null : connected.get(scope);
        return set == null ? 0 : set.size();
    }

    public int abandonedCount(String scope) {
        Set<String> set = scope == null ? null : abandoned.get(scope);
        return set == null ? 0 : set.size();
    }

    public boolean isDirty() {
        return dirty;
    }

    // ==================== 序列化 ====================

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":").append(FORMAT_VERSION).append(",\"scopes\":{");
        boolean first = true;
        Set<String> allScopes = new LinkedHashSet<>();
        allScopes.addAll(connected.keySet());
        allScopes.addAll(abandoned.keySet());
        for (String scope : allScopes) {
            Set<String> c = connected.getOrDefault(scope, Set.of());
            Set<String> a = abandoned.getOrDefault(scope, Set.of());
            if (c.isEmpty() && a.isEmpty()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(scope)).append(":{\"connected\":[");
            appendSet(sb, c);
            sb.append("],\"abandoned\":[");
            appendSet(sb, a);
            sb.append("]}");
        }
        sb.append("}}");
        return sb.toString();
    }

    private static void appendSet(StringBuilder sb, Set<String> set) {
        boolean first = true;
        for (String s : set) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(s));
        }
    }

    public static VaultCorridorStore fromJson(String text) {
        VaultCorridorStore store = new VaultCorridorStore();
        if (text == null || text.isBlank()) return store;
        try {
            Object parsed = AncientJson.parse(text);
            if (!(parsed instanceof Map<?, ?> root)) return store;
            if (!(root.get("scopes") instanceof Map<?, ?> scopes)) return store;
            for (Map.Entry<?, ?> entry : scopes.entrySet()) {
                if (!(entry.getKey() instanceof String scope) || !(entry.getValue() instanceof Map<?, ?> body)) continue;
                readInto(store.connected, scope, body.get("connected"));
                readInto(store.abandoned, scope, body.get("abandoned"));
            }
        } catch (RuntimeException e) {
            // 文件坏了当空存档：宁可重连一遍，也别让模块开不起来
        }
        return store;
    }

    private static void readInto(Map<String, Set<String>> target, String scope, Object list) {
        if (!(list instanceof List<?> items)) return;
        Set<String> set = new LinkedHashSet<>();
        for (Object item : items) {
            if (item instanceof String s && !s.isBlank()) set.add(s);
        }
        if (!set.isEmpty()) target.put(scope, set);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ==================== 文件 ====================

    public static Path path() {
        return FabricLoader.getInstance().getGameDir().resolve(RELATIVE_PATH);
    }

    public static VaultCorridorStore load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return new VaultCorridorStore();
            return fromJson(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return new VaultCorridorStore();
        }
    }

    public boolean saveIfDirty() {
        if (!dirty) return true;
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), toJson(), StandardCharsets.UTF_8);
            dirty = false;
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
