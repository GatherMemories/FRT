package com.awei.frt.ui;

import com.awei.frt.constants.RulesConstants;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FolderNode;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则生成向导 —— 表单式（一次填完所有参数，含策略链配置）
 *
 * 弹窗收集：作用目录（更新/删除）→ 目标文件夹（文件树下拉）→ 主策略参数
 * （策略类型 / patterns / excludePatterns / 继承开关 / replacements）
 * → 多策略组合链步骤（可增删）。
 * 确定后返回目标目录与完整 MatchRule；JSON 预览与写入仍走 RuleConfigWizard 公共流程。
 */
public class RuleWizardForm extends JDialog {

    /** 表单结果（目标目录 + 完整规则），取消时为 null */
    public static class Result {
        public final Path targetDir;
        public final MatchRule rule;

        Result(Path targetDir, MatchRule rule) {
            this.targetDir = targetDir;
            this.rule = rule;
        }
    }

    private static final String[] BASE_CHOICES = {"更新目录", "删除目录"};

    private final Config config;
    private Result result;

    private final JComboBox<String> baseCombo = new JComboBox<>(BASE_CHOICES);
    private final JComboBox<String> folderCombo = new JComboBox<>();
    private final JComboBox<String> strategyCombo = new JComboBox<>();
    private final JTextField patternsField = new JTextField(18);
    private final JTextField excludeField = new JTextField(18);
    private final JTextField replacementsField = new JTextField(18);
    private final JCheckBox inheritCheck = new JCheckBox("规则继承到子文件夹（子层无规则时沿用本层规则）");
    private final JPanel chainPanel = new JPanel();
    private final List<ChainRow> chainRows = new ArrayList<>();
    private final JLabel warningLabel = new JLabel(" ");
    private final JLabel folderCountLabel = new JLabel("");
    private final Map<String, Path> folderMap = new LinkedHashMap<>(); // 下拉显示名 -> 目录路径
    private final List<String> strategyTypes = new ArrayList<>();      // 注册表顺序（与下拉一一对应）

    public RuleWizardForm(Frame owner, Config config) {
        super(owner, "规则生成 - 表单式配置", true);
        this.config = config;
        UITheme.apply();
        buildContent();
        pack();
        setSize(700, 640);
        setLocationRelativeTo(owner);
        refreshFolders();
    }

    /**
     * 打开表单（模态）后的结果；未确定（取消）返回 null
     */
    public Result getResult() {
        return result;
    }

    // ---------------- UI 构建 ----------------

