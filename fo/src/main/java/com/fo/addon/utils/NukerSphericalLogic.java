package com.fo.addon.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SlimefunHelper MineBot SPHERICAL 模式的范围偏移表生成（纯逻辑，可单测）。
 * 返回以 (0,0,0) 为中心的 [-range, range]^3 全部相对坐标，按到中心距离升序。
 * 与 SlimefunHelper InteractExtra.getBlocksAround() 同款（先全量枚举再按距离排序）。
 */
public final class NukerSphericalLogic {

    private NukerSphericalLogic() {
    }

    public static List<int[]> offsets(int range) {
        List<int[]> list = new ArrayList<>();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    list.add(new int[]{x, y, z});
                }
            }
        }
        list.sort(Comparator.comparingDouble(v -> v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
        return list;
    }
}
