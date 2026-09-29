package com.fo.addon.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 潜影盒交互站立点候选生成（纯逻辑，可单测）。V4.16 新增。
 *
 * 修复：存沙/补给/INIT_SCAN 寻路目标此前用 GoalXZ（ignoreY）只给盒子的 xz，
 * Y 轴被完全忽略——盒子在沙丘顶/坑里时玩家走到盒子正下方/正上方，
 * "3D 距离到达判定"（isWithinDistance ≤ 3）永远不满足、开盒也够不到，
 * 状态机卡死在 GOING_STORE / GOING_SUPPLY。
 * 本类生成"盒子旁边可站立的候选格"（带 Y，脚位置），模块对候选做世界条件
 * 过滤（空气格 + 下方实心）后选三维最近者作为 Baritone 寻路目标
 * （目标格是空气格，GoalGetToBlock 不会像对实体方块那样绕圈），
 * 玩家走到盒子同一层旁边，Y 轴不再被忽略。
 */
public final class StandSpotLogic {

    private StandSpotLogic() {
    }

    /**
     * 生成盒子周围的候选站立点（脚所在格坐标）：水平 ±radius、Y ∈ [boxY-1, boxY+1]。
     * 排除盒子自身所在格（实体方块，不可能可站）。
     *
     * @return 候选坐标列表，每项 {x,y,z}；顺序不定，排序由调用方按与玩家的距离处理
     */
    public static List<int[]> candidates(int boxX, int boxY, int boxZ, int radius) {
        List<int[]> out = new ArrayList<>();
        int r = Math.max(1, radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) continue; // 盒子自身格：实体方块，排除
                for (int dy = -1; dy <= 1; dy++) {
                    out.add(new int[]{boxX + dx, boxY + dy, boxZ + dz});
                }
            }
        }
        return out;
    }

    /**
     * 过滤掉盒子正下方（Y < boxY）的候选（V4.17 新增）。
     * 站到盒子下面开盒会被盒子挡住/服务器按视线回溯拒绝，永远打不开；
     * 站立点只取盒子同一层/上一层的旁边格（"盒子面前"），寻路不会钻到盒子下面。
     *
     * @return 过滤后的候选列表（保留原顺序）
     */
    public static List<int[]> withoutBelow(int boxY, List<int[]> candidates) {
        List<int[]> out = new ArrayList<>();
        for (int[] c : candidates) {
            if (c[1] >= boxY) out.add(c);
        }
        return out;
    }
}
