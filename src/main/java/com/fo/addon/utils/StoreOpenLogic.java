package com.fo.addon.utils;

/**
 * 存沙/补给开盒状态机决策（纯逻辑，可单测）。V4.14 新增。
 *
 * 修复根因（对齐 miku AutoMiner.handleStoringItems 的成熟模式）：
 * 旧逻辑开盒等 40 次没开就回退 MINING，背包仍满立刻又 goToStore →
 * stop()+moveTo() 重新指路 + 转头开盒 → 又失败 → 死循环 =
 * "全程一直自转" 且 "存沙永远存不进去"。
 * 新逻辑：超时后原地 RETRY_OPEN（不重新指路、不回 MINING），
 * 只有连续失败达到上限才 GIVE_UP（由模块决定换盒/回挖矿）。
 *
 * 同时把"开盒后等服务器同步内容"独立成 SYNC_WAIT 动作：
 * 旧 storeTickCounter 永不重置，第二次开盒会跳过同步等待，
 * 在盒槽数据未同步时读槽误判。新流程每次开盒由模块重置计数。
 */
public final class StoreOpenLogic {

    public enum Action {
        SYNC_WAIT,   // 界面已开，等服务器同步盒内物品（syncTicks < syncWait）
        PROCEED,     // 界面已开且同步完成，执行存沙/补给
        WAIT_OPEN,   // 已发开盒包，等待界面打开（未超时）
        RETRY_OPEN,  // 开盒超时（或刚进入状态），原地再发一次开盒交互
        GIVE_UP      // 连续失败达到上限，放弃该盒（模块决定换盒/回挖矿）
    }

    private StoreOpenLogic() {
    }

    /**
     * @param screenOpen  潜影盒界面是否已打开
     * @param syncTicks   界面打开后已等待的同步 tick 数（每次开盒前由模块重置为 0）
     * @param syncWait    需要同步的 tick 数（如 3）
     * @param waitingOpen 是否已发出开盒交互、正在等界面
     * @param waitTicks   等待界面打开已经过的 tick 数
     * @param waitTimeout 等界面打开的超时 tick 数（如 40）
     * @param failCount   此前已连续开盒失败次数
     * @param failLimit   连续失败上限，达到后 GIVE_UP
     */
    public static Action decide(boolean screenOpen, int syncTicks, int syncWait,
                                boolean waitingOpen, int waitTicks, int waitTimeout,
                                int failCount, int failLimit) {
        if (screenOpen) {
            return syncTicks < syncWait ? Action.SYNC_WAIT : Action.PROCEED;
        }
        if (waitingOpen && waitTicks <= waitTimeout) {
            return Action.WAIT_OPEN;
        }
        // 超时（或首次进入还没发过开盒包）：失败次数到上限才放弃，否则原地重试
        // failCount 是"此前"的失败次数，本次超时算第 failCount+1 次
        if (waitingOpen && failCount + 1 >= failLimit) {
            return Action.GIVE_UP;
        }
        return Action.RETRY_OPEN;
    }
}
