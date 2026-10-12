package com.fo.addon.utils;

import java.util.Map;

/**
 * 「自动偷取」判定逻辑（纯逻辑，不依赖 MC 运行时，可单元测试）。
 *
 * <p>方案来源：彗星端（Meteor Client）官方 {@code InventoryTweaks} 模块的 Auto Steal
 * ——对着真实源码（{@code src/main/java/.../misc/InventoryTweaks.java}）与本机 1.21.11-85
 * 的字节码双向核对后移植。官方那套按 {@code Item} 匹配，**认不出附魔书里附的是哪条附魔**
 * （所以它拿不到「迅捷潜行3 附魔书」）——这正是本模块要比它多出来的能力：
 * 读物品的 {@code ItemEnchantmentsComponent}，按附魔 ID + 等级判定。
 *
 * <p>官方对应实现（供对照）：
 * <pre>
 * if (stealFilter == Whitelist &amp;&amp; !stealItems.contains(item)) continue;
 * if (stealFilter == Blacklist &amp;&amp; stealItems.contains(item)) continue;
 * InvUtils.shiftClick().slotId(i);          // 整组挪进背包
 * sleep = initial ? autoStealInitDelay : delay + random(0, randomDelay);
 * </pre>
 *
 * <p><b>V6.26 起收紧容器范围</b>：官方对「开的是什么容器」完全不作判断（只要收到
 * {@code InventoryEvent} 就偷），实机反馈会把玩家自己存在潜影盒里的物资也取走。
 * 本模块改为**只偷箱子**（见 {@link #shouldScanContainer}）。
 */
public final class AncientStealLogic {

    /** 迅捷潜行附魔的注册 ID（脚本掷骰器与附魔数据用的是同一个 ID）。 */
    public static final String SWIFT_SNEAK_ID = "minecraft:swift_sneak";

    private AncientStealLogic() {
    }

    /**
     * 从「附魔 ID → 等级」映射里取迅捷潜行的等级。
     *
     * @return 等级；不是附魔书 / 没有这条附魔时返回 0
     */
    public static int swiftSneakLevel(Map<String, Integer> enchantLevels) {
        if (enchantLevels == null || enchantLevels.isEmpty()) return 0;
        Integer level = enchantLevels.get(SWIFT_SNEAK_ID);
        return level == null ? 0 : Math.max(0, level);
    }

    /**
     * 这个槽位该不该偷。
     *
     * <p>命中任一条件即偷：① 物品在「偷取物品列表」里；② 是附魔书且迅捷潜行等级达标。
     * 等级门槛为 0 表示「不识别附魔书」（只按物品列表偷）。
     *
     * @param inStealItems 槽内物品是否在「偷取物品列表」里（按 {@code Item} 匹配，与官方一致）
     * @param swiftSneak   槽内物品的迅捷潜行等级；非附魔书传 0
     * @param minLevel     设置值：0=不识别附魔书；1~3=等级 ≥ 此值才偷
     */
    public static boolean shouldSteal(boolean inStealItems, int swiftSneak, int minLevel) {
        if (inStealItems) return true;
        return minLevel > 0 && swiftSneak >= minLevel;
    }

    /**
     * 「这个容器该不该偷」的判定（V6.26 新增）。
     *
     * <p><b>只偷箱子</b>：用户把自己的物资存在潜影盒里，彗星端原版是不分容器一律偷
     * （它只看 {@code InventoryEvent}，不判开的是什么方块），实测会把潜影盒里的东西也取走。
     * 所以本模块收紧为「必须能确证打开的是箱子方块」。
     *
     * <p>这里刻意做成**保守（fail-safe）**语义：只要判断依据缺失或无法确证是箱子，
     * 就一律不偷 —— 宁可漏偷（还能手动拿），也不能误偷用户自己的储物盒。
     *
     * @param targetKnown  是否拿到了有效的「刚打开的是哪个方块」依据（准星指向未过期）
     * @param isChestBlock 该依据指向的方块是不是箱子（{@code ChestBlock} 及其子类，如陷阱箱）
     *                     —— 潜影盒、木桶、末影箱、发射器等都不算
     */
    public static boolean shouldScanContainer(boolean targetKnown, boolean isChestBlock) {
        return targetKnown && isChestBlock;
    }

    /**
     * 取出一组之后要等多少刻（对应官方 {@code getSleepTime()}，官方单位是毫秒，本模块换成刻）。
     *
     * <p>官方：{@code delay + (randomDelay > 0 ? randomInt(0, randomDelay) : 0)}。
     * 这里把随机数由调用方传入，保证纯函数、可单测。
     *
     * @param base      基础间隔（刻）
     * @param randomBound 随机上限（刻）；≤0 表示不加随机
     * @param randomValue 调用方取好的随机值（应落在 {@code [0, randomBound)}）
     */
    public static int delayTicks(int base, int randomBound, int randomValue) {
        int d = Math.max(0, base);
        if (randomBound <= 0) return d;
        return d + Math.max(0, Math.min(randomValue, randomBound - 1));
    }
}
