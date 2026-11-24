package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.VaultEspLogic;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.enums.VaultState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.EmptyChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * FO 不祥宝库 ESP —— 只高亮「不祥宝库」，普通宝库一律不管。
 *
 * <p>区分原理（已在 1.21.11 官方 jar 上实证）：不祥宝库不是独立方块，
 * 而是 {@code minecraft:vault} 的 blockstate 属性 {@code ominous=true}；
 * {@code vault_state=inactive} 表示还没被打开过。属性随区块数据下发，
 * 客户端不需要任何额外网络包，纯本地渲染，服务器无感。
 *
 * <p>扫描策略：只扫已加载区块，先用 section 的调色板（{@code hasAny}）廉价筛出
 * 含宝库方块的 section，命中才逐格精确判定；跨 tick 分摊耗时并限时约 2ms，
 * 扫完一整遍后按「重扫间隔」再扫下一遍。
 */
public class FOVaultEsp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("扫描半径")
        .description("搜索范围（格）。服务端只下发已加载区块的方块，实际范围受渲染距离限制（500 格约等于服务器可渲染范围）。")
        .defaultValue(VaultEspLogic.DEFAULT_RADIUS)
        .range(32, 500)
        .sliderRange(32, 500)
        .build());

    private final Setting<Double> rescanInterval = sgGeneral.add(new DoubleSetting.Builder()
        .name("重扫间隔")
        .description("多久重新扫描一遍已加载区块（秒）。")
        .defaultValue(1.0)
        .min(0.2)
        .max(5.0)
        .sliderMin(0.2)
        .sliderMax(5.0)
        .build());

    private final Setting<Boolean> onlyUnopened = sgGeneral.add(new BoolSetting.Builder()
        .name("只显示未开")
        .description("只高亮还没被打开过的宝库（vault_state=inactive）。关掉后已开过的也会画，用「已开颜色」区分。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> notifyNew = sgGeneral.add(new BoolSetting.Builder()
        .name("发现提示")
        .description("新发现一个不祥宝库时在聊天栏提示坐标与距离（每个宝库只提示一次）。")
        .defaultValue(false)
        .build());

    private final Setting<Integer> maxRender = sgGeneral.add(new IntSetting.Builder()
        .name("最大显示数")
        .description("最多同时绘制多少个（取最近的），防卡顿。")
        .defaultValue(VaultEspLogic.DEFAULT_LIMIT)
        .range(4, 64)
        .sliderRange(4, 64)
        .build());

    private final Setting<SettingColor> unopenedColor = sgRender.add(new ColorSetting.Builder()
        .name("未开颜色")
        .description("还没被打开过的不祥宝库颜色。")
        .defaultValue(new SettingColor(255, 200, 40, 220))
        .build());

    private final Setting<SettingColor> openedColor = sgRender.add(new ColorSetting.Builder()
        .name("已开颜色")
        .description("已打开过的不祥宝库颜色（仅「只显示未开」关掉时可见）。")
        .defaultValue(new SettingColor(160, 160, 160, 180))
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("方块框的绘制方式。")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> showBeam = sgRender.add(new BoolSetting.Builder()
        .name("光柱")
        .description("从宝库顶画一根到地表的细柱，方便远距离/高空定位。")
        .defaultValue(true)
        .build());

    /** 当前这一遍扫描已确认的宝库（key = BlockPos.asLong）。 */
    private final Map<Long, VaultEspLogic.Hit> hits = new HashMap<>();
    /** 本遍扫描见到过的坐标，扫完整遍后用来清理已消失的命中。 */
    private final Set<Long> seenInPass = new HashSet<>();
    /** 地表 Y 缓存（画光柱用），key = x/z 打包。 */
    private final Map<Long, Integer> surfaceCache = new HashMap<>();

    private final List<long[]> pendingChunks = new ArrayList<>();
    private int pendingCursor;
    private long nextPassAtMs;
    private Object lastDimension;
    private Object surfaceWorld;

    public FOVaultEsp() {
        super(AddonTemplate.CATEGORY, "FO 不祥宝库 ESP",
            "只在服务端下发的方块里高亮不祥宝库（普通宝库不显示）；不发包，服务器无感。");
    }

    @Override
    public void onActivate() {
        clearAll();
        nextPassAtMs = 0;
        lastDimension = null;
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    private void clearAll() {
        hits.clear();
        seenInPass.clear();
        surfaceCache.clear();
        pendingChunks.clear();
        pendingCursor = 0;
        surfaceWorld = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        Object dimension = mc.world.getRegistryKey();
        if (!Objects.equals(dimension, lastDimension)) {
            lastDimension = dimension;
            clearAll();
            nextPassAtMs = 0;
        }

        if (pendingChunks.isEmpty() && System.currentTimeMillis() >= nextPassAtMs) {
            startPass();
        }
        if (!pendingChunks.isEmpty()) {
            scanSome();
        }
    }

    /** 生成这一遍要扫的区块列表（只保留已加载、且在半径内的）。 */
    private void startPass() {
        seenInPass.clear();
        pendingChunks.clear();
        pendingCursor = 0;

        double radius = scanRadius.get();
        double r2 = radius * radius;
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;
                double centerX = (cx << 4) + 8.0;
                double centerZ = (cz << 4) + 8.0;
                double ddx = centerX - px;
                double ddz = centerZ - pz;
                if (ddx * ddx + ddz * ddz > r2) continue;
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                pendingChunks.add(new long[]{cx, cz});
            }
        }

        if (pendingChunks.isEmpty()) {
            nextPassAtMs = System.currentTimeMillis() + intervalMs();
        }
    }

    /** 每 tick 最多约 2ms / 32 个区块，避免卡帧。 */
    private void scanSome() {
        long start = System.nanoTime();
        long budget = 2_000_000L;
        int processed = 0;
        while (pendingCursor < pendingChunks.size() && processed < 32) {
            if (processed > 0 && VaultEspLogic.budgetSpent(System.nanoTime() - start, budget)) break;
            long[] chunk = pendingChunks.get(pendingCursor++);
            scanChunk((int) chunk[0], (int) chunk[1]);
            processed++;
        }
        if (pendingCursor >= pendingChunks.size()) {
            pendingChunks.clear();
            pendingCursor = 0;
            hits.keySet().retainAll(seenInPass);
            nextPassAtMs = System.currentTimeMillis() + intervalMs();
        }
    }

    private long intervalMs() {
        return (long) Math.max(200.0, rescanInterval.get() * 1000.0);
    }

    /** 扫一个区块：先按 section 调色板过滤，命中才逐格精确判定。 */
    private void scanChunk(int cx, int cz) {
        Chunk chunk = mc.world.getChunk(cx, cz);
        if (chunk == null || chunk instanceof EmptyChunk) return;

        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = mc.world.getBottomY();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            // 调色板级别的廉价过滤：整段里没有宝库方块就不用逐格看
            if (!section.hasAny(state -> state.isOf(Blocks.VAULT))) continue;

            int baseY = bottomY + i * 16;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isOminous(state)) continue;

                        BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                        // 二次确认：世界上这个坐标确实是同一个不祥宝库（防 section 索引错位）
                        BlockState worldState;
                        try {
                            worldState = mc.world.getBlockState(pos);
                        } catch (RuntimeException e) {
                            continue;
                        }
                        if (!isOminous(worldState)) continue;
                        record(pos, worldState);
                    }
                }
            }
        }
    }

    private void record(BlockPos pos, BlockState state) {
        long key = pos.asLong();
        boolean unopened = state.get(VaultBlock.VAULT_STATE) == VaultState.INACTIVE;
        boolean isNew = !hits.containsKey(key);
        hits.put(key, new VaultEspLogic.Hit(pos.getX(), pos.getY(), pos.getZ(), unopened));
        seenInPass.add(key);

        if (isNew && notifyNew.get()) {
            double distance = Math.sqrt(mc.player.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            info("发现不祥宝库：%d, %d, %d（距离约 %.0f 格）",
                pos.getX(), pos.getY(), pos.getZ(), distance);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || hits.isEmpty()) return;

        Vec3d eye = mc.player.getEyePos();
        List<VaultEspLogic.Hit> visible = VaultEspLogic.nearest(
            new ArrayList<>(hits.values()), eye.x, eye.y, eye.z, scanRadius.get(), maxRender.get());

        for (VaultEspLogic.Hit hit : visible) {
            BlockPos pos = new BlockPos(hit.x(), hit.y(), hit.z());
            BlockState state = mc.world.getBlockState(pos);
            if (!isOminous(state)) continue;

            boolean unopened = state.get(VaultBlock.VAULT_STATE) == VaultState.INACTIVE;
            if (!VaultEspLogic.shouldHighlight(unopened, onlyUnopened.get())) continue;

            SettingColor color = unopened ? unopenedColor.get() : openedColor.get();
            event.renderer.box(pos, color, color, shapeMode.get(), 0);
            if (showBeam.get()) {
                drawBeam(event, hit, color);
            }
        }
    }

    /** 从宝库顶画一根细柱到地表（与古城高亮同一套穿墙渲染管线）。 */
    private void drawBeam(Render3DEvent event, VaultEspLogic.Hit hit, SettingColor color) {
        if (!mc.world.getChunkManager().isChunkLoaded(hit.x() >> 4, hit.z() >> 4)) return;

        Object dimension = mc.world.getRegistryKey();
        if (!Objects.equals(dimension, surfaceWorld)) {
            surfaceWorld = dimension;
            surfaceCache.clear();
        }

        long key = ((long) hit.x() << 32) | (hit.z() & 0xFFFFFFFFL);
        Integer surface = surfaceCache.get(key);
        if (surface == null) {
            try {
                surface = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, hit.x(), hit.z());
            } catch (RuntimeException e) {
                return;
            }
            surfaceCache.put(key, surface);
        }
        if (surface <= hit.y() + 1) return;

        double half = 0.0625; // 全宽 1/8 格
        event.renderer.box(
            hit.x() + 0.5 - half, hit.y() + 1, hit.z() + 0.5 - half,
            hit.x() + 0.5 + half, surface, hit.z() + 0.5 + half,
            color, color, ShapeMode.Both, 0);
    }

    private static boolean isOminous(BlockState state) {
        return state != null
            && state.isOf(Blocks.VAULT)
            && Boolean.TRUE.equals(state.get(VaultBlock.OMINOUS));
    }
}
