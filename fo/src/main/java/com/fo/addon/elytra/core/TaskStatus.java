package com.fo.addon.elytra.core;
public enum TaskStatus {
    IDLE("空闲"),
    RUNNING("进行中"),
    DONE("已完成"),
    FAILED("失败");

    public final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString，必须覆写否则显示英文枚举名） */
    @Override
    public String toString() {
        return label;
    }

}
