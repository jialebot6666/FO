package com.fo.addon;

import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.generator.structure.EndCityGenerator;
import com.seedfinding.mcseed.lcg.LCG;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * seedfinding 库冒烟测试：
 * 验证打进 jar 的核心依赖库可加载、计算可复现。
 * （ElytraCollector 的世界种子 / 末地船定位逻辑依赖这些库）
 */
public class SeedfindingSmokeTest {

    @Test
    void lcgMatchesJavaRandomFormula() {
        // MC 世界种子计算使用的 LCG 参数与 java.util.Random 一致
        LCG lcg = LCG.JAVA;
        long next = lcg.nextSeed(12345L);
        long expected = ((12345L * 25214903917L + 11L) & ((1L << 48) - 1));
        assertEquals(expected, next);
    }

    @Test
    void lcgChunkRandCompatible() {
        // ChunkRand 内部 LCG 可加载并可驱动（ElytraCollector 用 ChunkRand 算结构位置）
        com.seedfinding.mccore.rand.ChunkRand rand = new com.seedfinding.mccore.rand.ChunkRand();
        assertNotNull(rand);
    }

    @Test
    void endCityGeneratorConstructsForSupportedVersions() {
        EndCityGenerator gen = new EndCityGenerator(MCVersion.v1_21);
        assertNotNull(gen);
        assertNotNull(gen.getGlobalPieces());
    }

    @Test
    void mcVersionLatestAvailable() {
        MCVersion v = MCVersion.latest();
        assertNotNull(v);
        assertTrue(v.isNewerOrEqualTo(MCVersion.v1_20_6));
    }
}
