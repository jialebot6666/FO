package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.ancient.core.AncientChestStore;
import com.fo.addon.ancient.core.EnchantedGoldCityLogic;
import com.fo.addon.ancient.core.EnchantedGoldStore;
import com.fo.addon.ancient.core.FatFilterLogic;
import com.fo.addon.ancient.core.FireworkPolicyLogic;
import com.fo.addon.ancient.core.FlightHysteresisLogic;
import com.fo.addon.ancient.core.LandingPitchLogic;
import com.fo.addon.ancient.core.PackGateLogic;
import com.fo.addon.ancient.core.TakeoffClearanceLogic;
import com.fo.addon.ancient.core.FatChestLogic;
import com.fo.addon.ancient.core.FluidColumnLogic;
import com.fo.addon.ancient.core.GearRepairLogic;
import com.fo.addon.ancient.core.GearSupplyLogic;
import com.fo.addon.ancient.core.RepairSessionLogic;
import com.fo.addon.ancient.core.ShaftColumnLogic;
import com.fo.addon.ancient.core.ShaftFluidLogic;
import com.fo.addon.ancient.core.ShaftPrecheckLogic;
import com.fo.addon.ancient.core.SurfaceStopLogic;
import com.fo.addon.ancient.modules.FOAncientCitySearch;
import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.BlockClicker;
import com.fo.addon.utils.InteractionUtils;
import com.fo.addon.utils.StashChestSpotLogic;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FO 附魔金采集。
 *
 * <p>联动「FO 古城战利品搜索」的命中记录，自动飞向古城、下竖井、横向通道、只取附魔金苹果，
 * 满 64 自动进潜影盒；途中按阈值补给经验瓶并修复鞘翅与镐子。
 *
 * <p>安全底线：竖井每下挖一格都检查下方 + 四邻 + 前瞻流体，遇水/岩浆立即停止并升井；
 * 水下古城整城拉黑；附魔金上方有小水塘的箱子单独拉黑。所有黑名单独立存
 * {@code fo-enchanted-gold-auto.json}，不修改古城搜索的记录文件。
 */
public class FOEnchantedGoldCollector extends Module {

    /** 主状态机。 */
    private enum State {
        IDLE, PREPARE, SELECT_CITY, FLY, ARRIVE, PREP_AHEAD,
        SHAFT_PRECHECK, SHAFT_DIG, LOOT, ASCEND, SURFACE_STOP,
        STATION, REPAIR, NEXT, DONE, PAUSE
    }

    /** 飞行子阶段。 */
    private enum FlightPhase { RISING, SHAFT_CLIMB, FLYING, LANDING }

    /** 升井子阶段：对中 → 起跳 → 滑翔上升。 */
    private enum AscendPhase { CENTER, LAUNCH, GLIDE }

    /** 地表停靠的子步骤。 */
    private enum StationStep {
        NONE, PLACE_EC, OPEN_EC, TAKE_BOX, CLOSE_EC, PLACE_BOX, OPEN_BOX, FILL_BOX,
        CLOSE_BOX, MINE_BOX, REOPEN_EC, STORE_BOX, TAKE_SUPPLIES, MINE_EC, FINISH
    }

    /** 存盒方式。 */
    public enum StorageMode {
        ENDER_CHEST("末影箱加潜影盒"),
        BACKPACK("随身潜影盒");

        private final String label;

        StorageMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String STORE_FILE_NAME = "fo-enchanted-gold-auto.json";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFlight = settings.createGroup("飞行");
    private final SettingGroup sgShaft = settings.createGroup("竖井");
    private final SettingGroup sgStorage = settings.createGroup("存盒");
    private final SettingGroup sgRepair = settings.createGroup("耐久与补给");
    private final SettingGroup sgSafety = settings.createGroup("安全");

    private final Setting<Boolean> linkSearchSetting;
    private final Setting<Boolean> autoSearchSetting;
    private final Setting<Integer> targetApplesSetting;
    private final Setting<Integer> maxChestsPerCitySetting;

    private final Setting<Integer> minHeightSetting;
    private final Setting<Integer> maxHeightSetting;
    private final Setting<Integer> riseHeightSetting;
    private final Setting<Integer> minFireworksSetting;
    private final Setting<Integer> landingRetriesSetting;
    private final Setting<Double> fireworkIntervalSetting;
    private final Setting<Boolean> pitchSmoothSetting;
    /** 俯仰平滑：抬头速率（度/刻）。 */
    private final Setting<Double> pitchRotateUpSetting;
    /** 俯仰平滑：低头速率（度/刻）。 */
    private final Setting<Double> pitchRotateDownSetting;
    /** 抬头阶段垂直速度低于此值才补烟花。 */
    private final Setting<Double> fireworkVelocityThresholdSetting;
    private final Setting<Integer> fireworkBoostCooldownSetting;
    private final Setting<Boolean> autoFireworkBoostSetting;
    /** 降到落点 3 格内时每 2 刻反向一次，原地荡着下降。 */
    private final Setting<Boolean> slowFallBoostSetting;

    private final Setting<Integer> fluidLookaheadSetting;
    private final Setting<Boolean> blacklistOnFluidSetting;
    private final Setting<Boolean> abandonUnderwaterSetting;
    private final Setting<Integer> wardenDistanceSetting;
    /** 升井时竖直速度低于此值就补烟花。 */
    private final Setting<Double> ascendThrustSetting;

    private final Setting<Boolean> autoPackSetting;
    private final Setting<StorageMode> storageModeSetting;
    private final Setting<Integer> minEmptyBoxesSetting;
    /** 攒够多少附魔金苹果才停靠存盒。 */
    private final Setting<Integer> packThresholdSetting;

    private final Setting<Boolean> autoRepairSetting;
    private final Setting<Double> elytraThresholdSetting;
    private final Setting<Double> pickaxeThresholdSetting;
    private final Setting<Double> repairTargetSetting;
    private final Setting<Integer> minSafeDurabilitySetting;
    private final Setting<Boolean> autoSupplyBottlesSetting;
    private final Setting<Integer> minBottlesSetting;
    private final Setting<Integer> targetBottlesSetting;
    private final Setting<Integer> minTotemsSetting;
    private final Setting<Integer> maxBottlesPerRepairSetting;
    private final Setting<Boolean> mainHandPickaxeSetting;
    private final Setting<Boolean> pauseWithoutBottlesSetting;

    private final Setting<Integer> stageTimeoutSetting;
    private final Setting<Boolean> returnWhenFullSetting;

    private final FOAncientCitySearch searchModule;
    private EnchantedGoldStore store = new EnchantedGoldStore();
    private boolean storeLoaded;

    private State state = State.IDLE;
    private int stateTicks;
    private String statusText = "未启动";

    private List<EnchantedGoldCityLogic.City> cities = List.of();
    private EnchantedGoldCityLogic.City currentCity;
    private AncientChestStore.Mark currentChest;
    private int cityIndex;
    private int openedInCity;
    private int fakeCityFailures;
    private boolean fakeCityBlacklistPending;
    private int totalCollected;
    private int packedTotal;
    private int openedTotal;
    private int failedTotal;
    private int repairCount;
    private boolean autoSearchRequested;
    private int searchWaitTicks;
    private int rescanAttempts;

    private BlockPos shaftColumn;
    private BlockPos lastShaftColumn;
    private int lastShaftTopY;
    private int shaftTopY;
    private int shaftChestY;
    private int shaftDigY;
    private boolean needRepairAfterAscent;
    private boolean pauseAfterAscent;
    private boolean surfaceEvaluated;

    /** 井道 5 列预检：每个箱子只做一次。 */
    private boolean precheckDone;
    private ShaftPrecheckLogic.Verdict precheckVerdict;

    /** 升井子阶段与连发计数。 */
    private AscendPhase ascendPhase = AscendPhase.CENTER;
    private int ascendBurst;
    private int ascendTicks;

    private FlightPhase flightPhase = FlightPhase.RISING;
    private float smoothedPitch;
    private int fireworkCooldown;
    private int landingFailures;
    private int flightTicksTotal;
    private double flightTargetX;
    private double flightTargetZ;
    private double landingTargetY;

    /** 高度滞回：true=正在抬头爬升，false=正在 p40 俯冲。只在到顶/到底时翻转。 */
    private boolean climbing;
    /** 展翅后是否已经打过第一发烟花。 */
    private boolean firstFireworkDone;
    private int lastFireworkTick;
    private int fireworkBoostCooldown;
    /** 滑翔意外中断的恢复计数。 */
    private int glideFailTicks;
    private int glideRecoverTicks;
    private boolean glideRecovering;
    /** 低于降落点时反向拉升。 */
    private boolean landingRecover;
    private int recoverStageTick;
    private float recoveryYaw;
    private int recoverCount;

    /** 升井后若需存盒/修复，先落地处理；不需要就空中直接转场。 */
    private boolean landForStop;

    /** 停靠/修复结束后要回到的状态：落地后处理回预检，升井后停靠回 NEXT。 */
    private State afterStopState = State.NEXT;

    private StationStep stationStep = StationStep.NONE;
    private BlockPos stationEcPos;
    private BlockPos stationBoxPos;
    private int stationTicks;
    private int stationRetry;
    private int stationBoxSlot = -1;
    private boolean stationNeedPack;
    private boolean stationNeedSupply;
    private int stationPackBefore;
    private boolean stationFromEc;
    private boolean stationEcOwned;
    private boolean stationBoxTakeMode;
    private boolean stationSupplyBoxPending;
    private boolean stationSupplyBoxDone;
    private int stationApplesBefore;
    private int lootApplesBefore = -1;
    private int lootCooldown;

    /** 放置多候选：一次算好 8 个点，每个点点 2 次不中换下一个。 */
    private List<BlockPos> placeCandidates = new ArrayList<>();
    private int placeIndex;
    private int placeClickAttempts;
    private int lastPlaceClick;
    /** 打开箱子前是否已经把「站在箱子上」这件事处理掉了。 */
    private boolean standingMovedOff;
    private int ecOpenFailCount;
    /** 装盒卡槽检测：同一个背包槽连续这么久还被点，说明盒子其实满了。 */
    private int fillStuckSlot = -1;
    private int fillStuckTicks;
    /** 补给时连续这么久没取到东西，就判定拿完了。 */
    private int supplyEmptyTicks;
    /** 这次补给没拿到图腾（末影箱里可能没有），本轮不再因为图腾反复停靠。 */
    private boolean totemSupplyUnavailable;
    private ItemStack stationOriginalOffhand = ItemStack.EMPTY;

    private WLabel statusStage;
    private WLabel statusCity;
    private WLabel statusOpened;
    private WLabel statusCollected;
    private WLabel statusPacked;
    private WLabel statusRepair;
    private WLabel statusElytra;
    private WLabel statusPickaxe;
    private WLabel statusBottles;
    private WLabel statusFailed;
    private WLabel statusBlacklist;

    private int repairUsed;
    private boolean repairThenSelect;
    private int repairAttempts;
    private boolean repairModeB;
    private boolean repairTriedFallback;

