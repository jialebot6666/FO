package com.fo.addon.utils.threec;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3C 时长缓存的落盘存储：查询结果持久化到 {@code fo/threec-cache.json}，
 * 重启游戏或切服后不必把同一批玩家重新排队查一遍。
 *
 * <p>每条缓存带查询时间戳，由调用方按「缓存有效期」判断是否过期。
 * 过期条目仍照常显示（避免名字标签闪没），只是会被重新入队刷新。
 */
public final class ThreeCCacheStore {
    private static final int FORMAT_VERSION = 1;

    public record Entry(ThreeCPlayerService.PlayerTime data, long queriedAtMillis) {
        public boolean isExpired(long ttlHours, long nowMillis) {
            return ttlHours > 0 && nowMillis - queriedAtMillis >= ttlHours * 3_600_000L;
        }
    }

    private final Path path;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    ThreeCCacheStore(Path path) {
        this.path = path;
    }

    public static ThreeCCacheStore load() {
        Path path = FabricLoader.getInstance().getGameDir().resolve("fo").resolve("threec-cache.json");
        ThreeCCacheStore store = new ThreeCCacheStore(path);
        try {
            if (Files.exists(path)) readInto(Files.readString(path, StandardCharsets.UTF_8), store.entries);
        } catch (IOException | RuntimeException e) {
            // 文件坏掉时当空缓存，别拦着模块启动
        }
        return store;
    }

    public Entry get(String key) {
        return entries.get(key);
    }

    public int size() {
        return entries.size();
    }

    public void put(String key, Entry entry) {
        entries.put(key, entry);
        dirty = true;
    }

    public void clear() {
        entries.clear();
        dirty = true;
    }

    /** 有改动才写盘；失败只吞掉，下次有改动再试。 */
    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson(entries), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            // 落盘失败不影响本次会话的内存缓存
        }
    }

    public static String toJson(Map<String, Entry> source) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonObject items = new JsonObject();
        for (Map.Entry<String, Entry> e : source.entrySet()) {
            Entry entry = e.getValue();
            JsonObject item = new JsonObject();
            item.addProperty("name", entry.data().name());
            item.addProperty("duration", entry.data().totalSeconds());
            Long month = entry.data().monthSeconds();
            if (month == null) {
                item.add("month", JsonNull.INSTANCE);
            } else {
                item.addProperty("month", month);
            }
            item.addProperty("at", entry.queriedAtMillis());
            items.add(e.getKey(), item);
        }
        root.add("entries", items);
        return root.toString();
    }

    public static void readInto(String text, Map<String, Entry> target) {
        if (text == null || text.isBlank()) return;
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        JsonElement entries = root.get("entries");
        if (entries == null || !entries.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : entries.getAsJsonObject().entrySet()) {
            try {
                JsonObject item = e.getValue().getAsJsonObject();
                String name = item.get("name").getAsString();
                long duration = item.get("duration").getAsLong();
                JsonElement monthElement = item.get("month");
                Long month = monthElement == null || monthElement.isJsonNull() ? null : monthElement.getAsLong();
                long at = item.get("at").getAsLong();
                target.put(e.getKey(), new Entry(new ThreeCPlayerService.PlayerTime(name, duration, month), at));
            } catch (RuntimeException ignored) {
                // 单条坏掉只丢这条，其余照读
            }
        }
    }
}
