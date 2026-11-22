
package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.elytra.pure.EndLocator;
import com.fo.addon.pathing.PathManagers;
import com.seedfinding.mcbiome.source.BiomeSource;
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
import com.seedfinding.mcterrain.TerrainGenerator;
import com.seedfinding.mcterrain.terrain.EndTerrainGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.BlockView;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.network.packet.Packet;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.world.Heightmap;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.noise.InterpolatedNoiseSampler;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.DataComponentTypes;

public class ElytraCollector
extends Module {
    private static final MCVersion VERSION = MCVersion.v1_21;
    private volatile SimplexNoiseSampler islandNoise;
    private volatile net.minecraft.util.math.noise.InterpolatedNoiseSampler base3dNoise;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgFlight;
    private final Setting<String> seedSetting;
    private final Setting<Integer> searchRange;
    /** 搜索末地环内鞘翅：用 26.3 修复版 cubiomes 原生定位器搜索末地城（适配 Folia 等已修复末地环的服务端） */
    private final Setting<Boolean> ringSearch;
    private final Setting<Integer> minHeight;
    private final Setting<Integer> maxHeight;
    private final Setting<Integer> riseHeight;
    private final Setting<Double> pitchSpeed;
    private final Setting<Double> yawSpeed;
    private final Setting<Double> killAuraReach;
    private final Setting<Double> fireworkInterval;
    private final Setting<List<String>> blacklist;
    private final Setting<List<String>> searchResults;
    private final Setting<Boolean> btnStart;
    private final Setting<Boolean> debugSetting;
    private final SettingGroup sgStorage;
    private final Setting<Integer> freeSlotDump;
    private final Setting<List<String>> supplies;
    private final Setting<Boolean> lowYExit;
    private final Setting<Integer> lowYThreshold;
    // ===== 防卡区块 + 俯仰平滑（内嵌自 Ying 0.7.2 Pitch40Fly，不新增模块）=====
    private final SettingGroup sgAntiChunk;
    private final Setting<Boolean> antiChunkClimb;
    private final Setting<Integer> chunkLookahead;
    private final Setting<Integer> minClimbTicks;
    private final Setting<Integer> resumeMargin;
    private final Setting<Boolean> pitchSmooth;
    private final Setting<Double> pitchRotateUp;
    private final Setting<Double> pitchRotateDown;
    // ===== 绕圈扫图 + 缓降增强（内嵌自 Ying 0.7.2 ScanSweep / ElytraSlowFall）=====
    private final SettingGroup sgHelpers;
    private final Setting<Boolean> sweepScan;
    private final Setting<Integer> sweepCenterX;
    private final Setting<Integer> sweepCenterZ;
    private final Setting<SweepSide> sweepSide;
    private final Setting<Double> sweepOffset;
    private final Setting<Boolean> slowFallBoost;
    // ===== 起跳防卡（内嵌自 Ying 0.7.2 ElytraJump）=====
    private final Setting<Boolean> antiStuckJump;
    private final Setting<Integer> antiStuckInterval;
    private final Setting<Integer> antiStuckOffset;
    private final Setting<Integer> antiStuckResumeTicks;
    // ===== 强制拉升（内嵌自 Ying 0.7.2 PullUp / 界面名 Full Up）=====
    private final Setting<Boolean> pullUp;
    private final Setting<Integer> pullUpDangerY;
    private final Setting<Integer> pullUpTargetY;
    private final Setting<Double> pullUpPitch;
    // ===== 自动补烟花（内嵌自 Jeff Mod Pitch40Util）+ 滑翔失效恢复 =====
    private final Setting<Boolean> autoFireworkBoost;
    private final Setting<Double> fireworkVelocityThreshold;
    private final Setting<Integer> fireworkBoostCooldownTicks;
    private final Setting<Integer> lowHeightRocketGap;
    private State state;
    private CollectStep collectStep;
    private final List<ShipTarget> ships;
    private int targetIndex;
    private volatile Thread searchThread;
    private volatile boolean searchCancelled;
    private volatile int searchGeneration;
    private ShipTarget current;
    private ShipWaypoints waypoints;
    private int stateTick;
    private int lastGotoTick;
    private long worldSeed;
    private boolean climbing;
    private boolean antiClimbActive;
    private int antiClimbTicks;
    private int antiClearStreak;
    private float smoothedPitch = 37.72f;
    private net.minecraft.util.math.BlockPos lastJumpPos;
    private int jumpStuckCount;
    private int antiStuckResume;
    private boolean pullUpActive;
    private int pullUpTick;
    private int pullUpLastFireworkTick;
    private int fireworkBoostCooldown;
    private int glideFailTicks;
    private int glideRecoverTicks;
    private boolean glideRecovering;
    /** 烟花耗尽（剩余 ≤ FUEL_EMPTY_THRESHOLD）后进入补给降落 */
    private boolean fuelEmpty;
    /** 补给落地后补货完，回到原目标继续飞（不重搜列表） */
    private boolean supplyLandingResume;
    /** 补给降落选中的陆地落点 */
    private net.minecraft.util.math.BlockPos supplySpot;
    /** 连续补给仍无烟花的次数，防死循环 */
    private int supplyFailCount;
    private boolean headFound;
    private net.minecraft.util.math.BlockPos exactHead;
    private int nearTicks;
    private int scanAttempts;
    private boolean pullUpSuppressed;
    private final Set<String> sessionSkip;
    private int searchExtends;
    private net.minecraft.util.math.Direction takeoffFacing;
    private boolean takeoffFromP1;
    private int noElytraTicks;
    private boolean frameHadElytra;
    private boolean frameAirChecked;
    private boolean frameAirEmpty;
    private int frameAirScanTicks;
    private boolean pickupSessionDone;
    private boolean firstFireworkDone;
    private int lastFireworkTick;
    private boolean landingRecover;
    private double recoveryTargetY;
    private float recoveryYaw;
    private int recoverStageTick;
    private int recoverCount;
    private StoragePhase storagePhase;
    private int storageTick;
    private int pickupWaitStart;
    private net.minecraft.util.math.BlockPos ecPos;
    private net.minecraft.util.math.BlockPos boxPos;
    private boolean boxFromInventory;
    private int boxInventorySlot;
    private int boxBaseline;
    private int ecBaseline;
    private boolean dumpNeeded;
    private boolean resupplyNeeded;
    private StoragePhase afterClose;
    private int elytraBeforeFrame;
    private List<net.minecraft.util.math.BlockPos> placeCandidates;
    private int placeIndex;
    private int lastPlaceClick;
    private int placeClickAttempts;
    private final ArrayDeque<MoveOp> moveQueue;
    private int moveState;
    private int moveSrc;
    private int moveDst;
    private int pickStep;
    private int pickEcSlot;
    private int pickInvSlot;
    private int fillIndex;
    private int takeRule;
    private int takeSlot;
    private int takeNeed;
    private int takeStuckSlot;
    private int takeStuckTicks;
    private int takeEmptyTicks;
    private final Set<net.minecraft.item.Item> takeFailedItems;
    private int fillStuckSlot;
    private int fillStuckTicks;
    private int openRetry;
    private int boxOpenClickTick;
    private boolean boxPurposeSupply;
    private boolean boxPlaceFloorFallback;
    private int recoverStage;
    private int ecOpenFailCount;
    private int supplyScanStart;
    private boolean supplyPauseDump;
    private boolean resupplyRescan;
    private boolean standingMovedOff;
    private final Object debugLock;

    private static SimplexNoiseSampler createIslandNoise(long worldSeed) {
        JRand rand = new JRand(worldSeed);
        rand.advance(LCG.JAVA.combine(17292L));
        return new SimplexNoiseSampler(rand);
    }

    public ElytraCollector() {
        super(AddonTemplate.CATEGORY, "FO 鞘翅采集", "全自动找末地城鞘翅：种子定位 + 龙头精确定位 + 高度保持飞行 + 缓降 + 寻路 + 打展示框捡鞘翅.");
        this.sgGeneral = this.settings.createGroup("通用");
        this.sgFlight = this.settings.createGroup("飞行");
        this.sgAntiChunk = this.settings.createGroup("防卡区块");
        this.seedSetting = this.sgGeneral.add((((new StringSetting.Builder().name("世界种子")).description("世界种子 (0 = 当前世界). 默认 -7346913998703726680 为本服种子.")).defaultValue("-7346913998703726680")).build());
        this.searchRange = this.sgGeneral.add((((new IntSetting.Builder().name("搜索半径")).description("搜索半径，单位方块 (从玩家位置).")).defaultValue(5000)).range(320, 100000).sliderRange(320, 20000).build());
        this.ringSearch = this.sgGeneral.add((((new BoolSetting.Builder().name("搜索末地环内鞘翅")).description("用 26.3 修复版 cubiomes 原生定位器搜索末地城，末地环 (外岛) 的船也能定位；适配 Folia 等已修复末地环的服务端。关闭则用旧版 1.21 种子推算.")).defaultValue(true)).build());
        this.minHeight = this.sgFlight.add((((new IntSetting.Builder().name("最低高度")).description("飞行中低于此高度时触发爬升；低于此高度才补烟花.")).defaultValue(160)).range(100, 300).sliderRange(120, 260).build());
        this.maxHeight = this.sgFlight.add((((new IntSetting.Builder().name("最高高度")).description("飞行中高于此高度时停止爬升、转为平缓下滑 (与 最低高度 组成滞回区间).")).defaultValue(220)).range(120, 320).sliderRange(140, 300).build());
        this.riseHeight = this.sgFlight.add((((new IntSetting.Builder().name("拉升高度")).description("起飞后抬头爬升到的目标高度 (需高于 最高高度).")).defaultValue(230)).range(200, 320).sliderRange(200, 300).build());
        this.pitchSpeed = this.sgFlight.add(((new DoubleSetting.Builder().name("俯仰速度")).description("飞行 (俯冲巡航) 时上下转动视角 (俯仰角) 的速度 (度/游戏刻).")).defaultValue(10.0).min(1.0).sliderMax(45.0).build());
        this.yawSpeed = this.sgFlight.add(((new DoubleSetting.Builder().name("转向速度")).description("左右转动视角 (偏航角) 及鞘翅缓降的转向速度 (度/游戏刻).")).defaultValue(30.0).min(1.0).sliderMax(90.0).build());
        this.killAuraReach = this.sgFlight.add(((new DoubleSetting.Builder().name("攻击距离")).description("攻击展示框 (展示框) 的判定距离.")).defaultValue(3.0).min(1.0).sliderMax(6.0).build());
        this.fireworkInterval = this.sgFlight.add(((new DoubleSetting.Builder().name("烟花间隔")).description("展开鞘翅后立即使用第一个烟花，之后每隔这么多秒再用一个 (秒).")).defaultValue(2.0).min(0.5).sliderRange(0.5, 10.0).build());
        this.blacklist = this.sgGeneral.add(((((new StringListSetting.Builder().name("黑名单")).description("黑名单：已经去过的船 (x,z)，搜索时会自动跳过. 自动维护.")).defaultValue(new ArrayList())).visible(() -> false)).build());
        this.searchResults = this.sgGeneral.add((((new StringListSetting.Builder().name("搜索结果")).description("上次搜索结果列表 (x,y,z,朝向)，下一次搜索完成后覆盖.")).defaultValue(new ArrayList())).build());
        this.btnStart = this.sgGeneral.add(((((new BoolSetting.Builder().name("开始采集")).description("开始自动采集.")).defaultValue(false)).onChanged(b -> {
            if (b.booleanValue()) {
                this.start();
            }
        })).build());
        this.debugSetting = this.sgGeneral.add((((new BoolSetting.Builder().name("调试日志")).description("输出调试日志 (用于校准高度公式).")).defaultValue(false)).build());
        this.sgStorage = this.settings.createGroup("存储");
        this.freeSlotDump = this.sgStorage.add((((new IntSetting.Builder().name("存盒空格阈值")).description("背包可用空格 ≤ 此值时，拿到鞘翅后自动去末影箱存鞘翅.")).defaultValue(3)).range(0, 36).sliderRange(0, 36).build());
        this.supplies = this.sgStorage.add((((new StringListSetting.Builder().name("物资列表")).description("物资列表，格式: 物品代号;最低值;目标库存 (如 minecraft:firework_rocket;32;256). 背包物资低于最低值时自动从末影箱补货，拿到目标库存为止.")).defaultValue(new ArrayList<String>(List.of("minecraft:firework_rocket;4;256", "minecraft:golden_carrot;8;32")))).build());
        this.lowYExit = this.sgStorage.add((((new BoolSetting.Builder().name("低Y自动退出")).description("Y 低于阈值时自动退出游戏 (断开服务器，防虚空掉物).")).defaultValue(true)).build());
        this.lowYThreshold = this.sgStorage.add((((new IntSetting.Builder().name("低Y阈值")).description("低于此 Y 自动退出游戏.")).defaultValue(20)).range(-64, 100).sliderRange(-64, 100).build());
        // 防卡区块（内嵌自 Ying 0.7.2 Pitch40Fly）
        this.antiChunkClimb = this.sgAntiChunk.add((((new BoolSetting.Builder().name("防卡区块")).description("面向方向前方未加载区块太近时，强制抬头爬升，避免被服务端拉回。")).defaultValue(true)).build());
        this.chunkLookahead = this.sgAntiChunk.add((((new IntSetting.Builder().name("检测距离")).description("面向方向上前方多少格内出现未加载区块就触发爬升 (格).")).defaultValue(32)).range(4, 128).sliderRange(4, 128).build());
        this.minClimbTicks = this.sgAntiChunk.add((((new IntSetting.Builder().name("最短爬升")).description("触发防卡拉升后至少保持多少 游戏刻，防止一抖一抖。")).defaultValue(10)).range(0, 100).sliderRange(0, 100).build());
        this.resumeMargin = this.sgAntiChunk.add((((new IntSetting.Builder().name("恢复余量")).description("未加载区块距离要大于 检测距离+该值 且连续 3 游戏刻 满足，才结束拉升。")).defaultValue(16)).range(0, 64).sliderRange(0, 64).build());
        // 俯仰平滑（内嵌自 Ying 0.7.2 Pitch40Fly）
        this.pitchSmooth = this.sgFlight.add((((new BoolSetting.Builder().name("俯仰平滑")).description("爬升/俯冲时按固定角速度平滑过渡，不硬切 37.72/-54.77。")).defaultValue(true)).build());
        this.pitchRotateUp = this.sgFlight.add((((new DoubleSetting.Builder().name("抬头速度")).description("俯仰平滑开启时，爬升抬头速度 (度/游戏刻).")).defaultValue(5.45)).min(1.0).sliderMax(20.0).build());
        this.pitchRotateDown = this.sgFlight.add((((new DoubleSetting.Builder().name("低头速度")).description("俯仰平滑开启时，俯冲低头速度 (度/游戏刻).")).defaultValue(0.9)).min(0.5).sliderMax(2.0).build());
        // 绕圈扫图（内嵌自 Ying 0.7.2 ScanSweep）
        this.sgHelpers = this.settings.createGroup("辅助");
        this.sweepScan = this.sgHelpers.add((((new BoolSetting.Builder().name("绕圈扫图")).description("未确认龙头时绕中心点扫图：视角始终垂直于「中心点→当前位置」连线。")).defaultValue(false)).build());
        this.sweepCenterX = this.sgHelpers.add((((new IntSetting.Builder().name("扫图中心X")).description("扫图中心点 X 坐标 (方块).")).defaultValue(0)).range(-30000000, 30000000).sliderRange(-10000, 10000).build());
        this.sweepCenterZ = this.sgHelpers.add((((new IntSetting.Builder().name("扫图中心Z")).description("扫图中心点 Z 坐标 (方块).")).defaultValue(0)).range(-30000000, 30000000).sliderRange(-10000, 10000).build());
        this.sweepSide = this.sgHelpers.add((((new EnumSetting.Builder<SweepSide>().name("扫图方向")).description("取垂线的哪个方向：直线的左边 / 右边。")).defaultValue(SweepSide.LEFT)).build());
        this.sweepOffset = this.sgHelpers.add((((new DoubleSetting.Builder().name("扫图偏移")).description("偏移角 (度)：在垂线朝向上再转这么多度，范围 -30 ~ 30。")).defaultValue(0.0)).range(-30.0, 30.0).sliderRange(-30.0, 30.0).decimalPlaces(4).build());
        this.slowFallBoost = this.sgHelpers.add((((new BoolSetting.Builder().name("缓降增强")).description("缓降增强：平视 + 每 2 游戏刻 翻转 偏航角 180° 抵消水平速度。")).defaultValue(true)).build());
        // 起跳防卡（内嵌自 Ying 0.7.2 ElytraJump）
        this.antiStuckJump = this.sgHelpers.add((((new BoolSetting.Builder().name("起跳防卡")).description("起跳防卡：连续两次检测在同一位置时用 寻路绕开。")).defaultValue(false)).build());
        this.antiStuckInterval = this.sgHelpers.add((((new IntSetting.Builder().name("防卡检测间隔")).description("防卡住检测间隔 (游戏刻).")).defaultValue(20)).range(2, 200).build());
        this.antiStuckOffset = this.sgHelpers.add((((new IntSetting.Builder().name("绕行偏移")).description("防卡住目标点偏移 (沿运动方向，格).")).defaultValue(4)).range(1, 30).sliderRange(1, 30).build());
        this.antiStuckResumeTicks = this.sgHelpers.add((((new IntSetting.Builder().name("恢复等待")).description("触发后等待多少 游戏刻 恢复鞘翅跳跃 (寻路期间保持暂停)。")).defaultValue(80)).range(10, 600).build());
        // 强制拉升（内嵌自 Ying 0.7.2 PullUp / 界面名 Full Up）
        this.pullUp = this.sgHelpers.add((((new BoolSetting.Builder().name("强制拉升")).description("低于危险 Y 时无条件强制拉升 (抬头 + 烟花)，升到目标高度自动停止。")).defaultValue(true)).build());
        this.pullUpDangerY = this.sgHelpers.add((((new IntSetting.Builder().name("危险Y")).description("低于此 Y 时强制拉升 (降落/恢复期间除外)。默认 40，高于低Y退出线 20。")).defaultValue(40)).range(-64, 260).sliderRange(-64, 260).build());
        this.pullUpTargetY = this.sgHelpers.add((((new IntSetting.Builder().name("目标Y")).description("拉升到该高度后停止。")).defaultValue(200)).range(-20, 320).sliderRange(-20, 320).build());
        this.pullUpPitch = this.sgHelpers.add((((new DoubleSetting.Builder().name("拉升俯仰")).description("拉升时抬头俯仰角 (负值=抬头)。")).defaultValue(-37.72)).min(-90.0).max(-10.0).sliderRange(-80.0, -20.0).build());
        // 自动补烟花（内嵌自 Jeff Mod Pitch40Util）+ 滑翔失效恢复
        this.autoFireworkBoost = this.sgHelpers.add((((new BoolSetting.Builder().name("自动补烟花")).description("抬头速度不足或高度过低时自动补一发烟花 (借鉴 Jeff Mod Pitch40Util)。")).defaultValue(true)).build());
        this.fireworkVelocityThreshold = this.sgHelpers.add((((new DoubleSetting.Builder().name("补烟花速度阈值")).description("抬头阶段垂直速度低于此值就补烟花 (负值=已在下坠)。")).defaultValue(-0.35)).range(-0.5, 1.0).sliderRange(-0.5, 0.5).build());
        this.fireworkBoostCooldownTicks = this.sgHelpers.add((((new IntSetting.Builder().name("补烟花冷却")).description("两发补烟花之间至少间隔多少 游戏刻。")).defaultValue(10)).range(0, 100).sliderRange(0, 100).build());
        this.lowHeightRocketGap = this.sgHelpers.add((((new IntSetting.Builder().name("低空补烟花高度差")).description("(保留项) 低空补烟花高度差；当前以「最低高度」为界，此值暂不参与。")).defaultValue(20)).range(0, 100).sliderRange(0, 100).build());
        this.state = State.IDLE;
        this.collectStep = CollectStep.TO_P1;
        this.ships = Collections.synchronizedList(new ArrayList());
        this.targetIndex = 0;
        this.searchCancelled = true;
        this.searchGeneration = 0;
        this.stateTick = 0;
        this.lastGotoTick = 0;
        this.worldSeed = 0L;
        this.climbing = false;
        this.headFound = false;
        this.exactHead = null;
        this.nearTicks = 0;
        this.scanAttempts = 0;
        this.pullUpSuppressed = false;
        this.pullUpActive = false;
        this.pullUpTick = 0;
        this.fireworkBoostCooldown = 0;
        this.glideFailTicks = 0;
        this.glideRecoverTicks = 0;
        this.glideRecovering = false;
        this.fuelEmpty = false;
        this.supplyLandingResume = false;
        this.supplySpot = null;
        this.supplyFailCount = 0;
        this.sessionSkip = Collections.synchronizedSet(new HashSet());
        this.searchExtends = 0;
        this.takeoffFacing = null;
        this.noElytraTicks = 0;
        this.frameHadElytra = false;
        this.frameAirChecked = false;
        this.frameAirEmpty = false;
        this.frameAirScanTicks = 0;
        this.pickupSessionDone = false;
        this.firstFireworkDone = false;
        this.lastFireworkTick = 0;
        this.landingRecover = false;
        this.recoveryTargetY = 0.0;
        this.recoveryYaw = 0.0f;
        this.recoverStageTick = 0;
        this.recoverCount = 0;
        this.storagePhase = StoragePhase.NONE;
        this.storageTick = 0;
        this.pickupWaitStart = -1;
        this.boxFromInventory = false;
        this.boxInventorySlot = -1;
        this.boxBaseline = 0;
        this.ecBaseline = 0;
        this.dumpNeeded = false;
        this.resupplyNeeded = false;
        this.afterClose = StoragePhase.NONE;
        this.elytraBeforeFrame = 0;
        this.placeCandidates = new ArrayList<net.minecraft.util.math.BlockPos>();
        this.placeIndex = 0;
        this.lastPlaceClick = -100;
        this.placeClickAttempts = 0;
        this.moveQueue = new ArrayDeque();
        this.moveState = 0;
        this.moveSrc = -1;
        this.moveDst = -1;
        this.pickStep = 0;
        this.pickEcSlot = -1;
        this.pickInvSlot = -1;
        this.fillIndex = -1;
        this.takeRule = 0;
        this.takeSlot = -1;
        this.takeNeed = 0;
        this.takeStuckSlot = -1;
        this.takeStuckTicks = 0;
        this.takeEmptyTicks = 0;
        this.takeFailedItems = new HashSet<net.minecraft.item.Item>();
        this.fillStuckSlot = -1;
        this.fillStuckTicks = 0;
        this.openRetry = 0;
        this.boxOpenClickTick = -1;
        this.boxPurposeSupply = false;
        this.boxPlaceFloorFallback = false;
        this.recoverStage = 0;
        this.ecOpenFailCount = 0;
        this.supplyScanStart = 0;
        this.supplyPauseDump = false;
        this.resupplyRescan = false;
        this.standingMovedOff = false;
        this.debugLock = new Object();
    }

    public WWidget getWidget(GuiTheme theme) {
        WSection section = theme.section("黑名单", true);
        WButton clear = (WButton)section.add((WWidget)theme.button("清除黑名单")).expandX().widget();
        clear.action = () -> {
            List list = (List)this.blacklist.get();
            synchronized (list) {
                ((List)this.blacklist.get()).clear();
            }
            this.info("黑名单已清空.", new Object[0]);
        };
        return section;
    }

    public void onDeactivate() {
        this.searchCancelled = true;
        ++this.searchGeneration;
        this.searchThread = null;
        this.state = State.IDLE;
        this.ships.clear();
        this.pullUpSuppressed = false;
        this.pullUpActive = false;
        this.pullUpTick = 0;
        this.fireworkBoostCooldown = 0;
        this.glideFailTicks = 0;
        this.glideRecoverTicks = 0;
        this.glideRecovering = false;
        this.fuelEmpty = false;
        this.supplyLandingResume = false;
        this.supplySpot = null;
        this.supplyFailCount = 0;
        this.landingRecover = false;
        this.recoveryYaw = 0.0f;
        this.recoverStageTick = 0;
        this.storagePhase = StoragePhase.NONE;
        this.storageTick = 0;
        this.boxInventorySlot = -1;
        this.resetStorageClick();
        this.releaseForward();
        PathManagers.get().stop();
    }

    private void resetStorageClick() {
        this.moveQueue.clear();
        this.moveState = 0;
        this.moveSrc = -1;
        this.moveDst = -1;
        this.pickStep = 0;
        this.pickEcSlot = -1;
        this.pickInvSlot = -1;
        this.fillIndex = -1;
        this.takeRule = 0;
        this.takeSlot = -1;
        this.takeNeed = 0;
        this.takeStuckSlot = -1;
        this.takeStuckTicks = 0;
        this.takeEmptyTicks = 0;
        this.takeFailedItems.clear();
        this.fillStuckSlot = -1;
        this.fillStuckTicks = 0;
        this.openRetry = 0;
        this.boxPurposeSupply = false;
        this.ecOpenFailCount = 0;
        this.supplyScanStart = 0;
        this.standingMovedOff = false;
    }

    private void start() {
        if (this.state != State.IDLE && this.state != State.DONE) {
            this.btnStart.set(false);
            return;
        }
        this.state = State.SEARCHING;
        this.searchExtends = 0;
        this.takeoffFacing = null;
        this.landingRecover = false;
        this.recoveryYaw = 0.0f;
        this.recoverStageTick = 0;
        this.storagePhase = StoragePhase.NONE;
        this.storageTick = 0;
        this.boxInventorySlot = -1;
        this.info("开始搜索末地城 (范围=" + String.valueOf(this.searchRange.get()) + " 方块)...", new Object[0]);
        if ((this.debugSetting.get()).booleanValue()) {
            this.info("调试日志文件: " + String.valueOf(this.debugFilePath().toAbsolutePath()), new Object[0]);
        }
        this.launchSearch();
    }

    private void launchSearch() {
        this.searchCancelled = false;
        int gen = ++this.searchGeneration;
        Thread t = new Thread(() -> this.searchShips(gen), "ElytraCollector-Search");
        t.setDaemon(true);
        this.searchThread = t;
        t.start();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    /** 搜索入口：末地环模式优先走 26.3 原生定位器，否则走旧版种子推算。 */
    private void searchShips(int generation) {
        if ((this.ringSearch.get()).booleanValue()) {
            this.searchShipsRing(generation);
            return;
        }
        this.searchShipsLegacy(generation);
    }

    private void searchShipsLegacy(int generation) {
        block18: {
            try {
                if (this.searchCancelled || generation != this.searchGeneration) {
                    return;
                }
                this.worldSeed = this.parseSeed((String)this.seedSetting.get());
                EndCity endCity = new EndCity(VERSION);
                EndCityGenerator generator = new EndCityGenerator(VERSION);
                ChunkRand rand = new ChunkRand();
                EndBiomeSource biomeSource = new EndBiomeSource(VERSION, this.worldSeed);
                this.islandNoise = ElytraCollector.createIslandNoise(this.worldSeed);
                this.base3dNoise = new net.minecraft.util.math.noise.InterpolatedNoiseSampler((net.minecraft.util.math.random.Random)new net.minecraft.util.math.random.CheckedRandom(this.worldSeed), 0.25, 0.25, 80.0, 160.0, 4.0);
                EndTerrainGenerator terrainGen = new EndTerrainGenerator(biomeSource);
                int px = (int)Math.floor(this.mc.player.getX());
                int pz = (int)Math.floor(this.mc.player.getZ());
                int spacing = endCity.getSpacing();
                int range = this.searchRange.get() + this.searchExtends * 500;
                int regionRange = range / (spacing * 16) + 1;
                int prx = Math.floorDiv(px >> 4, spacing);
                int prz = Math.floorDiv(pz >> 4, spacing);
                ArrayList<ShipTarget> found = new ArrayList<ShipTarget>();
                for (int drx = -regionRange; drx <= regionRange; ++drx) {
                    if (this.searchCancelled || generation != this.searchGeneration) {
                        return;
                    }
                    for (int drz = -regionRange; drz <= regionRange; ++drz) {
                        ShipTarget t;
                        int rx = prx + drx;
                        int rz = prz + drz;
                        CPos city = endCity.getInRegion(this.worldSeed, rx, rz, rand);
                        if (city == null || !endCity.canSpawn(city, (BiomeSource)biomeSource) || !endCity.canGenerate(city, (TerrainGenerator)terrainGen)) continue;
                        generator.generate(terrainGen, city.getX(), city.getZ(), rand);
                        boolean hasShip = generator.hasShip();
                        if (hasShip && (t = this.extractShip(generator, city)) != null && !this.isBlacklisted(t.headPos) && !this.sessionSkip.contains(this.blacklistKey(t.headPos))) {
                            found.add(t);
                            if ((this.debugSetting.get()).booleanValue()) {
                                this.debugLog("船 城=(" + city.getX() + "," + city.getZ() + ") 龙头=(" + t.headPos.getX() + "," + t.headPos.getY() + "," + t.headPos.getZ() + ") 朝向=" + String.valueOf(t.facing) + " E=" + String.format("%.4f", this.endIslandDensity(city.getX() * 16 + 8, city.getZ() * 16 + 8)));
                            }
                        }
                        generator.reset();
                    }
                }
                if (this.searchCancelled || generation != this.searchGeneration) {
                    return;
                }
                this.applySearchResults(found, range, generation);
            }
            catch (Exception e) {
                if (this.searchCancelled || generation != this.searchGeneration) break block18;
                this.error("搜索失败: " + e.getMessage(), new Object[0]);
                this.state = State.IDLE;
                this.btnStart.set(false);
            }
        }
    }

    /**
     * 末地环模式：用 cubiomes 26.3（末地环修复版）原生定位器搜索末地城。
     * 原生库提供城市候选、地形可行性、船 piece 包围盒；展示框坐标由包围盒 + 朝向换算
     *（公式已与 seedfinding 321/321 实测对齐：x/z 端点、y=baseY+bb0.y+5、rot→朝向）。
     */
    private void searchShipsRing(int generation) {
        try {
            if (this.searchCancelled || generation != this.searchGeneration) {
                return;
            }
            this.worldSeed = this.parseSeed((String)this.seedSetting.get());
            EndBiomeSource biomeSource = new EndBiomeSource(MCVersion.v1_21, this.worldSeed);
            EndTerrainGenerator terrainGen = new EndTerrainGenerator(biomeSource);
            int px = (int)Math.floor(this.mc.player.getX());
            int pz = (int)Math.floor(this.mc.player.getZ());
            int spacing = 20;
            int range = this.searchRange.get() + this.searchExtends * 500;
            int regionRange = range / (spacing * 16) + 1;
            int prx = Math.floorDiv(px >> 4, spacing);
            int prz = Math.floorDiv(pz >> 4, spacing);
            ArrayList<ShipTarget> found = new ArrayList<ShipTarget>();
            EndLocator locator = new EndLocator(EndLocator.MC_1_26_3, this.worldSeed);
            try {
                int[] out = new int[2];
                int[] ship = new int[7];
                for (int drx = -regionRange; drx <= regionRange; ++drx) {
                    if (this.searchCancelled || generation != this.searchGeneration) {
                        return;
                    }
                    for (int drz = -regionRange; drz <= regionRange; ++drz) {
                        int rx = prx + drx;
                        int rz = prz + drz;
                        if (locator.getEndCityAt(rx, rz, out) == 0) {
                            continue;
                        }
                        int bx = out[0];
                        int bz = out[1];
                        if (!locator.isViableEndCity(bx, bz)) {
                            continue;
                        }
                        int cx = bx >> 4;
                        int cz = bz >> 4;
                        if (locator.getEndCityShip(cx, cz, ship) == 0) {
                            continue;
                        }
                        int baseY = EndCity.getAverageYPosition(terrainGen, cx, cz);
                        int[] f = ElytraCollector.frameFromShipBB(ship);
                        if (f == null) {
                            continue;
                        }
                        net.minecraft.util.math.BlockPos framePos = new net.minecraft.util.math.BlockPos(f[0], baseY + f[1], f[2]);
                        Direction facing = ElytraCollector.facingFromRot(ship[6]);
                        net.minecraft.util.math.BlockPos head = framePos.offset(facing, 7).offset(Direction.UP, 3);
                        if (this.isBlacklisted(head) || this.sessionSkip.contains(this.blacklistKey(head))) {
                            continue;
                        }
                        found.add(new ShipTarget(new CPos(cx, cz), head, facing));
                        if ((this.debugSetting.get()).booleanValue()) {
                            this.debugLog("环船 城=(" + bx + "," + bz + ") 龙头=(" + head.getX() + "," + head.getY() + "," + head.getZ() + ") 朝向=" + String.valueOf(facing) + " rot=" + ship[6] + " bb0Y=" + ship[1] + " baseY=" + baseY);
                        }
                    }
                }
            }
            finally {
                locator.free();
            }
            this.applySearchResults(found, range, generation);
        }
        catch (Exception e) {
            if (this.searchCancelled || generation != this.searchGeneration) {
                return;
            }
            this.error("末地环搜索失败: " + e.getMessage() + "；回退旧版种子推算.", new Object[0]);
            this.searchShipsLegacy(generation);
        }
    }

    /** 搜索结果统一收尾：排序、写列表、没船则扩范围、有船则进入起飞。 */
    private void applySearchResults(List<ShipTarget> found, int range, int generation) {
        if (this.searchCancelled || generation != this.searchGeneration) {
            return;
        }
        int px = (int)Math.floor(this.mc.player.getX());
        int pz = (int)Math.floor(this.mc.player.getZ());
        found.sort(Comparator.comparingInt(s -> (s.headPos.getX() - px) * (s.headPos.getX() - px) + (s.headPos.getZ() - pz) * (s.headPos.getZ() - pz)));
        this.ships.clear();
        this.ships.addAll(found);
        this.targetIndex = 0;
        List list = (List)this.searchResults.get();
        synchronized (list) {
            ((List)this.searchResults.get()).clear();
            for (ShipTarget t : found) {
                ((List)this.searchResults.get()).add(t.headPos.getX() + "," + t.headPos.getY() + "," + t.headPos.getZ() + "," + String.valueOf(t.facing));
            }
        }
        this.info("找到 " + this.ships.size() + " 艘带船末地城 (已排除黑名单, 范围=" + range + ((this.ringSearch.get()).booleanValue() ? ", 末地环模式 26.3" : "") + ").", new Object[0]);
        if (this.ships.isEmpty()) {
            if (this.searchExtends < 10) {
                ++this.searchExtends;
                this.info("范围内没有末地船，扩大范围 +" + this.searchExtends * 500 + " 格重新搜索...", new Object[0]);
                this.launchSearch();
            } else {
                this.searchExtends = 0;
                this.completeTask("范围内没有末地船，任务完成.");
            }
        } else {
            this.searchExtends = 0;
            if (this.state == State.SEARCHING) {
                this.firstFireworkDone = false;
                this.state = State.RISING;
                this.stateTick = 0;
            }
        }
    }

    /**
     * 由船 piece 包围盒 + rot 推展示框坐标（相对城市基准 y）。
     * 局部偏移 (6,5,7) 来自船模板；返回 {worldX, relY, worldZ}，世界 y = baseY + relY。
     */
    private static int[] frameFromShipBB(int[] s) {
        int ox = 6;
        int oy = 5;
        int oz = 7;
        switch (s[6]) {
            case 0: return new int[]{s[0] + ox, s[1] + oy, s[2] + oz};
            case 1: return new int[]{s[3] - oz, s[1] + oy, s[2] + ox};
            case 2: return new int[]{s[3] - ox, s[1] + oy, s[5] - oz};
            case 3: return new int[]{s[0] + oz, s[1] + oy, s[5] - ox};
            default: return null;
        }
    }

    /** cubiomes rot（0=NONE 1=CW90 2=180 3=CCW90）→ 船头朝向。 */
    private static Direction facingFromRot(int rot) {
        switch (rot) {
            case 1: return Direction.EAST;
            case 2: return Direction.SOUTH;
            case 3: return Direction.WEST;
            default: return Direction.NORTH;
        }
    }

    private ShipTarget extractShip(EndCityGenerator generator, CPos chunk) {
        try {
            net.minecraft.util.math.BlockPos itemFrame = null;
            int sternX = 0;
            int sternZ = 0;
            int sternCount = 0;
            for (Pair<Generator.ILootType, BPos> p : generator.getChestsPos()) {
                Generator.ILootType lt = p.getFirst();
                BPos b = p.getSecond();
                if (lt == EndCityGenerator.LootType.SHIP_ELYTRA) {
                    itemFrame = new net.minecraft.util.math.BlockPos(b.getX(), b.getY(), b.getZ());
                    continue;
                }
                if (lt != EndCityGenerator.LootType.SHIP_SENTRY_2 && lt != EndCityGenerator.LootType.SHIP_SENTRY_3) continue;
                sternX += b.getX();
                sternZ += b.getZ();
                ++sternCount;
            }
            if (itemFrame == null || sternCount == 0) {
                return null;
            }
            int dx = sternX / sternCount - itemFrame.getX();
            int dz = sternZ / sternCount - itemFrame.getZ();
            net.minecraft.util.math.Direction behind = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? net.minecraft.util.math.Direction.EAST : net.minecraft.util.math.Direction.WEST) : (dz > 0 ? net.minecraft.util.math.Direction.SOUTH : net.minecraft.util.math.Direction.NORTH);
            net.minecraft.util.math.Direction facing = behind.getOpposite();
            net.minecraft.util.math.BlockPos head = itemFrame.offset(facing, 7).offset(net.minecraft.util.math.Direction.UP, 3);
            return new ShipTarget(chunk, head, facing);
        }
        catch (Exception e) {
            return null;
        }
    }

    private void beginFlyToTarget() {
        if (this.targetIndex >= this.ships.size()) {
            this.completeTask("全部末地城已处理，任务完成.");
            return;
        }
        this.current = this.ships.get(this.targetIndex);
        this.waypoints = ShipWaypoints.from(this.current.headPos, this.current.facing);
        this.climbing = false;
        this.headFound = false;
        this.frameAirChecked = false;
        this.frameAirEmpty = false;
        this.frameAirScanTicks = 0;
        this.fireworkBoostCooldown = 0;
        this.glideFailTicks = 0;
        this.glideRecoverTicks = 0;
        this.glideRecovering = false;
        this.fuelEmpty = false;
        this.supplyLandingResume = false;
        this.supplySpot = null;
        this.supplyFailCount = 0;
        this.exactHead = null;
        this.nearTicks = 0;
        this.scanAttempts = 0;
        this.firstFireworkDone = false;
        this.landingRecover = false;
        this.recoverCount = 0;
        this.stateTick = 0;
        this.info("飞向最近未去过的船 近似龙头=" + String.valueOf(this.current.headPos) + " 朝向=" + String.valueOf(this.current.facing), new Object[0]);
        this.state = State.FLYING;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        try {
            if (this.mc.player == null || this.mc.world == null) {
                return;
            }
            ++this.stateTick;
            this.updatePullUp();
            // 低 Y 硬底线：低于阈值直接断开服务器退出游戏（防掉虚空丢物品）
            if (this.state != State.IDLE && this.state != State.DONE && (this.lowYExit.get()).booleanValue() && this.mc.player.getY() < (double)(this.lowYThreshold.get()).intValue()) {
                this.warning("当前高度低于阈值 " + String.valueOf(this.lowYThreshold.get()) + "，自动退出游戏.", new Object[0]);
                this.emergencyExit("FO 鞘翅采集: Y 低于阈值 " + String.valueOf(this.lowYThreshold.get()) + "，自动退出游戏保护");
                return;
            }
            if (this.pullUpActive && !this.mc.player.isOnGround()) {
                this.applyPullUp();
                return;
            }
            if (this.pullUpSuppressed) {
                return;
            }
            if (this.storagePhase != StoragePhase.NONE) {
                this.onStorageTick();
                return;
            }
            switch (this.state.ordinal()) {
                case 2: {
                    this.onRising();
                    break;
                }
                case 3: {
                    this.onFlying();
                    break;
                }
                case 4: {
                    this.onLanding();
                    break;
                }
                case 5: {
                    this.onCollecting();
                    break;
                }
                case 7: {
                    this.onSupplyLanding();
                    break;
                }
            }
        }
        catch (Throwable t) {
            this.error("任务异常: " + String.valueOf(t), new Object[0]);
            t.printStackTrace();
            this.stopTask("任务异常，停止任务.");
        }
    }

    private void onRising() {
        if (!this.landingRecover && this.stateTick > 400) {
            this.error("起飞超时 (可能没穿鞘翅或没有烟花).", new Object[0]);
            this.state = State.IDLE;
            this.btnStart.set(false);
            this.releaseForward();
            return;
        }
        // 起跳防卡（内嵌自 Ying 0.7.2 ElytraJump）：连续两次检测在同一位置就用 Baritone 绕开
        if (this.antiStuckJump.get() && !this.landingRecover && !this.mc.player.isGliding()) {
            if (this.antiStuckResume > 0) {
                --this.antiStuckResume;
                return;
            }
            if (this.stateTick > 20 && this.stateTick % this.antiStuckInterval.get() == 0) {
                if (this.lastJumpPos != null && this.mc.player.getBlockPos().equals(this.lastJumpPos)) {
                    ++this.jumpStuckCount;
                } else {
                    this.jumpStuckCount = 0;
                }
                this.lastJumpPos = this.mc.player.getBlockPos().toImmutable();
                if (this.jumpStuckCount >= 2) {
                    int off = this.antiStuckOffset.get();
                    double rad = Math.toRadians(this.mc.player.getYaw());
                    int bx = this.mc.player.getBlockX() + (int)Math.round(-Math.sin(rad) * (double)off);
                    int bz = this.mc.player.getBlockZ() + (int)Math.round(Math.cos(rad) * (double)off);
                    PathManagers.get().moveTo(new net.minecraft.util.math.BlockPos(bx, this.mc.player.getBlockY(), bz), false);
                    this.antiStuckResume = this.antiStuckResumeTicks.get();
                    this.jumpStuckCount = 0;
                    this.info("起跳卡住，绕行到 (" + bx + "," + this.mc.player.getBlockY() + "," + bz + ").", new Object[0]);
                    return;
                }
            }
        }
        if (this.mc.player.isOnGround() && this.stateTick <= 2) {
            this.maybeStartSession();
            if (this.storagePhase != StoragePhase.NONE) {
                return;
            }
        }
        if (this.landingRecover) {
            ++this.recoverStageTick;
            if (this.recoverStage == 0) {
                this.rotateFastTo(this.recoveryYaw, this.mc.player.getPitch());
            } else {
                this.rotateTo(this.yawTowards(this.waypoints.landing), 37.72f);
            }
        } else if (this.takeoffFacing != null) {
            this.faceDirection(this.takeoffFacing);
        }
        if (!this.landingRecover || this.recoverStage != 1) {
            this.mc.player.setPitch(-45.0f);
        }
        this.mc.options.forwardKey.setPressed(true);
        if (!this.mc.player.isGliding()) {
            if (this.mc.player.isOnGround()) {
                PathManagers.get().stop();
                this.mc.player.jump();
            } else if (this.stateTick % 4 == 0) {
                this.mc.player.networkHandler.sendPacket((net.minecraft.network.packet.Packet)new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket((net.minecraft.entity.Entity)this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }
        if (!this.firstFireworkDone) {
            this.useFirework();
            this.firstFireworkDone = true;
            this.lastFireworkTick = this.stateTick;
        } else if (!(this.landingRecover && this.recoverStage == 1 || (long)(this.stateTick - this.lastFireworkTick) < Math.max(1L, Math.round(this.fireworkInterval.get() * 20.0)))) {
            this.useFirework();
            this.lastFireworkTick = this.stateTick;
        }
        if (this.landingRecover) {
            net.minecraft.util.math.BlockPos lp = this.waypoints.landing;
            if (this.recoverStage == 0) {
                if (this.mc.player.getY() >= (double)(lp.getY() + 25) || this.recoverStageTick > 600) {
                    this.recoverStage = 1;
                    this.recoverStageTick = 0;
                    this.info("已拉升到降落点上方 25 格，用 俯冲巡航 模式飞向降落点.", new Object[0]);
                }
            } else if (this.horizontalDistance(lp) <= 3.0 || this.recoverStageTick > 600) {
                this.releaseForward();
                this.landingRecover = false;
                this.recoverStage = 0;
                this.recoverStageTick = 0;
                this.info("已回到降落点上方，进入缓降.", new Object[0]);
                this.state = State.LANDING;
                this.stateTick = 0;
            }
        } else if (this.mc.player.getY() >= (double)(this.riseHeight.get()).intValue()) {
            if (this.ships.isEmpty()) {
                this.mc.player.setPitch(-20.0f);
                this.mc.options.forwardKey.setPressed(true);
            } else {
                this.releaseForward();
                this.firstFireworkDone = false;
                this.beginFlyToTarget();
            }
        }
    }

    private void onFlying() {
        if (!this.mc.player.isGliding()) {
            if (this.mc.player.isOnGround()) {
                this.glideFailTicks = 0;
                this.glideRecoverTicks = 0;
                this.glideRecovering = false;
                this.firstFireworkDone = false;
                this.takeoffFacing = null;
                this.state = State.RISING;
                this.stateTick = 0;
                return;
            }
            this.handleGlideFailure();
            return;
        }
        this.glideFailTicks = 0;
        this.glideRecoverTicks = 0;
        this.glideRecovering = false;

        // 烟花燃料耗尽：中止 p40 巡航，改为缓降寻找陆地补给
        if (this.autoFireworkBoost.get() && this.countFireworks() <= 4) {
            this.enterSupplyLanding();
            return;
        }

        net.minecraft.util.math.BlockPos lp = this.waypoints.landing;

        if (!this.headFound) {
            if (this.horizontalDistance(lp) < 80.0) {
                ++this.nearTicks;
                if (this.nearTicks == 1 && this.debugSetting.get() && !this.cityTerrainHigh()) {
                    this.debugLog("注意: 地形高度判断不足 (船可能在虚空上)，改由龙头扫描确认.");
                }
                if (this.nearTicks % 20 == 0) {
                    boolean loaded = true;
                    for (int dx = -1; dx <= 1 && loaded; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (!this.mc.world.getChunkManager().isChunkLoaded((this.current.headPos.getX() >> 4) + dx, (this.current.headPos.getZ() >> 4) + dz)) {
                                loaded = false;
                                break;
                            }
                        }
                    }
                    if (loaded) {
                        ++this.scanAttempts;
                        DragonHead exact = this.findDragonHead(this.current.headPos, 10, 40);
                        if (exact != null) {
                            this.exactHead = exact.pos;
                            this.waypoints = ShipWaypoints.from(this.exactHead, this.current.facing);
                            this.headFound = true;
                            this.info("已确认龙头: " + this.exactHead + " 朝向=" + this.current.facing);
                            if (this.debugSetting.get()) {
                                int realTop = this.mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, this.exactHead.getX(), this.exactHead.getZ());
                                this.debugLog("龙头=(" + this.exactHead.getX() + "," + this.exactHead.getY() + "," + this.exactHead.getZ() + ") 龙头下地表=" + realTop + " 种子Y=" + this.current.headPos.getY());
                            }
                        } else {
                            this.info("检测龙头第 " + this.scanAttempts + "/7 次未找到 (目标=" + this.current.headPos.getX() + "," + this.current.headPos.getZ() + ").");
                        }
                    }
                }
                if (!this.headFound && this.scanAttempts >= 7) {
                    DragonHead last = this.findDragonHead(this.current.headPos, 10, 200);
                    if (last == null) {
                        this.warning("检测 7 次且整列无龙头，判定为假城，加入黑名单并重新搜索.");
                        this.addBlacklist(this.current.headPos);
                        this.sessionSkip.add(this.blacklistKey(this.current.headPos));
                        this.ships.clear();
                        this.targetIndex = 0;
                        this.searchExtends = 0;
                        this.takeoffFacing = null;
                        this.firstFireworkDone = false;
                        this.state = State.RISING;
                        this.stateTick = 0;
                        this.launchSearch();
                        return;
                    }
                    this.exactHead = last.pos;
                    this.waypoints = ShipWaypoints.from(this.exactHead, this.current.facing);
                    this.headFound = true;
                    this.info("整列扫描发现龙头: " + this.exactHead + " 朝向=" + this.current.facing);
                }
            } else {
                this.nearTicks = 0;
            }

            if (this.headFound) {
                this.stateTick = 0;
            }
        }

        if (this.headFound && !this.frameAirChecked) {
            if (this.frameAirScanTicks % 10 == 0) {
                net.minecraft.entity.decoration.ItemFrameEntity airFrame = this.findFrameNear(this.waypoints.p3, 2.0);
                if (airFrame != null) {
                    this.frameAirChecked = true;
                    this.frameAirEmpty = airFrame.getHeldItemStack().getItem() != net.minecraft.item.Items.ELYTRA;
                    if (this.frameAirEmpty) {
                        this.info("展示框里没有鞘翅，不降落：拉升到 最高高度 以上后跳过该船.");
                        this.climbing = true;
                    } else {
                        this.info("展示框里有鞘翅，按原计划降落.");
                    }
                }
            }
            ++this.frameAirScanTicks;
            if (this.frameAirScanTicks > 200) {
                this.frameAirChecked = true;
                this.frameAirEmpty = true;
                this.info("空中 200 tick 未找到展示框实体，按无鞘翅处理 (跳过该船).");
            }
        }

        boolean holdForFrame = this.headFound && !this.frameAirChecked && this.horizontalDistance(lp) <= 12.0;
        float yaw = !this.headFound && this.sweepScan.get() ? this.sweepYaw() : this.yawTowards(lp);
        if (holdForFrame) {
            yaw = this.mc.player.getYaw() + 4.0f;
        }
        double y = this.mc.player.getY();
        if (this.climbing) {
            if (y >= this.maxHeight.get() && !this.antiClimbActive) this.climbing = false;
        } else if (y <= this.minHeight.get()) {
            this.climbing = true;
        }

        // 防卡区块：只在"已确认龙头、飞向船"的阶段生效，避免干扰龙头扫描
        if (this.headFound && !this.frameAirEmpty) {
            this.updateAntiChunkClimb(lp);
        } else {
            this.antiClimbActive = false;
            this.antiClimbTicks = 0;
            this.antiClearStreak = 0;
        }

        float pitch = this.climbing ? -54.77f : 37.72f;
        if (holdForFrame) {
            pitch = this.climbing ? -54.77f : 0.0f;
        }
        if (this.pitchSmooth.get()) {
            float speed = pitch < this.smoothedPitch ? this.pitchRotateUp.get().floatValue() : this.pitchRotateDown.get().floatValue();
            this.smoothedPitch = ElytraCollector.stepToward(this.smoothedPitch, pitch, speed);
            pitch = this.smoothedPitch;
        } else {
            this.smoothedPitch = pitch;
        }

        this.rotateTo(yaw, pitch);
        this.mc.options.forwardKey.setPressed(true);

        // 自动补烟花（内嵌自 Jeff Mod Pitch40Util）：抬头速度不足 / 高度过低
        if (this.fireworkBoostCooldown > 0) {
            --this.fireworkBoostCooldown;
        }
        if (this.autoFireworkBoost.get()
                && this.fireworkBoostCooldown == 0
                && !holdForFrame
                && this.horizontalDistance(lp) > 20.0
                && this.mc.player.getY() < (double)this.maxHeight.get()) {
            boolean tooSlow = this.climbing && this.mc.player.getVelocity().y < this.fireworkVelocityThreshold.get();
            boolean tooLow = this.mc.player.getY() < (double)this.minHeight.get();
            if (tooSlow || tooLow) {
                this.useFirework();
                this.fireworkBoostCooldown = Math.max(this.fireworkBoostCooldownTicks.get(), (int)Math.max(1L, Math.round(this.fireworkInterval.get() * 20.0)));
            }
        }

        // 等框扫描期间绕圈，用烟花维持高度（不降落）
        if (holdForFrame && this.frameAirScanTicks > 0 && this.frameAirScanTicks % 40 == 0) {
            this.useFirework();
            this.fireworkBoostCooldown = Math.max(this.fireworkBoostCooldownTicks.get(), (int)Math.max(1L, Math.round(this.fireworkInterval.get() * 20.0)));
        }

        if (this.frameAirEmpty) {
            if (this.mc.player.getY() < this.maxHeight.get()) {
                this.climbing = true;
                if (this.fireworkBoostCooldown == 0 && this.stateTick - this.lastFireworkTick >= Math.max(1, Math.round(this.fireworkInterval.get() * 20))) {
                    this.useFirework();
                    this.lastFireworkTick = this.stateTick;
                    this.fireworkBoostCooldown = Math.max(this.fireworkBoostCooldownTicks.get(), (int)Math.max(1L, Math.round(this.fireworkInterval.get() * 20.0)));
                }
            } else {
                this.leaveShipNow();
            }
            return;
        }
        if (this.headFound && this.frameAirChecked && !this.frameAirEmpty && this.horizontalDistance(lp) <= 3.0) {
            this.state = State.LANDING;
            this.stateTick = 0;
        }
    }


    /** 滑翔意外中断（被打断/服务器拒绝/耐久耗尽）：先尝试恢复展开，200 tick 失败自动退出保护 */
    private void handleGlideFailure() {
        ++this.glideFailTicks;
        if (this.glideFailTicks == 1) {
            net.minecraft.item.ItemStack chest = this.mc.player.getInventory().getStack(38);
            if (chest.getItem() == net.minecraft.item.Items.ELYTRA && chest.getDamage() >= chest.getMaxDamage() - 1) {
                this.warning("鞘翅耐久耗尽，无法再展开，自动退出游戏保护.", new Object[0]);
                this.emergencyExit("FO 鞘翅采集: 鞘翅耐久耗尽，自动退出游戏保护");
                return;
            }
            this.info("滑翔意外中断，尝试恢复展开.", new Object[0]);
        }
        if (this.glideFailTicks <= 20) {
            this.mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }
        this.glideRecovering = true;
        ++this.glideRecoverTicks;
        this.mc.player.setPitch(-30.0f);
        this.mc.options.forwardKey.setPressed(true);
        if (this.glideRecoverTicks % 10 == 0) {
            this.mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
        if (this.glideRecoverTicks % 40 == 0) {
            this.useFirework();
        }
        if (this.glideRecoverTicks > 200) {
            this.warning("滑翔 200 游戏刻仍无法恢复，判定服务端拒绝，自动退出游戏保护.", new Object[0]);
            this.emergencyExit("FO 鞘翅采集: 滑翔无法恢复，自动退出游戏保护");
        }
    }

    void abortStorageForPullUp() {
        if (this.storagePhase == StoragePhase.NONE) {
            return;
        }
        if (this.isContainerOpen()) {
            this.mc.player.closeHandledScreen();
        }
        PathManagers.get().stop();
        this.releaseForward();
        this.storagePhase = StoragePhase.NONE;
        this.storageTick = 0;
        this.resetStorageClick();
        this.info("危险高度: 中止存储会话，强制拉升.", new Object[0]);
    }

    boolean isLandingOrRecovering() {
        return this.state == State.LANDING || this.state == State.SUPPLY_LANDING || this.landingRecover;
    }

    boolean isPullUpSuppressed() {
        return this.pullUpSuppressed;
    }

    void setPullUpSuppressed(boolean suppressed) {
        this.pullUpSuppressed = suppressed;
        if (!suppressed) {
            this.releaseForward();
        }
    }

    private void onLanding() {
        double ddz;
        double ddy;
        net.minecraft.util.math.BlockPos lp = this.waypoints.landing;
        double hDist = this.horizontalDistance(lp);
        if (!this.landingRecover && !this.mc.player.isOnGround() && this.mc.player.getY() < (double)(lp.getY() - 10)) {
            ++this.recoverCount;
            if (this.recoverCount > 3) {
                this.stopTask("多次偏离降落点无法降落，停止任务.");
                return;
            }
            this.landingRecover = true;
            this.recoverStage = 0;
            this.recoverStageTick = 0;
            this.recoveryYaw = this.yawTowards(lp) + 180.0f;
            this.firstFireworkDone = false;
            this.info("低于降落点 10 格，反向拉升 (目标降落点上方 25 格).", new Object[0]);
            this.state = State.RISING;
            this.stateTick = 0;
            return;
        }
        if (this.mc.player.isOnGround()) {
            if (hDist < 3.0) {
                this.releaseForward();
                this.recoverCount = 0;
                this.info("已降落在降落点.", new Object[0]);
                this.state = State.COLLECTING;
                this.collectStep = CollectStep.TO_P1;
                this.stateTick = 0;
                this.beginCollectStep();
                return;
            }
            if (this.stateTick < 6 || !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(lp, false);
            }
            if (this.stateTick > 6 && !PathManagers.get().isPathing() && hDist < 2.5) {
                this.releaseForward();
                this.state = State.COLLECTING;
                this.collectStep = CollectStep.TO_P1;
                this.stateTick = 0;
                this.beginCollectStep();
            }
            return;
        }
        if (!this.mc.player.isGliding()) {
            this.mc.player.networkHandler.sendPacket((net.minecraft.network.packet.Packet)new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket((net.minecraft.entity.Entity)this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }
        double ddx = this.mc.player.getX() - ((double)lp.getX() + 0.5);
        if (ddx * ddx + (ddy = this.mc.player.getY() - ((double)lp.getY() + 0.5)) * ddy + (ddz = this.mc.player.getZ() - ((double)lp.getZ() + 0.5)) * ddz > 400.0) {
            this.mc.player.setYaw(this.yawTowards(lp));
            this.mc.player.setPitch(55.0f);
            this.mc.options.forwardKey.setPressed(true);
            return;
        }
        if (hDist > 3.0) {
            double dy = this.mc.player.getY() - (double)(lp.getY() + 1);
            this.mc.player.setYaw(this.yawTowards(lp));
            this.mc.player.setPitch((float)Math.min(45.0, Math.max(5.0, dy * 0.8)));
            this.mc.options.forwardKey.setPressed(true);
        } else {
            this.releaseForward();
            this.mc.player.setPitch(0.0f);
            if (this.slowFallBoost.get() && this.stateTick % 2 == 0) {
                this.mc.player.setYaw(this.mc.player.getYaw() + 180.0f);
            }
        }
    }

    private void beginCollectStep() {
        if (this.waypoints == null) {
            this.state = State.RISING;
            return;
        }
        switch (this.collectStep.ordinal()) {
            case 0: {
                PathManagers.get().moveTo(this.waypoints.p1, false);
                break;
            }
            case 1: {
                PathManagers.get().moveTo(this.waypoints.p2, false);
                break;
            }
            case 3: {
                PathManagers.get().moveTo(this.waypoints.p3, false);
                break;
            }
            case 4: {
                this.elytraBeforeFrame = this.countElytraMain();
                break;
            }
            case 5: {
                this.pickUpElytra();
                break;
            }
            case 6: {
                this.equipElytra();
                break;
            }
            case 7: {
                PathManagers.get().moveTo(this.waypoints.p1, false);
                break;
            }
            case 8: {
                PathManagers.get().moveTo(this.waypoints.landing.down(4), false);
                break;
            }
            case 9: {
                PathManagers.get().moveTo(this.takeoffFromP1 ? this.waypoints.p1 : this.waypoints.landing.down(4), false);
                break;
            }
        }
    }

    private void onCollecting() {
        switch (this.collectStep.ordinal()) {
            case 0: {
                if (!this.reached(this.waypoints.p1, 2.5)) break;
                this.advance(CollectStep.TO_P2);
                break;
            }
            case 1: {
                if (!this.reached(this.waypoints.p2, 2.5)) break;
                this.advance(CollectStep.WAIT_SHULKER);
                break;
            }
            case 2: {
                if (!this.hasShulkerAtGuard()) {
                    this.advance(CollectStep.TO_P3);
                    break;
                }
                if (this.stateTick - this.lastGotoTick < 20) break;
                PathManagers.get().moveTo(this.waypoints.p2, false);
                this.lastGotoTick = this.stateTick;
                break;
            }
            case 3: {
                if (!this.reached(this.waypoints.p3, 2.0)) break;
                this.advance(CollectStep.HIT_FRAME);
                break;
            }
            case 4: {
                net.minecraft.entity.decoration.ItemFrameEntity frame = this.getNearestItemFrame(this.killAuraReach.get() + 2.0);
                if (frame != null && this.stateTick == 1) {
                    boolean bl = this.frameHadElytra = frame.getHeldItemStack().getItem() == net.minecraft.item.Items.ELYTRA;
                    if (!this.frameHadElytra) {
                        this.info("展示框里没有鞘翅，直接离开该船.", new Object[0]);
                        this.leaveShipNow();
                        return;
                    }
                }
                if ((this.stateTick <= 1 || this.stateTick % 20 == 0) && this.noElytraTicks < 3) {
                    ++this.noElytraTicks;
                    this.hitItemFrame();
                }
                if (this.findDroppedElytra() != null || this.countElytraMain() > this.elytraBeforeFrame) {
                    this.advance(CollectStep.PICK_UP);
                    break;
                }
                if (this.stateTick <= 80) break;
                this.info("攻击展示框后没有鞘翅掉落，直接离开该船.", new Object[0]);
                this.leaveShipNow();
                break;
            }
            case 5: {
                net.minecraft.entity.ItemEntity item = this.findDroppedElytra();
                if (item != null) {
                    this.walkTowards(item.getX(), item.getZ());
                    if (!(item.squaredDistanceTo((net.minecraft.entity.Entity)this.mc.player) < 1.2)) break;
                    this.mc.options.forwardKey.setPressed(false);
                    break;
                }
                if (this.countElytraMain() > this.elytraBeforeFrame) {
                    this.mc.options.forwardKey.setPressed(false);
                    this.advance(CollectStep.EQUIP);
                    break;
                }
                if (this.stateTick <= 60) break;
                this.info("鞘翅掉落消失未拾取，直接离开该船.", new Object[0]);
                this.leaveShipNow();
                break;
            }
            case 6: {
                if (this.isWearingFullDurabilityElytra()) {
                    if (!this.pickupSessionDone) {
                        this.pickupSessionDone = true;
                        this.maybeStartSession();
                        if (this.storagePhase != StoragePhase.NONE) {
                            return;
                        }
                    }
                    this.advance(CollectStep.EXIT_P1);
                    break;
                }
                this.equipElytra();
                break;
            }
            case 7: {
                if (!this.reached(this.waypoints.p1, 2.5)) break;
                this.advance(CollectStep.EXIT_LANDING);
                break;
            }
            case 8: {
                if (!this.reached(this.waypoints.landing, 2.5)) break;
                this.addBlacklist(this.current.headPos);
                this.info("该船完成，加入黑名单.", new Object[0]);
                this.finishShip();
                break;
            }
            case 9: {
                net.minecraft.util.math.BlockPos takeoff = this.takeoffFromP1 ? this.waypoints.p1 : this.waypoints.landing.down(4);
                if (!this.reached(takeoff, 2.5)) {
                    if (this.stateTick > 400) {
                        this.stopTask("走不到起飞点，请手动离开船.");
                    }
                    break;
                }
                if (!this.canTakeOffHere()) {
                    if (!this.takeoffFromP1) {
                        this.takeoffFromP1 = true;
                        this.stateTick = 0;
                        PathManagers.get().moveTo(this.waypoints.p1, false);
                        this.info("起飞点上方有遮挡，改到船外起飞点再试.", new Object[0]);
                        break;
                    }
                    this.stopTask("船外起飞点上方仍被遮挡，请手动离开船.");
                    break;
                }
                this.releaseForward();
                this.info("已到起飞点，起飞离开.", new Object[0]);
                this.state = State.RISING;
                this.stateTick = 0;
            }
        }
    }

    private void advance(CollectStep next) {
        this.collectStep = next;
        this.stateTick = 0;
        this.lastGotoTick = 0;
        this.noElytraTicks = 0;
        this.frameHadElytra = false;
        this.pickupSessionDone = false;
        this.beginCollectStep();
    }

    /** 断开服务器连接（自动退出游戏） */
    private void disconnect(String reason) {
        if (this.mc.player == null || this.mc.player.networkHandler == null) {
            return;
        }
        this.mc.player.networkHandler.getConnection().disconnect(net.minecraft.text.Text.literal(reason));
    }

    /** 自动下线保护：断开服务器 + 停任务 + 关闭模块，避免重连后残留激活 */
    private void emergencyExit(String reason) {
        this.disconnect(reason);
        this.stopTask(reason);
        if (this.isActive()) {
            this.toggle();
        }
    }

    private void stopTask(String reason) {
        this.info(reason, new Object[0]);
        this.searchCancelled = true;
        ++this.searchGeneration;
        this.searchThread = null;
        this.storagePhase = StoragePhase.NONE;
        this.storageTick = 0;
        this.landingRecover = false;
        this.recoveryYaw = 0.0f;
        this.recoverStageTick = 0;
        this.recoverCount = 0;
        this.releaseForward();
        this.resetStorageClick();
        if (this.state != State.IDLE) {
            this.state = State.IDLE;
        }
        PathManagers.get().stop();
        this.btnStart.set(false);
    }

    private void completeTask(String reason) {
        this.stopTask(reason);
    }

    private void finishShip() {
        this.maybeStartSession();
        if (this.storagePhase == StoragePhase.NONE) {
            this.relaunchAfterShip();
        }
    }

    private void maybeStartSession() {
        this.dumpNeeded = this.freeSlots() <= this.freeSlotDump.get();
        this.resupplyNeeded = this.anyDeficit();
        if (this.dumpNeeded || this.resupplyNeeded) {
            this.info("存储会话: 存鞘翅=" + this.dumpNeeded + ", 补货=" + this.resupplyNeeded, new Object[0]);
            this.storagePhase = StoragePhase.PLACE_EC;
            this.storageTick = 0;
            this.ecPos = null;
            this.boxPos = null;
            this.boxInventorySlot = -1;
            this.boxFromInventory = false;
            this.supplyScanStart = 0;
            this.supplyPauseDump = false;
            this.resupplyRescan = false;
            this.releaseForward();
            PathManagers.get().stop();
        }
    }

    private void relaunchAfterShip() {
        this.ships.clear();
        this.targetIndex = 0;
        this.searchExtends = 0;
        this.takeoffFacing = this.current != null ? this.current.facing : null;
        this.firstFireworkDone = false;
        this.landingRecover = false;
        this.state = State.RISING;
        this.stateTick = 0;
        this.launchSearch();
    }

    private void leaveShipNow() {
        this.addBlacklist(this.current.headPos);
        this.info("该船跳过并加入黑名单，重新搜索最近未去过的船.", new Object[0]);
        this.ships.clear();
        this.targetIndex = 0;
        this.searchExtends = 0;
        this.takeoffFacing = this.current.facing;
        this.firstFireworkDone = false;
        this.landingRecover = false;
        this.releaseForward();
        this.launchSearch();
        this.takeoffFromP1 = false;
        if (this.mc.player.isOnGround() && this.waypoints != null) {
            // 一律先走到起飞点、确认头顶 4 格开阔再起飞，绝不在船舱内原地升空
            this.info("先回起飞点确认头顶开阔，再离开该船.", new Object[0]);
            this.state = State.COLLECTING;
            this.collectStep = CollectStep.LEAVE_SHIP;
            this.stateTick = 0;
            this.beginCollectStep();
        } else {
            this.state = State.RISING;
            this.stateTick = 0;
        }
    }

    /** 起飞点头顶 3×3×4 是否全部是空气（防止在船舱/屋檐下起跳撞墙） */
    private boolean canTakeOffHere() {
        net.minecraft.util.math.BlockPos p = this.mc.player.getBlockPos();
        for (int dy = 1; dy <= 4; ++dy) {
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    if (!this.mc.world.getBlockState(p.add(dx, dy, dz)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** 强制拉升：低于危险 Y 时无条件抬头 + 烟花，升到目标 Y 自动停止（内嵌自 Ying 0.7.2 PullUp） */
    private void updatePullUp() {
        if (!this.pullUp.get()) {
            this.pullUpActive = false;
            return;
        }
        if (this.state == State.IDLE || this.state == State.DONE) {
            this.pullUpActive = false;
            return;
        }
        // 降落/恢复期间不拉升（与 Ying PullUp 原逻辑一致，避免打断降落）
        if (this.isLandingOrRecovering()) {
            if (this.pullUpActive) {
                this.pullUpActive = false;
                this.info("进入降落/恢复，暂停强制拉升.", new Object[0]);
            }
            return;
        }
        ++this.pullUpTick;
        if (this.pullUpActive) {
            if (this.mc.player.getY() >= (double)this.pullUpTargetY.get()) {
                this.pullUpActive = false;
                this.pullUpTick = 0;
                this.info("已升到目标高度 " + String.valueOf(this.pullUpTargetY.get()) + "，结束强制拉升.", new Object[0]);
            }
            return;
        }
        if (this.mc.player.getY() < (double)this.pullUpDangerY.get()) {
            this.pullUpActive = true;
            this.pullUpTick = 0;
            this.pullUpLastFireworkTick = -(int)Math.max(1L, Math.round(this.fireworkInterval.get() * 20.0));
            this.info("危险高度: 低于 " + String.valueOf(this.pullUpDangerY.get()) + "，强制拉升.", new Object[0]);
            this.abortStorageForPullUp();
            PathManagers.get().stop();
        }
    }

    private void applyPullUp() {
        this.mc.player.setPitch(this.pullUpPitch.get().floatValue());
        this.mc.options.forwardKey.setPressed(true);
        if (!this.mc.player.isGliding()) {
            if (!this.mc.player.isOnGround()) {
                this.mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }
        if ((long)(this.pullUpTick - this.pullUpLastFireworkTick) >= Math.max(1L, Math.round(this.fireworkInterval.get() * 20.0))) {
            this.useFirework();
            this.pullUpLastFireworkTick = this.pullUpTick;
        }
    }

    private void onStorageTick() {
        try {
            ++this.storageTick;
            if (this.storageTick > 1200) {
                this.stopTask("存储会话超时 (阶段=" + String.valueOf(this.storagePhase) + ")，停止任务.");
                return;
            }
            this.releaseForward();
            this.execMoves();
            switch (this.storagePhase.ordinal()) {
                case 1: {
                    this.onPlaceEc();
                    break;
                }
                case 2: {
                    this.onOpenEc();
                    break;
                }
                case 3: {
                    this.onPickBox();
                    break;
                }
                case 4: {
                    this.onCloseScreen();
                    break;
                }
                case 5: {
                    this.onPlaceBox();
                    break;
                }
                case 6: {
                    this.onOpenBox();
                    break;
                }
                case 7: {
                    this.onFillBox();
                    break;
                }
                case 8: {
                    if (this.freeSlots() <= this.freeSlotDump.get() && this.countElytraMain() > 0) {
                        this.supplyPauseDump = true;
                        this.resupplyRescan = true;
                        this.dumpNeeded = true;
                        this.info("补给暂停: 背包剩余 " + this.freeSlots() + " 槽，先回收补给盒、存鞘翅腾背包.", new Object[0]);
                        this.storagePhase = StoragePhase.MINE_BOX;
                        this.storageTick = 0;
                        break;
                    }
                    this.onTakeSupplies();
                    break;
                }
                case 9: {
                    this.onMineBox();
                    break;
                }
                case 10: {
                    this.onMineEc();
                    break;
                }
            }
        }
        catch (Throwable t) {
            this.error("存储会话异常: " + String.valueOf(t), new Object[0]);
            t.printStackTrace();
            this.stopTask("存储会话异常，停止任务.");
        }
    }

    private void onPlaceEc() {
        if (this.storageTick == 1) {
            net.minecraft.util.math.BlockPos existing;
            if (this.ecOpenFailCount == 0 && (existing = this.findNearbyEnderChest(5)) != null) {
                this.ecPos = existing;
                this.info("存储: 发现周围已有末影箱 " + String.valueOf(existing) + "，直接打开.", new Object[0]);
                this.storagePhase = StoragePhase.OPEN_EC;
                this.storageTick = 0;
                this.openRetry = 0;
                return;
            }
            int ecSlot = this.findItemSlot(net.minecraft.item.Items.ENDER_CHEST);
            if (ecSlot == -1) {
                this.stopTask("背包里没有末影箱，停止任务.");
                return;
            }
            this.ecBaseline = this.countItem(net.minecraft.item.Items.ENDER_CHEST);
            this.ensureItemSelected(ecSlot);
            this.placeCandidates = this.findPlaceSpots(null);
            if (this.placeCandidates.isEmpty() && this.waypoints != null && this.waypoints.p2 != null) {
                this.info("附近无空位，改用检测潜影贝的位置 " + String.valueOf(this.waypoints.p2) + " 附近放置.", new Object[0]);
                this.placeCandidates = this.findPlaceSpotsAt(this.waypoints.p2, null);
            }
            if (this.placeCandidates.isEmpty()) {
                this.stopTask("找不到放置末影箱的位置，停止任务.");
                return;
            }
            this.placeIndex = 0;
            this.lastPlaceClick = -100;
            this.placeClickAttempts = 0;
        }
        if (this.storageTick >= 4 && this.storageTick - this.lastPlaceClick >= 8) {
            if (this.placeIndex >= this.placeCandidates.size()) {
                this.stopTask("末影箱放置失败 (已尝试所有候选位置)，停止任务.");
                return;
            }
            net.minecraft.util.math.BlockPos spot = this.placeCandidates.get(this.placeIndex);
            if (this.mc.player.squaredDistanceTo((double)spot.getX() + 0.5, (double)spot.getY() + 0.5, (double)spot.getZ() + 0.5) > 16.0) {
                if (!PathManagers.get().isPathing()) {
                    PathManagers.get().moveTo(spot, false);
                }
                return;
            }
            this.lastPlaceClick = this.storageTick;
            ++this.placeClickAttempts;
            this.ecPos = spot;
            this.faceBlockForPlace(spot.down(), net.minecraft.util.math.Direction.UP);
            this.interactAt(spot.down());
            if (this.placeClickAttempts >= 2) {
                ++this.placeIndex;
                this.placeClickAttempts = 0;
            }
        }
        if (this.ecPos != null && this.isEnderChestBlock(this.mc.world.getBlockState(this.ecPos))) {
            PathManagers.get().stop();
            this.storagePhase = StoragePhase.OPEN_EC;
            this.storageTick = 0;
            this.openRetry = 0;
            return;
        }
        if (this.storageTick > 80) {
            this.stopTask("末影箱放置失败 (超时)，停止任务.");
        }
    }

    private net.minecraft.util.math.BlockPos findNearbyEnderChest(int radius) {
        net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
        net.minecraft.util.math.BlockPos p = this.mc.player.getBlockPos();
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dy = -2; dy <= 2; ++dy) {
                    m.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    if (!this.isEnderChestBlock(this.mc.world.getBlockState((net.minecraft.util.math.BlockPos)m))) continue;
                    return m.toImmutable();
                }
            }
        }
        return null;
    }

    private void onOpenEc() {
        if (this.storageTick == 1) {
            if (this.ecPos == null || !this.isEnderChestBlock(this.mc.world.getBlockState(this.ecPos))) {
                this.info("末影箱不在原位，重新放置.", new Object[0]);
                this.storagePhase = StoragePhase.PLACE_EC;
                this.storageTick = 0;
                return;
            }
            this.standingMovedOff = false;
        }
        if (!this.standingMovedOff && this.isStandingOnBlock(this.ecPos)) {
            net.minecraft.util.math.BlockPos off = this.findStandOffSpot(this.ecPos);
            if (off != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(off, false);
                this.info("存储: 玩家站在末影箱上，先走下来再打开.", new Object[0]);
            }
            if (this.isStandingOnBlock(this.ecPos)) {
                return;
            }
            this.standingMovedOff = true;
        }
        if (this.openRetry < 5 && this.storageTick == 1 + this.openRetry * 20) {
            this.faceBlockForPlace(this.ecPos, net.minecraft.util.math.Direction.UP);
            this.interactAt(this.ecPos);
            ++this.openRetry;
        }
        if (this.isContainerOpen()) {
            if (this.storageTick < 3) {
                return;
            }
            this.ecOpenFailCount = 0;
            this.storagePhase = StoragePhase.PICK_BOX;
            this.storageTick = 0;
        } else if (this.storageTick > 93) {
            if (this.isStandingOnBlock(this.ecPos)) {
                this.standingMovedOff = false;
                this.storageTick = 0;
                this.info("打开末影箱失败且玩家站在末影箱上，先走下来再重试.", new Object[0]);
                return;
            }
            this.warning("打开末影箱失败 (已尝试 5 次)，放置新的末影箱再试.", new Object[0]);
            ++this.ecOpenFailCount;
            this.storagePhase = StoragePhase.PLACE_EC;
            this.storageTick = 0;
            this.ecPos = null;
            this.openRetry = 0;
        }
    }

    private void onPickBox() {
        net.minecraft.screen.ScreenHandler sh = this.mc.player.currentScreenHandler;
        int rows = this.currentRows();
        if (sh == null || rows <= 0) {
            this.stopTask("末影箱界面异常，停止任务.");
            return;
        }
        if (this.storageTick < 3) {
            return;
        }
        if (this.pickStep == 1) {
            if (!this.moveIdle()) {
                return;
            }
            int emptyScreen = this.firstEmptyPlayerScreenSlot(sh, rows);
            if (emptyScreen != -1) {
                this.pickInvSlot = emptyScreen - rows * 9 + 9;
                this.moveQueue.add(new MoveOp(-1, emptyScreen));
                this.pickStep = 2;
            } else {
                this.moveQueue.add(new MoveOp(-1, this.pickEcSlot));
                this.pickStep = 3;
            }
            return;
        }
        if (this.pickStep == 2) {
            if (!this.moveIdle()) {
                return;
            }
            this.boxInventorySlot = this.pickInvSlot;
            this.boxFromInventory = false;
            this.pickStep = 0;
            this.afterClose = StoragePhase.PLACE_BOX;
            this.storagePhase = StoragePhase.CLOSE_SCREEN;
            this.storageTick = 0;
            return;
        }
        if (this.pickStep == 3) {
            if (!this.moveIdle()) {
                return;
            }
            this.pickStep = 0;
            if (this.boxPurposeSupply) {
                this.stopTask("背包无空位放潜影盒，无法补货，停止任务.");
                return;
            }
            this.stopTask("背包无空位放潜影盒，无法存鞘翅，停止任务.");
            return;
        }
        if (!this.moveIdle()) {
            return;
        }
        if (this.boxInventorySlot != -1) {
            int screenSlot = this.invSlotToScreen(rows, this.boxInventorySlot);
            if (this.firstEmptyContainerSlot(sh, rows) == -1) {
                this.stopTask("末影箱已满，无法归还潜影盒，停止任务.");
                return;
            }
            this.click(net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, screenSlot);
            this.boxInventorySlot = -1;
            return;
        }
        int nonEmpty = 0;
        StringBuilder items = new StringBuilder();
        for (int i = 0; i <= this.containerLast(rows); ++i) {
            net.minecraft.item.ItemStack st = sh.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            ++nonEmpty;
            if (items.length() >= 80) continue;
            items.append(st.getItem().getName().getString()).append("(").append(st.getCount()).append(") ");
        }
        this.info("存储: 末影箱行数=" + rows + " 非空槽=" + nonEmpty + " [" + String.valueOf(items) + "]", new Object[0]);
        if (this.dumpNeeded) {
            boolean bl = this.dumpNeeded = this.countElytraMain() > 0;
        }
        if (!this.dumpNeeded && !this.resupplyNeeded) {
            this.storagePhase = StoragePhase.MINE_EC;
            this.storageTick = 0;
            return;
        }
        if (this.dumpNeeded) {
            int bpBox = this.findBackpackBoxWithSpace();
            if (bpBox != -1) {
                this.boxInventorySlot = bpBox;
                this.boxFromInventory = true;
                this.pickInvSlot = bpBox;
                this.boxPurposeSupply = false;
                this.afterClose = StoragePhase.PLACE_BOX;
                this.storagePhase = StoragePhase.CLOSE_SCREEN;
                this.storageTick = 0;
                return;
            }
            int ecBox = this.findEcBoxWithSpace(sh, rows);
            if (ecBox != -1) {
                this.pickEcSlot = ecBox;
                this.boxPurposeSupply = false;
                this.pickStep = 1;
                this.moveQueue.add(new MoveOp(ecBox, -1));
                return;
            }
            if (this.freeSlots() == 0) {
                this.completeTask("背包与末影箱全部放满，任务完成.");
                return;
            }
            this.info("没有空位潜影盒，鞘翅留在背包.", new Object[0]);
            this.dumpNeeded = false;
        }
        if (this.resupplyNeeded) {
            if (this.resupplyRescan) {
                this.resupplyRescan = false;
                this.supplyScanStart = 0;
                this.info("存储: 存鞘翅腾出空间，从末影箱开头重新扫描补给.", new Object[0]);
            }
            if (this.supplyScanStart > this.containerLast(rows)) {
                if (this.anyDeficit()) {
                    this.stopTask("物资不足 (末影箱内已遍历完所有潜影盒)，停止任务.");
                    return;
                }
                this.info("存储: 补货遍历完成.", new Object[0]);
                this.resupplyNeeded = false;
                this.storagePhase = StoragePhase.MINE_EC;
                this.storageTick = 0;
                return;
            }
            List<SupplyRule> rules = this.parseSupplies();
            for (int i = this.supplyScanStart; i <= this.containerLast(rows); ++i) {
                net.minecraft.item.ItemStack st = sh.getSlot(i).getStack();
                if (!st.isEmpty()) {
                    if (!this.isShulkerBox(st)) {
                        if (!this.takeFailedItems.contains(st.getItem()) && this.needsItem(st.getItem())) {
                            this.supplyScanStart = i + 1;
                            this.click(net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, i);
                            this.info("存储: 从末影箱直接拿取散放物资 " + st.getItem().getName().getString() + ".", new Object[0]);
                        } else {
                            this.supplyScanStart = i + 1;
                        }
                        return;
                    }
                    boolean hasSupply = false;
                    for (SupplyRule r : rules) {
                        if (this.takeFailedItems.contains(r.item) || !this.needsItem(r.item) || !this.shulkerItems(st).contains(r.item)) continue;
                        hasSupply = true;
                        break;
                    }
                    if (hasSupply) {
                        this.supplyScanStart = i + 1;
                        this.pickEcSlot = i;
                        this.boxPurposeSupply = true;
                        this.pickStep = 1;
                        this.moveQueue.add(new MoveOp(i, -1));
                        return;
                    }
                    this.supplyScanStart = i + 1;
                    return;
                }
                this.supplyScanStart = i + 1;
            }
            this.supplyScanStart = this.containerLast(rows) + 1;
            return;
        }
        this.storagePhase = StoragePhase.MINE_EC;
        this.storageTick = 0;
    }

    private void onCloseScreen() {
        if (this.isContainerOpen()) {
            if (this.storageTick == 1) {
                this.mc.player.closeHandledScreen();
            }
            if (this.storageTick > 40) {
                this.stopTask("关闭容器界面超时，停止任务.");
                return;
            }
            return;
        }
        if (this.storageTick < 4) {
            return;
        }
        this.storagePhase = this.afterClose;
        this.afterClose = StoragePhase.NONE;
        this.storageTick = 0;
    }

    private void onPlaceBox() {
        if (this.storageTick == 1) {
            if (this.boxInventorySlot == -1) {
                this.stopTask("潜影盒槽位丢失，停止任务.");
                return;
            }
            this.boxInventorySlot = this.ensureItemSelected(this.boxInventorySlot);
            this.placeCandidates = this.findBoxPlaceSpots(this.ecPos);
            this.boxPlaceFloorFallback = false;
            if (this.placeCandidates.isEmpty()) {
                this.info("附近没有挂放位置 (空气+上方实心)，改用地板放置.", new Object[0]);
                this.placeCandidates = this.findBoxFloorSpots(this.ecPos);
                this.boxPlaceFloorFallback = true;
            }
            if (this.placeCandidates.isEmpty()) {
                this.stopTask("找不到放置潜影盒的位置 (需要空气格且上方有方块)，停止任务.");
                return;
            }
            this.placeIndex = 0;
            this.lastPlaceClick = -100;
            this.placeClickAttempts = 0;
        }
        if (this.storageTick >= 4 && this.storageTick - this.lastPlaceClick >= 8) {
            net.minecraft.util.math.BlockPos spot;
            if (this.placeIndex >= this.placeCandidates.size()) {
                if (!this.boxPlaceFloorFallback) {
                    this.info("挂放位置全部放置失败，改用地板放置.", new Object[0]);
                    this.placeCandidates = this.findBoxFloorSpots(this.ecPos);
                    this.boxPlaceFloorFallback = true;
                    this.placeIndex = 0;
                    this.placeClickAttempts = 0;
                    if (this.placeCandidates.isEmpty()) {
                        this.stopTask("潜影盒放置失败 (找不到地板放置位置)，停止任务.");
                        return;
                    }
                } else {
                    this.stopTask("潜影盒放置失败 (已尝试所有候选位置)，停止任务.");
                    return;
                }
            }
            if (this.mc.player.squaredDistanceTo((double)(spot = this.placeCandidates.get(this.placeIndex)).getX() + 0.5, (double)spot.getY() + 0.5, (double)spot.getZ() + 0.5) > 16.0) {
                if (!PathManagers.get().isPathing()) {
                    PathManagers.get().moveTo(spot, false);
                }
                return;
            }
            this.lastPlaceClick = this.storageTick;
            ++this.placeClickAttempts;
            this.boxPos = spot;
            this.boxBaseline = this.countBoxes();
            if (this.boxPlaceFloorFallback) {
                this.faceBlockForPlace(spot.down(), net.minecraft.util.math.Direction.UP);
                this.interactAt(spot.down(), net.minecraft.util.math.Direction.UP);
            } else {
                this.faceBlockForPlace(spot.up(), net.minecraft.util.math.Direction.DOWN);
                this.interactAt(spot.up(), net.minecraft.util.math.Direction.DOWN);
            }
            if (this.placeClickAttempts >= 2) {
                ++this.placeIndex;
                this.placeClickAttempts = 0;
            }
        }
        if (this.boxPos != null && this.isShulkerBlock(this.mc.world.getBlockState(this.boxPos))) {
            PathManagers.get().stop();
            this.storagePhase = StoragePhase.OPEN_BOX;
            this.storageTick = 0;
            return;
        }
        if (this.storageTick > 160) {
            this.stopTask("潜影盒放置失败 (超时)，停止任务.");
        }
    }

    private void onOpenBox() {
        if (this.storageTick == 1) {
            if (this.boxPos == null || !this.isShulkerBlock(this.mc.world.getBlockState(this.boxPos))) {
                this.warning("潜影盒不在原位，挖掉重来.", new Object[0]);
                this.storagePhase = StoragePhase.MINE_BOX;
                this.storageTick = 0;
                return;
            }
            this.standingMovedOff = false;
        }
        if (!this.standingMovedOff && this.isStandingOnBlock(this.boxPos)) {
            net.minecraft.util.math.BlockPos off = this.findStandOffSpot(this.boxPos);
            if (off != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(off, false);
                this.info("存储: 玩家站在潜影盒上，先走下来再打开.", new Object[0]);
            }
            if (this.isStandingOnBlock(this.boxPos)) {
                return;
            }
            this.standingMovedOff = true;
        }
        if (this.openRetry < 5 && this.storageTick == 1 + this.openRetry * 20) {
            this.lookAtBoxCenter();
            ++this.openRetry;
        }
        if (this.openRetry > 0 && this.boxOpenClickTick == -1 && this.isLookingAtBox()) {
            this.boxOpenClickTick = this.storageTick;
            Utils.rightClick();
        } else if (this.boxOpenClickTick != -1 && !this.isContainerOpen() && this.storageTick - this.boxOpenClickTick > 8) {
            this.boxOpenClickTick = -1;
        }
        if (this.isContainerOpen()) {
            if (this.storageTick < 3) {
                return;
            }
            this.storagePhase = this.boxPurposeSupply ? StoragePhase.TAKE_SUPPLIES : StoragePhase.FILL_BOX;
            this.storageTick = 0;
            this.openRetry = 0;
            this.boxOpenClickTick = -1;
        } else if (this.storageTick > 93) {
            if (this.isStandingOnBlock(this.boxPos)) {
                this.standingMovedOff = false;
                this.storageTick = 0;
                this.openRetry = 0;
                this.boxOpenClickTick = -1;
                this.info("打开潜影盒失败且玩家站在潜影盒上，先走下来再重试.", new Object[0]);
                return;
            }
            this.warning("打开潜影盒失败 (已尝试 5 次)，挖掉重来.", new Object[0]);
            this.storagePhase = StoragePhase.MINE_BOX;
            this.storageTick = 0;
            this.openRetry = 0;
            this.boxOpenClickTick = -1;
        }
    }

    private void onFillBox() {
        net.minecraft.screen.ScreenHandler sh = this.mc.player.currentScreenHandler;
        int rows = this.currentRows();
        if (sh == null || rows <= 0) {
            this.stopTask("潜影盒界面异常，停止任务.");
            return;
        }
        if (this.storageTick < 3) {
            return;
        }
        if (!this.moveIdle()) {
            return;
        }
        if (this.fillIndex == -1) {
            this.fillIndex = this.playerFirst(rows);
            this.info("存储: 开始往潜影盒存鞘翅.", new Object[0]);
        }
        if (this.fillStuckSlot >= 0) {
            if (sh.getSlot(this.fillStuckSlot).getStack().getItem() == net.minecraft.item.Items.ELYTRA) {
                ++this.fillStuckTicks;
                if (this.fillStuckTicks > 20) {
                    this.fillIndex = -1;
                    this.fillStuckSlot = -1;
                    this.fillStuckTicks = 0;
                    this.info("存储: 潜影盒已满.", new Object[0]);
                    this.afterClose = StoragePhase.MINE_BOX;
                    this.storagePhase = StoragePhase.CLOSE_SCREEN;
                    this.storageTick = 0;
                }
                return;
            }
            this.fillStuckSlot = -1;
            this.fillStuckTicks = 0;
        }
        int src = -1;
        for (int s = this.fillIndex; s <= this.playerLast(rows); ++s) {
            if (sh.getSlot(s).getStack().getItem() != net.minecraft.item.Items.ELYTRA) continue;
            src = s;
            break;
        }
        if (src == -1) {
            this.fillIndex = -1;
            this.info("存储: 鞘翅已全部存入潜影盒.", new Object[0]);
            this.afterClose = StoragePhase.MINE_BOX;
            this.storagePhase = StoragePhase.CLOSE_SCREEN;
            this.storageTick = 0;
            return;
        }
        this.fillIndex = src + 1;
        this.fillStuckSlot = src;
        this.fillStuckTicks = 0;
        this.click(net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, src);
    }

    private void onTakeSupplies() {
        net.minecraft.screen.ScreenHandler sh = this.mc.player.currentScreenHandler;
        int rows = this.currentRows();
        if (sh == null || rows <= 0) {
            this.stopTask("潜影盒界面异常，停止任务.");
            return;
        }
        if (this.storageTick < 3) {
            return;
        }
        if (!this.moveIdle()) {
            return;
        }
        SupplyRule target = null;
        List<SupplyRule> rules = this.parseSupplies();
        for (SupplyRule rule : rules) {
            if (this.takeFailedItems.contains(rule.item) || rule.batch - this.countItem(rule.item) <= 0) continue;
            for (int c = 0; c <= this.containerLast(rows); ++c) {
                net.minecraft.item.ItemStack st = sh.getSlot(c).getStack();
                if (st.isEmpty() || st.getItem() != rule.item) continue;
                target = rule;
                break;
            }
            if (target == null) continue;
            break;
        }
        if (target != null) {
            for (int c = 0; c <= this.containerLast(rows); ++c) {
                net.minecraft.item.ItemStack st = sh.getSlot(c).getStack();
                if (st.isEmpty() || st.getItem() != target.item) continue;
                if (this.takeStuckSlot == c) {
                    ++this.takeStuckTicks;
                    if (this.takeStuckTicks > 30) {
                        this.takeStuckSlot = -1;
                        this.takeStuckTicks = 0;
                        int boxFree = this.firstEmptyContainerSlot(sh, rows);
                        if (boxFree != -1) {
                            int elytra = this.findUnwornElytraSlot(sh, rows);
                            if (elytra != -1) {
                                this.moveQueue.add(new MoveOp(elytra, boxFree));
                                this.info("存储: 背包空间不足，先把鞘翅放入盒子腾出空位.", new Object[0]);
                            } else {
                                int junk = this.findJunkSlotInPlayer(sh, rows, rules);
                                if (junk != -1) {
                                    this.moveQueue.add(new MoveOp(junk, boxFree));
                                    this.info("存储: 背包已满，先把杂物放入盒子腾出空位.", new Object[0]);
                                } else {
                                    this.takeFailedItems.add(target.item);
                                    this.info("存储: 背包无空位且无杂物可腾，补货 " + target.item.getName().getString() + " 跳过.", new Object[0]);
                                }
                            }
                        } else {
                            this.takeFailedItems.add(target.item);
                            this.info("存储: 盒子已满无法腾位，补货 " + target.item.getName().getString() + " 跳过.", new Object[0]);
                        }
                    }
                    return;
                }
                this.takeStuckSlot = c;
                this.takeStuckTicks = 0;
                this.click(net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, c);
                return;
            }
            this.takeStuckSlot = -1;
            this.takeStuckTicks = 0;
        }
        ++this.takeEmptyTicks;
        if (this.takeEmptyTicks < 10) {
            return;
        }
        this.takeEmptyTicks = 0;
        this.takeStuckSlot = -1;
        this.takeStuckTicks = 0;
        this.info("存储: 本盒物资已拿取完毕 (所有能拿且需要的).", new Object[0]);
        this.afterClose = StoragePhase.MINE_BOX;
        this.storagePhase = StoragePhase.CLOSE_SCREEN;
        this.storageTick = 0;
    }

    private void onMineBox() {
        net.minecraft.block.BlockState s = this.mc.world.getBlockState(this.boxPos);
        if (this.isShulkerBlock(s)) {
            if (this.storageTick == 1) {
                this.pickupWaitStart = -1;
                PathManagers.get().stop();
                if (this.findSilkTouchSlot() == -1) {
                    this.stopTask("需要精准采集工具才能回收潜影盒，停止任务.");
                    return;
                }
                PathManagers.get().mine(s.getBlock());
                this.info("用 寻路 (精准采集) 挖掘潜影盒.", new Object[0]);
            } else if (this.storageTick > 600) {
                this.stopTask("寻路 挖掘潜影盒超时，停止任务.");
            }
            return;
        }
        if (this.pickupWaitStart == -1) {
            this.pickupWaitStart = this.storageTick;
            PathManagers.get().stop();
        }
        if (this.countBoxes() >= this.boxBaseline) {
            this.boxInventorySlot = this.pickInvSlot >= 0 && this.pickInvSlot < 36 && this.isShulkerBox((net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(this.pickInvSlot)) ? this.pickInvSlot : this.findBackpackShulkerBox();
            this.storagePhase = this.dumpNeeded || this.resupplyNeeded ? StoragePhase.OPEN_EC : StoragePhase.MINE_EC;
            this.storageTick = 0;
            this.pickupWaitStart = -1;
            return;
        }
        if (this.storageTick > this.pickupWaitStart + 200) {
            this.warning("潜影盒掉落未拾取，继续任务.", new Object[0]);
            this.boxInventorySlot = -1;
            this.boxFromInventory = false;
            this.storagePhase = this.dumpNeeded || this.resupplyNeeded ? StoragePhase.OPEN_EC : StoragePhase.MINE_EC;
            this.storageTick = 0;
            this.pickupWaitStart = -1;
            return;
        }
        net.minecraft.entity.ItemEntity it = this.findItemEntityNear(this.boxPos, 8.0);
        if (it != null && !PathManagers.get().isPathing()) {
            PathManagers.get().moveTo(it.getBlockPos(), false);
        }
    }

    private void onMineEc() {
        net.minecraft.block.BlockState s = this.mc.world.getBlockState(this.ecPos);
        if (this.isEnderChestBlock(s)) {
            if (this.storageTick == 1) {
                this.pickupWaitStart = -1;
                PathManagers.get().stop();
                if (this.findSilkTouchSlot() == -1) {
                    this.stopTask("需要精准采集工具才能回收末影箱，停止任务.");
                    return;
                }
                PathManagers.get().mine(net.minecraft.block.Blocks.ENDER_CHEST);
                this.info("用 寻路 (精准采集) 挖掘末影箱.", new Object[0]);
            } else if (this.storageTick > 600) {
                this.stopTask("寻路 挖掘末影箱超时，停止任务.");
            }
            return;
        }
        if (this.pickupWaitStart == -1) {
            this.pickupWaitStart = this.storageTick;
            PathManagers.get().stop();
        }
        if (this.countItem(net.minecraft.item.Items.ENDER_CHEST) >= this.ecBaseline) {
            this.afterStorageSession();
            return;
        }
        if (this.storageTick > this.pickupWaitStart + 400) {
            this.warning("末影箱掉落未拾取 (挖末影箱需要精准采集)，继续任务.", new Object[0]);
            this.afterStorageSession();
            return;
        }
        net.minecraft.entity.ItemEntity it = this.findItemEntityNear(this.ecPos, 8.0);
        if (it != null && !PathManagers.get().isPathing()) {
            PathManagers.get().moveTo(it.getBlockPos(), false);
        }
    }

    private void afterStorageSession() {
        this.storagePhase = StoragePhase.NONE;
        this.storageTick = 0;
        this.pickupWaitStart = -1;
        if (this.supplyLandingResume) {
            this.supplyLandingResume = false;
            if (this.countFireworks() > 4) {
                this.supplyFailCount = 0;
            }
            this.info("燃料补给完成，继续飞向当前目标.", new Object[0]);
            this.firstFireworkDone = false;
            this.landingRecover = false;
            this.state = State.RISING;
            this.stateTick = 0;
            return;
        }
        if (this.state == State.COLLECTING && this.collectStep == CollectStep.EQUIP) {
            this.info("船旁存储会话结束，先走回降落点再起飞.", new Object[0]);
            this.advance(CollectStep.EXIT_P1);
        } else {
            this.relaunchAfterShip();
        }
    }

    private void click(net.minecraft.screen.slot.SlotActionType type, int slotId) {
        net.minecraft.screen.ScreenHandler sh = this.mc.player.currentScreenHandler;
        if (sh == null) {
            return;
        }
        this.mc.interactionManager.clickSlot(sh.syncId, slotId, 0, type, (net.minecraft.entity.player.PlayerEntity)this.mc.player);
    }

    private void interactAt(net.minecraft.util.math.BlockPos pos) {
        this.interactAt(pos, net.minecraft.util.math.Direction.UP);
    }

    private void interactAt(net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction face) {
        net.minecraft.util.hit.BlockHitResult hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter((net.minecraft.util.math.Vec3i)pos), face, pos, false);
        this.mc.interactionManager.interactBlock(this.mc.player, net.minecraft.util.Hand.MAIN_HAND, hit);
    }

    private void faceBlockForPlace(net.minecraft.util.math.BlockPos target, net.minecraft.util.math.Direction face) {
        double cx = (double)target.getX() + 0.5 + (double)face.getOffsetX() * 0.5;
        double cy = (double)target.getY() + 0.5 + (double)face.getOffsetY() * 0.5;
        double cz = (double)target.getZ() + 0.5 + (double)face.getOffsetZ() * 0.5;
        double dx = cx - this.mc.player.getX();
        double dy = cy - (this.mc.player.getY() + (double)this.mc.player.getStandingEyeHeight());
        double dz = cz - this.mc.player.getZ();
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        this.mc.player.setYaw(yaw);
        this.mc.player.setPitch(pitch);
        this.mc.player.networkHandler.sendPacket((net.minecraft.network.packet.Packet)new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, this.mc.player.isOnGround(), false));
    }

    private void lookAtBoxCenter() {
        double cx = (double)this.boxPos.getX() + 0.5;
        double cy = (double)this.boxPos.getY() + 0.5;
        double cz = (double)this.boxPos.getZ() + 0.5;
        double dx = cx - this.mc.player.getX();
        double dy = cy - (this.mc.player.getY() + (double)this.mc.player.getStandingEyeHeight());
        double dz = cz - this.mc.player.getZ();
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        this.mc.player.setYaw(yaw);
        this.mc.player.setPitch(pitch);
        this.mc.player.networkHandler.sendPacket((net.minecraft.network.packet.Packet)new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, this.mc.player.isOnGround(), false));
    }

    private boolean isLookingAtBox() {
        net.minecraft.util.hit.BlockHitResult bhr;
        net.minecraft.util.hit.HitResult hit = this.mc.crosshairTarget;
        return hit instanceof net.minecraft.util.hit.BlockHitResult && (bhr = (net.minecraft.util.hit.BlockHitResult)hit).getBlockPos().equals(this.boxPos);
    }

    private boolean isStandingOnBlock(net.minecraft.util.math.BlockPos pos) {
        if (pos == null) {
            return false;
        }
        net.minecraft.util.math.BlockPos feet = this.mc.player.getBlockPos();
        return feet.getX() == pos.getX() && feet.getY() == pos.getY() + 1 && feet.getZ() == pos.getZ();
    }

    private net.minecraft.util.math.BlockPos findStandOffSpot(net.minecraft.util.math.BlockPos pos) {
        if (pos == null) {
            return null;
        }
        net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                if (dx == 0 && dz == 0) continue;
                m.set(pos.getX() + dx, pos.getY() + 1, pos.getZ() + dz);
                if (!this.mc.world.getBlockState((net.minecraft.util.math.BlockPos)m).isReplaceable() || this.mc.world.getBlockState(m.down()).isReplaceable()) continue;
                return m.toImmutable();
            }
        }
        return null;
    }

    private boolean isContainerOpen() {
        net.minecraft.screen.ScreenHandler sh = this.mc.player.currentScreenHandler;
        return sh != null && !(sh instanceof net.minecraft.screen.PlayerScreenHandler);
    }

    private int currentRows() {
        net.minecraft.screen.ScreenHandler sh = this.mc.player.currentScreenHandler;
        if (sh instanceof net.minecraft.screen.GenericContainerScreenHandler) {
            net.minecraft.screen.GenericContainerScreenHandler g = (net.minecraft.screen.GenericContainerScreenHandler)sh;
            return g.getRows();
        }
        if (sh instanceof net.minecraft.screen.ShulkerBoxScreenHandler) {
            return 3;
        }
        return -1;
    }

    private int containerLast(int rows) {
        return rows * 9 - 1;
    }

    private int playerFirst(int rows) {
        return rows * 9;
    }

    private int playerLast(int rows) {
        return rows * 9 + 35;
    }

    private int mainLast(int rows) {
        return rows * 9 + 26;
    }

    private int hotbarScreen(int rows, int invSlot) {
        return rows * 9 + 27 + invSlot;
    }

    private int ensureItemSelected(int invSlot) {
        if (invSlot <= 8) {
            InvUtils.swap((int)invSlot, (boolean)false);
            return invSlot;
        }
        InvUtils.move().from(invSlot).toHotbar(0);
        InvUtils.swap((int)0, (boolean)false);
        return 0;
    }

    private void execMoves() {
        if (this.moveState == 0) {
            if (this.moveQueue.isEmpty()) {
                return;
            }
            MoveOp op = this.moveQueue.poll();
            this.moveSrc = op.source;
            this.moveDst = op.target;
            if (this.moveSrc == -1) {
                this.click(net.minecraft.screen.slot.SlotActionType.PICKUP, this.moveDst);
                this.moveState = 2;
            } else {
                this.click(net.minecraft.screen.slot.SlotActionType.PICKUP, this.moveSrc);
                this.moveState = this.moveDst == -1 ? 3 : 1;
            }
            return;
        }
        net.minecraft.screen.ScreenHandler sh = this.mc.player.currentScreenHandler;
        if (sh == null) {
            return;
        }
        net.minecraft.item.ItemStack cursor = sh.getCursorStack();
        if (this.moveState == 3) {
            if (!cursor.isEmpty()) {
                this.moveState = 0;
            }
        } else if (this.moveState == 1) {
            if (!cursor.isEmpty()) {
                this.click(net.minecraft.screen.slot.SlotActionType.PICKUP, this.moveDst);
                this.moveState = 2;
            }
        } else if (this.moveState == 2 && cursor.isEmpty()) {
            this.moveState = 0;
        }
    }

    private boolean moveIdle() {
        return this.moveState == 0 && this.moveQueue.isEmpty();
    }

    private int invSlotToScreen(int rows, int invSlot) {
        return invSlot <= 8 ? this.hotbarScreen(rows, invSlot) : rows * 9 + (invSlot - 9);
    }

    private int firstEmptyContainerSlot(net.minecraft.screen.ScreenHandler sh, int rows) {
        for (int i = 0; i <= this.containerLast(rows); ++i) {
            if (!sh.getSlot(i).getStack().isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private int firstEmptyPlayerScreenSlot(net.minecraft.screen.ScreenHandler sh, int rows) {
        for (int i = this.playerFirst(rows); i <= this.playerLast(rows); ++i) {
            if (!sh.getSlot(i).getStack().isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private int findMergeSlotInPlayer(net.minecraft.screen.ScreenHandler sh, int rows, net.minecraft.item.Item item) {
        for (int i = this.playerFirst(rows); i <= this.playerLast(rows); ++i) {
            net.minecraft.item.ItemStack st = sh.getSlot(i).getStack();
            if (st.getItem() != item || st.getCount() >= st.getMaxCount()) continue;
            return i;
        }
        return -1;
    }

    private int findEcBoxWithSpace(net.minecraft.screen.ScreenHandler sh, int rows) {
        for (int i = 0; i <= this.containerLast(rows); ++i) {
            net.minecraft.item.ItemStack st = sh.getSlot(i).getStack();
            if (!this.isShulkerBox(st) || this.shulkerUsedSlots(st) >= 27) continue;
            return i;
        }
        return -1;
    }

    private int findBackpackBoxWithSpace() {
        for (int i = 0; i < 36; ++i) {
            net.minecraft.item.ItemStack st = (net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i);
            if (!this.isShulkerBox(st) || this.shulkerUsedSlots(st) >= 27) continue;
            return i;
        }
        return -1;
    }

    private int findUnwornElytraSlot(net.minecraft.screen.ScreenHandler sh, int rows) {
        for (int i = this.playerFirst(rows); i <= this.playerLast(rows); ++i) {
            if (sh.getSlot(i).getStack().getItem() != net.minecraft.item.Items.ELYTRA) continue;
            return i;
        }
        return -1;
    }

    private int findJunkSlotInPlayer(net.minecraft.screen.ScreenHandler sh, int rows, List<SupplyRule> rules) {
        HashSet<net.minecraft.item.Item> keep = new HashSet<net.minecraft.item.Item>();
        for (SupplyRule r : rules) {
            keep.add(r.item);
        }
        keep.add(net.minecraft.item.Items.ELYTRA);
        keep.add(net.minecraft.item.Items.ENDER_CHEST);
        int silkSlot = this.findSilkTouchSlot();
        for (int i = this.playerFirst(rows); i <= this.playerLast(rows); ++i) {
            net.minecraft.item.ItemStack st = sh.getSlot(i).getStack();
            if (st.isEmpty() || this.isShulkerBox(st) || keep.contains(st.getItem()) || silkSlot != -1 && i == this.invSlotToScreen(rows, silkSlot)) continue;
            return i;
        }
        return -1;
    }

    private boolean isShulkerBox(net.minecraft.item.ItemStack stack) {
        net.minecraft.item.BlockItem bi;
        net.minecraft.item.Item v17922 = stack.getItem();
        return v17922 instanceof net.minecraft.item.BlockItem && (bi = (net.minecraft.item.BlockItem)v17922).getBlock() instanceof net.minecraft.block.ShulkerBoxBlock;
    }

    private boolean isShulkerBlock(net.minecraft.block.BlockState state) {
        return state.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock;
    }

    private boolean isEnderChestBlock(net.minecraft.block.BlockState state) {
        return state.getBlock() == net.minecraft.block.Blocks.ENDER_CHEST;
    }

    private int shulkerUsedSlots(net.minecraft.item.ItemStack box) {
        net.minecraft.component.type.ContainerComponent container = (net.minecraft.component.type.ContainerComponent)box.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container == null) {
            return 0;
        }
        return (int)container.streamNonEmpty().count();
    }

    private Set<net.minecraft.item.Item> shulkerItems(net.minecraft.item.ItemStack box) {
        HashSet<net.minecraft.item.Item> out = new HashSet<net.minecraft.item.Item>();
        net.minecraft.component.type.ContainerComponent container = (net.minecraft.component.type.ContainerComponent)box.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container == null) {
            return out;
        }
        for (net.minecraft.item.ItemStack st : container.streamNonEmpty().toList()) {
            out.add(st.getItem());
        }
        return out;
    }

    private List<net.minecraft.util.math.BlockPos> findPlaceSpots(net.minecraft.util.math.BlockPos exclude) {
        return this.findPlaceSpotsAt(this.mc.player.getBlockPos(), exclude);
    }

    private List<net.minecraft.util.math.BlockPos> findPlaceSpotsAt(net.minecraft.util.math.BlockPos center, net.minecraft.util.math.BlockPos exclude) {
        ArrayList<net.minecraft.util.math.BlockPos> out = new ArrayList<net.minecraft.util.math.BlockPos>();
        net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dy = 0; dy <= 2; ++dy) {
                    net.minecraft.block.BlockState s;
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (exclude != null && Math.abs(m.getX() - exclude.getX()) <= 1 && Math.abs(m.getZ() - exclude.getZ()) <= 1 && Math.abs(m.getY() - exclude.getY()) <= 1 || !(s = this.mc.world.getBlockState((net.minecraft.util.math.BlockPos)m)).isReplaceable() || !this.mc.world.getBlockState(m.down()).isSolidBlock((net.minecraft.world.BlockView)this.mc.world, m.down())) continue;
                    out.add(m.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> this.mc.player.squaredDistanceTo((double)p.getX() + 0.5, (double)p.getY() + 0.5, (double)p.getZ() + 0.5)));
        return out;
    }

    private List<net.minecraft.util.math.BlockPos> findBoxPlaceSpots(net.minecraft.util.math.BlockPos exclude) {
        ArrayList<net.minecraft.util.math.BlockPos> out = new ArrayList<net.minecraft.util.math.BlockPos>();
        net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
        net.minecraft.util.math.BlockPos center = this.mc.player.getBlockPos();
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dy = 0; dy <= 2; ++dy) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (exclude != null && Math.abs(m.getX() - exclude.getX()) <= 1 && Math.abs(m.getZ() - exclude.getZ()) <= 1 && Math.abs(m.getY() - exclude.getY()) <= 1 || !this.mc.world.getBlockState((net.minecraft.util.math.BlockPos)m).isReplaceable() || !this.mc.world.getBlockState(m.up()).isSolidBlock((net.minecraft.world.BlockView)this.mc.world, m.up())) continue;
                    out.add(m.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> this.mc.player.squaredDistanceTo((double)p.getX() + 0.5, (double)p.getY() + 0.5, (double)p.getZ() + 0.5)));
        return out;
    }

    private List<net.minecraft.util.math.BlockPos> findBoxFloorSpots(net.minecraft.util.math.BlockPos exclude) {
        ArrayList<net.minecraft.util.math.BlockPos> out = new ArrayList<net.minecraft.util.math.BlockPos>();
        net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
        net.minecraft.util.math.BlockPos center = this.mc.player.getBlockPos();
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dy = 0; dy <= 2; ++dy) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (exclude != null && Math.abs(m.getX() - exclude.getX()) <= 1 && Math.abs(m.getZ() - exclude.getZ()) <= 1 && Math.abs(m.getY() - exclude.getY()) <= 1 || !this.mc.world.getBlockState((net.minecraft.util.math.BlockPos)m).isReplaceable() || !this.mc.world.getBlockState(m.down()).isSolidBlock((net.minecraft.world.BlockView)this.mc.world, m.down()) || !this.mc.world.getBlockState(m.up()).isReplaceable()) continue;
                    out.add(m.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> this.mc.player.squaredDistanceTo((double)p.getX() + 0.5, (double)p.getY() + 0.5, (double)p.getZ() + 0.5)));
        return out;
    }

    private net.minecraft.entity.ItemEntity findItemEntityNear(net.minecraft.util.math.BlockPos pos, double radius) {
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(pos).expand(radius);
        net.minecraft.entity.ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.entity.Entity entity : this.mc.world.getOtherEntities(null, box, e -> true)) {
            net.minecraft.entity.ItemEntity item;
            double d;
            if (!(entity instanceof net.minecraft.entity.ItemEntity) || !((d = (item = (net.minecraft.entity.ItemEntity)entity).squaredDistanceTo((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5)) < bestD)) continue;
            bestD = d;
            best = item;
        }
        return best;
    }

    private List<SupplyRule> parseSupplies() {
        ArrayList<SupplyRule> out = new ArrayList<SupplyRule>();
        for (String entry : (List<String>)this.supplies.get()) {
            String[] p = entry.split(";");
            if (p.length < 3) {
                this.warning("物资配置格式错误: " + entry, new Object[0]);
                continue;
            }
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse((String)p[0].trim());
            if (id == null) {
                this.warning("未知物品: " + p[0], new Object[0]);
                continue;
            }
            net.minecraft.item.Item supplyItem = net.minecraft.registry.Registries.ITEM.get(id);
            if (supplyItem == net.minecraft.item.Items.AIR) {
                this.warning("未知物品: " + p[0], new Object[0]);
                continue;
            }
            try {
                out.add(new SupplyRule(supplyItem, Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())));
            }
            catch (NumberFormatException e) {
                this.warning("物资数量格式错误: " + entry, new Object[0]);
            }
        }
        return out;
    }

    private boolean anyDeficit() {
        for (SupplyRule r : this.parseSupplies()) {
            if (this.countItem(r.item) >= r.min) continue;
            return true;
        }
        return false;
    }

    private boolean needsItem(net.minecraft.item.Item item) {
        for (SupplyRule r : this.parseSupplies()) {
            if (r.item != item || this.countItem(item) >= r.batch) continue;
            return true;
        }
        return false;
    }

    private int countItem(net.minecraft.item.Item item) {
        int n = 0;
        for (int i = 0; i < 36; ++i) {
            net.minecraft.item.ItemStack st = (net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i);
            if (st.getItem() != item) continue;
            n += st.getCount();
        }
        return n;
    }

    private int countElytraMain() {
        return this.countItem(net.minecraft.item.Items.ELYTRA);
    }

    private int countBoxes() {
        int n = 0;
        for (int i = 0; i < 36; ++i) {
            if (!this.isShulkerBox((net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i))) continue;
            ++n;
        }
        return n;
    }

    private int findItemSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 36; ++i) {
            if (((net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i)).getItem() != item) continue;
            return i;
        }
        return -1;
    }

    private int findSilkTouchSlot() {
        net.minecraft.registry.Registry reg = this.mc.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        net.minecraft.registry.entry.RegistryEntry entry = reg.getEntry(((net.minecraft.enchantment.Enchantment)reg.get(net.minecraft.enchantment.Enchantments.SILK_TOUCH)));
        for (int i = 0; i < 36; ++i) {
            net.minecraft.item.ItemStack st = (net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i);
            if (net.minecraft.enchantment.EnchantmentHelper.getLevel((net.minecraft.registry.entry.RegistryEntry)entry, (net.minecraft.item.ItemStack)st) <= 0) continue;
            return i;
        }
        return -1;
    }

    private int findBackpackShulkerBox() {
        for (int i = 0; i < 36; ++i) {
            if (!this.isShulkerBox((net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i))) continue;
            return i;
        }
        return -1;
    }

    private int freeSlots() {
        int n = 0;
        for (int i = 0; i < 36; ++i) {
            if (!((net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i)).isEmpty()) continue;
            ++n;
        }
        return n;
    }

    private boolean reached(net.minecraft.util.math.BlockPos pos, double tolerance) {
        double dz;
        if (this.stateTick < 6) {
            return false;
        }
        if (PathManagers.get().isPathing()) {
            return false;
        }
        double dx = this.mc.player.getX() - ((double)pos.getX() + 0.5);
        return dx * dx + (dz = this.mc.player.getZ() - ((double)pos.getZ() + 0.5)) * dz <= tolerance * tolerance;
    }

    private float endIslandHeightValue(int x, int z) {
        int i = x / 2;
        int j = z / 2;
        int k = x % 2;
        int l = z % 2;
        float f = 100.0f - (float)Math.sqrt((double)x * (double)x + (double)z * (double)z) * 8.0f;
        f = ElytraCollector.clampIsland(f);
        for (int rx = -12; rx <= 12; ++rx) {
            for (int rz = -12; rz <= 12; ++rz) {
                long k1 = i + rx;
                long l1 = j + rz;
                if (this.islandNoise == null || k1 * k1 + l1 * l1 <= 4096L || !(this.islandNoise.sample2D(k1, l1) < (double)-0.9f)) continue;
                float f1 = (Math.abs((float)k1) * 3439.0f + Math.abs((float)l1) * 147.0f) % 13.0f + 9.0f;
                float f2 = k - rx * 2;
                float f3 = l - rz * 2;
                float f4 = 100.0f - (float)Math.sqrt(f2 * f2 + f3 * f3) * f1;
                f4 = ElytraCollector.clampIsland(f4);
                f = Math.max(f, f4);
            }
        }
        return f;
    }

    private static float clampIsland(float value) {
        if (value < -100.0f) {
            return -100.0f;
        }
        return Math.min(value, 80.0f);
    }

    private double endIslandDensity(int blockX, int blockZ) {
        return ((double)this.endIslandHeightValue(blockX / 8, blockZ / 8) - 8.0) / 128.0;
    }

    private boolean isEndCityBiome(int chunkX, int chunkZ) {
        int blockX = chunkX * 16 + 8;
        long sx = blockX >> 4;
        int blockZ = chunkZ * 16 + 8;
        long sz = blockZ >> 4;
        if (sx * sx + sz * sz <= 4096L) {
            return false;
        }
        return this.endIslandDensity(blockX, blockZ) >= -0.0625;
    }

    private boolean cityTerrainHigh() {
        int[][] cols;
        int chunkX = this.current.chunk.getX();
        int chunkZ = this.current.chunk.getZ();
        ChunkRand r = new ChunkRand();
        r.setSeed(this.worldSeed);
        long a = r.nextLong();
        long b = r.nextLong();
        r.setSeed((long)chunkX * a ^ (long)chunkZ * b ^ this.worldSeed);
        BlockRotation rotation = BlockRotation.getRandom(r);
        int xOff = 5;
        int zOff = 5;
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
        for (int[] c : cols = new int[][]{{posX, posZ}, {posX, posZ + zOff}, {posX + xOff, posZ}, {posX + xOff, posZ + zOff}}) {
            if (this.mc.world.getChunkManager().isChunkLoaded(c[0] >> 4, c[1] >> 4)) continue;
            return true;
        }
        int h1 = this.mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, cols[0][0], cols[0][1]);
        int h2 = this.mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, cols[1][0], cols[1][1]);
        int h3 = this.mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, cols[2][0], cols[2][1]);
        int h4 = this.mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, cols[3][0], cols[3][1]);
        int min = Math.min(Math.min(h1, h2), Math.min(h3, h4));
        if ((this.debugSetting.get()).booleanValue()) {
            this.debugLog("城=(" + chunkX + "," + chunkZ + ") 旋转=" + String.valueOf(rotation) + " 最小=" + min);
            this.debugColumn(posX, posZ, h1);
            this.debugColumn(posX, posZ + zOff, h2);
            this.debugColumn(posX + xOff, posZ, h3);
            this.debugColumn(posX + xOff, posZ + zOff, h4);
        }
        return min >= 60;
    }

    private void debugColumn(int x, int z, int realHeight) {
        float rawF = this.endIslandHeightValue(x / 8, z / 8);
        double e = ((double)rawF - 8.0) / 128.0;
        int vanilla = this.vanillaWorldSurfaceWg(x, z);
        this.debugLog("列(" + x + "," + z + ") 实际=" + realHeight + " 原版=" + vanilla + " E=" + String.format("%.4f", e) + " 原始=" + String.format("%.1f", Float.valueOf(rawF)));
    }

    private int vanillaWorldSurfaceWg(int x, int z) {
        if (this.base3dNoise == null) {
            return 0;
        }
        int x0 = Math.floorDiv(x, 4) * 4;
        int z0 = Math.floorDiv(z, 4) * 4;
        double xf = (double)(x - x0) / 4.0;
        double zf = (double)(z - z0) / 4.0;
        double e00 = ((double)this.endIslandHeightValue(x0 / 8, z0 / 8) - 8.0) / 128.0;
        double e10 = ((double)this.endIslandHeightValue((x0 + 4) / 8, z0 / 8) - 8.0) / 128.0;
        double e01 = ((double)this.endIslandHeightValue(x0 / 8, (z0 + 4) / 8) - 8.0) / 128.0;
        double e11 = ((double)this.endIslandHeightValue((x0 + 4) / 8, (z0 + 4) / 8) - 8.0) / 128.0;
        double n00 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0, 256, z0));
        double n10 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0 + 4, 256, z0));
        double n01 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0, 256, z0 + 4));
        double n11 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0 + 4, 256, z0 + 4));
        for (int cell = 63; cell >= 0; --cell) {
            int yb = cell * 4;
            double b00 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0, yb, z0));
            double b10 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0 + 4, yb, z0));
            double b01 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0, yb, z0 + 4));
            double b11 = this.base3dNoise.sample((net.minecraft.world.gen.densityfunction.DensityFunction.NoisePos)new net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos(x0 + 4, yb, z0 + 4));
            double c000 = ElytraCollector.endSlide(e00 + b00, yb);
            double c010 = ElytraCollector.endSlide(e00 + n00, yb + 4);
            double c100 = ElytraCollector.endSlide(e10 + b10, yb);
            double c110 = ElytraCollector.endSlide(e10 + n10, yb + 4);
            double c001 = ElytraCollector.endSlide(e01 + b01, yb);
            double c011 = ElytraCollector.endSlide(e01 + n01, yb + 4);
            double c101 = ElytraCollector.endSlide(e11 + b11, yb);
            double c111 = ElytraCollector.endSlide(e11 + n11, yb + 4);
            for (int y = yb + 3; y >= yb; --y) {
                double c;
                double yf = (double)(y - yb) / 4.0;
                double x0z0 = c000 + yf * (c010 - c000);
                double x1z0 = c100 + yf * (c110 - c100);
                double z0v = x0z0 + xf * (x1z0 - x0z0);
                double x0z1 = c001 + yf * (c011 - c001);
                double x1z1 = c101 + yf * (c111 - c101);
                double z1v = x0z1 + xf * (x1z1 - x0z1);
                double v = z0v + zf * (z1v - z0v);
                double d = 0.64 * v;
                c = d < -1.0 ? -1.0 : (d > 1.0 ? 1.0 : d);
                double d2 = c;
                if (!(c / 2.0 - c * c * c / 24.0 > 0.0)) continue;
                return y + 1;
            }
            n00 = b00;
            n10 = b10;
            n01 = b01;
            n11 = b11;
        }
        return 0;
    }

    private static double endSlide(double f, int y) {
        double top = y <= 56 ? 1.0 : (y >= 312 ? 0.0 : (312.0 - (double)y) / 256.0);
        double middle = -23.4375 + top * (f + 23.4375);
        double bottom = y <= 4 ? 0.0 : (y >= 32 ? 1.0 : ((double)y - 4.0) / 28.0);
        return -0.234375 + bottom * (middle + 0.234375);
    }

    private boolean vanillaCityTerrainOk(int chunkX, int chunkZ) {
        ChunkRand r = new ChunkRand();
        r.setSeed(this.worldSeed);
        long a = r.nextLong();
        long b = r.nextLong();
        r.setSeed((long)chunkX * a ^ (long)chunkZ * b ^ this.worldSeed);
        BlockRotation rotation = BlockRotation.getRandom(r);
        int xOff = 5;
        int zOff = 5;
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
        int min = this.vanillaWorldSurfaceWg(posX, posZ);
        min = Math.min(min, this.vanillaWorldSurfaceWg(posX, posZ + zOff));
        min = Math.min(min, this.vanillaWorldSurfaceWg(posX + xOff, posZ));
        min = Math.min(min, this.vanillaWorldSurfaceWg(posX + xOff, posZ + zOff));
        if ((this.debugSetting.get()).booleanValue()) {
            this.debugLog("城=(" + chunkX + "," + chunkZ + ") 旋转=" + String.valueOf(rotation) + " 预测最小=" + min);
        }
        return min >= 60;
    }

    private Path debugFilePath() {
        return this.mc.runDirectory.toPath().resolve("elytra-collector-debug.log");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void debugLog(String msg) {
        if (!(this.debugSetting.get()).booleanValue()) {
            return;
        }
        try {
            String line = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + msg + System.lineSeparator();
            Object object = this.debugLock;
            synchronized (object) {
                Files.writeString(this.debugFilePath(), (CharSequence)line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
        catch (Exception e) {
            this.error("调试日志写入失败: " + e.getMessage(), new Object[0]);
        }
    }

    private String blacklistKey(net.minecraft.util.math.BlockPos head) {
        return head.getX() + "," + head.getZ();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean isBlacklisted(net.minecraft.util.math.BlockPos head) {
        List list = (List)this.blacklist.get();
        synchronized (list) {
            for (String entry : (List<String>)this.blacklist.get()) {
                String[] parts = entry.split(",");
                if (parts.length < 2) continue;
                try {
                    int dz;
                    int x = Integer.parseInt(parts[0].trim());
                    int z = Integer.parseInt(parts[1].trim());
                    int dx = x - head.getX();
                    if (dx * dx + (dz = z - head.getZ()) * dz > 4) continue;
                    return true;
                }
                catch (NumberFormatException numberFormatException) {
                }
            }
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void addBlacklist(net.minecraft.util.math.BlockPos head) {
        List list = (List)this.blacklist.get();
        synchronized (list) {
            String key = this.blacklistKey(head);
            if (!((List)this.blacklist.get()).contains(key)) {
                ((List)this.blacklist.get()).add(key);
            }
        }
    }

    private void hitItemFrame() {
        net.minecraft.entity.decoration.ItemFrameEntity frame = this.getNearestItemFrame(this.killAuraReach.get());
        if (frame == null) {
            if (this.waypoints != null) {
                this.faceDirection(this.waypoints.facing);
            }
            frame = this.getNearestItemFrame(this.killAuraReach.get() + 2.0);
        }
        if (frame != null) {
            this.mc.interactionManager.attackEntity((net.minecraft.entity.player.PlayerEntity)this.mc.player, (net.minecraft.entity.Entity)frame);
            this.info("攻击展示框.", new Object[0]);
        } else {
            this.info("未找到展示框.", new Object[0]);
        }
    }

    private void pickUpElytra() {
        net.minecraft.entity.ItemEntity item = this.findDroppedElytra();
        if (item != null) {
            PathManagers.get().moveTo(item.getBlockPos(), false);
        }
    }

    private void equipElytra() {
        if (this.isWearingFullDurabilityElytra()) {
            return;
        }
        int slot = this.findFullDurabilityElytraSlot();
        if (slot == -1) {
            return;
        }
        InvUtils.move().from(slot).toArmor(2);
        this.info("已穿上鞘翅.", new Object[0]);
    }

    private boolean isWearingFullDurabilityElytra() {
        net.minecraft.item.ItemStack chest = (net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(38);
        return chest.getItem() == net.minecraft.item.Items.ELYTRA && chest.getDamage() == 0;
    }

    private void walkTowards(double x, double z) {
        double dx = x - this.mc.player.getX();
        double dz = z - this.mc.player.getZ();
        this.mc.player.setYaw((float)Math.toDegrees(Math.atan2(-dx, dz)));
        this.mc.options.forwardKey.setPressed(true);
    }

    private boolean useFirework() {
        FindItemResult fw = InvUtils.findInHotbar((net.minecraft.item.Item[])new net.minecraft.item.Item[]{net.minecraft.item.Items.FIREWORK_ROCKET});
        if (!fw.found()) {
            FindItemResult inv = InvUtils.find((net.minecraft.item.Item[])new net.minecraft.item.Item[]{net.minecraft.item.Items.FIREWORK_ROCKET});
            if (inv.found()) {
                InvUtils.move().from(inv.slot()).toHotbar(1);
                this.info("物品栏没有烟花，从背包调取放到第 2 个槽位.", new Object[0]);
            }
            return false;
        }
        if (fw.isOffhand()) {
            this.mc.interactionManager.interactItem((net.minecraft.entity.player.PlayerEntity)this.mc.player, net.minecraft.util.Hand.OFF_HAND);
        } else {
            InvUtils.swap((int)fw.slot(), (boolean)true);
            this.mc.interactionManager.interactItem((net.minecraft.entity.player.PlayerEntity)this.mc.player, net.minecraft.util.Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
        return true;
    }

    /** 统计烟花总数（背包 36 格 + 副手）。 */
    private int countFireworks() {
        int n = this.countItem(net.minecraft.item.Items.FIREWORK_ROCKET);
        net.minecraft.item.ItemStack off = this.mc.player.getOffHandStack();
        if (off.getItem() == net.minecraft.item.Items.FIREWORK_ROCKET) {
            n += off.getCount();
        }
        return n;
    }

    /** 烟花耗尽：中止巡航，切到补给降落。 */
    private void enterSupplyLanding() {
        if (this.state == State.SUPPLY_LANDING) {
            return;
        }
        if (++this.supplyFailCount > 2) {
            this.warning("烟花多次补给后仍不足，停止任务 (请检查末影箱烟花库存).", new Object[0]);
            this.stopTask("烟花补给失败，停止任务.");
            return;
        }
        this.fuelEmpty = true;
        this.pullUpActive = false;
        this.pullUpTick = 0;
        this.supplySpot = null;
        this.landingRecover = false;
        this.state = State.SUPPLY_LANDING;
        this.stateTick = 0;
        this.info("烟花剩余 " + this.countFireworks() + " 发，判定燃料耗尽，改为缓降寻找陆地补给.", new Object[0]);
        PathManagers.get().stop();
    }

    /** 补给降落：缓降找最近已加载的陆地，落地后走存储会话补货。 */
    private void onSupplyLanding() {
        if (this.mc.player.isOnGround()) {
            this.info("燃料耗尽: 已落地，开始从末影箱补给.", new Object[0]);
            this.fuelEmpty = false;
            this.supplyLandingResume = true;
            this.supplySpot = null;
            this.releaseForward();
            PathManagers.get().stop();
            this.dumpNeeded = false;
            this.resupplyNeeded = true;
            this.storagePhase = StoragePhase.PLACE_EC;
            this.storageTick = 0;
            this.ecPos = null;
            this.boxPos = null;
            this.boxInventorySlot = -1;
            this.boxFromInventory = false;
            this.supplyScanStart = 0;
            this.supplyPauseDump = false;
            this.resupplyRescan = false;
            return;
        }
        if (!this.mc.player.isGliding()) {
            this.mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }
        if (this.stateTick % 40 == 0 || this.supplySpot == null) {
            this.supplySpot = this.findLandingSpotAhead();
        }
        if (this.supplySpot != null) {
            this.rotateTo(this.yawTowards(this.supplySpot), 30.0f);
        } else {
            this.rotateTo(this.mc.player.getYaw(), 20.0f);
        }
        this.mc.options.forwardKey.setPressed(true);
    }

    /** 沿当前朝向向前扫 160 格，找第一处已加载且有地表的落点。 */
    private net.minecraft.util.math.BlockPos findLandingSpotAhead() {
        double yawRad = Math.toRadians((double)this.mc.player.getYaw());
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);
        for (int d = 0; d <= 160; d += 8) {
            int x = (int)Math.floor(this.mc.player.getX() + dx * (double)d);
            int z = (int)Math.floor(this.mc.player.getZ() + dz * (double)d);
            if (!this.mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int top = this.mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
            if (top >= 30) {
                return new net.minecraft.util.math.BlockPos(x, top, z);
            }
        }
        return null;
    }

    private void releaseForward() {
        this.mc.options.forwardKey.setPressed(false);
    }

    private void faceDirection(net.minecraft.util.math.Direction d) {
        float yaw = switch (d) {
            case net.minecraft.util.math.Direction.SOUTH -> 0.0f;
            case net.minecraft.util.math.Direction.WEST -> 90.0f;
            case net.minecraft.util.math.Direction.NORTH -> 180.0f;
            case net.minecraft.util.math.Direction.EAST -> -90.0f;
            default -> 0.0f;
        };
        this.rotateFastTo(yaw, this.mc.player.getPitch());
    }

    private float yawTowards(net.minecraft.util.math.BlockPos pos) {
        double dx = (double)pos.getX() + 0.5 - this.mc.player.getX();
        double dz = (double)pos.getZ() + 0.5 - this.mc.player.getZ();
        return (float)Math.toDegrees(Math.atan2(-dx, dz));
    }

    private double horizontalDistance(net.minecraft.util.math.BlockPos pos) {
        double dx = (double)pos.getX() + 0.5 - this.mc.player.getX();
        double dz = (double)pos.getZ() + 0.5 - this.mc.player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ===== 防卡区块 + 俯仰平滑（内嵌自 Ying 0.7.2 Pitch40Fly）=====

    /** 面向方向上前方最近未加载区块的距离；-1 表示前方全部已加载 */
    private double distanceToUnloadedAhead() {
        float yaw = this.mc.player.getYaw();
        double rad = Math.toRadians(yaw);
        double dirX = -Math.sin(rad);
        double dirZ = Math.cos(rad);
        double px = this.mc.player.getX();
        double pz = this.mc.player.getZ();
        int max = Math.max(this.chunkLookahead.get() + 32, 192);
        for (int d = 0; d <= max; d++) {
            int bx = (int) Math.floor(px + dirX * d);
            int bz = (int) Math.floor(pz + dirZ * d);
            if (!this.mc.world.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4)) return d;
        }
        return -1;
    }

    /** 防卡状态机：前方未加载区块太近就强制爬升，满足最短时长 + 连续 3 tick 余量才恢复 */
    private void updateAntiChunkClimb(net.minecraft.util.math.BlockPos lp) {
        if (!this.antiChunkClimb.get()) {
            this.antiClimbActive = false;
            this.antiClimbTicks = 0;
            this.antiClearStreak = 0;
            return;
        }
        // 接近降落点 (48 格内) 不做防卡，否则会被迫拉升导致无法降落
        if (this.horizontalDistance(lp) <= 48.0) {
            this.antiClimbActive = false;
            this.antiClimbTicks = 0;
            this.antiClearStreak = 0;
            return;
        }
        double ahead = this.distanceToUnloadedAhead();
        if (this.antiClimbActive) {
            ++this.antiClimbTicks;
            boolean clear = ahead < 0.0 || ahead > (double)(this.chunkLookahead.get() + this.resumeMargin.get());
            this.antiClearStreak = clear ? this.antiClearStreak + 1 : 0;
            this.climbing = true;
            if (this.antiClimbTicks >= this.minClimbTicks.get() && this.antiClearStreak >= 3) {
                this.antiClimbActive = false;
                this.antiClimbTicks = 0;
                this.antiClearStreak = 0;
                this.debugLog("前方区块已加载，结束防卡拉升，恢复 俯冲巡航 飞行.");
            }
        } else if (ahead >= 0.0 && ahead <= (double)this.chunkLookahead.get()) {
            this.antiClimbActive = true;
            this.antiClimbTicks = 0;
            this.antiClearStreak = 0;
            this.climbing = true;
            this.debugLog("前方 " + String.format("%.0f", ahead) + " 格有未加载区块 (阈值 " + this.chunkLookahead.get() + ")，触发拉升.");
        }
    }

    /** 绕圈扫图 yaw：视角垂直于「中心点→当前位置」连线（内嵌自 Ying 0.7.2 ScanSweep） */
    private float sweepYaw() {
        double dx = this.mc.player.getX() - ((double)this.sweepCenterX.get() + 0.5);
        double dz = this.mc.player.getZ() - ((double)this.sweepCenterZ.get() + 0.5);
        if (dx * dx + dz * dz < 1.0E-6) {
            return this.mc.player.getYaw();
        }
        float lineYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float perpYaw = lineYaw + (this.sweepSide.get() == SweepSide.LEFT ? -90.0f : 90.0f);
        return perpYaw + this.sweepOffset.get().floatValue();
    }

    /** 角速度限幅逼近目标角 */
    private static float stepToward(float current, float target, float speed) {
        float d = target - current;
        if (Math.abs(d) <= speed) return target;
        return current + Math.signum(d) * speed;
    }

    private void rotateTo(float targetYaw, float targetPitch) {
        this.mc.player.setYaw(ElytraCollector.stepAngle(this.mc.player.getYaw(), targetYaw, (this.yawSpeed.get()).floatValue()));
        this.mc.player.setPitch(ElytraCollector.stepAngle(this.mc.player.getPitch(), targetPitch, (this.pitchSpeed.get()).floatValue()));
    }

    private void rotateFastTo(float targetYaw, float targetPitch) {
        float speed = (this.yawSpeed.get()).floatValue();
        this.mc.player.setYaw(ElytraCollector.stepAngle(this.mc.player.getYaw(), targetYaw, speed));
        this.mc.player.setPitch(ElytraCollector.stepAngle(this.mc.player.getPitch(), targetPitch, speed));
    }

    private static float stepAngle(float current, float target, float speed) {
        float d;
        for (d = target - current; d > 180.0f; d -= 360.0f) {
        }
        while (d < -180.0f) {
            d += 360.0f;
        }
        if (Math.abs(d) <= speed) {
            return target;
        }
        return current + Math.signum(d) * speed;
    }

    private static boolean isDragonHead(net.minecraft.block.BlockState state) {
        String id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
        return id.equals("dragon_head") || id.equals("dragon_wall_head");
    }

    private DragonHead findDragonHead(net.minecraft.util.math.BlockPos center, int radius, int yRange) {
        net.minecraft.util.math.BlockPos.Mutable pos = new net.minecraft.util.math.BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dy = -yRange; dy <= yRange; ++dy) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    net.minecraft.block.BlockState s = this.mc.world.getBlockState((net.minecraft.util.math.BlockPos)pos);
                    if (!ElytraCollector.isDragonHead(s)) continue;
                    net.minecraft.util.math.Direction facing = s.contains((net.minecraft.state.property.Property)net.minecraft.state.property.Properties.FACING) ? (net.minecraft.util.math.Direction)s.get((net.minecraft.state.property.Property)net.minecraft.state.property.Properties.FACING) : net.minecraft.util.math.Direction.NORTH;
                    return new DragonHead(pos.toImmutable(), facing);
                }
            }
        }
        return null;
    }

    private net.minecraft.entity.decoration.ItemFrameEntity getNearestItemFrame(double reach) {
        net.minecraft.util.math.Box box = this.mc.player.getBoundingBox().expand(reach);
        net.minecraft.entity.decoration.ItemFrameEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.entity.Entity entity : this.mc.world.getOtherEntities((net.minecraft.entity.Entity)this.mc.player, box, e -> true)) {
            net.minecraft.entity.decoration.ItemFrameEntity frame;
            double d;
            if (!(entity instanceof net.minecraft.entity.decoration.ItemFrameEntity) || !((d = (frame = (net.minecraft.entity.decoration.ItemFrameEntity)entity).squaredDistanceTo((net.minecraft.entity.Entity)this.mc.player)) < bestD)) continue;
            bestD = d;
            best = frame;
        }
        return best;
    }

    /** 在目标点周围 radius 格内找展示框：优先持有鞘翅的，其次取最近（解决种子坐标 ±1 偏差扫不到） */
    private net.minecraft.entity.decoration.ItemFrameEntity findFrameNear(net.minecraft.util.math.BlockPos pos, double radius) {
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(pos).expand(radius);
        net.minecraft.entity.decoration.ItemFrameEntity best = null;
        double bestD = Double.MAX_VALUE;
        net.minecraft.entity.decoration.ItemFrameEntity fallback = null;
        double fallbackD = Double.MAX_VALUE;
        for (net.minecraft.entity.Entity entity : this.mc.world.getOtherEntities(null, box, e -> e instanceof net.minecraft.entity.decoration.ItemFrameEntity)) {
            if (!(entity instanceof net.minecraft.entity.decoration.ItemFrameEntity)) continue;
            net.minecraft.entity.decoration.ItemFrameEntity frame = (net.minecraft.entity.decoration.ItemFrameEntity)entity;
            double d = frame.squaredDistanceTo((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5);
            if (frame.getHeldItemStack().getItem() == net.minecraft.item.Items.ELYTRA) {
                if (d < bestD) {
                    bestD = d;
                    best = frame;
                }
            } else if (d < fallbackD) {
                fallbackD = d;
                fallback = frame;
            }
        }
        return best != null ? best : fallback;
    }

    private net.minecraft.entity.decoration.ItemFrameEntity findFrameAt(net.minecraft.util.math.BlockPos pos) {
        for (net.minecraft.entity.Entity entity : this.mc.world.getOtherEntities(null, new net.minecraft.util.math.Box(pos), e -> e instanceof net.minecraft.entity.decoration.ItemFrameEntity)) {
            if (!(entity instanceof net.minecraft.entity.decoration.ItemFrameEntity)) continue;
            net.minecraft.entity.decoration.ItemFrameEntity frame = (net.minecraft.entity.decoration.ItemFrameEntity)entity;
            return frame;
        }
        return null;
    }

    private boolean hasShulkerAtGuard() {
        if (this.waypoints == null) {
            return false;
        }
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(this.waypoints.guard);
        for (net.minecraft.entity.Entity entity : this.mc.world.getOtherEntities(null, box, e -> e.getType() == net.minecraft.entity.EntityType.SHULKER)) {
            if (!entity.getBlockPos().equals(this.waypoints.guard)) continue;
            return true;
        }
        return false;
    }

    private net.minecraft.entity.ItemEntity findDroppedElytra() {
        net.minecraft.util.math.Box box = this.mc.player.getBoundingBox().expand(32.0);
        net.minecraft.entity.ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.entity.Entity entity : this.mc.world.getOtherEntities((net.minecraft.entity.Entity)this.mc.player, box, e -> true)) {
            double d;
            net.minecraft.entity.ItemEntity item;
            if (!(entity instanceof net.minecraft.entity.ItemEntity) || (item = (net.minecraft.entity.ItemEntity)entity).getStack().getItem() != net.minecraft.item.Items.ELYTRA || !((d = item.squaredDistanceTo((net.minecraft.entity.Entity)this.mc.player)) < bestD)) continue;
            bestD = d;
            best = item;
        }
        return best;
    }

    private boolean hasElytraInInventory() {
        return this.findFullDurabilityElytraSlot() != -1 || this.findAnyElytraSlot() != -1;
    }

    private boolean hasElytraEquipped() {
        return this.mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem() == net.minecraft.item.Items.ELYTRA;
    }

    private int findAnyElytraSlot() {
        for (int i = 0; i < this.mc.player.getInventory().size(); ++i) {
            if (((net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i)).getItem() != net.minecraft.item.Items.ELYTRA) continue;
            return i;
        }
        return -1;
    }

    private int findFullDurabilityElytraSlot() {
        for (int i = 0; i < this.mc.player.getInventory().size(); ++i) {
            net.minecraft.item.ItemStack s = (net.minecraft.item.ItemStack)this.mc.player.getInventory().getStack(i);
            if (s.getItem() != net.minecraft.item.Items.ELYTRA || s.getDamage() != 0) continue;
            return i;
        }
        return -1;
    }

    private long parseSeed(String s) {
        if (s == null || s.equals("0") || s.isEmpty()) {
            if (this.mc.getServer() != null) {
                return this.mc.getServer().getOverworld().getSeed();
            }
            return 0L;
        }
        try {
            return Long.parseLong(s);
        }
        catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    private static enum SweepSide {
        LEFT("左边"),
        RIGHT("右边");

        private final String label;

        private SweepSide(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private static enum State {
        IDLE("空闲"),
        SEARCHING("搜索"),
        RISING("起飞"),
        FLYING("飞行"),
        LANDING("降落"),
        COLLECTING("采集"),
        DONE("完成"),
        SUPPLY_LANDING("补给降落");

        private final String label;

        private State(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private static enum CollectStep {
        TO_P1("前往P1"),
        TO_P2("前往P2"),
        WAIT_SHULKER("等待潜影盒"),
        TO_P3("前往P3"),
        HIT_FRAME("打展示框"),
        PICK_UP("拾取鞘翅"),
        EQUIP("穿鞘翅"),
        EXIT_P1("撤离P1"),
        EXIT_LANDING("撤离降落点"),
        LEAVE_SHIP("离开船");

        private final String label;

        private CollectStep(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private static enum StoragePhase {
        NONE("无"),
        PLACE_EC("放置末影箱"),
        OPEN_EC("打开末影箱"),
        PICK_BOX("取潜影盒"),
        CLOSE_SCREEN("关界面"),
        PLACE_BOX("放置潜影盒"),
        OPEN_BOX("打开潜影盒"),
        FILL_BOX("装盒"),
        TAKE_SUPPLIES("取物资"),
        MINE_BOX("挖潜影盒"),
        MINE_EC("挖末影箱");

        private final String label;

        private StoragePhase(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private record ShipTarget(CPos chunk, net.minecraft.util.math.BlockPos headPos, net.minecraft.util.math.Direction facing) {
    }

    private record ShipWaypoints(net.minecraft.util.math.BlockPos landing, net.minecraft.util.math.BlockPos p1, net.minecraft.util.math.BlockPos p2, net.minecraft.util.math.BlockPos p3, net.minecraft.util.math.BlockPos guard, net.minecraft.util.math.Direction facing) {
        static ShipWaypoints from(net.minecraft.util.math.BlockPos head, net.minecraft.util.math.Direction facing) {
            return new ShipWaypoints(ShipWaypoints.waypoint(head, facing, 7, 0, 4), ShipWaypoints.waypoint(head, facing, 23, -1, -1), ShipWaypoints.waypoint(head, facing, 9, -1, -4), ShipWaypoints.waypoint(head, facing, 7, 0, -3), ShipWaypoints.waypoint(head, facing, 8, 0, -4), facing);
        }

        static net.minecraft.util.math.BlockPos waypoint(net.minecraft.util.math.BlockPos head, net.minecraft.util.math.Direction facing, int behind, int left, int dy) {
            return head.offset(facing.getOpposite(), behind).offset(facing.rotateYCounterclockwise(), left).offset(net.minecraft.util.math.Direction.UP, dy);
        }
    }

    private record DragonHead(net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction facing) {
    }

    private record MoveOp(int source, int target) {
    }

    private record SupplyRule(net.minecraft.item.Item item, int min, int batch) {
    }
}
