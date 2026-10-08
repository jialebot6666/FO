package com.fo.addon.elytra.modules;

import com.fo.addon.elytra.FOElytraModule;
import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.SettingHelper;
import com.fo.addon.elytra.core.SpeedTracker;
import java.util.ArrayList;
import java.util.List;
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
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;

public class SpeedMeter
extends FOElytraModule {
    private final SettingGroup sgSpeed;
    private final SettingGroup sgSegment;
    private final SettingGroup sgWaypoint;
    private final SettingGroup sgReset;
    private final SettingGroup sgDebug;
    private final DoubleSetting windowSeconds;
    private final EnumSetting<Unit> unit;
    private final IntSetting decimals;
    private final BoolSetting showVertical;
    private final BoolSetting showInstant;
    private final BoolSetting showPeak;
    private final BoolSetting showTotals;
    private final BoolSetting showEta;
    private final IntSetting etaDistance;
    private final IntSetting segmentSeconds;
    private final BoolSetting segmentChat;
    private final BoolSetting showSegment;
    private final BoolSetting showSegmentDetail;
    private final BoolSetting waypointEta;
    private final EnumSetting<EtaSpeed> etaSpeed;
    private final BoolSetting showWaypointEta;
    private final KeybindSetting resetKey;
    private final BoolSetting resetOnTeleport;
    private final BoolSetting hudLine;
    private final BoolSetting debugMessages;
    private boolean resetKeyWasPressed;
    private WLabel liveLabel;
    private WLabel segmentLabel;
    private WLabel waypointLabel;

    public SpeedMeter() {
        super("FO \u5b9e\u65f6\u5e73\u5747\u901f\u5ea6", "\u6ed1\u52a8\u7a97\u53e3\u5e73\u5747\u901f\u5ea6\uff08\u6c34\u5e73/\u5782\u76f4/\u4e09\u7ef4\uff09+ \u77ac\u65f6/\u5cf0\u503c/\u7d2f\u8ba1/ETA\uff1b\u5e26\u72ec\u7acb HUD \u5143\u7d20\uff0c\u53ef\u968f\u610f\u6446\u653e");
        this.sgSpeed = this.settings.createGroup("\u901f\u5ea6");
        this.sgSegment = this.settings.createGroup("\u65f6\u6bb5\u5e73\u5747");
        this.sgWaypoint = this.settings.createGroup("\u822a\u70b9\u5269\u4f59");
        this.sgReset = this.settings.createGroup("\u91cd\u7f6e");
        this.sgDebug = this.settings.createGroup("\u8c03\u8bd5");
        this.windowSeconds = SettingHelper.double_(this.sgSpeed, "\u5e73\u5747\u7a97\u53e3\uff08\u79d2\uff09", "\u7528\u6700\u8fd1\u591a\u5c11\u79d2\u7b97\u5e73\u5747\u3002\u592a\u5c0f\u4f1a\u4e71\u8df3\uff08\u653e\u4e2a\u70df\u82b1\u5c31\u98d9\u4e00\u4e0b\uff09\uff0c\u592a\u5927\u4f1a\u628a\u4e4b\u524d\u5730\u9762\u8d70\u8def/\u8865\u7ed9\u7684\u65f6\u95f4\u4e5f\u7b97\u8fdb\u6765\u3002\n\u8dd1\u56fe\u63a8\u8350 5 \u79d2\uff1b\u6d4b\u77ac\u65f6\u6027\u80fd\u53ef\u4ee5\u8c03\u5230 1 \u79d2\u3002", 5.0, 0.5, 60.0);
        this.unit = SettingHelper.enum_(this.sgSpeed, "\u5355\u4f4d", "\u683c/\u79d2 = \u539f\u7248\u5355\u4f4d\uff081 \u683c = 1 \u7c73\uff09\uff1b\u516c\u91cc/\u5c0f\u65f6 = \u683c/\u79d2 \u00d7 3.6\uff1b\u683c/tick = \u683c/\u79d2 \u00f7 20\u3002", Unit.BlocksPerSecond);
        this.decimals = SettingHelper.int_(this.sgSpeed, "\u5c0f\u6570\u4f4d", "\u663e\u793a\u4fdd\u7559\u51e0\u4f4d\u5c0f\u6570\u3002", 1, 0, 3);
        this.showVertical = SettingHelper.bool(this.sgSpeed, "\u663e\u793a\u5782\u76f4\u901f\u5ea6", "\u989d\u5916\u663e\u793a\u5782\u76f4\u5206\u91cf\uff08\u722c\u5347/\u4fef\u51b2\u65f6\u6709\u7528\uff09\u3002", false);
        this.showInstant = SettingHelper.bool(this.sgSpeed, "\u663e\u793a\u77ac\u65f6\u901f\u5ea6", "\u6700\u8fd1\u4e00\u6b21\u91c7\u6837\u7b97\u51fa\u6765\u7684\u901f\u5ea6\uff08\u4f1a\u8df3\uff0c\u4f46\u80fd\u770b\u51fa\u300c\u521a\u624d\u90a3\u4e00\u4e0b\u300d\uff09\u3002", true);
        this.showPeak = SettingHelper.bool(this.sgSpeed, "\u663e\u793a\u5cf0\u503c", "\u672c\u6b21\u8fd0\u884c\u4ee5\u6765\u7684\u6700\u9ad8\u6c34\u5e73\u901f\u5ea6\u3002", true);
        this.showTotals = SettingHelper.bool(this.sgSpeed, "\u663e\u793a\u7d2f\u8ba1\u4e0e\u5168\u7a0b\u5e73\u5747", "\u7d2f\u8ba1\u8def\u7a0b / \u51c0\u4f4d\u79fb / \u5df2\u7528\u65f6 / \u5168\u7a0b\u5e73\u5747\uff08\u51c0\u4f4d\u79fb\u8fdc\u5c0f\u4e8e\u8def\u7a0b = \u5728\u539f\u5730\u6253\u8f6c\uff09\u3002", true);
        this.showEta = SettingHelper.bool(this.sgSpeed, "\u663e\u793a ETA\uff08\u8fd8\u8981\u591a\u4e45\uff09", "\u6309\u7a97\u53e3\u5e73\u5747\u901f\u5ea6\u7b97\u300c\u8dd1\u5b8c\u300cETA \u53c2\u7167\u8ddd\u79bb\u300d\u8fd8\u8981\u591a\u4e45\u300d\uff0c\u7ed9\u4e2a\u76f4\u89c2\u7684\u65f6\u95f4\u611f\u3002", false);
        this.etaDistance = SettingHelper.int_(this.sgSpeed, "ETA \u53c2\u7167\u8ddd\u79bb\uff08\u683c\uff09", "ETA \u7528\u8fd9\u4e2a\u8ddd\u79bb\u7b97\uff08\u4f8b\u5982\u4f60\u5230\u76ee\u6807\u7684\u8ddd\u79bb\uff09\u3002\u6362\u6210 km/h \u5355\u4f4d\u65f6\u8fd9\u91cc\u4e5f\u6309\u683c\u7b97\uff0c\u4e0d\u5f71\u54cd\u3002", 1000, 1, 1000000);
        this.segmentSeconds = SettingHelper.int_(this.sgSegment, "\u65f6\u6bb5\u957f\u5ea6\uff08\u79d2\uff09", "\u628a\u8ba1\u65f6\u5207\u6210\u56fa\u5b9a\u957f\u5ea6\u7684\u65f6\u6bb5\uff0c\u6bcf\u6bb5\u7ed3\u675f\u7ed3\u7b97\u8fd9\u4e00\u6bb5\u8d70\u4e86\u591a\u5c11\u683c\u3001\u5e73\u5747\u591a\u5feb\uff08\u7528\u6e38\u620f tick \u8ba1\u65f6\uff0c\u5361\u987f\u4e0d\u4f1a\u628a\u6570\u636e\u7b97\u6b6a\uff09\u3002", 30, 5, 600);
        this.segmentChat = SettingHelper.bool(this.sgSegment, "\u65f6\u6bb5\u7ed3\u675f\u65f6\u62a5\u5230\u804a\u5929\u680f", "\u6bcf\u6bb5\u7ed3\u675f\u65f6\u5728\u804a\u5929\u680f\u6253\u4e00\u884c\u7ed3\u7b97\uff0c\u6587\u4ef6\u65e5\u5fd7\u91cc\u53e6\u6709\u4e00\u4efd\u542b\u4e09\u7ef4\u5e73\u5747\u4e0e\u51c0\u4f4d\u79fb\u7684\u8be6\u7ec6\u6570\u636e\u3002", true);
        this.showSegment = SettingHelper.bool(this.sgSegment, "\u663e\u793a\u672c\u6bb5\u8fdb\u5ea6", "\u663e\u793a\u5f53\u524d\u8fd9\u4e00\u6bb5\u7684\u8fdb\u884c\u4e2d\u5e73\u5747\uff0c\u4f8b\u5982\u300c\u672c\u6bb5 12.3/30.0 \u79d2\uff5c\u5747 41.1 \u683c/\u79d2\u300d\u3002", true);
        this.showSegmentDetail = SettingHelper.bool(this.sgSegment, "\u663e\u793a\u672c\u6bb5\u8def\u7a0b\u4e0e\u76f4\u7ebf", "\u989d\u5916\u663e\u793a\u8fd9\u4e00\u6bb5\u8d70\u4e86\u591a\u5c11\u683c\u3001\u4ee5\u53ca\u8d77\u70b9\u5230\u73b0\u5728\u7684\u76f4\u7ebf\u8ddd\u79bb\uff08\u4e24\u8005\u5dee\u5f97\u591a\u8bf4\u660e\u5728\u7ed5\u8def\uff09\u3002", false);
        this.waypointEta = SettingHelper.bool(this.sgWaypoint, "\u6309 Baritone \u822a\u70b9\u7b97\u5269\u4f59\u65f6\u95f4", "\u628a Baritone \u5f53\u524d\u8def\u5f84\u5269\u4e0b\u7684\u822a\u70b9\u7d2f\u52a0\u6210\u5269\u4f59\u8def\u7a0b\uff0c\u9664\u4ee5\u5f53\u524d\u5e73\u5747\u901f\u5ea6\u7b97\u51fa\u8fd8\u8981\u8dd1\u591a\u4e45\u3002", true);
        this.etaSpeed = SettingHelper.enum_(this.sgWaypoint, "\u822a\u70b9\u65f6\u95f4\u7528\u54ea\u4e2a\u901f\u5ea6", "\u5b9e\u65f6\u5e73\u5747 = \u7528\u6ed1\u52a8\u7a97\u53e3\u5e73\u5747\uff0c\u7acb\u523b\u6709\u503c\uff1b\u65f6\u6bb5\u5e73\u5747 = \u7528\u6700\u8fd1\u4e00\u6bb5\u5b8c\u6574\u65f6\u6bb5\u7684\u5e73\u5747\uff0c\u8fd8\u6ca1\u7ed3\u7b97\u5b8c\u5c31\u663e\u793a\u7b49\u7ed3\u7b97\u3002", EtaSpeed.\u5b9e\u65f6\u5e73\u5747);
        this.showWaypointEta = SettingHelper.bool(this.sgWaypoint, "\u663e\u793a\u822a\u70b9\u5269\u4f59\u65f6\u95f4", "\u663e\u793a\u300c\u822a\u70b9\u5269\u4f59 1234 \u683c \u2192 \u7ea6 1 \u5206 23 \u79d2\u300d\uff08\u9798\u7fc5\u98de\u884c\u65f6 Baritone \u4e0d\u7ed9\u822a\u70b9\uff0c\u6309\u5230\u76ee\u7684\u5730\u7684\u76f4\u7ebf\u4f30\uff09\u3002", true);
        this.resetKey = SettingHelper.keybind(this.sgReset, "\u91cd\u7f6e\u952e", "\u6309\u4e00\u4e0b\u628a\u7d2f\u8ba1\u4e0e\u901f\u5ea6\u7edf\u8ba1\u6e05\u96f6\uff08\u6362\u4e00\u6bb5\u822a\u7a0b\u65f6\u7528\uff09\u3002");
        this.resetOnTeleport = SettingHelper.bool(this.sgReset, "\u4f20\u9001\u65f6\u81ea\u52a8\u91cd\u7f6e\u7a97\u53e3", "\u4f4d\u7f6e\u8df3\u53d8\u8d85\u8fc7 32 \u683c\uff08/tp\u3001\u6362\u7ef4\u5ea6\u3001\u670d\u52a1\u5668\u62c9\u4eba\uff09\u65f6\u6e05\u7a7a\u6ed1\u52a8\u7a97\u53e3 \u2014\u2014 \u5426\u5219\u90a3\u4e00\u4e0b\u4f1a\u88ab\u7b97\u6210\u51e0\u5343\u683c\u6bcf\u79d2\u3002\u7d2f\u8ba1\u8def\u7a0b\u4e0d\u53d7\u5f71\u54cd\uff08\u4f20\u9001\u672c\u6765\u5c31\u4e0d\u8ba1\u5165\uff09\u3002", true);
        this.hudLine = SettingHelper.bool(this.sgDebug, "\u5728\u6a21\u5757\u5217\u8868\u663e\u793a", "\u5728\u300cHUD \u2192 \u6d3b\u52a8\u6a21\u5757\u300d\u90a3\u4e00\u884c\u91cc\u663e\u793a\u901f\u5ea6\uff08\u7b49\u4ef7\u4e8e\u72ec\u7acb HUD \u5143\u7d20\uff0c\u4e8c\u9009\u4e00\u6216\u90fd\u8981\uff09\u3002", true);
        this.debugMessages = SettingHelper.bool(this.sgDebug, "\u8c03\u8bd5\u8f93\u51fa", "\u628a\u6bcf\u6b21\u91c7\u6837/\u91cd\u7f6e/\u4f20\u9001\u68c0\u6d4b\u90fd\u6253\u51fa\u6765\uff08\u5f88\u5435\uff0c\u6392\u969c\u7528\uff09\u3002", false);
    }

    public void onActivate() {
        SpeedTracker.setWindowSeconds((Double)this.windowSeconds.get());
        SpeedTracker.setSegmentSeconds(((Integer)this.segmentSeconds.get()).intValue());
        SpeedTracker.setSegmentChat((Boolean)this.segmentChat.get());
        SpeedTracker.reset();
        this.resetKeyWasPressed = false;
        FOElytraLog.info("\u5b9e\u65f6\u5e73\u5747\u901f\u5ea6\uff1a\u7a97\u53e3 %.1f \u79d2\uff5c\u65f6\u6bb5 %.0f \u79d2\uff5c\u5355\u4f4d %s\uff5c\u5c0f\u6570 %d \u4f4d", this.windowSeconds.get(), SpeedTracker.segmentLengthSeconds(), SpeedTracker.unitName(((Unit)((Object)this.unit.get())).ordinal()), this.decimals.get());
        FOElytraLog.detail("\u60f3\u8981\u72ec\u7acb HUD\uff1aHUD \u7f16\u8f91\u5668\u91cc\u627e\u300c\u5b9e\u65f6\u901f\u5ea6\u300d\u5143\u7d20\uff08\u4e0d\u4f9d\u8d56\u672c\u6a21\u5757\u662f\u5426\u5f00\u7740\uff09", new Object[0]);
    }

    public void onDeactivate() {
        FOElytraLog.detail("\u5b9e\u65f6\u5e73\u5747\u901f\u5ea6\u5173\u95ed\uff08\u7d2f\u8ba1\u6570\u636e\u4fdd\u7559\u7ed9 HUD \u5143\u7d20\u7528\uff09", new Object[0]);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        boolean pressed;
        if (!this.isActive() || this.mc.player == null) {
            return;
        }
        FOElytraLog.debugEnabled = (Boolean)this.debugMessages.get();
        SpeedTracker.setWindowSeconds((Double)this.windowSeconds.get());
        SpeedTracker.setSegmentSeconds(((Integer)this.segmentSeconds.get()).intValue());
        SpeedTracker.setSegmentChat((Boolean)this.segmentChat.get());
        boolean fresh = SpeedTracker.sample();
        if (fresh && ((Boolean)this.debugMessages.get()).booleanValue()) {
            FOElytraLog.debug("\u6c34\u5e73 %.2f \u683c/\u79d2\uff5c\u5782\u76f4 %.2f\uff5c\u4e09\u7ef4 %.2f\uff5c\u77ac\u65f6 %.2f\uff5c\u7d2f\u8ba1 %.1f \u683c", SpeedTracker.horizontalSpeed(), SpeedTracker.verticalSpeed(), SpeedTracker.threeDSpeed(), SpeedTracker.instantHorizontalSpeed(), SpeedTracker.totalPath());
        }
        boolean bl = pressed = this.resetKey.get() != null && ((Keybind)this.resetKey.get()).isPressed();
        if (pressed && !this.resetKeyWasPressed) {
            SpeedTracker.reset();
            FOElytraLog.info("\u901f\u5ea6\u7edf\u8ba1\u5df2\u91cd\u7f6e\uff08\u5f53\u524d\u65f6\u6bb5\u4e5f\u6e05\u96f6\u91cd\u5f00\uff09", new Object[0]);
        }
        this.resetKeyWasPressed = pressed;
        if (this.liveLabel != null && this.mc.player.age % 5 == 0) {
            this.liveLabel.set(this.oneLine());
        }
        if (this.segmentLabel != null && this.mc.player.age % 5 == 0) {
            this.segmentLabel.set(this.segmentInfo());
        }
        if (this.waypointLabel != null && this.mc.player.age % 5 == 0) {
            this.waypointLabel.set(this.waypointLine());
        }
    }

    public String getInfoString() {
        if (!((Boolean)this.hudLine.get()).booleanValue() || this.mc.player == null) {
            return null;
        }
        return this.oneLine();
    }

    private String oneLine() {
        String w;
        StringBuilder sb = new StringBuilder(this.fmt(SpeedTracker.horizontalSpeed()));
        if (((Boolean)this.showVertical.get()).booleanValue()) {
            sb.append("/").append(this.fmt(SpeedTracker.verticalSpeed()));
        }
        if (((Boolean)this.showInstant.get()).booleanValue()) {
            sb.append(" \u2191").append(this.fmt(SpeedTracker.instantHorizontalSpeed()));
        }
        if (((Boolean)this.showPeak.get()).booleanValue()) {
            sb.append(" \u5cf0").append(this.fmt(SpeedTracker.peakHorizontalSpeed()));
        }
        if (((Boolean)this.showSegment.get()).booleanValue()) {
            sb.append("\uff5c").append(this.segmentLine());
        }
        if (!(w = this.waypointLine()).isEmpty()) {
            sb.append("\uff5c").append(w);
        }
        return sb.toString();
    }

    private String segmentLine() {
        return String.format("\u672c\u6bb5 %.1f/%.0f \u79d2\uff5c\u5747 %s", SpeedTracker.segmentElapsedSeconds(), SpeedTracker.segmentLengthSeconds(), this.fmt(SpeedTracker.segmentAverageHorizontal()));
    }

    private String segmentInfo() {
        if (!((Boolean)this.showSegment.get()).booleanValue()) {
            return "";
        }
        String s = this.segmentLine();
        if (((Boolean)this.showSegmentDetail.get()).booleanValue()) {
            s = s + String.format("\uff5c\u8def\u7a0b %.0f \u683c\uff5c\u76f4\u7ebf %.0f \u683c", SpeedTracker.segmentPath3d(), SpeedTracker.segmentDirectDistance());
        }
        return s;
    }

    public boolean etaUseSegmentAverage() {
        return this.etaSpeed.get() == EtaSpeed.\u65f6\u6bb5\u5e73\u5747;
    }

    private String waypointLine() {
        if (!((Boolean)this.waypointEta.get()).booleanValue() || !((Boolean)this.showWaypointEta.get()).booleanValue()) {
            return "";
        }
        BaritoneHook.WaypointRoute route = BaritoneHook.waypointRoute();
        if (route == null) {
            return "";
        }
        String head = String.format("\u822a\u70b9\u5269\u4f59 %.0f \u683c%s \u2192 ", route.distance(), route.elytraStraight() ? "\uff08\u9798\u7fc5\u76f4\u7ebf\uff09" : "");
        if (this.etaSpeed.get() == EtaSpeed.\u65f6\u6bb5\u5e73\u5747) {
            double speed = SpeedTracker.lastSegmentAverageHorizontal();
            if (speed < 0.0) {
                return head + String.format("\u7b49\u65f6\u6bb5\u7ed3\u7b97\uff08\u8fd8\u6709 %.1f \u79d2\uff09", SpeedTracker.segmentRemainingSeconds());
            }
            if (speed < 0.1) {
                return head + "\u2014\uff08\u8fd9\u6bb5\u6ca1\u52a8\uff09";
            }
            return head + "\u7ea6 " + SpeedTracker.timeText(route.distance() / speed) + "\uff08\u65f6\u6bb5\uff09";
        }
        double eta = SpeedTracker.etaSeconds(route.distance());
        return head + (String)(eta < 0.0 ? "\u2014\uff08\u901f\u5ea6\u592a\u4f4e\uff09" : "\u7ea6 " + SpeedTracker.timeText(eta) + "\uff08\u5b9e\u65f6\uff09");
    }

    public List<String> detailLines() {
        ArrayList<String> out = new ArrayList<String>();
        out.add("\u6c34\u5e73 " + this.fmt(SpeedTracker.horizontalSpeed()));
        if (((Boolean)this.showVertical.get()).booleanValue()) {
            out.add("\u5782\u76f4 " + this.fmt(SpeedTracker.verticalSpeed()));
            out.add("\u4e09\u7ef4 " + this.fmt(SpeedTracker.threeDSpeed()));
        }
        if (((Boolean)this.showInstant.get()).booleanValue()) {
            out.add("\u77ac\u65f6 " + this.fmt(SpeedTracker.instantHorizontalSpeed()));
        }
        if (((Boolean)this.showPeak.get()).booleanValue()) {
            out.add("\u5cf0\u503c " + this.fmt(SpeedTracker.peakHorizontalSpeed()));
        }
        if (((Boolean)this.showTotals.get()).booleanValue()) {
            out.add(String.format("\u8def\u7a0b %.0f \u683c\uff5c\u51c0\u4f4d\u79fb %.0f \u683c", SpeedTracker.totalPath(), SpeedTracker.netDisplacement()));
            out.add(String.format("\u5df2\u7528 %.0f \u79d2\uff5c\u5168\u7a0b\u5e73\u5747 %s", SpeedTracker.elapsedSeconds(), this.fmt(SpeedTracker.totalAverageSpeed())));
        }
        if (((Boolean)this.showEta.get()).booleanValue()) {
            double eta = SpeedTracker.etaSeconds(((Integer)this.etaDistance.get()).intValue());
            out.add(eta < 0.0 ? "ETA \u7b97\u4e0d\u51fa\uff08\u901f\u5ea6\u592a\u4f4e\uff09" : String.format("\u8dd1 %d \u683c\u8fd8\u8981 %s", this.etaDistance.get(), SpeedMeter.timeText(eta)));
        }
        if (((Boolean)this.showSegment.get()).booleanValue()) {
            out.add(this.segmentInfo());
            SpeedTracker.SegmentReport last = SpeedTracker.lastSegment();
            if (last != null) {
                out.add(String.format("\u4e0a\u6bb5 %d\uff1a%.0f \u79d2 / %.0f \u683c / \u5747 %s", last.index(), last.seconds(), last.path3d(), this.fmt(last.averageHorizontal())));
            }
        }
        if (((Boolean)this.waypointEta.get()).booleanValue() && ((Boolean)this.showWaypointEta.get()).booleanValue()) {
            String w = this.waypointLine();
            if (!w.isEmpty()) {
                out.add(w);
            }
            if (this.etaSpeed.get() == EtaSpeed.\u65f6\u6bb5\u5e73\u5747) {
                SpeedTracker.SegmentReport last = SpeedTracker.lastSegment();
                out.add(last == null ? String.format("\u822a\u70b9\u65f6\u95f4\u7528\u65f6\u6bb5\u5e73\u5747\uff1a\u8fd8\u6ca1\u6709\u5b8c\u6574\u65f6\u6bb5\uff0c\u7b49\u8fd9\u4e00\u6bb5\u7ed3\u7b97\uff08\u8fd8\u6709 %.1f \u79d2\uff09", SpeedTracker.segmentRemainingSeconds()) : String.format("\u822a\u70b9\u65f6\u95f4\u7528\u65f6\u6bb5\u5e73\u5747\uff1a\u4e0a\u6bb5\u5e73\u5747 %s\uff08\u7b2c %d \u6bb5\uff09", this.fmt(SpeedTracker.lastSegmentAverageHorizontal()), last.index()));
            } else {
                out.add("\u822a\u70b9\u65f6\u95f4\u7528\u5b9e\u65f6\u5e73\u5747\uff1a" + this.fmt(SpeedTracker.horizontalSpeed()));
            }
        }
        return out;
    }

    public String fmt(double blocksPerSecond) {
        double v = SpeedTracker.convert(blocksPerSecond, ((Unit)((Object)this.unit.get())).ordinal());
        String s = String.format("%." + String.valueOf(this.decimals.get()) + "f", v);
        return s + " " + SpeedTracker.unitName(((Unit)((Object)this.unit.get())).ordinal());
    }

    public static String timeText(double seconds) {
        return SpeedTracker.timeText(seconds);
    }

    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton resetBtn = (WButton)table.add((WWidget)theme.button("\u91cd\u7f6e\u7edf\u8ba1")).expandX().minWidth(120.0).widget();
        resetBtn.action = () -> {
            SpeedTracker.reset();
            FOElytraLog.info("\u901f\u5ea6\u7edf\u8ba1\u5df2\u91cd\u7f6e\uff08\u5f53\u524d\u65f6\u6bb5\u4e5f\u6e05\u96f6\u91cd\u5f00\uff09", new Object[0]);
            if (this.liveLabel != null) {
                this.liveLabel.set(this.oneLine());
            }
            if (this.segmentLabel != null) {
                this.segmentLabel.set(this.segmentInfo());
            }
            if (this.waypointLabel != null) {
                this.waypointLabel.set(this.waypointLine());
            }
        };
        table.row();
        table.add((WWidget)theme.label("\u72ec\u7acb HUD\uff1aHUD \u7f16\u8f91\u5668 \u2192 \u627e\u300c\u5b9e\u65f6\u901f\u5ea6\u300d\u5143\u7d20")).expandCellX();
        table.row();
        this.liveLabel = (WLabel)table.add((WWidget)theme.label(this.oneLine())).expandCellX().widget();
        table.row();
        this.segmentLabel = (WLabel)table.add((WWidget)theme.label(this.segmentInfo())).expandCellX().widget();
        table.row();
        this.waypointLabel = (WLabel)table.add((WWidget)theme.label(this.waypointLine())).expandCellX().widget();
        return table;
    }

    public static enum Unit {
        BlocksPerSecond("\u683c/\u79d2"),
        Kmh("\u516c\u91cc/\u65f6"),
        BlocksPerTick("\u683c/tick");


        private final String label;

        Unit(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public static enum EtaSpeed {
        \u5b9e\u65f6\u5e73\u5747,
        \u65f6\u6bb5\u5e73\u5747;

    }
}

