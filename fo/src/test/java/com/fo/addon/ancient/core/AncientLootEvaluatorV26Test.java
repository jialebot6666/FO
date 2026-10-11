package com.fo.addon.ancient.core;

import com.fo.addon.ancient.core.AncientJigsawSimulator.Piece;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「&gt;= 26.1」战利品表的黄金回归测试。
 *
 * <p>为什么单独测这一版：26.x 的古城战利品表比 1.21.11 <b>少一个 {@code minecraft:name_tag} 条目</b>
 * （池权重 86 → 84，条目 28 → 27）。少这一个条目就足以让<b>每一次加权抽取都错位</b>——
 * 实机表现正是「箱子位置全对、物品全错」：位置只取决于世界种子，物品取决于战利品表。
 *
 * <p>数据来源：Mojang 官方 26.1.2 客户端 jar 的
 * {@code data/minecraft/loot_table/chests/ancient_city.json}，逐字节放入
 * {@code resources/fo/ancient/v26/}。已核对 26.1.2 / 26.2 / 26.3 三个版本：
 * 26.3 只是把 JSON 键名换成了 {@code modifier}（内容一致），三版对自己的古城表<b>语义完全相同</b>；
 * 冰窖表、43 个附魔、22 个附魔标签、32 个可达物品标签与 1.21.11 <b>完全一致</b>，
 * 所以只有古城表需要按版本切换。
 *
 * <p>下面 113 个箱子是生产模式（MC_NATIVE）的期望值，改动掷骰链路会让它失败。
 */
class AncientLootEvaluatorV26Test {

    private static final long SEED = -7346913998703726680L;

    /** 区块 779,-595（18 箱）。 */
    private static final List<String> CITY_779_595 = List.of(
            "12383,-48,-9504|-|minecraft:bone:7;minecraft:coal:14;minecraft:echo_shard:2;minecraft:enchanted_book:2;minecraft:music_disc_cat:1;minecraft:potion:7",
            "12390,-47,-9496|-|minecraft:diamond_horse_armor:1;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:sculk:10;minecraft:soul_torch:6",
            "12390,-47,-9504|-|minecraft:coal:10;minecraft:disc_fragment_5:3;minecraft:leather:3;minecraft:potion:2;minecraft:sculk_catalyst:2",
            "12397,-35,-9538|-|minecraft:coal:6;minecraft:disc_fragment_5:3;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:leather:1;minecraft:sculk_sensor:3",
            "12398,-35,-9556|迅捷3|minecraft:amethyst_shard:3;minecraft:book:9;minecraft:compass:1;minecraft:enchanted_book:3;minecraft:experience_bottle:2;minecraft:sculk_sensor:3",
            "12410,-44,-9603|-|minecraft:book:5;minecraft:lead:1;minecraft:music_disc_otherside:1;minecraft:sculk_sensor:3;minecraft:soul_torch:5",
            "12416,-50,-9599|-|minecraft:book:9;minecraft:coal:19;minecraft:compass:1;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:potion:1;minecraft:sculk_sensor:2;minecraft:soul_torch:7",
            "12438,-49,-9531|-|minecraft:bone:11;minecraft:book:3;minecraft:diamond_horse_armor:2;minecraft:disc_fragment_5:1;minecraft:echo_shard:3;minecraft:glow_berries:6;minecraft:music_disc_13:1;minecraft:potion:5",
            "12450,-50,-9596|-|minecraft:book:8;minecraft:coal:14;minecraft:compass:1;minecraft:disc_fragment_5:1;minecraft:experience_bottle:1;minecraft:sculk_sensor:1;minecraft:ward_armor_trim_smithing_template:1",
            "12456,-49,-9442|-|minecraft:book:3;minecraft:coal:12;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:leather:3;minecraft:potion:3",
            "12457,-49,-9564|-|minecraft:bone:12;minecraft:coal:19;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:potion:1;minecraft:soul_torch:14",
            "12489,-37,-9619|-|minecraft:amethyst_shard:8;minecraft:bone:1;minecraft:coal:14;minecraft:compass:1;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:2;minecraft:echo_shard:3;minecraft:experience_bottle:3;minecraft:ward_armor_trim_smithing_template:1",
            "12521,-35,-9474|-|minecraft:bone:4;minecraft:candle:4;minecraft:coal:11;minecraft:compass:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:1;minecraft:glow_berries:3;minecraft:potion:1;minecraft:sculk_catalyst:1",
            "12529,-47,-9565|迅捷3|minecraft:diamond_hoe:1;minecraft:enchanted_book:1;minecraft:potion:1;minecraft:sculk:7;minecraft:sculk_catalyst:1",
            "12529,-47,-9573|-|minecraft:coal:10;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:leather:2;minecraft:potion:3",
            "12554,-49,-9583|附魔金|minecraft:candle:2;minecraft:coal:19;minecraft:disc_fragment_5:1;minecraft:enchanted_golden_apple:2;minecraft:sculk:11",
            "12555,-50,-9453|-|minecraft:amethyst_shard:3;minecraft:bone:13;minecraft:diamond_hoe:2;minecraft:music_disc_cat:1;minecraft:sculk_catalyst:1",
            "12557,-48,-9553|-|minecraft:bone:5;minecraft:candle:2;minecraft:coal:11;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:potion:1;minecraft:sculk:10;minecraft:sculk_sensor:3");

