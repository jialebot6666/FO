package com.fo.addon.gui;

import com.fo.addon.utils.EnchantList;
import com.fo.addon.utils.EnchantLevelMapSetting;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.Setting;
import org.apache.commons.lang3.Strings;

/**
 * 附魔书列表的设置界面（V6.22，仿 Meteor 官方 StatusEffectAmplifierMapSettingScreen 自研）。
 *
 * <p>全部 43 个原版附魔逐行列出：每行 = 附魔名 + 等级编辑框（0 = 不启用，1~最高等级），
 * 顶部搜索框按中文名/ID 过滤。等级 &gt; 0 写入设置映射，改回 0 移除。
 *
 * <p>V6.23：等级是<b>精确命中等级</b>——填 3 只命中 3 级、填 1 只命中 1 级（不再是「≥该等级」）。
 */
public class EnchantLevelMapSettingScreen extends WindowScreen {

    private final Setting<it.unimi.dsi.fastutil.objects.Object2IntMap<String>> setting;

    private WTable table;
    private String filterText = "";

    public EnchantLevelMapSettingScreen(GuiTheme theme, Setting<it.unimi.dsi.fastutil.objects.Object2IntMap<String>> setting) {
        super(theme, "附魔书列表");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        WTextBox filter = add(theme.textBox("")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initTable();
        };

        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        for (EnchantList.Entry entry : EnchantList.VANILLA) {
            if (!Strings.CI.contains(entry.name(), filterText) && !Strings.CI.contains(entry.id(), filterText)) {
                continue;
            }

            table.add(theme.label(entry.name())).expandCellX().widget().tooltip = entry.id();

            int current = setting.get().getInt(entry.id()); // fastutil：缺失键默认 0
            WIntEdit level = theme.intEdit(current, 0, entry.maxLevel(), true);
            level.action = () -> {
                int v = level.get();
                if (v > 0) {
                    setting.get().put(entry.id(), v);
                } else {
                    setting.get().removeInt(entry.id());
                }
                setting.onChanged();
            };

            table.add(level).minWidth(60);
            table.row();
        }
    }
}
