package com.fo.addon.ancient.core;

import com.fo.addon.ancient.core.AncientJigsawSimulator.Piece;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「&le; 1.20.6」与「1.21 ~ 1.21.5」两套战利品表的黄金回归测试。
 *
 * <p>表在版本间只有三处差异（详见 {@link GameVersionOption}）：&le;1.20.6 的附魔旧语法
 * （treasure / enchantments 列表）、1.21.6 起第 8 条由 saddle（常量、不耗随机数）变
 * leather（uniform、耗一次随机数）、&ge;26.1 少一个 name_tag。本测试锁住前两套表的掷骰结果，
 * 防止改动掷骰链路时无声改变判定。
 *
 * <p>下面是生产模式（MC_NATIVE）的期望值；改错版本表或改坏掷骰链路都会让它失败。
 */
class AncientLootEvaluatorLegacyTest {

    private static final long SEED = -7346913998703726680L;

    // ==================== 表结构（版本差异的锚点，全部对着官方客户端 jar 实测） ====================

    /** 四个变体的条目数 / 池 0 权重和 / 第 8 条目 / 附魔语法关键字。 */
    @Test
    void 四个变体的表结构与官方实测一致() {
        // ≤1.20.6：旧语法（treasure + enchantments 列表）
        assertVariant(GameVersionOption.UP_TO_1_20_6, 28, 86, "minecraft:saddle", true, true, false);
        // 1.21 ~ 1.21.5：附魔改 options；第 8 条仍是 saddle
        assertVariant(GameVersionOption.V1_21_TO_1_21_5, 28, 86, "minecraft:saddle", false, false, true);
        // 1.21.6 ~ 1.21.11：第 8 条变 leather
        assertVariant(GameVersionOption.BELOW_26_1, 28, 86, "minecraft:leather", false, false, true);
        // ≥26.1：少 name_tag
        assertVariant(GameVersionOption.FROM_26_1, 27, 84, "minecraft:music_disc_13", false, false, true);
    }

    /** 第 8 条的 count 类型：saddle 是常量（不消耗随机数）、leather 是 uniform（消耗一次）。 */
    @Test
    void 第8条的count类型差异() {
        assertTrue(countOf(GameVersionOption.UP_TO_1_20_6, 8) instanceof Number, "≤1.20.6 第 8 条应是常量");
        assertTrue(countOf(GameVersionOption.V1_21_TO_1_21_5, 8) instanceof Number, "1.21~1.21.5 第 8 条应是常量");
        assertTrue(countOf(GameVersionOption.BELOW_26_1, 8) instanceof Map, "1.21.6+ 第 8 条应是 uniform");
    }

    /** 冰窖表在四个变体完全一致（版本差异只有古城表这一张）。 */
    @Test
    void 冰窖表在四个变体一致() {
        String base = rawText(GameVersionOption.BELOW_26_1, "loot_table/chests/ancient_city_ice_box.json");
        for (GameVersionOption v : GameVersionOption.values()) {
            assertEquals(base, rawText(v, "loot_table/chests/ancient_city_ice_box.json"), v + " 的冰窖表");
        }
    }

    /** 旧语法（enchantments 列表）也要能产出迅捷潜行书——走的是 enchant_randomly 的旧字段。 */
    @Test
    void 旧语法表能产出迅捷潜行书() {
        int hits = 0;
        for (String row : chestsOf(GameVersionOption.UP_TO_1_20_6, 773, -622)) {
            if (row.contains("|迅捷3|") || row.contains("|两者|")) hits++;
        }
        assertTrue(hits > 0, "≤1.20.6 表应能产出迅捷潜行书（旧 enchantments 列表路径）");
    }

    // ==================== 黄金数据 ====================

    /** 区块 779,-595（18 箱），≤1.20.6 的表。 */
    private static final List<String> A1206_CITY_779_595 = List.of(
            "12383,-48,-9504|-|minecraft:book:4;minecraft:candle:4;minecraft:coal:11;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:sculk_sensor:1",
            "12390,-47,-9496|-|minecraft:coal:9;minecraft:disc_fragment_5:2;minecraft:echo_shard:1;minecraft:experience_bottle:3;minecraft:sculk_catalyst:2",
            "12390,-47,-9504|-|minecraft:bone:10;minecraft:coal:9;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:music_disc_cat:1;minecraft:soul_torch:4",
            "12397,-35,-9538|附魔金|minecraft:amethyst_shard:11;minecraft:echo_shard:2;minecraft:enchanted_golden_apple:1;minecraft:music_disc_cat:1;minecraft:name_tag:2",
            "12398,-35,-9556|-|minecraft:bone:3;minecraft:book:11;minecraft:coal:6;minecraft:echo_shard:1;minecraft:experience_bottle:2;minecraft:iron_leggings:1;minecraft:sculk_sensor:2",
            "12410,-44,-9603|-|minecraft:amethyst_shard:3;minecraft:candle:2;minecraft:coal:11;minecraft:disc_fragment_5:2;minecraft:sculk:5",
            "12416,-50,-9599|附魔金|minecraft:amethyst_shard:15;minecraft:bone:1;minecraft:diamond_leggings:1;minecraft:echo_shard:4;minecraft:enchanted_golden_apple:2;minecraft:experience_bottle:1;minecraft:music_disc_13:1;minecraft:music_disc_otherside:1;minecraft:name_tag:1",
            "12438,-49,-9531|-|minecraft:bone:14;minecraft:book:7;minecraft:coal:25;minecraft:diamond_hoe:1;minecraft:echo_shard:4;minecraft:sculk_catalyst:1;minecraft:soul_torch:12",
            "12450,-50,-9596|-|minecraft:bone:6;minecraft:book:8;minecraft:coal:7;minecraft:enchanted_book:1;minecraft:soul_torch:14",
            "12456,-49,-9442|-|minecraft:amethyst_shard:3;minecraft:book:10;minecraft:disc_fragment_5:6;minecraft:music_disc_13:1;minecraft:sculk_catalyst:2",
            "12457,-49,-9564|-|minecraft:book:8;minecraft:compass:1;minecraft:echo_shard:5;minecraft:enchanted_book:2;minecraft:iron_leggings:1;minecraft:sculk:10;minecraft:sculk_sensor:4",
            "12489,-37,-9619|附魔金|minecraft:candle:2;minecraft:coal:23;minecraft:disc_fragment_5:4;minecraft:enchanted_golden_apple:1;minecraft:glow_berries:15;minecraft:lead:1;minecraft:sculk:7",
            "12521,-35,-9474|-|minecraft:amethyst_shard:8;minecraft:coal:31;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:glow_berries:9;minecraft:iron_leggings:1;minecraft:music_disc_cat:1",
            "12529,-47,-9565|附魔金|minecraft:coal:6;minecraft:diamond_horse_armor:1;minecraft:enchanted_golden_apple:2;minecraft:glow_berries:15;minecraft:lead:1",
            "12529,-47,-9573|-|minecraft:amethyst_shard:5;minecraft:coal:10;minecraft:glow_berries:9;minecraft:music_disc_13:1;minecraft:sculk_sensor:1",
            "12554,-49,-9583|-|minecraft:candle:2;minecraft:coal:35;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1",
            "12555,-50,-9453|-|minecraft:bone:16;minecraft:book:5;minecraft:enchanted_book:1;minecraft:music_disc_otherside:1;minecraft:saddle:1",
            "12557,-48,-9553|-|minecraft:coal:10;minecraft:disc_fragment_5:3;minecraft:echo_shard:4;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:potion:2;minecraft:sculk_catalyst:2");

