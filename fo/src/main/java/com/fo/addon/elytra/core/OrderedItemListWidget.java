package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.OrderedItemListSetting;
import com.fo.addon.elytra.core.SettingHelper;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.screens.settings.ItemListSettingScreen;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import org.lwjgl.glfw.GLFW;

public class OrderedItemListWidget
extends WVerticalList {
    private static final int VISIBLE_ROWS = 6;
    private static boolean factoryRegistered;
    private final OrderedItemListSetting setting;
    private final List<WHorizontalList> rows = new ArrayList<WHorizontalList>();
    private final List<List<WWidget>> rowButtons = new ArrayList<List<WWidget>>();
    private final List<WLabel> rowLabels = new ArrayList<WLabel>();
    private final List<WItem> rowItems = new ArrayList<WItem>();
    private int scrollTop;
    private int dragIndex = -1;
    private int dropIndex = -1;
    private int highlightIndex = -1;

    public OrderedItemListWidget(GuiTheme theme, OrderedItemListSetting setting) {
        this.theme = theme;
        this.setting = setting;
        this.tooltip = setting.description;
    }

    public static void registerFactory() {
        if (factoryRegistered) {
            return;
        }
        factoryRegistered = true;
        try {
            SettingsWidgetFactory.registerCustomFactory(OrderedItemListSetting.class, theme -> (table, setting) -> {
                if (!(setting instanceof OrderedItemListSetting)) {
                    return;
                }
                OrderedItemListSetting s = (OrderedItemListSetting)setting;
                OrderedItemListWidget widget = new OrderedItemListWidget((GuiTheme)theme, s);
                table.add((WWidget)widget).expandCellX().widget();
                table.row();
            });
            FOElytraLog.detail("\u98df\u7269\u4f18\u5148\u7ea7\uff1a\u8bbe\u7f6e\u9875\u5185\u8054\u5217\u8868\u63a7\u4ef6\u5df2\u6ce8\u518c", new Object[0]);
        }
        catch (Throwable t) {
            factoryRegistered = false;
            FOElytraLog.warn("\u6ce8\u518c\u98df\u7269\u4f18\u5148\u7ea7\u5217\u8868\u63a7\u4ef6\u5931\u8d25\uff1a%s", String.valueOf(t));
        }
    }

    public void init() {
        super.init();
        this.rebuild();
    }

    private void rebuild() {
        int from;
        this.clear();
        this.rows.clear();
        this.rowButtons.clear();
        this.rowLabels.clear();
        this.rowItems.clear();
        this.add((WWidget)this.theme.label("\u98df\u7269\u4f18\u5148\u7ea7\uff08\u8d8a\u4e0a\u9762\u8d8a\u5148\u5403\u3001\u8d8a\u5148\u8865\uff1b\u6309\u4f4f\u6574\u884c\u53ef\u4ee5\u62d6\u52a8\u6392\u5e8f\uff09")).expandX();
        List<Item> all = this.setting.order();
        if (all.isEmpty()) {
            this.add((WWidget)this.theme.label("\uff08\u5217\u8868\u662f\u7a7a\u7684\uff1a\u70b9\u4e0b\u9762\u7684\u300c+ \u6dfb\u52a0\u98df\u7269\u300d\u52a0\uff0c\u53ea\u63a5\u53d7\u80fd\u5403\u7684\u4e1c\u897f\uff09")).expandX();
        }
        this.scrollTop = from = Math.max(0, Math.min(this.scrollTop, Math.max(0, all.size() - 6)));
        int to = Math.min(all.size(), from + 6);
        for (int i = from; i < to; ++i) {
            int idx = i;
            Item item = all.get(i);
            WHorizontalList row = (WHorizontalList)this.add((WWidget)this.theme.horizontalList()).expandX().widget();
            WItem icon = (WItem)row.add((WWidget)this.theme.item(item.getDefaultStack())).widget();
            WLabel label = (WLabel)row.add((WWidget)this.theme.label(this.labelText(i, item))).expandCellX().widget();
            ArrayList<WWidget> buttons = new ArrayList<WWidget>();
            WButton up = (WButton)row.add((WWidget)this.theme.button("\u2191")).widget();
            up.action = () -> {
                this.setting.moveUp(idx);
                this.rememberScroll(idx - 1);
                this.rebuild();
            };
            buttons.add(up);
            WButton down = (WButton)row.add((WWidget)this.theme.button("\u2193")).widget();
            down.action = () -> {
                this.setting.moveDown(idx);
                this.rememberScroll(idx + 1);
                this.rebuild();
            };
            buttons.add(down);
            WButton remove = (WButton)row.add((WWidget)this.theme.button("\u2715")).widget();
            remove.action = () -> {
                this.setting.removeAt(idx);
                FOElytraLog.info("\u98df\u7269\u4f18\u5148\u7ea7\uff1a\u79fb\u9664\u4e86 %s\uff08\u8fd8\u5269 %d \u9879\uff09", item.getName().getString(), this.setting.order().size());
                this.rememberScroll(idx);
                this.rebuild();
            };
            buttons.add(remove);
            this.rows.add(row);
            this.rowButtons.add(buttons);
            this.rowLabels.add(label);
            this.rowItems.add(icon);
        }
        if (all.size() > 6) {
            WLabel more = (WLabel)this.add((WWidget)this.theme.label(String.format("\uff08\u663e\u793a\u7b2c %d~%d \u9879 / \u5171 %d \u9879\uff1a\u6eda\u8f6e\u7ffb\u9875\uff09", from + 1, to, all.size()))).expandX().widget();
            more.color = this.theme.textSecondaryColor();
        }
        WHorizontalList bottom = (WHorizontalList)this.add((WWidget)this.theme.horizontalList()).expandX().widget();
        WButton addFood = (WButton)bottom.add((WWidget)this.theme.button("+ \u6dfb\u52a0\u98df\u7269")).widget();
        addFood.action = this::openPicker;
        WButton addHeld = (WButton)bottom.add((WWidget)this.theme.button("+ \u6dfb\u52a0\u624b\u4e0a\u7684\u98df\u7269")).widget();
        addHeld.action = this::addHeld;
        WLabel hint = (WLabel)bottom.add((WWidget)this.theme.label("\uff08\u53ea\u63a5\u53d7\u98df\u7269\uff1b\u91cd\u590d\u7684\u4e0d\u4f1a\u91cd\u590d\u52a0\uff09")).expandCellX().widget();
        hint.color = this.theme.textSecondaryColor();
    }

    private void rememberScroll(int index) {
        if (index < this.scrollTop) {
            this.scrollTop = Math.max(0, index);
        } else if (index >= this.scrollTop + 6) {
            this.scrollTop = Math.max(0, index - 6 + 1);
        }
    }

    private String labelText(int index, Item item) {
        String base = index + 1 + ". " + item.getName().getString();
        if (this.dragIndex >= 0 && index == this.dropIndex) {
            base = "\u25b6 " + base;
        }
        if (this.dragIndex == index) {
            base = base + "\uff08\u62d6\u7740\uff09";
        }
        if (index == this.highlightIndex) {
            base = "\u25b8 " + base;
        }
        return base;
    }

    private void markRows() {
        List<Item> all = this.setting.order();
        for (int i = 0; i < this.rowLabels.size(); ++i) {
            int abs = this.scrollTop + i;
            WLabel label = this.rowLabels.get(i);
            if (label == null || abs >= all.size()) continue;
            label.set(this.labelText(abs, all.get(abs)));
        }
    }

    private void openPicker() {
        try {
            ItemListSettingScreen screen = new ItemListSettingScreen(GuiThemes.get(), (ItemListSetting)this.setting);
            MinecraftClient.getInstance().setScreen((Screen)screen);
        }
        catch (Throwable t) {
            FOElytraLog.warn("\u6253\u4e0d\u5f00\u98df\u7269\u9009\u62e9\u754c\u9762\uff1a%s", String.valueOf(t));
        }
    }

    private void addHeld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.player.getMainHandStack().isEmpty()) {
            FOElytraLog.warn("\u624b\u4e0a\u6ca1\u6709\u4e1c\u897f\uff1a\u5148\u62ff\u4e00\u4e2a\u5403\u7684\u518d\u70b9\u8fd9\u4e2a\u6309\u94ae", new Object[0]);
            return;
        }
        Item held = mc.player.getMainHandStack().getItem();
        if (!SettingHelper.isFood(held)) {
            FOElytraLog.warn("\u98df\u7269\u4f18\u5148\u7ea7\u53ea\u63a5\u53d7\u98df\u7269\uff1a%s \u4e0d\u80fd\u5403\uff0c\u6ca1\u52a0\u8fdb\u53bb", held.getName().getString());
            return;
        }
        int before = this.setting.order().size();
        int index = this.setting.addLast(held);
        if (index < 0) {
            return;
        }
        if (before == this.setting.order().size()) {
            FOElytraLog.info("\u98df\u7269\u4f18\u5148\u7ea7\uff1a%s \u5df2\u7ecf\u5728\u5217\u8868\u91cc\uff08\u7b2c %d \u4f4d\uff09\uff0c\u6ca1\u91cd\u590d\u6dfb\u52a0", held.getName().getString(), index + 1);
        } else {
            FOElytraLog.info("\u98df\u7269\u4f18\u5148\u7ea7\uff1a%s \u52a0\u5230\u7b2c %d \u4f4d\uff08\u5171 %d \u9879\uff09", held.getName().getString(), index + 1, this.setting.order().size());
        }
        this.highlightIndex = index;
        this.rememberScroll(index);
        this.rebuild();
    }

    private int rowAt(double mouseY) {
        for (int i = 0; i < this.rows.size(); ++i) {
            WHorizontalList row = this.rows.get(i);
            if (row == null || !(mouseY >= row.y) || !(mouseY <= row.y + row.height)) continue;
            return i;
        }
        return -1;
    }

    private boolean overButton(double mouseX, double mouseY, int index) {
        if (index < 0 || index >= this.rowButtons.size()) {
            return false;
        }
        for (WWidget w : this.rowButtons.get(index)) {
            if (w == null || !(mouseX >= w.x) || !(mouseX <= w.x + w.width) || !(mouseY >= w.y) || !(mouseY <= w.y + w.height)) continue;
            return true;
        }
        return false;
    }

    private void updateDrop(double mouseY) {
        int idx = this.rowAt(mouseY);
        if (idx >= 0 && idx != this.dropIndex) {
            this.dropIndex = idx;
            this.markRows();
        }
    }

    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (this.dragIndex >= 0) {
            this.updateDrop(mouseY);
            if (!this.leftButtonDown()) {
                this.commitDrag(this.rowAt(mouseY));
            }
        }
        return super.render(renderer, mouseX, mouseY, delta);
    }

    private boolean leftButtonDown() {
        try {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            return GLFW.glfwGetMouseButton((long)handle, (int)0) == 1;
        }
        catch (Throwable t) {
            return true;
        }
    }

    private void commitDrag(int to) {
        int from = this.dragIndex;
        this.dragIndex = -1;
        this.dropIndex = -1;
        this.highlightIndex = -1;
        if (to >= 0 && from != to) {
            this.setting.move(this.scrollTop + from, this.scrollTop + to);
            FOElytraLog.info("\u98df\u7269\u4f18\u5148\u7ea7\uff1a\u7b2c %d \u4f4d\u62d6\u5230\u7b2c %d \u4f4d", this.scrollTop + from + 1, this.scrollTop + to + 1);
            this.rememberScroll(this.scrollTop + to);
            this.rebuild();
        } else {
            this.markRows();
        }
    }

    public boolean onMouseClicked(Click click, boolean doubled) {
        if (super.onMouseClicked(click, doubled)) {
            return true;
        }
        int idx = this.rowAt(click.y());
        if (idx >= 0 && !this.overButton(click.x(), click.y(), idx)) {
            this.dragIndex = idx;
            this.dropIndex = idx;
            this.markRows();
            return true;
        }
        return false;
    }

    public boolean onMouseReleased(Click click) {
        if (this.dragIndex >= 0) {
            this.commitDrag(this.rowAt(click.y()));
            return true;
        }
        return super.onMouseReleased(click);
    }

    public boolean onMouseScrolled(double amount) {
        List<Item> all = this.setting.order();
        if (all.size() <= 6) {
            return false;
        }
        int before = this.scrollTop;
        if (amount > 0.0) {
            this.scrollTop = Math.max(0, this.scrollTop - 1);
        } else if (amount < 0.0) {
            this.scrollTop = Math.min(Math.max(0, all.size() - 6), this.scrollTop + 1);
        }
        if (this.scrollTop == before) {
            return false;
        }
        this.rebuild();
        return true;
    }
}

