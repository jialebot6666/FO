package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.BlockClicker;
import com.fo.addon.utils.FacingLogic;
import com.fo.addon.utils.PeerMessageLogic;
import com.fo.addon.utils.Rotation;
import com.fo.addon.utils.RotationManager;
import com.fo.addon.utils.StashMoveLogic;
import com.fo.addon.utils.StashPearlLogic;
import com.fo.addon.utils.StashStats;
import com.fo.addon.utils.StashStatsSetting;
import com.fo.addon.utils.StringMapSetting;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FO 自动搬运（V6.7 新增）。
 *
 * <p>一个模块三种模式（模式下拉）：
 * <ul>
 *   <li><b>搬运端（双号）</b>：移植 misaka StashMover 的搬运状态机（开箱取物 → 发拉我N → 等确认 →
 *       投滞留珍珠 → 还珍珠 → 卸货 → 自杀回重生点），私信换成 3C3U 的 「/m 玩家名 拉我N / 收到N」；</li>
 *   <li><b>拉珍珠端（双号）</b>：移植 miku 珍珠大管家（珍珠点映射 + 右键活板门录入 + 告示牌识别 +
 *       距离检查 + Baritone 寻路 + miku 交互工具点活板门 + 赶路超时 + 回出发点）；</li>
 *   <li><b>三号模式</b>：1 号搬运 + 2 号待搬运箱端 + 3 号存储箱端，三个号各开一个角色；
 *       1 号在两区之间纯拉珍珠往返、不自杀；珍珠箱全路线只有一个，&lt;4 精确补到 16、不还珍珠。
 *       另可只开 1 号搬运端，把 2/3 号交给 ZenithProxy 插件拉珍珠。</li>
 * </ul>
 *
 * <p>相对 misaka 修复的 3 个 bug：
 * <ol>
 *   <li>等「收到N」加了超时 + 自动重发（原实现只发一次，丢消息就永久卡死）；</li>
 *   <li>珍珠滞留检测只认<b>自己的</b>珍珠（原实现会把别人的珍珠当成功）；</li>
 *   <li>删掉投掷前「准星指方块就等视线清空」检查（水柱结构下永不通过，直接 pitch 90° 投）。</li>
 * </ol>
 *
 * <p>⚠️ 双号搬运：<b>重生点必须设在待搬运箱旁边</b>（箱子搜索半径最大 20 格，超出会一直报警）。
 */
public class StashMover extends Module {

