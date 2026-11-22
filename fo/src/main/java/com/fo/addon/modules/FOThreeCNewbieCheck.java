package com.fo.addon.modules;

import com.fo.addon.AddonTemplate;
import com.fo.addon.utils.threec.ThreeCPlayerService;
import com.fo.addon.utils.threec.ThreeCCacheStore;
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

    private final Setting<Integer> cacheHours = sgGeneral.add(new IntSetting.Builder()
        .name("缓存有效期")
        .description("查询结果保留多少小时，0 表示永不过期。到期后会静默重查刷新，期间仍显示旧数据。")
        .defaultValue(24)
        .min(0).max(2160)
        .build());

    private final Setting<Boolean> btnClearCache = sgGeneral.add(new BoolSetting.Builder()
        .name("清空缓存")
        .description("删除所有已缓存的玩家时长，下次进入范围会重新查询。")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) onClearCache();
        })
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

    // 落盘缓存：开关模块、重启游戏都不丢，只有过期或手动清空才重查
    private final ThreeCCacheStore store = ThreeCCacheStore.load();
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
        queue = new ThreeCQueryQueue(ThreeCPlayerService::query, new ThreeCQueryQueue.Listener() {
            @Override
            public void success(ThreeCPlayerService.PlayerTime data) {
                store.put(ThreeCQueryQueue.key(data.name()),
                    new ThreeCCacheStore.Entry(data, System.currentTimeMillis()));
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
        store.saveIfDirty();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        ThreeCQueryQueue q = queue;
        if (q == null || mc.player == null || mc.world == null) return;
        int range = autoQueryRange.get();
        if (range <= 0) return;
        int tick = tickCounter.incrementAndGet();
        // 每 5 秒落盘一次，避免每查到一个就写文件
        if (tick % 100 == 0) store.saveIfDirty();
        if (tick % 20 != 0) return;
        long now = System.currentTimeMillis();
        long ttl = cacheHours.get();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) > range) continue;
            String name = player.getName().getString();
            ThreeCCacheStore.Entry cached = store.get(ThreeCQueryQueue.key(name));
            if (cached != null && !cached.isExpired(ttl, now)) continue;
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
        ThreeCCacheStore.Entry entry = store.get(ThreeCQueryQueue.key(player.getName().getString()));
        return entry == null ? null : entry.data();
    }

    public boolean isNewbie(ThreeCPlayerService.PlayerTime data) {
        return data != null && data.isNewbie(thresholdHours.get());
    }

    private void onClearCache() {
        btnClearCache.set(false);
        store.clear();
        store.saveIfDirty();
        info("3C 缓存已清空");
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
