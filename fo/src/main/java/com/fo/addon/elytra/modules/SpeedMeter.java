package com.fo.addon.elytra.modules;

import com.fo.addon.elytra.FOElytraModule;
import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.SettingHelper;
import com.fo.addon.elytra.core.SpeedTracker;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;

/**
 * 实时平均速度 —— 把「现在跑多快」变成一个能随时看的数字。
 *
 * <h2>为什么不是「当前速度」就完事</h2>
 * 鞘翅飞行时 {@code vy/vx 的瞬时值} 每 tick 都在跳（放烟花那一下、撞上升气流、被火球打歪…），
 * 盯着看没有意义。所以这个模块给的是<b>滑动窗口平均</b>（默认 5 秒）：
 * <ul>
 *   <li><b>平均水平速度</b>（格/秒 / 公里每小时 / 格每刻）—— 跑图真正该看的数，Baritone 的
 *       {@code elytraFireworkSpeed} 也是拿这个量级的数做阈值的；</li>
 *   <li><b>垂直 / 三维速度</b> —— 爬升时三维会明显大于水平，能看出「在升还是在平飞」；</li>
 *   <li><b>瞬时速度 + 峰值</b> —— 想知道「刚才那一下有多快」时用；</li>
 *   <li><b>累计路程 / 净位移 / 已用时 / 全程平均</b> —— 净位移远小于路程就是<b>在原地打转</b>（配合主模块的
 *       「卡住自救」看，能提前发现绕圈）；</li>
 *   <li><b>ETA</b> —— 给个距离，它按窗口平均算还要多久（主模块的距离数可以直接丢进来）。</li>
 * </ul>
 *
 * <h2>HUD</h2>
 * 两条路都有：
 * <ol>
 *   <li>模块列表里的那一行（{@link #getInfoString()}）—— 打开「HUD → 活动模块」就能看到，无需配置；</li>
 *   <li>独立的 <b>HUD 元素「实时速度」</b>（{@code hud/SpeedHud.java}）—— 在 HUD 编辑器里随便摆位置，
 *       可以只显示你想要的那几行、独立设置精度/单位/缩放。<b>它不依赖本模块开着</b>，数据是同一个 {@link SpeedTracker}。</li>
 * </ol>
 */
public class SpeedMeter extends FOElytraModule {
    public enum Unit {
        /** 格/秒（原版单位，最好对照 Baritone 设置）。 */
        BlocksPerSecond("格/秒"),
        /** 公里/小时（看着直观，1 格 = 1 米）。 */
        Kmh("公里/小时"),
        /** 格/刻（看物理量级时用；20 刻 = 1 秒）。 */
        BlocksPerTick("格/刻");

        public final String label;

        Unit(String label) {
            this.label = label;
        }

        /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString，必须覆写否则显示英文枚举名） */
        @Override
        public String toString() {
            return label;
        }
    }

    public enum EtaSpeed {
        实时平均,
        时段平均
    }

    private final SettingGroup sgSpeed = settings.createGroup("速度");
    private final SettingGroup sgSegment = settings.createGroup("时段平均");
    private final SettingGroup sgWaypoint = settings.createGroup("航点剩余");
    private final SettingGroup sgReset = settings.createGroup("重置");
    private final SettingGroup sgDebug = settings.createGroup("调试");

    private final DoubleSetting windowSeconds = SettingHelper.double_(sgSpeed, "平均窗口（秒）",
        "用最近多少秒算平均。太小会乱跳（放个烟花就飙一下），太大会把之前地面走路/补给的时间也算进来。\n"
            + "跑图推荐 5 秒；测瞬时性能可以调到 1 秒。", 5.0, 0.5, 60.0);

    private final EnumSetting<Unit> unit = SettingHelper.enum_(sgSpeed, "单位",
        "格/秒 = 原版单位（1 格 = 1 米）；公里/小时 = 格/秒 × 3.6；格/刻 = 格/秒 ÷ 20（20 刻 = 1 秒）。",
        Unit.BlocksPerSecond);

    private final IntSetting decimals = SettingHelper.int_(sgSpeed, "小数位",
        "显示保留几位小数。", 1, 0, 3);

    private final BoolSetting showVertical = SettingHelper.bool(sgSpeed, "显示垂直速度",
        "额外显示垂直分量（爬升/俯冲时有用）。", false);

