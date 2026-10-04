package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.FacingLogic;
import com.fo.addon.utils.InteractionUtils;
import com.fo.addon.utils.MiningGuard;
import com.fo.addon.utils.CraftingSlotMath;
import com.fo.addon.utils.PickupNextLogic;
import com.fo.addon.utils.PickupTimeoutLogic;
import com.fo.addon.utils.StartupCheckLogic;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoMining — FO 自动挖矿（V4.30）。
 *
 * <p>移植自 misaka AutoMiningModule 自动挖矿状态机（23 态），适配 Meteor Client 1.21.11：
 * 钻石模式（主世界挖钻石矿，背包满后安全位置放工作台合成钻石块存入潜影盒）与
 * 残骸模式（下界挖远古残骸直接存潜影盒，镐耐久低时挖石英合成石英块修镐）。
 * Baritone 全部走 FO pathing 纯反射桥接；自动扔垃圾联动开启（白名单模式 + 确保含石英）。
 *
 * <p>模块名带 "FO " 前缀防重名冲突，前端全中文。
 */
public class AutoMining extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** 挖矿模式 */
    public enum MiningMode {
        DIAMOND("钻石（主世界）"),
        ANCIENT_DEBRIS("远古残骸（下界）");

        private final String label;

        MiningMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** 23 态状态机（对齐 misaka x0278） */
    public enum MiningState {
        IDLE,                    // 空闲（短暂等待后回到挖掘）
        MINING,                  // 挖掘目标矿石
        REPAIRING_PICKAXE,       // 挖石英修复镐子
        INVENTORY_FULL,          // 背包满，寻找安全放置位置
        FINDING_SAFE_SPOT,       // 重新寻找安全位置
        PATHING_TO_SAFE_SPOT,    // 寻路前往安全位置
        CLEARING_AREA,           // 挖掘 3x3x3 空间
        PLACING_CRAFTING_TABLE,  // 放置工作台（钻石模式）
        CRAFTING,                // 打开工作台合成钻石块
        PLACING_SHULKER,         // 放置潜影盒
        STORING_ITEMS,           // 往潜影盒存储目标物品
        STORING_IN_ENDER_CHEST,  // 末影箱界面：存满盒 / 取空盒
        MINING_SHULKER,          // 挖掉已装满的潜影盒
        MINING_CRAFTING_TABLE,   // 挖掉工作台
        MINING_ENDER_CHEST,      // 挖掉末影箱
        PLACING_ENDER_CHEST,     // 放置末影箱
        PICKING_UP_ITEM,         // 拾取掉落物（潜影盒/工作台/末影箱）
        WAITING,                 // 等待 N tick 后回上一状态
        PLACE_ROTATE_WAIT,       // 放置旋转等待（简化：直接放置）
        PLACE_SNAPBACK_WAIT,     // 放置回弹等待（简化：直接恢复）
        OPEN_ROTATE_WAIT,        // 打开容器旋转等待（简化：直接交互）
        OPEN_SNAPBACK_WAIT,      // 打开回弹等待（简化：直接恢复）
        ESCAPING_DEEP_DARK       // 逃离深暗之域（主世界）
    }

    /** 拾取目标类型 */
    public enum PickupTarget {
        SHULKER_BOX,
        CRAFTING_TABLE,
        ENDER_CHEST
    }

    private final Setting<MiningMode> miningMode = sgGeneral.add(new EnumSetting.Builder<MiningMode>()
        .name("挖矿模式")
        .description("钻石（主世界）或远古残骸（下界）.")
        .defaultValue(MiningMode.DIAMOND)
        .build());

    private final Setting<Boolean> autoTrash = sgGeneral.add(new BoolSetting.Builder()
        .name("自动扔垃圾")
        .description("联动开启自动扔垃圾（白名单模式，确保石英在保留列表；石英块会主动丢弃）.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoKillAura = sgGeneral.add(new BoolSetting.Builder()
        .name("联动杀戮光环")
        .description("开启自动挖矿时同步开启 FO杀戮光环，关闭时同步关闭（成对联动）")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> debugOutput = sgGeneral.add(new BoolSetting.Builder()
        .name("调试输出")
        .description("在聊天栏输出详细状态信息.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> avoidMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("怪物避让")
        .description("设置 Baritone 怪物避让（探测半径/系数/刷怪笼避让）.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> avoidBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("方块避让")
        .description("设置 Baritone 避让深暗之域方块与刷怪笼，防止挖到监守者相关方块.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> avoidDeepDark = sgGeneral.add(new BoolSetting.Builder()
        .name("深暗之域避让")
        .description("主世界钻石模式下，检测到身处深暗之域（minecraft:deep_dark）时自动逃离到安全位置.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> pickDroppedItems = sgGeneral.add(new BoolSetting.Builder()
        .name("捡取挖掘掉落物")
        .description("开启后让 Baritone 在挖矿找不到新矿时把地面掉落物当目标捡起，兜住挖了没捡到的钻石/残骸.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> pickaxeThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("镐子耐久阈值")
        .description("镐子剩余耐久低于该值时，残骸模式自动挖石英修复（仅残骸模式生效）.")
        .defaultValue(200)
        .min(50)
        .sliderMin(50)
        .sliderMax(500)
        .build());

    private final Setting<Boolean> placeRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("放置旋转")
        .description("放置/打开容器前旋转视角对准目标，避免服务器视线回溯拒绝交互.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> craftWaitDelay = sgGeneral.add(new IntSetting.Builder()
        .name("合成等待延时")
        .description("合成相关点击之间的等待刻数，避免点击过快被服务器忽略.")
        .defaultValue(2)
        .min(0)
        .sliderMin(0)
        .sliderMax(20)
        .build());

    // ===== 状态机 =====
    private MiningState state = MiningState.IDLE;
    private MiningState previousState = MiningState.IDLE;
    private int stateWaitTicks = 0;
    private int tickCount = 0;
    private int lastPickaxeCheck = 0;
    private int lastToolCheck = 0;             // V4.31：锁时运镐节流计数
    private int miningStartedTick = 0;
    private int lastDeepDarkCheck = 0;
    private int pathTimeoutTicks = 0;
    private int pickupTimeoutTicks = 0;
    private int craftActionTicks = 0;
    private int placeActionTicks = 0;
    private int lastTrashTick = -100;

    // ===== 目标位置 =====
    private BlockPos safeSpot = null;          // 安全放置位置（3x3x3 清理中心）
    private BlockPos placeTarget = null;       // 待放置方块位置
    private BlockPos placedPos = null;         // 已放置方块（工作台/潜影盒/末影箱）
    private BlockPos minePos = null;           // 正在手动挖掘的方块
    private BlockPos escapeTarget = null;      // 深暗之域逃离目标
    private BlockPos openTarget = null;        // 待打开的容器

    private int repairPickaxeSlot = -1;        // 需要修复的镐子背包槽
    private PickupTarget pickupTargetType = null;
    private int pickupBaseCount = 0;           // 进入拾取时背包中目标物品数量（计数+1即拾取完成，对齐 misaka）
    private int pickupPathTicks = 0;           // 当前拾取寻路持续 tick（保留字段，V4.47 起不再用于门控）
    private boolean storingToEnder = false;    // 正在走末影箱取盒流程
    private boolean pendingMiningTable = false; // 钻石模式：挖完潜影盒拾取后还需挖工作台
    private BlockPos pendingTablePos = null;    // 钻石模式：合成用的工作台位置（存盒链完成后挖掉）
    private boolean disconnectFlag = false;
    private String disconnectMsg = null;

    // ===== 3x3x3 清理 =====
    private final List<BlockPos> clearBlocks = new ArrayList<>();
    private int clearIndex = 0;

    public AutoMining() {
        super(AddonTemplate.CATEGORY, "FO 自动挖矿", "自动挖矿：钻石/残骸模式全自动挖掘、合成、存储、精准回收末影箱、深暗之域逃离。");
    }

    // ================= 生命周期 =================

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // 世界维度检查
        boolean isNether = mc.world.getRegistryKey() == World.NETHER;
        if (miningMode.get() == MiningMode.DIAMOND && isNether) {
            error("钻石模式仅限主世界使用！");
            toggle();
            return;
        }
        if (miningMode.get() == MiningMode.ANCIENT_DEBRIS && !isNether) {
            error("残骸模式仅限下界使用！");
            toggle();
            return;
        }

        // 必需品检查：时运镐 + 空潜影盒（或末影箱取盒兜底）+ 工作台（钻石模式）+ 末影箱
        String missing = StartupCheckLogic.missingItem(
            findFortunePickaxeSlot() != -1,
            findEmptyShulkerSlot() != -1,
            findSlot(Items.ENDER_CHEST) != -1,
            miningMode.get() == MiningMode.DIAMOND,
            findSlot(Items.CRAFTING_TABLE) != -1
        );
        if (missing != null) {
            error("背包缺少必要物品！需要：" + missing);
            toggle();
            return;
        }

        // Baritone 避让设置 + 关闭自动换工具（V4.31 方案B：FO 自己锁时运镐，防 Baritone 选精准采集镐挖钻石掉原矿）
        PathManagers.get().applyMiningAvoidance(avoidMobs.get(), avoidBlocks.get());
        PathManagers.get().setAutoTool(false);
        // V4.42：挖矿找不到新矿时把掉落物当目标捡起（兜住挖了没捡到钻石）
        PathManagers.get().setMineScanDroppedItems(pickDroppedItems.get());

        // 自动扔垃圾联动
        if (autoTrash.get()) {
            AutoTrash trash = Modules.get().get(AutoTrash.class);
            if (trash != null) trash.enableForMineLink();
        }

        // FO杀戮光环联动（成对：开→开）
        if (autoKillAura.get()) {
            FOKillAura ka = Modules.get().get(FOKillAura.class);
            if (ka != null && !ka.isActive()) ka.toggle();
        }

        // 初始化状态
        state = MiningState.MINING;
        tickCount = 0;
        miningStartedTick = 0;
        lastPickaxeCheck = 0;
        lastToolCheck = 0;
        lastDeepDarkCheck = 0;
        escapeTarget = null;
        disconnectFlag = false;
        startMining();
        info("已启动（" + miningMode.get() + "）");
    }

    @Override
    public void onDeactivate() {
        // 恢复 Baritone 设置 + 关闭联动 + 停止寻路/挖掘
        PathManagers.get().resetMiningAvoidance();
        if (autoTrash.get()) {
            AutoTrash trash = Modules.get().get(AutoTrash.class);
            if (trash != null) trash.disableForMineLink();
        }
        // FO杀戮光环联动（成对：关→关）
        if (autoKillAura.get()) {
            FOKillAura ka = Modules.get().get(FOKillAura.class);
            if (ka != null && ka.isActive()) ka.toggle();
        }
        PathManagers.get().stop();
        state = MiningState.IDLE;
        previousState = MiningState.IDLE;
        safeSpot = null;
        placeTarget = null;
        placedPos = null;
        escapeTarget = null;
        clearBlocks.clear();
        clearIndex = 0;
        repairPickaxeSlot = -1;
        pickupTargetType = null;
        storingToEnder = false;
        pendingMiningTable = false;
        pendingTablePos = null;
        pickupPathTicks = 0;
        pickupBaseCount = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        tickCount++;

        // 深暗之域检测（钻石模式 + 避让开启；每 100 tick 检测一次）
        if (miningMode.get() == MiningMode.DIAMOND && avoidDeepDark.get()
            && state != MiningState.ESCAPING_DEEP_DARK
            && tickCount - lastDeepDarkCheck >= 100) {
            lastDeepDarkCheck = tickCount;
            if (isDeepDark(mc.player.getBlockPos())) {
                PathManagers.get().stop();
                info("检测到深暗之域，开始逃离");
                escapeTarget = null;
                state = MiningState.ESCAPING_DEEP_DARK;
                return;
            }
        }

        // 断开连接兜底
        if (disconnectFlag) {
            disconnect(disconnectMsg != null ? disconnectMsg : "自动挖矿异常，自动断开连接");
            disconnectFlag = false;
        }

        // 状态机分发
        switch (state) {
            case IDLE -> idle();
            case MINING -> mining();
            case REPAIRING_PICKAXE -> repairingPickaxe();
            case INVENTORY_FULL -> inventoryFull();
            case FINDING_SAFE_SPOT -> findingSafeSpot();
            case PATHING_TO_SAFE_SPOT -> pathingToSafeSpot();
            case CLEARING_AREA -> clearingArea();
            case PLACING_CRAFTING_TABLE -> placingCraftingTable();
            case CRAFTING -> crafting();
            case PLACING_SHULKER -> placingShulker();
            case STORING_ITEMS -> storingItems();
            case STORING_IN_ENDER_CHEST -> storingInEnderChest();
            case MINING_SHULKER -> miningShulker();
            case MINING_CRAFTING_TABLE -> miningCraftingTable();
            case MINING_ENDER_CHEST -> miningEnderChest();
            case PLACING_ENDER_CHEST -> placingEnderChest();
            case PICKING_UP_ITEM -> pickingUpItem();
            case WAITING -> waiting();
            case PLACE_ROTATE_WAIT, PLACE_SNAPBACK_WAIT, OPEN_ROTATE_WAIT, OPEN_SNAPBACK_WAIT -> rotateWaitFallthrough();
            case ESCAPING_DEEP_DARK -> escapingDeepDark();
        }
    }

    // ================= 状态处理 =================

    /** IDLE：短暂等待后回到挖掘 */
    private void idle() {
        if (tickCount % 2 == 0) {
            state = MiningState.MINING;
            startMining();
        }
    }

    /** MINING：挖掘循环（每 100 tick 重启挖掘；背包满/镐耐久低时切换） */
    private void mining() {
        // 背包满 → 停挖 → 找安全位置
        if (isInventoryFull()) {
            PathManagers.get().stop();
            info("背包已满，寻找安全位置存储");
            state = MiningState.INVENTORY_FULL;
            return;
        }

        // V4.31 方案B：每 20 tick 确认手持时运镐（防精准采集镐被选中挖钻石 → 掉原矿）
        if (tickCount - lastToolCheck >= 20) {
            lastToolCheck = tickCount;
            ensureFortunePickaxeHeld();
        }

        // 残骸模式：每 20 tick 检查镐耐久
        if (miningMode.get() == MiningMode.ANCIENT_DEBRIS && tickCount - lastPickaxeCheck >= 20) {
            lastPickaxeCheck = tickCount;
            int slot = findLowDurabilityPickaxe();
            if (slot != -1) {
                repairPickaxeSlot = slot;
                PathManagers.get().stop();
                info("镐子耐久不足，开始挖掘石英修复");
                state = MiningState.REPAIRING_PICKAXE;
                return;
            }
        }

        // 每 100 tick 重启挖掘（misaka 行为：周期性刷新挖掘目标，防 Baritone 发呆）
        if (tickCount - miningStartedTick >= 100) {
            miningStartedTick = tickCount;
            startMining();
        }
    }

    /** REPAIRING_PICKAXE：挖石英 + 随身 2x2 合成石英块丢弃 + 修复判定 */
    private void repairingPickaxe() {
        if (repairPickaxeSlot == -1 || mc.player.getInventory().getStack(repairPickaxeSlot).isEmpty()) {
            repairPickaxeSlot = -1;
            state = MiningState.MINING;
            return;
        }

        // V4.31 方案B：挖石英也锁时运镐（石英吃时运，掉更多石英）
        if (tickCount - lastToolCheck >= 20) {
            lastToolCheck = tickCount;
            ensureFortunePickaxeHeld();
        }

        // 没在挖石英 → 启动挖掘
        if (!PathManagers.get().isMining()) {
            PathManagers.get().mine(Blocks.NETHER_QUARTZ_ORE);
            info("开始挖掘石英矿修复镐子");
        }

        // 石英合成（静默 2x2，不需要打开界面）
        craftQuartzBlock();

        // 镐子已修复 → 回挖掘
        if (isPickaxeRepaired()) {
            PathManagers.get().stop();
            info("镐子已完全修复，继续挖掘残骸");
            repairPickaxeSlot = -1;
            state = MiningState.MINING;
        }
    }

    /** INVENTORY_FULL：找安全放置位置（玩家周围 5x5x5 全实体 → 就地 3x3x3；否则 32 格范围） */
    private void inventoryFull() {
        BlockPos playerPos = mc.player.getBlockPos();

        // misaka x0027：背包满 → 找安全位置；找到后 2 格内就地清理 3x3x3，否则寻路过去
        BlockPos spot = findSafeSpot();
        if (spot == null) {
            error("找不到安全的位置放置方块");
            toggle();
            return;
        }
        safeSpot = spot;
        debug("找到安全位置: " + spot.toShortString());
        double dist = playerPos.getSquaredDistance(spot);
        state = dist < 4 ? MiningState.CLEARING_AREA : MiningState.PATHING_TO_SAFE_SPOT;
    }

    /** FINDING_SAFE_SPOT：重新找安全位置（misaka x0027 同款：找到后按距离分流） */
    private void findingSafeSpot() {
        if (safeSpot == null) {
            BlockPos spot = findSafeSpot();
            if (spot != null) {
                safeSpot = spot;
                info("找到安全位置: " + spot.toShortString());
            } else {
                info("找不到安全的位置放置方块，等待后重试");
                waitTicks(MiningState.FINDING_SAFE_SPOT, 40);
                return;
            }
        }
        double dist = mc.player.getBlockPos().getSquaredDistance(safeSpot);
        if (dist < 4) {
            state = MiningState.CLEARING_AREA;
        } else {
            state = MiningState.PATHING_TO_SAFE_SPOT;
        }
    }

    /** PATHING_TO_SAFE_SPOT：寻路前往安全位置（600 tick 超时重新找） */
    private void pathingToSafeSpot() {
        if (safeSpot == null) {
            state = MiningState.FINDING_SAFE_SPOT;
            return;
        }

        if (!PathManagers.get().isPathing()) {
            PathManagers.get().moveTo(safeSpot, false);
            debug("正在前往安全位置: " + safeSpot.toShortString());
            pathTimeoutTicks = 0;
        } else {
            pathTimeoutTicks++;
            if (pathTimeoutTicks > 600) {
                PathManagers.get().stop();
                info("寻路超时，重新寻找安全位置");
                safeSpot = null;
                state = MiningState.FINDING_SAFE_SPOT;
                return;
            }
        }

        if (mc.player.getBlockPos().getSquaredDistance(safeSpot) < 4) {
            PathManagers.get().stop();
            state = MiningState.CLEARING_AREA;
        }
    }

    /** CLEARING_AREA：挖出 3x3x3 空间（safeSpot 为中心） */
    private void clearingArea() {
        if (safeSpot == null) {
            state = MiningState.FINDING_SAFE_SPOT;
            return;
        }

        // 扫描 3x3x3 非空气方块
        if (clearBlocks.isEmpty()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = safeSpot.add(dx, dy, dz);
                        if (!mc.world.getBlockState(p).isAir()) {
                            clearBlocks.add(p);
                        }
                    }
                }
            }
            clearIndex = 0;
            debug("需要清理 " + clearBlocks.size() + " 个方块（3x3x3空间）");
        }

        if (clearIndex < clearBlocks.size()) {
            BlockPos target = clearBlocks.get(clearIndex);
            debug("正在挖掘: " + target.toShortString() + " (" + (clearIndex + 1) + "/" + clearBlocks.size() + ")");
            if (mineBlock(target)) {
                clearIndex++;
                debug("挖掘完成，进度: " + clearIndex + "/" + clearBlocks.size());
            }
            return;
        }

        // 清理完成 → 钻石模式放工作台，残骸模式直接放潜影盒
        clearBlocks.clear();
        clearIndex = 0;
        info("3x3x3空间清理完成，准备放置方块");
        if (miningMode.get() == MiningMode.DIAMOND) {
            if (findSlot(Items.CRAFTING_TABLE) != -1) {
                info("进入放置工作台状态");
                state = MiningState.PLACING_CRAFTING_TABLE;
            } else {
                error("背包中没有工作台！");
                toggle();
            }
        } else {
            info("进入放置潜影盒状态");
            state = MiningState.PLACING_SHULKER;
        }
    }

    /** PLACING_CRAFTING_TABLE：找位置放工作台 → CRAFTING */
    private void placingCraftingTable() {
        if (safeSpot == null) {
            state = MiningState.FINDING_SAFE_SPOT;
            return;
        }
        int slot = findSlot(Items.CRAFTING_TABLE);
        if (slot == -1) {
            error("背包中没有工作台！");
            toggle();
            return;
        }
        BlockPos target = findPlacePosition(safeSpot);
        if (target == null) {
            error("找不到合适的放置位置！");
            toggle();
            return;
        }
        placeTarget = target;
        placeBlockAt(target, slot);
        placedPos = target;
        waitTicks(MiningState.CRAFTING, 10);
    }

    /** CRAFTING：打开工作台 → 钻石合成 → 放潜影盒存钻石块（对齐 misaka：CRAFTING→PLACING_SHULKER） */
    private void crafting() {
        // 打开工作台（未打开时交互）
        if (!(mc.currentScreen instanceof CraftingScreen)) {
            openContainer(placedPos);
            return;
        }

        if (craftDiamondBlock()) {
            mc.player.closeHandledScreen();
            // 记录工作台位置（后续存盒链会覆盖 placedPos），存完盒后回来挖掉
            pendingTablePos = placedPos;
            info("合成阶段结束，先放潜影盒存储");
            state = MiningState.PLACING_SHULKER;
        }
    }

    /** PLACING_SHULKER：找空潜影盒放盒 → STORING_ITEMS；无空盒 → 末影箱流程 */
    private void placingShulker() {
        if (safeSpot == null) {
            state = MiningState.FINDING_SAFE_SPOT;
            return;
        }
        int slot = findEmptyShulkerSlot();
        if (slot == -1) {
            // 背包无空潜影盒 → 末影箱取盒流程
            if (!storingToEnder) {
                storingToEnder = true;
                info("背包没有空潜影盒，从末影箱获取");
                state = MiningState.PLACING_ENDER_CHEST;
                return;
            }
            // 末影箱流程已走完仍无盒 → 断开
            disconnectFlag = true;
            disconnectMsg = "§c没有可用的空潜影盒，自动断开连接";
            toggle();
            return;
        }
        BlockPos target = findPlacePosition(safeSpot);
        if (target == null) {
            error("找不到合适的放置位置！");
            toggle();
            return;
        }
        placeTarget = target;
        placeBlockAt(target, slot);
        placedPos = target;
        storingToEnder = false;
        waitTicks(MiningState.STORING_ITEMS, 10);
    }

    /** STORING_ITEMS：打开潜影盒，把背包目标物品 QUICK_MOVE 进盒；盒满 → 挖盒 */
    private void storingItems() {
        if (placedPos == null) {
            state = MiningState.MINING;
            return;
        }

        // 打开潜影盒
        if (!(mc.currentScreen instanceof ShulkerBoxScreen)) {
            openContainer(placedPos);
            return;
        }

        ShulkerBoxScreenHandler h = (ShulkerBoxScreenHandler) mc.player.currentScreenHandler;
        Item target = miningMode.get() == MiningMode.DIAMOND ? Items.DIAMOND_BLOCK : Items.ANCIENT_DEBRIS;

        // 盒已满 → 关界面 → 挖盒
        if (isShulkerFull(h)) {
            mc.player.closeHandledScreen();
            info("潜影盒已满，挖掉潜影盒");
            minePos = placedPos;
            placedPos = null;
            state = MiningState.MINING_SHULKER;
            return;
        }

        // 找背包目标物品并移入盒内（一次一组）
        int invSlot = findInvSlot(target);
        if (invSlot == -1) {
            // 背包没有可存储的目标物品 → 存完，挖盒
            mc.player.closeHandledScreen();
            info("背包中没有可存储的" + target.getName().getString() + "，挖掉潜影盒");
            minePos = placedPos;
            placedPos = null;
            state = MiningState.MINING_SHULKER;
            return;
        }

        // 背包槽 → 潜影盒界面屏幕槽（ShulkerBox: 主背包 27-53，热键 54-62）
        int screenSlot = invSlot < 9 ? invSlot + 54 : invSlot + 18;
        mc.interactionManager.clickSlot(h.syncId, screenSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
        waitTicks(MiningState.STORING_ITEMS, 5);
    }

    /** STORING_IN_ENDER_CHEST：末影箱界面——存满盒入箱 / 取空盒 / 无可换则断开 */
    private void storingInEnderChest() {
        if (placedPos == null) {
            storingToEnder = false;
            state = MiningState.MINING;
            return;
        }

        // 打开末影箱
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen)) {
            openContainer(placedPos);
            return;
        }

        GenericContainerScreenHandler h = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
        Item target = miningMode.get() == MiningMode.DIAMOND ? Items.DIAMOND_BLOCK : Items.ANCIENT_DEBRIS;

        // A. 背包有"装满目标物品的潜影盒" → 放入末影箱空槽
        int fullBoxSlot = findFullTargetBoxSlot(target);
        if (fullBoxSlot != -1) {
            int emptyEcSlot = findEmptyContainerSlot(h, 0, 26);
            if (emptyEcSlot != -1) {
                // 背包槽 → 末影箱界面屏幕槽（主背包 27-53，热键 54-62）
                int screenSlot = fullBoxSlot < 9 ? fullBoxSlot + 54 : fullBoxSlot + 18;
                mc.interactionManager.clickSlot(h.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(h.syncId, emptyEcSlot, 0, SlotActionType.PICKUP, mc.player);
                debug("已将装满" + target.getName().getString() + "的潜影盒放入末影箱槽位 " + emptyEcSlot);
                waitTicks(MiningState.STORING_IN_ENDER_CHEST, 4);
                return;
            }
            // 末影箱满：找末影箱中的空潜影盒交换
            int emptyBoxInEc = findEmptyShulkerInContainer(h);
            if (emptyBoxInEc != -1) {
                int screenSlot = fullBoxSlot < 9 ? fullBoxSlot + 54 : fullBoxSlot + 18;
                mc.interactionManager.clickSlot(h.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(h.syncId, emptyBoxInEc, 0, SlotActionType.PICKUP, mc.player);
                debug("末影箱已满，与槽位 " + emptyBoxInEc + " 的空潜影盒交换");
                waitTicks(MiningState.STORING_IN_ENDER_CHEST, 4);
                return;
            }
            // 末影箱满且无可交换 → 断开
            disconnectFlag = true;
            disconnectMsg = "§c末影箱已满且没有可交换的潜影盒，自动断开连接";
            toggle();
            return;
        }

        // B. 取出末影箱中的空潜影盒到背包
        int emptyBoxInEc = findEmptyShulkerInContainer(h);
        if (emptyBoxInEc != -1) {
            mc.interactionManager.clickSlot(h.syncId, emptyBoxInEc, 0, SlotActionType.QUICK_MOVE, mc.player);
            debug("已从末影箱取出空潜影盒，继续放置");
            // V4.40：取盒完成 → 关界面 → 挖掉末影箱回收（对齐 misaka 顺序：取空盒后接 MINING_ENDER_CHEST）。
            // 修复原逻辑取到空盒后仍在 STORING_IN_ENDER_CHEST 循环取盒、把空盒取光才断开的死循环。
            // 下一轮 PLACING_SHULKER 放盒时用刚取到的空盒（中间隔了挖/拾取末影箱，服务端回包已同步）。
            mc.player.closeHandledScreen();
            storingToEnder = false;
            state = MiningState.MINING_ENDER_CHEST;
            return;
        }

        // C. 末影箱里也没有空盒 → 断开
        disconnectFlag = true;
        disconnectMsg = "§c末影箱中没有可用的空潜影盒，自动断开连接";
        toggle();
    }

    /** MINING_SHULKER：挖掉放置的潜影盒 → 拾取 */
    private void miningShulker() {
        if (minePos == null) {
            state = MiningState.MINING;
            return;
        }
        if (mineBlock(minePos)) {
            info("潜影盒已挖掉，准备拾取");
            minePos = null;
            // 钻石模式：存完盒后还需挖掉工作台（对齐 misaka：MINING_SHULKER→MINING_CRAFTING_TABLE）
            if (miningMode.get() == MiningMode.DIAMOND && pendingTablePos != null) {
                pendingMiningTable = true;
            }
            startPickup(PickupTarget.SHULKER_BOX);
            state = MiningState.PICKING_UP_ITEM;
        }
    }

    /** MINING_CRAFTING_TABLE：挖掉工作台 → 拾取 */
    private void miningCraftingTable() {
        if (placedPos == null) {
            state = MiningState.MINING;
            return;
        }
        if (mineBlock(placedPos)) {
            info("工作台已挖掉，准备拾取");
            placedPos = null;
            startPickup(PickupTarget.CRAFTING_TABLE);
            state = MiningState.PICKING_UP_ITEM;
        }
    }

    /** MINING_ENDER_CHEST：挖掉末影箱 → 拾取 */
    private void miningEnderChest() {
        if (placedPos == null) {
            storingToEnder = false;
            state = MiningState.MINING;
            return;
        }
        if (mineBlockWithSilkTouch(placedPos)) {
            info("末影箱已挖掉，准备拾取");
            // 挖完立刻换回时运镐（V4.31 方案B：防下一轮挖矿用错工具）
            ensureFortunePickaxeHeld();
            placedPos = null;
            // V4.47 bug1：对齐 misaka x0180 语义——末影箱链挖完同样挂"待挖工作台"标志，
            // 拾取完成/超时后都先挖工作台，不再遗忘（原逻辑只有挖潜影盒时挂标志）
            if (miningMode.get() == MiningMode.DIAMOND && pendingTablePos != null) {
                pendingMiningTable = true;
            }
            startPickup(PickupTarget.ENDER_CHEST);
            state = MiningState.PICKING_UP_ITEM;
        }
    }

    /** PLACING_ENDER_CHEST：找位置放末影箱 → STORING_IN_ENDER_CHEST */
    private void placingEnderChest() {
        if (safeSpot == null) {
            storingToEnder = false;
            state = MiningState.FINDING_SAFE_SPOT;
            return;
        }
        int slot = findSlot(Items.ENDER_CHEST);
        if (slot == -1) {
            error("背包中没有末影箱！");
            toggle();
            return;
        }
        BlockPos target = findPlacePosition(safeSpot);
        if (target == null) {
            error("找不到合适的放置位置！");
            toggle();
            return;
        }
        placeTarget = target;
        placeBlockAt(target, slot);
        placedPos = target;
        waitTicks(MiningState.STORING_IN_ENDER_CHEST, 10);
    }

    /** 进入拾取：记录目标类型、清超时/寻路计数、记录背包目标物品基数（计数+1即拾取完成，对齐 misaka） */
    private void startPickup(PickupTarget target) {
        pickupTargetType = target;
        pickupTimeoutTicks = 0;
        pickupPathTicks = 0;
        pickupBaseCount = countTargetInInventory(target);
    }

    /** 背包中目标物品总数量（含堆栈合并后的数量） */
    private int countTargetInInventory(PickupTarget target) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (pickupItemMatches(s.getItem(), target)) count += s.getCount();
        }
        return count;
    }

    /** 物品是否匹配拾取目标 */
    private boolean pickupItemMatches(Item item, PickupTarget target) {
        return switch (target) {
            case SHULKER_BOX -> isShulkerBoxItem(item);
            case CRAFTING_TABLE -> item == Items.CRAFTING_TABLE;
            case ENDER_CHEST -> item == Items.ENDER_CHEST;
        };
    }

    /** PICKING_UP_ITEM：寻路拾取目标掉落物（10 格内检测，200 tick 超时；潜影盒超时断开，其余回挖矿重试） */
    private void pickingUpItem() {
        if (pickupTargetType == null) {
            state = MiningState.MINING;
            return;
        }

        // ① 完成判定：背包目标物品计数 > 进入拾取时基数（misaka 语义，不受掉落物实体干扰）
        if (countTargetInInventory(pickupTargetType) > pickupBaseCount) {
            PickupTarget done = pickupTargetType; // 记录刚拾取的类型（completePickup 内会清空）
            completePickup(done);
            return;
        }

        // ② V4.50 bug修复：改用 Baritone pickup 持续拾取（对齐挖沙 AutoMineSand 成功经验）。
        //    原 moveTo(GoalGetToBlock) 只走到目标相邻格就停 → 玩家踩不到掉落物 → 拾取卡死"只发一次寻路"。
        //    pickup 无距离限制（不受 findPickupDrop 10 格限制），捡完自动结束；重复调用 Baritone 内部复用不刷网络。
        PathManagers.get().pickupItems(stack -> pickupItemMatches(stack.getItem(), pickupTargetType));

        // ③ 超时兜底：掉落物实体不可见/已被别人捡走且背包计数未变 → 原超时逻辑
        BlockPos drop = findPickupDrop(pickupTargetType);
        if (drop == null) {
            // 掉落物尚未生成/不可见：超时计数（pickup 仍每 tick 发起，不因 drop 缺失而停止）
            pickupTimeoutTicks++;
            boolean wasShulker = pickupTargetType == PickupTarget.SHULKER_BOX;
            switch (PickupTimeoutLogic.decide(wasShulker, pickupTimeoutTicks)) {
                case WAIT -> {
                    return;
                }
                case RETRY -> {
                    // V4.47 bug2a：对齐 misaka——拾取超时后，钻石模式还有待挖工作台 → 先挖工作台再回挖矿；
                    // 原逻辑直接清空 pendingTablePos 导致工作台被永久遗忘
                    info("拾取超时，重新开始挖掘");
                    PathManagers.get().stop();
                    pickupTargetType = null;
                    if (miningMode.get() == MiningMode.DIAMOND && pendingTablePos != null) {
                        placedPos = pendingTablePos;
                        pendingTablePos = null;
                        pendingMiningTable = false;
                        info("拾取超时，先挖掉工作台");
                        state = MiningState.MINING_CRAFTING_TABLE;
                    } else {
                        pendingMiningTable = false;
                        pendingTablePos = null;
                        state = MiningState.MINING;
                    }
                    return;
                }
                case DISCONNECT -> {
                    // ④ 潜影盒是核心资产：超时直接断开连接防丢（misaka 语义）
                    info("拾取潜影盒超时，自动断开连接");
                    PathManagers.get().stop();
                    pickupTargetType = null;
                    pendingMiningTable = false;
                    pendingTablePos = null;
                    disconnect("§c拾取潜影盒超时，自动断开连接");
                    return;
                }
            }
            return;
        }

        // 有掉落物实体在 → 重置超时计数（pickup 进程持续追踪中）
        pickupTimeoutTicks = 0;
    }

    /** 拾取完成公共收尾：V4.49 对齐 misaka case 0/1/2——满盒先换空盒（工作台延后），末影箱换完再挖工作台 */
    private void completePickup(PickupTarget done) {
        PathManagers.get().stop();
        info("拾取完成");
        pickupTargetType = null;
        pickupPathTicks = 0;
        pickupBaseCount = 0;

        // 决策表（对齐 misaka x0275）：拾取类型 → 满盒/待挖工作台 → 下一步
        switch (PickupNextLogic.decide(PickupNextLogic.PickupKind.valueOf(done.name()),
            hasFullShulkerInInventory(), pendingMiningTable)) {
            case PLACE_ENDER_CHEST -> {
                // 拾取完潜影盒、背包仍有满盒 → 立即放末影箱换空盒（工作台留到换盒链完成后）
                // 注意：不清 pendingTablePos/pendingMiningTable，换完盒后继续挖工作台
                info("背包有满盒，先放置末影箱换空盒");
                state = MiningState.PLACING_ENDER_CHEST;
            }
            case MINE_CRAFTING_TABLE -> {
                // 末影箱换盒链完成 / 无满盒 → 接着挖工作台（对齐 misaka x0180 语义）
                pendingMiningTable = false;
                placedPos = pendingTablePos;
                pendingTablePos = null;
                info("拾取完成，挖掉工作台");
                state = MiningState.MINING_CRAFTING_TABLE;
            }
            case MINING -> {
                pendingMiningTable = false;
                pendingTablePos = null;
                state = MiningState.MINING;
            }
        }
    }

    /** WAITING：倒计时后回上一状态 */
    private void waiting() {
        if (stateWaitTicks > 0) {
            stateWaitTicks--;
            return;
        }
        state = previousState;
    }

    /** rotate 等待状态：FO 用 InteractionUtils 同步旋转+交互，直接转上一状态（保持枚举对齐 misaka） */
    private void rotateWaitFallthrough() {
        state = previousState != MiningState.IDLE ? previousState : MiningState.MINING;
    }

    /** ESCAPING_DEEP_DARK：128 格范围找 y>=0 最近安全点逃离 */
    private void escapingDeepDark() {
        if (escapeTarget == null) {
            escapeTarget = findSafeEscapePoint();
            if (escapeTarget == null) {
                error("无法计算逃离方向，请手动离开深暗之域");
                state = MiningState.MINING;
                return;
            }
            PathManagers.get().moveTo(escapeTarget, false);
            info("正在逃离深暗之域: " + escapeTarget.toShortString());
            return;
        }

        // 已到达安全点（非深暗 biome）→ 继续挖掘
        if (!isDeepDark(mc.player.getBlockPos()) && mc.player.getBlockPos().getSquaredDistance(escapeTarget) < 64) {
            PathManagers.get().stop();
            info("已逃离深暗之域，继续挖掘");
            escapeTarget = null;
            state = MiningState.MINING;
        }
    }

    // ================= 挖掘 / 放置 / 打开 =================

    /** 启动 Baritone 挖掘（按模式） */
    private void startMining() {
        switch (miningMode.get()) {
            case DIAMOND -> PathManagers.get().mine(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
            case ANCIENT_DEBRIS -> PathManagers.get().mine(Blocks.ANCIENT_DEBRIS);
        }
    }

    /**
     * 手动挖一个方块（移植 misaka x0044）：距离 > 5 格先寻路；
     * 在范围内自动换最佳工具并发包挖掘，方块变空气即完成。
     */
    private boolean mineBlock(BlockPos pos) {
        return mineBlockWithTool(pos, findBestToolSlot(pos));
    }

    /** 挖末影箱专用：优先精准采集镐（回收末影箱本体），没有则回退普通挖（消耗式） */
    private boolean mineBlockWithSilkTouch(BlockPos pos) {
        boolean hasSilk = findSilkTouchPickaxeSlot() != -1;
        ToolStrategy s = pickaxeStrategy(false, true, hasSilk);
        int toolSlot = (s == ToolStrategy.SILK_TOUCH) ? findSilkTouchPickaxeSlot() : findBestToolSlot(pos);
        return mineBlockWithTool(pos, toolSlot);
    }

    /** 挖掘共用流程：工具切换（-1 表示保持当前槽）→ 朝向 → 发包挖掘 */
    private boolean mineBlockWithTool(BlockPos pos, int toolSlot) {
        if (mc.world.getBlockState(pos).isAir()) return true;
        if (mc.world.getBlockState(pos).getHardness(mc.world, pos) < 0) {
            debug("方块不可挖掘: " + pos.toShortString());
            return true;
        }

        // 距离 > 5 格 → 寻路（每 tick 只发起一次）
        if (mc.player.getBlockPos().getSquaredDistance(pos) > 25.0) {
            if (minePos == null || !minePos.equals(pos)) {
                minePos = pos;
                PathManagers.get().moveTo(pos, false);
                debug("距离方块太远，开始寻路: " + pos.toShortString());
            }
            return false;
        }

        // 切换工具
        if (toolSlot != -1 && toolSlot != mc.player.getInventory().getSelectedSlot()) {
            if (toolSlot > 8) {
                InvUtils.move().from(toolSlot).toHotbar(mc.player.getInventory().getSelectedSlot());
            } else {
                InvUtils.swap(toolSlot, false);
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(toolSlot));
            }
        }

        // 朝向 + 发包挖掘
        int faceIdx = FacingLogic.bestFaceIndex(
            mc.player.getEyePos().x, mc.player.getEyePos().y, mc.player.getEyePos().z,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Direction face = Direction.values()[faceIdx];
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.updateBlockBreakingProgress(pos, face);

        return mc.world.getBlockState(pos).isAir();
    }

    /** 放置方块到 target（地面放置：点下方方块 UP 面）；放置旋转开关控制是否转头 */
    private void placeBlockAt(BlockPos target, int invSlot) {
        // 交换到快捷栏
        if (invSlot > 8) {
            InvUtils.move().from(invSlot).toHotbar(mc.player.getInventory().getSelectedSlot());
        } else {
            InvUtils.swap(invSlot, false);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(invSlot));
        }

        BlockPos below = target.down();
        if (placeRotate.get()) {
            InteractionUtils.interactBlockSafely(below, Direction.UP);
        } else {
            if (mc.player == null || mc.world == null) return;
            net.minecraft.util.hit.BlockHitResult hit = new net.minecraft.util.hit.BlockHitResult(
                new net.minecraft.util.math.Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5),
                Direction.UP, below, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        }
    }

    /** 打开容器（潜影盒/工作台/末影箱）：六面检测选面，转头交互 */
    private void openContainer(BlockPos pos) {
        if (pos == null || mc.currentScreen != null) return;
        if (mc.player.getBlockPos().getSquaredDistance(pos) > 9.0) {
            PathManagers.get().moveTo(pos, false);
            return;
        }
        int faceIdx = FacingLogic.bestFaceIndex(
            mc.player.getEyePos().x, mc.player.getEyePos().y, mc.player.getEyePos().z,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Direction face = Direction.values()[faceIdx];
        InteractionUtils.interactBlockSafely(pos, face);
    }

    // ================= 钻石合成（CraftingScreenHandler） =================

    /**
     * 钻石合成（移植 misaka x0035）：
     * 输出槽有钻石块 → 取出（5 tick 等待）→ 完成；
     * 钻石 ≥ 9 → 清空合成格 → 拖拽 9 钻石入 1-9 → 等待合成。
     */
    private boolean craftDiamondBlock() {
        if (craftActionTicks > 0) {
            craftActionTicks--;
            return false;
        }
        if (!(mc.currentScreen instanceof CraftingScreen)) return false;

        CraftingScreenHandler h = (CraftingScreenHandler) mc.player.currentScreenHandler;
        ItemStack out = h.getSlot(0).getStack();

        // 输出槽有钻石块 → 取出到背包，等 5 tick 完成
        if (!out.isEmpty() && out.getItem() == Items.DIAMOND_BLOCK) {
            // V4.39 容量兜底（misaka 语义）：背包满时丢一组钻石腾出空间，保证产物能取走
            if (!hasEmptyInventorySlot()) {
                int dropDiamondSlot = findLargestDiamondSlot(h);
                if (dropDiamondSlot != -1) {
                    mc.interactionManager.clickSlot(h.syncId, dropDiamondSlot, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(h.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
                    info("背包已满，丢弃一组钻石以腾出空间");
                }
            }
            mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
            tookOutBlock = true;
            craftActionTicks = 5;
            debug("取出钻石块");
            return false;
        }

        // 取出等待结束（输出槽已空且上次取出动作完成）→ 本次合成完成
        if (craftActionTicks == 0 && out.isEmpty() && tookOutBlock) {
            tookOutBlock = false;
            return true;
        }

        // 统计钻石（背包 + 合成格）
        if (diamondCount(h) < 9) {
            // V4.47 bug3：对齐 misaka——钻石不足 9 个视为"本轮合成结束"返回 true，关工作台进存储链，
            // 防止永远卡在合成台退不出（原逻辑 return false 导致 CRAFTING 状态永不切换）
            debug("钻石不足9个，无法合成，先存储现有钻石块");
            tookOutBlock = false;
            return true;
        }

        // 找最大钻石堆槽（背包）
        int diamondSlot = findLargestDiamondSlot(h);
        if (diamondSlot == -1) return false;

        // 清空合成格中的非钻石物品
        clearCraftingGrid(h);
        craftActionTicks = craftWaitDelay.get();

        // 拖拽分配 9 钻石到合成格 1-9
        mc.interactionManager.clickSlot(h.syncId, diamondSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(h.syncId, -999, 0, SlotActionType.QUICK_CRAFT, mc.player);
        for (int i = 1; i <= 9; i++) {
            mc.interactionManager.clickSlot(h.syncId, i, 1, SlotActionType.QUICK_CRAFT, mc.player);
        }
        mc.interactionManager.clickSlot(h.syncId, -999, 2, SlotActionType.QUICK_CRAFT, mc.player);
        // 光标剩余钻石放回（拿起后原槽已空，必能找到空槽）
        returnCursorStack(h);
        tookOutBlock = false;
        info("合成格已填满，等待合成结果...");
        return false;
    }

    private boolean tookOutBlock = false;

    /** 统计钻石数量（背包 + 合成格） */
    private int diamondCount(CraftingScreenHandler h) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.DIAMOND) count += s.getCount();
        }
        for (int i = 1; i <= 9; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.getItem() == Items.DIAMOND) count += s.getCount();
        }
        return count;
    }

    /** 找背包中钻石数量最多的槽位 → 返回合成台界面屏幕槽（换算见 CraftingSlotMath，对齐 misaka x0005） */
    private int findLargestDiamondSlot(CraftingScreenHandler h) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.DIAMOND && s.getCount() > bestCount) {
                bestCount = s.getCount();
                best = i;
            }
        }
        return best == -1 ? -1 : CraftingSlotMath.inventoryIndexToCraftingScreenSlot(best);
    }

    /** 清空合成格中的非钻石物品（QUICK_MOVE 移回背包） */
    private void clearCraftingGrid(CraftingScreenHandler h) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (!s.isEmpty() && s.getItem() != Items.DIAMOND) {
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
        }
    }

    /** 光标剩余物品放回背包第一个空槽（原槽已空，必能找到） */
    private void returnCursorStack(ScreenHandler h) {
        if (h.getCursorStack().isEmpty()) return;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                int screenSlot = i < 9 ? i + 37 : i + 1; // CraftingScreenHandler 布局
                mc.interactionManager.clickSlot(h.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
        // 无空槽（理论不发生）：丢弃
        mc.interactionManager.clickSlot(h.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
    }

    // ================= 石英合成（PlayerScreenHandler 静默 2x2） =================

    /**
     * 石英合成（移植 misaka x0247）：随身 2x2 合成格 4 石英 → 1 石英块；
     * 输出槽的石英块主动丢弃（白名单不含石英块，防占背包）。
     */
    private void craftQuartzBlock() {
        PlayerScreenHandler h = mc.player.playerScreenHandler;

        // 输出槽有石英块 → 丢弃
        ItemStack out = h.getSlot(0).getStack();
        if (!out.isEmpty() && out.getItem() == Items.QUARTZ_BLOCK) {
            mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(h.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
            returnCursorStackPlayer(h);
            info("丢弃石英块");
            return;
        }

        // 石英总量 < 4 → 等挖更多
        if (countQuartz(h) < 4) return;

        // 合成格（1-4）石英数 0< n <4 → 清空合成格
        int gridQuartz = 0;
        for (int i = 1; i <= 4; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.getItem() == Items.QUARTZ) gridQuartz += s.getCount();
        }
        if (gridQuartz > 0 && gridQuartz < 4) {
            debug("合成栏石英不足4个，清空合成栏");
            for (int i = 1; i <= 4; i++) {
                if (!h.getSlot(i).getStack().isEmpty()) {
                    mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return;
                }
            }
            return;
        }
        if (gridQuartz >= 4) return; // 等合成结果

        // 找背包（主背包 9-35 屏幕槽，热键 36-44 屏幕槽）中的石英
        int quartzSlot = findQuartzScreenSlot(h);
        if (quartzSlot == -1) return;

        // 拖拽 4 石英入合成格 1-4
        mc.interactionManager.clickSlot(h.syncId, quartzSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(h.syncId, -999, 0, SlotActionType.QUICK_CRAFT, mc.player);
        for (int i = 1; i <= 4; i++) {
            mc.interactionManager.clickSlot(h.syncId, i, 1, SlotActionType.QUICK_CRAFT, mc.player);
        }
        mc.interactionManager.clickSlot(h.syncId, -999, 2, SlotActionType.QUICK_CRAFT, mc.player);
        if (!h.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(h.syncId, quartzSlot, 0, SlotActionType.PICKUP, mc.player);
        }
        returnCursorStackPlayer(h);
        debug("放入石英到合成栏");
    }

    /** 统计石英数量（背包 + 合成格） */
    private int countQuartz(PlayerScreenHandler h) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.QUARTZ) count += s.getCount();
        }
        for (int i = 1; i <= 4; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.getItem() == Items.QUARTZ) count += s.getCount();
        }
        return count;
    }

    /** PlayerScreenHandler 布局找石英屏幕槽：主背包 9-35、热键 36-44 */
    private int findQuartzScreenSlot(PlayerScreenHandler h) {
        for (int i = 9; i <= 35; i++) {
            if (h.getSlot(i).getStack().getItem() == Items.QUARTZ) return i;
        }
        for (int i = 36; i <= 44; i++) {
            if (h.getSlot(i).getStack().getItem() == Items.QUARTZ) return i;
        }
        return -1;
    }

    /** 光标剩余放回（PlayerScreenHandler 布局：主背包屏幕 9-35，热键 36-44） */
    private void returnCursorStackPlayer(PlayerScreenHandler h) {
        if (h.getCursorStack().isEmpty()) return;
        for (int i = 9; i <= 35; i++) {
            if (h.getSlot(i).getStack().isEmpty()) {
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
        for (int i = 36; i <= 44; i++) {
            if (h.getSlot(i).getStack().isEmpty()) {
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
        mc.interactionManager.clickSlot(h.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
    }

    // ================= 背包 / 容器判定 =================

    /** 背包满：主背包 9-35 全非空（纯逻辑，可单测） */
    public static boolean isInventoryFull(int[] mainInventoryCounts) {
        for (int i = 9; i < 36; i++) {
            if (mainInventoryCounts[i] <= 0) return false;
        }
        return true;
    }

    private boolean isInventoryFull() {
        int[] counts = new int[36];
        for (int i = 0; i < 36; i++) {
            counts[i] = mc.player.getInventory().getStack(i).getCount();
        }
        return isInventoryFull(counts);
    }

    /** 时运等级（1.21.11：Enchantments.FORTUNE 是 RegistryKey，需经注册表取 RegistryEntry） */
    private int getFortuneLevel(ItemStack stack) {
        if (mc.world == null) return 0;
        var reg = mc.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        var entry = reg.getEntry(reg.get(Enchantments.FORTUNE));
        return EnchantmentHelper.getLevel(entry, stack);
    }

    /** 找时运镐（fortune > 0 的镐子）背包槽 */
    private int findFortunePickaxeSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isPickaxe(s.getItem()) && getFortuneLevel(s) > 0) {
                return i;
            }
        }
        return -1;
    }

    /** 找低耐久镐子（剩余耐久 < 阈值）背包槽 */
    private int findLowDurabilityPickaxe() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isPickaxe(s.getItem()) && isPickaxeDurabilityLow(s.getMaxDamage(), s.getDamage(), pickaxeThreshold.get())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPickaxeRepaired() {
        if (repairPickaxeSlot == -1 || repairPickaxeSlot >= mc.player.getInventory().size()) return false;
        ItemStack s = mc.player.getInventory().getStack(repairPickaxeSlot);
        if (s.isEmpty() || !isPickaxe(s.getItem())) return false;
        return s.getMaxDamage() - s.getDamage() >= pickaxeThreshold.get();
    }

    /** 镐子判定（纯逻辑，可单测） */
    public static boolean isPickaxe(Item item) {
        return item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE
            || item == Items.IRON_PICKAXE || item == Items.STONE_PICKAXE
            || item == Items.WOODEN_PICKAXE || item == Items.GOLDEN_PICKAXE;
    }

    /** 镐子剩余耐久低于阈值（纯逻辑，可单测） */
    public static boolean isPickaxeDurabilityLow(int maxDamage, int damage, int threshold) {
        return maxDamage - damage < threshold;
    }

    /** 背包槽 → CraftingScreenHandler 屏幕槽（主背包 10-36，热键 37-45） */
    public static int invSlotToCraftingScreen(int invSlot) {
        return invSlot < 9 ? invSlot + 37 : invSlot + 1;
    }

    /** 背包槽 → ShulkerBox/GenericContainer 屏幕槽（主背包 27-53，热键 54-62） */
    public static int invSlotToContainerScreen(int invSlot) {
        return invSlot < 9 ? invSlot + 54 : invSlot + 18;
    }

    /** 钻石合成条件：钻石 >= 9（纯逻辑，可单测） */
    public static boolean canCraftDiamondBlock(int diamondCount) {
        return diamondCount >= 9;
    }

    /** 石英合成条件：石英 >= 4（纯逻辑，可单测） */
    public static boolean canCraftQuartzBlock(int quartzCount) {
        return quartzCount >= 4;
    }

    /** 找空潜影盒背包槽（含 CONTAINER 组件且 0 物品，或组件为 null） */
    private int findEmptyShulkerSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isShulkerBoxItem(s.getItem()) && isShulkerEmpty(s)) return i;
        }
        return -1;
    }

    /** 潜影盒是否为空（纯逻辑，可单测） */
    public static boolean isShulkerEmpty(ItemStack stack) {
        ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
        if (c == null) return true;
        return !c.iterateNonEmpty().iterator().hasNext();
    }

    /** 找背包中指定物品槽 */
    private int findSlot(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    /** 找背包中指定物品槽（含快捷栏优先热键） */
    private int findInvSlot(Item item) {
        return findSlot(item);
    }

    /** 背包是否还有空槽 */
    private boolean hasEmptyInventorySlot() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return true;
        }
        return false;
    }

    /** 潜影盒界面是否已满（27 格全非空） */
    private boolean isShulkerFull(ShulkerBoxScreenHandler h) {
        for (int i = 0; i < 27; i++) {
            if (h.getSlot(i).getStack().isEmpty()) return false;
        }
        return true;
    }

    /** 找背包中"装满目标物品的潜影盒"槽（27 格全满且全为目标物品） */
    private int findFullTargetBoxSlot(Item target) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!isShulkerBoxItem(s.getItem())) continue;
            ContainerComponent c = s.get(DataComponentTypes.CONTAINER);
            if (c == null) continue;
            int count = 0;
            boolean allTarget = true;
            for (ItemStack inner : c.iterateNonEmpty()) {
                count++;
                if (inner.getItem() != target) allTarget = false;
            }
            if (count >= 27 && allTarget) return i;
        }
        return -1;
    }

    /** 单个潜影盒堆是否为"满盒"（27 槽全满且无空槽）——纯逻辑，可单测；对齐 misaka x0024 */
    public static boolean isShulkerFullStack(ItemStack stack) {
        ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
        if (c == null) return false;
        int count = 0;
        for (ItemStack inner : c.iterateNonEmpty()) {
            count++;
            if (inner.getCount() < inner.getMaxCount()) return false;
        }
        return count >= 27;
    }

    /** V4.49 对齐 misaka x0024：背包里是否有"满盒"（任何 27 槽全满的潜影盒）——拾取潜影盒后立即检测 */
    private boolean hasFullShulkerInInventory() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isShulkerBoxItem(s.getItem()) && isShulkerFullStack(s)) return true;
        }
        return false;
    }

    /** 找容器空槽（0-26 范围） */
    private int findEmptyContainerSlot(GenericContainerScreenHandler h, int from, int to) {
        for (int i = from; i <= to; i++) {
            if (h.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    /** 找容器中的空潜影盒槽 */
    private int findEmptyShulkerInContainer(GenericContainerScreenHandler h) {
        for (int i = 0; i < 27; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (isShulkerBoxItem(s.getItem()) && isShulkerEmpty(s)) return i;
        }
        return -1;
    }

    /** 潜影盒物品判定（纯逻辑，可单测） */
    public static boolean isShulkerBoxItem(Item item) {
        return item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof ShulkerBoxBlock;
    }

    // ================= 位置搜索 =================

    /** misaka x0015：从玩家位置半径 3→128 方形递增扫描（Y ±3），第一个非液体实心方块即返回 */
    private BlockPos findSafeSpot() {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int r = 3; r <= 128; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos p = playerPos.add(dx, dy, dz);
                        if (isSafeSpotBlock(p)) return p;
                    }
                }
            }
        }
        return null;
    }

    /** 安全位置判定（misaka 语义）：实心方块（可站立/可挖）且非液体 */
    private boolean isSafeSpotBlock(BlockPos p) {
        BlockState state = mc.world.getBlockState(p);
        if (state.isAir() || !state.isSolid()) return false;
        return state.getFluidState().isEmpty();
    }

    /** 放置位置搜索（V4.50 对齐 misaka x0016：以 safeSpot 为中心 3x3x3 优先，不依赖玩家位置；
     *  玩家在洞里/附近时再兜底玩家周围 3x3x3）——修复"拾取后玩家不在洞里导致放末影箱/放盒放不下" */
    private BlockPos findPlacePosition(BlockPos center) {
        // 第一轮（misaka x0016 语义）：safeSpot 周围 3x3x3，洞内总有可放格（工作台占位除外）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (canPlaceAt(p)) return p;
                }
            }
        }
        // 第二轮兜底：玩家周围 3x3x3（玩家在洞里/附近时优先踩到的位置）
        BlockPos playerPos = mc.player.getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = playerPos.add(dx, dy, dz);
                    if (canPlaceAt(p)) return p;
                }
            }
        }
        return null;
    }

    /** 放置判定纯逻辑（对齐 misaka x0023：目标格空气 + 下方非空气/非液体）——纯布尔可单测 */
    public static boolean canPlaceOn(boolean targetIsAir, boolean belowIsAir, boolean belowFluidEmpty) {
        return targetIsAir && !belowIsAir && belowFluidEmpty;
    }

    /** 放置判定（misaka x0023(BlockPos)：目标格空气 + 下方非空气/非液体） */
    private boolean canPlaceAt(BlockPos p) {
        BlockState target = mc.world.getBlockState(p);
        BlockState below = mc.world.getBlockState(p.down());
        return canPlaceOn(target.isAir(), below.isAir(), below.getFluidState().isEmpty());
    }

    /** 拾取目标掉落物（10 格内最近） */
    private BlockPos findPickupDrop(PickupTarget target) {
        BlockPos best = null;
        double bestDist = 100.0; // 10 格
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity ie)) continue;
            Item item = ie.getStack().getItem();
            boolean match = switch (target) {
                case SHULKER_BOX -> isShulkerBoxItem(item);
                case CRAFTING_TABLE -> item == Items.CRAFTING_TABLE;
                case ENDER_CHEST -> item == Items.ENDER_CHEST;
            };
            if (!match) continue;
            double d = e.getBlockPos().getSquaredDistance(mc.player.getBlockPos());
            if (d < bestDist) {
                bestDist = d;
                best = e.getBlockPos();
            }
        }
        return best;
    }

    /** 深暗之域判定（纯逻辑可单测困难，依赖 MC；保留模块内） */
    private boolean isDeepDark(BlockPos pos) {
        RegistryKey<Biome> key = mc.world.getBiome(pos).getKey().orElse(null);
        return key != null && key.equals(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "deep_dark")));
    }

    /** 128 格范围找 y>=0 最近非深暗地面点（移植 misaka x0273 语义） */
    private BlockPos findSafeEscapePoint() {
        BlockPos p = mc.player.getBlockPos();
        for (int r = 16; r <= 128; r += 16) {
            for (int dx = -r; dx <= r; dx += 8) {
                for (int dz = -r; dz <= r; dz += 8) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // 只扫边缘环
                    BlockPos probe = p.add(dx, 0, dz);
                    if (isDeepDark(probe)) continue;
                    int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, probe.getX(), probe.getZ());
                    int groundY = top - 1;
                    if (groundY < 0) continue;
                    BlockPos ground = new BlockPos(probe.getX(), groundY, probe.getZ());
                    if (isDeepDark(ground)) continue;
                    return ground;
                }
            }
        }
        return null;
    }

    // ================= 工具 =================

    private void waitTicks(MiningState backTo, int ticks) {
        previousState = backTo;
        stateWaitTicks = ticks;
        state = MiningState.WAITING;
    }

    private void debug(String msg) {
        if (debugOutput.get()) info(msg);
    }

    private void disconnect(String msg) {
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.getConnection().disconnect(Text.literal(msg));
        }
    }

    /** 选最佳挖掘工具：时运镐优先（挖矿石掉落），其次最高效率工具；没有返回 -1（用当前槽） */
    private int findBestToolSlot(BlockPos target) {
        net.minecraft.block.BlockState bs = mc.world.getBlockState(target);
        int fortuneSlot = -1;
        int bestEffSlot = -1;
        float bestEff = 1.0f;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            if (isPickaxe(s.getItem())) {
                if (getFortuneLevel(s) > 0 && fortuneSlot == -1) {
                    fortuneSlot = i;
                }
            }
            float speed = s.getMiningSpeedMultiplier(bs);
            if (speed > bestEff) {
                bestEff = speed;
                bestEffSlot = i;
            }
        }
        return fortuneSlot != -1 ? fortuneSlot : bestEffSlot;
    }

    // ================= V4.31 方案B：工具策略（锁时运 / 精准采集挖末影箱） =================

    /** 挖箱/挖矿工具选择策略（纯逻辑，可单测）。
     *  @param miningOre    正在挖矿（钻石/残骸/石英）——必须时运，防止精准采集把钻石挖成原矿
     *  @param enderChest   正在挖末影箱——有精准采集镐就用精准（回收本体），没有就消耗式时运
     *  @param hasSilkPickaxe 背包是否有精准采集镐
     */
    public enum ToolStrategy { FORTUNE, SILK_TOUCH, ANY }

    public static ToolStrategy pickaxeStrategy(boolean miningOre, boolean enderChest, boolean hasSilkPickaxe) {
        if (enderChest) return hasSilkPickaxe ? ToolStrategy.SILK_TOUCH : ToolStrategy.FORTUNE;
        if (miningOre) return ToolStrategy.FORTUNE;
        return ToolStrategy.ANY;
    }

    /** 确保手持时运镐（挖矿状态每 20 tick 调用；防 Baritone/手动挖选中精准采集镐挖钻石 → 掉原矿） */
    private void ensureFortunePickaxeHeld() {
        // 自动吃/杀戮光环保护：AutoEat 在吃、玩家正在使用物品、FO杀戮光环正在攻击时
        // 不抢槽位，等结束下一 tick 再锁回时运镐（修复与 AutoEat/杀戮光环抢槽死循环）
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        FOKillAura ka = Modules.get().get(FOKillAura.class);
        if (MiningGuard.shouldSkipFortuneLock(
            mc.player.isUsingItem(),
            autoEat != null && autoEat.eating,
            ka != null && ka.attacking)) return;

        ItemStack held = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        if (isPickaxe(held.getItem()) && getFortuneLevel(held) > 0) return;
        int slot = findFortunePickaxeSlot();
        if (slot >= 0) {
            if (slot > 8) {
                InvUtils.move().from(slot).toHotbar(mc.player.getInventory().getSelectedSlot());
            } else {
                InvUtils.swap(slot, false);
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
            }
        }
    }

    /** 找"镐子 + 精准采集"背包槽（挖末影箱回收本体用），找不到返回 -1 */
    private int findSilkTouchPickaxeSlot() {
        if (mc.world == null) return -1;
        var reg = mc.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        var entry = reg.getEntry(reg.get(Enchantments.SILK_TOUCH));
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (st.isEmpty()) continue;
            if (isPickaxe(st.getItem()) && EnchantmentHelper.getLevel(entry, st) > 0) return i;
        }
        return -1;
    }
}
