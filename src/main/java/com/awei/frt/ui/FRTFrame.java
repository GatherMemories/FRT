package com.awei.frt.ui;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.ProgressCallback;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.service.CoreConfigWizard;
import com.awei.frt.service.FileDeleteService;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.service.PluginCompiler;
import com.awei.frt.service.RestoreService;
import com.awei.frt.service.RuleConfigWizard;
import com.awei.frt.service.UpdateChecker;
import com.awei.frt.util.BuildInfo;
import com.awei.frt.util.LoggerUtil;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FRT Swing 主窗口（固定输入区版）
 * - 顶部：5 个功能按钮（更新/删除/恢复/规则生成/清理残留备份）
 * - 中部：日志区（捕获 System.out/err 实时显示，长内容可滚动查看）
 * - 底部：输入区（快捷选项按钮 + 输入框，仅等待输入时出现）+ 最底部状态栏
 * 服务层通过 UserPrompter 抽象复用同一套逻辑；等待输入时不弹窗，直接在窗口内交互。
 */
public class FRTFrame extends JFrame implements SwingPrompter.PromptSource, SwingPrompter.InputPanel {

    private final JTextPane logArea;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final QuickButtonPanel quickPanel;
    private final JScrollPane quickScroll;   // 快捷按钮滚动区（最多显示约 3 行，超出滚动）
    private final JScrollPane logScroll;     // 日志滚动区（主题切换时重刷视口/边框，避免残留旧色）
    private final JPanel inputArea;          // 快捷按钮 + 输入行（等待输入时显示）
    private final JTextField inputField;
    private final JButton submitButton;
    private final JButton cancelButton;
    private final JButton fontMinusButton; // 日志字体缩小（A-）
    private final JButton fontPlusButton;  // 日志字体放大（A+）
    private final JButton clearLogButton;  // 清空日志按钮（主题切换时重刷样式）
    private final JPanel statusBar;        // 状态栏（主题切换时重刷背景/边框）
    private final JPanel topPanel;         // 顶部功能按钮栏（主题切换时重刷背景，避免深色下残留浅色）
    private final JPanel bottomArea;       // 底部区域（进度条+输入区+状态栏，主题切换时重刷背景）
    private final JPanel inputRow;         // 输入行（输入框+提交/取消，主题切换时重刷背景）
    private JRadioButtonMenuItem themeLightItem; // 视图→主题 浅色项（勾选与当前主题同步）
    private JRadioButtonMenuItem themeDarkItem;  // 视图→主题 深色项
    private JMenuItem fontMinusMenuItem;   // 视图→日志字体 缩小项（与 A- 按钮联动禁用）
    private JMenuItem fontPlusMenuItem;    // 视图→日志字体 放大项（与 A+ 按钮联动禁用）
    private int logFontSize = 13;          // 当前日志字体大小（可调 10~24，持久化到 config.json）
    private final List<JButton> topButtons = new ArrayList<>();
    // 状态栏右侧面板：版本链接 +（发现新版时的）提示链接，主题切换时重刷背景
    private final JPanel statusRight;
    // 状态栏"发现新版本"提示链接（自动检查显示，手动检查/打开下载页时清除）
    private LinkLabel updateHintLink;
    // 检查更新互斥标志：自动检查与手动「帮助 → 检查更新」共用，任一进行中另一触发方直接返回
    private volatile boolean updateCheckInProgress = false;
    // 提示缓冲：累积"自上次输入以来"打印的全部文本（结构树/选项列表/说明）
    private final StringBuilder promptBuffer = new StringBuilder();
    private static final int PROMPT_BUFFER_MAX = 20000;
    private static final int QUICK_MAX_VISIBLE_HEIGHT = 96; // 快捷按钮区最多显示约 3 行，超出滚动查看
    private static final int QUICK_MIN_HEIGHT = 30;
    private Config config;
    private SwingPrompter prompter;

    public FRTFrame() throws HeadlessException {
        // 标题带版本号：版本来自 pom.xml（Maven 构建时注入 build-info.properties，见 BuildInfo）
        super("多层级文件夹更新工具 v" + BuildInfo.VERSION);
        UITheme.apply(); // 全局主题：统一字体/颜色/间距
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 600);
        setLocationRelativeTo(null);

