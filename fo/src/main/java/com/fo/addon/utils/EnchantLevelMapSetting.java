package com.fo.addon.utils;

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * 附魔ID → 等级 的映射设置（V6.22，仿 Meteor 官方 StatusEffectAmplifierMapSetting 自研）。
 *
 * <p>只存启用中（等级 &gt; 0）的附魔；专属设置界面见 EnchantLevelMapSettingScreen
 * （全部 43 个附魔逐行列出，每行一个等级编辑框，带搜索）。
 * 持久化：NBT {@code value} 复合标签，键 = 附魔 ID（minecraft:xxx），值 = 最低命中等级。
 */
public class EnchantLevelMapSetting extends Setting<Object2IntMap<String>> {

    public EnchantLevelMapSetting(String name, String description, Object2IntMap<String> defaultValue,
                                  Consumer<Object2IntMap<String>> onChanged,
                                  Consumer<Setting<Object2IntMap<String>>> onModuleActivated,
                                  IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    public void resetImpl() {
        value = new Object2IntOpenHashMap<>(defaultValue);
    }

    /** 文本解析（供指令/粘贴用）：{@code minecraft:swift_sneak 3, mending 1}，无效片段静默忽略。 */
    @Override
    protected Object2IntMap<String> parseImpl(String str) {
        Object2IntMap<String> out = new Object2IntOpenHashMap<>();
        if (str == null) return out;
        for (String part : str.split(",")) {
            String[] split = part.trim().split("\\s+");
            if (split.length != 2) continue;
            EnchantList.Entry entry = EnchantList.findById(split[0]);
            if (entry == null) continue;
            int level;
            try {
                level = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (level > 0) out.put(entry.id(), Math.min(level, entry.maxLevel()));
        }
        return out;
    }

    @Override
    protected boolean isValueValid(Object2IntMap<String> value) {
        return true;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        NbtCompound valueTag = new NbtCompound();
        for (Object2IntMap.Entry<String> e : get().object2IntEntrySet()) {
            if (e.getIntValue() > 0) valueTag.putInt(e.getKey(), e.getIntValue());
        }
        tag.put("value", valueTag);
        return tag;
    }

    @Override
    public Object2IntMap<String> load(NbtCompound tag) {
        get().clear();
        NbtCompound valueTag = tag.getCompoundOrEmpty("value");
        for (String key : valueTag.getKeys()) {
            // 只收 EnchantList 里认识的附魔，等级 > 0；旧档多余键静默丢弃
            if (EnchantList.findById(key) == null) continue;
            int level = valueTag.getInt(key, 0);
            if (level > 0) get().put(key, level);
        }
        return get();
    }

    /** 当前启用中的规则数量（等级 > 0 的附魔数），供启动日志与界面显示。 */
    public int enabledCount() {
        int n = 0;
        for (Object2IntMap.Entry<String> e : get().object2IntEntrySet()) {
            if (e.getIntValue() > 0) n++;
        }
        return n;
    }

    public static class Builder extends Setting.SettingBuilder<Builder, Object2IntMap<String>, EnchantLevelMapSetting> {
        public Builder() {
            super(new Object2IntOpenHashMap<>(0));
        }

        @Override
        public EnchantLevelMapSetting build() {
            return new EnchantLevelMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
