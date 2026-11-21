
package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
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
    private boolean headFound;
    private net.minecraft.util.math.BlockPos exactHead;
    private int nearTicks;
    private int scanAttempts;
    private boolean pullUpSuppressed;
    private final Set<String> sessionSkip;
    private int searchExtends;
    private net.minecraft.util.math.Direction takeoffFacing;
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
        super(AddonTemplate.CATEGORY, "FO 鞘翅采集", "\u5168\u81ea\u52a8\u627e\u672b\u5730\u57ce\u9798\u7fc5\uff1a\u79cd\u5b50\u5b9a\u4f4d + \u9f99\u5934\u7cbe\u786e\u5b9a\u4f4d + \u9ad8\u5ea6\u4fdd\u6301\u98de\u884c + \u7f13\u964d + Baritone \u5bfb\u8def + \u6253\u5c55\u793a\u6846\u6361\u9798\u7fc5.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgFlight = this.settings.createGroup("飞行");
        this.sgAntiChunk = this.settings.createGroup("防卡区块");
        this.seedSetting = this.sgGeneral.add((((new StringSetting.Builder().name("seed")).description("\u4e16\u754c\u79cd\u5b50 (0 = \u5f53\u524d\u4e16\u754c).")).defaultValue("0")).build());
        this.searchRange = this.sgGeneral.add((((new IntSetting.Builder().name("search-range")).description("\u641c\u7d22\u534a\u5f84\uff0c\u5355\u4f4d\u65b9\u5757 (\u4ece\u73a9\u5bb6\u4f4d\u7f6e).")).defaultValue(5000)).range(320, 100000).sliderRange(320, 20000).build());
        this.minHeight = this.sgFlight.add((((new IntSetting.Builder().name("min-height")).description("\u98de\u884c\u4e2d\u4f4e\u4e8e\u6b64\u9ad8\u5ea6\u65f6\u89e6\u53d1\u722c\u5347.")).defaultValue(180)).range(100, 300).sliderRange(120, 260).build());
        this.maxHeight = this.sgFlight.add((((new IntSetting.Builder().name("max-height")).description("\u98de\u884c\u4e2d\u9ad8\u4e8e\u6b64\u9ad8\u5ea6\u65f6\u505c\u6b62\u722c\u5347\u3001\u8f6c\u4e3a\u5e73\u7f13\u4e0b\u6ed1 (\u4e0e min-height \u7ec4\u6210\u6ede\u56de\u533a\u95f4).")).defaultValue(220)).range(120, 320).sliderRange(140, 300).build());
        this.riseHeight = this.sgFlight.add((((new IntSetting.Builder().name("rise-height")).description("\u8d77\u98de\u540e\u62ac\u5934\u722c\u5347\u5230\u7684\u76ee\u6807\u9ad8\u5ea6 (\u9700\u9ad8\u4e8e max-height).")).defaultValue(230)).range(200, 320).sliderRange(200, 300).build());
        this.pitchSpeed = this.sgFlight.add(((new DoubleSetting.Builder().name("pitch-speed")).description("\u98de\u884c (pitch40) \u65f6\u4e0a\u4e0b\u8f6c\u52a8\u89c6\u89d2 (pitch) \u7684\u901f\u5ea6 (\u5ea6/tick).")).defaultValue(10.0).min(1.0).sliderMax(45.0).build());
        this.yawSpeed = this.sgFlight.add(((new DoubleSetting.Builder().name("yaw-speed")).description("\u5de6\u53f3\u8f6c\u52a8\u89c6\u89d2 (yaw) \u53ca\u9798\u7fc5\u7f13\u964d\u7684\u8f6c\u5411\u901f\u5ea6 (\u5ea6/tick).")).defaultValue(30.0).min(1.0).sliderMax(90.0).build());
        this.killAuraReach = this.sgFlight.add(((new DoubleSetting.Builder().name("kill-aura-reach")).description("\u653b\u51fb\u5c55\u793a\u6846 (item_frame) \u7684\u5224\u5b9a\u8ddd\u79bb.")).defaultValue(3.0).min(1.0).sliderMax(6.0).build());
        this.fireworkInterval = this.sgFlight.add(((new DoubleSetting.Builder().name("firework-interval")).description("\u5c55\u5f00\u9798\u7fc5\u540e\u7acb\u5373\u4f7f\u7528\u7b2c\u4e00\u4e2a\u70df\u82b1\uff0c\u4e4b\u540e\u6bcf\u9694\u8fd9\u4e48\u591a\u79d2\u518d\u7528\u4e00\u4e2a (\u79d2).")).defaultValue(2.0).min(0.5).sliderRange(0.5, 10.0).build());
        this.blacklist = this.sgGeneral.add(((((new StringListSetting.Builder().name("blacklist")).description("\u9ed1\u540d\u5355\uff1a\u5df2\u7ecf\u53bb\u8fc7\u7684\u8239 (x,z)\uff0c\u641c\u7d22\u65f6\u4f1a\u81ea\u52a8\u8df3\u8fc7. \u81ea\u52a8\u7ef4\u62a4.")).defaultValue(new ArrayList())).visible(() -> false)).build());
        this.searchResults = this.sgGeneral.add((((new StringListSetting.Builder().name("search-results")).description("\u4e0a\u6b21\u641c\u7d22\u7ed3\u679c\u5217\u8868 (x,y,z,\u671d\u5411)\uff0c\u4e0b\u4e00\u6b21\u641c\u7d22\u5b8c\u6210\u540e\u8986\u76d6.")).defaultValue(new ArrayList())).build());
        this.btnStart = this.sgGeneral.add(((((new BoolSetting.Builder().name("start")).description("\u5f00\u59cb\u81ea\u52a8\u91c7\u96c6.")).defaultValue(false)).onChanged(b -> {
            if (b.booleanValue()) {
                this.start();
            }
        })).build());
        this.debugSetting = this.sgGeneral.add((((new BoolSetting.Builder().name("debug")).description("\u8f93\u51fa\u8c03\u8bd5\u65e5\u5fd7 (\u7528\u4e8e\u6821\u51c6\u9ad8\u5ea6\u516c\u5f0f).")).defaultValue(false)).build());
        this.sgStorage = this.settings.createGroup("存储");
        this.freeSlotDump = this.sgStorage.add((((new IntSetting.Builder().name("dump-free-slots")).description("\u80cc\u5305\u53ef\u7528\u7a7a\u683c \u2264 \u6b64\u503c\u65f6\uff0c\u62ff\u5230\u9798\u7fc5\u540e\u81ea\u52a8\u53bb\u672b\u5f71\u7bb1\u5b58\u9798\u7fc5.")).defaultValue(3)).range(0, 36).sliderRange(0, 36).build());
        this.supplies = this.sgStorage.add((((new StringListSetting.Builder().name("supplies")).description("\u7269\u8d44\u5217\u8868\uff0c\u683c\u5f0f: \u7269\u54c1ID;\u6700\u4f4e\u503c;\u76ee\u6807\u5e93\u5b58 (\u5982 minecraft:firework_rocket;32;256). \u80cc\u5305\u7269\u8d44\u4f4e\u4e8e\u6700\u4f4e\u503c\u65f6\u81ea\u52a8\u4ece\u672b\u5f71\u7bb1\u8865\u8d27\uff0c\u62ff\u5230\u76ee\u6807\u5e93\u5b58\u4e3a\u6b62.")).defaultValue(new ArrayList<String>(List.of("minecraft:firework_rocket;4;16", "minecraft:cooked_beef;8;32")))).build());
        this.lowYExit = this.sgStorage.add((((new BoolSetting.Builder().name("low-y-exit")).description("Y 低于阈值时自动停止任务 (防虚空掉物，不退游戏).")).defaultValue(true)).build());
        this.lowYThreshold = this.sgStorage.add((((new IntSetting.Builder().name("low-y-threshold")).description("低于此 Y 自动停止任务.")).defaultValue(-20)).range(-64, 100).sliderRange(-64, 64).build());
        // 防卡区块（内嵌自 Ying 0.7.2 Pitch40Fly）
        this.antiChunkClimb = this.sgAntiChunk.add((((new BoolSetting.Builder().name("anti-chunk-climb")).description("面向方向前方未加载区块太近时，强制抬头爬升，避免被服务端拉回。")).defaultValue(true)).build());
        this.chunkLookahead = this.sgAntiChunk.add((((new IntSetting.Builder().name("chunk-lookahead")).description("面向方向上前方多少格内出现未加载区块就触发爬升 (格).")).defaultValue(32)).range(4, 128).sliderRange(4, 128).build());
        this.minClimbTicks = this.sgAntiChunk.add((((new IntSetting.Builder().name("min-climb-ticks")).description("触发防卡拉升后至少保持多少 tick，防止一抖一抖。")).defaultValue(10)).range(0, 100).sliderRange(0, 100).build());
        this.resumeMargin = this.sgAntiChunk.add((((new IntSetting.Builder().name("resume-margin")).description("未加载区块距离要大于 检测距离+该值 且连续 3 tick 满足，才结束拉升。")).defaultValue(16)).range(0, 64).sliderRange(0, 64).build());
        // 俯仰平滑（内嵌自 Ying 0.7.2 Pitch40Fly）
        this.pitchSmooth = this.sgFlight.add((((new BoolSetting.Builder().name("pitch-smooth")).description("爬升/俯冲时按固定角速度平滑过渡，不硬切 37.72/-54.77。")).defaultValue(true)).build());
        this.pitchRotateUp = this.sgFlight.add((((new DoubleSetting.Builder().name("pitch-rotate-up")).description("俯仰平滑开启时，爬升抬头速度 (度/tick).")).defaultValue(5.45)).min(1.0).sliderMax(20.0).build());
        this.pitchRotateDown = this.sgFlight.add((((new DoubleSetting.Builder().name("pitch-rotate-down")).description("俯仰平滑开启时，俯冲低头速度 (度/tick).")).defaultValue(0.9)).min(0.5).sliderMax(2.0).build());
        // 绕圈扫图（内嵌自 Ying 0.7.2 ScanSweep）
        this.sgHelpers = this.settings.createGroup("辅助");
        this.sweepScan = this.sgHelpers.add((((new BoolSetting.Builder().name("sweep-scan")).description("未确认龙头时绕中心点扫图：视角始终垂直于「中心点→当前位置」连线。")).defaultValue(false)).build());
        this.sweepCenterX = this.sgHelpers.add((((new IntSetting.Builder().name("sweep-center-x")).description("扫图中心点 X 坐标 (方块).")).defaultValue(0)).range(-30000000, 30000000).sliderRange(-10000, 10000).build());
        this.sweepCenterZ = this.sgHelpers.add((((new IntSetting.Builder().name("sweep-center-z")).description("扫图中心点 Z 坐标 (方块).")).defaultValue(0)).range(-30000000, 30000000).sliderRange(-10000, 10000).build());
        this.sweepSide = this.sgHelpers.add((((new EnumSetting.Builder<SweepSide>().name("sweep-side")).description("取垂线的哪个方向：直线的左边 / 右边。")).defaultValue(SweepSide.LEFT)).build());
        this.sweepOffset = this.sgHelpers.add((((new DoubleSetting.Builder().name("sweep-offset")).description("偏移角 (度)：在垂线朝向上再转这么多度，范围 -30 ~ 30。")).defaultValue(0.0)).range(-30.0, 30.0).sliderRange(-30.0, 30.0).decimalPlaces(4).build());
        this.slowFallBoost = this.sgHelpers.add((((new BoolSetting.Builder().name("slow-fall-boost")).description("缓降增强：平视 + 每 2 tick 翻转 yaw 180° 抵消水平速度。")).defaultValue(true)).build());
        // 起跳防卡（内嵌自 Ying 0.7.2 ElytraJump）
        this.antiStuckJump = this.sgHelpers.add((((new BoolSetting.Builder().name("anti-stuck-jump")).description("起跳防卡：连续两次检测在同一位置时用 Baritone 寻路绕开。")).defaultValue(false)).build());
        this.antiStuckInterval = this.sgHelpers.add((((new IntSetting.Builder().name("anti-stuck-interval")).description("防卡住检测间隔 (tick).")).defaultValue(20)).range(2, 200).build());
        this.antiStuckOffset = this.sgHelpers.add((((new IntSetting.Builder().name("anti-stuck-offset")).description("防卡住目标点偏移 (沿运动方向，格).")).defaultValue(4)).range(1, 30).sliderRange(1, 30).build());
        this.antiStuckResumeTicks = this.sgHelpers.add((((new IntSetting.Builder().name("anti-stuck-resume-tick")).description("触发后等待多少 tick 恢复鞘翅跳跃 (Baritone 寻路期间保持暂停)。")).defaultValue(80)).range(10, 600).build());
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
        WSection section = theme.section("\u9ed1\u540d\u5355", true);
        WButton clear = (WButton)section.add((WWidget)theme.button("\u6e05\u9664\u9ed1\u540d\u5355")).expandX().widget();
        clear.action = () -> {
            List list = (List)this.blacklist.get();
            synchronized (list) {
                ((List)this.blacklist.get()).clear();
            }
            this.info("\u9ed1\u540d\u5355\u5df2\u6e05\u7a7a.", new Object[0]);
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
        this.info("\u5f00\u59cb\u641c\u7d22\u672b\u5730\u57ce (\u8303\u56f4=" + String.valueOf(this.searchRange.get()) + " \u65b9\u5757)...", new Object[0]);
        if ((this.debugSetting.get()).booleanValue()) {
            this.info("\u8c03\u8bd5\u65e5\u5fd7\u6587\u4ef6: " + String.valueOf(this.debugFilePath().toAbsolutePath()), new Object[0]);
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
    private void searchShips(int generation) {
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
                                this.debugLog("ship city=(" + city.getX() + "," + city.getZ() + ") head=(" + t.headPos.getX() + "," + t.headPos.getY() + "," + t.headPos.getZ() + ") facing=" + String.valueOf(t.facing) + " E=" + String.format("%.4f", this.endIslandDensity(city.getX() * 16 + 8, city.getZ() * 16 + 8)));
                            }
                        }
                        generator.reset();
                    }
                }
                if (this.searchCancelled || generation != this.searchGeneration) {
                    return;
                }
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
                this.info("\u627e\u5230 " + this.ships.size() + " \u8258\u5e26\u8239\u672b\u5730\u57ce (\u5df2\u6392\u9664\u9ed1\u540d\u5355, \u8303\u56f4=" + range + ").", new Object[0]);
                if (this.ships.isEmpty()) {
                    if (this.searchExtends < 10) {
                        ++this.searchExtends;
                        this.info("\u8303\u56f4\u5185\u6ca1\u6709\u672b\u5730\u8239\uff0c\u6269\u5927\u8303\u56f4 +" + this.searchExtends * 500 + " \u683c\u91cd\u65b0\u641c\u7d22...", new Object[0]);
                        this.launchSearch();
                    } else {
                        this.searchExtends = 0;
                        this.completeTask("\u8303\u56f4\u5185\u6ca1\u6709\u672b\u5730\u8239\uff0c\u4efb\u52a1\u5b8c\u6210.");
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
            catch (Exception e) {
                if (this.searchCancelled || generation != this.searchGeneration) break block18;
                this.error("\u641c\u7d22\u5931\u8d25: " + e.getMessage(), new Object[0]);
                this.state = State.IDLE;
                this.btnStart.set(false);
            }
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
            this.completeTask("\u5168\u90e8\u672b\u5730\u57ce\u5df2\u5904\u7406\uff0c\u4efb\u52a1\u5b8c\u6210.");
            return;
        }
        this.current = this.ships.get(this.targetIndex);
        this.waypoints = ShipWaypoints.from(this.current.headPos, this.current.facing);
        this.climbing = false;
        this.headFound = false;
        this.frameAirChecked = false;
        this.frameAirEmpty = false;
        this.frameAirScanTicks = 0;
        this.exactHead = null;
        this.nearTicks = 0;
        this.scanAttempts = 0;
        this.firstFireworkDone = false;
        this.landingRecover = false;
        this.recoverCount = 0;
        this.stateTick = 0;
        this.info("\u98de\u5411\u6700\u8fd1\u672a\u53bb\u8fc7\u7684\u8239 \u8fd1\u4f3c\u9f99\u5934=" + String.valueOf(this.current.headPos) + " \u671d\u5411=" + String.valueOf(this.current.facing), new Object[0]);
        this.state = State.FLYING;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        try {
            if (this.mc.player == null || this.mc.world == null) {
                return;
            }
            ++this.stateTick;
            if (this.state != State.IDLE && this.state != State.DONE && (this.lowYExit.get()).booleanValue() && this.mc.player.getY() < (double)(this.lowYThreshold.get()).intValue()) {
                this.warning("Y=" + String.format("%.1f", this.mc.player.getY()) + " \u4f4e\u4e8e\u9608\u503c " + String.valueOf(this.lowYThreshold.get()) + "\uff0c\u4efb\u52a1\u5df2\u505c\u6b62.", new Object[0]);
                this.stopTask("\u4f4e\u9ad8\u5ea6\u4efb\u52a1\u505c\u6b62.");
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
            }
        }
        catch (Throwable t) {
            this.error("\u4efb\u52a1\u5f02\u5e38: " + String.valueOf(t), new Object[0]);
            t.printStackTrace();
            this.stopTask("\u4efb\u52a1\u5f02\u5e38\uff0c\u505c\u6b62\u4efb\u52a1.");
        }
    }

    private void onRising() {
        if (!this.landingRecover && this.stateTick > 400) {
            this.error("\u8d77\u98de\u8d85\u65f6 (\u53ef\u80fd\u6ca1\u7a7f\u9798\u7fc5\u6216\u6ca1\u6709\u70df\u82b1).", new Object[0]);
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
                    this.info("\u5df2\u62c9\u5347\u5230\u964d\u843d\u70b9\u4e0a\u65b9 25 \u683c\uff0c\u7528 pitch40 \u6a21\u5f0f\u98de\u5411\u964d\u843d\u70b9.", new Object[0]);
                }
            } else if (this.horizontalDistance(lp) <= 3.0 || this.recoverStageTick > 600) {
                this.releaseForward();
                this.landingRecover = false;
                this.recoverStage = 0;
                this.recoverStageTick = 0;
                this.info("\u5df2\u56de\u5230\u964d\u843d\u70b9\u4e0a\u65b9\uff0c\u8fdb\u5165\u7f13\u964d.", new Object[0]);
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
                this.firstFireworkDone = false;
                this.takeoffFacing = null;
                this.state = State.RISING;
                this.stateTick = 0;
                return;
            }
            this.mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(this.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
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
                                this.debugLog("head=(" + this.exactHead.getX() + "," + this.exactHead.getY() + "," + this.exactHead.getZ() + ") 龙头下地表=" + realTop + " seedY=" + this.current.headPos.getY());
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
            if (this.frameAirScanTicks % 20 == 0) {
                net.minecraft.entity.decoration.ItemFrameEntity airFrame = this.findFrameAt(this.waypoints.p3);
                if (airFrame != null) {
                    this.frameAirChecked = true;
                    this.frameAirEmpty = airFrame.getHeldItemStack().getItem() != net.minecraft.item.Items.ELYTRA;
                    if (this.frameAirEmpty) {
                        this.info("展示框里没有鞘翅，不降落：拉升到 max-height 以上后跳过该船.");
                        this.climbing = true;
                    } else {
                        this.info("展示框里有鞘翅，按原计划降落.");
                    }
                }
            }
            ++this.frameAirScanTicks;
            if (this.frameAirScanTicks > 120) {
                this.frameAirChecked = true;
                this.frameAirEmpty = true;
                this.info("空中未找到展示框实体，按无鞘翅处理 (跳过该船).");
            }
        }

        float yaw = !this.headFound && this.sweepScan.get() ? this.sweepYaw() : this.yawTowards(lp);
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
        if (this.pitchSmooth.get()) {
            float speed = pitch < this.smoothedPitch ? this.pitchRotateUp.get().floatValue() : this.pitchRotateDown.get().floatValue();
            this.smoothedPitch = ElytraCollector.stepToward(this.smoothedPitch, pitch, speed);
            pitch = this.smoothedPitch;
        } else {
            this.smoothedPitch = pitch;
        }

        this.rotateTo(yaw, pitch);
        this.mc.options.forwardKey.setPressed(true);

        if (this.frameAirEmpty) {
            if (this.mc.player.getY() < this.maxHeight.get()) {
                this.climbing = true;
                if (this.stateTick - this.lastFireworkTick >= Math.max(1, Math.round(this.fireworkInterval.get() * 20))) {
                    this.useFirework();
                    this.lastFireworkTick = this.stateTick;
                }
            } else {
                this.leaveShipNow();
            }
            return;
        }
        if (this.headFound && this.horizontalDistance(lp) <= 3.0) {
            this.state = State.LANDING;
            this.stateTick = 0;
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
        this.info("\u5371\u9669\u9ad8\u5ea6: \u4e2d\u6b62\u5b58\u50a8\u4f1a\u8bdd\uff0c\u5f3a\u5236\u62c9\u5347.", new Object[0]);
    }

    boolean isLandingOrRecovering() {
        return this.state == State.LANDING || this.landingRecover;
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
                this.stopTask("\u591a\u6b21\u504f\u79bb\u964d\u843d\u70b9\u65e0\u6cd5\u964d\u843d\uff0c\u505c\u6b62\u4efb\u52a1.");
                return;
            }
            this.landingRecover = true;
            this.recoverStage = 0;
            this.recoverStageTick = 0;
            this.recoveryYaw = this.yawTowards(lp) + 180.0f;
            this.firstFireworkDone = false;
            this.info("\u4f4e\u4e8e\u964d\u843d\u70b9 10 \u683c\uff0c\u53cd\u5411\u62c9\u5347 (\u76ee\u6807\u964d\u843d\u70b9\u4e0a\u65b9 25 \u683c).", new Object[0]);
            this.state = State.RISING;
            this.stateTick = 0;
            return;
        }
        if (this.mc.player.isOnGround()) {
            if (hDist < 3.0) {
                this.releaseForward();
                this.recoverCount = 0;
                this.info("\u5df2\u964d\u843d\u5728\u964d\u843d\u70b9.", new Object[0]);
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
                PathManagers.get().moveTo(this.waypoints.landing.down(4), false);
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
                        this.info("\u5c55\u793a\u6846\u91cc\u6ca1\u6709\u9798\u7fc5\uff0c\u76f4\u63a5\u79bb\u5f00\u8be5\u8239.", new Object[0]);
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
                this.info("\u653b\u51fb\u5c55\u793a\u6846\u540e\u6ca1\u6709\u9798\u7fc5\u6389\u843d\uff0c\u76f4\u63a5\u79bb\u5f00\u8be5\u8239.", new Object[0]);
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
                this.info("\u9798\u7fc5\u6389\u843d\u6d88\u5931\u672a\u62fe\u53d6\uff0c\u76f4\u63a5\u79bb\u5f00\u8be5\u8239.", new Object[0]);
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
                this.info("\u8be5\u8239\u5b8c\u6210\uff0c\u52a0\u5165\u9ed1\u540d\u5355.", new Object[0]);
                this.finishShip();
                break;
            }
            case 9: {
                if (!this.reached(this.waypoints.landing.down(4), 2.5)) break;
                this.releaseForward();
                this.info("\u5df2\u56de\u5230\u964d\u843d\u70b9\uff0c\u8d77\u98de\u79bb\u5f00.", new Object[0]);
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
            this.info("\u5b58\u50a8\u4f1a\u8bdd: \u5b58\u9798\u7fc5=" + this.dumpNeeded + ", \u8865\u8d27=" + this.resupplyNeeded, new Object[0]);
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
        this.info("\u8be5\u8239\u8df3\u8fc7\u5e76\u52a0\u5165\u9ed1\u540d\u5355\uff0c\u91cd\u65b0\u641c\u7d22\u6700\u8fd1\u672a\u53bb\u8fc7\u7684\u8239.", new Object[0]);
        this.ships.clear();
        this.targetIndex = 0;
        this.searchExtends = 0;
        this.takeoffFacing = this.current.facing;
        this.firstFireworkDone = false;
        this.landingRecover = false;
        this.releaseForward();
        this.launchSearch();
        if (this.mc.player.isOnGround() && this.waypoints != null && this.horizontalDistance(this.waypoints.landing) > 2.5) {
            this.info("\u5df2\u964d\u843d\uff1a\u5148\u56de\u964d\u843d\u70b9\u4fee\u6b63\u8d77\u98de\u65b9\u5411\u540e\u518d\u79bb\u5f00.", new Object[0]);
            this.state = State.COLLECTING;
            this.collectStep = CollectStep.LEAVE_SHIP;
            this.stateTick = 0;
            this.beginCollectStep();
        } else {
            this.state = State.RISING;
            this.stateTick = 0;
        }
    }

    private void onStorageTick() {
        try {
            ++this.storageTick;
            if (this.storageTick > 1200) {
                this.stopTask("\u5b58\u50a8\u4f1a\u8bdd\u8d85\u65f6 (\u9636\u6bb5=" + String.valueOf(this.storagePhase) + ")\uff0c\u505c\u6b62\u4efb\u52a1.");
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
                        this.info("\u8865\u7ed9\u6682\u505c: \u80cc\u5305\u5269\u4f59 " + this.freeSlots() + " \u69fd\uff0c\u5148\u56de\u6536\u8865\u7ed9\u76d2\u3001\u5b58\u9798\u7fc5\u817e\u80cc\u5305.", new Object[0]);
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
            this.error("\u5b58\u50a8\u4f1a\u8bdd\u5f02\u5e38: " + String.valueOf(t), new Object[0]);
            t.printStackTrace();
            this.stopTask("\u5b58\u50a8\u4f1a\u8bdd\u5f02\u5e38\uff0c\u505c\u6b62\u4efb\u52a1.");
        }
    }

    private void onPlaceEc() {
        if (this.storageTick == 1) {
            net.minecraft.util.math.BlockPos existing;
            if (this.ecOpenFailCount == 0 && (existing = this.findNearbyEnderChest(5)) != null) {
                this.ecPos = existing;
                this.info("\u5b58\u50a8: \u53d1\u73b0\u5468\u56f4\u5df2\u6709\u672b\u5f71\u7bb1 " + String.valueOf(existing) + "\uff0c\u76f4\u63a5\u6253\u5f00.", new Object[0]);
                this.storagePhase = StoragePhase.OPEN_EC;
                this.storageTick = 0;
                this.openRetry = 0;
                return;
            }
            int ecSlot = this.findItemSlot(net.minecraft.item.Items.ENDER_CHEST);
            if (ecSlot == -1) {
                this.stopTask("\u80cc\u5305\u91cc\u6ca1\u6709\u672b\u5f71\u7bb1\uff0c\u505c\u6b62\u4efb\u52a1.");
                return;
            }
            this.ecBaseline = this.countItem(net.minecraft.item.Items.ENDER_CHEST);
            this.ensureItemSelected(ecSlot);
            this.placeCandidates = this.findPlaceSpots(null);
            if (this.placeCandidates.isEmpty() && this.waypoints != null && this.waypoints.p2 != null) {
                this.info("\u9644\u8fd1\u65e0\u7a7a\u4f4d\uff0c\u6539\u7528\u68c0\u6d4b\u6f5c\u5f71\u8d1d\u7684\u4f4d\u7f6e " + String.valueOf(this.waypoints.p2) + " \u9644\u8fd1\u653e\u7f6e.", new Object[0]);
                this.placeCandidates = this.findPlaceSpotsAt(this.waypoints.p2, null);
            }
            if (this.placeCandidates.isEmpty()) {
                this.stopTask("\u627e\u4e0d\u5230\u653e\u7f6e\u672b\u5f71\u7bb1\u7684\u4f4d\u7f6e\uff0c\u505c\u6b62\u4efb\u52a1.");
                return;
            }
            this.placeIndex = 0;
            this.lastPlaceClick = -100;
            this.placeClickAttempts = 0;
        }
        if (this.storageTick >= 4 && this.storageTick - this.lastPlaceClick >= 8) {
            if (this.placeIndex >= this.placeCandidates.size()) {
                this.stopTask("\u672b\u5f71\u7bb1\u653e\u7f6e\u5931\u8d25 (\u5df2\u5c1d\u8bd5\u6240\u6709\u5019\u9009\u4f4d\u7f6e)\uff0c\u505c\u6b62\u4efb\u52a1.");
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
            this.stopTask("\u672b\u5f71\u7bb1\u653e\u7f6e\u5931\u8d25 (\u8d85\u65f6)\uff0c\u505c\u6b62\u4efb\u52a1.");
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
                this.info("\u672b\u5f71\u7bb1\u4e0d\u5728\u539f\u4f4d\uff0c\u91cd\u65b0\u653e\u7f6e.", new Object[0]);
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
                this.info("\u5b58\u50a8: \u73a9\u5bb6\u7ad9\u5728\u672b\u5f71\u7bb1\u4e0a\uff0c\u5148\u8d70\u4e0b\u6765\u518d\u6253\u5f00.", new Object[0]);
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
                this.info("\u6253\u5f00\u672b\u5f71\u7bb1\u5931\u8d25\u4e14\u73a9\u5bb6\u7ad9\u5728\u672b\u5f71\u7bb1\u4e0a\uff0c\u5148\u8d70\u4e0b\u6765\u518d\u91cd\u8bd5.", new Object[0]);
                return;
            }
            this.warning("\u6253\u5f00\u672b\u5f71\u7bb1\u5931\u8d25 (\u5df2\u5c1d\u8bd5 5 \u6b21)\uff0c\u653e\u7f6e\u65b0\u7684\u672b\u5f71\u7bb1\u518d\u8bd5.", new Object[0]);
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
            this.stopTask("\u672b\u5f71\u7bb1\u754c\u9762\u5f02\u5e38\uff0c\u505c\u6b62\u4efb\u52a1.");
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
                this.stopTask("\u80cc\u5305\u65e0\u7a7a\u4f4d\u653e\u6f5c\u5f71\u76d2\uff0c\u65e0\u6cd5\u8865\u8d27\uff0c\u505c\u6b62\u4efb\u52a1.");
                return;
            }
            this.stopTask("\u80cc\u5305\u65e0\u7a7a\u4f4d\u653e\u6f5c\u5f71\u76d2\uff0c\u65e0\u6cd5\u5b58\u9798\u7fc5\uff0c\u505c\u6b62\u4efb\u52a1.");
            return;
        }
        if (!this.moveIdle()) {
            return;
        }
        if (this.boxInventorySlot != -1) {
            int screenSlot = this.invSlotToScreen(rows, this.boxInventorySlot);
            if (this.firstEmptyContainerSlot(sh, rows) == -1) {
                this.stopTask("\u672b\u5f71\u7bb1\u5df2\u6ee1\uff0c\u65e0\u6cd5\u5f52\u8fd8\u6f5c\u5f71\u76d2\uff0c\u505c\u6b62\u4efb\u52a1.");
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
        this.info("\u5b58\u50a8: \u672b\u5f71\u7bb1\u884c\u6570=" + rows + " \u975e\u7a7a\u69fd=" + nonEmpty + " [" + String.valueOf(items) + "]", new Object[0]);
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
                this.completeTask("\u80cc\u5305\u4e0e\u672b\u5f71\u7bb1\u5168\u90e8\u653e\u6ee1\uff0c\u4efb\u52a1\u5b8c\u6210.");
                return;
            }
            this.info("\u6ca1\u6709\u7a7a\u4f4d\u6f5c\u5f71\u76d2\uff0c\u9798\u7fc5\u7559\u5728\u80cc\u5305.", new Object[0]);
            this.dumpNeeded = false;
        }
        if (this.resupplyNeeded) {
            if (this.resupplyRescan) {
                this.resupplyRescan = false;
                this.supplyScanStart = 0;
                this.info("\u5b58\u50a8: \u5b58\u9798\u7fc5\u817e\u51fa\u7a7a\u95f4\uff0c\u4ece\u672b\u5f71\u7bb1\u5f00\u5934\u91cd\u65b0\u626b\u63cf\u8865\u7ed9.", new Object[0]);
            }
            if (this.supplyScanStart > this.containerLast(rows)) {
                if (this.anyDeficit()) {
                    this.stopTask("\u7269\u8d44\u4e0d\u8db3 (\u672b\u5f71\u7bb1\u5185\u5df2\u904d\u5386\u5b8c\u6240\u6709\u6f5c\u5f71\u76d2)\uff0c\u505c\u6b62\u4efb\u52a1.");
                    return;
                }
                this.info("\u5b58\u50a8: \u8865\u8d27\u904d\u5386\u5b8c\u6210.", new Object[0]);
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
                            this.info("\u5b58\u50a8: \u4ece\u672b\u5f71\u7bb1\u76f4\u63a5\u62ff\u53d6\u6563\u653e\u7269\u8d44 " + st.getItem().getName().getString() + ".", new Object[0]);
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
                this.stopTask("\u5173\u95ed\u5bb9\u5668\u754c\u9762\u8d85\u65f6\uff0c\u505c\u6b62\u4efb\u52a1.");
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
                this.stopTask("\u6f5c\u5f71\u76d2\u69fd\u4f4d\u4e22\u5931\uff0c\u505c\u6b62\u4efb\u52a1.");
                return;
            }
            this.boxInventorySlot = this.ensureItemSelected(this.boxInventorySlot);
            this.placeCandidates = this.findBoxPlaceSpots(this.ecPos);
            this.boxPlaceFloorFallback = false;
            if (this.placeCandidates.isEmpty()) {
                this.info("\u9644\u8fd1\u6ca1\u6709\u6302\u653e\u4f4d\u7f6e (\u7a7a\u6c14+\u4e0a\u65b9\u5b9e\u5fc3)\uff0c\u6539\u7528\u5730\u677f\u653e\u7f6e.", new Object[0]);
                this.placeCandidates = this.findBoxFloorSpots(this.ecPos);
                this.boxPlaceFloorFallback = true;
            }
            if (this.placeCandidates.isEmpty()) {
                this.stopTask("\u627e\u4e0d\u5230\u653e\u7f6e\u6f5c\u5f71\u76d2\u7684\u4f4d\u7f6e (\u9700\u8981\u7a7a\u6c14\u683c\u4e14\u4e0a\u65b9\u6709\u65b9\u5757)\uff0c\u505c\u6b62\u4efb\u52a1.");
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
                    this.info("\u6302\u653e\u4f4d\u7f6e\u5168\u90e8\u653e\u7f6e\u5931\u8d25\uff0c\u6539\u7528\u5730\u677f\u653e\u7f6e.", new Object[0]);
                    this.placeCandidates = this.findBoxFloorSpots(this.ecPos);
                    this.boxPlaceFloorFallback = true;
                    this.placeIndex = 0;
                    this.placeClickAttempts = 0;
                    if (this.placeCandidates.isEmpty()) {
                        this.stopTask("\u6f5c\u5f71\u76d2\u653e\u7f6e\u5931\u8d25 (\u627e\u4e0d\u5230\u5730\u677f\u653e\u7f6e\u4f4d\u7f6e)\uff0c\u505c\u6b62\u4efb\u52a1.");
                        return;
                    }
                } else {
                    this.stopTask("\u6f5c\u5f71\u76d2\u653e\u7f6e\u5931\u8d25 (\u5df2\u5c1d\u8bd5\u6240\u6709\u5019\u9009\u4f4d\u7f6e)\uff0c\u505c\u6b62\u4efb\u52a1.");
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
            this.stopTask("\u6f5c\u5f71\u76d2\u653e\u7f6e\u5931\u8d25 (\u8d85\u65f6)\uff0c\u505c\u6b62\u4efb\u52a1.");
        }
    }

    private void onOpenBox() {
        if (this.storageTick == 1) {
            if (this.boxPos == null || !this.isShulkerBlock(this.mc.world.getBlockState(this.boxPos))) {
                this.warning("\u6f5c\u5f71\u76d2\u4e0d\u5728\u539f\u4f4d\uff0c\u6316\u6389\u91cd\u6765.", new Object[0]);
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
                this.info("\u5b58\u50a8: \u73a9\u5bb6\u7ad9\u5728\u6f5c\u5f71\u76d2\u4e0a\uff0c\u5148\u8d70\u4e0b\u6765\u518d\u6253\u5f00.", new Object[0]);
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
                this.info("\u6253\u5f00\u6f5c\u5f71\u76d2\u5931\u8d25\u4e14\u73a9\u5bb6\u7ad9\u5728\u6f5c\u5f71\u76d2\u4e0a\uff0c\u5148\u8d70\u4e0b\u6765\u518d\u91cd\u8bd5.", new Object[0]);
                return;
            }
            this.warning("\u6253\u5f00\u6f5c\u5f71\u76d2\u5931\u8d25 (\u5df2\u5c1d\u8bd5 5 \u6b21)\uff0c\u6316\u6389\u91cd\u6765.", new Object[0]);
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
            this.stopTask("\u6f5c\u5f71\u76d2\u754c\u9762\u5f02\u5e38\uff0c\u505c\u6b62\u4efb\u52a1.");
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
            this.info("\u5b58\u50a8: \u5f00\u59cb\u5f80\u6f5c\u5f71\u76d2\u5b58\u9798\u7fc5.", new Object[0]);
        }
        if (this.fillStuckSlot >= 0) {
            if (sh.getSlot(this.fillStuckSlot).getStack().getItem() == net.minecraft.item.Items.ELYTRA) {
                ++this.fillStuckTicks;
                if (this.fillStuckTicks > 20) {
                    this.fillIndex = -1;
                    this.fillStuckSlot = -1;
                    this.fillStuckTicks = 0;
                    this.info("\u5b58\u50a8: \u6f5c\u5f71\u76d2\u5df2\u6ee1.", new Object[0]);
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
            this.info("\u5b58\u50a8: \u9798\u7fc5\u5df2\u5168\u90e8\u5b58\u5165\u6f5c\u5f71\u76d2.", new Object[0]);
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
            this.stopTask("\u6f5c\u5f71\u76d2\u754c\u9762\u5f02\u5e38\uff0c\u505c\u6b62\u4efb\u52a1.");
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
                                this.info("\u5b58\u50a8: \u80cc\u5305\u7a7a\u95f4\u4e0d\u8db3\uff0c\u5148\u628a\u9798\u7fc5\u653e\u5165\u76d2\u5b50\u817e\u51fa\u7a7a\u4f4d.", new Object[0]);
                            } else {
                                int junk = this.findJunkSlotInPlayer(sh, rows, rules);
                                if (junk != -1) {
                                    this.moveQueue.add(new MoveOp(junk, boxFree));
                                    this.info("\u5b58\u50a8: \u80cc\u5305\u5df2\u6ee1\uff0c\u5148\u628a\u6742\u7269\u653e\u5165\u76d2\u5b50\u817e\u51fa\u7a7a\u4f4d.", new Object[0]);
                                } else {
                                    this.takeFailedItems.add(target.item);
                                    this.info("\u5b58\u50a8: \u80cc\u5305\u65e0\u7a7a\u4f4d\u4e14\u65e0\u6742\u7269\u53ef\u817e\uff0c\u8865\u8d27 " + target.item.getName().getString() + " \u8df3\u8fc7.", new Object[0]);
                                }
                            }
                        } else {
                            this.takeFailedItems.add(target.item);
                            this.info("\u5b58\u50a8: \u76d2\u5b50\u5df2\u6ee1\u65e0\u6cd5\u817e\u4f4d\uff0c\u8865\u8d27 " + target.item.getName().getString() + " \u8df3\u8fc7.", new Object[0]);
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
        this.info("\u5b58\u50a8: \u672c\u76d2\u7269\u8d44\u5df2\u62ff\u53d6\u5b8c\u6bd5 (\u6240\u6709\u80fd\u62ff\u4e14\u9700\u8981\u7684).", new Object[0]);
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
                    this.stopTask("\u9700\u8981\u7cbe\u51c6\u91c7\u96c6\u5de5\u5177\u624d\u80fd\u56de\u6536\u6f5c\u5f71\u76d2\uff0c\u505c\u6b62\u4efb\u52a1.");
                    return;
                }
                PathManagers.get().mine(s.getBlock());
                this.info("\u7528 baritone (\u7cbe\u51c6\u91c7\u96c6) \u6316\u6398\u6f5c\u5f71\u76d2.", new Object[0]);
            } else if (this.storageTick > 600) {
                this.stopTask("baritone \u6316\u6398\u6f5c\u5f71\u76d2\u8d85\u65f6\uff0c\u505c\u6b62\u4efb\u52a1.");
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
            this.warning("\u6f5c\u5f71\u76d2\u6389\u843d\u672a\u62fe\u53d6\uff0c\u7ee7\u7eed\u4efb\u52a1.", new Object[0]);
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
                    this.stopTask("\u9700\u8981\u7cbe\u51c6\u91c7\u96c6\u5de5\u5177\u624d\u80fd\u56de\u6536\u672b\u5f71\u7bb1\uff0c\u505c\u6b62\u4efb\u52a1.");
                    return;
                }
                PathManagers.get().mine(net.minecraft.block.Blocks.ENDER_CHEST);
                this.info("\u7528 baritone (\u7cbe\u51c6\u91c7\u96c6) \u6316\u6398\u672b\u5f71\u7bb1.", new Object[0]);
            } else if (this.storageTick > 600) {
                this.stopTask("baritone \u6316\u6398\u672b\u5f71\u7bb1\u8d85\u65f6\uff0c\u505c\u6b62\u4efb\u52a1.");
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
            this.warning("\u672b\u5f71\u7bb1\u6389\u843d\u672a\u62fe\u53d6 (\u6316\u672b\u5f71\u7bb1\u9700\u8981\u7cbe\u51c6\u91c7\u96c6)\uff0c\u7ee7\u7eed\u4efb\u52a1.", new Object[0]);
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
        if (this.state == State.COLLECTING && this.collectStep == CollectStep.EQUIP) {
            this.info("\u8239\u65c1\u5b58\u50a8\u4f1a\u8bdd\u7ed3\u675f\uff0c\u5148\u8d70\u56de\u964d\u843d\u70b9\u518d\u8d77\u98de.", new Object[0]);
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
                this.warning("\u7269\u8d44\u914d\u7f6e\u683c\u5f0f\u9519\u8bef: " + entry, new Object[0]);
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
                this.warning("\u7269\u8d44\u6570\u91cf\u683c\u5f0f\u9519\u8bef: " + entry, new Object[0]);
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
            this.debugLog("city=(" + chunkX + "," + chunkZ + ") rotation=" + String.valueOf(rotation) + " min=" + min);
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
        this.debugLog("col(" + x + "," + z + ") real=" + realHeight + " vanilla=" + vanilla + " E=" + String.format("%.4f", e) + " rawF=" + String.format("%.1f", Float.valueOf(rawF)));
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
            this.debugLog("city=(" + chunkX + "," + chunkZ + ") rotation=" + String.valueOf(rotation) + " predMin=" + min);
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
            this.error("\u8c03\u8bd5\u65e5\u5fd7\u5199\u5165\u5931\u8d25: " + e.getMessage(), new Object[0]);
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
            this.info("\u653b\u51fb\u5c55\u793a\u6846.", new Object[0]);
        } else {
            this.info("\u672a\u627e\u5230\u5c55\u793a\u6846.", new Object[0]);
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
        this.info("\u5df2\u7a7f\u4e0a\u9798\u7fc5.", new Object[0]);
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

    private void useFirework() {
        FindItemResult fw = InvUtils.findInHotbar((net.minecraft.item.Item[])new net.minecraft.item.Item[]{net.minecraft.item.Items.FIREWORK_ROCKET});
        if (!fw.found()) {
            FindItemResult inv = InvUtils.find((net.minecraft.item.Item[])new net.minecraft.item.Item[]{net.minecraft.item.Items.FIREWORK_ROCKET});
            if (inv.found()) {
                InvUtils.move().from(inv.slot()).toHotbar(1);
                this.info("\u7269\u54c1\u680f\u6ca1\u6709\u70df\u82b1\uff0c\u4ece\u80cc\u5305\u8c03\u53d6\u653e\u5230\u7b2c 2 \u4e2a\u69fd\u4f4d.", new Object[0]);
            }
            return;
        }
        if (fw.isOffhand()) {
            this.mc.interactionManager.interactItem((net.minecraft.entity.player.PlayerEntity)this.mc.player, net.minecraft.util.Hand.OFF_HAND);
        } else {
            InvUtils.swap((int)fw.slot(), (boolean)true);
            this.mc.interactionManager.interactItem((net.minecraft.entity.player.PlayerEntity)this.mc.player, net.minecraft.util.Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
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
                this.debugLog("前方区块已加载，结束防卡拉升，恢复 pitch40 飞行.");
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
        IDLE,
        SEARCHING,
        RISING,
        FLYING,
        LANDING,
        COLLECTING,
        DONE;

    }

    private static enum CollectStep {
        TO_P1,
        TO_P2,
        WAIT_SHULKER,
        TO_P3,
        HIT_FRAME,
        PICK_UP,
        EQUIP,
        EXIT_P1,
        EXIT_LANDING,
        LEAVE_SHIP;

    }

    private static enum StoragePhase {
        NONE,
        PLACE_EC,
        OPEN_EC,
        PICK_BOX,
        CLOSE_SCREEN,
        PLACE_BOX,
        OPEN_BOX,
        FILL_BOX,
        TAKE_SUPPLIES,
        MINE_BOX,
        MINE_EC;

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