    /** 区块 773,-622（26 箱），≤1.20.6 的表。 */
    private static final List<String> A1206_CITY_773_622 = List.of(
            "12269,-49,-9945|迅捷3|minecraft:bone:9;minecraft:coal:11;minecraft:diamond_hoe:1;minecraft:echo_shard:2;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:saddle:1;minecraft:soul_torch:7",
            "12282,-50,-9977|-|minecraft:amethyst_shard:7;minecraft:candle:1;minecraft:glow_berries:5;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk:5;minecraft:sculk_sensor:2;minecraft:soul_torch:12",
            "12287,-50,-9941|迅捷3|minecraft:book:10;minecraft:coal:8;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:potion:2;minecraft:soul_torch:15",
            "12293,-44,-9937|-|minecraft:bone:8;minecraft:coal:11;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:name_tag:1;minecraft:potion:3;minecraft:silence_armor_trim_smithing_template:1",
            "12301,-44,-10054|-|minecraft:book:8;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:experience_bottle:2;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:2",
            "12306,-50,-9944|迅捷3|minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:potion:3;minecraft:sculk_sensor:1",
            "12308,-49,-10011|-|minecraft:candle:4;minecraft:compass:1;minecraft:diamond_horse_armor:2;minecraft:echo_shard:3;minecraft:music_disc_13:2;minecraft:potion:3;minecraft:sculk_catalyst:1;minecraft:soul_torch:4",
            "12314,-50,-9918|-|minecraft:bone:20;minecraft:book:7;minecraft:glow_berries:14;minecraft:saddle:1",
            "12330,-49,-9893|-|minecraft:book:11;minecraft:glow_berries:3;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk_sensor:2",
            "12347,-47,-10045|附魔金|minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:glow_berries:15;minecraft:lead:1;minecraft:soul_torch:4",
            "12347,-47,-10053|-|minecraft:bone:37;minecraft:book:8;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:glow_berries:6;minecraft:music_disc_cat:1;minecraft:soul_torch:3",
            "12350,-35,-9868|-|minecraft:coal:8;minecraft:disc_fragment_5:2;minecraft:name_tag:1;minecraft:potion:1;minecraft:sculk_sensor:2",
            "12356,-50,-9850|附魔金|minecraft:amethyst_shard:3;minecraft:bone:7;minecraft:book:8;minecraft:coal:7;minecraft:enchanted_golden_apple:1;minecraft:iron_leggings:1;minecraft:sculk:11",
            "12384,-48,-9872|-|minecraft:book:7;minecraft:coal:9;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:2;minecraft:music_disc_13:1;minecraft:potion:2",
            "12389,-37,-9883|附魔金|minecraft:book:3;minecraft:compass:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:2;minecraft:glow_berries:7;minecraft:iron_leggings:1;minecraft:music_disc_cat:1",
            "12397,-44,-10014|-|minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:1;minecraft:echo_shard:3;minecraft:iron_leggings:1;minecraft:music_disc_cat:1;minecraft:name_tag:1",
            "12411,-49,-9944|-|minecraft:coal:7;minecraft:glow_berries:9;minecraft:music_disc_otherside:1;minecraft:potion:5;minecraft:sculk_sensor:2;minecraft:soul_torch:20",
            "12412,-50,-9969|-|minecraft:amethyst_shard:11;minecraft:book:8;minecraft:echo_shard:1;minecraft:experience_bottle:3;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:name_tag:1",
            "12415,-50,-10003|迅捷3|minecraft:book:9;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:music_disc_13:1",
            "12422,-47,-9933|-|minecraft:book:13;minecraft:coal:10;minecraft:diamond_hoe:1;minecraft:enchanted_book:1;minecraft:lead:1;minecraft:music_disc_13:1;minecraft:sculk_catalyst:2",
            "12422,-47,-9941|-|minecraft:diamond_hoe:1;minecraft:disc_fragment_5:1;minecraft:experience_bottle:3;minecraft:music_disc_cat:1;minecraft:potion:2",
            "12432,-50,-10003|附魔金|minecraft:coal:21;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:sculk_sensor:4;minecraft:soul_torch:10",
            "12432,-50,-9977|-|minecraft:book:5;minecraft:coal:19;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:glow_berries:15;minecraft:music_disc_cat:1;minecraft:name_tag:1;minecraft:ward_armor_trim_smithing_template:1",
            "12447,-48,-10005|-|minecraft:coal:8;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:sculk:6;minecraft:sculk_sensor:1",
            "12447,-49,-9944|-|minecraft:coal:7;minecraft:disc_fragment_5:1;minecraft:enchanted_book:1;minecraft:glow_berries:1;minecraft:name_tag:1;minecraft:sculk:8;minecraft:sculk_sensor:2",
            "12449,-49,-9968|-|minecraft:coal:11;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:glow_berries:3;minecraft:iron_leggings:1;minecraft:soul_torch:14");

