package com.fo.addon;

import com.fo.addon.elytra.hud.SpeedHud;
import com.fo.addon.elytra.modules.AutoElytraFlight;
import com.fo.addon.elytra.modules.AutoOminousVault;
import com.fo.addon.elytra.modules.SpeedMeter;
import com.fo.addon.modules.AutoLog;
import com.fo.addon.modules.AutoMineSand;
import com.fo.addon.modules.AutoMining;
import com.fo.addon.modules.AutoTrash;
import com.fo.addon.modules.AutoTree;
import com.fo.addon.modules.ElytraCollector;
import com.fo.addon.modules.FOKillAura;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
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
        Modules.get().add(new AutoMineSand());
        Modules.get().add(new AutoMining());
        Modules.get().add(new FOKillAura());

        // 鞘翅增强套件（移植进 FO 类目，顺序 1 → 2 → 3，不单独开类目）
        Modules.get().add(new AutoElytraFlight());   // 1. FO 自动鞘翅飞行
        Modules.get().add(new AutoOminousVault());   // 2. FO 自动开宝库
        Modules.get().add(new SpeedMeter());         // 3. FO 实时平均速度

        // HUD 元素：FO 实时速度（与上面的模块共享数据，模块关着也照常统计）
        Hud.get().add(SpeedHud.INFO, 10, 10);
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
