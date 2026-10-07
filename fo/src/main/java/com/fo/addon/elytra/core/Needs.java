package com.fo.addon.elytra.core;
public final class Needs {
    public int fireworkStacks;
    public int xpBottles;
    public int food;
    public int totems;
    public int elytra;
    public Needs() {
    }
    public Needs(int fireworkStacks, int xpBottles, int food, int totems, int elytra) {
        this.fireworkStacks = fireworkStacks;
        this.xpBottles = xpBottles;
        this.food = food;
        this.totems = totems;
        this.elytra = elytra;
    }
    public boolean isEmpty() {
        return fireworkStacks <= 0 && xpBottles <= 0 && food <= 0 && totems <= 0 && elytra <= 0;
    }
    public boolean needsAnyItem() {
        return fireworkStacks > 0 || xpBottles > 0 || food > 0 || totems > 0 || elytra > 0;
    }
    @Override
    public String toString() {
        return "烟花 " + fireworkStacks + " 组 / 经验瓶 " + xpBottles + " / 食物 " + food
            + " / 图腾 " + totems + " / 鞘翅 " + elytra;
    }
}