    private final BoolSetting showInstant = SettingHelper.bool(sgSpeed, "显示瞬时速度",
        "最近一次采样算出来的速度（会跳，但能看出「刚才那一下」）。", true);

    private final BoolSetting showPeak = SettingHelper.bool(sgSpeed, "显示峰值",
        "本次运行以来的最高水平速度。", true);

    private final BoolSetting showTotals = SettingHelper.bool(sgSpeed, "显示累计与全程平均",
        "累计路程 / 净位移 / 已用时 / 全程平均（净位移远小于路程 = 在原地打转）。", true);

    private final BoolSetting showEta = SettingHelper.bool(sgSpeed, "显示 ETA（还要多久）",
        "按窗口平均速度算「跑完「ETA 参照距离」还要多久」，给个直观的时间感。", false);

    private final IntSetting etaDistance = SettingHelper.int_(sgSpeed, "ETA 参照距离（格）",
        "ETA 用这个距离算（例如你到目标的距离）。换成 km/h 单位时这里也按格算，不影响。", 1000, 1, 1000000);

    private final IntSetting segmentSeconds = SettingHelper.int_(sgSegment, "时段长度（秒）",
        "把计时切成固定长度的时段，每段结束结算这一段走了多少格、平均多快（用游戏 tick 计时，卡顿不会把数据算歪）。", 30, 5, 600);

    private final BoolSetting segmentChat = SettingHelper.bool(sgSegment, "时段结束时报到聊天栏",
        "每段结束时在聊天栏打一行结算，文件日志里另有一份含三维平均与净位移的详细数据。", true);

    private final BoolSetting showSegment = SettingHelper.bool(sgSegment, "显示本段进度",
        "显示当前这一段的进行中平均，例如「本段 12.3/30.0 秒｜均 41.1 格/秒」。", true);

    private final BoolSetting showSegmentDetail = SettingHelper.bool(sgSegment, "显示本段路程与直线",
        "额外显示这一段走了多少格、以及起点到现在的直线距离（两者差得多说明在绕路）。", false);

    private final BoolSetting waypointEta = SettingHelper.bool(sgWaypoint, "按 Baritone 航点算剩余时间",
        "把 Baritone 当前路径剩下的航点累加成剩余路程，除以当前平均速度算出还要跑多久。", true);

    private final EnumSetting<EtaSpeed> etaSpeed = SettingHelper.enum_(sgWaypoint, "航点时间用哪个速度",
        "实时平均 = 用滑动窗口平均，立刻有值；时段平均 = 用最近一段完整时段的平均，还没结算完就显示等结算。", EtaSpeed.实时平均);

    private final BoolSetting showWaypointEta = SettingHelper.bool(sgWaypoint, "显示航点剩余时间",
        "显示「航点剩余 1234 格 → 约 1 分 23 秒」（鞘翅飞行时 Baritone 不给航点，按到目的地的直线估）。", true);

    private final KeybindSetting resetKey = SettingHelper.keybind(sgReset, "重置键",
        "按一下把累计与速度统计清零（换一段航程时用）。");

    private final BoolSetting resetOnTeleport = SettingHelper.bool(sgReset, "传送时自动重置窗口",
        "位置跳变超过 32 格（/tp、换维度、服务器拉人）时清空滑动窗口 —— 否则那一下会被算成几千格每秒。"
            + "累计路程不受影响（传送本来就不计入）。", true);

    private final BoolSetting hudLine = SettingHelper.bool(sgDebug, "在模块列表显示",
        "在「HUD → 活动模块」那一行里显示速度（等价于独立 HUD 元素，二选一或都要）。", true);

    private final BoolSetting debugMessages = SettingHelper.bool(sgDebug, "调试输出",
        "把每次采样/重置/传送检测都打出来（很吵，排障用）。", false);

    private boolean resetKeyWasPressed;
    private WLabel liveLabel;
    private WLabel segmentLabel;
    private WLabel waypointLabel;

