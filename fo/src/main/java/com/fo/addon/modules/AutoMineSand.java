package com.fo.addon.modules;

import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.Debug;
import com.fo.addon.utils.FacingLogic;
import com.fo.addon.utils.FoodType;
import com.fo.addon.utils.InteractionUtils;
import com.fo.addon.utils.NearestBoxLogic;
import com.fo.addon.utils.NukerMoveLogic;
import com.fo.addon.utils.NukerSphericalLogic;
import com.fo.addon.utils.OpenBoxRetryLogic;
import com.fo.addon.utils.StandSpotLogic;
import com.fo.addon.utils.StoreOpenLogic;
import com.fo.addon.utils.StoreSlotLogic;
import com.fo.addon.utils.SupplyFingerprintLogic;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * FO 自动挖沙
 * - Baritone 模式：调 Baritone mineProcess 自动寻路挖沙（默认）
 * - Nuker 模式：范围极速包挖（独立开关）
 * - FO补给盒：命名潜影盒，自动补铲子/金萝卜/图腾
 * - 存沙：背包满自动存到附近其他潜影盒
 */
public class AutoMineSand extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSupply = settings.createGroup("FO补给盒");
    private final SettingGroup sgStore = settings.createGroup("存沙");

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径").description("搜索沙子/潜影盒的半径")
        .defaultValue(32).min(4).max(200).sliderMin(8).sliderMax(64).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("操作延迟").description("状态机 tick 间隔")
        .defaultValue(5).min(0).max(40).sliderMin(0).sliderMax(20).build());

    private final Setting<Boolean> nukerMode = sgGeneral.add(new BoolSetting.Builder()
        .name("核爆挖掘(Nuker)").description("开启后范围极速挖沙，关闭则用 Baritone 寻路挖")
        .defaultValue(false).build());

    private final Setting<Integer> nukerRange = sgGeneral.add(new IntSetting.Builder()
        .name("核爆范围").description("Nuker 模式下最大挖掘半径")
        .defaultValue(4).min(1).max(6).sliderMin(1).sliderMax(6).build());

    private final Setting<Integer> nukerMaxInstaMine = sgGeneral.add(new IntSetting.Builder()
        .name("核爆发包上限").description("每 tick 最多连续发包次数（默认 30，防服务器踢出）")
        .defaultValue(30).min(1).max(60).sliderMin(1).sliderMax(60).build());

    private final Setting<Integer> nukerMinDy = sgGeneral.add(new IntSetting.Builder()
        .name("核爆纵向下限").description("Nuker 模式下相对玩家脚下可挖的最低层数")
        .defaultValue(-6).min(-6).max(6).sliderMin(-6).sliderMax(6).build());

    private final Setting<Integer> nukerMaxDy = sgGeneral.add(new IntSetting.Builder()
        .name("核爆纵向上限").description("Nuker 模式下相对玩家脚下可挖的最高层数")
        .defaultValue(6).min(-6).max(6).sliderMin(-6).sliderMax(6).build());

    private final Setting<Boolean> nukerRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("核爆自动转头").description("Nuker 模式下自动转头看向目标方块（默认不转头，更防踢）")
        .defaultValue(false).build());

    private final Setting<String> supplyBoxName = sgSupply.add(new StringSetting.Builder()
        .name("补给盒关键词").description("潜影盒命名包含此关键词即识别为 FO 补给盒")
        .defaultValue("FO补给").build());

    private final Setting<Integer> shovelMinDurability = sgSupply.add(new IntSetting.Builder()
        .name("铲子最低耐久").description("耐久低于此值自动换新铲")
        .defaultValue(10).min(1).max(100).sliderMin(1).sliderMax(100).build());

    private final Setting<FoodType> foodType = sgSupply.add(new EnumSetting.Builder<FoodType>()
        .name("补给食物种类").description("背包该食物为 0 时从补给盒补一整组（64 个）；四选一")
        .defaultValue(FoodType.GOLDEN_CARROT).build());

    private final Setting<Integer> totemTarget = sgSupply.add(new IntSetting.Builder()
        .name("图腾目标数量").description("从补给盒补到多少个")
        .defaultValue(3).min(1).max(10).sliderMin(1).sliderMax(10).build());

    private final Setting<Boolean> autoStore = sgStore.add(new BoolSetting.Builder()
        .name("自动存沙").description("背包满自动存到附近潜影盒")
        .defaultValue(true).build());

    private final Setting<Integer> storeEmptySlots = sgStore.add(new IntSetting.Builder()
        .name("背包空槽阈值").description("空槽位少于此值触发存沙")
        .defaultValue(2).min(0).max(9).sliderMin(0).sliderMax(9).build());

    private final Setting<SettingColor> supplyBoxColor = sgStore.add(new ColorSetting.Builder()
        .name("补给盒高亮颜色").description("FO补给盒的高亮框颜色")
        .defaultValue(new SettingColor(0, 255, 0, 77)).build());

    private final Setting<SettingColor> storeBoxColor = sgStore.add(new ColorSetting.Builder()
        .name("存沙盒高亮颜色").description("存沙潜影盒的高亮框颜色")
        .defaultValue(new SettingColor(255, 255, 0, 77)).build());

    private final Setting<Boolean> highlightBoxes = sgStore.add(new BoolSetting.Builder()
        .name("高亮潜影盒").description("在世界中高亮补给盒和存沙盒")
        .defaultValue(true).build());

    private enum State { MINING, INIT_SCAN, GOING_SUPPLY, OPEN_SUPPLY, GOING_STORE, OPEN_STORE }

    /** V4.23: 补给流程任务类型——一次补给流程只做一件事（铲子/食物/图腾），补完关盒回挖矿 */
    private enum SupplyJob { NONE, SHOVEL, FOOD, TOTEM }

    private State state = State.MINING;
    private int tickTimer = 0;
    private int shulkerWaitTimer = 0;
    private boolean waitingShulkerOpen = false;
    private boolean justClosed = false;  // V4.17: INIT_SCAN 关屏后只等1tick就推进，不固定等5tick
    private BlockPos initScanStand = null;    // V4.17: 当前盒子"面前"站立点缓存（每盒只算一次）
    private BlockPos initScanStandBox = null; // V4.17: 站立点缓存对应的盒子
    private int openFailCount = 0;         // INIT_SCAN 连续打不开盒子的次数
    private BlockPos storeBoxPos = null;
    private final Set<BlockPos> fullStoreBoxes = new HashSet<>();
    private BlockPos nukerTarget = null;   // Nuker 当前挖掘目标（SlimefunHelper lastMinePos）
    private java.util.List<int[]> nukerOffsets = null; // 球型范围相对坐标（按距离升序，懒生成）
    private double nukerOffsetsRange = -1; // 上次生成偏移表用的半径
    private int storeTickCounter = 0;     // 存沙等服务器同步 tick
    private int storeStuckSlot = -1;       // 上次 shift 点击的沙槽(界面槽位)
    private int storeStuckTicks = 0;      // 卡住检测计数
    private int supplyStuckSlot = -1;      // 补给时上次点的槽位
    private BlockPos supplyBoxPos = null;  // 缓存补给盒位置
    private BlockPos storeGoal = null;     // V4.16: 存沙寻路目标（盒子旁站立点，带 Y）
    private boolean storeGoalIgnoreY = false; // V4.16: 站立点找不到（盒子浮空）退化为 GoalXZ
    private BlockPos supplyGoal = null;    // V4.16: 补给寻路目标（盒子旁站立点，带 Y）
    private boolean supplyGoalIgnoreY = false; // V4.16: 同上退化标志
    private final java.util.List<BlockPos> initScanBoxes = new java.util.ArrayList<>(); // 待扫描的盒子
    private int initScanIndex = 0;         // 扫描到第几个
    private final Set<BlockPos> nameSyncedBoxes = new HashSet<>();  // INIT_SCAN 成功打开过（数据已同步）的盒子
    private final Set<BlockPos> unopenableBoxes = new HashSet<>();  // 存沙/补给时反复打不开被放弃的盒子
    private int openRetryCount = 0;        // 当前盒子连续开盒失败次数（StoreOpenLogic 用）
    private final Set<BlockPos> supplyLikeBoxes = new HashSet<>(); // V4.23: 内容指纹候选（盒内含钻石/合金铲，名字丢失兜底）
    private SupplyJob supplyJob = SupplyJob.NONE; // V4.23: 本次补给流程任务（一次只做一件事）
    private int supplyPhase = 0;            // V4.25: 补铲子流程阶段（0=未开始 1=已放旧铲 2=已拿新铲）
    private int supplyWaitTick = 0;         // V4.26: 补给回写等待超时计数（防切主手失败时无限等待卡死）

    private static final List<Item> SHOVELS = Arrays.asList(
        Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL, Items.IRON_SHOVEL,
        Items.STONE_SHOVEL, Items.WOODEN_SHOVEL
    );

    public AutoMineSand() {
        super(com.fo.addon.AddonTemplate.CATEGORY, "FO 自动挖沙", "自动寻路挖沙 + FO补给盒自动补铲/食物/图腾 + 存沙");
    }

    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            error("Baritone 不可用！");
            toggle();
            return;
        }
        // V4.22: 联动开启自动扔垃圾（强制白名单+确保沙在保留列表）。
        // 注意顺序：Baritone 检查通过后才开——若上方已 toggle() 关闭，
        // onDeactivate 会触发 disableForMineSandLink，此时标记未设=无操作，不会误关。
        AutoTrash trash = Modules.get().get(AutoTrash.class);
        if (trash != null) trash.enableForMineSandLink();
        state = State.INIT_SCAN;
        tickTimer = 0;
        shulkerWaitTimer = 0;
        waitingShulkerOpen = false;
        justClosed = false;
        initScanStand = null;
        initScanStandBox = null;
        storeBoxPos = null;
        supplyBoxPos = null;
        fullStoreBoxes.clear();
        storeTickCounter = 0;
        storeStuckSlot = -1;
        storeStuckTicks = 0;
        supplyStuckSlot = -1;
        initScanBoxes.clear();
        initScanIndex = 0;
        nameSyncedBoxes.clear();
        unopenableBoxes.clear();
        supplyLikeBoxes.clear();
        supplyJob = SupplyJob.NONE;
        supplyPhase = 0;
        supplyWaitTick = 0;
        openRetryCount = 0;
        nukerTarget = null;
        PathManagers.get().protectShulkerBoxes(true);

        // 收集附近所有潜影盒，准备逐个打开同步名字
        collectNearbyShulkers(initScanBoxes);
        if (initScanBoxes.isEmpty()) {
            error("附近 32 格内没有潜影盒，模块停止");
            toggle();
            return;
        }
        info("FO 自动挖沙启动：正在扫描附近 " + initScanBoxes.size() + " 个潜影盒...");
    }

    @Override
    public void onDeactivate() {
        // V4.22: 挖沙停止（手动关/模块自停/被禁用都走这里）→ 关闭挖沙联动开启的自动扔垃圾；
        // 用户手动开的自动扔垃圾不受影响（联动来源标记为空则不关）。
        AutoTrash trash = Modules.get().get(AutoTrash.class);
        if (trash != null) trash.disableForMineSandLink();
        PathManagers.get().stop();
        PathManagers.get().protectShulkerBoxes(false);
        nukerTarget = null;
        info("FO 自动挖沙已停止");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isUsingItem()) return;

        // 开着潜影盒时跳过 delay，每 tick 都处理存取
        boolean inShulker = mc.currentScreen instanceof ShulkerBoxScreen;
        // V4.19: INIT_SCAN/GOING_*/OPEN_* 状态跳过操作延迟——寻路到达、开盒重试必须
        // 立即响应（delay=5 时 40 tick 超时被放大成 10 秒，卡顿感来自这里）；
        // 只有 MINING 保留节流（挖沙发包本身不需要每 tick 决策）。
        boolean fastState = state == State.INIT_SCAN || state == State.GOING_SUPPLY
            || state == State.OPEN_SUPPLY || state == State.GOING_STORE || state == State.OPEN_STORE;

        if (!inShulker && !fastState && tickTimer > 0) { tickTimer--; return; }

        switch (state) {
            case MINING -> tickMining();
            case INIT_SCAN -> tickInitScan();
            case GOING_SUPPLY -> tickGoingSupply();
            case OPEN_SUPPLY -> tickOpenSupply();
            case GOING_STORE -> tickGoingStore();
            case OPEN_STORE -> tickOpenStore();
        }

        if (!inShulker && !fastState) tickTimer = delay.get();
    }

    /** 收集附近所有潜影盒位置 */
    private void collectNearbyShulkers(java.util.List<BlockPos> out) {
        BlockPos c = mc.player.getBlockPos();
        int r = searchRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    if (mc.world.getBlockState(p).getBlock() instanceof ShulkerBoxBlock) {
                        out.add(p.toImmutable());
                    }
                }
    }

    /** INIT_SCAN：逐个打开附近潜影盒同步名字，开完后识别补给盒和存沙盒 */
    private void tickInitScan() {
        // 开着潜影盒 → 等3tick（V4.17，原10tick）让服务器同步潜影盒名字，然后立即关闭
        if (mc.currentScreen instanceof ShulkerBoxScreen) {
            shulkerWaitTimer++;
            if (shulkerWaitTimer >= 3) { // V4.17: 3tick足够服务器同步潜影盒数据（认名字不需要等0.5秒）
                // V4.23: 顺手读盒内内容——含钻石/合金铲 → 记为内容指纹候选
                //（服务器清掉盒子名字后，按内容兜底识别补给盒）
                if (containsFingerprintShovel(mc.player.currentScreenHandler)) {
                    if (initScanIndex < initScanBoxes.size()) {
                        supplyLikeBoxes.add(initScanBoxes.get(initScanIndex).toImmutable());
                    }
                }
                closeScreen();
                // V4.14: 记录"成功打开过=数据已同步"的盒子，存沙候选只从这里选，
                // 防止扫描时被 SKIP 的盒子（名字未知，可能是补给盒）被误当存沙盒
                if (initScanIndex < initScanBoxes.size()) {
                    nameSyncedBoxes.add(initScanBoxes.get(initScanIndex).toImmutable());
                }
                initScanIndex++;
                shulkerWaitTimer = 0;
                justClosed = true;   // V4.17: 关屏后只等1tick就处理下一个盒子
                openFailCount = 0;
            }
            return;
        }
        // V4.17: 刚关屏，只等1tick就直接推进（原在 waitingShulkerOpen 里固定等5tick，太慢）
        if (justClosed) {
            justClosed = false;
            return;
        }
        if (waitingShulkerOpen) {
            shulkerWaitTimer++;
            if (shulkerWaitTimer >= 5) { // 开盒交互后等界面打开/重试，保持5tick防误判打不开
                waitingShulkerOpen = false;
                shulkerWaitTimer = 0;
            }
            return;
        }
        // 扫完了所有盒子，开始识别
        if (initScanIndex >= initScanBoxes.size()) {
            finishInitScan();
            return;
        }
        BlockPos box = initScanBoxes.get(initScanIndex);
        // V4.19: 每个盒子都先寻路到它"面前"的紧邻站立点（水平±1，同层/上一层，绝不到盒子
        // 正下方）再打开——玩家与盒子一格空间都不留，六面检测自然选正对的侧面开盒。
        // 站立点每盒缓存一次，到达判定 = 玩家中心在站立点格 1 格内（原 2 格太松，
        // GoalGetToBlock 停点距站立点 1 格就满足，等于没寻路就开）。
        if (!box.equals(initScanStandBox)) {
            initScanStand = findStandSpot(box);
            initScanStandBox = box;
        }
        boolean atStand = initScanStand != null
            ? mc.player.squaredDistanceTo(
                initScanStand.getX() + 0.5, initScanStand.getY() + 0.5, initScanStand.getZ() + 0.5) <= 1.0
            : mc.player.getBlockPos().isWithinDistance(box, 3); // 盒子悬空无站立点，退化原3格判定
        switch (OpenBoxRetryLogic.decide(atStand, openFailCount, 3)) {
            case TRY_OPEN -> {
                // 已站在盒子紧邻格 → 六面检测开盒（玩家正对的侧面，不会从下面开）
                openShulker(box);
                shulkerWaitTimer = 0;
                waitingShulkerOpen = true;
                openFailCount++;
            }
            case SKIP_BOX -> {
                info("打不开潜影盒，跳过该盒继续扫描");
                openFailCount = 0;
                initScanIndex++;
            }
            case MOVE_TO -> {
                // V4.19: 精确寻路到盒子面前的紧邻站立点（GoalNear 0.5，玩家中心进入格内，
                // 一格空间都不留）；站立点找不到（盒子浮空）才退化为 GoalXZ。
                if (!PathManagers.get().isPathing()) {
                    if (initScanStand != null) {
                        PathManagers.get().moveToPrecise(initScanStand);
                    } else {
                        PathManagers.get().moveTo(box, true);
                    }
                }
            }
        }
    }

    /** 扫描完所有盒子，识别补给盒和存沙盒 */
    private void finishInitScan() {
        PathManagers.get().stop();
        supplyBoxPos = findNamedShulker(supplyBoxName.get());
        if (supplyBoxPos == null) {
            // V4.23: 服务器清除长时间放置盒子名字时，用内容指纹兜底（盒内含钻石/合金铲）
            supplyBoxPos = findNearestSupplyLikeBox();
            if (supplyBoxPos != null) {
                info("补给盒名字丢失，已按盒内钻石/合金铲内容识别");  // V4.24: 不显示坐标（无政府服禁止暴露坐标，防录屏泄露）
            } else {
                error("扫描后仍未找到命名「" + supplyBoxName.get() + "」的补给盒，模块停止");
                toggle();
                return;
            }
        }
        info("补给盒已识别");  // V4.24: 不显示坐标（无政府服禁止暴露坐标）
        BlockPos store = findNearestStoreBox();
        if (store == null) {
            error("未找到存沙潜影盒，模块停止");
            toggle();
            return;
        }
        info("FO 自动挖沙已启动");
        state = State.MINING;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!highlightBoxes.get() || mc.player == null || mc.world == null) return;
        BlockPos c = mc.player.getBlockPos();
        int r = searchRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (!(s.getBlock() instanceof ShulkerBoxBlock)) continue;
                    boolean isSupply = supplyBoxPos != null && p.equals(supplyBoxPos); // V4.24: 内容指纹识别的补给盒（名字丢失）也亮绿色
                    if (!isSupply && mc.world.getBlockEntity(p) instanceof ShulkerBoxBlockEntity be) {
                        var n = be.getCustomName();
                        if (n != null && n.getString().contains(supplyBoxName.get())) {
                            isSupply = true;
                        }
                    }
                    if (isSupply) {
                        // 补给盒绿色高亮
                        event.renderer.box(p, supplyBoxColor.get(), supplyBoxColor.get(), ShapeMode.Both, 0);
                    } else if (!fullStoreBoxes.contains(p)) {
                        // 存沙盒黄色高亮（满了的不亮）
                        event.renderer.box(p, storeBoxColor.get(), storeBoxColor.get(), ShapeMode.Both, 0);
                    }
                }
    }

    private void tickMining() {
        // V4.25(A): 挖掘前自动切铲子到主手——手持不是铲子时，从背包/快捷栏找够耐久的铲子切到选中槽。
        // 不依赖补给流程的落点：任何来源的铲子都自动上手（AutoTree 同款 InvUtils.swap 用法）。
        if (!SHOVELS.contains(mc.player.getMainHandStack().getItem())) {
            FindItemResult r = InvUtils.find(itemStack ->
                SHOVELS.contains(itemStack.getItem()) &&
                (!itemStack.isDamageable() ||
                    (itemStack.getMaxDamage() - itemStack.getDamage()) > shovelMinDurability.get()));
            if (r.found()) InvUtils.swap(r.slot(), false);
        }
        // V4.23: 触发时锁定本次补给任务（一次补给流程只做一件事，补完关盒回挖矿）
        if (needNewShovel()) { supplyJob = SupplyJob.SHOVEL; goToSupply("铲子耐久不足"); return; }
        if (foodCount(foodItem()) < 1) { supplyJob = SupplyJob.FOOD; goToSupply(foodType.get().label + "不足"); return; }
        if (totemCount() < totemTarget.get()) { supplyJob = SupplyJob.TOTEM; goToSupply("图腾不足"); return; }
        // V4.23: 存沙触发需"背包有整组沙"——只有零头（不满一组）时不开盒，
        // 否则开盒→搬不动→关盒→又触发，死循环。零头沙留背包，挖沙时自动合并攒满一组。
        if (autoStore.get() && emptySlots() <= storeEmptySlots.get() && hasFullSandStack()) { goToStore(); return; }

        if (nukerMode.get()) {
            nukerTick();
        } else {
            PathManagers.get().mine(Blocks.SAND, Blocks.RED_SAND);
        }
    }

    /**
     * Nuker 模式：移植 SlimefunHelper MineBot（核爆挖掘）
     * - SPHERICAL 球型范围找最近沙块（偏移表按距离升序）
     * - 瞬时破坏（一 tick 能挖完，如效率铲挖沙）时每 tick 循环发包，直到服务器确认在挖或达到发包上限
     * - 非瞬时方块只发一次 START，交给原版慢慢挖
     * - 冷却由 onBlockBreakingCooldown 清零（等价 SlimefunHelper setMiningCooldown(0)）
     * - reach 内没沙：优先 pickup 走去捡沙掉落物（边走边核爆，捡完即止不停下等），没掉落物就走最近沙块
     */
    private void nukerTick() {
        // SlimefunHelper MineBot onMineCommon 挖掘循环
        // 原则：核爆全程开启；掉落物用 Baritone pickup 收集，但不进入"等捡完"状态（V4.11 卡死根因）。
        int tryMine = 0;
        do {
            if (nukerTarget == null || !checkMineCondition(nukerTarget)) {
                nukerTarget = findNextMinePosSpherical();
            }
            if (nukerTarget == null) {
                // 核爆没沙：优先走去捡沙掉落物（边走边核爆，捡完即止不停下等），
                // 没掉落物就走最近沙块；全程不进入"等捡完"状态，避免卡死
                switch (NukerMoveLogic.decide(hasSandDropsInRadius(), findNearestSand(searchRadius.get()) != null, PathManagers.get().isPathing())) {
                    case PICKUP -> {
                        PathManagers.get().pickupItems(AutoMineSand::isSandItem);
                    }
                    case MOVE_TO_SAND -> {
                        BlockPos nearest = findNearestSand(searchRadius.get());
                        // GoalGetToBlock：沙是普通可站方块（非潜影盒那种实体方块），可直达不绕圈
                        PathManagers.get().moveTo(nearest, false);
                    }
                    case WAIT -> {
                        // 正在寻路或确实没目标，等（不掉队）
                    }
                }
                return;
            }

            // 朝向目标方块（SlimefunHelper: Direction.getFacing(shouldFacing).getOpposite()）
            Vec3d shouldFacing = nukerTarget.toCenterPos().subtract(mc.player.getEyePos());
            Direction dir = Direction.getFacing(shouldFacing).getOpposite();

            // 自动转头（可选项，默认关 = SlimefunHelper NO_BYPASS 防踢）
            if (nukerRotate.get()) {
                Vec3d targetCenter = Vec3d.ofCenter(nukerTarget);
                Vec3d eye = mc.player.getEyePos();
                double dx = targetCenter.x - eye.x;
                double dy = targetCenter.y - eye.y;
                double dz = targetCenter.z - eye.z;
                double distXZ = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
            }

            // 发包（冷却已被 onBlockBreakingCooldown 清零，等价 SlimefunHelper setMiningCooldown(0)）
            mc.interactionManager.updateBlockBreakingProgress(nukerTarget, dir);

            // 非瞬时破坏：只发一次 START，交给原版逐步挖
            if (!isInstantBreak(nukerTarget)) break;

            tryMine++;
        } while (!mc.interactionManager.isBreakingBlock() && tryMine < nukerMaxInstaMine.get());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        if (nukerMode.get()) event.cooldown = 0;
    }

    /** SlimefunHelper checkDistanceAndCondition：距离内 + 是沙（跳过 double-break 检查） */
    private boolean checkMineCondition(BlockPos pos) {
        return isWithinReach(pos) && isSand(mc.world.getBlockState(pos).getBlock());
    }

    /** SlimefunHelper findNextMinePosSpherical：球型范围（偏移表按距离升序）找最近合法沙块 */
    private BlockPos findNextMinePosSpherical() {
        int range = nukerRange.get();
        ensureNukerOffsets(range);
        BlockPos center = mc.player.getSteppingPos().add(0, 1, 0);
        int lowest = nukerMinDy.get();
        int highest = nukerMaxDy.get();
        for (int[] v : nukerOffsets) {
            int y = v[1];
            if (y < lowest || y > highest) continue;
            BlockPos p = center.add(v[0], y, v[2]);
            if (checkMineCondition(p)) return p;
        }
        return null;
    }

    /** 懒生成球型范围偏移表（按距离升序，SlimefunHelper getBlocksAround 同款） */
    private void ensureNukerOffsets(int range) {
        if (nukerOffsets != null && nukerOffsetsRange == range) return;
        nukerOffsets = NukerSphericalLogic.offsets(range);
        nukerOffsetsRange = range;
    }

    /** 是否一 tick 内能挖完（瞬时破坏，SlimefunHelper shouldTreatAsInstantBreak: delta >= 1.0） */
    private boolean isInstantBreak(BlockPos pos) {
        return mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos) >= 1.0F;
    }

    /** 检查方块是否在原版 reach 范围内（从眼睛算） */
    private boolean isWithinReach(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.BLOCK_INTERACTION_RANGE);
        return eye.squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
    }

    private boolean isSand(Block b) {
        return b == Blocks.SAND || b == Blocks.RED_SAND;
    }

    /** 掉落物是否沙子物品 */
    private static boolean isSandItem(ItemStack stack) {
        Item i = stack.getItem();
        return i == Items.SAND || i == Items.RED_SAND;
    }

    /** 搜索半径内是否有沙子掉落物 */
    private boolean hasSandDropsInRadius() {
        int r = searchRadius.get();
        Box box = mc.player.getBoundingBox().expand(r);
        return !mc.world.getEntitiesByClass(ItemEntity.class, box, e -> isSandItem(e.getStack())).isEmpty();
    }

    /** 在原版 reach(4.5格) 内找沙块 */
    private BlockPos findReachableSand() {
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.BLOCK_INTERACTION_RANGE);
        BlockPos c = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -4; x <= 4; x++)
            for (int y = -4; y <= 4; y++)
                for (int z = -4; z <= 4; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (!isSand(s.getBlock())) continue;
                    double dist = eye.squaredDistanceTo(Vec3d.ofCenter(p));
                    if (dist <= reach * reach && dist < bestDist) {
                        bestDist = dist;
                        best = p;
                    }
                }
        return best;
    }

    /** 在大范围内找最近沙块（用于 Baritone 走过去） */
    private BlockPos findNearestSand(int radius) {
        BlockPos c = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = c.add(x, y, z);
                    if (isSand(mc.world.getBlockState(p).getBlock())) {
                        double d = c.getSquaredDistance(p);
                        if (d < bestDist) { bestDist = d; best = p; }
                    }
                }
        return best;
    }

    private void goToSupply(String reason) {
        BlockPos supply = supplyBoxPos != null ? supplyBoxPos : findNamedShulker(supplyBoxName.get());
        if (supply == null) {
            error("未找到命名含\"" + supplyBoxName.get() + "\"的补给盒！");
            state = State.MINING;
            return;
        }
        info("前往补给盒: " + reason);
        PathManagers.get().stop();
        state = State.GOING_SUPPLY;
        // V4.16: 优先走到盒子旁的站立点（带 Y），Y 轴不再被忽略——
        // 旧 GoalXZ(ignoreY) 只给 xz，盒子在沙丘顶/坑里时 3D 到达判定永远不满足、开盒也够不到。
        // 找不到站立点（如盒子浮空）才退化为 GoalXZ。
        supplyGoal = findStandSpot(supply);
        supplyGoalIgnoreY = supplyGoal == null;
        PathManagers.get().moveTo(supplyGoal != null ? supplyGoal : supply, supplyGoalIgnoreY);
    }

    private void tickGoingSupply() {
        BlockPos supply = supplyBoxPos != null ? supplyBoxPos : findNamedShulker(supplyBoxName.get());
        if (supply == null) { state = State.MINING; return; }
        // V4.19: 到达判定 = 玩家中心在站立点格 1 格内（紧贴盒子旁），不再用"盒子 3 格"——
        // 3 格判定让玩家停在盒子 2-3 格处够不到开盒，陷入抬头重试循环。
        if (supplyGoal == null || mc.player.getBlockPos().getSquaredDistance(supplyGoal) > 6 * 6) {
            // 站立点失效（到达过/被破坏/离太远）时重新计算，保证目标始终带 Y
            supplyGoal = findStandSpot(supply);
            supplyGoalIgnoreY = supplyGoal == null;
        }
        boolean atStand = supplyGoal != null
            ? mc.player.squaredDistanceTo(
                supplyGoal.getX() + 0.5, supplyGoal.getY() + 0.5, supplyGoal.getZ() + 0.5) <= 1.0
            : mc.player.getBlockPos().isWithinDistance(supply, 3); // 盒子浮空无站立点，退化3格判定
        if (atStand) {
            PathManagers.get().stop();
            state = State.OPEN_SUPPLY;
            waitingShulkerOpen = true;
            shulkerWaitTimer = 0;
            storeTickCounter = 0;   // V4.14: 每次开盒重置同步计数
            openRetryCount = 0;
            openShulker(supply);
        } else if (!PathManagers.get().isPathing()) {
            // 没在寻路才重新指路（被攻击打断后自动续上）；V4.19: 精确寻路到站立点格内
            if (supplyGoal != null) {
                PathManagers.get().moveToPrecise(supplyGoal);
            } else {
                PathManagers.get().moveTo(supply, true);
            }
        }
    }

    private void tickOpenSupply() {
        // V4.14: 对齐 miku handleGettingTools——开盒超时原地重试，不回 MINING
        //（回 MINING 会因补给条件未满足立刻再触发 goToSupply→重新指路→转头，形成自转死循环）。
        // V4.19: 补给盒打不开连续 5 次（failLimit=5）直接报错停模块——
        // 旧 failLimit=1000000 意味着永远循环"转头→等→转头"，打不开就不停。
        boolean screenOpen = mc.currentScreen instanceof ShulkerBoxScreen;
        if (screenOpen) storeTickCounter++; else shulkerWaitTimer++;
        switch (StoreOpenLogic.decide(screenOpen, storeTickCounter, 3,
                waitingShulkerOpen, shulkerWaitTimer, 40, openRetryCount, 5)) {
            case SYNC_WAIT, WAIT_OPEN -> { }
            case PROCEED -> { openRetryCount = 0; doSupply(); }
            case RETRY_OPEN -> {
                openRetryCount++;
                shulkerWaitTimer = 0;
                waitingShulkerOpen = true;
                BlockPos supply = supplyBoxPos != null ? supplyBoxPos : findNamedShulker(supplyBoxName.get());
                if (supply != null) openShulker(supply);
            }
            case GIVE_UP -> {
                // V4.19: 连续 5 次打不开，报错停模块（不再无限循环重试）
                waitingShulkerOpen = false;
                closeScreen();
                error("补给盒连续 5 次打不开，模块停止（请检查补给盒位置/是否被方块盖住）");
                toggle();
            }
        }
    }

    private void doSupply() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        // 等上次点击的槽位同步完（等槽变空 = 物品移动完成）
        if (supplyStuckSlot >= 0) {
            // V4.26: 回写等待超时保护（60 tick ≈ 3 秒）——异常时不再无限等待卡死
            if (++supplyWaitTick > 60) {
                error("补给物品移动超时，模块停止");
                closeScreen();
                toggle();
                return;
            }
            int checkSlot = supplyStuckSlot;
            ItemStack s = handler.getSlot(checkSlot).getStack();
            if (supplyJob == SupplyJob.SHOVEL) {
                // === V4.29: 先放旧铲回盒(1) → 拿新铲(2) → 关盒完成（切主手交给 A=tickMining 自动切手） ===
                if (supplyPhase == 1 || supplyPhase == 2) {
                    // 等上次移动的槽变空（phase1=旧铲槽放回盒；phase2=盒槽新铲拿进背包）
                    if (!s.isEmpty()) return;
                    supplyStuckSlot = -1;
                    supplyWaitTick = 0;
                    if (supplyPhase == 1) {
                        // 旧铲已放回盒 → 拿新铲
                        supplyPhase = 2;
                        int newSlot = findShulkerItem(handler, SHOVELS, shovelMinDurability.get());
                        if (newSlot == -1) {
                            error("补给盒里没有够耐久的铲子了，模块停止");
                            closeScreen();
                            toggle();
                            return;
                        }
                        quickMove(newSlot);
                        supplyStuckSlot = newSlot;
                        supplyWaitTick = 0;
                        return;
                    }
                    // 新铲已拿进背包 → 关盒完成（回 MINING 后 A 逻辑自动把新铲切到主手）
                    supplyPhase = 0;
                    supplyJob = SupplyJob.NONE;
                    closeScreen();
                    state = State.MINING;
                    return;
                }
            }
            if (!s.isEmpty()) return;
            supplyStuckSlot = -1;
            supplyWaitTick = 0;
            if (supplyJob == SupplyJob.FOOD) {
                // 拿完一组食物 → 关盒（不重入找食物槽——盒槽已被拿空，重找会误报"没有食物"）
                supplyPhase = 0;
                supplyJob = SupplyJob.NONE;
                closeScreen();
                state = State.MINING;
                return;
            }
            // TOTEM：回写后重入下方分支，拿满 target 自动关盒
        }
        // V4.23: 一次补给流程只做一件事（tickMining 触发时锁定的 supplyJob），补完关盒回挖矿
        switch (supplyJob) {
            case SHOVEL -> {
                // V4.29: 先放旧铲回盒 → 再拿新铲 → 关盒（V4.26 切主手已删，切手由 A=tickMining 自动切手负责）
                int oldShovel = findWornOutShovelInPlayer(handler);
                if (oldShovel != -1) {
                    // 盒子没空位时旧铲放不回去 → 明确报错停（防放不进去卡住，用户要求此提醒）
                    if (!hasEmptyShulkerSlot(handler)) {
                        error("补给盒没位置了，补给铲子失败，模块停止");
                        closeScreen();
                        toggle();
                        return;
                    }
                    quickMove(oldShovel);
                    supplyStuckSlot = oldShovel;
                    supplyPhase = 1;
                    supplyWaitTick = 0;
                } else {
                    // 背包没有旧铲 → 直接拿新铲
                    supplyPhase = 2;
                    int newSlot = findShulkerItem(handler, SHOVELS, shovelMinDurability.get());
                    if (newSlot == -1) {
                        error("补给盒里没有够耐久的铲子了，模块停止");
                        closeScreen();
                        toggle();
                        return;
                    }
                    quickMove(newSlot);
                    supplyStuckSlot = newSlot;
                    supplyWaitTick = 0;
                }
            }
            case FOOD -> {
                // 锁死一组：背包所选食物为 0 触发，shift 整组 64 恰好一组，无叠加超量
                Item food = foodItem();
                int slot = findShulkerItemExact(handler, food);
                if (slot == -1) {
                    error("补给盒里没有所选食物（" + foodType.get().label + "）了，模块停止（请给补给盒补货）");
                    closeScreen();
                    toggle();
                    return;
                }
                quickMove(slot);
                supplyStuckSlot = slot;
            }
            case TOTEM -> {
                // 图腾不可堆叠，逐个拿；拿满 target 后关盒
                if (totemCount() >= totemTarget.get()) {
                    supplyPhase = 0;
                    supplyJob = SupplyJob.NONE;
                    closeScreen();
                    state = State.MINING;
                    return;
                }
                int slot = findShulkerItemExact(handler, Items.TOTEM_OF_UNDYING);
                if (slot == -1) {
                    error("补给盒里没有图腾了，模块停止（请给补给盒补货）");
                    closeScreen();
                    toggle();
                    return;
                }
                quickMove(slot);
                supplyStuckSlot = slot;
            }
            case NONE -> {
                supplyPhase = 0;
                closeScreen();
                state = State.MINING;
            }
        }
    }

    /** 在玩家背包(界面槽位27-62)找没耐久的铲子，返回界面槽位 */
    private int findWornOutShovelInPlayer(ScreenHandler handler) {
        int rows = 3;
        for (int s = rows * 9; s <= rows * 9 + 35; s++) {
            if (s >= handler.slots.size()) break;
            ItemStack stack = handler.getSlot(s).getStack();
            if (!stack.isEmpty() && SHOVELS.contains(stack.getItem())) {
                if (stack.isDamageable() && (stack.getMaxDamage() - stack.getDamage()) <= shovelMinDurability.get()) {
                    return s;
                }
            }
        }
        return -1;
    }

    /** V4.28: 补给盒（界面槽位0-26）是否有空位——放回旧铲前检查，没位置直接报错停 */
    private boolean hasEmptyShulkerSlot(ScreenHandler handler) {
        for (int i = 0; i < 27; i++) {
            if (i >= handler.slots.size()) break;
            if (handler.getSlot(i).getStack().isEmpty()) return true;
        }
        return false;
    }

    private void goToStore() {
        // V4.15: 改用"名字已同步的最近盒"选存沙盒（对齐 V4.14 提交意图），
        // 不再用 findOtherShulker 的扫描顺序首盒（可能横穿沙漠 / 误存补给盒）。
        BlockPos box = findNearestStoreBox();
        if (box == null) {
            info("没有可用的存沙潜影盒（都已满或打不开），模块停止");
            toggle();
            return;
        }
        storeBoxPos = box;
        openRetryCount = 0;
        info("前往存沙潜影盒");
        PathManagers.get().stop();
        state = State.GOING_STORE;
        // V4.16: 优先走到盒子旁的站立点（带 Y），Y 轴不再被忽略——
        // 旧 GoalXZ(ignoreY) 只给 xz，盒子在沙丘顶/坑里时 3D 到达判定永远不满足、开盒也够不到。
        // 找不到站立点（如盒子浮空）才退化为 GoalXZ。
        storeGoal = findStandSpot(box);
        storeGoalIgnoreY = storeGoal == null;
        PathManagers.get().moveTo(storeGoal != null ? storeGoal : box, storeGoalIgnoreY);
    }

    private void tickGoingStore() {
        if (storeBoxPos == null) { state = State.MINING; return; }
        // V4.19: 到达判定 = 玩家中心在站立点格 1 格内（紧贴盒子旁），不再用"盒子 3 格"——
        // 3 格判定让玩家停在盒子 2-3 格处够不到开盒，陷入抬头重试循环。
        if (storeGoal == null || mc.player.getBlockPos().getSquaredDistance(storeGoal) > 6 * 6) {
            // 站立点失效（到达过/被破坏/离太远）时重新计算，保证目标始终带 Y
            storeGoal = findStandSpot(storeBoxPos);
            storeGoalIgnoreY = storeGoal == null;
        }
        boolean atStand = storeGoal != null
            ? mc.player.squaredDistanceTo(
                storeGoal.getX() + 0.5, storeGoal.getY() + 0.5, storeGoal.getZ() + 0.5) <= 1.0
            : mc.player.getBlockPos().isWithinDistance(storeBoxPos, 3); // 盒子浮空无站立点，退化3格判定
        if (atStand) {
            PathManagers.get().stop();
            state = State.OPEN_STORE;
            waitingShulkerOpen = true;
            shulkerWaitTimer = 0;
            storeTickCounter = 0;   // V4.14: 每次开盒重置同步计数（修复第二次开盒跳过同步等待）
            openRetryCount = 0;
            openShulker(storeBoxPos);
        } else if (!PathManagers.get().isPathing()) {
            // 不在站立点且没在寻路才重新指路（被攻击打断后自动续上）；V4.19: 精确寻路到站立点格内
            if (storeGoal != null) {
                PathManagers.get().moveToPrecise(storeGoal);
            } else {
                PathManagers.get().moveTo(storeBoxPos, true);
            }
        }
    }

    private void tickOpenStore() {
        // V4.15: 对齐补给侧 tickOpenSupply——开盒超时原地重试（RETRY_OPEN）不回 MINING，
        // 修复存沙侧"超时回挖矿 → 背包仍满 → goToStore → 重新指路转头 → 再失败"的自转死循环。
        // 与补给侧区别：存沙盒可换，failLimit 用有限值（3），连续失败到顶标记该盒
        // 打不开（unopenableBoxes）→ 换下一个存沙盒，而不是无脑回挖矿。
        boolean screenOpen = mc.currentScreen instanceof ShulkerBoxScreen;
        if (screenOpen) storeTickCounter++; else shulkerWaitTimer++;
        switch (StoreOpenLogic.decide(screenOpen, storeTickCounter, 3,
                waitingShulkerOpen, shulkerWaitTimer, 40, openRetryCount, 3)) {
            case SYNC_WAIT, WAIT_OPEN -> { }
            case PROCEED -> { openRetryCount = 0; doStore(); }
            case RETRY_OPEN -> {
                openRetryCount++;
                shulkerWaitTimer = 0;
                waitingShulkerOpen = true;
                if (storeBoxPos != null) openShulker(storeBoxPos);
            }
            case GIVE_UP -> {
                // 连续开盒失败：标记该盒打不开，换下一个存沙盒
                if (storeBoxPos != null) unopenableBoxes.add(storeBoxPos.toImmutable());
                info("存沙盒打不开，换下一个");
                closeScreen();
                goToStore();
            }
        }
    }

    private void doStore() {
        ScreenHandler sh = mc.player.currentScreenHandler;
        if (sh == null) return;
        int rows = 3; // 潜影盒固定 3 行
        int playerFirst = rows * 9;   // 27
        int playerLast = rows * 9 + 35; // 62

        // 兜底卡住检测：服务器拒绝 QUICK_MOVE 时槽位回写仍有沙（同步判满漏掉的服务端真相）
        if (storeStuckSlot >= playerFirst && storeStuckSlot <= playerLast) {
            ItemStack s = sh.getSlot(storeStuckSlot).getStack();
            if (!s.isEmpty() && isSandItem(s)) {
                storeStuckTicks++;
                if (storeStuckTicks > 20) {
                    if (storeBoxPos != null) fullStoreBoxes.add(storeBoxPos.toImmutable());
                    info("存沙盒已满，寻找下一个");
                    storeStuckSlot = -1;
                    storeStuckTicks = 0;
                    closeScreen();
                    goToStore();
                }
                return;
            }
            storeStuckSlot = -1;
            storeStuckTicks = 0;
        }

        // V4.14: miku 式同步判满——搬之前先读盒槽快照，装不下立即换盒，
        // 不再依赖"点击后等 20 tick 卡住"的慢机制。
        // V4.21: 一组一组存——每 tick 只搬一组沙（上限 1），QUICK_MOVE 包节流：
        // 一次批量 27 组瞬间发出容易被服务器吞包/触发反作弊丢沙；
        // 配合上方 storeStuckSlot 回写等待（服务端回写前不搬下一组），
        // 天然每组间隔 ≥1 tick，一盒打开期间持续搬，全部搬完才关盒。
        // 只搬沙（isSandItem 过滤），杜绝 miku 把其他物品也存进去的 bug。
        int moved = 0;
        for (int s = playerFirst; s <= playerLast && moved < 1; s++) {
            ItemStack stack = sh.getSlot(s).getStack();
            if (stack.isEmpty() || !isSandItem(stack)) continue;
            if (stack.getCount() < 64) continue; // V4.23: 不满一组不存——零头沙留在背包，挖沙时自动合并攒满一组再存
            if (StoreSlotLogic.findSandTargetSlot(boxCounts(sh), boxIsSand(sh), 64, stack.getCount()) == -1) {
                // 盒子对沙已满：立即换盒（旧逻辑要白等 20 tick）
                if (storeBoxPos != null) fullStoreBoxes.add(storeBoxPos.toImmutable());
                info("存沙盒已满，寻找下一个");
                storeStuckSlot = -1;
                storeStuckTicks = 0;
                closeScreen();
                goToStore();
                return;
            }
            quickMove(s);
            storeStuckSlot = s;
            moved++;
        }
        if (moved == 0) {
            // 没沙了，存完（moved>0 但到上限时不关盒，下一 tick 继续搬）
            storeStuckSlot = -1;
            storeStuckTicks = 0;
            closeScreen();
            state = State.MINING;
            info("存沙完成");
        }
    }

    /** 盒槽快照：前 27 槽的数量 */
    private int[] boxCounts(ScreenHandler sh) {
        int[] c = new int[27];
        for (int i = 0; i < 27 && i < sh.slots.size(); i++) c[i] = sh.getSlot(i).getStack().getCount();
        return c;
    }

    /** 盒槽快照：前 27 槽是否沙 */
    private boolean[] boxIsSand(ScreenHandler sh) {
        boolean[] b = new boolean[27];
        for (int i = 0; i < 27 && i < sh.slots.size(); i++) b[i] = isSandItem(sh.getSlot(i).getStack());
        return b;
    }

    private boolean needNewShovel() {
        FindItemResult r = InvUtils.find(itemStack -> SHOVELS.contains(itemStack.getItem()));
        if (!r.found()) return true;
        ItemStack s = mc.player.getInventory().getStack(r.slot());
        if (!s.isDamageable()) return false;
        return (s.getMaxDamage() - s.getDamage()) <= shovelMinDurability.get();
    }

    /** V4.23: 所选补给食物种类对应的 Item（FoodType.itemId → 注册表） */
    private Item foodItem() {
        return Registries.ITEM.get(Identifier.tryParse(foodType.get().itemId));
    }

    /** V4.23: 背包所选食物的总量 */
    private int foodCount(Item item) {
        int c = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) c += s.getCount();
        }
        return c;
    }

    /** V4.23: 盒内是否含钻石/合金铲（补给盒内容指纹，SupplyFingerprintLogic 定义硬性标准） */
    private boolean containsFingerprintShovel(ScreenHandler sh) {
        for (int i = 0; i < 27 && i < sh.slots.size(); i++) {
            ItemStack s = sh.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            String id = Registries.ITEM.getId(s.getItem()).toString();
            if (SupplyFingerprintLogic.FINGERPRINT_SHOVEL_IDS.contains(id)) return true;
        }
        return false;
    }

    /** V4.23: 内容指纹候选里选三维最近者（名字丢失时兜底识别补给盒） */
    private BlockPos findNearestSupplyLikeBox() {
        BlockPos c = mc.player.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : supplyLikeBoxes) {
            if (unopenableBoxes.contains(p)) continue;
            if (!(mc.world.getBlockState(p).getBlock() instanceof ShulkerBoxBlock)) continue;
            double d = c.getSquaredDistance(p);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private int totemCount() {
        int c = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.TOTEM_OF_UNDYING) c += s.getCount();
        }
        return c;
    }

    private int emptySlots() {
        int c = 0;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) c++;
        }
        return c;
    }

    /** V4.23: 背包是否存在整组沙（count ≥ 64）。存沙触发条件之一——只有零头沙不开盒（防死循环） */
    private boolean hasFullSandStack() {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && isSandItem(s) && s.getCount() >= 64) return true;
        }
        return false;
    }

    private BlockPos findNamedShulker(String nameContains) {
        BlockPos c = mc.player.getBlockPos();
        int r = searchRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (s.getBlock() instanceof ShulkerBoxBlock &&
                        mc.world.getBlockEntity(p) instanceof ShulkerBoxBlockEntity be) {
                        var n = be.getCustomName();
                        if (n != null && n.getString().contains(nameContains)) return p;
                    }
                }
        return null;
    }

    /**
     * 从"名字已同步且非补给、未满、可打开"的候选中选三维最近的存沙盒（V4.15 接线）。
     * 候选只取 nameSyncedBoxes（INIT_SCAN 成功打开过 = 名字已同步），
     * 排除满盒(fullStoreBoxes)、打不开的盒(unopenableBoxes)、补给盒(名字含补给关键词)。
     * 最近选择委托 NearestBoxLogic.select（纯逻辑，已有单测）。
     */
    private BlockPos findNearestStoreBox() {
        BlockPos c = mc.player.getBlockPos();
        java.util.List<int[]> candidates = new java.util.ArrayList<>();
        java.util.List<BlockPos> boxes = new java.util.ArrayList<>();
        for (BlockPos p : nameSyncedBoxes) {
            if (fullStoreBoxes.contains(p) || unopenableBoxes.contains(p)) continue;
            // V4.23: 内容指纹识别的补给盒（名字被服务器清掉）也要排除——否则沙会存进补给盒
            if (supplyBoxPos != null && p.equals(supplyBoxPos)) continue;
            BlockState s = mc.world.getBlockState(p);
            if (!(s.getBlock() instanceof ShulkerBoxBlock)) continue;
            boolean supply = false;
            if (mc.world.getBlockEntity(p) instanceof ShulkerBoxBlockEntity be) {
                var n = be.getCustomName();
                if (n != null && n.getString().contains(supplyBoxName.get())) supply = true;
            }
            if (supply) continue;
            candidates.add(new int[]{p.getX(), p.getY(), p.getZ()});
            boxes.add(p);
        }
        int idx = NearestBoxLogic.select(c.getX(), c.getY(), c.getZ(), candidates, Set.of());
        return idx == -1 ? null : boxes.get(idx);
    }

    private int findShulkerItem(ScreenHandler handler, List<Item> items, int minDurability) {
        for (int i = 0; i < 27; i++) {
            if (i >= handler.slots.size()) break;
            ItemStack s = handler.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            if (items.contains(s.getItem())) {
                if (!s.isDamageable() || (s.getMaxDamage() - s.getDamage()) > minDurability) return i;
            }
        }
        return -1;
    }

    private int findShulkerItemExact(ScreenHandler handler, Item item) {
        for (int i = 0; i < 27; i++) {
            if (i >= handler.slots.size()) break;
            ItemStack s = handler.getSlot(i).getStack();
            if (!s.isEmpty() && s.getItem() == item) return i;
        }
        return -1;
    }

    private void quickMove(int slot) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId, slot, 0,
            SlotActionType.QUICK_MOVE, mc.player);
    }

    private void openShulker(BlockPos pos) {
        if (!mc.player.getBlockPos().isWithinDistance(pos, 5)) return;
        // V4.16: 六面检测——按玩家眼睛相对盒子的位置选玩家正对的那个面交互
        // （不再固定 UP：盒子顶面被其他方块盖住、或玩家站在盒子侧面/下方时，
        // 固定 UP 的交互会被服务器按视线回溯拒绝，盒子永远打不开）。
        Vec3d eye = mc.player.getEyePos();
        Direction face = faceById(FacingLogic.bestFaceIndex(
            eye.x, eye.y, eye.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        // 统一转头模式（Ying/SlimefunHelper）：存视角→转头+发包→交互→恢复视角
        InteractionUtils.interactBlockSafely(pos, face);
    }

    private void closeScreen() {
        if (mc.currentScreen instanceof HandledScreen) mc.player.closeHandledScreen();
    }

    /** 面 ID（FacingLogic.bestFaceIndex 返回值，与 Direction.ID 顺序一致）→ Direction */
    private static Direction faceById(int id) {
        return switch (id) {
            case 0 -> Direction.DOWN;
            case 1 -> Direction.UP;
            case 2 -> Direction.NORTH;
            case 3 -> Direction.SOUTH;
            case 4 -> Direction.WEST;
            default -> Direction.EAST;
        };
    }

    /**
     * 计算潜影盒旁的交互站立点（V4.19）：优先盒子紧邻格（水平 ±1，同层/上一层）——
     * 玩家与盒子之间一格空隙都不留，站到盒子旁边同一层，六面检测自然选玩家正对的
     * 侧面开盒；紧邻格全被占/悬空才放宽到水平 ±2（仍排除盒子正下方）。
     * 过滤"空气格 + 下方实心"（脚能站住），选三维距离玩家最近的格作为寻路目标。
     * 找不到（如盒子浮空、四周无可站立格）返回 null，调用方退化为 GoalXZ。
     */
    private BlockPos findStandSpot(BlockPos box) {
        BlockPos best = pickStandSpot(box, StandSpotLogic.adjacentCandidates(box.getX(), box.getY(), box.getZ()));
        if (best != null) return best;
        return pickStandSpot(box, StandSpotLogic.withoutBelow(
            box.getY(),
            StandSpotLogic.candidates(box.getX(), box.getY(), box.getZ(), 2)));
    }

    /** 从候选里选"空气格 + 下方实心 + 距玩家最近"的站立点（世界条件过滤） */
    private BlockPos pickStandSpot(BlockPos box, java.util.List<int[]> cands) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] c : cands) {
            m.set(c[0], c[1], c[2]);
            if (!mc.world.getBlockState(m).isReplaceable()) continue;
            if (!mc.world.getBlockState(m.down()).isSolidBlock(mc.world, m.down())) continue;
            double d = mc.player.squaredDistanceTo(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5);
            if (d < bestD) {
                bestD = d;
                best = m.toImmutable();
            }
        }
        return best;
    }
}
