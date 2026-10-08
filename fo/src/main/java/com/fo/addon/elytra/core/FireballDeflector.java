package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class FireballDeflector {
    private static final int RETRY_TICKS = 30;
    private static final int MAX_ENGAGE_TICKS = 200;
    private static final double REACH_TOLERANCE = 0.5;
    private final Set<UUID> ignored = new HashSet<UUID>();
    private boolean engaging;
    private boolean pausedByUs;
    private long engageStart;
    private long lastAttackTick = Long.MIN_VALUE;
    private int attackDelay = -1;
    private Entity pendingAttack;
    private int deflected;
    private int tooMany;

    public Result tick(boolean enabled, boolean pauseBaritone, int maxFireballs, double fixedRange, boolean engageWhenTooMany, Runnable resumeWhenClear) {
        double reach;
        Entity fireball;
        double dist;
        List<Entity> fireballs;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return Result.CLEAR;
        }
        if (!enabled) {
            this.releasePending();
            this.finishEngagement(resumeWhenClear, null);
            return Result.CLEAR;
        }
        long now = mc.player.age;
        if (this.attackDelay > 0) {
            --this.attackDelay;
            if (this.attackDelay == 0) {
                if (this.pendingAttack != null && this.pendingAttack.isAlive()) {
                    this.attack(mc, this.pendingAttack);
                    this.lastAttackTick = now;
                }
                this.pendingAttack = null;
            }
        }
        if ((fireballs = this.nearbyFireballs(mc, fixedRange)).isEmpty()) {
            return this.finishEngagement(resumeWhenClear, this.engaging ? "\u706b\u7403\u5df2\u6d88\u5931" : null);
        }
        if (fireballs.size() > Math.max(1, maxFireballs)) {
            ++this.tooMany;
            if (!engageWhenTooMany) {
                return Result.TOO_MANY;
            }
        } else {
            this.tooMany = 0;
        }
        if ((dist = Math.sqrt((fireball = fireballs.get(0)).squaredDistanceTo((Entity)mc.player))) > (reach = this.attackReach(mc) + 0.5)) {
            return this.finishEngagement(resumeWhenClear, this.engaging ? "\u706b\u7403\u8d85\u51fa\u653b\u51fb\u8ddd\u79bb" : null);
        }
        if (this.engaging && now - this.engageStart > 200L) {
            FOElytraLog.warn("\u62e6\u622a\u706b\u7403\u8d85\u65f6\uff08%d tick\uff09\uff0c\u6062\u590d\u98de\u884c", 200);
            return this.finishEngagement(resumeWhenClear, null);
        }
        UUID id = fireball.getUuid();
        if (this.ignored.contains(id) && now - this.lastAttackTick < 30L) {
            return Result.ENGAGING;
        }
        if (!this.engaging) {
            this.engaging = true;
            this.engageStart = now;
            FOElytraLog.detail("\u62e6\u622a\u706b\u7403\uff1a\u8ddd\u79bb %.2f \u683c\uff5c\u8303\u56f4 %.2f\uff5c\u6682\u505c Baritone %s", dist, fixedRange, pauseBaritone ? "\u662f" : "\u5426");
        }
        if (pauseBaritone && !this.pausedByUs) {
            BaritoneHook.pause();
            this.pausedByUs = true;
        }
        this.ignored.add(id);
        Vec3d target = fireball.getEntityPos().add(0.0, 0.5, 0.0);
        InvHelper.lookAt((PlayerEntity)mc.player, target);
        if (now - this.lastAttackTick >= 30L) {
            FOElytraLog.warn("\u51c6\u5907\u62e6\u622a\u706b\u7403\uff01", new Object[0]);
        }
        this.pendingAttack = fireball;
        this.attackDelay = 2;
        return Result.ENGAGING;
    }

    private void attack(MinecraftClient mc, Entity fireball) {
        try {
            mc.interactionManager.attackEntity((PlayerEntity)mc.player, fireball);
            mc.player.swingHand(Hand.MAIN_HAND);
            ++this.deflected;
            FOElytraLog.detail("\u5df2\u5bf9\u706b\u7403\u51fa\u624b\uff08\u7d2f\u8ba1 %d \u6b21\uff09", this.deflected);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("\u653b\u51fb\u706b\u7403", t);
            FOElytraLog.debug("\u62e6\u622a\u706b\u7403\u5931\u8d25\uff1a%s", t);
        }
    }

    private Result finishEngagement(Runnable resumeWhenClear, String reason) {
        this.releasePending();
        if (!this.engaging) {
            return Result.CLEAR;
        }
        this.engaging = false;
        this.ignored.clear();
        if (reason != null) {
            FOElytraLog.debug("%s\uff0c\u6062\u590d\u98de\u884c\uff08\u7d2f\u8ba1\u62e6\u622a %d\uff09", reason, this.deflected);
        }
        if (this.pausedByUs) {
            this.pausedByUs = false;
            if (resumeWhenClear != null) {
                resumeWhenClear.run();
            }
        }
        return Result.CLEAR;
    }

    private List<Entity> nearbyFireballs(MinecraftClient mc, double fixedRange) {
        double range = fixedRange;
        if (range <= 0.0) {
            range = this.attackReach(mc) + 0.8;
        }
        Vec3d pos = mc.player.getEntityPos();
        Box box = new Box(pos.x - range, pos.y - range, pos.z - range, pos.x + range, pos.y + range, pos.z + range);
        return new ArrayList<Entity>(mc.world.getOtherEntities((Entity)mc.player, box, e -> e instanceof AbstractFireballEntity));
    }

    private double attackReach(MinecraftClient mc) {
        try {
            return mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
        }
        catch (Throwable ignored) {
            return 3.0;
        }
    }

    public int deflectedCount() {
        return this.deflected;
    }

    public boolean isEngaging() {
        return this.engaging;
    }

    public boolean pausedBaritone() {
        return this.pausedByUs;
    }

    public int tooManyCount() {
        return this.tooMany;
    }

    public void reset() {
        this.ignored.clear();
        this.engaging = false;
        this.pausedByUs = false;
        this.engageStart = 0L;
        this.lastAttackTick = Long.MIN_VALUE;
        this.deflected = 0;
        this.tooMany = 0;
        this.releasePending();
    }

    private void releasePending() {
        this.attackDelay = -1;
        this.pendingAttack = null;
    }

    public static enum Result {
        CLEAR("\u5b89\u5168"),
        ENGAGING("\u62e6\u622a\u4e2d"),
        TOO_MANY("\u8fc7\u591a");


        private final String label;

        Result(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }
}