    public FOEnchantedGoldCollector() {
        super(AddonTemplate.CATEGORY, "FO 附魔金采集",
            "自动飞向古城、下竖井、只取附魔金苹果；满 64 自动存潜影盒，"
                + "并按阈值补给经验瓶、修复鞘翅与镐子。需先关闭古城搜索的自动偷取。");

        linkSearchSetting = sgGeneral.add(new BoolSetting.Builder()
            .name("联动古城搜索")
            .description("读取古城战利品搜索的附魔金/两者标记，只处理这些箱子")
            .defaultValue(true)
            .build());

        autoSearchSetting = sgGeneral.add(new BoolSetting.Builder()
            .name("无标记自动搜索")
            .description("没有可用标记时，调用古城搜索模块跑一次搜索；对方模块未启用则暂停提示")
            .defaultValue(true)
            .build());

        targetApplesSetting = sgGeneral.add(new IntSetting.Builder()
            .name("目标数量")
            .description("累计拿到多少个附魔金苹果后停机（入盒后仍计入）")
            .defaultValue(64)
            .min(1).max(576)
            .sliderMin(1).sliderMax(256)
            .build());

        maxChestsPerCitySetting = sgGeneral.add(new IntSetting.Builder()
            .name("每城最多开箱")
            .description("同一座城最多处理多少个目标箱，防止单城耗尽时间")
            .defaultValue(8)
            .min(1).max(64)
            .sliderMin(1).sliderMax(32)
            .build());

        minHeightSetting = sgFlight.add(new IntSetting.Builder()
            .name("巡航最低高度")
            .description("低于此高度改用抬头爬升；死守 230，再低就可能撞山")
            .defaultValue(230)
            .min(-32).max(320)
            .sliderMin(0).sliderMax(320)
            .build());

        maxHeightSetting = sgFlight.add(new IntSetting.Builder()
            .name("巡航最高高度")
            .description("高于此高度改用滑翔下降；默认 300（世界顶 320）")
            .defaultValue(300)
            .min(0).max(320)
            .sliderMin(0).sliderMax(320)
            .build());

        riseHeightSetting = sgFlight.add(new IntSetting.Builder()
            .name("提升高度")
            .description("起飞后先爬到此高度再巡航（默认 300）")
            .defaultValue(300)
            .min(0).max(320)
            .sliderMin(0).sliderMax(320)
            .build());

        pitchRotateUpSetting = sgFlight.add(new DoubleSetting.Builder()
            .name("抬头速度")
            .description("俯仰平滑开启时，抬头爬升每游戏刻转多少度（默认 5.45）")
            .defaultValue(5.45)
            .min(1.0).max(20.0)
            .sliderMin(1.0).sliderMax(12.0)
            .build());

        pitchRotateDownSetting = sgFlight.add(new DoubleSetting.Builder()
            .name("低头速度")
            .description("俯仰平滑开启时，俯冲低头每游戏刻转多少度（默认 0.9）")
            .defaultValue(0.9)
            .min(0.5).max(2.0)
            .sliderMin(0.5).sliderMax(2.0)
            .build());

        autoFireworkBoostSetting = sgFlight.add(new BoolSetting.Builder()
            .name("自动补烟花")
            .description("只在抬头速度不足或低于最低高度时补，俯冲段不烧烟花")
            .defaultValue(true)
            .build());

        fireworkVelocityThresholdSetting = sgFlight.add(new DoubleSetting.Builder()
            .name("补烟花速度阈值")
            .description("抬头阶段垂直速度低于此值才补烟花（负值=已在下坠）")
            .defaultValue(-0.35)
            .min(-0.5).max(1.0)
            .sliderMin(-0.5).sliderMax(0.5)
            .build());

        fireworkBoostCooldownSetting = sgFlight.add(new IntSetting.Builder()
            .name("补烟花冷却")
            .description("两发补烟花之间至少间隔多少游戏刻")
            .defaultValue(10)
            .min(0).max(100)
            .sliderMin(0).sliderMax(100)
            .build());

        slowFallBoostSetting = sgFlight.add(new BoolSetting.Builder()
            .name("缓降辅助")
            .description("降到落点 3 格内时每 2 刻反向一次，原地荡着下降")
            .defaultValue(true)
            .build());

        ascendThrustSetting = sgFlight.add(new DoubleSetting.Builder()
            .name("升井补烟花速度")
            .description("竖井内垂直上升速度低于此值就补烟花（一次补 2 发）")
            .defaultValue(0.3)
            .min(0.0).max(1.5)
            .sliderMin(0.0).sliderMax(1.0)
            .build());

        minFireworksSetting = sgFlight.add(new IntSetting.Builder()
            .name("最低烟花数量")
            .description("少于这个数量拒绝起飞")
            .defaultValue(64)
            .min(1).max(512)
            .sliderMin(1).sliderMax(256)
            .build());

        landingRetriesSetting = sgFlight.add(new IntSetting.Builder()
            .name("落点重选次数")
            .description("落点是水/岩浆/悬空时最多换几个落点")
            .defaultValue(3)
            .min(1).max(10)
            .sliderMin(1).sliderMax(10)
            .build());

        fireworkIntervalSetting = sgFlight.add(new DoubleSetting.Builder()
            .name("烟花间隔")
            .description("巡航时两发烟花之间至少间隔多少秒（p40 默认 2.0）")
            .defaultValue(2.0)
            .min(0.5).max(10.0)
            .sliderMin(0.5).sliderMax(5.0)
            .build());

        pitchSmoothSetting = sgFlight.add(new BoolSetting.Builder()
            .name("俯仰平滑")
            .description("俯仰角逐游戏刻过渡，避免瞬间抬头/低头触发反作弊")
            .defaultValue(true)
            .build());

        fluidLookaheadSetting = sgShaft.add(new IntSetting.Builder()
            .name("流体前瞻格数")
            .description("每下挖一格，额外向下检查多少格")
            .defaultValue(2)
            .min(0).max(6)
            .sliderMin(0).sliderMax(6)
            .build());

        blacklistOnFluidSetting = sgShaft.add(new BoolSetting.Builder()
            .name("遇流体拉黑当前箱")
            .description("竖井或通道遇水/岩浆时，拉黑当前箱子并换位置")
            .defaultValue(true)
            .build());

        abandonUnderwaterSetting = sgShaft.add(new BoolSetting.Builder()
            .name("水下古城自动放弃")
            .description("从城顶到地表整列被水淹时，整城拉黑放弃")
            .defaultValue(true)
            .build());

        wardenDistanceSetting = sgShaft.add(new IntSetting.Builder()
            .name("监守者安全距离")
            .description("监守者进入此范围立即暂停作业（0 表示不检测）")
            .defaultValue(24)
            .min(0).max(64)
            .sliderMin(0).sliderMax(64)
            .build());

        autoPackSetting = sgStorage.add(new BoolSetting.Builder()
            .name("自动存盒")
            .description("附魔金达到目标或背包满时，自动装进潜影盒")
            .defaultValue(true)
            .build());

        storageModeSetting = sgStorage.add(new EnumSetting.Builder<StorageMode>()
            .name("存盒方式")
            .description("末影箱加潜影盒：存完把盒子放回末影箱；随身潜影盒：盒子留在背包")
            .defaultValue(StorageMode.ENDER_CHEST)
            .build());

        minEmptyBoxesSetting = sgStorage.add(new IntSetting.Builder()
            .name("最少空潜影盒")
            .description("背包/末影箱里少于这个数量时暂停提示")
            .defaultValue(1)
            .min(1).max(16)
            .sliderMin(1).sliderMax(8)
            .build());

        packThresholdSetting = sgStorage.add(new IntSetting.Builder()
            .name("存盒门槛")
            .description("背包里攒够多少个附魔金苹果才停靠存盒（不满不存）")
            .defaultValue(64)
            .min(1).max(576)
            .sliderMin(1).sliderMax(256)
            .build());

        autoRepairSetting = sgRepair.add(new BoolSetting.Builder()
            .name("自动修复装备")
            .description("鞘翅或镐子低于阈值时，用地表停靠投附魔之瓶修复")
            .defaultValue(true)
            .build());

        elytraThresholdSetting = sgRepair.add(new DoubleSetting.Builder()
            .name("鞘翅修复阈值")
            .description("鞘翅剩余耐久低于此百分比触发修复")
            .defaultValue(20.0)
            .min(1.0).max(90.0)
            .sliderMin(1.0).sliderMax(90.0)
            .build());

        pickaxeThresholdSetting = sgRepair.add(new DoubleSetting.Builder()
            .name("镐子修复阈值")
            .description("镐子剩余耐久低于此百分比触发修复")
            .defaultValue(20.0)
            .min(1.0).max(90.0)
            .sliderMin(1.0).sliderMax(90.0)
            .build());

        repairTargetSetting = sgRepair.add(new DoubleSetting.Builder()
            .name("修复目标")
            .description("修到此百分比以上停止投瓶（默认 100：鞘翅低于 20% 就一次修满）")
            .defaultValue(100.0)
            .min(30.0).max(100.0)
            .sliderMin(30.0).sliderMax(100.0)
            .build());

        minSafeDurabilitySetting = sgRepair.add(new IntSetting.Builder()
            .name("最低安全耐久")
            .description("剩余耐久低于此点数时不再投瓶，先换备用装备")
            .defaultValue(8)
            .min(1).max(64)
            .sliderMin(1).sliderMax(64)
            .build());

        autoSupplyBottlesSetting = sgRepair.add(new BoolSetting.Builder()
            .name("自动补充经验瓶")
            .description("经验瓶低于最低数量时，从末影箱补到目标数量")
            .defaultValue(true)
            .build());

        minBottlesSetting = sgRepair.add(new IntSetting.Builder()
            .name("经验瓶最低数量")
            .description("低于此数量先补给，不带着几瓶硬修")
            .defaultValue(32)
            .min(1).max(512)
            .sliderMin(1).sliderMax(256)
            .build());

        targetBottlesSetting = sgRepair.add(new IntSetting.Builder()
            .name("经验瓶目标数量")
            .description("补给时补到此数量（默认约 3 组）")
            .defaultValue(192)
            .min(1).max(1024)
            .sliderMin(32).sliderMax(576)
            .build());

        minTotemsSetting = sgRepair.add(new IntSetting.Builder()
            .name("最低图腾数量")
            .description("不死图腾少于这个数量就停靠补给；任何一次停靠补给都会顺带补到该数量")
            .defaultValue(5)
            .min(1).max(64)
            .sliderMin(1).sliderMax(16)
            .build());

        maxBottlesPerRepairSetting = sgRepair.add(new IntSetting.Builder()
            .name("每次修复最多瓶数")
            .description("一次停靠最多投多少瓶，防止异常消耗")
            .defaultValue(128)
            .min(1).max(512)
            .sliderMin(1).sliderMax(256)
            .build());

        mainHandPickaxeSetting = sgRepair.add(new BoolSetting.Builder()
            .name("修复时主手镐")
            .description("开：主手镐、副手投瓶；服务器不认副手时自动回退主手投瓶、副手镐")
            .defaultValue(true)
            .build());

        pauseWithoutBottlesSetting = sgRepair.add(new BoolSetting.Builder()
            .name("无经验瓶时暂停")
            .description("关闭后允许不带病飞行；默认开启，宁停不摔")
            .defaultValue(true)
            .build());

        stageTimeoutSetting = sgSafety.add(new IntSetting.Builder()
            .name("单阶段超时")
            .description("同一阶段卡住多少秒后暂停（0 表示不检测）")
            .defaultValue(20)
            .min(0).max(300)
            .sliderMin(0).sliderMax(120)
            .build());

        returnWhenFullSetting = sgSafety.add(new BoolSetting.Builder()
            .name("背包满自动返航")
            .description("背包没有空位时先回地表存盒，再继续")
            .defaultValue(true)
            .build());

        searchModule = Modules.get().get(FOAncientCitySearch.class);
    }

    @Override
    public void onActivate() {
        loadStore();
        state = State.PREPARE;
        stateTicks = 0;
        statusText = "准备中";
        cities = List.of();
        currentCity = null;
        currentChest = null;
        cityIndex = 0;
        openedInCity = 0;
        fakeCityFailures = 0;
        fakeCityBlacklistPending = false;
        totalCollected = 0;
        packedTotal = 0;
        openedTotal = 0;
        failedTotal = 0;
        repairCount = 0;
        autoSearchRequested = false;
        searchWaitTicks = 0;
        rescanAttempts = 0;
        totemSupplyUnavailable = false;
        needRepairAfterAscent = false;
        pauseAfterAscent = false;
        surfaceEvaluated = false;
        stationStep = StationStep.NONE;
        stationNeedPack = false;
        stationNeedSupply = false;
        stationEcPos = null;
        stationEcOwned = false;
        stationBoxTakeMode = false;
        stationSupplyBoxPending = false;
        stationSupplyBoxDone = false;
        stationBoxPos = null;
        stationBoxSlot = -1;
        stationOriginalOffhand = ItemStack.EMPTY;
        lootApplesBefore = -1;
        lootCooldown = 0;
        repairUsed = 0;
        repairAttempts = 0;
        repairModeB = false;
        repairTriedFallback = false;
        repairThenSelect = false;
        info("启动：目标 " + targetApplesSetting.get() + " 个附魔金苹果。");
    }

    @Override
    public void onDeactivate() {
        commitLootProgress();
        PathManagers.get().stop();
        PathManagers.get().setAllowBreak(true);
        releaseForward();
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
        }
        state = State.IDLE;
        statusText = "已关闭";
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WSection section = theme.section("采集状态", true);
        WTable table = section.add(theme.table()).expandX().widget();
        statusStage = addStatusRow(theme, table, "阶段", statusText);
        statusCity = addStatusRow(theme, table, "当前城市",
            currentCity == null ? "无" : "第 " + (cityIndex + 1) + " 座");
        statusOpened = addStatusRow(theme, table, "已开箱", String.valueOf(openedTotal));
        statusCollected = addStatusRow(theme, table, "已取附魔金", String.valueOf(totalCollected));
        statusPacked = addStatusRow(theme, table, "已入盒", String.valueOf(packedTotal));
        statusRepair = addStatusRow(theme, table, "修复次数", String.valueOf(repairCount));
        statusElytra = addStatusRow(theme, table, "鞘翅耐久", "—");
        statusPickaxe = addStatusRow(theme, table, "镐子耐久", "—");
        statusBottles = addStatusRow(theme, table, "经验瓶", "—");
        statusFailed = addStatusRow(theme, table, "失败次数", String.valueOf(failedTotal));
        statusBlacklist = addStatusRow(theme, table, "黑名单",
            store.cityCount(scope()) + " 城 / " + store.chestCount(scope()) + " 箱");

        WButton clearCities = section.add(theme.button("清空城市黑名单")).expandX().widget();
        clearCities.action = () -> {
            store.clearCities(scope());
            saveStore();
            info("已清空当前作用域的城市黑名单。");
        };
        WButton clearChests = section.add(theme.button("清空箱子黑名单")).expandX().widget();
        clearChests.action = () -> {
            store.clearChests(scope());
            saveStore();
            info("已清空当前作用域的箱子黑名单。");
        };
        return section;
    }

    private static WLabel addStatusRow(GuiTheme theme, WTable table, String name, String value) {
        table.add(theme.label(name));
        WLabel label = table.add(theme.label(value)).expandCellX().widget();
        table.row();
        return label;
    }

    /** 设置页打开时让状态区跟着 tick 刷新（WLabel 是可变文本，直接 set 即可）。 */
    private void updateStatusWidgets() {
        if (statusStage == null || mc.player == null) return;
        statusStage.set(statusText);
        statusCity.set(currentCity == null ? "无" : "第 " + (cityIndex + 1) + " 座");
        statusOpened.set(String.valueOf(openedTotal));
        statusCollected.set(String.valueOf(totalCollected));
        statusPacked.set(String.valueOf(packedTotal));
        statusRepair.set(String.valueOf(repairCount));
        statusFailed.set(String.valueOf(failedTotal));
        statusBlacklist.set(store.cityCount(scope()) + " 城 / " + store.chestCount(scope()) + " 箱");
        ItemStack elytra = elytraStack();
        statusElytra.set(elytra.getItem() == Items.ELYTRA
            ? String.format("%.0f%%", GearRepairLogic.remainingPercent(elytra.getMaxDamage(), elytra.getDamage()))
            : "未装备");
        ItemStack pick = pickaxeStack();
        statusPickaxe.set(pick.isEmpty()
            ? "无" : String.format("%.0f%%", GearRepairLogic.remainingPercent(pick.getMaxDamage(), pick.getDamage())));
        statusBottles.set(String.valueOf(countBottles()));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (state == State.IDLE || state == State.DONE || state == State.PAUSE) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) {
            pause("已离开主世界，附魔金采集停止");
            return;
        }

        stateTicks++;
        if (stateTicks % 5 == 0) updateStatusWidgets();
        if (stageTimeoutSetting.get() > 0
            && stateTicks > stageTimeoutSetting.get() * 20
            && state != State.SELECT_CITY && state != State.FLY && state != State.ARRIVE
            && state != State.PREP_AHEAD && state != State.SHAFT_PRECHECK
            && state != State.SHAFT_DIG && state != State.LOOT
            && state != State.ASCEND && state != State.STATION && state != State.REPAIR) {
            pause("阶段 " + statusText + " 超时，已暂停");
            return;
        }

