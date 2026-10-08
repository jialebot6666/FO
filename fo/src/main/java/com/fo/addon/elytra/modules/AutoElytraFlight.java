package com.fo.addon.elytra.modules;

import baritone.api.Settings;
import com.fo.addon.elytra.FOElytraModule;
import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.BlockBreaker;
import com.fo.addon.elytra.core.BounceProbe;
import com.fo.addon.elytra.core.EatController;
import com.fo.addon.elytra.core.FindPathToOpen;
import com.fo.addon.elytra.core.FireballDeflector;
import com.fo.addon.elytra.core.FoodPriority;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.InventoryRestocker;
import com.fo.addon.elytra.core.ItemHelper;
import com.fo.addon.elytra.core.JunkDropper;
import com.fo.addon.elytra.core.LavaEscape;
import com.fo.addon.elytra.core.LavaPredictor;
import com.fo.addon.elytra.core.MendTask;
import com.fo.addon.elytra.core.Needs;
import com.fo.addon.elytra.core.OrderedItemListSetting;
import com.fo.addon.elytra.core.OrderedItemListWidget;
import com.fo.addon.elytra.core.PlayerAction;
import com.fo.addon.elytra.core.SettingHelper;
import com.fo.addon.elytra.core.StuckEscape;
import com.fo.addon.elytra.core.SupplyOptions;
import com.fo.addon.elytra.core.SupplyTask;
import com.fo.addon.elytra.core.TaskStatus;
import com.fo.addon.elytra.core.TimelinessCounter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

