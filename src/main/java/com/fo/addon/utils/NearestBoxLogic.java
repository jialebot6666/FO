package com.fo.addon.utils;

import java.util.List;
import java.util.Set;

/**
 * 最近存沙盒选择（纯逻辑，可单测）。V4.14 新增。
 *
 * 修复：旧 findOtherShulker 返回扫描顺序第一个盒（扫描从 -半径 开始，
 * 可能让玩家横穿整个沙漠去存沙）；且 INIT_SCAN 跳过（名字未同步）的
 * 盒子会被误当存沙盒，沙可能存进 FO补给盒。
 * 新逻辑：调用方只把"名字已同步且非补给盒"的候选传进来，这里选三维最近的，
 * 并支持排除集合（已满的盒 / 打不开的盒）。
 */
public final class NearestBoxLogic {

    private NearestBoxLogic() {
    }

    /**
     * @param px,py,pz 玩家坐标
     * @param boxes    候选盒坐标列表，每项 {x,y,z}
     * @param excluded 要排除的候选下标集合
     * @return 最近候选的下标；无可用候选返回 -1
     */
    public static int select(int px, int py, int pz, List<int[]> boxes, Set<Integer> excluded) {
        int best = -1;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            if (excluded.contains(i)) continue;
            int[] b = boxes.get(i);
            long dx = b[0] - px;
            long dy = b[1] - py;
            long dz = b[2] - pz;
            long d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
