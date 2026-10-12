package com.fo.addon;

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
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
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