    /** 区块 773,-622（26 箱）。 */
    private static final List<String> CITY_773_622 = List.of(
            "12269,-49,-9945|附魔金|minecraft:compass:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:2;minecraft:glow_berries:12;minecraft:potion:2;minecraft:sculk:10;minecraft:soul_torch:1",
            "12282,-50,-9977|-|minecraft:book:3;minecraft:candle:4;minecraft:experience_bottle:3;minecraft:lead:1;minecraft:potion:2;minecraft:sculk_sensor:2;minecraft:soul_torch:14",
            "12287,-50,-9941|-|minecraft:coal:8;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:echo_shard:3;minecraft:enchanted_book:1;minecraft:experience_bottle:2;minecraft:sculk_catalyst:2",
            "12293,-44,-9937|-|minecraft:coal:11;minecraft:experience_bottle:3;minecraft:glow_berries:13;minecraft:music_disc_otherside:1;minecraft:sculk_sensor:3;minecraft:silence_armor_trim_smithing_template:1;minecraft:soul_torch:1",
            "12301,-44,-10054|-|minecraft:amethyst_shard:12;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:echo_shard:4;minecraft:potion:1;minecraft:sculk:9;minecraft:sculk_catalyst:1",
            "12306,-50,-9944|迅捷3|minecraft:candle:2;minecraft:coal:11;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:music_disc_13:1",
            "12308,-49,-10011|迅捷3|minecraft:amethyst_shard:4;minecraft:bone:41;minecraft:candle:4;minecraft:coal:9;minecraft:compass:1;minecraft:enchanted_book:1;minecraft:glow_berries:15;minecraft:potion:3",
            "12314,-50,-9918|-|minecraft:diamond_horse_armor:1;minecraft:enchanted_book:2;minecraft:music_disc_cat:1;minecraft:sculk:6",
            "12330,-49,-9893|-|minecraft:book:7;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:sculk_sensor:2;minecraft:soul_torch:11",
            "12347,-47,-10045|迅捷3|minecraft:amethyst_shard:12;minecraft:compass:1;minecraft:enchanted_book:2;minecraft:music_disc_13:1",
            "12347,-47,-10053|-|minecraft:candle:1;minecraft:diamond_hoe:2;minecraft:echo_shard:2;minecraft:enchanted_book:2;minecraft:iron_leggings:1;minecraft:lead:2;minecraft:music_disc_13:1",
            "12350,-35,-9868|附魔金|minecraft:amethyst_shard:11;minecraft:bone:13;minecraft:book:8;minecraft:enchanted_golden_apple:1;minecraft:soul_torch:12",
            "12356,-50,-9850|-|minecraft:bone:2;minecraft:book:8;minecraft:coal:6;minecraft:disc_fragment_5:3;minecraft:leather:3;minecraft:sculk_sensor:2;minecraft:soul_torch:7",
            "12384,-48,-9872|-|minecraft:candle:2;minecraft:echo_shard:5;minecraft:experience_bottle:1;minecraft:iron_leggings:1;minecraft:potion:1;minecraft:soul_torch:8;minecraft:ward_armor_trim_smithing_template:1",
            "12389,-37,-9883|-|minecraft:book:7;minecraft:glow_berries:18;minecraft:iron_leggings:1;minecraft:leather:4;minecraft:music_disc_13:1;minecraft:potion:1;minecraft:sculk_sensor:1",
            "12397,-44,-10014|-|minecraft:bone:1;minecraft:candle:3;minecraft:coal:13;minecraft:echo_shard:2;minecraft:iron_leggings:1;minecraft:potion:1;minecraft:sculk:4;minecraft:sculk_sensor:1",
            "12411,-49,-9944|-|minecraft:amethyst_shard:12;minecraft:book:6;minecraft:candle:4;minecraft:coal:16;minecraft:diamond_leggings:1;minecraft:sculk:11;minecraft:sculk_sensor:2",
            "12412,-50,-9969|-|minecraft:bone:13;minecraft:compass:1;minecraft:echo_shard:1;minecraft:experience_bottle:1;minecraft:iron_leggings:1;minecraft:potion:1;minecraft:soul_torch:6;minecraft:ward_armor_trim_smithing_template:1",
            "12415,-50,-10003|-|minecraft:book:10;minecraft:diamond_horse_armor:2;minecraft:disc_fragment_5:2;minecraft:potion:2",
            "12422,-47,-9933|-|minecraft:book:5;minecraft:candle:3;minecraft:coal:13;minecraft:enchanted_book:2;minecraft:music_disc_13:1;minecraft:potion:1;minecraft:soul_torch:1;minecraft:ward_armor_trim_smithing_template:1",
            "12422,-47,-9941|-|minecraft:amethyst_shard:11;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:soul_torch:4",
            "12432,-50,-10003|-|minecraft:book:10;minecraft:enchanted_book:1;minecraft:experience_bottle:4;minecraft:glow_berries:14;minecraft:sculk:6;minecraft:sculk_catalyst:1;minecraft:sculk_sensor:3",
            "12432,-50,-9977|-|minecraft:book:10;minecraft:coal:14;minecraft:compass:1;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:experience_bottle:1;minecraft:iron_leggings:1;minecraft:music_disc_13:1;minecraft:potion:3;minecraft:soul_torch:10",
            "12447,-48,-10005|-|minecraft:book:6;minecraft:coal:15;minecraft:disc_fragment_5:5;minecraft:experience_bottle:1;minecraft:sculk_sensor:3",
            "12447,-49,-9944|-|minecraft:bone:2;minecraft:book:3;minecraft:coal:7;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:soul_torch:13",
            "12449,-49,-9968|-|minecraft:bone:11;minecraft:candle:1;minecraft:echo_shard:3;minecraft:iron_leggings:1;minecraft:music_disc_13:1;minecraft:potion:1;minecraft:sculk_sensor:1;minecraft:soul_torch:6");

