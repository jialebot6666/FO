package com.fo.addon.pathing;

import com.fo.addon.utils.Debug;

public class PathManagers {
    private static IPathManager instance = new NopPathManager();

    static {
        if (BaritonePathManager.isAvailable()) {
            try {
                instance = new BaritonePathManager();
                Debug.info("[FO] Baritone 已连接，防卡住寻路可用。");
                Debug.chat("[FO] Baritone 已连接，防卡住寻路可用。");
            } catch (Throwable e) {
                Debug.info("[FO] Baritone 初始化失败: " + e);
                Debug.chat("[FO] Baritone 初始化失败: " + e);
            }
        } else {
            Debug.info("[FO] 未检测到 Baritone，防卡住寻路不可用。");
            Debug.chat("[FO] 未检测到 Baritone，防卡住寻路不可用。");
        }
    }

    public static IPathManager get() {
        return instance;
    }
}