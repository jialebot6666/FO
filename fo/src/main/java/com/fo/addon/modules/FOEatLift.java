package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.EatLiftLogic;
import com.fo.addon.utils.SlmHook;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FO 进食拉升。
 *
 * <p>移植自 eat-lift（作者 dev.hyh，Fabric 1.21.11 客户端 mod）。原版用 mixin 挂
 * {@code MinecraftClient.tick} 尾部并另做 F9 调参界面；
 * 这里用 Meteor 的 tick 事件 + 设置页分组实现，不新增 mixin。
 *
 * <p>行为：鞘翅飞行中吃食物会掉高度。本模块在“正在吃食物”期间按住 W（可选再按空格），
 * 并把俯仰平滑压到设定角度，按满设定时长后松开、还原吃之前的按键与视角。
 */
public class FOEatLift extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLift = settings.createGroup("拉升");

    private final Setting<Boolean> requireFood = sgGeneral.add(new BoolSetting.Builder()
        .name("只认食物")
        .description("只对食物生效；关闭后长矛、盾、弓、烟花等任何使用物品都会触发。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> delayTicks = sgLift.add(new IntSetting.Builder()
        .name("延迟")
        .description("开始吃之后等多少 tick 才按住（20 tick = 1 秒）。")
        .defaultValue(0)
        .min(0).max(600)
        .sliderMin(0).sliderMax(200)
        .build());

    private final Setting<Integer> holdTicks = sgLift.add(new IntSetting.Builder()
        .name("按住时长")
        .description("按住 W / 空格的持续时长（tick）。")
        .defaultValue(45)
        .min(0).max(600)
        .sliderMin(0).sliderMax(200)
        .build());

    private final Setting<Integer> escapeAngle = sgLift.add(new IntSetting.Builder()
        .name("拉升角度")
        .description("俯仰目标角度（度，正值向上）。")
        .defaultValue(45)
        .min(-89).max(89)
        .sliderMin(-89).sliderMax(89)
        .build());

    private final Setting<Boolean> lockPitch = sgLift.add(new BoolSetting.Builder()
        .name("锁俯仰")
        .description("按设定角度平滑改俯仰；关闭则完全不碰视角，只靠 W（+空格）的自然抬升。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useJump = sgLift.add(new BoolSetting.Builder()
        .name("加空格")
        .description("同时按住空格，垂直推力更足，实际角度更陡。")
        .defaultValue(true)
        .build());

    public static final Logger LOGGER = LoggerFactory.getLogger("fo-eatlift");

    private final EatLiftLogic logic = new EatLiftLogic();

    private boolean active;
    private boolean savedForward;
    private boolean savedJump;
    private float savedPitch;

    public FOEatLift() {
        super(AddonTemplate.CATEGORY, "FO 进食拉升",
            "吃东西时自动按住 W（可加空格）并平滑抬视角，吃完恢复原状；跟随 SlimefunHelper 鞘翅追击开关。");
    }

    @Override
    public void onDeactivate() {
        release();
        logic.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.options == null) {
            release();
            logic.reset();
            return;
        }

        boolean eating = mc.player.isUsingItem();
        if (eating && requireFood.get()) {
            ItemStack stack = mc.player.getActiveItem();
            eating = !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
        }

        // 与 eat-lift 原版一致：读 SlimefunHelper 鞘翅追击的开关；没装或读不到时放行
        boolean gate = SlmHook.isElytraBotOn();

        EatLiftLogic.Action action = logic.tick(eating, gate, delayTicks.get(), holdTicks.get());
        if (action == EatLiftLogic.Action.PRESS) {
            press();
        } else if (action == EatLiftLogic.Action.RELEASE) {
            release();
        }

        if (active && logic.phase() == EatLiftLogic.Phase.HOLDING) {
            applyAngle();
        }
    }

    private void press() {
        savedForward = mc.options.forwardKey.isPressed();
        savedJump = mc.options.jumpKey.isPressed();
        savedPitch = mc.player.getPitch();

        mc.options.forwardKey.setPressed(true);
        if (useJump.get()) {
            mc.options.jumpKey.setPressed(true);
        }
        active = true;
    }

    private void release() {
        if (!active) {
            return;
        }
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(savedForward);
            mc.options.jumpKey.setPressed(savedJump);
        }
        if (mc.player != null && lockPitch.get()) {
            mc.player.setPitch(savedPitch);
        }
        active = false;
    }

    private void applyAngle() {
        if (!lockPitch.get() || mc.player == null) {
            return;
        }
        float target = -escapeAngle.get();
        float current = mc.player.getPitch();
        float diff = target - current;
        if (Math.abs(diff) <= 8.0f) {
            mc.player.setPitch(target);
        } else {
            mc.player.setPitch(current + Math.signum(diff) * 8.0f);
        }
    }
}
