package com.fo.addon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 防回归测试：Baritone 的 blocksToDisallowBreaking 是 Setting<List<Block>>。
 * FO 的 protectShulkerBoxes 用反射写它的 value 字段，必须写 List 类型；
 * 写成 Block[] 会在 Baritone 读取时抛 ClassCastException（Block cannot be cast to List），
 * 导致自动挖沙寻路直接失败（站着不动、挖不掉）。
 * 本测试用纯 JDK 模型复现 Setting<T> 的泛型擦除 + Baritone 的 contains 读取方式。
 */
public class PathManagersSettingTest {

    /** 模拟 Baritone 的 Setting<T>：泛型擦除后 value 字段类型为 Object */
    static class Setting<T> {
        public T value;
    }

    /** 模拟 Baritone Settings 中的 blocksToDisallowBreaking 字段 */
    static class FakeBaritoneSettings {
        public final Setting<List<String>> blocksToDisallowBreaking = new Setting<>();
    }

    /** 模拟 Baritone 读取方式：getfield value 后 checkcast List 再 contains */
    @SuppressWarnings("unchecked")
    private static boolean baritoneContains(Setting<?> setting, Object block) {
        return ((List<Object>) setting.value).contains(block);
    }

    @Test
    void settingValueWrittenAsListIsReadableByBaritoneStyleAccess() throws Exception {
        FakeBaritoneSettings settings = new FakeBaritoneSettings();
        Field f = settings.getClass().getField("blocksToDisallowBreaking");
        Object setting = f.get(settings);
        Field valueField = setting.getClass().getField("value");

        // 修复后的写法：塞 List<Block>（用 String 模拟 Block 名）
        List<String> shulkers = Arrays.asList("SHULKER_BOX", "WHITE_SHULKER_BOX");
        valueField.set(setting, shulkers);

        // Baritone 式读取不抛异常，且能正确判断
        assertTrue(baritoneContains((Setting<?>) setting, "SHULKER_BOX"));
        assertFalse(baritoneContains((Setting<?>) setting, "SAND"));
    }

    @Test
    void settingValueWrittenAsArrayThrowsClassCastException() throws Exception {
        FakeBaritoneSettings settings = new FakeBaritoneSettings();
        Field f = settings.getClass().getField("blocksToDisallowBreaking");
        Object setting = f.get(settings);
        Field valueField = setting.getClass().getField("value");

        // 错误写法：塞数组（V4.3~V4.5 的 bug）
        String[] shulkers = new String[]{"SHULKER_BOX"};
        valueField.set(setting, shulkers);

        // 泛型擦除后 set 能成功，但 Baritone 式读取必然抛 ClassCastException
        assertThrows(ClassCastException.class, () -> baritoneContains((Setting<?>) setting, "SHULKER_BOX"));
    }

    @Test
    void disabledStateWritesEmptyList() throws Exception {
        FakeBaritoneSettings settings = new FakeBaritoneSettings();
        Field f = settings.getClass().getField("blocksToDisallowBreaking");
        Object setting = f.get(settings);
        Field valueField = setting.getClass().getField("value");

        // 关闭保护时：塞空 List（修复后的写法），而不是空数组
        valueField.set(setting, new ArrayList<String>());

        assertTrue(((List<?>) ((Setting<?>) setting).value).isEmpty());
        assertFalse(baritoneContains((Setting<?>) setting, "SHULKER_BOX"));
    }
}
