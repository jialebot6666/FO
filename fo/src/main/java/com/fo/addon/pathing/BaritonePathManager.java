package com.fo.addon.pathing;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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
    private final Constructor<?> goalXZCtor;
    private final Constructor<?> goalGetToBlockCtor;

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

        // GoalXZ 没有 (BlockPos) 构造器，只有 (int,int) 和 (BetterBlockPos)
        goalXZCtor = Class.forName("baritone.api.pathing.goals.GoalXZ").getConstructor(int.class, int.class);
        goalGetToBlockCtor = Class.forName("baritone.api.pathing.goals.GoalGetToBlock").getConstructor(BlockPos.class);
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
    public void mine(Block... blocks) {
        try {
            mineM.invoke(mineProcessM.invoke(baritone), (Object) blocks);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** 设置 Baritone 保护潜影盒不被挖 */
    public void protectShulkerBoxes(boolean protect) {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);
            java.lang.reflect.Field f = settings.getClass().getField("blocksToDisallowBreaking");
            Object setting = f.get(settings);
            java.lang.reflect.Method setValue = setting.getClass().getMethod("value", java.lang.Object[].class);
            if (protect) {
                java.util.List<Block> list = new java.util.ArrayList<>();
                for (net.minecraft.block.Block b : new net.minecraft.block.Block[]{
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
                }) list.add(b);
                setValue.invoke(setting, (Object) list.toArray());
            } else {
                setValue.invoke(setting, (Object) new java.util.ArrayList<>().toArray());
            }
        } catch (Exception ignored) {}
    }
}