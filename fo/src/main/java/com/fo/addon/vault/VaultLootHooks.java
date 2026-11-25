package com.fo.addon.vault;

import net.minecraft.block.BlockState;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.enums.VaultState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 复刻 SeedMapper 的不祥宝库检测采集逻辑（`ClientPacketListenerMixin`）。
 *
 * <p>与它逐条对应：
 * <ol>
 *   <li>方块更新包：`vault_state=EJECTING` 时记下弹出宝库坐标；状态回到 `ACTIVE/INACTIVE`
 *       且所有已记录物品都收到数据包 → 结束采集，回调（SeedMapper 在这里调 `predictLoot`）；</li>
 *   <li>实体生成包：`ItemEntity` 且位置在宝库方块 `inflate(1)` 的 AABB 内 → 记录实体 id；</li>
 *   <li>实体数据包：属于被记录的 id → 取 `itemEntity.getStack().copy()` 追加进列表
 *       （顺序 = 数据包到达顺序 = 吐出顺序）。</li>
 * </ol>
 * 注意：不按 tick 轮询、不按 id 排序，完全用网络包顺序——这样才和 SeedMapper 一致。
 */
public final class VaultLootHooks {
    /** 一次完整弹出：按到达顺序的物品 + 是否不祥宝库。 */
    public record Capture(List<ItemStack> items, boolean ominous) {
    }

    public interface Listener {
        void onCapture(Capture capture);
    }

    private static Listener listener;
    private static BlockPos vaultEjectingPos;
    private static boolean ominous;
    private static final Set<Integer> ejectedIds = new LinkedHashSet<>();
    private static final List<ItemStack> ejectedItems = new ArrayList<>();

    private VaultLootHooks() {
    }

    public static void setListener(Listener value) {
        listener = value;
        reset();
    }

    public static boolean hasListener() {
        return listener != null;
    }

    public static void clear() {
        listener = null;
        reset();
    }

    private static void reset() {
        vaultEjectingPos = null;
        ejectedIds.clear();
        ejectedItems.clear();
    }

    /** 方块更新包（对应 SeedMapper `handleBlockUpdate`）。 */
    public static void onBlockUpdate(BlockPos pos, BlockState state) {
        if (listener == null) return;
        Optional<VaultState> optionalState = state.getOrEmpty(VaultBlock.VAULT_STATE);
        if (optionalState.isEmpty()) return;
        boolean isOminous = state.getOrEmpty(VaultBlock.OMINOUS).orElse(false);
        switch (optionalState.get()) {
            case EJECTING -> {
                if (vaultEjectingPos == null) {
                    vaultEjectingPos = pos;
                    ominous = isOminous;
                }
            }
            case ACTIVE, INACTIVE -> {
                if (vaultEjectingPos != null && vaultEjectingPos.equals(pos)) {
                    vaultEjectingPos = null;
                    // SeedMapper 的条件：已记录的物品 id 全部收到数据包（集合为空）才结算
                    if (ejectedIds.isEmpty()) {
                        if (!ejectedItems.isEmpty()) {
                            listener.onCapture(new Capture(List.copyOf(ejectedItems), ominous));
                        }
                        reset();
                    }
                }
            }
        }
    }

    /** 实体生成包（对应 SeedMapper `handleAddEntity`）。 */
    public static void onEntitySpawn(int entityId, EntityType<?> type, double x, double y, double z) {
        if (listener == null || vaultEjectingPos == null) return;
        if (type != EntityType.ITEM) return;
        if (new Box(vaultEjectingPos).expand(1).contains(x, y, z)) {
            ejectedIds.add(entityId);
        }
    }

    /** 实体数据包（对应 SeedMapper `handleSetEntityData`）。 */
    public static void onEntityData(int entityId, ItemEntity entity) {
        if (listener == null) return;
        if (ejectedIds.remove(entityId)) {
            ejectedItems.add(entity.getStack().copy());
        }
    }
}
