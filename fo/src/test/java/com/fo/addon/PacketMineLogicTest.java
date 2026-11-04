package com.fo.addon;

import com.fo.addon.utils.PacketMineLogic;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FO 发包挖掘纯逻辑测试。
 *
 * <p>锚点来自 Hachimi V2 AutoMine 原版几何规则：
 * Burrow = 脚 Y 平面 ±0.25 四角；Face = y+1.5 平面同规则；Surround = 包围盒脚层地板格。</p>
 */
class PacketMineLogicTest {
    private static final double EPS = 1e-9;

    @Test
    void burrowCandidates_整格站立_只含脚下本体() {
        // 站在方块正中央：四角 ±0.25 都落在同一格
        List<BlockPos> out = PacketMineLogic.burrowCandidates(10.5, 64.0, 20.5);
        assertEquals(1, out.size());
        assertEquals(new BlockPos(10, 64, 20), out.get(0));
    }

    @Test
    void burrowCandidates_跨边界站立_覆盖两格() {
        // x=10.9：+0.25 角(11.15)在 11 格，-0.25 角(10.65)在 10 格
        List<BlockPos> out = PacketMineLogic.burrowCandidates(10.9, 64.0, 20.5);
        assertEquals(2, out.size());
        assertTrue(out.contains(new BlockPos(10, 64, 20)));
        assertTrue(out.contains(new BlockPos(11, 64, 20)));
    }

    @Test
    void faceCandidates_在头部平面() {
        // y=64 → 头部平面 65.5 → floor = 65（脚 Y+1）
        List<BlockPos> out = PacketMineLogic.faceCandidates(10.5, 64.0, 20.5);
        assertEquals(1, out.size());
        assertEquals(new BlockPos(10, 65, 20), out.get(0));
    }

    @Test
    void surroundCandidates_中央站立_单格() {
        List<BlockPos> out = PacketMineLogic.surroundCandidates(10.5, 64.0, 20.5, 0.6);
        assertEquals(1, out.size());
        assertEquals(new BlockPos(10, 64, 20), out.get(0));
    }

    @Test
    void surroundCandidates_骑边界_两格X() {
        // x=10.9, half=0.35：x-0.35=10.55→10，x+0.35=11.25→11
        List<BlockPos> out = PacketMineLogic.surroundCandidates(10.9, 64.0, 20.5, 0.6);
        assertEquals(2, out.size());
        assertTrue(out.contains(new BlockPos(10, 64, 20)));
        assertTrue(out.contains(new BlockPos(11, 64, 20)));
        // 全部在脚 Y 平面
        out.forEach(p -> assertEquals(64, p.getY()));
    }

    @Test
    void closest_按水平距离选最近() {
        List<BlockPos> out = List.of(new BlockPos(8, 64, 20), new BlockPos(12, 64, 20));
        BlockPos best = PacketMineLogic.closest(out, 11.0, 20.5);
        assertEquals(new BlockPos(12, 64, 20), best);
    }

    @Test
    void closest_忽略Y高度差() {
        // 两格 X 距离相同但 Y 不同：水平距离打平 → 取先出现的（稳定）
        List<BlockPos> out = List.of(new BlockPos(8, 60, 20), new BlockPos(12, 70, 20));
        BlockPos best = PacketMineLogic.closest(out, 11.0, 20.5);
        assertEquals(new BlockPos(12, 70, 20), best);
    }

    @Test
    void burrowCandidates_无重复() {
        List<BlockPos> out = PacketMineLogic.burrowCandidates(10.0, 64.0, 20.0);
        long distinct = out.stream().distinct().count();
        assertEquals(distinct, out.size());
    }

    @Test
    void candidates_永不包含负Y以下逻辑错误() {
        // 正常站立 Y=64 时，Surround 全部应 >= 64
        List<BlockPos> out = PacketMineLogic.surroundCandidates(10.5, 64.0, 20.5, 0.6);
        assertFalse(out.stream().anyMatch(p -> p.getY() < 64));
    }
}
