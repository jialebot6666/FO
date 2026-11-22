package com.fo.addon.elytra.pure;

import java.util.ArrayList;
import java.util.List;

/**
 * 末地城候选位置与拼装——cubiomes finders.c 的纯 Java 复刻。
 * 位置公式：salt 10387313, region 20, chunkRange 9, 外环 >= 1008。
 */
public final class EndCityPure {

    public static final int SALT = 10387313;
    public static final int REGION_SIZE = 20;
    public static final int CHUNK_RANGE = 9;
    public static final int MIN_RING_DISTANCE = 1008;

    private static final int BASE_FLOOR = 0;
    private static final int BASE_ROOF = 1;
    private static final int BRIDGE_END = 2;
    private static final int BRIDGE_GENTLE_STAIRS = 3;
    private static final int BRIDGE_PIECE = 4;
    private static final int BRIDGE_STEEP_STAIRS = 5;
    private static final int FAT_TOWER_BASE = 6;
    private static final int FAT_TOWER_MIDDLE = 7;
    private static final int FAT_TOWER_TOP = 8;
    private static final int SECOND_FLOOR_1 = 9;
    private static final int SECOND_FLOOR_2 = 10;
    private static final int SECOND_ROOF = 11;
    public static final int END_SHIP = 12;
    private static final int THIRD_FLOOR_1 = 13;
    private static final int THIRD_FLOOR_2 = 14;
    private static final int THIRD_ROOF = 15;
    private static final int TOWER_BASE = 16;
    private static final int TOWER_PIECE = 18;
    private static final int TOWER_TOP = 19;

    /** {sx, sy, sz} */
    private static final int[][] INFO = {
            {9, 3, 9},    // BASE_FLOOR
            {11, 1, 11},  // BASE_ROOF
            {4, 5, 1},    // BRIDGE_END
            {4, 6, 7},    // BRIDGE_GENTLE_STAIRS
            {4, 5, 3},    // BRIDGE_PIECE
            {4, 6, 3},    // BRIDGE_STEEP_STAIRS
            {12, 3, 12},  // FAT_TOWER_BASE
            {12, 7, 12},  // FAT_TOWER_MIDDLE
            {16, 5, 16},  // FAT_TOWER_TOP
            {11, 7, 11},  // SECOND_FLOOR_1
            {11, 7, 11},  // SECOND_FLOOR_2
            {13, 1, 13},  // SECOND_ROOF
            {12, 23, 28}, // END_SHIP
            {13, 7, 13},  // THIRD_FLOOR_1
            {13, 7, 13},  // THIRD_FLOOR_2
            {15, 1, 15},  // THIRD_ROOF
            {6, 6, 6},    // TOWER_BASE
            {6, 3, 6},    // TOWER_FLOOR
            {6, 3, 6},    // TOWER_PIECE
            {8, 4, 8}     // TOWER_TOP
    };

    private EndCityPure() {
    }

    /** 候选城位置（块坐标），不在外环或无效时返回 null。 */
    public static int[] getEndCityAt(long seed, int regX, int regZ) {
        long s = seed
                + (long) regX * 341873128712L
                + (long) regZ * 132897987541L
                + SALT;
        s = (s ^ 0x5deece66dL) & ((1L << 48) - 1L);

        JavaRandom r = new JavaRandom();
        r.setState(s);

        int x = r.next(31) % CHUNK_RANGE;
        x += r.next(31) % CHUNK_RANGE;
        int z = r.next(31) % CHUNK_RANGE;
        z += r.next(31) % CHUNK_RANGE;
        x >>= 1;
        z >>= 1;

        int bx = (regX * REGION_SIZE + x) << 4;
        int bz = (regZ * REGION_SIZE + z) << 4;

        long d2 = (long) bx * bx + (long) bz * bz;
        if (d2 < (long) MIN_RING_DISTANCE * MIN_RING_DISTANCE) {
            return null;
        }
        return new int[]{bx, bz};
    }

