package com.fo.addon.elytra.core;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import org.lwjgl.glfw.GLFW;
public final class PlayerAction {
    private PlayerAction() {
    }
    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
    public static boolean deployElytra() {
        MinecraftClient mc = mc();
        if (mc.player == null || mc.getNetworkHandler() == null) return false;
        if (mc.player.isGliding() || mc.player.isOnGround()) return false;
        try {
            mc.getNetworkHandler().sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        } catch (Throwable t) {
            FOElytraLog.debug("展开鞘翅包发送失败：%s", t);
            return false;
        }
    }
    public static boolean sendStartFallFlying() {
        MinecraftClient mc = mc();
        if (mc.player == null || mc.getNetworkHandler() == null) return false;
        try {
            mc.getNetworkHandler().sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    public static void pressJump(boolean pressed) {
        MinecraftClient mc = mc();
        mc.options.jumpKey.setPressed(pressed);
        try {
            Input.setKeyState(mc.options.jumpKey, pressed);
        } catch (Throwable ignored) {
        }
    }
    public static void pressForward(boolean pressed) {
        MinecraftClient mc = mc();
        mc.options.forwardKey.setPressed(pressed);
        try {
            Input.setKeyState(mc.options.forwardKey, pressed);
        } catch (Throwable ignored) {
        }
    }
    public static void pressUse(boolean pressed) {
        MinecraftClient mc = mc();
        mc.options.useKey.setPressed(pressed);
        try {
            Input.setKeyState(mc.options.useKey, pressed);
        } catch (Throwable ignored) {
        }
    }
    public static void releaseAll() {
        MinecraftClient mc = mc();
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        try {
            Input.setKeyState(mc.options.jumpKey, false);
            Input.setKeyState(mc.options.forwardKey, false);
            Input.setKeyState(mc.options.useKey, false);
        } catch (Throwable ignored) {
        }
    }
    public static void restoreHeldKeys() {
        MinecraftClient mc = mc();
        if (mc.player == null) return;
        try {
            boolean shiftHeld = Input.isPressed(mc.options.sneakKey)
                || Input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)
                || Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT)
                || physicallyDown(mc, GLFW.GLFW_KEY_LEFT_SHIFT)
                || physicallyDown(mc, GLFW.GLFW_KEY_RIGHT_SHIFT);
            if (mc.options.sneakKey.isPressed() != shiftHeld) {
                mc.options.sneakKey.setPressed(shiftHeld);
            }
            boolean sprintHeld = Input.isPressed(mc.options.sprintKey)
                || physicallyDown(mc, GLFW.GLFW_KEY_LEFT_CONTROL);
            if (mc.options.sprintKey.isPressed() != sprintHeld) {
                mc.options.sprintKey.setPressed(sprintHeld);
            }
            boolean forwardHeld = Input.isPressed(mc.options.forwardKey)
                || physicallyDown(mc, GLFW.GLFW_KEY_W);
            if (mc.options.forwardKey.isPressed() != forwardHeld) {
                mc.options.forwardKey.setPressed(forwardHeld);
            }
        } catch (Throwable ignored) {
        }
    }
    private static boolean physicallyDown(MinecraftClient mc, int key) {
        try {
            return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }
}
