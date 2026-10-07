package com.fo.addon.elytra.core;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
public final class VaultMarks {
    private static final String FILE_NAME = "fo-elytra-vaults.txt";
    private static final String TMP_SUFFIX = ".tmp";
    private static final String HEADER = "# FO 已打开宝库标记：每行「维度 x y z」；删掉某行即可重新考虑那个宝库";
    private static final int MIN_FIELDS = 4;
    private static volatile VaultMarks instance;
    private final Map<String, Set<Long>> marks = new LinkedHashMap<>();
    private Path cachedFile;
    private boolean noDirWarned;
    private VaultMarks() {
    }
    public static VaultMarks get() {
        VaultMarks local = instance;
        if (local == null) {
            synchronized (VaultMarks.class) {
                local = instance;
                if (local == null) {
                    local = new VaultMarks();
                    local.load();
                    instance = local;
                }
            }
        }
        return local;
    }
    public synchronized boolean isMarked(BlockPos pos) {
        if (pos == null) return false;
        String dim = currentDimensionId();
        if (dim == null) {
            FOElytraLog.detail("VaultMarks.isMarked：拿不到当前维度（世界还没加载？），%s 按「未标记」处理",
                pos.toShortString());
            return false;
        }
        return contains(dim, pos.getX(), pos.getY(), pos.getZ());
    }
    public synchronized boolean isMarked(String dimensionId, int x, int y, int z) {
        if (dimensionId == null) return false;
        return contains(dimensionId, x, y, z);
    }
    private boolean contains(String dim, int x, int y, int z) {
        Set<Long> set = marks.get(dim);
        if (set == null || set.isEmpty()) return false;
        return set.contains(new BlockPos(x, y, z).asLong());
    }
    public synchronized int count() {
        int total = 0;
        for (Set<Long> set : marks.values()) total += set.size();
        return total;
    }
    public synchronized int countIn(String dimensionId) {
        if (dimensionId == null) return 0;
        Set<Long> set = marks.get(dimensionId);
        return set == null ? 0 : set.size();
    }
    public synchronized String filePath() {
        Path f = file();
        return f == null ? "(游戏目录未就绪) " + FILE_NAME : f.toString();
    }
    public synchronized void mark(BlockPos pos) {
        if (pos == null) return;
        String dim = currentDimensionId();
        if (dim == null) {
            FOElytraLog.warn("拿不到当前维度，这个宝库（%s）没能记进持久标记表", pos.toShortString());
            return;
        }
        long key = pos.toImmutable().asLong();
        Set<Long> set = marks.computeIfAbsent(dim, k -> new LinkedHashSet<>());
        if (!set.add(key)) {
            FOElytraLog.detail("VaultMarks：%s %s 早就标记过（不重复落盘）", dim, pos.toShortString());
            return;
        }
        FOElytraLog.detail("VaultMarks：标记已开宝库 %s %s（现在共 %d 条，当前维度 %d 条）",
            dim, pos.toShortString(), count(), countIn(dim));
        save();
    }
    public synchronized void clear() {
        int before = count();
        marks.clear();
        Path f = file();
        boolean deleted = false;
        try {
            if (f != null) deleted = Files.deleteIfExists(f);
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.clear(" + f + ")", t);
        }
        FOElytraLog.info("已清空宝库标记：%d 条（文件%s：%s）",
            before, deleted ? "已删除" : "本来就不存在", filePath());
    }
    public synchronized void reload() {
        load();
        FOElytraLog.detail("VaultMarks.reload 完成：现在共 %d 条标记（%d 个维度）", count(), marks.size());
    }
    private void load() {
        marks.clear();
        Path f = file();
        if (f == null) {
            if (!noDirWarned) {
                noDirWarned = true;
                FOElytraLog.warn("拿不到游戏目录，本次的宝库标记只存在内存里（关游戏就丢）");
            }
            return;
        }
        if (!Files.exists(f)) {
            FOElytraLog.detail("VaultMarks：标记文件还不存在（%s），从空开始", f);
            return;
        }
        int bad = 0;
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < MIN_FIELDS) {
                    bad++;
                    FOElytraLog.detail("VaultMarks：跳过坏行（字段不足 %d 个）：%s", MIN_FIELDS, line);
                    continue;
                }
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    marks.computeIfAbsent(parts[0], k -> new LinkedHashSet<>())
                        .add(new BlockPos(x, y, z).asLong());
                } catch (Throwable t) {
                    bad++;
                    FOElytraLog.detail("VaultMarks：跳过坏行（坐标解析失败）：%s（%s）", line, String.valueOf(t));
                }
            }
            FOElytraLog.detail("VaultMarks：从 %s 读入 %d 条标记（%d 个维度，跳过 %d 行）",
                f, count(), marks.size(), bad);
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.load(" + f + ")", t);
        }
    }
    private void save() {
        Path f = file();
        if (f == null) {
            if (!noDirWarned) {
                noDirWarned = true;
                FOElytraLog.warn("拿不到游戏目录，宝库标记这次只存在内存里（关游戏就丢）");
            }
            return;
        }
        try {
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            for (Map.Entry<String, Set<Long>> e : marks.entrySet()) {
                for (long packed : e.getValue()) {
                    BlockPos p = BlockPos.fromLong(packed);
                    lines.add(e.getKey() + " " + p.getX() + " " + p.getY() + " " + p.getZ());
                }
            }
            Path tmp = f.resolveSibling(f.getFileName().toString() + TMP_SUFFIX);
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                FOElytraLog.detail("VaultMarks：文件系统不支持原子替换，退回普通覆盖：%s", String.valueOf(notAtomic));
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            FOElytraLog.detail("VaultMarks：已落盘 %d 条标记到 %s", count(), f);
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.save(" + f + ")", t);
        }
    }
    private static String currentDimensionId() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return null;
            RegistryKey<World> key = mc.world.getRegistryKey();
            if (key == null) return null;
            Identifier id = key.getValue();
            return id == null ? null : id.toString();
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.currentDimensionId", t);
            return null;
        }
    }
    private Path file() {
        if (cachedFile != null) return cachedFile;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            File dir = mc == null ? null : mc.runDirectory;
            if (dir == null) return null;
            cachedFile = dir.toPath().resolve(FILE_NAME).toAbsolutePath();
            return cachedFile;
        } catch (Throwable t) {
            FOElytraLog.detailError("VaultMarks.file", t);
            return null;
        }
    }
}
