package com.fo.addon.utils;

import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * 蘑菇岛群系采样（纯 Java，零原生依赖）。
 *
 * <p>与 {@code AncientCityBiomeCheck} 同一套构造方式：用 {@link BuiltinRegistries} 的静态注册表
 * 建 {@link NoiseConfig} 与 {@link MultiNoiseBiomeSource}，按世界种子在 quart 坐标采样。
 * 已逐文件核对 Mojang 官方数据：1.21.1 / 1.21.11 / 26.1 的 multi_noise 参数表与 biome 定义完全一致，
 * 因此客户端 1.21.11 采出来的结果就是 26.1 口径。
 *
 * <p>移植来源：Tanghulu（https://github.com/Tanghulu2019-a/Tanghulu）蘑菇岛定位，GPLv3。
 * 原实现下载官方 jar 开独立进程；这里改为进程内采样。
 */
public final class MushroomBiomeSampler {

    private final NoiseConfig noise;
    private final MultiNoiseBiomeSource source;

    public MushroomBiomeSampler(long seed) {
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

    /** 该方块坐标（按 y=64 表层）是否为蘑菇岛群系。 */
    public boolean isMushroomAt(int blockX, int blockZ) {
        return source.getBiome(blockX >> 2, 16, blockZ >> 2, noise.getMultiNoiseSampler())
                .matchesKey(BiomeKeys.MUSHROOM_FIELDS);
    }
}
