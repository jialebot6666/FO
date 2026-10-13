package com.fo.addon.pathing;

import com.fo.addon.utils.Debug;

public class PathManagers {
    private static IPathManager instance = new NopPathManager();

    static {
        // ⚠️ 整段（含 isAvailable 调用）都必须包在 try 里，不能只包构造。
        // V6.33 修复：BaritonePathManager 自 V6.33 起直接引用编译期 Baritone 类型
        // （字段 IBaritone、方法签名里的 Settings.Setting<T>），JVM 校验该类时会
        // 尝试加载这些类型。玩家没装 Baritone 时，调用静态方法 isAvailable()
        // 就可能抛 NoClassDefFoundError —— 旧版纯反射实现不会（类签名里没有 Baritone 类型）。
        // 若 try 从 if 之后才开始，这个 Error 会绕过 catch 变成 ExceptionInInitializerError，
        // 导致 PathManagers 整个类不可用，进而炸掉所有调用 PathManagers.get() 的模块
        // （鞘翅采集 / 自动挖矿 / 自动挖沙），而不是像设计意图那样降级到 NopPathManager。
        try {
            if (BaritonePathManager.isAvailable()) {
                instance = new BaritonePathManager();
                Debug.info("[FO] Baritone 已连接，防卡住寻路可用。");
                Debug.chat("[FO] Baritone 已连接，防卡住寻路可用。");
            } else {
                Debug.info("[FO] 未检测到 Baritone，防卡住寻路不可用。");
                Debug.chat("[FO] 未检测到 Baritone，防卡住寻路不可用。");
            }
        } catch (Throwable e) {
            // 最常见是 NoClassDefFoundError：玩家没装 Baritone，或装的版本与编译期 API 不匹配。
            // 此时静默降级到 NopPathManager——只关掉「防卡住寻路」，其余模块照常工作。
            Debug.info("[FO] Baritone 初始化失败: " + e);
            Debug.chat("[FO] Baritone 初始化失败（未安装或版本不匹配），防卡住寻路不可用: " + e);
        }
    }

    public static IPathManager get() {
        return instance;
    }
}