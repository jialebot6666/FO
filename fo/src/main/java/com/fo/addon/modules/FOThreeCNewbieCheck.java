package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.threec.ThreeCPlayerService;
import com.fo.addon.utils.threec.ThreeCQueryQueue;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FO 3C 萌新判断。
 *
 * <p>移植自 Tanghulu（https://github.com/Tanghulu2019-a/Tanghulu）的 {@code ThreeCNewbieCheck}（GPLv3）。
 * 数据来源 3c3u.info，按累计游玩时长判断：低于阈值显示绿色“萌新”，否则红色“糕手”。
 * 名字标签渲染由 {@code ThreeCNametagsMixin} 完成。
 */
public class FOThreeCNewbieCheck extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgManual = settings.createGroup("手动查询");

    private final Setting<Boolean> showOnNametags = sgGeneral.add(new BoolSetting.Builder()
        .name("名字标签显示")
        .description("在玩家名字后面追加“萌新 / 糕手”标记。")
        .defaultValue(true)
        .build());

    private final Setting<Integer> thresholdHours = sgGeneral.add(new IntSetting.Builder()
        .name("萌新阈值")
        .description("累计游玩时长低于该小时数判为萌新。")
        .defaultValue(24)
        .min(1).max(1000)
        .build());

    private final Setting<Integer> autoQueryRange = sgGeneral.add(new IntSetting.Builder()
        .name("自动查询范围")
        .description("该距离内的玩家自动查询；0 表示关闭自动查询。")
        .defaultValue(200)
        .min(0).max(2000)
        .build());

    private final Setting<Boolean> btnQuerySelf = sgGeneral.add(new BoolSetting.Builder()
        .name("查询自己")
        .description("立即查询自己的游玩时长。")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) onQuerySelf();
        })
        .build());

    private final Setting<String> manualName = sgManual.add(new StringSetting.Builder()
        .name("玩家名")
        .description("要查询的玩家名（3–16 位）。")
        .defaultValue("")
        .build());

    private final Setting<Boolean> btnQueryName = sgManual.add(new BoolSetting.Builder()
        .name("查询该玩家")
        .description("按上面的玩家名查询一次。")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) onQueryName();
        })
        .build());

    private final ConcurrentHashMap<String, ThreeCPlayerService.PlayerTime> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean selfToggle = new AtomicBoolean(false);
    private final AtomicBoolean nameToggle = new AtomicBoolean(false);
    private final AtomicInteger tickCounter = new AtomicInteger();

    private volatile ThreeCQueryQueue queue;

    public FOThreeCNewbieCheck() {
        super(AddonTemplate.CATEGORY, "FO 3C萌新判断",
            "查询 3c3u.info 游玩时长，在名字标签标记萌新 / 糕手。");
    }

    @Override
    public void onActivate() {
        closeQueue();
        cache.clear();
        queue = new ThreeCQueryQueue(ThreeCPlayerService::query, new ThreeCQueryQueue.Listener() {
            @Override
            public void success(ThreeCPlayerService.PlayerTime data) {
                cache.put(ThreeCQueryQueue.key(data.name()), data);
            }

            @Override
            public void retry(String name, long attempt, String error) {
                if (attempt <= 3) {
                    mc.execute(() -> warning("3C 查询 " + name + " 失败（第 " + attempt + " 次）：" + error + "，稍后重试"));
                }
            }
        }, 2000, 5000);
    }

    @Override
    public void onDeactivate() {
        closeQueue();
        cache.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        ThreeCQueryQueue q = queue;
        if (q == null || mc.player == null || mc.world == null) return;
        int range = autoQueryRange.get();
        if (range <= 0) return;
        if (tickCounter.incrementAndGet() % 20 != 0) return;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) > range) continue;
            String name = player.getName().getString();
            if (cache.containsKey(ThreeCQueryQueue.key(name))) continue;
            if (q.isQueued(name)) continue;
            try {
                q.enqueue(name, false);
            } catch (IllegalArgumentException ignored) {
                // 名字不符合接口规则时不查询
            }
        }
    }

    /** 供 mixin 读取：返回该玩家已缓存的时长数据，没有则 null。 */
    public ThreeCPlayerService.PlayerTime nametagResult(PlayerEntity player) {
        if (!showOnNametags.get() || player == null) return null;
        return cache.get(ThreeCQueryQueue.key(player.getName().getString()));
    }

    public boolean isNewbie(ThreeCPlayerService.PlayerTime data) {
        return data != null && data.isNewbie(thresholdHours.get());
    }

    private void onQuerySelf() {
        selfToggle.set(false);
        if (mc.player == null) {
            error("请先进入世界。");
            return;
        }
        queryManual(mc.player.getName().getString());
    }

    private void onQueryName() {
        nameToggle.set(false);
        queryManual(manualName.get());
    }

    private void queryManual(String name) {
        ThreeCQueryQueue q = queue;
        if (q == null) {
            error("模块尚未启动，请先启用模块。");
            return;
        }
        try {
            q.enqueue(name, true);
            info("已加入查询队列：" + name + "（每两次请求至少间隔 2 秒）");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    private void closeQueue() {
        ThreeCQueryQueue q = queue;
        if (q != null) {
            q.close();
            queue = null;
        }
    }
}
