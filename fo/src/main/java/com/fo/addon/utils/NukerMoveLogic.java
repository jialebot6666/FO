package com.fo.addon.utils;

/**
 * 核爆模式没沙时的下一步动作决策（纯逻辑，可单测）。
 *
 * 原则：核爆挖掘全程开启，不掉队等待捡掉落物（掉落物靠走路经过自动拾取）。
 * - 大范围内还有沙且当前没在寻路 → 走去最近沙块，到达后核爆自动接管；
 * - 正在寻路 → 等待寻路完成（防止重复指路）；
 * - 大范围也没沙 → 等待（不动作）。
 */
public final class NukerMoveLogic {

    public enum Action {
        MOVE_TO_SAND, // 走去最近沙块
        WAIT          // 等待（正在寻路 / 大范围确实没沙）
    }

    private NukerMoveLogic() {
    }

    public static Action decide(boolean hasAnySandNear, boolean pathing) {
        if (hasAnySandNear && !pathing) return Action.MOVE_TO_SAND;
        return Action.WAIT;
    }
}
