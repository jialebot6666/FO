package com.fo.addon.ancient.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 附魔金目标筛选与城市归组（纯逻辑，可单测）。
 *
 * <p>{@link AncientChestStore} 只保存箱子坐标，没有城市 ID。这里用水平距离聚类
 * 得到「城市」：默认 128 格内视为同一座城，城市中心只作为飞行锚点，
 * 真正下井仍以具体箱子为准。
 */
public final class EnchantedGoldCityLogic {

    /** 一座城：属于它的目标箱子 + 聚类中心。 */
    public record City(List<AncientChestStore.Mark> marks, int centerX, int centerZ) {
        public int size() {
            return marks.size();
        }

        public long key() {
            return cityKey(centerX, centerZ);
        }
    }

    private EnchantedGoldCityLogic() {
    }

    /** 只有「附魔金」或「两者」才是本模块的目标；「迅捷3」不碰。 */
    public static boolean isGoldMark(AncientChestStore.Mark mark) {
        if (mark == null) return false;
        return AncientChestStore.LABEL_GOLD.equals(mark.label())
            || AncientChestStore.LABEL_BOTH.equals(mark.label());
    }

    /**
     * 过滤出可处理的目标：附魔金/两者 + 外部谓词（已开、箱子黑名单、城市黑名单等）。
     */
    public static List<AncientChestStore.Mark> filterTargets(List<AncientChestStore.Mark> marks,
                                                             Predicate<AncientChestStore.Mark> usable) {
        List<AncientChestStore.Mark> out = new ArrayList<>();
        if (marks == null) return out;
        for (AncientChestStore.Mark m : marks) {
            if (!isGoldMark(m)) continue;
            if (usable != null && !usable.test(m)) continue;
            out.add(m);
        }
        return out;
    }

    /**
     * 按水平距离聚类。
     *
     * <p>用 BFS 连通块而不是「按第一个点归组」：古城箱子可能呈长条分布，
     * 逐个挂到最近簇会产生顺序依赖。这里只要两点距离 ≤ radius 就属于同一簇，
     * 结果与输入顺序无关（先按坐标排序保证稳定）。
     *
     * @param marks  已过滤的目标箱子
     * @param radius 同一座城的最大水平距离（格）
     */
    public static List<City> groupCities(List<AncientChestStore.Mark> marks, int radius) {
        List<City> cities = new ArrayList<>();
        if (marks == null || marks.isEmpty()) return cities;
        int r = Math.max(1, radius);
        long r2 = (long) r * r;

        List<AncientChestStore.Mark> sorted = new ArrayList<>(marks);
        sorted.sort(Comparator.comparingInt(AncientChestStore.Mark::x).thenComparingInt(AncientChestStore.Mark::z));

        boolean[] visited = new boolean[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            if (visited[i]) continue;
            List<AncientChestStore.Mark> cluster = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            visited[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                int idx = queue.poll();
                AncientChestStore.Mark cur = sorted.get(idx);
                cluster.add(cur);
                for (int j = 0; j < sorted.size(); j++) {
                    if (visited[j]) continue;
                    AncientChestStore.Mark other = sorted.get(j);
                    long dx = (long) cur.x() - other.x();
                    long dz = (long) cur.z() - other.z();
                    if (dx * dx + dz * dz <= r2) {
                        visited[j] = true;
                        queue.add(j);
                    }
                }
            }
            long sx = 0;
            long sz = 0;
            for (AncientChestStore.Mark m : cluster) {
                sx += m.x();
                sz += m.z();
            }
            int cx = (int) Math.round(sx / (double) cluster.size());
            int cz = (int) Math.round(sz / (double) cluster.size());
            cities.add(new City(cluster, cx, cz));
        }
        // 大城优先，其次按距离（模块还会再排一次，这里给一个稳定默认顺序）
        cities.sort(Comparator.comparingInt(City::size).reversed()
            .thenComparingInt(City::centerX).thenComparingInt(City::centerZ));
        return cities;
    }

    /** 按离玩家的水平距离排序（近的优先）。 */
    public static List<City> sortedByDistance(List<City> cities, double px, double pz) {
        List<City> out = new ArrayList<>(cities);
        out.sort(Comparator.comparingDouble(c -> distanceSq(c, px, pz)));
        return out;
    }

    public static double distanceSq(City city, double px, double pz) {
        double dx = city.centerX() + 0.5 - px;
        double dz = city.centerZ() + 0.5 - pz;
        return dx * dx + dz * dz;
    }

    /** 城市键（中心坐标打包），用于显示与日志；黑名单匹配用半径，不依赖这个键。 */
    public static long cityKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** 去重（同城可能因双箱记录重复），按 (x,z) 去重。 */
    public static List<AncientChestStore.Mark> distinct(List<AncientChestStore.Mark> marks) {
        List<AncientChestStore.Mark> out = new ArrayList<>();
        if (marks == null) return out;
        Set<Long> seen = new HashSet<>();
        for (AncientChestStore.Mark m : marks) {
            if (m == null) continue;
            long key = cityKey(m.x(), m.z());
            if (seen.add(key)) out.add(m);
        }
        return out;
    }
}
