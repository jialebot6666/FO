package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.SpeedMineLogic;
import meteordevelopment.meteorclient.events.entity.player.DoAttackEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * FO 发包挖掘（移植自 Hachimi V2 SpeedMine；源码类 {@code BlockPosBoxMultitask}，1083 行）。
 *
 * <p>用法：对准要挖的方块<b>左键点一下</b>，模块接管该方块的挖掘——客户端自己累计破坏进度，
 * 达到「挖掘进度」（Speed，3C3U 实战 0.7）就直接发 {@code STOP_DESTROY_BLOCK} 包破坏，
 * 不需要按住左键，也不受原版挖掘动画/冷却约束。再点别的方块即切换目标。</p>
 *
 * <p>核心机制（与原版对齐）：
 * <ul>
 *   <li>接管方式对齐原版 {@code onAttackBlock}：拦截攻击方块事件、取消原版挖掘、进发包队列。</li>
 *   <li>进度判定对齐原版 {@code shouldBreak}：{@code calcBlockBreakingDelta} 累计到 Speed 即破坏；
 *       一击即碎的方块直接发。</li>
 *   <li>「自动切换」开挖时自动切热栏里最快工具（对齐 SwapAction=Hotbar + AutoSwitch=true）。</li>
 *   <li>快捷键：暂停键（按住暂停发包）、取消键（放弃当前目标），对齐原版 Pause/Cancel。</li>
 *   <li>聊天提示：破坏后聊天栏报坐标（Notify）。</li>
 *   <li>渲染：进度色方块（开始/结束色与线框色渐变，实战值青色→深青、粉红线框）+ 方块轮廓。</li>
 * </ul></p>
 *
 * <p>默认参数照搬 3C3U 实战配置（cheng.json SpeedMine 段）：Speed=0.7、Range=6、
 * 仅黑名单（火/重生锚）、不挥手（Swing=None）、StartRotate/EndRotate 关、
 * Notify 开（聊天提示）、OnlyOnGround/PauseOnFlying/PauseInFluid 全关、
 * 渲染色 StartSide=0x3f7e7ef5 / EndSide=0x066e6ee1 / StartLine=EndLine=0xff9595ff。</p>
 *
 * <p>刻意不移植（依赖上游私有体系或需实机调试，需要时单独立项）：
 * Grim「NewDisabler」快速挖绕过、Desync 破坏去同步、InstantMine/DoubleBreak 原版漏洞、
 * NoReset/NoCollision、EnderChest 按键、Multitask 并发挖掘体系。
 * ⚠️ 因此 Grim 服上「挖掘进度」可以正常压到 0.7 发包，但没有针对 Grim 快速挖检测的对策，
 * 实机被拦截时优先把 Speed 往上调（0.85~0.95），仍不行再回来一起做绕过。</p>
 */
