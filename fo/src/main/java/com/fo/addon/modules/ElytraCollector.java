package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.Debug;
import com.fo.addon.utils.DirectionFilter;
import com.fo.addon.utils.FacingLogic;
import com.fo.addon.utils.FlightFireworkLogic;
import com.fo.addon.utils.ReturnPathLogic;
import com.fo.addon.utils.SearchExpansionLogic;
import com.fo.addon.utils.ShipFilterLogic;
import com.fo.addon.utils.TrashDefaults;
import com.seedfinding.mcbiome.source.EndBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.EndCity;
import com.seedfinding.mcfeature.structure.generator.Generator;
import com.seedfinding.mcfeature.structure.generator.structure.EndCityGenerator;
import com.seedfinding.mcnoise.simplex.SimplexNoiseSampler;
import com.seedfinding.mcseed.lcg.LCG;
import com.seedfinding.mcseed.rand.JRand;
import com.seedfinding.mcterrain.terrain.EndTerrainGenerator;
import com.fo.addon.pathing.PathManagers;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.noise.InterpolatedNoiseSampler;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.minecraft.screen.slot.SlotActionType.PICKUP;

/**
 * ElytraAutoCollector — 全自动鞘翅采集。
 */
public class ElytraCollector extends Module {

    private static final MCVersion VERSION = MCVersion.v1_21;

    // 每次搜索按世界种子重建的原版末地岛屿噪声 (volatile: 后台搜索线程写、tick 线程 debug 读)
    private volatile SimplexNoiseSampler islandNoise;
    // 末地 3D 噪声 (InterpolatedNoiseSampler, 种子=世界种子，与服务器端一致)
    private volatile InterpolatedNoiseSampler base3dNoise;

    private static SimplexNoiseSampler createIslandNoise(long worldSeed) {
        // 原版 LegacyNoiseDensityFunctionVisitor: new EndIslands(worldSeed) → CheckedRandom(seed) 跳过 17292 次
        JRand rand = new JRand(worldSeed);
        rand.advance(LCG.JAVA.combine(17292L));
        return new SimplexNoiseSampler(rand);
    }

    // ========== Settings ==========
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFlight = settings.createGroup("飞行");

    private final Setting<String> seedSetting = sgGeneral.add(new StringSetting.Builder()
        .name("世界种子")
        .description("世界种子 (0 = 当前世界).")
        .defaultValue("-7346913998703726680")
        .build()
    );

