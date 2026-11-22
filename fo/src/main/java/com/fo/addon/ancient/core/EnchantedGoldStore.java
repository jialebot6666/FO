package com.fo.addon.ancient.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔金采集的城市/箱子黑名单（纯逻辑，可单测）。
 *
 * <p>独立文件 {@code fo-enchanted-gold-auto.json}，按 scope（服务器 + 维度）隔离，
 * 不修改古城搜索的 {@code fo-ancient-chests.json}。
 *
 * <p>城市按「中心 + 半径」匹配而不是精确坐标：聚类中心会随新增/移除箱子轻微漂移，
 * 精确匹配会让同一条黑名单失效，导致反复飞回已经放弃的城。
 */
public final class EnchantedGoldStore {

    public static final String REASON_UNDERWATER_CITY = "水下古城";
    public static final String REASON_SURFACE_WATER = "上方水域";
    public static final String REASON_NO_SAFE_LANDING = "无安全落点";
    public static final String REASON_FAKE_CITY = "假城或标记失真";
    public static final String REASON_UNREACHABLE = "多次无法到达";
    public static final String REASON_MANUAL = "手动拉黑";

    public static final String REASON_POND_ABOVE = "上方水塘";
    public static final String REASON_SHAFT_FLUID = "竖井流体";
    public static final String REASON_TUNNEL_FLUID = "通道流体";
    public static final String REASON_NO_SAFE_MOUTH = "无安全井口";
    public static final String REASON_TARGET_MISSING = "目标缺失";

    /** 城市黑名单匹配半径（格）。 */
    public static final int CITY_MATCH_RADIUS = 128;

    private static final int FORMAT_VERSION = 1;

    /** 一条城市黑名单。 */
    public record CityEntry(int x, int z, String reason, long time) {
    }

    /** 一条箱子黑名单。 */
    public record ChestEntry(int x, int y, int z, String reason, long time) {
    }

    private final Map<String, List<CityEntry>> citiesByScope = new LinkedHashMap<>();
    private final Map<String, List<ChestEntry>> chestsByScope = new LinkedHashMap<>();

    // ==================== 城市 ====================

