package com.fo.addon.ancient.core;

/**
 * 古城战利品表的版本变体（决定用哪套表数据掷骰）。
 *
 * <p><b>实测结论</b>（对着 1.20.6 / 1.21.4 / 1.21.5 / 1.21.6 / 1.21.9 / 1.21.11 / 26.1.2 / 26.3
 * 的官方客户端 jar 逐条目比对）——古城表在版本间只有三处差异：
 * <ol>
 *   <li><b>≤ 1.20.6</b>：附魔函数用旧语法（{@code treasure:true}、{@code enchantments:[...]}），
 *       候选附魔池 = 全部附魔；</li>
 *   <li><b>1.21 ~ 1.21.5</b>：附魔函数换成 {@code options}（如 {@code #minecraft:on_random_loot}）；</li>
 *   <li><b>1.21.6</b>：池 0 第 8 条从 {@code saddle}（{@code set_count} 常量 1，<b>不消耗随机数</b>）
 *       改为 {@code leather}（{@code set_count} uniform 1~5，<b>消耗一次随机数</b>）——
 *       一旦抽中该条，它之后的随机数全都会错位；</li>
 *   <li><b>≥ 26.1</b>：少一个 {@code minecraft:name_tag} 条目（池权重 86 → 84）。</li>
 * </ol>
 *
 * <p>选错的表现：箱子位置仍然正确（位置只取决于世界种子、与战利品表无关），但物品判定出错。
 * 连服务器时按<b>服务端原生版本</b>选，不是按你的客户端版本。
 */
public enum GameVersionOption {
    /** 1.20.6 及更早：旧版附魔语法（treasure / enchantments 列表）。 */
    UP_TO_1_20_6("≤ 1.20.6", "fo/ancient/v1206/"),

    /** 1.21 ~ 1.21.5：附魔改成 options；第 8 条仍是 saddle（常量、不消耗随机数）。 */
    V1_21_TO_1_21_5("1.21 ~ 1.21.5", "fo/ancient/v1215/"),

    /**
     * 1.21.6 ~ 1.21.11：第 8 条变成 leather（uniform、消耗随机数）。
     *
     * <p>常量名沿用旧值（原标签「&lt; 26.1」），这样老配置里已选的选项不会失效。
     */
    BELOW_26_1("1.21.6 ~ 1.21.11", "fo/ancient/"),

    /** 26.1 及以后：少 name_tag（池权重 84）。 */
    FROM_26_1("≥ 26.1", "fo/ancient/v26/");

    private final String label;
    private final String lootTablePrefix;

    GameVersionOption(String label, String lootTablePrefix) {
        this.label = label;
        this.lootTablePrefix = lootTablePrefix;
    }

    /** 该变体的战利品表资源根（相对 classpath）。 */
    public String lootTablePrefix() {
        return lootTablePrefix;
    }

    @Override
    public String toString() {
        return label;
    }
}
