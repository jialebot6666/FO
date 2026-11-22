package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.ancient.core.XaeroWaypointBridge;
import com.fo.addon.utils.MushroomBiomeSampler;
import com.fo.addon.utils.MushroomClusterLogic;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * FO 蘑菇岛定位。
 *
 * <p>移植自 Tanghulu（https://github.com/Tanghulu2019-a/Tanghulu）的“蘑菇岛定位”（GPLv3）。
 * 原实现为每个版本下载 Mojang 官方 jar、开独立进程预测（要求 Java 25）；
 * 本移植改为进程内 {@link MushroomBiomeSampler} 采样，纯 Java、零下载。
 */
public class FOMushroomFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Precision {
        FAST("快速（64 格）", 64),
        BALANCED("均衡（32 格）", 32),
        FINE("精细（8 格）", 8);

        private final String label;
        private final int step;

        Precision(String label, int step) {
            this.label = label;
            this.step = step;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Setting<String> seedSetting = sgGeneral.add(new StringSetting.Builder()
        .name("种子")
        .description("世界种子；留空时读单人存档种子。")
        .defaultValue("-7346913998703726680")
        .build());

    private final Setting<Integer> radiusSetting = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径")
        .description("以玩家为中心的搜索半径（方块）。")
        .defaultValue(16000)
        .min(320).max(100000)
        .sliderMin(320).sliderMax(40000)
        .build());

    private final Setting<Precision> precision = sgGeneral.add(new EnumSetting.Builder<Precision>()
        .name("搜索精细度")
        .description("采样间隔。间隔越小越容易发现小型/狭长岛屿，但耗时更长。")
        .defaultValue(Precision.BALANCED)
        .build());

    private final Setting<Boolean> xaeroSync = sgGeneral.add(new BoolSetting.Builder()
        .name("同步Xaero航点")
        .description("把每处蘑菇岛写入 Xaero 主世界航点；聊天同时给出下界对应点。")
        .defaultValue(true)
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

    private volatile boolean searching;
    private volatile int searchGeneration;

    public FOMushroomFinder() {
        super(AddonTemplate.CATEGORY, "FO 蘑菇岛定位",
            "按世界种子搜索蘑菇岛群系，结果输出聊天并同步 Xaero 主世界航点。");
    }

    @Override
    public void onDeactivate() {
        ++searchGeneration;
        searching = false;
    }

    private void onStartButton() {
        btnStart.set(false);
        startSearch();
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
        int step = precision.get().step;
        int generation = ++searchGeneration;

        int px = (int) Math.floor(mc.player.getX());
        int pz = (int) Math.floor(mc.player.getZ());
        boolean inNether = mc.world.getRegistryKey() == World.NETHER;
        int cx = inNether ? px * 8 : px;
        int cz = inNether ? pz * 8 : pz;

        searching = true;
        info("开始搜索蘑菇岛，半径 " + radius + " 格，精细度 " + precision.get() + "...");
        if (inNether) {
            info("检测到下界启动，已把中心换算为主世界坐标 (" + cx + "," + cz + ")。");
        }

        int baseX = cx - radius;
        int baseZ = cz - radius;

        CompletableFuture.runAsync(() -> {
            List<MushroomClusterLogic.Point> found;
            try {
                MushroomBiomeSampler sampler = new MushroomBiomeSampler(seed);
                Set<Long> hits = new HashSet<>();
                int lastProgress = 0;
                for (int x = baseX; x <= cx + radius; x += step) {
                    int progress = (int) ((long) (x - baseX) * 100 / (2L * radius));
                    if (progress >= lastProgress + 10) {
                        lastProgress = progress;
                        final int p = progress;
                        mc.execute(() -> {
                            if (generation == searchGeneration) info("搜索进度 " + p + "%");
                        });
                    }
                    int ix = (x - baseX) / step;
                    for (int z = baseZ; z <= cz + radius; z += step) {
                        if (generation != searchGeneration) {
                            searching = false;
                            return;
                        }
                        if (sampler.isMushroomAt(x, z)) {
                            hits.add(MushroomClusterLogic.key(ix, (z - baseZ) / step));
                        }
                    }
                }
                found = MushroomClusterLogic.cluster(hits, baseX, baseZ, step);
            } catch (Throwable t) {
                searching = false;
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                mc.execute(() -> {
                    if (generation == searchGeneration) error("搜索失败: " + msg);
                });
                return;
            }
            searching = false;
            final List<MushroomClusterLogic.Point> result = found;
            mc.execute(() -> {
                if (generation == searchGeneration && isActive()) showResults(result);
            });
        });
    }

    private void showResults(List<MushroomClusterLogic.Point> points) {
        if (points.isEmpty()) {
            warning("该范围内没有发现蘑菇岛。");
            return;
        }
        if (xaeroSync.get() && XaeroWaypointBridge.isXaeroPresent()) {
            int count = 0;
            for (int i = 0; i < points.size(); i++) {
                MushroomClusterLogic.Point p = points.get(i);
                if (XaeroWaypointBridge.addWaypoint(XaeroWaypointBridge.Preference.AUTO,
                        p.x(), 64, p.z(), "蘑菇岛 #" + (i + 1), "蘑菇岛（FO）", "GOLD")) {
                    ++count;
                }
            }
            info("已同步 " + count + " 个蘑菇岛航点。");
        }
        int shown = Math.min(points.size(), 20);
        for (int i = 0; i < shown; i++) {
            MushroomClusterLogic.Point p = points.get(i);
            int nx = Math.floorDiv(p.x(), 8);
            int nz = Math.floorDiv(p.z(), 8);
            info("蘑菇岛 #" + (i + 1) + "：主世界 X=" + p.x() + " Z=" + p.z()
                + "；下界对应 X=" + nx + " Z=" + nz);
        }
        info("搜索完成，共发现 " + points.size() + " 处蘑菇岛；已显示前 " + shown + " 处。");
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


}
