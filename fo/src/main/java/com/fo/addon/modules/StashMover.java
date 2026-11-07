package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.pathing.PathManagers;
import com.fo.addon.utils.BlockClicker;
import com.fo.addon.utils.FacingLogic;
import com.fo.addon.utils.PeerMessageLogic;
import com.fo.addon.utils.StashMoveLogic;
import com.fo.addon.utils.StashMoveLogic.BoxSide;
import com.fo.addon.utils.StashChestSpotLogic;
import com.fo.addon.utils.StashPearlLogic;
import com.fo.addon.utils.StashStats;
import com.fo.addon.utils.StashStatsSetting;
import com.fo.addon.utils.StringMapSetting;
import com.fo.addon.utils.TeleportFloodDetector;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * 双号搬运端的路线（V6.7p14 按三号的走法重排）。
     *
     * <p>旧顺序是「发拉我 → 等收到 → 走向柱子 → 投珍珠 → 还珍珠 → 走去存储箱 → 卸货 → 自杀」，
     * 两个硬伤：① 收到「收到N」时人还在待搬运箱区，代码立刻开始走向存储区的活板门
     * （源区读不到那边的区块，站位搜索必然返回空 → 直接把活板门当归宿点跨图寻路），
     * 被珍珠拉过去的瞬间旧路径还活着，人就会顺着旧路往回走/乱走；
     * ② 卸货排在投珍珠后面，投掷链路任何一环不过，卸货永远轮不到 —— 表现为「不储存」。
     *
     * <p>现在的路线（与三号同构）：
     * 取货 → 发拉我 → 等确认 → <b>等落地</b> → <b>到达静默 2 秒</b> → <b>脱离气泡柱</b>
     * → 卸货 → 取珍珠 → 投回程票 → 还余量 → <b>确认腔室有珍珠</b> → 延时自杀 → 重生。
     */
    private enum MoverState {
        ZONE_CHECK,
        LOOT, ENDER_PUT, SEND, WAIT_ACK, WAIT_TELEPORT, ARRIVE_WAIT, SETTLE,
        UNLOAD, ENDER_TAKE, TAKE_PEARL, THROW, PUT_BACK, CONFIRM_PEARL,
        SUICIDE_PREP, SUICIDE_LAVA, DEAD_WAIT, REFILL, STUCK
    }

    /**
     * 三号模式 1 号搬运端的阶段（V6.7p3 重排：校正 → 补货 → 投珍珠 → 取货/存货 → 私信）。
     *
     * <p>V6.7p7 在最前面插入 {@link #ARRIVE_WAIT}：被拉过来后先停 2 秒等区块加载再动手
     * （用户实机反馈：以前一落地就立刻开活板门/脱离，地形还没同步，判断全乱）。
     */
    private enum TrioState {
        ARRIVE_WAIT, SETTLE, RESTOCK, THROW, LOOT, ENDER_PUT, UNLOAD, ENDER_TAKE, SEND, WAIT_TELEPORT, STUCK
    }

    // ==================== 设置 ====================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMover = settings.createGroup("搬运（双号）");
    private final SettingGroup sgPearl = settings.createGroup("拉珍珠");
    private final SettingGroup sgTrio = settings.createGroup("三号模式");
    private final SettingGroup sgEnder = settings.createGroup("末影箱");

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

    private final Setting<Double> openReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("开箱距离")
        .description("眼睛到方块**包围盒**的最大距离（与服务端 canInteractWithBlockAt 同一口径）。"
            + "反作弊严格的服建议 2.0；调大只会在更远处点击，可能被服务端静默拒绝。")
        .defaultValue(2.0)
        .min(1.0)
        .sliderMax(4.0)
        .build());

    private final Setting<Boolean> statsSetting = sgGeneral.add(new StashStatsSetting.Builder()
        .name("本次搬运")
        .description("只统计本次在线：趟数 + 潜影盒数。关模块不清零，断线/退服清零；右侧按钮可手动重置。")
        .build());

    // ---------- 末影箱（V6.7p27，双号 + 三号共用） ----------

    private final Setting<Boolean> enderEnabled = sgEnder.add(new BoolSetting.Builder()
        .name("启用末影箱中转")
        .description("背包装满后先把货存进末影箱，到存储区再取出来 —— 单趟多搬 27 个潜影盒。"
            + "末影箱需预先放在待取货箱区和存储区旁边：分区坐标填了就用坐标那台（会校验方块），"
            + "留空则自动找最近的。默认开。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> enderSearchRadius = sgEnder.add(new IntSetting.Builder()
        .name("末影箱搜索半径")
        .description("自动扫描时在玩家周围多少格内找末影箱（水平/垂直同一半径）。"
            + "下面两条分区坐标填了就以坐标为准，这项不参与。")
        .defaultValue(6)
        .min(2)
        .sliderMax(12)
        .visible(enderEnabled::get)
        .build());

    // 末影箱分区坐标（V6.7p30，混合模式：坐标优先 + 校验 + 自动扫描兜底）
    // ⚠️ 约定与存储箱/珍珠箱一致：0,0,0 = 未设置 = 自动扫描

    private final Setting<BlockPos> enderLootChest = sgEnder.add(new BlockPosSetting.Builder()
        .name("取货区末影箱位置")
        .description("双号模式：待取货箱区旁边那台末影箱。留空（0,0,0）= 自动扫描附近。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> enderEnabled.get() && mode.get() == Mode.MOVER)
        .build());

    private final Setting<BlockPos> enderStoreChest = sgEnder.add(new BlockPosSetting.Builder()
        .name("存储区末影箱位置")
        .description("双号模式：存储区旁边那台末影箱。留空（0,0,0）= 自动扫描附近。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> enderEnabled.get() && mode.get() == Mode.MOVER)
        .build());

    private final Setting<BlockPos> trioEnderLootChest = sgEnder.add(new BlockPosSetting.Builder()
        .name("待搬运箱区末影箱位置")
        .description("三号模式：待搬运箱区旁边那台末影箱。留空（0,0,0）= 自动扫描附近。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> enderEnabled.get() && mode.get() == Mode.TRIO)
        .build());

    private final Setting<BlockPos> trioEnderStoreChest = sgEnder.add(new BlockPosSetting.Builder()
        .name("存储箱区末影箱位置")
        .description("三号模式：存储箱区旁边那台末影箱。留空（0,0,0）= 自动扫描附近。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> enderEnabled.get() && mode.get() == Mode.TRIO)
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
        .defaultValue(ChestMode.SINGLE)
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

    private final Setting<Integer> dualZoneRadius = sgMover.add(new IntSetting.Builder()
        .name("存储区判定半径")
        .description("双号开模块时判断「我在待搬运箱区还是存储区」：离存储箱/珍珠箱/活板门的最近水平距离"
            + "不超过这个值就算在存储区（会先卸货再补回程票）。只在「自动搜索」模式用到——"
            + "单箱和框选有源区坐标，走双锚点取近，不看这个值。")
        .defaultValue(32)
        .min(8)
        .sliderMax(128)
        .visible(() -> mode.get() == Mode.MOVER && chestMode.get() == ChestMode.AUTO)
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

    /**
     * 自杀延时（V6.7p14：改成「确认腔室珍珠之后」的固定等待，不再是一个可关的开关）。
     *
     * <p>为什么下限锁死 600 tick：3C3U 服务端对滞留珍珠的同步有延迟，投完立刻死会让
     * 那颗珍珠消失 —— 下一趟就没有回程票，整条循环断掉。用户实机口径：必须 ≥600 tick。
     */
    private final Setting<Integer> suicideDelay = sgMover.add(new IntSetting.Builder()
        .name("自杀延时")
        .description("确认腔室里已经有自己的珍珠之后，再等多少 tick 才自杀（20 tick = 1 秒）。"
            + "⚠️ 3C3U 实测 660 tick 仍会把珍珠吞掉，默认已抬到 1200（60 秒）；"
            + "设太小珍珠会消失，设 0 = 确认完立刻死（必掉）。")
        .defaultValue(1200)
        .min(0)
        .sliderMax(2000)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<Integer> teleportWaitTimeout = sgMover.add(new IntSetting.Builder()
        .name("等待被拉超时")
        .description("发出「拉我N」后多少 tick 还没被拉过去，就原样重发一次（100 tick = 5 秒，最多重发 3 次）。")
        .defaultValue(100)
        .min(40)
        .sliderMax(400)
        .visible(() -> mode.get() == Mode.MOVER)
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
        .description("收到以该关键字开头的私信才拉珍珠（后面可跟数字，如「拉我3」；「不要拉我」不会误触发）。")
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

    private final Setting<Boolean> pullerNoDig = sgPearl.add(new BoolSetting.Builder()
        .name("赶路不挖掘")
        .description("去珍珠点/回出发点的路上禁止 Baritone 破坏方块。"
            + "拉珍珠只需要走过去点个门，不需要挖；关掉这个开关它就会为了开路一路挖下去（可能挖到基岩层）。"
            + "⚠️ 若路线本来就得挖一段，这里要关掉。")
        .defaultValue(true)
        .visible(this::isPullerRole)
        .build());

    private final Setting<List<String>> pullWhitelist = sgPearl.add(new StringListSetting.Builder()
        .name("拉珍珠白名单")
        .description("只接受这些玩家的拉珍珠请求。⚠️ 双号拉珍珠端**必须填**（留空 = 拒绝所有请求，防陌生人乱拉）；"
            + "三号模式另由「1 号搬运端」锁定，白名单留空不影响。")
        .defaultValue(new ArrayList<>())
        .visible(this::isPullerRole)
        .build());

    private final Setting<Integer> pullCooldown = sgPearl.add(new IntSetting.Builder()
        .name("拉珍珠冷却")
        .description("拉完一次后至少等待多少 tick 才接受下一次请求（40 tick = 2 秒）。")
        .defaultValue(40)
        .min(0)
        .sliderMax(400)
        .visible(this::isPullerRole)
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

    private final Setting<BlockPos> sourcePearlChest = sgTrio.add(new BlockPosSetting.Builder()
        .name("待搬运箱区珍珠箱")
        .description("2 号点这边的珍珠箱。留空（0,0,0）就用下面的「通用珍珠箱」。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<BlockPos> storePearlChest = sgTrio.add(new BlockPosSetting.Builder()
        .name("存储箱区珍珠箱")
        .description("3 号点这边的珍珠箱。留空（0,0,0）就用下面的「通用珍珠箱」。")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.TRIO && isMoverRole())
        .build());

    private final Setting<BlockPos> trioPearlChest = sgTrio.add(new BlockPosSetting.Builder()
        // ⚠️ 名字不要改：Meteor 用设置名当配置键，改名会让老配置里已存的坐标读不出来（V6.7p8 刻意保留原名）
        .name("珍珠箱位置")
        .description("通用的珍珠箱。只在一条路线上放了一个箱子就填这里；上面两个「分区珍珠箱」填了则以分区为准。")
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

    private final Setting<Integer> startMaxDistance = sgTrio.add(new IntSetting.Builder()
        .name("启动最远距离")
        .description("开模块时离最近的区超过这个距离就报警暂停（近的会自己走过去，远的怕走丢/走进危险区）。")
        .defaultValue(64)
        .min(16)
        .max(512)
        .sliderMax(256)
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

    // ===== V6.7p20：拉珍珠端的到达判定（与寻路目标同一套尺度）=====
    /** 水平离珍珠点多近算「到了」（格）。 */
    private static final double PEARL_NEAR_HORIZONTAL = 2.5;
    /** 高度差多大以内算「到了」（格）。 */
    private static final double PEARL_NEAR_DY = 2.5;
    /** 寻路目标给的容忍度（GoalNear range，格）。 */
    private static final int PULLER_GOAL_RANGE = 2;

    // ===== V6.7p14：双号的回程票（腔室珍珠）确认 =====
    /** 连续这么多 tick 都在腔室里检测到自己的珍珠才算确认（实体同步有延迟，看一帧不算）。 */
    private static final int PEARL_CONFIRM_TICKS = 6;
    /** 投出后等多久还没在腔室里看到珍珠 → 判定没进柱，重投（100 tick = 5 秒）。 */
    private static final int PEARL_CONFIRM_TIMEOUT = 100;
    /** 投完到「确定要自杀」之间允许重走正前方的次数（V6.7p18，用户口径：最多重走 3 次）。 */
    private static final int FRONT_ALIGN_MAX_TRIES = 3;
    /** 单次「走到正前方」的等待上限（tick）：超了算一次重走。 */
    private static final int FRONT_ALIGN_TIMEOUT = 240;

    // ===== V6.7p11：照 PearlDrop 校准的一套判定阈值与超时 =====
    /** 判定「已居中」的最大水平距离（格）。超过说明还没到边缘。 */
    private static final double TRIO_CENTER_DIST = 1.2;
    /** 判定「人在门板正对面那一格」的水平容差（格）：格子半宽 0.5，留 0.05 余量。 */
    private static final double FRONT_CELL_TOLERANCE = 0.45;
    /** 四个水平方向（告示牌扫描 / 方向换算用）。 */
    private static final Direction[] HORIZONTAL_DIRS = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    /** 告示牌扫描高度：活板门正上方 1~3 格（用户口径：这段内必定有告示牌）。 */
    private static final int SIGN_SCAN_UP = 3;
    /** 判定珍珠「在 1×1 柱内」的水平容差（格）：半宽 0.5，留 0.05 余量（V6.7p22 收紧，原 0.9）。 */
    private static final double PEARL_IN_COLUMN_XZ = 0.55;
    /** 判定「已居中」需要连续满足的 tick 数（PearlDrop centerStallTicks）。 */
    private static final int TRIO_CENTER_STALL_TICKS = 6;
    /** 单次投掷确认超时（tick）。PearlDrop throwTimeoutTicks。 */
    private static final int TRIO_THROW_TIMEOUT = 40;
    /** 投掷重试上限。 */
    private static final int TRIO_MAX_THROW_RETRIES = 3;
    /** 退回安全距离（格）。PearlDrop retreatDistance —— 原来我们用的 2.5 太远。 */
    private static final double TRIO_RETREAT_DISTANCE = 1.0;
    /** 走近柱子的超时（tick）。 */
    private static final int TRIO_APPROACH_TIMEOUT = 120;
    /** 退回超时（tick）。 */
    private static final int TRIO_RETREAT_TIMEOUT = 80;

    /**
     * 防卡参数（V6.7p9，照搬 ZenithProxy futa 插件 AntiStuck 的判定思路）。
     *
     * <p>原理：人一旦被卡住（挤在方块里/被水柱顶住），服务端会不停发位置校正包
     * （{@link PlayerPositionLookS2CPacket}），客户端每收一个就回一个确认包。
     * 所以「5 秒内收到 6 次」基本就等于卡住了——正常玩不会这么频繁。
     * 检测到之后：松键 → 停寻路 → 等 20 tick 让状态稳定 → 寻路到最近的能站人的格子。
     */
    private static final int ANTI_STUCK_TRIGGER = 6;
    private static final long ANTI_STUCK_WINDOW_MS = 5000;
    private static final int ANTI_STUCK_SETTLE_TICKS = 20;

    // ===== V6.7p21：过滤箱（有物品但不符合「只拿潜影盒」）的处理 =====
    /** 跳过期：10 秒后才允许重新打开同一个箱子，避免每 tick 重复开同一个箱子。 */
    private static final int SKIP_TICKS = 200;
    /** 「箱子被过滤」提示的最小间隔（tick）：30 秒一条。 */
    private static final int FILTERED_WARN_INTERVAL = 600;

    private long tick;
    private int cooldown;

    // 双号搬运
    private MoverState moverState = MoverState.LOOT;
    private final Set<BlockPos> blacklist = new HashSet<>();
    /**
     * 「箱里有物品、但不符合过滤条件」的箱子 → 跳过期（tick）。
     *
     * <p>V6.7p21：这类箱子不能拉黑（拉黑后整趟都不再被看），也不能每 tick 重开，
     * 所以只跳过 {@link #SKIP_TICKS}，等补货或用户改设置后还能重试。
     */
    private final LinkedHashMap<BlockPos, Long> skippedUntil = new LinkedHashMap<>();
    /** 上一次「箱子被过滤」提示的 tick（30 秒一条，别刷屏）。 */
    private long lastFilteredWarnTick;
    private final List<BlockPos> chestQueue = new ArrayList<>();
    private int queueIndex;
    private BlockPos openChest;
    /** 连续点击同一个箱子却没打开的次数（点不开就报错，不再无限沉默重试）。 */
    private int openAttempts;
    /** 当前寻路目标（寻路守卫：同一目标不重复下发，避免每 tick 重置 Baritone 路径）。 */
    private BlockPos pathTarget;
    /**
     * 当前寻路目标的类型（V6.7p23）。
     *
     * <p>同一个坐标在不同层之间切换时，目标类型也要跟着变，否则「同目标不重复下发」的守卫
     * 会把新目标吞掉。
     */
    private PathGoal pathGoal;
    /**
     * 每个箱子算好的落脚点（V6.7p25）：spot = 该站的空气格，los = 从那里能不能直视箱子。
     * 箱子换层/被拆/不再可站时重算；{@link #chestNoSpot} 记「算过，周围确实没有可站格」。
     */
    private final Map<BlockPos, ChestApproach> chestApproachCache = new HashMap<>();
    /** 四周 3 格内没有可站格的箱子（本次会话不再尝试，避免每 tick 重算 + 反复走箱子正上方）。 */
    private final Set<BlockPos> chestNoSpot = new HashSet<>();
    /**
     * 换格机制（V6.7p33）：每个箱子「试过失败的落脚点」→ 失败时刻。
     * 选格时跳过它们；{@link #SPOT_RETRY_AFTER_TICKS} 之后自动过期（地形可能变了）。
     */
    private final Map<BlockPos, LinkedHashMap<BlockPos, Long>> chestFailedSpots = new HashMap<>();
    /** 失败落脚点的重试间隔：60 秒。 */
    private static final long SPOT_RETRY_AFTER_TICKS = 1200;
    /** 上次换格的 tick（40 tick 节流：一次失败只换一次格）。 */
    private long lastSpotRotateTick;
    /** 当前正在接近的箱子 + 开始时间（走不到就报警/跳过，别无限挂着）。 */
    private BlockPos approachChest;
    private long approachStartTick;
    /** 单个箱子「走到落脚点」的等待上限（30 秒）；超时按模式决定跳过还是继续。 */
    private static final int APPROACH_TIMEOUT = 600;
    /** 选箱子时最多向后看几个，挑「落脚点有视线」的那个（V6.7p25）。 */
    private static final int LOS_LOOKAHEAD = 4;

    // ===== V6.7p27：末影箱中转 =====
    /** 本趟是否已经往末影箱存过货（一趟只存一次，避免来回倒腾）。 */
    private boolean enderPutDone;
    /** 本趟是否已经从末影箱取过货（一趟只取一次）。 */
    private boolean enderTakeDone;
    /** 本次要打开的那台末影箱（进状态时扫出来的）。 */
    private BlockPos enderTarget;
    /** 末影箱扫描缓存（附近没有时也缓存一小会儿，别每 tick 全立方体扫一遍）。 */
    private BlockPos enderChestCache;
    private long enderChestCacheTick;
    /** 进入末影箱状态的 tick（用于「打不开就放弃本趟」的超时）。 */
    private long enderStateStart;
    /** 单次末影箱操作（存/取）的等待上限：30 秒（含从 2~6 格外走过来的时间）。 */
    private static final int ENDER_STATE_TIMEOUT = 600;
    /** 末影箱状态里「关掉非末影箱界面」的容忍次数，超过就判定识别异常并降级（V6.7p29）。 */
    private static final int ENDER_CLOSE_LIMIT = 3;
    /** 本状态里已经关掉过几次「不是末影箱」的界面（防止打开/关闭死循环）。 */
    private int enderCloseCount;
    /** 是否已经打过「末影箱界面已就绪」的调试行（每趟只打一次）。 */
    private boolean enderOpenLogged;
    /** 「附近没有末影箱」的负缓存时长（tick）。 */
    private static final int ENDER_SCAN_TTL = 20;
    private long pathStartTick;
    /** 同一个精确走位目标开始的时间（用于 20 秒超时判定，不随每次重发归零）。 */
    private long pathToSince;
    private int pathRetries;
    private boolean finalTrip;
    private int pullCount;
    private int expectedN;
    private boolean ackReceived;
    private long lastSendTick;
    private int resendCount;
    private long verifyStartTick;
    private long lastPearlThrowTick;

    // ===== V6.7p14：双号的「等落地 / 到达 / 回程票确认」三件事 =====
    /** 发出「拉我」后开始等落地的 tick。 */
    private long dualTeleportWaitStart;
    /** 等落地阶段已经原样重发了几次（与 ack 阶段的重发分开计数）。 */
    private int dualTeleportResends;
    /** 落地后静默开始的 tick（等区块加载）。 */
    private long dualArriveWaitStart;
    /** 脱离气泡柱开始计时的 tick（0 = 还没开始）。 */
    private long dualSettleStart;
    /** 连续几 tick 在腔室里检测到自己的珍珠（确认回程票用）。 */
    private int pearlConfirmStreak;
    /** 回程票确认成功的那一 tick；0 = 未确认，**禁止自杀**。 */
    private long pearlConfirmedTick;
    /** 本轮确认开始计时的 tick。 */
    private long pearlConfirmStart;
    /** 本轮已经因为「没确认到」重投了几次。 */
    private int pearlConfirmRetries;
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
    /** V6.7p18：开门后「纠到正前方」已经重走了几次。 */
    private int frontAlignTries;
    /** V6.7p18：本轮纠位的开始 tick（0 = 还没开始）。 */
    private long frontWalkStart;

    // ===== V6.7p10：短按键站位（1 格级不再调寻路）=====
    private BlockPos stepTarget;
    private int stepTicks;
    private int stepStuckTicks;
    private int stepPhase;
    private double stepLastX;
    private double stepLastZ;
    private int stepFailCount;
    // 最后 1 格「潜行贴到边缘」的计数
    private int edgeTicks;
    private int edgeStuckTicks;
    /** 连续满足「走不动 + 离柱心 <1.2」的 tick 数（PearlDrop 的 centerStallTicks）。 */
    private int edgeCentered;
    private int edgePhase;
    private double edgeLastX;
    private double edgeLastZ;
    /** 投掷前记录的背包珍珠数：数量下降 = 投出去了（V6.7p11，PearlDrop 的判定方式）。 */
    private int pearlCountBefore;
    /** 补货子步骤（V6.7p12）：0=未开始 1=已拿起整堆 2=逐颗放 3=放回余量 4=确认光标空后关闭。 */
    private int restockPhase;
    /** 放回余量的尝试次数（防止死循环把所有槽位都试一遍）。 */
    private int restockPutbackTries;
    /** 走到目标格中心的判定半径（格）。 */
    private static final double STEP_ARRIVE_EPS = 0.35;
    /** 挂起的交互动作：等本 tick 的移动包（带新朝向）发完再执行。 */
    private Runnable afterMoveAction;

    /** 每个区第一次找到的安全站位（缓存后复用，避免每次传送过来都重新搜一遍）。 */
    private final java.util.Map<StashMoveLogic.Zone, BlockPos> zoneSafeSpot = new java.util.HashMap<>();
    private int restockRetries;
    private long nextPearlCheckTick;
    private StashMoveLogic.Zone trioZone = StashMoveLogic.Zone.UNKNOWN;
    private long teleportWaitStart;
    private Vec3d lastPos;

    // V6.7p7：到达后等待 + 逐步诊断
    /** 被拉过来后开始等待的 tick（ARRIVE_WAIT 截止点）。 */
    private long arriveWaitStart;
    /** 连续点活板门没打开的累计次数（换目标/打开成功即归零）。 */
    private int trapdoorClickAttempts;
    /** 「退开重来」轮数（V6.7p12，连点 6 次没开就退开换角度，最多 2 轮）。 */
    private int trapdoorRetreats;
    /** 上一次打印的「朝向三来源」摘要（只在结论变化时打印，避免每 tick 刷屏）。 */
    private String lastSideDump = "";
    /** 调试心跳：上次打印摘要的 tick。 */
    private long lastHeartbeatTick;
    /** 心跳摘要真正打印出去的 tick（内容不变时用它控制「仍在…」的间隔）。 */
    private long lastHeartbeatPrintTick;
    /** 上一次打印的状态/子步骤，用于“只在变化时打印”。 */
    private String lastHeartbeatSummary = "";

    // ===== 防卡（V6.7p9，照搬 ZenithProxy futa 插件 AntiStuck 的判定思路）=====
    /** 位置校正包洪泛检测：5 秒内 6 次 = 卡脚。 */
    private final TeleportFloodDetector teleportFlood =
        new TeleportFloodDetector(ANTI_STUCK_WINDOW_MS, ANTI_STUCK_TRIGGER);
    /** >=0 表示防卡流程正在进行（此时状态机让路）。 */
    private int antiStuckTicks = -1;
    /** 防卡冷却截止时间（毫秒），避免连续触发。 */
    private long antiStuckCooldownUntil;
    /** 本次防卡已经救过几次（用于日志/诊断）。 */
    private int antiStuckCount;
    /** 网络线程发现「疑似卡脚」时置位，由客户端线程（onTick）接手处理。 */
    private volatile boolean antiStuckPending;

    // 拉珍珠
    private boolean pullActive;
    private BlockPos pullPos;
    private String pullRequester;
    private int pullN = -1;
    private long pullStartTick;
    private int pullPhase;
    private boolean pullClickSent;
    private boolean pullTrapdoorWasOpen;
    private long pullClickStartTick;
    private int pullClickAttempts;
    private BlockPos pullStartPos;
    private long pullReturnStart;
    /** 上一次拉珍珠结束（或放弃）的 tick，用于请求冷却。 */
    private long lastPullEndTick = -100_000L;

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
        chestApproachCache.clear();
        chestNoSpot.clear();
        chestFailedSpots.clear();
        skippedUntil.clear();
        lastFilteredWarnTick = 0;
        chestQueue.clear();
        queueIndex = 0;
        openChest = null;
        openAttempts = 0;
        clearPathTarget();
        pathStartTick = 0;
        pathRetries = 0;
        finalTrip = false;
        ackReceived = false;
        resendCount = 0;
        cachedLava = null;
        stuckReason = "";
        nextAlarmTick = 0;
        forwardHeld = false;
        backoffTicks = 0;
        sneakHeld = false;
        lastPullEndTick = -100_000L;
        restockStepsLeft = 0;
        pullActive = false;
        unloadedSomething = false;
        lastProgressTick = 0;
        resetEnderTrip();
        invalidateEnderCache();

        // V6.7p14：双号新增的几个阶段状态
        dualTeleportWaitStart = 0;
        dualTeleportResends = 0;
        dualArriveWaitStart = 0;
        dualSettleStart = 0;
        pearlConfirmStreak = 0;
        pearlConfirmedTick = 0;
        pearlConfirmStart = 0;
        pearlConfirmRetries = 0;

        // V6.7p17：先判区再决定走哪一段——站在存储区开模块时不能再从 LOOT 起步
        //（自动搜索会把存储箱当待搬运箱，单箱/框选会直接寻路去源区）
        moverState = MoverState.ZONE_CHECK;
        trioState = TrioState.SETTLE;
        trioZone = StashMoveLogic.Zone.UNKNOWN;
        throwSub = 0;
        throwRetries = 0;
        throwSafeSpot = null;
        zoneSafeSpot.clear();
        restockRetries = 0;
        nextPearlCheckTick = 0;
        arriveWaitStart = 0;
        trapdoorClickAttempts = 0;
        trapdoorRetreats = 0;
        restockPhase = 0;
        restockPutbackTries = 0;
        lastHeartbeatTick = 0;
        lastHeartbeatPrintTick = 0;
        lastHeartbeatSummary = "";
        teleportFlood.clear();
        antiStuckTicks = -1;
        antiStuckCooldownUntil = 0;
        antiStuckCount = 0;
        antiStuckPending = false;
        lastPos = mc.player != null ? mc.player.getEntityPos() : null;

        if (isTrioMover()) {
            // V6.7p8：这里**不判区**。启动位置是随机的，判区 + 距离检查统一放到 tickTrioMover 的
            // 第一次 tick 里做（那里有「离两个区都太远就报警」的门槛）。原来在这里直接 initialZone()，
            // 会绕过距离检查。
            trioZone = StashMoveLogic.Zone.UNKNOWN;
            trioState = TrioState.SETTLE;
        }
    }

    @Override
    public void onDeactivate() {
        releaseMovementKeys();
        PathManagers.get().stop();
        PathManagers.get().setAllowBreak(true);
        blacklist.clear();
        chestApproachCache.clear();
        chestNoSpot.clear();
        chestFailedSpots.clear();
        chestQueue.clear();
        pullActive = false;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // 用户口径：断线/退服清零本次搬运统计（关模块不清）
        StashStats.get().reset();
        pullActive = false;
        teleportFlood.clear();
    }

    // ==================== 防卡（V6.7p9） ====================

    /**
     * 统计服务端位置校正包的频率，判断是否「卡脚」。
     *
     * <p>人卡住时服务端会不停把你拉回原位（每拉一次就是一个位置包），
     * 5 秒内 6 次基本等于卡死。正常跑图/被珍珠拉一次都不会到这个量。
     */
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        // V6.7p13：搬运端（双号/三人1号）与拉珍珠端（双号/三人2/3号）都参与防卡
        if (!isMoverRole() && !isPullerRole()) return;

        // ⚠️ 这个回调在**网络线程**：只记时间戳 + 置标志，绝不碰玩家/按键/寻路。
        // 真正的处理放到 onTick（客户端线程），见 tickAntiStuck 的调用点。
        if (teleportFlood.record(System.currentTimeMillis())) {
            antiStuckPending = true;
        }
    }

    private void startAntiStuck() {
        antiStuckTicks = 0;
        antiStuckCount++;
        releaseMovementKeys();
        PathManagers.get().stop();
        clearPathTarget();
        warning("检测到频繁传送（5 秒内 " + teleportFlood.count(System.currentTimeMillis())
            + " 次），判定卡脚，开始脱困");
        info("我卡住了！请手动检查站位");
        if (debug.get()) {
            info("[防卡] 第 " + antiStuckCount + " 次，状态=" + trioState + "，在水里="
                + (mc.player.isTouchingWater() ? "是" : "否") + "，碰撞=" + mc.player.horizontalCollision);
        }
    }

    /** 防卡流程：等 20 tick 稳住 → 找最近的安全落脚点 → 寻路过去。 */
    private void tickAntiStuck() {
        antiStuckTicks++;
        if (antiStuckTicks < ANTI_STUCK_SETTLE_TICKS) return;

        BlockPos safe = findAntiStuckSpot();
        if (safe != null) {
            info("尝试移动到附近的安全位置");
            pathTarget = safe;
            pathStartTick = tick;
            pathToSince = tick;
            pathRetries = 0;
            PathManagers.get().stop();
            PathManagers.get().moveToPrecise(safe);
        } else {
            warning("附近 5 格内找不到能站的格子，请手动处理");
        }

        antiStuckTicks = -1;
        antiStuckCooldownUntil = System.currentTimeMillis() + ANTI_STUCK_WINDOW_MS;
        teleportFlood.clear();
        antiStuckPending = false;
        cooldown = PACE_STEP;
    }

    /**
     * 找最近的安全落脚点（脚下实心、身体两格无碰撞、不在流体里）。
     *
     * <p>先在原地上下试，再逐圈向外扩到 5 格——和 ZenithProxy 的 `findSafePosition` 一个思路。
     */
    private BlockPos findAntiStuckSpot() {
        BlockPos base = mc.player.getBlockPos();
        int[] dyOrder = {0, -1, 1, -2, 2, -3, 3};
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : dyOrder) {
                        BlockPos p = base.add(dx, dy, dz);
                        if (isStandable(p)) return p;
                    }
                }
            }
        }
        return null;
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

        // V6.7p9：防卡流程优先，进行中时状态机整体让路（否则一边救一边按原逻辑走，越救越卡）
        if (antiStuckTicks >= 0) {
            tickAntiStuck();
            return;
        }
        // 网络线程只置标志，真正的判定/动作在这里（客户端线程）做
        if (antiStuckPending) {
            antiStuckPending = false;
            long now = System.currentTimeMillis();
            if (tick >= 100 && now >= antiStuckCooldownUntil) {
                startAntiStuck();
                return;
            }
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // V6.7p13：正在寻路且真的在移动 → 算进展（双号/三人通用，不再只限 trio）。
        // 否则「从很远处走去珍珠箱/柱子」会被 2 分钟看门狗误杀；Baritone 原地打转不算。
        if (PathManagers.get().isPathing() && lastPos != null
            && !StashMoveLogic.isStalled(lastPos.x, lastPos.z, mc.player.getX(), mc.player.getZ(), 0.05)) {
            progress();
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
            case ZONE_CHECK -> dualZoneCheck();
            case LOOT -> dualLoot();
            case ENDER_PUT -> dualEnderPut();
            case ENDER_TAKE -> dualEnderTake();
            case SEND -> dualSend();
            case WAIT_ACK -> dualWaitAck();
            case WAIT_TELEPORT -> dualWaitTeleport();
            case ARRIVE_WAIT -> dualArriveWait();
            case SETTLE -> dualSettle();
            case UNLOAD -> dualUnload();
            case TAKE_PEARL -> dualTakePearl();
            case THROW -> dualThrow();
            case PUT_BACK -> dualPutBack();
            case CONFIRM_PEARL -> dualConfirmPearl();
            case SUICIDE_PREP -> dualSuicidePrep();
            case SUICIDE_LAVA -> dualSuicideLava();
            case DEAD_WAIT -> dualDeadWait();
            case REFILL -> dualRefill();
            case STUCK -> tickStuck();
        }
    }

    // ==================== 双号：入口判区（V6.7p17） ====================

    /**
     * 先判「我在哪一侧」，再决定从路线的哪一段进场。
     *
     * <p>不判区的后果很直接：站在存储区开模块，状态机从 {@code LOOT} 起步 ——
     * 自动搜索会在脚下把存储箱当待搬运箱搬走，单箱 / 框选则直接寻路去源区。
     *
     * <p>判据与三号同源：
     * <ul>
     *   <li><b>单箱 / 框选</b>本来就有源区坐标 → 直接用
     *       {@link StashMoveLogic#nearerZone} 双锚点取近；</li>
     *   <li><b>自动搜索</b>没有源区锚点（语义就是「在重生点脚下搜箱子」）→
     *       退化成「离存储侧任一锚点 ≤ 判定半径」。</li>
     * </ul>
     *
     * <p>⚠️ 日志里只打距离，不打坐标（无政府服防泄露，`NoCoordinatesInLogsTest` 会盯）。
     */
    private void dualZoneCheck() {
        BlockPos store = storeChest.get();
        if (store.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置存储箱位置");
            return;
        }
        BlockPos source = dualSourceAnchor();
        StashMoveLogic.Zone zone;
        if (source != null) {
            zone = StashMoveLogic.nearerZone(mc.player.getX(), mc.player.getZ(),
                source.getX() + 0.5, source.getZ() + 0.5,
                store.getX() + 0.5, store.getZ() + 0.5);
            debugMsg("[双号][判区] 双锚点取近：离源区 " + distText(source)
                + "，离存储区 " + distText(store));
        } else {
            double d = nearestStoreAnchorDistanceXZ();
            zone = StashMoveLogic.storeZoneByDistance(d, dualZoneRadius.get());
            debugMsg("[双号][判区] 自动搜索按半径兜底：离存储侧最近 "
                + (d == Double.MAX_VALUE ? "未配置锚点" : numberText(d) + " 格")
                + "，判定半径 " + dualZoneRadius.get() + " 格");
        }
        if (zone == StashMoveLogic.Zone.STORE) {
            info("判定在存储区：先卸货、补回程票，再自杀回待搬运箱区");
            dualArriveWaitStart = tick;
            dualSettleStart = 0;
            moverState = MoverState.ARRIVE_WAIT;
            cooldown = PACE_STEP;
        } else {
            debugMsg("[双号][判区] 判定在待搬运箱区，开始取货");
            moverState = MoverState.LOOT;
            cooldown = 0;
        }
        progress();
    }

    /** 双号的源区锚点：单箱 = 目标箱子，框选 = 框中心，自动搜索 = 没有（null）。 */
    private BlockPos dualSourceAnchor() {
        switch (chestMode.get()) {
            case SINGLE -> {
                BlockPos t = targetChest.get();
                return t.equals(BlockPos.ORIGIN) ? null : t;
            }
            case RANGE -> {
                BlockPos a = pos1.get();
                BlockPos b = pos2.get();
                if (a.equals(BlockPos.ORIGIN) || b.equals(BlockPos.ORIGIN)) return null;
                return new BlockPos((a.getX() + b.getX()) / 2,
                    (a.getY() + b.getY()) / 2, (a.getZ() + b.getZ()) / 2);
            }
            default -> {
                return null;
            }
        }
    }

    /** 到存储侧三个锚点（存储箱 / 珍珠箱 / 活板门）的最近水平距离；都没配返回 {@code MAX_VALUE}。 */
    private double nearestStoreAnchorDistanceXZ() {
        double best = Double.MAX_VALUE;
        for (BlockPos p : new BlockPos[]{storeChest.get(), pearlChest.get(), trapdoor.get()}) {
            if (p == null || p.equals(BlockPos.ORIGIN)) continue;
            double d = Math.hypot(mc.player.getX() - (p.getX() + 0.5),
                mc.player.getZ() - (p.getZ() + 0.5));
            if (d < best) best = d;
        }
        return best;
    }

    private String distText(BlockPos p) {
        return numberText(Math.hypot(mc.player.getX() - (p.getX() + 0.5),
            mc.player.getZ() - (p.getZ() + 0.5))) + " 格";
    }

    private static String numberText(double d) {
        return String.format(Locale.ROOT, "%.0f", d);
    }

    private void dualLoot() {
        // 背包满优先：先试着塞进附近末影箱（V6.7p27），塞不了再去发请求
        if (inventoryFull()) {
            if (tryEnterEnderPut(() -> moverState = MoverState.ENDER_PUT)) return;
            closeScreen();
            moverState = MoverState.SEND;
            return;
        }
        if (containerOpen()) {
            StashMoveLogic.TakeOutcome outcome = takeFromContainer();
            if (outcome == StashMoveLogic.TakeOutcome.TAKEN) return;

            closeScreen();
            // V6.7p21：箱子里有东西、只是没有我们要的（例如「只拿潜影盒」遇到普通物资箱）。
            // 这种情况**不能**拉黑，否则整趟都不会再来看它；只跳过一小段时间。
            if (outcome == StashMoveLogic.TakeOutcome.NO_MATCH) {
                skipChest(openChest);
                openChest = null;
                cooldown = PACE_STEP;
                return;
            }

            // 当前箱子确实空了
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
            if (tryEnterEnderPut(() -> moverState = MoverState.ENDER_PUT)) return;
            moverState = MoverState.SEND;
            return;
        }

        BlockPos chest = nextLootChest();
        if (chest == null) {
            if (filteredCount() > 0) {
                warnFiltered();
            } else {
                warnThrottled("箱子搜索半径内没有待搬运箱，请检查重生点是否在箱子旁边");
            }
            return;
        }
        if (!goOpenChest(chest)) return;
    }

    /** 双号：把货存进附近末影箱（V6.7p27）。 */
    private void dualEnderPut() {
        if (!enderPutTick()) {
            moverState = MoverState.LOOT;
            cooldown = PACE_STEP;
            debugMsg("[末影箱] 存货结束，回取货");
        }
    }

    /** 双号：到存储区后把末影箱里的货取出来（V6.7p27）。 */
    private void dualEnderTake() {
        if (!enderTakeTick()) {
            moverState = MoverState.UNLOAD;
            cooldown = PACE_STEP;
            debugMsg("[末影箱] 取货结束，回卸货");
        }
    }

    private void dualSend() {
        if (targetPlayer.get().isBlank()) {
            enterStuck("请先填写搬运目标玩家 ID");
            return;
        }
        sendPullRequest(targetPlayer.get());
        if (waitAck.get()) {
            moverState = MoverState.WAIT_ACK;
        } else {
            startTeleportWait();
        }
    }

    /** 等确认/等落地超时后的「原样重发」：N 不递增，避免搭档按新数字再点一次门、多触一颗珍珠。 */
    private void resendPullRequest(String target) {
        if (target == null || target.isBlank() || mc.player == null) return;
        sendPrivate(target, PeerMessageLogic.buildRequest(keyword.get(), expectedN));
        debugMsg("原样重发「" + keyword.get() + expectedN + "」给 " + target);
        progress();
    }

    /** 进入「等被拉走」：先松键、停路 —— 绝不在源区就开始走向存储区的柱子。 */
    private void startTeleportWait() {
        releaseMovementKeys();
        PathManagers.get().stop();
        clearPathTarget();
        closeScreen();
        dualTeleportWaitStart = tick;
        dualTeleportResends = 0;
        moverState = MoverState.WAIT_TELEPORT;
        progress();
        debugMsg("[双号][拉我] 已发出请求，原地等被拉走（" + teleportWaitTimeout.get() + " tick 没走就重发）");
    }

    private void dualWaitAck() {
        // 搭档可能先拉人再回「收到」：先看有没有已经被拉走，别错过落点
        if (dualTeleportJumpedNow()) {
            onDualArrived();
            return;
        }
        if (ackReceived) {
            startTeleportWait();
            return;
        }
        if (tick - lastSendTick > 300) {
            if (resendCount < 3) {
                resendCount++;
                sendPullRequest(targetPlayer.get());
                debugMsg("等确认超时，第 " + resendCount + " 次重发「拉我N」");
            } else {
                enterStuck("等待搭档确认超时（已重发 3 次）");
            }
        }
    }

    /** 这一 tick 是否刚被珍珠拉走（水平突跳 > 32 格）。 */
    private boolean dualTeleportJumpedNow() {
        if (lastPos == null || mc.player == null) return false;
        Vec3d now = mc.player.getEntityPos();
        return StashMoveLogic.teleportJumped(lastPos.x, lastPos.z, now.x, now.z, 32);
    }

    /** 落地统一收尾：清队列、停路、松键，进到达静默（对齐三号的 WAIT_TELEPORT 收尾）。 */
    private void onDualArrived() {
        closeScreen();
        releaseMovementKeys();
        PathManagers.get().stop();
        clearPathTarget();
        blacklist.clear();
        chestApproachCache.clear();
        chestNoSpot.clear();
        chestFailedSpots.clear();
        skippedUntil.clear();
        chestQueue.clear();
        queueIndex = 0;
        openChest = null;
        openAttempts = 0;
        trapdoorClickAttempts = 0;
        throwSub = 0;
        throwRetries = 0;
        dualArriveWaitStart = tick;
        dualSettleStart = 0;
        moverState = MoverState.ARRIVE_WAIT;
        info("已到达存储区，先等 " + (ARRIVE_WAIT_TICKS / 20) + " 秒让区块加载");
        progress();
    }

    /**
     * 等被拉走（V6.7p14 新增，对应三号的 {@code trioWaitTeleport}）。
     *
     * <p>⚠️ 这一步<b>绝对不能走路</b>：此时人还在待搬运箱区，存储区区块没加载，
     * {@link #pearlStandSpot} 必然返回空，旧代码就会把活板门当归宿点开始跨图寻路；
     * 被珍珠拉过去之后 Baritone 那条旧路径还活着，人就会顺着它往回走 ——
     * 用户实机看到的「拉回来不储存、反而寻路回去」就是这里来的。
     */
    private void dualWaitTeleport() {
        releaseMovementKeys();
        if (PathManagers.get().isPathing()) PathManagers.get().stop();

        if (dualTeleportJumpedNow()) {
            onDualArrived();
            return;
        }

        if (tick - dualTeleportWaitStart > teleportWaitTimeout.get()) {
            if (dualTeleportResends < 3) {
                dualTeleportResends++;
                dualTeleportWaitStart = tick;
                warning("还没被拉过去（" + teleportWaitTimeout.get() + " tick 超时），第 "
                    + dualTeleportResends + " 次重发「拉我" + expectedN + "」");
                resendPullRequest(targetPlayer.get());
            } else {
                enterStuck("等待被拉走超时（已重发 3 次）：检查搭档是否在线、珍珠点是否填对、腔室里有没有你的珍珠");
            }
        }
    }

    /** 到达静默（V6.7p14，对齐三号的 ARRIVE_WAIT）：落地后什么都不碰，等区块同步完。 */
    private void dualArriveWait() {
        releaseMovementKeys();
        if (PathManagers.get().isPathing()) PathManagers.get().stop();
        long waited = tick - dualArriveWaitStart;
        if (waited < ARRIVE_WAIT_TICKS) {
            if (debug.get() && waited % 20 == 0) {
                debugMsg("[双号][到达] 等待加载 " + (waited / 20) + "/" + (ARRIVE_WAIT_TICKS / 20) + " 秒");
            }
            return;
        }
        dualSettleStart = tick;
        moverState = MoverState.SETTLE;
        cooldown = PACE_STEP;
        debugMsg("[双号][到达] 加载完成，开始脱离气泡柱");
    }

    /**
     * 脱离气泡柱（V6.7p14）。
     *
     * <p>被珍珠拉过来时人是泡在水柱里/站在活板门上的，这时候直接开存储箱会因距离和视线都不对
     * 而点不开。这里只做「离开水柱」，<b>不把人硬拽到柱子旁</b>——卸货不需要站位，
     * 只有投珍珠才需要（那一步由 {@code tickThrowSequence} 的 settlePosition 负责）。
     */
    /**
     * 脱离气泡柱（V6.7p19 与三号对齐）。
     *
     * <p>⚠️ 旧实现是双号自己写的一套：只 `pearlStandSpot` + `walkToPrecise`，**从来不去开活板门**。
     * 而实机里「搭档点完门，门是关着的」——人被拉到活板门<b>下面</b>时，气泡柱一直往上顶、
     * 头顶是关着的门，Baritone 又在水里来回作废路径，表现就是贴着门底反复浮沉、240 tick 后报警。
     *
     * <p>现在直接复用三号那套 {@link #settlePosition}：人在柱子里且门关着 → 先点开门；
     * 近距离用短按前进 + 跳（水里一直按），走不动再交给寻路。三号一直是这条链，实机没问题。
     */
    private void dualSettle() {
        BlockPos stand = trapdoor.get();
        if (stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置搬运活板门位置");
            return;
        }
        BlockPos feet = mc.player.getBlockPos();
        boolean inColumn = mc.player.isTouchingWater() || feet.equals(stand);
        if (!inColumn) {
            debugMsg("[双号][脱离] 已离开气泡柱，去卸货");
            dualSettleStart = 0;
            moverState = MoverState.UNLOAD;
            cooldown = PACE_SETTLE_DONE;
            return;
        }
        if (dualSettleStart == 0) dualSettleStart = tick;
        if (tick - dualSettleStart > 300) {
            enterStuck("被拉过来后卡在气泡柱里出不来（检查活板门能不能从里面点开）");
            return;
        }
        if (!settlePosition(stand)) return;   // 开门 / 短按走位都在里面
        debugMsg("[双号][脱离] 已离开气泡柱，去卸货");
        dualSettleStart = 0;
        moverState = MoverState.UNLOAD;
        cooldown = PACE_SETTLE_DONE;
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
        if (!goOpenChest(chestPos)) return;
    }

    private void dualThrow() {
        BlockPos stand = trapdoor.get();
        if (stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置搬运活板门位置");
            return;
        }
        // V6.7p13：双号复用和 1号同一套投掷子流程（脱离→开活板门→站正前方→贴边→投→判定→后退）。
        // V6.7p18：投完**先确认腔室**再还余量 —— 用户口径「成功之后再放回珍珠」。
        // 旧顺序（投→还→确认）在确认失败时珍珠已经还回箱子，得再跑一趟珍珠箱才能重投。
        ThrowSeq r = tickThrowSequence(stand, () -> {
            releaseSneak();
            startPearlConfirm("投掷完成");
        });
        if (r == ThrowSeq.NO_PEARL) {
            moverState = MoverState.TAKE_PEARL;
        }
    }

    private void dualPutBack() {
        if (countPearls() == 0) {
            closeScreen();
            afterPutBack();
            return;
        }
        if (containerOpen()) {
            // 珍珠箱塞满珍珠时放不回去，直接跳过（misaka 同款分支）
            if (containerIsFull()) {
                closeScreen();
                afterPutBack();
                return;
            }
            int slot = findPearlSlotInInventory();
            if (slot < 0) {
                closeScreen();
                afterPutBack();
                return;
            }
            clickSlot(slot, SlotActionType.QUICK_MOVE);
            cooldown = Math.max(1, clickDelay.get());
            return;
        }
        BlockPos chestPos = pearlChest.get();
        if (chestPos.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置珍珠箱位置");
            return;
        }
        if (!goOpenChest(chestPos)) return;
    }

    /**
     * 还完余量之后去哪（V6.7p18）。
     *
     * <p>新顺序是「投 → 确认 → 还」，所以正常走到这里 {@code pearlConfirmedTick > 0}，直接进自杀等待；
     * 只有“腔室里那颗珍珠其实是别人放的”这类极端情况才会没确认过，那就回去补确认 ——
     * 没票绝不自杀。
     */
    private void afterPutBack() {
        if (pearlConfirmedTick > 0) {
            moverState = MoverState.SUICIDE_PREP;
            cooldown = 20;
        } else {
            startPearlConfirm("余量已还，回程票未确认");
        }
    }

    /** 投掷与还珍珠都做完了 → 进入「确认回程票」（V6.7p14）。 */
    private void startPearlConfirm(String reason) {
        closeScreen();
        pearlConfirmStreak = 0;
        pearlConfirmStart = tick;
        moverState = MoverState.CONFIRM_PEARL;
        cooldown = PACE_STEP;
        debugMsg("[双号][回程票] " + reason + "，开始确认腔室里的珍珠");
    }

    /**
     * 确认回程票（V6.7p14，用户口径：和 3 号一样，确认腔室里真有珍珠才允许计时自杀）。
     *
     * <p>为什么要连续 {@link #PEARL_CONFIRM_TICKS} tick：珍珠实体是服务端同步过来的，
     * 投出去那一两 tick 客户端还看不到，看一帧就判「有」会误判。
     * 确认不到就重投（最多 3 次），绝不带着一张空票去死。
     */
    private void dualConfirmPearl() {
        BlockPos stand = trapdoor.get();
        if (stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置搬运活板门位置");
            return;
        }
        if (ownPearlInColumn(stand.down(1))) {
            pearlConfirmStreak++;
            if (StashMoveLogic.pearlConfirmed(pearlConfirmStreak, PEARL_CONFIRM_TICKS)) {
                pearlConfirmedTick = tick;
                int delay = suicideDelay.get();
                info("回程票已确认（腔室里检测到自己的珍珠），" + (delay / 20) + " 秒后自杀");
                // 确认成功之后才处理余量：还有珍珠就去还，没有就直接等自杀
                if (countPearls() > 0) {
                    moverState = MoverState.PUT_BACK;
                    cooldown = PACE_STEP;
                } else {
                    moverState = MoverState.SUICIDE_PREP;
                    cooldown = 20;
                }
            }
            return;
        }
        pearlConfirmStreak = 0;
        if (tick - pearlConfirmStart > PEARL_CONFIRM_TIMEOUT) {
            pearlConfirmRetries++;
            if (pearlConfirmRetries > 3) {
                enterStuck("投出去的珍珠一直不在腔室里（已重投 " + (pearlConfirmRetries - 1)
                    + " 次），请手动检查活板门/水柱结构");
                return;
            }
            warning("腔室里没检测到自己的珍珠，先补一颗再重投（第 " + pearlConfirmRetries + " 次）");
            // 用户口径：检测到腔室没珍珠 → 先取珍珠，再投放；投不进就再取再投。
            // 背包里还有珍珠时 TAKE_PEARL 会自动跳过开箱、直接进 THROW。
            throwSub = 0;
            throwRetries = 0;
            moverState = MoverState.TAKE_PEARL;
            cooldown = PACE_STEP;
        }
    }

    private void dualUnload() {
        BlockPos store = storeChest.get();
        if (store.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置存储箱位置");
            return;
        }
        if (!containerOpen()) {
            if (!hasItemsToUnload()) {
                finishDualUnload();
                return;
            }
            // V6.7p25：和取货/珍珠箱统一走 goOpenChest（走到箱子旁落脚点就停，不上箱子）。
            // 之前这里直连 openChestAt —— 它只在「已经够得着」时点击，不会走过去，
            // 被拉到存储区后只要离箱子 >3.5 格就会原地空转（双号专属漏洞）。
            if (!goOpenChest(store)) return;
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
            // ⚠️ V6.7p31 修复：这是双号卸货的主出口（最后一格 QUICK_MOVE 后界面还开着，
            // 下一 tick 就会走到这里）。p27 只给「界面没开」的出口接了末影箱取货，
            // 这个出口直接 onUnloadDone() → 跳过取货 → 直接进投珍珠，还打出
            // 「附近没找到末影箱？」的误导报警。现在统一走 finishDualUnload()。
            finishDualUnload();
            return;
        }
        unloadedSomething = true;
        clickSlot(slot, SlotActionType.QUICK_MOVE);
        cooldown = Math.max(1, clickDelay.get());
        progress();
    }

    /**
     * 双号卸货收尾（V6.7p31）：dualUnload 的**所有**「卸完了」出口都走这里。
     *
     * <p>顺序固定：先问末影箱要不要取货（本趟存过且还没取）→ 取完回 UNLOAD 再卸一轮 →
     * 全部落袋才真正收尾进投珍珠。收尾只有一个实现，杜绝「改一处漏一处」。
     */
    private void finishDualUnload() {
        if (tryEnterEnderTake(() -> moverState = MoverState.ENDER_TAKE)) return;
        onUnloadDone();
    }

    private void onUnloadDone() {
        if (unloadedSomething) StashStats.get().addTrip();
        unloadedSomething = false;
        if (enderPutDone && !enderTakeDone) {
            warning("本趟末影箱里的货没取出来（存储区附近没找到末影箱？可填「存储区末影箱位置」坐标指定），会留到下一趟");
        }
        resetEnderTrip();
        invalidateEnderCache();
        if (finalTrip) {
            info("最后一趟运输完成（" + StashStats.get().displayText() + "），关闭模块");
            toggle();
            return;
        }
        // V6.7p25：每趟卸完都把「趟数 + 盒数」报一次，方便实机核对统计
        info("本趟卸货完成：本次共 " + StashStats.get().displayText());
        // V6.7p14：卸完货才去投「回程票」——货先落袋，投掷链路再出问题也不会把货背死。
        pearlConfirmedTick = 0;
        pearlConfirmStreak = 0;
        pearlConfirmRetries = 0;
        throwSub = 0;
        throwRetries = 0;
        moverState = MoverState.TAKE_PEARL;
        cooldown = PACE_SETTLE_DONE;
        debugMsg("[双号][卸货] 完成，去珍珠箱取珍珠充回程票");
    }

    private void dualSuicidePrep() {
        // V6.7p14：**没确认回程票就不许自杀**。确认不到时退回重投，不拿号去赌。
        if (pearlConfirmedTick <= 0) {
            warning("还没确认腔室里有自己的珍珠（回程票没生效），先不自杀，回去重投");
            pearlConfirmStart = tick;
            pearlConfirmStreak = 0;
            moverState = MoverState.CONFIRM_PEARL;
            return;
        }
        // 3C3U 的硬要求：确认之后还要等 ≥600 tick，否则那颗珍珠会被服务器吞掉。
        int delay = suicideDelay.get();
        if (!StashMoveLogic.canSuicide(tick, pearlConfirmedTick, delay)) {
            long waited = tick - pearlConfirmedTick;
            if (debug.get() && waited % 100 == 0) {
                info("[双号][自杀] 回程票确认后等待 " + (waited / 20) + "/" + (delay / 20) + " 秒");
            }
            return;
        }
        closeScreen();
        if (suicideMode.get() == SuicideMode.COMMAND) {
            runSuicideCommand();
            moverState = MoverState.DEAD_WAIT;
            cooldown = 40;
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
                cooldown = 40;
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
            // V6.7p14：重生后多等 2 秒——重生点的区块/箱子还没同步就开始搜箱会白跑一趟
            pearlConfirmedTick = 0;
            pearlConfirmStreak = 0;
            pearlConfirmRetries = 0;
            lastPearlThrowTick = 0;
            clearPathTarget();
            moverState = MoverState.LOOT;
            cooldown = 40;
            debugMsg("[双号][重生] 已重生，等区块加载后继续取货");
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
            if (!goOpenChest(chestPos)) return;
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
            // V6.7p8：启动位置是随机的（可能在 2 号点、3 号点、珍珠箱旁，也可能在两区之外），
            // 先看「离最近的区有多远」：近的自己走过去，远的报警等人，别横跨地图乱跑。
            double near = nearestZoneDistanceXZ();
            if (near == Double.MAX_VALUE) {
                enterStuck("请先设置「待搬运箱位置」与「存储箱位置」");
                return;
            }
            int limit = startMaxDistance.get();
            if (near > limit) {
                enterStuck("离两个区都太远（最近 " + Math.round(near) + " 格，上限 " + limit
                    + " 格），请先走到 2 号点或 3 号点再开模块");
                return;
            }
            trioZone = initialZone();
            if (trioZone == StashMoveLogic.Zone.UNKNOWN) {
                enterStuck("请先设置「待搬运箱位置」与「存储箱位置」");
                return;
            }
            if (debug.get()) {
                info("[启动] 判定在" + zoneName() + "（离最近区锚点 " + Math.round(near) + " 格），本区珍珠箱"
                    + (zonePearlChest().equals(BlockPos.ORIGIN) ? "未配置" : "已配置"));
            }
            // 直接赋值：trioState 本来就是 SETTLE，走 setTrioState 会被「状态没变」挡掉而不打印
            trioState = TrioState.SETTLE;
            lastProgressTick = tick;
            cooldown = PACE_STEP;
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
            case ENDER_PUT -> trioEnderPut();
            case UNLOAD -> trioUnload();
            case ENDER_TAKE -> trioEnderTake();
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
            if (tryEnterEnderPut(() -> setTrioState(TrioState.ENDER_PUT, "背包满，先存末影箱"))) return;
            closeScreen();
            debugMsg("[取货] 背包已满，结束取货");
            setTrioState(TrioState.SEND, "背包已满，取货结束");
            return;
        }
        if (containerOpen()) {
            StashMoveLogic.TakeOutcome outcome = takeFromContainer();
            if (outcome == StashMoveLogic.TakeOutcome.TAKEN) return;

            closeScreen();
            // V6.7p21：箱内有物品但都不符合过滤条件 —— 不拉黑、不判「搬空」，跳过后找下一个
            if (outcome == StashMoveLogic.TakeOutcome.NO_MATCH) {
                skipChest(openChest);
                openChest = null;
                trapdoorClickAttempts = 0;
                cooldown = PACE_STEP;
                return;
            }

            debugMsg("[取货] 这个箱子取完了，拉黑并找下一个");
            blacklistChest(openChest);
            openChest = null;
            trapdoorClickAttempts = 0;
            cooldown = PACE_STEP;
            return;
        }
        BlockPos chest = nextLootChest();
        if (chest == null) {
            // V6.7p21：有箱子、只是都被「只拿潜影盒」挡掉时，不能报「已搬空」——
            // 那会让用户以为没货。留在取货状态等补货/改设置，并定期说明原因。
            if (filteredCount() > 0) {
                warnFiltered();
                progress();
                return;
            }
            enterStuck("待搬运箱区已搬空（没有可搬的箱子了）");
            return;
        }
        if (!goOpenChest(chest)) return;
    }

    private void trioUnload() {
        BlockPos store = trioStoreChest.get();
        if (store.equals(BlockPos.ORIGIN)) {
            enterStuck("三号模式请先设置存储箱位置");
            return;
        }
        if (!containerOpen()) {
            if (!hasItemsToUnload()) {
                finishTrioUnload();
                return;
            }
            if (!(mc.world.getBlockState(store).getBlock() instanceof ChestBlock)) {
                enterStuck("存储箱不存在");
                return;
            }
            if (!goOpenChest(store)) return;
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
            finishTrioUnload();
            return;
        }
        unloadedSomething = true;
        clickSlot(slot, SlotActionType.QUICK_MOVE);
        cooldown = Math.max(1, clickDelay.get());
        progress();
    }

    /** 三号：把货存进附近末影箱（V6.7p27）。 */
    private void trioEnderPut() {
        if (!enderPutTick()) {
            setTrioState(TrioState.LOOT, "末影箱存货完成，继续取货");
            cooldown = PACE_STEP;
        }
    }

    /** 三号：到存储区后把末影箱里的货取出来（V6.7p27）。 */
    private void trioEnderTake() {
        if (!enderTakeTick()) {
            setTrioState(TrioState.UNLOAD, "末影箱取货完成，继续卸货");
            cooldown = PACE_STEP;
        }
    }

    /** 三号：卸货真正结束（卸空 + 末影箱也取完）→ 计趟数、清标记、回发信。 */
    /**
     * 三号卸货收尾（V6.7p31 改成统一入口）：trioUnload 的**所有**「卸完了」出口都走这里。
     *
     * <p>顺序与 {@link #finishDualUnload()} 同构：先问末影箱要不要取货 →
     * 取完回 UNLOAD 再卸一轮 → 全部落袋才真正收尾回发信。
     */
    private void finishTrioUnload() {
        if (tryEnterEnderTake(() -> setTrioState(TrioState.ENDER_TAKE, "存货完成，去末影箱取货"))) return;
        if (unloadedSomething) StashStats.get().addTrip();
        unloadedSomething = false;
        info("本趟卸货完成：本次共 " + StashStats.get().displayText());
        if (enderPutDone && !enderTakeDone) {
            warning("本趟末影箱里的货没取出来（存储区附近没找到末影箱？可填「存储箱区末影箱位置」坐标指定），会留到下一趟");
        }
        resetEnderTrip();
        invalidateEnderCache();
        setTrioState(TrioState.SEND, "存货完成");
    }

    private void trioRestock() {
        BlockPos chestPos = zonePearlChest();
        if (chestPos == null || chestPos.equals(BlockPos.ORIGIN)) {
            enterStuck("请设置" + zoneName() + "的珍珠箱位置（或「通用珍珠箱」）");
            return;
        }
        int have = countPearls();
        int target = Math.max(restockTarget.get(), restockThreshold.get());
        int need = StashPearlLogic.missingToTarget(have, restockThreshold.get(), target);
        if (need == 0 && restockPhase == 0) {
            debugMsg("[补珍珠] 珍珠 " + have + " 颗已够（阈值 " + restockThreshold.get() + "），不用补");
            setTrioState(TrioState.THROW, "珍珠数量已够");
            cooldown = PACE_RESTOCK_DONE;
            return;
        }
        // V6.7p4：取消 64 格距离闸——珍珠不够就寻路过去补（走不过去由看门狗报警）
        double distSq = mc.player.getBlockPos().getSquaredDistance(chestPos);
        if (!containerOpen()) {
            if (restockPhase != 0) {
                warning("补货途中界面被关掉了，重置补货步骤（若少了珍珠请检查）");
                restockPhase = 0;
                restockPutbackTries = 0;
            }
            // 开新容器前先关掉可能残留的界面，避免读到别的容器
            if (mc.currentScreen != null) {
                closeScreen();
                cooldown = 5;
                return;
            }
            // V6.7p25：和取货同一套靠近逻辑（能开就开、要走走到能开、目标是箱子旁落脚点）
            if (!goOpenChest(chestPos)) {
                if (debug.get() && tick % 40 == 0) {
                    info("[补珍珠] 走近珍珠箱（" + String.format(Locale.ROOT, "%.0f", Math.sqrt(distSq)) + " 格外）");
                }
                return;
            }
            return;
        }

        // ===== V6.7p12：拆成显式子步骤，**光标不空绝不关界面** =====
        // 原来「放回余量」和「关界面」挤在同一个 tick：服务端可能先处理关界面，
        // 光标上那堆珍珠就掉地上了。更坑的是——如果箱里那堆刚好等于需要的数量，
        // 光标本来就是空的，最后那次「放回」点击会把整堆**重新拿起来**，然后关界面直接丢。
        switch (restockPhase) {
            case 0 -> {
                restockStepsLeft = need;
                restockChestSlot = findPearlSlotInContainer();
                restockInvSlot = findPearlSlotInInventory();
                debugMsg("[补珍珠] 开始补货：差 " + need + " 颗，箱内槽位=" + restockChestSlot
                    + " 背包槽位=" + restockInvSlot);
                if (restockChestSlot < 0) {
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
                clickSlot(restockChestSlot, SlotActionType.PICKUP);   // 拿起整堆（光标上）
                restockPhase = 1;
                restockPutbackTries = 0;
                cooldown = Math.max(1, clickDelay.get());
                progress();
            }
            case 1 -> {
                // 逐颗右键放到背包珍珠堆；光标空了（箱里那堆不够）也算这一步结束
                if (restockStepsLeft > 0 && cursorPearlCount() > 0) {
                    clickSlot(restockInvSlot, SlotActionType.PICKUP, 1);
                    restockStepsLeft--;
                    cooldown = Math.max(1, clickDelay.get());
                    progress();
                    return;
                }
                restockPhase = 2;
                cooldown = 2;
            }
            case 2 -> {
                if (cursorEmpty()) {
                    restockPhase = 3;
                    cooldown = 2;
                    return;
                }
                // 余量放回：优先原槽（空着或还是珍珠），否则找任意空容器槽
                int putback = putbackSlot();
                if (putback >= 0) clickSlot(putback, SlotActionType.PICKUP);
                restockPutbackTries++;
                cooldown = Math.max(1, clickDelay.get());
                progress();
                if (restockPutbackTries > 4) {
                    warning("珍珠放不回箱子，改塞进背包空格");
                    int inv = findEmptyInventorySlot();
                    if (inv >= 0) clickSlot(inv, SlotActionType.PICKUP);
                }
            }
            case 3 -> {
                // 光标必须是空的才允许关界面（否则那堆珍珠会掉地上）
                if (!cursorEmpty()) {
                    restockPhase = 2;
                    cooldown = 2;
                    return;
                }
                closeScreen();
                restockPhase = 0;
                restockPutbackTries = 0;
                debugMsg("[补珍珠] 补货完成，现在有 " + countPearls() + " 颗");
                setTrioState(TrioState.THROW, "补货完成");
                cooldown = PACE_RESTOCK_DONE;
            }
        }
    }

    /** 当前所在区的中文名（提示消息里不要出现 SOURCE / STORE 这种英文枚举名）。 */
    private String zoneName() {
        return trioZone == StashMoveLogic.Zone.SOURCE ? "待搬运箱区"
            : trioZone == StashMoveLogic.Zone.STORE ? "存储箱区" : "未知区域";
    }

    /**
     * 本区该用的珍珠箱（V6.7p8）。
     *
     * <p>优先用本区专用坐标，没填就回退「通用珍珠箱」（兼容只放了一个箱子的老配置）。
     * 这样补珍珠永远在本区内完成，不会出现「人在 2 号点、珍珠箱在 3 号点」跨区补货的死结。
     *
     * @return 珍珠箱坐标；两处都没填时返回 {@link BlockPos#ORIGIN}
     */
    private BlockPos zonePearlChest() {
        BlockPos own = trioZone == StashMoveLogic.Zone.SOURCE ? sourcePearlChest.get() : storePearlChest.get();
        BlockPos common = trioPearlChest.get();
        boolean ownSet = own != null && !own.equals(BlockPos.ORIGIN);
        boolean commonSet = common != null && !common.equals(BlockPos.ORIGIN);
        return switch (StashMoveLogic.pickPearlChestSlot(ownSet, commonSet)) {
            case StashMoveLogic.PEARL_CHEST_ZONE -> own;
            case StashMoveLogic.PEARL_CHEST_COMMON -> common;
            default -> BlockPos.ORIGIN;
        };
    }

    /** 当前所在区的活板门坐标。 */
    private BlockPos trioTrapdoor() {
        return trioZone == StashMoveLogic.Zone.SOURCE ? sourceTrapdoor.get() : storeTrapdoor.get();
    }

    // ==================== 真实转头（V6.7p10） ====================
    //
    // 参考实现（RustElytraClient 的 SupplyTask.lookAt）：
    //     player.setYaw(yaw); player.setPitch(pitch);   // 直接改真实朝向，不恢复
    //     ... delay(3) ...                              // 转完等几 tick 再交互
    //
    // Meteor 的 Rotations.rotate 是**静默转头**：发包前改朝向、发完立刻恢复原值，
    // 所以画面上只有一帧闪动，看起来跟没转一样（用户反馈「不是真的转头」）。
    // 这里改成：TickEvent.Pre 里真的把 yaw/pitch 设过去 → 本 tick 的移动包自然带上它 →
    // 移动包发完（SendMovementPacketsEvent.Post）再发交互包，顺序 = 朝向 → 交互。

    /** 真实转头：直接改玩家朝向并保持（不会自动恢复）。 */
    private void turnTo(float yaw, float pitch) {
        if (mc.player == null) return;
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    /** 真实转头看向某个坐标。 */
    private void lookAt(Vec3d target) {
        if (mc.player == null) return;
        float[] yp = FacingLogic.yawPitchTo(target.x, target.y, target.z,
            mc.player.getX(), mc.player.getEyePos().y, mc.player.getZ());
        turnTo(yp[0], yp[1]);
    }

    /** 朝目标点的 pitch（度，向下为负），yaw 保持不变。 */
    private float pitchTo(Vec3d target) {
        return FacingLogic.yawPitchTo(target.x, target.y, target.z,
            mc.player.getX(), mc.player.getEyePos().y, mc.player.getZ())[1];
    }

    /**
     * 柱子「腔底」中心（V6.7p11，照 PearlDrop 的 `pitchToSoulSand`）。
     *
     * <p>从水面那格往下走，直到第一格不是水/气泡柱的方块（通常就是灵魂沙），返回它的中心。
     * 瞄腔底比瞄最上面那格水更垂直，不容易被柱壁/门框吃掉。找不到腔底就退回水面格中心。
     */
    private Vec3d columnAimPoint(BlockPos water) {
        if (mc.world == null) return Vec3d.ofCenter(water);
        BlockPos p = water;
        for (int i = 0; i < 64; i++) {
            var st = mc.world.getBlockState(p);
            boolean waterLike = !st.getFluidState().isEmpty() || st.isOf(Blocks.BUBBLE_COLUMN);
            if (!waterLike) break;
            p = p.down();
        }
        return Vec3d.ofCenter(p);
    }

    /** 真实转头看向方块中心，并在本 tick 移动包发出后点击它（顺序：朝向 → 交互）。 */
    private void realClick(BlockPos pos) {
        if (pos == null) return;
        lookAt(Vec3d.ofCenter(pos));
        afterMoveAction = () -> {
            if (mc.player == null || mc.interactionManager == null) return;
            // V6.7p15：不要依赖 mc.crosshairTarget —— 它由渲染线程按上一帧朝向更新，
            // 同一 tick 刚 lookAt 后通常还没指向目标，会落到手搓包分支（边界命中点 + 无预测）。
            // 直接用玩家当前朝向做一次客户端 raycast，拿到真实命中点和面，再走 interactBlock。
            //
            // V6.7p16：**双箱另一半也算命中**。扫描时双箱只记其中半块，人站哪边不由我们决定，
            // 射线打到搭档那半块时旧写法一律判“没对上”→ 每次点击都掉进手搓包兜底（无预测）；
            // 而两半块点开的是同一个箱子，认它没有副作用。
            BlockPos partner = chestPartner(pos);
            if (mc.player.raycast(openReach.get() + 1.5, 1.0F, false) instanceof BlockHitResult hit
                && isSameChest(hit.getBlockPos(), pos, partner)) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                return;
            }
            // 兜底：准星目标如果已经同步到目标，也走真实交互
            if (mc.crosshairTarget instanceof BlockHitResult hit
                && isSameChest(hit.getBlockPos(), pos, partner)) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                return;
            }
            // 目标被挡住/射线没命中 → 手搓包兜底（命中点已向方块内收 0.001 格）
            BlockClicker.clickBlockNoRotate(pos, Hand.MAIN_HAND);
        };
    }

    /** 命中的方块是不是「目标本身或它的双箱搭档」。 */
    private static boolean isSameChest(BlockPos hit, BlockPos target, BlockPos partner) {
        return hit != null && (hit.equals(target) || (partner != null && hit.equals(partner)));
    }

    /**
     * 双箱的另一半；单箱 / 非箱子返回 null（V6.7p16）。
     *
     * <p>用途见 {@link #realClick}：扫描时双箱只记半块，射线打到另一半时也要算命中。
     * 偏移算法与 {@link #blacklistChest} 同源（{@code StashMoveLogic.chestOtherHalfOffset}，
     * 与原版 ChestBlock 字节码一致）。
     */
    private BlockPos chestPartner(BlockPos pos) {
        if (pos == null || mc.world == null) return null;
        var state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) return null;
        Direction facing = state.get(ChestBlock.FACING);
        int[] off = StashMoveLogic.chestOtherHalfOffset(type == ChestType.LEFT,
            facing.getOffsetX(), facing.getOffsetZ());
        return pos.add(off[0], 0, off[1]);
    }

    /**
     * 这个方块是不是「自家仓库」的箱子（存储箱 / 珍珠箱，含双箱搭档另一半）。
     *
     * <p>扫出来的待搬运队列里必须把它们剔掉：判区万一失误（基地太大、人站在判定半径外），
     * 至少不会把自家仓库当待搬运箱搬走。
     *
     * <p>V6.7p21：双号 + 三号的全部锚点都进名单。旧实现只比双号的两个坐标，
     * 三号模式会把本区珍珠箱 / 三号存储箱当物资箱搬空，补货时直接报「珍珠箱里没有珍珠」。
     */
    private boolean isStoreSideChest(BlockPos pos) {
        if (pos == null) return false;
        return StashMoveLogic.isProtectedChest(pos.getX(), pos.getY(), pos.getZ(), protectedChestAnchors());
    }

    /** 收集所有「自家仓库」坐标（双号 + 三号，含双箱搭档另一半）。 */
    private List<int[]> protectedChestAnchors() {
        List<int[]> out = new ArrayList<>(12);
        addProtectedAnchor(out, storeChest.get());
        addProtectedAnchor(out, pearlChest.get());
        addProtectedAnchor(out, trioStoreChest.get());
        addProtectedAnchor(out, trioPearlChest.get());
        addProtectedAnchor(out, sourcePearlChest.get());
        addProtectedAnchor(out, storePearlChest.get());
        return out;
    }

    private void addProtectedAnchor(List<int[]> out, BlockPos anchor) {
        if (anchor == null || anchor.equals(BlockPos.ORIGIN)) return;
        out.add(new int[]{anchor.getX(), anchor.getY(), anchor.getZ()});
        BlockPos partner = chestPartner(anchor);
        if (partner != null) out.add(new int[]{partner.getX(), partner.getY(), partner.getZ()});
    }

    /** 本 tick 的移动包（带朝向）发完之后再执行挂起的交互动作。 */
    @EventHandler
    private void onMovementPost(SendMovementPacketsEvent.Post event) {
        if (afterMoveAction == null) return;
        Runnable action = afterMoveAction;
        afterMoveAction = null;
        action.run();
    }

    /**
     * 每 tick 把视角转向本区气泡柱（用户口径「全程都要转头」）。
     *
     * <p>V6.7p10 起改成**真实转头**（见上面的说明）；坑：`throwSub == 4`（正在投掷）那一 tick 要跳过，
     * 否则这里会把刚瞄准好的投掷视角拽回柱心。
     */
    private void faceColumn() {
        faceColumnAt(trioTrapdoor());
    }

    /** 朝某根柱子的气泡柱心真实转头（V6.7p13 参数化，供共享投掷子流程用）。 */
    private void faceColumnAt(BlockPos td) {
        if (mc.player == null) return;
        if (td == null || td.equals(BlockPos.ORIGIN)) return;
        lookAt(Vec3d.ofCenter(td.down(1)));
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
        // V6.7p8：这里只处理「人就在柱子附近」的情况，不把远处的人硬拽过来
        //（启动时可能站在珍珠箱/存储箱旁，先走去柱子毫无意义；投掷阶段才需要站位）。
        Vec3d t = Vec3d.ofCenter(stand);
        double dist = Math.hypot(t.x - mc.player.getX(), t.z - mc.player.getZ());
        if (dist > 8.0) {
            setTrioState(TrioState.RESTOCK, "离柱子 " + Math.round(dist) + " 格，先去补珍珠");
            cooldown = PACE_SETTLE_DONE;
            return;
        }
        // V6.7p10：1~8 格这段用短按键走到「柱子旁那一格中心」，不调寻路
        if (settlePosition(stand)) {
            debugMsg("[脱离] 已站到柱子旁");
            setTrioState(TrioState.RESTOCK, "已站到柱子旁，本区珍珠箱"
                + (zonePearlChest().equals(BlockPos.ORIGIN) ? "未配置" : "已配置"));
            cooldown = PACE_SETTLE_DONE;
        }
    }

    // ==================== 站位（V6.7p10：短按键，1 格级不再调寻路） ====================

    /**
     * 活板门上方 1~3 格内找告示牌，返回**牌面朝向**（= 人该站的那一侧，V6.7p24）。
     *
     * <p>用户场地铁律：活板门正上方 1~3 格内必定有告示牌，人站在告示牌正对面（也就是
     * 牌面所指的那一侧）投掷。告示牌是**门关着时也能读**的锚点 —— 旧实现只看「开启后的
     * 门板碰撞箱」，门一关就什么都读不出，只能退回环形搜索，于是站到南/北侧去投。
     *
     * <p>四种告示牌（立牌 / 墙牌 / 挂牌 / 墙挂牌）都由
     * {@link AbstractSignBlock#getRotationDegrees} 给出「文字正面朝哪」的角度，
     * 直接 {@link Direction#fromHorizontalDegrees} 即可，不用自己按 ROTATION 算。
     *
     * <p>扫描顺序：先把「正上方」1~3 格扫完（立牌/挂牌/水下的墙牌都可能正好在这一列），
     * 再扫四个水平邻格（贴旁边墙上的墙牌会偏出 1 格）。
     *
     * @return 牌面朝向；1~3 格内没有告示牌时返回 null
     */
    private Direction signThrowSide(BlockPos trapdoor) {
        if (mc.world == null) return null;
        // 第一遍只扫「正上方」：立牌/挂牌/水下的墙牌都可能正好在这一列
        for (int dy = 1; dy <= SIGN_SCAN_UP; dy++) {
            Direction face = signFaceAt(trapdoor.up(dy));
            if (face != null) return face;
        }
        // 第二遍才扫四个邻格：贴在旁边墙上的墙牌会偏出 1 格
        for (int dy = 1; dy <= SIGN_SCAN_UP; dy++) {
            BlockPos base = trapdoor.up(dy);
            for (Direction h : HORIZONTAL_DIRS) {
                Direction face = signFaceAt(base.offset(h));
                if (face != null) return face;
            }
        }
        return null;
    }

    /** 该格是告示牌就返回牌面朝向，否则 null。 */
    private Direction signFaceAt(BlockPos p) {
        var st = mc.world.getBlockState(p);
        if (!(st.getBlock() instanceof AbstractSignBlock sign)) return null;
        Direction face = Direction.fromHorizontalDegrees(sign.getRotationDegrees(st));
        return face.getAxis().isHorizontal() ? face : null;
    }

    /**
     * 投掷/贴边必须站的那一侧（V6.7p24）。
     *
     * <p>三个来源，优先级从高到低：
     * <ol>
     *   <li><b>告示牌牌面</b>：用户场地的固定标志，门关着也能读；</li>
     *   <li><b>活板门 FACING</b>：放置朝向。原版门板恒在 FACING 的对侧（字节码里
     *       {@code shapeByDirection} 的基准形状就贴在 FACING 的对边），所以站 FACING 侧
     *       就是站门板对面；门关着也能读；</li>
     *   <li><b>开启后的门板</b>：p22 的几何口径，取其对面。</li>
     * </ol>
     *
     * <p>三者理论上指向同一侧；真出现告示牌贴反/结构异常时，
     * {@link StashMoveLogic#pickThrowSide} 会用「门板侧不能站人」做物理否决。
     *
     * @return 该站的一侧；三个来源都读不出时返回 null（投掷阶段会拒绝侧站）
     */
    private Direction throwFrontSide(BlockPos trapdoor) {
        if (mc.world == null || trapdoor == null) return null;
        var st = mc.world.getBlockState(trapdoor);
        if (!(st.getBlock() instanceof TrapdoorBlock)) return null;

        BoxSide sign = boxSideOf(signThrowSide(trapdoor));
        BoxSide facing = boxSideOf(st.get(TrapdoorBlock.FACING));
        BoxSide panel = boxSideOf(openLidSide(trapdoor));
        BoxSide picked = StashMoveLogic.pickThrowSide(sign, facing, panel);

        String dump = "告示牌=" + boxSideName(sign) + " 门FACING=" + boxSideName(facing)
            + " 门板=" + boxSideName(panel);
        if (!dump.equals(lastSideDump)) {
            lastSideDump = dump;
            if (picked == BoxSide.UNKNOWN) {
                debugMsg("[朝向] " + dump + " → 读不出投掷侧（门开后会重试）");
            } else {
                debugMsg("[朝向] " + dump + " → 站 " + boxSideName(picked) + " 侧");
            }
        }
        return directionOf(picked);
    }

    private static BoxSide boxSideOf(Direction d) {
        if (d == null) return BoxSide.UNKNOWN;
        return switch (d) {
            case EAST -> BoxSide.EAST;
            case WEST -> BoxSide.WEST;
            case SOUTH -> BoxSide.SOUTH;
            case NORTH -> BoxSide.NORTH;
            default -> BoxSide.UNKNOWN;
        };
    }

    private static Direction directionOf(BoxSide s) {
        if (s == null) return null;
        return switch (s) {
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            case SOUTH -> Direction.SOUTH;
            case NORTH -> Direction.NORTH;
            default -> null;
        };
    }

    private static String boxSideName(BoxSide s) {
        return s == null ? "?" : s.name();
    }

    /**
     * 开着的活板门「门板占哪一侧」（V6.7p12，V6.7p22 修判定）。
     *
     * <p>直接读碰撞箱：开着的门板是一张 3/16 厚的竖板，在薄的轴上只占 0.1875，
     * 另一条水平轴占满 0~1。**必须先找薄轴再判方向**——旧实现按 maxX 优先，
     * 门板贴南/北时也会命中「东」，正对面就被算成了垂直的侧面（实机「往两旁投」的根因）。
     * 关着的时候是平铺的薄板（X/Z 占满），返回 null。
     *
     * @return 门板贴着的那一侧；关着 / 不是活板门 / 形状不可判时返回 null
     */
    private Direction openLidSide(BlockPos trapdoor) {
        var st = mc.world.getBlockState(trapdoor);
        if (!(st.getBlock() instanceof TrapdoorBlock)) return null;
        if (!st.get(TrapdoorBlock.OPEN)) return null;   // 关着是平铺的薄板，不挡任何一侧
        VoxelShape shape = st.getCollisionShape(mc.world, trapdoor);
        if (shape.isEmpty()) return null;
        Box b = shape.getBoundingBox();
        return switch (StashMoveLogic.thinPanelSide(b.minX, b.minZ, b.maxX, b.maxZ)) {
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            case SOUTH -> Direction.SOUTH;
            case NORTH -> Direction.NORTH;
            default -> null;
        };
    }

    /**
     * 柱子旁边「该站哪一格」（V6.7p18 重写，V6.7p22 收紧为「只认正对面」）。
     *
     * <p>为什么必须正对面：门板立在自己那一格的某一侧，站对面贴到边缘也碰不到它；
     * 站侧面或门板那一侧，按住 Shift 往前贴的时候人物碰撞箱（0.6 宽）会顶到门板，
     * 走不到位 → 珍珠投不进腔室。
     *
     * <p>⚠️ V6.7p22 修掉的两个「向两旁扩展」来源：
     * <ol>
     *   <li>{@link #openLidSide} 原来按 maxX 优先判方向，南北朝向的门板会被误判成东侧 →
     *       「正对面」算成垂直侧面；</li>
     *   <li>正前方站不了时会打挑一个侧面（+100 分）甚至退回 {@link #pearlStandSpot} 的环形搜索。
     *       现在投掷阶段（{@link SettlePurpose#THROW}）**只返回正前方那一格**，站不了就返回 null，
     *       由调用方报警，不再侧站。</li>
     * </ol>
     */
    private BlockPos throwStandSpot(BlockPos trapdoor, SettlePurpose purpose) {
        if (mc.world == null || mc.player == null) return null;
        BlockPos water = trapdoor.down(1);
        // V6.7p24：不再只看「开启后的门板」——告示牌 / 活板门 FACING 在门关着时也能定侧。
        Direction front = throwFrontSide(trapdoor);
        if (front == null) {
            // 三个来源全读不出（不是活板门 / 形状异常 / 上方没告示牌）：
            // 脱困可以就近找一格；投掷绝不允许（宁可报警也不乱投）
            return purpose == SettlePurpose.THROW ? null : pearlStandSpot(trapdoor);
        }

        BlockPos frontSpot = frontStandSpot(trapdoor, water, front);
        if (frontSpot != null) {
            return frontSpot;
        }

        // V6.7p22：正前方站不了时**不再退回侧面**。投掷阶段直接失败；脱困阶段才允许就近站一格。
        if (purpose == SettlePurpose.THROW) return null;
        return pearlStandSpot(trapdoor);
    }

    /** 正前方那一格（脚与门同层或上一层）；两格都站不了返回 null。 */
    private BlockPos frontStandSpot(BlockPos trapdoor, BlockPos water, Direction front) {
        for (int level = 0; level <= 1; level++) {
            BlockPos p = new BlockPos(trapdoor.getX() + front.getOffsetX(),
                trapdoor.getY() + level, trapdoor.getZ() + front.getOffsetZ());
            if (p.equals(water)) continue;
            if (isStandable(p)) return p;
        }
        return null;
    }

    /**
     * 朝目标格中心「短按」走过去（V6.7p10，替代 1 格级的寻路）。
     *
     * <p>为什么不用 Baritone：它是按「到附近」设计的（容差大、到点会自己跳/贴边），
     * 旁边再按着前进键就是两套输入打架——用户反馈的「卡脚 → 珍珠投掷失败」就是这么来的。
     * 这里改成：面向中心 → 按 2 tick 松 2 tick → 用位移反馈判断到位/被挡，被挡就跳。
     *
     * @return 0=还在走 1=已到位 2=被挡或超时
     */
    private int stepToward(BlockPos target, int maxTicks) {
        if (target == null || mc.player == null) return 2;
        Vec3d c = Vec3d.ofCenter(target);
        double dist = Math.hypot(c.x - mc.player.getX(), c.z - mc.player.getZ());
        if (dist <= STEP_ARRIVE_EPS) {
            releaseStepKeys();
            return 1;
        }
        if (!target.equals(stepTarget)) {
            stepTarget = target;
            stepTicks = 0;
            stepStuckTicks = 0;
            stepPhase = 0;
            stepLastX = mc.player.getX();
            stepLastZ = mc.player.getZ();
        }

        float yaw = (float) (Math.toDegrees(Math.atan2(c.z - mc.player.getZ(), c.x - mc.player.getX())) - 90.0);
        mc.player.setYaw(yaw);                       // 决定前进方向
        turnTo(yaw, mc.player.getPitch());   // 真实转头（决定前进方向）

        boolean moved = !StashMoveLogic.isStalled(stepLastX, stepLastZ, mc.player.getX(), mc.player.getZ(), 0.02);
        stepLastX = mc.player.getX();
        stepLastZ = mc.player.getZ();
        stepStuckTicks = moved ? 0 : stepStuckTicks + 1;
        stepTicks++;

        // 被挡住：先跳（一格高的坎只能跳上去），跳几次还不行就交回去
        if (stepStuckTicks >= 8) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
            mc.options.jumpKey.setPressed(true);
            if (stepStuckTicks >= 16) {
                releaseStepKeys();
                return 2;
            }
            return 0;
        }
        if (stepTicks > maxTicks) {
            releaseStepKeys();
            return 2;
        }

        boolean press;
        if (mc.player.isTouchingWater()) {
            press = true;                                 // 水里要一直按才游得动
            mc.options.jumpKey.setPressed(true);          // 气泡柱会吸人，按跳帮一把
        } else {
            stepPhase = (stepPhase + 1) % 4;
            press = stepPhase < 2;                         // 干地：按 2 tick 松 2 tick
            if (mc.options != null) mc.options.jumpKey.setPressed(false);
        }
        mc.options.forwardKey.setPressed(press);
        forwardHeld = press;
        return 0;
    }

    private void releaseStepKeys() {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }
        forwardHeld = false;
        stepTarget = null;
        stepTicks = 0;
        stepStuckTicks = 0;
        stepPhase = 0;
    }

    /**
     * 走到柱子旁并确认站位（投掷前用）。
     *
     * <p>流程：算出该站的格子 → 人还在柱子里/活板门上且门关着就先点开 → 远（>6 格）才用寻路，
     * 近处一律 {@link #stepToward} 短按过去（V6.7p10：这就是「不用 Baritone」的那一段）。
     *
     * @return true = 已站好（并记下“原位”，投完退回这里）
     */
    private boolean settlePosition(BlockPos trapdoorPos) {
        return settlePosition(trapdoorPos, SettlePurpose.ESCAPE);
    }

    private boolean settlePosition(BlockPos trapdoorPos, SettlePurpose purpose) {
        if (mc.player == null) return false;
        BlockPos spot = throwStandSpot(trapdoorPos, purpose);
        if (spot == null) {
            dumpStandSpotFailure(trapdoorPos);
            if (purpose == SettlePurpose.THROW) {
                // 交给 tickThrowSequence 的「重走」计时器处理（门可能刚开、方块状态还没同步完），
                // 但无论如何都不会退到侧面投掷。
                debugMsg("[站位] 正前方站不了或读不出门板朝向，拒绝侧站投掷");
            } else {
                enterStuck("柱子周围找不到能站的实心格，请手动确认活板门坐标");
            }
            return false;
        }

        // 人还在柱子里 / 站在活板门上：门关着就先点开（否则关在 1×1 水柱里出不来）
        BlockPos feet = mc.player.getBlockPos();
        boolean onOrInColumn = feet.equals(trapdoorPos) || feet.equals(trapdoorPos.up())
            || mc.player.isTouchingWater();
        if (onOrInColumn && !trapdoorIsOpenOrAbsent(trapdoorPos)) {
            debugMsg("[站位] 人在柱子里/门边上，先打开活板门");
            realClick(trapdoorPos);
            cooldown = 10;
            return false;
        }

        Vec3d t = Vec3d.ofCenter(trapdoorPos);
        double toTrapdoor = Math.hypot(t.x - mc.player.getX(), t.z - mc.player.getZ());

        // 远距离（补完珍珠回来那种）才用寻路；1~6 格一律短按走，避免 Baritone 顶墙卡脚
        if (toTrapdoor > 6.0) {
            walkToPrecise(spot);
            if (debug.get() && tick % 40 == 0) {
                info("[站位] 距柱子 " + String.format(Locale.ROOT, "%.1f", toTrapdoor) + " 格，用寻路过去");
            }
            return false;
        }

        int r = stepToward(spot, 200);
        if (r == 1) {
            throwSafeSpot = mc.player.getBlockPos();
            stepFailCount = 0;
            return true;
        }
        if (r == 2) {
            stepFailCount++;
            if (stepFailCount >= 5) {
                stepFailCount = 0;
                dumpStandSpotFailure(trapdoorPos);
                enterStuck("走不到柱子旁（被挡住了？）");
            } else {
                warnThrottled("走到柱子旁站位失败，重试中（第 " + stepFailCount + " 次）");
            }
        }
        return false;
    }

    /**
     * 按住 Shift 短按前进到柱子边缘（V6.7p10，用户口径）。
     *
     * <p>只用在「已经站在柱子旁那一格中心」之后，位移最多 1 格，蹲着不会掉进柱子里；
     * 走不动就说明到边缘了，可以直接低头投。
     *
     * <p>V6.7p22：进门先确认「人就在门板正对面那一格」——不在就返回 3 让调用方重站，
     * 保证任何情况下都不会从侧面/门板侧起投。
     *
     * @return 0=还在走 1=已到边缘可投 2=踩进水里要退 3=不在活板门正对面（需重站）
     */
    private int sneakStepToEdge(BlockPos trapdoor, int maxTicks) {
        BlockPos water = trapdoor.down(1);
        // V6.7p24：和站位同源（告示牌 → FACING → 门板），不再只依赖开启后的碰撞箱
        Direction front = throwFrontSide(trapdoor);
        if (front == null) return 3;
        // 用「沿正前方轴的投影」判定，而不是到格心的距离：贴边时人会朝柱心移动，
        // 到格心的距离会变大，用距离会在半途把人退回。
        double dx = mc.player.getX() - (trapdoor.getX() + 0.5);
        double dz = mc.player.getZ() - (trapdoor.getZ() + 0.5);
        if (!StashMoveLogic.onFrontSide(dx, dz, front.getOffsetX(), front.getOffsetZ(),
                FRONT_CELL_TOLERANCE)) {
            return 3;
        }

        sneakForThrow();
        Vec3d aim = columnAimPoint(water);
        float yaw = (float) (Math.toDegrees(Math.atan2(aim.z - mc.player.getZ(), aim.x - mc.player.getX())) - 90.0);
        mc.player.setYaw(yaw);
        turnTo(yaw, pitchTo(aim));

        if (mc.player.isTouchingWater()) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
            return 2;
        }

        edgeTicks++;
        boolean moved = !StashMoveLogic.isStalled(edgeLastX, edgeLastZ, mc.player.getX(), mc.player.getZ(), 0.02);
        edgeLastX = mc.player.getX();
        edgeLastZ = mc.player.getZ();
        double hd = Math.hypot(aim.x - mc.player.getX(), aim.z - mc.player.getZ());

        // V6.7p11（照 PearlDrop 的 APPROACH）：**走不动 + 离柱心 < 1.2 格**同时满足才算「居中」，
        // 连续 6 tick 才允许投。只判「走不动」会在被箱子腿/门框挡住时误判成"到边缘了"，然后投偏。
        edgeStuckTicks = (moved ? 0 : edgeStuckTicks + 1);
        edgeCentered = StashMoveLogic.nextCenterStreak(edgeCentered, moved, hd, TRIO_CENTER_DIST);

        if (StashMoveLogic.readyToThrow(edgeCentered, TRIO_CENTER_STALL_TICKS)) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
            edgeTicks = 0;
            edgeStuckTicks = 0;
            edgeCentered = 0;
            return 1;
        }
        if (edgeTicks > maxTicks) {
            mc.options.forwardKey.setPressed(false);
            forwardHeld = false;
            edgeTicks = 0;
            edgeStuckTicks = 0;
            edgeCentered = 0;
            // 超时兜底（同 PearlDrop）：够近就投，否则退回去重站
            return hd < 1.3 ? 1 : 2;
        }

        edgePhase = (edgePhase + 1) % 4;
        boolean press = edgePhase < 2;
        mc.options.forwardKey.setPressed(press);
        forwardHeld = press;
        return 0;
    }

    /** 「投一颗珍珠进柱子」共享子流程的结果（V6.7p13 起，三人 1号 与 双号搬运端共用）。 */
    private enum ThrowSeq { RUNNING, DONE, NO_PEARL, FAILED }

    /**
     * 站位目的（V6.7p22）。
     *
     * <ul>
     *   <li>{@link #ESCAPE}：先从气泡柱里出来，允许站到附近任意能站的格；</li>
     *   <li>{@link #THROW}：投掷前的站位，**只允许活板门门板的正对面**，正前方被挡就拒绝投掷，
     *       绝不退到侧面（旧实现会打分挑侧面，实机就是「往两旁投」）。</li>
     * </ul>
     */
    private enum SettlePurpose { ESCAPE, THROW }

    /** 箱子旁算好的落脚点（V6.7p25）：spot 是空气格，los = 从那里能不能直视箱子。 */
    private record ChestApproach(BlockPos spot, boolean los) {
    }

    /** 靠近箱子的三种结果（V6.7p25）：已经能开 / 正在走 / 周围没有可站格。 */
    private enum Approach { IN_RANGE, MOVING, NO_SPOT }

    /** 三人模式 1 号的投掷入口（具体步骤在 {@link #tickThrowSequence}）。 */
    private void trioThrow() {
        BlockPos stand = trioTrapdoor();
        if (stand == null || stand.equals(BlockPos.ORIGIN)) {
            enterStuck("请先设置" + zoneName() + "的活板门位置");
            return;
        }
        ThrowSeq r = tickThrowSequence(stand, this::finishTrioThrow);
        if (r == ThrowSeq.NO_PEARL) {
            // V6.7p18：投掷把珍珠用光时不再直接卡死，回本区珍珠箱补货再来（补不到由 RESTOCK 报警）
            restockPhase = 0;
            setTrioState(TrioState.RESTOCK, "没有末影珍珠了，先回珍珠箱补货");
        }
    }

    /**
     * 投掷子流程（V6.7p3 建立，V6.7p7 按用户口径重排节奏）：
     * 检珍珠 → 校正站位 → <b>确认脱离</b> → <b>开活板门（大部分时候是关的，必须自己点开并确认）</b>
     * → 按住 Shift 走近 → 投掷 → 退回安全格 → 校验（失败重试 3 次）。
     *
     * <p>V6.7p7 改动：每个子步骤之间留 6 tick（原来 2 tick）、点完活板门等 14 tick 让服务端同步方块状态、
     * 校验超时 60 → 80 tick；每 tick 都调用 {@link #faceColumn()} 把视角锁在气泡柱上（全程转头）。
     */
    private ThrowSeq tickThrowSequence(BlockPos stand, Runnable onSuccess) {
        BlockPos water = stand.down(1);
        // 每 tick 都把视角对准气泡柱（用户口径：全程都要转头）。
        // 唯独「正在投掷」这一 tick 不叠加：doThrowPearl 已经算好精确瞄准角，
        // 这里再转一次会把镜头拽回柱心，投掷就偏了。
        if (throwSub != 4) faceColumnAt(stand);

        switch (throwSub) {
            case 0 -> {
                if (ownPearlInColumn(water)) {
                    debugMsg("[投掷] 气泡柱里已有自己的珍珠，跳过投掷");
                    onSuccess.run();
                    return ThrowSeq.DONE;
                }
                throwRetries = 0;
                frontAlignTries = 0;
                frontWalkStart = 0;
                setThrowSub(1, "柱里没珍珠，准备投一颗（先脱离气泡柱再开活板门）");
            }
            case 1 -> {
                if (settlePosition(stand)) {
                    debugMsg("[投掷] 站位已确认");
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
                    trapdoorRetreats = 0;
                    // V6.7p18：门开着才读得出门板占哪侧 —— 到这里才把站位纠到「正前方」。
                    // V6.7p22：这里必须用 THROW 口径（只认正对面），且站不了时不再退侧面。
                    if (!settlePosition(stand, SettlePurpose.THROW)) {
                        if (frontWalkStart == 0) {
                            frontWalkStart = tick;
                        } else if (tick - frontWalkStart > FRONT_ALIGN_TIMEOUT) {
                            frontAlignTries++;
                            frontWalkStart = tick;
                            PathManagers.get().stop();
                            if (frontAlignTries > FRONT_ALIGN_MAX_TRIES) {
                                // V6.7p24：三来源（告示牌/朝向/门板）任一能读出就给具体方向
                                Direction frontNow = throwFrontSide(stand);
                                enterStuck(frontNow == null
                                    ? "活板门已开，但告示牌/朝向/门板三处都读不出投掷侧，拒绝投掷"
                                    : "投掷侧（" + frontNow.asString()
                                        + " 侧）那一格站不了，已重走 " + FRONT_ALIGN_MAX_TRIES
                                        + " 次；本模块只在正对面投掷，不会退到侧面");
                                return ThrowSeq.FAILED;
                            }
                            warnThrottled("还没站到活板门正前方，重走（第 " + frontAlignTries + " 次）");
                        }
                        return ThrowSeq.RUNNING;
                    }
                    frontAlignTries = 0;
                    frontWalkStart = 0;
                    debugMsg("[投掷] 已站到活板门正前方");
                    setThrowSub(3, "已站到正前方，潜行贴到边缘");
                    edgeTicks = 0;
                    edgeStuckTicks = 0;
                    edgeCentered = 0;
                    edgePhase = 0;
                    edgeLastX = mc.player.getX();
                    edgeLastZ = mc.player.getZ();
                    cooldown = PACE_STEP;
                    return ThrowSeq.RUNNING;
                }
                // 关着 → 必须先自己打开（用户口径：大部分时间都是关的）
                // ⚠️ V6.7p11 刻意偏离：PearlDrop 反编译源码里是**反过来**的
                //   （`closeOpenTrapdoor=true`：投之前把开着的活板门关掉）。
                //   但用户明确指出「门关着珍珠就进不去柱子」，且其实机做法是**开着投**。
                //   别为了"对齐参考实现"把这里改成关闭——那是另一种腔体结构。
                // V6.7p12（用户口径）：连点 6 次还打不开 → **先离开当前位置再回来重试**。
                // 有时人贴得太近/站位太偏，准星被门框或自己挡住，退开一米换个角度就好了。
                if (trapdoorClickAttempts >= 6) {
                    if (trapdoorRetreats < 2) {
                        trapdoorRetreats++;
                        trapdoorClickAttempts = 0;
                        info("活板门连点 6 次没开，先退开重来（第 " + trapdoorRetreats + " 轮）");
                        // 面朝柱子按 S 退 25 tick（faceColumn 已经把朝向摆好），然后回第 1 步重新站位
                        backoffTicks = 25;
                        setThrowSub(1, "退开换角度重开活板门");
                        cooldown = 30;
                        return ThrowSeq.RUNNING;
                    }
                    enterStuck("连续 " + (6 * (trapdoorRetreats + 1)) + " 次都打不开活板门"
                        + "（眼睛距=" + String.format(Locale.ROOT, "%.2f", mc.player.getEyePos().distanceTo(Vec3d.ofCenter(stand)))
                        + "，请手动确认活板门是否填对、有没有被方块挡住）");
                    return ThrowSeq.FAILED;
                }
                if (!approachForOpen(stand)) {
                    debugMsg("[活板门] 离得还远，先走近");
                    return ThrowSeq.RUNNING;
                }
                trapdoorClickAttempts++;
                debugMsg("[活板门] 关着 → 点击打开（第 " + trapdoorClickAttempts + " 次）");
                realClick(stand);
                cooldown = PACE_TRAPDOOR_CLICK;
            }
            case 3 -> {
                // V6.7p10：站位已经由 settlePosition 站到「柱子旁那一格中心」，
                // 这里只做最后 1 格的潜行贴边（蹲着不会掉进柱子里），走不动就说明到边缘了。
                int r = sneakStepToEdge(stand, TRIO_APPROACH_TIMEOUT);
                if (r == 1) {
                    setThrowSub(4, "已到柱子边缘，低头投掷");
                    cooldown = PACE_STEP;
                } else if (r == 2) {
                    debugMsg("[贴边] 踩进气泡柱了，先退开重来");
                    setThrowSub(5, "贴边时踩进水里");
                    cooldown = PACE_STEP;
                } else if (r == 3) {
                    // V6.7p22：人不在门板正对面 → 回去重新站位，绝不从侧面投
                    debugMsg("[贴边] 人不在活板门正前方，退回重新站位");
                    setThrowSub(2, "不在正前方，重新站位");
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
                        return ThrowSeq.RUNNING;
                    }
                    return ThrowSeq.NO_PEARL;
                }
                sneakForThrow();
                // V6.7p11：投之前记一次背包珍珠数，投完用「数量是否下降」判定成功（PearlDrop 的做法）
                pearlCountBefore = countPearls();
                debugMsg("[投掷] 按住 Shift 投出（快捷栏槽位 " + slot + "，现有珍珠 " + pearlCountBefore + "）");
                doThrowPearl(slot, columnAimPoint(water));
                backoffTicks = TRIO_RETREAT_TIMEOUT;   // trio 投完蹲着后退（后退阶段处理停止）
                setThrowSub(5, "已投出，准备后退");
                cooldown = PACE_STEP;
            }
            case 5 -> {
                if (backOffToSafe(water)) {
                    setThrowSub(6, "已退到安全距离");
                    verifyStartTick = tick;
                    cooldown = PACE_STEP;
                }
            }
            case 6 -> {
                // V6.7p11（照 PearlDrop）：**背包珍珠数下降 = 投出去了**。
                // 之前用「柱里能扫到自己的珍珠实体」判定，客户端实体同步一慢就误判成失败 → 重投 → 一次少两颗。
                boolean thrown = countPearls() < pearlCountBefore;
                if (thrown) {
                    lastPearlThrowTick = tick;
                    boolean inColumn = ownPearlInColumn(water);
                    if (inColumn) {
                        debugMsg("[校验] 投掷成功：珍珠数 " + pearlCountBefore + " → " + countPearls()
                            + "，柱内也已检测到");
                    } else {
                        // 数量下降只证明"扔出去了"，落点是否进柱另说（客户端实体可能还没同步）。
                        // 这里只提示，不重投——真没进柱的话，发「拉我」之前那次校验会拦住。
                        warning("珍珠已投出（数量 " + pearlCountBefore + " → " + countPearls()
                            + "），但柱内暂未检测到，若下趟回不来请检查落点");
                    }
                    onSuccess.run();
                    return ThrowSeq.DONE;
                } else if (tick - verifyStartTick > TRIO_THROW_TIMEOUT) {
                    throwRetries++;
                    if (throwRetries >= TRIO_MAX_THROW_RETRIES) {
                        enterStuck("投掷珍珠 " + TRIO_MAX_THROW_RETRIES + " 次都没确认投出（珍珠数没变）");
                    } else {
                        debugMsg("[校验] 珍珠数没变，第 " + throwRetries + " 次重投");
                        frontAlignTries = 0;
                        frontWalkStart = 0;
                        setThrowSub(2, "校验失败，重开活板门重投");
                        cooldown = PACE_STEP;
                    }
                }
            }
        }
    
        return ThrowSeq.RUNNING;}

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

    /** ④ 松开 shift、按 S 退离气泡柱（退到离柱心 ≥2.5 格），再回到投掷前的原位。 */
    private boolean backOffToSafe(BlockPos water) {
        // V6.7p12：后退全程保持蹲着（用户口径），只在退到安全距离后才由 finishTrioThrow 松开
        sneakForThrow();
        Vec3d center = Vec3d.ofCenter(water);
        double dist = Math.hypot(center.x - mc.player.getX(), center.z - mc.player.getZ());

        if (StashMoveLogic.needMoreBackoff(dist, TRIO_RETREAT_DISTANCE)) {
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
                blacklist.clear();
        chestApproachCache.clear();
        chestNoSpot.clear();
        chestFailedSpots.clear();
            skippedUntil.clear();
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

    /**
     * 离「最近的那个区」有多远（水平距离，格）。
     *
     * <p>锚点用两个区的基准坐标：待搬运箱位置、存储箱位置（与 {@link #initialZone()} 判区用的是同一对，
     * 保证「判定所在区」和「距离检查」不会各说各话）。
     *
     * @return 两处坐标都没设置时返回 {@link Double#MAX_VALUE}
     */
    private double nearestZoneDistanceXZ() {
        if (mc.player == null) return Double.MAX_VALUE;
        BlockPos s = sourceTarget.get();
        BlockPos t = trioStoreChest.get();
        boolean hasS = s != null && !s.equals(BlockPos.ORIGIN);
        boolean hasT = t != null && !t.equals(BlockPos.ORIGIN);
        return StashMoveLogic.nearestZoneDistance(
            mc.player.getX(), mc.player.getZ(),
            hasS ? s.getX() + 0.5 : 0, hasS ? s.getZ() + 0.5 : 0, hasS,
            hasT ? t.getX() + 0.5 : 0, hasT ? t.getZ() + 0.5 : 0, hasT);
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

    /** 到珍珠点/出发点「到了」没：水平 ≤2.5 且高度差 ≤2.5（与 GoalNear 目标同一套尺度）。 */
    private boolean nearPullPoint(BlockPos pos) {
        BlockPos p = mc.player.getBlockPos();
        return StashPearlLogic.nearPearlPoint(p.getX() - pos.getX(), p.getY() - pos.getY(),
            p.getZ() - pos.getZ(), PEARL_NEAR_HORIZONTAL, PEARL_NEAR_DY);
    }

    /**
     * 拉珍珠端的寻路（V6.7p20）。
     *
     * <p>⚠️ 以前用 `moveTo(pos, true)` = GoalXZ（只看 XZ、不看 Y）：
     * Baritone 为了到那个 XZ 会一路挖穿挡路的方块（allowBreak 默认开），
     * 在洞穴系统里就可能挖到基岩层；而且它「到没到」只看 XZ，和我们的点击判定不同源。
     * 现在改成 GoalNear(2)（Y 参与）+ 可选禁挖。
     */
    private void pathToPullTarget(BlockPos pos) {
        PathManagers.get().setAllowBreak(!pullerNoDig.get());
        PathManagers.get().moveToNear(pos, PULLER_GOAL_RANGE);
    }

    private void tickPuller() {
        if (!pullActive || pullPos == null) return;

        if (pullPhase == 0) {
            if (tick - pullStartTick > pathTimeout.get()) {
                warning("拉珍珠赶路超时，已放弃");
                resetPull();
                return;
            }
            if (nearPullPoint(pullPos)) {
                // V6.7p13：点活板门加「确认 + 重试」——用门的状态是否翻转判断点中了没。
                boolean isTrapdoor = mc.world.getBlockState(pullPos).getBlock() instanceof TrapdoorBlock;
                if (!pullClickSent) {
                    pullClickSent = true;
                    pullClickAttempts = 1;
                    pullTrapdoorWasOpen = isTrapdoor && trapdoorIsOpenOrAbsent(pullPos);
                    pullClickStartTick = tick;
                    realClick(pullPos);
                    return;
                }
                boolean flipped = isTrapdoor && (trapdoorIsOpenOrAbsent(pullPos) != pullTrapdoorWasOpen);
                if (flipped) {
                    finishPullClick();
                } else if (tick - pullClickStartTick > 10) {
                    if (pullClickAttempts < 3) {
                        pullClickAttempts++;
                        pullTrapdoorWasOpen = isTrapdoor && trapdoorIsOpenOrAbsent(pullPos);
                        pullClickStartTick = tick;
                        debugMsg("[拉珍珠] 活板门没翻转，重试第 " + (pullClickAttempts - 1) + " 次");
                        realClick(pullPos);
                    } else {
                        // 3 次都没翻转也发 ack，别让 1 号一直等
                        finishPullClick();
                    }
                }
                return;
            }
            if (!PathManagers.get().isPathing()) pathToPullTarget(pullPos);
            return;
        }

        // 回出发点
        if (!returnToStart.get() || pullStartPos == null) {
            resetPull();
            return;
        }
        if (nearPullPoint(pullStartPos)) {
            resetPull();
            return;
        }
        if (tick - pullReturnStart > pathTimeout.get()) {
            resetPull();
            return;
        }
        if (!PathManagers.get().isPathing()) pathToPullTarget(pullStartPos);
    }

    private void finishPullClick() {
        String ack = pullN >= 0 ? ackKeyword.get() + pullN : ackKeyword.get();
        sendPrivate(pullRequester, ack);
        info("已拉 " + pullRequester + " 的珍珠");
        pullPhase = 1;
        pullReturnStart = tick;
    }

    private void resetPull() {
        pullActive = false;
        pullPos = null;
        pullRequester = null;
        pullN = -1;
        pullPhase = 0;
        pullClickSent = false;
        pullClickAttempts = 0;
        lastPullEndTick = tick;
        // V6.7p20：恢复 Baritone 的挖掘开关，别把「赶路不挖掘」带到别的模块
        PathManagers.get().setAllowBreak(true);
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
        // 请求指令必须出现在正文开头，避免「不要拉我」「别拉我」这类聊天误触发。
        var req = PeerMessageLogic.matchRequestKeyword(parsed.content(), keyword.get());
        if (!req.matched()) return;

        String expected = mode.get() == Mode.TRIO ? peerMover.get().trim() : "";
        var white = pullWhitelist.get();
        boolean whiteFilled = white != null && !white.isEmpty();
        // V6.7p16：双号拉珍珠端**必须**填白名单才收请求（三号仍靠「1 号搬运端」锁定）。
        if (!PeerMessageLogic.pullGateOpen(mode.get() == Mode.TRIO, !expected.isEmpty(), whiteFilled)) {
            warnThrottled(mode.get() == Mode.TRIO
                ? "拉珍珠端未指定「1 号搬运端」，已忽略拉珍珠请求"
                : "双号拉珍珠端未填「拉珍珠白名单」，已拒绝所有拉珍珠请求");
            return;
        }
        if (!PeerMessageLogic.requesterAllowed(parsed.sender(), expected, white)) {
            debugMsg("忽略不在白名单/非预期搭档的拉珍珠请求：" + parsed.sender());
            return;
        }
        if (!PeerMessageLogic.pullCooldownElapsed(pullActive, tick, lastPullEndTick, pullCooldown.get())) {
            info(pullActive ? "当前已有珍珠在拉中，忽略 " + parsed.sender() + " 的请求"
                : "拉珍珠冷却中，忽略 " + parsed.sender() + " 的请求");
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
        pullClickSent = false;
        pullClickAttempts = 0;
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
                            info("找到包含名字的告示牌");
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
        info("已记录珍珠点「" + name + "」，请在设置里把它改成玩家 ID");
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
        // V6.7p10：真实转头（直接改玩家 yaw/pitch 并保持），
        // 等本 tick 的移动包把朝向带给服务端之后，再发投掷包（SendMovementPacketsEvent.Post）。
        // 参考实现 RustElytraClient 的 SupplyTask.lookAt 就是 player.setYaw/setPitch + delay(3)；
        // Meteor 的 Rotations.rotate 是静默转头（发完立刻恢复），看起来只有一帧闪动。
        turnTo(yp[0], yp[1]);
        afterMoveAction = () -> {
            if (mc.player != null) mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        };
        // 收尾（是否后退、是否松 Shift）由调用方决定：trio 要蹲着后退，双号投完直接站起走下一步
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
        Direction lid = openLidSide(trapdoorPos);
        Direction sign = signThrowSide(trapdoorPos);
        Direction front = throwFrontSide(trapdoorPos);
        debugMsg("[脱困] 找不到可站格｜自身=" + blockName(feet)
            + " 脚下=" + blockName(feet.down())
            + " 水中=" + mc.player.isTouchingWater()
            + "｜活板门打开=" + trapdoorIsOpenOrAbsent(trapdoorPos)
            + " 门板=" + (lid == null ? "不可判" : lid.asString())
            + " 告示牌=" + (sign == null ? "1~3 格内没找到" : sign.asString())
            + " 投掷侧=" + (front == null ? "不可判" : front.asString())
            + " 水格=" + blockName(water));
    }

    private String blockName(BlockPos p) {
        if (mc.world == null) return "?";
        return net.minecraft.registry.Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
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
     * 气泡柱里是否已经有<b>自己的</b>珍珠（严格判定：XZ 必须真在 1×1 柱内、Y 在水柱内）。
     *
     * <p>用途有两个：① 投掷前先查——已经有珍珠就不必再投（用户口径）；
     * ② 投掷后校验——必须真的落在柱内才算成功。
     *
     * <p>V6.7p22：XZ 容差从 0.9 收紧到 {@link #PEARL_IN_COLUMN_XZ}（0.55）。原来的 0.9
     * 会把落在**旁边方块**上的珍珠也算成「柱内」（用户说的「检测向两旁扩展」），
     * 结果是回程票没投进去、模块却以为成功，下一趟拉不回来。
     */
    private boolean ownPearlInColumn(BlockPos water) {
        if (mc.world == null || mc.player == null) return false;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl)) continue;
            Entity owner = pearl.getOwner();
            boolean mine = owner == mc.player
                || (owner != null && owner.getUuid().equals(mc.player.getUuid()));
            if (!mine) continue;
            if (StashMoveLogic.pearlInColumn(pearl.getX(), pearl.getZ(),
                    water.getX(), water.getZ(), PEARL_IN_COLUMN_XZ)
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

    /** 光标上是否没有东西（V6.7p12：关界面前必须为空）。 */
    private boolean cursorEmpty() {
        if (mc.player == null) return true;
        ScreenHandler h = mc.player.currentScreenHandler;
        return h == null || h.getCursorStack().isEmpty();
    }

    /** 光标上拿着几颗珍珠。 */
    private int cursorPearlCount() {
        if (mc.player == null) return 0;
        ScreenHandler h = mc.player.currentScreenHandler;
        if (h == null) return 0;
        ItemStack st = h.getCursorStack();
        return st.isOf(Items.ENDER_PEARL) ? st.getCount() : 0;
    }

    /** 余量该放回哪个槽位：优先原槽，其次任意空容器槽，都没有返回 -1。 */
    private int putbackSlot() {
        ScreenHandler h = containerHandler();
        if (h == null) return -1;
        if (restockChestSlot >= 0 && restockChestSlot < h.slots.size()) {
            ItemStack st = h.getSlot(restockChestSlot).getStack();
            if (st.isEmpty() || st.isOf(Items.ENDER_PEARL)) return restockChestSlot;
        }
        int size = h.slots.size();
        for (int i = 0; i < size - 36; i++) {
            if (h.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    private void clickSlot(int slot, SlotActionType type) {
        clickSlot(slot, type, 0);
    }

    private void clickSlot(int slot, SlotActionType type, int button) {
        ScreenHandler handler = containerHandler();
        if (handler == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, button, type, mc.player);
    }

    /**
     * 容器区（非玩家背包）拿物品：拿一摞算 {@code TAKEN}。
     *
     * <p>V6.7p21：返回值从 boolean 改成三态 —— 「箱空」与「箱里有东西但全被过滤」必须分开，
     * 否则「只拿潜影盒」会把普通物资箱当空箱拉黑（见 {@link StashMoveLogic#takeOutcome}）。
     */
    private StashMoveLogic.TakeOutcome takeFromContainer() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return StashMoveLogic.TakeOutcome.EMPTY;
        int size = handler.slots.size();
        boolean anyItem = false;
        for (int i = 0; i < size - 36; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            anyItem = true;
            if (onlyShulker.get() && !StashPearlLogic.isShulkerBoxId(Registries.ITEM.getId(stack.getItem()).toString())) continue;
            clickSlot(i, SlotActionType.QUICK_MOVE);
            if (StashPearlLogic.isShulkerBoxId(Registries.ITEM.getId(stack.getItem()).toString())) {
                StashStats.get().addShulkers(stack.getCount());
            }
            cooldown = Math.max(1, clickDelay.get());
            progress();
            return StashMoveLogic.takeOutcome(anyItem, true);
        }
        return StashMoveLogic.takeOutcome(anyItem, false);
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

    // ==================== 末影箱中转（V6.7p27，双号 + 三号共用） ====================

    /**
     * 末影箱功能当前是否可用：开关打开 + 本号是搬运端（拉珍珠端不参与）+ 世界/玩家就绪。
     */
    private boolean enderUsable() {
        return enderEnabled.get() && isMoverRole() && mc.player != null && mc.world != null;
    }

    /**
     * 当前打开的是不是**我们要用的那台末影箱**（V6.7p27 引入，V6.7p29 修判定）。
     *
     * <p>⚠️ 不能再用 {@code getInventory() instanceof EnderChestInventory} 判断。客户端收到的
     * 只有 {@code OpenScreenS2CPacket(GENERIC_9X3)}，{@code HandledScreens} 会用
     * {@code GenericContainerScreenHandler.createGeneric9x3(syncId, inv)} 建界面，而那个工厂
     * 内部是 {@code new SimpleInventory(9 * 3)} —— <b>客户端永远看不到 {@code EnderChestInventory}</b>
     * （它只存在于服务端那一侧）。
     *
     * <p>旧判定因此恒为 false，实机表现为：打开末影箱 → 被判成「不是末影箱」→ 关掉 →
     * 下一 tick 再打开 → 再关掉 …… 一直循环到 15 秒超时。
     *
     * <p>现在按「界面形态 + 点击位置 + 方块类型」三重认定：9x3 通用容器（63 槽）、
     * 我们最后一次点击的就是 {@link #enderTarget}、而且那一格现在仍然是末影箱。
     * 这样既不会把普通箱子误认成末影箱（位置对不上），也不会被客户端背包类型骗到。
     */
    private boolean enderChestOpen() {
        if (enderTarget == null || !containerOpen()) return false;
        ScreenHandler handler = containerHandler();
        if (!StashMoveLogic.isEnderScreenShape(handler instanceof GenericContainerScreenHandler,
            handler == null ? 0 : handler.slots.size())) return false;
        if (!enderTarget.equals(openChest)) return false;  // 我们点的就是这一台
        return mc.world != null && mc.world.getBlockState(enderTarget).getBlock() == Blocks.ENDER_CHEST;
    }

    /**
     * 选出本次要用的末影箱（V6.7p30 混合模式：坐标优先 + 校验 + 自动扫描兜底）。
     *
     * <p>分区坐标填了就强制用那一台，但**必须校验那一格真的是末影箱**——
     * 被挪走 / 填错坐标时绝不能把货倒进普通箱子，报警一次并回退自动扫描。
     * 坐标留空（0,0,0）= 维持 p27 的自动扫描（就近找一台）。
     *
     * @param storageZone true = 存储区的末影箱（取货用）；false = 待取货箱区的（存货用）
     */
    private BlockPos resolveEnderTarget(boolean storageZone) {
        BlockPos coord = storageZone
            ? (mode.get() == Mode.TRIO ? trioEnderStoreChest.get() : enderStoreChest.get())
            : (mode.get() == Mode.TRIO ? trioEnderLootChest.get() : enderLootChest.get());
        boolean coordSet = coord != null && StashMoveLogic.isCoordSet(coord.getX(), coord.getY(), coord.getZ());
        if (coordSet) {
            boolean ok = mc.world != null && mc.world.getBlockState(coord).getBlock() == Blocks.ENDER_CHEST;
            if (ok) {
                debugMsg("[末影箱] 使用分区坐标指定的那台（" + (storageZone ? "存储区" : "取货区") + "）");
                return coord;
            }
            warnThrottled("末影箱分区坐标处不是末影箱（被挪走或填错？），本趟改用自动扫描");
        }
        return findNearbyEnderChest();
    }

    /**
     * 在玩家周围找最近的末影箱（V6.7p27）。
     *
     * <p>检测方式：以玩家脚所在格为中心、{@code 末影箱搜索半径}（默认 6 格）为半边长扫一个立方体，
     * 取欧氏距离最近的一台。用户场地是「预先放好、就在箱子旁边」，所以正常情况都在 1~2 格内。
     *
     * <p>结果带缓存：命中就复用（每 tick 校验方块还在）；**没命中也会缓存
     * {@link #ENDER_SCAN_TTL} tick**，避免每 tick 扫 13³ 个方块。
     */
    private BlockPos findNearbyEnderChest() {
        if (!enderUsable()) return null;
        if (enderChestCache != null) {
            if (mc.world.getBlockState(enderChestCache).getBlock() == Blocks.ENDER_CHEST) return enderChestCache;
            enderChestCache = null;   // 被拆了/换地方了
        }
        if (tick - enderChestCacheTick < ENDER_SCAN_TTL) return null;

        enderChestCacheTick = tick;
        int r = Math.max(2, enderSearchRadius.get());
        BlockPos base = mc.player.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.add(dx, dy, dz);
                    if (mc.world.getBlockState(p).getBlock() != Blocks.ENDER_CHEST) continue;
                    double d = base.getSquaredDistance(p);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        enderChestCache = best;
        if (best != null) {
            debugMsg("[末影箱] 附近找到一台，格距 " + String.format(Locale.ROOT, "%.1f", Math.sqrt(bestD))
                + "，眼→箱体 " + String.format(Locale.ROOT, "%.2f", eyeBoxDistance(best))
                + " 格（开箱距离 " + String.format(Locale.ROOT, "%.1f", openReach.get()) + "）");
        }
        return best;
    }

    private void invalidateEnderCache() {
        enderChestCache = null;
        enderChestCacheTick = 0;
    }

    /** 背包里还有没有「该存进末影箱」的东西（珍珠永远不存）。 */
    private boolean hasLootToDeposit() {
        if (mc.player == null) return false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (StashMoveLogic.shouldStoreInEnder(Registries.ITEM.getId(stack.getItem()).toString())) return true;
        }
        return false;
    }

    /**
     * 背包满时：如果开着末影箱、附近有末影箱、本趟还没存过 → 切到「存末影箱」。
     *
     * @return true = 本 tick 已经切状态，调用方不要再往下走
     */
    private boolean tryEnterEnderPut(Runnable enterState) {
        if (!enderUsable() || enderPutDone) return false;
        if (!hasLootToDeposit()) return false;
        BlockPos ender = resolveEnderTarget(false);
        if (ender == null) return false;
        enderTarget = ender;
        enderStateStart = tick;
        enderCloseCount = 0;
        enderOpenLogged = false;
        closeScreen();
        enterState.run();
        debugMsg("[末影箱] 背包满 → 先把货存进附近末影箱（眼→箱体 "
            + String.format(Locale.ROOT, "%.2f", eyeBoxDistance(ender)) + " 格 / 开箱距离 "
            + String.format(Locale.ROOT, "%.1f", openReach.get()) + "，"
            + (eyeBoxDistance(ender) <= openReach.get() ? "够得着" : "需要走近") + "）");
        return true;
    }

    /**
     * 到存储区、背包已经卸空时：如果本趟存过末影箱、还没取过 → 切到「取末影箱」。
     *
     * <p>注意：末影箱内容只有服务端知道（客户端方块实体里没有），所以「取」必须打开一次才知道空不空，
     * 空箱会多一次开箱动作，这是可接受的。
     *
     * @return true = 本 tick 已经切状态
     */
    private boolean tryEnterEnderTake(Runnable enterState) {
        if (!enderUsable() || !enderPutDone || enderTakeDone) return false;
        BlockPos ender = resolveEnderTarget(true);
        if (ender == null) return false;
        enderTarget = ender;
        enderStateStart = tick;
        enderCloseCount = 0;
        enderOpenLogged = false;
        enterState.run();
        debugMsg("[末影箱] 背包卸空 → 去把末影箱里的货取出来（眼→箱体 "
            + String.format(Locale.ROOT, "%.2f", eyeBoxDistance(ender)) + " 格 / 开箱距离 "
            + String.format(Locale.ROOT, "%.1f", openReach.get()) + "）");
        return true;
    }

    /** 存末影箱的一 tick：返回 false = 结束（调用方切回原状态）。 */
    private boolean enderPutTick() {
        if (enderTarget == null || mc.world.getBlockState(enderTarget).getBlock() != Blocks.ENDER_CHEST) {
            enderPutDone = true;
            return false;
        }
        if (tick - enderStateStart > ENDER_STATE_TIMEOUT) {
            closeScreen();
            enderPutDone = true;
            warning("末影箱存货超时（" + (ENDER_STATE_TIMEOUT / 20) + " 秒），本趟不再存");
            return false;
        }
        if (!enderChestOpen()) {
            if (containerOpen()) {
                // 上一个容器还没关干净（或手动开了别的箱子）：关掉重开，但不允许无限循环
                if (++enderCloseCount > ENDER_CLOSE_LIMIT) {
                    closeScreen();
                    enderPutDone = true;
                    warning("末影箱界面识别异常（连续关掉 " + enderCloseCount
                        + " 次非末影箱界面），本趟跳过中转；请确认末影箱位置没有被别的箱子挡住");
                    return false;
                }
                debugMsg("[末影箱] 当前界面不是那台末影箱，先关掉（第 " + enderCloseCount + " 次）");
                closeScreen();
                cooldown = 5;
                return true;
            }
            if (!hasLootToDeposit()) {
                enderPutDone = true;
                return false;
            }
            goOpenChest(enderTarget, false);
            // 四周确实没有「站过去够得着」的格子：立即降级，不要干等超时
            if (chestNoSpot.contains(enderTarget)) {
                closeScreen();
                enderPutDone = true;
                warning("末影箱四周没有可站的格子（或都超出开箱距离 "
                    + String.format(Locale.ROOT, "%.1f", openReach.get()) + " 格），本趟跳过中转");
                return false;
            }
            // 末影箱打不开就降级（不能让它触发 openChestAt 的「连续 8 次」硬停）
            if (openAttempts >= 5) {
                closeScreen();
                enderPutDone = true;
                warning("末影箱打不开（已点 5 次），本趟跳过中转");
                return false;
            }
            return true;
        }
        if (!enderOpenLogged) {
            enderOpenLogged = true;
            enderCloseCount = 0;
            debugMsg("[末影箱] 界面已就绪（9x3 / 63 槽），开始存货");
        }
        // 容量上限：27 格全满就停手，剩下的继续背着走（到存储区照常卸）
        if (containerIsFull()) {
            closeScreen();
            enderPutDone = true;
            warning("末影箱已满（27 格），剩下的货继续背着走");
            return false;
        }
        int slot = findEnderDepositSlot();
        if (slot < 0) {
            closeScreen();
            enderPutDone = true;
            return false;
        }
        clickSlot(slot, SlotActionType.QUICK_MOVE);
        cooldown = Math.max(1, clickDelay.get());
        progress();
        return true;
    }

    /** 取末影箱的一 tick：返回 false = 结束（调用方切回卸货）。 */
    private boolean enderTakeTick() {
        if (enderTarget == null || mc.world.getBlockState(enderTarget).getBlock() != Blocks.ENDER_CHEST) {
            enderTakeDone = true;
            return false;
        }
        if (tick - enderStateStart > ENDER_STATE_TIMEOUT) {
            closeScreen();
            enderTakeDone = true;
            warning("末影箱取货超时（" + (ENDER_STATE_TIMEOUT / 20) + " 秒），剩余货物留在箱里");
            return false;
        }
        if (!enderChestOpen()) {
            if (containerOpen()) {
                if (++enderCloseCount > ENDER_CLOSE_LIMIT) {
                    closeScreen();
                    enderTakeDone = true;
                    warning("末影箱界面识别异常（连续关掉 " + enderCloseCount
                        + " 次非末影箱界面），剩余货物留在箱里；请确认存储区末影箱位置");
                    return false;
                }
                debugMsg("[末影箱] 当前界面不是那台末影箱，先关掉（第 " + enderCloseCount + " 次）");
                closeScreen();
                cooldown = 5;
                return true;
            }
            goOpenChest(enderTarget, false);
            if (chestNoSpot.contains(enderTarget)) {
                closeScreen();
                enderTakeDone = true;
                warning("末影箱四周没有可站的格子（或都超出开箱距离 "
                    + String.format(Locale.ROOT, "%.1f", openReach.get()) + " 格），剩余货物留在箱里");
                return false;
            }
            if (openAttempts >= 5) {
                closeScreen();
                enderTakeDone = true;
                warning("末影箱打不开（已点 5 次），剩余货物留在箱里");
                return false;
            }
            return true;
        }
        if (!enderOpenLogged) {
            enderOpenLogged = true;
            enderCloseCount = 0;
            debugMsg("[末影箱] 界面已就绪（9x3 / 63 槽），开始取货");
        }
        if (containerEmpty()) {
            closeScreen();
            enderTakeDone = true;
            debugMsg("[末影箱] 是空的（本趟没存进去？），跳过");
            return false;
        }
        // 背包放不下就先停（正常流程：卸空后才来取，36 格 ≥ 末影箱 27 格）
        if (findEmptyPlayerSlotInHandler() < 0) {
            closeScreen();
            enderTakeDone = true;
            warning("背包已满，取不了末影箱（请检查存储箱是否够空）");
            return false;
        }
        int slot = findEnderWithdrawSlot();
        if (slot < 0) {
            closeScreen();
            enderTakeDone = true;
            return false;
        }
        clickSlot(slot, SlotActionType.QUICK_MOVE);
        cooldown = Math.max(1, clickDelay.get());
        progress();
        return true;
    }

    /** 末影箱界面里，背包部分第一个「可存入」的槽位（珍珠除外）。 */
    private int findEnderDepositSlot() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return -1;
        int size = handler.slots.size();
        for (int i = size - 36; i < size; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (StashMoveLogic.shouldStoreInEnder(Registries.ITEM.getId(stack.getItem()).toString())) return i;
        }
        return -1;
    }

    /** 末影箱容器部分第一个非空槽位（逐格 QUICK_MOVE 到背包）。 */
    private int findEnderWithdrawSlot() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return -1;
        int size = handler.slots.size();
        for (int i = 0; i < size - 36; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    /** 末影箱界面里还有没有空的背包槽位。 */
    private int findEmptyPlayerSlotInHandler() {
        ScreenHandler handler = containerHandler();
        if (handler == null) return -1;
        int size = handler.slots.size();
        for (int i = size - 36; i < size; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    /** 本趟结束（卸货完成）时清掉末影箱标记，下一趟重新来。 */
    private void resetEnderTrip() {
        enderPutDone = false;
        enderTakeDone = false;
        enderTarget = null;
        enderStateStart = 0;
        enderCloseCount = 0;
        enderOpenLogged = false;
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

    /**
     * 统一的“走过去”入口（V6.7p5）。
     *
     * <p>旧实现在自动搜索 / 单箱 / 存储箱等分支里<b>每 tick 裸调 moveTo</b>，Baritone 会每 tick
     * 重新计算路径（等于永远走不完），表现就是“人站着不动、箱子离远一点就永远点不开”。
     * 这里加守卫：同一目标正在走就不重复下发；走太久没到才重设，最多 2 次。
     */
    /**
     * 精确走到某个点（V6.7p8）。
     *
     * <p>和 {@link #pathTo} 的区别：这里用 {@code moveToPrecise}（站位要准，比如柱子旁的岸边格），
     * 并且**目标一变就先停掉旧路径**——否则「刚走完珍珠箱、又要走去柱子」时会继续沿着上一条路跑。
     * 同一目标不重复下发，Baritone 自己停下来时隔 20 tick 再试。
     */
    private void walkToPrecise(BlockPos pos) {
        if (pos == null || pos.equals(BlockPos.ORIGIN)) return;
        if (!pos.equals(pathTarget)) {
            pathTarget = pos;
            // 这条路径不走 pathTo，目标类型标记清零：之后同坐标若要改用 pathTo 的两种目标，
            // 必须能重新下发一次（否则会被「同目标不重复下发」挡住）。
            pathGoal = null;
            pathStartTick = tick;
            pathToSince = tick;
            pathRetries = 0;
            if (PathManagers.get().isPathing()) PathManagers.get().stop();
            PathManagers.get().moveToPrecise(pos);
            return;
        }
        // 同一个目标走太久还没到 → 别再无限重试，直接报出来
        if (tick - pathToSince > 400) {
            enterStuck("走位超时（20 秒没到），请检查路是否被堵");
            return;
        }
        if (!PathManagers.get().isPathing() && tick - pathStartTick > 20) {
            pathStartTick = tick;
            PathManagers.get().moveToPrecise(pos);
        }
    }

    /** 清掉「当前寻路目标」（连同它的目标类型一起清，避免下一次同坐标沿用旧类型）。 */
    private void clearPathTarget() {
        pathTarget = null;
        pathGoal = null;
        resetApproachTimer();
    }

    /**
     * 寻路目标的两种口径（V6.7p25）。
     *
     * <ul>
     *   <li>{@link #PRECISE}：GoalNear(pos, 0)，脚必须落在目标格（近处用，站得准）；</li>
     *   <li>{@link #NEAR}：GoalNear(pos, 1)，3D 距离 1 格内（远处用，容错高）。</li>
     * </ul>
     *
     * <p>⚠️ 两个都必须让 Y 参与，并且目标永远是**箱子旁边的空气格**，不是箱子本身 ——
     * 这是「不会走到箱子正上方」的根本保证。
     */
    private enum PathGoal { PRECISE, NEAR }

    private void applyGoal(BlockPos pos, PathGoal goal) {
        if (goal == PathGoal.PRECISE) {
            PathManagers.get().moveToPrecise(pos);
        } else {
            PathManagers.get().moveToNear(pos, 1);
        }
    }

    private void pathTo(BlockPos pos, PathGoal goal) {
        if (pos == null || pos.equals(BlockPos.ORIGIN)) return;
        // 目标坐标或「目标类型」任一变化都要重新下发：否则同坐标换目标类型时，
        // 会被下面这条「同目标不重复下发」的守卫直接吞掉。
        if (!pos.equals(pathTarget) || pathGoal != goal) {
            pathTarget = pos;
            pathGoal = goal;
            pathStartTick = tick;
            pathRetries = 0;
            if (PathManagers.get().isPathing()) PathManagers.get().stop();
            applyGoal(pos, goal);
            return;
        }
        if (PathManagers.get().isPathing()) {
            // 走太久还没到（可能被卡住/目标不可达）→ 重设目标
            if (tick - pathStartTick > 240 && pathRetries < 2) {
                pathRetries++;
                pathStartTick = tick;
                PathManagers.get().stop();
                applyGoal(pos, goal);
                debugMsg("寻路超时，重设目标（第 " + pathRetries + " 次）");
            }
            return;
        }
        // Baritone 自己停下了 → 隔 20 tick 再试一次，避免每 tick 重算
        if (tick - pathStartTick > 20) {
            pathStartTick = tick;
            applyGoal(pos, goal);
        }
    }

    /**
     * 眼睛到方块**包围盒**的距离（V6.7p28，与服务端同一口径）。
     *
     * <p>服务端 {@code PlayerEntity.canInteractWithBlockAt} 用的是
     * {@code new Box(blockPos).squaredMagnitude(eyePos)}，不是「到方块中心」——
     * 角落/斜向时两者能差 1 格以上，按中心算会误判成「够得着」。
     */
    private double eyeBoxDistance(BlockPos pos) {
        if (mc.player == null || pos == null) return Double.MAX_VALUE;
        return Math.sqrt(new Box(pos).squaredMagnitude(mc.player.getEyePos()));
    }

    /** 站在某个落脚点上时，眼睛到箱子包围盒的距离（选落脚点时预筛用）。 */
    private double eyeBoxDistanceAt(BlockPos feet, BlockPos chest) {
        Vec3d eye = new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
        return Math.sqrt(new Box(chest).squaredMagnitude(eye));
    }

    /** 现在这一格够不够得着（眼睛到包围盒 ≤ 「开箱距离」设置）。 */
    private boolean canOpenFromHere(BlockPos pos) {
        return eyeBoxDistance(pos) <= openReach.get();
    }

    /**
     * 在箱子周围找「站过去就能开」的落脚点（V6.7p25）。
     *
     * <p>候选由 {@link StashChestSpotLogic} 生成（搬运专用，不复用挖沙那套）：
     * 水平 1~3 圈 × {chestY, chestY+1}，**排除箱子自身那一列**，所以永远不会出现「站箱子上」。
     * 排序：有视线 &gt; 离得近 &gt; 同层。
     *
     * <p>结果按箱子缓存；落脚点不可站了会重算；四周确实没有可站格时记进
     * {@link #chestNoSpot}，本次会话不再尝试这个箱子。
     *
     * @return 落脚点；四周 3 格内没有可站格时返回 null
     */
    private ChestApproach findChestApproach(BlockPos chest) {
        if (mc.world == null || mc.player == null || chest == null) return null;
        // 区块没加载 / 箱子已经没了：方块会长成空气，这时候算出来的「没落脚点」是假结论，
        // 绝不能记进 chestNoSpot（否则远距离补珍珠时会把珍珠箱永久拉黑）。
        var chestState = mc.world.getBlockState(chest);
        if (!(chestState.getBlock() instanceof ChestBlock)
            && !(chestState.getBlock() instanceof ShulkerBoxBlock)
            && !(chestState.getBlock() instanceof EnderChestBlock)) {
            return null;
        }
        if (chestNoSpot.contains(chest)) return null;
        ChestApproach cached = chestApproachCache.get(chest);
        if (cached != null && isStandable(cached.spot()) && !isSpotFailed(chest, cached.spot())) return cached;

        BlockPos partner = chestPartner(chest);

        // V6.7p33：候选 = 箱子周围 1~3 圈 + **玩家当前格的水平邻格**。
        // 邻格解决「就差旁边挪一格」：站得稍远时，隔壁那格在箱子 3 圈之外，根本不在旧候选里。
        List<int[]> cands = new ArrayList<>(StashChestSpotLogic.candidates(chest.getX(), chest.getY(), chest.getZ()));
        BlockPos feet = mc.player.getBlockPos();
        for (Direction h : HORIZONTAL_DIRS) {
            BlockPos n = feet.offset(h);
            cands.add(new int[]{n.getX(), n.getY(), n.getZ()});
        }

        // 两遍：第一遍跳过「最近失败过」的格；全被失败记录盖住就清掉重选（允许重新逐个再试一轮）
        for (int pass = 0; pass < 2; pass++) {
            ChestApproach best = null;
            double bestScore = Double.MAX_VALUE;
            for (int[] c : cands) {
                BlockPos p = new BlockPos(c[0], c[1], c[2]);
                if (pass == 0 && isSpotFailed(chest, p)) continue;
                if (!isStandable(p)) continue;
                // 站过去必须真的够得着（V6.7p28：按包围盒距离预筛，否则选到 2 格外的点会永远开不了）
                if (eyeBoxDistanceAt(p, chest) > openReach.get()) continue;
                boolean los = hasLineOfSight(p, chest, partner);
                double distSq = mc.player.getBlockPos().getSquaredDistance(p);
                double score = StashChestSpotLogic.score(los, distSq, c[1] - chest.getY());
                if (score < bestScore) {
                    bestScore = score;
                    best = new ChestApproach(p, los);
                }
            }
            if (best != null) {
                chestApproachCache.put(chest, best);
                // 只打相对距离，不打坐标（NoCoordinatesInLogsTest 守卫：日志语句里不得出现坐标表达式）
                double dist = Math.hypot(mc.player.getX() - (best.spot().getX() + 0.5),
                    mc.player.getZ() - (best.spot().getZ() + 0.5));
                debugMsg("[接近] 落脚点" + (best.los() ? "有视线" : "无视线（发包开箱）")
                    + "，水平距 " + String.format(Locale.ROOT, "%.1f", dist) + " 格");
                return best;
            }
            if (pass == 0 && clearFailedSpots(chest)) {
                debugMsg("[接近] 周围格子都试过一轮，重新逐个再试");
                continue;
            }
            break;
        }
        chestNoSpot.add(chest);
        chestApproachCache.remove(chest);
        debugMsg("[接近] 箱子四周 3 格内没有可站的格子 → 跳过（绝不站到箱子正上方）");
        return null;
    }

    /** 这个落脚点是不是「最近试过失败」（{@link #SPOT_RETRY_AFTER_TICKS} 后自动过期，顺带清过期记录）。 */
    private boolean isSpotFailed(BlockPos chest, BlockPos spot) {
        LinkedHashMap<BlockPos, Long> failed = chestFailedSpots.get(chest);
        if (failed == null) return false;
        failed.values().removeIf(t -> tick - t > SPOT_RETRY_AFTER_TICKS);
        return failed.containsKey(spot);
    }

    /** 候选全被失败记录盖住时清空该箱子的失败记录，允许重新逐个再试一轮。@return 确实清过才 true */
    private boolean clearFailedSpots(BlockPos chest) {
        LinkedHashMap<BlockPos, Long> failed = chestFailedSpots.get(chest);
        if (failed == null || failed.isEmpty()) return false;
        failed.clear();
        return true;
    }

    /** 把一个落脚点记为「失败」（V6.7p33 换格机制）：缓存作废，下次选择跳过它。 */
    private void markSpotFailed(BlockPos chest, BlockPos spot, String reason) {
        if (chest == null || spot == null) return;
        chestFailedSpots.computeIfAbsent(chest, k -> new LinkedHashMap<>()).put(spot.toImmutable(), tick);
        chestApproachCache.remove(chest);
        debugMsg("[接近] 落脚点不行（" + reason + "）→ 换下一格");
    }


    /**
     * 从某个落脚点能不能直视箱子（V6.7p25）。
     *
     * <p>用脚位置 + 1.62 模拟眼睛高度，往箱子中心打一条 {@code COLLIDER} 射线；
     * 首个命中的方块是箱子（或双箱另一半）才算有视线。箱子夹在墙里、只露出半格时这里会判 false，
     * 但那种情况依然可以用发包开，所以不是硬门槛。
     */
    private boolean hasLineOfSight(BlockPos spot, BlockPos chest, BlockPos partner) {
        if (mc.world == null || mc.player == null) return false;
        Vec3d from = new Vec3d(spot.getX() + 0.5, spot.getY() + 1.62, spot.getZ() + 0.5);
        Vec3d to = Vec3d.ofCenter(chest);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit == null) return false;
        BlockPos hp = hit.getBlockPos();
        return hp.equals(chest) || (partner != null && hp.equals(partner));
    }

    /** 停掉「靠近箱子」这条寻路（避免一边走一边点箱子）。 */
    private void stopApproachPath() {
        if (PathManagers.get().isPathing()) PathManagers.get().stop();
        releaseMovementKeys();
        clearPathTarget();
    }

    private void resetApproachTimer() {
        approachChest = null;
        approachStartTick = 0;
    }

    /**
     * 走近某个「可交互方块」并返回能不能点——只用于<b>活板门</b>这类非容器目标（V6.7p25）。
     *
     * <p>箱子不要用这个（箱子要走 {@link #approachChest}：找箱子旁落脚点，绝不站箱子上）；
     * 活板门本来就要贴到它旁边/上面点，GoalNear(方块,1) 的语义正好合适。
     */
    private boolean approachForOpen(BlockPos pos) {
        if (pos == null || pos.equals(BlockPos.ORIGIN) || mc.player == null) return false;
        if (canOpenFromHere(pos)) {
            stopApproachPath();
            return true;
        }
        pathTo(pos, PathGoal.NEAR);
        return false;
    }

    /**
     * 靠近并打开箱子（V6.7p25）。
     *
     * <p>能开就开；要走路就走到能开为止；四周没有可站格时：
     * 单箱模式已经在 {@link #approachChest} 里报警暂停，搜索/框选模式则把这个箱子拉黑换下一个。
     *
     * @return true = 本 tick 已经把开箱动作发出去
     */
    private boolean goOpenChest(BlockPos chest) {
        return goOpenChest(chest, true);
    }

    /**
     * 靠近并打开箱子 / 末影箱（V6.7p27 增加 critical）。
     *
     * @param critical true = 打不开就报警暂停（存储箱/珍珠箱/单箱目标）；
     *                 false = 打不开只提示、由调用方降级（末影箱：跳过本趟中转）
     */
    private boolean goOpenChest(BlockPos chest, boolean critical) {
        if (chest == null || chest.equals(BlockPos.ORIGIN)) return false;
        Approach a = approachChest(chest);
        if (a == Approach.IN_RANGE) {
            openChestAt(chest);
            return true;
        }
        if (a == Approach.NO_SPOT) {
            // 队列箱子（搜索/框选）：跳过换下一个；固定目标：按 critical 决定报警还是降级
            if (chestQueue.contains(chest)) {
                blacklistChest(chest);
                openChest = null;
                debugMsg("[接近] 已跳过这个箱子（四周没有落脚点）");
            } else if (critical) {
                enterStuck("箱子周围 3 格内没有可站的格子（不会站到箱子正上方），请检查结构");
            } else {
                warnThrottled("末影箱旁边没有可站的格子，本趟跳过末影箱中转");
            }
        }
        return false;
    }

    /**
     * 开箱前靠近（V6.7p25 重写）。
     *
     * <p>用户口径：
     * <ol>
     *   <li><b>当前位置能开就直接开</b> —— 不寻路、不做多余动作；</li>
     *   <li>要寻路的话，<b>走到能开就停</b>（每 tick 先判距离，够了立刻停路 + 开箱）；</li>
     *   <li><b>绝不走到箱子正上方</b> —— 目标是箱子旁边的空气格，不是箱子本身。</li>
     * </ol>
     *
     * <p>旧实现（p22/p23）把箱子本身当寻路目标：GoalXZ 会为了凑 XZ 往下挖，
     * GoalNear(箱子,1) 又把「站在箱子正上方」算成到达 —— 两条都不符合用户口径，这里一并去掉。
     */
    private Approach approachChest(BlockPos pos) {
        if (pos == null || pos.equals(BlockPos.ORIGIN) || mc.player == null) return Approach.NO_SPOT;

        // 点了几次没开（V6.7p28 触发，V6.7p33 改动作）：不再「往最近的格挤」——那大概率还是同一格，
        // Baritone 认为目标格已到达就会原地不动（实机死锁：站在缓存格里贴边，眼距差半格开不了）。
        // 现在把当前落脚点记为失败 → 重新选格；候选里有「玩家邻格」，通常就是旁边挪一格。
        // 40 tick 节流：一次失败只换一次格。
        int tries = pos.equals(openChest) ? openAttempts : 0;
        if (tries >= 2 && tick - lastSpotRotateTick > 40) {
            ChestApproach cur = chestApproachCache.get(pos);
            if (cur != null) {
                lastSpotRotateTick = tick;
                approachStartTick = tick;
                markSpotFailed(pos, cur.spot(), "点了 " + tries + " 次没开");
            }
        }

        // ① 已经在可点击距离内：停路 + 松键，直接开
        if (canOpenFromHere(pos)) {
            stopApproachPath();
            return Approach.IN_RANGE;
        }

        // ② 箱子所在区块没加载 / 方块已经不在了：**不能判成「没有落脚点」**
        //    （否则会把存储箱/珍珠箱永久拉黑），原地等它加载/等人工处理。
        //    ⚠️ V6.7p29：这里必须把末影箱也算进来。p27 加末影箱时漏了这一处，
        //    导致「末影箱在 2 格外（不在开箱距离内）」时直接返回 MOVING、根本不寻路，
        //    人就站在原地等 15 秒超时 —— 实机表现就是「检测到了但不打开」。
        var posState = mc.world.getBlockState(pos);
        if (!(posState.getBlock() instanceof ChestBlock)
            && !(posState.getBlock() instanceof ShulkerBoxBlock)
            && !(posState.getBlock() instanceof EnderChestBlock)) {
            return Approach.MOVING;
        }

        // ③ 找箱子旁边的落脚点（有视线优先）；找不到就报告，绝不往箱子正上方走
        ChestApproach approach = findChestApproach(pos);
        if (approach == null) {
            resetApproachTimer();
            return Approach.NO_SPOT;
        }

        if (!pos.equals(approachChest)) {
            approachChest = pos;
            approachStartTick = tick;
        } else if (tick - approachStartTick > APPROACH_TIMEOUT) {
            // 队列里的箱子（搜索/框选）走不到就换下一个；
            // 固定目标（存储箱/珍珠箱/单箱）不再原地干等 —— 作废当前落脚点换一格重走（V6.7p33）
            if (chestQueue.contains(pos)) {
                warning("走到箱子旁边超时（" + (APPROACH_TIMEOUT / 20) + " 秒），跳过这个箱子");
                resetApproachTimer();
                blacklistChest(pos);
                return Approach.NO_SPOT;
            }
            warning("走到箱子旁边超时（" + (APPROACH_TIMEOUT / 20) + " 秒），换一个落脚点重走");
            ChestApproach original = approach;
            markSpotFailed(pos, approach.spot(), "30 秒走不到");
            resetApproachTimer();
            approach = findChestApproach(pos);
            if (approach == null) approach = original;   // 理论上到不了：原点至少可站
        }

        // ④ 目标永远是落脚空气格；远处用 NEAR 容错，近处用 PRECISE 站准
        BlockPos spot = approach.spot();
        double distSq = mc.player.getBlockPos().getSquaredDistance(spot);
        pathTo(spot, distSq <= 64 ? PathGoal.PRECISE : PathGoal.NEAR);
        return Approach.MOVING;
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
                info("[开箱] 换目标，失败计数归零");
            }
        }
        openChest = pos;
        // 保险：调用方已经判过距离，这里再兜一次，避免别处漏判后隔空点击
        if (approachChest(pos) != Approach.IN_RANGE) return;
        if (openAttempts >= 8) {
            enterStuck("连续 8 次都打不开箱子（检查是否被方块挡住或位置填错）");
            return;
        }
        openAttempts++;
        Vec3d center = Vec3d.ofCenter(pos);
        debugMsg("[开箱] 点击第 " + openAttempts + " 次"
            + "（眼→箱体 " + String.format(Locale.ROOT, "%.2f", eyeBoxDistance(pos))
            + " / 上限 " + String.format(Locale.ROOT, "%.1f", openReach.get())
            + "，界面=" + (mc.currentScreen == null ? "无" : mc.currentScreen.getClass().getSimpleName()) + "）");
        realClick(pos);
        cooldown = 15;
        progress();
    }

    private BlockPos nextLootChest() {
        ChestMode m = mode.get() == Mode.TRIO ? sourceChestMode.get() : chestMode.get();
        return switch (m) {
            case SINGLE -> {
                BlockPos t = mode.get() == Mode.TRIO ? sourceTarget.get() : targetChest.get();
                if (t.equals(BlockPos.ORIGIN) || blacklist.contains(t) || isSkipped(t)) yield null;
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
            // V6.7p17：自家仓库永远不进待搬运队列（含双箱搭档另一半）——
            // 判区只管「走哪一段」，这条名单管「哪些箱子永远不能碰」，两件事互不依赖。
            chestQueue.removeIf(this::isStoreSideChest);
            if (chestQueue.isEmpty()) return null;
        }
        // 先跳掉前缀里的拉黑/临时跳过，保证后面的「看 4 个」不会越扫越长
        while (queueIndex < chestQueue.size()
            && (blacklist.contains(chestQueue.get(queueIndex)) || isSkipped(chestQueue.get(queueIndex)))) {
            queueIndex++;
        }
        // 再在同一批候选里**优先挑有视线的**（V6.7p25）。
        // 只看前 LOS_LOOKAHEAD 个：落脚点结果按箱子缓存，通常第一个就命中，不会每个 tick 重算。
        BlockPos firstAny = null;
        int looked = 0;
        for (int i = queueIndex; i < chestQueue.size() && looked < LOS_LOOKAHEAD; i++) {
            BlockPos p = chestQueue.get(i);
            if (blacklist.contains(p) || isSkipped(p)) continue;
            looked++;
            if (firstAny == null) firstAny = p;
            ChestApproach a = findChestApproach(p);
            if (a != null && a.los()) {
                queueIndex = i;      // 下次从这一个继续
                return p;
            }
        }
        return firstAny;
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
        BlockPos partner = chestPartner(pos);
        if (partner != null) blacklist.add(partner);
    }

    /**
     * 把「有物品、但不符合过滤条件」的箱子跳过一小段时间（V6.7p21）。
     *
     * <p>刻意<b>不</b>拉黑：拉黑会让它整趟都不再被看，即使之后补了潜影盒也搬不到；
     * 跳过 {@link #SKIP_TICKS} 后还会重试。
     */
    private void skipChest(BlockPos pos) {
        if (pos == null) return;
        skippedUntil.put(pos, tick + SKIP_TICKS);
        debugMsg("[取货] 箱内有物品但不符合过滤条件，跳过（" + (SKIP_TICKS / 20) + " 秒后可重试）");
    }

    /** 当前仍在跳过期内的箱子数量（顺便清掉过期项）。 */
    private int filteredCount() {
        if (skippedUntil.isEmpty()) return 0;
        skippedUntil.entrySet().removeIf(e -> tick >= e.getValue());
        return skippedUntil.size();
    }

    /** 这个箱子是否还在跳过期。过期即移除，允许重新打开。 */
    private boolean isSkipped(BlockPos pos) {
        if (pos == null) return false;
        Long until = skippedUntil.get(pos);
        if (until == null) return false;
        if (tick >= until) {
            skippedUntil.remove(pos);
            return false;
        }
        return true;
    }

    /**
     * 「箱子都被过滤条件挡掉」的定期说明（30 秒一条）。
     *
     * <p>顺带调 {@link #progress()}：这是「在等补货/等用户改设置」的正常状态，
     * 不该被 2 分钟看门狗当成卡死。
     */
    private void warnFiltered() {
        progress();
        if (tick - lastFilteredWarnTick < FILTERED_WARN_INTERVAL) return;
        lastFilteredWarnTick = tick;
        warning("待搬运箱区有 " + filteredCount() + " 个箱子不符合「只拿潜影盒」，已跳过（不会搬空它们）；"
            + "如果这不是你要的，请关掉「只拿潜影盒」");
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
            info("[投掷] 子步骤 " + prev + " → " + next + " " + THROW_SUB_NAMES[next]
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
