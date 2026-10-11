package com.fo.addon.ancient.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 古城拼图几何（纯逻辑，可单测）。
 *
 * <p>复刻 MC：方向/旋转表、StructureTemplate.transform、JigsawBlock.canAttach、
 * Rotation.getShuffled、BoundingBox / AABB / VoxelShape（含 ONLY_FIRST 减法与 ONLY_SECOND 覆盖判定）。
 */
public final class AncientGeometry {

    private AncientGeometry() {
    }

    // ---- 方向 ----
    public static final int ROT_NONE = 0, ROT_CW90 = 1, ROT_CW180 = 2, ROT_CCW90 = 3;

    public static final Map<String, int[]> DIR_VEC = Map.of(
        "north", new int[]{0, 0, -1}, "south", new int[]{0, 0, 1},
        "east", new int[]{1, 0, 0}, "west", new int[]{-1, 0, 0},
        "up", new int[]{0, 1, 0}, "down", new int[]{0, -1, 0});

    public static final Map<String, String> DIR_OPPOSITE = Map.of(
        "north", "south", "south", "north", "east", "west", "west", "east",
        "up", "down", "down", "up");

    private static final Map<String, String[]> DIR_ROTATION = Map.of(
        "north", new String[]{"north", "east", "south", "west"},
        "south", new String[]{"south", "west", "north", "east"},
        "east", new String[]{"east", "south", "west", "north"},
        "west", new String[]{"west", "north", "east", "south"},
        "up", new String[]{"up", "up", "up", "up"},
        "down", new String[]{"down", "down", "down", "down"});

    public static String getFront(String orientation) {
        return orientation.split("_")[0];
    }

    public static String getTop(String orientation) {
        return orientation.split("_")[1];
    }

    public static String rotateOrientation(String orientation, int rotation) {
        String front = DIR_ROTATION.get(getFront(orientation))[rotation];
        String top = DIR_ROTATION.get(getTop(orientation))[rotation];
        return front + "_" + top;
    }

    /** JigsawBlock.canAttach 复刻。 */
    public static boolean canAttach(String sourceOrient, String sourceTarget, String sourceJoint,
                                    String targetOrient, String targetName) {
        String sourceFront = getFront(sourceOrient);
        String targetFront = getFront(targetOrient);
        boolean rollable = "rollable".equals(sourceJoint);
        return sourceFront.equals(DIR_OPPOSITE.get(targetFront))
            && (rollable || getTop(sourceOrient).equals(getTop(targetOrient)))
            && sourceTarget.equals(targetName);
    }

    /** Rotation.getShuffled：Util.shuffledCopy([NONE,CW90,CW180,CCW90], rng)。 */
    public static int[] rotationGetShuffled(AncientRandom rng) {
        int[] rots = {ROT_NONE, ROT_CW90, ROT_CW180, ROT_CCW90};
        return shuffledCopy(rots, rng);
    }

    /** Util.shuffledCopy 复刻（拷贝后 shuffle）。 */
    public static int[] shuffledCopy(int[] arr, AncientRandom rng) {
        int[] out = arr.clone();
        shuffled(out, rng);
        return out;
    }

    /** 泛型 List 版 shuffledCopy（拼图池用）。 */
    public static <T> List<T> shuffledCopy(List<T> list, AncientRandom rng) {
        List<T> out = new ArrayList<>(list);
        for (int i = out.size(); i > 1; i--) {
            int j = rng.nextInt(i);
            T tmp = out.get(i - 1);
            out.set(i - 1, out.get(j));
            out.set(j, tmp);
        }
        return out;
    }

    /** Util.shuffle 复刻（原地）。 */
    public static void shuffled(int[] arr, AncientRandom rng) {
        for (int i = arr.length; i > 1; i--) {
            int j = rng.nextInt(i);
            int tmp = arr[i - 1];
            arr[i - 1] = arr[j];
            arr[j] = tmp;
        }
    }

    /** StructureTemplate.transform 复刻（mirror=NONE，pivot 默认原点）。 */
    public static int[] transform(int[] pos, int rotation) {
        return transform(pos, rotation, new int[]{0, 0, 0});
    }

    public static int[] transform(int[] pos, int rotation, int[] pivot) {
        int x = pos[0], y = pos[1], z = pos[2];
        int px = pivot[0], py = pivot[1], pz = pivot[2];
        switch (rotation) {
            case ROT_CW90:  return new int[]{px + pz - z, y + py, pz - px + x};
            case ROT_CW180: return new int[]{px * 2 - x, y + py, pz * 2 - z};
            case ROT_CCW90: return new int[]{px - pz + z, y + py, px + pz - x};
            default:        return new int[]{x + px, y + py, z + pz};
        }
    }

    // ==================== BoundingBox（含端点整数） ====================
    public static final class BoundingBox {
        public int minX, minY, minZ, maxX, maxY, maxZ;

        public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public static BoundingBox fromCorners(int[] p0, int[] p1) {
            return new BoundingBox(
                Math.min(p0[0], p1[0]), Math.min(p0[1], p1[1]), Math.min(p0[2], p1[2]),
                Math.max(p0[0], p1[0]), Math.max(p0[1], p1[1]), Math.max(p0[2], p1[2]));
        }

        public BoundingBox moved(int dx, int dy, int dz) {
            return new BoundingBox(minX + dx, minY + dy, minZ + dz,
                maxX + dx, maxY + dy, maxZ + dz);
        }

