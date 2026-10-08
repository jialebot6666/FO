package com.fo.addon.elytra.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public final class FOElytraLog {
    private static final String PREFIX = "[ElytraAuto] ";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int RING_SIZE = 400;
    private static final int KEEP_FILES = 10;
    public static volatile boolean debugEnabled = false;
    public static volatile boolean fileEnabled = false;
    public static volatile boolean fileVerbose = true;
    private static final Deque<String> RING = new ArrayDeque<String>(400);
    private static Path logFile;
    private static PrintWriter writer;
    private static Path gameDir;

    private FOElytraLog() {
    }

    public static synchronized void setGameDir(Path dir) {
        gameDir = dir;
    }

    public static synchronized Path directory() {
        if (logFile != null) {
            return logFile.getParent();
        }
        if (gameDir != null) {
            return gameDir.resolve("icehack-logs");
        }
        return Paths.get("icehack-logs", new String[0]);
    }

    public static synchronized void ensureOpen(Path gameDir, int keepFiles) {
        if (writer != null) {
            FOElytraLog.setGameDir(gameDir);
            fileEnabled = true;
            return;
        }
        FOElytraLog.openFile(gameDir, keepFiles);
    }

    public static synchronized void openFile(Path gameDir, int keepFiles) {
        FOElytraLog.closeFile();
        try {
            FOElytraLog.setGameDir(gameDir);
            Path dir = gameDir.resolve("icehack-logs");
            Files.createDirectories(dir, new FileAttribute[0]);
            logFile = dir.resolve("icehack-" + LocalDateTime.now().format(FILE_TS) + ".log");
            writer = new PrintWriter((Writer)Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE), true);
            fileEnabled = true;
            FOElytraLog.write("==== FO \u8be6\u7ec6\u65e5\u5fd7 ====");
            FOElytraLog.write("\u5f00\u59cb\u65f6\u95f4\uff1a" + String.valueOf(LocalDateTime.now()));
            FOElytraLog.pruneOldFiles(dir, Math.max(2, keepFiles));
        }
        catch (Throwable t) {
            fileEnabled = false;
            ChatUtils.warning((String)FOElytraLog.chatSafe("[ElytraAuto] \u6253\u4e0d\u5f00\u65e5\u5fd7\u6587\u4ef6\uff1a" + String.valueOf(t)), (Object[])new Object[0]);
        }
    }

    public static synchronized void closeFile() {
        if (writer != null) {
            FOElytraLog.write("==== \u65e5\u5fd7\u7ed3\u675f\uff1a" + String.valueOf(LocalDateTime.now()) + " ====");
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
        try (Stream<Path> stream = Files.list(dir);){
            ArrayList<Path> files = new ArrayList<Path>(stream.filter(p -> p.getFileName().toString().startsWith("icehack-")).filter(p -> p.getFileName().toString().endsWith(".log")).toList());
            if (files.size() <= keep) {
                return;
            }
            files.sort(Comparator.comparingLong(p -> p.toFile().lastModified()));
            for (int i = 0; i < files.size() - keep; ++i) {
                Files.deleteIfExists((Path)files.get(i));
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static synchronized void write(String line) {
        RING.addLast(line);
        while (RING.size() > 400) {
            RING.removeFirst();
        }
        if (writer != null) {
            try {
                writer.println(line);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private static String line(String level, String message) {
        return "[" + LocalDateTime.now().format(TS) + "] [" + level + "] " + message;
    }

    public static void info(String fmt, Object ... args) {
        String s = FOElytraLog.fmt(fmt, args);
        ChatUtils.info((String)FOElytraLog.chatSafe(PREFIX + s), (Object[])new Object[0]);
        FOElytraLog.write(FOElytraLog.line("INFO", s));
    }

    public static void tip(String fmt, Object ... args) {
        String s = FOElytraLog.fmt(fmt, args);
        ChatUtils.info((String)FOElytraLog.chatSafe(PREFIX + s), (Object[])new Object[0]);
        FOElytraLog.write(FOElytraLog.line("TIP ", s));
    }

    public static void warn(String fmt, Object ... args) {
        String s = FOElytraLog.fmt(fmt, args);
        ChatUtils.warning((String)FOElytraLog.chatSafe(PREFIX + s), (Object[])new Object[0]);
        FOElytraLog.write(FOElytraLog.line("WARN", s));
    }

    public static void err(String fmt, Object ... args) {
        String s = FOElytraLog.fmt(fmt, args);
        ChatUtils.error((String)FOElytraLog.chatSafe(PREFIX + s), (Object[])new Object[0]);
        FOElytraLog.write(FOElytraLog.line("ERR ", s));
    }

    public static void debug(String fmt, Object ... args) {
        String s = FOElytraLog.fmt(fmt, args);
        if (debugEnabled) {
            ChatUtils.info((String)FOElytraLog.chatSafe("[ElytraAuto] [\u8c03\u8bd5] " + s), (Object[])new Object[0]);
        }
        if (fileEnabled && fileVerbose) {
            FOElytraLog.write(FOElytraLog.line("DBG ", s));
        }
    }

    public static void detail(String fmt, Object ... args) {
        if (!fileEnabled || !fileVerbose) {
            return;
        }
        FOElytraLog.write(FOElytraLog.line("\u7ec6\u8282", FOElytraLog.fmt(fmt, args)));
    }

    public static void detailError(String where, Throwable t) {
        if (!fileEnabled) {
            return;
        }
        FOElytraLog.write(FOElytraLog.line("\u5f02\u5e38", where + " " + String.valueOf(t)));
        if (fileVerbose) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            for (String l : sw.toString().split("\n")) {
                FOElytraLog.write("        " + l);
            }
        }
    }

    public static void chatRaw(String text) {
        ChatUtils.info((String)FOElytraLog.chatSafe(PREFIX + text), (Object[])new Object[0]);
    }

    public static synchronized List<String> tail(int n) {
        ArrayList<String> all = new ArrayList<String>(RING);
        int from = Math.max(0, all.size() - Math.max(1, n));
        return all.subList(from, all.size());
    }

    public static synchronized List<String> snapshot(String reason, int lines, int chatLines) {
        List<String> tail;
        block3: {
            tail = FOElytraLog.tail(lines);
            try {
                Path dir = FOElytraLog.directory();
                Files.createDirectories(dir, new FileAttribute[0]);
                Path crash = dir.resolve("icehack-crash-" + LocalDateTime.now().format(FILE_TS) + ".log");
                ArrayList<String> out = new ArrayList<String>();
                out.add("==== \u5931\u8d25\u5feb\u7167 " + String.valueOf(LocalDateTime.now()) + " ====");
                out.add("\u539f\u56e0\uff1a" + reason);
                out.addAll(tail);
                Files.write(crash, out, StandardCharsets.UTF_8, new OpenOption[0]);
                if (writer != null) {
                    writer.println("[\u5feb\u7167] \u5df2\u5199\u5165 " + String.valueOf(crash));
                }
            }
            catch (Throwable t) {
                if (writer == null) break block3;
                writer.println("[\u5feb\u7167] \u5199\u5165\u5931\u8d25\uff1a" + String.valueOf(t));
            }
        }
        int from = Math.max(0, tail.size() - Math.max(0, chatLines));
        return new ArrayList<String>(tail.subList(from, tail.size()));
    }

    private static String fmt(String fmt, Object ... args) {
        try {
            return args == null || args.length == 0 ? fmt : String.format(fmt, args);
        }
        catch (Throwable t) {
            return fmt + " " + Arrays.toString(args);
        }
    }

    private static String chatSafe(String s) {
        return s == null ? "" : s.replace("%", "%%");
    }
}

