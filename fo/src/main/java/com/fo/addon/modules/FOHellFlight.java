package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IElytraProcess;
import com.fo.addon.elytra.hell.HellFlightLogic;
import com.fo.addon.elytra.hell.HellFlightRuntime;
import com.fo.addon.elytra.hell.HellFlightStateMachine;
import com.fo.addon.elytra.hell.HellFlightStateMachine.Phase;
import com.fo.addon.elytra.hell.HellFlightStateMachine.RepairStep;
import com.fo.addon.elytra.hell.HellFlightStateMachine.SupplyStep;
import com.fo.addon.pathing.BaritonePathManager;
import com.fo.addon.pathing.PathManagers;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class FOHellFlight extends Module {
    private static final RegistryKey<Biome> NETHER_WASTES = RegistryKey.of(
        RegistryKeys.BIOME, Identifier.of("minecraft", "nether_wastes")
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("安全");
    private final SettingGroup sgFlight = settings.createGroup("飞行");

    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
        .name("目的地X").description("目的地方块 X 坐标。").defaultValue(0)
        .range(-30000000, 30000000).sliderRange(-10000, 10000).build());
    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
        .name("目的地Z").description("目的地方块 Z 坐标。").defaultValue(0)
        .range(-30000000, 30000000).sliderRange(-10000, 10000).build());
    private final Setting<Boolean> start = sgGeneral.add(new BoolSetting.Builder()
        .name("开始飞行").description("开启完整补给、飞行和着陆流程。").defaultValue(false)
        .onChanged(value -> {
            if (value) startFlight();
        }).build());
    private final Setting<Boolean> stop = sgGeneral.add(new BoolSetting.Builder()
        .name("停止飞行").description("停止当前任务并释放按键、寻路和界面。").defaultValue(false)
        .onChanged(value -> {
            if (value) stopFlight();
        }).build());
    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("调试日志").description("输出状态切换和超时诊断信息。").defaultValue(false).build());
    private final Setting<HellFlightLogic.Food> food = sgGeneral.add(new EnumSetting.Builder<HellFlightLogic.Food>()
        .name("食物").description("自动进食和补给使用的食物。").defaultValue(HellFlightLogic.Food.GOLDEN_CARROT).build());

    private final Setting<Boolean> autoLog = sgSafety.add(new BoolSetting.Builder()
        .name("自动退出").description("后续段失败时断开服务器，避免角色继续遇险。").defaultValue(true).build());
    private final Setting<Boolean> firstSegmentAutoLog = sgSafety.add(new BoolSetting.Builder()
        .name("第一段自动退出").description("第一段失败时也断开服务器。").defaultValue(false).build());
    private final Setting<Boolean> inspectArmor = sgSafety.add(new BoolSetting.Builder()
        .name("检查盔甲").description("启动前要求钻石或合金头盔、护腿、靴子和鞘翅。").defaultValue(false).build());
    private final Setting<Boolean> fireballProtect = sgSafety.add(new BoolSetting.Builder()
        .name("火球保护").description("补给时自动击退靠近的恶魂火球。").defaultValue(true).build());

    private final SettingGroup sgSupply = settings.createGroup("补给");
    private final Setting<Integer> fireworkStacks = sgSupply.add(new IntSetting.Builder()
        .name("烟花组数").description("目标烟花库存，1 组为 64 个。").defaultValue(HellFlightLogic.DEFAULT_FIREWORK_STACKS)
        .range(0, 64).sliderRange(0, 40).build());
    private final Setting<Integer> xpStacks = sgSupply.add(new IntSetting.Builder()
        .name("经验瓶组数").description("目标经验瓶库存，1 组为 64 个。").defaultValue(HellFlightLogic.DEFAULT_XP_STACKS)
        .range(0, 64).sliderRange(0, 20).build());
    private final Setting<Integer> foodStacks = sgSupply.add(new IntSetting.Builder()
        .name("食物组数").description("目标食物库存，1 组为 64 个。").defaultValue(HellFlightLogic.DEFAULT_FOOD_STACKS)
        .range(0, 64).sliderRange(0, 10).build());
    private final Setting<Integer> totemTarget = sgSupply.add(new IntSetting.Builder()
        .name("图腾数量").description("目标不死图腾数量，图腾每组只有 1 个。").defaultValue(HellFlightLogic.DEFAULT_TOTEMS)
        .range(0, 64).sliderRange(0, 10).build());
    private final Setting<Double> fireworkSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("烟花速度阈值").description("水平速度平方低于此值时补烟花。").defaultValue(1.5)
        .min(0.1).max(5.0).sliderRange(0.1, 3.0).build());
    private final Setting<Boolean> kineticProtection = sgFlight.add(new BoolSetting.Builder()
        .name("防撞墙损伤").description("预测到撞墙时临时调整朝向，降低动能伤害。").defaultValue(true).build());
    private final Setting<Boolean> dynamicTimer = sgFlight.add(new BoolSetting.Builder()
        .name("动态计时").description("区块加载缓慢时降低计时倍率，恢复后逐步回升。").defaultValue(true).build());

    private final HellFlightStateMachine machine = new HellFlightStateMachine();
    private final Set<BlockPos> badLandingSpots = new HashSet<>();
    private final Set<UUID> ignoredFireballs = new HashSet<>();
    private final Deque<ClickOp> clickQueue = new ArrayDeque<>();
    private final List<BoxSupply> supplyBoxes = new ArrayList<>();
    private final Map<Integer, Integer> shulkerSlotBaseline = new HashMap<>();

    private long tick;
    private long phaseTicks;
    private long totalTicks;
    private long segmentStartTick;
    private long lastDistanceTick;
    private double distanceTraveled;
    private Vec3d lastPosition;
    private Vec3d destination;
    private BlockPos startPos;
    private BlockPos enderPos;
    private BlockPos boxPos;
    private BlockPos landingPos;
    private BlockPos walkTarget;
    private int boxInventorySlot = -1;
    private int pickedBoxSlot = -1;
    private int boxPickupBaseline;
    private int enderPickupBaseline;
    private int boxIndex;
    private int takeoffStage;
    private double takeoffStartY;
    private boolean takeoffBoostUsed;
    private int lavaTicks;
    private int eatTicks;
    private int eatSlot = -1;
    private int repairBottleSlot = -1;
    private int repairAttempts;
    private int fireworkCooldown;
    private int slowChunkStrikes;
    private int supplyHealthBaseline;
    private boolean supplyActionStarted;
    private boolean placementPrepared;
    private boolean placementClicked;
    private long supplyActionTick;
    private long baritoneStartTick;
    private boolean walkIssued;
    private boolean chunkWaiting;
    private boolean clicksQueued;
    private Runnable afterClicks;
    private HellFlightLogic.Needs needs;
    private WLabel statusLabel;
    private WLabel segmentLabel;
    private WLabel distanceLabel;
    private WLabel remainingLabel;
    private WLabel speedLabel;
    private WLabel etaLabel;

    public FOHellFlight() {
        super(AddonTemplate.CATEGORY, "FO 地狱飞行", "XP 模式：可调补给、Baritone 鞘翅寻路、经验瓶修复与分段续飞。");
    }

    @Override
    public void onDeactivate() {
        machine.stop();
        cleanup();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        cleanup();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!machine.active()) return;
        if (mc.player == null || mc.world == null) {
            cleanup();
            return;
        }

        tick++;
        phaseTicks++;
        totalTicks++;
        if (lastPosition != null) distanceTraveled += mc.player.getEntityPos().distanceTo(lastPosition);
        lastPosition = mc.player.getEntityPos();
        if (fireworkCooldown > 0) fireworkCooldown--;

        try {
            if (machine.phase() == Phase.SUPPLY || machine.phase() == Phase.REPAIR_ELYTRA) {
                protectFireball();
                if (mc.player.getHealth() < supplyHealthBaseline) {
                    fail("补给过程中受伤，已停止任务");
                    return;
                }
            }
            if (HellFlightLogic.lowHealthExit(mc.player.getHealth(), countItem(Items.TOTEM_OF_UNDYING))) {
                fail("血量过低且不死图腾不足");
                return;
            }

            switch (machine.phase()) {
                case PREPARE -> prepareTick();
                case SUPPLY -> supplyTick();
                case TAKEOFF -> takeoffTick();
                case FLYING -> flightTick();
                case LANDING_SEARCH -> landingSearchTick();
                case LANDING -> landingTick();
                case REPAIR_ELYTRA -> repairTick();
                case STOPPING -> cleanup();
                default -> {
                }
            }
        } catch (Exception exception) {
            AddonTemplate.LOG.warn("地狱飞行运行异常", exception);
            String message = exception.getMessage();
            fail(message == null || message.matches(".*[A-Za-z].*") ? "运行异常，请查看调试日志" : message);
        }

        if (tick % 5 == 0) updateStatusWidgets();
    }

    private void startFlight() {
        if (machine.active()) {
            start.set(false);
            return;
        }
        if (mc.player == null || mc.world == null) {
            error("请先进入游戏");
            start.set(false);
            return;
        }
        if (!BaritonePathManager.isAvailable()) {
            error("未检测到 Baritone，无法执行地面补给与起飞寻路");
            start.set(false);
            return;
        }

        destination = new Vec3d(targetX.get(), 0, targetZ.get());
        startPos = mc.player.getBlockPos();
        lastPosition = mc.player.getEntityPos();
        distanceTraveled = 0;
        totalTicks = 0;
        segmentStartTick = 0;
        lastDistanceTick = 0;
        slowChunkStrikes = 0;
        takeoffStage = 0;
        takeoffStartY = mc.player.getY();
        takeoffBoostUsed = false;
        lavaTicks = 0;
        boxIndex = 0;
        boxInventorySlot = -1;
        pickedBoxSlot = -1;
        supplyBoxes.clear();
        shulkerSlotBaseline.clear();
        badLandingSpots.clear();
        ignoredFireballs.clear();
        repairBottleSlot = -1;
        repairAttempts = 0;
        if (!machine.start()) {
            start.set(false);
            return;
        }
        HellFlightRuntime.configure(true, kineticProtection.get());
        mc.setScreen(null);
        start.set(true);
        info("任务开始，目标 " + targetX.get() + ", " + targetZ.get());
    }

    private void stopFlight() {
        if (!machine.active() && machine.phase() != Phase.FAILED && machine.phase() != Phase.COMPLETE) {
            start.set(false);
            stop.set(false);
            return;
        }
        machine.stop();
        cleanup();
    }

    private void cleanup() {
        releaseKeys();
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
        }
        PathManagers.get().stop();
        HellFlightRuntime.configure(false, false);
        HellFlightRuntime.reset();
        clickQueue.clear();
        supplyBoxes.clear();
        shulkerSlotBaseline.clear();
        afterClicks = null;
        clicksQueued = false;
        chunkWaiting = false;
        eatTicks = 0;
        eatSlot = -1;
        walkTarget = null;
        walkIssued = false;
        landingPos = null;
        machine.finishStop();
        start.set(false);
        stop.set(false);
        updateStatusWidgets();
    }

    private void fail(String reason) {
        if (!machine.active()) return;
        warning("任务失败：" + reason);
        boolean shouldLogout = autoLog.get() && (machine.segment() > 0 || firstSegmentAutoLog.get());
        machine.fail();
        if (shouldLogout && mc.player != null) {
            mc.player.networkHandler.getConnection().disconnect(Text.literal("【地狱飞行】" + reason));
        }
        cleanup();
    }

    private void prepareTick() {
        ensureElytra();
        if (countItem(Items.ENDER_CHEST) < 3) throw new IllegalStateException("至少需要 3 个末影箱");
        if (!hasPickaxe()) throw new IllegalStateException("需要钻石镐或合金镐");
        if (!hasSword()) throw new IllegalStateException("需要钻石剑或合金剑");
        if (inspectArmor.get() && !hasStrongArmor()) throw new IllegalStateException("盔甲检查未通过");
        ensureHotbar(Items.ENDER_CHEST, 0);
        ensureHotbar(pickaxeItem(), 1);
        ensureHotbar(swordItem(), 2);
        if (countItem(foodItem()) > 0) ensureHotbar(foodItem(), 3);
        if (countItem(Items.TOTEM_OF_UNDYING) > 0) ensureHotbar(Items.TOTEM_OF_UNDYING, 4);
        if (countItem(Items.EXPERIENCE_BOTTLE) > 0) ensureHotbar(Items.EXPERIENCE_BOTTLE, 5);
        supplyHealthBaseline = (int) mc.player.getHealth();
        machine.beginSupply();
        enterSupplyStep(SupplyStep.CENTER);
    }

    private void ensureElytra() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
        FindItemResult found = InvUtils.find(Items.ELYTRA);
        if (!found.found()) throw new IllegalStateException("背包中没有鞘翅");
        InvUtils.move().from(found.slot()).toArmor(2);
    }

    private boolean hasPickaxe() {
        return countItem(Items.DIAMOND_PICKAXE) > 0 || countItem(Items.NETHERITE_PICKAXE) > 0;
    }

    private Item pickaxeItem() {
        return countItem(Items.DIAMOND_PICKAXE) > 0 ? Items.DIAMOND_PICKAXE : Items.NETHERITE_PICKAXE;
    }

    private boolean hasSword() {
        return countItem(Items.DIAMOND_SWORD) > 0 || countItem(Items.NETHERITE_SWORD) > 0;
    }

    private Item swordItem() {
        return countItem(Items.DIAMOND_SWORD) > 0 ? Items.DIAMOND_SWORD : Items.NETHERITE_SWORD;
    }

    private void ensureHotbar(Item item, int preferredSlot) {
        if (findHotbar(item) >= 0) return;
        FindItemResult found = InvUtils.find(item);
        if (!found.found()) throw new IllegalStateException("无法整理快捷栏物资");
        InvUtils.move().from(found.slot()).toHotbar(preferredSlot);
    }

    private boolean hasStrongArmor() {
        ItemStack helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack legs = mc.player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack boots = mc.player.getEquippedStack(EquipmentSlot.FEET);
        return (helmet.isOf(Items.DIAMOND_HELMET) || helmet.isOf(Items.NETHERITE_HELMET))
            && (legs.isOf(Items.DIAMOND_LEGGINGS) || legs.isOf(Items.NETHERITE_LEGGINGS))
            && (boots.isOf(Items.DIAMOND_BOOTS) || boots.isOf(Items.NETHERITE_BOOTS))
            && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void enterSupplyStep(SupplyStep step) {
        if (step != machine.supplyStep() && !machine.setSupplyStep(step)) {
            throw new IllegalStateException("补给状态转换无效");
        }
        phaseTicks = 0;
        supplyActionStarted = false;
        placementPrepared = false;
        placementClicked = false;
        supplyActionTick = 0;
        clicksQueued = false;
        clickQueue.clear();
        afterClicks = null;
        if (debug.get()) info("补给状态：" + step.label());
    }

    private void supplyTick() {
        SupplyStep beforeClicks = machine.supplyStep();
        tickClicks();
        if (machine.supplyStep() != beforeClicks) return;
        if (phaseTicks > 600) throw new IllegalStateException("补给步骤超时：" + machine.supplyStep().label());

        switch (machine.supplyStep()) {
            case CENTER -> {
                releaseKeys();
                if (!supplyActionStarted) {
                    PathManagers.get().moveToPrecise(mc.player.getBlockPos());
                    supplyActionStarted = true;
                }
                double distance = horizontalDistance(mc.player.getEntityPos(), Vec3d.ofCenter(mc.player.getBlockPos()));
                if (distance < 0.35 || phaseTicks >= 40) {
                    PathManagers.get().stop();
                    enterSupplyStep(SupplyStep.SORT);
                }
            }
            case SORT -> {
                needs = HellFlightLogic.needs(
                    countItem(Items.FIREWORK_ROCKET),
                    countItem(Items.EXPERIENCE_BOTTLE),
                    countItem(foodItem()),
                    countItem(Items.TOTEM_OF_UNDYING),
                    fireworkStacks.get(), xpStacks.get(), foodStacks.get(), totemTarget.get()
                );
                if (!needs.any()) {
                    enterSupplyStep(SupplyStep.DONE);
                } else {
                    info("需要补给：烟花 " + needs.fireworks() + "，经验瓶 " + needs.xp() + "，食物 " + needs.food() + "，图腾 " + needs.totems());
                    enterSupplyStep(SupplyStep.EXTINGUISH);
                }
            }
            case EXTINGUISH -> {
                BlockPos fire = findNearbyFire();
                if (fire != null && phaseTicks % 2 == 0) attackBlock(fire);
                if (fire == null && phaseTicks >= 2) enterSupplyStep(SupplyStep.FIND_ENDER_SPOT);
            }
            case FIND_ENDER_SPOT -> {
                enderPos = findPlaceSpot(null);
                if (enderPos == null) throw new IllegalStateException("附近没有平坦的末影箱放置位置");
                enterSupplyStep(SupplyStep.PLACE_ENDER);
            }
            case PLACE_ENDER -> {
                int slot = findHotbar(Items.ENDER_CHEST);
                if (slot < 0) throw new IllegalStateException("快捷栏没有末影箱");
                if (!placementPrepared) {
                    prepareItemAt(enderPos, slot);
                    placementPrepared = true;
                }
                if (!placementClicked && tick - supplyActionTick >= 3) {
                    placeBlockAt(enderPos);
                    placementClicked = true;
                    supplyActionTick = tick;
                }
                if (placementClicked && tick - supplyActionTick >= 5 && mc.world.getBlockState(enderPos).isOf(Blocks.ENDER_CHEST)) {
                    enterSupplyStep(SupplyStep.OPEN_ENDER);
                } else if (phaseTicks > 40) throw new IllegalStateException("末影箱放置失败");
            }
            case OPEN_ENDER -> {
                if (!supplyActionStarted) {
                    interactBlock(enderPos);
                    supplyActionStarted = true;
                }
                if (isEnderOpen()) enterSupplyStep(SupplyStep.SELECT_BOXES);
                else if (phaseTicks > 40) throw new IllegalStateException("末影箱打开失败");
            }
            case SELECT_BOXES -> {
                selectSupplyBoxes();
                if (supplyBoxes.isEmpty()) throw new IllegalStateException("末影箱中没有足够补给");
                boxIndex = 0;
                enterSupplyStep(SupplyStep.WITHDRAW_BOX);
            }
            case WITHDRAW_BOX -> withdrawBox();
            case PLACE_BOX -> {
                if (!placementPrepared) {
                    prepareItemAt(boxPos, boxInventorySlot);
                    placementPrepared = true;
                }
                if (!placementClicked && tick - supplyActionTick >= 3) {
                    placeBlockAt(boxPos);
                    placementClicked = true;
                    supplyActionTick = tick;
                }
                if (placementClicked && tick - supplyActionTick >= 5 && mc.world.getBlockState(boxPos).getBlock() instanceof ShulkerBoxBlock) {
                    enterSupplyStep(SupplyStep.OPEN_BOX);
                } else if (phaseTicks > 40) throw new IllegalStateException("补给盒放置失败");
            }
            case OPEN_BOX -> {
                if (!supplyActionStarted) {
                    interactBlock(boxPos);
                    supplyActionStarted = true;
                }
                if (isBoxOpen()) enterSupplyStep(SupplyStep.TRANSFER);
                else if (phaseTicks > 40) throw new IllegalStateException("补给盒打开失败");
            }
            case TRANSFER -> transferSupplies();
            case MINE_BOX -> mineSupplyBox();
            case PICKUP_BOX -> pickupSupplyBox();
            case RETURN_BOX -> returnSupplyBox();
            case CLOSE_ENDER -> {
                mc.player.closeHandledScreen();
                enterSupplyStep(SupplyStep.MINE_ENDER);
            }
            case MINE_ENDER -> mineEnderChest();
            case PICKUP_ENDER -> pickupEnderChest();
            case DONE -> machine.supplyDone();
        }
    }

    private void withdrawBox() {
        if (!isEnderOpen()) throw new IllegalStateException("末影箱界面已关闭");
        BoxSupply supply = supplyBoxes.get(boxIndex);
        if (!supplyActionStarted) {
            boxInventorySlot = findBoxHotbarSlot();
            if (boxInventorySlot < 0) throw new IllegalStateException("没有快捷栏空位取出补给盒");
            int rows = containerRows();
            int sourceSlot = supply.slot();
            int targetSlot = HellFlightLogic.hotbarScreenSlot(rows, boxInventorySlot);
            queueClick(sourceSlot, SlotActionType.PICKUP);
            queueClick(targetSlot, SlotActionType.PICKUP);
            queueClick(sourceSlot, SlotActionType.PICKUP);
            afterClicks = () -> {
                mc.player.closeHandledScreen();
                if (!mc.player.getInventory().getStack(boxInventorySlot).isEmpty()
                    && mc.player.getInventory().getStack(boxInventorySlot).getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    boxPos = findPlaceSpot(enderPos);
                    if (boxPos == null) throw new IllegalStateException("附近没有补给盒放置位置");
                    enterSupplyStep(SupplyStep.PLACE_BOX);
                } else {
                    throw new IllegalStateException("补给盒取出失败");
                }
            };
            supplyActionStarted = true;
        }
    }

    private void transferSupplies() {
        if (!isBoxOpen()) throw new IllegalStateException("补给盒界面已关闭");
        if (!supplyActionStarted) {
            ScreenHandler handler = mc.player.currentScreenHandler;
            int rows = containerRows();
            for (int slot = 0; slot < rows * 9; slot++) {
                ItemStack stack = handler.getSlot(slot).getStack();
                if (isSupplyItem(stack.getItem())) queueClick(slot, SlotActionType.QUICK_MOVE);
            }
            afterClicks = () -> {
                mc.player.closeHandledScreen();
                if (!needsMet()) throw new IllegalStateException("背包空间不足或补给转移失败");
                enterSupplyStep(SupplyStep.MINE_BOX);
            };
            supplyActionStarted = true;
        }
    }

    private void mineSupplyBox() {
        if (!supplyActionStarted) {
            Block block = mc.world.getBlockState(boxPos).getBlock();
            if (!(block instanceof ShulkerBoxBlock)) throw new IllegalStateException("补给盒方块已消失");
            boxPickupBaseline = countShulkers();
            shulkerSlotBaseline.clear();
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    shulkerSlotBaseline.put(slot, stack.getCount());
                }
            }
            pickedBoxSlot = -1;
            PathManagers.get().mine(block);
            supplyActionStarted = true;
        }
        if (mc.world.getBlockState(boxPos).isAir()) {
            PathManagers.get().stop();
            enterSupplyStep(SupplyStep.PICKUP_BOX);
        } else if (phaseTicks > 100) {
            throw new IllegalStateException("挖掘补给盒超时");
        }
    }

    private void pickupSupplyBox() {
        if (phaseTicks > 30) throw new IllegalStateException("补给盒拾取超时");
        if (!supplyActionStarted) {
            Box search = new Box(boxPos).expand(3);
            ItemEntity item = mc.world.getEntitiesByClass(ItemEntity.class, search, entity -> isShulkerStack(entity.getStack()))
                .stream().min(java.util.Comparator.comparingDouble(entity -> entity.squaredDistanceTo(Vec3d.ofCenter(boxPos)))).orElse(null);
            if (item == null) return;
            PathManagers.get().moveTo(item.getBlockPos(), false);
            supplyActionStarted = true;
        }
        if (countShulkers() > boxPickupBaseline) {
            pickedBoxSlot = findNewShulkerSlot();
            if (pickedBoxSlot < 0) throw new IllegalStateException("补给盒拾取后未进入背包");
            PathManagers.get().stop();
            enterSupplyStep(SupplyStep.RETURN_BOX);
        }
    }

    private void returnSupplyBox() {
        if (!supplyActionStarted) {
            interactBlock(enderPos);
            supplyActionStarted = true;
        }
        if (!isEnderOpen() || clicksQueued) return;
        int inventorySlot = pickedBoxSlot >= 0 ? pickedBoxSlot : findNewShulkerSlot();
        if (inventorySlot < 0) throw new IllegalStateException("没有找到挖回的补给盒");
        int rows = containerRows();
        int playerSlot = HellFlightLogic.inventoryScreenSlot(rows, inventorySlot);
        int sourceSlot = supplyBoxes.get(boxIndex).slot();
        if (!mc.player.currentScreenHandler.getSlot(sourceSlot).getStack().isEmpty()) {
            sourceSlot = firstEmptyContainerSlot(rows * 9);
        }
        if (sourceSlot < 0) throw new IllegalStateException("末影箱没有空位归还补给盒");
        queueClick(playerSlot, SlotActionType.PICKUP);
        queueClick(sourceSlot, SlotActionType.PICKUP);
        queueClick(playerSlot, SlotActionType.PICKUP);
        afterClicks = () -> {
            mc.player.closeHandledScreen();
            boxIndex++;
            if (boxIndex < supplyBoxes.size() && !needsMet()) {
                enterSupplyStep(SupplyStep.WITHDRAW_BOX);
            } else {
                enterSupplyStep(SupplyStep.CLOSE_ENDER);
            }
        };
        clicksQueued = true;
    }

    private void mineEnderChest() {
        if (!supplyActionStarted) {
            Block block = mc.world.getBlockState(enderPos).getBlock();
            if (block != Blocks.ENDER_CHEST) throw new IllegalStateException("末影箱方块已消失");
            enderPickupBaseline = countItem(Items.ENDER_CHEST);
            PathManagers.get().mine(block);
            supplyActionStarted = true;
        }
        if (mc.world.getBlockState(enderPos).isAir()) {
            PathManagers.get().stop();
            enterSupplyStep(SupplyStep.PICKUP_ENDER);
        } else if (phaseTicks > 100) {
            throw new IllegalStateException("挖掘末影箱超时");
        }
    }

    private void pickupEnderChest() {
        if (phaseTicks > 30) throw new IllegalStateException("末影箱拾取超时");
        if (!supplyActionStarted) {
            Box search = new Box(enderPos).expand(3);
            ItemEntity item = mc.world.getEntitiesByClass(ItemEntity.class, search, entity -> entity.getStack().isOf(Items.ENDER_CHEST))
                .stream().min(java.util.Comparator.comparingDouble(entity -> entity.squaredDistanceTo(Vec3d.ofCenter(enderPos)))).orElse(null);
            if (item == null) return;
            PathManagers.get().moveTo(item.getBlockPos(), false);
            supplyActionStarted = true;
        }
        if (countItem(Items.ENDER_CHEST) > enderPickupBaseline) {
            PathManagers.get().stop();
            enderPos = null;
            enterSupplyStep(SupplyStep.DONE);
        }
    }

    private void selectSupplyBoxes() {
        supplyBoxes.clear();
        ScreenHandler handler = mc.player.currentScreenHandler;
        int size = containerRows() * 9;
        List<HellFlightLogic.BoxCounts> candidates = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
                candidates.add(null);
                continue;
            }
            candidates.add(new HellFlightLogic.BoxCounts(
                countInContainer(stack, Items.FIREWORK_ROCKET),
                countInContainer(stack, Items.EXPERIENCE_BOTTLE),
                countInContainer(stack, foodItem()),
                countInContainer(stack, Items.TOTEM_OF_UNDYING)
            ));
        }
        List<Integer> selected = HellFlightLogic.chooseSupplyBoxes(needs, candidates);
        int fireworks = 0;
        int xp = 0;
        int foodCount = 0;
        int totems = 0;
        for (int slot : selected) {
            HellFlightLogic.BoxCounts counts = candidates.get(slot);
            supplyBoxes.add(new BoxSupply(slot, counts.fireworks(), counts.xp(), counts.food(), counts.totems()));
            fireworks += counts.fireworks();
            xp += counts.xp();
            foodCount += counts.food();
            totems += counts.totems();
        }
        if (fireworks < needs.fireworks() || xp < needs.xp() || foodCount < needs.food() || totems < needs.totems()) {
            throw new IllegalStateException("末影箱中的补给物品不足");
        }
    }

    private boolean needsMet() {
        return countItem(Items.FIREWORK_ROCKET) >= fireworkStacks.get() * 64
            && countItem(Items.EXPERIENCE_BOTTLE) >= xpStacks.get() * 64
            && countItem(foodItem()) >= foodStacks.get() * 64
            && countItem(Items.TOTEM_OF_UNDYING) >= totemTarget.get();
    }

    private boolean isSupplyItem(Item item) {
        return item == Items.FIREWORK_ROCKET || item == Items.EXPERIENCE_BOTTLE
            || item == foodItem() || item == Items.TOTEM_OF_UNDYING;
    }

    private int countInContainer(ItemStack box, Item item) {
        ContainerComponent container = box.get(DataComponentTypes.CONTAINER);
        if (container == null) return 0;
        int count = 0;
        for (ItemStack stack : container.iterateNonEmpty()) {
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private void queueClick(int slot, SlotActionType type) {
        clickQueue.add(new ClickOp(slot, type));
    }

    private void tickClicks() {
        if (clickQueue.isEmpty()) {
            if (afterClicks != null) {
                Runnable action = afterClicks;
                afterClicks = null;
                action.run();
            }
            return;
        }
        if (phaseTicks % 2 != 0) return;
        ClickOp click = clickQueue.removeFirst();
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, click.slot(), 0, click.type(), mc.player);
    }

    private void takeoffTick() {
        ensureElytra();
        if (mc.player.isInLava()) {
            escapeLava();
            return;
        }
        if (mc.player.isGliding()) {
            releaseKeys();
            startBaritoneFlight();
            machine.beginFlight();
            phaseTicks = 0;
            info("鞘翅已展开，开始飞行");
            return;
        }
        if (phaseTicks > 240) throw new IllegalStateException("起飞超时");

        if (mc.player.isOnGround()) {
            if (!mc.world.getBlockState(mc.player.getBlockPos().up(2)).getCollisionShape(mc.world, mc.player.getBlockPos().up(2)).isEmpty()) {
                walkForTakeoff();
                return;
            }
            if (takeoffStage == 0) {
                takeoffStartY = mc.player.getY();
                mc.options.jumpKey.setPressed(true);
                takeoffStage = 1;
            } else if (mc.player.getY() <= takeoffStartY + 0.5) {
                mc.options.jumpKey.setPressed(true);
            } else {
                mc.options.jumpKey.setPressed(false);
                mc.player.setPitch(-90);
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                if (!takeoffBoostUsed) {
                    useFirework(true);
                    takeoffBoostUsed = true;
                }
                takeoffStage = 2;
            }
        } else {
            mc.options.jumpKey.setPressed(false);
            if (takeoffStage < 2 || phaseTicks % 5 == 0) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                takeoffStage = 2;
            }
        }
    }

    private void walkForTakeoff() {
        if (walkTarget == null || !walkIssued) {
            walkTarget = findTakeoffSpot();
            if (walkTarget == null) throw new IllegalStateException("附近没有可起飞位置");
            PathManagers.get().moveToPrecise(walkTarget);
            walkIssued = true;
        }
        if (horizontalDistance(mc.player.getEntityPos(), Vec3d.ofCenter(walkTarget)) < 1.0
            && Math.abs(mc.player.getY() - walkTarget.getY()) < 2.0) {
            PathManagers.get().stop();
            walkTarget = null;
            walkIssued = false;
            takeoffStage = 0;
        }
    }

    private void flightTick() {
        if (mc.player.isInLava()) {
            escapeLava();
            return;
        }
        if (!mc.player.isGliding()) {
            if (tick - baritoneStartTick <= 15) return;
            requestBaritoneLanding("鞘翅飞行中断，准备着陆");
            return;
        }
        if (autoEatTick()) return;
        if (chunkLoadTick()) return;

        if (elytraNearlyBroken()) {
            if (!mc.world.getBiome(mc.player.getBlockPos()).matchesKey(NETHER_WASTES)) {
                throw new IllegalStateException("鞘翅耐久过低，只能在地狱荒野修复");
            }
            elytraProcess().pathTo(mc.player.getBlockPos());
            machine.requestRepair();
            phaseTicks = 0;
            info("鞘翅耐久不足，准备经验瓶修复");
            return;
        }
        if (HellFlightLogic.shouldLandForSupply(
            countItem(Items.FIREWORK_ROCKET), countItem(Items.EXPERIENCE_BOTTLE),
            countItem(foodItem()), countItem(Items.TOTEM_OF_UNDYING), false)) {
            requestBaritoneLanding("物资不足，准备着陆补给");
            return;
        }
        if (horizontalDistance(mc.player.getEntityPos(), destination) < 300
            || tick - baritoneStartTick > 15 && !elytraProcess().isActive()) {
            requestBaritoneLanding("已接近目的地或 Baritone 路径完成，准备着陆");
            return;
        }

        Vec3d velocity = mc.player.getVelocity();
        boolean boosted = hasAttachedFirework();
        boolean descendingNearTarget = velocity.y < 0.5;
        boolean farFromTarget = horizontalDistance(mc.player.getEntityPos(), destination) > 5;
        if (HellFlightLogic.shouldUseFirework(
            velocity.horizontalLengthSquared(), fireworkSpeed.get(), boosted, fireworkCooldown == 0,
            descendingNearTarget, farFromTarget)) {
            useFirework(false);
        }
        if (mc.player.getY() < 20) throw new IllegalStateException("飞行高度过低");
    }

    private IElytraProcess elytraProcess() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        return baritone.getElytraProcess();
    }

    private void startBaritoneFlight() {
        IElytraProcess process = elytraProcess();
        process.repackChunks();
        process.pathTo(BlockPos.ofFloored(destination));
        baritoneStartTick = tick;
    }

    private void requestBaritoneLanding(String reason) {
        elytraProcess().pathTo(mc.player.getBlockPos());
        machine.requestLandingSearch();
        phaseTicks = 0;
        info(reason);
    }

    private boolean autoEatTick() {
        if (eatTicks > 0) {
            eatTicks++;
            boolean abort = mc.player.isInLava() || mc.player.isOnGround() || mc.player.getVelocity().length() < 0.7;
            if (abort || eatTicks > 35) {
                stopEating();
                return false;
            }
            selectSlot(eatSlot);
            mc.options.useKey.setPressed(true);
            return true;
        }
        boolean hungry = mc.player.getHungerManager().getFoodLevel() < 16
            || mc.player.getHealth() < 15 && mc.player.getHungerManager().getFoodLevel() < 20;
        if (!hungry || mc.player.getVelocity().length() <= 1.3 || mc.player.isUsingItem()) return false;
        eatSlot = findHotbar(foodItem());
        if (eatSlot < 0) throw new IllegalStateException("没有可用食物");
        selectSlot(eatSlot);
        mc.options.useKey.setPressed(true);
        eatTicks = 1;
        return true;
    }

    private void stopEating() {
        mc.options.useKey.setPressed(false);
        mc.interactionManager.stopUsingItem(mc.player);
        eatTicks = 0;
        eatSlot = -1;
    }

    private boolean chunkLoadTick() {
        float unloaded = unloadedChunkRatio();
        if (!chunkWaiting && tick % 10 == 5 && unloaded > 0.4f && hasAirBubble(mc.player.getBlockPos())) {
            chunkWaiting = true;
            slowChunkStrikes++;
            if (dynamicTimer.get()) HellFlightRuntime.lowerTimer();
            info("区块加载缓慢，暂停等待");
        }
        if (!chunkWaiting) {
            if (slowChunkStrikes == 0 && tick % 120 == 0 && dynamicTimer.get()) HellFlightRuntime.recoverTimer();
            return false;
        }
        mc.options.forwardKey.setPressed(false);
        mc.player.setYaw(mc.player.getYaw() + 180);
        mc.player.setPitch(0);
        if (unloaded <= 0.05f || !hasAirBubble(mc.player.getBlockPos())) {
            chunkWaiting = false;
            info("区块加载恢复");
        }
        return true;
    }

    private float unloadedChunkRatio() {
        int radius = Math.min(5, Math.max(1, mc.options.getClampedViewDistance()));
        int total = 0;
        int unloaded = 0;
        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                total++;
                if (!mc.world.getChunkManager().isChunkLoaded(centerX + dx, centerZ + dz)) unloaded++;
            }
        }
        return total == 0 ? 0 : (float) unloaded / total;
    }

    private void escapeLava() {
        if (lavaTicks == 0) {
            info("检测到岩浆，尝试脱离");
            mc.player.setPitch(-90);
            useFirework(true);
            if (mc.player.isOnGround()) mc.options.jumpKey.setPressed(true);
        }
        lavaTicks++;
        mc.player.setPitch(-90);
        if (!mc.player.isInLava()) {
            mc.options.jumpKey.setPressed(false);
            lavaTicks = 0;
            return;
        }
        if (lavaTicks > 40) {
            mc.options.jumpKey.setPressed(false);
            lavaTicks = 0;
            throw new IllegalStateException("逃离岩浆超时");
        }
    }

    private void landingSearchTick() {
        releaseKeys();
        if (!mc.world.getBiome(mc.player.getBlockPos()).matchesKey(NETHER_WASTES)) {
            throw new IllegalStateException("只有地狱荒野可执行安全着陆补给");
        }
        if (elytraProcess().isActive()) {
            if (phaseTicks > 600) throw new IllegalStateException("Baritone 着陆超时");
            return;
        }
        if (!mc.player.isOnGround()) {
            if (phaseTicks > 200) throw new IllegalStateException("等待落地超时");
            return;
        }
        landingPos = validLandingSpot(mc.player.getBlockPos()) ? mc.player.getBlockPos() : findLandingSpot();
        if (landingPos == null) throw new IllegalStateException("附近没有安全着陆点");
        PathManagers.get().stop();
        machine.beginLanding();
        phaseTicks = 0;
        info("已安全着陆");
    }

    private void landingTick() {
        if (landingPos == null) {
            machine.landingFailed();
            phaseTicks = 0;
            return;
        }
        Vec3d target = Vec3d.ofCenter(landingPos);
        double horizontal = horizontalDistance(mc.player.getEntityPos(), target);
        if (mc.player.isOnGround() && horizontal <= 2.0) {
            boolean arrived = HellFlightLogic.arrived(Math.sqrt(mc.player.getBlockPos().getSquaredDistance(BlockPos.ofFloored(destination))));
            releaseKeys();
            if (arrived) {
                info("已到达目的地，任务完成");
                machine.landed(true);
                cleanup();
            } else {
                info("本段着陆完成，进入下一段补给");
                machine.landed(false);
                segmentStartTick = tick;
                phaseTicks = 0;
                landingPos = null;
                takeoffStage = 0;
                takeoffBoostUsed = false;
            }
            return;
        }
        if (mc.player.getY() < landingPos.getY() - 24 || phaseTicks > 400) {
            badLandingSpots.add(landingPos);
            warning("着陆失败，重新寻找落点");
            machine.landingFailed();
            phaseTicks = 0;
            landingPos = null;
            return;
        }
        releaseKeys();
        if (!mc.player.isOnGround() && phaseTicks > 200) {
            badLandingSpots.add(landingPos);
            machine.landingFailed();
            phaseTicks = 0;
            landingPos = null;
        }
    }

    private void repairTick() {
        switch (machine.repairStep()) {
            case LANDING -> {
                if (elytraProcess().isActive()) {
                    if (phaseTicks > 600) throw new IllegalStateException("修复着陆超时");
                    return;
                }
                if (!mc.player.isOnGround()) {
                    if (phaseTicks > 200) throw new IllegalStateException("等待修复落地超时");
                    return;
                }
                PathManagers.get().stop();
                enterRepairStep(RepairStep.PREPARE);
            }
            case PREPARE -> {
                if (xpStacks.get() <= 0) throw new IllegalStateException("经验瓶组数为 0，无法修复鞘翅");
                if (countItem(Items.EXPERIENCE_BOTTLE) < HellFlightLogic.LAND_XP) {
                    landingPos = mc.player.getBlockPos();
                    machine.repairNeedsSupply();
                    phaseTicks = 0;
                    info("经验瓶不足，转进补给流程");
                    return;
                }
                repairBottleSlot = findHotbar(Items.EXPERIENCE_BOTTLE);
                if (repairBottleSlot < 0) {
                    ensureHotbar(Items.EXPERIENCE_BOTTLE, 5);
                    repairBottleSlot = findHotbar(Items.EXPERIENCE_BOTTLE);
                }
                if (repairBottleSlot < 0) throw new IllegalStateException("没有可用经验瓶");
                repairAttempts = 0;
                enterRepairStep(RepairStep.THROWING);
            }
            case THROWING -> {
                ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (!chest.isOf(Items.ELYTRA)) throw new IllegalStateException("未穿戴鞘翅");
                if (chest.getDamage() < 25) {
                    enterRepairStep(RepairStep.RETURN);
                    return;
                }
                if (repairAttempts >= 40) throw new IllegalStateException("经验瓶修复鞘翅失败");
                if (phaseTicks % 4 == 0) {
                    mc.player.setPitch(90);
                    selectSlot(repairBottleSlot);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    repairAttempts++;
                }
            }
            case RETURN -> {
                mc.options.useKey.setPressed(false);
                int fireworkSlot = findHotbar(Items.FIREWORK_ROCKET);
                if (fireworkSlot >= 0) selectSlot(fireworkSlot);
                enterRepairStep(RepairStep.RESUME);
            }
            case RESUME -> {
                startBaritoneFlight();
                machine.resumeFlight();
                phaseTicks = 0;
                info("鞘翅修复完成，继续飞行");
            }
        }
    }

    private void enterRepairStep(RepairStep step) {
        if (step != machine.repairStep() && !machine.setRepairStep(step)) {
            throw new IllegalStateException("修复状态转换无效");
        }
        phaseTicks = 0;
        if (debug.get()) info("修复状态：" + step.label());
    }

    private BlockPos findLandingSpot() {
        BlockPos start = mc.player.getBlockPos();
        PriorityQueue<BlockPos> queue = new PriorityQueue<>(Comparator.<BlockPos>comparingInt(pos -> (pos.getX() - start.getX()) * (pos.getX() - start.getX())
                + (pos.getZ() - start.getZ()) * (pos.getZ() - start.getZ()))
            .thenComparingInt(pos -> -pos.getY()));
        Set<BlockPos> visited = new HashSet<>();
        visited.add(start);
        queue.add(start);
        int checked = 0;
        while (!queue.isEmpty() && checked++ < 4096) {
            BlockPos pos = queue.poll();
            if (!mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                || pos.getY() < 32 || pos.getY() > 120 || !mc.world.getBlockState(pos).isAir()) continue;
            if (validLandingSpot(pos) && !badLandingSpots.contains(pos)) return pos;
            addNeighbor(queue, visited, pos.up());
            addNeighbor(queue, visited, pos.down());
            addNeighbor(queue, visited, pos.north());
            addNeighbor(queue, visited, pos.south());
            addNeighbor(queue, visited, pos.east());
            addNeighbor(queue, visited, pos.west());
        }
        return null;
    }

    private boolean validLandingSpot(BlockPos pos) {
        BlockPos ground = pos.down();
        if (!mc.world.getBlockState(ground).isSolidBlock(mc.world, ground)
            || mc.world.getBlockState(ground).isOf(Blocks.LAVA)) return false;
        for (int y = 0; y < 15; y++) {
            if (!mc.world.getBlockState(pos.up(y)).isAir()) return false;
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 23; dy <= 25; dy++) {
                    if (!mc.world.getBlockState(pos.add(dx, dy, dz)).isAir()) return false;
                }
            }
        }
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof Monster && !(entity instanceof ZombifiedPiglinEntity)
                && entity.getBlockPos().getSquaredDistance(pos) < 24 * 24) return false;
        }
        return true;
    }

    private void addNeighbor(PriorityQueue<BlockPos> queue, Set<BlockPos> visited, BlockPos pos) {
        if (visited.add(pos)) queue.add(pos);
    }

    private void protectFireball() {
        if (!fireballProtect.get()) return;
        double range = mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ENTITY_INTERACTION_RANGE) + 1;
        Box box = new Box(
            mc.player.getX() - range, mc.player.getY() - range, mc.player.getZ() - range,
            mc.player.getX() + range, mc.player.getY() + range, mc.player.getZ() + range
        );
        List<FireballEntity> fireballs = mc.world.getEntitiesByType(EntityType.FIREBALL, box, entity -> true);
        if (fireballs.isEmpty()) {
            ignoredFireballs.clear();
            return;
        }
        if (fireballs.size() > 1) throw new IllegalStateException("附近有多个恶魂火球");
        FireballEntity fireball = fireballs.getFirst();
        if (ignoredFireballs.contains(fireball.getUuid())) return;
        ignoredFireballs.add(fireball.getUuid());
        Vec3d target = fireball.getEntityPos().add(0, 0.5, 0);
        mc.player.setYaw(yawTo(mc.player.getEyePos(), target));
        mc.player.setPitch(pitchTo(mc.player.getEyePos(), target));
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
        mc.interactionManager.attackEntity(mc.player, fireball);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean hasAttachedFirework() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework && firework.getOwner() == mc.player) return true;
        }
        return false;
    }

    private void useFirework(boolean force) {
        int slot = findHotbar(Items.FIREWORK_ROCKET);
        if (slot < 0) {
            FindItemResult found = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!found.found()) throw new IllegalStateException("没有烟花");
            InvUtils.move().from(found.slot()).toHotbar(1);
            slot = findHotbar(Items.FIREWORK_ROCKET);
        }
        if (slot < 0) throw new IllegalStateException("无法把烟花放到快捷栏");
        selectSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (!force) fireworkCooldown = 10;
    }

    private boolean elytraNearlyBroken() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && chest.getDamage() > chest.getMaxDamage() - 40;
    }

    private BlockPos findPlaceSpot(BlockPos exclude) {
        BlockPos origin = mc.player.getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (exclude != null && pos.isWithinDistance(exclude, 1)) continue;
                    if (!mc.world.getBlockState(pos).isAir() && !mc.world.getBlockState(pos).isReplaceable()) continue;
                    if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) continue;
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    return pos;
                }
            }
        }
        return null;
    }

    private BlockPos findTakeoffSpot() {
        BlockPos center = mc.player.getBlockPos();
        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, 0, dz);
                    if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) continue;
                    if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()
                        || !mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty()) continue;
                    return pos;
                }
            }
        }
        return null;
    }

    private boolean isEnderOpen() {
        return mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler
            && handler.getRows() == 6
            && enderPos != null
            && mc.world.getBlockState(enderPos).isOf(Blocks.ENDER_CHEST);
    }

    private boolean isBoxOpen() {
        if (boxPos == null || !(mc.world.getBlockState(boxPos).getBlock() instanceof ShulkerBoxBlock)) return false;
        return mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler
            || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler && handler.getRows() == 3;
    }

    private int containerRows() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) return handler.getRows();
        if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) return 3;
        return -1;
    }

    private int firstEmptyContainerSlot(int size) {
        for (int slot = 0; slot < size; slot++) {
            if (mc.player.currentScreenHandler.getSlot(slot).getStack().isEmpty()) return slot;
        }
        return -1;
    }

    private int findBoxHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !isSupplyItem(stack.getItem()) && stack.getItem() != Items.ENDER_CHEST
                && stack.getItem() != Items.DIAMOND_PICKAXE && stack.getItem() != Items.NETHERITE_PICKAXE
                && stack.getItem() != Items.DIAMOND_SWORD && stack.getItem() != Items.NETHERITE_SWORD) return slot;
        }
        return -1;
    }

    private int findNewShulkerSlot() {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) continue;
            if (stack.getCount() > shulkerSlotBaseline.getOrDefault(slot, 0)) return slot;
        }
        return -1;
    }

    private boolean isShulkerStack(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private int countShulkers() {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void prepareItemAt(BlockPos pos, int inventorySlot) {
        if (inventorySlot < 0) throw new IllegalStateException("物品不在快捷栏");
        selectSlot(inventorySlot);
        face(pos);
        supplyActionTick = tick;
    }

    private void placeBlockAt(BlockPos pos) {
        BlockPos support = pos.down();
        Vec3d hitPos = Vec3d.ofCenter(support).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, support, false);
        mc.player.swingHand(Hand.MAIN_HAND);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (!result.isAccepted()) throw new IllegalStateException("方块放置被拒绝");
    }

    private BlockPos findNearbyFire() {
        BlockPos center = mc.player.getBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (mc.world.getBlockState(pos).isOf(Blocks.FIRE)) return pos;
                }
            }
        }
        return null;
    }

    private void attackBlock(BlockPos pos) {
        face(pos);
        mc.interactionManager.attackBlock(pos, Direction.UP);
    }

    private void face(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        mc.player.setYaw(yawTo(mc.player.getEyePos(), center));
        mc.player.setPitch(pitchTo(mc.player.getEyePos(), center));
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    private void interactBlock(BlockPos pos) {
        face(pos);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.player.swingHand(Hand.MAIN_HAND);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (!result.isAccepted()) throw new IllegalStateException("方块交互被拒绝");
    }

    private void selectSlot(int slot) {
        if (mc.player.getInventory().getSelectedSlot() == slot) return;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private int findHotbar(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(item)) return slot;
        }
        return -1;
    }

    private int countItem(Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(item)) count += stack.getCount();
        }
        if (mc.player.getOffHandStack().isOf(item)) count += mc.player.getOffHandStack().getCount();
        return count;
    }

    private Item foodItem() {
        return switch (food.get()) {
            case GOLDEN_CARROT -> Items.GOLDEN_CARROT;
            case GOLDEN_APPLE -> Items.GOLDEN_APPLE;
            case ENCHANTED_GOLDEN_APPLE -> Items.ENCHANTED_GOLDEN_APPLE;
            case COOKED_BEEF -> Items.COOKED_BEEF;
            case COOKED_PORKCHOP -> Items.COOKED_PORKCHOP;
            case COOKED_CHICKEN -> Items.COOKED_CHICKEN;
        };
    }

    private boolean hasAirBubble(BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!mc.world.getBlockState(pos.add(dx, dy, dz)).isAir()) return false;
                }
            }
        }
        return true;
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static HellFlightLogic.Vec vec(Vec3d value) {
        return new HellFlightLogic.Vec(value.x, value.y, value.z);
    }

    private static float yawTo(Vec3d from, Vec3d to) {
        return HellFlightLogic.yawTo(vec(from), vec(to));
    }

    private static float pitchTo(Vec3d from, Vec3d to) {
        return HellFlightLogic.pitchTo(vec(from), vec(to));
    }

    private void info(String message) {
        super.info("【地狱飞行】" + message);
    }

    private void warning(String message) {
        super.warning("【地狱飞行】" + message);
    }

    private void error(String message) {
        super.error("【地狱飞行】" + message);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WSection section = theme.section("飞行状态", true);
        WTable table = section.add(theme.table()).expandX().widget();
        statusLabel = addRow(theme, table, "当前状态", "空闲");
        segmentLabel = addRow(theme, table, "当前段数", "0");
        distanceLabel = addRow(theme, table, "已飞距离", "0 格");
        remainingLabel = addRow(theme, table, "剩余距离", "0 格");
        speedLabel = addRow(theme, table, "平均速度", "0 格/秒");
        etaLabel = addRow(theme, table, "预计剩余", "0 秒");
        updateStatusWidgets();
        return section;
    }

    private WLabel addRow(GuiTheme theme, WTable table, String name, String value) {
        table.add(theme.label(name));
        WLabel label = table.add(theme.label(value)).expandCellX().widget();
        table.row();
        return label;
    }

    private void updateStatusWidgets() {
        if (statusLabel == null) return;
        double remaining = destination == null || mc.player == null
            ? 0 : horizontalDistance(mc.player.getEntityPos(), destination);
        double seconds = Math.max(1.0 / 20.0, totalTicks / 20.0);
        double averageSpeed = distanceTraveled / seconds;
        statusLabel.set(machine.statusText());
        segmentLabel.set(String.valueOf(machine.segment() + 1));
        distanceLabel.set(HellFlightLogic.formatDistance(distanceTraveled));
        remainingLabel.set(HellFlightLogic.formatDistance(remaining));
        speedLabel.set(String.format(Locale.CHINA, "%.1f 格/秒", averageSpeed));
        etaLabel.set(averageSpeed < 0.1 ? "未知" : HellFlightLogic.formatDuration((int) (remaining / averageSpeed * 20)));
    }

    private record ClickOp(int slot, SlotActionType type) {
    }

    private record BoxSupply(int slot, int fireworks, int xp, int food, int totems) {
    }
}
