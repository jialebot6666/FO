package com.fo.addon.ancient.core;

/** 古城战利品搜索的游戏版本（影响战利品随机子系统的掷骰逻辑）。 */
public enum GameVersionOption {
    BELOW_26_1("< 26.1"),
    FROM_26_1(">= 26.1");

    private final String label;

    GameVersionOption(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
