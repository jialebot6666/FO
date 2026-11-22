package com.fo.addon.ancient.core;

import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * 古城群系验证（纯 Java，零原生依赖）。
 *
 * <p>语义对齐 cubiomes 的 {@code isViableStructurePos(Ancient_City, ...)}：在 4:1（quart）
 * 坐标上采样一次生物群系，判断是否为深暗之域（Deep Dark）。采样点取 city_center 包围盒中心，
 * 与 Tanghulu 的 {@code VanillaBiomes} 做法一致。
 *
 * <p>采样器由 {@link BuiltinRegistries} 静态注册表 + 世界种子构造，不依赖 {@code ClientLevel}，
 * 也不加载任何区块，因此可在后台搜索线程直接使用。
 */
public final class AncientCityBiomeCheck {

    private final NoiseConfig noise;
    private final MultiNoiseBiomeSource source;

    public AncientCityBiomeCheck(long seed) {
        RegistryWrapper.WrapperLookup registries = BuiltinRegistries.createWrapperLookup();
        this.noise = NoiseConfig.create(
                registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
                        .getOrThrow(ChunkGeneratorSettings.OVERWORLD).value(),
                registries.getOrThrow(RegistryKeys.NOISE_PARAMETERS),
                seed);
        this.source = MultiNoiseBiomeSource.create(
                registries.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
    }

    /**
     * @param centerBlockX city_center 包围盒中心的方块 X
     * @param centerBlockZ city_center 包围盒中心的方块 Z
     * @return 该点（y 固定 -27）是否为深暗之域
     */
    public boolean isDeepDarkAtCenter(int centerBlockX, int centerBlockZ) {
        return source.getBiome(
                        Math.floorDiv(centerBlockX, 4),
                        Math.floorDiv(-27, 4),
                        Math.floorDiv(centerBlockZ, 4),
                        noise.getMultiNoiseSampler())
                .matchesKey(BiomeKeys.DEEP_DARK);
    }
}
