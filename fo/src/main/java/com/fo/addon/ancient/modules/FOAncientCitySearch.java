package com.fo.addon.ancient.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.ancient.core.AncientSearchLogic;
import com.fo.addon.ancient.core.CubiomesJNI;
import com.fo.addon.ancient.core.FoundChest;
import com.fo.addon.ancient.core.GameVersionOption;
import com.fo.addon.ancient.core.LootTarget;
import com.fo.addon.ancient.core.XaeroWaypointBridge;
import com.w.d.l.McVersion;
import com.w.d.w.j.Vec3i;
import com.w.d.w.x.Pair;
import com.w.d.w.x.Triple;
import com.w.d.x.WorldSeedFunctions;
import com.w.j.w.LootTable;
import com.w.j.w.VersionedRandom;
import com.w.j.w.l.LootItem;
import com.w.j.w.l.LootStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import w.j.AncientCityLootScanner;

/**
 * FO 古城战利品搜索（半自动）。
 * 移植自 misaka 古城模块：种子 → CubiomesJNI 快路径定位古城 region → 黑盒重放古城结构 →
 * 掷战利品表判定 附魔金苹果 / 迅捷潜行3 → 结果输出聊天 + Xaero 路径点。
 */
public class FOAncientCitySearch extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSeed = settings.createGroup("种子设置");

    private final Setting<LootTarget> searchTypeSetting;
    private final Setting<GameVersionOption> gameVersionSetting;
    private final Setting<Integer> searchRadiusSetting;
    private final Setting<Boolean> showDistanceSetting;
    private final Setting<Boolean> autoSearchSetting;

    private final Setting<String> seedSetting;
    private final Setting<Boolean> applySeedSetting;
    private final Setting<Boolean> startSearchSetting;

    private final MinecraftClient mc;
    private Long manualSeed;
    private boolean searching;
    private CompletableFuture<Void> searchTask;

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
            .description("选择游戏版本（26.1 使用新的 random_sequence 战利品种子系统）")
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

        searching = false;
        manualSeed = null;
    }

    @Override
    public void onActivate() {
        if (autoSearchSetting.get()) {
            startSearch();
        }
        info("古城附魔金搜索模块已启用");
    }

    @Override
    public void onDeactivate() {
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
                t.printStackTrace();
            } finally {
                setSearching(false);
            }
        });
    }

    private void scanForCities(long seed, BlockPos playerPos) {
        List<FoundChest> results = new ArrayList<>();
        int radius = searchRadiusSetting.get();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        CubiomesJNI jni = null;
        long generator = 0L;
        boolean jniReady = false;
        try {
            if (CubiomesJNI.isLibraryLoaded()) {
                jni = new CubiomesJNI();
                int versionId = gameVersionSetting.get() == GameVersionOption.FROM_26_1 ? 24 : 23;
                generator = jni.initGenerator(versionId, seed);
                jniReady = true;
            }
        } catch (Throwable t) {
            // native 链接失败（UnsatisfiedLinkError 等 Error 也要捕获，避免中断整个扫描）
            t.printStackTrace();
            jniReady = false;
        }

        if (!jniReady) {
            error("CubiomesJNI 原生库加载失败（DLL 缺失/不兼容/链接失败），无法定位古城。请确认使用 Windows 客户端且 DLL 完整。");
            return;
        }

        // 区域半径：半径(区块) / 24 + 1（对齐 misaka 真实实现，修正旧源码 /25 的错误）
        int regionRadius = AncientSearchLogic.regionRadius(radius);
        int baseRegionX = Math.floorDiv(playerChunkX, 24);
        int baseRegionZ = Math.floorDiv(playerChunkZ, 24);

        info("搜索参数: 半径=" + radius + "区块, 种子=" + seed + ", 区域半径=" + regionRadius);

        int total = (2 * regionRadius + 1) * (2 * regionRadius + 1);
        int done = 0;
        int found = 0;
        int lastPct = -1;

        for (int rx = baseRegionX - regionRadius; rx <= baseRegionX + regionRadius; rx++) {
            for (int rz = baseRegionZ - regionRadius; rz <= baseRegionZ + regionRadius; rz++) {
                if (!searching) {
                    return;
                }
                int pct = ++done * 100 / total;
                if (pct - lastPct >= 10) {
                    lastPct = pct;
                    info("进度: " + done + "/" + total + " (" + pct + "%)  已找到 " + found + " 个古城");
                }
                try {
                    if (jniReady && jni != null) {
                        int[] city = jni.findCityChunks(generator, rx, rz);
                        if (city != null) {
                            int blockX = (city[0] << 4) + 8;
                            int blockZ = (city[1] << 4) + 8;
                            if (!jni.isViableAncientCity(generator, blockX, blockZ)) {
                                continue;
                            }
                            // 距离判定对齐 misaka 原版：方块坐标差
                            double dist = Math.sqrt(Math.pow(blockX - playerPos.getX(), 2) + Math.pow(blockZ - playerPos.getZ(), 2));
                            if (dist > radius) {
                                continue;
                            }
                            found++;
                            results.addAll(chestsOfOneCity(seed, blockX, blockZ, playerPos, McVersion.V1_19));
                        }
                    }
                } catch (Throwable t) {
                    // UnsatisfiedLinkError 等 Error 不能中断整个扫描
                    t.printStackTrace();
                }
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

    private List<FoundChest> chestsOfOneCity(long seed, int cityX, int cityZ, BlockPos playerPos, McVersion mcVersion) {
        List<FoundChest> results = new ArrayList<>();
        AncientCityLootScanner scanner = new AncientCityLootScanner();
        WorldSeedFunctions wsf = new WorldSeedFunctions();
        scanner.generateAt(seed, cityX, cityZ, wsf);

        if (gameVersionSetting.get() == GameVersionOption.FROM_26_1) {
            processTriples(scanner.collectChestsRandomSequence(), results, playerPos);
        }
        try {
            processTriples(scanner.collectChestsLegacy(), results, playerPos);
        } catch (Exception e) {
            warning("古城生成失败: " + e.getMessage());
            e.printStackTrace();
        }
        return results;
    }

    private void processTriples(List<?> triples, List<FoundChest> results, BlockPos playerPos) {
        for (Object obj : triples) {
            Triple triple = (Triple) obj;
            Vec3i pos = (Vec3i) triple.getFirst();
            LootTable lootTable = (LootTable) triple.getSecond();
            long lootSeed = (Long) triple.getThird();

            LootTarget target = searchTypeSetting.get();
            boolean hasGold = (target == LootTarget.ENCHANTED_GOLDEN_APPLE || target == LootTarget.BOTH)
                && hasEnchantedGoldenApple(lootTable, lootSeed);
            boolean hasSwift = (target == LootTarget.SWIFT_SNEAK_3 || target == LootTarget.BOTH)
                && hasSwiftSneakThree(lootTable, lootSeed);
            if (!hasGold && !hasSwift) {
                continue;
            }

            double dist = Math.sqrt(
                Math.pow(pos.getX() - playerPos.getX(), 2)
                    + Math.pow(pos.getY() - playerPos.getY(), 2)
                    + Math.pow(pos.getZ() - playerPos.getZ(), 2));
            String label = hasGold && hasSwift ? "两者" : hasGold ? "附魔金" : "迅捷3";
            results.add(new FoundChest(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), dist, label));
        }
    }

    /** 掷战利品表，判定是否包含附魔金苹果（掷骰固定按 1.21 版本文本种子，与 misaka 一致）。 */
    private boolean hasEnchantedGoldenApple(LootTable lootTable, long lootSeed) {
        try {
            VersionedRandom rand = new VersionedRandom(lootSeed, McVersion.V1_21);
            for (Object obj : lootTable.generateLoot(rand)) {
                LootStack stack = (LootStack) obj;
                if ("enchanted_golden_apple".equals(stack.getItem().getItemId())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return false;
    }

    /** 掷战利品表，判定是否包含迅捷潜行3 附魔书。 */
    private boolean hasSwiftSneakThree(LootTable lootTable, long lootSeed) {
        try {
            VersionedRandom rand = new VersionedRandom(lootSeed, McVersion.V1_21);
            for (Object obj : lootTable.generateLoot(rand)) {
                LootStack stack = (LootStack) obj;
                LootItem item = stack.getItem();
                if (!"swift_sneak_book".equals(item.getItemId())) {
                    continue;
                }
                for (Object po : item.getEnchantments()) {
                    Pair pair = (Pair) po;
                    if ("swift_sneak".equals(pair.getFirst()) && (Integer) pair.getSecond() == 3) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return false;
    }

    private void reportResults(List<FoundChest> results, BlockPos playerPos) {
        if (results.isEmpty()) {
            warning("在指定范围内未找到包含" + searchTypeSetting.get() + "的古城箱子");
            return;
        }
        results.sort(Comparator.comparingDouble(fc -> fc.distance));

        if (!XaeroWaypointBridge.isLibraryLoaded()) {
            warning("Xaero地图模组未安装，无法添加路径点");
        } else {
            Object waypointSet = XaeroWaypointBridge.getCurrentWaypointSet();
            if (waypointSet == null) {
                warning("无法获取Xaero路径点集合");
            } else {
                int added = 0;
                for (int i = 0; i < results.size(); i++) {
                    FoundChest fc = results.get(i);
                    String group;
                    int color;
                    if ("附魔金".equals(fc.label)) {
                        group = "金";
                        color = 14;
                    } else if ("迅捷3".equals(fc.label)) {
                        group = "书";
                        color = 3;
                    } else {
                        group = "星";
                        color = 7; // 紫色独立组（源实现与"金"同为 14）
                    }
                    String name = showDistanceSetting.get()
                        ? String.format("%s #%d (%.0fm)", fc.label, i + 1, fc.distance)
                        : String.format("%s #%d", fc.label, i + 1);
                    XaeroWaypointBridge.addWaypoint(fc.pos.getX(), fc.pos.getY(), fc.pos.getZ(), name, group, color);
                    added++;
                }
                info("已添加 " + added + " 个位置到Xaero路径点");
            }
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
        info("提示: 路径点已添加到Xaero地图");
        info("=".repeat(50));
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
}
