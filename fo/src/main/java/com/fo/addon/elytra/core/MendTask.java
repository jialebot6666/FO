package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.BlockBreaker;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import com.fo.addon.elytra.core.TaskStatus;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public final class MendTask {
    private static final int PITCH_READY = 85;
    private static final int PITCH_SETTLE_TICKS = 11;
    private static final int AIR_WAIT_MAX = 100;
    private static final int BATCH_THROWS = 10;
    private static final int GROUND_CALM_TICKS = 20;
    private static final int LANDING_SEARCH_RADIUS = 24;
    private final Options opts;
    private State state = State.IDLE;
    private TaskStatus status = TaskStatus.IDLE;
    private int delay;
    private int waitTicks;
    private int throwsDone;
    private String failReason = "";
    private String lastMessage = "";
    private int xpSlot = -1;
    private int previousSlot = -1;
    private float previousPitch;
    private boolean landingRequested;
    private int batchStartRemaining;
    private int mendStartRemaining;
    private boolean intervalWarned;
    private int airWaitTicks;
    private boolean airWarned;
    private int groundCalmTicks;
    private boolean waitFlyingLogged;
    private BlockPos landingSpot;

    public MendTask(Options opts) {
        this.opts = opts;
    }

    public void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            this.fail("\u73a9\u5bb6/\u4e16\u754c\u4e3a\u7a7a");
            return;
        }
        ItemStack elytra = ItemHelper.wornElytra((PlayerEntity)mc.player);
        if (elytra.isEmpty()) {
            this.fail("\u6ca1\u6709\u7a7f\u9798\u7fc5");
            return;
        }
        int remainingNow = ItemHelper.remainingDurability(elytra);
        if (remainingNow > this.opts.triggerDurability()) {
            this.fail("\u9798\u7fc5\u5269\u4f59\u8010\u4e45 " + remainingNow + " \u8fd8\u9ad8\u4e8e\u89e6\u53d1\u9608\u503c " + this.opts.triggerDurability() + "\uff0c\u4e0d\u9700\u8981\u4fee\u590d");
            return;
        }
        if (this.opts.requireMending() && !ItemHelper.isMending(elytra, 1)) {
            this.fail("\u9798\u7fc5\u6ca1\u6709\u300c\u7ecf\u9a8c\u4fee\u8865\u300d\u9644\u9b54\uff0c\u6254\u7ecf\u9a8c\u74f6\u4fee\u4e0d\u4e86\u8010\u4e45");
            return;
        }
        if (this.opts.requireNetherWastes() && !this.isNetherWastes(mc)) {
            this.fail("\u5f53\u524d\u4e0d\u5728\u4e0b\u754c\u8352\u5730\u751f\u7269\u7fa4\u7cfb\uff08\u8bbe\u7f6e\u8981\u6c42\u5728\u8fd9\u91cc\u4fee\uff09");
            return;
        }
        int bottles = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.EXPERIENCE_BOTTLE);
        if (bottles < Math.max(1, this.opts.minBottles())) {
            this.fail("\u7ecf\u9a8c\u74f6\u4e0d\u8db3\uff08\u9700\u8981 " + Math.max(1, this.opts.minBottles()) + " \u4e2a\uff0c\u73b0\u6709 " + bottles + " \u4e2a\uff09");
            return;
        }
        this.state = State.ENSURE_GROUND;
        this.status = TaskStatus.RUNNING;
        this.delay = 0;
        this.waitTicks = 0;
        this.throwsDone = 0;
        this.failReason = "";
        this.landingRequested = false;
        this.previousSlot = mc.player.getInventory().getSelectedSlot();
        this.previousPitch = mc.player.getPitch();
        this.intervalWarned = false;
        this.airWaitTicks = 0;
        this.airWarned = false;
        this.mendStartRemaining = remainingNow;
        this.batchStartRemaining = remainingNow;
        BlockBreaker.reset();
        FOElytraLog.info("\u5f00\u59cb\u4fee\u590d\u9798\u7fc5\uff08\u5269\u4f59\u8010\u4e45 %d\uff0c\u74f6\u5b50 %d \u4e2a\uff09", ItemHelper.remainingDurability(elytra), bottles);
        FOElytraLog.detail("\u4fee\u590d\u53c2\u6570\uff1a\u89e6\u53d1\u9608\u503c %d\uff5c\u4f4e\u8010\u4e45\u8b66\u544a\u7ebf %d\uff5c\u4fee\u5230\u635f\u4f24 \u2264 %d\uff5c\u843d\u5730 %s\uff5c\u751f\u7269\u7fa4\u7cfb\u9650\u5236 %s\uff5c\u7ecf\u9a8c\u4fee\u8865\u5fc5\u9700 %s\uff5c\u4fef\u4ef0 %.0f\u00b0\uff5c\u95f4\u9694 %d tick\uff5c\u6700\u591a %d \u74f6\uff5c\u843d\u5730\u8d85\u65f6 %d tick", this.opts.triggerDurability(), this.opts.minDurability(), this.opts.repairToDamage(), this.opts.requireGround() ? "\u662f" : "\u5426", this.opts.requireNetherWastes() ? "\u4e0b\u754c\u8352\u5730" : "\u4e0d\u9650", this.opts.requireMending() ? "\u662f" : "\u5426", this.opts.lookPitch(), this.opts.throwDelay(), this.opts.maxThrows(), this.opts.landingTimeoutTicks());
        int remainingStart = ItemHelper.remainingDurability(elytra);
        if (remainingStart <= this.opts.minDurability()) {
            FOElytraLog.warn("\u9798\u7fc5\u5269\u4f59\u8010\u4e45\u53ea\u5269 %d\uff08\u8b66\u544a\u7ebf %d\uff09\uff1a\u4fee\u7684\u65f6\u5019\u522b\u65ad\u7ebf\uff0c\u5e76\u4e14\u628a\u300c\u8865\u7ed9\u6570\u91cf \u2192 \u76ee\u6807\u5907\u7528\u9798\u7fc5\u300d\u8bbe\u6210 1~2 \u7ec4\uff0c\u968f\u65f6\u80fd\u6362", remainingStart, this.opts.minDurability());
        }
    }

    public void abort(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (this.state != State.IDLE) {
            FOElytraLog.warn("\u4fee\u9798\u7fc5\u4e2d\u6b62\uff1a%s", reason);
        }
        if (mc.player != null && this.state != State.IDLE) {
            mc.player.setPitch(this.previousPitch);
            if (this.previousSlot >= 0) {
                InvHelper.selectSlot(this.previousSlot);
            }
        }
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.state = State.IDLE;
        this.status = TaskStatus.IDLE;
        this.delay = 0;
    }

    public TaskStatus status() {
        return this.status;
    }

    public State state() {
        return this.state;
    }

    public boolean isRunning() {
        return this.status == TaskStatus.RUNNING;
    }

    public String failReason() {
        return this.failReason;
    }

    public String lastMessage() {
        return this.lastMessage;
    }

    public String progress() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return this.state.name();
        }
        return this.state.name() + " \u8010\u4e45 " + ItemHelper.remainingDurability(ItemHelper.wornElytra((PlayerEntity)mc.player));
    }

    public void tick() {
        if (this.status != TaskStatus.RUNNING) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            this.fail("\u73a9\u5bb6/\u4e16\u754c\u4e3a\u7a7a");
            return;
        }
        if (this.delay > 0) {
            --this.delay;
            return;
        }
        try {
            this.step(mc);
        }
        catch (Throwable t) {
            FOElytraLog.err("\u4fee\u9798\u7fc5\u5185\u90e8\u5f02\u5e38: %s", String.valueOf(t));
            this.fail("\u5185\u90e8\u5f02\u5e38 " + t.getClass().getSimpleName());
        }
    }

    private void step(MinecraftClient mc) {
        switch (this.state.ordinal()) {
            case 1: {
                this.ensureGround(mc);
                break;
            }
            case 2: {
                this.prepare(mc);
                break;
            }
            case 3: {
                this.throwBottles(mc);
                break;
            }
            case 4: {
                this.restore(mc);
                break;
            }
            case 5: {
                this.status = TaskStatus.DONE;
                break;
            }
            case 6: {
                this.status = TaskStatus.FAILED;
                break;
            }
            default: {
                this.status = TaskStatus.DONE;
            }
        }
    }

    private void ensureGround(MinecraftClient mc) {
        if (mc.player.isOnGround() && !mc.player.isGliding() && !BaritoneHook.isFlying()) {
            if (++this.groundCalmTicks < 20) {
                this.delay = 1;
                return;
            }
            String unsafe = this.unsafeReason(mc);
            if (!unsafe.isEmpty()) {
                this.fail("\u964d\u843d\u5730\u4e0d\u5b89\u5168\uff08" + unsafe + "\uff09\uff0c\u6362\u4e2a\u5730\u65b9\u518d\u4fee");
                return;
            }
            BaritoneHook.stop();
            this.next(State.PREPARE, 2);
            return;
        }
        this.groundCalmTicks = 0;
        if (!this.opts.requireGround()) {
            this.next(State.PREPARE, 1);
            return;
        }
        if (BaritoneHook.isFlying()) {
            if (!this.waitFlyingLogged) {
                this.waitFlyingLogged = true;
                FOElytraLog.detail("Baritone \u9798\u7fc5\u8fd8\u6ca1\u505c\uff08isActive\uff09\uff0c\u7b49\u5b83\u9000\u51fa\u518d\u6254\u74f6\u5b50", new Object[0]);
            }
            BaritoneHook.stop();
        }
        if (!this.landingRequested) {
            this.landingRequested = true;
            this.landingSpot = this.findSafeLandingSpot(mc);
            if (this.landingSpot == null) {
                this.fail("\u9644\u8fd1 24 \u683c\u5185\u627e\u4e0d\u5230\u80fd\u843d\u5730\u7684\u4f4d\u7f6e\uff08\u811a\u4e0b\u662f\u5ca9\u6d46/\u5ca9\u6d46\u5757/\u6c34\uff0c\u6216\u8005\u8fd9\u91cc\u662f\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\uff09");
                return;
            }
            if (BaritoneHook.available()) {
                BaritoneHook.pathTo(this.landingSpot.getX(), this.landingSpot.getZ());
                FOElytraLog.info("\u4fee\u9798\u7fc5\uff1a\u5148\u843d\u5230\u5b89\u5168\u70b9 %d %d %d\uff08\u9646\u5730\uff0c\u811a\u4e0b\u4e0d\u662f\u5ca9\u6d46/\u5ca9\u6d46\u5757\uff0c\u4e0d\u662f\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\uff09", this.landingSpot.getX(), this.landingSpot.getY(), this.landingSpot.getZ());
                this.lastMessage = "\u8ba9 Baritone \u964d\u843d\u5230\u5b89\u5168\u70b9";
            } else if (mc.player.isGliding()) {
                this.fail("\u9700\u8981\u843d\u5730\u4fee\u7406\uff0c\u4f46\u6ca1\u6709\u88c5 Baritone \u65e0\u6cd5\u81ea\u52a8\u964d\u843d");
                return;
            }
        }
        if (mc.player.isOnGround() && !mc.player.isGliding()) {
            BaritoneHook.stop();
            String unsafe = this.unsafeReason(mc);
            if (!unsafe.isEmpty()) {
                this.fail("\u964d\u843d\u5730\u4e0d\u5b89\u5168\uff08" + unsafe + "\uff09\uff0c\u6362\u4e2a\u5730\u65b9\u518d\u4fee");
                return;
            }
            this.next(State.PREPARE, 2);
            return;
        }
        if (this.waitTicks++ > this.opts.landingTimeoutTicks()) {
            BaritoneHook.stop();
            this.fail("\u964d\u843d\u8d85\u65f6\uff08" + this.opts.landingTimeoutTicks() + " tick\uff09\uff0c\u653e\u5f03\u4fee\u7406");
            return;
        }
        this.delay = 1;
    }

    private BlockPos findSafeLandingSpot(MinecraftClient mc) {
        BlockPos here = this.safeStandPos(mc, mc.player.getBlockX(), mc.player.getBlockZ());
        if (here != null) {
            return here;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 2; r <= 24 && best == null; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    double d;
                    BlockPos spot;
                    int z;
                    int x;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !mc.world.isChunkLoaded((x = mc.player.getBlockX() + dx) >> 4, (z = mc.player.getBlockZ() + dz) >> 4) || (spot = this.safeStandPos(mc, x, z)) == null || !((d = Math.hypot((double)x - mc.player.getX(), (double)z - mc.player.getZ())) < bestDist)) continue;
                    bestDist = d;
                    best = spot;
                }
            }
        }
        return best;
    }

    private BlockPos safeStandPos(MinecraftClient mc, int x, int z) {
        for (int y = mc.player.getBlockY() + 16; y > mc.world.getBottomY() + 1; --y) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState ground = mc.world.getBlockState(p);
            if (ground.isAir()) continue;
            if (ground.isOf(Blocks.LAVA) || !ground.getFluidState().isEmpty()) {
                return null;
            }
            if (ground.isOf(Blocks.MAGMA_BLOCK)) {
                return null;
            }
            if (!mc.world.getBlockState(p.up()).isAir() || !mc.world.getBlockState(p.up(2)).isAir()) {
                return null;
            }
            if (mc.world.getBlockState(p.down()).isOf(Blocks.LAVA)) {
                return null;
            }
            if (mc.world.getBlockState(p.down(2)).isOf(Blocks.LAVA)) {
                return null;
            }
            if (this.isBasaltDeltas(mc, p)) {
                return null;
            }
            return p.up();
        }
        return null;
    }

    private String unsafeReason(MinecraftClient mc) {
        BlockPos p = mc.player.getBlockPos();
        BlockPos under = p.down();
        if (mc.world.getBlockState(p).isOf(Blocks.LAVA) || mc.world.getBlockState(under).isOf(Blocks.LAVA) || mc.world.getBlockState(under.down()).isOf(Blocks.LAVA)) {
            return "\u811a\u4e0b\u662f\u5ca9\u6d46";
        }
        if (mc.world.getBlockState(under).isOf(Blocks.MAGMA_BLOCK)) {
            return "\u811a\u4e0b\u662f\u5ca9\u6d46\u5757";
        }
        if (this.isBasaltDeltas(mc, p)) {
            return "\u8fd9\u91cc\u662f\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32";
        }
        return "";
    }

    private boolean isBasaltDeltas(MinecraftClient mc, BlockPos pos) {
        try {
            return mc.world.getBiomeAccess().getBiome(pos).getKey().map(key -> "minecraft:basalt_deltas".equals(key.getValue().toString())).orElse(false);
        }
        catch (Throwable t) {
            return false;
        }
    }

    private void prepare(MinecraftClient mc) {
        if (!this.ensureXpInHotbar(mc)) {
            this.fail("\u5feb\u6377\u680f\u817e\u4e0d\u51fa\u4f4d\u7f6e\u653e\u7ecf\u9a8c\u74f6");
            return;
        }
        int bottles = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.EXPERIENCE_BOTTLE);
        if (bottles <= 0) {
            this.fail("\u7ecf\u9a8c\u74f6\u5df2\u7528\u5b8c");
            return;
        }
        this.throwsDone = 0;
        this.airWaitTicks = 0;
        this.batchStartRemaining = ItemHelper.remainingDurability(ItemHelper.wornElytra((PlayerEntity)mc.player));
        this.next(State.THROW, 2);
    }

    private boolean ensureXpInHotbar(MinecraftClient mc) {
        for (int i = 0; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) continue;
            this.xpSlot = i;
            return true;
        }
        int source = InvHelper.findSlot(s -> s.isOf(Items.EXPERIENCE_BOTTLE), 9, 36);
        if (source < 0) {
            return false;
        }
        int target = InvHelper.findEmptyHotbarSlot();
        if (target < 0) {
            int least = -1;
            int leastCount = Integer.MAX_VALUE;
            for (int i = 0; i < 9; ++i) {
                ItemStack s2 = mc.player.getInventory().getStack(i);
                if (!s2.isOf(Items.FIREWORK_ROCKET) || s2.getCount() >= leastCount) continue;
                leastCount = s2.getCount();
                least = i;
            }
            target = least;
        }
        if (target < 0) {
            return false;
        }
        InvHelper.moveInvToHotbar(source, target);
        this.xpSlot = target;
        return true;
    }

    private void throwBottles(MinecraftClient mc) {
        ItemStack elytra = ItemHelper.wornElytra((PlayerEntity)mc.player);
        if (elytra.isEmpty()) {
            this.fail("\u9798\u7fc5\u4e0d\u89c1\u4e86");
            return;
        }
        int damage = elytra.getDamage();
        int remaining = ItemHelper.remainingDurability(elytra);
        if (damage <= this.opts.repairToDamage()) {
            this.lastMessage = "\u4fee\u590d\u5b8c\u6210\uff0c\u5269\u4f59\u8010\u4e45 " + remaining + "\uff08" + this.account(remaining) + "\uff09";
            FOElytraLog.tip("\u9798\u7fc5\u4fee\u590d\u5b8c\u6210\uff1a\u5269\u4f59\u8010\u4e45 %d\uff08%s\uff09", remaining, this.account(remaining));
            this.next(State.RESTORE, 1);
            return;
        }
        if (ItemHelper.countInInventory((PlayerEntity)mc.player, Items.EXPERIENCE_BOTTLE) <= 0) {
            this.lastMessage = "\u7ecf\u9a8c\u74f6\u7528\u5b8c\uff0c\u5269\u4f59\u8010\u4e45 " + remaining + "\uff08" + this.account(remaining) + "\uff09";
            FOElytraLog.warn("\u7ecf\u9a8c\u74f6\u7528\u5b8c\uff0c\u9798\u7fc5\u5269\u4f59\u8010\u4e45 %d\uff08\u635f\u4f24 %d\uff09\uff5c%s", remaining, damage, this.account(remaining));
            this.next(State.RESTORE, 1);
            return;
        }
        if (this.throwsDone >= this.opts.maxThrows()) {
            this.lastMessage = "\u6254\u74f6\u6b21\u6570\u8fbe\u5230\u4e0a\u9650\uff0c\u5269\u4f59\u8010\u4e45 " + remaining + "\uff08" + this.account(remaining) + "\uff09";
            FOElytraLog.warn("\u6254\u74f6\u6b21\u6570\u8fbe\u5230\u4e0a\u9650 %d\uff0c\u505c\u6b62\u4fee\u590d\uff08\u5269\u4f59\u8010\u4e45 %d\uff09\uff5c%s", this.opts.maxThrows(), remaining, this.account(remaining));
            this.next(State.RESTORE, 1);
            return;
        }
        if (mc.player.isGliding() || !mc.player.isOnGround()) {
            if (!this.airWarned) {
                this.airWarned = true;
                FOElytraLog.warn("\u79bb\u5730/\u6ed1\u7fd4\u65f6\u4e0d\u6295\u63b7\uff1a\u74f6\u5b50\u5728\u811a\u8fb9\u7834\uff0c\u7ecf\u9a8c\u7403\u4f1a\u843d\u5728\u8eab\u540e\u6361\u4e0d\u5230\uff0c\u5148\u843d\u5730\u518d\u4fee", new Object[0]);
            }
            if (++this.airWaitTicks > 100) {
                this.fail("\u79bb\u5730/\u6ed1\u7fd4\u4e2d\u65e0\u6cd5\u6295\u63b7\uff08\u7ecf\u9a8c\u7403\u4f1a\u843d\u5728\u8eab\u540e\uff09\uff0c\u5df2\u7b49 100 tick\uff1a\u8bf7\u5148\u843d\u5730\u518d\u4fee\u9798\u7fc5");
                return;
            }
            this.delay = 2;
            return;
        }
        this.airWaitTicks = 0;
        this.airWarned = false;
        if (mc.player.getPitch() < 85.0f) {
            mc.player.setPitch((float)this.opts.lookPitch());
            this.next(State.THROW, 11);
            return;
        }
        if (mc.player.getInventory().getStack(this.xpSlot).isEmpty() && !this.ensureXpInHotbar(mc)) {
            this.next(State.RESTORE, 1);
            return;
        }
        mc.player.setPitch((float)this.opts.lookPitch());
        InvHelper.selectSlot(this.xpSlot);
        InvHelper.useItem(Hand.MAIN_HAND);
        ++this.throwsDone;
        this.delay = this.clampedGap();
        FOElytraLog.detail("\u6254\u51fa\u7b2c %d \u4e2a\u7ecf\u9a8c\u74f6\uff08\u5269\u4f59\u8010\u4e45 %d\uff0c\u635f\u4f24 %d\uff0c\u5feb\u6377\u680f\u7b2c %d \u683c\uff0c\u624b\u6301 %s\uff0cBaritone \u98de\u884c\u4e2d %s\uff09", this.throwsDone, remaining, damage, mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE) ? "\u7ecf\u9a8c\u74f6" : mc.player.getMainHandStack().getName().getString(), BaritoneHook.isFlying() ? "\u662f" : "\u5426");
        if (this.throwsDone % 10 == 0) {
            FOElytraLog.info("\u5df2\u6254 %d \u53d1\uff1a\u8010\u4e45 %d \u2192 %d\uff08\u8fd9 %d \u53d1\u4fee\u4e86 %d\uff09", this.throwsDone, this.batchStartRemaining, remaining, 10, this.batchStartRemaining - remaining);
            this.batchStartRemaining = remaining;
        }
    }

    private int clampedGap() {
        return this.opts.throwDelay();
    }

    private String account(int remainingNow) {
        return "\u5171\u6254 " + this.throwsDone + " \u53d1\uff0c\u8010\u4e45 " + this.mendStartRemaining + " \u2192 " + remainingNow + "\uff08\u4fee\u4e86 " + (this.mendStartRemaining - remainingNow) + "\uff09";
    }

    private void restore(MinecraftClient mc) {
        mc.player.setPitch(this.previousPitch);
        if (this.previousSlot >= 0) {
            InvHelper.selectSlot(this.previousSlot);
        }
        this.next(State.DONE, 0);
    }

    private boolean isNetherWastes(MinecraftClient mc) {
        try {
            return mc.world.getBiomeAccess().getBiome(mc.player.getBlockPos()).getKey().map(key -> "minecraft:nether_wastes".equals(key.getValue().toString())).orElse(false);
        }
        catch (Throwable t) {
            return true;
        }
    }

    private void next(State next, int wait) {
        this.state = next;
        this.delay = Math.max(0, wait);
        if (next == State.DONE) {
            this.status = TaskStatus.DONE;
        }
        if (next == State.FAILED) {
            this.status = TaskStatus.FAILED;
        }
    }

    private void fail(String reason) {
        this.failReason = reason;
        this.lastMessage = reason;
        FOElytraLog.err("\u4fee\u9798\u7fc5\u5931\u8d25\uff1a%s", reason);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && this.state != State.IDLE) {
            mc.player.setPitch(this.previousPitch);
            if (this.previousSlot >= 0) {
                InvHelper.selectSlot(this.previousSlot);
            }
        }
        this.state = State.FAILED;
        this.status = TaskStatus.FAILED;
    }

    public static boolean shouldRepair(int threshold) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }
        ItemStack elytra = ItemHelper.wornElytra((PlayerEntity)mc.player);
        if (elytra.isEmpty()) {
            return false;
        }
        int remaining = ItemHelper.remainingDurability(elytra);
        return remaining >= 0 && remaining <= threshold;
    }

    public static enum State {
        IDLE("\u7a7a\u95f2"),
        ENSURE_GROUND("\u786e\u8ba4\u5730\u9762"),
        PREPARE("\u51c6\u5907"),
        THROW("\u6295\u63b7"),
        RESTORE("\u6062\u590d"),
        DONE("\u5b8c\u6210"),
        FAILED("\u5931\u8d25");


        private final String label;

        State(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public record Options(int triggerDurability, int minDurability, int minBottles, int repairToDamage, boolean requireGround, boolean requireNetherWastes, boolean requireMending, double lookPitch, int throwDelay, int maxThrows, int landingTimeoutTicks) {
    }
}

