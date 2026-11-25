package com.fo.addon.elytra.hell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HellFlightLogic {
    public static final int DEFAULT_FIREWORK_STACKS = 23;
    public static final int DEFAULT_XP_STACKS = 3;
    public static final int DEFAULT_FOOD_STACKS = 1;
    public static final int DEFAULT_TOTEMS = 2;
    public static final int LAND_FIREWORKS = 192;
    public static final int LAND_XP = 32;
    public static final int LAND_FOOD = 16;
    public static final double ARRIVAL_DISTANCE = 3801.0;
    public static final double DEFAULT_FIREWORK_SPEED = 1.5;

    private HellFlightLogic() {
    }

    public enum Food {
        GOLDEN_CARROT("minecraft:golden_carrot", "金胡萝卜"),
        GOLDEN_APPLE("minecraft:golden_apple", "金苹果"),
        ENCHANTED_GOLDEN_APPLE("minecraft:enchanted_golden_apple", "附魔金苹果"),
        COOKED_BEEF("minecraft:cooked_beef", "牛排"),
        COOKED_PORKCHOP("minecraft:cooked_porkchop", "熟猪排"),
        COOKED_CHICKEN("minecraft:cooked_chicken", "熟鸡肉");

        private final String itemId;
        private final String label;

        Food(String itemId, String label) {
            this.itemId = itemId;
            this.label = label;
        }

        public String itemId() {
            return itemId;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Vec(double x, double y, double z) {
    }

    public record Needs(int fireworks, int xp, int food, int totems) {
        public boolean any() {
            return fireworks > 0 || xp > 0 || food > 0 || totems > 0;
        }
    }

    public record BoxCounts(int fireworks, int xp, int food, int totems) {
    }

    public static Needs needs(
        int fireworks,
        int xp,
        int food,
        int totems,
        int fireworkStacks,
        int xpStacks,
        int foodStacks,
        int totemTarget
    ) {
        return new Needs(
            Math.max(0, fireworkStacks * 64 - fireworks),
            Math.max(0, xpStacks * 64 - xp),
            Math.max(0, foodStacks * 64 - food),
            Math.max(0, totemTarget - totems)
        );
    }

    public static boolean shouldLandForSupply(int fireworks, int xp, int food, int totems, boolean elytraNearlyBroken) {
        return fireworks < LAND_FIREWORKS || xp < LAND_XP || food < LAND_FOOD || totems < 1 || elytraNearlyBroken;
    }

    public static boolean arrived(double distanceToTarget) {
        return distanceToTarget < ARRIVAL_DISTANCE;
    }

    public static boolean lowHealthExit(double health, int totems) {
        return health < 3.5 && totems <= 1;
    }

    public static boolean shouldUseFirework(
        double speedSquared,
        double threshold,
        boolean boosted,
        boolean cooldownReady,
        boolean descendingNearTarget,
        boolean farFromTarget
    ) {
        if (!cooldownReady || boosted) return false;
        double thresholdSquared = threshold * threshold;
        return descendingNearTarget && farFromTarget && speedSquared < thresholdSquared;
    }

    public static int hotbarScreenSlot(int containerRows, int hotbarIndex) {
        return containerRows * 9 + 27 + hotbarIndex;
    }

    public static int inventoryScreenSlot(int containerRows, int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex < 9) return hotbarScreenSlot(containerRows, inventoryIndex);
        return containerRows * 9 + inventoryIndex - 9;
    }

    public static List<Integer> chooseSupplyBoxes(Needs need, List<BoxCounts> candidates) {
        List<Integer> selected = new ArrayList<>();
        int remainingFireworks = need.fireworks();
        int remainingXp = need.xp();
        int remainingFood = need.food();
        int remainingTotems = need.totems();
        while (remainingFireworks > 0 || remainingXp > 0 || remainingFood > 0 || remainingTotems > 0) {
            int bestIndex = -1;
            int bestScore = 0;
            for (int index = 0; index < candidates.size(); index++) {
                if (selected.contains(index) || candidates.get(index) == null) continue;
                BoxCounts counts = candidates.get(index);
                int score = Math.min(counts.fireworks(), remainingFireworks)
                    + Math.min(counts.xp(), remainingXp)
                    + Math.min(counts.food(), remainingFood)
                    + Math.min(counts.totems(), remainingTotems);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = index;
                }
            }
            if (bestIndex < 0) break;
            selected.add(bestIndex);
            BoxCounts counts = candidates.get(bestIndex);
            remainingFireworks = Math.max(0, remainingFireworks - counts.fireworks());
            remainingXp = Math.max(0, remainingXp - counts.xp());
            remainingFood = Math.max(0, remainingFood - counts.food());
            remainingTotems = Math.max(0, remainingTotems - counts.totems());
        }
        return selected;
    }

    public static float yawTo(Vec from, Vec to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float pitchTo(Vec from, Vec to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    public static boolean landingColumnSafe(
        boolean groundSolid,
        boolean groundNotLava,
        boolean fifteenAir,
        boolean nineByNineAir,
        boolean noHostileMob,
        boolean notBlacklisted
    ) {
        return groundSolid && groundNotLava && fifteenAir && nineByNineAir && noHostileMob && notBlacklisted;
    }

    public static String formatDistance(double distance) {
        if (distance >= 1000) return String.format(Locale.CHINA, "%.1f 千格", distance / 1000.0);
        return String.format(Locale.CHINA, "%.0f 格", distance);
    }

    public static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        if (seconds >= 3600) return String.format(Locale.CHINA, "%d 小时 %d 分", seconds / 3600, seconds % 3600 / 60);
        if (seconds >= 60) return String.format(Locale.CHINA, "%d 分 %d 秒", seconds / 60, seconds % 60);
        return seconds + " 秒";
    }
}
