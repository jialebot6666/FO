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
     * 离「最近的那个区锚点」有多远（水平距离，格）。V6.7p8 新增。
     *
     * <p>用于启动时的距离门槛：超过设定值就报警，让人把角色走到某个区再开模块。
     * 与 {@link #nearerZone} 用同一对锚点（待搬运箱位置 / 存储箱位置），保证
     * 「判定所在区」和「距离检查」不会各说各话。
     *
     * @param hasSource 待搬运箱位置是否已设置
     * @param hasStore  存储箱位置是否已设置
     * @return 最近距离；两处都没设置时返回 {@link Double#MAX_VALUE}
     */
    public static double nearestZoneDistance(double playerX, double playerZ,
                                             double sourceX, double sourceZ, boolean hasSource,
                                             double storeX, double storeZ, boolean hasStore) {
        double best = Double.MAX_VALUE;
        if (hasSource) best = Math.min(best, Math.hypot(playerX - sourceX, playerZ - sourceZ));
        if (hasStore) best = Math.min(best, Math.hypot(playerX - storeX, playerZ - storeZ));
        return best;
    }

    /**
     * 本区该用哪个珍珠箱（V6.7p8）：本区专用优先 → 回退「通用珍珠箱」→ 都没有。
     *
     * <p>做成纯逻辑是为了锁住优先级：只放了一个珍珠箱的老配置必须继续能用
     * （专用空着就自动回退通用），否则升级后补货会静默失效。
     *
     * @return 0 = 用本区专用；1 = 用通用；2 = 两处都没设置
     */
    public static int pickPearlChestSlot(boolean zoneChestSet, boolean commonChestSet) {
        if (zoneChestSet) return PEARL_CHEST_ZONE;
        if (commonChestSet) return PEARL_CHEST_COMMON;
        return PEARL_CHEST_NONE;
    }

    /** {@link #pickPearlChestSlot} 的返回值：用本区专用珍珠箱。 */
    public static final int PEARL_CHEST_ZONE = 0;
    /** {@link #pickPearlChestSlot} 的返回值：回退通用珍珠箱。 */
    public static final int PEARL_CHEST_COMMON = 1;
    /** {@link #pickPearlChestSlot} 的返回值：两处都没设置。 */
    public static final int PEARL_CHEST_NONE = 2;

    /**
     * 「已居中」计数（V6.7p11，照 ZenithProxy PearlDrop 的 APPROACH 相位）。
     *
     * <p>规则：**没移动 且 离柱心 &lt; maxDist** 才算居中，连续累加；任何一条不满足就清零。
     *
     * <p>为什么不能只看「走不动」：被箱子腿、门框、台阶挡住时人也不会动，
     * 但那时离柱子可能还有 3 格，这时候投出去必偏——这是实机上「投掷失败」的常见来源。
     *
     * @param streak   当前连续居中 tick 数
     * @param moved    这一 tick 有没有位移（&gt; 0.02 格）
     * @param hd       到柱心的水平距离（格）
     * @param maxDist  判定居中的最大水平距离（PearlDrop 用 1.2）
     * @return 新的连续居中 tick 数
     */
    public static int nextCenterStreak(int streak, boolean moved, double hd, double maxDist) {
        if (moved) return 0;
        return hd < maxDist ? streak + 1 : 0;
    }

    /** 连续居中够久了没（够久才允许投）。 */
    public static boolean readyToThrow(int streak, int needTicks) {
        return streak >= needTicks;
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

    /** 点击面：东。 */
    public static final int FACE_EAST = 0;
    /** 点击面：西。 */
    public static final int FACE_WEST = 1;
    /** 点击面：南。 */
    public static final int FACE_SOUTH = 2;
    /** 点击面：北。 */
    public static final int FACE_NORTH = 3;
    /** 点击面：上（顶面）。 */
    public static final int FACE_UP = 4;

    /**
     * 选一个“够得着”的点击面（V6.7p5）。
     *
     * <p>规则：优先水平四个侧面；只有在明显从上往下看、且方块顶上没被占用时才选顶面；
     * <b>永远不选底面</b>（眼睛不低于方块中心时绝不选顶面/底面）。
     *
     * <p>为什么需要：旧实现直接按“眼睛 → 方块中心”的向量取最大分量定面，玩家站在箱子
     * 斜上方时会选到顶面；而箱子上接了漏斗（刷物塔常见）时顶面命中点落在漏斗里，
     * 服务端会拒绝交互，表现就是“点了没反应、必须靠近才偶尔成功”。
     *
     * @param dx        眼睛相对方块中心的 X 偏移
     * @param dy        眼睛相对方块中心的 Y 偏移
     * @param dz        眼睛相对方块中心的 Z 偏移
     * @param upBlocked 方块上方是否被其它方块占用
     * @return {@link #FACE_EAST} / {@link #FACE_WEST} / {@link #FACE_SOUTH} / {@link #FACE_NORTH} / {@link #FACE_UP}
     */
    public static int pickClickFace(double dx, double dy, double dz, boolean upBlocked) {
        double horizontal = Math.hypot(dx, dz);
        // dy <= 0：眼睛不高于方块中心 → 只能用侧面；upBlocked：顶面被占 → 也只能用侧面
        boolean useSide = horizontal >= Math.abs(dy) * 0.6 || dy <= 0 || upBlocked;
        if (useSide) {
            if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? FACE_EAST : FACE_WEST;
            return dz >= 0 ? FACE_SOUTH : FACE_NORTH;
        }
        return FACE_UP;
    }
}
