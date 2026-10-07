package com.fo.addon.elytra.core;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import java.util.List;
public final class SettingHelper {
    private SettingHelper() {
    }
    public static BoolSetting bool(SettingGroup g, String name, String desc, boolean def) {
        return g.add(new BoolSetting.Builder().name(name).description(desc).defaultValue(def).build());
    }
    public static StringSetting string(SettingGroup g, String name, String desc, String def) {
        return g.add(new StringSetting.Builder().name(name).description(desc).defaultValue(def).build());
    }
    public static IntSetting int_(SettingGroup g, String name, String desc, int def, int min, int max) {
        return g.add(new IntSetting.Builder()
            .name(name).description(desc).defaultValue(def)
            .min(min).max(max).sliderRange(min, max)
            .build());
    }
    public static IntSetting intRaw(SettingGroup g, String name, String desc, int def, int min, int max) {
        return g.add(new IntSetting.Builder()
            .name(name).description(desc).defaultValue(def)
            .min(min).max(max).noSlider()
            .build());
    }
    public static DoubleSetting double_(SettingGroup g, String name, String desc, double def, double min, double max) {
        return g.add(new DoubleSetting.Builder()
            .name(name).description(desc).defaultValue(def)
            .min(min).max(max).sliderRange(min, max).decimalPlaces(2)
            .build());
    }
    public static <T extends Enum<?>> EnumSetting<T> enum_(SettingGroup g, String name, String desc, T def) {
        return g.add(new EnumSetting.Builder<T>().name(name).description(desc).defaultValue(def).build());
    }
    public static StringListSetting stringList(SettingGroup g, String name, String desc, List<String> def) {
        return g.add(new StringListSetting.Builder()
            .name(name).description(desc)
            .defaultValue(def.toArray(new String[0]))
            .build());
    }
    public static ItemListSetting items(SettingGroup g, String name, String desc, List<Item> def, boolean filterFood) {
        ItemListSetting.Builder builder = new ItemListSetting.Builder()
            .name(name).description(desc)
            .defaultValue(def.toArray(new Item[0]));
        if (filterFood) builder.filter(SettingHelper::isFood);
        return g.add(builder.build());
    }
    public static KeybindSetting keybind(SettingGroup g, String name, String desc) {
        return g.add(new KeybindSetting.Builder().name(name).description(desc).build());
    }
    public static boolean isFood(Item item) {
        if (item == null) return false;
        try {
            var components = item.getComponents();
            return components.contains(DataComponentTypes.FOOD)
                || components.contains(DataComponentTypes.CONSUMABLE);
        } catch (Throwable t) {
            return false;
        }
    }
}
