package com.fo.addon.utils;

/**
 * 自动搬运的纯逻辑（可单测，不碰 MC 运行时）。
 *
 * <p>包含三块：
 * <ul>
 *   <li>{@link Zone}：三号模式当前所在区（按离两区基准坐标的距离判定）；</li>
 *   <li>{@link #teleportJumped}：判定是否被珍珠拉走（位置突跳）；</li>
 *   <li>{@link #chestOtherHalfOffset}：双箱另一半的偏移（LEFT → 顺时针、RIGHT → 逆时针，
 *       与原版 ChestBlock 字节码一致，古城模块 6.17 同款结论）。</li>
 * </ul>
 */
public final class StashMoveLogic {

    private StashMoveLogic() {
    }

    /** 三号模式的两个区。 */
    public enum Zone {
        SOURCE("待搬运箱区"),
        STORE("存储箱区"),
        UNKNOWN("未判定");

        private final String label;

        Zone(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * 按水平距离判定当前在哪个区：离待搬运箱区基准更近 → SOURCE，否则 STORE。
     * 任一基准缺失（null 用 NaN 表示）时返回 UNKNOWN。
     */
    public static Zone nearerZone(double playerX, double playerZ,
                                  double sourceX, double sourceZ,
                                  double storeX, double storeZ) {
        if (Double.isNaN(sourceX) || Double.isNaN(sourceZ)) {
            return Double.isNaN(storeX) ? Zone.UNKNOWN : Zone.STORE;
        }
        if (Double.isNaN(storeX) || Double.isNaN(storeZ)) return Zone.SOURCE;

        double dxs = playerX - sourceX;
        double dzs = playerZ - sourceZ;
        double dxd = playerX - storeX;
        double dzd = playerZ - storeZ;
        double toSource = dxs * dxs + dzs * dzs;
        double toStore = dxd * dxd + dzd * dzd;
        return toSource <= toStore ? Zone.SOURCE : Zone.STORE;
    }

    /** 位置突跳判定：水平距离超过阈值即认为被珍珠拉走（默认 32 格）。 */
    public static boolean teleportJumped(double lastX, double lastZ, double nowX, double nowZ, double threshold) {
        double dx = nowX - lastX;
        double dz = nowZ - lastZ;
        return dx * dx + dz * dz > threshold * threshold;
    }

    /**
     * 双箱另一半的偏移（dx, dz）。
     *
     * @param left   是否是双箱的左半（ChestType.LEFT）
     * @param facingX 箱子朝向的 X 分量（Direction 的向量）
     * @param facingZ 箱子朝向的 Z 分量
     * @return {dx, dz}
     */
    public static int[] chestOtherHalfOffset(boolean left, int facingX, int facingZ) {
        // 顺时针 (x,z) → (-z, x)；逆时针 → (z, -x)
        if (left) return new int[]{-facingZ, facingX};
        return new int[]{facingZ, -facingX};
    }
}
