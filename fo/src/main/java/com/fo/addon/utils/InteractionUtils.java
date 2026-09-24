package com.fo.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * 交互转头工具（移植自 Ying / SlimefunHelper 的统一转头模式，三家写法一致）：
 * 1. 记录原视角（SlimefunHelper InteractionTasks 模式）
 * 2. atan2 计算目标角度并 setYaw/setPitch 对准目标（全项目同一公式）
 * 3. 显式发送 LookAndOnGround 视角包，让服务端在处理交互包前先看到新视角（Ying 模式）
 * 4. 执行交互
 * 5. 恢复原视角（SlimefunHelper 模式：避免视角被永久劫持，与移动/挖沙模块打架）
 */
public final class InteractionUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private InteractionUtils() {
    }

    /** 转头对准方块并安全交互，交互后恢复原视角 */
    public static void interactBlockSafely(BlockPos pos, Direction face) {
        if (mc.player == null || mc.world == null) return;

        // 1. 记录原视角
        float oldPitch = mc.player.getPitch();
        float oldYaw = mc.player.getYaw();

        // 2. atan2 计算目标角度并转头
        float[] fp = FacingLogic.yawPitchTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ());
        mc.player.setYaw(fp[0]);
        mc.player.setPitch(fp[1]);

        // 3. 显式发送视角包（先发视角包，服务器处理交互包时才能看到新视角）
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            fp[0], fp[1], mc.player.isOnGround(), mc.player.horizontalCollision));

        // 4. 交互
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), face, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

        // 5. 恢复原视角
        mc.player.setYaw(oldYaw);
        mc.player.setPitch(oldPitch);
    }
}
