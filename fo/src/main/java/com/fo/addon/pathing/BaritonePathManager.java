package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

/** 纯反射调用 BaritoneAPI，不依赖 baritone-api 编译期类。任何一步失败都会抛出异常，由 PathManagers 回退到空实现。 */
public class BaritonePathManager implements IPathManager {
    private final Object baritone;
    private final Method pathingBehaviorM;
    private final Method isPathingM;
    private final Method cancelEverythingM;
    private final Method customGoalProcessM;
    private final Method setGoalAndPathM;
    private final Method mineProcessM;
    private final Method mineM;
    private final Method followProcessM;
    private final Method pickupM;
    private final Constructor<?> goalXZCtor;
    private final Constructor<?> goalGetToBlockCtor;
    private final Constructor<?> goalNearCtor;

    public static boolean isAvailable() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public BaritonePathManager() throws ReflectiveOperationException {
        if (!isAvailable()) {
            throw new IllegalStateException("Baritone API not available");
        }

        Class<?> api = Class.forName("baritone.api.BaritoneAPI");        Object provider = api.getMethod("getProvider").invoke(null);
        baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
        // 兼容新老包名：优先 baritone.api.behavior (1.21.x)，回退 baritone.api.pathing.behavior (老版)
        Class<?> pathingBehavior;
        try {
            pathingBehavior = Class.forName("baritone.api.behavior.IPathingBehavior");
        } catch (ClassNotFoundException e) {
            pathingBehavior = Class.forName("baritone.api.pathing.behavior.IPathingBehavior");
        }
        pathingBehaviorM = baritone.getClass().getMethod("getPathingBehavior");
        isPathingM = pathingBehavior.getMethod("isPathing");
        cancelEverythingM = pathingBehavior.getMethod("cancelEverything");

        Class<?> customGoalProcess = Class.forName("baritone.api.process.ICustomGoalProcess");
        customGoalProcessM = baritone.getClass().getMethod("getCustomGoalProcess");
        setGoalAndPathM = customGoalProcess.getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal"));

        Class<?> mineProcess = Class.forName("baritone.api.process.IMineProcess");
        mineProcessM = baritone.getClass().getMethod("getMineProcess");
        // mine(Block...) 是变参，编译后签名是 mine(Block[])，反射用数组类型匹配
        mineM = mineProcess.getMethod("mine", Block[].class);
        isActiveM = mineProcess.getMethod("isActive");

        // FollowProcess.pickup(Predicate<ItemStack>)：持续走到匹配掉落物旁拾取
        Class<?> followProcess = Class.forName("baritone.api.process.IFollowProcess");
        followProcessM = baritone.getClass().getMethod("getFollowProcess");
        pickupM = followProcess.getMethod("pickup", Predicate.class);

        // GoalXZ 没有 (BlockPos) 构造器，只有 (int,int) 和 (BetterBlockPos)
        goalXZCtor = Class.forName("baritone.api.pathing.goals.GoalXZ").getConstructor(int.class, int.class);
        goalGetToBlockCtor = Class.forName("baritone.api.pathing.goals.GoalGetToBlock").getConstructor(BlockPos.class);
        // GoalNear(BlockPos, int range)：精确到达。range 是整格容忍度（注意是 int 不是 double，
        // 写 double 会 NoSuchMethodException 导致整个 Baritone 桥接初始化失败）。
        // range=0 → 玩家脚所在格必须等于目标格 = "一格空间都不留"（站到盒子紧邻格正中）。
        goalNearCtor = Class.forName("baritone.api.pathing.goals.GoalNear").getConstructor(BlockPos.class, int.class);
    }

