package com.fo.addon.ancient;

import com.fo.addon.ancient.core.AncientChestStore;
import com.fo.addon.ancient.core.FatChestLogic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 肥箱阈值判定与排序。 */
class FatChestLogicTest {

    private static AncientChestStore.Mark mark(int x, int z, int gold) {
        return new AncientChestStore.Mark(x, 0, z, AncientChestStore.LABEL_GOLD, gold);
    }

    @Test
    void thresholdIsInclusive() {
        assertTrue(FatChestLogic.isFat(mark(0, 0, 3), 3));
        assertFalse(FatChestLogic.isFat(mark(0, 0, 2), 3));
    }

    @Test
    void unknownCountIsNotFat() {
        assertFalse(FatChestLogic.isFat(mark(0, 0, 0), 3));
    }

    @Test
    void preferFatPutsFatFirstEvenIfFarther() {
        var far = mark(100, 0, 4);
        var near = mark(1, 0, 1);
        var out = FatChestLogic.preferFat(List.of(near, far), 3, 0, 0);
        assertEquals(far, out.get(0));
        assertEquals(near, out.get(1));
    }

    @Test
    void sameTierSortsByDistance() {
        var a = mark(10, 0, 4);
        var b = mark(5, 0, 4);
        var out = FatChestLogic.preferFat(List.of(a, b), 3, 0, 0);
        assertEquals(b, out.get(0));
        assertEquals(a, out.get(1));
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertTrue(FatChestLogic.preferFat(List.of(), 3, 0, 0).isEmpty());
        assertTrue(FatChestLogic.preferFat(null, 3, 0, 0).isEmpty());
    }

    @Test
    void inputIsNotMutated() {
        var a = mark(10, 0, 4);
        var b = mark(5, 0, 1);
        var src = List.of(a, b);
        FatChestLogic.preferFat(src, 3, 0, 0);
        assertEquals(a, src.get(0));
        assertEquals(b, src.get(1));
    }
}
