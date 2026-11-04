package com.fo.addon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动搬运模块（StashMover）的日志输出禁止出现坐标（用户长期规则，V6.7p13 起）。
 *
 * <p>无政府服里坐标泄露 = 基地位置暴露，所以无论开不开「调试输出」，
 * 聊天栏/日志都不许带 (x,y,z)。相对距离（格 / 米 / 眼睛距）不是坐标，允许保留。
 *
 * <p>范围只限自动搬运模块，不动其它模块。
 */
class NoCoordinatesInLogsTest {

    /** 会往聊天栏、日志里写内容的调用。 */
    private static final Pattern LOG_CALL = Pattern.compile(
        "\\b(info|warning|error|debug|debugMsg|enterStuck|warnThrottled|sendMessage|sendPrivate|setTrioState|setThrowSub)\\s*\\(");

    /** 会打印出绝对坐标的写法。 */
    private static final Pattern COORDINATE = Pattern.compile(
        "toShortString|formatPos|\\.getX\\(\\)|\\.getY\\(\\)|\\.getZ\\(\\)|"
            + "\\.x\\(\\)|\\.y\\(\\)|\\.z\\(\\)|%d,\\s*%d,\\s*%d|\\(\\{\\}, \\{\\}, \\{\\}\\)|BlockPos\\{");

    @Test
    void 自动搬运日志语句都不得输出坐标() throws IOException {
        Path file = Paths.get("src/main/java/com/fo/addon/modules/StashMover.java");
        assertTrue(Files.isRegularFile(file), "找不到源码: " + file.toAbsolutePath());

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!LOG_CALL.matcher(lines.get(i)).find()) continue;
            // 日志调用常常跨行拼接，收集到分号为止（最多 8 行）
            StringBuilder stmt = new StringBuilder();
            for (int j = i; j < Math.min(i + 8, lines.size()); j++) {
                stmt.append(lines.get(j)).append('\n');
                if (lines.get(j).contains(";")) break;
            }
            if (COORDINATE.matcher(stmt).find()) offenders.add("StashMover.java:" + (i + 1));
        }

        assertTrue(offenders.isEmpty(),
            "以下自动搬运日志语句输出了绝对坐标，请改成不含坐标的措辞：\n" + String.join("\n", offenders));
    }
}
