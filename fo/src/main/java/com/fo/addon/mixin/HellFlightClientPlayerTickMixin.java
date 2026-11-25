package com.fo.addon.mixin;

import com.fo.addon.elytra.hell.HellFlightRuntime;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class HellFlightClientPlayerTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void fo$afterTick(CallbackInfo ci) {
        HellFlightRuntime.onTickTail((ClientPlayerEntity) (Object) this);
    }
}
