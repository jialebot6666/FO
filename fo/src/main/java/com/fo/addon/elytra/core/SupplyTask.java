package com.fo.addon.elytra.core;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.enchantment.Enchantments;
public final class SupplyTask {
    private static final int WALK_MAX_TICKS = 40;
    private static final int SCREEN_WAIT_TICKS = 40;
    private static final int SCREEN_HOLD_MAX_TICKS = 600;
    private static final int VERIFY_WAIT_TICKS = 10;
    private static final int MOVE_GUARD = 240;
    private static final int OPEN_RETRY = 3;
    private static final int PLACE_RETRY = 3;
    private static final int PLACE_CANDIDATE_ROUNDS = 2;
    private static final int SHULKER_MAX_PICKS_PER_BOX = 2;
    private static final int PLACE_RETRY_WAIT_TICKS = 10;
    private static final int SHULKER_BLOCK_WAIT_TICKS = 60;
    private static final int PLACE_POLL_LOG_INTERVAL = 20;
    private static final int SWAP_RECHECK_MAX = 6;
    public enum State {
        IDLE("空闲"),
        WALK_CENTER("走到中心"),
        SORT_INV("整理背包"),
        EVALUATE("清点需求"),
        PUT_OUT_FIRE("灭火"),
        PLACE_EC("放末影箱"),
        OPEN_EC("开末影箱"),
        WAIT_EC("等末影箱界面"),
        SCAN("扫描存储"),
        TAKE_SHULKER("拿潜影盒"),
        VERIFY_TAKE("确认拿到"),
        PLACE_SH("放潜影盒"),
        OPEN_SH("开潜影盒"),
        WAIT_SH("等潜影盒界面"),
        MOVE_ITEMS("搬运物资"),
        CLOSE_SH("关潜影盒"),
        BREAK_SH("挖潜影盒"),
        WAIT_BREAK_SH("等挖完潜影盒"),
        REOPEN_EC("重开末影箱"),
        WAIT_EC_RETURN("等末影箱界面（放回）"),
        RETURN_SH("放回潜影盒"),
        CLOSE_EC("关末影箱"),
        NEXT_SH("下一个潜影盒"),
        BREAK_EC("收末影箱"),
        WAIT_BREAK_EC("等收完末影箱"),
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
    private record PlanItem(int rawSlot, int fireworkStacks, int second, boolean xpMode, String title) {}
    private final List<PlanItem> plan = new ArrayList<>();
    private final Set<Integer> storeTried = new LinkedHashSet<>();
    private int planIndex;
    private final Set<Item> exhausted = new LinkedHashSet<>();
    private boolean foodExhausted;
    private int boxFwTaken;
    private int boxSecondTaken;
    private boolean boxTookAnything;
    private boolean boxTookPartial;
    private int emptyBoxStrikes;
    private final Map<Integer, Integer> boxPickCount = new LinkedHashMap<>();
    private final Set<Integer> boxBlacklist = new LinkedHashSet<>();
    private final Deque<Integer> replaceSlots = new ArrayDeque<>();
    private boolean starvedWarned;
    private String starvedDetailKey = "";
    private String replaceSlotSummary = "";
    private int replaceRecheckSkips;
    private int shulkerBreakTarget;
    private int ecBreakTarget;
    private List<BlockPos> fireTargets = new ArrayList<>();
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
    private final List<BlockPos> placeCandidates = new ArrayList<>();
    private final Set<BlockPos> placeRejected = new LinkedHashSet<>();
    private int placeCandidateIndex;
    private int placeCandidateTotal;
    private int placeRounds;
    private String placeEmptyReason = "";
    private int moveGuard;
    private boolean merged;
    private boolean breakRequested;
    private boolean walkPressed;
    private boolean pendingReturn;
    private int breakTicks;
    private BlockPos breakTarget;
    public SupplyTask(SupplyOptions opts) {
        this.opts = opts;
    }
    public void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            fail("玩家/世界为空，无法补给");
            return;
        }
        state = State.WALK_CENTER;
        status = TaskStatus.RUNNING;
        delay = 0;
        failReason = "";
        plan.clear();
        planIndex = 0;
        storeTried.clear();
        exhausted.clear();
        foodExhausted = false;
        fireTargets = new ArrayList<>();
        sortMoves = 0;
        emptyBoxStrikes = 0;
        boxPickCount.clear();
        boxBlacklist.clear();
        waitTicks = 0;
        walkTicks = 0;
        screenHoldTicks = 0;
        openRetries = 0;
        placeRetries = 0;
        placeWaitTicks = 0;
        placeCandidates.clear();
        placeRejected.clear();
        placeCandidateIndex = 0;
        placeCandidateTotal = 0;
        placeRounds = 0;
        placeEmptyReason = "";
        moveGuard = 0;
        merged = false;
        breakRequested = false;
        walkPressed = false;
        ecPos = null;
        shulkerPos = null;
        shulkerRawSlot = -1;
        shulkerHotbarSlot = -1;
        shulkerCountBeforeTake = 0;
        BlockBreaker.reset();
        FOElytraLog.info("开始自动补给：目标 %s", describeTargets());
    }
    public void abort(String reason) {
        releaseKeys();
        BlockBreaker.cancel();
        if (BaritoneHook.isMining()) BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        if (state != State.IDLE) FOElytraLog.warn("补给中止：%s", reason);
        state = State.IDLE;
        status = TaskStatus.IDLE;
        delay = 0;
    }
    public TaskStatus status() {
        return status;
    }
    public State state() {
        return state;
    }
    public boolean isRunning() {
        return status == TaskStatus.RUNNING;
    }
    public String failReason() {
        return failReason;
    }
    public String progress() {
        if (status == TaskStatus.RUNNING) {
            return state.toString() + (needs.isEmpty() ? "" : (" 还需 " + needs));
        }
        return status.toString() + (failReason.isEmpty() ? "" : ("(" + failReason + ")"));
    }
    public String lastMessage() {
        return lastMessage;
    }
    public Set<Item> exhaustedItems() {
        return java.util.Collections.unmodifiableSet(exhausted);
    }
    public boolean foodExhausted() {
        return foodExhausted;
    }
    private void markExhausted() {
        if (needs.fireworkStacks > 0) exhausted.add(Items.FIREWORK_ROCKET);
        if (needs.xpBottles > 0) exhausted.add(Items.EXPERIENCE_BOTTLE);
        if (needs.totems > 0) exhausted.add(Items.TOTEM_OF_UNDYING);
        if (needs.elytra > 0) exhausted.add(Items.ELYTRA);
        if (needs.food > 0) foodExhausted = true;
        if (!exhausted.isEmpty() || foodExhausted) {
            FOElytraLog.detail("本轮判定这些暂时取不到：%s%s（可能是箱子里真没有，也可能是腾不出格子/界面没开成拿不出来）"
                    + " —— 已登记 2 分钟冷却，先用现有的继续飞",
                exhausted, foodExhausted ? " + 食物" : "");
        }
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
            FOElytraLog.err("补给内部异常: %s", String.valueOf(t));
            fail("内部异常 " + t.getClass().getSimpleName());
        }
    }
    private void step(MinecraftClient mc) {
        switch (state) {
            case WALK_CENTER -> walkCenter(mc);
            case SORT_INV -> sortInventory(mc);
            case EVALUATE -> evaluate(mc);
            case PUT_OUT_FIRE -> putOutFire(mc);
            case PLACE_EC -> placeEnderChest(mc);
            case OPEN_EC -> openEnderChest(mc);
            case WAIT_EC -> waitEnderChest(mc, State.SCAN);
            case SCAN -> scan(mc);
            case TAKE_SHULKER -> takeShulker(mc);
            case VERIFY_TAKE -> verifyTake(mc);
            case PLACE_SH -> placeShulker(mc);
            case OPEN_SH -> openShulker(mc);
            case WAIT_SH -> waitShulker(mc);
            case MOVE_ITEMS -> moveItems(mc);
            case CLOSE_SH -> {
                InvHelper.closeScreen();
                breakRequested = false;
                next(State.BREAK_SH, opts.actionDelay());
            }
            case BREAK_SH -> breakShulker(mc);
            case WAIT_BREAK_SH -> waitBreakShulker(mc);
            case REOPEN_EC -> openEnderChest(mc);
            case WAIT_EC_RETURN -> waitEnderChest(mc, State.RETURN_SH);
            case RETURN_SH -> returnShulker(mc);
            case CLOSE_EC -> {
                InvHelper.closeScreen();
                next(State.NEXT_SH, opts.actionDelay());
            }
            case NEXT_SH -> nextShulker(mc);
            case BREAK_EC -> breakEnderChest(mc);
            case WAIT_BREAK_EC -> waitBreakEnderChest(mc);
            case DONE -> status = TaskStatus.DONE;
            case FAILED -> status = TaskStatus.FAILED;
            default -> status = TaskStatus.DONE;
        }
    }
    private void walkCenter(MinecraftClient mc) {
        if (InvHelper.screenOpen()) {
            releaseKeys();
            if (++screenHoldTicks > SCREEN_HOLD_MAX_TICKS) {
                fail("你一直开着界面（" + (SCREEN_HOLD_MAX_TICKS / 20) + " 秒），补给已取消");
                return;
            }
            if (screenHoldTicks % 100 == 0) {
                FOElytraLog.warn("检测到你开着界面，补给暂停中（关掉后会自动继续）");
            }
            delay = 0;
            return;
        }
        screenHoldTicks = 0;
        if (walkTicks++ > WALK_MAX_TICKS) {
            releaseKeys();
            next(State.SORT_INV, 2);
            return;
        }
        BlockPos foot = mc.player.getBlockPos();
        Vec3d center = new Vec3d(foot.getX() + 0.5, mc.player.getY(), foot.getZ() + 0.5);
        Vec3d delta = center.subtract(mc.player.getEntityPos());
        if (Math.abs(delta.x) < 0.2 && Math.abs(delta.z) < 0.2) {
            releaseKeys();
            next(State.SORT_INV, 2);
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        mc.player.setYaw((float) yaw);
        PlayerAction.pressForward(true);
        walkPressed = true;
        delay = 0;
    }
    private void sortInventory(MinecraftClient mc) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || handler.slots.size() < 45) {
            next(State.EVALUATE, opts.actionDelay());
            return;
        }
        int moves = 0;
        for (int b = 9; b < 36 && moves < 2 && sortMoves < 24; b++) {
            ItemStack back = mc.player.getInventory().getStack(b);
            if (back.isEmpty() || !isKeyItem(back)) continue;
            int target = designatedHotbarSlot(mc, back);
            if (target < 0) continue;
            if (moveRawToHotbar(handler, b, target)) {
                moves++;
                sortMoves++;
            }
        }
        if (moves > 0) {
            delay = opts.actionDelay();
            return;
        }
        if (opts.debug()) FOElytraLog.debug("物品栏整理完成（快捷栏：0 镐 / 1 剑 / 2 末影箱 / 3-4 图腾 / 5 食物）");
        next(State.EVALUATE, opts.actionDelay());
    }
    private boolean isKeyItem(ItemStack s) {
        if (s.isEmpty()) return false;
        return isPickaxe(s) || isSword(s) || s.isOf(Items.ENDER_CHEST)
            || s.isOf(Items.TOTEM_OF_UNDYING) || matchesFood(s);
    }
    private int designatedHotbarSlot(MinecraftClient mc, ItemStack s) {
        if (isPickaxe(s)) return isPickaxe(mc.player.getInventory().getStack(0)) ? -1 : 0;
        if (isSword(s)) return isSword(mc.player.getInventory().getStack(1)) ? -1 : 1;
        if (s.isOf(Items.ENDER_CHEST)) return mc.player.getInventory().getStack(2).isOf(Items.ENDER_CHEST) ? -1 : 2;
        if (s.isOf(Items.TOTEM_OF_UNDYING)) {
            if (!mc.player.getInventory().getStack(3).isOf(Items.TOTEM_OF_UNDYING)) return 3;
            if (!mc.player.getInventory().getStack(4).isOf(Items.TOTEM_OF_UNDYING)) return 4;
            return -1;
        }
        if (matchesFood(s)) {
            ItemStack slot5 = mc.player.getInventory().getStack(5);
            return (slot5.isEmpty() || !slot5.isOf(s.getItem())) ? 5 : -1;
        }
        return -1;
    }
    private boolean hotbarHasKeyItem(MinecraftClient mc, int slot) {
        return isKeyItem(mc.player.getInventory().getStack(slot));
    }
    private boolean swapRaw(ScreenHandler handler, int rawA, int rawB) {
        InvHelper.click(handler, rawA, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, rawB, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, rawA, 0, SlotActionType.PICKUP);
        return true;
    }
    private boolean moveRawToHotbar(ScreenHandler handler, int invIndex, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return false;
        InvHelper.click(handler, invIndex, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, 36 + hotbarSlot, 0, SlotActionType.PICKUP);
        InvHelper.click(handler, invIndex, 0, SlotActionType.PICKUP);
        return true;
    }
    private boolean isPickaxe(ItemStack s) {
        return s.isOf(Items.NETHERITE_PICKAXE) || s.isOf(Items.DIAMOND_PICKAXE)
            || s.isOf(Items.IRON_PICKAXE) || s.isOf(Items.STONE_PICKAXE)
            || s.isOf(Items.WOODEN_PICKAXE) || s.isOf(Items.GOLDEN_PICKAXE);
    }
    private boolean isSword(ItemStack s) {
        return s.isOf(Items.NETHERITE_SWORD) || s.isOf(Items.DIAMOND_SWORD)
            || s.isOf(Items.IRON_SWORD) || s.isOf(Items.STONE_SWORD)
            || s.isOf(Items.WOODEN_SWORD) || s.isOf(Items.GOLDEN_SWORD)
            || s.isOf(Items.TRIDENT);
    }
    private boolean isGoodElytra(ItemStack s) {
        return s.getDamage() < 15 && ItemHelper.hasEnchantment(s, Enchantments.UNBREAKING, 3);
    }
    private boolean isJunkNow(ItemStack s) {
        if (s.isEmpty()) return true;
        if (s.isOf(Items.FIREWORK_ROCKET)) return false;
        if (s.isOf(Items.EXPERIENCE_BOTTLE)) return false;
        if (s.isOf(Items.TOTEM_OF_UNDYING)) return false;
        if (s.isOf(Items.ENDER_CHEST)) return false;
        if (matchesFood(s)) return false;
        if (isPickaxe(s) || isSword(s)) return false;
        if (ItemHelper.isShulkerBox(s)) return false;
        if (s.isOf(Items.ELYTRA)) return !isGoodElytra(s);
        return true;
    }
    private void evaluate(MinecraftClient mc) {
        computeNeeds(mc);
        int hotbarSlot = findEnderChestHotbar();
        if (hotbarSlot < 0) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_CHEST)) {
                    int empty = InvHelper.findEmptyHotbarSlot();
                    if (empty < 0) empty = 8;
                    InvHelper.moveInvToHotbar(i, empty);
                    hotbarSlot = empty;
                    break;
                }
            }
        }
        if (hotbarSlot < 0) {
            fail("背包里没有末影箱，无法补给");
            return;
        }
        ecHotbarSlot = hotbarSlot;
        int total = ItemHelper.countInInventory(mc.player, Items.ENDER_CHEST);
        if (total < opts.minEnderChests()) {
            FOElytraLog.warn("末影箱只剩 %d 个（建议至少 %d 个）", total, opts.minEnderChests());
        }
        if (needs.isEmpty()) {
            lastMessage = "无需补给";
            FOElytraLog.info("当前物资已达标，跳过补给");
            next(State.DONE, 0);
            return;
        }
        if (!opts.autoPlaceEnderChest()) {
            fail("未开启「自动放置末影箱」，且附近没有可用的末影箱");
            return;
        }
        fireTargets = scanNearbyFire(mc, 3);
        if (!fireTargets.isEmpty()) {
            FOElytraLog.tip("先把身边的火打掉（%d 处）再补给", fireTargets.size());
            next(State.PUT_OUT_FIRE, 0);
            return;
        }
        next(State.PLACE_EC, opts.actionDelay());
    }
    private List<BlockPos> scanNearbyFire(MinecraftClient mc, int radius) {
        List<BlockPos> fire = new ArrayList<>();
        BlockPos origin = mc.player.getBlockPos();
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                for (int k = -radius; k <= radius; k++) {
                    BlockPos target = origin.add(i, j, k);
                    if (mc.world.getBlockState(target).getBlock() == Blocks.FIRE) fire.add(target);
                }
            }
        }
        return fire;
    }
    private void putOutFire(MinecraftClient mc) {
        fireTargets.removeIf(p -> mc.world.getBlockState(p).getBlock() != Blocks.FIRE);
        if (fireTargets.isEmpty()) {
            next(State.PLACE_EC, opts.actionDelay());
            return;
        }
        BlockPos pos = fireTargets.remove(0);
        InvHelper.lookAt(mc.player, Vec3d.ofCenter(pos));
        if (mc.interactionManager != null) {
            mc.interactionManager.attackBlock(pos, Direction.UP);
        }
        delay = 1;
    }
    private void beginPlaceScan() {
        placeCandidates.clear();
        placeCandidateIndex = 0;
        placeCandidateTotal = 0;
        placeRounds = 0;
        placeEmptyReason = "";
    }
    private BlockPos nextPlaceCandidate(MinecraftClient mc, String what) {
        while (true) {
            if (!placeCandidates.isEmpty()) {
                BlockPos p = placeCandidates.remove(0);
                if (placeRejected.contains(p)) continue;
                placeCandidateIndex++;
                return p;
            }
            if (placeRounds >= PLACE_CANDIDATE_ROUNDS) return null;
            placeRounds++;
            List<BlockPos> all = InvHelper.findPlaceTargets(mc.player, opts.placeRadius(), true);
            List<BlockPos> free = InvHelper.findPlaceTargets(mc.player, opts.placeRadius(), false);
            placeCandidates.addAll(free);
            placeCandidates.removeIf(placeRejected::contains);
            placeCandidateIndex = 0;
            placeCandidateTotal = placeCandidates.size();
            if (placeCandidates.isEmpty()) {
                int blocked = all.size() - free.size();
                StringBuilder blockedList = new StringBuilder();
                for (BlockPos p : all) {
                    if (free.contains(p)) continue;
                    if (blockedList.length() > 0) blockedList.append('、');
                    blockedList.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());
                    if (blockedList.length() > 120) {
                        blockedList.append("…");
                        break;
                    }
                }
                if (free.isEmpty()) {
                    placeEmptyReason = all.isEmpty()
                        ? "附近没有能放" + what + "的位置（半径 " + opts.placeRadius() + " 都是挡住/悬空的）"
                        : "周围没有能放" + what + "的位置（你站的位置挡住了）";
                } else {
                    placeEmptyReason = "能放" + what + "的位置都试过了（可用 " + free.size() + " 个，已拉黑 " + placeRejected.size() + " 个）";
                }
                FOElytraLog.detail("摆放候选用尽：可用 %d 个｜被自己碰撞箱挡住 %d 个（%s）｜已拉黑 %d 个｜半径 %d",
                    free.size(), blocked, blockedList.length() == 0 ? "无" : blockedList, placeRejected.size(), opts.placeRadius());
                return null;
            }
        }
    }
    private void rejectPlaceCandidate(BlockPos pos, String what) {
        if (pos != null) placeRejected.add(pos.toImmutable());
        BlockPos next = null;
        for (BlockPos p : placeCandidates) {
            if (!placeRejected.contains(p)) { next = p; break; }
        }
        FOElytraLog.detail("换位置放%s：%d,%d,%d 被拒 → 改试 %s（候选 %d/%d）",
            what, pos == null ? 0 : pos.getX(), pos == null ? 0 : pos.getY(), pos == null ? 0 : pos.getZ(),
            next == null ? "重新扫一遍候选" : next.getX() + "," + next.getY() + "," + next.getZ(),
            placeCandidateIndex, placeCandidateTotal);
    }
    private void placeEnderChest(MinecraftClient mc) {
        beginPlaceScan();
        BlockPos pos = null;
        while (true) {
            BlockPos cand = nextPlaceCandidate(mc, "末影箱");
            if (cand == null) break;
            if (InvHelper.placeBlock(cand, ecHotbarSlot)) {
                pos = cand;
                break;
            }
            rejectPlaceCandidate(cand, "末影箱");
        }
        if (pos == null) {
            fail(placeEmptyReason.isEmpty() ? "附近没有合适的位置放置末影箱（半径 " + opts.placeRadius() + "）" : placeEmptyReason);
            return;
        }
        ItemStack stack = mc.player.getInventory().getStack(ecHotbarSlot);
        ecTitle = stack.isEmpty() ? "末影箱" : stack.getName().getString();
        ecPos = pos;
        ecBlockCountAfterPlace = ItemHelper.countInInventory(mc.player, Items.ENDER_CHEST);
        FOElytraLog.tip("已放置末影箱于 %d,%d,%d", pos.getX(), pos.getY(), pos.getZ());
        next(State.OPEN_EC, opts.actionDelay() * 2);
    }
    private void openEnderChest(MinecraftClient mc) {
        if (ecPos == null) {
            fail("末影箱坐标丢失");
            return;
        }
        if (mc.world.getBlockState(ecPos).isAir()) {
            fail("末影箱不见了（被破坏或被推走）");
            return;
        }
        InvHelper.interactBlock(ecPos);
        waitTicks = 0;
        next(pendingReturn ? State.WAIT_EC_RETURN : State.WAIT_EC, 1);
    }
    private void waitEnderChest(MinecraftClient mc, State onSuccess) {
        HandledScreen<?> screen = InvHelper.currentContainerScreen(ecTitle);
        if (screen != null) {
            openRetries = 0;
            next(onSuccess, opts.actionDelay());
            return;
        }
        if (waitTicks++ > SCREEN_WAIT_TICKS) {
            waitTicks = 0;
            if (openRetries++ >= OPEN_RETRY) {
                fail("末影箱界面打不开（标题不匹配或服务器拦截）");
                return;
            }
            next(State.OPEN_EC, opts.actionDelay());
        }
    }
    private int findSingleBoxCoveringAll(MinecraftClient mc, boolean xpMode) {
        int best = -1;
        int bestSurplus = 0;
        for (int i = 0; i < scanned.size(); i++) {
            ShulkerScanner.Entry e = scanned.get(i);
            boolean okFw = e.fireworkStacks() >= needs.fireworkStacks;
            boolean okSecond = xpMode ? e.xpBottles() >= needs.xpBottles : e.elytra() >= needs.elytra;
            boolean okFood = e.food() >= needs.food;
            boolean okTotem = e.totems() >= needs.totems;
            if (!(okFw && okSecond && okFood && okTotem)) continue;
            if (e.fireworkStacks() + e.xpBottles() + e.food() + e.totems() + e.elytra() <= 0) continue;
            int surplus = (e.fireworkStacks() - needs.fireworkStacks)
                + (xpMode ? e.xpBottles() - needs.xpBottles : e.elytra() - needs.elytra)
                + (e.food() - needs.food) + (e.totems() - needs.totems);
            if (best < 0 || surplus > bestSurplus) {
                best = i;
                bestSurplus = surplus;
            }
        }
        return best;
    }
    private List<ShulkerScanner.Entry> filterBoxes(List<ShulkerScanner.Entry> found) {
        List<ShulkerScanner.Entry> ok = new ArrayList<>();
        List<Integer> overLimit = new ArrayList<>();
        for (ShulkerScanner.Entry e : found) {
            if (boxBlacklist.contains(e.slot())) {
                FOElytraLog.detail("跳过盒子 槽位%d（上次没取到东西，本次补给不再选它）", e.slot());
                continue;
            }
            int picks = boxPickCount.getOrDefault(e.slot(), 0);
            if (picks >= SHULKER_MAX_PICKS_PER_BOX) {
                FOElytraLog.detail("跳过盒子 槽位%d（本盒已取 %d 次）", e.slot(), picks);
                overLimit.add(e.slot());
                continue;
            }
            ok.add(e);
        }
        if (ok.isEmpty() && !overLimit.isEmpty()) {
            FOElytraLog.detail("只剩这几个盒子可用（都取满 %d 次了，这次允许重来）：%s",
                SHULKER_MAX_PICKS_PER_BOX, overLimit);
            boxPickCount.keySet().removeAll(overLimit);
            for (ShulkerScanner.Entry e : found) {
                if (!boxBlacklist.contains(e.slot())) ok.add(e);
            }
        }
        return ok;
    }
    private int findFoodBox(ScreenHandler handler, List<ShulkerScanner.Entry> entries) {
        List<Item> prio = opts.foodPriority();
        if (prio == null || prio.isEmpty()) return ShulkerScanner.findFoodRichest(entries);
        int best = -1;
        int bestRank = Integer.MAX_VALUE;
        int bestCount = 0;
        for (int i = 0; i < entries.size(); i++) {
            int rank = Integer.MAX_VALUE;
            int count = 0;
            for (int r = 0; r < prio.size(); r++) {
                int n = countFoodInBox(handler, entries.get(i).slot(), prio.get(r));
                if (n > 0) {
                    rank = r;
                    count = n;
                    break;
                }
            }
            if (rank < bestRank || (rank == bestRank && count > bestCount)) {
                bestRank = rank;
                bestCount = count;
                best = i;
            }
        }
        if (best < 0 || bestRank == Integer.MAX_VALUE) {
            FOElytraLog.detail("优先级食物在哪个盒子里都没有，改按「食物总量最多」挑盒子");
            return ShulkerScanner.findFoodRichest(entries);
        }
        FOElytraLog.detail("按优先级挑食物盒：盒[%d] %s（命中最靠前的优先级第 %d 位，%d 个）",
            entries.get(best).slot(), entries.get(best).title(), bestRank + 1, bestCount);
        return best;
    }
    private int countFoodInBox(ScreenHandler handler, int rawSlot, Item item) {
        if (handler == null || item == null || rawSlot < 0 || rawSlot >= handler.slots.size()) return 0;
        int n = 0;
        for (ItemStack s : ItemHelper.shulkerContents(handler.slots.get(rawSlot).getStack())) {
            if (!s.isEmpty() && s.isOf(item)) n += s.getCount();
        }
        return n;
    }
    private void scan(MinecraftClient mc) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlots = containerSlots(handler);
        computeNeeds(mc);
        if (!plan.isEmpty()) {
            FOElytraLog.detail("SCAN 重建计划：丢弃上一轮的 %d 条（planIndex 已到 %d）", plan.size(), planIndex);
        }
        plan.clear();
        planIndex = 0;
        storeTried.clear();
        boxFwTaken = 0;
        boxSecondTaken = 0;
        starvedWarned = false;
        starvedDetailKey = "";
        replaceRecheckSkips = 0;
        List<ShulkerScanner.Entry> found = ShulkerScanner.scan(handler, containerSlots, opts.foodItems());
        if (found.isEmpty()) {
            fail("末影箱里没有可用的潜影盒");
            return;
        }
        scanned = filterBoxes(found);
        if (scanned.isEmpty()) {
            lastMessage = "这些盒子本次补给都用过了（取空或没取到东西）";
            markExhausted();
            FOElytraLog.warn("%s（需求：%s）", lastMessage, needs);
            next(State.CLOSE_EC, 0);
            return;
        }
        Set<Integer> plannedSlots = new LinkedHashSet<>();
        boolean xpMode = needs.xpBottles > 0;
        computeReplaceSlots(mc, xpMode);
        int secondNeed = xpMode ? ItemHelper.toStacks(Items.EXPERIENCE_BOTTLE, needs.xpBottles) : needs.elytra;
        List<Integer> chosen;
        int oneBox = findSingleBoxCoveringAll(mc, xpMode);
        if (oneBox >= 0) {
            chosen = List.of(oneBox);
            FOElytraLog.tip("一个盒子就能全包（%s），直接取它", scanned.get(oneBox).title());
            FOElytraLog.detail("单盒全包快路：盒[%d] %s（烟花%d组 瓶%d 食物%d 图腾%d 鞘翅%d）",
                scanned.get(oneBox).slot(), scanned.get(oneBox).title(),
                scanned.get(oneBox).fireworkStacks(), scanned.get(oneBox).xpBottles(),
                scanned.get(oneBox).food(), scanned.get(oneBox).totems(), scanned.get(oneBox).elytra());
        } else {
            chosen = (needs.fireworkStacks > 0 || secondNeed > 0)
                ? ShulkerScanner.select(scanned, needs.fireworkStacks, secondNeed, xpMode)
                : List.of();
        }
        int fwLeft = needs.fireworkStacks;
        int secondLeft = secondNeed;
        for (int idx : chosen) {
            if (idx < 0 || idx >= scanned.size()) continue;
            ShulkerScanner.Entry e = scanned.get(idx);
            int takeFw = Math.min(fwLeft, e.fireworkStacks());
            int takeSecond = Math.min(secondLeft, xpMode ? e.xpStacks() : e.elytra());
            if (takeFw <= 0 && takeSecond <= 0) continue;
            if (!plannedSlots.add(e.slot())) continue;
            plan.add(new PlanItem(e.slot(), takeFw, takeSecond, xpMode, e.title()));
            fwLeft -= takeFw;
            secondLeft -= takeSecond;
            if (fwLeft <= 0 && secondLeft <= 0) break;
        }
        if (needs.food > 0) {
            int idx = findFoodBox(handler, scanned);
            if (idx >= 0 && scanned.get(idx).food() > 0) {
                ShulkerScanner.Entry e = scanned.get(idx);
                if (plannedSlots.add(e.slot())) {
                    plan.add(new PlanItem(e.slot(), 0, 0, xpMode, e.title()));
                } else {
                    FOElytraLog.detail("食物最富的盒子 %s 已经在计划里了，不重复排队", e.title());
                }
            }
        }
        if (needs.totems > 0) {
            int idx = ShulkerScanner.findTotemRichest(scanned);
            if (idx >= 0 && scanned.get(idx).totems() >= Math.min(2, Math.max(1, needs.totems))) {
                ShulkerScanner.Entry e = scanned.get(idx);
                if (plannedSlots.add(e.slot())) {
                    plan.add(new PlanItem(e.slot(), 0, 0, xpMode, e.title()));
                } else {
                    FOElytraLog.detail("图腾最多的盒子 %s 已经在计划里了，不重复排队", e.title());
                }
            }
        }
        if (plan.size() > opts.maxShulkers()) {
            FOElytraLog.warn("需要 %d 个盒子，超过单次上限 %d，只取前 %d 个", plan.size(), opts.maxShulkers(), opts.maxShulkers());
            plan.subList(opts.maxShulkers(), plan.size()).clear();
        }
        for (PlanItem p : plan) {
            boxPickCount.merge(p.rawSlot(), 1, Integer::sum);
        }
        if (opts.debug()) {
            for (ShulkerScanner.Entry e : scanned) {
                FOElytraLog.debug("盒子[%d] %s: 烟花%d组 瓶%d 食物%d 图腾%d 鞘翅%d",
                    e.slot(), e.title(), e.fireworkStacks(), e.xpBottles(), e.food(), e.totems(), e.elytra());
            }
        }
        if (plan.isEmpty()) {
            lastMessage = "没翻到能补上需求的东西（可能是箱子里没有，也可能这批盒子没东西）";
            markExhausted();
            FOElytraLog.warn("%s（需求：%s）", lastMessage, needs);
            next(State.CLOSE_EC, 0);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plan.size(); i++) {
            PlanItem p = plan.get(i);
            if (i > 0) sb.append("、");
            sb.append(p.rawSlot());
            if (p.fireworkStacks() > 0 || p.second() > 0) {
                sb.append("(烟花").append(p.fireworkStacks()).append("组");
                if (p.second() > 0) sb.append(' ').append(p.xpMode() ? "瓶" : "鞘翅").append(p.second());
                sb.append(")");
            } else {
                sb.append("(食物/图腾)");
            }
        }
        FOElytraLog.info("所需的潜影盒槽位列表为：[%s]（需求：%s）", sb, needs);
        next(State.TAKE_SHULKER, opts.actionDelay());
    }
    private void computeReplaceSlots(MinecraftClient mc, boolean xpMode) {
        replaceSlots.clear();
        int keepFireworks = Math.max(0, opts.targetFireworkStacks());
        int fwKept = 0;
        int fwPartialKept = 0;
        int bottleKept = 0;
        int elytraKept = 0;
        int totemKept = 0;
        int ecKept = 0;
        int foodKept = 0;
        int toolKept = 0;
        int shulkerKept = 0;
        List<Integer> emptySlots = new ArrayList<>();
        List<Integer> junkSlots = new ArrayList<>();
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                emptySlots.add(i);
                continue;
            }
            if (s.isOf(Items.FIREWORK_ROCKET)) {
                if (s.getCount() < s.getMaxCount()) fwPartialKept++;
                else fwKept++;
            } else if (s.isOf(Items.EXPERIENCE_BOTTLE)) {
                bottleKept++;
            } else if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                totemKept++;
            } else if (s.isOf(Items.ENDER_CHEST)) {
                ecKept++;
            } else if (matchesFood(s)) {
                foodKept++;
            } else if (isPickaxe(s) || isSword(s)) {
                toolKept++;
            } else if (ItemHelper.isShulkerBox(s)) {
                shulkerKept++;
            } else if (s.isOf(Items.ELYTRA)) {
                if (isGoodElytra(s) && (needs.elytra > 0 || elytraKept < 5)) elytraKept++;
                else junkSlots.add(i);
            } else {
                junkSlots.add(i);
            }
        }
        replaceSlots.addAll(emptySlots);
        replaceSlots.addAll(junkSlots);
        replaceSlotSummary = String.format("空槽 %d / 杂物 %d / 排除：烟花 %d 摞（零散 %d 格、目标 %d 组）、"
                + "瓶 %d 摞、图腾 %d 个、末影箱 %d 个、食物 %d 堆、工具 %d 件、潜影盒 %d 个、好鞘翅 %d 条",
            emptySlots.size(), junkSlots.size(), fwKept, fwPartialKept, keepFireworks,
            bottleKept, totemKept, ecKept, foodKept, toolKept, shulkerKept, elytraKept);
        if (opts.debug()) {
            FOElytraLog.debug("可替换槽位 %d 个（空槽 %d 个优先 + 杂物 %d 个）：%s｜"
                    + "排除 满摞烟花%d摞/零散烟花%d格(目标%d组) 瓶%d摞 鞘翅%d条 图腾%d个 末影箱%d个 食物%d堆 工具%d件 盒%d个（xpMode=%s）",
                replaceSlots.size(), emptySlots.size(), junkSlots.size(), replaceSlots,
                fwKept, fwPartialKept, keepFireworks, bottleKept, elytraKept, totemKept, ecKept, foodKept,
                toolKept, shulkerKept, xpMode);
        }
    }
    private void takeShulker(MinecraftClient mc) {
        if (planIndex >= plan.size()) {
            next(State.CLOSE_EC, 0);
            return;
        }
        HandledScreen<?> screen = InvHelper.currentContainerScreen(ecTitle);
        if (screen == null) {
            fail("取盒时末影箱界面已关闭");
            return;
        }
        shulkerRawSlot = plan.get(planIndex).rawSlot();
        ScreenHandler handler = mc.player.currentScreenHandler;
        int hotbarSlot = makeHotbarRoom(handler);
        if (hotbarSlot < 0) {
            fail("快捷栏没有空位可以放潜影盒（已尝试把杂物/多余烟花挪回背包，仍腾不出来）");
            return;
        }
        int rawPlayerSlot = InvHelper.playerSlotId(handler, mc.player, hotbarSlot);
        if (rawPlayerSlot < 0) {
            fail("找不到快捷栏槽位映射");
            return;
        }
        shulkerCountBeforeTake = ItemHelper.countShulkers(mc.player);
        InvHelper.moveStack(handler, shulkerRawSlot, rawPlayerSlot);
        shulkerHotbarSlot = hotbarSlot;
        next(State.VERIFY_TAKE, opts.actionDelay());
    }
    private void verifyTake(MinecraftClient mc) {
        ItemStack held = mc.player.getInventory().getStack(shulkerHotbarSlot);
        int boxes = ItemHelper.countShulkers(mc.player);
        if (!ItemHelper.isShulkerBox(held)) {
            fail("潜影盒没有取到手（槽位 " + shulkerHotbarSlot + " 上是 "
                + (held.isEmpty() ? "空" : held.getName().getString()) + "；背包潜影盒 " + boxes + " 个）");
            return;
        }
        if (boxes < shulkerCountBeforeTake + 1) {
            FOElytraLog.detail("VERIFY_TAKE 复核异常：背包潜影盒 %d，取出前 %d（本地没看到盒子进背包）",
                boxes, shulkerCountBeforeTake);
        }
        shulkerTitle = held.getName().getString();
        InvHelper.closeScreen();
        openRetries = 0;
        placeRetries = 0;
        placeWaitTicks = 0;
        starvedWarned = false;
        starvedDetailKey = "";
        replaceRecheckSkips = 0;
        pendingReturn = false;
        boxFwTaken = 0;
        boxSecondTaken = 0;
        storeTried.clear();
        boxTookAnything = false;
        boxTookPartial = false;
        computeReplaceSlots(mc, needs.xpBottles > 0);
        PlanItem item = plan.get(planIndex);
        if (item.fireworkStacks() > 0 || item.second() > 0) {
            FOElytraLog.tip("本盒需要取出 %d 组烟花、%d %s（当前需求：%s）",
                item.fireworkStacks(), item.second(), item.xpMode() ? "组附魔之瓶" : "个鞘翅", needs);
        }
        next(State.PLACE_SH, Math.max(6, opts.actionDelay() * 3));
    }
    private void placeShulker(MinecraftClient mc) {
        if (!ensureShulkerSelected(mc)) {
            fail("手上和背包里都找不到潜影盒可放（selectedSlot=" + mc.player.getInventory().getSelectedSlot() + "）");
            return;
        }
        beginPlaceScan();
        int before = ItemHelper.countShulkers(mc.player);
        BlockPos pos = null;
        while (true) {
            BlockPos cand = nextPlaceCandidate(mc, "潜影盒");
            if (cand == null) break;
            boolean accepted = InvHelper.placeBlock(cand, shulkerHotbarSlot);
            shulkerCountAfterPlace = ItemHelper.countShulkers(mc.player);
            FOElytraLog.detail("PLACE_SH 放盒：目标 %s｜selectedSlot=%d 手持=%s｜背包潜影盒 %d → %d｜"
                    + "interactBlock 客户端结果=%s｜getBlockState=%s（方块/数量都要等服务端包回来才算数）",
                cand.toShortString(), mc.player.getInventory().getSelectedSlot(), describeHeld(mc),
                before, shulkerCountAfterPlace, accepted, describeBlock(mc, cand));
            if (accepted) {
                pos = cand;
                break;
            }
            FOElytraLog.warn("放置潜影盒：客户端当场拒绝（目标 %s，手持 %s）→ 换下一个位置",
                cand.toShortString(), describeHeld(mc));
            rejectPlaceCandidate(cand, "潜影盒");
        }
        if (pos == null) {
            fail(placeEmptyReason.isEmpty() ? "附近没有合适的位置放置潜影盒" : placeEmptyReason);
            return;
        }
        shulkerPos = pos;
        placeWaitTicks = 0;
        next(State.OPEN_SH, opts.actionDelay());
    }
    private void openShulker(MinecraftClient mc) {
        if (shulkerPos == null) {
            fail("潜影盒坐标丢失（没经过 PLACE_SH 就进了 OPEN_SH）");
            return;
        }
        BlockState st = mc.world.getBlockState(shulkerPos);
        int boxes = ItemHelper.countShulkers(mc.player);
        if (!st.isAir()) {
            if (placeWaitTicks > 0) {
                FOElytraLog.detail("OPEN_SH 潜影盒方块已出现（轮询了 %d tick）：%s",
                    placeWaitTicks, describeBlock(mc, shulkerPos));
            }
            placeWaitTicks = 0;
            placeRetries = 0;
            InvHelper.interactBlock(shulkerPos);
            waitTicks = 0;
            next(State.WAIT_SH, 1);
            return;
        }
        placeWaitTicks++;
        if (placeWaitTicks <= SHULKER_BLOCK_WAIT_TICKS) {
            if (placeWaitTicks == 1 || placeWaitTicks % PLACE_POLL_LOG_INTERVAL == 0) {
                FOElytraLog.detail("OPEN_SH 还没看到潜影盒方块（%d/%d tick，服务端包在路上或这次放置将被回滚）："
                        + "目标 %s｜背包潜影盒 %d（放置后记录 %d）｜selectedSlot=%d 手持=%s",
                    placeWaitTicks, SHULKER_BLOCK_WAIT_TICKS, shulkerPos.toShortString(),
                    boxes, shulkerCountAfterPlace, mc.player.getInventory().getSelectedSlot(), describeHeld(mc));
            }
            next(State.OPEN_SH, 0);
            return;
        }
        FOElytraLog.detail("OPEN_SH 失败诊断：目标 %s｜getBlockState=%s｜背包潜影盒 现在=%d / 放置后=%d / 取出前=%d｜"
                + "selectedSlot=%d 手持=%s｜轮询 %d tick 无方块｜已换位置 %d 次",
            shulkerPos.toShortString(), describeBlock(mc, shulkerPos), boxes, shulkerCountAfterPlace,
            shulkerCountBeforeTake, mc.player.getInventory().getSelectedSlot(), describeHeld(mc),
            placeWaitTicks, placeRetries);
        if (boxes >= 1) {
            placeRetries++;
            rejectPlaceCandidate(shulkerPos, "潜影盒");
            FOElytraLog.warn("潜影盒放下去后 %d tick 里客户端都没看到方块（盒子还在背包 %d 个）→ "
                    + "把这个位置拉黑、换个位置再放（第 %d 次换位置，%d tick 后重放）",
                placeWaitTicks, boxes, placeRetries, PLACE_RETRY_WAIT_TICKS);
            placeWaitTicks = 0;
            next(State.PLACE_SH, PLACE_RETRY_WAIT_TICKS);
            return;
        }
        fail("潜影盒没有放好（方块始终没出现，而且盒子已经不在背包里了：放置后 " + shulkerCountAfterPlace
            + " → 现在 " + boxes + "，目标 " + shulkerPos.toShortString() + " 现在是 " + describeBlock(mc, shulkerPos)
            + "，可能是服务端收下了物品但方块没落地，或盒子被别的玩家/实体拿走）");
    }
    private void waitShulker(MinecraftClient mc) {
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen) {
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (containerSlots(handler) >= 27) {
                merged = false;
                moveGuard = 0;
                next(State.MOVE_ITEMS, opts.actionDelay());
                return;
            }
        }
        if (waitTicks++ > SCREEN_WAIT_TICKS) {
            if (openRetries++ >= OPEN_RETRY) {
                fail("潜影盒界面打不开");
                return;
            }
            next(State.OPEN_SH, opts.actionDelay());
        }
    }
    private void moveItems(MinecraftClient mc) {
        if (moveGuard++ > MOVE_GUARD) {
            FOElytraLog.warn("取物资步数超限，停止搬运");
            next(State.CLOSE_SH, 0);
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlots = containerSlots(handler);
        if (containerSlots < 27) {
            next(State.CLOSE_SH, 0);
            return;
        }
        if (opts.storeLoot() && storeOneJunkStack(mc, handler)) {
            delay = opts.actionDelay();
            return;
        }
        if (!merged) {
            InvHelper.mergeSameItems(handler, s -> s.isOf(Items.FIREWORK_ROCKET), 0, containerSlots);
            InvHelper.mergeSameItems(handler, s -> s.isOf(Items.EXPERIENCE_BOTTLE), 0, containerSlots);
            InvHelper.mergeSameItems(handler, s -> s.isOf(Items.TOTEM_OF_UNDYING), 0, containerSlots);
            merged = true;
            delay = opts.actionDelay();
            return;
        }
        if (putOutOneStack(mc, handler, containerSlots, false)) {
            boxTookAnything = true;
            delay = opts.actionDelay();
            return;
        }
        if (putOutOneStack(mc, handler, containerSlots, true)) {
            boxTookAnything = true;
            boxTookPartial = true;
            delay = opts.actionDelay();
            return;
        }
        if (!boxTookAnything) {
            FOElytraLog.warn("本盒没有取到任何东西（盒子里没有需要的物品，或背包 9-35 格全是要留的东西）");
        } else if (boxTookPartial) {
            FOElytraLog.tip("本盒只取到零散堆（没有整摞可拿）");
        }
        FOElytraLog.tip("本次取物完成（本盒已取 烟花 %d 组 / %s %d），剩余需求（增量账，跨盒时会按背包重算）：%s",
            boxFwTaken, needs.xpBottles > 0 ? "瓶" : "鞘翅", boxSecondTaken,
            needs.isEmpty() ? "无" : needs.toString());
        next(State.CLOSE_SH, opts.actionDelay());
    }
    private int hotbarSameItem(MinecraftClient mc, ScreenHandler handler, Item item) {
        for (int j = 0; j < 9; j++) {
            if (mc.player.getInventory().getStack(j).isOf(item)) {
                int raw = InvHelper.playerSlotId(handler, mc.player, j);
                if (raw >= 0) return raw;
            }
        }
        return -1;
    }
    private int bestFoodSlot(MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        List<Item> prio = opts.foodPriority();
        int bestSlot = -1;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty() || !matchesFood(stack)) continue;
            if (hotbarSameItem(mc, handler, stack.getItem()) < 0) continue;
            int rank = 0;
            if (prio != null && !prio.isEmpty()) {
                int idx = prio.indexOf(stack.getItem());
                rank = idx < 0 ? prio.size() : idx;
            }
            if (rank < bestRank) {
                bestRank = rank;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
    private boolean putOutOneStack(MinecraftClient mc, ScreenHandler handler, int containerSlots, boolean allowPartial) {
        PlanItem item = plan.get(planIndex);
        int foodSlot = needs.food > 0 ? bestFoodSlot(mc, handler, containerSlots) : -1;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            if (matchesFood(stack) && needs.food > 0) {
                if (i != foodSlot) continue;
                int raw = hotbarSameItem(mc, handler, stack.getItem());
                if (raw < 0) continue;
                int taken = stack.getCount();
                Item takenItem = stack.getItem();
                InvHelper.moveStack(handler, i, raw);
                consumeNeed(takenItem, taken);
                return true;
            }
            if (stack.isOf(Items.TOTEM_OF_UNDYING) && needs.totems > 0) {
                int target = mc.player.getInventory().getStack(3).isOf(Items.TOTEM_OF_UNDYING) ? 4 : 3;
                int raw = InvHelper.playerSlotId(handler, mc.player, target);
                if (raw >= 0) {
                    int taken = stack.getCount();
                    Item takenItem = stack.getItem();
                    InvHelper.moveStack(handler, i, raw);
                    consumeNeed(takenItem, taken);
                    return true;
                }
                continue;
            }
            if (stack.isOf(Items.FIREWORK_ROCKET)
                && (allowPartial || stack.getCount() >= stack.getMaxCount())
                && boxFwTaken < item.fireworkStacks()) {
                int taken = stack.getCount();
                Item takenItem = stack.getItem();
                if (mergeIntoPartialStack(mc, handler, i, takenItem)
                    || moveToReplaceSlot(mc, handler, i, item.xpMode())) {
                    boxFwTaken++;
                    consumeNeed(takenItem, taken);
                    return true;
                }
                return false;
            }
            boolean isSecond = item.xpMode()
                ? stack.isOf(Items.EXPERIENCE_BOTTLE)
                : (stack.isOf(Items.ELYTRA)
                    && ItemHelper.hasEnchantment(stack, net.minecraft.enchantment.Enchantments.UNBREAKING, 3)
                    && stack.getDamage() < 15);
            if (isSecond && (allowPartial || stack.getCount() >= stack.getMaxCount())
                && boxSecondTaken < item.second()) {
                int taken = stack.getCount();
                Item takenItem = stack.getItem();
                if (mergeIntoPartialStack(mc, handler, i, takenItem)
                    || moveToReplaceSlot(mc, handler, i, item.xpMode())) {
                    boxSecondTaken++;
                    consumeNeed(takenItem, taken);
                    return true;
                }
                return false;
            }
        }
        return false;
    }
    private boolean mergeIntoPartialStack(MinecraftClient mc, ScreenHandler handler, int rawSlot, Item item) {
        ItemStack src = handler.slots.get(rawSlot).getStack();
        if (src.isEmpty() || !src.isOf(item)) return false;
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? 0 : 9;
            int to = pass == 0 ? 9 : 36;
            for (int inv = from; inv < to; inv++) {
                ItemStack s = mc.player.getInventory().getStack(inv);
                if (s.isEmpty() || !s.isOf(item)) continue;
                if (s.getCount() >= s.getMaxCount()) continue;
                int raw = InvHelper.playerSlotId(handler, mc.player, inv);
                if (raw < 0) continue;
                InvHelper.moveStack(handler, rawSlot, raw);
                FOElytraLog.detail("合并取物：盒 #%d %s → 背包 #%d（已有 %d/%d）",
                    rawSlot, item.getName().getString(), inv, s.getCount(), s.getMaxCount());
                return true;
            }
        }
        return false;
    }
    private boolean moveToReplaceSlot(MinecraftClient mc, ScreenHandler handler, int rawSlot, boolean xpMode) {
        if (replaceSlots.isEmpty()) {
            computeReplaceSlots(mc, xpMode);
        }
        int invIndex = -1;
        int rawTarget = -1;
        for (int checked = 0; checked < SWAP_RECHECK_MAX && !replaceSlots.isEmpty(); checked++) {
            int candidate = replaceSlots.pollFirst();
            ItemStack now = mc.player.getInventory().getStack(candidate);
            if (!isJunkNow(now)) {
                replaceRecheckSkips++;
                Integer next = replaceSlots.peekFirst();
                FOElytraLog.detail("换位目标 背包 #%d = %s → 已排除（现在不是杂物了，RC-10 复检），改选 #%s",
                    candidate, describeStack(now), next == null ? "（名单已空）" : String.valueOf(next));
                continue;
            }
            int raw = InvHelper.playerSlotId(handler, mc.player, candidate);
            if (raw < 0) continue;
            invIndex = candidate;
            rawTarget = raw;
            break;
        }
        if (rawTarget < 0) {
            if (!starvedWarned) {
                starvedWarned = true;
                FOElytraLog.warn("没多余槽位了（背包 9-35 格全是要留的东西），停止取物");
            }
            ItemStack stuck = (rawSlot >= 0 && rawSlot < handler.slots.size())
                ? handler.slots.get(rawSlot).getStack() : ItemStack.EMPTY;
            String what = stuck.isEmpty()
                ? ("盒 #" + rawSlot)
                : ("盒 #" + rawSlot + " " + stuck.getName().getString() + " x" + stuck.getCount());
            if (!what.equals(starvedDetailKey)) {
                starvedDetailKey = what;
                String summary = replaceSlotSummary.isEmpty() ? "（没有可替换槽位统计）" : replaceSlotSummary;
                if (replaceRecheckSkips > 0) summary += "｜点击前复检又排除 " + replaceRecheckSkips + " 格";
                FOElytraLog.detail("取物受阻（拿不出来）：想取 %s，但背包 9-35 没有可用槽位（%s）"
                        + "→ 本盒放弃该物品；这只说明「腾不出格子」，不代表盒子里/末影箱里没有（不登记暂时取不到）",
                    what, summary);
            }
            return false;
        }
        InvHelper.moveStack(handler, rawSlot, rawTarget);
        FOElytraLog.debug("取物：盒 #%d → 背包 #%d", rawSlot, invIndex);
        return true;
    }
    private void consumeNeed(Item item, int taken) {
        int count = Math.max(1, taken);
        if (item == Items.FIREWORK_ROCKET) needs.fireworkStacks -= Math.max(1, count / 64);
        else if (item == Items.EXPERIENCE_BOTTLE) needs.xpBottles -= count;
        else if (item == Items.TOTEM_OF_UNDYING) needs.totems -= count;
        else if (item == Items.ELYTRA) needs.elytra -= count;
        else if (matchesFood(item)) needs.food -= count;
        if (needs.fireworkStacks < 0) needs.fireworkStacks = 0;
        if (needs.xpBottles < 0) needs.xpBottles = 0;
        if (needs.food < 0) needs.food = 0;
        if (needs.totems < 0) needs.totems = 0;
        if (needs.elytra < 0) needs.elytra = 0;
    }
    private int findReceiveSlot(MinecraftClient mc, ItemStack moving) {
        var inv = mc.player.getInventory();
        Item item = moving.getItem();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.isOf(item) && s.getCount() < s.getMaxCount()) return i;
        }
        if (item == Items.FIREWORK_ROCKET || item == Items.EXPERIENCE_BOTTLE) {
            for (int i = 0; i < 9; i++) {
                if (inv.getStack(i).isEmpty()) return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }
    private boolean storeOneJunkStack(MinecraftClient mc, ScreenHandler handler) {
        for (int i = 9; i < 36; i++) {
            if (storeTried.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || isEssential(s)) continue;
            if (!opts.storeItems().isEmpty() && !opts.storeItems().contains(s.getItem())) continue;
            int raw = InvHelper.playerSlotId(handler, mc.player, i);
            if (raw < 0) continue;
            storeTried.add(i);
            InvHelper.quickMove(handler, raw);
            return true;
        }
        return false;
    }
    private boolean isEssential(ItemStack s) {
        return s.isOf(Items.FIREWORK_ROCKET) || s.isOf(Items.EXPERIENCE_BOTTLE)
            || s.isOf(Items.TOTEM_OF_UNDYING) || s.isOf(Items.ELYTRA)
            || s.isOf(Items.ENDER_CHEST) || ItemHelper.isShulkerBox(s)
            || isPickaxe(s) || isSword(s) || matchesFood(s);
    }
    private boolean matchesFood(ItemStack s) {
        if (opts.foodItems().isEmpty()) return ItemHelper.isFood(s);
        return opts.foodItems().contains(s.getItem());
    }
    private boolean matchesFood(Item item) {
        if (item == null) return false;
        List<Item> food = opts.foodItems();
        if (food == null || food.isEmpty()) return SettingHelper.isFood(item);
        return food.contains(item);
    }
    private void breakShulker(MinecraftClient mc) {
        if (shulkerPos == null) {
            next(State.REOPEN_EC, 0);
            return;
        }
        InvHelper.closeScreen();
        if (mc.world.getBlockState(shulkerPos).isAir()) {
            next(State.WAIT_BREAK_SH, 1);
            return;
        }
        if (!breakRequested) {
            breakTicks = 0;
            pickupWait = 0;
            shulkerBreakTarget = ItemHelper.countShulkers(mc.player) + 1;
            Block block = mc.world.getBlockState(shulkerPos).getBlock();
            if (opts.useBaritoneMine() && BaritoneHook.available() && hasPickaxe(mc)) {
                if (BaritoneHook.mine(shulkerBreakTarget, block)) {
                    breakRequested = true;
                    FOElytraLog.tip("让 Baritone 挖回潜影盒（目标：背包 %d 个）", shulkerBreakTarget);
                    next(State.WAIT_BREAK_SH, 1);
                    return;
                }
            } else if (!hasPickaxe(mc)) {
                FOElytraLog.warn("快捷栏里没有镐，挖不回潜影盒，它会留在原地");
                pendingReturn = false;
                next(State.REOPEN_EC, opts.actionDelay());
                return;
            }
            FOElytraLog.warn("Baritone 挖掘不可用，改用渐进破坏（掉落物可能不会自动捡起）");
            breakRequested = true;
        }
        if (BlockBreaker.tick(shulkerPos)) {
            breakRequested = false;
            if (!mc.world.getBlockState(shulkerPos).isAir()) {
                FOElytraLog.warn("潜影盒没能挖掉，它留在了原地");
                pendingReturn = false;
                next(State.REOPEN_EC, opts.actionDelay());
                return;
            }
            waitTicks = 0;
            next(State.WAIT_BREAK_SH, 2);
        }
    }
    private void waitBreakShulker(MinecraftClient mc) {
        boolean gone = mc.world.getBlockState(shulkerPos).isAir();
        boolean gotIt = ItemHelper.countShulkers(mc.player) >= shulkerBreakTarget;
        if (gone && gotIt) {
            BaritoneHook.stop();
            breakRequested = false;
            FOElytraLog.tip("潜影盒已收回（背包 %d 个）", ItemHelper.countShulkers(mc.player));
            pendingReturn = true;
            next(State.REOPEN_EC, opts.actionDelay());
            return;
        }
        stopMiningAfterPickupWindow(gone);
        waitTicks++;
        if (waitTicks == 40 && BaritoneHook.isMining()) {
            FOElytraLog.warn("挖掘异常？取消挖掘");
            BaritoneHook.stop();
        }
        if (waitTicks > 120) {
            FOElytraLog.warn("挖掘补给箱失败!（盒子没进背包：可能掉在远处/被水冲走/背包满）");
            BaritoneHook.stop();
            breakRequested = false;
            pendingReturn = true;
            next(State.REOPEN_EC, opts.actionDelay());
        }
    }
    private void returnShulker(MinecraftClient mc) {
        HandledScreen<?> screen = InvHelper.currentContainerScreen(ecTitle);
        if (screen == null) {
            fail("放回盒子时末影箱界面已关闭");
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        int rawHotbar = InvHelper.playerSlotId(handler, mc.player, shulkerHotbarSlot);
        if (rawHotbar < 0 || shulkerRawSlot < 0) {
            fail("放回盒子的槽位映射失败");
            return;
        }
        if (!ItemHelper.isShulkerBox(mc.player.getInventory().getStack(shulkerHotbarSlot))) {
            FOElytraLog.warn("手里没有潜影盒可放回，跳过本步");
            pendingReturn = false;
            next(State.CLOSE_EC, opts.actionDelay());
            return;
        }
        InvHelper.moveStack(handler, rawHotbar, shulkerRawSlot);
        FOElytraLog.tip("潜影盒已放回末影箱槽位 %d", shulkerRawSlot);
        pendingReturn = false;
        shulkerHotbarSlot = -1;
        next(State.CLOSE_EC, opts.actionDelay());
    }
    private void nextShulker(MinecraftClient mc) {
        String needsBefore = needs.toString();
        computeNeeds(mc);
        if (!needsBefore.equals(needs.toString())) {
            FOElytraLog.detail("NEXT_SH 需求按背包重算：%s → %s（增量账与背包不一致时以背包为准）",
                needsBefore, needs);
        }
        boolean exhaustedMarked = false;
        if (!boxTookAnything) {
            if (shulkerRawSlot >= 0 && boxBlacklist.add(shulkerRawSlot)) {
                FOElytraLog.detail("跳过盒子 槽位%d（上次没取到东西，本次补给不再选它）", shulkerRawSlot);
            }
            emptyBoxStrikes++;
            if (emptyBoxStrikes >= 2) {
                FOElytraLog.warn("连续 %d 盒都没取到东西，停止取物（%s）", emptyBoxStrikes,
                    needs.isEmpty() ? "需求其实已经满足" : "仍有缺口：" + needs);
                markExhausted();
                exhaustedMarked = true;
                needs.fireworkStacks = 0;
                needs.xpBottles = 0;
                needs.elytra = 0;
                planIndex = plan.size();
            }
        } else {
            emptyBoxStrikes = 0;
        }
        planIndex++;
        if (planIndex < plan.size() && needs.needsAnyItem() && emptyBoxStrikes < 2) {
            waitTicks = 0;
            pendingReturn = false;
            next(State.OPEN_EC, 0);
            return;
        }
        if (!needs.isEmpty() && !exhaustedMarked) {
            markExhausted();
            FOElytraLog.warn("补给结束，但仍有缺口：%s", needs);
        } else if (!needs.isEmpty()) {
            FOElytraLog.warn("补给结束，但仍有缺口：%s（这些项本轮暂时取不到，已在前面登记）", needs);
        }
        next(State.BREAK_EC, opts.actionDelay());
    }
    private void breakEnderChest(MinecraftClient mc) {
        InvHelper.closeScreen();
        if (!opts.autoPickupEnderChest()) {
            next(State.DONE, 0);
            return;
        }
        if (ecPos == null || mc.world.getBlockState(ecPos).isAir()) {
            next(State.DONE, 0);
            return;
        }
        if (!breakRequested) {
            breakTicks = 0;
            pickupWait = 0;
            ecItemBefore = ItemHelper.countInInventory(mc.player, Items.ENDER_CHEST);
            obsidianBefore = ItemHelper.countInInventory(mc.player, Items.OBSIDIAN);
            boolean silk = hasSilkTouch(mc);
            ecBreakTarget = (silk ? ecItemBefore : obsidianBefore) + 1;
            if (silk) {
                FOElytraLog.tip("检测到精准采集镐：末影箱会掉末影箱本身，按「末影箱 %d 个」校验", ecBreakTarget);
            }
            Block block = mc.world.getBlockState(ecPos).getBlock();
            if (opts.useBaritoneMine() && BaritoneHook.available() && hasPickaxe(mc)) {
                if (BaritoneHook.mine(ecBreakTarget, block)) {
                    breakRequested = true;
                    if (!silk) FOElytraLog.tip("让 Baritone 挖回末影箱（目标：黑曜石 %d 个）", ecBreakTarget);
                    waitTicks = 0;
                    next(State.WAIT_BREAK_EC, 1);
                    return;
                }
            } else if (!hasPickaxe(mc)) {
                FOElytraLog.warn("快捷栏里没有镐，末影箱会留在原地");
                next(State.DONE, 0);
                return;
            }
            FOElytraLog.warn("Baritone 挖掘不可用，改用渐进破坏");
            breakRequested = true;
        }
        if (BlockBreaker.tick(ecPos)) {
            breakRequested = false;
            if (!mc.world.getBlockState(ecPos).isAir()) {
                FOElytraLog.warn("末影箱没能挖掉，它留在了原地");
                next(State.DONE, 0);
                return;
            }
            waitTicks = 0;
            next(State.WAIT_BREAK_EC, 2);
        }
    }
    private void waitBreakEnderChest(MinecraftClient mc) {
        boolean gone = mc.world.getBlockState(ecPos).isAir();
        int ecNow = ItemHelper.countInInventory(mc.player, Items.ENDER_CHEST);
        int obsidianNow = ItemHelper.countInInventory(mc.player, Items.OBSIDIAN);
        boolean gotIt = ecNow > ecItemBefore || obsidianNow > obsidianBefore;
        if (gone && gotIt) {
            BaritoneHook.stop();
            breakRequested = false;
            FOElytraLog.tip("末影箱已收回（末影箱 %d 个 / 黑曜石 %d 个）", ecNow, obsidianNow);
            next(State.DONE, 0);
            return;
        }
        stopMiningAfterPickupWindow(gone);
        waitTicks++;
        if (waitTicks == 100 && BaritoneHook.isMining()) {
            FOElytraLog.warn("挖掘异常？取消挖掘!!");
            BaritoneHook.stop();
        }
        if (waitTicks > 200) {
            FOElytraLog.warn("末影箱可能没捡起来（背包满？掉在远处？用的是精准采集但没捡到末影箱？）");
            BaritoneHook.stop();
            breakRequested = false;
            next(State.DONE, 0);
        }
    }
    private void stopMiningAfterPickupWindow(boolean blockGone) {
        if (!blockGone) {
            pickupWait = 0;
            return;
        }
        pickupWait++;
        if (pickupWait == 40 && BaritoneHook.isMining()) {
            BaritoneHook.stop();
            FOElytraLog.debug("方块已消失，停掉挖掘进程等掉落物进背包");
        }
    }
    private boolean hasSilkTouch(MinecraftClient mc) {
        ItemStack selected = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        if (isPickaxe(selected) && ItemHelper.hasEnchantment(selected, net.minecraft.enchantment.Enchantments.SILK_TOUCH, 1)) {
            return true;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isPickaxe(s) && ItemHelper.hasEnchantment(s, net.minecraft.enchantment.Enchantments.SILK_TOUCH, 1)) return true;
        }
        return false;
    }
    private boolean hasPickaxe(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            if (isPickaxe(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }
    private boolean tickBreakBlock(MinecraftClient mc, BlockPos pos) {
        if (mc.world.getBlockState(pos).isAir()) {
            breakTicks = 0;
            return true;
        }
        if (!pos.equals(breakTarget)) {
            breakTarget = pos;
            breakTicks = 0;
            breakRequested = false;
        }
        if (breakTicks++ > 240) {
            FOElytraLog.warn("挖方块超时（%d, %d, %d）", pos.getX(), pos.getY(), pos.getZ());
            BaritoneHook.stop();
            BlockBreaker.cancel();
            breakTicks = 0;
            return true;
        }
        if (opts.useBaritoneMine() && BaritoneHook.available()) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (!breakRequested) {
                if (!BaritoneHook.mine(1, block)) {
                    return BlockBreaker.tick(pos);
                }
                breakRequested = true;
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
        needs.fireworkStacks = Math.max(0, opts.targetFireworkStacks()
            - ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET)));
        needs.xpBottles = Math.max(0, opts.targetXpBottles()
            - ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE));
        needs.food = Math.max(0, opts.targetFoodCount() - countFood(mc));
        needs.totems = Math.max(0, opts.targetTotems()
            - ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING));
        needs.elytra = Math.max(0, opts.targetElytraCount() - countUsableElytra(mc));
    }
    private int countFood(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && matchesFood(s)) n += s.getCount();
        }
        return n;
    }
    private int countUsableElytra(MinecraftClient mc) {
        int n = 0;
        for (int i = 0; i < 41; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.ELYTRA) && ItemHelper.hasEnchantment(s, net.minecraft.enchantment.Enchantments.UNBREAKING, 3)
                && s.getDamage() < 15) {
                n++;
            }
        }
        return n;
    }
    private int findEnderChestHotbar() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_CHEST)) return i;
        }
        return -1;
    }
    private int findShulkerHotbarSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 6; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }
    private boolean ensureShulkerSelected(MinecraftClient mc) {
        var inv = mc.player.getInventory();
        if (ItemHelper.isShulkerBox(inv.getStack(shulkerHotbarSlot))) {
            inv.setSelectedSlot(shulkerHotbarSlot);
            return true;
        }
        int hot = InvHelper.findSlot(s -> ItemHelper.isShulkerBox(s), 0, 9);
        if (hot >= 0) {
            shulkerHotbarSlot = hot;
            inv.setSelectedSlot(hot);
            FOElytraLog.detail("重放前重新定位手持潜影盒：快捷栏第 %d 格", hot);
            return true;
        }
        int bag = InvHelper.findSlot(s -> ItemHelper.isShulkerBox(s));
        if (bag >= 0) {
            int empty = InvHelper.findEmptyHotbarSlot();
            if (empty < 0) {
                FOElytraLog.detail("背包第 %d 格有潜影盒，但快捷栏 0-8 没有空位可以放它（此时不能开末影箱腾位："
                    + "界面已经关了）→ 不放置，交给上层判失败", bag);
                return false;
            }
            InvHelper.moveInvToHotbar(bag, empty);
            if (!ItemHelper.isShulkerBox(inv.getStack(empty))) {
                FOElytraLog.detail("换位没生效：槽 %d 上是 %s（不是潜影盒）→ 不放置，交给上层判失败",
                    empty, inv.getStack(empty).isEmpty() ? "空" : inv.getStack(empty).getName().getString());
                return false;
            }
            shulkerHotbarSlot = empty;
            inv.setSelectedSlot(empty);
            FOElytraLog.detail("重放前把背包第 %d 格的潜影盒换到快捷栏第 %d 格", bag, empty);
            return true;
        }
        return false;
    }
    private String describeHeld(MinecraftClient mc) {
        int slot = mc.player.getInventory().getSelectedSlot();
        return describeStack(mc.player.getInventory().getSelectedStack()) + "（槽 " + slot + "）";
    }
    private String describeStack(ItemStack s) {
        if (s.isEmpty()) return "空";
        return s.getName().getString() + " x" + s.getCount();
    }
    private String describeBlock(MinecraftClient mc, BlockPos pos) {
        if (pos == null) return "(无坐标)";
        BlockState st = mc.world.getBlockState(pos);
        return st.isAir() ? "空气" : st.getBlock().getName().getString();
    }
    private int makeHotbarRoom(ScreenHandler handler) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int direct = findShulkerHotbarSlot();
        if (direct >= 0) return direct;
        for (int h = 0; h < 9; h++) {
            ItemStack s = mc.player.getInventory().getStack(h);
            if (s.isEmpty() || isEssentialHotbar(s)) continue;
            int empty = emptyBackpackIndex(mc);
            if (empty < 0) break;
            if (movePlayerSlot(handler, h, empty, "腾位：杂物进背包")) return h;
        }
        for (int h = 0; h < 9; h++) {
            ItemStack s = mc.player.getInventory().getStack(h);
            if (s.isEmpty() || isEssentialHotbar(s)) continue;
            int junk = replaceableBackpackIndex(mc);
            if (junk < 0) break;
            if (movePlayerSlot(handler, h, junk, "腾位：换走背包杂物")) return h;
        }
        int fwSlots = 0;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) fwSlots++;
        if (fwSlots >= 2) {
            for (int h = 0; h < 9; h++) {
                if (!mc.player.getInventory().getStack(h).isOf(Items.FIREWORK_ROCKET)) continue;
                int empty = emptyBackpackIndex(mc);
                int target = empty >= 0 ? empty : replaceableBackpackIndex(mc);
                if (target < 0) break;
                if (movePlayerSlot(handler, h, target, "腾位：多余的烟花进背包")) return h;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && sb.length() < 90) sb.append(i).append('=').append(s.getName().getString()).append(' ');
        }
        FOElytraLog.warn("快捷栏 9 格全占满且腾不出位置：%s（背包空位 %d 个）", sb, countEmptyBackpack(mc));
        return -1;
    }
    private boolean isEssentialHotbar(ItemStack s) {
        if (s.isEmpty()) return true;
        return isPickaxe(s) || isSword(s) || s.isOf(Items.ENDER_CHEST)
            || s.isOf(Items.TOTEM_OF_UNDYING) || matchesFood(s) || ItemHelper.isShulkerBox(s)
            || s.isOf(Items.FIREWORK_ROCKET);
    }
    private int emptyBackpackIndex(MinecraftClient mc) {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }
    private int countEmptyBackpack(MinecraftClient mc) {
        int n = 0;
        for (int i = 9; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) n++;
        return n;
    }
    private int replaceableBackpackIndex(MinecraftClient mc) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) return i;
            if (isEssentialHotbar(s)) continue;
            if (s.isOf(Items.EXPERIENCE_BOTTLE) && s.getCount() >= s.getMaxCount()) continue;
            if (s.isOf(Items.ELYTRA)) continue;
            return i;
        }
        return -1;
    }
    private boolean movePlayerSlot(ScreenHandler handler, int a, int b, String why) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        int rawA = InvHelper.playerSlotId(handler, mc.player, a);
        int rawB = InvHelper.playerSlotId(handler, mc.player, b);
        if (rawA < 0 || rawB < 0) return false;
        InvHelper.moveStack(handler, rawA, rawB);
        FOElytraLog.tip("%s（快捷栏 %d ↔ 背包 %d；潜影盒马上放进来）", why, a, b);
        FOElytraLog.detail("%s：#%d → #%d（raw %d → %d）", why, a, b, rawA, rawB);
        return true;
    }
    private void next(State next, int wait) {
        if (next != this.state) {
            FOElytraLog.detail("补给状态 %s → %s（等 %d tick）｜需求 %s", this.state, next, Math.max(0, wait), needs);
        }
        this.state = next;
        this.delay = Math.max(0, wait);
        if (next == State.DONE) status = TaskStatus.DONE;
        if (next == State.FAILED) status = TaskStatus.FAILED;
    }
    private void fail(String reason) {
        failReason = reason;
        lastMessage = reason;
        FOElytraLog.err("补给失败：%s", reason);
        releaseKeys();
        BlockBreaker.cancel();
        if (BaritoneHook.isMining()) BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        FOElytraLog.detail("补给失败收尾：Baritone 已停=%s｜界面已关=%s｜状态 %s｜需求 %s",
            !BaritoneHook.isMining(), !InvHelper.hasContainerOpen(), state, needs);
        state = State.FAILED;
        status = TaskStatus.FAILED;
    }
    private void releaseKeys() {
        if (walkPressed) {
            PlayerAction.pressForward(false);
            walkPressed = false;
        }
    }
    private String describeTargets() {
        return "烟花 " + opts.targetFireworkStacks() + " 组 / 经验瓶 " + opts.targetXpBottles()
            + " / 食物 " + opts.targetFoodCount() + " / 图腾 " + opts.targetTotems()
            + " / 备用鞘翅 " + opts.targetElytraCount();
    }
    public static int containerSlots(ScreenHandler handler) {
        if (handler == null) return 27;
        int c = handler.slots.size() - 36;
        return c <= 0 ? 27 : c;
    }
    public static boolean isShulkerBoxBlock(Block block) {
        return block instanceof ShulkerBoxBlock || block == Blocks.SHULKER_BOX;
    }
    public Needs needs() {
        return needs;
    }
}
