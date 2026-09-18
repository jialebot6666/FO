package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.Debug;
import com.fo.addon.utils.FakeBlockManager;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 自动挖沙
 *
 * Baritone 寻路 + 目标方块挖掘 + 补给盒(FO补给)自动补给 + 存沙进潜影盒。
 * Nuker 模式通过 FakeBlockManager 的 GrimV3 sequence 补偿实现范围群挖。
 * 兼容 Meteor AutoEat：吃东西时暂停挖掘。
 */
public class AutoMineSand extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSupply = settings.createGroup("补给盒");
    private final SettingGroup sgNuker = settings.createGroup("Nuker 核爆");

    // ===== 通用 =====
    public final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("目标方块")
            .description("要挖的方块（默认沙子/红沙/沙砾）.")
            .defaultValue(List.of(Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL))
            .build()
    );

    public final Setting<Integer> scanRange = sgGeneral.add(new IntSetting.Builder()
            .name("扫描范围")
            .description("周围多少格内扫描目标方块.")
            .defaultValue(8).min(1).sliderRange(1, 32)
            .build()
    );

    public final Setting<Integer> reachDist = sgGeneral.add(new IntSetting.Builder()
            .name("到达距离")
            .description("走到离目标多近开始挖.")
            .defaultValue(3).min(1).sliderRange(1, 6)
            .build()
    );

    // ===== Nuker =====
    public final Setting<Boolean> nukerMode = sgNuker.add(new BoolSetting.Builder()
            .name("Nuker 核爆模式")
            .description("到位置后范围群挖（GrimV3 sequence 补偿）.")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> nukerRadius = sgNuker.add(new IntSetting.Builder()
            .name("核爆半径")
            .description("Nuker 模式下一次挖多大范围.")
            .defaultValue(4).min(1).sliderRange(1, 6)
            .visible(nukerMode::get)
            .build()
    );

    public final Setting<Integer> nukerDelay = sgNuker.add(new IntSetting.Builder()
            .name("核爆间隔 tick")
            .description("每挖一个方块间隔多少 tick（GrimV3 安全）.")
            .defaultValue(2).min(1).sliderRange(1, 10)
            .visible(nukerMode::get)
            .build()
    );

    // ===== 补给盒 =====
    public final Setting<String> supplyBoxName = sgSupply.add(new StringSetting.Builder()
            .name("补给盒名称")
            .description("补给潜影盒的命名关键词（含此名即视为补给盒）.")
            .defaultValue("FO补给")
            .build()
    );

    public final Setting<Integer> shovelDurability = sgSupply.add(new IntSetting.Builder()
            .name("铲子耐久阈值")
            .description("铲子剩余耐久低于此值时去补给盒换新铲.")
            .defaultValue(10).min(0).sliderRange(0, 200)
            .build()
    );

    public final Setting<Integer> foodMin = sgSupply.add(new IntSetting.Builder()
            .name("食物下限")
            .description("背包金萝卜/食物少于此数量时去补给.")
            .defaultValue(1).min(0).sliderRange(0, 64)
            .build()
    );

    public final Setting<Integer> foodTarget = sgSupply.add(new IntSetting.Builder()
            .name("食物补给目标")
            .description("补给后快捷栏/背包里食物达到此数量.")
            .defaultValue(64).min(1).sliderRange(1, 64)
            .build()
    );

    public final Setting<Integer> totemMin = sgSupply.add(new IntSetting.Builder()
            .name("图腾下限")
            .description("图腾少于此数量时去补给.")
            .defaultValue(3).min(0).sliderRange(0, 37)
            .build()
    );

    public final Setting<Integer> supplyRadius = sgSupply.add(new IntSetting.Builder()
            .name("补给盒搜索半径")
            .description("周围多少格内找补给盒.")
            .defaultValue(16).min(2).sliderRange(2, 32)
            .build()
    );

    // ===== 状态 =====
    private enum State {
        IDLE, SCAN, WALK, MINE, SUPPLY, STORE
    }

    private State state = State.IDLE;
    private int stateTick = 0;
    private BlockPos currentTarget = null;
    private BlockPos supplyBoxPos = null;
    private BlockPos storageBoxPos = null;
    private int nukerTick = 0;
    private int shulkerInteractionTick = 0;
    private boolean waitingForShulkerOpen = false;

    public AutoMineSand() {
        super(AddonTemplate.CATEGORY, "FO 自动挖沙", "Baritone 寻路挖沙 + 补给盒自动补给 + 存沙进潜影盒.");
    }

    @Override
    public void onActivate() {
        state = State.SCAN;
        stateTick = 0;
        currentTarget = null;
        supplyBoxPos = null;
        storageBoxPos = null;
        nukerTick = 0;
        waitingForShulkerOpen = false;
        Debug.chat("[FO 自动挖沙] 已启动");
    }

    @Override
    public void onDeactivate() {
        PathManagers.get().stop();
        if (mc.currentScreen != null && mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen) {
            mc.player.closeHandledScreen();
        }
        state = State.IDLE;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        stateTick++;

        // AutoEat 正在吃 → 暂停挖沙
        if (mc.player.isUsingItem()) {
            PathManagers.get().stop();
            return;
        }

        // 有 GUI 打开 → 等待 GUI 逻辑处理，不挖
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen) {
            handleShulkerScreen();
            return;
        }

        // 补给检查
        if (needSupply()) {
            goSupply();
            return;
        }

        // 背包满了要存沙
        if (isInventoryFull()) {
            goStore();
            return;
        }

        switch (state) {
            case SCAN -> {
                currentTarget = findNearestTarget();
                if (currentTarget != null) {
                    state = State.WALK;
                    stateTick = 0;
                } else {
                    // 没找到，扩大扫描或等
                    PathManagers.get().stop();
                }
            }
            case WALK -> {
                if (currentTarget == null) { state = State.SCAN; return; }
                double dist = mc.player.getBlockPos().getSquaredDistance(currentTarget);
                if (dist <= reachDist.get() * reachDist.get()) {
                    PathManagers.get().stop();
                    state = State.MINE;
                    stateTick = 0;
                } else {
                    if (stateTick < 6 || !PathManagers.get().isPathing()) {
                        PathManagers.get().moveTo(currentTarget, false);
                    }
                }
            }
            case MINE -> {
                if (currentTarget == null || !isTargetBlock(currentTarget)) {
                    state = State.SCAN;
                    return;
                }
                mineTarget(currentTarget);
                if (!nukerMode.get()) {
                    // 单挖模式：挖完这个，找下一个
                    state = State.SCAN;
                }
            }
            case SUPPLY -> handleSupply();
            case STORE -> handleStore();
            default -> {}
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerActionResponseS2CPacket ack) {
            FakeBlockManager.onAck(ack.sequence());
        }
    }

    // ===== 挖目标 =====
    private void mineTarget(BlockPos pos) {
        if (nukerMode.get()) {
            // Nuker: 扫半径内所有目标方块，逐个发补偿包
            nukerTick++;
            if (nukerTick < nukerDelay.get()) return;
            nukerTick = 0;
            List<BlockPos> near = findTargetsAround(pos, nukerRadius.get());
            if (near.isEmpty()) {
                state = State.SCAN;
                return;
            }
            BlockPos target = near.get(0);
            Direction dir = Direction.getFacing(mc.player.getEyePos().subtract(Vec3d.ofCenter(target)));
            FakeBlockManager.addFakeCompensate(target);
            mc.interactionManager.attackBlock(target, dir);
            mc.interactionManager.updateBlockBreakingProgress(target, dir);
        } else {
            // 单挖
            Direction dir = Direction.getFacing(mc.player.getEyePos().subtract(Vec3d.ofCenter(pos)));
            mc.interactionManager.attackBlock(pos, dir);
            mc.interactionManager.updateBlockBreakingProgress(pos, dir);
        }
    }

    // ===== 扫描目标 =====
    private BlockPos findNearestTarget() {
        BlockPos center = mc.player.getBlockPos();
        int r = scanRange.get();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++)
            for (int y = -4; y <= 4; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.add(x, y, z);
                    if (isTargetBlock(p)) {
                        double d = center.getSquaredDistance(p);
                        if (d < bestDist) { bestDist = d; best = p; }
                    }
                }
        return best;
    }

    private List<BlockPos> findTargetsAround(BlockPos center, int radius) {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = center.add(x, y, z);
                    if (isTargetBlock(p)) list.add(p);
                }
        return list;
    }

    private boolean isTargetBlock(BlockPos p) {
        BlockState s = mc.world.getBlockState(p);
        return targetBlocks.get().contains(s.getBlock());
    }

    private boolean isInventoryFull() {
        int empty = 0;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) empty++;
        }
        return empty <= 1;
    }

    // ===== 补给盒 =====
    private boolean needSupply() {
        // 铲子耐久
        ItemStack main = mc.player.getMainHandStack();
        if (main.isDamageable() && main.getMaxDamage() - main.getDamage() <= shovelDurability.get()) return true;
        // 食物
        int food = countInInventory(Items.GOLDEN_CARROT);
        if (food < foodMin.get()) return true;
        // 图腾
        int totems = countInInventory(Items.TOTEM_OF_UNDYING) +
                (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING ? 1 : 0);
        return totems < totemMin.get();
    }

    private void goSupply() {
        if (supplyBoxPos == null || !isSupplyBox(supplyBoxPos)) {
            supplyBoxPos = findNamedShulker(supplyBoxName.get());
        }
        if (supplyBoxPos == null) {
            Debug.chat("[FO 自动挖沙] 找不到补给盒（命名含 " + supplyBoxName.get() + "）");
            return;
        }
        state = State.SUPPLY;
        stateTick = 0;
    }

    private void handleSupply() {
        if (supplyBoxPos == null) { state = State.SCAN; return; }
        double dist = mc.player.getBlockPos().getSquaredDistance(supplyBoxPos);
        if (dist > 4) {
            if (stateTick < 6 || !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(supplyBoxPos, false);
            }
            return;
        }
        // 到了，打开补给盒
        PathManagers.get().stop();
        if (!waitingForShulkerOpen) {
            Direction face = Direction.getFacing(mc.player.getEyePos().subtract(Vec3d.ofCenter(supplyBoxPos)));
            mc.interactionManager.interactBlock(mc.player, mc.player.getActiveHand(),
                    new BlockHitResult(Vec3d.ofCenter(supplyBoxPos), face, supplyBoxPos, false));
            waitingForShulkerOpen = true;
            shulkerInteractionTick = 0;
        }
        // GUI 打开后由 onTick 顶部 handleShulkerScreen 处理
    }

    // ===== 存沙 =====
    private void goStore() {
        if (storageBoxPos == null || !isOtherShulker(storageBoxPos)) {
            storageBoxPos = findOtherShulker();
        }
        if (storageBoxPos == null) {
            Debug.chat("[FO 自动挖沙] 附近没有可存沙的潜影盒");
            state = State.SCAN;
            return;
        }
        state = State.STORE;
        stateTick = 0;
    }

    private void handleStore() {
        if (storageBoxPos == null) { state = State.SCAN; return; }
        double dist = mc.player.getBlockPos().getSquaredDistance(storageBoxPos);
        if (dist > 4) {
            if (stateTick < 6 || !PathManagers.get().isPathing()) {
                PathManagers.get().moveTo(storageBoxPos, false);
            }
            return;
        }
        PathManagers.get().stop();
        if (!waitingForShulkerOpen) {
            Direction face = Direction.getFacing(mc.player.getEyePos().subtract(Vec3d.ofCenter(storageBoxPos)));
            mc.interactionManager.interactBlock(mc.player, mc.player.getActiveHand(),
                    new BlockHitResult(Vec3d.ofCenter(storageBoxPos), face, storageBoxPos, false));
            waitingForShulkerOpen = true;
            shulkerInteractionTick = 0;
        }
    }

    // ===== 潜影盒 GUI 处理 =====
    private void handleShulkerScreen() {
        shulkerInteractionTick++;
        ScreenHandler sh = mc.player.currentScreenHandler;
        if (sh == null) { waitingForShulkerOpen = false; return; }

        if (state == State.SUPPLY) {
            // 从补给盒拿：铲子、食物（进快捷栏）、图腾
            boolean didSomething = takeFromSupply(sh);
            if (!didSomething || shulkerInteractionTick > 40) {
                mc.player.closeHandledScreen();
                waitingForShulkerOpen = false;
                state = State.SCAN;
                stateTick = 0;
            }
        } else if (state == State.STORE) {
            // 把背包里的沙子 shift 进潜影盒
            boolean didSomething = storeSand(sh);
            if (!didSomething || shulkerInteractionTick > 40) {
                mc.player.closeHandledScreen();
                waitingForShulkerOpen = false;
                storageBoxPos = null;
                state = State.SCAN;
                stateTick = 0;
            }
        }
    }

    /** 从补给盒拿需要的补给品 */
    private boolean takeFromSupply(ScreenHandler sh) {
        // 找补给盒里的铲子/金萝卜/图腾
        for (int boxSlot = 0; boxSlot < 27; boxSlot++) {
            ItemStack s = sh.getSlot(boxSlot).getStack();
            if (s.isEmpty()) continue;
            Item item = s.getItem();

            // 铲子耐久不够 → 拿新铲
            ItemStack main = mc.player.getMainHandStack();
            if (item instanceof net.minecraft.item.ShovelItem &&
                (main.isEmpty() || main.getMaxDamage() - main.getDamage() <= shovelDurability.get())) {
                mc.interactionManager.clickSlot(sh.syncId, boxSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
            // 金萝卜不够 → 补到快捷栏
            if (item == Items.GOLDEN_CARROT) {
                int have = countInInventory(Items.GOLDEN_CARROT);
                if (have < foodTarget.get()) {
                    mc.interactionManager.clickSlot(sh.syncId, boxSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }
            // 图腾不够 → 补
            if (item == Items.TOTEM_OF_UNDYING) {
                int have = countInInventory(Items.TOTEM_OF_UNDYING) +
                        (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING ? 1 : 0);
                if (have < totemMin.get()) {
                    mc.interactionManager.clickSlot(sh.syncId, boxSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }
        }
        return false;
    }

    /** 把背包里的沙子 shift 进潜影盒 */
    private boolean storeSand(ScreenHandler sh) {
        // 玩家背包槽位在潜影盒 GUI 里是 27-62
        for (int slot = 27; slot < sh.slots.size(); slot++) {
            ItemStack s = sh.getSlot(slot).getStack();
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            if (item == Items.SAND || item == Items.RED_SAND || item == Items.GRAVEL) {
                mc.interactionManager.clickSlot(sh.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    // ===== 工具 =====
    private int countInInventory(Item item) {
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    private boolean isSupplyBox(BlockPos pos) {
        BlockState s = mc.world.getBlockState(pos);
        if (!(s.getBlock() instanceof ShulkerBoxBlock)) return false;
        var be = mc.world.getBlockEntity(pos);
        if (be instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity shulker) {
            var name = shulker.getCustomName();
            return name != null && name.getString().contains(supplyBoxName.get());
        }
        return false;
    }

    private boolean isOtherShulker(BlockPos pos) {
        BlockState s = mc.world.getBlockState(pos);
        return s.getBlock() instanceof ShulkerBoxBlock;
    }

    private BlockPos findNamedShulker(String nameContains) {
        BlockPos c = mc.player.getBlockPos();
        int r = supplyRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (s.getBlock() instanceof ShulkerBoxBlock && mc.world.getBlockEntity(p) instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity be) {
                        var n = be.getCustomName();
                        if (n != null && n.getString().contains(nameContains)) return p;
                    }
                }
        return null;
    }

    private BlockPos findOtherShulker() {
        BlockPos c = mc.player.getBlockPos();
        int r = supplyRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (s.getBlock() instanceof ShulkerBoxBlock && !(mc.world.getBlockEntity(p) instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity be && be.getCustomName() != null && be.getCustomName().getString().contains(supplyBoxName.get()))) {
                        return p;
                    }
                }
        return null;
    }
}
