package com.awei.frt.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
    /** 日志区字体：按平台挑选已安装的好看等宽字体（Cascadia/Consolas/Menlo/JetBrains Mono…），找不到时回退系统等宽 */
    public static final Font LOG_FONT = createLogFont();

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

    // ---------- 日志区（浅色大众主题：白底深字，色相分散、互不重复，信息清晰分明） ----------
    public static final Color LOG_BG      = new Color(0xFFFFFF); // 背景：白
    public static final Color LOG_TEXT    = new Color(0x1F2937); // 正文：深灰
    public static final Color LOG_MUTED   = new Color(0x6B7280); // 说明 / 次要文字：灰
    public static final Color LOG_SUCCESS = new Color(0x16A34A); // [成功]：绿
    public static final Color LOG_WARN    = new Color(0xC2410C); // [警告] / [取消] / [跳过]：橙
    public static final Color LOG_ERROR   = new Color(0xDC2626); // [失败] / [错误]：红
    public static final Color LOG_ACCENT  = new Color(0x0969DA); // 交互输入 / 可选项列表：蓝
    public static final Color LOG_HEADING = new Color(0x1D4ED8); // 功能标题（[执行]/[列表]/[预览]等）：深蓝加粗
    public static final Color LOG_TITLE   = new Color(0x1D4ED8); // 摘要标题 =====：深蓝加粗
    public static final Color LOG_PINNED  = new Color(0x7C3AED); // [固定] 特殊状态：紫加粗

    // ---------- 间距 ----------
    public static final int GAP = 8;
    public static final int PADDING = 8;

    private UITheme() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 日志区等宽字体：按平台候选列表挑选第一个已安装的字体，
     * 保证 Windows（Cascadia/Consolas）/ macOS（Menlo）/ Linux（JetBrains Mono/DejaVu）都好看
     */
    private static Font createLogFont() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] candidates;
        if (os.contains("win")) {
            candidates = new String[]{"Cascadia Mono", "JetBrains Mono", "Consolas", "Courier New"};
        } else if (os.contains("mac") || os.contains("darwin")) {
            candidates = new String[]{"Menlo", "SF Mono", "Monaco", "Courier New"};
        } else {
            candidates = new String[]{"JetBrains Mono", "Ubuntu Mono", "DejaVu Sans Mono",
                    "Noto Sans Mono", "Liberation Mono", "Monospace"};
        }
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Set<String> available = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
            for (String name : candidates) {
                if (available.contains(name)) {
                    return new Font(name, Font.PLAIN, 13);
                }
            }
        } catch (Exception ignored) {
            // 无头环境等异常直接回退系统等宽
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 13);
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
        UIManager.put("TextArea.font", LOG_FONT);
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
