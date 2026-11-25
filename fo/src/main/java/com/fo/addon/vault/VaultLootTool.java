package com.fo.addon.vault;

import com.fo.addon.utils.VaultLootLogic;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 战利品预测引擎的外部进程封装。
 *
 * <p>引擎是 SeedMapper 同源的 cubiomes loot 库编译出来的 {@code vaulttool.exe}
 * （1.21.11 的 4 张宝库战利品表 + 附魔模拟），打包在 mod 资源 {@code /fo/vault/vaulttool.exe}，
 * 首次使用时释放到 {@code <游戏目录>/fo/tools/}，之后按 {@code 种子/序号} 调用。
 * 只支持 Windows（引擎是 Windows 可执行文件），其它系统给出明确提示。
 */
public final class VaultLootTool {
    /** 检测结果：全服第 offset 次开 + 之后的 32 位随机状态串。 */
    public record Detection(int offset, String state) {
    }

    /** 官方参考二进制的 SHA-256（与源码包 VERSION.txt 一致，来源 xpple/cubiomes 06393cf）。 */
    private static final String EXPECTED_SHA256 = "4b96414e647334d0c0a351b9e5177745ec88978814947cc9d3424f9770f7df64";
    private static final String ENGINE_RESOURCE = "/fo/vault/vaulttool.exe";

    private VaultLootTool() {
    }

    public static boolean isPlatformSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** 释放（或复用）引擎可执行文件，并强制校验 SHA-256。 */
    public static Path ensure() throws IOException {
        if (!isPlatformSupported()) {
            throw new IOException("战利品引擎仅支持 Windows（当前系统：" + System.getProperty("os.name") + "）");
        }
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("fo").resolve("tools");
        Files.createDirectories(dir);
        Path exe = dir.resolve("vaulttool.exe");

        byte[] data;
        try (InputStream in = VaultLootTool.class.getResourceAsStream(ENGINE_RESOURCE)) {
            if (in == null) throw new IOException("mod 里缺少内置战利品引擎 vaulttool.exe");
            data = in.readAllBytes();
        }
        if (!EXPECTED_SHA256.equals(sha256(data))) {
            throw new IOException("内置战利品引擎校验失败（SHA-256 不匹配），已拒绝执行");
        }

        if (!Files.exists(exe) || !EXPECTED_SHA256.equals(sha256(Files.readAllBytes(exe)))) {
            Files.write(exe, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        if (!EXPECTED_SHA256.equals(sha256(Files.readAllBytes(exe)))) {
            throw new IOException("战利品引擎写入后校验失败，已拒绝执行");
        }
        return exe;
    }

    private static String sha256(byte[] data) throws IOException {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("JVM 缺少 SHA-256 实现", e);
        }
    }

    /**
     * 在引擎内部扫描 offset 之后的 limit 次开箱，返回最多 amount 条目标物品命中。
     *
     * <p>注意：扫描与过滤都在 {@code vaulttool.exe} 里做，Java 侧只解析几十行输出——
     * 早期「把 10 万次开箱的物品全解析成对象」会把游戏堆冲得很高（用户 2026-11-25 的
     * G1 崩溃报告里，崩溃前夕正好跑过一次 10 万次 detect），所以改成这个低分配模式。
     */
    public static List<VaultLootLogic.Hit> search(long seed, boolean ominous, int offset, int limit, int amount, String targetSpec) throws IOException {
        List<String> lines = run(ensure(), "search", Long.toString(seed), ominous ? "1" : "0",
            Integer.toString(offset), Integer.toString(limit), Integer.toString(amount), targetSpec);
        List<VaultLootLogic.Hit> out = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("HIT ")) continue;
            String[] parts = line.split(" ");
            if (parts.length < 4) continue;
            try {
                out.add(new VaultLootLogic.Hit(Integer.parseInt(parts[1]), parts[2], Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** 用弹出物品反推全服第几次开；没有匹配返回 null。progress 每扫描 100 万次回调一次。 */
    public static Detection detect(long seed, boolean ominous, int start, int maxAttempts, String spec,
                                   java.util.function.IntConsumer progress) throws IOException {
        List<String> command = new ArrayList<>();
        Path exe = ensure();
        command.add(exe.toAbsolutePath().toString());
        command.add("detect");
        command.add(Long.toString(seed));
        command.add(ominous ? "1" : "0");
        command.add(Integer.toString(start));
        command.add(Integer.toString(maxAttempts));
        command.add(spec);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(exe.getParent().toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        Detection detection = null;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PROGRESS ")) {
                    try {
                        progress.accept(Integer.parseInt(line.substring(9).trim()));
                    } catch (NumberFormatException ignored) {
                    }
                } else if (line.startsWith("MATCH ")) {
                    String[] parts = line.split(" ");
                    try {
                        detection = new Detection(Integer.parseInt(parts[1]), parts.length > 3 ? parts[3] : "");
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        try {
            if (!process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("战利品引擎超时（300 秒）");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        return detection;
    }

    /** 扫描 0..maxScan 里所有与弹出物品（含顺序）匹配的 offset（候选集）。 */
    public static List<Integer> matches(long seed, boolean ominous, int maxScan, String spec,
                                        java.util.function.IntConsumer progress) throws IOException {
        List<Integer> out = new ArrayList<>();
        stream(ensure(), progress, (line) -> {
            if (line.startsWith("M ")) {
                try {
                    out.add(Integer.parseInt(line.substring(2).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }, "matches", Long.toString(seed), ominous ? "1" : "0", Integer.toString(maxScan), spec);
        return out;
    }

    /** 用上一次的候选集缩小范围：只保留与候选相差 1..delta 次的匹配 offset（只扫 [rangeStart, rangeEnd)）。 */
    public static List<Integer> narrow(long seed, boolean ominous, int rangeStart, int rangeEnd, int delta, String spec,
                                       List<Integer> candidates, java.util.function.IntConsumer progress) throws IOException {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("fo").resolve("tools");
        Files.createDirectories(dir);
        Path candFile = dir.resolve("vault-candidates.txt");
        StringBuilder text = new StringBuilder();
        for (Integer candidate : candidates) {
            text.append(candidate).append('\n');
        }
        Files.writeString(candFile, text.toString());
        List<Integer> out = new ArrayList<>();
        stream(ensure(), progress, (line) -> {
            if (line.startsWith("KEEP ")) {
                try {
                    out.add(Integer.parseInt(line.substring(5).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }, "narrow", Long.toString(seed), ominous ? "1" : "0", Integer.toString(rangeStart),
            Integer.toString(rangeEnd), Integer.toString(delta), spec, candFile.toAbsolutePath().toString());
        return out;
    }

    private interface LineSink {
        void accept(String line);
    }

    private static void stream(Path exe, java.util.function.IntConsumer progress, LineSink sink, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(exe.toAbsolutePath().toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(exe.getParent().toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PROGRESS ")) {
                    try {
                        progress.accept(Integer.parseInt(line.substring(9).trim()));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    sink.accept(line);
                }
            }
        }
        try {
            if (!process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("战利品引擎超时（300 秒）");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> run(Path exe, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(exe.toAbsolutePath().toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(exe.getParent().toFile()); // 状态缓存写在 exe 旁边（fo/tools，跨会话复用）
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        try {
            if (!process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("战利品引擎超时（120 秒）");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        return lines;
    }
}
