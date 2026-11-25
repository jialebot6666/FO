package com.fo.addon.mixin;

import com.fo.addon.elytra.hell.HellFlightRuntime;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class HellFlightFireworkMixin {
    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void fo$afterTracked(TrackedData<?> data, CallbackInfo ci) {
        if ((Object) this instanceof FireworkRocketEntity firework) {
            HellFlightRuntime.onFireworkSpawn(firework);
        }
    }
}
