package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.RepairLogic;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * FO 自动下线：多种触发条件下自动断开服务器（安全网）。
 * 触发：低于指定 Y、护甲耐久过低、长时间卡在传送门、靠近指定坐标、服务器无响应。
 * （移植 jefff stashhunting AutoLogPlus 功能思路，不包含其非法断开方式）
 */
public class AutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> logOnY = sgGeneral.add(new BoolSetting.Builder()
        .name("低于Y下线")
        .description("玩家低于指定 Y 高度时自动下线.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> yLevel = sgGeneral.add(new DoubleSetting.Builder()
        .name("Y高度")
        .description("低于此 Y 自动下线.")
        .defaultValue(256)
        .min(-128)
        .sliderRange(-128, 320)
        .visible(logOnY::get)
        .build()
    );

    private final Setting<Boolean> logArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("护甲耐久下线")
        .description("护甲耐久低于指定百分比时自动下线.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略鞘翅")
        .description("检查护甲耐久时跳过鞘翅.")
        .defaultValue(false)
        .visible(logArmor::get)
        .build()
    );

    private final Setting<Double> armorPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("护甲耐久阈值")
        .description("护甲剩余耐久低于此百分比时自动下线.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 100)
        .visible(logArmor::get)
        .build()
    );

    private final Setting<Boolean> logPortal = sgGeneral.add(new BoolSetting.Builder()
        .name("传送门超时下线")
        .description("在传送门内停留过久时自动下线.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> portalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("传送门停留刻数")
        .description("在传送门内超过多少 tick 自动下线 (穿过传送门约需 80 tick).")
        .defaultValue(30)
        .min(1)
        .sliderMax(70)
        .visible(logPortal::get)
        .build()
    );

    private final Setting<Boolean> logPosition = sgGeneral.add(new BoolSetting.Builder()
        .name("靠近坐标下线")
        .description("玩家靠近指定坐标时自动下线 (忽略 Y 轴).")
        .defaultValue(false)
        .build()
    );

    private final Setting<BlockPos> position = sgGeneral.add(new BlockPosSetting.Builder()
        .name("坐标")
        .description("靠近此坐标自动下线 (忽略 Y).")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(logPosition::get)
        .build()
    );

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("触发距离")
        .description("距离坐标多远时自动下线.")
        .defaultValue(100)
        .sliderRange(0, 1000)
        .visible(logPosition::get)
        .build()
    );

    private final Setting<Boolean> logHealth = sgGeneral.add(new BoolSetting.Builder()
        .name("生命值下线")
        .description("生命值低于或等于设定值时自动下线.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> healthThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("生命值阈值")
        .description("生命值低于或等于此值时自动下线 (0 表示不检查).")
        .defaultValue(6)
        .range(0, 20)
        .sliderRange(0, 20)
        .visible(logHealth::get)
        .build()
    );

    private final Setting<Boolean> logTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("图腾下线")
        .description("背包与副手的不死图腾少于设定数量时自动下线.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> totemThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("图腾数量阈值")
        .description("不死图腾少于此数量时自动下线.")
        .defaultValue(3)
        .range(0, 37)
        .sliderRange(0, 37)
        .visible(logTotem::get)
        .build()
    );

    private final Setting<Boolean> serverNotResponding = sgGeneral.add(new BoolSetting.Builder()
        .name("服务器无响应下线")
        .description("服务器一段时间无响应时自动下线.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> serverNotRespondingSecs = sgGeneral.add(new DoubleSetting.Builder()
        .name("无响应秒数")
        .description("服务器超过多少秒无响应自动下线.")
        .defaultValue(10)
        .min(1)
        .sliderMax(60)
        .visible(serverNotResponding::get)
        .build()
    );

    private final Setting<Boolean> reconnectAfterNotResponding = sgGeneral.add(new BoolSetting.Builder()
        .name("无响应后重连")
        .description("服务器无响应下线后自动重连.")
        .defaultValue(false)
        .visible(serverNotResponding::get)
        .build()
    );

    private final Setting<Double> secondsToReconnect = sgGeneral.add(new DoubleSetting.Builder()
        .name("重连等待秒数")
        .description("重连前等待的秒数 (会临时覆盖 Meteor 的自动重连设置).")
        .defaultValue(60)
        .min(10)
        .sliderMax(60 * 5)
        .visible(() -> reconnectAfterNotResponding.get() && serverNotResponding.get())
        .build()
    );

    private int currPortalTicks = 0;
    private double oldDelay;
    private boolean autoReconnectEnabled;
    private boolean waitingForReconnection = false;

    public AutoLog() {
        super(AddonTemplate.CATEGORY, "FO 自动LogPlus", "多种触发条件下自动断开服务器: 低于Y / 护甲耐久低 / 传送门超时 / 靠近坐标 / 服务器无响应.");
    }

    @Override
    public void onActivate() {
        currPortalTicks = 0;
        // 上次因服务器无响应下线且等待重连，本次激活时恢复原自动重连设置
        if (waitingForReconnection) {
            waitingForReconnection = false;
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            Setting<Double> delay = ((Setting<Double>) autoReconnect.settings.get("delay"));
            delay.set(oldDelay);
            if (!autoReconnectEnabled && autoReconnect.isActive()) {
                autoReconnect.toggle();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.getAbilities().allowFlying) return;

        // 服务器无响应
        if (serverNotResponding.get() && !waitingForReconnection) {
            if (TickRate.INSTANCE.getTimeSinceLastTick() > serverNotRespondingSecs.get()) {
                if (reconnectAfterNotResponding.get()) {
                    AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                    autoReconnectEnabled = autoReconnect.isActive();
                    Setting<Double> delay = ((Setting<Double>) autoReconnect.settings.get("delay"));
                    oldDelay = delay.get();
                    delay.set(secondsToReconnect.get());
                    if (!autoReconnectEnabled) {
                        autoReconnect.toggle();
                    }
                    waitingForReconnection = true;
                }
                logOut("服务器 " + serverNotRespondingSecs.get() + " 秒无响应，自动下线.", !reconnectAfterNotResponding.get());
                return;
            }
        }

        // 传送门超时
        if (logPortal.get() && mc.player.portalManager != null) {
            if (mc.player.portalManager.isInPortal()) {
                currPortalTicks++;
                if (currPortalTicks > portalTicks.get()) {
                    logOut("在传送门内停留 " + currPortalTicks + " tick，自动下线.", true);
                    return;
                }
            } else {
                currPortalTicks = 0;
            }
        }

        // 低于 Y
        if (logOnY.get() && mc.player.getY() < yLevel.get()) {
            logOut("Y=" + String.format("%.1f", mc.player.getY()) + " 低于设定高度 " + yLevel.get() + "，自动下线.", true);
            return;
        }

        // 生命值过低
        if (logHealth.get() && healthThreshold.get() > 0) {
            float health = mc.player.getHealth();
            if (health <= healthThreshold.get()) {
                logOut("生命值 " + String.format("%.1f", health) + " 低于或等于 " + healthThreshold.get() + "，自动下线.", true);
                return;
            }
        }

        // 图腾不足 (背包 + 副手)
        if (logTotem.get()) {
            int totems = mc.player.getInventory().count(Items.TOTEM_OF_UNDYING)
                + (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING ? 1 : 0);
            if (totems < totemThreshold.get()) {
                logOut("不死图腾 " + totems + " 个少于设定 " + totemThreshold.get() + "，自动下线.", true);
                return;
            }
        }

        // 护甲耐久过低
        if (logArmor.get()) {
            for (int i = 0; i < 4; i++) {
                ItemStack armorPiece = mc.player.getInventory().getStack(SlotUtils.ARMOR_START + i);
                if (ignoreElytra.get() && armorPiece.getItem() == Items.ELYTRA) continue;
                boolean damaged = RepairLogic.armorTooDamaged(
                    armorPiece.getMaxDamage(), armorPiece.getDamage(),
                    armorPiece.isDamageable(), armorPercent.get());
                if (damaged) {
                    logOut("护甲耐久过低，自动下线.", true);
                    return;
                }
            }
        }

        // 靠近坐标
        if (logPosition.get()) {
            double dx = mc.player.getX() - (position.get().getX() + 0.5);
            double dz = mc.player.getZ() - (position.get().getZ() + 0.5);
            double distanceToTarget = Math.sqrt(dx * dx + dz * dz);
            if (distanceToTarget < distance.get()) {
                logOut("距目标坐标 " + String.format("%.1f", distanceToTarget) + " 格，自动下线.", true);
            }
        }
    }

    private void logOut(String reason, boolean turnOffReconnect) {
        if (mc.player == null) return;
        if (turnOffReconnect && Modules.get().get(AutoReconnect.class).isActive()) {
            Modules.get().get(AutoReconnect.class).toggle();
        }
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[FO 自动下线] " + reason)));
    }
}
