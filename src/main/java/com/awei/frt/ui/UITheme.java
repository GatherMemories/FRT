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
 * 支持浅色（light，默认）与深色（dark）两套配色，通过 apply(dark) 切换；
 * 颜色字段非 final：切换主题后组件读取到的都是新值，配合 FRTFrame.refreshTheme
 * 显式重刷已捕获旧色的组件（日志区/滚动区/状态栏/按钮等）。
 * 通过 apply() 写入 UIManager 全局默认值，并对外提供按钮样式等辅助方法，
 * 使主窗口、表单弹窗等所有 Swing 组件观感一致。
 */
public final class UITheme {

    static {
        // Swing 文本抗锯齿（关键）：默认在部分 Linux/LAF 组合下关闭，文字渲染锯齿感强。
        // 必须在任何文本渲染前设置；类加载即设置 + Main/MainUI 入口双保险。
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
    }

    private static boolean dark = false;

    // ---------- 字体（两套主题共用；按平台挑选更圆润的 UI 字体） ----------
    private static final String UI_FAMILY = pickUIFamily();
    public static final Font BASE_FONT = new Font(UI_FAMILY, Font.PLAIN, 13);
    public static final Font TITLE_FONT = new Font(UI_FAMILY, Font.BOLD, 14);
    public static final Font SMALL_FONT = new Font(UI_FAMILY, Font.PLAIN, 11);
    public static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    /** 日志区字体：按平台挑选已安装的好看等宽字体（Cascadia/Consolas/Menlo/JetBrains Mono…），找不到时回退系统等宽 */
    public static final Font LOG_FONT = createLogFont();

    // ---------- 颜色（随主题切换，非 final） ----------
    // 声明时初始化为浅色值：保证未调用 apply() 前（如样式纯逻辑测试）也不为 null，
    // 语义与旧版 final 常量一致；apply(light/dark) 切换后组件读取到新值。
    public static Color PRIMARY = new Color(0x2E6FDB);
    public static Color PRIMARY_LIGHT = new Color(0xD8E4F8);
    public static Color BG = new Color(0xF7F9FC);
    public static Color PANEL_BG = new Color(0xFFFFFF);
    public static Color TEXT = new Color(0x1F2937);
    public static Color MUTED = new Color(0x6B7280);
    public static Color BORDER = new Color(0xD1D5DB);
    public static Color BUTTON_BG = new Color(0xEFF3F9);
    public static Color SUCCESS = new Color(0x16A34A);
    public static Color WARN = new Color(0xB45309);
    public static Color ERROR = new Color(0xDC2626);

    // ---------- 日志区颜色（随主题切换，非 final） ----------
    public static Color LOG_BG = new Color(0xFFFFFF);
    public static Color LOG_TEXT = new Color(0x1F2937);
    public static Color LOG_MUTED = new Color(0x6B7280);
    public static Color LOG_SUCCESS = new Color(0x16A34A);
    public static Color LOG_WARN = new Color(0xC2410C);
    public static Color LOG_ERROR = new Color(0xDC2626);
    public static Color LOG_ACCENT = new Color(0x0969DA);
    public static Color LOG_HEADING = new Color(0x1D4ED8);
    public static Color LOG_TITLE = new Color(0x1D4ED8);
    public static Color LOG_PINNED = new Color(0x7C3AED);

    // ---------- 间距 ----------
    public static final int GAP = 8;
    public static final int PADDING = 8;

    private UITheme() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 当前是否深色主题 */
    public static boolean isDark() {
        return dark;
    }

    /**
     * 应用主题（按当前 config 记录的主题切换），应在创建任何窗口前调用一次；
     * 运行中切换：apply(dark) 后调用 FRTFrame.refreshTheme() 重刷已捕获旧色的组件。
     */
    public static void apply() {
        apply(dark);
    }

    /** 应用指定主题并写入 UIManager 默认值 */
    public static void apply(boolean darkTheme) {
        dark = darkTheme;
        if (darkTheme) {
            applyDarkColors();
        } else {
            applyLightColors();
        }
        applyUIManager();
    }

