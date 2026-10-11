package com.fo.addon.ancient.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.ancient.core.AncientChestExtractor;
import com.fo.addon.ancient.core.AncientChestStore;
import com.fo.addon.ancient.core.AncientEnchantData;
import com.fo.addon.ancient.core.AncientJigsawSimulator;
import com.fo.addon.ancient.core.AncientLootEvaluator;
import com.fo.addon.ancient.core.AncientLootTable;
import com.fo.addon.ancient.core.AncientResourceSource;
import com.fo.addon.ancient.core.AncientSearchLogic;
import com.fo.addon.ancient.core.CubiomesJNI;
import com.fo.addon.ancient.core.FoundChest;
import com.fo.addon.ancient.core.GameVersionOption;
import com.fo.addon.ancient.core.LootTarget;
import com.fo.addon.ancient.core.XaeroWaypointBridge;
import com.fo.addon.utils.AncientCityLocatorLogic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

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

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSeed = settings.createGroup("种子设置");
    private final SettingGroup sgHighlight = settings.createGroup("高亮");

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
    private final Setting<XaeroWaypointBridge.Preference> waypointChannelSetting;

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

    /** 一个待应用的命中点（坐标 + 标签 + 预生成的路径点名字）。 */
    private record PendingMark(int x, int y, int z, String label, String waypointName) {
    }

    /** 已找到的目标箱子记录（按作用域分组）。 */
    private AncientChestStore chestStore = new AncientChestStore();
    private boolean storeLoaded;

    /** 上一 tick 准星指向的方块，用来判断「刚打开的是哪个箱子」。 */
    private BlockPos lastTargetedBlock;
    private boolean wasContainerOpen;
    private int cleanCooldown;

    public FOAncientCitySearch() {
        super(AddonTemplate.CATEGORY, "FO 古城战利品搜索",
            "精确搜索古城中包含附魔金苹果和迅捷潜行3的箱子位置（半自动，结果输出到聊天与 Xaero 路径点）");

        mc = MinecraftClient.getInstance();

        searchTypeSetting = sgGeneral.add(new EnumSetting.Builder<LootTarget>()
            .name("搜索类型")
            .description("选择要搜索的物品类型")
            .defaultValue(LootTarget.ENCHANTED_GOLDEN_APPLE)
            .build());

        gameVersionSetting = sgGeneral.add(new EnumSetting.Builder<GameVersionOption>()
            .name("游戏版本")
            .description("你要预测的是哪个游戏版本的战利品。26.x 的古城战利品表比 1.21.11 少一个 name_tag 条目"
                + "（池权重 86 → 84），选错会让物品判定全错但箱子位置仍然正确。"
                + "单人档按你自己的客户端版本选；连服务器按服务端版本选。")
            .defaultValue(GameVersionOption.BELOW_26_1)
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
            .description("输入世界种子")
            .defaultValue("-7346913998703726680")
            .build());

        applySeedSetting = sgSeed.add(new BoolSetting.Builder()
            .name("应用种子")
            .description("应用手动输入的种子")
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
            .defaultValue(64)
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

        waypointChannelSetting = sgHighlight.add(new EnumSetting.Builder<XaeroWaypointBridge.Preference>()
            .name("路径点写入方式")
            .description("官方第三方接口：路径点挂在独立来源下，不写进你自己的路径点文件（需 Xaero 26.x）；"
                + "玩家路径点集：等同于你自己按 B 加的路径点，可管理可传送，但会进你的路径点列表（Xaero 24.7.1+）")
            .defaultValue(XaeroWaypointBridge.Preference.AUTO)
            .build());

        searching = false;
        manualSeed = null;
    }

    @Override
    public void onActivate() {
        loadStore();
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
        if (searchTask != null && !searchTask.isDone()) {
            searchTask.cancel(true);
            setSearching(false);
            info("搜索已取消");
        }
        info("古城附魔金搜索模块已禁用");
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
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
    }

    /** 世界内高亮：只画玩家附近的命中箱子，颜色按命中类型区分。 */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!highlightSetting.get() || mc.player == null || mc.world == null) return;
        String scope = currentScope();
        if (scope == null) return;
        if (chestStore.isEmpty(scope)) return;

        BlockPos p = mc.player.getBlockPos();
        List<AncientChestStore.Mark> near = AncientChestStore.within(
            chestStore.marks(scope), p.getX(), p.getY(), p.getZ(), highlightRadiusSetting.get());
        for (AncientChestStore.Mark m : near) {
            SettingColor color = colorOf(m.label());
            event.renderer.box(new BlockPos(m.x(), m.y(), m.z()), color, color, ShapeMode.Both, 0);
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

    /** 启动时报一下本地已有的记录，让玩家知道高亮还在。 */
    private void reportStoredMarks() {
        loadStore();
        String scope = currentScope();
        if (scope == null) return;
        int n = chestStore.size(scope);
        if (n > 0) {
            info("本地已保存 " + n + " 个古城目标箱子，走近会自动高亮（设置里可关）");
        }
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

        // 1) 刚打开容器：用上一 tick 的准星指向判断开的是哪个箱子
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            lastTargetedBlock = hit.getBlockPos();
        }
        boolean containerOpen = mc.currentScreen instanceof HandledScreen<?>;
        if (containerOpen && !wasContainerOpen && lastTargetedBlock != null) {
            if (chestStore.remove(scope, lastTargetedBlock.getX(), lastTargetedBlock.getZ())) {
                XaeroWaypointBridge.removeWaypointAt(lastTargetedBlock.getX(), lastTargetedBlock.getZ());
                debug("箱子已打开，删除高亮与路径点: " + lastTargetedBlock.toShortString());
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
                if (state.getBlock() instanceof ChestBlock) continue;
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
        info("战利品表: " + (gameVersionSetting.get() == GameVersionOption.FROM_26_1
            ? ">= 26.1（26.x 的表）" : "< 26.1（1.21.11 的表）"));
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
        // 战利品表按游戏版本挑：26.x 的表比 1.21.11 少一个 minecraft:name_tag 条目，
        // 池权重 86 → 84，会导致每一次加权抽取都错位（位置对、物品全错就是这么来的）
        boolean from26 = gameVersionSetting.get() == GameVersionOption.FROM_26_1;
        AncientResourceSource dataSource = AncientResourceSource.bundledLootTables(from26);

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

        // 1) 本地记录落盘（跨重启保留），高亮框与路径点都由这份记录驱动
        loadStore();
        String scope = currentScope();
        int recorded = 0;
        if (scope != null) {
            for (PendingMark m : batch) {
                if (chestStore.add(scope, new AncientChestStore.Mark(m.x(), m.y(), m.z(), m.label()))) {
                    recorded++;
                }
            }
            saveStore();
        }

        // 2) Xaero 路径点：附魔金=金色、迅捷潜行3=蓝色、两者=紫色
        writeWaypoints(batch);

        if (scope != null && recorded > 0) {
            info("已记录 " + recorded + " 个新箱子到本地（本地共 " + chestStore.size(scope) + " 个）");
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

    /** 应用种子开关回调：解析输入并写入手动种子。 */
    private void onApplySeedChanged(boolean apply) {
        if (apply) {
            String seedText = seedSetting.get().trim();
            if (!seedText.isEmpty()) {
                try {
                    manualSeed = AncientSearchLogic.parseSeedInput(seedText);
                    info("已设置种子: " + seedText);
                } catch (Exception e) {
                    error("设置种子失败: " + e.getMessage());
                }
            } else {
                error("请先输入种子");
            }
            applySeedSetting.set(false);
        }
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
