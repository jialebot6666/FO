package com.fo.addon.utils;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 字符串映射设置：左边用户名、右边坐标（"x y z"），供「珍珠点」使用。
 *
 * <p>移植自 meteor-miku 的 util/StringMapSetting（用户点名的珍珠大管家同款）：
 * 设置页里是一张两列表格，每行「用户名输入框 | 坐标输入框 | 删除键」，
 * 下方「重置 / 加号」；数据落 NBT 的 {@code map} 复合标签，跨重启保留。
 */
public class StringMapSetting extends Setting<Map<String, String>> {

    public StringMapSetting(String name, String description, Map<String, String> defaultValue,
                            Consumer<Map<String, String>> onChanged,
                            Consumer<Setting<Map<String, String>>> onModuleActivated,
                            IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    /** 文本解析：{@code 用户名,坐标,用户名,坐标}（供指令/粘贴用），无效片段静默忽略。 */
    @Override
    protected Map<String, String> parseImpl(String str) {
        String[] values = str.split(",");
        Map<String, String> map = new LinkedHashMap<>(values.length / 2);
        try {
            String left = null;
            for (int i = 0; i < values.length; i++) {
                if (i % 2 == 0) left = values[i].trim();
                else if (left != null && !left.isEmpty()) map.put(left, values[i].trim());
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    @Override
    protected boolean isValueValid(Map<String, String> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashMap<>(defaultValue);
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        NbtCompound mapTag = new NbtCompound();
        for (Map.Entry<String, String> e : get().entrySet()) {
            mapTag.putString(e.getKey(), e.getValue());
        }
        tag.put("map", mapTag);
        return tag;
    }

    @Override
    public Map<String, String> load(NbtCompound tag) {
        get().clear();
        NbtCompound mapTag = tag.getCompoundOrEmpty("map");
        for (String key : mapTag.getKeys()) {
            get().put(key, mapTag.getString(key, ""));
        }
        return get();
    }

    /** 设置页表格（miku 同款：每行 用户名 | 坐标 | 删除，底部 重置 | 加号）。 */
    public static void fillTable(GuiTheme theme, WTable table, StringMapSetting setting) {
        table.clear();
        Map<String, String> map = setting.get();

        for (String key : map.keySet()) {
            AtomicReference<String> keyRef = new AtomicReference<>(key);

            WTextBox keyBox = table.add(theme.textBox(keyRef.get())).minWidth(150).expandX().widget();
            keyBox.actionOnUnfocused = () -> {
                String text = keyBox.get().trim();
                if (text.isEmpty() || map.containsKey(text)) {
                    keyBox.set(keyRef.get());
                    return;
                }
                String value = map.remove(keyRef.get());
                keyRef.set(text);
                map.put(text, value);
            };

            WTextBox valueBox = table.add(theme.textBox(map.get(keyRef.get()), (text, c) -> true))
                .minWidth(120).expandX().widget();
            valueBox.actionOnUnfocused = () -> map.replace(keyRef.get(), valueBox.get());

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                map.remove(keyRef.get());
                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!map.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton reset = table.add(theme.button("重置")).widget();
        reset.action = () -> {
            setting.reset();
            fillTable(theme, table, setting);
        };

        WPlus add = table.add(theme.plus()).widget();
        add.action = () -> {
            map.put("新珍珠点" + map.size(), "0 0 0");
            fillTable(theme, table, setting);
        };

        table.row();
    }

    public static class Builder extends Setting.SettingBuilder<Builder, Map<String, String>, StringMapSetting> {
        public Builder() {
            super(new LinkedHashMap<>(0));
        }

        public Builder defaultValue(Map<String, String> map) {
            this.defaultValue = map;
            return this;
        }

        @Override
        public StringMapSetting build() {
            return new StringMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
