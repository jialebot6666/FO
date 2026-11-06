package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

public interface IPathManager {
    boolean isPathing();

    void stop();

    void moveTo(BlockPos pos, boolean ignoreY);

    /**
     * 走到目标「附近」就算到（V6.7p20）：GoalNear(pos, range)。
     *
     * <p>为什么拉珍珠端不能用 {@link #moveTo(BlockPos, boolean)} 的 GoalXZ（ignoreY）：
     * 那个目标只认 XZ、完全不管 Y，Baritone 为了到那个 XZ 会一路挖穿挡路的方块
     * （`allowBreak` 默认开），在洞穴系统里就可能挖到基岩层；而且它的「到没到」只看 XZ，
     * 会和我们 3D 的点击判定打架。默认退化为 moveTo(pos, false)。
     */
    default void moveToNear(BlockPos pos, int range) {
        moveTo(pos, false);
    }

    /**
     * 临时开关 Baritone 的「允许挖掘」（V6.7p20）。
     *
     * <p>拉珍珠端只是走过去点个门，不需要破坏任何方块；关掉之后路被挡住会直接
     * 无路可走并超时放弃，而不是把人家基地挖穿。默认空实现（没有 Baritone 时无影响）。
     */
    default void setAllowBreak(boolean allow) {
    }

    /**
     * 精确寻路到目标格内（V4.19）：GoalGetToBlock 只走到目标格相邻格就停（玩家中心
     * 进不了目标格，站盒子旁会留 2-3 格空隙），这里改用 GoalNear(pos, 0.5)——
     * 玩家中心进入目标格 0.5 格内才算到达，真正"一格空间都不留"。
     * 默认退化为 moveTo(pos, false)，由 BaritonePathManager 覆盖为精确目标。
     */
    default void moveToPrecise(BlockPos pos) {
        moveTo(pos, false);
    }

    void mine(Block... blocks);

    /** 让 Baritone 持续走到匹配的物品掉落物旁拾取（FollowProcess.pickup） */
    void pickupItems(Predicate<ItemStack> filter);

    void protectShulkerBoxes(boolean protect);

    /** 挖掘进程是否正在工作（IMineProcess.isActive） */
    boolean isMining();

    /**
     * 应用自动挖矿需要的 Baritone 避让/挖掘设置（V4.30，移植 misaka AutoMining）：
     * 怪物避让（avoidance + 半径/系数 + 刷怪笼半径）+ 方块避让（深暗之域方块 + 刷怪笼）。
     * 实现类反射访问 BaritoneSettings；失败时静默跳过（版本差异不崩）。
     */
    void applyMiningAvoidance(boolean avoidMobs, boolean avoidBlocks);

    /** 恢复调用 applyMiningAvoidance 前备份的 Baritone 设置 */
    void resetMiningAvoidance();

    /**
     * 设置 Baritone 自动换工具（autoTool）。V4.31 方案 B：自动挖矿开启时设为 false，
     * 让 Baritone 只用当前手持工具挖掘，由 FO 自己锁定时运镐（防止 Baritone 扫热键栏
     * 选中精准采集镐挖钻石 → 掉原矿）。关闭模块时随 resetMiningAvoidance 一起恢复。
     */
    void setAutoTool(boolean enabled);

    /**
     * 设置 Baritone mineScanDroppedItems（V4.42）：mine 进程找不到新矿石时，
     * 把地面掉落物也当目标走过去捡起（兜住"挖了没捡到钻石"）。关闭模块时随 resetMiningAvoidance 恢复。
     */
    void setMineScanDroppedItems(boolean enabled);

    /**
     * 设置 Baritone mine 挖掘 Y 范围（V4.60）：minYLevelWhileMining / maxYLevelWhileMining。
     * 残骸模式限制 8~22（残骸生成区间）；不限制时 minY=6（防挖穿基岩）、maxY=256。
     * 恢复随 resetMiningAvoidance 一起处理。
     */
    void setMiningYRange(int minY, int maxY);
}