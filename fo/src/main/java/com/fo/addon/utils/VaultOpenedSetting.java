package com.fo.addon.utils;

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Supplier;

/**
 * 「已打开的不祥宝库记录」展示 + 清理按钮（V6.9p25）。
 *
 * <p>和 {@code StealStatsSetting} 一样：本身不承载任何配置，只是给
 * {@code SettingsWidgetFactory} 一个挂载点——设置页里显示「已记录 N 个」+「清理记录」按钮。
 * save/load 全空，不往配置里写任何键。
 */
public class VaultOpenedSetting extends Setting<Boolean> {

    private final Supplier<String> statusText;
    private final Runnable clearAction;

    public VaultOpenedSetting(String name, String description, Supplier<String> statusText,
                              Runnable clearAction, IVisible visible) {
        super(name, description, Boolean.FALSE, value -> { }, null, visible);
        this.statusText = statusText;
        this.clearAction = clearAction;
    }

    /** 设置页要显示的一行文字（条数 / 作用域）。 */
    public String statusText() {
        return statusText.get();
    }

    /** 设置页「清理记录」按钮的动作。 */
    public void clearRecords() {
        clearAction.run();
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

    public static class Builder extends Setting.SettingBuilder<Builder, Boolean, VaultOpenedSetting> {
        private Supplier<String> statusText = () -> "";
        private Runnable clearAction = () -> { };

        public Builder() {
            super(false);
        }

        public Builder statusText(Supplier<String> statusText) {
            this.statusText = statusText;
            return this;
        }

        public Builder clearAction(Runnable clearAction) {
            this.clearAction = clearAction;
            return this;
        }

        @Override
        public VaultOpenedSetting build() {
            return new VaultOpenedSetting(name, description, statusText, clearAction, visible);
        }
    }
}
