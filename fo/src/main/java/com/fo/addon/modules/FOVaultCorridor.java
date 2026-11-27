package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.VaultEspLogic;
import com.fo.addon.vault.TunnelDriver;
import com.fo.addon.vault.VaultCorridorPlanLogic;
import com.fo.addon.vault.VaultCorridorStore;
import com.fo.addon.vault.VaultGuardLogic;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.EmptyChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * FO 宝库连廊（7.0p1）：把范围里的不祥宝库按最近的顺序，用 2 高 2 宽的通道一段段串起来。
 *
 * <p>工作方式：只扫已加载区块里的「不祥宝库」（{@code minecraft:vault + ominous=true}）；
 * 选三维最近、未连、未放弃的那个；从玩家当前位置算 L 形路线（最后一段正对宝库），
 * 末端停在保护距离之外；预演默认开，只画路线不挖，关掉预演才开始挖。
 *
 * <p>保护：{@code minecraft:vault}、试炼刷怪笼、各类容器、基岩/屏障永不挖；
 * 宝库周围「保护距离」内按切比雪夫立方体一律不挖（默认 1 格 → 末端离宝库 2 格、墙外 1 格）。
 *
 * <p>p1 范围：同层 + ±16 的 45° 台阶；拐角优化、外围液体封堵、掉落物白名单留到 p2/p3。
 */
