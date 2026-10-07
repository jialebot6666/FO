package com.fo.addon;

import com.fo.addon.elytra.core.SpeedTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 速度显示的单位换算与时长文案（纯逻辑，不依赖 MC 运行时）。
 *
 * 前端全汉化：单位名必须是「格/秒」「公里/小时」「格/tick」，不允许出现 "km/h" 这类英文。
 */
class SpeedTrackerFormatTest {

    @Test
    void blocksPerSecondIsPassThrough() {
        assertEquals(10.0, SpeedTracker.convert(10.0, 0), 1e-9);
    }

    @Test
    void kmhMultipliesByThreePointSix() {
        assertEquals(36.0, SpeedTracker.convert(10.0, 1), 1e-9);
    }

    @Test
    void blocksPerTickDividesByTwenty() {
        assertEquals(1.0, SpeedTracker.convert(20.0, 2), 1e-9);
    }

    @Test
    void unitNamesAreChinese() {
        assertEquals("格/秒", SpeedTracker.unitName(0));
        assertEquals("公里/小时", SpeedTracker.unitName(1));
        assertEquals("格/刻", SpeedTracker.unitName(2));
    }

    @Test
    void unknownUnitFallsBackToBlocksPerSecond() {
        assertEquals("格/秒", SpeedTracker.unitName(99));
        assertEquals(5.0, SpeedTracker.convert(5.0, 99), 1e-9);
    }

    @Test
    void timeTextUnderOneMinuteShowsSeconds() {
        assertEquals("45 秒", SpeedTracker.timeText(45));
        assertEquals("0 秒", SpeedTracker.timeText(0));
    }

    @Test
    void timeTextUnderOneHourShowsMinutesAndSeconds() {
        assertEquals("1 分 30 秒", SpeedTracker.timeText(90));
        assertEquals("10 分 0 秒", SpeedTracker.timeText(600));
    }

    @Test
    void timeTextOverOneHourShowsHoursAndMinutes() {
        assertEquals("1 时 0 分", SpeedTracker.timeText(3600));
        assertEquals("2 时 30 分", SpeedTracker.timeText(9000));
    }

    @Test
    void negativeTimeIsDash() {
        assertEquals("-", SpeedTracker.timeText(-1));
    }

    @Test
    void unitNamesContainNoAsciiLetters() {
        for (int unit = 0; unit <= 2; unit++) {
            String name = SpeedTracker.unitName(unit);
            assertTrue(name.matches("^[^A-Za-z]+$"), "单位名不应含英文字母，实际：" + name);
        }
    }
}
