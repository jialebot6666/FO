package com.fo.addon.mixin;

import com.fo.addon.vault.VaultLootHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 复刻 SeedMapper `ClientPacketListenerMixin` 的三个采集点：
 * `onBlockUpdate`（宝库状态）、`onEntitySpawn`（物品实体生成）、`onEntityTrackerUpdate`（物品堆数据）。
 * 全部注入在 `RETURN`：vanilla 已经把实体/方块状态处理完，读到的就是最新值。
 */
@Mixin(ClientPlayNetworkHandler.class)
public class VaultLootPacketMixin {
    @Inject(method = "onBlockUpdate", at = @At("RETURN"))
    private void fo$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        VaultLootHooks.onBlockUpdate(packet.getPos(), packet.getState());
    }

    @Inject(method = "onEntitySpawn", at = @At("RETURN"))
    private void fo$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        VaultLootHooks.onEntitySpawn(packet.getEntityId(), packet.getEntityType(), packet.getX(), packet.getY(), packet.getZ());
    }

    @Inject(method = "onEntityTrackerUpdate", at = @At("RETURN"))
    private void fo$onEntityTrackerUpdate(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (client.world.getEntityById(packet.id()) instanceof ItemEntity item) {
            VaultLootHooks.onEntityData(packet.id(), item);
        }
    }
}
