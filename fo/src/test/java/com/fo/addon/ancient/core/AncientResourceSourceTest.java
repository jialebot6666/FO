package com.fo.addon.ancient.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 古城数据源与加载缓存的防回归测试（古城自写版）。
 *
 * <p>守住三件事：
 * <ol>
 *   <li>mod 内打包的 58 个结构模板 + 7 个拼图池能被 classpath 数据源读到；</li>
 *   <li>缺失资源要返回 null / 空池，不能抛异常；</li>
 *   <li>缓存按数据源隔离（不同数据源的同名资源不得互相串用），且并发下安全
 *       ——模块是在 CompletableFuture 后台线程里跑扫描的。</li>
 * </ol>
 */
class AncientResourceSourceTest {

    /** 七个拼图池（相对 template_pool 的路径，不含 .json）。 */
    private static final String[] POOLS = {
        "minecraft:ancient_city/city_center",
        "minecraft:ancient_city/city/entrance",
        "minecraft:ancient_city/structures",
        "minecraft:ancient_city/walls",
        "minecraft:ancient_city/walls/no_corners",
        "minecraft:ancient_city/city_center/walls",
        "minecraft:ancient_city/sculk",
    };

    /** 三个起始件模板（city_center_1/2/3）。 */
    private static final String[] CENTERS = {
        "minecraft:ancient_city/city_center/city_center_1",
        "minecraft:ancient_city/city_center/city_center_2",
        "minecraft:ancient_city/city_center/city_center_3",
    };

    @Test
    void classpath数据源能读到结构与拼图池() {
        AncientResourceSource structures = AncientResourceSource.bundledStructures();
        AncientResourceSource pools = AncientResourceSource.bundledPools();

        for (String loc : CENTERS) {
            AncientTemplate tpl = AncientTemplate.load(loc, structures);
            assertNotNull(tpl, "缺少模板: " + loc);
            assertTrue(tpl.size[0] > 0 && tpl.size[1] > 0 && tpl.size[2] > 0, "模板尺寸异常: " + loc);
            assertFalse(tpl.blocks.isEmpty(), "模板没有方块: " + loc);
            assertFalse(tpl.jigsawBlocks.isEmpty(), "模板没有拼图块: " + loc);
        }

        for (String pool : POOLS) {
            AncientPool p = AncientPool.load(pool, pools);
            assertFalse(p.elements.isEmpty(), "池为空(可能文件缺失): " + pool);
            // 注意：structures.json 的第一项本来就是 empty_pool_element（weight=7），
            // 所以不能拿「首元素不是 empty」判断，要判断「池里至少有一个真实元素」。
            assertTrue(p.elements.stream().anyMatch(e -> !"empty".equals(e.type)),
                "池退化成空池(文件缺失?): " + pool);
        }
    }

    @Test
    void 缺失资源返回空值而不是抛异常(@TempDir Path emptyDir) {
        AncientResourceSource missing = AncientResourceSource.directory(emptyDir);
        assertNull(AncientTemplate.load("minecraft:no_such/structure", missing));
        // 池缺失时退化为 empty 池（与 MC Pools.EMPTY 行为一致），不是 null
        AncientPool pool = AncientPool.load("minecraft:no_such/pool", missing);
        assertEquals(1, pool.elements.size());
        assertEquals("empty", pool.elements.get(0).type);
        assertEquals("minecraft:empty", pool.fallback);
    }

    @Test
    void 缓存按数据源隔离不串数据(@TempDir Path emptyDir) {
        AncientResourceSource bundled = AncientResourceSource.bundledStructures();
        AncientResourceSource empty = AncientResourceSource.directory(emptyDir);
        String loc = "minecraft:ancient_city/city_center/city_center_1";

        // 先让 classpath 源把结果灌进缓存
        assertNotNull(AncientTemplate.load(loc, bundled));
        // 空目录源必须自己得出「没有」，不能命中 classpath 源的缓存
        assertNull(AncientTemplate.load(loc, empty), "不同数据源的同名结构串了缓存");

        // 反向再来一遍，确认两个方向都隔离
        AncientResourceSource other = AncientResourceSource.directory(emptyDir.resolve("nested"));
        assertNull(AncientTemplate.load(loc, other));
        assertNotNull(AncientTemplate.load(loc, bundled));

        assertNotEquals(AncientResourceSource.directory(emptyDir).id(), bundled.id());
    }

    @Test
    void 并发加载同一模板安全且结果一致() throws Exception {
        AncientResourceSource structures = AncientResourceSource.bundledStructures();
        AncientResourceSource pools = AncientResourceSource.bundledPools();
        AncientTemplate.clearCache();
        AncientPool.clearCache();

        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            jobs.add(() -> {
                int hash = 0;
                for (String loc : CENTERS) {
                    AncientTemplate tpl = AncientTemplate.load(loc, structures);
                    hash = hash * 31 + (tpl == null ? -1 : tpl.jigsawBlocks.size() + tpl.blocks.size());
                }
                hash = hash * 31 + AncientPool.load(POOLS[0], pools).elements.size();
                return hash;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futures = pool.invokeAll(jobs, 60, TimeUnit.SECONDS);
            Integer expected = futures.get(0).get();
            for (Future<Integer> f : futures) {
                assertEquals(expected, f.get(), "并发加载结果不一致（缓存竞争）");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 混合数据源并发加载不抛异常(@TempDir Path emptyDir) throws Exception {
        AncientResourceSource bundled = AncientResourceSource.bundledStructures();
        AncientResourceSource empty = AncientResourceSource.directory(emptyDir);
        String loc = "minecraft:ancient_city/city_center/city_center_2";

        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            boolean useBundled = i % 2 == 0;
            jobs.add(() -> {
                AncientTemplate tpl = AncientTemplate.load(loc, useBundled ? bundled : empty);
                if (useBundled) {
                    assertNotNull(tpl);
                } else {
                    assertNull(tpl);
                }
                return null;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (Future<Void> f : pool.invokeAll(jobs, 60, TimeUnit.SECONDS)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
