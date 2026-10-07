package com.fo.addon.elytra.core;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class TerrainProbe {
    public record Column(int x, int z, int surfaceY, int airBelowSurface, int airGap, int topAirY, int bottomAirY,
                         boolean loaded, boolean pending, boolean cave, boolean ravine) {
        public boolean deepCave() {
            return cave || ravine;
        }
    }

    private record Cached(int depth, Column column, boolean pending) {
    }

    private static final int MAX_CACHE = 4096;
    private static final int MIN_DEPTH = 1;
    private static final int MAX_DEPTH = 128;
    private static final int CAVE_GAP = 4;
    private static final int RAVINE_GAP = 12;
    private static final double RAVINE_AIR_RATIO = 0.35;
    private static final Map<Long, Cached> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cached> eldest) {
            return size() > MAX_CACHE;
        }
    };

    private TerrainProbe() {
    }

    public static void request(int x, int z, int depth) {
        try {
            int d = clampDepth(depth);
            long key = ChunkPos.toLong(x, z);
            synchronized (CACHE) {
                Cached entry = CACHE.get(key);
                if (entry != null && entry.column != null && entry.column.loaded()) return;
                if (entry != null && entry.pending) return;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            IntegratedServer server = mc.getServer();
            if (server == null) {
                Column loaded = probeLoaded(x, z, d);
                synchronized (CACHE) {
                    CACHE.put(key, new Cached(d, loaded, false));
                }
                return;
            }
            synchronized (CACHE) {
                CACHE.put(key, new Cached(d, unknown(x, z, true), true));
            }
            Supplier<Column> task = () -> generate(server.getOverworld(), x, z, d);
            // 单机必须让整合服务端在自己线程上真生成区块，客户端线程只等结果
            CompletableFuture<Column> future = server.submit(task);
            future.whenComplete((column, error) -> finish(key, d, column, error));
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.request", t);
        }
    }

    public static Column poll(int x, int z, int depth) {
        try {
            int d = clampDepth(depth);
            synchronized (CACHE) {
                Cached entry = CACHE.get(ChunkPos.toLong(x, z));
                if (entry == null || entry.column == null || entry.depth != d) return null;
                return entry.column;
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.poll", t);
            return null;
        }
    }

    public static Column probeLoaded(int x, int z, int depth) {
        try {
            int d = clampDepth(depth);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return unknown(x, z, false);
            ClientWorld world = mc.world;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return unknown(x, z, false);
            return scan(world, x, z, d, true, false);
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.probeLoaded", t);
            return unknown(x, z, false);
        }
    }

    public static boolean deepCaveAt(int x, int z, int depth) {
        try {
            Column column = poll(x, z, depth);
            if (column == null) {
                request(x, z, depth);
                return false;
            }
            return column.deepCave();
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.deepCaveAt", t);
            return false;
        }
    }

    public static void clearCache() {
        try {
            synchronized (CACHE) {
                CACHE.clear();
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.clearCache", t);
        }
    }

    public static int cacheSize() {
        try {
            synchronized (CACHE) {
                return CACHE.size();
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.cacheSize", t);
            return 0;
        }
    }

    private static Column generate(ServerWorld world, int x, int z, int depth) {
        Chunk chunk = world.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        if (chunk == null) return null;
        return scan(world, x, z, depth, true, false);
    }

    private static void finish(long key, int depth, Column column, Throwable error) {
        try {
            if (error != null) FOElytraLog.detailError("TerrainProbe 服务端生成", error);
            synchronized (CACHE) {
                if (column != null) {
                    CACHE.put(key, new Cached(depth, column, false));
                    return;
                }
                Cached entry = CACHE.get(key);
                if (entry != null && entry.pending) CACHE.remove(key);
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.finish", t);
        }
    }

    private static Column scan(WorldView view, int x, int z, int depth, boolean loaded, boolean pending) {
        int bottomY = view.getBottomY();
        int topY = bottomY + view.getHeight() - 1;
        int heightTop = view.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) + 1;
        int startY = Math.min(heightTop, topY);
        int surfaceY = bottomY;
        for (int y = startY; y >= bottomY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = view.getBlockState(pos);
            if (state.isAir() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) continue;
            if (state.isSolidBlock(view, pos)) {
                surfaceY = y;
                break;
            }
        }
        int from = surfaceY - 1;
        int to = Math.max(bottomY, surfaceY - depth);
        int scanned = Math.max(1, from - to + 1);
        int airBelow = 0;
        int gap = 0;
        int topAirY = surfaceY;
        int bottomAirY = surfaceY;
        int runStart = surfaceY;
        boolean inRun = false;
        for (int y = from; y >= to; y--) {
            if (view.getBlockState(new BlockPos(x, y, z)).isAir()) {
                airBelow++;
                if (!inRun) {
                    inRun = true;
                    runStart = y;
                }
                continue;
            }
            if (inRun) {
                inRun = false;
                int len = runStart - y;
                if (len > gap) {
                    gap = len;
                    topAirY = runStart;
                    bottomAirY = y + 1;
                }
            }
        }
        if (inRun) {
            int len = runStart - to + 1;
            if (len > gap) {
                gap = len;
                topAirY = runStart;
                bottomAirY = to;
            }
        }
        boolean cave = gap >= CAVE_GAP && topAirY < surfaceY - 1;
        boolean ravine = (airBelow / (double) scanned) > RAVINE_AIR_RATIO || gap >= RAVINE_GAP;
        return new Column(x, z, surfaceY, airBelow, gap, topAirY, bottomAirY, loaded, pending, cave, ravine);
    }

    private static Column unknown(int x, int z, boolean pending) {
        return new Column(x, z, 0, 0, 0, 0, 0, false, pending, false, false);
    }

    private static int clampDepth(int depth) {
        return Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, depth));
    }
}
