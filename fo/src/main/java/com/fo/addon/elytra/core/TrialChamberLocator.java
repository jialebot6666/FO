package com.fo.addon.elytra.core;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapDecorationsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
public final class TrialChamberLocator {
    private static final Identifier TRIAL_CHAMBERS = Identifier.ofVanilla("trial_chambers");
    private static final TagKey<Structure> ON_TRIAL_CHAMBERS_MAPS =
        TagKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("on_trial_chambers_maps"));
    private static final String MULTIPLAYER_REASON =
        "多人服务器拿不到世界种子，请用『手动坐标』或『读地图』";
    private static final int DEFAULT_RADIUS_CHUNKS = 100;
    private static final int MAX_RADIUS_CHUNKS = 200;
    private static final int TRIAL_CHAMBERS_SPACING = 34;
    private static final int TRIAL_CHAMBERS_SEPARATION = 12;
    private static final int TRIAL_CHAMBERS_SALT = 94251327;
    private static final int SEED_MIN_REGIONS = 1;
    private static final int SEED_MAX_REGIONS = 8;
    private volatile String lastFailReason = "";
    private final AtomicReference<BlockPos> pendingHit = new AtomicReference<>();
    private volatile String pendingNote = "";
    private volatile boolean searching;
    public TrialChamberLocator() {
    }
    public enum Source {
        NONE("无"),
        SEED("种子推算"),
        MANUAL("手填坐标"),
        MAP("藏宝图");

        public final String label;

        Source(String label) {
            this.label = label;
        }

        /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString，必须覆写否则显示英文枚举名） */
        @Override
        public String toString() {
            return label;
        }
    }
    public record Target(int x, int z, Source source, String note) {
    }
    public static boolean seedSearchAvailable() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.getServer() != null;
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.seedSearchAvailable", t);
            return false;
        }
    }
    public Target locateBySeed(int fromX, int fromZ, int radiusChunks) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) {
                return fail("客户端还没初始化，拿不到整合服务端（单机世界生成器）");
            }
            IntegratedServer server = mc.getServer();
            if (server == null) {
                return fail(MULTIPLAYER_REASON + "（也可以填世界种子，用『手填种子推算』）");
            }
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) {
                return fail("整合服务端还没加载主世界（正在进入世界？），稍后再试");
            }
            if (searching) {
                return fail("正在后台搜索（上一次还没结束）：等 isSearching() 变 false，再用 pollSeedSearch() 取结果");
            }
            Clamped radiusInfo = resolveRadius(radiusChunks);
            pendingHit.set(null);
            pendingNote = "";
            searching = true;
            try {
                server.execute(() -> runSeedSearch(server, fromX, fromZ, radiusInfo.value(), radiusInfo.notice()));
            } catch (Throwable t) {
                searching = false;
                FOElytraLog.detailError("TrialChamberLocator.locateBySeed/dispatch", t);
                return fail(withNotice("把种子搜索交给整合服务端线程时出错：" + t, radiusInfo.notice()));
            }
            FOElytraLog.detail("已把种子搜索交给整合服务端线程：起点=(%d,0,%d) 半径=%d 区块",
                fromX, fromZ, radiusInfo.value());
            return fail(withNotice(String.format(Locale.ROOT,
                "正在后台搜索（已交给整合服务端线程，客户端不会被卡住）：起点 (%d, %d)，半径 %d 区块 ≈ %d 格；"
                    + "每 tick 看 isSearching()，搜索结束后用 pollSeedSearch() 取结果",
                fromX, fromZ, radiusInfo.value(), radiusInfo.value() * 16), radiusInfo.notice()));
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.locateBySeed", t);
            return fail("发起后台种子搜索时出错：" + t + "（可改用手动坐标或读地图）");
        }
    }
    public boolean isSearching() {
        return searching;
    }
    public Target pollSeedSearch() {
        try {
            BlockPos hit = pendingHit.getAndSet(null);
            if (hit == null) {
                return null;
            }
            String note = pendingNote;
            lastFailReason = "";
            FOElytraLog.detail("取到后台种子搜索结果：(%d, %d)", hit.getX(), hit.getZ());
            return new Target(hit.getX(), hit.getZ(), Source.SEED,
                note == null || note.isEmpty() ? "单机世界生成器后台定位：搜索完成" : note);
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.pollSeedSearch", t);
            return fail("取后台种子搜索结果时出错：" + t);
        }
    }
    private void runSeedSearch(IntegratedServer server, int fromX, int fromZ, int radius, String radiusNotice) {
        try {
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) {
                fail(withNotice("后台搜索开始时整合服务端还没加载主世界（正在进出世界？），稍后用同一个半径重试",
                    radiusNotice));
                return;
            }
            BlockPos from = new BlockPos(fromX, 0, fromZ);
            BlockPos hit = null;
            String route = "";
            try {
                hit = overworld.locateStructure(ON_TRIAL_CHAMBERS_MAPS, from, radius, false);
                route = "结构标签 #minecraft:on_trial_chambers_maps";
            } catch (Throwable t) {
                FOElytraLog.detailError("TrialChamberLocator.runSeedSearch/tag", t);
                FOElytraLog.detail("标签路线失败（%s），改走注册项直连路线", t);
            }
            if (hit == null) {
                try {
                    RegistryEntryLookup<Structure> lookup =
                        overworld.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                    RegistryEntry<Structure> entry =
                        lookup.getOptional(RegistryKey.of(RegistryKeys.STRUCTURE, TRIAL_CHAMBERS)).orElse(null);
                    if (entry != null) {
                        RegistryEntryList<Structure> list = RegistryEntryList.of(List.of(entry));
                        ChunkGenerator generator = overworld.getChunkManager().getChunkGenerator();
                        Pair<BlockPos, RegistryEntry<Structure>> found =
                            generator.locateStructure(overworld, list, from, radius, false);
                        if (found != null) {
                            hit = found.getFirst();
                            route = "注册项直连 minecraft:trial_chambers";
                        }
                    }
                } catch (Throwable t) {
                    FOElytraLog.detailError("TrialChamberLocator.runSeedSearch/entry", t);
                    FOElytraLog.detail("注册项直连路线也失败：%s", t);
                }
            }
            if (hit == null) {
                fail(withNotice(diagnoseNoHit(overworld, radius), radiusNotice));
                return;
            }
            lastFailReason = "";
            FOElytraLog.detail("试炼密室（种子/生成器）后台定位成功：(%d, %d) 路线=%s 起点=(%d,%d) 半径=%d 区块",
                hit.getX(), hit.getZ(), route, fromX, fromZ, radius);
            pendingNote = withNotice(String.format(Locale.ROOT,
                "单机世界生成器后台定位（%s）：搜索半径 %d 区块 ≈ %d 格", route, radius, radius * 16),
                radiusNotice);
            pendingHit.set(hit);
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.runSeedSearch", t);
            fail(withNotice("后台定位时出错：" + t + "（可改用手动坐标或读地图）", radiusNotice));
        } finally {
            searching = false;
        }
    }
    public Target locateBySeedValue(long seed, int fromX, int fromZ, int maxRegions) {
        try {
            List<Target> all = locateBySeedValues(seed, fromX, fromZ, maxRegions);
            if (all.isEmpty()) {
                if (failReason().isEmpty()) {
                    return fail("按手填种子推算没有算出任何候选点（圈数被夹到 " + SEED_MAX_REGIONS + " 也不该为空）");
                }
                return null;
            }
            return all.get(0);
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.locateBySeedValue", t);
            return fail("按手填种子定位时出错：" + t);
        }
    }
    public List<Target> locateBySeedValues(long seed, int fromX, int fromZ, int maxRegions) {
        try {
            Clamped ringsInfo = resolveRegionCount(maxRegions);
            int rings = ringsInfo.value();
            int centerRegionX = Math.floorDiv(fromX >> 4, TRIAL_CHAMBERS_SPACING);
            int centerRegionZ = Math.floorDiv(fromZ >> 4, TRIAL_CHAMBERS_SPACING);
            int side = 2 * rings + 1;
            List<Candidate> candidates = new ArrayList<>(side * side);
            for (int regionX = centerRegionX - rings; regionX <= centerRegionX + rings; regionX++) {
                for (int regionZ = centerRegionZ - rings; regionZ <= centerRegionZ + rings; regionZ++) {
                    ChunkPos startChunk = computeSeedStartChunk(seed, regionX, regionZ);
                    int x = startChunk.getStartX() + 8;
                    int z = startChunk.getStartZ() + 8;
                    long dx = (long) x - fromX;
                    long dz = (long) z - fromZ;
                    candidates.add(new Candidate(startChunk.getStartX() >> 4, startChunk.getStartZ() >> 4,
                        regionX, regionZ, x, z, dx * dx + dz * dz));
                }
            }
            Comparator<Candidate> byDistance = Comparator.comparingLong(Candidate::distanceSq)
                .thenComparingInt(Candidate::x)
                .thenComparingInt(Candidate::z);
            candidates.sort(byDistance);
            if (candidates.isEmpty()) {
                return List.of();
            }
            List<Target> out = new ArrayList<>(candidates.size());
            for (Candidate c : candidates) {
                out.add(new Target(c.x(), c.z(), Source.SEED,
                    seedNote(c, seed, rings, ringsInfo.notice())));
            }
            lastFailReason = "";
            Candidate nearest = candidates.get(0);
            FOElytraLog.detail("手填种子推算：seed=%d 起点=(%d,%d) 圈数=%d 候选=%d 最近=(%d,%d) 区块(%d,%d) region(%d,%d)",
                seed, fromX, fromZ, rings, out.size(), nearest.x(), nearest.z(),
                nearest.startChunkX(), nearest.startChunkZ(), nearest.regionX(), nearest.regionZ());
            return out;
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.locateBySeedValues", t);
            fail("按手填种子推算结构位置时出错：" + t);
            return List.of();
        }
    }
    public static ChunkPos computeSeedStartChunk(long seed, int regionX, int regionZ) {
        int span = TRIAL_CHAMBERS_SPACING - TRIAL_CHAMBERS_SEPARATION;
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        random.setRegionSeed(seed, regionX, regionZ, TRIAL_CHAMBERS_SALT);
        int dx = random.nextInt(span);
        int dz = random.nextInt(span);
        return new ChunkPos(regionX * TRIAL_CHAMBERS_SPACING + dx, regionZ * TRIAL_CHAMBERS_SPACING + dz);
    }
    private record Candidate(int startChunkX, int startChunkZ, int regionX, int regionZ,
                             int x, int z, long distanceSq) {
    }
    private static String seedNote(Candidate c, long seed, int rings, String notice) {
        return withNotice(String.format(Locale.ROOT,
            "手填种子推算（seed=%d）：起始区块 (%d, %d) → 区块中心 (%d, %d)；region 下标 (%d, %d)；"
                + "搜索 %d 圈；参数 spacing=%d separation=%d salt=%d"
                + "（来源：本机客户端 jar 的 trial_chambers 结构集 JSON，版本升级后要重新核对）；"
                + "注意这只是 random_spread 推算出的候选起始区块，真实生成还要过生物群系等条件，"
                + "可能压根没生成结构（建议到了附近再按单机种子搜索或看地图确认）",
            seed, c.startChunkX(), c.startChunkZ(), c.x(), c.z(), c.regionX(), c.regionZ(), rings,
            TRIAL_CHAMBERS_SPACING, TRIAL_CHAMBERS_SEPARATION, TRIAL_CHAMBERS_SALT), notice);
    }
    public static Target manual(int x, int z) {
        return new Target(x, z, Source.MANUAL,
            "手动坐标（未验证真实结构位置）：多人服务器没有世界种子，只能靠手动坐标或『读地图』");
    }
    public Target readMapTarget() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                return fail("还没进世界（拿不到背包），『读地图』要进世界之后才能用");
            }
            PlayerInventory inventory = mc.player.getInventory();
            if (inventory == null) {
                return fail("拿不到玩家背包，读不了地图");
            }
            int size = inventory.size();
            String trialNamedButUnreadable = null;
            for (int i = 0; i < size; i++) {
                ItemStack stack;
                try {
                    stack = inventory.getStack(i);
                } catch (Throwable t) {
                    FOElytraLog.detailError("TrialChamberLocator.readMapTarget(slot " + i + ")", t);
                    continue;
                }
                if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) continue;
                String name = stackName(stack);
                boolean nameLooksTrial = looksLikeTrialMap(name);
                MapDecorationsComponent decorations;
                try {
                    decorations = stack.get(DataComponentTypes.MAP_DECORATIONS);
                } catch (Throwable t) {
                    FOElytraLog.detailError("TrialChamberLocator.readMapTarget(component)", t);
                    decorations = null;
                }
                Map<String, MapDecorationsComponent.Decoration> marks =
                    decorations == null ? null : decorations.decorations();
                if (marks == null || marks.isEmpty()) {
                    if (nameLooksTrial && trialNamedButUnreadable == null) {
                        trialNamedButUnreadable = name;
                    }
                    continue;
                }
                MapDecorationsComponent.Decoration trialDecoration = null;
                MapDecorationsComponent.Decoration firstDecoration = null;
                for (MapDecorationsComponent.Decoration decoration : marks.values()) {
                    if (decoration == null) continue;
                    if (firstDecoration == null) firstDecoration = decoration;
                    if (isTrialChambersDecoration(decoration)) {
                        trialDecoration = decoration;
                        break;
                    }
                }
                MapDecorationsComponent.Decoration chosen = trialDecoration;
                String how = "标记类型 = minecraft:trial_chambers";
                if (chosen == null) {
                    if (nameLooksTrial && marks.size() == 1) {
                        chosen = firstDecoration;
                        how = "标记类型未识别，按地图名字判定";
                    }
                }
                if (chosen == null) {
                    if (nameLooksTrial && trialNamedButUnreadable == null) trialNamedButUnreadable = name;
                    continue;
                }
                int x = (int) Math.round(chosen.x());
                int z = (int) Math.round(chosen.z());
                if (x == 0 && z == 0) {
                    if (nameLooksTrial && trialNamedButUnreadable == null) trialNamedButUnreadable = name;
                    continue;
                }
                lastFailReason = "";
                FOElytraLog.detail("读地图成功：slot=%d 名字=%s 坐标=(%d,%d)（%s）", i, name, x, z, how);
                return new Target(x, z, Source.MAP,
                    String.format(Locale.ROOT, "读地图「%s」的目标标记：(%d, %d)，%s", name, x, z, how));
            }
            if (trialNamedButUnreadable != null) {
                String detail = mapStateHint(mc);
                return fail("找到了「" + trialNamedButUnreadable + "」但读不到地图装饰点"
                    + "（这个客户端版本读不到地图装饰点，或地图数据还没同步）" + detail);
            }
            return fail("背包里没有「埋藏的试炼密室地图」（要放在身上，不能留在箱子里）");
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.readMapTarget", t);
            return fail("读地图时出错：" + t);
        }
    }
    public String failReason() {
        String reason = lastFailReason;
        return reason == null ? "" : reason;
    }
    private Target fail(String reason) {
        lastFailReason = reason == null ? "" : reason;
        FOElytraLog.detail("试炼密室定位失败：%s", lastFailReason);
        return null;
    }
    private static String diagnoseNoHit(ServerWorld overworld, int radius) {
        try {
            boolean structuresEnabled = overworld.getServer()
                .getSaveProperties()
                .getGeneratorOptions()
                .shouldGenerateStructures();
            if (!structuresEnabled) {
                return "这个世界在创建时关掉了「生成结构」（GeneratorOptions#shouldGenerateStructures=false），"
                    + "世界生成器根本不会放试炼密室，换起点或加大半径都没用";
            }
            RegistryEntryLookup<Structure> lookup =
                overworld.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            if (lookup.getOptional(ON_TRIAL_CHAMBERS_MAPS).isEmpty()) {
                return "当前数据包没有结构标签 #minecraft:on_trial_chambers_maps（被数据包删了/改了？），"
                    + "标签为空时结构搜索只会静默返回 null";
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.diagnoseNoHit", t);
        }
        return String.format(Locale.ROOT,
            "半径 %d 区块（≈%d 格）内没找到试炼密室：换个起点，或把半径调大再试（越远越慢）",
            radius, radius * 16);
    }
    private record Clamped(int value, String notice) {
    }
    private static Clamped resolveRadius(int radiusChunks) {
        if (radiusChunks <= 0) {
            String notice = String.format(Locale.ROOT,
                "半径 %d 无效（<=0），已按默认值 %d 区块（≈%d 格）搜索",
                radiusChunks, DEFAULT_RADIUS_CHUNKS, DEFAULT_RADIUS_CHUNKS * 16);
            FOElytraLog.warn("试炼密室定位：%s", notice);
            FOElytraLog.detail("半径夹取：请求 %d → 实际 %d（<=0 走默认值）", radiusChunks, DEFAULT_RADIUS_CHUNKS);
            return new Clamped(DEFAULT_RADIUS_CHUNKS, notice);
        }
        if (radiusChunks > MAX_RADIUS_CHUNKS) {
            String notice = String.format(Locale.ROOT,
                "半径 %d 区块超过上限，已夹到 %d 区块（≈%d 格）",
                radiusChunks, MAX_RADIUS_CHUNKS, MAX_RADIUS_CHUNKS * 16);
            FOElytraLog.warn("试炼密室定位：%s", notice);
            FOElytraLog.detail("半径夹取：请求 %d → 实际 %d（上限 %d）",
                radiusChunks, MAX_RADIUS_CHUNKS, MAX_RADIUS_CHUNKS);
            return new Clamped(MAX_RADIUS_CHUNKS, notice);
        }
        return new Clamped(radiusChunks, "");
    }
    private static Clamped resolveRegionCount(int maxRegions) {
        if (maxRegions < SEED_MIN_REGIONS) {
            String notice = String.format(Locale.ROOT,
                "圈数 %d 无效（< %d），已按 %d 圈搜索（每圈 %d 区块）",
                maxRegions, SEED_MIN_REGIONS, SEED_MIN_REGIONS, TRIAL_CHAMBERS_SPACING);
            FOElytraLog.warn("试炼密室种子推算：%s", notice);
            FOElytraLog.detail("圈数夹取：请求 %d → 实际 %d（下限 %d）", maxRegions, SEED_MIN_REGIONS, SEED_MIN_REGIONS);
            return new Clamped(SEED_MIN_REGIONS, notice);
        }
        if (maxRegions > SEED_MAX_REGIONS) {
            String notice = String.format(Locale.ROOT,
                "圈数 %d 超过上限，已夹到 %d 圈（每圈 %d 区块，最多 %d 个候选点）",
                maxRegions, SEED_MAX_REGIONS, TRIAL_CHAMBERS_SPACING, (2 * SEED_MAX_REGIONS + 1) * (2 * SEED_MAX_REGIONS + 1));
            FOElytraLog.warn("试炼密室种子推算：%s", notice);
            FOElytraLog.detail("圈数夹取：请求 %d → 实际 %d（上限 %d）", maxRegions, SEED_MAX_REGIONS, SEED_MAX_REGIONS);
            return new Clamped(SEED_MAX_REGIONS, notice);
        }
        return new Clamped(maxRegions, "");
    }
    private static String withNotice(String text, String notice) {
        if (notice == null || notice.isEmpty()) return text;
        return text + "；" + notice;
    }
    private static String stackName(ItemStack stack) {
        try {
            return stack.getName().getString();
        } catch (Throwable t) {
            return "";
        }
    }
    private static boolean looksLikeTrialMap(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains("试炼")) return true;
        return name.toLowerCase(Locale.ROOT).contains("trial");
    }
    private static boolean isTrialChambersDecoration(MapDecorationsComponent.Decoration decoration) {
        try {
            RegistryEntry<MapDecorationType> type = decoration.type();
            if (type == null) return false;
            if (type == MapDecorationTypes.TRIAL_CHAMBERS) return true;
            return type.matchesId(TRIAL_CHAMBERS);
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.isTrialChambersDecoration", t);
            return false;
        }
    }
    private static String mapStateHint(MinecraftClient mc) {
        try {
            World world = mc.world;
            if (world == null || mc.player == null) return "";
            PlayerInventory inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) continue;
                MapState state = FilledMapItem.getMapState(stack, world);
                if (state != null && state.hasExplorationMapDecoration()) {
                    return "；地图 state 里只有像素坐标（客户端 centerX/centerZ 恒为 0），还原不出世界坐标，请改用手动坐标";
                }
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("TrialChamberLocator.mapStateHint", t);
        }
        return "";
    }
}
