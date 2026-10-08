package com.fo.addon.ancient.core;

/**
 * CubiomesJNI 薄转发层（FO 命名空间）。
 * native 符号绑定 DLL 的 com.custom.addon.util 包名，不能在本包直接声明 native；
 * 因此所有调用转发到黑盒域的 com.custom.addon.util.CubiomesJNI（由它负责 DLL 加载）。
 */
public class CubiomesJNI {
    private static final com.custom.addon.util.CubiomesJNI impl = new com.custom.addon.util.CubiomesJNI();

    public static boolean isLibraryLoaded() {
        return com.custom.addon.util.CubiomesJNI.isLibraryLoaded();
    }

    public long initGenerator(int versionId, long seed) {
        return impl.initGenerator(versionId, seed);
    }

    public int[] findCityChunks(long generator, int regionX, int regionZ) {
        return impl.findCityChunks(generator, regionX, regionZ);
    }

    public boolean isViableAncientCity(long generator, int blockX, int blockZ) {
        return impl.isViableAncientCity(generator, blockX, blockZ);
    }

    public void freeGenerator(long generator) {
        impl.freeGenerator(generator);
    }
}
