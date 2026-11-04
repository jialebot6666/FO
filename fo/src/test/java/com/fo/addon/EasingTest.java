package com.fo.addon;

import com.fo.addon.utils.Easing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓动曲线数学测试（移植自 Hachimi V2 Easing，数学公式逐个对齐）。
 */
class EasingTest {
    private static final double EPS = 1e-9;

    @Test
    void 端点_零和一() {
        // 所有曲线在 t=0/t=1 处的值：弹性/回弹/弹跳可越界，其余落在 [0,1]
        for (Easing e : Easing.values()) {
            double at0 = e.apply(0.0);
            double at1 = e.apply(1.0);
            if (e == Easing.回弹缓入 || e == Easing.回弹缓出 || e == Easing.回弹缓入出) {
                // 回弹在端点为 0（回弹缓入 t=0：2.7*0-1.7*0=0；回弹缓出 t=1：1 + c*0 + 1.7*0 = 1）
                assertTrue(Math.abs(at0) < 1e-6 || Math.abs(at0 - 1) < 1e-6, e.name());
            } else {
                assertEquals(0.0, at0, EPS, e.name());
                assertEquals(1.0, at1, EPS, e.name());
            }
        }
    }

    @Test
    void 线性_恒等() {
        assertEquals(0.25, Easing.线性.apply(0.25), EPS);
        assertEquals(0.9, Easing.线性.apply(0.9), EPS);
    }

    @Test
    void 圆弧缓出_上游默认值_对齐数学() {
        // CIRC_OUT: sqrt(1 - (t-1)^2)；t=0.5 → sqrt(1-0.25)=sqrt(0.75)
        assertEquals(Math.sqrt(0.75), Easing.圆弧缓出.apply(0.5), EPS);
    }

    @Test
    void 立方缓入_三次幂() {
        assertEquals(0.125, Easing.立方缓入.apply(0.5), EPS);
    }

    @Test
    void 立方缓出_镜像() {
        assertEquals(1.0 - Math.pow(0.5, 3.0), Easing.立方缓出.apply(0.5), EPS);
    }

    @Test
    void 弹跳缓出_分段公式() {
        // t < 1/2.75: 7.5625 * t^2；t=0.2 → 7.5625*0.04=0.3025
        assertEquals(0.3025, Easing.弹跳缓出.apply(0.2), 1e-9);
    }

    @Test
    void 弹性缓出_端点外可越界() {
        // ELASTIC_OUT 在中间段可 >1（过冲），t=1 精确回 1
        assertEquals(1.0, Easing.弹性缓出.apply(1.0), EPS);
        double mid = Easing.弹性缓出.apply(0.8);
        assertTrue(mid > 0, "弹性缓出中段应为正");
    }

    @Test
    void 指数缓入出_端点精确() {
        assertEquals(0.0, Easing.指数缓入出.apply(0.0), EPS);
        assertEquals(1.0, Easing.指数缓入出.apply(1.0), EPS);
    }
}
