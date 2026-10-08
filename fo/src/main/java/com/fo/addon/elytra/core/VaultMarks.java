package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class VaultMarks {
    private static final String FILE_NAME = "icehack-vaults.txt";
    private static final String TMP_SUFFIX = ".tmp";
    private static final String HEADER = "# FO \u5df2\u6253\u5f00\u5b9d\u5e93\u6807\u8bb0\uff1a\u6bcf\u884c\u300c\u7ef4\u5ea6 x y z\u300d\uff1b\u5220\u6389\u67d0\u884c\u5373\u53ef\u91cd\u65b0\u8003\u8651\u90a3\u4e2a\u5b9d\u5e93";
    private static final int MIN_FIELDS = 4;
    private static volatile VaultMarks instance;
    private final Map<String, Set<Long>> marks = new LinkedHashMap<String, Set<Long>>();
    private Path cachedFile;
    private boolean noDirWarned;

    private VaultMarks() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static VaultMarks get() {
        VaultMarks local = instance;
        if (local != null) return local;
        Class<VaultMarks> clazz = VaultMarks.class;
        synchronized (VaultMarks.class) {
            local = instance;
            if (local != null) return local;
            local = new VaultMarks();
            local.load();
            instance = local;
            // ** MonitorExit[var1_1] (shouldn't be in output)
            return local;
        }
    }

    public synchronized boolean isMarked(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        String dim = VaultMarks.currentDimensionId();
        if (dim == null) {
            FOElytraLog.detail("VaultMarks.isMarked\uff1a\u62ff\u4e0d\u5230\u5f53\u524d\u7ef4\u5ea6\uff08\u4e16\u754c\u8fd8\u6ca1\u52a0\u8f7d\uff1f\uff09\uff0c%s \u6309\u300c\u672a\u6807\u8bb0\u300d\u5904\u7406", pos.toShortString());
            return false;
        }
        return this.contains(dim, pos.getX(), pos.getY(), pos.getZ());
    }

    public synchronized boolean isMarked(String dimensionId, int x, int y, int z) {
        if (dimensionId == null) {
            return false;
        }
        return this.contains(dimensionId, x, y, z);
    }

    private boolean contains(String dim, int x, int y, int z) {
        Set<Long> set = this.marks.get(dim);
        if (set == null || set.isEmpty()) {
            return false;
        }
        return set.contains(new BlockPos(x, y, z).asLong());
    }

    public synchronized int count() {
        int total = 0;
        for (Set<Long> set : this.marks.values()) {
            total += set.size();
        }
        return total;
    }

    public synchronized int countIn(String dimensionId) {
        if (dimensionId == null) {
            return 0;
        }
        Set<Long> set = this.marks.get(dimensionId);
        return set == null ? 0 : set.size();
    }

    public synchronized String filePath() {
        Path f = this.file();
        return f == null ? "(\u6e38\u620f\u76ee\u5f55\u672a\u5c31\u7eea) icehack-vaults.txt" : f.toString();
    }

    public synchronized void mark(BlockPos pos) {
        if (pos == null) {
            return;
        }
        String dim = VaultMarks.currentDimensionId();
        if (dim == null) {
            FOElytraLog.warn("\u62ff\u4e0d\u5230\u5f53\u524d\u7ef4\u5ea6\uff0c\u8fd9\u4e2a\u5b9d\u5e93\uff08%s\uff09\u6ca1\u80fd\u8bb0\u8fdb\u6301\u4e45\u6807\u8bb0\u8868", pos.toShortString());
            return;
        }
        long key = pos.toImmutable().asLong();
        Set set = this.marks.computeIfAbsent(dim, k -> new LinkedHashSet());
        if (!set.add(key)) {
            FOElytraLog.detail("VaultMarks\uff1a%s %s \u65e9\u5c31\u6807\u8bb0\u8fc7\uff08\u4e0d\u91cd\u590d\u843d\u76d8\uff09", dim, pos.toShortString());
            return;
        }
        FOElytraLog.detail("VaultMarks\uff1a\u6807\u8bb0\u5df2\u5f00\u5b9d\u5e93 %s %s\uff08\u73b0\u5728\u5171 %d \u6761\uff0c\u5f53\u524d\u7ef4\u5ea6 %d \u6761\uff09", dim, pos.toShortString(), this.count(), this.countIn(dim));
        this.save();
    }

    public synchronized void clear() {
        int before = this.count();
        this.marks.clear();
        Path f = this.file();
        boolean deleted = false;
        try {
            if (f != null) {
                deleted = Files.deleteIfExists(f);
            }
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.clear(" + String.valueOf(f) + ")", t);
        }
        FOElytraLog.info("\u5df2\u6e05\u7a7a\u5b9d\u5e93\u6807\u8bb0\uff1a%d \u6761\uff08\u6587\u4ef6%s\uff1a%s\uff09", before, deleted ? "\u5df2\u5220\u9664" : "\u672c\u6765\u5c31\u4e0d\u5b58\u5728", this.filePath());
    }

    public synchronized void reload() {
        this.load();
        FOElytraLog.detail("VaultMarks.reload \u5b8c\u6210\uff1a\u73b0\u5728\u5171 %d \u6761\u6807\u8bb0\uff08%d \u4e2a\u7ef4\u5ea6\uff09", this.count(), this.marks.size());
    }

    private void load() {
        this.marks.clear();
        Path f = this.file();
        if (f == null) {
            if (!this.noDirWarned) {
                this.noDirWarned = true;
                FOElytraLog.warn("\u62ff\u4e0d\u5230\u6e38\u620f\u76ee\u5f55\uff0c\u672c\u6b21\u7684\u5b9d\u5e93\u6807\u8bb0\u53ea\u5b58\u5728\u5185\u5b58\u91cc\uff08\u5173\u6e38\u620f\u5c31\u4e22\uff09", new Object[0]);
            }
            return;
        }
        if (!Files.exists(f, new LinkOption[0])) {
            FOElytraLog.detail("VaultMarks\uff1a\u6807\u8bb0\u6587\u4ef6\u8fd8\u4e0d\u5b58\u5728\uff08%s\uff09\uff0c\u4ece\u7a7a\u5f00\u59cb", f);
            return;
        }
        int bad = 0;
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) {
                    ++bad;
                    FOElytraLog.detail("VaultMarks\uff1a\u8df3\u8fc7\u574f\u884c\uff08\u5b57\u6bb5\u4e0d\u8db3 %d \u4e2a\uff09\uff1a%s", 4, line);
                    continue;
                }
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    this.marks.computeIfAbsent(parts[0], k -> new LinkedHashSet()).add(new BlockPos(x, y, z).asLong());
                }
                catch (Throwable t) {
                    ++bad;
                    FOElytraLog.detail("VaultMarks\uff1a\u8df3\u8fc7\u574f\u884c\uff08\u5750\u6807\u89e3\u6790\u5931\u8d25\uff09\uff1a%s\uff08%s\uff09", line, String.valueOf(t));
                }
            }
            FOElytraLog.detail("VaultMarks\uff1a\u4ece %s \u8bfb\u5165 %d \u6761\u6807\u8bb0\uff08%d \u4e2a\u7ef4\u5ea6\uff0c\u8df3\u8fc7 %d \u884c\uff09", f, this.count(), this.marks.size(), bad);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.load(" + String.valueOf(f) + ")", t);
        }
    }

    private void save() {
        Path f = this.file();
        if (f == null) {
            if (!this.noDirWarned) {
                this.noDirWarned = true;
                FOElytraLog.warn("\u62ff\u4e0d\u5230\u6e38\u620f\u76ee\u5f55\uff0c\u5b9d\u5e93\u6807\u8bb0\u8fd9\u6b21\u53ea\u5b58\u5728\u5185\u5b58\u91cc\uff08\u5173\u6e38\u620f\u5c31\u4e22\uff09", new Object[0]);
            }
            return;
        }
        try {
            ArrayList<String> lines = new ArrayList<String>();
            lines.add(HEADER);
            for (Map.Entry<String, Set<Long>> e : this.marks.entrySet()) {
                for (long packed : e.getValue()) {
                    BlockPos p = BlockPos.fromLong((long)packed);
                    lines.add(e.getKey() + " " + p.getX() + " " + p.getY() + " " + p.getZ());
                }
            }
            Path tmp = f.resolveSibling(f.getFileName().toString() + TMP_SUFFIX);
            Files.write(tmp, lines, StandardCharsets.UTF_8, new OpenOption[0]);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException notAtomic) {
                FOElytraLog.detail("VaultMarks\uff1a\u6587\u4ef6\u7cfb\u7edf\u4e0d\u652f\u6301\u539f\u5b50\u66ff\u6362\uff0c\u9000\u56de\u666e\u901a\u8986\u76d6\uff1a%s", String.valueOf(notAtomic));
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            FOElytraLog.detail("VaultMarks\uff1a\u5df2\u843d\u76d8 %d \u6761\u6807\u8bb0\u5230 %s", this.count(), f);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.save(" + String.valueOf(f) + ")", t);
        }
    }

    private static String currentDimensionId() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return null;
            }
            RegistryKey key = mc.world.getRegistryKey();
            if (key == null) {
                return null;
            }
            Identifier id = key.getValue();
            return id == null ? null : id.toString();
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.currentDimensionId", t);
            return null;
        }
    }

    private Path file() {
        if (this.cachedFile != null) {
            return this.cachedFile;
        }
        try {
            File dir;
            MinecraftClient mc = MinecraftClient.getInstance();
            File file = dir = mc == null ? null : mc.runDirectory;
            if (dir == null) {
                return null;
            }
            this.cachedFile = dir.toPath().resolve(FILE_NAME).toAbsolutePath();
            return this.cachedFile;
        }
        catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.file", t);
            return null;
        }
    }
}

