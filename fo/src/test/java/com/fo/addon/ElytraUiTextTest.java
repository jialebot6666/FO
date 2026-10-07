package com.fo.addon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前端汉化守护测试（对应 AGENTS.md 铁律 3：所有用户可见文本一律中文）。
 *
 * <p>Meteor 的 {@code EnumSetting} 下拉框、模块列表、HUD、聊天输出都会走枚举的 {@code toString()}，
 * 所以「移植进来的每一个会出现在界面上的枚举」都必须有中文文本。这条测试把「以后有人新增英文枚举」
 * 直接卡在 CI 里，而不是等玩家看到 "BlocksPerSecond" 才发现。</p>
 *
 * <p>用 {@code Class.forName} 而不是直接引用类型：避免测试为了加载嵌套枚举而牵连外层模块类
 * （那些类依赖 MC 运行时）。</p>
 */
class ElytraUiTextTest {

    /** 移植进来的、会出现在界面上的枚举（模块下拉框 / 模块列表 / HUD / 聊天栏）。 */
    private static final String[] UI_ENUMS = {
        "com.fo.addon.elytra.modules.AutoElytraFlight$Mode",
        "com.fo.addon.elytra.modules.AutoElytraFlight$State",
        "com.fo.addon.elytra.modules.AutoOminousVault$Phase",
        "com.fo.addon.elytra.modules.AutoOminousVault$LocateMode",
        "com.fo.addon.elytra.modules.AutoOminousVault$DisplayMode",
        "com.fo.addon.elytra.modules.SpeedMeter$Unit",
        "com.fo.addon.elytra.modules.SpeedMeter$EtaSpeed",
        "com.fo.addon.elytra.core.MendTask$State",
        "com.fo.addon.elytra.core.SupplyTask$State",
        "com.fo.addon.elytra.core.VaultOpener$State",
        "com.fo.addon.elytra.core.TaskStatus",
        "com.fo.addon.elytra.core.TrialChamberLocator$Source",
    };

    private static boolean hasCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    @Test
    void everyUiEnumRendersChineseText() throws Exception {
        StringBuilder problems = new StringBuilder();
        for (String className : UI_ENUMS) {
            Class<?> type = Class.forName(className);
            Object[] constants = type.getEnumConstants();
            assertNotNull(constants, className + " 不是枚举类型");
            assertTrue(constants.length > 0, className + " 没有任何枚举常量");
            for (Object constant : constants) {
                String text = String.valueOf(constant);
                if (!hasCjk(text)) {
                    problems.append(className)
                        .append('.')
                        .append(((Enum<?>) constant).name())
                        .append(" => \"")
                        .append(text)
                        .append("\"; ");
                }
            }
        }
        assertEquals("", problems.toString(), "以下枚举缺少中文界面文本：" + problems);
    }

    @Test
    void speedUnitLabelsAreExact() throws Exception {
        Class<?> unit = Class.forName("com.fo.addon.elytra.modules.SpeedMeter$Unit");
        Object[] constants = unit.getEnumConstants();
        assertEquals(3, constants.length);
        assertEquals("格/秒", String.valueOf(constants[0]));
        assertEquals("公里/小时", String.valueOf(constants[1]));
        assertEquals("格/刻", String.valueOf(constants[2]));
    }

    @Test
    void vaultLocateModeLabelsAreExact() throws Exception {
        Class<?> mode = Class.forName("com.fo.addon.elytra.modules.AutoOminousVault$LocateMode");
        Object[] constants = mode.getEnumConstants();
        assertEquals(5, constants.length);
        assertEquals("自动（有什么用什么）", String.valueOf(constants[0]));
        assertEquals("种子推算", String.valueOf(constants[1]));
        assertEquals("坐标列表", String.valueOf(constants[2]));
        assertEquals("藏宝图", String.valueOf(constants[3]));
        assertEquals("单机世界搜索", String.valueOf(constants[4]));
    }
}
