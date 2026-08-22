package com.awei.frt.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/**
 * FRT 统一界面主题：字体 / 颜色 / 间距常量
 * 通过 apply() 写入 UIManager 全局默认值，并对外提供按钮样式等辅助方法，
 * 使主窗口、表单弹窗等所有 Swing 组件观感一致。
 */
public final class UITheme {

    // ---------- 字体 ----------
    public static final Font BASE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font SMALL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    public static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    // ---------- 颜色 ----------
    public static final Color PRIMARY = new Color(0x2E6FDB);
    public static final Color PRIMARY_LIGHT = new Color(0xD8E4F8);
    public static final Color BG = new Color(0xF7F9FC);
    public static final Color PANEL_BG = new Color(0xFFFFFF);
    public static final Color TEXT = new Color(0x1F2937);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color BORDER = new Color(0xD1D5DB);
    public static final Color BUTTON_BG = new Color(0xEFF3F9);
    public static final Color SUCCESS = new Color(0x16A34A);
    public static final Color WARN = new Color(0xB45309);
    public static final Color ERROR = new Color(0xDC2626);

    // ---------- 间距 ----------
    public static final int GAP = 8;
    public static final int PADDING = 8;

    private UITheme() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 应用全局主题（写入 UIManager 默认值），应在创建任何窗口前调用一次
     */
    public static void apply() {
        UIManager.put("Button.font", BASE_FONT);
        UIManager.put("Label.font", BASE_FONT);
        UIManager.put("CheckBox.font", BASE_FONT);
        UIManager.put("ComboBox.font", BASE_FONT);
        UIManager.put("TextField.font", BASE_FONT);
        UIManager.put("TextArea.font", MONO_FONT);
        UIManager.put("List.font", BASE_FONT);
        UIManager.put("Table.font", BASE_FONT);
        UIManager.put("TableHeader.font", SMALL_FONT);
        UIManager.put("OptionPane.messageFont", BASE_FONT);
        UIManager.put("ProgressBar.font", SMALL_FONT);
        UIManager.put("TitledBorder.font", TITLE_FONT);
        UIManager.put("Panel.background", PANEL_BG);
        UIManager.put("OptionPane.background", PANEL_BG);
        UIManager.put("ProgressBar.foreground", PRIMARY);
        UIManager.put("ProgressBar.background", new Color(0xE5E7EB));
        UIManager.put("ProgressBar.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("Button.background", BUTTON_BG);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.select", PRIMARY_LIGHT);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
    }

    /**
     * 统一主按钮样式（浅色底 + 描边 + 圆角感留白）
     */
    public static void styleButton(JButton button) {
        button.setFont(BASE_FONT);
        button.setFocusPainted(false);
        button.setBackground(BUTTON_BG);
        button.setForeground(TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
    }

    /**
     * 统一强调按钮样式（主色底 + 白字），用于表单的"确定生成"
     */
    public static void stylePrimaryButton(JButton button) {
        button.setFont(BASE_FONT);
        button.setFocusPainted(false);
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
    }

    public static Border lineBorder() {
        return BorderFactory.createLineBorder(BORDER);
    }
}
