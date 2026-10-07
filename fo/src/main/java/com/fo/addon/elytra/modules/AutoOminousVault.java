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
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「自动不祥宝库」—— 一个模块走完「找密室 → 飞过去 → 下去 → 找没开过的不祥宝库 → 开 → 标记」整条链。
 *
 * <h2>它按用户要求的八条实现</h2>
 * <ol>
 *   <li><b>自动在半径内找不祥宝库</b>，并且默认<b>避开试炼刷怪笼</b>（不在刷怪笼附近选库、不站它旁边开库），
 *       免得把普通刷怪笼转成不祥刷怪笼、把怪刷起来；</li>
 *   <li><b>背包里没有钥匙 → 聊天栏提醒 + 自动关闭</b>（{@link #noKeyStop()})；</li>
 *   <li>瞄准一个宝库后 <b>读它当前展示的物品</b>（{@link VaultDisplay}），按设置决定「命中就开 / 只是记录」；</li>
 *   <li><b>标记已打开的不祥宝库</b>（{@link VaultMarks}，落盘到 {@code fo-elytra-vaults.txt}，跨会话）；</li>
 *   <li>开关「任务结束后自动关闭」控制收工后要不要把自己关掉；</li>
 *   <li>本密室没库可开时：Baritone <b>自动爬上地表</b> → 借「自动鞘翅飞行」飞向
 *       <b>按种子（输入栏手填）推算出的最近试炼大厅</b>；</li>
 *   <li>到达后自动下降进入，再找<b>未标记</b>的不祥宝库，重复，<b>直到钥匙或食物用完</b>；</li>
 *   <li>八项相关参数全部是设置项，可手动调。</li>
 * </ol>
 *
 * <h2>必须说清楚的两个机制事实（不然你会觉得模块「笨」）</h2>
 * <ul>
 *   <li><b>展示物 ≠ 实际掉落。</b> 原版在宝库激活后每秒随机从掉落表里抽一件「展示物」循环放，
 *       而真正喷出来的战利品是<b>另外独立掷骰</b>的（wiki 原文：
 *       "the items to eject are rolled independently of whatever the idle display shows"）。
 *       所以「展示物是沉重核心才开」<b>不会</b>提高每把钥匙出目标的概率，只会让你少开很多库。
 *       因此默认是 {@link DisplayMode#LOG}（走到跟前读一眼、写进日志，但照样开），
 *       你要严格按展示物筛就改成 REQUIRE —— 模块会把这条机制写进聊天栏提醒你。</li>
 *   <li><b>钥匙和不祥刷怪笼是矛盾的。</b> 不祥试炼钥匙来自<b>不祥试炼刷怪笼</b>（喝不祥之瓶 → 靠近刷怪笼拿试炼之兆 →
 *       打死它刷出的怪，30% 概率喷钥匙），而本模块默认<b>绕开</b>刷怪笼。两条要求天生冲突，所以本模块
 *       <b>不刷钥匙</b>：钥匙得你自己准备好（或者你把「绕过试炼刷怪笼」关掉，再自己用别的模块刷）。
 *       没有钥匙时它会明确提醒并停手，而不是傻站着。</li>
 * </ul>
 *
 * <h2>状态机（{@link Phase}）</h2>
 * <pre>
 * PREPARE →（定位成功）→ CLIMB → TRAVEL → DIG → SCAN → FILTER → APPROACH → LOOK → OPEN → POST_OPEN
 *                    ↘（定位失败，就地找）→ SCAN ↗                                            ↓
 *                                     NO_VAULT ←──────────────────────────────────────────────┘
 * </pre>
 * 每个阶段都「要么推进、要么给出一条明确的中文原因」，不会静默卡死（这是前面几轮 bug 的总结）。
 */
public class AutoOminousVault extends FOElytraModule {

    /** 状态机阶段。命名 = 「这一步在干什么」。 */
    public enum Phase {
        /** 还没开始。 */
        IDLE("空闲"),
        /** 检查钥匙/食物/工具、写日志、决定要不要先飞。 */
        PREPARE("准备"),
        /** 在半径内增量扫描不祥宝库（每 tick 有预算，不卡客户端）。 */
        SCAN("扫描宝库"),
        /** 对扫到的候选做「刷怪笼距离」过滤（每个候选要读一片方块，所以也要摊到多 tick）。 */
        FILTER("过滤候选"),
        /** 用 Baritone 走到目标的激活范围内。 */
        APPROACH("走近宝库"),
        /** 到了跟前，等展示物同步过来并读一眼。 */
        LOOK("查看展示物"),
        /** 交给 {@link VaultOpener} 开这一个库（只开这一个）。 */
        OPEN("开宝库"),
        /** 处理开库结果：结算战利品、标记、决定下一个。 */
        POST_OPEN("开库结算"),
        /** 这个密室没有可开的库了：判断「收工」还是「换下一个密室」。 */
        NO_VAULT("本密室无库"),
        /** 用 Baritone 爬上地表（鞘翅在地下起飞不了）。 */
        CLIMB("爬升到地表"),
        /** 借「自动鞘翅飞行」飞往下一个试炼大厅坐标。 */
        TRAVEL("飞往下一处"),
        /** 到地方了，挖竖井下降到密室层。 */
        DIG("挖竖井下降"),
        /** 正常收工。 */
        DONE("完成"),
        /** 失败（原因见 {@link #failReason}）。 */
        FAILED("失败");

        public final String label;

        Phase(String label) {
            this.label = label;
        }

        /** 前端 UI/提示均显示中文（模块列表、日志、聊天输出都会打印这个阶段） */
        @Override
        public String toString() {
            return label;
        }
    }

    /** 怎么找到试炼大厅。 */
    public enum LocateMode {
        /** 种子推算 → 坐标列表 → 地图 → 单机种子搜索（有什么用什么）。 */
        AUTO("自动（有什么用什么）"),
        /** 只用手填种子推算（多人服务器上也能用）。 */
        SEED("种子推算"),
        /** 只用下面的坐标列表。 */
        COORD_LIST("坐标列表"),
        /** 只读背包里的「埋藏的试炼密室地图」。 */
        MAP("藏宝图"),
        /** 只用单机整合服务端的真实世界生成器搜索（只在单人存档有效）。 */
        INTEGRATED_SEARCH("单机世界搜索");

        public final String label;

        LocateMode(String label) {
            this.label = label;
        }

        /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString） */
        @Override
        public String toString() {
            return label;
        }
    }

    /** 展示物怎么用。 */
    public enum DisplayMode {
        /** 不看展示物，直接开（最省事）。 */
        OFF("不检查展示物"),
        /** 走到跟前读一眼、写进日志和聊天栏，但照样开（默认：因为展示物不预测掉落）。 */
        LOG("只记录展示物"),
        /** 展示物必须命中目标才开，不命中就换下一个（按用户原文，但会显著降低开库效率）。 */
        REQUIRE("必须命中展示物");

        public final String label;

        DisplayMode(String label) {
            this.label = label;
        }

        /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString） */
        @Override
        public String toString() {
            return label;
        }
    }

    // ------------------------------------------------------------------ 常量

    /** 扫描每 tick 最多处理多少个坐标（和 VaultOpener 同一个量级：6000 个坐标 ≈ 一瞬间，不影响帧率）。 */
    private static final int SCAN_BUDGET_PER_TICK = 6000;
    /**
     * 扫描的高度带下限/上限：试炼密室在 Y=-40~-20 起，房间多在 -20~0，宝库不会长在地表以上。
     *
     * <p><b>必须和 {@link VaultOpener} 内部那一对（同样 -40 / 16）完全一致</b>：
     * 模块自己扫出来的候选，最后是交给开库状态机去「走过去 + 开」的，而开库状态机有自己的一轮扫描。
     * 如果这边带得更宽（原来写的是 -60~10），Y &lt; -40 的宝库会被模块选中、走过去，然后被开库状态机
     * 判成「半径内没有可开的宝库」—— 白走一趟还白记一个「没能打开」。对齐之后这类假失败不可能发生。</p>
     */
    private static final int SCAN_Y_MIN = -40;
    private static final int SCAN_Y_MAX = 16;
    /** 玩家不在高度带里时的兜底上下范围（绝不放开成整个立方体，那正是卡死的成因）。 */
    private static final int SCAN_FALLBACK_HALF = 24;
    /**
     * 每 tick 最多做几次「刷怪笼距离」检查（每次要读约 1.5 万格方块，限流避免卡帧）。
     *
     * <p>默认 12 格安全距离时单次约 1.5 万次读方块，取 1 就是「每 tick 最多 1.5 万次」——
     * 对客户端来说很轻；试炼密室里宝库也就十几个，过滤完最多十几秒。</p>
     */
    private static final int SPAWNER_CHECK_BUDGET_PER_TICK = 1;
    /** 挖竖井时「连续多少 tick 一格都没挖掉」就判定挖不动（没镐子/方块太硬），报失败而不是无限挖。 */
    private static final int DIG_STALL_MAX_TICKS = 400;
    /** 挖竖井前「等落地」的上限（tick）：滑翔中截停之后要等玩家真的站到地上才开始挖。 */
    private static final int LANDING_WAIT_MAX_TICKS = 400;
    /** 扫描进度日志间隔（tick）。 */
    private static final int SCAN_PROGRESS_LOG_TICKS = 40;
    /** APPROACH 重发 goto 的间隔（tick）：走偏/被别人顶掉时能自愈。 */
    private static final int REPATH_TICKS = 100;
    /** 走到跟前之后，至少等这么多 tick 才认为「展示物已经同步」（服务端状态每 20 tick 才重算一次）。 */
    private static final int DISPLAY_SYNC_MIN_WAIT = 20;
    /** 玩家自己开着界面时最多等多久（tick）：10 秒，超时给明确原因，绝不硬关别人的界面。 */
    private static final int SCREEN_HOLD_MAX_TICKS = 200;

    // ------------------------------------------------------------------ 设置

    private final SettingGroup sgTarget = settings.createGroup("目标");
    private final SettingGroup sgFind = settings.createGroup("寻找宝库");
    private final SettingGroup sgDisplay = settings.createGroup("展示物筛选");
    private final SettingGroup sgOpen = settings.createGroup("开宝库");
    private final SettingGroup sgLocate = settings.createGroup("定位试炼大厅");
    private final SettingGroup sgTravel = settings.createGroup("飞行");
    private final SettingGroup sgDig = settings.createGroup("下降进入");
    private final SettingGroup sgStop = settings.createGroup("停止条件");
    private final SettingGroup sgDebug = settings.createGroup("调试");

    private final ItemListSetting targetItems = SettingHelper.items(sgTarget, "目标战利品",
        "开出来的东西命中这里任意一项就算这次有收益，默认「沉重核心 + 附魔金苹果」。",
        List.of(Items.HEAVY_CORE, Items.ENCHANTED_GOLDEN_APPLE), false);

    private final StringSetting targetEnchants = SettingHelper.string(sgTarget, "目标魔咒（附魔书）",
        "按魔咒 ID 判定附魔书，例如 wind_burst（风爆）。多个用逗号隔开，留空就不判。",
        "wind_burst");

    private final BoolSetting stopOnTarget = SettingHelper.bool(sgTarget, "命中目标就收工",
        "开到目标战利品就结束整个任务（关掉 = 把本密室能开的都开完再说）。", true);

    private final BoolSetting alsoNormal = SettingHelper.bool(sgTarget, "也开普通宝库",
        "打开后普通宝库也会被选中（用普通试炼钥匙）。默认只开不祥宝库 —— 沉重核心只在它的独有池里。", false);

    private final IntSetting searchRadius = SettingHelper.int_(sgFind, "宝库搜索半径（格）",
        "以你为中心、在已加载区块里找宝库的水平半径。试炼密室很大，太小会「明明在密室里却找不到库」。", 96, 16, 192);

    private final BoolSetting avoidSpawner = SettingHelper.bool(sgFind, "绕过试炼刷怪笼",
        "只挑离试炼刷怪笼够远的宝库，也不在刷怪笼旁边开库。走过去的路是 Baritone 寻路，可能会路过它。", true);

    private final IntSetting spawnerAvoidRadius = SettingHelper.int_(sgFind, "离刷怪笼至少这么远（格）",
        "候选宝库离试炼刷怪笼小于这个距离就跳过。默认 12 格，调大会跳过更多宝库。",
        12, 4, 48);

    private final EnumSetting<DisplayMode> displayMode = SettingHelper.enum_(sgDisplay, "展示物筛选方式",
        "展示物和实际掉落无关，默认「只记录」照开；改成「必须命中才开」会少开很多库。",
        DisplayMode.LOG);

    private final ItemListSetting displayItems = SettingHelper.items(sgDisplay, "展示物 · 目标物品",
        "「必须命中才开」/「只记录」模式下，认定「命中」的物品清单；默认沉重核心 + 附魔金苹果。",
        List.of(Items.HEAVY_CORE, Items.ENCHANTED_GOLDEN_APPLE), false);

    private final StringSetting displayEnchants = SettingHelper.string(sgDisplay, "展示物 · 目标魔咒",
        "展示物是附魔书时按魔咒 ID 判定（例如 wind_burst）。多个用逗号隔开。",
        "wind_burst");

    private final BoolSetting openWhenUnknown = SettingHelper.bool(sgDisplay, "读不到展示物也开",
        "读不到展示物时照开（默认）；关掉就当成不命中，换下一个。", true);

    private final IntSetting displayWaitTicks = SettingHelper.int_(sgDisplay, "读展示物前等多久（tick）",
        "站定后等这么久再读展示物，服务端每 20 tick 才刷新一次。默认 30。",
        30, DISPLAY_SYNC_MIN_WAIT, 100);

    private final DoubleSetting openDistance = SettingHelper.double_(sgOpen, "开库距离（格）",
        "站起来到这个水平距离内才右键（别顶着方块走）。宝库的激活半径是 4.0 格（3D 含 Y），别调太大。", 3.0, 1.0, 6.0);

    private final IntSetting collectTicks = SettingHelper.int_(sgOpen, "开完后收集多久（tick）",
        "不祥宝库是「一秒喷一件、最多 1+1~3 件」，内部有保守下限，调太小会漏判最后一件（往往是目标物）。", 120, 20, 600);

    private final IntSetting maxPerChamber = SettingHelper.int_(sgOpen, "一个密室最多开几个",
        "在一个试炼大厅里最多开这么多个宝库（每个都要一把钥匙），够了就换下一个密室/收工。", 8, 1, 64);

    private final IntSetting actionDelay = SettingHelper.int_(sgOpen, "动作间隔（tick）",
        "交给开库状态机的动作节流，卡服/高延迟时调大一点更稳。", 4, 0, 40);

    private final EnumSetting<LocateMode> locateMode = SettingHelper.enum_(sgLocate, "定位方式", 
        "怎么找试炼大厅。默认「自动」：种子推算 → 坐标列表 → 读地图 → 单机搜索，有什么用什么。",
        LocateMode.AUTO);

    private final StringSetting worldSeed = SettingHelper.string(sgLocate, "世界种子（手填）",
        "填 /seed 显示的那个数字，客户端直接算出试炼大厅候选点，多人服务器也能用。留空就跳过种子推算。",
        "");

    private final IntSetting seedRings = SettingHelper.int_(sgLocate, "种子推算圈数",
        "以你为中心往外推几圈 region 去找密室（1 圈 = 34×34 区块）。圈数越大能算到的越远，纯计算不吃性能。", 2, 1, 8);

    private final StringListSetting coordList = SettingHelper.stringList(sgLocate, "坐标列表",
        "一行一个坐标，格式 x,z（也可用空格或中文逗号）。模块会挑离你最近、这次没去过的那个。", List.of());

    private final IntSetting integratedSearchRadius = SettingHelper.int_(sgLocate, "单机种子搜索半径（区块）",
        "只在单人存档有效：用整合服务端的真实世界生成器从你当前位置往外搜（内部会夹到 200 区块）。",
        200, 16, 200);

    private final BoolSetting startFly = SettingHelper.bool(sgLocate, "启动时就飞往最近的试炼大厅",
        "打开模块就先定位并飞过去；关掉 = 你自己已经在密室里，直接开始找宝库。",
        true);

    private final BoolSetting autoClose = SettingHelper.bool(sgLocate, "任务结束后自动关闭",
        "开（默认）：一个密室刷完就关掉模块收工；关：钥匙没用完就继续爬上地表飞下一个密室。",
        true);

    private final IntSetting maxChambers = SettingHelper.int_(sgLocate, "最多换几个密室",
        "「任务结束后自动关闭」关掉时才有用：最多连续换这么多个密室就强制收工，防止挂机乱飞一晚上。", 5, 1, 20);

    private final IntSetting arriveRadius = SettingHelper.int_(sgTravel, "到达判定距离（格）",
        "水平距离小于这个值就算「到目标上空了」，开始下降。", 64, 8, 512);

    private final IntSetting digArriveRadius = SettingHelper.int_(sgTravel, "飞到多近才停下来挖（格）",
        "飞到离目标这么近才停飞下降。默认 16 格，调到 4 以下会绕圈。",
        16, 4, 64);

    private final IntSetting approachTimeoutSec = SettingHelper.int_(sgOpen, "走到宝库超时（秒）",
        "走到宝库的时限，超时就跳过它换下一个。默认 60 秒。", 60, 10, 600);
    private final IntSetting takeoffMinY = SettingHelper.int_(sgTravel, "最低起飞 Y",
        "Y 高于这个值就认为「能起飞了」（通常地面在 60 以上）。爬上地表就看它和「见天」两个条件。", 60, -64, 320);

    private final IntSetting climbTimeoutSec = SettingHelper.int_(sgTravel, "爬上地表超时（秒）",
        "让 Baritone 往上走这么久还上不去（被堵死/找不到路）就报失败并说明原因，绝不无限等。", 300, 30, 3600);

    private final IntSetting travelTimeoutSec = SettingHelper.int_(sgTravel, "飞行超时（秒）",
        "飞这么久还没到就放弃飞行、直接进下降阶段。", 900, 30, 7200);

    private final BoolSetting disableTravelOnArrive = SettingHelper.bool(sgTravel, "到达后关掉跑图模块",
        "「自动鞘翅飞行」是借来用的：到达后按这个开关决定要不要还回去（关掉它 = 让它继续开着）。", true);

    private final BoolSetting autoDig = SettingHelper.bool(sgDig, "自动挖竖井下降",
        "到目标上空后自己挖一条 1×1 竖井降到密室层。关掉 = 你自己把角色带到密室层（模块只做找库+开库）。", true);

    private final IntSetting digY = SettingHelper.int_(sgDig, "下降到 Y",
        "挖到这个高度就停。试炼大厅多在 Y=-20~0。", -20, -64, 320);

    private final IntSetting maxDig = SettingHelper.int_(sgDig, "单次最多挖多少格",
        "一次下降最多挖这么多格，超了就停下报原因。", 200, 1, 400);

    private final DoubleSetting digAbortHealth = SettingHelper.double_(sgDig, "下降时血量低于多少就停",
        "血量掉到这个值就停手保命。", 6.0, 1.0, 20.0);

    private final ItemListSetting foodItems = SettingHelper.items(sgStop, "算作「食物」的物品",
        "这些物品全用完就算食物用完，只数背包、不会自动吃。默认空清单 = 不检查，判定只在开完库之后。",
        List.of(), true);

    private final BoolSetting stopWhenNoFood = SettingHelper.bool(sgStop, "食物用完就停",
        "开着 = 食物数量变成 0 时结束任务（按「任务结束后自动关闭」决定关不关模块）。", true);

    private final BoolSetting noKeyAutoClose = SettingHelper.bool(sgStop, "没钥匙时自动关闭",
        "背包里没钥匙时聊天栏提醒并关掉模块（默认开）；关掉就只提醒、不关模块。", true);

    private final BoolSetting talkInChat = SettingHelper.bool(sgStop, "关键节点发聊天栏提示",
        "开库的关键节点（开始开库、开完、换密室等）发聊天栏，失败原因和「没钥匙停止」始终会发。"
            + "飞行阶段的状态监控、起飞、到达提示由「自动鞘翅飞行」自己的设置控制。", true);

    private final BoolSetting hudInfo = SettingHelper.bool(sgDebug, "HUD 状态",
        "在 HUD 上显示「阶段 + 关键数字」。", true);

    private final BoolSetting verboseLog = SettingHelper.bool(sgDebug, "详细文件日志",
        "把每个判断都写进 fo-elytra-*.log（排查问题用）。", true);

    private final BoolSetting debugMessages = SettingHelper.bool(sgDebug, "调试输出到聊天栏",
        "把 FOElytraLog.debug 的内容也发到聊天栏。本模块没有调试级输出，这个开关只对其他模块有效。", false);

    // ------------------------------------------------------------------ 运行状态

    private Phase phase = Phase.IDLE;
    private Phase lastPhase = Phase.IDLE;
    private int phaseTicks;
    private String failReason = "";

    /** 定位器：种子推算/地图/单机搜索都走它（它内部不记「已去过」，那是模块自己的事）。 */
    private final TrialChamberLocator locator = new TrialChamberLocator();
    /** 这次已经去过的密室坐标（"x,z"）—— 同一个密室刷完不要再来回横跳。 */
    private final Set<String> usedTargets = new HashSet<>();
    /** 本次运行已经进过几个密室。 */
    private int chambersVisited;
    private int targetX;
    private int targetZ;
    private String targetNote = "";
    private boolean integratedSearchStarted;
    private String locateFail = "";

    /** 扫描游标与统计（增量扫描，跨 tick 续扫）。 */
    private boolean scanning;
    private int scanOriginX, scanOriginY, scanOriginZ, scanRadius;
    private int scanDx, scanDyRel, scanDz, scanDyMin, scanDyMax;
    private long scanDone, scanTotal;
    private long scanChecked, scanSkippedUnloaded;
    private int scanTicks;
    private int scanChunkX = Integer.MIN_VALUE, scanChunkZ = Integer.MIN_VALUE;
    private boolean scanChunkLoadedFlag;
    /** 扫到的原始候选（还没做刷怪笼距离过滤）。 */
    private final List<BlockPos> pendingCandidates = new ArrayList<>();
    private final Set<Long> pendingKeys = new HashSet<>();
    /** 过滤之后的候选（按距离排序，最近的在前）。 */
    private final List<BlockPos> candidates = new ArrayList<>();
    private int filterIndex;
    private int filteredSpawner;
    private int filteredMarked;

    /** 本次运行跳过/试过的库（展示物不命中、走不到、没开开）—— 本轮不再选它。 */
    private final Set<Long> skipped = new HashSet<>();

    /** 当前盯上的宝库。 */
    private BlockPos current;
    private String displayDesc = "（还没看）";
    private int displayReadTicks;

    private VaultOpener vaultOpener;
    private int openedTotal;
    private int openedThisChamber;
    private int travelTicks = 0;
    private boolean weEnabledTravel;
    /** 借调「自动鞘翅飞行」前的全局详细日志开关（对方的 onActivate 会覆盖它，还回去时要恢复）。 */
    private boolean prevFileVerbose;
    private boolean verboseSaved;
    private boolean warnedNoTravel;
    private int digCount;
    private int digStallTicks;
    private int airBelowTicks;
    private int landingWaitTicks;
    private int digDelayTicks;
    private int surfaceYCache = Integer.MIN_VALUE;
    private int screenWaitTicks;
    private int gotoResend;

    public AutoOminousVault() {
        super("FO 自动开宝库", "找不祥宝库→避开刷怪笼→用不祥钥匙开→标记已开；本密室刷完自动换下一个，钥匙或食物用完即停。",
            "ominous-vault", "vault", "aov", "fovault");
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    public void onActivate() {
        FOElytraLog.fileVerbose = verboseLog.get();
        if (mc.runDirectory != null) {
            try {
                FOElytraLog.setGameDir(mc.runDirectory.toPath());
                FOElytraLog.ensureOpen(mc.runDirectory.toPath(), 10);
            } catch (Throwable t) {
                LOG.error("打开日志文件失败", t);
            }
        }

        phase = Phase.PREPARE;
        lastPhase = Phase.IDLE;
        phaseTicks = 0;
        failReason = "";
        usedTargets.clear();
        chambersVisited = 0;
        targetX = 0;
        targetZ = 0;
        targetNote = "";
        integratedSearchStarted = false;
        locateFail = "";
        resetScanState();
        skipped.clear();
        current = null;
        displayDesc = "（还没看）";
        displayReadTicks = 0;
        vaultOpener = null;
        openedTotal = 0;
        openedThisChamber = 0;
        travelTicks = 0;
        weEnabledTravel = false;
        warnedNoTravel = false;
        digCount = 0;
        digDelayTicks = 0;
        surfaceYCache = Integer.MIN_VALUE;
        screenWaitTicks = 0;
        gotoResend = 0;

        FOElytraLog.info("自动不祥宝库启动：半径 %d 格｜%s｜目标 %s｜展示物 %s｜结束后自动关闭 %s",
            searchRadius.get(), alsoNormal.get() ? "不祥宝库 + 普通宝库" : "只开不祥宝库",
            describeLoot(), displayMode.get(), autoClose.get() ? "是" : "否（会继续换密室）");
        FOElytraLog.detail("—— 本次设置 ——");
        for (SettingGroup g : settings) {
            for (var s : g) FOElytraLog.detail("    %s = %s", s.name, s.get());
        }
        FOElytraLog.detail("已记录的「已打开宝库」标记：%d 个（文件 %s）",
            VaultMarks.get().count(), VaultMarks.get().filePath());
        FOElytraLog.detail("钥匙：不祥试炼钥匙 %d 个；普通试炼钥匙 %d 个；%s",
            countKey(Items.OMINOUS_TRIAL_KEY), countKey(Items.TRIAL_KEY), foodCountText());

        if (!BaritoneHook.available()) {
            warning("没有检测到 Baritone：走到宝库、爬上地表都会用不了（会直接报失败原因，不会静默卡住）。");
        }
        if (!TrialChamberLocator.seedSearchAvailable()) {
            FOElytraLog.detail("当前是多人服务器：单机种子搜索不可用，"
                + "想自动飞往下一个密室请在「定位试炼大厅 → 世界种子（手填）」里填 /seed 的数字。");
        }
        if (avoidSpawner.get()) {
            FOElytraLog.detail("已开启「绕过试炼刷怪笼」：只在离刷怪笼 ≥ %d 格的地方选库/开库。"
                + "走过去的路是 Baritone 寻路，可能会路过刷怪笼，只能保证不在它旁边开库。", spawnerAvoidRadius.get());
        }
    }

    @Override
    public void onDeactivate() {
        cleanup(true);
        FOElytraLog.info("自动不祥宝库已关闭（阶段 %s｜本次共开 %d 个库｜去过 %d 个密室）",
            phase.toString(), openedTotal, chambersVisited);
        phase = Phase.IDLE;
    }

    @Override
    public String getInfoString() {
        if (!hudInfo.get() || mc.player == null) return null;
        StringBuilder sb = new StringBuilder(phase.toString());
        if (current != null) {
            double d = Math.hypot(mc.player.getX() - (current.getX() + 0.5), mc.player.getZ() - (current.getZ() + 0.5));
            sb.append(String.format(Locale.ROOT, " 库%.0fm", d));
        } else if (phase == Phase.CLIMB || phase == Phase.TRAVEL) {
            double d = Math.hypot(mc.player.getX() - (targetX + 0.5), mc.player.getZ() - (targetZ + 0.5));
            sb.append(String.format(Locale.ROOT, " 大厅%.0fm", d));
        }
        if (phase == Phase.SCAN && scanning) {
            sb.append(String.format(Locale.ROOT, " 扫%d%%", (int) (100 * scanDone / Math.max(1L, scanTotal))));
        }
        sb.append(" 已开").append(openedTotal);
        sb.append(" 钥").append(keyCount());
        return sb.toString();
    }

    // ------------------------------------------------------------------ 主循环

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.world == null) return;
        // 只在值变化时写这个全局开关：借调「自动鞘翅飞行」时它每 tick 也在写同一个字段，互相覆盖会让调试输出时有时无
        boolean dbg = debugMessages.get();
        if (FOElytraLog.debugEnabled != dbg) FOElytraLog.debugEnabled = dbg;
        try {
            if (phase != lastPhase) {
                lastPhase = phase;
                phaseTicks = 0;
                FOElytraLog.detail("阶段切换 → %s", phase.toString());
            }
            phaseTicks++;

            /*
             * 玩家自己开着界面时一行包都不发（原版开界面会清空按键状态，插一手只会搅乱玩家操作）。
             *
             * ⚠️ PREPARE 也**不能**豁免（审查抓出来的 P2）：PREPARE 里可能走到 finish()/noKeyStop()，
             * 而它们会调 cleanup() → InvHelper.closeScreen()，等于你在开箱子的时候插件把你的界面关了。
             * 放行到 here 之前只做纯读取，不动机器，所以这里一律等。
             */
            if (InvHelper.screenOpen() && phase != Phase.DONE && phase != Phase.FAILED) {
                if (screenWaitTicks++ > SCREEN_HOLD_MAX_TICKS) {
                    fail("你已经开着界面超过 " + (SCREEN_HOLD_MAX_TICKS / 20) + " 秒了，我先停下，不硬关你的界面");
                    return;
                }
                return;
            }
            screenWaitTicks = 0;

            switch (phase) {
                case PREPARE -> prepareTick();
                case SCAN -> scanTick();
                case FILTER -> filterTick();
                case APPROACH -> approachTick();
                case LOOK -> lookTick();
                case OPEN -> openTick();
                case POST_OPEN -> postOpenTick();
                case NO_VAULT -> noVaultTick();
                case CLIMB -> climbTick();
                case TRAVEL -> travelTick();
                case DIG -> digTick();
                case DONE, FAILED -> {
                    // 已收尾：停在这里等玩家处理
                }
                case IDLE -> phase = Phase.PREPARE;
            }
        } catch (Throwable t) {
            onError("onTick", t);
            fail("内部异常 " + t.getClass().getSimpleName() + "：" + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ 1) 准备

    private void prepareTick() {
        // 需求 2：没钥匙 → 聊天栏提醒 + 自动关闭（这条优先级最高，先查）
        if (!hasAnyKey()) {
            noKeyStop();
            return;
        }
        /*
         * ⚠️ 食物检查在这里**只提醒、不停止**。
         *
         * 为什么（这是审查抓出来的 P0）：原来这里一发现「食物数量 0」就 finish()，
         * 而 finish() 会按默认开着的「任务结束后自动关闭」把自己关掉 ——
         * 结果是「身上没带食物的人一开模块，模块立刻自己消失」，看起来就是「模块坏了/没反应」。
         * 「食物用完就停」这条停止条件应该只管**流程中**的判断（开完一个库之后、本密室没库可开时），
         * 不该在「还没开始干活」的时候把你拦下来。
         */
        if (stopWhenNoFood.get() && countFood() <= 0) {
            if (phaseTicks % 600 == 1) {
                FOElytraLog.warn("「%s」现在是 0 个（只提醒，不停手）：这条停止条件要等开完宝库才生效，"
                    + "想现在就停请自己往背包放点 %s", foodName(), foodName());
            }
        }
        if (phaseTicks == 1) {
            say("准备就绪：%s %d 个｜%s", keyName(), keyCount(), foodCountText());
            if (targetItems.get() == null || targetItems.get().isEmpty()) {
                warning("「目标战利品」是空的：模块不会判「命中」，只会记录每次开出了什么。");
            }
            if (displayMode.get() == DisplayMode.REQUIRE) {
                warning("已启用「必须命中才开」：展示物和实际掉落无关，这样只会少开很多宝库。");
            }
        }

        if (!startFly.get()) {
            FOElytraLog.detail("「启动时就飞往最近的试炼大厅」是关的：直接就地开始找宝库");
            enterScan();
            return;
        }

        switch (tryLocate()) {
            case WAITING -> {
                if (phaseTicks % 100 == 0) {
                    FOElytraLog.detail("正在后台搜索试炼大厅（整合服务端线程）…已等 %d 秒", phaseTicks / 20);
                }
            }
            case FOUND -> goToTarget();
            case FAILED -> {
                FOElytraLog.warn("没能定位到试炼大厅：%s", locateFail);
                FOElytraLog.warn("改成「就在当前位置附近找宝库」。要是本来不在密室里，请填「世界种子」或「坐标列表」。");
                enterScan();
            }
        }
    }

    // ------------------------------------------------------------------ 2) 扫描（增量）

    private void enterScan() {
        resetScanState();
        phase = Phase.SCAN;
    }

    private void resetScanState() {
        scanning = false;
        scanDone = 0;
        scanTotal = 0;
        scanChecked = 0;
        scanSkippedUnloaded = 0;
        scanTicks = 0;
        scanChunkX = Integer.MIN_VALUE;
        scanChunkZ = Integer.MIN_VALUE;
        scanChunkLoadedFlag = false;
        pendingCandidates.clear();
        pendingKeys.clear();
        candidates.clear();
        filterIndex = 0;
        filteredSpawner = 0;
        filteredMarked = 0;
    }

    /**
     * 一轮扫描：在已加载区块里找出**未标记、类型符合**的宝库方块（刷怪笼过滤放到下一个阶段，
     * 因为那一步每个候选要读一片方块，混进来会把单 tick 成本放大几十倍）。
     *
     * <p>为什么必须增量：半径 96 + Y 带（-40~16，57 层）≈ 193×193×57 ≈ 212 万个坐标。一次性扫完会卡住画面
     * （上一轮就是这么卡的），现在每 tick 只处理 {@value #SCAN_BUDGET_PER_TICK} 个，游标续扫。</p>
     */
    private void scanTick() {
        if (!scanning) beginScan();
        scanTicks++;

        int budget = SCAN_BUDGET_PER_TICK;
        while (budget-- > 0) {
            int x = scanOriginX + scanDx;
            int y = scanOriginY + scanDyRel;
            int z = scanOriginZ + scanDz;
            BlockPos pos = new BlockPos(x, y, z);
            scanDone++;

            if (!chunkLoaded(x >> 4, z >> 4)) {
                scanSkippedUnloaded++;
            } else {
                scanChecked++;
                BlockState st = mc.world.getBlockState(pos);
                if (st.isOf(Blocks.VAULT)) {
                    boolean ominous = Boolean.TRUE.equals(st.get(VaultBlock.OMINOUS));
                    boolean typeOk = ominous || alsoNormal.get();
                    if (typeOk && VaultMarks.get().isMarked(pos)) {
                        filteredMarked++;
                    } else if (typeOk && !skipped.contains(pos.asLong())) {
                        BlockPos fixed = pos.toImmutable();
                        if (pendingKeys.add(fixed.asLong())) pendingCandidates.add(fixed);
                    }
                }
            }

            if (!scanAdvance()) {
                finishScan();
                return;
            }
        }

        // 进度日志放在循环外：循环里每 tick 要跑 6000 次，塞日志会变成每秒几千行落盘（上一轮修过的坑）
        if (scanTicks % SCAN_PROGRESS_LOG_TICKS == 0) {
            FOElytraLog.detail("扫描进度 %d/%d（%.0f%%）第 %d tick：已读 %d 格、跳过未加载 %d 格，"
                + "待过滤候选 %d 个（已排除已标记 %d 个）",
                scanDone, scanTotal, 100.0 * scanDone / Math.max(1L, scanTotal), scanTicks,
                scanChecked, scanSkippedUnloaded, pendingCandidates.size(), filteredMarked);
        }
    }

    private void beginScan() {
        BlockPos origin = mc.player.getBlockPos().toImmutable();
        scanOriginX = origin.getX();
        scanOriginY = origin.getY();
        scanOriginZ = origin.getZ();
        scanRadius = Math.max(8, searchRadius.get());

        int yMin = Math.max(SCAN_Y_MIN, scanOriginY - scanRadius);
        int yMax = Math.min(SCAN_Y_MAX, scanOriginY + scanRadius);
        if (yMin > yMax) {
            // 玩家不在试炼密室高度带里（例如还站在地表）：只扫「玩家上下几层」，绝不放开成整个立方体
            yMin = Math.max(-64, scanOriginY - SCAN_FALLBACK_HALF);
            yMax = Math.min(319, scanOriginY + SCAN_FALLBACK_HALF);
            FOElytraLog.warn("你现在在 Y=%d，不在试炼密室的高度带（%d~%d）里：本次只在 Y=%d~%d 找宝库",
                scanOriginY, SCAN_Y_MIN, SCAN_Y_MAX, yMin, yMax);
        }
        scanDyMin = yMin - scanOriginY;
        scanDyMax = yMax - scanOriginY;
        scanDyRel = scanDyMin;
        scanDz = -scanRadius;
        scanDx = -scanRadius;

        long width = 2L * scanRadius + 1;
        scanTotal = width * width * (scanDyMax - scanDyMin + 1);
        scanDone = 0;
        scanChecked = 0;
        scanSkippedUnloaded = 0;
        scanTicks = 0;
        pendingCandidates.clear();
        pendingKeys.clear();
        candidates.clear();
        scanning = true;
        say("开始在 %d 格半径内找宝库（Y=%d~%d，共 %d 个坐标）", scanRadius, yMin, yMax, scanTotal);
        FOElytraLog.detail("扫描中心 %s｜每 tick 最多 %d 个坐标，跨 tick 续扫｜区块未加载整列跳过",
            origin.toShortString(), SCAN_BUDGET_PER_TICK);
    }

    /** 游标前进一步；扫完返回 false。顺序是 Y 外层 → Z → X 内层，让相邻 X 落在同一区块列里。 */
    private boolean scanAdvance() {
        scanDx++;
        if (scanDx <= scanRadius) return true;
        scanDx = -scanRadius;
        scanDz++;
        if (scanDz <= scanRadius) return true;
        scanDz = -scanRadius;
        scanDyRel++;
        if (scanDyRel <= scanDyMax) return true;
        scanDyRel = scanDyMin;
        return false;
    }

    private void finishScan() {
        scanning = false;
        filterIndex = 0;
        filteredSpawner = 0;
        phase = Phase.FILTER;
        say("扫描完成：读了 %d 格（跳过未加载 %d 格），待过滤候选 %d 个",
            scanChecked, scanSkippedUnloaded, pendingCandidates.size());
        FOElytraLog.detail("扫描统计：总坐标 %d｜已标记跳过 %d（标记总数 %d）",
            scanTotal, filteredMarked, VaultMarks.get().count());
    }

    /** 区块是否加载（同一区块列连续 16 格只查一次）。 */
    private boolean chunkLoaded(int cx, int cz) {
        if (cx == scanChunkX && cz == scanChunkZ) return scanChunkLoadedFlag;
        scanChunkX = cx;
        scanChunkZ = cz;
        try {
            scanChunkLoadedFlag = mc.world.isChunkLoaded(cx, cz);
        } catch (Throwable t) {
            scanChunkLoadedFlag = false;
        }
        return scanChunkLoadedFlag;
    }

    // ------------------------------------------------------------------ 3) 过滤（刷怪笼距离）

    private void filterTick() {
        int budget = SPAWNER_CHECK_BUDGET_PER_TICK;
        while (budget-- > 0 && filterIndex < pendingCandidates.size()) {
            BlockPos p = pendingCandidates.get(filterIndex++);
            // 方块可能在这几 tick 里被挖了/换了：落地复核一次，避免对空气走过去
            BlockState st = mc.world.getBlockState(p);
            if (!st.isOf(Blocks.VAULT)) {
                FOElytraLog.detail("过滤：%s 已经不是宝库方块了（被挖了？），跳过", p.toShortString());
                continue;
            }
            /*
             * 高度带复核（审查抓出来的 P1）：
             * 开库状态机自己的扫描只覆盖 Y=-40~16（那两条常量它没公开），所以模块选出来的候选
             * 必须也落在同一个带里 —— 否则会出现「我走过去 → 它说半径内没有可开的宝库 → 白走一趟」。
             * 模块扫描已经用了同一个带，这里再兜一层：玩家在带里、库却在带外时直接排除。
             */
            int y = p.getY();
            if (y < SCAN_Y_MIN || y > SCAN_Y_MAX) {
                FOElytraLog.detail("过滤：%s 在 Y=%d，超出开库状态机能扫的高度带（%d~%d），跳过",
                    p.toShortString(), y, SCAN_Y_MIN, SCAN_Y_MAX);
                continue;
            }
            if (avoidSpawner.get()) {
                boolean tooClose;
                try {
                    tooClose = VaultDisplay.tooCloseToSpawner(p, spawnerAvoidRadius.get());
                } catch (Throwable t) {
                    FOElytraLog.detailError("tooCloseToSpawner", t);
                    tooClose = false;      // 读不出来就按「不近」处理：宁可多开一个，也不要因为读不到而全跳过
                }
                if (tooClose) {
                    filteredSpawner++;
                    FOElytraLog.detail("过滤：%s 离试炼刷怪笼 < %d 格（不在它旁边开库）",
                        p.toShortString(), spawnerAvoidRadius.get());
                    continue;
                }
            }
            candidates.add(p);
        }

        if (filterIndex < pendingCandidates.size()) return;      // 还没过滤完，下一 tick 继续

        candidates.sort(Comparator.comparingDouble(this::distanceTo));
        if (candidates.isEmpty()) {
            FOElytraLog.warn("半径 %d 格内没有可开的不祥宝库（待过滤 %d 个，其中 %d 个因为离刷怪笼太近被跳过）",
                scanRadius, pendingCandidates.size(), filteredSpawner);
            phase = Phase.NO_VAULT;
            return;
        }
        say("找到 %d 个可开的不祥宝库（跳过 %d 个离刷怪笼太近的），最近的是 %s（%.0f 格）",
            candidates.size(), filteredSpawner, candidates.get(0).toShortString(), distanceTo(candidates.get(0)));
        FOElytraLog.detail("候选清单（前 10）：%s", describeCandidates(10));
        current = candidates.get(0);
        phase = Phase.APPROACH;
    }

    private String describeCandidates(int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, candidates.size()); i++) {
            if (i > 0) sb.append("、");
            sb.append(candidates.get(i).toShortString());
        }
        return sb.toString();
    }

    private double distanceTo(BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dy = mc.player.getY() - (pos.getY() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ 4) 走过去

    private void approachTick() {
        if (current == null) {
            phase = Phase.SCAN;
            return;
        }
        if (phaseTicks == 1) {
            gotoResend = 0;
            BaritoneHook.stop();
            say("走向不祥宝库 %s（距离 %.0f 格）", current.toShortString(), distanceTo(current));
            FOElytraLog.detail("APPROACH：目标 %s｜安全距离（离刷怪笼）%d 格｜超时 %d 秒",
                current.toShortString(), spawnerAvoidRadius.get(), approachTimeoutSec.get());
        }

        if (!mc.world.getBlockState(current).isOf(Blocks.VAULT)) {
            skipCurrent("这个宝库方块不见了（被挖掉/换掉了）");
            return;
        }

        double d = horizontalDistanceTo(current);
        // 交接阈值必须**不比开库状态机自己的判断更严**（它的到位判定是 openDistance），
        // 否则会出现「我这边没到位、它那边早就当到了」的白等；取 max 就是「谁的都不严于设置值」。
        if (d <= Math.max(1.0, openDistance.get())) {
            BaritoneHook.stop();
            FOElytraLog.detail("已到位（水平 %.1f 格）→ 进入读取展示物阶段", d);
            displayReadTicks = 0;
            displayDesc = "（还没看）";
            phase = Phase.LOOK;
            return;
        }

        if (avoidSpawner.get()) {
            boolean tooClose;
            try {
                tooClose = VaultDisplay.tooCloseToSpawner(mc.player.getBlockPos(), spawnerAvoidRadius.get());
            } catch (Throwable t) {
                tooClose = false;
            }
            if (tooClose && phaseTicks % 200 == 0) {
                // 这个库本来就被过滤过，走到跟前却还是太近：说明刷怪笼就在旁边（候选检查时那次读可能没加载）。
                FOElytraLog.warn("我现在站的位置离试炼刷怪笼不到 %d 格，可能会把怪刷起来",
                    spawnerAvoidRadius.get());
            }
        }

        if (!BaritoneHook.ready()) {
            fail("没有可用的 Baritone，走不到宝库 " + current.toShortString()
                + "。请装 Baritone，或把「自动挖竖井下降」关掉自己走过去。");
            return;
        }
        if (phaseTicks == 1 || phaseTicks % REPATH_TICKS == 0) {
            BaritoneHook.command("goto " + current.getX() + " " + current.getY() + " " + current.getZ());
            gotoResend++;
            FOElytraLog.detail("下发 goto %d %d %d（第 %d 次；走偏/被顶掉时会自动重发）",
                current.getX(), current.getY(), current.getZ(), gotoResend);
        }
        if (phaseTicks % 200 == 0) {
            FOElytraLog.detail("走向宝库中：距 %s 还有 %.0f 格（已走 %d 秒）", current.toShortString(), d, phaseTicks / 20);
        }
        if (phaseTicks > approachTimeoutSec.get() * 20) {
            skipCurrent(String.format(Locale.ROOT, "走了 %d 秒还没到（还差 %.0f 格，被封起来或路不通）",
                approachTimeoutSec.get(), d));
        }
    }

    private double horizontalDistanceTo(BlockPos pos) {
        return Math.hypot(mc.player.getX() - (pos.getX() + 0.5), mc.player.getZ() - (pos.getZ() + 0.5));
    }

    private void skipCurrent(String why) {
        if (current != null) {
            skipped.add(current.asLong());
            say("跳过这个宝库 %s：%s", current.toShortString(), why);
        }
        BaritoneHook.stop();
        current = null;
        candidates.removeIf(p -> skipped.contains(p.asLong()));
        if (!candidates.isEmpty()) {
            current = candidates.get(0);
            phase = Phase.APPROACH;
        } else {
            phase = Phase.NO_VAULT;
        }
    }

    // ------------------------------------------------------------------ 5) 读展示物

    private void lookTick() {
        if (current == null) {
            phase = Phase.SCAN;
            return;
        }
        int wait = Math.max(DISPLAY_SYNC_MIN_WAIT, displayWaitTicks.get());
        if (phaseTicks < wait) {
            if (phaseTicks == 1) {
                FOElytraLog.detail("站到激活范围内，等 %d tick 让服务端把「展示物」同步过来"
                    + "（服务端宝库状态每 20 tick 重算一次）", wait);
            }
            return;
        }

        DisplayMode mode = displayMode.get();
        boolean hit;
        ItemStack shown;
        try {
            shown = VaultDisplay.displayItem(current);
            displayDesc = VaultDisplay.describe(current);
            hit = VaultDisplay.displayContainsTarget(current, displayItems.get(), parseIds(displayEnchants.get()));
        } catch (Throwable t) {
            FOElytraLog.detailError("读取宝库展示物", t);
            shown = ItemStack.EMPTY;
            displayDesc = "（读取失败：" + t.getClass().getSimpleName() + "）";
            hit = false;
        }
        boolean unknown = shown == null || shown.isEmpty();
        displayReadTicks = phaseTicks;

        if (mode == DisplayMode.OFF) {
            openCurrent("展示物筛选已关（不看展示物）");
            return;
        }
        if (mode == DisplayMode.LOG) {
            // 「只记录」是一条**信息**，不是关键节点：进日志即可，命中时才值得说一句
            FOElytraLog.detail("宝库 %s 现在展示的是：%s", current.toShortString(), displayDesc);
            if (hit) {
                say("宝库 %s 展示物 = %s（命中清单，但展示物和实际掉落无关，照开）",
                    current.toShortString(), displayDesc);
            }
            openCurrent(hit ? "展示物命中（只记录模式，照开）" : "只记录模式，照开");
            return;
        }

        // REQUIRE：严格按「展示物命中才开」
        if (hit) {
            say("展示物命中目标（%s）→ 立刻用%s打开 %s", displayDesc, keyName(), current.toShortString());
            openCurrent("展示物命中");
            return;
        }
        if (unknown && openWhenUnknown.get()) {
            say("读不到宝库 %s 的展示物（可能没同步/已经被人开过）→ 按设置照样开", current.toShortString());
            openCurrent("读不到展示物，按设置照开");
            return;
        }
        skipCurrent("展示物是「" + displayDesc + "」，不在目标清单里（这次不浪费钥匙）");
    }

    // ------------------------------------------------------------------ 6) 开（借 VaultOpener，只开这一个）

    private void openCurrent(String why) {
        if (!hasAnyKey()) {
            noKeyStop();
            return;
        }
        BaritoneHook.stop();
        FOElytraLog.detail("准备开库：%s（原因：%s）", current.toShortString(), why);
        phase = Phase.OPEN;
    }

    private void openTick() {
        if (current == null) {
            phase = Phase.SCAN;
            return;
        }
        if (vaultOpener == null) {
            if (!mc.world.getBlockState(current).isOf(Blocks.VAULT)) {
                skipCurrent("准备开的时候它已经不是宝库方块了");
                return;
            }
            int radius = (int) Math.max(16, Math.min(64, Math.ceil(horizontalDistanceTo(current)) + 16));
            final BlockPos focus = current.toImmutable();
            vaultOpener = new VaultOpener(new VaultOpener.Options(
                radius,
                openDistance.get(),
                collectTicks.get(),
                1,                                  // 一次只开这一个（多开由模块自己循环控制，才能穿插展示物判断）
                !alsoNormal.get(),                   // needOminous：只有「也开普通宝库」关掉时才强制不祥
                safeItems(targetItems.get()),
                parseIds(targetEnchants.get()),
                stopOnTarget.get(),
                false,                               // drinkOminousBottle：本模块不喝瓶（要把普通刷怪笼转成不祥的，违背「绕开刷怪笼」）
                true,                                // useBaritoneWalk
                actionDelay.get(),
                pos -> pos != null && pos.equals(focus),   // candidateFilter：只认盯上的这一个
                this::onVaultOpened                        // onOpened：真开掉了就落盘标记
            ));
            vaultOpener.start();
            say("开始开库：%s｜半径 %d｜开完收集 %d tick｜目标 %s",
                current.toShortString(), radius, collectTicks.get(), describeLoot());
        }

        vaultOpener.tick();
        TaskStatus st = vaultOpener.status();
        if (st == TaskStatus.RUNNING) {
            if (phaseTicks % 100 == 0) {
                FOElytraLog.detail("开库中：%s｜已开 %d 个｜%s", vaultOpener.progress(), vaultOpener.openedCount(),
                    vaultOpener.foundTarget() ? "已命中目标" : "还没命中");
            }
            if (phaseTicks > 1200) {      // 60 秒还开不完：不再等，交给 POST_OPEN 按结果处理
                FOElytraLog.warn("开库流程跑了 %d 秒还没结束（状态 %s），先按当前结果处理", phaseTicks / 20,
                    vaultOpener.state());
                phase = Phase.POST_OPEN;
            }
            return;
        }
        phase = Phase.POST_OPEN;
    }

    /** 方块被真的打开时的回调（由 VaultOpener 在同一条件下调用一次）。 */
    private void onVaultOpened(BlockPos pos) {
        try {
            VaultMarks.get().mark(pos);
            say("已标记这个宝库为「已打开」：%s（以后不会再选它）", pos.toShortString());
        } catch (Throwable t) {
            FOElytraLog.detailError("onVaultOpened", t);
        }
    }

    // ------------------------------------------------------------------ 7) 开完结算

    private void postOpenTick() {
        if (vaultOpener == null) {
            phase = Phase.SCAN;
            return;
        }
        boolean found = vaultOpener.foundTarget();
        int opened = vaultOpener.openedCount();
        List<String> loot = vaultOpener.lootLog();
        String state = vaultOpener.state().toString();
        String fail = vaultOpener.failReason();
        String last = vaultOpener.lastMessage();

        for (String line : loot) {
            // 只走 say（= 聊天栏 + 文件，或只进文件）：原来这里 info + chatRaw 会让同一行在聊天栏出现两遍
            say("战利品：%s", line);
        }

        if (opened > 0) {
            // 双保险：即使回调没被触发（例如注入点没走到），也把标记写上 —— 标记是幂等的
            if (current != null) {
                try {
                    VaultMarks.get().mark(current);
                } catch (Throwable t) {
                    FOElytraLog.detailError("mark(postOpen)", t);
                }
                skipped.add(current.asLong());
            }
            openedTotal++;
            openedThisChamber++;
            say("第 %d 个不祥宝库开完了（本密室第 %d 个，本次共开 %d 个，已标记 %d 个）",
                openedTotal, openedThisChamber, openedTotal, VaultMarks.get().count());
        } else {
            FOElytraLog.warn("没能打开 %s：状态 %s｜原因 %s｜最后一步 %s", current == null ? "?" : current.toShortString(),
                state, fail.isEmpty() ? "没有给出具体原因" : fail, safe(last));
            if (current != null) skipped.add(current.asLong());
        }

        BlockPos openedPos = current;
        /*
         * 一定要先 abort() 再丢掉引用（审查抓出来的 P1）：
         * openTick 里「跑满 1200 tick 强制收尾」那条路会让子状态机还停在 RUNNING，
         * 直接置 null 等于把「Baritone goto 还在走 / 我们按着的键 / 它自己开的界面」全丢给下一次运行去踩。
         * abort() 是幂等的，正常结束（DONE）时调它也没副作用。
         */
        if (vaultOpener.status() == TaskStatus.RUNNING) {
            vaultOpener.abort("本次开库结算，交给模块处理下一个");
        }
        vaultOpener = null;
        current = null;

        if (found) {
            String msg = String.format(Locale.ROOT,
                "拿到目标战利品了！（本次共开 %d 个不祥宝库，命中在 %s）", openedTotal, String.join("、", loot));
            finish(msg);
            return;
        }

        // 还有钥匙吗？
        if (!hasAnyKey()) {
            noKeyStop();
            return;
        }
        if (stopWhenNoFood.get() && countFood() <= 0) {
            finish(String.format(Locale.ROOT, "食物用完了（%s 0 个）——先回去补货", foodName()));
            return;
        }

        // 一个密室开够了：交给 NO_VAULT 统一决定「收工」还是「换下一个密室」。
        // ⚠️ 这里绝不能偷偷再扫一轮 —— 那会让「一个密室最多开几个」这个设置形同虚设（扫到就接着开）。
        if (openedThisChamber >= maxPerChamber.get()) {
            say("本密室已经开了 %d 个（上限 %d）→ 交给「任务结束后自动关闭」决定收工还是换密室",
                openedThisChamber, maxPerChamber.get());
            openedThisChamber = 0;
            candidates.clear();
            phase = Phase.NO_VAULT;
            return;
        }

        // 本密室的候选表里还有下一个 → 直接接着走，省掉「每开一个就重扫一遍半径 96」的完整扫描
        if (openedPos != null) candidates.removeIf(p -> p.equals(openedPos));
        if (!candidates.isEmpty()) {
            current = candidates.get(0);
            FOElytraLog.detail("本密室还有 %d 个候选没开，去下一个：%s（%.0f 格）",
                candidates.size(), current.toShortString(), distanceTo(current));
            phase = Phase.APPROACH;
            return;
        }
        enterScan();
    }

    // ------------------------------------------------------------------ 8) 这个密室还有别的库吗

    private void noVaultTick() {
        // 需求 7：钥匙或食物用完就停
        if (!hasAnyKey()) {
            noKeyStop();
            return;
        }
        if (stopWhenNoFood.get() && countFood() <= 0) {
            finish(String.format(Locale.ROOT, "食物用完了（%s 0 个）——按设置停手", foodName()));
            return;
        }

        if (autoClose.get()) {
            finish(String.format(Locale.ROOT,
                "这个试炼大厅没有可开的未标记不祥宝库了（本次共开 %d 个）——按设置收工并关闭模块", openedTotal));
            return;
        }

        // 「任务结束后自动关闭」是关的 → 继续找下一个密室（钥匙还没用完）
        if (chambersVisited >= maxChambers.get()) {
            finish(String.format(Locale.ROOT,
                "已经连着换过 %d 个密室（上限）——收工，防止挂机乱飞", chambersVisited));
            return;
        }
        if (phaseTicks == 1) {
            say("本密室没库可开了：按设置（任务结束后自动关闭 = 否）去下一个试炼大厅（已去 %d 个，上限 %d）",
                chambersVisited, maxChambers.get());
        }

        switch (tryLocate()) {
            case WAITING -> {
                if (phaseTicks % 100 == 0) FOElytraLog.detail("正在后台搜索下一个试炼大厅…已等 %d 秒", phaseTicks / 20);
            }
            case FOUND -> goToTarget();
            case FAILED -> finish(String.format(Locale.ROOT,
                "本密室没库可开了，也定位不到下一个试炼大厅：%s（可填「世界种子」或「坐标列表」）", locateFail));
        }
    }

    // ------------------------------------------------------------------ 9) 定位

    private enum LocateResult {
        FOUND("找到了"),
        FAILED("失败"),
        WAITING("等待中");

        public final String label;

        LocateResult(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private LocateResult tryLocate() {
        if (mc.player == null) return LocateResult.FAILED;
        int px = mc.player.getBlockX();
        int pz = mc.player.getBlockZ();
        LocateMode mode = locateMode.get();
        locateFail = "";

        // ① 手填种子推算（多人服务器上也成立，且是用户点名要的那条路）
        if (mode == LocateMode.AUTO || mode == LocateMode.SEED) {
            Long seed = parseSeed();
            if (seed != null) {
                List<TrialChamberLocator.Target> all = locator.locateBySeedValues(seed, px, pz, seedRings.get());
                TrialChamberLocator.Target t = pickUnvisited(all);
                if (t != null) {
                    usedTargets.add(t.x() + "," + t.z());
                    return useTarget(t);
                }
                locateFail = String.format(Locale.ROOT, "按种子 %d 推算出的 %d 个候选点都已经去过了（圈数 %d，可调大）",
                    seed, all == null ? 0 : all.size(), seedRings.get());
            } else {
                locateFail = "「定位方式」要用种子推算，但「世界种子（手填）」是空的";
            }
            if (mode == LocateMode.SEED) return LocateResult.FAILED;
        }

        // ② 坐标列表
        if (mode == LocateMode.AUTO || mode == LocateMode.COORD_LIST) {
            TrialChamberLocator.Target t = pickCoords(px, pz);
            if (t != null) {
                usedTargets.add(t.x() + "," + t.z());
                return useTarget(t);
            }
            locateFail = "坐标列表里没有可用坐标（空、格式不对、或者都去过了）";
            if (mode == LocateMode.COORD_LIST) return LocateResult.FAILED;
        }

        // ③ 读「埋藏的试炼密室地图」
        if (mode == LocateMode.AUTO || mode == LocateMode.MAP) {
            TrialChamberLocator.Target t = locator.readMapTarget();
            if (t != null && !usedTargets.contains(t.x() + "," + t.z())) {
                usedTargets.add(t.x() + "," + t.z());
                return useTarget(t);
            }
            /*
             * ⚠️ 地图只有一张，同一张图永远返回同一个目标 —— 必须显式识别「已经去过了」，
             * 不然在「任务结束后自动关闭 = 否」时会把同一个密室当成「下一个」反复跑，
             * 直到 maxChambers 耗尽才报「已经连着换过 N 个密室」（审查抓出来的 P1）。
             */
            if (t != null) {
                locateFail = String.format(Locale.ROOT, "地图指向的试炼大厅 %d, %d 这次已经去过了"
                    + "（一张地图只给一个目标，换下一个请用「世界种子」或「坐标列表」）", t.x(), t.z());
            } else {
                locateFail = "背包里没有「埋藏的试炼密室地图」，或读不到标记点（" + safe(locator.failReason()) + "）";
            }
            if (mode == LocateMode.MAP) return LocateResult.FAILED;
        }

        // ④ 单机：整合服务端真实世界生成器搜索（异步，必须等 isSearching() 变 false）
        if (mode == LocateMode.AUTO || mode == LocateMode.INTEGRATED_SEARCH) {
            if (!TrialChamberLocator.seedSearchAvailable()) {
                // 只在前面几条路线都没留下原因时才写（第三轮复验新问题#4）：
                // 否则 AUTO 模式下「候选点都去过了（圈数可调大）」「地图目标已去过」这些更可操作的原因会被盖掉。
                if (locateFail.isEmpty()) {
                    locateFail = "单机种子搜索不可用（多人服务器没有世界种子，请用「手填种子」或坐标列表）";
                }
                return LocateResult.FAILED;
            }
            // ④ 只在前面几条路线都没留下原因时才改写 locateFail，
            //    否则 AUTO 模式下会把「地图目标已去过」这种更具体的原因盖成「单机搜索不可用」（第二轮审查 N7）
            if (!integratedSearchStarted) {
                integratedSearchStarted = true;
                locator.locateBySeed(px, pz, integratedSearchRadius.get());
                FOElytraLog.detail("已发起单机种子搜索（跑在整合服务端线程上，不卡画面）：起点 %d, %d，半径 %d 区块",
                    px, pz, integratedSearchRadius.get());
            }
            if (locator.isSearching()) return LocateResult.WAITING;
            integratedSearchStarted = false;
            TrialChamberLocator.Target t = locator.pollSeedSearch();
            if (t != null && !usedTargets.contains(t.x() + "," + t.z())) {
                usedTargets.add(t.x() + "," + t.z());
                return useTarget(t);
            }
            // 同上：搜索永远是「离搜索起点最近的那一个」，同一个起点必然给出同一个目标
            if (locateFail.isEmpty()) {
                locateFail = t != null
                    ? String.format(Locale.ROOT, "单机搜索给出的还是刚才那个试炼大厅 %d, %d（已经去过了），"
                        + "继续刷请用「世界种子」", t.x(), t.z())
                    : "单机种子搜索没找到：" + safe(locator.failReason());
            }
            return LocateResult.FAILED;
        }

        if (locateFail.isEmpty()) locateFail = "没有可用的定位方式";
        return LocateResult.FAILED;
    }

    private LocateResult useTarget(TrialChamberLocator.Target t) {
        targetX = t.x();
        targetZ = t.z();
        targetNote = t.note() == null ? "" : t.note();
        say("定位到试炼大厅候选人：%d, %d（来源 %s）", targetX, targetZ, t.source());
        FOElytraLog.detail("定位说明：%s", targetNote);
        return LocateResult.FOUND;
    }

    /** 定位到之后怎么走：已经很近就地下降/找库，否则先上地表再飞。 */
    private void goToTarget() {
        double d = Math.hypot(mc.player.getX() - (targetX + 0.5), mc.player.getZ() - (targetZ + 0.5));
        chambersVisited++;
        openedThisChamber = 0;
        if (d <= arriveRadius.get()) {
            say("已经在这个密室 %.0f 格范围内（判定距离 %d）：不飞了", d, arriveRadius.get());
            if (mc.player.getBlockY() > digY.get() + 8 && autoDig.get()) {
                phase = Phase.DIG;
            } else {
                enterScan();
            }
            return;
        }
        say("距目标 %.0f 格（阈值 %d）：先上地表再开鞘翅飞过去", d, arriveRadius.get());
        phase = Phase.CLIMB;
    }

    private TrialChamberLocator.Target pickUnvisited(List<TrialChamberLocator.Target> all) {
        if (all == null) return null;
        for (TrialChamberLocator.Target t : all) {
            if (!usedTargets.contains(t.x() + "," + t.z())) return t;
        }
        return null;
    }

    private TrialChamberLocator.Target pickCoords(int px, int pz) {
        List<String> lines = coordList.get();
        if (lines == null || lines.isEmpty()) return null;
        double best = Double.MAX_VALUE;
        TrialChamberLocator.Target bestT = null;
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty()) continue;
            String[] parts = s.split("[,，\\s]+");
            if (parts.length < 2) {
                FOElytraLog.warn("坐标列表里这一行看不懂（应为 x,z）：%s", s);
                continue;
            }
            try {
                int x = Integer.parseInt(parts[0].trim());
                int z = Integer.parseInt(parts[1].trim());
                if (usedTargets.contains(x + "," + z)) continue;
                double d = Math.hypot(px - x, pz - z);
                if (d < best) {
                    best = d;
                    bestT = TrialChamberLocator.manual(x, z);
                }
            } catch (NumberFormatException e) {
                FOElytraLog.warn("坐标列表里这一行不是合法整数：%s", s);
            }
        }
        return bestT;
    }

    /**
     * 解析「世界种子」设置。
     *
     * <p>只认纯数字（带符号），非数字文本按原版「字符串种子 → 哈希」的规则处理（MC 对非数字种子用的是
     * {@code String.hashCode()}）；解析不出来返回 {@code null}，由调用方给出明确原因，
     * 而不是拿 0 当种子去算出一堆错的坐标。</p>
     */
    private Long parseSeed() {
        String raw = worldSeed.get();
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            // 非数字：按原版字符串种子规则处理（就是 hashCode），并明确告诉用户我们做了什么
            int hash = s.hashCode();
            FOElytraLog.warn("「世界种子」不是纯数字（%s）：按原版字符串种子规则当成 %d 处理（想精确请填 /seed 的数字）",
                s, hash);
            return (long) hash;
        }
    }

    // ------------------------------------------------------------------ 10) 爬上地表

    private void climbTick() {
        if (phaseTicks == 1) {
            BaritoneHook.stop();
            surfaceYCache = Integer.MIN_VALUE;
            say("开始爬上地表：现在 Y=%d（鞘翅在地下起不来）", mc.player.getBlockY());
            FOElytraLog.detail("CLIMB：目标 Y ≥ %d 或「见天」即可起飞；Baritone %s",
                takeoffMinY.get(), BaritoneHook.ready() ? "已就绪" : "不可用");
        }

        if (surfaceOk()) {
            BaritoneHook.stop();
            say("已经能起飞了（Y=%d，见天 %s）→ 开始飞往 %d, %d",
                mc.player.getBlockY(), skyVisible() ? "是" : "否", targetX, targetZ);
            travelTicks = 0;
            phase = Phase.TRAVEL;
            return;
        }

        if (!BaritoneHook.ready()) {
            fail("没有可用的 Baritone，爬不上地表（当前 Y=" + mc.player.getBlockY() + "）。"
                + "请装 Baritone，或自己走回地面再开模块。");
            return;
        }

        if (phaseTicks == 1 || phaseTicks % REPATH_TICKS == 0) {
            int y = surfaceYEstimate();
            BaritoneHook.command("goto " + mc.player.getBlockX() + " " + y + " " + mc.player.getBlockZ());
            FOElytraLog.detail("让 Baritone 往地表走：goto %d %d %d（它会自己挖/绕上去；目标 Y 取本列地表高度）",
                mc.player.getBlockX(), y, mc.player.getBlockZ());
        }
        if (phaseTicks % 200 == 0) {
            FOElytraLog.detail("正在爬地表（%d 秒）：当前 Y=%d，见天 %s", phaseTicks / 20, mc.player.getBlockY(),
                skyVisible() ? "是" : "否");
        }
        if (phaseTicks > climbTimeoutSec.get() * 20) {
            fail(String.format(Locale.ROOT, "爬地表超时（%d 秒还没到 Y ≥ %d / 见天），当前 Y=%d。"
                + "请自己走回地面，或把超时调大", climbTimeoutSec.get(),
                takeoffMinY.get(), mc.player.getBlockY()));
        }
    }

    /**
     * 「已经落定了」的宽判定（第三轮复验新问题#1）。
     *
     * <p>为什么不能只认 {@code isOnGround()}：站在水面上、坐船/坐矿车/骑猪、爬梯子/脚手架时它**恒为 false**，
     * 于是试炼密室正好在海面下（飞过去落水里）这种最现实的情况会被判「等了 20 秒还没落地」+ 自动关模块。</p>
     */
    private boolean landed() {
        if (mc.player == null) return true;
        return mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.hasVehicle() || mc.player.isClimbing();
    }

    /** 能不能起飞：见天，或者已经高于「最低起飞 Y」。 */
    private boolean surfaceOk() {
        return skyVisible() || mc.player.getBlockY() >= takeoffMinY.get();
    }

    /** 头顶是不是空的（原版看的是天空光照等级 = 15，也就是「见天」）。 */
    private boolean skyVisible() {
        try {
            return mc.world.isSkyVisible(mc.player.getBlockPos());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 估一个「本列地表高度」给 Baritone 当 goto 的 Y。
     *
     * <p>为什么不用 Heightmap API：那要额外确认映射名与未加载区块的行为；这里直接读玩家所在这一列
     * （一定已加载），从世界顶部往下找第一个非空气方块，简单且不会读到未加载区域。结果缓存一次就够
     * （爬地表期间不会跑很远）。</p>
     */
    private int surfaceYEstimate() {
        if (surfaceYCache != Integer.MIN_VALUE) return surfaceYCache;
        int px = mc.player.getBlockX();
        int pz = mc.player.getBlockZ();
        int top;
        try {
            top = mc.world.getTopYInclusive();
        } catch (Throwable t) {
            top = 319;
        }
        for (int y = top; y > mc.world.getBottomY(); y--) {
            if (!mc.world.getBlockState(new BlockPos(px, y, pz)).isAir()) {
                surfaceYCache = Math.max(y + 1, takeoffMinY.get());
                return surfaceYCache;
            }
        }
        surfaceYCache = Math.max(mc.player.getBlockY(), takeoffMinY.get());
        return surfaceYCache;
    }

    // ------------------------------------------------------------------ 11) 飞过去

    private void travelTick() {
        AutoElytraFlight travel = travelModule();
        double dNow = Math.hypot(mc.player.getX() - (targetX + 0.5), mc.player.getZ() - (targetZ + 0.5));
        if (travel == null) {
            if (!warnedNoTravel) {
                warnedNoTravel = true;
                FOElytraLog.warn("找不到「自动鞘翅飞行」模块，飞不了：请自己飞到 %d, %d 附近（%d 格内模块会自动下降）",
                    targetX, targetZ, digArriveRadius.get());
            }
            afterTravel(dNow, "找不到「自动鞘翅飞行」模块");
            return;
        }
        travelTicks++;
        if (travelTicks == 1) {
            BaritoneHook.stop();        // 把「爬地表」的走路目标停掉，别和鞘翅进程抢
            if (!travel.isActive()) {
                // 记下借调前的全局日志开关：对方的 onActivate 会用自己的设置覆盖它，还回去时要能恢复
                prevFileVerbose = FOElytraLog.fileVerbose;
                verboseSaved = true;
                travel.toggle();
                weEnabledTravel = true;
                FOElytraLog.fileVerbose = prevFileVerbose;
                say("临时打开「自动鞘翅飞行」来跑这一段（到达后按设置还回去）");
            }
            travel.flyTo(targetX, targetZ);
        }

        double d = dNow;
        /*
         * 「到达」必须等落地（第二轮复审查出来的 P1）：
         * 原来只看水平距离，而 16 格截停通常发生在**滑翔中**——一进 16 格就把借来的模块关掉，
         * 玩家带着烟花的速度继续冲过目标几十格才落地，竖井就挖到结构外面去了。
         * 所以：水平够近**并且**（没在滑翔 或 已经落地）才算真到达。
         */
        if (d <= digArriveRadius.get() && (!mc.player.isGliding() || mc.player.isOnGround())) {
            say("已到达目标上空（水平 %.0f 格，停飞阈值 %d），准备下降", d, digArriveRadius.get());
            endTravel();
            phase = Phase.DIG;
            return;
        }
        if (travel.travelFailed()) {
            endTravel();
            afterTravel(d, "跑图模块报失败：" + travel.travelFailReason());
            return;
        }
        /*
         * 借来的模块自己把自己关了（第二轮复审查出来的 P1）：
         * AutoElytraFlight 在「没有 Baritone / 没装鞘翅」这类情况下会在 onActivate 里直接判失败并关闭自己，
         * 而我们紧随其后调用它的 flyTo() 仍会把它的 state 写成 PREPARE —— 它已经不 tick 了，那个 PREPARE
         * 永远不会变成 DONE，于是 travelTerminal() 永远 false，我们会在这里干等满「飞行超时」（默认 900 秒）。
         * 40 tick 足够让它走完 onActivate/toggle，之后 isActive() 还是 false 就说明它自己退了。
         */
        if (!travel.isActive() && travelTicks > 40) {
            endTravel();
            // 文案与事实对齐（第三轮复验新问题#2）：它在这里变 inactive 的原因最常见的是「到达后按它自己的
            // 「任务结束关闭模块」把自己关了」（对方默认开），而不是「判失败」；真原因在 travelFailReason() 里，
            // 而且 onDeactivate 之后 travelFailed() 永远是 false，所以必须自己去读那个字符串。
            String r = travel.travelFailReason();
            afterTravel(d, "借来的「自动鞘翅飞行」已经不在运行（" + (r == null || r.isEmpty()
                ? "可能是它到达后按自己的「任务结束关闭模块」关掉了，也可能是你自己关了它"
                : "它自己报的原因：" + r) + "）");
            return;
        }
        /*
         * ⚠️ 这里必须用 travelTerminal()，不能用 travelFinished()（第一轮审查抓出来的 P1-1）：
         * travelFinished() 把「segmentTarget == null」也算成「这段航程结束」，
         * 而 AutoElytraFlight 在「没穿鞘翅」时会先等「没有鞘翅时等多久」（默认 60 秒）才去定目标 ——
         * 于是刚借调过来 3 秒就会被误判成「飞完了」，人在起飞点就开始往下挖。
         */
        if (travel.travelTerminal() && travelTicks > 60) {
            endTravel();
            afterTravel(d, "跑图模块说这一段结束了（状态 " + travel.travelStateName() + "）");
            return;
        }
        if (travelTicks > travelTimeoutSec.get() * 20) {
            endTravel();
            afterTravel(d, "飞行超过 " + travelTimeoutSec.get() + " 秒还没到");
            return;
        }
        if (travelTicks % 200 == 0) {
            FOElytraLog.detail("飞行中：距目标 %.0f 格｜跑图模块状态 %s｜滑翔 %s", d, travel.travelStateName(),
                mc.player.isGliding() ? "是" : "否");
        }
    }

    /**
     * 飞行「没成功结束」时的收口（第一轮审查抓出来的 P1-2）。
     *
     * <p>为什么需要它：飞行失败/超时/被打断时，如果不管「现在离目标多远」就地开挖，
     * 常见后果是「在离结构几千米的地方往下挖 90 格 → 下去什么都没有 → 判定本密室结束 → 自动关模块」，
     * 看起来就是「模块自己跑完收工了但其实啥也没干」。这里给一次明确失败收手，把原因说清。</p>
     *
     * <p>容差取 {@code max(停飞阈值, 32)}（第二轮复审查出来的 N2）：借来的模块自己到 ≤32 格就会收工，
     * 用「到达判定距离（64）」当容差的话，17~64 格这段会绕过「必须飞到 16 格内」的本意。</p>
     */
    private void afterTravel(double d, String why) {
        // 容差给 40 而不是 32（第三轮复验新问题#3）：对方「到达判定半径」的默认值正好是 32，
        // 从它判到达那一 tick 到我们读到距离之间还会有几格滑翔惯性位移；玩家把对方那个值调到 40/64 时，
        // 32 的容差会把**成功的一次飞行**判成失败。40 留出余量，同时仍然满足「没飞到就别乱挖」的初衷。
        double tolerance = Math.max(digArriveRadius.get(), 40);
        if (d > tolerance) {
            fail(String.format(Locale.ROOT, "飞行没到目标（%s），还在 %.0f 格外，先停下不挖。"
                + "请检查鞘翅/烟花/Baritone 后重开模块，或用「坐标列表」自己过去", why, d));
            return;
        }
        FOElytraLog.warn("%s；当前距离 %.0f 格，就地下降", why, d);
        phase = Phase.DIG;
    }

    /** 把借来的「自动鞘翅飞行」按设置还回去。 */
    private void endTravel() {
        if (!weEnabledTravel) return;
        weEnabledTravel = false;
        // 先恢复日志开关：下面 travel == null 那条早退路径也必须恢复（第三轮复验新问题#6）
        if (verboseSaved) FOElytraLog.fileVerbose = verboseLog.get();
        AutoElytraFlight travel = travelModule();
        if (travel == null) return;
        if (!travel.isActive()) {
            // 它自己已经收工/自己关了：别说「保持开启」那种和事实相反的话
            say("借来的「自动鞘翅飞行」已经自己停下来了（不用再还）");
        } else if (disableTravelOnArrive.get()) {
            travel.toggle();
            say("已关掉借来的「自动鞘翅飞行」（设置「到达后关掉跑图模块」）");
        } else {
            say("「自动鞘翅飞行」保持开启（按设置不主动关掉）");
        }
    }

    private AutoElytraFlight travelModule() {
        try {
            return Modules.get().get(AutoElytraFlight.class);
        } catch (Throwable t) {
            FOElytraLog.detailError("travelModule", t);
            return null;
        }
    }

    // ------------------------------------------------------------------ 12) 挖竖井下降

    private void digTick() {
        if (!autoDig.get()) {
            say("「自动挖竖井下降」是关的：请自己把角色带到密室层（模块会在 %d 格内自动找库开库）",
                searchRadius.get());
            enterScan();
            return;
        }
        if (phaseTicks == 1) {
            BaritoneHook.stop();
            BlockBreaker.reset();
            digCount = 0;
            digStallTicks = 0;
            airBelowTicks = 0;
            landingWaitTicks = 0;
            say("开始挖竖井下降：现在 Y=%d，目标 Y=%d", mc.player.getBlockY(), digY.get());
        }
        // 「不用挖也已经够深」要先判：否则在船上/水里的时候会先白等 20 秒才走到这里（第三轮复验新问题#1 附带项）
        if (mc.player.getBlockY() <= digY.get()) {
            say("已到 Y=%d（目标 %d）→ 开始找宝库", mc.player.getBlockY(), digY.get());
            BlockBreaker.cancel();
            enterScan();
            return;
        }
        /*
         * 先等落地（第二轮复审查出来的 P1）：
         * 「飞到 16 格内就停飞」那一刀通常是在滑翔中砍的，人还会带着速度往前冲、落下去 ——
         * 悬空开挖既挖不准（一会儿就没方块可挖），也容易摔。所以这里先等真的站到地上再动镐子。
         *
         * ⚠️ 判定必须放宽（第三轮复验新问题#1）：只认 isOnGround() 的话，**水面/船/矿车/梯子/脚手架**上
         * 永远是 false —— 试炼密室正好在海面下时，飞过去落进水里就会被判「等了 20 秒还没落地」并关掉模块。
         * 现在「水面上、骑乘中、在攀爬」都算「已经落定了」。
         */
        if (mc.player.isGliding() || !landed()) {
            if (landingWaitTicks++ > LANDING_WAIT_MAX_TICKS) {
                fail(String.format(Locale.ROOT, "等了 %d 秒还没落地（滑翔 %s／着地 %s／在水里 %s／骑乘 %s）——"
                    + "请自己落地或落到平台上，再开模块",
                    LANDING_WAIT_MAX_TICKS / 20, mc.player.isGliding() ? "中" : "否",
                    mc.player.isOnGround() ? "是" : "否", mc.player.isTouchingWater() ? "是" : "否",
                    mc.player.hasVehicle() ? "是" : "否"));
                return;
            }
            if (landingWaitTicks % 100 == 1) {
                say("等着落地再挖竖井（当前 Y=%d，滑翔 %s）…", mc.player.getBlockY(),
                    mc.player.isGliding() ? "中" : "否");
            }
            return;
        }
        landingWaitTicks = 0;
        if (mc.player.getHealth() <= digAbortHealth.get().floatValue()) {
            fail(String.format(Locale.ROOT, "下降路上血量掉到 %.1f（阈值 %.1f）——停下来保命，请自己处理完再开模块",
                mc.player.getHealth(), digAbortHealth.get()));
            return;
        }
        if (digCount >= maxDig.get()) {
            fail(String.format(Locale.ROOT, "已经挖了 %d 格还没到 Y=%d（上限 %d）——把「下降到 Y」调高，或把上限调大",
                digCount, digY.get(), maxDig.get()));
            return;
        }
        if (digDelayTicks > 0) {
            digDelayTicks--;
            return;
        }

        BlockPos below = mc.player.getBlockPos().down();
        BlockState st = mc.world.getBlockState(below);
        // 流体：绝不往下挖（这是挖竖井唯一真会死人的情况）
        if (!st.getFluidState().isEmpty()) {
            fail("脚下方块是流体（岩浆/水）——已停止下降，请自己在旁边绕开或换个位置再开模块");
            return;
        }
        if (st.isOf(Blocks.BEDROCK)) {
            fail("下面是基岩，挖不下去了（当前 Y=" + mc.player.getBlockY() + "）");
            return;
        }
        if (st.isAir()) {
            /*
             * 已经在空中（罕见：站在方块边缘时自己这一列下方是空气）。
             * 不算「挖不动」，但也不能无限等着 —— 连续 60 tick 下方还是空气就明说，别静默 idling（第三轮复验「存-1」）。
             */
            if (airBelowTicks++ > 60) {
                fail("脚下不是实心方块（站到方块边缘了？）——请站到方正的位置再开模块");
                return;
            }
            return;
        }
        airBelowTicks = 0;

        /*
         * 「挖不动」超时（第一轮审查抓出来的 P1-8）：
         * BlockBreaker.tick 在「一个方块挖了 200 tick 还没掉」时会自己 cancel 并把内部计时清零，
         * 然后继续返回 false —— 于是同一块硬方块（黑曜石/没镐子）会 200 tick 一轮无限循环，
         * digCount 永远不涨，maxDig 永远触发不了，DIG 阶段就永久卡死了。
         * 这里用「连续尝试挖同一块方块多少 tick 都没挖掉」兜住它；注意只在**真的要挖方块**时才计数
         * （上面 air/流体/基岩三条都已经 return，所以不会把「等落地」「等下落」算成挖不动）。
         */
        if (digStallTicks++ > DIG_STALL_MAX_TICKS) {
            fail(String.format(Locale.ROOT, "挖不动了：%d 秒都没挖掉脚下方块（已挖 %d 格，当前 Y=%d）。"
                + "多半是快捷栏没镐子，或方块太硬（黑曜石/远古残骸）",
                DIG_STALL_MAX_TICKS / 20, digCount, mc.player.getBlockY()));
            return;
        }

        if (BlockBreaker.tick(below)) {
            digCount++;
            digStallTicks = 0;
            digDelayTicks = 3;
            if (digCount % 5 == 0) {
                FOElytraLog.detail("下降中：已挖 %d 格，当前 Y=%d（目标 %d）", digCount, mc.player.getBlockY(), digY.get());
            }
        }
    }

    // ------------------------------------------------------------------ 收尾

    private void finish(String message) {
        say("%s", message);
        phase = Phase.DONE;
        cleanup(false);
        maybeAutoClose("任务结束");
    }

    private void fail(String reason) {
        if (phase == Phase.FAILED) return;
        failReason = reason;
        FOElytraLog.err("自动不祥宝库失败：%s", reason);     // 失败一律进聊天栏（这条不该被开关藏起来）
        FOElytraLog.detail("失败诊断：阶段 %s｜位置 %d %d %d｜钥匙 %d｜食物(%s)｜已开 %d｜去过 %d 个密室",
            phase.toString(), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
            keyCount(), foodCountText(), openedTotal, chambersVisited);
        phase = Phase.FAILED;
        cleanup(true);
        try {
            for (String l : FOElytraLog.snapshot(reason, 200, 8)) {
                // 上面已经 err 过一次同一条了（第二轮审查 N13）：回显日志尾巴时跳过它，别让失败原因出现两遍
                if (l.contains("自动不祥宝库失败：")) continue;
                FOElytraLog.chatRaw(l);
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("fail snapshot", t);
        }
        maybeAutoClose("任务失败");
    }

    /**
     * 需求 2：背包里没有钥匙 → 聊天栏提醒 + 自动关闭。
     *
     * <p>提醒写三轮（聊天栏 + 快捷键上方提示），因为这条消息决定「模块为什么自己关了」，
     * 一闪而过等于没说。钥匙来源也一起说清楚 —— 本模块默认绕开刷怪笼，不刷钥匙。</p>
     */
    private void noKeyStop() {
        String msg = String.format(Locale.ROOT,
            "背包里没有%s（现在：不祥 %d 个 / 普通 %d 个）。%s",
            keyName(),
            countKey(Items.OMINOUS_TRIAL_KEY),
            countKey(Items.TRIAL_KEY),
            alsoNormal.get() ? "两种试炼钥匙都没有，开不了任何宝库。" : "开不祥宝库必须用不祥试炼钥匙。");
        say("%s", msg);
        // ⚠️ 这一行**没有** varargs（参数是空的），所以百分号必须写单个 `%`：
        // 写成 `%%` 时 FOElytraLog.fmt 的空参数分支会原样返回字符串，聊天栏就会出现字面的「30%%」。
        say("钥匙来源：喝不祥之瓶 → 靠近试炼刷怪笼拿到「试炼之兆」→ 打死它刷出的怪，30% 概率掉不祥试炼钥匙。"
            + "本模块默认「绕过试炼刷怪笼」，不替你刷，请自己备好。");
        // 这一条不受「关键节点发聊天栏提示」影响：它回答的是「模块为什么自己关了」，必须留下记录
        FOElytraLog.warn("已停止任务%s", noKeyAutoClose.get() ? "并自动关闭模块（设置「没钥匙时自动关闭」）"
            : "（设置里关掉了「没钥匙时自动关闭」，模块保持开启）");
        try {
            // 快捷键上方那一条不走聊天栏，所以不受「关键节点发聊天栏提示」影响：
            // 这条消息决定「模块为什么自己关了」，必须让人一眼看见。
            mc.inGameHud.setOverlayMessage(Text.of("[自动不祥宝库] 没有" + keyName() + "，任务停止"), false);
        } catch (Throwable t) {
            FOElytraLog.detailError("overlayMessage", t);
        }
        phase = Phase.FAILED;
        failReason = "没有" + keyName();
        cleanup(true);
        if (noKeyAutoClose.get() && isActive()) {
            FOElytraLog.detail("按设置「没钥匙时自动关闭」关闭模块");
            toggle();
        }
    }

    private void maybeAutoClose(String why) {
        if (!isActive()) return;
        if (autoClose.get()) {
            say("按设置「任务结束后自动关闭」关闭模块（%s；可在设置里改）", why);
            toggle();
        } else {
            say("「任务结束后自动关闭」是关的：模块保持开启，等你自己决定（%s）", why);
        }
    }

    /** 收尾：把借来的东西全部还回去，绝不留「插件还按着键 / Baritone 还在走」的状态。 */
    private void cleanup(boolean aborting) {
        BlockBreaker.cancel();
        if (vaultOpener != null) {
            if (vaultOpener.status() == TaskStatus.RUNNING) vaultOpener.abort(aborting ? "任务中止" : "任务结束");
            vaultOpener = null;
        }
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        endTravel();
        candidates.clear();
        pendingCandidates.clear();
        pendingKeys.clear();
        scanning = false;
    }

    // ------------------------------------------------------------------ 工具

    private int keyCount() {
        int n = countKey(Items.OMINOUS_TRIAL_KEY);
        if (alsoNormal.get()) n += countKey(Items.TRIAL_KEY);
        return n;
    }

    private boolean hasAnyKey() {
        return keyCount() > 0;
    }

    private String keyName() {
        return alsoNormal.get() ? "试炼钥匙（普通/不祥都算）" : "不祥试炼钥匙";
    }

    private String foodName() {
        List<Item> list = foodItems.get();
        if (list == null || list.isEmpty()) return "食物（清单为空）";
        StringBuilder sb = new StringBuilder();
        for (Item it : list) {
            if (it == null) continue;
            if (sb.length() > 0) sb.append("/");
            sb.append(it.getName().getString());
        }
        return sb.length() == 0 ? "食物（清单为空）" : sb.toString();
    }

    /**
     * 食物数量的可读文本。
     *
     * <p>为什么要有它（第二轮审查 N5）：{@code countFood()} 在「清单为空」时返回
     * {@code Integer.MAX_VALUE} 表示「不检查」，如果直接拿去 {@code %d}，
     * 聊天栏和日志就会出现「食物（清单为空） 2147483647 个」这种东西 —— 用户会以为模块坏了。</p>
     */
    private String foodCountText() {
        List<Item> list = foodItems.get();
        if (list == null || list.isEmpty()) return foodName() + "（不检查）";
        return foodName() + " " + countFood() + " 个";
    }

    private int countFood() {
        if (mc.player == null) return 0;
        List<Item> list = foodItems.get();
        if (list == null || list.isEmpty()) return Integer.MAX_VALUE;   // 没配就算「够用」，不要误停
        int n = 0;
        for (Item it : list) {
            if (it == null) continue;
            n += ItemHelper.countInInventory(mc.player, it);
        }
        return n;
    }

    private String describeLoot() {
        StringBuilder sb = new StringBuilder();
        List<Item> items = targetItems.get();
        if (items != null) {
            for (Item it : items) {
                if (it == null) continue;
                if (sb.length() > 0) sb.append("、");
                sb.append(it.getName().getString());
            }
        }
        String ench = targetEnchants.get();
        if (ench != null && !ench.isBlank()) {
            if (sb.length() > 0) sb.append("、");
            sb.append("附魔书[").append(ench.trim()).append("]");
        }
        return sb.length() == 0 ? "（空！不会判命中）" : sb.toString();
    }

    /** 物品清单的空值兜底（设置被清空时 List.of()，别让 null 传进状态机）。 */
    private static List<Item> safeItems(List<Item> in) {
        return in == null ? List.of() : in;
    }

    /** 把「附魔 id」那串文本解析成 id 列表（解析不了的跳过并留痕，绝不抛）。 */
    private List<Identifier> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<Identifier> out = new ArrayList<>();
        for (String part : raw.split("[,，]")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            if (!s.contains(":")) s = "minecraft:" + s;      // 允许只写 wind_burst
            try {
                Identifier id = Identifier.of(s);
                if (!out.contains(id)) out.add(id);
            } catch (Throwable t) {
                FOElytraLog.warn("「目标魔咒」里的这个 id 看不懂，已跳过：%s", s);
            }
        }
        return out;
    }

    /**
     * 「关键节点说一句」的统一出口。
     *
     * <p>为什么需要它：{@code FOElytraLog.info/warn/err} 是<b>既进聊天栏、又进文件</b>的
     * （见 {@link FOElytraLog} 的注释），所以「用 log 级别控制聊天栏」是控制不了的 —— 上一版就是这么错的：
     * 「关键节点发聊天栏提示」这个开关形同虚设，开关掉了一样刷屏。
     * 这里显式分流：开着就 {@code info}（聊天栏 + 文件），关掉就 {@code detail}（只进文件）。</p>
     */
    private void say(String fmt, Object... args) {
        if (talkInChat.get()) {
            FOElytraLog.info(fmt, args);
        } else {
            FOElytraLog.detail(fmt, args);
        }
    }

    /** 数物品时统一判空（onActivate 早期 mc.player 理论上是有的，但别赌）。 */
    private int countKey(Item item) {
        return mc.player == null ? 0 : ItemHelper.countInInventory(mc.player, item);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** HUD/日志用的失败原因。 */
    public String failReason() {
        return failReason;
    }
}
