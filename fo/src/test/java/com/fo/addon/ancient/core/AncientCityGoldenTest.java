package com.fo.addon.ancient.core;

import com.fo.addon.ancient.core.AncientJigsawSimulator.Piece;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 古城 Jigsaw 拼图 + 箱子提取 黄金回归测试（古城自写版）。
 *
 * <p>期望值由 <b>独立参考实现</b> zychen027/AncientCityLootPredictor（纯 Python，
 * 对 MC 反编译的 Jigsaw/RNG 复刻）在种子 -7346913998703726680
 * 下逐座跑出，共 5 座古城 / 113 个箱子，
 * 断言拼图块数、箱子坐标、LootTableSeed、战利品表四项全等。
 *
 * <p>注意：本测试只用纯 Java 管线（数据从 mod 资源里读），<b>不依赖黑盒 jar</b>，
 * 因此在 V6.8 删除 fo-ancient-blackbox.jar 之后依然有效。
 *
 * <p>数据来源为 MC 1.21.11 客户端 jar 的 {@code data/minecraft/structure} 与
 * {@code data/minecraft/worldgen/template_pool}，已打进 mod 资源。
 */
class AncientCityGoldenTest {

    private static final long SEED = -7346913998703726680L;

    /** 五座已知古城：区块坐标 + 拼图块数 + 箱子清单（已按字符串序排序）。 */
    @Test
    void 拼图块数与箱子清单与参考实现完全一致() {
        // --- 古城 #1 区块 779,-595 ---
        CityResult c0 = run(779, -595);
        assertEquals(89, c0.pieces, "区块 779,-595 的拼图块数");
        List<String> e0 = List.of(
            "12383,-48,-9504,8962348584094388250,minecraft:chests/ancient_city",
            "12390,-47,-9496,-2548970115727262605,minecraft:chests/ancient_city",
            "12390,-47,-9504,1642153455105469325,minecraft:chests/ancient_city",
            "12397,-35,-9538,1895550268209951196,minecraft:chests/ancient_city",
            "12398,-35,-9556,8851109994229509156,minecraft:chests/ancient_city",
            "12410,-44,-9603,3161147184385622565,minecraft:chests/ancient_city",
            "12416,-50,-9599,-7262651982542534347,minecraft:chests/ancient_city",
            "12438,-49,-9531,-8099118738100942837,minecraft:chests/ancient_city",
            "12450,-50,-9596,-6627092283917617466,minecraft:chests/ancient_city",
            "12456,-49,-9442,4100845982551623202,minecraft:chests/ancient_city",
            "12457,-49,-9564,4060476063951031587,minecraft:chests/ancient_city",
            "12489,-37,-9619,1353090470267301092,minecraft:chests/ancient_city",
            "12521,-35,-9474,9005479349391695024,minecraft:chests/ancient_city",
            "12529,-47,-9565,-8347343292245183560,minecraft:chests/ancient_city",
            "12529,-47,-9573,-8050814977725338628,minecraft:chests/ancient_city",
            "12554,-49,-9583,-4753687156001197518,minecraft:chests/ancient_city",
            "12555,-50,-9453,4904528503631122891,minecraft:chests/ancient_city",
            "12557,-48,-9553,-6358192030000065265,minecraft:chests/ancient_city");
        assertEquals(e0, c0.chests, "区块 779,-595 的箱子清单");
        // --- 古城 #2 区块 773,-622 ---
        CityResult c1 = run(773, -622);
        assertEquals(100, c1.pieces, "区块 773,-622 的拼图块数");
        List<String> e1 = List.of(
            "12269,-49,-9945,364798834676302702,minecraft:chests/ancient_city",
            "12282,-50,-9977,-3171595999694690741,minecraft:chests/ancient_city",
            "12287,-50,-9941,1751198006234903135,minecraft:chests/ancient_city",
            "12293,-44,-9937,1222724324521831343,minecraft:chests/ancient_city",
            "12301,-44,-10054,-6678179785620355255,minecraft:chests/ancient_city",
            "12306,-50,-9944,-5983630794929422325,minecraft:chests/ancient_city",
            "12308,-49,-10011,8528534322837150848,minecraft:chests/ancient_city",
            "12314,-50,-9918,2413892700773340936,minecraft:chests/ancient_city",
            "12330,-49,-9893,4555861481698903161,minecraft:chests/ancient_city",
            "12347,-47,-10045,6094853013046247273,minecraft:chests/ancient_city",
            "12347,-47,-10053,4556685057069759349,minecraft:chests/ancient_city",
            "12350,-35,-9868,-7494485315885952776,minecraft:chests/ancient_city",
            "12356,-50,-9850,8924428058222666246,minecraft:chests/ancient_city",
            "12384,-48,-9872,8879402849160141607,minecraft:chests/ancient_city",
            "12389,-37,-9883,-2050111074599991869,minecraft:chests/ancient_city",
            "12397,-44,-10014,1052204327211578398,minecraft:chests/ancient_city",
            "12411,-49,-9944,1547006808603794565,minecraft:chests/ancient_city",
            "12412,-50,-9969,-3877632347802421147,minecraft:chests/ancient_city",
            "12415,-50,-10003,-5146425370373316532,minecraft:chests/ancient_city",
            "12422,-47,-9933,-8998563231355323934,minecraft:chests/ancient_city",
            "12422,-47,-9941,1101329398247034235,minecraft:chests/ancient_city",
            "12432,-50,-10003,1736203479294470605,minecraft:chests/ancient_city",
            "12432,-50,-9977,-2778840897164225169,minecraft:chests/ancient_city",
            "12447,-48,-10005,-2927519783017542174,minecraft:chests/ancient_city",
            "12447,-49,-9944,-461877625739414896,minecraft:chests/ancient_city",
            "12449,-49,-9968,-5157201221073994605,minecraft:chests/ancient_city");
        assertEquals(e1, c1.chests, "区块 773,-622 的箱子清单");
        // --- 古城 #3 区块 744,-598 ---
        CityResult c2 = run(744, -598);
        assertEquals(101, c2.pieces, "区块 744,-598 的拼图块数");
        List<String> e2 = List.of(
            "11823,-49,-9552,219390690001774937,minecraft:chests/ancient_city",
            "11827,-49,-9609,7046353668469979592,minecraft:chests/ancient_city",
            "11839,-49,-9478,8932375714561325875,minecraft:chests/ancient_city",
            "11841,-49,-9502,-5040566145876316551,minecraft:chests/ancient_city",
            "11859,-49,-9552,6563531960331950444,minecraft:chests/ancient_city",
            "11861,-48,-9475,3666006767457986647,minecraft:chests/ancient_city",
            "11873,-50,-9474,4510560808247414383,minecraft:chests/ancient_city",
            "11876,-50,-9513,-1099409664699616964,minecraft:chests/ancient_city_ice_box",
            "11879,-44,-9470,-1786764844680840595,minecraft:chests/ancient_city",
            "11883,-37,-9637,1630886731039122413,minecraft:chests/ancient_city",
            "11883,-44,-9649,1533821212055932568,minecraft:chests/ancient_city",
            "11887,-50,-9655,-4982193574695713533,minecraft:chests/ancient_city",
            "11890,-50,-9670,4788252643087057482,minecraft:chests/ancient_city",
            "11891,-49,-9618,1686510846197279917,minecraft:chests/ancient_city",
            "11915,-49,-9652,6429298767707686513,minecraft:chests/ancient_city",
            "11918,-47,-9609,719752986156324950,minecraft:chests/ancient_city",
            "11923,-37,-9633,6836627288323776254,minecraft:chests/ancient_city",
            "11926,-47,-9609,-5699552471290609661,minecraft:chests/ancient_city",
            "11931,-49,-9518,220512840597906608,minecraft:chests/ancient_city",
            "11944,-48,-9578,4764389676181596432,minecraft:chests/ancient_city",
            "11948,-50,-9550,-6291327477624344904,minecraft:chests/ancient_city",
            "11951,-50,-9507,-2706818863355847361,minecraft:chests/ancient_city_ice_box",
            "11955,-48,-9475,-294063857503823754,minecraft:chests/ancient_city",
            "11965,-50,-9580,7438956947436357804,minecraft:chests/ancient_city_ice_box",
            "11968,-49,-9518,-9111035063673740542,minecraft:chests/ancient_city",
            "11983,-49,-9551,-3660254669160711708,minecraft:chests/ancient_city",
            "11984,-50,-9576,-3063626434643612658,minecraft:chests/ancient_city",
            "12004,-37,-9583,-6596748313756587137,minecraft:chests/ancient_city");
        assertEquals(e2, c2.chests, "区块 744,-598 的箱子清单");
        // --- 古城 #4 区块 777,-576 ---
        CityResult c3 = run(777, -576);
        assertEquals(87, c3.pieces, "区块 777,-576 的拼图块数");
        List<String> e3 = List.of(
            "12342,-47,-9236,4725668632644930514,minecraft:chests/ancient_city",
            "12342,-47,-9244,1040324672910163182,minecraft:chests/ancient_city",
            "12371,-50,-9234,4925251741155766450,minecraft:chests/ancient_city",
            "12383,-37,-9274,-6610011970654012460,minecraft:chests/ancient_city",
            "12388,-50,-9234,-9105679966385054818,minecraft:chests/ancient_city",
            "12392,-48,-9206,6562271835198547586,minecraft:chests/ancient_city",
            "12411,-47,-9309,-6464517464751893128,minecraft:chests/ancient_city",
            "12411,-47,-9317,2745020956392278486,minecraft:chests/ancient_city",
            "12451,-49,-9157,6300334649370006265,minecraft:chests/ancient_city",
            "12459,-49,-9282,433478857581118881,minecraft:chests/ancient_city",
            "12467,-47,-9309,-2576774057457877979,minecraft:chests/ancient_city",
            "12467,-47,-9317,456727280213696709,minecraft:chests/ancient_city",
            "12474,-50,-9204,-1394976509671272293,minecraft:chests/ancient_city",
            "12478,-50,-9307,8510248956241890302,minecraft:chests/ancient_city",
            "12480,-44,-9200,-7562586612082910004,minecraft:chests/ancient_city",
            "12485,-50,-9147,1205146955576337200,minecraft:chests/ancient_city",
            "12496,-50,-9281,4343031984939576530,minecraft:chests/ancient_city",
            "12497,-49,-9306,3257705995745725368,minecraft:chests/ancient_city",
            "12525,-49,-9256,3092755353522680231,minecraft:chests/ancient_city",
            "12532,-35,-9183,-751500891969157612,minecraft:chests/ancient_city",
            "12533,-50,-9233,-4319403390584461556,minecraft:chests/ancient_city");
        assertEquals(e3, c3.chests, "区块 777,-576 的箱子清单");
        // --- 古城 #5 区块 750,-621 ---
        CityResult c4 = run(750, -621);
        assertEquals(92, c4.pieces, "区块 750,-621 的拼图块数");
        List<String> e4 = List.of(
            "11900,-37,-9955,1916565042207515788,minecraft:chests/ancient_city",
            "11909,-47,-9912,-6784946836640188158,minecraft:chests/ancient_city",
            "11909,-47,-9920,-926884192473044146,minecraft:chests/ancient_city",
            "11938,-48,-9920,-1970722875135545680,minecraft:chests/ancient_city",
            "11958,-50,-9916,3238468042834292184,minecraft:chests/ancient_city",
            "11962,-48,-9870,-6939998393217544080,minecraft:chests/ancient_city",
            "11964,-50,-9839,7551455793994027363,minecraft:chests/ancient_city",
            "11970,-44,-9835,-2006122004995815184,minecraft:chests/ancient_city",
            "11989,-48,-9888,1640739666899395606,minecraft:chests/ancient_city",
            "11991,-50,-9874,546171205579898795,minecraft:chests/ancient_city",
            "11993,-49,-10034,-7304423500486447187,minecraft:chests/ancient_city",
            "12018,-50,-9998,-1398289429145588957,minecraft:chests/ancient_city",
            "12024,-37,-9872,3132301720822673555,minecraft:chests/ancient_city",
            "12024,-50,-9842,5107951858920913489,minecraft:chests/ancient_city",
            "12037,-50,-9839,8038261298355853431,minecraft:chests/ancient_city",
            "12043,-44,-9835,-6756907188872794837,minecraft:chests/ancient_city",
            "12055,-47,-9894,-8841182736740503441,minecraft:chests/ancient_city",
            "12062,-44,-9999,-5446114827114507222,minecraft:chests/ancient_city",
            "12063,-47,-9894,-6780887649423963087,minecraft:chests/ancient_city",
            "12098,-50,-9953,2150332400736627716,minecraft:chests/ancient_city");
        assertEquals(e4, c4.chests, "区块 750,-621 的箱子清单");
    }

    /** 跑一座古城的纯 Java 管线：拼图模拟 → 箱子提取。 */
    private static CityResult run(int chunkX, int chunkZ) {
        AncientResourceSource structures = AncientResourceSource.bundledStructures();
        AncientResourceSource pools = AncientResourceSource.bundledPools();
        List<Piece> pieces = AncientJigsawSimulator.simulate(SEED, chunkX, chunkZ, structures, pools);
        List<AncientChestExtractor.Chest> chests =
            AncientChestExtractor.extract(pieces, SEED, structures);
        List<String> rows = new ArrayList<>();
        for (AncientChestExtractor.Chest c : chests) {
            rows.add(c.x() + "," + c.y() + "," + c.z() + "," + c.seed() + "," + c.lootTable());
        }
        Collections.sort(rows);
        return new CityResult(pieces.size(), rows);
    }

    private record CityResult(int pieces, List<String> chests) {
    }
}
