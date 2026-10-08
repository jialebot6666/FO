package com.fo.addon.elytra.hud;

import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.SpeedTracker;
import com.fo.addon.elytra.modules.SpeedMeter;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class SpeedHud
extends HudElement {
    public static final HudElementInfo<SpeedHud> INFO = new HudElementInfo(Hud.GROUP, "speed-meter", "FO \u5b9e\u65f6\u5e73\u5747\u901f\u5ea6", "\u6ed1\u52a8\u7a97\u53e3\u5e73\u5747\u901f\u5ea6\uff1a\u6c34\u5e73/\u5782\u76f4/\u4e09\u7ef4 + \u77ac\u65f6/\u5cf0\u503c/\u7d2f\u8ba1/\u51c0\u4f4d\u79fb/ETA\u3002\u6570\u636e\u5728\u6a21\u5757\u5173\u7740\u65f6\u4e5f\u7167\u5e38\u7edf\u8ba1\u3002", SpeedHud::new);
    private final SettingGroup sgLines;
    private final SettingGroup sgStyle;
    private final BoolSetting horizontal;
    private final BoolSetting vertical;
    private final BoolSetting threeD;
    private final BoolSetting instant;
    private final BoolSetting peak;
    private final BoolSetting totals;
    private final BoolSetting segment;
    private final BoolSetting segmentDetail;
    private final BoolSetting waypointEta;
    private final BoolSetting elapsed;
    private final IntSetting unit;
    private final IntSetting decimals;
    private final BoolSetting shadow;
    private final DoubleSetting scale;

    public SpeedHud() {
        super(INFO);
        this.sgLines = this.settings.createGroup("\u663e\u793a\u5185\u5bb9");
        this.sgStyle = this.settings.createGroup("\u6837\u5f0f");
        this.horizontal = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u6c34\u5e73\u901f\u5ea6")).description("\u8dd1\u56fe\u771f\u6b63\u8be5\u770b\u7684\u6570\uff08XZ \u5e73\u9762\u7684\u6ed1\u52a8\u7a97\u53e3\u5e73\u5747\uff09\u3002")).defaultValue(true)).build());
        this.vertical = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u5782\u76f4\u901f\u5ea6")).description("\u722c\u5347/\u4fef\u51b2\u7684\u901f\u7387\u3002")).defaultValue(false)).build());
        this.threeD = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u4e09\u7ef4\u901f\u5ea6")).description("\u542b\u5782\u76f4\u5206\u91cf\u7684\u603b\u901f\u7387\uff08\u722c\u5347\u65f6\u4f1a\u660e\u663e\u5927\u4e8e\u6c34\u5e73\uff09\u3002")).defaultValue(false)).build());
        this.instant = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u77ac\u65f6\u901f\u5ea6")).description("\u6700\u8fd1\u4e00\u6b21\u91c7\u6837\u7b97\u51fa\u6765\u7684\u901f\u5ea6\uff08\u4f1a\u8df3\uff0c\u4f46\u80fd\u770b\u51fa\u300c\u521a\u624d\u90a3\u4e00\u4e0b\u300d\uff09\u3002")).defaultValue(false)).build());
        this.peak = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u5cf0\u503c")).description("\u672c\u6b21\u8fd0\u884c\u4ee5\u6765\u7684\u6700\u9ad8\u6c34\u5e73\u901f\u5ea6\u3002")).defaultValue(false)).build());
        this.totals = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u7d2f\u8ba1\u8def\u7a0b/\u51c0\u4f4d\u79fb")).description("\u51c0\u4f4d\u79fb\u8fdc\u5c0f\u4e8e\u8def\u7a0b = \u5728\u539f\u5730\u6253\u8f6c\uff0c\u914d\u5408\u4e3b\u6a21\u5757\u7684\u300c\u5361\u4f4f\u81ea\u6551\u300d\u770b\u3002")).defaultValue(false)).build());
        this.segment = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u672c\u6bb5\u8fdb\u5ea6")).description("\u5f53\u524d\u65f6\u6bb5\u8fdb\u884c\u4e2d\u7684\u5e73\u5747\u901f\u5ea6\uff0c\u4f8b\u5982\u300c\u672c\u6bb5 12.3/30.0 \u79d2\uff5c\u5747 41.1 \u683c/\u79d2\u300d\u3002")).defaultValue(true)).build());
        this.segmentDetail = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u672c\u6bb5\u8def\u7a0b/\u76f4\u7ebf")).description("\u8fd9\u4e00\u6bb5\u8d70\u4e86\u591a\u5c11\u683c\u3001\u8d77\u70b9\u5230\u73b0\u5728\u7684\u76f4\u7ebf\u8ddd\u79bb\uff08\u5dee\u5f97\u591a\u8bf4\u660e\u5728\u7ed5\u8def\uff09\u3002")).defaultValue(false)).build());
        this.waypointEta = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u822a\u70b9\u5269\u4f59\u65f6\u95f4\u663e\u793a\u5728 HUD")).description("\u6309 Baritone \u822a\u70b9\u7b97\u7684\u5269\u4f59\u8def\u7a0b\u4e0e\u65f6\u95f4\uff0c\u4f8b\u5982\u300c\u822a\u70b9\u5269\u4f59 1234 \u683c \u2192 \u7ea6 1 \u5206 23 \u79d2\u300d\uff08\u9798\u7fc5\u98de\u884c\u65f6\u6309\u76f4\u7ebf\u4f30\uff09\u3002")).defaultValue(true)).build());
        this.elapsed = (BoolSetting)this.sgLines.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u5df2\u7528\u65f6")).description("\u4ece\u91cd\u7f6e/\u5f00\u6a21\u5757\u7b97\u8d77\u8fc7\u4e86\u591a\u4e45\u3002")).defaultValue(false)).build());
        this.unit = (IntSetting)this.sgStyle.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("\u5355\u4f4d")).description("0 = \u683c/\u79d2\uff0c1 = \u516c\u91cc/\u5c0f\u65f6\uff08\u683c/\u79d2 \u00d7 3.6\uff09\uff0c2 = \u683c\u6bcf tick\uff08\u683c/\u79d2 \u00f7 20\uff09\u3002")).defaultValue(0)).min(0).max(2).sliderRange(0, 2).build());
        this.decimals = (IntSetting)this.sgStyle.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("\u5c0f\u6570\u4f4d")).description("\u663e\u793a\u4fdd\u7559\u51e0\u4f4d\u5c0f\u6570\u3002")).defaultValue(1)).min(0).max(3).sliderRange(0, 3).build());
        this.shadow = (BoolSetting)this.sgStyle.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("\u6587\u5b57\u9634\u5f71")).description("\u539f\u7248\u98ce\u683c\u7684\u6587\u5b57\u9634\u5f71\uff0c\u6697\u80cc\u666f\u4e0a\u66f4\u6e05\u695a\u3002")).defaultValue(true)).build());
        this.scale = (DoubleSetting)this.sgStyle.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("\u7f29\u653e")).description("\u6587\u5b57\u6574\u4f53\u7f29\u653e\uff080.5 = \u4e00\u534a\u5927\u5c0f\uff09\u3002")).defaultValue(1.0).min(0.5).max(3.0).sliderRange(0.5, 2.0).build());
    }

    public void render(HudRenderer renderer) {
        SpeedTracker.sample();
        List<String> lines = this.buildLines();
        boolean sh = (Boolean)this.shadow.get();
        double sc = (Double)this.scale.get();
        double w = 0.0;
        for (String l : lines) {
            w = Math.max(w, renderer.textWidth(l, sh, sc));
        }
        double lineH = renderer.textHeight(sh, sc);
        this.setSize(w, lineH * (double)lines.size());
        double y = this.y;
        for (String l : lines) {
            renderer.text(l, (double)this.x, y, Color.WHITE, sh, sc);
            y += lineH;
        }
    }

    private List<String> buildLines() {
        ArrayList<String> out = new ArrayList<String>();
        if (((Boolean)this.horizontal.get()).booleanValue()) {
            out.add("\u6c34\u5e73 " + this.fmt(SpeedTracker.horizontalSpeed()));
        }
        if (((Boolean)this.vertical.get()).booleanValue()) {
            out.add("\u5782\u76f4 " + this.fmt(SpeedTracker.verticalSpeed()));
        }
        if (((Boolean)this.threeD.get()).booleanValue()) {
            out.add("\u4e09\u7ef4 " + this.fmt(SpeedTracker.threeDSpeed()));
        }
        if (((Boolean)this.instant.get()).booleanValue()) {
            out.add("\u77ac\u65f6 " + this.fmt(SpeedTracker.instantHorizontalSpeed()));
        }
        if (((Boolean)this.peak.get()).booleanValue()) {
            out.add("\u5cf0\u503c " + this.fmt(SpeedTracker.peakHorizontalSpeed()));
        }
        if (((Boolean)this.segment.get()).booleanValue()) {
            out.add(String.format("\u672c\u6bb5 %.1f/%.0f \u79d2\uff5c\u5747 %s", SpeedTracker.segmentElapsedSeconds(), SpeedTracker.segmentLengthSeconds(), this.fmt(SpeedTracker.segmentAverageHorizontal())));
        }
        if (((Boolean)this.segmentDetail.get()).booleanValue()) {
            out.add(String.format("\u672c\u6bb5 %.0f \u683c\uff5c\u76f4\u7ebf %.0f \u683c", SpeedTracker.segmentPath3d(), SpeedTracker.segmentDirectDistance()));
        }
        if (((Boolean)this.totals.get()).booleanValue()) {
            out.add(String.format("\u8def\u7a0b %.0f \u683c", SpeedTracker.totalPath()));
            out.add(String.format("\u51c0\u4f4d\u79fb %.0f \u683c", SpeedTracker.netDisplacement()));
        }
        if (((Boolean)this.elapsed.get()).booleanValue()) {
            double s = SpeedTracker.elapsedSeconds();
            out.add(String.format("\u5df2\u7528 %d:%02d", (int)(s / 60.0), (int)(s % 60.0)));
        }
        if (((Boolean)this.waypointEta.get()).booleanValue()) {
            BaritoneHook.WaypointRoute route = BaritoneHook.waypointRoute();
            if (route == null) {
                out.add("\u822a\u70b9\u5269\u4f59 \u2014");
            } else {
                String head = String.format("\u822a\u70b9\u5269\u4f59 %.0f \u683c%s \u2192 ", route.distance(), route.elytraStraight() ? "\uff08\u9798\u7fc5\u76f4\u7ebf\uff09" : "");
                if (this.segmentEta()) {
                    double speed = SpeedTracker.lastSegmentAverageHorizontal();
                    if (speed < 0.0) {
                        out.add(head + String.format("\u7b49\u65f6\u6bb5\u7ed3\u7b97\uff08\u8fd8\u6709 %.1f \u79d2\uff09", SpeedTracker.segmentRemainingSeconds()));
                    } else if (speed < 0.1) {
                        out.add(head + "\u2014\uff08\u8fd9\u6bb5\u6ca1\u52a8\uff09");
                    } else {
                        out.add(head + "\u7ea6 " + SpeedTracker.timeText(route.distance() / speed) + "\uff08\u65f6\u6bb5\uff09");
                    }
                } else {
                    double eta = SpeedTracker.etaSeconds(route.distance());
                    out.add(head + (String)(eta < 0.0 ? "\u2014\uff08\u901f\u5ea6\u592a\u4f4e\uff09" : "\u7ea6 " + SpeedTracker.timeText(eta) + "\uff08\u5b9e\u65f6\uff09"));
                }
            }
        }
        if (out.isEmpty()) {
            out.add("\uff08\u6ca1\u52fe\u4efb\u4f55\u663e\u793a\u9879\uff09");
        }
        return out;
    }

    private boolean segmentEta() {
        Modules modules = Modules.get();
        if (modules == null) {
            return false;
        }
        SpeedMeter meter = (SpeedMeter)modules.get(SpeedMeter.class);
        return meter != null && meter.etaUseSegmentAverage();
    }

    private String fmt(double blocksPerSecond) {
        double v = SpeedTracker.convert(blocksPerSecond, (Integer)this.unit.get());
        return String.format("%." + String.valueOf(this.decimals.get()) + "f %s", v, SpeedTracker.unitName((Integer)this.unit.get()));
    }
}

