package com.fo.addon.elytra.core;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
public final class FOElytraLog {
    private static final String PREFIX = "[ElytraAuto] ";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int RING_SIZE = 400;
    private static final int KEEP_FILES = 10;
    public static volatile boolean debugEnabled = false;
    public static volatile boolean fileEnabled = false;
    public static volatile boolean fileVerbose = true;
    private static final Deque<String> RING = new ArrayDeque<>(RING_SIZE);
    private static Path logFile;
    private static PrintWriter writer;
    private static Path gameDir;
    private FOElytraLog() {
    }
    public static synchronized void setGameDir(Path dir) {
        gameDir = dir;
    }
    public static synchronized Path directory() {
        if (logFile != null) return logFile.getParent();
        if (gameDir != null) return gameDir.resolve("fo-elytra-logs");
        return Paths.get("fo-elytra-logs");
    }
    public static synchronized void ensureOpen(Path gameDir, int keepFiles) {
        if (writer != null) {
            setGameDir(gameDir);
            fileEnabled = true;
            return;
        }
        openFile(gameDir, keepFiles);
    }
    public static synchronized void openFile(Path gameDir, int keepFiles) {
        closeFile();
        try {
            setGameDir(gameDir);
            Path dir = gameDir.resolve("fo-elytra-logs");
            Files.createDirectories(dir);
            logFile = dir.resolve("fo-elytra-" + LocalDateTime.now().format(FILE_TS) + ".log");
            writer = new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE), true);
            fileEnabled = true;
            write("==== FO 鞘翅详细日志 ====");
            write("开始时间：" + LocalDateTime.now());
            pruneOldFiles(dir, Math.max(2, keepFiles));
        } catch (Throwable t) {
            fileEnabled = false;
            ChatUtils.warning(chatSafe(PREFIX + "打不开日志文件：" + t));
        }
    }
    public static synchronized void closeFile() {
        if (writer != null) {
            write("==== 日志结束：" + LocalDateTime.now() + " ====");
            writer.flush();
            writer.close();
            writer = null;
        }
        fileEnabled = false;
    }
    public static synchronized Path currentFile() {
        return logFile;
    }
    private static void pruneOldFiles(Path dir, int keep) {
        try (var stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>(stream
                .filter(p -> p.getFileName().toString().startsWith("fo-elytra-"))
                .filter(p -> p.getFileName().toString().endsWith(".log"))
                .toList());
            if (files.size() <= keep) return;
            files.sort(Comparator.comparingLong(p -> p.toFile().lastModified()));
            for (int i = 0; i < files.size() - keep; i++) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (Throwable ignored) {
        }
    }
    private static synchronized void write(String line) {
        RING.addLast(line);
        while (RING.size() > RING_SIZE) RING.removeFirst();
        if (writer != null) {
            try {
                writer.println(line);
            } catch (Throwable ignored) {
            }
        }
    }
    private static String line(String level, String message) {
        return "[" + LocalDateTime.now().format(TS) + "] [" + level + "] " + message;
    }
    public static void info(String fmt, Object... args) {
        String s = fmt(fmt, args);
        ChatUtils.info(chatSafe(PREFIX + s));
        write(line("INFO", s));
    }
    public static void tip(String fmt, Object... args) {
        String s = fmt(fmt, args);
        ChatUtils.info(chatSafe(PREFIX + s));
        write(line("TIP ", s));
    }
    public static void warn(String fmt, Object... args) {
        String s = fmt(fmt, args);
        ChatUtils.warning(chatSafe(PREFIX + s));
        write(line("WARN", s));
    }
    public static void err(String fmt, Object... args) {
        String s = fmt(fmt, args);
        ChatUtils.error(chatSafe(PREFIX + s));
        write(line("ERR ", s));
    }
    public static void debug(String fmt, Object... args) {
        String s = fmt(fmt, args);
        if (debugEnabled) ChatUtils.info(chatSafe(PREFIX + "[调试] " + s));
        if (fileEnabled && fileVerbose) write(line("DBG ", s));
    }
    public static void detail(String fmt, Object... args) {
        if (!fileEnabled || !fileVerbose) return;
        write(line("细节", fmt(fmt, args)));
    }
    public static void detailError(String where, Throwable t) {
        if (!fileEnabled) return;
        write(line("异常", where + " " + t));
        if (fileVerbose) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            for (String l : sw.toString().split("\n")) write("        " + l);
        }
    }
    public static void chatRaw(String text) {
        ChatUtils.info(chatSafe(PREFIX + text));
    }
    public static synchronized List<String> tail(int n) {
        List<String> all = new ArrayList<>(RING);
        int from = Math.max(0, all.size() - Math.max(1, n));
        return all.subList(from, all.size());
    }
    public static synchronized List<String> snapshot(String reason, int lines, int chatLines) {
        List<String> tail = tail(lines);
        try {
            Path dir = directory();
            Files.createDirectories(dir);
            Path crash = dir.resolve("fo-elytra-crash-" + LocalDateTime.now().format(FILE_TS) + ".log");
            List<String> out = new ArrayList<>();
            out.add("==== 失败快照 " + LocalDateTime.now() + " ====");
            out.add("原因：" + reason);
            out.addAll(tail);
            Files.write(crash, out, StandardCharsets.UTF_8);
            if (writer != null) writer.println("[快照] 已写入 " + crash);
        } catch (Throwable t) {
            if (writer != null) writer.println("[快照] 写入失败：" + t);
        }
        int from = Math.max(0, tail.size() - Math.max(0, chatLines));
        return new ArrayList<>(tail.subList(from, tail.size()));
    }
    private static String fmt(String fmt, Object... args) {
        try {
            return args == null || args.length == 0 ? fmt : String.format(fmt, args);
        } catch (Throwable t) {
            return fmt + " " + java.util.Arrays.toString(args);
        }
    }
    private static String chatSafe(String s) {
        return s == null ? "" : s.replace("%", "%%");
    }
}
