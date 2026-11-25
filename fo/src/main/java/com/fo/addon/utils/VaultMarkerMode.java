package com.fo.addon.utils;

/** 不祥宝库的位置标记方式（V6.9p25）。 */
public enum VaultMarkerMode {
    /** 从 y=256 垂直向下画到宝库（固定高度，不可调）。 */
    TRACER("追踪线"),
    /** 从宝库顶画一根到地表的细柱。 */
    BEAM("光柱"),
    /** 两种都画。 */
    BOTH("两者都");

    private final String label;

    VaultMarkerMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
