package com.fo.addon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前端汉化 + FO 前缀守护测试（对应 AGENTS.md 铁律 3/4：所有用户可见文本一律中文、模块名统一 "FO " 前缀）。
 *
 * <p>Meteor 的 {@code EnumSetting} 下拉框、模块列表、HUD、聊天输出都会走枚举的 {@code toString()}，
 * 所以「移植进来的每一个会出现在界面上的枚举」都必须有中文文本。这条测试把「以后有人新增英文枚举」
 * 直接卡在 CI 里。</p>
 *
 * <p>用 {@code Class.forName} 而不是直接引用类型：避免测试为了加载嵌套枚举而牵连外层模块类
 * （那些类依赖 MC 运行时）。</p>
 */
class ElytraUiTextTest {

    /** 移植进来的、会出现在界面上的枚举（模块下拉框 / 模块列表 / HUD / 聊天栏）。 */
    private static final String[] UI_ENUMS = {
        "com.fo.addon.elytra.modules.AutoElytraFlight$Mode",
        "com.fo.addon.elytra.modules.AutoElytraFlight$State",
        "com.fo.addon.elytra.modules.AutoOminousVault$DisplayMode",
        "com.fo.addon.elytra.modules.AutoOminousVault$LocateMode",
        "com.fo.addon.elytra.modules.AutoOminousVault$Phase",
        "com.fo.addon.elytra.modules.SpeedMeter$Unit",
        "com.fo.addon.elytra.modules.SpeedMeter$EtaSpeed",
        "com.fo.addon.elytra.core.MendTask$State",
        "com.fo.addon.elytra.core.SupplyTask$State",
        "com.fo.addon.elytra.core.VaultOpener$State",
        "com.fo.addon.elytra.core.TaskStatus",
        "com.fo.addon.elytra.core.TrialChamberLocator$Source",
        "com.fo.addon.elytra.core.FireballDeflector$Result",
        "com.fo.addon.elytra.core.LavaEscape$Result",
        "com.fo.addon.elytra.core.LavaPredictor$Result",
    };

    private static boolean hasCjk(String s) {
        if (s == null) return false;
        // Java 源码里中文以字面 \\uXXXX 转义存储，先解码
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u') {
                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                i += 5;
            } else {
                sb.append(c);
            }
        }
        String decoded = sb.toString();
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
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
                        .append("\"\n");
                }
            }
        }
        assertTrue(problems.length() == 0, "以下枚举常量缺少中文文本（会出现在界面上）：\n" + problems);
    }

    /** 模块名必须以 "FO " 开头（防与其他中文 addon 重名）。 */
    @Test
    void everyElytraModuleNameStartsWithFoPrefix() throws IOException {
        Path modulesDir = Paths.get("src/main/java/com/fo/addon/elytra/modules");
        List<Path> files;
        try (var stream = Files.list(modulesDir)) {
            files = stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(!files.isEmpty(), "没有找到 elytra 模块源码目录");
        Pattern p = Pattern.compile("super\\(\"([^\"]*)\"");
        StringBuilder problems = new StringBuilder();
        for (Path f : files) {
            String src = Files.readString(f);
            Matcher m = p.matcher(src);
            if (!m.find()) {
                problems.append(f.getFileName()).append(" 找不到 super(\"...\") 模块名\n");
                continue;
            }
            String name = m.group(1);
            if (!name.startsWith("FO ")) {
                problems.append(f.getFileName()).append(" 模块名 \"").append(name).append("\" 不是以 \"FO \" 开头\n");
            }
            if (!hasCjk(name)) {
                problems.append(f.getFileName()).append(" 模块名 \"").append(name).append("\" 不是中文\n");
            }
        }
        assertTrue(problems.length() == 0, "模块命名规范违规：\n" + problems);
    }
}
