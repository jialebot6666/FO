package com.fo.addon.utils;

import java.util.List;

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

    /**
     * 双号「自动搜索」模式的兜底判区（V6.7p17）。
     *
     * <p>单箱 / 框选模式都有源区坐标，直接走 {@link #nearerZone} 的双锚点取近；
     * 只有自动搜索没有源区锚点（它的语义就是「在重生点脚下搜箱子」），
     * 这时退化成「离存储侧任一锚点不超过半径 → 存储区」。
     *
     * @param nearestStoreDistanceXZ 玩家到存储侧锚点（存储箱 / 珍珠箱 / 活板门）的最近水平距离；
     *                               三个都没配时传 {@link Double#MAX_VALUE}
     * @param radius                 判定半径（格）
     */
    public static Zone storeZoneByDistance(double nearestStoreDistanceXZ, double radius) {
        if (Double.isNaN(nearestStoreDistanceXZ)) return Zone.SOURCE;
        return nearestStoreDistanceXZ <= Math.max(0, radius) ? Zone.STORE : Zone.SOURCE;
    }

    // ==================== V6.7p14：回程票（腔室珍珠）确认 + 自杀门槛 ====================

    /**
     * 回程票是否已确认：连续 {@code needTicks} 个 tick 都在腔室里看到自己的珍珠才算。
     *
     * <p>为什么不是「看到一次就算」：珍珠实体由服务端同步，投出去那一两 tick 客户端还看不到，
     * 单帧判定会把「还没同步」误判成「已经有了」。
     */
    public static boolean pearlConfirmed(int streak, int needTicks) {
        return needTicks > 0 && streak >= needTicks;
    }

    /**
     * 允不允许自杀（双号）。
     *
     * <p>硬约束：{@code confirmedTick <= 0} = 从没确认过腔室里的珍珠 → <b>一律不许自杀</b>，
     * 否则这一趟死完就没有回程票，下一趟永远拉不回来。
     *
     * <p>确认之后按设置里的「自杀延时」等够就行（V6.7p18 起不再夹 600 下限：3C3U 实测
     * 660 仍会把珍珠吞掉，默认值已抬到 1200，范围 0~2000 交给用户自己定）。
     *
     * @param nowTick       当前 tick
     * @param confirmedTick 确认成功的那一 tick（0 = 未确认）
     * @param delay         设置里的「自杀延时」（tick）
     */
    public static boolean canSuicide(long nowTick, long confirmedTick, int delay) {
        if (confirmedTick <= 0) return false;
        return nowTick - confirmedTick >= Math.max(0, delay);
    }

    // ==================== V6.7p21：取货三态 + 自家仓库保护 ====================

    /** 从容器取货的判定结果。 */
    public enum TakeOutcome {
        /** 有符合过滤条件的物品，已取走一组。 */
        TAKEN,
        /** 容器里确实什么都没有。 */
        EMPTY,
        /** 容器里有物品，但全被过滤条件挡掉了（典型：「只拿潜影盒」遇到普通物资箱）。 */
        NO_MATCH
    }

    /**
     * 取货三态判定（V6.7p21 修复）。
     *
     * <p>旧实现把「箱空」和「箱里有东西但都不符合过滤」合并成一个 {@code false}，
     * 调用方一律当成「已搬空」→ 把普通物资箱拉黑；三号还会误报「待搬运箱区已搬空」。
     * 这两个语义必须分开：只有 EMPTY 才该拉黑，NO_MATCH 只跳过。
     *
     * @param anyItemInContainer 容器区是否有任何非空物品
     * @param anyCandidate       是否至少有一个物品通过过滤条件
     */
    public static TakeOutcome takeOutcome(boolean anyItemInContainer, boolean anyCandidate) {
        if (anyCandidate) return TakeOutcome.TAKEN;
        return anyItemInContainer ? TakeOutcome.NO_MATCH : TakeOutcome.EMPTY;
    }

    /**
     * 坐标是否命中「自家仓库」保护名单（存储箱 / 珍珠箱，含双箱搭档另一半）。
     *
     * <p>V6.7p21：三号模式原来只保护双号的两个坐标，框选 / 自动搜索会把本区珍珠箱当物资箱搬空，
     * 之后补货时报「珍珠箱里没有珍珠」。现在双号 + 三号的全部锚点（及其搭档）一起进名单。
     *
     * @param anchors 每项 {x, y, z}
     */
    public static boolean isProtectedChest(int x, int y, int z, List<int[]> anchors) {
        if (anchors == null) return false;
        for (int[] a : anchors) {
            if (a == null || a.length != 3) continue;
            if (a[0] == x && a[1] == y && a[2] == z) return true;
        }
        return false;
    }

    // ==================== V6.7p22：投掷侧的几何判定 ====================

    /** 竖直薄板贴在方块的哪一侧（活板门开启时的门板）。 */
    public enum BoxSide { EAST, WEST, SOUTH, NORTH, UNKNOWN }

    /**
     * 从碰撞箱判断「竖着的薄板贴在哪一侧」（V6.7p22 修复）。
     *
     * <p>活板门打开后是一张 3/16 厚的竖板：**在薄的轴上只占 0.1875，在另一条水平轴上占满 0~1**。
     * 旧实现按 {@code maxX→东 / minX→西 / maxZ→南 / minZ→北} 的顺序判，
     * 门板贴南/北时 {@code maxX} 同样是 1.0（因为它横跨整个 X），于是南北朝向被误判成「东」，
     * 正对面变成垂直的侧面 —— 实机表现就是「往两旁投」。
     *
     * <p>必须先找出**薄的那条轴**，再在该轴上判东/西或南/北；不是「一薄一满」的竖板一律返回
     * {@link BoxSide#UNKNOWN}（宁可停下来报警，也不猜一个侧面）。
     *
     * @param minX 碰撞箱最小 X（0~1）
     * @param minZ 碰撞箱最小 Z（0~1）
     * @param maxX 碰撞箱最大 X（0~1）
     * @param maxZ 碰撞箱最大 Z（0~1）
     */
    public static BoxSide thinPanelSide(double minX, double minZ, double maxX, double maxZ) {
        double sx = maxX - minX;
        double sz = maxZ - minZ;
        boolean thinX = sx <= 0.25 && sz >= 0.99;
        boolean thinZ = sz <= 0.25 && sx >= 0.99;
        if (thinX) {
            if (maxX >= 0.99) return BoxSide.EAST;
            if (minX <= 0.01) return BoxSide.WEST;
            return BoxSide.UNKNOWN;
        }
        if (thinZ) {
            if (maxZ >= 0.99) return BoxSide.SOUTH;
            if (minZ <= 0.01) return BoxSide.NORTH;
            return BoxSide.UNKNOWN;
        }
        return BoxSide.UNKNOWN;
    }

    /**
     * 珍珠是否真的落在 1×1 气泡柱内（V6.7p22 收紧）。
     *
     * <p>旧判定用 ±0.9 格：珍珠落在**旁边的方块**上（离柱心 0.75~1.0）也会被算作「柱内」，
     * 即用户说的「检测向两旁扩展」—— 回程票其实没进柱，模块却以为成功了。
     * 1×1 方块的半宽是 0.5，取 0.55 既有余量又不会跨格。
     */
    public static boolean pearlInColumn(double pearlX, double pearlZ,
                                        int waterX, int waterZ, double tolerance) {
        double dx = Math.abs(pearlX - (waterX + 0.5));
        double dz = Math.abs(pearlZ - (waterZ + 0.5));
        return dx <= tolerance && dz <= tolerance;
    }

    /**
     * 玩家是否还在「门板正对面」那一侧（V6.7p22）。
     *
     * <p>不能用「到正前方格心的距离」判：贴边时人会朝柱心移动，离格心越来越远，
     * 半途就会被误判成「不在正前方」而退回。这里改成沿正前方轴投影：
     * 沿轴分量在 {@code (0.05, 1.2]} 之间（还在门前、没跨过柱心），垂直分量 ≤ {@code maxPerp}。
     *
     * @param dx        玩家 X - 活板门中心 X
     * @param dz        玩家 Z - 活板门中心 Z
     * @param frontDx   正前方的 X 分量（-1/0/1）
     * @param frontDz   正前方的 Z 分量（-1/0/1）
     * @param maxPerp   垂直方向允许的偏移（格）
     */
    public static boolean onFrontSide(double dx, double dz, int frontDx, int frontDz, double maxPerp) {
        double along = dx * frontDx + dz * frontDz;
        double perp = Math.abs(dx * frontDz) + Math.abs(dz * frontDx);
        return along > 0.05 && along <= 1.2 && perp <= maxPerp;
    }
}
