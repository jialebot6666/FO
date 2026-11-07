package com.fo.addon;

import com.fo.addon.utils.StashMoveLogic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分区判定 / 传送检测 / 双箱配对（V6.7 自动搬运三号模式）。 */
class StashMoveLogicTest {

    @Test
    void 离待搬运箱近判待搬运箱区() {
        assertEquals(StashMoveLogic.Zone.SOURCE,
            StashMoveLogic.nearerZone(0, 0, 10, 10, 100, 100));
    }

    @Test
    void 离存储箱近判存储箱区() {
        assertEquals(StashMoveLogic.Zone.STORE,
            StashMoveLogic.nearerZone(0, 0, 100, 100, 10, 10));
    }

    @Test
    void 等距判待搬运箱区() {
        assertEquals(StashMoveLogic.Zone.SOURCE,
            StashMoveLogic.nearerZone(0, 0, 10, 0, -10, 0));
    }

    @Test
    void 只有一个坐标时判到已知那侧() {
        assertEquals(StashMoveLogic.Zone.STORE,
            StashMoveLogic.nearerZone(0, 0, Double.NaN, Double.NaN, 5, 5));
        assertEquals(StashMoveLogic.Zone.SOURCE,
            StashMoveLogic.nearerZone(0, 0, 5, 5, Double.NaN, Double.NaN));
        assertEquals(StashMoveLogic.Zone.UNKNOWN,
            StashMoveLogic.nearerZone(0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN));
    }

    @Test
    void 位置突跳判定被拉走() {
        // 一 tick 内位移 40 格 > 阈值 32
        assertTrue(StashMoveLogic.teleportJumped(0, 0, 40, 0, 32));
        // 位移 10 格不算
        assertFalse(StashMoveLogic.teleportJumped(0, 0, 10, 0, 32));
        // 阈值边界：正好 32 格不算（严格大于）
        assertFalse(StashMoveLogic.teleportJumped(0, 0, 32, 0, 32));
    }

    @Test
    void 双箱另一半_左半顺时针() {
        // 朝北(0,-1) 的左半 → 顺时针 → 东(+1,0)（与原版 ChestBlock 字节码一致）
        int[] off = StashMoveLogic.chestOtherHalfOffset(true, 0, -1);
        assertEquals(1, off[0]);
        assertEquals(0, off[1]);
    }

    @Test
    void 双箱另一半_右半逆时针() {
        // 朝北(0,-1) 的右半 → 逆时针 → 西(-1,0)
        int[] off = StashMoveLogic.chestOtherHalfOffset(false, 0, -1);
        assertEquals(-1, off[0]);
        assertEquals(0, off[1]);
    }

