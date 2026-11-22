package com.fo.addon.mixin;

import com.fo.addon.modules.FOThreeCNewbieCheck;
import com.fo.addon.utils.threec.ThreeCPlayerService;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 Meteor 的玩家名字标签后追加“萌新 / 糕手”徽标。
 *
 * <p>移植自 Tanghulu（https://github.com/Tanghulu2019-a/Tanghulu）的
 * {@code ThreeCNametagsMixin}（GPLv3）。同时扩展宽度计算与渲染两处，
 * 避免徽标压到血量、延迟或距离文字。
 */
@Mixin(value = Nametags.class, remap = false)
public abstract class ThreeCNametagsMixin {
    @Unique
    private String fo$badge = "";

    @Unique
    private final Color fo$newbieColor = new Color(85, 255, 85);

    @Unique
    private final Color fo$expertColor = new Color(255, 85, 85);

    @Unique
    private Color fo$badgeColor;

    @Inject(method = "renderNametagPlayer", at = @At("HEAD"))
    private void fo$selectBadge(Render2DEvent event, PlayerEntity player, boolean shadow, CallbackInfo ci) {
        fo$badge = "";
        FOThreeCNewbieCheck module = Modules.get().get(FOThreeCNewbieCheck.class);
        if (module == null || !module.isActive()) return;
        ThreeCPlayerService.PlayerTime data = module.nametagResult(player);
        if (data == null) return;
        boolean newbie = module.isNewbie(data);
        fo$badge = newbie ? " 萌新" : " 糕手";
        fo$badgeColor = newbie ? fo$newbieColor : fo$expertColor;
    }

    @Redirect(method = "renderNametagPlayer", at = @At(value = "INVOKE", target =
        "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;getWidth(Ljava/lang/String;Z)D", ordinal = 1))
    private double fo$badgeWidth(TextRenderer renderer, String name, boolean shadow) {
        return renderer.getWidth(name, shadow) + (fo$badge.isEmpty() ? 0 : renderer.getWidth(fo$badge, shadow));
    }

    @Redirect(method = "renderNametagPlayer", at = @At(value = "INVOKE", target =
        "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;render(Ljava/lang/String;DDLmeteordevelopment/meteorclient/utils/render/color/Color;Z)D", ordinal = 1))
    private double fo$renderBadge(TextRenderer renderer, String name, double x, double y, Color color, boolean shadow) {
        double next = renderer.render(name, x, y, color, shadow);
        return fo$badge.isEmpty() ? next : renderer.render(fo$badge, next, y, fo$badgeColor, shadow);
    }
}
