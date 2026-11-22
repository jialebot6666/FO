package com.fo.addon.elytra.pure;

/**
 * 末地噪声与末地城地形判定——cubiomes biomenoise.c 的纯 Java 复刻。
 * 覆盖 1.21（环 bug 仍在）与 26.3（MC-159283 修复）两个分支。
 */
public final class EndNoisePure {

    public static final int MC_1_13 = 16;
    public static final int MC_1_18 = 22;
    public static final int MC_1_21 = 28;
    public static final int MC_1_26_3 = 29;

    private static final int DIM_END = 1;

    // ------------------------------------------------------------------
    // Perlin / Octave 基础结构
    // ------------------------------------------------------------------

    static final class PerlinNoise {
        final int[] d = new int[257];
        int h2;
        double d2;
        double t2;
        double a;
        double b;
        double c;
        double amplitude = 1.0;
        double lacunarity = 1.0;
    }

    static final class OctaveNoise {
        int octcnt;
        PerlinNoise[] octaves;
    }

    static final class SurfaceNoise {
        double xzScale;
        double yScale;
        double xzFactor;
        double yFactor;
        OctaveNoise octmin = new OctaveNoise();
        OctaveNoise octmax = new OctaveNoise();
        OctaveNoise octmain = new OctaveNoise();
        OctaveNoise octsurf = new OctaveNoise();
        OctaveNoise octdepth = new OctaveNoise();
    }

    static final class EndNoise {
        PerlinNoise perlin = new PerlinNoise();
        int mc;
    }

    // ------------------------------------------------------------------
    // 基础工具
    // ------------------------------------------------------------------

    private static double lerp(double part, double from, double to) {
        return from + part * (to - from);
    }

    private static double clampedLerp(double part, double from, double to) {
        if (part <= 0) {
            return from;
        }
        if (part >= 1) {
            return to;
        }
        return lerp(part, from, to);
    }

    private static double lerp2(double dx, double dy,
                                double v00, double v10, double v01, double v11) {
        return lerp(dy, lerp(dx, v00, v10), lerp(dx, v01, v11));
    }

    private static double lerp3(double dx, double dy, double dz,
                                double v000, double v100, double v010, double v110,
                                double v001, double v101, double v011, double v111) {
        double a = lerp2(dx, dy, v000, v100, v010, v110);
        double b = lerp2(dx, dy, v001, v101, v011, v111);
        return lerp(dz, a, b);
    }