    /** 区块 744,-598（28 箱），≤1.20.6 的表。 */
    private static final List<String> A1206_CITY_744_598 = List.of(
            "11823,-49,-9552|附魔金|minecraft:candle:1;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:2;minecraft:enchanted_golden_apple:2;minecraft:iron_leggings:1;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk_catalyst:1",
            "11827,-49,-9609|-|minecraft:amethyst_shard:8;minecraft:book:8;minecraft:coal:11;minecraft:enchanted_book:1;minecraft:music_disc_otherside:1;minecraft:sculk:5",
            "11839,-49,-9478|-|minecraft:bone:13;minecraft:echo_shard:4;minecraft:enchanted_book:3;minecraft:music_disc_13:1;minecraft:music_disc_cat:2;minecraft:potion:1",
            "11841,-49,-9502|-|minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:saddle:1;minecraft:sculk:8;minecraft:sculk_sensor:2",
            "11859,-49,-9552|-|minecraft:coal:15;minecraft:echo_shard:3;minecraft:enchanted_book:1;minecraft:music_disc_cat:1;minecraft:sculk_catalyst:2",
            "11861,-48,-9475|-|minecraft:amethyst_shard:23;minecraft:echo_shard:2;minecraft:iron_leggings:2",
            "11873,-50,-9474|-|minecraft:coal:12;minecraft:diamond_horse_armor:1;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:sculk_sensor:3",
            "11876,-50,-9513|-|minecraft:baked_potato:5;minecraft:golden_carrot:2;minecraft:packed_ice:10;minecraft:snowball:24;minecraft:suspicious_stew:5",
            "11879,-44,-9470|-|minecraft:amethyst_shard:5;minecraft:coal:6;minecraft:iron_leggings:1;minecraft:name_tag:1;minecraft:sculk:10;minecraft:sculk_catalyst:1",
            "11883,-37,-9637|附魔金|minecraft:coal:11;minecraft:disc_fragment_5:4;minecraft:enchanted_golden_apple:1;minecraft:experience_bottle:1;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:sculk:10;minecraft:sculk_catalyst:1",
            "11883,-44,-9649|-|minecraft:coal:13;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:music_disc_cat:1;minecraft:potion:3;minecraft:saddle:1;minecraft:sculk:5",
            "11887,-50,-9655|附魔金|minecraft:book:9;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:echo_shard:3;minecraft:enchanted_golden_apple:1;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:sculk_sensor:3;minecraft:soul_torch:15",
            "11890,-50,-9670|-|minecraft:disc_fragment_5:2;minecraft:echo_shard:3;minecraft:saddle:2;minecraft:sculk:6;minecraft:silence_armor_trim_smithing_template:1",
            "11891,-49,-9618|-|minecraft:bone:4;minecraft:book:13;minecraft:coal:22;minecraft:echo_shard:4;minecraft:enchanted_book:2;minecraft:sculk_sensor:3",
            "11915,-49,-9652|-|minecraft:bone:11;minecraft:candle:2;minecraft:echo_shard:2;minecraft:iron_leggings:2;minecraft:lead:1;minecraft:potion:3",
            "11918,-47,-9609|-|minecraft:bone:3;minecraft:book:4;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:experience_bottle:1;minecraft:soul_torch:8",
            "11923,-37,-9633|附魔金|minecraft:bone:7;minecraft:book:3;minecraft:candle:2;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:name_tag:1;minecraft:sculk:4",
            "11926,-47,-9609|-|minecraft:amethyst_shard:9;minecraft:candle:2;minecraft:coal:11;minecraft:echo_shard:3;minecraft:lead:1;minecraft:sculk_sensor:1",
            "11931,-49,-9518|-|minecraft:bone:8;minecraft:coal:6;minecraft:compass:1;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:iron_leggings:1;minecraft:sculk:9",
            "11944,-48,-9578|-|minecraft:coal:14;minecraft:disc_fragment_5:3;minecraft:echo_shard:3;minecraft:name_tag:1;minecraft:potion:3;minecraft:ward_armor_trim_smithing_template:1",
            "11948,-50,-9550|-|minecraft:coal:15;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:enchanted_book:1;minecraft:glow_berries:11;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:music_disc_cat:1",
            "11951,-50,-9507|-|minecraft:baked_potato:5;minecraft:packed_ice:3;minecraft:snowball:5;minecraft:suspicious_stew:5",
            "11955,-48,-9475|-|minecraft:bone:12;minecraft:candle:4;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:potion:3;minecraft:sculk_sensor:3;minecraft:soul_torch:20",
            "11965,-50,-9580|-|minecraft:packed_ice:3;minecraft:snowball:21",
            "11968,-49,-9518|-|minecraft:candle:1;minecraft:enchanted_book:2;minecraft:potion:1;minecraft:sculk:8;minecraft:sculk_sensor:3",
            "11983,-49,-9551|-|minecraft:bone:8;minecraft:disc_fragment_5:3;minecraft:name_tag:4;minecraft:saddle:1;minecraft:sculk_sensor:1;minecraft:soul_torch:3",
            "11984,-50,-9576|-|minecraft:amethyst_shard:13;minecraft:candle:1;minecraft:potion:5;minecraft:soul_torch:10",
            "12004,-37,-9583|-|minecraft:glow_berries:11;minecraft:name_tag:1;minecraft:sculk:4;minecraft:sculk_sensor:6;minecraft:soul_torch:8");

    /** 区块 777,-576（21 箱），≤1.20.6 的表。 */
    private static final List<String> A1206_CITY_777_576 = List.of(
            "12342,-47,-9236|-|minecraft:bone:22;minecraft:compass:1;minecraft:echo_shard:3;minecraft:enchanted_book:2;minecraft:experience_bottle:1;minecraft:name_tag:1",
            "12342,-47,-9244|迅捷3|minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:name_tag:1;minecraft:ward_armor_trim_smithing_template:1",
            "12371,-50,-9234|-|minecraft:coal:52;minecraft:diamond_leggings:1;minecraft:echo_shard:3;minecraft:experience_bottle:1;minecraft:potion:3;minecraft:soul_torch:10",
            "12383,-37,-9274|-|minecraft:amethyst_shard:6;minecraft:coal:31;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:potion:1;minecraft:soul_torch:8",
            "12388,-50,-9234|-|minecraft:enchanted_book:1;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:3;minecraft:soul_torch:15",
            "12392,-48,-9206|-|minecraft:amethyst_shard:13;minecraft:book:5;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:glow_berries:12;minecraft:sculk_sensor:4;minecraft:soul_torch:11",
            "12411,-47,-9309|-|minecraft:book:5;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:music_disc_cat:1;minecraft:name_tag:1;minecraft:potion:2;minecraft:saddle:1;minecraft:sculk_sensor:1",
            "12411,-47,-9317|-|minecraft:bone:7;minecraft:coal:19;minecraft:disc_fragment_5:2;minecraft:lead:1",
            "12451,-49,-9157|-|minecraft:amethyst_shard:13;minecraft:book:10;minecraft:coal:7;minecraft:diamond_hoe:1;minecraft:experience_bottle:2;minecraft:soul_torch:9;minecraft:ward_armor_trim_smithing_template:1",
            "12459,-49,-9282|-|minecraft:amethyst_shard:4;minecraft:bone:18;minecraft:book:5;minecraft:name_tag:1;minecraft:sculk:6",
            "12467,-47,-9309|-|minecraft:diamond_hoe:1;minecraft:disc_fragment_5:1;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:name_tag:1",
            "12467,-47,-9317|-|minecraft:amethyst_shard:1;minecraft:bone:11;minecraft:book:10;minecraft:diamond_hoe:1;minecraft:iron_leggings:1;minecraft:potion:3;minecraft:sculk_sensor:1;minecraft:soul_torch:25",
            "12474,-50,-9204|-|minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:enchanted_book:4;minecraft:experience_bottle:3;minecraft:music_disc_13:1",
            "12478,-50,-9307|附魔金|minecraft:bone:2;minecraft:enchanted_book:2;minecraft:enchanted_golden_apple:2;minecraft:music_disc_13:1",
            "12480,-44,-9200|-|minecraft:amethyst_shard:17;minecraft:bone:15;minecraft:coal:7;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:experience_bottle:4",
            "12485,-50,-9147|迅捷3|minecraft:coal:12;minecraft:compass:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:experience_bottle:2;minecraft:lead:1",
            "12496,-50,-9281|-|minecraft:candle:3;minecraft:coal:10;minecraft:compass:1;minecraft:echo_shard:2;minecraft:enchanted_book:3;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:name_tag:1",
            "12497,-49,-9306|-|minecraft:amethyst_shard:10;minecraft:bone:12;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:echo_shard:1;minecraft:iron_leggings:1;minecraft:lead:1",
            "12525,-49,-9256|-|minecraft:book:6;minecraft:candle:5;minecraft:echo_shard:2;minecraft:enchanted_book:1;minecraft:saddle:1",
            "12532,-35,-9183|-|minecraft:amethyst_shard:1;minecraft:book:5;minecraft:candle:1;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:2;minecraft:saddle:1",
            "12533,-50,-9233|-|minecraft:bone:17;minecraft:book:20;minecraft:coal:13;minecraft:diamond_hoe:1;minecraft:echo_shard:2;minecraft:enchanted_book:1");

