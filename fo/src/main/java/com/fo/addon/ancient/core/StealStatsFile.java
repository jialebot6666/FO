package com.fo.addon.ancient.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code fo-steal-stats.json} 的读写（游戏目录下，与「已开箱子记录」同级）。
 *
 * <p>单独拆出来的原因：累计统计要在**模块启动前**就能被设置页读到（用户可能一进游戏
 * 就打开模块设置看总数，此时模块还没被激活过）。所以由 {@code AddonTemplate.onInitialize()}
 * 在加载期读一次，模块负责在偷取结算后写回。
 *
 * <p>文件坏了/读不到一律当空统计，不抛异常——统计坏掉不该影响模块能用。
 */
public final class StealStatsFile {

    private static final String FILE_NAME = "fo-steal-stats.json";

    private StealStatsFile() {
    }

    public static Path path() {
        return FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
    }

    /** 读取并返回统计；文件不存在或损坏时返回空统计。 */
    public static AncientStealStats load() {
        Path path = path();
        try {
            if (!Files.exists(path)) return new AncientStealStats();
            return AncientStealStats.fromJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return new AncientStealStats();
        }
    }

    /** 写回磁盘；失败只记录不抛（调用方在客户端线程，不能因为 IO 崩）。 */
    public static boolean save(AncientStealStats stats) {
        if (stats == null) return false;
        try {
            Files.writeString(path(), stats.toJson(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
