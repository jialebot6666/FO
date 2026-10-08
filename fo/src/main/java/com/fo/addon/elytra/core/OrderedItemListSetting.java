package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.SettingHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;

public class OrderedItemListSetting
extends ItemListSetting {
    public OrderedItemListSetting(String name, String description, List<Item> defaultValue, Consumer<List<Item>> onChanged, Consumer<Setting<List<Item>>> onModuleActivated, IVisible visible, Predicate<Item> filter, boolean bypassFilterWhenSavingAndLoading) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible, filter, bypassFilterWhenSavingAndLoading);
    }

    public List<Item> order() {
        List list = (List)this.get();
        return list == null ? List.of() : list;
    }

    private List<Item> onlyFood(List<Item> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<Item>();
        }
        ArrayList<Item> out = new ArrayList<Item>();
        int dropped = 0;
        for (Item item : list) {
            if (item == null) continue;
            if (!SettingHelper.isFood(item)) {
                ++dropped;
                continue;
            }
            if (out.contains(item)) continue;
            out.add(item);
        }
        if (dropped > 0) {
            FOElytraLog.warn("\u98df\u7269\u4f18\u5148\u7ea7\uff1a\u5df2\u79fb\u9664 %d \u4e2a\u975e\u98df\u7269\u7269\u54c1\uff08\u4e0d\u80fd\u5403\u7684\u4e1c\u897f\u4e0d\u53c2\u4e0e\u4f18\u5148\u7ea7\uff09", dropped);
        }
        return out;
    }

    public boolean set(List<Item> value) {
        return super.set(this.onlyFood(value));
    }

    protected List<Item> parseImpl(String string) {
        return this.onlyFood(super.parseImpl(string));
    }

    public List<Item> load(NbtCompound tag) {
        return this.onlyFood(super.load(tag));
    }

    public void setAll(List<Item> list) {
        this.set(list == null ? new ArrayList<Item>() : new ArrayList<Item>(list));
    }

    public void move(int from, int to) {
        ArrayList<Item> list = new ArrayList<Item>(this.order());
        if (from < 0 || from >= list.size()) {
            return;
        }
        int dst = Math.max(0, Math.min(list.size() - 1, to));
        if (dst == from) {
            return;
        }
        list.add(dst, (Item)list.remove(from));
        this.set(list);
    }

    public void moveUp(int index) {
        this.move(index, index - 1);
    }

    public void moveDown(int index) {
        this.move(index, index + 1);
    }

    public void removeAt(int index) {
        ArrayList<Item> list = new ArrayList<Item>(this.order());
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        this.set(list);
    }

    public int addLast(Item item) {
        if (item == null) {
            return -1;
        }
        if (!SettingHelper.isFood(item)) {
            FOElytraLog.warn("\u98df\u7269\u4f18\u5148\u7ea7\u53ea\u63a5\u53d7\u98df\u7269\uff1a%s \u4e0d\u80fd\u5403\uff0c\u6ca1\u52a0\u8fdb\u53bb", item.getName().getString());
            return -1;
        }
        ArrayList<Item> list = new ArrayList<Item>(this.order());
        int existing = list.indexOf(item);
        if (existing >= 0) {
            return existing;
        }
        list.add(item);
        this.set(list);
        return list.size() - 1;
    }

    public static class Builder
    extends Setting.SettingBuilder<Builder, List<Item>, OrderedItemListSetting> {
        private Predicate<Item> filter;
        private boolean bypassFilterWhenSavingAndLoading;

        public Builder() {
            super(new ArrayList());
        }

        public Builder defaultValue(Item ... items) {
            this.defaultValue = items == null ? new ArrayList() : new ArrayList<Item>(List.of(items));
            return this;
        }

        public Builder filter(Predicate<Item> filter) {
            this.filter = filter;
            return this;
        }

        public Builder bypassFilterWhenSavingAndLoading() {
            this.bypassFilterWhenSavingAndLoading = true;
            return this;
        }

        public OrderedItemListSetting build() {
            return new OrderedItemListSetting(this.name, this.description, (List)this.defaultValue, this.onChanged, this.onModuleActivated, this.visible, this.filter, this.bypassFilterWhenSavingAndLoading);
        }
    }
}