    @Test
    void 双箱配对四个朝向都自洽() {
        // 朝向 (dx,dz)：北(0,-1) 东(1,0) 南(0,1) 西(-1,0)
        int[][] facings = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] f : facings) {
            int[] left = StashMoveLogic.chestOtherHalfOffset(true, f[0], f[1]);
            int[] right = StashMoveLogic.chestOtherHalfOffset(false, f[0], f[1]);
            // 左右两半互为相反方向
            assertEquals(-left[0], right[0]);
            assertEquals(-left[1], right[1]);
            // 偏移必须落在轴向上（不能是斜对角）
            assertEquals(1, Math.abs(left[0]) + Math.abs(left[1]));
        }
    }

    @Test
    void 区名中文用于界面() {
        assertEquals("待搬运箱区", StashMoveLogic.Zone.SOURCE.toString());
        assertEquals("存储箱区", StashMoveLogic.Zone.STORE.toString());
        assertEquals("未判定", StashMoveLogic.Zone.UNKNOWN.toString());
    }

    // ==================== 卸货过滤（V6.7p1 修复） ====================

    @Test
    void 末影珍珠任何时候都不入库() {
        assertFalse(StashMoveLogic.shouldUnload("minecraft:ender_pearl", false));
        assertFalse(StashMoveLogic.shouldUnload("minecraft:ender_pearl", true));
    }

    @Test
    void 只拿潜影盒时只存潜影盒() {
        assertTrue(StashMoveLogic.shouldUnload("minecraft:shulker_box", true));
        assertTrue(StashMoveLogic.shouldUnload("minecraft:red_shulker_box", true));
        assertFalse(StashMoveLogic.shouldUnload("minecraft:diamond", true));
        assertFalse(StashMoveLogic.shouldUnload("minecraft:golden_carrot", true));
    }

    @Test
    void 未开只拿潜影盒时非珍珠物品都存() {
        assertTrue(StashMoveLogic.shouldUnload("minecraft:diamond", false));
        assertTrue(StashMoveLogic.shouldUnload("minecraft:shulker_box", false));
    }

    @Test
    void 空ID不卸() {
        assertFalse(StashMoveLogic.shouldUnload(null, false));
    }

    // ==================== 容器识别 / 停滞判定（V6.7p3） ====================

    @Test
    void 箱子与潜影盒都算容器() {
        assertTrue(StashMoveLogic.isSupportedContainer(true, false));
        assertTrue(StashMoveLogic.isSupportedContainer(false, true));
        assertTrue(StashMoveLogic.isSupportedContainer(true, true));
        assertFalse(StashMoveLogic.isSupportedContainer(false, false));
    }

    @Test
    void 走不动判定() {
        // 位移 0.01 格（被边沿挡住，几乎不动）→ 停滞
        assertTrue(StashMoveLogic.isStalled(0, 0, 0.01, 0, 0.05));
        // 位移 0.06 格（还在走）→ 不算
        assertFalse(StashMoveLogic.isStalled(0, 0, 0.06, 0, 0.05));
        // 位移 1 格 → 不算
        assertFalse(StashMoveLogic.isStalled(0, 0, 1, 0, 0.05));
        // 斜向位移 0.04/0.02（合计约 0.045）→ 仍算停滞
        assertTrue(StashMoveLogic.isStalled(0, 0, 0.04, 0.02, 0.05));
    }

    @Test
    void 后退距离判定() {
        // 离气泡柱中心不到 2.5 格 → 继续退
        assertTrue(StashMoveLogic.needMoreBackoff(1.0, 2.5));
        assertTrue(StashMoveLogic.needMoreBackoff(2.49, 2.5));
        // 已经 2.5 格及以上 → 可以停
        assertFalse(StashMoveLogic.needMoreBackoff(2.5, 2.5));
        assertFalse(StashMoveLogic.needMoreBackoff(4.0, 2.5));
    }

    // ==================== 点击选面（V6.7p5） ====================

    @Test
    void 站斜上方优先点侧面() {
        // 眼睛在方块东侧 +X 2 格、高 2 格：水平 2 >= 2*0.6 → 选东面
        assertEquals(StashMoveLogic.FACE_EAST, StashMoveLogic.pickClickFace(2, 2, 0, false));
        assertEquals(StashMoveLogic.FACE_WEST, StashMoveLogic.pickClickFace(-2, 2, 0, false));
        assertEquals(StashMoveLogic.FACE_SOUTH, StashMoveLogic.pickClickFace(0, 2, 2, false));
        assertEquals(StashMoveLogic.FACE_NORTH, StashMoveLogic.pickClickFace(0, 2, -2, false));
    }

    @Test
    void 顶面被漏斗挡住时不选顶面() {
        // 正上方、顶面空 → 可以点顶面
        assertEquals(StashMoveLogic.FACE_UP, StashMoveLogic.pickClickFace(0, 2, 0, false));
        // 正上方但顶上被漏斗占住 → 必须走侧面（旧实现会选顶面然后点不动）
        int face = StashMoveLogic.pickClickFace(0, 2, 0, true);
        assertNotEquals(StashMoveLogic.FACE_UP, face);
    }

    @Test
    void 站在正下方永远不选底面() {
        // 眼睛低于方块中心：水平 0、dy 为负 → 只能侧面
        int face = StashMoveLogic.pickClickFace(0.2, -2, 0.1, false);
        assertNotEquals(StashMoveLogic.FACE_UP, face);
    }

    @Test
    void 水平方向取偏移更大的轴() {
        // X 偏移更大 → 东/西
        assertEquals(StashMoveLogic.FACE_EAST, StashMoveLogic.pickClickFace(3, 0.5, 1, false));
        // Z 偏移更大 → 南/北
        assertEquals(StashMoveLogic.FACE_SOUTH, StashMoveLogic.pickClickFace(0.5, 0.5, 3, false));
    }

    // ==================== V6.7p8：启动距离门槛 + 珍珠箱回退 ====================

    @Test
    void 距最近区取两区中的较小值() {
        // 玩家在 (0,0)，待搬运箱 (10,0) 距 10，存储箱 (0,20) 距 20 → 取 10
        assertEquals(10.0, StashMoveLogic.nearestZoneDistance(0, 0, 10, 0, true, 0, 20, true), 1e-9);
        // 反过来：存储箱更近
        assertEquals(5.0, StashMoveLogic.nearestZoneDistance(0, 0, 30, 0, true, 0, 5, true), 1e-9);
    }

    @Test
    void 只设置一个区时按那个区算距离() {
        assertEquals(7.0, StashMoveLogic.nearestZoneDistance(0, 0, 7, 0, true, 0, 0, false), 1e-9);
        assertEquals(9.0, StashMoveLogic.nearestZoneDistance(0, 0, 0, 0, false, 9, 0, true), 1e-9);
    }

    @Test
    void 两个区都没设置时返回最大值() {
        // 调用方据此判「请先设置坐标」，别把「没配置」当成「距离 0，就在脚边」
        assertEquals(Double.MAX_VALUE,
            StashMoveLogic.nearestZoneDistance(0, 0, 0, 0, false, 0, 0, false));
    }

    @Test
    void 站在锚点上距离为零() {
        assertEquals(0.0, StashMoveLogic.nearestZoneDistance(12.5, -3.5, 12.5, -3.5, true, 0, 0, false), 1e-9);
    }

    @Test
    void 珍珠箱本区专用优先于通用() {
        assertEquals(StashMoveLogic.PEARL_CHEST_ZONE, StashMoveLogic.pickPearlChestSlot(true, true));
        assertEquals(StashMoveLogic.PEARL_CHEST_ZONE, StashMoveLogic.pickPearlChestSlot(true, false));
    }

    @Test
    void 本区专用没填时回退通用珍珠箱() {
        // 关键回归点：只放了一个珍珠箱的老配置必须继续能用
        assertEquals(StashMoveLogic.PEARL_CHEST_COMMON, StashMoveLogic.pickPearlChestSlot(false, true));
    }

    @Test
    void 两个珍珠箱都没填时返回未设置() {
        assertEquals(StashMoveLogic.PEARL_CHEST_NONE, StashMoveLogic.pickPearlChestSlot(false, false));
    }

    // ==================== V6.7p11：居中计数（照 PearlDrop 的 APPROACH） ====================

    @Test
    void 站着不动且贴近柱心才算居中() {
        // 没动 + 距离 0.8 < 1.2 → 累加
        assertEquals(1, StashMoveLogic.nextCenterStreak(0, false, 0.8, 1.2));
        assertEquals(3, StashMoveLogic.nextCenterStreak(2, false, 1.19, 1.2));
    }

    @Test
    void 被挡住但离柱子远不算居中() {
        // 关键回归点：被箱子腿/门框挡住时人也不动，但离柱子还有 3 格，不能判"已居中"
        assertEquals(0, StashMoveLogic.nextCenterStreak(5, false, 3.0, 1.2));
    }

    @Test
    void 还在移动就清零() {
        assertEquals(0, StashMoveLogic.nextCenterStreak(4, true, 0.5, 1.2));
    }

    @Test
    void 连续居中够六次才允许投() {
        int streak = 0;
        for (int i = 0; i < 5; i++) {
            streak = StashMoveLogic.nextCenterStreak(streak, false, 0.6, 1.2);
            assertFalse(StashMoveLogic.readyToThrow(streak, 6), "第 " + (i + 1) + " 次不该允许投");
        }
        streak = StashMoveLogic.nextCenterStreak(streak, false, 0.6, 1.2);
        assertTrue(StashMoveLogic.readyToThrow(streak, 6), "第 6 次才允许投");
    }

    // ==================== V6.7p14：回程票确认 + 自杀门槛 ====================

    @Test
    void 腔室珍珠要连续命中才确认() {
        assertFalse(StashMoveLogic.pearlConfirmed(5, 6), "只看到 5 tick 不能算确认");
        assertTrue(StashMoveLogic.pearlConfirmed(6, 6), "连续 6 tick 才算确认");
        assertTrue(StashMoveLogic.pearlConfirmed(9, 6));
    }

    @Test
    void 确认阈值非法时不确认() {
        assertFalse(StashMoveLogic.pearlConfirmed(100, 0), "阈值 0 属于配置错误，不能当成已确认");
        assertFalse(StashMoveLogic.pearlConfirmed(0, 6));
    }

    @Test
    void 没确认过回程票一律不许自杀() {
        // 关键回归点：投出去的珍珠没进腔室时，绝不能死（死完下一趟就拉不回来了）
        assertFalse(StashMoveLogic.canSuicide(10000, 0, 1200));
        assertFalse(StashMoveLogic.canSuicide(10000, -1, 1200));
    }

    @Test
    void 确认后必须等够设置里的延时() {
        // V6.7p18：不再夹 600 硬下限，默认 1200（3C3U 实测 660 仍会掉）
        long confirmed = 1000;
        assertFalse(StashMoveLogic.canSuicide(confirmed + 1199, confirmed, 1200), "差一 tick 都不许死");
        assertTrue(StashMoveLogic.canSuicide(confirmed + 1200, confirmed, 1200), "正好够就能死");
    }

    @Test
    void 延时设零时确认完立刻可自杀() {
        // 用户可在 0~2000 之间自己定；设 0 就是“确认完立刻死”（必掉珍珠，风险自负）
        long confirmed = 500;
        assertTrue(StashMoveLogic.canSuicide(confirmed, confirmed, 0));
        assertFalse(StashMoveLogic.canSuicide(confirmed + 599, confirmed, 600));
        assertTrue(StashMoveLogic.canSuicide(confirmed + 600, confirmed, 600));
    }

    @Test
    void 负延时按零处理() {
        long confirmed = 100;
        assertTrue(StashMoveLogic.canSuicide(confirmed, confirmed, -5));
    }

    // ==================== V6.7p17：双号入口判区 ====================

    @Test
    void 自动搜索按半径兜底判区() {
        assertEquals(StashMoveLogic.Zone.STORE, StashMoveLogic.storeZoneByDistance(10, 32));
        // 边界值算存储区：正好站在判定圈上，按存储区处理更安全（宁可直接卸货，也不去搜自家箱子）
        assertEquals(StashMoveLogic.Zone.STORE, StashMoveLogic.storeZoneByDistance(32, 32));
        assertEquals(StashMoveLogic.Zone.SOURCE, StashMoveLogic.storeZoneByDistance(32.1, 32));
    }

    @Test
    void 没配存储锚点按源区处理() {
        assertEquals(StashMoveLogic.Zone.SOURCE, StashMoveLogic.storeZoneByDistance(Double.MAX_VALUE, 32));
        assertEquals(StashMoveLogic.Zone.SOURCE, StashMoveLogic.storeZoneByDistance(Double.NaN, 32));
    }

    @Test
    void 判定半径为零只认脚下() {
        assertEquals(StashMoveLogic.Zone.STORE, StashMoveLogic.storeZoneByDistance(0, 0));
        assertEquals(StashMoveLogic.Zone.SOURCE, StashMoveLogic.storeZoneByDistance(0.1, 0));
    }

    @Test
    void 单箱框选走双锚点取近() {
        // 复用三号同一个函数：离哪个锚点近就算哪个区
        assertEquals(StashMoveLogic.Zone.STORE, StashMoveLogic.nearerZone(100, 0, 0, 0, 100, 0));
        assertEquals(StashMoveLogic.Zone.SOURCE, StashMoveLogic.nearerZone(10, 0, 0, 0, 500, 0));
    }

    // ==================== V6.7p21：取货三态 / 自家仓库保护 ====================

    @Test
    void 取货三态_有候选就是取到() {
        assertEquals(StashMoveLogic.TakeOutcome.TAKEN, StashMoveLogic.takeOutcome(true, true));
        assertEquals(StashMoveLogic.TakeOutcome.TAKEN, StashMoveLogic.takeOutcome(false, true));
    }

    @Test
    void 取货三态_有物品但没候选是NO_MATCH而不是已空() {
        // 这就是「只拿潜影盒遇到普通物资箱」的场景：不能当空箱拉黑
        assertEquals(StashMoveLogic.TakeOutcome.NO_MATCH, StashMoveLogic.takeOutcome(true, false));
    }

    @Test
    void 取货三态_真的一件东西都没有才是EMPTY() {
        assertEquals(StashMoveLogic.TakeOutcome.EMPTY, StashMoveLogic.takeOutcome(false, false));
    }

    @Test
    void 自家仓库保护_双号与三号锚点都能命中() {
        List<int[]> anchors = List.of(
            new int[]{100, 64, 200},    // 双号存储箱
            new int[]{300, 70, 400},    // 三号存储箱
            new int[]{301, 70, 400});   // 它的双箱搭档
        assertTrue(StashMoveLogic.isProtectedChest(100, 64, 200, anchors));
        assertTrue(StashMoveLogic.isProtectedChest(300, 70, 400, anchors));
        assertTrue(StashMoveLogic.isProtectedChest(301, 70, 400, anchors), "双箱搭档也要保护");
        assertFalse(StashMoveLogic.isProtectedChest(302, 70, 400, anchors));
        assertFalse(StashMoveLogic.isProtectedChest(100, 65, 200, anchors), "y 不同不算同一个箱子");
        assertFalse(StashMoveLogic.isProtectedChest(0, 0, 0, null));
    }

    // ==================== V6.7p22：门板朝向 / 珍珠是否真在柱内 ====================

    @Test
    void 门板朝向_南北向不能被误判成东() {
        // 这是 V6.7p21 及以前的真实 bug：门板贴南/北时 X 占满，maxX=1 会被优先判成「东」，
        // 正对面就算成了垂直的侧面（实机表现「往两旁投」）。
        assertEquals(StashMoveLogic.BoxSide.SOUTH, StashMoveLogic.thinPanelSide(0.0, 0.8125, 1.0, 1.0));
        assertEquals(StashMoveLogic.BoxSide.NORTH, StashMoveLogic.thinPanelSide(0.0, 0.0, 1.0, 0.1875));
        // 东西向本来就是对的，别改坏
        assertEquals(StashMoveLogic.BoxSide.EAST, StashMoveLogic.thinPanelSide(0.8125, 0.0, 1.0, 1.0));
        assertEquals(StashMoveLogic.BoxSide.WEST, StashMoveLogic.thinPanelSide(0.0, 0.0, 0.1875, 1.0));
    }

    @Test
    void 门板朝向_平放或异常形状一律未知() {
        // 关着的活板门：平铺薄板，X/Z 都占满 → 不该猜任何一侧
        assertEquals(StashMoveLogic.BoxSide.UNKNOWN, StashMoveLogic.thinPanelSide(0.0, 0.0, 1.0, 1.0));
        // 又厚又不满：不是竖板
        assertEquals(StashMoveLogic.BoxSide.UNKNOWN, StashMoveLogic.thinPanelSide(0.4, 0.4, 0.6, 0.6));
    }

    @Test
    void 珍珠检测_只认柱内不认旁边方块() {
        // 柱心 (0.5, 0.5)，容差 0.55
        assertTrue(StashMoveLogic.pearlInColumn(0.5, 0.5, 0, 0, 0.55));
        assertTrue(StashMoveLogic.pearlInColumn(1.05, 1.0, 0, 0, 0.55), "柱内贴边仍算");
        // 旧容差 0.9 会把下面这个算成「在柱内」——旁边方块上的珍珠
        assertFalse(StashMoveLogic.pearlInColumn(1.3, 0.5, 0, 0, 0.55));
        assertFalse(StashMoveLogic.pearlInColumn(0.5, 1.4, 0, 0, 0.55));
        assertTrue(StashMoveLogic.pearlInColumn(0.5, 0.5, 0, 0, 0.55), "柱心必须命中");
    }

    @Test
    void 正前方判定_贴边过程中不会半途被退回() {
        // 正前方 = 东（frontDx=1, frontDz=0），玩家从格心 (1.0, 0) 一路贴到 (0.1, 0)
        assertTrue(StashMoveLogic.onFrontSide(1.0, 0.0, 1, 0, 0.45), "正前方格心");
        assertTrue(StashMoveLogic.onFrontSide(0.5, 0.0, 1, 0, 0.45), "贴边到一半");
        assertTrue(StashMoveLogic.onFrontSide(0.1, 0.0, 1, 0, 0.45), "贴到边缘");
        // 跨过柱心 / 站在别的方向 / 偏离中线太多 / 离得太远，都不算
        assertFalse(StashMoveLogic.onFrontSide(0.0, 0.0, 1, 0, 0.45), "已到柱心");
        assertFalse(StashMoveLogic.onFrontSide(-0.5, 0.0, 1, 0, 0.45), "在门板那一侧");
        assertFalse(StashMoveLogic.onFrontSide(0.0, 1.0, 1, 0, 0.45), "在垂直侧面");
        assertFalse(StashMoveLogic.onFrontSide(1.0, 0.5, 1, 0, 0.45), "偏离中线");
        assertFalse(StashMoveLogic.onFrontSide(1.3, 0.0, 1, 0, 0.45), "离得太远");
    }

    @Test
    void 正前方判定_南北向同样成立() {
        // 正前方 = 南（frontDx=0, frontDz=1）
        assertTrue(StashMoveLogic.onFrontSide(0.0, 1.0, 0, 1, 0.45));
        assertTrue(StashMoveLogic.onFrontSide(0.0, 0.2, 0, 1, 0.45));
        assertFalse(StashMoveLogic.onFrontSide(1.0, 0.0, 0, 1, 0.45), "东西侧不算正前方");
        assertFalse(StashMoveLogic.onFrontSide(0.0, -1.0, 0, 1, 0.45), "背后不算");
    }
}
