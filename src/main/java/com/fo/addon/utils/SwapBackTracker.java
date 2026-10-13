package com.fo.addon.utils;

/**
 * 杀戮光环「切换回来」（swap-back）状态机 —— 纯逻辑，不依赖 MC 运行时，可单元测试。
 *
 * <p>语义照抄彗星端（Meteor Client）官方 {@code KillAura} 的 {@code swap-back} 实现
 * （对着 {@code meteor-client-1.21.11-85.jar} 字节码逐条核对，不是凭记忆）：
 * <ul>
 *   <li>自动切换武器时<b>仅本会话的第一次</b>记录切换前占用的快捷栏槽位；会话期间反复切武器不覆盖记录；</li>
 *   <li>攻击会话结束时（目标列表为空 / 手上不是可接受武器 / 关闭模块），
 *       若「切换回来」开关打开且本会话确实切过，则切回记录的槽位，随后状态复位；</li>
 *   <li>开关关闭时不切回，但状态同样复位 —— 即下次会话会重新记录原槽位（对齐官方语义）。</li>
 * </ul>
 *
 * <p>官方对应字节码：字段 {@code private boolean swapped;} 与 {@code private static int previousSlot;}，
 * 在自动切换分支里 {@code if (!swapped) { previousSlot = selectedSlot; swapped = true; }}，
 * 在 {@code stopAttacking()} 里 {@code if (swapBack.get() && swapped) { InvUtils.swap(previousSlot, false); swapped = false; }}；
 * {@code onActivate()} 复位、{@code onDeactivate()} 也走 {@code stopAttacking()}。
 */
public final class SwapBackTracker {
    /** 未记录任何槽位时的哨兵值（与官方 {@code previousSlot = -1} 一致）。 */
    public static final int NO_SLOT = -1;

    private int previousSlot = NO_SLOT;
    private boolean swapped;

    /**
     * 记录「切换前」占用的槽位。<b>仅本次攻击会话的首次切换生效</b>，之后重复调用不覆盖。
     *
     * @param currentSlot 当前选中的快捷栏槽位（0~8），即切武器之前玩家手上占着的槽
     */
    public void recordBeforeSwap(int currentSlot) {
        if (!swapped) {
            previousSlot = currentSlot;
            swapped = true;
        }
    }

    /** 本会话是否切过武器（即是否有槽位待切回）。 */
    public boolean hasPendingRestore() {
        return swapped;
    }

    /** 记录的待恢复槽位；未记录时为 {@link #NO_SLOT}。 */
    public int previousSlot() {
        return previousSlot;
    }

    /**
     * 攻击会话结束：判定是否应当执行切回，并复位内部状态。
     *
     * <p>无论开关是否打开都会复位 —— 开关关闭时本次不切回，但下次会话重新记录原槽位。
     * 返回槽位号（而不是布尔）是为了让调用方拿到值后再复位，避免「先复位再读」拿到哨兵值。
     *
     * @param swapBackEnabled 「切换回来」设置是否打开
     * @return 应切回的槽位；本次不需要切回时返回 {@link #NO_SLOT}
     */
    public int completeSession(boolean swapBackEnabled) {
        if (!swapped) return NO_SLOT;

        int target = (swapBackEnabled && previousSlot != NO_SLOT) ? previousSlot : NO_SLOT;
        reset();
        return target;
    }

    /** 复位为初始状态（模块激活 / 手动重置时调用，对齐官方 {@code onActivate()}）。 */
    public void reset() {
        previousSlot = NO_SLOT;
        swapped = false;
    }
}
