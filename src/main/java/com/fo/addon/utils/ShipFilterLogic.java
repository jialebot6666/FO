package com.fo.addon.utils;

/**
 * 末地船候选是否纳入搜索的过滤判定。
 * 「忽略已访问记录」开启时，跳过黑名单（已访问船）过滤，重新扫描所有预测船。
 * 纯逻辑，不依赖 MC 运行时，可单元测试。
 */
public final class ShipFilterLogic {
    private ShipFilterLogic() {
    }

    /**
     * 该候选船是否应加入搜索结果。
     *
     * @param ignoreVisited 忽略已访问记录开关（开启 = 不按黑名单过滤）
     * @param blacklisted   该船是否在黑名单（已访问）中
     * @param sessionSkipped 该船是否被本次会话跳过
     */
    public static boolean shouldIncludeShip(boolean ignoreVisited, boolean blacklisted, boolean sessionSkipped) {
        return !sessionSkipped && (ignoreVisited || !blacklisted);
    }
}
