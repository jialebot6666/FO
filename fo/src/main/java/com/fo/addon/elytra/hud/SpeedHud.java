package com.fo.addon.elytra.hud;
import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.SpeedTracker;
import com.fo.addon.elytra.modules.SpeedMeter;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import java.util.ArrayList;
import java.util.List;
public class SpeedHud extends HudElement {
    public static final HudElementInfo<SpeedHud> INFO = new HudElementInfo<>(
        Hud.GROUP,
        "fo-speed-meter",
        "FO 实时速度",
        "滑动窗口平均速度：水平/垂直/三维 + 瞬时/峰值/累计/净位移/ETA。数据在模块关着时也照常统计。",
        SpeedHud::new);
    private final SettingGroup sgLines = settings.createGroup("显示内容");
    private final SettingGroup sgStyle = settings.createGroup("样式");
    private final BoolSetting horizontal = sgLines.add(new BoolSetting.Builder()
        .name("水平速度").description("跑图真正该看的数（XZ 平面的滑动窗口平均）。").defaultValue(true).build());
    private final BoolSetting vertical = sgLines.add(new BoolSetting.Builder()
        .name("垂直速度").description("爬升/俯冲的速率。").defaultValue(false).build());
    private final BoolSetting threeD = sgLines.add(new BoolSetting.Builder()
        .name("三维速度").description("含垂直分量的总速率（爬升时会明显大于水平）。").defaultValue(false).build());
    private final BoolSetting instant = sgLines.add(new BoolSetting.Builder()
        .name("瞬时速度").description("最近一次采样算出来的速度（会跳，但能看出「刚才那一下」）。").defaultValue(false).build());
    private final BoolSetting peak = sgLines.add(new BoolSetting.Builder()
        .name("峰值").description("本次运行以来的最高水平速度。").defaultValue(false).build());
    private final BoolSetting totals = sgLines.add(new BoolSetting.Builder()
        .name("累计路程/净位移").description("净位移远小于路程 = 在原地打转，配合主模块的「卡住自救」看。").defaultValue(false).build());
    private final BoolSetting segment = sgLines.add(new BoolSetting.Builder()
        .name("本段进度").description("当前时段进行中的平均速度，例如「本段 12.3/30.0 秒｜均 41.1 格/秒」。").defaultValue(true).build());
    private final BoolSetting segmentDetail = sgLines.add(new BoolSetting.Builder()
        .name("本段路程/直线").description("这一段走了多少格、起点到现在的直线距离（差得多说明在绕路）。").defaultValue(false).build());
    private final BoolSetting waypointEta = sgLines.add(new BoolSetting.Builder()
        .name("航点剩余时间显示在 HUD").description("按 Baritone 航点算的剩余路程与时间，例如「航点剩余 1234 格 → 约 1 分 23 秒」（鞘翅飞行时按直线估）。").defaultValue(true).build());
    private final BoolSetting elapsed = sgLines.add(new BoolSetting.Builder()
        .name("已用时").description("从重置/开模块算起过了多久。").defaultValue(false).build());
    private final IntSetting unit = sgStyle.add(new IntSetting.Builder()
        .name("单位").description("0 = 格/秒，1 = 公里/小时（格/秒 × 3.6），2 = 格每刻（格/秒 ÷ 20，20 刻 = 1 秒）。")
        .defaultValue(0).min(0).max(2).sliderRange(0, 2).build());
    private final IntSetting decimals = sgStyle.add(new IntSetting.Builder()
        .name("小数位").description("显示保留几位小数。").defaultValue(1).min(0).max(3).sliderRange(0, 3).build());
    private final BoolSetting shadow = sgStyle.add(new BoolSetting.Builder()
        .name("文字阴影").description("原版风格的文字阴影，暗背景上更清楚。").defaultValue(true).build());
    private final DoubleSetting scale = sgStyle.add(new DoubleSetting.Builder()
        .name("缩放").description("文字整体缩放（0.5 = 一半大小）。").defaultValue(1.0).min(0.5).max(3.0)
        .sliderRange(0.5, 2.0).build());
    public SpeedHud() {
        super(INFO);
    }
    @Override
    public void render(HudRenderer renderer) {
        SpeedTracker.sample();
        List<String> lines = buildLines();
        boolean sh = shadow.get();
        double sc = scale.get();
        double w = 0;
        for (String l : lines) w = Math.max(w, renderer.textWidth(l, sh, sc));
        double lineH = renderer.textHeight(sh, sc);
        setSize(w, lineH * lines.size());
        double y = this.y;
        for (String l : lines) {
            renderer.text(l, this.x, y, Color.WHITE, sh, sc);
            y += lineH;
        }
    }
    private List<String> buildLines() {
        List<String> out = new ArrayList<>();
        if (horizontal.get()) out.add("水平 " + fmt(SpeedTracker.horizontalSpeed()));
        if (vertical.get()) out.add("垂直 " + fmt(SpeedTracker.verticalSpeed()));
        if (threeD.get()) out.add("三维 " + fmt(SpeedTracker.threeDSpeed()));
        if (instant.get()) out.add("瞬时 " + fmt(SpeedTracker.instantHorizontalSpeed()));
        if (peak.get()) out.add("峰值 " + fmt(SpeedTracker.peakHorizontalSpeed()));
        if (segment.get()) {
            out.add(String.format("本段 %.1f/%.0f 秒｜均 %s", SpeedTracker.segmentElapsedSeconds(),
                SpeedTracker.segmentLengthSeconds(), fmt(SpeedTracker.segmentAverageHorizontal())));
        }
        if (segmentDetail.get()) {
            out.add(String.format("本段 %.0f 格｜直线 %.0f 格", SpeedTracker.segmentPath3d(),
                SpeedTracker.segmentDirectDistance()));
        }
        if (totals.get()) {
            out.add(String.format("路程 %.0f 格", SpeedTracker.totalPath()));
            out.add(String.format("净位移 %.0f 格", SpeedTracker.netDisplacement()));
        }
        if (elapsed.get()) {
            double s = SpeedTracker.elapsedSeconds();
            out.add(String.format("已用 %d:%02d", (int) (s / 60), (int) (s % 60)));
        }
        if (waypointEta.get()) {
            BaritoneHook.WaypointRoute route = BaritoneHook.waypointRoute();
            if (route == null) {
                out.add("航点剩余 —");
            } else {
                String head = String.format("航点剩余 %.0f 格%s → ", route.distance(),
                    route.elytraStraight() ? "（鞘翅直线）" : "");
                if (segmentEta()) {
                    double speed = SpeedTracker.lastSegmentAverageHorizontal();
                    if (speed < 0) {
                        out.add(head + String.format("等时段结算（还有 %.1f 秒）", SpeedTracker.segmentRemainingSeconds()));
                    } else if (speed < 0.1) {
                        out.add(head + "—（这段没动）");
                    } else {
                        out.add(head + "约 " + SpeedTracker.timeText(route.distance() / speed) + "（时段）");
                    }
                } else {
                    double eta = SpeedTracker.etaSeconds(route.distance());
                    out.add(head + (eta < 0 ? "—（速度太低）" : "约 " + SpeedTracker.timeText(eta) + "（实时）"));
                }
            }
        }
        if (out.isEmpty()) out.add("（没勾任何显示项）");
        return out;
    }
    private boolean segmentEta() {
        Modules modules = Modules.get();
        if (modules == null) return false;
        SpeedMeter meter = modules.get(SpeedMeter.class);
        return meter != null && meter.etaUseSegmentAverage();
    }
    private String fmt(double blocksPerSecond) {
        double v = SpeedTracker.convert(blocksPerSecond, unit.get());
        return String.format("%." + decimals.get() + "f %s", v, SpeedTracker.unitName(unit.get()));
    }
}
