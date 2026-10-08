package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.FlightPredictor;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.PlayerAction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class LavaPredictor {
    private static final int SCAN_STRIDE = 2;
    private static final int LAVA_SCAN_RADIUS = 2;
    private static final int CEILING_CHECK_BLOCKS = 4;
    private static final float EMERGENCY_PITCH_MIN = -35.0f;
    private static final float EMERGENCY_PITCH_MAX = 10.0f;
    private static final float EMERGENCY_PITCH_BLOCKED = -5.0f;
    private static final int EMERGENCY_MAX_TICKS = 80;
    private static final int DETOUR_MAX_TICKS = 400;
    private static final int WARN_COOLDOWN = 40;
    private final boolean enabled;
    private final int horizonTicks;
    private final int urgentTicks;
    private final int lateralDistance;
    private final int returnDistance;
    private final int cooldownTicks;
    private final boolean warnOnly;
    private final boolean pauseBaritone;
    private final float deflectAngle;
    private Result lastResult = Result.IDLE;
    private Threat threat;
    private int cooldown;
    private boolean avoiding;
    private boolean pausedBaritone;
    private int emergencyTicks;
    private int detourTicks;
    private int detourX;
    private int detourZ;
    private boolean detourIssued;
    private int originalTargetX;
    private int originalTargetZ;
    private boolean originalTargetValid;
    private int detourCount;
    private int emergencyCount;
    private int warnCount;
    private int warnCooldown;

    public LavaPredictor(Options opts) {
        this.enabled = opts.enabled();
        this.horizonTicks = (int)Math.round(Math.max(1.5, Math.min(4.0, opts.horizonSeconds())) * 20.0);
        this.urgentTicks = (int)Math.round(Math.max(0.4, Math.min(2.0, opts.urgentSeconds())) * 20.0);
        this.lateralDistance = Math.max(16, Math.min(96, opts.lateralDistance()));
        this.returnDistance = Math.max(8, Math.min(64, opts.returnDistance()));
        this.cooldownTicks = Math.max(10, Math.min(200, opts.cooldownTicks()));
        this.warnOnly = opts.warnOnly();
        this.pauseBaritone = opts.pauseBaritone();
        this.deflectAngle = (float)Math.max(30.0, Math.min(90.0, opts.deflectAngle()));
    }

    public Threat threat() {
        return this.threat;
    }

    public boolean isAvoiding() {
        return this.avoiding;
    }

    public boolean pausedBaritone() {
        return this.pausedBaritone;
    }

    public Result lastResult() {
        return this.lastResult;
    }

    public int detourCount() {
        return this.detourCount;
    }

    public int emergencyCount() {
        return this.emergencyCount;
    }

    public String statusText() {
        Threat t = this.threat;
        if (t == null) {
            return this.avoiding ? "\u5ca9\u6d46\u9884\u6d4b\uff1a\u89c4\u907f\u4e2d\uff08\u89c6\u91ce\u5185\u5df2\u65e0\u5a01\u80c1\uff09" : "\u5ca9\u6d46\u9884\u6d4b\uff1a\u524d\u65b9\u5e72\u51c0";
        }
        String phase = switch (this.lastResult.ordinal()) {
            case 4 -> "\u7d27\u6025\u89c4\u907f";
            case 3 -> "\u4fa7\u5411\u7ed5\u884c";
            case 2 -> "\u53ea\u9884\u8b66";
            case 1 -> "\u8ddf\u8e2a";
            default -> "\u5f85\u547d";
        };
        return String.format("\u5ca9\u6d46\u9884\u6d4b\uff1a%.1fs \u540e %d %d %d \u6709\u5ca9\u6d46\uff08\u5730\u5e73\u8ddd %.0f \u683c / \u65b9\u4f4d %.0f\u00b0\uff09\u2192 %s", (double)t.etaTicks() / 20.0, t.x(), t.y(), t.z(), t.horizontalDistance(), Float.valueOf(t.bearingYaw()), phase);
    }

    public Result tick(MinecraftClient mc, BlockPos target) {
        boolean urgent;
        Threat found;
        if (!this.enabled) {
            this.reset();
            return Result.IDLE;
        }
        if (mc == null || mc.player == null || mc.world == null) {
            this.reset();
            return Result.IDLE;
        }
        if (this.cooldown > 0) {
            --this.cooldown;
        }
        if (this.warnCooldown > 0) {
            --this.warnCooldown;
        }
        if (mc.player.isInLava() && !this.avoiding) {
            this.releaseAvoidKeys();
            this.threat = null;
            this.lastResult = Result.IDLE;
            return Result.IDLE;
        }
        if (InvHelper.screenOpen()) {
            this.threat = null;
            this.lastResult = Result.IDLE;
            return Result.IDLE;
        }
        this.threat = found = mc.player.isGliding() ? this.predict(mc, target) : null;
        if (this.avoiding) {
            if (this.lastResult == Result.EMERGENCY) {
                return this.tickEmergency(mc, found, target);
            }
            return this.tickDetour(mc, found, target);
        }
        if (found == null) {
            this.lastResult = Result.IDLE;
            return Result.IDLE;
        }
        boolean bl = urgent = found.etaTicks() <= this.urgentTicks;
        if (this.warnOnly) {
            this.lastResult = Result.WARN_ONLY;
            if (this.warnCooldown <= 0) {
                this.warnCooldown = 40;
                FOElytraLog.warn("\u3010\u5b9e\u9a8c\u6027\u00b7\u53ea\u9884\u8b66\u3011\u9884\u6d4b %.1f \u79d2\u540e\u4f1a\u78b0\u5230\u5ca9\u6d46\uff08%s\uff5c\u5730\u5e73\u8ddd %.0f \u683c / \u65b9\u4f4d %.0f\u00b0\uff09\u2192 \u6309\u8bbe\u7f6e\u4e0d\u63a5\u7ba1\uff08\u6253\u5f00\u300c\u53ea\u9884\u8b66\u4e0d\u63a5\u7ba1\u300d\u7684\u5f00\u5173\u624d\u80fd\u8ba9\u6211\u7ed5\u5f00\uff09", (double)found.etaTicks() / 20.0, found.x() + " " + found.y() + " " + found.z(), found.horizontalDistance(), Float.valueOf(found.bearingYaw()));
                FOElytraLog.detail("\u5ca9\u6d46\u9884\u6d4b\u660e\u7ec6\uff1aETA %d tick\uff5c\u91c7\u6837 %d \u683c\u5ca9\u6d46\uff5c\u4e0a\u65b9\u901a\u4e0d\u8fc7 %s\uff5c\u7d27\u6025\u9608\u503c %d tick\uff5c\u9884\u6d4b\u65f6\u957f %d tick", found.etaTicks(), found.lavaSamples(), found.blockedAbove() ? "\u662f" : "\u5426", this.urgentTicks, this.horizonTicks);
            }
            return this.lastResult;
        }
        if (this.cooldown > 0) {
            this.lastResult = Result.WATCHING;
            return this.lastResult;
        }
        if (urgent) {
            return this.beginEmergency(mc, found, target);
        }
        return this.beginDetour(mc, found, target);
    }

    private Threat predict(MinecraftClient mc, BlockPos target) {
        Vec3d targetDir;
        Vec3d look;
        ClientWorld world = mc.world;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        Vec3d vel = mc.player.getVelocity();
        Vec3d blended = look = mc.player.getRotationVec(1.0f);
        double velH = vel.horizontalLength();
        if (velH > 0.05) {
            blended = blended.add(new Vec3d(vel.x / velH * 0.5, 0.0, vel.z / velH * 0.5));
        }
        if ((targetDir = LavaPredictor.horizontalDir(px, pz, target)) != null) {
            blended = blended.add(targetDir.multiply(0.5));
        }
        if (blended.lengthSquared() < 1.0E-6) {
            return null;
        }
        look = blended.normalize();
        List<Vec3d> path = FlightPredictor.predictPath(this.horizonTicks, new Vec3d(px, py, pz), vel, look);
        for (int i = 1; i < path.size(); i += 2) {
            int lava;
            Vec3d p = path.get(i);
            int bx = (int)Math.floor(p.x);
            int by = (int)Math.floor(p.y);
            int bz = (int)Math.floor(p.z);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4) || (lava = LavaPredictor.lavaNear((World)world, bx, by, bz)) <= 0) continue;
            double dx = p.x - px;
            double dz = p.z - pz;
            double dh = Math.hypot(dx, dz);
            float bearing = (float)((Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0);
            float escape = (float)(((double)bearing + 180.0) % 360.0);
            boolean blocked = !LavaPredictor.passable((World)world, bx, by + 1, bz) || LavaPredictor.ceilingBlocked((World)world, bx, by, bz);
            return new Threat(dh, Math.sqrt(dh * dh + (p.y - py) * (p.y - py)), i, bearing, escape, bx, by, bz, blocked, lava);
        }
        return null;
    }

    private static Vec3d horizontalDir(double px, double pz, BlockPos target) {
        double dz;
        if (target == null) {
            return null;
        }
        double dx = (double)target.getX() - px;
        double len = Math.hypot(dx, dz = (double)target.getZ() - pz);
        if (len < 1.0) {
            return null;
        }
        return new Vec3d(dx / len, 0.0, dz / len);
    }

    private static int lavaNear(World world, int bx, int by, int bz) {
        int count = 0;
        int r = 2;
        for (int dx = -r; dx <= r; ++dx) {
            for (int dz = -r; dz <= r; ++dz) {
                for (int dy = -1; dy <= 1; ++dy) {
                    if (!LavaPredictor.isLava(world, new BlockPos(bx + dx, by + dy, bz + dz))) continue;
                    ++count;
                }
            }
        }
        return count;
    }

    private static boolean passable(World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return true;
        }
        return world.getBlockState(pos).getCollisionShape((BlockView)world, pos).isEmpty();
    }

    private static boolean ceilingBlocked(World world, double x, double y, double z) {
        int aboveY = (int)Math.floor(y + 1.8) + 1;
        int baseX = (int)Math.floor(x);
        int baseZ = (int)Math.floor(z);
        double offX = x - (double)baseX;
        double offZ = z - (double)baseZ;
        ArrayList<BlockPos> columns = new ArrayList<BlockPos>(4);
        columns.add(new BlockPos(baseX, aboveY, baseZ));
        if (offX > 0.7) {
            columns.add(new BlockPos(baseX + 1, aboveY, baseZ));
        } else if (offX < 0.3) {
            columns.add(new BlockPos(baseX - 1, aboveY, baseZ));
        }
        if (offZ > 0.7) {
            columns.add(new BlockPos(baseX, aboveY, baseZ + 1));
        } else if (offZ < 0.3) {
            columns.add(new BlockPos(baseX, aboveY, baseZ - 1));
        }
        for (BlockPos c : columns) {
            for (int i = 0; i < 4; ++i) {
                BlockPos q = c.up(i);
                if (!world.isChunkLoaded(q.getX() >> 4, q.getZ() >> 4) || world.getBlockState(q).getCollisionShape((BlockView)world, q).isEmpty()) continue;
                return true;
            }
        }
        return false;
    }

    private static boolean isLava(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        FluidState fluid = world.getFluidState(pos);
        return fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA;
    }

    private Result beginDetour(MinecraftClient mc, Threat t, BlockPos target) {
        double sideB;
        double fx;
        double perpZ;
        double len;
        double tox = (double)t.x() + 0.5 - mc.player.getX();
        double toz = (double)t.z() + 0.5 - mc.player.getZ();
        double fz = toz / (len = Math.max(0.001, Math.hypot(tox, toz)));
        double perpX = -fz;
        double sideA = this.sideScore(mc, perpX, perpZ = (fx = tox / len));
        double sign = sideA >= (sideB = this.sideScore(mc, -perpX, -perpZ)) ? 1.0 : -1.0;
        this.detourX = (int)Math.floor((double)t.x() + perpX * sign * (double)this.lateralDistance);
        this.detourZ = (int)Math.floor((double)t.z() + perpZ * sign * (double)this.lateralDistance);
        this.originalTargetX = target != null ? target.getX() : t.x();
        this.originalTargetZ = target != null ? target.getZ() : t.z();
        this.originalTargetValid = true;
        this.detourTicks = 0;
        this.detourIssued = BaritoneHook.pathTo(this.detourX, this.detourZ);
        if (!this.detourIssued) {
            this.cooldown = this.cooldownTicks;
            this.lastResult = Result.WATCHING;
            FOElytraLog.warn("\u5ca9\u6d46\u9884\u6d4b\uff1a%.1f \u79d2\u540e\u8981\u649e\u4e0a\u5ca9\u6d46\uff08%d %d %d\uff09\uff0c\u4f46\u6ca1\u6709\u53ef\u7528\u7684 Baritone \u53ef\u63d2\u7ed5\u884c\u822a\u70b9\u2192 \u672c tick \u53ea\u9884\u8b66\uff08\u7b49\u5b83\u66f4\u8fd1\u65f6\u4f1a\u6539\u8d70\u7d27\u6025\u89c4\u907f\uff09", (double)t.etaTicks() / 20.0, t.x(), t.y(), t.z());
            return this.lastResult;
        }
        this.avoiding = true;
        ++this.detourCount;
        this.lastResult = Result.DETOUR;
        FOElytraLog.warn("\u3010\u5b9e\u9a8c\u6027\u3011\u5ca9\u6d46\u9884\u6d4b\uff1a%.1f \u79d2\u540e\u8981\u649e\u4e0a\u5ca9\u6d46\uff08%d %d %d\uff0c\u5730\u5e73\u8ddd %.0f \u683c\uff09\u2192 \u4fa7\u5411\u7ed5\u884c\u5230 %d %d\uff08\u4fa7\u504f %d \u683c\uff0c\u5df2\u4ea4\u7ed9 Baritone \u81ea\u5df1\u89c4\u5212\uff09", (double)t.etaTicks() / 20.0, t.x(), t.y(), t.z(), t.horizontalDistance(), this.detourX, this.detourZ, this.lateralDistance);
        FOElytraLog.detail("\u5ca9\u6d46\u7ed5\u884c\u660e\u7ec6\uff1a\u65b9\u4f4d %.0f\u00b0\uff5c\u91c7\u6837 %d \u683c\u5ca9\u6d46\uff5c\u7ed5\u884c\u4fa7\u8bc4\u5206 %.1f vs %.1f\uff5c\u4e0a\u65b9\u901a\u4e0d\u8fc7 %s\uff5c\u9884\u6d4b\u65f6\u957f %d tick", Float.valueOf(t.bearingYaw()), t.lavaSamples(), sideA, sideB, t.blockedAbove() ? "\u662f" : "\u5426", this.horizonTicks);
        return this.lastResult;
    }

    private double sideScore(MinecraftClient mc, double ux, double uz) {
        ClientWorld world = mc.world;
        double score = 0.0;
        for (int s = 4; s <= this.lateralDistance; s += Math.max(4, this.lateralDistance / 6)) {
            int bx = (int)Math.floor(mc.player.getX() + ux * (double)s);
            int by = (int)Math.floor(mc.player.getY());
            int bz = (int)Math.floor(mc.player.getZ() + uz * (double)s);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            int lava = LavaPredictor.lavaNear((World)world, bx, by, bz);
            score += 1.0 - (double)Math.min(3, lava);
            if (!LavaPredictor.passable((World)world, bx, by, bz)) {
                score -= 1.5;
            }
            if (!LavaPredictor.ceilingBlocked((World)world, bx, by, bz)) continue;
            score -= 0.5;
        }
        return score;
    }

    private Result tickDetour(MinecraftClient mc, Threat t, BlockPos target) {
        boolean nearWaypoint;
        ++this.detourTicks;
        if (this.handOffIfInLava(mc)) {
            return Result.IDLE;
        }
        boolean passed = t == null;
        boolean bl = nearWaypoint = Math.abs(mc.player.getX() - ((double)this.detourX + 0.5)) <= (double)this.returnDistance && Math.abs(mc.player.getZ() - ((double)this.detourZ + 0.5)) <= (double)this.returnDistance;
        if (passed || nearWaypoint || this.detourTicks > 400) {
            String why = passed ? "\u89c6\u91ce\u5185\u5df2\u65e0\u5ca9\u6d46" : (nearWaypoint ? "\u5df2\u5230\u7ed5\u884c\u822a\u70b9\u9644\u8fd1" : "\u7ed5\u884c\u8d85\u65f6\uff08400 tick\uff09");
            int backX = target != null ? target.getX() : (this.originalTargetValid ? this.originalTargetX : this.detourX);
            int backZ = target != null ? target.getZ() : (this.originalTargetValid ? this.originalTargetZ : this.detourZ);
            this.again(backX, backZ, "\u5ca9\u6d46\u7ed5\u884c\u7ed3\u675f\uff08" + why + "\uff09\u2192 \u56de\u5230\u539f\u76ee\u6807 " + backX + " " + backZ);
            return this.lastResult;
        }
        this.lastResult = Result.DETOUR;
        return this.lastResult;
    }

    private Result beginEmergency(MinecraftClient mc, Threat t, BlockPos target) {
        this.originalTargetX = target != null ? target.getX() : t.x();
        this.originalTargetZ = target != null ? target.getZ() : t.z();
        this.originalTargetValid = true;
        this.emergencyTicks = 0;
        this.avoiding = true;
        ++this.emergencyCount;
        this.lastResult = Result.EMERGENCY;
        if (this.pauseBaritone && !this.pausedBaritone) {
            BaritoneHook.pause();
            this.pausedBaritone = true;
        }
        FOElytraLog.warn("\u3010\u5b9e\u9a8c\u6027\u3011\u5ca9\u6d46\u9884\u6d4b\uff1a\u53ea\u5269 %.1f \u79d2\u5c31\u8981\u649e\u4e0a\u5ca9\u6d46\uff08%d %d %d\uff0c\u5730\u5e73\u8ddd %.0f \u683c\uff09\u2192 \u7d27\u6025\u89c4\u907f\uff08\u504f\u8f6c %.0f\u00b0\uff0c%s\uff09", (double)t.etaTicks() / 20.0, t.x(), t.y(), t.z(), t.horizontalDistance(), Float.valueOf(this.deflectAngle), this.pausedBaritone ? "\u5df2\u6682\u505c Baritone" : "\u6ca1\u6682\u505c Baritone\uff08\u6309\u8bbe\u7f6e\uff09");
        this.applyEmergencyYaw(mc, t.escapeYaw());
        this.steerEmergency(mc, t);
        return this.lastResult;
    }

    private Result tickEmergency(MinecraftClient mc, Threat t, BlockPos target) {
        boolean timeout;
        ++this.emergencyTicks;
        if (this.handOffIfInLava(mc)) {
            return Result.IDLE;
        }
        boolean safe = t == null;
        boolean bl = timeout = this.emergencyTicks > 80;
        if (safe || timeout) {
            String why = safe ? "\u89c6\u91ce\u5185\u5df2\u65e0\u5ca9\u6d46" : "\u7d27\u6025\u89c4\u907f\u8d85\u65f6\uff0880 tick\uff09";
            int backX = target != null ? target.getX() : (this.originalTargetValid ? this.originalTargetX : mc.player.getBlockX());
            int backZ = target != null ? target.getZ() : (this.originalTargetValid ? this.originalTargetZ : mc.player.getBlockZ());
            this.again(backX, backZ, "\u5ca9\u6d46\u7d27\u6025\u89c4\u907f\u7ed3\u675f\uff08" + why + "\uff09\u2192 \u56de\u5230\u539f\u76ee\u6807 " + backX + " " + backZ);
            return this.lastResult;
        }
        this.applyEmergencyYaw(mc, t.escapeYaw());
        this.steerEmergency(mc, t);
        return this.lastResult;
    }

    private boolean handOffIfInLava(MinecraftClient mc) {
        if (!mc.player.isInLava()) {
            return false;
        }
        this.releaseAvoidKeys();
        if (this.pausedBaritone) {
            BaritoneHook.resume();
            this.pausedBaritone = false;
        }
        this.avoiding = false;
        this.cooldown = 0;
        this.lastResult = Result.IDLE;
        FOElytraLog.warn("\u5ca9\u6d46\u9884\u6d4b\uff1a\u5df2\u8fdb\u5165\u5ca9\u6d46 \u2192 \u89c4\u907f\u4ea4\u7ed9\u300c\u9003\u79bb\u5ca9\u6d46\u300d\uff08\u4e8b\u540e\u81ea\u6551\uff09\uff0c\u672c\u7c7b\u4e0d\u518d\u63a5\u7ba1", new Object[0]);
        return true;
    }

    private void applyEmergencyYaw(MinecraftClient mc, float escapeYaw) {
        float left = LavaPredictor.wrap(escapeYaw + this.deflectAngle * 0.5f);
        float right = LavaPredictor.wrap(escapeYaw - this.deflectAngle * 0.5f);
        float chosen = this.sideScore(mc, LavaPredictor.yawDirX(left), LavaPredictor.yawDirZ(left)) >= this.sideScore(mc, LavaPredictor.yawDirX(right), LavaPredictor.yawDirZ(right)) ? left : right;
        mc.player.setYaw(chosen);
        boolean blockedAbove = LavaPredictor.ceilingBlocked((World)mc.world, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float pitch = blockedAbove ? -5.0f : 10.0f;
        mc.player.setPitch(Math.max(-35.0f, Math.min(10.0f, pitch)));
    }

    private void steerEmergency(MinecraftClient mc, Threat t) {
        PlayerAction.pressForward(true);
        if (mc.player.isInLava() || mc.player.isTouchingWater()) {
            PlayerAction.pressJump(true);
        }
    }

    private void again(int targetX, int targetZ, String message) {
        this.releaseAvoidKeys();
        if (this.pausedBaritone) {
            BaritoneHook.resume();
            this.pausedBaritone = false;
        }
        this.avoiding = false;
        this.cooldown = this.cooldownTicks;
        this.lastResult = Result.IDLE;
        FOElytraLog.info("%s", message);
        BaritoneHook.pathTo(targetX, targetZ);
    }

    public void reset() {
        this.releaseAvoidKeys();
        if (this.pausedBaritone) {
            BaritoneHook.resume();
            this.pausedBaritone = false;
        }
        this.avoiding = false;
        this.threat = null;
        this.lastResult = Result.IDLE;
        this.cooldown = 0;
        this.warnCooldown = 0;
        this.emergencyTicks = 0;
        this.detourTicks = 0;
        this.detourIssued = false;
        this.originalTargetValid = false;
    }

    public void release(MinecraftClient mc) {
        this.reset();
    }

    private void releaseAvoidKeys() {
        PlayerAction.pressForward(false);
        PlayerAction.pressJump(false);
    }

    private static float wrap(float yaw) {
        return (yaw % 360.0f + 360.0f) % 360.0f;
    }

    private static double yawDirX(float yaw) {
        return -Math.sin(Math.toRadians(yaw));
    }

    private static double yawDirZ(float yaw) {
        return Math.cos(Math.toRadians(yaw));
    }

    public static List<String[]> recommendedSettings() {
        ArrayList<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{"\u542f\u7528\u5ca9\u6d46\u9884\u6d4b\u89c4\u907f", "\u5b9e\u9a8c\u6027\uff1a\u6cbf\u5f53\u524d\u98de\u884c\u65b9\u5411\u9884\u6d4b 1.5~4 \u79d2\uff0c\u63d0\u524d\u7ed5\u5f00\u5ca9\u6d46\u67f1/\u5ca9\u6d46\u6e56\u3002\u9884\u6d4b\u53ef\u80fd\u8bef\u5224\uff08\u6539\u6210\u722c\u5347\u3001\u7ed5\u5c71\u3001\u4e34\u65f6\u6539\u822a\u70b9\u90fd\u4f1a\u8ba9\u5b83\u7b97\u9519\uff09\uff0c\u8bef\u5224\u7684\u8868\u73b0\u662f\u300c\u591a\u7ed5\u4e00\u70b9\u8def\u300d\uff0c\u4e0d\u4f1a\u6389\u8840\u3001\u4e0d\u4f1a\u767b\u51fa\uff1b\u60f3\u5148\u89c2\u5bdf\u5c31\u5148\u53ea\u7528\u300c\u53ea\u9884\u8b66\u4e0d\u63a5\u7ba1\u300d\u3002", "\u5173", "\u5f00/\u5173"});
        out.add(new String[]{"\u9884\u6d4b\u65f6\u957f\uff08\u79d2\uff09", "\u5f80\u524d\u9884\u6d4b\u591a\u5c11\u79d2\u7684\u98de\u884c\u8f68\u8ff9\u3002\u592a\u77ed\u6765\u4e0d\u53ca\u7ed5\uff0c\u592a\u957f\u5bb9\u6613\u628a\u8fdc\u5904\u7684\u5ca9\u6d46\u6e56\u4e5f\u5f53\u6210\u5a01\u80c1\u3002", "3.0", "1.5 ~ 4.0"});
        out.add(new String[]{"\u7d27\u6025\u9608\u503c\uff08\u79d2\uff09", "\u9884\u8ba1\u5728\u8fd9\u4e2a\u65f6\u95f4\u5185\u649e\u4e0a\u5ca9\u6d46\u5c31\u6539\u7528\u300c\u7d27\u6025\u89c4\u907f\u300d\uff08\u6682\u505c Baritone + \u504f\u8f6c\u89c6\u89d2 + \u653e\u70df\u82b1\uff09\uff1b\u66f4\u65e9\u53d1\u73b0\u7684\u5a01\u80c1\u8d70\u300c\u4fa7\u5411\u7ed5\u884c\u300d\uff08\u63d2\u4e00\u4e2a\u822a\u70b9\u4ea4\u7ed9 Baritone \u81ea\u5df1\u89c4\u5212\uff09\u3002", "1.2", "0.4 ~ 2.0"});
        out.add(new String[]{"\u4fa7\u5411\u7ed5\u884c\u8ddd\u79bb", "\u7ed5\u884c\u822a\u70b9\u79bb\u5371\u9669\u70b9\u5f80\u4fa7\u9762\u504f\u591a\u5c11\u683c\u3002\u8d8a\u5927\u8d8a\u5b89\u5168\u3001\u4e5f\u8d8a\u7ed5\u8def\u3002", "45", "16 ~ 96"});
        out.add(new String[]{"\u7ed5\u884c\u7ed3\u675f\u8ddd\u79bb", "\u79bb\u7ed5\u884c\u822a\u70b9\u591a\u8fd1\u5c31\u7b97\u7ed5\u8fc7\u8fd9\u4e00\u6bb5\uff08\u4e5f\u53ef\u4ee5\u9760\u300c\u524d\u65b9\u9884\u6d4b\u53d8\u5e72\u51c0\u300d\u63d0\u524d\u7ed3\u675f\uff09\u3002", "25", "8 ~ 64"});
        out.add(new String[]{"\u89c4\u907f\u9632\u6296 tick", "\u4e24\u6b21\u89c4\u907f\u52a8\u4f5c\u4e4b\u95f4\u81f3\u5c11\u95f4\u9694\u591a\u5c11 tick\uff0c\u9632\u6b62\u5728\u5ca9\u6d46\u8fb9\u7f18\u53cd\u590d\u89e6\u53d1\u3002", "40", "10 ~ 200"});
        out.add(new String[]{"\u53ea\u9884\u8b66\u4e0d\u63a5\u7ba1", "\u53ea\u628a\u9884\u6d4b\u7ed3\u679c\u5199\u8fdb\u65e5\u5fd7/\u804a\u5929\u680f\uff0c\u4e0d\u6539 Baritone \u822a\u70b9\u3001\u4e0d\u78b0\u6309\u952e\uff08\u7528\u6765\u5148\u9a8c\u8bc1\u9884\u6d4b\u51c6\u4e0d\u51c6\uff09\u3002", "\u5173", "\u5f00/\u5173"});
        out.add(new String[]{"\u7d27\u6025\u65f6\u6682\u505c Baritone", "\u7d27\u6025\u89c4\u907f\u671f\u95f4\u6682\u505c Baritone\uff08p\uff09\uff0c\u8131\u79bb\u540e\u6062\u590d\uff08r\uff09\u2014\u2014\u4e0d\u6682\u505c\u7684\u8bdd\u5b83\u4f1a\u548c\u6211\u4eec\u62a2\u89c6\u89d2\u3002", "\u5f00", "\u5f00/\u5173"});
        out.add(new String[]{"\u7d27\u6025\u504f\u8f6c\u89d2\uff08\u5ea6\uff09", "\u7d27\u6025\u89c4\u907f\u65f6\u76f8\u5bf9\u300c\u5ca9\u6d46\u53cd\u65b9\u5411\u300d\u518d\u5de6\u53f3\u504f\u591a\u5c11\u5ea6\uff0c\u7528\u6765\u5728\u4e24\u4fa7\u91cc\u6311\u4e00\u6761\u66f4\u5e72\u51c0\u7684\u51fa\u8def\u3002", "75", "30 ~ 90"});
        return out;
    }

    public static enum Result {
        IDLE("\u7a7a\u95f2"),
        WATCHING("\u89c2\u5bdf\u4e2d"),
        WARN_ONLY("\u4ec5\u8b66\u544a"),
        DETOUR("\u7ed5\u884c"),
        EMERGENCY("\u7d27\u6025");


        private final String label;

        Result(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public record Options(boolean enabled, double horizonSeconds, double urgentSeconds, int lateralDistance, int returnDistance, int cooldownTicks, boolean warnOnly, boolean pauseBaritone, double deflectAngle) {
    }

    public record Threat(double horizontalDistance, double distance, int etaTicks, float bearingYaw, float escapeYaw, int x, int y, int z, boolean blockedAbove, int lavaSamples) {
    }
}

