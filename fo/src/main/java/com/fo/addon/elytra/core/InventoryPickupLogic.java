package com.fo.addon.elytra.core;

/**
 * 「东西到底有没有真的进背包」的槽位快照判定（纯逻辑，不依赖 MC 运行时，可单元测试）。
 *
 * <h2>为什么不能只看数量</h2>
 * 只看「目标物品总数变多了没有」在两种情况下会漏判：
 * <ul>
 *   <li>捡到的盒子<b>叠放</b>进了身上本来就有的那一摞 —— 总数确实 +1，但槽位没变；</li>
 *   <li>盒子和别的物品<b>换了格子</b> —— 总数一点没变。</li>
 * </ul>
 * 反过来只看槽位也不行（叠放时槽位没变）。所以两个信号要<b>取或</b>：
 * <b>总数变多</b> 或 <b>某个原本没有的槽位出现了</b>，只要命中一个就算捡到了。
 */
public final class InventoryPickupLogic {
    private InventoryPickupLogic() {
    }

    /** 一份背包快照：每格的计数 + 总数。 */
    public record Snapshot(int[] perSlot, int total) {
    }

    /** 按「每格计数」拍快照（数组会被复制，之后改动原数组不影响快照）。 */
    public static Snapshot of(int[] perSlot) {
        if (perSlot == null) return new Snapshot(new int[0], 0);
        int total = 0;
        for (int c : perSlot) total += c;
        return new Snapshot(perSlot.clone(), total);
    }

    /**
     * 是否捡到了新的目标物品。
     *
     * <p>命中任意一条即为真：总数变多（兼容叠放），或某个原本为空的槽位现在有了（兼容换格子）。</p>
     */
    public static boolean pickedUp(Snapshot before, Snapshot after) {
        if (before == null || after == null) return false;
        if (after.total() > before.total()) return true;
        return newSlot(before, after) >= 0;
    }

    /**
     * 新出现目标物品的槽位下标；没有则返回 -1。
     *
     * <p>背包满需要腾位时，靠这个知道盒子最后进了哪一格。</p>
     */
    public static int newSlot(Snapshot before, Snapshot after) {
        if (before == null || after == null) return -1;
        int n = Math.min(before.perSlot().length, after.perSlot().length);
        for (int i = 0; i < n; i++) {
            if (before.perSlot()[i] == 0 && after.perSlot()[i] > 0) return i;
        }
        return -1;
    }

    /** 快照里一共有几个格子是「有目标物品」的。 */
    public static int occupiedSlots(Snapshot snapshot) {
        if (snapshot == null) return 0;
        int n = 0;
        for (int c : snapshot.perSlot()) {
            if (c > 0) n++;
        }
        return n;
    }
}
