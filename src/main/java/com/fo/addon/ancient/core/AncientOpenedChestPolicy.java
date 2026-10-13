package com.fo.addon.ancient.core;

/**
 * 「开过的箱子」判定策略（客户端侧 best-effort）。
 *
 * <p>原理：原版服务端在区块数据包里会带上箱子的 {@code LootTable} 字段（{@code WorldChunk
 * getPackedBlockEntityNbt} → {@code createNbtWithIdentifyingData}，含完整 writeData 输出）；
 * 玩家开箱生成战利品时，{@code LootableInventory.generateLoot} 会先 {@code setLootTable(null)}。
 * 因此「（重新）加载后的客户端块实体里 {@code lootTable == null}」意味着这个箱子已经开过。
 *
 * <p><b>前提</b>是服务端确实原样下发该字段。部分服务端/插件会剥离 NBT —— 那样所有箱子都是
 * null，无法区分「已开」和「被剥离」。为避免灾难性的「全部误判成已开」，采用自适应策略：
 * 只有先确认「至少见过一个 {@code lootTable} 非空的箱子」（即服务端确实在下发）之后，
 * 才允许凭 null 判「已开」；确认之前一律判「判定不了」，宁可不删。
 */
public final class AncientOpenedChestPolicy {

    /** 判定结果。 */
    public enum Decision {
        /** 箱子还没开过（lootTable 字段还在）。 */
        UNOPENED,
        /** 确认已开过（字段被服务端置空，且我们确认服务端会下发该字段）。 */
        OPENED,
        /** 判定不了（服务端不下发该字段或数据不可得），保留标记。 */
        UNKNOWN
    }

    private AncientOpenedChestPolicy() {
    }

    /**
     * @param serverSyncsLootTable 是否已确认服务端会同步 LootTable 字段（见过至少一个非空的）
     * @param lootTablePresent     该箱子块实体里的 lootTable 字段是否非空
     */
    public static Decision decide(boolean serverSyncsLootTable, boolean lootTablePresent) {
        if (lootTablePresent) {
            return Decision.UNOPENED;
        }
        return serverSyncsLootTable ? Decision.OPENED : Decision.UNKNOWN;
    }
}
