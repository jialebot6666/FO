package com.fo.addon.elytra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * CubiomesEndJNI — FO 鞘翅采集独立末地城定位原生桥。
 *
 * 与古城黑盒（com.fo.addon.ancient / com.custom.addon.util.CubiomesJNI）零耦合：
 * 独立 DLL（CubiomesEndJNI.dll，自包含 cubiomes 26.3 修复版），独立包、独立加载。
 *
 * 原生导出（不可改名）：
 *   Java_com_fo_addon_elytra_CubiomesEndJNI_initEndLocator(int mc, long seed) -> long
 *   Java_com_fo_addon_elytra_CubiomesEndJNI_freeEndLocator(long)
 *   Java_com_fo_addon_elytra_CubiomesEndJNI_getEndCityAt(long, int regX, int regZ, int[2]) -> int
 *   Java_com_fo_addon_elytra_CubiomesEndJNI_isViableEndCity(long, int bx, int bz) -> boolean
 *   Java_com_fo_addon_elytra_CubiomesEndJNI_getEndCityShip(long, int chunkX, int chunkZ, int[7]) -> int
 */
public final class CubiomesEndJNI {

    /** cubiomes 版本枚举：26.3（末地环修复 MC-159283 后） */
    public static final int MC_1_26_3 = 29;
    /** 1.21 Winter Drop（环 bug 仍在，对照/回退用） */
    public static final int MC_1_21 = 28;

    private static boolean loaded;

    public static boolean isLibraryLoaded() {
        return loaded;
    }

    static {
        loaded = false;
        try {
            loadFromJarOrSystem();
            loaded = true;
            System.out.println("[CubiomesEndJNI] Library loaded successfully");
        } catch (Exception e) {
            System.err.println("[CubiomesEndJNI] Failed to load library: " + e.getMessage());
            loaded = false;
        }
    }

    private static void loadFromJarOrSystem() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String libName = windows ? "CubiomesEndJNI.dll" : "libCubiomesEndJNI.so";

        // 1) 显式指定目录（开发/测试：-Dfo.cubiomesend.dir=...）
        String dir = System.getProperty("fo.cubiomesend.dir");
        if (dir != null) {
            Path p = Path.of(dir, libName);
            if (Files.exists(p)) {
                System.load(p.toAbsolutePath().toString());
                System.out.println("[CubiomesEndJNI] Loaded from: " + p);
                return;
            }
            // 同目录下找其他平台命名（dll/so 互换）
            Path alt = Path.of(dir, windows ? "libCubiomesEndJNI.so" : "CubiomesEndJNI.dll");
            if (Files.exists(alt)) {
                System.load(alt.toAbsolutePath().toString());
                System.out.println("[CubiomesEndJNI] Loaded from: " + alt);
                return;
            }
            throw new IOException("fo.cubiomesend.dir specified but " + libName + " not found in " + dir);
        }

        // 2) 从 jar 内按平台目录提取原生库（natives/windows 或 natives/linux）
        String nativeDir = windows ? "/natives/windows/" : "/natives/linux/";
        InputStream stream = CubiomesEndJNI.class.getResourceAsStream(nativeDir + libName);
        if (stream == null) {
            stream = CubiomesEndJNI.class.getResourceAsStream(nativeDir + (windows ? "CubiomesEndJNI.dll" : "libCubiomesEndJNI.so"));
        }
        if (stream == null) {
            stream = CubiomesEndJNI.class.getResourceAsStream("/" + libName);
        }
        if (stream == null) {
            throw new IOException("Cannot find " + libName + " in JAR nor system property dir");
        }
        Path tempDir = Files.createTempDirectory("cubiomesend");
        tempDir.toFile().deleteOnExit();
        Path libPath = tempDir.resolve(libName);
        Files.copy(stream, libPath, StandardCopyOption.REPLACE_EXISTING);
        stream.close();
        System.load(libPath.toAbsolutePath().toString());
        System.out.println("[CubiomesEndJNI] Loaded from JAR: " + libPath);
    }

    // ---- native 接口（方法名与 DLL 导出一致，不可改名） ----

    /** 初始化末地城定位器，返回句柄；失败返回 0。mc 用 MC_1_26_3。 */
    public static native long initEndLocator(int mc, long seed);

    /** 释放句柄。 */
    public static native void freeEndLocator(long handle);

    /**
     * 结构候选：给定末地城 region（20x20 chunk 区），返回该 region 内的末地城块坐标。
     * out[0]=x, out[1]=z（块坐标）；返回 1 表示存在候选，0 表示无。
     */
    public static native int getEndCityAt(long handle, int regX, int regZ, int[] out);

    /**
     * 地形判定：该块坐标能否生成末地城（isViableEndCityTerrain）。
     * 26.3 环修复后，末地环区域同样判定为可生成。
     */
    public static native boolean isViableEndCity(long handle, int blockX, int blockZ);

    /**
     * 船 piece：给定末地城所在 chunk，遍历 pieces 找 END_SHIP。
     * out = {bb0.x, bb0.y, bb0.z, bb1.x, bb1.y, bb1.z, rot}；返回 1 有船，0 无船。
     */
    public static native int getEndCityShip(long handle, int chunkX, int chunkZ, int[] out);

    private CubiomesEndJNI() {
    }
}
