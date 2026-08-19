package com.awei.frt.ui;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.HeadlessException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * FRT Swing 主窗口（P3 UI 化基础版）
 * - 顶部：5 个功能按钮（更新/删除/恢复/规则向导/清理备份）
 * - 中部：日志区（捕获 System.out/err 实时显示）
 * - 底部：状态栏
 * 交互确认改为对话框（SwingPrompter）；服务层通过 UserPrompter 抽象复用同一套逻辑。
 */
public class FRTFrame extends JFrame implements SwingPrompter.PromptSource {

    private final JTextArea logArea;
    private final JLabel statusLabel;
    private final StringBuilder pendingLine = new StringBuilder();
    private String lastCompleteLine = "";
    private Config config;
    private UserPrompter prompter;

    public FRTFrame() throws HeadlessException {
        super("FRT - 多层级文件夹更新系统");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 520);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        top.add(makeButton("更新文件", this::runUpdate));
        top.add(makeButton("删除文件", this::runDelete));
        top.add(makeButton("恢复备份", this::runRestore));
        top.add(makeButton("规则生成", this::runWizard));
        top.add(makeButton("清理残留备份", this::runCleanup));
        add(top, BorderLayout.NORTH);

        statusLabel = new JLabel("就绪");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottom.add(statusLabel);
        add(bottom, BorderLayout.SOUTH);

        // 捕获 System.out/err 到日志区（双写：控制台 + 窗口），供 SwingPrompter 取提示行
        redirectOutput();

        // 初始化配置与日志（LoggerUtil 会在 tee 之后初始化，其输出链仍回到日志区）
        config = ConfigLoader.getConfig();
        prompter = new SwingPrompter(this, this);
        if (config == null) {
            appendText("[失败] 配置加载失败，请检查 config.json\n");
            LoggerUtil.logError("[失败] 配置加载失败，请检查配置文件");
        }
    }

    private JButton makeButton(String label, Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        return b;
    }

    // ---------------- 功能入口（后台线程执行，避免卡住 UI） ----------------

    private void runUpdate() {
        runService("更新文件", () -> {
            ProcessingResult r = new FileUpdateServiceNew(config, prompter).updateExecute();
            return "更新完成: 成功 " + r.getSuccessCount() + "，失败 " + r.getErrorCount() + "，跳过 " + r.getSkipCount();
        });
    }

    private void runDelete() {
        runService("删除文件", () -> {
            ProcessingResult r = new FileDeleteService(config, prompter).deleteExecute();
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
        runService("规则向导", () -> {
            new RuleConfigWizard(config, prompter).start();
            return "向导结束";
        });
    }

    private void runCleanup() {
        runService("清理备份", () -> {
            int deleted = BackupFileLoader.cleanupOrphanBackupFiles(prompter);
            return "清理完成: 删除 " + deleted + " 个孤立备份文件";
        });
    }

    private void runService(String name, java.util.function.Supplier<String> task) {
        if (config == null) {
            appendText("[失败] 配置未加载，无法执行\n");
            return;
        }
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
            }
        }.execute();
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
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lastCompleteLine = pendingLine.toString();
                pendingLine.setLength(0);
            } else {
                pendingLine.append(c);
            }
        }
    }

    @Override
    public String lastPrompt() {
        if (pendingLine.length() > 0) {
            return pendingLine.toString();
        }
        return lastCompleteLine;
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
