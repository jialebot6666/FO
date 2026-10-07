package com.fo.addon.elytra.core;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.enums.VaultState;
import net.minecraft.block.vault.VaultConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
public final class VaultOpener {
    public record Options(
        int searchRadius, double openDistance, int openWaitTicks, int maxVaultsPerRun,
        boolean needOminous,
        List<Item> targetItems,
        List<Identifier> targetEnchantments,
        boolean stopOnTarget, boolean drinkOminousBottle, boolean useBaritoneWalk, int actionDelay,
        Predicate<BlockPos> candidateFilter,
        Consumer<BlockPos> onOpened) {}
    public enum State {
        IDLE("空闲"),
        SCAN("扫描宝库"),
        EQUIP("换钥匙"),
        WALK("走近"),
        OPEN("开库"),
        COLLECT("收取战利品"),
        NEXT("下一个"),
        DRINK("喝药水"),
        FINISH("收尾"),
        DONE("完成"),
        FAILED("失败");

        public final String label;

        State(String label) {
            this.label = label;
        }

        /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString，必须覆写否则显示英文枚举名） */
        @Override
        public String toString() {
            return label;
        }
    }
    private static final int WALK_TIMEOUT_TICKS = 600;
    private static final int WALK_REPATH_TICKS = 100;
    private static final int COLLECT_MIN_TICKS = 90;
    private static final int SPAWN_QUIET_TICKS = 30;
    private static final int NAME_DEDUP_WARMUP_TICKS = 40;
    private static final int OPEN_MAX_RETRIES = 3;
    private static final int EQUIP_MAX_KEY_CHECK_FAILS = 3;
    private static final int DRINK_ANIM_TICKS = 40;
    private static final int DRINK_HOLD_TICKS = 35;
    private static final int DRINK_MAX_ATTEMPTS = 2;
    private static final int SCREEN_HOLD_MAX_TICKS = 200;
    private static final double LOOT_BOX_RADIUS = 3.0;
    private static final int INACTIVE_RECHECK_TICKS = 30;
    private static final double VAULT_ACTIVATION_RANGE = 4.0;
    private static final double CLOSE_IN_DISTANCE = 2.0;
    private static final int MAX_CLOSE_IN_ATTEMPTS = 2;
    private static final int SOFT_SKIP_MAX_FAILURES = 2;
    private static final int SOFT_SKIP_BUDGET = 4;
    private static final int SCREEN_WAIT_WARN_INTERVAL = 60;
    private static final int SCAN_BUDGET_PER_TICK = 6000;
    private static final int SCAN_Y_MIN = -40;
    private static final int SCAN_Y_MAX = 16;
    private static final int SCAN_FALLBACK_HALF = 8;
    private static final int SCAN_PROGRESS_LOG_TICKS = 20;
    private final Options opts;
    private final List<Item> targetItems;
    private final List<Identifier> targetEnchantments;
    private final Predicate<BlockPos> candidateFilter;
    private final Consumer<BlockPos> onOpened;
    private State state = State.IDLE;
    private TaskStatus status = TaskStatus.IDLE;
    private String failReason = "";
    private String lastMessage = "";
    private final List<String> lootLog = new ArrayList<>();
    private boolean foundTarget;
    private int openedCount;
    private final Set<BlockPos> openedPositions = new LinkedHashSet<>();
    private int delay;
    private BlockPos targetVault;
    private boolean targetOminous;
    private int openAttempts;
    private boolean clickedThisVault;
    private int inactiveRecheckTicks;
    private int spawnedCount;
    private int collectTicks;
    private int screenWaitTicks;
    private final Set<ItemEntity> seenItems = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> seenKeys = new HashSet<>();
    private int sinceLastSpawn;
    private final Map<Item, Integer> itemCountBaseline = new LinkedHashMap<>();
    private int targetBookBaseline;
    private boolean targetBookPickup;
    private final Set<String> targetEvidence = new LinkedHashSet<>();
    private int walkTicks;
    private int repathTicks;
    private int keyCheckFails;
    private double requiredOpenDistance;
    private int closeInAttempts;
    private final Map<BlockPos, Integer> unreachableFailures = new LinkedHashMap<>();
    private int softSkipCount;
    private boolean retryingSoftSkipped;
    private int drinkAttempts;
    private int drinkWaitTicks;
    private boolean holdingUse;
    private int holdUseTicks;
    private boolean pendingFail;
    private Box lootBox;
    private BlockPos lootBoxVault;
    private boolean keyConfigWarned;
    private boolean chunkLoadWarned;
    private boolean scanActive;
    private int scanOriginX;
    private int scanOriginY;
    private int scanOriginZ;
    private int scanRadius;
    private int scanDyMin;
    private int scanDyMax;
    private int scanDyRel;
    private int scanDz;
    private int scanDx;
    private final BlockPos.Mutable scanCursor = new BlockPos.Mutable();
    private long scanDone;
    private long scanTotal;
    private int scanChecked;
    private int scanSkippedUnloaded;
    private int scanTicks;
    private final List<BlockPos> scanCandidates = new ArrayList<>();
    private final Set<Long> scanCandidateKeys = new HashSet<>();
    private final Set<Long> filteredCandidateKeys = new LinkedHashSet<>();
    private final Set<Long> roundFilteredCandidateKeys = new LinkedHashSet<>();
    private int scanChunkX = Integer.MIN_VALUE;
    private int scanChunkZ = Integer.MIN_VALUE;
    private boolean scanChunkLoadedFlag;
    public VaultOpener(Options opts) {
        this.opts = opts;
        this.targetItems = copyItems(opts.targetItems());
        this.targetEnchantments = copyIds(opts.targetEnchantments());
        this.candidateFilter = opts.candidateFilter();
        this.onOpened = opts.onOpened();
    }
    private static List<Item> copyItems(List<Item> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<Item> out = new ArrayList<>(in.size());
        for (Item it : in) {
            if (it != null && !out.contains(it)) out.add(it);
        }
        return List.copyOf(out);
    }
    private static List<Identifier> copyIds(List<Identifier> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<Identifier> out = new ArrayList<>(in.size());
        for (Identifier id : in) {
            if (id != null && !out.contains(id)) out.add(id);
        }
        return List.copyOf(out);
    }
    public void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            fail("玩家/世界为空，无法寻找宝库");
            return;
        }
        lootLog.clear();
        openedPositions.clear();
        foundTarget = false;
        openedCount = 0;
        failReason = "";
        lastMessage = "";
        delay = 0;
        targetVault = null;
        targetOminous = false;
        openAttempts = 0;
        clickedThisVault = false;
        inactiveRecheckTicks = 0;
        spawnedCount = 0;
        collectTicks = 0;
        sinceLastSpawn = 0;
        screenWaitTicks = 0;
        seenItems.clear();
        seenKeys.clear();
        targetEvidence.clear();
        itemCountBaseline.clear();
        targetBookBaseline = 0;
        targetBookPickup = false;
        filteredCandidateKeys.clear();
        resetScanState();
        walkTicks = 0;
        repathTicks = 0;
        keyCheckFails = 0;
        closeInAttempts = 0;
        requiredOpenDistance = opts.openDistance();
        unreachableFailures.clear();
        softSkipCount = 0;
        retryingSoftSkipped = false;
        drinkAttempts = 0;
        drinkWaitTicks = 0;
        holdingUse = false;
        holdUseTicks = 0;
        pendingFail = false;
        keyConfigWarned = false;
        chunkLoadWarned = false;
        lootBox = null;
        lootBoxVault = null;
        status = TaskStatus.RUNNING;
        logConfiguredTargets();
        if (opts.needOminous() && opts.drinkOminousBottle()) {
            next(State.DRINK, 0);
        } else {
            next(State.SCAN, 0);
        }
        FOElytraLog.info("开始找宝库：半径 %d，%s，最多开 %d 个，收集时长 %d tick（设置原文 %d）",
            opts.searchRadius(), opts.needOminous() ? "只找不祥宝库" : "普通/不祥都行",
            opts.maxVaultsPerRun(), effectiveCollectTicks(), opts.openWaitTicks());
    }
    private void logConfiguredTargets() {
        if (targetItems.isEmpty() && targetEnchantments.isEmpty()) {
            FOElytraLog.warn("没有配置任何目标战利品（目标物品与目标魔咒都是空的）："
                + "本次只会记录每个宝库喷了什么，不会判定「命中」");
            return;
        }
        List<String> names = new ArrayList<>();
        for (Item it : targetItems) names.add(it.getName().getString());
        for (Identifier id : targetEnchantments) names.add("附魔书[" + id.getPath() + "]");
        FOElytraLog.detail("目标战利品：%s（物品 %d 种 / 魔咒 %d 个）",
            String.join("、", names), targetItems.size(), targetEnchantments.size());
    }
    public void abort(String reason) {
        if (BaritoneHook.ready()) BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        holdingUse = false;
        holdUseTicks = 0;
        if (state != State.IDLE) FOElytraLog.warn("宝库流程中止：%s", reason);
        state = State.IDLE;
        status = TaskStatus.IDLE;
        delay = 0;
        targetVault = null;
        lootBox = null;
        lootBoxVault = null;
        resetScanState();
    }
    public TaskStatus status() {
        return status;
    }
    public State state() {
        return state;
    }
    public String progress() {
        StringBuilder sb = new StringBuilder(state.toString());
        if (targetVault != null) {
            sb.append(' ').append(targetVault.toShortString()).append(targetOminous ? "(不祥)" : "(普通)");
        }
        if (state == State.SCAN && scanActive) {
            sb.append(" 扫描 ").append(scanDone).append('/').append(scanTotal)
                .append("（候选 ").append(scanCandidates.size()).append(" 个）");
        }
        if (status == TaskStatus.RUNNING) {
            sb.append(" 已开 ").append(openedCount).append('/').append(opts.maxVaultsPerRun());
            if (state == State.COLLECT) sb.append(" 已见 ").append(spawnedCount).append(" 件");
            if (state == State.OPEN) {
                sb.append(" 右键 ").append(openAttempts).append('/').append(OPEN_MAX_RETRIES);
                if (inactiveRecheckTicks > 0) {
                    sb.append(" 等状态刷新 ").append(inactiveRecheckTicks).append('/').append(INACTIVE_RECHECK_TICKS)
                        .append("（INACTIVE，先不点击）");
                }
            }
            if (state == State.WALK && requiredOpenDistance > 0 && requiredOpenDistance < opts.openDistance()) {
                sb.append(" 走近激活半径 ").append(closeInAttempts).append('/').append(MAX_CLOSE_IN_ATTEMPTS);
            }
            if (softSkipCount > 0) {
                sb.append(" 已软跳过 ").append(unreachableFailures.size()).append(" 个走不到的库");
                if (retryingSoftSkipped) sb.append("（正在再试一次）");
            }
            if (!filteredCandidateKeys.isEmpty()) {
                sb.append(" 已过滤 ").append(filteredCandidateKeys.size()).append(" 个（标记/展示物/刷怪笼）");
            }
        }
        if (!lastMessage.isEmpty()) sb.append(" — ").append(lastMessage);
        if (status == TaskStatus.FAILED && !failReason.isEmpty()) sb.append("(").append(failReason).append(')');
        return sb.toString();
    }
    public String failReason() {
        return failReason;
    }
    public String lastMessage() {
        return lastMessage;
    }
    public List<String> lootLog() {
        return java.util.Collections.unmodifiableList(lootLog);
    }
    public boolean foundTarget() {
        return foundTarget;
    }
    public int openedCount() {
        return openedCount;
    }
    public Set<BlockPos> openedPositions() {
        return openedPositions;
    }
    public void tick() {
        if (status != TaskStatus.RUNNING) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            fail("玩家/世界为空");
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            FOElytraLog.err("宝库流程内部异常: %s", String.valueOf(t));
            FOElytraLog.detailError("VaultOpener.step", t);
            fail("内部异常 " + t.getClass().getSimpleName());
        }
    }
    private void step(MinecraftClient mc) {
        switch (state) {
            case SCAN -> scan(mc);
            case EQUIP -> equip(mc);
            case WALK -> walk(mc);
            case OPEN -> open(mc);
            case COLLECT -> collect(mc);
            case NEXT -> next(mc);
            case DRINK -> drink(mc);
            case FINISH -> finish(mc);
            case DONE -> status = TaskStatus.DONE;
            case FAILED -> status = TaskStatus.FAILED;
            default -> status = TaskStatus.DONE;
        }
    }
    private void scan(MinecraftClient mc) {
        drinkWaitTicks = 0;
        drinkAttempts = 0;
        endUseHold("进入扫描阶段");
        if (!scanActive) beginScan(mc);
        scanTicks++;
        int budget = SCAN_BUDGET_PER_TICK;
        while (budget > 0) {
            int x = scanOriginX + scanDx;
            int y = scanOriginY + scanDyRel;
            int z = scanOriginZ + scanDz;
            scanCursor.set(x, y, z);
            scanDone++;
            budget--;
            if (!chunkLoaded(mc, x >> 4, z >> 4)) {
                scanSkippedUnloaded++;
            } else {
                scanChecked++;
                BlockState st = mc.world.getBlockState(scanCursor);
                if (st.isOf(Blocks.VAULT) && !openedPositions.contains(scanCursor)) {
                    boolean ominous = Boolean.TRUE.equals(st.get(VaultBlock.OMINOUS));
                    if (!opts.needOminous() || ominous) {
                        BlockPos fixed = scanCursor.toImmutable();
                        if (!rejectedByFilter(fixed) && scanCandidateKeys.add(fixed.asLong())) {
                            scanCandidates.add(fixed);
                        }
                    }
                }
            }
            if (!scanAdvance()) {
                finishScan(mc);
                return;
            }
        }
        if (scanTicks % SCAN_PROGRESS_LOG_TICKS == 0) {
            FOElytraLog.detail("SCAN 进度 %d/%d（%.0f%%）第 %d tick：已扫已加载 %d 格、跳过未加载 %d 格，候选宝库 %d 个",
                scanDone, scanTotal, 100.0 * scanDone / Math.max(1L, scanTotal), scanTicks,
                scanChecked, scanSkippedUnloaded, scanCandidates.size());
        }
        delay = 0;
    }
    private void beginScan(MinecraftClient mc) {
        BlockPos origin = mc.player.getBlockPos().toImmutable();
        scanOriginX = origin.getX();
        scanOriginY = origin.getY();
        scanOriginZ = origin.getZ();
        scanRadius = Math.max(1, opts.searchRadius());
        int yMin = Math.max(SCAN_Y_MIN, scanOriginY - scanRadius);
        int yMax = Math.min(SCAN_Y_MAX, scanOriginY + scanRadius);
        if (yMin > yMax) {
            yMin = Math.max(-64, scanOriginY - SCAN_FALLBACK_HALF);
            yMax = Math.min(319, scanOriginY + SCAN_FALLBACK_HALF);
            FOElytraLog.warn("你现在在 Y=%d，不在试炼密室的高度带（%d~%d）里：本次只在 Y=%d~%d 范围内找宝库",
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
        scanCandidates.clear();
        scanCandidateKeys.clear();
        roundFilteredCandidateKeys.clear();
        scanChunkX = Integer.MIN_VALUE;
        scanChunkZ = Integer.MIN_VALUE;
        scanChunkLoadedFlag = false;
        scanActive = true;
        FOElytraLog.detail("SCAN 开始：中心 %s，水平半径 %d 格，Y=%d~%d，共 %d 个坐标"
            + "（每 tick 最多 %d 个，跨 tick 续扫；Y 带与区块加载状态都先过滤）",
            origin.toShortString(), scanRadius, yMin, yMax, scanTotal, SCAN_BUDGET_PER_TICK);
    }
    private boolean scanAdvance() {
        scanDx++;
        if (scanDx <= scanRadius) return true;
        scanDx = -scanRadius;
        scanDz++;
        if (scanDz <= scanRadius) return true;
        scanDz = -scanRadius;
        scanDyRel++;
        return scanDyRel <= scanDyMax;
    }
    private boolean chunkLoaded(MinecraftClient mc, int cx, int cz) {
        if (cx == scanChunkX && cz == scanChunkZ) return scanChunkLoadedFlag;
        boolean loaded;
        try {
            loaded = mc.world.isChunkLoaded(cx, cz);
        } catch (Throwable t) {
            loaded = false;
            if (!chunkLoadWarned) {
                chunkLoadWarned = true;
                FOElytraLog.detailError("VaultOpener.chunkLoaded(" + cx + "," + cz + ")", t);
            }
        }
        scanChunkX = cx;
        scanChunkZ = cz;
        scanChunkLoadedFlag = loaded;
        return loaded;
    }
    private boolean rejectedByFilter(BlockPos pos) {
        if (candidateFilter == null) return false;
        boolean allowed = false;
        try {
            allowed = candidateFilter.test(pos);
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultOpener.candidateFilter(" + shortPos(pos) + ")", t);
            allowed = false;
        }
        if (allowed) return false;
        long key = pos.toImmutable().asLong();
        if (filteredCandidateKeys.add(key)) {
            FOElytraLog.detail("候选过滤：跳过 %s（上层 candidateFilter 拒绝 —— 已标记 / 展示物不是目标 / 离刷怪笼太近）",
                shortPos(pos));
        }
        roundFilteredCandidateKeys.add(key);
        return true;
    }
    private void finishScan(MinecraftClient mc) {
        scanActive = false;
        BlockPos origin = mc.player.getBlockPos();
        FOElytraLog.detail("SCAN 完成：处理 %d 个坐标（已加载 %d、跳过未加载 %d），候选宝库 %d 个，耗时约 %d tick",
            scanDone, scanChecked, scanSkippedUnloaded, scanCandidates.size(), scanTicks);
        if (candidateFilter != null) {
            FOElytraLog.detail("候选过滤：跳过 %d 个（本轮；累计 %d 个不同坐标）——被过滤 ≠ 找不到宝库："
                + "它们不进 openedPositions、也不占软跳过额度",
                roundFilteredCandidateKeys.size(), filteredCandidateKeys.size());
        }
        if (scanCandidates.isEmpty()) {
            lastMessage = "半径内没有可开的宝库";
            if (openedCount > 0) {
                FOElytraLog.warn("半径 %d 内已经没有没开过的%s宝库了（本次已开 %d 个）",
                    scanRadius, opts.needOminous() ? "不祥" : "", openedCount);
            } else {
                FOElytraLog.warn("%s（半径 %d，位置 %s）——可能不在试炼密室里，或此处宝库已经全被开过",
                    lastMessage, scanRadius, origin.toShortString());
            }
            next(State.FINISH, 0);
            return;
        }
        scanCandidates.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(origin)));
        BlockPos best = null;
        boolean bestOminous = false;
        int stale = 0;
        int softExcluded = 0;
        retryingSoftSkipped = false;
        for (BlockPos p : scanCandidates) {
            BlockState st = mc.world.getBlockState(p);
            if (!st.isOf(Blocks.VAULT)) {
                stale++;
                continue;
            }
            if (openedPositions.contains(p)) continue;
            if (rejectedByFilter(p)) continue;
            Integer fails = unreachableFailures.get(p);
            if (fails != null) continue;
            boolean ominous = Boolean.TRUE.equals(st.get(VaultBlock.OMINOUS));
            if (opts.needOminous() && !ominous) continue;
            best = p;
            bestOminous = ominous;
            break;
        }
        if (best == null) {
            int fewest = Integer.MAX_VALUE;
            for (BlockPos p : scanCandidates) {
                BlockState st = mc.world.getBlockState(p);
                if (!st.isOf(Blocks.VAULT)) continue;
                if (openedPositions.contains(p)) continue;
                if (rejectedByFilter(p)) continue;
                Integer fails = unreachableFailures.get(p);
                if (fails == null) continue;
                if (fails >= SOFT_SKIP_MAX_FAILURES) {
                    softExcluded++;
                    continue;
                }
                boolean ominous = Boolean.TRUE.equals(st.get(VaultBlock.OMINOUS));
                if (opts.needOminous() && !ominous) continue;
                if (fails < fewest) {
                    fewest = fails;
                    best = p;
                    bestOminous = ominous;
                }
            }
            if (best != null) {
                retryingSoftSkipped = true;
                FOElytraLog.warn("半径 %d 内没有「没试过」的宝库了，回去再试一次 %s（它之前已经软跳过 %d 次）",
                    scanRadius, best.toShortString(), fewest);
            } else if (softExcluded > 0) {
                FOElytraLog.warn("半径 %d 内的宝库都不适用：%d 个走不进去的已用完再试额度（每个最多 %d 次）",
                    scanRadius, softExcluded, SOFT_SKIP_MAX_FAILURES);
            }
        }
        if (best == null) {
            lastMessage = stale > 0 && softExcluded == 0
                ? "候选宝库都在扫描期间消失了" : "半径内没有可开的宝库";
            FOElytraLog.warn("%s（候选 %d 个，其中 %d 个已经不是宝库方块、%d 个走不进去且已用完再试额度）",
                lastMessage, scanCandidates.size(), stale, softExcluded);
            next(State.FINISH, 0);
            return;
        }
        targetVault = best;
        targetOminous = bestOminous;
        openAttempts = 0;
        clickedThisVault = false;
        inactiveRecheckTicks = 0;
        spawnedCount = 0;
        collectTicks = 0;
        sinceLastSpawn = 0;
        targetEvidence.clear();
        walkTicks = 0;
        repathTicks = 0;
        keyCheckFails = 0;
        closeInAttempts = 0;
        requiredOpenDistance = opts.openDistance();
        lastMessage = "";
        FOElytraLog.detail("SCAN 选中宝库 %s（%s，距离 %.1f 格，候选 %d 个里最近%s）",
            best.toShortString(), bestOminous ? "不祥" : "普通",
            Math.sqrt(best.getSquaredDistance(origin)), scanCandidates.size(),
            retryingSoftSkipped ? "，且这是「软跳过再试一次」的候选" : "");
        next(State.EQUIP, 0);
    }
    private void resetScanState() {
        scanActive = false;
        scanRadius = 0;
        scanOriginX = 0;
        scanOriginY = 0;
        scanOriginZ = 0;
        scanDyMin = 0;
        scanDyMax = -1;
        scanDyRel = 0;
        scanDz = 0;
        scanDx = 0;
        scanTotal = 0;
        scanDone = 0;
        scanChecked = 0;
        scanSkippedUnloaded = 0;
        scanTicks = 0;
        scanCandidates.clear();
        scanCandidateKeys.clear();
        roundFilteredCandidateKeys.clear();
        scanChunkX = Integer.MIN_VALUE;
        scanChunkZ = Integer.MIN_VALUE;
        scanChunkLoadedFlag = false;
        scanCursor.set(0, 0, 0);
    }
    private void equip(MinecraftClient mc) {
        if (targetVault == null) {
            FOElytraLog.warn("EQUIP 时宝库坐标丢失，回到 SCAN 重找");
            next(State.SCAN, 0);
            return;
        }
        BlockState st = mc.world.getBlockState(targetVault);
        if (!st.isOf(Blocks.VAULT)) {
            FOElytraLog.warn("宝库 %s 已经不是宝库方块了（被挖掉/被别人开掉），换下一个", targetVault.toShortString());
            next(State.NEXT, opts.actionDelay());
            return;
        }
        boolean ominous = Boolean.TRUE.equals(st.get(VaultBlock.OMINOUS));
        if (ominous != targetOminous) {
            FOElytraLog.detail("宝库 %s 的不祥状态在流程中发生了变化（%s → %s），按当前状态重新选钥匙",
                targetVault.toShortString(), targetOminous, ominous);
            targetOminous = ominous;
        }
        if (InvHelper.screenOpen()) {
            delay = waitForPlayerScreen();
            return;
        }
        Item key = requiredKey(mc);
        int hotbar = findKeyHotbar(key);
        if (hotbar >= 0) {
            mc.player.getInventory().setSelectedSlot(hotbar);
            FOElytraLog.detail("EQUIP 快捷栏第 %d 格已经是%s，直接选中（手持：%s）",
                hotbar, key.getName().getString(), mc.player.getInventory().getSelectedStack().getName().getString());
        } else {
            int slot = InvHelper.findSlot(s -> s.isOf(key));
            if (slot < 0) {
                FOElytraLog.detail("EQUIP 背包 0-35 里没有任何 %s", key.getName().getString());
                if (targetOminous) {
                    fail("背包里没有不祥试炼钥匙（不祥钥匙来自不祥试炼刷怪笼）");
                } else {
                    fail("背包里没有试炼钥匙（普通钥匙来自试炼刷怪笼）");
                }
                return;
            }
            int empty = InvHelper.findEmptyHotbarSlot();
            if (empty < 0) {
                empty = findSacrificialHotbarSlot(mc);
                if (empty < 0) {
                    FOElytraLog.detail("EQUIP 快捷栏 0-8 全是 %s，没有可换的格子", key.getName().getString());
                    fail("快捷栏没有空位可以放 " + key.getName().getString());
                    return;
                }
                FOElytraLog.detail("EQUIP 快捷栏已满，占用第 %d 格（原物品：%s）",
                    empty, mc.player.getInventory().getStack(empty).getName().getString());
            }
            InvHelper.moveInvToHotbar(slot, empty);
            mc.player.getInventory().setSelectedSlot(empty);
            FOElytraLog.detail("EQUIP 背包第 %d 格 → 快捷栏第 %d 格，已选中手持 %s",
                slot, empty, mc.player.getInventory().getSelectedStack().getName().getString());
        }
        ItemStack held = mc.player.getInventory().getSelectedStack();
        if (!keyMatches(held, key, mc)) {
            keyCheckFails++;
            String heldName = held.isEmpty() ? "空" : held.getName().getString();
            FOElytraLog.detail("EQUIP 校验失败（第 %d 次，上限 %d 次）：手持是 %s，期望 %s（可能因为界面打开/同步延迟）",
                keyCheckFails, EQUIP_MAX_KEY_CHECK_FAILS, heldName, key.getName().getString());
            if (keyCheckFails > EQUIP_MAX_KEY_CHECK_FAILS) {
                fail("钥匙换到手上失败超过 " + EQUIP_MAX_KEY_CHECK_FAILS + " 次（第 " + keyCheckFails
                    + " 次仍不对，手持是 " + heldName + "，期望 " + key.getName().getString()
                    + "）——请确认背包里有这把钥匙，并且没有别的模块一直在抢手持格");
                return;
            }
            next(State.EQUIP, Math.max(1, opts.actionDelay()));
            return;
        }
        next(State.WALK, opts.actionDelay());
    }
    private Item requiredKey(MinecraftClient mc) {
        if (targetVault != null) {
            try {
                if (mc.world.getBlockEntity(targetVault) instanceof VaultBlockEntity vault) {
                    VaultConfig cfg = vault.getConfig();
                    if (cfg != null && cfg.keyItem() != null && !cfg.keyItem().isEmpty()) return cfg.keyItem().getItem();
                }
            } catch (Throwable t) {
                if (!keyConfigWarned) {
                    keyConfigWarned = true;
                    FOElytraLog.detail("读不到宝库方块实体的 config.key_item，退回按 OMINOUS 状态判断：%s", String.valueOf(t));
                }
            }
        }
        return targetOminous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;
    }
    private boolean keyMatches(ItemStack held, Item required, MinecraftClient mc) {
        if (held == null || held.isEmpty() || required == null) return false;
        if (!held.isOf(required)) return false;
        ItemStack need = configuredKeyStack(required, mc);
        if (need == null) return true;
        return ItemStack.areItemsAndComponentsEqual(held, need) && held.getCount() >= Math.max(1, need.getCount());
    }
    private ItemStack configuredKeyStack(Item required, MinecraftClient mc) {
        if (targetVault == null) return null;
        try {
            if (mc.world.getBlockEntity(targetVault) instanceof VaultBlockEntity vault) {
                VaultConfig cfg = vault.getConfig();
                if (cfg != null && cfg.keyItem() != null && cfg.keyItem().isOf(required)) return cfg.keyItem();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
    private int findKeyHotbar(Item key) {
        return InvHelper.findSlot(s -> s.isOf(key), 0, 9);
    }
    private int findSacrificialHotbarSlot(MinecraftClient mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.TRIAL_KEY) || s.isOf(Items.OMINOUS_TRIAL_KEY)) continue;
            if (s.isOf(Items.TOTEM_OF_UNDYING)) continue;
            if (ItemHelper.isFood(s)) continue;
            return i;
        }
        return -1;
    }
    private void walk(MinecraftClient mc) {
        if (targetVault == null) {
            next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            delay = waitForPlayerScreen();
            return;
        }
        double d = horizontalDistance(mc, targetVault);
        if (walkTicks++ >= WALK_TIMEOUT_TICKS) {
            FOElytraLog.warn("走到宝库 %s 超时（%d 秒，水平距离还有 %.1f 格）",
                targetVault.toShortString(), WALK_TIMEOUT_TICKS / 20, d);
            if (BaritoneHook.ready()) BaritoneHook.stop();
            softSkipCurrentVault("走到它超时（" + (WALK_TIMEOUT_TICKS / 20) + " 秒还没进到位）");
            return;
        }
        if (d <= effectiveOpenDistance()) {
            FOElytraLog.detail("WALK 到位：水平距离 %.2f ≤ %.2f（用了 %d tick），准备开 %s",
                d, effectiveOpenDistance(), walkTicks, targetVault.toShortString());
            if (BaritoneHook.ready()) BaritoneHook.stop();
            walkTicks = 0;
            next(State.OPEN, 0);
            return;
        }
        if (!opts.useBaritoneWalk()) {
            delay = 0;
            return;
        }
        if (!BaritoneHook.ready()) {
            fail("Baritone 不可用，无法走到宝库（请安装 Baritone，或把手动走过去后会自动开）");
            return;
        }
        if (repathTicks <= 0) {
            BaritoneHook.command("goto " + targetVault.getX() + " " + targetVault.getY() + " " + targetVault.getZ());
            repathTicks = WALK_REPATH_TICKS;
            FOElytraLog.detail("WALK 下发 goto %d %d %d（水平距离 %.1f，已走 %d tick）",
                targetVault.getX(), targetVault.getY(), targetVault.getZ(), d, walkTicks);
            next(State.WALK, 5);
            return;
        }
        repathTicks--;
        delay = 0;
    }
    private double horizontalDistance(MinecraftClient mc, BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }
    private double effectiveOpenDistance() {
        return requiredOpenDistance > 0 ? requiredOpenDistance : opts.openDistance();
    }
    private double distanceToVaultCenter(MinecraftClient mc, BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dy = mc.player.getY() - (pos.getY() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    private void open(MinecraftClient mc) {
        if (targetVault == null) {
            next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            delay = waitForPlayerScreen();
            return;
        }
        BlockState st = mc.world.getBlockState(targetVault);
        if (!st.isOf(Blocks.VAULT)) {
            FOElytraLog.warn("OPEN 时宝库 %s 已经不是宝库方块了，换下一个", targetVault.toShortString());
            next(State.NEXT, opts.actionDelay());
            return;
        }
        VaultState vaultState = st.get(VaultBlock.VAULT_STATE);
        if (vaultState == VaultState.INACTIVE) {
            double dist = distanceToVaultCenter(mc, targetVault);
            if (dist > VAULT_ACTIVATION_RANGE) {
                if (closeInAttempts >= MAX_CLOSE_IN_ATTEMPTS) {
                    FOElytraLog.warn("宝库 %s 进不去它的激活半径（3D 距离 %.2f > %.1f，已尝试走近 %d 次）",
                        targetVault.toShortString(), dist, VAULT_ACTIVATION_RANGE, closeInAttempts);
                    softSkipCurrentVault("走到位了但 3D 距离 " + String.format("%.2f", dist)
                        + " 仍在激活半径 " + String.format("%.1f", VAULT_ACTIVATION_RANGE) + " 之外");
                    return;
                }
                closeInAttempts++;
                requiredOpenDistance = CLOSE_IN_DISTANCE;
                inactiveRecheckTicks = 0;
                walkTicks = 0;
                FOElytraLog.detail("宝库 %s 读到 INACTIVE 但先不采信：3D 距离 %.2f > 激活半径 %.1f"
                    + "（WALK 的到位判定只看水平距离，上一层/下一层就会这样）→ 回 WALK 走到 %.1f 格内"
                    + "再试（第 %d/%d 次）",
                    targetVault.toShortString(), dist, VAULT_ACTIVATION_RANGE, CLOSE_IN_DISTANCE,
                    closeInAttempts, MAX_CLOSE_IN_ATTEMPTS);
                if (BaritoneHook.ready()) BaritoneHook.stop();
                next(State.WALK, opts.actionDelay());
                return;
            }
            if (inactiveRecheckTicks < INACTIVE_RECHECK_TICKS) {
                inactiveRecheckTicks++;
                FOElytraLog.detail("INACTIVE 复核中 %d/%d tick（服务端状态最多滞后 %d tick，先不点击）："
                    + "宝库 %s，3D 距离 %.2f ≤ %.1f",
                    inactiveRecheckTicks, INACTIVE_RECHECK_TICKS, INACTIVE_RECHECK_TICKS,
                    targetVault.toShortString(), dist, VAULT_ACTIVATION_RANGE);
                next(State.OPEN, 0);
                return;
            }
            FOElytraLog.warn("宝库 %s 处于 INACTIVE（等了 %d tick 也没变成 ACTIVE，而且我确实在它的激活半径内："
                + "3D 距离 %.2f ≤ %.1f）= 这个宝库已经给我发过奖励，或数据包改了它的 key_item。"
                + "不浪费钥匙，换下一个",
                targetVault.toShortString(), INACTIVE_RECHECK_TICKS, dist, VAULT_ACTIVATION_RANGE);
            next(State.NEXT, opts.actionDelay());
            return;
        }
        int recheckWaited = inactiveRecheckTicks;
        inactiveRecheckTicks = 0;
        if (vaultState == VaultState.UNLOCKING || vaultState == VaultState.EJECTING) {
            FOElytraLog.warn("宝库 %s 已经在 %s（正在解锁 / 正在喷战利品），别重复开，换下一个",
                targetVault.toShortString(), vaultState);
            next(State.NEXT, opts.actionDelay());
            return;
        }
        if (recheckWaited > 0) {
            FOElytraLog.detail("状态刷新为 ACTIVE，继续开宝库 %s（INACTIVE 复核等了 %d tick，之前不点击是对的）",
                targetVault.toShortString(), recheckWaited);
        }
        Item key = requiredKey(mc);
        ItemStack held = mc.player.getInventory().getSelectedStack();
        if (!keyMatches(held, key, mc)) {
            keyCheckFails++;
            String heldName = held.isEmpty() ? "空" : held.getName().getString();
            FOElytraLog.detail("OPEN 前发现手持是 %s，不是 %s，回 EQUIP 重换（累计第 %d 次换不上手，"
                + "上限 %d 次）", heldName, key.getName().getString(), keyCheckFails, EQUIP_MAX_KEY_CHECK_FAILS);
            if (keyCheckFails > EQUIP_MAX_KEY_CHECK_FAILS) {
                fail("钥匙换到手上失败超过 " + EQUIP_MAX_KEY_CHECK_FAILS + " 次（第 " + keyCheckFails
                    + " 次 OPEN 前手持是 " + heldName + "，期望 " + key.getName().getString()
                    + "）——可能有别的模块一直在抢手持格");
                return;
            }
            next(State.EQUIP, opts.actionDelay());
            return;
        }
        boolean accepted = InvHelper.interactBlock(targetVault);
        openAttempts++;
        FOElytraLog.detail("OPEN 第 %d/%d 次右键 %s（%s，方块状态 %s，手持 %s），interactBlock=%s",
            openAttempts, OPEN_MAX_RETRIES, targetVault.toShortString(),
            targetOminous ? "不祥宝库" : "普通宝库", vaultState, held.getName().getString(), accepted);
        if (!accepted) {
            if (openAttempts >= OPEN_MAX_RETRIES) {
                FOElytraLog.warn("宝库 %s 右键 %d 次都没被接受（视线被挡？距离太远？服务器没批准），换下一个",
                    targetVault.toShortString(), openAttempts);
                next(State.NEXT, opts.actionDelay());
                return;
            }
            FOElytraLog.detail("OPEN 第 %d 次右键被拒，留在 OPEN 重试（同一状态自转 + delay 节流，上限 %d 次）",
                openAttempts, OPEN_MAX_RETRIES);
            next(State.OPEN, Math.max(1, opts.actionDelay()));
            return;
        }
        clickedThisVault = true;
        lootBox = new Box(targetVault).expand(LOOT_BOX_RADIUS);
        lootBoxVault = targetVault.toImmutable();
        spawnedCount = 0;
        collectTicks = 0;
        sinceLastSpawn = 0;
        targetEvidence.clear();
        snapshotTargetCounts(mc.player);
        next(State.COLLECT, 1);
    }
    private void collect(MinecraftClient mc) {
        if (targetVault == null) {
            next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            delay = waitForPlayerScreen();
            return;
        }
        scanLootEntities(mc);
        updateTargetCounts(mc.player);
        collectTicks++;
        sinceLastSpawn++;
        int wait = effectiveCollectTicks();
        if (collectTicks == 1 && wait != opts.openWaitTicks()) {
            FOElytraLog.detail("COLLECT 开始：等待喷出 tick 原文 %d / 实际 %d（低于下限 %d，按喷出节奏抬到下限）",
                opts.openWaitTicks(), wait, COLLECT_MIN_TICKS);
        }
        if (matchAnyTarget()) {
            onTargetFound(evidenceText());
            return;
        }
        if (spawnedCount > 0 && sinceLastSpawn >= SPAWN_QUIET_TICKS) {
            FOElytraLog.detail("COLLECT 提前收工：已见 %d 件、最后一个之后 %d tick 没有再出现（判据 %d tick）",
                spawnedCount, sinceLastSpawn, SPAWN_QUIET_TICKS);
            onCollectFinished();
            return;
        }
        if (collectTicks >= wait) {
            onCollectFinished();
            return;
        }
        delay = 0;
    }
    private void scanLootEntities(MinecraftClient mc) {
        if (lootBox == null || targetVault == null || !targetVault.equals(lootBoxVault)) {
            lootBox = new Box(targetVault).expand(LOOT_BOX_RADIUS);
            lootBoxVault = targetVault.toImmutable();
            FOElytraLog.detail("COLLECT 重建收集盒：以宝库 %s 为中心、半径 %.1f（换库必须重建，否则实体证据会落在上一个库的坐标上）",
                targetVault.toShortString(), LOOT_BOX_RADIUS);
        }
        List<ItemEntity> ents = mc.world.getEntitiesByClass(
            ItemEntity.class, lootBox,
            e -> e != null && e.isAlive() && !e.getStack().isEmpty());
        for (ItemEntity e : ents) {
            if (!seenItems.add(e)) continue;
            ItemStack stack = e.getStack();
            String name = stack.getItem().getName().getString();
            String key = shortPos(e.getBlockPos()) + "#" + name;
            if (collectTicks >= NAME_DEDUP_WARMUP_TICKS && !seenKeys.add(key)) continue;
            spawnedCount++;
            sinceLastSpawn = 0;
            boolean target = isTargetStack(stack);
            String shown = target ? describeTargetStack(stack) : name;
            FOElytraLog.detail("COLLECT 第 %d 件：%s x%d（实体 id=%d，位置 %s，命中目标=%s）",
                spawnedCount, shown, stack.getCount(), e.getId(), shortPos(e.getBlockPos()), target);
            lootLog.add("掉落：" + shown + " x" + stack.getCount() + "（宝库 " + vaultHead() + "）");
            if (target) targetEvidence.add(shown);
        }
    }
    private void snapshotTargetCounts(PlayerEntity player) {
        itemCountBaseline.clear();
        if (player != null) {
            for (Item it : targetItems) itemCountBaseline.put(it, ItemHelper.countInInventory(player, it));
        }
        targetBookBaseline = countTargetBooks(player);
        targetBookPickup = false;
        FOElytraLog.detail("COLLECT 背包基线：%s；带目标魔咒的附魔书 %d 本（目标物品 %d 种 / 目标魔咒 %d 个）",
            baselineText(), targetBookBaseline, targetItems.size(), targetEnchantments.size());
    }
    private void updateTargetCounts(PlayerEntity player) {
        if (player == null) return;
        for (Item it : targetItems) {
            int now = ItemHelper.countInInventory(player, it);
            Integer before = itemCountBaseline.get(it);
            if (before == null) {
                itemCountBaseline.put(it, now);
                continue;
            }
            if (now > before) {
                itemCountBaseline.put(it, now);
                targetEvidence.add(it.getName().getString() + "（已捡进背包）");
            }
        }
        int books = countTargetBooks(player);
        if (books > targetBookBaseline) {
            targetBookBaseline = books;
            targetBookPickup = true;
            targetEvidence.add(describeTargetBooks(player));
        }
    }
    private int countTargetBooks(PlayerEntity player) {
        if (player == null || targetEnchantments.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(Items.ENCHANTED_BOOK)) continue;
            if (matchesTargetEnchantment(s)) total += s.getCount();
        }
        return total;
    }
    private boolean matchAnyTarget() {
        return !targetEvidence.isEmpty();
    }
    private boolean isTargetStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!targetItems.isEmpty() && targetItems.contains(stack.getItem())) return true;
        return stack.isOf(Items.ENCHANTED_BOOK) && matchesTargetEnchantment(stack);
    }
    private boolean matchesTargetEnchantment(ItemStack stack) {
        if (targetEnchantments.isEmpty()) return false;
        ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;
        for (RegistryEntry<Enchantment> entry : stored.getEnchantments()) {
            for (Identifier id : targetEnchantments) {
                if (entry.matchesId(id)) return true;
            }
        }
        return false;
    }
    private List<String> matchingEnchantmentNames(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (targetEnchantments.isEmpty()) return out;
        ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return out;
        for (RegistryEntry<Enchantment> entry : stored.getEnchantments()) {
            for (Identifier id : targetEnchantments) {
                if (!entry.matchesId(id)) continue;
                String n = enchantDisplayName(entry, id);
                if (!out.contains(n)) out.add(n);
                break;
            }
        }
        return out;
    }
    private String enchantDisplayName(RegistryEntry<Enchantment> entry, Identifier matchedId) {
        try {
            var text = entry.value().description();
            if (text != null) {
                String s = text.getString();
                if (s != null && !s.isBlank()) return s;
            }
        } catch (Throwable t) {
            FOElytraLog.detail("读魔咒名字失败（用 ID 代替显示）：%s", String.valueOf(t));
        }
        return matchedId.getPath();
    }
    private String describeTargetStack(ItemStack stack) {
        String base = stack.getItem().getName().getString();
        if (!stack.isOf(Items.ENCHANTED_BOOK)) return base;
        List<String> names = matchingEnchantmentNames(stack);
        return names.isEmpty() ? base : base + "[" + String.join("、", names) + "]";
    }
    private String describeTargetBooks(PlayerEntity player) {
        String base = Items.ENCHANTED_BOOK.getName().getString();
        List<String> names = new ArrayList<>();
        if (player != null) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = player.getInventory().getStack(i);
                if (s.isEmpty() || !s.isOf(Items.ENCHANTED_BOOK)) continue;
                for (String n : matchingEnchantmentNames(s)) {
                    if (!names.contains(n)) names.add(n);
                }
            }
        }
        return names.isEmpty() ? base + "（带目标魔咒）" : base + "[" + String.join("、", names) + "]";
    }
    private String evidenceText() {
        return targetEvidence.isEmpty() ? "目标战利品" : String.join("、", targetEvidence);
    }
    private void onTargetFound(String what) {
        foundTarget = true;
        String line = "命中：出了 " + what + "（宝库 " + vaultHead() + "）";
        lootLog.add(line);
        FOElytraLog.info("命中目标！%s", line);
        FOElytraLog.detail("COLLECT 判定完成：命中 %s（实体 %d 件；证据：%s；目标魔咒书=%s）",
            what, spawnedCount, String.join("、", targetEvidence), targetBookPickup);
        if (opts.stopOnTarget()) {
            lastMessage = "已拿到目标战利品：" + what;
            settleBeforeDone();
            goal(State.DONE);
        } else {
            FOElytraLog.tip("已拿到 %s，按设置继续开下一个宝库", what);
            next(State.NEXT, opts.actionDelay());
        }
    }
    private void onCollectFinished() {
        MinecraftClient mc = MinecraftClient.getInstance();
        updateTargetCounts(mc.player);
        if (matchAnyTarget()) {
            onTargetFound(evidenceText());
            return;
        }
        String head = "宝库 " + vaultHead();
        if (spawnedCount > 0) {
            lootLog.add("不是目标：" + head + " 喷出 " + spawnedCount + " 件，都不是要的东西");
            FOElytraLog.tip("%s 出的不是目标（%d 件），换下一个宝库", head, spawnedCount);
        } else {
            lootLog.add("空开：" + head + " " + collectTicks + " tick 内没有喷出任何物品");
            FOElytraLog.warn("%s 空开（%d tick 内没喷出任何物品）——可能你之前已经开过它、钥匙没被服务端接受、"
                + "或者站着的位置离得太远；也可能是战利品还没来得及喷完（把「等待喷出 tick」调大试试）",
                head, collectTicks);
        }
        FOElytraLog.detail("COLLECT 结束：实体 %d 件，收集 %d tick（等待喷出 tick 原文 %d / 实际 %d），"
            + "距最后一个新掉落物 %d tick，命中证据 %d 条",
            spawnedCount, collectTicks, opts.openWaitTicks(), effectiveCollectTicks(),
            sinceLastSpawn, targetEvidence.size());
        next(State.NEXT, opts.actionDelay());
    }
    private void next(MinecraftClient mc) {
        if (targetVault != null) {
            boolean firstTime = openedPositions.add(targetVault);
            String pos = targetVault.toShortString();
            if (!firstTime) {
                FOElytraLog.detail("NEXT 宝库 %s 之前已经记过（同一坐标重复选中 → 空开），不再重复计数", pos);
            } else if (clickedThisVault) {
                openedCount++;
                FOElytraLog.detail("NEXT 记录已开宝库 %s（本次第 %d/%d 个，不祥=%s）",
                    pos, openedCount, opts.maxVaultsPerRun(), targetOminous);
                notifyOpened(targetVault);
            } else {
                FOElytraLog.detail("NEXT 宝库 %s 这次没发出过被接受的右键（走不到 / 方块变了 / 右键被拒），"
                    + "只记坐标防止回头重选，不计入「已开」", pos);
            }
        }
        if (spawnedCount == 0) {
            FOElytraLog.detail("NEXT 这次没拿到任何掉落物：%s 在 %d tick 里没有物品被我们看到"
                + "（可能是空开、也可能是掉落物已经被自己捡进背包）",
                targetVault == null ? "?" : targetVault.toShortString(), collectTicks);
        }
        resetVaultState();
        if (openedCount >= opts.maxVaultsPerRun()) {
            lastMessage = "已达到本次上限 " + opts.maxVaultsPerRun() + " 个宝库";
            FOElytraLog.info("%s（命中的目标：%s）", lastMessage, foundTarget ? "有" : "无");
            settleBeforeDone();
            goal(State.DONE);
            return;
        }
        next(State.SCAN, opts.actionDelay());
    }
    private void softSkipCurrentVault(String why) {
        BlockPos pos = targetVault == null ? null : targetVault.toImmutable();
        if (pos == null) {
            next(State.SCAN, opts.actionDelay());
            return;
        }
        int failures = unreachableFailures.merge(pos, 1, Integer::sum);
        softSkipCount++;
        FOElytraLog.warn("宝库 %s 走不进去（%s，第 %d 次），本轮暂时跳过；"
            + "如果周围有别的候选会先去别的，没有就再试它一次", pos.toShortString(), why, failures);
        FOElytraLog.detail("软跳过 %s：%s（第 %d 次；每个坐标最多 %d 次，达到后本轮不再选它）。"
            + "它不写进 openedPositions —— 那不是「已经处理过」，只是这一次没走到",
            pos.toShortString(), why, failures, SOFT_SKIP_MAX_FAILURES);
        resetVaultState();
        if (softSkipCount >= SOFT_SKIP_BUDGET) {
            lastMessage = "这一片已经有 " + unreachableFailures.size() + " 个宝库走不进去，本密室先收工";
            FOElytraLog.warn("%s（累计软跳过 %d 次，上限 %d 次）—— 多半是墙多/落差大够不到；"
                + "按「正常结束」收工（不是失败），换个密室或换个位置再跑",
                lastMessage, softSkipCount, SOFT_SKIP_BUDGET);
            next(State.FINISH, 0);
            return;
        }
        next(State.SCAN, opts.actionDelay());
    }
    private void resetVaultState() {
        targetVault = null;
        spawnedCount = 0;
        collectTicks = 0;
        sinceLastSpawn = 0;
        openAttempts = 0;
        clickedThisVault = false;
        inactiveRecheckTicks = 0;
        walkTicks = 0;
        repathTicks = 0;
        closeInAttempts = 0;
        requiredOpenDistance = opts.openDistance();
        lootBox = null;
        lootBoxVault = null;
        targetEvidence.clear();
        itemCountBaseline.clear();
        targetBookBaseline = 0;
        targetBookPickup = false;
        retryingSoftSkipped = false;
    }
    private void drink(MinecraftClient mc) {
        if (!opts.drinkOminousBottle()) {
            endUseHold("设置不需要喝不祥之瓶");
            next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            if (holdingUse) {
                endUseHold("你打开了界面，喝瓶被打断");
                drinkWaitTicks = DRINK_ANIM_TICKS;
            }
            delay = waitForPlayerScreen();
            return;
        }
        if (holdingUse) {
            if (hasOmen(mc.player)) {
                endUseHold("已经拿到征兆");
                drinkWaitTicks = 0;
                FOElytraLog.tip("不祥之兆已上身（靠近试炼刷怪笼后它会变成试炼之兆，不祥刷怪笼才会掉不祥钥匙）");
                next(State.SCAN, opts.actionDelay());
                return;
            }
            holdUseTicks--;
            if (holdUseTicks <= 0) {
                endUseHold("按住 " + DRINK_HOLD_TICKS + " tick 完成");
                drinkWaitTicks = DRINK_ANIM_TICKS;
            }
            delay = 0;
            return;
        }
        if (hasOmen(mc.player)) {
            FOElytraLog.detail("DRINK 身上已经有%s，不用喝不祥之瓶",
                mc.player.hasStatusEffect(StatusEffects.TRIAL_OMEN) ? "试炼之兆" : "不祥之兆");
            drinkWaitTicks = 0;
            next(State.SCAN, opts.actionDelay());
            return;
        }
        if (drinkWaitTicks > 0) {
            drinkWaitTicks--;
            if (drinkWaitTicks == 0) {
                FOElytraLog.warn("喝了不祥之瓶但身上还是没有征兆（第 %d 次尝试）", drinkAttempts);
            }
            delay = 0;
            return;
        }
        if (drinkAttempts >= DRINK_MAX_ATTEMPTS) {
            FOElytraLog.warn("不祥之瓶喝了 %d 次都没生效（可能被攻击打断，或服务器拦截了使用），直接去找宝库",
                drinkAttempts);
            next(State.SCAN, opts.actionDelay());
            return;
        }
        int hotbar = InvHelper.findSlot(s -> s.isOf(Items.OMINOUS_BOTTLE), 0, 9);
        if (hotbar < 0) {
            int slot = InvHelper.findSlot(s -> s.isOf(Items.OMINOUS_BOTTLE));
            if (slot < 0) {
                FOElytraLog.warn("设置要「自动喝不祥之瓶」，但背包里一个不祥之瓶都没有"
                    + "（不祥之瓶来自不祥宝库/试炼密室战利品，或已开过的不祥宝库概率掉落）");
                next(State.SCAN, opts.actionDelay());
                return;
            }
            int empty = InvHelper.findEmptyHotbarSlot();
            if (empty < 0) {
                empty = findSacrificialHotbarSlot(mc);
                if (empty < 0) {
                    FOElytraLog.warn("快捷栏没有空位放不祥之瓶，跳过喝瓶直接去找宝库");
                    next(State.SCAN, opts.actionDelay());
                    return;
                }
            }
            InvHelper.moveInvToHotbar(slot, empty);
            hotbar = empty;
            FOElytraLog.detail("DRINK 不祥之瓶 背包第 %d 格 → 快捷栏第 %d 格", slot, hotbar);
        }
        mc.player.getInventory().setSelectedSlot(hotbar);
        drinkAttempts++;
        PlayerAction.pressUse(true);
        holdingUse = true;
        holdUseTicks = DRINK_HOLD_TICKS;
        FOElytraLog.detail("DRINK 第 %d 次：按住右键喝不祥之瓶（快捷栏第 %d 格，按住 %d tick，期间拿到征兆会提前松开）",
            drinkAttempts, hotbar, DRINK_HOLD_TICKS);
        FOElytraLog.tip("正在喝不祥之瓶（不祥之兆）——靠近试炼刷怪笼后它才会变成试炼之兆，不祥刷怪笼才会掉不祥钥匙");
        delay = 0;
    }
    private void endUseHold(String why) {
        if (!holdingUse) return;
        holdingUse = false;
        holdUseTicks = 0;
        PlayerAction.pressUse(false);
        FOElytraLog.detail("DRINK 松开右键（%s）", why);
    }
    private boolean hasOmen(PlayerEntity player) {
        RegistryEntry<StatusEffect> trial = StatusEffects.TRIAL_OMEN;
        RegistryEntry<StatusEffect> bad = StatusEffects.BAD_OMEN;
        return player.hasStatusEffect(trial) || player.hasStatusEffect(bad);
    }
    private void finish(MinecraftClient mc) {
        if (BaritoneHook.ready()) BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        endUseHold("收尾");
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        if (pendingFail) {
            status = TaskStatus.FAILED;
        } else {
            status = TaskStatus.DONE;
        }
        FOElytraLog.detail("FINISH 收尾完成：%s，共开 %d 个宝库，命中目标=%s",
            status, openedCount, foundTarget);
    }
    private void settleBeforeDone() {
        if (BaritoneHook.ready()) BaritoneHook.stop();
        endUseHold("进入终态");
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
    }
    private void next(State ns, int delayTicks) {
        State old = state;
        state = ns;
        delay = Math.max(0, delayTicks);
        FOElytraLog.detail("状态 %s → %s（delay=%d）", old, ns, delay);
    }
    private void notifyOpened(BlockPos pos) {
        if (onOpened == null) return;
        if (pos == null) return;
        try {
            onOpened.accept(pos.toImmutable());
        } catch (Throwable t) {
            FOElytraLog.err("已开宝库 %s 的回调（onOpened）出错：%s", shortPos(pos), String.valueOf(t));
            FOElytraLog.detailError("VaultOpener.onOpened(" + shortPos(pos) + ")", t);
        }
    }
    private void goal(State terminal) {
        State old = state;
        state = terminal;
        delay = 0;
        FOElytraLog.detail("状态 %s → %s（终态，不再发包）", old, terminal);
    }
    private void fail(String reason) {
        failReason = reason;
        lastMessage = reason;
        FOElytraLog.err("%s", reason);
        FOElytraLog.detail("判失败：%s（此时状态 %s，已开 %d 个宝库）", reason, state, openedCount);
        pendingFail = true;
        goal(State.FINISH);
    }
    private int waitForPlayerScreen() {
        screenWaitTicks++;
        if (screenWaitTicks > SCREEN_HOLD_MAX_TICKS) {
            fail("你一直开着界面（" + (SCREEN_HOLD_MAX_TICKS / 20) + " 秒），宝库流程已取消");
            return 0;
        }
        if (screenWaitTicks % SCREEN_WAIT_WARN_INTERVAL == 0) {
            FOElytraLog.warn("检测到你开着界面，宝库流程暂停中（关掉后会自动继续）");
        }
        return 10;
    }
    private int effectiveCollectTicks() {
        return Math.max(COLLECT_MIN_TICKS, opts.openWaitTicks());
    }
    private String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
    private String vaultHead() {
        if (targetVault == null) return "?";
        return targetVault.toShortString() + (targetOminous ? "（不祥宝库）" : "（普通宝库）");
    }
    private String baselineText() {
        if (itemCountBaseline.isEmpty()) return "（没有配置目标物品）";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : itemCountBaseline.entrySet()) {
            parts.add(e.getKey().getName().getString() + "=" + e.getValue());
        }
        return String.join("、", parts);
    }
}