        logArea = new JTextPane();
        logArea.setEditable(false);
        logArea.setFont(UITheme.LOG_FONT);      // 好看的等宽字体（平台候选）
        logArea.setBackground(UITheme.LOG_BG);  // 深色终端背景
        logArea.setForeground(UITheme.LOG_TEXT);
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        // 加大滚轮步长：Swing 默认 unitIncrement 偏小，鼠标滚轮滚动日志区很慢
        logScroll = new JScrollPane(logArea);
        logScroll.setBackground(UITheme.LOG_BG);
        logScroll.getViewport().setBackground(UITheme.LOG_BG); // 视口同色，避免白底四周露边
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER)); // 顶部分隔线，与按钮区分界清晰
        logScroll.getVerticalScrollBar().setUnitIncrement(24);
        logScroll.getHorizontalScrollBar().setUnitIncrement(24);
        add(logScroll, BorderLayout.CENTER);

        // 顶部功能按钮
        topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topPanel.setBackground(UITheme.PANEL_BG);
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        topPanel.add(topButton("更新文件", this::runUpdate));
        topPanel.add(topButton("删除文件", this::runDelete));
        topPanel.add(topButton("恢复备份", this::runRestore));
        topPanel.add(topButton("规则生成", this::runWizard));
        topPanel.add(topButton("清理残留备份", this::runCleanup));
        topPanel.add(topButton("核心配置", this::runConfig));
        topPanel.add(topButton("打包插件", this::runPluginBuild));
        clearLogButton = new JButton("清空日志");
        UITheme.styleButton(clearLogButton);
        clearLogButton.addActionListener(e -> logArea.setText(""));
        topPanel.add(clearLogButton);
        // 日志字体大小调整（A-/A+）：即时生效并持久化到 config.json，重启后保留
        fontMinusButton = new JButton("A-");
        UITheme.styleButton(fontMinusButton);
        fontMinusButton.setToolTipText("缩小日志字体（最小 " + Config.MIN_LOG_FONT_SIZE + "px）");
        fontMinusButton.addActionListener(e -> adjustLogFontSize(-1));
        topPanel.add(fontMinusButton);
        fontPlusButton = new JButton("A+");
        UITheme.styleButton(fontPlusButton);
        fontPlusButton.setToolTipText("放大日志字体（最大 " + Config.MAX_LOG_FONT_SIZE + "px）");
        fontPlusButton.addActionListener(e -> adjustLogFontSize(1));
        topPanel.add(fontPlusButton);
        add(topPanel, BorderLayout.NORTH);

        // 底部区域：输入区（等待输入时显示）+ 最底部状态栏
        // 快捷按钮：FlowLayout 自动换行；最多显示约 3 行，超出部分右侧滚动查看
        // （不设横向滚动条，避免遮住按钮；按钮再多也不会把输入区撑出窗口或被裁掉）
        quickPanel = new QuickButtonPanel();
        quickScroll = new JScrollPane(quickPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                int h = Math.max(d.height, QUICK_MIN_HEIGHT);
                return new Dimension(d.width, Math.min(h, QUICK_MAX_VISIBLE_HEIGHT));
            }
        };
        quickScroll.setBorder(null);
        quickScroll.setBackground(UITheme.PANEL_BG);
        quickScroll.getViewport().setBackground(UITheme.PANEL_BG);
        inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        inputRow.setBackground(UITheme.PANEL_BG);
        inputField = new JTextField(38);
        inputField.addActionListener(e -> submitInput());
        submitButton = new JButton("提交");
        submitButton.addActionListener(e -> submitInput());
        cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> prompter.submit(""));
        inputRow.add(inputField);
        inputRow.add(submitButton);
        inputRow.add(cancelButton);
        inputArea = new JPanel(new BorderLayout(0, 4));
        inputArea.setBackground(UITheme.PANEL_BG);
        inputArea.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        inputArea.add(quickScroll, BorderLayout.NORTH);
        inputArea.add(inputRow, BorderLayout.SOUTH);
        inputArea.setVisible(false); // 平时隐藏，等待输入时才出现

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(UITheme.SMALL_FONT);
        statusLabel.setForeground(UITheme.MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(UITheme.PANEL_BG);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER));
        statusBar.add(statusLabel, BorderLayout.CENTER);

        // 状态栏右侧：版本号 + GitHub 仓库链接 +（发现新版时的）提示链接。
        // 版本自动取自 pom.xml；链接可点击，用系统浏览器打开。
        // 注：LinkLabel 用 HTML 锚点渲染，链接色由 HTML 样式表决定、不随主题（深色下仍可读），
        // 不做 setForeground 无效调用（见审查 F4）
        statusRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        statusRight.setBackground(UITheme.PANEL_BG);
        LinkLabel versionLink = new LinkLabel("v" + BuildInfo.VERSION + " · GitHub", BuildInfo.GITHUB_URL);
        versionLink.setFont(UITheme.SMALL_FONT);
        versionLink.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 10));
        statusRight.add(versionLink);
        statusBar.add(statusRight, BorderLayout.EAST);

        // 进度条：更新/删除真实执行阶段显示，平时隐藏
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(UITheme.SMALL_FONT);
        progressBar.setVisible(false);

        bottomArea = new JPanel(new BorderLayout());
        bottomArea.setBackground(UITheme.PANEL_BG);
        bottomArea.add(progressBar, BorderLayout.NORTH);
        bottomArea.add(inputArea, BorderLayout.CENTER);
        bottomArea.add(statusBar, BorderLayout.SOUTH);
        add(bottomArea, BorderLayout.SOUTH);
        setInputEnabled(false);

        // 捕获 System.out/err 到日志区（双写：控制台 + 窗口）
        redirectOutput();

        // 初始化配置与日志（LoggerUtil 会在 tee 之后初始化，其输出链仍回到日志区）
        config = ConfigLoader.getConfig();
        if (config != null) {
            // 应用 config.json 里持久化的日志字体大小（无该字段时默认 13）
            logFontSize = config.getLogFontSize();
            applyLogFontSize(logFontSize);
            // 按 config.json 记录的 UI 主题应用：组件已按浅色创建，配置为深色时补一次
            // apply(true) + refreshTheme()（窗口尚未显示，无闪烁；浅色时保持默认即可）
            if (Config.THEME_DARK.equals(config.getTheme()) && !UITheme.isDark()) {
                UITheme.apply(true);
                refreshTheme();
            }
        }
        // 菜单栏（放在 config 读取之后：主题菜单勾选初始状态与 UITheme 当前主题一致）
        setJMenuBar(buildMenuBar(this));
        updateFontButtons();
        prompter = new SwingPrompter(this, this);
        if (config == null) {
            appendText("[失败] 配置加载失败，请检查 config.json\n");
            LoggerUtil.logError("[失败] 配置加载失败，请检查配置文件");
        }
        // 残留备份过多时提醒清理
        BackupFileLoader.warnOrphanBackupsIfNeeded();
        // 启动时自动检查更新（FR-1）：config 未加载或开关关闭时静默跳过；查询在后台线程，
        // 发现新版仅状态栏+日志区非侵入提示，不阻塞 EDT、窗口显示前不弹任何东西
        startAutoCheckUpdateIfEnabled();
    }

    private JButton topButton(String label, Runnable action) {
        JButton b = new JButton(label);
        UITheme.styleButton(b);
        b.addActionListener(e -> action.run());
        topButtons.add(b);
        return b;
    }

    // ---------------- 菜单栏（文件/视图/帮助） ----------------

    /**
     * 构建菜单栏（包内可见，供菜单结构 headless 测试；frame 为 null 时只构建骨架不绑定动作）：
     * - 文件：打开目录 ▸ 更新/目标/删除/备份/日志目录、分隔线、退出
     * - 视图：主题 ▸ 浅色/深色（单选勾选，与当前主题同步）、日志字体 ▸ 缩小(A-)/放大(A+)
     * - 帮助：检查更新、关于
     * 菜单项动作与顶部按钮同一行为风格：执行动作走后台线程，日志区输出 [成功]/[失败]/[警告] 标记。
     */
    static JMenuBar buildMenuBar(FRTFrame frame) {
        JMenuBar bar = new JMenuBar();
        // 个别 L&F 下 JMenuBar 不透传 UIManager 的 MenuBar 颜色键，显式设置保证深色主题可读
        bar.setBackground(UITheme.PANEL_BG);
        bar.setForeground(UITheme.TEXT);

        // ---------- 文件：打开目录 ▸ 5 个目录 + 退出 ----------
        JMenu fileMenu = new JMenu("文件");
        JMenu openDirMenu = new JMenu("打开目录");
        openDirMenu.add(menuItem(frame, "更新目录", () -> frame.openDirectoryByName("更新目录")));
        openDirMenu.add(menuItem(frame, "目标目录", () -> frame.openDirectoryByName("目标目录")));
        openDirMenu.add(menuItem(frame, "删除目录", () -> frame.openDirectoryByName("删除目录")));
        openDirMenu.add(menuItem(frame, "备份目录", () -> frame.openDirectoryByName("备份目录")));
        openDirMenu.add(menuItem(frame, "日志目录", () -> frame.openDirectoryByName("日志目录")));
        fileMenu.add(openDirMenu);
        fileMenu.addSeparator();
        // 注意：用 lambda 而非方法引用——方法引用对实例目标会立即求值，frame 为 null（菜单结构测试）时会 NPE
        // 退出：触发 WINDOW_CLOSING 事件，由既有 setDefaultCloseOperation(EXIT_ON_CLOSE) 走正常退出流程
        //（dispose() 只发 WINDOW_CLOSED，Swing EDT 为非守护线程，窗口消失但进程残留，故不用）
        fileMenu.add(menuItem(frame, "退出",
                () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
        bar.add(fileMenu);

        // ---------- 视图：主题（浅色/深色单选勾选）+ 日志字体（A-/A+） ----------
        JMenu viewMenu = new JMenu("视图");
        JMenu themeMenu = new JMenu("主题");
        JRadioButtonMenuItem lightItem = new JRadioButtonMenuItem("浅色", !UITheme.isDark());
        JRadioButtonMenuItem darkItem = new JRadioButtonMenuItem("深色", UITheme.isDark());
        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(lightItem);
        themeGroup.add(darkItem);
        if (frame != null) {
            lightItem.addActionListener(e -> frame.switchTheme(false));
            darkItem.addActionListener(e -> frame.switchTheme(true));
            frame.themeLightItem = lightItem;
            frame.themeDarkItem = darkItem;
        }
        themeMenu.add(lightItem);
        themeMenu.add(darkItem);
        viewMenu.add(themeMenu);
        JMenu fontMenu = new JMenu("日志字体");
        JMenuItem fontMinusItem = new JMenuItem("缩小 (A-)");
        JMenuItem fontPlusItem = new JMenuItem("放大 (A+)");
        if (frame != null) {
            fontMinusItem.addActionListener(e -> frame.adjustLogFontSize(-1));
            fontPlusItem.addActionListener(e -> frame.adjustLogFontSize(1));
            frame.fontMinusMenuItem = fontMinusItem;
            frame.fontPlusMenuItem = fontPlusItem;
        }
        fontMenu.add(fontMinusItem);
        fontMenu.add(fontPlusItem);
        viewMenu.add(fontMenu);
        bar.add(viewMenu);

        // ---------- 帮助：检查更新 / 启动时自动检查更新（勾选项）/ 分隔线 / 关于 ----------
        JMenu helpMenu = new JMenu("帮助");
        helpMenu.add(menuItem(frame, "检查更新", () -> frame.runCheckUpdate()));
        // 启动时自动检查更新开关：勾选状态与 config.isAutoCheckUpdate() 同步（frame 为 null 的
        // 菜单结构测试只校验结构与文案，勾选状态取默认值 true，见 FRTFrameMenuTest 注释）
        JCheckBoxMenuItem autoCheckItem = new JCheckBoxMenuItem("启动时自动检查更新",
                initialAutoCheckState(frame == null ? null : frame.config));
        if (frame != null) {
            autoCheckItem.addActionListener(e -> frame.toggleAutoCheckUpdate(autoCheckItem.isSelected()));
        }
        helpMenu.add(autoCheckItem);
        helpMenu.addSeparator();
        helpMenu.add(menuItem(frame, "关于", () -> frame.showAbout()));
        bar.add(helpMenu);

        // 构建完成后按当前主题整树显式设色（启动即深色时初始就正确，不依赖 L&F 透传）
        rethemeMenuTree(bar);
        return bar;
    }

    /** 创建菜单项并绑定动作（frame 为 null 时不绑定，供菜单结构测试） */
    private static JMenuItem menuItem(FRTFrame frame, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (frame != null) {
            item.addActionListener(e -> action.run());
        }
        return item;
    }

    /**
     * 帮助菜单"启动时自动检查更新"勾选初始状态（包内可见，供菜单测试）：
     * config 为 null（菜单骨架/配置未加载）时取 Config 默认值（开启），
     * 构建真实 frame 时与 config.isAutoCheckUpdate() 同步（AC-4.2）。
     */
    static boolean initialAutoCheckState(Config config) {
        return config != null ? config.isAutoCheckUpdate() : new Config().isAutoCheckUpdate();
    }

    // ---------------- 便捷功能：主题切换 / 检查更新 / 打开目录 / 关于 ----------------

    /**
     * 主题切换（视图 → 主题 浅色/深色）：
     * apply(boolean) 换配色 + refreshTheme() 即时重刷已捕获旧色的组件 +
     * config.setTheme + ConfigLoader.saveTheme 持久化到 config.json（重启保留）。
     */
    private void switchTheme(boolean darkTheme) {
        if (UITheme.isDark() == darkTheme) {
            return;
        }
        UITheme.apply(darkTheme);
        refreshTheme();
        if (config != null) {
            config.setTheme(darkTheme ? Config.THEME_DARK : Config.THEME_LIGHT);
        }
        ConfigLoader.saveTheme(darkTheme ? Config.THEME_DARK : Config.THEME_LIGHT);
        statusLabel.setText("已切换为" + (darkTheme ? "深色" : "浅色") + "主题");
        appendText("[成功] 已切换为" + (darkTheme ? "深色" : "浅色") + "主题\n");
    }

    /**
     * 主题切换后重刷直接 setBackground/setForeground 捕获旧色的组件
     * （updateComponentTreeUI 只刷新 UIManager 默认值捕获的组件，直接设色的必须显式重刷）。
     */
    void refreshTheme() {
        SwingUtilities.updateComponentTreeUI(this);
        // 日志区 + 滚动区视口/边框
        logArea.setBackground(UITheme.LOG_BG);
        logArea.setForeground(UITheme.LOG_TEXT);
        logScroll.setBackground(UITheme.LOG_BG);
        logScroll.getViewport().setBackground(UITheme.LOG_BG);
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER));
        // 滚动条区显式重刷（轨道/滑块用主题色，Metal L&F 下避免深色主题露浅灰条）
        recolorScrollBars(logScroll);
        // 快捷按钮滚动区 + 面板
        quickScroll.setBackground(UITheme.PANEL_BG);
        quickScroll.getViewport().setBackground(UITheme.PANEL_BG);
        recolorScrollBars(quickScroll);
        quickPanel.setBackground(UITheme.PANEL_BG);
        // 状态栏（标签文字色 + 背景 + 边框）
        statusLabel.setForeground(UITheme.MUTED);
        statusBar.setBackground(UITheme.PANEL_BG);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER));
        statusRight.setBackground(UITheme.PANEL_BG);
        // 顶部功能按钮栏 / 底部区域 / 输入行（updateComponentTreeUI 对纯 JPanel 背景并非总生效，显式重刷）
        topPanel.setBackground(UITheme.PANEL_BG);
        bottomArea.setBackground(UITheme.PANEL_BG);
        inputArea.setBackground(UITheme.PANEL_BG);
        inputRow.setBackground(UITheme.PANEL_BG);
        inputField.setBackground(UITheme.PANEL_BG);
        inputField.setForeground(UITheme.TEXT);
        // 顶部功能按钮逐个重套主题样式（直接捕获了旧色）
        for (JButton b : topButtons) {
            UITheme.styleButton(b);
        }
        UITheme.styleButton(clearLogButton);
        UITheme.styleButton(fontMinusButton);
        UITheme.styleButton(fontPlusButton);
        // 菜单整树重刷（JMenuBar/JMenu/JMenuItem/弹出菜单逐个设色：个别 L&F 不透传 UIManager
        // 颜色键，updateComponentTreeUI 对菜单文字前景并非总生效，深色下会残留深字看不清）
        rethemeMenuTree(getJMenuBar());
        // 主题菜单勾选状态同步
        if (themeLightItem != null && themeDarkItem != null) {
            themeLightItem.setSelected(!UITheme.isDark());
            themeDarkItem.setSelected(UITheme.isDark());
        }
        // 重着色既有日志内容：日志行插入时把当时的 UITheme 颜色写入了字符属性，
        // 主题切换后需按当前主题重新着色（EDT 内，典型日志量级可接受）
        recolorLogContent();
    }

    /** 菜单整树重刷主题色：JMenuBar + 每个 JMenu（含子菜单/菜单项/弹出菜单）逐个显式设色 */
    private static void rethemeMenuTree(JMenuBar bar) {
        if (bar == null) {
            return;
        }
        bar.setBackground(UITheme.PANEL_BG);
        bar.setForeground(UITheme.TEXT);
        for (int i = 0; i < bar.getMenuCount(); i++) {
            rethemeMenu(bar.getMenu(i));
        }
    }

    /** 单个菜单：自身 + 弹出菜单 + 全部菜单组件（子菜单递归）设为主题色 */
    private static void rethemeMenu(JMenu menu) {
        if (menu == null) {
            return;
        }
        menu.setBackground(UITheme.PANEL_BG);
        menu.setForeground(UITheme.TEXT);
        JPopupMenu popup = menu.getPopupMenu();
        popup.setBackground(UITheme.PANEL_BG);
        popup.setForeground(UITheme.TEXT);
        popup.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        for (Component comp : menu.getMenuComponents()) {
            if (comp instanceof JMenu subMenu) {
                rethemeMenu(subMenu);
            } else if (comp instanceof JMenuItem item) {
                item.setBackground(UITheme.PANEL_BG);
                item.setForeground(UITheme.TEXT);
            }
        }
    }

    /** 滚动条区显式重刷：轨道/滑块用当前主题色（Metal/基础 L&F 读取 UIManager 键 + 组件级颜色双保险） */
    private static void recolorScrollBars(JScrollPane scroll) {
        for (JScrollBar sb : new JScrollBar[]{scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar()}) {
            sb.setBackground(UITheme.PANEL_BG);
            sb.setForeground(UITheme.BORDER);
        }
    }

    /**
     * 重着色日志区已有内容：读取 StyledDocument 全文 → remove 后复用 insertStyledLine/segmentStyledLine
     * 按当前 UITheme 颜色重新分段插入，使主题切换后既有日志行（含 [成功]/[失败] 标记着色）不残留旧色。
     */
    private void recolorLogContent() {
        StyledDocument doc = logArea.getStyledDocument();
        int len = doc.getLength();
        if (len == 0) {
            return;
        }
        try {
            String existing = doc.getText(0, len);
            doc.remove(0, len);
            appendStyled(existing);
        } catch (BadLocationException e) {
            // 只操作文档末尾，正常不会发生；失败仅记日志，不影响主题切换
            LoggerUtil.logException("[警告] 主题切换时日志内容重着色失败", e);
        }
    }

    /**
     * 检查更新（帮助 → 检查更新）：后台线程查询 GitHub 最新 Release（最多等 5s），
     * 与 BuildInfo.VERSION 比较；有新版弹提示并可打开下载页，无新版提示已最新，
     * 网络失败仅提示不崩溃。全程不阻塞 EDT。
     * 与启动自动检查共用互斥标志：任一检查进行中，另一触发方直接返回（不重复请求/弹窗）。
     */
    private void runCheckUpdate() {
        if (updateCheckInProgress) {
            appendText("[信息] 正在检查更新，请稍候\n");
            return;
        }
        // 用户手动检查时清除启动自动检查留下的"发现新版本"提示链接（避免误导性残留）
        clearUpdateHint();
        updateCheckInProgress = true;
        statusLabel.setText("正在检查更新...");
        appendText("[信息] 正在检查更新...\n");
        new SwingWorker<UpdateChecker.ReleaseInfo, Void>() {
            @Override
            protected UpdateChecker.ReleaseInfo doInBackground() {
                return UpdateChecker.fetchLatestRelease();
            }

            @Override
            protected void done() {
                try {
                    handleCheckUpdateResult(get());
                } catch (Exception e) {
                    LoggerUtil.logException("[检查更新] 执行异常", e);
                    appendText("[失败] 检查更新异常，详见日志\n");
                    statusLabel.setText("检查更新异常");
                } finally {
                    updateCheckInProgress = false; // 互斥标志复位（含异常路径）
                }
            }
        }.execute();
    }

    /** 检查更新结果处理（EDT，done() 回调）：有新版 / 已最新 / 失败 三分支 */
    private void handleCheckUpdateResult(UpdateChecker.ReleaseInfo info) {
        if (info == null) {
            // 网络失败 / API 不可达 / 证书被拦截 / GITHUB_URL 未配置：静默降级，仅提示不崩溃。
            // 常见原因：网络不可用、GitHub 无法访问、或安全软件/代理拦截 HTTPS（已自动尝试系统证书库）
            appendText("[警告] 检查更新失败（网络不可用/GitHub 无法访问/HTTPS 被拦截），请稍后重试\n");
            statusLabel.setText("检查更新失败");
            JOptionPane.showMessageDialog(this,
                    "检查更新失败\n常见原因：网络不可用、GitHub 无法访问，\n或安全软件/代理拦截了 HTTPS 连接。\n详见日志 logs/frt.log 中的真实原因。",
                    "检查更新", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (UpdateChecker.isNewer(info.tagName(), BuildInfo.VERSION)) {
            appendText("[成功] 发现新版本 " + info.tagName() + "（当前 v" + BuildInfo.VERSION + "）\n");
            statusLabel.setText("发现新版本 " + info.tagName());
            String message = "发现新版本 " + info.tagName() + "\n"
                    + "当前版本: v" + BuildInfo.VERSION + "\n"
                    + "发布名称: " + (info.name() == null || info.name().isBlank() ? "-" : info.name()) + "\n"
                    + "发布时间: " + (info.publishedAt() == null || info.publishedAt().isBlank() ? "-" : info.publishedAt()) + "\n"
                    + "\n是否打开下载页面？";
            Object[] options = {"打开下载页", "取消"};
            int choice = JOptionPane.showOptionDialog(this, message, "发现新版本",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                openDownloadPage(info);
            } else {
                appendText("[取消] 已忽略新版本提示\n");
                statusLabel.setText("已忽略新版本提示");
            }
        } else {
            appendText("[成功] 当前已是最新版本（v" + BuildInfo.VERSION + "）\n");
            statusLabel.setText("当前已是最新版本");
            JOptionPane.showMessageDialog(this, "当前已是最新版本（v" + BuildInfo.VERSION + "）",
                    "检查更新", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** 打开新版下载页（htmlUrl 缺失时回退仓库主页），失败弹提示不崩溃 */
    private void openDownloadPage(UpdateChecker.ReleaseInfo info) {
        // 用户已主动打开下载页：清除状态栏"发现新版本"提示链接（避免误导性残留）
        clearUpdateHint();
        String page = (info.htmlUrl() == null || info.htmlUrl().isBlank())
                ? BuildInfo.GITHUB_URL : info.htmlUrl();
        if (DesktopUtil.openUri(page)) {
            appendText("[成功] 已打开下载页: " + page + "\n");
            statusLabel.setText("已打开下载页");
        } else {
            appendText("[失败] 无法打开下载页，请手动访问: " + page + "\n");
            statusLabel.setText("无法打开下载页");
            JOptionPane.showMessageDialog(this, "无法打开下载页，请手动访问:\n" + page,
                    "打开下载页失败", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ---------------- 启动时自动检查更新（FR-1：后台静默 + 非侵入提示） ----------------

    /** 自动检查结果的三分支（headless 可测） */
    enum AutoCheckOutcome { FAILED, NEW_VERSION, UP_TO_DATE }

    /**
     * 自动检查结果决策（包内可见静态方法，headless 可测）：
     * info==null（网络失败/API 不可达/证书全失败）→ FAILED（UI 完全静默）；
     * 有新版 → NEW_VERSION（非侵入提示）；同版/更旧 → UP_TO_DATE（静默）。
     */
    static AutoCheckOutcome decideAutoCheck(UpdateChecker.ReleaseInfo info, String currentVersion) {
        if (info == null) {
            return AutoCheckOutcome.FAILED;
        }
        return UpdateChecker.isNewer(info.tagName(), currentVersion)
                ? AutoCheckOutcome.NEW_VERSION : AutoCheckOutcome.UP_TO_DATE;
    }

    /**
     * 启动时自动检查更新（构造末尾调用，包内可见）：
     * config 未加载或开关关闭 → 直接返回（完全静默）；已有检查进行中 → 直接返回（防重复请求）。
     * 否则置互斥标志，在 SwingWorker 后台线程执行查询（复用 UpdateChecker 三层证书兜底，走
     * 静默变体 fetchLatestReleaseQuiet），不阻塞 EDT；窗口显示前不弹任何东西。
     */
    void startAutoCheckUpdateIfEnabled() {
        if (config == null || !config.isAutoCheckUpdate()) {
            return; // 配置未加载 / 开关关闭：静默跳过（AC-3.1/3.8）
        }
        if (updateCheckInProgress) {
            return; // 已有检查在进行（如启动瞬间用户已点手动检查），不重复发起请求
        }
        updateCheckInProgress = true;
        new SwingWorker<UpdateChecker.ReleaseInfo, Void>() {
            @Override
            protected UpdateChecker.ReleaseInfo doInBackground() {
                // 极短延迟避免与启动争抢带宽；后台线程 sleep 不阻塞 EDT
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return UpdateChecker.fetchLatestReleaseQuiet();
            }

            @Override
            protected void done() {
                try {
                    handleAutoCheckResult(get());
                } catch (Exception e) {
                    // 结果处理异常：仅日志文件记录，UI 零打扰（启动路径不弹窗）
                    LoggerUtil.getInstance(null).logWarnFileOnly(
                            "[自动检查更新] 结果处理异常（静默，仅文件记录）: " + e.getClass().getSimpleName());
                } finally {
                    updateCheckInProgress = false; // 互斥标志复位（含异常路径）
                }
            }
        }.execute();
    }

    /**
     * 自动检查结果处理（EDT，done() 回调）：非侵入三分支——
     * 失败 / 已最新 → UI 完全静默（不弹窗、状态栏不变、日志区不追加行，仅日志文件保留排查信息）；
     * 发现新版 → 日志区 [信息] 一行 + 状态栏文本 + 状态栏可点击 LinkLabel（打开下载页），不弹模态框。
     */
    private void handleAutoCheckResult(UpdateChecker.ReleaseInfo info) {
        switch (decideAutoCheck(info, BuildInfo.VERSION)) {
            case FAILED -> LoggerUtil.getInstance(null).logWarnFileOnly(
                    "[自动检查更新] 查询 GitHub 最新版失败（静默，仅文件记录）");
            case UP_TO_DATE -> LoggerUtil.getInstance(null).logDebugFileOnly(
                    "[自动检查更新] 当前已是最新版本（静默）");
            case NEW_VERSION -> {
                appendText("[信息] 发现新版本 " + info.tagName() + "（当前 v" + BuildInfo.VERSION
                        + "），可在状态栏点击提示打开下载页\n");
                statusLabel.setText("发现新版本 " + info.tagName());
                showUpdateHint(info);
            }
        }
    }

    /** 状态栏右侧（versionLink 旁）显示可点击的"发现新版本"提示链接，点击复用 openDownloadPage */
    private void showUpdateHint(UpdateChecker.ReleaseInfo info) {
        String page = (info.htmlUrl() == null || info.htmlUrl().isBlank())
                ? BuildInfo.GITHUB_URL : info.htmlUrl();
        LinkLabel hint = new LinkLabel("发现新版本 " + info.tagName(), page);
        hint.setFont(UITheme.SMALL_FONT);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 10));
        // 复用 openDownloadPage（DesktopUtil.openUri，失败弹一次性提示不崩溃）：
        // 移除 LinkLabel 自带的无提示打开行为（其内部 MouseListener 直接 Desktop.browse），
        // 替换为本窗口的 openDownloadPage 回调
        for (MouseListener ml : hint.getMouseListeners()) {
            hint.removeMouseListener(ml);
        }
        hint.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openDownloadPage(info);
            }
        });
        updateHintLink = hint;
        statusRight.add(hint);
        statusRight.revalidate();
        statusRight.repaint();
    }

    /** 清除状态栏"发现新版本"提示链接并恢复默认文案（手动检查/打开下载页时调用） */
    private void clearUpdateHint() {
        if (updateHintLink != null && updateHintLink.getParent() == statusRight) {
            statusRight.remove(updateHintLink);
            statusRight.revalidate();
            statusRight.repaint();
        }
        updateHintLink = null;
        if (statusLabel.getText() != null && statusLabel.getText().startsWith("发现新版本")) {
            statusLabel.setText("就绪");
        }
    }

    /** 帮助菜单"启动时自动检查更新"勾选项切换：更新 config 并即时持久化到 config.json */
    private void toggleAutoCheckUpdate(boolean checked) {
        if (config != null) {
            config.setAutoCheckUpdate(checked);
        }
        ConfigLoader.saveAutoCheckUpdate(checked);
        appendText("[成功] 已" + (checked ? "开启" : "关闭") + "启动时自动检查更新\n");
        statusLabel.setText(checked ? "已开启启动时自动检查更新" : "已关闭启动时自动检查更新");
    }

    /**
     * 一键打开目录（文件 → 打开目录）：更新/目标/删除/备份 目录取 ConfigLoader 静态绝对路径，
     * 日志目录固定 logs/（与 logback.xml 一致）。目录不存在由 DesktopUtil 自动创建后打开；
     * 失败（headless/无 xdg-open 等）弹一次性提示，程序继续运行。
     */
    private void openDirectoryByName(String name) {
        Path dir = switch (name) {
            case "更新目录" -> ConfigLoader.getUpdatePath();
            case "目标目录" -> ConfigLoader.getTargetPath();
            case "删除目录" -> ConfigLoader.getDeletePath();
            case "备份目录" -> ConfigLoader.getBackupPath();
            case "日志目录" -> Path.of("logs");
            default -> null;
        };
        if (dir == null) {
            appendText("[失败] 无法确定" + name + "路径（配置未加载？）\n");
            statusLabel.setText("无法打开" + name);
            return;
        }
        if (DesktopUtil.openDirectory(dir)) {
            appendText("[成功] 已打开" + name + ": " + dir.toAbsolutePath().normalize() + "\n");
            statusLabel.setText("已打开" + name);
        } else {
            appendText("[失败] 无法打开" + name + "，详见日志\n");
            statusLabel.setText("无法打开" + name);
            JOptionPane.showMessageDialog(this,
                    "无法打开" + name + "（headless 环境或系统缺少文件管理器）\n" + dir.toAbsolutePath().normalize(),
                    "打开目录", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 关于对话框（帮助 → 关于）：版本/构建时间/GitHub 链接/许可证，数据全部取自 BuildInfo */
    private void showAbout() {
        new AboutDialog(this).setVisible(true);
    }

    // ---------------- 功能入口（后台线程执行，避免卡住 UI） ----------------

    private void runUpdate() {
        runServiceWithProgress("更新文件", progress -> {
            ProcessingResult r = new FileUpdateServiceNew(config, prompter).updateExecute(progress);
            if (r.isCancelled()) {
                return "已取消更新操作（未执行任何操作）";
            }
            if (r.getSuccessCount() == 0 && r.getErrorCount() == 0) {
                return "没有需要更新的文件";
            }
            return "更新完成: 成功 " + r.getSuccessCount() + "，失败 " + r.getErrorCount() + "，跳过 " + r.getSkipCount();
        });
    }

    private void runDelete() {
        runServiceWithProgress("删除文件", progress -> {
            ProcessingResult r = new FileDeleteService(config, prompter).deleteExecute(progress);
            if (r.isCancelled()) {
                return "已取消删除操作（未执行任何操作）";
            }
            if (r.getSuccessCount() == 0 && r.getErrorCount() == 0) {
                return "没有需要删除的文件";
            }
            return "删除完成: 成功 " + r.getSuccessCount() + "，失败 " + r.getErrorCount();
        });
    }

    private void runRestore() {
        runService("恢复备份", () -> {
            new RestoreService(config, prompter).executeRestore();
            return "恢复流程结束";
        });
    }

    private void runWizard() {
        if (config == null) {
            appendText("[失败] 配置未加载，无法执行\n");
            return;
        }
        // 表单式配置：一次填完所有参数（含策略链），模态弹窗（EDT 上阻塞直到确定/取消）
        RuleWizardForm form = new RuleWizardForm(this, config);
        form.setVisible(true);
        RuleWizardForm.Result result = form.getResult();
        if (result == null) {
            appendText("[取消] 规则生成已取消\n");
            statusLabel.setText("已取消规则生成");
            return;
        }
        runService("规则生成", () -> {
            // 复用控制台向导的预览/确认/写入/自校验公共流程
            new RuleConfigWizard(config, prompter).writeRuleFile(result.rule, result.targetDir);
            return "规则生成结束";
        });
    }

    private void runCleanup() {
        runService("清理残留备份", () -> {
            int deleted = BackupFileLoader.cleanupOrphanBackupFiles(prompter);
            return "清理完成: 删除 " + deleted + " 个残留备份文件";
        });
    }

    /**
     * 打包插件：把 plugins/ 目录下的 .java 策略源码编译打包成 jar（下次启动自动加载）
     */
    private void runPluginBuild() {
        runService("打包插件", () -> {
            PluginCompiler.CompileResult r = PluginCompiler.compilePluginsToJar(Path.of("plugins"));
            return r.isSuccess() ? r.getMessage() : "[失败] " + r.getMessage();
        });
    }

    /**
     * 核心配置编写向导（表单式）：设置 更新/目标/删除/备份目录 与日志级别，
     * 保存走 CoreConfigWizard 公共流程（预览/确认/自动创建缺失目录/写入/自校验）
     */
    private void runConfig() {
        if (config == null) {
            appendText("[失败] 配置未加载，无法执行\n");
            return;
        }
        ConfigFormDialog dialog = new ConfigFormDialog(this, config);
        dialog.setVisible(true); // 模态：EDT 上阻塞直到确定/取消
        ConfigFormDialog.Result result = dialog.getResult();
        if (result == null) {
            appendText("[取消] 核心配置已取消\n");
            statusLabel.setText("已取消核心配置");
            return;
        }
        runService("核心配置", () -> {
            boolean ok = new CoreConfigWizard(config, prompter)
                    .writeFromValues(result.updatePath, result.targetPath,
                            result.deletePath, result.backupPath, result.logLevel);
            return ok ? "核心配置保存完成（下次启动生效）" : "核心配置未保存";
        });
    }

    private void runService(String name, java.util.function.Supplier<String> task) {
        if (config == null) {
            appendText("[失败] 配置未加载，无法执行\n");
            return;
        }
        setTopButtonsEnabled(false);
        setInputEnabled(false);
        statusLabel.setText(name + " 执行中...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return task.get();
                } catch (Exception e) {
                    LoggerUtil.logException("[" + name + "] 执行失败", e);
                    return "[" + name + "] 执行失败，详见日志";
                }
            }

            @Override
            protected void done() {
                try {
                    String summary = get();
                    appendText("\n===== " + summary + " =====\n");
                    statusLabel.setText(summary);
                } catch (Exception e) {
                    statusLabel.setText(name + " 执行异常");
                }
                setTopButtonsEnabled(true);
                setInputEnabled(false);
            }
        }.execute();
    }

    /**
     * 带进度条的异步服务执行（更新/删除用）：
     * 服务通过 ProgressCallback 上报（已处理数/总数/当前文件），
     * SwingWorker 批量刷新进度条与状态栏，不阻塞 EDT。
     */
    private void runServiceWithProgress(String name, java.util.function.Function<ProgressCallback, String> task) {
        if (config == null) {
            appendText("[失败] 配置未加载，无法执行\n");
            return;
        }
        setTopButtonsEnabled(false);
        setInputEnabled(false);
        statusLabel.setText(name + " 执行中...");
        progressBar.setValue(0);
        progressBar.setMaximum(100);
        progressBar.setString("准备中...");
        progressBar.setVisible(true);
        new SwingWorker<String, int[]>() {
            private volatile String currentFile = "";

            @Override
            protected String doInBackground() {
                try {
                    return task.apply((processed, total, current) -> {
                        currentFile = current == null ? "" : current;
                        publish(new int[]{processed, Math.max(total, 0)});
                    });
                } catch (Exception e) {
                    LoggerUtil.logException("[" + name + "] 执行失败", e);
                    return "[" + name + "] 执行失败，详见日志";
                }
            }

            @Override
            protected void process(List<int[]> chunks) {
                int[] last = chunks.get(chunks.size() - 1);
                progressBar.setMaximum(Math.max(1, last[1]));
                progressBar.setValue(last[0]);
                progressBar.setString(last[0] + " / " + last[1]);
                String cur = currentFile;
                statusLabel.setText(name + " 执行中" + (cur.isEmpty() ? "" : ": " + cur));
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    String summary = get();
                    appendText("\n===== " + summary + " =====\n");
                    statusLabel.setText(summary);
                } catch (Exception e) {
                    statusLabel.setText(name + " 执行异常");
                }
                setTopButtonsEnabled(true);
                setInputEnabled(false);
            }
        }.execute();
    }

    // ---------------- 输入区（SwingPrompter.InputPanel） ----------------

    @Override
    public void showPrompt(String prompt) {
        // EDT：按提示生成快捷按钮、显示输入区并聚焦（提示全文已在日志区，不重复展示）
        statusLabel.setText("等待输入：请在下方输入或点击快捷按钮");
        rebuildQuickButtons(prompt);
        inputField.setText("");
        setInputEnabled(true);
        inputArea.setVisible(true);
        inputField.requestFocusInWindow();
    }

    @Override
    public void resetInput() {
        setInputEnabled(false);
        inputArea.setVisible(false);
        quickPanel.removeAll();
        quickPanel.revalidate();
        quickPanel.repaint();
        quickScroll.revalidate();
        quickScroll.repaint();
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        submitButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
    }

    private void setTopButtonsEnabled(boolean enabled) {
        for (JButton b : topButtons) {
            b.setEnabled(enabled);
        }
    }

    // ---------------- 日志字体大小调整（A-/A+） ----------------

    /** 调整日志字体大小（delta=±1），即时生效并持久化到 config.json，重启后保留 */
    private void adjustLogFontSize(int delta) {
        int next = Math.max(Config.MIN_LOG_FONT_SIZE,
                Math.min(Config.MAX_LOG_FONT_SIZE, logFontSize + delta));
        if (next == logFontSize) {
            return;
        }
        logFontSize = next;
        applyLogFontSize(next);
        if (config != null) {
            config.setLogFontSize(next);
        }
        ConfigLoader.saveLogFontSize(next);
        statusLabel.setText("日志字体: " + next + "px");
        updateFontButtons();
    }

    /** 只改字号，保留当前字体族（已插入的日志文本会随组件字体联动重绘） */
    private void applyLogFontSize(int size) {
        Font font = logArea.getFont();
        logArea.setFont(new Font(font.getFamily(), Font.PLAIN, size));
    }

    /** 到达可调范围边界时禁用对应按钮（视图菜单的 缩小/放大 项联动） */
    private void updateFontButtons() {
        fontMinusButton.setEnabled(logFontSize > Config.MIN_LOG_FONT_SIZE);
        fontPlusButton.setEnabled(logFontSize < Config.MAX_LOG_FONT_SIZE);
        if (fontMinusMenuItem != null) {
            fontMinusMenuItem.setEnabled(fontMinusButton.isEnabled());
        }
        if (fontPlusMenuItem != null) {
            fontPlusMenuItem.setEnabled(fontPlusButton.isEnabled());
        }
    }

    private void submitInput() {
        if (prompter != null) {
            prompter.submit(inputField.getText());
        }
    }

    /**
     * 按提示重建快捷按钮（清空 + 生成 + 绑定提交回调，封装在 QuickButtonPanel.show）
     */
    private void rebuildQuickButtons(String prompt) {
        quickPanel.show(prompt, value -> prompter.submit(value));
        quickScroll.revalidate();
        quickScroll.repaint();
    }

    // ---------------- 日志区输出与提示捕获 ----------------

    private void redirectOutput() {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        OutputStream logStream = new OutputStream() {
            @Override
            public void write(int b) {
                appendText(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendText(new String(b, off, len, StandardCharsets.UTF_8));
            }
        };
        System.setOut(new PrintStream(new TeeOutputStream(originalOut, logStream), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new TeeOutputStream(originalErr, logStream), true, StandardCharsets.UTF_8));
    }

    /** 日志区当前末尾是否以换行结尾（用于输入提交时判断是否要补换行，避免日志与提示拼接同行） */
    private volatile boolean lastOutputEndsWithNewline = true;

    private void appendText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        lastOutputEndsWithNewline = text.charAt(text.length() - 1) == '\n';
        SwingUtilities.invokeLater(() -> appendStyled(text));
        feedPrompt(text);
    }

    @Override
    public void ensureLineBreak() {
        // 提示行如 "是否执行以上 8 个更新操作？(y/n): " 以非换行结尾（print 输出），
        // 终端里用户回车自带换行，日志区没有——提交输入时补一个换行
        if (!lastOutputEndsWithNewline) {
            appendText("\n");
        }
    }

    /**
     * 按行追加到日志区，每行按行首 [标记] 着色（[成功]=绿 / [失败]=红 / [警告]=橙 / 交互提示=蓝 / 说明=灰，
     * 未识别标记与普通文本用默认浅色；===== 标题分隔线用亮蓝加粗）
     */
    private void appendStyled(String text) {
        StyledDocument doc = logArea.getStyledDocument();
        int start = 0;
        int nl;
        while ((nl = text.indexOf('\n', start)) >= 0) {
            insertStyledLine(doc, text.substring(start, nl + 1));
            start = nl + 1;
        }
        if (start < text.length()) {
            insertStyledLine(doc, text.substring(start));
        }
        logArea.setCaretPosition(doc.getLength());
    }

    private void insertStyledLine(StyledDocument doc, String line) {
        if (line.isEmpty()) {
            return;
        }
        try {
            for (LineSegment seg : segmentStyledLine(line)) {
                doc.insertString(doc.getLength(), seg.text(), seg.style());
            }
        } catch (BadLocationException ignored) {
            // 只追加在文档末尾，正常不会发生
        }
    }

    /** 日志行的一个着色片段：text 显示文本 + style 样式（包内可见供测试） */
    record LineSegment(String text, SimpleAttributeSet style) {
    }

    /** 行内特殊标记：固定（永久保留，不受淘汰影响），紫色加粗凸显 */
    private static final String PINNED_TOKEN = "[固定]";
    /** 行内错误标记：[!]（预览/恢复失败提示）只标记本身红色，路径与原因保持中性可读 */
    private static final String ERROR_TOKEN = "[!]";
    /** JSON 键名："key" :（规则/配置预览里的参数名，如黑白名单 patterns/excludePatterns） */
    private static final Pattern JSON_KEY = Pattern.compile("\"([^\"]+)\"\\s*:");
    /** logback 时间戳+级别前缀（CONSOLE pattern：%d{HH:mm:ss.SSS} %-5level %msg） */
    private static final Pattern LOG_PREFIX = Pattern.compile("^\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}\\s+\\S+\\s+");

    /**
     * 把一行拆分为若干着色片段（包内可见供测试）：
     * 整行先按 styleForLine 取基础样式，再扫描行内特殊 token 单独着色——
     * logback 时间戳前缀灰色、[固定] 紫色加粗、[!] 红色、JSON 键名蓝色（预览里的参数名醒目）。
     */
    static List<LineSegment> segmentStyledLine(String line) {
        List<LineSegment> segments = new ArrayList<>();
        int contentStart = 0;
        Matcher pm = LOG_PREFIX.matcher(line);
        if (pm.find()) {
            contentStart = pm.end();
            SimpleAttributeSet prefixStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(prefixStyle, UITheme.LOG_MUTED);
            segments.add(new LineSegment(line.substring(0, contentStart), prefixStyle));
        }
        String content = line.substring(contentStart);
        SimpleAttributeSet base = styleForLine(content);
        int idx = 0;
        int n = content.length();
        while (idx < n) {
            int pin = content.indexOf(PINNED_TOKEN, idx);
            int err = content.indexOf(ERROR_TOKEN, idx);
            Matcher m = JSON_KEY.matcher(content);
            int keyStart = -1;
            int keyEnd = -1;
            if (m.find(idx)) {
                keyStart = m.start();
                keyEnd = m.end();
            }
            // 选最近的 token 起点
            int next = n;
            boolean isPinned = false;
            boolean isError = false;
            boolean isKey = false;
            if (pin >= 0 && pin < next) {
                next = pin;
                isPinned = true;
            }
            if (err >= 0 && err < next) {
                next = err;
                isPinned = false;
                isError = true;
            }
            if (keyStart >= 0 && keyStart < next) {
                next = keyStart;
                isPinned = false;
                isError = false;
                isKey = true;
            }
            if (next > idx) {
                segments.add(new LineSegment(content.substring(idx, next), base));
            }
            if (next == n) {
                break;
            }
            if (isPinned) {
                segments.add(new LineSegment(PINNED_TOKEN, pinnedStyle()));
                idx = pin + PINNED_TOKEN.length();
            } else if (isError) {
                segments.add(new LineSegment(ERROR_TOKEN, errorStyle()));
                idx = err + ERROR_TOKEN.length();
            } else {
                segments.add(new LineSegment(content.substring(keyStart, keyEnd), keyStyle()));
                idx = keyEnd;
            }
        }
        return segments;
    }

    private static SimpleAttributeSet pinnedStyle() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, UITheme.LOG_PINNED);
        StyleConstants.setBold(s, true);
        return s;
    }

    private static SimpleAttributeSet errorStyle() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, UITheme.LOG_ERROR);
        return s;
    }

    private static SimpleAttributeSet keyStyle() {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, UITheme.LOG_ACCENT);
        return s;
    }

    /** 按行首 [标记] 挑选颜色（包内可见供样式测试）
     *  配色原则（用户拍板）：站在"用户使用功能时哪些信息必看/必操作"角度——
     *  可操作提示、可选项列表(1-xx/2-xx)、功能标题、交互输入 → 醒目蓝；
     *  状态反馈(成功/失败/警告) → 绿/红/橙；次要信息(说明/文件树/普通文本/装饰框) → 白/灰。
     *  （灰字配灰底阅读累，用户必看的信息不能用灰色）
     *  规则顺序：装饰框 → 摘要标题 → 行首标记 → 编号选项列表 → 操作提示 → 预览动作 → >>回显 → 默认 */
    static SimpleAttributeSet styleForLine(String line) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, UITheme.LOG_TEXT);
        String trimmed = line.trim();
        // 1. 纯装饰框/分隔线：一整行只有 = 或 -（向导标题框、列表分隔线）→ 灰，低调
        if (trimmed.matches("^[-=]{4,}$")) {
            StyleConstants.setForeground(s, UITheme.LOG_MUTED);
            return s;
        }
        // 2. 摘要标题：===== 文字 =====（结果汇总）→ 亮蓝加粗
        if (trimmed.startsWith("=====")) {
            StyleConstants.setForeground(s, UITheme.LOG_TITLE);
            StyleConstants.setBold(s, true);
            return s;
        }
        // 3. 行首 [标记]：成功绿 / 失败红 / 警告橙 / 交互输入蓝 / 功能标题青 / 其余说明灰
        String tag = tagOf(line);
        if (tag != null) {
            switch (tag) {
                case "成功" -> { StyleConstants.setForeground(s, UITheme.LOG_SUCCESS); return s; }
                case "失败", "错误" -> { StyleConstants.setForeground(s, UITheme.LOG_ERROR); return s; }
                case "警告", "取消", "跳过" -> { StyleConstants.setForeground(s, UITheme.LOG_WARN); return s; }
                case "输入", "选择", "确认" ->
                        { StyleConstants.setForeground(s, UITheme.LOG_ACCENT); return s; }
                case "执行", "列表", "预览", "信息", "校验", "规则生成", "核心配置", "FILE", "STATS" ->
                        { StyleConstants.setForeground(s, UITheme.LOG_HEADING); StyleConstants.setBold(s, true); return s; }
                default -> { StyleConstants.setForeground(s, UITheme.LOG_MUTED); return s; }
            }
        }
        // 4. 编号选项列表（用户要从中选择）：1. / 0. / -1. / 1-3. / 备份记录行 → 蓝
        if (OPTION_LINE.matcher(line).find()) {
            StyleConstants.setForeground(s, UITheme.LOG_ACCENT);
            return s;
        }
        // 5. 操作提示（用户必看/必操作）：请输入… / 操作：y=… / (y/n) 快捷键说明 → 蓝
        if (OPTION_HINT.matcher(line).find()) {
            StyleConstants.setForeground(s, UITheme.LOG_ACCENT);
            return s;
        }
        // 6. 预览操作类型（重点动作）：新增=绿、删除=橙；替换等常态操作保持中性
        //    （预览行格式 "[+] 新增: path"，动词后带冒号，避免误伤选项文本里的"删除"字样）
        //    [!] 错误行整行不上红：由 segmentStyledLine 只给 [!] 标记分段上红，路径/原因保持中性可读
        if (line.contains("新增:")) { StyleConstants.setForeground(s, UITheme.LOG_SUCCESS); return s; }
        if (line.contains("删除:")) { StyleConstants.setForeground(s, UITheme.LOG_WARN); return s; }
        // 7. 输入回显：灰（次要信息）
        if (trimmed.startsWith(">>")) {
            StyleConstants.setForeground(s, UITheme.LOG_MUTED);
            return s;
        }
        // 其余（文件树 / 普通文本）：默认浅色，不额外上色
        return s;
    }

    /** 编号选项行：1. / 0. / -1. / 1-3.（含中文标点变体）——用户要从中选择，醒目蓝 */
    private static final Pattern OPTION_LINE =
            Pattern.compile("^\\s*-?\\d+(-\\d+)?[.、:：)）]\\s");
    /** 操作提示：请输入… / 操作：… / (y/n)、(y/p) 等快捷键说明——用户必看必操作，醒目蓝 */
    private static final Pattern OPTION_HINT =
            Pattern.compile("^\\s*请输入|^\\s*操作[：:]|[(（][yYpPnN][/，,、]");

    /** 提取行首的 [标记]（容忍前导空白与 logback 时间戳前缀，如 "21:20:05.718 INFO  [成功] ..."）；
     *  只在行首识别，JSON 数组值、编号行等行内 [xxx] 不算标记；
     *  [+]/[-]/[=]/[!]/[→]/[○] 等图标符号也不算标记，返回 null（包内可见供样式测试） */
    static String tagOf(String line) {
        String t = line.trim();
        // 跳过 logback 时间戳+级别前缀（logback.xml CONSOLE pattern：%d{HH:mm:ss.SSS} %-5level %msg）
        t = t.replaceFirst("^\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}\\s+(INFO|WARN|ERROR|DEBUG|TRACE)\\s+", "");
        if (!t.startsWith("[")) {
            return null;
        }
        int end = t.indexOf(']', 1);
        if (end > 1) {
            String tag = t.substring(1, end).trim();
            if (!tag.isEmpty() && tag.length() <= 12
                    && tag.matches(".*[A-Za-z0-9\\u4e00-\\u9fff].*")) {
                return tag;
            }
        }
        return null;
    }

    private void feedPrompt(String text) {
        if (promptBuffer.length() + text.length() > PROMPT_BUFFER_MAX) {
            // 限长保护：超长时截断头部，保留最近内容
            promptBuffer.delete(0, Math.min(promptBuffer.length(), text.length()));
        }
        promptBuffer.append(text);
    }

    /**
     * 取走提示缓冲（消费式）：返回自上次输入以来的完整提示文本并清空
     */
    @Override
    public String takePrompt() {
        String prompt = promptBuffer.toString();
        promptBuffer.setLength(0);
        return prompt;
    }

    // ---------------- 双写流 ----------------

    private static class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            first.write(b, off, len);
            second.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
