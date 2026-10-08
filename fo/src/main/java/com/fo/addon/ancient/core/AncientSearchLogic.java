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
     */
    public static int regionRadius(int blockRadius) {
        return blockRadius / 24 + 1;
    }
}
