package com.fo.addon.utils;

import java.util.Set;

/**
 * 补给盒内容指纹（纯逻辑，可单测）。V4.23 新增。
 *
 * 问题：长时间放置的潜影盒方块实体会被服务器清除自定义名称（铁砧改名放置、
 * 原版会持久化，服务器插件清名后 FO 的"名字含 FO补给"识别失效，模块报错停止）。
 * 兜底：INIT_SCAN 打开盒子时顺手读盒内内容，含"钻石/合金铲"的盒子记为
 * 内容指纹候选——名字丢失时按内容识别为补给盒。
 *
 * 为什么用钻石/合金铲做硬性标准（用户拍板）：
 * - 铲子是补给盒的核心功能（FO 自动补铲），玩家会定期补货，是永久常驻物；
 * - 用途单一，私人囤一盒钻石/合金铲的概率远低于囤一盒金萝卜/图腾，误判率最低；
 * - 石铲/木铲不算（硬性标准，防低级误判）。
 */
public final class SupplyFingerprintLogic {

    /** 内容指纹铲子 ID 集合：盒内含其中任一 → 判为补给盒候选 */
    public static final Set<String> FINGERPRINT_SHOVEL_IDS = Set.of(
        "minecraft:diamond_shovel",   // 钻石铲
        "minecraft:netherite_shovel"  // 下界合金铲
    );

    private SupplyFingerprintLogic() {
    }
}
