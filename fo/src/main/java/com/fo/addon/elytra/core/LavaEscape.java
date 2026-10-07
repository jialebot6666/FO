package com.fo.addon.elytra.core;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
public final class LavaEscape {
    public enum Result {
        IDLE("空闲"),
        ESCAPING("脱离中"),
        FAILED("失败");

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
    private static final int FIRE_TRIGGER = 20;
    private static final int NOT_GLIDING_TRIGGER = 5;
    private static final int ESCAPE_COOLDOWN = 45;
    private static final int CEILING_CHECK_BLOCKS = 4;
    private static final float HORIZONTAL_PITCH_LIMIT = -35.0f;
    private static final float HORIZONTAL_PITCH_MAX = 20.0f;
    private static final int DIRECTION_SAMPLES = 16;
    private static final int CLEARANCE_STEPS = 6;
    private static final int HORIZONTAL_REFIRE_TICKS = 20;
    private static final int HORIZONTAL_REFIRE_MAX = 3;
    private static final float FAILED_RESTORE_PITCH = 0.0f;
    private final int fireTrigger;
    private final int notGlidingTrigger;
    private final int cooldownTicks;
    private final int maxRetries;
    private final boolean ignoreGlidingFire;
    private final double liftPitch;
    private final boolean useFirework;
    private final boolean swimToSafety;
    private final int searchRadius;
    private final boolean drinkFireRes;
    private int inFireTick;
    private int retries;
    private int jumpPhase = -1;
    private int phaseTicks;
    private boolean announced;
    private boolean glidingBurnHinted;
    private boolean loggedMissingFirework;
    private boolean loggedNoDirection;
    private String lastReason = "";
    private boolean justFinished;
    private boolean viewRestoreSignaled;
    private boolean failedNoFirework;
    private boolean horizontalMode;
    private int horizontalRefires;
    private int refireCooldown;
   private BlockPos exitTarget;
    private int targetRefresh;
    private int noExitWarned;
    private int drinkLeft;
    private double triggerX;
    private double triggerY;
    private double triggerZ;
    private boolean triggerPosValid;
    public LavaEscape(int fireTrigger, int notGlidingTrigger, boolean ignoreGlidingFire, int cooldownTicks,
                      int maxRetries, double liftPitch, boolean useFirework,
                      boolean swimToSafety, int searchRadius, boolean drinkFireRes) {
        this.fireTrigger = Math.max(0, fireTrigger);
        this.notGlidingTrigger = Math.max(0, notGlidingTrigger);
        this.ignoreGlidingFire = ignoreGlidingFire;
        this.cooldownTicks = Math.max(5, cooldownTicks);
        this.maxRetries = Math.max(1, maxRetries);
        this.liftPitch = liftPitch;
        this.useFirework = useFirework;
        this.swimToSafety = swimToSafety;
        this.searchRadius = Math.max(3, Math.min(24, searchRadius));
        this.drinkFireRes = drinkFireRes;
    }
    public boolean isEscaping() {
        return inFireTick < 0;
    }
    public int retries() {
        return retries;
    }
    public String lastReason() {
        return lastReason;
    }
    public boolean failedNoFirework() {
        return failedNoFirework;
    }
    public boolean consumeJustFinished() {
        boolean v = justFinished;
        justFinished = false;
        return v;
    }
    public void reset() {
        releaseKeys();
        inFireTick = 0;
        retries = 0;
        jumpPhase = -1;
        phaseTicks = 0;
        announced = false;
        glidingBurnHinted = false;
        loggedMissingFirework = false;
        loggedNoDirection = false;
        lastReason = "";
        justFinished = false;
        viewRestoreSignaled = false;
        failedNoFirework = false;
        horizontalMode = false;
        horizontalRefires = 0;
        refireCooldown = 0;
       exitTarget = null;
        targetRefresh = 0;
        noExitWarned = 0;
        drinkLeft = 0;
        triggerPosValid = false;
    }
    public void release(MinecraftClient mc) {
        reset();
    }
    private void releaseKeys() {
        PlayerAction.pressJump(false);
        PlayerAction.pressForward(false);
        PlayerAction.pressUse(false);
    }
    public Result tick(boolean enabled) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return Result.IDLE;
        if (!enabled) {
            reset();
            return Result.IDLE;
        }
        boolean inLava = mc.player.isInLava();
        boolean onFire = mc.player.isOnFire();
        boolean gliding = mc.player.isGliding();
        if (inLava && gliding && ignoreGlidingFire) {
            if (!glidingBurnHinted) {
                glidingBurnHinted = true;
                FOElytraLog.debug("滑翔中泡在岩浆里，但按设置「滑翔时忽略」不打断，继续飞");
            }
            if (jumpPhase >= 0) stepJumpSequence(mc);
            return Result.IDLE;
        }
        glidingBurnHinted = false;
        if (inFireTick >= 0) {
            inFireTick = inLava ? inFireTick + 1 : 0;
        } else {
            inFireTick++;
        }
        if (inFireTick == -1) {
            inFireTick = 0;
            if (inLava) return escapeFailed(mc);
            if (onFire) {
                FOElytraLog.debug("已离开岩浆（身上还有火：原版烫一下会着火 8 秒）");
            }
            if (retries > 0 || announced) announceEscaped();
            signalViewRestore();
            return Result.IDLE;
        }
        if (jumpPhase >= 0) stepJumpSequence(mc);
        if (inFireTick > fireTrigger || (inFireTick > notGlidingTrigger && !gliding)) {
            int lavaTicks = inFireTick;
            boolean blocked = ceilingBlocked(mc);
            inFireTick = -cooldownTicks;
            FOElytraLog.detail("触发岩浆自救：岩浆内 %s｜着火 %s｜滑翔 %s｜连续在岩浆里 %d tick"
                    + "（滑翔阈值 %d，非滑翔阈值 %d）｜头顶被挡 %s｜位置 %d %d %d",
                inLava ? "是" : "否", onFire ? "是" : "否", gliding ? "是" : "否", lavaTicks,
                fireTrigger, notGlidingTrigger, blocked ? "是" : "否",
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            return trigger(mc, inLava, onFire, gliding, blocked);
        }
        if (inFireTick < 0) {
            if (drinkFireRes && !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) startDrink(mc);
            if (drinkLeft > 0) drinkTick(mc);
            if (drinkLeft <= 0 && (inLava || onFire) && (ceilingBlocked(mc) || swimToSafety)) {
                driveHorizontal(mc, inLava, onFire);
            } else if (inLava || onFire) {
                boolean wasHorizontal = horizontalMode;
                stopHorizontal();
                if (wasHorizontal) mc.player.setPitch((float) liftPitch);
            } else {
                stopHorizontal();
                if (!viewRestoreSignaled) mc.player.setPitch(FAILED_RESTORE_PITCH);
                signalViewRestore();
            }
            return Result.ESCAPING;
        }
        return Result.IDLE;
    }
    private void announceEscaped() {
        retries = 0;
        announced = false;
        justFinished = true;
        FOElytraLog.tip("已脱离岩浆，恢复正常跑图");
    }
    private void signalViewRestore() {
        if (viewRestoreSignaled) return;
        viewRestoreSignaled = true;
        justFinished = true;
    }
    private Result trigger(MinecraftClient mc, boolean inLava, boolean onFire, boolean gliding, boolean blockedAbove) {
        jumpPhase = (mc.player.isOnGround() ? 0 : 2);
        phaseTicks = (jumpPhase == 0) ? 4 : 3;
        PlayerAction.pressJump(true);
        FOElytraLog.warn("位于岩浆中，已鞘翅打开");
        triggerPosValid = true;
        triggerX = mc.player.getX();
        triggerY = mc.player.getY();
        triggerZ = mc.player.getZ();
        horizontalRefires = 0;
        refireCooldown = HORIZONTAL_REFIRE_TICKS;
        loggedNoDirection = false;
        viewRestoreSignaled = false;
        if (blockedAbove || swimToSafety) {
            FOElytraLog.warn("头顶%s：不再笔直上冲（笔直朝上会顶着天花板烧），改朝可飞方向水平脱离"
                    + "（抬头角收到 %.0f° 以内 + 前进 + 流体内按住跳）",
                blockedAbove ? "被方块挡住" : "空着但设置了「兜底：游向安全点」", HORIZONTAL_PITCH_LIMIT);
            driveHorizontal(mc, inLava, onFire);
        } else {
            mc.player.setPitch((float) liftPitch);
        }
        if (useFirework) {
            Result r = useFireworkForEscape(mc, true);
            if (r != Result.ESCAPING) return r;
        }
        if (!announced) {
            announced = true;
            lastReason = gliding ? "滑翔中泡在岩浆里" : "没在滑翔且泡在岩浆里";
        }
        return Result.ESCAPING;
    }
    private Result useFireworkForEscape(MinecraftClient mc, boolean firstShot) {
        int slot = findFireworkSlot(mc);
        if (slot < 0) {
            FOElytraLog.err("找不到烟花（快捷栏 1~8 格全被占满）");
            lastReason = "找不到烟花（快捷栏 1~8 全被占满）";
            failedNoFirework = true;
            releaseKeys();
            return Result.FAILED;
        }
        boolean realFirework = mc.player.getInventory().getStack(slot).isOf(Items.FIREWORK_ROCKET);
        selectAndUse(mc, slot);
        if (realFirework) {
            if (firstShot) {
                FOElytraLog.info("已使用烟花！（快捷栏第 %d 格）", slot + 1);
            } else {
                FOElytraLog.detail("水平脱离：补射一发烟花（快捷栏第 %d 格，第 %d/%d 发）",
                    slot + 1, horizontalRefires, HORIZONTAL_REFIRE_MAX);
            }
            return Result.ESCAPING;
        }
        if (!loggedMissingFirework) {
            loggedMissingFirework = true;
            String extra = hasFirework(mc.player)
                ? "（背包里有烟花，但不在快捷栏 1~9 格；按原有槽位逻辑选了第 " + (slot + 1) + " 格）"
                : "（整个快捷栏都没有烟花，「放烟花上升」这次没有推力）";
            FOElytraLog.warn("注意：选中的第 %d 格不是烟花，右键没有产生推力%s", slot + 1, extra);
        }
        return Result.ESCAPING;
    }
    private static boolean hasFirework(PlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return true;
        }
        return false;
    }
    private int findFireworkSlot(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return i;
        }
        int slot = -1;
        for (int i = 0; i < 8; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || s.isOf(Items.FIREWORK_ROCKET)) slot = i;
        }
        return slot;
    }
    private void selectAndUse(MinecraftClient mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            try {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            } catch (Throwable ignored) {
            }
        }
        InvHelper.useItem(Hand.MAIN_HAND);
    }
    private void refireFirework(MinecraftClient mc) {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;
        selectAndUse(mc, slot);
        FOElytraLog.detail("水平脱离：补射一发烟花（快捷栏第 %d 格，第 %d/%d 发）",
            slot + 1, horizontalRefires, HORIZONTAL_REFIRE_MAX);
    }
    private Result escapeFailed(MinecraftClient mc) {
        retries++;
        releaseKeys();
        stopHorizontal();
        signalViewRestore();
        String progress = triggerPosValid
            ? String.format("位移 水平 %.1f 格 / 垂直 %+.1f 格",
                Math.hypot(mc.player.getX() - triggerX, mc.player.getZ() - triggerZ), mc.player.getY() - triggerY)
            : "位移（无触发点快照）";
        if (retries < maxRetries) {
            FOElytraLog.warn("逃离岩浆失败！（第 %d/%d 次，%s）仍然在岩浆里，继续下一次自救",
                retries, maxRetries, lastReason.isEmpty() ? "原因未知" : lastReason);
            FOElytraLog.detail("逃离岩浆尝试结束：%s｜在岩浆 %s｜位置 %d %d %d｜头顶被挡 %s",
                progress, mc.player.isInLava() ? "是" : "否",
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                ceilingBlocked(mc) ? "是" : "否");
            return Result.IDLE;
        }
        FOElytraLog.err("逃离岩浆失败！连续 %d 次自救都没能脱险（%s）"
                + "（在这一点上是直接结束任务的；是否登出由模块的「安全 → 失败自动登出」决定，"
                + "本类从不登出）",
            retries, lastReason.isEmpty() ? "原因未知" : lastReason);
        FOElytraLog.detail("逃离岩浆最终失败：%s｜在岩浆 %s｜位置 %d %d %d｜烟花 %d 发",
            progress, mc.player.isInLava() ? "是" : "否",
            mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), countFireworks(mc.player));
        if (mc.player != null) mc.player.setPitch(FAILED_RESTORE_PITCH);
        lastReason = lastReason.isEmpty() ? "连续自救都没能脱离岩浆" : lastReason;
        return Result.FAILED;
    }
    private static int countFireworks(PlayerEntity player) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(Items.FIREWORK_ROCKET)) n += s.getCount();
        }
        return n;
    }
    private void stepJumpSequence(MinecraftClient mc) {
        switch (jumpPhase) {
            case 0 -> {
                PlayerAction.pressJump(true);
                if (--phaseTicks <= 0) { jumpPhase = 1; phaseTicks = 1; }
            }
            case 1 -> {
                PlayerAction.pressJump(false);
                if (--phaseTicks <= 0) { jumpPhase = 2; phaseTicks = 3; }
            }
            case 2 -> {
                PlayerAction.pressJump(true);
                if (--phaseTicks <= 0) { jumpPhase = 3; phaseTicks = 1; }
            }
            default -> {
                PlayerAction.pressJump(false);
                jumpPhase = -1;
            }
        }
    }
    private boolean ceilingBlocked(MinecraftClient mc) {
        World world = mc.world;
        if (world == null || mc.player == null) return false;
        PlayerEntity p = mc.player;
        double headY = p.getY() + p.getHeight();
        int aboveY = (int) Math.floor(headY) + 1;
        int baseX = (int) Math.floor(p.getX());
        int baseZ = (int) Math.floor(p.getZ());
        double offX = p.getX() - baseX;
        double offZ = p.getZ() - baseZ;
        Set<BlockPos> columns = new HashSet<>();
        columns.add(new BlockPos(baseX, aboveY, baseZ));
        if (offX > 0.7) columns.add(new BlockPos(baseX + 1, aboveY, baseZ));
        else if (offX < 0.3) columns.add(new BlockPos(baseX - 1, aboveY, baseZ));
        if (offZ > 0.7) columns.add(new BlockPos(baseX, aboveY, baseZ + 1));
        else if (offZ < 0.3) columns.add(new BlockPos(baseX, aboveY, baseZ - 1));
        if (offX > 0.7 && offZ > 0.7) columns.add(new BlockPos(baseX + 1, aboveY, baseZ + 1));
        else if (offX > 0.7 && offZ < 0.3) columns.add(new BlockPos(baseX + 1, aboveY, baseZ - 1));
        else if (offX < 0.3 && offZ > 0.7) columns.add(new BlockPos(baseX - 1, aboveY, baseZ + 1));
        else if (offX < 0.3 && offZ < 0.3) columns.add(new BlockPos(baseX - 1, aboveY, baseZ - 1));
        for (BlockPos c : columns) {
            for (int i = 0; i < CEILING_CHECK_BLOCKS; i++) {
                BlockPos q = c.up(i);
                if (!world.isChunkLoaded(q.getX() >> 4, q.getZ() >> 4)) continue;
                if (solid(world, q)) return true;
            }
        }
        return false;
    }
    private void driveHorizontal(MinecraftClient mc, boolean inLava, boolean onFire) {
        if (!horizontalMode) {
            horizontalMode = true;
            FOElytraLog.detail("岩浆自救：进入水平脱离模式（不再只往上冲）");
        }
        boolean haveTarget = false;
        double dx = 0;
        double dz = 0;
        double dy = 0;
        String how = "";
        if (swimToSafety) {
            if (exitTarget == null || --targetRefresh <= 0 || !isSafe(mc.world, exitTarget)) {
                exitTarget = findExit(mc, searchRadius);
                targetRefresh = 20;
            }
            if (exitTarget != null) {
                dx = exitTarget.getX() + 0.5 - mc.player.getX();
                dz = exitTarget.getZ() + 0.5 - mc.player.getZ();
                dy = exitTarget.getY() - mc.player.getY();
                haveTarget = true;
                how = "安全点 " + exitTarget.toShortString();
            } else if (noExitWarned++ == 0) {
                FOElytraLog.warn("岩浆自救：半径 %d 格内没有落脚点（可把「兜底：安全点搜索半径」调大），改用「能飞出去的方向」",
                    searchRadius);
            }
        }
        if (!haveTarget) {
            float yaw = pickFreeYaw(mc);
            if (!Float.isNaN(yaw)) {
                double rad = Math.toRadians(yaw);
                dx = -Math.sin(rad);
                dz = Math.cos(rad);
                dy = 0.0;
                haveTarget = true;
                how = String.format("可飞方向 yaw %.0f°", yaw);
            }
        }
        if (!haveTarget) {
            if (!loggedNoDirection) {
                loggedNoDirection = true;
                FOElytraLog.warn("岩浆自救：16 个水平方向全都飞不出去（四周都被堵死）→ 退回默认抬头姿态");
            }
            stopHorizontal();
            mc.player.setPitch((float) liftPitch);
            return;
        }
        double flat = Math.max(0.001, Math.hypot(dx, dz));
        float yaw = (float) ((Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0);
        float pitch = (float) Math.max(HORIZONTAL_PITCH_LIMIT,
            Math.min(HORIZONTAL_PITCH_MAX, -Math.toDegrees(Math.atan2(dy, flat))));
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        if (jumpPhase < 0) {
            PlayerAction.pressForward(true);
            if (inLava) PlayerAction.pressJump(true);
        }
        if (useFirework && mc.player.isGliding() && (inLava || onFire) && jumpPhase < 0) {
            if (--refireCooldown <= 0) {
                refireCooldown = HORIZONTAL_REFIRE_TICKS;
                if (horizontalRefires < HORIZONTAL_REFIRE_MAX) {
                    horizontalRefires++;
                    refireFirework(mc);
                }
            }
        }
    }
    private void stopHorizontal() {
        if (horizontalMode) {
            horizontalMode = false;
            PlayerAction.pressForward(false);
            FOElytraLog.detail("岩浆自救：退出水平脱离模式（恢复默认抬头姿态）");
        }
    }
    private float pickFreeYaw(MinecraftClient mc) {
        World world = mc.world;
        if (world == null || mc.player == null) return Float.NaN;
        BlockPos origin = mc.player.getBlockPos();
        float currentYaw = mc.player.getYaw();
        float bestYaw = Float.NaN;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < DIRECTION_SAMPLES; k++) {
            float yaw = k * (360.0f / DIRECTION_SAMPLES);
            double rad = Math.toRadians(yaw);
            double stepX = -Math.sin(rad);
            double stepZ = Math.cos(rad);
            int free = 0;
            int lavaSteps = 0;
            for (int s = 1; s <= CLEARANCE_STEPS; s++) {
                int bx = (int) Math.floor(mc.player.getX() + stepX * s);
                int bz = (int) Math.floor(mc.player.getZ() + stepZ * s);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) break;
                BlockPos feet = new BlockPos(bx, origin.getY(), bz);
                BlockPos head = feet.up();
                if (solid(world, feet) || solid(world, head)) break;
                free++;
                if (isLava(world, feet)) lavaSteps++;
            }
            if (free <= 0) continue;
            double angleDiff = Math.abs(((yaw - currentYaw + 540.0f) % 360.0f) - 180.0f);
            double score = free * 2.0 - lavaSteps * 1.0 - angleDiff * 0.02;
            if (score > bestScore) {
                bestScore = score;
                bestYaw = yaw;
            }
        }
        return bestYaw;
    }
    private boolean startDrink(MinecraftClient mc) {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (isFireResPotion(mc.player.getInventory().getStack(i))) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return false;
        mc.player.getInventory().setSelectedSlot(slot);
        drinkLeft = 45;
        PlayerAction.pressUse(true);
        FOElytraLog.warn("岩浆自救：先喝抗火药水（快捷栏第 %d 格）", slot + 1);
        return true;
    }
    private void drinkTick(MinecraftClient mc) {
        drinkLeft--;
        PlayerAction.pressUse(drinkLeft > 0);
        if (drinkLeft <= 0 && mc.player != null && !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            FOElytraLog.warn("抗火药水没喝上（可能被别的模块抢了右键）");
        }
    }
    private static boolean isFireResPotion(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (!s.isOf(Items.POTION) && !s.isOf(Items.SPLASH_POTION) && !s.isOf(Items.LINGERING_POTION)) {
            return false;
        }
        PotionContentsComponent contents = s.get(DataComponentTypes.POTION_CONTENTS);
        return contents != null
            && (contents.matches(Potions.FIRE_RESISTANCE) || contents.matches(Potions.LONG_FIRE_RESISTANCE));
    }
    private BlockPos findExit(MinecraftClient mc, int radius) {
        World world = mc.world;
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -3; dy <= 4; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    double score = dx * dx + dz * dz + dy * dy * 6.0;
                    if (score >= bestScore) continue;
                    BlockPos q = origin.add(dx, dy, dz);
                    if (!isSafe(world, q)) continue;
                    bestScore = score;
                    best = q;
                }
            }
        }
        return best;
    }
    private boolean isSafe(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        if (isLava(world, pos) || isLava(world, pos.up())) return false;
        if (solid(world, pos) || solid(world, pos.up())) return false;
        return solid(world, pos.down()) && !isLava(world, pos.down());
    }
    private static boolean isLava(World world, BlockPos pos) {
        FluidState fluid = world.getFluidState(pos);
        return fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA;
    }
    private static boolean solid(World world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
    public static int fireTrigger() {
        return FIRE_TRIGGER;
    }
    public static int notGlidingTrigger() {
        return NOT_GLIDING_TRIGGER;
    }
    public static int escapeCooldown() {
        return ESCAPE_COOLDOWN;
    }
}
