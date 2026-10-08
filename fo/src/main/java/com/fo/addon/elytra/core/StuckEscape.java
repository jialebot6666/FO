package com.fo.addon.elytra.core;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class StuckEscape {
    private StuckEscape() {
    }

    public static boolean blocked(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return true;
        }
        try {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                return false;
            }
            if (!state.getFluidState().isEmpty()) {
                return false;
            }
            return !state.getCollisionShape((BlockView)world, pos).isEmpty();
        }
        catch (Throwable t) {
            return true;
        }
    }

    public static boolean ceilingBlocked(World world, BlockPos foot, int up) {
        if (world == null || foot == null) {
            return false;
        }
        for (int i = 1; i <= Math.max(1, up); ++i) {
            if (!StuckEscape.blocked(world, foot.up(i))) continue;
            return true;
        }
        return false;
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
            return this.stuck;
        }

        public int holdTicks() {
            return this.hold;
        }

        public double lastSpeed() {
            return this.lastSpeed;
        }

        public double lastMove() {
            return this.lastMove;
        }

        public void reset() {
            this.stuck = false;
            this.hold = 0;
            this.clip = 0;
            this.lastSpeed = 0.0;
            this.lastMove = 0.0;
        }

        public boolean sample(MinecraftClient mc, double speedThreshold, double moveThreshold, int holdTicks) {
            if (mc == null || mc.player == null) {
                this.reset();
                return false;
            }
            if (this.clip <= 0) {
                this.baseX = mc.player.getX();
                this.baseZ = mc.player.getZ();
                this.clip = 20;
            }
            --this.clip;
            double vx = mc.player.getVelocity().x;
            double vz = mc.player.getVelocity().z;
            this.lastSpeed = Math.sqrt(vx * vx + vz * vz);
            this.lastMove = Math.hypot(mc.player.getX() - this.baseX, mc.player.getZ() - this.baseZ);
            if (this.lastSpeed < speedThreshold && this.lastMove < moveThreshold) {
                ++this.hold;
            } else {
                this.hold = 0;
                this.stuck = false;
            }
            if (this.hold >= Math.max(1, holdTicks)) {
                this.stuck = true;
            }
            return this.stuck;
        }
    }
}

