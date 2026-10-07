package com.fo.addon.elytra.core;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
public final class MendTask {
    public record Options(
        int triggerDurability,
        int minDurability,
        int minBottles,
        int repairToDamage,
        boolean requireGround,
        boolean requireNetherWastes,
        boolean requireMending,
        double lookPitch,
        int throwDelay,
        int maxThrows,
        int landingTimeoutTicks
    ) {
    }
    public enum State {
        IDLE("空闲"),
        ENSURE_GROUND("先落地"),
        PREPARE("准备"),
        THROW("丢经验瓶"),
        RESTORE("恢复"),
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
    private static final int MIN_THROW_GAP = 2;
    private static final int VERIFY_TICKS = 20;
    private static final int RELAX_AFTER = 3;
    private static final int RELAX_MIN_WINDOW = 4;
    private static final int ORB_GRACE_TICKS = 40;
    private static final int INEFFECTIVE_LIMIT = 5;
    private static final int ORB_NO_REPAIR_LIMIT = 2;
    private static final int AIR_WAIT_MAX = 100;
    private static final int BATCH_THROWS = 10;
    private static final double ORB_RADIUS = 6.0;
    private final Options opts;
    private State state = State.IDLE;
    private TaskStatus status = TaskStatus.IDLE;
    private int delay;
    private int waitTicks;
    private int throwsDone;
    private String failReason = "";
    private String lastMessage = "";
    private int xpSlot = -1;
    private int previousSlot = -1;
    private float previousPitch;
    private boolean landingRequested;
    private int verifyElapsed = -1;
    private int preThrowDamage;
    private int preThrowXp;
    private boolean serverEffectSeen;
    private int ineffectiveStreak;
    private int effectiveStreak;
    private int orbNoRepairRounds;
    private int batchStartRemaining;
    private int mendStartRemaining;
    private boolean intervalWarned;
    private int airWaitTicks;
    private boolean airWarned;
    public MendTask(Options opts) {
        this.opts = opts;
    }
    public void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            fail("玩家/世界为空");
            return;
        }
        ItemStack elytra = ItemHelper.wornElytra(mc.player);
        if (elytra.isEmpty()) {
            fail("没有穿鞘翅");
            return;
        }
        int remainingNow = ItemHelper.remainingDurability(elytra);
        if (remainingNow > opts.triggerDurability()) {
            fail("鞘翅剩余耐久 " + remainingNow + " 还高于触发阈值 " + opts.triggerDurability() + "，不需要修复");
            return;
        }
        if (opts.requireMending() && !ItemHelper.isMending(elytra, 1)) {
            fail("鞘翅没有「经验修补」附魔，扔经验瓶修不了耐久");
            return;
        }
        if (opts.requireNetherWastes() && !isNetherWastes(mc)) {
            fail("当前不在下界荒地生物群系（设置要求在这里修）");
            return;
        }
        int bottles = ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE);
        if (bottles < Math.max(1, opts.minBottles())) {
            fail("经验瓶不足（需要 " + Math.max(1, opts.minBottles()) + " 个，现有 " + bottles + " 个）");
            return;
        }
        state = State.ENSURE_GROUND;
        status = TaskStatus.RUNNING;
        delay = 0;
        waitTicks = 0;
        throwsDone = 0;
        failReason = "";
        landingRequested = false;
        previousSlot = mc.player.getInventory().getSelectedSlot();
        previousPitch = mc.player.getPitch();
        verifyElapsed = -1;
        serverEffectSeen = false;
        ineffectiveStreak = 0;
        effectiveStreak = 0;
        orbNoRepairRounds = 0;
        intervalWarned = false;
        airWaitTicks = 0;
        airWarned = false;
        mendStartRemaining = remainingNow;
        batchStartRemaining = remainingNow;
        BlockBreaker.reset();
        FOElytraLog.info("开始修复鞘翅（剩余耐久 %d，瓶子 %d 个）",
            ItemHelper.remainingDurability(elytra), bottles);
        FOElytraLog.detail("修复参数：触发阈值 %d｜低耐久警告线 %d｜修到损伤 ≤ %d｜落地 %s｜生物群系限制 %s"
                + "｜经验修补必需 %s｜俯仰 %.0f°｜间隔 %d tick（实际下限 %d）｜最多 %d 瓶｜落地超时 %d tick",
            opts.triggerDurability(), opts.minDurability(), opts.repairToDamage(),
            opts.requireGround() ? "是" : "否", opts.requireNetherWastes() ? "下界荒地" : "不限",
            opts.requireMending() ? "是" : "否", opts.lookPitch(), opts.throwDelay(), MIN_THROW_GAP,
            opts.maxThrows(), opts.landingTimeoutTicks());
        int remainingStart = ItemHelper.remainingDurability(elytra);
        if (remainingStart <= opts.minDurability()) {
            FOElytraLog.warn("鞘翅剩余耐久只剩 %d（警告线 %d）：修的时候别断线，"
                + "并且把「补给数量 → 目标备用鞘翅」设成 1~2 组，随时能换", remainingStart, opts.minDurability());
        }
        if (opts.throwDelay() < MIN_THROW_GAP) {
            intervalWarned = true;
            FOElytraLog.warn("投掷间隔 %d tick 太快，已按 %d 用", opts.throwDelay(), MIN_THROW_GAP);
        }
    }
    public void abort(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (state != State.IDLE) FOElytraLog.warn("修鞘翅中止：%s", reason);
        if (mc.player != null && state != State.IDLE) {
            mc.player.setPitch(previousPitch);
            if (previousSlot >= 0) mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        if (com.fo.addon.elytra.core.InvHelper.hasContainerOpen()) com.fo.addon.elytra.core.InvHelper.closeScreen();
        state = State.IDLE;
        status = TaskStatus.IDLE;
        delay = 0;
        verifyElapsed = -1;
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
    public String lastMessage() {
        return lastMessage;
    }
    public String progress() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return state.toString();
        return state.toString() + " 耐久 " + ItemHelper.remainingDurability(ItemHelper.wornElytra(mc.player));
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
            FOElytraLog.err("修鞘翅内部异常: %s", String.valueOf(t));
            fail("内部异常 " + t.getClass().getSimpleName());
        }
    }
    private void step(MinecraftClient mc) {
        switch (state) {
            case ENSURE_GROUND -> ensureGround(mc);
            case PREPARE -> prepare(mc);
            case THROW -> throwBottles(mc);
            case RESTORE -> restore(mc);
            case DONE -> status = TaskStatus.DONE;
            case FAILED -> status = TaskStatus.FAILED;
            default -> status = TaskStatus.DONE;
        }
    }
    private void ensureGround(MinecraftClient mc) {
        if (mc.player.isOnGround() && !mc.player.isGliding()) {
            next(State.PREPARE, 2);
            return;
        }
        if (!opts.requireGround()) {
            next(State.PREPARE, 1);
            return;
        }
        if (!landingRequested) {
            landingRequested = true;
            if (BaritoneHook.available()) {
                BaritoneHook.pathTo(mc.player.getBlockX(), mc.player.getBlockZ());
                lastMessage = "让 Baritone 降落中";
            } else if (mc.player.isGliding()) {
                fail("需要落地修理，但没有装 Baritone 无法自动降落");
                return;
            }
        }
        if (mc.player.isOnGround() && !mc.player.isGliding()) {
            BaritoneHook.stop();
            next(State.PREPARE, 2);
            return;
        }
        if (waitTicks++ > opts.landingTimeoutTicks()) {
            BaritoneHook.stop();
            fail("降落超时（" + opts.landingTimeoutTicks() + " tick），放弃修理");
            return;
        }
        delay = 1;
    }
    private void prepare(MinecraftClient mc) {
        if (!ensureXpInHotbar(mc)) {
            fail("快捷栏腾不出位置放经验瓶");
            return;
        }
        int bottles = ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE);
        if (bottles <= 0) {
            fail("经验瓶已用完");
            return;
        }
        throwsDone = 0;
        verifyElapsed = -1;
        ineffectiveStreak = 0;
        effectiveStreak = 0;
        orbNoRepairRounds = 0;
        airWaitTicks = 0;
        batchStartRemaining = ItemHelper.remainingDurability(ItemHelper.wornElytra(mc.player));
        next(State.THROW, 2);
    }
    private boolean ensureXpInHotbar(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) {
                xpSlot = i;
                return true;
            }
        }
        int source = InvHelper.findSlot(s -> s.isOf(Items.EXPERIENCE_BOTTLE), 9, 36);
        if (source < 0) return false;
        int target = InvHelper.findEmptyHotbarSlot();
        if (target < 0) {
            int least = -1;
            int leastCount = Integer.MAX_VALUE;
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isOf(Items.FIREWORK_ROCKET) && s.getCount() < leastCount) {
                    leastCount = s.getCount();
                    least = i;
                }
            }
            target = least;
        }
        if (target < 0) return false;
        InvHelper.moveInvToHotbar(source, target);
        xpSlot = target;
        return true;
    }
    private void throwBottles(MinecraftClient mc) {
        ItemStack elytra = ItemHelper.wornElytra(mc.player);
        if (elytra.isEmpty()) {
            fail("鞘翅不见了");
            return;
        }
        if (verifyElapsed >= 0) {
            verifyThrow(mc, elytra);
            return;
        }
        int damage = elytra.getDamage();
        int remaining = ItemHelper.remainingDurability(elytra);
        if (damage <= opts.repairToDamage()) {
            lastMessage = "修复完成，剩余耐久 " + remaining + "（" + account(remaining) + "）";
            FOElytraLog.tip("鞘翅修复完成：剩余耐久 %d（%s）", remaining, account(remaining));
            next(State.RESTORE, 1);
            return;
        }
        if (ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE) <= 0) {
            lastMessage = "经验瓶用完，剩余耐久 " + remaining + "（" + account(remaining) + "）";
            FOElytraLog.warn("经验瓶用完，鞘翅剩余耐久 %d（损伤 %d）｜%s", remaining, damage, account(remaining));
            next(State.RESTORE, 1);
            return;
        }
        if (throwsDone >= opts.maxThrows()) {
            lastMessage = "扔瓶次数达到上限，剩余耐久 " + remaining + "（" + account(remaining) + "）";
            FOElytraLog.warn("扔瓶次数达到上限 %d，停止修复（剩余耐久 %d）｜%s",
                opts.maxThrows(), remaining, account(remaining));
            next(State.RESTORE, 1);
            return;
        }
        if (mc.player.isGliding() || !mc.player.isOnGround()) {
            if (!airWarned) {
                airWarned = true;
                FOElytraLog.warn("离地/滑翔时不投掷：瓶子在脚边破，经验球会落在身后捡不到，先落地再修");
            }
            if (++airWaitTicks > AIR_WAIT_MAX) {
                fail("离地/滑翔中无法投掷（经验球会落在身后），已等 " + AIR_WAIT_MAX + " tick：请先落地再修鞘翅");
                return;
            }
            delay = 2;
            return;
        }
        airWaitTicks = 0;
        airWarned = false;
        if (mc.player.getInventory().getStack(xpSlot).isEmpty()) {
            if (!ensureXpInHotbar(mc)) {
                next(State.RESTORE, 1);
                return;
            }
        }
        mc.player.setPitch((float) opts.lookPitch());
        mc.player.getInventory().setSelectedSlot(xpSlot);
        preThrowDamage = damage;
        preThrowXp = mc.player.totalExperience;
        serverEffectSeen = false;
        InvHelper.useItem(Hand.MAIN_HAND);
        throwsDone++;
        verifyElapsed = 0;
        delay = 0;
        FOElytraLog.detail("扔出第 %d 个经验瓶（剩余耐久 %d，损伤 %d，槽位 %d）",
            throwsDone, remaining, damage, xpSlot);
        if (throwsDone % BATCH_THROWS == 0) {
            FOElytraLog.info("已扔 %d 发：耐久 %d → %d（这 %d 发修了 %d）",
                throwsDone, batchStartRemaining, remaining, BATCH_THROWS,
                batchStartRemaining - remaining);
            batchStartRemaining = remaining;
        }
    }
    private void verifyThrow(MinecraftClient mc, ItemStack elytra) {
        verifyElapsed++;
        int damage = elytra.getDamage();
        if (damage < preThrowDamage) {
            ineffectiveStreak = 0;
            effectiveStreak++;
            orbNoRepairRounds = 0;
            verifyElapsed = -1;
            delay = clampedGap();
            return;
        }
        if (!serverEffectSeen && (nearbyOrb(mc) || mc.player.totalExperience > preThrowXp)) {
            serverEffectSeen = true;
        }
        int window = serverEffectSeen ? ORB_GRACE_TICKS
            : (effectiveStreak >= RELAX_AFTER ? Math.max(RELAX_MIN_WINDOW, clampedGap()) : VERIFY_TICKS);
        if (verifyElapsed < window) {
            delay = 0;
            return;
        }
        verifyElapsed = -1;
        delay = clampedGap();
        if (serverEffectSeen) {
            orbNoRepairRounds++;
            effectiveStreak = 0;
            FOElytraLog.warn("经验球被吸走但没修到鞘翅（可能修到别的经验修补物品了）：第 %d 轮", orbNoRepairRounds);
            if (orbNoRepairRounds >= ORB_NO_REPAIR_LIMIT) {
                fail("经验球出现了但鞘翅没修到（可能修到别的经验修补物品），连续 " + orbNoRepairRounds
                    + " 轮，已停止修复（" + account(ItemHelper.remainingDurability(elytra)) + "）");
            }
            return;
        }
        ineffectiveStreak++;
        effectiveStreak = 0;
        FOElytraLog.warn("第 %d 发没有任何服务端效果（连续 %d 次没动静）", throwsDone, ineffectiveStreak);
        if (ineffectiveStreak >= INEFFECTIVE_LIMIT) {
            fail("服务端没有接受投掷（客户端扣了瓶子但服务端没执行）。请把「投掷间隔 tick」调大，或重进游戏恢复数量（"
                + account(ItemHelper.remainingDurability(elytra)) + "）");
        }
    }
    private int clampedGap() {
        int gap = Math.max(MIN_THROW_GAP, opts.throwDelay());
        if (opts.throwDelay() < MIN_THROW_GAP && !intervalWarned) {
            intervalWarned = true;
            FOElytraLog.warn("投掷间隔 %d tick 太快，已按 %d 用", opts.throwDelay(), MIN_THROW_GAP);
        }
        return gap;
    }
    private boolean nearbyOrb(MinecraftClient mc) {
        try {
            return !mc.world.getEntitiesByClass(ExperienceOrbEntity.class,
                mc.player.getBoundingBox().expand(ORB_RADIUS), e -> true).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }
    private String account(int remainingNow) {
        return "共扔 " + throwsDone + " 发，耐久 " + mendStartRemaining + " → " + remainingNow
            + "（修了 " + (mendStartRemaining - remainingNow) + "）";
    }
    private void restore(MinecraftClient mc) {
        mc.player.setPitch(previousPitch);
        if (previousSlot >= 0) mc.player.getInventory().setSelectedSlot(previousSlot);
        next(State.DONE, 0);
    }
    private boolean isNetherWastes(MinecraftClient mc) {
        try {
            return mc.world.getBiomeAccess()
                .getBiome(mc.player.getBlockPos())
                .getKey()
                .map(key -> "minecraft:nether_wastes".equals(key.getValue().toString()))
                .orElse(false);
        } catch (Throwable t) {
            return true;
        }
    }
    private void next(State next, int wait) {
        state = next;
        delay = Math.max(0, wait);
        if (next == State.DONE) status = TaskStatus.DONE;
        if (next == State.FAILED) status = TaskStatus.FAILED;
    }
    private void fail(String reason) {
        failReason = reason;
        lastMessage = reason;
        FOElytraLog.err("修鞘翅失败：%s", reason);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && state != State.IDLE) {
            mc.player.setPitch(previousPitch);
            if (previousSlot >= 0) mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        verifyElapsed = -1;
        state = State.FAILED;
        status = TaskStatus.FAILED;
    }
    public static boolean shouldRepair(int threshold) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        ItemStack elytra = ItemHelper.wornElytra(mc.player);
        if (elytra.isEmpty()) return false;
        int remaining = ItemHelper.remainingDurability(elytra);
        return remaining >= 0 && remaining <= threshold;
    }
}
