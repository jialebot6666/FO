package com.fo.addon.ancient.core;

/** 古城搜索纯逻辑（可单元测试，不依赖 MC 运行时）。 */
public final class AncientSearchLogic {
    private AncientSearchLogic() {
    }

    /**
     * 解析种子输入：合法长整数直接解析，否则退回字符串 hashCode（与 misaka 原版一致）。
     */
    public static long parseSeedInput(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return input.hashCode();
        }
    }

    /**
     * 搜索半径（区块）→ 区域半径：半径 / 24 + 1（对齐 misaka 真实实现，修正旧源码 ÷25 的错误）。
     * 古城的区域网格是 spacing 24 / separation 8，所以这里除以 24，多出的 1 用于覆盖边界区域。
     */
    public static int regionRadius(int chunkRadius) {
        return chunkRadius / 24 + 1;
    }

    /**
     * 方块坐标 → 区块坐标。用算术右移，负数向负无穷取整，与 Minecraft 一致（-1 >> 4 == -1）。
     */
    public static int blockToChunk(int blockCoord) {
        return blockCoord >> 4;
    }

    /**
     * 两个区块坐标之间的欧氏距离（区块单位）。
     *
     * <p>搜索半径的设置单位是「区块」，所以距离判定必须先统一换算到区块再比：
     * misaka 原实现里 findCityChunks 返回的 city[0]/city[1] 会先 <<4 变成方块坐标供
     * isViableAncientCity 复核，随后**又取回区块坐标**去做距离判定并传给结构重放。
     * 拿方块距离去比区块半径会把有效半径缩小 16 倍。</p>
     */
    public static double chunkDistance(int chunkAX, int chunkAZ, int chunkBX, int chunkBZ) {
        return Math.hypot(chunkAX - chunkBX, chunkAZ - chunkBZ);
    }
}
