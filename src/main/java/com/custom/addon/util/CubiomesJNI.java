package com.custom.addon.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * CubiomesJNI 原生库桥（黑盒域，包名必须保持 com.custom.addon.util ——
 * DLL 导出的 JNI 符号是 Java_com_custom_addon_util_CubiomesJNI_*，改包名会链接失败）。
 * 从 jar 内 /natives/windows/ 提取 DLL 到临时目录后加载；找不到时回退本地文件/系统库路径。
 */
public class CubiomesJNI {
    private static boolean loaded;
    private static Path tempDir;

    public static boolean isLibraryLoaded() {
        return loaded;
    }

    static {
        loaded = false;
        try {
            loadFromJarOrSystem();
            loaded = true;
            System.out.println("[CubiomesJNI] Library loaded successfully");
        } catch (Exception e) {
            System.err.println("[CubiomesJNI] Failed to load library: " + e.getMessage());
            loaded = false;
        }
    }

    private static void loadFromJarOrSystem() throws IOException {
        String dllName = "CubiomesJNI.dll";

        InputStream jniStream = CubiomesJNI.class.getResourceAsStream("/natives/windows/" + dllName);
        if (jniStream == null) {
            jniStream = CubiomesJNI.class.getResourceAsStream("/" + dllName);
        }
        if (jniStream == null) {
            // jar 内没有：尝试本地文件（与原版一致）与系统库路径
            String[] candidates = { "./CubiomesJNI", "../CubiomesJNI", "../../CubiomesJNI" };
            for (String cand : candidates) {
                try {
                    System.load(cand);
                    System.out.println("[CubiomesJNI] Loaded from: " + cand);
                    return;
                } catch (UnsatisfiedLinkError e) {
                    // continue
                }
            }
            try {
                System.loadLibrary("CubiomesJNI");
                System.out.println("[CubiomesJNI] Loaded from system library path");
                return;
            } catch (UnsatisfiedLinkError e) {
                throw new IOException("Cannot find CubiomesJNI in JAR nor system library path", e);
            }
        }

        tempDir = Files.createTempDirectory("cubiomes");
        tempDir.toFile().deleteOnExit();

        // 依赖库 cubiomes.dll（CubiomesJNI.dll 运行时依赖，必须先加载）
        InputStream depStream = CubiomesJNI.class.getResourceAsStream("/natives/windows/cubiomes.dll");
        if (depStream == null) {
            depStream = CubiomesJNI.class.getResourceAsStream("/cubiomes.dll");
        }
        if (depStream == null) {
            throw new IOException("Cannot find cubiomes.dll in JAR");
        }
        Path depPath = tempDir.resolve("cubiomes.dll");
        Files.copy(depStream, depPath, StandardCopyOption.REPLACE_EXISTING);
        depStream.close();
        System.load(depPath.toString());
        System.out.println("[CubiomesJNI] Loaded dependency: " + depPath);

        Path jniPath = tempDir.resolve(dllName);
        Files.copy(jniStream, jniPath, StandardCopyOption.REPLACE_EXISTING);
        jniStream.close();
        System.load(jniPath.toString());
        System.out.println("[CubiomesJNI] Loaded from JAR: " + jniPath);
    }

    // ---- native 接口（方法名与 DLL 导出一致，不可改名） ----
    public native String getBiomeName(int biomeId);

    public native boolean isViableAncientCity(long generator, int blockX, int blockZ);

    public native boolean isAncientCityAt(long generator, int blockX, int blockZ);

    public native int getBiomeAt(long generator, int blockX, int blockY, int blockZ);

    public native long initGenerator(int versionId, long seed);

    public native void freeGenerator(long generator);

    public native long findAncientCity(long generator, int regionX, int regionZ);

    /** 在给定 region（24×24 区块）内查找古城，返回 int[]{chunkX, chunkZ}；未找到返回 null。 */
    public int[] findCityChunks(long generator, int regionX, int regionZ) {
        long packed = findAncientCity(generator, regionX, regionZ);
        if (packed == 0L) {
            return null;
        }
        int chunkX = (int) (packed >> 32);
        int chunkZ = (int) (packed & 0xFFFFFFFFL);
        return new int[]{chunkX >> 4, chunkZ >> 4};
    }
}