    /** 区块 750,-621（20 箱），≤1.20.6 的表。 */
    private static final List<String> A1206_CITY_750_621 = List.of(
            "11900,-37,-9955|-|minecraft:book:6;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:iron_leggings:1;minecraft:soul_torch:11",
            "11909,-47,-9912|-|minecraft:amethyst_shard:15;minecraft:coal:14;minecraft:compass:1;minecraft:disc_fragment_5:2;minecraft:saddle:1",
            "11909,-47,-9920|-|minecraft:amethyst_shard:7;minecraft:bone:5;minecraft:coal:14;minecraft:enchanted_book:1;minecraft:iron_leggings:2;minecraft:soul_torch:9",
            "11938,-48,-9920|迅捷3|minecraft:book:9;minecraft:enchanted_book:2;minecraft:iron_leggings:2;minecraft:lead:1;minecraft:name_tag:1;minecraft:sculk:5",
            "11958,-50,-9916|-|minecraft:bone:13;minecraft:book:3;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:music_disc_cat:2;minecraft:music_disc_otherside:1;minecraft:potion:3;minecraft:soul_torch:6",
            "11962,-48,-9870|-|minecraft:amethyst_shard:15;minecraft:coal:6;minecraft:echo_shard:2;minecraft:soul_torch:15;minecraft:ward_armor_trim_smithing_template:1",
            "11964,-50,-9839|-|minecraft:disc_fragment_5:1;minecraft:glow_berries:15;minecraft:saddle:1;minecraft:sculk:6;minecraft:soul_torch:5",
            "11970,-44,-9835|-|minecraft:book:8;minecraft:coal:8;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:sculk:4;minecraft:ward_armor_trim_smithing_template:1",
            "11989,-48,-9888|附魔金|minecraft:disc_fragment_5:3;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:music_disc_cat:1;minecraft:name_tag:1;minecraft:potion:7",
            "11991,-50,-9874|-|minecraft:amethyst_shard:9;minecraft:candle:4;minecraft:coal:7;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:sculk_sensor:1;minecraft:soul_torch:2",
            "11993,-49,-10034|-|minecraft:amethyst_shard:10;minecraft:book:8;minecraft:candle:4;minecraft:coal:12;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:glow_berries:14;minecraft:music_disc_13:1",
            "12018,-50,-9998|-|minecraft:bone:10;minecraft:disc_fragment_5:1;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:sculk_catalyst:3;minecraft:ward_armor_trim_smithing_template:1",
            "12024,-37,-9872|-|minecraft:amethyst_shard:9;minecraft:book:4;minecraft:candle:1;minecraft:coal:27;minecraft:potion:2",
            "12024,-50,-9842|-|minecraft:bone:8;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:enchanted_book:2;minecraft:experience_bottle:1",
            "12037,-50,-9839|-|minecraft:bone:6;minecraft:book:7;minecraft:coal:22;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:glow_berries:10;minecraft:music_disc_cat:1;minecraft:potion:2",
            "12043,-44,-9835|-|minecraft:amethyst_shard:10;minecraft:coal:15;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:5;minecraft:music_disc_13:1;minecraft:sculk:9",
            "12055,-47,-9894|-|minecraft:book:4;minecraft:experience_bottle:3;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:music_disc_13:1;minecraft:music_disc_otherside:1;minecraft:sculk_sensor:2",
            "12062,-44,-9999|-|minecraft:book:5;minecraft:coal:6;minecraft:diamond_leggings:1;minecraft:potion:2;minecraft:saddle:1;minecraft:sculk_catalyst:2",
            "12063,-47,-9894|-|minecraft:book:7;minecraft:candle:2;minecraft:coal:14;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:sculk:9;minecraft:sculk_catalyst:1;minecraft:soul_torch:16",
            "12098,-50,-9953|-|minecraft:candle:4;minecraft:diamond_horse_armor:1;minecraft:experience_bottle:2;minecraft:potion:1;minecraft:soul_torch:1");

