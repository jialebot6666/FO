package com.fo.addon.elytra.core;

/**
 * 背包满时「丢一个格子腾位」的选格与槽位号换算（纯逻辑，不依赖 MC 运行时，可单元测试）。
 *
 * <h2>为什么槽位号换算必须单独测</h2>
 * 丢东西用的是 {@code clickSlot(syncId, rawSlot, 1, THROW, player)}，这里的 {@code rawSlot}
 * 是 <b>玩家自身界面（PlayerScreenHandler）的原始槽位号</b>，和 {@code PlayerInventory} 的下标<b>不是一回事</b>：
 * <ul>
 *   <li>背包下标 0..8（快捷栏）→ 原始槽位 <b>36..44</b></li>
 *   <li>背包下标 9..35（背包）→ 原始槽位 <b>9..35</b>（这一段刚好相同）</li>
 * </ul>
 * 算错一格就会把旁边的东西丢掉（或者更糟：在开着箱子时点到箱子那半边）。所以这里单独抽出来钉死。
 *
 * <p>另外：只有在<b>没有开任何容器</b>的时候才能用这套映射 —— 开着容器时同一批原始槽位号指向别的东西。
 * 调用方必须自己确认当前是 {@code playerScreenHandler} 才能动手。</p>
 */
public final class FreeSlotLogic {
    /** 背包（含快捷栏）总格数。 */
    public static final int INVENTORY_SIZE = 36;
    /** 快捷栏格数。 */
    public static final int HOTBAR_SIZE = 9;

    private FreeSlotLogic() {
    }

    /**
     * 背包下标（0..35）→ 玩家自身界面的原始槽位号；越界返回 -1。
     */
    public static int rawPlayerSlotId(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= INVENTORY_SIZE) return -1;
        return inventoryIndex < HOTBAR_SIZE ? 36 + inventoryIndex : inventoryIndex;
    }

    /** 0..35 里还有没有空格子（有就不用丢东西了）。 */
    public static boolean hasEmptySlot(boolean[] isEmpty) {
        if (isEmpty == null) return false;
        for (boolean e : isEmpty) {
            if (e) return true;
        }
        return false;
    }

    /**
     * 挑一个可以丢掉的格子腾位；挑不到返回 -1。
     *
     * <p>顺序：<b>先背包（9..35），后快捷栏（0..8）</b> —— 尽量不动快捷栏的排布。
     * 跳过：空格子、受保护的物品（镐/剑/食物/图腾/烟花/鞘翅/末影箱/潜影盒等）、以及<b>当前手持的那一格</b>。</p>
     *
     * @param isEmpty        长度 36，每格是否为空
     * @param isProtected    长度 36，每格是否受保护（不可丢）
     * @param heldHotbarSlot 当前手持的快捷栏下标（0..8），-1 表示不保护
     */
    public static int pickDroppableSlot(boolean[] isEmpty, boolean[] isProtected, int heldHotbarSlot) {
        if (isEmpty == null || isProtected == null) return -1;
        int slot = scan(isEmpty, isProtected, heldHotbarSlot, HOTBAR_SIZE, INVENTORY_SIZE);
        if (slot >= 0) return slot;
        return scan(isEmpty, isProtected, heldHotbarSlot, 0, HOTBAR_SIZE);
    }

    private static int scan(boolean[] isEmpty, boolean[] isProtected, int heldHotbarSlot, int from, int to) {
        int limit = Math.min(to, Math.min(isEmpty.length, isProtected.length));
        for (int i = Math.max(0, from); i < limit; i++) {
            if (isEmpty[i]) continue;
            if (isProtected[i]) continue;
            if (i == heldHotbarSlot) continue;   // 别把手里的东西扔了
            return i;
        }
        return -1;
    }
}
