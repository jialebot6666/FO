package com.fo.addon.utils;

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

/**
 * 「组内小标题」显示型设置（V6.7p37 界面改版）。
 *
 * <p>用途：一个分组里设置项太多（自动搬运 5 组 68 项），只靠组标题分不出「坐标 / 参数 / 危险」，
 * 又不能改名或跨组搬移（Meteor 用组名+设置名当配置键，动了老配置就读不出来，见 AGENTS.md）。
 * 所以在组内插入若干条只读小标题，用原生分隔线控件画成「坐标 ──────」的样子。
 *
 * <p>关键性质：**不承载任何配置** —— {@link #save} / {@link #load} 都是空的，值恒等于默认值，
 * 因此 {@code wasChanged()} 永远为 false，不会被写进存档；新增/删除小标题不影响老配置。
 *
 * <p>控件在 {@code AddonTemplate} 注册（{@code SettingsWidgetFactory.registerCustomFactory}）：
 * 摘掉框架自动加的左侧标题行，再画一条带文字的原生水平分隔线。
 */
public class FoSectionSetting extends Setting<Boolean> {

    public FoSectionSetting(String name, String description, Boolean defaultValue,
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

    public static class Builder extends Setting.SettingBuilder<Builder, Boolean, FoSectionSetting> {
        public Builder() {
            super(false);
        }

        @Override
        public FoSectionSetting build() {
            return new FoSectionSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
