package com.fo.addon.elytra.modules;

import com.fo.addon.elytra.FOElytraModule;
import com.fo.addon.elytra.core.BaritoneHook;
import com.fo.addon.elytra.core.BlockBreaker;
import com.fo.addon.elytra.core.EatController;
import com.fo.addon.elytra.core.FindPathToOpen;
import com.fo.addon.elytra.core.FireballDeflector;
import com.fo.addon.elytra.core.FoodPriority;
import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.InventoryRestocker;
import com.fo.addon.elytra.core.InvHelper;
import com.fo.addon.elytra.core.ItemHelper;
import com.fo.addon.elytra.core.LavaEscape;
import com.fo.addon.elytra.core.LavaPredictor;
import com.fo.addon.elytra.core.MendTask;
import com.fo.addon.elytra.core.Needs;
import com.fo.addon.elytra.core.PlayerAction;
import com.fo.addon.elytra.core.SettingHelper;
import com.fo.addon.elytra.core.StuckEscape;
import com.fo.addon.elytra.core.SupplyOptions;
import com.fo.addon.elytra.core.TerrainProbe;
import com.fo.addon.elytra.core.SupplyTask;
import com.fo.addon.elytra.core.TaskStatus;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoElytraFlight extends FOElytraModule {

    public enum Mode {

        Waypoints("按航点列表"),

        SingleTarget("单一坐标"),

        Direction("朝向方向");

        public final String label;

        Mode(String label) {
            this.label = label;
        }

        /** 前端 UI/下拉框/提示均显示中文（Meteor EnumSetting 走 toString，必须覆写否则显示英文枚举名） */
        @Override
        public String toString() {
            return label;
        }
    }

    public enum State {
        IDLE("空闲"),
        PREPARE("准备"),
        TAKEOFF("起飞"),
        FLYING("飞行中"),
        LANDING("降落"),
        SUPPLY("补给"),
        MEND("修鞘翅"),
        DONE("完成"),
        FAILED("失败");

        public final String label;

        State(String label) {
            this.label = label;
        }

        /** 前端 UI/提示均显示中文（模块列表、日志、聊天输出都会打印这个状态） */
        @Override
        public String toString() {
            return label;
        }
    }

    private enum TakeoffPhase {

        INIT("初始化"),

        LAUNCH("原地起跳"),

        CLEAR_HEAD("清除头顶障碍"),

        FLY_TO_OPEN("飞向开阔地"),

        ASCEND("爬升"),

        WAIT_ARRIVE("等待 Baritone 接管");

        public final String label;

        TakeoffPhase(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final SettingGroup sgTarget = settings.createGroup("目标");
    private final SettingGroup sgFlight = settings.createGroup("飞行");
    private final SettingGroup sgBaritone = settings.createGroup("Baritone 飞行");
    private final SettingGroup sgFireball = settings.createGroup("反击火球");
    private final SettingGroup sgLava = settings.createGroup("逃离岩浆");

    private final SettingGroup sgLavaPredict = settings.createGroup("岩浆预测（实验性）");
    private final SettingGroup sgSupplyTrigger = settings.createGroup("补给触发");
    private final SettingGroup sgSupplyAmount = settings.createGroup("补给数量");
    private final SettingGroup sgSupplyExec = settings.createGroup("补给执行");
    private final SettingGroup sgEat = settings.createGroup("自动进食");
    private final SettingGroup sgMend = settings.createGroup("修鞘翅");
    private final SettingGroup sgSafety = settings.createGroup("安全");
    private final SettingGroup sgTerrain = settings.createGroup("地形绕开");
    private final SettingGroup sgLandSafety = settings.createGroup("降落安全");
    private final SettingGroup sgDebug = settings.createGroup("调试");

    private final EnumSetting<Mode> mode = SettingHelper.enum_(sgTarget, "模式","Waypoints=按航点列表顺序飞；SingleTarget=只飞一个坐标；Direction=朝启动时的视角方向跑图。",
        Mode.Waypoints);

    private final StringListSetting waypoints = SettingHelper.stringList(sgTarget, "航点列表",
        "每行一个坐标，格式 x,z（也接受 x z 或 x, z）。",
        List.of("1000,1000", "1000,-1000", "-1000,-1000"));

    private final IntSetting targetX = SettingHelper.intRaw(sgTarget, "目标 X","SingleTarget 模式的 X 坐标。默认 0。", 0, -30000000, 30000000);

    private final IntSetting targetZ = SettingHelper.intRaw(sgTarget, "目标 Z","SingleTarget 模式的 Z 坐标。默认 0。", 0, -30000000, 30000000);

    private final IntSetting segmentDistance = SettingHelper.intRaw(sgTarget, "单段距离","定向跑图每一段推进的距离（格）。默认 2000。",
        2000, 100, 100000);

    private final IntSetting arriveRadius = SettingHelper.int_(sgTarget, "到达判定半径","Baritone 停下后与目标的水平距离小于该值才算「到达」，否则记为一次失败段。默认 32。", 32, 4, 512);

    private final BoolSetting loop = SettingHelper.bool(sgTarget, "循环航点","航点列表飞完后从头再来。默认开。", true);

    private static String headingName(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        if (y < 45 || y >= 315) return "正南 +Z";
        if (y < 135) return "正西 -X";
        if (y < 225) return "正北 -Z";
        return "正东 +X";
    }

    private final BoolSetting autoTakeoff = SettingHelper.bool(sgFlight, "自动起飞","本插件自己起跳并展开鞘翅（做法：原地跳两下 → 头顶挡了就挖开 → 再不行就找开阔航线）。默认开。", true);

    private final IntSetting takeoffTimeout = SettingHelper.int_(sgFlight, "起飞看门狗","整个起飞流程（原地起跳 → 清障 → 找开阔航线 → 飞往开阔地）最多跑多少 tick 就判失败，20 tick = 1 秒。默认 120。", 120, 20, 1200);

    private final BoolSetting takeoffAutoJumpFallback = SettingHelper.bool(sgFlight, "起飞失败交给 Baritone","本插件的起飞流程（原地起跳 + 开阔地搜索）全部失败后，临时把 Baritone 的 elytraAutoJump 打开再试一次。默认开。",
        true);

    private final DoubleSetting takeoffPitch = SettingHelper.double_(sgFlight, "起飞俯仰角","起跳前看向的角度，-30 是设计值（略微抬头，起跳后容易展开）。默认 -30.0。", -30.0, -90.0, 0.0);

    private final BoolSetting openAreaSearch = SettingHelper.bool(sgFlight, "开阔地搜索起飞","原地起跳失败时，用 512 个方向找一条开阔航线飞过去。默认开。", true);

    private final DoubleSetting openSearchDist = SettingHelper.double_(sgFlight, "开阔地搜索距离","航线搜索往前推多少格（默认 25）。", 25.0, 5.0, 64.0);

    private final DoubleSetting openSafeDist = SettingHelper.double_(sgFlight, "开阔地安全距离",
        "评分时每条射线最多看多远，越大越偏好「大空地」（默认 20）。", 20.0, 4.0, 48.0);

    private final IntSetting openTries = SettingHelper.int_(sgFlight, "飞往开阔地尝试次数",
        "朝开阔地来回冲几次还没让 Baritone 接管就判起飞失败（默认 6）。", 6, 1, 12);

    private final BoolSetting allowAscend = SettingHelper.bool(sgFlight, "允许先抬升再找航线","四周都被挡死时先抬头垂直上升，从 +3.0 格一路试到 +10.5 格（每 0.5 格一次）再重新找航线。默认开。", true);

    private final IntSetting jumpBeforeOpen = SettingHelper.int_(sgFlight, "原地起跳尝试次数",
        "站在地上起跳几次还没展开鞘翅，就转去「飞往开阔地」（默认 3 次）。", 3, 1, 10);

    private final BoolSetting clearHeadBlock = SettingHelper.bool(sgFlight, "头顶障碍自动清除","起跳前头顶有方块挡着时，让 Baritone 把头顶 2×2×2 挖开再起跳（清障做法）。默认开。", true);

    private final BoolSetting fireworkRefill = SettingHelper.bool(sgFlight, "自动补充快捷栏烟花","快捷栏烟花少于阈值时，从背包把整摞烟花换到快捷栏（不开界面，直接发包）。默认开。", true);

    private final IntSetting fireworkHotbarMin = SettingHelper.int_(sgFlight, "快捷栏烟花阈值","快捷栏（9 格）里的烟花少于这个数量就从背包补充。默认 8。", 8, 1, 64);

    private final BoolSetting takeoffFirework = SettingHelper.bool(sgFlight, "起飞后立刻放烟花","起飞成功后立刻补一发烟花给推力。默认开。", true);

    private final BoolSetting chunkWait = SettingHelper.bool(sgFlight, "区块加载等待","未加载区块比例过高时暂停 Baritone 原地盘旋，等区块追上来再继续，避免撞进未加载地形。默认开。", true);

    private final DoubleSetting unloadedRatio = SettingHelper.double_(sgFlight, "未加载比例阈值",
        "视野范围内未加载区块占比超过该值就进入等待。默认 0.4。", 0.4, 0.05, 1.0);

    private final IntSetting chunkRadius = SettingHelper.int_(sgFlight, "区块检查半径","检查周围多少区块的加载状态（会被客户端视距上限限制）。默认 5。", 5, 1, 12);

    private final IntSetting hoverFirework = SettingHelper.int_(sgFlight, "盘旋补烟花间隔","等待区块 / 拦截火球 / 让行玩家时，每隔多少 tick 补一发烟花避免掉高度。默认 40。", 40, 0, 200);

    private final IntSetting hoverTimeout = SettingHelper.int_(sgFlight, "等待区块超时","「未加载区块太多」而暂停飞行最多持续多少 tick，超时强制恢复飞行（20 tick = 1 秒）。默认 1200。", 1200, 100, 12000);

    private final BoolSetting stuckFix = SettingHelper.bool(sgFlight, "卡住自救","定期检查是否原地绕圈/停滞，必要时重置 Baritone 鞘翅进程。默认开。", true);

    private final BoolSetting stuckEscape = SettingHelper.bool(sgFlight, "卡住检测与脱离","撞墙/卡天花板后水平速度几乎为零、位置不动就算卡住：停烟花 → Baritone 走出去 → 对准开口再飞。默认开。", true);

    private final IntSetting stuckHoldTicks = SettingHelper.int_(sgFlight, "卡住判定 tick","连续这么多 tick 没推进就算卡住。默认 40。", 40, 10, 400);

    private final DoubleSetting stuckSpeedThreshold = SettingHelper.double_(sgFlight, "卡住速度阈值","水平速度低于该值算「没在推进」。默认 0.08。", 0.08, 0.01, 1.0);

    private final DoubleSetting stuckMoveThreshold = SettingHelper.double_(sgFlight, "卡住位移阈值","20 tick 内水平位移小于该值算「没在推进」（格）。默认 1.0。", 1.0, 0.2, 10.0);

    private final DoubleSetting stuckHealthGuard = SettingHelper.double_(sgFlight, "卡住时血量保护","卡住期间血量掉到这个值以下就停止推进并找地方落地。默认 12.0。", 12.0, 1.0, 20.0);

    private final BoolSetting avoidCaves = SettingHelper.bool(sgTerrain, "绕开下方洞穴","前方或下方探到洞穴/峡谷就插临时航点绕开（偏航向或抬高）。默认开。", true);

    private final IntSetting probeDepth = SettingHelper.int_(sgTerrain, "探测深度","往下探多少格判断是不是洞穴。默认 24。", 24, 8, 48);

    private final IntSetting probeInterval = SettingHelper.int_(sgTerrain, "探测间隔 tick","每隔这么多 tick 探一次地形。默认 20。", 20, 5, 200);

    private final IntSetting probeDistance = SettingHelper.int_(sgTerrain, "前方探测距离","沿当前航向往前探多远（按 1/3、2/3、全长取三点）。默认 60。", 60, 20, 200);

    private final IntSetting landSafeRadius = SettingHelper.int_(sgLandSafety, "降落安全半径","降落点这个半径内有敌对生物就不落。默认 8。", 8, 2, 48);

    private final BoolSetting landAvoidMobs = SettingHelper.bool(sgLandSafety, "周围有怪就换降落点","候选降落点按距离排序，跳过半径内有怪的；全都有怪就继续飞。默认开。", true);

    private final BoolSetting landSkipWhenCrowded = SettingHelper.bool(sgLandSafety, "怪物太多就跳过这次补给","24 格内敌对生物 ≥ 5 只就不降落，直接继续飞。默认开。", true);

    private final BoolSetting hurtAbortSupply = SettingHelper.bool(sgLandSafety, "被打就中断补给起飞","补给中受到伤害就中止补给、清现场、立刻起飞，这次补给 30 秒内不再试。默认开。", true);

    private final BoolSetting torchBeforeSupply = SettingHelper.bool(sgLandSafety, "补给前先插火把","降落点脚下放一支火把或灯笼降低刷怪（背包里有时）。默认关。", false);

    private final IntSetting stuckTicks = SettingHelper.int_(sgFlight, "停滞检测间隔","多少 tick 检查一次位移（20 tick = 1 秒）。默认 400。", 400, 100, 2400);

    private final DoubleSetting stuckDistance = SettingHelper.double_(sgFlight, "停滞判定距离","一个检测周期内水平位移小于该值就算停滞（格）。默认 25.0。", 25.0, 3.0, 200.0);

    private final BoolSetting pauseOnPlayers = SettingHelper.bool(sgFlight, "有玩家时让行","附近有别的玩家时暂停飞行原地盘旋，玩家走远后自动继续。默认关。", false);

    private final DoubleSetting playerRange = SettingHelper.double_(sgFlight, "让行距离","触发让行的玩家距离（格）。默认 64.0。", 64.0, 8.0, 256.0);

    private final BoolSetting infinityElytra = SettingHelper.bool(sgFlight, "无尽鞘翅（每 12 tick 重发）","无尽鞘翅模式：每 12 tick 重新展开一次鞘翅。默认关。", false);

    private final BoolSetting btTermsAccepted = btBool("elytraTermsAccepted", "同意鞘翅条款",
        "Baritone 的 elytraTermsAccepted：不同意时鞘翅进程拒绝工作。");

    private final BoolSetting btAutoJump = btBool("elytraAutoJump", "自动起跳",
        "交给 Baritone 起跳：它会先找一条「走到某个能往下跳的台阶」的步行路线，平原/室内会直接报 "
            + "Failed to compute a walking path to a spot to jump off from 并拒绝起飞（日志里那句提示就是它）。"
            + "这一项默认就是关着的：本插件在跑图时会强制压掉它，起跳由自己完成（原地跳两下 → 头顶挡了就挖开 → "
            + "512 个方向找开阔航线）；只有「起飞失败交给 Baritone」兜底触发时才临时打开。"
            + "点「保存并设为默认」会把当前值写进 baritone/settings.txt，建议保持关闭。");

    private final DoubleSetting btFireworkSpeed = btDouble("elytraFireworkSpeed", "烟花速度",
        "鞘翅烟花的最低速度要求：越小越省烟花、越大越快。Baritone 出厂默认 1.2；这里的默认值 0.5（更省烟花）。",
        0.05, 2.0);

    private final BoolSetting btConserveFireworks = btBool("elytraConserveFireworks", "节省烟花",
        "尽量避免用烟花（能滑翔就不放），赶路速度会变慢。");

    private final BoolSetting btAutoSwap = btBool("elytraAutoSwap", "自动换取鞘翅",
        "鞘翅耐久不够时自动换背包里的备用鞘翅。");

    private final BoolSetting btPredictTerrain = btBool("elytraPredictTerrain", "预测地形",
        "按地形高度预测路线（下界/峡谷飞行时很有用，关掉更容易撞地形）。");

    private final BoolSetting btFreeLook = btBool("elytraFreeLook", "自由视角",
        "飞行时允许视角与前进方向分离（开着更像原版鞘翅手感）。");

    private final BoolSetting btSmoothLook = btBool("elytraSmoothLook", "平滑视角",
        "平滑过渡视角（关掉会让视角更硬更快）。");

    private final IntSetting btPitchRange = btInt("elytraPitchRange", "俯仰范围",
        "允许的俯仰角变化范围（度）。", 0, 180);

    private final IntSetting btSimulationTicks = btInt("elytraSimulationTicks", "模拟 tick 数",
        "每次决策向前模拟多少 tick；越大越聪明也越吃 CPU。", 1, 100);

    private final DoubleSetting btMinimumAvoidance = btDouble("elytraMinimumAvoidance", "最小规避强度",
        "遇到障碍时至少偏移多少，越大越保守。", 0.0, 10.0);

    private final BoolSetting btAllowEmergencyLand = btBool("elytraAllowEmergencyLand", "允许紧急降落",
        "没烟花/耐久不够时允许 Baritone 紧急降落；无限鞘翅玩法可以关掉。");

    private final IntSetting btMinFireworksBeforeLanding = btInt("elytraMinFireworksBeforeLanding", "降落前最少烟花",
        "剩余烟花少于这个数量时不再尝试远距离飞行。", 0, 256);

    private final IntSetting btMinimumDurability = btInt("elytraMinimumDurability", "最低鞘翅耐久",
        "鞘翅剩余耐久低于这个值就准备降落（配合自动换鞘翅使用）。", 0, 1000);

    private final BoolSetting btAllowLandOnNetherFortress = btBool("elytraAllowLandOnNetherFortress", "允许落在下界要塞",
        "是否允许把下界要塞当降落点（要塞上有烈焰人，谨慎打开）。");

    private final BoolSetting btChatSpam = btBool("elytraChatSpam", "Baritone 聊天刷屏",
        "让 Baritone 把飞行决策打印到聊天栏；想要干净聊天栏就关掉。");

    private final BoolSetting btRenderSimulation = btBool("elytraRenderSimulation", "渲染模拟路径",
        "把 Baritone 的飞行模拟画出来（纯调试用，正式跑图建议关掉）。");

    private final KeybindSetting baritoneSaveKey = SettingHelper.keybind(sgBaritone, "一键保存快捷键",
        "按下 = 应用面板里的 Baritone 设置并保存为默认（等价于点面板上的「保存并设为默认」）。");

    private WLabel baritoneStatusLabel;

    private WLabel logPathLabel;
    private boolean baritoneKeyWasPressed;

    private BoolSetting btBool(String btName, String label, String desc) {
        boolean def = BaritoneHook.btDefaultBool(btName, false);
        return sgBaritone.add(new BoolSetting.Builder()
            .name(label)
            .description(desc + "  [Baritone: " + btName + "]")
            .defaultValue(def)
            .onChanged(v -> BaritoneHook.btSet(btName, v))
            .onModuleActivated(s -> s.set(BaritoneHook.btBool(btName, def)))
            .build());
    }

    private IntSetting btInt(String btName, String label, String desc, int min, int max) {
        int def = clamp(BaritoneHook.btDefaultInt(btName, min), min, max);
        return sgBaritone.add(new IntSetting.Builder()
            .name(label)
            .description(desc + "  [Baritone: " + btName + "]")
            .defaultValue(def)
            .min(min).max(max).sliderRange(min, max)
            .onChanged(v -> BaritoneHook.btSet(btName, v))
            .onModuleActivated(s -> s.set(clamp(BaritoneHook.btInt(btName, def), min, max)))
            .build());
    }

    private DoubleSetting btDouble(String btName, String label, String desc, double min, double max) {
        double def = clamp(BaritoneHook.btDefaultDouble(btName, min), min, max);
        return sgBaritone.add(new DoubleSetting.Builder()
            .name(label)
            .description(desc + "  [Baritone: " + btName + "]")
            .defaultValue(def)
            .min(min).max(max).sliderRange(min, max).decimalPlaces(2)
            .onChanged(v -> BaritoneHook.btSet(btName, v))
            .onModuleActivated(s -> s.set(clamp(BaritoneHook.btDouble(btName, def), min, max)))
            .build());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private List<Map.Entry<String, Setting<?>>> baritoneBindings() {
        List<Map.Entry<String, Setting<?>>> list = new ArrayList<>();
        list.add(Map.entry("elytraTermsAccepted", btTermsAccepted));
        list.add(Map.entry("elytraAutoJump", btAutoJump));
        list.add(Map.entry("elytraFireworkSpeed", btFireworkSpeed));
        list.add(Map.entry("elytraConserveFireworks", btConserveFireworks));
        list.add(Map.entry("elytraAutoSwap", btAutoSwap));
        list.add(Map.entry("elytraPredictTerrain", btPredictTerrain));
        list.add(Map.entry("elytraFreeLook", btFreeLook));
        list.add(Map.entry("elytraSmoothLook", btSmoothLook));
        list.add(Map.entry("elytraPitchRange", btPitchRange));
        list.add(Map.entry("elytraSimulationTicks", btSimulationTicks));
        list.add(Map.entry("elytraMinimumAvoidance", btMinimumAvoidance));
        list.add(Map.entry("elytraAllowEmergencyLand", btAllowEmergencyLand));
        list.add(Map.entry("elytraMinFireworksBeforeLanding", btMinFireworksBeforeLanding));
        list.add(Map.entry("elytraMinimumDurability", btMinimumDurability));
        list.add(Map.entry("elytraAllowLandOnNetherFortress", btAllowLandOnNetherFortress));
        list.add(Map.entry("elytraChatSpam", btChatSpam));
        list.add(Map.entry("elytraRenderSimulation", btRenderSimulation));
        return list;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();

        WButton apply = table.add(theme.button("应用 Baritone 设置")).expandX().minWidth(120).widget();
        apply.action = () -> applyBaritoneFromPanel(false);
        table.row();

        WButton save = table.add(theme.button("保存并设为默认")).expandX().minWidth(120).widget();
        save.action = () -> applyBaritoneFromPanel(true);
        table.row();

        WButton openLog = table.add(theme.button("打开日志文件夹")).expandX().minWidth(120).widget();
        openLog.action = this::openLogFolder;
        table.row();

        logPathLabel = table.add(theme.label(logPathText())).expandCellX().widget();
        table.row();

        baritoneStatusLabel = table.add(theme.label(baritoneStatus())).expandCellX().widget();
        return table;
    }

    private Path logDirectory() {
        return FOElytraLog.directory();
    }

    private String logPathText() {
        if (!detailLog.get()) return "详细日志：已关闭（打开上面的开关就会开始写文件）";
        Path cur = FOElytraLog.currentFile();
        return cur == null
            ? "详细日志：目录 " + logDirectory()
            : "详细日志：" + logDirectory().getFileName() + "/" + cur.getFileName();
    }

    private void openLogFolder() {
        try {
            Path dir = logDirectory();
            Files.createDirectories(dir);

            net.minecraft.util.Util.getOperatingSystem().open(dir.toFile());
            FOElytraLog.info("已打开日志目录：%s", dir);
        } catch (Throwable t) {
            FOElytraLog.warn("打不开日志目录（%s）—— 手动去这里看：%s", t, logDirectory());
        }
        if (logPathLabel != null) logPathLabel.set(logPathText());
    }

    private void applyBaritoneFromPanel(boolean saveAsDefault) {
        if (!BaritoneHook.available()) {
            error("没有检测到 Baritone：这些设置无法写入。");
            refreshBaritoneStatus();
            return;
        }

        int applied = 0;
        for (Map.Entry<String, Setting<?>> binding : baritoneBindings()) {
            if (BaritoneHook.btSet(binding.getKey(), binding.getValue().get())) applied++;
        }

        if (saveAsDefault) {
            boolean saved = BaritoneHook.saveBaritone();
            try {
                Modules.get().save();
            } catch (Throwable t) {
                LOG.warn("保存 Meteor 配置失败", t);
            }
            if (saved) {
                info("已应用 %d 项并保存为默认（baritone/settings.txt + Meteor 配置）", applied);
            } else {
                warning("已应用 %d 项，但写入 baritone/settings.txt 失败（看日志）", applied);
            }
        } else {
            info("已应用 %d 项 Baritone 设置（仅本次运行，重启后恢复）", applied);
        }
        refreshBaritoneStatus();
    }

    private String baritoneStatus() {
        if (!BaritoneHook.available()) return "Baritone: 未检测到 —— 这些设置不会生效";
        int modified = BaritoneHook.modifiedCount();
        return modified >= 0
            ? "Baritone: 已加载，当前有 " + modified + " 项与出厂默认值不同"
            : "Baritone: 已加载";
    }

    private void refreshBaritoneStatus() {
        if (baritoneStatusLabel != null) baritoneStatusLabel.set(baritoneStatus());
    }

    private void baritoneKeyTick() {
        boolean pressed = baritoneSaveKey.get() != null && baritoneSaveKey.get().isPressed();
        if (pressed && !baritoneKeyWasPressed) {
            applyBaritoneFromPanel(true);
        }
        baritoneKeyWasPressed = pressed;
    }

    private final BoolSetting deflectFireballs = SettingHelper.bool(sgFireball, "反击火球","把飞向自己的火球打回去：暂停飞行 → 看向火球 → 打一拳 → 火球消失后恢复飞行。默认开。", true);

    private final DoubleSetting fireballRange = SettingHelper.double_(sgFireball, "拦截距离","火球探测半径（格）；0 = 按实体交互距离自动算。默认 0。", 0.0, 0.0, 16.0);

    private final IntSetting fireballMax = SettingHelper.int_(sgFireball, "最多同时拦几个",
        "同时存在的火球超过这个数量就放弃拦截（默认 1，即 2 个就放弃）。", 1, 1, 8);

    private final BoolSetting fireballPauseBaritone = SettingHelper.bool(sgFireball, "拦截时暂停飞行","拦截期间暂停 Baritone，打回火球后恢复飞行。默认开。", true);

    private final BoolSetting fireballFailOnMultiple = SettingHelper.bool(sgFireball, "拦不过来就判失败","火球数量超过上限时直接判任务失败（设计行为）。默认关。", false);

    private final BoolSetting lavaEscape = SettingHelper.bool(sgLava, "逃离岩浆","真的泡在岩浆里时自动抬头、开鞘翅、放烟花脱离。默认开。", true);

    private final IntSetting lavaTriggerTicks = SettingHelper.int_(sgLava, "着火触发 tick",
        "连续待在岩浆里超过这么多 tick（滑翔中）就触发自救。默认 20。", 20, 1, 200);

    private final IntSetting lavaFastTriggerTicks = SettingHelper.int_(sgLava, "非滑翔急触发 tick","不在滑翔时更急：连续待在岩浆里超过这么多 tick 就立刻自救（没有鞘翅撑不住）。默认 5。", 5, 1, 60);

    private final BoolSetting lavaIgnoreGlidingFire = SettingHelper.bool(sgLava, "滑翔时忽略岩浆","滑翔中泡在岩浆里也不自救（危险，别开）。默认关。", false);

    private final IntSetting lavaCooldownTicks = SettingHelper.int_(sgLava, "自救冷却 tick",
        "自救后进入冷却，冷却走完仍在岩浆里就判一次失败。默认 45。", 45, 5, 400);

    private final BoolSetting lavaSwimToSafety = SettingHelper.bool(sgLava, "兜底：游向安全点","自救冷却期间朝最近的落脚点游过去（水里按住跳会往上浮）。默认开。", true);

    private final IntSetting lavaSearchRadius = SettingHelper.int_(sgLava, "兜底：安全点搜索半径","上面那一项的搜索半径（格）。默认 12。", 12, 3, 24);

    private final BoolSetting lavaDrinkFireRes = SettingHelper.bool(sgLava, "兜底：喝抗火药水","自救冷却期间如果快捷栏里有抗火药水就先喝掉。默认关。", false);

    private final IntSetting lavaMaxRetries = SettingHelper.int_(sgLava, "自救最大重试","冷却走完仍在岩浆里算一次失败，连续失败这么多次才判任务失败。默认 5。", 5, 1, 20);

    private final DoubleSetting lavaPitch = SettingHelper.double_(sgLava, "自救抬头角度","自救时把视角抬到垂直朝上（`setPitch(-90)`）。默认 -90.0。", -90.0, -90.0, -20.0);

    private final BoolSetting lavaUseFirework = SettingHelper.bool(sgLava, "自救时放烟花","自救时从快捷栏放一发烟花把自己推起来。默认开。", true);

    private final BoolSetting lavaFailAbort = SettingHelper.bool(sgLava, "重试耗尽才判失败","自救连续失败「自救最大重试」次后是否判任务失败。默认开。", true);

    private final BoolSetting lavaRestoreView = SettingHelper.bool(sgLava, "脱险后回调视角","自救结束后把视角从「垂直朝上 -90°」转回目标方向。默认开。", true);

    private final DoubleSetting lavaRestorePitch = SettingHelper.double_(sgLava, "脱险后俯仰角","脱险回调视角时的俯仰角，0 = 平视。默认 0。",
        0.0, -60.0, 60.0);

    private final IntSetting lavaRestoreHold = SettingHelper.int_(sgLava, "回调保持 tick","Baritone 一旦接管就立刻不再插手，避免和它的转向打架。默认 40。", 40, 0, 400);

    private final BoolSetting lavaReplan = SettingHelper.bool(sgLava, "脱险后重新规划路线","脱险后重新下发一次目标路线，必要时重新起飞。默认开。", true);

    private final BoolSetting lavaPredictEnabled = SettingHelper.bool(sgLavaPredict, "岩浆预测：启用预测规避","实验性，默认关：沿当前飞行方向预测 1.5~4 秒，提前绕开岩浆柱/岩浆湖。", false);

    private final DoubleSetting lavaPredictHorizon = SettingHelper.double_(sgLavaPredict, "岩浆预测：预测时长（秒）","往前预测多少秒的飞行轨迹。默认 3.0。", 3.0, 1.5, 4.0);

    private final DoubleSetting lavaPredictUrgent = SettingHelper.double_(sgLavaPredict, "岩浆预测：紧急阈值（秒）","预计在这个时间内撞上岩浆就改用「紧急规避」（暂停 Baritone + 偏转视角 + 放烟花）。默认 1.2。", 1.2, 0.4, 2.0);

    private final IntSetting lavaPredictLateral = SettingHelper.int_(sgLavaPredict, "岩浆预测：侧向绕行距离","绕行航点离危险点往侧面偏多少格。默认 30。",
        30, 16, 96);

    private final IntSetting lavaPredictReturn = SettingHelper.int_(sgLavaPredict, "岩浆预测：绕行结束距离","离绕行航点多近就算绕过这一段（也可以靠「前方预测变干净」提前结束）。默认 25。", 25, 8, 64);

    private final IntSetting lavaPredictCooldown = SettingHelper.int_(sgLavaPredict, "岩浆预测：规避防抖 tick","两次规避动作之间至少间隔多少 tick，防止在岩浆边缘反复触发。默认 40。", 40, 10, 200);

    private final BoolSetting lavaPredictWarnOnly = SettingHelper.bool(sgLavaPredict, "岩浆预测：只预警不接管","只把预测结果写进日志，不改航点、不碰按键。默认关。", false);

    private final BoolSetting lavaPredictPauseBaritone = SettingHelper.bool(sgLavaPredict, "岩浆预测：紧急时暂停 Baritone","紧急规避期间暂停 Baritone（p），脱离后恢复（r）——不暂停的话它会和我们抢视角。默认开。", true);

    private final DoubleSetting lavaPredictDeflect = SettingHelper.double_(sgLavaPredict, "岩浆预测：紧急偏转角（度）","紧急规避时相对「岩浆反方向」再左右偏多少度，用来在两侧里挑一条更干净的出路。默认 75.0。", 75.0, 30.0, 90.0);

    private final BoolSetting autoSupply = SettingHelper.bool(sgSupplyTrigger, "启用自动补给","缺物资时自动降落，放末影箱、取潜影盒补给。默认开。", true);

    private final BoolSetting supplyBeforeSegment = SettingHelper.bool(sgSupplyTrigger, "每段先做补给检查","每段开始前先跑一次补给判定，什么都不缺就直接起飞。默认开。", true);

    private final IntSetting minFireworkStacks = SettingHelper.int_(sgSupplyTrigger, "烟花最低组数","背包里烟花少于这个组数就触发补给。默认 3。", 3, 0, 27);

    private final IntSetting minFoodCount = SettingHelper.int_(sgSupplyTrigger, "食物最低数量","食物少于这个数量就触发补给。默认 8。", 8, 0, 64);

    private final IntSetting minXpBottles = SettingHelper.int_(sgSupplyTrigger, "经验瓶最低数量","附魔之瓶少于这个数量就触发补给（修鞘翅的前提）。默认 8。", 8, 0, 640);

    private final IntSetting minTotems = SettingHelper.int_(sgSupplyTrigger, "图腾最低数量","不死图腾少于这个数量就触发补给。默认 1。", 1, 0, 8);

    private final IntSetting minElytraDurability = SettingHelper.int_(sgSupplyTrigger, "鞘翅耐久警戒线","所有鞘翅的剩余耐久总和低于该值就触发补给（顺路换新鞘翅）。默认 60。", 60, 0, 400);

    private final BoolSetting landForSupply = SettingHelper.bool(sgSupplyTrigger, "补给前自动降落","让 Baritone 降落到地面后再开始放箱子。默认开。", true);

    private final IntSetting maxSupplyRetries = SettingHelper.int_(sgSupplyTrigger, "最大补给重试次数","补给后物资仍然不达标就退避重试。默认 2。", 2, 1, 10);

    private final IntSetting supplyErrorRetries = SettingHelper.int_(sgSupplyTrigger, "补给出错重试次数","补给过程出错时不判失败，退避重试这么多次。默认 3。", 3, 1, 10);

    private final IntSetting supplyRetryDelay = SettingHelper.int_(sgSupplyTrigger, "补给重试等待","两次补给之间的最小间隔 tick（20 tick = 1 秒）。默认 200。", 200, 20, 2400);

    private final BoolSetting autoRestock = SettingHelper.bool(sgSupplyTrigger, "自动补充物资至物品栏","快捷栏里清单物品不够就从背包换过来（不打开界面）。默认开。", true);

    private final ItemListSetting restockItems = SettingHelper.items(sgSupplyTrigger, "物品栏补充清单","要维持的物品清单。",
        List.of(Items.FIREWORK_ROCKET), false);

    private final IntSetting restockStacks = SettingHelper.int_(sgSupplyTrigger, "每个物品补到几组","清单里每样物品在快捷栏里保持几组（一组 = 该物品的最大堆叠数：烟花 64、经验瓶 64、图腾 1）。",
        1, 1, 8);

    private final IntSetting restockInterval = SettingHelper.int_(sgSupplyTrigger, "补充间隔 tick","两次搬运之间的最小间隔（20 tick = 1 秒）。默认 10。", 10, 1, 100);

    private final BoolSetting restockKeepHeld = SettingHelper.bool(sgSupplyTrigger, "补充时不占手持格","换位时跳过你当前拿着的那一格，免得把你正用的东西换走。默认开。", true);

    private final BoolSetting restockTriggerSupply = SettingHelper.bool(sgSupplyTrigger, "背包不足时触发补给","清单里的东西连整个背包都不够时，触发末影箱补给（降落 → 放末影箱 → 开潜影盒取物）。默认开。", true);

    private final BoolSetting fullSupplyOnStart = SettingHelper.bool(sgSupplyTrigger, "任务开始时先补满","任务开始时先做一次完整补给，补满再起飞。默认关。", false);

    private final KeybindSetting supplyKey = SettingHelper.keybind(sgSupplyTrigger, "手动补给键",
        "按一下立刻做一次补给（不需要打开任何界面）。");

    private final IntSetting targetFireworkStacks = SettingHelper.int_(sgSupplyAmount, "目标烟花组数","补到多少组烟花（1 组 = 64 个）。默认 21。", 21, 0, 36);

    private final IntSetting targetXpBottles = SettingHelper.int_(sgSupplyAmount, "目标经验瓶数量",
        "补到多少个附魔之瓶（修鞘翅用）。默认 192。", 192, 0, 2560);

    private final IntSetting targetFoodCount = SettingHelper.int_(sgSupplyAmount, "目标食物数量","补到多少个食物。默认 32。", 32, 0, 512);

    private final IntSetting targetTotems = SettingHelper.int_(sgSupplyAmount, "目标图腾数量","补到多少个不死图腾。默认 2。", 2, 0, 16);

    private final IntSetting targetElytraCount = SettingHelper.int_(sgSupplyAmount, "目标备用鞘翅","补到多少条「耐久 3、损伤 < 15」的备用鞘翅。默认 2。", 2, 0, 8);

    private final IntSetting minEnderChests = SettingHelper.int_(sgSupplyAmount, "最少末影箱数量","背包里末影箱少于这个数量时给警告（按设计直接判定失败）。默认 3。", 3, 1, 9);

    private final IntSetting maxShulkers = SettingHelper.int_(sgSupplyAmount, "单次最多取盒数","一次补给最多从末影箱里取几个潜影盒（防止物品太分散）。默认 4。", 4, 1, 27);

    private final IntSetting actionDelay = SettingHelper.int_(sgSupplyExec, "动作间隔 tick","每个点击/放置动作之间的间隔。默认 3。", 3, 1, 20);

    private final IntSetting placeRadius = SettingHelper.int_(sgSupplyExec, "放置搜索半径","在玩家周围多少格内寻找可以放末影箱/潜影盒的位置。默认 2。", 2, 1, 4);

    private final BoolSetting autoPlaceEnderChest = SettingHelper.bool(sgSupplyExec, "自动放置末影箱","从快捷栏拿出末影箱放在脚边。默认开。", true);

    private final BoolSetting autoPickupEnderChest = SettingHelper.bool(sgSupplyExec, "用后回收末影箱","补给完成后把末影箱挖回来（否则会消耗末影箱）。默认开。", true);

    private final BoolSetting useBaritoneMine = SettingHelper.bool(sgSupplyExec, "用 Baritone 挖方块","挖潜影盒/末影箱交给 Baritone：它会走过去按住挖，并捡回掉落物。默认开。", true);

    private final BoolSetting storeLoot = SettingHelper.bool(sgSupplyExec, "顺路存战利品","取物资时，把背包里的杂物（或下面的白名单物品）shift 进当前打开的潜影盒，腾出空间。默认关。", false);

    private final ItemListSetting storeItems = SettingHelper.items(sgSupplyExec, "要存放的物品","只有这些物品会被存进潜影盒。", List.of(), false);

    private final ItemListSetting supplyFoodItems = SettingHelper.items(sgSupplyExec, "补给的食物白名单","补给时补哪些食物。",
        List.of(Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.BREAD), true);

    private final BoolSetting autoEat = SettingHelper.bool(sgEat, "启用自动进食","饥饿或血量偏低时自动吃东西。默认开。", true);

    private final IntSetting hungerThreshold = SettingHelper.int_(sgEat, "饥饿阈值","饥饿值低于该值时吃饭（0-20）。默认 16。", 16, 0, 20);

    private final DoubleSetting healthThreshold = SettingHelper.double_(sgEat, "血量阈值","血量低于该值且饥饿值不满时也吃饭（配合自然回血）。默认 15.0。", 15.0, 0.0, 20.0);

    private final BoolSetting eatWhileGliding = SettingHelper.bool(sgEat, "飞行中进食","允许在滑翔途中吃（只在爬升段吃，避免掉高度）。默认开。", true);

    private final DoubleSetting eatMinRise = SettingHelper.double_(sgEat, "爬升速度阈值","垂直速度高于该值才允许在飞行中进食。默认 0.6。", 0.6, 0.0, 3.0);

    private final ItemListSetting foodWhitelist = SettingHelper.items(sgEat, "吃的食物白名单",
        "留空 = 任何能吃的东西都吃。", List.of(Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.BREAD), true);

    private final StringSetting foodPriority = SettingHelper.string(sgEat, "食物优先级",
        "按这个顺序挑食物，物品 id 用逗号隔开，例如 golden_carrot,cooked_beef；留空按默认。",
        "");

    private final BoolSetting autoMend = SettingHelper.bool(sgMend, "启用自动修鞘翅","鞘翅耐久不足时降落并用附魔之瓶修复。默认开。", true);

    private final IntSetting mendDurability = SettingHelper.int_(sgMend, "修复触发耐久","鞘翅剩余耐久低于该值时触发修复。默认 60。", 60, 8, 400);

    private final KeybindSetting mendKey = SettingHelper.keybind(sgMend, "手动修鞘翅键",
        "按一下立刻开始一次修复。");

    private final IntSetting maxMendRetries = SettingHelper.int_(sgMend, "最大修复重试次数","连续失败这么多次后自动关掉「启用自动修鞘翅」，避免一直降落又修不了。默认 2。", 2, 1, 10);

    private final IntSetting minBottles = SettingHelper.int_(sgMend, "最少经验瓶","背包里少于这个数量就不启动修复（设计上要求 ≥ 30 个）。默认 32。", 32, 1, 640);

    private final IntSetting repairToDamage = SettingHelper.int_(sgMend, "修复到损伤值","鞘翅损伤降到该值以下就停手（0 = 修满）。默认 20。", 20, 0, 200);

    private final BoolSetting requireGround = SettingHelper.bool(sgMend, "需要落地","先让 Baritone 降落再修。默认开。", true);

    private final IntSetting landingTimeout = SettingHelper.int_(sgMend, "降落超时","等待落地的最长 tick 数（20 tick = 1 秒）。默认 600。", 600, 100, 6000);

    private final DoubleSetting mendPitch = SettingHelper.double_(sgMend, "投掷俯仰角","扔瓶子时的视角角度，90 = 垂直朝下（经验球会落在脚边被自己吸走）。默认 90.0。", 90.0, 45.0, 90.0);

    private final IntSetting throwDelay = SettingHelper.int_(sgMend, "投掷间隔 tick",
        "两次扔瓶子之间的间隔；调小更快，但设太小服务端会不认这两瓶。默认 4。", 4, 2, 20);

    private final IntSetting maxThrows = SettingHelper.int_(sgMend, "单次最多扔几瓶","一次修复最多扔几瓶附魔之瓶。默认 128。", 128, 1, 1280);

    private final BoolSetting requireMending = SettingHelper.bool(sgMend, "必须有经验修补","鞘翅没有「经验修补」时直接放弃（避免白扔瓶子）。默认开。", true);

    private final BoolSetting requireNetherWastes = SettingHelper.bool(sgMend, "仅下界荒地修复","只在 nether_wastes 生物群系修鞘翅（落地相对安全）。默认关。", false);

    private final BoolSetting autoLogout = SettingHelper.bool(sgSafety, "危险自动登出","血量过低且图腾不足时自动断开连接（保命）。默认开。", true);

    private final DoubleSetting logoutHealth = SettingHelper.double_(sgSafety, "登出血量","血量低于该值且图腾数量不足时登出。默认 8.0。", 8.0, 1.0, 20.0);

    private final IntSetting logoutTotemMin = SettingHelper.int_(sgSafety, "登出图腾阈值","图腾数量少于等于该值时，配合血量条件触发登出。默认 1。", 1, 0, 8);

    private final BoolSetting logoutOnFailure = SettingHelper.bool(sgSafety, "失败自动登出","飞行任务失败时自动断开连接。默认开。", true);

    private final BoolSetting logoutOnSupplyFail = SettingHelper.bool(sgSafety, "补给失败也登出","补给这一路失败（放不了末影箱 / 盒子里没货 / 降落超时）时是否也登出。默认关。", false);

    private final BoolSetting logoutOnArrive = SettingHelper.bool(sgSafety, "到达自动登出","跑完所有航点后自动断开连接（挂机跑图常用）。默认关。", false);

    private final BoolSetting disableOnFinish = SettingHelper.bool(sgSafety, "任务结束关闭模块","跑完 / 失败后自动把本模块关掉。默认开。", true);

    private final IntSetting noElytraWaitSec = SettingHelper.int_(sgSafety, "没有鞘翅时等多久（秒）","身上没有可用鞘翅时先等这么久（刚进服务器物品栏可能还没同步）。默认 60。", 60, 0, 600);

    private final BoolSetting debugMessages = SettingHelper.bool(sgDebug, "调试输出","在聊天栏打印状态机的每一步、潜影盒扫描结果与火球/岩浆细节。默认关。", false);

    private final BoolSetting hudInfo = SettingHelper.bool(sgDebug, "HUD 状态","在模块列表里显示当前状态/距离/烟花数。默认开。", true);

    private final BoolSetting statusMonitor = SettingHelper.bool(sgDebug, "状态监控","每 10 秒在聊天栏打一行当前状态。默认开。", true);

    private final BoolSetting detailLog = SettingHelper.bool(sgDebug, "详细日志（写文件）","把每一步动作写进 fo-elytra-logs 下的日志文件。默认开。", true);

    private final BoolSetting logSteps = SettingHelper.bool(sgDebug, "日志记录每一步动作","连每一次点击/按键/状态迁移都写进日志（文件更大）。默认开。", true);

    private final IntSetting logKeep = SettingHelper.int_(sgDebug, "日志保留份数","fo-elytra-logs 目录最多保留几份日志，老的自动删除。默认 10。", 10, 2, 50);

    private final EatController eat = new EatController();
    private final FireballDeflector fireballs = new FireballDeflector();

    private LavaEscape lava;
    private int lavaCacheTrigger = -1;
    private int lavaCacheFastTrigger = -1;
    private int lavaCacheCooldown = -1;
    private int lavaCacheRetries = -1;
    private int lavaCacheRadius = -1;
    private boolean lavaCacheIgnoreGliding;
    private boolean lavaCacheSwim;
    private boolean lavaCachePotion;
    private double lavaCachePitch = Double.NaN;
    private boolean lavaCacheFirework;

    private LavaPredictor lavaPredictor;
    private boolean pdCacheEnabled;
    private double pdCacheHorizon = Double.NaN;
    private double pdCacheUrgent = Double.NaN;
    private int pdCacheLateral = -1;
    private int pdCacheReturn = -1;
    private int pdCacheCooldown = -1;
    private boolean pdCacheWarnOnly;
    private boolean pdCachePauseBaritone;
    private double pdCacheDeflect = Double.NaN;

    private SupplyTask supplyTask;
    private MendTask mendTask;
    private boolean manualTask;

    private final InventoryRestocker restocker = new InventoryRestocker();

    private boolean startFullSupplyDone;

    private boolean startFullSupplyPending;

    private boolean supplyFailPhase;

    private final Set<Item> exhaustedItems = new LinkedHashSet<>();
    private boolean foodExhaustedCache;
    private int exhaustedCooldown;

    private static final int SUPPLY_EXHAUSTED_COOLDOWN = 2400;

    private static final int SUPPLY_NO_PROGRESS_LIMIT = 1;

    private static final int TAKEOFF_FIREWORK_MAX = 3;

    private static final int TAKEOFF_FIREWORK_RETRY_TICKS = 60;

    private String lastSupplyStockSignature = "";

    private int supplyNoProgressRounds;

    private boolean logFileOwned;

    private int noElytraWaitTicks;

    private boolean suppressLogout;

    private int supplyTicks;

    private State state = State.IDLE;
    private String failReason = "";

    private final List<BlockPos> route = new ArrayList<>();
    private int routeIndex;
    private BlockPos segmentTarget;
    private boolean directionInitialised;
    private float directionFrozen;

    private int tickCounter;
    private int waitTicks;

    private int takeoffTicks;

    private TakeoffPhase takeoffPhase = TakeoffPhase.INIT;

    private int takeoffFireworksUsed;
    private int takeoffFireworkCooldown;
    private double takeoffFireworkY = Double.NaN;

    private int jumpSeq;
    private int jumpSeqTicks;

    private int jumpAttempts;

    private int jumpPhase;

    private int jumpIteration;

    private int takeoffDelayTicks;

    private int takeoffReArmCount;

    private int airJumpHold;

    private int fakeGlideTicks;

    private int controlTicks;

    private int airborneTicks;

    private int activityTicks;

    private double lastActivityDistance = -1;

    private int fallTicks;

    private int launchWait;

    private int glidingLostTicks;

    private int openTriesDone;

    private int clearWaited;
    private boolean clearingHead;

    private double ascendTargetY;
    private int ascendTicks;

    private BlockPos openEnd;
    private double openStartY;

    private int hopTicks;

    private int openSearchFails;

    private boolean ascendingSearch;
    private double searchYh;

    private int viewHoldTicks;
    private int hoverTicker;
    private boolean hovering;
    private boolean pausedByPlayer;
    private int lastCheckX;
    private int lastCheckZ;
    private int stuckStrikes;
    private int segFailStrikes;
    private int flightGrace;

    private int spinTimes;
    private int spinPauseTicks;
    private BlockPos lastSpinPos;

    private boolean segResetDone;

    private boolean forceFlyToOpen;
    private int supplyRetries;

    private int supplyErrorRetryCount;
    private int supplyCooldown;
    private int mendRetries;
    private int mendCooldown;
    private int hoverStart;
    private boolean fireballTooManyWarned;
    private boolean supplyKeyWasPressed;
    private boolean mendKeyWasPressed;
    private boolean flightSettingsApplied;
    private boolean takeoffAutoJumpUsed;

    private boolean baritoneAutoJumpForced;

    private final StuckEscape.Tracker stuckTracker = new StuckEscape.Tracker();
    private boolean stuckNow;
    private boolean stuckFireworkHold;
    private int stuckEscapeMode;
    private int stuckEscapeTicks;
    private BlockPos stuckEscapeSpot;
    private int stuckEscapeFails;
    private boolean stuckHealthStopped;
    private double flightStartX;
    private double flightStartZ;
    private int flightProgressTicks;
    private boolean takeoffCeilingChecked;
    private double landingY = Double.NaN;
    private double landingAngle;
    private double landingTargetX;
    private double landingTargetZ;
    private int landingTicks;

    private static final int STUCK_ESCAPE_NONE = 0;
    private static final int STUCK_ESCAPE_WALK = 1;
    private static final int STUCK_ESCAPE_MAX_FAILS = 3;
    private static final int STUCK_WALK_TIMEOUT_TICKS = 1200;
    private static final int STUCK_WALK_REPATH_TICKS = 100;
    private static final double STUCK_WALK_ARRIVE_DISTANCE = 3.0;
    private static final int STUCK_OPEN_SPOT_RADIUS = 48;
    private static final int STUCK_CLEAR_UP = 40;
    private static final int STUCK_SPOT_BUDGET = 420;
    private static final int STUCK_CEILING_UP = 3;
    private static final double ESCAPE_RAY_DISTANCE = 20.0;
    private static final int NO_PROGRESS_TICKS = 300;
    private static final double NO_PROGRESS_DISTANCE = 10.0;
    private static final double LANDING_SPIRAL_MIN_RADIUS = 4.0;
    private static final double LANDING_SPIRAL_DIVISOR = 8.0;
    private static final double LANDING_NO_FIREWORK_ABOVE = 20.0;
    private static final double LANDING_CUTOFF_SECONDS = 3.0;
    private static final int LANDING_FIREWORK_INTERVAL = 10;
    private static final double LANDING_SPIRAL_STEP = 0.25;

    private int terrainProbeTicks;
    private int terrainDetourCooldown;
    private BlockPos terrainDetour;
    private int terrainDetourTicks;
    private int terrainClimbTicks;
    private double terrainClimbStartY;
    private boolean terrainClimbPausedBaritone;
    private final List<TerrainSample> terrainPending = new ArrayList<>();
    private int terrainPendingTicks;
    private int landSkippedCave;
    private int landSkippedThreat;
    private int terrainDetourCount;
    private double lastHurtHealth = -1.0;
    private boolean torchPlacedThisLanding;

    private record TerrainSample(BlockPos pos, int forward, String label) {
    }

    private static final int TERRAIN_SAMPLE_POINTS = 3;
    private static final double TERRAIN_DETOUR_ANGLE = 45.0;
    private static final double TERRAIN_DETOUR_DISTANCE = 80.0;
    private static final double TERRAIN_CLIMB_HEIGHT = 25.0;
    private static final int TERRAIN_TRIGGER_COOLDOWN = 400;
    private static final int TERRAIN_DETOUR_TIMEOUT = 500;
    private static final int TERRAIN_PROBE_TIMEOUT = 100;
    private static final int TERRAIN_CLIMB_TICKS = 30;
    private static final int LAND_THREAT_CROWD_RADIUS = 24;
    private static final int LAND_THREAT_CROWD_COUNT = 5;
    private static final int SUPPLY_HURT_COOLDOWN = 600;

    private static final List<String> CONFLICT_FLIGHT = List.of("ElytraFly", "Flight", "ElytraBoost", "TridentBoost");
    private static final List<String> CONFLICT_ITEMS = List.of("AutoEat", "AutoMend", "AutoReplenish", "ChestSwap", "InventoryTweaks");

    public AutoElytraFlight() {

        super("FO 自动鞘翅飞行",
            "全自动鞘翅跑图：Baritone 飞行 + 末影箱/潜影盒补给 + 自动进食 + 经验瓶修鞘翅 + 反击火球 + 逃离岩浆 + 危险登出。",
            "elytra", "autofly", "autoelytra", "aef", "foelytra");
    }

    @Override
    public void onActivate() {
        state = State.PREPARE;
        failReason = "";
        route.clear();
        routeIndex = 0;
        segmentTarget = null;
        directionInitialised = false;
        tickCounter = 0;
        resetStuckState();
        resetTerrainState();
        waitTicks = 0;
        takeoffTicks = 0;
        takeoffPhase = TakeoffPhase.INIT;
        jumpSeq = 0;
        jumpSeqTicks = 0;
        jumpAttempts = 0;
        fallTicks = 0;
        glidingLostTicks = 0;
        openTriesDone = 0;
        clearWaited = 0;
        clearingHead = false;
        ascendTicks = 0;
        ascendTargetY = 0;
        openEnd = null;
        openStartY = 0;
        hopTicks = 0;
        openSearchFails = 0;
        ascendingSearch = false;
        searchYh = 0.0;
        viewHoldTicks = 0;
        hoverTicker = 0;
        hovering = false;
        pausedByPlayer = false;
        stuckStrikes = 0;
        segFailStrikes = 0;
        spinTimes = 0;
        spinPauseTicks = 0;
        lastSpinPos = null;
        segResetDone = false;
        forceFlyToOpen = false;
        flightGrace = 0;
        supplyRetries = 0;
        supplyErrorRetryCount = 0;
        lastSupplyStockSignature = "";
        supplyNoProgressRounds = 0;
        supplyCooldown = 0;
        exhaustedItems.clear();
        foodExhaustedCache = false;
        exhaustedCooldown = 0;
        mendRetries = 0;
        mendCooldown = 0;
        hoverStart = 0;
        fireballTooManyWarned = false;
        flightSettingsApplied = false;
        takeoffAutoJumpUsed = false;
        directionFrozen = 0f;
        supplyTask = null;
        mendTask = null;
        manualTask = false;
        restocker.reset();
        startFullSupplyDone = false;
        startFullSupplyPending = false;
        noElytraWaitTicks = 0;
        suppressLogout = false;
        fireballs.reset();
        eat.stop();
        takeoffFireworksUsed = 0;
        takeoffFireworkCooldown = 0;
        takeoffFireworkY = Double.NaN;
        ensureLava();
        ensureLavaPredictor();
        if (lavaPredictor != null) lavaPredictor.reset();

        if (!BaritoneHook.available()) {
            error("没有检测到 Baritone：请先安装 Baritone（或 Meteor 的 baritone 集成）再使用本模块。");
            state = State.FAILED;
            if (isActive()) toggle();
            return;
        }
        if (!BaritoneHook.ready()) {
            error("Baritone 已安装但还没有就绪（拿不到 IBaritone 实例），请进入世界后再打开本模块。");
            state = State.FAILED;
            if (isActive()) toggle();
            return;
        }
        if (btTermsAccepted.get()) BaritoneHook.acceptTerms();
        BaritoneHook.installSegFailLogger();
        BaritoneHook.clearSegFailCounter();
        refreshBaritoneStatus();
        warnConflicts();

        FOElytraLog.fileVerbose = logSteps.get();
        if (mc.runDirectory != null) {
            FOElytraLog.setGameDir(mc.runDirectory.toPath());
            if (detailLog.get()) {

                logFileOwned = FOElytraLog.currentFile() == null;
                FOElytraLog.ensureOpen(mc.runDirectory.toPath(), logKeep.get());
                dumpEnvironment();
            }
        }

        if (btAutoJump.get()) {
            warning("Baritone 的 elytraAutoJump = true（已写进 baritone/settings.txt）：开着它 Baritone 会在起飞前"
                + "先去找「能往下跳的台阶」，平原/室内直接报 Failed to compute a walking path to a spot to jump off from "
                + "并拒绝起飞。本插件起飞时会临时压掉这一项；想永久关掉就关掉面板「Baritone 飞行 → 自动起跳」"
                + "再点一次「保存并设为默认」。");
        }

        FOElytraLog.info("AutoElytraFlight 启动：模式 %s，Baritone 就绪", mode.get());
        if (mc.player != null && ItemHelper.wornElytra(mc.player).isEmpty()) {
            warning("身上没有穿鞘翅，准备阶段会尝试自动穿上。");
        } else if (mc.player != null && ItemHelper.remainingDurability(ItemHelper.wornElytra(mc.player)) < 30
            && targetElytraCount.get() <= 0) {
            warning("身上的鞘翅只剩 %d 点耐久，而「补给数量 → 目标备用鞘翅」是 0 —— "
                + "鞘翅一飞坏，模块就再也起不来了。建议把「目标备用鞘翅」设成 1~2 组。",
                ItemHelper.remainingDurability(ItemHelper.wornElytra(mc.player)));
        }
    }

    @Override
    public void onDeactivate() {
        eat.stop();
        fireballs.reset();
        restocker.reset();
        BaritoneHook.removeSegFailLogger();
        if (lava != null) lava.release(mc);
        if (lavaPredictor != null) lavaPredictor.release(mc);
        viewHoldTicks = 0;
        if (takeoffAutoJumpUsed) {

            takeoffAutoJumpUsed = false;
            BaritoneHook.btSet("elytraAutoJump", btAutoJump.get());
        }
        if (baritoneAutoJumpForced) {

            baritoneAutoJumpForced = false;
            BaritoneHook.btSet("elytraAutoJump", btAutoJump.get());
        }
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        PlayerAction.releaseAll();
        PlayerAction.restoreHeldKeys();
        BlockBreaker.cancel();
        BaritoneHook.stop();

        if (supplyTask != null && supplyTask.isRunning()) supplyTask.abort("模块关闭");
        if (mendTask != null && mendTask.isRunning()) mendTask.abort("模块关闭");
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();

        if (state != State.IDLE) FOElytraLog.info("AutoElytraFlight 已关闭（%s）", state.toString());
        state = State.IDLE;

        if (logFileOwned) {
            logFileOwned = false;
            FOElytraLog.closeFile();
        }
    }

    public boolean flyTo(int x, int z) {
        if (mc.player == null || mc.world == null) return false;

        targetX.set(x);
        targetZ.set(z);
        mode.set(Mode.SingleTarget);
        segmentTarget = null;
        segFailStrikes = 0;
        segResetDone = false;
        spinTimes = 0;
        spinPauseTicks = 0;
        takeoffPhase = TakeoffPhase.INIT;
        flightSettingsApplied = false;
        forceFlyToOpen = false;
        takeoffTicks = 0;
        jumpAttempts = 0;
        openTriesDone = 0;
        startFullSupplyDone = true;
        startFullSupplyPending = false;
        manualTask = false;
        state = State.PREPARE;
        FOElytraLog.info("收到外部飞行请求：%d, %d（当前距离 %.0f 格）", x, z,
            Math.hypot(mc.player.getX() - (x + 0.5), mc.player.getZ() - (z + 0.5)));
        return true;
    }

    public boolean travelFinished() {
        return state == State.DONE || state == State.FAILED || state == State.IDLE || segmentTarget == null;
    }

    public boolean travelFailed() {
        return state == State.FAILED;
    }

    public boolean travelTerminal() {
        return state == State.DONE || state == State.FAILED || state == State.IDLE;
    }

    public double distanceToSegmentTarget() {
        if (mc.player == null || segmentTarget == null) return -1;
        double dx = mc.player.getX() - (segmentTarget.getX() + 0.5);
        double dz = mc.player.getZ() - (segmentTarget.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    public String travelStateName() {
        return state.toString();
    }

    public String travelFailReason() {
        return failReason == null ? "" : failReason;
    }

    @Override
    public String getInfoString() {
        if (!hudInfo.get()) return null;
        if (mc.player == null || state == State.IDLE) return state.toString();

        StringBuilder sb = new StringBuilder(state.toString());
        if (segmentTarget != null) {
            double dx = mc.player.getX() - (segmentTarget.getX() + 0.5);
            double dz = mc.player.getZ() - (segmentTarget.getZ() + 0.5);
            sb.append(String.format(" %.0fm", Math.sqrt(dx * dx + dz * dz)));
        }
        sb.append(" 烟花").append(ItemHelper.countInHotbar(mc.player, Items.FIREWORK_ROCKET));
        if (state == State.SUPPLY && supplyTask != null) sb.append(" ").append(supplyTask.progress());
        if (state == State.MEND && mendTask != null) sb.append(" ").append(mendTask.progress());
        return sb.toString();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.world == null) return;
        FOElytraLog.debugEnabled = debugMessages.get();
        try {
            tick();
        } catch (Throwable t) {
            onError("onTick", t);
            fail("内部异常 " + t.getClass().getSimpleName());
        }
    }

    private void dumpEnvironment() {
        FOElytraLog.detail("Minecraft %s｜Baritone %s｜模块 %s", "1.21.11",
            BaritoneHook.available() ? (BaritoneHook.ready() ? "已就绪" : "已加载未就绪") : "未安装",
            name);
        if (mc.player != null) {
            FOElytraLog.detail("玩家位置 %d %d %d｜世界 %s",
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                mc.world != null ? mc.world.getRegistryKey().getValue() : "?");
        }
        FOElytraLog.detail("—— 当前设置 ——");
        try {
            for (SettingGroup g : settings) {
                FOElytraLog.detail("【%s】", g.name);
                for (Setting<?> s : g) {
                    FOElytraLog.detail("    %s = %s", s.name, s.get());
                }
            }
        } catch (Throwable t) {
            FOElytraLog.detailError("dumpEnvironment", t);
        }
        FOElytraLog.detail("—— 设置结束 ——");
    }

    private void statusHeartbeat() {
        if (!statusMonitor.get()) return;
        if (tickCounter % 200 != 0) return;
        if (mc.player == null) return;
        String dist = segmentTarget == null ? "-" : String.format("%.0f", Math.hypot(
            mc.player.getX() - (segmentTarget.getX() + 0.5), mc.player.getZ() - (segmentTarget.getZ() + 0.5)));

        String predict = (lavaPredictor != null && (lavaPredictor.threat() != null || lavaPredictor.isAvoiding()))
            ? " | " + lavaPredictor.statusText() : "";
        FOElytraLog.info("状态监控：状态 %s%s | Baritone %s | 烟花 %d 发（%d 组）| 血 %.1f | 距目标 %s 格 | 滑翔 %s%s",
            state.toString(),
            hovering ? "(等区块)" : (pausedByPlayer ? "(让行)" : ""),
            BaritoneHook.isFlying() ? "飞行中" : "未接管",
            ItemHelper.countInHotbar(mc.player, Items.FIREWORK_ROCKET),
            ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET)),
            mc.player.getHealth(), dist,
            mc.player.isGliding() ? "是" : "否", predict);
    }

    private void tick() {
        tickCounter++;
        BaritoneHook.markTick();

        if (tickCounter % 600 == 0) BaritoneHook.installSegFailLogger();
        if (supplyCooldown > 0) supplyCooldown--;
        if (mendCooldown > 0) mendCooldown--;
        if (exhaustedCooldown > 0) {
            if (--exhaustedCooldown == 0) {

                FOElytraLog.info("补给重试冷却结束（之前判定取光的：%s），下次仍会去末影箱翻一遍",
                    describeExhausted());
                exhaustedItems.clear();
                foodExhaustedCache = false;
            }
        }

        ensureLava();
        ensureLavaPredictor();
        manualKeyTick();
        baritoneKeyTick();
        infinityElytraTick();

        lavaTick();
        lavaViewHoldTick();

        boolean busy = state == State.SUPPLY || state == State.MEND;
        boolean guiOpen = InvHelper.screenOpen();

        if (guiOpen) PlayerAction.restoreHeldKeys();

        boolean escaping = lavaEscaping() || (lavaPredictor != null && lavaPredictor.isAvoiding());
        if (!busy && !guiOpen && !escaping) {

            fireballTick();
            eat.tick(autoEat.get(), hungerThreshold.get(), healthThreshold.get(),
                eatWhileGliding.get(), eatMinRise.get(), foodWhitelist.get(), foodPriorityList());
        } else if (eat.isEating() && (guiOpen || escaping)) {
            eat.stop();
        }

        if (autoRestock.get() && !busy && !guiOpen && !escaping) {            restocker.tick(restockItems.get(), restockStacks.get(), restockKeepHeld.get(), restockInterval.get());
        }

        safetyTick();
        statusHeartbeat();
        takeoffFireworkTick();

        if (stuckEscapeActive()) {
            stuckEscapeTick();
            return;
        }

        switch (state) {
            case PREPARE -> prepare();
            case TAKEOFF -> takeoff();
            case FLYING -> flying();
            case LANDING -> landing();
            case SUPPLY -> supplyTick();
            case MEND -> mendTick();
            case DONE, FAILED -> {

            }
            case IDLE -> state = State.PREPARE;
        }
    }

    private void manualKeyTick() {
        boolean supplyPressed = supplyKey.get() != null && supplyKey.get().isPressed();
        if (supplyPressed && !supplyKeyWasPressed
            && state != State.SUPPLY && state != State.MEND && state != State.LANDING) {
            manualTask = true;
            if (startSupply()) FOElytraLog.info("手动触发补给");
            else manualTask = false;
        }
        supplyKeyWasPressed = supplyPressed;

        boolean mendPressed = mendKey.get() != null && mendKey.get().isPressed();
        if (mendPressed && !mendKeyWasPressed
            && state != State.SUPPLY && state != State.MEND && state != State.LANDING) {
            manualTask = true;
            if (startMend()) FOElytraLog.info("手动触发修鞘翅");
            else manualTask = false;
        }
        mendKeyWasPressed = mendPressed;
    }

    private void prepare() {
        if (!BaritoneHook.available()) {
            fail("Baritone 不可用");
            return;
        }
        if (!ensureElytraWorn()) {

            if (noElytraWaitTicks++ < noElytraWaitSec.get() * 20) {
                if (noElytraWaitTicks == 1 || noElytraWaitTicks % 100 == 0) {
                    FOElytraLog.warn("身上没有可用的鞘翅：先等你穿上或等物品栏同步（已等 %d 秒，最多 %d 秒）。"
                        + "等满后才会判失败，这条失败不会自动登出。",
                        noElytraWaitTicks / 20, noElytraWaitSec.get());
                }
                return;
            }
            noElytraWaitTicks = 0;
            failNoLogout("没有任何可用的鞘翅（等了 " + noElytraWaitSec.get() + " 秒仍没穿上）");
            return;
        }
        noElytraWaitTicks = 0;

        if (segmentTarget == null && !chooseNextTarget()) {
            finish("所有航点已完成");
            return;
        }

        if (InvHelper.screenOpen()) {
            waitTicks++;
            if (waitTicks == 1) {
                FOElytraLog.info("检测到你开着界面：我先等你关掉再继续（补给/起飞已暂停）。"
                    + "我不会替你关界面；不想跑了就再按一次模块快捷键关掉它。");
            } else if (waitTicks % 200 == 0) {
                FOElytraLog.warn("仍在等你关闭界面（已等 %d 秒）——关掉界面我就继续；不想等就按快捷键关掉模块", waitTicks / 20);
            }
            return;
        }
        waitTicks = 0;

        boolean lavaNow = lavaDanger();

        if (!lavaNow && autoSupply.get() && supplyBeforeSegment.get() && supplyCooldown <= 0) {
            String need = supplyNeedText();
            if (!need.isEmpty()) {
                FOElytraLog.info("所需补给：%s", need);

                String stock = supplyStockSignature(need);
                if (stock.equals(lastSupplyStockSignature)) {
                    supplyNoProgressRounds++;
                } else {
                    lastSupplyStockSignature = stock;
                    supplyNoProgressRounds = 0;
                }

                if (supplyNoProgressRounds >= SUPPLY_NO_PROGRESS_LIMIT) {
                    registerUnobtainableFromNeed(need);
                    lastSupplyStockSignature = "";
                    supplyNoProgressRounds = 0;
                    supplyCooldown = supplyRetryDelay.get();
                    return;
                }
                if (startSupply()) return;
                supplyCooldown = supplyRetryDelay.get();
            } else {
                lastSupplyStockSignature = "";
                supplyNoProgressRounds = 0;
            }
        }

        if (!lavaNow && autoSupply.get() && fullSupplyOnStart.get() && !startFullSupplyDone && supplyCooldown <= 0) {
            if (fullSupplyNeeded()) {
                if (startSupply()) {
                    startFullSupplyDone = true;
                    startFullSupplyPending = true;
                    FOElytraLog.info("任务开始：先补满物资再起飞（%s）→ %s", supplyReason(), fullSupplyGap());
                    return;
                }
                startFullSupplyDone = true;
            } else {
                startFullSupplyDone = true;
                FOElytraLog.info("任务开始：物资够用，直接起飞（差额：%s）", fullSupplyGap());
            }
        }

        if (!lavaNow && supplyBeforeSegment.get() && autoSupply.get() && supplyNeeded() && supplyCooldown <= 0) {
            FOElytraLog.info("起飞前检查：%s", supplyReason());
            if (startSupply()) return;
            supplyCooldown = supplyRetryDelay.get();
        }
        if (!lavaNow && autoMend.get() && mendCooldown <= 0 && MendTask.shouldRepair(mendDurability.get())) {
            if (startMend()) return;
            mendCooldown = 100;
        }

        flightSettingsApplied = false;
        takeoffTicks = 0;
        takeoffAutoJumpUsed = false;
        takeoffPhase = TakeoffPhase.INIT;
        jumpSeq = 0;
        jumpAttempts = 0;
        openTriesDone = 0;
        openEnd = null;
        clearingHead = false;
        glidingLostTicks = 0;
        ascendingSearch = false;
        searchYh = 0.0;
        openSearchFails = 0;
        waitTicks = 0;
        takeoffCeilingChecked = false;
        stuckNow = false;
        stuckTracker.reset();
        stuckFireworkHold = false;
        state = State.TAKEOFF;
    }

    private void warnConflicts() {
        if (mc.player == null) return;
        List<String> flight = new ArrayList<>();
        List<String> items = new ArrayList<>();
        try {
            for (Module m : Modules.get().getAll()) {
                if (!m.isActive()) continue;
                if (CONFLICT_FLIGHT.contains(m.name)) flight.add(m.name);
                else if (CONFLICT_ITEMS.contains(m.name)) items.add(m.name);
            }
        } catch (Throwable t) {
            return;
        }
        if (!flight.isEmpty()) {
            warning("检测到同时开启的飞行模块 %s —— 它们会和本模块抢鞘翅控制，建议只留一个。", flight);
        }
        if (!items.isEmpty()) {
            warning("检测到同时开启的 %s —— 它们会和本模块抢右键/物品栏（本模块自带进食、修鞘翅与烟花补充），建议关掉。", items);
        }
    }

    private boolean ensureElytraWorn() {
        ItemStack worn = ItemHelper.wornElytra(mc.player);
        if (!worn.isEmpty() && !isBroken(worn)) return true;
        if (InvHelper.screenOpen()) return true;

        if (!worn.isEmpty() && isBroken(worn)) {
            InvHelper.click(mc.player.currentScreenHandler, 6, 0, SlotActionType.QUICK_MOVE);
            FOElytraLog.warn("胸甲槽里的鞘翅已经用坏了（耐久 0），先取下来");
        }

        int slot = InvHelper.findSlot(s -> s.isOf(Items.ELYTRA) && !isBroken(s), 0, 36);
        if (slot < 0) return false;
        InvHelper.clickPlayerInv(slot, 0, SlotActionType.QUICK_MOVE);
        ItemStack after = ItemHelper.wornElytra(mc.player);
        if (!after.isEmpty() && !isBroken(after)) {
            FOElytraLog.tip("已自动穿上鞘翅（剩余耐久 %d）", ItemHelper.remainingDurability(after));
            return true;
        }
        FOElytraLog.warn("鞘翅没穿上（胸甲槽里是不是有别的盔甲？），先脱掉再试");
        return false;
    }

    private static boolean isBroken(ItemStack stack) {
        return stack.getMaxDamage() > 0 && stack.getDamage() >= stack.getMaxDamage();
    }

    private boolean chooseNextTarget() {

        if (lavaPredictor != null) lavaPredictor.reset();
        switch (mode.get()) {
            case SingleTarget -> {
                segmentTarget = new BlockPos(targetX.get(), 0, targetZ.get());
                FOElytraLog.info("目标坐标：%d, %d", targetX.get(), targetZ.get());
                return true;
            }
            case Direction -> {
                if (!directionInitialised) {

                    directionInitialised = true;
                    directionFrozen = mc.player.getYaw();
                    FOElytraLog.info("定向跑图方向已锁定：%.0f°（%s）。想换方向请转头后重新开关模块。",
                        directionFrozen, headingName(directionFrozen));
                }
                double rad = Math.toRadians(directionFrozen);
                int dx = (int) Math.round(-Math.sin(rad) * segmentDistance.get());
                int dz = (int) Math.round(Math.cos(rad) * segmentDistance.get());

                segmentTarget = new BlockPos(mc.player.getBlockX() + dx, 0, mc.player.getBlockZ() + dz);
                FOElytraLog.info("定向跑图下一段：%d, %d（方向 %.0f° %s）",
                    segmentTarget.getX(), segmentTarget.getZ(), directionFrozen, headingName(directionFrozen));
                return true;
            }
            default -> {
                if (route.isEmpty()) {
                    route.addAll(parseWaypoints());
                    if (route.isEmpty()) {
                        fail("航点列表为空（格式应为 x,z）");
                        return false;
                    }
                }
                if (routeIndex >= route.size()) {
                    if (!loop.get()) return false;
                    routeIndex = 0;
                }
                segmentTarget = route.get(routeIndex);
                FOElytraLog.info("第 %d/%d 个航点：%d, %d", routeIndex + 1, route.size(),
                    segmentTarget.getX(), segmentTarget.getZ());
                routeIndex++;
                return true;
            }
        }
    }

    private List<BlockPos> parseWaypoints() {
        List<BlockPos> list = new ArrayList<>();
        for (String raw : waypoints.get()) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("[,\\s]+");
            if (parts.length < 2) {
                FOElytraLog.warn("航点格式无法识别：%s", line);
                continue;
            }
            try {
                list.add(new BlockPos(Integer.parseInt(parts[0].trim()), 0, Integer.parseInt(parts[1].trim())));
            } catch (NumberFormatException e) {
                FOElytraLog.warn("航点数字解析失败：%s", line);
            }
        }
        return list;
    }

    private void takeoff() {
        if (segmentTarget == null) {
            PlayerAction.pressJump(false);
            state = State.PREPARE;
            return;
        }

        if (lavaEscaping()) return;

        if (InvHelper.screenOpen()) {
            waitTicks++;
            if (waitTicks == 1) FOElytraLog.info("检测到你开着界面，等你关掉再继续（起飞已暂停）");
            else if (waitTicks % 200 == 0) FOElytraLog.warn("仍在等你关闭界面（起飞已等 %d 秒）", waitTicks / 20);
            return;
        }
        waitTicks = 0;

        if (stuckEscape.get() && !takeoffCeilingChecked) {
            takeoffCeilingChecked = true;
            if (StuckEscape.ceilingBlocked(mc.world, mc.player.getBlockPos(), STUCK_CEILING_UP)) {
                FOElytraLog.warn("起飞位置头顶被堵（在洞里/顶到天花板）：先出去再起飞，别原地怼天花板");
                stuckNow = true;
                if (beginStuckEscape("起飞位置封顶")) return;
            }
        }

        if (!flightSettingsApplied) {
            flightSettingsApplied = true;

            baritoneAutoJumpForced = true;
            BaritoneHook.applyFlightSettings(false, btFireworkSpeed.get(), btAllowEmergencyLand.get());

            if (!lavaEscaping()) mc.player.setPitch(takeoffPitch.get().floatValue());
            if (!BaritoneHook.pathTo(segmentTarget.getX(), segmentTarget.getZ())) {
                fail("Baritone 拒绝规划鞘翅路线");
                return;
            }
            takeoffDelayTicks = 15;
            takeoffPhase = TakeoffPhase.LAUNCH;
            if (forceFlyToOpen) {

                forceFlyToOpen = false;
                takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
                FOElytraLog.info("复飞：直接进入「飞往开阔地」流程");
            }
            takeoffTicks = 0;
            jumpAttempts = 0;
            openTriesDone = 0;
            openEnd = null;
            clearingHead = false;
            jumpSeq = 0;
            glidingLostTicks = 0;
            takeoffFireworksUsed = 0;
            takeoffFireworkCooldown = 0;
            takeoffFireworkY = Double.NaN;

            FOElytraLog.info("开始起飞：位置 %d %d %d｜地面 %s｜滑翔 %s｜头顶被挡 %s｜烟花 %d 发",
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                mc.player.isOnGround() ? "是" : "否",
                mc.player.isGliding() ? "是" : "否",
                blockingBlocks(1).isEmpty() ? "否" : "是",
                ItemHelper.countInHotbar(mc.player, Items.FIREWORK_ROCKET));
        }

        takeoffTicks++;

        if (baritoneControlling()) {
            takeoffSucceeded();
            return;
        }

        int budget = Math.max(600, takeoffTimeout.get() * 5);
        if (autoTakeoff.get() && takeoffTicks > budget) {

            takeoffFallbackOrFail(String.format("起飞流程跑了 %d tick（%.0f 秒）Baritone 仍未接管",
                takeoffTicks, takeoffTicks / 20.0));
            return;
        }

        switch (takeoffPhase) {
            case CLEAR_HEAD -> clearHeadTick();
            case FLY_TO_OPEN -> flyToOpenTick();
            case ASCEND -> ascendTick();
            case WAIT_ARRIVE -> waitArriveTick();
            default -> {
                if (autoTakeoff.get()) launchTick();
                else manualLaunchTick();
            }
        }
    }

    private boolean baritoneControlling() {

        return mc.player != null && mc.player.isGliding() && !mc.player.isOnGround()
            && BaritoneHook.isFlying();
    }

    private void takeoffFireworkTick() {
        if (mc.player == null) return;
        if (!takeoffFirework.get()) return;
        if (takeoffFireworkCooldown > 0) takeoffFireworkCooldown--;

        if (takeoffFireworksUsed == 0) {
            if (!baritoneControlling()) return;
            if (takeoffFireworkCooldown > 0) return;
            takeoffFireworkY = mc.player.getY();
            fireTakeoffFirework();
            return;
        }
        if (takeoffFireworksUsed >= TAKEOFF_FIREWORK_MAX) return;
        if (takeoffFireworkCooldown > 0) return;
        boolean nearGround = mc.player.isOnGround()
            || (!Double.isNaN(takeoffFireworkY) && mc.player.getY() <= takeoffFireworkY + 2.0);
        if (!nearGround) return;
        fireTakeoffFirework();
    }

    private void fireTakeoffFirework() {
        if (mc.player == null || !mc.player.isGliding() || mc.player.isOnGround()) return;
        if (ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET) <= 0) return;
        if (!useFirework()) {
            refillHotbarFireworks();
            if (!useFirework()) {
                takeoffFireworkCooldown = 20;
                return;
            }
        }
        takeoffFireworksUsed++;
        takeoffFireworkCooldown = TAKEOFF_FIREWORK_RETRY_TICKS;
        FOElytraLog.info("起飞后补烟花（第 %d/%d 发）", takeoffFireworksUsed, TAKEOFF_FIREWORK_MAX);
    }

    private void updateActivity() {
        if (mc.player == null) {
            activityTicks = 0;
            return;
        }
        boolean airborneFalling = !mc.player.isOnGround() && mc.player.getVelocity().y < -0.05;
        double horiz = Math.sqrt(mc.player.getVelocity().x * mc.player.getVelocity().x
            + mc.player.getVelocity().z * mc.player.getVelocity().z);
        boolean moving = horiz > 0.2;
        boolean closing = false;
        if (segmentTarget != null) {
            double d = Math.hypot(mc.player.getX() - (segmentTarget.getX() + 0.5),
                mc.player.getZ() - (segmentTarget.getZ() + 0.5));
            closing = lastActivityDistance >= 0 && d < lastActivityDistance - 0.05;
            lastActivityDistance = d;
        }
        if (airborneFalling || moving || closing) {
            if (activityTicks < 100) activityTicks++;
        } else {
            activityTicks = 0;
        }
    }

    private void takeoffSucceeded() {
        PlayerAction.pressJump(false);
        jumpSeq = 0;
        if (takeoffAutoJumpUsed) {

            takeoffAutoJumpUsed = false;
            BaritoneHook.btSet("elytraAutoJump", false);
            baritoneAutoJumpForced = true;
        }
        FOElytraLog.info("起飞成功：Baritone 已接管鞘翅飞行（第 %d tick，方式 %s）", takeoffTicks, takeoffPhase);
        takeoffPhase = TakeoffPhase.INIT;
        openEnd = null;
        clearingHead = false;
        hopTicks = 0;
        ascendingSearch = false;
        searchYh = 0.0;
        openSearchFails = 0;
        state = State.FLYING;
        waitTicks = 0;
        flightGrace = 60;
        stuckStrikes = 0;
        lastCheckX = mc.player.getBlockX();
        lastCheckZ = mc.player.getBlockZ();
        stuckNow = false;
        stuckTracker.reset();
        stuckFireworkHold = false;
        stuckEscapeMode = STUCK_ESCAPE_NONE;
        stuckEscapeSpot = null;
        takeoffCeilingChecked = false;
        flightStartX = mc.player.getX();
        flightStartZ = mc.player.getZ();
        flightProgressTicks = 0;
        stuckEscapeFails = 0;
    }

    private boolean flightStatusCheck() {
        if (mc.player == null) return true;
        updateActivity();

        if (baritoneControlling()) {
            controlTicks = 0;
            airborneTicks = 0;
            return false;
        }

        if (mc.player.isGliding()) {
            if (mc.player.isOnGround()) {

                mc.player.stopGliding();
                PlayerAction.pressJump(false);
                if (++fakeGlideTicks % 20 == 1) {
                    FOElytraLog.warn("检测到「假滑翔」（客户端说在滑翔、人却站在地上）：已清掉本地滑翔状态，重新起跳");
                }
            } else {
                fakeGlideTicks = 0;

                if (++controlTicks > 15) {
                    controlTicks = 0;
                    if (openAreaSearch.get() && openTriesDone < openTries.get()) beginFlyToOpen();
                    else replanIfLost(true);
                }
                return true;
            }
        } else {
            fakeGlideTicks = 0;
        }

        if (!mc.player.isOnGround()) {

            if (airJumpHold > 0) {
                PlayerAction.pressJump(false);
                airJumpHold = 0;
                airborneTicks = 0;
            } else if (++airborneTicks > 2) {
                PlayerAction.pressJump(true);
                airJumpHold = 1;
            }
            return true;
        }

        airborneTicks = 0;
        if (jumpSeq != 0) {
            jumpSeqTick();
            return true;
        }
        boolean stationary = Math.abs(mc.player.getVelocity().x) < 0.01
            && Math.abs(mc.player.getVelocity().z) < 0.01;
        if (!stationary) return true;

        if (clearHeadBlock.get() && !blockingBlocks(1).isEmpty()) {
            beginClearHead();
            return true;
        }
        if (takeoffDelayTicks > 0) {
            takeoffDelayTicks--;
            return true;
        }
        if (jumpAttempts < jumpBeforeOpen.get()) {
            jumpAttempts++;
            FOElytraLog.info("自动起跳：第 %d/%d 次（%s）", jumpAttempts, jumpBeforeOpen.get(),
                state == State.FLYING ? "飞行中重新起跳（落地了）" : "起飞阶段");
            startJumpSequence();
        } else {

            if (openAreaSearch.get() && openTriesDone < openTries.get()) beginFlyToOpen();
            else replanIfLost(true);
        }
        return true;
    }

    private void manualLaunchTick() {
        if (mc.player.isGliding()) {
            if (++glidingLostTicks % 100 == 0) FOElytraLog.info("已在滑翔，等 Baritone 接管飞行…");
            return;
        }
        glidingLostTicks = 0;
        if (takeoffTicks % 200 == 0) {
            FOElytraLog.warn("「自动起飞」已关：请自己起跳两下展开鞘翅，模块会在你起飞后接管（已等 %d 秒）", takeoffTicks / 20);
        }
    }

    private void launchTick() {

        if (mc.player.isGliding() && mc.player.isOnGround()) {

            mc.player.stopGliding();
            PlayerAction.pressJump(false);
            jumpSeq = 0;
            jumpPhase = 0;
            if (++fakeGlideTicks % 20 == 1) {
                FOElytraLog.warn("检测到「假滑翔」（客户端说在滑翔、人却站在地上）：已清掉本地滑翔状态，重新走起跳流程");
            }
        } else if (mc.player.isGliding()) {
            fakeGlideTicks = 0;
            PlayerAction.pressJump(false);
            jumpSeq = 0;

            if (glidingLostTicks == 0) replanIfLost(true);
            if (++glidingLostTicks > 15) {
                glidingLostTicks = 0;
                if (openAreaSearch.get() && openTriesDone < openTries.get()) beginFlyToOpen();
                else takeoffFallbackOrFail("已经在滑翔，但 Baritone 15 tick 后仍未接管");
            }
            return;
        } else {
            fakeGlideTicks = 0;
        }

        if (!mc.player.isOnGround()) {
            fallTicks++;
            if (airJumpHold > 0) {
                PlayerAction.pressJump(false);
                airJumpHold = 0;
                fallTicks = 0;
            } else if (fallTicks > 2) {
                PlayerAction.pressJump(true);
                airJumpHold = 1;
            }
            return;
        }

        fallTicks = 0;
        if (jumpSeq != 0) {
            jumpSeqTick();
            return;
        }

        boolean moving = Math.abs(mc.player.getVelocity().x) >= 0.01 || Math.abs(mc.player.getVelocity().z) >= 0.01;
        if (moving) {
             ++launchWait;
            if (launchWait < 40) return;
            launchWait = 0;
        } else {
            launchWait = 0;
        }

        if (clearHeadBlock.get() && !blockingBlocks(1).isEmpty()) {
            beginClearHead();
            return;
        }

        if (takeoffDelayTicks > 0) {
            takeoffDelayTicks--;
            return;
        }

        if (jumpAttempts < jumpBeforeOpen.get()) {
            jumpAttempts++;

            FOElytraLog.info("自动起跳：第 %d 次尝试（最多 %d 次，之后改用「飞往开阔地」）",
                jumpAttempts, jumpBeforeOpen.get());
            startJumpSequence();
            return;
        }

        if (openAreaSearch.get() && openTriesDone < openTries.get()) {
            beginFlyToOpen();
            return;
        }
        takeoffFallbackOrFail(jumpAttempts + " 次原地起跳都没能展开鞘翅");
    }

    private void startJumpSequence() {
        jumpPhase = 1;
        jumpIteration = 0;
        takeoffFireworksUsed = 0;
        takeoffFireworkCooldown = 0;
        takeoffFireworkY = Double.NaN;
        if (!lavaEscaping()) mc.player.setPitch(takeoffPitch.get().floatValue());
        PlayerAction.pressJump(true);
        jumpSeq = 1;
        jumpSeqTicks = 0;
    }

    private void jumpSeqTick() {
        jumpSeqTicks++;
        switch (jumpPhase) {
            case 1 -> {
                if (jumpIteration == 1) PlayerAction.pressJump(false);
                jumpPhase = 2;
            }
            case 2 -> {
                if (mc.player.getVelocity().y < -0.1) {
                    jumpPhase = 3;
                    return;
                }
                jumpIteration++;
                if (jumpIteration >= 8) {
                    jumpPhase = 3;
                    return;
                }
                jumpPhase = 1;
            }
            case 3 -> {
                PlayerAction.pressJump(true);
                jumpPhase = 4;
            }
            case 4 -> jumpPhase = 5;
            case 5 -> {
                PlayerAction.pressJump(false);
                jumpPhase = 0;
                jumpSeq = 0;
                jumpSeqTicks = 0;
            }
            default -> {
                jumpPhase = 0;
                jumpSeq = 0;
                jumpSeqTicks = 0;
            }
        }
    }

    private static List<BlockPos> blockingBlocks(int checkY) {
        List<BlockPos> out = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return out;
        World world = client.world;
        PlayerEntity player = client.player;

        Vec3d pos = player.getEntityPos();
        double headX = pos.x;
        double headZ = pos.z;
        double headY = pos.y + player.getHeight();
        int aboveY = (int) Math.floor(headY) + 1;
        int baseX = (int) Math.floor(headX);
        int baseZ = (int) Math.floor(headZ);
        double offsetX = headX - baseX;
        double offsetZ = headZ - baseZ;

        Set<BlockPos> toCheck = new LinkedHashSet<>();
        toCheck.add(new BlockPos(baseX, aboveY, baseZ));
        if (offsetX > 0.7) toCheck.add(new BlockPos(baseX + 1, aboveY, baseZ));
        else if (offsetX < 0.3) toCheck.add(new BlockPos(baseX - 1, aboveY, baseZ));
        if (offsetZ > 0.7) toCheck.add(new BlockPos(baseX, aboveY, baseZ + 1));
        else if (offsetZ < 0.3) toCheck.add(new BlockPos(baseX, aboveY, baseZ - 1));
        if (offsetX > 0.7 && offsetZ > 0.7) toCheck.add(new BlockPos(baseX + 1, aboveY, baseZ + 1));
        else if (offsetX > 0.7 && offsetZ < 0.3) toCheck.add(new BlockPos(baseX + 1, aboveY, baseZ - 1));
        else if (offsetX < 0.3 && offsetZ > 0.7) toCheck.add(new BlockPos(baseX - 1, aboveY, baseZ + 1));
        else if (offsetX < 0.3 && offsetZ < 0.3) toCheck.add(new BlockPos(baseX - 1, aboveY, baseZ - 1));

        for (BlockPos pos0 : toCheck) {
            for (int i = 0; i < checkY; i++) {
                BlockPos q = pos0.add(0, i, 0);
                if (chunkLoaded(world, q) && !world.getBlockState(q).isAir()) out.add(pos0);
            }
            for (int i = checkY; i < 0; i++) {
                BlockPos q = pos0.add(0, i, 0);
                if (chunkLoaded(world, q) && !world.getBlockState(q).isAir()) out.add(pos0);
            }
        }
        return out;
    }

    private static boolean chunkLoaded(World world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private void beginClearHead() {
        List<BlockPos> bp = blockingBlocks(1);
        if (bp.isEmpty()) return;
        FOElytraLog.warn("头顶有方块阻挡，让 Baritone 先挖开（设计行为）");

        Vec3d pos = mc.player.getEntityPos();
        BaritoneHook.stop();
        BaritoneHook.builderClearArea(
            new BlockPos((int) Math.floor(pos.x - 0.3), bp.get(0).getY(), (int) Math.floor(pos.z - 0.3)),
            new BlockPos((int) Math.floor(pos.x - 0.3) + 1, bp.get(0).getY() + 1, (int) Math.floor(pos.z - 0.3) + 1));

        clearingHead = true;
        clearWaited = 0;
        jumpAttempts = 0;
        takeoffPhase = TakeoffPhase.CLEAR_HEAD;
    }

    private void clearHeadTick() {
        clearWaited++;
        List<BlockPos> left = blockingBlocks(1);
        boolean digging = clearingHead && clearWaited <= 200 && !left.isEmpty() && BaritoneHook.builderActive();
        if (digging) return;

        clearingHead = false;
        if (left.isEmpty()) {
            FOElytraLog.info("头顶障碍清除完毕（用了 %d tick），立刻起跳", clearWaited);
            if (segmentTarget != null) BaritoneHook.pathTo(segmentTarget.getX(), segmentTarget.getZ());
        } else {
            FOElytraLog.warn("头顶还是被挡（挖了 %d tick），照样起跳（设计上也是这样）", clearWaited);
        }

        jumpAttempts++;
        takeoffPhase = TakeoffPhase.LAUNCH;
        if (jumpAttempts <= jumpBeforeOpen.get()) {
            startJumpSequence();
        } else if (openAreaSearch.get() && openTriesDone < openTries.get()) {
            beginFlyToOpen();
        } else {
            takeoffFallbackOrFail("头顶被挡，挖不掉也起不来");
        }
    }

    private void beginFlyToOpen() {
        openTriesDone++;
        openEnd = null;
        openSearchFails = 0;
        ascendingSearch = false;
        searchYh = 0.0;
        PlayerAction.pressJump(false);
        jumpSeq = 0;
        jumpAttempts = 0;
        takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
        FOElytraLog.warn("尝试飞往开阔地带（第 %d/%d 次：先原地起跳，再找一条开阔航线）", openTriesDone, openTries.get());
    }

    private void flyToOpenTick() {

        if (!mc.player.isGliding()) {
            if (jumpSeq != 0) {
                jumpSeqTick();
                return;
            }
            if (mc.player.isOnGround()) {
                if (Math.abs(mc.player.getVelocity().x) < 0.01 && Math.abs(mc.player.getVelocity().z) < 0.01) {
                    startJumpSequence();
                }
                return;
            }
            fallTicks++;
            if (mc.player.getVelocity().y < -0.1 && fallTicks > 2) {
                fallTicks = 0;

                startJumpSequence();
            }
            return;
        }

        replanIfLost(false);
        double yr = mc.player.isOnGround() ? 1.2 : 1.7;
        FindPathToOpen.Takeoff t = null;
        double usedYh = 0.0;

        if (!ascendingSearch) {
            t = FindPathToOpen.getTakeoffDirection(openSearchDist.get(), openSafeDist.get(), yr, 0.0);
            if (t == null && allowAscend.get()) {
                ascendingSearch = true;
                searchYh = 3.0;
                return;
            }
        }

        if (t == null && ascendingSearch) {
            if (searchYh < 11.0) {
                t = FindPathToOpen.getTakeoffDirection(openSearchDist.get(), openSafeDist.get(), 1.7, searchYh);
                if (t != null) {
                    usedYh = searchYh;
                } else {
                    searchYh += 0.5;
                    return;
                }
            } else {
                ascendingSearch = false;
            }
        }

        if (t != null) {
            ascendingSearch = false;
            searchYh = 0.0;
        }

        if (t == null) {

            openSearchFails++;
            if (openSearchFails < 3) return;
            if (openTriesDone >= openTries.get()) {
                takeoffFallbackOrFail("四周全被挡死，找不到任何可用的起飞航线（已试 " + openTriesDone + " 次）");
                return;
            }
            beginFlyToOpen();
            return;
        }
        openSearchFails = 0;

        int slot = fireworkHotbarSlot();
        if (slot < 0) {
            takeoffFallbackOrFail("快捷栏里没有烟花，没法朝开阔地加速");
            return;
        }
        selectHotbar(slot);

        if (usedYh != 0.0) {

            mc.player.setPitch(-90f);
            InvHelper.useItem(Hand.MAIN_HAND);
            ascendTargetY = mc.player.getY() + usedYh;
            ascendTicks = 0;
            takeoffPhase = TakeoffPhase.ASCEND;
            FOElytraLog.info("四周太窄，先抬头抬升 %.1f 格再找航线", usedYh);
            return;
        }

        mc.player.setYaw(t.yaw);
        mc.player.setPitch(t.pitch);
        InvHelper.useItem(Hand.MAIN_HAND);
        openEnd = t.end;
        openStartY = mc.player.getY();
        hopTicks = 0;
        takeoffPhase = TakeoffPhase.WAIT_ARRIVE;
        FOElytraLog.info("锁定开阔航线：yaw %.0f° / pitch %.0f° → %s", t.yaw, t.pitch, openEnd.toShortString());
    }

    private void ascendTick() {
        if (!mc.player.isGliding()) {
            takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
            return;
        }

        if (mc.player.getY() > ascendTargetY) {
            ascendTicks++;

            mc.player.setPitch(0f);
            mc.player.setYaw(mc.player.getYaw() + 180f);
            if (ascendTicks > 40) {
                ascendTicks = 0;
                if (openTriesDone >= openTries.get()) takeoffFallbackOrFail("抬升之后仍然找不到航线");
                else beginFlyToOpen();
            }
            return;
        }

        ascendTicks++;
        mc.player.setPitch(-90f);
        if (ascendTicks > 20) {
            ascendTicks = 0;
            takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
        }
    }

    private void waitArriveTick() {
        if (openEnd == null) {
            takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
            return;
        }

        if (!mc.player.isGliding()) {

            if (clearHeadBlock.get() && !blockingBlocks(1).isEmpty()) beginClearHead();
            else takeoffPhase = TakeoffPhase.LAUNCH;
            return;
        }

        replanIfLost(false);
        boolean arrived = mc.player.getBlockPos().isWithinDistance(openEnd, 1.5);
        if (!arrived) {

            Vec3d toEnd = Vec3d.ofCenter(openEnd).subtract(mc.player.getEntityPos());
            arrived = toEnd.dotProduct(mc.player.getVelocity()) < 0.0;
        }

        if (!arrived) {

            hopTicks++;
            if (hopTicks % 10 == 0
                && (mc.player.getVelocity().horizontalLength() < 0.5 || mc.player.getY() < openStartY - 4.0)) {
                InvHelper.useItem(Hand.MAIN_HAND);
            }
            return;
        }

        openEnd = null;
        if (openTriesDone >= openTries.get()) {
            takeoffFallbackOrFail("来回冲了 " + openTriesDone + " 次开阔地，Baritone 仍未接管");
            return;
        }
        beginFlyToOpen();
    }

    private void takeoffFallbackOrFail(String reason) {
        PlayerAction.pressJump(false);
        jumpSeq = 0;

        if (!openAreaSearch.get() && takeoffReArmCount < 5) {
            takeoffReArmCount++;
            jumpAttempts = 0;
            takeoffTicks = 0;
            takeoffPhase = TakeoffPhase.LAUNCH;
            takeoffDelayTicks = 0;
            FOElytraLog.warn("起飞重试（第 %d/5 轮，已关闭「开阔地搜索起飞」：不绕开阔地，直接原地再起跳）",
                takeoffReArmCount);
            return;
        }

        if (takeoffAutoJumpFallback.get() && !takeoffAutoJumpUsed) {
            takeoffAutoJumpUsed = true;
            takeoffTicks = 0;
            jumpAttempts = 0;
            openTriesDone = 0;
            openEnd = null;
            clearingHead = false;
            ascendingSearch = false;
            searchYh = 0.0;
            openSearchFails = 0;
            takeoffPhase = TakeoffPhase.LAUNCH;
            BaritoneHook.btSet("elytraAutoJump", true);
            if (segmentTarget != null) BaritoneHook.pathTo(segmentTarget.getX(), segmentTarget.getZ());
            FOElytraLog.warn("本插件的起飞流程失败（%s），临时打开 Baritone 的「自动起跳」再试一次（用 F3 看是不是头顶/脚下被挡）", reason);
            return;
        }

        failNoLogout("起飞失败：" + reason + "（可打开「起飞失败交给 Baritone」，或自己起跳后再开模块）");
    }

    private void replanIfLost(boolean force) {
        if (segmentTarget == null) return;

        if (lavaPredictor != null && lavaPredictor.isAvoiding()) return;
        if (BaritoneHook.isFlying()) return;
        if (!force && tickCounter % 40 != 0) return;
        BaritoneHook.pathTo(segmentTarget.getX(), segmentTarget.getZ());
    }

    private int fireworkHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) return i;
        }
        return -1;
    }

    private void selectHotbar(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            try {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            } catch (Throwable ignored) {
            }
        }
    }

    private void startOpenAreaEscape() {
        if (segmentTarget == null) {
            state = State.PREPARE;
            return;
        }
        segFailStrikes = 0;
        spinTimes = 0;
        takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
        forceFlyToOpen = true;
        flightSettingsApplied = false;
        takeoffTicks = 0;
        openTriesDone = 0;
        openEnd = null;
        state = State.TAKEOFF;
    }

    private void resetTerrainState() {
        terrainProbeTicks = 0;
        terrainDetourCooldown = 0;
        terrainDetour = null;
        terrainDetourTicks = 0;
        terrainClimbTicks = 0;
        terrainClimbStartY = 0.0;
        terrainClimbPausedBaritone = false;
        terrainPending.clear();
        terrainPendingTicks = 0;
        landSkippedCave = 0;
        landSkippedThreat = 0;
        lastHurtHealth = -1.0;
        torchPlacedThisLanding = false;
    }

    private void terrainTick() {
        if (!avoidCaves.get() || segmentTarget == null) {
            terrainPending.clear();
            return;
        }
        if (terrainDetourCooldown > 0) terrainDetourCooldown--;
        if (!resolveTerrainProbes()) return;
        if (terrainDetour != null || terrainClimbTicks > 0 || terrainDetourCooldown > 0) return;
        terrainProbeTicks++;
        if (terrainProbeTicks % Math.max(5, probeInterval.get()) != 0) return;
        scheduleTerrainProbes();
    }

    private void scheduleTerrainProbes() {
        if (!terrainPending.isEmpty()) return;
        int depth = Math.max(8, probeDepth.get());
        int maxDist = Math.max(20, probeDistance.get());
        double yaw = mc.player.getYaw();
        for (int i = 1; i <= TERRAIN_SAMPLE_POINTS; i++) {
            int d = (int) Math.round(maxDist * (double) i / TERRAIN_SAMPLE_POINTS);
            int x = (int) Math.floor(forwardX(yaw, d));
            int z = (int) Math.floor(forwardZ(yaw, d));
            requestProbe(x, z, depth);
            terrainPending.add(new TerrainSample(new BlockPos(x, mc.player.getBlockY(), z), d, "前方 " + d + " 格"));
        }
        requestProbe(segmentTarget.getX(), segmentTarget.getZ(), depth);
        terrainPending.add(new TerrainSample(segmentTarget, 0, "当前航点"));
    }

    private boolean resolveTerrainProbes() {
        if (terrainPending.isEmpty()) {
            terrainPendingTicks = 0;
            return true;
        }
        terrainPendingTicks++;
        if (terrainPendingTicks > TERRAIN_PROBE_TIMEOUT) {
            FOElytraLog.detail("地形探测 %d tick 还没结果，这一轮当成未知（继续飞）", terrainPendingTicks);
            terrainPending.clear();
            terrainPendingTicks = 0;
            return true;
        }
        int depth = Math.max(8, probeDepth.get());
        List<TerrainSample> keep = new ArrayList<>();
        for (TerrainSample s : terrainPending) {
            TerrainProbe.Column col = pollProbe(s.pos().getX(), s.pos().getZ(), depth);
            if (col == null) {
                requestProbe(s.pos().getX(), s.pos().getZ(), depth);
                if (keep.size() < 8) keep.add(s);
                continue;
            }
            if (col.pending()) {
                if (keep.size() < 8) keep.add(s);
                continue;
            }
            if (!col.loaded()) continue;
            if (col.deepCave()) {
                terrainPending.clear();
                terrainPendingTicks = 0;
                triggerTerrainAvoidance(s.label());
                return false;
            }
        }
        terrainPending.clear();
        terrainPending.addAll(keep);
        return true;
    }

    private void triggerTerrainAvoidance(String where) {
        double yaw = mc.player.getYaw();
        int forwardCave = 0;
        for (int i = 1; i <= TERRAIN_SAMPLE_POINTS; i++) {
            int maxDist = Math.max(20, probeDistance.get());
            int d = (int) Math.round(maxDist * (double) i / TERRAIN_SAMPLE_POINTS);
            if (caveAtHeading(yaw, d)) forwardCave++;
        }
        boolean leftCave = caveAtHeading(yaw - TERRAIN_DETOUR_ANGLE, TERRAIN_DETOUR_DISTANCE);
        boolean rightCave = caveAtHeading(yaw + TERRAIN_DETOUR_ANGLE, TERRAIN_DETOUR_DISTANCE);
        double lateralCost = TERRAIN_DETOUR_DISTANCE * 2.0;
        double climbCost = TERRAIN_CLIMB_HEIGHT * 2.0;
        terrainDetourCount++;
        if (leftCave && rightCave) {
            startTerrainClimb(where);
            return;
        }
        if (forwardCave >= TERRAIN_SAMPLE_POINTS || climbCost >= lateralCost) {
            startTerrainDetour(leftCave ? yaw + TERRAIN_DETOUR_ANGLE : yaw - TERRAIN_DETOUR_ANGLE,
                leftCave ? "右侧" : "左侧", where);
            return;
        }
        if (leftCave) {
            startTerrainDetour(yaw + TERRAIN_DETOUR_ANGLE, "右侧", where);
            return;
        }
        if (rightCave) {
            startTerrainDetour(yaw - TERRAIN_DETOUR_ANGLE, "左侧", where);
            return;
        }
        startTerrainClimb(where);
    }

    private void startTerrainDetour(double targetYaw, String side, String where) {
        int x = (int) Math.floor(forwardX(targetYaw, TERRAIN_DETOUR_DISTANCE));
        int z = (int) Math.floor(forwardZ(targetYaw, TERRAIN_DETOUR_DISTANCE));
        terrainDetour = new BlockPos(x, mc.player.getBlockY(), z);
        terrainDetourTicks = 0;
        terrainDetourCooldown = TERRAIN_TRIGGER_COOLDOWN;
        terrainProbeTicks = 0;
        BaritoneHook.pathTo(x, z);
        FOElytraLog.warn("%s下面是洞穴/峡谷 → 绕开（改从%s，临时航点 %s）", where, side, terrainDetour.toShortString());
    }

    private void startTerrainClimb(String where) {
        if (terrainClimbTicks > 0) return;
        terrainClimbTicks = TERRAIN_CLIMB_TICKS;
        terrainClimbStartY = mc.player.getY();
        terrainDetourCooldown = TERRAIN_TRIGGER_COOLDOWN;
        terrainProbeTicks = 0;
        if (!terrainClimbPausedBaritone) {
            BaritoneHook.pause();
            terrainClimbPausedBaritone = true;
        }
        FOElytraLog.warn("%s下面是洞穴/峡谷 → 绕开（改从上方：抬高 %.0f 格再继续）", where, TERRAIN_CLIMB_HEIGHT);
    }

    private boolean terrainClimbTick() {
        if (terrainClimbTicks <= 0) return false;
        terrainClimbTicks--;
        if (terrainClimbTicks == 0) {
            if (terrainClimbPausedBaritone) {
                terrainClimbPausedBaritone = false;
                BaritoneHook.resume();
                BaritoneHook.resetState();
            }
            if (segmentTarget != null) BaritoneHook.pathTo(segmentTarget.getX(), segmentTarget.getZ());
            FOElytraLog.info("抬高完成（+%.0f 格，当前 Y=%.0f），继续飞原航点",
                mc.player.getY() - terrainClimbStartY, mc.player.getY());
            return false;
        }
        if (!mc.player.isGliding()) {
            PlayerAction.pressJump(true);
            return true;
        }
        PlayerAction.pressJump(false);
        mc.player.setPitch(-90.0f);
        if (terrainClimbTicks % 5 == 0) {
            int slot = fireworkHotbarSlot();
            if (slot >= 0) {
                selectHotbar(slot);
                InvHelper.useItem(Hand.MAIN_HAND);
            }
        }
        return true;
    }

    private void terrainDetourTick() {
        if (terrainDetour == null) return;
        terrainDetourTicks++;
        double d = Math.hypot(mc.player.getX() - (terrainDetour.getX() + 0.5),
            mc.player.getZ() - (terrainDetour.getZ() + 0.5));
        boolean timeout = terrainDetourTicks > TERRAIN_DETOUR_TIMEOUT;
        if (!timeout && d > Math.max(12.0, arriveRadius.get())) return;
        FOElytraLog.info("绕行%s：恢复原航点 %s", timeout ? "超时" : "到达",
            segmentTarget == null ? "无" : segmentTarget.toShortString());
        terrainDetour = null;
        terrainDetourTicks = 0;
        if (segmentTarget != null) BaritoneHook.pathTo(segmentTarget.getX(), segmentTarget.getZ());
    }

    private double forwardX(double yaw, double dist) {
        return mc.player.getX() - Math.sin(Math.toRadians(yaw)) * dist;
    }

    private double forwardZ(double yaw, double dist) {
        return mc.player.getZ() + Math.cos(Math.toRadians(yaw)) * dist;
    }

    private boolean caveAtHeading(double yaw, double dist) {
        return caveBelow((int) Math.floor(forwardX(yaw, dist)), (int) Math.floor(forwardZ(yaw, dist)));
    }

    private void requestProbe(int x, int z, int depth) {
        try {
            TerrainProbe.request(x, z, depth);
        } catch (Throwable t) {
            FOElytraLog.detailError("TerrainProbe.request", t);
        }
    }

    private TerrainProbe.Column pollProbe(int x, int z, int depth) {
        try {
            return TerrainProbe.poll(x, z, depth);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean caveBelow(int x, int z) {
        int depth = Math.max(8, probeDepth.get());
        try {
            TerrainProbe.Column col = TerrainProbe.poll(x, z, depth);
            if (col == null) {
                TerrainProbe.request(x, z, depth);
                col = TerrainProbe.probeLoaded(x, z, depth);
            }
            if (col == null || col.pending() || !col.loaded()) return false;
            return col.deepCave();
        } catch (Throwable t) {
            return false;
        }
    }

    private int threatNear(double cx, double cy, double cz, double radius) {
        try {
            double scan = radius + 24.0;
            var list = mc.world.getEntitiesByClass(Entity.class,
                mc.player.getBoundingBox().expand(scan),
                e -> e != null && e.isAlive() && e instanceof Monster
                    && e.squaredDistanceTo(cx, cy, cz) <= radius * radius);
            return list.size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private void abortLandingForSafety() {
        if (supplyTask != null && supplyTask.isRunning()) supplyTask.abort("降落点不安全");
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        landingY = Double.NaN;
        landingTicks = 0;
        manualTask = false;
        supplyTicks = 0;
        supplyErrorRetryCount = 0;
        supplyCooldown = Math.max(supplyCooldown, supplyRetryDelay.get());
        FOElytraLog.info("降落点不安全，取消这次降落，继续飞（下次再试）");
        state = State.PREPARE;
    }

    private boolean pickLandingColumn() {
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        if (landSkipWhenCrowded.get()) {
            int crowd = threatNear(px, py, pz, LAND_THREAT_CROWD_RADIUS);
            if (crowd >= LAND_THREAT_CROWD_COUNT) {
                FOElytraLog.warn("%d 格内有 %d 只敌对生物（≥%d）：按设置跳过这次补给，继续飞",
                    LAND_THREAT_CROWD_RADIUS, crowd, LAND_THREAT_CROWD_COUNT);
                return false;
            }
        }
        int[][] offsets = {{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}, {12, 12}, {-12, 12},
            {12, -12}, {-12, -12}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}};
        landSkippedCave = 0;
        landSkippedThreat = 0;
        for (int[] off : offsets) {
            int x = mc.player.getBlockX() + off[0];
            int z = mc.player.getBlockZ() + off[1];
            if (avoidCaves.get() && caveBelow(x, z)) {
                landSkippedCave++;
                FOElytraLog.detail("降落点 %d %d 地底是洞穴/峡谷，跳过", x, z);
                continue;
            }
            if (landAvoidMobs.get() && threatNear(x + 0.5, py, z + 0.5, landSafeRadius.get()) > 0) {
                landSkippedThreat++;
                FOElytraLog.detail("降落点 %d %d 半径 %d 格内有敌对生物，换个点", x, z, landSafeRadius.get());
                continue;
            }
            int surface = StuckEscape.surfaceY(mc.world, x, z);
            landingTargetX = x + 0.5;
            landingTargetZ = z + 0.5;
            landingY = surface == Integer.MIN_VALUE ? Math.max(-64.0, py - 40.0) : surface;
            FOElytraLog.info("降落点选在 %d %d（跳过地底是洞穴的 %d 个、附近有怪的 %d 个），地面 Y=%.0f",
                x, z, landSkippedCave, landSkippedThreat, landingY);
            return true;
        }
        FOElytraLog.warn("附近候选降落点全不合适（地底是洞穴 %d 个、附近有怪 %d 个）：不硬降，继续飞",
            landSkippedCave, landSkippedThreat);
        return false;
    }

    private boolean supplyHurtTick() {
        float hp = mc.player.getHealth();
        if (!hurtAbortSupply.get()) {
            lastHurtHealth = hp;
            return false;
        }
        double before = lastHurtHealth;
        boolean dropped = lastHurtHealth >= 0.0 && lastHurtHealth - hp >= 2.0;
        boolean hurt = mc.player.hurtTime > 0;
        lastHurtHealth = hp;
        if (!hurt && !dropped) return false;
        FOElytraLog.warn("补给中被攻击（血量 %s → %.1f）→ 中断补给、原地起飞",
            before < 0.0 ? String.format("%.1f", hp) : String.format("%.1f", before), hp);
        if (supplyTask != null && supplyTask.isRunning()) supplyTask.abort("补给中被攻击");
        BlockBreaker.cancel();
        PlayerAction.releaseAll();
        BaritoneHook.stop();
        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        manualTask = false;
        supplyTicks = 0;
        supplyErrorRetryCount = 0;
        supplyCooldown = SUPPLY_HURT_COOLDOWN;
        lastHurtHealth = -1.0;
        FOElytraLog.info("补给已中断，这次补给 %d 秒内不再试（先起飞）", SUPPLY_HURT_COOLDOWN / 20);
        state = State.PREPARE;
        return true;
    }

    private void placeTorchBeforeSupply() {
        if (!torchBeforeSupply.get() || torchPlacedThisLanding) return;
        torchPlacedThisLanding = true;
        int slot = findTorchHotbar();
        if (slot < 0) {
            FOElytraLog.detail("想插火把但背包里没有火把或灯笼，跳过");
            return;
        }
        BlockPos target = InvHelper.findPlaceTarget(mc.player, 2);
        if (target == null) {
            FOElytraLog.detail("想插火把但附近没有可放的位置，跳过");
            return;
        }
        if (InvHelper.placeBlock(target, slot)) {
            FOElytraLog.info("降落点插了火把（%s），降低刷怪", target.toShortString());
        } else {
            FOElytraLog.detail("插火把失败（%s），继续补给", target.toShortString());
        }
    }

    private int findTorchHotbar() {
        int slot = InvHelper.findSlot(s -> s.isOf(Items.TORCH) || s.isOf(Items.SOUL_TORCH)
            || s.isOf(Items.LANTERN) || s.isOf(Items.SOUL_LANTERN), 0, 9);
        if (slot >= 0) return slot;
        int bag = InvHelper.findSlot(s -> s.isOf(Items.TORCH) || s.isOf(Items.SOUL_TORCH)
            || s.isOf(Items.LANTERN) || s.isOf(Items.SOUL_LANTERN), 9, 36);
        if (bag < 0) return -1;
        int empty = InvHelper.findEmptyHotbarSlot();
        if (empty < 0) return -1;
        InvHelper.moveInvToHotbar(bag, empty);
        return empty;
    }

    private boolean stuckEscapeActive() {
        return stuckEscapeMode != STUCK_ESCAPE_NONE && (state == State.TAKEOFF || state == State.FLYING);
    }

    private void resetStuckState() {
        stuckTracker.reset();
        stuckNow = false;
        stuckFireworkHold = false;
        stuckEscapeMode = STUCK_ESCAPE_NONE;
        stuckEscapeTicks = 0;
        stuckEscapeSpot = null;
        stuckHealthStopped = false;
        flightProgressTicks = 0;
        takeoffCeilingChecked = false;
        landingY = Double.NaN;
        landingTicks = 0;
        landingAngle = 0.0;
    }

    private boolean stuckTick() {
        if (!stuckEscape.get()) {
            stuckTracker.reset();
            stuckNow = false;
            return false;
        }
        if (stuckNow) return true;
        boolean detected = stuckTracker.sample(mc, stuckSpeedThreshold.get(), stuckMoveThreshold.get(), stuckHoldTicks.get());
        if (!detected) return false;
        stuckNow = true;
        boolean ceiling = StuckEscape.ceilingBlocked(mc.world, mc.player.getBlockPos(), STUCK_CEILING_UP);
        FOElytraLog.warn("卡住：水平速度 %.3f，20 tick 位移 %.2f 格（%s）→ 暂停烟花、尝试脱离",
            stuckTracker.lastSpeed(), stuckTracker.lastMove(), ceiling ? "顶到天花板" : "侧面撞墙");
        FOElytraLog.detail("卡住判定：连续 %d tick 未推进｜位置 %d %d %d｜滑翔 %s｜血量 %.1f｜目标 %s",
            stuckTracker.holdTicks(), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
            mc.player.isGliding() ? "是" : "否", mc.player.getHealth(),
            segmentTarget == null ? "无" : segmentTarget.toShortString());
        return beginStuckEscape(ceiling ? "卡天花板" : "撞墙");
    }

    private boolean beginStuckEscape(String reason) {
        stuckEscapeTicks = 0;
        PlayerAction.pressUse(false);
        PlayerAction.pressForward(false);
        PlayerAction.pressJump(false);
        BaritoneHook.stop();
        stuckFireworkHold = true;

        if (mc.player.getHealth() <= stuckHealthGuard.get().floatValue()) {
            stuckHealthStop();
            return true;
        }

        BlockPos spot = StuckEscape.findOpenSpot(mc.world, mc.player.getBlockPos(),
            STUCK_OPEN_SPOT_RADIUS, STUCK_CLEAR_UP, STUCK_SPOT_BUDGET);
        if (spot != null) {
            stuckEscapeMode = STUCK_ESCAPE_WALK;
            stuckEscapeSpot = spot;
            BaritoneHook.command("goto " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
            FOElytraLog.warn("脱离（%s）：暂停烟花，交给 Baritone 地面走到最近的开阔点 %s", reason, spot.toShortString());
            return true;
        }
        return aimAtOpening(reason);
    }

    private boolean aimAtOpening(String reason) {
        FindPathToOpen.Takeoff t = FindPathToOpen.getTakeoffDirection(ESCAPE_RAY_DISTANCE, openSafeDist.get(), 1.7, 0.0);
        BlockPos end = t == null ? null : t.end;
        if (end != null && !StuckEscape.skyClear(mc.world, end, STUCK_CLEAR_UP)) end = null;
        if (end == null) {
            stuckEscapeFails++;
            FOElytraLog.err("卡住（%s）第 %d 次脱离失败：附近找不到能走到的开阔点，也没有通向天空的开口", reason, stuckEscapeFails);
            fail("撞墙/卡天花板后脱离失败（" + reason + "）：周围既没有开阔点也没有开口，停下来请自己走出去");
            return true;
        }
        stuckEscapeMode = STUCK_ESCAPE_NONE;
        stuckEscapeSpot = null;
        stuckEscapeFails = 0;
        stuckNow = false;
        stuckTracker.reset();
        stuckFireworkHold = false;
        takeoffCeilingChecked = false;
        flightSettingsApplied = false;
        takeoffPhase = TakeoffPhase.FLY_TO_OPEN;
        openTriesDone = 0;
        openEnd = null;
        clearingHead = false;
        takeoffTicks = 0;
        takeoffFireworksUsed = 0;
        takeoffFireworkCooldown = 0;
        jumpAttempts = 0;
        jumpSeq = 0;
        glidingLostTicks = 0;
        mc.player.setYaw(t.yaw);
        mc.player.setPitch(t.pitch);
        state = State.TAKEOFF;
        FOElytraLog.warn("脱离（%s）：改为对准开口 yaw %.0f° / pitch %.0f° → %s 再飞",
            reason, t.yaw, t.pitch, end.toShortString());
        return true;
    }

    private void stuckEscapeTick() {
        PlayerAction.pressUse(false);
        stuckFireworkHold = true;
        if (InvHelper.screenOpen()) return;
        if (stuckEscapeMode != STUCK_ESCAPE_WALK) {
            stuckEscapeMode = STUCK_ESCAPE_NONE;
            return;
        }
        stuckEscapeTicks++;
        BlockPos spot = stuckEscapeSpot;
        if (spot == null) {
            endStuckEscapeWalk("开阔点丢失");
            return;
        }
        boolean arrived = StuckEscape.horizontalDistance(mc.player.getX(), mc.player.getZ(), spot)
            <= STUCK_WALK_ARRIVE_DISTANCE;
        if (arrived && mc.player.isOnGround() && !mc.player.isGliding()) {
            FOElytraLog.info("脱离成功：已走到开阔点 %s，重新起飞", spot.toShortString());
            stuckEscapeFails = 0;
            endStuckEscapeWalk(null);
            return;
        }
        if (stuckEscapeTicks > STUCK_WALK_TIMEOUT_TICKS) {
            stuckEscapeFails++;
            FOElytraLog.warn("脱离失败：走了 %d 秒还没到开阔点 %s（第 %d 次）",
                stuckEscapeTicks / 20, spot.toShortString(), stuckEscapeFails);
            endStuckEscapeWalk("走不到开阔点");
            return;
        }
        if (stuckEscapeTicks == 1 || stuckEscapeTicks % STUCK_WALK_REPATH_TICKS == 0) {
            BaritoneHook.command("goto " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
        }
        if (stuckEscapeTicks % 200 == 0) {
            FOElytraLog.info("脱离中：距开阔点 %.0f 格（已走 %d 秒）",
                StuckEscape.horizontalDistance(mc.player.getX(), mc.player.getZ(), spot), stuckEscapeTicks / 20);
        }
    }

    private void endStuckEscapeWalk(String why) {
        BaritoneHook.stop();
        stuckEscapeMode = STUCK_ESCAPE_NONE;
        stuckEscapeSpot = null;
        stuckEscapeTicks = 0;
        if (stuckEscapeFails >= STUCK_ESCAPE_MAX_FAILS) {
            stuckNow = false;
            stuckTracker.reset();
            stuckFireworkHold = false;
            segFailStrikes++;
            FOElytraLog.err("连续 %d 次卡住都没脱离成功：本段飞行停止，先交给 Baritone 地面走出去", stuckEscapeFails);
            state = State.PREPARE;
            return;
        }
        if (why == null) {
            stuckNow = false;
            stuckTracker.reset();
            stuckFireworkHold = false;
            takeoffCeilingChecked = false;
            flightSettingsApplied = false;
            takeoffPhase = TakeoffPhase.INIT;
            openTriesDone = 0;
            takeoffTicks = 0;
            jumpAttempts = 0;
            jumpSeq = 0;
            state = State.TAKEOFF;
            return;
        }
        stuckFireworkHold = true;
        aimAtOpening(why);
    }

    private void stuckHealthStop() {
        stuckHealthStopped = true;
        stuckNow = false;
        stuckFireworkHold = true;
        stuckEscapeMode = STUCK_ESCAPE_NONE;
        stuckEscapeSpot = null;
        PlayerAction.pressUse(false);
        PlayerAction.pressForward(false);
        BaritoneHook.stop();
        FOElytraLog.err("卡住期间血量只剩 %.1f（保护线 %.1f）：已经停掉烟花和推进", mc.player.getHealth(), stuckHealthGuard.get());
        FOElytraLog.warn("卡住时血量低于保护线：不再放烟花，就地滑翔降落；想继续请手动接管或重新起飞");
    }

    private void stuckHealthHoldTick() {
        PlayerAction.pressUse(false);
        stuckFireworkHold = true;
        if (mc.player.isOnGround() && !mc.player.isGliding()) {
            stuckHealthStopped = false;
            stuckTracker.reset();
            FOElytraLog.info("血量保护：已经落地，本段停止飞行（可重新起飞或手动接管）");
            state = State.PREPARE;
            return;
        }
        if (tickCounter % 100 == 0) {
            FOElytraLog.warn("血量保护中（%.1f 血）：正在滑翔下降，不放烟花", mc.player.getHealth());
        }
    }

    private boolean noProgressTick() {
        flightProgressTicks++;
        if (flightProgressTicks != NO_PROGRESS_TICKS) return false;
        double moved = Math.hypot(mc.player.getX() - flightStartX, mc.player.getZ() - flightStartZ);
        if (moved >= NO_PROGRESS_DISTANCE) return false;
        FOElytraLog.warn("起飞后 %d 秒只飞了 %.1f 格（不足 %.0f 格）：判定没进展，停止本段飞行",
            NO_PROGRESS_TICKS / 20, moved, NO_PROGRESS_DISTANCE);
        stuckNow = true;
        return beginStuckEscape("起飞后没进展");
    }

    private boolean stuckRescueTick() {

        if (spinPauseTicks > 0) {
            if (--spinPauseTicks == 0) {
                BaritoneHook.resume();
                BaritoneHook.resetState();
                BaritoneHook.repackChunks();
                FOElytraLog.warn("原地绕圈复飞：重置 Baritone 并冲向开阔地");
                startOpenAreaEscape();
            }
            return true;
        }

        int segFails = BaritoneHook.segFailCount();
        if (segFails > 25) {
            if (segFails > 30) {
                fail("baritone寻路异常（Baritone 连续报 'Failed to compute/recompute segment'）");
                return true;
            }
            if (BaritoneHook.consumeSegResetRequest()) {
                BaritoneHook.resetState();
                BaritoneHook.repackChunks();
                FOElytraLog.warn("SegFailed！正在重置 baritone!（6 tick 内 %d 条段失败消息）", segFails);
                startOpenAreaEscape();
                return true;
            }
        }

        if (segFailStrikes >= 4 && !segResetDone) {
            segResetDone = true;
            BaritoneHook.resetState();
            BaritoneHook.repackChunks();
            FOElytraLog.warn("SegFailed！正在重置 baritone!");
            startOpenAreaEscape();
            return true;
        }
        if (segFailStrikes > 8) {
            fail("baritone 寻路异常（连续 " + segFailStrikes + " 段无法抵达目标）");
            return true;
        }

        if (tickCounter % Math.max(100, stuckTicks.get()) == 0) {
            BlockPos now = mc.player.getBlockPos();
            if (lastSpinPos != null && now.isWithinDistance(lastSpinPos, Math.max(25.0, stuckDistance.get()))) {
                FOElytraLog.warn("SegFailed！原地绕圈（第 %d 次）", spinTimes + 1);
                spinTimes++;

                if (spinTimes > 4) {
                    fail("baritone 寻路异常？！疑似原地转圈");
                    return true;
                }
                if (spinTimes > 1) {
                    BaritoneHook.pause();
                    spinPauseTicks = 20;
                } else {
                    BaritoneHook.resetState();
                }
            }
            lastSpinPos = now;
        }
        return false;
    }

    private void flying() {
        if (segmentTarget == null) {
            state = State.PREPARE;
            return;
        }

        if (lavaEscaping()) return;

        if (stuckHealthStopped) {
            stuckHealthHoldTick();
            return;
        }

        if (!BaritoneHook.isFlying()) {
            if (flightGrace > 0) {
                flightGrace--;
                return;
            }
            double dx = mc.player.getX() - (segmentTarget.getX() + 0.5);
            double dz = mc.player.getZ() - (segmentTarget.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist <= arriveRadius.get()) {
                FOElytraLog.info("到达目标 %d, %d（误差 %.1f 格）", segmentTarget.getX(), segmentTarget.getZ(), dist);
                stuckStrikes = 0;
                segFailStrikes = 0;
                segResetDone = false;
                spinTimes = 0;
                BaritoneHook.clearSegFailCounter();
                segmentTarget = null;
                if (lavaPredictor != null) lavaPredictor.reset();
                if (mode.get() == Mode.SingleTarget) {
                    finish("已到达目标坐标");
                    return;
                }
                state = State.PREPARE;
                return;
            }

            segFailStrikes++;

            FOElytraLog.warn("本段提前结束（距目标 %.0f 格，第 %d 次）", dist, segFailStrikes);
            if (lavaPredictor != null) lavaPredictor.reset();
            state = State.PREPARE;
            return;
        }

        if (lavaPredictor != null) {
                           lavaPredictor.tick(mc, segmentTarget);
            if (lavaPredictor.isAvoiding()) {
                return;
            }
        }

        if (flightStatusCheck()) return;

        if (!lavaDanger() && autoSupply.get() && supplyCooldown <= 0
            && ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET) == 0) {
            FOElytraLog.warn("烟花已经用光，立刻降落补给（%s）", supplyReason());
            if (startSupply()) return;
            supplyCooldown = supplyRetryDelay.get();
        }

        if (pauseOnPlayers.get() && playerNearby(playerRange.get())) {
            if (!pausedByPlayer) {
                pausedByPlayer = true;
                BaritoneHook.pause();
                FOElytraLog.info("附近有玩家，暂停飞行等待其离开");
            }
            hover(hoverFirework.get());
            return;
        }
        if (pausedByPlayer) {
            pausedByPlayer = false;
            if (!hovering && !fireballs.isEngaging()) BaritoneHook.resume();
            FOElytraLog.info("玩家已离开，继续飞行");
        }

        if (fireballs.isEngaging() && fireballs.pausedBaritone()) {
            hover(hoverFirework.get());
            return;
        }

        if (!chunkWait.get()) {

            if (hovering) {
                hovering = false;
                if (!pausedByPlayer && !fireballs.pausedBaritone()) BaritoneHook.resume();
                FOElytraLog.info("已关闭区块等待，恢复飞行");
            }
        } else {
            float ratio = unloadedChunkRatio(chunkRadius.get());
            if (!hovering && ratio > unloadedRatio.get()) {
                hovering = true;
                hoverStart = tickCounter;
                BaritoneHook.pause();
                FOElytraLog.warn("未加载区块 %.0f%%，暂停等待（原地盘旋补烟花）", ratio * 100);
            } else if (hovering) {
                boolean loaded = ratio <= Math.max(0.05, unloadedRatio.get() * 0.2);
                boolean timeout = tickCounter - hoverStart > hoverTimeout.get();
                if (loaded || timeout) {
                    hovering = false;
                    if (timeout) FOElytraLog.warn("等待区块超时（%d tick），强制恢复飞行", hoverTimeout.get());
                    else FOElytraLog.info("区块加载完成，继续飞行");
                    if (!pausedByPlayer && !fireballs.pausedBaritone()) BaritoneHook.resume();
                }
            }
        }
        if (hovering) {
            hover(hoverFirework.get());
            return;
        }

        if (terrainClimbTick()) return;
        terrainDetourTick();
        terrainTick();

        if (stuckTick()) return;
        if (noProgressTick()) return;

        if (stuckFix.get() && stuckRescueTick()) return;

        if (fireworkRefill.get()) refillHotbarFireworks();

        boolean lavaNow = lavaDanger();

        if (!lavaNow && autoMend.get() && mendCooldown <= 0 && MendTask.shouldRepair(mendDurability.get())) {
            FOElytraLog.info("鞘翅耐久不足，准备降落修复");
            if (startMend()) return;

            mendCooldown = 100;
        }

        if (!lavaNow && autoSupply.get() && supplyCooldown <= 0 && supplyNeeded()) {
            FOElytraLog.info("触发补给：%s", supplyReason());
            if (startSupply()) return;
            supplyCooldown = supplyRetryDelay.get();
        }
    }

    private void hover(int fireworkInterval) {
        if (tickCounter % 10 == 0) {
            mc.player.setYaw(mc.player.getYaw() + 180.0f);
        }
        mc.player.setPitch(0.0f);
        if (fireworkInterval > 0 && hoverTicker++ >= fireworkInterval) {
            hoverTicker = 0;
            if (mc.player.getVelocity().y < 0.2) useFirework();
        }
    }

    private void refillHotbarFireworks() {
        if (stuckFireworkHold) return;
        if (ItemHelper.countInHotbar(mc.player, Items.FIREWORK_ROCKET) >= fireworkHotbarMin.get()) return;

        int source = InvHelper.findSlot(s -> s.isOf(Items.FIREWORK_ROCKET), 9, 36);
        if (source < 0) return;

        int target = InvHelper.findEmptyHotbarSlot();
        if (target < 0) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isOf(Items.FIREWORK_ROCKET)) continue;
                if (ItemHelper.isFood(s) || s.isOf(Items.TOTEM_OF_UNDYING) || s.isOf(Items.ELYTRA)
                    || s.isOf(Items.ENDER_CHEST) || ItemHelper.isShulkerBox(s)) {
                    continue;
                }
                target = i;
                break;
            }
        }
        if (target < 0) return;

        InvHelper.moveInvToHotbar(source, target);
    }

    private boolean useFirework() {
        if (stuckFireworkHold) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                mc.player.getInventory().setSelectedSlot(i);
                return InvHelper.useItem(Hand.MAIN_HAND);
            }
        }
        return false;
    }

    private boolean playerNearby(double range) {
        for (var entity : mc.world.getPlayers()) {
            if (entity == mc.player || entity.isDead()) continue;
            if (entity.squaredDistanceTo(mc.player) <= range * range) return true;
        }
        return false;
    }

    private float unloadedChunkRatio(int radius) {
        try {
            ChunkPos center = new ChunkPos(mc.player.getBlockPos());
            int r = Math.min(mc.options.getClampedViewDistance(), radius);
            int total = 0;
            int unloaded = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    total++;
                    if (mc.world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false) == null) unloaded++;
                }
            }
            return total > 0 ? (float) unloaded / total : 0f;
        } catch (Throwable t) {
            return 0f;
        }
    }

    private void fireballTick() {
        boolean enabled = deflectFireballs.get();
        FireballDeflector.Result result = fireballs.tick(
            enabled,
            fireballPauseBaritone.get(),
            fireballMax.get(),
            fireballRange.get(),
            !fireballFailOnMultiple.get(),
            this::resumeBaritoneIfIdle
        );

        if (result == FireballDeflector.Result.TOO_MANY) {

            if (!fireballTooManyWarned) {
                fireballTooManyWarned = true;
                FOElytraLog.err("火球太多（超过 %d 个），拦不过来！", fireballMax.get());
            }
            fail("火球太多无法拦截");
            return;
        }

        if (fireballs.tooManyCount() > 0) {
            if (!fireballTooManyWarned) {
                fireballTooManyWarned = true;
                FOElytraLog.err("火球太多（超过 %d 个），只拦最近的一个！", fireballMax.get());
            }
        } else {
            fireballTooManyWarned = false;
        }
    }

    private void resumeBaritoneIfIdle() {
        if (!hovering && !pausedByPlayer) BaritoneHook.resume();
    }

    private void ensureLava() {
        int fireT = lavaTriggerTicks.get();
        int fastT = lavaFastTriggerTicks.get();
        boolean ignoreGliding = lavaIgnoreGlidingFire.get();
        int cd = lavaCooldownTicks.get();
        int retries = lavaMaxRetries.get();
        double p = lavaPitch.get();
        boolean f = lavaUseFirework.get();
        boolean swim = lavaSwimToSafety.get();
        int radius = lavaSearchRadius.get();
        boolean potion = lavaDrinkFireRes.get();
        if (lava == null || fireT != lavaCacheTrigger || fastT != lavaCacheFastTrigger || cd != lavaCacheCooldown
            || retries != lavaCacheRetries || radius != lavaCacheRadius
            || ignoreGliding != lavaCacheIgnoreGliding || p != lavaCachePitch || f != lavaCacheFirework
            || swim != lavaCacheSwim || potion != lavaCachePotion) {

            if (lava != null) lava.release(mc);
            lava = new LavaEscape(fireT, fastT, ignoreGliding, cd, retries, p, f, swim, radius, potion);
            lavaCacheTrigger = fireT;
            lavaCacheFastTrigger = fastT;
            lavaCacheIgnoreGliding = ignoreGliding;
            lavaCacheCooldown = cd;
            lavaCacheRetries = retries;
            lavaCacheRadius = radius;
            lavaCachePitch = p;
            lavaCacheFirework = f;
            lavaCacheSwim = swim;
            lavaCachePotion = potion;
        }
    }

    private void ensureLavaPredictor() {
        boolean en = lavaPredictEnabled.get();
        double horizon = lavaPredictHorizon.get();
        double urgent = lavaPredictUrgent.get();
        int lateral = lavaPredictLateral.get();
        int back = lavaPredictReturn.get();
        int cd = lavaPredictCooldown.get();
        boolean warnOnly = lavaPredictWarnOnly.get();
        boolean pauseBt = lavaPredictPauseBaritone.get();
        double deflect = lavaPredictDeflect.get();
        if (lavaPredictor == null || en != pdCacheEnabled || horizon != pdCacheHorizon || urgent != pdCacheUrgent
            || lateral != pdCacheLateral || back != pdCacheReturn || cd != pdCacheCooldown
            || warnOnly != pdCacheWarnOnly || pauseBt != pdCachePauseBaritone || deflect != pdCacheDeflect) {
            if (lavaPredictor != null) lavaPredictor.release(mc);
            lavaPredictor = new LavaPredictor(new LavaPredictor.Options(
                en, horizon, urgent, lateral, back, cd, warnOnly, pauseBt, deflect));
            pdCacheEnabled = en;
            pdCacheHorizon = horizon;
            pdCacheUrgent = urgent;
            pdCacheLateral = lateral;
            pdCacheReturn = back;
            pdCacheCooldown = cd;
            pdCacheWarnOnly = warnOnly;
            pdCachePauseBaritone = pauseBt;
            pdCacheDeflect = deflect;
            FOElytraLog.detail("岩浆预测（实验性）：%s（预测 %.1f 秒 / 紧急 %.1f 秒 / 侧偏 %d 格 / "
                    + "绕行结束 %d 格 / 防抖 %d tick / 只预警 %s / 紧急暂停 Baritone %s / 偏转 %.0f°）",
                en ? "已启用" : "已关闭", horizon, urgent, lateral, back, cd,
                warnOnly ? "是" : "否", pauseBt ? "是" : "否", deflect);
        }
    }

    private void lavaTick() {
        if (lava == null) return;
        LavaEscape.Result result = lava.tick(lavaEscape.get());

        if (result == LavaEscape.Result.ESCAPING) {

            if (state == State.SUPPLY || state == State.MEND) {
                abortChildTasks();
                state = State.PREPARE;
                FOElytraLog.warn("补给/修复过程中掉进岩浆，已中止并准备脱离");
            }

            supplyCooldown = Math.max(supplyCooldown, supplyRetryDelay.get());
            mendCooldown = Math.max(mendCooldown, supplyRetryDelay.get());
        } else if (result == LavaEscape.Result.FAILED) {

            boolean noFirework = lava.failedNoFirework();
            String lavaWhy = lava.lastReason();
            lava.reset();
            if (lavaFailAbort.get()) {

                failNoLogout(noFirework
                    ? "逃离岩浆失败：快捷栏里找不到烟花（" + lavaWhy + "）"
                    : "逃离岩浆失败（连续自救都没能脱险：" + lavaWhy + "）");
            } else {
                FOElytraLog.warn("逃离岩浆失败，但按设置继续跑图（原因：%s；严格做法是直接结束任务）", lavaWhy);
                restoreViewAfterLava();
            }
        }

        if (lava.consumeJustFinished()) restoreViewAfterLava();
    }

    private void restoreViewAfterLava() {
        if (!lavaRestoreView.get() || mc.player == null) return;

        float yaw = mc.player.getYaw();
        Float aim = aimYaw();
        if (aim != null) yaw = aim;
        float pitch = lavaRestorePitch.get().floatValue();

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        viewHoldTicks = lavaRestoreHold.get();
        FOElytraLog.info("已脱离岩浆：视角回调到目标方向（yaw %.0f° / pitch %.0f°%s）",
            yaw, pitch, aim == null ? "，没有目标就只压平俯仰" : "");

        if (lavaReplan.get() && segmentTarget != null) {
            BaritoneHook.pathTo(segmentTarget.getX(), segmentTarget.getZ());

            if (!BaritoneHook.isFlying() && state == State.FLYING) state = State.PREPARE;
            if (state == State.TAKEOFF) {
                flightSettingsApplied = false;
                takeoffPhase = TakeoffPhase.INIT;
                takeoffTicks = 0;
                jumpAttempts = 0;
                openTriesDone = 0;
                openEnd = null;
                clearingHead = false;
            }
        }
    }

    private void lavaViewHoldTick() {
        if (viewHoldTicks <= 0 || mc.player == null) return;
        if (BaritoneHook.isFlying() || lavaEscaping()) {
            viewHoldTicks = 0;
            return;
        }
        viewHoldTicks--;
        Float aim = aimYaw();
        if (aim != null) mc.player.setYaw(aim);
        mc.player.setPitch(lavaRestorePitch.get().floatValue());
    }

    private Float aimYaw() {
        if (mc.player == null) return null;
        if (mode.get() == Mode.Direction && directionInitialised) return directionFrozen;
        if (segmentTarget != null) {
            double dx = (segmentTarget.getX() + 0.5) - mc.player.getX();
            double dz = (segmentTarget.getZ() + 0.5) - mc.player.getZ();
            if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) return null;
            return (float) ((Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0);
        }
        return null;
    }

    private void safetyTick() {
        if (mc.player == null) return;

        if (autoLogout.get()) {
            int totems = ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING);
            if (mc.player.getHealth() < logoutHealth.get() && totems <= logoutTotemMin.get()) {
                FOElytraLog.err("血量 %.1f 且图腾 %d 个，执行自动登出", mc.player.getHealth(), totems);
                disconnect("血量过低且图腾不足");
            }
        }
    }

    private void disconnect(String reason) {
        try {
            var handler = mc.getNetworkHandler();
            if (handler != null) {
                handler.getConnection().disconnect(Text.of("[AutoElytraFlight] 自动登出：" + reason));
            }
        } catch (Throwable t) {
            FOElytraLog.err("自动登出失败: %s", String.valueOf(t));
        }

        if (state != State.FAILED) state = State.DONE;
        releaseEverything();
        if (disableOnFinish.get() && isActive()) toggle();
    }

    private String foodPriorityRaw = "\u0000";
    private List<Item> foodPriorityParsed = List.of();

    private List<Item> foodPriorityList() {
        String raw = foodPriority.get();
        String key = raw == null ? "" : raw;
        if (!key.equals(foodPriorityRaw)) {
            foodPriorityRaw = key;
            foodPriorityParsed = FoodPriority.parse(key);
            if (!foodPriorityParsed.isEmpty()) {
                FOElytraLog.detail("食物优先级：%d 项已解析（%s）｜进食与补给共用", foodPriorityParsed.size(), key);
            }
        }
        return foodPriorityParsed;
    }

    private SupplyOptions supplyOptions() {
        return new SupplyOptions(
            targetFireworkStacks.get(),
            targetXpBottles.get(),
            targetFoodCount.get(),
            targetTotems.get(),
            targetElytraCount.get(),
            minEnderChests.get(),
            maxShulkers.get(),
            placeRadius.get(),
            actionDelay.get(),
            autoPlaceEnderChest.get(),
            autoPickupEnderChest.get(),
            useBaritoneMine.get(),
            storeLoot.get(),
            storeItems.get() == null ? List.<Item>of() : storeItems.get(),
            supplyFoodItems.get() == null ? List.<Item>of() : supplyFoodItems.get(),
            debugMessages.get(),
            foodPriorityList()
        );
    }

    private int supplyErrorRetriesMax() {
        try {
            return Math.max(1, supplyErrorRetries.get());
        } catch (Throwable t) {
            return 3;
        }
    }

    private boolean startSupply() {
        if (supplyTask != null && supplyTask.isRunning()) {
            FOElytraLog.warn("上一次补给还没结束");
            return false;
        }
        eat.stop();
        mc.options.useKey.setPressed(false);
        BaritoneHook.stop();
        supplyTask = new SupplyTask(supplyOptions());
        supplyTask.start();
        waitTicks = 0;
        landingY = Double.NaN;
        landingTicks = 0;
        landingAngle = 0.0;
        torchPlacedThisLanding = false;
        lastHurtHealth = -1.0;
        stuckNow = false;
        stuckTracker.reset();
        stuckFireworkHold = false;
        stuckHealthStopped = false;
        state = State.LANDING;
        FOElytraLog.info("准备降落补给（目标 %d 组烟花 / %d 瓶 / %d 食物 / %d 图腾 / %d 鞘翅）",
            targetFireworkStacks.get(), targetXpBottles.get(), targetFoodCount.get(),
            targetTotems.get(), targetElytraCount.get());
        return true;
    }

    private void infinityElytraTick() {
        if (!infinityElytra.get() || mc.player == null) return;
        if (state != State.TAKEOFF && state != State.FLYING && state != State.LANDING) return;
        if (!mc.player.isGliding() && !BaritoneHook.isFlying()) return;

        if (tickCounter % 12 == 0) {
            mc.player.stopGliding();
            PlayerAction.sendStartFallFlying();
        } else if (tickCounter % 12 == 1) {
            mc.player.startGliding();
            PlayerAction.sendStartFallFlying();
        }
    }

    private void supplyTick() {
        if (supplyTask == null) {
            state = State.PREPARE;
            return;
        }
        if (supplyHurtTick()) return;

        if (InvHelper.screenOpen()) PlayerAction.restoreHeldKeys();

        supplyTask.tick();
        TaskStatus status = supplyTask.status();

        if (status == TaskStatus.RUNNING) {

            supplyTicks++;
            if (supplyTicks % 100 == 0) {
                FOElytraLog.info("补给进行中（%d 秒）：%s", supplyTicks / 20, supplyTask.progress());
            }

            if (supplyTicks > 2400) {
                FOElytraLog.warn("补给超时（%d 秒），中止本次补给（%s）", supplyTicks / 20, supplyTask.progress());
                supplyTask.abort("补给超时");
                supplyTicks = 0;
                manualTask = false;

                supplyErrorRetryCount++;
                if (supplyErrorRetryCount >= supplyErrorRetriesMax()) {
                    failSupply("补给连续 " + supplyErrorRetryCount + " 次超时（每次 2 分钟还没跑完；"
                        + "最后一次卡在：" + supplyTask.progress() + "）——请检查末影箱/潜影盒是否有空间、"
                        + "快捷栏是否留了空位，或把「补给重试等待」调大后再试");
                    return;
                }
                supplyCooldown = supplyRetryDelay.get();
                FOElytraLog.warn("补给超时第 %d/%d 次：%d tick 后才会再试（不会立刻重开）",
                    supplyErrorRetryCount, supplyErrorRetriesMax(), supplyCooldown);
                state = State.PREPARE;
            }
            return;
        }
        supplyTicks = 0;
        if (status == TaskStatus.IDLE) {
            failSupply("补给任务被中止");
            return;
        }

        if (status == TaskStatus.DONE) {

            if (startFullSupplyPending) {
                startFullSupplyPending = false;
                if (fullSupplyNeeded()) {
                    FOElytraLog.warn("任务开始补给完成，但仍有缺口（%s）；按设置继续起飞", supplyReason());
                } else {
                    FOElytraLog.info("任务开始：物资已补满，起飞");
                }
            }

            Set<Item> gone = supplyTask.exhaustedItems();
            boolean foodGone = supplyTask.foodExhausted();
            if (!gone.isEmpty() || foodGone) {
                exhaustedItems.addAll(gone);
                if (foodGone) foodExhaustedCache = true;
                exhaustedCooldown = SUPPLY_EXHAUSTED_COOLDOWN;
                supplyRetries = 0;
                supplyCooldown = Math.max(supplyCooldown, supplyRetryDelay.get());
                FOElytraLog.warn("补给任务判定这些暂时取不到：%s —— 不再反复降落补给，先用现有的继续跑"
                    + "（要补就往末影箱里塞 / 给背包腾出格子；%d 秒后会再试一次）",
                    describeExhausted(), SUPPLY_EXHAUSTED_COOLDOWN / 20);
            } else if (supplyNeeded() && !manualTask) {
                supplyRetries++;
                if (supplyRetries >= maxSupplyRetries.get()) {
                    failSupply("连续 " + supplyRetries + " 次补给仍未达标（" + supplyReason() + "）");
                    return;
                }
                supplyCooldown = supplyRetryDelay.get();
                FOElytraLog.warn("补给后仍有缺口（第 %d 次）：%s；%d tick 后重试",
                    supplyRetries, supplyReason(), supplyCooldown);
            } else {
                supplyRetries = 0;
                FOElytraLog.info("补给完成%s", manualTask ? "" : "，继续跑图");
            }
            manualTask = false;
            supplyErrorRetryCount = 0;
            state = State.PREPARE;
        } else {
            manualTask = false;

            supplyErrorRetryCount++;
            if (supplyErrorRetryCount >= supplyErrorRetriesMax()) {
                failSupply("补给连续 " + supplyErrorRetryCount + " 次出错（最后一次：" + supplyTask.failReason() + "）");
                return;
            }
            FOElytraLog.warn("补给过程中出错（第 %d/%d 次）：%s —— 先不判失败，%d tick 后重试补给",
                supplyErrorRetryCount, supplyErrorRetriesMax(), supplyTask.failReason(),
                supplyRetryDelay.get());

            BaritoneHook.stop();
            PlayerAction.releaseAll();
            if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
            supplyTicks = 0;
            supplyCooldown = supplyRetryDelay.get();
            state = State.PREPARE;
        }
    }

    private boolean isExhausted(Item item) {
        return exhaustedCooldown > 0 && exhaustedItems.contains(item);
    }

    private boolean foodExhaustedNow() {
        return exhaustedCooldown > 0 && foodExhaustedCache;
    }

    private String describeExhausted() {
        StringBuilder sb = new StringBuilder();
        for (Item it : exhaustedItems) {
            if (sb.length() > 0) sb.append("、");
            sb.append(it.getName().getString());
        }
        if (foodExhaustedCache) {
            if (sb.length() > 0) sb.append("、");
            sb.append("食物");
        }
        return sb.length() == 0 ? "无" : sb.toString();
    }

    private boolean supplyNeeded() {
        if (!isExhausted(Items.FIREWORK_ROCKET)
            && ItemHelper.toStacks(Items.FIREWORK_ROCKET,
                ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET)) < minFireworkStacks.get()) return true;
        if (!foodExhaustedNow() && countFood() < minFoodCount.get()) return true;
        if (!isExhausted(Items.TOTEM_OF_UNDYING)
            && ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING) < minTotems.get()) return true;

        if (autoMend.get() && !isExhausted(Items.EXPERIENCE_BOTTLE)
            && ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE) < minXpBottles.get()) return true;
        if (!isExhausted(Items.ELYTRA)
            && ItemHelper.totalElytraDurability(mc.player) < minElytraDurability.get()) return true;

        if (autoRestock.get() && restockTriggerSupply.get()
            && InventoryRestocker.shortage(restockItems.get(), restockStacks.get()) != null
            && restockShortageNotExhausted()) return true;
        return false;
    }

    private String supplyStockSignature(String need) {
        if (mc.player == null) return "";
        StringBuilder sb = new StringBuilder();
        if (need.contains("烟花")) sb.append("fw=").append(ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET)).append(';');
        if (need.contains("经验瓶")) sb.append("xp=").append(ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE)).append(';');
        if (need.contains("图腾")) sb.append("tot=").append(ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING)).append(';');
        if (need.contains("食物")) sb.append("food=").append(countFood()).append(';');
        if (sb.length() == 0) {
            sb.append("all=").append(ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET)).append(',')
                .append(ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE)).append(',')
                .append(ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING)).append(',')
                .append(countFood()).append(',')
                .append(countUsableElytra());
        }
        return sb.toString();
    }

    private void registerUnobtainableFromNeed(String need) {
        Set<Item> blocked = new LinkedHashSet<>();
        if (need.contains("烟花")) blocked.add(Items.FIREWORK_ROCKET);
        if (need.contains("经验瓶")) blocked.add(Items.EXPERIENCE_BOTTLE);
        if (need.contains("图腾")) blocked.add(Items.TOTEM_OF_UNDYING);
        boolean foodBlocked = need.contains("食物");
        if (blocked.isEmpty() && !foodBlocked) return;

        exhaustedItems.addAll(blocked);
        if (foodBlocked) foodExhaustedCache = true;
        exhaustedCooldown = SUPPLY_EXHAUSTED_COOLDOWN;
        supplyRetries = 0;
        FOElytraLog.warn("连续 %d 轮都为「%s」降落补给，却一点进展都没有（多半是背包没空位放，"
            + "或者那个盒子里的东西拿不出来）→ 先当成「暂时取不到」：%d 秒内不再为它降落，"
            + "用现有的物资继续飞（时间到了会自动再试一次）",
            supplyNoProgressRounds, need.trim(), SUPPLY_EXHAUSTED_COOLDOWN / 20);
        FOElytraLog.detail("活锁保护登记：缺口「%s」→ 登记 %s（冷却 %d tick）",
            need.trim(), describeExhausted(), SUPPLY_EXHAUSTED_COOLDOWN);
    }

    private boolean restockShortageNotExhausted() {
        if (exhaustedCooldown <= 0) return true;
        List<Item> items = restockItems.get();
        if (items == null || items.isEmpty()) return true;
        for (Item it : items) {
            if (!InventoryRestocker.isShort(mc.player, it, restockStacks.get())) continue;
            if (!isExhausted(it)) return true;
        }
        return false;
    }

    private String supplyNeedText() {
        if (mc.player == null) return "";
        int fwNeed = Math.max(0, targetFireworkStacks.get()
            - ItemHelper.toStacks(Items.FIREWORK_ROCKET, ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET)));
        int xpNeed = 0;
        if (autoMend.get()) {
            xpNeed = (int) Math.ceil(Math.max(0,
                targetXpBottles.get() - ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE)) / 64.0);
        }
        ItemStack hotbar3 = mc.player.getInventory().getStack(3);
        ItemStack hotbar4 = mc.player.getInventory().getStack(4);
        ItemStack hotbar5 = mc.player.getInventory().getStack(5);
        boolean hasTotem = hotbar3.isOf(Items.TOTEM_OF_UNDYING) || hotbar4.isOf(Items.TOTEM_OF_UNDYING);
        boolean hasFood = matchesFoodSlot(hotbar5) && hotbar5.getCount() > 18;

        StringBuilder sb = new StringBuilder();
        if (fwNeed != 0 && !isExhausted(Items.FIREWORK_ROCKET)) sb.append("烟花 ").append(fwNeed).append(" 组；");
        if (xpNeed != 0 && !isExhausted(Items.EXPERIENCE_BOTTLE)) sb.append("经验瓶 ").append(xpNeed).append(" 组；");
        if (!hasTotem && !isExhausted(Items.TOTEM_OF_UNDYING)) sb.append("快捷栏 3/4 格没有图腾；");
        if (!hasFood && !foodExhaustedNow()) sb.append("快捷栏 5 格没有食物（或不足 19 个）；");
        return sb.toString();
    }

    private boolean matchesFoodSlot(ItemStack s) {
        if (s.isEmpty()) return false;
        List<Item> whitelist = supplyFoodItems.get();
        if (whitelist != null && !whitelist.isEmpty()) return whitelist.contains(s.getItem());
        return ItemHelper.isFood(s);
    }

    private boolean fullSupplyNeeded() {
        if (mc.player == null) return false;
        int fwGap = targetFireworkStacks.get() - ItemHelper.toStacks(Items.FIREWORK_ROCKET,
            ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET));
        if (fwGap >= 1 && !isExhausted(Items.FIREWORK_ROCKET)) return true;
        if (autoMend.get() && !isExhausted(Items.EXPERIENCE_BOTTLE)) {
            int xpGap = targetXpBottles.get() - ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE);
            if (xpGap >= 64) return true;
        }
        int totemGap = targetTotems.get() - ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING);
        if (totemGap >= 1 && !isExhausted(Items.TOTEM_OF_UNDYING)) return true;
        if (!foodExhaustedNow() && countFood() < targetFoodCount.get() - 32) return true;
        return !isExhausted(Items.ELYTRA) && countUsableElytra() < targetElytraCount.get();
    }

    private String fullSupplyGap() {
        if (mc.player == null) return "无";
        StringBuilder sb = new StringBuilder();
        int fwGap = targetFireworkStacks.get() - ItemHelper.toStacks(Items.FIREWORK_ROCKET,
            ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET));
        if (fwGap >= 1) sb.append("烟花还差 ").append(fwGap).append(" 组；");
        if (autoMend.get()) {
            int xpGap = targetXpBottles.get() - ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE);
            if (xpGap >= 64) sb.append("经验瓶还差 ").append(xpGap).append("；");
        }
        int totemGap = targetTotems.get() - ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING);
        if (totemGap >= 1) sb.append("图腾还差 ").append(totemGap).append("；");
        if (countFood() < targetFoodCount.get() - 32) sb.append("食物还差 ").append(targetFoodCount.get() - countFood()).append("；");
        if (countUsableElytra() < targetElytraCount.get()) sb.append("备用鞘翅还差 ").append(targetElytraCount.get() - countUsableElytra()).append("；");
        return sb.length() == 0 ? "无（差额都不值得降落）" : sb.toString();
    }

    private int countUsableElytra() {
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 41; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.ELYTRA)
                && ItemHelper.hasEnchantment(s, net.minecraft.enchantment.Enchantments.UNBREAKING, 3)
                && s.getDamage() < 15) n++;
        }
        return n;
    }

    private String supplyReason() {
        String restock = autoRestock.get()
            ? InventoryRestocker.shortage(restockItems.get(), restockStacks.get())
            : null;
        return "烟花 " + ItemHelper.toStacks(Items.FIREWORK_ROCKET,
            ItemHelper.countInInventory(mc.player, Items.FIREWORK_ROCKET)) + " 组 / 食物 " + countFood()
            + " / 经验瓶 " + ItemHelper.countInInventory(mc.player, Items.EXPERIENCE_BOTTLE)
            + " / 图腾 " + ItemHelper.countInInventory(mc.player, Items.TOTEM_OF_UNDYING)
            + " / 鞘翅总耐久 " + ItemHelper.totalElytraDurability(mc.player)
            + (restock == null ? "" : " / 清单缺口：" + restock);
    }

    private boolean lavaDanger() {
        if (mc.player == null) return false;
        if (lava != null && lava.isEscaping()) return true;
        return mc.player.isOnFire() || mc.player.isInLava();
    }

    private boolean lavaEscaping() {
        return lava != null && lava.isEscaping();
    }

    private int countFood() {
        int n = 0;
        List<Item> whitelist = supplyFoodItems.get();
        boolean useWhitelist = whitelist != null && !whitelist.isEmpty();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            if (useWhitelist ? whitelist.contains(s.getItem()) : ItemHelper.isFood(s)) n += s.getCount();
        }
        return n;
    }

    private void landing() {
        if (InvHelper.screenOpen()) {
            if (waitTicks % 200 == 0) FOElytraLog.warn("等你关闭界面后再开始补给（已等 %d 秒）", waitTicks / 20);
            if (waitTicks++ > 2400) {
                fail("等你关界面等了 2 分钟，补给取消（可手动触发补给键重试）");
            }
            return;
        }
        if (mc.player.isOnGround() && !mc.player.isGliding()) {
            BaritoneHook.stop();
            landingY = Double.NaN;
            stuckFireworkHold = false;
            placeTorchBeforeSupply();
            state = State.SUPPLY;
            FOElytraLog.detail("已落地（%d %d %d），进入补给执行",
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            return;
        }
        if (supplyHurtTick()) return;
        if (!landForSupply.get()) {
            BaritoneHook.stop();
            landingY = Double.NaN;
            placeTorchBeforeSupply();
            state = State.SUPPLY;
            return;
        }

        if (Double.isNaN(landingY)) {
            if (!pickLandingColumn()) {
                abortLandingForSafety();
                return;
            }
            landingTicks = 0;
            landingAngle = 0.0;
            stuckFireworkHold = false;
            stuckNow = false;
            stuckTracker.reset();
            BaritoneHook.stop();
            FOElytraLog.info("开始收敛下降：目标地面 Y=%.0f（当前 Y=%.1f），已停掉绕圈飞行", landingY, mc.player.getY());
        }

        landingTicks++;
        waitTicks++;
        descentTick();

        if (landingTicks % 60 == 0) {
            double above = mc.player.getY() - landingY;
            FOElytraLog.info("下降中（%d 秒）：高度 %.1f｜离地面 %.1f 格｜盘旋半径 %.1f｜滑翔 %s",
                landingTicks / 20, mc.player.getY(), above, Math.max(LANDING_SPIRAL_MIN_RADIUS, above / LANDING_SPIRAL_DIVISOR),
                mc.player.isGliding() ? "是" : "否");
        }
        if (waitTicks > 2400) {
            failSupply("降落超时，无法补给");
        }
    }

    private void descentTick() {
        if (!mc.player.isGliding()) {
            mc.player.setPitch(mc.player.isOnGround() ? 0.0f : 20.0f);
            return;
        }
        double above = mc.player.getY() - landingY;
        double radius = Math.max(LANDING_SPIRAL_MIN_RADIUS, above / LANDING_SPIRAL_DIVISOR);
        landingAngle += LANDING_SPIRAL_STEP;
        double aimX = landingTargetX;
        double aimZ = landingTargetZ;
        if (above > LANDING_SPIRAL_MIN_RADIUS) {
            aimX += Math.cos(landingAngle) * radius;
            aimZ += Math.sin(landingAngle) * radius;
        }
        double dx = aimX - mc.player.getX();
        double dz = aimZ - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setPitch(above > LANDING_NO_FIREWORK_ABOVE ? 8.0f : 22.0f);

        double vy = Math.max(0.05, -mc.player.getVelocity().y);
        boolean withinCutoff = above / vy < LANDING_CUTOFF_SECONDS;
        boolean noFirework = above <= LANDING_NO_FIREWORK_ABOVE || withinCutoff;
        if (noFirework) return;
        if (landingTicks % LANDING_FIREWORK_INTERVAL != 0) return;
        if (mc.player.getVelocity().y >= 0.2) return;
        useFirework();
    }

    private MendTask.Options mendOptions() {
        return new MendTask.Options(
            mendDurability.get(),
            Math.max(1, mendDurability.get() / 4),
            minBottles.get(),
            repairToDamage.get(),
            requireGround.get(),
            requireNetherWastes.get(),
            requireMending.get(),
            mendPitch.get(),
            throwDelay.get(),
            maxThrows.get(),
            landingTimeout.get()
        );
    }

    private boolean startMend() {
        if (mendTask != null && mendTask.isRunning()) {
            FOElytraLog.warn("上一次修复还没结束");
            return false;
        }
        eat.stop();
        mc.options.useKey.setPressed(false);
        BaritoneHook.stop();
        mendTask = new MendTask(mendOptions());
        mendTask.start();
        if (mendTask.status() != TaskStatus.RUNNING) {

            FOElytraLog.warn("修鞘翅没能开始：%s", mendTask.failReason());
            return false;
        }
        state = State.MEND;
        return true;
    }

    private void mendTick() {
        if (mendTask == null) {
            state = State.PREPARE;
            return;
        }
        mendTask.tick();
        TaskStatus status = mendTask.status();

        if (status == TaskStatus.RUNNING) return;
        if (status == TaskStatus.IDLE) {
            fail("修鞘翅任务被中止");
            return;
        }

        if (status == TaskStatus.DONE) {
            mendRetries = 0;
            FOElytraLog.info("鞘翅修复完成%s", manualTask ? "" : "，继续跑图");
        } else {
            mendRetries++;
            FOElytraLog.warn("修鞘翅失败：%s（第 %d 次）", mendTask.failReason(), mendRetries);
            if (mendRetries >= maxMendRetries.get()) {
                autoMend.set(false);
                FOElytraLog.err("连续 %d 次修复失败，已自动关闭「启用自动修鞘翅」", mendRetries);
            }
        }
        manualTask = false;
        state = State.PREPARE;
    }

    private void finish(String message) {
        FOElytraLog.info("%s", message);
        state = State.DONE;
        releaseEverything();
        if (logoutOnArrive.get()) {
            disconnect(message);
            return;
        }
        if (disableOnFinish.get() && isActive()) toggle();
    }

    private void fail(String reason) {
        if (state == State.FAILED) return;
        failReason = reason;
        FOElytraLog.err("任务失败：%s", reason);
        FOElytraLog.detail("失败诊断：原因 %s｜状态 %s｜补给阶段 %s｜位置 %s｜地面 %s｜滑翔 %s｜烟花 %d 发｜Baritone %s",
            reason, state.toString(), supplyFailPhase ? "是" : "否",
            mc.player == null ? "-" : String.format("%d %d %d",
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()),
            mc.player != null && mc.player.isOnGround() ? "是" : "否",
            mc.player != null && mc.player.isGliding() ? "是" : "否",
            mc.player == null ? 0 : ItemHelper.countInHotbar(mc.player, Items.FIREWORK_ROCKET),
            BaritoneHook.isFlying() ? "接管中" : "未接管");
        BaritoneHook.stop();
        state = State.FAILED;
        releaseEverything();
        boolean logout = logoutOnFailure.get() && (!supplyFailPhase || logoutOnSupplyFail.get())
            && !suppressLogout;
        supplyFailPhase = false;
        suppressLogout = false;

        try {
            FOElytraLog.snapshot(reason, 200, 0);
        } catch (Throwable t) {
            FOElytraLog.detailError("fail snapshot", t);
        }

        if (logout) {
            disconnect(reason);
            return;
        }
        if (disableOnFinish.get() && isActive()) toggle();
    }

    private void failSupply(String reason) {
        supplyFailPhase = true;
        fail(reason);
    }

    private void failNoLogout(String reason) {
        suppressLogout = true;
        fail(reason);
        suppressLogout = false;
    }

    private void releaseEverything() {
        PlayerAction.releaseAll();
        resetStuckState();
        resetTerrainState();

        PlayerAction.restoreHeldKeys();
        if (lava != null) lava.release(mc);
        if (lavaPredictor != null) lavaPredictor.release(mc);
        eat.stop();
        abortChildTasks();
    }

    private void abortChildTasks() {
        try {
            if (supplyTask != null && supplyTask.isRunning()) supplyTask.abort("任务结束");
            if (mendTask != null && mendTask.isRunning()) mendTask.abort("任务结束");
        } catch (Throwable t) {
            LOG.warn("abortChildTasks 失败", t);
        }

        if (InvHelper.hasContainerOpen()) InvHelper.closeScreen();
        mc.options.forwardKey.setPressed(false);
        eat.stop();
    }

    public State travelState() {
        return state;
    }

    public String failReason() {
        return failReason;
    }

    public BlockPos currentTarget() {
        return segmentTarget;
    }

    public Needs currentNeeds() {
        return supplyTask == null ? new Needs() : supplyTask.needs();
    }
}
