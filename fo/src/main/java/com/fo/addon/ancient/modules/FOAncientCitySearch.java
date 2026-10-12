package com.fo.addon.ancient.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.ancient.core.AncientChestExtractor;
import com.fo.addon.ancient.core.AncientChestStore;
import com.fo.addon.ancient.core.AncientEnchantData;
import com.fo.addon.ancient.core.AncientJigsawSimulator;
import com.fo.addon.ancient.core.AncientLootEvaluator;
import com.fo.addon.ancient.core.AncientLootTable;
import com.fo.addon.ancient.core.AncientOpenedChestPolicy;
import com.fo.addon.ancient.core.AncientResourceSource;
import com.fo.addon.ancient.core.AncientSearchLogic;
import com.fo.addon.ancient.core.AncientSeedStore;
import com.fo.addon.ancient.core.AncientStealStats;
import com.fo.addon.ancient.core.CubiomesJNI;
import com.fo.addon.ancient.core.FoundChest;
import com.fo.addon.ancient.core.GameVersionOption;
import com.fo.addon.ancient.core.LootTarget;
import com.fo.addon.ancient.core.StealStatsFile;
import com.fo.addon.ancient.core.XaeroWaypointBridge;
import com.fo.addon.utils.AncientCityLocatorLogic;
import com.fo.addon.utils.AncientStealLogic;
import com.fo.addon.utils.StealStatsSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * FO 古城战利品搜索（半自动）。
 * 整条链已是**纯 Java 自写实现**（零黑盒）：
 * 种子 → AncientCityLocatorLogic 直接算出候选古城 → cubiomes 只负责群系验证 →
 * AncientJigsawSimulator 重放拼图 → AncientChestExtractor 取箱子坐标与 LootTableSeed →
 * AncientLootEvaluator 自研掷骰判定 附魔金苹果 / 迅捷潜行3 → 结果输出聊天 + Xaero 路径点 + 世界内高亮。
 *
 * <p>只标记这两样物品的箱子（其余战利品不需要位置）：附魔金=金色、迅捷潜行3=蓝色、两者=紫色。
 * 命中记录写在本地文件里，跨重启保留；箱子被开掉或被挖掉时，世界高亮与路径点一并删除。
 *
 * <p>半径语义：搜索半径的单位是「区块」，但距离按**方块距离**计算，基准点是城市原点方块坐标。
 * （旧实现按区块距离、基准点是「城市原点 + 8」，两者在半径边界会差半个区块。）
 *
 * <p>V6.25：新增「自动偷取」设置组——打开容器时自动取走 **附魔金苹果** 与 **迅捷潜行3 附魔书**，
 * 其余物品一律不动。方案移植自彗星端（Meteor Client）官方 {@code InventoryTweaks} 的 Auto Steal
 * （源码 + 本机 1.21.11-85 字节码双向核对），差别有两点：
 * ① 官方只能按 {@code Item} 匹配，**认不出附魔书附的是哪条附魔**，所以拿不到「迅捷潜行3」——
 *    本模块读 {@code ItemEnchantmentsComponent} 按附魔 ID + 等级判定，补上了这个能力；
 * ② 官方在后台线程 {@code Thread.sleep} 后点槽位（跨线程发交互包），本模块改成
 *    「事件里只快照槽位 → TickEvent.Post 里逐个取」，全程客户端线程（对齐 V6.11 的教训）。
 *
 * <p>V6.26：**自动偷取收紧为「只偷箱子」**。V6.25 沿用了官方的行为——不判断开的是什么容器，
 * 只要容器内容同步过来就偷，实机反馈会把玩家自己存在**潜影盒**里的附魔金苹果/迅捷潜行书
 * 一并取走。现改为：必须确证「打开的是箱子方块（{@code ChestBlock}）」才偷，依据复用本模块
 * 「刚打开的是哪个方块」的准星判定（{@link #TARGET_FRESH_TICKS} 时效），潜影盒 / 木桶 / 末影箱
 * 等一律不偷；判断依据缺失或过期时保守不偷。判定纯逻辑在
 * {@code AncientStealLogic.shouldScanContainer}（可单测）。
 *
 * <p>V6.27：**累计偷取统计**。设置页「自动偷取」开关正下方新增只读文字块
 * （{@code 您已使用本插件共计获取了 / N 个附魔金苹果 / M 迅捷潜行III}），
 * 数据写游戏目录 {@code fo-steal-stats.json}、**跨重启保留、不自动清零**（用户要求长期累加）。
 * ⚠️ 记账口径是「**真的取到手才算**」：{@code InvUtils.shiftClick()} 不返回成败，背包满时点击等于白点，
 * 所以在点击时记下槽位原本的内容、下一刻回读核对（槽位变空/变少才算取到），
 * 避免累计数字虚高。界面关掉来不及核对的那一次按乐观口径计入。
 *
 * <p>V6.28：**「应用种子」改成点一次就记住**。{@code manualSeed} 原本只活在内存里、每次启动游戏归零，
 * 用户每次开模块都得重设。现在点「应用种子」会把值写进 {@code fo-ancient-seed.json}（全局一份），
 * 模块激活时自动带上；想换种子就改「种子」输入框再点一次应用（覆盖保存）；
 * 把输入框清空再点应用 = 清除保存的种子（关闭自动应用）。持久化见 {@code AncientSeedStore}。
 * ⚠️ 刻意**只影响搜索用的种子**：不动本模块原有的记录种子校验与渲染过滤
 * （{@code currentSeedGuess()}），避免"自动带种子"顺带触发清档等新副作用。
 *
 * <p>V6.29：累计统计从「自动偷取」组里挪出来，单独成组「累计统计」（建在 sgSteal 之后 →
 * 渲染在「初始延迟」下面）。分组本身就是可折叠的 {@code WSection}，展示控件也改用
 * {@code WSection} 当框（抬头行当标题、计数行放框内），解决"没对齐、没框"的问题。
 */
public class FOAncientCitySearch extends Module {

    /** 命中记录落盘位置（游戏目录下，每个玩家各存自己的一份）。 */
    private static final String STORE_FILE_NAME = "fo-ancient-chests.json";

    /**
     * Xaero 路径点颜色。
     *
     * <p>直接给 Xaero 的颜色枚举名（{@code WaypointColor}），不再用数字索引猜——
     * 枚举常量已对着真实 Xaero jar 核对：{@code GOLD}=金、{@code BLUE}=蓝、{@code PURPLE}=浅紫。
     */
    private static final String WAYPOINT_COLOR_GOLD = "GOLD";
    private static final String WAYPOINT_COLOR_BLUE = "BLUE";
    private static final String WAYPOINT_COLOR_PURPLE = "PURPLE";

    /**
     * 「上一 tick 准星指向的方块」记录的时效（tick）。
     *
     * <p>开箱那一刻的准星指向是判断「刚打开的是哪个方块」的唯一依据，过期就不能再用
     * （防止一个无关 GUI 借旧指向触发判定）。开箱检测与自动偷取共用这个阈值。
     */
    private static final int TARGET_FRESH_TICKS = 10;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSeed = settings.createGroup("种子设置");
    private final SettingGroup sgHighlight = settings.createGroup("高亮");
    private final SettingGroup sgSteal = settings.createGroup("自动偷取");
    /**
     * 累计统计单独一个分组（V6.29）。
     *
     * <p>Meteor 的设置分组渲染成 {@code WSection} —— 本身就是「能展开能关闭」的面板，
     * 与「高亮」「自动偷取」同一种控件。用户要求统计放在「初始延迟」下面并单独成组，
     * 所以这里建在 sgSteal 之后（分组按创建顺序渲染）。
     */
    private final SettingGroup sgStats = settings.createGroup("累计统计");

    private final Setting<LootTarget> searchTypeSetting;
    private final Setting<GameVersionOption> gameVersionSetting;
    private final Setting<Integer> searchRadiusSetting;
    private final Setting<Boolean> showDistanceSetting;
    private final Setting<Boolean> autoSearchSetting;
    private final Setting<Boolean> debugModeSetting;

    private final Setting<String> seedSetting;
    private final Setting<Boolean> applySeedSetting;
    private final Setting<Boolean> startSearchSetting;

    private final Setting<Boolean> highlightSetting;
    private final Setting<Integer> highlightRadiusSetting;
    private final Setting<SettingColor> goldColorSetting;
    private final Setting<SettingColor> blueColorSetting;
    private final Setting<SettingColor> purpleColorSetting;
    private final Setting<Boolean> autoCleanSetting;
    private final Setting<Boolean> beamSetting;
    private final Setting<XaeroWaypointBridge.Preference> waypointChannelSetting;

    private final Setting<Boolean> autoStealSetting;
    /** 只读展示：累计偷取统计（V6.27；V6.29 挪到独立的「累计统计」分组里）。 */
    private final Setting<Boolean> stealStatsSetting;
    private final Setting<List<Item>> stealItemsSetting;
    private final Setting<Integer> swiftSneakMinLevelSetting;
    private final Setting<Integer> stealDelaySetting;
    private final Setting<Integer> stealRandomDelaySetting;
    private final Setting<Integer> stealInitDelaySetting;

    private final MinecraftClient mc;
    private Long manualSeed;
    private boolean searching;
    private CompletableFuture<Void> searchTask;

    /**
     * 待写入的命中点：<b>搜索结果在后台线程算出，但落盘与 Xaero 路径点写入必须在客户端线程做</b>。
     *
     * <p>这不是洁癖 —— V6.10 实机崩溃就是踩在这里：搜索跑在 CompletableFuture 后台线程，
     * 直接调 Xaero 的 {@code ThirdPartyWaypoints.add(...)}，而 Xaero 的渲染线程每帧都在遍历
     * 同一张 HashMap，于是渲染线程抛 ConcurrentModificationException 整局崩掉。
     * Xaero 的路径点容器不是线程安全的，也不该由我们去同步它的内部迭代。
     * 同理，{@code AncientChestStore} 也是普通 LinkedHashMap，后台写 + 客户端线程清理会打架。
     */
    private final Deque<PendingMark> pendingMarks = new ArrayDeque<>();
    /** 最近一次搜索用的种子；渲染时用它过滤本地记录，防止别的种子的残留记录产生假高亮。 */
    private volatile Long activeSeed;
    /** 本轮搜索的结果首次落地前，先【整体替换】该作用域的旧记录（旧记录可能来自别的种子）。 */
    private boolean storeReplacePending;

    /** 一个待应用的命中点（坐标 + 标签 + 预生成的路径点名字）。 */
    private record PendingMark(int x, int y, int z, String label, String waypointName) {
    }

    /** 已找到的目标箱子记录（按作用域分组）。 */
    private AncientChestStore chestStore = new AncientChestStore();
    private boolean storeLoaded;

    /**
     * 「已应用的种子」的本地持久化（V6.28，全局一份）。
     *
     * <p>{@link #manualSeed} 只活在内存里，每次启动游戏都归零 —— 于是用户每次开模块都得重点一次
     * 「应用种子」。现在点一次就把值存进 {@code fo-ancient-seed.json}，激活时自动带回来。
     */
    private final AncientSeedStore seedStore = new AncientSeedStore();
    private boolean seedStoreLoaded;

    /** 上一 tick 准星指向的方块，用来判断「刚打开的是哪个箱子」。 */
    private BlockPos lastTargetedBlock;
    /** {@link #lastTargetedBlock} 的记录时间（tick），用于过滤过期指向。 */
    private long lastTargetedAtTick;
    private boolean wasContainerOpen;
    private long tickCounter;
    private int cleanCooldown;

    // ---------- 自动偷取（V6.25）----------

    /**
     * 待偷取的容器槽位队列。
     *
     * <p><b>只在客户端线程消费</b>：彗星端原版是在后台线程里 {@code Thread.sleep} 后再点槽位，
     * 那等于从非渲染线程发交互包。本项目在 V6.11 已经吃过一次「后台线程动共享状态」的亏
     * （Xaero 渲染线程 ConcurrentModificationException 直接崩游戏），所以这里改成
     * 「事件里只快照槽位 → TickEvent.Post 里逐个取」，全程不跨线程。
     */
    private final Deque<Integer> stealQueue = new ArrayDeque<>();
    /** {@link #stealQueue} 所属容器的 syncId；界面一关或换成别的容器，整队作废。 */
    private int stealQueueSyncId = -1;
    /** 距下一次取物还要等几刻（对应彗星端的 delay / initial-delay / random）。 */
    private int stealDelayTimer;
    /** 本轮偷到的物品统计（物品名 → 个数），取完在聊天栏汇报一次。 */
    private final Map<String, Integer> stealSummary = new LinkedHashMap<>();

    /**
     * 上一次点击的槽位，以及那一刻该槽位里的东西——用于**下一刻核对这次点击是否真的取走了**。
     *
     * <p>为什么要核对：{@code InvUtils.shiftClick().slotId(i)} 不返回成败，背包满时点击等于白点。
     * 直接在心那一刻记账会让「累计统计」虚高（用户要的是长期累计，数字必须可信）。
     * shift 点击是整组挪走，所以下一刻该槽位变空 = 真取到；还剩原来的数量 = 没生效，不记。
     */
    private int pendingStealSlot = -1;
    private String pendingStealItemId;
    private String pendingStealBookEnchantId;
    private int pendingStealBookLevel;
    private int pendingStealCount;

    /** 累计统计有改动、还没落盘。 */
    private boolean stealStatsDirty;

    /**
     * 「柱子地表 Y」缓存：key = (x<<32)|z。每根柱子只扫一次地表，维度切换时整体失效。
     */
    private final Map<Long, Integer> groundCache = new HashMap<>();
    private Object groundCacheWorld;

    /**
     * 服务端是否会同步箱子的 LootTable 字段给客户端（自适应学习，见 {@link AncientOpenedChestPolicy}）。
     * 只有确认同步后，才允许凭「lootTable == null」判定箱子被开过。
     */
    private boolean lootSyncConfirmed;

    public FOAncientCitySearch() {
        super(AddonTemplate.CATEGORY, "FO 古城战利品搜索",
            "精确搜索古城中包含附魔金苹果和迅捷潜行3的箱子位置（半自动，结果输出到聊天与 Xaero 路径点；"
                + "开启「自动偷取」后开箱即自动取走这两样）");

        mc = MinecraftClient.getInstance();

        searchTypeSetting = sgGeneral.add(new EnumSetting.Builder<LootTarget>()
            .name("搜索类型")
            .description("选择要搜索的物品类型")
            .defaultValue(LootTarget.BOTH)
            .build());

        gameVersionSetting = sgGeneral.add(new EnumSetting.Builder<GameVersionOption>()
            .name("游戏版本")
            .description("你要预测的是哪个游戏版本的战利品。古城表在版本间有三处差异："
                + "≤1.20.6 用旧版附魔语法；1.21.6 起第 8 条由 saddle（不耗随机数）变成 leather（耗一次）；"
                + "≥26.1 少一个 name_tag 条目。选错会让物品判定出错，但箱子位置仍然正确。"
                + "单人档按自己的客户端版本选；连服务器按服务端原生版本选，不确定就逐个试。")
            .defaultValue(GameVersionOption.FROM_26_1)
            .build());

        searchRadiusSetting = sgGeneral.add(new IntSetting.Builder()
            .name("搜索半径")
            .description("以玩家为中心的搜索半径（区块）")
            .defaultValue(1000)
            .min(1000).max(5000)
            .sliderMin(1000).sliderMax(5000)
            .build());

        showDistanceSetting = sgGeneral.add(new BoolSetting.Builder()
            .name("显示距离")
            .description("在结果中显示距离信息")
            .defaultValue(true)
            .build());

        autoSearchSetting = sgGeneral.add(new BoolSetting.Builder()
            .name("自动搜索")
            .description("启用模块时自动开始搜索")
            .defaultValue(false)
            .build());

        debugModeSetting = sgGeneral.add(new BoolSetting.Builder()
            .name("调试模式")
            .description("开启后输出详细调试信息（DLL加载、城市定位、箱子判定明细、异常堆栈），便于排查问题")
            .defaultValue(false)
            .build());

        seedSetting = sgSeed.add(new StringSetting.Builder()
            .name("种子")
            .description("输入世界种子（改完记得点一次「应用种子」）")
            .defaultValue("-7346913998703726680")
            .build());

        applySeedSetting = sgSeed.add(new BoolSetting.Builder()
            .name("应用种子")
            .description("应用手动输入的种子，并记住它——以后打开模块自动生效，不用再点。"
                + "想换种子：改上面的输入框后再点一次这里覆盖保存；"
                + "想取消自动应用：把输入框清空后再点一次这里")
            .defaultValue(false)
            .onChanged(this::onApplySeedChanged)
            .build());

        startSearchSetting = sgSeed.add(new BoolSetting.Builder()
            .name("开始搜索")
            .description("开始搜索古城中的物品")
            .defaultValue(false)
            .onChanged(v -> {
                if (v && !searching) {
                    startSearch();
                }
            })
            .build());

        highlightSetting = sgHighlight.add(new BoolSetting.Builder()
            .name("高亮箱子")
            .description("走近古城时把命中的箱子画上高亮框（附魔金=金色 / 迅捷潜行3=蓝色 / 两者=紫色）")
            .defaultValue(true)
            .build());

        highlightRadiusSetting = sgHighlight.add(new IntSetting.Builder()
            .name("高亮半径")
            .description("玩家多少格以内的命中箱子才画高亮框（方块）")
            .defaultValue(256)
            .min(8).max(256)
            .sliderMin(8).sliderMax(256)
            .build());

        goldColorSetting = sgHighlight.add(new ColorSetting.Builder()
            .name("附魔金苹果颜色")
            .description("只有附魔金苹果的箱子所用高亮颜色")
            .defaultValue(new SettingColor(255, 200, 0, 77))
            .build());

        blueColorSetting = sgHighlight.add(new ColorSetting.Builder()
            .name("迅捷潜行3颜色")
            .description("只有迅捷潜行3 附魔书的箱子所用高亮颜色")
            .defaultValue(new SettingColor(60, 130, 255, 77))
            .build());

        purpleColorSetting = sgHighlight.add(new ColorSetting.Builder()
            .name("两者都有颜色")
            .description("同时有附魔金苹果和迅捷潜行3 的箱子所用高亮颜色")
            .defaultValue(new SettingColor(170, 60, 255, 77))
            .build());

        autoCleanSetting = sgHighlight.add(new BoolSetting.Builder()
            .name("自动清理已开箱子")
            .description("箱子被打开或被挖掉后，自动删掉它的高亮框和 Xaero 路径点")
            .defaultValue(true)
            .build());

        beamSetting = sgHighlight.add(new BoolSetting.Builder()
            .name("目标柱子")
            .description("给命中箱子画一根通到地表的同色细柱（Baritone 风格、穿墙可见），"
                + "方便从地表找到挖掘点；可见半径与「高亮半径」共用")
            .defaultValue(true)
            .build());

        waypointChannelSetting = sgHighlight.add(new EnumSetting.Builder<XaeroWaypointBridge.Preference>()
            .name("路径点写入方式")
            .description("玩家路径点集（默认）：等同于你自己按 B 加的路径点，可管理可传送，会进你的路径点列表（Xaero 24.7.1+）。"
                + "官方第三方接口：路径点挂在独立来源下，不写进你自己的路径点文件（需 Xaero 26.x）。"
                + "不想让这些点显示在小地图上，可在 Xaero 小地图设置里单独关闭小地图的路径点显示，世界地图不受影响")
            .defaultValue(XaeroWaypointBridge.Preference.WAYPOINT_SET)
            .build());

        // ---------- 自动偷取（V6.25；移植彗星端 InventoryTweaks 的 Auto Steal）----------

        autoStealSetting = sgSteal.add(new BoolSetting.Builder()
            .name("自动偷取")
            .description("自动偷取箱子附魔金苹果和迅捷潜行三")
            .defaultValue(true)
            .build());

        // V6.27 新增、V6.29 挪进独立的「累计统计」分组（显示在「初始延迟」下面）。
        // 控件由 AddonTemplate 注册的 StealStatsSetting 工厂负责画，这里只占位置。
        stealStatsSetting = sgStats.add(new StealStatsSetting.Builder()
            .name("战果")
            .description("本插件至今一共帮你拿到了多少（跨重启累计，不清零）。数据存在游戏目录 fo-steal-stats.json")
            .build());

        stealItemsSetting = sgSteal.add(new ItemListSetting.Builder()
            .name("偷取物品列表")
            .description("打开容器时自动取走的物品（按物品匹配，与彗星端一致）。默认只取附魔金苹果。")
            .defaultValue(Items.ENCHANTED_GOLDEN_APPLE)
            .build());

        swiftSneakMinLevelSetting = sgSteal.add(new IntSetting.Builder()
            .name("迅捷潜行最低等级")
            .description("附魔书的额外识别：迅捷潜行等级 ≥ 此值就取走（0=不识别附魔书，只按物品列表偷）。"
                + "默认 3 = 只偷满级书。彗星端原版只能按物品匹配、认不出附魔书附的是哪条附魔，"
                + "所以它拿不到迅捷潜行3 —— 这条是本模块比它多出来的。")
            .defaultValue(3)
            .min(0).max(3)
            .sliderMin(0).sliderMax(3)
            .build());

        stealDelaySetting = sgSteal.add(new IntSetting.Builder()
            .name("偷取间隔")
            .description("每取走一组之后等多少刻再取下一组（刻≈50 毫秒）。"
                + "彗星端原版是 20 毫秒 + 随机 50 毫秒，约合 1~2 刻。")
            .defaultValue(1)
            .min(0).max(40)
            .sliderMin(0).sliderMax(20)
            .build());

        stealRandomDelaySetting = sgSteal.add(new IntSetting.Builder()
            .name("随机附加延迟")
            .description("在「偷取间隔」之上再随机加 0~此值 刻，避免每次都固定节奏（对应彗星端的 random）")
            .defaultValue(1)
            .min(0).max(40)
            .sliderMin(0).sliderMax(20)
            .build());

        stealInitDelaySetting = sgSteal.add(new IntSetting.Builder()
            .name("初始延迟")
            .description("开箱到取第一组之间先等多少刻（对应彗星端的 initial-delay；"
                + "本模块由服务端内容同步事件触发、已确保槽位数据到位，所以可以很短）")
            .defaultValue(1)
            .min(0).max(40)
            .sliderMin(0).sliderMax(20)
            .build());

        searching = false;
        manualSeed = null;
    }

    @Override
    public void onActivate() {
        loadStore();
        // V6.28：自动带上上次「应用」过的种子 —— 点过一次就一直记住，不用每次开模块重设。
        // 没应用过（或已手动清除）时保持 manualSeed = null，行为与以前一致（联机会提示手动设种子）。
        loadSeedStore();
        if (manualSeed == null && seedStore.hasSeed()) {
            manualSeed = seedStore.seed();
            info("已自动应用上次保存的种子: " + manualSeed + "（想换种子：改「种子」输入框后再点一次「应用种子」）");
        }
        if (manualSeed != null) {
            // 输入框内容与"已生效的种子"不一致时点一下 —— 否则用户会困惑"我明明填了新的却没生效"
            String boxText = seedSetting.get().trim();
            if (!boxText.isEmpty()) {
                try {
                    if (AncientSearchLogic.parseSeedInput(boxText) != manualSeed.longValue()) {
                        info("提示：「种子」输入框里是 " + boxText + "，当前生效的是 " + manualSeed
                            + "；要用输入框里的那个请点一次「应用种子」");
                    }
                } catch (RuntimeException e) {
                    // 解析不了就算了，别为一个提示影响模块启用
                }
            }
        }
        if (autoSearchSetting.get()) {
            startSearch();
        }
        info("古城附魔金搜索模块已启用");
        reportStoredMarks();
    }

    @Override
    public void onDeactivate() {
        // 丢弃还没落地的命中点：模块已关，再往 Xaero 写就属于意外副作用
        pendingMarks.clear();
        // 关模块时把没取完的偷取队列一起丢掉（已取到的先汇报掉）
        abortStealQueue();
        if (searchTask != null && !searchTask.isDone()) {
            searchTask.cancel(true);
            setSearching(false);
            info("搜索已取消");
        }
        // 关闭模块：把本模块写入的 Xaero 路径点全部收掉（按记录坐标逐个删，
        // 不会碰你手动加的点）。本地记录保留，重新开启模块时会验证并重写路径点。
        if (mc.world != null) {
            String scope = currentScope();
            if (scope != null && !chestStore.isEmpty(scope)) {
                int removed = clearWaypointsOf(chestStore.marks(scope));
                if (removed > 0) {
                    info("已移除 " + removed + " 个 Xaero 路径点（本地记录保留，重新开启模块可恢复）");
                }
            }
        }
        groundCache.clear();
        info("古城附魔金搜索模块已禁用");
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        tickCounter++;
        if (searchTask != null && searchTask.isDone()) {
            setSearching(false);
            searchTask = null;
            startSearchSetting.set(false);
        }
        // 后台线程算出的命中点在这里落地：客户端线程 = 渲染线程，写 Xaero 不会与渲染打架
        flushPendingMarks();
        if (autoCleanSetting.get()) {
            cleanupStaleMarks();
        }
        // 自动偷取：队列也在客户端线程逐个消费（不跨线程，见 stealQueue 的注释）
        if (autoStealSetting.get()) {
            processStealQueue();
        }
    }

    // ==================== 自动偷取（V6.25）====================

    /**
     * 自动偷取：服务端把容器内容同步过来时，快照「该偷哪些槽位」。
     *
     * <p>触发点对齐彗星端（它也是监听 {@code InventoryEvent}，而不是每 tick 扫容器）——
     * 这样天然避开「刚开箱、槽位还是空的」那一刻：内容同步事件到达时物品已经在客户端了，
     * 不会把「还没同步」误判成「箱子里没有目标物品」。
     */
    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!autoStealSetting.get()) return;
        if (mc.player == null || mc.world == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        if (mc.currentScreen instanceof InventoryScreen) return;   // 玩家自己的背包不算容器

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || handler == mc.player.playerScreenHandler) return;
        if (event.packet.syncId() != handler.syncId) return;       // 不是当前这个界面的同步包

        // 只偷箱子（V6.26）：判断依据复用本模块「刚打开的是哪个方块」那一套
        // （上一 tick 的准星指向 + 时效窗口）——这样偷取与「已开检测」永远指同一个方块。
        // 彗星端原版不判容器，实测会把用户存在潜影盒里的物资一起取走，这里收紧。
        // 判定保守：依据缺失/过期 → 不偷（宁可漏，也不能误偷自己的储物盒）。
        boolean targetKnown = lastTargetedBlock != null
            && tickCounter - lastTargetedAtTick <= TARGET_FRESH_TICKS;
        boolean isChest = targetKnown
            && mc.world.getBlockState(lastTargetedBlock).getBlock() instanceof ChestBlock;
        if (!AncientStealLogic.shouldScanContainer(targetKnown, isChest)) {
            debug("自动偷取：打开的不是箱子，跳过（"
                + (targetKnown ? "识别为其他容器" : "拿不到方块依据") + "）");
            return;
        }

        // 容器区 = 除玩家背包 36 格（主背包 27 + 快捷栏 9）之外的槽位
        int playerInvStart = Math.max(0, handler.slots.size() - 36);
        List<Integer> hit = new ArrayList<>();
        for (int i = 0; i < playerInvStart; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            boolean inList = stealItemsSetting.get().contains(stack.getItem());
            int swift = AncientStealLogic.swiftSneakLevel(bookEnchantLevels(stack));
            if (AncientStealLogic.shouldSteal(inList, swift, swiftSneakMinLevelSetting.get())) {
                hit.add(i);
            }
        }
        if (hit.isEmpty()) return;

        // 换了容器（syncId 变了）说明上一个容器已经关掉：它的最后一次点击没法再核对，
        // 按乐观口径计入累计统计，别把那次漏掉
        if (stealQueueSyncId != handler.syncId) {
            commitPendingStealOptimistically();
        }

        stealQueue.clear();
        stealQueue.addAll(hit);
        stealQueueSyncId = handler.syncId;
        stealDelayTimer = Math.max(0, stealInitDelaySetting.get());
        stealSummary.clear();
        debug("自动偷取：命中 " + hit.size() + " 个槽位");
    }

    /**
     * 逐个取走队列里的槽位（客户端线程，每刻最多取一组）。
     *
     * <p>界面被关掉或换成别的容器 → 整队丢弃：彗星端原版 {@code moveSlots} 每一步也都要重查界面，
     * 这里做同一件事，避免「对着已经关掉的界面点槽位」。
     *
     * <p>每次开头先结算上一刻那次点击（{@link #verifyPendingSteal}），所以队列排空后还要再跑
     * 一刻才能真正收尾——这也是这里不能简单地「队列空就 return」的原因。
     */
    private void processStealQueue() {
        if (stealQueue.isEmpty() && pendingStealSlot < 0) return;   // 没活干

        ScreenHandler handler = mc.player == null ? null : mc.player.currentScreenHandler;
        boolean sameScreen = mc.currentScreen instanceof HandledScreen<?>
            && handler != null && handler.syncId == stealQueueSyncId;
        if (!sameScreen) {
            abortStealQueue();      // 界面关了/换容器了
            return;
        }

        // 结算上一刻的点击：只有槽位真的空了才计入累计统计（背包满时点击不生效，不能虚记）
        verifyPendingSteal(handler);

        if (stealQueue.isEmpty()) {
            reportStealSummary();   // 队列已空且无待核对 → 本轮结束
            return;
        }
        if (stealDelayTimer > 0) {
            stealDelayTimer--;
            return;
        }

        int slotId = stealQueue.poll();
        ItemStack stack = handler.getSlot(slotId).getStack();
        if (!stack.isEmpty()) {
            stealSummary.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
            rememberPendingSteal(slotId, stack);
            InvUtils.shiftClick().slotId(slotId);   // 整组挪进背包（对应彗星端的 shiftClick）
        }

        int bound = stealRandomDelaySetting.get();
        stealDelayTimer = AncientStealLogic.delayTicks(stealDelaySetting.get(), bound,
            bound > 0 ? ThreadLocalRandom.current().nextInt(0, bound) : 0);
    }

    /** 记下这次点击动了哪个槽位、里面原本是什么，供下一刻核对。 */
    private void rememberPendingSteal(int slotId, ItemStack stack) {
        pendingStealSlot = slotId;
        pendingStealCount = stack.getCount();
        pendingStealItemId = Registries.ITEM.getId(stack.getItem()).toString();
        int swift = AncientStealLogic.swiftSneakLevel(bookEnchantLevels(stack));
        pendingStealBookEnchantId = swift > 0 ? AncientStealStats.SWIFT_SNEAK_ID : null;
        pendingStealBookLevel = swift;
    }

    /**
     * 核对上一次点击是否真的把东西取走了，真取到才计入累计统计。
     *
     * <p>shift 点击是**整组**挪走，所以：槽位变空（或数量变少）= 取到了；数量没变 = 没生效
     * （典型是背包满了），此时不记账。
     */
    private void verifyPendingSteal(ScreenHandler handler) {
        if (pendingStealSlot < 0) return;
        int slot = pendingStealSlot;
        int amount = pendingStealCount;
        String itemId = pendingStealItemId;
        String bookEnchantId = pendingStealBookEnchantId;
        int bookLevel = pendingStealBookLevel;
        clearPendingSteal();

        if (handler == null || slot >= handler.slots.size()) return;
        ItemStack now = handler.getSlot(slot).getStack();
        int left = now.isEmpty() ? 0 : now.getCount();
        int taken = Math.max(0, amount - left);
        if (taken <= 0) return;

        AncientStealStats stats = AncientStealStats.get();
        if (bookEnchantId != null && bookLevel > 0) {
            // 附魔书单独按「附魔 + 等级」计，不混进物品计数里
            stats.addBook(bookEnchantId, bookLevel, taken);
        } else if (itemId != null) {
            stats.addItem(itemId, taken);
        }
        stealStatsDirty = true;
    }

    /** 界面已经关掉、没法再核对：这次点击按「已经取到」乐观计入（点击早已发出，绝大多数都生效）。 */
    private void commitPendingStealOptimistically() {
        if (pendingStealSlot < 0) return;
        String itemId = pendingStealItemId;
        String bookEnchantId = pendingStealBookEnchantId;
        int bookLevel = pendingStealBookLevel;
        int amount = pendingStealCount;
        clearPendingSteal();

        AncientStealStats stats = AncientStealStats.get();
        if (bookEnchantId != null && bookLevel > 0) {
            stats.addBook(bookEnchantId, bookLevel, amount);
        } else if (itemId != null) {
            stats.addItem(itemId, amount);
        }
        stealStatsDirty = true;
    }

    private void clearPendingSteal() {
        pendingStealSlot = -1;
        pendingStealItemId = null;
        pendingStealBookEnchantId = null;
        pendingStealBookLevel = 0;
        pendingStealCount = 0;
    }

    /** 累计统计有改动就落盘（跨重启保留）。失败不影响游戏，下次还会再试。 */
    private void saveStealStatsIfDirty() {
        if (!stealStatsDirty) return;
        if (StealStatsFile.save(AncientStealStats.get())) {
            stealStatsDirty = false;
        } else {
            debug("累计统计落盘失败，稍后重试");
        }
    }

    /** 界面关掉/换容器：丢弃队列，并把已取到的战果先汇报掉（不丢信息）。 */
    private void abortStealQueue() {
        // 界面已经关了，没法再核对最后一次点击 —— 按乐观口径计入累计统计
        commitPendingStealOptimistically();
        stealQueue.clear();
        stealQueueSyncId = -1;
        reportStealSummary();
    }

    /** 汇报本轮偷取战果（物品名 + 个数），并顺手把累计统计落盘。 */
    private void reportStealSummary() {
        if (!stealSummary.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            stealSummary.forEach((name, count) -> {
                if (!sb.isEmpty()) sb.append("、");
                sb.append(name).append("×").append(count);
            });
            info("自动偷取完成：" + sb);
            stealSummary.clear();
        }
        saveStealStatsIfDirty();
    }

    /** 该物品堆的附魔（附魔 ID → 等级）；非附魔书返回空表。 */
    private Map<String, Integer> bookEnchantLevels(ItemStack stack) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) return Map.of();
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        Map<String, Integer> out = new HashMap<>();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
            entry.getKey().getKey().ifPresent(key -> out.put(key.getValue().toString(), entry.getIntValue()));
        }
        return out;
    }

    /** 世界内高亮：只画玩家附近的命中箱子，颜色按命中类型区分。 */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!highlightSetting.get() || mc.player == null || mc.world == null) return;
        String scope = currentScope();
        if (scope == null) return;
        if (chestStore.isEmpty(scope)) return;

        // 记录按种子过滤：本地存的那批属于某一次搜索的世界；如果当前世界是别的种子
        // （例如单人切了存档、或换了种子重新搜但还没落地），就不画，避免「别的箱子也高亮」。
        Long storedSeed = chestStore.scopeSeed(scope);
        Long currentSeed = currentSeedGuess();
        if (storedSeed != null && currentSeed != null && !storedSeed.equals(currentSeed)) return;

        BlockPos p = mc.player.getBlockPos();
        List<AncientChestStore.Mark> near = AncientChestStore.within(
            chestStore.marks(scope), p.getX(), p.getY(), p.getZ(), highlightRadiusSetting.get());
        for (AncientChestStore.Mark m : near) {
            SettingColor color = colorOf(m.label());
            event.renderer.box(new BlockPos(m.x(), m.y(), m.z()), color, color, ShapeMode.Both, 0);
            if (beamSetting.get()) {
                drawBeam(event, m.x(), m.y(), m.z(), color);
            }
        }
    }

    /**
     * Baritone 风格的目标柱：从箱子顶画到地表，颜色与高亮一致。
     *
     * <p>穿墙可见是刻意的 —— {@code event.renderer} 走 Meteor 的 {@code NO_DEPTH_TEST}
     * 管线（另一个 {@code depthRenderer} 才受地形遮挡）。古城箱子在 y≈-51 的地下，
     * 柱子要从地表就能看到才有「找挖掘点」的意义。地表 Y 每个箱子只扫一次（缓存）。
     */
    private void drawBeam(Render3DEvent event, int x, int y, int z, SettingColor color) {
        Object dim = mc.world != null ? mc.world.getRegistryKey() : null;
        if (dim != groundCacheWorld) {
            groundCache.clear();
            groundCacheWorld = dim;
        }
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Integer ground = groundCache.get(key);
        if (ground == null) {
            ground = findSurfaceY(x, z);
            if (ground == null) {
                return; // 区块未加载等，本帧不画柱子（高亮框照画）
            }
            groundCache.put(key, ground);
        }
        if (ground <= y + 1) {
            return; // 地表不高于箱顶（城市露出地面等），柱子没有意义
        }
        double half = 0.0625; // 全宽 1/8 格，Baritone 风格细柱
        event.renderer.box(x + 0.5 - half, y + 1, z + 0.5 - half,
            x + 0.5 + half, ground, z + 0.5 + half, color, color, ShapeMode.Both, 0);
    }

    /** 箱子正上方的地表 Y（首个非空气方块之上）。区块未加载或异常时返回 null。 */
    private Integer findSurfaceY(int x, int z) {
        BlockPos pos = new BlockPos(x, mc.world.getBottomY(), z);
        if (!isChunkLoaded(pos)) {
            return null;
        }
        try {
            return mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 命中类型 → 高亮/路径点颜色。 */
    private SettingColor colorOf(String label) {
        if (AncientChestStore.LABEL_GOLD.equals(label)) return goldColorSetting.get();
        if (AncientChestStore.LABEL_SWIFT.equals(label)) return blueColorSetting.get();
        return purpleColorSetting.get();
    }

    private String waypointColorOf(String label) {
        if (AncientChestStore.LABEL_GOLD.equals(label)) return WAYPOINT_COLOR_GOLD;
        if (AncientChestStore.LABEL_SWIFT.equals(label)) return WAYPOINT_COLOR_BLUE;
        return WAYPOINT_COLOR_PURPLE;
    }

    // ==================== 命中记录的落盘与清理 ====================

    /** 当前作用域：服务器地址（单人则「单人」）+ 维度，避免不同服务器/维度的记录互相串。 */
    private String currentScope() {
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
        Path path = storePath();
        try {
            chestStore = Files.exists(path)
                ? AncientChestStore.fromJson(Files.readString(path, StandardCharsets.UTF_8))
                : new AncientChestStore();
        } catch (IOException | RuntimeException e) {
            chestStore = new AncientChestStore();
            warning("读取古城箱子记录失败，已从空记录开始: " + e.getMessage());
        }
    }

    private void saveStore() {
        Path path = storePath();
        try {
            Files.writeString(path, chestStore.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warning("保存古城箱子记录失败: " + e.getMessage());
        }
    }

    /**
     * 开启模块时的恢复流程：本地记录 → 验证 → 重新写路径点。
     *
     * <ol>
     *   <li>记录带的种子与当前世界对不上 → 整批清空（属于另一个世界）；</li>
     *   <li>逐个验证：不是箱子（被挖/被替换）→ 删；确认被开过（战利品已生成）→ 删；</li>
     *   <li>幸存的记录重新写进 Xaero 路径点（关闭模块时它们被收掉了）。</li>
     * </ol>
     *
     * <p>「开过」的判定是 best-effort：只有确认服务端会把 LootTable 字段同步给客户端
     * （至少见过一个非空的）之后，才允许凭 null 判「已开」；判定不了就保留标记。
     * 模块开着时开的箱子当时就会被删掉，不依赖这一步。
     */
    private void reportStoredMarks() {
        loadStore();
        String scope = currentScope();
        if (scope == null) return;

        Long storedSeed = chestStore.scopeSeed(scope);
        Long currentSeed = currentSeedGuess();
        if (storedSeed != null && currentSeed != null && !storedSeed.equals(currentSeed)) {
            int n = chestStore.size(scope);
            chestStore.clear(scope);   // 换种子 = 换世界：标记、已开名单、种子全部清空
            saveStore();
            info("本地记录（" + n + " 个）属于另一个种子（" + storedSeed + "），已清空；请重新搜索");
            return;
        }

        List<AncientChestStore.Mark> marks = chestStore.marks(scope);
        if (marks.isEmpty()) return;

        // 第一遍：探测服务端是否会同步 LootTable 字段（见到一个非空的就实锤）
        boolean syncsLoot = lootSyncConfirmed;
        for (AncientChestStore.Mark m : marks) {
            BlockPos pos = new BlockPos(m.x(), m.y(), m.z());
            if (!isChunkLoaded(pos)) continue;
            if (!(mc.world.getBlockState(pos).getBlock() instanceof ChestBlock)) continue;
            if (mc.world.getBlockEntity(pos) instanceof LootableContainerBlockEntity lootable
                && lootable.getLootTable() != null) {
                syncsLoot = true;
                break;
            }
        }
        lootSyncConfirmed = syncsLoot;

        // 第二遍：逐个验证
        List<AncientChestStore.Mark> alive = new ArrayList<>();
        int removedMined = 0;
        int removedOpened = 0;
        for (AncientChestStore.Mark m : marks) {
            BlockPos pos = new BlockPos(m.x(), m.y(), m.z());
            if (!isChunkLoaded(pos)) {
                alive.add(m);   // 区块没加载，无从验证，先保留
                continue;
            }
            if (!(mc.world.getBlockState(pos).getBlock() instanceof ChestBlock)) {
                removedMined++; // 被挖/被替换
                continue;
            }
            if (mc.world.getBlockEntity(pos) instanceof LootableContainerBlockEntity lootable) {
                AncientOpenedChestPolicy.Decision d =
                    AncientOpenedChestPolicy.decide(syncsLoot, lootable.getLootTable() != null);
                if (d == AncientOpenedChestPolicy.Decision.UNOPENED) {
                    alive.add(m);
                } else if (d == AncientOpenedChestPolicy.Decision.OPENED) {
                    chestStore.markOpened(scope, m.x(), m.z());
                    removedOpened++;
                } else {
                    alive.add(m);   // 判定不了（服务端不下发该字段），保留
                }
            } else {
                alive.add(m);   // 拿不到块实体（少见），保留
            }
        }

        if (removedMined + removedOpened > 0) {
            chestStore.clearMarks(scope);
            if (storedSeed != null) {
                chestStore.setScopeSeed(scope, storedSeed);
            }
            for (AncientChestStore.Mark m : alive) {
                chestStore.add(scope, m);
            }
            saveStore();
        }

        if (alive.isEmpty()) {
            info("本地记录已全部失效（被挖 " + removedMined + " 个，已开 " + removedOpened + " 个），请重新搜索");
            return;
        }

        info("本地已保存 " + alive.size() + " 个古城目标箱子，走近会自动高亮；正在把路径点写回 Xaero"
            + "（已跳过被挖 " + removedMined + " 个、已开 " + removedOpened + " 个）...");
        List<PendingMark> batch = new ArrayList<>();
        for (int i = 0; i < alive.size(); i++) {
            AncientChestStore.Mark m = alive.get(i);
            batch.add(new PendingMark(m.x(), m.y(), m.z(), m.label(), m.label() + " #" + (i + 1)));
        }
        writeWaypoints(batch);
    }

    /**
     * 当前世界的种子（能拿到才返回）：单人/内置服直接问服务端；
     * 联机时退回「已应用的种子设置」。拿不到返回 {@code null}（此时渲染不做种子过滤，
     * 保持「重启后高亮还在」的持久化行为）。
     */
    private Long currentSeedGuess() {
        try {
            if (mc.getServer() != null) {
                return mc.getServer().getOverworld().getSeed();
            }
            if (applySeedSetting.get()) {
                return AncientSearchLogic.parseSeedInput(seedSetting.get().trim());
            }
        } catch (RuntimeException e) {
            // 种子串解析失败等情况就当作不知道
        }
        return null;
    }

    /**
     * 清理「已经没了」的箱子：箱子被打开，或者被挖掉/换成了别的方块。
     * 两种情况都把世界高亮与 Xaero 路径点一并删除。
     */
    private void cleanupStaleMarks() {
        if (mc.player == null || mc.world == null) return;
        String scope = currentScope();
        if (scope == null || chestStore.isEmpty(scope)) return;

        boolean changed = false;

        // 1) 刚打开容器：用上一 tick 的准星指向判断开的是哪个箱子。
        //    V6.16 实机 bug：玩家背包(E)也是 HandledScreen，走到箱子前按 E 开背包，
        //    被旧判定误当成「开箱」，把准星指着那个箱子的标记删了（且删的是本地记录，
        //    关掉背包也不会恢复）。修两处：显式排除玩家自己的背包界面；
        //    并要求指向记录是 10 tick 内的，防止过期的旧指向被无关 GUI 触发。
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            lastTargetedBlock = hit.getBlockPos();
            lastTargetedAtTick = tickCounter;
        }
        boolean containerOpen = mc.currentScreen instanceof HandledScreen<?>
            && !(mc.currentScreen instanceof InventoryScreen);
        if (containerOpen && !wasContainerOpen && lastTargetedBlock != null
            && tickCounter - lastTargetedAtTick <= TARGET_FRESH_TICKS) {
            // 先记入「已开名单」（V6.19）：之后所有搜索都跳过这个坐标，
            // 重开模块/重新搜索不会再把开过的箱子加回来
            if (mc.world.getBlockState(lastTargetedBlock).getBlock() instanceof ChestBlock) {
                chestStore.markOpened(scope, lastTargetedBlock.getX(), lastTargetedBlock.getZ());
            }
            if (chestStore.remove(scope, lastTargetedBlock.getX(), lastTargetedBlock.getZ())) {
                XaeroWaypointBridge.removeWaypointAt(lastTargetedBlock.getX(), lastTargetedBlock.getZ());
                debug("箱子已打开，删除高亮与路径点: " + lastTargetedBlock.toShortString());
                changed = true;
            }
            // 双箱：两半是同一个战利品，另一半一并删
            if (removePartnerMark(scope, lastTargetedBlock)) {
                changed = true;
            }
        }
        wasContainerOpen = containerOpen;

        // 2) 周期性检查箱子是否还在（限流，别每 tick 扫）
        if (--cleanCooldown <= 0) {
            cleanCooldown = 20;
            BlockPos p = mc.player.getBlockPos();
            int radius = Math.min(highlightRadiusSetting.get(), 128);
            for (AncientChestStore.Mark m : AncientChestStore.within(
                    chestStore.marks(scope), p.getX(), p.getY(), p.getZ(), radius)) {
                BlockPos pos = new BlockPos(m.x(), m.y(), m.z());
                if (!isChunkLoaded(pos)) continue;   // 未加载的区块读出来是空气，不能当成被挖了
                BlockState state = mc.world.getBlockState(pos);
                if (state.getBlock() instanceof ChestBlock) {
                    // 顺手自适应学习「服务端是否下发 LootTable」；确认下发后，
                    // 「null = 已开过」能抓到关着模块时被开掉的箱子（区块重载后字段已被服务端清空）
                    if (mc.world.getBlockEntity(pos) instanceof LootableContainerBlockEntity lootable) {
                        boolean present = lootable.getLootTable() != null;
                        AncientOpenedChestPolicy.Decision d =
                            AncientOpenedChestPolicy.decide(lootSyncConfirmed, present);
                        if (present) {
                            lootSyncConfirmed = true;
                        } else if (d == AncientOpenedChestPolicy.Decision.OPENED) {
                            chestStore.markOpened(scope, m.x(), m.z());
                            if (chestStore.remove(scope, m.x(), m.z())) {
                                XaeroWaypointBridge.removeWaypointAt(m.x(), m.z());
                                debug("箱子已开过（战利品已生成），删除高亮与路径点: " + pos.toShortString());
                                changed = true;
                            }
                        }
                    }
                    continue;
                }
                if (chestStore.remove(scope, m.x(), m.z())) {
                    XaeroWaypointBridge.removeWaypointAt(m.x(), m.z());
                    debug("箱子不在了（被挖/被替换），删除高亮与路径点: " + pos.toShortString());
                    changed = true;
                }
            }
        }

        // 只有真的删掉了东西才写盘，避免每秒一次无谓的磁盘 IO
        if (changed) {
            saveStore();
        }
    }

    /**
     * 双箱：开一半时另一半也是同一个战利品，一并删。
     *
     * <p>搭档方向照抄原版 {@code ChestBlock} 的判定（字节码核对）：
     * {@code CHEST_TYPE == LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise()}。
     * 只按这一个方向找搭档，不会误伤相邻摆放的同朝向单箱/另一组双箱。
     */
    private boolean removePartnerMark(String scope, BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return false;
        }
        Direction facing = state.get(ChestBlock.FACING);
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) {
            return false;
        }
        Direction partnerDir = type == ChestType.LEFT
            ? facing.rotateYClockwise()
            : facing.rotateYCounterclockwise();
        BlockPos partner = pos.offset(partnerDir);
        if (!isChunkLoaded(partner)) {
            return false;
        }
        BlockState partnerState = mc.world.getBlockState(partner);
        if (!(partnerState.getBlock() instanceof ChestBlock)
            || partnerState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
            return false;
        }
        chestStore.markOpened(scope, partner.getX(), partner.getZ());
        if (chestStore.remove(scope, partner.getX(), partner.getZ())) {
            XaeroWaypointBridge.removeWaypointAt(partner.getX(), partner.getZ());
            debug("双箱另一半已随开删除高亮与路径点: " + partner.toShortString());
            return true;
        }
        return false;
    }

    private boolean isChunkLoaded(BlockPos pos) {
        try {
            return mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void startSearch() {
        if (searching) {
            warning("搜索正在进行中...");
            return;
        }
        if (mc.player == null) {
            error("玩家不存在");
            return;
        }
        Long seed = manualSeed;
        if (seed == null && mc.getServer() != null) {
            seed = mc.getServer().getOverworld().getSeed();
        }
        if (seed == null) {
            error("无法获取世界种子，请手动设置种子");
            return;
        }
        activeSeed = seed;
        storeReplacePending = true;
        pendingMarks.clear();
        setSearching(true);
        BlockPos playerPos = mc.player.getBlockPos();
        info("开始搜索古城中的" + searchTypeSetting.get() + "... 半径: "
            + searchRadiusSetting.get() + " 区块 版本: " + gameVersionSetting.get());
        long finalSeed = seed;
        searchTask = CompletableFuture.runAsync(() -> {
            try {
                scanForCities(finalSeed, playerPos);
            } catch (Throwable t) {
                error("异步任务异常: " + t.getMessage());
                debugStack(t);
            } finally {
                setSearching(false);
            }
        });
    }

    private void scanForCities(long seed, BlockPos playerPos) {
        List<FoundChest> results = new ArrayList<>();
        int radius = searchRadiusSetting.get();

        // 群系验证暂时仍走 cubiomes（阶段 2.5 自写群系采样完成前，DLL 只保留这一个用途）
        CubiomesJNI jni = null;
        long generator = 0L;
        boolean jniReady = false;
        debug("DLL 加载状态: " + CubiomesJNI.isLibraryLoaded());
        try {
            if (CubiomesJNI.isLibraryLoaded()) {
                jni = new CubiomesJNI();
                int versionId = gameVersionSetting.get() == GameVersionOption.FROM_26_1 ? 24 : 23;
                generator = jni.initGenerator(versionId, seed);
                jniReady = true;
                debug("生成器初始化完成: versionId=" + versionId + " generator=" + generator);
            }
        } catch (Throwable t) {
            // native 链接失败（UnsatisfiedLinkError 等 Error 也要捕获，避免中断整个扫描）
            debug("生成器初始化失败: " + t);
            debugStack(t);
            jniReady = false;
        }

        // 古城定位改为纯 Java（零原生依赖、零黑盒）。
        // 语义：半径单位是区块，但距离按「方块距离」算，基准点是城市原点方块坐标
        //（旧实现按区块距离、基准点是「城市原点 + 8」，两者在半径边界会差半个区块）。
        info("搜索参数: 半径=" + radius + "区块, 种子=" + seed);
        info("战利品表版本: " + gameVersionSetting.get());
        List<AncientCityLocatorLogic.City> cities =
            AncientCityLocatorLogic.findNearby(seed, playerPos.getX(), playerPos.getZ(), radius);
        info("定位完成: 半径内候选古城 " + cities.size() + " 座");

        if (jniReady) {
            info("群系验证: 已启用（cubiomes）");
        } else {
            warning("CubiomesJNI 原生库不可用，跳过群系验证；候选里可能包含实际不生成的古城（前往前请留意）。");
        }

        int total = cities.size();
        int done = 0;
        int found = 0;
        int lastPct = -1;

        for (AncientCityLocatorLogic.City city : cities) {
            if (!searching) {
                return;
            }
            int pct = ++done * 100 / Math.max(1, total);
            if (pct - lastPct >= 10) {
                lastPct = pct;
                // 注意：Meteor info() 内部 String.format，裸 % 必须转义为 %%（对齐原版 %%）否则抛 Conversion 异常
                info("进度: " + done + "/" + total + " (" + pct + "%%)  已找到 " + found + " 个古城");
            }

            int cityChunkX = city.chunkX();
            int cityChunkZ = city.chunkZ();
            try {
                if (jniReady && jni != null) {
                    // 群系验证探针点与旧实现保持一致：城市原点 + 8（不要改成原点，那会改变验证结果）
                    int probeX = city.blockX() + 8;
                    int probeZ = city.blockZ() + 8;
                    boolean viable = jni.isViableAncientCity(generator, probeX, probeZ);
                    debug("古城候选 区块 " + cityChunkX + "," + cityChunkZ
                        + " 方块 " + city.blockX() + "," + city.blockZ() + " 群系验证=" + viable);
                    if (!viable) {
                        continue;
                    }
                }
                found++;
                // 结构重放仍走黑盒（V6.8 换成纯 Java 后即可删除黑盒 jar）。
                // 已用独立参考实现交叉验证过：纯 Java 管线给出的箱子集与黑盒完全一致。
                List<FoundChest> chests = chestsOfOneCity(seed, cityChunkX, cityChunkZ, playerPos);
                debug("城市箱子收集完成: 候选 " + chests.size() + " 个");
                results.addAll(chests);
            } catch (Throwable t) {
                // UnsatisfiedLinkError 等 Error 不能中断整个扫描
                debug("古城 区块 " + cityChunkX + "," + cityChunkZ + " 处理异常: " + t);
                debugStack(t);
            }
        }

        if (jni != null && generator != 0L) {
            try {
                jni.freeGenerator(generator);
            } catch (Throwable t) {
                // 忽略
            }
        }

        reportResults(results, playerPos);
        setSearching(false);
    }

    /**
     * 重放一座古城的结构、取出箱子并掷骰判定（纯 Java，无黑盒）。
     *
     * @param cityChunkX 古城所在【区块】X（与 {@code AncientCityLocatorLogic.City.chunkX()} 一致）
     * @param cityChunkZ 古城所在【区块】Z
     */
    private List<FoundChest> chestsOfOneCity(long seed, int cityChunkX, int cityChunkZ, BlockPos playerPos) {
        List<FoundChest> results = new ArrayList<>();
        AncientResourceSource structures = AncientResourceSource.bundledStructures();
        AncientResourceSource pools = AncientResourceSource.bundledPools();
        // 战利品表按游戏版本挑。表在版本间有三处差异：
        //   ≤1.20.6 附魔旧语法 / 1.21.6 起第 8 条由 saddle 常量变 leather uniform / ≥26.1 少 name_tag。
        // 选错会让每一次加权抽取错位 —— 实机表现正是「位置对、物品全错」。
        AncientResourceSource dataSource =
            AncientResourceSource.bundledLootTables(gameVersionSetting.get());

        List<AncientJigsawSimulator.Piece> pieces =
            AncientJigsawSimulator.simulate(seed, cityChunkX, cityChunkZ, structures, pools);
        List<AncientChestExtractor.Chest> chests =
            AncientChestExtractor.extract(pieces, seed, structures);
        debug("古城结构生成完成: 区块 " + cityChunkX + "," + cityChunkZ
            + " 拼图块 " + pieces.size() + " 箱子 " + chests.size());

        AncientEnchantData enchantData = AncientEnchantData.bundled();
        LootTarget target = searchTypeSetting.get();

        for (AncientChestExtractor.Chest chest : chests) {
            String lootTableId = chest.lootTable().replace("minecraft:", "");
            AncientLootTable table = AncientLootTable.load(lootTableId, dataSource);
            List<AncientLootEvaluator.ItemStack> items =
                AncientLootEvaluator.roll(table, chest.seed(), enchantData);

            // 与旧实现一致：「两者都搜索」是「任一命中就收」，不是「必须同时命中」
            boolean hasGold = target != LootTarget.SWIFT_SNEAK_3
                && AncientLootEvaluator.hasEnchantedGoldenApple(items);
            boolean hasSwift = target != LootTarget.ENCHANTED_GOLDEN_APPLE
                && AncientLootEvaluator.hasSwiftSneak(items, 3);
            if (!hasGold && !hasSwift) {
                debug("箱子 " + chest.x() + "," + chest.y() + "," + chest.z()
                    + " 判定: 附魔金=" + hasGold + " 迅捷3=" + hasSwift + "（无目标物品，跳过）");
                continue;
            }

            double dist = Math.sqrt(
                Math.pow(chest.x() - playerPos.getX(), 2)
                    + Math.pow(chest.y() - playerPos.getY(), 2)
                    + Math.pow(chest.z() - playerPos.getZ(), 2));
            String label = hasGold && hasSwift ? AncientChestStore.LABEL_BOTH
                : hasGold ? AncientChestStore.LABEL_GOLD : AncientChestStore.LABEL_SWIFT;
            results.add(new FoundChest(new BlockPos(chest.x(), chest.y(), chest.z()), dist, label));
            debug("箱子命中: " + chest.x() + "," + chest.y() + "," + chest.z() + " -> " + label);
        }
        return results;
    }

    private void reportResults(List<FoundChest> results, BlockPos playerPos) {
        if (results.isEmpty()) {
            warning("在指定范围内未找到包含" + searchTypeSetting.get() + "的古城箱子");
            return;
        }
        results.sort(Comparator.comparingDouble(fc -> fc.distance));

        // 本方法跑在搜索线程上，只做两件安全的事：整理待写入队列 + 输出聊天。
        // 落盘与 Xaero 写入都交给客户端线程（onTickPost），避免与渲染线程抢共享状态。
        for (int i = 0; i < results.size(); i++) {
            FoundChest fc = results.get(i);
            String name = showDistanceSetting.get()
                ? String.format("%s #%d (%.0fm)", fc.label, i + 1, fc.distance)
                : String.format("%s #%d", fc.label, i + 1);
            pendingMarks.add(new PendingMark(
                fc.pos.getX(), fc.pos.getY(), fc.pos.getZ(), fc.label, name));
        }

        info("=".repeat(50));
        info("搜索完成！找到 " + results.size() + " 个箱子:");
        info("=".repeat(50));
        for (int i = 0; i < results.size(); i++) {
            FoundChest fc = results.get(i);
            if (showDistanceSetting.get()) {
                info(String.format("[#%d][%s] 位置: %d, %d, %d (距离: %.0f 米)",
                    i + 1, fc.label, fc.pos.getX(), fc.pos.getY(), fc.pos.getZ(), fc.distance));
            } else {
                info(String.format("[#%d][%s] 位置: %d, %d, %d",
                    i + 1, fc.label, fc.pos.getX(), fc.pos.getY(), fc.pos.getZ()));
            }
        }
        info("=".repeat(50));
        info("高亮：附魔金=金色 / 迅捷潜行3=蓝色 / 两者=紫色；走近 " + highlightRadiusSetting.get() + " 格内自动显示");
        info("已开或已被挖掉的箱子会自动删掉高亮与路径点");
        info("=".repeat(50));
    }

    /**
     * 把队列里的命中点真正落地：写进本地记录 + 写进 Xaero 路径点。
     *
     * <p><b>只允许在客户端线程调用</b>（渲染也在这个线程上）—— 这是 V6.10 实机崩溃的修复点：
     * 以前这一步在后台线程直接调 Xaero，渲染线程正在遍历同一张 HashMap，
     * 于是抛 ConcurrentModificationException 把整局崩掉。
     */
    private void flushPendingMarks() {
        if (pendingMarks.isEmpty()) {
            return;
        }
        List<PendingMark> batch = new ArrayList<>(pendingMarks);
        pendingMarks.clear();
        debug("开始写入 " + batch.size() + " 个命中点（客户端线程）");

        // 1) 本地记录落盘（跨重启保留），高亮框与路径点都由这份记录驱动。
        //    每轮搜索【整体替换】该作用域的旧记录，并把种子一并记下：
        //    旧记录可能来自别的种子（或旧版本的错误判定），合并留着的话，
        //    只要那个位置恰好有真实箱子就会产生假高亮 —— V6.12 实机「别的箱子也高亮」的根源。
        loadStore();
        String scope = currentScope();
        int recorded = 0;
        int skippedOpened = 0;
        List<PendingMark> toWrite = new ArrayList<>();
        if (scope != null) {
            if (storeReplacePending) {
                storeReplacePending = false;
                // 本轮结果要【整体替换】旧记录：先把上一批写入的路径点撤掉再清记录。
                // 「已开名单」刻意保留（clearMarks 而非 clear）——开过的箱子正是靠它
                // 在新一轮搜索里被跳过（V6.19）
                clearWaypointsOf(chestStore.marks(scope));
                chestStore.clearMarks(scope);
            }
            if (activeSeed != null) {
                chestStore.setScopeSeed(scope, activeSeed);
            }
            for (PendingMark m : batch) {
                // 「已开名单」里的坐标跳过：不写标记、不恢复高亮、不加路径点（V6.19）
                if (chestStore.isOpen(scope, m.x(), m.z())) {
                    skippedOpened++;
                    continue;
                }
                if (chestStore.add(scope, new AncientChestStore.Mark(m.x(), m.y(), m.z(), m.label()))) {
                    recorded++;
                    toWrite.add(m);
                }
            }
            saveStore();
        }

        // 2) Xaero 路径点：附魔金=金色、迅捷潜行3=蓝色、两者=紫色（只写未被跳过的）
        if (!toWrite.isEmpty()) {
            writeWaypoints(toWrite);
        }

        if (scope != null && recorded > 0) {
            info("已记录 " + recorded + " 个新箱子到本地（本地共 " + chestStore.size(scope) + " 个）"
                + (skippedOpened > 0 ? "；已跳过 " + skippedOpened + " 个开过的箱子" : ""));
        }
    }

    /**
     * 把命中位置写进 Xaero 路径点，并明确告知实际走通的通道。
     *
     * <p>刻意把「没装 Xaero」和「装了但版本对不上」分开报 —— 旧实现两者都报
     * 「Xaero地图模组未安装」，在装了 Xaero 的客户端上纯属误导。
     */
    private void writeWaypoints(List<PendingMark> batch) {
        XaeroWaypointBridge.Preference pref = waypointChannelSetting.get();
        XaeroWaypointBridge.Channel channel = XaeroWaypointBridge.pick(pref);

        debug("Xaero 状态: " + XaeroWaypointBridge.statusText()
            + " | 官方通道=" + XaeroWaypointBridge.officialSupported()
            + " 路径点集通道=" + XaeroWaypointBridge.waypointSetSupported()
            + " | 实测通道=" + channel);

        if (channel == XaeroWaypointBridge.Channel.NONE) {
            warning(XaeroWaypointBridge.statusText() + "，无法添加路径点（世界内高亮不受影响）");
            if (pref != XaeroWaypointBridge.Preference.AUTO) {
                warning("当前「路径点写入方式」被设为「" + pref + "」，可改回「自动」再试");
            }
            return;
        }

        int added = 0;
        for (PendingMark m : batch) {
            if (XaeroWaypointBridge.addWaypoint(pref, m.x(), m.y(), m.z(),
                    m.waypointName(), m.label(), waypointColorOf(m.label()))) {
                added++;
            }
        }

        if (added == 0) {
            warning("Xaero 路径点写入失败（" + XaeroWaypointBridge.statusText()
                + "）。若此刻不在游戏世界内（例如停在主界面），请先进入世界再重试。");
            return;
        }

        String channelName = channel == XaeroWaypointBridge.Channel.OFFICIAL
            ? "官方第三方接口（不占用你自己的路径点列表）"
            : "玩家路径点集（会出现在你自己的路径点列表里）";
        info("已写入 " + added + "/" + batch.size() + " 个路径点到 Xaero，通道: " + channelName);
    }

    /** 把本模块写入的路径点撤掉（按记录坐标逐个删；桥内部两条通道都会尝试）。 */
    private int clearWaypointsOf(List<AncientChestStore.Mark> marks) {
        int removed = 0;
        for (AncientChestStore.Mark m : marks) {
            if (XaeroWaypointBridge.removeWaypointAt(m.x(), m.z())) {
                removed++;
            }
        }
        return removed;
    }

    /** 应用种子开关回调：解析输入、写入手动种子，并**持久化记住**（V6.28）。 */
    private void onApplySeedChanged(boolean apply) {
        if (!apply) return;

        String seedText = seedSetting.get().trim();

        // 清空输入框再点应用 = 忘掉保存的种子（关闭「自动应用」的唯一出口）
        if (seedText.isEmpty()) {
            manualSeed = null;
            seedStore.setSeed(null);
            boolean cleared = seedStore.save();
            info(cleared ? "已清除保存的种子（下次打开模块不再自动应用）"
                : "已清除本次生效的种子（⚠️ 文件写入失败，重启后可能仍会自动应用）");
            applySeedSetting.set(false);
            return;
        }

        long parsed;
        try {
            parsed = AncientSearchLogic.parseSeedInput(seedText);
        } catch (RuntimeException e) {
            error("设置种子失败: " + e.getMessage());
            applySeedSetting.set(false);
            return;
        }

        manualSeed = parsed;
        seedStore.setSeed(parsed);
        // 记住它：以后每次打开模块自动带上，不用再点（用户要求，V6.28）
        boolean saved = seedStore.save();
        info("已设置种子: " + seedText + (saved
            ? "（已保存，下次打开模块自动生效）"
            : "（⚠️ 保存失败，本次有效但重启后需重新应用）"));
        applySeedSetting.set(false);
    }

    /** 读一次「已应用的种子」（幂等）。 */
    private void loadSeedStore() {
        if (seedStoreLoaded) return;
        seedStoreLoaded = true;
        seedStore.setSeed(AncientSeedStore.load().seed());
    }

    private void setSearching(boolean value) {
        searching = value;
    }

    /** 调试模式输出（未开启时静默）。 */
    private void debug(String msg) {
        if (debugModeSetting.get()) {
            info("[调试] " + msg);
        }
    }

    /** 调试模式输出完整异常堆栈到聊天。 */
    private void debugStack(Throwable t) {
        if (!debugModeSetting.get()) {
            return;
        }
        info("[调试] 异常: " + t);
        for (StackTraceElement el : t.getStackTrace()) {
            info("[调试]   at " + el);
        }
        Throwable cause = t.getCause();
        while (cause != null) {
            info("[调试] 由以下原因引起: " + cause);
            for (StackTraceElement el : cause.getStackTrace()) {
                info("[调试]   at " + el);
            }
            cause = cause.getCause();
        }
    }
}
