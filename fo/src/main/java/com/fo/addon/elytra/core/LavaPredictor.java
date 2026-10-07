package com.fo.addon.elytra.core;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
public final class LavaPredictor {
    public enum Result {
        IDLE("空闲"),
        WATCHING("监视中"),
        WARN_ONLY("仅预警"),
        DETOUR("绕行"),
        EMERGENCY("紧急规避");

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
    public record Threat(
        double horizontalDistance,
        double distance,
        int etaTicks,
        float bearingYaw,
        float escapeYaw,
        int x,
        int y,
        int z,
        boolean blockedAbove,
        int lavaSamples
    ) {}
    public record Options(
        boolean enabled,
        double horizonSeconds,
        double urgentSeconds,
        int lateralDistance,
        int returnDistance,
        int cooldownTicks,
        boolean warnOnly,
        boolean pauseBaritone,
        double deflectAngle
    ) {}
    private static final int SCAN_STRIDE = 2;
    private static final int LAVA_SCAN_RADIUS = 2;
    private static final int CEILING_CHECK_BLOCKS = 4;
    private static final float EMERGENCY_PITCH_MIN = -35.0f;
    private static final float EMERGENCY_PITCH_MAX = 10.0f;
    private static final float EMERGENCY_PITCH_BLOCKED = -5.0f;
    private static final int EMERGENCY_MAX_TICKS = 80;
    private static final int DETOUR_MAX_TICKS = 400;
    private static final int REFIRE_TICKS = 20;
    private static final int REFIRE_MAX = 2;
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
    private int refires;
    private int refireCooldown;
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
        this.horizonTicks = (int) Math.round(Math.max(1.5, Math.min(4.0, opts.horizonSeconds())) * 20.0);
        this.urgentTicks = (int) Math.round(Math.max(0.4, Math.min(2.0, opts.urgentSeconds())) * 20.0);
        this.lateralDistance = Math.max(16, Math.min(96, opts.lateralDistance()));
        this.returnDistance = Math.max(8, Math.min(64, opts.returnDistance()));
        this.cooldownTicks = Math.max(10, Math.min(200, opts.cooldownTicks()));
        this.warnOnly = opts.warnOnly();
        this.pauseBaritone = opts.pauseBaritone();
        this.deflectAngle = (float) Math.max(30.0, Math.min(90.0, opts.deflectAngle()));
    }
    public Threat threat() {
        return threat;
    }
    public boolean isAvoiding() {
        return avoiding;
    }
    public boolean pausedBaritone() {
        return pausedBaritone;
    }
    public Result lastResult() {
        return lastResult;
    }
    public int detourCount() {
        return detourCount;
    }
    public int emergencyCount() {
        return emergencyCount;
    }
    public String statusText() {
        Threat t = threat;
        if (t == null) {
            return avoiding ? "岩浆预测：规避中（视野内已无威胁）" : "岩浆预测：前方干净";
        }
        String phase = switch (lastResult) {
            case EMERGENCY -> "紧急规避";
            case DETOUR -> "侧向绕行";
            case WARN_ONLY -> "只预警";
            case WATCHING -> "跟踪";
            default -> "待命";
        };
        return String.format("岩浆预测：%.1fs 后 %d %d %d 有岩浆（地平距 %.0f 格 / 方位 %.0f°）→ %s",
            t.etaTicks() / 20.0, t.x(), t.y(), t.z(), t.horizontalDistance(), t.bearingYaw(), phase);
    }
    public Result tick(MinecraftClient mc, BlockPos target) {
        if (!enabled) {
            reset();
            return Result.IDLE;
        }
        if (mc == null || mc.player == null || mc.world == null) {
            reset();
            return Result.IDLE;
        }
        if (cooldown > 0) cooldown--;
        if (warnCooldown > 0) warnCooldown--;
       if (mc.player.isInLava() && !avoiding) {
            releaseAvoidKeys();
            threat = null;
            lastResult = Result.IDLE;
            return Result.IDLE;
        }
        if (InvHelper.screenOpen()) {
            threat = null;
            lastResult = Result.IDLE;
            return Result.IDLE;
        }
        Threat found = mc.player.isGliding() ? predict(mc, target) : null;
        threat = found;
        if (avoiding) {
            if (lastResult == Result.EMERGENCY) return tickEmergency(mc, found, target);
            return tickDetour(mc, found, target);
        }
        if (found == null) {
            lastResult = Result.IDLE;
            return Result.IDLE;
        }
        boolean urgent = found.etaTicks() <= urgentTicks;
        if (warnOnly) {
            lastResult = Result.WARN_ONLY;
            if (warnCooldown <= 0) {
                warnCooldown = WARN_COOLDOWN;
                FOElytraLog.warn("【实验性·只预警】预测 %.1f 秒后会碰到岩浆（%s｜地平距 %.0f 格 / 方位 %.0f°）"
                        + "→ 按设置不接管（打开「只预警不接管」的开关才能让我绕开）",
                    found.etaTicks() / 20.0, found.x() + " " + found.y() + " " + found.z(),
                    found.horizontalDistance(), found.bearingYaw());
                FOElytraLog.detail("岩浆预测明细：ETA %d tick｜采样 %d 格岩浆｜上方通不过 %s｜"
                        + "紧急阈值 %d tick｜预测时长 %d tick",
                    found.etaTicks(), found.lavaSamples(), found.blockedAbove() ? "是" : "否",
                    urgentTicks, horizonTicks);
            }
            return lastResult;
        }
        if (cooldown > 0) {
            lastResult = Result.WATCHING;
            return lastResult;
        }
        if (urgent) return beginEmergency(mc, found, target);
        return beginDetour(mc, found, target);
    }
    private Threat predict(MinecraftClient mc, BlockPos target) {
        World world = mc.world;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        Vec3d vel = mc.player.getVelocity();
        Vec3d look = mc.player.getRotationVec(1.0F);
        Vec3d blended = look;
        double velH = vel.horizontalLength();
        if (velH > 0.05) {
            blended = blended.add(new Vec3d(vel.x / velH * 0.5, 0.0, vel.z / velH * 0.5));
        }
        Vec3d targetDir = horizontalDir(px, pz, target);
        if (targetDir != null) {
            blended = blended.add(targetDir.multiply(0.5));
        }
        if (blended.lengthSquared() < 1.0E-6) return null;
        look = blended.normalize();
        List<Vec3d> path = FlightPredictor.predictPath(horizonTicks, new Vec3d(px, py, pz), vel, look);
        for (int i = 1; i < path.size(); i += SCAN_STRIDE) {
            Vec3d p = path.get(i);
            int bx = (int) Math.floor(p.x);
            int by = (int) Math.floor(p.y);
            int bz = (int) Math.floor(p.z);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            int lava = lavaNear(world, bx, by, bz);
            if (lava <= 0) continue;
            double dx = p.x - px;
            double dz = p.z - pz;
            double dh = Math.hypot(dx, dz);
            float bearing = (float) ((Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0);
            float escape = (float) ((bearing + 180.0) % 360.0);
            boolean blocked = !passable(world, bx, by + 1, bz) || ceilingBlocked(world, bx, by, bz);
            return new Threat(dh, Math.sqrt(dh * dh + (p.y - py) * (p.y - py)), i, bearing, escape,
                bx, by, bz, blocked, lava);
        }
        return null;
    }
    private static Vec3d horizontalDir(double px, double pz, BlockPos target) {
        if (target == null) return null;
        double dx = target.getX() - px;
        double dz = target.getZ() - pz;
        double len = Math.hypot(dx, dz);
        if (len < 1.0) return null;
        return new Vec3d(dx / len, 0.0, dz / len);
    }
    private static int lavaNear(World world, int bx, int by, int bz) {
        int count = 0;
        int r = LAVA_SCAN_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (isLava(world, new BlockPos(bx + dx, by + dy, bz + dz))) count++;
                }
            }
        }
        return count;
    }
    private static boolean passable(World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return true;
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
    private static boolean ceilingBlocked(World world, double x, double y, double z) {
        int aboveY = (int) Math.floor(y + 1.8) + 1;
        int baseX = (int) Math.floor(x);
        int baseZ = (int) Math.floor(z);
        double offX = x - baseX;
        double offZ = z - baseZ;
        List<BlockPos> columns = new ArrayList<>(4);
        columns.add(new BlockPos(baseX, aboveY, baseZ));
        if (offX > 0.7) columns.add(new BlockPos(baseX + 1, aboveY, baseZ));
        else if (offX < 0.3) columns.add(new BlockPos(baseX - 1, aboveY, baseZ));
        if (offZ > 0.7) columns.add(new BlockPos(baseX, aboveY, baseZ + 1));
        else if (offZ < 0.3) columns.add(new BlockPos(baseX, aboveY, baseZ - 1));
        for (BlockPos c : columns) {
            for (int i = 0; i < CEILING_CHECK_BLOCKS; i++) {
                BlockPos q = c.up(i);
                if (!world.isChunkLoaded(q.getX() >> 4, q.getZ() >> 4)) continue;
                if (!world.getBlockState(q).getCollisionShape(world, q).isEmpty()) return true;
            }
        }
        return false;
    }
    private static boolean isLava(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        FluidState fluid = world.getFluidState(pos);
        return fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA;
    }
    private Result beginDetour(MinecraftClient mc, Threat t, BlockPos target) {
        double tox = t.x() + 0.5 - mc.player.getX();
        double toz = t.z() + 0.5 - mc.player.getZ();
        double len = Math.max(0.001, Math.hypot(tox, toz));
        double fx = tox / len;
        double fz = toz / len;
        double perpX = -fz;
        double perpZ = fx;
        double sideA = sideScore(mc, perpX, perpZ);
        double sideB = sideScore(mc, -perpX, -perpZ);
        double sign = sideA >= sideB ? 1.0 : -1.0;
        detourX = (int) Math.floor(t.x() + perpX * sign * lateralDistance);
        detourZ = (int) Math.floor(t.z() + perpZ * sign * lateralDistance);
        originalTargetX = target != null ? target.getX() : t.x();
        originalTargetZ = target != null ? target.getZ() : t.z();
        originalTargetValid = true;
        detourTicks = 0;
        detourIssued = BaritoneHook.pathTo(detourX, detourZ);
        if (!detourIssued) {
            cooldown = cooldownTicks;
            lastResult = Result.WATCHING;
            FOElytraLog.warn("岩浆预测：%.1f 秒后要撞上岩浆（%d %d %d），但没有可用的 Baritone 可插绕行航点"
                    + "→ 本 tick 只预警（等它更近时会改走紧急规避）",
                t.etaTicks() / 20.0, t.x(), t.y(), t.z());
            return lastResult;
        }
        avoiding = true;
        detourCount++;
        lastResult = Result.DETOUR;
        FOElytraLog.warn("【实验性】岩浆预测：%.1f 秒后要撞上岩浆（%d %d %d，地平距 %.0f 格）"
                + "→ 侧向绕行到 %d %d（侧偏 %d 格，已交给 Baritone 自己规划）",
            t.etaTicks() / 20.0, t.x(), t.y(), t.z(), t.horizontalDistance(),
            detourX, detourZ, lateralDistance);
        FOElytraLog.detail("岩浆绕行明细：方位 %.0f°｜采样 %d 格岩浆｜绕行侧评分 %.1f vs %.1f｜"
                + "上方通不过 %s｜预测时长 %d tick",
            t.bearingYaw(), t.lavaSamples(), sideA, sideB, t.blockedAbove() ? "是" : "否", horizonTicks);
        return lastResult;
    }
    private double sideScore(MinecraftClient mc, double ux, double uz) {
        World world = mc.world;
        double score = 0;
        for (int s = 4; s <= lateralDistance; s += Math.max(4, lateralDistance / 6)) {
            int bx = (int) Math.floor(mc.player.getX() + ux * s);
            int by = (int) Math.floor(mc.player.getY());
            int bz = (int) Math.floor(mc.player.getZ() + uz * s);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            int lava = lavaNear(world, bx, by, bz);
            score += 1.0 - Math.min(3, lava);
            if (!passable(world, bx, by, bz)) score -= 1.5;
            if (ceilingBlocked(world, bx, by, bz)) score -= 0.5;
        }
        return score;
    }
    private Result tickDetour(MinecraftClient mc, Threat t, BlockPos target) {
        detourTicks++;
        if (handOffIfInLava(mc)) return Result.IDLE;
        boolean passed = t == null;
        boolean nearWaypoint = Math.abs(mc.player.getX() - (detourX + 0.5)) <= returnDistance
            && Math.abs(mc.player.getZ() - (detourZ + 0.5)) <= returnDistance;
        if (passed || nearWaypoint || detourTicks > DETOUR_MAX_TICKS) {
            String why = passed ? "视野内已无岩浆" : (nearWaypoint ? "已到绕行航点附近" : "绕行超时（" + DETOUR_MAX_TICKS + " tick）");
            int backX = target != null ? target.getX() : (originalTargetValid ? originalTargetX : detourX);
            int backZ = target != null ? target.getZ() : (originalTargetValid ? originalTargetZ : detourZ);
            again(backX, backZ, "岩浆绕行结束（" + why + "）→ 回到原目标 " + backX + " " + backZ);
            return lastResult;
        }
        lastResult = Result.DETOUR;
        return lastResult;
    }
    private Result beginEmergency(MinecraftClient mc, Threat t, BlockPos target) {
        originalTargetX = target != null ? target.getX() : t.x();
        originalTargetZ = target != null ? target.getZ() : t.z();
        originalTargetValid = true;
        emergencyTicks = 0;
        refires = 0;
        refireCooldown = REFIRE_TICKS;
        avoiding = true;
        emergencyCount++;
        lastResult = Result.EMERGENCY;
        if (pauseBaritone && !pausedBaritone) {
            BaritoneHook.pause();
            pausedBaritone = true;
        }
        FOElytraLog.warn("【实验性】岩浆预测：只剩 %.1f 秒就要撞上岩浆（%d %d %d，地平距 %.0f 格）"
                + "→ 紧急规避（偏转 %.0f°，%s）",
            t.etaTicks() / 20.0, t.x(), t.y(), t.z(), t.horizontalDistance(), deflectAngle,
            pausedBaritone ? "已暂停 Baritone" : "没暂停 Baritone（按设置）");
        applyEmergencyYaw(mc, t.escapeYaw());
        steerEmergency(mc, t);
        return lastResult;
    }
    private Result tickEmergency(MinecraftClient mc, Threat t, BlockPos target) {
        emergencyTicks++;
        if (handOffIfInLava(mc)) return Result.IDLE;
        boolean safe = t == null;
        boolean timeout = emergencyTicks > EMERGENCY_MAX_TICKS;
        if (safe || timeout) {
            String why = safe ? "视野内已无岩浆" : "紧急规避超时（" + EMERGENCY_MAX_TICKS + " tick）";
            int backX = target != null ? target.getX() : (originalTargetValid ? originalTargetX : mc.player.getBlockX());
            int backZ = target != null ? target.getZ() : (originalTargetValid ? originalTargetZ : mc.player.getBlockZ());
            again(backX, backZ, "岩浆紧急规避结束（" + why + "）→ 回到原目标 " + backX + " " + backZ);
            return lastResult;
        }
        applyEmergencyYaw(mc, t.escapeYaw());
        steerEmergency(mc, t);
        return lastResult;
    }
    private boolean handOffIfInLava(MinecraftClient mc) {
        if (!mc.player.isInLava()) return false;
        releaseAvoidKeys();
        if (pausedBaritone) {
            BaritoneHook.resume();
            pausedBaritone = false;
        }
        avoiding = false;
        cooldown = 0;
        lastResult = Result.IDLE;
        FOElytraLog.warn("岩浆预测：已进入岩浆 → 规避交给「逃离岩浆」（事后自救），本类不再接管");
        return true;
    }
    private void applyEmergencyYaw(MinecraftClient mc, float escapeYaw) {
        float left = wrap(escapeYaw + deflectAngle * 0.5f);
        float right = wrap(escapeYaw - deflectAngle * 0.5f);
        float chosen = sideScore(mc, yawDirX(left), yawDirZ(left)) >= sideScore(mc, yawDirX(right), yawDirZ(right))
            ? left : right;
        mc.player.setYaw(chosen);
        boolean blockedAbove = ceilingBlocked(mc.world, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float pitch = blockedAbove ? EMERGENCY_PITCH_BLOCKED : EMERGENCY_PITCH_MAX;
        mc.player.setPitch(Math.max(EMERGENCY_PITCH_MIN, Math.min(EMERGENCY_PITCH_MAX, pitch)));
    }
    private void steerEmergency(MinecraftClient mc, Threat t) {
        PlayerAction.pressForward(true);
        if (mc.player.isInLava() || mc.player.isTouchingWater()) PlayerAction.pressJump(true);
        if (--refireCooldown <= 0) {
            refireCooldown = REFIRE_TICKS;
            if (mc.player.isGliding() && refires < REFIRE_MAX) {
                refires++;
                int slot = fireworkSlot(mc);
                if (slot >= 0) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    if (mc.getNetworkHandler() != null) {
                        try {
                            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                        } catch (Throwable ignored) {
                        }
                    }
                    InvHelper.useItem(Hand.MAIN_HAND);
                    FOElytraLog.detail("岩浆紧急规避：补射一发烟花（快捷栏第 %d 格，第 %d/%d 发）",
                        slot + 1, refires, REFIRE_MAX);
                }
            }
        }
    }
    private void again(int targetX, int targetZ, String message) {
        releaseAvoidKeys();
        if (pausedBaritone) {
            BaritoneHook.resume();
            pausedBaritone = false;
        }
        avoiding = false;
        cooldown = cooldownTicks;
        lastResult = Result.IDLE;
        FOElytraLog.info("%s", message);
        BaritoneHook.pathTo(targetX, targetZ);
    }
    public void reset() {
        releaseAvoidKeys();
        if (pausedBaritone) {
            BaritoneHook.resume();
            pausedBaritone = false;
        }
        avoiding = false;
        threat = null;
        lastResult = Result.IDLE;
        cooldown = 0;
        warnCooldown = 0;
       emergencyTicks = 0;
        detourTicks = 0;
        detourIssued = false;
        refires = 0;
        refireCooldown = 0;
        originalTargetValid = false;
    }
    public void release(MinecraftClient mc) {
        reset();
    }
    private void releaseAvoidKeys() {
        PlayerAction.pressForward(false);
        PlayerAction.pressJump(false);
    }
    private static int fireworkSlot(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.FIREWORK_ROCKET)) return i;
        }
        return -1;
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
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"启用岩浆预测规避",
            "实验性：沿当前飞行方向预测 1.5~4 秒，提前绕开岩浆柱/岩浆湖。"
                + "预测可能误判（改成爬升、绕山、临时改航点都会让它算错），误判的表现是「多绕一点路」，"
                + "不会掉血、不会登出；想先观察就先只用「只预警不接管」。", "关", "开/关"});
        out.add(new String[]{"预测时长（秒）",
            "往前预测多少秒的飞行轨迹。太短来不及绕，太长容易把远处的岩浆湖也当成威胁。", "3.0", "1.5 ~ 4.0"});
        out.add(new String[]{"紧急阈值（秒）",
            "预计在这个时间内撞上岩浆就改用「紧急规避」（暂停 Baritone + 偏转视角 + 放烟花）；"
                + "更早发现的威胁走「侧向绕行」（插一个航点交给 Baritone 自己规划）。", "1.2", "0.4 ~ 2.0"});
        out.add(new String[]{"侧向绕行距离",
            "绕行航点离危险点往侧面偏多少格。越大越安全、也越绕路。", "45", "16 ~ 96"});
        out.add(new String[]{"绕行结束距离",
            "离绕行航点多近就算绕过这一段（也可以靠「前方预测变干净」提前结束）。", "25", "8 ~ 64"});
        out.add(new String[]{"规避防抖 tick",
            "两次规避动作之间至少间隔多少 tick，防止在岩浆边缘反复触发。", "40", "10 ~ 200"});
        out.add(new String[]{"只预警不接管",
            "只把预测结果写进日志/聊天栏，不改 Baritone 航点、不碰按键（用来先验证预测准不准）。", "关", "开/关"});
        out.add(new String[]{"紧急时暂停 Baritone",
            "紧急规避期间暂停 Baritone（p），脱离后恢复（r）——不暂停的话它会和我们抢视角。", "开", "开/关"});
        out.add(new String[]{"紧急偏转角（度）",
            "紧急规避时相对「岩浆反方向」再左右偏多少度，用来在两侧里挑一条更干净的出路。", "75", "30 ~ 90"});
        return out;
    }
}