        public boolean isInside(int x, int y, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
        }

        public boolean isInsidePos(int[] pos) {
            return isInside(pos[0], pos[1], pos[2]);
        }

        public int[] toTuple() {
            return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
        }
    }

    // ==================== AABB（半开浮点） ====================
    public static final class Aabb {
        public double minX, minY, minZ, maxX, maxY, maxZ;

        public Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public static Aabb of(BoundingBox bb) {
            return new Aabb(bb.minX, bb.minY, bb.minZ, bb.maxX + 1, bb.maxY + 1, bb.maxZ + 1);
        }

        public Aabb deflate(double amount) {
            return new Aabb(minX + amount, minY + amount, minZ + amount,
                maxX - amount, maxY - amount, maxZ - amount);
        }

        public boolean intersects(Aabb o) {
            return minX < o.maxX && maxX > o.minX
                && minY < o.maxY && maxY > o.minY
                && minZ < o.maxZ && maxZ > o.minZ;
        }

        public boolean containsPoint(double x, double y, double z) {
            return minX <= x && x < maxX && minY <= y && y < maxY && minZ <= z && z < maxZ;
        }
    }

    // ==================== VoxelShape（AABB 列表） ====================
    public static final class VoxelShape {
        public List<Aabb> aabbs;

        public VoxelShape(List<Aabb> aabbs) {
            this.aabbs = aabbs;
        }

        public static VoxelShape create(Aabb aabb) {
            List<Aabb> l = new ArrayList<>();
            l.add(aabb);
            return new VoxelShape(l);
        }

        public static VoxelShape empty() {
            return new VoxelShape(new ArrayList<>());
        }

        public boolean isEmpty() {
            return aabbs.isEmpty();
        }

        /** ONLY_SECOND：other 是否存在未被 self 覆盖的部分（= 碰撞）。 */
        public boolean joinIsNotEmptyOnlySecond(VoxelShape other) {
            for (Aabb o : other.aabbs) {
                if (!fullyCovers(o)) return true;
            }
            return false;
        }

        private boolean fullyCovers(Aabb target) {
            int txMin = (int) Math.floor(target.minX);
            int tyMin = (int) Math.floor(target.minY);
            int tzMin = (int) Math.floor(target.minZ);
            int txMax = (int) Math.ceil(target.maxX) - 1;
            int tyMax = (int) Math.ceil(target.maxY) - 1;
            int tzMax = (int) Math.ceil(target.maxZ) - 1;
            for (int x = txMin; x <= txMax; x++) {
                for (int y = tyMin; y <= tyMax; y++) {
                    for (int z = tzMin; z <= tzMax; z++) {
                        boolean covered = false;
                        for (Aabb s : aabbs) {
                            if (s.minX <= x && x < s.maxX && s.minY <= y && y < s.maxY
                                && s.minZ <= z && z < s.maxZ) {
                                covered = true;
                                break;
                            }
                        }
                        if (!covered) return false;
                    }
                }
            }
            return true;
        }

        /** ONLY_FIRST：self 减 other。 */
        public VoxelShape joinOnlyFirst(VoxelShape other) {
            List<Aabb> result = new ArrayList<>(aabbs);
            for (Aabb o : other.aabbs) {
                List<Aabb> next = new ArrayList<>();
                for (Aabb s : result) {
                    if (!s.intersects(o)) {
                        next.add(s);
                    } else {
                        next.addAll(subtractAabb(s, o));
                    }
                }
                result = next;
            }
            return new VoxelShape(result);
        }
    }

    /** AABB 减法（最多切 6 块）。 */
    public static List<Aabb> subtractAabb(Aabb s, Aabb o) {
        List<Aabb> pieces = new ArrayList<>();
        if (!s.intersects(o)) {
            pieces.add(s);
            return pieces;
        }
        double ixMin = Math.max(s.minX, o.minX);
        double iyMin = Math.max(s.minY, o.minY);
        double izMin = Math.max(s.minZ, o.minZ);
        double ixMax = Math.min(s.maxX, o.maxX);
        double iyMax = Math.min(s.maxY, o.maxY);
        double izMax = Math.min(s.maxZ, o.maxZ);

        if (s.minX < ixMin) pieces.add(new Aabb(s.minX, s.minY, s.minZ, ixMin, s.maxY, s.maxZ));
        if (ixMax < s.maxX) pieces.add(new Aabb(ixMax, s.minY, s.minZ, s.maxX, s.maxY, s.maxZ));
        if (s.minY < iyMin) pieces.add(new Aabb(ixMin, s.minY, s.minZ, ixMax, iyMin, s.maxZ));
        if (iyMax < s.maxY) pieces.add(new Aabb(ixMin, iyMax, s.minZ, ixMax, s.maxY, s.maxZ));
        if (s.minZ < izMin) pieces.add(new Aabb(ixMin, iyMin, s.minZ, ixMax, iyMax, izMin));
        if (izMax < s.maxZ) pieces.add(new Aabb(ixMin, iyMin, izMax, ixMax, iyMax, s.maxZ));
        return pieces;
    }

    /** Shapes.joinIsNotEmpty(shape1, shape2, op)。 */
    public static boolean joinIsNotEmpty(VoxelShape shape1, VoxelShape shape2, boolean onlySecond) {
        if (onlySecond) {
            return shape1.joinIsNotEmptyOnlySecond(shape2);
        }
        return shape2.joinIsNotEmptyOnlySecond(shape1);
    }
}
