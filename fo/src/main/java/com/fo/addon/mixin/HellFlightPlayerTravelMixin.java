package com.fo.addon.mixin;

import com.fo.addon.elytra.hell.HellFlightRuntime;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class HellFlightPlayerTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"))
    private void fo$beforeTravel(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity player) {
            HellFlightRuntime.onTravelHead(player);
        }
    }
}
