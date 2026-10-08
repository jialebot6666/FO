package com.fo.addon.elytra.core;

public enum TaskStatus {
    IDLE("空闲"),
    RUNNING("运行中"),
    DONE("完成"),
    FAILED("失败");

    private final String label;

    TaskStatus(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}

