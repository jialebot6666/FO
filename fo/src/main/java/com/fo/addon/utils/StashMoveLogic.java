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
     * 卸货过滤（V6.7p1 修复）：哪些物品允许存进存储箱。     *
     * <ul>
     *   <li>末影珍珠<b>永远不入库</b>——三号模式的珍珠是随身携带的“回程票”，
     *       双号模式万一还珍珠失败也不能把它存掉；</li>
     *   <li>开了「只拿潜影盒」时，只存潜影盒（拿什么存什么，非潜影盒留在背包）。</li>
     * </ul>
     */
    public static boolean shouldUnload(String itemId, boolean onlyShulker) {
        if (itemId == null) return false;
        if ("minecraft:ender_pearl".equals(itemId)) return false;
        return !onlyShulker || StashPearlLogic.isShulkerBoxId(itemId);
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

    /**
     * 支持的容器界面类型（V6.7p3）：箱子类（含木桶/漏斗/发射器）与<b>放下的潜影盒</b>。
     *
     * <p>放下的潜影盒界面是 ShulkerBoxScreenHandler，旧实现只认 GenericContainerScreenHandler，
     * 导致「珍珠箱 = 潜影盒」时永远判定“没打开箱子”、补货逻辑不执行。
     */
    public static boolean isSupportedContainer(boolean genericContainer, boolean shulkerBox) {
        return genericContainer || shulkerBox;
    }

    /**
     * 投掷后是否还需要继续后退：离气泡柱中心的水平距离小于安全距离就要继续退。
     */
    public static boolean needMoreBackoff(double distanceToColumn, double safeDistance) {
        return distanceToColumn < safeDistance;
    }

    /**
     * 前进是否已“走不动”（用于潜行走到气泡柱边沿的判定）：
     * 本 tick 水平位移小于 minMove 即算停滞。
     */
    public static boolean isStalled(double prevX, double prevZ, double nowX, double nowZ, double minMove) {
        double dx = nowX - prevX;
        double dz = nowZ - prevZ;
        return dx * dx + dz * dz < minMove * minMove;
    }
}
