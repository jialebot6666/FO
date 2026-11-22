package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.ancient.core.XaeroWaypointBridge;
import com.fo.addon.utils.StructurePlacementPure;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.BuriedTreasure;
import com.seedfinding.mcfeature.structure.DesertPyramid;
import com.seedfinding.mcfeature.structure.EndCity;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mcfeature.structure.Igloo;
import com.seedfinding.mcfeature.structure.JunglePyramid;
import com.seedfinding.mcfeature.structure.Mansion;
import com.seedfinding.mcfeature.structure.Mineshaft;
import com.seedfinding.mcfeature.structure.Monument;
import com.seedfinding.mcfeature.structure.NetherFossil;
import com.seedfinding.mcfeature.structure.OceanRuin;
import com.seedfinding.mcfeature.structure.PillagerOutpost;
import com.seedfinding.mcfeature.structure.RegionStructure;
import com.seedfinding.mcfeature.structure.RuinedPortal;
import com.seedfinding.mcfeature.structure.Shipwreck;
import com.seedfinding.mcfeature.structure.Structure;
import com.seedfinding.mcfeature.structure.Stronghold;
import com.seedfinding.mcfeature.structure.SwampHut;
import com.seedfinding.mcfeature.structure.Village;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * FO 结构搜索。
 *
 * <p>移植自 Tanghulu（https://github.com/Tanghulu2019-a/Tanghulu）的 {@code StructureFinder}（GPLv3）。
 * 原实现的 26.x 分支走「官方 jar + 独立进程 + Java 25」；本移植改为纯 Java：
 * 位置和群系判定都用 seedfinding {@code MCVersion.v1_21}。
 *
 * <p><b>为什么 v1_21 就等于 26.1：</b>已逐文件核对 Mojang 官方客户端 jar，
 * 1.21.1 / 1.21.11 / 26.1 的 {@code structure_set}、{@code structure}、{@code biome}、
 * {@code multi_noise_biome_source_parameter_list}、{@code template_pool} 完全一致；
 * 只有 {@code noise_settings} 的 caves / nether / floating_islands 有差异，不影响结构位置。
 */
