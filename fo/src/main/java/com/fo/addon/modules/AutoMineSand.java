package com.fo.addon.modules;

import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.Debug;
import com.fo.addon.utils.FacingLogic;
import com.fo.addon.utils.NukerSphericalLogic;
import com.fo.addon.utils.OpenBoxRetryLogic;
import com.fo.addon.utils.SandPickupLogic;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * FO 自动挖沙
 * - Baritone 模式：调 Baritone mineProcess 自动寻路挖沙（默认）
 * - Nuker 模式：范围极速包挖（独立开关）
 * - FO补给盒：命名潜影盒，自动补铲子/金萝卜/图腾
 * - 存沙：背包满自动存到附近其他潜影盒
 */
public class AutoMineSand extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSupply = settings.createGroup("FO补给盒");
    private final SettingGroup sgStore = settings.createGroup("存沙");

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("搜索半径").description("搜索沙子/潜影盒的半径")
        .defaultValue(32).min(4).max(200).sliderMin(8).sliderMax(64).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("操作延迟").description("状态机 tick 间隔")
        .defaultValue(5).min(0).max(40).sliderMin(0).sliderMax(20).build());

    private final Setting<Boolean> nukerMode = sgGeneral.add(new BoolSetting.Builder()
        .name("核爆挖掘(Nuker)").description("开启后范围极速挖沙，关闭则用 Baritone 寻路挖")
        .defaultValue(false).build());

    private final Setting<Integer> nukerRange = sgGeneral.add(new IntSetting.Builder()
        .name("核爆范围").description("Nuker 模式下最大挖掘半径")
        .defaultValue(4).min(1).max(6).sliderMin(1).sliderMax(6).build());

    private final Setting<Integer> nukerMaxInstaMine = sgGeneral.add(new IntSetting.Builder()
        .name("核爆发包上限").description("每 tick 最多连续发包次数（SlimefunHelper 原版 30，防踢）")
        .defaultValue(30).min(1).max(60).sliderMin(1).sliderMax(60).build());

    private final Setting<Integer> nukerMinDy = sgGeneral.add(new IntSetting.Builder()
        .name("核爆纵向下限").description("Nuker 模式下相对玩家脚下可挖的最低层数")
        .defaultValue(0).min(-6).max(6).sliderMin(-6).sliderMax(6).build());

    private final Setting<Integer> nukerMaxDy = sgGeneral.add(new IntSetting.Builder()
        .name("核爆纵向上限").description("Nuker 模式下相对玩家脚下可挖的最高层数")
        .defaultValue(6).min(-6).max(6).sliderMin(-6).sliderMax(6).build());

    private final Setting<Boolean> nukerRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("核爆自动转头").description("Nuker 模式下自动转头看向目标方块（SlimefunHelper 默认 NO_BYPASS 不转头更防踢）")
        .defaultValue(false).build());

    private final Setting<String> supplyBoxName = sgSupply.add(new StringSetting.Builder()
        .name("补给盒关键词").description("潜影盒命名包含此关键词即识别为 FO 补给盒")
        .defaultValue("FO补给").build());

    private final Setting<Integer> shovelMinDurability = sgSupply.add(new IntSetting.Builder()
        .name("铲子最低耐久").description("耐久低于此值自动换新铲")
        .defaultValue(10).min(1).max(100).sliderMin(1).sliderMax(100).build());

    private final Setting<Integer> foodTarget = sgSupply.add(new IntSetting.Builder()
        .name("金萝卜目标数量").description("从补给盒补到多少")
        .defaultValue(64).min(8).max(64).sliderMin(8).sliderMax(64).build());

    private final Setting<Integer> totemTarget = sgSupply.add(new IntSetting.Builder()
        .name("图腾目标数量").description("从补给盒补到多少个")
        .defaultValue(3).min(1).max(10).sliderMin(1).sliderMax(10).build());

    private final Setting<Boolean> autoStore = sgStore.add(new BoolSetting.Builder()
        .name("自动存沙").description("背包满自动存到附近潜影盒")
        .defaultValue(true).build());

    private final Setting<Integer> storeEmptySlots = sgStore.add(new IntSetting.Builder()
        .name("背包空槽阈值").description("空槽位少于此值触发存沙")
        .defaultValue(2).min(0).max(9).sliderMin(0).sliderMax(9).build());

    private final Setting<SettingColor> supplyBoxColor = sgStore.add(new ColorSetting.Builder()
        .name("补给盒高亮颜色").description("FO补给盒的高亮框颜色")
        .defaultValue(new SettingColor(0, 255, 0, 77)).build());

    private final Setting<SettingColor> storeBoxColor = sgStore.add(new ColorSetting.Builder()
        .name("存沙盒高亮颜色").description("存沙潜影盒的高亮框颜色")
        .defaultValue(new SettingColor(255, 255, 0, 77)).build());

    private final Setting<Boolean> highlightBoxes = sgStore.add(new BoolSetting.Builder()
        .name("高亮潜影盒").description("在世界中高亮补给盒和存沙盒")
        .defaultValue(true).build());

    private enum State { MINING, INIT_SCAN, GOING_SUPPLY, OPEN_SUPPLY, GOING_STORE, OPEN_STORE }

    private State state = State.MINING;
    private int tickTimer = 0;
    private int shulkerWaitTimer = 0;
    private boolean waitingShulkerOpen = false;
    private int openFailCount = 0;         // INIT_SCAN 连续打不开盒子的次数
    private BlockPos storeBoxPos = null;
    private final Set<BlockPos> fullStoreBoxes = new HashSet<>();
    private BlockPos nukerTarget = null;   // Nuker 当前挖掘目标（SlimefunHelper lastMinePos）
    private boolean pickingUp = false;     // Nuker 正在用 Baritone pickup 捡沙子掉落物
    private java.util.List<int[]> nukerOffsets = null; // 球型范围相对坐标（按距离升序，懒生成）
    private double nukerOffsetsRange = -1; // 上次生成偏移表用的半径
    private int storeTickCounter = 0;     // 存沙等服务器同步 tick
    private int storeStuckSlot = -1;       // 上次 shift 点击的沙槽(界面槽位)
    private int storeStuckTicks = 0;      // 卡住检测计数
    private int supplyStuckSlot = -1;      // 补给时上次点的槽位
    private BlockPos supplyBoxPos = null;  // 缓存补给盒位置
    private final java.util.List<BlockPos> initScanBoxes = new java.util.ArrayList<>(); // 待扫描的盒子
    private int initScanIndex = 0;         // 扫描到第几个

    private static final List<Item> SHOVELS = Arrays.asList(
        Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL, Items.IRON_SHOVEL,
        Items.STONE_SHOVEL, Items.WOODEN_SHOVEL
    );

    public AutoMineSand() {
        super(com.fo.addon.AddonTemplate.CATEGORY, "FO 自动挖沙", "自动寻路挖沙 + FO补给盒自动补铲/食物/图腾 + 存沙");
    }

    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            error("Baritone 不可用！");
            toggle();
            return;
        }
        state = State.INIT_SCAN;
        tickTimer = 0;
        shulkerWaitTimer = 0;
        waitingShulkerOpen = false;
        storeBoxPos = null;
        supplyBoxPos = null;
        fullStoreBoxes.clear();
        storeTickCounter = 0;
        storeStuckSlot = -1;
        storeStuckTicks = 0;
        supplyStuckSlot = -1;
        initScanBoxes.clear();
        initScanIndex = 0;
        nukerTarget = null;
        pickingUp = false;
        PathManagers.get().protectShulkerBoxes(true);

        // 收集附近所有潜影盒，准备逐个打开同步名字
        collectNearbyShulkers(initScanBoxes);
        if (initScanBoxes.isEmpty()) {
            error("附近 32 格内没有潜影盒，模块停止");
            toggle();
            return;
        }
        info("FO 自动挖沙启动：正在扫描附近 " + initScanBoxes.size() + " 个潜影盒...");
    }

    @Override
    public void onDeactivate() {
        PathManagers.get().stop();
        PathManagers.get().protectShulkerBoxes(false);
        pickingUp = false;
        nukerTarget = null;
        info("FO 自动挖沙已停止");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isUsingItem()) return;

        // 开着潜影盒时跳过 delay，每 tick 都处理存取
        boolean inShulker = mc.currentScreen instanceof ShulkerBoxScreen;

        if (!inShulker && tickTimer > 0) { tickTimer--; return; }

        switch (state) {
            case MINING -> tickMining();
            case INIT_SCAN -> tickInitScan();
            case GOING_SUPPLY -> tickGoingSupply();
            case OPEN_SUPPLY -> tickOpenSupply();
            case GOING_STORE -> tickGoingStore();
            case OPEN_STORE -> tickOpenStore();
        }

        if (!inShulker) tickTimer = delay.get();
    }

    /** 收集附近所有潜影盒位置 */
    private void collectNearbyShulkers(java.util.List<BlockPos> out) {
        BlockPos c = mc.player.getBlockPos();
        int r = searchRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    if (mc.world.getBlockState(p).getBlock() instanceof ShulkerBoxBlock) {
                        out.add(p.toImmutable());
                    }
                }
    }

    /** INIT_SCAN：逐个打开附近潜影盒同步名字，开完后识别补给盒和存沙盒 */
    private void tickInitScan() {
        // 开着潜影盒 → 等几tick让BlockEntity同步名字，然后关闭
        if (mc.currentScreen instanceof ShulkerBoxScreen) {
            shulkerWaitTimer++;
            if (shulkerWaitTimer >= 10) { // 等10tick让服务器同步潜影盒数据
                closeScreen();
                initScanIndex++;
                shulkerWaitTimer = 0;
                waitingShulkerOpen = true;
                openFailCount = 0;
            }
            return;
        }
        if (waitingShulkerOpen) {
            shulkerWaitTimer++;
            if (shulkerWaitTimer >= 5) { // 关完等5tick再开下一个
                waitingShulkerOpen = false;
                shulkerWaitTimer = 0;
            }
            return;
        }
        // 扫完了所有盒子，开始识别
        if (initScanIndex >= initScanBoxes.size()) {
            finishInitScan();
            return;
        }
        BlockPos box = initScanBoxes.get(initScanIndex);
        switch (OpenBoxRetryLogic.decide(
            mc.player.getBlockPos().isWithinDistance(box, 3),
            openFailCount,
            3)) {
            case TRY_OPEN -> {
                // 打开后等结果（界面开→推进；5 tick 没开→再试），失败 3 次跳过该盒，避免无限转头重试
                openShulker(box);
                shulkerWaitTimer = 0;
                waitingShulkerOpen = true;
                openFailCount++;
            }
            case SKIP_BOX -> {
                info("打不开潜影盒，跳过该盒继续扫描");
                openFailCount = 0;
                initScanIndex++;
            }
            case MOVE_TO -> {
                if (!PathManagers.get().isPathing()) {
                    // 没在寻路才重新指路；用 GoalXZ（ignoreY）避免把实体方块当目标导致寻路绕圈
                    PathManagers.get().moveTo(box, true);
                }
            }
        }
    }

    /** 扫描完所有盒子，识别补给盒和存沙盒 */
    private void finishInitScan() {
        PathManagers.get().stop();
        supplyBoxPos = findNamedShulker(supplyBoxName.get());
        if (supplyBoxPos == null) {
            error("扫描后仍未找到命名「" + supplyBoxName.get() + "」的补给盒，模块停止");
            toggle();
            return;
        }
        info("补给盒: " + supplyBoxPos.toShortString());
        BlockPos store = findOtherShulker();
        if (store == null) {
            error("未找到存沙潜影盒，模块停止");
            toggle();
            return;
        }
        info("FO 自动挖沙已启动");
        state = State.MINING;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!highlightBoxes.get() || mc.player == null || mc.world == null) return;
        BlockPos c = mc.player.getBlockPos();
        int r = searchRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (!(s.getBlock() instanceof ShulkerBoxBlock)) continue;
                    boolean isSupply = false;
                    if (mc.world.getBlockEntity(p) instanceof ShulkerBoxBlockEntity be) {
                        var n = be.getCustomName();
                        if (n != null && n.getString().contains(supplyBoxName.get())) {
                            isSupply = true;
                        }
                    }
                    if (isSupply) {
                        // 补给盒绿色高亮
                        event.renderer.box(p, supplyBoxColor.get(), supplyBoxColor.get(), ShapeMode.Both, 0);
                    } else if (!fullStoreBoxes.contains(p)) {
                        // 存沙盒黄色高亮（满了的不亮）
                        event.renderer.box(p, storeBoxColor.get(), storeBoxColor.get(), ShapeMode.Both, 0);
                    }
                }
    }

    private void tickMining() {
        if (needNewShovel()) { goToSupply("铲子耐久不足"); return; }
        if (foodCount() < 1) { goToSupply("金萝卜不足"); return; }
        if (totemCount() < totemTarget.get()) { goToSupply("图腾不足"); return; }
        if (autoStore.get() && emptySlots() <= storeEmptySlots.get()) { goToStore(); return; }

        if (nukerMode.get()) {
            nukerTick();
        } else {
            PathManagers.get().mine(Blocks.SAND, Blocks.RED_SAND);
        }
    }

    /**
     * Nuker 模式：移植 SlimefunHelper MineBot（核爆挖掘）
     * - SPHERICAL 球型范围找最近沙块（偏移表按距离升序）
     * - 瞬时破坏（一 tick 能挖完，如效率铲挖沙）时每 tick 循环发包，直到服务器确认在挖或达到发包上限
     * - 非瞬时方块只发一次 START，交给原版慢慢挖
     * - 冷却由 onBlockBreakingCooldown 清零（等价 SlimefunHelper setMiningCooldown(0)）
     * - reach 内没沙了：优先用 Baritone pickup 走到沙子掉落物旁拾取，捡完再挖沙
     */
    private void nukerTick() {
        // 拾取优先：正在捡掉落物时，搜索半径内没有沙掉落物才算捡完，然后恢复挖沙
        if (pickingUp) {
            if (hasSandDropsInRadius()) return;
            pickingUp = false;
            PathManagers.get().stop();
        }

        // SlimefunHelper MineBot onMineCommon 挖掘循环
        int tryMine = 0;
        do {
            if (nukerTarget == null || !checkMineCondition(nukerTarget)) {
                nukerTarget = findNextMinePosSpherical();
            }
            if (nukerTarget == null) {
                // reach 内没沙了：有沙掉落物就捡，捡完再挖；没有掉落物直接挖沙
                SandPickupLogic.Action act = SandPickupLogic.decide(hasSandDropsInRadius(), pickingUp);
                switch (act) {
                    case START -> {
                        pickingUp = true;
                        PathManagers.get().pickupItems(AutoMineSand::isSandItem);
                        return;
                    }
                    case KEEP -> { return; }
                    case STOP_AND_MINE -> {
                        pickingUp = false;
                        PathManagers.get().stop();
                    }
                    case MINE -> { }
                }
                // 没有可捡的掉落物，让 Baritone 挖沙（边走边挖）
                PathManagers.get().mine(Blocks.SAND, Blocks.RED_SAND);
                return;
            }

            // 朝向目标方块（SlimefunHelper: Direction.getFacing(shouldFacing).getOpposite()）
            Vec3d shouldFacing = nukerTarget.toCenterPos().subtract(mc.player.getEyePos());
            Direction dir = Direction.getFacing(shouldFacing).getOpposite();

            // 自动转头（可选项，默认关 = SlimefunHelper NO_BYPASS 防踢）
            if (nukerRotate.get()) {
                Vec3d targetCenter = Vec3d.ofCenter(nukerTarget);
                Vec3d eye = mc.player.getEyePos();
                double dx = targetCenter.x - eye.x;
                double dy = targetCenter.y - eye.y;
                double dz = targetCenter.z - eye.z;
                double distXZ = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
            }

            // 发包（冷却已被 onBlockBreakingCooldown 清零，等价 SlimefunHelper setMiningCooldown(0)）
            mc.interactionManager.updateBlockBreakingProgress(nukerTarget, dir);

            // 非瞬时破坏：只发一次 START，交给原版逐步挖
            if (!isInstantBreak(nukerTarget)) break;

            tryMine++;
        } while (!mc.interactionManager.isBreakingBlock() && tryMine < nukerMaxInstaMine.get());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        if (nukerMode.get()) event.cooldown = 0;
    }

    /** SlimefunHelper checkDistanceAndCondition：距离内 + 是沙（跳过 double-break 检查） */
    private boolean checkMineCondition(BlockPos pos) {
        return isWithinReach(pos) && isSand(mc.world.getBlockState(pos).getBlock());
    }

    /** SlimefunHelper findNextMinePosSpherical：球型范围（偏移表按距离升序）找最近合法沙块 */
    private BlockPos findNextMinePosSpherical() {
        int range = nukerRange.get();
        ensureNukerOffsets(range);
        BlockPos center = mc.player.getSteppingPos().add(0, 1, 0);
        int lowest = nukerMinDy.get();
        int highest = nukerMaxDy.get();
        for (int[] v : nukerOffsets) {
            int y = v[1];
            if (y < lowest || y > highest) continue;
            BlockPos p = center.add(v[0], y, v[2]);
            if (checkMineCondition(p)) return p;
        }
        return null;
    }

    /** 懒生成球型范围偏移表（按距离升序，SlimefunHelper getBlocksAround 同款） */
    private void ensureNukerOffsets(int range) {
        if (nukerOffsets != null && nukerOffsetsRange == range) return;
        nukerOffsets = NukerSphericalLogic.offsets(range);
        nukerOffsetsRange = range;
    }

    /** 是否一 tick 内能挖完（瞬时破坏，SlimefunHelper shouldTreatAsInstantBreak: delta >= 1.0） */
    private boolean isInstantBreak(BlockPos pos) {
        return mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos) >= 1.0F;
    }

    /** 检查方块是否在原版 reach 范围内（从眼睛算） */
    private boolean isWithinReach(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.BLOCK_INTERACTION_RANGE);
        return eye.squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
    }

    private boolean isSand(Block b) {
        return b == Blocks.SAND || b == Blocks.RED_SAND;
    }

    /** 掉落物是否沙子物品 */
    private static boolean isSandItem(ItemStack stack) {
        Item i = stack.getItem();
        return i == Items.SAND || i == Items.RED_SAND;
    }

    /** 搜索半径内是否有沙子掉落物 */
    private boolean hasSandDropsInRadius() {
        int r = searchRadius.get();
        Box box = mc.player.getBoundingBox().expand(r);
        return !mc.world.getEntitiesByClass(ItemEntity.class, box, e -> isSandItem(e.getStack())).isEmpty();
    }

    /** 在原版 reach(4.5格) 内找沙块 */
    private BlockPos findReachableSand() {
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.BLOCK_INTERACTION_RANGE);
        BlockPos c = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -4; x <= 4; x++)
            for (int y = -4; y <= 4; y++)
                for (int z = -4; z <= 4; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (!isSand(s.getBlock())) continue;
                    double dist = eye.squaredDistanceTo(Vec3d.ofCenter(p));
                    if (dist <= reach * reach && dist < bestDist) {
                        bestDist = dist;
                        best = p;
                    }
                }
        return best;
    }

    /** 在大范围内找最近沙块（用于 Baritone 走过去） */
    private BlockPos findNearestSand(int radius) {
        BlockPos c = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = c.add(x, y, z);
                    if (isSand(mc.world.getBlockState(p).getBlock())) {
                        double d = c.getSquaredDistance(p);
                        if (d < bestDist) { bestDist = d; best = p; }
                    }
                }
        return best;
    }

    private void goToSupply(String reason) {
        BlockPos supply = supplyBoxPos != null ? supplyBoxPos : findNamedShulker(supplyBoxName.get());
        if (supply == null) {
            error("未找到命名含\"" + supplyBoxName.get() + "\"的补给盒！");
            state = State.MINING;
            return;
        }
        info("前往补给盒: " + reason);
        PathManagers.get().stop();
        state = State.GOING_SUPPLY;
        // GoalXZ（ignoreY）：走到盒子 xz 位置即可，避免把实体方块当目标导致寻路绕圈
        PathManagers.get().moveTo(supply, true);
    }

    private void tickGoingSupply() {
        BlockPos supply = supplyBoxPos != null ? supplyBoxPos : findNamedShulker(supplyBoxName.get());
        if (supply == null) { state = State.MINING; return; }
        if (mc.player.getBlockPos().isWithinDistance(supply, 3)) {
            PathManagers.get().stop();
            state = State.OPEN_SUPPLY;
            waitingShulkerOpen = true;
            shulkerWaitTimer = 0;
            openShulker(supply);
        } else if (!PathManagers.get().isPathing()) {
            // 没在寻路才重新指路；用 GoalXZ（ignoreY）避免把实体方块当目标导致寻路绕圈
            PathManagers.get().moveTo(supply, true);
        }
    }

    private void tickOpenSupply() {
        if (mc.currentScreen instanceof ShulkerBoxScreen) { doSupply(); return; }
        if (waitingShulkerOpen) {
            shulkerWaitTimer++;
            if (shulkerWaitTimer > 40) { waitingShulkerOpen = false; state = State.MINING; }
            return;
        }
        closeScreen();
        state = State.MINING;
    }

    private void doSupply() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        // 等上次点的槽位同步完
        if (supplyStuckSlot >= 0) {
            // supplyStuckSlot >= 27 表示点的是玩家背包槽位（放回旧铲子）
            // < 27 表示点的是补给盒槽位（拿新物资）
            int checkSlot = supplyStuckSlot;
            ItemStack s = handler.getSlot(checkSlot).getStack();
            if (!s.isEmpty()) return;
            supplyStuckSlot = -1;
        }
        int moved = 0;
        // 先把背包里没耐久的铲子放回补给盒
        if (needNewShovel()) {
            int oldShovelScreenSlot = findWornOutShovelInPlayer(handler);
            if (oldShovelScreenSlot != -1) {
                quickMove(oldShovelScreenSlot);
                supplyStuckSlot = oldShovelScreenSlot;
                moved++;
            } else {
                // 旧铲子已清掉，拿新的
                int slot = findShulkerItem(handler, SHOVELS, shovelMinDurability.get());
                if (slot != -1) {
                    quickMove(slot);
                    supplyStuckSlot = slot;
                    moved++;
                } else {
                    // 补给盒里也没有够耐久的铲子
                    error("补给盒里没有够耐久的铲子了，模块停止");
                    closeScreen();
                    toggle();
                    return;
                }
            }
        }
        if (moved > 0) { return; } // 这 tick 只处理铲子
        if (foodCount() < foodTarget.get()) {
            int slot = findShulkerItemExact(handler, Items.GOLDEN_CARROT);
            if (slot != -1) { quickMove(slot); supplyStuckSlot = slot; moved++; }
        }
        if (totemCount() < totemTarget.get()) {
            int slot = findShulkerItemExact(handler, Items.TOTEM_OF_UNDYING);
            if (slot != -1) { quickMove(slot); supplyStuckSlot = slot; moved++; }
        }
        if (moved == 0) { closeScreen(); state = State.MINING; }
    }

    /** 在玩家背包(界面槽位27-62)找没耐久的铲子，返回界面槽位 */
    private int findWornOutShovelInPlayer(ScreenHandler handler) {
        int rows = 3;
        for (int s = rows * 9; s <= rows * 9 + 35; s++) {
            if (s >= handler.slots.size()) break;
            ItemStack stack = handler.getSlot(s).getStack();
            if (!stack.isEmpty() && SHOVELS.contains(stack.getItem())) {
                if (stack.isDamageable() && (stack.getMaxDamage() - stack.getDamage()) <= shovelMinDurability.get()) {
                    return s;
                }
            }
        }
        return -1;
    }

    private void goToStore() {
        BlockPos box = findOtherShulker();
        if (box == null) {
            info("所有存沙潜影盒都已满，模块停止");
            toggle();
            return;
        }
        storeBoxPos = box;
        info("前往存沙潜影盒");
        PathManagers.get().stop();
        state = State.GOING_STORE;
        // GoalXZ（ignoreY）：走到盒子 xz 位置即可，避免把实体方块当目标导致寻路绕圈
        PathManagers.get().moveTo(box, true);
    }

    private void tickGoingStore() {
        if (storeBoxPos == null) { state = State.MINING; return; }
        if (mc.player.getBlockPos().isWithinDistance(storeBoxPos, 3)) {
            PathManagers.get().stop();
            state = State.OPEN_STORE;
            waitingShulkerOpen = true;
            shulkerWaitTimer = 0;
            openShulker(storeBoxPos);
        } else if (!PathManagers.get().isPathing()) {
            // 不在附近且没在寻路才重新指路（被攻击打断后自动续上）；用 GoalXZ（ignoreY）避免把实体方块当目标导致寻路绕圈
            PathManagers.get().moveTo(storeBoxPos, true);
        }
    }

    private void tickOpenStore() {
        if (mc.currentScreen instanceof ShulkerBoxScreen) {
            storeTickCounter++;
            if (storeTickCounter < 3) return; // 等服务器同步潜影盒内容
            doStore();
            return;
        }
        if (waitingShulkerOpen) {
            shulkerWaitTimer++;
            if (shulkerWaitTimer > 40) { waitingShulkerOpen = false; state = State.MINING; }
            return;
        }
        closeScreen();
        state = State.MINING;
    }

    private void doStore() {
        ScreenHandler sh = mc.player.currentScreenHandler;
        if (sh == null) return;
        int rows = 3; // 潜影盒固定 3 行
        int playerFirst = rows * 9;   // 27
        int playerLast = rows * 9 + 35; // 62

        // 卡住检测：上次点的槽位还是沙=盒子满了
        if (storeStuckSlot >= playerFirst && storeStuckSlot <= playerLast) {
            ItemStack s = sh.getSlot(storeStuckSlot).getStack();
            if (!s.isEmpty() && (s.getItem() == Items.SAND || s.getItem() == Items.RED_SAND)) {
                storeStuckTicks++;
                if (storeStuckTicks > 20) {
                    if (storeBoxPos != null) fullStoreBoxes.add(storeBoxPos.toImmutable());
                    info("存沙盒已满，寻找下一个");
                    storeStuckSlot = -1;
                    storeStuckTicks = 0;
                    closeScreen();
                    goToStore();
                }
                return;
            }
            storeStuckSlot = -1;
            storeStuckTicks = 0;
        }
        // 从服务器同步的界面状态找沙，一次只移一组
        for (int s = playerFirst; s <= playerLast; s++) {
            ItemStack stack = sh.getSlot(s).getStack();
            if (!stack.isEmpty() && (stack.getItem() == Items.SAND || stack.getItem() == Items.RED_SAND)) {
                quickMove(s);
                storeStuckSlot = s;
                storeStuckTicks = 0;
                return;
            }
        }
        // 没沙了，存完
        storeStuckSlot = -1;
        storeStuckTicks = 0;
        closeScreen();
        state = State.MINING;
        info("存沙完成");
    }

    private boolean needNewShovel() {
        FindItemResult r = InvUtils.find(itemStack -> SHOVELS.contains(itemStack.getItem()));
        if (!r.found()) return true;
        ItemStack s = mc.player.getInventory().getStack(r.slot());
        if (!s.isDamageable()) return false;
        return (s.getMaxDamage() - s.getDamage()) <= shovelMinDurability.get();
    }

    private int foodCount() {
        int c = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.GOLDEN_CARROT) c += s.getCount();
        }
        return c;
    }

    private int totemCount() {
        int c = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.TOTEM_OF_UNDYING) c += s.getCount();
        }
        return c;
    }

    private int emptySlots() {
        int c = 0;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) c++;
        }
        return c;
    }

    private BlockPos findNamedShulker(String nameContains) {
        BlockPos c = mc.player.getBlockPos();
        int r = searchRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    BlockState s = mc.world.getBlockState(p);
                    if (s.getBlock() instanceof ShulkerBoxBlock &&
                        mc.world.getBlockEntity(p) instanceof ShulkerBoxBlockEntity be) {
                        var n = be.getCustomName();
                        if (n != null && n.getString().contains(nameContains)) return p;
                    }
                }
        return null;
    }

    private BlockPos findOtherShulker() {
        BlockPos c = mc.player.getBlockPos();
        int r = searchRadius.get();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = c.add(x, y, z);
                    if (fullStoreBoxes.contains(p)) continue;
                    BlockState s = mc.world.getBlockState(p);
                    if (s.getBlock() instanceof ShulkerBoxBlock) {
                        if (mc.world.getBlockEntity(p) instanceof ShulkerBoxBlockEntity be) {
                            var n = be.getCustomName();
                            if (n == null || !n.getString().contains(supplyBoxName.get())) return p;
                        } else {
                            return p;
                        }
                    }
                }
        return null;
    }

    private int findShulkerItem(ScreenHandler handler, List<Item> items, int minDurability) {
        for (int i = 0; i < 27; i++) {
            if (i >= handler.slots.size()) break;
            ItemStack s = handler.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            if (items.contains(s.getItem())) {
                if (!s.isDamageable() || (s.getMaxDamage() - s.getDamage()) > minDurability) return i;
            }
        }
        return -1;
    }

    private int findShulkerItemExact(ScreenHandler handler, Item item) {
        for (int i = 0; i < 27; i++) {
            if (i >= handler.slots.size()) break;
            ItemStack s = handler.getSlot(i).getStack();
            if (!s.isEmpty() && s.getItem() == item) return i;
        }
        return -1;
    }

    private void quickMove(int slot) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId, slot, 0,
            SlotActionType.QUICK_MOVE, mc.player);
    }

    private void openShulker(BlockPos pos) {
        if (!mc.player.getBlockPos().isWithinDistance(pos, 5)) return;
        // 先转头对准盒子中心并同步视角包：反作弊要求视线与交互点一致，否则服务端不响应，盒子打不开
        faceBoxForOpen(pos);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    /** 对准潜影盒中心并显式发送视角包（先发视角包，服务器处理交互包时才能看到新视角） */
    private void faceBoxForOpen(BlockPos pos) {
        float[] fp = FacingLogic.yawPitchTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ());
        mc.player.setYaw(fp[0]);
        mc.player.setPitch(fp[1]);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            fp[0], fp[1], mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    private void closeScreen() {
        if (mc.currentScreen instanceof HandledScreen) mc.player.closeHandledScreen();
    }
}
