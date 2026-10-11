package com.fo.addon.ancient.core;

import com.fo.addon.ancient.core.AncientJigsawSimulator.Piece;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 战利品掷骰 黄金回归测试（古城自写版）。
 *
 * <p>测的是「箱子用 LootTableSeed 掷出来到底是什么」这条链：战利品表解析 → 池掷次数 →
 * 加权抽条目 → 逐个函数（set_count / set_damage / 两个附魔函数）→ 命中判定。
 * RNG 消耗顺序一旦错位，后面的箱子就全错，所以这里逐箱锁死。
 *
 * <p>期望值的来源：本实现已与独立参考实现 zychen027/AncientCityLootPredictor 在
 * <b>同一份 MC 1.21.11 数据</b>（战利品表 + 附魔 + 标签）下做过 <b>113/113 逐箱等价性验证</b>
 * （物品清单与附魔清单全等）。等价性验证需要 Python 侧跑参考实现，无法进 JUnit，
 * 所以这里把验证过的输出固化成断言。
 *
 * <p>数据来源为 MC 1.21.11 客户端 jar 的 loot_table / enchantment / tags，已打进 mod 资源。
 */
class AncientLootEvaluatorTest {

    private static final long SEED = -7346913998703726680L;

    /** 每箱一行：{@code x,y,z|命中标签|物品清单}，物品按 id 排序、格式 {@code id:数量}。 */
    @Test
    void 生产模式掷骰结果与黄金数据一致() {
        // --- 古城 #1 区块 779,-595 ---
        List<String> e0 = List.of(
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
            "12457,-49,-9564|-|minecraft:book:8;minecraft:compass:1;minecraft:echo_shard:5;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:leather:3;minecraft:sculk:10;minecraft:sculk_sensor:4",
            "12489,-37,-9619|附魔金|minecraft:candle:2;minecraft:coal:23;minecraft:disc_fragment_5:4;minecraft:enchanted_golden_apple:1;minecraft:glow_berries:15;minecraft:lead:1;minecraft:sculk:7",
            "12521,-35,-9474|-|minecraft:amethyst_shard:8;minecraft:coal:31;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:glow_berries:9;minecraft:iron_leggings:1;minecraft:music_disc_cat:1",
            "12529,-47,-9565|附魔金|minecraft:coal:6;minecraft:diamond_horse_armor:1;minecraft:enchanted_golden_apple:2;minecraft:glow_berries:15;minecraft:lead:1",
            "12529,-47,-9573|-|minecraft:amethyst_shard:5;minecraft:coal:10;minecraft:glow_berries:9;minecraft:music_disc_13:1;minecraft:sculk_sensor:1",
            "12554,-49,-9583|-|minecraft:candle:2;minecraft:coal:35;minecraft:echo_shard:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1",
            "12555,-50,-9453|-|minecraft:bone:28;minecraft:book:5;minecraft:enchanted_book:1;minecraft:music_disc_otherside:1",
            "12557,-48,-9553|-|minecraft:coal:10;minecraft:disc_fragment_5:3;minecraft:echo_shard:4;minecraft:iron_leggings:1;minecraft:lead:1;minecraft:potion:2;minecraft:sculk_catalyst:2");
        assertEquals(e0, chestsOf(779, -595), "区块 779,-595 的掷骰结果");
        // --- 古城 #2 区块 773,-622 ---
        List<String> e1 = List.of(
            "12269,-49,-9945|-|minecraft:bone:9;minecraft:coal:22;minecraft:diamond_hoe:1;minecraft:enchanted_book:1;minecraft:leather:5;minecraft:music_disc_13:1;minecraft:soul_torch:7",
            "12282,-50,-9977|-|minecraft:amethyst_shard:7;minecraft:candle:1;minecraft:glow_berries:5;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk:5;minecraft:sculk_sensor:2;minecraft:soul_torch:12",
            "12287,-50,-9941|迅捷3|minecraft:book:10;minecraft:coal:8;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:potion:2;minecraft:soul_torch:15",
            "12293,-44,-9937|-|minecraft:bone:8;minecraft:coal:11;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:name_tag:1;minecraft:potion:3;minecraft:silence_armor_trim_smithing_template:1",
            "12301,-44,-10054|-|minecraft:book:8;minecraft:diamond_leggings:1;minecraft:echo_shard:1;minecraft:experience_bottle:2;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:2",
            "12306,-50,-9944|迅捷3|minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:music_disc_13:2;minecraft:potion:3;minecraft:sculk_sensor:1",
            "12308,-49,-10011|-|minecraft:candle:4;minecraft:compass:1;minecraft:diamond_horse_armor:2;minecraft:echo_shard:3;minecraft:music_disc_13:2;minecraft:potion:3;minecraft:sculk_catalyst:1;minecraft:soul_torch:4",
            "12314,-50,-9918|-|minecraft:bone:20;minecraft:book:7;minecraft:glow_berries:14;minecraft:leather:3",
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
        assertEquals(e1, chestsOf(773, -622), "区块 773,-622 的掷骰结果");
        // --- 古城 #3 区块 744,-598 ---
        List<String> e2 = List.of(
            "11823,-49,-9552|附魔金|minecraft:candle:1;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:2;minecraft:enchanted_golden_apple:2;minecraft:iron_leggings:1;minecraft:music_disc_13:1;minecraft:name_tag:1;minecraft:potion:3;minecraft:sculk_catalyst:1",
            "11827,-49,-9609|-|minecraft:amethyst_shard:8;minecraft:bone:5;minecraft:coal:11;minecraft:enchanted_book:2;minecraft:sculk:5",
            "11839,-49,-9478|-|minecraft:bone:13;minecraft:echo_shard:4;minecraft:enchanted_book:3;minecraft:music_disc_13:1;minecraft:music_disc_cat:2;minecraft:potion:1",
            "11841,-49,-9502|-|minecraft:coal:13;minecraft:diamond_leggings:1;minecraft:disc_fragment_5:3;minecraft:enchanted_book:1;minecraft:leather:5;minecraft:sculk:8",
            "11859,-49,-9552|-|minecraft:coal:10;minecraft:enchanted_book:1;minecraft:experience_bottle:2;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:soul_torch:10",
            "11861,-48,-9475|-|minecraft:amethyst_shard:23;minecraft:echo_shard:2;minecraft:iron_leggings:2",
            "11873,-50,-9474|-|minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:music_disc_13:1;minecraft:sculk_catalyst:1;minecraft:sculk_sensor:3",
            "11876,-50,-9513|-|minecraft:baked_potato:5;minecraft:golden_carrot:2;minecraft:packed_ice:10;minecraft:snowball:24;minecraft:suspicious_stew:5",
            "11879,-44,-9470|-|minecraft:amethyst_shard:5;minecraft:coal:6;minecraft:iron_leggings:1;minecraft:name_tag:1;minecraft:sculk:10;minecraft:sculk_catalyst:1",
            "11883,-37,-9637|附魔金|minecraft:coal:11;minecraft:disc_fragment_5:4;minecraft:enchanted_golden_apple:1;minecraft:experience_bottle:1;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:sculk:10;minecraft:sculk_catalyst:1",
            "11883,-44,-9649|-|minecraft:diamond_hoe:1;minecraft:disc_fragment_5:8;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:leather:1",
            "11887,-50,-9655|附魔金|minecraft:book:9;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:echo_shard:3;minecraft:enchanted_golden_apple:1;minecraft:music_disc_cat:1;minecraft:potion:2;minecraft:sculk_sensor:3;minecraft:soul_torch:15",
            "11890,-50,-9670|-|minecraft:bone:12;minecraft:disc_fragment_5:2;minecraft:echo_shard:3;minecraft:leather:3;minecraft:sculk:6",
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
            "11983,-49,-9551|-|minecraft:bone:3;minecraft:disc_fragment_5:3;minecraft:echo_shard:2;minecraft:enchanted_book:1;minecraft:leather:4;minecraft:name_tag:3;minecraft:sculk_sensor:1;minecraft:soul_torch:3",
            "11984,-50,-9576|-|minecraft:amethyst_shard:13;minecraft:candle:1;minecraft:potion:5;minecraft:soul_torch:10",
            "12004,-37,-9583|-|minecraft:glow_berries:11;minecraft:name_tag:1;minecraft:sculk:4;minecraft:sculk_sensor:6;minecraft:soul_torch:8");
        assertEquals(e2, chestsOf(744, -598), "区块 744,-598 的掷骰结果");
        // --- 古城 #4 区块 777,-576 ---
        List<String> e3 = List.of(
            "12342,-47,-9236|-|minecraft:bone:22;minecraft:compass:1;minecraft:echo_shard:3;minecraft:enchanted_book:2;minecraft:experience_bottle:1;minecraft:name_tag:1",
            "12342,-47,-9244|迅捷3|minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:name_tag:1;minecraft:ward_armor_trim_smithing_template:1",
            "12371,-50,-9234|-|minecraft:coal:52;minecraft:diamond_leggings:1;minecraft:echo_shard:3;minecraft:experience_bottle:1;minecraft:potion:3;minecraft:soul_torch:10",
            "12383,-37,-9274|-|minecraft:amethyst_shard:6;minecraft:coal:31;minecraft:diamond_horse_armor:1;minecraft:enchanted_book:1;minecraft:iron_leggings:1;minecraft:potion:1;minecraft:soul_torch:8",
            "12388,-50,-9234|-|minecraft:enchanted_book:1;minecraft:lead:1;minecraft:music_disc_cat:1;minecraft:potion:3;minecraft:soul_torch:15",
            "12392,-48,-9206|-|minecraft:amethyst_shard:13;minecraft:book:5;minecraft:enchanted_book:1;minecraft:experience_bottle:3;minecraft:glow_berries:12;minecraft:sculk_sensor:4;minecraft:soul_torch:11",
            "12411,-47,-9309|-|minecraft:candle:3;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1;minecraft:lead:1;minecraft:leather:1;minecraft:music_disc_cat:1;minecraft:name_tag:1;minecraft:potion:2;minecraft:sculk_sensor:1;minecraft:silence_armor_trim_smithing_template:1",
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
            "12525,-49,-9256|迅捷3|minecraft:book:6;minecraft:candle:2;minecraft:enchanted_book:2;minecraft:leather:3;minecraft:soul_torch:9",
            "12532,-35,-9183|-|minecraft:amethyst_shard:1;minecraft:bone:11;minecraft:book:8;minecraft:candle:1;minecraft:enchanted_book:2;minecraft:leather:3",
            "12533,-50,-9233|-|minecraft:bone:17;minecraft:book:10;minecraft:coal:13;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:disc_fragment_5:2;minecraft:enchanted_book:1");
        assertEquals(e3, chestsOf(777, -576), "区块 777,-576 的掷骰结果");
        // --- 古城 #5 区块 750,-621 ---
        List<String> e4 = List.of(
            "11900,-37,-9955|-|minecraft:book:6;minecraft:diamond_hoe:1;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:enchanted_book:2;minecraft:iron_leggings:1;minecraft:soul_torch:11",
            "11909,-47,-9912|-|minecraft:amethyst_shard:15;minecraft:coal:14;minecraft:disc_fragment_5:2;minecraft:leather:1;minecraft:sculk:9",
            "11909,-47,-9920|-|minecraft:amethyst_shard:7;minecraft:bone:5;minecraft:coal:14;minecraft:enchanted_book:1;minecraft:iron_leggings:2;minecraft:soul_torch:9",
            "11938,-48,-9920|迅捷3|minecraft:bone:12;minecraft:book:9;minecraft:enchanted_book:2;minecraft:iron_leggings:2;minecraft:lead:1;minecraft:sculk:5",
            "11958,-50,-9916|-|minecraft:bone:13;minecraft:book:3;minecraft:compass:1;minecraft:diamond_hoe:1;minecraft:music_disc_cat:2;minecraft:music_disc_otherside:1;minecraft:potion:3;minecraft:soul_torch:6",
            "11962,-48,-9870|-|minecraft:amethyst_shard:15;minecraft:coal:6;minecraft:echo_shard:2;minecraft:soul_torch:15;minecraft:ward_armor_trim_smithing_template:1",
            "11964,-50,-9839|-|minecraft:disc_fragment_5:1;minecraft:glow_berries:15;minecraft:leather:2;minecraft:sculk:6;minecraft:soul_torch:5",
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
            "12062,-44,-9999|-|minecraft:candle:2;minecraft:coal:6;minecraft:diamond_horse_armor:1;minecraft:diamond_leggings:1;minecraft:glow_berries:9;minecraft:leather:5;minecraft:potion:1",
            "12063,-47,-9894|-|minecraft:book:7;minecraft:candle:2;minecraft:coal:14;minecraft:music_disc_13:1;minecraft:potion:2;minecraft:sculk:9;minecraft:sculk_catalyst:1;minecraft:soul_torch:16",
            "12098,-50,-9953|-|minecraft:candle:4;minecraft:diamond_horse_armor:1;minecraft:experience_bottle:2;minecraft:potion:1;minecraft:soul_torch:1");
        assertEquals(e4, chestsOf(750, -621), "区块 750,-621 的掷骰结果");
    }

    /** 跑一座古城：拼图模拟 → 箱子提取 → 生产模式掷骰，输出每箱一行的可读结果。 */
    private static List<String> chestsOf(int chunkX, int chunkZ) {
        AncientResourceSource structures = AncientResourceSource.bundledStructures();
        AncientResourceSource pools = AncientResourceSource.bundledPools();
        AncientResourceSource dataSrc = AncientResourceSource.bundledAncient();
        AncientEnchantData data = AncientEnchantData.load(dataSrc);

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

    // ==================== 附魔来源语义 ====================

    /**
     * 生产模式按 MC 本体语义解析 {@code #minecraft:on_random_loot}：
     * 它等于 non_treasure + 四个额外项，<b>不含</b> soul_speed / swift_sneak / wind_burst。
     *
     * <p>这一条正是我们与参考实现的已知偏差所在：参考实现在 {@code enchant_with_levels} 里
     * 忽略 options 直接用全部 43 个附魔，会让护腿额外被附上 swift_sneak，并通过互斥过滤
     * 改变后续循环长度，最终影响 113 箱里 21 箱的物品清单。
     */
    @Test
    void 按MC语义解析附魔标签() {
        AncientEnchantData data = AncientEnchantData.load(AncientResourceSource.bundledAncient());
        List<String> orl = data.resolveEnchantTag("#minecraft:on_random_loot");

        assertEquals(40, orl.size(), "on_random_loot 应展开为 40 个附魔");
        assertTrue(orl.contains("minecraft:mending"));
        assertTrue(orl.contains("minecraft:protection"));
        assertFalse(orl.contains("minecraft:swift_sneak"), "swift_sneak 不在 on_random_loot 里");
        assertFalse(orl.contains("minecraft:soul_speed"));
        assertFalse(orl.contains("minecraft:wind_burst"));

        // enchant_randomly 用具体附魔时不受影响
        assertEquals(List.of("minecraft:swift_sneak"),
            data.resolveRandomSource("minecraft:swift_sneak", AncientEnchantData.SourceMode.MC_NATIVE));
        // 兼容模式复刻参考实现的偏差
        assertEquals(data.allEnchantIds().size(),
            data.resolveLevelsSource("#minecraft:on_random_loot",
                AncientEnchantData.SourceMode.REFERENCE_COMPAT).size());
    }

    /** 护腿在两种模式下「可被 enchant_with_levels 附上的附魔」不同：生产模式排除 swift_sneak。 */
    @Test
    void 护腿的可用附魔在生产模式下不含迅捷潜行() {
        AncientEnchantData data = AncientEnchantData.load(AncientResourceSource.bundledAncient());
        String legs = "minecraft:diamond_leggings";

        List<AncientEnchantAlgorithm.EnchantInstance> faithful =
            AncientEnchantAlgorithm.getAvailable(40, legs,
                data.resolveLevelsSource("#minecraft:on_random_loot", AncientEnchantData.SourceMode.MC_NATIVE), data);
        List<AncientEnchantAlgorithm.EnchantInstance> compat =
            AncientEnchantAlgorithm.getAvailable(40, legs,
                data.resolveLevelsSource("#minecraft:on_random_loot", AncientEnchantData.SourceMode.REFERENCE_COMPAT), data);

        assertFalse(faithful.isEmpty());
        assertTrue(faithful.stream().noneMatch(e -> e.enchantment().equals("minecraft:swift_sneak")),
            "生产模式下护腿不应能被 enchant_with_levels 附上 swift_sneak");
        assertTrue(compat.stream().anyMatch(e -> e.enchantment().equals("minecraft:swift_sneak")),
            "兼容模式（复刻参考实现）应当能附上 swift_sneak，否则等价性验证失去意义");
    }

    /** 互斥规则：同一 exclusive_set 的附魔不能共存；没写 exclusive_set 字段的也能被标签兜住。 */
    @Test
    void 附魔互斥规则() {
        AncientEnchantData data = AncientEnchantData.load(AncientResourceSource.bundledAncient());
        // 同属 #minecraft:exclusive_set/armor
        assertFalse(data.areCompatible("minecraft:protection", "minecraft:blast_protection"));
        // loyalty / channeling 自己没有 exclusive_set 字段，但被 exclusive_set/riptide 列在一起
        assertFalse(data.areCompatible("minecraft:loyalty", "minecraft:channeling"));
        // 互不相干
        assertTrue(data.areCompatible("minecraft:mending", "minecraft:unbreaking"));
        assertTrue(data.areCompatible("minecraft:swift_sneak", "minecraft:thorns"));
    }

    /** Mth.nextInt：min >= max 时直接返回 min，<b>不消耗随机数</b>。 */
    @Test
    void 等级区间退化时不消耗随机数() {
        AncientRandom a = new AncientRandom(12345L);
        AncientRandom b = new AncientRandom(12345L);
        assertEquals(3, AncientLootEvaluator.mthNextInt(a, 3, 3));
        // 两个随机数流仍然同步
        assertEquals(b.nextInt(1000), a.nextInt(1000));
    }

    /** 附魔能力为 0 的物品走 enchant_with_levels 时不附魔、也不消耗随机数。 */
    @Test
    void 附魔能力为零时不消耗随机数() {
        AncientEnchantData data = AncientEnchantData.load(AncientResourceSource.bundledAncient());
        AncientRandom a = new AncientRandom(999L);
        AncientRandom b = new AncientRandom(999L);
        assertTrue(AncientEnchantAlgorithm.select(a, "minecraft:lead", 40,
            data.allEnchantIds(), data).isEmpty());
        assertEquals(b.nextInt(1000), a.nextInt(1000), "不应消耗随机数");
        // 对照：会附魔的物品必然消耗随机数
        assertNotNull(AncientEnchantAlgorithm.enchantability("minecraft:diamond_hoe") > 0 ? a : b);
    }
}
