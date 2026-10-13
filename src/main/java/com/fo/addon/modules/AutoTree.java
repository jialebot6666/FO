package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.TreeLogic;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.SaplingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoTree — 自动种树（V1）。
 *
 * <p>移植自 LeavesHack (CC0) 的 AutoTree：
 * 左键点击方块登记为种植位（再点一次取消）；手持树苗时自动在种植位上方
 * 放置树苗，可选使用骨粉催熟。
 */
public class AutoTree extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> useDelay = sgGeneral.add(new IntSetting.Builder()
        .name("操作延迟")
        .description("每次操作之间的延迟（毫秒）.")
        .defaultValue(50)
        .min(0)
        .sliderMax(1000)
        .build());

    private final Setting<Integer> blocksPer = sgGeneral.add(new IntSetting.Builder()
        .name("每tick方块数")
        .description("每 tick 最多操作的方块数量.")
        .defaultValue(1)
        .min(1)
        .sliderMax(4)
        .build());

    private final Setting<Boolean> useBoneMeal = sgGeneral.add(new BoolSetting.Builder()
        .name("使用骨粉")
        .description("对已种下的树苗自动使用骨粉催熟.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("渲染已登记的种植位.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("渲染模式.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("外框颜色")
        .description("外框颜色.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("填充颜色")
        .description("填充颜色.")
        .defaultValue(new SettingColor(255, 255, 255, 50))
        .build());

    private final List<BlockPos> treePos = new ArrayList<>();
    private long lastUseMs = 0;
    private long lastHintMs = 0;

    public AutoTree() {
        super(AddonTemplate.CATEGORY, "FO 自动种树", "自动种树：左键选择目标方块，手持树苗时自动种植。");
    }

    @Override
    public void onActivate() {
        treePos.clear();
        lastUseMs = System.currentTimeMillis() - 999_999L; // 立即可用
        lastHintMs = 0;
    }

    @Override
    public void onDeactivate() {
        treePos.clear();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!BlockUtils.canBreak(event.blockPos)) return;
        event.cancel();
        if (!treePos.contains(event.blockPos)) {
            treePos.add(event.blockPos);
            info("已登记种植位 (" + event.blockPos.getX() + ", " + event.blockPos.getY() + ", " + event.blockPos.getZ() + ")，手持树苗后自动种植；再点一次取消.");
        } else {
            treePos.remove(event.blockPos);
            info("已取消种植位.");
        }
    }

    /** 低频提示（每 5 秒最多一条），避免刷屏 */
    private void hint(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastHintMs < 5000) return;
        lastHintMs = now;
        info(msg);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 操作引导：缺哪一步直接提示 (原版无提示, 用户以为坏了)
        if (treePos.isEmpty()) {
            hint("自动种树: 请先用左键点击一个地面方块登记种植位 (再点一下取消).");
            return;
        }
        if (System.currentTimeMillis() - lastUseMs < useDelay.get()) return;

        // 仅手持树苗时工作
        ItemStack held = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        if (!isSaplingItem(held)) {
            hint("自动种树: 请手持树苗 (橡树/云杉/桦树/丛林/金合欢/深色橡树/樱花树苗).");
            return;
        }

        FindItemResult sapling = InvUtils.findInHotbar(AutoTree::isSaplingItem);
        FindItemResult boneMeal = InvUtils.findInHotbar(Items.BONE_MEAL);
        if (!sapling.found()) {
            hint("自动种树: 快捷栏没有树苗，请把树苗放进快捷栏.");
            return;
        }
        if (useBoneMeal.get() && !boneMeal.found()) {
            hint("自动种树: 骨粉已用完，暂停种树等待骨粉补充 (防止树苗只种不催熟而耗尽).");
            return;
        }

        int done = 0;
        for (BlockPos pos : treePos) {
            if (done >= blocksPer.get()) break;

            BlockState up = mc.world.getBlockState(pos.up());
            boolean upIsSapling = up.getBlock() instanceof SaplingBlock;
            boolean upIsAir = mc.world.isAir(pos.up()) || up.isReplaceable();

            TreeLogic.Action action = TreeLogic.decide(
                upIsSapling, upIsAir,
                useBoneMeal.get(), boneMeal.found(), sapling.found()
            );

            if (action == TreeLogic.Action.BONEMEAL) {
                clickBlockBoneMeal(pos.up(), Direction.DOWN, boneMeal.slot());
                done++;
                lastUseMs = System.currentTimeMillis();
            } else if (action == TreeLogic.Action.PLANT) {
                BlockUtils.place(pos.up(), sapling, true, 0, true, false);
                done++;
                lastUseMs = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || treePos.isEmpty()) return;
        for (BlockPos pos : treePos) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    /**
     * 对树苗使用骨粉：静默转向后右键点击（催熟）。
     * 必须在旋转回调内完成「切骨粉 → 右键 → 切回」，否则回调执行时槽位已变：
     * ① swap 必须传 true 记录原槽位，swapBack 才能切回树苗（之前传 false 导致永远停在骨粉）
     * ② swap/点击/swapBack 全部在回调里同步执行，保证点击瞬间手里就是骨粉
     */
    private void clickBlockBoneMeal(BlockPos pos, Direction side, int boneMealSlot) {
        Vec3d point = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5,
            pos.getY() + 0.5 + side.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        Rotations.rotate(Rotations.getYaw(point), Rotations.getPitch(point), 100, () -> {
            InvUtils.swap(boneMealSlot, true);
            mc.player.swingHand(Hand.MAIN_HAND);
            BlockHitResult result = new BlockHitResult(point, side, pos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
            InvUtils.swapBack();
        });
    }

    private static boolean isSaplingItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof SaplingBlock;
    }
}
