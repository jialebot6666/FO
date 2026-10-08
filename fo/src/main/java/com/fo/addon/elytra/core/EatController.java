package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.FoodPriority;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class EatController {
    private static final int START_TIMEOUT = 8;
    private static final int RELEASE_GRACE = 3;
    private static final int CHECK_LOG_INTERVAL = 100;
    private boolean eating;
    private boolean baritonePaused;
    private int checkTicks;
    private int startWait;
    private int releaseWait;
    private int previousSlot = -1;

    public boolean isEating() {
        return this.eating;
    }

    public void tick(boolean enabled, int hungerThreshold, double healthThreshold, boolean allowWhileGliding, double minRiseSpeed, List<Item> foodWhitelist) {
        this.tick(enabled, hungerThreshold, healthThreshold, allowWhileGliding, minRiseSpeed, foodWhitelist, null);
    }

    public void tick(boolean enabled, int hungerThreshold, double healthThreshold, boolean allowWhileGliding, double minRiseSpeed, List<Item> foodWhitelist, List<Item> foodPriority) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            this.stop();
            return;
        }
        if (!enabled) {
            this.stop();
            return;
        }
        if (this.eating) {
            if (mc.player.isUsingItem()) {
                this.startWait = 0;
                this.releaseWait = 0;
                return;
            }
            if (this.startWait < 8) {
                ++this.startWait;
                return;
            }
            if (this.releaseWait++ >= 3) {
                this.release();
            }
            return;
        }
        ++this.checkTicks;
        if (this.checkTicks % 100 == 0) {
            String reason = this.eatBlockReason(mc, hungerThreshold, healthThreshold, allowWhileGliding, minRiseSpeed);
            FOElytraLog.detail("\u8fdb\u98df\u68c0\u67e5\uff1a\u9965\u997f %d\uff08\u9608\u503c %d\uff09\uff5c\u8840\u91cf %.1f\uff08\u9608\u503c %.1f\uff09\uff5c\u722c\u5347 %.2f\uff08\u9608\u503c %.2f\uff09\uff5c\u6ed1\u7fd4 %s\uff5c\u624b\u4e0a %s\uff5c\u7ed3\u8bba\uff1a%s", mc.player.getHungerManager().getFoodLevel(), hungerThreshold, Float.valueOf(mc.player.getHealth()), healthThreshold, mc.player.getVelocity().y, minRiseSpeed, mc.player.isGliding() ? "\u662f" : "\u5426", mc.player.getMainHandStack().isEmpty() ? "\u7a7a\u624b" : mc.player.getMainHandStack().getName().getString(), reason == null ? "\u8be5\u5403\u5c31\u5403" : "\u4e0d\u5403\uff08" + reason + "\uff09");
        }
        if (mc.player.isUsingItem()) {
            return;
        }
        if (!this.shouldEat(mc, hungerThreshold, healthThreshold, allowWhileGliding, minRiseSpeed)) {
            return;
        }
        int slot = this.findFood(mc, foodWhitelist, foodPriority);
        if (slot < 0) {
            return;
        }
        this.previousSlot = mc.player.getInventory().getSelectedSlot();
        InvHelper.selectSlot(slot);
        mc.options.useKey.setPressed(true);
        this.eating = true;
        this.startWait = 0;
        this.releaseWait = 0;
        if (!this.baritonePaused) {
            this.baritonePaused = true;
            try {
                BaritoneHook.pause();
                FOElytraLog.info("\u8fdb\u98df\u4e2d\uff1a\u6682\u505c Baritone", new Object[0]);
            }
            catch (Throwable t) {
                FOElytraLog.warn("\u8fdb\u98df\u65f6\u6682\u505c Baritone \u5931\u8d25\uff1a%s", String.valueOf(t));
            }
        }
    }

    private boolean shouldEat(MinecraftClient mc, int hungerThreshold, double healthThreshold, boolean allowWhileGliding, double minRiseSpeed) {
        return this.eatBlockReason(mc, hungerThreshold, healthThreshold, allowWhileGliding, minRiseSpeed) == null;
    }

    private String eatBlockReason(MinecraftClient mc, int hungerThreshold, double healthThreshold, boolean allowWhileGliding, double minRiseSpeed) {
        boolean gliding = mc.player.isGliding();
        if (gliding) {
            if (!allowWhileGliding) {
                return "\u5173\u4e86\u300c\u98de\u884c\u4e2d\u8fdb\u98df\u300d";
            }
            if (mc.player.getVelocity().y < minRiseSpeed) {
                return "\u6ed1\u7fd4\u4e2d\u4f46\u6ca1\u5728\u722c\u5347";
            }
        } else if (!mc.player.isOnGround()) {
            return "\u5728\u7a7a\u4e2d\uff08\u6ca1\u6ed1\u7fd4\u4e5f\u6ca1\u843d\u5730\uff09";
        }
        int food = mc.player.getHungerManager().getFoodLevel();
        float health = mc.player.getHealth();
        if (food < hungerThreshold) {
            return null;
        }
        if ((double)health < healthThreshold && food < 20) {
            return null;
        }
        return "\u4e0d\u997f\u4e5f\u6ca1\u4f4e\u4e8e\u8840\u91cf\u7ebf";
    }

    private int findFood(MinecraftClient mc, List<Item> whitelist, List<Item> priority) {
        ItemStack s;
        int i;
        int slot;
        if (priority != null && !priority.isEmpty() && (slot = FoodPriority.findSlotByPriority(mc, priority)) >= 0) {
            ItemStack s2 = mc.player.getInventory().getStack(slot);
            FOElytraLog.detail("\u81ea\u52a8\u8fdb\u98df\uff1a\u6309\u4f18\u5148\u7ea7\u9009\u4e86 %s\uff08\u7b2c %d \u683c\uff0cx%d\uff09", s2.getName().getString(), slot + 1, s2.getCount());
            return slot;
        }
        for (i = 0; i < 9; ++i) {
            s = mc.player.getInventory().getStack(i);
            if (!this.isFood(s, whitelist)) continue;
            return i;
        }
        for (i = 9; i < 36; ++i) {
            s = mc.player.getInventory().getStack(i);
            if (!this.isFood(s, whitelist)) continue;
            int empty = InvHelper.findEmptyHotbarSlot();
            if (empty < 0) {
                return -1;
            }
            InvHelper.moveInvToHotbar(i, empty);
            return empty;
        }
        return -1;
    }

    private boolean isFood(ItemStack s, List<Item> whitelist) {
        if (s.isEmpty()) {
            return false;
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return ItemHelper.isFood(s);
        }
        return whitelist.contains(s.getItem());
    }

    public void stop() {
        this.release();
        this.eating = false;
        this.startWait = 0;
        this.releaseWait = 0;
    }

    private void release() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.useKey.setPressed(false);
        if (this.previousSlot >= 0 && mc.player != null) {
            InvHelper.selectSlot(this.previousSlot);
        }
        this.previousSlot = -1;
        this.eating = false;
        if (this.baritonePaused) {
            this.baritonePaused = false;
            try {
                BaritoneHook.resume();
                FOElytraLog.info("\u8fdb\u98df\u7ed3\u675f\uff1a\u6062\u590d Baritone", new Object[0]);
            }
            catch (Throwable t) {
                FOElytraLog.warn("\u8fdb\u98df\u540e\u6062\u590d Baritone \u5931\u8d25\uff1a%s", String.valueOf(t));
            }
        }
    }
}