public class FOStructureSearch extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStructures = settings.createGroup("结构选择");

    private final Setting<String> seedSetting = sgGeneral.add(new StringSetting.Builder()
        .name("种子")
        .description("世界种子；留空时读单人存档种子。")
        .defaultValue("-7346913998703726680")
        .build());

    private final Setting<Integer> radiusSetting = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径")
        .description("以玩家为中心的搜索半径（区块）。")
        .defaultValue(100)
        .min(10).max(20000)
        .sliderMin(10).sliderMax(20000)
        .build());

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("结果包含距离")
        .description("聊天结果里附带距离。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> xaeroSync = sgGeneral.add(new BoolSetting.Builder()
        .name("同步Xaero航点")
        .description("把结果按维度写入 Xaero 小地图，重复搜索自动去重。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxWaypoints = sgGeneral.add(new IntSetting.Builder()
        .name("每种结构航点上限")
        .description("同步到 Xaero 时每种结构最多写几个最近的点；0 表示不写航点。")
        .defaultValue(3)
        .min(0).max(50)
        .sliderMin(0).sliderMax(10)
        .build());

    private final Setting<Boolean> btnStart = sgGeneral.add(new BoolSetting.Builder()
        .name("开始搜索")
        .description("开启后立即搜索一次。")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) {
                onStartButton();
            }
        })
        .build());

    private final Setting<Boolean> findVillage = sgStructures.add(new BoolSetting.Builder().name("村庄").defaultValue(false).build());
    private final Setting<Boolean> findShipwreck = sgStructures.add(new BoolSetting.Builder().name("沉船").defaultValue(false).build());
    private final Setting<Boolean> findBuriedTreasure = sgStructures.add(new BoolSetting.Builder().name("埋藏的宝藏").defaultValue(false).build());
    private final Setting<Boolean> findOceanRuin = sgStructures.add(new BoolSetting.Builder().name("海底废墟").defaultValue(false).build());
    private final Setting<Boolean> findPillagerOutpost = sgStructures.add(new BoolSetting.Builder().name("掠夺者前哨站").defaultValue(false).build());
    private final Setting<Boolean> findDesertPyramid = sgStructures.add(new BoolSetting.Builder().name("沙漠神殿").defaultValue(false).build());
    private final Setting<Boolean> findIgloo = sgStructures.add(new BoolSetting.Builder().name("雪屋").defaultValue(false).build());
    private final Setting<Boolean> findJunglePyramid = sgStructures.add(new BoolSetting.Builder().name("丛林神庙").defaultValue(false).build());
    private final Setting<Boolean> findMansion = sgStructures.add(new BoolSetting.Builder().name("林地府邸").defaultValue(false).build());
    private final Setting<Boolean> findMineshaft = sgStructures.add(new BoolSetting.Builder().name("废弃矿井").defaultValue(false).build());
    private final Setting<Boolean> findMonument = sgStructures.add(new BoolSetting.Builder().name("海底神殿").defaultValue(false).build());
    private final Setting<Boolean> findRuinedPortal = sgStructures.add(new BoolSetting.Builder().name("废弃传送门").defaultValue(false).build());
    private final Setting<Boolean> findStronghold = sgStructures.add(new BoolSetting.Builder().name("要塞").defaultValue(false).build());
    private final Setting<Boolean> findSwampHut = sgStructures.add(new BoolSetting.Builder().name("沼泽小屋").defaultValue(false).build());
    private final Setting<Boolean> findNetherFortress = sgStructures.add(new BoolSetting.Builder().name("下界要塞").defaultValue(false).build());
    private final Setting<Boolean> findBastionRemnant = sgStructures.add(new BoolSetting.Builder().name("堡垒遗迹").defaultValue(false).build());
    private final Setting<Boolean> findNetherFossil = sgStructures.add(new BoolSetting.Builder().name("下界化石").defaultValue(false).build());
    private final Setting<Boolean> findEndCity = sgStructures.add(new BoolSetting.Builder().name("末地城").defaultValue(false).build());

    private volatile boolean searching;
    private volatile int searchGeneration;

    public FOStructureSearch() {
        super(AddonTemplate.CATEGORY, "FO 结构搜索",
            "按世界种子搜索 18 种原版结构位置，结果输出聊天并同步 Xaero 航点。");
    }

    @Override
    public void onDeactivate() {
        ++searchGeneration;
        searching = false;
    }

    private void startSearch() {
        if (searching) {
            warning("搜索正在进行中。");
            return;
        }
        if (mc.player == null || mc.world == null) {
            error("请先进入世界。");
            return;
        }
        long seed = parseSeed(seedSetting.get());
        int radius = radiusSetting.get();
        int generation = ++searchGeneration;
        BlockPos playerPos = mc.player.getBlockPos();
        int pcx = playerPos.getX() >> 4;
        int pcz = playerPos.getZ() >> 4;
        boolean inNether = mc.world.getRegistryKey() == World.NETHER;
        // 主世界与下界 1:8，按各自维度换算搜索中心
        int owPcx = inNether ? pcx * 8 : pcx;
        int owPcz = inNether ? pcz * 8 : pcz;
        int netherPcx = inNether ? pcx : (int) Math.floor(pcx / 8.0);
        int netherPcz = inNether ? pcz : (int) Math.floor(pcz / 8.0);
        searching = true;
        info("开始搜索结构位置，半径 " + radius + " 区块...");

        CompletableFuture.runAsync(() -> {
            List<Loc> found = new ArrayList<>();
            try {
                MCVersion version = MCVersion.v1_21;
                BiomeSource overworld = BiomeSource.of(Dimension.OVERWORLD, version, seed);
                BiomeSource nether = BiomeSource.of(Dimension.NETHER, version, seed);
                BiomeSource end = BiomeSource.of(Dimension.END, version, seed);
                ChunkRand rand = new ChunkRand();

                if (findVillage.get()) searchRegion(found, new Village(version), new StructurePlacementPure.Config(34, 8, 10387312, false), seed, overworld, rand, owPcx, owPcz, radius, "村庄", generation);
                if (findShipwreck.get()) searchRegion(found, new Shipwreck(version), new StructurePlacementPure.Config(24, 4, 165745295, false), seed, overworld, rand, owPcx, owPcz, radius, "沉船", generation);
                if (findBuriedTreasure.get()) searchChunks(found, new BuriedTreasure(version), seed, overworld, radius, owPcx, owPcz, "埋藏的宝藏", generation, 0);
                if (findOceanRuin.get()) searchRegion(found, new OceanRuin(version), new StructurePlacementPure.Config(20, 8, 14357621, false), seed, overworld, rand, owPcx, owPcz, radius, "海底废墟", generation);
                if (findPillagerOutpost.get()) searchRegion(found, new PillagerOutpost(version), new StructurePlacementPure.Config(32, 8, 165745296, false), seed, overworld, rand, owPcx, owPcz, radius, "掠夺者前哨站", generation);
                if (findDesertPyramid.get()) searchRegion(found, new DesertPyramid(version), new StructurePlacementPure.Config(32, 8, 14357617, false), seed, overworld, rand, owPcx, owPcz, radius, "沙漠神殿", generation);
                if (findIgloo.get()) searchRegion(found, new Igloo(version), new StructurePlacementPure.Config(32, 8, 14357618, false), seed, overworld, rand, owPcx, owPcz, radius, "雪屋", generation);
                if (findJunglePyramid.get()) searchRegion(found, new JunglePyramid(version), new StructurePlacementPure.Config(32, 8, 14357619, false), seed, overworld, rand, owPcx, owPcz, radius, "丛林神庙", generation);
                if (findMansion.get()) searchRegion(found, new Mansion(version), new StructurePlacementPure.Config(80, 20, 10387319, true), seed, overworld, rand, owPcx, owPcz, radius, "林地府邸", generation);
                if (findMonument.get()) searchRegion(found, new Monument(version), new StructurePlacementPure.Config(32, 5, 10387313, true), seed, overworld, rand, owPcx, owPcz, radius, "海底神殿", generation);
                if (findSwampHut.get()) searchRegion(found, new SwampHut(version), new StructurePlacementPure.Config(32, 8, 14357620, false), seed, overworld, rand, owPcx, owPcz, radius, "沼泽小屋", generation);
                if (findRuinedPortal.get()) searchRegion(found, new RuinedPortal(Dimension.OVERWORLD, version), new StructurePlacementPure.Config(40, 15, 34222645, false), seed, overworld, rand, owPcx, owPcz, radius, "废弃传送门", generation);
                if (findMineshaft.get()) searchChunks(found, new Mineshaft(version), seed, overworld, radius, owPcx, owPcz, "废弃矿井", generation, 1);
                if (findStronghold.get()) searchChunks(found, new Stronghold(version), seed, overworld, radius, owPcx, owPcz, "要塞", generation, 2);
                if (findNetherFortress.get()) searchRegion(found, new Fortress(version), new StructurePlacementPure.Config(27, 4, 30084232, false), seed, nether, rand, netherPcx, netherPcz, radius, "下界要塞", generation);
                if (findBastionRemnant.get()) searchRegion(found, new BastionRemnant(version), new StructurePlacementPure.Config(27, 4, 30084232, false), seed, nether, rand, netherPcx, netherPcz, radius, "堡垒遗迹", generation);
                if (findNetherFossil.get()) searchRegion(found, new NetherFossil(version), new StructurePlacementPure.Config(2, 1, 14357921, false), seed, nether, rand, netherPcx, netherPcz, radius, "下界化石", generation);
                if (findEndCity.get()) searchRegion(found, new EndCity(version), new StructurePlacementPure.Config(20, 11, 10387313, true), seed, end, rand, pcx, pcz, radius, "末地城", generation);
            } catch (Throwable t) {
                searching = false;
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                mc.execute(() -> {
                    if (generation == searchGeneration) error("搜索失败: " + msg);
                });
                return;
            }
            searching = false;
            mc.execute(() -> {
                if (generation == searchGeneration && isActive()) showResults(found);
            });
        });
    }

    private void searchRegion(List<Loc> out, RegionStructure<?, ?> structure, StructurePlacementPure.Config config,
                              long seed, BiomeSource biomeSource, ChunkRand rand, int pcx, int pcz, int radius,
                              String name, int generation) {
        int spacing = config.spacing();
        if (spacing <= 0) {
            warning("结构 " + name + " 间距无效: " + spacing);
            return;
        }
        int minRx = (pcx - radius) / spacing;
        int maxRx = (pcx + radius) / spacing;
        int minRz = (pcz - radius) / spacing;
        int maxRz = (pcz + radius) / spacing;
        for (int rx = minRx; rx <= maxRx; rx++) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                if (generation != searchGeneration) {
                    return;
                }
                try {
                    CPos pos = StructurePlacementPure.getInRegion(seed, rx, rz, config);
                    if (pos == null) {
                        continue;
                    }
                    int dx = pos.getX() - pcx;
                    int dz = pos.getZ() - pcz;
                    if (Math.sqrt((double) dx * dx + (double) dz * dz) > radius) {
                        continue;
                    }
                    if (structure.canSpawn(pos, biomeSource)) {
                        out.add(new Loc(pos.getX() * 16, pos.getZ() * 16, name, dx * 16.0, dz * 16.0));
                    }
                } catch (Throwable ignored) {
                    // 单个区域失败不影响整体搜索（对齐 Tanghulu 的处理）
                }
            }
        }
    }

    private void searchChunks(List<Loc> out, Structure structure, long seed, BiomeSource biomeSource,
                              int radius, int pcx, int pcz, String name, int generation, int mode) {
        int minX = Math.max(pcx - radius, Integer.MIN_VALUE / 2);
        int maxX = Math.min(pcx + radius, Integer.MAX_VALUE / 2);
        int minZ = Math.max(pcz - radius, Integer.MIN_VALUE / 2);
        int maxZ = Math.min(pcz + radius, Integer.MAX_VALUE / 2);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (generation != searchGeneration) {
                    return;
                }
                try {
                    CPos pos = new CPos(cx, cz);
                    int dx = cx - pcx;
                    int dz = cz - pcz;
                    if (Math.sqrt((double) dx * dx + (double) dz * dz) > radius) {
                        continue;
                    }
                    boolean hit;
                    if (mode == 0) {
                        hit = StructurePlacementPure.isBuriedTreasureAt(seed, cx, cz, 0);
                    } else if (mode == 1) {
                        hit = StructurePlacementPure.isMineshaftAt(seed, cx, cz, 0);
                    } else {
                        hit = structure.canSpawn(pos, biomeSource);
                    }
                    if (hit && structure.canSpawn(pos, biomeSource)) {
                        out.add(new Loc(cx * 16, cz * 16, name, dx * 16.0, dz * 16.0));
                    }
                } catch (Throwable ignored) {
                    // 单点失败忽略
                }
            }
        }
    }

    private void showResults(List<Loc> locations) {
        if (locations.isEmpty()) {
            warning("在指定范围内未找到任何结构。");
            return;
        }
        locations.sort(Comparator.comparingDouble(l -> l.distance));

        int cap = maxWaypoints.get();
        if (xaeroSync.get() && cap > 0 && XaeroWaypointBridge.isXaeroPresent()) {
            Map<String, Integer> perType = new HashMap<>();
            int count = 0;
            for (Loc l : locations) {
                int used = perType.getOrDefault(l.name, 0);
                if (used >= cap) {
                    continue;
                }
                perType.put(l.name, used + 1);
                String group = l.name + "（FO）";
                if (XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO,
                        l.x, 64, l.z, shortName(l.name) + (used + 1), group, "GOLD")) {
                    ++count;
                }
            }
            if (count > 0) {
                info("已同步 " + count + " 个结构航点到 Xaero 小地图。");
            } else {
                warning("Xaero 航点写入失败，聊天坐标仍可使用。");
            }
        }

        int shown = Math.min(locations.size(), 20);
        for (int i = 0; i < shown; i++) {
            Loc l = locations.get(i);
            info(l.name + "：X=" + l.x + " Z=" + l.z
                + (showDistance.get() ? String.format("，距离 %.0f 米", l.distance) : ""));
        }
        info("搜索完成，共 " + locations.size() + " 个结果；已显示最近 " + shown + " 个。");
    }

    private void onStartButton() {
        btnStart.set(false);
        startSearch();
    }

    private static String shortName(String name) {
        return switch (name) {
            case "埋藏的宝藏" -> "宝藏";
            case "掠夺者前哨站" -> "前哨";
            case "林地府邸" -> "府邸";
            case "废弃传送门" -> "传送门";
            case "废弃矿井" -> "矿井";
            case "堡垒遗迹" -> "堡垒";
            case "下界化石" -> "化石";
            case "海底废墟" -> "废墟";
            default -> name;
        };
    }

    private long parseSeed(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            if (mc.getServer() != null) {
                return mc.getServer().getOverworld().getSeed();
            }
            error("没有种子：请填写世界种子。");
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return raw.trim().hashCode();
        }
    }

    private static final class Loc {
        final int x;
        final int z;
        final String name;
        final double distance;

        Loc(int x, int z, String name, double dx, double dz) {
            this.x = x;
            this.z = z;
            this.name = name;
            this.distance = Math.sqrt(dx * dx + dz * dz);
        }
    }
}
