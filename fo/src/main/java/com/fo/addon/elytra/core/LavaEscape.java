package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.PlayerAction;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.world.BlockView;

public final class LavaEscape {
    private static final int FIRE_TRIGGER = 20;
    private static final int NOT_GLIDING_TRIGGER = 5;
    private static final int ESCAPE_COOLDOWN = 45;
    private static final int RETRY_TICKS = 8;
    private static final int JUMP_FIRST_TICKS = 4;
    private static final int JUMP_GAP_TICKS = 1;
    private static final int JUMP_SECOND_TICKS = 3;
    private static final float LOOK_UP_PITCH = -90.0f;
    private static final int FALLBACK_DIRECTION_SAMPLES = 16;
    private static final int FALLBACK_CLEARANCE_STEPS = 6;
    private static final int FALLBACK_DIRECTION_REFRESH = 10;
    private static final int CEILING_SCAN = 5;
    private static final int OPEN_SCAN_RADIUS = 14;
    private static final int OPEN_SCAN_UP = 6;
    private static final int RELOCATE_MAX_TICKS = 200;
    private static final int ALIGN_MAX_TICKS = 60;
    private static final double RELOCATE_ARRIVE = 1.6;
    private final boolean useFirework;
    private final boolean swimToSafety;
    private final int searchRadius;
    private final boolean drinkFireRes;
    private final boolean ignoreGlidingFire;
    private final float lookPitch;
    private BlockPos openTarget;
    private int relocateTicks;
    private int relocateFireCooldown;
    private boolean relocateGaveUp;
    private int alignTicks;
    private int inFireTick;
    private int retryCooldown;
    private int jumpPhase = -1;
    private int phaseTicks;
    private boolean pendingFirework;
    private boolean fireworkMissing;
    private boolean loggedMissingFirework;
    private boolean fallbackHorizontal;
    private int fallbackTargetRefresh;
    private BlockPos fallbackTarget;
    private String lastReason = "";
    private boolean failedNoFirework;
    private int drinkLeft;
    private double triggerX;
    private double triggerY;
    private double triggerZ;
    private float triggerPitch;
    private boolean triggerPosValid;

    public LavaEscape(boolean useFirework, boolean swimToSafety, int searchRadius, boolean drinkFireRes, boolean ignoreGlidingFire, float lookPitch) {
        this.useFirework = useFirework;
        this.swimToSafety = swimToSafety;
        this.searchRadius = Math.max(3, Math.min(24, searchRadius));
        this.drinkFireRes = drinkFireRes;
        this.ignoreGlidingFire = ignoreGlidingFire;
        this.lookPitch = Math.max(-90.0f, Math.min(0.0f, lookPitch));
    }

    public boolean isEscaping() {
        return this.inFireTick < 0;
    }

    public String lastReason() {
        return this.lastReason;
    }

    public boolean failedNoFirework() {
        return this.failedNoFirework;
    }

    public void reset() {
        PlayerAction.pressJump(false);
        PlayerAction.pressUse(false);
        PlayerAction.pressForward(false);
        this.inFireTick = 0;
        this.jumpPhase = -1;
        this.phaseTicks = 0;
        this.pendingFirework = false;
        this.fireworkMissing = false;
        this.loggedMissingFirework = false;
        this.fallbackHorizontal = false;
        this.fallbackTarget = null;
        this.fallbackTargetRefresh = 0;
        this.lastReason = "";
        this.failedNoFirework = false;
        this.drinkLeft = 0;
        this.retryCooldown = 0;
        this.triggerPosValid = false;
        this.openTarget = null;
        this.relocateTicks = 0;
        this.relocateFireCooldown = 0;
        this.relocateGaveUp = false;
        this.alignTicks = 0;
        PlayerAction.pressForward(false);
    }

