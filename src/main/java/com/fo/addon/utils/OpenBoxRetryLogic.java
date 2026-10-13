package com.fo.addon.utils;

/**
 * 潜影盒打开重试策略（纯逻辑，可单测）。
 * INIT_SCAN/补给/存沙开盒场景：每次尝试打开后等待结果，连续失败超过上限就跳过该盒，
 * 避免"打不开还无限转头+右键重试"导致人物抽搐/自转。
 */
public final class OpenBoxRetryLogic {

    public enum Action {
        TRY_OPEN,   // 尝试打开潜影盒
        SKIP_BOX,   // 失败次数达到上限，跳过该盒
        MOVE_TO     // 距离太远，先走过去
    }

    private OpenBoxRetryLogic() {
    }

    public static Action decide(boolean nearBox, int failCount, int failLimit) {
        if (!nearBox) return Action.MOVE_TO;
        return failCount >= failLimit ? Action.SKIP_BOX : Action.TRY_OPEN;
    }
}