    /** 区块 779,-595（18 箱），1.21 ~ 1.21.5 的表。 */
    private static final List<String> A1215_CITY_779_595 = List.of(
            "12383,-48,-9504|-|minecraft:book:14;minecraft:coal:11;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:sculk_sensor:1;minecraft:soul_torch:3",
            "12390,-47,-9496|-|minecraft:coal:9;minecraft:disc_fragment_5:2;minecraft:echo_shard:1;minecraft:experience_bottle:3;minecraft:sculk_catalyst:2",
            "12390,-47,-9504|-|minecraft:bone:10;minecraft:coal:9;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:music_disc_cat:1;minecraft:soul_torch:4",
            "12397,-35,-9538|附魔金|minecraft:amethyst_shard:11;minecraft:echo_shard:2;minecraft:enchanted_golden_apple:1;minecraft:music_disc_cat:1;minecraft:name_tag:2",
            "12398,-35,-9556|-|minecraft:bone:3;minecraft:book:11;minecraft:coal:6;minecraft:echo_shard:1;minecraft:experience_bottle:2;minecraft:iron_leggings:1;minecraft:sculk_sensor:2",
            "12410,-44,-9603|-|minecraft:amethyst_shard:3;minecraft:candle:2;minecraft:coal:11;minecraft:disc_fragment_5:2;minecraft:sculk:5",
            "12416,-50,-9599|附魔金|minecraft:amethyst_shard:15;minecraft:bone:1;minecraft:diamond_leggings:1;minecraft:echo_shard:4;minecraft:enchanted_golden_apple:2;minecraft:experience_bottle:1;minecraft:music_disc_13:1;minecraft:music_disc_otherside:1;minecraft:name_tag:1",
            "12438,-49,-9531|-|minecraft:bone:14;minecraft:book:7;minecraft:coal:25;minecraft:diamond_hoe:1;minecraft:echo_shard:4;minecraft:sculk_catalyst:1;minecraft:soul_torch:12",
            "12450,-50,-9596|-|minecraft:bone:6;minecraft:book:8;minecraft:coal:7;minecraft:enchanted_book:1;minecraft:soul_torch:14",
            "12456,-49,-9442|-|minecraft:amethyst_shard:3;minecraft:book:10;minecraft:disc_fragment_5:6;minecraft:music_disc_13:1;minecraft:sculk_catalyst:2",
            "12457,-49,-9564|-|minecraft:book:8;minecraft:compass:1;minecraft:echo_shard:5;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:saddle:1;minecraft:sculk:10;minecraft:sculk_sensor:4",
            "12489,-37,-9619|附魔金|minecraft:candle:2;minecraft:coal:23;minecraft:disc_fragment_5:4;minecraft:enchanted_golden_apple:1;minecraft:glow_berries:15;minecraft:lead:1;minecraft:sculk:7",
            "12521,-35,-9474|-|minecraft:amethyst_shard:8;minecraft:coal:31;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:glow_berries:9;minecraft:iron_leggings:1;minecraft:music_disc_cat:1",
            "12529,-47,-9565|附魔金|minecraft:coal:6;minecraft:diamond_horse_armor:1;minecraft:enchanted_golden_apple:2;minecraft:glow_berries:15;minecraft:lead:1",
            "12529,-47,-9573|-|minecraft:amethyst_shard:5;minecraft:coal:10;minecraft:glow_berries:9;minecraft:music_disc_13:1;minecraft:sculk_sensor:1",
            "12554,-49,-9583|-|minecraft:candle:2;minecraft:coal:35;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1",
            "12555,-50,-9453|-|minecraft:bone:28;minecraft:book:5;minecraft:enchanted_book:1;minecraft:music_disc_otherside:1",
            "12557,-48,-9553|-|minecraft:coal:10;minecraft:disc_fragment_5:3;minecraft:echo_shard:4;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:potion:2;minecraft:sculk_catalyst:2");

    /** 区块 773,-622（26 箱），1.21 ~ 1.21.5 的表。 */
    private static final List<String> A1215_CITY_773_622 = List.of(
            "12269,-49,-9945|迅捷3|minecraft:bone:9;minecraft:coal:11;minecraft:diamond_hoe:1;minecraft:echo_shard:2;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:saddle:1;minecraft:soul_torch:7",
            "12282,-50,-9977|-|minecraft:amethyst_shard:7;minecraft:candle:1;minecraft:glow_berries:5;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk:5;minecraft:sculk_sensor:2;minecraft:soul_torch:12",
            "12287,-50,-9941|迅捷3|minecraft:book:10;minecraft:coal:8;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:potion:2;minecraft:soul_torch:15",
            "12293,-44,-9937|-|minecraft:bone:8;minecraft:coal:11;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:name_tag:1;minecraft:potion:3;minecraft:silence_armor_trim_smithing_template:1",
            "12301,-44,-10054|-|minecraft:book:8;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:experience_bottle:2;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:2",
            "12306,-50,-9944|迅捷3|minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:potion:3;minecraft:sculk_sensor:1",
            "12308,-49,-10011|-|minecraft:candle:4;minecraft:compass:1;minecraft:diamond_horse_armor:2;minecraft:echo_shard:3;minecraft:music_disc_13:2;minecraft:potion:3;minecraft:sculk_catalyst:1;minecraft:soul_torch:4",
            "12314,-50,-9918|-|minecraft:bone:20;minecraft:book:7;minecraft:glow_berries:14;minecraft:saddle:1",
            "12330,-49,-9893|-|minecraft:book:11;minecraft:glow_berries:3;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk_sensor:2",
            "12347,-47,-10045|附魔金|minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:glow_berries:15;minecraft:lead:1;minecraft:soul_torch:4",
            "12347,-47,-10053|-|minecraft:bone:37;minecraft:book:8;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:glow_berries:6;minecraft:music_disc_cat:1;minecraft:soul_torch:3",
            "12350,-35,-9868|-|minecraft:coal:8;minecraft:disc_fragment_5:2;minecraft:name_tag:1;minecraft:potion:1;minecraft:sculk_sensor:2",
            "12356,-50,-9850|附魔金|minecraft:amethyst_shard:3;minecraft:bone:7;minecraft:book:8;minecraft:coal:7;minecraft:enchanted_golden_apple:1;minecraft:iron_leggings:1;minecraft:sculk:11",
            "12384,-48,-9872|-|minecraft:book:7;minecraft:coal:9;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:2;minecraft:music_disc_13:1;minecraft:potion:2",
            "12389,-37,-9883|附魔金|minecraft:book:3;minecraft:compass:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:2;minecraft:glow_berries:7;minecraft:iron_leggings:1;minecraft:music_disc_cat:1",
            "12397,-44,-10014|-|minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:1;minecraft:echo_shard:3;minecraft:iron_leggings:1;minecraft:music_disc_cat:1;minecraft:name_tag:1",
            "12411,-49,-9944|-|minecraft:coal:7;minecraft:glow_berries:9;minecraft:music_disc_otherside:1;minecraft:potion:5;minecraft:sculk_sensor:2;minecraft:soul_torch:20",
            "12412,-50,-9969|-|minecraft:amethyst_shard:11;minecraft:book:8;minecraft:echo_shard:1;minecraft:experience_bottle:3;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:name_tag:1",
            "12415,-50,-10003|迅捷3|minecraft:book:9;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:music_disc_13:1",
            "12422,-47,-9933|-|minecraft:book:13;minecraft:coal:10;minecraft:diamond_hoe:1;minecraft:enchanted_book:1;minecraft:lead:1;minecraft:music_disc_13:1;minecraft:sculk_catalyst:2",
            "12422,-47,-9941|-|minecraft:diamond_hoe:1;minecraft:disc_fragment_5:1;minecraft:experience_bottle:3;minecraft:music_disc_cat:1;minecraft:potion:2",
            "12432,-50,-10003|附魔金|minecraft:coal:21;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:sculk_sensor:4;minecraft:soul_torch:10",
            "12432,-50,-9977|-|minecraft:book:5;minecraft:coal:19;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:glow_berries:15;minecraft:music_disc_cat:1;minecraft:name_tag:1;minecraft:ward_armor_trim_smithing_template:1",
            "12447,-48,-10005|-|minecraft:coal:8;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:sculk:6;minecraft:sculk_sensor:1",
            "12447,-49,-9944|-|minecraft:coal:7;minecraft:disc_fragment_5:1;minecraft:enchanted_book:1;minecraft:glow_berries:1;minecraft:name_tag:1;minecraft:sculk:8;minecraft:sculk_sensor:2",
            "12449,-49,-9968|-|minecraft:coal:11;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:glow_berries:3;minecraft:iron_leggings:1;minecraft:soul_torch:14");