public class FOVaultCorridor extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDig = settings.createGroup("挖掘");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("扫描半径")
        .description("搜索宝库的范围（格）。只扫已加载区块，实际范围受服务端渲染距离限制。")
        .defaultValue(500)
        .range(32, 500)
        .sliderRange(32, 500)
        .build());

    private final Setting<Double> rescanInterval = sgGeneral.add(new DoubleSetting.Builder()
        .name("重扫间隔")
        .description("多久重新扫描一遍已加载区块（秒）。")
        .defaultValue(1.0)
        .min(0.2)
        .max(5.0)
        .sliderMin(0.2)
        .sliderMax(5.0)
        .build());

    private final Setting<Integer> protectDistance = sgGeneral.add(new IntSetting.Builder()
        .name("保护距离")
        .description("宝库周围多少格内一律不挖（默认 1 → 通道末端离宝库 2 格，墙外 1 格）。")
        .defaultValue(VaultGuardLogic.DEFAULT_PROTECT_DISTANCE)
        .range(VaultGuardLogic.MIN_PROTECT_DISTANCE, VaultGuardLogic.MAX_PROTECT_DISTANCE)
        .sliderRange(VaultGuardLogic.MIN_PROTECT_DISTANCE, VaultGuardLogic.MAX_PROTECT_DISTANCE)
        .build());

    private final Setting<Integer> maxLevelDiff = sgGeneral.add(new IntSetting.Builder()
        .name("高度差上限")
        .description("允许接的高度差（格），超过就记「暂不支持」换下一个。")
        .defaultValue(VaultCorridorPlanLogic.MAX_LEVEL_DIFF)
        .range(0, 32)
        .sliderRange(0, 32)
        .build());

    private final Setting<Boolean> preview = sgGeneral.add(new BoolSetting.Builder()
        .name("预演")
        .description("只画路线不挖。确认路线没问题后关掉它，模块才开始真正开挖。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> stepMode = sgGeneral.add(new BoolSetting.Builder()
        .name("单步")
        .description("每挖一格停 0.5 秒，方便盯着看。")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> autoWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("自动前进")
        .description("前方清干净后自动往前走。关掉就只能手动跟着走。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> pickPercent = sgDig.add(new IntSetting.Builder()
        .name("镐耐久下限")
        .description("镐剩余耐久低于这个百分比就不挖了，免得挖坏。")
        .defaultValue(20)
        .range(5, 80)
        .sliderRange(5, 80)
        .build());

    private final Setting<Integer> minBlocks = sgDig.add(new IntSetting.Builder()
        .name("最少方块")
        .description("背包里至少要有多少个填充方块才开工（补地板洞、封液体用）。")
        .defaultValue(32)
        .range(0, 256)
        .sliderRange(0, 256)
        .build());

    private final Setting<Integer> minEmptySlots = sgDig.add(new IntSetting.Builder()
        .name("最少空格")
        .description("背包至少留几个空格，不够就不开工（挖出来的圆石会占格）。")
        .defaultValue(4)
        .range(0, 16)
        .sliderRange(0, 16)
        .build());

    private final Setting<String> placeBlocks = sgDig.add(new StringSetting.Builder()
        .name("填充方块")
        .description("允许用来补地板 / 封液体的方块 id，用英文逗号分隔。")
        .defaultValue("minecraft:cobblestone,minecraft:cobbled_deepslate,minecraft:stone,"
            + "minecraft:deepslate,minecraft:andesite,minecraft:diorite,minecraft:granite,minecraft:tuff")
        .build());

    private final Setting<Boolean> stopOnLava = sgDig.add(new BoolSetting.Builder()
        .name("遇到岩浆停")
        .description("p1 遇到岩浆就停并跳过这个宝库，不做封堵。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> stuckTicks = sgDig.add(new IntSetting.Builder()
        .name("卡住判定")
        .description("连续多少 tick 既挖不动也走不动就判定卡住。")
        .defaultValue(100)
        .range(20, 200)
        .sliderRange(20, 200)
        .build());

    private final Setting<Double> reach = sgDig.add(new DoubleSetting.Builder()
        .name("可及距离")
        .description("挖 / 放的最大距离（格），别超过服务器允许的交互距离。")
        .defaultValue(5.0)
        .min(3.0)
        .max(8.0)
        .sliderMin(3.0)
        .sliderMax(8.0)
        .build());

    private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder()
        .name("目标颜色")
        .description("当前正在连的宝库颜色。")
        .defaultValue(new SettingColor(255, 200, 40, 220))
        .build());

    private final Setting<SettingColor> routeColor = sgRender.add(new ColorSetting.Builder()
        .name("路线颜色")
        .description("规划出来的 L 形路线（穿墙可见）。")
        .defaultValue(new SettingColor(80, 220, 255, 230))
        .build());

    private final Setting<SettingColor> connectedColor = sgRender.add(new ColorSetting.Builder()
        .name("已连颜色")
        .description("已经连上的宝库。")
        .defaultValue(new SettingColor(80, 255, 120, 200))
        .build());

    private final Setting<SettingColor> abandonedColor = sgRender.add(new ColorSetting.Builder()
        .name("放弃颜色")
        .description("放弃的宝库（高度差 / 岩浆 / 挖不动）。")
        .defaultValue(new SettingColor(255, 80, 80, 200))
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("方块框的绘制方式。")
        .defaultValue(ShapeMode.Both)
        .build());

    /** 已确认的宝库命中（key = BlockPos.asLong）。 */
    private final Map<Long, VaultEspLogic.Hit> hits = new HashMap<>();
    private final Set<Long> seenInPass = new HashSet<>();
    private final List<long[]> pendingChunks = new ArrayList<>();

    private final TunnelDriver driver = new TunnelDriver();
    private VaultCorridorStore store = new VaultCorridorStore();

    private VaultEspLogic.Hit target;
    private VaultCorridorPlanLogic.Route route;
    private final List<TunnelDriver.Leg> legs = new ArrayList<>();
    private int legIndex;
    private final Map<Long, Integer> failureCounts = new HashMap<>();
    private int retryAtTick;
    private int tickCounter;

    private boolean digging;
    private boolean finishedMessageSent;
    private long noTargetSinceMs;
    private long lastGateNoticeMs;
    private boolean passCompleteOnce;

    private int pendingCursor;
    private long nextPassAtMs;
    private Object lastDimension;

    public FOVaultCorridor() {
        super(AddonTemplate.CATEGORY, "FO 宝库连廊",
            "范围内把不祥宝库按最近顺序用 2 高 2 宽通道串起来；停墙外 1 格、宝库一格不碰；预演默认开。");
    }

    @Override
    public void onActivate() {
        store = VaultCorridorStore.load();
        hits.clear();
        seenInPass.clear();
        pendingChunks.clear();
        pendingCursor = 0;
        nextPassAtMs = 0;
        lastDimension = null;
        target = null;
        route = null;
        legs.clear();
        legIndex = 0;
        digging = false;
        finishedMessageSent = false;
        noTargetSinceMs = 0;
        passCompleteOnce = false;
        driver.stop();
        info("宝库连廊已启动：半径 %d，保护 %d 格；预演%s。", scanRadius.get(), protectDistance.get(),
            preview.get() ? "开（只画不挖）" : "关（直接挖）");
    }

    @Override
    public void onDeactivate() {
        driver.stop();
        store.saveIfDirty();
        digging = false;
    }

    // ==================== tick ====================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        tickCounter++;
        store.saveIfDirty();

        Object dimension = mc.world.getRegistryKey();
        if (!Objects.equals(dimension, lastDimension)) {
            lastDimension = dimension;
            hits.clear();
            seenInPass.clear();
            pendingChunks.clear();
            pendingCursor = 0;
            nextPassAtMs = 0;
            target = null;
            route = null;
            legs.clear();
            driver.stop();
            digging = false;
            passCompleteOnce = false;
        }

        scanTick();

        if (digging) {
            tickDriving();
            return;
        }

        if (target == null) {
            if (passCompleteOnce) {
                selectTarget();
            }
            return;
        }

        if (preview.get()) {
            return;
        }

        if (!gateOk()) return;
        startDigging();
    }

    private void tickDriving() {
        TunnelDriver.Result result = driver.tick();
        if (result == TunnelDriver.Result.ARRIVED) {
            legIndex++;
            if (legIndex >= legs.size()) {
                finishSegment(true, null);
            } else {
                startLeg(legIndex);
            }
        } else if (result == TunnelDriver.Result.FAILED) {
            String reason = driver.failReason();
            finishSegment(false, reason);
        }
    }

    // ==================== 扫描 ====================

    private void scanTick() {
        if (pendingChunks.isEmpty() && System.currentTimeMillis() >= nextPassAtMs) {
            startPass();
        }
        if (pendingChunks.isEmpty()) return;

        long start = System.nanoTime();
        long budget = 2_000_000L;
        int processed = 0;
        while (pendingCursor < pendingChunks.size() && processed < 32) {
            if (processed > 0 && VaultEspLogic.budgetSpent(System.nanoTime() - start, budget)) break;
            long[] chunk = pendingChunks.get(pendingCursor++);
            scanChunk((int) chunk[0], (int) chunk[1]);
            processed++;
        }
        if (pendingCursor >= pendingChunks.size()) {
            pendingChunks.clear();
            pendingCursor = 0;
            hits.keySet().retainAll(seenInPass);
            nextPassAtMs = System.currentTimeMillis() + (long) Math.max(200.0, rescanInterval.get() * 1000.0);
            passCompleteOnce = true;
        }
    }

    private void startPass() {
        seenInPass.clear();
        pendingChunks.clear();
        pendingCursor = 0;

        double radius = scanRadius.get();
        double r2 = radius * radius;
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;
                double centerX = (cx << 4) + 8.0;
                double centerZ = (cz << 4) + 8.0;
                double ddx = centerX - px;
                double ddz = centerZ - pz;
                if (ddx * ddx + ddz * ddz > r2) continue;
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                pendingChunks.add(new long[]{cx, cz});
            }
        }
        if (pendingChunks.isEmpty()) {
            nextPassAtMs = System.currentTimeMillis() + 1000L;
        }
    }

    private void scanChunk(int cx, int cz) {
        Chunk chunk = mc.world.getChunk(cx, cz);
        if (chunk == null || chunk instanceof EmptyChunk) return;

        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = mc.world.getBottomY();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(state -> state.isOf(Blocks.VAULT))) continue;

            int baseY = bottomY + i * 16;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isOminous(state)) continue;
                        BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                        BlockState worldState;
                        try {
                            worldState = mc.world.getBlockState(pos);
                        } catch (RuntimeException e) {
                            continue;
                        }
                        if (!isOminous(worldState)) continue;
                        long key = pos.asLong();
                        hits.put(key, new VaultEspLogic.Hit(pos.getX(), pos.getY(), pos.getZ()));
                        seenInPass.add(key);
                    }
                }
            }
        }
    }

    private static boolean isOminous(BlockState state) {
        return state != null
            && state.isOf(Blocks.VAULT)
            && Boolean.TRUE.equals(state.get(VaultBlock.OMINOUS));
    }

    // ==================== 选目标 / 规划 ====================

    private void selectTarget() {
        if (hits.isEmpty()) {
            idleFinish();
            return;
        }
        String scope = currentScope();
        List<int[]> candidates = new ArrayList<>();
        List<VaultEspLogic.Hit> mapping = new ArrayList<>();
        for (VaultEspLogic.Hit hit : hits.values()) {
            if (store.isConnected(scope, hit.x(), hit.y(), hit.z())) continue;
            if (store.isAbandoned(scope, hit.x(), hit.y(), hit.z())) continue;
            candidates.add(new int[]{hit.x(), hit.y(), hit.z()});
            mapping.add(hit);
        }
        if (candidates.isEmpty()) {
            idleFinish();
            return;
        }

        ClientPlayerEntity p = mc.player;
        int index = VaultCorridorPlanLogic.selectNearest(candidates,
            p.getBlockX(), p.getBlockY(), p.getBlockZ(), scanRadius.get(), Set.of());
        if (index < 0) {
            idleFinish();
            return;
        }
        target = mapping.get(index);
        noTargetSinceMs = 0;
        if (!planSegment()) {
            // 规划失败直接放弃，换下一个
            abandonTarget("规划失败");
        }
    }

    /** 算路线 + 高度方案；失败返回 false。 */
    private boolean planSegment() {
        ClientPlayerEntity p = mc.player;
        int px = p.getBlockX();
        int pz = p.getBlockZ();
        int footY = p.getBlockY();
        int vx = target.x();
        int vy = target.y();
        int vz = target.z();

        VaultCorridorPlanLogic.Vertical vertical = VaultCorridorPlanLogic.vertical(footY, vy);
        if (!vertical.supported() || Math.abs(vy - footY) > maxLevelDiff.get()) {
            info("跳过宝库 %d, %d, %d：高度差 %d 格，超过上限 %d。",
                vx, vy, vz, vy - footY, maxLevelDiff.get());
            return false;
        }

        int[] end = VaultCorridorPlanLogic.endpoint(vx, vz, px, pz, protectDistance.get());
        boolean lastAxisX = Math.abs(vx - px) >= Math.abs(vz - pz);
        int cornerX = lastAxisX ? px : end[0];
        int cornerZ = lastAxisX ? end[1] : pz;
        route = new VaultCorridorPlanLogic.Route(px, pz, cornerX, cornerZ, end[0], end[1]);

        legs.clear();
        int total = route.length();
        int steps = vertical.steps();
        if (lastAxisX) {
            int l1 = Math.abs(cornerZ - pz);
            int s1 = total == 0 ? steps : (int) ((long) steps * l1 / total);
            int leg1EndY = footY + Integer.signum(vy - footY) * s1;
            legs.add(newLeg(px, pz, cornerX, cornerZ, footY, leg1EndY));
            legs.add(newLeg(cornerX, cornerZ, end[0], end[1], leg1EndY, vy));
        } else {
            int l1 = Math.abs(cornerX - px);
            int s1 = total == 0 ? steps : (int) ((long) steps * l1 / total);
            int leg1EndY = footY + Integer.signum(vy - footY) * s1;
            legs.add(newLeg(px, pz, cornerX, cornerZ, footY, leg1EndY));
            legs.add(newLeg(cornerX, cornerZ, end[0], end[1], leg1EndY, vy));
        }

        // 长度为 0 的段直接跳过
        legs.removeIf(leg -> leg.length() == 0 && leg.steps() == 0);
        legIndex = 0;
        info("目标宝库 %d, %d, %d：路线 %d 格，%s，末端 %d, %d。", vx, vy, vz, route.length(),
            vertical.mode() == VaultCorridorPlanLogic.VerticalMode.FLAT ? "同层平走" : "台阶 " + vertical.deltaY(),
            end[0], end[1]);
        return true;
    }

    private TunnelDriver.Leg newLeg(int fromX, int fromZ, int toX, int toZ, int startFootY, int endFootY) {
        int dx = toX - fromX;
        int dz = toZ - fromZ;
        VaultCorridorPlanLogic.Axis axis = Math.abs(dx) >= Math.abs(dz)
            ? VaultCorridorPlanLogic.Axis.X : VaultCorridorPlanLogic.Axis.Z;
        int fixed = axis == VaultCorridorPlanLogic.Axis.X ? fromZ : fromX;
        int from = axis == VaultCorridorPlanLogic.Axis.X ? fromX : fromZ;
        int to = axis == VaultCorridorPlanLogic.Axis.X ? toX : toZ;
        int dir = Integer.compare(to, from);
        return new TunnelDriver.Leg(axis, dir == 0 ? 1 : dir, fixed, from, to, startFootY, endFootY);
    }

    // ==================== 开工 / 结束 ====================

    private boolean gateOk() {
        ClientPlayerEntity p = mc.player;
        int worn = 0;
        int blocks = 0;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) {
                empty++;
                continue;
            }
            if (s.isIn(ItemTags.PICKAXES)) {
                int max = s.getMaxDamage();
                if (max > 0 && (long) (max - s.getDamage()) * 100 >= (long) max * pickPercent.get()) worn++;
            }
            if (isPlaceBlock(s)) blocks += s.getCount();
        }
        long now = System.currentTimeMillis();
        if (blocks < minBlocks.get()) {
            if (now - lastGateNoticeMs > 4000) {
                lastGateNoticeMs = now;
                info("还没开工：填充方块 %d 个，少于设置的 %d 个。", blocks, minBlocks.get());
            }
            return false;
        }
        if (empty < minEmptySlots.get()) {
            if (now - lastGateNoticeMs > 4000) {
                lastGateNoticeMs = now;
                info("还没开工：空格 %d 个，少于设置的 %d 个。", empty, minEmptySlots.get());
            }
            return false;
        }
        if (worn == 0) {
            if (now - lastGateNoticeMs > 4000) {
                lastGateNoticeMs = now;
                info("还没开工：没有耐久高于 %d%% 的镐。", pickPercent.get());
            }
            return false;
        }
        return true;
    }

    private void startDigging() {
        if (legs.isEmpty()) {
            if (route != null && route.length() == 0) {
                finishSegment(true, null);
            } else {
                abandonTarget("没有可走的段");
            }
            return;
        }
        digging = true;
        legIndex = 0;
        startLeg(0);
    }

    private void startLeg(int index) {
        if (index >= legs.size()) {
            finishSegment(true, null);
            return;
        }
        TunnelDriver.Leg leg = legs.get(index);
        List<int[]> vaults = new ArrayList<>();
        for (VaultEspLogic.Hit hit : hits.values()) {
            vaults.add(new int[]{hit.x(), hit.y(), hit.z()});
        }
        driver.start(leg, new TunnelDriver.Options(reach.get(), protectDistance.get(), autoWalk.get(),
            stepMode.get(), stopOnLava.get(), minBlocks.get(), pickPercent.get(), placeIdSet(), stuckTicks.get(),
            vaults), target.x(), target.y(), target.z());
        info("开始第 %d/%d 段：%s %d 格，高度 %d → %d。", index + 1, legs.size(),
            leg.axis() == VaultCorridorPlanLogic.Axis.X ? "X 轴" : "Z 轴", leg.length(), leg.footY(), leg.endFootY());
    }

    private void finishSegment(boolean arrived, String reason) {
        digging = false;
        driver.stop();
        if (arrived) {
            String scope = currentScope();
            store.markConnected(scope, target.x(), target.y(), target.z());
            store.saveIfDirty();
            info("连上了宝库 %d, %d, %d（本作用域已连 %d 个）。",
                target.x(), target.y(), target.z(), store.connectedCount(scope));
            target = null;
            route = null;
            legs.clear();
            return;
        }

        long key = BlockPos.asLong(target.x(), target.y(), target.z());
        int fails = failureCounts.getOrDefault(key, 0) + 1;
        failureCounts.put(key, fails);
        info("这一段失败（第 %d 次）：%s", fails, reason);
        if (fails >= 3) {
            failureCounts.remove(key);
            abandonTarget(reason);
        } else {
            target = null;
            route = null;
            legs.clear();
        }
    }

    private void abandonTarget(String reason) {
        if (target != null) {
            String scope = currentScope();
            store.markAbandoned(scope, target.x(), target.y(), target.z());
            store.saveIfDirty();
            info("放弃宝库 %d, %d, %d：%s。换下一个。",
                target.x(), target.y(), target.z(), reason);
        }
        target = null;
        route = null;
        legs.clear();
        digging = false;
    }

    private void idleFinish() {
        if (finishedMessageSent) return;
        long now = System.currentTimeMillis();
        if (noTargetSinceMs == 0) {
            noTargetSinceMs = now;
            return;
        }
        if (now - noTargetSinceMs < 30_000L) return;
        finishedMessageSent = true;
        String scope = currentScope();
        info("收工：范围内能连的宝库都串完了（已连 %d 个，放弃 %d 个）。",
            store.connectedCount(scope), store.abandonedCount(scope));
    }

    // ==================== 渲染 ====================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        String scope = currentScope();

        for (VaultEspLogic.Hit hit : hits.values()) {
            BlockPos pos = new BlockPos(hit.x(), hit.y(), hit.z());
            SettingColor color;
            if (store.isConnected(scope, hit.x(), hit.y(), hit.z())) {
                color = connectedColor.get();
            } else if (store.isAbandoned(scope, hit.x(), hit.y(), hit.z())) {
                color = abandonedColor.get();
            } else if (target != null && hit.x() == target.x() && hit.y() == target.y() && hit.z() == target.z()) {
                color = targetColor.get();
            } else {
                continue;
            }
            event.renderer.box(pos, color, color, shapeMode.get(), 0);
        }

        if (route == null || target == null) return;
        SettingColor color = routeColor.get();
        int total = route.length();
        int steps = VaultCorridorPlanLogic.vertical(mc.player.getBlockY(), target.y()).steps();
        int startY = mc.player.getBlockY();
        int sign = Integer.signum(target.y() - startY);
        double y1 = startY + 0.5;
        int firstLeg = Math.abs(route.cornerX() - route.startX()) + Math.abs(route.cornerZ() - route.startZ());
        double yCorner = startY + sign * (int) ((long) steps * firstLeg / Math.max(1, total)) + 0.5;
        double y2 = target.y() + 0.5;

        event.renderer.line(route.startX() + 0.5, y1, route.startZ() + 0.5,
            route.cornerX() + 0.5, yCorner, route.cornerZ() + 0.5, color);
        event.renderer.line(route.cornerX() + 0.5, yCorner, route.cornerZ() + 0.5,
            route.endX() + 0.5, y2, route.endZ() + 0.5, color);
    }

    // ==================== 小工具 ====================

    private String currentScope() {
        if (mc.world == null) return null;
        String server = mc.getCurrentServerEntry() != null
            ? mc.getCurrentServerEntry().address : "单人";
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    private Set<String> placeIdSet() {
        Set<String> set = new LinkedHashSet<>();
        for (String part : placeBlocks.get().split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) set.add(id);
        }
        return set;
    }

    private boolean isPlaceBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.item.BlockItem bi)) return false;
        return placeIdSet().contains(net.minecraft.registry.Registries.BLOCK.getId(bi.getBlock()).toString());
    }

    /** 设置页显示不下的统计，直接聊天栏报一次。 */
    public String statusLine() {
        if (target == null) return "待机";
        return "目标 " + target.x() + "," + target.y() + "," + target.z() + "｜" + driver.status();
    }
}
