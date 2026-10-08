package com.fo.addon.elytra.modules;

import com.fo.addon.elytra.FOElytraModule;
import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.BlockBreaker;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import com.fo.addon.elytra.core.PlayerAction;
import com.fo.addon.elytra.core.SettingHelper;
import com.fo.addon.elytra.core.TaskStatus;
import com.fo.addon.elytra.core.TrialChamberLocator;
import com.fo.addon.elytra.core.VaultDisplay;
import com.fo.addon.elytra.core.VaultMarks;
import com.fo.addon.elytra.core.VaultOpener;
import com.fo.addon.elytra.modules.AutoElytraFlight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class AutoOminousVault
extends FOElytraModule {
    private static final int SCAN_BUDGET_PER_TICK = 6000;
    private static final int SCAN_Y_MIN = -40;
    private static final int SCAN_Y_MAX = 16;
    private static final int SCAN_FALLBACK_HALF = 24;
    private static final int SPAWNER_CHECK_BUDGET_PER_TICK = 1;
    private static final int DIG_STALL_MAX_TICKS = 400;
    private static final int LANDING_WAIT_MAX_TICKS = 400;
    private static final int SCAN_PROGRESS_LOG_TICKS = 40;
    private static final int REPATH_TICKS = 100;
    private static final int DISPLAY_SYNC_MIN_WAIT = 20;
    private static final int SCREEN_HOLD_MAX_TICKS = 200;
    private final SettingGroup sgTarget;
    private final SettingGroup sgFind;
    private final SettingGroup sgDisplay;
    private final SettingGroup sgOpen;
    private final SettingGroup sgLocate;
    private final SettingGroup sgTravel;
    private final SettingGroup sgDig;
    private final SettingGroup sgStop;
    private final SettingGroup sgDebug;
    private final ItemListSetting targetItems;
    private final StringSetting targetEnchants;
    private final BoolSetting stopOnTarget;
    private final BoolSetting alsoNormal;
    private final IntSetting searchRadius;
    private final BoolSetting avoidSpawner;
    private final IntSetting spawnerAvoidRadius;
    private final EnumSetting<DisplayMode> displayMode;
    private final ItemListSetting displayItems;
    private final StringSetting displayEnchants;
    private final BoolSetting openWhenUnknown;
    private final IntSetting displayWaitTicks;
    private final DoubleSetting openDistance;
    private final IntSetting collectTicks;
    private final IntSetting maxPerChamber;
    private final IntSetting actionDelay;
    private final EnumSetting<LocateMode> locateMode;
    private final StringSetting worldSeed;
    private final IntSetting seedRings;
    private final StringListSetting coordList;
    private final IntSetting integratedSearchRadius;
    private final BoolSetting startFly;
    private final BoolSetting autoClose;
    private final IntSetting maxChambers;
    private final IntSetting arriveRadius;
    private final IntSetting digArriveRadius;
    private final IntSetting approachTimeoutSec;
    private final IntSetting takeoffMinY;
    private final IntSetting climbTimeoutSec;
    private final IntSetting travelTimeoutSec;
    private final BoolSetting disableTravelOnArrive;
    private final BoolSetting autoDig;
    private final IntSetting digY;
    private final IntSetting maxDig;
    private final DoubleSetting digAbortHealth;
    private final ItemListSetting foodItems;
    private final BoolSetting stopWhenNoFood;
    private final BoolSetting noKeyAutoClose;
    private final BoolSetting talkInChat;
    private final BoolSetting hudInfo;
    private final BoolSetting verboseLog;
    private final BoolSetting debugMessages;
    private Phase phase;
    private Phase lastPhase;
    private int phaseTicks;
    private String failReason;
    private final TrialChamberLocator locator;
    private final Set<String> usedTargets;
    private int chambersVisited;
    private int targetX;
    private int targetZ;
    private String targetNote;
    private boolean integratedSearchStarted;
    private String locateFail;
    private boolean scanning;
    private int scanOriginX;
    private int scanOriginY;
    private int scanOriginZ;
    private int scanRadius;
    private int scanDx;
    private int scanDyRel;
    private int scanDz;
    private int scanDyMin;
    private int scanDyMax;
    private long scanDone;
    private long scanTotal;
    private long scanChecked;
    private long scanSkippedUnloaded;
    private int scanTicks;
    private int scanChunkX;
    private int scanChunkZ;
    private boolean scanChunkLoadedFlag;
    private final List<BlockPos> pendingCandidates;
    private final Set<Long> pendingKeys;
    private final List<BlockPos> candidates;
    private int filterIndex;
    private int filteredSpawner;
    private int filteredMarked;
    private final Set<Long> skipped;
    private BlockPos current;
    private String displayDesc;
    private int displayReadTicks;
    private VaultOpener vaultOpener;
    private int openedTotal;
    private int openedThisChamber;
    private int travelTicks;
    private boolean weEnabledTravel;
    private boolean prevFileVerbose;
    private boolean verboseSaved;
    private boolean warnedNoTravel;
    private int digCount;
    private int digStallTicks;
    private int airBelowTicks;
    private int landingWaitTicks;
    private int digDelayTicks;
    private int surfaceYCache;
    private int screenWaitTicks;
    private int gotoResend;

    public AutoOminousVault() {
        super("FO \u81ea\u52a8\u4e0d\u7965\u5b9d\u5e93", "\u627e\u4e0d\u7965\u5b9d\u5e93\u2192\u907f\u5f00\u5237\u602a\u7b3c\u2192\u7528\u4e0d\u7965\u94a5\u5319\u5f00\u2192\u6807\u8bb0\u5df2\u5f00\uff1b\u672c\u5bc6\u5ba4\u5237\u5b8c\u81ea\u52a8\u6362\u4e0b\u4e00\u4e2a\uff0c\u94a5\u5319\u6216\u98df\u7269\u7528\u5b8c\u5373\u505c\u3002", "ominous-vault", "vault", "aov");
        this.sgTarget = this.settings.createGroup("\u76ee\u6807");
        this.sgFind = this.settings.createGroup("\u5bfb\u627e\u5b9d\u5e93");
        this.sgDisplay = this.settings.createGroup("\u5c55\u793a\u7269\u7b5b\u9009");
        this.sgOpen = this.settings.createGroup("\u5f00\u5b9d\u5e93");
        this.sgLocate = this.settings.createGroup("\u5b9a\u4f4d\u8bd5\u70bc\u5927\u5385");
        this.sgTravel = this.settings.createGroup("\u98de\u884c");
        this.sgDig = this.settings.createGroup("\u4e0b\u964d\u8fdb\u5165");
        this.sgStop = this.settings.createGroup("\u505c\u6b62\u6761\u4ef6");
        this.sgDebug = this.settings.createGroup("\u8c03\u8bd5");
        this.targetItems = SettingHelper.items(this.sgTarget, "\u76ee\u6807\u6218\u5229\u54c1", "\u5f00\u51fa\u6765\u7684\u4e1c\u897f\u547d\u4e2d\u8fd9\u91cc\u4efb\u610f\u4e00\u9879\u5c31\u7b97\u8fd9\u6b21\u6709\u6536\u76ca\uff0c\u9ed8\u8ba4\u300c\u6c89\u91cd\u6838\u5fc3 + \u9644\u9b54\u91d1\u82f9\u679c\u300d\u3002", List.of(Items.HEAVY_CORE, Items.ENCHANTED_GOLDEN_APPLE), false);
        this.targetEnchants = SettingHelper.string(this.sgTarget, "\u76ee\u6807\u9b54\u5492\uff08\u9644\u9b54\u4e66\uff09", "\u6309\u9b54\u5492 ID \u5224\u5b9a\u9644\u9b54\u4e66\uff0c\u4f8b\u5982 wind_burst\uff08\u98ce\u7206\uff09\u3002\u591a\u4e2a\u7528\u9017\u53f7\u9694\u5f00\uff0c\u7559\u7a7a\u5c31\u4e0d\u5224\u3002", "wind_burst");
        this.stopOnTarget = SettingHelper.bool(this.sgTarget, "\u547d\u4e2d\u76ee\u6807\u5c31\u6536\u5de5", "\u5f00\u5230\u76ee\u6807\u6218\u5229\u54c1\u5c31\u7ed3\u675f\u6574\u4e2a\u4efb\u52a1\uff08\u5173\u6389 = \u628a\u672c\u5bc6\u5ba4\u80fd\u5f00\u7684\u90fd\u5f00\u5b8c\u518d\u8bf4\uff09\u3002", true);
        this.alsoNormal = SettingHelper.bool(this.sgTarget, "\u4e5f\u5f00\u666e\u901a\u5b9d\u5e93", "\u6253\u5f00\u540e\u666e\u901a\u5b9d\u5e93\u4e5f\u4f1a\u88ab\u9009\u4e2d\uff08\u7528\u666e\u901a\u8bd5\u70bc\u94a5\u5319\uff09\u3002\u9ed8\u8ba4\u53ea\u5f00\u4e0d\u7965\u5b9d\u5e93 \u2014\u2014 \u6c89\u91cd\u6838\u5fc3\u53ea\u5728\u5b83\u7684\u72ec\u6709\u6c60\u91cc\u3002", false);
        this.searchRadius = SettingHelper.int_(this.sgFind, "\u5b9d\u5e93\u641c\u7d22\u534a\u5f84\uff08\u683c\uff09", "\u4ee5\u4f60\u4e3a\u4e2d\u5fc3\u3001\u5728\u5df2\u52a0\u8f7d\u533a\u5757\u91cc\u627e\u5b9d\u5e93\u7684\u6c34\u5e73\u534a\u5f84\u3002\u8bd5\u70bc\u5bc6\u5ba4\u5f88\u5927\uff0c\u592a\u5c0f\u4f1a\u300c\u660e\u660e\u5728\u5bc6\u5ba4\u91cc\u5374\u627e\u4e0d\u5230\u5e93\u300d\u3002", 96, 16, 192);
        this.avoidSpawner = SettingHelper.bool(this.sgFind, "\u7ed5\u8fc7\u8bd5\u70bc\u5237\u602a\u7b3c", "\u53ea\u6311\u79bb\u8bd5\u70bc\u5237\u602a\u7b3c\u591f\u8fdc\u7684\u5b9d\u5e93\uff0c\u4e5f\u4e0d\u5728\u5237\u602a\u7b3c\u65c1\u8fb9\u5f00\u5e93\u3002\u8d70\u8fc7\u53bb\u7684\u8def\u662f Baritone \u5bfb\u8def\uff0c\u53ef\u80fd\u4f1a\u8def\u8fc7\u5b83\u3002", true);
        this.spawnerAvoidRadius = SettingHelper.int_(this.sgFind, "\u79bb\u5237\u602a\u7b3c\u81f3\u5c11\u8fd9\u4e48\u8fdc\uff08\u683c\uff09", "\u5019\u9009\u5b9d\u5e93\u79bb\u8bd5\u70bc\u5237\u602a\u7b3c\u5c0f\u4e8e\u8fd9\u4e2a\u8ddd\u79bb\u5c31\u8df3\u8fc7\u3002\u9ed8\u8ba4 12 \u683c\uff0c\u8c03\u5927\u4f1a\u8df3\u8fc7\u66f4\u591a\u5b9d\u5e93\u3002", 12, 4, 48);
        this.displayMode = SettingHelper.enum_(this.sgDisplay, "\u5c55\u793a\u7269\u7b5b\u9009\u65b9\u5f0f", "\u5c55\u793a\u7269\u548c\u5b9e\u9645\u6389\u843d\u65e0\u5173\uff0c\u9ed8\u8ba4\u300c\u53ea\u8bb0\u5f55\u300d\u7167\u5f00\uff1b\u6539\u6210\u300c\u5fc5\u987b\u547d\u4e2d\u624d\u5f00\u300d\u4f1a\u5c11\u5f00\u5f88\u591a\u5e93\u3002", DisplayMode.LOG);
        this.displayItems = SettingHelper.items(this.sgDisplay, "\u5c55\u793a\u7269 \u00b7 \u76ee\u6807\u7269\u54c1", "\u300c\u5fc5\u987b\u547d\u4e2d\u624d\u5f00\u300d/\u300c\u53ea\u8bb0\u5f55\u300d\u6a21\u5f0f\u4e0b\uff0c\u8ba4\u5b9a\u300c\u547d\u4e2d\u300d\u7684\u7269\u54c1\u6e05\u5355\uff1b\u9ed8\u8ba4\u6c89\u91cd\u6838\u5fc3 + \u9644\u9b54\u91d1\u82f9\u679c\u3002", List.of(Items.HEAVY_CORE, Items.ENCHANTED_GOLDEN_APPLE), false);
        this.displayEnchants = SettingHelper.string(this.sgDisplay, "\u5c55\u793a\u7269 \u00b7 \u76ee\u6807\u9b54\u5492", "\u5c55\u793a\u7269\u662f\u9644\u9b54\u4e66\u65f6\u6309\u9b54\u5492 ID \u5224\u5b9a\uff08\u4f8b\u5982 wind_burst\uff09\u3002\u591a\u4e2a\u7528\u9017\u53f7\u9694\u5f00\u3002", "wind_burst");
        this.openWhenUnknown = SettingHelper.bool(this.sgDisplay, "\u8bfb\u4e0d\u5230\u5c55\u793a\u7269\u4e5f\u5f00", "\u8bfb\u4e0d\u5230\u5c55\u793a\u7269\u65f6\u7167\u5f00\uff08\u9ed8\u8ba4\uff09\uff1b\u5173\u6389\u5c31\u5f53\u6210\u4e0d\u547d\u4e2d\uff0c\u6362\u4e0b\u4e00\u4e2a\u3002", true);
        this.displayWaitTicks = SettingHelper.int_(this.sgDisplay, "\u8bfb\u5c55\u793a\u7269\u524d\u7b49\u591a\u4e45\uff08tick\uff09", "\u7ad9\u5b9a\u540e\u7b49\u8fd9\u4e48\u4e45\u518d\u8bfb\u5c55\u793a\u7269\uff0c\u670d\u52a1\u7aef\u6bcf 20 tick \u624d\u5237\u65b0\u4e00\u6b21\u3002\u9ed8\u8ba4 30\u3002", 30, 20, 100);
        this.openDistance = SettingHelper.double_(this.sgOpen, "\u5f00\u5e93\u8ddd\u79bb\uff08\u683c\uff09", "\u7ad9\u8d77\u6765\u5230\u8fd9\u4e2a\u6c34\u5e73\u8ddd\u79bb\u5185\u624d\u53f3\u952e\uff08\u522b\u9876\u7740\u65b9\u5757\u8d70\uff09\u3002\u5b9d\u5e93\u7684\u6fc0\u6d3b\u534a\u5f84\u662f 4.0 \u683c\uff083D \u542b Y\uff09\uff0c\u522b\u8c03\u592a\u5927\u3002", 3.0, 1.0, 6.0);
        this.collectTicks = SettingHelper.int_(this.sgOpen, "\u5f00\u5b8c\u540e\u6536\u96c6\u591a\u4e45\uff08tick\uff09", "\u4e0d\u7965\u5b9d\u5e93\u662f\u300c\u4e00\u79d2\u55b7\u4e00\u4ef6\u3001\u6700\u591a 1+1~3 \u4ef6\u300d\uff0c\u5185\u90e8\u6709\u4fdd\u5b88\u4e0b\u9650\uff0c\u8c03\u592a\u5c0f\u4f1a\u6f0f\u5224\u6700\u540e\u4e00\u4ef6\uff08\u5f80\u5f80\u662f\u76ee\u6807\u7269\uff09\u3002", 120, 20, 600);
        this.maxPerChamber = SettingHelper.int_(this.sgOpen, "\u4e00\u4e2a\u5bc6\u5ba4\u6700\u591a\u5f00\u51e0\u4e2a", "\u5728\u4e00\u4e2a\u8bd5\u70bc\u5927\u5385\u91cc\u6700\u591a\u5f00\u8fd9\u4e48\u591a\u4e2a\u5b9d\u5e93\uff08\u6bcf\u4e2a\u90fd\u8981\u4e00\u628a\u94a5\u5319\uff09\uff0c\u591f\u4e86\u5c31\u6362\u4e0b\u4e00\u4e2a\u5bc6\u5ba4/\u6536\u5de5\u3002", 8, 1, 64);
        this.actionDelay = SettingHelper.int_(this.sgOpen, "\u52a8\u4f5c\u95f4\u9694\uff08tick\uff09", "\u4ea4\u7ed9\u5f00\u5e93\u72b6\u6001\u673a\u7684\u52a8\u4f5c\u8282\u6d41\uff0c\u5361\u670d/\u9ad8\u5ef6\u8fdf\u65f6\u8c03\u5927\u4e00\u70b9\u66f4\u7a33\u3002", 4, 0, 40);
        this.locateMode = SettingHelper.enum_(this.sgLocate, "\u5b9a\u4f4d\u65b9\u5f0f", "\u600e\u4e48\u627e\u8bd5\u70bc\u5927\u5385\u3002\u9ed8\u8ba4\u300c\u81ea\u52a8\u300d\uff1a\u79cd\u5b50\u63a8\u7b97 \u2192 \u5750\u6807\u5217\u8868 \u2192 \u8bfb\u5730\u56fe \u2192 \u5355\u673a\u641c\u7d22\uff0c\u6709\u4ec0\u4e48\u7528\u4ec0\u4e48\u3002", LocateMode.AUTO);
        this.worldSeed = SettingHelper.string(this.sgLocate, "\u4e16\u754c\u79cd\u5b50\uff08\u624b\u586b\uff09", "\u586b /seed \u663e\u793a\u7684\u90a3\u4e2a\u6570\u5b57\uff0c\u5ba2\u6237\u7aef\u76f4\u63a5\u7b97\u51fa\u8bd5\u70bc\u5927\u5385\u5019\u9009\u70b9\uff0c\u591a\u4eba\u670d\u52a1\u5668\u4e5f\u80fd\u7528\u3002\u7559\u7a7a\u5c31\u8df3\u8fc7\u79cd\u5b50\u63a8\u7b97\u3002", "");
        this.seedRings = SettingHelper.int_(this.sgLocate, "\u79cd\u5b50\u63a8\u7b97\u5708\u6570", "\u4ee5\u4f60\u4e3a\u4e2d\u5fc3\u5f80\u5916\u63a8\u51e0\u5708 region \u53bb\u627e\u5bc6\u5ba4\uff081 \u5708 = 34\u00d734 \u533a\u5757\uff09\u3002\u5708\u6570\u8d8a\u5927\u80fd\u7b97\u5230\u7684\u8d8a\u8fdc\uff0c\u7eaf\u8ba1\u7b97\u4e0d\u5403\u6027\u80fd\u3002", 2, 1, 8);
        this.coordList = SettingHelper.stringList(this.sgLocate, "\u5750\u6807\u5217\u8868", "\u4e00\u884c\u4e00\u4e2a\u5750\u6807\uff0c\u683c\u5f0f x,z\uff08\u4e5f\u53ef\u7528\u7a7a\u683c\u6216\u4e2d\u6587\u9017\u53f7\uff09\u3002\u6a21\u5757\u4f1a\u6311\u79bb\u4f60\u6700\u8fd1\u3001\u8fd9\u6b21\u6ca1\u53bb\u8fc7\u7684\u90a3\u4e2a\u3002", List.of());
        this.integratedSearchRadius = SettingHelper.int_(this.sgLocate, "\u5355\u673a\u79cd\u5b50\u641c\u7d22\u534a\u5f84\uff08\u533a\u5757\uff09", "\u53ea\u5728\u5355\u4eba\u5b58\u6863\u6709\u6548\uff1a\u7528\u6574\u5408\u670d\u52a1\u7aef\u7684\u771f\u5b9e\u4e16\u754c\u751f\u6210\u5668\u4ece\u4f60\u5f53\u524d\u4f4d\u7f6e\u5f80\u5916\u641c\uff08\u5185\u90e8\u4f1a\u5939\u5230 200 \u533a\u5757\uff09\u3002", 200, 16, 200);
        this.startFly = SettingHelper.bool(this.sgLocate, "\u542f\u52a8\u65f6\u5c31\u98de\u5f80\u6700\u8fd1\u7684\u8bd5\u70bc\u5927\u5385", "\u6253\u5f00\u6a21\u5757\u5c31\u5148\u5b9a\u4f4d\u5e76\u98de\u8fc7\u53bb\uff1b\u5173\u6389 = \u4f60\u81ea\u5df1\u5df2\u7ecf\u5728\u5bc6\u5ba4\u91cc\uff0c\u76f4\u63a5\u5f00\u59cb\u627e\u5b9d\u5e93\u3002", true);
        this.autoClose = SettingHelper.bool(this.sgLocate, "\u4efb\u52a1\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed", "\u5f00\uff08\u9ed8\u8ba4\uff09\uff1a\u4e00\u4e2a\u5bc6\u5ba4\u5237\u5b8c\u5c31\u5173\u6389\u6a21\u5757\u6536\u5de5\uff1b\u5173\uff1a\u94a5\u5319\u6ca1\u7528\u5b8c\u5c31\u7ee7\u7eed\u722c\u4e0a\u5730\u8868\u98de\u4e0b\u4e00\u4e2a\u5bc6\u5ba4\u3002", true);
        this.maxChambers = SettingHelper.int_(this.sgLocate, "\u6700\u591a\u6362\u51e0\u4e2a\u5bc6\u5ba4", "\u300c\u4efb\u52a1\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed\u300d\u5173\u6389\u65f6\u624d\u6709\u7528\uff1a\u6700\u591a\u8fde\u7eed\u6362\u8fd9\u4e48\u591a\u4e2a\u5bc6\u5ba4\u5c31\u5f3a\u5236\u6536\u5de5\uff0c\u9632\u6b62\u6302\u673a\u4e71\u98de\u4e00\u665a\u4e0a\u3002", 5, 1, 20);
        this.arriveRadius = SettingHelper.int_(this.sgTravel, "\u5230\u8fbe\u5224\u5b9a\u8ddd\u79bb\uff08\u683c\uff09", "\u6c34\u5e73\u8ddd\u79bb\u5c0f\u4e8e\u8fd9\u4e2a\u503c\u5c31\u7b97\u300c\u5230\u76ee\u6807\u4e0a\u7a7a\u4e86\u300d\uff0c\u5f00\u59cb\u4e0b\u964d\u3002", 64, 8, 512);
        this.digArriveRadius = SettingHelper.int_(this.sgTravel, "\u98de\u5230\u591a\u8fd1\u624d\u505c\u4e0b\u6765\u6316\uff08\u683c\uff09", "\u98de\u5230\u79bb\u76ee\u6807\u8fd9\u4e48\u8fd1\u624d\u505c\u98de\u4e0b\u964d\u3002\u9ed8\u8ba4 16 \u683c\uff0c\u8c03\u5230 4 \u4ee5\u4e0b\u4f1a\u7ed5\u5708\u3002", 16, 4, 64);
        this.approachTimeoutSec = SettingHelper.int_(this.sgOpen, "\u8d70\u5230\u5b9d\u5e93\u8d85\u65f6\uff08\u79d2\uff09", "\u8d70\u5230\u5b9d\u5e93\u7684\u65f6\u9650\uff0c\u8d85\u65f6\u5c31\u8df3\u8fc7\u5b83\u6362\u4e0b\u4e00\u4e2a\u3002\u9ed8\u8ba4 60 \u79d2\u3002", 60, 10, 600);
        this.takeoffMinY = SettingHelper.int_(this.sgTravel, "\u6700\u4f4e\u8d77\u98de Y", "Y \u9ad8\u4e8e\u8fd9\u4e2a\u503c\u5c31\u8ba4\u4e3a\u300c\u80fd\u8d77\u98de\u4e86\u300d\uff08\u901a\u5e38\u5730\u9762\u5728 60 \u4ee5\u4e0a\uff09\u3002\u722c\u4e0a\u5730\u8868\u5c31\u770b\u5b83\u548c\u300c\u89c1\u5929\u300d\u4e24\u4e2a\u6761\u4ef6\u3002", 60, -64, 320);
        this.climbTimeoutSec = SettingHelper.int_(this.sgTravel, "\u722c\u4e0a\u5730\u8868\u8d85\u65f6\uff08\u79d2\uff09", "\u8ba9 Baritone \u5f80\u4e0a\u8d70\u8fd9\u4e48\u4e45\u8fd8\u4e0a\u4e0d\u53bb\uff08\u88ab\u5835\u6b7b/\u627e\u4e0d\u5230\u8def\uff09\u5c31\u62a5\u5931\u8d25\u5e76\u8bf4\u660e\u539f\u56e0\uff0c\u7edd\u4e0d\u65e0\u9650\u7b49\u3002", 300, 30, 3600);
        this.travelTimeoutSec = SettingHelper.int_(this.sgTravel, "\u98de\u884c\u8d85\u65f6\uff08\u79d2\uff09", "\u98de\u8fd9\u4e48\u4e45\u8fd8\u6ca1\u5230\u5c31\u653e\u5f03\u98de\u884c\u3001\u76f4\u63a5\u8fdb\u4e0b\u964d\u9636\u6bb5\u3002", 900, 30, 7200);
        this.disableTravelOnArrive = SettingHelper.bool(this.sgTravel, "\u5230\u8fbe\u540e\u5173\u6389\u8dd1\u56fe\u6a21\u5757", "\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u662f\u501f\u6765\u7528\u7684\uff1a\u5230\u8fbe\u540e\u6309\u8fd9\u4e2a\u5f00\u5173\u51b3\u5b9a\u8981\u4e0d\u8981\u8fd8\u56de\u53bb\uff08\u5173\u6389\u5b83 = \u8ba9\u5b83\u7ee7\u7eed\u5f00\u7740\uff09\u3002", true);
        this.autoDig = SettingHelper.bool(this.sgDig, "\u81ea\u52a8\u6316\u7ad6\u4e95\u4e0b\u964d", "\u5230\u76ee\u6807\u4e0a\u7a7a\u540e\u81ea\u5df1\u6316\u4e00\u6761 1\u00d71 \u7ad6\u4e95\u964d\u5230\u5bc6\u5ba4\u5c42\u3002\u5173\u6389 = \u4f60\u81ea\u5df1\u628a\u89d2\u8272\u5e26\u5230\u5bc6\u5ba4\u5c42\uff08\u6a21\u5757\u53ea\u505a\u627e\u5e93+\u5f00\u5e93\uff09\u3002", true);
        this.digY = SettingHelper.int_(this.sgDig, "\u4e0b\u964d\u5230 Y", "\u6316\u5230\u8fd9\u4e2a\u9ad8\u5ea6\u5c31\u505c\u3002\u8bd5\u70bc\u5927\u5385\u591a\u5728 Y=-20~0\u3002", -20, -64, 320);
        this.maxDig = SettingHelper.int_(this.sgDig, "\u5355\u6b21\u6700\u591a\u6316\u591a\u5c11\u683c", "\u4e00\u6b21\u4e0b\u964d\u6700\u591a\u6316\u8fd9\u4e48\u591a\u683c\uff0c\u8d85\u4e86\u5c31\u505c\u4e0b\u62a5\u539f\u56e0\u3002", 200, 1, 400);
        this.digAbortHealth = SettingHelper.double_(this.sgDig, "\u4e0b\u964d\u65f6\u8840\u91cf\u4f4e\u4e8e\u591a\u5c11\u5c31\u505c", "\u8840\u91cf\u6389\u5230\u8fd9\u4e2a\u503c\u5c31\u505c\u624b\u4fdd\u547d\u3002", 6.0, 1.0, 20.0);
        this.foodItems = SettingHelper.items(this.sgStop, "\u7b97\u4f5c\u300c\u98df\u7269\u300d\u7684\u7269\u54c1", "\u8fd9\u4e9b\u7269\u54c1\u5168\u7528\u5b8c\u5c31\u7b97\u98df\u7269\u7528\u5b8c\uff0c\u53ea\u6570\u80cc\u5305\u3001\u4e0d\u4f1a\u81ea\u52a8\u5403\u3002\u9ed8\u8ba4\u7a7a\u6e05\u5355 = \u4e0d\u68c0\u67e5\uff0c\u5224\u5b9a\u53ea\u5728\u5f00\u5b8c\u5e93\u4e4b\u540e\u3002", List.of(), true);
        this.stopWhenNoFood = SettingHelper.bool(this.sgStop, "\u98df\u7269\u7528\u5b8c\u5c31\u505c", "\u5f00\u7740 = \u98df\u7269\u6570\u91cf\u53d8\u6210 0 \u65f6\u7ed3\u675f\u4efb\u52a1\uff08\u6309\u300c\u4efb\u52a1\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed\u300d\u51b3\u5b9a\u5173\u4e0d\u5173\u6a21\u5757\uff09\u3002", true);
        this.noKeyAutoClose = SettingHelper.bool(this.sgStop, "\u6ca1\u94a5\u5319\u65f6\u81ea\u52a8\u5173\u95ed", "\u80cc\u5305\u91cc\u6ca1\u94a5\u5319\u65f6\u804a\u5929\u680f\u63d0\u9192\u5e76\u5173\u6389\u6a21\u5757\uff08\u9ed8\u8ba4\u5f00\uff09\uff1b\u5173\u6389\u5c31\u53ea\u63d0\u9192\u3001\u4e0d\u5173\u6a21\u5757\u3002", true);
        this.talkInChat = SettingHelper.bool(this.sgStop, "\u5173\u952e\u8282\u70b9\u53d1\u804a\u5929\u680f\u63d0\u793a", "\u5f00\u5e93\u7684\u5173\u952e\u8282\u70b9\uff08\u5f00\u59cb\u5f00\u5e93\u3001\u5f00\u5b8c\u3001\u6362\u5bc6\u5ba4\u7b49\uff09\u53d1\u804a\u5929\u680f\uff0c\u5931\u8d25\u539f\u56e0\u548c\u300c\u6ca1\u94a5\u5319\u505c\u6b62\u300d\u59cb\u7ec8\u4f1a\u53d1\u3002\u98de\u884c\u9636\u6bb5\u7684\u72b6\u6001\u76d1\u63a7\u3001\u8d77\u98de\u3001\u5230\u8fbe\u63d0\u793a\u7531\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u81ea\u5df1\u7684\u8bbe\u7f6e\u63a7\u5236\u3002", true);
        this.hudInfo = SettingHelper.bool(this.sgDebug, "HUD \u72b6\u6001", "\u5728 HUD \u4e0a\u663e\u793a\u300c\u9636\u6bb5 + \u5173\u952e\u6570\u5b57\u300d\u3002", true);
        this.verboseLog = SettingHelper.bool(this.sgDebug, "\u8be6\u7ec6\u6587\u4ef6\u65e5\u5fd7", "\u628a\u6bcf\u4e2a\u5224\u65ad\u90fd\u5199\u8fdb icehack-*.log\uff08\u6392\u67e5\u95ee\u9898\u7528\uff09\u3002", true);
        this.debugMessages = SettingHelper.bool(this.sgDebug, "\u8c03\u8bd5\u8f93\u51fa\u5230\u804a\u5929\u680f", "\u628a FOElytraLog.debug \u7684\u5185\u5bb9\u4e5f\u53d1\u5230\u804a\u5929\u680f\u3002\u672c\u6a21\u5757\u6ca1\u6709\u8c03\u8bd5\u7ea7\u8f93\u51fa\uff0c\u8fd9\u4e2a\u5f00\u5173\u53ea\u5bf9\u5176\u4ed6\u6a21\u5757\u6709\u6548\u3002", false);
        this.phase = Phase.IDLE;
        this.lastPhase = Phase.IDLE;
        this.failReason = "";
        this.locator = new TrialChamberLocator();
        this.usedTargets = new HashSet<String>();
        this.targetNote = "";
        this.locateFail = "";
        this.scanChunkX = Integer.MIN_VALUE;
        this.scanChunkZ = Integer.MIN_VALUE;
        this.pendingCandidates = new ArrayList<BlockPos>();
        this.pendingKeys = new HashSet<Long>();
        this.candidates = new ArrayList<BlockPos>();
        this.skipped = new HashSet<Long>();
        this.displayDesc = "\uff08\u8fd8\u6ca1\u770b\uff09";
        this.travelTicks = 0;
        this.surfaceYCache = Integer.MIN_VALUE;
    }

    public void onActivate() {
        FOElytraLog.fileVerbose = (Boolean)this.verboseLog.get();
        if (this.mc.runDirectory != null) {
            try {
                FOElytraLog.setGameDir(this.mc.runDirectory.toPath());
                FOElytraLog.ensureOpen(this.mc.runDirectory.toPath(), 10);
            }
            catch (Throwable t) {
                LOG.error("\u6253\u5f00\u65e5\u5fd7\u6587\u4ef6\u5931\u8d25", t);
            }
        }
        this.phase = Phase.PREPARE;
        this.lastPhase = Phase.IDLE;
        this.phaseTicks = 0;
        this.failReason = "";
        this.usedTargets.clear();
        this.chambersVisited = 0;
        this.targetX = 0;
        this.targetZ = 0;
        this.targetNote = "";
        this.integratedSearchStarted = false;
        this.locateFail = "";
        this.resetScanState();
        this.skipped.clear();
        this.current = null;
        this.displayDesc = "\uff08\u8fd8\u6ca1\u770b\uff09";
        this.displayReadTicks = 0;
        this.vaultOpener = null;
        this.openedTotal = 0;
        this.openedThisChamber = 0;
        this.travelTicks = 0;
        this.weEnabledTravel = false;
        this.warnedNoTravel = false;
        this.digCount = 0;
        this.digDelayTicks = 0;
        this.surfaceYCache = Integer.MIN_VALUE;
        this.screenWaitTicks = 0;
        this.gotoResend = 0;
        FOElytraLog.info("\u81ea\u52a8\u4e0d\u7965\u5b9d\u5e93\u542f\u52a8\uff1a\u534a\u5f84 %d \u683c\uff5c%s\uff5c\u76ee\u6807 %s\uff5c\u5c55\u793a\u7269 %s\uff5c\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed %s", this.searchRadius.get(), (Boolean)this.alsoNormal.get() != false ? "\u4e0d\u7965\u5b9d\u5e93 + \u666e\u901a\u5b9d\u5e93" : "\u53ea\u5f00\u4e0d\u7965\u5b9d\u5e93", this.describeLoot(), this.displayMode.get(), (Boolean)this.autoClose.get() != false ? "\u662f" : "\u5426\uff08\u4f1a\u7ee7\u7eed\u6362\u5bc6\u5ba4\uff09");
        FOElytraLog.detail("\u2014\u2014 \u672c\u6b21\u8bbe\u7f6e \u2014\u2014", new Object[0]);
        for (SettingGroup g : this.settings) {
            for (Setting s : g) {
                FOElytraLog.detail("    %s = %s", s.name, s.get());
            }
        }
        FOElytraLog.detail("\u5df2\u8bb0\u5f55\u7684\u300c\u5df2\u6253\u5f00\u5b9d\u5e93\u300d\u6807\u8bb0\uff1a%d \u4e2a\uff08\u6587\u4ef6 %s\uff09", VaultMarks.get().count(), VaultMarks.get().filePath());
        FOElytraLog.detail("\u94a5\u5319\uff1a\u4e0d\u7965\u8bd5\u70bc\u94a5\u5319 %d \u4e2a\uff1b\u666e\u901a\u8bd5\u70bc\u94a5\u5319 %d \u4e2a\uff1b%s", this.countKey(Items.OMINOUS_TRIAL_KEY), this.countKey(Items.TRIAL_KEY), this.foodCountText());
        if (!BaritoneHook.available()) {
            this.warning("\u6ca1\u6709\u68c0\u6d4b\u5230 Baritone\uff1a\u8d70\u5230\u5b9d\u5e93\u3001\u722c\u4e0a\u5730\u8868\u90fd\u4f1a\u7528\u4e0d\u4e86\uff08\u4f1a\u76f4\u63a5\u62a5\u5931\u8d25\u539f\u56e0\uff0c\u4e0d\u4f1a\u9759\u9ed8\u5361\u4f4f\uff09\u3002", new Object[0]);
        }
        if (!TrialChamberLocator.seedSearchAvailable()) {
            FOElytraLog.detail("\u5f53\u524d\u662f\u591a\u4eba\u670d\u52a1\u5668\uff1a\u5355\u673a\u79cd\u5b50\u641c\u7d22\u4e0d\u53ef\u7528\uff0c\u60f3\u81ea\u52a8\u98de\u5f80\u4e0b\u4e00\u4e2a\u5bc6\u5ba4\u8bf7\u5728\u300c\u5b9a\u4f4d\u8bd5\u70bc\u5927\u5385 \u2192 \u4e16\u754c\u79cd\u5b50\uff08\u624b\u586b\uff09\u300d\u91cc\u586b /seed \u7684\u6570\u5b57\u3002", new Object[0]);
        }
        if (((Boolean)this.avoidSpawner.get()).booleanValue()) {
            FOElytraLog.detail("\u5df2\u5f00\u542f\u300c\u7ed5\u8fc7\u8bd5\u70bc\u5237\u602a\u7b3c\u300d\uff1a\u53ea\u5728\u79bb\u5237\u602a\u7b3c \u2265 %d \u683c\u7684\u5730\u65b9\u9009\u5e93/\u5f00\u5e93\u3002\u8d70\u8fc7\u53bb\u7684\u8def\u662f Baritone \u5bfb\u8def\uff0c\u53ef\u80fd\u4f1a\u8def\u8fc7\u5237\u602a\u7b3c\uff0c\u53ea\u80fd\u4fdd\u8bc1\u4e0d\u5728\u5b83\u65c1\u8fb9\u5f00\u5e93\u3002", this.spawnerAvoidRadius.get());
        }
    }

    public void onDeactivate() {
        this.cleanup(true);
        FOElytraLog.info("\u81ea\u52a8\u4e0d\u7965\u5b9d\u5e93\u5df2\u5173\u95ed\uff08\u9636\u6bb5 %s\uff5c\u672c\u6b21\u5171\u5f00 %d \u4e2a\u5e93\uff5c\u53bb\u8fc7 %d \u4e2a\u5bc6\u5ba4\uff09", this.phase.name(), this.openedTotal, this.chambersVisited);
        this.phase = Phase.IDLE;
    }

    public String getInfoString() {
        if (!((Boolean)this.hudInfo.get()).booleanValue() || this.mc.player == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(this.phase.name());
        if (this.current != null) {
            double d = Math.hypot(this.mc.player.getX() - ((double)this.current.getX() + 0.5), this.mc.player.getZ() - ((double)this.current.getZ() + 0.5));
            sb.append(String.format(Locale.ROOT, " \u5e93%.0fm", d));
        } else if (this.phase == Phase.CLIMB || this.phase == Phase.TRAVEL) {
            double d = Math.hypot(this.mc.player.getX() - ((double)this.targetX + 0.5), this.mc.player.getZ() - ((double)this.targetZ + 0.5));
            sb.append(String.format(Locale.ROOT, " \u5927\u5385%.0fm", d));
        }
        if (this.phase == Phase.SCAN && this.scanning) {
            sb.append(String.format(Locale.ROOT, " \u626b%d%%", (int)(100L * this.scanDone / Math.max(1L, this.scanTotal))));
        }
        sb.append(" \u5df2\u5f00").append(this.openedTotal);
        sb.append(" \u94a5").append(this.keyCount());
        return sb.toString();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!this.isActive() || this.mc.player == null || this.mc.world == null) {
            return;
        }
        boolean dbg = (Boolean)this.debugMessages.get();
        if (FOElytraLog.debugEnabled != dbg) {
            FOElytraLog.debugEnabled = dbg;
        }
        try {
            if (this.phase != this.lastPhase) {
                this.lastPhase = this.phase;
                this.phaseTicks = 0;
                FOElytraLog.detail("\u9636\u6bb5\u5207\u6362 \u2192 %s", this.phase.name());
            }
            ++this.phaseTicks;
            if (InvHelper.screenOpen() && this.phase != Phase.DONE && this.phase != Phase.FAILED) {
                if (this.screenWaitTicks++ > 200) {
                    this.fail("\u4f60\u5df2\u7ecf\u5f00\u7740\u754c\u9762\u8d85\u8fc7 10 \u79d2\u4e86\uff0c\u6211\u5148\u505c\u4e0b\uff0c\u4e0d\u786c\u5173\u4f60\u7684\u754c\u9762");
                    return;
                }
                return;
            }
            this.screenWaitTicks = 0;
            switch (this.phase.ordinal()) {
                case 1: {
                    this.prepareTick();
                    break;
                }
                case 2: {
                    this.scanTick();
                    break;
                }
                case 3: {
                    this.filterTick();
                    break;
                }
                case 4: {
                    this.approachTick();
                    break;
                }
                case 5: {
                    this.lookTick();
                    break;
                }
                case 6: {
                    this.openTick();
                    break;
                }
                case 7: {
                    this.postOpenTick();
                    break;
                }
                case 8: {
                    this.noVaultTick();
                    break;
                }
                case 9: {
                    this.climbTick();
                    break;
                }
                case 10: {
                    this.travelTick();
                    break;
                }
                case 11: {
                    this.digTick();
                    break;
                }
                case 12: 
                case 13: {
                    break;
                }
                case 0: {
                    this.phase = Phase.PREPARE;
                }
            }
        }
        catch (Throwable t) {
            this.onError("onTick", t);
            this.fail("\u5185\u90e8\u5f02\u5e38 " + t.getClass().getSimpleName() + "\uff1a" + t.getMessage());
        }
    }

    private void prepareTick() {
        if (!this.hasAnyKey()) {
            this.noKeyStop();
            return;
        }
        if (((Boolean)this.stopWhenNoFood.get()).booleanValue() && this.countFood() <= 0 && this.phaseTicks % 600 == 1) {
            FOElytraLog.warn("\u300c%s\u300d\u73b0\u5728\u662f 0 \u4e2a\uff08\u53ea\u63d0\u9192\uff0c\u4e0d\u505c\u624b\uff09\uff1a\u8fd9\u6761\u505c\u6b62\u6761\u4ef6\u8981\u7b49\u5f00\u5b8c\u5b9d\u5e93\u624d\u751f\u6548\uff0c\u60f3\u73b0\u5728\u5c31\u505c\u8bf7\u81ea\u5df1\u5f80\u80cc\u5305\u653e\u70b9 %s", this.foodName(), this.foodName());
        }
        if (this.phaseTicks == 1) {
            this.say("\u51c6\u5907\u5c31\u7eea\uff1a%s %d \u4e2a\uff5c%s", this.keyName(), this.keyCount(), this.foodCountText());
            if (this.targetItems.get() == null || ((List)this.targetItems.get()).isEmpty()) {
                this.warning("\u300c\u76ee\u6807\u6218\u5229\u54c1\u300d\u662f\u7a7a\u7684\uff1a\u6a21\u5757\u4e0d\u4f1a\u5224\u300c\u547d\u4e2d\u300d\uff0c\u53ea\u4f1a\u8bb0\u5f55\u6bcf\u6b21\u5f00\u51fa\u4e86\u4ec0\u4e48\u3002", new Object[0]);
            }
            if (this.displayMode.get() == DisplayMode.REQUIRE) {
                this.warning("\u5df2\u542f\u7528\u300c\u5fc5\u987b\u547d\u4e2d\u624d\u5f00\u300d\uff1a\u5c55\u793a\u7269\u548c\u5b9e\u9645\u6389\u843d\u65e0\u5173\uff0c\u8fd9\u6837\u53ea\u4f1a\u5c11\u5f00\u5f88\u591a\u5b9d\u5e93\u3002", new Object[0]);
            }
        }
        if (!((Boolean)this.startFly.get()).booleanValue()) {
            FOElytraLog.detail("\u300c\u542f\u52a8\u65f6\u5c31\u98de\u5f80\u6700\u8fd1\u7684\u8bd5\u70bc\u5927\u5385\u300d\u662f\u5173\u7684\uff1a\u76f4\u63a5\u5c31\u5730\u5f00\u59cb\u627e\u5b9d\u5e93", new Object[0]);
            this.enterScan();
            return;
        }
        switch (this.tryLocate().ordinal()) {
            case 2: {
                if (this.phaseTicks % 100 != 0) break;
                FOElytraLog.detail("\u6b63\u5728\u540e\u53f0\u641c\u7d22\u8bd5\u70bc\u5927\u5385\uff08\u6574\u5408\u670d\u52a1\u7aef\u7ebf\u7a0b\uff09\u2026\u5df2\u7b49 %d \u79d2", this.phaseTicks / 20);
                break;
            }
            case 0: {
                this.goToTarget();
                break;
            }
            case 1: {
                FOElytraLog.warn("\u6ca1\u80fd\u5b9a\u4f4d\u5230\u8bd5\u70bc\u5927\u5385\uff1a%s", this.locateFail);
                FOElytraLog.warn("\u6539\u6210\u300c\u5c31\u5728\u5f53\u524d\u4f4d\u7f6e\u9644\u8fd1\u627e\u5b9d\u5e93\u300d\u3002\u8981\u662f\u672c\u6765\u4e0d\u5728\u5bc6\u5ba4\u91cc\uff0c\u8bf7\u586b\u300c\u4e16\u754c\u79cd\u5b50\u300d\u6216\u300c\u5750\u6807\u5217\u8868\u300d\u3002", new Object[0]);
                this.enterScan();
            }
        }
    }

    private void enterScan() {
        this.resetScanState();
        this.phase = Phase.SCAN;
    }

    private void resetScanState() {
        this.scanning = false;
        this.scanDone = 0L;
        this.scanTotal = 0L;
        this.scanChecked = 0L;
        this.scanSkippedUnloaded = 0L;
        this.scanTicks = 0;
        this.scanChunkX = Integer.MIN_VALUE;
        this.scanChunkZ = Integer.MIN_VALUE;
        this.scanChunkLoadedFlag = false;
        this.pendingCandidates.clear();
        this.pendingKeys.clear();
        this.candidates.clear();
        this.filterIndex = 0;
        this.filteredSpawner = 0;
        this.filteredMarked = 0;
    }

    private void scanTick() {
        if (!this.scanning) {
            this.beginScan();
        }
        ++this.scanTicks;
        int budget = 6000;
        while (budget-- > 0) {
            int x = this.scanOriginX + this.scanDx;
            int y = this.scanOriginY + this.scanDyRel;
            int z = this.scanOriginZ + this.scanDz;
            BlockPos pos = new BlockPos(x, y, z);
            ++this.scanDone;
            if (!this.chunkLoaded(x >> 4, z >> 4)) {
                ++this.scanSkippedUnloaded;
            } else {
                ++this.scanChecked;
                BlockState st = this.mc.world.getBlockState(pos);
                if (st.isOf(Blocks.VAULT)) {
                    BlockPos fixed;
                    boolean typeOk;
                    boolean ominous = Boolean.TRUE.equals(st.get((Property)VaultBlock.OMINOUS));
                    boolean bl = typeOk = ominous || (Boolean)this.alsoNormal.get() != false;
                    if (typeOk && VaultMarks.get().isMarked(pos)) {
                        ++this.filteredMarked;
                    } else if (typeOk && !this.skipped.contains(pos.asLong()) && this.pendingKeys.add((fixed = pos.toImmutable()).asLong())) {
                        this.pendingCandidates.add(fixed);
                    }
                }
            }
            if (this.scanAdvance()) continue;
            this.finishScan();
            return;
        }
        if (this.scanTicks % 40 == 0) {
            FOElytraLog.detail("\u626b\u63cf\u8fdb\u5ea6 %d/%d\uff08%.0f%%\uff09\u7b2c %d tick\uff1a\u5df2\u8bfb %d \u683c\u3001\u8df3\u8fc7\u672a\u52a0\u8f7d %d \u683c\uff0c\u5f85\u8fc7\u6ee4\u5019\u9009 %d \u4e2a\uff08\u5df2\u6392\u9664\u5df2\u6807\u8bb0 %d \u4e2a\uff09", this.scanDone, this.scanTotal, 100.0 * (double)this.scanDone / (double)Math.max(1L, this.scanTotal), this.scanTicks, this.scanChecked, this.scanSkippedUnloaded, this.pendingCandidates.size(), this.filteredMarked);
        }
    }

    private void beginScan() {
        int yMax;
        BlockPos origin = this.mc.player.getBlockPos().toImmutable();
        this.scanOriginX = origin.getX();
        this.scanOriginY = origin.getY();
        this.scanOriginZ = origin.getZ();
        this.scanRadius = Math.max(8, (Integer)this.searchRadius.get());
        int yMin = Math.max(-40, this.scanOriginY - this.scanRadius);
        if (yMin > (yMax = Math.min(16, this.scanOriginY + this.scanRadius))) {
            yMin = Math.max(-64, this.scanOriginY - 24);
            yMax = Math.min(319, this.scanOriginY + 24);
            FOElytraLog.warn("\u4f60\u73b0\u5728\u5728 Y=%d\uff0c\u4e0d\u5728\u8bd5\u70bc\u5bc6\u5ba4\u7684\u9ad8\u5ea6\u5e26\uff08%d~%d\uff09\u91cc\uff1a\u672c\u6b21\u53ea\u5728 Y=%d~%d \u627e\u5b9d\u5e93", this.scanOriginY, -40, 16, yMin, yMax);
        }
        this.scanDyMin = yMin - this.scanOriginY;
        this.scanDyMax = yMax - this.scanOriginY;
        this.scanDyRel = this.scanDyMin;
        this.scanDz = -this.scanRadius;
        this.scanDx = -this.scanRadius;
        long width = 2L * (long)this.scanRadius + 1L;
        this.scanTotal = width * width * (long)(this.scanDyMax - this.scanDyMin + 1);
        this.scanDone = 0L;
        this.scanChecked = 0L;
        this.scanSkippedUnloaded = 0L;
        this.scanTicks = 0;
        this.pendingCandidates.clear();
        this.pendingKeys.clear();
        this.candidates.clear();
        this.scanning = true;
        this.say("\u5f00\u59cb\u5728 %d \u683c\u534a\u5f84\u5185\u627e\u5b9d\u5e93\uff08Y=%d~%d\uff0c\u5171 %d \u4e2a\u5750\u6807\uff09", this.scanRadius, yMin, yMax, this.scanTotal);
        FOElytraLog.detail("\u626b\u63cf\u4e2d\u5fc3 %s\uff5c\u6bcf tick \u6700\u591a %d \u4e2a\u5750\u6807\uff0c\u8de8 tick \u7eed\u626b\uff5c\u533a\u5757\u672a\u52a0\u8f7d\u6574\u5217\u8df3\u8fc7", origin.toShortString(), 6000);
    }

    private boolean scanAdvance() {
        ++this.scanDx;
        if (this.scanDx <= this.scanRadius) {
            return true;
        }
        this.scanDx = -this.scanRadius;
        ++this.scanDz;
        if (this.scanDz <= this.scanRadius) {
            return true;
        }
        this.scanDz = -this.scanRadius;
        ++this.scanDyRel;
        if (this.scanDyRel <= this.scanDyMax) {
            return true;
        }
        this.scanDyRel = this.scanDyMin;
        return false;
    }

    private void finishScan() {
        this.scanning = false;
        this.filterIndex = 0;
        this.filteredSpawner = 0;
        this.phase = Phase.FILTER;
        this.say("\u626b\u63cf\u5b8c\u6210\uff1a\u8bfb\u4e86 %d \u683c\uff08\u8df3\u8fc7\u672a\u52a0\u8f7d %d \u683c\uff09\uff0c\u5f85\u8fc7\u6ee4\u5019\u9009 %d \u4e2a", this.scanChecked, this.scanSkippedUnloaded, this.pendingCandidates.size());
        FOElytraLog.detail("\u626b\u63cf\u7edf\u8ba1\uff1a\u603b\u5750\u6807 %d\uff5c\u5df2\u6807\u8bb0\u8df3\u8fc7 %d\uff08\u6807\u8bb0\u603b\u6570 %d\uff09", this.scanTotal, this.filteredMarked, VaultMarks.get().count());
    }

    private boolean chunkLoaded(int cx, int cz) {
        if (cx == this.scanChunkX && cz == this.scanChunkZ) {
            return this.scanChunkLoadedFlag;
        }
        this.scanChunkX = cx;
        this.scanChunkZ = cz;
        try {
            this.scanChunkLoadedFlag = this.mc.world.isChunkLoaded(cx, cz);
        }
        catch (Throwable t) {
            this.scanChunkLoadedFlag = false;
        }
        return this.scanChunkLoadedFlag;
    }

    private void filterTick() {
        int budget = 1;
        while (budget-- > 0 && this.filterIndex < this.pendingCandidates.size()) {
            BlockPos p;
            BlockState st;
            if (!(st = this.mc.world.getBlockState(p = this.pendingCandidates.get(this.filterIndex++))).isOf(Blocks.VAULT)) {
                FOElytraLog.detail("\u8fc7\u6ee4\uff1a%s \u5df2\u7ecf\u4e0d\u662f\u5b9d\u5e93\u65b9\u5757\u4e86\uff08\u88ab\u6316\u4e86\uff1f\uff09\uff0c\u8df3\u8fc7", p.toShortString());
                continue;
            }
            int y = p.getY();
            if (y < -40 || y > 16) {
                FOElytraLog.detail("\u8fc7\u6ee4\uff1a%s \u5728 Y=%d\uff0c\u8d85\u51fa\u5f00\u5e93\u72b6\u6001\u673a\u80fd\u626b\u7684\u9ad8\u5ea6\u5e26\uff08%d~%d\uff09\uff0c\u8df3\u8fc7", p.toShortString(), y, -40, 16);
                continue;
            }
            if (((Boolean)this.avoidSpawner.get()).booleanValue()) {
                boolean tooClose;
                try {
                    tooClose = VaultDisplay.tooCloseToSpawner(p, (Integer)this.spawnerAvoidRadius.get());
                }
                catch (Throwable t) {
                    FOElytraLog.detailError("tooCloseToSpawner", t);
                    tooClose = false;
                }
                if (tooClose) {
                    ++this.filteredSpawner;
                    FOElytraLog.detail("\u8fc7\u6ee4\uff1a%s \u79bb\u8bd5\u70bc\u5237\u602a\u7b3c < %d \u683c\uff08\u4e0d\u5728\u5b83\u65c1\u8fb9\u5f00\u5e93\uff09", p.toShortString(), this.spawnerAvoidRadius.get());
                    continue;
                }
            }
            this.candidates.add(p);
        }
        if (this.filterIndex < this.pendingCandidates.size()) {
            return;
        }
        this.candidates.sort(Comparator.comparingDouble(this::distanceTo));
        if (this.candidates.isEmpty()) {
            FOElytraLog.warn("\u534a\u5f84 %d \u683c\u5185\u6ca1\u6709\u53ef\u5f00\u7684\u4e0d\u7965\u5b9d\u5e93\uff08\u5f85\u8fc7\u6ee4 %d \u4e2a\uff0c\u5176\u4e2d %d \u4e2a\u56e0\u4e3a\u79bb\u5237\u602a\u7b3c\u592a\u8fd1\u88ab\u8df3\u8fc7\uff09", this.scanRadius, this.pendingCandidates.size(), this.filteredSpawner);
            this.phase = Phase.NO_VAULT;
            return;
        }
        this.say("\u627e\u5230 %d \u4e2a\u53ef\u5f00\u7684\u4e0d\u7965\u5b9d\u5e93\uff08\u8df3\u8fc7 %d \u4e2a\u79bb\u5237\u602a\u7b3c\u592a\u8fd1\u7684\uff09\uff0c\u6700\u8fd1\u7684\u662f %s\uff08%.0f \u683c\uff09", this.candidates.size(), this.filteredSpawner, this.candidates.get(0).toShortString(), this.distanceTo(this.candidates.get(0)));
        FOElytraLog.detail("\u5019\u9009\u6e05\u5355\uff08\u524d 10\uff09\uff1a%s", this.describeCandidates(10));
        this.current = this.candidates.get(0);
        this.phase = Phase.APPROACH;
    }

    private String describeCandidates(int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, this.candidates.size()); ++i) {
            if (i > 0) {
                sb.append("\u3001");
            }
            sb.append(this.candidates.get(i).toShortString());
        }
        return sb.toString();
    }

    private double distanceTo(BlockPos pos) {
        if (this.mc.player == null) {
            return Double.MAX_VALUE;
        }
        double dx = this.mc.player.getX() - ((double)pos.getX() + 0.5);
        double dy = this.mc.player.getY() - ((double)pos.getY() + 0.5);
        double dz = this.mc.player.getZ() - ((double)pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void approachTick() {
        if (this.current == null) {
            this.phase = Phase.SCAN;
            return;
        }
        if (this.phaseTicks == 1) {
            this.gotoResend = 0;
            BaritoneHook.stop();
            this.say("\u8d70\u5411\u4e0d\u7965\u5b9d\u5e93 %s\uff08\u8ddd\u79bb %.0f \u683c\uff09", this.current.toShortString(), this.distanceTo(this.current));
            FOElytraLog.detail("APPROACH\uff1a\u76ee\u6807 %s\uff5c\u5b89\u5168\u8ddd\u79bb\uff08\u79bb\u5237\u602a\u7b3c\uff09%d \u683c\uff5c\u8d85\u65f6 %d \u79d2", this.current.toShortString(), this.spawnerAvoidRadius.get(), this.approachTimeoutSec.get());
        }
        if (!this.mc.world.getBlockState(this.current).isOf(Blocks.VAULT)) {
            this.skipCurrent("\u8fd9\u4e2a\u5b9d\u5e93\u65b9\u5757\u4e0d\u89c1\u4e86\uff08\u88ab\u6316\u6389/\u6362\u6389\u4e86\uff09");
            return;
        }
        double d = this.horizontalDistanceTo(this.current);
        if (d <= Math.max(1.0, (Double)this.openDistance.get())) {
            BaritoneHook.stop();
            FOElytraLog.detail("\u5df2\u5230\u4f4d\uff08\u6c34\u5e73 %.1f \u683c\uff09\u2192 \u8fdb\u5165\u8bfb\u53d6\u5c55\u793a\u7269\u9636\u6bb5", d);
            this.displayReadTicks = 0;
            this.displayDesc = "\uff08\u8fd8\u6ca1\u770b\uff09";
            this.phase = Phase.LOOK;
            return;
        }
        if (((Boolean)this.avoidSpawner.get()).booleanValue()) {
            boolean tooClose;
            try {
                tooClose = VaultDisplay.tooCloseToSpawner(this.mc.player.getBlockPos(), (Integer)this.spawnerAvoidRadius.get());
            }
            catch (Throwable t) {
                tooClose = false;
            }
            if (tooClose && this.phaseTicks % 200 == 0) {
                FOElytraLog.warn("\u6211\u73b0\u5728\u7ad9\u7684\u4f4d\u7f6e\u79bb\u8bd5\u70bc\u5237\u602a\u7b3c\u4e0d\u5230 %d \u683c\uff0c\u53ef\u80fd\u4f1a\u628a\u602a\u5237\u8d77\u6765", this.spawnerAvoidRadius.get());
            }
        }
        if (!BaritoneHook.ready()) {
            this.fail("\u6ca1\u6709\u53ef\u7528\u7684 Baritone\uff0c\u8d70\u4e0d\u5230\u5b9d\u5e93 " + this.current.toShortString() + "\u3002\u8bf7\u88c5 Baritone\uff0c\u6216\u628a\u300c\u81ea\u52a8\u6316\u7ad6\u4e95\u4e0b\u964d\u300d\u5173\u6389\u81ea\u5df1\u8d70\u8fc7\u53bb\u3002");
            return;
        }
        if (this.phaseTicks == 1 || this.phaseTicks % 100 == 0) {
            BaritoneHook.command("goto " + this.current.getX() + " " + this.current.getY() + " " + this.current.getZ());
            ++this.gotoResend;
            FOElytraLog.detail("\u4e0b\u53d1 goto %d %d %d\uff08\u7b2c %d \u6b21\uff1b\u8d70\u504f/\u88ab\u9876\u6389\u65f6\u4f1a\u81ea\u52a8\u91cd\u53d1\uff09", this.current.getX(), this.current.getY(), this.current.getZ(), this.gotoResend);
        }
        if (this.phaseTicks % 200 == 0) {
            FOElytraLog.detail("\u8d70\u5411\u5b9d\u5e93\u4e2d\uff1a\u8ddd %s \u8fd8\u6709 %.0f \u683c\uff08\u5df2\u8d70 %d \u79d2\uff09", this.current.toShortString(), d, this.phaseTicks / 20);
        }
        if (this.phaseTicks > (Integer)this.approachTimeoutSec.get() * 20) {
            this.skipCurrent(String.format(Locale.ROOT, "\u8d70\u4e86 %d \u79d2\u8fd8\u6ca1\u5230\uff08\u8fd8\u5dee %.0f \u683c\uff0c\u88ab\u5c01\u8d77\u6765\u6216\u8def\u4e0d\u901a\uff09", this.approachTimeoutSec.get(), d));
        }
    }

    private double horizontalDistanceTo(BlockPos pos) {
        return Math.hypot(this.mc.player.getX() - ((double)pos.getX() + 0.5), this.mc.player.getZ() - ((double)pos.getZ() + 0.5));
    }

    private void skipCurrent(String why) {
        if (this.current != null) {
            this.skipped.add(this.current.asLong());
            this.say("\u8df3\u8fc7\u8fd9\u4e2a\u5b9d\u5e93 %s\uff1a%s", this.current.toShortString(), why);
        }
        BaritoneHook.stop();
        this.current = null;
        this.candidates.removeIf(p -> this.skipped.contains(p.asLong()));
        if (!this.candidates.isEmpty()) {
            this.current = this.candidates.get(0);
            this.phase = Phase.APPROACH;
        } else {
            this.phase = Phase.NO_VAULT;
        }
    }

    private void lookTick() {
        boolean hit;
        ItemStack shown;
        if (this.current == null) {
            this.phase = Phase.SCAN;
            return;
        }
        int wait = Math.max(20, (Integer)this.displayWaitTicks.get());
        if (this.phaseTicks < wait) {
            if (this.phaseTicks == 1) {
                FOElytraLog.detail("\u7ad9\u5230\u6fc0\u6d3b\u8303\u56f4\u5185\uff0c\u7b49 %d tick \u8ba9\u670d\u52a1\u7aef\u628a\u300c\u5c55\u793a\u7269\u300d\u540c\u6b65\u8fc7\u6765\uff08\u670d\u52a1\u7aef\u5b9d\u5e93\u72b6\u6001\u6bcf 20 tick \u91cd\u7b97\u4e00\u6b21\uff09", wait);
            }
            return;
        }
        DisplayMode mode = (DisplayMode)((Object)this.displayMode.get());
        try {
            shown = VaultDisplay.displayItem(this.current);
            this.displayDesc = VaultDisplay.describe(this.current);
            hit = VaultDisplay.displayContainsTarget(this.current, (List)this.displayItems.get(), this.parseIds((String)this.displayEnchants.get()));
        }
        catch (Throwable t) {
            FOElytraLog.detailError("\u8bfb\u53d6\u5b9d\u5e93\u5c55\u793a\u7269", t);
            shown = ItemStack.EMPTY;
            this.displayDesc = "\uff08\u8bfb\u53d6\u5931\u8d25\uff1a" + t.getClass().getSimpleName() + "\uff09";
            hit = false;
        }
        boolean unknown = shown == null || shown.isEmpty();
        this.displayReadTicks = this.phaseTicks;
        if (mode == DisplayMode.OFF) {
            this.openCurrent("\u5c55\u793a\u7269\u7b5b\u9009\u5df2\u5173\uff08\u4e0d\u770b\u5c55\u793a\u7269\uff09");
            return;
        }
        if (mode == DisplayMode.LOG) {
            FOElytraLog.detail("\u5b9d\u5e93 %s \u73b0\u5728\u5c55\u793a\u7684\u662f\uff1a%s", this.current.toShortString(), this.displayDesc);
            if (hit) {
                this.say("\u5b9d\u5e93 %s \u5c55\u793a\u7269 = %s\uff08\u547d\u4e2d\u6e05\u5355\uff0c\u4f46\u5c55\u793a\u7269\u548c\u5b9e\u9645\u6389\u843d\u65e0\u5173\uff0c\u7167\u5f00\uff09", this.current.toShortString(), this.displayDesc);
            }
            this.openCurrent(hit ? "\u5c55\u793a\u7269\u547d\u4e2d\uff08\u53ea\u8bb0\u5f55\u6a21\u5f0f\uff0c\u7167\u5f00\uff09" : "\u53ea\u8bb0\u5f55\u6a21\u5f0f\uff0c\u7167\u5f00");
            return;
        }
        if (hit) {
            this.say("\u5c55\u793a\u7269\u547d\u4e2d\u76ee\u6807\uff08%s\uff09\u2192 \u7acb\u523b\u7528%s\u6253\u5f00 %s", this.displayDesc, this.keyName(), this.current.toShortString());
            this.openCurrent("\u5c55\u793a\u7269\u547d\u4e2d");
            return;
        }
        if (unknown && ((Boolean)this.openWhenUnknown.get()).booleanValue()) {
            this.say("\u8bfb\u4e0d\u5230\u5b9d\u5e93 %s \u7684\u5c55\u793a\u7269\uff08\u53ef\u80fd\u6ca1\u540c\u6b65/\u5df2\u7ecf\u88ab\u4eba\u5f00\u8fc7\uff09\u2192 \u6309\u8bbe\u7f6e\u7167\u6837\u5f00", this.current.toShortString());
            this.openCurrent("\u8bfb\u4e0d\u5230\u5c55\u793a\u7269\uff0c\u6309\u8bbe\u7f6e\u7167\u5f00");
            return;
        }
        this.skipCurrent("\u5c55\u793a\u7269\u662f\u300c" + this.displayDesc + "\u300d\uff0c\u4e0d\u5728\u76ee\u6807\u6e05\u5355\u91cc\uff08\u8fd9\u6b21\u4e0d\u6d6a\u8d39\u94a5\u5319\uff09");
    }

    private void openCurrent(String why) {
        if (!this.hasAnyKey()) {
            this.noKeyStop();
            return;
        }
        BaritoneHook.stop();
        FOElytraLog.detail("\u51c6\u5907\u5f00\u5e93\uff1a%s\uff08\u539f\u56e0\uff1a%s\uff09", this.current.toShortString(), why);
        this.phase = Phase.OPEN;
    }

    private void openTick() {
        if (this.current == null) {
            this.phase = Phase.SCAN;
            return;
        }
        if (this.vaultOpener == null) {
            if (!this.mc.world.getBlockState(this.current).isOf(Blocks.VAULT)) {
                this.skipCurrent("\u51c6\u5907\u5f00\u7684\u65f6\u5019\u5b83\u5df2\u7ecf\u4e0d\u662f\u5b9d\u5e93\u65b9\u5757\u4e86");
                return;
            }
            int radius = (int)Math.max(16.0, Math.min(64.0, Math.ceil(this.horizontalDistanceTo(this.current)) + 16.0));
            BlockPos focus = this.current.toImmutable();
            this.vaultOpener = new VaultOpener(new VaultOpener.Options(radius, (Double)this.openDistance.get(), (Integer)this.collectTicks.get(), 1, (Boolean)this.alsoNormal.get() == false, AutoOminousVault.safeItems((List)this.targetItems.get()), this.parseIds((String)this.targetEnchants.get()), (Boolean)this.stopOnTarget.get(), false, true, (Integer)this.actionDelay.get(), pos -> pos != null && pos.equals((Object)focus), this::onVaultOpened));
            this.vaultOpener.start();
            this.say("\u5f00\u59cb\u5f00\u5e93\uff1a%s\uff5c\u534a\u5f84 %d\uff5c\u5f00\u5b8c\u6536\u96c6 %d tick\uff5c\u76ee\u6807 %s", this.current.toShortString(), radius, this.collectTicks.get(), this.describeLoot());
        }
        this.vaultOpener.tick();
        TaskStatus st = this.vaultOpener.status();
        if (st == TaskStatus.RUNNING) {
            if (this.phaseTicks % 100 == 0) {
                FOElytraLog.detail("\u5f00\u5e93\u4e2d\uff1a%s\uff5c\u5df2\u5f00 %d \u4e2a\uff5c%s", this.vaultOpener.progress(), this.vaultOpener.openedCount(), this.vaultOpener.foundTarget() ? "\u5df2\u547d\u4e2d\u76ee\u6807" : "\u8fd8\u6ca1\u547d\u4e2d");
            }
            if (this.phaseTicks > 1200) {
                FOElytraLog.warn("\u5f00\u5e93\u6d41\u7a0b\u8dd1\u4e86 %d \u79d2\u8fd8\u6ca1\u7ed3\u675f\uff08\u72b6\u6001 %s\uff09\uff0c\u5148\u6309\u5f53\u524d\u7ed3\u679c\u5904\u7406", new Object[]{this.phaseTicks / 20, this.vaultOpener.state()});
                this.phase = Phase.POST_OPEN;
            }
            return;
        }
        this.phase = Phase.POST_OPEN;
    }

    private void onVaultOpened(BlockPos pos) {
        try {
            VaultMarks.get().mark(pos);
            this.say("\u5df2\u6807\u8bb0\u8fd9\u4e2a\u5b9d\u5e93\u4e3a\u300c\u5df2\u6253\u5f00\u300d\uff1a%s\uff08\u4ee5\u540e\u4e0d\u4f1a\u518d\u9009\u5b83\uff09", pos.toShortString());
        }
        catch (Throwable t) {
            FOElytraLog.detailError("onVaultOpened", t);
        }
    }

    private void postOpenTick() {
        if (this.vaultOpener == null) {
            this.phase = Phase.SCAN;
            return;
        }
        boolean found = this.vaultOpener.foundTarget();
        int opened = this.vaultOpener.openedCount();
        List<String> loot = this.vaultOpener.lootLog();
        String state = this.vaultOpener.state().name();
        String fail = this.vaultOpener.failReason();
        String last = this.vaultOpener.lastMessage();
        for (String line : loot) {
            this.say("\u6218\u5229\u54c1\uff1a%s", line);
        }
        if (opened > 0) {
            if (this.current != null) {
                try {
                    VaultMarks.get().mark(this.current);
                }
                catch (Throwable t) {
                    FOElytraLog.detailError("mark(postOpen)", t);
                }
                this.skipped.add(this.current.asLong());
            }
            ++this.openedTotal;
            ++this.openedThisChamber;
            this.say("\u7b2c %d \u4e2a\u4e0d\u7965\u5b9d\u5e93\u5f00\u5b8c\u4e86\uff08\u672c\u5bc6\u5ba4\u7b2c %d \u4e2a\uff0c\u672c\u6b21\u5171\u5f00 %d \u4e2a\uff0c\u5df2\u6807\u8bb0 %d \u4e2a\uff09", this.openedTotal, this.openedThisChamber, this.openedTotal, VaultMarks.get().count());
        } else {
            FOElytraLog.warn("\u6ca1\u80fd\u6253\u5f00 %s\uff1a\u72b6\u6001 %s\uff5c\u539f\u56e0 %s\uff5c\u6700\u540e\u4e00\u6b65 %s", this.current == null ? "?" : this.current.toShortString(), state, fail.isEmpty() ? "\u6ca1\u6709\u7ed9\u51fa\u5177\u4f53\u539f\u56e0" : fail, AutoOminousVault.safe(last));
            if (this.current != null) {
                this.skipped.add(this.current.asLong());
            }
        }
        BlockPos openedPos = this.current;
        if (this.vaultOpener.status() == TaskStatus.RUNNING) {
            this.vaultOpener.abort("\u672c\u6b21\u5f00\u5e93\u7ed3\u7b97\uff0c\u4ea4\u7ed9\u6a21\u5757\u5904\u7406\u4e0b\u4e00\u4e2a");
        }
        this.vaultOpener = null;
        this.current = null;
        if (found) {
            String msg = String.format(Locale.ROOT, "\u62ff\u5230\u76ee\u6807\u6218\u5229\u54c1\u4e86\uff01\uff08\u672c\u6b21\u5171\u5f00 %d \u4e2a\u4e0d\u7965\u5b9d\u5e93\uff0c\u547d\u4e2d\u5728 %s\uff09", this.openedTotal, String.join((CharSequence)"\u3001", loot));
            this.finish(msg);
            return;
        }
        if (!this.hasAnyKey()) {
            this.noKeyStop();
            return;
        }
        if (((Boolean)this.stopWhenNoFood.get()).booleanValue() && this.countFood() <= 0) {
            this.finish(String.format(Locale.ROOT, "\u98df\u7269\u7528\u5b8c\u4e86\uff08%s 0 \u4e2a\uff09\u2014\u2014\u5148\u56de\u53bb\u8865\u8d27", this.foodName()));
            return;
        }
        if (this.openedThisChamber >= (Integer)this.maxPerChamber.get()) {
            this.say("\u672c\u5bc6\u5ba4\u5df2\u7ecf\u5f00\u4e86 %d \u4e2a\uff08\u4e0a\u9650 %d\uff09\u2192 \u4ea4\u7ed9\u300c\u4efb\u52a1\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed\u300d\u51b3\u5b9a\u6536\u5de5\u8fd8\u662f\u6362\u5bc6\u5ba4", this.openedThisChamber, this.maxPerChamber.get());
            this.openedThisChamber = 0;
            this.candidates.clear();
            this.phase = Phase.NO_VAULT;
            return;
        }
        if (openedPos != null) {
            this.candidates.removeIf(p -> p.equals((Object)openedPos));
        }
        if (!this.candidates.isEmpty()) {
            this.current = this.candidates.get(0);
            FOElytraLog.detail("\u672c\u5bc6\u5ba4\u8fd8\u6709 %d \u4e2a\u5019\u9009\u6ca1\u5f00\uff0c\u53bb\u4e0b\u4e00\u4e2a\uff1a%s\uff08%.0f \u683c\uff09", this.candidates.size(), this.current.toShortString(), this.distanceTo(this.current));
            this.phase = Phase.APPROACH;
            return;
        }
        this.enterScan();
    }

    private void noVaultTick() {
        if (!this.hasAnyKey()) {
            this.noKeyStop();
            return;
        }
        if (((Boolean)this.stopWhenNoFood.get()).booleanValue() && this.countFood() <= 0) {
            this.finish(String.format(Locale.ROOT, "\u98df\u7269\u7528\u5b8c\u4e86\uff08%s 0 \u4e2a\uff09\u2014\u2014\u6309\u8bbe\u7f6e\u505c\u624b", this.foodName()));
            return;
        }
        if (((Boolean)this.autoClose.get()).booleanValue()) {
            this.finish(String.format(Locale.ROOT, "\u8fd9\u4e2a\u8bd5\u70bc\u5927\u5385\u6ca1\u6709\u53ef\u5f00\u7684\u672a\u6807\u8bb0\u4e0d\u7965\u5b9d\u5e93\u4e86\uff08\u672c\u6b21\u5171\u5f00 %d \u4e2a\uff09\u2014\u2014\u6309\u8bbe\u7f6e\u6536\u5de5\u5e76\u5173\u95ed\u6a21\u5757", this.openedTotal));
            return;
        }
        if (this.chambersVisited >= (Integer)this.maxChambers.get()) {
            this.finish(String.format(Locale.ROOT, "\u5df2\u7ecf\u8fde\u7740\u6362\u8fc7 %d \u4e2a\u5bc6\u5ba4\uff08\u4e0a\u9650\uff09\u2014\u2014\u6536\u5de5\uff0c\u9632\u6b62\u6302\u673a\u4e71\u98de", this.chambersVisited));
            return;
        }
        if (this.phaseTicks == 1) {
            this.say("\u672c\u5bc6\u5ba4\u6ca1\u5e93\u53ef\u5f00\u4e86\uff1a\u6309\u8bbe\u7f6e\uff08\u4efb\u52a1\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed = \u5426\uff09\u53bb\u4e0b\u4e00\u4e2a\u8bd5\u70bc\u5927\u5385\uff08\u5df2\u53bb %d \u4e2a\uff0c\u4e0a\u9650 %d\uff09", this.chambersVisited, this.maxChambers.get());
        }
        switch (this.tryLocate().ordinal()) {
            case 2: {
                if (this.phaseTicks % 100 != 0) break;
                FOElytraLog.detail("\u6b63\u5728\u540e\u53f0\u641c\u7d22\u4e0b\u4e00\u4e2a\u8bd5\u70bc\u5927\u5385\u2026\u5df2\u7b49 %d \u79d2", this.phaseTicks / 20);
                break;
            }
            case 0: {
                this.goToTarget();
                break;
            }
            case 1: {
                this.finish(String.format(Locale.ROOT, "\u672c\u5bc6\u5ba4\u6ca1\u5e93\u53ef\u5f00\u4e86\uff0c\u4e5f\u5b9a\u4f4d\u4e0d\u5230\u4e0b\u4e00\u4e2a\u8bd5\u70bc\u5927\u5385\uff1a%s\uff08\u53ef\u586b\u300c\u4e16\u754c\u79cd\u5b50\u300d\u6216\u300c\u5750\u6807\u5217\u8868\u300d\uff09", this.locateFail));
            }
        }
    }

    private LocateResult tryLocate() {
        TrialChamberLocator.Target t;
        if (this.mc.player == null) {
            return LocateResult.FAILED;
        }
        int px = this.mc.player.getBlockX();
        int pz = this.mc.player.getBlockZ();
        LocateMode mode = (LocateMode)((Object)this.locateMode.get());
        this.locateFail = "";
        if (mode == LocateMode.AUTO || mode == LocateMode.SEED) {
            Long seed = this.parseSeed();
            if (seed != null) {
                List<TrialChamberLocator.Target> all = this.locator.locateBySeedValues(seed, px, pz, (Integer)this.seedRings.get());
                TrialChamberLocator.Target t2 = this.pickUnvisited(all);
                if (t2 != null) {
                    this.usedTargets.add(t2.x() + "," + t2.z());
                    return this.useTarget(t2);
                }
                this.locateFail = String.format(Locale.ROOT, "\u6309\u79cd\u5b50 %d \u63a8\u7b97\u51fa\u7684 %d \u4e2a\u5019\u9009\u70b9\u90fd\u5df2\u7ecf\u53bb\u8fc7\u4e86\uff08\u5708\u6570 %d\uff0c\u53ef\u8c03\u5927\uff09", seed, all == null ? 0 : all.size(), this.seedRings.get());
            } else {
                this.locateFail = "\u300c\u5b9a\u4f4d\u65b9\u5f0f\u300d\u8981\u7528\u79cd\u5b50\u63a8\u7b97\uff0c\u4f46\u300c\u4e16\u754c\u79cd\u5b50\uff08\u624b\u586b\uff09\u300d\u662f\u7a7a\u7684";
            }
            if (mode == LocateMode.SEED) {
                return LocateResult.FAILED;
            }
        }
        if (mode == LocateMode.AUTO || mode == LocateMode.COORD_LIST) {
            t = this.pickCoords(px, pz);
            if (t != null) {
                this.usedTargets.add(t.x() + "," + t.z());
                return this.useTarget(t);
            }
            this.locateFail = "\u5750\u6807\u5217\u8868\u91cc\u6ca1\u6709\u53ef\u7528\u5750\u6807\uff08\u7a7a\u3001\u683c\u5f0f\u4e0d\u5bf9\u3001\u6216\u8005\u90fd\u53bb\u8fc7\u4e86\uff09";
            if (mode == LocateMode.COORD_LIST) {
                return LocateResult.FAILED;
            }
        }
        if (mode == LocateMode.AUTO || mode == LocateMode.MAP) {
            t = this.locator.readMapTarget();
            if (t != null && !this.usedTargets.contains(t.x() + "," + t.z())) {
                this.usedTargets.add(t.x() + "," + t.z());
                return this.useTarget(t);
            }
            this.locateFail = t != null ? String.format(Locale.ROOT, "\u5730\u56fe\u6307\u5411\u7684\u8bd5\u70bc\u5927\u5385 %d, %d \u8fd9\u6b21\u5df2\u7ecf\u53bb\u8fc7\u4e86\uff08\u4e00\u5f20\u5730\u56fe\u53ea\u7ed9\u4e00\u4e2a\u76ee\u6807\uff0c\u6362\u4e0b\u4e00\u4e2a\u8bf7\u7528\u300c\u4e16\u754c\u79cd\u5b50\u300d\u6216\u300c\u5750\u6807\u5217\u8868\u300d\uff09", t.x(), t.z()) : "\u80cc\u5305\u91cc\u6ca1\u6709\u300c\u57cb\u85cf\u7684\u8bd5\u70bc\u5bc6\u5ba4\u5730\u56fe\u300d\uff0c\u6216\u8bfb\u4e0d\u5230\u6807\u8bb0\u70b9\uff08" + AutoOminousVault.safe(this.locator.failReason()) + "\uff09";
            if (mode == LocateMode.MAP) {
                return LocateResult.FAILED;
            }
        }
        if (mode == LocateMode.AUTO || mode == LocateMode.INTEGRATED_SEARCH) {
            if (!TrialChamberLocator.seedSearchAvailable()) {
                if (this.locateFail.isEmpty()) {
                    this.locateFail = "\u5355\u673a\u79cd\u5b50\u641c\u7d22\u4e0d\u53ef\u7528\uff08\u591a\u4eba\u670d\u52a1\u5668\u6ca1\u6709\u4e16\u754c\u79cd\u5b50\uff0c\u8bf7\u7528\u300c\u624b\u586b\u79cd\u5b50\u300d\u6216\u5750\u6807\u5217\u8868\uff09";
                }
                return LocateResult.FAILED;
            }
            if (!this.integratedSearchStarted) {
                this.integratedSearchStarted = true;
                this.locator.locateBySeed(px, pz, (Integer)this.integratedSearchRadius.get());
                FOElytraLog.detail("\u5df2\u53d1\u8d77\u5355\u673a\u79cd\u5b50\u641c\u7d22\uff08\u8dd1\u5728\u6574\u5408\u670d\u52a1\u7aef\u7ebf\u7a0b\u4e0a\uff0c\u4e0d\u5361\u753b\u9762\uff09\uff1a\u8d77\u70b9 %d, %d\uff0c\u534a\u5f84 %d \u533a\u5757", px, pz, this.integratedSearchRadius.get());
            }
            if (this.locator.isSearching()) {
                return LocateResult.WAITING;
            }
            this.integratedSearchStarted = false;
            t = this.locator.pollSeedSearch();
            if (t != null && !this.usedTargets.contains(t.x() + "," + t.z())) {
                this.usedTargets.add(t.x() + "," + t.z());
                return this.useTarget(t);
            }
            if (this.locateFail.isEmpty()) {
                this.locateFail = t != null ? String.format(Locale.ROOT, "\u5355\u673a\u641c\u7d22\u7ed9\u51fa\u7684\u8fd8\u662f\u521a\u624d\u90a3\u4e2a\u8bd5\u70bc\u5927\u5385 %d, %d\uff08\u5df2\u7ecf\u53bb\u8fc7\u4e86\uff09\uff0c\u7ee7\u7eed\u5237\u8bf7\u7528\u300c\u4e16\u754c\u79cd\u5b50\u300d", t.x(), t.z()) : "\u5355\u673a\u79cd\u5b50\u641c\u7d22\u6ca1\u627e\u5230\uff1a" + AutoOminousVault.safe(this.locator.failReason());
            }
            return LocateResult.FAILED;
        }
        if (this.locateFail.isEmpty()) {
            this.locateFail = "\u6ca1\u6709\u53ef\u7528\u7684\u5b9a\u4f4d\u65b9\u5f0f";
        }
        return LocateResult.FAILED;
    }

    private LocateResult useTarget(TrialChamberLocator.Target t) {
        this.targetX = t.x();
        this.targetZ = t.z();
        this.targetNote = t.note() == null ? "" : t.note();
        this.say("\u5b9a\u4f4d\u5230\u8bd5\u70bc\u5927\u5385\u5019\u9009\u4eba\uff1a%d, %d\uff08\u6765\u6e90 %s\uff09", new Object[]{this.targetX, this.targetZ, t.source()});
        FOElytraLog.detail("\u5b9a\u4f4d\u8bf4\u660e\uff1a%s", this.targetNote);
        return LocateResult.FOUND;
    }

    private void goToTarget() {
        double d = Math.hypot(this.mc.player.getX() - ((double)this.targetX + 0.5), this.mc.player.getZ() - ((double)this.targetZ + 0.5));
        ++this.chambersVisited;
        this.openedThisChamber = 0;
        if (d <= (double)((Integer)this.arriveRadius.get()).intValue()) {
            this.say("\u5df2\u7ecf\u5728\u8fd9\u4e2a\u5bc6\u5ba4 %.0f \u683c\u8303\u56f4\u5185\uff08\u5224\u5b9a\u8ddd\u79bb %d\uff09\uff1a\u4e0d\u98de\u4e86", d, this.arriveRadius.get());
            if (this.mc.player.getBlockY() > (Integer)this.digY.get() + 8 && ((Boolean)this.autoDig.get()).booleanValue()) {
                this.phase = Phase.DIG;
            } else {
                this.enterScan();
            }
            return;
        }
        this.say("\u8ddd\u76ee\u6807 %.0f \u683c\uff08\u9608\u503c %d\uff09\uff1a\u5148\u4e0a\u5730\u8868\u518d\u5f00\u9798\u7fc5\u98de\u8fc7\u53bb", d, this.arriveRadius.get());
        this.phase = Phase.CLIMB;
    }

    private TrialChamberLocator.Target pickUnvisited(List<TrialChamberLocator.Target> all) {
        if (all == null) {
            return null;
        }
        for (TrialChamberLocator.Target t : all) {
            if (this.usedTargets.contains(t.x() + "," + t.z())) continue;
            return t;
        }
        return null;
    }

    private TrialChamberLocator.Target pickCoords(int px, int pz) {
        List<String> lines = this.coordList.get();
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        double best = Double.MAX_VALUE;
        TrialChamberLocator.Target bestT = null;
        for (String line : lines) {
            String s;
            if (line == null || (s = line.trim()).isEmpty()) continue;
            String[] parts = s.split("[,\uff0c\\s]+");
            if (parts.length < 2) {
                FOElytraLog.warn("\u5750\u6807\u5217\u8868\u91cc\u8fd9\u4e00\u884c\u770b\u4e0d\u61c2\uff08\u5e94\u4e3a x,z\uff09\uff1a%s", s);
                continue;
            }
            try {
                double d;
                int z;
                int x = Integer.parseInt(parts[0].trim());
                if (this.usedTargets.contains(x + "," + (z = Integer.parseInt(parts[1].trim()))) || !((d = Math.hypot(px - x, pz - z)) < best)) continue;
                best = d;
                bestT = TrialChamberLocator.manual(x, z);
            }
            catch (NumberFormatException e) {
                FOElytraLog.warn("\u5750\u6807\u5217\u8868\u91cc\u8fd9\u4e00\u884c\u4e0d\u662f\u5408\u6cd5\u6574\u6570\uff1a%s", s);
            }
        }
        return bestT;
    }

    private Long parseSeed() {
        String raw = (String)this.worldSeed.get();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return Long.parseLong(s);
        }
        catch (NumberFormatException e) {
            int hash = s.hashCode();
            FOElytraLog.warn("「世界种子」不是纯数字（%s）：按原版字符串种子规则当成 %d 处理（想精确请填 /seed 的数字）", s, hash);
            FOElytraLog.warn("\u300c\u4e16\u754c\u79cd\u5b50\u300d\u4e0d\u662f\u7eaf\u6570\u5b57\uff08%s\uff09\uff1a\u6309\u539f\u7248\u5b57\u7b26\u4e32\u79cd\u5b50\u89c4\u5219\u5f53\u6210 %d \u5904\u7406\uff08\u60f3\u7cbe\u786e\u8bf7\u586b /seed \u7684\u6570\u5b57\uff09", s, hash);
            return (long)hash;
        }
    }

    private void climbTick() {
        if (this.phaseTicks == 1) {
            BaritoneHook.stop();
            this.surfaceYCache = Integer.MIN_VALUE;
            this.say("\u5f00\u59cb\u722c\u4e0a\u5730\u8868\uff1a\u73b0\u5728 Y=%d\uff08\u9798\u7fc5\u5728\u5730\u4e0b\u8d77\u4e0d\u6765\uff09", this.mc.player.getBlockY());
            FOElytraLog.detail("CLIMB\uff1a\u76ee\u6807 Y \u2265 %d \u6216\u300c\u89c1\u5929\u300d\u5373\u53ef\u8d77\u98de\uff1bBaritone %s", this.takeoffMinY.get(), BaritoneHook.ready() ? "\u5df2\u5c31\u7eea" : "\u4e0d\u53ef\u7528");
        }
        if (this.surfaceOk()) {
            BaritoneHook.stop();
            this.say("\u5df2\u7ecf\u80fd\u8d77\u98de\u4e86\uff08Y=%d\uff0c\u89c1\u5929 %s\uff09\u2192 \u5f00\u59cb\u98de\u5f80 %d, %d", this.mc.player.getBlockY(), this.skyVisible() ? "\u662f" : "\u5426", this.targetX, this.targetZ);
            this.travelTicks = 0;
            this.phase = Phase.TRAVEL;
            return;
        }
        if (!BaritoneHook.ready()) {
            this.fail("\u6ca1\u6709\u53ef\u7528\u7684 Baritone\uff0c\u722c\u4e0d\u4e0a\u5730\u8868\uff08\u5f53\u524d Y=" + this.mc.player.getBlockY() + "\uff09\u3002\u8bf7\u88c5 Baritone\uff0c\u6216\u81ea\u5df1\u8d70\u56de\u5730\u9762\u518d\u5f00\u6a21\u5757\u3002");
            return;
        }
        if (this.phaseTicks == 1 || this.phaseTicks % 100 == 0) {
            int y = this.surfaceYEstimate();
            BaritoneHook.command("goto " + this.mc.player.getBlockX() + " " + y + " " + this.mc.player.getBlockZ());
            FOElytraLog.detail("\u8ba9 Baritone \u5f80\u5730\u8868\u8d70\uff1agoto %d %d %d\uff08\u5b83\u4f1a\u81ea\u5df1\u6316/\u7ed5\u4e0a\u53bb\uff1b\u76ee\u6807 Y \u53d6\u672c\u5217\u5730\u8868\u9ad8\u5ea6\uff09", this.mc.player.getBlockX(), y, this.mc.player.getBlockZ());
        }
        if (this.phaseTicks % 200 == 0) {
            FOElytraLog.detail("\u6b63\u5728\u722c\u5730\u8868\uff08%d \u79d2\uff09\uff1a\u5f53\u524d Y=%d\uff0c\u89c1\u5929 %s", this.phaseTicks / 20, this.mc.player.getBlockY(), this.skyVisible() ? "\u662f" : "\u5426");
        }
        if (this.phaseTicks > (Integer)this.climbTimeoutSec.get() * 20) {
            this.fail(String.format(Locale.ROOT, "\u722c\u5730\u8868\u8d85\u65f6\uff08%d \u79d2\u8fd8\u6ca1\u5230 Y \u2265 %d / \u89c1\u5929\uff09\uff0c\u5f53\u524d Y=%d\u3002\u8bf7\u81ea\u5df1\u8d70\u56de\u5730\u9762\uff0c\u6216\u628a\u8d85\u65f6\u8c03\u5927", this.climbTimeoutSec.get(), this.takeoffMinY.get(), this.mc.player.getBlockY()));
        }
    }

    private boolean landed() {
        if (this.mc.player == null) {
            return true;
        }
        return this.mc.player.isOnGround() || this.mc.player.isTouchingWater() || this.mc.player.hasVehicle() || this.mc.player.isClimbing();
    }

    private boolean surfaceOk() {
        return this.skyVisible() || this.mc.player.getBlockY() >= (Integer)this.takeoffMinY.get();
    }

    private boolean skyVisible() {
        try {
            return this.mc.world.isSkyVisible(this.mc.player.getBlockPos());
        }
        catch (Throwable t) {
            return false;
        }
    }

    private int surfaceYEstimate() {
        int top;
        if (this.surfaceYCache != Integer.MIN_VALUE) {
            return this.surfaceYCache;
        }
        int px = this.mc.player.getBlockX();
        int pz = this.mc.player.getBlockZ();
        try {
            top = this.mc.world.getTopYInclusive();
        }
        catch (Throwable t) {
            top = 319;
        }
        for (int y = top; y > this.mc.world.getBottomY(); --y) {
            if (this.mc.world.getBlockState(new BlockPos(px, y, pz)).isAir()) continue;
            this.surfaceYCache = Math.max(y + 1, (Integer)this.takeoffMinY.get());
            return this.surfaceYCache;
        }
        this.surfaceYCache = Math.max(this.mc.player.getBlockY(), (Integer)this.takeoffMinY.get());
        return this.surfaceYCache;
    }

    private void travelTick() {
        double d;
        AutoElytraFlight travel = this.travelModule();
        double dNow = Math.hypot(this.mc.player.getX() - ((double)this.targetX + 0.5), this.mc.player.getZ() - ((double)this.targetZ + 0.5));
        if (travel == null) {
            if (!this.warnedNoTravel) {
                this.warnedNoTravel = true;
                FOElytraLog.warn("\u627e\u4e0d\u5230\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u6a21\u5757\uff0c\u98de\u4e0d\u4e86\uff1a\u8bf7\u81ea\u5df1\u98de\u5230 %d, %d \u9644\u8fd1\uff08%d \u683c\u5185\u6a21\u5757\u4f1a\u81ea\u52a8\u4e0b\u964d\uff09", this.targetX, this.targetZ, this.digArriveRadius.get());
            }
            this.afterTravel(dNow, "\u627e\u4e0d\u5230\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u6a21\u5757");
            return;
        }
        ++this.travelTicks;
        if (this.travelTicks == 1) {
            BaritoneHook.stop();
            if (!travel.isActive()) {
                this.prevFileVerbose = FOElytraLog.fileVerbose;
                this.verboseSaved = true;
                travel.toggle();
                this.weEnabledTravel = true;
                FOElytraLog.fileVerbose = this.prevFileVerbose;
                this.say("\u4e34\u65f6\u6253\u5f00\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u6765\u8dd1\u8fd9\u4e00\u6bb5\uff08\u5230\u8fbe\u540e\u6309\u8bbe\u7f6e\u8fd8\u56de\u53bb\uff09", new Object[0]);
            }
            travel.flyTo(this.targetX, this.targetZ);
        }
        if ((d = dNow) <= (double)((Integer)this.digArriveRadius.get()).intValue() && (!this.mc.player.isGliding() || this.mc.player.isOnGround())) {
            this.say("\u5df2\u5230\u8fbe\u76ee\u6807\u4e0a\u7a7a\uff08\u6c34\u5e73 %.0f \u683c\uff0c\u505c\u98de\u9608\u503c %d\uff09\uff0c\u51c6\u5907\u4e0b\u964d", d, this.digArriveRadius.get());
            this.endTravel();
            this.phase = Phase.DIG;
            return;
        }
        if (travel.travelFailed()) {
            this.endTravel();
            this.afterTravel(d, "\u8dd1\u56fe\u6a21\u5757\u62a5\u5931\u8d25\uff1a" + travel.travelFailReason());
            return;
        }
        if (!travel.isActive() && this.travelTicks > 40) {
            this.endTravel();
            String r = travel.travelFailReason();
            this.afterTravel(d, "\u501f\u6765\u7684\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u5df2\u7ecf\u4e0d\u5728\u8fd0\u884c\uff08" + (String)(r == null || r.isEmpty() ? "\u53ef\u80fd\u662f\u5b83\u5230\u8fbe\u540e\u6309\u81ea\u5df1\u7684\u300c\u4efb\u52a1\u7ed3\u675f\u5173\u95ed\u6a21\u5757\u300d\u5173\u6389\u4e86\uff0c\u4e5f\u53ef\u80fd\u662f\u4f60\u81ea\u5df1\u5173\u4e86\u5b83" : "\u5b83\u81ea\u5df1\u62a5\u7684\u539f\u56e0\uff1a" + r) + "\uff09");
            return;
        }
        if (travel.travelTerminal() && this.travelTicks > 60) {
            this.endTravel();
            this.afterTravel(d, "\u8dd1\u56fe\u6a21\u5757\u8bf4\u8fd9\u4e00\u6bb5\u7ed3\u675f\u4e86\uff08\u72b6\u6001 " + travel.travelStateName() + "\uff09");
            return;
        }
        if (this.travelTicks > (Integer)this.travelTimeoutSec.get() * 20) {
            this.endTravel();
            this.afterTravel(d, "\u98de\u884c\u8d85\u8fc7 " + String.valueOf(this.travelTimeoutSec.get()) + " \u79d2\u8fd8\u6ca1\u5230");
            return;
        }
        if (this.travelTicks % 200 == 0) {
            FOElytraLog.detail("\u98de\u884c\u4e2d\uff1a\u8ddd\u76ee\u6807 %.0f \u683c\uff5c\u8dd1\u56fe\u6a21\u5757\u72b6\u6001 %s\uff5c\u6ed1\u7fd4 %s", d, travel.travelStateName(), this.mc.player.isGliding() ? "\u662f" : "\u5426");
        }
    }

    private void afterTravel(double d, String why) {
        double tolerance = Math.max((Integer)this.digArriveRadius.get(), 40);
        if (d > tolerance) {
            this.fail(String.format(Locale.ROOT, "\u98de\u884c\u6ca1\u5230\u76ee\u6807\uff08%s\uff09\uff0c\u8fd8\u5728 %.0f \u683c\u5916\uff0c\u5148\u505c\u4e0b\u4e0d\u6316\u3002\u8bf7\u68c0\u67e5\u9798\u7fc5/\u70df\u82b1/Baritone \u540e\u91cd\u5f00\u6a21\u5757\uff0c\u6216\u7528\u300c\u5750\u6807\u5217\u8868\u300d\u81ea\u5df1\u8fc7\u53bb", why, d));
            return;
        }
        FOElytraLog.warn("%s\uff1b\u5f53\u524d\u8ddd\u79bb %.0f \u683c\uff0c\u5c31\u5730\u4e0b\u964d", why, d);
        this.phase = Phase.DIG;
    }

    private void endTravel() {
        AutoElytraFlight travel;
        if (!this.weEnabledTravel) {
            return;
        }
        this.weEnabledTravel = false;
        if (this.verboseSaved) {
            FOElytraLog.fileVerbose = (Boolean)this.verboseLog.get();
        }
        if ((travel = this.travelModule()) == null) {
            return;
        }
        if (!travel.isActive()) {
            this.say("\u501f\u6765\u7684\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u5df2\u7ecf\u81ea\u5df1\u505c\u4e0b\u6765\u4e86\uff08\u4e0d\u7528\u518d\u8fd8\uff09", new Object[0]);
        } else if (((Boolean)this.disableTravelOnArrive.get()).booleanValue()) {
            travel.toggle();
            this.say("\u5df2\u5173\u6389\u501f\u6765\u7684\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\uff08\u8bbe\u7f6e\u300c\u5230\u8fbe\u540e\u5173\u6389\u8dd1\u56fe\u6a21\u5757\u300d\uff09", new Object[0]);
        } else {
            this.say("\u300c\u81ea\u52a8\u9798\u7fc5\u98de\u884c\u300d\u4fdd\u6301\u5f00\u542f\uff08\u6309\u8bbe\u7f6e\u4e0d\u4e3b\u52a8\u5173\u6389\uff09", new Object[0]);
        }
    }

    private AutoElytraFlight travelModule() {
        try {
            return (AutoElytraFlight)Modules.get().get(AutoElytraFlight.class);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("travelModule", t);
            return null;
        }
    }

    private void digTick() {
        if (!((Boolean)this.autoDig.get()).booleanValue()) {
            this.say("\u300c\u81ea\u52a8\u6316\u7ad6\u4e95\u4e0b\u964d\u300d\u662f\u5173\u7684\uff1a\u8bf7\u81ea\u5df1\u628a\u89d2\u8272\u5e26\u5230\u5bc6\u5ba4\u5c42\uff08\u6a21\u5757\u4f1a\u5728 %d \u683c\u5185\u81ea\u52a8\u627e\u5e93\u5f00\u5e93\uff09", this.searchRadius.get());
            this.enterScan();
            return;
        }
        if (this.phaseTicks == 1) {
            BaritoneHook.stop();
            BlockBreaker.reset();
            this.digCount = 0;
            this.digStallTicks = 0;
            this.airBelowTicks = 0;
            this.landingWaitTicks = 0;
            this.say("\u5f00\u59cb\u6316\u7ad6\u4e95\u4e0b\u964d\uff1a\u73b0\u5728 Y=%d\uff0c\u76ee\u6807 Y=%d", this.mc.player.getBlockY(), this.digY.get());
        }
        if (this.mc.player.getBlockY() <= (Integer)this.digY.get()) {
            this.say("\u5df2\u5230 Y=%d\uff08\u76ee\u6807 %d\uff09\u2192 \u5f00\u59cb\u627e\u5b9d\u5e93", this.mc.player.getBlockY(), this.digY.get());
            BlockBreaker.cancel();
            this.enterScan();
            return;
        }
        if (this.mc.player.isGliding() || !this.landed()) {
            if (this.landingWaitTicks++ > 400) {
                this.fail(String.format(Locale.ROOT, "\u7b49\u4e86 %d \u79d2\u8fd8\u6ca1\u843d\u5730\uff08\u6ed1\u7fd4 %s\uff0f\u7740\u5730 %s\uff0f\u5728\u6c34\u91cc %s\uff0f\u9a91\u4e58 %s\uff09\u2014\u2014\u8bf7\u81ea\u5df1\u843d\u5730\u6216\u843d\u5230\u5e73\u53f0\u4e0a\uff0c\u518d\u5f00\u6a21\u5757", 20, this.mc.player.isGliding() ? "\u4e2d" : "\u5426", this.mc.player.isOnGround() ? "\u662f" : "\u5426", this.mc.player.isTouchingWater() ? "\u662f" : "\u5426", this.mc.player.hasVehicle() ? "\u662f" : "\u5426"));
                return;
            }
            if (this.landingWaitTicks % 100 == 1) {
                this.say("\u7b49\u7740\u843d\u5730\u518d\u6316\u7ad6\u4e95\uff08\u5f53\u524d Y=%d\uff0c\u6ed1\u7fd4 %s\uff09\u2026", this.mc.player.getBlockY(), this.mc.player.isGliding() ? "\u4e2d" : "\u5426");
            }
            return;
        }
        this.landingWaitTicks = 0;
        if (this.mc.player.getHealth() <= ((Double)this.digAbortHealth.get()).floatValue()) {
            this.fail(String.format(Locale.ROOT, "\u4e0b\u964d\u8def\u4e0a\u8840\u91cf\u6389\u5230 %.1f\uff08\u9608\u503c %.1f\uff09\u2014\u2014\u505c\u4e0b\u6765\u4fdd\u547d\uff0c\u8bf7\u81ea\u5df1\u5904\u7406\u5b8c\u518d\u5f00\u6a21\u5757", Float.valueOf(this.mc.player.getHealth()), this.digAbortHealth.get()));
            return;
        }
        if (this.digCount >= (Integer)this.maxDig.get()) {
            this.fail(String.format(Locale.ROOT, "\u5df2\u7ecf\u6316\u4e86 %d \u683c\u8fd8\u6ca1\u5230 Y=%d\uff08\u4e0a\u9650 %d\uff09\u2014\u2014\u628a\u300c\u4e0b\u964d\u5230 Y\u300d\u8c03\u9ad8\uff0c\u6216\u628a\u4e0a\u9650\u8c03\u5927", this.digCount, this.digY.get(), this.maxDig.get()));
            return;
        }
        if (this.digDelayTicks > 0) {
            --this.digDelayTicks;
            return;
        }
        BlockPos below = this.mc.player.getBlockPos().down();
        BlockState st = this.mc.world.getBlockState(below);
        if (!st.getFluidState().isEmpty()) {
            this.fail("\u811a\u4e0b\u65b9\u5757\u662f\u6d41\u4f53\uff08\u5ca9\u6d46/\u6c34\uff09\u2014\u2014\u5df2\u505c\u6b62\u4e0b\u964d\uff0c\u8bf7\u81ea\u5df1\u5728\u65c1\u8fb9\u7ed5\u5f00\u6216\u6362\u4e2a\u4f4d\u7f6e\u518d\u5f00\u6a21\u5757");
            return;
        }
        if (st.isOf(Blocks.BEDROCK)) {
            this.fail("\u4e0b\u9762\u662f\u57fa\u5ca9\uff0c\u6316\u4e0d\u4e0b\u53bb\u4e86\uff08\u5f53\u524d Y=" + this.mc.player.getBlockY() + "\uff09");
            return;
        }
        if (st.isAir()) {
            if (this.airBelowTicks++ > 60) {
                this.fail("\u811a\u4e0b\u4e0d\u662f\u5b9e\u5fc3\u65b9\u5757\uff08\u7ad9\u5230\u65b9\u5757\u8fb9\u7f18\u4e86\uff1f\uff09\u2014\u2014\u8bf7\u7ad9\u5230\u65b9\u6b63\u7684\u4f4d\u7f6e\u518d\u5f00\u6a21\u5757");
                return;
            }
            return;
        }
        this.airBelowTicks = 0;
        if (this.digStallTicks++ > 400) {
            this.fail(String.format(Locale.ROOT, "\u6316\u4e0d\u52a8\u4e86\uff1a%d \u79d2\u90fd\u6ca1\u6316\u6389\u811a\u4e0b\u65b9\u5757\uff08\u5df2\u6316 %d \u683c\uff0c\u5f53\u524d Y=%d\uff09\u3002\u591a\u534a\u662f\u5feb\u6377\u680f\u6ca1\u9550\u5b50\uff0c\u6216\u65b9\u5757\u592a\u786c\uff08\u9ed1\u66dc\u77f3/\u8fdc\u53e4\u6b8b\u9ab8\uff09", 20, this.digCount, this.mc.player.getBlockY()));
            return;
        }
        if (BlockBreaker.tick(below)) {
            ++this.digCount;
            this.digStallTicks = 0;
            this.digDelayTicks = 3;
            if (this.digCount % 5 == 0) {
                FOElytraLog.detail("\u4e0b\u964d\u4e2d\uff1a\u5df2\u6316 %d \u683c\uff0c\u5f53\u524d Y=%d\uff08\u76ee\u6807 %d\uff09", this.digCount, this.mc.player.getBlockY(), this.digY.get());
            }
        }
    }

    private void finish(String message) {
        this.say("%s", message);
        this.phase = Phase.DONE;
        this.cleanup(false);
        this.maybeAutoClose("\u4efb\u52a1\u7ed3\u675f");
    }

    private void fail(String reason) {
        if (this.phase == Phase.FAILED) {
            return;
        }
        this.failReason = reason;
        FOElytraLog.err("\u81ea\u52a8\u4e0d\u7965\u5b9d\u5e93\u5931\u8d25\uff1a%s", reason);
        FOElytraLog.detail("\u5931\u8d25\u8bca\u65ad\uff1a\u9636\u6bb5 %s\uff5c\u4f4d\u7f6e %d %d %d\uff5c\u94a5\u5319 %d\uff5c\u98df\u7269(%s)\uff5c\u5df2\u5f00 %d\uff5c\u53bb\u8fc7 %d \u4e2a\u5bc6\u5ba4", this.phase.name(), this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ(), this.keyCount(), this.foodCountText(), this.openedTotal, this.chambersVisited);
        this.phase = Phase.FAILED;
        this.cleanup(true);
        try {
            for (String l : FOElytraLog.snapshot(reason, 200, 8)) {
                if (l.contains("\u81ea\u52a8\u4e0d\u7965\u5b9d\u5e93\u5931\u8d25\uff1a")) continue;
                FOElytraLog.chatRaw(l);
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("fail snapshot", t);
        }
        this.maybeAutoClose("\u4efb\u52a1\u5931\u8d25");
    }

    private void noKeyStop() {
        String msg = String.format(Locale.ROOT, "\u80cc\u5305\u91cc\u6ca1\u6709%s\uff08\u73b0\u5728\uff1a\u4e0d\u7965 %d \u4e2a / \u666e\u901a %d \u4e2a\uff09\u3002%s", this.keyName(), this.countKey(Items.OMINOUS_TRIAL_KEY), this.countKey(Items.TRIAL_KEY), (Boolean)this.alsoNormal.get() != false ? "\u4e24\u79cd\u8bd5\u70bc\u94a5\u5319\u90fd\u6ca1\u6709\uff0c\u5f00\u4e0d\u4e86\u4efb\u4f55\u5b9d\u5e93\u3002" : "\u5f00\u4e0d\u7965\u5b9d\u5e93\u5fc5\u987b\u7528\u4e0d\u7965\u8bd5\u70bc\u94a5\u5319\u3002");
        this.say("%s", msg);
        this.say("\u94a5\u5319\u6765\u6e90\uff1a\u559d\u4e0d\u7965\u4e4b\u74f6 \u2192 \u9760\u8fd1\u8bd5\u70bc\u5237\u602a\u7b3c\u62ff\u5230\u300c\u8bd5\u70bc\u4e4b\u5146\u300d\u2192 \u6253\u6b7b\u5b83\u5237\u51fa\u7684\u602a\uff0c30% \u6982\u7387\u6389\u4e0d\u7965\u8bd5\u70bc\u94a5\u5319\u3002\u672c\u6a21\u5757\u9ed8\u8ba4\u300c\u7ed5\u8fc7\u8bd5\u70bc\u5237\u602a\u7b3c\u300d\uff0c\u4e0d\u66ff\u4f60\u5237\uff0c\u8bf7\u81ea\u5df1\u5907\u597d\u3002", new Object[0]);
        FOElytraLog.warn("\u5df2\u505c\u6b62\u4efb\u52a1%s", (Boolean)this.noKeyAutoClose.get() != false ? "\u5e76\u81ea\u52a8\u5173\u95ed\u6a21\u5757\uff08\u8bbe\u7f6e\u300c\u6ca1\u94a5\u5319\u65f6\u81ea\u52a8\u5173\u95ed\u300d\uff09" : "\uff08\u8bbe\u7f6e\u91cc\u5173\u6389\u4e86\u300c\u6ca1\u94a5\u5319\u65f6\u81ea\u52a8\u5173\u95ed\u300d\uff0c\u6a21\u5757\u4fdd\u6301\u5f00\u542f\uff09");
        try {
            this.mc.inGameHud.setOverlayMessage(Text.of((String)("[\u81ea\u52a8\u4e0d\u7965\u5b9d\u5e93] \u6ca1\u6709" + this.keyName() + "\uff0c\u4efb\u52a1\u505c\u6b62")), false);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("overlayMessage", t);
        }
        this.phase = Phase.FAILED;
        this.failReason = "\u6ca1\u6709" + this.keyName();
        this.cleanup(true);
        if (((Boolean)this.noKeyAutoClose.get()).booleanValue() && this.isActive()) {
            FOElytraLog.detail("\u6309\u8bbe\u7f6e\u300c\u6ca1\u94a5\u5319\u65f6\u81ea\u52a8\u5173\u95ed\u300d\u5173\u95ed\u6a21\u5757", new Object[0]);
            this.toggle();
        }
    }

    private void maybeAutoClose(String why) {
        if (!this.isActive()) {
            return;
        }
        if (((Boolean)this.autoClose.get()).booleanValue()) {
            this.say("\u6309\u8bbe\u7f6e\u300c\u4efb\u52a1\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed\u300d\u5173\u95ed\u6a21\u5757\uff08%s\uff1b\u53ef\u5728\u8bbe\u7f6e\u91cc\u6539\uff09", why);
            this.toggle();
        } else {
            this.say("\u300c\u4efb\u52a1\u7ed3\u675f\u540e\u81ea\u52a8\u5173\u95ed\u300d\u662f\u5173\u7684\uff1a\u6a21\u5757\u4fdd\u6301\u5f00\u542f\uff0c\u7b49\u4f60\u81ea\u5df1\u51b3\u5b9a\uff08%s\uff09", why);
        }
    }

    private void cleanup(boolean aborting) {
        BlockBreaker.cancel();
        if (this.vaultOpener != null) {
            if (this.vaultOpener.status() == TaskStatus.RUNNING) {
                this.vaultOpener.abort(aborting ? "\u4efb\u52a1\u4e2d\u6b62" : "\u4efb\u52a1\u7ed3\u675f");
            }
            this.vaultOpener = null;
        }
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.endTravel();
        this.candidates.clear();
        this.pendingCandidates.clear();
        this.pendingKeys.clear();
        this.scanning = false;
    }

    private int keyCount() {
        int n = this.countKey(Items.OMINOUS_TRIAL_KEY);
        if (((Boolean)this.alsoNormal.get()).booleanValue()) {
            n += this.countKey(Items.TRIAL_KEY);
        }
        return n;
    }

    private boolean hasAnyKey() {
        return this.keyCount() > 0;
    }

    private String keyName() {
        return (Boolean)this.alsoNormal.get() != false ? "\u8bd5\u70bc\u94a5\u5319\uff08\u666e\u901a/\u4e0d\u7965\u90fd\u7b97\uff09" : "\u4e0d\u7965\u8bd5\u70bc\u94a5\u5319";
    }

    private String foodName() {
        List<Item> list = this.foodItems.get();
        if (list == null || list.isEmpty()) {
            return "\u98df\u7269\uff08\u6e05\u5355\u4e3a\u7a7a\uff09";
        }
        StringBuilder sb = new StringBuilder();
        for (Item it : list) {
            if (it == null) continue;
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(it.getName().getString());
        }
        return sb.length() == 0 ? "\u98df\u7269\uff08\u6e05\u5355\u4e3a\u7a7a\uff09" : sb.toString();
    }

    private String foodCountText() {
        List<Item> list = this.foodItems.get();
        if (list == null || list.isEmpty()) {
            return this.foodName() + "\uff08\u4e0d\u68c0\u67e5\uff09";
        }
        return this.foodName() + " " + this.countFood() + " \u4e2a";
    }

    private int countFood() {
        if (this.mc.player == null) {
            return 0;
        }
        List<Item> list = this.foodItems.get();
        if (list == null || list.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int n = 0;
        for (Item it : list) {
            if (it == null) continue;
            n += ItemHelper.countInInventory((PlayerEntity)this.mc.player, it);
        }
        return n;
    }

    private String describeLoot() {
        String ench;
        StringBuilder sb = new StringBuilder();
        List<Item> items = this.targetItems.get();
        if (items != null) {
            for (Item it : items) {
                if (it == null) continue;
                if (sb.length() > 0) {
                    sb.append("\u3001");
                }
                sb.append(it.getName().getString());
            }
        }
        if ((ench = (String)this.targetEnchants.get()) != null && !ench.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\u3001");
            }
            sb.append("\u9644\u9b54\u4e66[").append(ench.trim()).append("]");
        }
        return sb.length() == 0 ? "\uff08\u7a7a\uff01\u4e0d\u4f1a\u5224\u547d\u4e2d\uff09" : sb.toString();
    }

    private static List<Item> safeItems(List<Item> in) {
        return in == null ? List.of() : in;
    }

    private List<Identifier> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        ArrayList<Identifier> out = new ArrayList<Identifier>();
        for (String part : raw.split("[,\uff0c]")) {
            Object s = part.trim();
            if (((String)s).isEmpty()) continue;
            if (!((String)s).contains(":")) {
                s = "minecraft:" + (String)s;
            }
            try {
                Identifier id = Identifier.of((String)s);
                if (out.contains(id)) continue;
                out.add(id);
            }
            catch (Throwable t) {
                FOElytraLog.warn("\u300c\u76ee\u6807\u9b54\u5492\u300d\u91cc\u7684\u8fd9\u4e2a id \u770b\u4e0d\u61c2\uff0c\u5df2\u8df3\u8fc7\uff1a%s", s);
            }
        }
        return out;
    }

    private void say(String fmt, Object ... args) {
        if (((Boolean)this.talkInChat.get()).booleanValue()) {
            FOElytraLog.info(fmt, args);
        } else {
            FOElytraLog.detail(fmt, args);
        }
    }

    private int countKey(Item item) {
        return this.mc.player == null ? 0 : ItemHelper.countInInventory((PlayerEntity)this.mc.player, item);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public String failReason() {
        return this.failReason;
    }

    public static enum DisplayMode {
        OFF("\u5173\u95ed"),
        LOG("\u4ec5\u8bb0\u5f55"),
        REQUIRE("\u5fc5\u987b\u6253\u5f00");


        private final String label;

        DisplayMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public static enum LocateMode {
        AUTO("\u81ea\u52a8"),
        SEED("\u79cd\u5b50\u5b9a\u4f4d"),
        COORD_LIST("\u5750\u6807\u6e05\u5355"),
        MAP("\u5730\u56fe\u6807\u8bb0"),
        INTEGRATED_SEARCH("\u6574\u5408\u641c\u7d22");


        private final String label;

        LocateMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public static enum Phase {
        IDLE("\u7a7a\u95f2"),
        PREPARE("\u51c6\u5907"),
        SCAN("\u626b\u63cf"),
        FILTER("\u7b5b\u9009"),
        APPROACH("\u63a5\u8fd1"),
        LOOK("\u89c2\u5bdf"),
        OPEN("\u6253\u5f00"),
        POST_OPEN("\u5f00\u7bb1\u540e"),
        NO_VAULT("\u672a\u627e\u5230\u5b9d\u5e93"),
        CLIMB("\u6500\u722c"),
        TRAVEL("\u98de\u884c"),
        DIG("\u6316\u6398"),
        DONE("\u5b8c\u6210"),
        FAILED("\u5931\u8d25");


        private final String label;

        Phase(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private static enum LocateResult {
        FOUND,
        FAILED,
        WAITING;

    }
}

