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
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
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

        // V6.27：自动偷取累计统计（只读展示）。
        // V6.29 改样式：用 WSection（就是「高亮」「自动偷取」这些分组用的同一种控件）当"框"——
        //   抬头行 → section 标题，计数行 → 框内内容。这样文字左对齐、有内边距、有框，
        //   并且这个框本身也能折叠，比把几行裸标签堆在设置行右侧整齐得多。
        //   设置名的左侧标签由 Meteor 框架自动加（取 Setting.title），这里只补右侧内容。
        SettingsWidgetFactory.registerCustomFactory(StealStatsSetting.class, theme -> (table, setting) -> {
            AncientStealStats stats = AncientStealStats.get();
            WSection box = table.add(theme.section(stats.displayHeader(), true)).expandCellX().widget();
            for (String line : stats.displayBody()) {
                box.add(theme.label(line));
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
