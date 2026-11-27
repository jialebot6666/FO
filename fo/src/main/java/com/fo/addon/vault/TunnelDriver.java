package com.fo.addon.vault;

import com.fo.addon.vault.VaultCorridorPlanLogic.Axis;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 2×2 宝库通道的隧道机（7.0p1 新增）。
 *
 * <p>移植自 MIT 许可的 {@code sniprrfire/Highway-Builder}（v1.0，544 行单类）的核心循环：
 * 逐格扫描可及范围、先填液体再挖、只补地板洞、自动换最好的镐、工具快坏就停、
 * 卡住自停、自动前进。改造点：
 * <ul>
 *   <li>截面固定 2 高 2 宽，不做对角、不做栏杆、不铺黑曜石；</li>
 *   <li>地板只补洞（{@code place-missing}），原有地板一律不动；</li>
 *   <li>所有方块先过 {@link VaultGuardLogic}：宝库本体 / 容器 / 保护区一律不挖；</li>
 *   <li>按规划的 L 形一段走到底，不再无限往前；支持 ±16 的 45° 升降；</li>
 *   <li>遇到岩浆、工具快坏、没方块、卡住 → 立即停并给原因。</li>
 * </ul>
 *
 * <p>运行时不在这里做目标选择，模块把一段（{@link Leg}）交给它，走完返回 {@link Result#ARRIVED}。
 * 所有方块判定都走纯逻辑类，方便以后补测。
 */
public final class TunnelDriver {

    /** 一段直线通道（L 形的其中一段）。 */
    public record Leg(Axis axis, int dir, int fixed, int from, int to, int footY, int endFootY) {

        public int length() {
            return Math.abs(to - from);
        }

        public int steps() {
            return Math.abs(endFootY - footY);
        }
    }

    /** 运行参数（由模块设置映射过来，避免驱动直接依赖模块类）。 */
    public record Options(double reach, int protectDistance, boolean autoWalk, boolean stepMode,
                          boolean stopOnLava, int minBlocks, int pickPercent, Set<String> placeIds,
                          int stuckTicks, List<int[]> vaults) {
    }

    public enum Result {
        IDLE,
        RUNNING,
        PAUSED,
        ARRIVED,
        FAILED
    }

    private static final int STEP_DELAY_TICKS = 10;
    private static final Direction[] PLACE_ORDER = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private boolean active;
    private Leg leg;
    private Options options;
    private int vaultX;
    private int vaultY;
    private int vaultZ;

    private BlockPos breaking;
    private int idleTicks;
    private int delayTicks;
    private int failureCount;
    private String failReason = "";

    /** 当前这段的进度文案，给模块做 HUD 用。 */
    private String status = "待机";

    public boolean isActive() {
        return active;
    }

    public String status() {
        return status;
    }

    public String failReason() {
        return failReason;
    }

    public int remaining() {
        if (leg == null || mc.player == null) return 0;
        int along = coord(leg.axis(), mc.player.getBlockPos());
        return leg.dir() > 0 ? leg.to() - along : along - leg.to();
    }

    public void start(Leg leg, Options options, int vaultX, int vaultY, int vaultZ) {
        this.leg = leg;
        this.options = options;
        this.vaultX = vaultX;
        this.vaultY = vaultY;
        this.vaultZ = vaultZ;
        this.active = true;
        this.breaking = null;
        this.idleTicks = 0;
        this.delayTicks = 0;
        this.failureCount = 0;
        this.failReason = "";
        this.status = "开挖";
    }

    public void stop() {
        if (!active) return;
        active = false;
        releaseKeys();
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        breaking = null;
    }

    private void fail(String reason) {
        failReason = reason;
        active = false;
        releaseKeys();
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        breaking = null;
        status = "停止：" + reason;
    }

    // ==================== 主循环 ====================

    public Result tick() {
        if (!active) return Result.IDLE;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null || options == null || leg == null) {
            fail("世界或玩家不可用");
            return Result.FAILED;
        }

        int footY = p.getBlockPos().getY();
        int plannedNow = plannedFootY(coord(leg.axis(), p.getBlockPos()));
        if (footY < Math.min(plannedNow, leg.footY()) - 3) {
            fail("掉出通道");
            return Result.FAILED;
        }

        if (mc.currentScreen != null || p.isUsingItem()) {
            releaseKeys();
            status = "暂停：界面打开 / 正在吃东西";
            return Result.PAUSED;
        }

        int along = coord(leg.axis(), p.getBlockPos());
        boolean reached = (leg.dir() > 0 ? along >= leg.to() : along <= leg.to());
        if (reached && Math.abs(footY - leg.endFootY()) <= 2) {
            releaseKeys();
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
            breaking = null;
            active = false;
            status = "抵达";
            return Result.ARRIVED;
        }

        if (delayTicks > 0) {
            delayTicks--;
            holdMovement();
            status = "单步等待";
            return Result.RUNNING;
        }

        List<BlockPos> toMine = new ArrayList<>();
        List<BlockPos> toPlace = new ArrayList<>();
        collectTasks(p, w, toMine, toPlace);

        boolean didSomething = false;

        if (!toMine.isEmpty()) {
            BlockPos target = breaking != null && toMine.contains(breaking) ? breaking : toMine.get(0);
            if (!mine(p, w, target)) return Result.FAILED;
            didSomething = true;
        } else {
            breaking = null;
        }

        if (!toPlace.isEmpty()) {
            if (countPlaceBlocks(p) <= 0) {
                fail("没方块可填了（需要至少 " + options.minBlocks() + " 个填充方块）");
                return Result.FAILED;
            }
            int placed = 0;
            for (BlockPos pos : toPlace) {
                if (placed >= 2) break;
                if (place(p, w, pos)) placed++;
            }
            if (placed > 0) didSomething = true;
        }

        boolean done = toMine.isEmpty() && toPlace.isEmpty();
        setYaw(p);
        if (options().autoWalk()) {
            mc.options.forwardKey.setPressed(done);
        } else {
            mc.options.forwardKey.setPressed(false);
        }
        applyJump(p, plannedNow);
        center(p);

        if (didSomething || done) {
            idleTicks = 0;
        } else if (++idleTicks > options.stuckTicks()) {
            fail("卡住 " + options.stuckTicks() + " tick，挖不动也走不动");
            return Result.FAILED;
        }

        if (didSomething && options.stepMode()) {
            delayTicks = STEP_DELAY_TICKS;
        }
        status = done ? "前进" : "开挖 " + (remaining() > 0 ? "剩 " + remaining() + " 格" : "");
        return Result.RUNNING;
    }

    private Options options() {
        return options;
    }

    private void holdMovement() {
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    // ==================== 扫描任务 ====================

    private void collectTasks(ClientPlayerEntity p, ClientWorld w, List<BlockPos> toMine, List<BlockPos> toPlace) {
        Options o = options;
        Vec3d eye = p.getEyePos();
        Box body = p.getBoundingBox();
        double placeRange = Math.min(o.reach(), 4.5);
        double breakRange = o.reach();

        int along = coord(leg.axis(), p.getBlockPos());
        int fixedA = leg.fixed();
        int reach = (int) Math.ceil(Math.max(placeRange, breakRange)) + 1;
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int da = -1; da <= reach; da++) {
            int a = along + leg.dir() * da;
            if (leg.dir() > 0 ? a > leg.to() : a < leg.to()) break;
            if (leg.dir() > 0 ? a < leg.from() - 1 : a > leg.from() + 1) continue;

            for (int lateral = 0; lateral < 2; lateral++) {
                int fixed = fixedA + lateral;
                int x = leg.axis() == Axis.X ? a : fixed;
                int z = leg.axis() == Axis.X ? fixed : a;
                int planned = plannedFootY(a);

                // 主体（脚层 + 头层）
                for (int y = planned; y <= planned + 1; y++) {
                    m.set(x, y, z);
                    BlockPos pos = m.toImmutable();
                    if (pos.equals(p.getBlockPos()) || pos.equals(p.getBlockPos().up())) continue;
                    considerBlock(w, pos, eye, body, toMine, toPlace, placeRange, breakRange);
                }

                // 地板：只补洞，永不挖
                BlockPos floor = new BlockPos(x, planned - 1, z);
                BlockState floorState = w.getBlockState(floor);
                if ((floorState.isReplaceable() || !floorState.getFluidState().isEmpty())
                    && eye.distanceTo(Vec3d.ofCenter(floor)) <= placeRange
                    && !body.intersects(new Box(floor))) {
                    if (allowed(floor)) toPlace.add(floor);
                }
            }
        }

        toMine.sort(Comparator.<BlockPos>comparingInt(b -> alongDist(b)).thenComparingInt(b -> -b.getY())
            .thenComparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b))));
        toPlace.sort(Comparator.<BlockPos>comparingInt(b -> alongDist(b)).thenComparingInt(BlockPos::getY)
            .thenComparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b))));
    }

    private int alongDist(BlockPos pos) {
        int a = coord(leg.axis(), pos);
        return Math.abs(a - leg.from());
    }

    private void considerBlock(ClientWorld w, BlockPos pos, Vec3d eye, Box body,
                               List<BlockPos> toMine, List<BlockPos> toPlace,
                               double placeRange, double breakRange) {
        BlockState st = w.getBlockState(pos);
        if (st.isAir()) return;
        if (!allowed(pos)) return;

        double dist = eye.distanceTo(Vec3d.ofCenter(pos));
        boolean fluid = !st.getFluidState().isEmpty();
        if (fluid) {
            if (options.stopOnLava() && st.getFluidState().getFluid() == Fluids.LAVA) {
                fail("遇到岩浆，p1 先停");
                return;
            }
            if (dist <= placeRange && !body.intersects(new Box(pos))) {
                toPlace.add(pos);
            }
            return;
        }
        if (breakable(st, pos) && dist <= breakRange) {
            toMine.add(pos);
        }
    }

    /** 保护判定：宝库本体/容器/保护区一律不动。 */
    private boolean allowed(BlockPos pos) {
        String id = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString();
        if (VaultGuardLogic.isForbiddenBlock(id)) return false;
        // 范围内所有已知宝库都要让路，不只当前目标
        if (options.vaults() != null) {
            for (int[] v : options.vaults()) {
                if (v == null || v.length < 3) continue;
                if (VaultGuardLogic.inProtectionZone(pos.getX() - v[0], pos.getY() - v[1], pos.getZ() - v[2],
                    options.protectDistance())) return false;
            }
        }
        return true;
    }

    private boolean breakable(BlockState st, BlockPos pos) {
        return !st.isAir() && st.getHardness(mc.world, pos) >= 0;
    }

    // ==================== 挖掘 / 放置 ====================

    private boolean mine(ClientPlayerEntity p, ClientWorld w, BlockPos pos) {
        BlockState st = w.getBlockState(pos);
        int tool = bestTool(p, st);
        if (tool == ONLY_WORN_TOOLS) {
            fail("镐快坏了（耐久低于 " + options.pickPercent() + "%）");
            return false;
        }
        if (tool >= 0) {
            select(p, tool);
        } else if (isWorn(p.getMainHandStack())) {
            selectFirstNotDamageable(p);
        }

        Direction side = facingSide(p.getEyePos(), pos);
        look(p, Vec3d.ofCenter(pos));
        if (mc.interactionManager.updateBlockBreakingProgress(pos, side)) {
            p.swingHand(Hand.MAIN_HAND);
        }
        breaking = pos;
        return true;
    }

    private boolean place(ClientPlayerEntity p, ClientWorld w, BlockPos pos) {
        Direction dir = findSupport(w, pos);
        BlockPos support;
        Direction face;
        Vec3d hit;
        if (dir != null) {
            support = pos.offset(dir);
            face = dir.getOpposite();
            hit = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        } else {
            return false;
        }

        int slot = selectPlaceBlock(p);
        if (slot < 0) return false;
        look(p, hit);
        ActionResult result = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND,
            new BlockHitResult(hit, face, support, false));
        if (result.isAccepted()) p.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private Direction findSupport(ClientWorld w, BlockPos pos) {
        for (Direction d : PLACE_ORDER) {
            BlockState s = w.getBlockState(pos.offset(d));
            if (s.isReplaceable() || s.getBlock() instanceof BlockWithEntity) continue;
            return d;
        }
        return null;
    }

    private int selectPlaceBlock(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (isPlaceBlock(s) && !isWorn(s)) {
                p.getInventory().setSelectedSlot(i);
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!isPlaceBlock(s) || isWorn(s)) continue;
            int hotbar = freeHotbarSlot(p);
            if (hotbar < 0) return -1;
            if (p.currentScreenHandler != p.playerScreenHandler) return -1;
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, hotbar,
                net.minecraft.screen.slot.SlotActionType.SWAP, p);
            p.getInventory().setSelectedSlot(hotbar);
            return hotbar;
        }
        return -1;
    }

    private int countPlaceBlocks(ClientPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (isPlaceBlock(s)) n += s.getCount();
        }
        return n;
    }

    private boolean isPlaceBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return false;
        return options.placeIds().contains(Registries.BLOCK.getId(bi.getBlock()).toString());
    }

    // ==================== 工具 ====================

    private static final int NOT_FOUND = -1;
    private static final int ONLY_WORN_TOOLS = -2;

    private int bestTool(ClientPlayerEntity p, BlockState state) {
        int best = NOT_FOUND;
        float bestSpeed = 1f;
        boolean rejected = false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            float speed = s.getMiningSpeedMultiplier(state);
            if (speed <= 1f) continue;
            if (isWorn(s)) {
                rejected = true;
                continue;
            }
            if (speed > bestSpeed || (speed == bestSpeed && i < 9 && best >= 9)) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best == NOT_FOUND && rejected) return ONLY_WORN_TOOLS;
        return best;
    }

    private boolean isWorn(ItemStack s) {
        if (!s.isDamageable()) return false;
        int max = s.getMaxDamage();
        if (max <= 0) return false;
        int left = max - s.getDamage();
        return left * 100L < (long) max * options.pickPercent();
    }

    private void select(ClientPlayerEntity p, int index) {
        if (index < 0) return;
        if (index >= 9) {
            if (p.currentScreenHandler != p.playerScreenHandler) return;
            int hotbar = freeHotbarSlot(p);
            if (hotbar < 0) return;
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, index, hotbar,
                net.minecraft.screen.slot.SlotActionType.SWAP, p);
            index = hotbar;
        }
        p.getInventory().setSelectedSlot(index);
    }

    private void selectFirstNotDamageable(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (!p.getInventory().getStack(i).isDamageable()) {
                p.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private int freeHotbarSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 8; i >= 0; i--) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isIn(ItemTags.PICKAXES) || s.contains(DataComponentTypes.FOOD) || isPlaceBlock(s)
                || s.isOf(Items.ENDER_CHEST) || s.isOf(Items.TOTEM_OF_UNDYING)) continue;
            return i;
        }
        return -1;
    }

    // ==================== 移动 / 视角 ====================

    private static int coord(Axis axis, BlockPos pos) {
        return axis == Axis.X ? pos.getX() : pos.getZ();
    }

    private int plannedFootY(int along) {
        int total = leg.length();
        int steps = leg.steps();
        if (total == 0) return leg.endFootY();
        int d = Math.min(Math.abs(along - leg.from()), total);
        int climbed = (int) ((long) d * steps / total);
        int sign = Integer.signum(leg.endFootY() - leg.footY());
        return leg.footY() + sign * climbed;
    }

    private void setYaw(ClientPlayerEntity p) {
        float yaw = switch (leg.axis()) {
            case X -> leg.dir() > 0 ? -90f : 90f;
            case Z -> leg.dir() > 0 ? 0f : 180f;
        };
        p.setYaw(yaw);
    }

    private void applyJump(ClientPlayerEntity p, int planned) {
        int footY = p.getBlockPos().getY();
        boolean needUp = planned > footY;
        // 只在真的被台阶挡住时才跳，避免一路蹦着走偏
        mc.options.jumpKey.setPressed(needUp && p.isOnGround() && p.horizontalCollision);
    }

    private void center(ClientPlayerEntity p) {
        int lateral = leg.axis() == Axis.X ? p.getBlockPos().getZ() : p.getBlockPos().getX();
        double desired = leg.fixed() + 1.0; // 2 宽截面的中线
        double err = lateral + 0.5 - desired;
        mc.options.leftKey.setPressed(err > 0.35);
        mc.options.rightKey.setPressed(err < -0.35);
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void look(ClientPlayerEntity p, Vec3d target) {
        Vec3d eye = p.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90f, 90f);
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, pitch, p.isOnGround(), p.horizontalCollision));
    }

    private static Direction facingSide(Vec3d eye, BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        double dx = eye.x - c.x;
        double dy = eye.y - c.y;
        double dz = eye.z - c.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
