package com.fo.addon;

import com.fo.addon.utils.StartupCheckLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** StartupCheckLogic 纯逻辑测试：启动必需品判定（V4.40 满盒+末影箱可启动） */
public class StartupCheckLogicTest {

    @Test
    void requiresFortunePickaxe() {
        assertEquals("时运镐", StartupCheckLogic.missingItem(false, true, true, false, true));
    }

    @Test
    void fullShulkerWithEnderChestCanStart() {
        // 用户场景：背包只有满盒（无空盒）+ 有末影箱 → 允许启动（运行中走末影箱取盒）
        assertNull(StartupCheckLogic.missingItem(true, false, true, false, true));
    }

    @Test
    void noShulkerAndNoEnderChestRejected() {
        assertEquals("空潜影盒或末影箱", StartupCheckLogic.missingItem(true, false, false, false, true));
    }

    @Test
    void enderChestAlwaysRequired() {
        // 有空盒但没末影箱 → 仍拒绝（末影箱是存盒/取盒必需）
        assertEquals("末影箱", StartupCheckLogic.missingItem(true, true, false, false, true));
    }

    @Test
    void diamondModeRequiresCraftingTable() {
        assertEquals("工作台", StartupCheckLogic.missingItem(true, true, true, true, false));
        assertNull(StartupCheckLogic.missingItem(true, true, true, true, true));
    }

    @Test
    void allPresentPasses() {
        assertNull(StartupCheckLogic.missingItem(true, true, true, false, true));
    }
}
