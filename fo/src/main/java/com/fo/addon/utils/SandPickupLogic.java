package com.fo.addon.utils;

/**
 * Nuker 模式下沙子掉落物拾取决策（纯逻辑，可单测）。
 *
 * 行为：
 * - 正在拾取（pickingUp=true）：搜索半径内还有沙掉落物就继续捡（KEEP），没有了就停掉拾取去挖沙（STOP_AND_MINE）；
 * - 未在拾取：有沙掉落物就开捡（START），没有就直接挖沙（MINE）。
 */
public final class SandPickupLogic {

    public enum Action {
        START,         // 开始用 Baritone pickup 捡掉落物
        KEEP,          // 保持拾取，继续捡
        STOP_AND_MINE, // 掉落物捡完了，停掉拾取，恢复挖沙
        MINE           // 没有掉落物，直接挖沙
    }

    private SandPickupLogic() {
    }

    public static Action decide(boolean hasSandDrops, boolean pickingUp) {
        if (pickingUp) {
            return hasSandDrops ? Action.KEEP : Action.STOP_AND_MINE;
        }
        return hasSandDrops ? Action.START : Action.MINE;
    }
}
