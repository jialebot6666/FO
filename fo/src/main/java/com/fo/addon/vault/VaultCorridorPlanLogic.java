package com.fo.addon.vault;

import java.util.List;
import java.util.Set;

/**
 * 宝库连廊的路线规划（纯逻辑，不依赖 Minecraft 运行时，可单测）。7.0p1 新增。
 *
 * <p>职责只有四件事：挑最近的宝库、算 L 形路线、算停靠点、算台阶怎么爬。
 * 真正挖方块、判定方块、赶路都不在这里，方便先单测锁死行为再上模块。
 *
 * <p>规格（2026-11-28 用户拍板）：通道 2 高 2 宽、只走正 X / 正 Z、L 形（先长边后短边）、
 * 停靠点为「保护距离 + 1」，保护距离默认 1 → 末端离宝库 2 格，墙那一格不动。
 * 高度差 p1 直接支持 ±16 的 45° 台阶，超过 16 格判不支持。
 */
public final class VaultCorridorPlanLogic {

    /** p1 支持的最大高度差（格），超过就记「暂不支持」。 */
    public static final int MAX_LEVEL_DIFF = 16;

    /** 最多记录的目标数量。 */
    public static final int MAX_TARGETS = 512;

    /** 通道截面宽（格）。 */
    public static final int WIDTH = 2;

    /** 通道截面高（格，脚层 + 头层）。 */
    public static final int HEIGHT = 2;

    private VaultCorridorPlanLogic() {
    }

    /** 水平轴。只走正 X / 正 Z，不做对角。 */
    public enum Axis {
        X, Z
    }

    /** 高度处理方式。 */
    public enum VerticalMode {
        /** 同层平走。 */
        FLAT,
        /** 向上台阶。 */
        STAIRS_UP,
        /** 向下台阶。 */
        STAIRS_DOWN,
        /** 超过 ±16，暂不支持。 */
        UNSUPPORTED
    }

    /** 一段 L 形路线的关键点：起点 → 拐角 → 终点。 */
    public record Route(int startX, int startZ, int cornerX, int cornerZ, int endX, int endZ) {

        /** 水平总长（格）。 */
        public int length() {
            return Math.abs(endX - startX) + Math.abs(endZ - startZ);
        }
    }

    /** 高度方案。 */
    public record Vertical(VerticalMode mode, int deltaY, int steps) {

        /** 能不能按这个方案走。 */
        public boolean supported() {
            return mode != VerticalMode.UNSUPPORTED;
        }
    }

    /** 台阶上的一个落脚格（脚层坐标）。 */
    public record Step(int x, int z, int footY) {
    }

    /**
     * 从候选里挑三维最近的宝库。
     *
     * @param vaults   候选坐标，每项 {x,y,z}，null 项跳过
     * @param px,py,pz 参考点（一般是玩家）
     * @param radius   扫描半径，超出直接不考虑；≤0 表示不限
     * @param excluded 已连 / 已放弃的候选下标
     * @return 选中的下标；没有可用候选返回 -1
     */
    public static int selectNearest(List<int[]> vaults, int px, int py, int pz, double radius, Set<Integer> excluded) {
        if (vaults == null || vaults.isEmpty()) {
            return -1;
        }
        double r2 = radius > 0 ? radius * radius : Double.MAX_VALUE;
        int best = -1;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < vaults.size() && i < MAX_TARGETS; i++) {
            if (excluded != null && excluded.contains(i)) {
                continue;
            }
            int[] v = vaults.get(i);
            if (v == null || v.length < 3) {
                continue;
            }
            long dx = (long) v[0] - px;
            long dy = (long) v[1] - py;
            long dz = (long) v[2] - pz;
            double d2 = (double) dx * dx + (double) dy * dy + (double) dz * dz;
            if (d2 > r2) {
                continue;
            }
            long d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * 算 L 形路线：先走长边，再走短边，最后一段正对宝库。
     * 两轴长度相等时先走 X，保证同样的输入永远得到同样的路线。
     */
    public static Route lShape(int startX, int startZ, int endX, int endZ) {
        int dx = endX - startX;
        int dz = endZ - startZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new Route(startX, startZ, endX, startZ, endX, endZ);
        }
        return new Route(startX, startZ, startX, endZ, endX, endZ);
    }

    /**
     * 算停靠点：沿「出发点 → 宝库」的主轴接近，终点在宝库 ± (保护距离 + 1)，
     * 另一轴与宝库对齐，保证最后一段是正对宝库的直线。
     *
     * @return {x, z}
     */
    public static int[] endpoint(int vaultX, int vaultZ, int fromX, int fromZ, int protectDistance) {
        int dx = fromX - vaultX;
        int dz = fromZ - vaultZ;
        int protect = Math.max(0, protectDistance);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[]{VaultGuardLogic.endpointCoord(vaultX, fromX, protect), vaultZ};
        }
        return new int[]{vaultX, VaultGuardLogic.endpointCoord(vaultZ, fromZ, protect)};
    }

    /**
     * 判高度方案：同层平走；±1~16 走 45° 台阶；超过 16 判不支持。
     */
    public static Vertical vertical(int footY, int vaultY) {
        int delta = vaultY - footY;
        if (delta == 0) {
            return new Vertical(VerticalMode.FLAT, 0, 0);
        }
        if (Math.abs(delta) > MAX_LEVEL_DIFF) {
            return new Vertical(VerticalMode.UNSUPPORTED, delta, 0);
        }
        VerticalMode mode = delta > 0 ? VerticalMode.STAIRS_UP : VerticalMode.STAIRS_DOWN;
        return new Vertical(mode, delta, Math.abs(delta));
    }

    /**
     * 把路线摊成逐格的台阶落脚点：每走一格抬 / 降一格（45°），
     * 台阶数少于水平长度时，把抬升均匀摊开，最后一定落在终点高度。
     */
    public static List<Step> stairSteps(Route route, int startFootY, int endFootY) {
        int length = route.length();
        int delta = endFootY - startFootY;
        int steps = Math.abs(delta);
        if (length == 0) {
            return List.of(new Step(route.startX(), route.startZ(), endFootY));
        }
        int sign = Integer.signum(delta);
        List<Step> out = new java.util.ArrayList<>(length + 1);
        int x = route.startX();
        int z = route.startZ();
        for (int i = 0; i <= length; i++) {
            int climbed = (int) ((long) i * steps / length);
            int footY = startFootY + sign * climbed;
            if (i == length) {
                footY = endFootY;
            }
            out.add(new Step(x, z, footY));
            if (i < length) {
                if (x != route.endX()) {
                    x += Integer.signum(route.endX() - x);
                } else {
                    z += Integer.signum(route.endZ() - z);
                }
            }
        }
        return out;
    }

    /** 拐角处要清的体积（2×2×2 = 8 格），保证两段接得上。 */
    public static int cornerVolume() {
        return WIDTH * HEIGHT * 2;
    }

    /** 台阶处每步要多清的头上一层（爬坡时脑袋不会卡进斜上方方块）。 */
    public static int stepHeadroom() {
        return 1;
    }
}