    /** 区块 744,-598（28 箱）。 */
    private static final List<String> CITY_744_598 = List.of(
            "11823,-49,-9552|附魔金|minecraft:bone:14;minecraft:coal:15;minecraft:echo_shard:2;minecraft:enchanted_golden_apple:2;minecraft:music_disc_13:1;minecraft:music_disc_otherside:1;minecraft:sculk_catalyst:3;minecraft:sculk_sensor:2",
            "11827,-49,-9609|-|minecraft:amethyst_shard:11;minecraft:book:9;minecraft:coal:8;minecraft:diamond_hoe:1;minecraft:experience_bottle:3;minecraft:lead:1",
            "11839,-49,-9478|-|minecraft:bone:10;minecraft:book:9;minecraft:candle:4;minecraft:coal:13;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:1;minecraft:echo_shard:3;minecraft:experience_bottle:2;minecraft:potion:3;minecraft:silence_armor_trim_smithing_template:1;minecraft:soul_torch:1",
            "11841,-49,-9502|-|minecraft:disc_fragment_5:3;minecraft:echo_shard:6;minecraft:enchanted_book:1;minecraft:potion:1;minecraft:soul_torch:11",
            "11859,-49,-9552|-|minecraft:bone:15;minecraft:candle:4;minecraft:echo_shard:1;minecraft:sculk:9;minecraft:sculk_catalyst:2;minecraft:sculk_sensor:1",
            "11861,-48,-9475|-|minecraft:amethyst_shard:12;minecraft:coal:23;minecraft:disc_fragment_5:2;minecraft:music_disc_cat:1",
            "11873,-50,-9474|-|minecraft:bone:10;minecraft:diamond_hoe:1;minecraft:sculk_catalyst:1;minecraft:soul_torch:10",
            "11876,-50,-9513|-|minecraft:baked_potato:5;minecraft:golden_carrot:2;minecraft:packed_ice:10;minecraft:snowball:24;minecraft:suspicious_stew:5",
            "11879,-44,-9470|-|minecraft:bone:6;minecraft:book:10;minecraft:candle:4;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:experience_bottle:1",
            "11883,-37,-9637|-|minecraft:amethyst_shard:7;minecraft:bone:4;minecraft:coal:15;minecraft:diamond_leggings:1;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:sculk:4;minecraft:sculk_catalyst:1",
            "11883,-44,-9649|-|minecraft:bone:21;minecraft:coal:13;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:3;minecraft:music_disc_cat:1",
            "11887,-50,-9655|-|minecraft:coal:39;minecraft:disc_fragment_5:1;minecraft:echo_shard:3;minecraft:iron_leggings:2;minecraft:lead:1;minecraft:soul_torch:14",
            "11890,-50,-9670|迅捷3|minecraft:book:3;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:2;minecraft:experience_bottle:3",
            "11891,-49,-9618|迅捷3|minecraft:bone:4;minecraft:book:6;minecraft:coal:10;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:1;minecraft:enchanted_book:1;minecraft:sculk:4;minecraft:sculk_catalyst:2;minecraft:sculk_sensor:1;minecraft:soul_torch:10;minecraft:ward_armor_trim_smithing_template:1",
            "11915,-49,-9652|-|minecraft:coal:8;minecraft:diamond_horse_armor:1;minecraft:echo_shard:2;minecraft:glow_berries:15;minecraft:lead:1;minecraft:leather:1;minecraft:music_disc_otherside:1",
            "11918,-47,-9609|-|minecraft:candle:2;minecraft:coal:7;minecraft:disc_fragment_5:1;minecraft:echo_shard:1;minecraft:experience_bottle:3;minecraft:leather:2",
            "11923,-37,-9633|-|minecraft:amethyst_shard:14;minecraft:book:9;minecraft:coal:9;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:iron_leggings:1;minecraft:potion:3;minecraft:sculk:6",
            "11926,-47,-9609|-|minecraft:bone:7;minecraft:coal:14;minecraft:enchanted_book:1;minecraft:lead:1;minecraft:music_disc_13:1;minecraft:soul_torch:13",
            "11931,-49,-9518|-|minecraft:book:4;minecraft:coal:12;minecraft:enchanted_book:1;minecraft:glow_berries:12;minecraft:sculk:10;minecraft:soul_torch:15",
            "11944,-48,-9578|-|minecraft:bone:12;minecraft:coal:16;minecraft:glow_berries:9;minecraft:lead:1;minecraft:soul_torch:12;minecraft:ward_armor_trim_smithing_template:1",
            "11948,-50,-9550|迅捷3|minecraft:amethyst_shard:15;minecraft:candle:1;minecraft:disc_fragment_5:3;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:leather:1;minecraft:sculk_catalyst:2;minecraft:sculk_sensor:1",
            "11951,-50,-9507|-|minecraft:baked_potato:5;minecraft:packed_ice:3;minecraft:snowball:5;minecraft:suspicious_stew:5",
            "11955,-48,-9475|附魔金|minecraft:bone:11;minecraft:coal:12;minecraft:diamond_hoe:1;minecraft:enchanted_golden_apple:1;minecraft:glow_berries:18;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:potion:1",
            "11965,-50,-9580|-|minecraft:packed_ice:3;minecraft:snowball:21",
            "11968,-49,-9518|-|minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:echo_shard:3;minecraft:enchanted_book:1;minecraft:music_disc_otherside:1;minecraft:soul_torch:6",
            "11983,-49,-9551|-|minecraft:amethyst_shard:7;minecraft:book:4;minecraft:coal:12;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:potion:1;minecraft:sculk:6;minecraft:soul_torch:22",
            "11984,-50,-9576|-|minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:potion:3;minecraft:sculk:4;minecraft:sculk_sensor:1",
            "12004,-37,-9583|-|minecraft:candle:3;minecraft:compass:2;minecraft:disc_fragment_5:2;minecraft:iron_leggings:1;minecraft:soul_torch:8");

