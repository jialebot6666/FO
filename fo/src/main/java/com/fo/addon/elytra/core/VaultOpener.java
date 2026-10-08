package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import com.fo.addon.elytra.core.PlayerAction;
import com.fo.addon.elytra.core.TaskStatus;
import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.Collections;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.enums.VaultState;
import net.minecraft.block.vault.VaultConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;

public final class VaultOpener {
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
    private final List<String> lootLog = new ArrayList<String>();
    private boolean foundTarget;
    private int openedCount;
    private final Set<BlockPos> openedPositions = new LinkedHashSet<BlockPos>();
    private int delay;
    private BlockPos targetVault;
    private boolean targetOminous;
    private int openAttempts;
    private boolean clickedThisVault;
    private int inactiveRecheckTicks;
    private int spawnedCount;
    private int collectTicks;
    private int screenWaitTicks;
    private final Set<ItemEntity> seenItems = Collections.newSetFromMap(new IdentityHashMap());
    private final Set<String> seenKeys = new HashSet<String>();
    private int sinceLastSpawn;
    private final Map<Item, Integer> itemCountBaseline = new LinkedHashMap<Item, Integer>();
    private int targetBookBaseline;
    private boolean targetBookPickup;
    private final Set<String> targetEvidence = new LinkedHashSet<String>();
    private int walkTicks;
    private int repathTicks;
    private int keyCheckFails;
    private double requiredOpenDistance;
    private int closeInAttempts;
    private final Map<BlockPos, Integer> unreachableFailures = new LinkedHashMap<BlockPos, Integer>();
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
    private final List<BlockPos> scanCandidates = new ArrayList<BlockPos>();
    private final Set<Long> scanCandidateKeys = new HashSet<Long>();
    private final Set<Long> filteredCandidateKeys = new LinkedHashSet<Long>();
    private final Set<Long> roundFilteredCandidateKeys = new LinkedHashSet<Long>();
    private int scanChunkX = Integer.MIN_VALUE;
    private int scanChunkZ = Integer.MIN_VALUE;
    private boolean scanChunkLoadedFlag;

    public VaultOpener(Options opts) {
        this.opts = opts;
        this.targetItems = VaultOpener.copyItems(opts.targetItems());
        this.targetEnchantments = VaultOpener.copyIds(opts.targetEnchantments());
        this.candidateFilter = opts.candidateFilter();
        this.onOpened = opts.onOpened();
    }

    private static List<Item> copyItems(List<Item> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        ArrayList<Item> out = new ArrayList<Item>(in.size());
        for (Item it : in) {
            if (it == null || out.contains(it)) continue;
            out.add(it);
        }
        return List.copyOf(out);
    }

    private static List<Identifier> copyIds(List<Identifier> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        ArrayList<Identifier> out = new ArrayList<Identifier>(in.size());
        for (Identifier id : in) {
            if (id == null || out.contains(id)) continue;
            out.add(id);
        }
        return List.copyOf(out);
    }

