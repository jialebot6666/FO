package com.fo.addon;

import com.fo.addon.utils.CruiseApexDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 上升顶点检测单测（对齐 Pitch40Util 的 goingUp && velocity.y &lt;= 0） */
class CruiseApexDetectorTest {

    @Test
    void detectsApexOnVelocitySignFlip() {
        CruiseApexDetector d = new CruiseApexDetector();
        assertFalse(d.update(0.30), "开始上升，还不是顶点");
        assertFalse(d.update(0.12), "仍在上升");
        assertTrue(d.update(-0.01), "速度转负 → 顶点");
    }

    @Test
    void doesNotReportApexWithoutPriorRise() {
        CruiseApexDetector d = new CruiseApexDetector();
        // 一直在下沉：没有「先上升」过，不能误判成顶点
        assertFalse(d.update(-0.4));
        assertFalse(d.update(-0.2));
        assertFalse(d.update(-0.05));
        assertFalse(d.update(0.0), "速度为 0 但没有上升过 → 不是顶点");
    }

    @Test
    void apexFiresOnlyOncePerRise() {
        CruiseApexDetector d = new CruiseApexDetector();
        assertFalse(d.update(0.5));
        assertTrue(d.update(-0.3));
        // 触发后继续下沉，不应连续触发
        assertFalse(d.update(-0.6));
        assertFalse(d.update(-0.9));
        // 再次上升 → 再次到达顶点时可再触发
        assertFalse(d.update(0.4));
        assertTrue(d.update(-0.2));
    }

    @Test
    void zeroVelocityCountsAsApexWhenRising() {
        // Pitch40Util 用 <= 0 判定，速度正好为 0 也算顶点
        CruiseApexDetector d = new CruiseApexDetector();
        assertFalse(d.update(0.2));
        assertTrue(d.update(0.0));
    }

    @Test
    void resetClearsPendingRise() {
        CruiseApexDetector d = new CruiseApexDetector();
        assertFalse(d.update(0.5));
        d.reset();
        assertFalse(d.update(-0.3), "重置后残留的上升状态不应再触发顶点");
    }
}
