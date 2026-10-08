package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.BlockBreaker;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import com.fo.addon.elytra.core.Needs;
import com.fo.addon.elytra.core.PlayerAction;
import com.fo.addon.elytra.core.SettingHelper;
import com.fo.addon.elytra.core.ShulkerScanner;
import com.fo.addon.elytra.core.SupplyOptions;
import com.fo.addon.elytra.core.TaskStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public final class SupplyTask {
    private static final int WALK_MAX_TICKS = 40;
    private static final int SCREEN_WAIT_TICKS = 40;
    private static final int SCREEN_HOLD_MAX_TICKS = 600;
    private static final int VERIFY_WAIT_TICKS = 30;
    private static final int MOVE_GUARD = 240;
    private static final int OPEN_RETRY = 3;
    private static final int PLACE_RETRY = 3;
    private static final int PLACE_CANDIDATE_ROUNDS = 2;
    private static final int SWAP_RECHECK_MAX = 6;
    private static final int PLACE_RETRY_WAIT_TICKS = 10;
    private static final int SHULKER_BLOCK_WAIT_TICKS = 60;
    private static final int PLACE_POLL_LOG_INTERVAL = 20;
    private static final int SHULKER_RESCUE_RADIUS = 8;
    private static final int SHULKER_RESCUE_INTERVAL = 10;
    private static final int SHULKER_RESCUE_LOG_INTERVAL = 40;
    private static final int SHULKER_BREAK_WAIT_TICKS = 300;
    private static final int SHULKER_PROGRESS_GRACE_TICKS = 200;
    private static final int SHULKER_BREAK_HARD_TICKS = 900;
    private static final int MINE_STALL_TICKS = 60;
    private static final int WALK_CENTER_MAX_TICKS = 40;
    private static final int MINE_RETRY_MAX = 3;
    private static final int MINE_PROGRESS_LOG_TICKS = 100;
    private static final int MINE_MAX_WAIT_TICKS = 400;
    private static final int EC_PICKUP_WAIT_TICKS = 200;
    private static final int RECOVER_MAX_TICKS = 400;
    private static final int TAKE_ROUND_MAX = 3;
    private static final int RECOVER_PICKUP_TICKS = 200;
    private static final int PLACE_FLUID_DEPTH = 2;
    private final SupplyOptions opts;
    private State state = State.IDLE;
    private TaskStatus status = TaskStatus.IDLE;
    private int delay;
    private String failReason = "";
    private String lastMessage = "";
    private final Needs needs = new Needs();
    private BlockPos ecPos;
    private String ecTitle = "";
    private int ecHotbarSlot = -1;
    private int ecBlockCountAfterPlace;
    private int shulkerRawSlot = -1;
    private int shulkerHotbarSlot = -1;
    private int shulkerCountAfterPlace;
    private int shulkerCountBeforeTake;
    private BlockPos shulkerPos;
    private String shulkerTitle = "";
    private List<ShulkerScanner.Entry> scanned = List.of();
    private final List<PlanItem> plan = new ArrayList<PlanItem>();
    private final Set<Integer> storeTried = new LinkedHashSet<Integer>();
    private final Set<Integer> planOpened = new LinkedHashSet<Integer>();
    private int planIndex;
    private final Set<Item> exhausted = new LinkedHashSet<Item>();
    private boolean foodExhausted;
    private int boxFwTaken;
    private int boxSecondTaken;
    private boolean boxTookAnything;
    private boolean boxTookPartial;
    private final Deque<Integer> replaceSlots = new ArrayDeque<Integer>();
    private boolean starvedWarned;
    private String starvedDetailKey = "";
    private String replaceSlotSummary = "";
    private int replaceRecheckSkips;
    private int shulkerBreakTarget;
    private int ecBreakTarget;
    private List<BlockPos> fireTargets = new ArrayList<BlockPos>();
    private int sortMoves;
    private int ecItemBefore;
    private int obsidianBefore;
    private int pickupWait;
    private int waitTicks;
    private int walkTicks;
    private int screenHoldTicks;
    private int openRetries;
    private int placeRetries;
    private int placeWaitTicks;
    private final List<BlockPos> placeCandidates = new ArrayList<BlockPos>();
    private final Set<BlockPos> placeRejected = new LinkedHashSet<BlockPos>();
    private int placeCandidateIndex;
    private int placeCandidateTotal;
    private int placeRounds;
    private int takeRounds;
    private String placeEmptyReason = "";
    private int placeLavaSkipped;
    private int moveGuard;
    private boolean merged;
    private boolean breakRequested;
    private boolean walkPressed;
    private boolean pendingReturn;
    private int breakTicks;
    private BlockPos breakTarget;
    private int rescueTicks;
    private int rescueProgressTick;
    private int rescueBackpackSeen = -1;
    private int rescueDropSeen = -1;
    private BlockPos rescueTarget;
    private int boxXpTaken;
    private int boxElytraTaken;
    private int boxTotemTaken;
    private int boxFoodTaken;
    private boolean boxFoodNoRoom;
    private int forcedFoodSlot = -1;
    private TakeVerify takeVerify;
    private int takeVerifyTicks;
    private int takeVerifyRetries;
    private boolean totemRoomWarned;
    private int runLostShulkers;
    private String runLostPos = "";
    private int runLeftShulkers;
    private String runLeftPos = "";
    private int runLeftEnderChests;
    private String runLeftEcPos = "";
    private int mineIdleTicks;
    private int mineRetries;
    private int manualBreakRetries;
    private boolean mineManual;
    private String mineBackFailReason = "";
    private int walkCenterTicks;
    private boolean breakBlockGone;
    private boolean ecBlockGone;
    private int returnVerifyTicks;
    private int returnVerifyBefore;
    private int returnAttempt;
    private int returnTargetSlot = -1;
    private String shulkerSelectFailReason = "";
    private int recoverTicks;
    private int recoverStage;
    private int recoverStageTicks;
    private int recoverShulkerTarget;
    private int recoverEcTarget;
    private int recoverEcBefore;
    private int recoverObsidianBefore;
    private boolean recoverShulkerDone;
    private boolean recoverEcDone;
    private int recoverDropTicks;
    private BlockPos recoverDropTarget;
    private String recoverReason = "";
    private static int pendingLostShulkers;
    private static String pendingLostPos;
    private static int pendingLeftShulkers;
    private static String pendingLeftPos;
    private static int pendingLeftEnderChests;
    private static String pendingLeftEcPos;

    public SupplyTask(SupplyOptions opts) {
        this.opts = opts;
    }

    public void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            this.fail("\u73a9\u5bb6/\u4e16\u754c\u4e3a\u7a7a\uff0c\u65e0\u6cd5\u8865\u7ed9");
            return;
        }
        this.state = State.WALK_CENTER;
        this.status = TaskStatus.RUNNING;
        this.delay = 0;
        this.failReason = "";
        this.plan.clear();
        this.planIndex = 0;
        this.storeTried.clear();
        this.planOpened.clear();
        this.exhausted.clear();
        this.foodExhausted = false;
        this.fireTargets = new ArrayList<BlockPos>();
        this.sortMoves = 0;
        this.waitTicks = 0;
        this.walkTicks = 0;
        this.screenHoldTicks = 0;
        this.openRetries = 0;
        this.placeRetries = 0;
        this.placeWaitTicks = 0;
        this.placeCandidates.clear();
        this.placeRejected.clear();
        this.placeCandidateIndex = 0;
        this.placeCandidateTotal = 0;
        this.placeRounds = 0;
        this.placeEmptyReason = "";
        this.moveGuard = 0;
        this.merged = false;
        this.breakRequested = false;
        this.walkPressed = false;
        this.ecPos = null;
        this.shulkerPos = null;
        this.shulkerRawSlot = -1;
        this.shulkerHotbarSlot = -1;
        this.shulkerCountBeforeTake = 0;
        this.rescueTicks = 0;
        this.rescueProgressTick = 0;
        this.rescueBackpackSeen = -1;
        this.rescueDropSeen = -1;
        this.rescueTarget = null;
        this.runLostShulkers = 0;
        this.runLostPos = "";
        this.totemRoomWarned = false;
        this.runLeftShulkers = 0;
        this.runLeftPos = "";
        this.runLeftEnderChests = 0;
        this.runLeftEcPos = "";
        this.mineIdleTicks = 0;
        this.mineRetries = 0;
        this.breakBlockGone = false;
        this.ecBlockGone = false;
        BlockBreaker.reset();
        if (pendingLeftShulkers > 0) {
            FOElytraLog.warn("\u4e0a\u6b21\u8865\u7ed9\u6709 %d \u4e2a\u6f5c\u5f71\u76d2\u7559\u5728\u539f\u5730\uff08\u4f4d\u7f6e %s\uff09", pendingLeftShulkers, pendingLeftPos);
            pendingLeftShulkers = 0;
            pendingLeftPos = "";
        }
        if (pendingLeftEnderChests > 0) {
            FOElytraLog.warn("\u4e0a\u6b21\u8865\u7ed9\u6709 %d \u4e2a\u672b\u5f71\u7bb1\u7559\u5728\u539f\u5730\uff08\u4f4d\u7f6e %s\uff09", pendingLeftEnderChests, pendingLeftEcPos);
            pendingLeftEnderChests = 0;
            pendingLeftEcPos = "";
        }
        if (pendingLostShulkers > 0) {
            FOElytraLog.warn("\u4e0a\u6b21\u8865\u7ed9\u4e22\u5931\u6f5c\u5f71\u76d2 %d \u4e2a\uff08\u4f4d\u7f6e %s\uff09", pendingLostShulkers, pendingLostPos);
            pendingLostShulkers = 0;
            pendingLostPos = "";
        }
        FOElytraLog.info("\u5f00\u59cb\u81ea\u52a8\u8865\u7ed9\uff1a\u76ee\u6807 %s", this.describeTargets());
    }

    public void abort(String reason) {
        this.releaseKeys();
        this.clearStuckSneak("\u8865\u7ed9\u4e2d\u6b62");
        BlockBreaker.cancel();
        if (BaritoneHook.isMining()) {
            BaritoneHook.stop();
        }
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        if (this.state != State.IDLE) {
            FOElytraLog.warn("\u8865\u7ed9\u4e2d\u6b62\uff1a%s", reason);
        }
        this.state = State.IDLE;
        this.status = TaskStatus.IDLE;
        this.delay = 0;
    }

    public TaskStatus status() {
        return this.status;
    }

    public State state() {
        return this.state;
    }

    public boolean isRunning() {
        return this.status == TaskStatus.RUNNING;
    }

    public String failReason() {
        return this.failReason;
    }

    public String progress() {
        if (this.status == TaskStatus.RUNNING) {
            return this.state.name() + (String)(this.needs.isEmpty() ? "" : " \u8fd8\u9700 " + String.valueOf(this.needs));
        }
        return this.status.name() + (String)(this.failReason.isEmpty() ? "" : "(" + this.failReason + ")");
    }

    public String lastMessage() {
        return this.lastMessage;
    }

    public Set<Item> exhaustedItems() {
        return Collections.unmodifiableSet(this.exhausted);
    }

    public boolean foodExhausted() {
        return this.foodExhausted;
    }

    private void markExhausted() {
        if (this.needs.fireworkStacks > 0) {
            this.exhausted.add(Items.FIREWORK_ROCKET);
        }
        if (this.needs.xpBottles > 0) {
            this.exhausted.add(Items.EXPERIENCE_BOTTLE);
        }
        if (this.needs.totems > 0) {
            this.exhausted.add(Items.TOTEM_OF_UNDYING);
        }
        if (this.needs.elytra > 0) {
            this.exhausted.add(Items.ELYTRA);
        }
        if (this.needs.food > 0) {
            this.foodExhausted = true;
        }
        if (!this.exhausted.isEmpty() || this.foodExhausted) {
            FOElytraLog.detail("\u672c\u8f6e\u5224\u5b9a\u8fd9\u4e9b\u6682\u65f6\u53d6\u4e0d\u5230\uff1a%s%s\uff08\u53ef\u80fd\u662f\u7bb1\u5b50\u91cc\u771f\u6ca1\u6709\uff0c\u4e5f\u53ef\u80fd\u662f\u817e\u4e0d\u51fa\u683c\u5b50/\u754c\u9762\u6ca1\u5f00\u6210\u62ff\u4e0d\u51fa\u6765\uff09 \u2014\u2014 \u5df2\u767b\u8bb0 2 \u5206\u949f\u51b7\u5374\uff0c\u5148\u7528\u73b0\u6709\u7684\u7ee7\u7eed\u98de", this.exhausted, this.foodExhausted ? " + \u98df\u7269" : "");
        }
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
            this.noSneak("\u8865\u7ed9\u8fd0\u884c\u4e2d\uff08\u7b49\u52a8\u4f5c\u95f4\u9694\uff09");
            --this.delay;
            return;
        }
        try {
            this.step(mc);
        }
        catch (Throwable t) {
            FOElytraLog.err("\u8865\u7ed9\u5185\u90e8\u5f02\u5e38: %s", String.valueOf(t));
            this.fail("\u5185\u90e8\u5f02\u5e38 " + t.getClass().getSimpleName());
        }
    }

    private void step(MinecraftClient mc) {
        this.noSneak("\u72b6\u6001 " + String.valueOf((Object)this.state));
        switch (this.state.ordinal()) {
            case 1: {
                this.walkCenter(mc);
                break;
            }
            case 2: {
                this.sortInventory(mc);
                break;
            }
            case 3: {
                this.evaluate(mc);
                break;
            }
            case 4: {
                this.putOutFire(mc);
                break;
            }
            case 5: {
                this.placeEnderChest(mc);
                break;
            }
            case 6: {
                this.openEnderChest(mc);
                break;
            }
            case 7: {
                this.waitEnderChest(mc, State.SCAN);
                break;
            }
            case 8: {
                this.scan(mc);
                break;
            }
            case 9: {
                this.takeShulker(mc);
                break;
            }
            case 10: {
                this.verifyTake(mc);
                break;
            }
            case 11: {
                this.placeShulker(mc);
                break;
            }
            case 12: {
                this.openShulker(mc);
                break;
            }
            case 13: {
                this.waitShulker(mc);
                break;
            }
            case 14: {
                this.moveItems(mc);
                break;
            }
            case 15: {
                InvHelper.closeScreen();
                this.breakRequested = false;
                this.next(State.BREAK_SH, this.opts.actionDelay());
                break;
            }
            case 16: {
                this.breakShulker(mc);
                break;
            }
            case 17: {
                this.waitBreakShulker(mc);
                break;
            }
            case 18: {
                this.openEnderChest(mc);
                break;
            }
            case 19: {
                this.waitEnderChest(mc, State.RETURN_SH);
                break;
            }
            case 20: {
                this.returnShulker(mc);
                break;
            }
            case 21: {
                InvHelper.closeScreen();
                this.next(State.NEXT_SH, this.opts.actionDelay());
                break;
            }
            case 22: {
                this.nextShulker(mc);
                break;
            }
            case 23: {
                this.breakEnderChest(mc);
                break;
            }
            case 24: {
                this.waitBreakEnderChest(mc);
                break;
            }
            case 25: {
                this.verifyReturn(mc);
                break;
            }
            case 26: {
                this.recoverTick(mc);
                break;
            }
            case 27: {
                this.status = TaskStatus.DONE;
                break;
            }
            case 28: {
                this.status = TaskStatus.FAILED;
                break;
            }
            default: {
                this.status = TaskStatus.DONE;
            }
        }
    }

    private void walkCenter(MinecraftClient mc) {
        if (InvHelper.screenOpen()) {
            this.releaseKeys();
            if (++this.screenHoldTicks > 600) {
                this.fail("\u4f60\u4e00\u76f4\u5f00\u7740\u754c\u9762\uff0830 \u79d2\uff09\uff0c\u8865\u7ed9\u5df2\u53d6\u6d88");
                return;
            }
            if (this.screenHoldTicks % 100 == 0) {
                FOElytraLog.warn("\u68c0\u6d4b\u5230\u4f60\u5f00\u7740\u754c\u9762\uff0c\u8865\u7ed9\u6682\u505c\u4e2d\uff08\u5173\u6389\u540e\u4f1a\u81ea\u52a8\u7ee7\u7eed\uff09", new Object[0]);
            }
            this.delay = 0;
            return;
        }
        this.screenHoldTicks = 0;
        if (this.walkTicks++ > 40) {
            this.releaseKeys();
            this.next(State.SORT_INV, 2);
            return;
        }
        BlockPos foot = mc.player.getBlockPos();
        Vec3d center = new Vec3d((double)foot.getX() + 0.5, mc.player.getY(), (double)foot.getZ() + 0.5);
        Vec3d delta = center.subtract(mc.player.getEntityPos());
        if (Math.abs(delta.x) < 0.2 && Math.abs(delta.z) < 0.2) {
            this.releaseKeys();
            this.next(State.SORT_INV, 2);
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        mc.player.setYaw((float)yaw);
        PlayerAction.pressForward(true);
        this.walkPressed = true;
        this.delay = 0;
    }

    private void sortInventory(MinecraftClient mc) {
        this.noSneak("\u6574\u7406\u80cc\u5305\u524d");
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || handler.slots.size() < 45) {
            this.next(State.EVALUATE, this.opts.actionDelay());
            return;
        }
        int moves = 0;
        for (int b = 9; b < 36 && moves < 2 && this.sortMoves < 24; ++b) {
            int target;
            ItemStack back = mc.player.getInventory().getStack(b);
            if (back.isEmpty() || !this.isKeyItem(back) || (target = this.designatedHotbarSlot(mc, back)) < 0 || !this.moveRawToHotbar(handler, b, target)) continue;
            ++moves;
            ++this.sortMoves;
        }
        if (moves > 0) {
            this.delay = this.opts.actionDelay();
            return;
        }
        if (this.opts.debug()) {
            FOElytraLog.debug("\u7269\u54c1\u680f\u6574\u7406\u5b8c\u6210\uff08\u5feb\u6377\u680f\uff1a0 \u9550 / 1 \u5251 / 2 \u672b\u5f71\u7bb1 / 3-4 \u56fe\u817e / 5 \u98df\u7269\uff09", new Object[0]);
        }
        this.next(State.EVALUATE, this.opts.actionDelay());
    }

    private boolean isKeyItem(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        return this.isPickaxe(s) || this.isSword(s) || s.isOf(Items.ENDER_CHEST) || s.isOf(Items.TOTEM_OF_UNDYING) || this.matchesFood(s);
    }

    private int designatedHotbarSlot(MinecraftClient mc, ItemStack s) {
        if (this.isPickaxe(s)) {
            return this.isPickaxe(mc.player.getInventory().getStack(0)) ? -1 : 0;
        }
        if (this.isSword(s)) {
            return this.isSword(mc.player.getInventory().getStack(1)) ? -1 : 1;
        }
        if (s.isOf(Items.ENDER_CHEST)) {
            return mc.player.getInventory().getStack(2).isOf(Items.ENDER_CHEST) ? -1 : 2;
        }
        if (s.isOf(Items.TOTEM_OF_UNDYING)) {
            if (!mc.player.getInventory().getStack(3).isOf(Items.TOTEM_OF_UNDYING)) {
                return 3;
            }
            if (!mc.player.getInventory().getStack(4).isOf(Items.TOTEM_OF_UNDYING)) {
                return 4;
            }
            return -1;
        }
        if (this.matchesFood(s)) {
            ItemStack slot5 = mc.player.getInventory().getStack(5);
            return slot5.isEmpty() || !slot5.isOf(s.getItem()) ? 5 : -1;
        }
        return -1;
    }

    private boolean hotbarHasKeyItem(MinecraftClient mc, int slot) {
        return this.isKeyItem(mc.player.getInventory().getStack(slot));
    }

    private boolean swapRaw(ScreenHandler handler, int rawA, int rawB) {
        InvHelper.click(handler, rawA, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, rawB, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, rawA, 0, SlotActionType.PICKUP);
        return true;
    }

    private boolean moveRawToHotbar(ScreenHandler handler, int invIndex, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }
        InvHelper.click(handler, invIndex, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, 36 + hotbarSlot, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, invIndex, 0, SlotActionType.PICKUP);
        return true;
    }

    private boolean isPickaxe(ItemStack s) {
        return s.isOf(Items.NETHERITE_PICKAXE) || s.isOf(Items.DIAMOND_PICKAXE) || s.isOf(Items.IRON_PICKAXE) || s.isOf(Items.STONE_PICKAXE) || s.isOf(Items.WOODEN_PICKAXE) || s.isOf(Items.GOLDEN_PICKAXE);
    }

    private boolean isSword(ItemStack s) {
        return s.isOf(Items.NETHERITE_SWORD) || s.isOf(Items.DIAMOND_SWORD) || s.isOf(Items.IRON_SWORD) || s.isOf(Items.STONE_SWORD) || s.isOf(Items.WOODEN_SWORD) || s.isOf(Items.GOLDEN_SWORD) || s.isOf(Items.TRIDENT);
    }

    private boolean isGoodElytra(ItemStack s) {
        return s.getDamage() < 15 && ItemHelper.hasEnchantment(s, (RegistryKey<Enchantment>)Enchantments.UNBREAKING, 3);
    }

    private boolean isJunkNow(ItemStack s) {
        if (s.isEmpty()) {
            return true;
        }
        if (s.isOf(Items.FIREWORK_ROCKET)) {
            return false;
        }
        if (s.isOf(Items.EXPERIENCE_BOTTLE)) {
            return false;
        }
        if (s.isOf(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        if (s.isOf(Items.ENDER_CHEST)) {
            return false;
        }
        if (this.matchesFood(s)) {
            return false;
        }
        if (this.isFoodPriority(s)) {
            return false;
        }
        if (this.isPickaxe(s) || this.isSword(s)) {
            return false;
        }
        if (ItemHelper.isShulkerBox(s)) {
            return false;
        }
        if (s.isOf(Items.ELYTRA)) {
            return !this.isGoodElytra(s);
        }
        return true;
    }

    private void evaluate(MinecraftClient mc) {
        this.computeNeeds(mc);
        int hotbarSlot = this.findEnderChestHotbar();
        if (hotbarSlot < 0) {
            for (int i = 9; i < 36; ++i) {
                if (!mc.player.getInventory().getStack(i).isOf(Items.ENDER_CHEST)) continue;
                int empty = InvHelper.findEmptyHotbarSlot();
                if (empty < 0) {
                    empty = 8;
                }
                InvHelper.moveInvToHotbar(i, empty);
                hotbarSlot = empty;
                break;
            }
        }
        if (hotbarSlot < 0) {
            this.fail("\u80cc\u5305\u91cc\u6ca1\u6709\u672b\u5f71\u7bb1\uff0c\u65e0\u6cd5\u8865\u7ed9");
            return;
        }
        this.ecHotbarSlot = hotbarSlot;
        int total = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.ENDER_CHEST);
        if (total < this.opts.minEnderChests()) {
            FOElytraLog.warn("\u672b\u5f71\u7bb1\u53ea\u5269 %d \u4e2a\uff08\u5efa\u8bae\u81f3\u5c11 %d \u4e2a\uff09", total, this.opts.minEnderChests());
        }
        if (this.needs.isEmpty()) {
            this.lastMessage = "\u65e0\u9700\u8865\u7ed9";
            FOElytraLog.info("\u5f53\u524d\u7269\u8d44\u5df2\u8fbe\u6807\uff0c\u8df3\u8fc7\u8865\u7ed9", new Object[0]);
            this.next(State.DONE, 0);
            return;
        }
        if (!this.opts.autoPlaceEnderChest()) {
            this.fail("\u672a\u5f00\u542f\u300c\u81ea\u52a8\u653e\u7f6e\u672b\u5f71\u7bb1\u300d\uff0c\u4e14\u9644\u8fd1\u6ca1\u6709\u53ef\u7528\u7684\u672b\u5f71\u7bb1");
            return;
        }
        this.fireTargets = this.scanNearbyFire(mc, 3);
        if (!this.fireTargets.isEmpty()) {
            FOElytraLog.tip("\u5148\u628a\u8eab\u8fb9\u7684\u706b\u6253\u6389\uff08%d \u5904\uff09\u518d\u8865\u7ed9", this.fireTargets.size());
            this.next(State.PUT_OUT_FIRE, 0);
            return;
        }
        this.next(State.PLACE_EC, this.opts.actionDelay());
    }

    private List<BlockPos> scanNearbyFire(MinecraftClient mc, int radius) {
        ArrayList<BlockPos> fire = new ArrayList<BlockPos>();
        BlockPos origin = mc.player.getBlockPos();
        for (int i = -radius; i <= radius; ++i) {
            for (int j = -radius; j <= radius; ++j) {
                for (int k = -radius; k <= radius; ++k) {
                    BlockPos target = origin.add(i, j, k);
                    if (mc.world.getBlockState(target).getBlock() != Blocks.FIRE) continue;
                    fire.add(target);
                }
            }
        }
        return fire;
    }

    private void putOutFire(MinecraftClient mc) {
        this.fireTargets.removeIf(p -> mc.world.getBlockState(p).getBlock() != Blocks.FIRE);
        if (this.fireTargets.isEmpty()) {
            this.next(State.PLACE_EC, this.opts.actionDelay());
            return;
        }
        BlockPos pos = this.fireTargets.remove(0);
        InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)pos));
        if (mc.interactionManager != null) {
            mc.interactionManager.attackBlock(pos, Direction.UP);
        }
        this.delay = 1;
    }

    private void beginPlaceScan() {
        this.placeCandidates.clear();
        this.placeCandidateIndex = 0;
        this.placeCandidateTotal = 0;
        this.placeRounds = 0;
        this.placeEmptyReason = "";
        this.placeLavaSkipped = 0;
    }

    private BlockPos nextPlaceCandidate(MinecraftClient mc, String what) {
        ArrayList<BlockPos> wet;
        List<BlockPos> free;
        List<BlockPos> all;
        while (true) {
            if (!this.placeCandidates.isEmpty()) {
                BlockPos p2 = this.placeCandidates.remove(0);
                if (this.placeRejected.contains(p2)) continue;
                ++this.placeCandidateIndex;
                return p2;
            }
            if (this.placeRounds >= 2) {
                return null;
            }
            ++this.placeRounds;
            all = InvHelper.findPlaceTargets((PlayerEntity)mc.player, this.opts.placeRadius(), true);
            free = InvHelper.findPlaceTargets((PlayerEntity)mc.player, this.opts.placeRadius(), false);
            this.placeCandidates.addAll(free);
            this.placeCandidates.removeIf(this.placeRejected::contains);
            wet = new ArrayList<BlockPos>();
            ArrayList<BlockPos> wetFinal = wet;
            this.placeCandidates.removeIf(p -> {
                if (!SupplyTask.isLavaDanger(mc, p)) {
                    return false;
                }
                wetFinal.add((BlockPos)p);
                return true;
            });
            if (!wet.isEmpty()) {
                this.placeRejected.addAll(wet);
                this.placeLavaSkipped += wet.size();
                FOElytraLog.detail("\u7b5b\u6389 %d \u4e2a\u5ca9\u6d46\u4f4d\u7f6e\uff08\u4e0b\u65b9 %d \u683c\u5185\u6709\u5ca9\u6d46\u6216\u7d27\u6328\u5ca9\u6d46\uff09\uff1a%s", wet.size(), 2, SupplyTask.posList(wet));
            }
            ArrayList<BlockPos> dry = new ArrayList<BlockPos>();
            ArrayList<BlockPos> watery = new ArrayList<BlockPos>();
            for (BlockPos p3 : this.placeCandidates) {
                if (SupplyTask.isWaterNear(mc, p3)) {
                    watery.add(p3);
                    continue;
                }
                dry.add(p3);
            }
            if (!watery.isEmpty() && !dry.isEmpty()) {
                this.placeCandidates.clear();
                this.placeCandidates.addAll(dry);
                this.placeCandidates.addAll(watery);
                FOElytraLog.detail("\u5019\u9009\u91cc\u6709\u6c34\uff0c\u4f18\u5148\u6311\u4e0d\u542b\u6c34\u7684\u4f4d\u7f6e\uff08\u6709\u6c34\u7684 %d \u4e2a\u6392\u5728\u540e\u9762\uff0c\u4e0d\u542b\u6c34\u7684 %d \u4e2a\uff09", watery.size(), dry.size());
            }
            this.placeCandidateIndex = 0;
            this.placeCandidateTotal = this.placeCandidates.size();
            if (this.placeCandidates.isEmpty()) break;
        }
        int blocked = all.size() - free.size();
        StringBuilder blockedList = new StringBuilder();
        for (BlockPos p4 : all) {
            if (free.contains(p4)) continue;
            if (blockedList.length() > 0) {
                blockedList.append('\u3001');
            }
            blockedList.append(p4.getX()).append(',').append(p4.getY()).append(',').append(p4.getZ());
            if (blockedList.length() <= 120) continue;
            blockedList.append("\u2026");
            break;
        }
        this.placeEmptyReason = free.isEmpty() ? (all.isEmpty() ? "\u9644\u8fd1\u6ca1\u6709\u80fd\u653e" + what + "\u7684\u4f4d\u7f6e\uff08\u534a\u5f84 " + this.opts.placeRadius() + " \u90fd\u662f\u6321\u4f4f/\u60ac\u7a7a\u7684\uff09" : "\u5468\u56f4\u6ca1\u6709\u80fd\u653e" + what + "\u7684\u4f4d\u7f6e\uff08\u4f60\u7ad9\u7684\u4f4d\u7f6e\u6321\u4f4f\u4e86\uff09") : (wet.size() >= free.size() ? "\u80fd\u653e" + what + "\u7684\u4f4d\u7f6e\u4e0b\u65b9\u662f\u5ca9\u6d46\u6216\u7d27\u6328\u5ca9\u6d46\uff08\u672c\u8f6e\u7b5b\u6389 " + wet.size() + " \u4e2a\uff09\uff0c\u6ca1\u6709\u5b89\u5168\u4f4d\u7f6e" : "\u80fd\u653e" + what + "\u7684\u4f4d\u7f6e\u90fd\u8bd5\u8fc7\u4e86\uff08\u53ef\u7528 " + free.size() + " \u4e2a\uff0c\u5df2\u62c9\u9ed1 " + this.placeRejected.size() + " \u4e2a\uff09");
        FOElytraLog.detail("\u6446\u653e\u5019\u9009\u7528\u5c3d\uff1a\u53ef\u7528 %d \u4e2a\uff5c\u88ab\u81ea\u5df1\u78b0\u649e\u7bb1\u6321\u4f4f %d \u4e2a\uff08%s\uff09\uff5c\u5ca9\u6d46\u7b5b\u6389 %d \u4e2a\uff08\u7d2f\u8ba1 %d\uff09\uff5c\u5df2\u62c9\u9ed1 %d \u4e2a\uff5c\u534a\u5f84 %d", free.size(), blocked, blockedList.length() == 0 ? "\u65e0" : blockedList, wet.size(), this.placeLavaSkipped, this.placeRejected.size(), this.opts.placeRadius());
        return null;
    }

    private void rejectPlaceCandidate(BlockPos pos, String what) {
        if (pos != null) {
            this.placeRejected.add(pos.toImmutable());
        }
        BlockPos next = null;
        for (BlockPos p : this.placeCandidates) {
            if (this.placeRejected.contains(p)) continue;
            next = p;
            break;
        }
        FOElytraLog.detail("\u6362\u4f4d\u7f6e\u653e%s\uff1a%d,%d,%d \u88ab\u62d2 \u2192 \u6539\u8bd5 %s\uff08\u5019\u9009 %d/%d\uff09", what, pos == null ? 0 : pos.getX(), pos == null ? 0 : pos.getY(), pos == null ? 0 : pos.getZ(), next == null ? "\u91cd\u65b0\u626b\u4e00\u904d\u5019\u9009" : next.getX() + "," + next.getY() + "," + next.getZ(), this.placeCandidateIndex, this.placeCandidateTotal);
    }

    private static boolean isLavaDanger(MinecraftClient mc, BlockPos pos) {
        return SupplyTask.isFluidNear(mc, pos, true);
    }

    private static boolean isWaterNear(MinecraftClient mc, BlockPos pos) {
        return SupplyTask.isFluidNear(mc, pos, false);
    }

    private static boolean isFluidNear(MinecraftClient mc, BlockPos pos, boolean lava) {
        if (mc == null || mc.world == null || pos == null) {
            return false;
        }
        for (int d = 1; d <= 2; ++d) {
            if (!SupplyTask.fluidMatches(mc, pos.down(d), lava)) continue;
            return true;
        }
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN || !SupplyTask.fluidMatches(mc, pos.offset(dir), lava)) continue;
            return true;
        }
        return false;
    }

    private static boolean fluidMatches(MinecraftClient mc, BlockPos pos, boolean lava) {
        FluidState fluid = mc.world.getFluidState(pos);
        if (fluid == null || fluid.isEmpty()) {
            return false;
        }
        if (lava) {
            return fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA;
        }
        return fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.FLOWING_WATER;
    }

    private static String posList(List<BlockPos> list) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : list) {
            if (sb.length() > 0) {
                sb.append('\u3001');
            }
            sb.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());
            if (sb.length() <= 90) continue;
            sb.append("\u2026");
            break;
        }
        return sb.length() == 0 ? "\u65e0" : sb.toString();
    }

    private static String posText(BlockPos p) {
        return p == null ? "\u65e0" : p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private boolean walkToBlockCenterTick(MinecraftClient mc) {
        if (mc.player == null) {
            return false;
        }
        BlockPos foot = mc.player.getBlockPos();
        double dx = (double)foot.getX() + 0.5 - mc.player.getX();
        double dz = (double)foot.getZ() + 0.5 - mc.player.getZ();
        if (Math.abs(dx) < 0.2 && Math.abs(dz) < 0.2) {
            if (this.walkCenterTicks > 0) {
                FOElytraLog.detail("\u5df2\u7ecf\u7ad9\u5230\u811a\u4e0b\u65b9\u5757\u4e2d\u5fc3\u4e86\uff08\u8fd8\u5dee %.2f/%.2f \u683c\uff09\uff0c\u53ef\u4ee5\u653e\u7bb1\u5b50", dx, dz);
            }
            this.walkCenterTicks = 0;
            PlayerAction.pressForward(false);
            return false;
        }
        if (this.walkCenterTicks == 0) {
            FOElytraLog.detail("\u5148\u8d70\u5230\u811a\u4e0b\u65b9\u5757\u4e2d\u5fc3\u518d\u653e\u7bb1\u5b50\uff08\u8fd8\u5dee %.2f/%.2f \u683c\uff09", dx, dz);
        }
        ++this.walkCenterTicks;
        if (this.walkCenterTicks > 40) {
            FOElytraLog.detail("\u8d70\u4e2d\u5fc3\u8d70\u4e0d\u5230\u4f4d\uff08\u5df2\u7ecf %d tick\uff0c\u8fd8\u5dee %.2f/%.2f \u683c\uff09\uff0c\u5c31\u5728\u539f\u5730\u653e\u7bb1\u5b50", this.walkCenterTicks, dx, dz);
            this.walkCenterTicks = 0;
            PlayerAction.pressForward(false);
            return false;
        }
        mc.player.setYaw((float)Math.toDegrees(Math.atan2(-dx, dz)));
        PlayerAction.pressForward(true);
        return true;
    }

    private int extinguishFireNearby(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) {
            return 0;
        }
        BlockPos base = mc.player.getBlockPos();
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dy = -3; dy <= 3; ++dy) {
                for (int dz = -3; dz <= 3; ++dz) {
                    BlockPos p = base.add(dx, dy, dz);
                    if (!mc.world.getBlockState(p).isOf(Blocks.FIRE)) continue;
                    try {
                        mc.interactionManager.attackBlock(p, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    catch (Throwable t) {
                        FOElytraLog.detailError("extinguishFireNearby", t);
                        return 0;
                    }
                    FOElytraLog.detail("\u8865\u7ed9\u524d\u5148\u6253\u706d\u8eab\u8fb9\u7684\u706b\uff08%s\uff09", SupplyTask.posText(p));
                    return 1;
                }
            }
        }
        return 0;
    }

    private boolean fireballNearby(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return false;
        }
        try {
            Box box = new Box(mc.player.getBlockPos()).expand(12.0);
            List list = mc.world.getEntitiesByClass(FireballEntity.class, box, e -> e != null && e.isAlive());
            return !list.isEmpty();
        }
        catch (Throwable t) {
            FOElytraLog.detailError("fireballNearby", t);
            return false;
        }
    }

    private void placeEnderChest(MinecraftClient mc) {
        BlockPos cand;
        if (this.extinguishFireNearby(mc) > 0) {
            return;
        }
        if (this.walkToBlockCenterTick(mc)) {
            return;
        }
        this.beginPlaceScan();
        BlockPos pos = null;
        while ((cand = this.nextPlaceCandidate(mc, "\u672b\u5f71\u7bb1")) != null) {
            if (InvHelper.placeBlock(cand, this.ecHotbarSlot)) {
                pos = cand;
                break;
            }
            this.rejectPlaceCandidate(cand, "\u672b\u5f71\u7bb1");
        }
        if (pos == null) {
            this.fail((String)(this.placeEmptyReason.isEmpty() ? "\u9644\u8fd1\u6ca1\u6709\u5408\u9002\u7684\u4f4d\u7f6e\u653e\u7f6e\u672b\u5f71\u7bb1\uff08\u534a\u5f84 " + this.opts.placeRadius() + "\uff09" : this.placeEmptyReason));
            return;
        }
        ItemStack stack = mc.player.getInventory().getStack(this.ecHotbarSlot);
        this.ecTitle = stack.isEmpty() ? "\u672b\u5f71\u7bb1" : stack.getName().getString();
        this.ecPos = pos;
        this.ecBlockCountAfterPlace = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.ENDER_CHEST);
        FOElytraLog.tip("\u5df2\u653e\u7f6e\u672b\u5f71\u7bb1\u4e8e %d,%d,%d", pos.getX(), pos.getY(), pos.getZ());
        this.next(State.OPEN_EC, this.opts.actionDelay() * 2);
    }

    private void openEnderChest(MinecraftClient mc) {
        this.noSneak("\u5f00\u672b\u5f71\u7bb1\u524d");
        if (this.ecPos == null) {
            this.fail("\u672b\u5f71\u7bb1\u5750\u6807\u4e22\u5931");
            return;
        }
        if (mc.world.getBlockState(this.ecPos).isAir()) {
            this.fail("\u672b\u5f71\u7bb1\u4e0d\u89c1\u4e86\uff08\u88ab\u7834\u574f\u6216\u88ab\u63a8\u8d70\uff09");
            return;
        }
        InvHelper.interactBlock(this.ecPos);
        this.waitTicks = 0;
        this.next(this.pendingReturn ? State.WAIT_EC_RETURN : State.WAIT_EC, 1);
    }

    private void waitEnderChest(MinecraftClient mc, State onSuccess) {
        HandledScreen<?> screen = InvHelper.currentContainerScreen(this.ecTitle);
        if (screen != null) {
            this.openRetries = 0;
            if (onSuccess == State.RETURN_SH) {
                this.returnAttempt = -1;
                this.returnVerifyTicks = 0;
            }
            this.next(onSuccess, this.opts.actionDelay());
            return;
        }
        if (this.waitTicks++ > 40) {
            this.waitTicks = 0;
            if (this.openRetries++ >= 3) {
                this.fail("\u672b\u5f71\u7bb1\u754c\u9762\u6253\u4e0d\u5f00\uff08\u6807\u9898\u4e0d\u5339\u914d\u6216\u670d\u52a1\u5668\u62e6\u622a\uff09");
                return;
            }
            this.next(State.OPEN_EC, this.opts.actionDelay());
        }
    }

    private int countPlanFireworkBoxes() {
        int n = 0;
        for (PlanItem p : this.plan) {
            if (p.fireworkStacks() <= 0) continue;
            ++n;
        }
        return n;
    }

    private int countPlanSecondBoxes() {
        int n = 0;
        for (PlanItem p : this.plan) {
            if (p.second() <= 0) continue;
            ++n;
        }
        return n;
    }

    private int countPlanGapBoxes() {
        int n = 0;
        for (PlanItem p : this.plan) {
            if (p.fireworkStacks() > 0 || p.second() > 0) continue;
            ++n;
        }
        return n;
    }

    private void warnUnopenedPlanBoxes(String where) {
        if (this.plan.isEmpty()) {
            return;
        }
        ArrayList<Integer> missed = new ArrayList<Integer>();
        for (PlanItem p : this.plan) {
            if (this.planOpened.contains(p.rawSlot())) continue;
            missed.add(p.rawSlot());
        }
        if (missed.isEmpty()) {
            return;
        }
        FOElytraLog.warn("%s\uff1a\u672c\u8f6e\u8ba1\u5212\u91cc\u6709 %d \u4e2a\u76d2\u5b50\u59cb\u7ec8\u6ca1\u88ab\u6253\u5f00\u53d6\u7269\uff08\u69fd\u4f4d %s\uff0c\u8ba1\u5212 %d \u4e2a\uff09\u2192 \u8fd9\u4e9b\u9700\u6c42\u6ca1\u52a8\u8fc7\uff0c\u4e0d\u8981\u5f53\u6210\u300c\u76d2\u5b50\u91cc\u6ca1\u6709\u300d", where, missed.size(), missed, this.plan.size());
    }

    private List<Integer> computeShulkerPlan(int fireworkNeed, int secondNeed, boolean xpMode) {
        int i;
        int total = this.scanned.size();
        int f = Math.max(0, fireworkNeed);
        int e = Math.max(0, secondNeed);
        int MAX = 0x1FFFFFFF;
        int[][][] dp = new int[f + 1][e + 1][2];
        for (i = 0; i <= f; ++i) {
            for (int j = 0; j <= e; ++j) {
                dp[i][j][0] = 0x1FFFFFFF;
                dp[i][j][1] = 0;
            }
        }
        dp[0][0][0] = 0;
        for (i = 0; i < total; ++i) {
            int a = this.boxFireworkValue(i);
            int b = this.boxSecondValue(i, xpMode);
            for (int ca = f; ca >= 0; --ca) {
                for (int cb = e; cb >= 0; --cb) {
                    int nb;
                    int na;
                    int count;
                    if (dp[ca][cb][0] >= 0x1FFFFFFF || (count = dp[ca][cb][0] + 1) >= dp[na = Math.min(f, ca + a)][nb = Math.min(e, cb + b)][0]) continue;
                    dp[na][nb][0] = count;
                    dp[na][nb][1] = dp[ca][cb][1] | 1 << i;
                }
            }
        }
        ArrayList<Integer> out = new ArrayList<Integer>();
        if (dp[f][e][0] >= 0x1FFFFFFF) {
            return out;
        }
        int mask = dp[f][e][1];
        for (int i2 = 0; i2 < total; ++i2) {
            if ((mask & 1 << i2) == 0) continue;
            out.add(i2);
        }
        return out;
    }

    private int hotbarFoodCount(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 9; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !this.matchesFood(s)) continue;
            n += s.getCount();
        }
        return n;
    }

    private int boxSecondValue(int idx, boolean xpMode) {
        if (idx < 0 || idx >= this.scanned.size()) {
            return 0;
        }
        ShulkerScanner.Entry e = this.scanned.get(idx);
        return xpMode ? e.xpStacks() : e.elytra();
    }

    private int boxFireworkValue(int idx) {
        if (idx < 0 || idx >= this.scanned.size()) {
            return 0;
        }
        return this.scanned.get(idx).fireworkStacks();
    }

    private void scan(MinecraftClient mc) {
        int foodBox;
        this.noSneak("\u626b\u672b\u5f71\u7bb1\u524d");
        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlots = SupplyTask.containerSlots(handler);
        this.computeNeeds(mc);
        if (!this.plan.isEmpty()) {
            FOElytraLog.detail("SCAN \u91cd\u5efa\u8ba1\u5212\uff1a\u4e22\u5f03\u4e0a\u4e00\u8f6e\u7684 %d \u6761\uff08planIndex \u5df2\u5230 %d\uff09", this.plan.size(), this.planIndex);
            if (this.planIndex < this.plan.size()) {
                this.warnUnopenedPlanBoxes("\u91cd\u5efa\u8ba1\u5212\u524d");
            }
        }
        this.plan.clear();
        this.planIndex = 0;
        this.takeRounds = 0;
        this.storeTried.clear();
        this.planOpened.clear();
        this.resetBoxTakeCounters();
        this.starvedWarned = false;
        this.starvedDetailKey = "";
        this.replaceRecheckSkips = 0;
        List<ShulkerScanner.Entry> found = ShulkerScanner.scan(handler, containerSlots, this.opts.foodItems());
        if (found.isEmpty()) {
            this.fail("\u672b\u5f71\u7bb1\u91cc\u6ca1\u6709\u53ef\u7528\u7684\u6f5c\u5f71\u76d2");
            return;
        }
        this.scanned = new ArrayList<ShulkerScanner.Entry>(found);
        if (this.scanned.isEmpty()) {
            this.lastMessage = "\u672b\u5f71\u7bb1\u91cc\u7684\u76d2\u5b50\u90fd\u662f\u7a7a\u7684";
            this.markExhausted();
            FOElytraLog.warn("%s\uff08\u9700\u6c42\uff1a%s\uff09", this.lastMessage, this.needs);
            this.next(State.CLOSE_EC, 0);
            return;
        }
        boolean xpMode = this.needs.xpBottles > 0;
        this.computeReplaceSlots(mc, xpMode);
        int fwNeed = Math.max(0, this.needs.fireworkStacks);
        int secondNeed = xpMode ? Math.max(0, ItemHelper.toStacks(Items.EXPERIENCE_BOTTLE, this.needs.xpBottles)) : Math.max(0, this.needs.elytra);
        List<Integer> chosen = this.computeShulkerPlan(fwNeed, secondNeed, xpMode);
        if (chosen.isEmpty() && (fwNeed > 0 || secondNeed > 0)) {
            FOElytraLog.warn("\u672b\u5f71\u7bb1\u91cc\u6ca1\u6709\u80fd\u51d1\u9f50 \u70df\u82b1 %d \u7ec4 / %s %d \u7684\u76d2\u5b50\u7ec4\u5408\uff08\u9700\u6c42\uff1a%s\uff09", fwNeed, xpMode ? "\u74f6" : "\u9798\u7fc5", secondNeed, this.needs);
            StringBuilder ev = new StringBuilder();
            for (ShulkerScanner.Entry e : this.scanned) {
                if (ev.length() > 0) {
                    ev.append("\uff5c");
                }
                ev.append("\u69fd\u4f4d").append(e.slot()).append(' ').append(e.title()).append("\uff1a\u70df\u82b1 ").append(e.fireworkStacks()).append(" \u7ec4").append("\u3001").append(xpMode ? "\u74f6 " + e.xpBottles() + " \u4e2a" : "\u9798\u7fc5 " + e.elytra() + " \u6761").append("\u3001\u98df\u7269 ").append(e.food()).append("\u3001\u56fe\u817e ").append(e.totems());
            }
            FOElytraLog.warn("\u626b\u63cf\u5230\u7684\u76d2\u5b50\u4e00\u5171 %d \u4e2a\uff08\u8fd9\u5c31\u662f\u5168\u90e8\u8bc1\u636e\uff09\uff1a%s", this.scanned.size(), ev.length() == 0 ? "\u65e0" : ev);
            FOElytraLog.warn("\u7ed3\u8bba\uff1a\u8fd9 %d \u4e2a\u76d2\u5b50\u52a0\u8d77\u6765\u4e5f\u51d1\u4e0d\u51fa\u7f3a\u53e3\uff08\u4e0d\u662f\u540d\u5b57/\u989c\u8272\u7684\u5224\u65ad\uff0c\u662f\u6309\u76d2\u5185\u5b9e\u9645\u6570\u91cf\u7b97\u7684\uff09", this.scanned.size());
        }
        if (this.needs.food > 0 && this.hotbarFoodCount(mc) < 30 && (foodBox = ShulkerScanner.findFoodRichest(this.scanned)) >= 0 && this.scanned.get(foodBox).food() > 0) {
            FOElytraLog.detail("\u5feb\u6377\u680f\u98df\u7269\u53ea\u6709 %d \u4e2a\uff08< 30\uff09\u2192 \u518d\u5f00\u4e00\u4e2a\u98df\u7269\u6700\u591a\u7684\u76d2\u5b50 \u69fd\u4f4d%d\uff08\u98df\u7269 %d \u4e2a\uff09", this.hotbarFoodCount(mc), this.scanned.get(foodBox).slot(), this.scanned.get(foodBox).food());
            chosen.add(foodBox);
        }
        if (this.needs.totems > 0) {
            int totemBox = ShulkerScanner.findTotemRichest(this.scanned);
            if (totemBox >= 0 && this.scanned.get(totemBox).totems() > 0) {
                FOElytraLog.detail("\u56fe\u817e\u4f18\u5148\uff1a\u518d\u5f00\u4e00\u4e2a\u56fe\u817e\u6700\u591a\u7684\u76d2\u5b50 \u69fd\u4f4d%d\uff08\u91cc\u9762\u56fe\u817e %d \u4e2a\uff0c\u8fd8\u5dee %d \u4e2a\uff09", this.scanned.get(totemBox).slot(), this.scanned.get(totemBox).totems(), this.needs.totems);
                chosen.add(0, totemBox);
            } else {
                FOElytraLog.warn("\u672b\u5f71\u7bb1\u91cc\u6ca1\u6709\u88c5\u7740\u56fe\u817e\u7684\u76d2\u5b50\uff08\u6700\u591a\u7684\u90a3\u4e2a\u53ea\u6709 %d \u4e2a\uff09", totemBox < 0 ? 0 : this.scanned.get(totemBox).totems());
            }
        }
        LinkedHashSet<Integer> plannedSlots = new LinkedHashSet<Integer>();
        int fwLeft = fwNeed;
        int secondLeft = secondNeed;
        Iterator<Integer> iterator = chosen.iterator();
        while (iterator.hasNext()) {
            int idx = iterator.next();
            if (idx < 0 || idx >= this.scanned.size()) continue;
            ShulkerScanner.Entry e = this.scanned.get(idx);
            int takeFw = Math.min(fwLeft, e.fireworkStacks());
            int takeSecond = Math.min(secondLeft, xpMode ? e.xpStacks() : e.elytra());
            if (takeFw <= 0 && takeSecond <= 0 && e.food() <= 0 && e.totems() <= 0 || !plannedSlots.add(e.slot())) continue;
            this.plan.add(new PlanItem(e.slot(), takeFw, takeSecond, xpMode, e.title()));
            fwLeft -= takeFw;
            secondLeft -= takeSecond;
        }
        if (this.plan.size() > this.opts.maxShulkers()) {
            FOElytraLog.warn("\u672b\u5f71\u7bb1\u91cc\u7684\u4e1c\u897f\u8fc7\u4e8e\u5206\u6563\uff1a\u6700\u5c11\u8981 %d \u4e2a\u76d2\u5b50\uff0c\u8d85\u8fc7\u5355\u6b21\u4e0a\u9650 %d\uff0c\u53ea\u53d6\u524d %d \u4e2a\uff08\u5269\u4e0b\u7684\u4e0b\u4e00\u8f6e\u6309\u5269\u4f59\u9700\u6c42\u518d\u9009\uff09", this.plan.size(), this.opts.maxShulkers(), this.opts.maxShulkers());
            this.plan.subList(this.opts.maxShulkers(), this.plan.size()).clear();
        }
        if (this.opts.debug()) {
            for (ShulkerScanner.Entry e : this.scanned) {
                FOElytraLog.debug("\u76d2\u5b50[%d] %s: \u70df\u82b1%d\u7ec4 \u74f6%d \u98df\u7269%d \u56fe\u817e%d \u9798\u7fc5%d", e.slot(), e.title(), e.fireworkStacks(), e.xpBottles(), e.food(), e.totems(), e.elytra());
            }
        }
        if (this.plan.isEmpty()) {
            this.lastMessage = "\u6ca1\u7ffb\u5230\u80fd\u8865\u4e0a\u9700\u6c42\u7684\u4e1c\u897f\uff08\u53ef\u80fd\u662f\u7bb1\u5b50\u91cc\u6ca1\u6709\uff0c\u4e5f\u53ef\u80fd\u8fd9\u6279\u76d2\u5b50\u6ca1\u4e1c\u897f\uff09";
            this.markExhausted();
            FOElytraLog.warn("%s\uff08\u9700\u6c42\uff1a%s\uff09", this.lastMessage, this.needs);
            this.next(State.CLOSE_EC, 0);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.plan.size(); ++i) {
            PlanItem p = this.plan.get(i);
            if (i > 0) {
                sb.append("\u3001");
            }
            sb.append(p.rawSlot());
            if (p.fireworkStacks() > 0 || p.second() > 0) {
                sb.append("(\u70df\u82b1").append(p.fireworkStacks()).append("\u7ec4");
                if (p.second() > 0) {
                    sb.append(' ').append(p.xpMode() ? "\u74f6" : "\u9798\u7fc5").append(p.second());
                }
                sb.append(")");
                continue;
            }
            sb.append("(\u98df\u7269/\u56fe\u817e)");
        }
        FOElytraLog.info("\u672c\u8f6e\u53d6\u7269\u8ba1\u5212\uff1a\u76d2\u5b50 [%s]\uff08\u6700\u5c11 %d \u4e2a\uff09\uff5c\u9700\u6c42 \u70df\u82b1 %d \u7ec4/\u74f6 %d/\u98df\u7269 %d/\u56fe\u817e %d/\u9798\u7fc5 %d", sb, this.plan.size(), this.needs.fireworkStacks, this.needs.xpBottles, this.needs.food, this.needs.totems, this.needs.elytra);
        FOElytraLog.detail("\u8ba1\u5212\u91cc\u7684\u76d2\u5b50\u53ea\u6709\u771f\u88ab\u6253\u5f00\u53d6\u8fc7\u624d\u7b97\u7528\u8fc7\uff1b\u8fd9\u8f6e\u8ba1\u5212\u542b\u70df\u82b1\u76d2 %d \u4e2a\u3001\u74f6/\u9798\u7fc5\u76d2 %d \u4e2a\u3001\u98df\u7269\u56fe\u817e\u76d2 %d \u4e2a", this.countPlanFireworkBoxes(), this.countPlanSecondBoxes(), this.countPlanGapBoxes());
        this.next(State.TAKE_SHULKER, this.opts.actionDelay());
    }

    private void computeReplaceSlots(MinecraftClient mc, boolean xpMode) {
        this.replaceSlots.clear();
        int keepFireworks = Math.max(0, this.opts.targetFireworkStacks());
        int fwKept = 0;
        int fwPartialKept = 0;
        int bottleKept = 0;
        int elytraKept = 0;
        int totemKept = 0;
        int ecKept = 0;
        int foodKept = 0;
        int toolKept = 0;
        int shulkerKept = 0;
        int surplusKept = 0;
        int targetBottles = Math.max(0, this.opts.targetXpBottles());
        int targetFood = Math.max(0, this.opts.targetFoodCount());
        boolean fwSurplus = ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)mc.player, Items.FIREWORK_ROCKET)) > keepFireworks;
        boolean bottleSurplus = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.EXPERIENCE_BOTTLE) > targetBottles;
        boolean foodSurplus = this.countFood(mc) > targetFood;
        ArrayList<Integer> emptySlots = new ArrayList<Integer>();
        ArrayList<Integer> junkSlots = new ArrayList<Integer>();
        ArrayList<Integer> surplusSlots = new ArrayList<Integer>();
        for (int i = 9; i < 36; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                emptySlots.add(i);
                continue;
            }
            if (s.isOf(Items.FIREWORK_ROCKET)) {
                if (fwSurplus) {
                    ++surplusKept;
                    if (s.getCount() < s.getMaxCount()) {
                        surplusSlots.add(0, i);
                        continue;
                    }
                    surplusSlots.add(i);
                    continue;
                }
                if (s.getCount() < s.getMaxCount()) {
                    ++fwPartialKept;
                    continue;
                }
                ++fwKept;
                continue;
            }
            if (s.isOf(Items.EXPERIENCE_BOTTLE)) {
                if (bottleSurplus) {
                    ++surplusKept;
                    surplusSlots.add(0, i);
                    continue;
                }
                ++bottleKept;
                continue;
            }
            if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                ++totemKept;
                continue;
            }
            if (s.isOf(Items.ENDER_CHEST)) {
                ++ecKept;
                continue;
            }
            if (this.matchesFood(s) || this.isFoodPriority(s)) {
                if (foodSurplus) {
                    ++surplusKept;
                    surplusSlots.add(i);
                    continue;
                }
                ++foodKept;
                continue;
            }
            if (this.isPickaxe(s) || this.isSword(s)) {
                ++toolKept;
                continue;
            }
            if (ItemHelper.isShulkerBox(s)) {
                ++shulkerKept;
                continue;
            }
            if (s.isOf(Items.ELYTRA)) {
                if (this.isGoodElytra(s) && (this.needs.elytra > 0 || elytraKept < 5)) {
                    ++elytraKept;
                    continue;
                }
                junkSlots.add(i);
                continue;
            }
            junkSlots.add(i);
        }
        this.replaceSlots.addAll(emptySlots);
        this.replaceSlots.addAll(junkSlots);
        this.replaceSlots.addAll(surplusSlots);
        this.replaceSlotSummary = String.format("\u7a7a\u69fd %d / \u6742\u7269 %d / \u591a\u51fa\u6765\u7684\u7269\u8d44 %d \u683c / \u6392\u9664\uff1a\u70df\u82b1 %d \u645e\uff08\u96f6\u6563 %d \u683c\u3001\u76ee\u6807 %d \u7ec4\uff09\u3001\u74f6 %d \u645e\u3001\u56fe\u817e %d \u4e2a\u3001\u672b\u5f71\u7bb1 %d \u4e2a\u3001\u98df\u7269 %d \u5806\u3001\u5de5\u5177 %d \u4ef6\u3001\u6f5c\u5f71\u76d2 %d \u4e2a\u3001\u597d\u9798\u7fc5 %d \u6761", emptySlots.size(), junkSlots.size(), surplusKept, fwKept, fwPartialKept, keepFireworks, bottleKept, totemKept, ecKept, foodKept, toolKept, shulkerKept, elytraKept);
        if (this.opts.debug()) {
            FOElytraLog.debug("\u53ef\u66ff\u6362\u69fd\u4f4d %d \u4e2a\uff08\u7a7a\u69fd %d \u4e2a\u4f18\u5148 + \u6742\u7269 %d \u4e2a + \u591a\u51fa\u6765\u7684\u7269\u8d44 %d \u683c\uff09\uff1a%s\uff5c\u6392\u9664 \u6ee1\u645e\u70df\u82b1%d\u645e/\u96f6\u6563\u70df\u82b1%d\u683c(\u76ee\u6807%d\u7ec4) \u74f6%d\u645e \u9798\u7fc5%d\u6761 \u56fe\u817e%d\u4e2a \u672b\u5f71\u7bb1%d\u4e2a \u98df\u7269%d\u5806 \u5de5\u5177%d\u4ef6 \u76d2%d\u4e2a\uff08xpMode=%s\uff09", this.replaceSlots.size(), emptySlots.size(), junkSlots.size(), surplusSlots.size(), this.replaceSlots, fwKept, fwPartialKept, keepFireworks, bottleKept, elytraKept, totemKept, ecKept, foodKept, toolKept, shulkerKept, xpMode);
        }
    }

    private void takeShulker(MinecraftClient mc) {
        HandledScreen<?> screen;
        this.noSneak("\u53d6\u76d2\u5b50\u524d");
        if (this.planIndex >= this.plan.size()) {
            this.next(State.CLOSE_EC, 0);
            return;
        }
        this.computeNeeds(mc);
        if (this.needs.isEmpty()) {
            FOElytraLog.detail("\u53d6\u76d2\u524d\u590d\u6838\uff1a\u672c\u8f6e\u9700\u6c42\u5df2\u7ecf\u6ee1\u8db3\uff0c\u8df3\u8fc7\u8fd9\u4e2a\u76d2\u5b50\uff08\u6ca1\u5fc5\u8981\u518d\u5f00\u4e00\u6b21\u7bb1\u5b50\uff09", new Object[0]);
            this.planIndex = this.plan.size();
            this.next(State.CLOSE_EC, this.opts.actionDelay());
            return;
        }
        if (this.needs.totems > 0 && this.noRoomForTotems(mc)) {
            this.warnTotemNoRoom();
            this.exhausted.add(Items.TOTEM_OF_UNDYING);
            this.needs.totems = 0;
            FOElytraLog.warn("\u56fe\u817e\u5148\u8bb0\u6210\u6682\u65f6\u53d6\u4e0d\u5230\uff08\u80cc\u5305\u6ca1\u5730\u65b9\u653e\uff09\uff0c\u672c\u8f6e\u4e0d\u518d\u4e3a\u5b83\u5f00\u76d2\u5b50", new Object[0]);
            if (this.needs.isEmpty()) {
                this.planIndex = this.plan.size();
                this.next(State.CLOSE_EC, this.opts.actionDelay());
                return;
            }
        }
        if ((screen = InvHelper.currentContainerScreen(this.ecTitle)) == null) {
            this.fail("\u53d6\u76d2\u65f6\u672b\u5f71\u7bb1\u754c\u9762\u5df2\u5173\u95ed");
            return;
        }
        this.shulkerRawSlot = this.plan.get(this.planIndex).rawSlot();
        ScreenHandler handler = mc.player.currentScreenHandler;
        this.shulkerCountBeforeTake = ItemHelper.countShulkers((PlayerEntity)mc.player);
        int hotbarSlot = this.makeHotbarRoom(handler);
        if (hotbarSlot < 0) {
            hotbarSlot = this.swapOutReplaceableHotbar(handler);
            if (hotbarSlot < 0) {
                this.fail("\u5feb\u6377\u680f 9 \u683c\u5168\u662f\u8981\u7559\u7684\u4e1c\u897f\uff08\u56fe\u817e\u3001\u70df\u82b1\u3001\u7ecf\u9a8c\u74f6\u3001\u9798\u7fc5\u3001\u98df\u7269\u3001\u672b\u5f71\u7bb1\u3001\u9550\u5251\u3001\u6f5c\u5f71\u76d2\u90fd\u6362\u4e0d\u5f97\uff09\uff0c\u76d2\u5b50\u62ff\u4e0d\u5230\u624b\u4e0a\uff1b\u8bc1\u636e\uff1a\u80cc\u5305\u6f5c\u5f71\u76d2 " + this.shulkerCountBeforeTake + " \u4e2a\uff5c\u5feb\u6377\u680f\u7a7a\u4f4d " + this.countEmptyHotbar(mc) + "\uff5c\u53ef\u66ff\u6362\u5feb\u6377\u680f " + this.countReplaceableHotbar(mc) + "\uff5c\u5bb9\u5668\u7a7a\u4f4d " + this.countEmptyContainer(handler, -1));
                return;
            }
            this.shulkerHotbarSlot = hotbarSlot;
            this.next(State.VERIFY_TAKE, this.opts.actionDelay());
            return;
        }
        int rawPlayerSlot = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, hotbarSlot);
        if (rawPlayerSlot < 0) {
            this.fail("\u627e\u4e0d\u5230\u5feb\u6377\u680f\u69fd\u4f4d\u6620\u5c04\uff08hotbar=" + hotbarSlot + "\uff09");
            return;
        }
        InvHelper.moveStack(handler, this.shulkerRawSlot, rawPlayerSlot);
        this.shulkerHotbarSlot = hotbarSlot;
        this.next(State.VERIFY_TAKE, this.opts.actionDelay());
    }

    private void verifyTake(MinecraftClient mc) {
        PlanItem opened;
        ItemStack held = mc.player.getInventory().getStack(this.shulkerHotbarSlot);
        int boxes = ItemHelper.countShulkers((PlayerEntity)mc.player);
        if (!ItemHelper.isShulkerBox(held)) {
            this.fail("\u6f5c\u5f71\u76d2\u6ca1\u6709\u53d6\u5230\u624b\uff08\u69fd\u4f4d " + this.shulkerHotbarSlot + " \u4e0a\u662f " + (held.isEmpty() ? "\u7a7a" : held.getName().getString()) + "\uff1b\u80cc\u5305\u6f5c\u5f71\u76d2 " + boxes + " \u4e2a\uff09");
            return;
        }
        if (boxes < this.shulkerCountBeforeTake + 1) {
            FOElytraLog.detail("VERIFY_TAKE \u590d\u6838\u5f02\u5e38\uff1a\u80cc\u5305\u6f5c\u5f71\u76d2 %d\uff0c\u53d6\u51fa\u524d %d\uff08\u672c\u5730\u6ca1\u770b\u5230\u76d2\u5b50\u8fdb\u80cc\u5305\uff09", boxes, this.shulkerCountBeforeTake);
        }
        this.shulkerTitle = held.getName().getString();
        InvHelper.closeScreen();
        PlanItem planItem = opened = this.planIndex >= 0 && this.planIndex < this.plan.size() ? this.plan.get(this.planIndex) : null;
        if (opened != null) {
            this.planOpened.add(opened.rawSlot());
        }
        this.openRetries = 0;
        this.placeRetries = 0;
        this.placeWaitTicks = 0;
        this.starvedWarned = false;
        this.starvedDetailKey = "";
        this.replaceRecheckSkips = 0;
        this.pendingReturn = false;
        this.resetBoxTakeCounters();
        this.storeTried.clear();
        this.boxTookAnything = false;
        this.boxTookPartial = false;
        this.computeReplaceSlots(mc, this.needs.xpBottles > 0);
        PlanItem item = this.plan.get(this.planIndex);
        if (item.fireworkStacks() > 0 || item.second() > 0) {
            FOElytraLog.tip("\u672c\u76d2\u9700\u8981\u53d6\u51fa %d \u7ec4\u70df\u82b1\u3001%d %s\uff08\u5f53\u524d\u9700\u6c42\uff1a%s\uff09", item.fireworkStacks(), item.second(), item.xpMode() ? "\u7ec4\u9644\u9b54\u4e4b\u74f6" : "\u4e2a\u9798\u7fc5", this.needs);
        }
        this.next(State.PLACE_SH, this.opts.actionDelay() * 3);
    }

    private void placeShulker(MinecraftClient mc) {
        BlockPos cand;
        this.noSneak("\u653e\u76d2\u5b50\u524d");
        this.computeNeeds(mc);
        if (this.needs.isEmpty()) {
            FOElytraLog.detail("\u653e\u76d2\u524d\u590d\u6838\uff1a\u672c\u8f6e\u9700\u6c42\u5df2\u7ecf\u6ee1\u8db3\uff0c\u4e0d\u653e\u4e86\uff0c\u628a\u76d2\u5b50\u653e\u56de\u672b\u5f71\u7bb1", new Object[0]);
            this.planIndex = this.plan.size();
            this.pendingReturn = true;
            this.next(State.REOPEN_EC, this.opts.actionDelay());
            return;
        }
        if (!this.ensureShulkerSelected(mc)) {
            this.fail((String)(this.shulkerSelectFailReason == null || this.shulkerSelectFailReason.isEmpty() ? "\u624b\u4e0a\u548c\u80cc\u5305\u91cc\u90fd\u6ca1\u6709\u6f5c\u5f71\u76d2\u53ef\u653e\uff08\u80cc\u5305\u6f5c\u5f71\u76d2 " + ItemHelper.countShulkers((PlayerEntity)mc.player) + " \u4e2a\uff09" : this.shulkerSelectFailReason));
            return;
        }
        this.beginPlaceScan();
        int before = ItemHelper.countShulkers((PlayerEntity)mc.player);
        BlockPos pos = null;
        while ((cand = this.nextPlaceCandidate(mc, "\u6f5c\u5f71\u76d2")) != null) {
            boolean accepted = InvHelper.placeBlock(cand, this.shulkerHotbarSlot);
            this.shulkerCountAfterPlace = ItemHelper.countShulkers((PlayerEntity)mc.player);
            FOElytraLog.detail("PLACE_SH \u653e\u76d2\uff1a\u76ee\u6807 %s\uff5cselectedSlot=%d \u624b\u6301=%s\uff5c\u80cc\u5305\u6f5c\u5f71\u76d2 %d \u2192 %d\uff5cinteractBlock \u5ba2\u6237\u7aef\u7ed3\u679c=%s\uff5cgetBlockState=%s\uff08\u65b9\u5757/\u6570\u91cf\u90fd\u8981\u7b49\u670d\u52a1\u7aef\u5305\u56de\u6765\u624d\u7b97\u6570\uff09", cand.toShortString(), mc.player.getInventory().getSelectedSlot(), this.describeHeld(mc), before, this.shulkerCountAfterPlace, accepted, this.describeBlock(mc, cand));
            if (accepted) {
                pos = cand;
                break;
            }
            FOElytraLog.warn("\u653e\u7f6e\u6f5c\u5f71\u76d2\uff1a\u5ba2\u6237\u7aef\u5f53\u573a\u62d2\u7edd\uff08\u76ee\u6807 %s\uff0c\u624b\u6301 %s\uff09\u2192 \u6362\u4e0b\u4e00\u4e2a\u4f4d\u7f6e", cand.toShortString(), this.describeHeld(mc));
            this.rejectPlaceCandidate(cand, "\u6f5c\u5f71\u76d2");
        }
        if (pos == null) {
            this.fail(this.placeEmptyReason.isEmpty() ? "\u9644\u8fd1\u6ca1\u6709\u5408\u9002\u7684\u4f4d\u7f6e\u653e\u7f6e\u6f5c\u5f71\u76d2" : this.placeEmptyReason);
            return;
        }
        this.shulkerPos = pos;
        this.placeWaitTicks = 0;
        this.next(State.OPEN_SH, this.opts.actionDelay());
    }

    private void openShulker(MinecraftClient mc) {
        this.noSneak("\u5f00\u76d2\u5b50\u524d");
        if (this.shulkerPos == null) {
            this.fail("\u6f5c\u5f71\u76d2\u5750\u6807\u4e22\u5931\uff08\u6ca1\u7ecf\u8fc7 PLACE_SH \u5c31\u8fdb\u4e86 OPEN_SH\uff09");
            return;
        }
        BlockState st = mc.world.getBlockState(this.shulkerPos);
        int boxes = ItemHelper.countShulkers((PlayerEntity)mc.player);
        if (!st.isAir()) {
            if (this.placeWaitTicks > 0) {
                FOElytraLog.detail("OPEN_SH \u6f5c\u5f71\u76d2\u65b9\u5757\u5df2\u51fa\u73b0\uff08\u8f6e\u8be2\u4e86 %d tick\uff09\uff1a%s", this.placeWaitTicks, this.describeBlock(mc, this.shulkerPos));
            }
            this.placeWaitTicks = 0;
            this.placeRetries = 0;
            InvHelper.interactBlock(this.shulkerPos);
            this.waitTicks = 0;
            this.next(State.WAIT_SH, 1);
            return;
        }
        ++this.placeWaitTicks;
        if (this.placeWaitTicks <= 60) {
            if (this.placeWaitTicks == 1 || this.placeWaitTicks % 20 == 0) {
                FOElytraLog.detail("OPEN_SH \u8fd8\u6ca1\u770b\u5230\u6f5c\u5f71\u76d2\u65b9\u5757\uff08%d/%d tick\uff0c\u670d\u52a1\u7aef\u5305\u5728\u8def\u4e0a\u6216\u8fd9\u6b21\u653e\u7f6e\u5c06\u88ab\u56de\u6eda\uff09\uff1a\u76ee\u6807 %s\uff5c\u80cc\u5305\u6f5c\u5f71\u76d2 %d\uff08\u653e\u7f6e\u540e\u8bb0\u5f55 %d\uff09\uff5cselectedSlot=%d \u624b\u6301=%s", this.placeWaitTicks, 60, this.shulkerPos.toShortString(), boxes, this.shulkerCountAfterPlace, mc.player.getInventory().getSelectedSlot(), this.describeHeld(mc));
            }
            this.next(State.OPEN_SH, 0);
            return;
        }
        FOElytraLog.detail("OPEN_SH \u5931\u8d25\u8bca\u65ad\uff1a\u76ee\u6807 %s\uff5cgetBlockState=%s\uff5c\u80cc\u5305\u6f5c\u5f71\u76d2 \u73b0\u5728=%d / \u653e\u7f6e\u540e=%d / \u53d6\u51fa\u524d=%d\uff5cselectedSlot=%d \u624b\u6301=%s\uff5c\u8f6e\u8be2 %d tick \u65e0\u65b9\u5757\uff5c\u5df2\u6362\u4f4d\u7f6e %d \u6b21", this.shulkerPos.toShortString(), this.describeBlock(mc, this.shulkerPos), boxes, this.shulkerCountAfterPlace, this.shulkerCountBeforeTake, mc.player.getInventory().getSelectedSlot(), this.describeHeld(mc), this.placeWaitTicks, this.placeRetries);
        if (boxes >= 1) {
            ++this.placeRetries;
            this.rejectPlaceCandidate(this.shulkerPos, "\u6f5c\u5f71\u76d2");
            FOElytraLog.warn("\u6f5c\u5f71\u76d2\u653e\u4e0b\u53bb\u540e %d tick \u91cc\u5ba2\u6237\u7aef\u90fd\u6ca1\u770b\u5230\u65b9\u5757\uff08\u76d2\u5b50\u8fd8\u5728\u80cc\u5305 %d \u4e2a\uff09\u2192 \u628a\u8fd9\u4e2a\u4f4d\u7f6e\u62c9\u9ed1\u3001\u6362\u4e2a\u4f4d\u7f6e\u518d\u653e\uff08\u7b2c %d \u6b21\u6362\u4f4d\u7f6e\uff0c%d tick \u540e\u91cd\u653e\uff09", this.placeWaitTicks, boxes, this.placeRetries, 10);
            this.placeWaitTicks = 0;
            this.next(State.PLACE_SH, 10);
            return;
        }
        this.fail("\u6f5c\u5f71\u76d2\u6ca1\u6709\u653e\u597d\uff08\u65b9\u5757\u59cb\u7ec8\u6ca1\u51fa\u73b0\uff0c\u800c\u4e14\u76d2\u5b50\u5df2\u7ecf\u4e0d\u5728\u80cc\u5305\u91cc\u4e86\uff1a\u653e\u7f6e\u540e " + this.shulkerCountAfterPlace + " \u2192 \u73b0\u5728 " + boxes + "\uff0c\u76ee\u6807 " + this.shulkerPos.toShortString() + " \u73b0\u5728\u662f " + this.describeBlock(mc, this.shulkerPos) + "\uff0c\u53ef\u80fd\u662f\u670d\u52a1\u7aef\u6536\u4e0b\u4e86\u7269\u54c1\u4f46\u65b9\u5757\u6ca1\u843d\u5730\uff0c\u6216\u76d2\u5b50\u88ab\u522b\u7684\u73a9\u5bb6/\u5b9e\u4f53\u62ff\u8d70\uff09");
    }

    private void waitShulker(MinecraftClient mc) {
        ScreenHandler handler;
        this.noSneak("\u7b49\u76d2\u5b50\u754c\u9762");
        if (mc.currentScreen instanceof ShulkerBoxScreen && SupplyTask.containerSlots(handler = mc.player.currentScreenHandler) >= 27) {
            this.merged = false;
            this.moveGuard = 0;
            this.next(State.MOVE_ITEMS, this.opts.actionDelay());
            return;
        }
        if (this.waitTicks++ > 40) {
            if (this.openRetries++ >= 3) {
                this.fail("\u6f5c\u5f71\u76d2\u754c\u9762\u6253\u4e0d\u5f00");
                return;
            }
            this.next(State.OPEN_SH, this.opts.actionDelay());
        }
    }

    private void moveItems(MinecraftClient mc) {
        this.noSneak("\u53d6\u7269\u72b6\u6001\u6bcf tick");
        if (this.moveGuard++ > 240) {
            FOElytraLog.warn("\u53d6\u7269\u8d44\u6b65\u6570\u8d85\u9650\uff0c\u505c\u6b62\u642c\u8fd0", new Object[0]);
            this.next(State.CLOSE_SH, 0);
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlots = SupplyTask.containerSlots(handler);
        if (containerSlots < 27) {
            this.next(State.CLOSE_SH, 0);
            return;
        }
        if (this.takeVerify != null && this.verifyTakeStep(mc, handler, containerSlots)) {
            this.delay = this.opts.actionDelay();
            return;
        }
        if (this.opts.storeLoot() && this.storeOneJunkStack(mc, handler)) {
            this.delay = this.opts.actionDelay();
            return;
        }
        if (!this.merged) {
            InvHelper.mergeSameItems(handler, s -> s.isOf(Items.FIREWORK_ROCKET), 0, containerSlots);
            InvHelper.mergeSameItems(handler, s -> s.isOf(Items.EXPERIENCE_BOTTLE), 0, containerSlots);
            InvHelper.mergeSameItems(handler, s -> s.isOf(Items.TOTEM_OF_UNDYING), 0, containerSlots);
            this.merged = true;
            if (this.planIndex < this.plan.size()) {
                PlanItem item = this.plan.get(this.planIndex);
                FOElytraLog.detail("\u76d2\u5185\u5408\u5e76\u5b8c\u6210\uff08\u540c\u7c7b\u645e\u5408\u5e76\u624b\u52bf\uff1a\u62ac\u8d77\u6700\u5c0f\u90a3\u645e \u2192 \u53cc\u51fb\u805a\u62e2 PICKUP_ALL \u2192 \u653e\u56de\uff09\uff1b\u672c\u76d2\u8ba1\u5212\u53d6\u51fa %d \u7ec4\u70df\u82b1\u3001%d %s\uff08m/n \u53e3\u5f84\uff09", item.fireworkStacks(), item.second(), item.xpMode() ? "\u4e2a\u7ecf\u9a8c\u74f6" : "\u6761\u9798\u7fc5");
            }
            this.delay = this.opts.actionDelay();
            return;
        }
        if (this.putOutOneStack(mc, handler, containerSlots)) {
            this.boxTookAnything = true;
            this.delay = this.opts.actionDelay();
            return;
        }
        if (!this.boxTookAnything) {
            FOElytraLog.warn("\u672c\u76d2\u6ca1\u6709\u53d6\u5230\u4efb\u4f55\u4e1c\u897f\uff08\u76d2\u5b50\u91cc\u6ca1\u6709\u9700\u8981\u7684\u6574\u645e\uff0c\u6216\u80cc\u5305 9-35 \u683c\u5168\u662f\u8981\u7559\u7684\u4e1c\u897f\uff09", new Object[0]);
        } else if (this.boxTookPartial) {
            FOElytraLog.tip("\u672c\u76d2\u53ea\u53d6\u5230\u96f6\u6563\u5806\uff08\u6ca1\u6709\u6574\u645e\u53ef\u62ff\uff09", new Object[0]);
        }
        FOElytraLog.tip("\u672c\u76d2\u5b9e\u9645\u53d6\u5230 \u70df\u82b1 %d \u7ec4/\u74f6 %d/\u98df\u7269 %d/\u56fe\u817e %d/\u9798\u7fc5 %d\uff5c\u5269\u4f59\u9700\u6c42 %s", this.boxFwTaken, this.boxXpTaken, this.boxFoodTaken, this.boxTotemTaken, this.boxElytraTaken, this.needs.isEmpty() ? "\u65e0" : this.needs.toString());
        this.logBoxZeroReasons(mc, handler, containerSlots);
        if (this.planIndex < this.plan.size()) {
            int gotSecond;
            PlanItem planned = this.plan.get(this.planIndex);
            int n = gotSecond = planned.xpMode() ? this.boxXpTaken / 64 : this.boxElytraTaken;
            if (this.boxFwTaken < planned.fireworkStacks() || gotSecond < planned.second()) {
                FOElytraLog.warn("\u672c\u76d2\u8ba1\u5212 \u70df\u82b1 %d \u7ec4/%s %d\uff0c\u5b9e\u9645\u53d6\u5230 \u70df\u82b1 %d \u7ec4/%s %d\uff08\u6ca1\u53d6\u5230\u7684\u90e8\u5206\u7559\u7ed9\u6e05\u5355\u91cc\u540e\u9762\u7684\u76d2\u5b50\uff09", planned.fireworkStacks(), planned.xpMode() ? "\u74f6" : "\u9798\u7fc5", planned.second(), this.boxFwTaken, planned.xpMode() ? "\u74f6" : "\u9798\u7fc5", gotSecond);
            }
        }
        this.next(State.CLOSE_SH, this.opts.actionDelay());
    }

    private void resetBoxTakeCounters() {
        this.boxFwTaken = 0;
        this.boxSecondTaken = 0;
        this.boxXpTaken = 0;
        this.boxElytraTaken = 0;
        this.boxTotemTaken = 0;
        this.boxFoodTaken = 0;
        this.boxFoodNoRoom = false;
        this.forcedFoodSlot = -1;
        this.takeVerify = null;
        this.takeVerifyTicks = 0;
        this.takeVerifyRetries = 0;
    }

    private void beginTakeVerify(MinecraftClient mc, ScreenHandler handler, int srcSlot, Item item, int kind) {
        ItemStack src = ((Slot)handler.slots.get(srcSlot)).getStack();
        this.takeVerify = new TakeVerify(item, srcSlot, ItemHelper.countInInventory((PlayerEntity)mc.player, item), this.countInContainer(handler, item), kind, src.isEmpty() ? 0 : src.getCount());
        this.takeVerifyTicks = 0;
        this.takeVerifyRetries = 0;
    }

    private int countInContainer(ScreenHandler handler, Item item) {
        int n = 0;
        int limit = Math.min(SupplyTask.containerSlots(handler), handler.slots.size());
        for (int i = 0; i < limit; ++i) {
            ItemStack s = ((Slot)handler.slots.get(i)).getStack();
            if (s.isEmpty() || !s.isOf(item)) continue;
            n += s.getCount();
        }
        return n;
    }

    private boolean verifyTakeStep(MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        TakeVerify v = this.takeVerify;
        if (containerSlots < 27) {
            this.takeVerify = null;
            return false;
        }
        int nowInv = ItemHelper.countInInventory((PlayerEntity)mc.player, v.item());
        int nowBox = this.countInContainer(handler, v.item());
        if (nowInv > v.beforeInv() || v.beforeBox() >= 0 && nowBox < v.beforeBox()) {
            FOElytraLog.detail("\u53d6\u7269\u9a8c\u8bc1\u901a\u8fc7\uff1a%s \u80cc\u5305 %d \u2192 %d\uff5c\u76d2\u5185 %d \u2192 %d", v.item().getName().getString(), v.beforeInv(), nowInv, v.beforeBox(), nowBox);
            if (v.kind() == 1) {
                ++this.boxFwTaken;
            }
            if (v.kind() == 2) {
                ++this.boxSecondTaken;
                if (v.item() == Items.EXPERIENCE_BOTTLE) {
                    this.boxXpTaken += v.takenCount();
                } else {
                    this.boxElytraTaken += v.takenCount();
                }
            }
            if (v.kind() == 3) {
                this.boxTotemTaken += v.takenCount();
            }
            if (v.kind() == 4) {
                this.boxFoodTaken += v.takenCount();
            }
            if (v.takenCount() < ItemHelper.stackSize(v.item())) {
                this.boxTookPartial = true;
            }
            this.boxTookAnything = true;
            this.consumeNeed(v.item(), v.takenCount());
            this.takeVerify = null;
            return false;
        }
        ++this.takeVerifyTicks;
        if (this.takeVerifyTicks <= 30) {
            return true;
        }
        if (this.takeVerifyRetries < 1) {
            ++this.takeVerifyRetries;
            FOElytraLog.warn("\u53d6\u7269\u6ca1\u751f\u6548\uff08%s \u80cc\u5305\u4ecd %d \u4e2a\uff09\u2192 \u6362\u6210\u4e09\u51fb\u4e92\u6362\u518d\u70b9\u4e00\u6b21", v.item().getName().getString(), nowInv);
            this.noSneak("\u53d6\u7269\u91cd\u8bd5\u524d");
            if (!this.mergeIntoPartialStack(mc, handler, v.srcSlot(), v.item())) {
                this.moveToReplaceSlot(mc, handler, v.srcSlot(), v.item() == Items.EXPERIENCE_BOTTLE);
            }
            this.takeVerifyTicks = 0;
            return true;
        }
        FOElytraLog.warn("\u53d6\u7269\u4e24\u6b21\u90fd\u6ca1\u751f\u6548\uff08%s \u80cc\u5305\u4ecd %d \u4e2a / \u76d2\u5185 %d \u4e2a\uff09\u2192 \u8fd9\u4e00\u7c7b\u8bb0\u6210\u6682\u65f6\u53d6\u4e0d\u5230", v.item().getName().getString(), nowInv, nowBox);
        this.markTakeFailed(v.item());
        this.takeVerify = null;
        return false;
    }

    private void markTakeFailed(Item item) {
        if (item == null) {
            return;
        }
        this.exhausted.add(item);
        if (this.matchesFood(item)) {
            this.foodExhausted = true;
        }
        FOElytraLog.warn("\u6682\u65f6\u53d6\u4e0d\u5230\uff1a%s\uff08\u91cd\u8bd5\u4e00\u6b21\u4ecd\u6ca1\u8fdb\u80cc\u5305\uff09\u2192 \u672c\u8f6e\u4e0d\u518d\u4e3a\u5b83\u5f00\u76d2\u5b50\uff0c\u9700\u6c42\u5148\u7559\u7740\u4e0d\u5047\u88c5\u8865\u4e0a\u4e86", item.getName().getString());
    }

    private boolean quickTake(MinecraftClient mc, ScreenHandler handler, int srcSlot, Item item) {
        return this.bulkTake(mc, handler, srcSlot, item);
    }

    private boolean slotTake(MinecraftClient mc, ScreenHandler handler, int srcSlot, int destInvIndex, Item item) {
        ItemStack src = ((Slot)handler.slots.get(srcSlot)).getStack();
        if (src.isEmpty() || !src.isOf(item)) {
            return false;
        }
        int raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, destInvIndex);
        if (raw < 0) {
            return false;
        }
        this.noSneak("\u53d6\u98df\u7269\u6362\u4f4d\u524d");
        this.beginTakeVerify(mc, handler, srcSlot, item, SupplyTask.kindOf(item));
        InvHelper.moveStack(handler, srcSlot, raw);
        FOElytraLog.detail("\u98df\u7269\u4e09\u51fb\u4e92\u6362\uff1a\u76d2\u5185\u69fd %d \u7684 %s x%d \u2192 \u624b\u4e0a/\u5feb\u6377\u680f %d\uff5c\u642c\u8fd0\u524d \u80cc\u5305 %d / \u76d2\u5185 %d", srcSlot, item.getName().getString(), this.takeVerify.takenCount(), destInvIndex, this.takeVerify.beforeInv(), this.takeVerify.beforeBox());
        return true;
    }

    private boolean bulkTake(MinecraftClient mc, ScreenHandler handler, int srcSlot, Item item) {
        this.noSneak("\u6574\u645e\u53d6\u7269\u524d");
        if (this.mergeIntoPartialStack(mc, handler, srcSlot, item)) {
            return true;
        }
        if (this.moveToReplaceSlot(mc, handler, srcSlot, item == Items.EXPERIENCE_BOTTLE)) {
            return true;
        }
        FOElytraLog.detail("\u6574\u645e\u53d6\u7269\u6ca1\u843d\u70b9\uff1a\u76d2\u5185\u69fd %d \u7684 %s \u65e2\u6ca1\u6709\u534a\u645e\u540c\u7c7b\u53ef\u5408\u5e76\uff0c\u80cc\u5305\u4e5f\u6ca1\u6709\u53ef\u66ff\u6362\u69fd\uff08\u4e09\u51fb\u4e92\u6362\u9700\u8981\u843d\u70b9\uff09", srcSlot, item.getName().getString());
        return false;
    }

    private boolean noSpaceForStack(MinecraftClient mc, Item item) {
        for (int i = 0; i < 36; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isOf(item) || s.getCount() >= s.getMaxCount()) continue;
            return false;
        }
        if (InvHelper.emptyBackpackSlots() > 0) {
            return false;
        }
        return InvHelper.findEmptyHotbarSlot() < 0;
    }

    private int hotbarIndexOf(MinecraftClient mc, Item item) {
        for (int j = 0; j < 9; ++j) {
            if (!mc.player.getInventory().getStack(j).isOf(item)) continue;
            return j;
        }
        return -1;
    }

    private boolean noRoomForTotems(MinecraftClient mc) {
        if (mc.player == null) {
            return false;
        }
        PlayerInventory inv = mc.player.getInventory();
        if (!inv.getStack(3).isOf(Items.TOTEM_OF_UNDYING) || !inv.getStack(4).isOf(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack s = inv.getStack(i);
            if (!s.isOf(Items.TOTEM_OF_UNDYING) || s.getCount() >= s.getMaxCount()) continue;
            return false;
        }
        if (InvHelper.emptyBackpackSlots() > 0) {
            return false;
        }
        return this.surplusSwapCandidate(mc) < 0;
    }

    private int surplusSwapCandidate(MinecraftClient mc) {
        if (mc.player == null) {
            return -1;
        }
        boolean fwSurplus = ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)mc.player, Items.FIREWORK_ROCKET)) > Math.max(0, this.opts.targetFireworkStacks());
        boolean bottleSurplus = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.EXPERIENCE_BOTTLE) > Math.max(0, this.opts.targetXpBottles());
        boolean foodSurplus = this.countFood(mc) > Math.max(0, this.opts.targetFoodCount());
        for (int i = 9; i < 36; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            if (this.isJunkNow(s)) {
                return i;
            }
            if (s.isOf(Items.FIREWORK_ROCKET) && fwSurplus) {
                return i;
            }
            if (s.isOf(Items.EXPERIENCE_BOTTLE) && bottleSurplus) {
                return i;
            }
            if (!this.matchesFood(s) && !this.isFoodPriority(s) || !foodSurplus) continue;
            return i;
        }
        return -1;
    }

    private void warnTotemNoRoom() {
        if (this.totemRoomWarned) {
            return;
        }
        this.totemRoomWarned = true;
        FOElytraLog.warn("\u80cc\u5305\u6ca1\u6709\u7a7a\u4f4d\u653e\u66f4\u591a\u56fe\u817e\uff08\u5feb\u6377\u680f 3/4 \u5df2\u6ee1\u3001\u80cc\u5305\u65e0\u7a7a\u4f4d\uff09\uff0c\u5148\u817e\u683c\u5b50", new Object[0]);
    }

    private int foodTargetSlot(MinecraftClient mc, Item item) {
        int same = this.hotbarIndexOf(mc, item);
        if (same >= 0) {
            return same;
        }
        int empty = InvHelper.findEmptyHotbarSlot();
        if (empty >= 0) {
            return empty;
        }
        for (int h = 0; h < 9; ++h) {
            if (!this.isJunkNow(mc.player.getInventory().getStack(h))) continue;
            return h;
        }
        return -1;
    }

    private int smallestFireworkHotbarSlot(MinecraftClient mc) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int h = 0; h < 9; ++h) {
            ItemStack s = mc.player.getInventory().getStack(h);
            if (!s.isOf(Items.FIREWORK_ROCKET) || s.getCount() >= bestCount) continue;
            bestCount = s.getCount();
            best = h;
        }
        return best;
    }

    private int forceFoodSlotByMovingFirework(MinecraftClient mc, ScreenHandler handler) {
        int h = this.smallestFireworkHotbarSlot(mc);
        if (h < 0) {
            FOElytraLog.warn("\u98df\u7269\u6ca1\u5730\u65b9\u653e\uff0c\u60f3\u632a\u4e00\u7ec4\u70df\u82b1\u817e\u4f4d\u5b50\uff0c\u4f46\u5feb\u6377\u680f 0~8 \u91cc\u4e00\u7ec4\u70df\u82b1\u90fd\u6ca1\u6709", new Object[0]);
            return -1;
        }
        int count = mc.player.getInventory().getStack(h).getCount();
        int raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, h);
        FOElytraLog.warn("\u6ca1\u6709\u7a7a\u683c\u653e\u98df\u7269 \u2192 \u628a\u5feb\u6377\u680f\u7b2c %d \u683c\u90a3\u7ec4\u70df\u82b1\uff08%d \u4e2a\uff09\u642c\u56de\u76d2\u5b50\u817e\u4f4d\u7f6e", h + 1, count);
        if (raw >= 0) {
            InvHelper.quickMove(handler, raw);
            if (mc.player.getInventory().getStack(h).isEmpty()) {
                this.forcedFoodSlot = h;
                return h;
            }
        }
        int back = -1;
        for (int i = 9; i < 36; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            back = i;
            break;
        }
        if (back < 0) {
            FOElytraLog.warn("\u642c\u56de\u76d2\u5b50\u5931\u8d25\uff0c\u80cc\u5305 9~35 \u4e5f\u6ca1\u6709\u7a7a\u683c \u2192 \u76d2\u5b50\u91cc\u7684\u98df\u7269\u653e\u4e0d\u4e0b", new Object[0]);
            return -1;
        }
        int rawBack = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, back);
        if (raw < 0 || rawBack < 0) {
            FOElytraLog.warn("\u642c\u56de\u76d2\u5b50\u5931\u8d25\uff0c\u80cc\u5305\u7a7a\u683c\u4e5f\u6362\u4e0d\u52a8\uff08\u69fd\u4f4d\u5bf9\u4e0d\u4e0a\uff09\u2192 \u76d2\u5b50\u91cc\u7684\u98df\u7269\u653e\u4e0d\u4e0b", new Object[0]);
            return -1;
        }
        InvHelper.moveStack(handler, raw, rawBack);
        if (mc.player.getInventory().getStack(h).isEmpty()) {
            FOElytraLog.warn("\u642c\u56de\u76d2\u5b50\u6ca1\u6210\u529f \u2192 \u6539\u6210\u628a\u5feb\u6377\u680f\u7b2c %d \u683c\u90a3\u7ec4\u70df\u82b1\uff08%d \u4e2a\uff09\u6362\u8fdb\u80cc\u5305\u7b2c %d \u683c", h + 1, count, back + 1);
            this.forcedFoodSlot = h;
            return h;
        }
        FOElytraLog.warn("\u642c\u56de\u76d2\u5b50\u5931\u8d25\uff0c\u80cc\u5305\u7a7a\u683c\u4e5f\u6362\u4e0d\u52a8 \u2192 \u76d2\u5b50\u91cc\u7684\u98df\u7269\u653e\u4e0d\u4e0b", new Object[0]);
        return -1;
    }

    private int foodTargetSlotForBox(MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        for (int i = 0; i < containerSlots; ++i) {
            int t;
            ItemStack s = ((Slot)handler.slots.get(i)).getStack();
            if (s.isEmpty() || !this.matchesFood(s) || (t = this.foodTargetSlot(mc, s.getItem())) < 0) continue;
            return t;
        }
        return -1;
    }

    private ShulkerScanner.Entry currentBoxEntry() {
        if (this.planIndex < 0 || this.planIndex >= this.plan.size()) {
            return null;
        }
        int slot = this.plan.get(this.planIndex).rawSlot();
        for (ShulkerScanner.Entry e : this.scanned) {
            if (e.slot() != slot) continue;
            return e;
        }
        return null;
    }

    private void logBoxZeroReasons(MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        ShulkerScanner.Entry e = this.currentBoxEntry();
        if (e == null) {
            return;
        }
        this.zeroReason("\u70df\u82b1", Items.FIREWORK_ROCKET, e.fireworkStacks(), this.boxFwTaken, "\u7ec4", mc, handler, containerSlots);
        this.zeroReason("\u7ecf\u9a8c\u74f6", Items.EXPERIENCE_BOTTLE, e.xpBottles(), this.boxXpTaken, "\u4e2a", mc, handler, containerSlots);
        this.zeroReason("\u9798\u7fc5", Items.ELYTRA, e.elytra(), this.boxElytraTaken, "\u6761", mc, handler, containerSlots);
        this.zeroReason("\u56fe\u817e", Items.TOTEM_OF_UNDYING, e.totems(), this.boxTotemTaken, "\u4e2a", mc, handler, containerSlots);
        this.zeroReason("\u98df\u7269", null, e.food(), this.boxFoodTaken, "\u4e2a", mc, handler, containerSlots);
    }

    private void zeroReason(String name, Item item, int avail, int taken, String unit, MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        if (taken > 0) {
            return;
        }
        if (avail <= 0) {
            FOElytraLog.detail("%s\uff1a\u76d2\u5b50\u91cc\u672c\u6765\u5c31\u6ca1\u6709\uff08\u626b\u63cf 0 %s\uff09", name, unit);
            return;
        }
        String why = item == null ? (this.foodTargetSlotForBox(mc, handler, containerSlots) < 0 ? "\u6709\u4f46\u6ca1\u5730\u65b9\u653e\uff08\u5feb\u6377\u680f\u6ca1\u6709\u540c\u79cd\u98df\u7269\u683c\u3001\u6ca1\u6709\u7a7a\u683c\u3001\u4e5f\u6ca1\u6709\u53ef\u66ff\u6362\u683c\uff0c\u552f\u4e00\u80fd\u632a\u7684\u70df\u82b1\u4e5f\u6ca1\u632a\u6210\uff09" : "\u6709\u4f46\u642c\u8fd0\u6ca1\u751f\u6548\uff08\u624b\u52bf\u5931\u8d25\uff09") : (item == Items.TOTEM_OF_UNDYING ? (this.noRoomForTotems(mc) ? "\u6709\u4f46\u6ca1\u5730\u65b9\u653e\uff08\u5feb\u6377\u680f 3/4 \u5df2\u6ee1\u3001\u80cc\u5305\u65e0\u7a7a\u4f4d\uff09" : "\u6709\u4f46\u642c\u8fd0\u6ca1\u751f\u6548\uff08\u624b\u52bf\u5931\u8d25\uff09") : (this.noSpaceForStack(mc, item) ? "\u6709\u4f46\u6ca1\u5730\u65b9\u653e\uff08\u80cc\u5305\u6ca1\u6709\u540c\u7c7b\u53ef\u5408\u5e76\u683c\u3001\u4e5f\u6ca1\u7a7a\u4f4d/\u53ef\u66ff\u6362\u683c\uff09" : "\u6709\u4f46\u642c\u8fd0\u6ca1\u751f\u6548\uff08\u624b\u52bf\u5931\u8d25\uff09"));
        FOElytraLog.warn("%s\uff1a\u76d2\u5b50\u91cc\u6709 %d %s\uff0c\u4f46\u4e00\u53d1\u6ca1\u53d6\u5230 \u2014\u2014 %s", name, avail, unit, why);
    }

    private int bestFoodSlot(MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        List<Item> prio = this.opts.foodPriority();
        int bestSlot = -1;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < containerSlots; ++i) {
            ItemStack stack = ((Slot)handler.slots.get(i)).getStack();
            if (stack.isEmpty() || !this.matchesFood(stack) || this.foodTargetSlot(mc, stack.getItem()) < 0 && this.smallestFireworkHotbarSlot(mc) < 0) continue;
            int rank = 0;
            if (prio != null && !prio.isEmpty()) {
                int idx = prio.indexOf(stack.getItem());
                int n = rank = idx < 0 ? prio.size() : idx;
            }
            if (rank >= bestRank) continue;
            bestRank = rank;
            bestSlot = i;
        }
        return bestSlot;
    }

    private boolean putOutOneStack(MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        int foodSlot;
        PlanItem item = this.plan.get(this.planIndex);
        int n = foodSlot = this.needs.food > 0 ? this.bestFoodSlot(mc, handler, containerSlots) : -1;
        if (this.needs.totems > 0) {
            FOElytraLog.detail("\u56fe\u817e\u6700\u91cd\u8981\uff1a\u672c\u8f6e\u5148\u4ece\u76d2\u5b50\u91cc\u62ff\u56fe\u817e\uff08\u8fd8\u5dee %d \u4e2a\uff09\uff0c\u5176\u5b83\u7269\u8d44/\u6742\u7269\u90fd\u7528\u6765\u7ed9\u5b83\u817e\u683c\u5b50", this.needs.totems);
        }
        for (int i = 0; i < containerSlots; ++i) {
            ItemStack stack = ((Slot)handler.slots.get(i)).getStack();
            if (stack.isEmpty()) continue;
            if (stack.isOf(Items.TOTEM_OF_UNDYING) && this.needs.totems > 0 && !this.exhausted.contains(Items.TOTEM_OF_UNDYING)) {
                int target;
                if (this.noRoomForTotems(mc)) {
                    this.warnTotemNoRoom();
                    this.exhausted.add(Items.TOTEM_OF_UNDYING);
                    this.needs.totems = 0;
                    FOElytraLog.warn("\u56fe\u817e\u5148\u8bb0\u6210\u6682\u65f6\u53d6\u4e0d\u5230\uff08\u80cc\u5305\u6ca1\u5730\u65b9\u653e\u3001\u4e5f\u6ca1\u6709\u80fd\u6362\u51fa\u53bb\u7684\u7269\u8d44\uff09\uff0c\u8fd9\u8f6e\u4e0d\u518d\u4ece\u76d2\u5b50\u91cc\u62ff\u5b83", new Object[0]);
                    return false;
                }
                int n2 = target = mc.player.getInventory().getStack(3).isOf(Items.TOTEM_OF_UNDYING) ? 4 : 3;
                if (this.slotTake(mc, handler, i, target, Items.TOTEM_OF_UNDYING)) {
                    FOElytraLog.info("\u56fe\u817e\u8fdb\u5feb\u6377\u680f\u7b2c %d \u683c\uff08\u6700\u91cd\u8981\uff0c\u4f18\u5148\u62ff\uff09", target + 1);
                    return true;
                }
                if (!this.bulkTake(mc, handler, i, Items.TOTEM_OF_UNDYING)) continue;
                return true;
            }
            if (this.matchesFood(stack) && this.needs.food > 0) {
                if (i != foodSlot) continue;
                int foodTarget = this.foodTargetSlot(mc, stack.getItem());
                if (foodTarget < 0) {
                    foodTarget = this.forceFoodSlotByMovingFirework(mc, handler);
                }
                if (foodTarget < 0) {
                    if (this.boxFoodNoRoom) continue;
                    this.boxFoodNoRoom = true;
                    FOElytraLog.warn("\u76d2\u5185\u69fd %d \u6709 %s x%d\uff0c\u4f46\u5feb\u6377\u680f\u6ca1\u6709\u540c\u79cd\u98df\u7269\u683c\u3001\u6ca1\u6709\u7a7a\u683c\u3001\u4e5f\u6ca1\u6709\u53ef\u66ff\u6362\u683c\u3001\u70df\u82b1\u4e5f\u632a\u4e0d\u8d70 \u2192 \u98df\u7269\u8fd9\u6b21\u771f\u653e\u4e0d\u4e0b", i, stack.getItem().getName().getString(), stack.getCount());
                    continue;
                }
                int foodCount = stack.getCount();
                if (this.slotTake(mc, handler, i, foodTarget, stack.getItem())) {
                    if (this.forcedFoodSlot == foodTarget) {
                        this.forcedFoodSlot = -1;
                        FOElytraLog.info("\u98df\u7269\u653e\u8fdb\u5feb\u6377\u680f\u7b2c %d \u683c\uff08%d \u4e2a\uff09", foodTarget + 1, foodCount);
                    }
                    return true;
                }
                if (!this.mergeIntoPartialStack(mc, handler, i, stack.getItem())) continue;
                return true;
            }
            if (stack.isOf(Items.FIREWORK_ROCKET) && stack.getCount() >= stack.getMaxCount() && this.needs.fireworkStacks > 0) {
                if (this.exhausted.contains(Items.FIREWORK_ROCKET)) {
                    FOElytraLog.detail("\u76d2\u5185\u69fd %d \u6709\u70df\u82b1 x%d\uff0c\u4f46\u70df\u82b1\u5df2\u88ab\u767b\u8bb0\u6210\u672c\u8f6e\u6682\u65f6\u53d6\u4e0d\u5230\uff08\u91cd\u8bd5\u4e24\u6b21\u6ca1\u8fdb\u80cc\u5305\uff09\u2192 \u8fd9\u4e00\u8f6e\u4e0d\u518d\u52a8\u5b83", i, stack.getCount());
                    continue;
                }
                if (this.mergeIntoPartialStack(mc, handler, i, stack.getItem())) {
                    return true;
                }
                return this.bulkTake(mc, handler, i, stack.getItem());
            }
            boolean isSecond = stack.isOf(Items.ELYTRA) && ItemHelper.hasEnchantment(stack, (RegistryKey<Enchantment>)Enchantments.UNBREAKING, 3) && stack.getDamage() < 15;
            boolean bl = item.xpMode() ? stack.isOf(Items.EXPERIENCE_BOTTLE) : isSecond;
            if (!isSecond || stack.getCount() < stack.getMaxCount() || !(item.xpMode() ? this.needs.xpBottles > 0 : this.needs.elytra > 0) || this.exhausted.contains(stack.getItem())) continue;
            if (this.mergeIntoPartialStack(mc, handler, i, stack.getItem())) {
                return true;
            }
            return this.bulkTake(mc, handler, i, stack.getItem());
        }
        return false;
    }

    private boolean mergeIntoPartialStack(MinecraftClient mc, ScreenHandler handler, int rawSlot, Item item) {
        ItemStack src = ((Slot)handler.slots.get(rawSlot)).getStack();
        if (src.isEmpty() || !src.isOf(item)) {
            return false;
        }
        for (int pass = 0; pass < 2; ++pass) {
            int from = pass == 0 ? 0 : 9;
            int to = pass == 0 ? 9 : 36;
            for (int inv = from; inv < to; ++inv) {
                int raw;
                ItemStack s = mc.player.getInventory().getStack(inv);
                if (s.isEmpty() || !s.isOf(item) || s.getCount() >= s.getMaxCount() || (raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, inv)) < 0) continue;
                this.beginTakeVerify(mc, handler, rawSlot, item, SupplyTask.kindOf(item));
                InvHelper.moveStack(handler, rawSlot, raw);
                FOElytraLog.detail("\u5408\u5e76\u53d6\u7269\uff1a\u76d2 #%d %s \u2192 \u80cc\u5305 #%d\uff08\u5df2\u6709 %d/%d\uff09\uff5c\u642c\u8fd0\u524d \u80cc\u5305 %d / \u76d2\u5185 %d", rawSlot, item.getName().getString(), inv, s.getCount(), s.getMaxCount(), this.takeVerify.beforeInv(), this.takeVerify.beforeBox());
                return true;
            }
        }
        return false;
    }

    private static int kindOf(Item item) {
        if (item == Items.FIREWORK_ROCKET) {
            return 1;
        }
        if (item == Items.EXPERIENCE_BOTTLE || item == Items.ELYTRA) {
            return 2;
        }
        if (item == Items.TOTEM_OF_UNDYING) {
            return 3;
        }
        return 4;
    }

    private boolean moveToReplaceSlot(MinecraftClient mc, ScreenHandler handler, int rawSlot, boolean xpMode) {
        if (this.replaceSlots.isEmpty()) {
            this.computeReplaceSlots(mc, xpMode);
        }
        int invIndex = -1;
        int rawTarget = -1;
        for (int checked = 0; checked < 6 && !this.replaceSlots.isEmpty(); ++checked) {
            int candidate = this.replaceSlots.pollFirst();
            ItemStack now = mc.player.getInventory().getStack(candidate);
            if (!this.isJunkNow(now)) {
                ++this.replaceRecheckSkips;
                Integer next = this.replaceSlots.peekFirst();
                FOElytraLog.detail("\u6362\u4f4d\u76ee\u6807 \u80cc\u5305 #%d = %s \u2192 \u5df2\u6392\u9664\uff08\u73b0\u5728\u4e0d\u662f\u6742\u7269\u4e86\uff0cRC-10 \u590d\u68c0\uff09\uff0c\u6539\u9009 #%s", candidate, this.describeStack(now), next == null ? "\uff08\u540d\u5355\u5df2\u7a7a\uff09" : String.valueOf(next));
                continue;
            }
            int raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, candidate);
            if (raw < 0) continue;
            invIndex = candidate;
            rawTarget = raw;
            break;
        }
        if (rawTarget < 0) {
            String what;
            if (!this.starvedWarned) {
                this.starvedWarned = true;
                FOElytraLog.warn("\u6ca1\u591a\u4f59\u69fd\u4f4d\u4e86\uff08\u80cc\u5305 9-35 \u683c\u5168\u662f\u8981\u7559\u7684\u4e1c\u897f\uff09\uff0c\u505c\u6b62\u53d6\u7269", new Object[0]);
            }
            ItemStack stuck = rawSlot >= 0 && rawSlot < handler.slots.size() ? ((Slot)handler.slots.get(rawSlot)).getStack() : ItemStack.EMPTY;
            String string = what = stuck.isEmpty() ? "\u76d2 #" + rawSlot : "\u76d2 #" + rawSlot + " " + stuck.getName().getString() + " x" + stuck.getCount();
            if (!what.equals(this.starvedDetailKey)) {
                Object summary;
                this.starvedDetailKey = what;
                Object object = summary = this.replaceSlotSummary.isEmpty() ? "\uff08\u6ca1\u6709\u53ef\u66ff\u6362\u69fd\u4f4d\u7edf\u8ba1\uff09" : this.replaceSlotSummary;
                if (this.replaceRecheckSkips > 0) {
                    summary = (String)summary + "\uff5c\u70b9\u51fb\u524d\u590d\u68c0\u53c8\u6392\u9664 " + this.replaceRecheckSkips + " \u683c";
                }
                FOElytraLog.detail("\u53d6\u7269\u53d7\u963b\uff08\u62ff\u4e0d\u51fa\u6765\uff09\uff1a\u60f3\u53d6 %s\uff0c\u4f46\u80cc\u5305 9-35 \u6ca1\u6709\u53ef\u7528\u69fd\u4f4d\uff08%s\uff09\u2192 \u672c\u76d2\u653e\u5f03\u8be5\u7269\u54c1\uff1b\u8fd9\u53ea\u8bf4\u660e\u300c\u817e\u4e0d\u51fa\u683c\u5b50\u300d\uff0c\u4e0d\u4ee3\u8868\u76d2\u5b50\u91cc/\u672b\u5f71\u7bb1\u91cc\u6ca1\u6709\uff08\u4e0d\u767b\u8bb0\u6682\u65f6\u53d6\u4e0d\u5230\uff09", what, summary);
            }
            return false;
        }
        ItemStack moving = ((Slot)handler.slots.get(rawSlot)).getStack();
        if (!moving.isEmpty()) {
            this.beginTakeVerify(mc, handler, rawSlot, moving.getItem(), SupplyTask.kindOf(moving.getItem()));
        }
        this.noSneak("\u4e09\u51fb\u4e92\u6362\u524d");
        InvHelper.moveStack(handler, rawSlot, rawTarget);
        FOElytraLog.detail("\u4e09\u51fb\u4e92\u6362\u53d6\u7269\uff08\u6d88\u8017\u4e00\u683c\u53ef\u66ff\u6362\u69fd\uff09\uff1a\u76d2\u5185\u69fd %d \u7684 %s x%d \u2192 \u80cc\u5305 #%d\uff1b\u88ab\u6362\u51fa\u6765\u7684\u4e1c\u897f\u843d\u56de\u76d2\u5185\u69fd %d\uff0c\u4e0d\u4e22", rawSlot, moving.isEmpty() ? "\u7a7a" : moving.getItem().getName().getString(), moving.getCount(), invIndex, rawSlot);
        return true;
    }

    private void consumeNeed(Item item, int taken) {
        int count = Math.max(1, taken);
        if (item == Items.FIREWORK_ROCKET) {
            this.needs.fireworkStacks -= Math.max(1, count / 64);
        } else if (item == Items.EXPERIENCE_BOTTLE) {
            this.needs.xpBottles -= count;
        } else if (item == Items.TOTEM_OF_UNDYING) {
            this.needs.totems -= count;
        } else if (item == Items.ELYTRA) {
            this.needs.elytra -= count;
        } else if (this.matchesFood(item)) {
            this.needs.food -= count;
        }
        if (this.needs.fireworkStacks < 0) {
            this.needs.fireworkStacks = 0;
        }
        if (this.needs.xpBottles < 0) {
            this.needs.xpBottles = 0;
        }
        if (this.needs.food < 0) {
            this.needs.food = 0;
        }
        if (this.needs.totems < 0) {
            this.needs.totems = 0;
        }
        if (this.needs.elytra < 0) {
            this.needs.elytra = 0;
        }
    }

    private int findReceiveSlot(MinecraftClient mc, ItemStack moving) {
        int i;
        PlayerInventory inv = mc.player.getInventory();
        Item item = moving.getItem();
        for (i = 0; i < 36; ++i) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.isOf(item) || s.getCount() >= s.getMaxCount()) continue;
            return i;
        }
        if (item == Items.FIREWORK_ROCKET || item == Items.EXPERIENCE_BOTTLE) {
            for (i = 0; i < 9; ++i) {
                if (!inv.getStack(i).isEmpty()) continue;
                return i;
            }
        }
        for (i = 9; i < 36; ++i) {
            if (!inv.getStack(i).isEmpty()) continue;
            return i;
        }
        for (i = 0; i < 9; ++i) {
            if (!inv.getStack(i).isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private boolean storeOneJunkStack(MinecraftClient mc, ScreenHandler handler) {
        int containerSlots = SupplyTask.containerSlots(handler);
        for (int i = 9; i < 36; ++i) {
            int raw;
            ItemStack s;
            if (this.storeTried.contains(i) || (s = mc.player.getInventory().getStack(i)).isEmpty() || this.isEssential(s) || !this.opts.storeItems().isEmpty() && !this.opts.storeItems().contains(s.getItem()) || (raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, i)) < 0) continue;
            int target = -1;
            for (int c = 0; c < containerSlots; ++c) {
                ItemStack in = ((Slot)handler.slots.get(c)).getStack();
                if (!in.isEmpty() && (!in.isOf(s.getItem()) || in.getCount() >= in.getMaxCount())) continue;
                target = c;
                break;
            }
            this.storeTried.add(i);
            if (target < 0) {
                FOElytraLog.detail("\u76d2\u5185\u6ca1\u6709\u7a7a\u69fd\u4e5f\u6ca1\u6709\u53ef\u5408\u5e76\u7684\u540c\u7c7b\u645e\uff0c%s \u5148\u7559\u5728\u80cc\u5305\uff08\u4e09\u51fb\u4e92\u6362\u8981\u6709\u843d\u70b9\uff09", s.getItem().getName().getString());
                continue;
            }
            this.noSneak("\u585e\u6742\u7269\u8fdb\u76d2\u524d");
            InvHelper.moveStack(handler, raw, target);
            FOElytraLog.detail("\u585e\u6742\u7269\u8fdb\u76d2\uff08\u4e09\u51fb\u4e92\u6362\uff09\uff1a\u80cc\u5305 #%d \u7684 %s x%d \u2192 \u76d2\u5185\u69fd %d", i, s.getItem().getName().getString(), s.getCount(), target);
            return true;
        }
        return false;
    }

    private boolean isEssential(ItemStack s) {
        return s.isOf(Items.FIREWORK_ROCKET) || s.isOf(Items.EXPERIENCE_BOTTLE) || s.isOf(Items.TOTEM_OF_UNDYING) || s.isOf(Items.ELYTRA) || s.isOf(Items.ENDER_CHEST) || ItemHelper.isShulkerBox(s) || this.isPickaxe(s) || this.isSword(s) || this.matchesFood(s) || this.isFoodPriority(s);
    }

    private boolean isFoodPriority(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        List<Item> prio = this.opts.foodPriority();
        return prio != null && !prio.isEmpty() && prio.contains(s.getItem());
    }

    private boolean matchesFood(ItemStack s) {
        if (this.opts.foodItems().isEmpty()) {
            return ItemHelper.isFood(s);
        }
        return this.opts.foodItems().contains(s.getItem());
    }

    private boolean matchesFood(Item item) {
        if (item == null) {
            return false;
        }
        List<Item> food = this.opts.foodItems();
        if (food == null || food.isEmpty()) {
            return SettingHelper.isFood(item);
        }
        return food.contains(item);
    }

    private void breakShulker(MinecraftClient mc) {
        this.noSneak("\u6316\u76d2\u5b50\u524d");
        if (this.shulkerPos == null) {
            this.next(State.REOPEN_EC, 0);
            return;
        }
        InvHelper.closeScreen();
        if (mc.world.getBlockState(this.shulkerPos).isAir()) {
            this.next(State.WAIT_BREAK_SH, 1);
            return;
        }
        if (!this.breakRequested) {
            this.breakTicks = 0;
            this.pickupWait = 0;
            this.waitTicks = 0;
            this.rescueTicks = 0;
            this.rescueProgressTick = 0;
            this.rescueBackpackSeen = -1;
            this.rescueDropSeen = -1;
            this.rescueTarget = null;
            this.mineIdleTicks = 0;
            this.mineRetries = 0;
            this.manualBreakRetries = 0;
            this.mineManual = false;
            this.mineBackFailReason = "";
            this.breakBlockGone = false;
            this.shulkerBreakTarget = ItemHelper.countShulkers((PlayerEntity)mc.player) + 1;
            Block block = mc.world.getBlockState(this.shulkerPos).getBlock();
            if (this.opts.useBaritoneMine() && BaritoneHook.available() && this.hasPickaxe(mc)) {
                if (BaritoneHook.mine(this.shulkerBreakTarget, block)) {
                    this.breakRequested = true;
                    FOElytraLog.tip("\u8ba9 Baritone \u6316\u56de\u6f5c\u5f71\u76d2\uff08\u76ee\u6807\uff1a\u80cc\u5305 %d \u4e2a\uff09", this.shulkerBreakTarget);
                    this.next(State.WAIT_BREAK_SH, 1);
                    return;
                }
            } else if (!this.hasPickaxe(mc)) {
                FOElytraLog.warn("\u5feb\u6377\u680f\u91cc\u6ca1\u6709\u9550\uff1a\u6539\u7528\u5f92\u624b\u6316\u56de\u6f5c\u5f71\u76d2 %s\uff08\u5f92\u624b\u6316\u5f97\u6162\uff0c\u4f46\u76d2\u5b50\u4f1a\u6389\uff09", SupplyTask.posText(this.shulkerPos));
            } else {
                FOElytraLog.warn("Baritone \u6316\u6398\u4e0d\u53ef\u7528\uff0c\u6539\u7528\u6e10\u8fdb\u7834\u574f\uff08\u6389\u843d\u7269\u53ef\u80fd\u4e0d\u4f1a\u81ea\u52a8\u6361\u8d77\uff09", new Object[0]);
            }
            this.breakRequested = true;
        }
        if (BlockBreaker.tick(this.shulkerPos)) {
            this.breakRequested = false;
            if (!mc.world.getBlockState(this.shulkerPos).isAir()) {
                if (this.manualBreakRetries < 2) {
                    ++this.manualBreakRetries;
                    BlockBreaker.reset();
                    InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)this.shulkerPos));
                    FOElytraLog.warn("\u5f92\u624b\u6316\u6ca1\u6316\u6389\u6f5c\u5f71\u76d2\uff08%s\uff09\u2192 \u6362\u4e2a\u89d2\u5ea6\u518d\u6316\u4e00\u6b21\uff08\u7b2c %d/2 \u6b21\uff09", SupplyTask.posText(this.shulkerPos), this.manualBreakRetries);
                    this.breakRequested = true;
                    this.delay = this.opts.actionDelay();
                    return;
                }
                FOElytraLog.warn("\u5f92\u624b\u6316 2 \u6b21\u90fd\u6ca1\u6316\u6389\u6f5c\u5f71\u76d2\uff08%s\uff09", SupplyTask.posText(this.shulkerPos));
                this.leaveShulkerBehind("\u5f92\u624b\u6316 2 \u6b21\u6ca1\u6316\u6389\uff08\u65b9\u5757\u6ca1\u6d88\u5931\uff09", false);
                return;
            }
            this.waitTicks = 0;
            this.next(State.WAIT_BREAK_SH, 2);
        }
    }

    private void waitBreakShulker(MinecraftClient mc) {
        boolean fresh;
        boolean gotIt;
        boolean gone = mc.world.getBlockState(this.shulkerPos).isAir();
        int backpackNow = ItemHelper.countShulkers((PlayerEntity)mc.player);
        boolean bl = gotIt = backpackNow >= this.shulkerBreakTarget;
        if (gone && gotIt) {
            BaritoneHook.stop();
            this.breakRequested = false;
            FOElytraLog.tip("\u6f5c\u5f71\u76d2\u5df2\u6536\u56de\uff08\u80cc\u5305 %d \u4e2a\uff09", backpackNow);
            this.pendingReturn = true;
            this.next(State.REOPEN_EC, this.opts.actionDelay());
            return;
        }
        if (!gone) {
            this.waitBreakShulkerMining(mc, backpackNow);
            return;
        }
        if (!this.breakBlockGone) {
            this.breakBlockGone = true;
            this.waitTicks = 0;
            this.rescueProgressTick = 0;
            this.mineIdleTicks = 0;
            FOElytraLog.detail("\u6f5c\u5f71\u76d2\u5df2\u7ecf\u6316\u6389\uff08%s\uff09\uff0c\u5f00\u59cb\u7b49\u6389\u843d\u7269\u8fdb\u80cc\u5305", SupplyTask.posText(this.shulkerPos));
        }
        this.stopMiningAfterPickupWindow(true);
        ++this.waitTicks;
        this.rescueSupplyDrops(mc, true, backpackNow);
        if (this.waitTicks <= 300) {
            return;
        }
        DropEvidence ev = this.nearbySupplyDrops(mc);
        boolean bl2 = fresh = this.waitTicks - this.rescueProgressTick <= 200;
        if (ev.count() > 0 && fresh && this.waitTicks <= 900) {
            if (this.waitTicks % 40 == 0) {
                FOElytraLog.detail("\u8865\u7ed9\u7bb1\u6389\u843d\u7269\u8fd8\u6ca1\u6361\u5b8c\uff1a\u9644\u8fd1 %d \u4e2a\uff08\u6700\u8fd1 %.1f \u683c\uff0c\u5728 %s\uff09\uff5c\u5df2\u7b49 %d \u79d2\uff0c\u7ee7\u7eed\u8ffd", ev.count(), ev.nearest(), SupplyTask.posText(ev.nearestPos()), this.waitTicks / 20);
            }
            return;
        }
        FOElytraLog.warn("\u6316\u6398\u8865\u7ed9\u7bb1\u5931\u8d25\uff1a\u80cc\u5305\u4ecd %d \u4e2a\uff5c\u9644\u8fd1\u6389\u843d\u7269 %d \u4e2a\uff08\u6700\u8fd1 %s\uff0c\u5728 %s\uff09\uff5c\u53ef\u80fd\u6389\u8fdb\u5ca9\u6d46/\u865a\u7a7a", backpackNow, ev.count(), ev.nearestPos() == null ? "\u65e0" : String.format("%.1f \u683c", ev.nearest()), SupplyTask.posText(ev.nearestPos()));
        BaritoneHook.stop();
        this.breakRequested = false;
        this.recordLostShulker("\u76d2\u5b50\u5df2\u6d88\u5931\u4f46\u6ca1\u8fdb\u80cc\u5305");
        this.pendingReturn = true;
        this.next(State.REOPEN_EC, this.opts.actionDelay());
    }

    private void waitBreakShulkerMining(MinecraftClient mc, int backpackNow) {
        this.stopMiningAfterPickupWindow(false);
        if (this.fireballNearby(mc)) {
            if (this.waitTicks > 40 || this.mineIdleTicks > 0) {
                FOElytraLog.detail("\u9644\u8fd1\u6709\u706b\u7403\uff0c\u6316\u6398\u7b49\u5f85\u8ba1\u65f6\u5148\u6e05\u96f6\uff08%s\uff09", SupplyTask.posText(this.shulkerPos));
            }
            this.waitTicks = 0;
            this.mineIdleTicks = 0;
        }
        ++this.waitTicks;
        if (this.mineManual) {
            boolean breakerDone = BlockBreaker.tick(this.shulkerPos);
            if (!mc.world.getBlockState(this.shulkerPos).isAir()) {
                if (!breakerDone) {
                    return;
                }
                this.retryMineBack(mc, this.shulkerPos, true, this.hasPickaxe(mc) ? "\u5f92\u624b\u6316\u6ca1\u6316\u6389\uff08\u65b9\u5757\u6ca1\u6d88\u5931\uff09" : "\u6ca1\u6709\u9550\u5b50\uff0c\u5f92\u624b\u6316\u6ca1\u6316\u6389");
                return;
            }
            this.mineManual = false;
            return;
        }
        if (BaritoneHook.isMining()) {
            this.mineIdleTicks = 0;
            if (this.waitTicks % 100 == 0) {
                FOElytraLog.info("\u6316\u76d2\u4e2d\u2026\u5df2 %d \u79d2\uff08\u65b9\u5757\u8fd8\u5728 %s\uff0cBaritone \u6b63\u5728\u6316\uff09", this.waitTicks / 20, SupplyTask.posText(this.shulkerPos));
            }
        } else {
            ++this.mineIdleTicks;
            if (this.mineIdleTicks >= 60) {
                this.mineIdleTicks = 0;
                if (this.mineRetries >= 3) {
                    String why = this.mineFailClass(mc, "\u6f5c\u5f71\u76d2");
                    FOElytraLog.warn("\u6f5c\u5f71\u76d2\u6316\u4e0d\u52a8\uff08\u5df2\u91cd\u8bd5 %d \u6b21\uff09\uff1a%s", this.mineRetries, why);
                    this.leaveShulkerBehind(why);
                    return;
                }
                if (!this.mineManual && this.opts.useBaritoneMine() && BaritoneHook.available() && this.hasPickaxe(mc)) {
                    ++this.mineRetries;
                    Block block = mc.world.getBlockState(this.shulkerPos).getBlock();
                    boolean issued = BaritoneHook.mine(this.shulkerBreakTarget, block);
                    FOElytraLog.warn("\u91cd\u65b0\u4e0b\u53d1\u6316\u76d2\uff1a\u7b2c %d/%d \u6b21\uff08\u65b9\u5757\u8fd8\u5728 %s\uff09\uff5c\u4e0b\u53d1%s", this.mineRetries, 3, SupplyTask.posText(this.shulkerPos), issued ? "\u6210\u529f" : "\u5931\u8d25");
                    if (!issued) {
                        this.retryMineBack(mc, this.shulkerPos, true, this.mineFailClass(mc, "\u6f5c\u5f71\u76d2"));
                        return;
                    }
                } else {
                    this.retryMineBack(mc, this.shulkerPos, true, this.hasPickaxe(mc) ? "\u6316\u77ff\u8fdb\u7a0b\u53cd\u590d\u505c\u6389" : "\u6ca1\u6709\u9550\u5b50");
                    return;
                }
            }
        }
        if (this.waitTicks > 400) {
            String why = "\u7b49 20 \u79d2\u6ca1\u6316\u6389\uff08\u91cd\u53d1\u6316\u77ff " + this.mineRetries + " \u6b21\uff0c\u80cc\u5305\u6f5c\u5f71\u76d2 " + backpackNow + " \u4e2a\uff09";
            FOElytraLog.warn("\u7b49\u6316\u76d2\u7b49\u5230 %d \u79d2\u8fd8\u6ca1\u6316\u6389\uff08\u65b9\u5757\u8fd8\u5728 %s\uff0c\u91cd\u53d1\u6316\u77ff %d \u6b21\uff0c\u80cc\u5305\u6f5c\u5f71\u76d2 %d \u4e2a\uff09", 20, SupplyTask.posText(this.shulkerPos), this.mineRetries, backpackNow);
            this.leaveShulkerBehind(why);
        }
    }

    private void leaveShulkerBehind(String why) {
        this.leaveShulkerBehind(why, true);
    }

    private void leaveShulkerBehind(String why, boolean returnBox) {
        MinecraftClient mc;
        ++this.runLeftShulkers;
        String pos = SupplyTask.posText(this.shulkerPos);
        this.runLeftPos = this.runLeftPos.isEmpty() ? pos : this.runLeftPos + "\u3001" + pos;
        ++pendingLeftShulkers;
        String string = pendingLeftPos = pendingLeftPos.isEmpty() ? pos : pendingLeftPos + "\u3001" + pos;
        if (this.shulkerPos != null) {
            this.placeRejected.add(this.shulkerPos.toImmutable());
        }
        int backpack = (mc = MinecraftClient.getInstance()) == null || mc.player == null ? 0 : ItemHelper.countShulkers((PlayerEntity)mc.player);
        FOElytraLog.warn("\u6f5c\u5f71\u76d2\u6ca1\u6316\u6389\uff0c\u8fd8\u7559\u5728 %s \u2014\u2014 \u5df2\u505c\u624b\uff08\u4f60\u53ef\u4ee5\u81ea\u5df1\u6316\uff0c\u6216\u4e0b\u6b21\u8865\u7ed9\u518d\u6765\uff09", pos);
        FOElytraLog.detail("\u8fd9\u6b21\u4e0d\u7b97\u4e22\u5931\uff08\u65b9\u5757\u6ca1\u4e22\uff09\uff1a\u539f\u56e0 %s\uff5c\u80cc\u5305\u6f5c\u5f71\u76d2 %d \u4e2a\uff5c%s \u5df2\u8bb0\u8fdb\u672c\u6b21\u4e0d\u518d\u5c1d\u8bd5\u7684\u9ed1\u540d\u5355", why, backpack, pos);
        BaritoneHook.stop();
        this.breakRequested = false;
        this.pendingReturn = returnBox;
        this.planIndex = this.plan.size();
        if (this.recoverNeeded()) {
            FOElytraLog.warn("\u4e16\u754c\u91cc\u8fd8\u6709\u6211\u65b9\u653e\u4e0b\u7684\u4e1c\u897f \u2192 \u4e0d\u518d\u653e\u4e0b\u4e00\u4e2a\u76d2\u5b50\uff0c\u5148\u628a\u7559\u4e0b\u7684\u5168\u6536\u56de\u6765\uff08\u539f\u56e0\uff1a%s\uff09", why);
            this.beginRecover("\u6709\u76d2\u5b50\u6ca1\u6316\u56de\u6765\uff1a" + why);
            return;
        }
        this.next(State.REOPEN_EC, this.opts.actionDelay());
    }

    private String mineFailClass(MinecraftClient mc, String what) {
        if (!BaritoneHook.available()) {
            return "\u6ca1\u6709 Baritone \u5b9e\u4f8b\uff08\u5f92\u624b\u6316\u4e5f\u6ca1\u6316\u6389\uff09";
        }
        if (!this.opts.useBaritoneMine()) {
            return "\u6ca1\u5f00 Baritone \u6316\u6398\uff0c\u6e10\u8fdb\u7834\u574f\u4e5f\u6ca1\u6316\u6389";
        }
        if (!this.hasPickaxe(mc)) {
            return "\u6ca1\u6709\u9550\u5b50\uff0c\u5f92\u624b\u6316\u4e5f\u6ca1\u6316\u6389 " + what;
        }
        return "\u6316\u77ff\u8fdb\u7a0b\u53cd\u590d\u505c\u6389/\u65b9\u5757\u6ca1\u6d88\u5931\uff08\u91cd\u8bd5 3 \u6b21\uff09";
    }

    private void retryMineBack(MinecraftClient mc, BlockPos pos, boolean shulker, String why) {
        if (this.mineRetries < 3) {
            ++this.mineRetries;
            BaritoneHook.stop();
            BlockBreaker.reset();
            this.mineManual = true;
            this.mineIdleTicks = 0;
            InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)pos));
            FOElytraLog.warn("%s \u7b2c %d/%d \u6b21\u5931\u8d25\uff08%s\uff09\u2192 \u6362\u6210\u5f92\u624b\u6316 %s\uff08\u5df2\u5bf9\u51c6\u65b9\u5757\uff09", shulker ? "\u6316\u56de\u6f5c\u5f71\u76d2" : "\u6316\u56de\u672b\u5f71\u7bb1", this.mineRetries, 3, why, SupplyTask.posText(pos));
            return;
        }
        this.mineBackFailReason = why;
        FOElytraLog.warn("%s\u6ca1\u80fd\u6536\u56de\uff1a%s\uff08%s\uff09\u2014\u2014 \u8bf7\u81ea\u5df1\u53bb\u62ff", shulker ? "\u6f5c\u5f71\u76d2" : "\u672b\u5f71\u7bb1", SupplyTask.posText(pos), why);
        if (shulker) {
            this.leaveShulkerBehind(why);
        } else {
            this.giveUpEnderChest(why);
        }
    }

    private void rescueSupplyDrops(MinecraftClient mc, boolean blockGone, int backpackNow) {
        boolean progressed;
        if (!blockGone) {
            this.rescueTicks = 0;
            this.rescueTarget = null;
            return;
        }
        DropEvidence ev = this.nearbySupplyDrops(mc);
        boolean bl = progressed = this.rescueBackpackSeen >= 0 && (backpackNow > this.rescueBackpackSeen || ev.count() > 0 && ev.count() < this.rescueDropSeen);
        if (progressed) {
            FOElytraLog.detail("\u8ffd\u6389\u843d\u7269\u6709\u8fdb\u5c55\uff1a\u80cc\u5305\u6f5c\u5f71\u76d2 %d \u2192 %d\uff5c\u9644\u8fd1\u6389\u843d\u7269 %d \u4e2a\uff0c\u7ee7\u7eed\u7b49", this.rescueBackpackSeen, backpackNow, ev.count());
            this.rescueProgressTick = this.waitTicks;
        }
        this.rescueBackpackSeen = backpackNow;
        this.rescueDropSeen = ev.count();
        ++this.rescueTicks;
        if (ev.count() <= 0) {
            if (this.rescueTarget != null) {
                this.rescueTarget = null;
                FOElytraLog.detail("\u9644\u8fd1\u5df2\u7ecf\u6ca1\u6709\u76f8\u5173\u6389\u843d\u7269\u4e86\uff08\u80cc\u5305\u6f5c\u5f71\u76d2 %d \u4e2a\uff09", backpackNow);
            }
            return;
        }
        if (this.rescueTicks % 10 != 1 && this.rescueTarget != null) {
            return;
        }
        BlockPos pos = ev.nearestPos();
        if (pos == null) {
            return;
        }
        if (pos.equals((Object)this.rescueTarget)) {
            FOElytraLog.detail("\u8ffd\u6389\u843d\u7269\uff1a\u8fd8\u5728 %s\uff08\u6700\u8fd1 %.1f \u683c\uff0c\u5171 %d \u4e2a\uff09\uff0c\u7ee7\u7eed\u7b49\u5b83\u8fdb\u80cc\u5305", SupplyTask.posText(pos), ev.nearest(), ev.count());
            return;
        }
        this.rescueTarget = pos.toImmutable();
        this.rescueProgressTick = this.waitTicks;
        if (!BaritoneHook.ready()) {
            FOElytraLog.detail("\u8ffd\u6389\u843d\u7269\uff1aBaritone \u4e0d\u53ef\u7528\uff0c\u53ea\u80fd\u7b49\u539f\u5730\u81ea\u52a8\u6361\u53d6\uff08%s\uff0c\u6700\u8fd1 %.1f \u683c\uff09", SupplyTask.posText(pos), ev.nearest());
            return;
        }
        FOElytraLog.info("\u8865\u7ed9\u7bb1\u6389\u843d\u7269\u5728 %s\uff08\u6700\u8fd1 %.1f \u683c\uff0c\u5171 %d \u4e2a\uff09\uff1a\u8ba9 Baritone \u8d70\u8fc7\u53bb\u6361", SupplyTask.posText(pos), ev.nearest(), ev.count());
        BaritoneHook.stop();
        BaritoneHook.command("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private DropEvidence nearbySupplyDrops(MinecraftClient mc) {
        return this.dropsNear(mc, this.shulkerPos, false);
    }

    private DropEvidence dropsNear(MinecraftClient mc, BlockPos origin, boolean ecLoot) {
        if (mc.world == null || origin == null) {
            return new DropEvidence(0, -1.0, null);
        }
        Box box = new Box(origin).expand(8.0);
        List<ItemEntity> ents = mc.world.getEntitiesByClass(ItemEntity.class, box, e -> e != null && e.isAlive() && !e.getStack().isEmpty() && (ecLoot ? SupplyTask.isEnderChestLoot(e.getStack()) : SupplyTask.isSupplyLoot(e.getStack())));
        int count = 0;
        double nearest = -1.0;
        BlockPos nearestPos = null;
        for (ItemEntity e2 : ents) {
            count += e2.getStack().getCount();
            double d = e2.squaredDistanceTo((double)origin.getX() + 0.5, (double)origin.getY() + 0.5, (double)origin.getZ() + 0.5);
            if (nearestPos != null && !(d < nearest)) continue;
            nearest = d;
            nearestPos = e2.getBlockPos();
        }
        if (nearestPos == null) {
            return new DropEvidence(0, -1.0, null);
        }
        return new DropEvidence(count, Math.sqrt(nearest), nearestPos.toImmutable());
    }

    private static boolean isEnderChestLoot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.isOf(Items.ENDER_CHEST) || stack.isOf(Items.OBSIDIAN);
    }

    private static boolean isSupplyLoot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (ItemHelper.isShulkerBox(stack)) {
            return true;
        }
        if (stack.isOf(Items.FIREWORK_ROCKET) || stack.isOf(Items.EXPERIENCE_BOTTLE) || stack.isOf(Items.TOTEM_OF_UNDYING) || stack.isOf(Items.ELYTRA)) {
            return true;
        }
        return ItemHelper.isFood(stack);
    }

    private void recordLostShulker(String why) {
        ++this.runLostShulkers;
        String pos = SupplyTask.posText(this.shulkerPos);
        this.runLostPos = this.runLostPos.isEmpty() ? pos : this.runLostPos + "\u3001" + pos;
        ++pendingLostShulkers;
        pendingLostPos = pendingLostPos.isEmpty() ? pos : pendingLostPos + "\u3001" + pos;
        FOElytraLog.warn("\u6f5c\u5f71\u76d2\u6ca1\u6536\u56de\uff08%s\uff09\uff0c\u8bb0\u4e3a\u4e22\u5931 1 \u4e2a\uff1a%s", why, pos);
    }

    private void returnShulker(MinecraftClient mc) {
        HandledScreen<?> screen = InvHelper.currentContainerScreen(this.ecTitle);
        if (screen == null) {
            this.fail("\u653e\u56de\u76d2\u5b50\u65f6\u672b\u5f71\u7bb1\u754c\u9762\u5df2\u5173\u95ed");
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int inv = this.findShulkerAnywhere(mc);
        int boxes = ItemHelper.countShulkers((PlayerEntity)mc.player);
        int raw = this.findShulkerRawAnywhere(mc, handler);
        if (raw < 0) {
            FOElytraLog.warn("\u653e\u56de\u524d\u627e\u4e0d\u5230\u80fd\u64cd\u4f5c\u7684\u6f5c\u5f71\u76d2\uff08\u80cc\u5305\u6f5c\u5f71\u76d2 %d \u4e2a\uff5c\u627e\u5230\u7684\u80cc\u5305\u683c #%d\uff5c\u5feb\u6377\u680f\u7a7a\u4f4d %d\uff5c\u53ef\u66ff\u6362\u5feb\u6377\u680f %d\uff5c\u5bb9\u5668\u7a7a\u4f4d %d\uff09\u2192 \u8fd9\u6b21\u8df3\u8fc7\u653e\u56de\uff0c\u628a\u672b\u5f71\u7bb1\u6536\u597d\u5c31\u8d70", boxes, inv, this.countEmptyHotbar(mc), this.countReplaceableHotbar(mc), this.countEmptyContainer(handler, -1));
            this.pendingReturn = false;
            this.next(State.CLOSE_EC, this.opts.actionDelay());
            return;
        }
        this.returnVerifyBefore = boxes;
        this.returnVerifyTicks = 0;
        this.returnAttempt = -1;
        this.returnTargetSlot = this.shulkerRawSlot;
        this.shulkerHotbarSlot = inv >= 0 && inv < 9 ? inv : this.shulkerHotbarSlot;
        this.issueReturn(mc, handler, raw);
        this.next(State.VERIFY_RETURN, 1);
    }

    private void issueReturn(MinecraftClient mc, ScreenHandler handler, int raw) {
        ++this.returnAttempt;
        StringBuilder how = new StringBuilder();
        if (this.returnAttempt == 0) {
            how.append("\u6574\u645e\u642c\u8fd0\uff08SHIFT \u70b9\u51fb\uff09\u8fdb\u672b\u5f71\u7bb1");
            InvHelper.quickMove(handler, raw);
        } else if (this.returnAttempt == 1) {
            int empty;
            int n = empty = this.countEmptyContainer(handler, -1) > 0 ? this.firstEmptyContainer(handler) : -1;
            if (empty >= 0) {
                this.returnTargetSlot = empty;
                how.append("\u4e24\u6bb5\u5f0f\u70b9\u8fdb\u5bb9\u5668\u7a7a\u69fd ").append(empty);
                InvHelper.moveStack(handler, raw, empty);
            } else {
                int swapWith;
                this.returnAttempt = 2;
                this.returnTargetSlot = swapWith = this.pickSwapTarget(handler);
                how.append((String)(swapWith >= 0 ? "\u4e09\u65b9\u4ea4\u6362\uff1a\u548c\u5bb9\u5668\u69fd " + swapWith + " \u4e92\u6362\u5185\u5bb9\uff08\u539f\u6765\u90a3\u4ef6\u4e1c\u897f\u4f1a\u843d\u56de\u6211\u7684\u80cc\u5305\u683c\uff0c\u4e0d\u4f1a\u4e22\uff09" : "\u4e09\u65b9\u4ea4\u6362\u5931\u8d25\uff1a\u5bb9\u5668\u91cc\u6ca1\u6709\u53ef\u6362\u7684\u69fd\u4f4d"));
                if (swapWith >= 0) {
                    InvHelper.moveStack(handler, raw, swapWith);
                }
            }
        } else {
            int swapWith;
            this.returnTargetSlot = swapWith = this.pickSwapTarget(handler);
            how.append((String)(swapWith >= 0 ? "\u4e09\u65b9\u4ea4\u6362\uff1a\u548c\u5bb9\u5668\u69fd " + swapWith + " \u4e92\u6362\u5185\u5bb9\uff08\u539f\u6765\u90a3\u4ef6\u4e1c\u897f\u4f1a\u843d\u56de\u6211\u7684\u80cc\u5305\u683c\uff0c\u4e0d\u4f1a\u4e22\uff09" : "\u4e09\u65b9\u4ea4\u6362\u5931\u8d25\uff1a\u5bb9\u5668\u91cc\u6ca1\u6709\u53ef\u6362\u7684\u69fd\u4f4d"));
            if (swapWith >= 0) {
                InvHelper.moveStack(handler, raw, swapWith);
            }
        }
        FOElytraLog.detail("\u653e\u56de\u6f5c\u5f71\u76d2\uff08\u7b2c %d \u6b21\u5c1d\u8bd5\uff09\uff1a%s\uff5c\u6e90 raw=%d\uff5c\u76ee\u6807\u69fd %d\uff5c\u5bb9\u5668\u7a7a\u4f4d %d\uff5c\u5feb\u6377\u680f\u7a7a\u4f4d %d", this.returnAttempt + 1, how, raw, this.returnTargetSlot, this.countEmptyContainer(handler, this.returnTargetSlot), this.countEmptyHotbar(mc));
    }

    private int pickSwapTarget(ScreenHandler handler) {
        if (this.shulkerRawSlot >= 0 && this.shulkerRawSlot < SupplyTask.containerSlots(handler)) {
            return this.shulkerRawSlot;
        }
        int limit = SupplyTask.containerSlots(handler);
        for (int i = 0; i < limit; ++i) {
            if (!ItemHelper.isShulkerBox(((Slot)handler.slots.get(i)).getStack())) continue;
            return i;
        }
        return -1;
    }

    private int firstEmptyContainer(ScreenHandler handler) {
        int limit = SupplyTask.containerSlots(handler);
        for (int i = 0; i < limit; ++i) {
            if (!((Slot)handler.slots.get(i)).getStack().isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private int countEmptyContainer(ScreenHandler handler, int ignore) {
        if (handler == null) {
            return 0;
        }
        int limit = SupplyTask.containerSlots(handler);
        int n = 0;
        for (int i = 0; i < limit; ++i) {
            if (i == ignore || !((Slot)handler.slots.get(i)).getStack().isEmpty()) continue;
            ++n;
        }
        return n;
    }

    private int countEmptyHotbar(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            ++n;
        }
        return n;
    }

    private int countReplaceableHotbar(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 9; ++i) {
            if (!this.isJunkNow(mc.player.getInventory().getStack(i))) continue;
            ++n;
        }
        return n;
    }

    private int findShulkerAnywhere(MinecraftClient mc) {
        for (int i = 0; i < 36; ++i) {
            if (!ItemHelper.isShulkerBox(mc.player.getInventory().getStack(i))) continue;
            return i;
        }
        return -1;
    }

    private int findShulkerRawAnywhere(MinecraftClient mc, ScreenHandler handler) {
        int raw;
        if (handler == null) {
            return -1;
        }
        int inv = this.findShulkerAnywhere(mc);
        if (inv >= 0 && (raw = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, inv)) >= 0) {
            return raw;
        }
        for (int i = 0; i < handler.slots.size(); ++i) {
            Slot slot = (Slot)handler.slots.get(i);
            if (!(slot.inventory instanceof PlayerInventory) || !ItemHelper.isShulkerBox(slot.getStack())) continue;
            return i;
        }
        return -1;
    }

    private void verifyReturn(MinecraftClient mc) {
        HandledScreen<?> screen = InvHelper.currentContainerScreen(this.ecTitle);
        if (screen == null) {
            this.fail("\u653e\u56de\u76d2\u5b50\u65f6\u672b\u5f71\u7bb1\u754c\u9762\u5df2\u5173\u95ed");
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        boolean inEc = this.returnTargetSlot >= 0 && this.returnTargetSlot < handler.slots.size() && ItemHelper.isShulkerBox(handler.getSlot(this.returnTargetSlot).getStack());
        int now = ItemHelper.countShulkers((PlayerEntity)mc.player);
        if (inEc || now < this.returnVerifyBefore) {
            FOElytraLog.tip("\u6f5c\u5f71\u76d2\u5df2\u653e\u56de\u672b\u5f71\u7bb1\u69fd\u4f4d %d\uff08\u80cc\u5305 %d \u2192 %d \u4e2a\uff09", this.returnTargetSlot, this.returnVerifyBefore, now);
            this.pendingReturn = false;
            this.shulkerHotbarSlot = -1;
            this.nextBoxAfterReturn(mc);
            return;
        }
        ++this.returnVerifyTicks;
        if (this.returnVerifyTicks <= 30) {
            return;
        }
        if (this.returnAttempt < 2) {
            int raw = this.findShulkerRawAnywhere(mc, handler);
            if (raw < 0) {
                this.failReturnBox(mc, handler, "\u80cc\u5305\u91cc\u5df2\u7ecf\u627e\u4e0d\u5230\u90a3\u4e2a\u76d2\u5b50\u4e86\uff08\u53ef\u80fd\u88ab\u522b\u7684\u64cd\u4f5c\u632a\u8d70\uff09");
                return;
            }
            FOElytraLog.warn("\u653e\u56de\u7b2c %d \u6b21\u6ca1\u751f\u6548\uff08\u80cc\u5305\u6f5c\u5f71\u76d2\u4ecd %d \u4e2a\uff09\u2192 \u6362\u65b9\u5f0f\u518d\u653e\u4e00\u6b21", this.returnAttempt + 1, now);
            this.returnVerifyTicks = 0;
            this.issueReturn(mc, handler, raw);
            return;
        }
        this.failReturnBox(mc, handler, "\u4e09\u79cd\u653e\u56de\u65b9\u5f0f\u90fd\u6ca1\u751f\u6548\uff08\u53ef\u80fd\u5bb9\u5668\u69fd\u4f4d\u88ab\u670d\u52a1\u5668\u9501\u4f4f\uff09");
    }

    private void failReturnBox(MinecraftClient mc, ScreenHandler handler, String why) {
        int inv = this.findShulkerAnywhere(mc);
        FOElytraLog.warn("\u6f5c\u5f71\u76d2\u653e\u56de\u5931\u8d25\uff1a%s\uff5c\u80cc\u5305\u6f5c\u5f71\u76d2 %d \u4e2a\uff5c\u627e\u5230\u7684\u80cc\u5305\u683c #%d\uff5c\u5feb\u6377\u680f\u7a7a\u4f4d %d\uff5c\u53ef\u66ff\u6362\u5feb\u6377\u680f %d\uff5c\u5bb9\u5668\u7a7a\u4f4d %d", why, ItemHelper.countShulkers((PlayerEntity)mc.player), inv, this.countEmptyHotbar(mc), this.countReplaceableHotbar(mc), this.countEmptyContainer(handler, -1));
        this.fail("\u6f5c\u5f71\u76d2\u6ca1\u80fd\u653e\u56de\u672b\u5f71\u7bb1\uff08" + why + "\uff09\uff1b\u76d2\u5b50\u8fd8\u5728\u80cc\u5305\u91cc\uff0c\u6ca1\u4e22");
    }

    private void nextBoxAfterReturn(MinecraftClient mc) {
        ++this.planIndex;
        this.resetBoxTakeCounters();
        this.computeNeeds(mc);
        if (this.planIndex < this.plan.size() && this.needs.needsAnyItem()) {
            this.waitTicks = 0;
            this.pendingReturn = false;
            int nextSlot = this.plan.get(this.planIndex).rawSlot();
            FOElytraLog.info("\u76d2\u5b50\u5df2\u653e\u56de\uff0c\u672b\u5f71\u7bb1\u754c\u9762\u8fd8\u5f00\u7740\uff1a\u63a5\u7740\u5f00\u6e05\u5355\u91cc\u7684\u4e0b\u4e00\u4e2a\u76d2\u5b50 \u69fd\u4f4d%d\uff08\u7b2c %d/%d \u4e2a\uff09", nextSlot, this.planIndex + 1, this.plan.size());
            this.next(State.TAKE_SHULKER, this.opts.actionDelay());
            return;
        }
        this.next(State.CLOSE_EC, this.opts.actionDelay());
    }

    private void nextShulker(MinecraftClient mc) {
        String needsBefore = this.needs.toString();
        this.computeNeeds(mc);
        if (!needsBefore.equals(this.needs.toString())) {
            FOElytraLog.detail("NEXT_SH \u9700\u6c42\u6309\u80cc\u5305\u91cd\u7b97\uff1a%s \u2192 %s\uff08\u589e\u91cf\u8d26\u4e0e\u80cc\u5305\u4e0d\u4e00\u81f4\u65f6\u4ee5\u80cc\u5305\u4e3a\u51c6\uff09", needsBefore, this.needs);
        }
        if (!this.needs.isEmpty()) {
            if (!this.plan.isEmpty() && this.takeRounds < 3) {
                ++this.takeRounds;
                this.planIndex = 0;
                FOElytraLog.warn("\u6e05\u5355\u91cc\u7684\u76d2\u5b50\u90fd\u5f00\u5b8c\u4e86\u4f46\u4ecd\u6709\u7f3a\u53e3\uff1a%s \u2192 \u518d\u5f00\u4e00\u8f6e\uff08\u7b2c %d/%d \u8f6e\uff1a\u628a\u591a\u51fa\u6765\u7684\u7269\u8d44/\u6742\u7269\u6362\u8fdb\u76d2\u5b50\u817e\u683c\u5b50\uff0c\u518d\u53d6\u5c11\u7684\uff0c\u76f4\u5230\u548c\u9884\u8bbe\u6570\u91cf\u4e00\u81f4\uff09", this.needs, this.takeRounds, 3);
                this.next(State.SCAN, this.opts.actionDelay());
                return;
            }
            this.markExhausted();
            FOElytraLog.warn("\u6e05\u5355\u91cc\u7684\u76d2\u5b50\u90fd\u5f00\u5b8c\u4e86\uff0c\u4f46\u4ecd\u6709\u7f3a\u53e3\uff1a%s", this.needs);
        } else {
            FOElytraLog.info("\u6e05\u5355\u53d6\u5b8c\uff0c\u9700\u6c42\u5df2\u7ecf\u6ee1\u8db3\uff08\u672c\u76d2\u53d6\u5230 \u70df\u82b1 %d \u7ec4/\u74f6 %d/\u98df\u7269 %d/\u56fe\u817e %d/\u9798\u7fc5 %d\uff09", this.boxFwTaken, this.boxXpTaken, this.boxFoodTaken, this.boxTotemTaken, this.boxElytraTaken);
        }
        this.warnUnopenedPlanBoxes("\u672c\u8f6e\u53d6\u7269\u6536\u5c3e");
        this.next(State.BREAK_EC, this.opts.actionDelay());
    }

    private void breakEnderChest(MinecraftClient mc) {
        this.noSneak("\u6316\u672b\u5f71\u7bb1\u524d");
        InvHelper.closeScreen();
        if (!this.opts.autoPickupEnderChest()) {
            this.next(State.DONE, 0);
            return;
        }
        if (this.ecPos == null || mc.world.getBlockState(this.ecPos).isAir()) {
            this.next(State.DONE, 0);
            return;
        }
        if (!this.breakRequested) {
            this.breakTicks = 0;
            this.pickupWait = 0;
            this.mineIdleTicks = 0;
            this.mineRetries = 0;
            this.manualBreakRetries = 0;
            this.mineManual = false;
            this.mineBackFailReason = "";
            this.ecBlockGone = false;
            this.ecItemBefore = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.ENDER_CHEST);
            this.obsidianBefore = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.OBSIDIAN);
            boolean silk = this.hasSilkTouch(mc);
            this.ecBreakTarget = (silk ? this.ecItemBefore : this.obsidianBefore) + 1;
            if (silk) {
                FOElytraLog.tip("\u68c0\u6d4b\u5230\u7cbe\u51c6\u91c7\u96c6\u9550\uff1a\u672b\u5f71\u7bb1\u4f1a\u6389\u672b\u5f71\u7bb1\u672c\u8eab\uff0c\u6309\u300c\u672b\u5f71\u7bb1 %d \u4e2a\u300d\u6821\u9a8c", this.ecBreakTarget);
            }
            Block block = mc.world.getBlockState(this.ecPos).getBlock();
            if (this.opts.useBaritoneMine() && BaritoneHook.available() && this.hasPickaxe(mc)) {
                if (BaritoneHook.mine(this.ecBreakTarget, block)) {
                    this.breakRequested = true;
                    if (!silk) {
                        FOElytraLog.tip("\u8ba9 Baritone \u6316\u56de\u672b\u5f71\u7bb1\uff08\u76ee\u6807\uff1a\u9ed1\u66dc\u77f3 %d \u4e2a\uff09", this.ecBreakTarget);
                    }
                    this.waitTicks = 0;
                    this.next(State.WAIT_BREAK_EC, 1);
                    return;
                }
            } else if (!this.hasPickaxe(mc)) {
                FOElytraLog.warn("\u6ca1\u6709\u9550\u5b50\uff0c\u672b\u5f71\u7bb1\u5f92\u624b\u6316\u4e0d\u6389\u843d\uff08\u6316\u4e86\u5c31\u662f\u767d\u6254\uff09\u2192 \u5148\u7559\u7740 %s\uff08\u672b\u5f71\u7bb1 %d \u4e2a / \u9ed1\u66dc\u77f3 %d \u4e2a\uff09", SupplyTask.posText(this.ecPos), this.ecItemBefore, this.obsidianBefore);
                if (this.recoverNeeded()) {
                    FOElytraLog.warn("\u4e16\u754c\u91cc\u8fd8\u6709\u6211\u65b9\u653e\u4e0b\u7684\u672b\u5f71\u7bb1 \u2192 \u8fd9\u6b21\u8865\u7ed9\u5230\u6b64\u4e3a\u6b62\uff0c\u5148\u628a\u7559\u4e0b\u7684\u6536\u56de\u6765\uff08\u6216\u81ea\u5df1\u62ff\uff09", new Object[0]);
                    this.beginRecover("\u6ca1\u6709\u9550\u5b50\uff0c\u672b\u5f71\u7bb1\u6316\u4e0d\u56de\u6765");
                    return;
                }
                this.next(State.DONE, 0);
                return;
            }
            FOElytraLog.warn("Baritone \u6316\u6398\u4e0d\u53ef\u7528\uff0c\u6539\u7528\u6e10\u8fdb\u7834\u574f", new Object[0]);
            this.breakRequested = true;
        }
        if (BlockBreaker.tick(this.ecPos)) {
            this.breakRequested = false;
            if (!mc.world.getBlockState(this.ecPos).isAir()) {
                if (this.manualBreakRetries < 2) {
                    ++this.manualBreakRetries;
                    BlockBreaker.reset();
                    InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)this.ecPos));
                    FOElytraLog.warn("\u672b\u5f71\u7bb1\u6ca1\u6316\u6389\uff08%s\uff09\u2192 \u6362\u4e2a\u89d2\u5ea6\u518d\u6316\u4e00\u6b21\uff08\u7b2c %d/2 \u6b21\uff09", SupplyTask.posText(this.ecPos), this.manualBreakRetries);
                    this.breakRequested = true;
                    this.delay = this.opts.actionDelay();
                    return;
                }
                FOElytraLog.warn("\u672b\u5f71\u7bb1\u6316\u4e86 2 \u6b21\u90fd\u6ca1\u6316\u6389\uff0c\u8fd8\u7559\u5728\u539f\u5730 %s", SupplyTask.posText(this.ecPos));
                this.giveUpEnderChest("\u6316\u4e86 2 \u6b21\u6ca1\u6316\u6389\uff08\u65b9\u5757\u6ca1\u6d88\u5931\uff09");
                return;
            }
            this.waitTicks = 0;
            this.next(State.WAIT_BREAK_EC, 2);
        }
    }

    private void waitBreakEnderChest(MinecraftClient mc) {
        boolean gotIt;
        boolean gone = mc.world.getBlockState(this.ecPos).isAir();
        int ecNow = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.ENDER_CHEST);
        int obsidianNow = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.OBSIDIAN);
        boolean bl = gotIt = ecNow > this.ecItemBefore || obsidianNow > this.obsidianBefore;
        if (gone && gotIt) {
            BaritoneHook.stop();
            this.breakRequested = false;
            FOElytraLog.tip("\u672b\u5f71\u7bb1\u5df2\u6536\u56de\uff08\u672b\u5f71\u7bb1 %d \u4e2a / \u9ed1\u66dc\u77f3 %d \u4e2a\uff09", ecNow, obsidianNow);
            this.next(State.DONE, 0);
            return;
        }
        if (!gone) {
            this.waitBreakEnderChestMining(mc, ecNow, obsidianNow);
            return;
        }
        if (!this.ecBlockGone) {
            this.ecBlockGone = true;
            this.waitTicks = 0;
            this.mineIdleTicks = 0;
            FOElytraLog.detail("\u672b\u5f71\u7bb1\u5df2\u7ecf\u6316\u6389\uff08%s\uff09\uff0c\u5f00\u59cb\u7b49\u6389\u843d\u7269\u8fdb\u80cc\u5305", SupplyTask.posText(this.ecPos));
        }
        this.stopMiningAfterPickupWindow(true);
        ++this.waitTicks;
        if (this.waitTicks > 200) {
            FOElytraLog.warn("\u672b\u5f71\u7bb1\u5df2\u7ecf\u6316\u6389\uff08%s\uff09\u4f46\u6ca1\u8fdb\u80cc\u5305\uff1a\u672b\u5f71\u7bb1 %d \u4e2a / \u9ed1\u66dc\u77f3 %d \u4e2a\uff08\u80cc\u5305\u6ee1\uff1f\u6389\u8fdc\u5904\uff1f\u7cbe\u51c6\u91c7\u96c6\u6ca1\u6361\u5230\uff1f\uff09", SupplyTask.posText(this.ecPos), ecNow, obsidianNow);
            BaritoneHook.stop();
            this.breakRequested = false;
            this.next(State.DONE, 0);
        }
    }

    private void waitBreakEnderChestMining(MinecraftClient mc, int ecNow, int obsidianNow) {
        this.stopMiningAfterPickupWindow(false);
        if (this.fireballNearby(mc)) {
            if (this.waitTicks > 40 || this.mineIdleTicks > 0) {
                FOElytraLog.detail("\u9644\u8fd1\u6709\u706b\u7403\uff0c\u6316\u672b\u5f71\u7bb1\u7684\u7b49\u5f85\u8ba1\u65f6\u5148\u6e05\u96f6\uff08%s\uff09", SupplyTask.posText(this.ecPos));
            }
            this.waitTicks = 0;
            this.mineIdleTicks = 0;
        }
        ++this.waitTicks;
        if (this.mineManual) {
            boolean breakerDone = BlockBreaker.tick(this.ecPos);
            if (!mc.world.getBlockState(this.ecPos).isAir()) {
                if (!breakerDone) {
                    return;
                }
                this.retryMineBack(mc, this.ecPos, false, this.hasPickaxe(mc) ? "\u5f92\u624b\u6316\u6ca1\u6316\u6389\uff08\u65b9\u5757\u6ca1\u6d88\u5931\uff09" : "\u6ca1\u6709\u9550\u5b50\uff0c\u672b\u5f71\u7bb1\u6316\u4e0d\u6389");
                return;
            }
            this.mineManual = false;
            return;
        }
        if (BaritoneHook.isMining()) {
            this.mineIdleTicks = 0;
            if (this.waitTicks % 100 == 0) {
                FOElytraLog.info("\u6316\u672b\u5f71\u7bb1\u4e2d\u2026\u5df2 %d \u79d2\uff08\u65b9\u5757\u8fd8\u5728 %s\uff0cBaritone \u6b63\u5728\u6316\uff09", this.waitTicks / 20, SupplyTask.posText(this.ecPos));
            }
        } else {
            ++this.mineIdleTicks;
            if (this.mineIdleTicks >= 60) {
                this.mineIdleTicks = 0;
                if (this.mineRetries >= 3) {
                    String why = this.hasPickaxe(mc) ? "\u6316\u77ff\u8fdb\u7a0b\u53cd\u590d\u505c\u6389/\u65b9\u5757\u6ca1\u6d88\u5931" : "\u6ca1\u6709\u9550\u5b50\uff0c\u672b\u5f71\u7bb1\u5f92\u624b\u6316\u4e0d\u6389\u843d";
                    FOElytraLog.warn("\u672b\u5f71\u7bb1\u6316\u4e0d\u52a8\uff08\u5df2\u91cd\u8bd5 %d \u6b21\uff09\uff1a%s", this.mineRetries, why);
                    this.giveUpEnderChest(why);
                    return;
                }
                if (!this.mineManual && this.opts.useBaritoneMine() && BaritoneHook.available() && this.hasPickaxe(mc)) {
                    ++this.mineRetries;
                    Block block = mc.world.getBlockState(this.ecPos).getBlock();
                    boolean issued = BaritoneHook.mine(this.ecBreakTarget, block);
                    FOElytraLog.warn("\u91cd\u65b0\u4e0b\u53d1\u6316\u672b\u5f71\u7bb1\uff1a\u7b2c %d/%d \u6b21\uff08\u65b9\u5757\u8fd8\u5728 %s\uff09\uff5c\u4e0b\u53d1%s", this.mineRetries, 3, SupplyTask.posText(this.ecPos), issued ? "\u6210\u529f" : "\u5931\u8d25");
                    if (!issued) {
                        this.retryMineBack(mc, this.ecPos, false, "\u6316\u77ff\u4e0b\u53d1\u5931\u8d25");
                        return;
                    }
                } else {
                    this.retryMineBack(mc, this.ecPos, false, this.hasPickaxe(mc) ? "\u6316\u77ff\u8fdb\u7a0b\u53cd\u590d\u505c\u6389" : "\u6ca1\u6709\u9550\u5b50");
                    return;
                }
            }
        }
        if (this.waitTicks > 400) {
            FOElytraLog.warn("\u7b49\u6316\u672b\u5f71\u7bb1\u7b49\u5230 %d \u79d2\u8fd8\u6ca1\u6316\u6389\uff08\u65b9\u5757\u8fd8\u5728 %s\uff0c\u91cd\u53d1\u6316\u77ff %d \u6b21\uff09", 20, SupplyTask.posText(this.ecPos), this.mineRetries);
            this.giveUpEnderChest("\u7b49 20 \u79d2\u6ca1\u6316\u6389");
        }
    }

    private void giveUpEnderChest(String why) {
        FOElytraLog.warn("\u672b\u5f71\u7bb1\u6ca1\u6316\u6389\uff0c\u8fd8\u7559\u5728 %s \u2014\u2014 \u5df2\u505c\u624b\uff08\u4f60\u53ef\u4ee5\u81ea\u5df1\u6316\uff0c\u6216\u4e0b\u6b21\u8865\u7ed9\u518d\u6765\uff09\uff5c\u539f\u56e0\uff1a%s", SupplyTask.posText(this.ecPos), why);
        BaritoneHook.stop();
        this.breakRequested = false;
        if (this.recoverNeeded()) {
            FOElytraLog.warn("\u4e16\u754c\u91cc\u8fd8\u6709\u6211\u65b9\u653e\u4e0b\u7684\u4e1c\u897f \u2192 \u8fd9\u6b21\u8865\u7ed9\u5230\u6b64\u4e3a\u6b62\uff0c\u5148\u628a\u7559\u4e0b\u7684\u5168\u6536\u56de\u6765", new Object[0]);
            this.beginRecover("\u672b\u5f71\u7bb1\u6ca1\u6316\u56de\u6765\uff1a" + why);
            return;
        }
        this.next(State.DONE, 0);
    }

    public boolean recoverNeeded() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) {
            return false;
        }
        return SupplyTask.blockIsShulker(mc, this.shulkerPos) || SupplyTask.blockIsEnderChest(mc, this.ecPos);
    }

    public boolean isRecovering() {
        return this.state == State.RECOVER && this.status == TaskStatus.RUNNING;
    }

    private static boolean blockIsShulker(MinecraftClient mc, BlockPos pos) {
        if (mc == null || mc.world == null || pos == null) {
            return false;
        }
        try {
            return SupplyTask.isShulkerBoxBlock(mc.world.getBlockState(pos).getBlock());
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static boolean blockIsEnderChest(MinecraftClient mc, BlockPos pos) {
        if (mc == null || mc.world == null || pos == null) {
            return false;
        }
        try {
            return mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST;
        }
        catch (Throwable t) {
            return false;
        }
    }

    public boolean beginRecover(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            return false;
        }
        if (!this.recoverNeeded()) {
            return false;
        }
        this.recoverReason = reason == null ? "" : reason;
        this.recoverTicks = 0;
        this.recoverStage = 0;
        this.recoverStageTicks = 0;
        this.recoverDropTicks = 0;
        this.recoverDropTarget = null;
        this.recoverShulkerDone = false;
        this.recoverEcDone = false;
        this.mineIdleTicks = 0;
        this.mineRetries = 0;
        this.manualBreakRetries = 0;
        this.mineManual = false;
        this.mineBackFailReason = "";
        this.rescueTicks = 0;
        this.rescueTarget = null;
        this.recoverShulkerTarget = ItemHelper.countShulkers((PlayerEntity)mc.player) + (SupplyTask.blockIsShulker(mc, this.shulkerPos) ? 1 : 0);
        this.recoverEcBefore = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.ENDER_CHEST);
        this.recoverObsidianBefore = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.OBSIDIAN);
        this.recoverEcTarget = this.recoverEcBefore + 1;
        this.releaseKeys();
        BlockBreaker.cancel();
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        this.state = State.RECOVER;
        this.status = TaskStatus.RUNNING;
        this.delay = 0;
        FOElytraLog.warn("\u5f00\u59cb\u5f52\u4f4d\uff08%s\uff09\uff1a\u628a\u653e\u4e0b\u7684%s\u6316\u56de\u6765\uff0c\u6700\u591a\u7b49 %d \u79d2", this.recoverReason, SupplyTask.blockIsShulker(mc, this.shulkerPos) && SupplyTask.blockIsEnderChest(mc, this.ecPos) ? "\u6f5c\u5f71\u76d2\u548c\u672b\u5f71\u7bb1" : (SupplyTask.blockIsShulker(mc, this.shulkerPos) ? "\u6f5c\u5f71\u76d2" : "\u672b\u5f71\u7bb1"), 20);
        return true;
    }

    private void recoverTick(MinecraftClient mc) {
        ++this.recoverTicks;
        ++this.recoverStageTicks;
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        if (this.recoverTicks > 400) {
            this.recoverFinish(mc);
            return;
        }
        if (this.recoverStage == 0) {
            this.recoverShulkerStage(mc);
            if (this.recoverShulkerDone) {
                this.recoverStage = 1;
                this.recoverStageTicks = 0;
            }
            return;
        }
        if (this.recoverStage == 1) {
            this.recoverEcStage(mc);
            if (this.recoverEcDone) {
                this.recoverStage = 2;
            }
            return;
        }
        this.recoverFinish(mc);
    }

    private void recoverShulkerStage(MinecraftClient mc) {
        if (this.shulkerPos == null) {
            this.recoverShulkerDone = true;
            return;
        }
        if (SupplyTask.blockIsShulker(mc, this.shulkerPos)) {
            if (this.recoverStageTicks == 1) {
                FOElytraLog.info("\u5f52\u4f4d\uff1a\u5f00\u59cb\u6316\u56de\u6f5c\u5f71\u76d2 %s", SupplyTask.posText(this.shulkerPos));
            }
            if (!this.recoverMine(mc, this.shulkerPos)) {
                this.recoverLeftover(true, this.shulkerPos, "\u6316\u4e0d\u52a8");
                this.recoverShulkerDone = true;
            }
            return;
        }
        if (ItemHelper.countShulkers((PlayerEntity)mc.player) >= this.recoverShulkerTarget) {
            this.recoverShulkerDone = true;
            FOElytraLog.info("\u5f52\u4f4d\uff1a\u6f5c\u5f71\u76d2\u5df2\u6536\u56de\uff08\u80cc\u5305 %d \u4e2a\uff09", ItemHelper.countShulkers((PlayerEntity)mc.player));
            return;
        }
        DropEvidence ev = this.dropsNear(mc, this.shulkerPos, false);
        this.recoverWalkToDrops(mc, this.shulkerPos, false);
        if (ev.count() > 0 || this.recoverStageTicks <= 200) {
            if (this.recoverStageTicks % 100 == 0) {
                FOElytraLog.detail("\u5f52\u4f4d\uff1a\u7b49\u6f5c\u5f71\u76d2\u8fdb\u80cc\u5305\uff08\u9644\u8fd1\u6389\u843d\u7269 %d \u4e2a\uff0c\u6700\u8fd1 %s\uff09", ev.count(), ev.nearestPos() == null ? "\u65e0" : String.format("%.1f \u683c", ev.nearest()));
            }
            return;
        }
        this.recoverLeftover(true, this.shulkerPos, "\u65b9\u5757\u5df2\u6316\u6389\u4f46\u9644\u8fd1\u6ca1\u6709\u6389\u843d\u7269");
        this.recoverShulkerDone = true;
    }

    private void recoverEcStage(MinecraftClient mc) {
        boolean gotIt;
        if (this.ecPos == null) {
            this.recoverEcDone = true;
            return;
        }
        if (SupplyTask.blockIsEnderChest(mc, this.ecPos)) {
            if (this.recoverStageTicks == 1) {
                FOElytraLog.info("\u5f52\u4f4d\uff1a\u5f00\u59cb\u6316\u56de\u672b\u5f71\u7bb1 %s", SupplyTask.posText(this.ecPos));
            }
            if (!this.recoverMine(mc, this.ecPos)) {
                this.recoverLeftover(false, this.ecPos, "\u6316\u4e0d\u52a8");
                this.recoverEcDone = true;
            }
            return;
        }
        boolean bl = gotIt = ItemHelper.countInInventory((PlayerEntity)mc.player, Items.ENDER_CHEST) >= this.recoverEcTarget || ItemHelper.countInInventory((PlayerEntity)mc.player, Items.OBSIDIAN) > this.recoverObsidianBefore;
        if (gotIt) {
            this.recoverEcDone = true;
            FOElytraLog.info("\u5f52\u4f4d\uff1a\u672b\u5f71\u7bb1\u5df2\u6536\u56de\uff08\u672b\u5f71\u7bb1 %d \u4e2a / \u9ed1\u66dc\u77f3 %d \u4e2a\uff09", ItemHelper.countInInventory((PlayerEntity)mc.player, Items.ENDER_CHEST), ItemHelper.countInInventory((PlayerEntity)mc.player, Items.OBSIDIAN));
            return;
        }
        DropEvidence ev = this.dropsNear(mc, this.ecPos, true);
        this.recoverWalkToDrops(mc, this.ecPos, true);
        if (ev.count() > 0 || this.recoverStageTicks <= 200) {
            if (this.recoverStageTicks % 100 == 0) {
                FOElytraLog.detail("\u5f52\u4f4d\uff1a\u7b49\u672b\u5f71\u7bb1/\u9ed1\u66dc\u77f3\u8fdb\u80cc\u5305\uff08\u9644\u8fd1\u6389\u843d\u7269 %d \u4e2a\uff0c\u6700\u8fd1 %s\uff09", ev.count(), ev.nearestPos() == null ? "\u65e0" : String.format("%.1f \u683c", ev.nearest()));
            }
            return;
        }
        this.recoverLeftover(false, this.ecPos, "\u65b9\u5757\u5df2\u6316\u6389\u4f46\u9644\u8fd1\u6ca1\u6709\u6389\u843d\u7269");
        this.recoverEcDone = true;
    }

    private boolean recoverMine(MinecraftClient mc, BlockPos pos) {
        this.noSneak("\u5f52\u4f4d\u6316\u6398\u524d");
        if (this.mineManual) {
            boolean breakerDone = BlockBreaker.tick(pos);
            if (mc.world.getBlockState(pos).isAir()) {
                this.mineManual = false;
                return true;
            }
            if (!breakerDone) {
                return true;
            }
            this.mineManual = false;
            if (this.mineRetries < 3) {
                ++this.mineRetries;
                BlockBreaker.reset();
                InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)pos));
                FOElytraLog.warn("\u5f52\u4f4d\uff1a\u5f92\u624b\u6316\u6ca1\u6316\u6389 %s \u2192 \u6362\u4e2a\u89d2\u5ea6\u518d\u6765\uff08\u7b2c %d/%d \u6b21\uff09", SupplyTask.posText(pos), this.mineRetries, 3);
                this.mineManual = true;
                return true;
            }
            this.mineBackFailReason = this.hasPickaxe(mc) ? "\u5f92\u624b\u6316\u591a\u6b21\u6ca1\u6316\u6389\uff08\u65b9\u5757\u6ca1\u6d88\u5931\uff09" : "\u6ca1\u6709\u9550\u5b50\uff0c\u5f92\u624b\u4e5f\u6316\u4e0d\u6389";
            FOElytraLog.warn("\u5f52\u4f4d\uff1a%s \u6536\u4e0d\u56de\u6765\uff08%s\uff09", SupplyTask.posText(pos), this.mineBackFailReason);
            return false;
        }
        if (BaritoneHook.isMining()) {
            this.mineIdleTicks = 0;
            if (this.recoverStageTicks % 100 == 0) {
                FOElytraLog.info("\u5f52\u4f4d\uff1a\u6b63\u5728\u6316 %s\uff08\u7b2c %d \u79d2\uff09", SupplyTask.posText(pos), this.recoverStageTicks / 20);
            }
            return true;
        }
        ++this.mineIdleTicks;
        if (this.mineIdleTicks < 60) {
            return true;
        }
        this.mineIdleTicks = 0;
        if (this.mineRetries >= 3) {
            this.mineBackFailReason = this.mineFailClass(mc, "\u65b9\u5757");
            FOElytraLog.warn("\u5f52\u4f4d\uff1a\u6316\u4e0d\u52a8 %s\uff08\u5df2\u91cd\u8bd5 %d \u6b21\uff09\uff1a%s", SupplyTask.posText(pos), this.mineRetries, this.mineBackFailReason);
            return false;
        }
        if (this.opts.useBaritoneMine() && BaritoneHook.available() && this.hasPickaxe(mc)) {
            ++this.mineRetries;
            Block block = mc.world.getBlockState(pos).getBlock();
            boolean issued = BaritoneHook.mine(1, block);
            FOElytraLog.warn("\u5f52\u4f4d\uff1a\u91cd\u65b0\u4e0b\u53d1\u6316 %s\uff08\u7b2c %d/%d \u6b21\uff09\uff5c\u4e0b\u53d1%s", SupplyTask.posText(pos), this.mineRetries, 3, issued ? "\u6210\u529f" : "\u5931\u8d25");
            if (issued) {
                return true;
            }
        }
        ++this.mineRetries;
        this.mineManual = true;
        BaritoneHook.stop();
        BlockBreaker.reset();
        InvHelper.lookAt((PlayerEntity)mc.player, Vec3d.ofCenter((Vec3i)pos));
        FOElytraLog.warn("\u5f52\u4f4d\uff1aBaritone \u6316\u4e0d\u52a8 %s\uff08%s\uff09\u2192 \u6539\u5f92\u624b\u6316\uff08\u7b2c %d/%d \u6b21\uff09", SupplyTask.posText(pos), this.hasPickaxe(mc) ? "\u6316\u77ff\u8fdb\u7a0b\u53cd\u590d\u505c\u6389" : "\u6ca1\u6709\u9550\u5b50", this.mineRetries, 3);
        return true;
    }

    private void recoverWalkToDrops(MinecraftClient mc, BlockPos origin, boolean ecLoot) {
        DropEvidence ev = this.dropsNear(mc, origin, ecLoot);
        BlockPos nearest = ev.nearestPos();
        if (nearest == null) {
            this.recoverDropTarget = null;
            return;
        }
        if (nearest.equals((Object)this.recoverDropTarget)) {
            return;
        }
        this.recoverDropTarget = nearest;
        if (!BaritoneHook.ready()) {
            return;
        }
        FOElytraLog.info("\u5f52\u4f4d\uff1a\u8d70\u8fc7\u53bb\u6361\u6389\u843d\u7269 %s\uff08\u6700\u8fd1 %.1f \u683c\uff0c\u5171 %d \u4e2a\uff09", SupplyTask.posText(nearest), ev.nearest(), ev.count());
        BaritoneHook.stop();
        BaritoneHook.command("goto " + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ());
    }

    private void recoverFinish(MinecraftClient mc) {
        boolean shulkerLeft = SupplyTask.blockIsShulker(mc, this.shulkerPos);
        boolean ecLeft = SupplyTask.blockIsEnderChest(mc, this.ecPos);
        if (this.recoverTicks > 400) {
            if (shulkerLeft) {
                this.recoverLeftover(true, this.shulkerPos, "\u5f52\u4f4d\u8d85\u65f6 20 \u79d2\u8fd8\u6ca1\u6316\u6389");
            }
            if (ecLeft) {
                this.recoverLeftover(false, this.ecPos, "\u5f52\u4f4d\u8d85\u65f6 20 \u79d2\u8fd8\u6ca1\u6316\u6389");
            }
            FOElytraLog.warn("\u5f52\u4f4d\u8d85\u65f6\u7ed3\u675f\uff08%d \u79d2\uff09\uff1a\u6f5c\u5f71\u76d2 %s\uff5c\u672b\u5f71\u7bb1 %s", 20, shulkerLeft ? "\u8fd8\u5728 " + SupplyTask.posText(this.shulkerPos) : "\u5df2\u5904\u7406", ecLeft ? "\u8fd8\u5728 " + SupplyTask.posText(this.ecPos) : "\u5df2\u5904\u7406");
        } else {
            Object[] objectArray = new Object[2];
            Object object = this.shulkerPos == null ? "\u672c\u6765\u5c31\u6ca1\u653e\u4e0b" : (objectArray[0] = shulkerLeft ? "\u8fd8\u5728 " + SupplyTask.posText(this.shulkerPos) : "\u5df2\u6536\u56de");
            objectArray[1] = this.ecPos == null ? "\u672c\u6765\u5c31\u6ca1\u653e\u4e0b" : (ecLeft ? "\u8fd8\u5728 " + SupplyTask.posText(this.ecPos) : "\u5df2\u6536\u56de");
            FOElytraLog.tip("\u5f52\u4f4d\u5b8c\u6210\uff1a\u6f5c\u5f71\u76d2 %s\uff5c\u672b\u5f71\u7bb1 %s", objectArray);
        }
        BaritoneHook.stop();
        BlockBreaker.cancel();
        this.releaseKeys();
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        PlayerAction.clearStuckSneak();
        this.state = State.DONE;
        this.status = TaskStatus.DONE;
        this.delay = 0;
    }

    private void recoverLeftover(boolean shulker, BlockPos pos, String why) {
        String what = shulker ? "\u6f5c\u5f71\u76d2" : "\u672b\u5f71\u7bb1";
        String at = SupplyTask.posText(pos);
        if (shulker) {
            ++this.runLeftShulkers;
            this.runLeftPos = this.runLeftPos.isEmpty() ? at : this.runLeftPos + "\u3001" + at;
            ++pendingLeftShulkers;
            String string = pendingLeftPos = pendingLeftPos.isEmpty() ? at : pendingLeftPos + "\u3001" + at;
            if (pos != null) {
                this.placeRejected.add(pos.toImmutable());
            }
        } else {
            ++this.runLeftEnderChests;
            this.runLeftEcPos = this.runLeftEcPos.isEmpty() ? at : this.runLeftEcPos + "\u3001" + at;
            ++pendingLeftEnderChests;
            pendingLeftEcPos = pendingLeftEcPos.isEmpty() ? at : pendingLeftEcPos + "\u3001" + at;
        }
        FOElytraLog.warn("%s\u6ca1\u80fd\u6536\u56de\uff1a%s\uff08%s\uff09\u2014\u2014 \u8bf7\u81ea\u5df1\u53bb\u62ff", what, at, why);
    }

    private void stopMiningAfterPickupWindow(boolean blockGone) {
        if (!blockGone) {
            this.pickupWait = 0;
            return;
        }
        ++this.pickupWait;
        if (this.pickupWait == 40 && BaritoneHook.isMining()) {
            BaritoneHook.stop();
            FOElytraLog.debug("\u65b9\u5757\u5df2\u6d88\u5931\uff0c\u505c\u6389\u6316\u6398\u8fdb\u7a0b\u7b49\u6389\u843d\u7269\u8fdb\u80cc\u5305", new Object[0]);
        }
    }

    private boolean hasSilkTouch(MinecraftClient mc) {
        ItemStack selected = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        if (this.isPickaxe(selected) && ItemHelper.hasEnchantment(selected, (RegistryKey<Enchantment>)Enchantments.SILK_TOUCH, 1)) {
            return true;
        }
        for (int i = 0; i < 9; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!this.isPickaxe(s) || !ItemHelper.hasEnchantment(s, (RegistryKey<Enchantment>)Enchantments.SILK_TOUCH, 1)) continue;
            return true;
        }
        return false;
    }

    private boolean hasPickaxe(MinecraftClient mc) {
        for (int i = 0; i < 9; ++i) {
            if (!this.isPickaxe(mc.player.getInventory().getStack(i))) continue;
            return true;
        }
        return false;
    }

    private boolean tickBreakBlock(MinecraftClient mc, BlockPos pos) {
        this.noSneak("\u6316\u65b9\u5757\u4e2d");
        if (mc.world.getBlockState(pos).isAir()) {
            this.breakTicks = 0;
            return true;
        }
        if (!pos.equals((Object)this.breakTarget)) {
            this.breakTarget = pos;
            this.breakTicks = 0;
            this.breakRequested = false;
        }
        if (this.breakTicks++ > 240) {
            FOElytraLog.warn("\u6316\u65b9\u5757\u8d85\u65f6\uff08%d, %d, %d\uff09", pos.getX(), pos.getY(), pos.getZ());
            BaritoneHook.stop();
            BlockBreaker.cancel();
            this.breakTicks = 0;
            return true;
        }
        if (this.opts.useBaritoneMine() && BaritoneHook.available()) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (!this.breakRequested) {
                if (!BaritoneHook.mine(1, block)) {
                    return BlockBreaker.tick(pos);
                }
                this.breakRequested = true;
                return false;
            }
            if (!BaritoneHook.isMining()) {
                BaritoneHook.stop();
                return mc.world.getBlockState(pos).isAir();
            }
            return false;
        }
        return BlockBreaker.tick(pos);
    }

    private void computeNeeds(MinecraftClient mc) {
        this.needs.fireworkStacks = Math.max(0, this.opts.targetFireworkStacks() - ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory((PlayerEntity)mc.player, Items.FIREWORK_ROCKET)));
        this.needs.xpBottles = Math.max(0, this.opts.targetXpBottles() - ItemHelper.countInInventory((PlayerEntity)mc.player, Items.EXPERIENCE_BOTTLE));
        this.needs.food = Math.max(0, this.opts.targetFoodCount() - this.countFood(mc));
        this.needs.totems = Math.max(0, this.opts.targetTotems() - ItemHelper.countInInventory((PlayerEntity)mc.player, Items.TOTEM_OF_UNDYING));
        this.needs.elytra = Math.max(0, this.opts.targetElytraCount() - this.countUsableElytra(mc));
    }

    private int countFood(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !this.matchesFood(s)) continue;
            n += s.getCount();
        }
        return n;
    }

    private int countUsableElytra(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 41; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isOf(Items.ELYTRA) || !ItemHelper.hasEnchantment(s, (RegistryKey<Enchantment>)Enchantments.UNBREAKING, 3) || s.getDamage() >= 15) continue;
            ++n;
        }
        return n;
    }

    private int findEnderChestHotbar() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 0; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.ENDER_CHEST)) continue;
            return i;
        }
        return -1;
    }

    private int findShulkerHotbarSlot() {
        int i;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (i = 6; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            return i;
        }
        for (i = 0; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private boolean ensureShulkerSelected(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        if (ItemHelper.isShulkerBox(inv.getStack(this.shulkerHotbarSlot))) {
            InvHelper.selectSlot(this.shulkerHotbarSlot);
            return true;
        }
        int found = this.findShulkerAnywhere(mc);
        if (found < 0) {
            this.shulkerSelectFailReason = "\u624b\u4e0a\u548c\u80cc\u5305\u91cc\u90fd\u6ca1\u6709\u6f5c\u5f71\u76d2\uff08\u80cc\u5305\u6f5c\u5f71\u76d2 " + ItemHelper.countShulkers((PlayerEntity)mc.player) + " \u4e2a\uff09";
            return false;
        }
        if (found < 9) {
            this.shulkerHotbarSlot = found;
            InvHelper.selectSlot(found);
            FOElytraLog.detail("\u91cd\u653e\u524d\u91cd\u65b0\u5b9a\u4f4d\u624b\u6301\u6f5c\u5f71\u76d2\uff1a\u5feb\u6377\u680f\u7b2c %d \u683c", found);
            return true;
        }
        int target = InvHelper.findEmptyHotbarSlot();
        boolean swapped = false;
        if (target < 0) {
            for (int h = 0; h < 9; ++h) {
                if (!this.isJunkNow(inv.getStack(h))) continue;
                target = h;
                swapped = true;
                break;
            }
        }
        if (target < 0) {
            FOElytraLog.warn("\u5feb\u6377\u680f 35 \u683c\u5168\u662f\u8981\u7559\u7684\u4e1c\u897f\uff0c\u6362\u4e0d\u51fa\u4f4d\u7f6e\u653e\u6f5c\u5f71\u76d2\uff08\u76d2\u5b50\u5728\u80cc\u5305\u7b2c %d \u683c\uff5c\u5feb\u6377\u680f\u7a7a\u4f4d %d\uff5c\u53ef\u66ff\u6362 %d\uff09", found, this.countEmptyHotbar(mc), this.countReplaceableHotbar(mc));
            this.shulkerSelectFailReason = "\u5feb\u6377\u680f 35 \u683c\u5168\u662f\u8981\u7559\u7684\u4e1c\u897f\uff0c\u6362\u4e0d\u51fa\u4f4d\u7f6e\u653e\u6f5c\u5f71\u76d2";
            return false;
        }
        InvHelper.moveInvToHotbar(found, target);
        if (!ItemHelper.isShulkerBox(inv.getStack(target))) {
            FOElytraLog.warn("\u6362\u4f4d\u6ca1\u751f\u6548\uff1a\u5feb\u6377\u680f %d \u4e0a\u73b0\u5728\u662f %s\uff08\u4e0d\u662f\u6f5c\u5f71\u76d2\uff09", target, this.describeStack(inv.getStack(target)));
            this.shulkerSelectFailReason = "\u6f5c\u5f71\u76d2\u6362\u5230\u5feb\u6377\u680f\u6ca1\u751f\u6548\uff08\u69fd " + target + " \u4e0a\u73b0\u5728\u662f " + this.describeStack(inv.getStack(target)) + "\uff09";
            return false;
        }
        this.shulkerHotbarSlot = target;
        InvHelper.selectSlot(target);
        FOElytraLog.detail("\u628a\u80cc\u5305\u7b2c %d \u683c\u7684\u6f5c\u5f71\u76d2\u6362\u5230\u5feb\u6377\u680f\u7b2c %d \u683c\uff08%s\uff09", found, target, swapped ? "\u6362\u6389\u4e00\u4e2a\u53ef\u66ff\u6362\u69fd\u4f4d" : "\u7528\u7a7a\u69fd");
        return true;
    }

    private String describeHeld(MinecraftClient mc) {
        int slot = mc.player.getInventory().getSelectedSlot();
        return this.describeStack(mc.player.getInventory().getSelectedStack()) + "\uff08\u69fd " + slot + "\uff09";
    }

    private String describeStack(ItemStack s) {
        if (s.isEmpty()) {
            return "\u7a7a";
        }
        return s.getName().getString() + " x" + s.getCount();
    }

    private String describeBlock(MinecraftClient mc, BlockPos pos) {
        if (pos == null) {
            return "(\u65e0\u5750\u6807)";
        }
        BlockState st = mc.world.getBlockState(pos);
        return st.isAir() ? "\u7a7a\u6c14" : st.getBlock().getName().getString();
    }

    private int makeHotbarRoom(ScreenHandler handler) {
        int empty;
        ItemStack s;
        int h;
        MinecraftClient mc = MinecraftClient.getInstance();
        int direct = this.findShulkerHotbarSlot();
        if (direct >= 0) {
            return direct;
        }
        for (h = 0; h < 9; ++h) {
            s = mc.player.getInventory().getStack(h);
            if (s.isEmpty() || this.isEssentialHotbar(s)) continue;
            empty = this.emptyBackpackIndex(mc);
            if (empty < 0) break;
            if (!this.movePlayerSlot(handler, h, empty, "\u817e\u4f4d\uff1a\u6742\u7269\u8fdb\u80cc\u5305")) continue;
            return h;
        }
        for (h = 0; h < 9; ++h) {
            s = mc.player.getInventory().getStack(h);
            if (s.isEmpty() || this.isEssentialHotbar(s)) continue;
            int junk = this.replaceableBackpackIndex(mc);
            if (junk < 0) break;
            if (!this.movePlayerSlot(handler, h, junk, "\u817e\u4f4d\uff1a\u6362\u8d70\u80cc\u5305\u6742\u7269")) continue;
            return h;
        }
        int fwSlots = 0;
        for (int i = 0; i < 9; ++i) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) continue;
            ++fwSlots;
        }
        if (fwSlots >= 2) {
            for (int h2 = 0; h2 < 9; ++h2) {
                int target;
                if (!mc.player.getInventory().getStack(h2).isOf(Items.FIREWORK_ROCKET)) continue;
                empty = this.emptyBackpackIndex(mc);
                int n = target = empty >= 0 ? empty : this.replaceableBackpackIndex(mc);
                if (target < 0) break;
                if (!this.movePlayerSlot(handler, h2, target, "\u817e\u4f4d\uff1a\u591a\u4f59\u7684\u70df\u82b1\u8fdb\u80cc\u5305")) continue;
                return h2;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; ++i) {
            ItemStack s2 = mc.player.getInventory().getStack(i);
            if (s2.isEmpty() || sb.length() >= 90) continue;
            sb.append(i).append('=').append(s2.getName().getString()).append(' ');
        }
        FOElytraLog.detail("\u817e\u4f4d\uff1a\u80cc\u5305\u91cc\u65e2\u6ca1\u6709\u7a7a\u4f4d\u4e5f\u6ca1\u6709\u80fd\u6362\u8d70\u7684\u6742\u7269\uff08%s\uff1b\u80cc\u5305\u7a7a\u4f4d %d \u4e2a\uff09\u2192 \u6539\u8bd5\u53ef\u66ff\u6362\u5feb\u6377\u680f\u4e92\u6362\uff08\u6309\u53ef\u66ff\u6362\u6e05\u5355\uff0c\u4e0d\u52a8\u8981\u7559\u7684\u4e1c\u897f\uff09", sb, this.countEmptyBackpack(mc));
        return -1;
    }

    private int swapOutReplaceableHotbar(ScreenHandler handler) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (handler == null || mc.player == null) {
            return -1;
        }
        if (this.shulkerRawSlot < 0 || this.shulkerRawSlot >= handler.slots.size()) {
            return -1;
        }
        ItemStack box = ((Slot)handler.slots.get(this.shulkerRawSlot)).getStack();
        if (!ItemHelper.isShulkerBox(box)) {
            FOElytraLog.warn("\u76d2\u5b50\u69fd %d \u4e0a\u73b0\u5728\u662f %s\uff08\u4e0d\u662f\u6f5c\u5f71\u76d2\uff09\uff0c\u6362\u4f4d\u53d6\u76d2\u505a\u4e0d\u4e86", this.shulkerRawSlot, this.describeStack(box));
            return -1;
        }
        for (int h = 0; h < 9; ++h) {
            ItemStack s = mc.player.getInventory().getStack(h);
            if (!this.isJunkNow(s)) continue;
            if (!ItemHelper.isShulkerBox(((Slot)handler.slots.get(this.shulkerRawSlot)).getStack())) {
                return -1;
            }
            int rawHotbar = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, h);
            if (rawHotbar < 0) continue;
            InvHelper.moveStack(handler, this.shulkerRawSlot, rawHotbar);
            ItemStack nowHotbar = mc.player.getInventory().getStack(h);
            if (ItemHelper.isShulkerBox(nowHotbar)) {
                FOElytraLog.tip("\u5feb\u6377\u680f\u585e\u6ee1\uff1a\u62ff\u5feb\u6377\u680f\u7b2c %d \u683c\u7684 %s \u548c\u672b\u5f71\u7bb1\u69fd %d \u4e92\u6362\u5185\u5bb9\uff0c\u8fd9\u683c\u817e\u51fa\u6765\u653e\u76d2\u5b50\uff08\u4e92\u6362\u4e0d\u4e22\u4e1c\u897f\uff09", h, this.describeStack(s), this.shulkerRawSlot);
                return h;
            }
            ItemStack nowBox = ((Slot)handler.slots.get(this.shulkerRawSlot)).getStack();
            FOElytraLog.detail("\u6362\u4f4d\u540e\u8fd8\u6ca1\u770b\u5230\u76d2\u5b50\uff1a\u5feb\u6377\u680f\u7b2c %d \u683c\uff1d%s\uff5c\u76d2\u69fd %d\uff1d%s", h, this.describeStack(nowHotbar), this.shulkerRawSlot, this.describeStack(nowBox));
            if (ItemHelper.isShulkerBox(nowBox)) continue;
            FOElytraLog.detail("\u76d2\u69fd %d \u91cc\u7684\u76d2\u5b50\u5df2\u7ecf\u4e0d\u5728\u539f\u5904\u4e86\uff0c\u8fd9\u6b21\u6362\u4f4d\u770b\u6765\u751f\u6548\u4e86\uff0c\u4ea4\u7ed9\u4e0b\u4e00 tick \u7684\u53d6\u76d2\u590d\u6838\u786e\u8ba4", this.shulkerRawSlot);
            return h;
        }
        FOElytraLog.warn("\u5feb\u6377\u680f 9 \u683c\u91cc\u6ca1\u6709\u53ef\u66ff\u6362\u7684\u683c\u5b50\uff08\u56fe\u817e\u3001\u70df\u82b1\u3001\u7ecf\u9a8c\u74f6\u3001\u9798\u7fc5\u3001\u98df\u7269\u3001\u672b\u5f71\u7bb1\u3001\u9550\u5251\u3001\u6f5c\u5f71\u76d2\u90fd\u4e0d\u6362\uff09\uff5c\u5feb\u6377\u680f\u7a7a\u4f4d %d\uff5c\u53ef\u66ff\u6362 %d", this.countEmptyHotbar(mc), this.countReplaceableHotbar(mc));
        return -1;
    }

    private boolean isEssentialHotbar(ItemStack s) {
        if (s.isEmpty()) {
            return true;
        }
        return this.isPickaxe(s) || this.isSword(s) || s.isOf(Items.ENDER_CHEST) || s.isOf(Items.TOTEM_OF_UNDYING) || this.matchesFood(s) || ItemHelper.isShulkerBox(s) || s.isOf(Items.FIREWORK_ROCKET);
    }

    private int emptyBackpackIndex(MinecraftClient mc) {
        for (int i = 9; i < 36; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private int countEmptyBackpack(MinecraftClient mc) {
        int n = 0;
        for (int i = 9; i < 36; ++i) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            ++n;
        }
        return n;
    }

    private int replaceableBackpackIndex(MinecraftClient mc) {
        for (int i = 9; i < 36; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
            if (this.isEssentialHotbar(s) || s.isOf(Items.EXPERIENCE_BOTTLE) && s.getCount() >= s.getMaxCount() || s.isOf(Items.ELYTRA)) continue;
            return i;
        }
        return -1;
    }

    private boolean movePlayerSlot(ScreenHandler handler, int a, int b, String why) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }
        int rawA = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, a);
        int rawB = InvHelper.playerSlotId(handler, (PlayerEntity)mc.player, b);
        if (rawA < 0 || rawB < 0) {
            return false;
        }
        InvHelper.moveStack(handler, rawA, rawB);
        FOElytraLog.tip("%s\uff08\u5feb\u6377\u680f %d \u2194 \u80cc\u5305 %d\uff1b\u6f5c\u5f71\u76d2\u9a6c\u4e0a\u653e\u8fdb\u6765\uff09", why, a, b);
        FOElytraLog.detail("%s\uff1a#%d \u2192 #%d\uff08raw %d \u2192 %d\uff09", why, a, b, rawA, rawB);
        return true;
    }

    private void next(State next, int wait) {
        if (next != this.state) {
            FOElytraLog.detail("\u8865\u7ed9\u72b6\u6001 %s \u2192 %s\uff08\u7b49 %d tick\uff09\uff5c\u9700\u6c42 %s", new Object[]{this.state, next, Math.max(0, wait), this.needs});
        }
        this.noSneak("\u5207\u72b6\u6001 \u2192 " + String.valueOf((Object)next));
        this.state = next;
        this.delay = Math.max(0, wait);
        if (next == State.DONE) {
            this.status = TaskStatus.DONE;
            this.clearStuckSneak("\u8865\u7ed9\u6536\u5c3e");
            if (this.runLostShulkers > 0) {
                FOElytraLog.warn("\u672c\u6b21\u8865\u7ed9\u4e22\u5931\u6f5c\u5f71\u76d2 %d \u4e2a\uff08\u4f4d\u7f6e %s\uff09", this.runLostShulkers, this.runLostPos);
            }
            if (this.runLeftShulkers > 0) {
                FOElytraLog.warn("\u672c\u6b21\u8865\u7ed9\u6709 %d \u4e2a\u6f5c\u5f71\u76d2\u7559\u5728\u539f\u5730\uff08\u4f4d\u7f6e %s\uff09", this.runLeftShulkers, this.runLeftPos);
            }
            if (this.runLeftEnderChests > 0) {
                FOElytraLog.warn("\u672c\u6b21\u8865\u7ed9\u6709 %d \u4e2a\u672b\u5f71\u7bb1\u7559\u5728\u539f\u5730\uff08\u4f4d\u7f6e %s\uff09", this.runLeftEnderChests, this.runLeftEcPos);
            }
        }
        if (next == State.FAILED) {
            this.status = TaskStatus.FAILED;
        }
    }

    private void clearStuckSneak(String where) {
        boolean cleared = PlayerAction.clearStuckSneak();
        FOElytraLog.detail("\u6e05\u6389\u6b8b\u7559\u6f5c\u884c\uff1a%s\uff08%s\uff09", cleared ? "\u662f" : "\u5426", where);
    }

    private void noSneak(String where) {
        if (PlayerAction.forceNoSneak()) {
            FOElytraLog.detail("\u5f3a\u5236\u677e\u5f00\u6f5c\u884c\uff08\u8865\u7ed9\u91cc\u4e0d\u8bb8\u6f5c\u884c\uff09\uff1a%s", where);
        }
    }

    private static boolean isContainerState(State s) {
        return switch (s.ordinal()) {
            case 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 25 -> true;
            default -> false;
        };
    }

    private void fail(String reason) {
        this.failReason = reason;
        this.lastMessage = reason;
        FOElytraLog.err("\u8865\u7ed9\u5931\u8d25\uff1a%s", reason);
        this.releaseKeys();
        BlockBreaker.cancel();
        if (BaritoneHook.isMining()) {
            BaritoneHook.stop();
        }
        if (InvHelper.hasContainerOpen()) {
            InvHelper.closeScreen();
        }
        FOElytraLog.detail("\u8865\u7ed9\u5931\u8d25\u6536\u5c3e\uff1aBaritone \u5df2\u505c=%s\uff5c\u754c\u9762\u5df2\u5173=%s\uff5c\u72b6\u6001 %s\uff5c\u9700\u6c42 %s", new Object[]{!BaritoneHook.isMining(), !InvHelper.hasContainerOpen(), this.state, this.needs});
        this.clearStuckSneak("\u8865\u7ed9\u5931\u8d25");
        this.state = State.FAILED;
        this.status = TaskStatus.FAILED;
    }

    private void releaseKeys() {
        if (this.walkPressed) {
            PlayerAction.pressForward(false);
            this.walkPressed = false;
        }
        PlayerAction.forceNoSneak();
    }

    private String describeTargets() {
        return "\u70df\u82b1 " + this.opts.targetFireworkStacks() + " \u7ec4 / \u7ecf\u9a8c\u74f6 " + this.opts.targetXpBottles() + " / \u98df\u7269 " + this.opts.targetFoodCount() + " / \u56fe\u817e " + this.opts.targetTotems() + " / \u5907\u7528\u9798\u7fc5 " + this.opts.targetElytraCount();
    }

    public static int containerSlots(ScreenHandler handler) {
        if (handler == null) {
            return 27;
        }
        int c = handler.slots.size() - 36;
        return c <= 0 ? 27 : c;
    }

    public static boolean isShulkerBoxBlock(Block block) {
        return block instanceof ShulkerBoxBlock || block == Blocks.SHULKER_BOX;
    }

    public Needs needs() {
        return this.needs;
    }

    static {
        pendingLostPos = "";
        pendingLeftPos = "";
        pendingLeftEcPos = "";
    }

    public static enum State {
        IDLE("\u7a7a\u95f2"),
        WALK_CENTER("\u8d70\u4f4d\u5230\u4e2d\u5fc3"),
        SORT_INV("\u6574\u7406\u80cc\u5305"),
        EVALUATE("\u8bc4\u4f30"),
        PUT_OUT_FIRE("\u706d\u706b"),
        PLACE_EC("\u653e\u672b\u5f71\u7bb1"),
        OPEN_EC("\u5f00\u672b\u5f71\u7bb1"),
        WAIT_EC("\u7b49\u5f85\u672b\u5f71\u7bb1"),
        SCAN("\u626b\u63cf"),
        TAKE_SHULKER("\u53d6\u6f5c\u5f71\u76d2"),
        VERIFY_TAKE("\u9a8c\u8bc1\u53d6\u76d2"),
        PLACE_SH("\u653e\u6f5c\u5f71\u76d2"),
        OPEN_SH("\u5f00\u6f5c\u5f71\u76d2"),
        WAIT_SH("\u7b49\u5f85\u6f5c\u5f71\u76d2"),
        MOVE_ITEMS("\u8f6c\u79fb\u7269\u54c1"),
        CLOSE_SH("\u5173\u6f5c\u5f71\u76d2"),
        BREAK_SH("\u6316\u6f5c\u5f71\u76d2"),
        WAIT_BREAK_SH("\u7b49\u5f85\u6316\u76d2"),
        REOPEN_EC("\u91cd\u5f00\u672b\u5f71\u7bb1"),
        WAIT_EC_RETURN("\u7b49\u5f85\u56de\u672b\u5f71\u7bb1"),
        RETURN_SH("\u5f52\u8fd8\u6f5c\u5f71\u76d2"),
        CLOSE_EC("\u5173\u672b\u5f71\u7bb1"),
        NEXT_SH("\u4e0b\u4e00\u4e2a\u6f5c\u5f71\u76d2"),
        BREAK_EC("\u6316\u672b\u5f71\u7bb1"),
        WAIT_BREAK_EC("\u7b49\u5f85\u6316\u672b\u5f71\u7bb1"),
        VERIFY_RETURN("\u9a8c\u8bc1\u5f52\u8fd8"),
        RECOVER("\u6062\u590d"),
        DONE("\u5b8c\u6210"),
        FAILED("\u5931\u8d25");


        private final String label;

        State(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private record PlanItem(int rawSlot, int fireworkStacks, int second, boolean xpMode, String title) {
    }

    private record TakeVerify(Item item, int srcSlot, int beforeInv, int beforeBox, int kind, int takenCount) {
    }

    private record DropEvidence(int count, double nearest, BlockPos nearestPos) {
    }
}

