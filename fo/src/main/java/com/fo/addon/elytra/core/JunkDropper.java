package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class JunkDropper {
    private static final int DROP_DELAY_TICKS = 4;
    private static final int NO_LAVA_LOG_TICKS = 600;
    private static final int AIRBORNE_LOG_TICKS = 200;
    private static final int TOO_HIGH_PENALTY = 24;
    private int delay;
    private int lastNoLavaLog = -600;
    private int lastAirborneLog = -200;
    private int lastDropTick;
    private int dropped;

    public int droppedCount() {
        return this.dropped;
    }

    public void reset() {
        this.delay = 0;
        this.dropped = 0;
        this.lastNoLavaLog = -600;
        this.lastAirborneLog = -200;
    }

    public void tick(boolean enabled, boolean lavaOnly, int radius, List<Item> junk, List<Item> extraProtected, int tick) {
        ScreenHandler handler;
        int raw;
        double dist;
        if (!enabled) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (this.delay > 0) {
            --this.delay;
            return;
        }
        if (InvHelper.screenOpen()) {
            return;
        }
        int slot = this.findJunkSlot(mc, junk, extraProtected);
        if (slot < 0) {
            return;
        }
        if (!mc.player.isOnGround()) {
            if (tick - this.lastAirborneLog >= 200) {
                this.lastAirborneLog = tick;
                FOElytraLog.detail("\u80cc\u5305\u91cc\u6709\u5783\u573e\u65b9\u5757\uff0c\u4f46\u73b0\u5728\u6ca1\u843d\u5730\uff1a\u7b49\u843d\u5730\u518d\u4e22\uff08\u4e0d\u843d\u5730\u4e22\u5bb9\u6613\u628a\u4e1c\u897f\u4e22\u98de\uff09", new Object[0]);
            }
            return;
        }
        BlockPos lava = this.findLava(mc, radius);
        if (lava == null && lavaOnly) {
            if (tick - this.lastNoLavaLog >= 600) {
                this.lastNoLavaLog = tick;
                FOElytraLog.detail("\u534a\u5f84 %d \u683c\u5185\u6ca1\u627e\u5230\u5ca9\u6d46\uff0c\u5783\u573e\u5148\u7559\u7740\uff08\u5173\u6389\u300c\u53ea\u5728\u9644\u8fd1\u6709\u5ca9\u6d46\u65f6\u4e22\u300d\u53ef\u4ee5\u5c31\u5730\u4e22\uff09", radius);
            }
            return;
        }
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty()) {
            return;
        }
        String name = stack.getName().getString();
        int count = stack.getCount();
        double d = dist = lava == null ? 0.0 : Math.sqrt(mc.player.squaredDistanceTo((double)lava.getX() + 0.5, (double)lava.getY() + 0.5, (double)lava.getZ() + 0.5));
        if (lava != null) {
            InvHelper.lookAt((PlayerEntity)mc.player, new Vec3d((double)lava.getX() + 0.5, (double)lava.getY() + 0.6, (double)lava.getZ() + 0.5));
        }
        if ((raw = InvHelper.playerSlotId(handler = mc.player.currentScreenHandler, (PlayerEntity)mc.player, slot)) < 0) {
            return;
        }
        if (!InvHelper.dropSlot(handler, raw)) {
            FOElytraLog.warn("\u6ca1\u80fd\u628a %s x%d \u4e22\u51fa\u53bb\uff08\u670d\u52a1\u7aef\u6ca1\u53d7\u7406\uff09\u2192 \u539f\u5730\u653e\u56de\uff0c\u7b49\u4e0b\u4e00\u8f6e\u518d\u8bd5", name, count);
            this.delay = 4;
            return;
        }
        ++this.dropped;
        this.delay = 4;
        this.lastDropTick = tick;
        if (lava == null) {
            FOElytraLog.info("\u4e22\u6389\u5783\u573e\uff1a%s x%d \u2192 \u5730\u4e0a\uff08\u9644\u8fd1\u6ca1\u5ca9\u6d46\uff0c\u5c31\u5730\u4e22\uff09\uff5c\u80cc\u5305\u7a7a\u4f4d %d\uff0c\u5feb\u6377\u680f\u7a7a\u4f4d %d", name, count, InvHelper.emptyBackpackSlots(), this.countEmptyHotbar(mc));
        } else if (slot < 9) {
            FOElytraLog.info("\u4e22\u6389\u5783\u573e\uff1a%s x%d \u2192 \u5ca9\u6d46\uff08\u8ddd\u79bb %.1f \u683c\uff09\uff5c\u80cc\u5305\u91cc\u6ca1\u6709\u53ef\u4e22\u7684\uff0c\u624d\u52a8\u4e86\u5feb\u6377\u680f\u8fd9\u4e00\u683c\uff5c\u80cc\u5305\u7a7a\u4f4d %d", name, count, dist, InvHelper.emptyBackpackSlots());
        } else {
            FOElytraLog.info("\u4e22\u6389\u5783\u573e\uff1a%s x%d \u2192 \u5ca9\u6d46\uff08\u8ddd\u79bb %.1f \u683c\uff09\uff5c\u80cc\u5305\u7a7a\u4f4d %d\uff0c\u5feb\u6377\u680f\u7a7a\u4f4d %d", name, count, dist, InvHelper.emptyBackpackSlots(), this.countEmptyHotbar(mc));
        }
    }

    public int lastDropTick() {
        return this.lastDropTick;
    }

    private int findJunkSlot(MinecraftClient mc, List<Item> junk, List<Item> extraProtected) {
        for (int pass = 0; pass < 2; ++pass) {
            int from = pass == 0 ? 9 : 0;
            int to = pass == 0 ? 36 : 9;
            for (int i = from; i < to; ++i) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty() || !this.isJunk(s, junk, extraProtected)) continue;
                return i;
            }
        }
        return -1;
    }

    private boolean isJunk(ItemStack stack, List<Item> junk, List<Item> extraProtected) {
        if (junk == null || junk.isEmpty()) {
            return false;
        }
        if (!junk.contains(stack.getItem())) {
            return false;
        }
        return !this.isProtected(stack, extraProtected);
    }

    private boolean isProtected(ItemStack stack, List<Item> extraProtected) {
        Item item = stack.getItem();
        if (item == Items.FIREWORK_ROCKET || item == Items.EXPERIENCE_BOTTLE || item == Items.ELYTRA || item == Items.TOTEM_OF_UNDYING || item == Items.ENDER_CHEST) {
            return true;
        }
        if (ItemHelper.isShulkerBox(stack)) {
            return true;
        }
        if (ItemHelper.isFood(stack)) {
            return true;
        }
        if (stack.getMaxDamage() > 0) {
            return true;
        }
        return extraProtected != null && extraProtected.contains(item);
    }

    private BlockPos findLava(MinecraftClient mc, int radius) {
        BlockPos base = mc.player.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    BlockPos p = base.add(dx, dy, dz);
                    if (!mc.world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4) || !mc.world.getBlockState(p).isOf(Blocks.LAVA) || !mc.world.getBlockState(p.up()).isAir()) continue;
                    double d = mc.player.squaredDistanceTo((double)p.getX() + 0.5, (double)p.getY() + 0.5, (double)p.getZ() + 0.5);
                    double score = d + (p.getY() > base.getY() ? 24.0 : 0.0);
                    if (!(score < bestScore)) continue;
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }

    private int countEmptyHotbar(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            ++n;
        }
        return n;
    }
}

