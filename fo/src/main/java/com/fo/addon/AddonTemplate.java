package com.fo.addon;

import com.fo.addon.modules.AutoLog;
import com.fo.addon.modules.AutoTrash;
import com.fo.addon.modules.AutoTree;
import com.fo.addon.modules.ElytraCollector;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("FO");

    @Override
    public void onInitialize() {
        LOG.info("Initializing FO Addon");

        // Modules
        Modules.get().add(new ElytraCollector());
        Modules.get().add(new AutoTree());
        Modules.get().add(new AutoTrash());
        Modules.get().add(new AutoLog());
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
        return new GithubRepo("jialebot6666", "fo");
    }
}
