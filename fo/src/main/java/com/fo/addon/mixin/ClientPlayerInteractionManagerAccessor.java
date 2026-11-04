package com.fo.addon.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 原版交互管理器的挖掘进度/冷却字段访问器。
 *
 * <p>移植自 Hachimi V2 的 {@code ClientPlayerInteractionManagerMixin2}
 * （{@code hookGetCurrentBreakingProgress}/{@code hookSetCurrentBreakingProgress}），
 * 供「FO 发包挖掘」的 Damage（原版加速）模式把原版挖掘进度直接置满实现秒破，
 * 以及 NoDelay 清除破块后的挖掘冷却。</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {

    /** 把原版挖掘进度直接置满（Damage 模式的核心一步，原版同 tick 内即发破坏包）。 */
    @Accessor("currentBreakingProgress")
    void setCurrentBreakingProgress(float progress);

    /** 读原版挖掘冷却（破块后的 5 tick 等待）。 */
    @Accessor("blockBreakingCooldown")
    int getBlockBreakingCooldown();

    /** 清零原版挖掘冷却（NoDelay）。 */
    @Accessor("blockBreakingCooldown")
    void setBlockBreakingCooldown(int cooldown);
}
