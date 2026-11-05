package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.Easing;
import com.fo.addon.utils.SpeedMineLogic;
import meteordevelopment.meteorclient.events.entity.player.DoAttackEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 发包挖掘——移植自 Hachimi V2 SpeedMine（源码类 {@code BlockPosBoxMultitask}，
 * 1083 行 + 会话类 {@code BlockPosPlayerHelper}，591 行）的 <b>完整移植版</b>（6.9p0）。
 *
 * <p>核心机制：对准方块左键接管 → 客户端自算破坏进度 → 到阈值（Speed，3C3U 实战 0.7）
 * 直接发 {@code STOP_DESTROY_BLOCK} 包破坏。</p>
 *
 * <p>6.9p0 全量移植内容：</p>
 * <ul>
 *   <li>双会话（Multitask）：主会话（左键/对外 API 请求，带优先级抢占）+
 *       镜像会话（双重破坏的第二条挖掘进度线，上游 helper/helper2）。</li>
 *   <li>对外 API：{@link #mine(BlockPos, int)}——FO 其他模块可请求"帮我挖这个方块"，
 *       带优先级抢占语义（对齐上游 alI）。</li>
 *   <li>原版加速（Damage）模式：不接管攻击，原版挖掘进度达到 Speed 即置满秒破
 *       （Mixin 访问 {@code currentBreakingProgress}）；附属：无冷却（NoDelay）、
 *       不重置（NoReset）、不中断（NoAbort，Mixin 拦截 cancelBlockBreaking）。</li>
 *   <li>Grim 绕过四模式：新版绕过（START 后补 START(0,-999,0) 伪造包）、旧版绕过
 *       （START 前先 STOP）、时间校验（STOP 前等够真实挖掘时间）、无。</li>
 *   <li>渲染全套：盒子动画（随进度向中心收缩）、缓动曲线（28 种，对齐上游 Easing）、
 *       形状贴合（方块真实轮廓）、帧间平滑（tickDelta 插值）、四色渐变。</li>
 *   <li>末影箱按键：在会话方块位置放置末影箱（放置范围/放置转向，上游 EnderChest）。</li>
 *   <li>碰撞穿透（NoCollision）：Mixin 取消会话方块对本人的碰撞，可走进方块。</li>
 *   <li>时序：280ms 会话冷却（alP）、重试等待（RetryTicks）、挖掘上限（MiningTicks）、
 *       即时重破（InstantMine）、保持/持握上限（Hold/HoldTicks）、额外切换（AutoSwitch）。</li>
 *   <li>状态旗标：地面检测（CheckGround）/飞行检测（CheckFly）——控制挖掘速度预测
 *       是否计入空中/鞘翅惩罚（1.21.11 原版对离地挖掘一律 /5）。</li>
 *   <li>假破块（Desync）：无 / 正常破坏 / 假破块（客户端本地移除）。</li>
 * </ul>
 *
 * <p>默认参数照搬 3C3U 实战配置（cheng.json SpeedMine 段）：Speed=0.7、Range=6、
 * 仅黑名单（火/重生锚）、Grim=新版绕过、DoubleBreak=true、RetryTicks=10、
 * MiningTicks=10、InstantMine=true、Hold=true（持握下限 2）、CheckGround=true、CheckFly=true、
 * IgnoreBroken=true、StrictCheck=true、MineAir=false、Desync=假破块、Notify 开、挖掘优先级 8。
 * ⚠️ 6.8p11 修正：此前 Hold/MineAir/CheckGround/IgnoreBroken/StrictCheck 五项抄的是上游
 * <b>源码默认值</b>而不是 cheng.json 的实战值（MineAir、CheckGround 的「实战值」还被注释写反了）。</p>
 *
 * <p>与上游的刻意差异（依赖 Hachimi 私有体系，无法等价移植）：</p>
 * <ul>
 *   <li>换持方式（SwapMode）：快捷栏（Hotbar 直切 1~9）与静默换持（Screen 容器级，
 *       6.8p8 移植——遍历整个 ScreenHandler 找最快工具，用 ClickSlot(SWAP) 容器点击包换持，
 *       不发选栏包，带 syncId 环境守卫；上游 Pick 不移植）；</li>
 *   <li>MainHand/Blink/AutoAnchor 联动不适用（Hachimi 专属模块）；</li>
 *   <li>长按持续挖掘不做（6.8p5 的按键状态卡死漏洞已删，见 AGENTS.md 6.8p6）。</li>
 * </ul>
 */
public class FOPacketMine extends Module {
    /** 单例（Mixin 与对外 API 入口）。 */
    private static FOPacketMine instance;

    public static FOPacketMine get() {
        return instance;
    }

    /** Mixin 访问 mc 用的入口。 */
    public net.minecraft.client.MinecraftClient mc() {
        return mc;
    }

    // ── 设置组 ──────────────────────────────────────────────

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDamage = settings.createGroup("原版加速");
    private final SettingGroup sgPause = settings.createGroup("暂停条件");
    private final SettingGroup sgChest = settings.createGroup("末影箱");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    /** 上游 DamagePacketMode（Packet=发包挖掘 / Damage=原版加速）。 */
    public enum MineMode {
        发包挖掘, 原版加速
    }

    /** 上游 DisablerNewDisablerMode（旧版绕过=Disabler、新版绕过=NewDisabler；Strict/Time 合并为时间校验）。 */
    public enum GrimMode {
        无, 旧版绕过, 新版绕过, 时间校验
    }

    /** 上游 NoneNormalMode（Desync：无 / 正常破坏=客户端完整破坏 / 假破块=本地移除）。 */
    public enum DesyncMode {
        无, 正常破坏, 假破块
    }

    /**
     * 上游 SwapMode（快捷栏=Hotbar 直切 / 静默换持=Screen 容器级；Pick 不移植）。
     *
     * <p>快捷栏：只在快捷栏 1~9 找最快工具，发 UpdateSelectedSlot 直切（手会明显切栏）。
     * 静默换持：遍历整个 ScreenHandler（含背包 36 格、副手、已打开容器的所有槽位）找最快工具，
     * 用 ClickSlot(SWAP) 容器点击包把它与当前选中快捷栏位对调——不发选栏包，
     * 选栏位不变，服务端视角只看到一次容器交换（真·鬼手）。</p>
     */
    public enum SwapActionMode {
        快捷栏, 静默换持
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

    public enum FilterMode {
        全部, 仅黑名单, 仅白名单
    }

    private final Setting<MineMode> mode = sgGeneral.add(new EnumSetting.Builder<MineMode>()
        .name("模式").description("发包挖掘=接管左键纯发包；原版加速=不接管，原版进度到阈值直接置满秒破（Mode，实战发包挖掘）")
        .defaultValue(MineMode.发包挖掘).build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("挖掘进度").description("触发破坏的进度阈值（Speed，实战 0.7）")
        .defaultValue(0.7).min(0.1).sliderMax(1).build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("挖掘范围").description("离目标方块超过该距离就不挖（Range，实战 6）")
        .defaultValue(6).min(1).sliderMax(8).build()
    );

    private final Setting<Integer> priority = sgGeneral.add(new IntSetting.Builder()
        .name("挖掘优先级").description("左键接管时给会话的优先级；其他模块走 mine(坐标, 优先级) 抢占时比这个数（Priority，上游默认 8、3C3U 实战 10）")
        .defaultValue(8).min(0).sliderMax(10).build()
    );

    private final Setting<GrimMode> grimMode = sgGeneral.add(new EnumSetting.Builder<GrimMode>()
        .name("Grim绕过").description("绕过 Grim fastbreak 检查的方式（Grim）。实战新版绕过")
        .defaultValue(GrimMode.新版绕过).build()
    );

    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("双重破坏").description("会话开始补发一个 STOP，并开第二条挖掘进度线（DoubleBreak，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> instantMine = sgGeneral.add(new BoolSetting.Builder()
        .name("即时重破").description("方块破坏后继续对其补发 STOP，重破刷新回来的方块（InstantMine，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<Integer> retryTicks = sgGeneral.add(new IntSetting.Builder()
        .name("重试等待").description("发包后方块一直不掉，超过该 tick 数整个会话重来（RetryTicks，实战 10）")
        .defaultValue(10).min(1).sliderMax(60).build()
    );

    private final Setting<Integer> miningTicks = sgGeneral.add(new IntSetting.Builder()
        .name("挖掘上限").description("进度挖满后允许等待破坏的最大 tick 数（MiningTicks）")
        .defaultValue(10).min(1).sliderMax(60).build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("额外切换").description("进度挖满时若没拿最快工具，自动换上发破坏包再换回（AutoSwitch）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> hold = sgGeneral.add(new BoolSetting.Builder()
        .name("保持换持").description("发包后先不把工具切回去：方块还在就一直持着，方块没了也要持满「持握下限」才切回（Hold，1.21+ 服务器适用，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> manual = sgGeneral.add(new BoolSetting.Builder()
        .name("手动切换").description("不自动换工具，自己手动切（Manual）")
        .defaultValue(false).build()
    );

    private final Setting<SwapActionMode> swapAction = sgGeneral.add(new EnumSetting.Builder<SwapActionMode>()
        .name("换持方式").description("快捷栏=直切1~9号栏；静默换持=容器级换持，从整个背包/已打开容器换工具，只发容器点击包（Screen，真鬼手）")
        .defaultValue(SwapActionMode.快捷栏)
        .visible(() -> !manual.get())
        .build()
    );

    private final Setting<Integer> holdTicks = sgGeneral.add(new IntSetting.Builder()
        .name("持握下限").description("保持换持时，方块挖掉后至少要持握这么多 tick 才切回（上游 HoldTicks 是最短持握而非上限，3C3U 实战 2）")
        .defaultValue(2).min(0).sliderMax(60).build()
    );

    private final Setting<Boolean> mineAir = sgGeneral.add(new BoolSetting.Builder()
        .name("允许挖空气").description("允许对空气位发起挖掘（MineAir，上游代码默认开、3C3U 实战关闭）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("挥手").description("发包破坏/开挖时挥手（Swing）。实战=None 关闭，多余挥手包可能与 Grim 状态不同步")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> ignoreBroken = sgGeneral.add(new BoolSetting.Builder()
        .name("忽略已破坏").description("已被破坏（空）的方块不成为挖掘目标（IgnoreBroken，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<DesyncMode> desync = sgGeneral.add(new EnumSetting.Builder<DesyncMode>()
        .name("假破块").description("发包破坏后客户端本地怎么处理方块（Desync）。实战假破块")
        .defaultValue(DesyncMode.假破块).build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("聊天提示").description("发包破坏方块后在聊天栏提示坐标（Notify，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> strictCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("严格暂停").description("暂停期间禁止发起新的挖掘会话（StrictCheck，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> checkGround = sgGeneral.add(new BoolSetting.Builder()
        .name("地面检测").description("挖掘速度预测计入离地惩罚（CheckGround，实战开启=尊重空中惩罚；关闭则按在地面算，进度涨得更快）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> checkFly = sgGeneral.add(new BoolSetting.Builder()
        .name("飞行检测").description("挖掘速度预测计入鞘翅滑翔惩罚（CheckFly，实战开启）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> noCollision = sgGeneral.add(new BoolSetting.Builder()
        .name("碰撞穿透").description("正在挖的方块对本人的碰撞取消，可以直接走进去（NoCollision）")
        .defaultValue(false).build()
    );

    // ── 原版加速（Damage）模式附属 ──

    private final Setting<Boolean> noDelay = sgDamage.add(new BoolSetting.Builder()
        .name("无冷却").description("破块后无挖掘冷却，可立即继续挖（NoDelay，仅原版加速模式）")
        .defaultValue(false).visible(() -> mode.get() == MineMode.原版加速).build()
    );

    private final Setting<Boolean> noReset = sgDamage.add(new BoolSetting.Builder()
        .name("不重置").description("挖掘中切换物品不重置进度（NoReset，仅原版加速模式）")
        .defaultValue(false).visible(() -> mode.get() == MineMode.原版加速).build()
    );

    private final Setting<Boolean> noAbort = sgDamage.add(new BoolSetting.Builder()
        .name("不中断").description("拦截原版对挖掘的取消（吃东西以外的打断，NoAbort，仅原版加速模式）")
        .defaultValue(false).visible(() -> mode.get() == MineMode.原版加速).build()
    );

    // ── 过滤 ──

    private final Setting<FilterMode> filterMode = sgGeneral.add(new EnumSetting.Builder<FilterMode>()
        .name("过滤模式").description("目标方块的过滤规则。实战仅黑名单")
        .defaultValue(FilterMode.仅黑名单).build()
    );

    private final Setting<List<Block>> blacklist = sgGeneral.add(new BlockListSetting.Builder()
        .name("黑名单").description("过滤模式下不挖的方块。实战：火、重生锚")
        .defaultValue(Blocks.FIRE, Blocks.RESPAWN_ANCHOR)
        .visible(() -> filterMode.get() != FilterMode.仅白名单)
        .build()
    );

    private final Setting<List<Block>> whitelist = sgGeneral.add(new BlockListSetting.Builder()
        .name("白名单").description("过滤模式下只挖的方块。实战：黑曜石、末影箱")
        .defaultValue(Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
        .visible(() -> filterMode.get() == FilterMode.仅白名单)
        .build()
    );

    // ── 暂停条件 ──

    private final Setting<Boolean> onlyOnGround = sgPause.add(new BoolSetting.Builder()
        .name("仅在地面").description("只在站在方块上时工作（OnlyOnGround，站梯子例外）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> pauseOnFlying = sgPause.add(new BoolSetting.Builder()
        .name("鞘翅暂停").description("鞘翅滑翔时暂停当前挖掘（PauseOnFlying）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> pauseInFluid = sgPause.add(new BoolSetting.Builder()
        .name("水中暂停").description("处于流体中时暂停当前挖掘（PauseInFluid）")
        .defaultValue(false).build()
    );

    // ── 旋转 ──

    private final Setting<Boolean> startRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("开挖转向").description("开挖前把视角转向目标方块（StartRotate，实战关闭）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> endRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("结束转向").description("发破坏包前把视角转向目标方块（EndRotate）")
        .defaultValue(false).build()
    );

    // ── 按键 ──

    private final Setting<Keybind> pauseKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("暂停键").description("按住时暂停发包挖掘（不丢当前目标）")
        .defaultValue(Keybind.none()).build()
    );

    private final Setting<Keybind> cancelKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("取消键").description("按住时放弃当前挖掘目标")
        .defaultValue(Keybind.none()).build()
    );

    private final Setting<Keybind> enderChestKey = sgChest.add(new KeybindSetting.Builder()
        .name("末影箱键").description("按一下，在当前会话方块位置放置一个末影箱（EnderChest）")
        .defaultValue(Keybind.none()).build()
    );

    private final Setting<Double> placeRange = sgChest.add(new DoubleSetting.Builder()
        .name("放置范围").description("放末影箱的最大交互距离（PlaceRange）")
        .defaultValue(4.5).min(1).sliderMax(8).build()
    );

    private final Setting<Boolean> placeRotate = sgChest.add(new BoolSetting.Builder()
        .name("放置转向").description("放末影箱前把视角转向放置点（PlaceRotate）")
        .defaultValue(true).build()
    );

    // ── 渲染 ──

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染").description("渲染挖掘进度方块（Render）")
        .defaultValue(true).build()
    );

    private final Setting<BoxMode> boxMode = sgRender.add(new EnumSetting.Builder<BoxMode>()
        .name("形状模式").description("线框/填充方式（BoxMode）")
        .defaultValue(BoxMode.全部).build()
    );

    private final Setting<Boolean> boxAnimation = sgRender.add(new BoolSetting.Builder()
        .name("盒子动画").description("渲染盒随进度向中心收缩（BoxAnimation）")
        .defaultValue(true).build()
    );

    private final Setting<Easing> easing = sgRender.add(new EnumSetting.Builder<Easing>()
        .name("缓动曲线").description("盒子动画的缓动曲线（Easing）。上游默认圆弧缓出")
        .defaultValue(Easing.圆弧缓出).build()
    );

    private final Setting<SettingColor> startColor = sgRender.add(new ColorSetting.Builder()
        .name("开始颜色").description("刚开挖的面包色（StartSide，实战青色）")
        .defaultValue(new SettingColor(126, 126, 245, 63)).build()
    );

    private final Setting<SettingColor> endColor = sgRender.add(new ColorSetting.Builder()
        .name("结束颜色").description("即将破坏的面包色（EndSide，实战深青）")
        .defaultValue(new SettingColor(110, 110, 225, 6)).build()
    );

    private final Setting<SettingColor> startLineColor = sgRender.add(new ColorSetting.Builder()
        .name("开始线色").description("刚开挖的线框色（StartLine，实战粉红）")
        .defaultValue(new SettingColor(149, 149, 255, 255)).build()
    );

    private final Setting<SettingColor> endLineColor = sgRender.add(new ColorSetting.Builder()
        .name("结束线色").description("即将破坏的线框色（EndLine，实战粉红）")
        .defaultValue(new SettingColor(149, 149, 255, 255)).build()
    );

    private final Setting<Boolean> blockOutline = sgRender.add(new BoolSetting.Builder()
        .name("方块轮廓").description("渲染盒贴合方块真实轮廓（BlockOutline）")
        .defaultValue(true).build()
    );

    // ── 会话状态 ──

    /** 上游 280ms 会话冷却（alP）。 */
    private static final long SESSION_COOLDOWN_MS = 280;
    /** 时间校验模式放行余量（上游 25ms）。 */
    private static final double TIME_MODE_SLACK_MS = 25;

    /** 主会话（上游 blockPosPlayerHelper2）：左键点击或对外 API 请求触发。 */
    private Session mainSession;
    /** 镜像会话（上游 blockPosPlayerHelper）：双重破坏的第二条进度线。 */
    private Session mirrorSession;
    /** 上游 i：280ms 会话冷却时钟，由<b>每一个</b> STOP 包武装（含 DoubleBreak/旧版绕过的会话期 STOP）。 */
    private long lastStopTime;
    /** 上游 j：时间校验模式的诚实等待基准，由每一个 START 包（含 (0,-999,0) 伪造包）武装。 */
    private long lastStartTime;
    /** 保持换持：发包后等待切回的旧槽位。 */
    private int revertSlot = -1;
    /** 保持换持（静默换持）：切回用的 ScreenHandler 槽位号、SWAP 按钮（目标快捷栏位）与捕获时的 syncId。 */
    private int revertScreenSlot = -1;
    private int revertScreenButton = -1;
    private int revertScreenSyncId = -1;
    /** 原版加速模式：NoReset 进度快照。 */
    private ItemStack lastHeldStack;
    private float lastDamageProgress;
    /** 末影箱键边沿检测。 */
    private boolean enderChestKeyWasPressed;

    public FOPacketMine() {
        super(AddonTemplate.CATEGORY, "FO 发包挖掘", "移植自 Hachimi V2 SpeedMine：左键接管方块，客户端自算进度到阈值直接发包破坏。");
        instance = this;
    }

    // ── 对外 API（Multitask）──

    /**
     * 请求挖掘一个方块（上游 alI 的移植版，带优先级抢占）。
     *
     * <p>FO 其他模块可调用：{@code FOPacketMine.get().mine(pos, 优先级)}。
     * 当前主会话优先级更高且未完成时请求被拒绝。</p>
     *
     * @param pos      目标方块
     * @param priority 优先级（数值越大越优先）
     * @return 是否接受请求
     */
    public boolean mine(BlockPos pos, int priority) {
        if (mc.player == null || mc.world == null) return false;
        if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        if (!isValidTarget(pos)) return false;

        if (mainSession != null && mainSession.pos.equals(pos)) return true;

        // 双重破坏开启时，镜像会话正在挖的方块拒绝再开主会话（上游 alH 守卫）
        if (doubleBreak.get() && mirrorSession != null && mirrorSession.pos.equals(pos)) return false;

        if (mainSession != null && !mainSession.done && mainSession.priority > priority
            && reachableFace(mainSession.pos) != null) {
            // 上游 alI L329-336：范围测的是**已有会话那一格的面中心**——那一格还在手上干活才不让抢。
            // 6.8p10 测的是「新请求那一格」，对象接错了：远处的新请求能抢掉近处的高优先级会话（6.8p11 修复）
            return false;
        }

        mainSession = new Session(pos, priority, speed.get().floatValue());
        mainSession.tick();
        return true;
    }

    // ── Mixin 挂钩 ──

    /**
     * NoAbort 挂钩（Mixin 调用）：原版加速模式 + 不中断 + 未按使用键时，
     * 拦截原版 cancelBlockBreaking（对齐上游 CancelBlockBreakingEvent 条件）。
     */
    public boolean shouldKeepBreaking() {
        return mode.get() == MineMode.原版加速
            && noAbort.get()
            && !mc.options.useKey.isPressed();
    }

    /**
     * NoCollision 挂钩（Mixin 调用）：会话进行中且已发过破坏包（上游 m>0）时，
     * 方块对本人的碰撞取消。
     */
    public boolean shouldIgnoreCollision(BlockPos pos) {
        return noCollision.get()
            && mainSession != null
            && mainSession.breakAttempts > 0
            && pos.equals(mainSession.pos);
    }

    // ── 静默换持（上游 SwapMode.Screen / xw / InventoryTracker.SWAP）──

    /**
     * 静默换持选槽：遍历整个 ScreenHandler 找最快工具（上游 xw）。
     * 无界面时 ScreenHandler = playerScreenHandler（覆盖背包 36 格 + 副手）；
     * 打开容器时还包括该容器的所有槽位。选槽判定走 {@link SpeedMineLogic#fastestScreenSlot}。
     *
     * @param state 目标方块状态
     * @return ScreenHandler 槽位号；没有快于徒手的工具返回 -1
     */
    private int findFastestScreenSlot(BlockState state) {
        List<Float> speeds = new ArrayList<>(mc.player.currentScreenHandler.slots.size());
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            speeds.add(slot.getStack().getMiningSpeedMultiplier(state));
        }
        int index = SpeedMineLogic.fastestScreenSlot(speeds);
        return index != -1 ? mc.player.currentScreenHandler.slots.get(index).id : -1;
    }

    /**
     * 静默换持执行：ClickSlot(SWAP) 把 slotId 槽位与 button 号快捷栏位对调
     * （上游 InventoryTracker/MainHand 的 {@code clickSlot(syncId, slot, button, SWAP, player)} 语义）。
     * 不发 UpdateSelectedSlot——选栏位不变，客户端视觉与服务端背包同时交换，旁观者看不出切栏。
     */
    private void screenSwap(int slotId, int button) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId, slotId, button, SlotActionType.SWAP, mc.player);
    }

    // ── 事件 ──

    @Override
    public void onActivate() {
        clearSessions();
        lastHeldStack = null;
        lastDamageProgress = 0;
    }

    @Override
    public void onDeactivate() {
        // 关模块前先把欠着的切回做掉：6.8p10 只清了 revertSlot，静默换持的字段整个漏掉，
        // 结果是镐子留在快捷栏、原物品卡在被换出的槽里没人管（6.8p11 修复）
        revertHeldTool(false);
        clearSessions();
    }

    /** 丢掉两个会话与待切回记录（只清状态，不发任何包）。 */
    private void clearSessions() {
        mainSession = null;
        mirrorSession = null;
        revertSlot = -1;
        revertScreenSlot = -1;
        revertScreenButton = -1;
        revertScreenSyncId = -1;
    }

    /**
     * 把「保持换持」借来的工具切回原位（上游 xy/xB 里的 {@code bb}）。
     *
     * @param keepPendingOnSyncMismatch 界面环境变了（syncId 不匹配）时要不要留着待切回、等下一个 tick
     *                                  （上游的 syncId 守卫）；关模块/退服传 false = 放弃，不再碰背包
     */
    private void revertHeldTool(boolean keepPendingOnSyncMismatch) {
        if (revertScreenSlot != -1) {
            boolean envOk = mc.player != null
                && mc.player.currentScreenHandler.syncId == revertScreenSyncId;
            if (envOk) screenSwap(revertScreenSlot, revertScreenButton);
            if (!envOk && keepPendingOnSyncMismatch) return;
            revertScreenSlot = -1;
            revertScreenButton = -1;
            revertScreenSyncId = -1;
        } else if (revertSlot != -1) {
            if (mc.player != null) InvUtils.swap(revertSlot, false);
            revertSlot = -1;
        }
    }

    @EventHandler
    private void onAttack(DoAttackEvent event) {
        if (mode.get() != MineMode.发包挖掘) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)
            || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!isValidTarget(pos)) return;

        // 只接管有效目标：无效目标放行原版挖掘
        event.cancel();

        if (mainSession != null && pos.equals(mainSession.pos)) return; // 已经在挖这格
        mine(pos, priority.get());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // 上游 onTickPre（L394 + L433-437）：player/world 任一为 null 就把会话和待切回一起丢掉。
        // 6.8p10 完全没有这层守卫——退服/换世界后旧坐标的会话还在跑，会对新世界同一格发 START/STOP
        // 并本地 removeBlock（假破块），还可能直接 NPE（6.8p11 修复）
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            revertHeldTool(false);
            clearSessions();
            return;
        }

        if (pauseKey.get().isPressed()) return;
        if (cancelKey.get().isPressed()) {
            revertHeldTool(false);
            clearSessions();
            return;
        }

        // ── 原版加速（Damage）模式：不接管攻击 ──
        if (mode.get() == MineMode.原版加速) {
            damageTick();
            return;
        }

        // ── 发包挖掘（Packet）模式 ──

        // 保持换持切回（上游 xB L542-555）：方块还在就一直持着；方块没了最多再持 HoldTicks tick；
        // 目标不可挖或跑出范围立刻切回。计数用会话自己的 holdTicksCount（上游 n），
        // 不是模块级的独立计时器，也不是「方块一没就切回」（6.8p11 修正）
        if (revertSlot != -1 || revertScreenSlot != -1) {
            boolean blockStillThere = mainSession != null
                && !mc.world.getBlockState(mainSession.pos).isAir();
            boolean mineable = mainSession != null
                && reachableFace(mainSession.pos) != null
                && BlockUtils.canBreak(mainSession.pos);
            int heldTicks = mainSession != null ? mainSession.holdTicksCount : 0;
            if (!SpeedMineLogic.shouldKeepHolding(mainSession != null, blockStillThere,
                heldTicks, holdTicks.get(), mineable)) {
                revertHeldTool(true);
            }
        }

        // 镜像会话（双重破坏的第二条进度线，上游 helper.xs()+xu()）：
        // ⚠️ 上游镜像只推进度与完成判定，从不发 STOP——它若发包会把共享的 280ms 冷却时钟
        //    和时间校验时钟每 tick 刷新，把主会话（点第二个方块后）永久卡死（6.8p10 修复）
        if (mirrorSession != null) {
            mirrorSession.progressTick();
            if (mirrorSession.done
                || (mirrorSession.progress >= 1.0f
                    && ++mirrorSession.breakAttempts > miningTicks.get())) {
                mirrorSession = null;
            } else if (mirrorSession.progress < 1.0f) {
                // 上游 xu() L442：完成门控没满足就把计数归零（6.8p11 补回，此前镜像从不归零 → 提前判死）
                mirrorSession.breakAttempts = 0;
            }
        }

        // 主会话（上游 helper2.xq()）：完成收尾只走 done（进度满后的收尾在 tryBreak 内，
        // 上游 xu 是镜像专用——主会话若再走一遍会双重计数并发双 STOP，6.8p10 修复）；
        // InstantMine 开启时保留 done 会话：方块重生后进度仍满，直接秒补 STOP（上游 f/d 不重置语义）
        if (mainSession != null) {
            // 会话期间每 tick 清零原版挖掘进度：原版破坏由发包接管
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
            mainSession.tick();
            if (mainSession.done && !instantMine.get()) mainSession = null;
        }

        // 末影箱键（边沿触发，防止按住连放）
        boolean pressed = enderChestKey.get().isPressed();
        if (pressed && !enderChestKeyWasPressed && mainSession != null) {
            placeEnderChest(mainSession.pos);
        }
        enderChestKeyWasPressed = pressed;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get()) return;
        // 主会话完成后不再画（避免空气位残留满格盒子）；镜像画之前与主会话同位去重（上游 aly）
        if (mainSession != null && !mainSession.done) mainSession.render(event);
        if (mirrorSession != null && !mirrorSession.done
            && (mainSession == null || !mainSession.pos.equals(mirrorSession.pos))) {
            mirrorSession.render(event);
        }
    }

    // ── 挖掘会话（上游 BlockPosPlayerHelper 的移植版）──

    public class Session {
        /** 目标方块。 */
        public BlockPos pos;
        /** 破坏面。 */
        public Direction dir;
        /** 请求优先级。 */
        public int priority;
        /** 速度倍率（Speed；镜像会话固定 1.0）。 */
        public float speedScale;

        /** 当前破坏进度（上游 d）。 */
        public float progress;
        /** 上一 tick 进度（渲染帧间插值，上游 c）。 */
        public float prevProgress;
        /** 单 tick 最大进度（时间校验 + 理想钳制用，上游 p）。 */
        public float maxDelta;
        /** 已开始发包（上游 f）。 */
        public boolean started;
        /** 挖空气会话（上游 o）。 */
        public boolean miningAir;
        /** 完成（上游 e）。 */
        public boolean done;

        /** 进行 tick 数（上游 l）。 */
        public int ticks;
        /** 破坏包发送次数（上游 m，双阈值：RetryTicks 重置 / MiningTicks 完成）。 */
        public int breakAttempts;
        /** 持握 tick 数（上游 n）。 */
        public int holdTicksCount;
        /** 会话方块状态。 */
        public BlockState state;
        /** 最快工具。 */
        public ItemStack bestStack;
        /** 最快工具槽位（-1=无）。 */
        public int bestSlot = -1;

        Session(BlockPos pos, int priority, float speedScale) {
            this.pos = pos;
            this.dir = getFacing(pos);
            this.priority = priority;
            this.speedScale = speedScale;
        }

        /** 每 tick 推进（上游 xq）：暂停判定 → 开始 → 进度 → 破坏。 */
        void tick() {
            boolean paused = false;
            if (onlyOnGround.get() && !mc.player.isOnGround()) {
                // 站在梯子上例外（上游 xq 的 LadderBlock 分支）
                BlockState standing = mc.world.getBlockState(mc.player.getBlockPos());
                paused = !(standing.getBlock() instanceof LadderBlock);
            }
            if (pauseOnFlying.get() && mc.player.isGliding()) paused = true;
            if (pauseInFluid.get() && mc.player.isInFluid()) paused = true;

            if (!paused) {
                if (!started && !inCooldown()) start();
                if (started) progressTick();
                if (progress >= 1.0f
                    && !mc.world.getBlockState(pos).isAir()
                    && BlockUtils.canBreak(pos)) {
                    tryBreak();
                }
                holdTicksCount++;
                return;
            }

            // 暂停中：严格暂停禁止新会话；已有会话继续攒进度但不发破坏包（对齐上游 xq）
            if (!started && !strictCheck.get() && !inCooldown()) start();
            if (started) progressTick();
        }

        /** 开始会话（上游 xr）：转向 → Grim 绕过包序列 → 双重破坏。 */
        private void start() {
            state = mc.world.getBlockState(pos);

            // 上游 xr() 第一步就是 Fi(pos, range)：取不到合法破坏面或超出范围，整个 start 直接放弃
            // （此前只有 tryBreak 有范围门，START 系列包在范围外照发，6.8p11 补）
            Direction face = reachableFace(pos);
            if (face == null) return;
            dir = face;

            if (state.isAir()) {
                if (!mineAir.get()) return;
                miningAir = true;
            } else if (!BlockUtils.canBreak(pos)) {
                return;
            }

            // 「开挖转向」：上游 nJ 是当场发一个服务端视角包，再发这一整组 START/STOP，
            // 服务端处理挖掘包时已经看到正确朝向（6.8p10 用 Meteor Rotations 推到下一个移动包 = 无效，6.8p11 修）
            boolean rotated = startRotate.get() && snapLookAt(pos, dir);

            // 旧版绕过（Disabler）：START 前先发 STOP（上游 xG）
            if (grimMode.get() == GrimMode.旧版绕过) {
                sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir);
            }

            // 真实 START（上游 xF）
            sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, dir);

            // 新版绕过（NewDisabler）：补发 START(0,-999,0) 伪造包，
            // 把服务端 vanilla 1.21.2+ 快挖防护与 Grim 的挖掘位置追踪重指向假方块（上游 xE）。
            // ⚠️ 必须带正确 block-change sequence（上游 xE 是 SequencedPacketCreator，
            //    6.8p7 重写时误用裸包 sequence=0 导致 Grim 不认伪造包、STOP 被拒 = 回弹，6.8p9 修复）
            if (grimMode.get() == GrimMode.新版绕过) {
                sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(0, -999, 0), Direction.UP);
            }

            // 双重破坏：会话开始补一个 STOP（上游 xD），并建镜像会话（第二条进度线）
            if (doubleBreak.get()) {
                sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir);
                if (mirrorSession == null || mirrorSession.done) {
                    mirrorSession = new Session(pos, priority, 1.0f);
                    mirrorSession.started = true;
                }
            }

            swing();
            if (rotated) snapLookBack();
            started = true;
        }

        /** 进度推进（上游 xs）。 */
        void progressTick() {
            ticks++;
            prevProgress = progress;
            state = mc.world.getBlockState(pos);

            if (state.isAir()) {
                breakAttempts = 0;
                if (!miningAir) {
                    // 即时重破：进度不归零，继续对原位置补 STOP（上游 booleanSetting6 分支）
                    if (!instantMine.get()) {
                        started = false;
                        progress = 0;
                        prevProgress = 0;
                        ticks = 0;
                    }
                    done = true;
                }
                return;
            }

            // 最快工具：快捷栏=只看 1~9（上游 xv）；静默换持=遍历整个 ScreenHandler（上游 xw）
            if (!manual.get()) {
                if (swapAction.get() == SwapActionMode.静默换持) {
                    bestSlot = findFastestScreenSlot(state);
                    bestStack = bestSlot != -1
                        ? mc.player.currentScreenHandler.getSlot(bestSlot).getStack() : null;
                } else {
                    FindItemResult fir = InvUtils.findFastestTool(state);
                    bestSlot = fir.found() ? fir.slot() : -1;
                    bestStack = fir.found() ? mc.player.getInventory().getStack(bestSlot) : null;
                }
            }

            if (!started) return;

            // 挖掘速度预测：以原版实时公式为基线，按最快工具速度缩放，
            // 再按 CheckGround/CheckFly 旗标修正空中/鞘翅惩罚（上游 StackPlayerUtils.oQ 语义）
            float vanillaDelta = state.calcBlockBreakingDelta(mc.player, mc.world, pos);
            if (vanillaDelta <= 0) return;

            ItemStack held = mc.player.getMainHandStack();
            float heldSpeed = held.getMiningSpeedMultiplier(state);
            float bestSpeed = bestStack != null ? bestStack.getMiningSpeedMultiplier(state) : heldSpeed;
            if (heldSpeed > 0 && bestSpeed != heldSpeed) {
                vanillaDelta *= bestSpeed / heldSpeed;
            }
            vanillaDelta = SpeedMineLogic.applyStateFlags(
                vanillaDelta,
                mc.player.isOnGround(),
                mc.player.isGliding(),
                checkGround.get(),
                checkFly.get());

            progress = SpeedMineLogic.nextProgress(progress, vanillaDelta, speedScale);
            maxDelta = Math.max(maxDelta, vanillaDelta);

            // 理想进度钳制（上游 xs 尾部 L259-263）：上限 = 理想单 tick 进度 / Speed * 已进行 tick 数。
            // ⚠️ 除数 Speed 是 6.8p11 补回来的：6.8p10 写成 maxDelta * ticks（不除 Speed），
            // 于是每 tick 涨的 delta/Speed 立刻被压回 delta，进度恒等于原版进度——
            // 「挖掘进度」这个设置从 6.8p2 起就没生效过（Speed 0.7 与 1.0 行为一致）。
            float ideal = SpeedMineLogic.idealProgressBound(maxDelta, ticks, speedScale);
            if (SpeedMineLogic.clampToIdealBound(ideal, progress, miningAir)) {
                progress = ideal;
                miningAir = false;   // 上游同一处一次性清旗标（6.8p11 补；此前永不清，空气会话后续不再走 done 分支）
            }
        }

        /** 发破坏包（上游 xt）：实时面/范围门 → 时间校验 → 换持策略 → STOP。 */
        void tryBreak() {
            // 实时范围与破坏面（上游 xt 每次调 Fi(pos, range) 重算：取不到面或超范围就不发）。
            // 玩家转身/远离后仍用旧面发 STOP 会被 Grim 判非法（6.8p10 修复；6.8p11 收敛成同一个 Fi 等价物）
            Direction face = reachableFace(pos);
            if (face == null) return;
            dir = face;

            // 时间校验模式：STOP 前必须等够真实挖掘时间（上游 xt 的 FX 分支）。
            // ⚠️ 基准是上游的 j = 本次会话的 START 时刻，不是「上次 STOP」：6.8p10 拿 lastStopTime
            // 当基准，等于用上一轮的结束时间给这一轮凑数，可能直接跳过诚实等待（6.8p11 修复）
            if (grimMode.get() == GrimMode.时间校验) {
                double expected = Math.ceil(1.0 / Math.max(maxDelta, 1e-6)) * 50.0;
                if (!SpeedMineLogic.timeModeStopAllowed(
                    expected, System.currentTimeMillis() - lastStartTime, TIME_MODE_SLACK_MS)) {
                    return;
                }
            }

            ItemStack held = mc.player.getMainHandStack();

            if (!manual.get() && bestSlot != -1
                && (bestStack == null || !ItemStack.areEqual(bestStack, held))) {
                if (swapAction.get() == SwapActionMode.静默换持) {
                    // 静默换持：ClickSlot(SWAP) 换上 → 发 STOP → 换回，包序与直切一致；
                    // 不发 UpdateSelectedSlot，选栏位不变（上游 SwapMode.Screen）
                    int slotId = bestSlot;
                    int button = mc.player.getInventory().getSelectedSlot();
                    int syncId = mc.player.currentScreenHandler.syncId;
                    screenSwap(slotId, button);
                    breakAttempts++;
                    sendStop();
                    swing();
                    if (hold.get()) {
                        // 保持换持：不切回，切回时机交给 onTick 里的 shouldKeepHolding（上游 Hold/xB + syncId 守卫）
                        revertScreenSlot = slotId;
                        revertScreenButton = button;
                        revertScreenSyncId = syncId;
                        revertSlot = -1;
                    } else {
                        holdTicksCount = 0;   // 上游 xt L309：这次已经把工具换回来了
                        screenSwap(slotId, button);
                    }
                } else if (!hold.get()) {
                    // 直切：换上→发 STOP→自动换回（上游 ba/xA/bb）
                    holdTicksCount = 0;   // 上游 xt L309
                    InvUtils.swap(bestSlot, true);
                    breakAttempts++;
                    sendStop();
                    swing();
                } else {
                    // 保持换持：换上不切回，切回时机交给 onTick 里的 shouldKeepHolding（上游 Hold/xB 分支）
                    revertSlot = mc.player.getInventory().getSelectedSlot();
                    InvUtils.swap(bestSlot, false);
                    breakAttempts++;
                    sendStop();
                    swing();
                }
            } else {
                holdTicksCount = 0;   // 上游 xt L347：手上已经是合适工具
                breakAttempts++;
                sendStop();
                swing();
            }

            // 重试等待：重试超限，整个会话重来（上游 xt 尾部）
            if (breakAttempts > retryTicks.get() && (!instantMine.get() || !done)) {
                started = false;
                ticks = 0;
                breakAttempts = 0;
                progress = 0;
                prevProgress = 0;
            }
        }

        /** 渲染（上游 alz）：缓动 + 帧间插值 + 盒子收缩 + 形状贴合 + 四色渐变。 */
        void render(Render3DEvent event) {
            // 帧间平滑：上一 tick 进度与当前进度按 tickDelta 插值，再过缓动曲线（上游 c+(d-c)*f）
            double raw = prevProgress + (progress - prevProgress) * event.tickDelta;
            double eased = easing.get().apply(Math.max(0, Math.min(1, raw)));

            Box box;
            if (boxAnimation.get()) {
                // 形状贴合：用方块真实轮廓（火是扁的、台阶是半格，上游 getOutlineShape）
                Box base;
                if (blockOutline.get()) {
                    VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
                    base = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
                } else {
                    base = new Box(pos);
                }
                // 盒子动画：随缓动值向中心收缩（上游 BoxAnimation）
                Vec3d center = base.getCenter();
                double hx = base.getLengthX() / 2.0 * eased;
                double hy = base.getLengthY() / 2.0 * eased;
                double hz = base.getLengthZ() / 2.0 * eased;
                box = new Box(center.subtract(hx, hy, hz), center.add(hx, hy, hz));
            } else {
                box = new Box(pos);
            }

            int[] side = SpeedMineLogic.lerpColor(toRgba(startColor.get()), toRgba(endColor.get()), eased);
            int[] line = SpeedMineLogic.lerpColor(toRgba(startLineColor.get()), toRgba(endLineColor.get()), eased);

            event.renderer.box(box,
                new SettingColor(side[0], side[1], side[2], side[3]),
                new SettingColor(line[0], line[1], line[2], line[3]),
                boxMode.get().toShapeMode(), 0);
        }

        private void sendStop() {
            // 上游 xz/xA/xC：Desync 的客户端处理在**构造 STOP 包之前**执行
            // （「正常破坏」那一支自己也会发包，与 STOP 的先后在线上有区别）；
            // 时钟记账统一交给 sendDig（6.8p11）
            markBreak(pos);
            // 「结束转向」：与上游 nJ 一样当场发视角包，让服务端在处理这条 STOP 前就看到新朝向
            boolean rotated = endRotate.get() && snapLookAt(pos, dir);
            sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir);
            if (rotated) snapLookBack();
        }
    }

    // ── 原版加速（Damage）模式 ──

    private void damageTick() {
        net.minecraft.client.network.ClientPlayerInteractionManager im = mc.interactionManager;
        if (im == null) return;
        com.fo.addon.mixin.ClientPlayerInteractionManagerAccessor acc =
            (com.fo.addon.mixin.ClientPlayerInteractionManagerAccessor) im;

        // 无冷却：清掉破块后的挖掘冷却（上游 MiningCooldownFixEvent 语义）
        if (noDelay.get() && acc.getBlockBreakingCooldown() > 0) {
            acc.setBlockBreakingCooldown(0);
        }

        ItemStack held = mc.player.getMainHandStack();

        // 不重置：换手物品后恢复进度快照（上游 StackEvent 语义）
        if (noReset.get()
            && lastHeldStack != null
            && !ItemStack.areEqual(held, lastHeldStack)
            && lastDamageProgress > 0
            && im.getBlockBreakingProgress() <= 0) {
            acc.setCurrentBreakingProgress(lastDamageProgress);
        }
        lastHeldStack = held;

        // 核心一步：原版进度达到 Speed 阈值 → 直接置满，原版同 tick 内发包破坏
        // （上游 ordinal==0 分支的 hookSetCurrentBreakingProgress(1.0F)）
        float prog = im.getBlockBreakingProgress();
        lastDamageProgress = prog;
        if (prog >= speed.get().floatValue() && prog < 1.0f) {
            acc.setCurrentBreakingProgress(1.0f);
        }
    }

    // ── 辅助 ──

    /** 从准星取破坏面；没看着该方块时取离眼睛最近的面（上游 PlacementUtils.Fh 简化等价）。 */
    private Direction getFacing(BlockPos pos) {
        if (mc.crosshairTarget instanceof BlockHitResult hit
            && hit.getBlockPos().equals(pos)
            && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return hit.getSide();
        }
        Vec3d eye = mc.player.getEyePos();
        Vec3d c = pos.toCenterPos();
        double dx = eye.x - c.x;
        double dy = eye.y - c.y;
        double dz = eye.z - c.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * 上游 {@code PlacementUtils.Fi(pos, range)} 的等价物：取破坏面并校验
     * 「该面中心到眼睛」在挖掘范围内；取不到面或超范围返回 null。
     * 上游所有发包点（xr/xt/xB）都走这一道门，FO 此前只在 tryBreak 用「方块中心距离」近似。
     */
    private Direction reachableFace(BlockPos pos) {
        Direction face = getFacing(pos);
        return mc.player.getEyePos().distanceTo(faceCenter(pos, face)) <= range.get() ? face : null;
    }

    /** 面中心 = 方块中心 + 面法向量的一半（上游 {@code PlacementUtils.Fn}）。 */
    private static Vec3d faceCenter(BlockPos pos, Direction face) {
        var normal = face.getVector();
        return new Vec3d(pos.getX() + 0.5 + normal.getX() * 0.5,
            pos.getY() + 0.5 + normal.getY() * 0.5,
            pos.getZ() + 0.5 + normal.getZ() * 0.5);
    }

    /**
     * 瞬转发一个<b>服务端</b>视角包对准该面的面中心（上游 {@code RotationUtils.nJ → MovementTracker.Mj}：
     * 它当场发 {@code PlayerMoveC2SPacket}，之后才发挖掘包，所以服务端处理 START/STOP 时已经看到正确朝向）。
     *
     * <p>⚠️ 6.8p10 用的是 Meteor 的 {@code Rotations.rotate(yaw, pitch, null)}——它把新朝向推到
     * 「下一个移动包」，而挖掘包当场就发了（本仓库 {@code BlockClicker} 头注释写的就是这个差别），
     * 于是「开挖转向 / 结束转向」两个设置对服务端完全不生效（6.8p11 修复）。</p>
     *
     * <p>与上游的另一处刻意差异：上游把转回的活儿丢给别的模块/后续移动包，
     * FO 学本仓库 {@code InteractionUtils}/{@code AutoTree} 的做法——发包方自己立刻转回，不外溢状态。</p>
     */
    private boolean snapLookAt(BlockPos pos, Direction face) {
        if (mc.player == null || mc.getNetworkHandler() == null) return false;
        Vec3d point = faceCenter(pos, face);
        sendLook((float) Rotations.getYaw(point), (float) Rotations.getPitch(point));
        return true;
    }

    /** 转回客户端真实朝向（上游 {@code MovementTracker.Mk}）。 */
    private void snapLookBack() {
        if (mc.player == null) return;
        sendLook(mc.player.getYaw(), mc.player.getPitch());
    }

    private void sendLook(float yaw, float pitch) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    /** 目标有效性：过滤模式 + 忽略已破坏。 */
    private boolean isValidTarget(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (ignoreBroken.get() && state.isAir()) return false;
        Block block = state.getBlock();
        return switch (filterMode.get()) {
            case 全部 -> true;
            case 仅黑名单 -> !blacklist.get().contains(block);
            case 仅白名单 -> whitelist.get().contains(block);
        };
    }

    private boolean inCooldown() {
        return grimMode.get() != GrimMode.无
            && grimMode.get() != GrimMode.旧版绕过
            && SpeedMineLogic.inSessionCooldown(
                System.currentTimeMillis(), lastStopTime, SESSION_COOLDOWN_MS);
    }

    /**
     * 挖掘包统一带正确的 block-change sequence（复用 fo.accesswidener 的 GrimV3 条目），
     * 并在这里集中记两个时钟——对齐上游 onSendImmediately（BlockPosBoxMultitask L441-453）：
     * ① 每个 <b>STOP</b> 包武装 280ms 会话冷却时钟（上游 i），所以 DoubleBreak 与会话开始时
     *    那两发 STOP 同样算数；② 每个 <b>START</b> 包（含 (0,-999,0) 伪造包）重置时间校验基准
     *    （上游 j），且非「时间校验」模式下 STOP 也顺带重置它。
     * 6.8p10 只有一个 lastStopTime 且只在 tryBreak 那一发 STOP 记账，两个时钟被并成了一个。
     */
    private void sendDig(PlayerActionC2SPacket.Action action, BlockPos pos, Direction dir) {
        var pending = mc.world.getPendingUpdateManager().incrementSequence();
        int seq = pending.sequence;
        pending.close();
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(action, pos, dir, seq));

        long now = System.currentTimeMillis();
        if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            lastStartTime = now;
        } else if (action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            lastStopTime = now;
            // 上游：mode != Time 时 j = i（FO 把 Strict/Time 合并成「时间校验」，这里按 Time 语义不重置）
            if (grimMode.get() != GrimMode.时间校验) lastStartTime = now;
        }
    }

    private void swing() {
        // 实战 Swing=None：默认不挥手（上游 SwingMode.avX 由设置驱动）
        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** 发包破坏后的统一收尾：假破块 + 聊天提示（上游 xz/xA/xC 的 Desync 分支）。 */
    private void markBreak(BlockPos pos) {
        switch (desync.get()) {
            case 正常破坏 -> mc.interactionManager.breakBlock(pos);
            case 假破块 -> mc.world.removeBlock(pos, false);
            case 无 -> {
            }
        }
        if (notify.get()) {
            info("发包挖掘：已破坏 %d %d %d", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private void placeEnderChest(BlockPos pos) {
        if (mc.player.getEyePos().distanceTo(pos.toCenterPos()) > placeRange.get()) return;
        FindItemResult fir = InvUtils.find(Items.ENDER_CHEST);
        if (fir.found()) {
            BlockUtils.place(pos, fir, placeRotate.get(), 0);
        }
    }

    private static int[] toRgba(SettingColor c) {
        return new int[]{c.r, c.g, c.b, c.a};
    }
}