        try {
            switch (state) {
                case PREPARE -> onPrepare();
                case SELECT_CITY -> onSelectCity();
                case FLY -> onFly();
                case ARRIVE -> onArrive();
                case PREP_AHEAD -> onPrepAhead();
                case SHAFT_PRECHECK -> onShaftPrecheck();
                case SHAFT_DIG -> onShaftDig();
                case LOOT -> onLoot();
                case ASCEND -> onAscend();
                case SURFACE_STOP -> onSurfaceStop();
                case STATION -> onStation();
                case REPAIR -> onRepair();
                case NEXT -> onNext();
                default -> {
                }
            }
        } catch (Throwable t) {
            error("任务异常：" + t);
            t.printStackTrace();
            pause("任务异常，已暂停");
        }
    }

    // ==================== 主流程 ====================

    private void onPrepare() {
        if (mc.world.getRegistryKey() != World.OVERWORLD) {
            pause("请在主世界使用附魔金采集（古城只生成在主世界）");
            return;
        }
        if (searchModule == null) {
            pause("未找到「FO 古城战利品搜索」模块，请先安装/启用");
            return;
        }
        if (!searchModule.isActive()) {
            pause("请先启用「FO 古城战利品搜索」模块");
            return;
        }
        if (!linkSearchSetting.get()) {
            pause("请开启「联动古城搜索」，否则没有目标数据");
            return;
        }
        if (searchModule.isAutoStealEnabled()) {
            pause("请先关闭古城搜索的「自动偷取」，避免两个模块抢同一个箱子");
            return;
        }
        if (PathManagers.get() instanceof com.fo.addon.pathing.NopPathManager) {
            pause("未检测到 Baritone，无法走到井口/箱子旁；请安装 Baritone 后再启用");
            return;
        }
        if (!hasElytraEquipped() && findElytraSlot() == -1) {
            pause("背包里没有鞘翅，无法飞行");
            return;
        }
        if (findPickaxeSlot() == -1) {
            if (!isPickaxe(mc.player.getOffHandStack().getItem())) {
                pause("背包里没有镐子");
                return;
            }
            int empty = findEmptyInventorySlot();
            if (empty == -1) {
                pause("背包已满，无法把副手镐移到背包");
                return;
            }
            InvUtils.move().fromOffhand().to(empty);
        }
        if (countFireworks() < minFireworksSetting.get()) {
            pause("烟花不足 " + minFireworksSetting.get() + " 发，拒绝起飞");
            return;
        }
        if (autoPackSetting.get() && countEmptyBoxes() < minEmptyBoxesSetting.get()) {
            warning("背包里只有 " + countEmptyBoxes() + " 个空潜影盒（设置最少 "
                + minEmptyBoxesSetting.get() + " 个），满 64 时会先尝试从末影箱取。");
        }
        if (autoRepairSetting.get() && countBottles() < minBottlesSetting.get()) {
            warning("经验瓶只有 " + countBottles() + " 个，低于最低值；停靠时会先尝试补给。");
        }
        if (!hasEnoughFood()) {
            warning("食物少于 8 个，长时间作业可能饿；建议先补给。");
        }
        if (autoRepairSetting.get() && elytraNeedsRepairOrPickaxe()) {
            if (countBottles() < minBottlesSetting.get()) {
                warning("装备低于修复阈值，但经验瓶不足 " + minBottlesSetting.get() + " 个；先继续，停靠时会补给。");
            } else {
                info("起飞前先修复装备。");
                repairThenSelect = true;
                state = State.REPAIR;
                stateTicks = 0;
                statusText = "起飞前修复";
                return;
            }
        }
        info("装备检查通过，开始读取古城标记。");
        state = State.SELECT_CITY;
        stateTicks = 0;
        statusText = "选城";
    }

    private void onSelectCity() {
        if (searchModule.isSearching() || searchModule.hasPendingResults()) {
            statusText = "等待古城搜索";
            return;
        }
        String scope = scope();
        if (scope == null) {
            pause("当前没有世界作用域");
            return;
        }
        List<AncientChestStore.Mark> marks = searchModule.currentMarks();
        List<AncientChestStore.Mark> targets = EnchantedGoldCityLogic.filterTargets(marks, m ->
            !searchModule.isChestOpened(m.x(), m.z())
                && !store.isChestBlacklisted(scope, m.x(), m.y(), m.z())
                && !store.isCityBlacklisted(scope, m.x(), m.z()));

        if (targets.isEmpty()) {
            if (linkSearchSetting.get() && autoSearchSetting.get() && !autoSearchRequested) {
                autoSearchRequested = true;
                searchWaitTicks = 0;
                info("没有可用附魔金标记，触发一次古城搜索。");
                searchModule.startAutoSearch();
                statusText = "等待古城搜索";
                return;
            }
            if (autoSearchRequested) {
                searchWaitTicks++;
                if (searchWaitTicks > 20 * 15) {
                    pause("自动搜索完成但仍没有可用目标");
                } else {
                    statusText = "等待古城搜索";
                }
                return;
            }
            info("没有可处理的附魔金标记，任务结束。");
            state = State.DONE;
            statusText = "完成";
            return;
        }

        cities = EnchantedGoldCityLogic.sortedByDistance(
            EnchantedGoldCityLogic.groupCities(EnchantedGoldCityLogic.distinct(targets), 128),
            mc.player.getX(), mc.player.getZ());
        if (cities.isEmpty()) {
            pause("目标聚类为空");
            return;
        }
        currentCity = cities.get(0);
        cityIndex = 0;
        openedInCity = 0;
        fakeCityFailures = 0;
        if (!pickChestInCity()) {
            rotateToNextCity();
            return;
        }
        beginFlight();
    }

    /** 在当前城里挑离玩家最近、未开、未拉黑的箱子。 */
    private boolean pickChestInCity() {
        if (currentCity == null) return false;
        String scope = scope();
        if (scope == null) return false;
        boolean onlyFat = searchModule.isOnlyFatChest();
        int fatThreshold = Math.max(1, searchModule.getFatChestThreshold());
        List<AncientChestStore.Mark> usable = EnchantedGoldCityLogic.filterTargets(currentCity.marks(), m ->
            FatFilterLogic.keep(m.goldCount(), onlyFat, fatThreshold)
                && !searchModule.isChestOpened(m.x(), m.z())
                && !store.isChestBlacklisted(scope, m.x(), m.y(), m.z()));
        // 肥箱优先：已知附魔金个数多的先飞，数量未知的排最后；同档按距离
        usable.sort(Comparator
            .comparingInt(AncientChestStore.Mark::goldCount).reversed()
            .thenComparingDouble(m -> {
                double dx = m.x() + 0.5 - mc.player.getX();
                double dz = m.z() + 0.5 - mc.player.getZ();
                return dx * dx + dz * dz;
            }));
        if (usable.isEmpty()) return false;
        currentChest = usable.get(0);
        return true;
    }

    private void rotateToNextCity() {
        if (cities == null || cities.isEmpty()) {
            state = State.DONE;
            statusText = "完成";
            return;
        }
        // 用循环而不是递归：搜索半径大时城市可能有上百座，
        // 全是没有可用箱子的城时递归会一路压栈。
        while (true) {
            cityIndex++;
            if (cityIndex >= cities.size()) {
                if (rescanAttempts < 3 && linkSearchSetting.get() && autoSearchSetting.get()) {
                    rescanAttempts++;
                    autoSearchRequested = false;
                    searchWaitTicks = 0;
                    cities = List.of();
                    currentCity = null;
                    info("首批城市跑完，重读标记重新聚类（第 " + rescanAttempts + "/3 次）。");
                    state = State.SELECT_CITY;
                    stateTicks = 0;
                    statusText = "重新搜城";
                    return;
                }
                info("所有城市都处理完了，任务结束。");
                state = State.DONE;
                statusText = "完成";
                return;
            }
            currentCity = cities.get(cityIndex);
            openedInCity = 0;
            fakeCityFailures = 0;
            fakeCityBlacklistPending = false;
            if (pickChestInCity()) {
                beginFlight();
                return;
            }
        }
    }

    private void beginFlight() {
        if (currentChest == null) {
            state = State.DONE;
            return;
        }
        PathManagers.get().setAllowBreak(true);
        flightTargetX = currentChest.x() + 0.5;
        flightTargetZ = currentChest.z() + 0.5;
        landingTargetY = surfaceY(currentChest.x(), currentChest.z());
        climbing = mc.player.getY() <= minHeightSetting.get();
        firstFireworkDone = false;
        lastFireworkTick = 0;
        fireworkBoostCooldown = 0;
        fireworkCooldown = 0;
        glideFailTicks = 0;
        glideRecoverTicks = 0;
        glideRecovering = false;
        landingRecover = false;
        recoverStageTick = 0;
        recoverCount = 0;
        smoothedPitch = mc.player.getPitch();

        if (mc.player.isGliding()) {
            // 出井后还没到拉升高度：-85° 垂直顶上去；已经够高就直接 p40 巡航
            flightPhase = mc.player.getY() < riseHeightSetting.get()
                ? FlightPhase.SHAFT_CLIMB : FlightPhase.FLYING;
        } else {
            flightPhase = FlightPhase.RISING;
        }
        state = State.FLY;
        stateTicks = 0;
        statusText = "飞向目标";
        info("目标箱已选定，水平距离 "
            + (int) Math.sqrt(horizontalDistanceSq(flightTargetX, flightTargetZ)) + " 格。");
    }

    private void onFly() {
        flightTicksTotal++;
        if (flightTicksTotal > 20 * 600) {
            pause("飞行超过 10 分钟仍未到达，已暂停");
            return;
        }
        if (stateTicks % 20 == 0 && countFireworks() < 4
            && flightPhase != FlightPhase.LANDING) {
            warning("烟花剩余 " + countFireworks() + " 发，就近迫降补给。");
            enterLowFuelLanding();
            return;
        }
        switch (flightPhase) {
            case RISING -> flyRising();
            case SHAFT_CLIMB -> flyShaftClimb();
            case FLYING -> flyFlying();
            case LANDING -> flyLanding();
        }
    }

    /** 地面起飞：头顶开阔才起跳，展翅后一路顶到拉升高度。 */
    private void flyRising() {
        if (stateTicks > 400) {
            pause("起飞失败：没穿鞘翅或没有烟花");
            return;
        }
        if (!mc.player.isGliding()) {
            mc.player.setPitch(-45.0f);
            mc.options.forwardKey.setPressed(true);
            if (mc.player.isOnGround()) {
                if (!canTakeOffHere()) {
                    BlockPos spot = findTakeoffSpot();
                    if (spot != null && stateTicks % 20 == 1 && !PathManagers.get().isPathing()) {
                        statusText = "走到开阔点";
                        PathManagers.get().moveTo(spot, false);
                    }
                    if (stateTicks > 20 * 10) {
                        pause("起飞点头顶被挡，找不到开阔位置");
                    }
                    return;
                }
                PathManagers.get().stop();
                mc.player.jump();
            } else if (stateTicks % 4 == 0) {
                sendStartGliding();
            }
            return;
        }

        // 已展翅：首发立即打，之后按烟花间隔顶到拉升高度
        if (!firstFireworkDone) {
            useFirework();
            firstFireworkDone = true;
            lastFireworkTick = stateTicks;
        } else if (stateTicks - lastFireworkTick >= fireworkTicks()) {
            useFirework();
            lastFireworkTick = stateTicks;
        }
        rotateTo(yawTo(flightTargetX, flightTargetZ), -45.0f);
        mc.options.forwardKey.setPressed(true);
        if (mc.player.getY() >= riseHeightSetting.get()) {
            flightPhase = FlightPhase.FLYING;
            stateTicks = 0;
            statusText = "巡航";
        }
    }

    /** 出井后：-85° 近乎垂直，靠烟花一路顶到拉升高度，中途不做水平巡航。 */
    private void flyShaftClimb() {
        if (!mc.player.isGliding()) {
            if (stateTicks % 4 == 0) sendStartGliding();
            if (stateTicks % 20 == 0) useFirework();
            rotateTo(yawTo(flightTargetX, flightTargetZ), -85.0f);
            mc.options.forwardKey.setPressed(true);
            return;
        }
        rotateTo(yawTo(flightTargetX, flightTargetZ), -85.0f);
        mc.options.forwardKey.setPressed(true);
        if (fireworkCooldown > 0) fireworkCooldown--;
        if (fireworkCooldown == 0) {
            useFirework();
            fireworkCooldown = fireworkTicks();
        }
        if (mc.player.getY() >= riseHeightSetting.get()) {
            flightPhase = FlightPhase.FLYING;
            stateTicks = 0;
            statusText = "巡航";
        }
    }

    /** p40 巡航：230 以下才抬头，300 以上才停抬，中间一律 37.72° 俯冲。 */
    private void flyFlying() {
        if (!mc.player.isGliding()) {
            if (mc.player.isOnGround()) {
                // 半路落地：重新起飞
                firstFireworkDone = false;
                flightPhase = FlightPhase.RISING;
                stateTicks = 0;
                return;
            }
            handleGlideFailure();
            return;
        }
        glideFailTicks = 0;
        glideRecoverTicks = 0;
        glideRecovering = false;

        double dist = Math.sqrt(horizontalDistanceSq(flightTargetX, flightTargetZ));

        if (climbing) {
            if (mc.player.getY() >= maxHeightSetting.get()) climbing = false;
        } else if (mc.player.getY() <= minHeightSetting.get()) {
            climbing = true;
        }
        climbing = FlightHysteresisLogic.nextClimbing(mc.player.getY(),
            minHeightSetting.get(), maxHeightSetting.get(), climbing);

        float pitch = FlightHysteresisLogic.pitch(climbing);
        if (pitchSmoothSetting.get()) {
            float step = pitch < smoothedPitch
                ? pitchRotateUpSetting.get().floatValue()
                : pitchRotateDownSetting.get().floatValue();
            smoothedPitch = stepAngle(smoothedPitch, pitch, step);
            pitch = smoothedPitch;
        } else {
            smoothedPitch = pitch;
        }
        mc.player.setYaw(stepAngle(mc.player.getYaw(),
            yawTo(flightTargetX, flightTargetZ), 6.0f));
        mc.player.setPitch(pitch);
        mc.options.forwardKey.setPressed(true);

        if (fireworkBoostCooldown > 0) fireworkBoostCooldown--;
        if (autoFireworkBoostSetting.get()
            && fireworkBoostCooldown == 0
            && dist > 20.0
            && mc.player.getY() < maxHeightSetting.get()) {
            boolean tooSlow = climbing
                && mc.player.getVelocity().y < fireworkVelocityThresholdSetting.get();
            boolean tooLow = mc.player.getY() < minHeightSetting.get();
            if (tooSlow || tooLow) {
                useFirework();
                fireworkBoostCooldown = Math.max(fireworkBoostCooldownSetting.get(), fireworkTicks());
            }
        }

        if (dist <= 3.0) {
            flightPhase = FlightPhase.LANDING;
            stateTicks = 0;
            statusText = "进近降落";
        }
    }

    /** 直线进近：只俯冲不抬头，距目标 3 格内松前进缓降。 */
    private void flyLanding() {
        double dist = Math.sqrt(horizontalDistanceSq(flightTargetX, flightTargetZ));

        if (landingRecover) {
            recoverStageTick++;
            mc.player.setYaw(recoveryYaw);
            mc.player.setPitch(-54.77f);
            mc.options.forwardKey.setPressed(true);
            if (fireworkCooldown > 0) fireworkCooldown--;
            if (fireworkCooldown == 0) {
                useFirework();
                fireworkCooldown = fireworkTicks();
            }
            if (mc.player.getY() >= landingTargetY + 25 || recoverStageTick > 600) {
                landingRecover = false;
                recoverStageTick = 0;
                stateTicks = 0;
                info("已拉升到降落点上方 25 格，重新进近。");
            }
            return;
        }

        if (!mc.player.isOnGround() && mc.player.getY() < landingTargetY - 10) {
            recoverCount++;
            if (recoverCount > 3) {
                pause("多次低于降落点，无法降落");
                return;
            }
            landingRecover = true;
            recoverStageTick = 0;
            recoveryYaw = yawTo(flightTargetX, flightTargetZ) + 180.0f;
            firstFireworkDone = false;
            info("低于降落点 10 格，反向拉升到上方 25 格。");
            return;
        }

        if (mc.player.isOnGround()) {
            recoverCount = 0;
            releaseForward();
            if (dist <= 3.0) {
                state = State.ARRIVE;
                stateTicks = 0;
                statusText = "到达检查";
                return;
            }
            if (stateTicks % 20 == 1) {
                PathManagers.get().setAllowBreak(false);
                PathManagers.get().moveToPrecise(
                    new BlockPos((int) flightTargetX, (int) landingTargetY + 1, (int) flightTargetZ));
            }
            if (stateTicks > 20 * 20) {
                PathManagers.get().setAllowBreak(true);
                blacklistCurrentChest(EnchantedGoldStore.REASON_UNREACHABLE);
            }
            return;
        }

        if (!mc.player.isGliding()) {
            sendStartGliding();
            return;
        }

        double dy = mc.player.getY() - (landingTargetY + 1);
        double d3 = horizontalDistanceSq(flightTargetX, flightTargetZ) + dy * dy;
        if (d3 > 400.0) {
            mc.player.setYaw(stepAngle(mc.player.getYaw(),
                yawTo(flightTargetX, flightTargetZ), 6.0f));
            mc.player.setPitch(55.0f);
            mc.options.forwardKey.setPressed(true);
            return;
        }
        if (dist > 3.0) {
            float pitch = LandingPitchLogic.pitchFor(dy, dist);
            mc.player.setYaw(stepAngle(mc.player.getYaw(),
                yawTo(flightTargetX, flightTargetZ), 6.0f));
            mc.player.setPitch(pitch);
            mc.options.forwardKey.setPressed(true);
        } else {
            releaseForward();
            mc.player.setPitch(0.0f);
            if (slowFallBoostSetting.get() && stateTicks % 2 == 0) {
                mc.player.setYaw(mc.player.getYaw() + 180.0f);
            }
        }
    }

    /** 滑翔意外中断：前 20 刻每 4 刻重发，之后抬头 + 烟花恢复，200 刻不行就停。 */
    private void handleGlideFailure() {
        glideFailTicks++;
        if (glideFailTicks <= 20) {
            if (glideFailTicks % 4 == 0) sendStartGliding();
            return;
        }
        glideRecovering = true;
        glideRecoverTicks++;
        mc.player.setPitch(-30.0f);
        mc.options.forwardKey.setPressed(true);
        if (glideRecoverTicks % 10 == 0) sendStartGliding();
        if (glideRecoverTicks % 40 == 0) useFirework();
        if (glideRecoverTicks > 200) {
            pause("滑翔 200 刻仍无法恢复，判定服务端拒绝起飞");
        }
    }

    /** 起飞点头顶 3×3×4 是否全空气。 */
    private boolean canTakeOffHere() {
        BlockPos p = mc.player.getBlockPos();
        boolean[] blocks = new boolean[36];
        int i = 0;
        for (int dy = 1; dy <= 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    blocks[i++] = mc.world.getBlockState(p.add(dx, dy, dz)).isAir();
                }
            }
        }
        return TakeoffClearanceLogic.canTakeOff(blocks);
    }

    /** 半径 8 内找一个头顶开阔的落脚点。 */
    private BlockPos findTakeoffSpot() {
        BlockPos center = mc.player.getBlockPos();
        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    if (!isChunkLoaded(x, z)) continue;
                    BlockPos ground = new BlockPos(x, surfaceY(x, z), z);
                    if (!mc.world.getBlockState(ground).isSolidBlock(mc.world, ground)) continue;
                    if (!canTakeOffAt(ground.up())) continue;
                    return ground.up();
                }
            }
        }
        return null;
    }

    private boolean canTakeOffAt(BlockPos feet) {
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!mc.world.getBlockState(feet.add(dx, dy, dz)).isAir()) return false;
                }
            }
        }
        return true;
    }

    /** 烟花不够了：改降落到最近的安全地面。 */
    private void enterLowFuelLanding() {
        BlockPos safe = findSafeLandingSpot();
        if (safe != null) {
            flightTargetX = safe.getX() + 0.5;
            flightTargetZ = safe.getZ() + 0.5;
            landingTargetY = safe.getY();
        } else {
            landingTargetY = surfaceY((int) flightTargetX, (int) flightTargetZ);
        }
        flightPhase = FlightPhase.LANDING;
        landingRecover = false;
        stateTicks = 0;
        statusText = "烟花不足，就近迫降";
    }

    private void onArrive() {
        if (mc.player.isInLava()) {
            pause("落点在岩浆里，已暂停；请先回到安全地面");
            return;
        }
        if (mc.player.isTouchingWater() || !mc.player.isOnGround()) {
            if (mc.player.isTouchingWater() && stateTicks % 40 == 1) {
                if (landingFailures >= landingRetriesSetting.get()) {
                    pause("落点在水里，且多次找不到安全地面");
                    return;
                }
                BlockPos safe = findSafeLandingSpot();
                landingFailures++;
                if (safe != null) {
                    statusText = "离开水域";
                    PathManagers.get().moveToPrecise(safe);
                }
            }
            return;
        }
        landingFailures = 0;
        if (currentChest == null) {
            state = State.NEXT;
            return;
        }
        if (!isChunkLoaded(currentChest.x(), currentChest.z())) {
            statusText = "等待区块加载";
            return;
        }
        if (isWardenNear()) {
            pause("监守者进入 " + wardenDistanceSetting.get() + " 格范围，暂停作业");
            return;
        }

        // 落点必须对准箱子正上方：井就开在这里，偏了就用 Baritone 走回来
        double dx = mc.player.getX() - (currentChest.x() + 0.5);
        double dz = mc.player.getZ() - (currentChest.z() + 0.5);
        double offSq = dx * dx + dz * dz;
        if (offSq > 2.25) {
            statusText = "对准箱子上方";
            if (stateTicks % 20 == 1) {
                PathManagers.get().setAllowBreak(false);
                PathManagers.get().moveToPrecise(
                    new BlockPos(currentChest.x(), surfaceY(currentChest.x(), currentChest.z()) + 1,
                        currentChest.z()));
            }
            if (stateTicks > 20 * 20) {
                PathManagers.get().setAllowBreak(true);
                blacklistCurrentChest(EnchantedGoldStore.REASON_UNREACHABLE);
            }
            return;
        }
        PathManagers.get().stop();
        PathManagers.get().setAllowBreak(false);

        shaftColumn = new BlockPos(currentChest.x(), 0, currentChest.z());
        shaftTopY = surfaceY(currentChest.x(), currentChest.z());
        shaftChestY = currentChest.y();
        shaftDigY = shaftTopY;
        lastShaftColumn = shaftColumn;
        lastShaftTopY = shaftTopY;
        precheckDone = false;
        precheckVerdict = null;

        // 水下古城：整列水淹直接整城放弃（5 列预检也会抓水，这里是兜底）
        int top = shaftTopY;
        boolean[] column = sampleFluidColumn(currentChest.x(), currentChest.y() + 1, top, currentChest.z());
        if (abandonUnderwaterSetting.get() && FluidColumnLogic.isUnderwaterCity(column)) {
            if (currentCity != null) {
                store.blacklistCity(scope(), currentCity.centerX(), currentCity.centerZ(),
                    EnchantedGoldStore.REASON_UNDERWATER_CITY);
                saveStore();
            }
            warning("目标上方整列被水淹，判定水下古城，整城放弃。");
            rotateToNextCity();
            return;
        }

        state = State.PREP_AHEAD;
        stateTicks = 0;
        statusText = "落地后处理";
    }

    /** 到目标落地后：先存盒、再修鞘翅、最后补给；都不需要就 0 tick 跳过。 */
    private void onPrepAhead() {
        if (stateTicks == 1) {
            int packThreshold = Math.max(1, packThresholdSetting.get());
            boolean pack = autoPackSetting.get() && PackGateLogic.shouldPack(countApples(), packThreshold);
            boolean repair = autoRepairSetting.get() && elytraNeedsRepair();
            boolean supply = (autoSupplyBottlesSetting.get() && countBottles() < minBottlesSetting.get())
                || countFireworks() < minFireworksSetting.get()
                || (countTotems() < minTotemsSetting.get() && !totemSupplyUnavailable)
                || !hasEnoughFood();
            if (pack || supply) {
                stationNeedPack = pack;
                stationNeedSupply = supply;
                afterStopState = State.SHAFT_PRECHECK;
                startStation();
                return;
            }
            if (repair) {
                afterStopState = State.SHAFT_PRECHECK;
                state = State.REPAIR;
                stateTicks = 0;
                statusText = "修复鞘翅";
                return;
            }
        }
        state = State.SHAFT_PRECHECK;
        stateTicks = 0;
        statusText = "井道预检";
    }

    /** 扫一列：从井口地表到箱子层，返回最严重的阻断类型。 */
    private ShaftPrecheckLogic.ColumnScan scanColumn(int x, int z) {
        if (currentChest == null) return ShaftPrecheckLogic.ColumnScan.clean();
        int from = Math.min(shaftTopY, currentChest.y());
        int to = Math.max(shaftTopY, currentChest.y());
        ShaftPrecheckLogic.Blocker worst = ShaftPrecheckLogic.Blocker.NONE;
        int worstY = 0;
        for (int y = from; y <= to; y++) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState st = mc.world.getBlockState(p);
            ShaftPrecheckLogic.Blocker b = ShaftPrecheckLogic.Blocker.NONE;
            if (isFluid(p)) {
                b = ShaftPrecheckLogic.Blocker.FLUID;
            } else if (isUnbreakable(p, st)) {
                b = ShaftPrecheckLogic.Blocker.UNBREAKABLE;
            } else if (st.getBlock() == Blocks.SCULK_SHRIEKER) {
                b = ShaftPrecheckLogic.Blocker.SHRIEKER;
            } else if (st.getBlock() instanceof FallingBlock) {
                b = ShaftPrecheckLogic.Blocker.FALLING;
            } else if (st.getBlock() == Blocks.BUDDING_AMETHYST
                || st.getBlock() == Blocks.AMETHYST_BLOCK
                || st.getBlock() instanceof AmethystClusterBlock) {
                b = ShaftPrecheckLogic.Blocker.AMETHYST;
            }
            if (b != ShaftPrecheckLogic.Blocker.NONE
                && ShaftPrecheckLogic.worst(java.util.List.of(worst, b)) == b) {
                worst = b;
                worstY = y;
            }
        }
        return new ShaftPrecheckLogic.ColumnScan(worst, worstY);
    }

    /** 挖之前一次性扫 5 列：正下方 + 前后左右，见流体/不可破/尖啸体就拉黑该箱。 */
    private void onShaftPrecheck() {
        if (currentChest == null || shaftColumn == null) {
            state = State.NEXT;
            return;
        }
        if (!isChunkLoaded(shaftColumn.getX(), shaftColumn.getZ())) {
            statusText = "等待区块加载";
            return;
        }
        if (!precheckDone) {
            precheckDone = true;
            precheckVerdict = ShaftPrecheckLogic.evaluate(
                scanColumn(shaftColumn.getX(), shaftColumn.getZ()),
                scanColumn(shaftColumn.getX(), shaftColumn.getZ() - 1),
                scanColumn(shaftColumn.getX(), shaftColumn.getZ() + 1),
                scanColumn(shaftColumn.getX() - 1, shaftColumn.getZ()),
                scanColumn(shaftColumn.getX() + 1, shaftColumn.getZ()));
            if (!precheckVerdict.pass()) {
                warning(precheckVerdict.reason());
                String reason = precheckVerdict.blocker() == ShaftPrecheckLogic.Blocker.FLUID
                    ? EnchantedGoldStore.REASON_SHAFT_FLUID
                    : EnchantedGoldStore.REASON_NO_SAFE_MOUTH;
                blacklistCurrentChest(reason);
                return;
            }
        }
        shaftDigY = shaftTopY;
        state = State.SHAFT_DIG;
        stateTicks = 0;
        statusText = "下挖竖井（箱子正上方）";
    }

    private void onShaftDig() {
        if (currentChest == null || shaftColumn == null) {
            state = State.NEXT;
            return;
        }
        int chestY = currentChest.y();

        // 到箱子正上方一格就停手，绝不挖箱子
        if (mc.player.getBlockY() <= chestY + 1 && mc.player.isOnGround()) {
            releaseForward();
            PathManagers.get().stop();
            state = State.LOOT;
            stateTicks = 0;
            lootApplesBefore = -1;
            lootCooldown = 0;
            statusText = "开箱";
            return;
        }

        if (stateTicks % 40 == 0 && isWardenNear()) {
            abortShaft("监守者靠近", false, EnchantedGoldStore.REASON_NO_SAFE_MOUTH);
            return;
        }
        if (mc.player.isTouchingWater() || mc.player.isInLava()) {
            abortShaft("井内已经进水/岩浆", true, EnchantedGoldStore.REASON_SHAFT_FLUID);
            return;
        }

        // 严格 1×1：偏出箱子 X/Z 就先用前进键回到井中心
        if (mc.player.getBlockX() != shaftColumn.getX() || mc.player.getBlockZ() != shaftColumn.getZ()) {
            statusText = "回到井中心";
            mc.player.setYaw(yawTo(shaftColumn.getX() + 0.5, shaftColumn.getZ() + 0.5));
            mc.options.forwardKey.setPressed(true);
            return;
        }
        releaseForward();

        if (stateTicks % 20 == 1) ensurePickaxeInInventory();
        int pickSlot = findPickaxeSlot();
        if (pickSlot == -1) {
            pause("镐子不见了，停止下挖");
            return;
        }
        ItemStack pick = mc.player.getInventory().getStack(pickSlot);
        if (GearRepairLogic.belowSafety(pick.getMaxDamage(), pick.getDamage(),
            minSafeDurabilitySetting.get() + 24)) {
            needRepairAfterAscent = true;
            abortShaft("镐子耐久不足", false, null);
            return;
        }

        BlockPos digPos = mc.player.getBlockPos().down();
        BlockState st = mc.world.getBlockState(digPos);
        if (st.isAir()) {
            statusText = "下挖竖井 Y=" + digPos.getY();
            return;
        }
        if (st.getBlock() instanceof ChestBlock) {
            releaseForward();
            state = State.LOOT;
            stateTicks = 0;
            lootApplesBefore = -1;
            lootCooldown = 0;
            statusText = "到达箱子层";
            return;
        }
        if (isUnbreakable(digPos, st)) {
            abortShaft("遇到不可破坏方块", false, EnchantedGoldStore.REASON_NO_SAFE_MOUTH);
            return;
        }
        if (st.getBlock() == Blocks.SCULK_SHRIEKER) {
            abortShaft("遇到幽匿尖啸体", false, EnchantedGoldStore.REASON_NO_SAFE_MOUTH);
            return;
        }
        if (st.getBlock() instanceof FallingBlock) {
            abortShaft("遇到沙子/沙砾，可能塌方", false, EnchantedGoldStore.REASON_NO_SAFE_MOUTH);
            return;
        }

        ShaftFluidLogic.Risk risk = assessShaftRisk(digPos);
        if (ShaftFluidLogic.mustStop(risk)) {
            abortShaft(ShaftFluidLogic.reasonText(risk), true, EnchantedGoldStore.REASON_SHAFT_FLUID);
            return;
        }

        if (mineBlock(digPos, pickSlot)) {
            statusText = "下挖竖井 Y=" + digPos.getY();
        }
    }

    private void onLoot() {
        if (stateTicks % 20 == 0 && isWardenNear()) {
            pause("开箱时监守者进入安全距离，已暂停");
            return;
        }
        if (currentChest == null) {
            state = State.NEXT;
            return;
        }
        BlockPos chestPos = resolveChestPos();
        if (chestPos == null) {
            blacklistCurrentChest(EnchantedGoldStore.REASON_TARGET_MISSING);
            return;
        }
        if (!(mc.world.getBlockState(chestPos).getBlock() instanceof ChestBlock)) {
            blacklistCurrentChest(EnchantedGoldStore.REASON_TARGET_MISSING);
            return;
        }

        // 站在箱子正上方开箱（井就开在这里，不用绕到旁边）
        BlockPos stand = chestPos.up();
        if (!mc.player.getBlockPos().equals(stand)) {
            statusText = "站到箱子上方";
            double d2 = mc.player.squaredDistanceTo(
                stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            if (d2 > 1.0) {
                mc.player.setYaw(yawTo(stand.getX() + 0.5, stand.getZ() + 0.5));
                mc.options.forwardKey.setPressed(true);
                return;
            }
        }
        releaseForward();
        mc.player.setPitch(60.0f);

        if (isContainerOpen()) {
            ScreenHandler sh = mc.player.currentScreenHandler;
            int rows = containerRows(sh);
            if (rows <= 0) {
                mc.player.closeHandledScreen();
                return;
            }
            if (lootApplesBefore < 0) lootApplesBefore = countApples();
            if (lootCooldown > 0) {
                lootCooldown--;
                return;
            }
            if (lootOneApple(sh, rows)) {
                lootCooldown = 2;
                return;
            }
            if (state == State.PAUSE) return;
            int gained = countApples() - lootApplesBefore;
            lootApplesBefore = -1;
            mc.player.closeHandledScreen();
            openedTotal++;
            openedInCity++;
            int realGain = Math.max(0, gained);
            totalCollected += realGain;
            if (realGain == 0) {
                info("这个箱子没有附魔金苹果，标记为已开。");
            } else {
                info("取到 " + realGain + " 个附魔金苹果，累计 " + totalCollected + "。");
            }
            if (searchModule != null) {
                // 预测坐标与实际箱子可能差一格：两处都记入已开，避免下一轮又飞回来
                searchModule.markChestOpenedExternally(currentChest.x(), currentChest.y(), currentChest.z());
                searchModule.markChestOpenedExternally(chestPos.getX(), chestPos.getY(), chestPos.getZ());
            }
            afterLoot();
            return;
        }

        if (stateTicks % 10 == 1) {
            BlockClicker.clickBlockFacingPlayer(chestPos, true, Hand.MAIN_HAND);
        }
        if (stateTicks > 20 * 12) {
            blacklistCurrentChest(EnchantedGoldStore.REASON_TARGET_MISSING);
        }
    }

    /** 从容器里取一组附魔金；返回 true 表示这一 tick 做了动作，还要继续。 */
    private boolean lootOneApple(ScreenHandler sh, int rows) {
        int last = rows * 9;
        for (int i = 0; i < last; i++) {
            ItemStack stack = sh.getSlot(i).getStack();
            if (stack.isEmpty() || stack.getItem() != Items.ENCHANTED_GOLDEN_APPLE) continue;
            if (isInventoryFull()) {
                pause("背包已满，无法继续取附魔金");
                return false;
            }
            mc.interactionManager.clickSlot(sh.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            return true;
        }
        return false;
    }

    private void afterLoot() {
        // 取完就原地升井：不垫脚、不走横向通道；存盒/修复放到下一个目标落地后
        ascendPhase = AscendPhase.CENTER;
        ascendBurst = 0;
        ascendTicks = 0;
        releaseForward();
        state = State.ASCEND;
        stateTicks = 0;
        statusText = "原地升井";
    }

    /**
     * 原地升井：抬头 -90°，起跳展开鞘翅，靠烟花垂直冲出井口。
     *
     * <p>不做：垫脚方块、向上挖、走横向通道。竖井是 1×1 笔直的，人没离开过井中心，
     * 所以设计上不会失败；万一滑翔中断就重展开 + 补 2 发。
     */
    private void onAscend() {
        ascendTicks++;
        if (ascendTicks > 20 * 180) {
            pause("升井超时，已暂停");
            return;
        }
        if (shaftColumn == null) {
            state = State.NEXT;
            stateTicks = 0;
            return;
        }

        // 出井：井口地表 +6 格才算真正出来，之后由飞行段 -85° 继续顶到拉升高度
        if (mc.player.getY() >= shaftTopY + 6) {
            releaseForward();
            int packThreshold = Math.max(1, packThresholdSetting.get());
            boolean needStop = autoPackSetting.get() && PackGateLogic.shouldPack(countApples(), packThreshold);
            needStop |= autoRepairSetting.get() && elytraNeedsRepair();
            needStop |= autoSupplyBottlesSetting.get() && countBottles() < minBottlesSetting.get();
            needStop |= countFireworks() < minFireworksSetting.get();
            needStop |= countTotems() < minTotemsSetting.get() && !totemSupplyUnavailable;
            needStop |= !hasEnoughFood();
            if (needStop) {
                landForStop = true;
                afterStopState = State.NEXT;
                state = State.SURFACE_STOP;
                statusText = "落地停靠";
            } else {
                state = State.NEXT;
                statusText = "空中转场";
            }
            stateTicks = 0;
            return;
        }

        switch (ascendPhase) {
            case CENTER -> {
                double dx = mc.player.getX() - (shaftColumn.getX() + 0.5);
                double dz = mc.player.getZ() - (shaftColumn.getZ() + 0.5);
                if (Math.abs(dx) <= 0.15 && Math.abs(dz) <= 0.15) {
                    releaseForward();
                    ascendPhase = AscendPhase.LAUNCH;
                    return;
                }
                mc.player.setYaw(yawTo(shaftColumn.getX() + 0.5, shaftColumn.getZ() + 0.5));
                mc.options.forwardKey.setPressed(true);
                if (ascendTicks > 20 * 10) {
                    releaseForward();
                    ascendPhase = AscendPhase.LAUNCH;
                }
            }
            case LAUNCH -> {
                releaseForward();
                mc.player.setPitch(-90.0f);
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                    return;
                }
                if (!mc.player.isGliding()) {
                    sendStartGliding();
                    return;
                }
                useFirework();
                ascendPhase = AscendPhase.GLIDE;
            }
            case GLIDE -> {
                mc.player.setPitch(-90.0f);
                mc.options.forwardKey.setPressed(true);
                if (!mc.player.isGliding() && !mc.player.isOnGround()) {
                    sendStartGliding();
                    return;
                }
                if (ascendBurst > 0) {
                    useFirework();
                    ascendBurst--;
                    return;
                }
                if (mc.player.getVelocity().y < ascendThrustSetting.get()) {
                    // 掉速就一次补 2 发
                    ascendBurst = 2;
                }
            }
        }
    }

    private void onSurfaceStop() {
        if (!mc.player.isOnGround()) {
            if (landForStop) {
                // 出井时已经离井口 6 格以上，小俯角前进缓降即可
                mc.player.setPitch(15.0f);
                mc.options.forwardKey.setPressed(true);
            }
            return;
        }
        releaseForward();
        PathManagers.get().stop();

        if (pauseAfterAscent) {
            pauseAfterAscent = false;
            pause("竖井/通道遇到流体，已安全升井；按设置未拉黑，任务暂停");
            return;
        }
        if (fakeCityBlacklistPending) {
            fakeCityBlacklistPending = false;
            if (currentCity != null) {
                store.blacklistCity(scope(), currentCity.centerX(), currentCity.centerZ(),
                    EnchantedGoldStore.REASON_FAKE_CITY);
                saveStore();
                warning("同一座城连续 3 个目标失真，判定假城，整城拉黑。");
            }
            fakeCityFailures = 0;
            landForStop = false;
            rotateToNextCity();
            return;
        }
        if (!landForStop) {
            state = afterStopState;
            stateTicks = 0;
            return;
        }

        int packThreshold = Math.max(1, packThresholdSetting.get());
        boolean pack = autoPackSetting.get() && PackGateLogic.shouldPack(countApples(), packThreshold);
        boolean supply = (autoSupplyBottlesSetting.get() && countBottles() < minBottlesSetting.get())
            || countFireworks() < minFireworksSetting.get()
            || (countTotems() < minTotemsSetting.get() && !totemSupplyUnavailable)
            || !hasEnoughFood();
        boolean repair = autoRepairSetting.get() && elytraNeedsRepair();

        landForStop = false;
        if (pack || supply) {
            stationNeedPack = pack;
            stationNeedSupply = supply;
            startStation();
            return;
        }
        if (repair) {
            state = State.REPAIR;
            stateTicks = 0;
            statusText = "修复鞘翅";
            return;
        }
        state = afterStopState;
        stateTicks = 0;
    }

    private void onNext() {
        afterStopState = State.NEXT;
        if (totalCollected >= targetApplesSetting.get()) {
            if (autoPackSetting.get() && countApples() > 0
                && PackGateLogic.shouldPack(countApples(), packThresholdSetting.get()) && !landForStop) {
                landForStop = true;
                afterStopState = State.DONE;
                state = State.SURFACE_STOP;
                stateTicks = 0;
                statusText = "收工存盒";
                return;
            }
            info("任务完成：共取到 " + totalCollected + " 个附魔金苹果。");
            state = State.DONE;
            statusText = "完成";
            return;
        }
        if (openedInCity >= maxChestsPerCitySetting.get()) {
            rotateToNextCity();
            return;
        }
        if (pickChestInCity()) {
            beginFlight();
            return;
        }
        rotateToNextCity();
    }
    // ==================== 地表停靠：存盒 / 补给 ====================

    private void startStation() {
        stationStep = StationStep.PLACE_EC;
        stationTicks = 0;
        stationRetry = 0;
        stationPackBefore = countApples();
        stationEcPos = null;
        stationEcOwned = false;
        stationBoxTakeMode = false;
        stationSupplyBoxPending = false;
        stationSupplyBoxDone = false;
        stationBoxPos = null;
        stationBoxSlot = -1;
        stationFromEc = storageModeSetting.get() == StorageMode.ENDER_CHEST;
        state = State.STATION;
        stateTicks = 0;
        statusText = "地表停靠";
    }

    private void setStationStep(StationStep step) {
        stationStep = step;
        stationTicks = 0;
        stationRetry = 0;
        statusText = switch (step) {
            case PLACE_EC -> "放置末影箱";
            case OPEN_EC -> "打开末影箱";
            case TAKE_BOX -> "取空潜影盒";
            case CLOSE_EC -> "关闭末影箱";
            case PLACE_BOX -> "放置潜影盒";
            case OPEN_BOX -> "打开潜影盒";
            case FILL_BOX -> "装入附魔金";
            case CLOSE_BOX -> "关闭潜影盒";
            case MINE_BOX -> "回收潜影盒";
            case REOPEN_EC -> "重新打开末影箱";
            case STORE_BOX -> "存回潜影盒";
            case TAKE_SUPPLIES -> "补給";
            case MINE_EC -> "回收末影箱";
            case FINISH -> "停靠完成";
            default -> "地表停靠";
        };
    }

    private void onStation() {
        stationTicks++;
        if (stationTicks > 20 * 180) {
            pause("地表停靠超时，已暂停");
            return;
        }
        switch (stationStep) {
            case PLACE_EC -> stationPlaceEc();
            case OPEN_EC -> stationOpenEc();
            case TAKE_BOX -> stationTakeBox();
            case CLOSE_EC -> stationCloseEc();
            case PLACE_BOX -> stationPlaceBox();
            case OPEN_BOX -> stationOpenBox();
            case FILL_BOX -> stationFillBox();
            case CLOSE_BOX -> stationCloseBox();
            case MINE_BOX -> stationMineBox();
            case REOPEN_EC -> stationReopenEc();
            case STORE_BOX -> stationStoreBox();
            case TAKE_SUPPLIES -> stationTakeSupplies();
            case MINE_EC -> stationMineEc();
            case FINISH -> stationFinish();
            default -> {
            }
        }
    }

    private void stationPlaceEc() {
        if (stationNeedPack && findSilkTouchPickaxeSlot() == -1) {
            pause("没有精准采集镐，无法回收装了苹果的潜影盒");
            return;
        }
        if (!stationNeedPack && !stationNeedSupply) {
            setStationStep(StationStep.FINISH);
            return;
        }
        if (!stationFromEc) {
            // 随身潜影盒模式：直接找背包里的空盒
            if (!stationNeedPack && stationNeedSupply) {
                pause("随身潜影盒模式没有末影箱，无法补给经验瓶/烟花/食物");
                return;
            }
            if (stationNeedPack) {
                int slot = findEmptyShulkerSlot();
                if (slot == -1) {
                    pause("没有空潜影盒，无法存盒");
                    return;
                }
                stationBoxSlot = slot;
                setStationStep(StationStep.PLACE_BOX);
            } else {
                setStationStep(StationStep.FINISH);
            }
            return;
        }
        if (stationTicks == 1) {
            BlockPos existing = ecOpenFailCount == 0 ? findNearbyEnderChest(5) : null;
            if (existing != null) {
                stationEcPos = existing;
                stationEcOwned = false;
                setStationStep(StationStep.OPEN_EC);
                return;
            }
            int ecSlot = findItemSlot(Items.ENDER_CHEST);
            if (ecSlot == -1) {
                warning("背包里没有末影箱，改用随身潜影盒模式。");
                stationFromEc = false;
                stationNeedSupply = false;
                if (stationNeedPack) {
                    int box = findEmptyShulkerSlot();
                    if (box == -1) {
                        pause("没有末影箱也没有空潜影盒，无法存盒");
                        return;
                    }
                    stationBoxSlot = box;
                    setStationStep(StationStep.PLACE_BOX);
                } else {
                    pause("需要补给经验瓶/烟花/食物，但背包里没有末影箱");
                }
                return;
            }
            if (findSilkTouchPickaxeSlot() == -1) {
                pause("没有精准采集镐，无法回收自己放的末影箱");
                return;
            }
            selectItem(ecSlot);
            placeCandidates = findPlaceSpots(null, 8);
            if (placeCandidates.isEmpty()) {
                pause("附近找不到放置末影箱的位置");
                return;
            }
            placeIndex = 0;
            placeClickAttempts = 0;
            lastPlaceClick = -100;
            stationEcPos = null;
        }
        if (stationEcPos != null && mc.world.getBlockState(stationEcPos).getBlock() == Blocks.ENDER_CHEST) {
            PathManagers.get().stop();
            setStationStep(StationStep.OPEN_EC);
            return;
        }
        if (stationTicks >= 4 && stationTicks - lastPlaceClick >= 8) {
            if (placeIndex >= placeCandidates.size()) {
                pause("末影箱放置失败（所有候选位置都试过了）");
                return;
            }
            BlockPos spot = placeCandidates.get(placeIndex);
            if (mc.player.squaredDistanceTo(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5) > 16.0) {
                if (!PathManagers.get().isPathing()) {
                    PathManagers.get().moveTo(spot, false);
                }
                return;
            }
            if (mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot()).getItem() != Items.ENDER_CHEST) {
                int ec = findItemSlot(Items.ENDER_CHEST);
                if (ec == -1) {
                    pause("末影箱不在背包里");
                    return;
                }
                selectItem(ec);
                return;
            }
            lastPlaceClick = stationTicks;
            placeClickAttempts++;
            stationEcPos = spot;
            InteractionUtils.interactBlockSafely(spot.down(), Direction.UP);
            if (placeClickAttempts >= 2) {
                placeIndex++;
                placeClickAttempts = 0;
            }
        }
        if (stationTicks > 80) {
            pause("末影箱放置失败（超时）");
        }
    }

    private void stationOpenEc() {
        if (stationTicks == 1) {
            if (stationEcPos == null || mc.world.getBlockState(stationEcPos).getBlock() != Blocks.ENDER_CHEST) {
                setStationStep(StationStep.PLACE_EC);
                return;
            }
            standingMovedOff = false;
            stationRetry = 0;
        }
        if (!standingMovedOff && isStandingOnBlock(stationEcPos)) {
            BlockPos off = findStandOffSpot(stationEcPos);
            if (off != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(off, false);
            }
            if (isStandingOnBlock(stationEcPos)) {
                return;
            }
            standingMovedOff = true;
        }
        if (stationRetry < 5 && stationTicks == 1 + stationRetry * 20) {
            BlockClicker.clickBlockFacingPlayer(stationEcPos, true, Hand.MAIN_HAND);
            stationRetry++;
        }
        if (isContainerOpen()) {
            if (stationTicks < 3) return;
            ecOpenFailCount = 0;
            if (stationNeedPack) {
                setStationStep(StationStep.TAKE_BOX);
            } else {
                setStationStep(StationStep.TAKE_SUPPLIES);
            }
            return;
        }
        if (stationTicks > 93) {
            if (isStandingOnBlock(stationEcPos)) {
                standingMovedOff = true;
                stationTicks = 1;
                return;
            }
            warning("打开末影箱失败（已试 5 次），换地方重新放。");
            ecOpenFailCount++;
            stationEcPos = null;
            setStationStep(StationStep.PLACE_EC);
        }
    }

    private void stationTakeBox() {
        if (stationTicks < 4) return;
        if (stationNeedPack && stationTicks == 4 && (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler g)) {
            int inEc = countEmptyBoxesInContainer(mc.player.currentScreenHandler, g.getRows());
            if (countEmptyBoxes() + inEc < minEmptyBoxesSetting.get()) {
                pause("空潜影盒不足 " + minEmptyBoxesSetting.get() + " 个，无法继续存盒");
                return;
            }
        }
        if (stationBoxSlot != -1) {
            setStationStep(StationStep.CLOSE_EC);
            return;
        }
        // 上一 tick 的 QUICK_MOVE 可能已经把盒子送到背包，先认领再继续点，避免一次取好几个
        int already = findEmptyShulkerSlot();
        if (already != -1) {
            stationBoxSlot = already;
            setStationStep(StationStep.CLOSE_EC);
            return;
        }
        ScreenHandler sh = mc.player.currentScreenHandler;
        int rows = containerRows(sh);
        if (rows <= 0) {
            setStationStep(StationStep.OPEN_EC);
            return;
        }
        int ecSlot = findEmptyShulkerInContainer(sh, rows);
        if (ecSlot != -1) {
            mc.interactionManager.clickSlot(sh.syncId, ecSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
            stationRetry++;
            if (stationRetry > 40) {
                pause("从末影箱取潜影盒失败（背包可能已满）");
            }
            return;
        }
        int invSlot = findEmptyShulkerSlot();
        if (invSlot == -1) {
            pause("末影箱和背包里都没有空潜影盒");
            return;
        }
        stationBoxSlot = invSlot;
        setStationStep(StationStep.CLOSE_EC);
    }

    private void stationCloseEc() {
        if (!isContainerOpen() && stationSupplyBoxPending) {
            if (stationBoxSlot == -1) stationBoxSlot = findShulkerSlot();
            if (stationBoxSlot == -1) {
                pause("补给潜影盒没有拿到背包里");
                return;
            }
            setStationStep(StationStep.PLACE_BOX);
            return;
        }
        if (isContainerOpen()) {
            if (stationTicks == 1) mc.player.closeHandledScreen();
            if (stationTicks > 40) {
                pause("关闭末影箱界面超时");
            }
            return;
        }
        if (stationTicks < 4) return;
        if (stationNeedPack) {
            if (stationBoxSlot == -1) {
                stationBoxSlot = findEmptyShulkerSlot();
            }
            if (stationBoxSlot == -1) {
                pause("潜影盒没有拿到背包里");
                return;
            }
            setStationStep(StationStep.PLACE_BOX);
        } else {
            setStationStep(StationStep.TAKE_SUPPLIES);
        }
    }

    private void stationPlaceBox() {
        if (findSilkTouchPickaxeSlot() == -1) {
            pause("没有精准采集镐，不能放置会装苹果的潜影盒");
            return;
        }
        if (stationBoxSlot == -1 || stationBoxSlot >= 36) {
            pause("潜影盒槽位丢失");
            return;
        }
        if (stationTicks == 1) {
            selectItem(stationBoxSlot);
            // 盒子不能放在末影箱正上方，其它位置都可以试
            placeCandidates = findPlaceSpots(stationEcPos, 8);
            if (placeCandidates.isEmpty()) {
                pause("找不到放置潜影盒的位置");
                return;
            }
            placeIndex = 0;
            placeClickAttempts = 0;
            lastPlaceClick = -100;
            stationBoxPos = null;
        }
        if (stationBoxPos != null && mc.world.getBlockState(stationBoxPos).getBlock() instanceof ShulkerBoxBlock) {
            PathManagers.get().stop();
            setStationStep(StationStep.OPEN_BOX);
            return;
        }
        if (stationTicks >= 4 && stationTicks - lastPlaceClick >= 8) {
            if (placeIndex >= placeCandidates.size()) {
                pause("潜影盒放置失败（所有候选位置都试过了）");
                return;
            }
            BlockPos spot = placeCandidates.get(placeIndex);
            if (mc.player.squaredDistanceTo(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5) > 16.0) {
                if (!PathManagers.get().isPathing()) {
                    PathManagers.get().moveTo(spot, false);
                }
                return;
            }
            if (!isShulkerBox(mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot()))) {
                int box = stationBoxTakeMode ? findShulkerSlot() : findEmptyShulkerSlot();
                if (box == -1) {
                    pause(stationBoxTakeMode ? "补给潜影盒不在背包里" : "空潜影盒不在背包里");
                    return;
                }
                selectItem(box);
                return;
            }
            lastPlaceClick = stationTicks;
            placeClickAttempts++;
            stationBoxPos = spot;
            InteractionUtils.interactBlockSafely(spot.down(), Direction.UP);
            if (placeClickAttempts >= 2) {
                placeIndex++;
                placeClickAttempts = 0;
            }
        }
        if (stationTicks > 80) {
            pause("潜影盒放置失败（超时）");
        }
    }

    private void stationOpenBox() {
        if (stationTicks == 1) {
            if (stationBoxPos == null || !(mc.world.getBlockState(stationBoxPos).getBlock() instanceof ShulkerBoxBlock)) {
                setStationStep(StationStep.PLACE_BOX);
                return;
            }
            standingMovedOff = false;
            stationRetry = 0;
        }
        if (!standingMovedOff && isStandingOnBlock(stationBoxPos)) {
            BlockPos off = findStandOffSpot(stationBoxPos);
            if (off != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(off, false);
            }
            if (isStandingOnBlock(stationBoxPos)) {
                return;
            }
            standingMovedOff = true;
        }
        if (stationRetry < 5 && stationTicks == 1 + stationRetry * 20) {
            BlockClicker.clickBlockFacingPlayer(stationBoxPos, true, Hand.MAIN_HAND);
            stationRetry++;
        }
        if (isContainerOpen() && mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
            if (stationTicks < 3) return;
            stationApplesBefore = countApples();
            setStationStep(StationStep.FILL_BOX);
            return;
        }
        if (stationTicks > 93) {
            if (isStandingOnBlock(stationBoxPos)) {
                standingMovedOff = true;
                stationTicks = 1;
                return;
            }
            setStationStep(StationStep.PLACE_BOX);
        }
    }

    private void stationFillBox() {
        ScreenHandler sh = mc.player.currentScreenHandler;
        if (!(sh instanceof ShulkerBoxScreenHandler)) {
            setStationStep(StationStep.OPEN_BOX);
            return;
        }
        if (stationBoxTakeMode) {
            takeSuppliesFromBox(sh);
            return;
        }
        int rows = 3;
        int freeInBox = 0;
        for (int i = 0; i < rows * 9; i++) {
            if (sh.getSlot(i).getStack().isEmpty()) freeInBox++;
        }
        if (freeInBox == 0) {
            setStationStep(StationStep.CLOSE_BOX);
            return;
        }
        int playerFirst = rows * 9;
        int playerLast = playerFirst + 35;
        int target = -1;
        for (int i = playerFirst; i <= playerLast; i++) {
            ItemStack stack = sh.getSlot(i).getStack();
            if (stack.isEmpty() || stack.getItem() != Items.ENCHANTED_GOLDEN_APPLE) continue;
            target = i;
            break;
        }
        if (target == -1) {
            setStationStep(StationStep.CLOSE_BOX);
            return;
        }
        // 同一个槽连续 20 tick 还点得动 → 说明盒子满了，装不进去
        if (target == fillStuckSlot) {
            fillStuckTicks++;
        } else {
            fillStuckSlot = target;
            fillStuckTicks = 0;
        }
        if (fillStuckTicks >= 20) {
            warning("潜影盒装不进去，判定为已满。");
            setStationStep(StationStep.CLOSE_BOX);
            return;
        }
        mc.interactionManager.clickSlot(sh.syncId, target, 0, SlotActionType.QUICK_MOVE, mc.player);
        stationRetry++;
        if (stationRetry > 20 * 100) {
            pause("装入潜影盒超时");
        }
    }

    private void stationCloseBox() {
        if (isContainerOpen()) {
            if (stationTicks == 1) mc.player.closeHandledScreen();
            if (stationTicks > 40) {
                pause("关闭潜影盒界面超时");
            }
            return;
        }
        if (stationTicks < 4) return;
        if (!stationBoxTakeMode) {
            int gained = Math.max(0, stationApplesBefore - countApples());
            packedTotal += gained;
            info("入盒 " + gained + " 个附魔金苹果，累计入盒 " + packedTotal + "。");
        }
        setStationStep(StationStep.MINE_BOX);
    }

    private void stationMineBox() {
        if (stationBoxPos == null) {
            setStationStep(stationFromEc ? StationStep.REOPEN_EC : StationStep.FINISH);
            return;
        }
        BlockState st = mc.world.getBlockState(stationBoxPos);
        if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
            int slot = stationBoxTakeMode ? findShulkerSlot() : findShulkerWithApples();
            if (slot != -1) {
                stationBoxSlot = slot;
                stationSupplyBoxPending = false;
                setStationStep(stationFromEc ? StationStep.REOPEN_EC : StationStep.FINISH);
                return;
            }
            // 方块没了就是掉落物阶段：等它落地，每 20 tick 走过去捡
            statusText = "等潜影盒掉落物";
            if (stationTicks % 20 == 0 && !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(stationBoxPos, false);
            }
            if (stationTicks > 200) {
                pause("潜影盒挖掉后没有捡回来（需要精准采集镐）");
            }
            return;
        }
        int silk = findSilkTouchPickaxeSlot();
        if (silk == -1) {
            pause("没有精准采集镐，无法回收装了东西的潜影盒");
            return;
        }
        ItemStack silkStack = mc.player.getInventory().getStack(silk);
        if (GearRepairLogic.belowSafety(silkStack.getMaxDamage(), silkStack.getDamage(),
            minSafeDurabilitySetting.get() + 8)) {
            pause("精准采集镐耐久不足，停止回收潜影盒");
            return;
        }
        if (stationTicks % 4 == 0) {
            mineBlock(stationBoxPos, silk);
        }
        if (stationTicks > 20 * 20) {
            pause("回收潜影盒超时");
        }
    }

    private void stationReopenEc() {
        if (!stationFromEc) {
            setStationStep(StationStep.FINISH);
            return;
        }
        if (isContainerOpen()) {
            if (stationTicks < 4) return;
            setStationStep(StationStep.STORE_BOX);
            return;
        }
        if (stationEcPos == null || mc.world.getBlockState(stationEcPos).getBlock() != Blocks.ENDER_CHEST) {
            pause("末影箱在停靠过程中消失");
            return;
        }
        if (stationTicks % 15 == 1) {
            BlockClicker.clickBlockFacingPlayer(stationEcPos, true, Hand.MAIN_HAND);
        }
        if (stationTicks > 20 * 10) {
            pause("重新打开末影箱失败");
        }
    }

    private void stationStoreBox() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            setStationStep(StationStep.REOPEN_EC);
            return;
        }
        ScreenHandler sh = mc.player.currentScreenHandler;
        int rows = containerRows(sh);
        if (rows <= 0) {
            setStationStep(StationStep.REOPEN_EC);
            return;
        }
        if (stationBoxSlot == -1) {
            stationBoxSlot = findEmptyShulkerSlot();
        }
        if (stationBoxSlot == -1) {
            pause("潜影盒不在背包里，无法存回末影箱");
            return;
        }
        int screenSlot = invSlotToScreen(rows, stationBoxSlot);
        if (stationRetry == 0) {
            mc.interactionManager.clickSlot(sh.syncId, screenSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
            stationRetry = 1;
            return;
        }
        // 等服务端把 QUICK_MOVE 处理完再关界面，避免「点了就关」导致盒子留在背包/丢失
        if (stationTicks < 5) return;
        stationBoxSlot = -1;
        mc.player.closeHandledScreen();
        if (stationBoxTakeMode) {
            stationSupplyBoxDone = true;
            stationBoxTakeMode = false;
        }
        setStationStep(StationStep.TAKE_SUPPLIES);
    }

    private void stationTakeSupplies() {
        if (stationSupplyBoxDone) {
            if (isContainerOpen()) mc.player.closeHandledScreen();
            setStationStep(stationFromEc ? StationStep.MINE_EC : StationStep.FINISH);
            return;
        }
        if (!stationNeedSupply) {
            if (isContainerOpen()) {
                mc.player.closeHandledScreen();
            }
            setStationStep(stationFromEc ? StationStep.MINE_EC : StationStep.FINISH);
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            if (stationFromEc) {
                setStationStep(StationStep.OPEN_EC);
            } else {
                setStationStep(StationStep.FINISH);
            }
            return;
        }
        ScreenHandler sh = mc.player.currentScreenHandler;
        int rows = containerRows(sh);
        if (rows <= 0) {
            setStationStep(StationStep.OPEN_EC);
            return;
        }
        // 背包塞满时 QUICK_MOVE 会把东西丢地上，先腾一个格子
        if (isInventoryFull() && dropJunkForSpace(sh, rows)) {
            supplyEmptyTicks = 0;
            return;
        }
        boolean needBottles = countBottles() < targetBottlesSetting.get();
        boolean needFireworks = countFireworks() < minFireworksSetting.get() * 2;
        boolean needFood = !hasEnoughFood();
        boolean needTotems = countTotems() < minTotemsSetting.get();
        int target = -1;
        for (int i = 0; i < rows * 9; i++) {
            ItemStack stack = sh.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            boolean take = (needBottles && stack.getItem() == Items.EXPERIENCE_BOTTLE)
                || (needFireworks && stack.getItem() == Items.FIREWORK_ROCKET)
                || (needTotems && stack.getItem() == Items.TOTEM_OF_UNDYING)
                || (isFood(stack) && !hasEnoughFood());
            if (take) {
                target = i;
                break;
            }
        }
        if (target != -1) {
            mc.interactionManager.clickSlot(sh.syncId, target, 0, SlotActionType.QUICK_MOVE, mc.player);
            supplyEmptyTicks = 0;
            stationRetry++;
            if (stationRetry > 20 * 60) {
                pause("从末影箱补给超时");
            }
            return;
        }
        // 散放槽位没有需要的物资：尝试找一只装了经验瓶/烟花的补给潜影盒
        int supplyBox = findSupplyShulker(sh, rows, needBottles, needFireworks, needFood, needTotems);
        if (supplyBox != -1) {
            mc.interactionManager.clickSlot(sh.syncId, supplyBox, 0, SlotActionType.QUICK_MOVE, mc.player);
            stationSupplyBoxPending = true;
            stationBoxTakeMode = true;
            setStationStep(StationStep.CLOSE_EC);
            return;
        }
        supplyEmptyTicks++;
        if (supplyEmptyTicks >= 10) {
            mc.player.closeHandledScreen();
            setStationStep(stationFromEc ? StationStep.MINE_EC : StationStep.FINISH);
        }
    }

    /** 从背包丢一件杂物腾位；返回 true 表示丢了一件。 */
    private boolean dropJunkForSpace(ScreenHandler sh, int rows) {
        int playerFirst = rows * 9;
        for (int i = playerFirst; i < playerFirst + 36; i++) {
            ItemStack st = sh.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            if (st.getItem() == Items.ENCHANTED_GOLDEN_APPLE) continue;
            if (st.getItem() == Items.FIREWORK_ROCKET) continue;
            if (st.getItem() == Items.EXPERIENCE_BOTTLE) continue;
            if (st.getItem() == Items.ENDER_CHEST) continue;
            if (st.getItem() == Items.ELYTRA) continue;
            if (st.getItem() == Items.TOTEM_OF_UNDYING) continue;
            if (isPickaxe(st.getItem())) continue;
            if (isShulkerBox(st)) continue;
            if (isFood(st)) continue;
            mc.interactionManager.clickSlot(sh.syncId, i, 0, SlotActionType.THROW, mc.player);
            return true;
        }
        return false;
    }

    /** 从补给潜影盒里取经验瓶/烟花/食物。 */
    private void takeSuppliesFromBox(ScreenHandler sh) {
        boolean needBottles = countBottles() < targetBottlesSetting.get();
        boolean needFireworks = countFireworks() < minFireworksSetting.get() * 2;
        boolean needFood = !hasEnoughFood();
        boolean needTotems = countTotems() < minTotemsSetting.get();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = sh.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            boolean take = (needBottles && stack.getItem() == Items.EXPERIENCE_BOTTLE)
                || (needFireworks && stack.getItem() == Items.FIREWORK_ROCKET)
                || (needTotems && stack.getItem() == Items.TOTEM_OF_UNDYING)
                || (needFood && isFood(stack));
            if (!take) continue;
            mc.interactionManager.clickSlot(sh.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            stationRetry++;
            if (stationRetry > 20 * 60) {
                pause("从补给潜影盒取物资超时");
            }
            return;
        }
        setStationStep(StationStep.CLOSE_BOX);
    }

    /** 找一只含有当前缺口物资的潜影盒（只看物品，不打开）。 */
    private int findSupplyShulker(ScreenHandler sh, int rows, boolean needBottles,
                                  boolean needFireworks, boolean needFood, boolean needTotems) {
        for (int i = 0; i < rows * 9; i++) {
            ItemStack stack = sh.getSlot(i).getStack();
            if (!isShulkerBox(stack)) continue;
            ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
            if (c == null) continue;
            for (ItemStack inside : c.streamNonEmpty().toList()) {
                if (needBottles && inside.getItem() == Items.EXPERIENCE_BOTTLE) return i;
                if (needFireworks && inside.getItem() == Items.FIREWORK_ROCKET) return i;
                if (needTotems && inside.getItem() == Items.TOTEM_OF_UNDYING) return i;
                if (needFood && isFood(inside)) return i;
            }
        }
        return -1;
    }

    private void stationMineEc() {
        if (!stationEcOwned) {
            // 用的是周围原有的末影箱，不能拆别人的
            setStationStep(StationStep.FINISH);
            return;
        }
        if (stationEcPos == null || mc.world.getBlockState(stationEcPos).getBlock() != Blocks.ENDER_CHEST) {
            if (findItemSlot(Items.ENDER_CHEST) == -1) {
                statusText = "等末影箱掉落物";
                if (stationTicks % 20 == 0 && stationEcPos != null && !PathManagers.get().isPathing()) {
                    PathManagers.get().moveTo(stationEcPos, false);
                }
                if (stationTicks > 200) {
                    pause("末影箱挖掉后没有捡回来（需要精准采集镐）");
                }
                return;
            }
            setStationStep(StationStep.FINISH);
            return;
        }
        int silk = findSilkTouchPickaxeSlot();
        if (silk == -1) {
            warning("没有精准采集镐，末影箱将无法回收。");
            setStationStep(StationStep.FINISH);
            return;
        }
        ItemStack silkStack = mc.player.getInventory().getStack(silk);
        if (GearRepairLogic.belowSafety(silkStack.getMaxDamage(), silkStack.getDamage(),
            minSafeDurabilitySetting.get() + 8)) {
            pause("精准采集镐耐久不足，停止回收末影箱");
            return;
        }
        if (stationTicks % 4 == 0) {
            mineBlock(stationEcPos, silk);
        }
        if (stationTicks > 20 * 20) {
            pause("回收末影箱超时");
        }
    }

    private void stationFinish() {
        stationStep = StationStep.NONE;
        if (stationNeedSupply && !totemSupplyUnavailable
            && countTotems() < minTotemsSetting.get()) {
            totemSupplyUnavailable = true;
            warning("这次补给没拿到图腾（末影箱里可能没有），本轮不再因为图腾停靠；重新开关模块可以再试。");
        }
        PathManagers.get().setAllowBreak(true);
        if (countFireworks() < 4) {
            pause("烟花不足 4 发，无法继续飞行");
            return;
        }
        // 补给完再检查一次：低耐久可能是本次停靠才发现的，不能只依赖 needRepairAfterAscent
        if (autoRepairSetting.get() && elytraNeedsRepairOrPickaxe()) {
            state = State.REPAIR;
            stateTicks = 0;
            statusText = "修复装备";
            return;
        }
        // 一个潜影盒可能装不下全部苹果（例如盒里已有杂物），再停靠一次把剩下的装完；
        // 但这次一个都没装进去就别再循环了，否则会无限「继续装盒」
        if (autoPackSetting.get() && PackGateLogic.shouldPack(countApples(), packThresholdSetting.get())
            && countApples() < stationPackBefore) {
            state = State.SURFACE_STOP;
            stateTicks = 0;
            surfaceEvaluated = false;
            statusText = "继续装盒";
            return;
        }
        state = afterStopState;
        afterStopState = State.NEXT;
        stateTicks = 0;
        statusText = "下一个目标";
    }

    // ==================== 修复 ====================

    private void onRepair() {
        if (!autoRepairSetting.get()) {
            state = State.NEXT;
            return;
        }
        if (stateTicks == 1) {
            if (!mc.player.isOnGround()) {
                pause("修复必须站在安全地面进行");
                return;
            }
            if (countBottles() < minBottlesSetting.get()) {
                pause("经验瓶不足 " + minBottlesSetting.get() + " 个，无法修复");
                return;
            }
            equipElytra();
            ItemStack elytra = elytraStack();
            ItemStack pick = pickaxeStack();
            if (elytra.getItem() != Items.ELYTRA) {
                pause("鞘翅不在身上，无法修复");
                return;
            }
            if (elytra.getItem() == Items.ELYTRA
                && GearRepairLogic.belowSafety(elytra.getMaxDamage(), elytra.getDamage(),
                minSafeDurabilitySetting.get())) {
                pause("鞘翅低于最低安全耐久，请换备用鞘翅");
                return;
            }
            if (pick.getItem() != Items.AIR
                && GearRepairLogic.belowSafety(pick.getMaxDamage(), pick.getDamage(),
                minSafeDurabilitySetting.get())) {
                pause("镐子低于最低安全耐久，请换备用镐");
                return;
            }
            repairUsed = 0;
            repairAttempts = 0;
            repairModeB = !mainHandPickaxeSetting.get();
            repairTriedFallback = false;
            stationOriginalOffhand = mc.player.getOffHandStack().copy();
            if (!selectRepairSetup()) {
                pause("修复装备摆放失败（缺少经验瓶、镐子或鞘翅）");
                return;
            }
            mc.player.setPitch(90.0f);
            statusText = repairModeB ? "修复（主手投瓶）" : "修复（副手投瓶）";
            return;
        }
        if (stateTicks % 5 != 0) return;

        mc.player.setPitch(90.0f);
        int before = countBottles();
        Hand hand = repairModeB ? Hand.MAIN_HAND : Hand.OFF_HAND;
        mc.interactionManager.interactItem(mc.player, hand);
        int after = countBottles();
        if (after >= before) {
            repairAttempts++;
            if (!repairModeB && !repairTriedFallback && repairAttempts >= 3) {
                warning("服务器不认副手投瓶，自动回退主手投瓶 + 副手镐。");
                repairTriedFallback = true;
                repairModeB = true;
                repairAttempts = 0;
                selectRepairSetup();
            } else if (repairAttempts > 8) {
                pause("投瓶没有消耗，可能服务器拒绝使用经验瓶");
            }
            return;
        }
        repairUsed++;
        repairAttempts = 0;
        refillRepairBottle();

        if (stateTicks % 20 == 0) {
            boolean elytraDone = GearRepairLogic.repairDone(
                elytraStack().getMaxDamage(), elytraStack().getDamage(), repairTargetSetting.get());
            boolean pickDone = GearRepairLogic.repairDone(
                pickaxeStack().getMaxDamage(), pickaxeStack().getDamage(), repairTargetSetting.get());
            boolean elytraSafe = !GearRepairLogic.belowSafety(
                elytraStack().getMaxDamage(), elytraStack().getDamage(), minSafeDurabilitySetting.get());
            boolean pickSafe = !GearRepairLogic.belowSafety(
                pickaxeStack().getMaxDamage(), pickaxeStack().getDamage(), minSafeDurabilitySetting.get());
            RepairSessionLogic.Next next = RepairSessionLogic.decide(
                elytraDone, pickDone, repairUsed, maxBottlesPerRepairSetting.get(),
                countBottles(), elytraSafe, pickSafe);
            if (next != RepairSessionLogic.Next.CONTINUE) {
                finishRepair(next);
            }
        }
    }

    private boolean selectRepairSetup() {
        if (!ensureBottleInInventory()) return false;
        if (findRepairPickaxeSlot() == -1) return false;
        if (repairModeB) {
            int bottleSlot = findItemSlot(Items.EXPERIENCE_BOTTLE);
            selectItem(bottleSlot);
            int pick = findRepairPickaxeSlot();
            if (pick == -1) return false;
            setOffhand(pick);
        } else {
            int pickSlot = findRepairPickaxeSlot();
            selectItem(pickSlot);
            int bottle = findItemSlot(Items.EXPERIENCE_BOTTLE);
            if (bottle == -1) return false;
            setOffhand(bottle);
        }
        return true;
    }

    /** 手里那组瓶子投完后自动换下一组，否则从第 65 瓶开始就是空投。 */
    private void refillRepairBottle() {
        if (repairModeB) {
            ItemStack main = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
            if (main.getItem() == Items.EXPERIENCE_BOTTLE) return;
            int bottle = findItemSlot(Items.EXPERIENCE_BOTTLE);
            if (bottle != -1) selectItem(bottle);
        } else {
            if (mc.player.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE) return;
            int bottle = findItemSlot(Items.EXPERIENCE_BOTTLE);
            if (bottle != -1) setOffhand(bottle);
        }
    }

    /** 副手可能已经在上一轮模式 A 里放了瓶子；要切模式 B 时先把它换回背包。 */
    private boolean ensureBottleInInventory() {
        if (findItemSlot(Items.EXPERIENCE_BOTTLE) != -1) return true;
        if (mc.player.getOffHandStack().getItem() != Items.EXPERIENCE_BOTTLE) return false;
        int empty = findEmptyInventorySlot();
        if (empty == -1) return false;
        setOffhand(empty);
        return findItemSlot(Items.EXPERIENCE_BOTTLE) != -1;
    }

    private void finishRepair(RepairSessionLogic.Next next) {
        restoreOffhand();
        needRepairAfterAscent = false;
        if (next == RepairSessionLogic.Next.STOP_SAFETY) {
            pause("修复中装备低于最低安全耐久，已停止");
            return;
        }
        if (next == RepairSessionLogic.Next.STOP_NO_BOTTLES) {
            if (pauseWithoutBottlesSetting.get()) {
                pause("经验瓶用完，无法继续修复");
                return;
            }
        }
        repairCount++;
        if (repairThenSelect) {
            repairThenSelect = false;
            info("起飞前修复完成，投瓶 " + repairUsed + " 个。");
            state = State.SELECT_CITY;
            stateTicks = 0;
            statusText = "选城";
            return;
        }
        String result = switch (next) {
            case DONE -> "已达标";
            case STOP_LIMIT -> "达到本次瓶数上限";
            case STOP_NO_BOTTLES -> "经验瓶用完";
            case STOP_SAFETY -> "低于安全耐久";
            case CONTINUE -> "继续";
        };
        info("本次修复投瓶 " + repairUsed + " 个（" + result + "）。");
        state = afterStopState;
        afterStopState = State.NEXT;
        stateTicks = 0;
        statusText = "下一个目标";
    }

    // ==================== 基础工具 ====================

    private void pause(String reason) {
        commitLootProgress();
        error(reason);
        releaseForward();
        PathManagers.get().stop();
        PathManagers.get().setAllowBreak(true);
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
        }
        state = State.PAUSE;
        statusText = "暂停";
    }

    /** 开箱中途暂停/关闭时，把已经取到手的苹果先记进总数，避免重新开箱后少算。 */
    private void commitLootProgress() {
        if (state != State.LOOT || lootApplesBefore < 0 || mc.player == null) return;
        int now = countApples();
        totalCollected += Math.max(0, now - lootApplesBefore);
        lootApplesBefore = now;
    }

    private void releaseForward() {
        if (mc.options != null && mc.options.forwardKey != null) {
            mc.options.forwardKey.setPressed(false);
        }
    }

    private void sendStartGliding() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendPacket(
            new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(
                mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    private String scope() {
        if (mc.world == null) return null;
        String server = mc.getCurrentServerEntry() != null
            ? mc.getCurrentServerEntry().address : "单人";
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    private static Path storePath() {
        return FabricLoader.getInstance().getGameDir().resolve(STORE_FILE_NAME);
    }

    private void loadStore() {
        if (storeLoaded) return;
        storeLoaded = true;
        try {
            store = EnchantedGoldStore.load(storePath());
        } catch (IOException | RuntimeException e) {
            store = new EnchantedGoldStore();
            warning("读取附魔金黑名单失败，已从空记录开始：" + e.getMessage());
        }
    }

    private void saveStore() {
        try {
            store.save(storePath());
        } catch (IOException e) {
            warning("保存附魔金黑名单失败：" + e.getMessage());
        }
    }

    private int surfaceY(int x, int z) {
        // 未加载区块 getTopY 会给出无意义的值；用海平面 64 兜底，等飞近后区块加载再取真实高度
        if (!isChunkLoaded(x, z)) return 64;
        return mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
    }

    private boolean isChunkLoaded(int x, int z) {
        try {
            return mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isFluid(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState st = mc.world.getBlockState(pos);
        if (!st.getFluidState().isEmpty()) return true;
        return st.getBlock() == Blocks.MAGMA_BLOCK || st.getBlock() == Blocks.BUBBLE_COLUMN;
    }

    /** 井口是否可用（地表实心、两格空气、无流体）。 */
    private boolean isUsableShaftColumn(int x, int z, int chestY) {
        if (!isChunkLoaded(x, z)) return false;
        int topY = surfaceY(x, z);
        if (topY <= chestY + 8) return false;
        BlockState topState = mc.world.getBlockState(new BlockPos(x, topY, z));
        boolean solid = !topState.isAir() && topState.isSolidBlock(mc.world, new BlockPos(x, topY, z));
        boolean feetAir = mc.world.getBlockState(new BlockPos(x, topY + 1, z)).isAir();
        boolean headAir = mc.world.getBlockState(new BlockPos(x, topY + 2, z)).isAir();
        boolean fluid = isFluid(new BlockPos(x, topY, z))
            || isFluid(new BlockPos(x, topY + 1, z))
            || isFluid(new BlockPos(x, topY + 2, z));
        return ShaftColumnLogic.surfaceOk(solid, feetAir, headAir, fluid);
    }

    private boolean isShriekerNear(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() == Blocks.SCULK_SHRIEKER
            || mc.world.getBlockState(pos.north()).getBlock() == Blocks.SCULK_SHRIEKER
            || mc.world.getBlockState(pos.south()).getBlock() == Blocks.SCULK_SHRIEKER
            || mc.world.getBlockState(pos.west()).getBlock() == Blocks.SCULK_SHRIEKER
            || mc.world.getBlockState(pos.east()).getBlock() == Blocks.SCULK_SHRIEKER;
    }

    private boolean isUnbreakable(BlockPos pos, BlockState st) {
        if (st.getBlock() == Blocks.REINFORCED_DEEPSLATE) return true;
        return st.getHardness(mc.world, pos) < 0;
    }

    private boolean[] sampleFluidColumn(int x, int fromY, int toY, int z) {
        if (!isChunkLoaded(x, z)) return new boolean[0];
        int lo = Math.min(fromY, toY);
        int hi = Math.max(fromY, toY);
        int len = Math.min(512, hi - lo + 1);
        boolean[] out = new boolean[len];
        for (int i = 0; i < len; i++) {
            out[i] = isFluid(new BlockPos(x, lo + i, z));
        }
        return out;
    }

    private ShaftFluidLogic.Risk assessShaftRisk(BlockPos digPos) {
        boolean below = isFluid(digPos);
        boolean north = isFluid(digPos.north());
        boolean south = isFluid(digPos.south());
        boolean west = isFluid(digPos.west());
        boolean east = isFluid(digPos.east());
        int look = fluidLookaheadSetting.get();
        boolean[] ahead = new boolean[Math.max(0, look)];
        for (int i = 0; i < ahead.length; i++) {
            ahead[i] = isFluid(digPos.down(i + 1));
        }
        BlockState st = mc.world.getBlockState(digPos);
        return ShaftFluidLogic.assess(below, north, south, west, east, ahead,
            isUnbreakable(digPos, st));
    }

    private ShaftFluidLogic.Risk assessTunnelRisk(BlockPos pos) {
        boolean fluid = isFluid(pos) || isFluid(pos.up());
        boolean north = isFluid(pos.north()) || isFluid(pos.north().up());
        boolean south = isFluid(pos.south()) || isFluid(pos.south().up());
        boolean west = isFluid(pos.west()) || isFluid(pos.west().up());
        boolean east = isFluid(pos.east()) || isFluid(pos.east().up());
        BlockState st = mc.world.getBlockState(pos);
        return ShaftFluidLogic.assess(fluid, north, south, west, east, new boolean[0],
            isUnbreakable(pos, st));
    }

    private boolean isWardenNear() {
        int r = wardenDistanceSetting.get();
        if (r <= 0 || mc.world == null || mc.player == null) return false;
        double r2 = (double) r * r;
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.mob.WardenEntity
                && mc.player.squaredDistanceTo(entity) <= r2) {
                return true;
            }
        }
        return false;
    }

    private void abortShaft(String reason, boolean fluid, String blacklistReason) {
        warning("中止竖井：" + reason);
        releaseForward();
        PathManagers.get().stop();
        boolean blacklist = blacklistReason != null;
        if (fluid && !blacklistOnFluidSetting.get()) {
            // 用户关掉了「遇流体拉黑」：升井后暂停，不再自动重试同一箱
            blacklist = false;
            pauseAfterAscent = true;
        }
        if (blacklist) {
            blacklistCurrentChest(blacklistReason);
            currentChest = null;
        }
        state = State.ASCEND;
        stateTicks = 0;
        statusText = "升井";
    }

    private void blacklistCurrentChest(String reason) {
        if (currentChest == null) return;
        String scope = scope();
        if (scope != null) {
            store.blacklistChest(scope, currentChest.x(), currentChest.y(), currentChest.z(), reason);
            saveStore();
        }
        failedTotal++;
        warning("拉黑当前箱子（" + reason + "），换下一个目标。");
        currentChest = null;
        boolean underground = (state == State.SHAFT_DIG || state == State.LOOT
            || state == State.ASCEND)
            && shaftColumn != null && shaftTopY > mc.world.getBottomY()
            && mc.player.getBlockY() < shaftTopY - 1;
        if (EnchantedGoldStore.REASON_TARGET_MISSING.equals(reason)) {
            fakeCityFailures++;
            // 在地底不能直接切城起飞；等升井后再算，避免从竖井里启动飞行
            if (fakeCityFailures >= 3 && currentCity != null) {
                if (!underground) {
                    store.blacklistCity(scope, currentCity.centerX(), currentCity.centerZ(),
                        EnchantedGoldStore.REASON_FAKE_CITY);
                    saveStore();
                    warning("同一座城连续 3 个目标失真，判定假城，整城拉黑。");
                    fakeCityFailures = 0;
                    rotateToNextCity();
                    return;
                }
                // 地底不能直接起飞：先升井，回到地表再拉黑整城
                fakeCityBlacklistPending = true;
            }
        }
        if (underground) {
            state = State.ASCEND;
            stateTicks = 0;
            statusText = "升井";
        } else {
            state = State.NEXT;
            stateTicks = 0;
            statusText = "换目标";
        }
    }

    private BlockPos resolveChestPos() {
        if (currentChest == null) return null;
        BlockPos exact = new BlockPos(currentChest.x(), currentChest.y(), currentChest.z());
        if (mc.world.getBlockState(exact).getBlock() instanceof ChestBlock) return exact;
        // 优先看自己脚下这一格：井就挖在箱子正上方，人在箱子上面时它必然在脚下
        BlockPos below = mc.player.getBlockPos().down();
        if (mc.world.getBlockState(below).getBlock() instanceof ChestBlock) return below;
        // 只找 ±1 格，避免把隔壁箱子误认成目标
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = exact.add(dx, dy, dz);
                    if (mc.world.getBlockState(p).getBlock() instanceof ChestBlock) return p;
                }
            }
        }
        return null;
    }

    /** 落点在水里时，就近找一块干燥、两格空气、脚下实心的地面。 */
    private BlockPos findSafeLandingSpot() {
        BlockPos center = mc.player.getBlockPos();
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -64; dx <= 64; dx += 4) {
            for (int dz = -64; dz <= 64; dz += 4) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!isChunkLoaded(x, z)) continue;
                int y = surfaceY(x, z);
                if (y <= mc.world.getBottomY() + 2) continue;
                BlockPos feet = new BlockPos(x, y + 1, z);
                if (!mc.world.getBlockState(feet).isAir()) continue;
                if (!mc.world.getBlockState(feet.up()).isAir()) continue;
                if (!mc.world.getBlockState(feet.down()).isSolidBlock(mc.world, feet.down())) continue;
                if (isFluid(feet) || isFluid(feet.up()) || isFluid(feet.down())) continue;
                spots.add(feet);
            }
        }
        spots.sort(Comparator.comparingDouble(p ->
            mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
        return spots.isEmpty() ? null : spots.get(0);
    }

    /** 箱子周围 1~3 格、同层或上一层的站立点，绝不站箱子正上方。 */
    private BlockPos findChestStandSpot(BlockPos chest) {
        List<BlockPos> spots = new ArrayList<>();
        for (int[] c : StashChestSpotLogic.candidates(chest.getX(), chest.getY(), chest.getZ())) {
            BlockPos p = new BlockPos(c[0], c[1], c[2]);
            if (!mc.world.getBlockState(p).isAir()) continue;
            if (!mc.world.getBlockState(p.up()).isAir()) continue;
            if (!mc.world.getBlockState(p.down()).isSolidBlock(mc.world, p.down())) continue;
            if (isFluid(p) || isFluid(p.up())) continue;
            if (p.equals(chest.up())) continue;
            spots.add(p);
        }
        spots.sort(Comparator.comparingDouble(p ->
            mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
        return spots.isEmpty() ? null : spots.get(0);
    }

    private Direction directionFromShaftToChest() {
        if (shaftColumn == null || currentChest == null) return Direction.NORTH;
        int dx = currentChest.x() - shaftColumn.getX();
        int dz = currentChest.z() - shaftColumn.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private int containerRows(ScreenHandler sh) {
        if (sh instanceof GenericContainerScreenHandler g) return g.getRows();
        if (sh instanceof ShulkerBoxScreenHandler) return 3;
        return -1;
    }

    private boolean isContainerOpen() {
        return mc.player != null
            && mc.player.currentScreenHandler != null
            && !(mc.player.currentScreenHandler instanceof net.minecraft.screen.PlayerScreenHandler);
    }

    private int invSlotToScreen(int rows, int invSlot) {
        if (invSlot < 9) return rows * 9 + 27 + invSlot;
        return rows * 9 + invSlot - 9;
    }

    // ==================== 计数与物品 ====================

    private int countItem(Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) n += s.getCount();
        }
        ItemStack off = mc.player.getOffHandStack();
        if (off.getItem() == item) n += off.getCount();
        return n;
    }

    private int countApples() {
        return countItem(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private int countBottles() {
        return countItem(Items.EXPERIENCE_BOTTLE);
    }

    private int countFireworks() {
        return countItem(Items.FIREWORK_ROCKET);
    }

    private int countTotems() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) n++;
        }
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) n++;
        return n;
    }

    private boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }

    private boolean hasEnoughFood() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (isFood(mc.player.getInventory().getStack(i))) n++;
        }
        return n >= 8;
    }

    private boolean isInventoryFull() {
        // 快捷栏 0-8 也算：旧版只扫 9-35，快捷栏满了仍判「没满」，会把苹果塞到副手/丢地上
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private int findEmptyInventorySlot() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    // ==================== 鞘翅 / 镐子 ====================

    private ItemStack elytraStack() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST);
    }

    private boolean hasElytraEquipped() {
        return elytraStack().getItem() == Items.ELYTRA;
    }

    private int findElytraSlot() {
        int best = -1;
        int bestRemain = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() != Items.ELYTRA) continue;
            int remain = GearRepairLogic.remaining(s.getMaxDamage(), s.getDamage());
            if (remain > bestRemain) {
                bestRemain = remain;
                best = i;
            }
        }
        return best;
    }

    private void equipElytra() {
        if (hasElytraEquipped()) return;
        int slot = findElytraSlot();
        if (slot == -1) return;
        ItemStack chestArmor = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        // 护甲栏有别的胸甲时 toArmor(2) 会直接失败，先把胸甲挪到背包再穿
        if (!chestArmor.isEmpty()) {
            int empty = findEmptyInventorySlot();
            if (empty != -1) {
                InvUtils.move().fromArmor(2).to(empty);
            } else {
                // 没空格就互换：鞘翅进护甲栏，胸甲回到鞘翅原来那格
                InvUtils.move().from(slot).toArmor(2);
                return;
            }
        }
        InvUtils.move().from(slot).toArmor(2);
    }

    /** 副手拿着唯一的镐时，先把它换回背包，避免「找不到镐」中止挖井。 */
    private void ensurePickaxeInInventory() {
        ItemStack off = mc.player.getEquippedStack(EquipmentSlot.OFFHAND);
        if (!isPickaxe(off.getItem())) return;
        if (findPickaxeSlot() != -1) return;
        int empty = findEmptyInventorySlot();
        if (empty == -1) return;
        setOffhand(empty);
    }

    private boolean isPickaxe(Item item) {
        // 1.21.5 起 PickaxeItem 类已并入通用 Item，只能按物品判断
        return item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE
            || item == Items.IRON_PICKAXE || item == Items.GOLDEN_PICKAXE
            || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE
            || item == Items.COPPER_PICKAXE;
    }

    private ItemStack pickaxeStack() {
        ItemStack selected = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        if (isPickaxe(selected.getItem())) return selected;
        ItemStack off = mc.player.getOffHandStack();
        if (isPickaxe(off.getItem())) return off;
        int slot = findPickaxeSlot();
        return slot == -1 ? ItemStack.EMPTY : mc.player.getInventory().getStack(slot);
    }

    private int findPickaxeSlot() {
        int best = -1;
        int bestRemain = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!isPickaxe(s.getItem())) continue;
            int remain = GearRepairLogic.remaining(s.getMaxDamage(), s.getDamage());
            if (remain > bestRemain) {
                bestRemain = remain;
                best = i;
            }
        }
        return best;
    }

    private int findRepairPickaxeSlot() {
        int best = -1;
        int bestRemain = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!isPickaxe(s.getItem())) continue;
            if (!hasEnchantment(s, Enchantments.MENDING)) continue;
            int remain = GearRepairLogic.remaining(s.getMaxDamage(), s.getDamage());
            if (remain > bestRemain) {
                bestRemain = remain;
                best = i;
            }
        }
        return best != -1 ? best : findPickaxeSlot();
    }

    private int findSilkTouchPickaxeSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isPickaxe(s.getItem()) && hasEnchantment(s, Enchantments.SILK_TOUCH)) return i;
        }
        return -1;
    }

    private int enchantLevel(ItemStack stack, net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key) {
        if (mc.world == null || stack.isEmpty()) return 0;
        var reg = mc.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        var entry = reg.getEntry(reg.get(key));
        return EnchantmentHelper.getLevel(entry, stack);
    }

    private boolean hasEnchantment(ItemStack stack,
                                   net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key) {
        return enchantLevel(stack, key) > 0;
    }

    private boolean elytraNeedsRepair() {
        ItemStack e = elytraStack();
        return e.getItem() == Items.ELYTRA
            && GearRepairLogic.needsRepair(e.getMaxDamage(), e.getDamage(), elytraThresholdSetting.get());
    }

    private boolean pickaxeNeedsRepair() {
        int slot = findRepairPickaxeSlot();
        if (slot == -1) return false;
        ItemStack p = mc.player.getInventory().getStack(slot);
        return GearRepairLogic.needsRepair(p.getMaxDamage(), p.getDamage(), pickaxeThresholdSetting.get());
    }

    private boolean elytraNeedsRepairOrPickaxe() {
        return elytraNeedsRepair() || pickaxeNeedsRepair();
    }

    // ==================== 飞行/挖掘工具 ====================

    private void useFirework() {
        var fw = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!fw.found()) {
            var inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (inv.found()) {
                InvUtils.move().from(inv.slot()).toHotbar(mc.player.getInventory().getSelectedSlot());
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

    private int fireworkTicks() {
        return Math.max(1, (int) Math.round(fireworkIntervalSetting.get() * 20.0));
    }

    private void rotateTo(float yaw, float pitch) {
        float p = pitch;
        if (pitchSmoothSetting.get()) {
            float step = pitch < smoothedPitch
                ? pitchRotateUpSetting.get().floatValue()
                : pitchRotateDownSetting.get().floatValue();
            smoothedPitch = stepAngle(smoothedPitch, pitch, step);
            p = smoothedPitch;
        } else {
            smoothedPitch = pitch;
        }
        mc.player.setYaw(stepAngle(mc.player.getYaw(), yaw, 6.0f));
        mc.player.setPitch(p);
    }

    private float yawTo(double x, double z) {
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private double horizontalDistanceSq(double x, double z) {
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        return dx * dx + dz * dz;
    }

    private static float stepAngle(float current, float target, float speed) {
        float diff = ((target - current + 540.0f) % 360.0f) - 180.0f;
        if (Math.abs(diff) <= speed) return target;
        return current + Math.signum(diff) * speed;
    }

    private boolean mineBlock(BlockPos pos, int slot) {
        if (mc.world.getBlockState(pos).isAir()) return true;
        if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 25.0) {
            PathManagers.get().moveTo(pos, false);
            return false;
        }
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            selectItem(slot);
        }
        Direction face = Direction.values()[com.fo.addon.utils.FacingLogic.bestFaceIndex(
            mc.player.getEyePos().x, mc.player.getEyePos().y, mc.player.getEyePos().z,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)];
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.updateBlockBreakingProgress(pos, face);
        return mc.world.getBlockState(pos).isAir();
    }

    private void selectItem(int slot) {
        if (slot < 0 || slot > 35) return;
        if (slot > 8) {
            InvUtils.move().from(slot).toHotbar(mc.player.getInventory().getSelectedSlot());
        } else {
            InvUtils.swap(slot, false);
            mc.player.networkHandler.sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private int findPlaceableBlockSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            if (s.getItem() == Items.COBBLESTONE || s.getItem() == Items.COBBLED_DEEPSLATE
                || s.getItem() == Items.DEEPSLATE || s.getItem() == Items.STONE
                || s.getItem() == Items.DIRT || s.getItem() == Items.ANDESITE
                || s.getItem() == Items.TUFF || s.getItem() == Items.NETHERRACK) {
                return i;
            }
        }
        return -1;
    }

    private BlockPos findPlaceSpot(BlockPos exclude) {
        List<BlockPos> spots = findPlaceSpots(exclude, 8);
        return spots.isEmpty() ? null : spots.get(0);
    }

    /** 一次性列出附近所有可放置点，按距离排序。 */
    private List<BlockPos> findPlaceSpots(BlockPos exclude, int limit) {
        BlockPos center = mc.player.getBlockPos();
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (exclude != null && p.isWithinDistance(exclude, 1.5)) continue;
                    if (p.equals(center) || p.equals(center.up())) continue;
                    BlockState st = mc.world.getBlockState(p);
                    if (!st.isReplaceable()) continue;
                    if (!mc.world.getBlockState(p.down()).isSolidBlock(mc.world, p.down())) continue;
                    spots.add(p);
                }
            }
        }
        spots.sort(Comparator.comparingDouble(p ->
            mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
        if (spots.size() > limit) {
            return new ArrayList<>(spots.subList(0, limit));
        }
        return spots;
    }

    private boolean isStandingOnBlock(BlockPos pos) {
        if (pos == null) return false;
        BlockPos feet = mc.player.getBlockPos();
        return feet.getX() == pos.getX() && feet.getY() == pos.getY() + 1 && feet.getZ() == pos.getZ();
    }

    private BlockPos findStandOffSpot(BlockPos pos) {
        if (pos == null) return null;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                m.set(pos.getX() + dx, pos.getY() + 1, pos.getZ() + dz);
                if (!mc.world.getBlockState(m).isReplaceable()) continue;
                if (mc.world.getBlockState(m.down()).isReplaceable()) continue;
                return m.toImmutable();
            }
        }
        return null;
    }

    private BlockPos findNearbyEnderChest(int radius) {
        BlockPos p = mc.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos q = p.add(dx, dy, dz);
                    if (mc.world.getBlockState(q).getBlock() == Blocks.ENDER_CHEST) return q;
                }
            }
        }
        return null;
    }

    // ==================== 潜影盒 ====================

    private boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isEmptyShulker(ItemStack stack) {
        if (!isShulkerBox(stack)) return false;
        ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
        return c == null || c.streamNonEmpty().findAny().isEmpty();
    }

    private int countEmptyBoxes() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (isEmptyShulker(mc.player.getInventory().getStack(i))) n++;
        }
        return n;
    }

    private int countEmptyBoxesInContainer(ScreenHandler sh, int rows) {
        int n = 0;
        for (int i = 0; i < rows * 9; i++) {
            if (isEmptyShulker(sh.getSlot(i).getStack())) n++;
        }
        return n;
    }

    private int findEmptyShulkerSlot() {
        for (int i = 0; i < 36; i++) {
            if (isEmptyShulker(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private int findShulkerWithApples() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isShulkerBox(stack)) continue;
            ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
            if (c == null) continue;
            for (ItemStack inside : c.streamNonEmpty().toList()) {
                if (inside.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return i;
            }
        }
        return -1;
    }

    private int findShulkerSlot() {
        for (int i = 0; i < 36; i++) {
            if (isShulkerBox(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private int findEmptyShulkerInContainer(ScreenHandler sh, int rows) {
        for (int i = 0; i < rows * 9; i++) {
            if (isEmptyShulker(sh.getSlot(i).getStack())) return i;
        }
        return -1;
    }

    // ==================== 副手 ====================

    private void setOffhand(int invSlot) {
        if (invSlot < 0 || invSlot > 35) return;
        int screen = invSlot < 9 ? invSlot + 36 : invSlot;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, screen, 40,
            SlotActionType.SWAP, mc.player);
    }

    private void restoreOffhand() {
        if (stationOriginalOffhand.isEmpty()) {
            int empty = findEmptyInventorySlot();
            if (empty != -1) setOffhand(empty);
        } else {
            ItemStack want = stationOriginalOffhand;
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                // 按「同一个物品 + 同样数量」找，避免组数错位后拿回另一组
                if (s.getItem() == want.getItem() && s.getCount() == want.getCount()) {
                    setOffhand(i);
                    break;
                }
            }
        }
        stationOriginalOffhand = ItemStack.EMPTY;
    }
}
