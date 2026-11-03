package com.fo.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * 方块点击工具（流程原样移植自 meteor-miku 的 BaritoneUtil.clickBlock，用户指定用 miku 的实现，
 * 不复用自动种树那套）。
 *
 * <p>miku 的点击流程（逐步对齐，未做简化）：
 * <ol>
 *   <li>按目标面算出命中点（方块中心 + 面方向偏移 0.5）；</li>
 *   <li>挥手：客户端 swingHand + 服务端 HandSwingC2SPacket；</li>
 *   <li>旋转：{@link RotationManager#register} 发完整位置包（带目标 yaw/pitch）并同步摄像机；</li>
 *   <li>发带 sequence 的 PlayerInteractBlockC2SPacket（FO 用 accesswidener 打开的
 *       PendingUpdateManager 取序列号，与 miku 的 IClientWorld 访问器等价）；</li>
 *   <li>{@link RotationManager#sync} 发回真实朝向，视角归位。</li>
 * </ol>
 *
 * <p>与 miku 唯一的差别：miku 通过 mixin 访问器取 PendingUpdateManager，FO 走 accesswidener
 * 暴露的 {@code ClientWorld.getPendingUpdateManager()}，取到的序列号与发包内容完全一致。
 */
public final class BlockClicker {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private BlockClicker() {
    }

    /** 右键点击方块指定面（带旋转 + 挥手 + 序列号交互包）。 */
    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, Hand hand) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // 1. 面中心命中点（miku：pos + 0.5 + side 偏移 * 0.5）
        Vec3d hitVec = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5,
            pos.getY() + 0.5 + side.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );

        // 2. 挥手（客户端 + 服务端）
        swingHand(hand);

        BlockHitResult result = new BlockHitResult(hitVec, side, pos, false);

        // 3. 旋转（可选）
        if (rotate) {
            RotationManager.getInstance().register(new Rotation(hitVec).setPriority(10));
        }

        // 4. 带序列号的交互包（序列号语义对齐 miku 的 sendSequencedPacket）
        sendSequencedPacket(hand, result);

        // 5. 视角归位
        if (rotate) {
            RotationManager.getInstance().sync();
        }
    }

    /** 以玩家视线方向自动选面后点击（开箱子/点活板门常用：哪面看得见点哪面）。 */
    public static void clickBlockFacingPlayer(BlockPos pos, boolean rotate, Hand hand) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d diff = blockCenter.subtract(mc.player.getEyePos());
        Direction side = Direction.getFacing(diff.x, diff.y, diff.z);
        clickBlock(pos, side, rotate, hand);
    }

    private static void sendSequencedPacket(Hand hand, BlockHitResult result) {
        int sequence;
        var pending = mc.world.getPendingUpdateManager().incrementSequence();
        sequence = pending.sequence;
        pending.close();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, result, sequence));
    }

    private static void swingHand(Hand hand) {
        mc.player.swingHand(hand);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }
}