    @Override
    public boolean isPathing() {
        try {
            return (boolean) isPathingM.invoke(pathingBehaviorM.invoke(baritone));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public void stop() {
        try {
            cancelEverythingM.invoke(pathingBehaviorM.invoke(baritone));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void moveTo(BlockPos pos, boolean ignoreY) {
        try {
            Object goal = ignoreY
                ? goalXZCtor.newInstance(pos.getX(), pos.getZ())
                : goalGetToBlockCtor.newInstance(pos);
            setGoalAndPathM.invoke(customGoalProcessM.invoke(baritone), goal);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void moveToPrecise(BlockPos pos) {
        try {
            // GoalNear(pos, 0)：玩家脚所在格必须 == 目标格才算到达（range=0 整格精确），
            // 站在盒子紧邻格正中，一格空间都不留（GoalGetToBlock 只到相邻格就停）。
            Object goal = goalNearCtor.newInstance(pos, 0);
            setGoalAndPathM.invoke(customGoalProcessM.invoke(baritone), goal);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void mine(Block... blocks) {
        try {
            mineM.invoke(mineProcessM.invoke(baritone), (Object) blocks);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void pickupItems(Predicate<ItemStack> filter) {
        try {
            pickupM.invoke(followProcessM.invoke(baritone), filter);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** 设置 Baritone 保护潜影盒不被挖 */
    public void protectShulkerBoxes(boolean protect) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);
            // blocksToDisallowBreaking 是 Setting<List<Block>>，直接改它的 value 字段（List）
            java.lang.reflect.Field f = settings.getClass().getField("blocksToDisallowBreaking");
            Object setting = f.get(settings);
            java.lang.reflect.Field valueField = setting.getClass().getField("value");
            if (protect) {
                Block[] shulkers = new Block[]{
                    net.minecraft.block.Blocks.SHULKER_BOX,
                    net.minecraft.block.Blocks.WHITE_SHULKER_BOX,
                    net.minecraft.block.Blocks.ORANGE_SHULKER_BOX,
                    net.minecraft.block.Blocks.MAGENTA_SHULKER_BOX,
                    net.minecraft.block.Blocks.LIGHT_BLUE_SHULKER_BOX,
                    net.minecraft.block.Blocks.YELLOW_SHULKER_BOX,
                    net.minecraft.block.Blocks.LIME_SHULKER_BOX,
                    net.minecraft.block.Blocks.PINK_SHULKER_BOX,
                    net.minecraft.block.Blocks.GRAY_SHULKER_BOX,
                    net.minecraft.block.Blocks.LIGHT_GRAY_SHULKER_BOX,
                    net.minecraft.block.Blocks.CYAN_SHULKER_BOX,
                    net.minecraft.block.Blocks.PURPLE_SHULKER_BOX,
                    net.minecraft.block.Blocks.BLUE_SHULKER_BOX,
                    net.minecraft.block.Blocks.BROWN_SHULKER_BOX,
                    net.minecraft.block.Blocks.GREEN_SHULKER_BOX,
                    net.minecraft.block.Blocks.RED_SHULKER_BOX,
                    net.minecraft.block.Blocks.BLACK_SHULKER_BOX
                };
                valueField.set(setting, java.util.Arrays.asList(shulkers));
            } else {
                valueField.set(setting, new java.util.ArrayList<Block>());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final java.lang.reflect.Method isActiveM;

    @Override
    public boolean isMining() {
        try {
            return (boolean) isActiveM.invoke(mineProcessM.invoke(baritone));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    // ---- 自动挖矿避让设置（V4.30，移植 misaka AutoMining onActivate/onDeactivate）----
    // 备份对象：{settingsObj, 字段名, 原 value}
    private final List<Object[]> avoidanceBackup = new java.util.ArrayList<>();

    @Override
    public void applyMiningAvoidance(boolean avoidMobs, boolean avoidBlocks) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);

            // 最小挖掘 Y（主世界基岩上 6 格起挖，与 misaka 一致）
            // V4.41 修复：原实现拿 value 字段却对 settings 对象 get/set，抛异常被吞 → 设置从未生效
            backupSetting(settings, "minYLevelWhileMining");
            setSettingValue(settings, "minYLevelWhileMining", 6);

            if (avoidMobs) {
                String[] mobFields = {"avoidance", "mobAvoidanceRadius", "mobAvoidanceCoefficient", "mobSpawnerAvoidanceRadius"};
                for (String name : mobFields) {
                    backupSetting(settings, name);
                }
                setSettingValue(settings, "avoidance", Boolean.TRUE);
                setSettingValue(settings, "mobAvoidanceRadius", 12);
                setSettingValue(settings, "mobAvoidanceCoefficient", 5.0d);
                setSettingValue(settings, "mobSpawnerAvoidanceRadius", 16);
            }

            if (avoidBlocks) {
                backupSetting(settings, "blocksToAvoid");
                List<Block> avoid = new java.util.ArrayList<>();
                avoid.add(net.minecraft.block.Blocks.SCULK_SENSOR);
                avoid.add(net.minecraft.block.Blocks.SCULK);
                avoid.add(net.minecraft.block.Blocks.SCULK_VEIN);
                avoid.add(net.minecraft.block.Blocks.SCULK_CATALYST);
                avoid.add(net.minecraft.block.Blocks.SCULK_SHRIEKER);
                avoid.add(net.minecraft.block.Blocks.TRIAL_SPAWNER);
                avoid.add(net.minecraft.block.Blocks.SPAWNER);
                setSettingValue(settings, "blocksToAvoid", avoid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 拿 Settings 上的 Setting 对象（反射，任何失败返回 null） */
    private Object getSettingObject(Object settings, String fieldName) {
        try {
            return settings.getClass().getField(fieldName).get(settings);
        } catch (Exception e) {
            return null;
        }
    }

    /** 备份某个 Setting 当前值（供 resetMiningAvoidance 恢复） */
    private void backupSetting(Object settings, String fieldName) {
        try {
            Object settingObj = getSettingObject(settings, fieldName);
            if (settingObj == null) return;
            java.lang.reflect.Field vf = settingObj.getClass().getField("value");
            avoidanceBackup.add(new Object[]{settingObj, fieldName, vf.get(settingObj)});
        } catch (Exception ignored) {
        }
    }

    /**
     * 设置 Baritone 设置值（对 Setting 对象操作，非 settings 对象）。
     * V4.41 修复原实现 bug：value 字段定义在 Setting 类上，却对 settings 对象 get/set → 抛异常被吞，设置从未生效。
     * 类型适配：部分 Baritone Y 类设置是 Long，传 Integer 会类型不匹配。
     */
    private boolean setSettingValue(Object settings, String fieldName, Object value) {
        try {
            Object settingObj = getSettingObject(settings, fieldName);
            if (settingObj == null) return false;
            java.lang.reflect.Field vf = settingObj.getClass().getField("value");
            Class<?> type = vf.getType();
            if (value instanceof Integer && (type == Long.class || type == long.class)) {
                vf.set(settingObj, ((Integer) value).longValue());
            } else if (value instanceof Integer && (type == Integer.class || type == int.class)) {
                vf.set(settingObj, value);
            } else {
                vf.set(settingObj, value);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void resetMiningAvoidance() {
        for (Object[] entry : avoidanceBackup) {
            try {
                Object settingObj = entry[0];
                String name = (String) entry[1];
                Object oldValue = entry[2];
                java.lang.reflect.Field vf = settingObj.getClass().getField("value");
                vf.set(settingObj, oldValue);
            } catch (Exception ignored) {
            }
        }
        avoidanceBackup.clear();
    }

    @Override
    public void setAutoTool(boolean enabled) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);
            Object settingObj = settings.getClass().getField("autoTool").get(settings);
            java.lang.reflect.Field vf = settingObj.getClass().getField("value");
            // 只在首次调用时备份（避免重复开关叠加备份）
            boolean backed = false;
            for (Object[] e : avoidanceBackup) {
                if ("autoTool".equals(e[1])) {
                    backed = true;
                    break;
                }
            }
            if (!backed) {
                avoidanceBackup.add(new Object[]{settingObj, "autoTool", vf.get(settingObj)});
            }
            vf.set(settingObj, enabled);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** V4.42：mine 找不到新矿时把地面掉落物当目标捡起（兜住挖了没捡到钻石） */
    @Override
    public void setMineScanDroppedItems(boolean enabled) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);
            backupSetting(settings, "mineScanDroppedItems");
            setSettingValue(settings, "mineScanDroppedItems", enabled);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}