    public synchronized boolean isCityBlacklisted(String scope, int x, int z) {
        List<CityEntry> list = citiesByScope.get(scope);
        if (list == null) return false;
        long r2 = (long) CITY_MATCH_RADIUS * CITY_MATCH_RADIUS;
        for (CityEntry e : list) {
            long dx = (long) e.x() - x;
            long dz = (long) e.z() - z;
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    public synchronized void blacklistCity(String scope, int x, int z, String reason) {
        List<CityEntry> list = citiesByScope.computeIfAbsent(scope, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        long r2 = 32L * 32L;
        for (int i = 0; i < list.size(); i++) {
            CityEntry old = list.get(i);
            long dx = (long) old.x() - x;
            long dz = (long) old.z() - z;
            if (dx * dx + dz * dz <= r2) {
                // 同一座城重复拉黑：保留首次时间，更新原因/中心
                list.set(i, new CityEntry(x, z, reason, old.time()));
                return;
            }
        }
        list.add(new CityEntry(x, z, reason, now));
    }

    public synchronized List<CityEntry> cities(String scope) {
        return new ArrayList<>(citiesByScope.getOrDefault(scope, List.of()));
    }

    public synchronized int cityCount(String scope) {
        return citiesByScope.getOrDefault(scope, List.of()).size();
    }

    public synchronized void clearCities(String scope) {
        citiesByScope.remove(scope);
    }

    // ==================== 箱子 ====================

    public synchronized boolean isChestBlacklisted(String scope, int x, int y, int z) {
        List<ChestEntry> list = chestsByScope.get(scope);
        if (list == null) return false;
        for (ChestEntry e : list) {
            // 水平坐标唯一确定一个箱子；y 只作记录，允许结构预测与实际落点差几格
            if (e.x() == x && e.z() == z) return true;
        }
        return false;
    }

    public synchronized void blacklistChest(String scope, int x, int y, int z, String reason) {
        List<ChestEntry> list = chestsByScope.computeIfAbsent(scope, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            ChestEntry old = list.get(i);
            if (old.x() == x && old.z() == z) {
                list.set(i, new ChestEntry(x, y, z, reason, old.time()));
                return;
            }
        }
        list.add(new ChestEntry(x, y, z, reason, now));
    }

    public synchronized List<ChestEntry> chests(String scope) {
        return new ArrayList<>(chestsByScope.getOrDefault(scope, List.of()));
    }

    public synchronized int chestCount(String scope) {
        return chestsByScope.getOrDefault(scope, List.of()).size();
    }

    public synchronized void clearChests(String scope) {
        chestsByScope.remove(scope);
    }

    // ==================== 持久化 ====================

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"version\":").append(FORMAT_VERSION).append(",\"scopes\":{");
        Map<String, Boolean> scopes = new LinkedHashMap<>();
        for (String s : citiesByScope.keySet()) scopes.put(s, Boolean.TRUE);
        for (String s : chestsByScope.keySet()) scopes.put(s, Boolean.TRUE);
        boolean firstScope = true;
        for (String scope : scopes.keySet()) {
            List<CityEntry> cities = citiesByScope.getOrDefault(scope, List.of());
            List<ChestEntry> chests = chestsByScope.getOrDefault(scope, List.of());
            if (cities.isEmpty() && chests.isEmpty()) continue;
            if (!firstScope) sb.append(',');
            firstScope = false;
            sb.append('"').append(escape(scope)).append("\":{\"cities\":[");
            for (int i = 0; i < cities.size(); i++) {
                CityEntry e = cities.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"x\":").append(e.x()).append(",\"z\":").append(e.z())
                    .append(",\"reason\":\"").append(escape(e.reason())).append("\"")
                    .append(",\"time\":").append(e.time()).append('}');
            }
            sb.append("],\"chests\":[");
            for (int i = 0; i < chests.size(); i++) {
                ChestEntry e = chests.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"x\":").append(e.x()).append(",\"y\":").append(e.y())
                    .append(",\"z\":").append(e.z())
                    .append(",\"reason\":\"").append(escape(e.reason())).append("\"")
                    .append(",\"time\":").append(e.time()).append('}');
            }
            sb.append("]}");
        }
        sb.append("}}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static EnchantedGoldStore fromJson(String text) {
        EnchantedGoldStore store = new EnchantedGoldStore();
        if (text == null || text.isBlank()) return store;
        Object parsed;
        try {
            parsed = AncientJson.parse(text);
        } catch (RuntimeException e) {
            return store;
        }
        if (!(parsed instanceof Map<?, ?> root)) return store;
        Object scopesObj = ((Map<String, Object>) root).get("scopes");
        if (!(scopesObj instanceof Map<?, ?> scopes)) return store;
        for (Map.Entry<?, ?> e : scopes.entrySet()) {
            String scope = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> rawScope)) continue;
            Map<String, Object> obj = (Map<String, Object>) rawScope;
            if (obj.get("cities") instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    Map<String, Object> city = (Map<String, Object>) m;
                    store.citiesByScope.computeIfAbsent(scope, k -> new ArrayList<>()).add(
                        new CityEntry(intOf(city.get("x")), intOf(city.get("z")),
                            stringOf(city.get("reason"), REASON_MANUAL), longOf(city.get("time"))));
                }
            }
            if (obj.get("chests") instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    Map<String, Object> chest = (Map<String, Object>) m;
                    store.chestsByScope.computeIfAbsent(scope, k -> new ArrayList<>()).add(
                        new ChestEntry(intOf(chest.get("x")), intOf(chest.get("y")), intOf(chest.get("z")),
                            stringOf(chest.get("reason"), REASON_MANUAL), longOf(chest.get("time"))));
                }
            }
        }
        return store;
    }

    public static EnchantedGoldStore load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return new EnchantedGoldStore();
        return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public synchronized void save(Path path) throws IOException {
        if (path == null) return;
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static long longOf(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static String stringOf(Object o, String fallback) {
        return o instanceof String s ? s : fallback;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