public class FOPacketMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgGuard = settings.createGroup("暂停条件");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // 通用

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("挖掘进度").description("vanilla 破坏进度累计到该倍率即发包破坏（Speed）。3C3U 实战 0.7")
        .defaultValue(0.7).min(0.1).sliderMin(0.3).sliderMax(1.0).build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围").description("能接管的方块最大距离（Range）")
        .defaultValue(6).min(1).sliderMax(8).build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换").description("开挖时自动切热栏里最快的适用工具")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("挥手").description("发包破坏时挥手（实战 Swing=None，默认关）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> startRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("开挖转向").description("开挖前把视角转向目标方块（实战关闭）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("聊天提示").description("发包破坏方块后在聊天栏提示坐标（Notify，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<Keybind> pauseKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("暂停键").description("按住时暂停发包挖掘（不丢当前目标）")
        .defaultValue(Keybind.none()).build()
    );

    private final Setting<Keybind> cancelKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("取消键").description("按一下放弃当前目标")
        .defaultValue(Keybind.none()).build()
    );

    private final Setting<FilterMode> filterMode = sgGeneral.add(new EnumSetting.Builder<FilterMode>()
        .name("过滤模式").description("能接管的方块过滤规则")
        .defaultValue(FilterMode.仅黑名单).build()
    );

    /** 黑名单默认值：照搬 3C3U 实战配置（cheng.json SpeedMine.Blacklist）。 */
    private static final Block[] DEFAULT_BLACKLIST = {Blocks.FIRE, Blocks.RESPAWN_ANCHOR};

    private final Setting<List<Block>> blacklist = sgGeneral.add(new BlockListSetting.Builder()
        .name("黑名单").description("过滤模式下不接管的方块")
        .defaultValue(DEFAULT_BLACKLIST)
        .visible(() -> filterMode.get() != FilterMode.仅白名单)
        .build()
    );

    private final Setting<List<Block>> whitelist = sgGeneral.add(new BlockListSetting.Builder()
        .name("白名单").description("过滤模式下只接管的方块")
        .defaultValue(Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
        .visible(() -> filterMode.get() == FilterMode.仅白名单)
        .build()
    );

    // 暂停条件（实战值全关，按需打开）

    private final Setting<Boolean> onlyOnGround = sgGuard.add(new BoolSetting.Builder()
        .name("仅地面").description("只有站在地面上才发包（OnlyOnGround）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> pauseOnFlying = sgGuard.add(new BoolSetting.Builder()
        .name("飞行暂停").description("飞行时暂停（PauseOnFlying）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> pauseInFluid = sgGuard.add(new BoolSetting.Builder()
        .name("水中暂停").description("游泳时暂停（PauseInFluid）")
        .defaultValue(false).build()
    );

    // 渲染

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染").description("画出当前目标和挖掘进度（Render）")
        .defaultValue(true).build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式").description("线框/填充方式（BoxMode）")
        .defaultValue(ShapeMode.Both).build()
    );

    private final Setting<SettingColor> startColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("开始颜色").description("刚开挖的颜色（StartSide，实战 0x3f7e7ef5 青色）")
        .defaultValue(new SettingColor(63, 126, 126, 245)).build()
    );

    private final Setting<SettingColor> endColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("结束颜色").description("即将破坏的颜色（EndSide，实战 0x066e6ee1 深青）")
        .defaultValue(new SettingColor(6, 110, 110, 225)).build()
    );

    private final Setting<SettingColor> startLineColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("开始线条").description("刚开挖的线框颜色（StartLine，实战 0xff9595ff 粉红）")
        .defaultValue(new SettingColor(255, 149, 149, 255)).build()
    );

    private final Setting<SettingColor> endLineColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("结束线条").description("即将破坏的线框颜色（EndLine，实战 0xff9595ff 粉红）")
        .defaultValue(new SettingColor(255, 149, 149, 255)).build()
    );

    private final Setting<Boolean> blockOutline = sgRender.add(new BoolSetting.Builder()
        .name("方块轮廓").description("画目标方块的轮廓线（BlockOutline）")
        .defaultValue(true).build()
    );

    /** 当前正在发包挖的方块；null = 空闲。 */
    private BlockPos current;
    /** 当前目标方块的破坏面。 */
    private Direction currentDir;
    /** 已累计的破坏进度（0~1）。 */
    private float progress;

    public FOPacketMine() {
        super(AddonTemplate.CATEGORY, "FO 发包挖掘", "移植自 Hachimi V2 SpeedMine：对准方块左键接管，自动发包快挖（进度阈值 0.7），支持自动切工具/暂停/取消/进度渲染。");
    }

    @Override
    public void onActivate() {
        cancelSession();
    }

    @Override
    public void onDeactivate() {
        cancelSession();
    }

    /**
     * 接管方块（对齐原版 onAttackBlock：拦截攻击 → 取消原版挖掘 → 进发包队列）。
     * 注：彗星端 1.21.11 没有 AttackBlockEvent，等价触发点是 DoAttackEvent
     * （原版 doAttack() 开头触发），目标方块从准星取。
     */
    @EventHandler
    private void onAttack(DoAttackEvent event) {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)
            || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!isValidTarget(pos)) return;

        // 接管：不让原版开始真挖掘
        event.cancel();

        if (pos.equals(current)) return; // 已经在挖这格
        startSession(pos, hit.getSide());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (pauseKey.get().isPressed()) return;
        if (cancelKey.get().isPressed()) {
            cancelSession();
            return;
        }

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;
        if (pauseOnFlying.get() && mc.player.getAbilities().flying) return;
        if (pauseInFluid.get() && mc.player.isTouchingWater()) return;

        if (current == null) return;

        // 服务端已经破坏了这格 → 收工
        BlockState state = mc.world.getBlockState(current);
        if (state.isAir()) {
            cancelSession();
            return;
        }
        if (!BlockUtils.canBreak(current)) {
            cancelSession();
            return;
        }

        float delta = state.calcBlockBreakingDelta(mc.player, mc.world, current);
        if (SpeedMineLogic.shouldBreak(progress, delta, speed.get().floatValue())) {
            // 到阈值：直接发 STOP_DESTROY_BLOCK 包破坏（发包挖掘的核心一步）
            mc.player.networkHandler.sendPacket(
                new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, current, currentDir));
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            if (notify.get()) info("发包挖掘：已破坏 (px, %d, %d, %d)", current.getX(), current.getY(), current.getZ());
            // 客户端状态复位，避免原版残留半截挖掘进度
            mc.interactionManager.cancelBlockBreaking();
            progress = 0;
            current = null;
        } else {
            progress = SpeedMineLogic.accumulate(progress, delta);
        }
    }

    /** 开始挖一格：切工具 → 转向（可选）→ 发 START 包。 */
    private void startSession(BlockPos pos, Direction dir) {
        cancelSession();

        if (autoSwitch.get()) switchToBestTool(mc.world.getBlockState(pos));

        currentDir = dir;
        progress = 0;

        Runnable start = () -> mc.interactionManager.attackBlock(pos, currentDir);
        if (startRotate.get()) {
            Rotations.rotate(Rotations.getYaw(pos.toCenterPos()), Rotations.getPitch(pos.toCenterPos()), start);
        } else {
            start.run();
        }

        current = pos;
    }

    /** 自动切换：热栏里对该方块最快的工具（Meteor 现成 findFastestTool，对齐 SwapAction=Hotbar）。 */
    private void switchToBestTool(BlockState state) {
        meteordevelopment.meteorclient.utils.player.FindItemResult tool = InvUtils.findFastestTool(state);
        if (tool.found() && tool.slot() != mc.player.getInventory().getSelectedSlot()) {
            InvUtils.swap(tool.slot(), false);
        }
    }

    /** 目标过滤（对齐原版 Selection/Whitelist/Blacklist + Range）。 */
    private boolean isValidTarget(BlockPos pos) {
        if (pos.equals(current)) return true; // 已接管的不重复过滤
        if (mc.player.getEyePos().distanceTo(pos.toCenterPos()) > range.get()) return false;

        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;

        Block block = state.getBlock();
        switch (filterMode.get()) {
            case 仅黑名单: return !blacklist.get().contains(block);
            case 仅白名单: return whitelist.get().contains(block);
            default: return true;
        }
    }

    private void cancelSession() {
        if (current != null && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        current = null;
        progress = 0;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || current == null) return;
        if (mc.world.getBlockState(current).isAir()) return;

        int[] side = SpeedMineLogic.lerpColor(
            new int[]{startColor.get().r, startColor.get().g, startColor.get().b, startColor.get().a},
            new int[]{endColor.get().r, endColor.get().g, endColor.get().b, endColor.get().a},
            progress
        );
        int[] line = SpeedMineLogic.lerpColor(
            new int[]{startLineColor.get().r, startLineColor.get().g, startLineColor.get().b, startLineColor.get().a},
            new int[]{endLineColor.get().r, endLineColor.get().g, endLineColor.get().b, endLineColor.get().a},
            progress
        );
        SettingColor sideColor = new SettingColor(side[0], side[1], side[2], side[3]);
        SettingColor lineColor = new SettingColor(line[0], line[1], line[2], line[3]);

        Box box = new Box(current);
        event.renderer.box(box, sideColor, lineColor, shapeMode.get(), blockOutline.get() ? 0 : -1);
    }

    public enum FilterMode {
        全部, 仅黑名单, 仅白名单
    }
}
