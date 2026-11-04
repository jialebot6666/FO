package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.BlockClicker;
import com.fo.addon.utils.FacingLogic;
import com.fo.addon.utils.PeerMessageLogic;
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
import meteordevelopment.meteorclient.utils.player.Rotations;
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

    /**
     * 三号模式 1 号搬运端的阶段（V6.7p3 重排：校正 → 补货 → 投珍珠 → 取货/存货 → 私信）。
     *
     * <p>V6.7p7 在最前面插入 {@link #ARRIVE_WAIT}：被拉过来后先停 2 秒等区块加载再动手
     * （用户实机反馈：以前一落地就立刻开活板门/脱离，地形还没同步，判断全乱）。
     */
    private enum TrioState {
        ARRIVE_WAIT, SETTLE, RESTOCK, THROW, LOOT, UNLOAD, SEND, WAIT_TELEPORT, STUCK
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

    /**
     * 三号模式节奏常量（V6.7p7，用户口径「一步一步来，不要那么紧凑」）。
     *
     * <p>全部以 tick 计（20 tick = 1 秒）。原来各步骤之间只隔 2~5 tick，
     * 服务端还没把结果同步回来就进了下一步，表现就是「跳步、看起来没等」。
     */
    private static final int ARRIVE_WAIT_TICKS = 40;    // 被拉过来后先等 2 秒（区块/实体加载）
    private static final int PACE_SETTLE_DONE = 10;     // 脱离完成 → 下一步
    private static final int PACE_STEP = 6;             // 投掷子步骤之间
    private static final int PACE_THROW_DONE = 10;      // 投完 → 取货/存货
    private static final int PACE_RESTOCK_DONE = 10;    // 补珍珠完成 → 投掷
    private static final int PACE_TRAPDOOR_CLICK = 14;  // 点活板门后等方块状态同步

    private long tick;
    private int cooldown;

    // 双号搬运
    private MoverState moverState = MoverState.LOOT;
    private final Set<BlockPos> blacklist = new HashSet<>();
    private final List<BlockPos> chestQueue = new ArrayList<>();
    private int queueIndex;
    private BlockPos openChest;
    /** 连续点击同一个箱子却没打开的次数（点不开就报错，不再无限沉默重试）。 */
    private int openAttempts;
    /** 当前寻路目标（寻路守卫：同一目标不重复下发，避免每 tick 重置 Baritone 路径）。 */
    private BlockPos pathTarget;
    private long pathStartTick;
    private int pathRetries;
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
    private int walkPhase;
    private int escapeTicks;
    /** 脱困目标格（每 20 tick 重算，玩家在水里浮沉时目标会变）。 */
    private BlockPos escapeTarget;
    /** 每个区第一次找到的安全站位（缓存后复用，避免每次传送过来都重新搜一遍）。 */
    private final java.util.Map<StashMoveLogic.Zone, BlockPos> zoneSafeSpot = new java.util.HashMap<>();
    private int restockRetries;
    private long nextPearlCheckTick;
    private double lastWalkX;
    private double lastWalkZ;
    private StashMoveLogic.Zone trioZone = StashMoveLogic.Zone.UNKNOWN;
    private long teleportWaitStart;
    private Vec3d lastPos;

    // V6.7p7：到达后等待 + 逐步诊断
    /** 被拉过来后开始等待的 tick（ARRIVE_WAIT 截止点）。 */
    private long arriveWaitStart;
    /** 连续点活板门没打开的累计次数（换目标/打开成功即归零）。 */
    private int trapdoorClickAttempts;
    /** 调试心跳：上次打印摘要的 tick。 */
    private long lastHeartbeatTick;
    /** 心跳摘要真正打印出去的 tick（内容不变时用它控制「仍在…」的间隔）。 */
    private long lastHeartbeatPrintTick;
    /** 上一次打印的状态/子步骤，用于“只在变化时打印”。 */
    private String lastHeartbeatSummary = "";

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
        openAttempts = 0;
        pathTarget = null;
        pathStartTick = 0;
        pathRetries = 0;
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
        walkPhase = 0;
        escapeTicks = 0;
        escapeTarget = null;
        zoneSafeSpot.clear();
        restockRetries = 0;
        nextPearlCheckTick = 0;
        arriveWaitStart = 0;
        trapdoorClickAttempts = 0;
        lastHeartbeatTick = 0;
        lastHeartbeatPrintTick = 0;
        lastHeartbeatSummary = "";
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

        // 容器已打开 → 说明上一次点击成功，点击失败计数归零
        if (containerOpen()) openAttempts = 0;

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
        if (!approachForOpen(chest)) return;
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
        if (!approachForOpen(chestPos)) return;
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
        if (!approachForOpen(chestPos)) return;
        openChestAt(chestPos);
    }

    private void dualWalkStore() {
        BlockPos store = storeChest.get();
        if (store.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置存储箱位置");
            return;
        }
        if (containerOpen()) {
            moverState = MoverState.UNLOAD;
            return;
        }
        if (!approachForOpen(store)) return;
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
            if (!approachForOpen(chestPos)) return;
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
            setTrioState(TrioState.SETTLE, "启动时已确定所在区");
        }
        if (trioState != TrioState.STUCK && tick - lastProgressTick > 2400) {
            enterStuck("长时间没有进展（2 分钟无有效动作）");
            return;
        }

        trioHeartbeat();

        // V6.7p4：取货/存货期间每 5 秒确认一次本区珍珠还在（被踩掉就回去重投）
        if ((trioState == TrioState.LOOT || trioState == TrioState.UNLOAD)
            && tick >= nextPearlCheckTick) {
            nextPearlCheckTick = tick + 100;
            if (!ownPearlInColumn(trioTrapdoor().down(1))) {
                closeScreen();
                releaseMovementKeys();
                info("检测到本区珍珠消失（可能踩到气泡柱），重新投掷");
                setThrowSub(0, "珍珠没了");
                throwRetries = 0;
                setTrioState(TrioState.THROW, "本区珍珠消失了");
                return;
            }
        }

        // V6.7p4：干活期间出现位置突跳 = 意外传送（踩到珍珠被拉走），停下来等人工确认
        if ((trioState == TrioState.LOOT || trioState == TrioState.UNLOAD) && lastPos != null
            && StashMoveLogic.teleportJumped(lastPos.x, lastPos.z, mc.player.getX(), mc.player.getZ(), 32)) {
            enterStuck("检测到意外传送（可能踩到气泡柱），请手动检查");
            return;
        }

        switch (trioState) {
            case ARRIVE_WAIT -> trioArriveWait();
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

    /**
     * 到达等待（V6.7p7，用户口径）：被拉过来后先原地停 2 秒。
     *
     * <p>为什么不直接干活：刚传送落地时区块还没同步完、脚下方块可能还是空气，
     * 立刻判断「站没站稳 / 活板门开没开」必然全是错的，然后就一路错下去。
     * 这 2 秒里只做三件事：松键、停寻路、什么都不碰。
     */
    private void trioArriveWait() {
        releaseMovementKeys();
        if (PathManagers.get().isPathing()) PathManagers.get().stop();
        long waited = tick - arriveWaitStart;
        if (debug.get() && waited % 20 == 0) {
            info("[三号][到达] 等待加载 " + (waited / 20) + "/" + (ARRIVE_WAIT_TICKS / 20) + " 秒");
        }
        if (waited < ARRIVE_WAIT_TICKS) return;
        setTrioState(TrioState.SETTLE, "等待结束，开始脱离");
        cooldown = PACE_STEP;
    }

    private void trioLoot() {
        if (inventoryFull()) {
            closeScreen();
            debugMsg("[取货] 背包已满，结束取货");
            setTrioState(TrioState.SEND, "背包已满，取货结束");
            return;
        }
        if (containerOpen()) {
            if (takeFromContainer()) return;
            debugMsg("[取货] 这个箱子取完了，拉黑并找下一个");
            closeScreen();
            blacklistChest(openChest);
            openChest = null;
            trapdoorClickAttempts = 0;
            cooldown = PACE_STEP;
            return;
        }
        BlockPos chest = nextLootChest();
        if (chest == null) {
            enterStuck("待搬运箱区已搬空（没有可搬的箱子了）");
            return;
        }
        if (!approachForOpen(chest)) return;
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
                debugMsg("[存货] 没有可卸物品，结束存货");
                setTrioState(TrioState.SEND, "存货完成");
                return;
            }
            if (!(mc.world.getBlockState(store).getBlock() instanceof ChestBlock)) {
                enterStuck("存储箱不存在");
                return;
            }
            if (!approachForOpen(store)) return;
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
            debugMsg("[存货] 背包里没有可卸的物品了");
            setTrioState(TrioState.SEND, "存货完成");
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
            debugMsg("[补珍珠] 珍珠 " + have + " 颗已够（阈值 " + restockThreshold.get() + "），不用补");
            setTrioState(TrioState.THROW, "珍珠数量已够");
            cooldown = PACE_RESTOCK_DONE;
            return;
        }
        // V6.7p4：取消 64 格距离闸——珍珠不够就寻路过去补（走不过去由看门狗报警）
        double distSq = mc.player.getBlockPos().getSquaredDistance(chestPos);
        if (!containerOpen()) {
            // 开新容器前先关掉可能残留的界面，避免读到别的容器
            if (mc.currentScreen != null) {
                closeScreen();
                cooldown = 5;
                return;
            }
            if (distSq > 400) {
                if (!PathManagers.get().isPathing()) PathManagers.get().moveTo(chestPos, true);
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
            debugMsg("[补珍珠] 开始补货：差 " + need + " 颗，箱内槽位=" + restockChestSlot
                + " 背包槽位=" + restockInvSlot);
            if (restockChestSlot < 0) {
                // V6.7p4：诊断 + 空读重试（排除槽位同步延迟 / 开错容器）
                var handler = containerHandler();
                debugMsg("[补珍珠] 界面=" + (handler == null ? "null" : handler.getClass().getSimpleName())
                    + " 槽位=" + (handler == null ? 0 : handler.slots.size())
                    + " 找到珍珠槽位=" + restockChestSlot);
                closeScreen();
                restockStepsLeft = 0;
                if (restockRetries < 2) {
                    restockRetries++;
                    debugMsg("珍珠箱里没读到珍珠，第 " + restockRetries + " 次重开确认");
                    cooldown = 20;
                    return;
                }
                restockRetries = 0;
                if (have > 0) {
                    info("珍珠箱里没有珍珠了，先用剩余 " + have + " 颗");
                    setTrioState(TrioState.THROW, "珍珠箱空了，用剩余珍珠");
                    cooldown = PACE_RESTOCK_DONE;
                } else {
                    enterStuck("珍珠箱里没有珍珠");
                }
                return;
            }
            restockRetries = 0;
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
            debugMsg("[补珍珠] 补货完成，现在有 " + countPearls() + " 颗");
            setTrioState(TrioState.THROW, "补货完成");
            cooldown = PACE_RESTOCK_DONE;
        }
    }

    /** 当前所在区的中文名（提示消息里不要出现 SOURCE / STORE 这种英文枚举名）。 */
    private String zoneName() {
        return trioZone == StashMoveLogic.Zone.SOURCE ? "待搬运箱区"
            : trioZone == StashMoveLogic.Zone.STORE ? "存储箱区" : "未知区域";
    }

    /** 当前所在区的活板门坐标。 */
    private BlockPos trioTrapdoor() {
        return trioZone == StashMoveLogic.Zone.SOURCE ? sourceTrapdoor.get() : storeTrapdoor.get();
    }

    /**
     * 每 tick 把视角转向本区气泡柱（V6.7p7，用户口径「全程都要转头」）。
     *
     * <p>用 Meteor 的 {@link Rotations}，第一人称真的会转（miku 的 RotationManager 只改第三人称身体朝向，
     * 6.7p5 已踩过这个坑）。优先级给 5：低于开箱/点活板门的 60，不会被它们覆盖。
     */
    private void faceColumn() {
        if (mc.player == null) return;
        BlockPos td = trioTrapdoor();
        if (td == null || td.equals(BlockPos.ORIGIN)) return;
        Vec3d c = Vec3d.ofCenter(td.down(1));
        float[] yp = FacingLogic.yawPitchTo(c.x, c.y, c.z,
            mc.player.getX(), mc.player.getEyePos().y, mc.player.getZ());
        Rotations.rotate(yp[0], yp[1], 5);
    }

    /**
     * ① 校正站位（V6.7p3，用户口径）：被拉过来后先走离气泡柱/活板门，
     * 站到最近的实心安全格，确认没踩在水柱或活板门上。
     */
    private void trioSettle() {
        BlockPos stand = trioTrapdoor();
        if (stand == null || stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置" + zoneName() + "的活板门位置");
            return;
        }
        faceColumn();
        if (settlePosition(stand)) {
            debugMsg("[脱离] 已站稳在 " + mc.player.getBlockPos().toShortString() + "，确认没踩水柱/活板门");
            setTrioState(TrioState.RESTOCK, "已脱离气泡柱");
            cooldown = PACE_SETTLE_DONE;
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

        // ① 先脱困（V6.7p4）：被活板门压住 / 泡在气泡柱里时，必须先开活板门再往外挪
        boolean insideTrapdoor = feet.equals(trapdoorPos)
            || mc.world.getBlockState(feet).getBlock() instanceof TrapdoorBlock
            || mc.world.getBlockState(feet.down()).getBlock() instanceof TrapdoorBlock;
        boolean inWater = mc.player.isTouchingWater() || feet.equals(water)
            || !mc.world.getBlockState(feet.down()).getFluidState().isEmpty();
        if (insideTrapdoor || inWater) {
            if (!trapdoorIsOpenOrAbsent(trapdoorPos)) {
                debugMsg("脱困：先打开活板门");
                BlockClicker.clickBlockFacingPlayer(trapdoorPos, true, Hand.MAIN_HAND);
                cooldown = 10;
                return false;
            }
            // 每 20 tick 重新找一次（玩家在水里会上下浮，目标格会变）；
            // 优先用本区上次找到的安全格（结构没变就一直有效，省得每次重搜）
            if (escapeTarget == null || tick % 20 == 0) {
                BlockPos cached = zoneSafeSpot.get(trioZone);
                escapeTarget = (cached != null && isStandable(cached)) ? cached : pearlStandSpot(trapdoorPos);
            }
            BlockPos escape = escapeTarget;
            if (escape == null) {
                dumpStandSpotFailure(trapdoorPos);
                // 兜底：真的没有可站格时，先按住 shift + 后退贴离水柱（不再直接停下等人）
                if (escapeTicks < 40) {
                    sneakForThrow();
                    mc.options.backKey.setPressed(true);
                    escapeTicks++;
                    if (escapeTicks == 1) debugMsg("脱困：没找到可站格，先潜行后退离开水柱");
                    return false;
                }
                releaseMovementKeys();
                enterStuck("气泡柱周围找不到可站的实心格，请手动站到旁边");
                return false;
            }
            // 手动脱困：朝安全格方向游/走（水里或目标在上方时同时按跳）
            Vec3d c = Vec3d.ofCenter(escape);
            mc.player.setYaw((float) (Math.toDegrees(Math.atan2(c.z - mc.player.getZ(), c.x - mc.player.getX())) - 90.0));
            mc.options.forwardKey.setPressed(true);
            forwardHeld = true;
            if (mc.player.isTouchingWater() || escape.getY() > feet.getY()) {
                mc.options.jumpKey.setPressed(true);
            }
            escapeTicks++;
            if (escapeTicks > 120) {
                releaseMovementKeys();
                dumpStandSpotFailure(trapdoorPos);
                enterStuck("无法脱离气泡柱（120 tick 未脱困），请手动处理");
            }
            if (debug.get() && escapeTicks % 20 == 1) {
                debugMsg("脱困中：目标=" + escape.toShortString() + " (" + escapeTicks + "/120)");
            }
            return false;
        }
        escapeTicks = 0;
        escapeTarget = null;
        if (mc.options != null) {
            mc.options.jumpKey.setPressed(false);
            mc.options.backKey.setPressed(false);
        }

        // ② 离珍珠点太远（例如刚补完珍珠）→ 先回到活板门旁的安全格
        if (feet.getSquaredDistance(trapdoorPos) > 9) {
            BlockPos far = pearlStandSpot(trapdoorPos);
            if (far != null && !PathManagers.get().isPathing()) {
                PathManagers.get().moveToPrecise(far);
            }
            return false;
        }
        throwSafeSpot = feet;
        return true;
    }

    /**
     * 投掷子流程（V6.7p3 建立，V6.7p7 按用户口径重排节奏）：
     * 检珍珠 → 校正站位 → <b>确认脱离</b> → <b>开活板门（大部分时候是关的，必须自己点开并确认）</b>
     * → 按住 Shift 走近 → 投掷 → 退回安全格 → 校验（失败重试 3 次）。
     *
     * <p>V6.7p7 改动：每个子步骤之间留 6 tick（原来 2 tick）、点完活板门等 14 tick 让服务端同步方块状态、
     * 校验超时 60 → 80 tick；每 tick 都调用 {@link #faceColumn()} 把视角锁在气泡柱上（全程转头）。
     */
    private void trioThrow() {
        BlockPos stand = trioTrapdoor();
        if (stand == null || stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置" + zoneName() + "的活板门位置");
            return;
        }
        BlockPos water = stand.down(1);

        // 每 tick 都把视角对准气泡柱（用户口径：全程都要转头）。
        // 唯独「正在投掷」这一 tick 不叠加：doThrowPearl 会以更高优先级登记精确瞄准，
        // 这里再压一条低优先级旋转会把镜头拽回柱心，干扰投掷视角。
        if (throwSub != 4) faceColumn();

        switch (throwSub) {
            case 0 -> {
                if (ownPearlInColumn(water)) {
                    debugMsg("[投掷] 气泡柱里已有自己的珍珠，跳过投掷");
                    finishTrioThrow();
                    return;
                }
                throwRetries = 0;
                setThrowSub(1, "柱里没珍珠，准备投一颗");
            }
            case 1 -> {
                if (settlePosition(stand)) {
                    debugMsg("[投掷] 站位已确认：" + mc.player.getBlockPos().toShortString());
                    setThrowSub(2, "脱离完成，开始检查活板门");
                    cooldown = PACE_STEP;
                }
            }
            case 2 -> {
                if (trapdoorIsOpenOrAbsent(stand)) {
                    if (trapdoorClickAttempts > 0) {
                        debugMsg("[活板门] 已确认打开（点了 " + trapdoorClickAttempts + " 次）");
                    }
                    trapdoorClickAttempts = 0;
                    setThrowSub(3, "活板门已开，开始潜行走近");
                    walkTicks = 0;
                    stallTicks = 0;
                    lastWalkX = mc.player.getX();
                    lastWalkZ = mc.player.getZ();
                    cooldown = PACE_STEP;
                    return;
                }
                // 关着 → 必须先自己打开（用户口径：大部分时间都是关的）
                if (trapdoorClickAttempts >= 8) {
                    enterStuck("连续 8 次都打不开活板门 " + stand.toShortString()
                        + "（眼睛距=" + String.format(Locale.ROOT, "%.2f", mc.player.getEyePos().distanceTo(Vec3d.ofCenter(stand)))
                        + "，请手动确认这个坐标是不是活板门、有没有被方块挡住）");
                    return;
                }
                if (!approachForOpen(stand)) {
                    debugMsg("[活板门] 离得还远，先走近");
                    return;
                }
                trapdoorClickAttempts++;
                debugMsg("[活板门] 关着 → 点击打开（第 " + trapdoorClickAttempts + " 次）");
                BlockClicker.clickBlockFacingPlayer(stand, true, Hand.MAIN_HAND);
                cooldown = PACE_TRAPDOOR_CLICK;
            }
            case 3 -> {
                int r = walkToColumnCenter(stand);
                if (r == 1) {
                    setThrowSub(4, "已走到柱边");
                    cooldown = PACE_STEP;
                } else if (r == 2) {
                    debugMsg("[走近] 踩进气泡柱了，先退开再重试");
                    setThrowSub(5, "走近时踩进水里");
                    cooldown = PACE_STEP;
                }
            }
            case 4 -> {
                int slot = findPearlHotbarSlot();
                if (slot < 0) {
                    int inv = findPearlIndexMainInventory();
                    if (inv >= 0) {
                        InvUtils.move().from(inv).toHotbar(mc.player.getInventory().getSelectedSlot());
                        cooldown = PACE_STEP;
                        return;
                    }
                    enterStuck("快捷栏里没有末影珍珠");
                    return;
                }
                sneakForThrow();
                debugMsg("[投掷] 按住 Shift 投出（快捷栏槽位 " + slot + "）");
                doThrowPearl(slot, Vec3d.ofCenter(water));
                setThrowSub(5, "已投出，准备后退");
                cooldown = PACE_STEP;
            }
            case 5 -> {
                if (backOffToSafe()) {
                    setThrowSub(6, "已退到安全距离");
                    verifyStartTick = tick;
                    cooldown = PACE_STEP;
                }
            }
            case 6 -> {
                if (ownPearlInColumn(water)) {
                    lastPearlThrowTick = tick;
                    debugMsg("[校验] 气泡柱里已有自己的珍珠，投掷成功");
                    finishTrioThrow();
                } else if (tick - verifyStartTick > 80) {
                    throwRetries++;
                    if (throwRetries >= 3) {
                        enterStuck("投掷珍珠 3 次失败（气泡柱里没有检测到自己的珍珠）");
                    } else {
                        debugMsg("[校验] 柱里没珍珠，第 " + throwRetries + " 次重试");
                        setThrowSub(2, "校验失败，重开活板门重投");
                        cooldown = PACE_STEP;
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
        trapdoorClickAttempts = 0;
        boolean toStore = trioZone == StashMoveLogic.Zone.STORE;
        setTrioState(toStore ? TrioState.UNLOAD : TrioState.LOOT,
            toStore ? "回程票已就位，开始存货" : "回程票已就位，开始取货");
        cooldown = PACE_THROW_DONE;
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
     * <p>停滞判定：连续 3 tick 水平位移 &lt; 0.05 格；另加 60 tick 上限保护。
     * V6.7p7：点按节奏从「每 3 tick 走 1 tick」放到「每 4 tick 走 1 tick」，上限 45 → 60 tick
     * （用户口径：不要那么紧凑，一步一步来）；同时把视角锁在柱心（第一人称真的转头）。
     *
     * @return 0=还在走 1=已到边缘可投 2=踩进水里要退
     */
    private int walkToColumnCenter(BlockPos trapdoorPos) {
        sneakForThrow();
        Vec3d center = Vec3d.ofCenter(trapdoorPos.down(1));
        float yaw = (float) (Math.toDegrees(Math.atan2(center.z - mc.player.getZ(), center.x - mc.player.getX())) - 90.0);
        // setYaw 决定「前进键往哪走」（服务端视角由下面的 Rotations 负责）
        mc.player.setYaw(yaw);
        mc.player.setPitch(90f);
        Rotations.rotate(yaw, 90f, 5);

        // 踩进气泡柱了：立刻停手，交给后退阶段（V6.7p4）
        if (mc.player.isTouchingWater()) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
            return 2;
        }

        // 慢速点按：每 4 tick 只按 1 tick，走一小步就重新判定（原来每 tick 按住容易冲过头）
        walkPhase = (walkPhase + 1) % 4;
        boolean press = walkPhase == 0;
        mc.options.forwardKey.setPressed(press);
        forwardHeld = press;
        walkTicks++;

        boolean stalled = StashMoveLogic.isStalled(lastWalkX, lastWalkZ, mc.player.getX(), mc.player.getZ(), 0.05);
        lastWalkX = mc.player.getX();
        lastWalkZ = mc.player.getZ();
        if (stallTicks == 0) {
            stallTicks = 1;
            return 0;
        }
        stallTicks = stalled ? stallTicks + 1 : 1;

        if (stallTicks >= 3 || walkTicks >= 60) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
            walkTicks = 0;
            stallTicks = 0;
            return 1;
        }
        return 0;
    }

    /** ④ 松开 shift、按 S 退离气泡柱（退到离柱心 ≥2.5 格），再回到投掷前的原位。 */
    private boolean backOffToSafe() {
        releaseSneak();
        BlockPos water = trioTrapdoor().down(1);
        Vec3d center = Vec3d.ofCenter(water);
        double dist = Math.hypot(center.x - mc.player.getX(), center.z - mc.player.getZ());

        if (StashMoveLogic.needMoreBackoff(dist, 2.5)) {
            // 还没退够：继续按 S（tick 处理器会在 backoffTicks 归零时松开）
            if (backoffTicks <= 0) {
                backoffTicks = 20;
                progress();
            }
            if (mc.player.isTouchingWater()) {
                // 在水里按 S 退不快，改用跳+后退
                mc.options.jumpKey.setPressed(true);
            }
            return false;
        }

        // 退够了：立刻松键，避免继续退过头
        if (mc.options != null) {
            mc.options.backKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }
        backoffTicks = 0;

        if (throwSafeSpot == null) return true;
        if (mc.player.getBlockPos().getSquaredDistance(throwSafeSpot) <= 4) return true;
        if (!PathManagers.get().isPathing()) {
            PathManagers.get().moveToPrecise(throwSafeSpot);
        }
        return false;
    }

    private void trioSend() {
        // V6.7p4：发私信前再确认一次本区珍珠还在（被踩掉就不要发，回去重投）
        if (!ownPearlInColumn(trioTrapdoor().down(1))) {
            info("发私信前发现本区珍珠不在了，回去重投");
            setThrowSub(0, "珍珠不在了");
            throwRetries = 0;
            setTrioState(TrioState.THROW, "发私信前发现珍珠没了");
            return;
        }
        String target = trioZone == StashMoveLogic.Zone.SOURCE ? peerStore.get().trim() : peerSource.get().trim();
        if (target.isEmpty()) {
            enterStuck(trioZone == StashMoveLogic.Zone.SOURCE ? "请填写 3号ID（存储箱端）" : "请填写 2号ID（待搬运箱端）");
            return;
        }
        debugMsg("[私信] 向 " + target + " 发「拉我」");
        sendPullRequest(target);
        teleportWaitStart = tick;
        setTrioState(TrioState.WAIT_TELEPORT, "已发拉我，等待被拉走");
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
            trapdoorClickAttempts = 0;
            throwSafeSpot = null;
            // V6.7p7：先停 2 秒等区块加载，再开始脱离（用户口径：以前一落地就动手，判断全是错的）
            arriveWaitStart = tick;
            setTrioState(TrioState.ARRIVE_WAIT, "已到达" + zoneName());
            releaseMovementKeys();
            PathManagers.get().stop();
            info("已到达" + zoneName() + "，等 2 秒让区块加载");
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
     * <p>视角必须“服务端已看到”才算数。V6.7p7 起改用 Meteor 的 {@link Rotations}：
     * 先让这一 tick 的移动包带上目标朝向，回调里再发交互包（顺序：移动包带朝向 → 交互包），
     * 并且第一人称真的会把头转过去（miku 的 RotationManager 只改第三人称身体，6.7p5 已确认）。
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
        // V6.7p7：改用 Meteor 的 Rotations（第一人称真的转头，见 6.7p5 的教训）；
        // 回调里再发交互包——保证顺序是「移动包带目标朝向 → 服务端看到朝向 → 才收到投掷」。
        mc.player.setYaw(yp[0]);
        mc.player.setPitch(yp[1]);
        Rotations.rotate(yp[0], yp[1], 60,
            () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));

        releaseSneak();
        backoffTicks = 60;   // 投完按 S 后退（实际按“离柱心 ≥2.5 格”提前停，60 只是上限）
        cooldown = 3;
        progress();
    }

    /**
     * 找活板门旁「能站人」的实心格（不踩气泡柱）：2 格半径内、脚下实心、身体两格是空气，
     * 取离玩家最近的一个；找不到返回 null。
     */
    private BlockPos pearlStandSpot(BlockPos trapdoorPos) {
        if (mc.world == null || trapdoorPos == null) return null;
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos water = trapdoorPos.down(1);
        // V6.7p6：由近到远逐圈扫（半径 1→4），找到最近一圈就停；Y 范围放到 -2..3，
        // 岸在水柱上一层/下一层都能找到（原来只扫 ±2 格、Y±1，珍珠房稍宽一点就一律判“没有实心格”）
        for (int r = 1; r <= 4; r++) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;   // 只扫本圈
                    for (int dy = -2; dy <= 3; dy++) {
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
            if (best != null) {
                zoneSafeSpot.put(trioZone, best);
                return best;
            }
        }
        return null;
    }

    /**
     * 该格能不能站人（V6.7p6 改用碰撞箱判定）。
     *
     * <p>原来用 {@code isAir()} 判空，凡是「非空气但无碰撞」的方块（草、雪层、藤蔓、告示牌、
     * 按钮、打开的活板门……）一律被判成“站不了”，于是明明旁边就是能站的地面也报
     * 「找不到可站的实心格」。现在改成：本格与头顶无碰撞即可通行，脚下方块有碰撞且不含流体。
     */
    private boolean isStandable(BlockPos p) {
        if (!mc.world.getBlockState(p).getCollisionShape(mc.world, p).isEmpty()) return false;
        if (!mc.world.getBlockState(p.up()).getCollisionShape(mc.world, p.up()).isEmpty()) return false;
        var below = mc.world.getBlockState(p.down());
        if (below.getCollisionShape(mc.world, p.down()).isEmpty()) return false;
        return below.getFluidState().isEmpty();
    }

    /** 找不到安全格时把玩家/活板门周边的实际方块打到调试输出，方便定位结构问题。 */
    private void dumpStandSpotFailure(BlockPos trapdoorPos) {
        if (!debug.get() || mc.world == null || mc.player == null) return;
        BlockPos feet = mc.player.getBlockPos();
        BlockPos water = trapdoorPos.down(1);
        debugMsg("[脱困] 找不到可站格｜玩家格=" + feet.toShortString()
            + " 自身=" + blockName(feet)
            + " 脚下=" + blockName(feet.down())
            + " 水中=" + mc.player.isTouchingWater()
            + "｜活板门=" + trapdoorPos.toShortString()
            + " 打开=" + trapdoorIsOpenOrAbsent(trapdoorPos)
            + " 水格=" + blockName(water));
    }

    private String blockName(BlockPos p) {
        if (mc.world == null) return "?";
        return net.minecraft.registry.Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
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

    /** 开箱的有效距离（眼睛到方块中心，单位格）。超过就先寻路靠近，绝不隔空点。 */
    private static final double OPEN_REACH = 3.5;

    /**
     * 统一的“走过去”入口（V6.7p5）。
     *
     * <p>旧实现在自动搜索 / 单箱 / 存储箱等分支里<b>每 tick 裸调 moveTo</b>，Baritone 会每 tick
     * 重新计算路径（等于永远走不完），表现就是“人站着不动、箱子离远一点就永远点不开”。
     * 这里加守卫：同一目标正在走就不重复下发；走太久没到才重设，最多 2 次。
     */
    private void pathTo(BlockPos pos, boolean ignoreY) {
        if (pos == null || pos.equals(BlockPos.ORIGIN)) return;
        if (!pos.equals(pathTarget)) {
            pathTarget = pos;
            pathStartTick = tick;
            pathRetries = 0;
            if (PathManagers.get().isPathing()) PathManagers.get().stop();
            PathManagers.get().moveTo(pos, ignoreY);
            return;
        }
        if (PathManagers.get().isPathing()) {
            // 走太久还没到（可能被卡住/目标不可达）→ 重设目标
            if (tick - pathStartTick > 240 && pathRetries < 2) {
                pathRetries++;
                pathStartTick = tick;
                PathManagers.get().stop();
                PathManagers.get().moveTo(pos, ignoreY);
                debugMsg("寻路超时，重设目标（第 " + pathRetries + " 次）");
            }
            return;
        }
        // Baritone 自己停下了 → 隔 20 tick 再试一次，避免每 tick 重算
        if (tick - pathStartTick > 20) {
            pathStartTick = tick;
            PathManagers.get().moveTo(pos, ignoreY);
        }
    }

    /**
     * 开箱前先靠近：眼睛到方块中心 ≤ {@link #OPEN_REACH} 格才允许点击。
     *
     * <p>服务端对交互有距离校验，隔空点击会被静默丢弃（表现为“点了没反应”）；
     * 这里统一把“够不够近”前置判断，不够就先走。
     *
     * @return true = 已经在可点击范围内
     */
    private boolean approachForOpen(BlockPos pos) {
        if (pos == null || pos.equals(BlockPos.ORIGIN)) return false;
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d eye = mc.player.getEyePos();
        if (eye.distanceTo(center) <= OPEN_REACH) {
            pathTarget = null;
            return true;
        }
        // 已经站在方块正上方/正下方（XZ 基本重合）：GoalXZ 寻路不再有帮助，直接允许点击
        // （否则会出现"XZ 距离=0、Baritone 认为已到达、却因为高度差永远判超距"的死循环）
        if (Math.hypot(eye.x - center.x, eye.z - center.z) <= 1.2) {
            pathTarget = null;
            return true;
        }
        pathTo(pos, true);
        return false;
    }

    private void openChestAt(BlockPos pos) {
        if (pos == null) return;
        // ⚠️ V6.7p7 修复：换目标必须重新计数。
        // 原来 openAttempts 只在「容器真的打开」时归零，换到下一个箱子仍继续累加；
        // 连着几个箱子各点两三次没开（比如站位略偏），凑够 8 次就误报
        // 「连续 8 次都打不开箱子」并永久暂停 —— 这就是用户说的「有时候不会打开箱子」。
        if (!pos.equals(openChest)) {
            openAttempts = 0;
            if (debug.get()) {
                info("[开箱] 换目标 → " + pos.toShortString() + "，失败计数归零");
            }
        }
        openChest = pos;
        // 保险：调用方已经判过距离，这里再兜一次，避免别处漏判后隔空点击
        if (!approachForOpen(pos)) return;
        if (openAttempts >= 8) {
            enterStuck("连续 8 次都打不开箱子 " + pos.toShortString() + "（检查是否被方块挡住或位置填错）");
            return;
        }
        openAttempts++;
        Vec3d center = Vec3d.ofCenter(pos);
        debugMsg("[开箱] 点击 " + pos.toShortString() + " 第 " + openAttempts + " 次"
            + "（眼睛距 " + String.format(Locale.ROOT, "%.2f", mc.player.getEyePos().distanceTo(center))
            + " / 阈值 " + OPEN_REACH + "，界面=" + (mc.currentScreen == null ? "无" : mc.currentScreen.getClass().getSimpleName()) + "）");
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
        if (mc.options != null) {
            mc.options.backKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
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

    // ==================== 三号模式调试（V6.7p7 重做） ====================
    //
    // 用户反馈：打开「调试输出」后看不出卡在哪一步。原来的输出只有零星几条，
    // 状态切换完全没记录。现在改成「状态/子步骤一变就打印 + 每 2 秒一条心跳摘要」，
    // 出问题时把聊天栏截图发过来就能直接定位到「哪个状态、哪个子步骤、什么数值」。

    /** 三号模式状态切换（只在变化时打印，避免刷屏）。 */
    private void setTrioState(TrioState next, String why) {
        if (trioState == next) return;
        TrioState prev = trioState;
        trioState = next;
        if (debug.get()) {
            info("[三号][状态] " + prev + " → " + next + (why == null || why.isEmpty() ? "" : "（" + why + "）"));
        }
    }

    /** 投掷子步骤切换（只在变化时打印）。 */
    private void setThrowSub(int next, String why) {
        if (throwSub == next) return;
        int prev = throwSub;
        throwSub = next;
        if (debug.get()) {
            info("[三号][投掷] 子步骤 " + prev + " → " + next + " " + THROW_SUB_NAMES[next]
                + (why == null || why.isEmpty() ? "" : "（" + why + "）"));
        }
    }

    private static final String[] THROW_SUB_NAMES = {
        "开始/检珍珠", "校正站位", "开活板门", "潜行走近", "投掷", "退回安全格", "校验珍珠"
    };

    /**
     * 每 2 秒打一条心跳摘要：区域 / 状态 / 子步骤 / 珍珠数 / 离柱心距离 / 活板门状态 / 是否在水里。
     *
     * <p>只在「调试输出」开启时打印；内容无变化时跳过，避免同一状态刷满屏。
     */
    private void trioHeartbeat() {
        if (!debug.get()) return;
        if (tick - lastHeartbeatTick < 40) return;
        lastHeartbeatTick = tick;

        BlockPos td = trioTrapdoor();
        String zone = trioZone == StashMoveLogic.Zone.SOURCE ? "待搬运箱区"
            : trioZone == StashMoveLogic.Zone.STORE ? "存储箱区" : "未知区";
        String dist = "-";
        String door = "-";
        if (td != null && !td.equals(BlockPos.ORIGIN)) {
            Vec3d c = Vec3d.ofCenter(td.down(1));
            double d = Math.hypot(c.x - mc.player.getX(), c.z - mc.player.getZ());
            dist = String.format(Locale.ROOT, "%.2f", d);
            door = trapdoorIsOpenOrAbsent(td) ? "开" : "关";
        }
        String summary = zone + " 状态=" + trioState
            + (trioState == TrioState.THROW ? "(子步骤" + throwSub + ":" + THROW_SUB_NAMES[Math.min(throwSub, 6)] + ")" : "")
            + " 珍珠=" + countPearls()
            + " 距柱心=" + dist
            + " 活板门=" + door
            + " 在水里=" + (mc.player.isTouchingWater() ? "是" : "否");

        // 内容变了就打；内容没变则每 10 秒补一条「仍在…」，让人知道没死（也方便算卡了多久）
        if (summary.equals(lastHeartbeatSummary)) {
            if (tick - lastHeartbeatPrintTick < 200) return;
            info("[三号][心跳] 仍在 " + summary);
        } else {
            info("[三号][心跳] " + summary);
        }
        lastHeartbeatSummary = summary;
        lastHeartbeatPrintTick = tick;
    }

    private void enterStuck(String reason) {
        stuckReason = reason;
        nextAlarmTick = tick;
        if (mode.get() == Mode.TRIO) {
            if (debug.get()) {
                info("[卡住] 区域=" + zoneName() + " 状态=" + trioState + " 子步骤=" + throwSub
                    + " 珍珠=" + countPearls() + " 原因：" + reason);
            }
            trioState = TrioState.STUCK;
        } else moverState = MoverState.STUCK;
        releaseMovementKeys();
        PathManagers.get().stop();
        warning("自动搬运卡住：" + reason + "（已暂停，等手动处理）");
        nextAlarmTick = tick + 200;
    }

    // ==================== 渲染 ====================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Color green = new Color(0, 255, 0, 100);        // 珍珠房活板门
        Color blue = new Color(0, 170, 255, 100);       // 珍珠箱
        Color yellow = new Color(255, 255, 0, 100);     // 存储箱
        Color orange = new Color(255, 140, 0, 100);     // 已搬空拉黑
        Color cyan = new Color(0, 255, 255, 100);       // 珍珠点（拉珍珠端）
        Color source = new Color(255, 0, 200, 160);     // 待搬运箱（品红，最显眼）
        Color faint = new Color(255, 0, 200, 45);       // 扫描到的其它待搬运箱（淡）
        Color active = new Color(255, 255, 255, 220);   // 当前正在操作的箱子

        if (isMoverRole()) {
            if (mode.get() == Mode.MOVER) {
                drawIfSet(event, targetChest.get(), source);    // 待搬运箱（单箱子模式的坐标）
                drawIfSet(event, trapdoor.get(), green);
                drawIfSet(event, pearlChest.get(), blue);
                drawIfSet(event, storeChest.get(), yellow);
            } else {
                drawIfSet(event, sourceTarget.get(), source);   // 待搬运箱（三号模式坐标）
                drawIfSet(event, sourceTrapdoor.get(), green);
                drawIfSet(event, storeTrapdoor.get(), green);
                drawIfSet(event, trioPearlChest.get(), blue);
                drawIfSet(event, trioStoreChest.get(), yellow);
            }
            // 自动/框选扫到的待搬运箱队列（当前目标另外用高亮画）
            for (BlockPos p : chestQueue) {
                if (p.equals(openChest)) continue;
                event.renderer.box(p, faint, faint, ShapeMode.Sides, 0);
            }
            // 当前正在操作（要点开）的箱子：白框全亮，一眼看出它在开哪一格
            if (openChest != null && !openChest.equals(BlockPos.ORIGIN)) {
                event.renderer.box(openChest, active, active, ShapeMode.Both, 0);
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