    public SpeedMeter() {
        super("FO 实时平均速度",
            "滑动窗口平均速度（水平/垂直/三维）+ 瞬时/峰值/累计/ETA；带独立 HUD 元素，可随意摆放");
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    public void onActivate() {
        SpeedTracker.setWindowSeconds(windowSeconds.get());
        SpeedTracker.setSegmentSeconds(segmentSeconds.get());
        SpeedTracker.setSegmentChat(segmentChat.get());
        SpeedTracker.reset();
        resetKeyWasPressed = false;
        FOElytraLog.info("实时平均速度：窗口 %.1f 秒｜时段 %.0f 秒｜单位 %s｜小数 %d 位",
            windowSeconds.get(), SpeedTracker.segmentLengthSeconds(), SpeedTracker.unitName(unit.get().ordinal()), decimals.get());
        FOElytraLog.detail("想要独立 HUD：HUD 编辑器里找「实时速度」元素（不依赖本模块是否开着）");
    }

    @Override
    public void onDeactivate() {
        // 故意不 reset：关掉模块后 HUD 元素还能继续显示（数据是共享的）
        FOElytraLog.detail("实时平均速度关闭（累计数据保留给 HUD 元素用）");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;
        FOElytraLog.debugEnabled = debugMessages.get();

        SpeedTracker.setWindowSeconds(windowSeconds.get());   // 允许运行中改窗口
        SpeedTracker.setSegmentSeconds(segmentSeconds.get());
        SpeedTracker.setSegmentChat(segmentChat.get());
        boolean fresh = SpeedTracker.sample();
        if (fresh && debugMessages.get()) {
            FOElytraLog.debug("水平 %.2f 格/秒｜垂直 %.2f｜三维 %.2f｜瞬时 %.2f｜累计 %.1f 格",
                SpeedTracker.horizontalSpeed(), SpeedTracker.verticalSpeed(),
                SpeedTracker.threeDSpeed(), SpeedTracker.instantHorizontalSpeed(),
                SpeedTracker.totalPath());
        }

        // 重置键（上升沿）
        boolean pressed = resetKey.get() != null && resetKey.get().isPressed();
        if (pressed && !resetKeyWasPressed) {
            SpeedTracker.reset();
            FOElytraLog.info("速度统计已重置（当前时段也清零重开）");
        }
        resetKeyWasPressed = pressed;

        if (liveLabel != null && mc.player.age % 5 == 0) liveLabel.set(oneLine());
        if (segmentLabel != null && mc.player.age % 5 == 0) segmentLabel.set(segmentInfo());
        if (waypointLabel != null && mc.player.age % 5 == 0) waypointLabel.set(waypointLine());
    }

    // ------------------------------------------------------------------ 显示

    /** 模块列表里那一行（HUD → 活动模块）。 */
    @Override
    public String getInfoString() {
        if (!hudLine.get() || mc.player == null) return null;
        return oneLine();
    }

    /** 一行紧凑文本，给模块列表用。 */
    private String oneLine() {
        StringBuilder sb = new StringBuilder(fmt(SpeedTracker.horizontalSpeed()));
        if (showVertical.get()) sb.append("/").append(fmt(SpeedTracker.verticalSpeed()));
        if (showInstant.get()) sb.append(" ↑").append(fmt(SpeedTracker.instantHorizontalSpeed()));
        if (showPeak.get()) sb.append(" 峰").append(fmt(SpeedTracker.peakHorizontalSpeed()));
        if (showSegment.get()) sb.append("｜").append(segmentLine());
        String w = waypointLine();
        if (!w.isEmpty()) sb.append("｜").append(w);
        return sb.toString();
    }

    private String segmentLine() {
        return String.format("本段 %.1f/%.0f 秒｜均 %s", SpeedTracker.segmentElapsedSeconds(),
            SpeedTracker.segmentLengthSeconds(), fmt(SpeedTracker.segmentAverageHorizontal()));
    }

    private String segmentInfo() {
        if (!showSegment.get()) return "";
        String s = segmentLine();
        if (showSegmentDetail.get()) {
            s += String.format("｜路程 %.0f 格｜直线 %.0f 格", SpeedTracker.segmentPath3d(),
                SpeedTracker.segmentDirectDistance());
        }
        return s;
    }

    public boolean etaUseSegmentAverage() {
        return etaSpeed.get() == EtaSpeed.时段平均;
    }

    private String waypointLine() {
        if (!waypointEta.get() || !showWaypointEta.get()) return "";
        BaritoneHook.WaypointRoute route = BaritoneHook.waypointRoute();
        if (route == null) return "";
        String head = String.format("航点剩余 %.0f 格%s → ", route.distance(),
            route.elytraStraight() ? "（鞘翅直线）" : "");
        if (etaSpeed.get() == EtaSpeed.时段平均) {
            double speed = SpeedTracker.lastSegmentAverageHorizontal();
            if (speed < 0) {
                return head + String.format("等时段结算（还有 %.1f 秒）", SpeedTracker.segmentRemainingSeconds());
            }
            if (speed < 0.1) return head + "—（这段没动）";
            return head + "约 " + SpeedTracker.timeText(route.distance() / speed) + "（时段）";
        }
        double eta = SpeedTracker.etaSeconds(route.distance());
        return head + (eta < 0 ? "—（速度太低）" : "约 " + SpeedTracker.timeText(eta) + "（实时）");
    }

    /** 多行详情（模块设置页里的实时标签 + HUD 元素共用同一套格式）。 */
    public java.util.List<String> detailLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("水平 " + fmt(SpeedTracker.horizontalSpeed()));
        if (showVertical.get()) {
            out.add("垂直 " + fmt(SpeedTracker.verticalSpeed()));
            out.add("三维 " + fmt(SpeedTracker.threeDSpeed()));
        }
        if (showInstant.get()) out.add("瞬时 " + fmt(SpeedTracker.instantHorizontalSpeed()));
        if (showPeak.get()) out.add("峰值 " + fmt(SpeedTracker.peakHorizontalSpeed()));
        if (showTotals.get()) {
            out.add(String.format("路程 %.0f 格｜净位移 %.0f 格", SpeedTracker.totalPath(), SpeedTracker.netDisplacement()));
            out.add(String.format("已用 %.0f 秒｜全程平均 %s", SpeedTracker.elapsedSeconds(), fmt(SpeedTracker.totalAverageSpeed())));
        }
        if (showEta.get()) {
            double eta = SpeedTracker.etaSeconds(etaDistance.get());
            out.add(eta < 0 ? "ETA 算不出（速度太低）" : String.format("跑 %d 格还要 %s",
                etaDistance.get(), timeText(eta)));
        }
        if (showSegment.get()) {
            out.add(segmentInfo());
            SpeedTracker.SegmentReport last = SpeedTracker.lastSegment();
            if (last != null) {
                out.add(String.format("上段 %d：%.0f 秒 / %.0f 格 / 均 %s", last.index(), last.seconds(),
                    last.path3d(), fmt(last.averageHorizontal())));
            }
        }
        if (waypointEta.get() && showWaypointEta.get()) {
            String w = waypointLine();
            if (!w.isEmpty()) out.add(w);
            if (etaSpeed.get() == EtaSpeed.时段平均) {
                SpeedTracker.SegmentReport last = SpeedTracker.lastSegment();
                out.add(last == null
                    ? String.format("航点时间用时段平均：还没有完整时段，等这一段结算（还有 %.1f 秒）",
                        SpeedTracker.segmentRemainingSeconds())
                    : String.format("航点时间用时段平均：上段平均 %s（第 %d 段）",
                        fmt(SpeedTracker.lastSegmentAverageHorizontal()), last.index()));
            } else {
                out.add("航点时间用实时平均：" + fmt(SpeedTracker.horizontalSpeed()));
            }
        }
        return out;
    }

