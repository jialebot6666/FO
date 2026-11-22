package com.fo.addon.utils;

import com.fo.addon.modules.FOEatLift;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射读取 SlimefunHelper 鞘翅追击（{@code ElytraBot}）的开关状态。
 *
 * <p>移植自 eat-lift（作者 dev.hyh）的 {@code SlmHook}，语义保持：
 * <ul>
 *   <li>每秒最多解析一次类，避免每 tick 反射；</li>
 *   <li>没装 SlimefunHelper、或读不到开关时一律放行（不影响本模块单独使用）；</li>
 *   <li>读到开关对象后，调 {@code get()} 或 {@code getValue()} 取布尔值，结果缓存 20 ms。</li>
 * </ul>
 */
public final class SlmHook {

    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long CACHE_MS = 20L;

    private static Object bot;
    private static Field enableField;
    private static Method flagGet;
    private static boolean missing;
    private static long lastLookup;
    private static long lastState;
    private static boolean cached = true;

    private SlmHook() {
    }

    private static void resolve() {
        if (missing) {
            return;
        }
        if (bot != null && enableField != null && flagGet != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLookup < RETRY_INTERVAL_MS) {
            return;
        }
        lastLookup = now;

        try {
            Class<?> clazz = Class.forName("me.matl114.hacks.modules.combat.ElytraBot");
            Field instance = clazz.getField("INSTANCE");
            Object instanceValue = instance.get(null);
            if (instanceValue == null) {
                return;
            }

            Field field = clazz.getField("enable");
            Method getter;
            try {
                getter = field.getType().getMethod("get");
            } catch (NoSuchMethodException e) {
                getter = field.getType().getMethod("getValue");
            }

            bot = instanceValue;
            enableField = field;
            flagGet = getter;
        } catch (ClassNotFoundException e) {
            missing = true;
            FOEatLift.LOGGER.info("[FO 进食拉升] 未安装 SlimefunHelper，鞘翅追击门禁自动放行");
        } catch (Throwable t) {
            FOEatLift.LOGGER.warn("[FO 进食拉升] 读取鞘翅追击开关失败，本次放行", t);
        }
    }

    /** 鞘翅追击是否开启；未安装或读取失败时返回 true（放行）。 */
    public static boolean isElytraBotOn() {
        resolve();
        if (bot == null || enableField == null || flagGet == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastState < CACHE_MS) {
            return cached;
        }
        lastState = now;

        try {
            Object flag = enableField.get(bot);
            if (flag == null) {
                cached = true;
            } else {
                Object value = flagGet.invoke(flag);
                cached = !(value instanceof Boolean) || (Boolean) value;
            }
        } catch (Throwable t) {
            cached = true;
        }
        return cached;
    }
}
