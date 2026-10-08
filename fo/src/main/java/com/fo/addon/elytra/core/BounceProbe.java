package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import com.fo.addon.elytra.core.ItemHelper;
import java.util.Locale;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class BounceProbe {
    private static final int SAMPLES = 240;
    private static final int DUMP_LINES = 60;
    private static final int STATUS_INTERVAL = 20;
    private static final int MARK_THROTTLE = 5;
    private static final int CLIENT_BOUNCE_COOLDOWN = 10;
    private static final int CORRECTION_WINDOW = 3;
    private static final String[] KEYWORDS = new String[]{"\u53cd\u4f5c\u5f0a", "anticheat", "anti-cheat", "setback", "\u56de\u5f39", "\u56de\u9000", "\u62e6\u622a", "\u62d2\u7edd", "invalid", "kick", "\u8e22\u51fa", "\u901f\u5ea6", "\u79fb\u52a8"};
    private static BounceProbe instance;
    private final Sample[] ring = new Sample[240];
    private int cursor;
    private int filled;
    private int tick;
    private int lastCorrectionTick = -1000;
    private int lastClientBounceTick = -1000;
    private int lastMarkTick = -1000;
    private double corrFromX;
    private double corrFromY;
    private double corrFromZ;
    private int corrPendingTick = -1000;

    private BounceProbe() {
    }

    public static synchronized void init() {
        if (instance != null) {
            return;
        }
        try {
            BounceProbe probe = new BounceProbe();
            MeteorClient.EVENT_BUS.subscribe((Object)probe);
            instance = probe;
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u5df2\u6302\u4e0a\u6536\u5305\u76d1\u542c\uff08\u4f4d\u7f6e/\u901f\u5ea6\u7ea0\u6b63\u5305\u4e0e\u804a\u5929\u5173\u952e\u8bcd\u90fd\u4f1a dump\uff09", new Object[0]);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("BounceProbe.init", t);
        }
    }

    public static synchronized void shutdown() {
        BounceProbe probe = instance;
        instance = null;
        if (probe == null) {
            return;
        }
        try {
            MeteorClient.EVENT_BUS.unsubscribe((Object)probe);
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u5df2\u5378\u8f7d\u6536\u5305\u76d1\u542c", new Object[0]);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("BounceProbe.shutdown", t);
        }
    }

    public static void tick(MinecraftClient mc, String stateName, boolean baritoneFlying) {
        try {
            BounceProbe.init();
            BounceProbe probe = instance;
            if (probe == null) {
                return;
            }
            if (mc == null || mc.player == null || mc.world == null) {
                return;
            }
            probe.sampleTick(mc, stateName, baritoneFlying);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("BounceProbe.tick", t);
        }
    }

    public static void mark(String event) {
        try {
            BounceProbe probe = instance;
            if (probe == null) {
                return;
            }
            probe.markEvent(event);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("BounceProbe.mark", t);
        }
    }

    @EventHandler
    public void onReceive(PacketEvent.Receive event) {
        try {
            if (event == null) {
                return;
            }
            this.handle(event.packet);
        }
        catch (Throwable t) {
            FOElytraLog.detailError("BounceProbe.onReceive", t);
        }
    }

    private void handle(Packet<?> packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (packet == null) {
            return;
        }
        if (packet instanceof PlayerPositionLookS2CPacket) {
            PlayerPositionLookS2CPacket look = (PlayerPositionLookS2CPacket)packet;
            this.correction("\u670d\u52a1\u7aef\u4f4d\u7f6e\u7ea0\u6b63 PlayerPositionLookS2CPacket\uff08\u5178\u578b setback\uff09", look.toString(), look.change(), mc);
            return;
        }
        if (packet instanceof EntityPositionS2CPacket) {
            EntityPositionS2CPacket ep = (EntityPositionS2CPacket)packet;
            if (BounceProbe.isSelf(mc, ep.entityId())) {
                this.correction("\u670d\u52a1\u7aef\u5f3a\u5236\u6539\u6211\u7684\u4f4d\u7f6e EntityPositionS2CPacket", ep.toString(), ep.change(), mc);
            }
            return;
        }
        if (packet instanceof EntityVelocityUpdateS2CPacket) {
            EntityVelocityUpdateS2CPacket ev = (EntityVelocityUpdateS2CPacket)packet;
            if (!BounceProbe.isSelf(mc, ev.getEntityId())) {
                return;
            }
            Vec3d v = ev.getVelocity();
            ClientPlayerEntity player = mc == null ? null : mc.player;
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u670d\u52a1\u7aef\u6539\u6211\u7684\u901f\u5ea6 EntityVelocityUpdateS2CPacket\uff5c\u76ee\u6807\u901f\u5ea6=(%.3f, %.3f, %.3f)\uff5c\u5f53\u524d\u901f\u5ea6=(%.3f, %.3f, %.3f)\uff5c\u5f53\u524d\u4f4d\u7f6e=(%.2f, %.2f, %.2f)", v.x, v.y, v.z, player == null ? 0.0 : player.getVelocity().x, player == null ? 0.0 : player.getVelocity().y, player == null ? 0.0 : player.getVelocity().z, player == null ? 0.0 : player.getX(), player == null ? 0.0 : player.getY(), player == null ? 0.0 : player.getZ());
            this.rememberCorrection((PlayerEntity)player);
            this.dumpSamples("\u670d\u52a1\u7aef\u901f\u5ea6\u7ea0\u6b63");
            this.lastCorrectionTick = this.tick;
            return;
        }
        if (packet instanceof EntityS2CPacket) {
            EntityS2CPacket es = (EntityS2CPacket)packet;
            if (mc == null || mc.world == null || mc.player == null) {
                return;
            }
            if (es.getEntity((World)mc.world) != mc.player) {
                return;
            }
            if (!es.isPositionChanged() && !es.hasRotation()) {
                return;
            }
            this.correction("\u670d\u52a1\u7aef\u5c0f\u5e45\u4fee\u6b63\u6211\u7684\u4f4d\u7f6e EntityS2CPacket\uff08delta " + es.getDeltaX() + "," + es.getDeltaY() + "," + es.getDeltaZ() + "\uff09", es.toString(), null, mc);
            return;
        }
        if (packet instanceof GameMessageS2CPacket) {
            GameMessageS2CPacket gm = (GameMessageS2CPacket)packet;
            String text = gm.content() == null ? "" : gm.content().getString();
            String hit = BounceProbe.keywordHit(text);
            if (hit == null) {
                return;
            }
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u804a\u5929\u547d\u4e2d\u5173\u952e\u8bcd\u300c%s\u300d\uff1a%s", hit, text);
            this.dumpSamples("\u804a\u5929\u5173\u952e\u8bcd\uff1a" + hit);
            this.lastCorrectionTick = this.tick;
        }
    }

    private void correction(String what, String raw, EntityPosition target, MinecraftClient mc) {
        ClientPlayerEntity player;
        ClientPlayerEntity clientPlayerEntity2 = player = mc == null ? null : mc.player;
        if (target != null && player != null) {
            Vec3d pos = target.position();
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a%s\uff5c\u76ee\u6807\u5750\u6807=(%.2f, %.2f, %.2f)\uff5c\u5f53\u524d\u5750\u6807=(%.2f, %.2f, %.2f)\uff5c\u0394=(%.2f, %.2f, %.2f)\uff5c\u76ee\u6807\u671d\u5411=(%.1f, %.1f)\uff5c\u5f53\u524d\u671d\u5411=(%.1f, %.1f)", what, pos.x, pos.y, pos.z, player.getX(), player.getY(), player.getZ(), pos.x - player.getX(), pos.y - player.getY(), pos.z - player.getZ(), Float.valueOf(target.yaw()), Float.valueOf(target.pitch()), Float.valueOf(player.getYaw()), Float.valueOf(player.getPitch()));
        } else {
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a%s\uff5c\u5f53\u524d\u5750\u6807=(%.2f, %.2f, %.2f)", what, player == null ? 0.0 : player.getX(), player == null ? 0.0 : player.getY(), player == null ? 0.0 : player.getZ());
        }
        FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u539f\u59cb\u5305\u5185\u5bb9 %s", raw);
        this.rememberCorrection((PlayerEntity)player);
        this.dumpSamples(what);
        this.lastCorrectionTick = this.tick;
    }

    private void rememberCorrection(PlayerEntity player) {
        if (player == null) {
            return;
        }
        this.corrFromX = player.getX();
        this.corrFromY = player.getY();
        this.corrFromZ = player.getZ();
        this.corrPendingTick = this.tick;
    }

    private void sampleTick(MinecraftClient mc, String stateName, boolean baritoneFlying) {
        ++this.tick;
        Sample prev = this.at(0);
        Sample now = new Sample(this.tick, mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getVelocity().x, mc.player.getVelocity().y, mc.player.getVelocity().z, mc.player.getYaw(), mc.player.getPitch(), mc.player.isGliding(), mc.player.isOnGround(), BounceProbe.heldName(mc), ItemHelper.countInHotbar((PlayerEntity)mc.player, Items.FIREWORK_ROCKET), ItemHelper.remainingDurability(ItemHelper.wornElytra((PlayerEntity)mc.player)), stateName == null ? "?" : stateName, baritoneFlying, mc.player.isInLava(), mc.player.getHealth());
        this.push(now);
        if (this.corrPendingTick == this.tick - 1) {
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u670d\u52a1\u7aef\u7ea0\u6b63\u540e\u7684\u5b9e\u9645\u4f4d\u79fb \u0394=(%.2f, %.2f, %.2f)\uff08\u4e0a\u4e00 tick \u6536\u5230\u7ea0\u6b63\u5305\uff09", now.x - this.corrFromX, now.y - this.corrFromY, now.z - this.corrFromZ);
        }
        if (prev != null && prev.hp > now.hp + 0.01f) {
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u6389\u8840 %.1f \u2192 %.1f\uff08\u0394 %.1f\uff09\uff5c\u4f4d\u7f6e=(%.2f, %.2f, %.2f)\uff5c\u901f\u5ea6=(%.3f, %.3f, %.3f)", Float.valueOf(prev.hp), Float.valueOf(now.hp), Float.valueOf(prev.hp - now.hp), now.x, now.y, now.z, now.vx, now.vy, now.vz);
            this.markEvent("\u6389\u8840");
        }
        this.checkClientBounce(prev, now);
        if (this.tick % 20 == 0) {
            this.statusLine(now);
        }
    }

    private void checkClientBounce(Sample prev, Sample now) {
        boolean reversed;
        if (prev == null) {
            return;
        }
        boolean recentCorrection = this.tick - this.lastCorrectionTick <= 3;
        double dot = prev.vx * now.vx + prev.vz * now.vz;
        double dv = Math.hypot(now.vx - prev.vx, now.vz - prev.vz);
        Sample old = this.at(1);
        boolean jumpedBack = false;
        double dist = 0.0;
        if (old != null) {
            double dx = now.x - old.x;
            double dz = now.z - old.z;
            dist = Math.hypot(dx, dz);
            jumpedBack = dist > 0.5 && dx * old.vx + dz * old.vz < 0.0;
        }
        boolean bl = reversed = dot < 0.0 && dv > 0.3;
        if (!reversed && !jumpedBack) {
            return;
        }
        if (recentCorrection) {
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u68c0\u6d4b\u5230\u4f4d\u79fb/\u901f\u5ea6\u53cd\u8f6c\uff0c\u4f46 %.0f tick \u5185\u6709\u670d\u52a1\u7aef\u7ea0\u6b63\u5305 \u2192 \u5f52\u56e0\u670d\u52a1\u7aef", this.tick - this.lastCorrectionTick);
            return;
        }
        if (this.tick - this.lastClientBounceTick < 10) {
            return;
        }
        this.lastClientBounceTick = this.tick;
        FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\uff1a\u7591\u4f3c\u5ba2\u6237\u7aef\u56de\u5f39\uff08\u6ca1\u6709\u670d\u52a1\u7aef\u7ea0\u6b63\u5305\uff09\uff5c\u901f\u5ea6\u53cd\u8f6c=%s\uff5c2 tick \u4f4d\u79fb=%.2f \u5012\u9000=%s\uff5c\u901f\u5ea6 (%.3f, %.3f, %.3f) \u2192 (%.3f, %.3f, %.3f)\uff5c\u4f4d\u7f6e \u0394=(%.2f, %.2f, %.2f)\uff5c\u72b6\u6001=%s Baritone=%s", reversed ? "\u662f" : "\u5426", dist, jumpedBack ? "\u662f" : "\u5426", prev.vx, prev.vy, prev.vz, now.vx, now.vy, now.vz, now.x - prev.x, now.y - prev.y, now.z - prev.z, now.state, now.baritone ? "\u63a5\u7ba1" : "\u672a\u63a5\u7ba1");
        this.dumpSamples("\u7591\u4f3c\u5ba2\u6237\u7aef\u56de\u5f39");
    }

    private void markEvent(String event) {
        String name = event == null ? "?" : event;
        Sample now = this.at(0);
        FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\u4e8b\u4ef6\uff1a%s\uff08tick %d%s\uff09", name, this.tick, now == null ? "" : String.format(Locale.ROOT, "\uff0c\u4f4d\u7f6e %.2f %.2f %.2f\uff0c\u901f\u5ea6 %.3f %.3f %.3f\uff0c\u72b6\u6001 %s", now.x, now.y, now.z, now.vx, now.vy, now.vz, now.state));
        if (this.tick - this.lastMarkTick < 5) {
            return;
        }
        this.lastMarkTick = this.tick;
        this.dumpSamples("\u4e8b\u4ef6 " + name);
    }

    private void statusLine(Sample s) {
        FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1\u72b6\u6001\uff1a\u4f4d\u7f6e=(%.2f, %.2f, %.2f) \u901f\u5ea6=(%.3f, %.3f, %.3f) \u671d\u5411=(%.1f, %.1f) %s%s \u624b\u6301=%s \u5feb\u6377\u680f\u70df\u82b1=%d \u9798\u7fc5\u8010\u4e45=%d \u72b6\u6001=%s Baritone=%s \u5ca9\u6d46=%s \u8840\u91cf=%.1f", s.x, s.y, s.z, s.vx, s.vy, s.vz, Float.valueOf(s.yaw), Float.valueOf(s.pitch), s.gliding ? "\u6ed1\u7fd4" : "\u4e0d\u6ed1\u7fd4", s.onGround ? " \u63a5\u5730" : " \u7a7a\u4e2d", s.held, s.fireworks, s.elytra, s.state, s.baritone ? "\u63a5\u7ba1" : "\u672a\u63a5\u7ba1", s.inLava ? "\u662f" : "\u5426", Float.valueOf(s.hp));
    }

    private void dumpSamples(String reason) {
        if (this.filled <= 0) {
            FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1 dump\uff08%s\uff09\uff1a\u8fd8\u6ca1\u6709\u91c7\u6837", reason);
            return;
        }
        int n = Math.min(60, this.filled);
        FOElytraLog.detail("\u56de\u5f39\u53d6\u8bc1 dump\uff08%s\uff09\uff1a\u6700\u8fd1 %d \u6761\u91c7\u6837\uff0ctick \u4ece\u65e7\u5230\u65b0", reason, n);
        for (int i = n - 1; i >= 0; --i) {
            Sample s = this.at(i);
            Sample p = this.at(i + 1);
            if (s == null) continue;
            String delta = p == null ? "" : String.format(Locale.ROOT, "\uff5c\u0394=(%.3f,%.3f,%.3f) \u0394v=(%.3f,%.3f,%.3f)", s.x - p.x, s.y - p.y, s.z - p.z, s.vx - p.vx, s.vy - p.vy, s.vz - p.vz);
            FOElytraLog.detail("  [%d] (%.2f,%.2f,%.2f) v=(%.3f,%.3f,%.3f) yaw=%.1f pitch=%.1f %s%s \u624b\u6301=%s \u70df\u82b1=%d \u9798\u7fc5=%d \u72b6\u6001=%s Baritone=%s \u5ca9\u6d46=%s \u8840=%.1f%s", s.tick, s.x, s.y, s.z, s.vx, s.vy, s.vz, Float.valueOf(s.yaw), Float.valueOf(s.pitch), s.gliding ? "\u6ed1\u7fd4" : "\u4e0d\u6ed1\u7fd4", s.onGround ? " \u63a5\u5730" : " \u7a7a\u4e2d", s.held, s.fireworks, s.elytra, s.state, s.baritone ? "\u63a5\u7ba1" : "\u672a\u63a5\u7ba1", s.inLava ? "\u662f" : "\u5426", Float.valueOf(s.hp), delta);
        }
    }

    private void push(Sample s) {
        this.ring[this.cursor] = s;
        this.cursor = (this.cursor + 1) % 240;
        if (this.filled < 240) {
            ++this.filled;
        }
    }

    private Sample at(int back) {
        if (back < 0 || back >= this.filled) {
            return null;
        }
        int i = (this.cursor - 1 - back) % 240;
        if (i < 0) {
            i += 240;
        }
        return this.ring[i];
    }

    private static boolean isSelf(MinecraftClient mc, int entityId) {
        return mc != null && mc.player != null && entityId == mc.player.getId();
    }

    private static String heldName(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return "?";
        }
        ItemStack s = mc.player.getMainHandStack();
        if (s == null || s.isEmpty()) {
            return "\u7a7a\u624b";
        }
        return s.getName().getString() + " x" + s.getCount();
    }

    private static String keywordHit(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) {
            if (!lower.contains(k)) continue;
            return k;
        }
        return null;
    }

    private record Sample(int tick, double x, double y, double z, double vx, double vy, double vz, float yaw, float pitch, boolean gliding, boolean onGround, String held, int fireworks, int elytra, String state, boolean baritone, boolean inLava, float hp) {
    }
}

