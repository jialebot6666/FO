package com.fo.addon.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * 直接调用 Baritone 编译期 API（不再用反射）。
 *
 * <p>2026-10-13 改写：此前全靠 {@code Class.forName} + 反射，原因是依赖的是
 * {@code baritone-meteor}（Meteor 打包版，mod id baritone-meteor）的 API jar，
 * 而实际运行时玩家装的是原版 {@code baritone}——拿不准才用反射兜着。
 * 现在改用原版 baritone 的 API jar 作为 {@code modCompileOnly} 依赖，直接写类型即可。
 *
 * <p>好处：
 * <ul>
 *   <li>编译期就能发现签名不匹配——旧反射里 {@code GoalNear(BlockPos, int)} 的 int/double
 *       写错要等到运行时才炸；</li>
 *   <li>不再需要「兼容新老包名」的 try/catch（{@code baritone.api.behavior} 与旧的
 *       {@code baritone.api.pathing.behavior} 二选一）；</li>
 *   <li>省掉 12 个 Method/Constructor/Field 字段与满屏 try-catch。</li>
 * </ul>
 *
 * <p>Baritone 缺失时（{@link #isAvailable()} 为 false），由 {@link PathManagers}
 * 回退到 {@link NopPathManager}，其余模块照常工作。
 *
 * <p>⚠️ 签名核对：本类每个方法签名都对 {@code libs/baritone-1.21.11-2026.jar}
 * 用 javap 逐条反编译确认，未凭记忆书写。升级 Baritone 时请重新核对。
 */
public class BaritonePathManager implements IPathManager {
    private final IBaritone baritone;

    /**
     * Baritone 是否在运行时可用。判据是 {@code BaritoneAPI} 这个纯 API 门面类能否加载——
     * 它存在即代表 baritone mod 已装。注意本项目对 Baritone 是 {@code modCompileOnly}，
     * 编译期有、运行时未必有，所以这个检查不可省。
     */
    public static boolean isAvailable() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public BaritonePathManager() {
        this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    @Override
    public boolean isPathing() {
        try {
            return baritone.getPathingBehavior().isPathing();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void stop() {
        try {
            baritone.getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void moveTo(BlockPos pos, boolean ignoreY) {
        try {
            Goal goal = ignoreY
                ? new GoalXZ(pos.getX(), pos.getZ())
                : new GoalGetToBlock(pos);
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void moveToPrecise(BlockPos pos) {
        try {
            // GoalNear(pos, 0)：range 是 int 整格容忍度（写成 double 编译不过）。
            // range=0 → 玩家脚所在格必须 == 目标格 =「一格空间都不留」；
            // GoalGetToBlock 只到相邻格就停，会留 2-3 格空隙。
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 0));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void mine(Block... blocks) {
        try {
            baritone.getMineProcess().mine(blocks);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void pickupItems(Predicate<ItemStack> filter) {
        try {
            baritone.getFollowProcess().pickup(filter);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isMining() {
        try {
            return baritone.getMineProcess().isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- 潜影盒保护 ----

    /** 设置 Baritone 保护潜影盒不被挖 */
    public void protectShulkerBoxes(boolean protect) {
        try {
            Settings.Setting<List<Block>> setting = BaritoneAPI.getSettings().blocksToDisallowBreaking;
            setting.value = protect ? new ArrayList<>(Arrays.asList(
                Blocks.SHULKER_BOX,
                Blocks.WHITE_SHULKER_BOX,
                Blocks.ORANGE_SHULKER_BOX,
                Blocks.MAGENTA_SHULKER_BOX,
                Blocks.LIGHT_BLUE_SHULKER_BOX,
                Blocks.YELLOW_SHULKER_BOX,
                Blocks.LIME_SHULKER_BOX,
                Blocks.PINK_SHULKER_BOX,
                Blocks.GRAY_SHULKER_BOX,
                Blocks.LIGHT_GRAY_SHULKER_BOX,
                Blocks.CYAN_SHULKER_BOX,
                Blocks.PURPLE_SHULKER_BOX,
                Blocks.BLUE_SHULKER_BOX,
                Blocks.BROWN_SHULKER_BOX,
                Blocks.GREEN_SHULKER_BOX,
                Blocks.RED_SHULKER_BOX,
                Blocks.BLACK_SHULKER_BOX
            )) : new ArrayList<>();
        } catch (Throwable ignored) {
        }
    }

    // ---- 自动挖矿避让设置（V4.30，移植 misaka AutoMining）----
    // 改设置前先备份，resetMiningAvoidance 时还原。

    private final List<SettingBackup<?>> avoidanceBackup = new ArrayList<>();

    /** 一条 Setting 的原值备份。泛型保留 Setting<T> 类型，restore() 直接赋值回 value，无需强转。 */
    private record SettingBackup<T>(Settings.Setting<T> setting, T oldValue) {
        void restore() {
            setting.value = oldValue;
        }
    }

    /** 备份某 Setting 当前值 */
    private <T> void backup(Settings.Setting<T> setting) {
        avoidanceBackup.add(new SettingBackup<>(setting, setting.value));
    }

    /** 备份某 Setting；若已备份过同一个 Setting 对象则跳过（防反复开关叠加备份） */
    private <T> void backupIfAbsent(Settings.Setting<T> setting) {
        for (SettingBackup<?> entry : avoidanceBackup) {
            if (entry.setting() == setting) return;
        }
        backup(setting);
    }

    @Override
    public void applyMiningAvoidance(boolean avoidMobs, boolean avoidBlocks) {
        try {
            applyMiningAvoidance0(avoidMobs, avoidBlocks);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void applyMiningAvoidance0(boolean avoidMobs, boolean avoidBlocks) {
        Settings s = BaritoneAPI.getSettings();

        // 最小挖掘 Y（主世界基床岩上 6 格起挖，与 misaka 一致）
        backup(s.minYLevelWhileMining);
        s.minYLevelWhileMining.value = 6;

        if (avoidMobs) {
            backup(s.avoidance);
            backup(s.mobAvoidanceRadius);
            backup(s.mobAvoidanceCoefficient);
            backup(s.mobSpawnerAvoidanceRadius);

            s.avoidance.value = true;
            s.mobAvoidanceRadius.value = 12;
            s.mobAvoidanceCoefficient.value = 5.0d;
            s.mobSpawnerAvoidanceRadius.value = 16;
        }

        if (avoidBlocks) {
            backup(s.blocksToAvoid);
            s.blocksToAvoid.value = new ArrayList<>(Arrays.asList(
                Blocks.SCULK_SENSOR,
                Blocks.SCULK,
                Blocks.SCULK_VEIN,
                Blocks.SCULK_CATALYST,
                Blocks.SCULK_SHRIEKER,
                Blocks.TRIAL_SPAWNER,
                Blocks.SPAWNER
            ));
        }
    }

    @Override
    public void resetMiningAvoidance() {
        try {
            for (SettingBackup<?> entry : avoidanceBackup) {
                entry.restore();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            avoidanceBackup.clear();
        }
    }

    @Override
    public void setAutoTool(boolean enabled) {
        try {
            Settings.Setting<Boolean> setting = BaritoneAPI.getSettings().autoTool;
            backupIfAbsent(setting);
            setting.value = enabled;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** V4.42：mine 找不到新矿时把地面掉落物当目标捡起（兜住挖了没捡到钻石） */
    @Override
    public void setMineScanDroppedItems(boolean enabled) {
        try {
            Settings.Setting<Boolean> setting = BaritoneAPI.getSettings().mineScanDroppedItems;
            backupIfAbsent(setting);
            setting.value = enabled;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** V4.60：残骸模式限制挖掘 Y 范围（8~22）；不限制时 6~256（minY 防挖穿基床岩） */
    @Override
    public void setMiningYRange(int minY, int maxY) {
        try {
            Settings s = BaritoneAPI.getSettings();
            backupIfAbsent(s.minYLevelWhileMining);
            backupIfAbsent(s.maxYLevelWhileMining);
            s.minYLevelWhileMining.value = minY;
            s.maxYLevelWhileMining.value = maxY;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
