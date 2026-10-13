package com.fo.addon;

import com.fo.addon.utils.ShipFilterLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 末地船纳入搜索过滤判定单测（忽略已访问记录开关） */
class ShipFilterLogicTest {

    @Test
    void normalMode_blacklistedSkipped() {
        // 忽略开关关：黑名单船排除，非黑名单船保留
        assertFalse(ShipFilterLogic.shouldIncludeShip(false, true, false));
        assertTrue(ShipFilterLogic.shouldIncludeShip(false, false, false));
    }

    @Test
    void ignoreVisitedMode_blacklistIgnored() {
        // 忽略开关开：黑名单船也纳入（重新扫描所有预测船）
        assertTrue(ShipFilterLogic.shouldIncludeShip(true, true, false));
        assertTrue(ShipFilterLogic.shouldIncludeShip(true, false, false));
    }

    @Test
    void sessionSkipAlwaysExcluded() {
        // 本会话跳过优先于一切：无论忽略开关如何都不纳入
        assertFalse(ShipFilterLogic.shouldIncludeShip(false, false, true));
        assertFalse(ShipFilterLogic.shouldIncludeShip(true, false, true));
        assertFalse(ShipFilterLogic.shouldIncludeShip(true, true, true));
    }
}