    /** 返回船的包围盒 {bb0.x, bb0.y, bb0.z, bb1.x, bb1.y, bb1.z, rot}，没有船返回 null。 */
    public static int[] getEndCityShip(long seed, int chunkX, int chunkZ) {
        List<Piece> pieces = buildPieces(seed, chunkX, chunkZ);
        for (Piece p : pieces) {
            if (p.type == END_SHIP) {
                return new int[]{p.bb0[0], p.bb0[1], p.bb0[2], p.bb1[0], p.bb1[1], p.bb1[2], p.rot};
            }
        }
        return null;
    }

    public static int[][] debugPieces(long seed, int chunkX, int chunkZ) {
        List<Piece> pieces = buildPieces(seed, chunkX, chunkZ);
        int[][] r = new int[pieces.size()][9];
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            r[i] = new int[]{p.type, p.bb0[0], p.bb0[1], p.bb0[2],
                    p.bb1[0], p.bb1[1], p.bb1[2], p.rot, p.depth};
        }
        return r;
    }

    private static List<Piece> buildPieces(long seed, int chunkX, int chunkZ) {
        List<Piece> pieces = new ArrayList<>();
        Env env = new Env(pieces);
        JavaRandom rng = new JavaRandom();
        rng.setState(JavaRandom.chunkGenerateRnd(seed, chunkX, chunkZ));
        env.rng = rng;

        int rot = rng.nextInt(4);
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;

        Piece base = addPiece(env, null, rot, x, 0, z, BASE_FLOOR);
        base = addPiece(env, base, rot, -1, 0, -1, SECOND_FLOOR_1);
        base = addPiece(env, base, rot, -1, 4, -1, THIRD_FLOOR_1);
        base = addPiece(env, base, rot, -1, 8, -1, THIRD_ROOF);

        genRec(env, GEN_TOWER, base, 1);
        return pieces;
    }

    // ------------------------------------------------------------------

    private static final int GEN_TOWER = 0;
    private static final int GEN_BRIDGE = 1;
    private static final int GEN_HOUSE_TOWER = 2;
    private static final int GEN_FAT_TOWER = 3;

    private static final class Piece {
        int type;
        int rot;
        byte depth;
        final int[] pos = new int[3];
        final int[] bb0 = new int[3];
        final int[] bb1 = new int[3];
    }

    private static final class Env {
        final List<Piece> list;
        JavaRandom rng;
        int y;
        final int[] ship;

        Env(List<Piece> list) {
            this.list = list;
            this.ship = new int[]{0};
        }

        Env(Env other) {
            this.list = new ArrayList<>();
            this.rng = other.rng;
            this.y = other.y;
            this.ship = other.ship;
        }
    }

    private static Piece addPiece(Env env, Piece prev, int rot,
                                  int px, int py, int pz, int typ) {
        Piece p = new Piece();
        p.type = typ;
        p.rot = rot;
        p.depth = 0;

        int[] pos;
        if (prev != null) {
            pos = new int[]{prev.pos[0], prev.pos[1], prev.pos[2]};
        } else {
            pos = new int[]{px, py, pz};
        }
        System.arraycopy(pos, 0, p.pos, 0, 3);
        System.arraycopy(pos, 0, p.bb0, 0, 3);
        System.arraycopy(pos, 0, p.bb1, 0, 3);

        int sx = INFO[typ][0];
        int sy = INFO[typ][1];
        int sz = INFO[typ][2];

        p.bb1[1] += sy;
        switch (rot) {
            case 0 -> {
                p.bb1[0] += sx;
                p.bb1[2] += sz;
            }
            case 1 -> {
                p.bb0[0] -= sz;
                p.bb1[2] += sx;
            }
            case 2 -> {
                p.bb0[0] -= sx;
                p.bb0[2] -= sz;
            }
            default -> {
                p.bb1[0] += sz;
                p.bb0[2] -= sx;
            }
        }

        if (prev != null) {
            int dx = 0;
            int dy = py;
            int dz = 0;
            switch (prev.rot) {
                case 0 -> {
                    dx += px;
                    dz += pz;
                }
                case 1 -> {
                    dx -= pz;
                    dz += px;
                }
                case 2 -> {
                    dx -= px;
                    dz -= pz;
                }
                default -> {
                    dx += pz;
                    dz -= px;
                }
            }
            p.pos[0] += dx;
            p.pos[1] += dy;
            p.pos[2] += dz;
            p.bb0[0] += dx;
            p.bb0[1] += dy;
            p.bb0[2] += dz;
            p.bb1[0] += dx;
            p.bb1[1] += dy;
            p.bb1[2] += dz;
        }

        env.list.add(p);
        return p;
    }

    private static boolean genRec(Env env, int gen, Piece current, int depth) {
        if (depth > 8) {
            return false;
        }

        Env local = new Env(env);
        if (!genOne(local, gen, current, depth)) {
            return false;
        }

        int gendepth = env.rng.next(32);

        for (Piece p : local.list) {
            p.depth = (byte) gendepth;
            for (Piece q : env.list) {
                if (q.bb1[0] >= p.bb0[0] && q.bb0[0] <= p.bb1[0]
                        && q.bb1[2] >= p.bb0[2] && q.bb0[2] <= p.bb1[2]
                        && q.bb1[1] >= p.bb0[1] && q.bb0[1] <= p.bb1[1]) {
                    if (current.depth != q.depth) {
                        return false;
                    }
                    break;
                }
            }
        }

        env.list.addAll(local.list);
        env.ship[0] = local.ship[0];
        return true;
    }

    private static boolean genOne(Env env, int gen, Piece current, int depth) {
        return switch (gen) {
            case GEN_TOWER -> genTower(env, current, depth);
            case GEN_BRIDGE -> genBridge(env, current, depth);
            case GEN_HOUSE_TOWER -> genHouseTower(env, current, depth);
            case GEN_FAT_TOWER -> genFatTower(env, current, depth);
            default -> false;
        };
    }

    private static boolean genTower(Env env, Piece current, int depth) {
        int rot = current.rot;
        int x = 3 + env.rng.nextInt(2);
        int z = 3 + env.rng.nextInt(2);

        Piece base = current;
        base = addPiece(env, base, rot, x, -3, z, TOWER_BASE);
        base = addPiece(env, base, rot, 0, 7, 0, TOWER_PIECE);

        Piece floor = env.rng.nextInt(3) == 0 ? base : null;
        int floorcnt = 1 + env.rng.nextInt(3);

        for (int i = 0; i < floorcnt; i++) {
            base = addPiece(env, base, rot, 0, 4, 0, TOWER_PIECE);
            if (i < floorcnt - 1 && env.rng.next(1) != 0) {
                floor = base;
            }
        }

        if (floor != null) {
            int[][] binfo = {
                    {0, 1, -1, 0},
                    {1, 6, -1, 1},
                    {3, 0, -1, 5},
                    {2, 5, -1, 6}
            };
            for (int i = 0; i < 4; i++) {
                if (env.rng.next(1) == 0) {
                    continue;
                }
                int brot = (rot + binfo[i][0]) & 3;
                Piece bridge = addPiece(env, base, brot,
                        binfo[i][1], binfo[i][2], binfo[i][3], BRIDGE_END);
                genRec(env, GEN_BRIDGE, bridge, depth + 1);
            }
        } else if (depth != 7) {
            return genRec(env, GEN_FAT_TOWER, base, depth + 1);
        }

        addPiece(env, base, rot, -1, 4, -1, TOWER_TOP);
        return true;
    }

    private static boolean genBridge(Env env, Piece current, int depth) {
        int rot = current.rot;
        int floorcnt = 1 + env.rng.nextInt(4);

        Piece base = current;
        base = addPiece(env, base, rot, 0, 0, -4, BRIDGE_PIECE);
        base.depth = -1;

        int y = 0;
        for (int i = 0; i < floorcnt; i++) {
            if (env.rng.next(1) != 0) {
                base = addPiece(env, base, rot, 0, y, -4, BRIDGE_PIECE);
                y = 0;
                continue;
            }
            if (env.rng.next(1) != 0) {
                base = addPiece(env, base, rot, 0, y, -4, BRIDGE_STEEP_STAIRS);
            } else {
                base = addPiece(env, base, rot, 0, y, -8, BRIDGE_GENTLE_STAIRS);
            }
            y = 4;
        }

        if (env.ship[0] == 0 && env.rng.nextInt(10 - depth) == 0) {
            int x = -8 + env.rng.nextInt(8);
            int z = -70 + env.rng.nextInt(10);
            addPiece(env, base, rot, x, y, z, END_SHIP);
            env.ship[0] = 1;
        } else {
            env.y = y + 1;
            if (!genRec(env, GEN_HOUSE_TOWER, base, depth + 1)) {
                return false;
            }
        }

        base = addPiece(env, base, (rot + 2) & 3, 4, y, 0, BRIDGE_END);
        base.depth = -1;
        return true;
    }

    private static boolean genHouseTower(Env env, Piece current, int depth) {
        if (depth > 8) {
            return false;
        }
        int rot = current.rot;
        Piece base = current;
        base = addPiece(env, base, rot, -3, env.y, -11, BASE_FLOOR);

        int size = env.rng.nextInt(3);
        if (size == 0) {
            addPiece(env, base, rot, -1, 4, -1, BASE_ROOF);
            return true;
        }

        base = addPiece(env, base, rot, -1, 0, -1, SECOND_FLOOR_2);
        if (size == 1) {
            base = addPiece(env, base, rot, -1, 8, -1, SECOND_ROOF);
        } else {
            base = addPiece(env, base, rot, -1, 4, -1, THIRD_FLOOR_2);
            base = addPiece(env, base, rot, -1, 8, -1, THIRD_ROOF);
        }
        genRec(env, GEN_TOWER, base, depth + 1);
        return true;
    }

    private static boolean genFatTower(Env env, Piece current, int depth) {
        int rot = current.rot;
        Piece base = current;
        base = addPiece(env, base, rot, -3, 4, -3, FAT_TOWER_BASE);
        base = addPiece(env, base, rot, 0, 4, 0, FAT_TOWER_MIDDLE);

        int[][] binfo = {
                {0, 4, -1, 0},
                {1, 12, -1, 4},
                {3, 0, -1, 8},
                {2, 8, -1, 12}
        };

        for (int j = 0; j < 2 && env.rng.nextInt(3) != 0; j++) {
            base = addPiece(env, base, rot, 0, 8, 0, FAT_TOWER_MIDDLE);
            for (int i = 0; i < 4; i++) {
                if (env.rng.next(1) == 0) {
                    continue;
                }
                int brot = (rot + binfo[i][0]) & 3;
                Piece bridge = addPiece(env, base, brot,
                        binfo[i][1], binfo[i][2], binfo[i][3], BRIDGE_END);
                genRec(env, GEN_BRIDGE, bridge, depth + 1);
            }
        }

        addPiece(env, base, rot, -2, 8, -2, FAT_TOWER_TOP);
        return true;
    }

    /**
     * 原版 EndCity.getAverageYPosition：取城中心 4 个角地形高度的最小值。
     * 与 seedfinding 的区别是这里走 26.3 噪声，外环不再判虚空。
     */
    static int getAverageYPosition(EndNoisePure.SurfaceNoise sn, EndNoisePure.EndNoise en,
                                   int chunkX, int chunkZ) {
        JavaRandom rand = new JavaRandom();
        rand.setSeed(chunkX + (long) chunkZ * 10387313L);
        int rot = rand.nextInt(4);
        int dx = 5;
        int dz = 5;
        if (rot == 1) {
            dx = -5;
        } else if (rot == 2) {
            dx = -5;
            dz = -5;
        } else if (rot == 3) {
            dz = -5;
        }
        int x = (chunkX << 4) + 7;
        int z = (chunkZ << 4) + 7;
        int h0 = EndNoisePure.heightInGround(sn, en, x, z);
        int h1 = EndNoisePure.heightInGround(sn, en, x, z + dz);
        int h2 = EndNoisePure.heightInGround(sn, en, x + dx, z);
        int h3 = EndNoisePure.heightInGround(sn, en, x + dx, z + dz);
        return Math.min(Math.min(h0, h1), Math.min(h2, h3));
    }
}
