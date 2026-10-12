package com.fo.addon;

import com.fo.addon.ancient.core.AncientStealStats;
import com.fo.addon.ancient.core.StealStatsFile;
import com.fo.addon.ancient.modules.FOAncientCitySearch;
import com.fo.addon.gui.EnchantLevelMapSettingScreen;
import com.fo.addon.modules.AutoLog;
import com.fo.addon.modules.AutoMineSand;
import com.fo.addon.modules.AutoMining;
import com.fo.addon.modules.AutoTrash;
import com.fo.addon.modules.AutoTree;
import com.fo.addon.modules.ElytraCollector;
import com.fo.addon.modules.FOKillAura;
import com.fo.addon.utils.EnchantLevelMapSetting;
import com.fo.addon.utils.StealStatsSetting;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.settings.Setting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("FO");

    @Override
    public void onInitialize() {
        LOG.info("Initializing FO Addon");

        // V6.22：附魔书列表（附魔ID→等级映射）的自定义设置控件——
        // 设置页里一个「选择」按钮，点开全附魔配等级界面（公开 API registerCustomFactory）
        SettingsWidgetFactory.registerCustomFactory(EnchantLevelMapSetting.class, theme -> (table, setting) -> {
            // factory 入参是 Setting<?>，这里确定只会收到 EnchantLevelMapSetting（按键就是它的 Class）
            @SuppressWarnings("unchecked")
            Setting<Object2IntMap<String>> cast = (Setting<Object2IntMap<String>>) setting;
            WButton button = table.add(theme.button("选择")).expandCellX().widget();
            button.action = () -> MinecraftClient.getInstance()
                .setScreen(new EnchantLevelMapSettingScreen(theme, cast));
        });

        // V6.27 新增；V6.29/V6.30 反复调样式，V6.31 定稿：
        //   内容用 WTable 排成「名称 | 只读黑框(数值)」两列 —— 框自动对齐；
        //   框用 theme.textBox(文本, 过滤器)，过滤器一律返回 false → 看得见、点不进、改不动
        //   （即「高亮半径 [256]」「种子 [...]」同款原生框，不是自己画的野框）。
        //   ⚠️ 曾经用 WSection 当框，但它的标题走**大号字体**，嵌在设置行里会盖住左侧标签
        //   （实机截图确认"卡成一团"），所以改用 WTable + 只读文本框。
        //   V6.31：框架会在调工厂前往本行加左侧标题标签（取 Setting.title），这里把该行
        //   整行删掉（WTable.removeRow）——「战果」左标签不再显示，内容顶到最左；
        //   原本挂在左标签上的 tooltip（设置 description）转移到抬头「您已使用本插件…」上，
        //   悬停抬头即可查看说明。
        SettingsWidgetFactory.registerCustomFactory(StealStatsSetting.class, theme -> (table, setting) -> {
            AncientStealStats stats = AncientStealStats.get();
            // 一律拒绝输入的过滤器 = 只读（CharFilter.filter(当前文本, 待输入字符)）
            CharFilter readOnly = (text, c) -> false;

            // 框架刚给本行加了左侧标题标签（当前行只有这一个控件），摘下它的 tooltip 后整行删除
            String tooltip = table.getRow(table.rowI()).get(0).widget().tooltip;
            table.removeRow(table.rowI());

            WTable grid = table.add(theme.table()).expandCellX().marginTop(6).widget();
            WLabel header = grid.add(theme.label(stats.displayHeader())).expandCellX().widget();
            header.tooltip = tooltip;
            grid.row();
            for (AncientStealStats.StatRow row : stats.displayRows()) {
                grid.add(theme.label(row.name()));
                grid.add(theme.textBox(row.value(), readOnly)).expandCellX().widget();
                grid.row();
            }
        });

        // V6.27：累计统计要在模块启动前就能被设置页读到，所以加载期先读一次文件
        AncientStealStats.get().replaceWith(StealStatsFile.load());

        // Modules
        Modules.get().add(new ElytraCollector());
        Modules.get().add(new AutoTree());
        Modules.get().add(new AutoTrash());
        Modules.get().add(new AutoLog());
        Modules.get().add(new AutoMineSand());
        Modules.get().add(new AutoMining());
        Modules.get().add(new FOKillAura());
        Modules.get().add(new FOAncientCitySearch());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.fo.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("jialebot6666", "FO");
    }
}