    /** 区块 777,-576（21 箱）。 */
    private static final List<String> CITY_777_576 = List.of(
            "12342,-47,-9236|-|minecraft:candle:2;minecraft:coal:7;minecraft:compass:1;minecraft:glow_berries:15;minecraft:iron_leggings:1;minecraft:potion:3;minecraft:sculk_catalyst:1;minecraft:ward_armor_trim_smithing_template:1",
            "12342,-47,-9244|-|minecraft:amethyst_shard:12;minecraft:bone:6;minecraft:coal:8;minecraft:diamond_hoe:1;minecraft:music_disc_13:1;minecraft:ward_armor_trim_smithing_template:1",
            "12371,-50,-9234|-|minecraft:candle:7;minecraft:disc_fragment_5:3;minecraft:echo_shard:3;minecraft:experience_bottle:1;minecraft:glow_berries:10;minecraft:lead:1;minecraft:sculk:9;minecraft:sculk_catalyst:2;minecraft:soul_torch:3",
            "12383,-37,-9274|-|minecraft:coal:24;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:glow_berries:12;minecraft:lead:1;minecraft:music_disc_cat:2;minecraft:ward_armor_trim_smithing_template:1",
            "12388,-50,-9234|-|minecraft:bone:7;minecraft:book:10;minecraft:iron_leggings:1;minecraft:potion:1;minecraft:sculk_sensor:6",
            "12392,-48,-9206|-|minecraft:diamond_horse_armor:3;minecraft:disc_fragment_5:6;minecraft:iron_leggings:1;minecraft:music_disc_otherside:1",
            "12411,-47,-9309|-|minecraft:book:9;minecraft:disc_fragment_5:2;minecraft:echo_shard:1;minecraft:enchanted_book:2;minecraft:glow_berries:12;minecraft:iron_leggings:1;minecraft:music_disc_otherside:1;minecraft:soul_torch:1",
            "12411,-47,-9317|附魔金|minecraft:disc_fragment_5:2;minecraft:echo_shard:2;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:sculk_sensor:1",
            "12451,-49,-9157|附魔金|minecraft:bone:7;minecraft:coal:8;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:experience_bottle:2;minecraft:leather:1",
            "12459,-49,-9282|-|minecraft:bone:14;minecraft:book:5;minecraft:disc_fragment_5:1;minecraft:iron_leggings:1;minecraft:lead:1",
            "12467,-47,-9309|-|minecraft:compass:1;minecraft:diamond_horse_armor:1;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:music_disc_cat:1;minecraft:sculk_catalyst:1",
            "12467,-47,-9317|-|minecraft:bone:6;minecraft:book:9;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:potion:2;minecraft:sculk:12;minecraft:sculk_sensor:2",
            "12474,-50,-9204|-|minecraft:bone:19;minecraft:coal:20;minecraft:disc_fragment_5:2;minecraft:music_disc_13:1;minecraft:music_disc_otherside:1;minecraft:potion:1",
            "12478,-50,-9307|-|minecraft:candle:2;minecraft:enchanted_book:1;minecraft:glow_berries:2;minecraft:potion:2;minecraft:sculk_sensor:3",
            "12480,-44,-9200|-|minecraft:amethyst_shard:15;minecraft:enchanted_book:2;minecraft:glow_berries:4;minecraft:lead:1;minecraft:potion:6;minecraft:sculk_catalyst:1;minecraft:ward_armor_trim_smithing_template:1",
            "12485,-50,-9147|-|minecraft:bone:8;minecraft:candle:1;minecraft:iron_leggings:2;minecraft:music_disc_otherside:1;minecraft:sculk_sensor:1",
            "12496,-50,-9281|-|minecraft:amethyst_shard:4;minecraft:book:3;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:1;minecraft:echo_shard:1;minecraft:enchanted_book:2;minecraft:experience_bottle:1;minecraft:leather:2;minecraft:potion:2",
            "12497,-49,-9306|-|minecraft:book:7;minecraft:diamond_hoe:1;minecraft:experience_bottle:1;minecraft:music_disc_cat:1;minecraft:potion:1;minecraft:sculk_catalyst:1",
            "12525,-49,-9256|-|minecraft:coal:29;minecraft:experience_bottle:1;minecraft:lead:1;minecraft:sculk_sensor:1",
            "12532,-35,-9183|-|minecraft:amethyst_shard:9;minecraft:book:4;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:music_disc_otherside:1;minecraft:potion:1;minecraft:sculk_catalyst:2;minecraft:soul_torch:14",
            "12533,-50,-9233|-|minecraft:book:10;minecraft:coal:10;minecraft:diamond_hoe:1;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:lead:1;minecraft:potion:2;minecraft:sculk:10;minecraft:sculk_sensor:3");

