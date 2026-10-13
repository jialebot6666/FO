package com.fo.addon.utils;

/**
 * 搜索方向过滤纯逻辑 (从 ElytraFinder ElytraDirectionFilterV2 移植，可单测)。
 * 规则：以玩家位置为基准，|dx| >= |dz| 视为东西向，否则南北向；
 * 对应方向的开关为 false 时丢弃该船。
 */
public final class DirectionFilter {
    private DirectionFilter() {}

    /**
     * @param px 玩家 X
     * @param pz 玩家 Z
     * @param tx 船 X
     * @param tz 船 Z
     * @param north 允许北方 (-Z)
     * @param south 允许南方 (+Z)
     * @param east  允许东方 (+X)
     * @param west  允许西方 (-X)
     * @return true=保留该船
     */
    public static boolean shouldKeep(int px, int pz, int tx, int tz,
                                     boolean north, boolean south, boolean east, boolean west) {
        double dx = tx - px;
        double dz = tz - pz;
        if (Math.abs(dx) >= Math.abs(dz)) {
            // 东西向：dx>=0 在东，dx<0 在西
            return dx >= 0 ? east : west;
        } else {
            // 南北向：dz>=0 在南，dz<0 在北
            return dz >= 0 ? south : north;
        }
    }
}
