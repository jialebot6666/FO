package com.fo.addon.vault;

import java.util.Set;

/**
 * 宝库连廊的方块保护判定（纯逻辑，不依赖 Minecraft 运行时，可单测）。7.0p1 新增。
 *
 * <p>设计前提（2026-11-28 用户拍板）：通道末端停在宝库墙外 1 格，保护距离设为 1。
 * 保护距离按<b>切比雪夫距离</b>算（{@code max(|dx|, |dy|, |dz|)}），也就是以宝库方块为中心
 * 的立方体区域，保护 3×3×3 全部 26 个相邻格。用立方体而不是球形，是为了在 1 格这么薄的
 * 缓冲下也不留「斜角擦进房间」的口子。
 *
 * <p>方块级黑名单是第二道闸：宝库本体、试炼刷怪笼、各类容器、基岩/屏障等，
 * 不管距离多远都永不挖。
 */
public final class VaultGuardLogic {

    /** 默认保护距离（格）。用户 2026-11-28 把原来的 4 格改成 1 格。 */
    public static final int DEFAULT_PROTECT_DISTANCE = 1;

    /** 保护距离允许的上下限（设置项范围）。 */
    public static final int MIN_PROTECT_DISTANCE = 1;
    public static final int MAX_PROTECT_DISTANCE = 8;

    /** 永不挖的方块（去掉命名空间后的 id）。 */
    private static final Set<String> FORBIDDEN_NAMES = Set.of(
            "vault",
            "trial_spawner",
            "chest",
            "trapped_chest",
            "barrel",
            "dispenser",
            "dropper",
            "hopper",
            "bedrock",
            "barrier",
            "end_portal_frame",
            "command_block",
            "chain_command_block",
            "repeating_command_block",
            "reinforced_deepslate",
            "shulker_box",
            "white_shulker_box",
            "orange_shulker_box",
            "magenta_shulker_box",
            "light_blue_shulker_box",
            "yellow_shulker_box",
            "lime_shulker_box",
            "pink_shulker_box",
            "gray_shulker_box",
            "light_gray_shulker_box",
            "cyan_shulker_box",
            "purple_shulker_box",
            "blue_shulker_box",
            "brown_shulker_box",
            "green_shulker_box",
            "red_shulker_box",
            "black_shulker_box");

    private VaultGuardLogic() {
    }

    /** 判定结果。 */
    public enum Verdict {
        /** 可以挖。 */
        ALLOW,
        /** 方块本身在黑名单里，永不挖。 */
        BLOCK,
        /** 落在宝库保护区内，不挖。 */
        ZONE
    }

    /**
     * 方块是否属于「永不挖」名单。
     *
     * @param blockId 方块 id，可带 {@code minecraft:} 前缀，也接受纯名字
     */
    public static boolean isForbiddenBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        String name = blockId;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return FORBIDDEN_NAMES.contains(name);
    }

    /**
     * 该格是否落在宝库保护区（切比雪夫距离 ≤ 保护距离）。
     *
     * @param dx,dy,dz         目标格相对宝库方块的偏移
     * @param protectDistance  保护距离，≤0 视为不设保护区
     */
    public static boolean inProtectionZone(int dx, int dy, int dz, int protectDistance) {
        if (protectDistance <= 0) {
            return false;
        }
        int chebyshev = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        return chebyshev <= protectDistance;
    }

    /**
     * 综合判定：先看方块黑名单，再看保护区。黑名单优先级更高，
     * 这样日志里能明确区分「是宝库/容器不让挖」还是「离宝库太近不让挖」。
     */
    public static Verdict judge(String blockId, int dx, int dy, int dz, int protectDistance) {
        if (isForbiddenBlock(blockId)) {
            return Verdict.BLOCK;
        }
        if (inProtectionZone(dx, dy, dz, protectDistance)) {
            return Verdict.ZONE;
        }
        return Verdict.ALLOW;
    }

    /**
     * 是否已抵达本段终点：通道前端格的水平切比雪夫距离正好等于「保护距离 + 1」。
     *
     * <p>保护距离 1 → 末端停在离宝库 2 格处，宝库所在的那面墙（1 格）保持不动，
     * 也就是用户要的「墙外 1 格」。
     */
    public static boolean isArrival(int frontX, int frontZ, int vaultX, int vaultZ, int protectDistance) {
        int dx = Math.abs(frontX - vaultX);
        int dz = Math.abs(frontZ - vaultZ);
        int horizontal = Math.max(dx, dz);
        return horizontal == Math.max(0, protectDistance) + 1;
    }

    /**
     * 沿单轴接近时的终点坐标：宝库坐标 ± (保护距离 + 1)，方向取自出发点。
     *
     * <p>出发点与宝库同坐标时按正方向处理，避免出现 0 长度端点。
     */
    public static int endpointCoord(int vaultCoord, int fromCoord, int protectDistance) {
        int sign = fromCoord >= vaultCoord ? 1 : -1;
        return vaultCoord + sign * (Math.max(0, protectDistance) + 1);
    }
}