    private final Setting<Integer> searchRange = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径")
        .description("搜索半径，单位方块 (从玩家位置).")
        .defaultValue(5000)
        .range(320, 100000)
        .sliderRange(320, 20000)
        .build()
    );

    private final Setting<Integer> minHeight = sgFlight.add(new IntSetting.Builder()
        .name("最低高度")
        .description("飞行中低于此高度时触发爬升.")
        .defaultValue(180)
        .range(100, 300)
        .sliderRange(120, 260)
        .build()
    );

    private final Setting<Integer> maxHeight = sgFlight.add(new IntSetting.Builder()
        .name("最高高度")
        .description("飞行中高于此高度时停止爬升、转为平缓下滑 (与 min-height 组成滞回区间).")
        .defaultValue(220)
        .range(120, 320)
        .sliderRange(140, 300)
        .build()
    );

    private final Setting<Integer> riseHeight = sgFlight.add(new IntSetting.Builder()
        .name("爬升高度")
        .description("起飞后抬头爬升到的目标高度 (需高于 max-height).")
        .defaultValue(230)
        .range(200, 320)
        .sliderRange(200, 300)
        .build()
    );

    private final Setting<Double> pitchSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("俯仰速度")
        .description("飞行 (pitch40) 时上下转动视角 (pitch) 的速度 (度/tick).")
        .defaultValue(10.0)
        .min(1)
        .sliderMax(45)
        .build()
    );

    private final Setting<Double> yawSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("偏航速度")
        .description("左右转动视角 (yaw) 及鞘翅缓降的转向速度 (度/tick).")
        .defaultValue(30.0)
        .min(1)
        .sliderMax(90)
        .build()
    );

    private final Setting<Double> killAuraReach = sgFlight.add(new DoubleSetting.Builder()
        .name("交互判定距离")
        .description("攻击展示框 (item_frame) 的判定距离.")
        .defaultValue(3.0)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> fireworkInterval = sgFlight.add(new DoubleSetting.Builder()
        .name("烟花间隔")
        .description("展开鞘翅后立即使用第一个烟花，之后每隔这么多秒再用一个 (秒).")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 10)
        .build()
    );

    private final Setting<Double> takeoffPitch = sgFlight.add(new DoubleSetting.Builder()
        .name("起飞拉升角度")
        .description("首次起飞以及滑翔降到最低高度后的重新爬升角度（度）。达到最高高度后自动切换滑翔。")
        .defaultValue(45.0)
        .range(5.0, 80.0)
        .sliderRange(5.0, 80.0)
        .build()
    );

    private final Setting<Double> cruiseClimbPitch = sgFlight.add(new DoubleSetting.Builder()
        .name("巡航爬升角度")
        .description("长距离巡航到最低高度后使用的抬头角度（度）。")
        .defaultValue(54.77)
        .range(5.0, 80.0)
        .sliderRange(5.0, 80.0)
        .build()
    );

    private final Setting<Double> cruiseGlidePitch = sgFlight.add(new DoubleSetting.Builder()
        .name("巡航滑翔角度")
        .description("达到最高高度后使用的向下滑翔角度（度）。滑翔阶段不使用烟花，降到最低高度后重新爬升。")
        .defaultValue(37.72)
        .range(0.0, 70.0)
        .sliderRange(0.0, 70.0)
        .build()
    );

    private final Setting<Boolean> ignoreVisited = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略已访问记录")
        .description("开启后重新扫描所有预测末地船，不使用旧的“已访问船”列表。适合旧版本记录异常时临时恢复搜索。")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> blacklist = sgGeneral.add(new StringListSetting.Builder()
        .name("黑名单")
        .description("黑名单：已经去过的船 (x,z)，搜索时会自动跳过. 自动维护.")
        .defaultValue(new ArrayList<>())
        .visible(() -> false) // 黑名单不输出到 UI，只静默记日志
        .build()
    );

    private final Setting<List<String>> searchResults = sgGeneral.add(new StringListSetting.Builder()
        .name("搜索结果")
        .description("上次搜索结果列表 (x,y,z,朝向)，下一次搜索完成后覆盖.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    // 防止 stopTask 内部置按钮时递归触发 onChanged (必须定义在 btnStart 之前)
    private boolean btnSelfSet = false;

    private final Setting<Boolean> btnStart = sgGeneral.add(new BoolSetting.Builder()
        .name("开始采集")
        .description("开始自动采集.")
        .defaultValue(false)
        .onChanged(b -> {
            if (btnSelfSet) return;
            if (b) {
                start();
            } else {
                // 关闭按钮 = 立即停止任务 (修复: 之前只处理开启分支, 关闭后状态机仍在运行)
                stopTask("手动停止.");
            }
        })
        .build()
    );

    /** 内部置按钮为 false 时使用，防止 onChanged 递归 */
    private void btnOff() {
        btnSelfSet = true;
        btnStart.set(false);
        btnSelfSet = false;
    }

    private final Setting<Boolean> debugSetting = sgGeneral.add(new BoolSetting.Builder()
        .name("调试日志")
        .description("输出调试日志 (用于校准高度公式).")
        .defaultValue(false)
        .build()
    );

    // ========== Storage Settings ==========
    private final SettingGroup sgStorage = settings.createGroup("存储");

    private final Setting<Integer> freeSlotDump = sgStorage.add(new IntSetting.Builder()
        .name("背包空格阈值")
        .description("背包可用空格 ≤ 此值时，拿到鞘翅后自动去末影箱存鞘翅.")
        .defaultValue(3)
        .range(0, 36)
        .sliderRange(0, 36)
        .build()
    );

    private final Setting<List<String>> supplies = sgStorage.add(new StringListSetting.Builder()
        .name("物资列表")
        .description("物资列表，格式: 物品ID;最低值;目标库存 (如 minecraft:firework_rocket;32;256). 背包物资低于最低值时自动从末影箱补货，拿到目标库存为止. 留空 = 不补货.")
        .defaultValue(new ArrayList<>(List.of("minecraft:firework_rocket;4;16", "minecraft:golden_carrot;8;32", "minecraft:totem_of_undying;3;3")))
        .build()
    );

    private final Setting<Boolean> autoTakeBox = sgStorage.add(new BoolSetting.Builder()
        .name("自动拿空盒")
        .description("当需要放置鞘翅到潜影盒但背包没有空盒子时，自动从末影箱获取.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lowYExit = sgStorage.add(new BoolSetting.Builder()
        .name("低Y退出")
        .description("Y 低于阈值时自动退出游戏 (防虚空掉物).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> lowYThreshold = sgStorage.add(new IntSetting.Builder()
        .name("低Y阈值")
        .description("低于此 Y 自动退出游戏.")
        .defaultValue(-20)
        .range(-64, 100)
        .sliderRange(-64, 64)
        .build()
    );

    // ========== 搜索方向过滤 ==========
    private final SettingGroup sgDirection = settings.createGroup("搜索方向");

    private final Setting<Boolean> searchNorth = sgDirection.add(new BoolSetting.Builder()
        .name("搜索北方")
        .description("允许搜索以开始搜索时的位置为中心，北方（-Z）方向扇区内的末地船.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchSouth = sgDirection.add(new BoolSetting.Builder()
        .name("搜索南方")
        .description("允许搜索以开始搜索时的位置为中心，南方（+Z）方向扇区内的末地船.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchEast = sgDirection.add(new BoolSetting.Builder()
        .name("搜索东方")
        .description("允许搜索以开始搜索时的位置为中心，东方（+X）方向扇区内的末地船.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchWest = sgDirection.add(new BoolSetting.Builder()
        .name("搜索西方")
        .description("允许搜索以开始搜索时的位置为中心，西方（-X）方向扇区内的末地船.")
        .defaultValue(true)
        .build()
    );

    // ========== State Machine ==========
    private enum State { IDLE, SEARCHING, RISING, FLYING, LANDING, COLLECTING, DONE }
    private enum CollectStep { TO_P1, TO_P2, WAIT_SHULKER, TO_P3, HIT_FRAME, PICK_UP, EQUIP, EXIT_P1, EXIT_LANDING, LEAVE_SHIP }
    private enum StoragePhase { NONE, PLACE_EC, OPEN_EC, PICK_BOX, CLOSE_SCREEN, PLACE_BOX, OPEN_BOX, FILL_BOX, TAKE_SUPPLIES, MINE_BOX, MINE_EC }

    private State state = State.IDLE;
    private CollectStep collectStep = CollectStep.TO_P1;

    // ========== Runtime Data ==========
    private final List<ShipTarget> ships = Collections.synchronizedList(new ArrayList<>());
    private int targetIndex = 0;
    private volatile Thread searchThread;
    private volatile boolean searchCancelled = true;
    private volatile int searchGeneration = 0;

    private ShipTarget current;
    private ShipWaypoints waypoints;
    private int stateTick = 0;
    private int lastGotoTick = 0;
    private long worldSeed = 0L;
    private boolean climbing = false;
    private boolean headFound = false;
    private BlockPos exactHead = null;
    private int nearTicks = 0;
    private int scanAttempts = 0;
    private boolean pullUpSuppressed = false; // PullUp 独立模块强制拉升期间暂停采集器全部控制 (危险高度保护已移至 PullUp 模块)
    private final java.util.Set<String> sessionSkip = Collections.synchronizedSet(new java.util.HashSet<>());
    private int searchExtends = 0;
    private Direction takeoffFacing = null;
    private int noElytraTicks = 0;
    private boolean frameHadElytra = false; // 到达展示框时框里是否有鞘翅 (打掉后框会变空，不能据此判定没鞘翅)
    private boolean frameAirChecked = false; // 龙头确认后是否已在空中检查过展示框内容
    private boolean frameAirEmpty = false;   // 空中检查结果：展示框里没有鞘翅
    private boolean relaunchAfterReturn = false; // LEAVE_SHIP 由 relaunchAfterShip 触发（采集完成回起飞点）时为 true；超时停止，绝不原地起飞
    private int frameAirScanTicks = 0;       // 空中找展示框实体的累计扫描 tick
    private boolean pickupSessionDone = false; // 每艘船捡到鞘翅后只触发一次船旁存储会话 (防缺口未补齐时原地无限重启)
    private boolean firstFireworkDone = false;
    private int lastFireworkTick = 0;
    private boolean linkedTrash = false;     // 本次激活是否由我们联动开启了自动扔垃圾 (关闭时智能回滚)

    // ========== 降落恢复爬升 ==========
    private boolean landingRecover = false;
    private double recoveryTargetY = 0;
    private float recoveryYaw = 0f;          // 背对降落点的方向
    private int recoverStageTick = 0;        // 当前恢复阶段已持续 tick (超时保护，杜绝一直升天)
    private int recoverCount = 0;            // 本船恢复触发次数 (超过 3 次仍落不了地就停止)

    // ========== 存储会话 (存鞘翅/补货) ==========
    private StoragePhase storagePhase = StoragePhase.NONE;
    private int storageTick = 0;
    private int pickupWaitStart = -1;        // 挖掉后等待拾取的起始 tick
    private BlockPos ecPos;
    private BlockPos boxPos;
    private boolean boxFromInventory = false;
    private int boxInventorySlot = -1;   // 玩家主栏槽位 0..35
    private int boxBaseline = 0;         // 放置潜影盒前背包中的潜影盒数量 (挖掉拾取后应恢复)
    private int ecBaseline = 0;          // 放置末影箱前的末影箱数量
    private boolean dumpNeeded = false;
    private boolean resupplyNeeded = false;
    private StoragePhase afterClose = StoragePhase.NONE;
    private int elytraBeforeFrame = 0;   // 攻击展示框前的鞘翅数量
    private List<BlockPos> placeCandidates = new ArrayList<>(); // 放置候选位置 (按距离排序)
    private int placeIndex = 0;          // 当前候选下标
    private int lastPlaceClick = -100;   // 上次点击放置的 tick (每 8 tick 才点一次)
    private int placeClickAttempts = 0;  // 当前候选位置点击次数 (每个位置最多点 2 次)

    // ===== 点击握手: 客户端点击只发包，容器界面状态要等服务器回包 (1~5 tick) 才更新 =====
    // 用队列 + 光标状态轮询串行化所有搬运点击，防止用过期界面状态连续点击
    private record MoveOp(int source, int target) {}  // target=-1 拾取后停留在光标; source=-1 直接把光标放下到 target
    private final ArrayDeque<MoveOp> moveQueue = new ArrayDeque<>();
    private int moveState = 0;           // 0 空闲 1 等光标非空(拾取后放下) 2 等光标空(放下完成) 3 等光标非空(拾取保留)
    private int moveSrc = -1;
    private int moveDst = -1;
    private int pickStep = 0;            // 取盒流程: 0 空闲 1 光标持有盒子待判定 2 已放下成功 3 放回中(失败)
    private int pickEcSlot = -1;         // 取盒来源的末影箱槽位
    private int pickInvSlot = -1;        // 取盒成功时的玩家槽位 (0..35)
    private int fillIndex = -1;          // 填盒循环当前扫描起点 (-1 未开始)
    private int takeRule = 0;            // 补货当前规则下标
    private int takeSlot = -1;           // 补货当前容器槽起点
    private int takeNeed = 0;            // 补货当前规则还需数量
    private int takeStuckSlot = -1;      // 补货最近一次 shift 点击的槽 (连续不动 = 背包满被忽略)
    private int takeStuckTicks = 0;      // 该槽连续不动 tick
    private int takeEmptyTicks = 0;      // 盒内无物资连续确认 tick (防屏幕同步延迟误判收尾)
    private final Set<Item> takeFailedItems = new HashSet<>(); // 背包满且无杂物可腾导致拿不到的物品 (整个补货会话有效，防死循环重试)
    private int fillStuckSlot = -1;      // 存鞘翅最近一次 shift 点击的槽 (连续不动 = 盒子满)
    private int fillStuckTicks = 0;      // 该槽连续不动 tick
    private int recoveryAttempts = 0;    // 容器界面异常连续恢复次数 (最多 3 次，之后停任务) — StorageRecovery
    private int openRetry = 0;           // 当前打开容器已尝试次数 (最多 5 次，间隔 1 秒)
    private int boxOpenClickTick = -1;   // 最近一次 vanilla 右键 (Utils.rightClick) 的 tick，用于判断点完是否已打开
    private boolean boxPurposeSupply = false; // 当前潜影盒用途: true=补货 false=存鞘翅
    private boolean boxPlaceFloorFallback = false; // 潜影盒放置: 挂放 (空气+上方实心) 失败后改用地板放置 (空气+下方实心, 顶面敞开)
    private int recoverStage = 0;        // 降落恢复阶段: 0=反向拉升到降落点上方25格 1=面向降落点急速下降
    private int ecOpenFailCount = 0;     // 末影箱连续打开失败轮数 (>0 时跳过扫描已有末影箱，直接放新的)
    private int supplyScanStart = 0;     // 补货扫描游标: 末影箱内下一个要检查的盒子槽位 (取出盒子后推进，遍历完没凑齐就物资不足)
    private boolean supplyPauseDump = false; // 补货时背包容量低 (≤ dump-free-slots)：暂停补给，先回收补给盒再存鞘翅腾空间
    private boolean resupplyRescan = false;  // 存鞘翅腾空间后，重新从头扫描末影箱找补给盒 (补给盒已在游标之前)
    private boolean standingMovedOff = false; // 打开容器失败时是否已触发"站在容器上走下来"流程

    // ========== Constructor ==========
    public ElytraCollector() {
        super(AddonTemplate.CATEGORY, "FO 鞘翅采集",
            "全自动找末地城鞘翅：种子定位 + 龙头精确定位 + 高度保持飞行 + 缓降 + Baritone 寻路 + 打展示框捡鞘翅.");
    }

    // 黑名单不输出到 UI (彗星界面)，只静默记日志；"清除黑名单"由勾选框改为按钮
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WSection section = theme.section("黑名单", true);
        WButton clear = section.add(theme.button("清除黑名单")).expandX().widget();
        clear.action = () -> {
            synchronized (blacklist.get()) {
                blacklist.get().clear();
            }
            info("黑名单已清空.");
        };
        return section;
    }

    @Override
    public void onActivate() {
        // 联动：自动打开「FO 自动扔垃圾」(白名单 + 61 项默认列表)，并标记本次联动开启
        AutoTrash trash = Modules.get().get(AutoTrash.class);
        if (trash != null && !trash.isActive()) {
            trash.enableForElytraLink();
            linkedTrash = true;
            info("已联动开启 FO 自动扔垃圾 (白名单 " + TrashDefaults.DEFAULT_WHITELIST_IDS.size() + " 项).");
        }

        // 按钮保存值为 true 时，激活模块即自动开始 (用户无需再手动点一次按钮)
        if (btnStart.get() && state == State.IDLE && mc.player != null && mc.world != null) {
            start();
        }
    }

    @Override
    public void onDeactivate() {
        // 联动回滚：仅关闭由本次联动开启的自动扔垃圾（手动开的不会被关）
        if (linkedTrash) {
            AutoTrash trash = Modules.get().get(AutoTrash.class);
            if (trash != null) trash.disableForElytraLink();
            linkedTrash = false;
        }
        searchCancelled = true;
        searchGeneration++;
        searchThread = null;
        state = State.IDLE;
        ships.clear();
        pullUpSuppressed = false;
        landingRecover = false;
        recoveryYaw = 0f;
        recoverStageTick = 0;
        storagePhase = StoragePhase.NONE;
        storageTick = 0;
        boxInventorySlot = -1;
        resetStorageClick();
        releaseForward();
        PathManagers.get().stop();
    }

    private void resetStorageClick() {
        moveQueue.clear();
        moveState = 0;
        moveSrc = -1;
        moveDst = -1;
        pickStep = 0;
        pickEcSlot = -1;
        pickInvSlot = -1;
        fillIndex = -1;
        takeRule = 0;
        takeSlot = -1;
        takeNeed = 0;
        takeStuckSlot = -1;
        takeStuckTicks = 0;
        takeEmptyTicks = 0;
        takeFailedItems.clear();
        fillStuckSlot = -1;
        fillStuckTicks = 0;
        openRetry = 0;
        boxPurposeSupply = false;
        ecOpenFailCount = 0;
        supplyScanStart = 0;
        standingMovedOff = false;
    }

    // ========== Start ==========
    private void start() {
        if (mc.player == null || mc.world == null) {
            // 配置加载时可能误触发 (按钮保存值为 true)，此时游戏未进存档，直接忽略
            btnOff();
            return;
        }
        if (state != State.IDLE && state != State.DONE) {
            btnOff();
            return;
        }
        state = State.SEARCHING;
        searchExtends = 0;
        takeoffFacing = null;
        landingRecover = false;
        recoveryYaw = 0f;
        recoverStageTick = 0;
        storagePhase = StoragePhase.NONE;
        storageTick = 0;
        boxInventorySlot = -1;
        info("开始搜索末地城 (范围=" + searchRange.get() + " 方块)...");
        if (parseSupplies().isEmpty()) {
            info("物资列表为空，自动补货已跳过 (如需补货请在存储分组中配置).");
        }
        if (debugSetting.get()) {
            info("调试日志文件: " + debugFilePath().toAbsolutePath());
        }
        launchSearch();
    }

    // 独立搜索线程；generation 递增使旧任务的结果失效，cancelled 使循环可被真正打断
    private void launchSearch() {
        searchCancelled = false;
        int gen = ++searchGeneration;
        Thread t = new Thread(() -> searchShips(gen), "ElytraCollector-Search");
        t.setDaemon(true);
        searchThread = t;
        t.start();
    }

    // ========== Search ==========
    private void searchShips(int generation) {
        try {
            if (searchCancelled || generation != searchGeneration) return;
            worldSeed = parseSeed(seedSetting.get());
            EndCity endCity = new EndCity(VERSION);
            EndCityGenerator generator = new EndCityGenerator(VERSION);
            ChunkRand rand = new ChunkRand();
            EndBiomeSource biomeSource = new EndBiomeSource(VERSION, worldSeed);
            // 按世界种子重建原版末地岛屿噪声 (用于生物群系判定 + 高度链)
            islandNoise = createIslandNoise(worldSeed);
            // 末地 3D 噪声 (与服务器端一致: 世界种子)
            base3dNoise = new InterpolatedNoiseSampler(new CheckedRandom(worldSeed), 0.25, 0.25, 80.0, 160.0, 4.0);
            // 高度过滤：与 miku ElytraFinder 完全一致 — seedfinding 高度链 (getAverageYPosition ≥ 60)
            // 不再用自写噪声链 (阈值低 1 格会报假船)，generate 内部也用真实地形，双保险不报假城
            EndTerrainGenerator terrainGen = new EndTerrainGenerator(biomeSource);

            int px = (int) Math.floor(mc.player.getX());
            int pz = (int) Math.floor(mc.player.getZ());

            int spacing = endCity.getSpacing();
            int spacingBlocks = spacing * 16;
            int baseRegionRange = SearchExpansionLogic.ringCount(searchRange.get(), spacingBlocks);
            int maxRegionRange = SearchExpansionLogic.maxRing(spacingBlocks, baseRegionRange);
            int prx = Math.floorDiv(px >> 4, spacing);
            int prz = Math.floorDiv(pz >> 4, spacing);

            List<ShipTarget> found = new ArrayList<>();
            int[] stats = new int[4]; // [0]=有船 [1]=可构造 [2]=排除(黑名单/已访问/会话) [3]=单点异常

            // 初始方形扫描
            for (int drx = -baseRegionRange; drx <= baseRegionRange; drx++) {
                if (searchCancelled || generation != searchGeneration) return;
                for (int drz = -baseRegionRange; drz <= baseRegionRange; drz++) {
                    ShipTarget t = scanShipCell(endCity, generator, rand, biomeSource, terrainGen, worldSeed, prx, prz, drx, drz, stats);
                    if (t != null) found.add(t);
                }
            }

            if (searchCancelled || generation != searchGeneration) return;

            // 空结果 → 环形外扩 (对齐 Ying ElytraSearchFallback: square → ring 只扫新环带，不重扫旧区，上限 50000 格)
            int expandedRange = searchRange.get();
            if (found.isEmpty()) {
                for (int ring = baseRegionRange + 1; ring <= maxRegionRange; ring++) {
                    if (searchCancelled || generation != searchGeneration) return;
                    int before = found.size();
                    for (int drx = -ring; drx <= ring; drx++) {
                        for (int drz = -ring; drz <= ring; drz++) {
                            if (!SearchExpansionLogic.isRingBoundary(drx, drz, ring)) continue;
                            ShipTarget t = scanShipCell(endCity, generator, rand, biomeSource, terrainGen, worldSeed, prx, prz, drx, drz, stats);
                            if (t != null) found.add(t);
                        }
                    }
                    expandedRange = ring * spacingBlocks;
                    if (found.size() > before) break;
                }
            }

            if (searchCancelled || generation != searchGeneration) return;

            // 日志对齐 Ying 搜船回退口径
            info("搜船回退: 基准=(" + px + "," + pz + "), 初始范围=" + searchRange.get() + ", 实际扩展范围=" + expandedRange
                + ", 有船=" + stats[0] + ", 可构造=" + stats[1] + ", 排除=" + stats[2] + ", 恢复候选=" + found.size()
                + ", 单点异常=" + stats[3] + ".");

            // 方向过滤：按玩家所在位置的方位过滤 (北/南/东/西 独立开关，可只搜单方向)
            {
                int before = found.size();
                boolean n = searchNorth.get(), s = searchSouth.get(), e = searchEast.get(), w = searchWest.get();
                found.removeIf(t -> !com.fo.addon.utils.DirectionFilter.shouldKeep(px, pz, t.headPos.getX(), t.headPos.getZ(), n, s, e, w));
                if (before != found.size()) {
                    info("方向过滤: 候船 " + before + " → 保留 " + found.size() + " (北=" + n + " 南=" + s + " 东=" + e + " 西=" + w + ").");
                }
            }

            found.sort(Comparator.comparingInt(s ->
                (s.headPos.getX() - px) * (s.headPos.getX() - px) + (s.headPos.getZ() - pz) * (s.headPos.getZ() - pz)
            ));

            ships.clear();
            ships.addAll(found);
            targetIndex = 0;

            // 保存搜索结果 (下一次搜索会覆盖)
            synchronized (searchResults.get()) {
                searchResults.get().clear();
                for (ShipTarget t : found) {
                    searchResults.get().add(t.headPos.getX() + "," + t.headPos.getY() + "," + t.headPos.getZ() + "," + t.facing);
                }
            }

            info("找到 " + ships.size() + " 艘带船末地城 (已排除黑名单, 范围=" + expandedRange + ").");
            if (ships.isEmpty()) {
                searchExtends = 0;
                completeTask("范围内没有末地船，任务完成.");
            } else {
                searchExtends = 0;
                if (state == State.SEARCHING) {
                    firstFireworkDone = false;
                    state = State.RISING;
                    stateTick = 0;
                }
            }
        } catch (Exception e) {
            if (!searchCancelled && generation == searchGeneration) {
                error("搜索失败: " + e.getMessage());
                state = State.IDLE;
                btnOff();
            }
        }
    }

    /**
     * 扫描单个末地城单元格 (对齐 Ying ElytraSearchFallback.scanCell)：
     * 单点异常跳过继续 (不中断整个搜索)，黑名单/已访问/本会话跳过按 ShipFilterLogic 判定。
     * stats[0]=有船 [1]=可构造 [2]=排除 [3]=单点异常
     */
    private ShipTarget scanShipCell(EndCity endCity, EndCityGenerator generator, ChunkRand rand,
                                    EndBiomeSource biomeSource, EndTerrainGenerator terrainGen,
                                    long seed, int prx, int prz, int drx, int drz, int[] stats) {
        int rx = prx + drx;
        int rz = prz + drz;
        CPos city = endCity.getInRegion(seed, rx, rz, rand);
        if (city == null) return null;
        // 与 miku ElytraFinder 完全一致：seedfinding 生物群系判定 (末地中山/高岛)
        if (!endCity.canSpawn(city, biomeSource)) return null;
        // 与 miku ElytraFinder 完全一致：seedfinding 地形高度判定 (min(getHeightInGround) ≥ 60)
        if (!endCity.canGenerate(city, terrainGen)) return null;
        try {
            generator.generate(terrainGen, city.getX(), city.getZ(), rand);
            boolean hasShip = generator.hasShip();
            if (!hasShip) return null;
            stats[0]++;
            ShipTarget t = extractShip(generator, city);
            if (t == null) return null;
            stats[1]++;
            // ③ 忽略已访问记录：开启时跳过黑名单过滤，重新扫描所有预测船
            if (!ShipFilterLogic.shouldIncludeShip(ignoreVisited.get(), isBlacklisted(t.headPos), sessionSkip.contains(blacklistKey(t.headPos)))) {
                stats[2]++;
                return null;
            }
            if (debugSetting.get()) {
                debugLog("ship city=(" + city.getX() + "," + city.getZ() + ") head=(" + t.headPos.getX() + "," + t.headPos.getY() + "," + t.headPos.getZ() + ") facing=" + t.facing
                    + " E=" + String.format("%.4f", endIslandDensity(city.getX() * 16 + 8, city.getZ() * 16 + 8)));
            }
            return t;
        } catch (Exception e) {
            // 单点异常：跳过该城继续扫 (对齐 Ying scanCell 的 catch，不中断整个搜索)
            stats[3]++;
            if (debugSetting.get()) debugLog("单点异常跳过: city=(" + city.getX() + "," + city.getZ() + ") " + e.getMessage());
            return null;
        } finally {
            generator.reset();
        }
    }

    private ShipTarget extractShip(EndCityGenerator generator, CPos chunk) {
        try {
            BlockPos itemFrame = null;
            int sternX = 0, sternZ = 0, sternCount = 0;
            for (Pair<Generator.ILootType, BPos> p : generator.getChestsPos()) {
                Generator.ILootType lt = p.getFirst();
                BPos b = p.getSecond();
                if (lt == EndCityGenerator.LootType.SHIP_ELYTRA) {
                    itemFrame = new BlockPos(b.getX(), b.getY(), b.getZ());
                } else if (lt == EndCityGenerator.LootType.SHIP_SENTRY_2 || lt == EndCityGenerator.LootType.SHIP_SENTRY_3) {
                    sternX += b.getX();
                    sternZ += b.getZ();
                    sternCount++;
                }
            }
            if (itemFrame == null || sternCount == 0) return null;

            int dx = sternX / sternCount - itemFrame.getX();
            int dz = sternZ / sternCount - itemFrame.getZ();
            Direction behind = (Math.abs(dx) > Math.abs(dz))
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            Direction facing = behind.getOpposite();

            // X/Z 由种子精确计算；Y 不可靠 (种子库用整城平均地形高度)，留到飞行靠近后扫描真实龙头修正
            BlockPos head = itemFrame.offset(facing, 7).offset(Direction.UP, 3);
            return new ShipTarget(chunk, head, facing);
        } catch (Exception e) {
            return null;
        }
    }

    private void beginFlyToTarget() {
        if (targetIndex >= ships.size()) {
            completeTask("全部末地城已处理，任务完成.");
            return;
        }
        current = ships.get(targetIndex);
        waypoints = ShipWaypoints.from(current.headPos, current.facing);
        climbing = false;
        headFound = false;
        frameAirChecked = false;
        frameAirEmpty = false;
        frameAirScanTicks = 0;
        exactHead = null;
        nearTicks = 0;
        scanAttempts = 0;
        firstFireworkDone = false;
        landingRecover = false;
        recoverCount = 0;
        stateTick = 0;
        info("飞向最近未去过的船，朝向=" + directionLabel(current.facing));  // V4.24: 不显示坐标（无政府服禁止暴露）
        state = State.FLYING;
    }

    // ========== Tick ==========
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        try {
            if (mc.player == null || mc.world == null) return;
            stateTick++;

            // 低高度安全网：只有任务运行中才生效 (防虚空掉物)；低于阈值自动退出游戏 (断开服务器)
            if (state != State.IDLE && state != State.DONE
                && lowYExit.get() && mc.player.getY() < lowYThreshold.get()) {
                warning("当前高度低于阈值 " + lowYThreshold.get() + "，自动退出游戏.");  // V4.24: 不显示 Y 坐标
                disconnect("FO 鞘翅采集: Y 低于阈值 " + lowYThreshold.get() + "，自动退出游戏保护");
                stopTask("低高度自动退出游戏.");
                return;
            }

            // 独立 PullUp 模块强制拉升期间：暂停采集器全部控制 (危险高度保护已移至独立 PullUp 模块)
            if (pullUpSuppressed) {
                return;
            }

            // 存储会话 (存鞘翅/补货) 优先于飞行状态机
            if (storagePhase != StoragePhase.NONE) {
                onStorageTick();
                return;
            }

            switch (state) {
                case RISING -> onRising();
                case FLYING -> onFlying();
                case LANDING -> onLanding();
                case COLLECTING -> onCollecting();
                default -> {}
            }
        } catch (Throwable t) {
            error("任务异常: " + t);
            t.printStackTrace();
            stopTask("任务异常，停止任务.");
        }
    }

    // ========== 起飞爬升 (跳 -> 展开鞘翅 -> 抬头 -> 烟花爬升) ==========
    private void onRising() {
        // 恢复流程有独立的阶段超时保护 (30s/30s/20s)，不套用普通起飞超时 (恢复全程可达几十秒)
        if (!landingRecover && stateTick > 400) {
            error("起飞超时 (可能没穿鞘翅或没有烟花).");
            state = State.IDLE;
            btnOff();
            releaseForward();
            return;
        }

        // 地面且需要存储操作时先执行 (补货/存鞘翅)
        if (mc.player.isOnGround() && stateTick <= 2) {
            maybeStartSession();
            if (storagePhase != StoragePhase.NONE) return;
        }

        // 起飞方向：降落恢复阶段 0 反向 (背对降落点) 拉升；阶段 1 pitch40 模式平滑转向飞向降落点；末地城起飞朝向龙头朝向，首次平地起飞保持原方向
        if (landingRecover) {
            recoverStageTick++;
            if (recoverStage == 0) {
                rotateFastTo(recoveryYaw, mc.player.getPitch());
            } else {
                // 阶段 1: pitch40 模式 (平滑转向 + 平缓下滑)，不用急速俯冲
                rotateTo(yawTowards(waypoints.landing), cruiseGlidePitch.get().floatValue());
            }
        } else if (takeoffFacing != null) {
            faceDirection(takeoffFacing);
        }

        // 降落恢复: 阶段 0 抬头爬升 (到降落点上方 25 格)；阶段 1 pitch40 模式下滑 (pitch 由 rotateTo 控制)；正常起飞抬头爬升
        if (!(landingRecover && recoverStage == 1)) {
            mc.player.setPitch(-(float) takeoffPitch.get().doubleValue());
        }
        mc.options.forwardKey.setPressed(true);

        if (!mc.player.isGliding()) {
            if (mc.player.isOnGround()) {
                // 第一次起跳 (先停掉遗留的 baritone 寻路，防止它抢走移动控制权导致起跳失败)
                PathManagers.get().stop();
                mc.player.jump();
            } else if (stateTick % 4 == 0) {
                // 在空中再跳一下展开鞘翅（与起跳错开，避免反作弊误判）
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }

        // 展开鞘翅后立刻使用第一个烟花，之后按 UI 间隔
        if (!firstFireworkDone) {
            useFirework();
            firstFireworkDone = true;
            lastFireworkTick = stateTick;
        } else if (!(landingRecover && recoverStage == 1)
            && stateTick - lastFireworkTick >= Math.max(1, Math.round(fireworkInterval.get() * 20))) {
            useFirework();
            lastFireworkTick = stateTick;
        }

        if (landingRecover) {
            // 恢复流程: 0 反向拉升到降落点上方 25 格 → 1 pitch40 模式飞向降落点，到达降落点上方后转缓降
            // 超时保护：拉升 30 秒、飞向降落点 30 秒，超时强制进入下一阶段，杜绝一直升天
            BlockPos lp = waypoints.landing;
            if (recoverStage == 0) {
                if (mc.player.getY() >= lp.getY() + 25 || recoverStageTick > 600) {
                    recoverStage = 1;
                    recoverStageTick = 0;
                    info("已拉升到降落点上方 25 格，用 pitch40 模式飞向降落点.");
                }
            } else {
                if (horizontalDistance(lp) <= 3 || recoverStageTick > 600) {
                    releaseForward();
                    landingRecover = false;
                    recoverStage = 0;
                    recoverStageTick = 0;
                    info("已回到降落点上方，进入缓降.");
                    state = State.LANDING;
                    stateTick = 0;
                }
            }
        } else if (mc.player.getY() >= riseHeight.get()) {
            if (ships.isEmpty()) {
                // 等后台重新搜索完成
                mc.player.setPitch(-20f);
                mc.options.forwardKey.setPressed(true);
            } else {
                releaseForward();
                firstFireworkDone = false;
                beginFlyToTarget();
            }
        }
    }

    // ========== 飞行 (高度保持 + 龙头精确扫描，找到前不下降) ==========
    private void onFlying() {
        if (!mc.player.isGliding()) {
            if (mc.player.isOnGround()) {
                firstFireworkDone = false;
                takeoffFacing = null;
                state = State.RISING;
                stateTick = 0;
                return;
            }
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }

        BlockPos lp = waypoints.landing;

        // 未确认龙头：进入种子区域后检测龙头，每 5 秒再检测一次，检测 8 次仍没有就直接走
        if (!headFound) {
            if (horizontalDistance(lp) < 80) {
                nearTicks++;
                if (nearTicks == 1 && debugSetting.get() && !cityTerrainHigh()) {
                    // 船可能悬在虚空上 (城市在地上)，地形高度判断不可靠，交给龙头扫描确认，不直接杀
                    debugLog("注意: 地形高度判断不足 (船可能在虚空上)，改由龙头扫描确认.");
                }
                if (nearTicks % 20 == 0) {
                    // 扫描框 (3x3 区块) 全部加载后才检测并计数，避免加载慢的真城被误杀
                    boolean loaded = true;
                    for (int dx = -1; dx <= 1 && loaded; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (!mc.world.isChunkLoaded((current.headPos.getX() >> 4) + dx, (current.headPos.getZ() >> 4) + dz)) {
                                loaded = false;
                                break;
                            }
                        }
                    }
                    if (loaded) {
                        scanAttempts++;
                        // 每秒检测一次，共 7 次 (±40 高度范围)
                        DragonHead exact = findDragonHead(current.headPos, 10, 40);
                        if (exact != null) {
                            // 位置用扫描结果；朝向用种子计算结果 (区块里墙面龙头的 FACING 在部分服务端会被翻转，不可靠)
                            exactHead = exact.pos;
                            waypoints = ShipWaypoints.from(exactHead, current.facing);
                            headFound = true;
                            info("已确认龙头，朝向=" + directionLabel(current.facing));  // V4.24: 不显示坐标（无政府服禁止暴露）
                            if (debugSetting.get()) {
                                int realTop = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, exactHead.getX(), exactHead.getZ());
                                debugLog("head=(" + exactHead.getX() + "," + exactHead.getY() + "," + exactHead.getZ() + ") 龙头下地表=" + realTop + " seedY=" + current.headPos.getY());
                            }
                        } else {
                            info("检测龙头第 " + scanAttempts + "/7 次未找到");  // V4.24: 不显示目标坐标（无政府服禁止暴露）
                        }
                    }
                }
                if (!headFound && scanAttempts >= 7) {
                    // 最终仲裁：±200 高度范围整列再扫一次，确认真的没有龙头才拉黑
                    DragonHead last = findDragonHead(current.headPos, 10, 200);
                    if (last != null) {
                        exactHead = last.pos;
                        waypoints = ShipWaypoints.from(exactHead, current.facing);
                        headFound = true;
                        info("整列扫描发现龙头: " + exactHead + " 朝向=" + current.facing);
                    } else {
                        // 确认没有龙头：判定为假城，永久拉黑并重新搜索
                        warning("检测 7 次且整列无龙头，判定为假城，加入黑名单并重新搜索.");
                        addBlacklist(current.headPos);
                        sessionSkip.add(blacklistKey(current.headPos));
                        ships.clear();
                        targetIndex = 0;
                        searchExtends = 0;
                        takeoffFacing = null;
                        firstFireworkDone = false;
                        state = State.RISING;
                        stateTick = 0;
                        launchSearch();
                        return;
                    }
                }
            } else {
                nearTicks = 0;
            }

            if (headFound) {
                stateTick = 0;
            }
        }

        // 龙头已确认 → 空中检查展示框：只查 p3 这个精确坐标上有没有"挂在墙上的"展示框实体，
        // 有 → 再看框里有没有鞘翅；没有 → 没有鞘翅可拿 → 跳过该船：
        // p3 = 展示框精确位置 (龙头 + 船尾方向 7 格 - 3 格，结构偏移)；
        // 只检测 p3 这一个格子的展示框，避免加载范围内相邻船的展示框被误判成目标船的
        if (headFound && !frameAirChecked) {
            if (frameAirScanTicks % 20 == 0) {
                ItemFrameEntity airFrame = findFrameAt(waypoints.p3); // 第一步：精确坐标上有没有展示框实体
                if (airFrame != null) {
                    frameAirChecked = true;
                    frameAirEmpty = airFrame.getHeldItemStack().getItem() != Items.ELYTRA; // 第二步：框里有没有鞘翅
                    if (frameAirEmpty) {
                        info("展示框里没有鞘翅，不降落：拉升到 max-height 以上后跳过该船.");
                        climbing = true;
                    } else {
                        info("展示框里有鞘翅，按原计划降落.");
                    }
                }
            }
            frameAirScanTicks++;
            if (frameAirScanTicks > 120) {
                frameAirChecked = true;
                frameAirEmpty = true; // 找不到展示框实体 → 没有鞘翅可拿 → 跳过该船
                info("空中未找到展示框实体，按无鞘翅处理 (跳过该船).");
            }
        }

        // 高度保持滞回飞行
        float yaw = yawTowards(lp);
        double y = mc.player.getY();
        if (climbing) {
            if (y >= maxHeight.get()) climbing = false;
        } else {
            if (y <= minHeight.get()) climbing = true;
        }
        float pitch = climbing ? -(float) cruiseClimbPitch.get().doubleValue() : (float) cruiseGlidePitch.get().doubleValue();

        rotateTo(yaw, pitch);
        mc.options.forwardKey.setPressed(true);

        // 对齐 Ying：巡航爬升阶段按间隔使用烟花加速 (防掉速)，滑翔/下滑阶段不烧烟花 (省消耗)
        if (FlightFireworkLogic.shouldUseFirework(climbing, false, stateTick, lastFireworkTick, (int) Math.max(1, Math.round(fireworkInterval.get() * 20)))) {
            useFirework();
            lastFireworkTick = stateTick;
        }

        // 先飞到降落点正上方，再进入下降；展示框无鞘翅则先拉升到 max-height 以上，到了就跳过该船
        if (frameAirEmpty) {
            if (mc.player.getY() < maxHeight.get()) {
                climbing = true;
                // 紧急拉升：必须用烟花加速，否则速度掉光会坠落 (与 RISING 共用放烟花间隔)
                if (FlightFireworkLogic.shouldUseFirework(climbing, true, stateTick, lastFireworkTick, (int) Math.max(1, Math.round(fireworkInterval.get() * 20)))) {
                    useFirework();
                    lastFireworkTick = stateTick;
                }
            } else {
                leaveShipNow();
                return;
            }
        } else if (headFound && horizontalDistance(lp) <= 3) {
            state = State.LANDING;
            stateTick = 0;
        }
    }

    // ========== 与独立 PullUp 模块的协调 ==========
    // 拉升前中止存储会话：容器界面开着时玩家被冻结无法拉升，先关界面/停寻路/复位再拉升 (不影响任务，拉升完继续)
    // 供 PullUp 模块在强制拉升开始时调用
    void abortStorageForPullUp() {
        if (storagePhase == StoragePhase.NONE) return;
        if (isContainerOpen()) mc.player.closeHandledScreen();
        PathManagers.get().stop();
        releaseForward();
        storagePhase = StoragePhase.NONE;
        storageTick = 0;
        resetStorageClick();
        info("危险高度: 中止存储会话，强制拉升.");
    }

    // PullUp 模块用：采集器是否处于降落/降落恢复 (此时由 landing 自身接管，独立拉升模块不干预)
    boolean isLandingOrRecovering() {
        return state == State.LANDING || landingRecover;
    }

    // PullUp 模块拉升期间：暂停采集器全部控制 (由 PullUp 全权接管)
    boolean isPullUpSuppressed() {
        return pullUpSuppressed;
    }

    void setPullUpSuppressed(boolean suppressed) {
        pullUpSuppressed = suppressed;
        if (!suppressed) releaseForward();
    }

    // ========== 缓降 (龙头已精确确认) ==========
    private void onLanding() {
        BlockPos lp = waypoints.landing;
        double hDist = horizontalDistance(lp);

        // 降落检测：与降落点 Y 轴差低于 -10 (自身比降落点低 10 格，且仍在空中) → 反向 (背对降落点) 触发拉升，升到降落点上方 25 格
        if (!landingRecover && !mc.player.isOnGround()
            && mc.player.getY() < lp.getY() - 10) {
            recoverCount++;
            if (recoverCount > 3) {
                stopTask("多次偏离降落点无法降落，停止任务.");
                return;
            }
            landingRecover = true;
            recoverStage = 0;
            recoverStageTick = 0;
            recoveryYaw = yawTowards(lp) + 180f;   // 反向: 背对降落点拉升
            firstFireworkDone = false;
            info("低于降落点 10 格，反向拉升 (目标降落点上方 25 格).");
            state = State.RISING;
            stateTick = 0;
            return;
        }

        if (mc.player.isOnGround()) {
            if (hDist < 3) {
                releaseForward();
                recoverCount = 0;
                info("已降落在降落点.");
                state = State.COLLECTING;
                collectStep = CollectStep.TO_P1;
                stateTick = 0;
                beginCollectStep();
                return;
            }
            // 落偏了：baritone 走到降落点
            if (stateTick < 6 || !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(lp, false);
            }
            if (stateTick > 6 && !PathManagers.get().isPathing() && hDist < 2.5) {
                releaseForward();
                state = State.COLLECTING;
                collectStep = CollectStep.TO_P1;
                stateTick = 0;
                beginCollectStep();
            }
            return;
        }

        if (!mc.player.isGliding()) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }

        // 先急速下降，直到距离降落点小于 20 格才触发缓降 (直接瞄准，防止高速绕圈)
        double ddx = mc.player.getX() - (lp.getX() + 0.5);
        double ddy = mc.player.getY() - (lp.getY() + 0.5);
        double ddz = mc.player.getZ() - (lp.getZ() + 0.5);
        if (ddx * ddx + ddy * ddy + ddz * ddz > 400) {
            mc.player.setYaw(yawTowards(lp));
            mc.player.setPitch(55f);
            mc.options.forwardKey.setPressed(true);
            return;
        }

        if (hDist > 3) {
            // 尚未到达准确坐标：直接瞄准飞向它并逐渐下降
            double dy = mc.player.getY() - (lp.getY() + 1);
            mc.player.setYaw(yawTowards(lp));
            mc.player.setPitch((float) Math.min(45, Math.max(5, dy * 0.8)));
            mc.options.forwardKey.setPressed(true);
        } else {
            // 已到准确坐标：缓降（平视 + 翻转 yaw 抵消水平速度）
            releaseForward();
            mc.player.setPitch(0f);
            if (stateTick % 2 == 0) {
                mc.player.setYaw(mc.player.getYaw() + 180f);
            }
        }
    }

    // ========== 地面采集 ==========
    private void beginCollectStep() {
        if (waypoints == null) { state = State.RISING; return; }
        switch (collectStep) {
            case TO_P1 -> PathManagers.get().moveTo(waypoints.p1, false);
            case TO_P2 -> PathManagers.get().moveTo(waypoints.p2, false);
            case TO_P3 -> PathManagers.get().moveTo(waypoints.p3, false);
            case HIT_FRAME -> {
                // 只记录基数，不在这里攻击：先在 onCollecting 确认框里有鞘翅再打
                elytraBeforeFrame = countElytraMain();
            }
            case PICK_UP -> pickUpElytra();
            case EQUIP -> equipElytra();
            case EXIT_P1 -> PathManagers.get().moveTo(waypoints.p1, false);
            case EXIT_LANDING -> PathManagers.get().moveTo(waypoints.landing.down(4), false);
            case LEAVE_SHIP -> PathManagers.get().moveTo(waypoints.landing.down(4), false);
            default -> {}
        }
    }

    private void onCollecting() {
        switch (collectStep) {
            case TO_P1 -> { if (reached(waypoints.p1, 2.5)) advance(CollectStep.TO_P2); }
            case TO_P2 -> { if (reached(waypoints.p2, 2.5)) advance(CollectStep.WAIT_SHULKER); }
            case WAIT_SHULKER -> {
                // 只等潜影贝还在鞘翅正前方固定一格时 (结构 SHIP_SENTRY_1 位置)；传走/闪现到别处都不算威胁
                if (!hasShulkerAtGuard()) {
                    advance(CollectStep.TO_P3);
                } else if (stateTick - lastGotoTick >= 20) {
                    // 潜影贝还在：每 1 秒重新寻路 #2 修正站位
                    PathManagers.get().moveTo(waypoints.p2, false);
                    lastGotoTick = stateTick;
                }
            }
            case TO_P3 -> { if (reached(waypoints.p3, 2.0)) advance(CollectStep.HIT_FRAME); }
            case HIT_FRAME -> {
                ItemFrameEntity frame = getNearestItemFrame(killAuraReach.get() + 2);
                if (frame != null && stateTick == 1) {
                    // 记录到达时的原始内容：打掉后展示框会变空，只有到达时就没鞘翅才直接离开
                    frameHadElytra = frame.getHeldItemStack().getItem() == Items.ELYTRA;
                    if (!frameHadElytra) {
                        info("展示框里没有鞘翅，直接离开该船.");
                        leaveShipNow();
                        return;
                    }
                }
                // 首个 tick 立即打一次，之后每 1 秒补打 (最多 3 次)，等掉落出现
                if ((stateTick <= 1 || stateTick % 20 == 0) && noElytraTicks < 3) {
                    noElytraTicks++;
                    hitItemFrame();
                }
                if (findDroppedElytra() != null || countElytraMain() > elytraBeforeFrame) {
                    advance(CollectStep.PICK_UP);
                } else if (stateTick > 80) {
                    info("攻击展示框后没有鞘翅掉落，直接离开该船.");
                    leaveShipNow();
                }
            }
            case PICK_UP -> {
                // 掉落的鞘翅优先：径直走过去捡 (不依赖 baritone，就 1~3 格)
                ItemEntity item = findDroppedElytra();
                if (item != null) {
                    walkTowards(item.getX(), item.getZ());
                    if (item.squaredDistanceTo(mc.player) < 1.2) {
                        mc.options.forwardKey.setPressed(false);
                    }
                } else if (countElytraMain() > elytraBeforeFrame) {
                    // 已捡到鞘翅 → 进入穿戴
                    mc.options.forwardKey.setPressed(false);
                    advance(CollectStep.EQUIP);
                } else if (stateTick > 60) {
                    // 掉落物不见了但没捡到：直接走，不无限重试
                    info("鞘翅掉落消失未拾取，直接离开该船.");
                    leaveShipNow();
                }
            }
            case EQUIP -> {
                if (isWearingFullDurabilityElytra()) {
                    // 捡到鞘翅 (已穿戴) → 立刻在当前位置触发背包检测 (存鞘翅/补货)，不用等走回降落点；
                    // 每艘船只在捡到鞘翅后尝试一次会话，防止缺口未补齐时原地无限重启会话
                    if (!pickupSessionDone) {
                        pickupSessionDone = true;
                        maybeStartSession();
                        if (storagePhase != StoragePhase.NONE) {
                            return; // 存储会话在船旁直接执行，结束后回本步骤继续
                        }
                    }
                    advance(CollectStep.EXIT_P1);
                } else {
                    equipElytra();
                }
            }
            case EXIT_P1 -> {
                if (reached(waypoints.p1, 2.5)) {
                    advance(CollectStep.EXIT_LANDING);
                } else if (ReturnPathLogic.timedOut(stateTick)) {
                    // 走回降落点超时 (StorageReturn 语义：30s 走不到也继续，不卡死)
                    warning("走回降落点超时 (30s)，跳过该步骤继续.");
                    advance(CollectStep.EXIT_LANDING);
                } else if (ReturnPathLogic.shouldRepath(stateTick, lastGotoTick, PathManagers.get().isPathing())) {
                    // 每 20 tick 或寻路失败时重发 moveTo (对齐 Ying StorageReturn REPATH_INTERVAL 语义)
                    PathManagers.get().moveTo(waypoints.p1, false);
                    lastGotoTick = stateTick;
                }
            }
            case EXIT_LANDING -> {
                if (reached(waypoints.landing, 2.5)) {
                    addBlacklist(current.headPos);
                    info("该船完成，加入黑名单.");
                    finishShip();
                } else if (ReturnPathLogic.timedOut(stateTick)) {
                    // 对齐 Ying StorageReturn 超时语义：回不到起飞点宁可停止任务，也不原地起飞撞墙
                    warning("回不到降落点 (30s)，停止任务.");
                    stopTask("回不到起飞点 (30s)，FO 鞘翅采集任务停止.");
                } else if (ReturnPathLogic.shouldRepath(stateTick, lastGotoTick, PathManagers.get().isPathing())) {
                    PathManagers.get().moveTo(waypoints.landing.down(4), false);
                    lastGotoTick = stateTick;
                }
            }
            case LEAVE_SHIP -> {
                // 已回降落点：修正起飞方向后起飞离开
                if (reached(waypoints.landing.down(4), 2.5)) {
                    releaseForward();
                    info("已回到降落点，起飞离开.");
                    state = State.RISING;
                    stateTick = 0;
                } else if (ReturnPathLogic.timedOut(stateTick)) {
                    if (relaunchAfterReturn) {
                        // 采集完成回起飞点超时：宁可停止任务，也不原地起飞撞墙 (对齐 Ying StorageReturn)
                        warning("回降落点超时 (30s)，停止任务.");
                        stopTask("回不到起飞点 (30s)，FO 鞘翅采集任务停止.");
                    } else {
                        // 展示框没鞘翅跳过船：回降落点超时原地起飞离开 (原行为)
                        warning("回降落点超时 (30s)，原地起飞离开.");
                        releaseForward();
                        state = State.RISING;
                        stateTick = 0;
                    }
                } else if (ReturnPathLogic.shouldRepath(stateTick, lastGotoTick, PathManagers.get().isPathing())) {
                    PathManagers.get().moveTo(waypoints.landing.down(4), false);
                    lastGotoTick = stateTick;
                }
            }
        }
    }

    private void advance(CollectStep next) {
        collectStep = next;
        stateTick = 0;
        lastGotoTick = 0;
        noElytraTicks = 0;
        frameHadElytra = false;
        pickupSessionDone = false;
        beginCollectStep();
    }

    // ========== 任务控制 (完成/停止/掉线/退出) ==========
    // 自动断开服务器连接 (用户要求: 背包潜影盒也满了就自动下线 log 掉)
    private void disconnect(String reason) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.getConnection().disconnect(Text.literal(reason));
    }

    private void stopTask(String reason) {
        info(reason);
        searchCancelled = true;
        searchGeneration++;
        searchThread = null;
        storagePhase = StoragePhase.NONE;
        storageTick = 0;
        landingRecover = false;
        recoveryYaw = 0f;
        recoverStageTick = 0;
        recoverCount = 0;
        recoveryAttempts = 0;
        releaseForward();
        resetStorageClick();
        if (state != State.IDLE) state = State.IDLE;
        PathManagers.get().stop();
        btnOff();
    }

    private void completeTask(String reason) {
        // 只停止任务，绝不在模块内关闭游戏 (防止任何路径导致游戏静默退出)
        stopTask(reason);
    }

    // ========== 船完成 → 检查存储需求 ==========
    private void finishShip() {
        maybeStartSession();
        if (storagePhase == StoragePhase.NONE) {
            relaunchAfterShip();
        }
    }

    private void maybeStartSession() {
        // 存鞘翅触发条件：只要背包里有未穿戴鞘翅就存 (用户要求"取得一个鞘翅就自动存入潜影盒"，
        // 不再等背包快满，防止鞘翅攒在背包里、也避免和补货抢空间)
        dumpNeeded = countElytraMain() > 0;
        resupplyNeeded = anyDeficit();
        if (dumpNeeded || resupplyNeeded) {
            info("存储会话: 存鞘翅=" + dumpNeeded + ", 补货=" + resupplyNeeded);
            if (resupplyNeeded) {
                // 明确提示缺哪些物资 (修复: 之前只报 true/false, 用户不知道缺什么)
                StringBuilder sb = new StringBuilder("缺少物资: ");
                for (SupplyRule r : parseSupplies()) {
                    int have = countItem(r.item);
                    if (have < r.min) {
                        sb.append(r.item.getName().getString())
                            .append("(").append(have).append("/").append(r.min).append(") ");
                    }
                }
                info(sb.toString().trim());
            }
            // 快捷路径：只存鞘翅 (无补货) 且背包有可用鞘翅盒/空盒 → 直接放盒存，不用开末影箱
            // (用户要求"取得一个鞘翅就自动存入潜影盒"，背包常备盒时最省事；只用鞘翅盒/空盒，绝不污染补给盒)
            if (dumpNeeded && !resupplyNeeded) {
                int bpBox = findBackpackElytraBox();
                if (bpBox != -1) {
                    info("存储: 背包有可用潜影盒，直接放盒存鞘翅 (无需末影箱).");
                    boxInventorySlot = bpBox;
                    boxFromInventory = true;
                    pickInvSlot = bpBox;
                    boxPurposeSupply = false;
                    ecPos = null;
                    boxPos = null;
                    storagePhase = StoragePhase.PLACE_BOX;
                    storageTick = 0;
                    supplyScanStart = 0;
                    supplyPauseDump = false;
                    resupplyRescan = false;
                    releaseForward();
                    PathManagers.get().stop();
                    return;
                }
            }
            storagePhase = StoragePhase.PLACE_EC;
            storageTick = 0;
            ecPos = null;
            boxPos = null;
            boxInventorySlot = -1;
            boxFromInventory = false;
supplyScanStart = 0;
        supplyPauseDump = false;
        resupplyRescan = false;
            releaseForward();
            PathManagers.get().stop();
        }
    }

    private void relaunchAfterShip() {
        ships.clear();
        targetIndex = 0;
        searchExtends = 0;
        // 起飞前触发的存储会话可能还没有任何船 (current == null)，此时不修正起飞方向
        takeoffFacing = current != null ? current.facing : null;
        firstFireworkDone = false;
        landingRecover = false;
        // 对齐 Ying t()：已降落且不在起飞点 → 先回降落点 (LEAVE_SHIP) 修正起飞方向再起飞，防止原地起飞撞墙
        relaunchAfterReturn = true;
        if (mc.player.isOnGround() && waypoints != null && horizontalDistance(waypoints.landing) > 2.5) {
            info("采集完成：先回降落点修正起飞方向后再起飞.");
            state = State.COLLECTING;
            collectStep = CollectStep.LEAVE_SHIP;
            stateTick = 0;
            lastGotoTick = 0;
            beginCollectStep();
        } else {
            state = State.RISING;
            stateTick = 0;
        }
        launchSearch();
    }

    // 展示框没有鞘翅掉落：离开该船并重新搜索
    // 触发时机区分：在空中 → 直接起飞离开；已降落 → 先回降落点修正起飞方向，再起飞离开
    private void leaveShipNow() {
        addBlacklist(current.headPos);
        info("该船跳过并加入黑名单，重新搜索最近未去过的船.");
        ships.clear();
        targetIndex = 0;
        searchExtends = 0;
        takeoffFacing = current.facing;
        firstFireworkDone = false;
        landingRecover = false;
        relaunchAfterReturn = false; // 跳过船场景：回降落点超时仍原地起飞离开 (原行为)
        releaseForward();
        launchSearch();
        if (mc.player.isOnGround() && waypoints != null && horizontalDistance(waypoints.landing) > 2.5) {
            // 已降落：先走到降落点 (甲板站立点) 修正起飞方向
            info("已降落：先回降落点修正起飞方向后再离开.");
            state = State.COLLECTING;
            collectStep = CollectStep.LEAVE_SHIP;
            stateTick = 0;
            beginCollectStep();
        } else {
            // 在空中或已在降落点：直接起飞离开
            state = State.RISING;
            stateTick = 0;
        }
    }

    // ========== 存储会话主循环 (主线程 tick 驱动) ==========
    private void onStorageTick() {
        try {
            storageTick++;
            if (storageTick > 1200) {
                stopTask("存储会话超时 (阶段=" + storagePhase + ")，停止任务.");
                return;
            }
            releaseForward();
            execMoves(); // 点击握手：等服务器回包确认后再继续下一步
            switch (storagePhase) {
                case PLACE_EC -> onPlaceEc();
                case OPEN_EC -> onOpenEc();
                case PICK_BOX -> onPickBox();
                case CLOSE_SCREEN -> onCloseScreen();
                case PLACE_BOX -> onPlaceBox();
                case OPEN_BOX -> onOpenBox();
                case FILL_BOX -> onFillBox();
                case TAKE_SUPPLIES -> {
                    // 补货中背包可用空格 ≤ dump-free-slots 且身上还有未穿戴鞘翅 → 暂停补给：
                    // 挖掉补给盒放入末影箱 → 拿空盒把鞘翅存进去腾背包 → 再次补给
                    if (freeSlots() <= freeSlotDump.get() && countElytraMain() > 0) {
                        supplyPauseDump = true;
                        resupplyRescan = true;
                        dumpNeeded = true;
                        info("补给暂停: 背包剩余 " + freeSlots() + " 槽，先回收补给盒、存鞘翅腾背包.");
                        storagePhase = StoragePhase.MINE_BOX;
                        storageTick = 0;
                    } else {
                        onTakeSupplies();
                    }
                }
                case MINE_BOX -> onMineBox();
                case MINE_EC -> onMineEc();
                default -> {}
            }
        } catch (Throwable t) {
            error("存储会话异常: " + t);
            t.printStackTrace();
            stopTask("存储会话异常，停止任务.");
        }
    }

    private void onPlaceEc() {
        if (storageTick == 1) {
            // 先检测周围有没有末影箱 (上次遗留的)，有就直接打开；但上一轮打开失败过则跳过它直接放新的
            if (ecOpenFailCount == 0) {
                BlockPos existing = findNearbyEnderChest(5);
                if (existing != null) {
                    ecPos = existing;
                    info("存储: 发现周围已有末影箱 " + existing + "，直接打开.");
                    storagePhase = StoragePhase.OPEN_EC;
                    storageTick = 0;
                    openRetry = 0;
                    return;
                }
            }
            int ecSlot = findItemSlot(Items.ENDER_CHEST);
            if (ecSlot == -1) {
                if (dumpNeeded) {
                    // 存鞘翅是刚需 (背包快满)，没末影箱只能停
                    stopTask("背包里没有末影箱，无法存鞘翅，停止任务. 请先准备末影箱.");
                } else {
                    // 只是补货 → 跳过存储会话，继续主任务 (修复: 之前直接停任务导致"一直不启动")
                    info("背包里没有末影箱，跳过补货，继续任务.");
                    storagePhase = StoragePhase.NONE;
                    storageTick = 0;
                    dumpNeeded = false;
                    resupplyNeeded = false;
                }
                return;
            }
            ecBaseline = countItem(Items.ENDER_CHEST);
            ensureItemSelected(ecSlot);
            placeCandidates = findPlaceSpots(null);
            if (placeCandidates.isEmpty() && waypoints != null && waypoints.p2 != null) {
                // 附近没有空位 → 把末影箱放到检测潜影贝的位置 (p2)
                info("附近无空位，改用检测潜影贝的位置 " + waypoints.p2 + " 附近放置.");
                placeCandidates = findPlaceSpotsAt(waypoints.p2, null);
            }
            if (placeCandidates.isEmpty()) {
                stopTask("找不到放置末影箱的位置，停止任务.");
                return;
            }
            placeIndex = 0;
            lastPlaceClick = -100;
            placeClickAttempts = 0;
        }
        // 每 8 tick 点击一次，同一位置最多点 2 次，失败换下一个候选位置
        if (storageTick >= 4 && storageTick - lastPlaceClick >= 8) {
            if (placeIndex >= placeCandidates.size()) {
                stopTask("末影箱放置失败 (已尝试所有候选位置)，停止任务.");
                return;
            }
            BlockPos spot = placeCandidates.get(placeIndex);
            if (mc.player.squaredDistanceTo(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5) > 16) {
                // 距离太远 (如回退到 p2 附近)：先走过去再点
                if (!PathManagers.get().isPathing()) PathManagers.get().moveTo(spot, false);
                return;
            }
            lastPlaceClick = storageTick;
            placeClickAttempts++;
            ecPos = spot;
            // 视角面对目标格下方方块再点 (反作弊校验视线)，face UP 会把末影箱放到目标格上
            faceBlockForPlace(spot.down(), Direction.UP);
            interactAt(spot.down());
            if (placeClickAttempts >= 2) {
                placeIndex++;
                placeClickAttempts = 0;
            }
        }
        if (ecPos != null && isEnderChestBlock(mc.world.getBlockState(ecPos))) {
            PathManagers.get().stop();
            storagePhase = StoragePhase.OPEN_EC;
            storageTick = 0;
            openRetry = 0;
            return;
        }
        if (storageTick > 80) {
            stopTask("末影箱放置失败 (超时)，停止任务.");
        }
    }

    // 扫描周围 (水平半径 radius) 有没有末影箱方块
    private BlockPos findNearbyEnderChest(int radius) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockPos p = mc.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    m.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    if (isEnderChestBlock(mc.world.getBlockState(m))) return m.toImmutable();
                }
            }
        }
        return null;
    }

    private void onOpenEc() {
        if (storageTick == 1) {
            if (ecPos == null || !isEnderChestBlock(mc.world.getBlockState(ecPos))) {
                // 末影箱不在原位：回到放置流程 (重新扫描/重新放置)
                info("末影箱不在原位，重新放置.");
                storagePhase = StoragePhase.PLACE_EC;
                storageTick = 0;
                return;
            }
            standingMovedOff = false;
        }
        // 打开前：若玩家站在末影箱上，先走下来再打开 (站在上面打开会被服务器拒绝/命中判定异常)
        if (!standingMovedOff && isStandingOnBlock(ecPos)) {
            BlockPos off = findStandOffSpot(ecPos);
            if (off != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(off, false);
                info("存储: 玩家站在末影箱上，先走下来再打开.");
            }
            if (isStandingOnBlock(ecPos)) return; // 还没走下来，等 baritone 到位
            standingMovedOff = true;
        }
        // 打开重试：最多 5 次，每次间隔 1 秒 (storageTick 1/21/41/61/81)
        if (openRetry < 5 && storageTick == 1 + openRetry * 20) {
            // V4.16: 六面检测——末影箱顶面可能被盖住、玩家也可能站在侧面/下方，
            // 固定 UP 面交互会被服务器按视线回溯拒绝；改为选玩家正对的面交互
            Vec3d eye = mc.player.getEyePos();
            Direction face = faceById(FacingLogic.bestFaceIndex(
                eye.x, eye.y, eye.z, ecPos.getX() + 0.5, ecPos.getY() + 0.5, ecPos.getZ() + 0.5));
            faceBlockForPlace(ecPos, face);
            interactAt(ecPos, face);
            openRetry++;
        }
        if (isContainerOpen()) {
            if (storageTick < 3) return; // 等服务器同步末影箱内容
            ecOpenFailCount = 0;
            storagePhase = StoragePhase.PICK_BOX;
            storageTick = 0;
        } else if (storageTick > 1 + 4 * 20 + 12) {
            // 5 次都没打开：先检测是否站在末影箱上，站在上面就先走下来再按抓包流程处理；
            // 否则回放置流程，放置一个新的末影箱再试
            if (isStandingOnBlock(ecPos)) {
                standingMovedOff = false;
                storageTick = 0;
                info("打开末影箱失败且玩家站在末影箱上，先走下来再重试.");
                return;
            }
            warning("打开末影箱失败 (已尝试 5 次)，放置新的末影箱再试.");
            ecOpenFailCount++;
            storagePhase = StoragePhase.PLACE_EC;
            storageTick = 0;
            ecPos = null;
            openRetry = 0;
        }
    }

    private void onPickBox() {
        var sh = mc.player.currentScreenHandler;
        int rows = currentRows();
        if (sh == null || rows <= 0) {
            stopTask("末影箱界面异常，停止任务.");
            return;
        }
        // 等服务器同步完末影箱内容再扫描 (否则会误判没有空位潜影盒)
        if (storageTick < 3) return;

        // 取盒流程 (点击握手)
        if (pickStep == 1) {
            if (!moveIdle()) return; // 光标还没拿到盒子
            int emptyScreen = firstEmptyPlayerScreenSlot(sh, rows);
            if (emptyScreen != -1) {
                pickInvSlot = emptyScreen - rows * 9 + 9;
                moveQueue.add(new MoveOp(-1, emptyScreen)); // 放下到空玩家槽
                pickStep = 2;
            } else {
                moveQueue.add(new MoveOp(-1, pickEcSlot));  // 没空位：放回末影箱
                pickStep = 3;
            }
            return;
        }
        if (pickStep == 2) {
            if (!moveIdle()) return;
            boxInventorySlot = pickInvSlot;
            boxFromInventory = false;
            pickStep = 0;
            // 预取备用盒 (鞘翅已存完且无补货需求) → 盒子留在背包，会话直接结束；
            // 正常取盒 (还有鞘翅/补货要处理) → 继续放置流程
            afterClose = (dumpNeeded || resupplyNeeded) ? StoragePhase.PLACE_BOX : StoragePhase.MINE_EC;
            storagePhase = StoragePhase.CLOSE_SCREEN;
            storageTick = 0;
            return;
        }
        if (pickStep == 3) {
            if (!moveIdle()) return;
            pickStep = 0;
            // 补货意图：背包无空位 = 拿不了物资，和"物资不足"同性质，直接停止 (避免每艘船循环跳过补货)
            if (boxPurposeSupply) {
                stopTask("背包无空位放潜影盒，无法补货，停止任务.");
                return;
            }
            // 存鞘翅意图：背包 0 空位 = 鞘翅也捡不起来，任务已卡死，直接停止 (避免每艘船循环跳过)
            stopTask("背包无空位放潜影盒，无法存鞘翅，停止任务.");
            return;
        }
        if (!moveIdle()) return; // 归还上一盒的搬运还没完成

        // 先归还上一盒 (放入末影箱) — QUICK_MOVE shift 点击，服务器自动放到末影箱第一个空位
        if (boxInventorySlot != -1) {
            int screenSlot = invSlotToScreen(rows, boxInventorySlot);
            if (firstEmptyContainerSlot(sh, rows) == -1) {
                stopTask("末影箱已满，无法归还潜影盒，停止任务.");
                return;
            }
            click(SlotActionType.QUICK_MOVE, screenSlot);
            boxInventorySlot = -1;
            return;
        }

        // 诊断日志：打印末影箱实际内容，确认识别情况
        int nonEmpty = 0;
        StringBuilder items = new StringBuilder();
        for (int i = 0; i <= containerLast(rows); i++) {
            ItemStack st = sh.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            nonEmpty++;
            if (items.length() < 80) items.append(st.getItem().getName().getString()).append("(").append(st.getCount()).append(") ");
        }
        info("存储: 末影箱行数=" + rows + " 非空槽=" + nonEmpty + " [" + items + "]");

        // 重新评估需求 (补货不因中途满足而提前结束：必须遍历末影箱到最后一位才收尾)
        // 存鞘翅：会话一旦开始，只要身上还有未穿戴的鞘翅就继续拿下一盒存
        // (一盒存满后腾出的背包格会让 freeSlots 变大，不能用 freeSlots 判定提前结束)
        if (dumpNeeded) {
            dumpNeeded = countElytraMain() > 0;
        }
        if (!dumpNeeded && !resupplyNeeded) {
            // 存完鞘翅且无补货需求：确保背包常备一个可用潜影盒 (用户要求"末影箱的空盒再拿一个出来")，
            // 下一船捡到鞘翅时可直接放盒，不用再开末影箱 (受"自动拿空盒"开关控制)
            if (autoTakeBox.get() && findBackpackElytraBox() == -1) {
                int spare = findEcBoxWithSpace(sh, rows);
                if (spare != -1) {
                    info("存储: 预取一个潜影盒进背包备用.");
                    pickEcSlot = spare;
                    boxPurposeSupply = false;
                    pickStep = 1;
                    moveQueue.add(new MoveOp(spare, -1)); // 拾取盒子到光标
                    return;
                }
            }
            storagePhase = StoragePhase.MINE_EC;
            storageTick = 0;
            return;
        }
        if (dumpNeeded) {
            // 优先用背包里可存鞘翅的潜影盒 (鞘翅盒/空盒，跳过补给盒)
            int bpBox = findBackpackElytraBox();
            if (bpBox != -1) {
                boxInventorySlot = bpBox;
                boxFromInventory = true;
                pickInvSlot = bpBox; // 记录来源槽位，挖回拾取后优先恢复该槽
                boxPurposeSupply = false;
                afterClose = StoragePhase.PLACE_BOX;
                storagePhase = StoragePhase.CLOSE_SCREEN;
                storageTick = 0;
                return;
            }
            int ecBox = findEcBoxWithSpace(sh, rows);
            if (ecBox != -1) {
                pickEcSlot = ecBox;
                boxPurposeSupply = false;
                pickStep = 1;
                moveQueue.add(new MoveOp(ecBox, -1)); // 拾取盒子到光标
                return;
            }
            // 末影箱全是满盒、背包也没有可用鞘翅盒/空盒 (只剩补给盒或满盒)：
            // 按用户要求自动下线保护——既不能把鞘翅塞进补给盒，也不能让鞘翅烂在背包
            info("末影箱与背包均无可用潜影盒 (补给盒已保护)，自动下线.");
            disconnect("FO 鞘翅采集: 末影箱与背包均无空潜影盒，自动下线保护");
            stopTask("末影箱与背包均无可用潜影盒，已自动下线.");
            return;
        }
        if (resupplyNeeded) {
            // 存鞘翅腾空间后重新从头扫描，补给盒 (已过游标) 才能被再次选中继续补货
            if (resupplyRescan) {
                resupplyRescan = false;
                supplyScanStart = 0;
                info("存储: 存鞘翅腾出空间，从末影箱开头重新扫描补给.");
            }
            // 遍历到末影箱最后一位后才收尾：即使中途已经拿齐，也要继续向后遍历
            if (supplyScanStart > containerLast(rows)) {
                if (anyDeficit()) {
                    stopTask("物资不足 (末影箱内已遍历完所有潜影盒)，停止任务.");
                    return;
                }
                info("存储: 补货遍历完成.");
                resupplyNeeded = false;
                storagePhase = StoragePhase.MINE_EC;
                storageTick = 0;
                return;
            }
            // 从扫描游标逐格向后遍历 (游标严格递增，每格最多处理一次)：
            // 散放物资 (末影箱里的非潜影盒物品) 直接 shift 点击拿进背包；潜影盒走取盒流程
            List<SupplyRule> rules = parseSupplies();
            for (int i = supplyScanStart; i <= containerLast(rows); i++) {
                ItemStack st = sh.getSlot(i).getStack();
                if (st.isEmpty()) {
                    supplyScanStart = i + 1;
                    continue;
                }
                if (!isShulkerBox(st)) {
                    // 散放物资：直接 shift 点击 (服务器自动放进背包空位/合并)，不占光标
                    if (!takeFailedItems.contains(st.getItem()) && needsItem(st.getItem())) {
                        supplyScanStart = i + 1;
                        click(SlotActionType.QUICK_MOVE, i);
                        info("存储: 从末影箱直接拿取散放物资 " + st.getItem().getName().getString() + ".");
                    } else {
                        supplyScanStart = i + 1; // 不需要或拿不到的散放物资：跳过
                    }
                    return;
                }
                // 潜影盒：盒内有需要的 (仍有缺口) 且未失败的物资才取
                boolean hasSupply = false;
                for (SupplyRule r : rules) {
                    if (!takeFailedItems.contains(r.item) && needsItem(r.item) && shulkerItems(st).contains(r.item)) {
                        hasSupply = true;
                        break;
                    }
                }
                if (hasSupply) {
                    supplyScanStart = i + 1;
                    pickEcSlot = i;
                    boxPurposeSupply = true;
                    pickStep = 1;
                    moveQueue.add(new MoveOp(i, -1));
                    return;
                }
                supplyScanStart = i + 1; // 这盒没有需要的物资：跳过继续扫
                return;
            }
            // 扫描到尾部但游标还没越界 (理论上不会到这)：直接收尾
            supplyScanStart = containerLast(rows) + 1;
            return;
        }
        storagePhase = StoragePhase.MINE_EC;
        storageTick = 0;
    }

    private void onCloseScreen() {
        if (isContainerOpen()) {
            if (storageTick == 1) mc.player.closeHandledScreen();
            if (storageTick > 40) {
                stopTask("关闭容器界面超时，停止任务.");
                return;
            }
            return;
        }
        if (storageTick < 4) return; // 等服务端处理关闭包
        storagePhase = afterClose;
        afterClose = StoragePhase.NONE;
        storageTick = 0;
    }

    private void onPlaceBox() {
        if (storageTick == 1) {
            if (boxInventorySlot == -1) {
                stopTask("潜影盒槽位丢失，停止任务.");
                return;
            }
            boxInventorySlot = ensureItemSelected(boxInventorySlot);
            // 潜影盒：优先找空气格 + 上方有实心方块 → 对着上方方块放置 (挂在它下方)
            placeCandidates = findBoxPlaceSpots(ecPos);
            boxPlaceFloorFallback = false;
            if (placeCandidates.isEmpty()) {
                // 没有挂放位置 → 直接用后备：空气格 + 下方有实心方块，对着下方方块放置 (顶面敞开，不会被反作弊拦截打开)
                info("附近没有挂放位置 (空气+上方实心)，改用地板放置.");
                placeCandidates = findBoxFloorSpots(ecPos);
                boxPlaceFloorFallback = true;
            }
            if (placeCandidates.isEmpty()) {
                stopTask("找不到放置潜影盒的位置 (需要空气格且上方有方块)，停止任务.");
                return;
            }
            placeIndex = 0;
            lastPlaceClick = -100;
            placeClickAttempts = 0;
        }
        // 每 8 tick 点击一次，同一位置最多点 2 次，失败换下一个候选位置
        if (storageTick >= 4 && storageTick - lastPlaceClick >= 8) {
            if (placeIndex >= placeCandidates.size()) {
                if (!boxPlaceFloorFallback) {
                    // 挂放位置全部失败 → 改用地板放置 (顶面敞开，好打开)
                    info("挂放位置全部放置失败，改用地板放置.");
                    placeCandidates = findBoxFloorSpots(ecPos);
                    boxPlaceFloorFallback = true;
                    placeIndex = 0;
                    placeClickAttempts = 0;
                    if (placeCandidates.isEmpty()) {
                        stopTask("潜影盒放置失败 (找不到地板放置位置)，停止任务.");
                        return;
                    }
                } else {
                    stopTask("潜影盒放置失败 (已尝试所有候选位置)，停止任务.");
                    return;
                }
            }
            BlockPos spot = placeCandidates.get(placeIndex);
            if (mc.player.squaredDistanceTo(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5) > 16) {
                if (!PathManagers.get().isPathing()) PathManagers.get().moveTo(spot, false);
                return;
            }
            lastPlaceClick = storageTick;
            placeClickAttempts++;
            boxPos = spot;
            boxBaseline = countBoxes();
            if (boxPlaceFloorFallback) {
                // 地板放置：对着下方方块顶面放置 (盒子放在它上面)，同末影箱的放置方式
                faceBlockForPlace(spot.down(), Direction.UP);
                interactAt(spot.down(), Direction.UP);
            } else {
                // 挂放：视角面对目标格上方的实心方块 (抬头，反作弊校验视线)，face DOWN 会把潜影盒放到它下方的空气格
                faceBlockForPlace(spot.up(), Direction.DOWN);
                interactAt(spot.up(), Direction.DOWN);
            }
            if (placeClickAttempts >= 2) {
                placeIndex++;
                placeClickAttempts = 0;
            }
        }
        if (boxPos != null && isShulkerBlock(mc.world.getBlockState(boxPos))) {
            PathManagers.get().stop();
            storagePhase = StoragePhase.OPEN_BOX;
            storageTick = 0;
            return;
        }
        // 挂放 + 地板放置两轮尝试需要更多时间
        if (storageTick > 160) {
            stopTask("潜影盒放置失败 (超时)，停止任务.");
        }
    }

    private void onOpenBox() {
        if (storageTick == 1) {
            if (boxPos == null || !isShulkerBlock(mc.world.getBlockState(boxPos))) {
                // 盒子不在原位：挖掉重来 (回末影箱重新取盒)
                warning("潜影盒不在原位，挖掉重来.");
                storagePhase = StoragePhase.MINE_BOX;
                storageTick = 0;
                return;
            }
            standingMovedOff = false;
        }
        // 打开前：若玩家站在潜影盒上，先走下来再打开 (站在上面打开会被服务器拒绝/命中判定异常)
        if (!standingMovedOff && isStandingOnBlock(boxPos)) {
            BlockPos off = findStandOffSpot(boxPos);
            if (off != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(off, false);
                info("存储: 玩家站在潜影盒上，先走下来再打开.");
            }
            if (isStandingOnBlock(boxPos)) return; // 还没走下来，等 baritone 到位
            standingMovedOff = true;
        }
        // 打开重试：最多 5 次，每次间隔 1 秒 (storageTick 1/21/41/61/81)
        if (openRetry < 5 && storageTick == 1 + openRetry * 20) {
            lookAtBoxCenter(); // 对准盒子中心 (不指定面：顶面被盖住时准星也会命中盒子侧面)
            openRetry++;
        }
        // 准星命中盒子后才用 vanilla 右键管线 (Meteor Utils.rightClick → Minecraft.startUseItem)：
        // 交互包由客户端准星射线 (mc.hitResult) 生成，与视线完全一致，反作弊按视线回溯不会拦截
        // (之前手工构造点击位置与视线不符，被反作弊拦截导致打不开)
        if (openRetry > 0 && boxOpenClickTick == -1 && isLookingAtBox()) {
            boxOpenClickTick = storageTick;
            Utils.rightClick();
        } else if (boxOpenClickTick != -1 && !isContainerOpen() && storageTick - boxOpenClickTick > 8) {
            boxOpenClickTick = -1; // 点完 8 tick 还没开：下一轮重试再点
        }
        if (isContainerOpen()) {
            if (storageTick < 3) return; // 等服务器同步潜影盒内容
            recoveryAttempts = 0; // 界面恢复成功，清除异常计数
            storagePhase = boxPurposeSupply ? StoragePhase.TAKE_SUPPLIES : StoragePhase.FILL_BOX;
            storageTick = 0;
            openRetry = 0;
            boxOpenClickTick = -1;
        } else if (storageTick > 1 + 4 * 20 + 12) {
            // 5 次都没打开：先检测是否站在潜影盒上，站在上面就先走下来再按抓包流程处理；
            // 否则挖掉这个盒子，回末影箱重新取盒再试
            if (isStandingOnBlock(boxPos)) {
                standingMovedOff = false;
                storageTick = 0;
                openRetry = 0;
                boxOpenClickTick = -1;
                info("打开潜影盒失败且玩家站在潜影盒上，先走下来再重试.");
                return;
            }
            warning("打开潜影盒失败 (已尝试 5 次)，挖掉重来.");
            storagePhase = StoragePhase.MINE_BOX;
            storageTick = 0;
            openRetry = 0;
            boxOpenClickTick = -1;
        }
    }

    // 把背包里除穿戴外的所有鞘翅存进当前潜影盒 (QUICK_MOVE shift 点击，服务器自动放入盒子，一次一组)
    private void onFillBox() {
        var sh = mc.player.currentScreenHandler;
        int rows = currentRows();
        if (sh == null || rows <= 0) {
            // StorageRecovery：界面未同步/异常 → 重试重开潜影盒 (最多 3 次)，不直接停任务
            if (storageTick < 15) return;
            recoveryAttempts++;
            if (recoveryAttempts > 3) {
                stopTask("潜影盒界面连续异常 (已重试 3 次)，停止任务.");
                return;
            }
            warning("潜影盒界面尚未同步，等待后重新打开 (" + recoveryAttempts + "/3)...");
            storagePhase = StoragePhase.OPEN_BOX;
            storageTick = 0;
            openRetry = 0;
            boxOpenClickTick = -1;
            return;
        }
        if (storageTick < 3) return; // 等服务器同步潜影盒内容
        if (!moveIdle()) return;     // 上一组搬运还没完成
        if (fillIndex == -1) {
            fillIndex = playerFirst(rows);
            info("存储: 开始往潜影盒存鞘翅.");
        }
        // 上次 shift 点击的槽一直没变化 → 盒子满了 (点击被服务器忽略)，收尾
        if (fillStuckSlot >= 0) {
            if (sh.getSlot(fillStuckSlot).getStack().getItem() == Items.ELYTRA) {
                fillStuckTicks++;
                if (fillStuckTicks > 20) {
                    fillIndex = -1;
                    fillStuckSlot = -1;
                    fillStuckTicks = 0;
                    info("存储: 潜影盒已满.");
                    afterClose = StoragePhase.MINE_BOX;
                    storagePhase = StoragePhase.CLOSE_SCREEN;
                    storageTick = 0;
                }
                return; // 点击生效需要等服务器同步，期间不重复点
            }
            fillStuckSlot = -1;
            fillStuckTicks = 0;
        }
        int src = -1;
        for (int s = fillIndex; s <= playerLast(rows); s++) {
            if (sh.getSlot(s).getStack().getItem() == Items.ELYTRA) {
                src = s;
                break;
            }
        }
        if (src == -1) {
            fillIndex = -1;
            info("存储: 鞘翅已全部存入潜影盒.");
            afterClose = StoragePhase.MINE_BOX;
            storagePhase = StoragePhase.CLOSE_SCREEN;
            storageTick = 0;
            return;
        }
        fillIndex = src + 1;
        fillStuckSlot = src;
        fillStuckTicks = 0;
        click(SlotActionType.QUICK_MOVE, src);
    }

    // 从当前潜影盒补充物资：仿 SlimefunHelper AutoSteal — 遍历容器槽位 → 匹配物品 → QUICK_MOVE shift 点击，
    // 服务器自动归位到背包空位/合并；每 tick 重新评估缺口，内容同步晚到也能拿到
    private void onTakeSupplies() {
        var sh = mc.player.currentScreenHandler;
        int rows = currentRows();
        if (sh == null || rows <= 0) {
            // StorageRecovery：界面未同步/异常 → 重试重开潜影盒 (最多 3 次)，不直接停任务
            if (storageTick < 15) return;
            recoveryAttempts++;
            if (recoveryAttempts > 3) {
                stopTask("潜影盒界面连续异常 (已重试 3 次)，停止任务.");
                return;
            }
            warning("潜影盒界面尚未同步，等待后重新打开 (" + recoveryAttempts + "/3)...");
            storagePhase = StoragePhase.OPEN_BOX;
            storageTick = 0;
            openRetry = 0;
            boxOpenClickTick = -1;
            return;
        }
        if (storageTick < 3) return; // 等服务器同步潜影盒内容
        if (!moveIdle()) return;     // 上一组搬运 (如腾杂物) 还没完成

        // 找盒内实际存在且还有缺口的规则 (按配置顺序；缺口按"目标库存 batch"计算，拿到 batch 为止)
        SupplyRule target = null;
        List<SupplyRule> rules = parseSupplies();
        for (SupplyRule rule : rules) {
            if (takeFailedItems.contains(rule.item)) continue;
            if (rule.batch - countItem(rule.item) <= 0) continue;
            for (int c = 0; c <= containerLast(rows); c++) {
                ItemStack st = sh.getSlot(c).getStack();
                if (!st.isEmpty() && st.getItem() == rule.item) {
                    target = rule;
                    break;
                }
            }
            if (target != null) break;
        }

        if (target != null) {
            // 扫盒内该物品的堆叠 → shift 点击 (一次一个，防止发包过快)
            for (int c = 0; c <= containerLast(rows); c++) {
                ItemStack st = sh.getSlot(c).getStack();
                if (st.isEmpty() || st.getItem() != target.item) continue;
                if (takeStuckSlot == c) {
                    takeStuckTicks++;
                    if (takeStuckTicks > 30) {
                        // 一直没动：背包满了 (shift 点击被服务器忽略) → 先把未穿戴的鞘翅放进盒子，再腾杂物，再试
                        takeStuckSlot = -1;
                        takeStuckTicks = 0;
                        int boxFree = firstEmptyContainerSlot(sh, rows);
                        if (boxFree != -1) {
                            int elytra = findUnwornElytraSlot(sh, rows);
                            if (elytra != -1) {
                                moveQueue.add(new MoveOp(elytra, boxFree));
                                info("存储: 背包空间不足，先把鞘翅放入盒子腾出空位.");
                            } else {
                                int junk = findJunkSlotInPlayer(sh, rows, rules);
                                if (junk != -1) {
                                    moveQueue.add(new MoveOp(junk, boxFree));
                                    info("存储: 背包已满，先把杂物放入盒子腾出空位.");
                                } else {
                                    // 无杂物可腾：本会话内不再尝试该物品，避免每 tick 重复点击死循环
                                    takeFailedItems.add(target.item);
                                    info("存储: 背包无空位且无杂物可腾，补货 " + target.item.getName().getString() + " 跳过.");
                                }
                            }
                        } else {
                            takeFailedItems.add(target.item);
                            info("存储: 盒子已满无法腾位，补货 " + target.item.getName().getString() + " 跳过.");
                        }
                    }
                    return; // 等上一次点击生效
                }
                takeStuckSlot = c;
                takeStuckTicks = 0;
                click(SlotActionType.QUICK_MOVE, c);
                return;
            }
            // 盒内没有该物品了
            takeStuckSlot = -1;
            takeStuckTicks = 0;
        }

        // 没有缺口，或盒内已无可拿：连续确认 N tick 才收尾 (防屏幕内容同步延迟误判)
        takeEmptyTicks++;
        if (takeEmptyTicks < 10) return;
        takeEmptyTicks = 0;
        takeStuckSlot = -1;
        takeStuckTicks = 0;
        info("存储: 本盒物资已拿取完毕 (所有能拿且需要的).");
        afterClose = StoragePhase.MINE_BOX;
        storagePhase = StoragePhase.CLOSE_SCREEN;
        storageTick = 0;
    }

    private void onMineBox() {
        BlockState s = mc.world.getBlockState(boxPos);
        if (isShulkerBlock(s)) {
            if (storageTick == 1) {
                pickupWaitStart = -1;
                PathManagers.get().stop();
                // 必须带精准采集工具，否则盒子消失、内容物散落 (baritone 会优先用精准采集工具)
                if (findSilkTouchSlot() == -1) {
                    stopTask("需要精准采集工具才能回收潜影盒，停止任务.");
                    return;
                }
                PathManagers.get().mine(s.getBlock());
                info("用 baritone (精准采集) 挖掘潜影盒.");
            } else if (storageTick > 600) {
                stopTask("baritone 挖掘潜影盒超时，停止任务.");
            }
            return;
        }
        // 挖完：等拾取 (用 baritone 走位到掉落物，走过去自然拾取；手动走路会被 releaseForward 每 tick 取消)
        if (pickupWaitStart == -1) {
            pickupWaitStart = storageTick;
            PathManagers.get().stop(); // 停止挖掘路径，改走去拾取 (只停一次，避免每 tick 杀 baritone 导致走不动)
        }
        if (countBoxes() >= boxBaseline) {
            // 优先恢复到取盒时的玩家槽位 (拾取通常落到取盒时的空槽)，避免背包有多个潜影盒时还错盒子
            if (pickInvSlot >= 0 && pickInvSlot < 36 && isShulkerBox(mc.player.getInventory().getMainStacks().get(pickInvSlot))) {
                boxInventorySlot = pickInvSlot;
            } else {
                boxInventorySlot = findBackpackShulkerBox();
            }
            if (ecPos == null) {
                // 快捷路径 (未放末影箱)：会话直接结束，无需挖末影箱
                afterStorageSession();
                return;
            }
            storagePhase = (dumpNeeded || resupplyNeeded) ? StoragePhase.OPEN_EC : StoragePhase.MINE_EC;
            storageTick = 0;
            pickupWaitStart = -1;
            return;
        }
        if (storageTick > pickupWaitStart + 200) {
            warning("潜影盒掉落未拾取，继续任务.");
            boxInventorySlot = -1;
            boxFromInventory = false;
            if (ecPos == null) {
                // 快捷路径：未放末影箱，直接结束会话
                afterStorageSession();
                return;
            }
            storagePhase = (dumpNeeded || resupplyNeeded) ? StoragePhase.OPEN_EC : StoragePhase.MINE_EC;
            storageTick = 0;
            pickupWaitStart = -1;
            return;
        }
        ItemEntity it = findItemEntityNear(boxPos, 8);
        if (it != null && !PathManagers.get().isPathing()) {
            PathManagers.get().moveTo(it.getBlockPos(), false);
        }
    }

    private void onMineEc() {
        BlockState s = mc.world.getBlockState(ecPos);
        if (isEnderChestBlock(s)) {
            if (storageTick == 1) {
                pickupWaitStart = -1;
                PathManagers.get().stop();
                // 必须带精准采集工具，否则只会掉黑曜石
                if (findSilkTouchSlot() == -1) {
                    stopTask("需要精准采集工具才能回收末影箱，停止任务.");
                    return;
                }
                PathManagers.get().mine(Blocks.ENDER_CHEST);
                info("用 baritone (精准采集) 挖掘末影箱.");
            } else if (storageTick > 600) {
                stopTask("baritone 挖掘末影箱超时，停止任务.");
            }
            return;
        }
        // 挖完：等拾取 (用 baritone 走位到掉落物，走过去自然拾取)
        if (pickupWaitStart == -1) {
            pickupWaitStart = storageTick;
            PathManagers.get().stop(); // 停止挖掘路径，改走去拾取 (只停一次，避免每 tick 杀 baritone 导致走不动)
        }
        if (countItem(Items.ENDER_CHEST) >= ecBaseline) {
            afterStorageSession();
            return;
        }
        if (storageTick > pickupWaitStart + 400) {
            warning("末影箱掉落未拾取 (挖末影箱需要精准采集)，继续任务.");
            afterStorageSession();
            return;
        }
        ItemEntity it = findItemEntityNear(ecPos, 8);
        if (it != null && !PathManagers.get().isPathing()) {
            PathManagers.get().moveTo(it.getBlockPos(), false);
        }
    }

    // 存储会话结束后的去向：
    // 船旁 (捡到鞘翅时) 触发的会话 → 先走回降落点，由 finishShip 复查后再起飞，绝不原地起飞撞船；
    // 降落点/起飞前触发的会话 → 原地起飞 (旧行为，起飞方向已修正)
    private void afterStorageSession() {
        storagePhase = StoragePhase.NONE;
        storageTick = 0;
        pickupWaitStart = -1;
        if (state == State.COLLECTING && collectStep == CollectStep.EQUIP) {
            info("船旁存储会话结束，先走回降落点再起飞.");
            advance(CollectStep.EXIT_P1);
        } else {
            relaunchAfterShip();
        }
    }

    // ========== 存储会话工具 ==========
    private void click(SlotActionType type, int slotId) {
        var sh = mc.player.currentScreenHandler;
        if (sh == null) return;
        mc.interactionManager.clickSlot(sh.syncId, slotId, 0, type, mc.player);
    }

    private void interactAt(BlockPos pos) {
        interactAt(pos, Direction.UP);
    }

    private void interactAt(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3d.ofCenter(pos), face, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    // 面 ID（FacingLogic.bestFaceIndex 返回值，与 Direction.ID 顺序一致）→ Direction
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

    // 把视角转向目标方块面并同步给服务器 (反作弊要求视线与交互点一致，否则放置会被拦截)
    private void faceBlockForPlace(BlockPos target, Direction face) {
        double cx = target.getX() + 0.5 + face.getOffsetX() * 0.5;
        double cy = target.getY() + 0.5 + face.getOffsetY() * 0.5;
        double cz = target.getZ() + 0.5 + face.getOffsetZ() * 0.5;
        double dx = cx - mc.player.getX();
        double dy = cy - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = cz - mc.player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        // 显式发送视角包，让服务器在处理交互包前先看到新视角
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    // 对准潜影盒中心：交互点不手工指定，交给客户端准星射线 (mc.hitResult) 生成，
    // 保证与视线完全一致 (反作弊要求视线与交互点一致，顶面被盖住时准星会命中盒子侧面，同样能打开)
    private void lookAtBoxCenter() {
        double cx = boxPos.getX() + 0.5;
        double cy = boxPos.getY() + 0.5;
        double cz = boxPos.getZ() + 0.5;
        double dx = cx - mc.player.getX();
        double dy = cy - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = cz - mc.player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        // 显式发送视角包，让服务器在处理交互包前先看到新视角
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    // 客户端准星 (crosshair) 是否已命中潜影盒 (盒子需要在玩家视线内且未被遮挡)
    private boolean isLookingAtBox() {
        return mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getBlockPos().equals(boxPos);
    }

    // 玩家是否正站在容器方块上 (脚所在位置上方就是该方块，即人踩在容器顶面)
    private boolean isStandingOnBlock(BlockPos pos) {
        if (pos == null) return false;
        BlockPos feet = mc.player.getBlockPos();
        return feet.getX() == pos.getX() && feet.getY() == pos.getY() + 1 && feet.getZ() == pos.getZ();
    }

    // 找一个离开容器方块后的落脚点 (容器四周 3x3 范围内、非容器所在格的空气格)
    private BlockPos findStandOffSpot(BlockPos pos) {
        if (pos == null) return null;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue; // 跳过容器所在格
                m.set(pos.getX() + dx, pos.getY() + 1, pos.getZ() + dz);
                if (mc.world.getBlockState(m).isReplaceable()
                    && !mc.world.getBlockState(m.down()).isReplaceable()) {
                    return m.toImmutable();
                }
            }
        }
        return null;
    }

    private boolean isContainerOpen() {
        // 按状态判断：当前开的不是玩家自己的物品栏界面，就是打开了某个容器 (末影箱/潜影盒)
        var sh = mc.player.currentScreenHandler;
        return sh != null && !(sh instanceof PlayerScreenHandler);
    }

    // 当前容器界面行数：末影箱 3 行 (GenericContainerScreenHandler)，潜影盒也是 3 行 27 格 (ShulkerBoxScreenHandler)
    private int currentRows() {
        var sh = mc.player.currentScreenHandler;
        if (sh instanceof GenericContainerScreenHandler g) return g.getRows();
        if (sh instanceof ShulkerBoxScreenHandler) return 3;
        return -1;
    }

    // ===== 容器屏幕槽位换算 (行数感知)：容器 0..rows*9-1，玩家主栏 rows*9..rows*9+26，热栏 rows*9+27..rows*9+35 =====
    private int containerLast(int rows) { return rows * 9 - 1; }
    private int playerFirst(int rows) { return rows * 9; }
    private int playerLast(int rows) { return rows * 9 + 35; }
    private int mainLast(int rows) { return rows * 9 + 26; }
    private int hotbarScreen(int rows, int invSlot) { return rows * 9 + 27 + invSlot; }

    // 把物品移到热栏并选中，同时把选中槽同步给服务器 (不同步的话服务器放置时用错物品)
    private int ensureItemSelected(int invSlot) {
        if (invSlot <= 8) {
            InvUtils.swap(invSlot, false); // 已在热栏：直接选中并同步
            return invSlot;
        }
        InvUtils.move().from(invSlot).toHotbar(0); // 在背包：移到热栏 0
        InvUtils.swap(0, false);
        return 0;
    }

    // ===== 点击握手执行器 =====
    // 客户端 clickSlot 只发包，界面槽位/光标状态要等服务器回包 (1~5 tick) 才更新；
    // 因此所有搬运点击都进入队列，等光标满足条件后再发下一步，杜绝"读过期状态"导致的误判。
    private void execMoves() {
        if (moveState == 0) {
            if (moveQueue.isEmpty()) return;
            MoveOp op = moveQueue.poll();
            moveSrc = op.source;
            moveDst = op.target;
            if (moveSrc == -1) {
                click(PICKUP, moveDst);        // 直接把光标放下到目标槽
                moveState = 2;
            } else {
                click(PICKUP, moveSrc);        // 先拾取
                moveState = moveDst == -1 ? 3 : 1;
            }
            return;
        }
        var sh = mc.player.currentScreenHandler;
        if (sh == null) return;
        ItemStack cursor = sh.getCursorStack();
        if (moveState == 3) {
            if (!cursor.isEmpty()) moveState = 0;          // 拾取保留完成 (光标持有物品)
        } else if (moveState == 1) {
            if (!cursor.isEmpty()) {                       // 光标拿到物品 → 放下到目标槽
                click(PICKUP, moveDst);
                moveState = 2;
            }
        } else if (moveState == 2) {
            if (cursor.isEmpty()) moveState = 0;           // 放下完成
        }
    }

    private boolean moveIdle() {
        return moveState == 0 && moveQueue.isEmpty();
    }

    private int invSlotToScreen(int rows, int invSlot) {
        return invSlot <= 8 ? hotbarScreen(rows, invSlot) : rows * 9 + (invSlot - 9);
    }

    private int firstEmptyContainerSlot(ScreenHandler sh, int rows) {
        for (int i = 0; i <= containerLast(rows); i++) {
            if (sh.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    private int firstEmptyPlayerScreenSlot(ScreenHandler sh, int rows) {
        for (int i = playerFirst(rows); i <= playerLast(rows); i++) {
            if (sh.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    private int findMergeSlotInPlayer(ScreenHandler sh, int rows, Item item) {
        for (int i = playerFirst(rows); i <= playerLast(rows); i++) {
            ItemStack st = sh.getSlot(i).getStack();
            if (st.getItem() == item && st.getCount() < st.getMaxCount()) return i;
        }
        return -1;
    }

    private int findEcBoxWithSpace(ScreenHandler sh, int rows) {
        for (int i = 0; i <= containerLast(rows); i++) {
            ItemStack st = sh.getSlot(i).getStack();
            if (isShulkerBox(st) && shulkerUsedSlots(st) < 27) return i;
        }
        return -1;
    }

    private int findBackpackBoxWithSpace() {
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getMainStacks().get(i);
            if (isShulkerBox(st) && shulkerUsedSlots(st) < 27) return i;
        }
        return -1;
    }

    // 找背包中可用于"存鞘翅"的盒 (修复: 绝不用补给盒)：
    // ① 优先选盒内已有鞘翅且有空间 ② 其次选完全空的盒 ③ 跳过装其他物资的盒 (烟花/食物/图腾 = 补给盒)
    private int findBackpackElytraBox() {
        int emptyBox = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getMainStacks().get(i);
            if (!isShulkerBox(st)) continue;
            Set<Item> items = shulkerItems(st);
            if (items.contains(Items.ELYTRA)) {
                if (shulkerUsedSlots(st) < 27) return i; // 鞘翅盒有空间 → 直接用
                continue;                                // 鞘翅满盒 → 不能用
            }
            if (items.isEmpty() && emptyBox == -1) emptyBox = i; // 完全空盒 → 备用
            // 含其他物资(烟花/食物/图腾等) = 补给盒 → 跳过，绝不把鞘翅塞进补给盒
        }
        return emptyBox;
    }

    // 找背包里未穿戴的鞘翅槽位 (屏幕坐标)
    private int findUnwornElytraSlot(ScreenHandler sh, int rows) {
        for (int i = playerFirst(rows); i <= playerLast(rows); i++) {
            if (sh.getSlot(i).getStack().getItem() == Items.ELYTRA) return i;
        }
        return -1;
    }

    // 找可腾进盒子的杂物槽位 (非物资/非鞘翅/非潜影盒/非末影箱/非精准采集工具)
    private int findJunkSlotInPlayer(ScreenHandler sh, int rows, List<SupplyRule> rules) {
        Set<Item> keep = new HashSet<>();
        for (SupplyRule r : rules) keep.add(r.item);
        keep.add(Items.ELYTRA);
        keep.add(Items.ENDER_CHEST);
        int silkSlot = findSilkTouchSlot();
        for (int i = playerFirst(rows); i <= playerLast(rows); i++) {
            ItemStack st = sh.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            if (isShulkerBox(st)) continue;
            if (keep.contains(st.getItem())) continue;
            if (silkSlot != -1 && i == invSlotToScreen(rows, silkSlot)) continue;
            return i;
        }
        return -1;
    }

    private boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isShulkerBlock(BlockState state) {
        return state.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isEnderChestBlock(BlockState state) {
        return state.getBlock() == Blocks.ENDER_CHEST;
    }

    private int shulkerUsedSlots(ItemStack box) {
        var container = box.get(DataComponentTypes.CONTAINER);
        if (container == null) return 0;
        return (int) container.streamNonEmpty().count();
    }

    private Set<Item> shulkerItems(ItemStack box) {
        Set<Item> out = new HashSet<>();
        var container = box.get(DataComponentTypes.CONTAINER);
        if (container == null) return out;
        for (ItemStack st : (Iterable<ItemStack>) container.streamNonEmpty()::iterator) {
            out.add(st.getItem());
        }
        return out;
    }

    // 找可放置方块的位置列表 (按距离排序)：格子可替换(空气) + 下方实心 → 对着下方方块上方放置 (末影箱用)
    private List<BlockPos> findPlaceSpots(BlockPos exclude) {
        return findPlaceSpotsAt(mc.player.getBlockPos(), exclude);
    }

    private List<BlockPos> findPlaceSpotsAt(BlockPos center, BlockPos exclude) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (exclude != null && Math.abs(m.getX() - exclude.getX()) <= 1
                        && Math.abs(m.getZ() - exclude.getZ()) <= 1 && Math.abs(m.getY() - exclude.getY()) <= 1) {
                        continue;
                    }
                    BlockState s = mc.world.getBlockState(m);
                    if (!s.isReplaceable()) continue;
                    if (!mc.world.getBlockState(m.down()).isSolidBlock(mc.world, m.down())) continue;
                    out.add(m.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
        return out;
    }

    // 潜影盒放置位置：格子是空气 + 上方有实心方块 → 对着上方方块放置 (挂在它下方)
    private List<BlockPos> findBoxPlaceSpots(BlockPos exclude) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockPos center = mc.player.getBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (exclude != null && Math.abs(m.getX() - exclude.getX()) <= 1
                        && Math.abs(m.getZ() - exclude.getZ()) <= 1 && Math.abs(m.getY() - exclude.getY()) <= 1) {
                        continue;
                    }
                    if (!mc.world.getBlockState(m).isReplaceable()) continue;                // 该格必须是空气
                    if (!mc.world.getBlockState(m.up()).isSolidBlock(mc.world, m.up())) continue; // 上方必须有实心方块
                    out.add(m.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
        return out;
    }

    // 潜影盒地板放置位置 (后备)：格子是空气 + 下方有实心方块 + 顶面敞开 → 对着下方方块顶面放置 (盒子放在它上面，顶面不被盖住)
    private List<BlockPos> findBoxFloorSpots(BlockPos exclude) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockPos center = mc.player.getBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (exclude != null && Math.abs(m.getX() - exclude.getX()) <= 1
                        && Math.abs(m.getZ() - exclude.getZ()) <= 1 && Math.abs(m.getY() - exclude.getY()) <= 1) {
                        continue;
                    }
                    if (!mc.world.getBlockState(m).isReplaceable()) continue;                // 该格必须是空气
                    if (!mc.world.getBlockState(m.down()).isSolidBlock(mc.world, m.down())) continue; // 下方必须有实心方块 (放在它上面)
                    if (!mc.world.getBlockState(m.up()).isReplaceable()) continue;            // 顶面必须敞开 (不被盖住，好打开)
                    out.add(m.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
        return out;
    }

    private ItemEntity findItemEntityNear(BlockPos pos, double radius) {
        Box box = new Box(pos).expand(radius);
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (var entity : mc.world.getOtherEntities(null, box, e -> true)) {
            if (entity instanceof ItemEntity item) {
                double d = item.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (d < bestD) {
                    bestD = d;
                    best = item;
                }
            }
        }
        return best;
    }

    // ========== 物资 / 背包统计 ==========
    private record SupplyRule(Item item, int min, int batch) {}

    private List<SupplyRule> parseSupplies() {
        List<SupplyRule> out = new ArrayList<>();
        for (String entry : supplies.get()) {
            String[] p = entry.split(";");
            if (p.length < 3) {
                warning("物资配置格式错误: " + entry);
                continue;
            }
            Identifier id = Identifier.tryParse(p[0].trim());
            if (id == null || Registries.ITEM.get(id) == Items.AIR) {
                warning("未知物品: " + p[0]);
                continue;
            }
            try {
                out.add(new SupplyRule(Registries.ITEM.get(id),
                    Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())));
            } catch (NumberFormatException e) {
                warning("物资数量格式错误: " + entry);
            }
        }
        return out;
    }

    private boolean anyDeficit() {
        for (SupplyRule r : parseSupplies()) {
            if (countItem(r.item) < r.min) return true;
        }
        return false;
    }

    // 该物品是否还有缺口 (散放物资/盒子内容判定用；按目标库存 batch 计算，拿到 batch 为止)
    private boolean needsItem(Item item) {
        for (SupplyRule r : parseSupplies()) {
            if (r.item == item && countItem(item) < r.batch) return true;
        }
        return false;
    }

    private int countItem(Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getMainStacks().get(i);
            if (st.getItem() == item) n += st.getCount();
        }
        return n;
    }

    private int countElytraMain() {
        return countItem(Items.ELYTRA);
    }

    private int countBoxes() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (isShulkerBox(mc.player.getInventory().getMainStacks().get(i))) n++;
        }
        return n;
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getMainStacks().get(i).getItem() == item) return i;
        }
        return -1;
    }

    // 找带精准采集的工具槽位 (回收潜影盒/末影箱必须用，否则盒子消失/只掉黑曜石)
    private int findSilkTouchSlot() {
        var reg = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var entry = reg.getEntry(reg.get(Enchantments.SILK_TOUCH));
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getMainStacks().get(i);
            if (EnchantmentHelper.getLevel(entry, st) > 0) return i;
        }
        return -1;
    }

    // 找背包里的潜影盒槽位 (挖回盒子后重新定位，拾取槽位不一定等于原槽位)
    private int findBackpackShulkerBox() {
        for (int i = 0; i < 36; i++) {
            if (isShulkerBox(mc.player.getInventory().getMainStacks().get(i))) return i;
        }
        return -1;
    }

    private int freeSlots() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getMainStacks().get(i).isEmpty()) n++;
        }
        return n;
    }

    private boolean reached(BlockPos pos, double tolerance) {
        if (stateTick < 6) return false;
        if (PathManagers.get().isPathing()) return false;
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= tolerance * tolerance;
    }
    // ========== 原版 1.21.4 末地高度 / 生物群系 (精确复刻) ==========
    // DensityFunctionTypes.EndIslands.getHeightValue，输入为 blockX/8、blockZ/8
    private float endIslandHeightValue(int x, int z) {
        int i = x / 2;
        int j = z / 2;
        int k = x % 2;
        int l = z % 2;
        // 原版用 int 平方会溢出 (MC-159283)，用 double 修复
        float f = 100.0F - (float) Math.sqrt((double) x * x + (double) z * z) * 8.0F;
        f = clampIsland(f);

        for (int rx = -12; rx <= 12; rx++) {
            for (int rz = -12; rz <= 12; rz++) {
                long k1 = (long) (i + rx);
                long l1 = (long) (j + rz);
                if (islandNoise != null && k1 * k1 + l1 * l1 > 4096L && islandNoise.sample2D((double) k1, (double) l1) < -0.9F) {
                    float f1 = (Math.abs((float) k1) * 3439.0F + Math.abs((float) l1) * 147.0F) % 13.0F + 9.0F;
                    float f2 = (float) (k - rx * 2);
                    float f3 = (float) (l - rz * 2);
                    float f4 = 100.0F - (float) Math.sqrt(f2 * f2 + f3 * f3) * f1;
                    f4 = clampIsland(f4);
                    f = Math.max(f, f4);
                }
            }
        }
        return f;
    }

    private static float clampIsland(float value) {
        if (value < -100.0F) return -100.0F;
        return Math.min(value, 80.0F);
    }

    // end_islands 密度值 (与 erosion 相同，2D 且与 Y 无关)
    private double endIslandDensity(int blockX, int blockZ) {
        return (endIslandHeightValue(blockX / 8, blockZ / 8) - 8.0) / 128.0;
    }

    // 原版 TheEndBiomeSource.getNoiseBiome: 中心 64 section 内是 the_end (无末地城)，
    // 其余 erosion ≥ -0.0625 为 highlands/midlands (可生成末地城)
    private boolean isEndCityBiome(int chunkX, int chunkZ) {
        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        long sx = blockX >> 4;
        long sz = blockZ >> 4;
        if (sx * sx + sz * sz <= 4096L) return false;
        return endIslandDensity(blockX, blockZ) >= -0.0625;
    }

    // 原版高度判定：用游戏里的真实高度图 (区块已加载) 检查城市 5x5 区域最低高度 ≥ 60
    private boolean cityTerrainHigh() {
        int chunkX = current.chunk.getX();
        int chunkZ = current.chunk.getZ();

        ChunkRand r = new ChunkRand();
        r.setSeed(worldSeed);
        long a = r.nextLong();
        long b = r.nextLong();
        r.setSeed((long) chunkX * a ^ (long) chunkZ * b ^ worldSeed);
        BlockRotation rotation = BlockRotation.getRandom(r);

        int xOff = 5, zOff = 5;
        if (rotation == BlockRotation.CLOCKWISE_90) {
            xOff = -5;
        } else if (rotation == BlockRotation.CLOCKWISE_180) {
            xOff = -5;
            zOff = -5;
        } else if (rotation == BlockRotation.COUNTERCLOCKWISE_90) {
            zOff = -5;
        }

        int posX = (chunkX << 4) + 7;
        int posZ = (chunkZ << 4) + 7;

        // 4 根采样柱的区块必须全部加载，否则 getTopY 返回 0 会误杀真城
        int[][] cols = {
            {posX, posZ}, {posX, posZ + zOff}, {posX + xOff, posZ}, {posX + xOff, posZ + zOff}
        };
        for (int[] c : cols) {
            if (!mc.world.isChunkLoaded(c[0] >> 4, c[1] >> 4)) {
                return true; // 未加载：不判，交给龙头扫描
            }
        }

        int h1 = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cols[0][0], cols[0][1]);
        int h2 = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cols[1][0], cols[1][1]);
        int h3 = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cols[2][0], cols[2][1]);
        int h4 = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cols[3][0], cols[3][1]);
        int min = Math.min(Math.min(h1, h2), Math.min(h3, h4));

        if (debugSetting.get()) {
            debugLog("city=(" + chunkX + "," + chunkZ + ") rotation=" + rotation + " min=" + min);
            debugColumn(posX, posZ, h1);
            debugColumn(posX, posZ + zOff, h2);
            debugColumn(posX + xOff, posZ, h3);
            debugColumn(posX + xOff, posZ + zOff, h4);
        }

        return min >= 60;
    }

    // 调试日志: 输出某列的真实高度 + 离线公式计算值 (用于校准)
    private void debugColumn(int x, int z, int realHeight) {
        float rawF = endIslandHeightValue(x / 8, z / 8);
        double e = (rawF - 8.0) / 128.0;
        int vanilla = vanillaWorldSurfaceWg(x, z);
        debugLog("col(" + x + "," + z + ") real=" + realHeight + " vanilla=" + vanilla + " E=" + String.format("%.4f", e) + " rawF=" + String.format("%.1f", rawF));
    }

    // ========== 原版 1.21.4 末地高度链精确复刻 (离线预测 WORLD_SURFACE_WG) ==========
    // finalDensity = squeeze(0.64 * interpolated(blendDensity(slideEnd(endIslands + base3d))))
    // 与 NoiseChunkGenerator.sampleHeightmap 一致: 4x4x4 单元角点采样 + 三线性插值

    // 与 vanilla getHeight(x,z,WORLD_SURFACE_WG) 一致: 从 y=255 向下找第一个实心块, 返回 y+1
    private int vanillaWorldSurfaceWg(int x, int z) {
        if (base3dNoise == null) return 0;
        int x0 = Math.floorDiv(x, 4) * 4;
        int z0 = Math.floorDiv(z, 4) * 4;
        double xf = (x - x0) / 4.0;
        double zf = (z - z0) / 4.0;
        double e00 = (endIslandHeightValue(x0 / 8, z0 / 8) - 8.0) / 128.0;
        double e10 = (endIslandHeightValue((x0 + 4) / 8, z0 / 8) - 8.0) / 128.0;
        double e01 = (endIslandHeightValue(x0 / 8, (z0 + 4) / 8) - 8.0) / 128.0;
        double e11 = (endIslandHeightValue((x0 + 4) / 8, (z0 + 4) / 8) - 8.0) / 128.0;
        double n00 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0, 256, z0));
        double n10 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0 + 4, 256, z0));
        double n01 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0, 256, z0 + 4));
        double n11 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0 + 4, 256, z0 + 4));
        for (int cell = 63; cell >= 0; cell--) {
            int yb = cell * 4;
            double b00 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0, yb, z0));
            double b10 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0 + 4, yb, z0));
            double b01 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0, yb, z0 + 4));
            double b11 = base3dNoise.sample(new DensityFunction.UnblendedNoisePos(x0 + 4, yb, z0 + 4));
            double c000 = endSlide(e00 + b00, yb);
            double c010 = endSlide(e00 + n00, yb + 4);
            double c100 = endSlide(e10 + b10, yb);
            double c110 = endSlide(e10 + n10, yb + 4);
            double c001 = endSlide(e01 + b01, yb);
            double c011 = endSlide(e01 + n01, yb + 4);
            double c101 = endSlide(e11 + b11, yb);
            double c111 = endSlide(e11 + n11, yb + 4);
            for (int y = yb + 3; y >= yb; y--) {
                double yf = (y - yb) / 4.0;
                double x0z0 = c000 + yf * (c010 - c000);
                double x1z0 = c100 + yf * (c110 - c100);
                double x0z1 = c001 + yf * (c011 - c001);
                double x1z1 = c101 + yf * (c111 - c101);
                double z0v = x0z0 + xf * (x1z0 - x0z0);
                double z1v = x0z1 + xf * (x1z1 - x0z1);
                double v = z0v + zf * (z1v - z0v);
                double d = 0.64 * v;
                double c = d < -1.0 ? -1.0 : (d > 1.0 ? 1.0 : d);
                if (c / 2.0 - c * c * c / 24.0 > 0.0) return y + 1;
            }
            n00 = b00; n10 = b10; n01 = b01; n11 = b11;
        }
        return 0;
    }

    private static double endSlide(double f, int y) {
        double top = y <= 56 ? 1.0 : (y >= 312 ? 0.0 : (312.0 - y) / 256.0);
        double middle = -23.4375 + top * (f + 23.4375);
        double bottom = y <= 4 ? 0.0 : (y >= 32 ? 1.0 : (y - 4.0) / 28.0);
        return -0.234375 + bottom * (middle + 0.234375);
    }

    // 原版 EndCityStructure 判定: 按 rotation 偏移的 4 根柱子 WORLD_SURFACE_WG 最低 < 60 就不生成
    // 原版精确阈值为 getHeightInGround(=getHeight-1) >= 60 即 getHeight >= 61;
    // 实测离线预测在边界有 ±1 格误差, 阈值取 60 (低 1 格) 保证不漏真城, 误报假城由龙头扫描兜底
    private boolean vanillaCityTerrainOk(int chunkX, int chunkZ) {
        ChunkRand r = new ChunkRand();
        r.setSeed(worldSeed);
        long a = r.nextLong();
        long b = r.nextLong();
        r.setSeed((long) chunkX * a ^ (long) chunkZ * b ^ worldSeed);
        BlockRotation rotation = BlockRotation.getRandom(r);
        int xOff = 5, zOff = 5;
        if (rotation == BlockRotation.CLOCKWISE_90) {
            xOff = -5;
        } else if (rotation == BlockRotation.CLOCKWISE_180) {
            xOff = -5;
            zOff = -5;
        } else if (rotation == BlockRotation.COUNTERCLOCKWISE_90) {
            zOff = -5;
        }
        int posX = (chunkX << 4) + 7;
        int posZ = (chunkZ << 4) + 7;
        int min = vanillaWorldSurfaceWg(posX, posZ);
        min = Math.min(min, vanillaWorldSurfaceWg(posX, posZ + zOff));
        min = Math.min(min, vanillaWorldSurfaceWg(posX + xOff, posZ));
        min = Math.min(min, vanillaWorldSurfaceWg(posX + xOff, posZ + zOff));
        if (debugSetting.get()) {
            debugLog("city=(" + chunkX + "," + chunkZ + ") rotation=" + rotation + " predMin=" + min);
        }
        return min >= 60;
    }

    // ========== 调试日志 (写入 .minecraft/elytra-collector-debug.log) ==========
    private final Object debugLock = new Object();

    private java.nio.file.Path debugFilePath() {
        return mc.runDirectory.toPath().resolve("elytra-collector-debug.log");
    }

    private void debugLog(String msg) {
        if (!debugSetting.get()) return;
        try {
            String line = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " " + msg + System.lineSeparator();
            synchronized (debugLock) {
                java.nio.file.Files.writeString(debugFilePath(), line,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            error("调试日志写入失败: " + e.getMessage());
        }
    }

    // ========== 黑名单 ==========
    private String blacklistKey(BlockPos head) {
        return head.getX() + "," + head.getZ();
    }

    private boolean isBlacklisted(BlockPos head) {
        synchronized (blacklist.get()) {
            for (String entry : blacklist.get()) {
                String[] parts = entry.split(",");
                if (parts.length < 2) continue;
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int z = Integer.parseInt(parts[1].trim());
                    int dx = x - head.getX();
                    int dz = z - head.getZ();
                    // 种子坐标有 ±1 误差，用 2 格容差匹配
                    if (dx * dx + dz * dz <= 4) return true;
                } catch (NumberFormatException ignored) {}
            }
        }
        return false;
    }

    private void addBlacklist(BlockPos head) {
        synchronized (blacklist.get()) {
            String key = blacklistKey(head);
            if (!blacklist.get().contains(key)) blacklist.get().add(key);
        }
    }

    // ========== 打展示框 / 捡鞘翅 / 穿鞘翅 ==========
    private void hitItemFrame() {
        ItemFrameEntity frame = getNearestItemFrame(killAuraReach.get());
        if (frame == null) {
            if (waypoints != null) faceDirection(waypoints.facing);
            frame = getNearestItemFrame(killAuraReach.get() + 2);
        }
        if (frame != null) {
            mc.interactionManager.attackEntity(mc.player, frame);
            info("攻击展示框.");
        } else {
            info("未找到展示框.");
        }
    }

    private void pickUpElytra() {
        ItemEntity item = findDroppedElytra();
        if (item != null) {
            PathManagers.get().moveTo(item.getBlockPos(), false);
        }
    }

    private void equipElytra() {
        if (isWearingFullDurabilityElytra()) return;
        int slot = findFullDurabilityElytraSlot();
        if (slot == -1) return;

        // 用 InvUtils 发点击包换装 (直接改客户端列表会被服务器同步覆盖)
        InvUtils.move().from(slot).toArmor(2);
        info("已穿上鞘翅.");
    }

    private boolean isWearingFullDurabilityElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.getItem() == Items.ELYTRA && chest.getDamage() == 0;
    }

    private void walkTowards(double x, double z) {
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.options.forwardKey.setPressed(true);
    }

    private void useFirework() {
        FindItemResult fw = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!fw.found()) {
            // 物品栏没有烟花：从背包拿一个放到第 2 个槽位 (下次使用)
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (inv.found()) {
                InvUtils.move().from(inv.slot()).toHotbar(1);
                info("物品栏没有烟花，从背包调取放到第 2 个槽位.");
            }
            return;
        }
        if (fw.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        } else {
            InvUtils.swap(fw.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
    }

    // ========== Helpers ==========
    private void releaseForward() {
        mc.options.forwardKey.setPressed(false);
    }

    private void faceDirection(Direction d) {
        float yaw = switch (d) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
        rotateFastTo(yaw, mc.player.getPitch());
    }

    private float yawTowards(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private double horizontalDistance(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void rotateTo(float targetYaw, float targetPitch) {
        mc.player.setYaw(stepAngle(mc.player.getYaw(), targetYaw, yawSpeed.get().floatValue()));
        mc.player.setPitch(stepAngle(mc.player.getPitch(), targetPitch, pitchSpeed.get().floatValue()));
    }

    private void rotateFastTo(float targetYaw, float targetPitch) {
        float speed = yawSpeed.get().floatValue();
        mc.player.setYaw(stepAngle(mc.player.getYaw(), targetYaw, speed));
        mc.player.setPitch(stepAngle(mc.player.getPitch(), targetPitch, speed));
    }

    private static float stepAngle(float current, float target, float speed) {
        float d = target - current;
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        if (Math.abs(d) <= speed) return target;
        return current + Math.signum(d) * speed;
    }

    private static boolean isDragonHead(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).getPath();
        return id.equals("dragon_head") || id.equals("dragon_wall_head");
    }

    private DragonHead findDragonHead(BlockPos center, int radius, int yRange) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -yRange; dy <= yRange; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = mc.world.getBlockState(pos);
                    if (isDragonHead(s)) {
                        Direction facing = s.contains(Properties.FACING) ? s.get(Properties.FACING) : Direction.NORTH;
                        return new DragonHead(pos.toImmutable(), facing);
                    }
                }
            }
        }
        return null;
    }

    private ItemFrameEntity getNearestItemFrame(double reach) {
        Box box = mc.player.getBoundingBox().expand(reach);
        ItemFrameEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (var entity : mc.world.getOtherEntities(mc.player, box, e -> true)) {
            if (entity instanceof ItemFrameEntity frame) {
                double d = frame.squaredDistanceTo(mc.player);
                if (d < bestD) { bestD = d; best = frame; }
            }
        }
        return best;
    }

    // 空中龙头确认后：只查 p3 这个精确坐标上有没有"挂在墙上的"展示框实体 (不扫周边范围，防相邻船误判)
    private ItemFrameEntity findFrameAt(BlockPos pos) {
        for (var entity : mc.world.getOtherEntities(null, new Box(pos), e -> e instanceof ItemFrameEntity)) {
            if (entity instanceof ItemFrameEntity frame) {
                return frame;
            }
        }
        return null;
    }

    // 只检测鞘翅正前方固定一格有没有潜影贝 (不扫范围)：潜影贝传走到别处就不再威胁，直接推进
    private boolean hasShulkerAtGuard() {
        if (waypoints == null) return false;
        Box box = new Box(waypoints.guard);
        for (var entity : mc.world.getOtherEntities(null, box, e -> e.getType() == EntityType.SHULKER)) {
            if (entity.getBlockPos().equals(waypoints.guard)) return true;
        }
        return false;
    }

    private ItemEntity findDroppedElytra() {
        Box box = mc.player.getBoundingBox().expand(32);
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (var entity : mc.world.getOtherEntities(mc.player, box, e -> true)) {
            if (entity instanceof ItemEntity item && item.getStack().getItem() == Items.ELYTRA) {
                double d = item.squaredDistanceTo(mc.player);
                if (d < bestD) { bestD = d; best = item; }
            }
        }
        return best;
    }

    private boolean hasElytraInInventory() {
        return findFullDurabilityElytraSlot() != -1 || findAnyElytraSlot() != -1;
    }

    private boolean hasElytraEquipped() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private int findAnyElytraSlot() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getMainStacks().get(i).getItem() == Items.ELYTRA) return i;
        }
        return -1;
    }

    private int findFullDurabilityElytraSlot() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getMainStacks().get(i);
            if (s.getItem() == Items.ELYTRA && s.getDamage() == 0) return i;
        }
        return -1;
    }

    private long parseSeed(String s) {
        if (s == null || s.equals("0") || s.isEmpty()) {
            if (mc.getServer() != null) return mc.getServer().getOverworld().getSeed();
            return 0L;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return (long) s.hashCode();
        }
    }

    /** V4.24: 方向英文枚举 → 中文（前端全汉化） */
    private static String directionLabel(Direction d) {
        return switch (d) {
            case NORTH -> "北";
            case SOUTH -> "南";
            case EAST -> "东";
            case WEST -> "西";
            default -> d.toString();
        };
    }

    // ========== 数据类 ==========
    private record DragonHead(BlockPos pos, Direction facing) {}

    private record ShipTarget(CPos chunk, BlockPos headPos, Direction facing) {}

    private record ShipWaypoints(BlockPos landing, BlockPos p1, BlockPos p2, BlockPos p3, BlockPos guard, Direction facing) {
        static ShipWaypoints from(BlockPos head, Direction facing) {
            return new ShipWaypoints(
                waypoint(head, facing, 7, 0, 4),
                waypoint(head, facing, 23, -1, -1),
                waypoint(head, facing, 9, -1, -4),
                waypoint(head, facing, 7, 0, -3),
                // 潜影贝固定坐标 = 展示框 (p3) 正前方 1 格、低 1 格 (结构 SHIP_SENTRY_1)
                waypoint(head, facing, 8, 0, -4),
                facing
            );
        }

        static BlockPos waypoint(BlockPos head, Direction facing, int behind, int left, int dy) {
            return head.offset(facing.getOpposite(), behind)
                       .offset(facing.rotateYCounterclockwise(), left)
                       .offset(Direction.UP, dy);
        }
    }
}