    public void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            this.fail("\u73a9\u5bb6/\u4e16\u754c\u4e3a\u7a7a\uff0c\u65e0\u6cd5\u5bfb\u627e\u5b9d\u5e93");
            return;
        }
        this.lootLog.clear();
        this.openedPositions.clear();
        this.foundTarget = false;
        this.openedCount = 0;
        this.failReason = "";
        this.lastMessage = "";
        this.delay = 0;
        this.targetVault = null;
        this.targetOminous = false;
        this.openAttempts = 0;
        this.clickedThisVault = false;
        this.inactiveRecheckTicks = 0;
        this.spawnedCount = 0;
        this.collectTicks = 0;
        this.sinceLastSpawn = 0;
        this.screenWaitTicks = 0;
        this.seenItems.clear();
        this.seenKeys.clear();
        this.targetEvidence.clear();
        this.itemCountBaseline.clear();
        this.targetBookBaseline = 0;
        this.targetBookPickup = false;
        this.filteredCandidateKeys.clear();
        this.resetScanState();
        this.walkTicks = 0;
        this.repathTicks = 0;
        this.keyCheckFails = 0;
        this.closeInAttempts = 0;
        this.requiredOpenDistance = this.opts.openDistance();
        this.unreachableFailures.clear();
        this.softSkipCount = 0;
        this.retryingSoftSkipped = false;
        this.drinkAttempts = 0;
        this.drinkWaitTicks = 0;
        this.holdingUse = false;
        this.holdUseTicks = 0;
        this.pendingFail = false;
        this.keyConfigWarned = false;
        this.chunkLoadWarned = false;
        this.lootBox = null;
        this.lootBoxVault = null;
        this.status = TaskStatus.RUNNING;
        this.logConfiguredTargets();
        if (this.opts.needOminous() && this.opts.drinkOminousBottle()) {
            this.next(State.DRINK, 0);
        } else {
            this.next(State.SCAN, 0);
        }
        FOElytraLog.info("\u5f00\u59cb\u627e\u5b9d\u5e93\uff1a\u534a\u5f84 %d\uff0c%s\uff0c\u6700\u591a\u5f00 %d \u4e2a\uff0c\u6536\u96c6\u65f6\u957f %d tick\uff08\u8bbe\u7f6e\u539f\u6587 %d\uff09", this.opts.searchRadius(), this.opts.needOminous() ? "\u53ea\u627e\u4e0d\u7965\u5b9d\u5e93" : "\u666e\u901a/\u4e0d\u7965\u90fd\u884c", this.opts.maxVaultsPerRun(), this.effectiveCollectTicks(), this.opts.openWaitTicks());
    }

    private void logConfiguredTargets() {
        if (this.targetItems.isEmpty() && this.targetEnchantments.isEmpty()) {
            FOElytraLog.warn("\u6ca1\u6709\u914d\u7f6e\u4efb\u4f55\u76ee\u6807\u6218\u5229\u54c1\uff08\u76ee\u6807\u7269\u54c1\u4e0e\u76ee\u6807\u9b54\u5492\u90fd\u662f\u7a7a\u7684\uff09\uff1a\u672c\u6b21\u53ea\u4f1a\u8bb0\u5f55\u6bcf\u4e2a\u5b9d\u5e93\u55b7\u4e86\u4ec0\u4e48\uff0c\u4e0d\u4f1a\u5224\u5b9a\u300c\u547d\u4e2d\u300d", new Object[0]);
            return;
        }
        ArrayList<String> names = new ArrayList<String>();
        for (Item it : this.targetItems) {
            names.add(it.getName().getString());
        }
        for (Identifier id : this.targetEnchantments) {
            names.add("\u9644\u9b54\u4e66[" + id.getPath() + "]");
        }
        FOElytraLog.detail("\u76ee\u6807\u6218\u5229\u54c1\uff1a%s\uff08\u7269\u54c1 %d \u79cd / \u9b54\u5492 %d \u4e2a\uff09", String.join((CharSequence)"\u3001", names), this.targetItems.size(), this.targetEnchantments.size());
    }

    public void abort(String reason) {
        if (BaritoneHook.ready()) {
            BaritoneHook.stop();
        }
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        this.holdingUse = false;
        this.holdUseTicks = 0;
        if (this.state != State.IDLE) {
            FOElytraLog.warn("\u5b9d\u5e93\u6d41\u7a0b\u4e2d\u6b62\uff1a%s", reason);
        }
        this.state = State.IDLE;
        this.status = TaskStatus.IDLE;
        this.delay = 0;
        this.targetVault = null;
        this.lootBox = null;
        this.lootBoxVault = null;
        this.resetScanState();
    }

    public TaskStatus status() {
        return this.status;
    }

    public State state() {
        return this.state;
    }

    public String progress() {
        StringBuilder sb = new StringBuilder(this.state.name());
        if (this.targetVault != null) {
            sb.append(' ').append(this.targetVault.toShortString()).append(this.targetOminous ? "(\u4e0d\u7965)" : "(\u666e\u901a)");
        }
        if (this.state == State.SCAN && this.scanActive) {
            sb.append(" \u626b\u63cf ").append(this.scanDone).append('/').append(this.scanTotal).append("\uff08\u5019\u9009 ").append(this.scanCandidates.size()).append(" \u4e2a\uff09");
        }
        if (this.status == TaskStatus.RUNNING) {
            sb.append(" \u5df2\u5f00 ").append(this.openedCount).append('/').append(this.opts.maxVaultsPerRun());
            if (this.state == State.COLLECT) {
                sb.append(" \u5df2\u89c1 ").append(this.spawnedCount).append(" \u4ef6");
            }
            if (this.state == State.OPEN) {
                sb.append(" \u53f3\u952e ").append(this.openAttempts).append('/').append(3);
                if (this.inactiveRecheckTicks > 0) {
                    sb.append(" \u7b49\u72b6\u6001\u5237\u65b0 ").append(this.inactiveRecheckTicks).append('/').append(30).append("\uff08INACTIVE\uff0c\u5148\u4e0d\u70b9\u51fb\uff09");
                }
            }
            if (this.state == State.WALK && this.requiredOpenDistance > 0.0 && this.requiredOpenDistance < this.opts.openDistance()) {
                sb.append(" \u8d70\u8fd1\u6fc0\u6d3b\u534a\u5f84 ").append(this.closeInAttempts).append('/').append(2);
            }
            if (this.softSkipCount > 0) {
                sb.append(" \u5df2\u8f6f\u8df3\u8fc7 ").append(this.unreachableFailures.size()).append(" \u4e2a\u8d70\u4e0d\u5230\u7684\u5e93");
                if (this.retryingSoftSkipped) {
                    sb.append("\uff08\u6b63\u5728\u518d\u8bd5\u4e00\u6b21\uff09");
                }
            }
            if (!this.filteredCandidateKeys.isEmpty()) {
                sb.append(" \u5df2\u8fc7\u6ee4 ").append(this.filteredCandidateKeys.size()).append(" \u4e2a\uff08\u6807\u8bb0/\u5c55\u793a\u7269/\u5237\u602a\u7b3c\uff09");
            }
        }
        if (!this.lastMessage.isEmpty()) {
            sb.append(" \u2014 ").append(this.lastMessage);
        }
        if (this.status == TaskStatus.FAILED && !this.failReason.isEmpty()) {
            sb.append("(").append(this.failReason).append(')');
        }
        return sb.toString();
    }

    public String failReason() {
        return this.failReason;
    }

    public String lastMessage() {
        return this.lastMessage;
    }

    public List<String> lootLog() {
        return Collections.unmodifiableList(this.lootLog);
    }

    public boolean foundTarget() {
        return this.foundTarget;
    }

    public int openedCount() {
        return this.openedCount;
    }

    public Set<BlockPos> openedPositions() {
        return this.openedPositions;
    }

    public void tick() {
        if (this.status != TaskStatus.RUNNING) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            this.fail("\u73a9\u5bb6/\u4e16\u754c\u4e3a\u7a7a");
            return;
        }
        if (this.delay > 0) {
            --this.delay;
            return;
        }
        try {
            this.step(mc);
        }
        catch (Throwable t) {
            FOElytraLog.err("\u5b9d\u5e93\u6d41\u7a0b\u5185\u90e8\u5f02\u5e38: %s", String.valueOf(t));
            FOElytraLog.detailError("VaultOpener.step", t);
            this.fail("\u5185\u90e8\u5f02\u5e38 " + t.getClass().getSimpleName());
        }
    }

    private void step(MinecraftClient mc) {
        switch (this.state.ordinal()) {
            case 1: {
                this.scan(mc);
                break;
            }
            case 2: {
                this.equip(mc);
                break;
            }
            case 3: {
                this.walk(mc);
                break;
            }
            case 4: {
                this.open(mc);
                break;
            }
            case 5: {
                this.collect(mc);
                break;
            }
            case 6: {
                this.next(mc);
                break;
            }
            case 7: {
                this.drink(mc);
                break;
            }
            case 8: {
                this.finish(mc);
                break;
            }
            case 9: {
                this.status = TaskStatus.DONE;
                break;
            }
            case 10: {
                this.status = TaskStatus.FAILED;
                break;
            }
            default: {
                this.status = TaskStatus.DONE;
            }
        }
    }

    private void scan(MinecraftClient mc) {
        this.drinkWaitTicks = 0;
        this.drinkAttempts = 0;
        this.endUseHold("\u8fdb\u5165\u626b\u63cf\u9636\u6bb5");
        if (!this.scanActive) {
            this.beginScan(mc);
        }
        ++this.scanTicks;
        int budget = 6000;
        while (budget > 0) {
            int x = this.scanOriginX + this.scanDx;
            int y = this.scanOriginY + this.scanDyRel;
            int z = this.scanOriginZ + this.scanDz;
            this.scanCursor.set(x, y, z);
            ++this.scanDone;
            --budget;
            if (!this.chunkLoaded(mc, x >> 4, z >> 4)) {
                ++this.scanSkippedUnloaded;
            } else {
                ++this.scanChecked;
                BlockState st = mc.world.getBlockState((BlockPos)this.scanCursor);
                if (st.isOf(Blocks.VAULT) && !this.openedPositions.contains(this.scanCursor)) {
                    BlockPos fixed;
                    boolean ominous = Boolean.TRUE.equals(st.get((Property)VaultBlock.OMINOUS));
                    if ((!this.opts.needOminous() || ominous) && !this.rejectedByFilter(fixed = this.scanCursor.toImmutable()) && this.scanCandidateKeys.add(fixed.asLong())) {
                        this.scanCandidates.add(fixed);
                    }
                }
            }
            if (this.scanAdvance()) continue;
            this.finishScan(mc);
            return;
        }
        if (this.scanTicks % 20 == 0) {
            FOElytraLog.detail("SCAN \u8fdb\u5ea6 %d/%d\uff08%.0f%%\uff09\u7b2c %d tick\uff1a\u5df2\u626b\u5df2\u52a0\u8f7d %d \u683c\u3001\u8df3\u8fc7\u672a\u52a0\u8f7d %d \u683c\uff0c\u5019\u9009\u5b9d\u5e93 %d \u4e2a", this.scanDone, this.scanTotal, 100.0 * (double)this.scanDone / (double)Math.max(1L, this.scanTotal), this.scanTicks, this.scanChecked, this.scanSkippedUnloaded, this.scanCandidates.size());
        }
        this.delay = 0;
    }

    private void beginScan(MinecraftClient mc) {
        int yMax;
        BlockPos origin = mc.player.getBlockPos().toImmutable();
        this.scanOriginX = origin.getX();
        this.scanOriginY = origin.getY();
        this.scanOriginZ = origin.getZ();
        this.scanRadius = Math.max(1, this.opts.searchRadius());
        int yMin = Math.max(-40, this.scanOriginY - this.scanRadius);
        if (yMin > (yMax = Math.min(16, this.scanOriginY + this.scanRadius))) {
            yMin = Math.max(-64, this.scanOriginY - 8);
            yMax = Math.min(319, this.scanOriginY + 8);
            FOElytraLog.warn("\u4f60\u73b0\u5728\u5728 Y=%d\uff0c\u4e0d\u5728\u8bd5\u70bc\u5bc6\u5ba4\u7684\u9ad8\u5ea6\u5e26\uff08%d~%d\uff09\u91cc\uff1a\u672c\u6b21\u53ea\u5728 Y=%d~%d \u8303\u56f4\u5185\u627e\u5b9d\u5e93", this.scanOriginY, -40, 16, yMin, yMax);
        }
        this.scanDyMin = yMin - this.scanOriginY;
        this.scanDyMax = yMax - this.scanOriginY;
        this.scanDyRel = this.scanDyMin;
        this.scanDz = -this.scanRadius;
        this.scanDx = -this.scanRadius;
        long width = 2L * (long)this.scanRadius + 1L;
        this.scanTotal = width * width * (long)(this.scanDyMax - this.scanDyMin + 1);
        this.scanDone = 0L;
        this.scanChecked = 0;
        this.scanSkippedUnloaded = 0;
        this.scanTicks = 0;
        this.scanCandidates.clear();
        this.scanCandidateKeys.clear();
        this.roundFilteredCandidateKeys.clear();
        this.scanChunkX = Integer.MIN_VALUE;
        this.scanChunkZ = Integer.MIN_VALUE;
        this.scanChunkLoadedFlag = false;
        this.scanActive = true;
        FOElytraLog.detail("SCAN \u5f00\u59cb\uff1a\u4e2d\u5fc3 %s\uff0c\u6c34\u5e73\u534a\u5f84 %d \u683c\uff0cY=%d~%d\uff0c\u5171 %d \u4e2a\u5750\u6807\uff08\u6bcf tick \u6700\u591a %d \u4e2a\uff0c\u8de8 tick \u7eed\u626b\uff1bY \u5e26\u4e0e\u533a\u5757\u52a0\u8f7d\u72b6\u6001\u90fd\u5148\u8fc7\u6ee4\uff09", origin.toShortString(), this.scanRadius, yMin, yMax, this.scanTotal, 6000);
    }

    private boolean scanAdvance() {
        ++this.scanDx;
        if (this.scanDx <= this.scanRadius) {
            return true;
        }
        this.scanDx = -this.scanRadius;
        ++this.scanDz;
        if (this.scanDz <= this.scanRadius) {
            return true;
        }
        this.scanDz = -this.scanRadius;
        ++this.scanDyRel;
        return this.scanDyRel <= this.scanDyMax;
    }

    private boolean chunkLoaded(MinecraftClient mc, int cx, int cz) {
        boolean loaded;
        block3: {
            if (cx == this.scanChunkX && cz == this.scanChunkZ) {
                return this.scanChunkLoadedFlag;
            }
            try {
                loaded = mc.world.isChunkLoaded(cx, cz);
            }
            catch (Throwable t) {
                loaded = false;
                if (this.chunkLoadWarned) break block3;
                this.chunkLoadWarned = true;
                FOElytraLog.detailError("VaultOpener.chunkLoaded(" + cx + "," + cz + ")", t);
            }
        }
        this.scanChunkX = cx;
        this.scanChunkZ = cz;
        this.scanChunkLoadedFlag = loaded;
        return loaded;
    }

    private boolean rejectedByFilter(BlockPos pos) {
        if (this.candidateFilter == null) {
            return false;
        }
        boolean allowed = false;
        try {
            allowed = this.candidateFilter.test(pos);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultOpener.candidateFilter(" + this.shortPos(pos) + ")", t);
            allowed = false;
        }
        if (allowed) {
            return false;
        }
        long key = pos.toImmutable().asLong();
        if (this.filteredCandidateKeys.add(key)) {
            FOElytraLog.detail("\u5019\u9009\u8fc7\u6ee4\uff1a\u8df3\u8fc7 %s\uff08\u4e0a\u5c42 candidateFilter \u62d2\u7edd \u2014\u2014 \u5df2\u6807\u8bb0 / \u5c55\u793a\u7269\u4e0d\u662f\u76ee\u6807 / \u79bb\u5237\u602a\u7b3c\u592a\u8fd1\uff09", this.shortPos(pos));
        }
        this.roundFilteredCandidateKeys.add(key);
        return true;
    }

    private void finishScan(MinecraftClient mc) {
        this.scanActive = false;
        BlockPos origin = mc.player.getBlockPos();
        FOElytraLog.detail("SCAN \u5b8c\u6210\uff1a\u5904\u7406 %d \u4e2a\u5750\u6807\uff08\u5df2\u52a0\u8f7d %d\u3001\u8df3\u8fc7\u672a\u52a0\u8f7d %d\uff09\uff0c\u5019\u9009\u5b9d\u5e93 %d \u4e2a\uff0c\u8017\u65f6\u7ea6 %d tick", this.scanDone, this.scanChecked, this.scanSkippedUnloaded, this.scanCandidates.size(), this.scanTicks);
        if (this.candidateFilter != null) {
            FOElytraLog.detail("\u5019\u9009\u8fc7\u6ee4\uff1a\u8df3\u8fc7 %d \u4e2a\uff08\u672c\u8f6e\uff1b\u7d2f\u8ba1 %d \u4e2a\u4e0d\u540c\u5750\u6807\uff09\u2014\u2014\u88ab\u8fc7\u6ee4 \u2260 \u627e\u4e0d\u5230\u5b9d\u5e93\uff1a\u5b83\u4eec\u4e0d\u8fdb openedPositions\u3001\u4e5f\u4e0d\u5360\u8f6f\u8df3\u8fc7\u989d\u5ea6", this.roundFilteredCandidateKeys.size(), this.filteredCandidateKeys.size());
        }
        if (this.scanCandidates.isEmpty()) {
            this.lastMessage = "\u534a\u5f84\u5185\u6ca1\u6709\u53ef\u5f00\u7684\u5b9d\u5e93";
            if (this.openedCount > 0) {
                FOElytraLog.warn("\u534a\u5f84 %d \u5185\u5df2\u7ecf\u6ca1\u6709\u6ca1\u5f00\u8fc7\u7684%s\u5b9d\u5e93\u4e86\uff08\u672c\u6b21\u5df2\u5f00 %d \u4e2a\uff09", this.scanRadius, this.opts.needOminous() ? "\u4e0d\u7965" : "", this.openedCount);
            } else {
                FOElytraLog.warn("%s\uff08\u534a\u5f84 %d\uff0c\u4f4d\u7f6e %s\uff09\u2014\u2014\u53ef\u80fd\u4e0d\u5728\u8bd5\u70bc\u5bc6\u5ba4\u91cc\uff0c\u6216\u6b64\u5904\u5b9d\u5e93\u5df2\u7ecf\u5168\u88ab\u5f00\u8fc7", this.lastMessage, this.scanRadius, origin.toShortString());
            }
            this.next(State.FINISH, 0);
            return;
        }
        this.scanCandidates.sort(Comparator.comparingDouble(p -> p.getSquaredDistance((Vec3i)origin)));
        BlockPos best = null;
        boolean bestOminous = false;
        int stale = 0;
        int softExcluded = 0;
        this.retryingSoftSkipped = false;
        for (BlockPos p2 : this.scanCandidates) {
            Integer fails;
            BlockState st = mc.world.getBlockState(p2);
            if (!st.isOf(Blocks.VAULT)) {
                ++stale;
                continue;
            }
            if (this.openedPositions.contains(p2) || this.rejectedByFilter(p2) || (fails = this.unreachableFailures.get(p2)) != null) continue;
            boolean ominous = Boolean.TRUE.equals(st.get((Property)VaultBlock.OMINOUS));
            if (this.opts.needOminous() && !ominous) continue;
            best = p2;
            bestOminous = ominous;
            break;
        }
        if (best == null) {
            int fewest = Integer.MAX_VALUE;
            for (BlockPos p3 : this.scanCandidates) {
                Integer fails;
                BlockState st = mc.world.getBlockState(p3);
                if (!st.isOf(Blocks.VAULT) || this.openedPositions.contains(p3) || this.rejectedByFilter(p3) || (fails = this.unreachableFailures.get(p3)) == null) continue;
                if (fails >= 2) {
                    ++softExcluded;
                    continue;
                }
                boolean ominous = Boolean.TRUE.equals(st.get((Property)VaultBlock.OMINOUS));
                if (this.opts.needOminous() && !ominous || fails >= fewest) continue;
                fewest = fails;
                best = p3;
                bestOminous = ominous;
            }
            if (best != null) {
                this.retryingSoftSkipped = true;
                FOElytraLog.warn("\u534a\u5f84 %d \u5185\u6ca1\u6709\u300c\u6ca1\u8bd5\u8fc7\u300d\u7684\u5b9d\u5e93\u4e86\uff0c\u56de\u53bb\u518d\u8bd5\u4e00\u6b21 %s\uff08\u5b83\u4e4b\u524d\u5df2\u7ecf\u8f6f\u8df3\u8fc7 %d \u6b21\uff09", this.scanRadius, best.toShortString(), fewest);
            } else if (softExcluded > 0) {
                FOElytraLog.warn("\u534a\u5f84 %d \u5185\u7684\u5b9d\u5e93\u90fd\u4e0d\u9002\u7528\uff1a%d \u4e2a\u8d70\u4e0d\u8fdb\u53bb\u7684\u5df2\u7528\u5b8c\u518d\u8bd5\u989d\u5ea6\uff08\u6bcf\u4e2a\u6700\u591a %d \u6b21\uff09", this.scanRadius, softExcluded, 2);
            }
        }
        if (best == null) {
            this.lastMessage = stale > 0 && softExcluded == 0 ? "\u5019\u9009\u5b9d\u5e93\u90fd\u5728\u626b\u63cf\u671f\u95f4\u6d88\u5931\u4e86" : "\u534a\u5f84\u5185\u6ca1\u6709\u53ef\u5f00\u7684\u5b9d\u5e93";
            FOElytraLog.warn("%s\uff08\u5019\u9009 %d \u4e2a\uff0c\u5176\u4e2d %d \u4e2a\u5df2\u7ecf\u4e0d\u662f\u5b9d\u5e93\u65b9\u5757\u3001%d \u4e2a\u8d70\u4e0d\u8fdb\u53bb\u4e14\u5df2\u7528\u5b8c\u518d\u8bd5\u989d\u5ea6\uff09", this.lastMessage, this.scanCandidates.size(), stale, softExcluded);
            this.next(State.FINISH, 0);
            return;
        }
        this.targetVault = best;
        this.targetOminous = bestOminous;
        this.openAttempts = 0;
        this.clickedThisVault = false;
        this.inactiveRecheckTicks = 0;
        this.spawnedCount = 0;
        this.collectTicks = 0;
        this.sinceLastSpawn = 0;
        this.targetEvidence.clear();
        this.walkTicks = 0;
        this.repathTicks = 0;
        this.keyCheckFails = 0;
        this.closeInAttempts = 0;
        this.requiredOpenDistance = this.opts.openDistance();
        this.lastMessage = "";
        FOElytraLog.detail("SCAN \u9009\u4e2d\u5b9d\u5e93 %s\uff08%s\uff0c\u8ddd\u79bb %.1f \u683c\uff0c\u5019\u9009 %d \u4e2a\u91cc\u6700\u8fd1%s\uff09", best.toShortString(), bestOminous ? "\u4e0d\u7965" : "\u666e\u901a", Math.sqrt(best.getSquaredDistance((Vec3i)origin)), this.scanCandidates.size(), this.retryingSoftSkipped ? "\uff0c\u4e14\u8fd9\u662f\u300c\u8f6f\u8df3\u8fc7\u518d\u8bd5\u4e00\u6b21\u300d\u7684\u5019\u9009" : "");
        this.next(State.EQUIP, 0);
    }

    private void resetScanState() {
        this.scanActive = false;
        this.scanRadius = 0;
        this.scanOriginX = 0;
        this.scanOriginY = 0;
        this.scanOriginZ = 0;
        this.scanDyMin = 0;
        this.scanDyMax = -1;
        this.scanDyRel = 0;
        this.scanDz = 0;
        this.scanDx = 0;
        this.scanTotal = 0L;
        this.scanDone = 0L;
        this.scanChecked = 0;
        this.scanSkippedUnloaded = 0;
        this.scanTicks = 0;
        this.scanCandidates.clear();
        this.scanCandidateKeys.clear();
        this.roundFilteredCandidateKeys.clear();
        this.scanChunkX = Integer.MIN_VALUE;
        this.scanChunkZ = Integer.MIN_VALUE;
        this.scanChunkLoadedFlag = false;
        this.scanCursor.set(0, 0, 0);
    }

    private void equip(MinecraftClient mc) {
        if (this.targetVault == null) {
            FOElytraLog.warn("EQUIP \u65f6\u5b9d\u5e93\u5750\u6807\u4e22\u5931\uff0c\u56de\u5230 SCAN \u91cd\u627e", new Object[0]);
            this.next(State.SCAN, 0);
            return;
        }
        BlockState st = mc.world.getBlockState(this.targetVault);
        if (!st.isOf(Blocks.VAULT)) {
            FOElytraLog.warn("\u5b9d\u5e93 %s \u5df2\u7ecf\u4e0d\u662f\u5b9d\u5e93\u65b9\u5757\u4e86\uff08\u88ab\u6316\u6389/\u88ab\u522b\u4eba\u5f00\u6389\uff09\uff0c\u6362\u4e0b\u4e00\u4e2a", this.targetVault.toShortString());
            this.next(State.NEXT, this.opts.actionDelay());
            return;
        }
        boolean ominous = Boolean.TRUE.equals(st.get((Property)VaultBlock.OMINOUS));
        if (ominous != this.targetOminous) {
            FOElytraLog.detail("\u5b9d\u5e93 %s \u7684\u4e0d\u7965\u72b6\u6001\u5728\u6d41\u7a0b\u4e2d\u53d1\u751f\u4e86\u53d8\u5316\uff08%s \u2192 %s\uff09\uff0c\u6309\u5f53\u524d\u72b6\u6001\u91cd\u65b0\u9009\u94a5\u5319", this.targetVault.toShortString(), this.targetOminous, ominous);
            this.targetOminous = ominous;
        }
        if (InvHelper.screenOpen()) {
            this.delay = this.waitForPlayerScreen();
            return;
        }
        Item key = this.requiredKey(mc);
        int hotbar = this.findKeyHotbar(key);
        if (hotbar >= 0) {
            InvHelper.selectSlot(hotbar);
            FOElytraLog.detail("EQUIP \u5feb\u6377\u680f\u7b2c %d \u683c\u5df2\u7ecf\u662f%s\uff0c\u76f4\u63a5\u9009\u4e2d\uff08\u624b\u6301\uff1a%s\uff09", hotbar, key.getName().getString(), mc.player.getInventory().getSelectedStack().getName().getString());
        } else {
            int slot = InvHelper.findSlot(s -> s.isOf(key));
            if (slot < 0) {
                FOElytraLog.detail("EQUIP \u80cc\u5305 0-35 \u91cc\u6ca1\u6709\u4efb\u4f55 %s", key.getName().getString());
                if (this.targetOminous) {
                    this.fail("\u80cc\u5305\u91cc\u6ca1\u6709\u4e0d\u7965\u8bd5\u70bc\u94a5\u5319\uff08\u4e0d\u7965\u94a5\u5319\u6765\u81ea\u4e0d\u7965\u8bd5\u70bc\u5237\u602a\u7b3c\uff09");
                } else {
                    this.fail("\u80cc\u5305\u91cc\u6ca1\u6709\u8bd5\u70bc\u94a5\u5319\uff08\u666e\u901a\u94a5\u5319\u6765\u81ea\u8bd5\u70bc\u5237\u602a\u7b3c\uff09");
                }
                return;
            }
            int empty = InvHelper.findEmptyHotbarSlot();
            if (empty < 0) {
                empty = this.findSacrificialHotbarSlot(mc);
                if (empty < 0) {
                    FOElytraLog.detail("EQUIP \u5feb\u6377\u680f 0-8 \u5168\u662f %s\uff0c\u6ca1\u6709\u53ef\u6362\u7684\u683c\u5b50", key.getName().getString());
                    this.fail("\u5feb\u6377\u680f\u6ca1\u6709\u7a7a\u4f4d\u53ef\u4ee5\u653e " + key.getName().getString());
                    return;
                }
                FOElytraLog.detail("EQUIP \u5feb\u6377\u680f\u5df2\u6ee1\uff0c\u5360\u7528\u7b2c %d \u683c\uff08\u539f\u7269\u54c1\uff1a%s\uff09", empty, mc.player.getInventory().getStack(empty).getName().getString());
            }
            InvHelper.moveInvToHotbar(slot, empty);
            InvHelper.selectSlot(empty);
            FOElytraLog.detail("EQUIP \u80cc\u5305\u7b2c %d \u683c \u2192 \u5feb\u6377\u680f\u7b2c %d \u683c\uff0c\u5df2\u9009\u4e2d\u624b\u6301 %s", slot, empty, mc.player.getInventory().getSelectedStack().getName().getString());
        }
        ItemStack held = mc.player.getInventory().getSelectedStack();
        if (!this.keyMatches(held, key, mc)) {
            ++this.keyCheckFails;
            String heldName = held.isEmpty() ? "\u7a7a" : held.getName().getString();
            FOElytraLog.detail("EQUIP \u6821\u9a8c\u5931\u8d25\uff08\u7b2c %d \u6b21\uff0c\u4e0a\u9650 %d \u6b21\uff09\uff1a\u624b\u6301\u662f %s\uff0c\u671f\u671b %s\uff08\u53ef\u80fd\u56e0\u4e3a\u754c\u9762\u6253\u5f00/\u540c\u6b65\u5ef6\u8fdf\uff09", this.keyCheckFails, 3, heldName, key.getName().getString());
            if (this.keyCheckFails > 3) {
                this.fail("\u94a5\u5319\u6362\u5230\u624b\u4e0a\u5931\u8d25\u8d85\u8fc7 3 \u6b21\uff08\u7b2c " + this.keyCheckFails + " \u6b21\u4ecd\u4e0d\u5bf9\uff0c\u624b\u6301\u662f " + heldName + "\uff0c\u671f\u671b " + key.getName().getString() + "\uff09\u2014\u2014\u8bf7\u786e\u8ba4\u80cc\u5305\u91cc\u6709\u8fd9\u628a\u94a5\u5319\uff0c\u5e76\u4e14\u6ca1\u6709\u522b\u7684\u6a21\u5757\u4e00\u76f4\u5728\u62a2\u624b\u6301\u683c");
                return;
            }
            this.next(State.EQUIP, this.opts.actionDelay());
            return;
        }
        this.next(State.WALK, this.opts.actionDelay());
    }

    private Item requiredKey(MinecraftClient mc) {
        block4: {
            if (this.targetVault != null) {
                try {
                    VaultBlockEntity vault;
                    VaultConfig cfg;
                    BlockEntity blockEntity2 = mc.world.getBlockEntity(this.targetVault);
                    if (blockEntity2 instanceof VaultBlockEntity && (cfg = (vault = (VaultBlockEntity)blockEntity2).getConfig()) != null && cfg.keyItem() != null && !cfg.keyItem().isEmpty()) {
                        return cfg.keyItem().getItem();
                    }
                }
                catch (Throwable t) {
                    if (this.keyConfigWarned) break block4;
                    this.keyConfigWarned = true;
                    FOElytraLog.detail("\u8bfb\u4e0d\u5230\u5b9d\u5e93\u65b9\u5757\u5b9e\u4f53\u7684 config.key_item\uff0c\u9000\u56de\u6309 OMINOUS \u72b6\u6001\u5224\u65ad\uff1a%s", String.valueOf(t));
                }
            }
        }
        return this.targetOminous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;
    }

    private boolean keyMatches(ItemStack held, Item required, MinecraftClient mc) {
        if (held == null || held.isEmpty() || required == null) {
            return false;
        }
        if (!held.isOf(required)) {
            return false;
        }
        ItemStack need = this.configuredKeyStack(required, mc);
        if (need == null) {
            return true;
        }
        return ItemStack.areItemsAndComponentsEqual((ItemStack)held, (ItemStack)need) && held.getCount() >= Math.max(1, need.getCount());
    }

    private ItemStack configuredKeyStack(Item required, MinecraftClient mc) {
        if (this.targetVault == null) {
            return null;
        }
        try {
            VaultBlockEntity vault;
            VaultConfig cfg;
            BlockEntity blockEntity2 = mc.world.getBlockEntity(this.targetVault);
            if (blockEntity2 instanceof VaultBlockEntity && (cfg = (vault = (VaultBlockEntity)blockEntity2).getConfig()) != null && cfg.keyItem() != null && cfg.keyItem().isOf(required)) {
                return cfg.keyItem();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private int findKeyHotbar(Item key) {
        return InvHelper.findSlot(s -> s.isOf(key), 0, 9);
    }

    private int findSacrificialHotbarSlot(MinecraftClient mc) {
        ItemStack s;
        int i;
        PlayerInventory inv = mc.player.getInventory();
        for (i = 0; i < 9; ++i) {
            s = inv.getStack(i);
            if (!s.isEmpty()) continue;
            return i;
        }
        for (i = 0; i < 9; ++i) {
            s = inv.getStack(i);
            if (s.isOf(Items.TRIAL_KEY) || s.isOf(Items.OMINOUS_TRIAL_KEY) || s.isOf(Items.TOTEM_OF_UNDYING) || ItemHelper.isFood(s)) continue;
            return i;
        }
        return -1;
    }

    private void walk(MinecraftClient mc) {
        if (this.targetVault == null) {
            this.next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            this.delay = this.waitForPlayerScreen();
            return;
        }
        double d = this.horizontalDistance(mc, this.targetVault);
        if (this.walkTicks++ >= 600) {
            FOElytraLog.warn("\u8d70\u5230\u5b9d\u5e93 %s \u8d85\u65f6\uff08%d \u79d2\uff0c\u6c34\u5e73\u8ddd\u79bb\u8fd8\u6709 %.1f \u683c\uff09", this.targetVault.toShortString(), 30, d);
            if (BaritoneHook.ready()) {
                BaritoneHook.stop();
            }
            this.softSkipCurrentVault("\u8d70\u5230\u5b83\u8d85\u65f6\uff0830 \u79d2\u8fd8\u6ca1\u8fdb\u5230\u4f4d\uff09");
            return;
        }
        if (d <= this.effectiveOpenDistance()) {
            FOElytraLog.detail("WALK \u5230\u4f4d\uff1a\u6c34\u5e73\u8ddd\u79bb %.2f \u2264 %.2f\uff08\u7528\u4e86 %d tick\uff09\uff0c\u51c6\u5907\u5f00 %s", d, this.effectiveOpenDistance(), this.walkTicks, this.targetVault.toShortString());
            if (BaritoneHook.ready()) {
                BaritoneHook.stop();
            }
            this.walkTicks = 0;
            this.next(State.OPEN, 0);
            return;
        }
        if (!this.opts.useBaritoneWalk()) {
            this.delay = 0;
            return;
        }
        if (!BaritoneHook.ready()) {
            this.fail("Baritone \u4e0d\u53ef\u7528\uff0c\u65e0\u6cd5\u8d70\u5230\u5b9d\u5e93\uff08\u8bf7\u5b89\u88c5 Baritone\uff0c\u6216\u628a\u624b\u52a8\u8d70\u8fc7\u53bb\u540e\u4f1a\u81ea\u52a8\u5f00\uff09");
            return;
        }
        if (this.repathTicks <= 0) {
            BaritoneHook.command("goto " + this.targetVault.getX() + " " + this.targetVault.getY() + " " + this.targetVault.getZ());
            this.repathTicks = 100;
            FOElytraLog.detail("WALK \u4e0b\u53d1 goto %d %d %d\uff08\u6c34\u5e73\u8ddd\u79bb %.1f\uff0c\u5df2\u8d70 %d tick\uff09", this.targetVault.getX(), this.targetVault.getY(), this.targetVault.getZ(), d, this.walkTicks);
            this.next(State.WALK, 5);
            return;
        }
        --this.repathTicks;
        this.delay = 0;
    }

    private double horizontalDistance(MinecraftClient mc, BlockPos pos) {
        double dx = mc.player.getX() - ((double)pos.getX() + 0.5);
        double dz = mc.player.getZ() - ((double)pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double effectiveOpenDistance() {
        return this.requiredOpenDistance > 0.0 ? this.requiredOpenDistance : this.opts.openDistance();
    }

    private double distanceToVaultCenter(MinecraftClient mc, BlockPos pos) {
        double dx = mc.player.getX() - ((double)pos.getX() + 0.5);
        double dy = mc.player.getY() - ((double)pos.getY() + 0.5);
        double dz = mc.player.getZ() - ((double)pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void open(MinecraftClient mc) {
        if (this.targetVault == null) {
            this.next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            this.delay = this.waitForPlayerScreen();
            return;
        }
        BlockState st = mc.world.getBlockState(this.targetVault);
        if (!st.isOf(Blocks.VAULT)) {
            FOElytraLog.warn("OPEN \u65f6\u5b9d\u5e93 %s \u5df2\u7ecf\u4e0d\u662f\u5b9d\u5e93\u65b9\u5757\u4e86\uff0c\u6362\u4e0b\u4e00\u4e2a", this.targetVault.toShortString());
            this.next(State.NEXT, this.opts.actionDelay());
            return;
        }
        VaultState vaultState = (VaultState)st.get(VaultBlock.VAULT_STATE);
        if (vaultState == VaultState.INACTIVE) {
            double dist = this.distanceToVaultCenter(mc, this.targetVault);
            if (dist > 4.0) {
                if (this.closeInAttempts >= 2) {
                    FOElytraLog.warn("\u5b9d\u5e93 %s \u8fdb\u4e0d\u53bb\u5b83\u7684\u6fc0\u6d3b\u534a\u5f84\uff083D \u8ddd\u79bb %.2f > %.1f\uff0c\u5df2\u5c1d\u8bd5\u8d70\u8fd1 %d \u6b21\uff09", this.targetVault.toShortString(), dist, 4.0, this.closeInAttempts);
                    this.softSkipCurrentVault("\u8d70\u5230\u4f4d\u4e86\u4f46 3D \u8ddd\u79bb " + String.format("%.2f", dist) + " \u4ecd\u5728\u6fc0\u6d3b\u534a\u5f84 " + String.format("%.1f", 4.0) + " \u4e4b\u5916");
                    return;
                }
                ++this.closeInAttempts;
                this.requiredOpenDistance = 2.0;
                this.inactiveRecheckTicks = 0;
                this.walkTicks = 0;
                FOElytraLog.detail("\u5b9d\u5e93 %s \u8bfb\u5230 INACTIVE \u4f46\u5148\u4e0d\u91c7\u4fe1\uff1a3D \u8ddd\u79bb %.2f > \u6fc0\u6d3b\u534a\u5f84 %.1f\uff08WALK \u7684\u5230\u4f4d\u5224\u5b9a\u53ea\u770b\u6c34\u5e73\u8ddd\u79bb\uff0c\u4e0a\u4e00\u5c42/\u4e0b\u4e00\u5c42\u5c31\u4f1a\u8fd9\u6837\uff09\u2192 \u56de WALK \u8d70\u5230 %.1f \u683c\u5185\u518d\u8bd5\uff08\u7b2c %d/%d \u6b21\uff09", this.targetVault.toShortString(), dist, 4.0, 2.0, this.closeInAttempts, 2);
                if (BaritoneHook.ready()) {
                    BaritoneHook.stop();
                }
                this.next(State.WALK, this.opts.actionDelay());
                return;
            }
            if (this.inactiveRecheckTicks < 30) {
                ++this.inactiveRecheckTicks;
                FOElytraLog.detail("INACTIVE \u590d\u6838\u4e2d %d/%d tick\uff08\u670d\u52a1\u7aef\u72b6\u6001\u6700\u591a\u6ede\u540e %d tick\uff0c\u5148\u4e0d\u70b9\u51fb\uff09\uff1a\u5b9d\u5e93 %s\uff0c3D \u8ddd\u79bb %.2f \u2264 %.1f", this.inactiveRecheckTicks, 30, 30, this.targetVault.toShortString(), dist, 4.0);
                this.next(State.OPEN, 0);
                return;
            }
            FOElytraLog.warn("\u5b9d\u5e93 %s \u5904\u4e8e INACTIVE\uff08\u7b49\u4e86 %d tick \u4e5f\u6ca1\u53d8\u6210 ACTIVE\uff0c\u800c\u4e14\u6211\u786e\u5b9e\u5728\u5b83\u7684\u6fc0\u6d3b\u534a\u5f84\u5185\uff1a3D \u8ddd\u79bb %.2f \u2264 %.1f\uff09= \u8fd9\u4e2a\u5b9d\u5e93\u5df2\u7ecf\u7ed9\u6211\u53d1\u8fc7\u5956\u52b1\uff0c\u6216\u6570\u636e\u5305\u6539\u4e86\u5b83\u7684 key_item\u3002\u4e0d\u6d6a\u8d39\u94a5\u5319\uff0c\u6362\u4e0b\u4e00\u4e2a", this.targetVault.toShortString(), 30, dist, 4.0);
            this.next(State.NEXT, this.opts.actionDelay());
            return;
        }
        int recheckWaited = this.inactiveRecheckTicks;
        this.inactiveRecheckTicks = 0;
        if (vaultState == VaultState.UNLOCKING || vaultState == VaultState.EJECTING) {
            FOElytraLog.warn("\u5b9d\u5e93 %s \u5df2\u7ecf\u5728 %s\uff08\u6b63\u5728\u89e3\u9501 / \u6b63\u5728\u55b7\u6218\u5229\u54c1\uff09\uff0c\u522b\u91cd\u590d\u5f00\uff0c\u6362\u4e0b\u4e00\u4e2a", this.targetVault.toShortString(), vaultState);
            this.next(State.NEXT, this.opts.actionDelay());
            return;
        }
        if (recheckWaited > 0) {
            FOElytraLog.detail("\u72b6\u6001\u5237\u65b0\u4e3a ACTIVE\uff0c\u7ee7\u7eed\u5f00\u5b9d\u5e93 %s\uff08INACTIVE \u590d\u6838\u7b49\u4e86 %d tick\uff0c\u4e4b\u524d\u4e0d\u70b9\u51fb\u662f\u5bf9\u7684\uff09", this.targetVault.toShortString(), recheckWaited);
        }
        Item key = this.requiredKey(mc);
        ItemStack held = mc.player.getInventory().getSelectedStack();
        if (!this.keyMatches(held, key, mc)) {
            ++this.keyCheckFails;
            String heldName = held.isEmpty() ? "\u7a7a" : held.getName().getString();
            FOElytraLog.detail("OPEN \u524d\u53d1\u73b0\u624b\u6301\u662f %s\uff0c\u4e0d\u662f %s\uff0c\u56de EQUIP \u91cd\u6362\uff08\u7d2f\u8ba1\u7b2c %d \u6b21\u6362\u4e0d\u4e0a\u624b\uff0c\u4e0a\u9650 %d \u6b21\uff09", heldName, key.getName().getString(), this.keyCheckFails, 3);
            if (this.keyCheckFails > 3) {
                this.fail("\u94a5\u5319\u6362\u5230\u624b\u4e0a\u5931\u8d25\u8d85\u8fc7 3 \u6b21\uff08\u7b2c " + this.keyCheckFails + " \u6b21 OPEN \u524d\u624b\u6301\u662f " + heldName + "\uff0c\u671f\u671b " + key.getName().getString() + "\uff09\u2014\u2014\u53ef\u80fd\u6709\u522b\u7684\u6a21\u5757\u4e00\u76f4\u5728\u62a2\u624b\u6301\u683c");
                return;
            }
            this.next(State.EQUIP, this.opts.actionDelay());
            return;
        }
        boolean accepted = InvHelper.interactBlock(this.targetVault);
        ++this.openAttempts;
        FOElytraLog.detail("OPEN \u7b2c %d/%d \u6b21\u53f3\u952e %s\uff08%s\uff0c\u65b9\u5757\u72b6\u6001 %s\uff0c\u624b\u6301 %s\uff09\uff0cinteractBlock=%s", this.openAttempts, 3, this.targetVault.toShortString(), this.targetOminous ? "\u4e0d\u7965\u5b9d\u5e93" : "\u666e\u901a\u5b9d\u5e93", vaultState, held.getName().getString(), accepted);
        if (!accepted) {
            if (this.openAttempts >= 3) {
                FOElytraLog.warn("\u5b9d\u5e93 %s \u53f3\u952e %d \u6b21\u90fd\u6ca1\u88ab\u63a5\u53d7\uff08\u89c6\u7ebf\u88ab\u6321\uff1f\u8ddd\u79bb\u592a\u8fdc\uff1f\u670d\u52a1\u5668\u6ca1\u6279\u51c6\uff09\uff0c\u6362\u4e0b\u4e00\u4e2a", this.targetVault.toShortString(), this.openAttempts);
                this.next(State.NEXT, this.opts.actionDelay());
                return;
            }
            FOElytraLog.detail("OPEN \u7b2c %d \u6b21\u53f3\u952e\u88ab\u62d2\uff0c\u7559\u5728 OPEN \u91cd\u8bd5\uff08\u540c\u4e00\u72b6\u6001\u81ea\u8f6c + delay \u8282\u6d41\uff0c\u4e0a\u9650 %d \u6b21\uff09", this.openAttempts, 3);
            this.next(State.OPEN, this.opts.actionDelay());
            return;
        }
        this.clickedThisVault = true;
        this.lootBox = new Box(this.targetVault).expand(3.0);
        this.lootBoxVault = this.targetVault.toImmutable();
        this.spawnedCount = 0;
        this.collectTicks = 0;
        this.sinceLastSpawn = 0;
        this.targetEvidence.clear();
        this.snapshotTargetCounts((PlayerEntity)mc.player);
        this.next(State.COLLECT, 1);
    }

    private void collect(MinecraftClient mc) {
        if (this.targetVault == null) {
            this.next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            this.delay = this.waitForPlayerScreen();
            return;
        }
        this.scanLootEntities(mc);
        this.updateTargetCounts((PlayerEntity)mc.player);
        ++this.collectTicks;
        ++this.sinceLastSpawn;
        int wait = this.effectiveCollectTicks();
        if (this.collectTicks == 1 && wait != this.opts.openWaitTicks()) {
            FOElytraLog.detail("COLLECT \u5f00\u59cb\uff1a\u7b49\u5f85\u55b7\u51fa tick \u539f\u6587 %d / \u5b9e\u9645 %d\uff08\u4f4e\u4e8e\u4e0b\u9650 %d\uff0c\u6309\u55b7\u51fa\u8282\u594f\u62ac\u5230\u4e0b\u9650\uff09", this.opts.openWaitTicks(), wait, 90);
        }
        if (this.matchAnyTarget()) {
            this.onTargetFound(this.evidenceText());
            return;
        }
        if (this.spawnedCount > 0 && this.sinceLastSpawn >= 30) {
            FOElytraLog.detail("COLLECT \u63d0\u524d\u6536\u5de5\uff1a\u5df2\u89c1 %d \u4ef6\u3001\u6700\u540e\u4e00\u4e2a\u4e4b\u540e %d tick \u6ca1\u6709\u518d\u51fa\u73b0\uff08\u5224\u636e %d tick\uff09", this.spawnedCount, this.sinceLastSpawn, 30);
            this.onCollectFinished();
            return;
        }
        if (this.collectTicks >= wait) {
            this.onCollectFinished();
            return;
        }
        this.delay = 0;
    }

    private void scanLootEntities(MinecraftClient mc) {
        if (this.lootBox == null || this.targetVault == null || !this.targetVault.equals((Object)this.lootBoxVault)) {
            this.lootBox = new Box(this.targetVault).expand(3.0);
            this.lootBoxVault = this.targetVault.toImmutable();
            FOElytraLog.detail("COLLECT \u91cd\u5efa\u6536\u96c6\u76d2\uff1a\u4ee5\u5b9d\u5e93 %s \u4e3a\u4e2d\u5fc3\u3001\u534a\u5f84 %.1f\uff08\u6362\u5e93\u5fc5\u987b\u91cd\u5efa\uff0c\u5426\u5219\u5b9e\u4f53\u8bc1\u636e\u4f1a\u843d\u5728\u4e0a\u4e00\u4e2a\u5e93\u7684\u5750\u6807\u4e0a\uff09", this.targetVault.toShortString(), 3.0);
        }
        List<ItemEntity> ents = mc.world.getEntitiesByClass(ItemEntity.class, this.lootBox, e -> e != null && e.isAlive() && !e.getStack().isEmpty());
        for (ItemEntity e2 : ents) {
            if (!this.seenItems.add(e2)) continue;
            ItemStack stack = e2.getStack();
            String name = stack.getItem().getName().getString();
            String key = this.shortPos(e2.getBlockPos()) + "#" + name;
            if (this.collectTicks >= 40 && !this.seenKeys.add(key)) continue;
            ++this.spawnedCount;
            this.sinceLastSpawn = 0;
            boolean target = this.isTargetStack(stack);
            String shown = target ? this.describeTargetStack(stack) : name;
            FOElytraLog.detail("COLLECT \u7b2c %d \u4ef6\uff1a%s x%d\uff08\u5b9e\u4f53 id=%d\uff0c\u4f4d\u7f6e %s\uff0c\u547d\u4e2d\u76ee\u6807=%s\uff09", this.spawnedCount, shown, stack.getCount(), e2.getId(), this.shortPos(e2.getBlockPos()), target);
            this.lootLog.add("\u6389\u843d\uff1a" + shown + " x" + stack.getCount() + "\uff08\u5b9d\u5e93 " + this.vaultHead() + "\uff09");
            if (!target) continue;
            this.targetEvidence.add(shown);
        }
    }

    private void snapshotTargetCounts(PlayerEntity player) {
        this.itemCountBaseline.clear();
        if (player != null) {
            for (Item it : this.targetItems) {
                this.itemCountBaseline.put(it, ItemHelper.countInInventory(player, it));
            }
        }
        this.targetBookBaseline = this.countTargetBooks(player);
        this.targetBookPickup = false;
        FOElytraLog.detail("COLLECT \u80cc\u5305\u57fa\u7ebf\uff1a%s\uff1b\u5e26\u76ee\u6807\u9b54\u5492\u7684\u9644\u9b54\u4e66 %d \u672c\uff08\u76ee\u6807\u7269\u54c1 %d \u79cd / \u76ee\u6807\u9b54\u5492 %d \u4e2a\uff09", this.baselineText(), this.targetBookBaseline, this.targetItems.size(), this.targetEnchantments.size());
    }

    private void updateTargetCounts(PlayerEntity player) {
        if (player == null) {
            return;
        }
        for (Item it : this.targetItems) {
            int now = ItemHelper.countInInventory(player, it);
            Integer before = this.itemCountBaseline.get(it);
            if (before == null) {
                this.itemCountBaseline.put(it, now);
                continue;
            }
            if (now <= before) continue;
            this.itemCountBaseline.put(it, now);
            this.targetEvidence.add(it.getName().getString() + "\uff08\u5df2\u6361\u8fdb\u80cc\u5305\uff09");
        }
        int books = this.countTargetBooks(player);
        if (books > this.targetBookBaseline) {
            this.targetBookBaseline = books;
            this.targetBookPickup = true;
            this.targetEvidence.add(this.describeTargetBooks(player));
        }
    }

    private int countTargetBooks(PlayerEntity player) {
        if (player == null || this.targetEnchantments.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(Items.ENCHANTED_BOOK) || !this.matchesTargetEnchantment(s)) continue;
            total += s.getCount();
        }
        return total;
    }

    private boolean matchAnyTarget() {
        return !this.targetEvidence.isEmpty();
    }

    private boolean isTargetStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!this.targetItems.isEmpty() && this.targetItems.contains(stack.getItem())) {
            return true;
        }
        return stack.isOf(Items.ENCHANTED_BOOK) && this.matchesTargetEnchantment(stack);
    }

    private boolean matchesTargetEnchantment(ItemStack stack) {
        if (this.targetEnchantments.isEmpty()) {
            return false;
        }
        ItemEnchantmentsComponent stored = (ItemEnchantmentsComponent)stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        for (RegistryEntry entry : stored.getEnchantments()) {
            for (Identifier id : this.targetEnchantments) {
                if (!entry.matchesId(id)) continue;
                return true;
            }
        }
        return false;
    }

    private List<String> matchingEnchantmentNames(ItemStack stack) {
        ArrayList<String> out = new ArrayList<String>();
        if (this.targetEnchantments.isEmpty()) {
            return out;
        }
        ItemEnchantmentsComponent stored = (ItemEnchantmentsComponent)stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return out;
        }
        block0: for (RegistryEntry entry : stored.getEnchantments()) {
            for (Identifier id : this.targetEnchantments) {
                if (!entry.matchesId(id)) continue;
                String n = this.enchantDisplayName((RegistryEntry<Enchantment>)entry, id);
                if (out.contains(n)) continue block0;
                out.add(n);
                continue block0;
            }
        }
        return out;
    }

    private String enchantDisplayName(RegistryEntry<Enchantment> entry, Identifier matchedId) {
        try {
            String s;
            Text text = ((Enchantment)entry.value()).description();
            if (text != null && (s = text.getString()) != null && !s.isBlank()) {
                return s;
            }
        }
        catch (Throwable t) {
            FOElytraLog.detail("\u8bfb\u9b54\u5492\u540d\u5b57\u5931\u8d25\uff08\u7528 ID \u4ee3\u66ff\u663e\u793a\uff09\uff1a%s", String.valueOf(t));
        }
        return matchedId.getPath();
    }

    private String describeTargetStack(ItemStack stack) {
        String base = stack.getItem().getName().getString();
        if (!stack.isOf(Items.ENCHANTED_BOOK)) {
            return base;
        }
        List<String> names = this.matchingEnchantmentNames(stack);
        return names.isEmpty() ? base : base + "[" + String.join((CharSequence)"\u3001", names) + "]";
    }

    private String describeTargetBooks(PlayerEntity player) {
        String base = Items.ENCHANTED_BOOK.getName().getString();
        ArrayList<String> names = new ArrayList<String>();
        if (player != null) {
            for (int i = 0; i < 36; ++i) {
                ItemStack s = player.getInventory().getStack(i);
                if (s.isEmpty() || !s.isOf(Items.ENCHANTED_BOOK)) continue;
                for (String n : this.matchingEnchantmentNames(s)) {
                    if (names.contains(n)) continue;
                    names.add(n);
                }
            }
        }
        return names.isEmpty() ? base + "\uff08\u5e26\u76ee\u6807\u9b54\u5492\uff09" : base + "[" + String.join((CharSequence)"\u3001", names) + "]";
    }

    private String evidenceText() {
        return this.targetEvidence.isEmpty() ? "\u76ee\u6807\u6218\u5229\u54c1" : String.join((CharSequence)"\u3001", this.targetEvidence);
    }

    private void onTargetFound(String what) {
        this.foundTarget = true;
        String line = "\u547d\u4e2d\uff1a\u51fa\u4e86 " + what + "\uff08\u5b9d\u5e93 " + this.vaultHead() + "\uff09";
        this.lootLog.add(line);
        FOElytraLog.info("\u547d\u4e2d\u76ee\u6807\uff01%s", line);
        FOElytraLog.detail("COLLECT \u5224\u5b9a\u5b8c\u6210\uff1a\u547d\u4e2d %s\uff08\u5b9e\u4f53 %d \u4ef6\uff1b\u8bc1\u636e\uff1a%s\uff1b\u76ee\u6807\u9b54\u5492\u4e66=%s\uff09", what, this.spawnedCount, String.join((CharSequence)"\u3001", this.targetEvidence), this.targetBookPickup);
        if (this.opts.stopOnTarget()) {
            this.lastMessage = "\u5df2\u62ff\u5230\u76ee\u6807\u6218\u5229\u54c1\uff1a" + what;
            this.settleBeforeDone();
            this.goal(State.DONE);
        } else {
            FOElytraLog.tip("\u5df2\u62ff\u5230 %s\uff0c\u6309\u8bbe\u7f6e\u7ee7\u7eed\u5f00\u4e0b\u4e00\u4e2a\u5b9d\u5e93", what);
            this.next(State.NEXT, this.opts.actionDelay());
        }
    }

    private void onCollectFinished() {
        MinecraftClient mc = MinecraftClient.getInstance();
        this.updateTargetCounts((PlayerEntity)mc.player);
        if (this.matchAnyTarget()) {
            this.onTargetFound(this.evidenceText());
            return;
        }
        String head = "\u5b9d\u5e93 " + this.vaultHead();
        if (this.spawnedCount > 0) {
            this.lootLog.add("\u4e0d\u662f\u76ee\u6807\uff1a" + head + " \u55b7\u51fa " + this.spawnedCount + " \u4ef6\uff0c\u90fd\u4e0d\u662f\u8981\u7684\u4e1c\u897f");
            FOElytraLog.tip("%s \u51fa\u7684\u4e0d\u662f\u76ee\u6807\uff08%d \u4ef6\uff09\uff0c\u6362\u4e0b\u4e00\u4e2a\u5b9d\u5e93", head, this.spawnedCount);
        } else {
            this.lootLog.add("\u7a7a\u5f00\uff1a" + head + " " + this.collectTicks + " tick \u5185\u6ca1\u6709\u55b7\u51fa\u4efb\u4f55\u7269\u54c1");
            FOElytraLog.warn("%s \u7a7a\u5f00\uff08%d tick \u5185\u6ca1\u55b7\u51fa\u4efb\u4f55\u7269\u54c1\uff09\u2014\u2014\u53ef\u80fd\u4f60\u4e4b\u524d\u5df2\u7ecf\u5f00\u8fc7\u5b83\u3001\u94a5\u5319\u6ca1\u88ab\u670d\u52a1\u7aef\u63a5\u53d7\u3001\u6216\u8005\u7ad9\u7740\u7684\u4f4d\u7f6e\u79bb\u5f97\u592a\u8fdc\uff1b\u4e5f\u53ef\u80fd\u662f\u6218\u5229\u54c1\u8fd8\u6ca1\u6765\u5f97\u53ca\u55b7\u5b8c\uff08\u628a\u300c\u7b49\u5f85\u55b7\u51fa tick\u300d\u8c03\u5927\u8bd5\u8bd5\uff09", head, this.collectTicks);
        }
        FOElytraLog.detail("COLLECT \u7ed3\u675f\uff1a\u5b9e\u4f53 %d \u4ef6\uff0c\u6536\u96c6 %d tick\uff08\u7b49\u5f85\u55b7\u51fa tick \u539f\u6587 %d / \u5b9e\u9645 %d\uff09\uff0c\u8ddd\u6700\u540e\u4e00\u4e2a\u65b0\u6389\u843d\u7269 %d tick\uff0c\u547d\u4e2d\u8bc1\u636e %d \u6761", this.spawnedCount, this.collectTicks, this.opts.openWaitTicks(), this.effectiveCollectTicks(), this.sinceLastSpawn, this.targetEvidence.size());
        this.next(State.NEXT, this.opts.actionDelay());
    }

    private void next(MinecraftClient mc) {
        if (this.targetVault != null) {
            boolean firstTime = this.openedPositions.add(this.targetVault);
            String pos = this.targetVault.toShortString();
            if (!firstTime) {
                FOElytraLog.detail("NEXT \u5b9d\u5e93 %s \u4e4b\u524d\u5df2\u7ecf\u8bb0\u8fc7\uff08\u540c\u4e00\u5750\u6807\u91cd\u590d\u9009\u4e2d \u2192 \u7a7a\u5f00\uff09\uff0c\u4e0d\u518d\u91cd\u590d\u8ba1\u6570", pos);
            } else if (this.clickedThisVault) {
                ++this.openedCount;
                FOElytraLog.detail("NEXT \u8bb0\u5f55\u5df2\u5f00\u5b9d\u5e93 %s\uff08\u672c\u6b21\u7b2c %d/%d \u4e2a\uff0c\u4e0d\u7965=%s\uff09", pos, this.openedCount, this.opts.maxVaultsPerRun(), this.targetOminous);
                this.notifyOpened(this.targetVault);
            } else {
                FOElytraLog.detail("NEXT \u5b9d\u5e93 %s \u8fd9\u6b21\u6ca1\u53d1\u51fa\u8fc7\u88ab\u63a5\u53d7\u7684\u53f3\u952e\uff08\u8d70\u4e0d\u5230 / \u65b9\u5757\u53d8\u4e86 / \u53f3\u952e\u88ab\u62d2\uff09\uff0c\u53ea\u8bb0\u5750\u6807\u9632\u6b62\u56de\u5934\u91cd\u9009\uff0c\u4e0d\u8ba1\u5165\u300c\u5df2\u5f00\u300d", pos);
            }
        }
        if (this.spawnedCount == 0) {
            FOElytraLog.detail("NEXT \u8fd9\u6b21\u6ca1\u62ff\u5230\u4efb\u4f55\u6389\u843d\u7269\uff1a%s \u5728 %d tick \u91cc\u6ca1\u6709\u7269\u54c1\u88ab\u6211\u4eec\u770b\u5230\uff08\u53ef\u80fd\u662f\u7a7a\u5f00\u3001\u4e5f\u53ef\u80fd\u662f\u6389\u843d\u7269\u5df2\u7ecf\u88ab\u81ea\u5df1\u6361\u8fdb\u80cc\u5305\uff09", this.targetVault == null ? "?" : this.targetVault.toShortString(), this.collectTicks);
        }
        this.resetVaultState();
        if (this.openedCount >= this.opts.maxVaultsPerRun()) {
            this.lastMessage = "\u5df2\u8fbe\u5230\u672c\u6b21\u4e0a\u9650 " + this.opts.maxVaultsPerRun() + " \u4e2a\u5b9d\u5e93";
            FOElytraLog.info("%s\uff08\u547d\u4e2d\u7684\u76ee\u6807\uff1a%s\uff09", this.lastMessage, this.foundTarget ? "\u6709" : "\u65e0");
            this.settleBeforeDone();
            this.goal(State.DONE);
            return;
        }
        this.next(State.SCAN, this.opts.actionDelay());
    }

    private void softSkipCurrentVault(String why) {
        BlockPos pos;
        BlockPos blockPos2 = pos = this.targetVault == null ? null : this.targetVault.toImmutable();
        if (pos == null) {
            this.next(State.SCAN, this.opts.actionDelay());
            return;
        }
        int failures = this.unreachableFailures.merge(pos, 1, Integer::sum);
        ++this.softSkipCount;
        FOElytraLog.warn("\u5b9d\u5e93 %s \u8d70\u4e0d\u8fdb\u53bb\uff08%s\uff0c\u7b2c %d \u6b21\uff09\uff0c\u672c\u8f6e\u6682\u65f6\u8df3\u8fc7\uff1b\u5982\u679c\u5468\u56f4\u6709\u522b\u7684\u5019\u9009\u4f1a\u5148\u53bb\u522b\u7684\uff0c\u6ca1\u6709\u5c31\u518d\u8bd5\u5b83\u4e00\u6b21", pos.toShortString(), why, failures);
        FOElytraLog.detail("\u8f6f\u8df3\u8fc7 %s\uff1a%s\uff08\u7b2c %d \u6b21\uff1b\u6bcf\u4e2a\u5750\u6807\u6700\u591a %d \u6b21\uff0c\u8fbe\u5230\u540e\u672c\u8f6e\u4e0d\u518d\u9009\u5b83\uff09\u3002\u5b83\u4e0d\u5199\u8fdb openedPositions \u2014\u2014 \u90a3\u4e0d\u662f\u300c\u5df2\u7ecf\u5904\u7406\u8fc7\u300d\uff0c\u53ea\u662f\u8fd9\u4e00\u6b21\u6ca1\u8d70\u5230", pos.toShortString(), why, failures, 2);
        this.resetVaultState();
        if (this.softSkipCount >= 4) {
            this.lastMessage = "\u8fd9\u4e00\u7247\u5df2\u7ecf\u6709 " + this.unreachableFailures.size() + " \u4e2a\u5b9d\u5e93\u8d70\u4e0d\u8fdb\u53bb\uff0c\u672c\u5bc6\u5ba4\u5148\u6536\u5de5";
            FOElytraLog.warn("%s\uff08\u7d2f\u8ba1\u8f6f\u8df3\u8fc7 %d \u6b21\uff0c\u4e0a\u9650 %d \u6b21\uff09\u2014\u2014 \u591a\u534a\u662f\u5899\u591a/\u843d\u5dee\u5927\u591f\u4e0d\u5230\uff1b\u6309\u300c\u6b63\u5e38\u7ed3\u675f\u300d\u6536\u5de5\uff08\u4e0d\u662f\u5931\u8d25\uff09\uff0c\u6362\u4e2a\u5bc6\u5ba4\u6216\u6362\u4e2a\u4f4d\u7f6e\u518d\u8dd1", this.lastMessage, this.softSkipCount, 4);
            this.next(State.FINISH, 0);
            return;
        }
        this.next(State.SCAN, this.opts.actionDelay());
    }

    private void resetVaultState() {
        this.targetVault = null;
        this.spawnedCount = 0;
        this.collectTicks = 0;
        this.sinceLastSpawn = 0;
        this.openAttempts = 0;
        this.clickedThisVault = false;
        this.inactiveRecheckTicks = 0;
        this.walkTicks = 0;
        this.repathTicks = 0;
        this.closeInAttempts = 0;
        this.requiredOpenDistance = this.opts.openDistance();
        this.lootBox = null;
        this.lootBoxVault = null;
        this.targetEvidence.clear();
        this.itemCountBaseline.clear();
        this.targetBookBaseline = 0;
        this.targetBookPickup = false;
        this.retryingSoftSkipped = false;
    }

    private void drink(MinecraftClient mc) {
        if (!this.opts.drinkOminousBottle()) {
            this.endUseHold("\u8bbe\u7f6e\u4e0d\u9700\u8981\u559d\u4e0d\u7965\u4e4b\u74f6");
            this.next(State.SCAN, 0);
            return;
        }
        if (InvHelper.screenOpen()) {
            if (this.holdingUse) {
                this.endUseHold("\u4f60\u6253\u5f00\u4e86\u754c\u9762\uff0c\u559d\u74f6\u88ab\u6253\u65ad");
                this.drinkWaitTicks = 40;
            }
            this.delay = this.waitForPlayerScreen();
            return;
        }
        if (this.holdingUse) {
            if (this.hasOmen((PlayerEntity)mc.player)) {
                this.endUseHold("\u5df2\u7ecf\u62ff\u5230\u5f81\u5146");
                this.drinkWaitTicks = 0;
                FOElytraLog.tip("\u4e0d\u7965\u4e4b\u5146\u5df2\u4e0a\u8eab\uff08\u9760\u8fd1\u8bd5\u70bc\u5237\u602a\u7b3c\u540e\u5b83\u4f1a\u53d8\u6210\u8bd5\u70bc\u4e4b\u5146\uff0c\u4e0d\u7965\u5237\u602a\u7b3c\u624d\u4f1a\u6389\u4e0d\u7965\u94a5\u5319\uff09", new Object[0]);
                this.next(State.SCAN, this.opts.actionDelay());
                return;
            }
            --this.holdUseTicks;
            if (this.holdUseTicks <= 0) {
                this.endUseHold("\u6309\u4f4f 35 tick \u5b8c\u6210");
                this.drinkWaitTicks = 40;
            }
            this.delay = 0;
            return;
        }
        if (this.hasOmen((PlayerEntity)mc.player)) {
            FOElytraLog.detail("DRINK \u8eab\u4e0a\u5df2\u7ecf\u6709%s\uff0c\u4e0d\u7528\u559d\u4e0d\u7965\u4e4b\u74f6", mc.player.hasStatusEffect(StatusEffects.TRIAL_OMEN) ? "\u8bd5\u70bc\u4e4b\u5146" : "\u4e0d\u7965\u4e4b\u5146");
            this.drinkWaitTicks = 0;
            this.next(State.SCAN, this.opts.actionDelay());
            return;
        }
        if (this.drinkWaitTicks > 0) {
            --this.drinkWaitTicks;
            if (this.drinkWaitTicks == 0) {
                FOElytraLog.warn("\u559d\u4e86\u4e0d\u7965\u4e4b\u74f6\u4f46\u8eab\u4e0a\u8fd8\u662f\u6ca1\u6709\u5f81\u5146\uff08\u7b2c %d \u6b21\u5c1d\u8bd5\uff09", this.drinkAttempts);
            }
            this.delay = 0;
            return;
        }
        if (this.drinkAttempts >= 2) {
            FOElytraLog.warn("\u4e0d\u7965\u4e4b\u74f6\u559d\u4e86 %d \u6b21\u90fd\u6ca1\u751f\u6548\uff08\u53ef\u80fd\u88ab\u653b\u51fb\u6253\u65ad\uff0c\u6216\u670d\u52a1\u5668\u62e6\u622a\u4e86\u4f7f\u7528\uff09\uff0c\u76f4\u63a5\u53bb\u627e\u5b9d\u5e93", this.drinkAttempts);
            this.next(State.SCAN, this.opts.actionDelay());
            return;
        }
        int hotbar = InvHelper.findSlot(s -> s.isOf(Items.OMINOUS_BOTTLE), 0, 9);
        if (hotbar < 0) {
            int slot = InvHelper.findSlot(s -> s.isOf(Items.OMINOUS_BOTTLE));
            if (slot < 0) {
                FOElytraLog.warn("\u8bbe\u7f6e\u8981\u300c\u81ea\u52a8\u559d\u4e0d\u7965\u4e4b\u74f6\u300d\uff0c\u4f46\u80cc\u5305\u91cc\u4e00\u4e2a\u4e0d\u7965\u4e4b\u74f6\u90fd\u6ca1\u6709\uff08\u4e0d\u7965\u4e4b\u74f6\u6765\u81ea\u4e0d\u7965\u5b9d\u5e93/\u8bd5\u70bc\u5bc6\u5ba4\u6218\u5229\u54c1\uff0c\u6216\u5df2\u5f00\u8fc7\u7684\u4e0d\u7965\u5b9d\u5e93\u6982\u7387\u6389\u843d\uff09", new Object[0]);
                this.next(State.SCAN, this.opts.actionDelay());
                return;
            }
            int empty = InvHelper.findEmptyHotbarSlot();
            if (empty < 0 && (empty = this.findSacrificialHotbarSlot(mc)) < 0) {
                FOElytraLog.warn("\u5feb\u6377\u680f\u6ca1\u6709\u7a7a\u4f4d\u653e\u4e0d\u7965\u4e4b\u74f6\uff0c\u8df3\u8fc7\u559d\u74f6\u76f4\u63a5\u53bb\u627e\u5b9d\u5e93", new Object[0]);
                this.next(State.SCAN, this.opts.actionDelay());
                return;
            }
            InvHelper.moveInvToHotbar(slot, empty);
            hotbar = empty;
            FOElytraLog.detail("DRINK \u4e0d\u7965\u4e4b\u74f6 \u80cc\u5305\u7b2c %d \u683c \u2192 \u5feb\u6377\u680f\u7b2c %d \u683c", slot, hotbar);
        }
        InvHelper.selectSlot(hotbar);
        ++this.drinkAttempts;
        PlayerAction.pressUse(true);
        this.holdingUse = true;
        this.holdUseTicks = 35;
        FOElytraLog.detail("DRINK \u7b2c %d \u6b21\uff1a\u6309\u4f4f\u53f3\u952e\u559d\u4e0d\u7965\u4e4b\u74f6\uff08\u5feb\u6377\u680f\u7b2c %d \u683c\uff0c\u6309\u4f4f %d tick\uff0c\u671f\u95f4\u62ff\u5230\u5f81\u5146\u4f1a\u63d0\u524d\u677e\u5f00\uff09", this.drinkAttempts, hotbar, 35);
        FOElytraLog.tip("\u6b63\u5728\u559d\u4e0d\u7965\u4e4b\u74f6\uff08\u4e0d\u7965\u4e4b\u5146\uff09\u2014\u2014\u9760\u8fd1\u8bd5\u70bc\u5237\u602a\u7b3c\u540e\u5b83\u624d\u4f1a\u53d8\u6210\u8bd5\u70bc\u4e4b\u5146\uff0c\u4e0d\u7965\u5237\u602a\u7b3c\u624d\u4f1a\u6389\u4e0d\u7965\u94a5\u5319", new Object[0]);
        this.delay = 0;
    }

    private void endUseHold(String why) {
        if (!this.holdingUse) {
            return;
        }
        this.holdingUse = false;
        this.holdUseTicks = 0;
        PlayerAction.pressUse(false);
        FOElytraLog.detail("DRINK \u677e\u5f00\u53f3\u952e\uff08%s\uff09", why);
    }

    private boolean hasOmen(PlayerEntity player) {
        RegistryEntry trial = StatusEffects.TRIAL_OMEN;
        RegistryEntry bad = StatusEffects.BAD_OMEN;
        return player.hasStatusEffect(trial) || player.hasStatusEffect(bad);
    }

    private void finish(MinecraftClient mc) {
        if (BaritoneHook.ready()) {
            BaritoneHook.stop();
        }
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.endUseHold("\u6536\u5c3e");
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        this.status = this.pendingFail ? TaskStatus.FAILED : TaskStatus.DONE;
        FOElytraLog.detail("FINISH \u6536\u5c3e\u5b8c\u6210\uff1a%s\uff0c\u5171\u5f00 %d \u4e2a\u5b9d\u5e93\uff0c\u547d\u4e2d\u76ee\u6807=%s", new Object[]{this.status, this.openedCount, this.foundTarget});
    }

    private void settleBeforeDone() {
        if (BaritoneHook.ready()) {
            BaritoneHook.stop();
        }
        this.endUseHold("\u8fdb\u5165\u7ec8\u6001");
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
    }

    private void next(State ns, int delayTicks) {
        State old = this.state;
        this.state = ns;
        this.delay = Math.max(0, delayTicks);
        FOElytraLog.detail("\u72b6\u6001 %s \u2192 %s\uff08delay=%d\uff09", new Object[]{old, ns, this.delay});
    }

    private void notifyOpened(BlockPos pos) {
        if (this.onOpened == null) {
            return;
        }
        if (pos == null) {
            return;
        }
        try {
            this.onOpened.accept(pos.toImmutable());
        }
        catch (Throwable t) {
            FOElytraLog.err("\u5df2\u5f00\u5b9d\u5e93 %s \u7684\u56de\u8c03\uff08onOpened\uff09\u51fa\u9519\uff1a%s", this.shortPos(pos), String.valueOf(t));
            FOElytraLog.detailError("VaultOpener.onOpened(" + this.shortPos(pos) + ")", t);
        }
    }

    private void goal(State terminal) {
        State old = this.state;
        this.state = terminal;
        this.delay = 0;
        FOElytraLog.detail("\u72b6\u6001 %s \u2192 %s\uff08\u7ec8\u6001\uff0c\u4e0d\u518d\u53d1\u5305\uff09", new Object[]{old, terminal});
    }

    private void fail(String reason) {
        this.failReason = reason;
        this.lastMessage = reason;
        FOElytraLog.err("%s", reason);
        FOElytraLog.detail("\u5224\u5931\u8d25\uff1a%s\uff08\u6b64\u65f6\u72b6\u6001 %s\uff0c\u5df2\u5f00 %d \u4e2a\u5b9d\u5e93\uff09", new Object[]{reason, this.state, this.openedCount});
        this.pendingFail = true;
        this.goal(State.FINISH);
    }

    private int waitForPlayerScreen() {
        ++this.screenWaitTicks;
        if (this.screenWaitTicks > 200) {
            this.fail("\u4f60\u4e00\u76f4\u5f00\u7740\u754c\u9762\uff0810 \u79d2\uff09\uff0c\u5b9d\u5e93\u6d41\u7a0b\u5df2\u53d6\u6d88");
            return 0;
        }
        if (this.screenWaitTicks % 60 == 0) {
            FOElytraLog.warn("\u68c0\u6d4b\u5230\u4f60\u5f00\u7740\u754c\u9762\uff0c\u5b9d\u5e93\u6d41\u7a0b\u6682\u505c\u4e2d\uff08\u5173\u6389\u540e\u4f1a\u81ea\u52a8\u7ee7\u7eed\uff09", new Object[0]);
        }
        return 10;
    }

    private int effectiveCollectTicks() {
        return Math.max(90, this.opts.openWaitTicks());
    }

    private String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String vaultHead() {
        if (this.targetVault == null) {
            return "?";
        }
        return this.targetVault.toShortString() + (this.targetOminous ? "\uff08\u4e0d\u7965\u5b9d\u5e93\uff09" : "\uff08\u666e\u901a\u5b9d\u5e93\uff09");
    }

    private String baselineText() {
        if (this.itemCountBaseline.isEmpty()) {
            return "\uff08\u6ca1\u6709\u914d\u7f6e\u76ee\u6807\u7269\u54c1\uff09";
        }
        ArrayList<String> parts = new ArrayList<String>();
        for (Map.Entry<Item, Integer> e : this.itemCountBaseline.entrySet()) {
            parts.add(e.getKey().getName().getString() + "=" + e.getValue());
        }
        return String.join((CharSequence)"\u3001", parts);
    }

    public static enum State {
        IDLE("\u7a7a\u95f2"),
        SCAN("\u626b\u63cf"),
        EQUIP("\u88c5\u5907"),
        WALK("\u8d70\u8fd1"),
        OPEN("\u6253\u5f00"),
        COLLECT("\u6536\u96c6"),
        NEXT("\u4e0b\u4e00\u4e2a"),
        DRINK("\u996e\u7528"),
        FINISH("\u6536\u5c3e"),
        DONE("\u5b8c\u6210"),
        FAILED("\u5931\u8d25");


        private final String label;

        State(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public record Options(int searchRadius, double openDistance, int openWaitTicks, int maxVaultsPerRun, boolean needOminous, List<Item> targetItems, List<Identifier> targetEnchantments, boolean stopOnTarget, boolean drinkOminousBottle, boolean useBaritoneWalk, int actionDelay, Predicate<BlockPos> candidateFilter, Consumer<BlockPos> onOpened) {
    }
}

