package com.fo.addon.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fo.addon.modules.FOPacketMine;

/**
 * 拦截原版 {@code cancelBlockBreaking()}（NoAbort 的挂钩点）。
 *
 * <p>移植自 Hachimi V2 的 {@code CancelBlockBreakingEvent}：原版在多种时机
 * （如使用物品、打开界面）会取消挖掘进度；Damage 模式开启「不中断」时，
 * 吃东西期间挖掘不被打断。</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void onCancelBlockBreaking(CallbackInfo ci) {
        FOPacketMine mod = FOPacketMine.get();
        if (mod != null && mod.shouldKeepBreaking()) {
            ci.cancel();
        }
    }
}
