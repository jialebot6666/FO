package com.fo.addon;

import com.fo.addon.ancient.core.AncientXoroshiro;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Xoroshiro 世界生成随机数防回归测试（古城自写版）。
 *
 * <p>基准来源：zychen027/AncientCityLootPredictor 的 Python 复刻。断言值取自
 * 种子 -7346913998703726680、区块 (779,-595) 的 decoration 链，已与 Python 逐值对照一致。
 */
public class AncientXoroshiroTest {

    @Test
    void decorationSeedAndLootSeeds() {
        AncientXoroshiro.WorldgenRandom r = new AncientXoroshiro.WorldgenRandom();
        long decorationSeed = r.setDecorationSeed(-7346913998703726680L, 779 * 16, -595 * 16);
        assertEquals(-4654619850048351224L, decorationSeed);
        r.setFeatureSeed(decorationSeed, 0, 7);
        assertEquals(-3814752086172659699L, r.nextLong());
        assertEquals(-5886386224116812970L, r.nextLong());
        assertEquals(113242071983172222L, r.nextLong());
    }
}
