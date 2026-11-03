package com.fo.addon.utils;

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

/**
 * 「本次搬运」展示型设置（仿 StealStatsSetting 的挂载点思路，但前端重做）。
 *
 * <p>本身不承载配置：save/load 全空，不往配置里写键。
 * 设置页控件在 {@code AddonTemplate} 注册：一行「本次搬运：X 趟 · Y 个潜影盒」+
 * 右侧原生的「重置」按钮（与普通功能重置键同样对齐），点击立即清零。
 */
public class StashStatsSetting extends Setting<Boolean> {

    public StashStatsSetting(String name, String description, Boolean defaultValue,
                             Consumer<Boolean> onChanged,
                             Consumer<Setting<Boolean>> onModuleActivated,
                             IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    public void resetImpl() {
        value = defaultValue;
    }

    @Override
    protected Boolean parseImpl(String str) {
        return defaultValue;
    }

    @Override
    protected boolean isValueValid(Boolean value) {
        return true;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        return tag;
    }

    @Override
    public Boolean load(NbtCompound tag) {
        return get();
    }

    public static class Builder extends Setting.SettingBuilder<Builder, Boolean, StashStatsSetting> {
        public Builder() {
            super(false);
        }

        @Override
        public StashStatsSetting build() {
            return new StashStatsSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