    /** 区块 750,-621（20 箱）。 */
    private static final List<String> CITY_750_621 = List.of(
            "11900,-37,-9955|迅捷3|minecraft:bone:9;minecraft:book:7;minecraft:echo_shard:3;minecraft:enchanted_book:2;minecraft:lead:1;minecraft:leather:4;minecraft:soul_torch:8",
            "11909,-47,-9912|-|minecraft:coal:13;minecraft:diamond_horse_armor:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:ward_armor_trim_smithing_template:1",
            "11909,-47,-9920|-|minecraft:enchanted_book:1;minecraft:music_disc_otherside:1;minecraft:potion:5;minecraft:sculk_catalyst:1;minecraft:soul_torch:18",
            "11938,-48,-9920|-|minecraft:disc_fragment_5:6;minecraft:enchanted_book:2;minecraft:lead:2;minecraft:sculk_catalyst:2;minecraft:sculk_sensor:3",
            "11958,-50,-9916|迅捷3|minecraft:amethyst_shard:11;minecraft:coal:13;minecraft:diamond_hoe:1;minecraft:echo_shard:1;minecraft:enchanted_book:2;minecraft:experience_bottle:5;minecraft:leather:2",
            "11962,-48,-9870|-|minecraft:diamond_leggings:2;minecraft:enchanted_book:1;minecraft:glow_berries:15;minecraft:leather:2",
            "11964,-50,-9839|-|minecraft:book:10;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:soul_torch:17",
            "11970,-44,-9835|-|minecraft:bone:7;minecraft:book:8;minecraft:candle:1;minecraft:compass:1;minecraft:disc_fragment_5:1",
            "11989,-48,-9888|-|minecraft:amethyst_shard:13;minecraft:book:5;minecraft:coal:13;minecraft:experience_bottle:2;minecraft:glow_berries:12;minecraft:iron_leggings:1;minecraft:potion:2;minecraft:sculk_sensor:2",
            "11991,-50,-9874|附魔金|minecraft:amethyst_shard:9;minecraft:book:16;minecraft:candle:3;minecraft:enchanted_book:2;minecraft:enchanted_golden_apple:2;minecraft:music_disc_cat:1;minecraft:potion:1",
            "11993,-49,-10034|-|minecraft:bone:9;minecraft:coal:22;minecraft:enchanted_book:1;minecraft:experience_bottle:2;minecraft:glow_berries:14;minecraft:iron_leggings:1;minecraft:potion:2;minecraft:soul_torch:1",
            "12018,-50,-9998|迅捷3|minecraft:book:6;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:1;minecraft:enchanted_book:1;minecraft:experience_bottle:2;minecraft:soul_torch:24",
            "12024,-37,-9872|-|minecraft:book:3;minecraft:coal:27;minecraft:enchanted_book:1;minecraft:glow_berries:14;minecraft:potion:2",
            "12024,-50,-9842|-|minecraft:amethyst_shard:3;minecraft:book:13;minecraft:disc_fragment_5:2;minecraft:experience_bottle:3;minecraft:potion:5",
            "12037,-50,-9839|-|minecraft:amethyst_shard:1;minecraft:compass:1;minecraft:disc_fragment_5:2;minecraft:experience_bottle:1;minecraft:lead:1;minecraft:sculk_sensor:1;minecraft:soul_torch:20",
            "12043,-44,-9835|-|minecraft:bone:21;minecraft:book:8;minecraft:coal:19;minecraft:lead:1;minecraft:music_disc_13:1;minecraft:sculk_sensor:2;minecraft:soul_torch:10",
            "12055,-47,-9894|附魔金|minecraft:bone:2;minecraft:coal:22;minecraft:enchanted_book:1;minecraft:enchanted_golden_apple:1;minecraft:iron_leggings:1;minecraft:lead:1",
            "12062,-44,-9999|-|minecraft:book:5;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:3;minecraft:experience_bottle:2;minecraft:glow_berries:4;minecraft:iron_leggings:1;minecraft:potion:1",
            "12063,-47,-9894|-|minecraft:amethyst_shard:8;minecraft:bone:14;minecraft:book:11;minecraft:coal:11;minecraft:compass:1;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:leather:2;minecraft:soul_torch:9",
            "12098,-50,-9953|-|minecraft:amethyst_shard:11;minecraft:glow_berries:2;minecraft:iron_leggings:1;minecraft:sculk_catalyst:1");

