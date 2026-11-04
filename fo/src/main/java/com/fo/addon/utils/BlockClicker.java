package com.fo.addon.utils;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * 方块点击工具（流程移植自 meteor-miku 的 BaritoneUtil.clickBlock，用户指定用 miku 的实现）。
 *
 * <p>miku 的点击流程（逐步对齐）：
 * <ol>
 *   <li>按目标面算出命中点（方块中心 + 面方向偏移 0.5）；</li>
 *   <li>挥手：客户端 swingHand + 服务端 HandSwingC2SPacket；</li>
 *   <li>旋转到目标朝向；</li>
 *   <li>发带 sequence 的 PlayerInteractBlockC2SPacket（FO 用 accesswidener 打开的
 *       PendingUpdateManager 取序列号，与 miku 的 IClientWorld 访问器等价）；</li>
 *   <li>视角归位。</li>
 * </ol>
 *
 * <p>V6.7p5 改动（用户反馈“所有功能都不转头”）：
 * 旋转改用 Meteor 的 {@link Rotations} 系统。miku 的 {@code RotationManager} 走的是
 * {@code Rotations.setCamRotation}，而 Meteor 里它只影响第三人称的<b>身体朝向</b>
 * （AvatarRendererMixin），第一人称摄像机完全不转，看起来就是“没反应”。
 * 换成 {@code Rotations.rotate(yaw, pitch, priority, callback)} 后：
 * ① 下一个移动包会带上目标朝向（服务端先看到朝向，再收到交互包，顺序有保证）；
 * ② 客户端会临时把玩家朝向切到目标值（第一人称真的转头），回调后再归位。
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

        // 3~5. 转头 → 交互包 → 归位（由 Meteor 的 Rotations 在移动包前后完成）
        if (rotate) {
            Rotation rotation = new Rotation(hitVec);
            Rotations.rotate(rotation.getYaw(), rotation.getPitch(), 60,
                () -> sendSequencedPacket(hand, result));
        } else {
            sendSequencedPacket(hand, result);
        }
    }

    /** 自动选面后点击（开箱子/点活板门常用：哪面够得着点哪面）。 */
    public static void clickBlockFacingPlayer(BlockPos pos, boolean rotate, Hand hand) {
        if (mc.player == null || mc.world == null) return;
        clickBlock(pos, pickFace(pos), rotate, hand);
    }

    /**
     * 只发交互包，<b>完全不碰视角</b>（V6.7p10）。
     *
     * <p>配合调用方的「真实转头」使用：先把玩家 yaw/pitch 真的转过去，等本 tick 的移动包
     * 把朝向带给服务端之后，再调这个方法发包——顺序才是「朝向 → 交互」。
     * 这也是参考实现（RustElytraClient 的 `lookAt` + `delay(3)`）的做法。
     */
    public static void clickBlockNoRotate(BlockPos pos, Hand hand) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        clickBlockNoRotate(pos, pickFace(pos), hand);
    }

    /** 指定面 + 不碰视角的点击（需要固定点某一面时用，比如从上方点活板门）。 */
    public static void clickBlockNoRotate(BlockPos pos, Direction side, Hand hand) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        Vec3d hitVec = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5,
            pos.getY() + 0.5 + side.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        swingHand(hand);
        sendSequencedPacket(hand, new BlockHitResult(hitVec, side, pos, false));
    }

    /**
     * 选一个“够得着”的面：优先水平四个侧面，其次顶面，<b>绝不选底面</b>。
     *
     * <p>旧实现直接 {@code Direction.getFacing(eye - center)}：玩家站在箱子斜上方时会选顶面，
     * 而箱子顶上接了漏斗（比如刷物塔）时，顶面命中点正好落在漏斗里，服务端会拒绝这次交互，
     * 表现就是“点了没反应、靠近一点才偶尔成功”。
     */
    private static Direction pickFace(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        boolean upBlocked = !mc.world.getBlockState(pos.up()).isAir();
        int face = StashMoveLogic.pickClickFace(dx, dy, dz, upBlocked);
        return switch (face) {
            case StashMoveLogic.FACE_EAST -> Direction.EAST;
            case StashMoveLogic.FACE_WEST -> Direction.WEST;
            case StashMoveLogic.FACE_SOUTH -> Direction.SOUTH;
            case StashMoveLogic.FACE_NORTH -> Direction.NORTH;
            default -> Direction.UP;
        };
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