    public enum Mode {
        MOVER("搬运端（双号）"),
        PEARL("拉珍珠端（双号）"),
        TRIO("三号模式");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ChatCommand {
        SLASH_M("/m"),
        TELL("/tell");

        private final String label;

        ChatCommand(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ChestMode {
        AUTO("自动搜索"),
        SINGLE("单箱子"),
        RANGE("区域框选");

        private final String label;

        ChestMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SuicideMode {
        COMMAND("指令"),
        LAVA("岩浆");

        private final String label;

        SuicideMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum MoverState {
        LOOT, SEND, WAIT_ACK, WALK_WATER, TAKE_PEARL, THROW, VERIFY, PUT_BACK,
        WALK_STORE, UNLOAD, SUICIDE_PREP, SUICIDE_LAVA, DEAD_WAIT, REFILL, STUCK
    }

    /** 三号模式 1 号搬运端的阶段（V6.7p3 重排：校正 → 补货 → 投珍珠 → 取货/存货 → 私信）。 */
    private enum TrioState {
        SETTLE, RESTOCK, THROW, LOOT, UNLOAD, SEND, WAIT_TELEPORT, STUCK
    }

    // ==================== 设置 ====================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMover = settings.createGroup("搬运（双号）");
    private final SettingGroup sgPearl = settings.createGroup("拉珍珠");
    private final SettingGroup sgTrio = settings.createGroup("三号模式");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("搬运端（双号）/ 拉珍珠端（双号）/ 三号模式（1搬运+2拉珍珠，三个号各开一个角色）。")
        .defaultValue(Mode.MOVER)
        .build());

    private final Setting<ChatCommand> chatCommand = sgGeneral.add(new EnumSetting.Builder<ChatCommand>()
        .name("私聊指令")
        .description("发私信用的服务器指令：/m 或 /tell。")
        .defaultValue(ChatCommand.SLASH_M)
        .build());

    private final Setting<Boolean> waitAck = sgGeneral.add(new BoolSetting.Builder()
        .name("等待确认")
        .description("发完「拉我N」等对方的「收到N」。对方是 ZenithProxy 机器人且不回复时关掉。")
        .defaultValue(true)
        .build());

    private final Setting<String> ackKeyword = sgGeneral.add(new StringSetting.Builder()
        .name("确认关键字")
        .description("对方确认消息里的关键字（默认收到）。回复里带数字就校验对应 N，不带数字也放行（兼容机器人）。")
        .defaultValue("收到")
        .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("调试输出")
        .description("在聊天栏输出状态机调试信息。")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> statsSetting = sgGeneral.add(new StashStatsSetting.Builder()
        .name("本次搬运")
        .description("只统计本次在线：趟数 + 潜影盒数。关模块不清零，断线/退服清零；右侧按钮可手动重置。")
        .build());

    // ---------- 搬运端（双号） ----------

    private final Setting<String> targetPlayer = sgMover.add(new StringSetting.Builder()
        .name("搬运目标玩家")
        .description("双号模式：拉珍珠搭档的玩家 ID（发给对方「拉我N」，等对方「收到N」）。")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<ChestMode> chestMode = sgMover.add(new EnumSetting.Builder<ChestMode>()
        .name("待搬运方式")
        .description("自动搜索=以玩家为中心扫箱子；单箱子=只搬目标箱子；区域框选=搬指定长方体范围内所有箱子。")
        .defaultValue(ChestMode.AUTO)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<BlockPos> storeChest = sgMover.add(new BlockPosSetting.Builder()
        .name("存储箱位置")
        .description("卸货的箱子（卸完背包后自杀回重生点）。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<BlockPos> pearlChest = sgMover.add(new BlockPosSetting.Builder()
        .name("珍珠箱位置")
        .description("存放末影珍珠的箱子（投掷前从这里取珍珠，多余的还回去）。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<BlockPos> trapdoor = sgMover.add(new BlockPosSetting.Builder()
        .name("搬运活板门位置")
        .description("珍珠房活板门坐标（其正下方 1 格是水，投珍珠进水柱做滞留）。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<BlockPos> targetChest = sgMover.add(new BlockPosSetting.Builder()
        .name("目标箱子位置")
        .description("「单箱子」模式要搬的那个箱子。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.MOVER && chestMode.get() == ChestMode.SINGLE)
        .build());

    private final Setting<BlockPos> pos1 = sgMover.add(new BlockPosSetting.Builder()
        .name("框选位置1")
        .description("「区域框选」模式的长方体一角。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.MOVER && chestMode.get() == ChestMode.RANGE)
        .build());

    private final Setting<BlockPos> pos2 = sgMover.add(new BlockPosSetting.Builder()
        .name("框选位置2")
        .description("「区域框选」模式的长方体另一角。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.MOVER && chestMode.get() == ChestMode.RANGE)
        .build());

    private final Setting<Integer> clickDelay = sgMover.add(new IntSetting.Builder()
        .name("箱子点击延迟")
        .description("每次槽位点击之间隔多少 tick（防发包过快被踢）。")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<Integer> searchDistance = sgMover.add(new IntSetting.Builder()
        .name("箱子搜索距离")
        .description("「自动搜索」模式扫描箱子的半径（格）。⚠️ 重生点必须在这个范围内。")
        .defaultValue(10)
        .min(1)
        .sliderMax(20)
        .visible(() -> mode.get() == Mode.MOVER || mode.get() == Mode.TRIO)
        .build());

    private final Setting<Boolean> onlyShulker = sgMover.add(new BoolSetting.Builder()
        .name("只拿潜影盒")
        .description("只搬潜影盒，其他物品不动。")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.MOVER || mode.get() == Mode.TRIO)
        .build());

    private final Setting<Boolean> ignoreSingleChest = sgMover.add(new BoolSetting.Builder()
        .name("忽略单个箱子")
        .description("「自动搜索」模式只搬双箱，不碰单箱。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.MOVER && chestMode.get() == ChestMode.AUTO)
        .build());

    private final Setting<Boolean> waitForFill = sgMover.add(new BoolSetting.Builder()
        .name("等待箱子填充")
        .description("「单箱子」模式：箱子拿空后原地等它重新填满。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.MOVER && chestMode.get() == ChestMode.SINGLE)
        .build());

    private final Setting<Integer> waitTimeout = sgMover.add(new IntSetting.Builder()
        .name("等待超时")
        .description("等箱子填充最多等多少秒。")
        .defaultValue(30)
        .min(1)
        .sliderMax(60)
        .visible(() -> mode.get() == Mode.MOVER && chestMode.get() == ChestMode.SINGLE && waitForFill.get())
        .build());

    private final Setting<Boolean> closeWhenFull = sgMover.add(new BoolSetting.Builder()
        .name("箱子满自动关闭")
        .description("存储箱塞满时自动关模块（关闭则原地等待）。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.MOVER || mode.get() == Mode.TRIO)
        .build());

    private final Setting<SuicideMode> suicideMode = sgMover.add(new EnumSetting.Builder<SuicideMode>()
        .name("自杀方式")
        .description("指令=发自杀指令；岩浆=找附近岩浆跳进去。")
        .defaultValue(SuicideMode.COMMAND)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<String> suicideCommand = sgMover.add(new StringSetting.Builder()
        .name("自杀指令")
        .description("自杀指令内容（带不带 / 都行）。")
        .defaultValue("/suicide --confirm")
        .visible(() -> mode.get() == Mode.MOVER && suicideMode.get() == SuicideMode.COMMAND)
        .build());

    private final Setting<Boolean> suicideAfterPearl = sgMover.add(new BoolSetting.Builder()
        .name("投掷珍珠后延时自杀")
        .description("投完滞留珍珠先等一段时间再死，防止珍珠消失。")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<Integer> suicideDelay = sgMover.add(new IntSetting.Builder()
        .name("自杀延时")
        .description("投掷珍珠后等多少 tick 才能自杀（20 tick = 1 秒）。")
        .defaultValue(660)
        .min(0)
        .sliderMax(1200)
        .visible(() -> mode.get() == Mode.MOVER && suicideAfterPearl.get())
        .build());

    private final Setting<Integer> lavaRadius = sgMover.add(new IntSetting.Builder()
        .name("岩浆搜索半径")
        .description("找岩浆的最大半径（格）。")
        .defaultValue(100)
        .min(10)
        .sliderMin(10)
        .sliderMax(100)
        .visible(() -> mode.get() == Mode.MOVER && suicideMode.get() == SuicideMode.LAVA)
        .build());

    // ---------- 拉珍珠（双号 / 三号 2、3 号端） ----------

    private final Setting<String> keyword = sgPearl.add(new StringSetting.Builder()
        .name("拉我关键字")
        .description("收到含该关键字的私信就拉珍珠（后面可跟数字，如「拉我3」）。")
        .defaultValue("拉我")
        .build());

    private final Setting<java.util.Map<String, String>> pearlPoints = sgPearl.add(new StringMapSetting.Builder()
        .name("珍珠点")
        .description("左边玩家 ID、右边活板门坐标（x y z）。默认靠告示牌识别自动记录，也可右键录入。")
        .defaultValue(new LinkedHashMap<>())
        .build());

    private final Setting<Boolean> setupMode = sgPearl.add(new BoolSetting.Builder()
        .name("添加珍珠点模式")
        .description("开启后右键活板门即可记录一个珍珠点（自动起名，之后在「珍珠点」表里改成玩家 ID）。")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> searchSigns = sgPearl.add(new BoolSetting.Builder()
        .name("智能识别告示牌")
        .description("没找到某个玩家的珍珠点记录时，扫附近告示牌：写有该玩家名字的告示牌正下方就是珍珠点。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> signRadius = sgPearl.add(new IntSetting.Builder()
        .name("搜索告示牌半径")
        .description("扫描告示牌的水平半径（格）。")
        .defaultValue(64)
        .min(8)
        .sliderMin(8)
        .sliderMax(128)
        .visible(searchSigns::get)
        .build());

    private final Setting<Integer> pathTimeout = sgPearl.add(new IntSetting.Builder()
        .name("赶路超时")
        .description("拉珍珠最多允许赶路多少 tick（20 tick = 1 秒），超时自动放弃。")
        .defaultValue(200)
        .min(20)
        .sliderMin(20)
        .sliderMax(2000)
        .build());

    private final Setting<Boolean> distanceCheck = sgPearl.add(new BoolSetting.Builder()
        .name("太远的不拉")
        .description("离珍珠点太远时拒绝请求，并私信告知对方。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxDistance = sgPearl.add(new IntSetting.Builder()
        .name("最大距离")
        .description("允许拉珍珠的最大距离（格）。")
        .defaultValue(120)
        .min(10)
        .sliderMin(10)
        .sliderMax(300)
        .visible(distanceCheck::get)
        .build());

    private final Setting<Boolean> returnToStart = sgPearl.add(new BoolSetting.Builder()
        .name("使用后返回出发点")
        .description("拉完珍珠后自动走回开始时的位置。")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> customRegex = sgPearl.add(new BoolSetting.Builder()
        .name("自定义私聊正则")
        .description("服务器私信显示格式不一样时打开，自行填正则。")
        .defaultValue(false)
        .build());

    private final Setting<String> regex = sgPearl.add(new StringSetting.Builder()
        .name("私聊正则")
        .description("两个捕获组：第 1 组=发送者，第 2 组=消息内容。默认是 3C 的 📨 发送者 ➡ 内容。")
        .defaultValue(PeerMessageLogic.DEFAULT_REGEX)
        .visible(customRegex::get)
        .build());

    // ---------- 三号模式 ----------

    private final Setting<Boolean> onlyMover = sgTrio.add(new BoolSetting.Builder()
        .name("只开1号搬运端")
        .description("2号/3号交给 ZenithProxy 插件拉珍珠。开启后本号固定为 1 号，不用再选角色。")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.TRIO)
        .build());

    private final Setting<Boolean> roleMover = sgTrio.add(new BoolSetting.Builder()
        .name("1号搬运端")
        .description("搬货的那个号：在两区之间往返，不自杀。三个角色只能选一个。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.TRIO && !onlyMover.get())
        .onChanged(v -> {
            if (v) onRoleChanged(0);
        })
        .build());

    private final Setting<Boolean> roleSource = sgTrio.add(new BoolSetting.Builder()
        .name("2号待搬运箱端")
        .description("守在待搬运箱区的珍珠房：收到 1 号的「拉我N」就点活板门把他拉过来。")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.TRIO && !onlyMover.get())
        .onChanged(v -> {
            if (v) onRoleChanged(1);
        })
        .build());

    private final Setting<Boolean> roleStore = sgTrio.add(new BoolSetting.Builder()
        .name("3号存储箱端")
        .description("守在存储箱区的珍珠房：收到 1 号的「拉我N」就点活板门把他拉过来。")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.TRIO && !onlyMover.get())
        .onChanged(v -> {
            if (v) onRoleChanged(2);
        })
        .build());

    private final Setting<String> peerSource = sgTrio.add(new StringSetting.Builder()
        .name("2号ID（待搬运箱端）")
        .description("2 号玩家的 ID：1 号要回待搬运箱区时发「拉我N」给他。")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.TRIO)
        .build());

    private final Setting<String> peerStore = sgTrio.add(new StringSetting.Builder()
        .name("3号ID（存储箱端）")
        .description("3 号玩家的 ID：1 号要去存储箱区卸货时发「拉我N」给他。")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.TRIO)
        .build());

    private final Setting<String> peerMover = sgTrio.add(new StringSetting.Builder()
        .name("1号ID（搬运端）")
        .description("2/3 号端专用：只响应这个 ID 的「拉我N」，其他人的请求一律不理。")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.TRIO && !onlyMover.get() && (roleSource.get() || roleStore.get()))
        .build());

    private final Setting<ChestMode> sourceChestMode = sgTrio.add(new EnumSetting.Builder<ChestMode>()
        .name("待搬运方式")
        .description("待搬运箱区的检索方式。")
        .defaultValue(ChestMode.AUTO)
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<BlockPos> sourceTarget = sgTrio.add(new BlockPosSetting.Builder()
        .name("待搬运箱位置")
        .description("待搬运箱区的基准坐标（判区 + 单箱子模式的目标）。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<BlockPos> sourcePos1 = sgTrio.add(new BlockPosSetting.Builder()
        .name("待搬运框选位置1")
        .description("区域框选的一角。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole() && sourceChestMode.get() == ChestMode.RANGE)
        .build());

    private final Setting<BlockPos> sourcePos2 = sgTrio.add(new BlockPosSetting.Builder()
        .name("待搬运框选位置2")
        .description("区域框选的另一角。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole() && sourceChestMode.get() == ChestMode.RANGE)
        .build());

    private final Setting<BlockPos> sourceTrapdoor = sgTrio.add(new BlockPosSetting.Builder()
        .name("待搬运箱区活板门")
        .description("待搬运箱区珍珠房的活板门（1 号每趟往这里的水柱投一颗珍珠）。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<BlockPos> trioStoreChest = sgTrio.add(new BlockPosSetting.Builder()
        .name("存储箱位置")
        .description("卸货的箱子。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<BlockPos> storeTrapdoor = sgTrio.add(new BlockPosSetting.Builder()
        .name("存储箱区活板门")
        .description("存储箱区珍珠房的活板门（1 号每趟往这里的水柱投一颗珍珠）。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<BlockPos> trioPearlChest = sgTrio.add(new BlockPosSetting.Builder()
        .name("珍珠箱位置")
        .description("全路线共用一个珍珠箱：手上珍珠少于阈值时来这里补到目标数量。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<Integer> restockThreshold = sgTrio.add(new IntSetting.Builder()
        .name("补货阈值")
        .description("手上珍珠少于这个数就去珍珠箱补货。")
        .defaultValue(4)
        .min(1)
        .sliderMax(16)
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<Integer> restockTarget = sgTrio.add(new IntSetting.Builder()
        .name("补货目标")
        .description("精确补到这个数量（一颗一颗补，不是整堆搬）。")
        .defaultValue(16)
        .min(1)
        .sliderMax(16)
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    // ==================== 运行时状态 ====================

    private long tick;
    private int cooldown;

    // 双号搬运
    private MoverState moverState = MoverState.LOOT;
    private final Set<BlockPos> blacklist = new HashSet<>();
    private final List<BlockPos> chestQueue = new ArrayList<>();
    private int queueIndex;
    private BlockPos openChest;
    private boolean finalTrip;
    private int pullCount;
    private int expectedN;
    private boolean ackReceived;
    private long lastSendTick;
    private int resendCount;
    private long verifyStartTick;
    private int throwAttempts;
    private long lastPearlThrowTick;
    private long refillStartTick;
    private BlockPos cachedLava;
    private long lavaScanTick;
    private int warnCooldown;
    private String stuckReason = "";
    private long nextAlarmTick;
    private boolean forwardHeld;
    /** 投掷后按 S 后退的剩余 tick（防止脚踩气泡柱触发珍珠）。 */
    private int backoffTicks;
    /** 投掷时是否按住潜行键。 */
    private boolean sneakHeld;

    // 三号模式
    private TrioState trioState = TrioState.SETTLE;
    /** 投掷子步骤：0=开始/检珍珠 1=校正站位 2=开活板门 3=潜行走近 4=投掷 5=退回 6=校验 */
    private int throwSub;
    private int throwRetries;
    private BlockPos throwSafeSpot;
    private int stallTicks;
    private int walkTicks;
    private double lastWalkX;
    private double lastWalkZ;
    private StashMoveLogic.Zone trioZone = StashMoveLogic.Zone.UNKNOWN;
    private long teleportWaitStart;
    private Vec3d lastPos;

    // 拉珍珠
    private boolean pullActive;
    private BlockPos pullPos;
    private String pullRequester;
    private int pullN = -1;
    private long pullStartTick;
    private int pullPhase;
    private BlockPos pullStartPos;
    private long pullReturnStart;

    // 三号珍珠补货（精确补到 target，一颗一颗）
    private int restockStepsLeft;
    private int restockChestSlot;
    private int restockInvSlot;

    /** 本轮卸货是否真的卸过东西（统计「趟数」用，空手开局不算一趟）。 */
    private boolean unloadedSomething;
    /** 长时间无进展看门狗（防死循环卡住不报警）。 */
    private long lastProgressTick;

    public StashMover() {
        super(AddonTemplate.CATEGORY, "FO 自动搬运",
            "珍珠超穿自动搬运：双号搬运/拉珍珠、三号模式（1搬运+2拉珍珠）。"
                + "⚠️ 双号搬运模式的出生点必须设在待搬运箱旁边（箱子搜索半径最大 20 格）。");
    }

    // ==================== 生命周期 ====================

    @Override
    public void onActivate() {
        tick = 0;
        cooldown = 0;
        blacklist.clear();
        chestQueue.clear();
        queueIndex = 0;
        openChest = null;
        finalTrip = false;
        ackReceived = false;
        resendCount = 0;
        throwAttempts = 0;
        cachedLava = null;
        stuckReason = "";
        nextAlarmTick = 0;
        forwardHeld = false;
        backoffTicks = 0;
        sneakHeld = false;
        restockStepsLeft = 0;
        pullActive = false;
        unloadedSomething = false;
        lastProgressTick = 0;

        moverState = MoverState.LOOT;
        trioState = TrioState.SETTLE;
        trioZone = StashMoveLogic.Zone.UNKNOWN;
        throwSub = 0;
        throwRetries = 0;
        throwSafeSpot = null;
        stallTicks = 0;
        walkTicks = 0;
        lastPos = mc.player != null ? mc.player.getEntityPos() : null;

        if (isTrioMover()) {
            trioZone = initialZone();
            trioState = TrioState.SETTLE;
        }
    }

    @Override
    public void onDeactivate() {
        releaseMovementKeys();
        PathManagers.get().stop();
        blacklist.clear();
        chestQueue.clear();
        pullActive = false;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // 用户口径：断线/退服清零本次搬运统计（关模块不清）
        StashStats.get().reset();
        pullActive = false;
    }

    // ==================== 主循环 ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        tick++;
        lastPos = lastPos == null ? mc.player.getEntityPos() : lastPos;

        // 投完珍珠立即按 S 后退，避免站在气泡柱上触发珍珠（每 tick 处理，不受动作冷却影响）
        if (backoffTicks > 0) {
            mc.options.backKey.setPressed(true);
            backoffTicks--;
            if (backoffTicks <= 0) mc.options.backKey.setPressed(false);
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        try {
            if (isMoverRole()) {
                if (mode.get() == Mode.TRIO) tickTrioMover();
                else tickDualMover();
            } else if (isPullerRole()) {
                tickPuller();
            }
        } catch (Exception e) {
            if (debug.get()) error("自动搬运出错: " + e);
        } finally {
            if (lastPos != null) lastPos = mc.player.getEntityPos();
        }
    }

    /** 三号模式角色互斥：开一个自动关掉另外两个（0=1号 1=2号 2=3号）。 */
    private void onRoleChanged(int which) {
        if (which != 0 && roleMover != null) roleMover.set(false);
        if (which != 1 && roleSource != null) roleSource.set(false);
        if (which != 2 && roleStore != null) roleStore.set(false);
    }

    private boolean isMoverRole() {
        if (mode.get() == Mode.MOVER) return true;
        if (mode.get() == Mode.TRIO) return onlyMover.get() || roleMover.get();
        return false;
    }

    private boolean isTrioMover() {
        return mode.get() == Mode.TRIO && isMoverRole();
    }

    private boolean isPullerRole() {
        if (mode.get() == Mode.PEARL) return true;
        return mode.get() == Mode.TRIO && !onlyMover.get() && (roleSource.get() || roleStore.get());
    }

    // ==================== 双号搬运状态机 ====================

    private void tickDualMover() {
        if (moverState != MoverState.STUCK && tick - lastProgressTick > 2400) {
            enterStuck("长时间没有进展（2 分钟无有效动作）");
            return;
        }
        switch (moverState) {
            case LOOT -> dualLoot();
            case SEND -> dualSend();
            case WAIT_ACK -> dualWaitAck();
            case WALK_WATER -> dualWalkWater();
            case TAKE_PEARL -> dualTakePearl();
            case THROW -> dualThrow();
            case VERIFY -> dualVerify();
            case PUT_BACK -> dualPutBack();
            case WALK_STORE -> dualWalkStore();
            case UNLOAD -> dualUnload();
            case SUICIDE_PREP -> dualSuicidePrep();
            case SUICIDE_LAVA -> dualSuicideLava();
            case DEAD_WAIT -> dualDeadWait();
            case REFILL -> dualRefill();
            case STUCK -> tickStuck();
        }
    }

    private void dualLoot() {
        // 背包满优先：关掉容器直接去发请求（否则容器里点击 QUICK_MOVE 一发都放不进去，会死循环）
        if (inventoryFull()) {
            closeScreen();
            moverState = MoverState.SEND;
            return;
        }
        if (containerOpen()) {
            if (takeFromContainer()) return;

            // 当前箱子已空
            closeScreen();
            if (chestMode.get() == ChestMode.SINGLE && waitForFill.get() && !finalTrip) {
                refillStartTick = tick;
                moverState = MoverState.REFILL;
                info("待搬运箱已空，等待刷新…");
                return;
            }
            blacklistChest(openChest);
            openChest = null;
            return;
        }

        if (inventoryFull()) {
            moverState = MoverState.SEND;
            return;
        }

        BlockPos chest = nextLootChest();
        if (chest == null) {
            warnThrottled("箱子搜索半径内没有待搬运箱，请检查重生点是否在箱子旁边");
            return;
        }
        if (mc.player.getBlockPos().getSquaredDistance(chest) > 20) {
            PathManagers.get().moveTo(chest, true);
            return;
        }
        openChestAt(chest);
    }

    private void dualSend() {
        if (targetPlayer.get().isBlank()) {
            enterStuck("请先填写搬运目标玩家 ID");
            return;
        }
        sendPullRequest(targetPlayer.get());
        moverState = waitAck.get() ? MoverState.WAIT_ACK : MoverState.WALK_WATER;
    }

    private void dualWaitAck() {
        if (ackReceived) {
            moverState = MoverState.WALK_WATER;
            return;
        }
        if (tick - lastSendTick > 300) {
            if (resendCount < 3) {
                int next = resendCount + 1;
                sendPullRequest(targetPlayer.get());
                resendCount = next;
                debugMsg("等确认超时，第 " + resendCount + " 次重发「拉我N」");
            } else {
                enterStuck("等待搭档确认超时（已重发 3 次）");
            }
        }
    }

    private void dualWalkWater() {
        BlockPos stand = trapdoor.get();
        if (stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置搬运活板门位置");
            return;
        }
        // 气泡柱里已经有自己的珍珠 → 不用再走这一趟，直接去还珍珠/卸货
        if (ownPearlInColumn(stand.down(1))) {
            moverState = MoverState.PUT_BACK;
            return;
        }
        // 走到活板门「旁边」的实心格，不踩气泡柱（踩上去会触发珍珠）
        BlockPos spot = pearlStandSpot(stand);
        BlockPos target = spot != null ? spot : stand;
        if (mc.player.getBlockPos().getSquaredDistance(target) > 4) {
            if (!PathManagers.get().isPathing()) {
                PathManagers.get().moveToPrecise(target);
            }
            return;
        }
        moverState = MoverState.TAKE_PEARL;
    }

    private void dualTakePearl() {
        // 气泡柱里已经有自己的珍珠就不用再投一颗（省珍珠、也避免重复投放触发）
        if (ownPearlInColumn(trapdoor.get().down(1))) {
            closeScreen();
            moverState = MoverState.PUT_BACK;
            return;
        }
        if (countPearls() > 0) {
            closeScreen();
            moverState = MoverState.THROW;
            return;
        }
        BlockPos chestPos = pearlChest.get();
        if (chestPos.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置珍珠箱位置");
            return;
        }
        if (containerOpen()) {
            int slot = findPearlSlotInContainer();
            if (slot < 0) {
                closeScreen();
                enterStuck("珍珠箱里没有珍珠");
                return;
            }
            clickSlot(slot, SlotActionType.QUICK_MOVE);
            cooldown = Math.max(1, clickDelay.get());
            return;
        }
        if (mc.player.getBlockPos().getSquaredDistance(chestPos) > 20) {
            PathManagers.get().moveTo(chestPos, true);
            return;
        }
        openChestAt(chestPos);
    }

    private void dualThrow() {
        BlockPos stand = trapdoor.get();
        BlockPos water = stand.down(1);
        if (ownPearlInColumn(water)) {
            moverState = MoverState.PUT_BACK;
            return;
        }
        if (!prepareThrowPosition(stand)) return;
        int slot = findPearlHotbarSlot();
        if (slot < 0) {
            // 珍珠被 QUICK_MOVE 塞进主背包时，先用 Meteor 的背包移动把它换到快捷栏（不打断投掷流程）
            int inv = findPearlIndexMainInventory();
            if (inv >= 0) {
                InvUtils.move().from(inv).toHotbar(mc.player.getInventory().getSelectedSlot());
                cooldown = 5;
                return;
            }
            moverState = MoverState.TAKE_PEARL;
            return;
        }
        sneakForThrow();
        doThrowPearl(slot, Vec3d.ofCenter(water));
        throwAttempts++;
        verifyStartTick = tick;
        moverState = MoverState.VERIFY;
    }

    private void dualVerify() {
        BlockPos water = trapdoor.get().down(1);
        if (ownPearlInColumn(water)) {
            lastPearlThrowTick = tick;
            moverState = MoverState.PUT_BACK;
            cooldown = 10;
            return;
        }
        if (tick - verifyStartTick > 60) {
            if (throwAttempts >= 3) {
                enterStuck("投掷珍珠失败（水柱里没有检测到自己的珍珠）");
            } else {
                moverState = MoverState.THROW;
                cooldown = 20;
            }
        }
    }

    private void dualPutBack() {
        if (countPearls() == 0) {
            closeScreen();
            moverState = MoverState.WALK_STORE;
            return;
        }
        if (containerOpen()) {
            // 珍珠箱塞满珍珠时放不回去，直接跳过（misaka 同款分支）
            if (containerIsFull()) {
                closeScreen();
                moverState = MoverState.WALK_STORE;
                return;
            }
            int slot = findPearlSlotInInventory();
            if (slot < 0) {
                closeScreen();
                moverState = MoverState.WALK_STORE;
                return;
            }
            clickSlot(slot, SlotActionType.QUICK_MOVE);
            cooldown = Math.max(1, clickDelay.get());
            return;
        }
        BlockPos chestPos = pearlChest.get();
        if (mc.player.getBlockPos().getSquaredDistance(chestPos) > 20) {
            PathManagers.get().moveTo(chestPos, true);
            return;
        }
        openChestAt(chestPos);
    }

    private void dualWalkStore() {
        BlockPos store = storeChest.get();
        if (store.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置存储箱位置");
            return;
        }
        if (mc.player.getBlockPos().getSquaredDistance(store) > 20) {
            PathManagers.get().moveTo(store, true);
            return;
        }
        if (containerOpen()) {
            moverState = MoverState.UNLOAD;
            return;
        }
        openChestAt(store);
    }

    private void dualUnload() {
        if (!containerOpen()) {
            if (!hasItemsToUnload()) {
                onUnloadDone();
                return;
            }
            openChestAt(storeChest.get());
            return;
        }
        if (containerIsFull()) {
            closeScreen();
            if (closeWhenFull.get()) {
                info("存储箱已满，自动关闭模块");
                toggle();
            } else {
                warnThrottled("存储箱已满，等待清理…");
            }
            return;
        }
        int slot = findInventorySlotToUnload();
        if (slot < 0) {
            closeScreen();
            onUnloadDone();
            return;
        }
        unloadedSomething = true;
        clickSlot(slot, SlotActionType.QUICK_MOVE);
        cooldown = Math.max(1, clickDelay.get());
        progress();
    }

    private void onUnloadDone() {
        if (unloadedSomething) StashStats.get().addTrip();
        unloadedSomething = false;
        if (finalTrip) {
            info("最后一趟运输完成，关闭模块");
            toggle();
            return;
        }
        moverState = MoverState.SUICIDE_PREP;
    }

    private void dualSuicidePrep() {
        if (suicideAfterPearl.get() && tick - lastPearlThrowTick < suicideDelay.get()) {
            return;
        }
        closeScreen();
        if (suicideMode.get() == SuicideMode.COMMAND) {
            runSuicideCommand();
            moverState = MoverState.DEAD_WAIT;
            cooldown = 20;
            return;
        }
        moverState = MoverState.SUICIDE_LAVA;
    }

    private void dualSuicideLava() {
        if (mc.player.isDead()) {
            moverState = MoverState.DEAD_WAIT;
            return;
        }
        if (cachedLava == null || tick - lavaScanTick > 200) {
            lavaScanTick = tick;
            cachedLava = findNearestLava(mc.player.getBlockPos(), lavaRadius.get());
            if (cachedLava == null) {
                warning("附近没有找到岩浆，改用指令自杀");
                releaseForward();
                runSuicideCommand();
                moverState = MoverState.DEAD_WAIT;
                cooldown = 20;
                return;
            }
        }
        double distSq = mc.player.getBlockPos().getSquaredDistance(cachedLava);
        if (distSq <= 16) {
            faceBlock(cachedLava);
            forwardHeld = true;
            mc.options.forwardKey.setPressed(true);
            return;
        }
        if (!PathManagers.get().isPathing()) PathManagers.get().moveTo(cachedLava, true);
    }

    private void dualDeadWait() {
        if (mc.player.isDead()) {
            mc.player.requestRespawn();
            releaseForward();
            moverState = MoverState.LOOT;
            cooldown = 20;
        }
    }

    private void dualRefill() {
        BlockPos chestPos = targetChest.get();
        if (chestPos.equals(BlockPos.ORIGIN)) {
            enterStuck("单箱子模式请先设置目标箱子位置");
            return;
        }
        if (!(mc.world.getBlockState(chestPos).getBlock() instanceof ChestBlock)) {
            enterStuck("目标箱子不存在");
            return;
        }
        if (!containerOpen()) {
            if (mc.player.getBlockPos().getSquaredDistance(chestPos) > 20) {
                PathManagers.get().moveTo(chestPos, true);
                return;
            }
            openChestAt(chestPos);
            return;
        }
        if (!containerEmpty()) {
            closeScreen();
            info("箱子有物品了，继续搬运");
            moverState = MoverState.LOOT;
            return;
        }
        if ((tick - refillStartTick) / 20 >= waitTimeout.get()) {
            closeScreen();
            if (inventoryEmpty()) {
                info("背包已空，关闭模块");
                toggle();
            } else {
                finalTrip = true;
                moverState = MoverState.SEND;
            }
            return;
        }
        closeScreen();
        cooldown = 20;
    }

    private void tickStuck() {
        if (tick >= nextAlarmTick) {
            warning("自动搬运卡住：" + stuckReason + "（已暂停，等手动处理）");
            nextAlarmTick = tick + 200;
        }
    }

    // ==================== 三号模式（1 号搬运端） ====================

    private void tickTrioMover() {
        if (trioZone == StashMoveLogic.Zone.UNKNOWN) {
            trioZone = initialZone();
            if (trioZone == StashMoveLogic.Zone.UNKNOWN) {
                enterStuck("请先设置待搬运箱与存储箱坐标");
                return;
            }
            trioState = TrioState.SETTLE;
        }
        if (trioState != TrioState.STUCK && tick - lastProgressTick > 2400) {
            enterStuck("长时间没有进展（2 分钟无有效动作）");
            return;
        }
        switch (trioState) {
            case SETTLE -> trioSettle();
            case RESTOCK -> trioRestock();
            case THROW -> trioThrow();
            case LOOT -> trioLoot();
            case UNLOAD -> trioUnload();
            case SEND -> trioSend();
            case WAIT_TELEPORT -> trioWaitTeleport();
            case STUCK -> tickStuck();
        }
    }

    private void trioLoot() {
        if (inventoryFull()) {
            closeScreen();
            trioState = TrioState.SEND;   // V6.7p3：取货完成 → 发私信拉珍珠
            return;
        }
        if (containerOpen()) {
            if (takeFromContainer()) return;
            closeScreen();
            blacklistChest(openChest);
            openChest = null;
            return;
        }
        BlockPos chest = nextLootChest();
        if (chest == null) {
            enterStuck("待搬运箱区已搬空（没有可搬的箱子了）");
            return;
        }
        if (mc.player.getBlockPos().getSquaredDistance(chest) > 20) {
            PathManagers.get().moveTo(chest, true);
            return;
        }
        openChestAt(chest);
    }

    private void trioUnload() {
        BlockPos store = trioStoreChest.get();
        if (store.equals(BlockPos.ORIGIN)) {
            enterStuck("三号模式请先设置存储箱位置");
            return;
        }
        if (!containerOpen()) {
            if (!hasItemsToUnload()) {
                if (unloadedSomething) StashStats.get().addTrip();
                unloadedSomething = false;
                trioState = TrioState.SEND;   // V6.7p3：存货完成 → 发私信拉珍珠
                return;
            }
            if (!(mc.world.getBlockState(store).getBlock() instanceof ChestBlock)) {
                enterStuck("存储箱不存在");
                return;
            }
            if (mc.player.getBlockPos().getSquaredDistance(store) > 20) {
                PathManagers.get().moveTo(store, true);
                return;
            }
            openChestAt(store);
            return;
        }
        if (containerIsFull()) {
            closeScreen();
            enterStuck("存储箱已满，请清理");
            return;
        }
        int slot = findInventorySlotToUnload();
        if (slot < 0) {
            closeScreen();
            if (unloadedSomething) StashStats.get().addTrip();
            unloadedSomething = false;
            trioState = TrioState.SEND;   // V6.7p3：存货完成 → 发私信拉珍珠
            return;
        }
        unloadedSomething = true;
        clickSlot(slot, SlotActionType.QUICK_MOVE);
        cooldown = Math.max(1, clickDelay.get());
        progress();
    }

    private void trioRestock() {
        BlockPos chestPos = trioPearlChest.get();
        int have = countPearls();
        int target = Math.max(restockTarget.get(), restockThreshold.get());
        int need = StashPearlLogic.missingToTarget(have, restockThreshold.get(), target);
        if (need == 0) {
            trioState = TrioState.THROW;
            return;
        }
        // 珍珠箱离得太远（不在本区）时先用剩余珍珠，耗尽了再报警
        double distSq = mc.player.getBlockPos().getSquaredDistance(chestPos);
        if (!containerOpen() && distSq > 64 * 64) {
            if (have > 0) {
                debugMsg("珍珠箱不在附近，先用剩余 " + have + " 颗珍珠");
                trioState = TrioState.THROW;
            } else {
                enterStuck("珍珠耗尽且珍珠箱不在附近");
            }
            return;
        }
        if (!containerOpen()) {
            if (distSq > 400) {
                PathManagers.get().moveTo(chestPos, true);
                return;
            }
            openChestAt(chestPos);
            return;
        }

        // 精确补到 target：一次拿起箱内珍珠堆 → 对背包珍珠堆右键逐颗放 → 余量放回
        if (restockStepsLeft <= 0) {
            restockStepsLeft = need;
            restockChestSlot = findPearlSlotInContainer();
            restockInvSlot = findPearlSlotInInventory();
            if (restockChestSlot < 0) {
                closeScreen();
                if (have > 0) {
                    info("珍珠箱里没有珍珠了，先用剩余 " + have + " 颗");
                    trioState = TrioState.THROW;
                } else {
                    enterStuck("珍珠箱里没有珍珠");
                }
                return;
            }
            if (restockInvSlot < 0) restockInvSlot = findEmptyInventorySlot();
            if (restockInvSlot < 0) {
                closeScreen();
                enterStuck("背包没有空位放珍珠");
                return;
            }
            // 第 1 步：拿起箱内整堆
            clickSlot(restockChestSlot, SlotActionType.PICKUP);
            cooldown = Math.max(1, clickDelay.get());
            progress();
            return;
        }

        // 逐个右键放到背包珍珠堆（每次 +1）
        clickSlot(restockInvSlot, SlotActionType.PICKUP, 1);
        restockStepsLeft--;
        cooldown = Math.max(1, clickDelay.get());
        progress();
        if (restockStepsLeft <= 0) {
            // 余量放回箱子原槽
            clickSlot(restockChestSlot, SlotActionType.PICKUP);
            closeScreen();
            trioState = TrioState.THROW;
        }
    }

    /** 当前所在区的活板门坐标。 */
    private BlockPos trioTrapdoor() {
        return trioZone == StashMoveLogic.Zone.SOURCE ? sourceTrapdoor.get() : storeTrapdoor.get();
    }

    /**
     * ① 校正站位（V6.7p3，用户口径）：被拉过来后先走离气泡柱/活板门，
     * 站到最近的实心安全格，确认没踩在水柱或活板门上。
     */
    private void trioSettle() {
        BlockPos stand = trioTrapdoor();
        if (stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置" + trioZone + "的活板门位置");
            return;
        }
        if (settlePosition(stand)) {
            trioState = TrioState.RESTOCK;
            cooldown = 5;
        }
    }

    /**
     * 玩家若站在活板门/水格/水里，走到最近的实心安全格。
     *
     * @return true = 已站好（并记下“原位”，投完退回这里）
     */
    private boolean settlePosition(BlockPos trapdoorPos) {
        if (mc.player == null) return false;
        BlockPos feet = mc.player.getBlockPos();
        BlockPos water = trapdoorPos.down(1);
        // 离珍珠点太远（例如刚补完珍珠）→ 先回到活板门旁的安全格
        if (feet.getSquaredDistance(trapdoorPos) > 9) {
            BlockPos far = pearlStandSpot(trapdoorPos);
            if (far != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveToPrecise(far);
            }
            return false;
        }
        boolean unsafe = feet.equals(trapdoorPos)
            || feet.equals(water)
            || mc.player.isTouchingWater()
            || mc.world.getBlockState(feet.down()).getBlock() instanceof TrapdoorBlock
            || !mc.world.getBlockState(feet.down()).getFluidState().isEmpty();
        if (!unsafe) {
            throwSafeSpot = feet;
            return true;
        }
        BlockPos spot = pearlStandSpot(trapdoorPos);
        if (spot == null) {
            // 四周没有可站的实心格：只能原地小心处理
            throwSafeSpot = feet;
            return true;
        }
        if (feet.getSquaredDistance(spot) <= 2.25) {
            throwSafeSpot = mc.player.getBlockPos();
            return true;
        }
        if (!PathManagers.get().isPathing()) {
            PathManagers.get().moveToPrecise(spot);
        }
        return false;
    }

    /**
     * 投掷子流程（V6.7p3）：检珍珠 → 校正站位 → 开活板门 → 潜行走近 → 投掷 → 退回 → 校验（失败重试 3 次）。
     */
    private void trioThrow() {
        BlockPos stand = trioTrapdoor();
        if (stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置" + trioZone + "的活板门位置");
            return;
        }
        BlockPos water = stand.down(1);

        switch (throwSub) {
            case 0 -> {
                if (ownPearlInColumn(water)) {
                    debugMsg("气泡柱里已有自己的珍珠，跳过投掷");
                    finishTrioThrow();
                    return;
                }
                throwRetries = 0;
                throwSub = 1;
            }
            case 1 -> {
                if (settlePosition(stand)) throwSub = 2;
            }
            case 2 -> {
                if (trapdoorIsOpenOrAbsent(stand)) {
                    throwSub = 3;
                    walkTicks = 0;
                    stallTicks = 0;
                    lastWalkX = mc.player.getX();
                    lastWalkZ = mc.player.getZ();
                } else {
                    BlockClicker.clickBlockFacingPlayer(stand, true, Hand.MAIN_HAND);
                    cooldown = 10;
                }
            }
            case 3 -> {
                if (walkToColumnCenter(stand)) {
                    throwSub = 4;
                    cooldown = 2;
                }
            }
            case 4 -> {
                int slot = findPearlHotbarSlot();
                if (slot < 0) {
                    int inv = findPearlIndexMainInventory();
                    if (inv >= 0) {
                        InvUtils.move().from(inv).toHotbar(mc.player.getInventory().getSelectedSlot());
                        cooldown = 5;
                        return;
                    }
                    enterStuck("快捷栏里没有末影珍珠");
                    return;
                }
                sneakForThrow();
                doThrowPearl(slot, Vec3d.ofCenter(water));
                throwSub = 5;
                cooldown = 5;
            }
            case 5 -> {
                if (backOffToSafe()) {
                    throwSub = 6;
                    verifyStartTick = tick;
                }
            }
            case 6 -> {
                if (ownPearlInColumn(water)) {
                    lastPearlThrowTick = tick;
                    finishTrioThrow();
                } else if (tick - verifyStartTick > 60) {
                    throwRetries++;
                    if (throwRetries >= 3) {
                        enterStuck("投掷珍珠 3 次失败（气泡柱里没有检测到自己的珍珠）");
                    } else {
                        debugMsg("投掷校验失败，第 " + throwRetries + " 次重试");
                        throwSub = 2;
                        cooldown = 10;
                    }
                }
            }
        }
    }

    /** 投掷流程收尾：松键、回到“取货/存货”阶段。 */
    private void finishTrioThrow() {
        releaseSneak();
        releaseForward();
        throwSub = 0;
        throwRetries = 0;
        trioState = trioZone == StashMoveLogic.Zone.STORE ? TrioState.UNLOAD : TrioState.LOOT;
        cooldown = 5;
        progress();
    }

    /** 活板门是否已打开（方块不是活板门时按“无需打开”处理，避免死循环点击）。 */
    private boolean trapdoorIsOpenOrAbsent(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof TrapdoorBlock)) return true;
        return state.get(TrapdoorBlock.OPEN);
    }

    /**
     * ③ 按住 shift + pitch 90，朝气泡柱中心按前进键，直到走不动。
     *
     * <p>停滞判定：连续 3 tick 水平位移 &lt; 0.05 格；另加 30 tick 上限保护。
     *
     * @return true = 已经到边缘/走不动，可以投掷
     */
    private boolean walkToColumnCenter(BlockPos trapdoorPos) {
        sneakForThrow();
        Vec3d center = Vec3d.ofCenter(trapdoorPos.down(1));
        double dx = center.x - mc.player.getX();
        double dz = center.z - mc.player.getZ();
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch(90f);
        mc.options.forwardKey.setPressed(true);
        forwardHeld = true;
        walkTicks++;

        boolean stalled = StashMoveLogic.isStalled(lastWalkX, lastWalkZ, mc.player.getX(), mc.player.getZ(), 0.05);
        lastWalkX = mc.player.getX();
        lastWalkZ = mc.player.getZ();
        if (stallTicks == 0) {
            stallTicks = 1;
            return false;
        }
        stallTicks = stalled ? stallTicks + 1 : 1;

        if (stallTicks >= 3 || walkTicks >= 30) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
            walkTicks = 0;
            stallTicks = 0;
            return true;
        }
        return false;
    }

    /** ④ 松开 shift、按 S 退离气泡柱，回到投掷前的原位。 */
    private boolean backOffToSafe() {
        releaseSneak();
        if (backoffTicks > 0) return false;   // 投完已在按 S 后退，等它走完
        if (throwSafeSpot == null) return true;
        if (mc.player.getBlockPos().getSquaredDistance(throwSafeSpot) <= 4) return true;
        if (!PathManagers.get().isPathing()) {
            PathManagers.get().moveToPrecise(throwSafeSpot);
        }
        return false;
    }

    private void trioSend() {
        String target = trioZone == StashMoveLogic.Zone.SOURCE ? peerStore.get().trim() : peerSource.get().trim();
        if (target.isEmpty()) {
            enterStuck(trioZone == StashMoveLogic.Zone.SOURCE ? "请填写 3号ID（存储箱端）" : "请填写 2号ID（待搬运箱端）");
            return;
        }
        sendPullRequest(target);
        teleportWaitStart = tick;
        trioState = TrioState.WAIT_TELEPORT;
    }

    private void trioWaitTeleport() {
        Vec3d now = mc.player.getEntityPos();
        if (lastPos != null && StashMoveLogic.teleportJumped(lastPos.x, lastPos.z, now.x, now.z, 32)) {
            trioZone = trioZone == StashMoveLogic.Zone.SOURCE ? StashMoveLogic.Zone.STORE : StashMoveLogic.Zone.SOURCE;
            throwAttempts = 0;
            blacklist.clear();
            chestQueue.clear();
            queueIndex = 0;
            throwSub = 0;
            throwRetries = 0;
            trioState = TrioState.SETTLE;   // V6.7p3：到位后先走离气泡柱再干活
            info("已到达" + trioZone);
            progress();
            return;
        }
        if (tick - teleportWaitStart > 300) {
            if (resendCount < 3) {
                int next = resendCount + 1;
                String target = trioZone == StashMoveLogic.Zone.SOURCE ? peerStore.get().trim() : peerSource.get().trim();
                sendPullRequest(target);
                resendCount = next;
                debugMsg("等待被拉走超时，重发第 " + resendCount + " 次");
                teleportWaitStart = tick;
            } else {
                enterStuck("等待被拉走超时（已重发 3 次）");
            }
        }
    }

    private StashMoveLogic.Zone initialZone() {
        BlockPos source = sourceTarget.get();
        BlockPos store = trioStoreChest.get();
        double sx = source.equals(BlockPos.ORIGIN) ? Double.NaN : source.getX();
        double sz = source.equals(BlockPos.ORIGIN) ? Double.NaN : source.getZ();
        double tx = store.equals(BlockPos.ORIGIN) ? Double.NaN : store.getX();
        double tz = store.equals(BlockPos.ORIGIN) ? Double.NaN : store.getZ();
        return StashMoveLogic.nearerZone(mc.player.getX(), mc.player.getZ(), sx, sz, tx, tz);
    }

    // ==================== 拉珍珠端（miku 珍珠大管家） ====================

    private void tickPuller() {
        if (!pullActive || pullPos == null) return;

        if (pullPhase == 0) {
            if (tick - pullStartTick > pathTimeout.get()) {
                warning("拉珍珠赶路超时，已放弃");
                resetPull();
                return;
            }
            double distSq = mc.player.getBlockPos().getSquaredDistance(pullPos);
            if (distSq <= 16) {
                BlockClicker.clickBlock(pullPos, Direction.UP, true, Hand.MAIN_HAND);
                String ack = pullN >= 0 ? ackKeyword.get() + pullN : ackKeyword.get();
                sendPrivate(pullRequester, ack);
                info("已拉 " + pullRequester + " 的珍珠");
                pullPhase = 1;
                pullReturnStart = tick;
                return;
            }
            if (!PathManagers.get().isPathing()) PathManagers.get().moveTo(pullPos, true);
            return;
        }

        // 回出发点
        if (!returnToStart.get() || pullStartPos == null) {
            resetPull();
            return;
        }
        if (mc.player.getBlockPos().getSquaredDistance(pullStartPos) <= 4) {
            resetPull();
            return;
        }
        if (tick - pullReturnStart > pathTimeout.get()) {
            resetPull();
            return;
        }
        if (!PathManagers.get().isPathing()) PathManagers.get().moveTo(pullStartPos, true);
    }

    private void resetPull() {
        pullActive = false;
        pullPos = null;
        pullRequester = null;
        pullN = -1;
        pullPhase = 0;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null || mc.world == null) return;

        var pattern = PeerMessageLogic.compileRegex(customRegex.get() ? regex.get() : PeerMessageLogic.DEFAULT_REGEX);
        var parsed = PeerMessageLogic.parsePrivate(event.getMessage().getString(), pattern);
        if (parsed == null) return;

        if (isPullerRole()) handlePullRequest(parsed);
        if (isMoverRole()) handleAck(parsed);
    }

    private void handlePullRequest(PeerMessageLogic.Parsed parsed) {
        var req = PeerMessageLogic.matchKeyword(parsed.content(), keyword.get());
        if (!req.matched()) return;

        if (mode.get() == Mode.TRIO) {
            String expected = peerMover.get().trim();
            if (expected.isEmpty() || !parsed.sender().equalsIgnoreCase(expected)) return;
        }
        if (pullActive) {
            info("当前已有珍珠在拉中，忽略 " + parsed.sender() + " 的请求");
            return;
        }

        BlockPos point = resolvePearlPoint(parsed.sender());
        if (point == null) {
            sendPrivate(parsed.sender(), "没找到你的珍珠啊 " + parsed.sender());
            return;
        }
        double dist = Math.sqrt(mc.player.getBlockPos().getSquaredDistance(point));
        if (distanceCheck.get() && dist > maxDistance.get()) {
            sendPrivate(parsed.sender(), "我现在离你的珍珠太远，距离 " + (int) dist + " 格");
            return;
        }

        pullActive = true;
        pullPos = point;
        pullRequester = parsed.sender();
        pullN = req.number();
        pullStartTick = tick;
        pullPhase = 0;
        pullStartPos = mc.player.getBlockPos();
        info("收到 " + parsed.sender() + " 的拉珍珠请求，出发");
    }

    private void handleAck(PeerMessageLogic.Parsed parsed) {
        if (!ackKeyword.get().isBlank() && !parsed.content().contains(ackKeyword.get().trim())) return;
        var ack = PeerMessageLogic.matchKeyword(parsed.content(), ackKeyword.get());
        if (!ack.matched()) return;

        String expected = expectedPartnerId();
        if (expected.isEmpty() || !parsed.sender().equalsIgnoreCase(expected)) return;

        if (PeerMessageLogic.ackAccepts(ack, expectedN)) {
            ackReceived = true;
            debugMsg("收到确认消息：" + parsed.sender());
        }
    }

    private String expectedPartnerId() {
        if (mode.get() == Mode.MOVER) return targetPlayer.get().trim();
        if (mode.get() == Mode.TRIO) {
            return trioZone == StashMoveLogic.Zone.SOURCE ? peerStore.get().trim() : peerSource.get().trim();
        }
        return "";
    }

    private BlockPos resolvePearlPoint(String sender) {
        String key = sender.toLowerCase(Locale.ROOT);
        for (var e : pearlPoints.get().entrySet()) {
            if (!e.getKey().toLowerCase(Locale.ROOT).equals(key)) continue;
            int[] xyz = StashPearlLogic.parsePos(e.getValue());
            if (xyz != null) return new BlockPos(xyz[0], xyz[1], xyz[2]);
        }
        if (!searchSigns.get()) return null;
        BlockPos found = scanSignFor(sender);
        if (found != null) {
            pearlPoints.get().put(sender, StashPearlLogic.formatPos(found.getX(), found.getY(), found.getZ()));
            info("已通过告示牌记录 " + sender + " 的珍珠点");
        }
        return found;
    }

    private BlockPos scanSignFor(String username) {
        if (mc.world == null) return null;
        String key = username.toLowerCase(Locale.ROOT);
        BlockPos center = mc.player.getBlockPos();
        int r = signRadius.get();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (!(mc.world.getBlockEntity(p) instanceof SignBlockEntity sign)) continue;
                    for (var line : sign.getFrontText().getMessages(false)) {
                        if (line.getString().toLowerCase(Locale.ROOT).contains(key)) {
                            info("找到包含名字的告示牌: " + p.toShortString());
                            return p.down();
                        }
                    }
                }
            }
        }
        return null;
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (!isPullerRole() || !setupMode.get() || mc.world == null) return;
        BlockPos pos = event.result.getBlockPos();
        if (!(mc.world.getBlockState(pos).getBlock() instanceof TrapdoorBlock)) return;
        event.setCancelled(true);
        String name = "新珍珠点" + pearlPoints.get().size();
        pearlPoints.get().put(name, StashPearlLogic.formatPos(pos.getX(), pos.getY(), pos.getZ()));
        info("已记录珍珠点 " + pos.toShortString() + "，请在设置里把「" + name + "」改成玩家 ID");
    }

    // ==================== 通用工具 ====================

    private void sendPullRequest(String target) {
        expectedN = ++pullCount;
        ackReceived = false;
        resendCount = 0;
        lastSendTick = tick;
        sendPrivate(target, PeerMessageLogic.buildRequest(keyword.get(), expectedN));
        debugMsg("已发送「" + keyword.get() + expectedN + "」给 " + target);
        progress();
    }

    /** 刷新看门狗时间戳（任何有效动作都算进展）。 */
    private void progress() {
        lastProgressTick = tick;
    }

    private void sendPrivate(String target, String text) {
        if (target == null || target.isBlank() || mc.player == null) return;
        String body = chatCommand.get() == ChatCommand.SLASH_M
            ? "m " + target + " " + text
            : "tell " + target + " " + text;
        mc.player.networkHandler.sendChatCommand(body);
    }

    private void runSuicideCommand() {
        String cmd = suicideCommand.get().trim();
        if (cmd.isEmpty()) return;
        if (cmd.startsWith("/")) mc.player.networkHandler.sendChatCommand(cmd.substring(1));
        else mc.player.networkHandler.sendChatMessage(cmd);
    }

    /**
     * 朝指定点投一颗珍珠（用户口径：站着别踩气泡柱，视线看向气泡柱中心投，投完按 S 后退）。
     *
     * <p>视角必须“服务端已看到”才算数：用 miku 的 RotationManager 先发带目标 yaw/pitch 的完整包，
     * 再发交互包，最后 sync 回真实朝向（顺序：视角包 → 交互包 → 归位包）。
     */
    private void doThrowPearl(int hotbarSlot, Vec3d aim) {
        closeScreen();
        mc.player.getInventory().setSelectedSlot(hotbarSlot);

        double hd = Math.hypot(aim.x - mc.player.getX(), aim.z - mc.player.getZ());
        float[] yp;
        if (hd <= 0.5) {
            // 已经站在气泡柱正上方：pitch 90 垂直向下投
            yp = new float[]{mc.player.getYaw(), 90f};
        } else {
            yp = FacingLogic.yawPitchTo(aim.x, aim.y, aim.z,
                mc.player.getX(), mc.player.getEyePos().y, mc.player.getZ());
        }
        RotationManager.getInstance().register(new Rotation(yp[0], yp[1]).setPriority(10));
        mc.player.setYaw(yp[0]);
        mc.player.setPitch(yp[1]);

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

        RotationManager.getInstance().sync();
        releaseSneak();
        backoffTicks = 10;   // 投完立即按 S 后退，避免脚踩气泡柱触发珍珠
        cooldown = 3;
        progress();
    }

    /**
     * 找活板门旁「能站人」的实心格（不踩气泡柱）：2 格半径内、脚下实心、身体两格是空气，
     * 取离玩家最近的一个；找不到返回 null。
     */
    private BlockPos pearlStandSpot(BlockPos trapdoorPos) {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos water = trapdoorPos.down(1);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = trapdoorPos.add(dx, dy, dz);
                    if (p.equals(trapdoorPos) || p.equals(water)) continue;
                    if (!isStandable(p)) continue;
                    double d = p.getSquaredDistance(playerPos);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /** 该格能不能站：脚下非空气且不是流体，身体两格空气。 */
    private boolean isStandable(BlockPos p) {
        var below = mc.world.getBlockState(p.down());
        if (below.isAir() || !below.getFluidState().isEmpty()) return false;
        return mc.world.getBlockState(p).isAir() && mc.world.getBlockState(p.up()).isAir();
    }

    /**
     * 投掷前的站位与活板门准备：
     * ① 走到活板门旁边的实心格（绝不踩气泡柱）；② 活板门关着就点开。
     *
     * @return true = 已就位且活板门开着，可以投
     */
    private boolean prepareThrowPosition(BlockPos trapdoorPos) {
        BlockPos spot = pearlStandSpot(trapdoorPos);
        if (spot == null) spot = trapdoorPos;   // 四周无处可站时退回原位（仍然潜行投）
        if (mc.player.getBlockPos().getSquaredDistance(spot) > 2.25) {
            if (!PathManagers.get().isPathing()) {
                PathManagers.get().moveToPrecise(spot);
            }
            return false;
        }
        var state = mc.world.getBlockState(trapdoorPos);
        if (state.getBlock() instanceof TrapdoorBlock && !state.get(TrapdoorBlock.OPEN)) {
            BlockClicker.clickBlockFacingPlayer(trapdoorPos, true, Hand.MAIN_HAND);
            cooldown = 10;
            return false;
        }
        return true;
    }

    /** 投掷时按住潜行键（防止走位滑进气泡柱）。 */
    private void sneakForThrow() {
        if (mc.options == null) return;
        mc.options.sneakKey.setPressed(true);
        sneakHeld = true;
    }

    private void releaseSneak() {
        if (sneakHeld && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
        sneakHeld = false;
    }

    /**
     * 气泡柱里是否已经有<b>自己的</b>珍珠（严格判定：XZ 误差 ≤0.9 格、Y 在水柱内）。
     *
     * <p>用途有两个：① 投掷前先查——已经有珍珠就不必再投（用户口径）；
     * ② 投掷后校验——必须真的落在柱内才算成功（原来 Y±8 的宽松范围会把落在活板门上的也算成功）。
     */
    private boolean ownPearlInColumn(BlockPos water) {
        if (mc.world == null) return false;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl)) continue;
            Entity owner = pearl.getOwner();
            boolean mine = owner == mc.player
                || (owner != null && owner.getUuid().equals(mc.player.getUuid()));
            if (!mine) continue;
            if (Math.abs(pearl.getX() - (water.getX() + 0.5)) <= 0.9
                && Math.abs(pearl.getZ() - (water.getZ() + 0.5)) <= 0.9
                && pearl.getY() >= water.getY() - 8.0
                && pearl.getY() <= water.getY() + 1.6) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前打开的容器（V6.7p3：同时支持箱子类与<b>放下的潜影盒</b>）。
     *
     * <p>放下的潜影盒界面是 {@link ShulkerBoxScreenHandler}，旧实现只认
     * {@link GenericContainerScreenHandler}，导致「珍珠箱 = 潜影盒」时永远判定“没打开箱子”、
     * 补货逻辑一直不执行。这里统一返回容器 handler，拿不到返回 null。
     */
    private ScreenHandler containerHandler() {
        if (mc.player == null) return null;
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || handler == mc.player.playerScreenHandler) return null;
        if (StashMoveLogic.isSupportedContainer(handler instanceof GenericContainerScreenHandler,
            handler instanceof ShulkerBoxScreenHandler)) return handler;
        return null;
    }

    private boolean containerOpen() {
        return containerHandler() != null;
    }

    private void closeScreen() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
    }

    private void clickSlot(int slot, SlotActionType type) {
        clickSlot(slot, type, 0);
    }

    private void clickSlot(int slot, SlotActionType type, int button) {
        ScreenHandler handler = containerHandler();
        if (handler == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, button, type, mc.player);
    }

    /** 容器区（非玩家背包）拿物品：拿一摞就返回 true；已空返回 false。 */
    private boolean takeFromContainer() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return false;
        int size = handler.slots.size();
        for (int i = 0; i < size - 36; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (onlyShulker.get() && !StashPearlLogic.isShulkerBoxId(Registries.ITEM.getId(stack.getItem()).toString())) continue;
            clickSlot(i, SlotActionType.QUICK_MOVE);
            if (StashPearlLogic.isShulkerBoxId(Registries.ITEM.getId(stack.getItem()).toString())) {
                StashStats.get().addShulkers(stack.getCount());
            }
            cooldown = Math.max(1, clickDelay.get());
            progress();
            return true;
        }
        return false;
    }

    private boolean inventoryFull() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private boolean inventoryEmpty() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private boolean containerIsFull() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return false;
        int size = handler.slots.size();
        for (int i = 0; i < size - 36; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) return false;
        }
        return true;
    }

    private boolean containerEmpty() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return false;
        int size = handler.slots.size();
        for (int i = 0; i < size - 36; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) return false;
        }
        return true;
    }

    private int findInventorySlotToUnload() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return -1;
        int size = handler.slots.size();
        for (int i = size - 36; i < size; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (isUnloadCandidate(stack)) return i;
        }
        return -1;
    }

    /** 背包里是否还有“可以存进存储箱”的东西（珍珠、只拿潜影盒时的非潜影盒都排除）。 */
    private boolean hasItemsToUnload() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (isUnloadCandidate(inv.getStack(i))) return true;
        }
        return false;
    }

    /**
     * 卸货过滤（V6.7p1 修复）：
     * ① 末影珍珠任何时候都不入库（三号模式的回程票 / 双号还珍珠失败时的保命珍珠）；
     * ② 开了「只拿潜影盒」时只存潜影盒，非潜影盒留在背包。
     */
    private boolean isUnloadCandidate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        return StashMoveLogic.shouldUnload(id, onlyShulker.get());
    }

    private int findPearlSlotInContainer() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return -1;
        int size = handler.slots.size();
        for (int i = 0; i < size - 36; i++) {
            if (handler.getSlot(i).getStack().isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private int findPearlSlotInInventory() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return -1;
        int size = handler.slots.size();
        for (int i = size - 36; i < size; i++) {
            if (handler.getSlot(i).getStack().isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private int findEmptyInventorySlot() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return -1;
        int size = handler.slots.size();
        for (int i = size - 36; i < size; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    private int countPearls() {
        var inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.ENDER_PEARL)) n += s.getCount();
        }
        return n;
    }

    /** 主背包（9~35 格）里第一处珍珠的背包下标，没有返回 -1。 */
    private int findPearlIndexMainInventory() {
        var inv = mc.player.getInventory();
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private int findPearlHotbarSlot() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private void openChestAt(BlockPos pos) {
        if (pos == null) return;
        openChest = pos;
        BlockClicker.clickBlockFacingPlayer(pos, true, Hand.MAIN_HAND);
        cooldown = 15;
        progress();
    }

    private BlockPos nextLootChest() {
        ChestMode m = mode.get() == Mode.TRIO ? sourceChestMode.get() : chestMode.get();
        return switch (m) {
            case SINGLE -> {
                BlockPos t = mode.get() == Mode.TRIO ? sourceTarget.get() : targetChest.get();
                if (t.equals(BlockPos.ORIGIN) || blacklist.contains(t)) yield null;
                yield t;
            }
            case RANGE -> nextFromQueue(true);
            case AUTO -> nextFromQueue(false);
        };
    }

    private BlockPos nextFromQueue(boolean ranged) {
        if (chestQueue.isEmpty() || queueIndex >= chestQueue.size()) {
            chestQueue.clear();
            queueIndex = 0;
            if (ranged) {
                BlockPos a = mode.get() == Mode.TRIO ? sourcePos1.get() : pos1.get();
                BlockPos b = mode.get() == Mode.TRIO ? sourcePos2.get() : pos2.get();
                if (a.equals(BlockPos.ORIGIN) || b.equals(BlockPos.ORIGIN)) return null;
                scanChestsInBox(a, b, chestQueue, false);
            } else {
                BlockPos center = mc.player.getBlockPos();
                scanChestsInBox(center.add(-searchDistance.get(), -searchDistance.get(), -searchDistance.get()),
                    center.add(searchDistance.get(), searchDistance.get(), searchDistance.get()), chestQueue,
                    ignoreSingleChest.get());
                chestQueue.sort((x, y) -> Double.compare(
                    mc.player.getBlockPos().getSquaredDistance(x),
                    mc.player.getBlockPos().getSquaredDistance(y)));
            }
            if (chestQueue.isEmpty()) return null;
        }
        while (queueIndex < chestQueue.size()) {
            BlockPos p = chestQueue.get(queueIndex);
            if (blacklist.contains(p)) {
                queueIndex++;
                continue;
            }
            return p;
        }
        return null;
    }

    /** 扫描长方体范围内的箱子（双箱只记一份；单箱按「忽略单个箱子」过滤）。 */
    private void scanChestsInBox(BlockPos a, BlockPos b, List<BlockPos> out, boolean skipSingle) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        Set<BlockPos> seen = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    var state = mc.world.getBlockState(p);
                    if (!(state.getBlock() instanceof ChestBlock)) continue;
                    ChestType type = state.get(ChestBlock.CHEST_TYPE);
                    if (type == ChestType.SINGLE) {
                        if (skipSingle) continue;
                        out.add(p);
                        continue;
                    }
                    if (seen.contains(p)) continue;
                    Direction facing = state.get(ChestBlock.FACING);
                    int[] off = StashMoveLogic.chestOtherHalfOffset(type == ChestType.LEFT,
                        facing.getOffsetX(), facing.getOffsetZ());
                    BlockPos other = p.add(off[0], 0, off[1]);
                    seen.add(p);
                    seen.add(other);
                    out.add(p);
                }
            }
        }
    }

    private void blacklistChest(BlockPos pos) {
        if (pos == null) return;
        blacklist.add(pos);
        var state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return;
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) return;
        Direction facing = state.get(ChestBlock.FACING);
        int[] off = StashMoveLogic.chestOtherHalfOffset(type == ChestType.LEFT,
            facing.getOffsetX(), facing.getOffsetZ());
        blacklist.add(pos.add(off[0], 0, off[1]));
    }

    private BlockPos findNearestLava(BlockPos center, int radius) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int r = Math.min(radius, 100);
        for (int dx = -r; dx <= r; dx += 3) {
            for (int dy = -r; dy <= r; dy += 3) {
                for (int dz = -r; dz <= r; dz += 3) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (mc.world.getBlockState(p).getBlock() != net.minecraft.block.Blocks.LAVA) continue;
                    double d = center.getSquaredDistance(p);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private void faceBlock(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = center.x - mc.player.getX();
        double dz = center.z - mc.player.getZ();
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) (-Math.toDegrees(Math.atan2(center.y - mc.player.getEyePos().y,
            Math.sqrt(dx * dx + dz * dz)))));
    }

    private void releaseForward() {
        if (forwardHeld && mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
        }
    }

    /** 释放所有自动按下的移动键（前进 / 潜行 / 后退），关模块或卡住时调用。 */
    private void releaseMovementKeys() {
        releaseForward();
        releaseSneak();
        if (backoffTicks > 0 && mc.options != null) {
            mc.options.backKey.setPressed(false);
        }
        backoffTicks = 0;
    }

    private void warnThrottled(String msg) {
        if (tick - warnCooldown > 100) {
            warning(msg);
            warnCooldown = (int) Math.min(tick, Integer.MAX_VALUE);
        }
    }

    private void debugMsg(String msg) {
        if (debug.get()) info(msg);
    }

    private void enterStuck(String reason) {
        stuckReason = reason;
        nextAlarmTick = tick;
        if (mode.get() == Mode.TRIO) trioState = TrioState.STUCK;
        else moverState = MoverState.STUCK;
        releaseMovementKeys();
        PathManagers.get().stop();
        warning("自动搬运卡住：" + reason + "（已暂停，等手动处理）");
        nextAlarmTick = tick + 200;
    }

    // ==================== 渲染 ====================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Color green = new Color(0, 255, 0, 100);
        Color blue = new Color(0, 170, 255, 100);
        Color yellow = new Color(255, 255, 0, 100);
        Color orange = new Color(255, 140, 0, 100);
        Color cyan = new Color(0, 255, 255, 100);

        if (isMoverRole()) {
            if (mode.get() == Mode.MOVER) {
                drawIfSet(event, trapdoor.get(), green);
                drawIfSet(event, pearlChest.get(), blue);
                drawIfSet(event, storeChest.get(), yellow);
            } else {
                drawIfSet(event, sourceTrapdoor.get(), green);
                drawIfSet(event, storeTrapdoor.get(), green);
                drawIfSet(event, trioPearlChest.get(), blue);
                drawIfSet(event, trioStoreChest.get(), yellow);
            }
            for (BlockPos p : blacklist) {
                event.renderer.box(p, orange, orange, ShapeMode.Sides, 0);
            }
        }

        if (isPullerRole()) {
            for (String v : pearlPoints.get().values()) {
                int[] xyz = StashPearlLogic.parsePos(v);
                if (xyz == null) continue;
                event.renderer.box(new BlockPos(xyz[0], xyz[1], xyz[2]), cyan, cyan, ShapeMode.Sides, 0);
            }
            if (pullActive && pullPos != null) {
                event.renderer.box(pullPos, green, green, ShapeMode.Both, 0);
            }
        }
    }

    private void drawIfSet(Render3DEvent event, BlockPos pos, Color color) {
        if (pos == null || pos.equals(BlockPos.ORIGIN)) return;
        event.renderer.box(pos, color, color, ShapeMode.Both, 0);
    }
}
