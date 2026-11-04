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
import meteordevelopment.meteorclient.settings.IntSetting;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * FO 发包挖掘（移植自 Hachimi V2 SpeedMine；源码类 {@code BlockPosBoxMultitask} +
 * {@code BlockPosPlayerHelper}，Packet 模式完整协议）。
 *
 * <p>用法：对准要挖的方块<b>左键点一下</b>，模块接管该方块的挖掘——客户端自己累计破坏进度，
 * 达到「挖掘进度」（Speed）就发 {@code STOP_DESTROY_BLOCK} 包破坏，不需要按住左键。</p>
 *
 * <p><b>Grim 绕过协议（3C3U 实战依赖，逐包对齐上游）：</b>
 * <ul>
 *   <li>{@code NewDisabler}（实战值）：会话开始连发 START(真实位置) →
 *       START(0,-999,0)（伪造包把服务端/Grim 的挖掘位置追踪重指向假方块）→
 *       DoubleBreak 开时再补一个 STOP（双进度漏洞）。这样后续真实的 STOP 不受
 *       服务端 vanilla 1.21.2+ 快挖防护与 Grim fastbreak 检查约束。</li>
 *   <li>{@code Disabler}：START 前先发一个 STOP。</li>
 *   <li>{@code 时间校验}（上游 Strict/Time）：不做伪造，STOP 发出前等待
 *       {@code ceil(1/最大单tick进度)*50ms} 的真实挖掘时间（只差 25ms 以内才放行）。</li>
 *   <li>所有模式共用：上次 STOP 后 <b>280ms 冷却</b>内不开新会话（上游 alP()）。</li>
 *   <li>所有挖掘包都带正确的 block-change sequence（与仓库 {@code BlockClicker} 同款
 *       accesswidener 取号，不再发 sequence=0 的裸包）。</li>
 * </ul></p>
 *
 * <p>进度模型对齐上游 {@code xs()}：{@code delta / Speed} 累计到 1.0 即破坏；
 * 实战配置 CheckGround/CheckFly 均为 true，与直接用原版
 * {@code calcBlockBreakingDelta}（内部按真实 onGround/鞘翅/水中衰减）等价。</p>
 *
 * <p>其余对齐：STOP 发出后方块未消失，等「重试等待」tick 后重开整个会话（RetryTicks）；
 * 「即时重破」开启时破坏完成后继续对原位置补发 STOP 流（上游 InstantMine：方块被刷新回
 * 来时立即再破，最多持续 RetryTicks tick）。</p>
 *
 * <p>默认参数照搬 3C3U 实战配置（cheng.json SpeedMine 段）：Speed=0.7、Range=6、
 * 仅黑名单（火/重生锚）、不挥手（Swing=None）、StartRotate/EndRotate 关、Grim=NewDisabler、
 * DoubleBreak=true、RetryTicks=10、InstantMine=true、Desync=SetAir（假破块）、
 * Notify 开（聊天提示）、
 * OnlyOnGround/PauseOnFlying/PauseInFluid 全关、渲染色实战 hex。</p>
 *
 * <p>刻意不移植（依赖上游私有体系）：Hold/HoldTicks 换持握回切（本模块自动切换后全程持工具，
 * 不做屏幕容器级回切）、Desync 客户端假破块、NoCollision 挖掘格无碰撞、MineAir、
 * EnderChest 按键、Multitask 并发体系。Grim 服被拦时优先换 Grim 模式或调高「挖掘进度」。</p>
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

    private final Setting<DesyncMode> desync = sgGeneral.add(new EnumSetting.Builder<DesyncMode>()
        .name("假破块").description("发破坏包后客户端本地立即移除方块，视觉上瞬间消失（上游 Desync=SetAir，实战开启）")
        .defaultValue(DesyncMode.客户端清除).build()
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

    // Grim 绕过（3C3U 实战核心）

    private final Setting<GrimMode> grimMode = sgGeneral.add(new EnumSetting.Builder<GrimMode>()
        .name("Grim绕过").description("绕过 Grim fastbreak 检查的方式（Grim）。新版绕过=实战 NewDisabler，旧版绕过=Disabler")
        .defaultValue(GrimMode.新版绕过).build()
    );

    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("双重破坏").description("会话开始即补发一个 STOP（原版双挖掘进度漏洞，DoubleBreak）。实战开启")
        .defaultValue(true).build()
    );

    private final Setting<Integer> retryTicks = sgGeneral.add(new IntSetting.Builder()
        .name("重试等待").description("STOP 发出后方块仍未消失，等待该 tick 数后重开会话（RetryTicks）。实战 10")
        .defaultValue(10).min(0).sliderMax(60).build()
    );

    private final Setting<Boolean> instantMine = sgGeneral.add(new BoolSetting.Builder()
        .name("即时重破").description("破坏完成后继续对原位置补发 STOP 流，方块被刷新回来时立即再破（InstantMine）。实战开启")
        .defaultValue(true).build()
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

    private final Setting<BoxMode> boxMode = sgRender.add(new EnumSetting.Builder<BoxMode>()
        .name("形状模式").description("线框/填充方式（上游 BoxMode，实战 Both）")
        .defaultValue(BoxMode.全部).build()
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

    /** 上游 alP()：NewDisabler/Time 模式下两次会话的最小间隔。 */
    private static final long SESSION_COOLDOWN_MS = 280;
    /** 上游 xt()：Time 模式放行 STOP 的剩余时间阈值。 */
    private static final double TIME_MODE_SLACK_MS = 25.0;

    // 会话状态

    /** 当前正在发包挖的方块；null = 空闲。 */
    private BlockPos current;
    /** 当前目标方块的破坏面。 */
    private Direction currentDir;
    /** 已累计的破坏进度（0~1，= vanilla delta 累计 / Speed）。 */
    private float progress;
    /** 是否已发过 START 序列。 */
    private boolean started;
    /** 是否已发过破坏 STOP，等待服务端确认。 */
    private boolean breakSent;
    /** STOP 发出后等待服务端破坏的 tick 计数（RetryTicks）。 */
    private int waitTicks;
    /** 即时重破流已发的 tick 数。 */
    private int rebreakTicks;
    /** 本会话最大单 tick 破坏进度（Time 模式估时用，上游 p）。 */
    private float maxDelta;
    /** START 发出时刻（Time 模式用，上游 j）。 */
    private long startTime;
    /** 上次 STOP 发出时刻（会话冷却用，上游 i）。 */
    private long lastStopTime;

    public FOPacketMine() {
        super(AddonTemplate.CATEGORY, "FO 发包挖掘", "移植自 Hachimi V2 SpeedMine：对准方块左键接管，自动发包快挖，带 Grim NewDisabler 绕过（3C3U 实战协议）。");
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

        // 只接管有效目标：无效目标放行原版挖掘（否则普通方块完全挖不动）
        event.cancel();

        if (pos.equals(current)) return; // 已经在挖这格
        startSession(pos, hit.getSide());
    }

    /** 开始挖一格：记录目标与破坏面，START 序列由 tick 循环发（冷却后）。 */
    private void startSession(BlockPos pos, Direction dir) {
        cancelSession();
        current = pos;
        currentDir = dir;
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

        // 会话期间每 tick 清零原版挖掘进度：原版破坏由发包接管
        // （6.8p6：删除 6.8p5 的"按住攻击键自动接管"——存在按键状态卡死漏洞，
        //   打开界面后松键事件被吞，导致不点击也自动挖掘）
        if (current != null && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }

        if (current == null) return;

        BlockState state = mc.world.getBlockState(current);

        // ── 阶段一：还没发 START 序列 ──
        if (!started) {
            if (state.isAir()) { // 目标没了（被别人挖掉）
                cancelSession();
                return;
            }
            // 会话冷却（上游 alP()：NewDisabler/时间校验模式下 280ms 内不开新会话）
            if (grimMode.get() != GrimMode.无 && grimMode.get() != GrimMode.旧版绕过
                && SpeedMineLogic.inSessionCooldown(System.currentTimeMillis(), lastStopTime, SESSION_COOLDOWN_MS)) {
                return;
            }
            if (autoSwitch.get()) switchToBestTool(state);
            sendStartSequence();
            return;
        }

        // ── 阶段三：STOP 已发，等服务端破坏 ──
        if (breakSent) {
            if (state.isAir()) {
                // 破坏成功。即时重破（上游 InstantMine）：继续对原位置每 tick 补发 STOP，
                // 方块若被刷新回来会被流里的包直接破掉
                if (instantMine.get() && rebreakTicks < retryTicks.get()) {
                    rebreakTicks++;
                    sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, current, currentDir);
                    lastStopTime = System.currentTimeMillis();
                } else {
                    cancelSession();
                }
                return;
            }
            waitTicks++;
            if (waitTicks > retryTicks.get()) {
                // 服务端一直没破（被拦/回弹）：整个会话重来
                restartSession();
            }
            return;
        }

        // ── 阶段二：已 START，累计进度 ──
        if (state.isAir()) { // 服务端已破坏
            cancelSession();
            return;
        }
        if (!BlockUtils.canBreak(current)) {
            cancelSession();
            return;
        }

        float delta = state.calcBlockBreakingDelta(mc.player, mc.world, current);
        if (delta > maxDelta) maxDelta = delta;

        if (SpeedMineLogic.shouldBreak(progress, delta, speed.get().floatValue())) {
            progress = 1;
            // Time 模式：STOP 前必须等够真实挖掘时间（上游 xt() 的 FX 分支）
            if (grimMode.get() == GrimMode.时间校验) {
                double expected = Math.ceil(1.0 / Math.max(maxDelta, 1e-6)) * 50.0;
                if (!SpeedMineLogic.timeModeStopAllowed(expected, System.currentTimeMillis() - startTime, TIME_MODE_SLACK_MS)) {
                    return;
                }
            }
            sendBreakPacket();
        } else {
            progress = SpeedMineLogic.accumulate(progress, delta);
        }
    }

    /**
     * 会话开始发包序列（逐包对齐上游 {@code BlockPosPlayerHelper.xr()}，Packet 模式分支）：
     * Disabler: STOP(真实) → START(真实)；NewDisabler: START(真实) → START(0,-999,0) →
     * DoubleBreak 开时补 STOP(真实)；时间校验/无: 仅 START(真实)。
     */
    private void sendStartSequence() {
        progress = 0;
        maxDelta = 0;
        waitTicks = 0;
        rebreakTicks = 0;

        Runnable start = this::sendStartPackets;
        if (startRotate.get()) {
            Rotations.rotate(Rotations.getYaw(current.toCenterPos()), Rotations.getPitch(current.toCenterPos()), start);
        } else {
            start.run();
        }
    }

    private void sendStartPackets() {
        Direction dir = currentDir;

        if (grimMode.get() == GrimMode.旧版绕过) {
            sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, current, dir);
        }
        sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, current, dir);
        startTime = System.currentTimeMillis();

        if (grimMode.get() == GrimMode.新版绕过) {
            // 伪造包：把服务端/Grim 的挖掘位置追踪重指向不存在的方块（上游 xE）
            sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(0, -999, 0), Direction.UP);
        }
        if (doubleBreak.get()) {
            // 原版双挖掘进度漏洞（上游 xD，DoubleBreak=true 实战开启）
            sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, current, dir);
        }

        started = true;
    }

    /** 发破坏 STOP（上游 xt() 的发包部分）。 */
    private void sendBreakPacket() {
        sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, current, currentDir);
        if (swing.get()) mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        // 假破块（上游 Desync=SetAir）：客户端本地立即移除，视觉瞬间消失
        if (desync.get() == DesyncMode.客户端清除) {
            mc.world.removeBlock(current, false);
        }
        if (notify.get()) info("发包挖掘：已破坏 %d %d %d", current.getX(), current.getY(), current.getZ());
        mc.interactionManager.cancelBlockBreaking();
        breakSent = true;
        waitTicks = 0;
        lastStopTime = System.currentTimeMillis();
    }

    /** 带正确 block-change sequence 的挖掘包（与仓库 BlockClicker 同款取号，上游 SequencedPacketCreator）。 */
    private void sendDig(PlayerActionC2SPacket.Action action, BlockPos pos, Direction dir) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        int sequence;
        var pending = mc.world.getPendingUpdateManager().incrementSequence();
        sequence = pending.sequence;
        pending.close();
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, dir, sequence));
    }

    /** 自动切换：热栏里对该方块最快的工具（Meteor 现成 findFastestTool，对齐 SwapAction=Hotbar）。 */
    private void switchToBestTool(BlockState state) {
        var tool = InvUtils.findFastestTool(state);
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

    /** STOP 没生效：整会话重置，下个 tick 重新走冷却 → START 序列。 */
    private void restartSession() {
        started = false;
        breakSent = false;
        progress = 0;
        maxDelta = 0;
        waitTicks = 0;
        rebreakTicks = 0;
    }

    private void cancelSession() {
        if (current != null && mc.interactionManager != null && started && !breakSent) {
            mc.interactionManager.cancelBlockBreaking();
        }
        current = null;
        progress = 0;
        started = false;
        breakSent = false;
        waitTicks = 0;
        rebreakTicks = 0;
        maxDelta = 0;
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
        event.renderer.box(box, sideColor, lineColor, boxMode.get().toShapeMode(), blockOutline.get() ? 0 : -1);
    }

    public enum FilterMode {
        全部, 仅黑名单, 仅白名单
    }

    /** 上游 BoxMode 的中文化（Meteor 的 ShapeMode 枚举值是英文，GUI 会露英文）。 */
    public enum BoxMode {
        全部, 仅线框, 仅填充;

        public ShapeMode toShapeMode() {
            return switch (this) {
                case 全部 -> ShapeMode.Both;
                case 仅线框 -> ShapeMode.Lines;
                default -> ShapeMode.Sides;
            };
        }
    }

    /** 上游 NoneNormalMode 的中文化（Desync）。 */
    public enum DesyncMode {
        无, 客户端清除
    }

    /** 上游 DisablerNewDisablerMode（旧版绕过=Disabler、新版绕过=NewDisabler；Strict 与 Time 行为相同，合并为时间校验）。 */
    public enum GrimMode {
        无, 旧版绕过, 新版绕过, 时间校验
    }
}
