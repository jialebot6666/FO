package com.fo.addon.elytra.hell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;

public final class HellFlightRuntime {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static boolean active;
    private static boolean kineticProtection;
    private static float timerMultiplier = 1.0f;
    private static int collisionTicks;
    private static FireworkRocketEntity lastFirework;
    private static boolean rotationChanged;
    private static float savedYaw;
    private static float savedPitch;

    private HellFlightRuntime() {
    }

    public static void configure(boolean active, boolean kineticProtection) {
        HellFlightRuntime.active = active;
        HellFlightRuntime.kineticProtection = kineticProtection;
        if (!active) reset();
    }

    public static void reset() {
        collisionTicks = 0;
        lastFirework = null;
        rotationChanged = false;
        timerMultiplier = 1.0f;
    }

    public static boolean isActive() {
        return active;
    }

    public static float timerMultiplier() {
        return timerMultiplier;
    }

    public static void setTimerMultiplier(float value) {
        timerMultiplier = Math.max(0.6f, Math.min(1.0f, value));
    }

    public static void lowerTimer() {
        setTimerMultiplier(timerMultiplier - 0.1f);
    }

    public static void recoverTimer() {
        setTimerMultiplier(timerMultiplier + 0.1f);
    }

    public static void onTravelHead(ClientPlayerEntity player) {
        if (!active || !kineticProtection || player == null || !player.isGliding()) return;
        Vec3d motion = player.getVelocity().multiply(4.0);
        Vec3d collided = Entity.adjustMovementForCollisions(
            player,
            motion,
            player.getBoundingBox(),
            player.getEntityWorld(),
            Entity.findCollisions(player, player.getEntityWorld(), player.getBoundingBox().stretch(motion))
        );
        boolean collision = Math.abs(collided.x - motion.x) > 1.0E-4 || Math.abs(collided.z - motion.z) > 1.0E-4;
        if (collision) collisionTicks = 2;
        if (!collision && collisionTicks <= 0) return;
        if (collisionTicks > 0) collisionTicks--;
        if (!rotationChanged) {
            savedYaw = player.getYaw();
            savedPitch = player.getPitch();
            rotationChanged = true;
        }
        player.setYaw(player.getYaw() + 180.0f);
        if (lastFirework != null && lastFirework.isAlive()) player.setPitch(-89.999f);
    }

    public static void onTickTail(ClientPlayerEntity player) {
        if (!rotationChanged || player == null) return;
        player.setYaw(savedYaw);
        player.setPitch(savedPitch);
        rotationChanged = false;
    }

    public static void onFireworkSpawn(FireworkRocketEntity firework) {
        if (active && MC.player != null && firework.getOwner() == MC.player) lastFirework = firework;
    }
}