    @Test
    void 区块779_595的结果与黄金数据一致() {
        assertEquals(CITY_779_595, chestsOf(779, -595), "区块 779,-595 的掷骰结果");
    }

    @Test
    void 区块773_622的结果与黄金数据一致() {
        assertEquals(CITY_773_622, chestsOf(773, -622), "区块 773,-622 的掷骰结果");
    }

    @Test
    void 区块744_598的结果与黄金数据一致() {
        assertEquals(CITY_744_598, chestsOf(744, -598), "区块 744,-598 的掷骰结果");
    }

    @Test
    void 区块777_576的结果与黄金数据一致() {
        assertEquals(CITY_777_576, chestsOf(777, -576), "区块 777,-576 的掷骰结果");
    }

    @Test
    void 区块750_621的结果与黄金数据一致() {
        assertEquals(CITY_750_621, chestsOf(750, -621), "区块 750,-621 的掷骰结果");
    }

    // ==================== 版本切换本身 ====================

    /** 两张表必须真的不一样：26.x 少 name_tag，条目 28 → 27、权重 86 → 84。 */
    @Test
    void 两个版本的古城表确实不同() {
        String legacy = text("loot_table/chests/ancient_city.json", false);
        String v26 = text("loot_table/chests/ancient_city.json", true);

        assertTrue(legacy.contains("minecraft:name_tag"), "1.21.11 的表应当有 name_tag");
        assertFalse(v26.contains("minecraft:name_tag"), "26.x 的表不该有 name_tag");
        assertEquals(28, countEntries(legacy), "1.21.11 池 0 条目数");
        assertEquals(27, countEntries(v26), "26.x 池 0 条目数");
        assertEquals(86, totalWeight(legacy), "1.21.11 池 0 权重和");
        assertEquals(84, totalWeight(v26), "26.x 池 0 权重和");
    }

