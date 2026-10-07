package com.fo.addon.elytra.core;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
public final class EatController {
    private static final int START_TIMEOUT = 8;
    private static final int RELEASE_GRACE = 3;
    private boolean eating;
    private int startWait;
    private int releaseWait;
    private int previousSlot = -1;
    public boolean isEating() {
        return eating;
    }
    public void tick(boolean enabled, int hungerThreshold, double healthThreshold,
                     boolean allowWhileGliding, double minRiseSpeed,
                     java.util.List<net.minecraft.item.Item> foodWhitelist) {
        tick(enabled, hungerThreshold, healthThreshold, allowWhileGliding, minRiseSpeed, foodWhitelist, null);
    }
    public void tick(boolean enabled, int hungerThreshold, double healthThreshold,
                     boolean allowWhileGliding, double minRiseSpeed,
                     java.util.List<net.minecraft.item.Item> foodWhitelist,
                     java.util.List<net.minecraft.item.Item> foodPriority) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            stop();
            return;
        }
        if (!enabled) {
            stop();
            return;
        }
        if (eating) {
            if (mc.player.isUsingItem()) {
                startWait = 0;
                releaseWait = 0;
                return;
            }
            if (startWait < START_TIMEOUT) {
                startWait++;
                return;
            }
            if (releaseWait++ >= RELEASE_GRACE) {
                release();
            }
            return;
        }
        if (mc.player.isUsingItem()) return;
        if (!shouldEat(mc, hungerThreshold, healthThreshold, allowWhileGliding, minRiseSpeed)) return;
        int slot = findFood(mc, foodWhitelist, foodPriority);
        if (slot < 0) return;
        previousSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
        eating = true;
        startWait = 0;
        releaseWait = 0;
    }
    private boolean shouldEat(MinecraftClient mc, int hungerThreshold, double healthThreshold,
                              boolean allowWhileGliding, double minRiseSpeed) {
        boolean gliding = mc.player.isGliding();
        if (gliding) {
            if (!allowWhileGliding) return false;
            if (mc.player.getVelocity().y < minRiseSpeed) return false;
        } else if (!mc.player.isOnGround()) {
            return false;
        }
        int food = mc.player.getHungerManager().getFoodLevel();
        float health = mc.player.getHealth();
        return food < hungerThreshold || (health < healthThreshold && food < 20);
    }
    private int findFood(MinecraftClient mc, java.util.List<net.minecraft.item.Item> whitelist,
                         java.util.List<net.minecraft.item.Item> priority) {
        if (priority != null && !priority.isEmpty()) {
            int slot = FoodPriority.findSlotByPriority(mc, priority);
            if (slot >= 0) {
                ItemStack s = mc.player.getInventory().getStack(slot);
                FOElytraLog.detail("自动进食：按优先级选了 %s（第 %d 格，x%d）",
                    s.getName().getString(), slot + 1, s.getCount());
                return slot;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isFood(s, whitelist)) return i;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isFood(s, whitelist)) {
                int empty = InvHelper.findEmptyHotbarSlot();
                if (empty < 0) return -1;
                InvHelper.moveInvToHotbar(i, empty);
                return empty;
            }
        }
        return -1;
    }
    private boolean isFood(ItemStack s, java.util.List<net.minecraft.item.Item> whitelist) {
        if (s.isEmpty()) return false;
        if (whitelist == null || whitelist.isEmpty()) return ItemHelper.isFood(s);
        return whitelist.contains(s.getItem());
    }
    public void stop() {
        release();
        eating = false;
        startWait = 0;
        releaseWait = 0;
    }
    private void release() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.useKey.setPressed(false);
        if (previousSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        eating = false;
    }
}