    // ---------- 浅色主题（默认，大众白底深字） ----------
    private static void applyLightColors() {
        PRIMARY = new Color(0x2E6FDB);
        PRIMARY_LIGHT = new Color(0xD8E4F8);
        BG = new Color(0xF7F9FC);
        PANEL_BG = new Color(0xFFFFFF);
        TEXT = new Color(0x1F2937);
        MUTED = new Color(0x6B7280);
        BORDER = new Color(0xD1D5DB);
        BUTTON_BG = new Color(0xEFF3F9);
        SUCCESS = new Color(0x16A34A);
        WARN = new Color(0xB45309);
        ERROR = new Color(0xDC2626);

        LOG_BG = new Color(0xFFFFFF);
        LOG_TEXT = new Color(0x1F2937);
        LOG_MUTED = new Color(0x6B7280);
        LOG_SUCCESS = new Color(0x16A34A);
        LOG_WARN = new Color(0xC2410C);
        LOG_ERROR = new Color(0xDC2626);
        LOG_ACCENT = new Color(0x0969DA);
        LOG_HEADING = new Color(0x1D4ED8);
        LOG_TITLE = new Color(0x1D4ED8);
        LOG_PINNED = new Color(0x7C3AED);
    }

    // ---------- 深色主题（暗底浅字，色相与浅色一致便于区分状态） ----------
    private static void applyDarkColors() {
        PRIMARY = new Color(0x3B82F6);
        PRIMARY_LIGHT = new Color(0x1E3A5F);
        BG = new Color(0x111827);
        PANEL_BG = new Color(0x1F2937);
        TEXT = new Color(0xE5E7EB);
        MUTED = new Color(0x9CA3AF);
        BORDER = new Color(0x374151);
        BUTTON_BG = new Color(0x273549);
        SUCCESS = new Color(0x34D399);
        WARN = new Color(0xFB923C);
        ERROR = new Color(0xF87171);

        LOG_BG = new Color(0x111827);
        LOG_TEXT = new Color(0xE5E7EB);
        LOG_MUTED = new Color(0x9CA3AF);
        LOG_SUCCESS = new Color(0x34D399);
        LOG_WARN = new Color(0xFB923C);
        LOG_ERROR = new Color(0xF87171);
        LOG_ACCENT = new Color(0x60A5FA);
        LOG_HEADING = new Color(0x93C5FD);
        LOG_TITLE = new Color(0x93C5FD);
        LOG_PINNED = new Color(0xC4B5FD);
    }

    /** 写入 UIManager 全局默认值（主题色切换后需重新调用，组件用 updateComponentTreeUI 重刷） */
    private static void applyUIManager() {
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
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("ProgressBar.foreground", PRIMARY);
        UIManager.put("ProgressBar.background", new Color(dark ? 0x374151 : 0xE5E7EB));
        UIManager.put("ProgressBar.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("Button.background", BUTTON_BG);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.select", PRIMARY_LIGHT);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("Menu.background", PANEL_BG);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("MenuItem.background", PANEL_BG);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuBar.background", PANEL_BG);
        UIManager.put("MenuBar.foreground", TEXT);
        // 滚动条（Metal/基础 L&F 读取这些键；深色主题下轨道/滑块随主题，避免露浅灰条）
        UIManager.put("ScrollBar.background", PANEL_BG);
        UIManager.put("ScrollBar.foreground", BORDER);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.track", PANEL_BG);
        UIManager.put("ScrollBar.trackHighlight", BORDER);
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

    /**
     * UI 字体族：按平台挑选已安装的圆润现代字体（中文优先），找不到时回退系统无衬线。
     * - Windows：微软雅黑 UI / 微软雅黑 / Segoe UI
     * - macOS：苹方 / 冬青黑体 / 黑体-简
     * - Linux：思源黑体(Noto Sans CJK SC) / Source Han Sans SC / 文泉驿微米黑 / Droid Sans Fallback
     */
    private static String pickUIFamily() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] candidates;
        if (os.contains("win")) {
            candidates = new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI"};
        } else if (os.contains("mac") || os.contains("darwin")) {
            candidates = new String[]{"PingFang SC", "Hiragino Sans GB", "Heiti SC"};
        } else {
            candidates = new String[]{"Noto Sans CJK SC", "Source Han Sans SC", "Source Han Sans CN",
                    "WenQuanYi Micro Hei", "Droid Sans Fallback"};
        }
        return pickFirstInstalled(candidates, Font.SANS_SERIF);
    }

    /**
     * 从候选列表中挑第一个已安装的字体族；都没有时返回 fallback。
     */
    private static String pickFirstInstalled(String[] candidates, String fallback) {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Set<String> available = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
            for (String name : candidates) {
                if (available.contains(name)) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // 无头环境等异常直接回退
        }
        return fallback;
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
        return new Font(pickFirstInstalled(candidates, Font.MONOSPACED), Font.PLAIN, 13);
    }
}
