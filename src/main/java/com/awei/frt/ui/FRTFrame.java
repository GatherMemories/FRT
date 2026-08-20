package com.awei.frt.ui;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.ProgressCallback;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.service.FileDeleteService;
import com.awei.frt.service.FileUpdateServiceNew;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.HeadlessException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

    private final JTextArea logArea;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JPanel quickPanel;
    private final JPanel inputArea;          // 快捷按钮 + 输入行（等待输入时显示）
    private final JTextField inputField;
    private final JButton submitButton;
    private final JButton cancelButton;
    private final List<JButton> topButtons = new ArrayList<>();
    // 提示缓冲：累积"自上次输入以来"打印的全部文本（结构树/选项列表/说明）
    private final StringBuilder promptBuffer = new StringBuilder();
    private static final int PROMPT_BUFFER_MAX = 20000;
    private static final Pattern OPTION_RANGE = Pattern.compile("1\\s*-\\s*(\\d+)");
    private Config config;
    private SwingPrompter prompter;

    public FRTFrame() throws HeadlessException {
        super("FRT - 多层级文件夹更新系统");
        UITheme.apply(); // 全局主题：统一字体/颜色/间距
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 600);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(UITheme.MONO_FONT);
        logArea.setBackground(UITheme.PANEL_BG);
        logArea.setForeground(UITheme.TEXT);
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        // 顶部功能按钮
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        top.add(topButton("更新文件", this::runUpdate));
        top.add(topButton("删除文件", this::runDelete));
        top.add(topButton("恢复备份", this::runRestore));
        top.add(topButton("规则生成", this::runWizard));
        top.add(topButton("清理残留备份", this::runCleanup));
        JButton clearLogButton = new JButton("清空日志");
        UITheme.styleButton(clearLogButton);
        clearLogButton.addActionListener(e -> logArea.setText(""));
        top.add(clearLogButton);
        add(top, BorderLayout.NORTH);

        // 底部区域：输入区（等待输入时显示）+ 最底部状态栏
        // 快捷按钮自动换行（不设滚动条，避免滚动条遮住按钮；按钮多时换行显示）
        quickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
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
        inputArea.add(quickPanel, BorderLayout.NORTH);
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

    private void submitInput() {
        if (prompter != null) {
            prompter.submit(inputField.getText());
        }
    }

    /**
     * 按提示生成快捷按钮：
     * - 含 (y/n) → 是/否
     * - 含 "1-N" 数字范围 → 1..N 数字按钮（上限 12）
     * - 含 编号/选项 但无范围 → 1..9
     * - 含 "0" / "-1" 特殊选项 → 对应按钮
     * - 始终附 取消
     */
    private void rebuildQuickButtons(String prompt) {
        quickPanel.removeAll();
        if (prompt.contains("(y/n)")) {
            addQuick("是", "y");
            addQuick("否", "n");
        } else {
            int max = parseMaxOption(prompt);
            if (max == 0 && (prompt.contains("编号") || prompt.contains("选项"))) {
                max = 9;
            }
            if (max > 0) {
                int upper = Math.min(max, 20);
                for (int i = 1; i <= upper; i++) {
                    addQuick(String.valueOf(i), String.valueOf(i));
                }
            }
            if (prompt.contains("0")) {
                addQuick("0", "0");
            }
            if (prompt.contains("-1")) {
                addQuick("-1", "-1");
            }
        }
        addQuick("取消", "");
        quickPanel.revalidate();
        quickPanel.repaint();
    }

    private void addQuick(String label, String value) {
        JButton b = new JButton(label);
        b.addActionListener(e -> prompter.submit(value));
        quickPanel.add(b);
    }

    private static int parseMaxOption(String prompt) {
        int max = 0;
        Matcher m = OPTION_RANGE.matcher(prompt);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v > max) {
                    max = v;
                }
            } catch (NumberFormatException ignored) {
                // 忽略异常格式
            }
        }
        return max;
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

    private void appendText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            logArea.append(text);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        feedPrompt(text);
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
