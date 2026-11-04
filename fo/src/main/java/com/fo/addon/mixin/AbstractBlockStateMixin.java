package com.fo.addon.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.fo.addon.modules.FOPacketMine;

/**
 * 碰撞形状挂钩（NoCollision「碰撞穿透」的实现点）。
 *
 * <p>移植自 Hachimi V2 的 {@code GetCollisionShapeEvent}：正在发包挖掘的方块
 * 对本地玩家不产生碰撞，可以直接走进方块里。只影响本地玩家自身的碰撞查询，
 * 其他实体/寻路不受影响。</p>
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
        at = @At("HEAD"), cancellable = true)
    private void onGetCollisionShape(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        FOPacketMine mod = FOPacketMine.get();
        if (mod == null || context == null) return;

        // 只对本地玩家自身的碰撞查询生效（渲染/寻路等其他查询不碰）
        if (context instanceof net.minecraft.block.EntityShapeContext esc
            && esc.getEntity() != null
            && esc.getEntity() == mod.mc().player
            && mod.shouldIgnoreCollision(pos)) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
