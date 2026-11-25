package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.VaultLootLogic;
import com.fo.addon.utils.VaultLootTarget;
import com.fo.addon.vault.VaultLootHooks;
import com.fo.addon.vault.VaultLootTool;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * FO 不祥宝库战利品定位 —— 按种子预测「全服第几次开」会出附魔金苹果/沉重核心。
 *
 * <p>原理（已实证）：试炼大厅宝库的 {@code reward_ominous} 战利品表带 {@code random_sequence}，
 * 随机序列是**全服共享的 Xoroshiro128++**、每开一个不祥宝库推进一次，所以「第 N 次开」的掉落
 * 只取决于种子和 N（与位置、玩家无关）。引擎用 SeedMapper 同源的 cubiomes loot 库编译。
 *
 * <p><b>检测逻辑完全照 SeedMapper</b>（{@code VaultCommand} + {@code ClientPacketListenerMixin}）：
 * 由 {@link VaultLootHooks} 监听方块更新/实体生成/实体数据三个网络包，
 * 宝库进入 {@code EJECTING} 开始收集，离开 {@code EJECTING} 且物品数据包都收齐后，
 * 按**到达顺序**取出物品、**整体反转**后与生成的战利品逐件比对（顺序敏感）。
 */
public class FOVaultLoot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> seed = sgGeneral.add(new StringSetting.Builder()
        .name("种子")
        .description("服务器世界种子（整数）。")
        .defaultValue("-7346913998703726680")
        .build());

    private final Setting<Integer> startOffset = sgGeneral.add(new IntSetting.Builder()
        .name("起始序号")
        .description("当前全服已开出的不祥宝库数量（0 = 还没开过）。自动检测成功后会更新成实际序号。")
        .defaultValue(0)
        .range(0, 1_000_000)
        .sliderRange(0, 10000)
        .build());

    private final Setting<Integer> scanLimit = sgGeneral.add(new IntSetting.Builder()
        .name("最大尝试次数")
        .description("检测时向后搜索多少次开箱（引擎 O2 下约 600 万次/秒，默认 100 万；不够可调到 5000 万）。")
        .defaultValue(5_000_000)
        .range(1, 50_000_000)
        .sliderRange(500_000, 50_000_000)
        .build());

    private final Setting<Integer> predictCount = sgGeneral.add(new IntSetting.Builder()
        .name("预判数量")
        .description("列出接下来几个会出目标物品的序号（1~20）。")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 20)
        .build());

    private final Setting<VaultLootTarget> target = sgGeneral.add(new EnumSetting.Builder<VaultLootTarget>()
        .name("目标物品")
        .description("要定位的战利品：附魔金苹果 / 沉重核心 / 两者都。")
        .defaultValue(VaultLootTarget.BOTH)
        .build());

    private final Setting<Boolean> autoDetect = sgGeneral.add(new BoolSetting.Builder()
        .name("自动检测")
        .description("开不祥宝库后，按弹出的物品（含顺序）多开缩圈，反推这是全服第几次开。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> openGap = sgGeneral.add(new IntSetting.Builder()
        .name("间隔上限")
        .description("按时间推算窗口的封顶次数（10 秒 30 次、20 秒 60 次、40 秒 120 次、60 秒 200 次，往后每 60 秒 +200，封顶 1000）。")
        .defaultValue(1000)
        .range(30, 1000)
        .sliderRange(30, 1000)
        .build());

    private volatile boolean busy;
    /** 当前候选 offset 集合（多开缩圈的核心状态；关掉再开模块会清空）。 */
    private final List<Integer> candidates = new ArrayList<>();
    private int captureCount;
    /** >0 表示上一次已初步定位，下一次开箱要做精准校验（只查 verifyOffset+1 .. verifyOffset+间隔上限）。 */
    private int verifyOffset;
    /** 上一次「已校验通过」的全服序号（锚点）；0 表示还没有。 */
    private int anchorOffset;
    /** 校验通过一次之后，后续开箱不再做验证，直接用时间窗口往后推。 */
    private boolean confirmed;
    /** 上一次开箱的时间（毫秒）与耗时（秒），用于算别人开了多少 / 平均速度。 */
    private long lastCaptureMillis;
    private long lastElapsedSeconds;

    public FOVaultLoot() {
        super(AddonTemplate.CATEGORY, "FO 不祥宝库战利品定位",
            "按种子预测不祥宝库战利品：附魔金苹果/沉重核心出现在全服第几次开；开箱后可用弹出物品（含顺序）反推当前是全服第几次。");
    }

    @Override
    public void onActivate() {
        if (autoDetect.get()) {
            VaultLootHooks.setListener(this::onCapture);
        } else {
            VaultLootHooks.clear();
        }
        long parsed = parseSeed();
        if (parsed == Long.MIN_VALUE) {
            info("种子不是合法整数：%s", seed.get());
            return;
        }
        predictAsync(parsed, startOffset.get());
    }

    @Override
    public void onDeactivate() {
        VaultLootHooks.clear();
        candidates.clear();
        captureCount = 0;
        verifyOffset = 0;
        anchorOffset = 0;
        confirmed = false;
        lastCaptureMillis = 0;
        lastElapsedSeconds = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // 开关在运行中切换时要同步监听器
        if (autoDetect.get()) {
            if (!VaultLootHooks.hasListener()) VaultLootHooks.setListener(this::onCapture);
        } else if (VaultLootHooks.hasListener()) {
            VaultLootHooks.clear();
        }
    }

    // ==================== 检测（照 SeedMapper） ====================

    private void onCapture(VaultLootHooks.Capture capture) {
        List<ItemStack> items = capture.items();
        if (items.isEmpty()) return;
        long parsed = parseSeed();
        if (parsed == Long.MIN_VALUE) {
            info("种子不是合法整数：%s", seed.get());
            return;
        }
        if (busy) {
            info("上一次还在计算，这次开箱先不处理；等结果出来后再开下一个。");
            return;
        }
        busy = true;
        // SeedMapper：ejectedItems.reversed() 后与生成列表逐项比对
        List<String> specParts = new ArrayList<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemStack stack = items.get(i);
            specParts.add(Registries.ITEM.getId(stack.getItem()) + ":" + stack.getCount());
        }
        final String spec = String.join(";", specParts);
        final boolean ominous = capture.ominous();
        final int scan = scanLimit.get();
        final int cap = Math.max(30, Math.min(1000, openGap.get()));

        // 距上次开箱的时间 → 推算窗口（用户口径）
        long now = System.currentTimeMillis();
        long elapsed = lastCaptureMillis == 0 ? 0 : Math.max(0, (now - lastCaptureMillis) / 1000);
        lastCaptureMillis = now;
        lastElapsedSeconds = elapsed;
        int delta = Math.min(cap, VaultLootLogic.windowForSeconds(elapsed));

        final int verifying = verifyOffset;
        final List<Integer> snapshot = new ArrayList<>(candidates);
        final int anchor = anchorOffset;
        captureCount++;
        if (verifying > 0) {
            info("第 %d 次开箱（%d 件）：校验上次定位（距上次 %d 秒，窗口 %d 次）…",
                captureCount, items.size(), elapsed, delta);
        } else if (!snapshot.isEmpty()) {
            info("第 %d 次开箱（%d 件）：用 %d 个候选缩圈（距上次 %d 秒，窗口 %d 次）…",
                captureCount, items.size(), snapshot.size(), elapsed, delta);
        } else if (anchor > 0) {
            info("第 %d 次开箱（%d 件）：从上次第 %d 次往后推（距上次 %d 秒，窗口 %d 次）…",
                captureCount, items.size(), anchor, elapsed, delta);
        } else {
            info("第 %d 次开箱（%d 件，%s）：正在 %d 次内找候选…",
                captureCount, items.size(), ominous ? "不祥宝库" : "普通宝库", scan);
        }

        new Thread(() -> {
            try {
                if (verifying > 0) {
                    int rangeEnd = (int) Math.min((long) verifying + delta, scan);
                    if (rangeEnd <= verifying) {
                        mc.execute(() -> {
                            busy = false;
                            info("校验跳过：最大尝试次数（%d）不够覆盖第 %d 次之后，请把它调大。", scan, verifying);
                        });
                        return;
                    }
                    List<Integer> kept = VaultLootTool.narrow(parsed, ominous, verifying, rangeEnd, delta, spec,
                        List.of(verifying), scanned -> mc.execute(() -> info("已扫描到第 %d 次…", scanned)));
                    mc.execute(() -> {
                        busy = false;
                        if (kept.isEmpty()) {
                            info("校验失败：第 %d 次之后 %d 次内没有匹配的掉落，之前的定位不可靠（撞车/间隔超限）。已重置，请重新开箱定位。",
                                verifying, delta);
                            verifyOffset = 0;
                            candidates.clear();
                        } else if (kept.size() == 1) {
                            onVerified(parsed, verifying, kept.get(0));
                        } else {
                            info("校验发现 %d 个可能（掉落太短），继续开一个缩小：%s", kept.size(), kept);
                            candidates.clear();
                            candidates.addAll(kept);
                            verifyOffset = 0;
                        }
                    });
                } else if (!snapshot.isEmpty()) {
                    int minCandidate = Integer.MAX_VALUE;
                    int maxCandidate = 0;
                    for (Integer candidate : snapshot) {
                        minCandidate = Math.min(minCandidate, candidate);
                        maxCandidate = Math.max(maxCandidate, candidate);
                    }
                    int rangeStart = Math.max(0, minCandidate);
                    int rangeEnd = (int) Math.min((long) maxCandidate + delta, scan);
                    List<Integer> kept = VaultLootTool.narrow(parsed, ominous, rangeStart, rangeEnd, delta, spec, snapshot,
                        scanned -> mc.execute(() -> info("已扫描到第 %d 次…", scanned)));
                    mc.execute(() -> {
                        busy = false;
                        candidates.clear();
                        candidates.addAll(kept);
                        if (kept.isEmpty()) {
                            info("候选全部排除（可能中间别人开过、间隔超过 %d 次，或掉落表版本不同）。已清空候选，请把下一次开箱当作第 1 次。", delta);
                        } else if (kept.size() == 1) {
                            beginVerify(parsed, kept.get(0), scan);
                        } else {
                            info("候选 %d 个（第 %d 次开箱后）。继续开不祥宝库，直到只剩 1 个。", kept.size(), captureCount);
                        }
                    });
                } else if (anchor > 0) {
                    // 已有一个校验过的锚点：直接按时间窗口往后推
                    int rangeEnd = (int) Math.min((long) anchor + delta, scan);
                    List<Integer> kept = VaultLootTool.narrow(parsed, ominous, anchor, rangeEnd, delta, spec,
                        List.of(anchor), scanned -> mc.execute(() -> info("已扫描到第 %d 次…", scanned)));
                    if (!kept.isEmpty()) {
                        mc.execute(() -> {
                            busy = false;
                            if (kept.size() == 1) {
                                onAdvance(parsed, kept.get(0), scan);
                            } else {
                                info("按时间窗口找到 %d 个可能，继续开一个缩小：%s", kept.size(), kept);
                                candidates.clear();
                                candidates.addAll(kept);
                            }
                        });
                        return;
                    }
                    // 窗口内没有 → 告诉用户，然后退回全范围找候选
                    mc.execute(() -> info("时间太长：别人已经开了 %d 次（超过窗口上限），已重新全范围找候选。", delta));
                    List<Integer> found = VaultLootTool.matches(parsed, ominous, scan, spec,
                        scanned -> mc.execute(() -> info("已扫描到第 %d 次…", scanned)));
                    mc.execute(() -> {
                        busy = false;
                        candidates.clear();
                        candidates.addAll(found);
                        if (found.isEmpty()) {
                            info("没有候选：这 %d 次里没有一组掉落和本次一致。", scan);
                        } else if (found.size() == 1) {
                            if (confirmed) {
                                onAdvance(parsed, found.get(0), scan);
                            } else {
                                beginVerify(parsed, found.get(0), scan);
                            }
                        } else {
                            info("候选 %d 个（重新全范围）。请再开一个不祥宝库继续缩小。", found.size());
                        }
                    });
                } else {
                    List<Integer> found = VaultLootTool.matches(parsed, ominous, scan, spec,
                        scanned -> mc.execute(() -> info("已扫描到第 %d 次…", scanned)));
                    mc.execute(() -> {
                        busy = false;
                        candidates.clear();
                        candidates.addAll(found);
                        if (found.isEmpty()) {
                            info("没有候选：这 %d 次里没有一组掉落和本次一致（范围不够就调大「最大尝试次数」）。", scan);
                        } else if (found.size() == 1) {
                            beginVerify(parsed, found.get(0), scan);
                        } else {
                            info("候选 %d 个（第 1 次开箱）。请再开一个不祥宝库，我按时间窗口继续缩小。", found.size());
                        }
                    });
                }
            } catch (Exception exception) {
                busy = false;
                mc.execute(() -> info("战利品引擎调用失败：%s", exception.getMessage()));
            }
        }, "FO-VaultLoot-Narrow").start();
    }

    /** 缩圈到唯一候选后，先只报「初步定位」，等下一次开箱做精准校验。 */
    private void beginVerify(long parsed, int offset, int scan) {
        candidates.clear();
        verifyOffset = offset;
        startOffset.set(offset);
        int end = (int) Math.min((long) offset + Math.max(30, Math.min(1000, VaultLootLogic.windowForSeconds(10))), scan);
        info("初步定位：全服第 %d 次开的不祥宝库。请再开一个宝库做精准校验（按时间窗口查第 %d ~ %d 次）。",
            offset, offset + 1, end);
    }

    /** 校验通过：标记已确认（之后不再验证），报统计 + 下一个的距离。 */
    private void onVerified(long parsed, int verifying, int offset) {
        confirmed = true;
        info("校验通过：当前是全服第 %d 次开的不祥宝库。", offset);
        reportAdvance(parsed, offset);
    }

    /** 已确认后的开箱：不再验证，直接报间隔统计 + 下一个的距离。 */
    private void onAdvance(long parsed, int offset, int scan) {
        int previous = anchorOffset;
        if (previous > 0 && offset > previous) {
            info("定位：全服第 %d 次开的不祥宝库（第 %d 次之后开的）。", offset, previous);
            reportAdvance(parsed, offset);
        } else {
            reportAdvance(parsed, offset);
        }
    }

    private void reportAdvance(long parsed, int offset) {
        int previous = anchorOffset;
        long elapsed = lastElapsedSeconds;
        anchorOffset = offset;
        verifyOffset = 0;
        candidates.clear();
        startOffset.set(offset);
        if (previous > 0 && offset > previous) {
            info("%s", VaultLootLogic.formatGapReport(previous, offset, elapsed));
        }
        predictAsync(parsed, offset);
    }

    // ==================== 预测 ====================

    private void predictAsync(long parsedSeed, int from) {
        if (busy) return;
        busy = true;
        int scan = scanLimit.get();
        int amount = predictCount.get();
        VaultLootTarget wanted = target.get();
        new Thread(() -> {
            try {
                List<VaultLootLogic.Hit> hits = VaultLootTool.search(parsedSeed, true, from, scan, amount, wanted.searchSpec());
                List<VaultLootLogic.Occurrence> top = VaultLootLogic.firstN(VaultLootLogic.occurrencesFromHits(hits), amount);
                mc.execute(() -> {
                    info("战利品定位：种子 %d，从全服第 %d 次之后开始找 %s", parsedSeed, from, wanted);
                    if (top.isEmpty()) {
                        info("接下来 %d 次开箱里没有出现 %s（可调大「最大尝试次数」）。", scan, wanted);
                    } else {
                        VaultLootLogic.Occurrence first = top.get(0);
                        info("下一个%s：第 %d 次（还差 %d 次）",
                            VaultLootLogic.itemLabel(first.id()), first.offset(), first.offset() - from);
                        for (VaultLootLogic.Occurrence occurrence : top) {
                            info("  %s", VaultLootLogic.formatOccurrence(from, occurrence));
                        }
                    }
                    busy = false;
                });
            } catch (Exception exception) {
                busy = false;
                mc.execute(() -> {
                    info("战利品引擎调用失败：%s", exception.getMessage());
                });
            }
        }, "FO-VaultLoot-Predict").start();
    }

    private long parseSeed() {
        try {
            return Long.parseLong(seed.get().trim());
        } catch (NumberFormatException exception) {
            return Long.MIN_VALUE;
        }
    }
}
