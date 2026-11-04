package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.PacketMineLogic;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 发包挖掘（移植自 Hachimi V2 反编译源码的 AutoMine）。
 *
 * <p>来源：{@code pw.hachimi.client.module.combat.AutoMine}（Hachimi-V2-crack.jar 去混淆重构版）。
 * 目标方块计算抽到 {@link PacketMineLogic}（纯逻辑，可单测）；实际挖掘走彗星端（Meteor）
 * 官方 {@link BlockUtils#breakBlock(BlockPos, boolean)}（自动旋转 + 切槽 + 发包），不用自写 Mixin。</p>
 *
 * <p>行为（与 Hachimi 原版对齐的部分）：
 * <ul>
 *   <li>选目标：范围内最近的存活真人玩家（跳过自己/创造模式/好友，默认排序最近）。</li>
 *   <li>Burrow：挖目标脚里的方块（±0.25 四角，覆盖跨边界站立）。</li>
 *   <li>Face：挖目标头顶的方块。</li>
 *   <li>Surround：挖目标脚下包的围城方块；「检查支撑」开启时只挖
 *       下面是黑曜石/基岩的邻位（对齐原版 {@code ary()} 的支撑判定）。</li>
 *   <li>白名单/黑名单/全部 三档过滤，默认仅黑名单（3C3U 实战名单，见下）。</li>
 *   <li>好友同步：好友正在挖的方块跳过（原版 FriendSync，依赖的"谁在挖哪格"状态
 *       彗星端拿不到，移植版降级为不做——见「与原版差异」）。</li>
 * </ul></p>
 *
 * <p>默认参数对齐 3C3U 实战配置（cheng.json AutoMine 段实测值）：目标范围 6、挖掘范围 6、
 * 仅黑名单模式（梯子/基岩/脚手架/火/蜘蛛网/15 色床/重生锚）、排序最近角度、
 * 挖脚里+挖围城开、挖头顶关、聊天提示开。CheckSelf/FriendSync/Priority 为上游
 * Multitask 体系专属，不适用本移植版（见下方差异说明）。</p>
 *
 * <p>与原版的刻意差异（反编译语义无法完全确认的部分，保守处理）：
 * <ul>
 *   <li>原版 Surround 邻位候选包含"空气位"（依赖其私有 Multitask 的放置/挖掘复用逻辑），
 *       移植版只挖实心可破坏方块——挖空气无意义。</li>
 *   <li>原版 FriendSync 需要全服玩家的挖掘状态表，彗星端无此数据，不实现。</li>
 *   <li>原版 Wait/双任务并发（Multitask 同时挖两格）未移植，本模块每 tick 只发一次包、
 *       一个会话只挖一格，靠「重复发包间隔」保证速度——行为简单可预期。</li>
 * </ul></p>
 */
public class FOPacketMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("目标选择");
    private final SettingGroup sgMine = settings.createGroup("挖掘");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // 目标选择

    private final Setting<Double> targetRange = sgTarget.add(new DoubleSetting.Builder()
        .name("目标范围").description("搜索目标玩家的最大距离")
        .defaultValue(6).min(1).sliderMax(16).build()
    );

    private final Setting<SortPriority> sortPriority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
        .name("排序方式").description("多个目标时优先攻击谁")
        .defaultValue(SortPriority.ClosestAngle).build()
    );

    private final Setting<Boolean> ignoreCreative = sgTarget.add(new BoolSetting.Builder()
        .name("忽略创造模式").description("跳过创造模式的玩家")
        .defaultValue(true).build()
    );

    // 挖掘

    private final Setting<Double> mineRange = sgMine.add(new DoubleSetting.Builder()
        .name("挖掘范围").description("离目标方块超过该距离就不挖（原版 MineRange）")
        .defaultValue(6).min(1).sliderMax(8).build()
    );

    private final Setting<Boolean> mineBurrow = sgMine.add(new BoolSetting.Builder()
        .name("挖脚里").description("挖目标玩家脚里的方块（Burrow）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> mineFace = sgMine.add(new BoolSetting.Builder()
        .name("挖头顶").description("挖目标玩家头顶的方块（Face）")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> mineSurround = sgMine.add(new BoolSetting.Builder()
        .name("挖围城").description("挖目标玩家脚下的围城方块（Surround）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> checkSupport = sgMine.add(new BoolSetting.Builder()
        .name("检查支撑").description("围城方块只挖下面是黑曜石/基岩的（对齐原版支撑判定）")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> pauseOnUse = sgMine.add(new BoolSetting.Builder()
        .name("使用物品时暂停").description("正在吃食物/拉弓等使用物品动作时暂停挖掘")
        .defaultValue(true).build()
    );

    private final Setting<Integer> resendInterval = sgMine.add(new IntSetting.Builder()
        .name("重复发包间隔").description("对同一格重复发挖掘包的间隔（tick）。1=每 tick 都发")
        .defaultValue(2).min(1).sliderMax(10).build()
    );

    private final Setting<Boolean> swing = sgMine.add(new BoolSetting.Builder()
        .name("挥手").description("发包挖掘时挥手")
        .defaultValue(true).build()
    );

    private final Setting<FilterMode> filterMode = sgMine.add(new EnumSetting.Builder<FilterMode>()
        .name("过滤模式").description("目标方块的过滤规则")
        .defaultValue(FilterMode.仅黑名单).build()
    );

    private final Setting<Boolean> notify = sgMine.add(new BoolSetting.Builder()
        .name("聊天提示").description("锁定新目标时在聊天栏提示（对齐原版 Notify）")
        .defaultValue(true).build()
    );

    /** 黑名单默认值：照搬 3C3U 实战配置（cheng.json AutoMine.Blacklist）。 */
    private static final Block[] DEFAULT_BLACKLIST = {
        Blocks.LADDER, Blocks.BEDROCK, Blocks.SCAFFOLDING, Blocks.FIRE, Blocks.COBWEB,
        Blocks.ORANGE_BED, Blocks.MAGENTA_BED, Blocks.LIGHT_BLUE_BED, Blocks.YELLOW_BED,
        Blocks.LIME_BED, Blocks.PINK_BED, Blocks.GRAY_BED, Blocks.LIGHT_GRAY_BED,
        Blocks.CYAN_BED, Blocks.PURPLE_BED, Blocks.BLUE_BED, Blocks.BROWN_BED,
        Blocks.GREEN_BED, Blocks.RED_BED, Blocks.BLACK_BED, Blocks.RESPAWN_ANCHOR
    };

    private final Setting<List<Block>> blacklist = sgMine.add(new BlockListSetting.Builder()
        .name("黑名单").description("过滤模式下不挖的方块")
        .defaultValue(DEFAULT_BLACKLIST)
        .visible(() -> filterMode.get() != FilterMode.仅白名单)
        .build()
    );

    /** 白名单默认值：照搬 3C3U 实战配置（cheng.json AutoMine.Whitelist）。 */
    private final Setting<List<Block>> whitelist = sgMine.add(new BlockListSetting.Builder()
        .name("白名单").description("过滤模式下只挖的方块")
        .defaultValue(Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
        .visible(() -> filterMode.get() == FilterMode.仅白名单)
        .build()
    );

    // 渲染

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("高亮").description("在游戏里画出正在挖的方块")
        .defaultValue(true).build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式").description("高亮的线框/填充方式")
        .defaultValue(ShapeMode.Both).build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("填充颜色").description("高亮的填充颜色")
        .defaultValue(new SettingColor(225, 57, 57, 40)).build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色").description("高亮的线条颜色")
        .defaultValue(new SettingColor(225, 57, 57, 200)).build()
    );

    /** 当前目标（HUD 信息用）。 */
    private PlayerEntity target;
    /** 当前会话正在挖的方块；null = 没有在挖。 */
    private BlockPos current;
    /** 上一次发包的方块（会话标识）。 */
    private BlockPos lastPos;
    /** 当前会话已发包次数（超时放弃换下一格）。 */
    private int packets;
    /** 重复发包计数器。 */
    private int resendTimer;
    /** 上一次聊天提示过的目标名（避免每 tick 刷屏）。 */
    private String lastNotifyName;

    public FOPacketMine() {
        super(AddonTemplate.CATEGORY, "FO 发包挖掘", "移植自 Hachimi V2 AutoMine：自动发包挖目标玩家的脚里(Burrow)/头顶(Face)/围城(Surround)方块。");
    }

    @Override
    public void onActivate() {
        target = null;
        current = null;
        packets = 0;
        resendTimer = 0;
        lastNotifyName = null;
    }

    @Override
    public void onDeactivate() {
        target = null;
        current = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        target = null;
        current = null;

        // 使用物品时暂停（对齐原版 breakSetting/useItem 守卫）
        if (pauseOnUse.get() && mc.player.isUsingItem()) return;

        // 选目标（跳过自己/好友/创造/死人，范围内按排序取第一）
        // 注：彗星端 TargetUtils.getPlayerTarget 返回 LivingEntity，但语义就是玩家；
        //     PlayerUtils.isAlive() 不带参（只查自己），实体用 isAlive()
        LivingEntity lt = TargetUtils.getPlayerTarget(targetRange.get(), sortPriority.get());
        if (lt == null || !lt.isAlive()) return;
        PlayerEntity t = (PlayerEntity) lt;
        if (ignoreCreative.get() && t.isCreative()) return;
        if (Friends.get().isFriend(t)) return;
        target = t;

        // 锁定新目标时提示一次（对齐原版 Notify）
        if (notify.get()) {
            String name = t.getName().getString();
            if (!name.equals(lastNotifyName)) {
                info("发包挖掘目标: " + name);
                lastNotifyName = name;
            }
        }

        // 收集候选方块
        List<BlockPos> candidates = collectCandidates(t);
        if (candidates.isEmpty()) return;

        BlockPos best = PacketMineLogic.closest(candidates, mc.player.getX(), mc.player.getZ());
        if (best == null) return;

        // 距离守卫（对齐原版 MineRange：眼睛到方块中心的直线距离）
        double dist = mc.player.getEyePos().distanceTo(best.toCenterPos());
        if (dist > mineRange.get()) return;

        if (!BlockUtils.canBreak(best)) return;

        // 会话：同一格按间隔重复发包，直到方块消失或超时
        if (best.equals(lastPos) && !sessionExpired()) {
            current = lastPos;
            if (++resendTimer < resendInterval.get()) return;
            resendTimer = 0;
        } else {
            // 换了新格子，开新会话
            lastPos = best;
            packets = 0;
            resendTimer = 0;
            current = best;
        }

        BlockUtils.breakBlock(best, swing.get());
        packets++;
    }

    /** 会话超时：同一格发了太多包还没挖掉，视为打不动（黑曜石没工具/伪 obsidian），放弃。 */
    private boolean sessionExpired() {
        return packets >= 40;
    }

    private List<BlockPos> collectCandidates(LivingEntity t) {
        List<BlockPos> out = new ArrayList<>();

        if (mineBurrow.get()) {
            for (BlockPos pos : PacketMineLogic.burrowCandidates(t.getX(), t.getY(), t.getZ())) {
                if (isMineableTarget(pos)) out.add(pos);
            }
        }

        if (mineFace.get()) {
            for (BlockPos pos : PacketMineLogic.faceCandidates(t.getX(), t.getY(), t.getZ())) {
                if (isMineableTarget(pos)) out.add(pos);
            }
        }

        if (mineSurround.get()) {
            double width = t.getWidth();
            for (BlockPos pos : PacketMineLogic.surroundCandidates(t.getX(), t.getY(), t.getZ(), width)) {
                if (checkSupport.get() && !hasUnbreakableSupport(pos)) continue;
                if (isMineableTarget(pos)) out.add(pos);
            }
        }

        return out;
    }

    /**
     * 目标方块过滤：非空气、无流体、过白/黑名单（对齐原版 {@code arC()}）。
     */
    private boolean isMineableTarget(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;

        switch (filterMode.get()) {
            case 仅黑名单: return !blacklist.get().contains(block);
            case 仅白名单: return whitelist.get().contains(block);
            default: return true;
        }
    }

    /** 支撑判定（对齐原版 {@code ary()}）：下面一格是黑曜石或基岩才允许挖。 */
    private boolean hasUnbreakableSupport(BlockPos pos) {
        Block below = mc.world.getBlockState(pos.down()).getBlock();
        return below == Blocks.BEDROCK || below == Blocks.OBSIDIAN;
    }

    @Override
    public String getInfoString() {
        return target != null ? target.getName().getString() : null;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || current == null) return;
        if (mc.world.getBlockState(current).isAir()) return;
        Box box = new Box(current);
        event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    public enum FilterMode {
        全部, 仅黑名单, 仅白名单
    }
}
