package com.fo.addon.utils;

import java.util.List;

/**
 * 原版附魔数据表（V6.21，纯数据不依赖 MC 运行时，可单元测试）。
 *
 * <p>数据来源（AGENTS.md 铁律 4：逐条核实，不凭记忆）：
 * <ul>
 *   <li>清单与最高等级：官方客户端 jar 的 {@code data/minecraft/enchantment/*.json}（max_level），
 *       1.21.11 与 26.1.2 两版逐条一致，共 43 个；</li>
 *   <li>中文名：26.1.2 官方 {@code assets/minecraft/lang/zh_cn.json}（lunge=突进 等）。</li>
 * </ul>
 *
 * <p>若未来 MC 新增附魔，此表需同步更新（新附魔不会有对应设置项，行为等同不启用）。
 */
public final class EnchantList {

    /** 一个附魔：ID（minecraft:xxx）+ 中文名 + 最高等级。 */
    public record Entry(String id, String name, int maxLevel) {}

    private EnchantList() {}

    /** 全部 43 个原版附魔，按 盔甲/工具武器/三叉戟/重锤/弓弩/钓鱼/其他 分组排序。 */
    public static final List<Entry> VANILLA = List.of(
        // ---- 盔甲 / 装备 ----
        new Entry("minecraft:protection", "保护", 4),
        new Entry("minecraft:fire_protection", "火焰保护", 4),
        new Entry("minecraft:feather_falling", "摔落缓冲", 4),
        new Entry("minecraft:blast_protection", "爆炸保护", 4),
        new Entry("minecraft:projectile_protection", "弹射物保护", 4),
        new Entry("minecraft:thorns", "荆棘", 3),
        new Entry("minecraft:respiration", "水下呼吸", 3),
        new Entry("minecraft:depth_strider", "深海探索者", 3),
        new Entry("minecraft:aqua_affinity", "水下速掘", 1),
        new Entry("minecraft:frost_walker", "冰霜行者", 2),
        new Entry("minecraft:soul_speed", "灵魂疾行", 3),
        new Entry("minecraft:swift_sneak", "迅捷潜行", 3),
        new Entry("minecraft:binding_curse", "绑定诅咒", 1),
        // ---- 工具 / 近战武器 ----
        new Entry("minecraft:sharpness", "锋利", 5),
        new Entry("minecraft:smite", "亡灵杀手", 5),
        new Entry("minecraft:bane_of_arthropods", "节肢杀手", 5),
        new Entry("minecraft:knockback", "击退", 2),
        new Entry("minecraft:fire_aspect", "火焰附加", 2),
        new Entry("minecraft:looting", "抢夺", 3),
        new Entry("minecraft:sweeping_edge", "横扫之刃", 3),
        new Entry("minecraft:efficiency", "效率", 5),
        new Entry("minecraft:silk_touch", "精准采集", 1),
        new Entry("minecraft:unbreaking", "耐久", 3),
        new Entry("minecraft:fortune", "时运", 3),
        // ---- 三叉戟 ----
        new Entry("minecraft:loyalty", "忠诚", 3),
        new Entry("minecraft:impaling", "穿刺", 5),
        new Entry("minecraft:riptide", "激流", 3),
        new Entry("minecraft:channeling", "引雷", 1),
        // ---- 重锤（1.21+）----
        new Entry("minecraft:density", "致密", 5),
        new Entry("minecraft:breach", "破甲", 4),
        new Entry("minecraft:wind_burst", "风爆", 3),
        // ---- 弓 / 弩 ----
        new Entry("minecraft:power", "力量", 5),
        new Entry("minecraft:punch", "冲击", 2),
        new Entry("minecraft:flame", "火矢", 1),
        new Entry("minecraft:infinity", "无限", 1),
        new Entry("minecraft:multishot", "多重射击", 1),
        new Entry("minecraft:quick_charge", "快速装填", 3),
        new Entry("minecraft:piercing", "穿透", 4),
        // ---- 钓鱼 ----
        new Entry("minecraft:luck_of_the_sea", "海之眷顾", 3),
        new Entry("minecraft:lure", "饵钓", 3),
        // ---- 长矛（26.x）----
        new Entry("minecraft:lunge", "突进", 3),
        // ---- 其他 ----
        new Entry("minecraft:mending", "经验修补", 1),
        new Entry("minecraft:vanishing_curse", "消失诅咒", 1)
    );
}