    /** 按当前单位格式化一个「格/秒」值。 */
    public String fmt(double blocksPerSecond) {
        double v = SpeedTracker.convert(blocksPerSecond, unit.get().ordinal());
        String s = String.format("%." + decimals.get() + "f", v);
        return s + " " + SpeedTracker.unitName(unit.get().ordinal());
    }

    /** 秒 → 「1 分 12 秒」。 */
    public static String timeText(double seconds) {
        return SpeedTracker.timeText(seconds);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton resetBtn = table.add(theme.button("重置统计")).expandX().minWidth(120).widget();
        resetBtn.action = () -> {
            SpeedTracker.reset();
            FOElytraLog.info("速度统计已重置（当前时段也清零重开）");
            if (liveLabel != null) liveLabel.set(oneLine());
            if (segmentLabel != null) segmentLabel.set(segmentInfo());
            if (waypointLabel != null) waypointLabel.set(waypointLine());
        };
        table.row();
        table.add(theme.label("独立 HUD：HUD 编辑器 → 找「实时速度」元素")).expandCellX();
        table.row();
        liveLabel = table.add(theme.label(oneLine())).expandCellX().widget();
        table.row();
        segmentLabel = table.add(theme.label(segmentInfo())).expandCellX().widget();
        table.row();
        waypointLabel = table.add(theme.label(waypointLine())).expandCellX().widget();
        return table;
    }
}
