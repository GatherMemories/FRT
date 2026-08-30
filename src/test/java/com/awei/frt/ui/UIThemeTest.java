package com.awei.frt.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UITheme 深浅主题切换纯逻辑测试（AC-4）：
 * - apply(true) 后 isDark()==true、BG/TEXT/LOG_BG 为深色值；apply(false) 后回浅色值
 * - 任意时刻所有颜色字段非 null（静态初始化即为浅色值，未调用 apply 前也不为 null）
 * - 深/浅两套主题的关键颜色互不相同
 * 纯静态方法，可在无头环境运行；测试后恢复浅色，避免影响其他测试。
 */
class UIThemeTest {

    @AfterAll
    static void restoreLightTheme() {
        UITheme.apply(false); // 恢复浅色默认，不影响同 JVM 内其他测试
    }

    @Test
    void applyDarkSetsDarkValues() {
        UITheme.apply(true);
        assertTrue(UITheme.isDark());
        assertEquals(new Color(0x111827), UITheme.BG, "深色背景应为 0x111827");
        assertEquals(new Color(0xE5E7EB), UITheme.TEXT, "深色正文应为 0xE5E7EB");
        assertEquals(new Color(0x111827), UITheme.LOG_BG, "深色日志背景应为 0x111827");
        assertEquals(new Color(0x1F2937), UITheme.PANEL_BG);
    }

    @Test
    void applyLightRestoresLightValues() {
        UITheme.apply(true);
        UITheme.apply(false);
        assertFalse(UITheme.isDark());
        assertEquals(new Color(0xF7F9FC), UITheme.BG, "浅色背景应为 0xF7F9FC");
        assertEquals(new Color(0x1F2937), UITheme.TEXT, "浅色正文应为 0x1F2937");
        assertEquals(new Color(0xFFFFFF), UITheme.LOG_BG, "浅色日志背景应为白色");
    }

    @Test
    void allColorFieldsNonNullAtAnyTime() {
        // 未调用 apply 的初始状态（类加载时静态初始化浅色值）也不为 null
        assertNonNullColors();
        UITheme.apply(true);
        assertNonNullColors();
        UITheme.apply(false);
        assertNonNullColors();
    }

    @Test
    void darkAndLightKeyColorsDiffer() {
        UITheme.apply(true);
        Color darkBg = UITheme.BG;
        Color darkText = UITheme.TEXT;
        Color darkLogBg = UITheme.LOG_BG;
        Color darkLogText = UITheme.LOG_TEXT;
        UITheme.apply(false);
        assertNotEquals(darkBg, UITheme.BG, "BG 深浅必须不同");
        assertNotEquals(darkText, UITheme.TEXT, "TEXT 深浅必须不同");
        assertNotEquals(darkLogBg, UITheme.LOG_BG, "LOG_BG 深浅必须不同");
        assertNotEquals(darkLogText, UITheme.LOG_TEXT, "LOG_TEXT 深浅必须不同");
    }

    /** 反射检查全部 public Color 字段非 null */
    private static void assertNonNullColors() {
        for (Field f : UITheme.class.getFields()) {
            if (f.getType() == Color.class) {
                try {
                    assertNotNull(f.get(null), "颜色字段不应为 null: " + f.getName());
                } catch (IllegalAccessException e) {
                    throw new AssertionError("无法读取字段: " + f.getName(), e);
                }
            }
        }
    }
}
