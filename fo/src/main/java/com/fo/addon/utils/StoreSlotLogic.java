package com.fo.addon.utils;

/**
 * 存沙盒槽位同步判满（纯逻辑，可单测）。V4.14 新增。
 *
 * 借鉴 miku AutoMiner.findShulkerSlotForItem 的同步判满：
 * 搬沙前先读盒槽快照，当场判定"还能不能装下一组沙"，满了立即换盒，
 * 不再依赖旧的"点击后等 20 tick 卡住检测"慢机制。
 *
 * 与 miku 的关键区别（修 miku 的误存 bug）：只认沙。
 * 非沙槽位一律视为占用，绝不把沙往非沙物品槽上堆，
 * 也绝不允许把非沙物品存进盒子（模块侧只挑沙来调用本逻辑）。
 */
public final class StoreSlotLogic {

    private StoreSlotLogic() {
    }

    /**
     * 在盒槽快照中为" incoming 个沙"找目标槽。
     *
     * @param counts    每个盒槽当前数量（0 = 空槽）
     * @param isSand    每个盒槽当前是否沙（空槽填 false）
     * @param maxStack  沙的最大堆叠数（64）
     * @param incoming  本次要存入的数量
     * @return 目标槽下标；-1 = 盒子对沙已满（模块据此立即换盒）
     */
    public static int findSandTargetSlot(int[] counts, boolean[] isSand, int maxStack, int incoming) {
        int firstEmpty = -1;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) {
                if (firstEmpty == -1) firstEmpty = i;
                continue;
            }
            // 只往沙槽上堆叠，且必须装得下整组（避免 QUICK_MOVE 部分移动造成误判）
            if (isSand[i] && counts[i] + incoming <= maxStack) {
                return i;
            }
        }
        return firstEmpty;
    }
}
