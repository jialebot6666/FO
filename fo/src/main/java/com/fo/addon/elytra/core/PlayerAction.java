package com.fo.addon.elytra.core;

import com.fo.addon.elytra.core.FOElytraLog;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import org.lwjgl.glfw.GLFW;

public final class PlayerAction {
    private PlayerAction() {
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    public static boolean deployElytra() {
        MinecraftClient mc = PlayerAction.mc();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return false;
        }
        if (mc.player.isGliding() || mc.player.isOnGround()) {
            return false;
        }
        try {
            mc.getNetworkHandler().sendPacket((Packet)new ClientCommandC2SPacket((Entity)mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        catch (Throwable t) {
            FOElytraLog.debug("\u5c55\u5f00\u9798\u7fc5\u5305\u53d1\u9001\u5931\u8d25\uff1a%s", t);
            return false;
        }
    }

    public static boolean sendStartFallFlying() {
        MinecraftClient mc = PlayerAction.mc();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return false;
        }
        try {
            mc.getNetworkHandler().sendPacket((Packet)new ClientCommandC2SPacket((Entity)mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static void pressJump(boolean pressed) {
        MinecraftClient mc = PlayerAction.mc();
        mc.options.jumpKey.setPressed(pressed);
        try {
            Input.setKeyState((KeyBinding)mc.options.jumpKey, (boolean)pressed);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void pressForward(boolean pressed) {
        MinecraftClient mc = PlayerAction.mc();
        mc.options.forwardKey.setPressed(pressed);
        try {
            Input.setKeyState((KeyBinding)mc.options.forwardKey, (boolean)pressed);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void pressUse(boolean pressed) {
        MinecraftClient mc = PlayerAction.mc();
        mc.options.useKey.setPressed(pressed);
        try {
            Input.setKeyState((KeyBinding)mc.options.useKey, (boolean)pressed);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void releaseAll() {
        MinecraftClient mc = PlayerAction.mc();
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        try {
            Input.setKeyState((KeyBinding)mc.options.jumpKey, (boolean)false);
            Input.setKeyState((KeyBinding)mc.options.forwardKey, (boolean)false);
            Input.setKeyState((KeyBinding)mc.options.useKey, (boolean)false);
            Input.setKeyState((KeyBinding)mc.options.sneakKey, (boolean)false);
            Input.setKeyState((KeyBinding)mc.options.sprintKey, (boolean)false);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static boolean clearStuckSneak() {
        MinecraftClient mc = PlayerAction.mc();
        if (mc.player == null || mc.options == null) {
            return false;
        }
        try {
            boolean shiftHeld;
            boolean bl = shiftHeld = PlayerAction.physicallyDown(mc, 340) || PlayerAction.physicallyDown(mc, 344);
            if (shiftHeld) {
                return false;
            }
            boolean acted = false;
            if (mc.options.sneakKey.isPressed()) {
                mc.options.sneakKey.setPressed(false);
                acted = true;
            }
            try {
                Input.setKeyState((KeyBinding)mc.options.sneakKey, (boolean)false);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            if (mc.player.isSneaking()) {
                mc.player.setSneaking(false);
                acted = true;
            }
            return acted;
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean sneakHeld() {
        MinecraftClient mc = PlayerAction.mc();
        if (mc.player == null || mc.options == null) {
            return false;
        }
        try {
            return mc.options.sneakKey.isPressed() || mc.player.isSneaking();
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean forceNoSneak() {
        MinecraftClient mc = PlayerAction.mc();
        if (mc.player == null || mc.options == null) {
            return false;
        }
        try {
            boolean acted = mc.options.sneakKey.isPressed() || mc.player.isSneaking();
            mc.options.sneakKey.setPressed(false);
            try {
                Input.setKeyState((KeyBinding)mc.options.sneakKey, (boolean)false);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            mc.player.setSneaking(false);
            return acted;
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static void restoreHeldKeys() {
        MinecraftClient mc = PlayerAction.mc();
        if (mc.player == null) {
            return;
        }
        try {
            boolean forwardHeld;
            boolean sprintHeld;
            PlayerAction.forceNoSneak();
            boolean bl = sprintHeld = Input.isPressed((KeyBinding)mc.options.sprintKey) || PlayerAction.physicallyDown(mc, 341);
            if (mc.options.sprintKey.isPressed() != sprintHeld) {
                mc.options.sprintKey.setPressed(sprintHeld);
            }
            boolean bl2 = forwardHeld = Input.isPressed((KeyBinding)mc.options.forwardKey) || PlayerAction.physicallyDown(mc, 87);
            if (mc.options.forwardKey.isPressed() != forwardHeld) {
                mc.options.forwardKey.setPressed(forwardHeld);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static boolean physicallyDown(MinecraftClient mc, int key) {
        try {
            return GLFW.glfwGetKey((long)mc.getWindow().getHandle(), (int)key) == 1;
        }
        catch (Throwable t) {
            return false;
        }
    }
}

