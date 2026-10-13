package com.fo.addon.utils;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;

/**
 * 日志/聊天辅助 (从 SlimefunHelper 移植，输出改用 Meteor 通道)。
 */
public class Debug {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Logger getLogger() {
        return LOGGER;
    }

    public static void info(String msg) {
        LOGGER.info(msg);
    }

    public static void info(Object obj) {
        if (obj instanceof Throwable t) {
            LOGGER.info("", t);
        } else {
            LOGGER.info(String.valueOf(obj));
        }
    }

    public static void info(Object... objs) {
        StringBuilder sb = new StringBuilder();
        for (Object o : objs) sb.append(o);
        info(sb.toString());
    }

    public static void chat(Object obj) {
        if (MeteorClient.mc.player == null) return;
        MeteorClient.mc.player.sendMessage(
                obj instanceof Text t ? t : Text.literal(String.valueOf(obj)), false);
    }

    public static void chat(Object... objs) {
        StringBuilder sb = new StringBuilder();
        for (Object o : objs) sb.append(o);
        chat(sb.toString());
    }

    public static void debug(Object obj) {
        LOGGER.debug(String.valueOf(obj));
    }

    public static void debug(Object... objs) {
        StringBuilder sb = new StringBuilder();
        for (Object o : objs) sb.append(o);
        debug(sb.toString());
    }
}