    private void buildContent() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UITheme.PANEL_BG);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 12, 6, 12);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int row = 0;

        // ---- 1. 作用目录 ----
        c.gridy = row++;
        form.add(sectionTitle("1. 选择作用目录"), c);
        c.gridy = row++;
        JPanel baseRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        baseRow.setBackground(UITheme.PANEL_BG);
        baseRow.add(new JLabel("规则文件作用于:"));
        baseCombo.addActionListener(e -> refreshFolders());
        baseRow.add(baseCombo);
        form.add(baseRow, c);

        // ---- 2. 目标文件夹 ----
        c.gridy = row++;
        form.add(sectionTitle("2. 选择目标文件夹（生成规则文件的层级）"), c);
        c.gridy = row++;
        JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        folderRow.setBackground(UITheme.PANEL_BG);
        folderRow.add(new JLabel("目标文件夹:"));
        folderRow.add(folderCombo);
        folderRow.add(folderCountLabel);
        folderCombo.addActionListener(e -> reloadFromSelectedFolder());
        form.add(folderRow, c);

        // ---- 3. 主策略参数 ----
        c.gridy = row++;
        form.add(sectionTitle("3. 主策略参数"), c);
        c.gridy = row++;
        form.add(fieldRow("策略类型:", strategyCombo), c);
        c.gridy = row++;
        form.add(fieldRow("patterns（匹配白名单，逗号分隔，空=全部）:", patternsField), c);
        c.gridy = row++;
        form.add(fieldRow("excludePatterns（排除黑名单，逗号分隔，空=无）:", excludeField), c);
        c.gridy = row++;
        form.add(inheritCheck, c);
        c.gridy = row++;
        form.add(fieldRow("replacements（key=value，逗号分隔，空=无）:", replacementsField), c);

        // ---- 4. 多策略组合链 ----
        c.gridy = row++;
        form.add(sectionTitle("4. 多策略组合链（可选）"), c);
        c.gridy = row++;
        JLabel chainHint = new JLabel("链中后续策略只处理前序策略“剩余”的文件；若配置，第 1 步即上方主策略。");
        chainHint.setFont(UITheme.SMALL_FONT);
        chainHint.setForeground(UITheme.MUTED);
        form.add(chainHint, c);

        chainPanel.setLayout(new BoxLayout(chainPanel, BoxLayout.Y_AXIS));
        JScrollPane chainScroll = new JScrollPane(chainPanel);
        chainScroll.setBorder(BorderFactory.createTitledBorder("链步骤"));
        chainScroll.setPreferredSize(new Dimension(640, 140));
        c.gridy = row++;
        form.add(chainScroll, c);

        JButton addStepButton = new JButton("+ 添加链步骤");
        UITheme.styleButton(addStepButton);
        addStepButton.addActionListener(e -> addChainRow());
        c.gridy = row++;
        form.add(addStepButton, c);

        // ---- 警告区 ----
        warningLabel.setFont(UITheme.SMALL_FONT);
        warningLabel.setForeground(UITheme.ERROR);
        c.gridy = row++;
        form.add(warningLabel, c);

        // ---- 按钮 ----
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(UITheme.PANEL_BG);
        JButton okButton = new JButton("确定生成");
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

        // 策略下拉：注册表全部类型（内置 + 外部插件）
        for (String type : StrategyFactory.getSupportedTypes()) {
            strategyTypes.add(type);
            String desc = StrategyFactory.getDescription(type);
            strategyCombo.addItem(desc.isEmpty() ? type : type + "（" + desc + "）");
        }
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.TITLE_FONT);
        label.setForeground(UITheme.TEXT);
        return label;
    }

    private JPanel fieldRow(String label, Component field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setBackground(UITheme.PANEL_BG);
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    // ---------------- 数据刷新 ----------------

    private void refreshFolders() {
        folderMap.clear();
        folderCombo.removeAllItems();
        Path basePath = resolveBasePath();
        if (basePath == null || !Files.isDirectory(basePath)) {
            folderCombo.addItem("（目录不存在）");
            folderCountLabel.setText("");
            reloadFromSelectedFolder();
            return;
        }
        FileNode tree = FileTreeBuilder.buildTree(basePath);
        // 根目录始终可选
        folderMap.put("（根目录）", basePath);
        folderCombo.addItem("（根目录）");
        // 栈迭代收集全部子文件夹（先序，与控制台向导编号顺序一致）
        Deque<FileNode> stack = new ArrayDeque<>();
        stack.push(tree);
        while (!stack.isEmpty()) {
            FileNode node = stack.pop();
            if (!node.isDirectory()) {
                continue;
            }
            FolderNode folder = (FolderNode) node;
            String rel = folder.getRelativePath();
            if (!rel.isEmpty()) {
                String label = rel.replace('\\', '/') + "/";
                folderMap.put(label, folder.getPath());
                folderCombo.addItem(label);
            }
            List<FileNode> children = folder.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        folderCountLabel.setText("（共 " + folderMap.size() + " 个可选目录）");
        reloadFromSelectedFolder();
    }

    private Path resolveBasePath() {
        if (config == null) {
            return null;
        }
        boolean delete = "删除目录".equals(baseCombo.getSelectedItem());
        Path rel = delete ? config.getDeletePath() : config.getUpdatePath();
        return config.getBaseDirectory().resolve(rel).normalize();
    }

    /**
     * 切换目标文件夹时刷新表单：
     * 该层已有规则文件（按查找优先级）→ 解析并预填全部字段（主策略 + 策略链），基于现有配置编辑；
     * 无规则文件 → 表单恢复空白。
     */
    private void reloadFromSelectedFolder() {
        Path targetDir = folderMap.get(folderCombo.getSelectedItem());
        if (targetDir == null) {
            warningLabel.setText(" ");
            warningLabel.setForeground(UITheme.ERROR);
            return;
        }
        Path existing = findExistingRuleFile(targetDir);
        if (existing == null) {
            warningLabel.setText(" ");
            warningLabel.setForeground(UITheme.ERROR);
            resetForm();
            return;
        }
        try {
            String json = Files.readString(existing);
            MatchRule rule = MatchRuleLoader.fromJson(json);
            if (rule == null) {
                warningLabel.setText("警告：解析 " + existing.getFileName() + " 失败，表单为空（生成将覆盖原文件）");
                warningLabel.setForeground(UITheme.ERROR);
                resetForm();
                return;
            }
            applyRuleToForm(rule);
            warningLabel.setText("已加载现有规则文件 " + existing.getFileName() + "，可修改后重新生成（将覆盖原文件）");
            warningLabel.setForeground(UITheme.MUTED);
        } catch (IOException e) {
            warningLabel.setText("警告：读取 " + existing.getFileName() + " 失败");
            warningLabel.setForeground(UITheme.ERROR);
        }
    }

    /** 按查找优先级返回目录下已存在的规则文件，无则 null */
    private Path findExistingRuleFile(Path dir) {
        if (dir == null) {
            return null;
        }
        for (String name : RulesConstants.FileNames.ALL_RULE_FILES) {
            Path f = dir.resolve(name);
            if (Files.exists(f)) {
                return f;
            }
        }
        return null;
    }

    /** 将已解析的规则填充到表单（主策略 = 规则自身或链首步；链步骤 = 第 2 步起） */
    private void applyRuleToForm(MatchRule rule) {
        if (rule == null) {
            return;
        }
        List<MatchRule> steps = rule.getStrategyChain();
        MatchRule main = rule;
        if (steps != null && !steps.isEmpty()) {
            main = steps.get(0);
        }
        selectStrategy(main.getStrategyType());
        patternsField.setText(joinList(main.getPatterns()));
        excludeField.setText(joinList(main.getExcludePatterns()));
        inheritCheck.setSelected(main.isInheritToSubfolders());
        replacementsField.setText(joinMap(main.getReplacements()));

        // 清空旧链步骤，按第 2 步起重建
        chainRows.clear();
        chainPanel.removeAll();
        if (steps != null) {
            for (int i = 1; i < steps.size(); i++) {
                addChainRow(steps.get(i));
            }
        }
        rebuildChainPanel();
    }

    /** 表单恢复空白默认值 */
    private void resetForm() {
        strategyCombo.setSelectedIndex(0);
        patternsField.setText("");
        excludeField.setText("");
        replacementsField.setText("");
        inheritCheck.setSelected(false);
        chainRows.clear();
        chainPanel.removeAll();
        chainPanel.revalidate();
        chainPanel.repaint();
    }

    /** 按策略类型选中下拉项；不在注册表时红字提示 */
    private void selectStrategy(String type) {
        if (type == null) {
            return;
        }
        int idx = strategyTypes.indexOf(type);
        if (idx >= 0) {
            strategyCombo.setSelectedIndex(idx);
        } else {
            warningLabel.setText("警告：策略类型 " + type + " 不在当前注册表，请重新选择");
            warningLabel.setForeground(UITheme.ERROR);
        }
    }

    private static String joinList(List<String> list) {
        return list == null || list.isEmpty() ? "" : String.join(", ", list);
    }

    private static String joinMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ---------------- 策略链步骤 ----------------

    private void addChainRow() {
        addChainRow(null);
    }

    /** 添加链步骤；step 非空时按已有规则预填该步骤 */
    private void addChainRow(MatchRule step) {
        ChainRow row = new ChainRow(chainRows.size() + 1, step);
        chainRows.add(row);
        row.installRemoveAction(() -> {
            chainRows.remove(row);
            rebuildChainPanel();
        });
        rebuildChainPanel();
    }

    /** 清空并按当前列表重建链面板（含重新编号） */
    private void rebuildChainPanel() {
        chainPanel.removeAll();
        for (int i = 0; i < chainRows.size(); i++) {
            chainRows.get(i).setIndex(i + 1);
            chainPanel.add(chainRows.get(i).panel);
        }
        chainPanel.revalidate();
        chainPanel.repaint();
    }

    /** 一行链步骤：策略类型 + patterns + excludePatterns + replacements + 删除 */
    private class ChainRow {
        private final JComboBox<String> typeCombo = new JComboBox<>();
        private final JTextField patterns = new JTextField(10);
        private final JTextField excludes = new JTextField(10);
        private final JTextField replacements = new JTextField(10);
        private final JButton removeButton = new JButton("删除");
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        private int index;

        ChainRow(int index, MatchRule step) {
            this.index = index;
            for (String type : StrategyFactory.getSupportedTypes()) {
                typeCombo.addItem(type);
            }
            if (step != null) {
                int idx = strategyTypes.indexOf(step.getStrategyType());
                if (idx >= 0) {
                    typeCombo.setSelectedIndex(idx);
                }
                patterns.setText(joinList(step.getPatterns()));
                excludes.setText(joinList(step.getExcludePatterns()));
                replacements.setText(joinMap(step.getReplacements()));
            }
            UITheme.styleButton(removeButton);
            panel.setBackground(UITheme.PANEL_BG);
            rebuild();
        }

        void installRemoveAction(Runnable action) {
            removeButton.addActionListener(e -> action.run());
        }

        void setIndex(int i) {
            this.index = i;
            rebuild();
        }

        private void rebuild() {
            panel.removeAll();
            panel.add(new JLabel("步骤" + index + ":"));
            panel.add(new JLabel("策略"));
            panel.add(typeCombo);
            panel.add(new JLabel("patterns（白名单）"));
            panel.add(patterns);
            panel.add(new JLabel("excludePatterns（黑名单）"));
            panel.add(excludes);
            panel.add(new JLabel("replacements（key=value）"));
            panel.add(replacements);
            panel.add(removeButton);
        }

        MatchRule buildRule() {
            MatchRule step = new MatchRule();
            step.setStrategyType((String) typeCombo.getSelectedItem());
            step.setPatterns(parseList(patterns.getText()));
            step.setExcludePatterns(parseList(excludes.getText()));
            step.setReplacements(parseMap(replacements.getText()));
            return step;
        }
    }

    // ---------------- 确定/取消 ----------------

    private void onOk() {
        Object folderSel = folderCombo.getSelectedItem();
        if (folderSel == null || folderMap.get(folderSel) == null) {
            showError("目标文件夹无效，请先确认作用目录存在且已加载文件夹列表");
            return;
        }
        Path targetDir = folderMap.get(folderSel);
        String strategy = (String) strategyCombo.getSelectedItem();
        if (strategy == null) {
            showError("请选择策略类型");
            return;
        }

        MatchRule rule = new MatchRule();
        rule.setStrategyType(strategy);
        rule.setPatterns(parseList(patternsField.getText()));
        rule.setExcludePatterns(parseList(excludeField.getText()));
        rule.setInheritToSubfolders(inheritCheck.isSelected());
        rule.setReplacements(parseMap(replacementsField.getText()));

        if (!chainRows.isEmpty()) {
            List<MatchRule> chain = new ArrayList<>();
            chain.add(rule); // 第 1 步 = 主策略
            for (ChainRow row : chainRows) {
                chain.add(row.buildRule());
            }
            rule.setStrategyChain(chain);
        }

        result = new Result(targetDir, rule);
        dispose();
    }

    private void showError(String message) {
        javax.swing.JOptionPane.showMessageDialog(this, message, "规则生成", javax.swing.JOptionPane.WARNING_MESSAGE);
    }

    // ---------------- 解析工具（与 RuleConfigWizard 一致） ----------------

    private static List<String> parseList(String input) {
        List<String> list = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return list;
        }
        for (String item : input.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private static Map<String, String> parseMap(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        if (input == null || input.trim().isEmpty()) {
            return map;
        }
        for (String item : input.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (!key.isEmpty()) {
                    map.put(key, value);
                    continue;
                }
            }
            System.out.println("[警告] 忽略格式错误的参数项: " + trimmed + " (应为 key=value)");
        }
        return map;
    }
}
