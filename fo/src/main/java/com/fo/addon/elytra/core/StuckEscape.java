package com.fo.addon.elytra.core;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class StuckEscape {

    private StuckEscape() {
    }

    public static final class Tracker {
        private boolean stuck;
        private int hold;
        private int clip;
        private double baseX;
        private double baseZ;
        private double lastSpeed;
        private double lastMove;

        public boolean stuck() {
            return stuck;
        }

        public int holdTicks() {
            return hold;
        }

        public double lastSpeed() {
            return lastSpeed;
        }

        public double lastMove() {
            return lastMove;
        }

        public void reset() {
            stuck = false;
            hold = 0;
            clip = 0;
            lastSpeed = 0.0;
            lastMove = 0.0;
        }

        public boolean sample(MinecraftClient mc, double speedThreshold, double moveThreshold, int holdTicks) {
            if (mc == null || mc.player == null) {
                reset();
                return false;
            }
            if (clip <= 0) {
                baseX = mc.player.getX();
                baseZ = mc.player.getZ();
                clip = 20;
            }
            clip--;
            double vx = mc.player.getVelocity().x;
            double vz = mc.player.getVelocity().z;
            lastSpeed = Math.sqrt(vx * vx + vz * vz);
            lastMove = Math.hypot(mc.player.getX() - baseX, mc.player.getZ() - baseZ);
            if (lastSpeed < speedThreshold && lastMove < moveThreshold) {
                hold++;
            } else {
                hold = 0;
                stuck = false;
            }
            if (hold >= Math.max(1, holdTicks)) stuck = true;
            return stuck;
        }
    }

    public static boolean loaded(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        try {
            return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean blocked(World world, BlockPos pos) {
        if (world == null || pos == null) return true;
        try {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) return false;
            if (!state.getFluidState().isEmpty()) return false;
            return !state.getCollisionShape(world, pos).isEmpty();
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean ceilingBlocked(World world, BlockPos foot, int up) {
        if (world == null || foot == null) return false;
        for (int i = 1; i <= Math.max(1, up); i++) {
            if (blocked(world, foot.up(i))) return true;
        }
        return false;
    }

    public static boolean skyClear(World world, BlockPos from, int up) {
        if (world == null || from == null) return false;
        for (int i = 1; i <= Math.max(1, up); i++) {
            BlockPos p = from.up(i);
            if (!loaded(world, p)) return false;
            if (blocked(world, p)) return false;
        }
        return true;
    }

    private static boolean walkBox(World world, BlockPos foot) {
        if (!blocked(world, foot.down())) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = foot.add(dx, 0, dz);
                if (!loaded(world, p)) return false;
                if (blocked(world, p) || blocked(world, p.up())) return false;
            }
        }
        return true;
    }

    public static BlockPos findOpenSpot(World world, BlockPos origin, int maxRadius, int clearUp, int columnBudget) {
        if (world == null || origin == null) return null;
        int budget = Math.max(8, columnBudget);
        int checked = 0;
        int[] offsets = {0, 2, -2, 4, -4, 6, -6};
        for (int r = 2; r <= Math.max(2, maxRadius); r += 2) {
            int ring = Math.max(8, r);
            for (int i = 0; i < ring; i++) {
                double ang = i * (Math.PI * 2.0) / ring;
                int x = origin.getX() + (int) Math.round(Math.cos(ang) * r);
                int z = origin.getZ() + (int) Math.round(Math.sin(ang) * r);
                for (int dy : offsets) {
                    if (checked++ >= budget) return null;
                    BlockPos c = new BlockPos(x, origin.getY() + dy, z);
                    if (!walkBox(world, c)) continue;
                    if (!skyClear(world, c, clearUp)) continue;
                    return c;
                }
            }
        }
        return null;
    }

    public static int surfaceY(World world, int x, int z) {
        if (world == null) return Integer.MIN_VALUE;
        int top;
        try {
            top = world.getTopYInclusive();
        } catch (Throwable t) {
            top = 319;
        }
        int bottom;
        try {
            bottom = world.getBottomY();
        } catch (Throwable t) {
            bottom = -64;
        }
        for (int y = top; y > bottom; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!loaded(world, p)) return Integer.MIN_VALUE;
            if (blocked(world, p)) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    public static double horizontalDistance(double x, double z, BlockPos pos) {
        if (pos == null) return Double.MAX_VALUE;
        return Math.hypot(x - (pos.getX() + 0.5), z - (pos.getZ() + 0.5));
    }
}
