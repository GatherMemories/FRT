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
import com.awei.frt.util.LoggerUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.HeadlessException;
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
    private final JPanel inputArea;          // 快捷按钮 + 输入行（等待输入时显示）
    private final JTextField inputField;
    private final JButton submitButton;
    private final JButton cancelButton;
    private final JButton fontMinusButton; // 日志字体缩小（A-）
    private final JButton fontPlusButton;  // 日志字体放大（A+）
    private int logFontSize = 13;          // 当前日志字体大小（可调 10~24，持久化到 config.json）
    private final List<JButton> topButtons = new ArrayList<>();
    // 提示缓冲：累积"自上次输入以来"打印的全部文本（结构树/选项列表/说明）
    private final StringBuilder promptBuffer = new StringBuilder();
    private static final int PROMPT_BUFFER_MAX = 20000;
    private static final int QUICK_MAX_VISIBLE_HEIGHT = 96; // 快捷按钮区最多显示约 3 行，超出滚动查看
    private static final int QUICK_MIN_HEIGHT = 30;
    private Config config;
    private SwingPrompter prompter;

    public FRTFrame() throws HeadlessException {
        super("多层级文件夹更新工具");
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
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBackground(UITheme.LOG_BG);
        logScroll.getViewport().setBackground(UITheme.LOG_BG); // 视口同色，避免白底四周露边
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER)); // 顶部分隔线，与按钮区分界清晰
        logScroll.getVerticalScrollBar().setUnitIncrement(24);
        logScroll.getHorizontalScrollBar().setUnitIncrement(24);
        add(logScroll, BorderLayout.CENTER);

        // 顶部功能按钮
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        top.add(topButton("更新文件", this::runUpdate));
        top.add(topButton("删除文件", this::runDelete));
        top.add(topButton("恢复备份", this::runRestore));
        top.add(topButton("规则生成", this::runWizard));
        top.add(topButton("清理残留备份", this::runCleanup));
        top.add(topButton("核心配置", this::runConfig));
        top.add(topButton("打包插件", this::runPluginBuild));
        JButton clearLogButton = new JButton("清空日志");
        UITheme.styleButton(clearLogButton);
        clearLogButton.addActionListener(e -> logArea.setText(""));
        top.add(clearLogButton);
        // 日志字体大小调整（A-/A+）：即时生效并持久化到 config.json，重启后保留
        fontMinusButton = new JButton("A-");
        UITheme.styleButton(fontMinusButton);
        fontMinusButton.setToolTipText("缩小日志字体（最小 " + Config.MIN_LOG_FONT_SIZE + "px）");
        fontMinusButton.addActionListener(e -> adjustLogFontSize(-1));
        top.add(fontMinusButton);
        fontPlusButton = new JButton("A+");
        UITheme.styleButton(fontPlusButton);
        fontPlusButton.setToolTipText("放大日志字体（最大 " + Config.MAX_LOG_FONT_SIZE + "px）");
        fontPlusButton.addActionListener(e -> adjustLogFontSize(1));
        top.add(fontPlusButton);
        add(top, BorderLayout.NORTH);

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
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
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
        inputArea.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        inputArea.add(quickScroll, BorderLayout.NORTH);
        inputArea.add(inputRow, BorderLayout.SOUTH);
        inputArea.setVisible(false); // 平时隐藏，等待输入时才出现

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(UITheme.SMALL_FONT);
        statusLabel.setForeground(UITheme.MUTED);
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        statusBar.setBackground(UITheme.PANEL_BG);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER));
        statusBar.add(statusLabel);

        // 进度条：更新/删除真实执行阶段显示，平时隐藏
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(UITheme.SMALL_FONT);
        progressBar.setVisible(false);

        JPanel bottomArea = new JPanel(new BorderLayout());
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
        }
        updateFontButtons();
        prompter = new SwingPrompter(this, this);
        if (config == null) {
            appendText("[失败] 配置加载失败，请检查 config.json\n");
            LoggerUtil.logError("[失败] 配置加载失败，请检查配置文件");
        }
        // 残留备份过多时提醒清理
        BackupFileLoader.warnOrphanBackupsIfNeeded();
    }

    private JButton topButton(String label, Runnable action) {
        JButton b = new JButton(label);
        UITheme.styleButton(b);
        b.addActionListener(e -> action.run());
        topButtons.add(b);
        return b;
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

    /** 到达可调范围边界时禁用对应按钮 */
    private void updateFontButtons() {
        fontMinusButton.setEnabled(logFontSize > Config.MIN_LOG_FONT_SIZE);
        fontPlusButton.setEnabled(logFontSize < Config.MAX_LOG_FONT_SIZE);
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
