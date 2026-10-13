package com.fo.addon.utils;

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

/**
 * 「偷取统计」展示型设置（V6.27）。
 *
 * <p>它本身不承载任何配置——只是给 {@code SettingsWidgetFactory} 提供一个挂载点，
 * 让设置页在「自动偷取」开关的正下方画出累计统计那几行文字
 * （控件实现在 {@code AddonTemplate} 里注册，取 {@link com.fo.addon.ancient.core.AncientStealStats#displayLines()}）。
 *
 * <p>为什么不直接塞个 {@code BoolSetting} 之类：那会往配置里写一个毫无意义的键，
 * 而且语义上误导。这里 save/load 全部留空，落盘时不产生任何数据。
 */
public class StealStatsSetting extends Setting<Boolean> {

    public StealStatsSetting(String name, String description, Boolean defaultValue,
                             Consumer<Boolean> onChanged,
                             Consumer<Setting<Boolean>> onModuleActivated,
                             IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    public void resetImpl() {
        value = defaultValue;
    }

    /** 文本解析：本设置无值可解析（永远返回默认值）。 */
    @Override
    protected Boolean parseImpl(String str) {
        return defaultValue;
    }

    @Override
    protected boolean isValueValid(Boolean value) {
        return true;
    }

    /** 不落盘：统计是只读展示，配置里不留任何键。 */
    @Override
    public NbtCompound save(NbtCompound tag) {
        return tag;
    }

    @Override
    public Boolean load(NbtCompound tag) {
        return get();
    }

    public static class Builder extends Setting.SettingBuilder<Builder, Boolean, StealStatsSetting> {
        public Builder() {
            super(false);
        }

        @Override
        public StealStatsSetting build() {
            return new StealStatsSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