    private static double indexedLerp(int idx, double a, double b, double c) {
        switch (idx & 0xf) {
            case 0: return a + b;
            case 1: return -a + b;
            case 2: return a - b;
            case 3: return -a - b;
            case 4: return a + c;
            case 5: return -a + c;
            case 6: return a - c;
            case 7: return -a - c;
            case 8: return b + c;
            case 9: return -b + c;
            case 10: return b - c;
            case 11: return -b - c;
            case 12: return a + b;
            case 13: return -b + c;
            case 14: return -a + b;
            case 15: return -b - c;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------
    // 初始化
    // ------------------------------------------------------------------

    static void perlinInit(PerlinNoise noise, JavaRandom r) {
        noise.a = r.nextDouble() * 256.0;
        noise.b = r.nextDouble() * 256.0;
        noise.c = r.nextDouble() * 256.0;
        noise.amplitude = 1.0;
        noise.lacunarity = 1.0;

        int[] idx = noise.d;
        for (int i = 0; i < 256; i++) {
            idx[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int j = r.nextInt(256 - i) + i;
            int n = idx[i];
            idx[i] = idx[j];
            idx[j] = n;
        }
        idx[256] = idx[0];

        double i2 = Math.floor(noise.b);
        double d2 = noise.b - i2;
        noise.h2 = (int) i2;
        noise.d2 = d2;
        noise.t2 = d2 * d2 * d2 * (d2 * (d2 * 6.0 - 15.0) + 10.0);
    }

    static void octaveInit(OctaveNoise noise, JavaRandom r,
                           PerlinNoise[] octaves, int omin, int len) {
        int end = omin + len - 1;
        double persist = 1.0 / ((1L << len) - 1.0);
        double lacuna = Math.pow(2.0, end);

        int i;
        if (end == 0) {
            perlinInit(octaves[0], r);
            octaves[0].amplitude = persist;
            octaves[0].lacunarity = lacuna;
            persist *= 2.0;
            lacuna *= 0.5;
            i = 1;
        } else {
            r.skip((long) (-end) * 262L);
            i = 0;
        }

        for (; i < len; i++) {
            perlinInit(octaves[i], r);
            octaves[i].amplitude = persist;
            octaves[i].lacunarity = lacuna;
            persist *= 2.0;
            lacuna *= 0.5;
        }

        noise.octcnt = len;
        noise.octaves = octaves;
    }

    public static void setEndSeed(EndNoise en, int mc, long seed) {
        JavaRandom r = new JavaRandom(seed);
        r.skip(17292L);
        perlinInit(en.perlin, r);
        en.mc = mc;
    }

    public static void initSurfaceNoise(SurfaceNoise sn, int dim, long seed) {
        JavaRandom r = new JavaRandom(seed);

        sn.octmin.octaves = new PerlinNoise[16];
        for (int i = 0; i < 16; i++) sn.octmin.octaves[i] = new PerlinNoise();
        sn.octmax.octaves = new PerlinNoise[16];
        for (int i = 0; i < 16; i++) sn.octmax.octaves[i] = new PerlinNoise();
        sn.octmain.octaves = new PerlinNoise[8];
        for (int i = 0; i < 8; i++) sn.octmain.octaves[i] = new PerlinNoise();

        octaveInit(sn.octmin, r, sn.octmin.octaves, -15, 16);
        octaveInit(sn.octmax, r, sn.octmax.octaves, -15, 16);
        octaveInit(sn.octmain, r, sn.octmain.octaves, -7, 8);

        if (dim == DIM_END) {
            sn.xzScale = 2.0;
            sn.yScale = 1.0;
            sn.xzFactor = 80;
            sn.yFactor = 160;
        } else {
            sn.octsurf.octaves = new PerlinNoise[4];
            for (int i = 0; i < 4; i++) sn.octsurf.octaves[i] = new PerlinNoise();
            sn.octdepth.octaves = new PerlinNoise[16];
            for (int i = 0; i < 16; i++) sn.octdepth.octaves[i] = new PerlinNoise();

            octaveInit(sn.octsurf, r, sn.octsurf.octaves, -3, 4);
            r.skip(262L * 10L);
            octaveInit(sn.octdepth, r, sn.octdepth.octaves, -15, 16);
            sn.xzScale = 0.9999999814507745;
            sn.yScale = 0.9999999814507745;
            sn.xzFactor = 80;
            sn.yFactor = 160;
        }
    }

    // ------------------------------------------------------------------
    // Perlin 采样
    // ------------------------------------------------------------------

    static double samplePerlin(PerlinNoise noise, double d1, double d2, double d3,
                               double yamp, double ymin) {
        int h2;
        double t2;

        if (d2 == 0.0) {
            d2 = noise.d2;
            h2 = noise.h2;
            t2 = noise.t2;
        } else {
            d2 += noise.b;
            double i2 = Math.floor(d2);
            d2 -= i2;
            h2 = ((int) i2) & 0xff;
            t2 = d2 * d2 * d2 * (d2 * (d2 * 6.0 - 15.0) + 10.0);
        }

        d1 += noise.a;
        d3 += noise.c;

        double i1f = Math.floor(d1);
        double i3f = Math.floor(d3);
        d1 -= i1f;
        d3 -= i3f;

        int h1 = ((int) i1f) & 0xff;
        int h3 = ((int) i3f) & 0xff;

        double t1 = d1 * d1 * d1 * (d1 * (d1 * 6.0 - 15.0) + 10.0);
        double t3 = d3 * d3 * d3 * (d3 * (d3 * 6.0 - 15.0) + 10.0);

        if (yamp != 0.0) {
            double yclamp = Math.min(ymin, d2);
            d2 -= Math.floor(yclamp / yamp) * yamp;
        }

        int[] idx = noise.d;

        int v1a = idx[h1] + h2;
        int v1b = idx[h1 + 1] + h2;

        int v2a = idx[v1a & 0xff] + h3;
        int v2b = idx[(v1a + 1) & 0xff] + h3;
        int v3a = idx[v1b & 0xff] + h3;
        int v3b = idx[(v1b + 1) & 0xff] + h3;

        int v4a = idx[v2a & 0xff];
        int v4b = idx[(v2a + 1) & 0xff];
        int v5a = idx[v2b & 0xff];
        int v5b = idx[(v2b + 1) & 0xff];
        int v6a = idx[v3a & 0xff];
        int v6b = idx[(v3a + 1) & 0xff];
        int v7a = idx[v3b & 0xff];
        int v7b = idx[(v3b + 1) & 0xff];

        double l1 = indexedLerp(v4a, d1, d2, d3);
        double l5 = indexedLerp(v4b, d1, d2, d3 - 1);
        double l2 = indexedLerp(v6a, d1 - 1, d2, d3);
        double l6 = indexedLerp(v6b, d1 - 1, d2, d3 - 1);
        double l3 = indexedLerp(v5a, d1, d2 - 1, d3);
        double l7 = indexedLerp(v5b, d1, d2 - 1, d3 - 1);
        double l4 = indexedLerp(v7a, d1 - 1, d2 - 1, d3);
        double l8 = indexedLerp(v7b, d1 - 1, d2 - 1, d3 - 1);

        l1 = lerp(t1, l1, l2);
        l3 = lerp(t1, l3, l4);
        l5 = lerp(t1, l5, l6);
        l7 = lerp(t1, l7, l8);

        l1 = lerp(t2, l1, l3);
        l5 = lerp(t2, l5, l7);

        return lerp(t3, l1, l5);
    }

    private static double simplexGrad(int idx, double x, double y, double z, double d) {
        double con = d - x * x - y * y - z * z;
        if (con < 0) {
            return 0;
        }
        con *= con;
        return con * con * indexedLerp(idx, x, y, z);
    }

    static double sampleSimplex2D(PerlinNoise noise, double x, double y) {
        final double SKEW = 0.5 * (Math.sqrt(3.0) - 1.0);
        final double UNSKEW = (3.0 - Math.sqrt(3.0)) / 6.0;

        double hf = (x + y) * SKEW;
        int hx = (int) Math.floor(x + hf);
        int hz = (int) Math.floor(y + hf);
        double mhxz = (hx + hz) * UNSKEW;
        double x0 = x - (hx - mhxz);
        double y0 = y - (hz - mhxz);
        int offx = (x0 > y0) ? 1 : 0;
        int offz = 1 - offx;
        double x1 = x0 - offx + UNSKEW;
        double y1 = y0 - offz + UNSKEW;
        double x2 = x0 - 1.0 + 2.0 * UNSKEW;
        double y2 = y0 - 1.0 + 2.0 * UNSKEW;

        int[] d = noise.d;
        int gi0 = d[0xff & hz];
        int gi1 = d[0xff & (hz + offz)];
        int gi2 = d[0xff & (hz + 1)];
        gi0 = d[0xff & (gi0 + hx)];
        gi1 = d[0xff & (gi1 + hx + offx)];
        gi2 = d[0xff & (gi2 + hx + 1)];

        double t = 0;
        t += simplexGrad(gi0 % 12, x0, y0, 0.0, 0.5);
        t += simplexGrad(gi1 % 12, x1, y1, 0.0, 0.5);
        t += simplexGrad(gi2 % 12, x2, y2, 0.0, 0.5);
        return 70.0 * t;
    }

    // ------------------------------------------------------------------
    // 末地表面噪声
    // ------------------------------------------------------------------

    static double sampleSurfaceNoiseBetween(SurfaceNoise sn, int x, int y, int z,
                                            double noiseMin, double noiseMax) {
        double xzScale = 684.412 * sn.xzScale;
        double yScale = 684.412 * sn.yScale;

        double vmin = 0;
        double vmax = 0;

        double persist = 1.0 / 32768.0;
        double amp = 64.0;

        for (int i = 15; i >= 0; i--) {
            double dx = x * xzScale * persist;
            double dz = z * xzScale * persist;
            double sy = yScale * persist;
            double dy = y * sy;

            vmin += samplePerlin(sn.octmin.octaves[i], dx, dy, dz, sy, dy) * amp;
            vmax += samplePerlin(sn.octmax.octaves[i], dx, dy, dz, sy, dy) * amp;

            if (vmin - amp > noiseMax && vmax - amp > noiseMax) {
                return noiseMax;
            }
            if (vmin + amp < noiseMin && vmax + amp < noiseMin) {
                return noiseMin;
            }

            amp *= 0.5;
            persist *= 2.0;
        }

        double xzStep = xzScale / sn.xzFactor;
        double yStep = yScale / sn.yFactor;
        double vmain = 0.5;

        persist = 1.0 / 128.0;
        amp = 0.05 * 128.0;

        for (int i = 7; i >= 0; i--) {
            double dx = x * xzStep * persist;
            double dz = z * xzStep * persist;
            double sy = yStep * persist;
            double dy = y * sy;

            vmain += samplePerlin(sn.octmain.octaves[i], dx, dy, dz, sy, dy) * amp;

            if (vmain - amp > 1) {
                return vmax;
            }
            if (vmain + amp < 0) {
                return vmin;
            }

            amp *= 0.5;
            persist *= 2.0;
        }

        return clampedLerp(vmain, vmin, vmax);
    }

    private static final double[] UPPER_DROP = {
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 63.0 / 64,
            62.0 / 64, 61.0 / 64, 60.0 / 64, 59.0 / 64, 58.0 / 64, 57.0 / 64, 56.0 / 64, 55.0 / 64,
            54.0 / 64, 53.0 / 64, 52.0 / 64, 51.0 / 64, 50.0 / 64, 49.0 / 64, 48.0 / 64, 47.0 / 64,
            46.0 / 64
    };

    private static final double[] LOWER_DROP = {
            0.0, 0.0, 1.0 / 7, 2.0 / 7, 3.0 / 7, 4.0 / 7, 5.0 / 7, 6.0 / 7,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            1.0
    };

    static void sampleNoiseColumnEnd(double[] column, SurfaceNoise sn, EndNoise en,
                                     int x, int z, int colymin, int colymax) {
        // 26.3 修复 MC-159283：旧版（<=1.21）在外环整数溢出时判虚空，26.3 不再判。
        if (en.mc > MC_1_13 && en.mc <= MC_1_21) {
            long rsq = (long) x * x + (long) z * z;
            if ((int) rsq < 0) {
                for (int y = colymin; y <= colymax; y++) {
                    column[y - colymin] = Double.NaN;
                }
                return;
            }
        }

        double depth = getEndHeightNoise(en, x, z, 0) - 8.0;

        for (int y = colymin; y <= colymax; y++) {
            if (LOWER_DROP[y] == 0.0) {
                column[y - colymin] = -30;
                continue;
            }
            double noise = sampleSurfaceNoiseBetween(sn, x, y, z, -128, 128);
            double clamped = noise + depth;
            clamped = lerp(UPPER_DROP[y], -3000, clamped);
            clamped = lerp(LOWER_DROP[y], -30, clamped);
            column[y - colymin] = clamped;
        }
    }

    static int getSurfaceHeight(double[] n00, double[] n01, double[] n10, double[] n11,
                                int colymin, int colymax, int blockspercell,
                                double dx, double dz) {
        for (int celly = colymax - 1; celly >= colymin; celly--) {
            int idx = celly - colymin;

            double v000 = n00[idx];
            double v001 = n01[idx];
            double v100 = n10[idx];
            double v101 = n11[idx];
            double v010 = n00[idx + 1];
            double v011 = n01[idx + 1];
            double v110 = n10[idx + 1];
            double v111 = n11[idx + 1];

            for (int y = blockspercell - 1; y >= 0; y--) {
                double dy = y / (double) blockspercell;
                double noise = lerp3(dy, dx, dz,
                        v000, v010, v100, v110,
                        v001, v011, v101, v111);
                if (noise > 0) {
                    return celly * blockspercell + y;
                }
            }
        }
        return 0;
    }

    /** 原版 getHeightInGround：返回该列最上面实心方块的 y（末地 0..127）。 */
    static int heightInGround(SurfaceNoise sn, EndNoise en, int blockX, int blockZ) {
        int cellx = blockX >> 3;
        int cellz = blockZ >> 3;
        final int y0 = 0;
        final int y1 = 32;
        final int yn = y1 - y0 + 1;
        double[][] n = new double[4][yn];
        sampleNoiseColumnEnd(n[0], sn, en, cellx, cellz, y0, y1);
        sampleNoiseColumnEnd(n[1], sn, en, cellx, cellz + 1, y0, y1);
        sampleNoiseColumnEnd(n[2], sn, en, cellx + 1, cellz, y0, y1);
        sampleNoiseColumnEnd(n[3], sn, en, cellx + 1, cellz + 1, y0, y1);
        return getSurfaceHeight(n[0], n[1], n[2], n[3], y0, y1, 4,
                (blockX & 7) / 8.0, (blockZ & 7) / 8.0);
    }

    /** 末地高度噪声（8 格一个 cell）。 */
    public static float getEndHeightNoise(EndNoise en, int x, int z, int range) {
        int hx = x / 2;
        int hz = z / 2;
        int oddx = x % 2;
        int oddz = z % 2;

        long h = 64L * ((long) x * x + (long) z * z);
        if (range == 0) {
            range = 12;
        }

        for (int j = -range; j <= range; j++) {
            for (int i = -range; i <= range; i++) {
                long rx = hx + i;
                long rz = hz + j;
                long rsq = rx * rx + rz * rz;

                int v = 0;
                if (rsq > 4096 && sampleSimplex2D(en.perlin, rx, rz) < -0.9f) {
                    float fx = Math.abs((float) rx);
                    float fz = Math.abs((float) rz);
                    float raw = fx * 3439.0f + fz * 147.0f;
                    v = ((int) raw) % 13 + 9;

                    long rx2 = oddx - (long) i * 2;
                    long rz2 = oddz - (long) j * 2;
                    long rsq2 = rx2 * rx2 + rz2 * rz2;
                    h = Math.min(h, rsq2 * (long) v * (long) v);
                }
            }
        }

        float ret = 100.0f - (float) Math.sqrt((float) h);
        if (ret < -100) ret = -100;
        if (ret > 80) ret = 80;
        return ret;
    }

    // ------------------------------------------------------------------
    // 原版 EndCityStructure 的 Y >= 60 判定
    // ------------------------------------------------------------------

    public static boolean isViableEndCityTerrain(long seed, int mc, SurfaceNoise sn, EndNoise en,
                                                 int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        blockX = chunkX * 16 + 7;
        blockZ = chunkZ * 16 + 7;
        int cellx = blockX >> 3;
        int cellz = blockZ >> 3;

        final int y0 = 15;
        final int y1 = 18;
        final int yn = y1 - y0 + 1;

        double[][][] ncol = new double[3][3][yn];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                java.util.Arrays.fill(ncol[i][j], Double.NaN);
            }
        }

        sampleNoiseColumnEnd(ncol[0][0], sn, en, cellx, cellz, y0, y1);
        sampleNoiseColumnEnd(ncol[0][1], sn, en, cellx, cellz + 1, y0, y1);
        sampleNoiseColumnEnd(ncol[1][0], sn, en, cellx + 1, cellz, y0, y1);
        sampleNoiseColumnEnd(ncol[1][1], sn, en, cellx + 1, cellz + 1, y0, y1);

        int h00 = getSurfaceHeight(ncol[0][0], ncol[0][1], ncol[1][0], ncol[1][1],
                y0, y1, 4, (blockX & 7) / 8.0, (blockZ & 7) / 8.0);

        JavaRandom cs = new JavaRandom();
        if (mc <= MC_1_18) {
            cs.setSeed(chunkX + (long) chunkZ * 10387313L);
        } else {
            cs.setState(JavaRandom.chunkGenerateRnd(seed, chunkX, chunkZ));
        }

        int dir = cs.nextInt(4);
        int h01;
        int h10;
        int h11;

        switch (dir) {
            case 0 -> {
                sampleNoiseColumnEnd(ncol[0][2], sn, en, cellx, cellz + 2, y0, y1);
                sampleNoiseColumnEnd(ncol[1][2], sn, en, cellx + 1, cellz + 2, y0, y1);
                sampleNoiseColumnEnd(ncol[2][0], sn, en, cellx + 2, cellz, y0, y1);
                sampleNoiseColumnEnd(ncol[2][1], sn, en, cellx + 2, cellz + 1, y0, y1);
                sampleNoiseColumnEnd(ncol[2][2], sn, en, cellx + 2, cellz + 2, y0, y1);

                h01 = getSurfaceHeight(ncol[0][1], ncol[0][2], ncol[1][1], ncol[1][2],
                        y0, y1, 4, (blockX & 7) / 8.0, ((blockZ + 5) & 7) / 8.0);
                h10 = getSurfaceHeight(ncol[1][0], ncol[1][1], ncol[2][0], ncol[2][1],
                        y0, y1, 4, ((blockX + 5) & 7) / 8.0, (blockZ & 7) / 8.0);
                h11 = getSurfaceHeight(ncol[1][1], ncol[1][2], ncol[2][1], ncol[2][2],
                        y0, y1, 4, ((blockX + 5) & 7) / 8.0, ((blockZ + 5) & 7) / 8.0);
            }
            case 1 -> {
                sampleNoiseColumnEnd(ncol[0][2], sn, en, cellx, cellz + 2, y0, y1);
                sampleNoiseColumnEnd(ncol[1][2], sn, en, cellx + 1, cellz + 2, y0, y1);

                h01 = getSurfaceHeight(ncol[0][1], ncol[0][2], ncol[1][1], ncol[1][2],
                        y0, y1, 4, (blockX & 7) / 8.0, ((blockZ + 5) & 7) / 8.0);
                h10 = getSurfaceHeight(ncol[0][0], ncol[0][1], ncol[1][0], ncol[1][1],
                        y0, y1, 4, ((blockX - 5) & 7) / 8.0, (blockZ & 7) / 8.0);
                h11 = getSurfaceHeight(ncol[0][1], ncol[0][2], ncol[1][1], ncol[1][2],
                        y0, y1, 4, ((blockX - 5) & 7) / 8.0, ((blockZ + 5) & 7) / 8.0);
            }
            case 2 -> {
                h01 = getSurfaceHeight(ncol[0][0], ncol[0][1], ncol[1][0], ncol[1][1],
                        y0, y1, 4, (blockX & 7) / 8.0, ((blockZ - 5) & 7) / 8.0);
                h10 = getSurfaceHeight(ncol[0][0], ncol[0][1], ncol[1][0], ncol[1][1],
                        y0, y1, 4, ((blockX - 5) & 7) / 8.0, (blockZ & 7) / 8.0);
                h11 = getSurfaceHeight(ncol[0][0], ncol[0][1], ncol[1][0], ncol[1][1],
                        y0, y1, 4, ((blockX - 5) & 7) / 8.0, ((blockZ - 5) & 7) / 8.0);
            }
            case 3 -> {
                sampleNoiseColumnEnd(ncol[2][0], sn, en, cellx + 2, cellz, y0, y1);
                sampleNoiseColumnEnd(ncol[2][1], sn, en, cellx + 2, cellz + 1, y0, y1);

                h01 = getSurfaceHeight(ncol[0][0], ncol[0][1], ncol[1][0], ncol[1][1],
                        y0, y1, 4, (blockX & 7) / 8.0, ((blockZ - 5) & 7) / 8.0);
                h10 = getSurfaceHeight(ncol[1][0], ncol[1][1], ncol[2][0], ncol[2][1],
                        y0, y1, 4, ((blockX + 5) & 7) / 8.0, (blockZ & 7) / 8.0);
                h11 = getSurfaceHeight(ncol[1][0], ncol[1][1], ncol[2][0], ncol[2][1],
                        y0, y1, 4, ((blockX + 5) & 7) / 8.0, ((blockZ - 5) & 7) / 8.0);
            }
            default -> {
                return false;
            }
        }

        if (h01 < h00) h00 = h01;
        if (h10 < h00) h00 = h10;
        if (h11 < h00) h00 = h11;

        return h00 >= 60;
    }
}
