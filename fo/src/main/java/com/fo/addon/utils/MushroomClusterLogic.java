package com.fo.addon.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把网格采样点按连通性聚成「一座岛」，每座岛只输出一个代表点。
 *
 * <p>采样点在固定步长的网格上，索引 (ix, iz) 换算成世界坐标是
 * {@code baseX + ix * step}。两个索引八邻接即视为连在一起（蘑菇岛是大块，
 * 八邻接能正确处理斜向延伸，也不会把隔着空格的岛连起来）。
 *
 * <p>代表点取连通块的质心，并吸附到块内离质心最近的采样格，
 * 避免质心落在岛外凹陷处。
 */
public final class MushroomClusterLogic {

    private MushroomClusterLogic() {
    }

    public record Point(int x, int z) {
    }

    public static long key(int ix, int iz) {
        return ((long) ix << 32) ^ (iz & 0xFFFFFFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    /**
     * @param hits  命中的网格索引集合（{@link #key(int, int)} 编码）
     * @param baseX 网格 ix=0 对应的世界 X
     * @param baseZ 网格 iz=0 对应的世界 Z
     * @param step  采样步长（方块）
     */
    public static List<Point> cluster(Set<Long> hits, int baseX, int baseZ, int step) {
        List<Point> out = new ArrayList<>();
        if (hits == null || hits.isEmpty() || step <= 0) {
            return out;
        }

        Set<Long> visited = new HashSet<>();
        for (long start : hits) {
            if (!visited.add(start)) {
                continue;
            }

            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            List<int[]> members = new ArrayList<>();

            while (!queue.isEmpty()) {
                long current = queue.poll();
                int ix = keyX(current);
                int iz = keyZ(current);
                members.add(new int[]{ix, iz});

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        long neighbor = key(ix + dx, iz + dz);
                        if (hits.contains(neighbor) && visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }

            long sumX = 0;
            long sumZ = 0;
            for (int[] member : members) {
                sumX += member[0];
                sumZ += member[1];
            }
            double centerX = (double) sumX / members.size();
            double centerZ = (double) sumZ / members.size();

            int[] nearest = members.get(0);
            double best = Double.MAX_VALUE;
            for (int[] member : members) {
                double dx = member[0] - centerX;
                double dz = member[1] - centerZ;
                double distance = dx * dx + dz * dz;
                if (distance < best) {
                    best = distance;
                    nearest = member;
                }
            }

            out.add(new Point(baseX + nearest[0] * step, baseZ + nearest[1] * step));
        }
        return out;
    }
}
