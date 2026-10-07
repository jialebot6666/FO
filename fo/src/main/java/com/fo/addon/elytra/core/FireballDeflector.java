package com.fo.addon.elytra.core;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
public final class FireballDeflector {
    private static final int RETRY_TICKS = 30;
    private static final int MAX_ENGAGE_TICKS = 200;
    private static final double REACH_TOLERANCE = 0.5;
    public enum Result {
        CLEAR("无威胁"),
        ENGAGING("拦截中"),
        TOO_MANY("火球过多");

        public final String label;

        Result(String label) {
            this.label = label;
        }

        /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString，必须覆写否则显示英文枚举名） */
        @Override
        public String toString() {
            return label;
        }
    }
    private final Set<UUID> ignored = new HashSet<>();
    private boolean engaging;
    private boolean pausedByUs;
    private long engageStart;
    private long lastAttackTick = Long.MIN_VALUE;
    private int attackDelay = -1;
    private Entity pendingAttack;
    private int deflected;
    private int tooMany;
    public Result tick(boolean enabled, boolean pauseBaritone, int maxFireballs, double fixedRange,
                       boolean engageWhenTooMany, Runnable resumeWhenClear) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return Result.CLEAR;
        if (!enabled) {
            releasePending();
            finishEngagement(resumeWhenClear, null);
            return Result.CLEAR;
        }
        long now = mc.player.age;
        if (attackDelay > 0) {
            attackDelay--;
            if (attackDelay == 0) {
                if (pendingAttack != null && pendingAttack.isAlive()) {
                    attack(mc, pendingAttack);
                    lastAttackTick = now;
                }
                pendingAttack = null;
            }
        }
        List<Entity> fireballs = nearbyFireballs(mc, fixedRange);
        if (fireballs.isEmpty()) {
            return finishEngagement(resumeWhenClear, engaging ? "火球已消失" : null);
        }
        if (fireballs.size() > Math.max(1, maxFireballs)) {
            tooMany++;
            if (!engageWhenTooMany) return Result.TOO_MANY;
        } else {
            tooMany = 0;
        }
        Entity fireball = fireballs.get(0);
        double dist = Math.sqrt(fireball.squaredDistanceTo(mc.player));
        double reach = attackReach(mc) + REACH_TOLERANCE;
        if (dist > reach) {
            return finishEngagement(resumeWhenClear, engaging ? "火球超出攻击距离" : null);
        }
        if (engaging && now - engageStart > MAX_ENGAGE_TICKS) {
            FOElytraLog.warn("拦截火球超时（%d tick），恢复飞行", MAX_ENGAGE_TICKS);
            return finishEngagement(resumeWhenClear, null);
        }
        UUID id = fireball.getUuid();
        if (ignored.contains(id) && now - lastAttackTick < RETRY_TICKS) {
            return Result.ENGAGING;
        }
        if (!engaging) {
            engaging = true;
            engageStart = now;
            FOElytraLog.detail("拦截火球：距离 %.2f 格｜范围 %.2f｜暂停 Baritone %s",
                dist, fixedRange, pauseBaritone ? "是" : "否");
        }
        if (pauseBaritone && !pausedByUs) {
            BaritoneHook.pause();
            pausedByUs = true;
        }
        ignored.add(id);
        Vec3d target = fireball.getEntityPos().add(0, 0.5, 0);
        InvHelper.lookAt(mc.player, target);
        if (now - lastAttackTick >= RETRY_TICKS) FOElytraLog.warn("准备拦截火球！");
        pendingAttack = fireball;
        attackDelay = 2;
        return Result.ENGAGING;
    }
    private void attack(MinecraftClient mc, Entity fireball) {
        try {
            mc.interactionManager.attackEntity(mc.player, fireball);
            mc.player.swingHand(Hand.MAIN_HAND);
            deflected++;
            FOElytraLog.detail("已对火球出手（累计 %d 次）", deflected);
        } catch (Throwable t) {
            FOElytraLog.detailError("攻击火球", t);
            FOElytraLog.debug("拦截火球失败：%s", t);
        }
    }
    private Result finishEngagement(Runnable resumeWhenClear, String reason) {
        releasePending();
        if (!engaging) return Result.CLEAR;
        engaging = false;
        ignored.clear();
        if (reason != null) FOElytraLog.debug("%s，恢复飞行（累计拦截 %d）", reason, deflected);
        if (pausedByUs) {
            pausedByUs = false;
            if (resumeWhenClear != null) resumeWhenClear.run();
        }
        return Result.CLEAR;
    }
    private List<Entity> nearbyFireballs(MinecraftClient mc, double fixedRange) {
        double range = fixedRange;
        if (range <= 0) {
            range = attackReach(mc) + 0.8;
        }
        Vec3d pos = mc.player.getEntityPos();
        Box box = new Box(
            pos.x - range, pos.y - range, pos.z - range,
            pos.x + range, pos.y + range, pos.z + range
        );
        return new ArrayList<>(mc.world.getOtherEntities(mc.player, box, e -> e instanceof AbstractFireballEntity));
    }
    private double attackReach(MinecraftClient mc) {
        try {
            return mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
        } catch (Throwable ignored) {
            return 3.0;
        }
    }
    public int deflectedCount() {
        return deflected;
    }
    public boolean isEngaging() {
        return engaging;
    }
    public boolean pausedBaritone() {
        return pausedByUs;
    }
    public int tooManyCount() {
        return tooMany;
    }
    public void reset() {
        ignored.clear();
        engaging = false;
        pausedByUs = false;
        engageStart = 0;
        lastAttackTick = Long.MIN_VALUE;
        deflected = 0;
        tooMany = 0;
        releasePending();
    }
    private void releasePending() {
        attackDelay = -1;
        pendingAttack = null;
    }
}