    /** 区块 744,-598（28 箱），1.21 ~ 1.21.5 的表。 */
    private static final List<String> A1215_CITY_744_598 = List.of(
            "11823,-49,-9552|附魔金|minecraft:candle:1;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:2;minecraft:enchanted_golden_apple:2;minecraft:iron_leggings:1;minecraft:music_disc_13:1;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk_catalyst:1",
            "11827,-49,-9609|-|minecraft:amethyst_shard:8;minecraft:bone:5;minecraft:coal:11;minecraft:enchanted_book:2;minecraft:sculk:5",
            "11839,-49,-9478|-|minecraft:bone:13;minecraft:echo_shard:4;minecraft:enchanted_book:3;minecraft:music_disc_13:1;minecraft:music_disc_cat:2;minecraft:potion:1",
            "11841,-49,-9502|-|minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:saddle:1;minecraft:sculk:8;minecraft:sculk_sensor:2",
            "11859,-49,-9552|-|minecraft:coal:10;minecraft:enchanted_book:1;minecraft:experience_bottle:2;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:soul_torch:10",
            "11861,-48,-9475|-|minecraft:amethyst_shard:23;minecraft:echo_shard:2;minecraft:iron_leggings:2",
            "11873,-50,-9474|-|minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:sculk_catalyst:1;minecraft:sculk_sensor:3",
            "11876,-50,-9513|-|minecraft:baked_potato:5;minecraft:golden_carrot:2;minecraft:packed_ice:10;minecraft:snowball:24;minecraft:suspicious_stew:5",
            "11879,-44,-9470|-|minecraft:amethyst_shard:5;minecraft:coal:6;minecraft:iron_leggings:1;minecraft:name_tag:1;minecraft:sculk:10;minecraft:sculk_catalyst:1",
            "11883,-37,-9637|附魔金|minecraft:coal:11;minecraft:disc_fragment_5:4;minecraft:enchanted_golden_apple:1;minecraft:experience_bottle:1;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:sculk:10;minecraft:sculk_catalyst:1",
            "11883,-44,-9649|-|minecraft:coal:13;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:music_disc_cat:1;minecraft:potion:3;minecraft:saddle:1;minecraft:sculk:5",
            "11887,-50,-9655|附魔金|minecraft:book:9;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:echo_shard:3;minecraft:enchanted_golden_apple:1;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:sculk_sensor:3;minecraft:soul_torch:15",
            "11890,-50,-9670|-|minecraft:disc_fragment_5:2;minecraft:echo_shard:3;minecraft:saddle:2;minecraft:sculk:6;minecraft:silence_armor_trim_smithing_template:1",
            "11891,-49,-9618|-|minecraft:bone:4;minecraft:book:13;minecraft:coal:22;minecraft:echo_shard:4;minecraft:enchanted_book:2;minecraft:sculk_sensor:3",
            "11915,-49,-9652|-|minecraft:bone:11;minecraft:candle:2;minecraft:echo_shard:2;minecraft:iron_leggings:2;minecraft:lead:1;minecraft:potion:3",
            "11918,-47,-9609|-|minecraft:bone:3;minecraft:book:4;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:experience_bottle:1;minecraft:soul_torch:8",
            "11923,-37,-9633|附魔金|minecraft:bone:7;minecraft:book:3;minecraft:candle:2;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:name_tag:1;minecraft:sculk:4",
            "11926,-47,-9609|-|minecraft:amethyst_shard:9;minecraft:candle:2;minecraft:coal:11;minecraft:echo_shard:3;minecraft:lead:1;minecraft:sculk_sensor:1",
            "11931,-49,-9518|-|minecraft:bone:8;minecraft:coal:6;minecraft:compass:1;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:iron_leggings:1;minecraft:sculk:9",
            "11944,-48,-9578|-|minecraft:coal:14;minecraft:disc_fragment_5:3;minecraft:echo_shard:3;minecraft:name_tag:1;minecraft:potion:3;minecraft:ward_armor_trim_smithing_template:1",
            "11948,-50,-9550|附魔金|minecraft:coal:15;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:2;minecraft:glow_berries:11;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:sculk_sensor:2",
            "11951,-50,-9507|-|minecraft:baked_potato:5;minecraft:packed_ice:3;minecraft:snowball:5;minecraft:suspicious_stew:5",
            "11955,-48,-9475|-|minecraft:bone:12;minecraft:candle:4;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:potion:3;minecraft:sculk_sensor:3;minecraft:soul_torch:20",
            "11965,-50,-9580|-|minecraft:packed_ice:3;minecraft:snowball:21",
            "11968,-49,-9518|-|minecraft:candle:1;minecraft:enchanted_book:2;minecraft:potion:1;minecraft:sculk:8;minecraft:sculk_sensor:3",
            "11983,-49,-9551|-|minecraft:bone:8;minecraft:disc_fragment_5:3;minecraft:name_tag:4;minecraft:saddle:1;minecraft:sculk_sensor:1;minecraft:soul_torch:3",
            "11984,-50,-9576|-|minecraft:amethyst_shard:13;minecraft:candle:1;minecraft:potion:5;minecraft:soul_torch:10",
            "12004,-37,-9583|-|minecraft:glow_berries:11;minecraft:name_tag:1;minecraft:sculk:4;minecraft:sculk_sensor:6;minecraft:soul_torch:8");