    private void relocateTick(MinecraftClient mc) {
        double dx = (double)this.openTarget.getX() + 0.5 - mc.player.getX();
        double dz = (double)this.openTarget.getZ() + 0.5 - mc.player.getZ();
        double dist = Math.hypot(dx, dz);
        if (this.relocateTicks == 0) {
            FOElytraLog.warn("\u5ca9\u6d46\u81ea\u6551\uff1a\u5934\u9876\u5c01\u6b7b\uff0c\u5148\u7528\u9798\u7fc5\u5728\u5ca9\u6d46\u91cc\u6a2a\u7740\u51b2\u5230\u6700\u8fd1\u7684\u5f00\u53e3\u5ca9\u6d46\u67f1 %d %d %d\uff08\u6c34\u5e73 %.0f \u683c\uff09\uff0c\u5230\u4e86\u518d\u5f80\u4e0a\u51b2", this.openTarget.getX(), this.openTarget.getY(), this.openTarget.getZ(), dist);
        }
        ++this.relocateTicks;
        if (dist <= 1.6) {
            PlayerAction.pressForward(false);
            this.openTarget = null;
            this.relocateTicks = 0;
            this.relocateFireCooldown = 0;
            FOElytraLog.info("\u5ca9\u6d46\u81ea\u6551\uff1a\u5df2\u7ecf\u5230\u5f00\u53e3\u5904\u4e86\uff0c\u6539\u6210\u62ac\u5934\u5f80\u4e0a\u51b2", new Object[0]);
            return;
        }
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(yaw);
        mc.player.setPitch(0.0f);
        PlayerAction.pressForward(true);
        PlayerAction.pressJump(true);
        if (this.relocateTicks >= 200) {
            PlayerAction.pressForward(false);
            FOElytraLog.warn("\u5ca9\u6d46\u81ea\u6551\uff1a\u6a2a\u7740\u51b2\u4e86 %.1f \u79d2\u8fd8\u6ca1\u5230\u5f00\u53e3\u5904\uff08\u6c34\u5e73\u8fd8\u5269 %.0f \u683c\uff09\uff0c\u653e\u5f03\u6a2a\u79fb\uff0c\u6309\u539f\u65f6\u5e8f\u5f80\u4e0a\u51b2", 10.0, dist);
            this.openTarget = null;
            return;
        }
        if (this.useFirework && --this.relocateFireCooldown <= 0) {
            this.relocateFireCooldown = 8;
            this.shootFirework(mc, "\u5ca9\u6d46\u81ea\u6551\uff1a\u671d\u5f00\u53e3\u5904\u8865\u5c04\u70df\u82b1");
        }
    }

    private boolean shootFirework(MinecraftClient mc, String why) {
        for (int i = 7; i >= 0; --i) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) continue;
            mc.player.getInventory().setSelectedSlot(i);
            if (mc.getNetworkHandler() != null) {
                try {
                    mc.getNetworkHandler().sendPacket((Packet)new UpdateSelectedSlotC2SPacket(i));
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
            InvHelper.useItem(Hand.MAIN_HAND);
            FOElytraLog.info("%s\uff08\u5feb\u6377\u680f\u7b2c %d \u683c\uff09", why, i + 1);
            return true;
        }
        return false;
    }

