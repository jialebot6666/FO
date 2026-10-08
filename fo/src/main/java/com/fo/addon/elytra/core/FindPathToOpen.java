package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FlightPredictor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class FindPathToOpen {
    private static final double PLAYER_WIDTH = 0.6;
    private static final double PLAYER_HEIGHT = 0.6;
    private static final int PREDICT_TICKS = 40;
    private static final double ANGLE_RANGE = 15.0;
    private static final int STEPS = 5;

    private FindPathToOpen() {
    }

    public static Takeoff getTakeoffDirection(double maxDist, double surroundDist, double yr, double yh) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) {
            return null;
        }
        Vec3d start = mc.player.getEntityPos().add(0.0, yh, 0.0);
        Vec3d startv = mc.player.getVelocity();
        ArrayList<Scored> candidates = new ArrayList<Scored>();
        for (Vec3d dir : FindPathToOpen.generateDirections(yr)) {
            double score;
            BlockPos end = FindPathToOpen.hasNoObstacleInLine((World)world, start, startv, dir, maxDist);
            if (end == null || !((score = FindPathToOpen.computeOpennessScore((World)world, start, dir, maxDist, surroundDist)) > 0.0)) continue;
            candidates.add(new Scored(dir, dir.y < 0.0 ? score * 0.5 : score, end));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        ArrayList<Scored> top = new ArrayList<Scored>(candidates.subList(0, Math.min(3, candidates.size())));
        Scored best = FindPathToOpen.refineDirections((World)world, start, startv, top, maxDist, surroundDist);
        return new Takeoff(FindPathToOpen.getPitchFromDirection(best.dir), FindPathToOpen.getYawFromDirection(best.dir), best.end);
    }

    private static List<Vec3d> generateDirections(double yr) {
        ArrayList<Vec3d> list = new ArrayList<Vec3d>(512);
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < 512; ++i) {
            double y = 1.0 - (double)i / 511.0 * yr;
            double radius = Math.sqrt(1.0 - y * y);
            double theta = phi * (double)i;
            list.add(new Vec3d(Math.cos(theta) * radius, y, Math.sin(theta) * radius));
        }
        return list;
    }

    private static BlockPos hasNoObstacleInLine(World world, Vec3d start, Vec3d startv, Vec3d dir, double maxDist) {
        Vec3d lookVec = dir.normalize();
        double distanceTravelled = 0.0;
        Vec3d nextPos = null;
        List<Vec3d> ts = FlightPredictor.predictPath(40, start, startv, lookVec);
        for (int t = 1; t < 40; ++t) {
            nextPos = ts.get(t);
            if (!world.isAir(BlockPos.ofFloored((Position)nextPos))) {
                return null;
            }
            Box playerBox = new Box(nextPos.x - 0.3, nextPos.y, nextPos.z - 0.3, nextPos.x + 0.3, nextPos.y + 0.6, nextPos.z + 0.3);
            if (FindPathToOpen.collides(world, playerBox)) {
                return null;
            }
            if ((distanceTravelled += ts.get(t - 1).distanceTo(nextPos)) >= maxDist) break;
        }
        return nextPos == null ? null : BlockPos.ofFloored(nextPos);
    }

    private static boolean collides(World world, Box box) {
        for (BlockPos pos : BlockPos.iterate((Box)box)) {
            VoxelShape shape;
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || (shape = state.getCollisionShape((BlockView)world, pos)).isEmpty()) continue;
            for (Box bb : shape.getBoundingBoxes()) {
                if (!bb.offset(pos).intersects(box)) continue;
                return true;
            }
        }
        return false;
    }

    private static double computeOpennessScore(World world, Vec3d start, Vec3d dir, double maxDist, double surroundDist) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double[][] sampleDistances = new double[][]{{10.0, 0.15}, {20.0, 0.35}, {30.0, 0.35}, {40.0, 0.15}};
        double score = 0.0;
        for (double[] sample : sampleDistances) {
            double d = sample[0];
            double weight = sample[1];
            if (d > maxDist) continue;
            Vec3d point = start.add(dir.multiply(d));
            BlockPos pos = BlockPos.ofFloored((Position)point);
            if (!world.getBlockState(pos).isAir()) {
                return 0.0;
            }
            Vec3d[] axes = new Vec3d[]{new Vec3d(1.0, 0.0, 0.0), new Vec3d(-1.0, 0.0, 0.0), new Vec3d(0.0, 1.0, 0.0), new Vec3d(0.0, -1.0, 0.0), new Vec3d(0.0, 0.0, 1.0), new Vec3d(0.0, 0.0, -1.0)};
            double pointMin = Double.MAX_VALUE;
            for (Vec3d axis : axes) {
                Vec3d end = point.add(axis.multiply(surroundDist));
                RaycastContext ctx = new RaycastContext(point, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, (Entity)mc.player);
                BlockHitResult hit = world.raycast(ctx);
                double dist = hit != null && hit.getType() != HitResult.Type.MISS ? hit.getPos().distanceTo(point) : surroundDist;
                pointMin = Math.min(pointMin, dist);
            }
            if (pointMin <= 1.5) break;
            score += pointMin * weight;
        }
        return score;
    }

    private static Scored refineDirections(World world, Vec3d start, Vec3d startv, List<Scored> topCandidates, double maxDist, double surroundDist) {
        Scored best = null;
        double bestScore = -1.0;
        for (Scored cand : topCandidates) {
            float baseYaw = FindPathToOpen.getYawFromDirection(cand.dir);
            float basePitch = FindPathToOpen.getPitchFromDirection(cand.dir);
            for (int i = 0; i < 5; ++i) {
                for (int j = 0; j < 5; ++j) {
                    double score;
                    float yawOffset = (float)(-15.0 + 30.0 * (double)i / 4.0);
                    float testYaw = baseYaw + yawOffset;
                    float pitchOffset = (float)(-15.0 + 30.0 * (double)j / 4.0);
                    float testPitch = Math.max(-90.0f, Math.min(90.0f, basePitch + pitchOffset));
                    Vec3d testDir = FindPathToOpen.getDirectionFromYawPitch(testYaw, testPitch);
                    BlockPos end = FindPathToOpen.hasNoObstacleInLine(world, start, startv, testDir, maxDist);
                    if (end == null || !((score = FindPathToOpen.computeOpennessScore(world, start, testDir, maxDist, surroundDist)) > bestScore)) continue;
                    bestScore = score;
                    best = new Scored(testDir, score, end);
                }
            }
        }
        return best != null ? best : topCandidates.get(0);
    }

    private static float getYawFromDirection(Vec3d dir) {
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        return (float)((yaw + 360.0) % 360.0);
    }

    private static float getPitchFromDirection(Vec3d dir) {
        return (float)Math.toDegrees(-Math.asin(dir.y));
    }

    public static Vec3d getDirectionFromYawPitch(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z);
    }

    private static final class Scored {
        final Vec3d dir;
        final double score;
        final BlockPos end;

        Scored(Vec3d dir, double score, BlockPos end) {
            this.dir = dir;
            this.score = score;
            this.end = end;
        }
    }

    public static final class Takeoff {
        public final float pitch;
        public final float yaw;
        public final BlockPos end;

        public Takeoff(float pitch, float yaw, BlockPos end) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.end = end;
        }
    }
}

