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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
            info("已登记种植位，手持树苗后自动种植；再点一次取消.");
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
                bonemealSapling(pos.up(), boneMeal.slot());
                done++;
                lastUseMs = System.currentTimeMillis();
            } else if (action == TreeLogic.Action.PLANT) {
                plantSapling(pos);
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
     * 对树苗使用骨粉（6.42：全面对齐 LeavesHack）。
     * 与原版一致：① 找树苗「可见侧面」点击（getClickSide），不再点地面；
     * ② 同步瞬转——直接发 PlayerMoveC2SPacket.LookAndOnGround 服务端视角包，
     *    不经 Meteor 异步旋转回调（回调易被其他模块抢旋转吞掉，表现为「偶尔才催熟一下」）。
     * 顺序：切骨粉 → 瞬转 → 挥臂 → 右键树苗侧面 → 转回 → 切回树苗。
     */
    private void bonemealSapling(BlockPos saplingPos, int boneMealSlot) {
        Direction side = getClickSide(saplingPos);
        Vec3d point = new Vec3d(
            saplingPos.getX() + 0.5 + side.getVector().getX() * 0.5,
            saplingPos.getY() + 0.5 + side.getVector().getY() * 0.5,
            saplingPos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        InvUtils.swap(boneMealSlot, true);
        snapLook(point);
        mc.player.swingHand(Hand.MAIN_HAND);
        BlockHitResult result = new BlockHitResult(point, side, saplingPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
        snapBack();
        InvUtils.swapBack();
    }

    /**
     * 放置树苗（6.42：对齐 LeavesHack placeBlock(pos.up(), DOWN, true)）。
     * 等价于「手持树苗点击地面方块上表面」，树苗落到 pos.up()。
     */
    private void plantSapling(BlockPos groundPos) {
        Direction side = Direction.UP;
        Vec3d point = new Vec3d(
            groundPos.getX() + 0.5 + side.getVector().getX() * 0.5,
            groundPos.getY() + 0.5 + side.getVector().getY() * 0.5,
            groundPos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        snapLook(point);
        mc.player.swingHand(Hand.MAIN_HAND);
        BlockHitResult result = new BlockHitResult(point, side, groundPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
        snapBack();
    }

    // ========== 同步瞬转（对齐 LeavesHack Rotation.snapAt/snapBack）==========
    // 只发服务端视角包，客户端视角不动——不存在异步回调被吞的问题。
    private void snapLook(Vec3d point) {
        sendLookPacket((float) Rotations.getYaw(point), (float) Rotations.getPitch(point));
    }

    private void sendLookPacket(float yaw, float pitch) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    private void snapBack() {
        sendLookPacket(mc.player.getYaw(), mc.player.getPitch());
    }

    // ========== 可见侧面查找（对齐 LeavesHack BlockUtil.getClickSide / canSee）==========
    private Direction getClickSide(BlockPos pos) {
        Direction side = null;
        double range = 100;
        for (Direction i : Direction.values()) {
            if (!canSee(pos, i)) continue;
            double d = Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos()));
            if (d > range) continue;
            side = i;
            range = d;
        }
        if (side != null) return side;
        // 无可见面时退回最近面（原版走 isGrimDirection 分支，这里简化取距离最近）
        side = Direction.UP;
        double best = Double.MAX_VALUE;
        for (Direction i : Direction.values()) {
            double d = mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos());
            if (d < best) {
                best = d;
                side = i;
            }
        }
        return side;
    }

    private boolean canSee(BlockPos pos, Direction side) {
        Vec3d test = pos.toCenterPos().add(
            side.getVector().getX() * 0.5,
            side.getVector().getY() * 0.5,
            side.getVector().getZ() * 0.5);
        HitResult result = mc.world.raycast(new RaycastContext(
            mc.player.getEyePos(), test,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    private static boolean isSaplingItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof SaplingBlock;
    }
}