    private BlockPos findOpenLavaColumn(MinecraftClient mc) {
        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();
        for (int r = 1; r <= 14; ++r) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; ++dx) {
                for (int dz = -r; dz <= r; ++dz) {
                    double d;
                    BlockPos spot;
                    int z;
                    int x;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !mc.world.isChunkLoaded((x = px + dx) >> 4, (z = pz + dz) >> 4) || (spot = this.openColumnAt(mc, x, z, py)) == null || !this.lavaPathClear(mc, px, py, pz, x, z) || !((d = Math.hypot(x - px, z - pz)) < bestDist)) continue;
                    bestDist = d;
                    best = spot;
                }
            }
            if (best == null) continue;
            return best;
        }
        return null;
    }

    private BlockPos openColumnAt(MinecraftClient mc, int x, int z, int py) {
        for (int dy = -1; dy <= 1; ++dy) {
            BlockPos p = new BlockPos(x, py + dy, z);
            BlockState st = mc.world.getBlockState(p);
            if (!st.getFluidState().isOf((Fluid)Fluids.LAVA)) continue;
            boolean open = true;
            for (int i = 1; i <= 6; ++i) {
                BlockState up = mc.world.getBlockState(p.up(i));
                if (up.isAir() || !up.getFluidState().isEmpty()) continue;
                open = false;
                break;
            }
            if (!open) continue;
            return p;
        }
        return null;
    }

    private boolean lavaPathClear(MinecraftClient mc, int px, int py, int pz, int x, int z) {
        int steps = (int)Math.ceil(Math.hypot(x - px, z - pz));
        for (int i = 1; i <= steps; ++i) {
            double t = (double)i / (double)steps;
            int ix = (int)Math.round((double)px + (double)(x - px) * t);
            int iz = (int)Math.round((double)pz + (double)(z - pz) * t);
            for (int dy = -1; dy <= 1; ++dy) {
                BlockState st = mc.world.getBlockState(new BlockPos(ix, py + dy, iz));
                if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                return false;
            }
        }
        return true;
    }

    private BlockPos solidBlockAbove(MinecraftClient mc, int maxUp) {
        BlockPos base = BlockPos.ofFloored((Position)mc.player.getEyePos());
        for (int i = 0; i <= maxUp; ++i) {
            BlockPos p = base.up(i);
            BlockState st = mc.world.getBlockState(p);
            if (st.isAir() || !st.getFluidState().isEmpty()) continue;
            return p;
        }
        return null;
    }

    public void release(MinecraftClient mc) {
        this.reset();
    }

    public Result tick(boolean enabled) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return Result.IDLE;
        }
        if (!enabled) {
            this.reset();
            return Result.IDLE;
        }
        boolean inLava = mc.player.isInLava();
        if (this.inFireTick < 0 && inLava) {
            BlockPos above = this.solidBlockAbove(mc, 5);
            if (above != null) {
                if (this.openTarget == null && !this.relocateGaveUp && this.relocateTicks == 0) {
                    this.openTarget = this.findOpenLavaColumn(mc);
                    if (this.openTarget == null) {
                        this.relocateGaveUp = true;
                        FOElytraLog.warn("\u5ca9\u6d46\u81ea\u6551\uff1a\u5934\u9876\u88ab %d %d %d \u5c01\u6b7b\uff0c%d \u683c\u5185\u4e5f\u6ca1\u6709\u5f00\u53e3\u7684\u5ca9\u6d46\u67f1\uff08\u4e0d\u6316\u5934\u9876\uff09\u2192 \u53ea\u80fd\u6309\u539f\u65f6\u5e8f\u5f80\u4e0a\u51b2\uff0c\u5927\u6982\u7387\u51fa\u4e0d\u53bb", above.getX(), above.getY(), above.getZ(), 14);
                    }
                }
                if (this.openTarget != null && this.relocateTicks < 200) {
                    this.relocateTick(mc);
                    return Result.ESCAPING;
                }
            } else if (this.openTarget != null) {
                this.openTarget = null;
                PlayerAction.pressForward(false);
                FOElytraLog.info("\u5ca9\u6d46\u81ea\u6551\uff1a\u5934\u9876\u5df2\u7ecf\u901a\u4e86\uff0c\u7ee7\u7eed\u62ac\u5934\u51b2\u51fa\u53bb", new Object[0]);
            }
        }
        if (this.inFireTick < 0 && inLava && this.solidBlockAbove(mc, 5) == null) {
            if (this.alignTicks >= 60) {
                if (this.alignTicks == 60) {
                    ++this.alignTicks;
                    PlayerAction.pressForward(false);
                    FOElytraLog.warn("\u5ca9\u6d46\u81ea\u6551\uff1a\u5bf9\u51c6\u4e95\u53e3\u4e2d\u5fc3\u7528\u4e86 %.0f \u79d2\u8fd8\u6ca1\u5230\u4f4d\uff08\u811a\u4e0b\u6253\u6ed1\uff1f\uff09\u2192 \u4e0d\u6309\u524d\u8fdb\u952e\u4e86\uff0c\u76f4\u63a5\u62ac\u5934\u51b2", 3.0);
                }
            } else {
                double cx = Math.floor(mc.player.getX()) + 0.5;
                double cz = Math.floor(mc.player.getZ()) + 0.5;
                double off = Math.hypot(mc.player.getX() - cx, mc.player.getZ() - cz);
                if (off > 0.3) {
                    if (this.alignTicks == 0) {
                        FOElytraLog.detail("\u5ca9\u6d46\u81ea\u6551\uff1a\u5934\u9876\u662f\u7ad6\u76f4\u5f00\u53e3\uff0c\u5148\u5bf9\u51c6\u8fd9\u4e00\u683c\u7684\u4e2d\u5fc3\u518d\u5f80\u4e0a\u51b2\uff08\u504f %.2f \u683c\uff09", off);
                    }
                    float yaw = (float)Math.toDegrees(Math.atan2(-(cx - mc.player.getX()), cz - mc.player.getZ()));
                    mc.player.setYaw(yaw);
                    mc.player.setPitch(0.0f);
                    PlayerAction.pressForward(true);
                    ++this.alignTicks;
                    return Result.ESCAPING;
                }
                if (this.alignTicks > 0) {
                    PlayerAction.pressForward(false);
                    this.alignTicks = 0;
                }
            }
        }
        this.inFireTick = this.inFireTick >= 0 ? (inLava ? this.inFireTick + 1 : 0) : ++this.inFireTick;
        if (this.inFireTick == -1) {
            this.inFireTick = 0;
            PlayerAction.pressJump(false);
            PlayerAction.pressForward(false);
            this.jumpPhase = -1;
            this.pendingFirework = false;
            this.fallbackHorizontal = false;
            if (inLava) {
                return this.escapeFailed(mc, "\u51b7\u5374\u8d70\u5b8c\u8fd8\u5728\u5ca9\u6d46\u91cc\uff08\u81ea\u6551\u6ca1\u6210\u529f\uff09");
            }
            mc.player.setPitch(this.triggerPitch);
            FOElytraLog.info("\u9003\u79bb\u5ca9\u6d46\u6210\u529f\uff1a\u5df2\u7ecf\u4e0d\u5728\u5ca9\u6d46\u91cc\u4e86\uff08%s\uff09", this.posText(mc));
            return Result.IDLE;
        }
        if (this.inFireTick < 0 && !inLava) {
            this.inFireTick = -1;
        }
        if (this.inFireTick >= 0 && this.ignoreGlidingFire && mc.player.isGliding()) {
            if (this.inFireTick == 21) {
                FOElytraLog.warn("\u8bbe\u7f6e\u5f00\u4e86\u300c\u6ed1\u7fd4\u65f6\u5ffd\u7565\u5ca9\u6d46\u300d\uff1a\u6ed1\u7fd4\u4e2d\u4e0d\u89e6\u53d1\u81ea\u6551\uff08\u5371\u9669\u8bbe\u7f6e\uff0c\u5efa\u8bae\u5173\u6389\uff09", new Object[0]);
            }
            return Result.IDLE;
        }
        if (this.inFireTick > 20 || this.inFireTick > 5 && !mc.player.isGliding()) {
            this.inFireTick = -45;
            this.triggerX = mc.player.getX();
            this.triggerY = mc.player.getY();
            this.triggerZ = mc.player.getZ();
            this.triggerPitch = mc.player.getPitch();
            this.triggerPosValid = true;
            this.fireworkMissing = false;
            this.pendingFirework = true;
            this.retryCooldown = 8;
            this.fallbackHorizontal = false;
            this.fallbackTarget = null;
            this.fallbackTargetRefresh = 0;
            if (mc.player.isOnGround()) {
                this.jumpPhase = 0;
                this.phaseTicks = 4;
            } else {
                this.jumpPhase = 2;
                this.phaseTicks = 3;
            }
            PlayerAction.pressJump(true);
            return Result.ESCAPING;
        }
        if (this.inFireTick < 0) {
            if (this.pendingFirework) {
                this.stepJumpSequence(mc);
                if (this.pendingFirework) {
                    return Result.ESCAPING;
                }
                if (this.fireworkMissing) {
                    return this.escapeFailed(mc, "\u627e\u4e0d\u5230\u70df\u82b1\uff08\u5feb\u6377\u680f 0~7 \u65e2\u6ca1\u6709\u7a7a\u69fd\u4e5f\u6ca1\u6709\u70df\u82b1\uff09");
                }
            }
            if (--this.retryCooldown <= 0) {
                this.retryCooldown = 8;
                if (mc.player.isGliding() && mc.player.isInLava()) {
                    mc.player.setPitch(this.lookPitch);
                    int slot2 = -1;
                    for (int i = 0; i < 8; ++i) {
                        if (!mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) continue;
                        slot2 = i;
                    }
                    if (slot2 >= 0) {
                        mc.player.getInventory().setSelectedSlot(slot2);
                        if (mc.getNetworkHandler() != null) {
                            try {
                                mc.getNetworkHandler().sendPacket((Packet)new UpdateSelectedSlotC2SPacket(slot2));
                            }
                            catch (Throwable throwable) {
                                // empty catch block
                            }
                        }
                        InvHelper.useItem(Hand.MAIN_HAND);
                        FOElytraLog.info("\u81ea\u6551\u7a97\u53e3\u5185\u5c55\u7fc5\u6210\u529f\uff0c\u7acb\u523b\u8865\u5c04\u70df\u82b1\uff08\u5feb\u6377\u680f\u7b2c %d \u683c\uff09", slot2 + 1);
                    }
                } else {
                    if (mc.player.isOnGround()) {
                        this.jumpPhase = 0;
                        this.phaseTicks = 4;
                    } else {
                        this.jumpPhase = 2;
                        this.phaseTicks = 3;
                    }
                    this.pendingFirework = true;
                    PlayerAction.pressJump(true);
                }
                return Result.ESCAPING;
            }
            if (this.drinkFireRes) {
                if (this.drinkLeft > 0) {
                    this.drinkTick(mc);
                } else if (!mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
                    this.startDrink(mc);
                }
            }
            if (this.swimToSafety) {
                this.fallbackSwimTick(mc, inLava);
            }
            return Result.ESCAPING;
        }
        return Result.IDLE;
    }

    private void stepJumpSequence(MinecraftClient mc) {
        switch (this.jumpPhase) {
            case 0: {
                PlayerAction.pressJump(true);
                if (--this.phaseTicks > 0) break;
                this.jumpPhase = 1;
                this.phaseTicks = 1;
                break;
            }
            case 1: {
                PlayerAction.pressJump(false);
                if (--this.phaseTicks > 0) break;
                this.jumpPhase = 2;
                this.phaseTicks = 3;
                break;
            }
            case 2: {
                PlayerAction.pressJump(true);
                if (--this.phaseTicks > 0) break;
                this.jumpPhase = 3;
                this.phaseTicks = 1;
                break;
            }
            case 3: {
                PlayerAction.pressJump(false);
                this.jumpPhase = -1;
                this.finishTriggerActions(mc);
                break;
            }
            default: {
                this.jumpPhase = -1;
            }
        }
    }

    private void finishTriggerActions(MinecraftClient mc) {
        int i;
        this.pendingFirework = false;
        FOElytraLog.warn("\u4f4d\u4e8e\u5ca9\u6d46\u4e2d\uff0c\u5df2\u9798\u7fc5\u6253\u5f00", new Object[0]);
        mc.player.setPitch(this.lookPitch);
        if (!this.useFirework) {
            return;
        }
        int slot = -1;
        for (i = 0; i < 8; ++i) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) continue;
            slot = i;
        }
        if (slot < 0) {
            for (i = 0; i < 8; ++i) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
                slot = i;
            }
        }
        if (slot < 0) {
            this.fireworkMissing = true;
            this.failedNoFirework = true;
            FOElytraLog.err("\u627e\u4e0d\u5230\u70df\u82b1\uff1a\u5feb\u6377\u680f 0~7 \u65e2\u6ca1\u6709\u7a7a\u69fd\u4e5f\u6ca1\u6709\u70df\u82b1\u683c\uff0c\u653e\u4e0d\u51fa\u70df\u82b1", new Object[0]);
            return;
        }
        boolean realFirework = mc.player.getInventory().getStack(slot).isOf(Items.FIREWORK_ROCKET);
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            try {
                mc.getNetworkHandler().sendPacket((Packet)new UpdateSelectedSlotC2SPacket(slot));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        InvHelper.useItem(Hand.MAIN_HAND);
        if (realFirework) {
            FOElytraLog.info("\u5df2\u4f7f\u7528\u70df\u82b1\uff01\uff08\u5feb\u6377\u680f\u7b2c %d \u683c\uff09", slot + 1);
        } else if (!this.loggedMissingFirework) {
            this.loggedMissingFirework = true;
            FOElytraLog.warn("\u6ce8\u610f\uff1a\u5feb\u6377\u680f 0~7 \u91cc\u9009\u4e2d\u7684\u662f\u7b2c %d \u683c\u7a7a\u69fd\uff08\u69fd\u91cc\u6ca1\u70df\u82b1\uff09\uff0c\u8fd9\u4e00\u4e0b\u53f3\u952e\u6ca1\u6709\u63a8\u529b", slot + 1);
        }
    }

    private Result escapeFailed(MinecraftClient mc, String why) {
        this.lastReason = why;
        FOElytraLog.err("\u9003\u79bb\u5ca9\u6d46\u5931\u8d25\uff1a%s\uff08%s\uff09", why, this.posText(mc));
        this.failedNoFirework = this.failedNoFirework || LavaEscape.countFireworks((PlayerEntity)mc.player) <= 0;
        PlayerAction.pressJump(false);
        PlayerAction.pressForward(false);
        PlayerAction.pressUse(false);
        this.jumpPhase = -1;
        this.pendingFirework = false;
        this.fallbackHorizontal = false;
        this.inFireTick = 0;
        return Result.FAILED;
    }

    private String posText(MinecraftClient mc) {
        if (mc.player == null) {
            return "\u4f4d\u7f6e\u672a\u77e5";
        }
        if (!this.triggerPosValid) {
            return String.format("\u4f4d\u7f6e %d %d %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        }
        return String.format("\u4ece %d %d %d \u5230 %d %d %d", (int)this.triggerX, (int)this.triggerY, (int)this.triggerZ, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
    }

    private void fallbackSwimTick(MinecraftClient mc, boolean inLava) {
        float yaw;
        if (!this.fallbackHorizontal) {
            this.fallbackHorizontal = true;
            FOElytraLog.detail("\u515c\u5e95\uff1a\u6e38\u5411\u5b89\u5168\u70b9\uff08\u989d\u5916\u529f\u80fd\uff0c\u9ed8\u8ba4\u5173\uff09\u5df2\u6253\u5f00\uff0c\u671d\u6700\u8fd1\u7684\u5b89\u5168\u70b9\u6e38\u8fc7\u53bb", new Object[0]);
        }
        if (this.fallbackTarget == null || --this.fallbackTargetRefresh <= 0 || !this.isSafe(mc, this.fallbackTarget)) {
            this.fallbackTarget = this.findSafeSpot(mc, this.searchRadius);
            this.fallbackTargetRefresh = 10;
        }
        boolean haveTarget = false;
        double dx = 0.0;
        double dz = 0.0;
        double dy = 0.0;
        if (this.fallbackTarget != null) {
            dx = (double)this.fallbackTarget.getX() + 0.5 - mc.player.getX();
            dz = (double)this.fallbackTarget.getZ() + 0.5 - mc.player.getZ();
            dy = (double)this.fallbackTarget.getY() - mc.player.getY();
            haveTarget = true;
        }
        if (!haveTarget && !Float.isNaN(yaw = this.pickFreeYaw(mc))) {
            double rad = Math.toRadians(yaw);
            dx = -Math.sin(rad);
            dz = Math.cos(rad);
            dy = 0.0;
            haveTarget = true;
        }
        if (!haveTarget) {
            PlayerAction.pressForward(false);
            return;
        }
        double flat = Math.max(0.001, Math.hypot(dx, dz));
        float yaw2 = (float)((Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0);
        float pitch = (float)Math.max(-35.0, Math.min(20.0, -Math.toDegrees(Math.atan2(dy, flat))));
        mc.player.setYaw(yaw2);
        mc.player.setPitch(pitch);
        PlayerAction.pressForward(true);
        if (inLava) {
            PlayerAction.pressJump(true);
        }
    }

    private float pickFreeYaw(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return Float.NaN;
        }
        BlockPos origin = mc.player.getBlockPos();
        float currentYaw = mc.player.getYaw();
        float bestYaw = Float.NaN;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < 16; ++k) {
            double angleDiff;
            double score;
            BlockPos feet;
            int bz;
            int bx;
            float yaw = (float)k * 22.5f;
            double rad = Math.toRadians(yaw);
            double stepX = -Math.sin(rad);
            double stepZ = Math.cos(rad);
            int free = 0;
            for (int s = 1; s <= 6 && mc.world.isChunkLoaded((bx = (int)Math.floor(mc.player.getX() + stepX * (double)s)) >> 4, (bz = (int)Math.floor(mc.player.getZ() + stepZ * (double)s)) >> 4) && !this.solid(mc, feet = new BlockPos(bx, origin.getY(), bz)) && !this.solid(mc, feet.up()); ++s) {
                ++free;
            }
            if (free <= 0 || !((score = (double)free * 2.0 - (angleDiff = (double)Math.abs((yaw - currentYaw + 540.0f) % 360.0f - 180.0f)) * 0.02) > bestScore)) continue;
            bestScore = score;
            bestYaw = yaw;
        }
        return bestYaw;
    }

    private BlockPos findSafeSpot(MinecraftClient mc, int radius) {
        if (mc.world == null || mc.player == null) {
            return null;
        }
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -2; dy <= 3; ++dy) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    BlockPos q;
                    double score;
                    if (dx == 0 && dz == 0 && dy <= 0 || (score = (double)(dx * dx + dz * dz) + (double)(dy * dy) * 6.0) >= bestScore || !this.isSafe(mc, q = origin.add(dx, dy, dz))) continue;
                    bestScore = score;
                    best = q;
                }
            }
        }
        return best;
    }

    private boolean isSafe(MinecraftClient mc, BlockPos pos) {
        if (mc.world == null || pos == null) {
            return false;
        }
        if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        if (this.isLava(mc, pos) || this.isLava(mc, pos.up())) {
            return false;
        }
        if (this.solid(mc, pos) || this.solid(mc, pos.up())) {
            return false;
        }
        return this.solid(mc, pos.down()) && !this.isLava(mc, pos.down());
    }

    private boolean isLava(MinecraftClient mc, BlockPos pos) {
        FluidState fluid = mc.world.getFluidState(pos);
        return fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA;
    }

    private boolean solid(MinecraftClient mc, BlockPos pos) {
        return !mc.world.getBlockState(pos).getCollisionShape((BlockView)mc.world, pos).isEmpty();
    }

    private boolean startDrink(MinecraftClient mc) {
        int slot = -1;
        for (int i = 0; i < 9; ++i) {
            if (!LavaEscape.isFireResPotion(mc.player.getInventory().getStack(i))) continue;
            slot = i;
            break;
        }
        if (slot < 0) {
            return false;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        this.drinkLeft = 45;
        PlayerAction.pressUse(true);
        FOElytraLog.warn("\u515c\u5e95\uff1a\u559d\u6297\u706b\u836f\u6c34\uff08\u989d\u5916\u529f\u80fd\uff09\uff1a\u5feb\u6377\u680f\u7b2c %d \u683c\u5148\u559d\u6389", slot + 1);
        return true;
    }

    private void drinkTick(MinecraftClient mc) {
        --this.drinkLeft;
        PlayerAction.pressUse(this.drinkLeft > 0);
        if (this.drinkLeft <= 0 && mc.player != null && !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            FOElytraLog.warn("\u6297\u706b\u836f\u6c34\u6ca1\u559d\u4e0a\uff08\u53ef\u80fd\u88ab\u522b\u7684\u6a21\u5757\u62a2\u4e86\u53f3\u952e\uff09", new Object[0]);
        }
    }

    private static boolean isFireResPotion(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (!(s.isOf(Items.POTION) || s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION))) {
            return false;
        }
        PotionContentsComponent contents = (PotionContentsComponent)s.get(DataComponentTypes.POTION_CONTENTS);
        return contents != null && (contents.matches(Potions.FIRE_RESISTANCE) || contents.matches(Potions.LONG_FIRE_RESISTANCE));
    }

    private static int countFireworks(PlayerEntity player) {
        int n = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isOf(Items.FIREWORK_ROCKET)) continue;
            n += s.getCount();
        }
        return n;
    }

    public static int fireTrigger() {
        return 20;
    }

    public static int notGlidingTrigger() {
        return 5;
    }

    public static int escapeCooldown() {
        return 45;
    }

    public static enum Result {
        IDLE("\u7a7a\u95f2"),
        ESCAPING("\u9003\u79bb\u4e2d"),
        FAILED("\u5931\u8d25");


        private final String label;

        Result(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }
}

