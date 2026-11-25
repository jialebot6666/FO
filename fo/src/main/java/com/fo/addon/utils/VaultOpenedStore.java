package com.fo.addon.utils;

import com.fo.addon.ancient.core.AncientJson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「已经右键打开过的不祥宝库」本地记录（V6.9p25 新增）。
 *
 * <p>为什么需要它：不祥宝库开过之后方块还在，而客户端拿到的 {@code vault_state}
 * 不一定及时变成「已开」（有的服务端不重发方块状态），所以 ESP 光看方块状态分不出
 * 「这是新宝库还是已经开过的」。用户口径：**右键点过的不祥宝库就记下来**，以后不再显示。
 *
 * <p>存储：{@code <游戏目录>/fo/vault-opened.json}（FO 自己的文件夹），
 * 按「服务器地址 + 维度」分作用域（单人记为「单人」），避免换服/换维度串记录。
 *
 * <p>纯逻辑（{@link #toJson()} / {@link #fromJson(String)}）与文件读写分开，前者可单测。
 */
public final class VaultOpenedStore {

    /** 相对游戏目录的路径（FO 自己的文件夹）。 */
    private static final String RELATIVE_PATH = "fo/vault-opened.json";
    private static final int FORMAT_VERSION = 1;

    /** 作用域 → 坐标集合（坐标存成 "x,y,z" 字符串，读写都直观）。 */
    private final Map<String, Set<String>> scopes = new LinkedHashMap<>();
    private boolean dirty;

    // ==================== 查询 / 写入 ====================

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** 是否记录过这个宝库。 */
    public boolean isOpened(String scope, int x, int y, int z) {
        if (scope == null) return false;
        Set<String> set = scopes.get(scope);
        return set != null && set.contains(key(x, y, z));
    }

    /** 记录一次右键打开；返回 true 表示这次是新记录（原来没有）。 */
    public boolean mark(String scope, int x, int y, int z) {
        if (scope == null) return false;
        Set<String> set = scopes.computeIfAbsent(scope, k -> new LinkedHashSet<>());
        boolean added = set.add(key(x, y, z));
        if (added) dirty = true;
        return added;
    }

    /** 某个作用域已记录的条数。 */
    public int count(String scope) {
        Set<String> set = scope == null ? null : scopes.get(scope);
        return set == null ? 0 : set.size();
    }

    /** 全部作用域合计条数。 */
    public int totalCount() {
        int n = 0;
        for (Set<String> set : scopes.values()) n += set.size();
        return n;
    }

    /** 清空某个作用域。 */
    public void clearScope(String scope) {
        if (scope == null) return;
        Set<String> removed = scopes.remove(scope);
        if (removed != null && !removed.isEmpty()) dirty = true;
    }

    /** 清空全部作用域的记录（设置页「清理记录」按钮用）。 */
    public void clearAll() {
        if (scopes.isEmpty()) return;
        scopes.clear();
        dirty = true;
    }

    // ==================== 序列化 ====================

    public boolean isDirty() {
        return dirty;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":").append(FORMAT_VERSION).append(",\"scopes\":{");
        boolean firstScope = true;
        for (Map.Entry<String, Set<String>> entry : scopes.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            if (!firstScope) sb.append(',');
            firstScope = false;
            sb.append(quote(entry.getKey())).append(":[");
            boolean firstItem = true;
            for (String pos : entry.getValue()) {
                if (!firstItem) sb.append(',');
                firstItem = false;
                sb.append(quote(pos));
            }
            sb.append(']');
        }
        sb.append("}}");
        return sb.toString();
    }

    public static VaultOpenedStore fromJson(String text) {
        VaultOpenedStore store = new VaultOpenedStore();
        if (text == null || text.isBlank()) return store;
        try {
            Object parsed = AncientJson.parse(text);
            if (!(parsed instanceof Map<?, ?> root)) return store;
            if (!(root.get("scopes") instanceof Map<?, ?> scopeMap)) return store;
            for (Map.Entry<?, ?> entry : scopeMap.entrySet()) {
                if (!(entry.getKey() instanceof String scope) || !(entry.getValue() instanceof List<?> list)) continue;
                Set<String> set = new LinkedHashSet<>();
                for (Object item : list) {
                    if (item instanceof String s) set.add(s);
                }
                if (!set.isEmpty()) store.scopes.put(scope, set);
            }
        } catch (RuntimeException e) {
            // 文件坏掉时当空记录：宁可重复显示已开宝库，也别让模块启动失败
        }
        return store;
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

    /** {@code <游戏目录>/fo/vault-opened.json}。 */
    public static Path path() {
        return FabricLoader.getInstance().getGameDir().resolve(RELATIVE_PATH);
    }

    public static VaultOpenedStore load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return new VaultOpenedStore();
            return fromJson(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return new VaultOpenedStore();
        }
    }

    /** 有改动才写盘；失败只吞掉，下次有改动再试。 */
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