public class AutoElytraFlight
extends FOElytraModule {
    private final SettingGroup sgTarget;
    private final SettingGroup sgFlight;
    private final SettingGroup sgBaritone;
    private final SettingGroup sgBtSafety;
    private final SettingGroup sgFireball;
    private final SettingGroup sgLava;
    private final SettingGroup sgLavaPredict;
    private final SettingGroup sgSupplyTrigger;
    private final SettingGroup sgSupplyAmount;
    private final SettingGroup sgSupplyExec;
    private final SettingGroup sgEat;
    private final SettingGroup sgMend;
    private final SettingGroup sgSafety;
    private final SettingGroup sgNether;
    private final SettingGroup sgLandSafety;
    private final SettingGroup sgJunk;
    private final SettingGroup sgDebug;
    private final EnumSetting<Mode> mode;
    private final StringListSetting waypoints;
    private final IntSetting targetX;
    private final IntSetting targetZ;
    private final IntSetting segmentDistance;
    private final IntSetting arriveRadius;
    private final BoolSetting loop;
    private final BoolSetting autoTakeoff;
    private final IntSetting takeoffTimeout;
    private final BoolSetting takeoffAutoJumpFallback;
    private final BoolSetting openAreaSearch;
    private final DoubleSetting openSearchDist;
    private final DoubleSetting openSafeDist;
    private final IntSetting openTries;
    private final BoolSetting allowAscend;
    private final IntSetting jumpBeforeOpen;
    private final BoolSetting clearHeadBlock;
    private final BoolSetting fireworkRefill;
    private final IntSetting fireworkHotbarMin;
    private final BoolSetting takeoffFirework;
    private final BoolSetting takeoffByBaritone;
    private final BoolSetting chunkWait;
    private final DoubleSetting unloadedRatio;
    private final IntSetting chunkRadius;
    private final IntSetting hoverTimeout;
    private final BoolSetting btSafetyTakeover;
    private final DoubleSetting btAvoidMargin;
    private final IntSetting btLookahead;
    private final IntSetting netherReplanSeconds;
    private final BoolSetting netherPredictOff;
    private final BoolSetting noSupplyInBasaltDeltas;
    private final IntSetting landSafeRadius;
    private final BoolSetting landAvoidMobs;
    private final BoolSetting landSkipWhenCrowded;
    private final BoolSetting hurtAbortSupply;
    private final BoolSetting torchBeforeSupply;
    private final BoolSetting pauseOnPlayers;
    private final DoubleSetting playerRange;
    private final BoolSetting infinityElytra;
    private final BoolSetting btTermsAccepted;
    private final BoolSetting btAutoJump;
    private final DoubleSetting btFireworkSpeed;
    private final BoolSetting btConserveFireworks;
    private final BoolSetting btAutoSwap;
    private final BoolSetting btPredictTerrain;
    private final BoolSetting btFreeLook;
    private final BoolSetting btSmoothLook;
    private final IntSetting btPitchRange;
    private final BoolSetting btAllowEmergencyLand;
    private final IntSetting btMinFireworksBeforeLanding;
    private final IntSetting btMinimumDurability;
    private final BoolSetting btAllowLandOnNetherFortress;
    private final BoolSetting btChatSpam;
    private final BoolSetting btRenderSimulation;
    private final KeybindSetting baritoneSaveKey;
    private WLabel baritoneStatusLabel;
    private WLabel logPathLabel;
    private boolean baritoneKeyWasPressed;
    private final BoolSetting deflectFireballs;
    private final DoubleSetting fireballRange;
    private final IntSetting fireballMax;
    private final BoolSetting fireballPauseBaritone;
    private final BoolSetting fireballFailOnMultiple;
    private final BoolSetting lavaEscape;
    private final BoolSetting lavaIgnoreGlidingFire;
    private final BoolSetting lavaSwimToSafety;
    private final IntSetting lavaSearchRadius;
    private final BoolSetting lavaDrinkFireRes;
    private final DoubleSetting lavaLookPitch;
    private final BoolSetting lavaUseFirework;
    private final BoolSetting lavaFailAbort;
    private final BoolSetting lavaPredictEnabled;
    private final DoubleSetting lavaPredictHorizon;
    private final DoubleSetting lavaPredictUrgent;
    private final IntSetting lavaPredictLateral;
    private final IntSetting lavaPredictReturn;
    private final IntSetting lavaPredictCooldown;
    private final BoolSetting lavaPredictWarnOnly;
    private final BoolSetting lavaPredictPauseBaritone;
    private final DoubleSetting lavaPredictDeflect;
    private final BoolSetting autoSupply;
    private final BoolSetting supplyBeforeSegment;
    private final IntSetting minFireworkStacks;
    private final IntSetting minFoodCount;
    private final IntSetting minXpBottles;
    private final IntSetting minTotems;
    private final IntSetting minElytraDurability;
    private final BoolSetting landForSupply;
    private final IntSetting maxSupplyRetries;
    private final IntSetting supplyErrorRetries;
    private final IntSetting supplyRetryDelay;
    private final BoolSetting autoRestock;
    private final ItemListSetting restockItems;
    private final IntSetting restockStacks;
    private final IntSetting restockInterval;
    private final BoolSetting restockKeepHeld;
    private final BoolSetting restockTriggerSupply;
    private final BoolSetting fullSupplyOnStart;
    private final KeybindSetting supplyKey;
    private final IntSetting targetFireworkStacks;
    private final IntSetting targetXpBottles;
    private final IntSetting targetFoodCount;
    private final IntSetting targetTotems;
    private final IntSetting targetElytraCount;
    private final IntSetting minEnderChests;
    private final IntSetting maxShulkers;
    private final IntSetting actionDelay;
    private final IntSetting placeRadius;
    private final BoolSetting autoPlaceEnderChest;
    private final BoolSetting autoPickupEnderChest;
    private final BoolSetting useBaritoneMine;
    private final BoolSetting storeLoot;
    private final ItemListSetting storeItems;
    private final ItemListSetting supplyFoodItems;
    private final BoolSetting autoEat;
    private final IntSetting hungerThreshold;
    private final DoubleSetting healthThreshold;
    private final BoolSetting eatWhileGliding;
    private final DoubleSetting eatMinRise;
    private final ItemListSetting foodWhitelist;
    private final OrderedItemListSetting foodPriorityOrdered;
    private final StringSetting foodPriority;
    private final BoolSetting junkDrop;
    private final BoolSetting junkOnlyLava;
    private final IntSetting junkRadius;
    private final ItemListSetting junkItems;
    private final BoolSetting autoMend;
    private final IntSetting mendDurability;
    private final KeybindSetting mendKey;
    private final IntSetting maxMendRetries;
    private final IntSetting minBottles;
    private final IntSetting repairToDamage;
    private final BoolSetting requireGround;
    private final IntSetting landingTimeout;
    private final DoubleSetting mendPitch;
    private final IntSetting throwDelay;
    private final IntSetting maxThrows;
    private final BoolSetting requireMending;
    private final BoolSetting requireNetherWastes;
    private final BoolSetting autoLogout;
    private final DoubleSetting logoutHealth;
    private final IntSetting logoutTotemMin;
    private final BoolSetting logoutOnFailure;
    private final BoolSetting logoutOnSupplyFail;
    private final BoolSetting logoutOnArrive;
    private final BoolSetting disableOnFinish;
    private final IntSetting noElytraWaitSec;
    private final BoolSetting debugMessages;
    private final BoolSetting hudInfo;
    private final BoolSetting statusMonitor;
    private final BoolSetting detailLog;
    private final BoolSetting logSteps;
    private final IntSetting logKeep;
    private final EatController eat;
    private final FireballDeflector fireballs;
    private LavaEscape lava;
    private boolean lavaWasEscaping;
    private int lavaCacheRadius;
    private boolean lavaCacheIgnoreGliding;
    private boolean lavaCacheSwim;
    private boolean lavaCachePotion;
    private boolean lavaCacheFirework;
    private double lavaCachePitch;
    private LavaPredictor lavaPredictor;
    private boolean pdCacheEnabled;
    private double pdCacheHorizon;
    private double pdCacheUrgent;
    private int pdCacheLateral;
    private int pdCacheReturn;
    private int pdCacheCooldown;
    private boolean pdCacheWarnOnly;
    private boolean pdCachePauseBaritone;
    private double pdCacheDeflect;
    private SupplyTask supplyTask;
    private MendTask mendTask;
    private boolean manualTask;
    private final InventoryRestocker restocker;
    private final JunkDropper junkDropper;
    private boolean startFullSupplyDone;
    private boolean startFullSupplyPending;
    private boolean supplyFailPhase;
    private final Set<Item> exhaustedItems;
    private boolean foodExhaustedCache;
    private int exhaustedCooldown;
    private static final int SUPPLY_EXHAUSTED_COOLDOWN = 2400;
    private static final int SUPPLY_NO_PROGRESS_LIMIT = 1;
    private String lastSupplyStockSignature;
    private int supplyNoProgressRounds;
    private boolean logFileOwned;
    private int noElytraWaitTicks;
    private boolean suppressLogout;
    private int supplyTicks;
    private State state;
    private String failReason;
    private final List<BlockPos> route;
    private int routeIndex;
    private BlockPos segmentTarget;
    private boolean directionInitialised;
    private float directionFrozen;
    private int tickCounter;
    private int waitTicks;
    private int takeoffTicks;
    private TakeoffPhase takeoffPhase;
    private int jumpSeq;
    private int jumpSeqTicks;
    private int jumpAttempts;
    private int jumpAttemptTick;
    private int jumpPhase;
    private int jumpIteration;
    private int takeoffDelayTicks;
    private int takeoffReArmCount;
    private int takeoffFireworkPending;
    private int btTakeoffWaitTick;
    private boolean btTakeoffFallbackLogged;
    private BlockPos shaftColumn;
    private int shaftTicks;
    private int airJumpHold;
    private final TimelinessCounter fakeGlideWindow;
    private int fakeGlideRecovers;
    private int fakeGlidePhase;
    private int fakeGlidePhaseTicks;
    private String takeoffFlowOwner;
    private int takeoffFlowHoldTicks;
    private int controlTicks;
    private int lostControlCycles;
    private int airborneTicks;
    private int notGlidingAirTicks;
    private int fireworkSeenTotal;
    private int mendSkipLogTick;
    private int usingItemTicks;
    private int usingItemLogTick;
    private final TimelinessCounter openLavaWindow;
    private int lastFireworkConsumeTick;
    private int fireworkStallLogTick;
    private int activityTicks;
    private double lastActivityDistance;
    private int fallTicks;
    private int launchWait;
    private int glidingLostTicks;
    private int openTriesDone;
    private int clearWaited;
    private boolean clearingHead;
    private double ascendTargetY;
    private int ascendTicks;
    private BlockPos openEnd;
    private double openStartY;
    private int hopTicks;
    private int openSearchFails;
    private boolean ascendingSearch;
    private double searchYh;
    private int viewHoldTicks;
    private boolean hovering;
    private boolean pausedByPlayer;
    private int lastCheckX;
    private int lastCheckZ;
    private final TimelinessCounter segFailWindow;
    private final TimelinessCounter spinWindow;
    private int spinPauseTicks;
    private BlockPos lastSpinPos;
    private int lowFireworkWarnTick;
    private boolean segResetDone;
    private boolean forceFlyToOpen;
    private int supplyRetries;
    private int supplyErrorRetryCount;
    private int supplyCooldown;
    private int mendRetries;
    private int mendCooldown;
    private int hoverStart;
    private boolean fireballTooManyWarned;
    private boolean supplyKeyWasPressed;
    private boolean mendKeyWasPressed;
    private boolean flightSettingsApplied;
    private boolean takeoffAutoJumpUsed;
    private boolean baritoneAutoJumpForced;
    private int supplyHurtNoLand;
    private int eatHoldTicks;
    private boolean lavaPriorityActive;
    private double landingY;
    private double landingAngle;
    private double landingTargetX;
    private double landingTargetZ;
    private int landingTicks;
    private static final float CHUNK_WAIT_EXIT_RATIO = 0.05f;
    private static final int FIREWORK_LOW_TOTAL = 128;
    private static final int FIREWORK_STALL_TICKS = 600;
    private static final int FIREWORK_STALL_LOG_INTERVAL = 200;
    private static final double LANDING_SPIRAL_MIN_RADIUS = 4.0;
    private static final double LANDING_SPIRAL_DIVISOR = 8.0;
    private static final double LANDING_NO_FIREWORK_ABOVE = 20.0;
    private static final double LANDING_SPIRAL_STEP = 0.25;
    private static final int LANDING_UNREACHABLE_COOLDOWN = 600;
    private static final int BASALT_DELTAS_NO_SUPPLY_COOLDOWN = 600;
    private static final int RECOVER_WATCHDOG_TICKS = 500;
    private static final int NETHER_RESET_MIN_TICKS = 200;
    private static final int FAKE_GLIDE_CONFIRM_TICKS = 20;
    private static final int FAKE_GLIDE_RECOVER_MAX = 3;
    private static final int FAKE_GLIDE_RELEASE_TICKS = 5;
    private static final int FAKE_GLIDE_JUMP_HOLD_TICKS = 2;
    private static final int FAKE_GLIDE_CHECK_TICKS = 15;
    private static final int TAKEOFF_FLOW_HOLD_TICKS = 20;
    private static final int REPLAN_MIN_TICKS = 60;
    private static final double RUST_FIREWORK_SPEED = 0.5;
    private static final int BT_TAKEOFF_WAIT_TICKS = 120;
    private int lastReplanTick;
    private boolean replanEver;
    private int lastResetTick;
    private Object prevNetherSeed;
    private boolean netherSeedSaved;
    private boolean btSafetySaved;
    private Boolean lastForcedAutoJump;
    private Double prevBtAvoid;
    private Integer prevBtLookahead;
    private Double lastAppliedAvoid;
    private Integer lastAppliedLook;
    private int mobHitsInSegment;
    private int landSkippedThreat;
    private int landSkippedBasalt;
    private int landSkippedUnsafe;
    private int landSkippedAbove;
    private boolean basaltSkipHandled;
    private boolean basaltBiomeUnknown;
    private boolean recoverArmed;
    private int recoverWatchdog;
    private RecoverAfter recoverAfter;
    private Object recoverRunner;
    private String pendingFailReason;
    private String pendingFinishMessage;
    private boolean recoveryJustRan;
    private double lastHurtHealth;
    private boolean torchPlacedThisLanding;
    private static final int LAND_THREAT_CROWD_RADIUS = 24;
    private static final int LAND_THREAT_CROWD_COUNT = 5;
    private static final int LAND_UNDER_SCAN = 10;
    private static final int LAND_SAFE_ABOVE = 3;
    private static final int LAND_NO_SAFE_SPOT_COOLDOWN = 600;
    private static final int LAND_ABORT_CHECK_TICKS = 10;
    private static final int EAT_LAVA_HOLD_TICKS = 60;
    private static final int THREAT_VERTICAL_BAND = 8;
    private static final int SUPPLY_HURT_COOLDOWN = 600;
    private static final List<String> CONFLICT_FLIGHT = List.of("ElytraFly", "Flight", "ElytraBoost", "TridentBoost");
    private static final List<String> CONFLICT_ITEMS = List.of("AutoEat", "AutoMend", "AutoReplenish", "ChestSwap", "InventoryTweaks");
    private String foodPriorityRaw;
    private List<Item> foodPriorityParsed;

    private static String headingName(float yaw) {
        float y = (yaw % 360.0f + 360.0f) % 360.0f;
        if (y < 45.0f || y >= 315.0f) {
            return "\u6b63\u5357 +Z";
        }
        if (y < 135.0f) {
            return "\u6b63\u897f -X";
        }
        if (y < 225.0f) {
            return "\u6b63\u5317 -Z";
        }
        return "\u6b63\u4e1c +X";
    }

    private BoolSetting btBool(String btName, String label, String desc) {
        boolean def = BaritoneHook.btDefaultBool(btName, false);
        return (BoolSetting)this.sgBaritone.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name(label)).description(desc + "  [Baritone: " + btName + "]")).defaultValue(def)).onChanged(v -> BaritoneHook.btSet(btName, v))).onModuleActivated(s -> s.set(BaritoneHook.btBool(btName, def)))).build());
    }

    private IntSetting btInt(String btName, String label, String desc, int min, int max) {
        int def = AutoElytraFlight.clamp(BaritoneHook.btDefaultInt(btName, min), min, max);
        return (IntSetting)this.sgBaritone.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name(label)).description(desc + "  [Baritone: " + btName + "]")).defaultValue(def)).min(min).max(max).sliderRange(min, max).onChanged(v -> BaritoneHook.btSet(btName, v))).onModuleActivated(s -> s.set(AutoElytraFlight.clamp(BaritoneHook.btInt(btName, def), min, max)))).build());
    }

    private DoubleSetting btDouble(String btName, String label, String desc, double min, double max) {
        double def = AutoElytraFlight.clamp(BaritoneHook.btDefaultDouble(btName, min), min, max);
        return (DoubleSetting)this.sgBaritone.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name(label)).description(desc + "  [Baritone: " + btName + "]")).defaultValue(def).min(min).max(max).sliderRange(min, max).decimalPlaces(2).onChanged(v -> BaritoneHook.btSet(btName, v))).onModuleActivated(s -> s.set(AutoElytraFlight.clamp(BaritoneHook.btDouble(btName, def), min, max)))).build());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private List<Map.Entry<String, Setting<?>>> baritoneBindings() {
        ArrayList list = new ArrayList();
        list.add(Map.entry("elytraTermsAccepted", this.btTermsAccepted));
        list.add(Map.entry("elytraAutoJump", this.btAutoJump));
        list.add(Map.entry("elytraFireworkSpeed", this.btFireworkSpeed));
        list.add(Map.entry("elytraConserveFireworks", this.btConserveFireworks));
        list.add(Map.entry("elytraAutoSwap", this.btAutoSwap));
        list.add(Map.entry("elytraPredictTerrain", this.btPredictTerrain));
        list.add(Map.entry("elytraFreeLook", this.btFreeLook));
        list.add(Map.entry("elytraSmoothLook", this.btSmoothLook));
        list.add(Map.entry("elytraPitchRange", this.btPitchRange));
        list.add(Map.entry("elytraAllowEmergencyLand", this.btAllowEmergencyLand));
        list.add(Map.entry("elytraMinFireworksBeforeLanding", this.btMinFireworksBeforeLanding));
        list.add(Map.entry("elytraMinimumDurability", this.btMinimumDurability));
        list.add(Map.entry("elytraAllowLandOnNetherFortress", this.btAllowLandOnNetherFortress));
        list.add(Map.entry("elytraChatSpam", this.btChatSpam));
        list.add(Map.entry("elytraRenderSimulation", this.btRenderSimulation));
        return list;
    }

    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton apply = (WButton)table.add((WWidget)theme.button("\u5e94\u7528 Baritone \u8bbe\u7f6e")).expandX().minWidth(120.0).widget();
        apply.action = () -> this.applyBaritoneFromPanel(false);
        table.row();
        WButton save = (WButton)table.add((WWidget)theme.button("\u4fdd\u5b58\u5e76\u8bbe\u4e3a\u9ed8\u8ba4")).expandX().minWidth(120.0).widget();
        save.action = () -> this.applyBaritoneFromPanel(true);
        table.row();
        WButton openLog = (WButton)table.add((WWidget)theme.button("\u6253\u5f00\u65e5\u5fd7\u6587\u4ef6\u5939")).expandX().minWidth(120.0).widget();
        openLog.action = this::openLogFolder;
        table.row();
        this.logPathLabel = (WLabel)table.add((WWidget)theme.label(this.logPathText())).expandCellX().widget();
        table.row();
        this.baritoneStatusLabel = (WLabel)table.add((WWidget)theme.label(this.baritoneStatus())).expandCellX().widget();
        return table;
    }

    private Path logDirectory() {
        return FOElytraLog.directory();
    }

    private String logPathText() {
        if (!((Boolean)this.detailLog.get()).booleanValue()) {
            return "\u8be6\u7ec6\u65e5\u5fd7\uff1a\u5df2\u5173\u95ed\uff08\u6253\u5f00\u4e0a\u9762\u7684\u5f00\u5173\u5c31\u4f1a\u5f00\u59cb\u5199\u6587\u4ef6\uff09";
        }
        Path cur = FOElytraLog.currentFile();
        return cur == null ? "\u8be6\u7ec6\u65e5\u5fd7\uff1a\u76ee\u5f55 " + String.valueOf(this.logDirectory()) : "\u8be6\u7ec6\u65e5\u5fd7\uff1a" + String.valueOf(this.logDirectory().getFileName()) + "/" + String.valueOf(cur.getFileName());
    }

    private void openLogFolder() {
        try {
            Path dir = this.logDirectory();
            Files.createDirectories(dir, new FileAttribute[0]);
            Util.getOperatingSystem().open(dir.toFile());
            FOElytraLog.info("\u5df2\u6253\u5f00\u65e5\u5fd7\u76ee\u5f55\uff1a%s", dir);
        }
        catch (Throwable t) {
            FOElytraLog.warn("\u6253\u4e0d\u5f00\u65e5\u5fd7\u76ee\u5f55\uff08%s\uff09\u2014\u2014 \u624b\u52a8\u53bb\u8fd9\u91cc\u770b\uff1a%s", t, this.logDirectory());
        }
        if (this.logPathLabel != null) {
            this.logPathLabel.set(this.logPathText());
        }
    }

    private void applyBaritoneFromPanel(boolean saveAsDefault) {
        if (!BaritoneHook.available()) {
            this.error("\u6ca1\u6709\u68c0\u6d4b\u5230 Baritone\uff1a\u8fd9\u4e9b\u8bbe\u7f6e\u65e0\u6cd5\u5199\u5165\u3002", new Object[0]);
            this.refreshBaritoneStatus();
            return;
        }
        int applied = 0;
        for (Map.Entry<String, Setting<?>> binding : this.baritoneBindings()) {
            if (!BaritoneHook.btSet(binding.getKey(), binding.getValue().get())) continue;
            ++applied;
        }
        if (saveAsDefault) {
            boolean saved = BaritoneHook.saveBaritone();
            try {
                Modules.get().save();
            }
            catch (Throwable t) {
                LOG.warn("\u4fdd\u5b58 Meteor \u914d\u7f6e\u5931\u8d25", t);
            }
            if (saved) {
                this.info("\u5df2\u5e94\u7528 %d \u9879\u5e76\u4fdd\u5b58\u4e3a\u9ed8\u8ba4\uff08baritone/settings.txt + Meteor \u914d\u7f6e\uff09", new Object[]{applied});
            } else {
                this.warning("\u5df2\u5e94\u7528 %d \u9879\uff0c\u4f46\u5199\u5165 baritone/settings.txt \u5931\u8d25\uff08\u770b\u65e5\u5fd7\uff09", new Object[]{applied});
            }
        } else {
            this.info("\u5df2\u5e94\u7528 %d \u9879 Baritone \u8bbe\u7f6e\uff08\u4ec5\u672c\u6b21\u8fd0\u884c\uff0c\u91cd\u542f\u540e\u6062\u590d\uff09", new Object[]{applied});
        }
        this.refreshBaritoneStatus();
    }

    private String baritoneStatus() {
        if (!BaritoneHook.available()) {
            return "Baritone: \u672a\u68c0\u6d4b\u5230 \u2014\u2014 \u8fd9\u4e9b\u8bbe\u7f6e\u4e0d\u4f1a\u751f\u6548";
        }
        int modified = BaritoneHook.modifiedCount();
        return modified >= 0 ? "Baritone: \u5df2\u52a0\u8f7d\uff0c\u5f53\u524d\u6709 " + modified + " \u9879\u4e0e\u51fa\u5382\u9ed8\u8ba4\u503c\u4e0d\u540c" : "Baritone: \u5df2\u52a0\u8f7d";
    }

    private void refreshBaritoneStatus() {
        if (this.baritoneStatusLabel != null) {
            this.baritoneStatusLabel.set(this.baritoneStatus());
        }
    }

    private void baritoneKeyTick() {
        boolean pressed;
        boolean bl = pressed = this.baritoneSaveKey.get() != null && ((Keybind)this.baritoneSaveKey.get()).isPressed();
        if (pressed && !this.baritoneKeyWasPressed) {
            this.applyBaritoneFromPanel(true);
        }
        this.baritoneKeyWasPressed = pressed;
    }

    public AutoElytraFlight() {
        super("FO \u81ea\u52a8\u9798\u7fc5\u98de\u884c", "\u5168\u81ea\u52a8\u9798\u7fc5\u8dd1\u56fe\uff1aBaritone \u98de\u884c + \u672b\u5f71\u7bb1/\u6f5c\u5f71\u76d2\u8865\u7ed9 + \u81ea\u52a8\u8fdb\u98df + \u7ecf\u9a8c\u74f6\u4fee\u9798\u7fc5 + \u53cd\u51fb\u706b\u7403 + \u9003\u79bb\u5ca9\u6d46 + \u5371\u9669\u767b\u51fa\u3002", "elytra", "autofly", "autoelytra", "aef", "ice");
        this.sgTarget = this.settings.createGroup("\u76ee\u6807");
        this.sgFlight = this.settings.createGroup("\u98de\u884c");
        this.sgBaritone = this.settings.createGroup("Baritone \u98de\u884c");
        this.sgBtSafety = this.settings.createGroup("Baritone \u5b89\u5168\u53c2\u6570");
        this.sgFireball = this.settings.createGroup("\u53cd\u51fb\u706b\u7403");
        this.sgLava = this.settings.createGroup("\u9003\u79bb\u5ca9\u6d46");
        this.sgLavaPredict = this.settings.createGroup("\u5ca9\u6d46\u9884\u6d4b\uff08\u5b9e\u9a8c\u6027\uff09");
        this.sgSupplyTrigger = this.settings.createGroup("\u8865\u7ed9\u89e6\u53d1");
        this.sgSupplyAmount = this.settings.createGroup("\u8865\u7ed9\u6570\u91cf");
        this.sgSupplyExec = this.settings.createGroup("\u8865\u7ed9\u6267\u884c");
        this.sgEat = this.settings.createGroup("\u81ea\u52a8\u8fdb\u98df");
        this.sgMend = this.settings.createGroup("\u4fee\u9798\u7fc5");
        this.sgSafety = this.settings.createGroup("\u5b89\u5168");
        this.sgNether = this.settings.createGroup("\u4e0b\u754c\u5b89\u5168");
        this.sgLandSafety = this.settings.createGroup("\u964d\u843d\u5b89\u5168");
        this.sgJunk = this.settings.createGroup("\u5783\u573e\u5904\u7406");
        this.sgDebug = this.settings.createGroup("\u8c03\u8bd5");
        this.mode = SettingHelper.enum_(this.sgTarget, "\u6a21\u5f0f", "Waypoints=\u6309\u822a\u70b9\u5217\u8868\u987a\u5e8f\u98de\uff1bSingleTarget=\u53ea\u98de\u4e00\u4e2a\u5750\u6807\uff1bDirection=\u671d\u542f\u52a8\u65f6\u7684\u89c6\u89d2\u65b9\u5411\u8dd1\u56fe\u3002", Mode.Waypoints);
        this.waypoints = SettingHelper.stringList(this.sgTarget, "\u822a\u70b9\u5217\u8868", "\u6bcf\u884c\u4e00\u4e2a\u5750\u6807\uff0c\u683c\u5f0f x,z\uff08\u4e5f\u63a5\u53d7 x z \u6216 x, z\uff09\u3002", List.of("1000,1000", "1000,-1000", "-1000,-1000"));
        this.targetX = SettingHelper.intRaw(this.sgTarget, "\u76ee\u6807 X", "SingleTarget \u6a21\u5f0f\u7684 X \u5750\u6807\u3002\u9ed8\u8ba4 0\u3002", 0, -30000000, 30000000);
        this.targetZ = SettingHelper.intRaw(this.sgTarget, "\u76ee\u6807 Z", "SingleTarget \u6a21\u5f0f\u7684 Z \u5750\u6807\u3002\u9ed8\u8ba4 0\u3002", 0, -30000000, 30000000);
        this.segmentDistance = SettingHelper.intRaw(this.sgTarget, "\u5355\u6bb5\u8ddd\u79bb", "\u5b9a\u5411\u8dd1\u56fe\u6bcf\u4e00\u6bb5\u63a8\u8fdb\u7684\u8ddd\u79bb\uff08\u683c\uff09\u3002\u9ed8\u8ba4 2000\u3002", 2000, 100, 100000);
        this.arriveRadius = SettingHelper.int_(this.sgTarget, "\u5230\u8fbe\u5224\u5b9a\u534a\u5f84", "Baritone \u505c\u4e0b\u540e\u4e0e\u76ee\u6807\u7684\u6c34\u5e73\u8ddd\u79bb\u5c0f\u4e8e\u8be5\u503c\u624d\u7b97\u300c\u5230\u8fbe\u300d\uff0c\u5426\u5219\u8bb0\u4e3a\u4e00\u6b21\u5931\u8d25\u6bb5\u3002\u9ed8\u8ba4 32\u3002", 32, 4, 512);
        this.loop = SettingHelper.bool(this.sgTarget, "\u5faa\u73af\u822a\u70b9", "\u822a\u70b9\u5217\u8868\u98de\u5b8c\u540e\u4ece\u5934\u518d\u6765\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.autoTakeoff = SettingHelper.bool(this.sgFlight, "\u81ea\u52a8\u8d77\u98de", "\u672c\u63d2\u4ef6\u81ea\u5df1\u8d77\u8df3\u5e76\u5c55\u5f00\u9798\u7fc5\uff08\u505a\u6cd5\uff1a\u539f\u5730\u8df3\u4e24\u4e0b \u2192 \u5934\u9876\u6321\u4e86\u5c31\u6316\u5f00 \u2192 \u518d\u4e0d\u884c\u5c31\u627e\u5f00\u9614\u822a\u7ebf\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.takeoffTimeout = SettingHelper.int_(this.sgFlight, "\u8d77\u98de\u770b\u95e8\u72d7", "\u6574\u4e2a\u8d77\u98de\u6d41\u7a0b\uff08\u539f\u5730\u8d77\u8df3 \u2192 \u6e05\u969c \u2192 \u627e\u5f00\u9614\u822a\u7ebf \u2192 \u98de\u5f80\u5f00\u9614\u5730\uff09\u6700\u591a\u8dd1\u591a\u5c11 tick \u5c31\u5224\u5931\u8d25\uff0c20 tick = 1 \u79d2\u3002\u9ed8\u8ba4 120\u3002", 120, 20, 1200);
        this.takeoffAutoJumpFallback = SettingHelper.bool(this.sgFlight, "\u8d77\u98de\u5931\u8d25\u4ea4\u7ed9 Baritone", "\u672c\u63d2\u4ef6\u7684\u8d77\u98de\u6d41\u7a0b\uff08\u539f\u5730\u8d77\u8df3 + \u5f00\u9614\u5730\u641c\u7d22\uff09\u5168\u90e8\u5931\u8d25\u540e\uff0c\u4e34\u65f6\u628a Baritone \u7684 elytraAutoJump \u6253\u5f00\u518d\u8bd5\u4e00\u6b21\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.openAreaSearch = SettingHelper.bool(this.sgFlight, "\u5f00\u9614\u5730\u641c\u7d22\u8d77\u98de", "\u539f\u5730\u8d77\u8df3\u5931\u8d25\u65f6\uff0c\u7528 512 \u4e2a\u65b9\u5411\u627e\u4e00\u6761\u5f00\u9614\u822a\u7ebf\u98de\u8fc7\u53bb\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.openSearchDist = SettingHelper.double_(this.sgFlight, "\u5f00\u9614\u5730\u641c\u7d22\u8ddd\u79bb", "\u822a\u7ebf\u641c\u7d22\u5f80\u524d\u63a8\u591a\u5c11\u683c\uff08\u9ed8\u8ba4 25\uff09\u3002", 25.0, 5.0, 64.0);
        this.openSafeDist = SettingHelper.double_(this.sgFlight, "\u5f00\u9614\u5730\u5b89\u5168\u8ddd\u79bb", "\u8bc4\u5206\u65f6\u6bcf\u6761\u5c04\u7ebf\u6700\u591a\u770b\u591a\u8fdc\uff0c\u8d8a\u5927\u8d8a\u504f\u597d\u300c\u5927\u7a7a\u5730\u300d\uff08\u9ed8\u8ba4 20\uff09\u3002", 20.0, 4.0, 48.0);
        this.openTries = SettingHelper.int_(this.sgFlight, "\u98de\u5f80\u5f00\u9614\u5730\u5c1d\u8bd5\u6b21\u6570", "\u671d\u5f00\u9614\u5730\u6765\u56de\u51b2\u51e0\u6b21\u8fd8\u6ca1\u8ba9 Baritone \u63a5\u7ba1\u5c31\u5224\u8d77\u98de\u5931\u8d25\uff08\u9ed8\u8ba4 6\uff09\u3002", 6, 1, 12);
        this.allowAscend = SettingHelper.bool(this.sgFlight, "\u5141\u8bb8\u5148\u62ac\u5347\u518d\u627e\u822a\u7ebf", "\u56db\u5468\u90fd\u88ab\u6321\u6b7b\u65f6\u5148\u62ac\u5934\u5782\u76f4\u4e0a\u5347\uff0c\u4ece +3.0 \u683c\u4e00\u8def\u8bd5\u5230 +10.5 \u683c\uff08\u6bcf 0.5 \u683c\u4e00\u6b21\uff09\u518d\u91cd\u65b0\u627e\u822a\u7ebf\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.jumpBeforeOpen = SettingHelper.int_(this.sgFlight, "\u539f\u5730\u8d77\u8df3\u5c1d\u8bd5\u6b21\u6570", "\u7ad9\u5728\u5730\u4e0a\u8d77\u8df3\u51e0\u6b21\u8fd8\u6ca1\u5c55\u5f00\u9798\u7fc5\uff0c\u5c31\u8f6c\u53bb\u300c\u98de\u5f80\u5f00\u9614\u5730\u300d\uff08\u9ed8\u8ba4 3 \u6b21\uff09\u3002", 3, 1, 10);
        this.clearHeadBlock = SettingHelper.bool(this.sgFlight, "\u5934\u9876\u969c\u788d\u81ea\u52a8\u6e05\u9664", "\u8d77\u8df3\u524d\u5934\u9876\u6709\u65b9\u5757\u6321\u7740\u65f6\uff0c\u8ba9 Baritone \u628a\u5934\u9876 2\u00d72\u00d72 \u6316\u5f00\u518d\u8d77\u8df3\uff08\u6e05\u969c\u505a\u6cd5\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.fireworkRefill = SettingHelper.bool(this.sgFlight, "\u81ea\u52a8\u8865\u5145\u5feb\u6377\u680f\u70df\u82b1", "\u5feb\u6377\u680f\u70df\u82b1\u5c11\u4e8e\u9608\u503c\u65f6\uff0c\u4ece\u80cc\u5305\u628a\u6574\u645e\u70df\u82b1\u6362\u5230\u5feb\u6377\u680f\uff08\u4e0d\u5f00\u754c\u9762\uff0c\u76f4\u63a5\u53d1\u5305\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.fireworkHotbarMin = SettingHelper.int_(this.sgFlight, "\u5feb\u6377\u680f\u70df\u82b1\u9608\u503c", "\u5feb\u6377\u680f\uff089 \u683c\uff09\u91cc\u7684\u70df\u82b1\u5c11\u4e8e\u8fd9\u4e2a\u6570\u91cf\u5c31\u4ece\u80cc\u5305\u8865\u5145\u3002\u9ed8\u8ba4 64\u3002", 64, 1, 64);
        this.takeoffFirework = SettingHelper.bool(this.sgFlight, "\u8d77\u98de\u540e\u8865\u4e00\u53d1\u70df\u82b1", "\u6bcf\u6b21\u8d77\u8df3\uff08\u542b\u843d\u5730\u540e\u7684\u81ea\u52a8\u91cd\u8df3\uff09\u9798\u7fc5\u4e00\u5c55\u5f00\u5c31\u81ea\u5df1\u653e\u4e00\u53d1\u70df\u82b1\u7ed9\u63a8\u529b\uff0c\u7136\u540e\u7acb\u523b\u4ea4\u56de Baritone\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.takeoffByBaritone = SettingHelper.bool(this.sgFlight, "\u4f18\u5148 Baritone \u81ea\u52a8\u8d77\u8df3", "\u5148\u7528 Baritone \u7684 elytraAutoJump \u8d77\u8df3\uff08\u5b83\u81ea\u5df1\u627e\u8df3\u53f0\u8d70\u8fc7\u53bb\uff09\uff1b6 \u79d2\u6ca1\u63a5\u7ba1\u5c31\u6539\u7528\u672c\u63d2\u4ef6\u7684\u539f\u5730\u8d77\u8df3 + \u70df\u82b1\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.chunkWait = SettingHelper.bool(this.sgFlight, "\u533a\u5757\u52a0\u8f7d\u7b49\u5f85", "\u672a\u52a0\u8f7d\u533a\u5757\u6bd4\u4f8b\u8fc7\u9ad8\u65f6\u6682\u505c Baritone \u539f\u5730\u76d8\u65cb\uff0c\u7b49\u533a\u5757\u8ffd\u4e0a\u6765\u518d\u7ee7\u7eed\uff0c\u907f\u514d\u649e\u8fdb\u672a\u52a0\u8f7d\u5730\u5f62\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.unloadedRatio = SettingHelper.double_(this.sgFlight, "\u672a\u52a0\u8f7d\u6bd4\u4f8b\u9608\u503c", "\u89c6\u91ce\u8303\u56f4\u5185\u672a\u52a0\u8f7d\u533a\u5757\u5360\u6bd4\u8d85\u8fc7\u8be5\u503c\u5c31\u8fdb\u5165\u7b49\u5f85\u3002\u9ed8\u8ba4 0.4\u3002", 0.4, 0.05, 1.0);
        this.chunkRadius = SettingHelper.int_(this.sgFlight, "\u533a\u5757\u68c0\u67e5\u534a\u5f84", "\u68c0\u67e5\u5468\u56f4\u591a\u5c11\u533a\u5757\u7684\u52a0\u8f7d\u72b6\u6001\uff08\u4f1a\u88ab\u5ba2\u6237\u7aef\u89c6\u8ddd\u4e0a\u9650\u9650\u5236\uff09\u3002\u9ed8\u8ba4 5\u3002", 5, 1, 12);
        this.hoverTimeout = SettingHelper.int_(this.sgFlight, "\u7b49\u5f85\u533a\u5757\u8d85\u65f6", "\u300c\u672a\u52a0\u8f7d\u533a\u5757\u592a\u591a\u300d\u800c\u6682\u505c\u98de\u884c\u6700\u591a\u6301\u7eed\u591a\u5c11 tick\uff0c\u8d85\u65f6\u5f3a\u5236\u6062\u590d\u98de\u884c\uff0820 tick = 1 \u79d2\uff09\u3002\u9ed8\u8ba4 1200\u3002", 1200, 100, 12000);
        this.btSafetyTakeover = (BoolSetting)this.sgBtSafety.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u63a5\u7ba1\u907f\u8ba9\u53c2\u6570")).description("\u6a21\u5757\u542f\u7528\u65f6\u628a Baritone \u7684\u907f\u8ba9\u4f59\u91cf/\u524d\u77bb tick \u8c03\u5927\uff0c\u5173\u95ed\u6a21\u5757\u65f6\u8fd8\u539f\u539f\u503c\u3002\u9ed8\u8ba4\u5f00\u3002")).defaultValue(true)).build());
        this.btAvoidMargin = (DoubleSetting)this.sgBtSafety.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("\u907f\u8ba9\u4f59\u91cf")).description("\u5199\u5165 Baritone \u7684 elytraMinimumAvoidance\uff0c\u8d8a\u5927\u8d8a\u65e9\u7ed5\u5f00\u969c\u788d\u3002\u9ed8\u8ba4 0.8\u3002")).defaultValue(0.8).min(0.2).max(2.0).sliderRange(0.2, 2.0).decimalPlaces(2).build());
        this.btLookahead = (IntSetting)this.sgBtSafety.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("\u524d\u77bb tick")).description("\u5199\u5165 Baritone \u7684 elytraSimulationTicks\uff0c\u8d8a\u5927\u8d8a\u65e9\u770b\u89c1\u5899\u4e5f\u8d8a\u5403 CPU\u3002\u9ed8\u8ba4 35\u3002")).defaultValue(35)).min(20).max(60).sliderRange(20, 60).build());
        this.netherReplanSeconds = SettingHelper.int_(this.sgNether, "\u4e0b\u754c\u91cd\u89c4\u5212\u6700\u5c0f\u95f4\u9694\uff08\u79d2\uff09", "\u4e0b\u754c\u91cc\u6211\u4eec\u4e3b\u52a8\u91cd\u53d1\u822a\u70b9\u81f3\u5c11\u8981\u9694\u8fd9\u4e48\u4e45\uff0c\u5c11\u6253\u6270 Baritone\u3002\u9ed8\u8ba4 8\u3002", 8, 0, 60);
        this.netherPredictOff = SettingHelper.bool(this.sgNether, "\u4e0b\u754c\u5173\u95ed Baritone \u5730\u5f62\u9884\u6d4b", "\u628a Baritone \u7684 elytraNetherSeed \u7f6e 0 \u5173\u6389\u5b83\u7684\u4e0b\u754c\u5730\u5f62\u9884\u6d4b\uff0c\u5173\u6a21\u5757\u65f6\u8fd8\u539f\u539f\u503c\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.noSupplyInBasaltDeltas = SettingHelper.bool(this.sgNether, "\u4e0d\u8981\u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\u8865\u7ed9", "\u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\u5c31\u4e0d\u964d\u843d\u8865\u7ed9\uff0c\u7ee7\u7eed\u98de\u627e\u522b\u5904\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.landSafeRadius = SettingHelper.int_(this.sgLandSafety, "\u964d\u843d\u5b89\u5168\u534a\u5f84", "\u964d\u843d\u70b9\u8fd9\u4e2a\u534a\u5f84\u5185\u6709\u654c\u5bf9\u751f\u7269\u5c31\u4e0d\u843d\u3002\u9ed8\u8ba4 8\u3002", 8, 2, 48);
        this.landAvoidMobs = SettingHelper.bool(this.sgLandSafety, "\u5468\u56f4\u6709\u602a\u5c31\u6362\u964d\u843d\u70b9", "\u5019\u9009\u964d\u843d\u70b9\u6309\u8ddd\u79bb\u6392\u5e8f\uff0c\u8df3\u8fc7\u534a\u5f84\u5185\u6709\u602a\u7684\uff1b\u5168\u90fd\u6709\u602a\u5c31\u7ee7\u7eed\u98de\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.landSkipWhenCrowded = SettingHelper.bool(this.sgLandSafety, "\u602a\u7269\u592a\u591a\u5c31\u8df3\u8fc7\u8fd9\u6b21\u8865\u7ed9", "24 \u683c\u5185\u654c\u5bf9\u751f\u7269 \u2265 5 \u53ea\u5c31\u4e0d\u964d\u843d\uff0c\u76f4\u63a5\u7ee7\u7eed\u98de\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.hurtAbortSupply = SettingHelper.bool(this.sgLandSafety, "\u88ab\u6253\u5c31\u4e2d\u65ad\u8865\u7ed9\u8d77\u98de", "\u8865\u7ed9\u4e2d\u53d7\u5230\u4f24\u5bb3\u5c31\u4e2d\u6b62\u8865\u7ed9\u3001\u6e05\u73b0\u573a\u3001\u7acb\u523b\u8d77\u98de\uff0c\u8fd9\u6b21\u8865\u7ed9 30 \u79d2\u5185\u4e0d\u518d\u8bd5\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.torchBeforeSupply = SettingHelper.bool(this.sgLandSafety, "\u8865\u7ed9\u524d\u5148\u63d2\u706b\u628a", "\u964d\u843d\u70b9\u811a\u4e0b\u653e\u4e00\u652f\u706b\u628a\u6216\u706f\u7b3c\u964d\u4f4e\u5237\u602a\uff08\u80cc\u5305\u91cc\u6709\u65f6\uff09\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.pauseOnPlayers = SettingHelper.bool(this.sgFlight, "\u6709\u73a9\u5bb6\u65f6\u8ba9\u884c", "\u9644\u8fd1\u6709\u522b\u7684\u73a9\u5bb6\u65f6\u6682\u505c\u98de\u884c\u539f\u5730\u76d8\u65cb\uff0c\u73a9\u5bb6\u8d70\u8fdc\u540e\u81ea\u52a8\u7ee7\u7eed\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.playerRange = SettingHelper.double_(this.sgFlight, "\u8ba9\u884c\u8ddd\u79bb", "\u89e6\u53d1\u8ba9\u884c\u7684\u73a9\u5bb6\u8ddd\u79bb\uff08\u683c\uff09\u3002\u9ed8\u8ba4 64.0\u3002", 64.0, 8.0, 256.0);
        this.infinityElytra = SettingHelper.bool(this.sgFlight, "\u65e0\u5c3d\u9798\u7fc5\uff08\u6bcf 12 tick \u91cd\u53d1\uff09", "\u65e0\u5c3d\u9798\u7fc5\u6a21\u5f0f\uff1a\u6bcf 12 tick \u91cd\u65b0\u5c55\u5f00\u4e00\u6b21\u9798\u7fc5\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.btTermsAccepted = this.btBool("elytraTermsAccepted", "\u540c\u610f\u9798\u7fc5\u6761\u6b3e", "Baritone \u7684 elytraTermsAccepted\uff1a\u4e0d\u540c\u610f\u65f6\u9798\u7fc5\u8fdb\u7a0b\u62d2\u7edd\u5de5\u4f5c\u3002");
        this.btAutoJump = this.btBool("elytraAutoJump", "\u81ea\u52a8\u8d77\u8df3", "\u4ea4\u7ed9 Baritone \u8d77\u8df3\uff1a\u5b83\u4f1a\u5148\u627e\u4e00\u6761\u300c\u8d70\u5230\u67d0\u4e2a\u80fd\u5f80\u4e0b\u8df3\u7684\u53f0\u9636\u300d\u7684\u6b65\u884c\u8def\u7ebf\uff0c\u5e73\u539f/\u5ba4\u5185\u4f1a\u76f4\u63a5\u62a5 Failed to compute a walking path to a spot to jump off from \u5e76\u62d2\u7edd\u8d77\u98de\uff08\u65e5\u5fd7\u91cc\u90a3\u53e5\u63d0\u793a\u5c31\u662f\u5b83\uff09\u3002\u8fd9\u4e00\u9879\u9ed8\u8ba4\u5c31\u662f\u5173\u7740\u7684\uff1a\u672c\u63d2\u4ef6\u5728\u8dd1\u56fe\u65f6\u4f1a\u5f3a\u5236\u538b\u6389\u5b83\uff0c\u8d77\u8df3\u7531\u81ea\u5df1\u5b8c\u6210\uff08\u539f\u5730\u8df3\u4e24\u4e0b \u2192 \u5934\u9876\u6321\u4e86\u5c31\u6316\u5f00 \u2192 512 \u4e2a\u65b9\u5411\u627e\u5f00\u9614\u822a\u7ebf\uff09\uff1b\u53ea\u6709\u300c\u8d77\u98de\u5931\u8d25\u4ea4\u7ed9 Baritone\u300d\u515c\u5e95\u89e6\u53d1\u65f6\u624d\u4e34\u65f6\u6253\u5f00\u3002\u70b9\u300c\u4fdd\u5b58\u5e76\u8bbe\u4e3a\u9ed8\u8ba4\u300d\u4f1a\u628a\u5f53\u524d\u503c\u5199\u8fdb baritone/settings.txt\uff0c\u5efa\u8bae\u4fdd\u6301\u5173\u95ed\u3002");
        this.btFireworkSpeed = this.btDouble("elytraFireworkSpeed", "\u70df\u82b1\u901f\u5ea6", "\u9798\u7fc5\u70df\u82b1\u7684\u6700\u4f4e\u901f\u5ea6\u8981\u6c42\uff1a\u8d8a\u5c0f\u8d8a\u7701\u70df\u82b1\u3001\u8d8a\u5927\u8d8a\u5feb\u3002Baritone \u51fa\u5382\u9ed8\u8ba4 1.2\u3002", 0.05, 2.0);
        this.btConserveFireworks = this.btBool("elytraConserveFireworks", "\u8282\u7701\u70df\u82b1", "\u5c3d\u91cf\u907f\u514d\u7528\u70df\u82b1\uff08\u80fd\u6ed1\u7fd4\u5c31\u4e0d\u653e\uff09\uff0c\u8d76\u8def\u901f\u5ea6\u4f1a\u53d8\u6162\u3002");
        this.btAutoSwap = this.btBool("elytraAutoSwap", "\u81ea\u52a8\u6362\u53d6\u9798\u7fc5", "\u9798\u7fc5\u8010\u4e45\u4e0d\u591f\u65f6\u81ea\u52a8\u6362\u80cc\u5305\u91cc\u7684\u5907\u7528\u9798\u7fc5\u3002");
        this.btPredictTerrain = this.btBool("elytraPredictTerrain", "\u9884\u6d4b\u5730\u5f62", "\u6309\u5730\u5f62\u9ad8\u5ea6\u9884\u6d4b\u8def\u7ebf\uff08\u4e0b\u754c/\u5ce1\u8c37\u98de\u884c\u65f6\u5f88\u6709\u7528\uff0c\u5173\u6389\u66f4\u5bb9\u6613\u649e\u5730\u5f62\uff09\u3002");
        this.btFreeLook = this.btBool("elytraFreeLook", "\u81ea\u7531\u89c6\u89d2", "\u98de\u884c\u65f6\u5141\u8bb8\u89c6\u89d2\u4e0e\u524d\u8fdb\u65b9\u5411\u5206\u79bb\uff08\u5f00\u7740\u66f4\u50cf\u539f\u7248\u9798\u7fc5\u624b\u611f\uff09\u3002");
        this.btSmoothLook = this.btBool("elytraSmoothLook", "\u5e73\u6ed1\u89c6\u89d2", "\u5e73\u6ed1\u8fc7\u6e21\u89c6\u89d2\uff08\u5173\u6389\u4f1a\u8ba9\u89c6\u89d2\u66f4\u786c\u66f4\u5feb\uff09\u3002");
        this.btPitchRange = this.btInt("elytraPitchRange", "\u4fef\u4ef0\u8303\u56f4", "\u5141\u8bb8\u7684\u4fef\u4ef0\u89d2\u53d8\u5316\u8303\u56f4\uff08\u5ea6\uff09\u3002", 0, 180);
        this.btAllowEmergencyLand = this.btBool("elytraAllowEmergencyLand", "\u5141\u8bb8\u7d27\u6025\u964d\u843d", "\u6ca1\u70df\u82b1/\u8010\u4e45\u4e0d\u591f\u65f6\u5141\u8bb8 Baritone \u7d27\u6025\u964d\u843d\uff1b\u65e0\u9650\u9798\u7fc5\u73a9\u6cd5\u53ef\u4ee5\u5173\u6389\u3002");
        this.btMinFireworksBeforeLanding = this.btInt("elytraMinFireworksBeforeLanding", "\u964d\u843d\u524d\u6700\u5c11\u70df\u82b1", "\u5269\u4f59\u70df\u82b1\u5c11\u4e8e\u8fd9\u4e2a\u6570\u91cf\u65f6\u4e0d\u518d\u5c1d\u8bd5\u8fdc\u8ddd\u79bb\u98de\u884c\u3002", 0, 256);
        this.btMinimumDurability = this.btInt("elytraMinimumDurability", "\u6700\u4f4e\u9798\u7fc5\u8010\u4e45", "\u9798\u7fc5\u5269\u4f59\u8010\u4e45\u4f4e\u4e8e\u8fd9\u4e2a\u503c\u5c31\u51c6\u5907\u964d\u843d\uff08\u914d\u5408\u81ea\u52a8\u6362\u9798\u7fc5\u4f7f\u7528\uff09\u3002", 0, 1000);
        this.btAllowLandOnNetherFortress = this.btBool("elytraAllowLandOnNetherFortress", "\u5141\u8bb8\u843d\u5728\u4e0b\u754c\u8981\u585e", "\u662f\u5426\u5141\u8bb8\u628a\u4e0b\u754c\u8981\u585e\u5f53\u964d\u843d\u70b9\uff08\u8981\u585e\u4e0a\u6709\u70c8\u7130\u4eba\uff0c\u8c28\u614e\u6253\u5f00\uff09\u3002");
        this.btChatSpam = this.btBool("elytraChatSpam", "Baritone \u804a\u5929\u5237\u5c4f", "\u8ba9 Baritone \u628a\u98de\u884c\u51b3\u7b56\u6253\u5370\u5230\u804a\u5929\u680f\uff1b\u60f3\u8981\u5e72\u51c0\u804a\u5929\u680f\u5c31\u5173\u6389\u3002");
        this.btRenderSimulation = this.btBool("elytraRenderSimulation", "\u6e32\u67d3\u6a21\u62df\u8def\u5f84", "\u628a Baritone \u7684\u98de\u884c\u6a21\u62df\u753b\u51fa\u6765\uff08\u7eaf\u8c03\u8bd5\u7528\uff0c\u6b63\u5f0f\u8dd1\u56fe\u5efa\u8bae\u5173\u6389\uff09\u3002");
        this.baritoneSaveKey = SettingHelper.keybind(this.sgBaritone, "\u4e00\u952e\u4fdd\u5b58\u5feb\u6377\u952e", "\u6309\u4e0b = \u5e94\u7528\u9762\u677f\u91cc\u7684 Baritone \u8bbe\u7f6e\u5e76\u4fdd\u5b58\u4e3a\u9ed8\u8ba4\uff08\u7b49\u4ef7\u4e8e\u70b9\u9762\u677f\u4e0a\u7684\u300c\u4fdd\u5b58\u5e76\u8bbe\u4e3a\u9ed8\u8ba4\u300d\uff09\u3002");
        this.deflectFireballs = SettingHelper.bool(this.sgFireball, "\u53cd\u51fb\u706b\u7403", "\u628a\u98de\u5411\u81ea\u5df1\u7684\u706b\u7403\u6253\u56de\u53bb\uff1a\u6682\u505c\u98de\u884c \u2192 \u770b\u5411\u706b\u7403 \u2192 \u6253\u4e00\u62f3 \u2192 \u706b\u7403\u6d88\u5931\u540e\u6062\u590d\u98de\u884c\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.fireballRange = SettingHelper.double_(this.sgFireball, "\u62e6\u622a\u8ddd\u79bb", "\u706b\u7403\u63a2\u6d4b\u534a\u5f84\uff08\u683c\uff09\uff1b0 = \u6309\u5b9e\u4f53\u4ea4\u4e92\u8ddd\u79bb\u81ea\u52a8\u7b97\u3002\u9ed8\u8ba4 0\u3002", 0.0, 0.0, 16.0);
        this.fireballMax = SettingHelper.int_(this.sgFireball, "\u6700\u591a\u540c\u65f6\u62e6\u51e0\u4e2a", "\u540c\u65f6\u5b58\u5728\u7684\u706b\u7403\u8d85\u8fc7\u8fd9\u4e2a\u6570\u91cf\u5c31\u653e\u5f03\u62e6\u622a\uff08\u9ed8\u8ba4 1\uff0c\u5373 2 \u4e2a\u5c31\u653e\u5f03\uff09\u3002", 1, 1, 8);
        this.fireballPauseBaritone = SettingHelper.bool(this.sgFireball, "\u62e6\u622a\u65f6\u6682\u505c\u98de\u884c", "\u62e6\u622a\u671f\u95f4\u6682\u505c Baritone\uff0c\u6253\u56de\u706b\u7403\u540e\u6062\u590d\u98de\u884c\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.fireballFailOnMultiple = SettingHelper.bool(this.sgFireball, "\u62e6\u4e0d\u8fc7\u6765\u5c31\u5224\u5931\u8d25", "\u706b\u7403\u6570\u91cf\u8d85\u8fc7\u4e0a\u9650\u65f6\u76f4\u63a5\u5224\u4efb\u52a1\u5931\u8d25\uff08\u8bbe\u8ba1\u884c\u4e3a\uff09\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.lavaEscape = SettingHelper.bool(this.sgLava, "\u9003\u79bb\u5ca9\u6d46", "\u771f\u7684\u6ce1\u5728\u5ca9\u6d46\u91cc\u65f6\u81ea\u52a8\u62ac\u5934\u3001\u5f00\u9798\u7fc5\u3001\u653e\u70df\u82b1\u8131\u79bb\uff08\u4e25\u683c\u7167\u53c2\u8003\u5b9e\u73b0\u7684\u65f6\u5e8f\uff1a\u89e6\u53d1\u9608\u503c 20 / \u672a\u6ed1\u7fd4 5\uff0c\u51b7\u5374 45 tick\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.lavaIgnoreGlidingFire = SettingHelper.bool(this.sgLava, "\u6ed1\u7fd4\u65f6\u5ffd\u7565\u5ca9\u6d46", "\u6ed1\u7fd4\u4e2d\u6ce1\u5728\u5ca9\u6d46\u91cc\u4e5f\u4e0d\u81ea\u6551\uff08\u5371\u9669\uff0c\u522b\u5f00\uff09\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.lavaSwimToSafety = SettingHelper.bool(this.sgLava, "\u515c\u5e95\uff1a\u6e38\u5411\u5b89\u5168\u70b9", "\u989d\u5916\u529f\u80fd\uff08\u9ed8\u8ba4\u5173\uff09\uff1a\u81ea\u6551\u7a97\u53e3\u5185\u8fd8\u671d\u6700\u8fd1\u7684\u5b89\u5168\u70b9\u6e38\u3001\u4f1a\u6309\u524d\u8fdb\u548c\u8f6c\u5411\u3002\u9ed8\u8ba4\u5173 = \u5b8c\u5168\u6309\u53c2\u8003\u5b9e\u73b0\uff0c\u6ce1\u5728\u5ca9\u6d46\u6d77\u91cc\u4e0d\u4f1a\u81ea\u6551\u6210\u529f\uff0c\u9700\u8981\u81ea\u6551\u8bf7\u6253\u5f00\u515c\u5e95\u3002", false);
        this.lavaSearchRadius = SettingHelper.int_(this.sgLava, "\u515c\u5e95\uff1a\u5b89\u5168\u70b9\u641c\u7d22\u534a\u5f84", "\u4e0a\u9762\u90a3\u4e00\u9879\u7684\u641c\u7d22\u534a\u5f84\uff08\u683c\uff09\u3002\u9ed8\u8ba4 12\u3002", 12, 3, 24);
        this.lavaDrinkFireRes = SettingHelper.bool(this.sgLava, "\u515c\u5e95\uff1a\u559d\u6297\u706b\u836f\u6c34", "\u989d\u5916\u529f\u80fd\uff08\u9ed8\u8ba4\u5173\uff09\uff1a\u81ea\u6551\u7a97\u53e3\u5185\u5feb\u6377\u680f\u6709\u6297\u706b\u836f\u6c34\u5c31\u5148\u559d\u6389\u3002\u9ed8\u8ba4\u5173 = \u5b8c\u5168\u6309\u53c2\u8003\u5b9e\u73b0\uff0c\u6ce1\u5728\u5ca9\u6d46\u6d77\u91cc\u4e0d\u4f1a\u81ea\u6551\u6210\u529f\uff0c\u9700\u8981\u81ea\u6551\u8bf7\u6253\u5f00\u515c\u5e95\u3002", false);
        this.lavaLookPitch = SettingHelper.double_(this.sgLava, "\u81ea\u6551\u62ac\u5934\u89d2\u5ea6", "\u81ea\u6551\u65f6\u628a\u89c6\u89d2\u62ac\u5230\u591a\u5c11\u5ea6\uff08-90 = \u6b63\u4e0a\u65b9\uff0c\u8d8a\u5927\u8d8a\u5e73\uff09\u3002\u9ed8\u8ba4 -90\u3002", -90.0, -90.0, 0.0);
        this.lavaUseFirework = SettingHelper.bool(this.sgLava, "\u81ea\u6551\u65f6\u653e\u70df\u82b1", "\u81ea\u6551\u65f6\u4ece\u5feb\u6377\u680f\u653e\u4e00\u53d1\u70df\u82b1\u628a\u81ea\u5df1\u63a8\u8d77\u6765\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.lavaFailAbort = SettingHelper.bool(this.sgLava, "\u81ea\u6551\u5931\u8d25\u624d\u5224\u5931\u8d25", "\u81ea\u6551\u7a97\u53e3\u8d70\u5b8c\u8fd8\u5728\u5ca9\u6d46\u91cc\uff08\u6216\u627e\u4e0d\u5230\u70df\u82b1\uff09\u5c31\u5224\u4efb\u52a1\u5931\u8d25\uff1b\u662f\u5426\u767b\u51fa\u7531\u300c\u5b89\u5168\u300d\u7ec4\u91cc\u7684\u5931\u8d25\u81ea\u52a8\u767b\u51fa\u51b3\u5b9a\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.lavaPredictEnabled = SettingHelper.bool(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u542f\u7528\u9884\u6d4b\u89c4\u907f", "\u5b9e\u9a8c\u6027\uff0c\u9ed8\u8ba4\u5173\uff1a\u6cbf\u5f53\u524d\u98de\u884c\u65b9\u5411\u9884\u6d4b 1.5~4 \u79d2\uff0c\u63d0\u524d\u907f\u5f00\u5ca9\u6d46\u67f1/\u5ca9\u6d46\u6e56\u3002", false);
        this.lavaPredictHorizon = SettingHelper.double_(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u9884\u6d4b\u65f6\u957f\uff08\u79d2\uff09", "\u5f80\u524d\u9884\u6d4b\u591a\u5c11\u79d2\u7684\u98de\u884c\u8f68\u8ff9\u3002\u9ed8\u8ba4 3.0\u3002", 3.0, 1.5, 4.0);
        this.lavaPredictUrgent = SettingHelper.double_(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u7d27\u6025\u9608\u503c\uff08\u79d2\uff09", "\u9884\u8ba1\u5728\u8fd9\u4e2a\u65f6\u95f4\u5185\u649e\u4e0a\u5ca9\u6d46\u5c31\u6539\u7528\u300c\u7d27\u6025\u89c4\u907f\u300d\uff08\u6682\u505c Baritone + \u504f\u8f6c\u89c6\u89d2 + \u653e\u70df\u82b1\uff09\u3002\u9ed8\u8ba4 1.2\u3002", 1.2, 0.4, 2.0);
        this.lavaPredictLateral = SettingHelper.int_(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u4fa7\u5411\u7ed5\u884c\u8ddd\u79bb", "\u7ed5\u884c\u822a\u70b9\u79bb\u5371\u9669\u70b9\u5f80\u4fa7\u9762\u504f\u591a\u5c11\u683c\u3002\u9ed8\u8ba4 30\u3002", 30, 16, 96);
        this.lavaPredictReturn = SettingHelper.int_(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u7ed5\u884c\u7ed3\u675f\u8ddd\u79bb", "\u79bb\u7ed5\u884c\u822a\u70b9\u591a\u8fd1\u5c31\u7b97\u7ed5\u8fc7\u8fd9\u4e00\u6bb5\uff08\u4e5f\u53ef\u4ee5\u9760\u300c\u524d\u65b9\u9884\u6d4b\u53d8\u5e72\u51c0\u300d\u63d0\u524d\u7ed3\u675f\uff09\u3002\u9ed8\u8ba4 25\u3002", 25, 8, 64);
        this.lavaPredictCooldown = SettingHelper.int_(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u89c4\u907f\u9632\u6296 tick", "\u4e24\u6b21\u89c4\u907f\u52a8\u4f5c\u4e4b\u95f4\u81f3\u5c11\u95f4\u9694\u591a\u5c11 tick\uff0c\u9632\u6b62\u5728\u5ca9\u6d46\u8fb9\u7f18\u53cd\u590d\u89e6\u53d1\u3002\u9ed8\u8ba4 40\u3002", 40, 10, 200);
        this.lavaPredictWarnOnly = SettingHelper.bool(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u53ea\u9884\u8b66\u4e0d\u63a5\u7ba1", "\u53ea\u628a\u9884\u6d4b\u7ed3\u679c\u5199\u8fdb\u65e5\u5fd7\uff0c\u4e0d\u6539\u822a\u70b9\u3001\u4e0d\u78b0\u6309\u952e\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.lavaPredictPauseBaritone = SettingHelper.bool(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u7d27\u6025\u65f6\u6682\u505c Baritone", "\u7d27\u6025\u89c4\u907f\u671f\u95f4\u6682\u505c Baritone\uff08p\uff09\uff0c\u8131\u79bb\u540e\u6062\u590d\uff08r\uff09\u2014\u2014\u4e0d\u6682\u505c\u7684\u8bdd\u5b83\u4f1a\u548c\u6211\u4eec\u62a2\u89c6\u89d2\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.lavaPredictDeflect = SettingHelper.double_(this.sgLavaPredict, "\u5ca9\u6d46\u9884\u6d4b\uff1a\u7d27\u6025\u504f\u8f6c\u89d2\uff08\u5ea6\uff09", "\u7d27\u6025\u89c4\u907f\u65f6\u76f8\u5bf9\u300c\u5ca9\u6d46\u53cd\u65b9\u5411\u300d\u518d\u5de6\u53f3\u504f\u591a\u5c11\u5ea6\uff0c\u7528\u6765\u5728\u4e24\u4fa7\u91cc\u6311\u4e00\u6761\u66f4\u5e72\u51c0\u7684\u51fa\u8def\u3002\u9ed8\u8ba4 75.0\u3002", 75.0, 30.0, 90.0);
        this.autoSupply = SettingHelper.bool(this.sgSupplyTrigger, "\u542f\u7528\u81ea\u52a8\u8865\u7ed9", "\u7f3a\u7269\u8d44\u65f6\u81ea\u52a8\u964d\u843d\uff0c\u653e\u672b\u5f71\u7bb1\u3001\u53d6\u6f5c\u5f71\u76d2\u8865\u7ed9\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.supplyBeforeSegment = SettingHelper.bool(this.sgSupplyTrigger, "\u6bcf\u6bb5\u5148\u505a\u8865\u7ed9\u68c0\u67e5", "\u6bcf\u6bb5\u5f00\u59cb\u524d\u5148\u8dd1\u4e00\u6b21\u8865\u7ed9\u5224\u5b9a\uff0c\u4ec0\u4e48\u90fd\u4e0d\u7f3a\u5c31\u76f4\u63a5\u8d77\u98de\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.minFireworkStacks = SettingHelper.int_(this.sgSupplyTrigger, "\u70df\u82b1\u6700\u4f4e\u7ec4\u6570", "\u80cc\u5305\u91cc\u70df\u82b1\u5c11\u4e8e\u8fd9\u4e2a\u7ec4\u6570\u5c31\u89e6\u53d1\u8865\u7ed9\u3002\u9ed8\u8ba4 3\u3002", 3, 0, 27);
        this.minFoodCount = SettingHelper.int_(this.sgSupplyTrigger, "\u98df\u7269\u6700\u4f4e\u6570\u91cf", "\u98df\u7269\u5c11\u4e8e\u8fd9\u4e2a\u6570\u91cf\u5c31\u89e6\u53d1\u8865\u7ed9\u3002\u9ed8\u8ba4 8\u3002", 8, 0, 64);
        this.minXpBottles = SettingHelper.int_(this.sgSupplyTrigger, "\u7ecf\u9a8c\u74f6\u6700\u4f4e\u6570\u91cf", "\u9644\u9b54\u4e4b\u74f6\u5c11\u4e8e\u8fd9\u4e2a\u6570\u91cf\u5c31\u89e6\u53d1\u8865\u7ed9\uff08\u4fee\u9798\u7fc5\u7684\u524d\u63d0\uff09\u3002\u9ed8\u8ba4 8\u3002", 8, 0, 640);
        this.minTotems = SettingHelper.int_(this.sgSupplyTrigger, "\u56fe\u817e\u6700\u4f4e\u6570\u91cf", "\u4e0d\u6b7b\u56fe\u817e\u5c11\u4e8e\u8fd9\u4e2a\u6570\u91cf\u5c31\u89e6\u53d1\u8865\u7ed9\u3002\u9ed8\u8ba4 1\u3002", 1, 0, 8);
        this.minElytraDurability = SettingHelper.int_(this.sgSupplyTrigger, "\u9798\u7fc5\u8010\u4e45\u8b66\u6212\u7ebf", "\u6240\u6709\u9798\u7fc5\u7684\u5269\u4f59\u8010\u4e45\u603b\u548c\u4f4e\u4e8e\u8be5\u503c\u5c31\u89e6\u53d1\u8865\u7ed9\uff08\u987a\u8def\u6362\u65b0\u9798\u7fc5\uff09\u3002\u9ed8\u8ba4 60\u3002", 60, 0, 400);
        this.landForSupply = SettingHelper.bool(this.sgSupplyTrigger, "\u8865\u7ed9\u524d\u81ea\u52a8\u964d\u843d", "\u8ba9 Baritone \u964d\u843d\u5230\u5730\u9762\u540e\u518d\u5f00\u59cb\u653e\u7bb1\u5b50\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.maxSupplyRetries = SettingHelper.int_(this.sgSupplyTrigger, "\u6700\u5927\u8865\u7ed9\u91cd\u8bd5\u6b21\u6570", "\u8865\u7ed9\u540e\u7269\u8d44\u4ecd\u7136\u4e0d\u8fbe\u6807\u5c31\u9000\u907f\u91cd\u8bd5\u3002\u9ed8\u8ba4 2\u3002", 2, 1, 10);
        this.supplyErrorRetries = SettingHelper.int_(this.sgSupplyTrigger, "\u8865\u7ed9\u51fa\u9519\u91cd\u8bd5\u6b21\u6570", "\u8865\u7ed9\u8fc7\u7a0b\u51fa\u9519\u65f6\u4e0d\u5224\u5931\u8d25\uff0c\u9000\u907f\u91cd\u8bd5\u8fd9\u4e48\u591a\u6b21\u3002\u9ed8\u8ba4 3\u3002", 3, 1, 10);
        this.supplyRetryDelay = SettingHelper.int_(this.sgSupplyTrigger, "\u8865\u7ed9\u91cd\u8bd5\u7b49\u5f85", "\u4e24\u6b21\u8865\u7ed9\u4e4b\u95f4\u7684\u6700\u5c0f\u95f4\u9694 tick\uff0820 tick = 1 \u79d2\uff09\u3002\u9ed8\u8ba4 200\u3002", 200, 20, 2400);
        this.autoRestock = SettingHelper.bool(this.sgSupplyTrigger, "\u81ea\u52a8\u8865\u5145\u7269\u8d44\u81f3\u7269\u54c1\u680f", "\u5feb\u6377\u680f\u91cc\u6e05\u5355\u7269\u54c1\u4e0d\u591f\u5c31\u4ece\u80cc\u5305\u6362\u8fc7\u6765\uff08\u4e0d\u6253\u5f00\u754c\u9762\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.restockItems = SettingHelper.items(this.sgSupplyTrigger, "\u7269\u54c1\u680f\u8865\u5145\u6e05\u5355", "\u8981\u7ef4\u6301\u7684\u7269\u54c1\u6e05\u5355\u3002", List.of(Items.FIREWORK_ROCKET), false);
        this.restockStacks = SettingHelper.int_(this.sgSupplyTrigger, "\u6bcf\u4e2a\u7269\u54c1\u8865\u5230\u51e0\u7ec4", "\u6e05\u5355\u91cc\u6bcf\u6837\u7269\u54c1\u5728\u5feb\u6377\u680f\u91cc\u4fdd\u6301\u51e0\u7ec4\uff08\u4e00\u7ec4 = \u8be5\u7269\u54c1\u7684\u6700\u5927\u5806\u53e0\u6570\uff1a\u70df\u82b1 64\u3001\u7ecf\u9a8c\u74f6 64\u3001\u56fe\u817e 1\uff09\u3002", 1, 1, 8);
        this.restockInterval = SettingHelper.int_(this.sgSupplyTrigger, "\u8865\u5145\u95f4\u9694 tick", "\u4e24\u6b21\u642c\u8fd0\u4e4b\u95f4\u7684\u6700\u5c0f\u95f4\u9694\uff0820 tick = 1 \u79d2\uff09\u3002\u9ed8\u8ba4 10\u3002", 10, 1, 100);
        this.restockKeepHeld = SettingHelper.bool(this.sgSupplyTrigger, "\u8865\u5145\u65f6\u4e0d\u5360\u624b\u6301\u683c", "\u6362\u4f4d\u65f6\u8df3\u8fc7\u4f60\u5f53\u524d\u62ff\u7740\u7684\u90a3\u4e00\u683c\uff0c\u514d\u5f97\u628a\u4f60\u6b63\u7528\u7684\u4e1c\u897f\u6362\u8d70\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.restockTriggerSupply = SettingHelper.bool(this.sgSupplyTrigger, "\u80cc\u5305\u4e0d\u8db3\u65f6\u89e6\u53d1\u8865\u7ed9", "\u6e05\u5355\u91cc\u7684\u4e1c\u897f\u8fde\u6574\u4e2a\u80cc\u5305\u90fd\u4e0d\u591f\u65f6\uff0c\u89e6\u53d1\u672b\u5f71\u7bb1\u8865\u7ed9\uff08\u964d\u843d \u2192 \u653e\u672b\u5f71\u7bb1 \u2192 \u5f00\u6f5c\u5f71\u76d2\u53d6\u7269\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.fullSupplyOnStart = SettingHelper.bool(this.sgSupplyTrigger, "\u4efb\u52a1\u5f00\u59cb\u65f6\u5148\u8865\u6ee1", "\u4efb\u52a1\u5f00\u59cb\u65f6\u5148\u505a\u4e00\u6b21\u5b8c\u6574\u8865\u7ed9\uff0c\u8865\u6ee1\u518d\u8d77\u98de\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.supplyKey = SettingHelper.keybind(this.sgSupplyTrigger, "\u624b\u52a8\u8865\u7ed9\u952e", "\u6309\u4e00\u4e0b\u7acb\u523b\u505a\u4e00\u6b21\u8865\u7ed9\uff08\u4e0d\u9700\u8981\u6253\u5f00\u4efb\u4f55\u754c\u9762\uff09\u3002");
        this.targetFireworkStacks = SettingHelper.int_(this.sgSupplyAmount, "\u76ee\u6807\u70df\u82b1\u7ec4\u6570", "\u8865\u5230\u591a\u5c11\u7ec4\u70df\u82b1\uff081 \u7ec4 = 64 \u4e2a\uff09\u3002\u9ed8\u8ba4 21\u3002", 21, 0, 36);
        this.targetXpBottles = SettingHelper.int_(this.sgSupplyAmount, "\u76ee\u6807\u7ecf\u9a8c\u74f6\u6570\u91cf", "\u8865\u5230\u591a\u5c11\u4e2a\u9644\u9b54\u4e4b\u74f6\uff08\u4fee\u9798\u7fc5\u7528\uff09\u3002\u9ed8\u8ba4 192\u3002", 192, 0, 2560);
        this.targetFoodCount = SettingHelper.int_(this.sgSupplyAmount, "\u76ee\u6807\u98df\u7269\u6570\u91cf", "\u8865\u5230\u591a\u5c11\u4e2a\u98df\u7269\u3002\u9ed8\u8ba4 32\u3002", 32, 0, 512);
        this.targetTotems = SettingHelper.int_(this.sgSupplyAmount, "\u76ee\u6807\u56fe\u817e\u6570\u91cf", "\u8865\u5230\u591a\u5c11\u4e2a\u4e0d\u6b7b\u56fe\u817e\u3002\u9ed8\u8ba4 2\u3002", 2, 0, 16);
        this.targetElytraCount = SettingHelper.int_(this.sgSupplyAmount, "\u76ee\u6807\u5907\u7528\u9798\u7fc5", "\u8865\u5230\u591a\u5c11\u6761\u300c\u8010\u4e45 3\u3001\u635f\u4f24 < 15\u300d\u7684\u5907\u7528\u9798\u7fc5\u3002\u9ed8\u8ba4 5\u3002", 5, 0, 8);
        this.minEnderChests = SettingHelper.int_(this.sgSupplyAmount, "\u6700\u5c11\u672b\u5f71\u7bb1\u6570\u91cf", "\u80cc\u5305\u91cc\u672b\u5f71\u7bb1\u5c11\u4e8e\u8fd9\u4e2a\u6570\u91cf\u65f6\u7ed9\u8b66\u544a\uff08\u6309\u8bbe\u8ba1\u76f4\u63a5\u5224\u5b9a\u5931\u8d25\uff09\u3002\u9ed8\u8ba4 3\u3002", 3, 1, 9);
        this.maxShulkers = SettingHelper.int_(this.sgSupplyAmount, "\u5355\u6b21\u6700\u591a\u53d6\u76d2\u6570", "\u4e00\u6b21\u8865\u7ed9\u6700\u591a\u4ece\u672b\u5f71\u7bb1\u91cc\u53d6\u51e0\u4e2a\u6f5c\u5f71\u76d2\uff08\u9632\u6b62\u7269\u54c1\u592a\u5206\u6563\uff09\u3002\u9ed8\u8ba4 4\u3002", 4, 1, 27);
        this.actionDelay = SettingHelper.int_(this.sgSupplyExec, "\u52a8\u4f5c\u95f4\u9694 tick", "\u6bcf\u4e2a\u70b9\u51fb/\u653e\u7f6e\u52a8\u4f5c\u4e4b\u95f4\u7684\u95f4\u9694\u3002\u9ed8\u8ba4 3\u3002", 3, 1, 20);
        this.placeRadius = SettingHelper.int_(this.sgSupplyExec, "\u653e\u7f6e\u641c\u7d22\u534a\u5f84", "\u5728\u73a9\u5bb6\u5468\u56f4\u591a\u5c11\u683c\u5185\u5bfb\u627e\u53ef\u4ee5\u653e\u672b\u5f71\u7bb1/\u6f5c\u5f71\u76d2\u7684\u4f4d\u7f6e\u3002\u9ed8\u8ba4 2\u3002", 2, 1, 4);
        this.autoPlaceEnderChest = SettingHelper.bool(this.sgSupplyExec, "\u81ea\u52a8\u653e\u7f6e\u672b\u5f71\u7bb1", "\u4ece\u5feb\u6377\u680f\u62ff\u51fa\u672b\u5f71\u7bb1\u653e\u5728\u811a\u8fb9\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.autoPickupEnderChest = SettingHelper.bool(this.sgSupplyExec, "\u7528\u540e\u56de\u6536\u672b\u5f71\u7bb1", "\u8865\u7ed9\u5b8c\u6210\u540e\u628a\u672b\u5f71\u7bb1\u6316\u56de\u6765\uff08\u5426\u5219\u4f1a\u6d88\u8017\u672b\u5f71\u7bb1\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.useBaritoneMine = SettingHelper.bool(this.sgSupplyExec, "\u7528 Baritone \u6316\u65b9\u5757", "\u6316\u6f5c\u5f71\u76d2/\u672b\u5f71\u7bb1\u4ea4\u7ed9 Baritone\uff1a\u5b83\u4f1a\u8d70\u8fc7\u53bb\u6309\u4f4f\u6316\uff0c\u5e76\u6361\u56de\u6389\u843d\u7269\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.storeLoot = SettingHelper.bool(this.sgSupplyExec, "\u987a\u8def\u5b58\u6218\u5229\u54c1", "\u53d6\u7269\u8d44\u65f6\uff0c\u628a\u80cc\u5305\u91cc\u7684\u6742\u7269\uff08\u6216\u4e0b\u9762\u7684\u767d\u540d\u5355\u7269\u54c1\uff09shift \u8fdb\u5f53\u524d\u6253\u5f00\u7684\u6f5c\u5f71\u76d2\uff0c\u817e\u51fa\u7a7a\u95f4\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.storeItems = SettingHelper.items(this.sgSupplyExec, "\u8981\u5b58\u653e\u7684\u7269\u54c1", "\u53ea\u6709\u8fd9\u4e9b\u7269\u54c1\u4f1a\u88ab\u5b58\u8fdb\u6f5c\u5f71\u76d2\u3002", List.of(), false);
        this.supplyFoodItems = SettingHelper.items(this.sgSupplyExec, "\u8865\u7ed9\u7684\u98df\u7269\u767d\u540d\u5355", "\u8865\u7ed9\u65f6\u8865\u54ea\u4e9b\u98df\u7269\u3002", List.of(Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.BREAD), true);
        this.autoEat = SettingHelper.bool(this.sgEat, "\u542f\u7528\u81ea\u52a8\u8fdb\u98df", "\u9965\u997f\u6216\u8840\u91cf\u504f\u4f4e\u65f6\u81ea\u52a8\u5403\u4e1c\u897f\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.hungerThreshold = SettingHelper.int_(this.sgEat, "\u9965\u997f\u9608\u503c", "\u9965\u997f\u503c\u4f4e\u4e8e\u8be5\u503c\u65f6\u5403\u996d\uff080-20\uff09\u3002\u9ed8\u8ba4 16\u3002", 16, 0, 20);
        this.healthThreshold = SettingHelper.double_(this.sgEat, "\u8840\u91cf\u9608\u503c", "\u8840\u91cf\u4f4e\u4e8e\u8be5\u503c\u4e14\u9965\u997f\u503c\u4e0d\u6ee1\u65f6\u4e5f\u5403\u996d\uff08\u914d\u5408\u81ea\u7136\u56de\u8840\uff09\u3002\u9ed8\u8ba4 15.0\u3002", 15.0, 0.0, 20.0);
        this.eatWhileGliding = SettingHelper.bool(this.sgEat, "\u98de\u884c\u4e2d\u8fdb\u98df", "\u5141\u8bb8\u5728\u6ed1\u7fd4\u9014\u4e2d\u5403\uff08\u53ea\u5728\u722c\u5347\u6bb5\u5403\uff0c\u907f\u514d\u6389\u9ad8\u5ea6\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.eatMinRise = SettingHelper.double_(this.sgEat, "\u722c\u5347\u901f\u5ea6\u9608\u503c", "\u5782\u76f4\u901f\u5ea6\u9ad8\u4e8e\u8be5\u503c\u624d\u5141\u8bb8\u5728\u98de\u884c\u4e2d\u8fdb\u98df\u3002\u9ed8\u8ba4 0.6\u3002", 0.6, 0.0, 3.0);
        this.foodWhitelist = SettingHelper.items(this.sgEat, "\u5403\u7684\u98df\u7269\u767d\u540d\u5355", "\u7559\u7a7a = \u4efb\u4f55\u80fd\u5403\u7684\u4e1c\u897f\u90fd\u5403\u3002", List.of(Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.BREAD), true);
        this.foodPriorityOrdered = (OrderedItemListSetting)this.sgEat.add((Setting)((OrderedItemListSetting.Builder)((Object)((OrderedItemListSetting.Builder)((Object)new OrderedItemListSetting.Builder().name("\u98df\u7269\u4f18\u5148\u7ea7"))).description("\u6309\u987a\u5e8f\u5403/\u8865\uff0c\u5217\u8868\u8d8a\u9760\u524d\u4f18\u5148\u3002\u9ed8\u8ba4\u7a7a\u3002"))).filter(SettingHelper::isFood).build());
        this.foodPriority = SettingHelper.string(this.sgEat, "\u98df\u7269\u4f18\u5148\u7ea7\uff08\u65e7\u7248\uff0c\u53ef\u7559\u7a7a\uff09", "\u65e7\u7248\u7684\u9017\u53f7\u5206\u9694\u5199\u6cd5\uff0c\u542f\u52a8\u65f6\u4f1a\u81ea\u52a8\u8fc1\u79fb\u5230\u4e0a\u9762\u7684\u5217\u8868\uff0c\u7559\u7a7a\u5c31\u884c\u3002", "");
        this.junkDrop = SettingHelper.bool(this.sgJunk, "\u6316\u5230\u5783\u573e\u65b9\u5757\u81ea\u52a8\u4e22\u6389", "\u843d\u5730\u540e\u628a\u80cc\u5305\u91cc\u7684\u5783\u573e\u65b9\u5757\u4e22\u8fdb\u9644\u8fd1\u5ca9\u6d46\uff0c\u987a\u624b\u817e\u683c\u5b50\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.junkOnlyLava = SettingHelper.bool(this.sgJunk, "\u53ea\u5728\u9644\u8fd1\u6709\u5ca9\u6d46\u65f6\u4e22", "\u627e\u4e0d\u5230\u5ca9\u6d46\u5c31\u4e0d\u4e22\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.junkRadius = SettingHelper.int_(this.sgJunk, "\u5783\u573e\u5224\u5b9a\u534a\u5f84", "\u4ee5\u4f60\u4e3a\u4e2d\u5fc3\u627e\u5ca9\u6d46\u7684\u534a\u5f84\uff08\u683c\uff09\u3002\u9ed8\u8ba4 8\u3002", 8, 2, 32);
        this.junkItems = SettingHelper.items(this.sgJunk, "\u5783\u573e\u6e05\u5355", "\u8fd9\u4e9b\u65b9\u5757\u4f1a\u88ab\u81ea\u52a8\u4e22\u6389\uff08\u4fdd\u62a4\u6e05\u5355\u91cc\u7684\u6c38\u4e0d\u4e22\uff09\u3002", List.of(Items.NETHERRACK, Items.STONE, Items.COBBLESTONE, Items.DIRT, Items.GRAVEL, Items.SAND, Items.DEEPSLATE, Items.TUFF, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.BLACKSTONE, Items.BASALT, Items.SOUL_SAND, Items.SOUL_SOIL, Items.END_STONE), false);
        this.autoMend = SettingHelper.bool(this.sgMend, "\u542f\u7528\u81ea\u52a8\u4fee\u9798\u7fc5", "\u9798\u7fc5\u8010\u4e45\u4e0d\u8db3\u65f6\u964d\u843d\u5e76\u7528\u9644\u9b54\u4e4b\u74f6\u4fee\u590d\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.mendDurability = SettingHelper.int_(this.sgMend, "\u4fee\u590d\u89e6\u53d1\u8010\u4e45", "\u9798\u7fc5\u5269\u4f59\u8010\u4e45\u4f4e\u4e8e\u8be5\u503c\u65f6\u89e6\u53d1\u4fee\u590d\u3002\u9ed8\u8ba4 60\u3002", 60, 8, 400);
        this.mendKey = SettingHelper.keybind(this.sgMend, "\u624b\u52a8\u4fee\u9798\u7fc5\u952e", "\u6309\u4e00\u4e0b\u7acb\u523b\u5f00\u59cb\u4e00\u6b21\u4fee\u590d\u3002");
        this.maxMendRetries = SettingHelper.int_(this.sgMend, "\u6700\u5927\u4fee\u590d\u91cd\u8bd5\u6b21\u6570", "\u8fde\u7eed\u5931\u8d25\u8fd9\u4e48\u591a\u6b21\u540e\u81ea\u52a8\u5173\u6389\u300c\u542f\u7528\u81ea\u52a8\u4fee\u9798\u7fc5\u300d\uff0c\u907f\u514d\u4e00\u76f4\u964d\u843d\u53c8\u4fee\u4e0d\u4e86\u3002\u9ed8\u8ba4 2\u3002", 2, 1, 10);
        this.minBottles = SettingHelper.int_(this.sgMend, "\u6700\u5c11\u7ecf\u9a8c\u74f6", "\u80cc\u5305\u91cc\u5c11\u4e8e\u8fd9\u4e2a\u6570\u91cf\u5c31\u4e0d\u542f\u52a8\u4fee\u590d\uff08\u8bbe\u8ba1\u4e0a\u8981\u6c42 \u2265 30 \u4e2a\uff09\u3002\u9ed8\u8ba4 32\u3002", 32, 1, 640);
        this.repairToDamage = SettingHelper.int_(this.sgMend, "\u4fee\u590d\u5230\u635f\u4f24\u503c", "\u9798\u7fc5\u635f\u4f24\u964d\u5230\u8be5\u503c\u4ee5\u4e0b\u5c31\u505c\u624b\uff080 = \u4fee\u6ee1\uff09\u3002\u9ed8\u8ba4 20\u3002", 20, 0, 200);
        this.requireGround = SettingHelper.bool(this.sgMend, "\u9700\u8981\u843d\u5730", "\u5148\u8ba9 Baritone \u964d\u843d\u518d\u4fee\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.landingTimeout = SettingHelper.int_(this.sgMend, "\u964d\u843d\u8d85\u65f6", "\u7b49\u5f85\u843d\u5730\u7684\u6700\u957f tick \u6570\uff0820 tick = 1 \u79d2\uff09\u3002\u9ed8\u8ba4 600\u3002", 600, 100, 6000);
        this.mendPitch = SettingHelper.double_(this.sgMend, "\u6295\u63b7\u4fef\u4ef0\u89d2", "\u6254\u74f6\u5b50\u65f6\u7684\u89c6\u89d2\u89d2\u5ea6\uff0c90 = \u5782\u76f4\u671d\u4e0b\uff08\u7ecf\u9a8c\u7403\u4f1a\u843d\u5728\u811a\u8fb9\u88ab\u81ea\u5df1\u5438\u8d70\uff09\u3002\u9ed8\u8ba4 90.0\u3002", 90.0, 45.0, 90.0);
        this.throwDelay = SettingHelper.int_(this.sgMend, "\u6295\u63b7\u95f4\u9694 tick", "\u4e24\u6b21\u6254\u74f6\u5b50\u4e4b\u95f4\u7684\u95f4\u9694\uff1b\u8c03\u5c0f\u66f4\u5feb\uff0c\u4f46\u8bbe\u592a\u5c0f\u670d\u52a1\u7aef\u4f1a\u4e0d\u8ba4\u8fd9\u4e24\u74f6\u3002\u9ed8\u8ba4 4\u3002", 4, 2, 20);
        this.maxThrows = SettingHelper.int_(this.sgMend, "\u5355\u6b21\u6700\u591a\u6254\u51e0\u74f6", "\u4e00\u6b21\u4fee\u590d\u6700\u591a\u6254\u51e0\u74f6\u9644\u9b54\u4e4b\u74f6\u3002\u9ed8\u8ba4 128\u3002", 128, 1, 1280);
        this.requireMending = SettingHelper.bool(this.sgMend, "\u5fc5\u987b\u6709\u7ecf\u9a8c\u4fee\u8865", "\u9798\u7fc5\u6ca1\u6709\u300c\u7ecf\u9a8c\u4fee\u8865\u300d\u65f6\u76f4\u63a5\u653e\u5f03\uff08\u907f\u514d\u767d\u6254\u74f6\u5b50\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.requireNetherWastes = SettingHelper.bool(this.sgMend, "\u4ec5\u4e0b\u754c\u8352\u5730\u4fee\u590d", "\u53ea\u5728 nether_wastes \u751f\u7269\u7fa4\u7cfb\u4fee\u9798\u7fc5\uff08\u843d\u5730\u76f8\u5bf9\u5b89\u5168\uff09\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.autoLogout = SettingHelper.bool(this.sgSafety, "\u5371\u9669\u81ea\u52a8\u767b\u51fa", "\u8840\u91cf\u8fc7\u4f4e\u4e14\u56fe\u817e\u4e0d\u8db3\u65f6\u81ea\u52a8\u65ad\u5f00\u8fde\u63a5\uff08\u4fdd\u547d\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.logoutHealth = SettingHelper.double_(this.sgSafety, "\u767b\u51fa\u8840\u91cf", "\u8840\u91cf\u4f4e\u4e8e\u8be5\u503c\u4e14\u56fe\u817e\u6570\u91cf\u4e0d\u8db3\u65f6\u767b\u51fa\u3002\u9ed8\u8ba4 8.0\u3002", 8.0, 1.0, 20.0);
        this.logoutTotemMin = SettingHelper.int_(this.sgSafety, "\u767b\u51fa\u56fe\u817e\u9608\u503c", "\u56fe\u817e\u6570\u91cf\u5c11\u4e8e\u7b49\u4e8e\u8be5\u503c\u65f6\uff0c\u914d\u5408\u8840\u91cf\u6761\u4ef6\u89e6\u53d1\u767b\u51fa\u3002\u9ed8\u8ba4 1\u3002", 1, 0, 8);
        this.logoutOnFailure = SettingHelper.bool(this.sgSafety, "\u5931\u8d25\u81ea\u52a8\u767b\u51fa", "\u98de\u884c\u4efb\u52a1\u5931\u8d25\u65f6\u81ea\u52a8\u65ad\u5f00\u8fde\u63a5\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.logoutOnSupplyFail = SettingHelper.bool(this.sgSafety, "\u8865\u7ed9\u5931\u8d25\u4e5f\u767b\u51fa", "\u8865\u7ed9\u8fd9\u4e00\u8def\u5931\u8d25\uff08\u653e\u4e0d\u4e86\u672b\u5f71\u7bb1 / \u76d2\u5b50\u91cc\u6ca1\u8d27 / \u964d\u843d\u8d85\u65f6\uff09\u65f6\u662f\u5426\u4e5f\u767b\u51fa\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.logoutOnArrive = SettingHelper.bool(this.sgSafety, "\u5230\u8fbe\u81ea\u52a8\u767b\u51fa", "\u8dd1\u5b8c\u6240\u6709\u822a\u70b9\u540e\u81ea\u52a8\u65ad\u5f00\u8fde\u63a5\uff08\u6302\u673a\u8dd1\u56fe\u5e38\u7528\uff09\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.disableOnFinish = SettingHelper.bool(this.sgSafety, "\u4efb\u52a1\u7ed3\u675f\u5173\u95ed\u6a21\u5757", "\u8dd1\u5b8c / \u5931\u8d25\u540e\u81ea\u52a8\u628a\u672c\u6a21\u5757\u5173\u6389\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.noElytraWaitSec = SettingHelper.int_(this.sgSafety, "\u6ca1\u6709\u9798\u7fc5\u65f6\u7b49\u591a\u4e45\uff08\u79d2\uff09", "\u8eab\u4e0a\u6ca1\u6709\u53ef\u7528\u9798\u7fc5\u65f6\u5148\u7b49\u8fd9\u4e48\u4e45\uff08\u521a\u8fdb\u670d\u52a1\u5668\u7269\u54c1\u680f\u53ef\u80fd\u8fd8\u6ca1\u540c\u6b65\uff09\u3002\u9ed8\u8ba4 60\u3002", 60, 0, 600);
        this.debugMessages = SettingHelper.bool(this.sgDebug, "\u8c03\u8bd5\u8f93\u51fa", "\u5728\u804a\u5929\u680f\u6253\u5370\u72b6\u6001\u673a\u7684\u6bcf\u4e00\u6b65\u3001\u6f5c\u5f71\u76d2\u626b\u63cf\u7ed3\u679c\u4e0e\u706b\u7403/\u5ca9\u6d46\u7ec6\u8282\u3002\u9ed8\u8ba4\u5173\u3002", false);
        this.hudInfo = SettingHelper.bool(this.sgDebug, "HUD \u72b6\u6001", "\u5728\u6a21\u5757\u5217\u8868\u91cc\u663e\u793a\u5f53\u524d\u72b6\u6001/\u8ddd\u79bb/\u70df\u82b1\u6570\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.statusMonitor = SettingHelper.bool(this.sgDebug, "\u72b6\u6001\u76d1\u63a7", "\u6bcf 10 \u79d2\u5728\u804a\u5929\u680f\u6253\u4e00\u884c\u5f53\u524d\u72b6\u6001\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.detailLog = SettingHelper.bool(this.sgDebug, "\u8be6\u7ec6\u65e5\u5fd7\uff08\u5199\u6587\u4ef6\uff09", "\u628a\u6bcf\u4e00\u6b65\u52a8\u4f5c\u5199\u8fdb icehack-logs \u4e0b\u7684\u65e5\u5fd7\u6587\u4ef6\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.logSteps = SettingHelper.bool(this.sgDebug, "\u65e5\u5fd7\u8bb0\u5f55\u6bcf\u4e00\u6b65\u52a8\u4f5c", "\u8fde\u6bcf\u4e00\u6b21\u70b9\u51fb/\u6309\u952e/\u72b6\u6001\u8fc1\u79fb\u90fd\u5199\u8fdb\u65e5\u5fd7\uff08\u6587\u4ef6\u66f4\u5927\uff09\u3002\u9ed8\u8ba4\u5f00\u3002", true);
        this.logKeep = SettingHelper.int_(this.sgDebug, "\u65e5\u5fd7\u4fdd\u7559\u4efd\u6570", "icehack-logs \u76ee\u5f55\u6700\u591a\u4fdd\u7559\u51e0\u4efd\u65e5\u5fd7\uff0c\u8001\u7684\u81ea\u52a8\u5220\u9664\u3002\u9ed8\u8ba4 10\u3002", 10, 2, 50);
        this.eat = new EatController();
        this.fireballs = new FireballDeflector();
        this.lavaCacheRadius = -1;
        this.lavaCachePitch = -999.0;
        this.pdCacheHorizon = Double.NaN;
        this.pdCacheUrgent = Double.NaN;
        this.pdCacheLateral = -1;
        this.pdCacheReturn = -1;
        this.pdCacheCooldown = -1;
        this.pdCacheDeflect = Double.NaN;
        this.restocker = new InventoryRestocker();
        this.junkDropper = new JunkDropper();
        this.exhaustedItems = new LinkedHashSet<Item>();
        this.lastSupplyStockSignature = "";
        this.state = State.IDLE;
        this.failReason = "";
        this.route = new ArrayList<BlockPos>();
        this.takeoffPhase = TakeoffPhase.INIT;
        this.fakeGlideWindow = new TimelinessCounter(20);
        this.takeoffFlowOwner = "";
        this.fireworkSeenTotal = -1;
        this.openLavaWindow = new TimelinessCounter(20);
        this.lastActivityDistance = -1.0;
        this.segFailWindow = new TimelinessCounter(600);
        this.spinWindow = new TimelinessCounter(400);
        this.landingY = Double.NaN;
        this.lastReplanTick = -600;
        this.recoverAfter = RecoverAfter.PREPARE;
        this.lastHurtHealth = -1.0;
        this.foodPriorityRaw = "\u0000";
        this.foodPriorityParsed = List.of();
        OrderedItemListWidget.registerFactory();
    }

    public void onActivate() {
        SupplyTask keepRecovering;
        this.state = State.PREPARE;
        this.failReason = "";
        this.route.clear();
        this.routeIndex = 0;
        this.segmentTarget = null;
        this.directionInitialised = false;
        this.tickCounter = 0;
        this.migrateFoodPriority();
        this.resetTerrainState();
        this.applyNetherSeedPause();
        this.applyBtSafety();
        this.resetSegmentStability();
        this.waitTicks = 0;
        this.takeoffTicks = 0;
        this.takeoffPhase = TakeoffPhase.INIT;
        this.jumpSeq = 0;
        this.jumpSeqTicks = 0;
        this.jumpAttempts = 0;
        this.fallTicks = 0;
        this.glidingLostTicks = 0;
        this.openTriesDone = 0;
        this.clearWaited = 0;
        this.clearingHead = false;
        this.ascendTicks = 0;
        this.ascendTargetY = 0.0;
        this.openEnd = null;
        this.openStartY = 0.0;
        this.hopTicks = 0;
        this.openSearchFails = 0;
        this.ascendingSearch = false;
        this.searchYh = 0.0;
        this.viewHoldTicks = 0;
        this.hovering = false;
        this.pausedByPlayer = false;
        this.segFailWindow.reset();
        this.spinWindow.reset();
        this.spinPauseTicks = 0;
        this.lastSpinPos = null;
        this.segResetDone = false;
        this.forceFlyToOpen = false;
        this.supplyRetries = 0;
        this.supplyErrorRetryCount = 0;
        this.lastSupplyStockSignature = "";
        this.supplyNoProgressRounds = 0;
        this.supplyCooldown = 0;
        this.exhaustedItems.clear();
        this.foodExhaustedCache = false;
        this.exhaustedCooldown = 0;
        this.mendRetries = 0;
        this.mendCooldown = 0;
        this.hoverStart = 0;
        this.fireballTooManyWarned = false;
        this.flightSettingsApplied = false;
        this.takeoffAutoJumpUsed = false;
        this.resetFakeGlideRecovery();
        this.directionFrozen = 0.0f;
        this.supplyTask = keepRecovering = this.recoverArmed ? this.supplyTask : null;
        this.mendTask = null;
        this.manualTask = false;
        this.restocker.reset();
        this.startFullSupplyDone = false;
        this.startFullSupplyPending = false;
        this.noElytraWaitTicks = 0;
        this.suppressLogout = false;
        this.fireballs.reset();
        this.eat.stop();
        this.ensureLava();
        this.ensureLavaPredictor();
        if (this.lavaPredictor != null) {
            this.lavaPredictor.reset();
        }
        if (!BaritoneHook.available()) {
            this.error("\u6ca1\u6709\u68c0\u6d4b\u5230 Baritone\uff1a\u8bf7\u5148\u5b89\u88c5 Baritone\uff08\u6216 Meteor \u7684 baritone \u96c6\u6210\uff09\u518d\u4f7f\u7528\u672c\u6a21\u5757\u3002", new Object[0]);
            this.state = State.FAILED;
            if (this.isActive()) {
                this.toggle();
            }
            return;
        }
        if (!BaritoneHook.ready()) {
            this.error("Baritone \u5df2\u5b89\u88c5\u4f46\u8fd8\u6ca1\u6709\u5c31\u7eea\uff08\u62ff\u4e0d\u5230 IBaritone \u5b9e\u4f8b\uff09\uff0c\u8bf7\u8fdb\u5165\u4e16\u754c\u540e\u518d\u6253\u5f00\u672c\u6a21\u5757\u3002", new Object[0]);
            this.state = State.FAILED;
            if (this.isActive()) {
                this.toggle();
            }
            return;
        }
        if (((Boolean)this.btTermsAccepted.get()).booleanValue()) {
            BaritoneHook.acceptTerms();
        }
        BaritoneHook.installSegFailLogger();
        BaritoneHook.clearSegFailCounter();
        this.refreshBaritoneStatus();
        this.warnConflicts();
        FOElytraLog.fileVerbose = (Boolean)this.logSteps.get();
        if (this.mc.runDirectory != null) {
            FOElytraLog.setGameDir(this.mc.runDirectory.toPath());
            if (((Boolean)this.detailLog.get()).booleanValue()) {
                this.logFileOwned = FOElytraLog.currentFile() == null;
                FOElytraLog.ensureOpen(this.mc.runDirectory.toPath(), (Integer)this.logKeep.get());
                this.dumpEnvironment();
            }
        }
        if (((Boolean)this.btAutoJump.get()).booleanValue()) {
            this.warning("Baritone \u7684 elytraAutoJump = true\uff08\u5df2\u5199\u8fdb baritone/settings.txt\uff09\uff1a\u5f00\u7740\u5b83 Baritone \u4f1a\u5728\u8d77\u98de\u524d\u5148\u53bb\u627e\u300c\u80fd\u5f80\u4e0b\u8df3\u7684\u53f0\u9636\u300d\uff0c\u5e73\u539f/\u5ba4\u5185\u76f4\u63a5\u62a5 Failed to compute a walking path to a spot to jump off from \u5e76\u62d2\u7edd\u8d77\u98de\u3002\u672c\u63d2\u4ef6\u8d77\u98de\u65f6\u4f1a\u4e34\u65f6\u538b\u6389\u8fd9\u4e00\u9879\uff1b\u60f3\u6c38\u4e45\u5173\u6389\u5c31\u5173\u6389\u9762\u677f\u300cBaritone \u98de\u884c \u2192 \u81ea\u52a8\u8d77\u8df3\u300d\u518d\u70b9\u4e00\u6b21\u300c\u4fdd\u5b58\u5e76\u8bbe\u4e3a\u9ed8\u8ba4\u300d\u3002", new Object[0]);
        }
        FOElytraLog.info("AutoElytraFlight \u542f\u52a8\uff1a\u6a21\u5f0f %s\uff0cBaritone \u5c31\u7eea", this.mode.get());
        if (keepRecovering != null && keepRecovering.isRecovering()) {
            this.stopRecoverRunner();
            this.state = State.RECOVER;
            FOElytraLog.warn("\u6a21\u5757\u53c8\u6253\u5f00\u4e86\uff1a\u5148\u63a5\u7740\u628a\u4e0a\u6b21\u6ca1\u6536\u56de\u7684\u6f5c\u5f71\u76d2/\u672b\u5f71\u7bb1\u6536\u5b8c\uff0c\u518d\u7ee7\u7eed\u8dd1\u56fe", new Object[0]);
        }
        if (this.mc.player != null && ItemHelper.wornElytra((PlayerEntity)this.mc.player).isEmpty()) {
            this.warning("\u8eab\u4e0a\u6ca1\u6709\u7a7f\u9798\u7fc5\uff0c\u51c6\u5907\u9636\u6bb5\u4f1a\u5c1d\u8bd5\u81ea\u52a8\u7a7f\u4e0a\u3002", new Object[0]);
        } else if (this.mc.player != null && ItemHelper.remainingDurability(ItemHelper.wornElytra((PlayerEntity)this.mc.player)) < 30 && (Integer)this.targetElytraCount.get() <= 0) {
            this.warning("\u8eab\u4e0a\u7684\u9798\u7fc5\u53ea\u5269 %d \u70b9\u8010\u4e45\uff0c\u800c\u300c\u8865\u7ed9\u6570\u91cf \u2192 \u76ee\u6807\u5907\u7528\u9798\u7fc5\u300d\u662f 0 \u2014\u2014 \u9798\u7fc5\u4e00\u98de\u574f\uff0c\u6a21\u5757\u5c31\u518d\u4e5f\u8d77\u4e0d\u6765\u4e86\u3002\u5efa\u8bae\u628a\u300c\u76ee\u6807\u5907\u7528\u9798\u7fc5\u300d\u8bbe\u6210 1~2 \u7ec4\u3002", new Object[]{ItemHelper.remainingDurability(ItemHelper.wornElytra((PlayerEntity)this.mc.player))});
        }
    }

    public void onDeactivate() {
        BounceProbe.shutdown();
        this.eat.stop();
        this.fireballs.reset();
        this.restocker.reset();
        BaritoneHook.removeSegFailLogger();
        this.restoreNetherSeed();
        this.restoreBtSafety();
        if (this.lava != null) {
            this.lava.release(this.mc);
        }
        if (this.lavaPredictor != null) {
            this.lavaPredictor.release(this.mc);
        }
        this.viewHoldTicks = 0;
        if (this.takeoffAutoJumpUsed) {
            this.takeoffAutoJumpUsed = false;
            this.restoreAutoJumpIfOurs();
        }
        if (this.baritoneAutoJumpForced) {
            this.baritoneAutoJumpForced = false;
            this.restoreAutoJumpIfOurs();
        }
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        PlayerAction.clearStuckSneak();
        BlockBreaker.cancel();
        this.releaseTakeoffFlow();
        BaritoneHook.stop();
        boolean recoveringNow = false;
        if (this.supplyTask != null && (this.supplyTask.isRunning() || this.supplyTask.recoverNeeded())) {
            if (this.supplyTask.recoverNeeded() && this.supplyTask.beginRecover("\u6a21\u5757\u5173\u95ed")) {
                this.recoverArmed = true;
                this.recoverAfter = RecoverAfter.IDLE;
                this.recoverWatchdog = 0;
                recoveringNow = true;
                FOElytraLog.warn("\u6a21\u5757\u5173\u95ed\uff1a\u5148\u628a\u653e\u4e0b\u7684\u6f5c\u5f71\u76d2/\u672b\u5f71\u7bb1\u6536\u56de\u6765\uff08\u6700\u591a %d \u79d2\uff09\u518d\u505c", 25);
            } else if (this.supplyTask.isRunning()) {
                this.supplyTask.abort("\u6a21\u5757\u5173\u95ed");
            }
        }
        if (this.mendTask != null && this.mendTask.isRunning()) {
            this.mendTask.abort("\u6a21\u5757\u5173\u95ed");
        }
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        if (recoveringNow) {
            this.state = State.RECOVER;
            this.startRecoverRunner();
        } else if (this.state != State.IDLE) {
            FOElytraLog.info("AutoElytraFlight \u5df2\u5173\u95ed\uff08%s\uff09", this.state.name());
            this.state = State.IDLE;
        }
        if (this.logFileOwned && !recoveringNow) {
            this.logFileOwned = false;
            FOElytraLog.closeFile();
        }
    }

    public boolean flyTo(int x, int z) {
        if (this.mc.player == null || this.mc.world == null) {
            return false;
        }
        this.targetX.set(x);
        this.targetZ.set(z);
        this.mode.set(Mode.SingleTarget);
        this.segmentTarget = null;
        this.segFailWindow.reset();
        this.segResetDone = false;
        this.spinWindow.reset();
        this.spinPauseTicks = 0;
        this.takeoffPhase = TakeoffPhase.INIT;
        this.flightSettingsApplied = false;
        this.forceFlyToOpen = false;
        this.takeoffTicks = 0;
        this.jumpAttempts = 0;
        this.openTriesDone = 0;
        this.startFullSupplyDone = true;
        this.startFullSupplyPending = false;
        this.manualTask = false;
        this.state = State.PREPARE;
        FOElytraLog.info("\u6536\u5230\u5916\u90e8\u98de\u884c\u8bf7\u6c42\uff1a%d, %d\uff08\u5f53\u524d\u8ddd\u79bb %.0f \u683c\uff09", x, z, Math.hypot(this.mc.player.getX() - ((double)x + 0.5), this.mc.player.getZ() - ((double)z + 0.5)));
        return true;
    }

    public boolean travelFinished() {
        return this.state == State.DONE || this.state == State.FAILED || this.state == State.IDLE || this.segmentTarget == null;
    }

    public boolean travelFailed() {
        return this.state == State.FAILED;
    }

    public boolean travelTerminal() {
        return this.state == State.DONE || this.state == State.FAILED || this.state == State.IDLE;
    }

    public double distanceToSegmentTarget() {
        if (this.mc.player == null || this.segmentTarget == null) {
            return -1.0;
        }
        double dx = this.mc.player.getX() - ((double)this.segmentTarget.getX() + 0.5);
        double dz = this.mc.player.getZ() - ((double)this.segmentTarget.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    public String travelStateName() {
        return this.state.name();
    }

    public String travelFailReason() {
        return this.failReason == null ? "" : this.failReason;
    }

    public String getInfoString() {
        if (!((Boolean)this.hudInfo.get()).booleanValue()) {
            return null;
        }
        if (this.mc.player == null || this.state == State.IDLE) {
            return this.state.name();
        }
        StringBuilder sb = new StringBuilder(this.state.name());
        if (this.segmentTarget != null) {
            double dx = this.mc.player.getX() - ((double)this.segmentTarget.getX() + 0.5);
            double dz = this.mc.player.getZ() - ((double)this.segmentTarget.getZ() + 0.5);
            sb.append(String.format(" %.0fm", Math.sqrt(dx * dx + dz * dz)));
        }
        sb.append(" \u70df\u82b1").append(ItemHelper.countInHotbar((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET));
        if ((this.state == State.SUPPLY || this.state == State.RECOVER) && this.supplyTask != null) {
            sb.append(" ").append(this.supplyTask.progress());
        }
        if (this.state == State.MEND && this.mendTask != null) {
            sb.append(" ").append(this.mendTask.progress());
        }
        return sb.toString();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!this.isActive() || this.mc.player == null || this.mc.world == null) {
            return;
        }
        FOElytraLog.debugEnabled = (Boolean)this.debugMessages.get();
        try {
            this.tick();
        }
        catch (Throwable t) {
            this.onError("onTick", t);
            this.fail("\u5185\u90e8\u5f02\u5e38 " + t.getClass().getSimpleName());
        }
    }

    private void dumpEnvironment() {
        Object[] objectArray = new Object[3];
        objectArray[0] = "1.21.11";
        objectArray[1] = BaritoneHook.available() ? (BaritoneHook.ready() ? "\u5df2\u5c31\u7eea" : "\u5df2\u52a0\u8f7d\u672a\u5c31\u7eea") : "\u672a\u5b89\u88c5";
        objectArray[2] = this.name;
        FOElytraLog.detail("Minecraft %s\uff5cBaritone %s\uff5c\u6a21\u5757 %s", objectArray);
        if (this.mc.player != null) {
            FOElytraLog.detail("\u73a9\u5bb6\u4f4d\u7f6e %d %d %d\uff5c\u4e16\u754c %s", this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ(), this.mc.world != null ? this.mc.world.getRegistryKey().getValue() : "?");
        }
        FOElytraLog.detail("\u2014\u2014 \u5f53\u524d\u8bbe\u7f6e \u2014\u2014", new Object[0]);
        try {
            for (SettingGroup g : this.settings) {
                FOElytraLog.detail("\u3010%s\u3011", g.name);
                for (Setting s : g) {
                    FOElytraLog.detail("    %s = %s", s.name, s.get());
                }
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("dumpEnvironment", t);
        }
        FOElytraLog.detail("\u2014\u2014 \u8bbe\u7f6e\u7ed3\u675f \u2014\u2014", new Object[0]);
    }

    private void statusHeartbeat() {
        if (!((Boolean)this.statusMonitor.get()).booleanValue()) {
            return;
        }
        if (this.tickCounter % 200 != 0) {
            return;
        }
        if (this.mc.player == null) {
            return;
        }
        String dist = this.segmentTarget == null ? "-" : String.format("%.0f", Math.hypot(this.mc.player.getX() - ((double)this.segmentTarget.getX() + 0.5), this.mc.player.getZ() - ((double)this.segmentTarget.getZ() + 0.5)));
        String predict = this.lavaPredictor != null && (this.lavaPredictor.threat() != null || this.lavaPredictor.isAvoiding()) ? " | " + this.lavaPredictor.statusText() : "";
        FOElytraLog.info("\u72b6\u6001\u76d1\u63a7\uff1a\u72b6\u6001 %s%s | Baritone %s | \u70df\u82b1 %d \u53d1\uff08%d \u7ec4\uff09| \u8840 %.1f | \u8ddd\u76ee\u6807 %s \u683c | \u6ed1\u7fd4 %s%s", this.state.name(), this.hovering ? "(\u7b49\u533a\u5757)" : (this.pausedByPlayer ? "(\u8ba9\u884c)" : ""), BaritoneHook.isFlying() ? "\u98de\u884c\u4e2d" : "\u672a\u63a5\u7ba1", ItemHelper.countInHotbar((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET), ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET)), Float.valueOf(this.mc.player.getHealth()), dist, this.mc.player.isGliding() ? "\u662f" : "\u5426", predict);
    }

    private void tick() {
        boolean escaping;
        boolean lavaHoldNow;
        ++this.tickCounter;
        BaritoneHook.markTick();
        BounceProbe.tick(this.mc, this.state.name(), BaritoneHook.isFlying());
        PlayerAction.forceNoSneak();
        if (this.takeoffFlowHoldTicks > 0 && --this.takeoffFlowHoldTicks == 0) {
            FOElytraLog.detail("\u8d77\u98de\u6d41\u7a0b\u4e92\u65a5\u89e3\u9664\uff08%s \u5df2\u7ed3\u675f %d tick\uff09", this.takeoffFlowOwner, 20);
            this.takeoffFlowOwner = "";
        }
        if (this.tickCounter % 600 == 0) {
            BaritoneHook.installSegFailLogger();
        }
        if (this.supplyCooldown > 0) {
            --this.supplyCooldown;
        }
        if (this.mendCooldown > 0) {
            --this.mendCooldown;
        }
        if (this.supplyHurtNoLand > 0) {
            --this.supplyHurtNoLand;
        }
        if (this.exhaustedCooldown > 0 && --this.exhaustedCooldown == 0) {
            FOElytraLog.info("\u8865\u7ed9\u91cd\u8bd5\u51b7\u5374\u7ed3\u675f\uff08\u4e4b\u524d\u5224\u5b9a\u53d6\u5149\u7684\uff1a%s\uff09\uff0c\u4e0b\u6b21\u4ecd\u4f1a\u53bb\u672b\u5f71\u7bb1\u7ffb\u4e00\u904d", this.describeExhausted());
            this.exhaustedItems.clear();
            this.foodExhaustedCache = false;
        }
        boolean bl = lavaHoldNow = this.mc.player != null && this.mc.player.isInLava();
        if (lavaHoldNow) {
            this.eatHoldTicks = 60;
            if (this.eat.isEating()) {
                FOElytraLog.detail("\u5728\u5ca9\u6d46\u91cc\uff0c\u5148\u505c\u4e0b\u8fdb\u98df\uff08\u5403\u4e86\u4e5f\u6ca1\u7528\uff0c\u7b49\u51fa\u6765\u518d\u5403\uff09", new Object[0]);
                this.eat.stop();
            }
        } else if (this.eatHoldTicks > 0) {
            --this.eatHoldTicks;
        }
        this.ensureLava();
        this.ensureLavaPredictor();
        if (this.tickCounter % 20 == 0) {
            this.applyNetherSeedPause();
        }
        this.manualKeyTick();
        this.baritoneKeyTick();
        this.infinityElytraTick();
        boolean inLavaNow = this.mc.player != null && this.mc.player.isInLava();
        this.lavaTick();
        this.autoMendTick();
        this.inputStuckTick();
        if (this.lavaEscaping() || inLavaNow && ((Boolean)this.lavaEscape.get()).booleanValue()) {
            if (!this.lavaPriorityActive) {
                this.lavaPriorityActive = true;
                BaritoneHook.stop();
                BlockBreaker.cancel();
                PlayerAction.releaseAll();
                FOElytraLog.warn("\u5ca9\u6d46\u4f18\u5148\uff1a\u6682\u505c\u5176\u5b83\u6d41\u7a0b\uff08\u5f53\u524d\u72b6\u6001 %s\uff0c\u5728\u5ca9\u6d46\u91cc %s\uff0c\u6ed1\u7fd4 %s\uff09", new Object[]{this.state, inLavaNow ? "\u662f" : "\u5426", this.mc.player.isGliding() ? "\u662f" : "\u5426"});
            }
            if (this.eat.isEating()) {
                this.eat.stop();
            }
            return;
        }
        if (this.lavaPriorityActive) {
            this.lavaPriorityActive = false;
            FOElytraLog.info("\u5ca9\u6d46\u4f18\u5148\u7ed3\u675f\uff1a\u6062\u590d\u539f\u6765\u7684\u6d41\u7a0b\uff08\u72b6\u6001 %s\uff09", new Object[]{this.state});
        }
        boolean busy = this.state == State.SUPPLY || this.state == State.MEND;
        boolean guiOpen = InvHelper.screenOpen();
        if (guiOpen) {
            PlayerAction.restoreHeldKeys();
        }
        if (this.state == State.RECOVER) {
            this.recoverTick();
            return;
        }
        boolean bl2 = escaping = this.lavaEscaping() || this.lavaPredictor != null && this.lavaPredictor.isAvoiding();
        if (this.eat.isEating() && this.fireballs.isEngaging() && !this.fireballs.pausedBaritone()) {
            FOElytraLog.detail("\u6709\u706b\u7403\u8981\u62e6\u622a\uff1a\u5148\u505c\u4e0b\u8fdb\u98df\uff08\u514d\u5f97\u8fdb\u98df\u7ed3\u675f\u65f6\u628a\u62e6\u622a\u7684\u6682\u505c\u9876\u6389\uff09", new Object[0]);
            this.eat.stop();
        }
        if (!(busy || guiOpen || escaping || this.eatHoldTicks > 0 || this.pausedByPlayer || this.fireballs.isEngaging())) {
            this.fireballTick();
            this.eat.tick((Boolean)this.autoEat.get(), (Integer)this.hungerThreshold.get(), (Double)this.healthThreshold.get(), (Boolean)this.eatWhileGliding.get(), (Double)this.eatMinRise.get(), (List)this.foodWhitelist.get(), this.foodPriorityList());
        } else if (this.eat.isEating() && (guiOpen || escaping)) {
            this.eat.stop();
        }
        if (((Boolean)this.autoRestock.get()).booleanValue() && !busy && !guiOpen && !escaping) {
            this.restocker.tick((List)this.restockItems.get(), (Integer)this.restockStacks.get(), (Boolean)this.restockKeepHeld.get(), (Integer)this.restockInterval.get());
        }
        if (((Boolean)this.junkDrop.get()).booleanValue() && !busy && !guiOpen && !escaping && this.tickCounter % 10 == 0) {
            this.junkDropper.tick(true, (Boolean)this.junkOnlyLava.get(), (Integer)this.junkRadius.get(), (List)this.junkItems.get(), this.junkProtectedExtras(), this.tickCounter);
        }
        this.safetyTick();
        this.statusHeartbeat();
        switch (this.state.ordinal()) {
            case 1: {
                this.prepare();
                break;
            }
            case 2: {
                this.takeoff();
                break;
            }
            case 3: {
                this.flying();
                break;
            }
            case 4: {
                this.landing();
                break;
            }
            case 5: {
                this.supplyTick();
                break;
            }
            case 6: {
                this.mendTick();
                break;
            }
            case 7: {
                this.recoverTick();
                break;
            }
            case 8: 
            case 9: {
                break;
            }
            case 0: {
                this.state = State.PREPARE;
            }
        }
    }

    private void manualKeyTick() {
        boolean mendPressed;
        boolean supplyPressed;
        boolean bl = supplyPressed = this.supplyKey.get() != null && ((Keybind)this.supplyKey.get()).isPressed();
        if (supplyPressed && !this.supplyKeyWasPressed && this.state != State.SUPPLY && this.state != State.MEND && this.state != State.LANDING) {
            this.manualTask = true;
            if (this.startSupply()) {
                FOElytraLog.info("\u624b\u52a8\u89e6\u53d1\u8865\u7ed9", new Object[0]);
            } else {
                this.manualTask = false;
            }
        }
        this.supplyKeyWasPressed = supplyPressed;
        boolean bl2 = mendPressed = this.mendKey.get() != null && ((Keybind)this.mendKey.get()).isPressed();
        if (mendPressed && !this.mendKeyWasPressed && this.state != State.SUPPLY && this.state != State.MEND && this.state != State.LANDING) {
            this.manualTask = true;
            if (this.startMend()) {
                FOElytraLog.info("\u624b\u52a8\u89e6\u53d1\u4fee\u9798\u7fc5", new Object[0]);
            } else {
                this.manualTask = false;
            }
        }
        this.mendKeyWasPressed = mendPressed;
    }

    private void prepare() {
        if (!BaritoneHook.available()) {
            this.fail("Baritone \u4e0d\u53ef\u7528");
            return;
        }
        if (!this.ensureElytraWorn()) {
            if (this.noElytraWaitTicks++ < (Integer)this.noElytraWaitSec.get() * 20) {
                if (this.noElytraWaitTicks == 1 || this.noElytraWaitTicks % 100 == 0) {
                    FOElytraLog.warn("\u8eab\u4e0a\u6ca1\u6709\u53ef\u7528\u7684\u9798\u7fc5\uff1a\u5148\u7b49\u4f60\u7a7f\u4e0a\u6216\u7b49\u7269\u54c1\u680f\u540c\u6b65\uff08\u5df2\u7b49 %d \u79d2\uff0c\u6700\u591a %d \u79d2\uff09\u3002\u7b49\u6ee1\u540e\u624d\u4f1a\u5224\u5931\u8d25\uff0c\u8fd9\u6761\u5931\u8d25\u4e0d\u4f1a\u81ea\u52a8\u767b\u51fa\u3002", this.noElytraWaitTicks / 20, this.noElytraWaitSec.get());
                }
                return;
            }
            this.noElytraWaitTicks = 0;
            this.failNoLogout("\u6ca1\u6709\u4efb\u4f55\u53ef\u7528\u7684\u9798\u7fc5\uff08\u7b49\u4e86 " + String.valueOf(this.noElytraWaitSec.get()) + " \u79d2\u4ecd\u6ca1\u7a7f\u4e0a\uff09");
            return;
        }
        this.noElytraWaitTicks = 0;
        if (this.segmentTarget == null && !this.chooseNextTarget()) {
            this.finish("\u6240\u6709\u822a\u70b9\u5df2\u5b8c\u6210");
            return;
        }
        if (this.waitForUserScreen("\u51c6\u5907\u8d77\u98de/\u98de\u884c")) {
            return;
        }
        this.waitTicks = 0;
        boolean lavaNow = this.lavaDanger();
        if (!lavaNow && ((Boolean)this.autoSupply.get()).booleanValue() && ((Boolean)this.supplyBeforeSegment.get()).booleanValue() && this.supplyCooldown <= 0) {
            String need = this.supplyNeedText();
            if (!need.isEmpty()) {
                FOElytraLog.info("\u6240\u9700\u8865\u7ed9\uff1a%s", need);
                String stock = this.supplyStockSignature(need);
                if (stock.equals(this.lastSupplyStockSignature)) {
                    ++this.supplyNoProgressRounds;
                } else {
                    this.lastSupplyStockSignature = stock;
                    this.supplyNoProgressRounds = 0;
                }
                if (this.supplyNoProgressRounds >= 1) {
                    this.registerUnobtainableFromNeed(need);
                    this.lastSupplyStockSignature = "";
                    this.supplyNoProgressRounds = 0;
                    this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
                    return;
                }
                if (this.startSupply()) {
                    return;
                }
                this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
            } else {
                this.lastSupplyStockSignature = "";
                this.supplyNoProgressRounds = 0;
            }
        }
        if (!lavaNow && ((Boolean)this.autoSupply.get()).booleanValue() && ((Boolean)this.fullSupplyOnStart.get()).booleanValue() && !this.startFullSupplyDone && this.supplyCooldown <= 0) {
            if (this.fullSupplyNeeded()) {
                if (this.startSupply()) {
                    this.startFullSupplyDone = true;
                    this.startFullSupplyPending = true;
                    FOElytraLog.info("\u4efb\u52a1\u5f00\u59cb\uff1a\u5148\u8865\u6ee1\u7269\u8d44\u518d\u8d77\u98de\uff08%s\uff09\u2192 %s", this.supplyReason(), this.fullSupplyGap());
                    return;
                }
                this.startFullSupplyDone = true;
            } else {
                this.startFullSupplyDone = true;
                FOElytraLog.info("\u4efb\u52a1\u5f00\u59cb\uff1a\u7269\u8d44\u591f\u7528\uff0c\u76f4\u63a5\u8d77\u98de\uff08\u5dee\u989d\uff1a%s\uff09", this.fullSupplyGap());
            }
        }
        if (!lavaNow && ((Boolean)this.supplyBeforeSegment.get()).booleanValue() && ((Boolean)this.autoSupply.get()).booleanValue() && this.supplyNeeded() && this.supplyCooldown <= 0) {
            FOElytraLog.info("\u8d77\u98de\u524d\u68c0\u67e5\uff1a%s", this.supplyReason());
            if (this.startSupply()) {
                return;
            }
            this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
        }
        if (!lavaNow && ((Boolean)this.autoMend.get()).booleanValue() && this.mendCooldown <= 0 && MendTask.shouldRepair((Integer)this.mendDurability.get())) {
            if (this.startMend()) {
                return;
            }
            this.mendCooldown = 100;
        }
        this.flightSettingsApplied = false;
        this.takeoffTicks = 0;
        this.takeoffAutoJumpUsed = false;
        this.takeoffPhase = TakeoffPhase.INIT;
        this.jumpSeq = 0;
        this.jumpAttempts = 0;
        this.openTriesDone = 0;
        this.openEnd = null;
        this.clearingHead = false;
        this.glidingLostTicks = 0;
        this.resetFakeGlideRecovery();
        this.ascendingSearch = false;
        this.searchYh = 0.0;
        this.openSearchFails = 0;
        this.waitTicks = 0;
        this.state = State.TAKEOFF;
    }

    private void warnConflicts() {
        if (this.mc.player == null) {
            return;
        }
        ArrayList<String> flight = new ArrayList<String>();
        ArrayList<String> items = new ArrayList<String>();
        try {
            for (Module m : Modules.get().getAll()) {
                if (!m.isActive()) continue;
                if (CONFLICT_FLIGHT.contains(m.name)) {
                    flight.add(m.name);
                    continue;
                }
                if (!CONFLICT_ITEMS.contains(m.name)) continue;
                items.add(m.name);
            }
        }
        catch (Throwable t) {
            return;
        }
        if (!flight.isEmpty()) {
            this.warning("\u68c0\u6d4b\u5230\u540c\u65f6\u5f00\u542f\u7684\u98de\u884c\u6a21\u5757 %s \u2014\u2014 \u5b83\u4eec\u4f1a\u548c\u672c\u6a21\u5757\u62a2\u9798\u7fc5\u63a7\u5236\uff0c\u5efa\u8bae\u53ea\u7559\u4e00\u4e2a\u3002", new Object[]{flight});
        }
        if (!items.isEmpty()) {
            this.warning("\u68c0\u6d4b\u5230\u540c\u65f6\u5f00\u542f\u7684 %s \u2014\u2014 \u5b83\u4eec\u4f1a\u548c\u672c\u6a21\u5757\u62a2\u53f3\u952e/\u7269\u54c1\u680f\uff08\u672c\u6a21\u5757\u81ea\u5e26\u8fdb\u98df\u3001\u4fee\u9798\u7fc5\u4e0e\u70df\u82b1\u8865\u5145\uff09\uff0c\u5efa\u8bae\u5173\u6389\u3002", new Object[]{items});
        }
    }

    private boolean ensureElytraWorn() {
        int slot;
        ItemStack worn = ItemHelper.wornElytra((PlayerEntity)this.mc.player);
        if (!worn.isEmpty() && !AutoElytraFlight.isBroken(worn)) {
            return true;
        }
        if (InvHelper.screenOpen()) {
            return true;
        }
        if (!worn.isEmpty() && AutoElytraFlight.isBroken(worn)) {
            InvHelper.click(this.mc.player.currentScreenHandler, 6, 0, SlotActionType.QUICK_MOVE);
            FOElytraLog.warn("\u80f8\u7532\u69fd\u91cc\u7684\u9798\u7fc5\u5df2\u7ecf\u7528\u574f\u4e86\uff08\u8010\u4e45 0\uff09\uff0c\u5148\u53d6\u4e0b\u6765", new Object[0]);
        }
        if ((slot = InvHelper.findSlot(s -> s.isOf(Items.ELYTRA) && !AutoElytraFlight.isBroken(s), 0, 36)) < 0) {
            return false;
        }
        InvHelper.clickPlayerInv(slot, 0, SlotActionType.QUICK_MOVE);
        ItemStack after = ItemHelper.wornElytra((PlayerEntity)this.mc.player);
        if (!after.isEmpty() && !AutoElytraFlight.isBroken(after)) {
            FOElytraLog.tip("\u5df2\u81ea\u52a8\u7a7f\u4e0a\u9798\u7fc5\uff08\u5269\u4f59\u8010\u4e45 %d\uff09", ItemHelper.remainingDurability(after));
            return true;
        }
        FOElytraLog.warn("\u9798\u7fc5\u6ca1\u7a7f\u4e0a\uff08\u80f8\u7532\u69fd\u91cc\u662f\u4e0d\u662f\u6709\u522b\u7684\u76d4\u7532\uff1f\uff09\uff0c\u5148\u8131\u6389\u518d\u8bd5", new Object[0]);
        return false;
    }

    private static boolean isBroken(ItemStack stack) {
        return stack.getMaxDamage() > 0 && stack.getDamage() >= stack.getMaxDamage();
    }

    private boolean chooseNextTarget() {
        if (this.lavaPredictor != null) {
            this.lavaPredictor.reset();
        }
        switch (((Mode)((Object)this.mode.get())).ordinal()) {
            case 1: {
                this.segmentTarget = new BlockPos(((Integer)this.targetX.get()).intValue(), 0, ((Integer)this.targetZ.get()).intValue());
                FOElytraLog.info("\u76ee\u6807\u5750\u6807\uff1a%d, %d", this.targetX.get(), this.targetZ.get());
                return true;
            }
            case 2: {
                if (!this.directionInitialised) {
                    this.directionInitialised = true;
                    this.directionFrozen = this.mc.player.getYaw();
                    FOElytraLog.info("\u5b9a\u5411\u8dd1\u56fe\u65b9\u5411\u5df2\u9501\u5b9a\uff1a%.0f\u00b0\uff08%s\uff09\u3002\u60f3\u6362\u65b9\u5411\u8bf7\u8f6c\u5934\u540e\u91cd\u65b0\u5f00\u5173\u6a21\u5757\u3002", Float.valueOf(this.directionFrozen), AutoElytraFlight.headingName(this.directionFrozen));
                }
                double rad = Math.toRadians(this.directionFrozen);
                int dx = (int)Math.round(-Math.sin(rad) * (double)((Integer)this.segmentDistance.get()).intValue());
                int dz = (int)Math.round(Math.cos(rad) * (double)((Integer)this.segmentDistance.get()).intValue());
                this.segmentTarget = new BlockPos(this.mc.player.getBlockX() + dx, 0, this.mc.player.getBlockZ() + dz);
                FOElytraLog.info("\u5b9a\u5411\u8dd1\u56fe\u4e0b\u4e00\u6bb5\uff1a%d, %d\uff08\u65b9\u5411 %.0f\u00b0 %s\uff09", this.segmentTarget.getX(), this.segmentTarget.getZ(), Float.valueOf(this.directionFrozen), AutoElytraFlight.headingName(this.directionFrozen));
                return true;
            }
        }
        if (this.route.isEmpty()) {
            this.route.addAll(this.parseWaypoints());
            if (this.route.isEmpty()) {
                this.fail("\u822a\u70b9\u5217\u8868\u4e3a\u7a7a\uff08\u683c\u5f0f\u5e94\u4e3a x,z\uff09");
                return false;
            }
        }
        if (this.routeIndex >= this.route.size()) {
            if (!((Boolean)this.loop.get()).booleanValue()) {
                return false;
            }
            this.routeIndex = 0;
        }
        this.segmentTarget = this.route.get(this.routeIndex);
        FOElytraLog.info("\u7b2c %d/%d \u4e2a\u822a\u70b9\uff1a%d, %d", this.routeIndex + 1, this.route.size(), this.segmentTarget.getX(), this.segmentTarget.getZ());
        ++this.routeIndex;
        return true;
    }

    private List<BlockPos> parseWaypoints() {
        ArrayList<BlockPos> list = new ArrayList<BlockPos>();
        for (String raw : this.waypoints.get()) {
            String line;
            if (raw == null || (line = raw.trim()).isEmpty()) continue;
            String[] parts = line.split("[,\\s]+");
            if (parts.length < 2) {
                FOElytraLog.warn("\u822a\u70b9\u683c\u5f0f\u65e0\u6cd5\u8bc6\u522b\uff1a%s", line);
                continue;
            }
            try {
                list.add(new BlockPos(Integer.parseInt(parts[0].trim()), 0, Integer.parseInt(parts[1].trim())));
            }
            catch (NumberFormatException e) {
                FOElytraLog.warn("\u822a\u70b9\u6570\u5b57\u89e3\u6790\u5931\u8d25\uff1a%s", line);
            }
        }
        return list;
    }

    private void takeoff() {
        if (this.segmentTarget == null) {
            PlayerAction.pressJump(false);
            this.state = State.PREPARE;
            return;
        }
        if (this.lavaEscaping()) {
            return;
        }
        if (this.waitForUserScreen("\u8d77\u98de")) {
            return;
        }
        this.waitTicks = 0;
        if (!this.flightSettingsApplied) {
            this.flightSettingsApplied = true;
            int fwStacksNow = ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET));
            if ((Integer)this.minFireworkStacks.get() > 0 && fwStacksNow < (Integer)this.minFireworkStacks.get()) {
                FOElytraLog.err("\u6ca1\u6709\u70df\u82b1\uff0c\u4e0d\u80fd\u8d77\u98de\uff08\u968f\u8eab\u53ea\u6709 %d \u7ec4\uff0c\u6700\u4f4e\u8981 %d \u7ec4\uff0c\u8865\u7ed9\u4e5f\u6ca1\u8865\u4e0a\uff09\u2192 \u505c\u624b\uff0c\u5f80\u672b\u5f71\u7bb1\u91cc\u8865\u70df\u82b1\u6216\u624b\u52a8\u62ff\u51fa\u6765\u518d\u5f00\uff1b0 \u70df\u82b1\u8d77\u98de\u53ea\u4f1a\u6ed1\u7fd4\u8fdb\u5ca9\u6d46/\u6454\u6b7b", fwStacksNow, this.minFireworkStacks.get());
                PlayerAction.pressJump(false);
                PlayerAction.pressUse(false);
                PlayerAction.pressForward(false);
                PlayerAction.restoreHeldKeys();
                BaritoneHook.stop();
                if (this.beginRecover("\u6ca1\u6709\u70df\u82b1\u4e0d\u80fd\u8d77\u98de", RecoverAfter.IDLE)) {
                    FOElytraLog.warn("\u5148\u8d70\u5f52\u4f4d\uff1a\u628a\u653e\u4e0b\u7684\u672b\u5f71\u7bb1\u548c\u6f5c\u5f71\u76d2\u6536\u56de\u6765\uff0c\u6536\u5b8c\u5c31\u505c\u624b\uff08\u4e0d\u786c\u8d77\u98de\uff09", new Object[0]);
                    return;
                }
                this.failNoLogout("\u6ca1\u6709\u70df\u82b1\u4e0d\u80fd\u8d77\u98de\uff08\u968f\u8eab " + fwStacksNow + " \u7ec4\uff0c\u6700\u4f4e\u8981 " + String.valueOf(this.minFireworkStacks.get()) + " \u7ec4\uff09");
                return;
            }
            this.baritoneAutoJumpForced = true;
            BaritoneHook.applyFlightSettings((Boolean)this.takeoffByBaritone.get(), 0.5, (Boolean)this.infinityElytra.get() != false ? false : (Boolean)this.btAllowEmergencyLand.get());
            this.lastForcedAutoJump = (Boolean)this.takeoffByBaritone.get();
            if (!this.allowReplan("\u8d77\u98de")) {
                FOElytraLog.detail("\u8d77\u98de\u9996\u6bb5\u91cd\u89c4\u5212\u88ab\u8282\u6d41\u8df3\u8fc7\uff0c\u4ea4\u7ed9\u540e\u9762\u7684\u6389\u7ebf\u91cd\u89c4\u5212\u515c\u5e95", new Object[0]);
            } else if (!BaritoneHook.pathTo(this.segmentTarget.getX(), this.segmentTarget.getZ())) {
                this.fail("Baritone \u62d2\u7edd\u89c4\u5212\u9798\u7fc5\u8def\u7ebf");
                return;
            }
            this.takeoffDelayTicks = 15;
            this.takeoffPhase = TakeoffPhase.LAUNCH;
            if (this.forceFlyToOpen) {
                this.forceFlyToOpen = false;
                this.takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
                FOElytraLog.info("\u590d\u98de\uff1a\u76f4\u63a5\u8fdb\u5165\u300c\u98de\u5f80\u5f00\u9614\u5730\u300d\u6d41\u7a0b", new Object[0]);
            }
            this.takeoffTicks = 0;
            this.jumpAttempts = 0;
            this.openTriesDone = 0;
            this.openEnd = null;
            this.clearingHead = false;
            this.jumpSeq = 0;
            this.glidingLostTicks = 0;
            this.resetFakeGlideRecovery();
            PlayerAction.clearStuckSneak();
            FOElytraLog.info("\u5f00\u59cb\u8d77\u98de\uff1a\u4f4d\u7f6e %d %d %d\uff5c\u5730\u9762 %s\uff5c\u6ed1\u7fd4 %s\uff5c\u5934\u9876\u88ab\u6321 %s\uff5c\u70df\u82b1 %d \u53d1", this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ(), this.mc.player.isOnGround() ? "\u662f" : "\u5426", this.mc.player.isGliding() ? "\u662f" : "\u5426", AutoElytraFlight.blockingBlocks(1).isEmpty() ? "\u5426" : "\u662f", ItemHelper.countInHotbar((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET));
        }
        ++this.takeoffTicks;
        if (this.baritoneControlling()) {
            this.takeoffSucceeded();
            return;
        }
        int budget = Math.max(600, (Integer)this.takeoffTimeout.get() * 5);
        if (((Boolean)this.autoTakeoff.get()).booleanValue() && this.takeoffTicks > budget) {
            this.takeoffFallbackOrFail(String.format("\u8d77\u98de\u6d41\u7a0b\u8dd1\u4e86 %d tick\uff08%.0f \u79d2\uff09Baritone \u4ecd\u672a\u63a5\u7ba1", this.takeoffTicks, (double)this.takeoffTicks / 20.0));
            return;
        }
        switch (this.takeoffPhase.ordinal()) {
            case 2: {
                this.clearHeadTick();
                break;
            }
            case 3: {
                this.flyToOpenTick();
                break;
            }
            case 4: {
                this.ascendTick();
                break;
            }
            case 5: {
                this.waitArriveTick();
                break;
            }
            default: {
                if (((Boolean)this.autoTakeoff.get()).booleanValue()) {
                    this.launchTick();
                    break;
                }
                this.manualLaunchTick();
            }
        }
    }

    private boolean baritoneControlling() {
        return this.mc.player != null && this.mc.player.isGliding() && !this.mc.player.isOnGround() && BaritoneHook.isFlying();
    }

    private void updateActivity() {
        if (this.mc.player == null) {
            this.activityTicks = 0;
            return;
        }
        boolean airborneFalling = !this.mc.player.isOnGround() && this.mc.player.getVelocity().y < -0.05;
        double horiz = Math.sqrt(this.mc.player.getVelocity().x * this.mc.player.getVelocity().x + this.mc.player.getVelocity().z * this.mc.player.getVelocity().z);
        boolean moving = horiz > 0.2;
        boolean closing = false;
        if (this.segmentTarget != null) {
            double d = Math.hypot(this.mc.player.getX() - ((double)this.segmentTarget.getX() + 0.5), this.mc.player.getZ() - ((double)this.segmentTarget.getZ() + 0.5));
            closing = this.lastActivityDistance >= 0.0 && d < this.lastActivityDistance - 0.05;
            this.lastActivityDistance = d;
        }
        if (airborneFalling || moving || closing) {
            if (this.activityTicks < 100) {
                ++this.activityTicks;
            }
        } else {
            this.activityTicks = 0;
        }
    }

    private void takeoffSucceeded() {
        BounceProbe.mark("\u8d77\u98de\u6210\u529f");
        PlayerAction.pressJump(false);
        this.jumpSeq = 0;
        this.releaseTakeoffFlow();
        if (this.takeoffAutoJumpUsed) {
            this.takeoffAutoJumpUsed = false;
            BaritoneHook.btSet("elytraAutoJump", false);
            this.lastForcedAutoJump = false;
            this.baritoneAutoJumpForced = true;
        }
        FOElytraLog.info("\u8d77\u98de\u6210\u529f\uff1aBaritone \u5df2\u63a5\u7ba1\u9798\u7fc5\u98de\u884c\uff08\u7b2c %d tick\uff0c\u65b9\u5f0f %s\uff09", new Object[]{this.takeoffTicks, this.takeoffPhase});
        this.takeoffPhase = TakeoffPhase.INIT;
        this.openEnd = null;
        this.clearingHead = false;
        this.hopTicks = 0;
        this.ascendingSearch = false;
        this.searchYh = 0.0;
        this.openSearchFails = 0;
        this.state = State.FLYING;
        this.waitTicks = 0;
        this.clearShaftTakeoff();
        this.btTakeoffWaitTick = 0;
        this.btTakeoffFallbackLogged = false;
        BaritoneHook.btSet("elytraAutoJump", false);
        this.lastForcedAutoJump = false;
        this.takeoffReArmCount = 0;
        this.jumpAttemptTick = this.tickCounter;
        this.btTakeoffWaitTick = 0;
        this.btTakeoffFallbackLogged = false;
        this.lastCheckX = this.mc.player.getBlockX();
        this.lastCheckZ = this.mc.player.getBlockZ();
    }

    private boolean flightStatusCheck() {
        boolean stationary;
        if (this.mc.player == null) {
            return true;
        }
        this.updateActivity();
        if (this.baritoneControlling()) {
            this.controlTicks = 0;
            this.lostControlCycles = 0;
            this.airborneTicks = 0;
            return false;
        }
        if (this.fakeGlideTick()) {
            return true;
        }
        if (this.mc.player.isGliding() && !this.mc.player.isOnGround()) {
            if (++this.controlTicks > 15) {
                this.controlTicks = 0;
                if (((Boolean)this.openAreaSearch.get()).booleanValue() && this.openTriesDone < (Integer)this.openTries.get()) {
                    this.beginFlyToOpen();
                } else if (!BaritoneHook.isFlying() && ++this.lostControlCycles >= 2) {
                    this.lostControlCycles = 0;
                    this.replanIfLost(true);
                }
            }
            return true;
        }
        if (!this.mc.player.isOnGround()) {
            if (this.mc.player.isGliding()) {
                this.notGlidingAirTicks = 0;
            } else if (++this.notGlidingAirTicks == 20 || this.notGlidingAirTicks % 100 == 0) {
                boolean hasElytra = !ItemHelper.wornElytra((PlayerEntity)this.mc.player).isEmpty();
                FOElytraLog.warn("\u4eba\u5728\u7a7a\u4e2d\u4f46\u9798\u7fc5\u6ca1\u5c55\u5f00\uff08\u5ba2\u6237\u7aef\u8bf4\u6ca1\u5728\u6ed1\u7fd4\uff09\u5df2 %.1f \u79d2\uff5c\u8eab\u4e0a\u9798\u7fc5 %s\uff5c\u4f4d\u7f6e %d %d %d \u2192 \u8fd9\u6bb5\u65f6\u95f4\u53f3\u952e\u653e\u70df\u82b1\u662f\u6ca1\u53cd\u5e94\u7684\uff08\u539f\u7248\u89c4\u5219\uff1a\u53ea\u6709\u6ed1\u7fd4\u4e2d\u624d\u80fd\u653e\u70df\u82b1\uff09\uff0c\u6b63\u5728\u4e00\u76f4\u6309\u8df3\u5c1d\u8bd5\u91cd\u65b0\u5c55\u5f00", (double)this.notGlidingAirTicks / 20.0, hasElytra ? "\u5728" : "\u6ca1\u6709", this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ());
            }
            if (this.airJumpHold > 0) {
                PlayerAction.pressJump(false);
                this.airJumpHold = 0;
                this.airborneTicks = 0;
            } else if (++this.airborneTicks > 2) {
                PlayerAction.pressJump(true);
                this.airJumpHold = 1;
            }
            return true;
        }
        this.notGlidingAirTicks = 0;
        this.airborneTicks = 0;
        if (this.jumpSeq != 0) {
            this.jumpSeqTick();
            return true;
        }
        boolean bl = stationary = Math.abs(this.mc.player.getVelocity().x) < 0.01 && Math.abs(this.mc.player.getVelocity().z) < 0.01;
        if (!stationary) {
            return true;
        }
        if (((Boolean)this.clearHeadBlock.get()).booleanValue() && !AutoElytraFlight.blockingBlocks(1).isEmpty()) {
            this.beginClearHead();
            return true;
        }
        if (this.takeoffDelayTicks > 0) {
            --this.takeoffDelayTicks;
            return true;
        }
        if (this.shaftAlignTick()) {
            return true;
        }
        if (this.waitForBaritoneTakeoff()) {
            return true;
        }
        if (this.tickCounter - this.jumpAttemptTick > 100) {
            this.jumpAttempts = 0;
        }
        this.jumpAttemptTick = this.tickCounter;
        if (this.jumpAttempts < (Integer)this.jumpBeforeOpen.get()) {
            ++this.jumpAttempts;
            FOElytraLog.info("\u81ea\u52a8\u8d77\u8df3\uff1a\u7b2c %d/%d \u6b21\uff08%s\uff09", this.jumpAttempts, this.jumpBeforeOpen.get(), this.state == State.FLYING ? "\u98de\u884c\u4e2d\u91cd\u65b0\u8d77\u8df3\uff08\u843d\u5730\u4e86\uff09" : "\u8d77\u98de\u9636\u6bb5");
            this.startJumpSequence();
        } else if (((Boolean)this.openAreaSearch.get()).booleanValue() && this.openTriesDone < (Integer)this.openTries.get()) {
            this.beginFlyToOpen();
        } else {
            this.takeoffFallbackOrFail("\u843d\u5730\u540e\u539f\u5730\u8d77\u8df3 " + String.valueOf(this.jumpBeforeOpen.get()) + " \u6b21\u6ca1\u80fd\u8d77\u98de");
        }
        return true;
    }

    private void manualLaunchTick() {
        if (this.mc.player.isGliding()) {
            if (++this.glidingLostTicks % 100 == 0) {
                FOElytraLog.info("\u5df2\u5728\u6ed1\u7fd4\uff0c\u7b49 Baritone \u63a5\u7ba1\u98de\u884c\u2026", new Object[0]);
            }
            return;
        }
        this.glidingLostTicks = 0;
        if (this.takeoffTicks % 200 == 0) {
            FOElytraLog.warn("\u300c\u81ea\u52a8\u8d77\u98de\u300d\u5df2\u5173\uff1a\u8bf7\u81ea\u5df1\u8d77\u8df3\u4e24\u4e0b\u5c55\u5f00\u9798\u7fc5\uff0c\u6a21\u5757\u4f1a\u5728\u4f60\u8d77\u98de\u540e\u63a5\u7ba1\uff08\u5df2\u7b49 %d \u79d2\uff09", this.takeoffTicks / 20);
        }
    }

    private void launchTick() {
        boolean moving;
        if (this.fakeGlideTick()) {
            return;
        }
        if ("\u8d77\u98de\u91cd\u8bd5".equals(this.takeoffFlowOwner)) {
            this.claimTakeoffFlow("\u8d77\u98de\u91cd\u8bd5");
        }
        if (this.mc.player.isGliding()) {
            PlayerAction.pressJump(false);
            this.jumpSeq = 0;
            if (this.glidingLostTicks == 0) {
                this.replanIfLost(true);
            }
            if (++this.glidingLostTicks > 15) {
                this.glidingLostTicks = 0;
                if (((Boolean)this.openAreaSearch.get()).booleanValue() && this.openTriesDone < (Integer)this.openTries.get()) {
                    this.beginFlyToOpen();
                } else {
                    this.takeoffFallbackOrFail("\u5df2\u7ecf\u5728\u6ed1\u7fd4\uff0c\u4f46 Baritone 15 tick \u540e\u4ecd\u672a\u63a5\u7ba1");
                }
            }
            return;
        }
        if (!this.mc.player.isOnGround()) {
            ++this.fallTicks;
            if (this.airJumpHold > 0) {
                PlayerAction.pressJump(false);
                this.airJumpHold = 0;
                this.fallTicks = 0;
            } else if (this.fallTicks > 2) {
                PlayerAction.pressJump(true);
                this.airJumpHold = 1;
            }
            return;
        }
        this.fallTicks = 0;
        if (this.jumpSeq != 0) {
            this.jumpSeqTick();
            return;
        }
        boolean bl = moving = Math.abs(this.mc.player.getVelocity().x) >= 0.01 || Math.abs(this.mc.player.getVelocity().z) >= 0.01;
        if (moving) {
            ++this.launchWait;
            if (this.launchWait < 40) {
                return;
            }
            this.launchWait = 0;
        } else {
            this.launchWait = 0;
        }
        if (((Boolean)this.clearHeadBlock.get()).booleanValue() && !AutoElytraFlight.blockingBlocks(1).isEmpty()) {
            this.beginClearHead();
            return;
        }
        if (this.takeoffDelayTicks > 0) {
            --this.takeoffDelayTicks;
            return;
        }
        if (this.waitForBaritoneTakeoff()) {
            return;
        }
        if (this.jumpAttempts < (Integer)this.jumpBeforeOpen.get()) {
            ++this.jumpAttempts;
            FOElytraLog.info("\u81ea\u52a8\u8d77\u8df3\uff1a\u7b2c %d \u6b21\u5c1d\u8bd5\uff08\u6700\u591a %d \u6b21\uff0c\u4e4b\u540e\u6539\u7528\u300c\u98de\u5f80\u5f00\u9614\u5730\u300d\uff09", this.jumpAttempts, this.jumpBeforeOpen.get());
            this.startJumpSequence();
            return;
        }
        if (((Boolean)this.openAreaSearch.get()).booleanValue() && this.openTriesDone < (Integer)this.openTries.get()) {
            this.beginFlyToOpen();
            return;
        }
        this.takeoffFallbackOrFail(this.jumpAttempts + " \u6b21\u539f\u5730\u8d77\u8df3\u90fd\u6ca1\u80fd\u5c55\u5f00\u9798\u7fc5");
    }

    private boolean fakeGlideTick() {
        return false;
    }

    private boolean beginFakeGlideRecovery() {
        if (this.takeoffFlowBusy("\u5047\u6ed1\u7fd4\u6062\u590d")) {
            FOElytraLog.detail("\u5047\u6ed1\u7fd4\u5148\u8bb0\u7740\uff08%s \u6b63\u5728\u8fdb\u884c\uff0c\u8fd8\u5269 %d tick\uff09\u2192 \u7b49\u5b83\u7ed3\u675f\u518d\u6062\u590d", this.takeoffFlowOwner, this.takeoffFlowHoldTicks);
            return true;
        }
        if (this.fakeGlideRecovers >= 3) {
            int head = AutoElytraFlight.blockingBlocks(1).size();
            String where = this.mc.player.getBlockX() + " " + this.mc.player.getBlockY() + " " + this.mc.player.getBlockZ();
            FOElytraLog.warn("\u8fde\u7eed %d \u6b21\u5047\u6ed1\u7fd4\u90fd\u6ca1\u79bb\u5730\uff08\u4f4d\u7f6e %s\uff0c\u5934\u9876\u6321 %d \u683c\uff09\u2192 \u505c\u624b\uff1a\u53ef\u80fd\u662f\u5730\u5f62/\u670d\u52a1\u7aef\u72b6\u6001\u95ee\u9898\uff0c\u8bf7\u624b\u52a8\u8d77\u8df3\u6216\u6362\u4e2a\u5730\u65b9", 3, where, head);
            this.releaseTakeoffFlow();
            this.failNoLogout("\u5047\u6ed1\u7fd4\u6062\u590d 3 \u6b21\u90fd\u6ca1\u79bb\u5730\uff08\u4f4d\u7f6e " + where + "\uff0c\u5934\u9876\u6321 " + head + " \u683c\uff09");
            return true;
        }
        ++this.fakeGlideRecovers;
        this.fakeGlideWindow.reset();
        this.fakeGlidePhase = 1;
        this.fakeGlidePhaseTicks = 0;
        PlayerAction.pressJump(false);
        PlayerAction.releaseAll();
        this.mc.player.stopGliding();
        this.jumpSeq = 0;
        this.jumpPhase = 0;
        this.claimTakeoffFlow("\u5047\u6ed1\u7fd4\u6062\u590d");
        FOElytraLog.warn("\u5047\u6ed1\u7fd4\u786e\u8ba4\uff08\u8fde\u7eed %d tick \u5ba2\u6237\u7aef\u8bf4\u5728\u6ed1\u7fd4\u3001\u4eba\u5374\u5728\u5730\u4e0a\uff09\u2192 \u7b2c %d/%d \u6b21\u6700\u5c0f\u6062\u590d\uff1a\u677e\u952e \u2192 \u7b49 %d tick \u2192 \u53ea\u6309\u4e00\u6b21\u8df3 \u2192 \u518d\u770b %d tick \u662f\u5426\u79bb\u5730", 20, this.fakeGlideRecovers, 3, 5, 15);
        return true;
    }

    private void fakeGlideRecoveryTick() {
        ++this.fakeGlidePhaseTicks;
        if (this.fakeGlidePhase == 1) {
            if (this.fakeGlidePhaseTicks < 5) {
                return;
            }
            this.fakeGlidePhase = 2;
            this.fakeGlidePhaseTicks = 0;
            PlayerAction.pressJump(true);
            FOElytraLog.detail("\u5047\u6ed1\u7fd4\u6062\u590d\uff1a\u6309\u4e00\u6b21\u8df3\uff08\u7b2c %d/%d \u6b21\uff09", this.fakeGlideRecovers, 3);
            return;
        }
        if (this.fakeGlidePhase == 2) {
            if (this.fakeGlidePhaseTicks < 2) {
                return;
            }
            PlayerAction.pressJump(false);
            this.fakeGlidePhase = 3;
            this.fakeGlidePhaseTicks = 0;
            return;
        }
        if (this.mc.player != null && !this.mc.player.isOnGround()) {
            FOElytraLog.info("\u5047\u6ed1\u7fd4\u6062\u590d\u6210\u529f\uff1a\u5df2\u7ecf\u79bb\u5730\uff08\u7b2c %d/%d \u6b21\uff09\uff0c\u7ee7\u7eed\u8d77\u98de\u6d41\u7a0b", this.fakeGlideRecovers, 3);
            this.fakeGlideRecovered();
            return;
        }
        if (this.fakeGlidePhaseTicks < 15) {
            return;
        }
        FOElytraLog.warn("\u5047\u6ed1\u7fd4\u6062\u590d\u7b2c %d/%d \u6b21\u6ca1\u79bb\u5730\uff08\u4f4d\u7f6e %s\uff0c\u5934\u9876\u6321 %d \u683c\uff09", this.fakeGlideRecovers, 3, this.mc.player == null ? "\u672a\u77e5" : this.mc.player.getBlockX() + " " + this.mc.player.getBlockY() + " " + this.mc.player.getBlockZ(), this.mc.player == null ? 0 : AutoElytraFlight.blockingBlocks(1).size());
        this.fakeGlideWindow.reset();
        this.fakeGlidePhase = 0;
        this.fakeGlidePhaseTicks = 0;
    }

    private void fakeGlideRecovered() {
        this.fakeGlideWindow.reset();
        this.fakeGlidePhase = 0;
        this.fakeGlidePhaseTicks = 0;
        this.fakeGlideRecovers = 0;
    }

    private void resetFakeGlideRecovery() {
        this.fakeGlideWindow.reset();
        this.fakeGlidePhase = 0;
        this.fakeGlidePhaseTicks = 0;
        this.fakeGlideRecovers = 0;
        this.releaseTakeoffFlow();
    }

    private void claimTakeoffFlow(String who) {
        this.takeoffFlowOwner = who;
        this.takeoffFlowHoldTicks = 20;
    }

    private void releaseTakeoffFlow() {
        this.takeoffFlowOwner = "";
        this.takeoffFlowHoldTicks = 0;
    }

    private boolean takeoffFlowBusy(String who) {
        return this.takeoffFlowHoldTicks > 0 && !who.equals(this.takeoffFlowOwner);
    }

    private void startJumpSequence() {
        if (PlayerAction.clearStuckSneak()) {
            FOElytraLog.warn("\u68c0\u6d4b\u5230\u6b8b\u7559\u6f5c\u884c\uff08\u4f60\u6ca1\u6309 Shift\uff09\u2014\u2014\u5df2\u89e3\u9664\uff0c\u5426\u5219\u8df3\u4e0d\u8d77\u6765", new Object[0]);
        }
        this.jumpPhase = 1;
        this.jumpIteration = 0;
        if (this.mc.player != null) {
            this.mc.player.setPitch(-30.0f);
        }
        this.takeoffFireworkPending = (Boolean)this.takeoffFirework.get() != false ? 1 : 0;
        PlayerAction.pressJump(true);
        this.jumpSeq = 1;
        this.jumpSeqTicks = 0;
    }

    private void jumpSeqTick() {
        ++this.jumpSeqTicks;
        switch (this.jumpPhase) {
            case 1: {
                if (this.jumpIteration == 1) {
                    PlayerAction.pressJump(false);
                }
                this.jumpPhase = 2;
                break;
            }
            case 2: {
                if (this.mc.player.getVelocity().y < -0.1) {
                    this.jumpPhase = 3;
                    return;
                }
                ++this.jumpIteration;
                if (this.jumpIteration >= 8) {
                    this.jumpPhase = 3;
                    return;
                }
                this.jumpPhase = 1;
                break;
            }
            case 3: {
                PlayerAction.pressJump(true);
                this.jumpPhase = 4;
                break;
            }
            case 4: {
                this.jumpPhase = 5;
                break;
            }
            case 5: {
                PlayerAction.pressJump(false);
                this.jumpPhase = 0;
                this.jumpSeq = 0;
                this.jumpSeqTicks = 0;
                break;
            }
            default: {
                this.jumpPhase = 0;
                this.jumpSeq = 0;
                this.jumpSeqTicks = 0;
            }
        }
    }

    private static List<BlockPos> blockingBlocks(int checkY) {
        ArrayList<BlockPos> out = new ArrayList<BlockPos>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return out;
        }
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        Vec3d pos = player.getEntityPos();
        double headX = pos.x;
        double headZ = pos.z;
        double headY = pos.y + (double)player.getHeight();
        int aboveY = (int)Math.floor(headY) + 1;
        int baseX = (int)Math.floor(headX);
        int baseZ = (int)Math.floor(headZ);
        double offsetX = headX - (double)baseX;
        double offsetZ = headZ - (double)baseZ;
        LinkedHashSet<BlockPos> toCheck = new LinkedHashSet<BlockPos>();
        toCheck.add(new BlockPos(baseX, aboveY, baseZ));
        if (offsetX > 0.7) {
            toCheck.add(new BlockPos(baseX + 1, aboveY, baseZ));
        } else if (offsetX < 0.3) {
            toCheck.add(new BlockPos(baseX - 1, aboveY, baseZ));
        }
        if (offsetZ > 0.7) {
            toCheck.add(new BlockPos(baseX, aboveY, baseZ + 1));
        } else if (offsetZ < 0.3) {
            toCheck.add(new BlockPos(baseX, aboveY, baseZ - 1));
        }
        if (offsetX > 0.7 && offsetZ > 0.7) {
            toCheck.add(new BlockPos(baseX + 1, aboveY, baseZ + 1));
        } else if (offsetX > 0.7 && offsetZ < 0.3) {
            toCheck.add(new BlockPos(baseX + 1, aboveY, baseZ - 1));
        } else if (offsetX < 0.3 && offsetZ > 0.7) {
            toCheck.add(new BlockPos(baseX - 1, aboveY, baseZ + 1));
        } else if (offsetX < 0.3 && offsetZ < 0.3) {
            toCheck.add(new BlockPos(baseX - 1, aboveY, baseZ - 1));
        }
        for (BlockPos pos0 : toCheck) {
            BlockPos q;
            int i;
            for (i = 0; i < checkY; ++i) {
                q = pos0.add(0, i, 0);
                if (!AutoElytraFlight.chunkLoaded((World)world, q) || world.getBlockState(q).isAir()) continue;
                out.add(pos0);
            }
            for (i = checkY; i < 0; ++i) {
                q = pos0.add(0, i, 0);
                if (!AutoElytraFlight.chunkLoaded((World)world, q) || world.getBlockState(q).isAir()) continue;
                out.add(pos0);
            }
        }
        return out;
    }

    private static boolean chunkLoaded(World world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private void beginClearHead() {
        BounceProbe.mark("\u5934\u9876\u88ab\u6321");
        List<BlockPos> bp = AutoElytraFlight.blockingBlocks(1);
        if (bp.isEmpty()) {
            return;
        }
        FOElytraLog.warn("\u5934\u9876\u6709\u65b9\u5757\u963b\u6321\uff0c\u8ba9 Baritone \u5148\u6316\u5f00\uff08\u8bbe\u8ba1\u884c\u4e3a\uff09", new Object[0]);
        Vec3d pos = this.mc.player.getEntityPos();
        BaritoneHook.stop();
        BaritoneHook.builderClearArea(new BlockPos((int)Math.floor(pos.x - 0.3), bp.get(0).getY(), (int)Math.floor(pos.z - 0.3)), new BlockPos((int)Math.floor(pos.x - 0.3) + 1, bp.get(0).getY() + 1, (int)Math.floor(pos.z - 0.3) + 1));
        this.clearingHead = true;
        this.clearWaited = 0;
        this.jumpAttempts = 0;
        this.takeoffPhase = TakeoffPhase.CLEAR_HEAD;
    }

    private void clearHeadTick() {
        boolean digging;
        ++this.clearWaited;
        List<BlockPos> left = AutoElytraFlight.blockingBlocks(1);
        boolean bl = digging = this.clearingHead && this.clearWaited <= 200 && !left.isEmpty() && BaritoneHook.builderActive();
        if (digging) {
            return;
        }
        this.clearingHead = false;
        if (left.isEmpty()) {
            FOElytraLog.info("\u5934\u9876\u969c\u788d\u6e05\u9664\u5b8c\u6bd5\uff08\u7528\u4e86 %d tick\uff09\uff0c\u7acb\u523b\u8d77\u8df3", this.clearWaited);
            if (this.segmentTarget != null) {
                this.requeuePath("\u6e05\u969c\u540e\u8d77\u98de", this.segmentTarget.getX(), this.segmentTarget.getZ());
            }
        } else {
            FOElytraLog.warn("\u5934\u9876\u8fd8\u662f\u88ab\u6321\uff08\u6316\u4e86 %d tick\uff09\uff0c\u7167\u6837\u8d77\u8df3\uff08\u8bbe\u8ba1\u4e0a\u4e5f\u662f\u8fd9\u6837\uff09", this.clearWaited);
        }
        this.takeoffDelayTicks = 0;
        this.takeoffPhase = TakeoffPhase.LAUNCH;
    }

    private void beginFlyToOpen() {
        ++this.openTriesDone;
        this.openEnd = null;
        this.openSearchFails = 0;
        this.ascendingSearch = false;
        this.searchYh = 0.0;
        PlayerAction.pressJump(false);
        this.jumpSeq = 0;
        this.jumpAttempts = 0;
        this.takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
        FOElytraLog.warn("\u5c1d\u8bd5\u98de\u5f80\u5f00\u9614\u5730\u5e26\uff08\u7b2c %d/%d \u6b21\uff1a\u5148\u539f\u5730\u8d77\u8df3\uff0c\u518d\u627e\u4e00\u6761\u5f00\u9614\u822a\u7ebf\uff09", this.openTriesDone, this.openTries.get());
    }

    /*
     * Enabled aggressive block sorting
     */
    private void flyToOpenTick() {
        if (this.mc.player.isInLava()) {
            this.openLavaWindow.accumulate(this.tickCounter);
        }
        if (this.openLavaWindow.getCount(this.tickCounter) > 15) {
            this.openLavaWindow.reset();
            this.takeoffFallbackOrFail("\u98de\u5f80\u5f00\u9614\u5730\u7684\u8def\u4e0a\u6301\u7eed\u6ce1\u5728\u5ca9\u6d46\u91cc\uff0820 tick \u7a97\u53e3\u91cc\u8d85\u8fc7 15 tick\uff09");
            return;
        }
        if (!this.mc.player.isGliding()) {
            if (this.jumpSeq != 0) {
                this.jumpSeqTick();
                return;
            }
            if (this.mc.player.isOnGround()) {
                if (Math.abs(this.mc.player.getVelocity().x) < 0.01 && Math.abs(this.mc.player.getVelocity().z) < 0.01) {
                    this.startJumpSequence();
                }
                return;
            }
            ++this.fallTicks;
            if (this.mc.player.getVelocity().y < -0.1 && this.fallTicks > 2) {
                this.fallTicks = 0;
                this.startJumpSequence();
            }
            return;
        }
        this.replanIfLost(false);
        double yr = this.mc.player.isOnGround() ? 1.2 : 1.7;
        FindPathToOpen.Takeoff t = null;
        double usedYh = 0.0;
        if (!this.ascendingSearch && (t = FindPathToOpen.getTakeoffDirection((Double)this.openSearchDist.get(), (Double)this.openSafeDist.get(), yr, 0.0)) == null && ((Boolean)this.allowAscend.get()).booleanValue()) {
            this.ascendingSearch = true;
            this.searchYh = 3.0;
            return;
        }
        if (t == null && this.ascendingSearch) {
            if (this.searchYh < 11.0) {
                t = FindPathToOpen.getTakeoffDirection((Double)this.openSearchDist.get(), (Double)this.openSafeDist.get(), 1.7, this.searchYh);
                if (t == null) {
                    this.searchYh += 0.5;
                    return;
                }
                usedYh = this.searchYh;
            } else {
                this.ascendingSearch = false;
            }
        }
        if (t != null) {
            this.ascendingSearch = false;
            this.searchYh = 0.0;
        }
        if (t != null) {
            this.openSearchFails = 0;
            this.takeoffFallbackOrFail("\u98de\u5f80\u5f00\u9614\u5730\u9700\u8981\u6a21\u5757\u81ea\u5df1\u653e\u70df\u82b1\uff0c\u8fd9\u6761\u8def\u7ebf\u5df2\u6309\u4f60\u7684\u8981\u6c42\u505c\u7528");
            return;
        }
        ++this.openSearchFails;
        if (this.openSearchFails < 3) {
            return;
        }
        if (this.openTriesDone >= (Integer)this.openTries.get()) {
            this.takeoffFallbackOrFail("\u56db\u5468\u5168\u88ab\u6321\u6b7b\uff0c\u627e\u4e0d\u5230\u4efb\u4f55\u53ef\u7528\u7684\u8d77\u98de\u822a\u7ebf\uff08\u5df2\u8bd5 " + this.openTriesDone + " \u6b21\uff09");
            return;
        }
        this.beginFlyToOpen();
    }

    private void ascendTick() {
        if (!this.mc.player.isGliding()) {
            this.takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
            return;
        }
        if (this.mc.player.getY() > this.ascendTargetY) {
            ++this.ascendTicks;
            if (this.ascendTicks > 40) {
                this.ascendTicks = 0;
                if (this.openTriesDone >= (Integer)this.openTries.get()) {
                    this.takeoffFallbackOrFail("\u5782\u76f4\u722c\u5347\u4e4b\u540e\u4ecd\u7136\u627e\u4e0d\u5230\u822a\u7ebf");
                } else {
                    this.beginFlyToOpen();
                }
            }
            return;
        }
        ++this.ascendTicks;
        if (this.ascendTicks > 20) {
            this.ascendTicks = 0;
            this.takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
        }
    }

    private void waitArriveTick() {
        if (this.openEnd == null) {
            this.takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
            return;
        }
        if (!this.mc.player.isGliding()) {
            if (((Boolean)this.clearHeadBlock.get()).booleanValue() && !AutoElytraFlight.blockingBlocks(1).isEmpty()) {
                this.beginClearHead();
            } else {
                this.takeoffPhase = TakeoffPhase.LAUNCH;
            }
            return;
        }
        this.replanIfLost(false);
        boolean arrived = this.mc.player.getBlockPos().isWithinDistance((Vec3i)this.openEnd, 1.5);
        if (!arrived) {
            Vec3d toEnd = Vec3d.ofCenter((Vec3i)this.openEnd).subtract(this.mc.player.getEntityPos());
            boolean bl = arrived = toEnd.dotProduct(this.mc.player.getVelocity()) < 0.0;
        }
        if (!arrived) {
            return;
        }
        this.openEnd = null;
        if (this.openTriesDone >= (Integer)this.openTries.get()) {
            this.takeoffFallbackOrFail("\u6765\u56de\u51b2\u4e86 " + this.openTriesDone + " \u6b21\u5f00\u9614\u5730\uff0cBaritone \u4ecd\u672a\u63a5\u7ba1");
            return;
        }
        this.beginFlyToOpen();
    }

    private void takeoffFallbackOrFail(String reason) {
        BounceProbe.mark("\u8d77\u98de\u5931\u8d25\uff1a" + reason);
        if (this.takeoffFlowBusy("\u8d77\u98de\u515c\u5e95")) {
            FOElytraLog.detail("\u8d77\u98de\u515c\u5e95\u6682\u7f13\uff08%s \u6b63\u5728\u8fdb\u884c\uff0c\u8fd8\u5269 %d tick\uff09\u2192 \u8fd9\u6b21\u4e0d\u52a8\u8d77\u8df3\u6d41\u7a0b", this.takeoffFlowOwner, this.takeoffFlowHoldTicks);
            return;
        }
        PlayerAction.pressJump(false);
        this.jumpSeq = 0;
        if (!((Boolean)this.openAreaSearch.get()).booleanValue() && this.takeoffReArmCount < 5) {
            ++this.takeoffReArmCount;
            this.jumpAttempts = 0;
            this.takeoffTicks = 0;
            this.takeoffPhase = TakeoffPhase.LAUNCH;
            this.takeoffDelayTicks = 0;
            this.claimTakeoffFlow("\u8d77\u98de\u91cd\u8bd5");
            FOElytraLog.warn("\u8d77\u98de\u91cd\u8bd5\uff08\u7b2c %d/5 \u8f6e\uff1a\u5df2\u5173\u95ed\u300c\u5f00\u9614\u5730\u641c\u7d22\u8d77\u98de\u300d\uff0c\u76f4\u63a5\u539f\u5730\u518d\u8d77\u8df3\uff09", this.takeoffReArmCount);
            return;
        }
        if (((Boolean)this.takeoffAutoJumpFallback.get()).booleanValue() && !this.takeoffAutoJumpUsed) {
            this.takeoffAutoJumpUsed = true;
            this.takeoffTicks = 0;
            this.jumpAttempts = 0;
            this.openTriesDone = 0;
            this.openEnd = null;
            this.clearingHead = false;
            this.ascendingSearch = false;
            this.searchYh = 0.0;
            this.openSearchFails = 0;
            this.takeoffPhase = TakeoffPhase.LAUNCH;
            BaritoneHook.btSet("elytraAutoJump", true);
            this.lastForcedAutoJump = true;
            this.claimTakeoffFlow("\u4ea4\u7ed9 Baritone");
            if (this.segmentTarget != null) {
                this.requeuePath("\u8d77\u98de\u5931\u8d25\u4ea4\u7ed9 Baritone", this.segmentTarget.getX(), this.segmentTarget.getZ());
            }
            FOElytraLog.warn("\u672c\u63d2\u4ef6\u7684\u8d77\u98de\u6d41\u7a0b\u5931\u8d25\uff08%s\uff5c\u6f5c\u884c %s\uff09\uff0c\u4e34\u65f6\u6253\u5f00 Baritone \u7684\u300c\u81ea\u52a8\u8d77\u8df3\u300d\u518d\u8bd5\u4e00\u6b21\uff08\u7528 F3 \u770b\u662f\u4e0d\u662f\u5934\u9876/\u811a\u4e0b\u88ab\u6321\uff09", reason, PlayerAction.sneakHeld() ? "\u662f" : "\u5426");
            return;
        }
        this.failNoLogout("\u8d77\u98de\u5931\u8d25\uff1a" + reason + "\uff08\u6f5c\u884c " + (PlayerAction.sneakHeld() ? "\u662f" : "\u5426") + "\uff1b\u53ef\u6253\u5f00\u300c\u8d77\u98de\u5931\u8d25\u4ea4\u7ed9 Baritone\u300d\uff0c\u6216\u81ea\u5df1\u8d77\u8df3\u540e\u518d\u5f00\u6a21\u5757\uff09");
    }

    private void replanIfLost(boolean force) {
        if (this.segmentTarget == null) {
            return;
        }
        if (this.lavaPredictor != null && this.lavaPredictor.isAvoiding()) {
            return;
        }
        if (BaritoneHook.isFlying()) {
            return;
        }
        if (!force && this.tickCounter % 40 != 0) {
            return;
        }
        this.requeuePath("\u6389\u7ebf\u91cd\u89c4\u5212", this.segmentTarget.getX(), this.segmentTarget.getZ());
    }

    private int fireworkHotbarSlot() {
        if (this.mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; ++i) {
            if (this.mc.player.getInventory().getStack(i).getItem() != Items.FIREWORK_ROCKET) continue;
            return i;
        }
        return -1;
    }

    private void startOpenAreaEscape() {
        if (this.segmentTarget == null) {
            this.state = State.PREPARE;
            return;
        }
        this.segFailWindow.reset();
        this.spinWindow.reset();
        this.takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
        this.forceFlyToOpen = true;
        this.flightSettingsApplied = false;
        this.takeoffTicks = 0;
        this.openTriesDone = 0;
        this.openEnd = null;
        this.state = State.TAKEOFF;
    }

    private void resetTerrainState() {
        this.landSkippedThreat = 0;
        this.landSkippedBasalt = 0;
        this.basaltSkipHandled = false;
        this.basaltBiomeUnknown = false;
        this.lastHurtHealth = -1.0;
        this.torchPlacedThisLanding = false;
    }

    private boolean inNether() {
        try {
            return this.mc.world != null && this.mc.world.getRegistryKey() == World.NETHER;
        }
        catch (Throwable t) {
            return false;
        }
    }

    private boolean allowReplan(String reason) {
        int since;
        if (!AutoElytraFlight.takeoffCriticalReplan(reason) && (since = Math.max(0, this.tickCounter - this.lastReplanTick)) < 60) {
            FOElytraLog.detail("\u91cd\u89c4\u5212\uff1a\u539f\u56e0 %s\uff5c\u8ddd\u4e0a\u6b21 %.1f \u79d2\uff08\u6700\u5c11 3 \u79d2\uff09\u2192 \u8df3\u8fc7\u8fd9\u6b21\u4e0b\u53d1", reason, (double)since / 20.0);
            return false;
        }
        this.lastReplanTick = this.tickCounter;
        this.replanEver = true;
        return true;
    }

    private static boolean takeoffCriticalReplan(String reason) {
        return reason != null && reason.contains("\u8d77\u98de");
    }

    private boolean requeuePath(String reason, int x, int z) {
        BounceProbe.mark("\u91cd\u89c4\u5212 " + reason);
        if (!this.allowReplan(reason)) {
            return false;
        }
        return BaritoneHook.pathTo(x, z);
    }

    private boolean allowBariReset(String reason) {
        if (!this.inNether()) {
            this.lastResetTick = this.tickCounter;
            return true;
        }
        int since = Math.max(0, this.tickCounter - this.lastResetTick);
        if (since < 200) {
            FOElytraLog.detail("\u4e0b\u754c\u8282\u6d41\uff1a%s \u7684 Baritone \u91cd\u7f6e/\u91cd\u6253\u5305\u8df3\u8fc7\uff08\u8ddd\u4e0a\u6b21 %d \u79d2\uff0c\u6700\u5c11 %d \u79d2\uff09", reason, since / 20, 10);
            return false;
        }
        this.lastResetTick = this.tickCounter;
        FOElytraLog.detail("\u4e0b\u754c\u5141\u8bb8\u4e00\u6b21 Baritone \u91cd\u7f6e\uff1a%s\uff08\u8ddd\u4e0a\u6b21 %d \u79d2\uff09", reason, since / 20);
        return true;
    }

    private void btSafetyTick() {
        if (this.tickCounter % 40 != 0) {
            return;
        }
        if (!((Boolean)this.btSafetyTakeover.get()).booleanValue()) {
            this.restoreBtSafety();
            return;
        }
        boolean ourChanged = false;
        try {
            Double d;
            Object object;
            Settings.Setting<?> avoid = BaritoneHook.btSetting("elytraMinimumAvoidance");
            double want = Math.max(0.2, Math.min(2.0, (Double)this.btAvoidMargin.get()));
            if (avoid != null && (object = avoid.value) instanceof Double && Math.abs((d = (Double)object) - want) > 0.001) {
                if (this.lastAppliedAvoid != null && Math.abs(d - this.lastAppliedAvoid) > 0.001) {
                    this.btAvoidMargin.set(d);
                    FOElytraLog.info("\u4f60\u5728 Baritone \u91cc\u628a\u907f\u8ba9\u4f59\u91cf\u6539\u6210 %.2f \u2192 \u4ee5\u4f60\u7684\u4e3a\u51c6\uff0c\u5df2\u540c\u6b65\u8fdb\u672c\u6a21\u5757\uff08\u4e0d\u518d\u5199\u56de\u53bb\uff09", d);
                } else {
                    ourChanged = true;
                }
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("btSafetyTick(\u907f\u8ba9\u4f59\u91cf)", t);
        }
        try {
            Integer i;
            Object object;
            Settings.Setting<?> look = BaritoneHook.btSetting("elytraSimulationTicks");
            int want = Math.max(20, Math.min(60, (Integer)this.btLookahead.get()));
            if (look != null && (object = look.value) instanceof Integer && (i = (Integer)object) != want) {
                if (this.lastAppliedLook != null && i != this.lastAppliedLook) {
                    this.btLookahead.set(i);
                    FOElytraLog.info("\u4f60\u5728 Baritone \u91cc\u628a\u524d\u77bb tick \u6539\u6210 %d \u2192 \u4ee5\u4f60\u7684\u4e3a\u51c6\uff0c\u5df2\u540c\u6b65\u8fdb\u672c\u6a21\u5757\uff08\u4e0d\u518d\u5199\u56de\u53bb\uff09", i);
                } else {
                    ourChanged = true;
                }
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("btSafetyTick(\u524d\u77bb tick)", t);
        }
        if (ourChanged) {
            FOElytraLog.detail("Baritone \u5b89\u5168\u53c2\u6570\u548c\u672c\u6a21\u5757\u8bbe\u7f6e\u4e0d\u4e00\u81f4\uff1a\u6309\u5f53\u524d\u8bbe\u7f6e\u5199\u5165\u4e00\u6b21", new Object[0]);
            this.applyBtSafety();
        }
    }

    private void applyBtSafety() {
        Object object;
        if (!((Boolean)this.btSafetyTakeover.get()).booleanValue()) {
            this.restoreBtSafety();
            return;
        }
        try {
            Settings.Setting<?> avoid = BaritoneHook.btSetting("elytraMinimumAvoidance");
            if (avoid == null) {
                FOElytraLog.detail("\u62ff\u4e0d\u5230 Baritone \u7684 elytraMinimumAvoidance\uff0c\u8df3\u8fc7\u907f\u8ba9\u4f59\u91cf\u63a5\u7ba1", new Object[0]);
            } else {
                double want;
                if (this.prevBtAvoid == null && (object = avoid.value) instanceof Double) {
                    Double d;
                    this.prevBtAvoid = d = (Double)object;
                }
                if (BaritoneHook.btSet("elytraMinimumAvoidance", want = Math.max(0.2, Math.min(2.0, (Double)this.btAvoidMargin.get())))) {
                    this.lastAppliedAvoid = want;
                    FOElytraLog.info("\u5df2\u63a5\u7ba1 Baritone \u907f\u8ba9\u4f59\u91cf\uff1a%.2f\uff08\u539f\u6765\u662f %s\uff09\uff0c\u5173\u6a21\u5757\u65f6\u8fd8\u539f", want, this.prevBtAvoid == null ? "\u672a\u77e5" : String.format("%.2f", this.prevBtAvoid));
                } else {
                    FOElytraLog.warn("\u5199\u5165 elytraMinimumAvoidance \u5931\u8d25\uff0c\u4fdd\u6301 Baritone \u539f\u8bbe\u7f6e", new Object[0]);
                }
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("applyBtSafety(\u907f\u8ba9\u4f59\u91cf)", t);
        }
        try {
            Settings.Setting<?> look = BaritoneHook.btSetting("elytraSimulationTicks");
            if (look == null) {
                FOElytraLog.detail("\u62ff\u4e0d\u5230 Baritone \u7684 elytraSimulationTicks\uff0c\u8df3\u8fc7\u524d\u77bb\u63a5\u7ba1", new Object[0]);
            } else {
                int want;
                if (this.prevBtLookahead == null && (object = look.value) instanceof Integer) {
                    Integer i;
                    this.prevBtLookahead = i = (Integer)object;
                }
                if (BaritoneHook.btSet("elytraSimulationTicks", want = Math.max(20, Math.min(60, (Integer)this.btLookahead.get())))) {
                    this.lastAppliedLook = want;
                    FOElytraLog.info("\u5df2\u63a5\u7ba1 Baritone \u524d\u77bb tick\uff1a%d\uff08\u539f\u6765\u662f %s\uff09\uff0c\u5173\u6a21\u5757\u65f6\u8fd8\u539f", want, this.prevBtLookahead == null ? "\u672a\u77e5" : String.valueOf(this.prevBtLookahead));
                } else {
                    FOElytraLog.warn("\u5199\u5165 elytraSimulationTicks \u5931\u8d25\uff0c\u4fdd\u6301 Baritone \u539f\u8bbe\u7f6e", new Object[0]);
                }
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("applyBtSafety(\u524d\u77bb tick)", t);
        }
        this.btSafetySaved = this.prevBtAvoid != null || this.prevBtLookahead != null;
    }

    private void restoreAutoJumpIfOurs() {
        Boolean cur = null;
        try {
            Object object;
            Settings.Setting<?> s = BaritoneHook.btSetting("elytraAutoJump");
            if (s != null && (object = s.value) instanceof Boolean) {
                Boolean b;
                cur = b = (Boolean)object;
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("restoreAutoJumpIfOurs", t);
        }
        if (cur != null && this.lastForcedAutoJump != null && !cur.equals(this.lastForcedAutoJump)) {
            FOElytraLog.info("elytraAutoJump \u5728 Baritone \u91cc\u53c8\u88ab\u6539\u6210 %s\uff08\u4e0d\u662f\u672c\u6a21\u5757\u5199\u7684\uff09\u2192 \u4e0d\u8fd8\u539f\uff0c\u4fdd\u6301\u4f60\u7684\u503c", cur);
            this.lastForcedAutoJump = null;
            return;
        }
        this.lastForcedAutoJump = null;
        BaritoneHook.btSet("elytraAutoJump", this.btAutoJump.get());
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private void restoreBtSafety() {
        block16: {
            Object object;
            Settings.Setting<?> cur;
            block15: {
                if (!this.btSafetySaved) {
                    return;
                }
                if (this.prevBtAvoid != null) {
                    try {
                        cur = BaritoneHook.btSetting("elytraMinimumAvoidance");
                        if (cur != null && (object = cur.value) instanceof Double) {
                            Double d = (Double)object;
                            if (this.lastAppliedAvoid != null && Math.abs(d - this.lastAppliedAvoid) > 0.001) {
                                FOElytraLog.info("\u907f\u8ba9\u4f59\u91cf\u5728 Baritone \u91cc\u53c8\u88ab\u6539\u6210 %.2f\uff08\u4e0d\u662f\u672c\u6a21\u5757\u5199\u7684\uff09\u2192 \u4e0d\u8fd8\u539f\uff0c\u4fdd\u6301\u4f60\u7684\u503c", d);
                                this.prevBtAvoid = null;
                                this.lastAppliedAvoid = null;
                                break block15;
                            }
                        }
                        if (BaritoneHook.btSet("elytraMinimumAvoidance", this.prevBtAvoid)) {
                            FOElytraLog.info("\u5df2\u8fd8\u539f Baritone \u907f\u8ba9\u4f59\u91cf\uff08%.2f\uff09", this.prevBtAvoid);
                            this.prevBtAvoid = null;
                            this.lastAppliedAvoid = null;
                        } else {
                            FOElytraLog.warn("\u8fd8\u539f elytraMinimumAvoidance \u5931\u8d25\uff0cBaritone \u91cc\u53ef\u80fd\u8fd8\u662f\u63a5\u7ba1\u540e\u7684\u503c", new Object[0]);
                        }
                    }
                    catch (Throwable t) {
                        FOElytraLog.detailError("restoreBtSafety(\u907f\u8ba9\u4f59\u91cf)", t);
                    }
                }
            }
            if (this.prevBtLookahead != null) {
                try {
                    cur = BaritoneHook.btSetting("elytraSimulationTicks");
                    if (cur != null && (object = cur.value) instanceof Integer) {
                        Integer i = (Integer)object;
                        if (this.lastAppliedLook != null && i != this.lastAppliedLook) {
                            FOElytraLog.info("\u524d\u77bb tick \u5728 Baritone \u91cc\u53c8\u88ab\u6539\u6210 %d\uff08\u4e0d\u662f\u672c\u6a21\u5757\u5199\u7684\uff09\u2192 \u4e0d\u8fd8\u539f\uff0c\u4fdd\u6301\u4f60\u7684\u503c", i);
                            this.prevBtLookahead = null;
                            this.lastAppliedLook = null;
                            break block16;
                        }
                    }
                    if (BaritoneHook.btSet("elytraSimulationTicks", this.prevBtLookahead)) {
                        FOElytraLog.info("\u5df2\u8fd8\u539f Baritone \u524d\u77bb tick\uff08%d\uff09", this.prevBtLookahead);
                        this.prevBtLookahead = null;
                        this.lastAppliedLook = null;
                    } else {
                        FOElytraLog.warn("\u8fd8\u539f elytraSimulationTicks \u5931\u8d25\uff0cBaritone \u91cc\u53ef\u80fd\u8fd8\u662f\u63a5\u7ba1\u540e\u7684\u503c", new Object[0]);
                    }
                }
                catch (Throwable t) {
                    FOElytraLog.detailError("restoreBtSafety(\u524d\u77bb tick)", t);
                }
            }
        }
        this.btSafetySaved = this.prevBtAvoid != null || this.prevBtLookahead != null;
    }

    private void logSegmentStability(String where) {
        if (this.mobHitsInSegment == 0) {
            FOElytraLog.info("\u672c\u6bb5\u7a33\u5b9a\u6027\uff08%s\uff09\uff1a\u6ca1\u88ab\u602a\u6253", where);
        } else {
            FOElytraLog.info("\u672c\u6bb5\u7a33\u5b9a\u6027\uff08%s\uff09\uff1a\u88ab\u602a\u6253 %d \u6b21", where, this.mobHitsInSegment);
        }
    }

    private void resetSegmentStability() {
        this.mobHitsInSegment = 0;
    }

    private void applyNetherSeedPause() {
        if (!((Boolean)this.netherPredictOff.get()).booleanValue()) {
            this.restoreNetherSeed();
            return;
        }
        try {
            Settings.Setting<?> st = BaritoneHook.btSetting("elytraNetherSeed");
            if (st == null) {
                FOElytraLog.detail("\u62ff\u4e0d\u5230 Baritone \u7684 elytraNetherSeed\uff0c\u8df3\u8fc7\u5173\u95ed\u5730\u5f62\u9884\u6d4b", new Object[0]);
                return;
            }
            if (!this.netherSeedSaved) {
                this.prevNetherSeed = st.value;
                this.netherSeedSaved = true;
            }
            if (BaritoneHook.btSet("elytraNetherSeed", 0L)) {
                FOElytraLog.info("\u5df2\u628a Baritone \u7684 elytraNetherSeed \u7f6e 0\uff08\u5173\u95ed\u4e0b\u754c\u5730\u5f62\u9884\u6d4b\uff09\uff0c\u5173\u6a21\u5757\u65f6\u8fd8\u539f", new Object[0]);
            } else {
                FOElytraLog.warn("\u5199\u5165 elytraNetherSeed \u5931\u8d25\uff0c\u4fdd\u6301 Baritone \u539f\u8bbe\u7f6e", new Object[0]);
                this.restoreNetherSeed();
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("applyNetherSeedPause", t);
            this.restoreNetherSeed();
        }
    }

    private void restoreNetherSeed() {
        if (!this.netherSeedSaved) {
            return;
        }
        try {
            if (this.prevNetherSeed != null && BaritoneHook.btSet("elytraNetherSeed", this.prevNetherSeed)) {
                FOElytraLog.info("\u5df2\u8fd8\u539f Baritone \u7684 elytraNetherSeed\uff08%s\uff09", this.prevNetherSeed);
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("restoreNetherSeed", t);
        }
        finally {
            this.netherSeedSaved = false;
            this.prevNetherSeed = null;
        }
    }

    private int threatNear(double cx, double cy, double cz, double radius) {
        try {
            double scan = radius + 24.0;
            List list = this.mc.world.getEntitiesByClass(Entity.class, this.mc.player.getBoundingBox().expand(scan), e -> e != null && e.isAlive() && (e instanceof Monster || e instanceof HostileEntity) && AutoElytraFlight.horizontalDistanceSq(e.getX(), e.getZ(), cx, cz) <= radius * radius && Math.abs(e.getY() - cy) <= 8.0);
            return list.size();
        }
        catch (Throwable t) {
            return 0;
        }
    }

    private static double horizontalDistanceSq(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    private void abortLandingForSafety() {
        this.supplyTicks = 0;
        this.supplyErrorRetryCount = 0;
        this.manualTask = false;
        this.supplyCooldown = Math.max(this.supplyCooldown, (Integer)this.supplyRetryDelay.get());
        FOElytraLog.info("\u964d\u843d\u70b9\u4e0d\u5b89\u5168\uff0c\u53d6\u6d88\u8fd9\u6b21\u964d\u843d\uff0c\u7ee7\u7eed\u98de\uff08\u4e0b\u6b21\u518d\u8bd5\uff09", new Object[0]);
        if (this.beginRecover("\u964d\u843d\u70b9\u4e0d\u5b89\u5168", RecoverAfter.PREPARE)) {
            return;
        }
        if (this.supplyTask != null && this.supplyTask.isRunning()) {
            this.supplyTask.abort("\u964d\u843d\u70b9\u4e0d\u5b89\u5168");
        }
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.landingY = Double.NaN;
        this.landingTicks = 0;
        this.state = State.PREPARE;
    }

    private boolean pickLandingColumn() {
        double groundCheckY;
        double px = this.mc.player.getX();
        double py = this.mc.player.getY();
        double pz = this.mc.player.getZ();
        int groundY = this.landingSurfaceY(this.mc.player.getBlockX(), this.mc.player.getBlockZ());
        double d = groundCheckY = groundY == Integer.MIN_VALUE ? py : (double)groundY + 1.0;
        if (((Boolean)this.landSkipWhenCrowded.get()).booleanValue()) {
            int crowd = this.threatNear(px, groundCheckY, pz, 24.0);
            FOElytraLog.detail("\u964d\u843d\u5b89\u5168\u9884\u68c0\uff1a\u534a\u5f84 %d \u683c\u5185\u654c\u5bf9\u751f\u7269 %d \u53ea\uff08\u6309\u5730\u9762 Y=%.0f \u67e5\uff0c\u4e0d\u6309\u98de\u884c Y=%.0f\uff09\u3001\u8df3\u8fc7\u7ebf %d \u53ea \u2192 %s", 24, crowd, groundCheckY, py, 5, crowd >= 5 ? "\u8df3\u8fc7\u8fd9\u6b21\u8865\u7ed9" : "\u53ef\u4ee5\u7ee7\u7eed\u9009\u70b9");
            if (crowd >= 5) {
                FOElytraLog.warn("%d \u683c\u5185\u6709 %d \u53ea\u654c\u5bf9\u751f\u7269\uff08\u2265%d\uff09\uff1a\u6309\u8bbe\u7f6e\u8df3\u8fc7\u8fd9\u6b21\u8865\u7ed9\uff0c\u7ee7\u7eed\u98de", 24, crowd, 5);
                return false;
            }
        }
        if (this.supplyHurtNoLand > 0) {
            FOElytraLog.detail("\u521a\u5728\u8865\u7ed9\u91cc\u88ab\u6253\u8fc7\uff08\u8fd8\u5269 %d \u79d2\u51b7\u5374\uff09\uff0c\u8fd9\u6b21\u4e0d\u964d\u843d\u8865\u7ed9\uff0c\u7ee7\u7eed\u98de", this.supplyHurtNoLand / 20);
            return false;
        }
        int[][] offsets = new int[][]{{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}, {12, 12}, {-12, 12}, {12, -12}, {-12, -12}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}};
        this.landSkippedThreat = 0;
        this.landSkippedBasalt = 0;
        this.landSkippedUnsafe = 0;
        this.landSkippedAbove = 0;
        this.basaltBiomeUnknown = false;
        boolean noBasalt = (Boolean)this.noSupplyInBasaltDeltas.get();
        boolean playerInBasalt = noBasalt && this.isBasaltDeltasAt(this.mc.player.getBlockX(), this.mc.player.getBlockZ());
        for (int[] off : offsets) {
            int x = this.mc.player.getBlockX() + off[0];
            int z = this.mc.player.getBlockZ() + off[1];
            if (noBasalt && (playerInBasalt || this.isBasaltDeltasAt(x, z))) {
                ++this.landSkippedBasalt;
                FOElytraLog.detail("\u964d\u843d\u70b9 %d %d \u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\uff0c\u8df3\u8fc7\u8fd9\u6b21\u964d\u843d", x, z);
                continue;
            }
            int surface = this.landingSurfaceY(x, z);
            boolean loaded = surface != Integer.MIN_VALUE;
            double surfaceY = loaded ? (double)surface + 1.0 : py;
            StringBuilder note = new StringBuilder();
            int verdict = this.landColumnVerdict(x, z, surface, note);
            int threats = this.threatNear((double)x + 0.5, surfaceY, (double)z + 0.5, ((Integer)this.landSafeRadius.get()).intValue());
            note.append("\uff5c\u602a ").append(threats).append(" \u53ea");
            if (verdict == 1 || verdict == 2 || verdict == 3 || verdict == 4) {
                ++this.landSkippedUnsafe;
                FOElytraLog.detail("\u964d\u843d\u70b9 %d %d\uff08\u5730\u9762 Y=%.0f\uff09\u4e0d\u5b89\u5168\uff1a%s \u2192 \u7ed3\u8bba\uff1a\u8df3\u8fc7\uff08%s\uff09", x, z, surfaceY, note, this.verdictText(verdict));
                continue;
            }
            if (verdict == 5) {
                ++this.landSkippedAbove;
                FOElytraLog.detail("\u964d\u843d\u70b9 %d %d\uff08\u5730\u9762 Y=%.0f\uff09\u7ad9\u4e0d\u7a33\uff1a%s \u2192 \u7ed3\u8bba\uff1a\u8df3\u8fc7\uff08\u5730\u8868\u4e0a\u65b9 %d \u683c\u88ab\u5835\uff09", x, z, surfaceY, note, 3);
                continue;
            }
            if (((Boolean)this.landAvoidMobs.get()).booleanValue() && threats > 0) {
                ++this.landSkippedThreat;
                FOElytraLog.detail("\u964d\u843d\u70b9 %d %d\uff08\u5730\u9762 Y=%.0f\uff09\u534a\u5f84 %d \u683c\u5185\u6709 %d \u53ea\u654c\u5bf9\u751f\u7269\uff1a%s \u2192 \u7ed3\u8bba\uff1a\u6362\u4e2a\u70b9", x, z, surfaceY, this.landSafeRadius.get(), threats, note);
                continue;
            }
            this.landingTargetX = (double)x + 0.5;
            this.landingTargetZ = (double)z + 0.5;
            this.landingY = loaded ? (double)surface : Math.max(-64.0, py - 40.0);
            FOElytraLog.info("\u964d\u843d\u70b9\u9009\u5728 %d %d\uff08\u9644\u8fd1\u6709\u602a\u7684 %d \u4e2a\u3001\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\u7684 %d \u4e2a\uff09\uff0c\u5730\u9762 Y=%.0f", x, z, this.landSkippedThreat, this.landSkippedBasalt, this.landingY);
            FOElytraLog.detail("\u964d\u843d\u70b9 %d %d \u901a\u8fc7\u5b89\u5168\u68c0\u67e5\uff1a%s \u2192 \u7ed3\u8bba\uff1a\u5c31\u843d\u8fd9\u91cc", x, z, note);
            FOElytraLog.detail("\u964d\u843d\u70b9\u5224\u5b9a\uff1a\u5171 %d \u4e2a\u5019\u9009\u3001%d \u4e2a\u56e0\u4e3a\u4e0b\u65b9\u5ca9\u6d46/\u6c34/\u60ac\u7a7a\u88ab\u8df3\u8fc7\uff08\u5f80\u4e0b\u67e5 %d \u683c\uff09\u3001%d \u4e2a\u56e0\u4e3a\u5730\u8868\u4e0a\u65b9 %d \u683c\u88ab\u5835\u88ab\u8df3\u8fc7\u3001%d \u4e2a\u56e0\u4e3a\u6709\u602a\u88ab\u8df3\u8fc7\uff08\u534a\u5f84 %d \u683c\u3001\u6309\u6bcf\u4e2a\u5019\u9009\u81ea\u5df1\u7684\u5730\u9762 Y \u67e5\u3001\u5782\u76f4\u5bb9\u5dee %d \u683c\uff09\u3001%d \u4e2a\u56e0\u4e3a\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\u88ab\u8df3\u8fc7 \u2192 \u9009 (%d %d)\uff0c\u8be5\u70b9\u534a\u5f84\u5185\u654c\u5bf9\u751f\u7269 %d \u53ea", offsets.length, this.landSkippedUnsafe, 10, this.landSkippedAbove, 3, this.landSkippedThreat, this.landSafeRadius.get(), 8, this.landSkippedBasalt, x, z, threats);
            return true;
        }
        if (this.landSkippedBasalt >= offsets.length) {
            this.skipSupplyInBasaltDeltas(this.basaltBiomeUnknown ? "\u8bfb\u4e0d\u5230\u751f\u7269\u7fa4\u7cfb\uff0c\u4fdd\u5b88\u8df3\u8fc7" : (playerInBasalt ? "\u73a9\u5bb6\u6b63\u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32" : "\u5019\u9009\u964d\u843d\u70b9\u90fd\u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32"));
            this.basaltSkipHandled = true;
            return false;
        }
        FOElytraLog.warn("\u672c\u6b21\u6ca1\u6709\u5b89\u5168\u964d\u843d\u70b9\uff08\u5019\u9009 %d \u4e2a\u90fd\u88ab\u5ca9\u6d46/\u6c34/\u602a/\u5730\u5f62\u6392\u9664\uff09\u2192 \u7ee7\u7eed\u98de\uff0c\u627e\u4e0b\u4e00\u4e2a\u5b89\u5168\u70b9", offsets.length);
        FOElytraLog.detail("\u5019\u9009\u6392\u67e5\u6c47\u603b\uff1a\u4e0b\u65b9\u5ca9\u6d46/\u6c34/\u60ac\u7a7a %d \u4e2a\uff5c\u5730\u8868\u4e0a\u65b9\u88ab\u5835 %d \u4e2a\uff5c\u6709\u602a %d \u4e2a\uff5c\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32 %d \u4e2a\uff5c%d \u79d2\u5185\u4e0d\u518d\u8bd5\u7740\u964d\u843d", this.landSkippedUnsafe, this.landSkippedAbove, this.landSkippedThreat, this.landSkippedBasalt, 30);
        this.supplyCooldown = Math.max(this.supplyCooldown, 600);
        return false;
    }

    private int landColumnVerdict(int x, int z, int surface, StringBuilder note) {
        if (surface == Integer.MIN_VALUE) {
            note.append("\u4e0b\u65b9\uff1a\u6574\u5217\u6ca1\u627e\u5230\u5b9e\u5fc3\u5730\u9762\uff08\u6d1e\u7a74/\u865a\u7a7a\uff09");
            int aboveMissing = 0;
            for (int i = 0; i < 3; ++i) {
                if (!StuckEscape.blocked((World)this.mc.world, new BlockPos(x, this.mc.player.getBlockY() + i, z))) continue;
                ++aboveMissing;
            }
            note.append("\uff5c\u4e0a\u65b9 3 \u683c\uff08\u6309\u4f60\u5f53\u524d\u9ad8\u5ea6\uff09\uff1a").append(aboveMissing).append(" \u683c\u6709\u65b9\u5757");
            return 3;
        }
        int reason = 0;
        Object below = "\u5f80\u4e0b 10 \u683c\u90fd\u662f\u7a7a\u6c14";
        for (int i = 1; i <= 10; ++i) {
            BlockPos p = new BlockPos(x, surface - i, z);
            if (!this.mc.world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) {
                below = "\u4e0b\u65b9\u533a\u5757\u6ca1\u52a0\u8f7d";
                reason = 4;
                break;
            }
            BlockState st = this.mc.world.getBlockState(p);
            if (st.isOf(Blocks.LAVA)) {
                below = "\u5ca9\u6d46\uff08\u5f80\u4e0b\u7b2c " + i + " \u683c\uff09";
                reason = 1;
                break;
            }
            if (!st.getFluidState().isEmpty()) {
                below = "\u6c34\uff08\u5f80\u4e0b\u7b2c " + i + " \u683c\uff09";
                reason = 2;
                break;
            }
            if (st.getCollisionShape((BlockView)this.mc.world, p).isEmpty()) continue;
            below = "\u5b9e\u5fc3\uff08\u5f80\u4e0b\u7b2c " + i + " \u683c\uff09";
            break;
        }
        if (reason == 0 && ((String)below).startsWith("\u5f80\u4e0b")) {
            reason = 3;
        }
        StringBuilder above = new StringBuilder();
        boolean aboveBlocked = false;
        for (int i = 0; i < 3; ++i) {
            boolean blocked = StuckEscape.blocked((World)this.mc.world, new BlockPos(x, surface + i, z));
            if (blocked) {
                aboveBlocked = true;
            }
            if (i > 0) {
                above.append('/');
            }
            above.append(blocked ? "\u6709\u65b9\u5757" : "\u7a7a");
        }
        note.append("\u4e0b\u65b9\uff1a").append((String)below).append("\uff5c\u5730\u8868\u4e0a\u65b9 ").append(3).append(" \u683c\uff1a").append((CharSequence)above);
        if (reason == 0 && aboveBlocked) {
            reason = 5;
        }
        return reason;
    }

    private String verdictText(int verdict) {
        return switch (verdict) {
            case 1 -> "\u4e0b\u65b9\u6709\u5ca9\u6d46";
            case 2 -> "\u4e0b\u65b9\u6709\u6c34";
            case 3 -> "\u4e0b\u65b9\u6ca1\u6709\u5b9e\u5fc3\u5730\u9762\uff08\u60ac\u7a7a\uff09";
            case 4 -> "\u4e0b\u65b9\u533a\u5757\u6ca1\u52a0\u8f7d";
            case 5 -> "\u5730\u8868\u4e0a\u65b9\u88ab\u5835";
            default -> "\u6ca1\u95ee\u9898";
        };
    }

    private boolean landingColumnTurnedBad() {
        try {
            int surface;
            int tz;
            int y0 = (int)Math.floor(this.mc.player.getY());
            for (int i = 1; i <= 6; ++i) {
                BlockPos p = new BlockPos(this.mc.player.getBlockX(), y0 - i, this.mc.player.getBlockZ());
                if (!this.mc.world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) {
                    return false;
                }
                BlockState st = this.mc.world.getBlockState(p);
                if (st.isOf(Blocks.LAVA)) {
                    return true;
                }
                if (st.getFluidState().isEmpty()) continue;
                return true;
            }
            int tx = (int)Math.floor(this.landingTargetX);
            int verdict = this.landColumnVerdict(tx, tz = (int)Math.floor(this.landingTargetZ), surface = this.landingSurfaceY(tx, tz), new StringBuilder());
            return verdict == 1 || verdict == 2 || verdict == 3;
        }
        catch (Throwable t) {
            return false;
        }
    }

    private void abortLandingLavaWater(String why) {
        FOElytraLog.warn("\u964d\u843d\u4e2d\u6b62\uff1a\u4e0b\u65b9\u51fa\u73b0\u5ca9\u6d46/\u6c34\uff08%s\uff09\u2192 \u62c9\u8d77\u6765\u7ee7\u7eed\u98de\uff0c\u627e\u4e0b\u4e00\u4e2a\u5b89\u5168\u70b9\u518d\u964d", why);
        BaritoneHook.stop();
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        this.landingY = Double.NaN;
        this.landingTicks = 0;
        this.supplyCooldown = Math.max(this.supplyCooldown, 600);
        this.state = State.PREPARE;
    }

    private int landingSurfaceY(int x, int z) {
        try {
            if (!this.mc.world.isChunkLoaded(x >> 4, z >> 4)) {
                return Integer.MIN_VALUE;
            }
            int top = this.mc.world.getTopYInclusive();
            int bottom = this.mc.world.getBottomY();
            for (int y = top; y > bottom; --y) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState st = this.mc.world.getBlockState(p);
                if (st.isAir() || !st.getFluidState().isEmpty() || st.getCollisionShape((BlockView)this.mc.world, p).isEmpty()) continue;
                return y + 1;
            }
        }
        finally {
            return Integer.MIN_VALUE;
        }
    }

    private boolean waitForUserScreen(String stage) {
        if (!InvHelper.screenOpen()) {
            return false;
        }
        if (InvHelper.hasContainerOpen()) {
            FOElytraLog.detail("\u5173\u6389\u8865\u7ed9\u7559\u4e0b\u7684\u5bb9\u5668\u754c\u9762\uff08%s\uff09\uff0c\u7ee7\u7eed", stage);
            InvHelper.closeScreen();
            return false;
        }
        ++this.waitTicks;
        if (this.waitTicks == 1) {
            FOElytraLog.info("\u68c0\u6d4b\u5230\u4f60\u5f00\u7740\u754c\u9762\uff1a\u7b49\u4f60\u5173\u6389\u518d\u7ee7\u7eed\uff08%s\uff09", stage);
        } else if (this.waitTicks % 100 == 0) {
            FOElytraLog.warn("\u4ecd\u5728\u7b49\u4f60\u5173\u95ed\u754c\u9762\uff08%s\uff0c\u5df2\u7b49 %d \u79d2\uff09", stage, this.waitTicks / 20);
        }
        return true;
    }

    private boolean isBasaltDeltasAt(int x, int z) {
        if (!this.inNether() || this.mc.player == null) {
            return false;
        }
        try {
            return this.mc.world.getBiomeAccess().getBiome(new BlockPos(x, this.mc.player.getBlockY(), z)).getKey().map(key -> "minecraft:basalt_deltas".equals(key.getValue().toString())).orElse(false);
        }
        catch (Throwable t) {
            if (!this.basaltBiomeUnknown) {
                this.basaltBiomeUnknown = true;
                FOElytraLog.warn("\u8bfb\u4e0d\u5230\u751f\u7269\u7fa4\u7cfb\uff08%s\uff09\uff0c\u4fdd\u5b88\u6309\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\u5904\u7406\uff1a\u8fd9\u6b21\u4e0d\u964d\u843d\u8865\u7ed9", String.valueOf(t));
            }
            return true;
        }
    }

    private void skipSupplyInBasaltDeltas(String why) {
        boolean first = this.supplyCooldown < 600;
        this.landingY = Double.NaN;
        this.landingTicks = 0;
        this.manualTask = false;
        this.supplyTicks = 0;
        this.supplyErrorRetryCount = 0;
        this.supplyCooldown = Math.max(this.supplyCooldown, 600);
        if (first) {
            FOElytraLog.warn("\u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\uff0c\u8df3\u8fc7\u8fd9\u6b21\u8865\u7ed9\uff0c\u7ee7\u7eed\u98de\uff08%s\uff09", why);
            FOElytraLog.info("\u8fd9\u6b21\u8865\u7ed9 %d \u79d2\u5185\u4e0d\u518d\u8bd5\uff0c\u4e5f\u4e0d\u4f1a\u9a6c\u4e0a\u4e0b\u843d", 30);
        }
        if (this.beginRecover("\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\u4e0d\u8865\u7ed9", RecoverAfter.PREPARE)) {
            return;
        }
        if (this.supplyTask != null && this.supplyTask.isRunning()) {
            this.supplyTask.abort("\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\u4e0d\u8865\u7ed9");
        }
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.state = State.PREPARE;
    }

    private boolean beginRecover(String reason, RecoverAfter after) {
        if (this.supplyTask == null || !this.supplyTask.recoverNeeded()) {
            return false;
        }
        if (this.supplyTask.isRecovering()) {
            return true;
        }
        if (!this.supplyTask.beginRecover(reason)) {
            return false;
        }
        this.recoverArmed = true;
        this.recoverAfter = after;
        this.recoverWatchdog = 0;
        BaritoneHook.stop();
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.state = State.RECOVER;
        FOElytraLog.warn("\u5148\u5f52\u4f4d\uff08%s\uff09\uff1a\u628a\u653e\u4e0b\u7684\u6f5c\u5f71\u76d2/\u672b\u5f71\u7bb1\u6536\u56de\u6765\u518d\u7ee7\u7eed", reason);
        return true;
    }

    private void recoverTick() {
        if (this.supplyTask == null) {
            this.finishRecover();
            return;
        }
        if (InvHelper.screenOpen()) {
            PlayerAction.restoreHeldKeys();
        }
        this.supplyTask.tick();
        if (this.supplyTask.status() == TaskStatus.RUNNING) {
            ++this.recoverWatchdog;
            if (this.recoverWatchdog % 100 == 0) {
                FOElytraLog.info("\u5f52\u4f4d\u4e2d\uff08%d \u79d2\uff09\uff1a%s", this.recoverWatchdog / 20, this.supplyTask.progress());
            }
            if (this.recoverWatchdog <= 500) {
                return;
            }
            FOElytraLog.warn("\u5f52\u4f4d\u8d85\u65f6\uff08%d \u79d2\uff09\uff0c\u4e0d\u518d\u7b49\uff1a%s", 25, this.supplyTask.progress());
            this.supplyTask.abort("\u5f52\u4f4d\u8d85\u65f6");
        }
        this.finishRecover();
    }

    private void finishRecover() {
        this.recoverArmed = false;
        this.supplyTask = null;
        BaritoneHook.stop();
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        PlayerAction.clearStuckSneak();
        this.manualTask = false;
        this.supplyTicks = 0;
        this.landingY = Double.NaN;
        FOElytraLog.info("\u5f52\u4f4d\u6d41\u7a0b\u7ed3\u675f", new Object[0]);
        switch (this.recoverAfter.ordinal()) {
            case 1: {
                String reason = this.pendingFailReason == null ? "\u4efb\u52a1\u5931\u8d25" : this.pendingFailReason;
                this.pendingFailReason = null;
                this.recoveryJustRan = true;
                this.fail(reason);
                break;
            }
            case 2: {
                String message = this.pendingFinishMessage == null ? "\u4efb\u52a1\u7ed3\u675f" : this.pendingFinishMessage;
                this.pendingFinishMessage = null;
                this.recoveryJustRan = true;
                this.finish(message);
                break;
            }
            case 3: {
                this.state = State.IDLE;
                if (!this.isActive()) break;
                this.toggle();
                break;
            }
            case 0: {
                this.state = State.PREPARE;
            }
        }
    }

    private void startRecoverRunner() {
        if (this.recoverRunner != null) {
            return;
        }
        try {
            if (!this.isActive()) {
                RecoverRunner runner = new RecoverRunner();
                MeteorClient.EVENT_BUS.subscribe((Object)runner);
                this.recoverRunner = runner;
                FOElytraLog.detail("\u6a21\u5757\u5df2\u5173\u95ed\uff1a\u5f52\u4f4d\u6539\u7531\u72ec\u7acb\u8ba1\u65f6\u5668\u7ee7\u7eed\uff08\u62ff\u5230\u4e1c\u897f\u624d\u4f1a\u505c\uff09", new Object[0]);
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("startRecoverRunner", t);
        }
    }

    private void stopRecoverRunner() {
        Object runner = this.recoverRunner;
        this.recoverRunner = null;
        if (runner == null) {
            return;
        }
        try {
            MeteorClient.EVENT_BUS.unsubscribe(runner);
            FOElytraLog.detail("\u5f52\u4f4d\u8ba1\u65f6\u5668\u5df2\u505c", new Object[0]);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("stopRecoverRunner", t);
        }
    }

    private boolean supplyHurtTick() {
        float hp = this.mc.player.getHealth();
        if (!((Boolean)this.hurtAbortSupply.get()).booleanValue()) {
            this.lastHurtHealth = hp;
            return false;
        }
        double before = this.lastHurtHealth;
        boolean dropped = this.lastHurtHealth >= 0.0 && this.lastHurtHealth - (double)hp >= 2.0;
        boolean hurt = this.mc.player.hurtTime > 0;
        this.lastHurtHealth = hp;
        if (!hurt && !dropped) {
            return false;
        }
        ++this.mobHitsInSegment;
        BounceProbe.mark("\u8865\u7ed9\u4e2d\u88ab\u653b\u51fb");
        FOElytraLog.warn("\u8865\u7ed9\u4e2d\u88ab\u653b\u51fb\uff08\u8840\u91cf %s \u2192 %.1f\uff09\u2192 \u4e2d\u65ad\u8865\u7ed9\u3001\u539f\u5730\u8d77\u98de", before < 0.0 ? String.format("%.1f", Float.valueOf(hp)) : String.format("%.1f", before), Float.valueOf(hp));
        this.manualTask = false;
        this.supplyTicks = 0;
        this.supplyErrorRetryCount = 0;
        this.lastHurtHealth = -1.0;
        this.supplyCooldown = 600;
        this.supplyHurtNoLand = 600;
        FOElytraLog.detail("\u8fd9\u6bb5\u65f6\u95f4\uff08%d \u79d2\uff09\u4e0d\u964d\u843d\u3001\u4e5f\u4e0d\u91cd\u8bd5\u8865\u7ed9\uff1b\u5982\u679c\u843d\u70b9\u9644\u8fd1\u672c\u6765\u5c31\u6709\u602a\uff0c\u8bf4\u660e\u964d\u843d\u70b9\u7684\u67e5\u602a\u6f0f\u4e86\uff08\u73b0\u5728\u6309\u6bcf\u4e2a\u5019\u9009\u70b9\u81ea\u5df1\u7684\u5730\u9762\u9ad8\u5ea6\u67e5\u602a\uff0c\u5782\u76f4\u5bb9\u5dee %d \u683c\uff09", 30, 8);
        if (this.beginRecover("\u8865\u7ed9\u4e2d\u88ab\u653b\u51fb", RecoverAfter.PREPARE)) {
            FOElytraLog.info("\u8fd9\u6b21\u8865\u7ed9 %d \u79d2\u5185\u4e0d\u518d\u8bd5\uff08\u5148\u628a\u653e\u4e0b\u7684\u4e1c\u897f\u6536\u56de\u6765\u518d\u8d77\u98de\uff09", 30);
            return true;
        }
        if (this.supplyTask != null && this.supplyTask.isRunning()) {
            this.supplyTask.abort("\u8865\u7ed9\u4e2d\u88ab\u653b\u51fb");
        }
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        FOElytraLog.info("\u8865\u7ed9\u5df2\u4e2d\u65ad\uff0c\u8fd9\u6b21\u8865\u7ed9 %d \u79d2\u5185\u4e0d\u518d\u8bd5\uff08\u5148\u8d77\u98de\uff09", 30);
        this.state = State.PREPARE;
        return true;
    }

    private void placeTorchBeforeSupply() {
        if (!((Boolean)this.torchBeforeSupply.get()).booleanValue() || this.torchPlacedThisLanding) {
            return;
        }
        this.torchPlacedThisLanding = true;
        int slot = this.findTorchHotbar();
        if (slot < 0) {
            FOElytraLog.detail("\u60f3\u63d2\u706b\u628a\u4f46\u80cc\u5305\u91cc\u6ca1\u6709\u706b\u628a\u6216\u706f\u7b3c\uff0c\u8df3\u8fc7", new Object[0]);
            return;
        }
        BlockPos target = InvHelper.findPlaceTarget((PlayerEntity)this.mc.player, 2);
        if (target == null) {
            FOElytraLog.detail("\u60f3\u63d2\u706b\u628a\u4f46\u9644\u8fd1\u6ca1\u6709\u53ef\u653e\u7684\u4f4d\u7f6e\uff0c\u8df3\u8fc7", new Object[0]);
            return;
        }
        if (InvHelper.placeBlock(target, slot)) {
            FOElytraLog.info("\u964d\u843d\u70b9\u63d2\u4e86\u706b\u628a\uff08%s\uff09\uff0c\u964d\u4f4e\u5237\u602a", target.toShortString());
        } else {
            FOElytraLog.detail("\u63d2\u706b\u628a\u5931\u8d25\uff08%s\uff09\uff0c\u7ee7\u7eed\u8865\u7ed9", target.toShortString());
        }
    }

    private int findTorchHotbar() {
        int slot = InvHelper.findSlot(s -> s.isOf(Items.TORCH) || s.isOf(Items.SOUL_TORCH) || s.isOf(Items.LANTERN) || s.isOf(Items.SOUL_LANTERN), 0, 9);
        if (slot >= 0) {
            return slot;
        }
        int bag = InvHelper.findSlot(s -> s.isOf(Items.TORCH) || s.isOf(Items.SOUL_TORCH) || s.isOf(Items.LANTERN) || s.isOf(Items.SOUL_LANTERN), 9, 36);
        if (bag < 0) {
            return -1;
        }
        int empty = InvHelper.findEmptyHotbarSlot();
        if (empty < 0) {
            return -1;
        }
        InvHelper.moveInvToHotbar(bag, empty);
        return empty;
    }

    private void flying() {
        boolean lavaNow;
        if (this.segmentTarget == null) {
            this.state = State.PREPARE;
            return;
        }
        if (this.lavaEscaping()) {
            return;
        }
        this.fireworkStallTick();
        this.takeoffFireworkTick();
        if (!BaritoneHook.isFlying()) {
            double dz;
            double dx = this.mc.player.getX() - ((double)this.segmentTarget.getX() + 0.5);
            double dist = Math.sqrt(dx * dx + (dz = this.mc.player.getZ() - ((double)this.segmentTarget.getZ() + 0.5)) * dz);
            if (dist <= (double)((Integer)this.arriveRadius.get()).intValue()) {
                FOElytraLog.info("\u5230\u8fbe\u76ee\u6807 %d, %d\uff08\u8bef\u5dee %.1f \u683c\uff09", this.segmentTarget.getX(), this.segmentTarget.getZ(), dist);
                this.segFailWindow.reset();
                this.segResetDone = false;
                this.spinWindow.reset();
                BaritoneHook.clearSegFailCounter();
                this.segmentTarget = null;
                if (this.lavaPredictor != null) {
                    this.lavaPredictor.reset();
                }
                this.logSegmentStability("\u5230\u8fbe\u76ee\u6807");
                this.resetSegmentStability();
                if (this.mode.get() == Mode.SingleTarget) {
                    this.finish("\u5df2\u5230\u8fbe\u76ee\u6807\u5750\u6807");
                    return;
                }
                this.state = State.PREPARE;
                return;
            }
            this.segFailWindow.accumulate(this.tickCounter);
            FOElytraLog.warn("\u672c\u6bb5\u63d0\u524d\u7ed3\u675f\uff08\u8ddd\u76ee\u6807 %.0f \u683c\uff0c\u7b2c %d \u6b21\uff09", dist, this.segFailWindow.getCount(this.tickCounter));
            this.logSegmentStability("\u672c\u6bb5\u63d0\u524d\u7ed3\u675f");
            this.resetSegmentStability();
            if (this.lavaPredictor != null) {
                this.lavaPredictor.reset();
            }
            this.state = State.PREPARE;
            return;
        }
        if (this.lavaPredictor != null) {
            this.lavaPredictor.tick(this.mc, this.segmentTarget);
            if (this.lavaPredictor.isAvoiding()) {
                return;
            }
        }
        if (this.flightStatusCheck()) {
            return;
        }
        if (!this.lavaDanger() && ((Boolean)this.autoSupply.get()).booleanValue() && this.supplyCooldown <= 0 && ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET) == 0) {
            FOElytraLog.warn("\u70df\u82b1\u5df2\u7ecf\u7528\u5149\uff0c\u7acb\u523b\u964d\u843d\u8865\u7ed9\uff08%s\uff09", this.supplyReason());
            if (this.startSupply()) {
                return;
            }
            this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
        }
        if (((Boolean)this.pauseOnPlayers.get()).booleanValue() && this.playerNearby((Double)this.playerRange.get())) {
            if (!this.pausedByPlayer) {
                this.pausedByPlayer = true;
                if (this.eat.isEating()) {
                    FOElytraLog.detail("\u6709\u73a9\u5bb6\u5728\u9644\u8fd1\u8981\u8ba9\u884c\uff1a\u5148\u505c\u4e0b\u8fdb\u98df\uff08\u514d\u5f97\u8fdb\u98df\u7ed3\u675f\u65f6\u628a\u8ba9\u884c\u7684\u6682\u505c\u9876\u6389\uff09", new Object[0]);
                    this.eat.stop();
                }
                BaritoneHook.pause();
                FOElytraLog.info("\u9644\u8fd1\u6709\u73a9\u5bb6\uff0c\u6682\u505c\u98de\u884c\u7b49\u5f85\u5176\u79bb\u5f00", new Object[0]);
            }
            return;
        }
        if (this.pausedByPlayer) {
            this.pausedByPlayer = false;
            if (!this.hovering && !this.fireballs.isEngaging()) {
                BaritoneHook.resume();
            }
            FOElytraLog.info("\u73a9\u5bb6\u5df2\u79bb\u5f00\uff0c\u7ee7\u7eed\u98de\u884c", new Object[0]);
        }
        if (this.fireballs.isEngaging() && this.fireballs.pausedBaritone()) {
            return;
        }
        if (!((Boolean)this.chunkWait.get()).booleanValue()) {
            if (this.hovering) {
                this.hovering = false;
                if (!this.pausedByPlayer && !this.fireballs.pausedBaritone()) {
                    BaritoneHook.resume();
                }
                FOElytraLog.info("\u5df2\u5173\u95ed\u533a\u5757\u7b49\u5f85\uff0c\u6062\u590d\u98de\u884c", new Object[0]);
            }
        } else if (this.tickCounter % 10 == 0) {
            float ratio = this.unloadedChunkRatio((Integer)this.chunkRadius.get());
            if (!this.hovering && (double)ratio > (Double)this.unloadedRatio.get() && this.openBelowWithin(7)) {
                this.hovering = true;
                this.hoverStart = this.tickCounter;
                BaritoneHook.pause();
                FOElytraLog.warn("\u672a\u52a0\u8f7d\u533a\u5757 %.0f%% \u4e14\u811a\u4e0b 7 \u683c\u5185\u6ca1\u6709\u65b9\u5757\uff0c\u6682\u505c\u7b49\u5f85\uff08\u539f\u5730\u76d8\u65cb\u8865\u70df\u82b1\uff09", Float.valueOf(ratio * 100.0f));
            } else if (this.hovering) {
                boolean timeout;
                boolean loaded = ratio <= 0.05f;
                boolean bl = timeout = this.tickCounter - this.hoverStart > (Integer)this.hoverTimeout.get();
                if (loaded || timeout) {
                    this.hovering = false;
                    if (timeout) {
                        FOElytraLog.warn("\u7b49\u5f85\u533a\u5757\u8d85\u65f6\uff08%d tick\uff09\uff0c\u5f3a\u5236\u6062\u590d\u98de\u884c", this.hoverTimeout.get());
                    } else {
                        FOElytraLog.info("\u533a\u5757\u52a0\u8f7d\u5b8c\u6210\uff08\u672a\u52a0\u8f7d %.0f%%\uff09\uff0c\u7ee7\u7eed\u98de\u884c", Float.valueOf(ratio * 100.0f));
                    }
                    if (!this.pausedByPlayer && !this.fireballs.pausedBaritone()) {
                        BaritoneHook.resume();
                    }
                }
            }
        }
        if (this.hovering) {
            return;
        }
        if (this.tickCounter % 20 == 0) {
            PlayerAction.clearStuckSneak();
        }
        if (!(lavaNow = this.lavaDanger()) && ((Boolean)this.autoMend.get()).booleanValue() && this.mendCooldown <= 0 && MendTask.shouldRepair((Integer)this.mendDurability.get())) {
            FOElytraLog.info("\u9798\u7fc5\u8010\u4e45\u4e0d\u8db3\uff0c\u51c6\u5907\u964d\u843d\u4fee\u590d", new Object[0]);
            if (this.startMend()) {
                return;
            }
            this.mendCooldown = 100;
        }
        if (!lavaNow && ((Boolean)this.autoSupply.get()).booleanValue() && this.supplyCooldown <= 0 && this.supplyNeeded()) {
            FOElytraLog.info("\u89e6\u53d1\u8865\u7ed9\uff1a%s", this.supplyReason());
            if (this.startSupply()) {
                return;
            }
            this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
        }
    }

    private boolean openBelowWithin(int depth) {
        int cy;
        if (this.mc.world == null || this.mc.player == null) {
            return false;
        }
        int x = this.mc.player.getBlockX();
        int z = this.mc.player.getBlockZ();
        int y = this.mc.player.getBlockY() - 1;
        for (int i = 0; i < depth && (cy = y - i) >= this.mc.world.getBottomY(); ++i) {
            if (!this.mc.world.isChunkLoaded(x >> 4, z >> 4)) {
                return false;
            }
            if (this.mc.world.getBlockState(new BlockPos(x, cy, z)).isAir()) continue;
            return false;
        }
        return true;
    }

    private void inputStuckTick() {
        if (this.mc.player == null) {
            return;
        }
        if (!this.mc.player.isUsingItem()) {
            this.usingItemTicks = 0;
            return;
        }
        if (++this.usingItemTicks > 40 && this.tickCounter - this.usingItemLogTick > 100 && !this.eat.isEating() && !this.lavaEscaping()) {
            this.usingItemLogTick = this.tickCounter;
            FOElytraLog.warn("\u300c\u6b63\u5728\u4f7f\u7528\u7269\u54c1\u300d\u5df2\u7ecf\u5361\u4f4f %.0f \u79d2\uff08\u4e0d\u662f\u5728\u8fdb\u98df\u3001\u4e5f\u4e0d\u662f\u5ca9\u6d46\u81ea\u6551\uff09\uff1a\u624b\u6301 %s\uff5c\u53f3\u952e\u952e %s\uff5c\u754c\u9762 %s\uff5c\u8fd9\u4e00\u6bb5\u539f\u7248\u4e0d\u4f1a\u518d\u63a5\u53d7\u4efb\u4f55\u53f3\u952e", (double)this.usingItemTicks / 20.0, this.mc.player.getMainHandStack().isEmpty() ? "\u7a7a\u624b" : this.mc.player.getMainHandStack().getName().getString(), this.mc.options.useKey.isPressed() ? "\u6309\u4e0b" : "\u6ca1\u6309", this.mc.currentScreen != null ? "\u5f00\u7740" : "\u65e0");
        }
    }

    private void autoMendTick() {
        if (!((Boolean)this.autoMend.get()).booleanValue() || this.mendCooldown > 0) {
            return;
        }
        if (!MendTask.shouldRepair((Integer)this.mendDurability.get())) {
            return;
        }
        if (this.state == State.MEND || this.state == State.SUPPLY || this.state == State.LANDING || this.state == State.RECOVER || this.state == State.FAILED) {
            return;
        }
        if (this.lavaEscaping() || this.mc.player.isInLava()) {
            return;
        }
        if (!this.safeLandingBelow()) {
            if (this.tickCounter - this.mendSkipLogTick > 200) {
                this.mendSkipLogTick = this.tickCounter;
                FOElytraLog.detail("\u9798\u7fc5\u8010\u4e45\u5df2\u5230\u7ebf\uff0c\u4f46\u811a\u4e0b\u8fd9\u6bb5\u843d\u70b9\u4e0d\u5b89\u5168\uff08\u5ca9\u6d46/\u6c34/\u60ac\u7a7a\uff09\u2192 \u8fd9\u6b21\u5148\u4e0d\u4fee\uff0c\u98de\u5230\u6709\u9646\u5730\u7684\u5730\u65b9\u518d\u4fee", new Object[0]);
            }
            return;
        }
        FOElytraLog.info("\u9798\u7fc5\u8010\u4e45\u4e0d\u8db3\uff0c\u7acb\u523b\u51c6\u5907\u964d\u843d\u4fee\u590d", new Object[0]);
        BounceProbe.mark("\u8010\u4e45\u5230\u7ebf\u964d\u843d\u4fee\u8865");
        if (this.startMend()) {
            return;
        }
        this.mendCooldown = 100;
    }

    private boolean safeLandingBelow() {
        try {
            int i;
            int x = this.mc.player.getBlockX();
            int z = this.mc.player.getBlockZ();
            int y = this.mc.player.getBlockY();
            int ground = Integer.MIN_VALUE;
            for (i = 1; i <= 16; ++i) {
                BlockState st = this.mc.world.getBlockState(new BlockPos(x, y - i, z));
                if (!st.getFluidState().isEmpty()) {
                    return false;
                }
                if (st.isAir()) continue;
                ground = y - i;
                break;
            }
            if (ground == Integer.MIN_VALUE) {
                return false;
            }
            for (i = 1; i <= 3; ++i) {
                if (this.mc.world.getBlockState(new BlockPos(x, ground + i, z)).isAir()) continue;
                return false;
            }
            return true;
        }
        catch (Throwable t) {
            return false;
        }
    }

    private void fireworkStallTick() {
        int total = ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET);
        if (this.fireworkSeenTotal < 0 || total < this.fireworkSeenTotal) {
            this.lastFireworkConsumeTick = this.tickCounter;
        }
        this.fireworkSeenTotal = total;
        if (this.tickCounter - this.lastFireworkConsumeTick < 600) {
            return;
        }
        if (this.mc.player.getVelocity().y > -0.05) {
            return;
        }
        if (this.tickCounter - this.fireworkStallLogTick < 200) {
            return;
        }
        this.fireworkStallLogTick = this.tickCounter;
        ItemStack chest = ItemHelper.wornElytra((PlayerEntity)this.mc.player);
        FOElytraLog.warn("\u5df2\u7ecf %.0f \u79d2\u6ca1\u6d88\u8017\u70df\u82b1\u3001\u8fd8\u5728\u6389\u9ad8\u5ea6\uff08vy %.2f\uff09\u2192 \u53ef\u80fd\u653e\u4e0d\u51fa\u70df\u82b1\uff1a\u624b\u6301 %s\uff08\u5feb\u6377\u680f\u7b2c %d \u683c\uff09\uff5c\u6ed1\u7fd4 %s\uff5c\u63a5\u5730 %s\uff5c\u5feb\u6377\u680f\u70df\u82b1 %d \u53d1\uff5cBaritone %s\uff5c\u80f8\u7532\u69fd %s\uff08\u8010\u4e45 %d / \u6ee1 %d\uff09\uff5c\u6b63\u5728\u4f7f\u7528\u7269\u54c1 %s\uff5c\u53f3\u952e\u952e %s\uff5c\u754c\u9762 %s", (double)(this.tickCounter - this.lastFireworkConsumeTick) / 20.0, this.mc.player.getVelocity().y, this.mc.player.getMainHandStack().isEmpty() ? "\u7a7a\u624b" : this.mc.player.getMainHandStack().getName().getString(), this.mc.player.getInventory().getSelectedSlot() + 1, this.mc.player.isGliding() ? "\u662f" : "\u5426", this.mc.player.isOnGround() ? "\u662f" : "\u5426", ItemHelper.countInHotbar((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET), BaritoneHook.isFlying() ? "\u63a5\u7ba1\u4e2d" : "\u672a\u63a5\u7ba1", chest.isEmpty() ? "\u7a7a\u7684/\u4e0d\u662f\u9798\u7fc5" : chest.getName().getString(), ItemHelper.remainingDurability(chest), chest.getMaxDamage(), this.mc.player.isUsingItem() ? "\u662f" : "\u5426", this.mc.options.useKey.isPressed() ? "\u6309\u4e0b" : "\u6ca1\u6309", this.mc.currentScreen != null ? "\u5f00\u7740" : "\u65e0");
    }

    private void refillHotbarFireworks() {
        if (ItemHelper.countInHotbar((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET) >= (Integer)this.fireworkHotbarMin.get()) {
            this.checkFireworkTotal();
            return;
        }
        int source = InvHelper.findSlot(s -> s.isOf(Items.FIREWORK_ROCKET), 9, 36);
        if (source < 0) {
            return;
        }
        int target = InvHelper.findEmptyHotbarSlot();
        if (target < 0) {
            for (int i = 0; i < 9; ++i) {
                ItemStack s2 = this.mc.player.getInventory().getStack(i);
                if (s2.isOf(Items.FIREWORK_ROCKET) || ItemHelper.isFood(s2) || s2.isOf(Items.TOTEM_OF_UNDYING) || s2.isOf(Items.ELYTRA) || s2.isOf(Items.ENDER_CHEST) || ItemHelper.isShulkerBox(s2)) continue;
                target = i;
                break;
            }
        }
        if (target < 0) {
            return;
        }
        InvHelper.moveInvToHotbar(source, target);
        this.checkFireworkTotal();
    }

    private void checkFireworkTotal() {
        if (this.mc.player == null) {
            return;
        }
        int total = ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET);
        if (total > 128) {
            return;
        }
        if (this.tickCounter - this.lowFireworkWarnTick < 1200) {
            return;
        }
        this.lowFireworkWarnTick = this.tickCounter;
        ItemStack hotbar5 = this.mc.player.getInventory().getStack(5);
        boolean foodOk = this.matchesFoodSlot(hotbar5) && hotbar5.getCount() > 18;
        boolean totemOk = this.mc.player.getInventory().getStack(3).isOf(Items.TOTEM_OF_UNDYING) || this.mc.player.getInventory().getStack(4).isOf(Items.TOTEM_OF_UNDYING);
        FOElytraLog.warn("\u80cc\u5305\u70df\u82b1\u53ea\u5269 %d \u4e2a\uff08\u4e0d\u8db3 2 \u645e\uff09\uff1a\u5feb\u6377\u680f\u98df\u7269 %s\u3001\u5feb\u6377\u680f 3/4 \u56fe\u817e %s \u2192 \u4e0d\u8db3\u5c31\u63d0\u524d\u627e\u4f4d\u7f6e\u964d\u843d", total, foodOk ? "\u591f" : "\u4e0d\u591f", totemOk ? "\u5728" : "\u4e0d\u5728");
    }

    private boolean playerNearby(double range) {
        for (AbstractClientPlayerEntity entity : this.mc.world.getPlayers()) {
            if (entity == this.mc.player || entity.isDead() || !(entity.squaredDistanceTo((Entity)this.mc.player) <= range * range)) continue;
            return true;
        }
        return false;
    }

    private float unloadedChunkRatio(int radius) {
        try {
            ChunkPos center = new ChunkPos(this.mc.player.getBlockPos());
            int r = Math.min(this.mc.options.getClampedViewDistance(), radius);
            int total = 0;
            int unloaded = 0;
            for (int dx = -r; dx <= r; ++dx) {
                for (int dz = -r; dz <= r; ++dz) {
                    ++total;
                    if (this.mc.world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false) != null) continue;
                    ++unloaded;
                }
            }
            return total > 0 ? (float)unloaded / (float)total : 0.0f;
        }
        catch (Throwable t) {
            return 0.0f;
        }
    }

    private void fireballTick() {
        boolean enabled = (Boolean)this.deflectFireballs.get();
        FireballDeflector.Result result = this.fireballs.tick(enabled, (Boolean)this.fireballPauseBaritone.get(), (Integer)this.fireballMax.get(), (Double)this.fireballRange.get(), (Boolean)this.fireballFailOnMultiple.get() == false, this::resumeBaritoneIfIdle);
        if (result == FireballDeflector.Result.TOO_MANY) {
            if (!this.fireballTooManyWarned) {
                this.fireballTooManyWarned = true;
                FOElytraLog.err("\u706b\u7403\u592a\u591a\uff08\u8d85\u8fc7 %d \u4e2a\uff09\uff0c\u62e6\u4e0d\u8fc7\u6765\uff01", this.fireballMax.get());
            }
            this.fail("\u706b\u7403\u592a\u591a\u65e0\u6cd5\u62e6\u622a");
            return;
        }
        if (this.fireballs.tooManyCount() > 0) {
            if (!this.fireballTooManyWarned) {
                this.fireballTooManyWarned = true;
                FOElytraLog.err("\u706b\u7403\u592a\u591a\uff08\u8d85\u8fc7 %d \u4e2a\uff09\uff0c\u53ea\u62e6\u6700\u8fd1\u7684\u4e00\u4e2a\uff01", this.fireballMax.get());
            }
        } else {
            this.fireballTooManyWarned = false;
        }
    }

    private void resumeBaritoneIfIdle() {
        if (!this.hovering && !this.pausedByPlayer) {
            BaritoneHook.resume();
        }
    }

    private void ensureLava() {
        boolean ignoreGliding = (Boolean)this.lavaIgnoreGlidingFire.get();
        boolean f = (Boolean)this.lavaUseFirework.get();
        boolean swim = (Boolean)this.lavaSwimToSafety.get();
        int radius = (Integer)this.lavaSearchRadius.get();
        boolean potion = (Boolean)this.lavaDrinkFireRes.get();
        double pitch = (Double)this.lavaLookPitch.get();
        if (this.lava == null || radius != this.lavaCacheRadius || ignoreGliding != this.lavaCacheIgnoreGliding || f != this.lavaCacheFirework || swim != this.lavaCacheSwim || potion != this.lavaCachePotion || Math.abs(pitch - this.lavaCachePitch) > 0.001) {
            if (this.lava != null) {
                this.lava.release(this.mc);
            }
            this.lava = new LavaEscape(f, swim, radius, potion, ignoreGliding, (float)pitch);
            this.lavaCacheIgnoreGliding = ignoreGliding;
            this.lavaCacheRadius = radius;
            this.lavaCacheFirework = f;
            this.lavaCacheSwim = swim;
            this.lavaCachePotion = potion;
            this.lavaCachePitch = pitch;
        }
    }

    private void ensureLavaPredictor() {
        boolean en = (Boolean)this.lavaPredictEnabled.get();
        double horizon = (Double)this.lavaPredictHorizon.get();
        double urgent = (Double)this.lavaPredictUrgent.get();
        int lateral = (Integer)this.lavaPredictLateral.get();
        int back = (Integer)this.lavaPredictReturn.get();
        int cd = (Integer)this.lavaPredictCooldown.get();
        boolean warnOnly = (Boolean)this.lavaPredictWarnOnly.get();
        boolean pauseBt = (Boolean)this.lavaPredictPauseBaritone.get();
        double deflect = (Double)this.lavaPredictDeflect.get();
        if (this.lavaPredictor == null || en != this.pdCacheEnabled || horizon != this.pdCacheHorizon || urgent != this.pdCacheUrgent || lateral != this.pdCacheLateral || back != this.pdCacheReturn || cd != this.pdCacheCooldown || warnOnly != this.pdCacheWarnOnly || pauseBt != this.pdCachePauseBaritone || deflect != this.pdCacheDeflect) {
            if (this.lavaPredictor != null) {
                this.lavaPredictor.release(this.mc);
            }
            this.lavaPredictor = new LavaPredictor(new LavaPredictor.Options(en, horizon, urgent, lateral, back, cd, warnOnly, pauseBt, deflect));
            this.pdCacheEnabled = en;
            this.pdCacheHorizon = horizon;
            this.pdCacheUrgent = urgent;
            this.pdCacheLateral = lateral;
            this.pdCacheReturn = back;
            this.pdCacheCooldown = cd;
            this.pdCacheWarnOnly = warnOnly;
            this.pdCachePauseBaritone = pauseBt;
            this.pdCacheDeflect = deflect;
            FOElytraLog.detail("\u5ca9\u6d46\u9884\u6d4b\uff08\u5b9e\u9a8c\u6027\uff09\uff1a%s\uff08\u9884\u6d4b %.1f \u79d2 / \u7d27\u6025 %.1f \u79d2 / \u4fa7\u504f %d \u683c / \u7ed5\u884c\u7ed3\u675f %d \u683c / \u9632\u6296 %d tick / \u53ea\u9884\u8b66 %s / \u7d27\u6025\u6682\u505c Baritone %s / \u504f\u8f6c %.0f\u00b0\uff09", en ? "\u5df2\u542f\u7528" : "\u5df2\u5173\u95ed", horizon, urgent, lateral, back, cd, warnOnly ? "\u662f" : "\u5426", pauseBt ? "\u662f" : "\u5426", deflect);
        }
    }

    private void lavaTick() {
        if (this.lava == null) {
            return;
        }
        LavaEscape.Result result = this.lava.tick((Boolean)this.lavaEscape.get());
        if (result == LavaEscape.Result.ESCAPING) {
            BounceProbe.mark("\u5ca9\u6d46\u81ea\u6551\u89e6\u53d1");
            this.lavaWasEscaping = true;
            if (this.state == State.SUPPLY || this.state == State.MEND) {
                this.abortChildTasks();
                this.state = State.PREPARE;
                FOElytraLog.warn("\u8865\u7ed9/\u4fee\u590d\u8fc7\u7a0b\u4e2d\u6389\u8fdb\u5ca9\u6d46\uff0c\u5df2\u4e2d\u6b62\u5e76\u51c6\u5907\u8131\u79bb", new Object[0]);
            }
            this.supplyCooldown = Math.max(this.supplyCooldown, (Integer)this.supplyRetryDelay.get());
            this.mendCooldown = Math.max(this.mendCooldown, (Integer)this.supplyRetryDelay.get());
        } else if (result == LavaEscape.Result.FAILED) {
            this.lavaWasEscaping = false;
            boolean noFirework = this.lava.failedNoFirework() || ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET) <= 0;
            String lavaWhy = this.lava.lastReason();
            BounceProbe.mark("\u5ca9\u6d46\u81ea\u6551\u5931\u8d25\uff1a" + lavaWhy);
            this.lava.reset();
            if (((Boolean)this.lavaFailAbort.get()).booleanValue()) {
                FOElytraLog.warn("\u9003\u79bb\u5ca9\u6d46\u5931\u8d25\uff1a\u6309\u300c\u5931\u8d25\u81ea\u52a8\u767b\u51fa\u300d\u7684\u8bbe\u7f6e\u5904\u7406\uff08\u9ed8\u8ba4\u4e0d\u767b\u51fa\uff0c\u4f1a\u7559\u5728\u539f\u5730\u7b49\u4f60\u624b\u52a8\u63a5\u7ba1\uff09", new Object[0]);
                this.fail(noFirework ? "\u6ca1\u6709\u70df\u82b1\uff0c\u81ea\u6551\u65e0\u6548\uff08\u70df\u82b1 0 \u53d1 / \u5feb\u6377\u680f\u6ca1\u70df\u82b1\uff1a" + lavaWhy + "\uff09" : "\u9003\u79bb\u5ca9\u6d46\u5931\u8d25\uff08" + lavaWhy + "\uff09");
            } else {
                FOElytraLog.warn("\u9003\u79bb\u5ca9\u6d46\u5931\u8d25\uff0c\u4f46\u6309\u8bbe\u7f6e\u7ee7\u7eed\u8dd1\u56fe\uff08\u539f\u56e0\uff1a%s\uff1b\u4e25\u683c\u505a\u6cd5\u662f\u76f4\u63a5\u7ed3\u675f\u4efb\u52a1\uff09", lavaWhy);
            }
        } else if (this.lavaWasEscaping) {
            this.lavaWasEscaping = false;
            this.handBackToBaritone("\u5df2\u8131\u79bb\u5ca9\u6d46");
        }
    }

    private boolean clearAhead(float yaw, float pitch, int blocks) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3d eye = this.mc.player.getEyePos();
        for (int i = 1; i <= blocks; ++i) {
            BlockPos p = BlockPos.ofFloored((double)(eye.x + dx * (double)i), (double)(eye.y + dy * (double)i), (double)(eye.z + dz * (double)i));
            if (this.mc.world.getBlockState(p).isAir()) continue;
            return false;
        }
        return true;
    }

    private boolean waitForBaritoneTakeoff() {
        int waited;
        if (!((Boolean)this.takeoffByBaritone.get()).booleanValue()) {
            return false;
        }
        if (this.mc.player != null && this.walledInOneBlock() && AutoElytraFlight.blockingBlocks(1).isEmpty()) {
            return false;
        }
        if (this.btTakeoffWaitTick == 0) {
            this.btTakeoffWaitTick = this.tickCounter;
            BaritoneHook.btSet("elytraAutoJump", true);
            this.lastForcedAutoJump = true;
            FOElytraLog.info("\u5148\u4ea4\u7ed9 Baritone \u7684\u81ea\u52a8\u8d77\u8df3\uff08\u5b83\u81ea\u5df1\u627e\u8df3\u53f0\u8d70\u8fc7\u53bb\u8df3\uff09\uff1b%.0f \u79d2\u6ca1\u63a5\u7ba1\u5c31\u6539\u7528\u672c\u63d2\u4ef6\u7684\u539f\u5730\u8d77\u8df3 + \u70df\u82b1", 6.0);
        }
        if ((waited = this.tickCounter - this.btTakeoffWaitTick) < 120) {
            if (waited > 0 && waited % 40 == 0) {
                FOElytraLog.detail("\u7b49 Baritone \u81ea\u52a8\u8d77\u8df3\uff1a\u5df2 %.0f \u79d2\uff08\u5b83\u8981\u8d70\u5230\u6709\u843d\u5dee\u7684\u5730\u65b9\uff0c\u5e73\u5730/\u6d1e\u91cc\u4f1a\u5931\u8d25\uff09", (double)waited / 20.0);
            }
            return true;
        }
        if (!this.btTakeoffFallbackLogged) {
            this.btTakeoffFallbackLogged = true;
            FOElytraLog.warn("Baritone \u81ea\u52a8\u8d77\u8df3 %.0f \u79d2\u6ca1\u63a5\u7ba1\uff08\u5b83\u5fc5\u987b\u8d70\u5230\u6709\u843d\u5dee\u7684\u5730\u65b9\u624d\u80fd\u8df3\uff0c\u5e73\u5730/\u5bc6\u95ed\u5730\u5f62\u4f1a\u5931\u8d25\uff09 \u2192 \u6539\u7528\u672c\u63d2\u4ef6\u7684\u539f\u5730\u8d77\u8df3 + \u8865\u70df\u82b1", 6.0);
            BaritoneHook.btSet("elytraAutoJump", false);
            this.lastForcedAutoJump = false;
            this.takeoffDelayTicks = 0;
        }
        return false;
    }

    private void handBackToBaritone(String why) {
        if (this.mc.player == null) {
            return;
        }
        if (this.segmentTarget == null) {
            FOElytraLog.info("%s\uff1a\u8fd9\u4e00\u6bb5\u6ca1\u6709\u822a\u7ebf\u76ee\u6807\uff0cBaritone \u4ea4\u4e0d\u56de\u6765\uff08\u6211\u5728 %d %d %d\uff09", why, this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ());
            return;
        }
        BounceProbe.mark(why + " \u4ea4\u56de Baritone");
        if (BaritoneHook.isFlying()) {
            FOElytraLog.info("%s \u2192 Baritone \u7684\u9798\u7fc5\u8fdb\u7a0b\u8fd8\u5728\u98de\uff08isActive\uff09\uff0c\u4e0d\u91cd\u53d1\u822a\u7ebf\uff08\u91cd\u53d1\u4f1a\u6253\u65ad\u5b83\uff0c\u53c2\u8003\u5b9e\u73b0\u53ea\u5728\u8d77\u59cb\u4e0b\u53d1\u4e00\u6b21\uff09", why);
            return;
        }
        FOElytraLog.info("%s \u2192 \u7acb\u523b\u4ea4\u56de Baritone\uff08%d, %d\uff09\uff5c\u6211\u5728 %d %d %d", why, this.segmentTarget.getX(), this.segmentTarget.getZ(), this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ());
        this.lastReplanTick = this.tickCounter;
        this.replanEver = true;
        BaritoneHook.pathTo(this.segmentTarget.getX(), this.segmentTarget.getZ());
    }

    private boolean solidAt(BlockPos p) {
        BlockState s = this.mc.world.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    private boolean walledInOneBlock() {
        int[][] dirs;
        BlockPos p = this.mc.player.getBlockPos();
        for (int[] d : dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (this.solidAt(p.add(d[0], 0, d[1])) && this.solidAt(p.add(d[0], 1, d[1]))) continue;
            return false;
        }
        return true;
    }

    private void clearShaftTakeoff() {
        this.shaftColumn = null;
        this.shaftTicks = 0;
        PlayerAction.pressForward(false);
        PlayerAction.pressJump(false);
    }

    private boolean shaftAlignTick() {
        if (this.mc.player == null || this.mc.world == null) {
            return false;
        }
        if (this.mc.player.isGliding() || !this.mc.player.isOnGround()) {
            if (this.shaftColumn != null) {
                this.clearShaftTakeoff();
            }
            return false;
        }
        if (!AutoElytraFlight.blockingBlocks(1).isEmpty()) {
            return false;
        }
        if (!this.walledInOneBlock()) {
            if (this.shaftColumn != null) {
                this.clearShaftTakeoff();
            }
            return false;
        }
        if (this.shaftColumn == null) {
            this.shaftColumn = this.mc.player.getBlockPos();
            this.shaftTicks = 0;
            FOElytraLog.warn("\u56db\u9762 1 \u683c\u90fd\u88ab\u5835\u6b7b\u3001\u53ea\u6709\u5934\u9876\u662f\u7ad6\u76f4\u5f00\u53e3 \u2192 \u4e0d\u8d70 Baritone \u7684\u627e\u8df3\u53f0\uff0c\u76f4\u63a5\u539f\u5730\u8d77\u8df3 + \u653e\u70df\u82b1\uff08%d %d %d\uff09", this.shaftColumn.getX(), this.shaftColumn.getY(), this.shaftColumn.getZ());
        }
        double cx = (double)this.shaftColumn.getX() + 0.5;
        double cz = (double)this.shaftColumn.getZ() + 0.5;
        double off = Math.hypot(this.mc.player.getX() - cx, this.mc.player.getZ() - cz);
        if (off > 0.25 && this.shaftTicks++ < 40) {
            float yaw = (float)Math.toDegrees(Math.atan2(-(cx - this.mc.player.getX()), cz - this.mc.player.getZ()));
            this.mc.player.setYaw(yaw);
            PlayerAction.pressForward(true);
            return true;
        }
        PlayerAction.pressForward(false);
        return false;
    }

    private void takeoffFireworkTick() {
        if (this.takeoffFireworkPending <= 0) {
            return;
        }
        if (this.mc.player == null) {
            this.takeoffFireworkPending = 0;
            return;
        }
        ++this.takeoffFireworkPending;
        if (!this.mc.player.isGliding() || this.mc.player.isOnGround()) {
            if (this.takeoffFireworkPending > 60) {
                this.takeoffFireworkPending = 0;
                FOElytraLog.detail("\u8d77\u98de\u540e\u8865\u70df\u82b1\uff1a60 tick \u5185\u6ca1\u8fdb\u6ed1\u7fd4\uff0c\u8fd9\u6b21\u4e0d\u653e", new Object[0]);
            }
            return;
        }
        if (this.walledInOneBlock()) {
            this.mc.player.setPitch(-90.0f);
            FOElytraLog.detail("\u8d77\u98de\u540e\u8865\u70df\u82b1\uff1a\u56db\u9762\u5835\u6b7b\u7684\u7ad6\u76f4\u4e95 \u2192 \u62ac\u5934 -90\u00b0 \u5f80\u6b63\u4e0a\u65b9\u63a8", new Object[0]);
        } else if (!this.clearAhead(this.mc.player.getYaw(), this.mc.player.getPitch(), 8)) {
            this.takeoffFireworkPending = 0;
            FOElytraLog.detail("\u8d77\u98de\u540e\u8865\u70df\u82b1\uff1a\u524d\u65b9 8 \u683c\u6709\u969c\u788d\uff08\u5bc6\u95ed\u7a7a\u95f4\uff09\uff0c\u8fd9\u6b21\u4e0d\u653e\uff0c\u76f4\u63a5\u4ea4\u7ed9 Baritone", new Object[0]);
            this.handBackToBaritone("\u8d77\u98de\u540e\u524d\u65b9\u6709\u969c\u788d\u6ca1\u653e\u70df\u82b1");
            return;
        }
        int slot = InvHelper.findSlot(s -> s.isOf(Items.FIREWORK_ROCKET), 0, 9);
        if (slot < 0) {
            this.takeoffFireworkPending = 0;
            FOElytraLog.detail("\u8d77\u98de\u540e\u8865\u70df\u82b1\uff1a\u5feb\u6377\u680f 0~8 \u6ca1\u6709\u70df\u82b1\uff0c\u8df3\u8fc7\uff08\u7b49\u300c\u81ea\u52a8\u8865\u5145\u5feb\u6377\u680f\u70df\u82b1\u300d\uff09", new Object[0]);
            this.handBackToBaritone("\u8d77\u98de\u540e\u6ca1\u70df\u82b1\u53ef\u8865");
            return;
        }
        this.takeoffFireworkPending = 0;
        InvHelper.selectSlot(slot);
        boolean ok = InvHelper.useItem(Hand.MAIN_HAND);
        this.handBackToBaritone(ok ? "\u8d77\u98de\u540e\u8865\u4e86\u4e00\u53d1\u70df\u82b1\uff08\u5feb\u6377\u680f\u7b2c " + (slot + 1) + " \u683c\uff09" : "\u8d77\u98de\u540e\u8865\u70df\u82b1\u6ca1\u53d1\u51fa\u53bb\uff08\u5feb\u6377\u680f\u7b2c " + (slot + 1) + " \u683c\uff09");
    }

    private void safetyTick() {
        if (this.mc.player == null) {
            return;
        }
        if (((Boolean)this.autoLogout.get()).booleanValue()) {
            int totems = ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING);
            if ((double)this.mc.player.getHealth() < (Double)this.logoutHealth.get() && totems <= (Integer)this.logoutTotemMin.get()) {
                FOElytraLog.err("\u8840\u91cf %.1f \u4e14\u56fe\u817e %d \u4e2a\uff0c\u6267\u884c\u81ea\u52a8\u767b\u51fa", Float.valueOf(this.mc.player.getHealth()), totems);
                this.disconnect("\u8840\u91cf\u8fc7\u4f4e\u4e14\u56fe\u817e\u4e0d\u8db3");
            }
        }
    }

    private void disconnect(String reason) {
        try {
            ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
            if (handler != null) {
                handler.getConnection().disconnect(Text.of((String)("[AutoElytraFlight] \u81ea\u52a8\u767b\u51fa\uff1a" + reason)));
            }
        }
        catch (Throwable t) {
            FOElytraLog.err("\u81ea\u52a8\u767b\u51fa\u5931\u8d25: %s", String.valueOf(t));
        }
        if (this.state != State.FAILED) {
            this.state = State.DONE;
        }
        this.releaseEverything();
        if (((Boolean)this.disableOnFinish.get()).booleanValue() && this.isActive()) {
            this.toggle();
        }
    }

    private List<Item> foodPriorityList() {
        String key;
        List ordered = (List)this.foodPriorityOrdered.get();
        if (ordered != null && !ordered.isEmpty()) {
            return ordered;
        }
        String raw = (String)this.foodPriority.get();
        String string = key = raw == null ? "" : raw;
        if (!key.equals(this.foodPriorityRaw)) {
            this.foodPriorityRaw = key;
            this.foodPriorityParsed = FoodPriority.parse(key);
            if (!this.foodPriorityParsed.isEmpty()) {
                FOElytraLog.detail("\u98df\u7269\u4f18\u5148\u7ea7\uff08\u65e7\u7248\u6587\u672c\uff09\uff1a%d \u9879\u5df2\u89e3\u6790\uff5c\u8fdb\u98df\u4e0e\u8865\u7ed9\u5171\u7528", this.foodPriorityParsed.size());
            }
        }
        return this.foodPriorityParsed;
    }

    private void migrateFoodPriority() {
        List ordered = (List)this.foodPriorityOrdered.get();
        if (ordered != null && !ordered.isEmpty()) {
            return;
        }
        String raw = (String)this.foodPriority.get();
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        ArrayList<Item> parsed = new ArrayList<Item>();
        int dropped = 0;
        for (Item item : FoodPriority.parse(raw)) {
            if (item == null) continue;
            if (!SettingHelper.isFood(item)) {
                ++dropped;
                continue;
            }
            if (parsed.contains(item)) continue;
            parsed.add(item);
        }
        if (dropped > 0) {
            FOElytraLog.warn("\u98df\u7269\u4f18\u5148\u7ea7\uff1a\u5df2\u79fb\u9664 %d \u4e2a\u975e\u98df\u7269\u7269\u54c1\uff08\u4e0d\u80fd\u5403\u7684\u4e1c\u897f\u4e0d\u53c2\u4e0e\u4f18\u5148\u7ea7\uff09", dropped);
        }
        if (parsed.isEmpty()) {
            return;
        }
        this.foodPriorityOrdered.setAll(parsed);
        FOElytraLog.info("\u98df\u7269\u4f18\u5148\u7ea7\uff1a\u5df2\u628a\u65e7\u914d\u7f6e\u8fc1\u79fb\u5230\u65b0\u5217\u8868\uff08%d \u9879\uff0c\u987a\u5e8f\u4fdd\u7559\uff1b\u65e7\u6587\u672c\u53ef\u4ee5\u6e05\u7a7a\u4e86\uff09", parsed.size());
    }

    private List<Item> junkProtectedExtras() {
        ArrayList<Item> out = new ArrayList<Item>();
        if (this.foodPriorityList() != null) {
            out.addAll(this.foodPriorityList());
        }
        if (this.supplyFoodItems.get() != null) {
            out.addAll((Collection)this.supplyFoodItems.get());
        }
        if (this.storeItems.get() != null) {
            out.addAll((Collection)this.storeItems.get());
        }
        if (this.restockItems.get() != null) {
            out.addAll((Collection)this.restockItems.get());
        }
        if (this.foodWhitelist.get() != null) {
            out.addAll((Collection)this.foodWhitelist.get());
        }
        return out;
    }

    private int fireworkTargetStacks() {
        int base = 21;
        if (((Boolean)this.infinityElytra.get()).booleanValue()) {
            base = 26;
        } else if (((Boolean)this.autoMend.get()).booleanValue() && (Integer)this.targetXpBottles.get() > 0) {
            base = 23;
        }
        return Math.max(base, (Integer)this.targetFireworkStacks.get());
    }

    private SupplyOptions supplyOptions() {
        return new SupplyOptions(this.fireworkTargetStacks(), (Integer)this.targetXpBottles.get(), (Integer)this.targetFoodCount.get(), (Integer)this.targetTotems.get(), (Integer)this.targetElytraCount.get(), (Integer)this.minEnderChests.get(), (Integer)this.maxShulkers.get(), (Integer)this.placeRadius.get(), (Integer)this.actionDelay.get(), (Boolean)this.autoPlaceEnderChest.get(), (Boolean)this.autoPickupEnderChest.get(), (Boolean)this.useBaritoneMine.get(), (Boolean)this.storeLoot.get(), this.storeItems.get() == null ? List.of() : (List)this.storeItems.get(), this.supplyFoodItems.get() == null ? List.of() : (List)this.supplyFoodItems.get(), (Boolean)this.debugMessages.get(), this.foodPriorityList());
    }

    private int supplyErrorRetriesMax() {
        try {
            return Math.max(1, (Integer)this.supplyErrorRetries.get());
        }
        catch (Throwable t) {
            return 3;
        }
    }

    private boolean startSupply() {
        BounceProbe.mark("\u5f00\u59cb\u8865\u7ed9");
        if (this.supplyTask != null && this.supplyTask.isRunning()) {
            FOElytraLog.warn("\u4e0a\u4e00\u6b21\u8865\u7ed9\u8fd8\u6ca1\u7ed3\u675f", new Object[0]);
            return false;
        }
        if (((Boolean)this.noSupplyInBasaltDeltas.get()).booleanValue() && this.isBasaltDeltasAt(this.mc.player.getBlockX(), this.mc.player.getBlockZ())) {
            FOElytraLog.warn("\u73b0\u5728\u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\uff0c\u8df3\u8fc7\u8fd9\u6b21\u8865\u7ed9\uff0c\u7ee7\u7eed\u98de", new Object[0]);
            FOElytraLog.info("\u8fd9\u6b21\u8865\u7ed9 %d \u79d2\u5185\u4e0d\u518d\u8bd5\uff08\u8d77\u98de\u524d\u5c31\u5728\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32\uff0c\u4ec0\u4e48\u90fd\u4e0d\u4f1a\u653e\u4e0b\uff09", 30);
            this.supplyCooldown = Math.max(this.supplyCooldown, 600);
            return false;
        }
        if (this.totemIsOnlyShortage() && this.totemHasNoRoom()) {
            FOElytraLog.warn("\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u653e\u66f4\u591a\u56fe\u817e\uff08\u5feb\u6377\u680f 3/4 \u5df2\u6ee1\u3001\u80cc\u5305\u65e0\u7a7a\u4f4d\uff09\uff0c\u5148\u817e\u683c\u5b50", new Object[0]);
            this.exhaustedItems.add(Items.TOTEM_OF_UNDYING);
            this.exhaustedCooldown = 2400;
            this.supplyCooldown = Math.max(this.supplyCooldown, (Integer)this.supplyRetryDelay.get());
            FOElytraLog.warn("\u5148\u4e0d\u964d\u843d\u8865\u7ed9\uff1a\u8fd9\u6b21\u53ea\u4e3a\u56fe\u817e\u6765\uff0c\u800c\u56fe\u817e\u6ca1\u5730\u65b9\u653e\uff08%d \u79d2\u5185\u4e0d\u518d\u4e3a\u5b83\u964d\u843d\uff09", 120);
            return false;
        }
        this.eat.stop();
        BaritoneHook.stop();
        this.supplyTask = new SupplyTask(this.supplyOptions());
        this.supplyTask.start();
        this.waitTicks = 0;
        this.landingY = Double.NaN;
        this.landingTicks = 0;
        this.landingAngle = 0.0;
        this.torchPlacedThisLanding = false;
        this.lastHurtHealth = -1.0;
        this.state = State.LANDING;
        FOElytraLog.info("\u51c6\u5907\u964d\u843d\u8865\u7ed9\uff08\u76ee\u6807 %d \u7ec4\u70df\u82b1 / %d \u74f6 / %d \u98df\u7269 / %d \u56fe\u817e / %d \u9798\u7fc5\uff09", this.fireworkTargetStacks(), this.targetXpBottles.get(), this.targetFoodCount.get(), this.targetTotems.get(), this.targetElytraCount.get());
        return true;
    }

    private void infinityElytraTick() {
        if (!((Boolean)this.infinityElytra.get()).booleanValue() || this.mc.player == null) {
            return;
        }
        if (this.state != State.TAKEOFF && this.state != State.FLYING && this.state != State.LANDING) {
            return;
        }
        if (!this.mc.player.isGliding() && !BaritoneHook.isFlying()) {
            return;
        }
        if (this.tickCounter % 12 == 0) {
            PlayerAction.sendStartFallFlying();
        } else if (this.tickCounter % 12 == 1) {
            this.mc.player.startGliding();
            PlayerAction.sendStartFallFlying();
        }
    }

    private boolean totemHasNoRoom() {
        if (this.mc.player == null) {
            return false;
        }
        PlayerInventory inv = this.mc.player.getInventory();
        if (!inv.getStack(3).isOf(Items.TOTEM_OF_UNDYING) || !inv.getStack(4).isOf(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack s = inv.getStack(i);
            if (!s.isOf(Items.TOTEM_OF_UNDYING) || s.getCount() >= s.getMaxCount()) continue;
            return false;
        }
        return InvHelper.emptyBackpackSlots() == 0;
    }

    private boolean totemIsOnlyShortage() {
        if (this.mc.player == null) {
            return false;
        }
        if (this.isExhausted(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        if (ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING) >= (Integer)this.minTotems.get()) {
            return false;
        }
        if (!this.isExhausted(Items.FIREWORK_ROCKET) && ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET)) < (Integer)this.minFireworkStacks.get()) {
            return false;
        }
        if (!this.foodExhaustedNow() && this.countFood() < (Integer)this.minFoodCount.get()) {
            return false;
        }
        if (((Boolean)this.autoMend.get()).booleanValue() && !this.isExhausted(Items.EXPERIENCE_BOTTLE) && ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE) < (Integer)this.minXpBottles.get()) {
            return false;
        }
        if (!this.isExhausted(Items.ELYTRA) && ItemHelper.totalElytraDurability((PlayerEntity)this.mc.player) < (Integer)this.minElytraDurability.get()) {
            return false;
        }
        return (Boolean)this.autoRestock.get() == false || (Boolean)this.restockTriggerSupply.get() == false || InventoryRestocker.shortage((List)this.restockItems.get(), (Integer)this.restockStacks.get()) == null || !this.restockShortageNotExhausted();
    }

    private void supplyTick() {
        if (this.supplyTask == null) {
            this.state = State.PREPARE;
            return;
        }
        if (this.supplyHurtTick()) {
            return;
        }
        if (InvHelper.screenOpen()) {
            PlayerAction.restoreHeldKeys();
        }
        this.supplyTask.tick();
        TaskStatus status = this.supplyTask.status();
        if (status == TaskStatus.RUNNING) {
            ++this.supplyTicks;
            if (this.supplyTicks % 100 == 0) {
                FOElytraLog.info("\u8865\u7ed9\u8fdb\u884c\u4e2d\uff08%d \u79d2\uff09\uff1a%s", this.supplyTicks / 20, this.supplyTask.progress());
            }
            if (this.supplyTicks > 2400) {
                FOElytraLog.warn("\u8865\u7ed9\u8d85\u65f6\uff08%d \u79d2\uff09\uff0c\u4e2d\u6b62\u672c\u6b21\u8865\u7ed9\uff08%s\uff09", this.supplyTicks / 20, this.supplyTask.progress());
                String stuckAt = this.supplyTask.progress();
                this.supplyTicks = 0;
                this.manualTask = false;
                ++this.supplyErrorRetryCount;
                if (this.supplyErrorRetryCount >= this.supplyErrorRetriesMax()) {
                    this.failSupply("\u8865\u7ed9\u8fde\u7eed " + this.supplyErrorRetryCount + " \u6b21\u8d85\u65f6\uff08\u6bcf\u6b21 2 \u5206\u949f\u8fd8\u6ca1\u8dd1\u5b8c\uff1b\u6700\u540e\u4e00\u6b21\u5361\u5728\uff1a" + stuckAt + "\uff09\u2014\u2014\u8bf7\u68c0\u67e5\u672b\u5f71\u7bb1/\u6f5c\u5f71\u76d2\u662f\u5426\u6709\u7a7a\u95f4\u3001\u5feb\u6377\u680f\u662f\u5426\u7559\u4e86\u7a7a\u4f4d\uff0c\u6216\u628a\u300c\u8865\u7ed9\u91cd\u8bd5\u7b49\u5f85\u300d\u8c03\u5927\u540e\u518d\u8bd5");
                    return;
                }
                this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
                FOElytraLog.warn("\u8865\u7ed9\u8d85\u65f6\u7b2c %d/%d \u6b21\uff1a%d tick \u540e\u624d\u4f1a\u518d\u8bd5\uff08\u4e0d\u4f1a\u7acb\u523b\u91cd\u5f00\uff09", this.supplyErrorRetryCount, this.supplyErrorRetriesMax(), this.supplyCooldown);
                if (this.beginRecover("\u8865\u7ed9\u8d85\u65f6", RecoverAfter.PREPARE)) {
                    return;
                }
                this.supplyTask.abort("\u8865\u7ed9\u8d85\u65f6");
                this.state = State.PREPARE;
            }
            return;
        }
        this.supplyTicks = 0;
        if (status == TaskStatus.IDLE) {
            this.failSupply("\u8865\u7ed9\u4efb\u52a1\u88ab\u4e2d\u6b62");
            return;
        }
        if (status == TaskStatus.DONE) {
            if (this.supplyTask.recoverNeeded() && this.beginRecover("\u8865\u7ed9\u6536\u5c3e", RecoverAfter.PREPARE)) {
                return;
            }
            if (this.startFullSupplyPending) {
                this.startFullSupplyPending = false;
                if (this.fullSupplyNeeded()) {
                    FOElytraLog.warn("\u4efb\u52a1\u5f00\u59cb\u8865\u7ed9\u5b8c\u6210\uff0c\u4f46\u4ecd\u6709\u7f3a\u53e3\uff08%s\uff09\uff1b\u6309\u8bbe\u7f6e\u7ee7\u7eed\u8d77\u98de", this.supplyReason());
                } else {
                    FOElytraLog.info("\u4efb\u52a1\u5f00\u59cb\uff1a\u7269\u8d44\u5df2\u8865\u6ee1\uff0c\u8d77\u98de", new Object[0]);
                }
            }
            Set<Item> gone = this.supplyTask.exhaustedItems();
            boolean foodGone = this.supplyTask.foodExhausted();
            if (!gone.isEmpty() || foodGone) {
                this.exhaustedItems.addAll(gone);
                if (foodGone) {
                    this.foodExhaustedCache = true;
                }
                this.exhaustedCooldown = 2400;
                this.supplyRetries = 0;
                this.supplyCooldown = Math.max(this.supplyCooldown, (Integer)this.supplyRetryDelay.get());
                FOElytraLog.warn("\u8865\u7ed9\u4efb\u52a1\u5224\u5b9a\u8fd9\u4e9b\u6682\u65f6\u53d6\u4e0d\u5230\uff1a%s \u2014\u2014 \u4e0d\u518d\u53cd\u590d\u964d\u843d\u8865\u7ed9\uff0c\u5148\u7528\u73b0\u6709\u7684\u7ee7\u7eed\u8dd1\uff08\u8981\u8865\u5c31\u5f80\u672b\u5f71\u7bb1\u91cc\u585e / \u7ed9\u80cc\u5305\u817e\u51fa\u683c\u5b50\uff1b%d \u79d2\u540e\u4f1a\u518d\u8bd5\u4e00\u6b21\uff09", this.describeExhausted(), 120);
            } else if (this.supplyGapAfterRun() && !this.manualTask) {
                ++this.supplyRetries;
                if (this.supplyRetries >= (Integer)this.maxSupplyRetries.get()) {
                    this.failSupply("\u8fde\u7eed " + this.supplyRetries + " \u6b21\u8865\u7ed9\u4ecd\u672a\u8fbe\u6807\uff08" + this.supplyReason() + "\uff09");
                    return;
                }
                this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
                FOElytraLog.warn("\u8865\u7ed9\u540e\u4ecd\u6709\u7f3a\u53e3\uff08\u7b2c %d \u6b21\uff09\uff1a%s\uff1b%d tick \u540e\u91cd\u8bd5", this.supplyRetries, this.supplyReason(), this.supplyCooldown);
            } else {
                this.supplyRetries = 0;
                FOElytraLog.info("\u8865\u7ed9\u5b8c\u6210%s", this.manualTask ? "" : "\uff0c\u7ee7\u7eed\u8dd1\u56fe");
            }
            this.manualTask = false;
            this.supplyErrorRetryCount = 0;
            this.state = State.PREPARE;
        } else {
            this.manualTask = false;
            ++this.supplyErrorRetryCount;
            if (this.supplyErrorRetryCount >= this.supplyErrorRetriesMax()) {
                this.failSupply("\u8865\u7ed9\u8fde\u7eed " + this.supplyErrorRetryCount + " \u6b21\u51fa\u9519\uff08\u6700\u540e\u4e00\u6b21\uff1a" + this.supplyTask.failReason() + "\uff09");
                return;
            }
            FOElytraLog.warn("\u8865\u7ed9\u8fc7\u7a0b\u4e2d\u51fa\u9519\uff08\u7b2c %d/%d \u6b21\uff09\uff1a%s \u2014\u2014 \u5148\u4e0d\u5224\u5931\u8d25\uff0c%d tick \u540e\u91cd\u8bd5\u8865\u7ed9", this.supplyErrorRetryCount, this.supplyErrorRetriesMax(), this.supplyTask.failReason(), this.supplyRetryDelay.get());
            this.supplyTicks = 0;
            this.supplyCooldown = (Integer)this.supplyRetryDelay.get();
            if (this.beginRecover("\u8865\u7ed9\u51fa\u9519\uff1a" + this.supplyTask.failReason(), RecoverAfter.PREPARE)) {
                return;
            }
            BaritoneHook.stop();
            PlayerAction.releaseAll();
            if (InvHelper.hasContainerOpen()) {
                InvHelper.closeScreen();
            }
            this.state = State.PREPARE;
        }
    }

    private boolean isExhausted(Item item) {
        return this.exhaustedCooldown > 0 && this.exhaustedItems.contains(item);
    }

    private boolean foodExhaustedNow() {
        return this.exhaustedCooldown > 0 && this.foodExhaustedCache;
    }

    private String describeExhausted() {
        StringBuilder sb = new StringBuilder();
        for (Item it : this.exhaustedItems) {
            if (sb.length() > 0) {
                sb.append("\u3001");
            }
            sb.append(it.getName().getString());
        }
        if (this.foodExhaustedCache) {
            if (sb.length() > 0) {
                sb.append("\u3001");
            }
            sb.append("\u98df\u7269");
        }
        return sb.length() == 0 ? "\u65e0" : sb.toString();
    }

    private boolean supplyNeeded() {
        if (!this.isExhausted(Items.FIREWORK_ROCKET) && ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET)) < (Integer)this.minFireworkStacks.get()) {
            return true;
        }
        if (!this.foodExhaustedNow() && this.countFood() < (Integer)this.minFoodCount.get()) {
            return true;
        }
        if (!this.isExhausted(Items.TOTEM_OF_UNDYING) && ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING) < (Integer)this.minTotems.get()) {
            return true;
        }
        if (((Boolean)this.autoMend.get()).booleanValue() && !this.isExhausted(Items.EXPERIENCE_BOTTLE) && ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE) < (Integer)this.minXpBottles.get()) {
            return true;
        }
        return !this.isExhausted(Items.ELYTRA) && ItemHelper.totalElytraDurability((PlayerEntity)this.mc.player) < (Integer)this.minElytraDurability.get();
    }

    private String supplyStockSignature(String need) {
        if (this.mc.player == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (need.contains("\u70df\u82b1")) {
            sb.append("fw=").append(ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET)).append(';');
        }
        if (need.contains("\u7ecf\u9a8c\u74f6")) {
            sb.append("xp=").append(ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE)).append(';');
        }
        if (need.contains("\u56fe\u817e")) {
            sb.append("tot=").append(ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING)).append(';');
        }
        if (need.contains("\u98df\u7269")) {
            sb.append("food=").append(this.countFood()).append(';');
        }
        if (sb.length() == 0) {
            sb.append("all=").append(ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET)).append(',').append(ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE)).append(',').append(ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING)).append(',').append(this.countFood()).append(',').append(this.countUsableElytra());
        }
        return sb.toString();
    }

    private void registerUnobtainableFromNeed(String need) {
        LinkedHashSet<Item> blocked = new LinkedHashSet<Item>();
        if (need.contains("\u70df\u82b1")) {
            blocked.add(Items.FIREWORK_ROCKET);
        }
        if (need.contains("\u7ecf\u9a8c\u74f6")) {
            blocked.add(Items.EXPERIENCE_BOTTLE);
        }
        if (need.contains("\u56fe\u817e")) {
            blocked.add(Items.TOTEM_OF_UNDYING);
        }
        boolean foodBlocked = need.contains("\u98df\u7269");
        if (blocked.isEmpty() && !foodBlocked) {
            return;
        }
        this.exhaustedItems.addAll(blocked);
        if (foodBlocked) {
            this.foodExhaustedCache = true;
        }
        this.exhaustedCooldown = 2400;
        this.supplyRetries = 0;
        FOElytraLog.warn("\u8fde\u7eed %d \u8f6e\u90fd\u4e3a\u300c%s\u300d\u964d\u843d\u8865\u7ed9\uff0c\u5374\u4e00\u70b9\u8fdb\u5c55\u90fd\u6ca1\u6709\uff08\u591a\u534a\u662f\u80cc\u5305\u6ca1\u7a7a\u4f4d\u653e\uff0c\u6216\u8005\u90a3\u4e2a\u76d2\u5b50\u91cc\u7684\u4e1c\u897f\u62ff\u4e0d\u51fa\u6765\uff09\u2192 \u5148\u5f53\u6210\u300c\u6682\u65f6\u53d6\u4e0d\u5230\u300d\uff1a%d \u79d2\u5185\u4e0d\u518d\u4e3a\u5b83\u964d\u843d\uff0c\u7528\u73b0\u6709\u7684\u7269\u8d44\u7ee7\u7eed\u98de\uff08\u65f6\u95f4\u5230\u4e86\u4f1a\u81ea\u52a8\u518d\u8bd5\u4e00\u6b21\uff09", this.supplyNoProgressRounds, need.trim(), 120);
        FOElytraLog.detail("\u6d3b\u9501\u4fdd\u62a4\u767b\u8bb0\uff1a\u7f3a\u53e3\u300c%s\u300d\u2192 \u767b\u8bb0 %s\uff08\u51b7\u5374 %d tick\uff09", need.trim(), this.describeExhausted(), 2400);
    }

    private boolean restockShortageNotExhausted() {
        if (this.exhaustedCooldown <= 0) {
            return true;
        }
        List<Item> items = this.restockItems.get();
        if (items == null || items.isEmpty()) {
            return true;
        }
        for (Item it : items) {
            if (!InventoryRestocker.isShort((PlayerEntity)this.mc.player, it, (Integer)this.restockStacks.get()) || this.isExhausted(it)) continue;
            return true;
        }
        return false;
    }

    private String supplyNeedText() {
        if (this.mc.player == null) {
            return "";
        }
        int fwNeed = Math.max(0, this.fireworkTargetStacks() - ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET)));
        int xpNeed = 0;
        if (((Boolean)this.autoMend.get()).booleanValue()) {
            xpNeed = (int)Math.ceil((double)Math.max(0, (Integer)this.targetXpBottles.get() - ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE)) / 64.0);
        }
        ItemStack hotbar3 = this.mc.player.getInventory().getStack(3);
        ItemStack hotbar4 = this.mc.player.getInventory().getStack(4);
        ItemStack hotbar5 = this.mc.player.getInventory().getStack(5);
        boolean hasTotem = hotbar3.isOf(Items.TOTEM_OF_UNDYING) || hotbar4.isOf(Items.TOTEM_OF_UNDYING);
        boolean hasFood = this.matchesFoodSlot(hotbar5) && hotbar5.getCount() > 18;
        StringBuilder sb = new StringBuilder();
        if (fwNeed != 0 && !this.isExhausted(Items.FIREWORK_ROCKET)) {
            sb.append("\u70df\u82b1 ").append(fwNeed).append(" \u7ec4\uff1b");
        }
        if (xpNeed != 0 && !this.isExhausted(Items.EXPERIENCE_BOTTLE)) {
            sb.append("\u7ecf\u9a8c\u74f6 ").append(xpNeed).append(" \u7ec4\uff1b");
        }
        if (!hasTotem && !this.isExhausted(Items.TOTEM_OF_UNDYING)) {
            sb.append("\u5feb\u6377\u680f 3/4 \u683c\u6ca1\u6709\u56fe\u817e\uff1b");
        }
        if (!hasFood && !this.foodExhaustedNow()) {
            sb.append("\u5feb\u6377\u680f 5 \u683c\u6ca1\u6709\u98df\u7269\uff08\u6216\u4e0d\u8db3 19 \u4e2a\uff09\uff1b");
        }
        return sb.toString();
    }

    private boolean matchesFoodSlot(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        List whitelist = (List)this.supplyFoodItems.get();
        if (whitelist != null && !whitelist.isEmpty()) {
            return whitelist.contains(s.getItem());
        }
        return ItemHelper.isFood(s);
    }

    private boolean fullSupplyNeeded() {
        int xpGap;
        if (this.mc.player == null) {
            return false;
        }
        int fwGap = this.fireworkTargetStacks() - ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET));
        if (fwGap >= 1 && !this.isExhausted(Items.FIREWORK_ROCKET)) {
            return true;
        }
        if (((Boolean)this.autoMend.get()).booleanValue() && !this.isExhausted(Items.EXPERIENCE_BOTTLE) && (xpGap = (Integer)this.targetXpBottles.get() - ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE)) >= 64) {
            return true;
        }
        int totemGap = (Integer)this.targetTotems.get() - ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING);
        if (totemGap >= 1 && !this.isExhausted(Items.TOTEM_OF_UNDYING)) {
            return true;
        }
        if (!this.foodExhaustedNow() && this.countFood() < (Integer)this.targetFoodCount.get() - 32) {
            return true;
        }
        return !this.isExhausted(Items.ELYTRA) && this.countUsableElytra() < (Integer)this.targetElytraCount.get();
    }

    private String fullSupplyGap() {
        int totemGap;
        int xpGap;
        if (this.mc.player == null) {
            return "\u65e0";
        }
        StringBuilder sb = new StringBuilder();
        int fwGap = this.fireworkTargetStacks() - ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET));
        if (fwGap >= 1) {
            sb.append("\u70df\u82b1\u8fd8\u5dee ").append(fwGap).append(" \u7ec4\uff1b");
        }
        if (((Boolean)this.autoMend.get()).booleanValue() && (xpGap = (Integer)this.targetXpBottles.get() - ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE)) >= 64) {
            sb.append("\u7ecf\u9a8c\u74f6\u8fd8\u5dee ").append(xpGap).append("\uff1b");
        }
        if ((totemGap = (Integer)this.targetTotems.get() - ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING)) >= 1) {
            sb.append("\u56fe\u817e\u8fd8\u5dee ").append(totemGap).append("\uff1b");
        }
        if (this.countFood() < (Integer)this.targetFoodCount.get() - 32) {
            sb.append("\u98df\u7269\u8fd8\u5dee ").append((Integer)this.targetFoodCount.get() - this.countFood()).append("\uff1b");
        }
        if (this.countUsableElytra() < (Integer)this.targetElytraCount.get()) {
            sb.append("\u5907\u7528\u9798\u7fc5\u8fd8\u5dee ").append((Integer)this.targetElytraCount.get() - this.countUsableElytra()).append("\uff1b");
        }
        return sb.length() == 0 ? "\u65e0\uff08\u5dee\u989d\u90fd\u4e0d\u503c\u5f97\u964d\u843d\uff09" : sb.toString();
    }

    private int countUsableElytra() {
        if (this.mc.player == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < 41; ++i) {
            ItemStack s = this.mc.player.getInventory().getStack(i);
            if (!s.isOf(Items.ELYTRA) || !ItemHelper.hasEnchantment(s, (RegistryKey<Enchantment>)Enchantments.UNBREAKING, 3) || s.getDamage() >= 15) continue;
            ++n;
        }
        return n;
    }

    private String supplyReason() {
        return "\u70df\u82b1 " + ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET)) + " \u7ec4 / \u98df\u7269 " + this.countFood() + " / \u7ecf\u9a8c\u74f6 " + ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.EXPERIENCE_BOTTLE) + " / \u56fe\u817e " + ItemHelper.countInInventory((PlayerEntity)this.mc.player, Items.TOTEM_OF_UNDYING) + " / \u9798\u7fc5\u603b\u8010\u4e45 " + ItemHelper.totalElytraDurability((PlayerEntity)this.mc.player);
    }

    private boolean supplyGapAfterRun() {
        Needs taskNeeds;
        if (this.supplyTask != null && (taskNeeds = this.supplyTask.needs()) != null && taskNeeds.isEmpty()) {
            return false;
        }
        return this.supplyNeeded();
    }

    private boolean lavaDanger() {
        if (this.mc.player == null) {
            return false;
        }
        if (this.lava != null && this.lava.isEscaping()) {
            return true;
        }
        return this.mc.player.isOnFire() || this.mc.player.isInLava();
    }

    private boolean lavaEscaping() {
        return this.lava != null && this.lava.isEscaping();
    }

    private int countFood() {
        int n = 0;
        List whitelist = (List)this.supplyFoodItems.get();
        boolean useWhitelist = whitelist != null && !whitelist.isEmpty();
        for (int i = 0; i < 36; ++i) {
            ItemStack s = this.mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !(useWhitelist ? whitelist.contains(s.getItem()) : ItemHelper.isFood(s))) continue;
            n += s.getCount();
        }
        return n;
    }

    private void landing() {
        if (this.waitForUserScreen("\u51c6\u5907\u964d\u843d\u8865\u7ed9")) {
            if (this.waitTicks > 2400) {
                this.fail("\u7b49\u4f60\u5173\u754c\u9762\u7b49\u4e86 2 \u5206\u949f\uff0c\u8865\u7ed9\u53d6\u6d88\uff08\u53ef\u624b\u52a8\u89e6\u53d1\u8865\u7ed9\u952e\u91cd\u8bd5\uff09");
            }
            return;
        }
        this.waitTicks = 0;
        if (this.mc.player.isOnGround() && !this.mc.player.isGliding()) {
            BaritoneHook.stop();
            this.landingY = Double.NaN;
            this.placeTorchBeforeSupply();
            this.state = State.SUPPLY;
            FOElytraLog.detail("\u5df2\u843d\u5730\uff08%d %d %d\uff09\uff0c\u8fdb\u5165\u8865\u7ed9\u6267\u884c", this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ());
            BounceProbe.mark("\u843d\u5730\u8fdb\u5165\u8865\u7ed9");
            return;
        }
        if (this.supplyHurtTick()) {
            return;
        }
        if (!((Boolean)this.landForSupply.get()).booleanValue()) {
            BaritoneHook.stop();
            this.landingY = Double.NaN;
            this.placeTorchBeforeSupply();
            this.state = State.SUPPLY;
            return;
        }
        if (Double.isNaN(this.landingY)) {
            if (!this.pickLandingColumn()) {
                if (this.basaltSkipHandled) {
                    this.basaltSkipHandled = false;
                    return;
                }
                this.abortLandingForSafety();
                return;
            }
            this.landingTicks = 0;
            this.landingAngle = 0.0;
            BaritoneHook.stop();
            FOElytraLog.info("\u5f00\u59cb\u6536\u655b\u4e0b\u964d\uff1a\u76ee\u6807\u5730\u9762 Y=%.0f\uff08\u5f53\u524d Y=%.1f\uff09\uff0c\u5df2\u505c\u6389\u7ed5\u5708\u98de\u884c", this.landingY, this.mc.player.getY());
        }
        ++this.landingTicks;
        ++this.waitTicks;
        this.descentTick();
        if (this.landingTicks % 60 == 0) {
            double above = this.mc.player.getY() - this.landingY;
            FOElytraLog.info("\u4e0b\u964d\u4e2d\uff08%d \u79d2\uff09\uff1a\u9ad8\u5ea6 %.1f\uff5c\u79bb\u5730\u9762 %.1f \u683c\uff5c\u76d8\u65cb\u534a\u5f84 %.1f\uff5c\u6ed1\u7fd4 %s", this.landingTicks / 20, this.mc.player.getY(), above, Math.max(4.0, above / 8.0), this.mc.player.isGliding() ? "\u662f" : "\u5426");
        }
        if (this.waitTicks > 2400) {
            this.failSupply("\u964d\u843d\u8d85\u65f6\uff0c\u65e0\u6cd5\u8865\u7ed9");
        }
    }

    private void descentTick() {
        if (this.landingTicks % 10 == 0 && this.landingColumnTurnedBad()) {
            this.abortLandingLavaWater("\u76ee\u6807\u70b9\u6216\u6b63\u4e0b\u65b9\u6709\u5ca9\u6d46/\u6c34");
            return;
        }
        if (!this.mc.player.isGliding()) {
            return;
        }
        double above = this.mc.player.getY() - this.landingY;
        double radius = Math.max(4.0, above / 8.0);
        this.landingAngle += 0.25;
        double aimX = this.landingTargetX;
        double aimZ = this.landingTargetZ;
        if (above > 4.0) {
            aimX += Math.cos(this.landingAngle) * radius;
            aimZ += Math.sin(this.landingAngle) * radius;
        }
        double dx = aimX - this.mc.player.getX();
        double dz = aimZ - this.mc.player.getZ();
        this.checkGlideReachable(above);
    }

    private void checkGlideReachable(double above) {
        if (this.landingTicks < 40) {
            return;
        }
        if (above <= 4.0) {
            return;
        }
        double flat = Math.hypot(this.landingTargetX - this.mc.player.getX(), this.landingTargetZ - this.mc.player.getZ());
        double needRatio = flat / Math.max(1.0, above);
        if (needRatio <= 10.0) {
            return;
        }
        FOElytraLog.warn("\u6ed1\u7fd4\u9ad8\u5ea6\u4e0d\u8db3\u4ee5\u5230\u8fbe\u964d\u843d\u70b9\uff08\u5e73\u8ddd %.0f \u683c / \u9ad8\u5ea6\u5dee %.0f \u683c\uff0c\u8fd8\u5dee %d \u683c\u9ad8\u5ea6\uff09\u2192 \u5c31\u5730\u964d\u843d/\u4ea4\u7ed9 Baritone", flat, above, Math.max(0, (int)Math.round(flat / 10.0 - above)));
        BaritoneHook.stop();
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        this.landingY = Double.NaN;
        this.landingTicks = 0;
        this.supplyCooldown = Math.max(this.supplyCooldown, 600);
        this.state = State.PREPARE;
    }

    private MendTask.Options mendOptions() {
        return new MendTask.Options((Integer)this.mendDurability.get(), Math.max(1, (Integer)this.mendDurability.get() / 4), (Integer)this.minBottles.get(), (Integer)this.repairToDamage.get(), (Boolean)this.requireGround.get(), (Boolean)this.requireNetherWastes.get(), (Boolean)this.requireMending.get(), (Double)this.mendPitch.get(), (Integer)this.throwDelay.get(), (Integer)this.maxThrows.get(), (Integer)this.landingTimeout.get());
    }

    private boolean startMend() {
        BounceProbe.mark("\u5f00\u59cb\u4fee\u9798\u7fc5");
        if (this.mendTask != null && this.mendTask.isRunning()) {
            FOElytraLog.warn("\u4e0a\u4e00\u6b21\u4fee\u590d\u8fd8\u6ca1\u7ed3\u675f", new Object[0]);
            return false;
        }
        this.eat.stop();
        BaritoneHook.stop();
        this.mendTask = new MendTask(this.mendOptions());
        this.mendTask.start();
        if (this.mendTask.status() != TaskStatus.RUNNING) {
            FOElytraLog.warn("\u4fee\u9798\u7fc5\u6ca1\u80fd\u5f00\u59cb\uff1a%s", this.mendTask.failReason());
            return false;
        }
        this.state = State.MEND;
        return true;
    }

    private void mendTick() {
        if (this.mendTask == null) {
            this.state = State.PREPARE;
            return;
        }
        this.mendTask.tick();
        TaskStatus status = this.mendTask.status();
        if (status == TaskStatus.RUNNING) {
            return;
        }
        if (status == TaskStatus.IDLE) {
            this.fail("\u4fee\u9798\u7fc5\u4efb\u52a1\u88ab\u4e2d\u6b62");
            return;
        }
        if (status == TaskStatus.DONE) {
            this.mendRetries = 0;
            FOElytraLog.info("\u9798\u7fc5\u4fee\u590d\u5b8c\u6210%s", this.manualTask ? "" : "\uff0c\u7ee7\u7eed\u8dd1\u56fe");
        } else {
            ++this.mendRetries;
            FOElytraLog.warn("\u4fee\u9798\u7fc5\u5931\u8d25\uff1a%s\uff08\u7b2c %d \u6b21\uff09", this.mendTask.failReason(), this.mendRetries);
            if (this.mendRetries >= (Integer)this.maxMendRetries.get()) {
                this.autoMend.set(false);
                FOElytraLog.err("\u8fde\u7eed %d \u6b21\u4fee\u590d\u5931\u8d25\uff0c\u5df2\u81ea\u52a8\u5173\u95ed\u300c\u542f\u7528\u81ea\u52a8\u4fee\u9798\u7fc5\u300d", this.mendRetries);
            }
        }
        this.manualTask = false;
        this.state = State.PREPARE;
    }

    private void finish(String message) {
        FOElytraLog.info("%s", message);
        if (!this.recoveryJustRan && this.state != State.RECOVER && this.supplyTask != null && this.supplyTask.recoverNeeded() && this.beginRecover("\u4efb\u52a1\u7ed3\u675f\uff1a" + message, RecoverAfter.FINISH)) {
            this.pendingFinishMessage = message;
            FOElytraLog.warn("\u4efb\u52a1\u7ed3\u675f\uff1a\u5148\u628a\u653e\u4e0b\u7684\u6f5c\u5f71\u76d2/\u672b\u5f71\u7bb1\u6536\u56de\u6765\u518d\u6536\u5c3e", new Object[0]);
            return;
        }
        this.recoveryJustRan = false;
        this.state = State.DONE;
        this.releaseEverything();
        if (((Boolean)this.logoutOnArrive.get()).booleanValue()) {
            this.disconnect(message);
            return;
        }
        if (((Boolean)this.disableOnFinish.get()).booleanValue() && this.isActive()) {
            this.toggle();
        }
    }

    private void fail(String reason) {
        if (this.state == State.FAILED) {
            return;
        }
        if (!this.recoveryJustRan && this.state != State.RECOVER && this.supplyTask != null && this.supplyTask.recoverNeeded() && this.beginRecover("\u4efb\u52a1\u5931\u8d25\uff1a" + reason, RecoverAfter.FAIL)) {
            this.pendingFailReason = reason;
            FOElytraLog.warn("\u4efb\u52a1\u5931\u8d25\uff08%s\uff09\uff1a\u5148\u628a\u653e\u4e0b\u7684\u6f5c\u5f71\u76d2/\u672b\u5f71\u7bb1\u6536\u56de\u6765\uff0c\u518d\u8d70\u5931\u8d25\u6536\u5c3e", reason);
            return;
        }
        this.recoveryJustRan = false;
        this.failReason = reason;
        FOElytraLog.err("\u4efb\u52a1\u5931\u8d25\uff1a%s", reason);
        FOElytraLog.detail("\u5931\u8d25\u8bca\u65ad\uff1a\u539f\u56e0 %s\uff5c\u72b6\u6001 %s\uff5c\u8865\u7ed9\u9636\u6bb5 %s\uff5c\u4f4d\u7f6e %s\uff5c\u5730\u9762 %s\uff5c\u6ed1\u7fd4 %s\uff5c\u70df\u82b1 %d \u53d1\uff5cBaritone %s", reason, this.state.name(), this.supplyFailPhase ? "\u662f" : "\u5426", this.mc.player == null ? "-" : String.format("%d %d %d", this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ()), this.mc.player != null && this.mc.player.isOnGround() ? "\u662f" : "\u5426", this.mc.player != null && this.mc.player.isGliding() ? "\u662f" : "\u5426", this.mc.player == null ? 0 : ItemHelper.countInHotbar((PlayerEntity)this.mc.player, Items.FIREWORK_ROCKET), BaritoneHook.isFlying() ? "\u63a5\u7ba1\u4e2d" : "\u672a\u63a5\u7ba1");
        BaritoneHook.stop();
        this.state = State.FAILED;
        this.releaseEverything();
        boolean logout = (Boolean)this.logoutOnFailure.get() != false && (!this.supplyFailPhase || (Boolean)this.logoutOnSupplyFail.get() != false) && !this.suppressLogout;
        this.supplyFailPhase = false;
        this.suppressLogout = false;
        try {
            FOElytraLog.snapshot(reason, 200, 0);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("fail snapshot", t);
        }
        if (logout) {
            this.disconnect(reason);
            return;
        }
        if (((Boolean)this.disableOnFinish.get()).booleanValue() && this.isActive()) {
            this.toggle();
        }
    }

    private void failSupply(String reason) {
        this.supplyFailPhase = true;
        this.fail(reason);
    }

    private void failNoLogout(String reason) {
        this.suppressLogout = true;
        try {
            this.fail(reason);
        }
        finally {
            if (this.state != State.RECOVER) {
                this.suppressLogout = false;
            }
        }
    }

    private void releaseEverything() {
        PlayerAction.releaseAll();
        this.resetTerrainState();
        PlayerAction.restoreHeldKeys();
        PlayerAction.clearStuckSneak();
        if (this.lava != null) {
            this.lava.release(this.mc);
        }
        if (this.lavaPredictor != null) {
            this.lavaPredictor.release(this.mc);
        }
        this.eat.stop();
        this.abortChildTasks();
    }

    private void abortChildTasks() {
        try {
            if (this.supplyTask != null && this.supplyTask.isRunning()) {
                this.supplyTask.abort("\u4efb\u52a1\u7ed3\u675f");
            }
            if (this.mendTask != null && this.mendTask.isRunning()) {
                this.mendTask.abort("\u4efb\u52a1\u7ed3\u675f");
            }
        }
        catch (Throwable t) {
            LOG.warn("abortChildTasks \u5931\u8d25", t);
        }
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.eat.stop();
    }

    public State travelState() {
        return this.state;
    }

    public String failReason() {
        return this.failReason;
    }

    public BlockPos currentTarget() {
        return this.segmentTarget;
    }

    public Needs currentNeeds() {
        return this.supplyTask == null ? new Needs() : this.supplyTask.needs();
    }

    public static enum Mode {
        Waypoints("\u822a\u70b9"),
        SingleTarget("\u5355\u4e00\u76ee\u6807"),
        Direction("\u65b9\u5411");


        private final String label;

        Mode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public static enum State {
        IDLE("\u7a7a\u95f2"),
        PREPARE("\u51c6\u5907"),
        TAKEOFF("\u8d77\u98de"),
        FLYING("\u98de\u884c\u4e2d"),
        LANDING("\u964d\u843d"),
        SUPPLY("\u8865\u7ed9"),
        MEND("\u4fee\u590d"),
        RECOVER("\u6062\u590d"),
        DONE("\u5b8c\u6210"),
        FAILED("\u5931\u8d25");


        private final String label;

        State(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private static enum TakeoffPhase {
        INIT,
        LAUNCH,
        CLEAR_HEAD,
        FLY_TO_OPEN,
        ASCEND,
        WAIT_ARRIVE;

    }

    private static enum RecoverAfter {
        PREPARE,
        FAIL,
        FINISH,
        IDLE;

    }

    private final class RecoverRunner {
        private RecoverRunner() {
        }

        @EventHandler
        public void onTick(TickEvent.Pre event) {
            if (!AutoElytraFlight.this.recoverArmed) {
                AutoElytraFlight.this.stopRecoverRunner();
                return;
            }
            if (AutoElytraFlight.this.isActive()) {
                AutoElytraFlight.this.stopRecoverRunner();
                return;
            }
            if (((AutoElytraFlight)AutoElytraFlight.this).mc.player == null || ((AutoElytraFlight)AutoElytraFlight.this).mc.world == null) {
                FOElytraLog.warn("\u4e16\u754c/\u73a9\u5bb6\u6ca1\u4e86\uff0c\u5f52\u4f4d\u63d0\u524d\u7ed3\u675f\uff08\u8fd8\u6ca1\u6536\u56de\u7684\u4e1c\u897f\u8bf7\u81ea\u5df1\u62ff\uff09", new Object[0]);
                AutoElytraFlight.this.stopRecoverRunner();
                return;
            }
            AutoElytraFlight.this.recoverTick();
        }
    }
}