    /** 冰窖表两版一致，别为了「对称」把它也复制一份。 */
    @Test
    void 冰窖表两版本字节一致() {
        assertEquals(text("loot_table/chests/ancient_city_ice_box.json", false),
            text("loot_table/chests/ancient_city_ice_box.json", true));
    }

    // ==================== 工具 ====================

    private static String text(String path, boolean from26) {
        try {
            String s = AncientResourceSource.bundledLootTables(from26).readText(path);
            assertTrue(s != null && !s.isEmpty(), "缺少资源: " + path + " (from26=" + from26 + ")");
            return s;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** 池 0 的条目数。 */
    private static int countEntries(String tableJson) {
        return firstPoolEntries(tableJson).size();
    }

    private static java.util.List<?> firstPoolEntries(String tableJson) {
        java.util.Map<?, ?> root = (java.util.Map<?, ?>) AncientJson.parse(tableJson);
        java.util.List<?> pools = (java.util.List<?>) root.get("pools");
        return (java.util.List<?>) ((java.util.Map<?, ?>) pools.get(0)).get("entries");
    }

    /** 池 0 的权重和（缺省权重按 1 计）。 */
    private static int totalWeight(String tableJson) {
        int sum = 0;
        for (Object e : firstPoolEntries(tableJson)) {
            Object w = ((java.util.Map<?, ?>) e).get("weight");
            sum += w == null ? 1 : ((Number) w).intValue();
        }
        return sum;
    }

    /** 跑一座古城：拼图模拟 → 箱子提取 → <b>26.x 表</b> + 生产模式掷骰，输出每箱一行的可读结果。 */
    private static List<String> chestsOf(int chunkX, int chunkZ) {
        AncientResourceSource structures = AncientResourceSource.bundledStructures();
        AncientResourceSource pools = AncientResourceSource.bundledPools();
        AncientResourceSource dataSrc = AncientResourceSource.bundledLootTables(true);
        AncientEnchantData data = AncientEnchantData.load(AncientResourceSource.bundledAncient());

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
            for (var e : totals.entrySet()) {
                if (itemText.length() > 0) itemText.append(';');
                itemText.append(e.getKey()).append(':').append(e.getValue());
            }
            rows.add(c.x() + "," + c.y() + "," + c.z() + "|" + label + "|" + itemText);
        }
        Collections.sort(rows);
        return rows;
    }
}
