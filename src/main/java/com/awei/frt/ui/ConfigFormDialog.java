package com.awei.frt.ui;

import com.awei.frt.model.Config;
import com.awei.frt.service.PathHistoryStore;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 核心配置编写向导 —— 表单式（一次填完所有路径与日志级别）
 * 编辑 config.json 的 更新/目标/删除/备份目录 与日志级别；
 * 字段留空 = 保留当前值。保存（预览/确认/写入/自校验）走 CoreConfigWizard 公共流程。
 * 四个路径输入框为"可编辑下拉 + 历史记忆"：保存成功（CoreConfigWizard 记录）后，
 * 下次打开本表单每个输入框可下拉点选该字段最近保存过的路径（最近优先、去重、限量），
 * 输入框仍可直接编辑/清空，不破坏"留空 = 保留当前值"语义。
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

    /** 路径下拉框固定宽度（近似原 JTextField(30) 的观感宽度，避免随历史项长短伸缩） */
    private static final int PATH_FIELD_WIDTH = 300;

    private final Config config;
    private final PathHistoryStore historyStore; // 路径历史存取（null = 无历史，仅容错兜底）
    private Result result;

    private final JComboBox<String> updateField = newPathCombo(); // 更新目录（可编辑 + 历史下拉）
    private final JComboBox<String> targetField = newPathCombo(); // 目标目录（可编辑 + 历史下拉）
    private final JComboBox<String> deleteField = newPathCombo(); // 删除目录（可编辑 + 历史下拉）
    private final JComboBox<String> backupField = newPathCombo(); // 备份目录（可编辑 + 历史下拉）
    private final JComboBox<String> logLevelCombo = new JComboBox<>(LOG_LEVELS);

    public ConfigFormDialog(Frame owner, Config config) {
        this(owner, config, new PathHistoryStore(PathHistoryStore.defaultHistoryFile()));
    }

    /**
     * @param historyStore 路径历史存储（null 或读取失败均降级为无历史，不弹错）；
     *                     测试可注入临时目录 sidecar 隔离
     */
    public ConfigFormDialog(Frame owner, Config config, PathHistoryStore historyStore) {
        super(owner, "核心配置 - 表单式编写", true);
        this.config = config;
        this.historyStore = historyStore;
        UITheme.apply();
        UITheme.applyComboArrowTheme(logLevelCombo); // 日志级别下拉箭头随主题
        buildContent();
        pack();
        setSize(620, 430);
        setLocationRelativeTo(owner);
        prefill();
    }

    /** 创建可编辑的路径输入下拉：可直接输入，也可点开下拉选用保存过的历史路径 */
    private static JComboBox<String> newPathCombo() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.setMaximumRowCount(PathHistoryStore.MAX_ENTRIES_PER_FIELD);
        combo.setPreferredSize(new Dimension(PATH_FIELD_WIDTH, combo.getPreferredSize().height));
        combo.setToolTipText("历史路径：点开可快速选择本字段保存过的路径（最近优先），也可直接输入；留空 = 保留当前值");
        UITheme.applyComboArrowTheme(combo); // 深色主题下 Metal 自带箭头几乎不可见，换主题化箭头
        return combo;
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

        // 路径字段（可编辑下拉：直接输入或点开历史）
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
        JLabel hint = new JLabel("提示：留空 = 保留当前值；目录不存在时确认后自动创建；修改在下次启动时生效；"
                + "路径框可点开下拉切换本字段保存过的历史路径（最近优先）。");
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

    private JPanel fieldRow(String label, JComponent field, String hint) {
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

    /** 预填当前配置值 + 各字段历史下拉项 */
    private void prefill() {
        if (config == null) {
            return;
        }
        prefillPathField(updateField, str(config.getUpdatePath()), PathHistoryStore.FIELD_UPDATE_PATH);
        prefillPathField(targetField, str(config.getTargetPath()), PathHistoryStore.FIELD_TARGET_PATH);
        prefillPathField(deleteField, str(config.getDeletePath()), PathHistoryStore.FIELD_DELETE_PATH);
        prefillPathField(backupField, str(config.getBackupPath()), PathHistoryStore.FIELD_BACKUP_PATH);
        String level = config.getLogLevel() == null ? "INFO" : config.getLogLevel().toUpperCase();
        logLevelCombo.setSelectedItem(containsLogLevel(level) ? level : "INFO");
    }

    /**
     * 把字段历史装入可编辑下拉（最近优先），并把当前配置值预填进编辑框：
     * 当前值在历史中则同时选中（打开即高亮），不在历史中也正常显示，保证可直接编辑。
     */
    private void prefillPathField(JComboBox<String> combo, String current, String field) {
        List<String> history = historyStore == null ? List.of() : historyStore.history(field);
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String item : history) {
            model.addElement(item);
        }
        combo.setModel(model);
        if (current != null && !current.isEmpty()) {
            if (model.getIndexOf(current) >= 0) {
                combo.setSelectedItem(current); // 当前值在历史中：选中并同步到编辑框
            } else {
                combo.getEditor().setItem(current); // 不在历史中：仅预填编辑框内容
            }
        }
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
        Path update = parseOrKeep(comboText(updateField), config != null ? config.getUpdatePath() : null);
        Path target = parseOrKeep(comboText(targetField), config != null ? config.getTargetPath() : null);
        Path delete = parseOrKeep(comboText(deleteField), config != null ? config.getDeletePath() : null);
        Path backup = parseOrKeep(comboText(backupField), config != null ? config.getBackupPath() : null);
        String level = (String) logLevelCombo.getSelectedItem();
        result = new Result(update, target, delete, backup, level);
        dispose();
    }

    /** 读取可编辑下拉当前内容（用户输入或点选的历史项均落在编辑器里） */
    private static String comboText(JComboBox<String> combo) {
        Object item = combo.getEditor().getItem();
        return item == null ? "" : item.toString();
    }

    private static Path parseOrKeep(String text, Path current) {
        if (text == null || text.trim().isEmpty()) {
            return current;
        }
        return Paths.get(text.trim());
    }
}