    /** 区块 777,-576（21 箱），1.21 ~ 1.21.5 的表。 */
    private static final List<String> A1215_CITY_777_576 = List.of(
            "12342,-47,-9236|-|minecraft:bone:22;minecraft:compass:1;minecraft:echo_shard:3;minecraft:enchanted_book:2;minecraft:experience_bottle:1;minecraft:name_tag:1",
            "12342,-47,-9244|迅捷3|minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:name_tag:1;minecraft:ward_armor_trim_smithing_template:1",
            "12371,-50,-9234|-|minecraft:coal:52;minecraft:diamond_leggings:1;minecraft:echo_shard:3;minecraft:experience_bottle:1;minecraft:potion:3;minecraft:soul_torch:10",
            "12383,-37,-9274|-|minecraft:amethyst_shard:6;minecraft:coal:31;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:potion:1;minecraft:soul_torch:8",
            "12388,-50,-9234|-|minecraft:enchanted_book:1;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:3;minecraft:soul_torch:15",
            "12392,-48,-9206|-|minecraft:amethyst_shard:13;minecraft:book:5;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:glow_berries:12;minecraft:sculk_sensor:4;minecraft:soul_torch:11",
            "12411,-47,-9309|-|minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:music_disc_cat:1;minecraft:name_tag:1;minecraft:potion:2;minecraft:saddle:2;minecraft:sculk_sensor:1",
            "12411,-47,-9317|-|minecraft:bone:7;minecraft:coal:19;minecraft:disc_fragment_5:2;minecraft:lead:1",
            "12451,-49,-9157|-|minecraft:amethyst_shard:13;minecraft:book:10;minecraft:coal:7;minecraft:diamond_hoe:1;minecraft:experience_bottle:2;minecraft:soul_torch:9;minecraft:ward_armor_trim_smithing_template:1",
            "12459,-49,-9282|-|minecraft:amethyst_shard:4;minecraft:bone:18;minecraft:book:5;minecraft:name_tag:1;minecraft:sculk:6",
            "12467,-47,-9309|-|minecraft:diamond_hoe:1;minecraft:disc_fragment_5:1;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:name_tag:1",
            "12467,-47,-9317|-|minecraft:amethyst_shard:1;minecraft:bone:11;minecraft:book:10;minecraft:diamond_hoe:1;minecraft:iron_leggings:1;minecraft:potion:3;minecraft:sculk_sensor:1;minecraft:soul_torch:25",
            "12474,-50,-9204|-|minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:enchanted_book:3;minecraft:experience_bottle:3;minecraft:music_disc_13:1;minecraft:sculk_sensor:2",
            "12478,-50,-9307|-|minecraft:book:9;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:music_disc_cat:1",
            "12480,-44,-9200|-|minecraft:amethyst_shard:17;minecraft:bone:15;minecraft:coal:7;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:experience_bottle:4",
            "12485,-50,-9147|迅捷3|minecraft:coal:12;minecraft:compass:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:experience_bottle:2;minecraft:lead:1",
            "12496,-50,-9281|-|minecraft:candle:7;minecraft:compass:1;minecraft:echo_shard:2;minecraft:enchanted_book:1;minecraft:lead:1;minecraft:music_disc_13:1;minecraft:name_tag:1;minecraft:potion:2;minecraft:soul_torch:12",
            "12497,-49,-9306|-|minecraft:amethyst_shard:10;minecraft:bone:12;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:echo_shard:1;minecraft:iron_leggings:1;minecraft:lead:1",
            "12525,-49,-9256|-|minecraft:book:6;minecraft:candle:5;minecraft:echo_shard:2;minecraft:enchanted_book:1;minecraft:saddle:1",
            "12532,-35,-9183|-|minecraft:amethyst_shard:1;minecraft:book:5;minecraft:candle:1;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:2;minecraft:saddle:1",
            "12533,-50,-9233|-|minecraft:bone:17;minecraft:book:10;minecraft:coal:13;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1");

    /** 区块 750,-621（20 箱），1.21 ~ 1.21.5 的表。 */
    private static final List<String> A1215_CITY_750_621 = List.of(
            "11900,-37,-9955|-|minecraft:book:6;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:iron_leggings:1;minecraft:soul_torch:11",
            "11909,-47,-9912|-|minecraft:amethyst_shard:15;minecraft:coal:14;minecraft:compass:1;minecraft:disc_fragment_5:2;minecraft:saddle:1",
            "11909,-47,-9920|-|minecraft:amethyst_shard:7;minecraft:bone:5;minecraft:coal:14;minecraft:enchanted_book:1;minecraft:iron_leggings:2;minecraft:soul_torch:9",
            "11938,-48,-9920|迅捷3|minecraft:bone:12;minecraft:book:9;minecraft:enchanted_book:2;minecraft:iron_leggings:2;minecraft:lead:1;minecraft:sculk:5",
            "11958,-50,-9916|-|minecraft:bone:13;minecraft:book:3;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:music_disc_cat:2;minecraft:music_disc_otherside:1;minecraft:potion:3;minecraft:soul_torch:6",
            "11962,-48,-9870|-|minecraft:amethyst_shard:15;minecraft:coal:6;minecraft:echo_shard:2;minecraft:soul_torch:15;minecraft:ward_armor_trim_smithing_template:1",
            "11964,-50,-9839|-|minecraft:disc_fragment_5:1;minecraft:glow_berries:15;minecraft:saddle:1;minecraft:sculk:6;minecraft:soul_torch:5",
            "11970,-44,-9835|-|minecraft:bone:2;minecraft:book:8;minecraft:coal:8;minecraft:enchanted_book:1;minecraft:sculk:4;minecraft:ward_armor_trim_smithing_template:1",
            "11989,-48,-9888|附魔金|minecraft:disc_fragment_5:3;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:music_disc_cat:1;minecraft:name_tag:1;minecraft:potion:7",
            "11991,-50,-9874|-|minecraft:amethyst_shard:9;minecraft:candle:4;minecraft:coal:7;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:sculk_sensor:1;minecraft:soul_torch:2",
            "11993,-49,-10034|-|minecraft:amethyst_shard:10;minecraft:book:8;minecraft:candle:4;minecraft:coal:12;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:glow_berries:14;minecraft:music_disc_13:1",
            "12018,-50,-9998|-|minecraft:bone:10;minecraft:disc_fragment_5:1;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:sculk_catalyst:3;minecraft:ward_armor_trim_smithing_template:1",
            "12024,-37,-9872|-|minecraft:amethyst_shard:9;minecraft:book:4;minecraft:candle:1;minecraft:coal:27;minecraft:potion:2",
            "12024,-50,-9842|-|minecraft:amethyst_shard:6;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:enchanted_book:2;minecraft:experience_bottle:1;minecraft:name_tag:1",
            "12037,-50,-9839|-|minecraft:bone:6;minecraft:book:7;minecraft:coal:22;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:glow_berries:10;minecraft:music_disc_cat:1;minecraft:potion:2",
            "12043,-44,-9835|-|minecraft:amethyst_shard:10;minecraft:coal:15;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:5;minecraft:music_disc_13:1;minecraft:sculk:9",
            "12055,-47,-9894|-|minecraft:book:4;minecraft:experience_bottle:3;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:music_disc_13:1;minecraft:music_disc_otherside:1;minecraft:sculk_sensor:2",
            "12062,-44,-9999|-|minecraft:book:5;minecraft:coal:6;minecraft:diamond_leggings:1;minecraft:potion:2;minecraft:saddle:1;minecraft:sculk_catalyst:2",
            "12063,-47,-9894|-|minecraft:book:7;minecraft:candle:2;minecraft:coal:14;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:sculk:9;minecraft:sculk_catalyst:1;minecraft:soul_torch:16",
            "12098,-50,-9953|-|minecraft:candle:4;minecraft:diamond_horse_armor:1;minecraft:experience_bottle:2;minecraft:potion:1;minecraft:soul_torch:1");

