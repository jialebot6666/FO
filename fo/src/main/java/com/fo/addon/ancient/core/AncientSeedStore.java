package com.fo.addon.ancient.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 「已应用的种子」持久化（V6.28，**全局一份**）。
 *
 * <p>要解决的问题：{@code manualSeed}（真正生效的种子）只活在内存里，每次启动游戏都归零，
 * 于是用户每次开模块都得重新点一次「应用种子」。现在改成：**点一次「应用种子」就把种子存下来**，
 * 模块激活时自动带上；想换种子就改输入框再点一次应用（覆盖保存）。
 *
 * <p>为什么单独一个文件、而不是塞进 {@code fo-ancient-chests.json}：
 * 那份记录有「换种子=换世界就整批清空」的生命周期语义（{@code clear(scope)} 会连作用域种子一起删），
 * 「上次应用过的种子」不该被它牵连——这份数据是**输入**，那份是**结果**。
 *
 * <p>刻意不叫"世界种子"：它只代表"用户上次点过应用的那个值"，模块不保证它与当前世界一致
 * （判据仍是本模块原有的记录种子校验）。
 *
 * <p>纯逻辑（{@link #toJson()} / {@link #fromJson(String)}）与文件读写分开，前者可单测。
 */
public final class AncientSeedStore {

    private static final String FILE_NAME = "fo-ancient-seed.json";
    private static final int FORMAT_VERSION = 1;

    /** 已应用的种子；{@code null} = 用户没应用过（或已手动清除）。 */
    private Long seed;

    public Long seed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public boolean hasSeed() {
        return seed != null;
    }

    // ==================== 序列化 ====================

    public String toJson() {
        return "{\"version\":" + FORMAT_VERSION + ",\"seed\":" + (seed == null ? "null" : seed) + "}";
    }

    public static AncientSeedStore fromJson(String text) {
        AncientSeedStore store = new AncientSeedStore();
        if (text == null || text.isBlank()) return store;
        try {
            Object parsed = AncientJson.parse(text);
            if (parsed instanceof Map<?, ?> root && root.get("seed") instanceof Number n) {
                store.seed = n.longValue();
            }
        } catch (RuntimeException e) {
            // 文件坏掉时当「没存过」：宁可让用户再点一次应用，也别报错卡住模块
        }
        return store;
    }

    // ==================== 文件 ====================

    private static Path path() {
        return FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
    }

    public static AncientSeedStore load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return new AncientSeedStore();
            return fromJson(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return new AncientSeedStore();
        }
    }

    /** 写回磁盘；失败返回 false（调用方会提示用户"本次有效但重启后要重设"）。 */
    public boolean save() {
        try {
            Files.writeString(path(), toJson(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
