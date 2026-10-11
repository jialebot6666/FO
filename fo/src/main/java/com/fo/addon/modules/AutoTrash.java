package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.TrashDefaults;
import com.fo.addon.utils.TrashLogic;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

/**
 * AutoTrash — 自动扔垃圾（V1，自研）。
 *
 * <p>参考 meteor-miku (GPL) 自动扔垃圾的设计思路自研实现，并增强为黑白名单模式：
 * 黑名单：列表中的物品自动丢弃；
 * 白名单：仅保留列表中的物品，其余全部丢弃（列表为空时保护性暂停，防止误清空背包）。
 *
 * <p>V4.38：丢弃改为 misaka 式批量——每个「丢弃间隔」扫一遍背包，把所有命中的堆一次性全扔
 * （此前每轮只扔一种，视觉上一个一个蹦）；新增「附魔书过滤」设置组（参考 xiaohe66-meteor-lotus
 * 自动分类按附魔等级分价值的思路自研）：附魔书可按等级阈值保留（如古城只留迅捷潜行3）、全部丢弃、
 * 全部保留或跟随名单。
 */
public class AutoTrash extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<TrashLogic.Mode> mode = sgGeneral.add(new EnumSetting.Builder<TrashLogic.Mode>()
        .name("名单模式")
        .description("名单模式：黑名单=只丢列表中的物品；白名单=只保留列表中的物品，其余全丢.")
        .defaultValue(TrashLogic.Mode.WHITELIST)
        .build());

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("物品列表")
        .description("物品列表.")
        .defaultValue(TrashDefaults.toItems())
        .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("丢弃间隔")
        .description("扫描间隔（tick）：每隔 N tick 扫一遍背包，把所有命中的堆一次性全扔（misaka 式批量）。0 = 每 tick 扫.")
        .defaultValue(20)
        .min(0)
        .sliderMin(0)
        .sliderMax(60)
        .build());

    private final Setting<Boolean> dropAll = sgGeneral.add(new BoolSetting.Builder()
        .name("丢弃整组")
        .description("丢弃整组物品；关闭时每次只丢一个.")
        .defaultValue(true)
        .build());

    private final SettingGroup sgBook = settings.createGroup("附魔书过滤");
    private final Setting<TrashLogic.BookMode> bookMode = sgBook.add(new EnumSetting.Builder<TrashLogic.BookMode>()
        .name("附魔书模式")
        .description("跟随名单=附魔书按普通物品走黑白名单；保留等级达标=附魔等级≥阈值的书保留、低于阈值的当垃圾；"
            + "全部丢弃/全部保留字面含义。古城的迅捷潜行书为 1~3 级，想只留满级书就选「保留等级达标的书」+ 阈值 3.")
        .defaultValue(TrashLogic.BookMode.FOLLOW_LIST)
        .build());
    private final Setting<Integer> bookLevel = sgBook.add(new IntSetting.Builder()
        .name("附魔等级阈值")
        .description("「保留等级达标的书」模式下：附魔书里的最高附魔等级 ≥ 该值才保留.")
        .defaultValue(3)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .build());

    private final Setting<Boolean> excludeHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("排除快捷栏")
        .description("不处理快捷栏中的物品（推荐开启，保护常用物品）.")
        .defaultValue(false)   // V4.22: 默认不启用——挖沙联动时快捷栏杂物也要丢（主手槽始终跳过）
        .build());

    private final Setting<Boolean> toggleOffOnClear = sgGeneral.add(new BoolSetting.Builder()
        .name("清空后自动关闭")
        .description("背包中无可丢弃物品后自动关闭模块.")
        .defaultValue(false)
        .build());

    private int tickTimer = 0;
    /** 空名单保护提示只发一次，防止每 tick 刷聊天栏。 */
    private boolean warnedEmptyList;

    /** V4.22: 联动来源标记——仅当由"FO 自动挖沙"联动开启时，才在挖沙停止时联动关闭；
     *  用户手动开的（标记为空）一律不关，避免误伤手动操作。 */
    private boolean linkedByMineSand = false;

    /** V4.30: 联动来源标记——仅当由"FO 自动挖矿"联动开启时，才在挖矿停止时联动关闭 */
    private boolean linkedByMining = false;

    public AutoTrash() {
        super(AddonTemplate.CATEGORY, "FO 自动扔垃圾", "自动扔垃圾：黑/白名单模式自动丢弃指定物品。");
    }

    /** 供 ElytraCollector 联动调用：强制白名单模式；列表非空则沿用用户配置，空才填默认列表；额外弹一次系统通知 */
    public void enableForElytraLink() {
        mode.set(TrashLogic.Mode.WHITELIST);
        boolean filledDefault = TrashDefaults.shouldFillDefault(items.get());
        if (filledDefault) {
            items.set(TrashDefaults.toItems());
        }
        if (!isActive()) toggle();

        // 额外弹一次系统通知 (Toast)，明确本次联动用的什么列表
        try {
            String text = filledDefault
                ? "物品列表为空，已重置为基础白名单 " + TrashDefaults.DEFAULT_WHITELIST_IDS.size() + " 项"
                : "沿用你的白名单列表（" + items.get().size() + " 项）";
            MeteorToast toast = new MeteorToast.Builder("已联动开启 FO 自动扔垃圾")
                .icon(Items.SHULKER_BOX)
                .text(text)
                .build();
            mc.getToastManager().add(toast);
        } catch (Exception ignored) {
            // 通知失败不影响联动主逻辑
        }
    }

    /** 供 ElytraCollector 联动回滚调用：仅关闭模块（不还原模式/列表） */
    public void disableForElytraLink() {
        if (isActive()) toggle();
    }

    /**
     * V4.22: 供 AutoMineSand 挖沙联动调用。强制白名单 + 确保列表含沙——
     * 白名单模式下列表缺沙会把沙一起丢掉（存沙永远攒不满），
     * 联动时缺沙自动补 minecraft:sand，其余项不动（尊重用户已有配置）。
     * 记录联动来源标记：挖沙停止时只关自己联动开的。
     */
    public void enableForMineSandLink() {
        mode.set(TrashLogic.Mode.WHITELIST);
        java.util.List<Item> list = new java.util.ArrayList<>(items.get());
        if (!list.contains(Items.SAND)) {
            list.add(Items.SAND);
            items.set(list);
        }
        linkedByMineSand = true;
        if (!isActive()) toggle();

        try {
            MeteorToast toast = new MeteorToast.Builder("已联动开启 FO 自动扔垃圾（自动挖沙）")
                .icon(Items.SHULKER_BOX)
                .text("白名单模式，已确保沙在保留列表（" + list.size() + " 项）")
                .build();
            mc.getToastManager().add(toast);
        } catch (Exception ignored) {
        }
    }

    /** V4.22: 挖沙联动回滚——仅当是挖沙联动开启的才关闭（用户手动开的不动） */
    public void disableForMineSandLink() {
        if (linkedByMineSand && isActive()) toggle();
        linkedByMineSand = false;
    }

    /**
     * V4.30: 供 AutoMining 自动挖矿联动调用。强制白名单 + 确保列表含石英（残骸模式挖石英
     * 修镐，白名单缺石英会把石英一起丢掉，镐子永远修不好）。石英块刻意不进名单——
     * 修镐逻辑会主动把合成出的石英块丢弃（参考 misaka AutoMining 行为）。
     * V4.37: 同时确保钻石/钻石块/远古残骸在保留列表——钻石模式合成出的钻石块若不在
     * 白名单会被联动扔垃圾丢掉，导致"背包没有可存储的钻石块"、存盒链中断。
     */
    public void enableForMineLink() {
        mode.set(TrashLogic.Mode.WHITELIST);
        java.util.List<Item> list = new java.util.ArrayList<>(items.get());
        // 挖掘产物与原料必须保留：石英（修镐）、钻石/钻石块（钻石模式存盒）、远古残骸（残骸模式存盒）
        for (Item keep : List.of(Items.QUARTZ, Items.DIAMOND, Items.DIAMOND_BLOCK, Items.ANCIENT_DEBRIS)) {
            if (!list.contains(keep)) {
                list.add(keep);
            }
        }
        items.set(list);
        linkedByMining = true;
        if (!isActive()) toggle();

        try {
            MeteorToast toast = new MeteorToast.Builder("已联动开启 FO 自动扔垃圾（自动挖矿）")
                .icon(Items.SHULKER_BOX)
                .text("白名单模式，已确保石英/钻石/钻石块/残骸在保留列表（" + list.size() + " 项）")
                .build();
            mc.getToastManager().add(toast);
        } catch (Exception ignored) {
        }
    }

    /** V4.30: 挖矿联动回滚——仅当是挖矿联动开启的才关闭（用户手动开的不动） */
    public void disableForMineLink() {
        if (linkedByMining && isActive()) toggle();
        linkedByMining = false;
    }

    /** V4.30: 供 AutoMining 合成界面丢垃圾调用——判定该物品是否属于"应丢弃的垃圾"（按当前名单模式） */
    public boolean isTrashItem(Item item) {
        List<Item> list = items.get();
        boolean listConfigured = !list.isEmpty();
        boolean inList = list.contains(item);
        return TrashLogic.shouldDrop(mode.get(), listConfigured, inList);
    }

    @Override
    public void onActivate() {
        tickTimer = 0;
        warnedEmptyList = false;
        // 启动时打印当前模式与列表数量，方便确认设置是否生效
        info("已启动 (模式: " + mode.get() + ", 列表物品数: " + items.get().size() + ").");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 防误丢：只在无界面时扫背包 —— 槽位映射（SlotUtils.indexToId）按玩家背包设计，
        // 开着箱子等界面时 currentScreenHandler 不同，槽位会错位，可能把界面里的东西丢错。
        if (mc.currentScreen != null) return;

        List<Item> list = items.get();
        boolean listConfigured = !list.isEmpty();
        TrashLogic.BookMode bookModeValue = bookMode.get();

        if (!listConfigured && bookModeValue == TrashLogic.BookMode.FOLLOW_LIST) {
            // 保护：列表为空时不丢任何物品（尤其白名单模式，防止清空背包）。
            // 不强制关闭模块——之前 toggle() 自动关会让用户误以为"设置被重置成黑名单"。
            // 提示只发一次（V4.38 前这里每 tick 刷聊天栏）。
            if (mode.get() == TrashLogic.Mode.WHITELIST && !warnedEmptyList) {
                warnedEmptyList = true;
                info("白名单模式但物品列表为空，未丢弃任何物品 (请在物品列表中添加要保留的物品).");
            }
            return;
        }

        if (tickTimer > 0) {
            tickTimer--;
            return;
        }

        // misaka 式批量：一轮扫描把所有命中的堆一次全扔（V4.38 前每轮只扔一种，眼花缭乱）
        int droppedCount = 0;
        int start = excludeHotbar.get() ? 9 : 0;
        int end = mc.player.getInventory().size();

        for (int i = start; i < end; i++) {
            // 保护：跳过当前主手槽，避免误丢正在使用的物品
            if (i == mc.player.getInventory().getSelectedSlot()) continue;

            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // 附魔书特判优先（V4.38）：非附魔书/跟随名单模式返回 null，回落普通名单逻辑
            Boolean bookDrop = TrashLogic.decideBook(
                bookModeValue,
                stack.getItem() == Items.ENCHANTED_BOOK,
                maxEnchantLevel(stack),
                bookLevel.get());
            boolean drop;
            if (bookDrop != null) {
                drop = bookDrop;
            } else if (!listConfigured) {
                continue; // 名单为空的保护（附魔书特判未命中时）
            } else {
                drop = TrashLogic.shouldDrop(mode.get(), listConfigured, list.contains(stack.getItem()));
            }

            if (drop) {
                dropItem(i);
                droppedCount++;
            }
        }

        if (droppedCount > 0) {
            tickTimer = delay.get();
        } else if (toggleOffOnClear.get()) {
            info("背包中已无垃圾物品，自动关闭.");
            toggle();
        }
    }

    /** 该堆物品里的最高附魔等级（非附魔书/无附魔返回 0）。 */
    private int maxEnchantLevel(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        int max = 0;
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
            max = Math.max(max, entry.getIntValue());
        }
        return max;
    }

    private void dropItem(int slot) {
        if (mc.interactionManager == null) return;

        int screenSlot = SlotUtils.indexToId(slot);
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            screenSlot,
            dropAll.get() ? 1 : 0, // 1=整组(Ctrl+Q)，0=单个(Q)
            SlotActionType.THROW,
            mc.player
        );
    }
}