    @Test
    void a1206_区块779_595_结果与黄金数据一致() {
        assertEquals(A1206_CITY_779_595, chestsOf(GameVersionOption.UP_TO_1_20_6, 779, -595));
    }

    @Test
    void a1206_区块773_622_结果与黄金数据一致() {
        assertEquals(A1206_CITY_773_622, chestsOf(GameVersionOption.UP_TO_1_20_6, 773, -622));
    }

    @Test
    void a1206_区块744_598_结果与黄金数据一致() {
        assertEquals(A1206_CITY_744_598, chestsOf(GameVersionOption.UP_TO_1_20_6, 744, -598));
    }

    @Test
    void a1206_区块777_576_结果与黄金数据一致() {
        assertEquals(A1206_CITY_777_576, chestsOf(GameVersionOption.UP_TO_1_20_6, 777, -576));
    }

    @Test
    void a1206_区块750_621_结果与黄金数据一致() {
        assertEquals(A1206_CITY_750_621, chestsOf(GameVersionOption.UP_TO_1_20_6, 750, -621));
    }

    @Test
    void a1215_区块779_595_结果与黄金数据一致() {
        assertEquals(A1215_CITY_779_595, chestsOf(GameVersionOption.V1_21_TO_1_21_5, 779, -595));
    }

    @Test
    void a1215_区块773_622_结果与黄金数据一致() {
        assertEquals(A1215_CITY_773_622, chestsOf(GameVersionOption.V1_21_TO_1_21_5, 773, -622));
    }

    @Test
    void a1215_区块744_598_结果与黄金数据一致() {
        assertEquals(A1215_CITY_744_598, chestsOf(GameVersionOption.V1_21_TO_1_21_5, 744, -598));
    }

    @Test
    void a1215_区块777_576_结果与黄金数据一致() {
        assertEquals(A1215_CITY_777_576, chestsOf(GameVersionOption.V1_21_TO_1_21_5, 777, -576));
    }

    @Test
    void a1215_区块750_621_结果与黄金数据一致() {
        assertEquals(A1215_CITY_750_621, chestsOf(GameVersionOption.V1_21_TO_1_21_5, 750, -621));
    }

    // ==================== 工具 ====================

    /** 跑一座古城：拼图模拟 → 箱子提取 → 指定版本的表 + 生产模式掷骰，输出每箱一行的可读结果。 */
    private static List<String> chestsOf(GameVersionOption version, int chunkX, int chunkZ) {
        AncientResourceSource structures = AncientResourceSource.bundledStructures();
        AncientResourceSource pools = AncientResourceSource.bundledPools();
        AncientResourceSource dataSrc = AncientResourceSource.bundledLootTables(version);
        AncientEnchantData data = AncientEnchantData.bundled();

        List<Piece> pieces = AncientJigsawSimulator.simulate(SEED, chunkX, chunkZ, structures, pools);
        List<AncientChestExtractor.Chest> chests =
            AncientChestExtractor.extract(pieces, SEED, structures);

        List<String> rows = new ArrayList<>();
        for (AncientChestExtractor.Chest c : chests) {
            String id = c.lootTable().replace("minecraft:", "");
            AncientLootTable table = AncientLootTable.load(id, dataSrc);
            List<AncientLootEvaluator.ItemStack> items =
                AncientLootEvaluator.roll(table, c.seed(), data);

            TreeMap<String, Integer> totals = new TreeMap<>();
            for (AncientLootEvaluator.ItemStack s : items) {
                totals.merge(s.id(), s.count(), Integer::sum);
            }
            boolean gold = AncientLootEvaluator.hasEnchantedGoldenApple(items);
            boolean swift3 = AncientLootEvaluator.hasSwiftSneak(items, 3);
            String label = gold && swift3 ? "两者" : gold ? "附魔金" : swift3 ? "迅捷3" : "-";

            StringBuilder itemText = new StringBuilder();
            for (Map.Entry<String, Integer> e : totals.entrySet()) {
                if (itemText.length() > 0) itemText.append(';');
                itemText.append(e.getKey()).append(':').append(e.getValue());
            }
            rows.add(c.x() + "," + c.y() + "," + c.z() + "|" + label + "|" + itemText);
        }
        Collections.sort(rows);
        return rows;
    }

    private static String rawText(GameVersionOption version, String path) {
        try {
            String s = AncientResourceSource.bundledLootTables(version).readText(path);
            assertTrue(s != null && !s.isEmpty(), "缺少资源: " + version + " / " + path);
            return s;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pool0(GameVersionOption version) {
        Map<String, Object> root =
            (Map<String, Object>) AncientJson.parse(rawText(version, "loot_table/chests/ancient_city.json"));
        List<Map<String, Object>> pools = (List<Map<String, Object>>) root.get("pools");
        return (List<Map<String, Object>>) pools.get(0).get("entries");
    }

    @SuppressWarnings("unchecked")
    private static Object countOf(GameVersionOption version, int entryIndex) {
        Map<String, Object> entry = pool0(version).get(entryIndex);
        List<Map<String, Object>> functions = (List<Map<String, Object>>) entry.get("functions");
        return functions.get(0).get("count");
    }

    private static void assertVariant(GameVersionOption version, int entries, int weight, String entry8,
                                      boolean treasure, boolean legacyList, boolean options) {
        List<Map<String, Object>> es = pool0(version);
        assertEquals(entries, es.size(), version + " 池 0 条目数");
        int w = 0;
        for (Map<String, Object> e : es) {
            w += ((Number) e.getOrDefault("weight", 1)).intValue();
        }
        assertEquals(weight, w, version + " 池 0 权重和");
        assertEquals(entry8, String.valueOf(es.get(8).get("name")), version + " 第 8 条目");
        String text = rawText(version, "loot_table/chests/ancient_city.json");
        assertEquals(treasure, text.contains("\"treasure\""), version + " treasure 关键字");
        assertEquals(legacyList, text.contains("\"enchantments\""), version + " enchantments 旧列表");
        assertEquals(options, text.contains("\"options\""), version + " options 关键字");
    }
}
