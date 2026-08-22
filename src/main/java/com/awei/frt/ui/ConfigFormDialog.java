package com.awei.frt.ui;

import com.awei.frt.model.Config;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 核心配置编写向导 —— 表单式（一次填完所有路径与日志级别）
 * 编辑 config.json 的 更新/目标/删除/备份目录 与日志级别；
 * 字段留空 = 保留当前值。保存（预览/确认/写入/自校验）走 CoreConfigWizard 公共流程。
 */
public class ConfigFormDialog extends JDialog {

    /** 表单结果（各字段已解析；为 null 表示取消） */
    public static class Result {
        public final Path updatePath;
        public final Path targetPath;
        public final Path deletePath;
        public final Path backupPath;
        public final String logLevel;

        Result(Path updatePath, Path targetPath, Path deletePath, Path backupPath, String logLevel) {
            this.updatePath = updatePath;
            this.targetPath = targetPath;
            this.deletePath = deletePath;
            this.backupPath = backupPath;
            this.logLevel = logLevel;
        }
    }

    private static final String[] LOG_LEVELS = {"INFO", "DEBUG", "WARN", "ERROR"};

    private final Config config;
    private Result result;

    private final JTextField updateField = new JTextField(30);
    private final JTextField targetField = new JTextField(30);
    private final JTextField deleteField = new JTextField(30);
    private final JTextField backupField = new JTextField(30);
    private final JComboBox<String> logLevelCombo = new JComboBox<>(LOG_LEVELS);

    public ConfigFormDialog(Frame owner, Config config) {
        super(owner, "核心配置 - 表单式编写", true);
        this.config = config;
        UITheme.apply();
        buildContent();
        pack();
        setSize(620, 430);
        setLocationRelativeTo(owner);
        prefill();
    }

    /** 打开表单（模态）后的结果；取消返回 null */
    public Result getResult() {
        return result;
    }

    private void buildContent() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UITheme.PANEL_BG);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 12, 6, 12);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int row = 0;

        // 基准目录（只读说明）
        c.gridy = row++;
        JLabel baseLabel = new JLabel("基准目录（固定）: " + (config != null ? config.getBaseDirectory() : "?"));
        baseLabel.setFont(UITheme.SMALL_FONT);
        baseLabel.setForeground(UITheme.MUTED);
        form.add(baseLabel, c);

        // 路径字段
        c.gridy = row++;
        form.add(fieldRow("更新目录 updatePath:", updateField, "更新文件所在目录（相对基准目录或绝对路径）"), c);
        c.gridy = row++;
        form.add(fieldRow("目标目录 targetPath:", targetField, "文件将被更新/删除到的目标目录"), c);
        c.gridy = row++;
        form.add(fieldRow("删除目录 deletePath:", deleteField, "删除操作规则文件所在目录"), c);
        c.gridy = row++;
        form.add(fieldRow("备份目录 backupPath:", backupField, "备份文件存放目录"), c);

        // 日志级别
        c.gridy = row++;
        JPanel logRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        logRow.setBackground(UITheme.PANEL_BG);
        logRow.add(new JLabel("日志级别 logLevel:"));
        logRow.add(logLevelCombo);
        form.add(logRow, c);

        // 提示
        c.gridy = row++;
        JLabel hint = new JLabel("提示：留空 = 保留当前值；目录不存在时确认后自动创建；修改在下次启动时生效。");
        hint.setFont(UITheme.SMALL_FONT);
        hint.setForeground(UITheme.MUTED);
        form.add(hint, c);

        // 按钮
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(UITheme.PANEL_BG);
        JButton okButton = new JButton("确定保存");
        UITheme.stylePrimaryButton(okButton);
        okButton.addActionListener(e -> onOk());
        JButton cancelButton = new JButton("取消");
        UITheme.styleButton(cancelButton);
        cancelButton.addActionListener(e -> dispose());
        buttons.add(okButton);
        buttons.add(cancelButton);
        c.gridy = row++;
        form.add(buttons, c);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new JScrollPane(form), BorderLayout.CENTER);
    }

    private JPanel fieldRow(String label, JTextField field, String hint) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setBackground(UITheme.PANEL_BG);
        row.add(new JLabel(label));
        row.add(field);
        if (hint != null && !hint.isEmpty()) {
            JLabel h = new JLabel(hint);
            h.setFont(UITheme.SMALL_FONT);
            h.setForeground(UITheme.MUTED);
            row.add(h);
        }
        return row;
    }

    /** 预填当前配置值 */
    private void prefill() {
        if (config == null) {
            return;
        }
        updateField.setText(str(config.getUpdatePath()));
        targetField.setText(str(config.getTargetPath()));
        deleteField.setText(str(config.getDeletePath()));
        backupField.setText(str(config.getBackupPath()));
        String level = config.getLogLevel() == null ? "INFO" : config.getLogLevel().toUpperCase();
        logLevelCombo.setSelectedItem(containsLogLevel(level) ? level : "INFO");
    }

    private static String str(Path p) {
        return p == null ? "" : p.toString();
    }

    private static boolean containsLogLevel(String s) {
        for (String l : LOG_LEVELS) {
            if (l.equals(s)) {
                return true;
            }
        }
        return false;
    }

    private void onOk() {
        // 留空 = 保留当前值
        Path update = parseOrKeep(updateField.getText(), config != null ? config.getUpdatePath() : null);
        Path target = parseOrKeep(targetField.getText(), config != null ? config.getTargetPath() : null);
        Path delete = parseOrKeep(deleteField.getText(), config != null ? config.getDeletePath() : null);
        Path backup = parseOrKeep(backupField.getText(), config != null ? config.getBackupPath() : null);
        String level = (String) logLevelCombo.getSelectedItem();
        result = new Result(update, target, delete, backup, level);
        dispose();
    }

    private static Path parseOrKeep(String text, Path current) {
        if (text == null || text.trim().isEmpty()) {
            return current;
        }
        return Paths.get(text.trim());
    }
}
