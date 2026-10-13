package com.fo.addon.utils;

/**
 * V4.49 对齐 misaka x0275 拾取完成决策（case 0/1/2）：
 * 拾取潜影盒完成 → 立即检测背包满盒，满盒先放末影箱换空盒（工作台延后）；未满盒才挖工作台；
 * 拾取末影箱完成（换盒链结束）→ 有工作台待挖先挖工作台，否则回挖矿；
 * 拾取工作台完成 → 直接回挖矿。
 * 纯决策表，不依赖 MC 运行时，可单元测试。
 */
public class PickupNextLogic {
    /** 刚拾取的掉落物类型（与 AutoMining.PickupTarget 同名映射） */
    public enum PickupKind {
        SHULKER_BOX,
        CRAFTING_TABLE,
        ENDER_CHEST
    }

    /** 拾取完成后下一步状态 */
    public enum PickupNext {
        /** 回挖矿 */
        MINING,
        /** 挖工作台 */
        MINE_CRAFTING_TABLE,
        /** 放末影箱换空盒（满盒优先，工作台延后） */
        PLACE_ENDER_CHEST
    }

    public static PickupNext decide(PickupKind done, boolean hasFullShulker, boolean pendingTable) {
        return switch (done) {
            case SHULKER_BOX ->
                // misaka case 0：满盒 → PLACING_ENDER_CHEST；否则有工作台先挖工作台，再否则回挖矿
                hasFullShulker ? PickupNext.PLACE_ENDER_CHEST
                    : (pendingTable ? PickupNext.MINE_CRAFTING_TABLE : PickupNext.MINING);
            case ENDER_CHEST ->
                // misaka case 2：末影箱换盒链完成 → 有工作台先挖工作台，否则回挖矿
                pendingTable ? PickupNext.MINE_CRAFTING_TABLE : PickupNext.MINING;
            case CRAFTING_TABLE ->
                // misaka case 1：工作台拾取完 → 直接回挖矿
                PickupNext.MINING;
        };
    }
}
