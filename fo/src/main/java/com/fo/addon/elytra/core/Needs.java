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
        return this.fireworkStacks <= 0 && this.xpBottles <= 0 && this.food <= 0 && this.totems <= 0 && this.elytra <= 0;
    }

    public boolean needsAnyItem() {
        return this.fireworkStacks > 0 || this.xpBottles > 0 || this.food > 0 || this.totems > 0 || this.elytra > 0;
    }

    public String toString() {
        return "\u70df\u82b1 " + this.fireworkStacks + " \u7ec4 / \u7ecf\u9a8c\u74f6 " + this.xpBottles + " / \u98df\u7269 " + this.food + " / \u56fe\u817e " + this.totems + " / \u9798\u7fc5 " + this.elytra;
    }
}

