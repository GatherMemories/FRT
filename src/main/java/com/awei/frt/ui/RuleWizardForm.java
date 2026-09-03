package com.awei.frt.ui;

import com.awei.frt.constants.RulesConstants;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FolderNode;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.RuleTemplate;
import com.awei.frt.model.StrategyStep;
import com.awei.frt.service.RuleTemplateLibrary;

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
import java.awt.Color;
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
import com.awei.frt.util.RuleInputParser;

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

    /** 模板下拉首项（不套用模板） */
    private static final String NO_TEMPLATE_ITEM = "（不使用模板）";
    /** 模板下拉分隔项（内置与自定义之间，不可套用） */
    private static final String CUSTOM_SEPARATOR_ITEM = "—— 自定义模板 ——";
    /** 自定义模板显示名前缀标记（与内置模板来源可辨，需求 §3.4） */
    private static final String CUSTOM_MARKER = "【自定义】";

    private final Config config;
    private Result result;

    private final JComboBox<String> templateCombo = new JComboBox<>(); // 模板下拉（首项=（不使用模板））
    private final List<RuleTemplate> builtinTemplates = new ArrayList<>(); // 内置模板（下拉原名展示）
    private final List<RuleTemplate> customTemplates = new ArrayList<>();  // 自定义模板（下拉带【自定义】标记）
    private final Map<String, RuleTemplate> templateByDisplay = new LinkedHashMap<>(); // 下拉显示名 -> 模板（套用映射）
    private JLabel templateHint; // 模板区块提示（随下拉刷新）
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
    /** 套用模板前的表单快照：切回"（不使用模板）"时还原用户手填参数
     *  （v0.1.17 修复：原实现切回无模板后表单仍保留模板值） */
    private FormSnapshot preApplySnapshot;

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

        // ---- 0. 套用规则模板（可选）：模板区块放表单顶部，不重排既有区块顺序 ----
        c.gridy = row++;
        form.add(sectionTitle("0. 套用规则模板（可选）"), c);
        c.gridy = row++;
        JPanel templateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        templateRow.setBackground(UITheme.PANEL_BG);
        JButton applyTemplateButton = new JButton("套用模板");
        UITheme.styleButton(applyTemplateButton);
        applyTemplateButton.addActionListener(e -> applySelectedTemplate());
        // 选中真实模板（非占位项/分隔项）即自动套用：切换模板配置立即生效（按钮保留用于修改后重新套用）
        templateCombo.addActionListener(e -> {
            Object sel = templateCombo.getSelectedItem();
            if (sel == null || CUSTOM_SEPARATOR_ITEM.equals(sel)) {
                return;
            }
            if (NO_TEMPLATE_ITEM.equals(sel)) {
                // 切回"（不使用模板）"：还原套用前的用户手填参数，而不是残留模板字段
                revertToPreApplyState();
                return;
            }
            applySelectedTemplate();
        });
        JButton manageTemplateButton = new JButton("管理自定义模板");
        UITheme.styleButton(manageTemplateButton);
        manageTemplateButton.addActionListener(e -> openManageTemplates());
        templateRow.add(templateCombo);
        templateRow.add(applyTemplateButton);
        templateRow.add(manageTemplateButton);
        form.add(templateRow, c);
        c.gridy = row++;
        // 模板加载失败（空列表）→ 下拉只有（不使用模板），提示手动填写，不影响手动流程
        templateHint = new JLabel(" ");
        templateHint.setFont(UITheme.SMALL_FONT);
        templateHint.setForeground(UITheme.MUTED);
        form.add(templateHint, c);
        refreshTemplateCombo(); // 内置 + 自定义合并填充下拉

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
        // 加大滚轮步长：Swing 默认 unitIncrement 偏小，鼠标滚轮滚动很慢
        chainScroll.getVerticalScrollBar().setUnitIncrement(24);
        chainScroll.getHorizontalScrollBar().setUnitIncrement(24);
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
        JButton saveTemplateButton = new JButton("保存为模板");
        UITheme.styleButton(saveTemplateButton);
        saveTemplateButton.addActionListener(e -> saveAsTemplate());
        JButton okButton = new JButton("确定生成");
        UITheme.stylePrimaryButton(okButton);
        okButton.addActionListener(e -> onOk());
        JButton cancelButton = new JButton("取消");
        UITheme.styleButton(cancelButton);
        cancelButton.addActionListener(e -> dispose());
        buttons.add(saveTemplateButton);
        buttons.add(okButton);
        buttons.add(cancelButton);
        c.gridy = row++;
        form.add(buttons, c);

        getContentPane().setLayout(new BorderLayout());
        // 加大滚轮步长（Swing 默认 unitIncrement 偏小，鼠标滚轮滚动很慢）
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.getVerticalScrollBar().setUnitIncrement(24);
        formScroll.getHorizontalScrollBar().setUnitIncrement(24);
        getContentPane().add(formScroll, BorderLayout.CENTER);

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

    /**
     * 刷新模板下拉（内置 + 自定义合并，自定义带【自定义】标记，需求 §3.4）：
     * 顺序 =（不使用模板）→ 内置模板（原名）→ 分隔项 → 自定义模板（标记名）。
     * 保存/删除/改名后调用，下拉立即反映最新状态。
     */
    private void refreshTemplateCombo() {
        templateCombo.removeAllItems();
        templateByDisplay.clear();
        builtinTemplates.clear();
        customTemplates.clear();
        templateCombo.addItem(NO_TEMPLATE_ITEM);
        builtinTemplates.addAll(RuleTemplateLibrary.loadAll());
        for (RuleTemplate t : builtinTemplates) {
            templateCombo.addItem(t.getName());
            templateByDisplay.put(t.getName(), t);
        }
        customTemplates.addAll(RuleTemplateLibrary.loadAllCustom());
        if (!customTemplates.isEmpty()) {
            templateCombo.addItem(CUSTOM_SEPARATOR_ITEM); // 分隔项：仅视觉分隔，不可套用
        }
        for (RuleTemplate t : customTemplates) {
            String display = CUSTOM_MARKER + t.getName();
            templateCombo.addItem(display);
            templateByDisplay.put(display, t);
        }
        if (templateHint != null) {
            templateHint.setText(builtinTemplates.isEmpty() && customTemplates.isEmpty()
                    ? "模板库加载失败，可手动填写"
                    : "选择模板后点套用，可继续修改任意参数；自定义模板带" + CUSTOM_MARKER + "标记");
        }
    }

    /**
     * 套用选中的规则模板（内置或自定义，模板区块「套用模板」按钮）：
     * 复用 applyRuleToForm 预填全部字段（策略类型/patterns/excludePatterns/replacements/
     * 继承开关/策略链步骤），套用后用户仍可修改任意字段，「确定生成」走既有 onOk() 流程。
     */
    private void applySelectedTemplate() {
        Object selected = templateCombo.getSelectedItem();
        if (selected == null || NO_TEMPLATE_ITEM.equals(selected) || CUSTOM_SEPARATOR_ITEM.equals(selected)) {
            warningLabel.setText("请先选择要套用的模板");
            warningLabel.setForeground(UITheme.ERROR);
            return;
        }
        RuleTemplate t = templateByDisplay.get(selected);
        if (t == null) {
            warningLabel.setText("请先选择要套用的模板");
            warningLabel.setForeground(UITheme.ERROR);
            return;
        }
        // 套用前捕获一次表单快照：仅当尚未捕获（首次套用/上次已还原）时保存，
        // 之后切回"（不使用模板）"可还原到这次套用前的用户手填状态
        if (preApplySnapshot == null) {
            preApplySnapshot = FormSnapshot.capture(this);
        }
        applyRuleToForm(t.getRule().copy()); // 深拷贝：模板资源不被表单修改污染
        warningLabel.setText("已套用模板「" + t.getName() + "」，可继续修改后生成");
        warningLabel.setForeground(UITheme.MUTED);
        // 顶部模板区块同步提示（不滚动也能看到套用结果）
        if (templateHint != null) {
            templateHint.setText("已套用模板「" + t.getName() + "」，可继续修改任意参数后「确定生成」");
            templateHint.setForeground(UITheme.SUCCESS);
        }
    }

    /**
     * 切回"（不使用模板）"：还原套用模板前的用户手填参数（策略/patterns/exclude/
     * replacements/继承开关/链步骤）；无快照（从未套用模板）时无操作。
     */
    private void revertToPreApplyState() {
        if (preApplySnapshot == null) {
            return; // 从未套用模板，无需还原
        }
        preApplySnapshot.restoreTo(this);
        preApplySnapshot = null; // 一次性还原：下次再套用模板时重新捕获
        warningLabel.setText("已还原为套用模板前的参数，可手动填写后生成");
        warningLabel.setForeground(UITheme.MUTED);
        if (templateHint != null) {
            templateHint.setText("已还原为套用模板前的参数（未套用模板）");
            templateHint.setForeground(UITheme.MUTED);
        }
    }

    /**
     * 「保存为模板」按钮（需求 §3.5）：组装当前表单规则（复用 buildRuleFromForm，
     * 不含作用目录/目标文件夹——模板只存规则）→ 保存前校验（MatchRuleLoader）→
     * 收集名称/分类/描述 → 保存；重名（自定义）覆盖询问、重名（内置）拒绝；
     * 成功刷新模板下拉并提示，不关闭表单，可继续「确定生成」。
     */
    private void saveAsTemplate() {
        MatchRule rule = buildRuleFromForm();
        if (rule == null) {
            showError("请先选择策略类型，再保存为模板");
            return;
        }
        // 保存前校验（§3.7）：rule 序列化 → MatchRuleLoader 非 null，不合法不写文件、不关闭表单
        if (!RuleTemplateLibrary.isValidRule(rule)) {
            showError("当前规则不合法，无法保存为模板（策略类型或策略链步骤未注册）");
            return;
        }
        TemplateInfo info = askTemplateInfo();
        if (info == null) {
            return; // 用户取消
        }
        RuleTemplate template = new RuleTemplate();
        template.setId(RuleTemplateLibrary.generateCustomTemplateId());
        template.setName(info.name);
        template.setCategory(info.category);
        template.setDescription(info.description);
        template.setRule(rule);
        RuleTemplateLibrary.SaveStatus status = RuleTemplateLibrary.saveTemplate(template, false);
        if (status == RuleTemplateLibrary.SaveStatus.DUPLICATE_NAME) {
            // 与自定义模板重名：询问是否覆盖（覆盖保持原 id，引用不失效）
            int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                    "已存在同名模板「" + info.name + "」，是否覆盖？", "保存为模板",
                    javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
            if (choice != javax.swing.JOptionPane.YES_OPTION) {
                return;
            }
            status = RuleTemplateLibrary.saveTemplate(template, true);
        }
        if (status == RuleTemplateLibrary.SaveStatus.SUCCESS) {
            refreshTemplateCombo(); // 下拉立即反映新增自定义项
            warningLabel.setText("已保存自定义模板「" + info.name + "」");
            warningLabel.setForeground(UITheme.SUCCESS);
        } else if (status == RuleTemplateLibrary.SaveStatus.BUILTIN_NAME_CONFLICT) {
            showError("与内置模板重名，请换一个名称");
        } else if (status == RuleTemplateLibrary.SaveStatus.INVALID_RULE) {
            showError("当前规则不合法，无法保存为模板");
        } else {
            showError("保存模板失败，请检查模板目录是否可写");
        }
    }

    /** 模板信息（名称必填；分类默认"自定义"；描述可选） */
    private static class TemplateInfo {
        final String name;
        final String category;
        final String description;

        TemplateInfo(String name, String category, String description) {
            this.name = name;
            this.category = category;
            this.description = description;
        }
    }

    /**
     * 收集模板信息：样式化输入面板（UITheme 组件，深色主题可读），
     * 名称必填（空名弹错提示并视为取消）；用户取消返回 null。
     */
    private TemplateInfo askTemplateInfo() {
        JTextField nameField = new JTextField(20);
        JTextField categoryField = new JTextField(20);
        categoryField.setText("自定义");
        JTextField descField = new JTextField(20);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UITheme.PANEL_BG);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int row = 0;
        c.gridy = row++;
        panel.add(new JLabel("模板名称（必填）:"), c);
        c.gridy = row++;
        panel.add(nameField, c);
        c.gridy = row++;
        panel.add(new JLabel("分类（可选，默认「自定义」）:"), c);
        c.gridy = row++;
        panel.add(categoryField, c);
        c.gridy = row++;
        panel.add(new JLabel("描述（可选）:"), c);
        c.gridy = row++;
        panel.add(descField, c);
        int choice = javax.swing.JOptionPane.showConfirmDialog(this, panel, "保存为自定义模板",
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (choice != javax.swing.JOptionPane.OK_OPTION) {
            return null;
        }
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            showError("模板名称不能为空");
            return null;
        }
        String category = categoryField.getText() == null ? "" : categoryField.getText().trim();
        String description = descField.getText() == null ? "" : descField.getText().trim();
        return new TemplateInfo(name, category.isEmpty() ? "自定义" : category, description);
    }

    /**
     * 「管理自定义模板」入口（需求 §3.5）：打开管理对话框（仅列自定义，内置不展示、
     * 删除入口隐藏——内置只读保护第一层），对话框内删除/改名后即刷新下拉，关闭后再刷一次兜底。
     */
    private void openManageTemplates() {
        new TemplateManageDialog().setVisible(true); // 模态：关闭后才继续
        refreshTemplateCombo();
    }

    /**
     * 自定义模板管理对话框（FR-1，v0.1.16，§3.5；内嵌于表单，与表单共享刷新路径）
     * <p>
     * 仅列出自定义模板（内置模板不展示、删除入口对内置隐藏——内置只读保护第一层，
     * 库层 deleteTemplate/renameTemplate 对内置 id 仍拒绝，双保险）。
     * 每行提供「改名」「删除」（删除需确认，确认后生效）；操作成功后列表与表单模板下拉
     * 立即刷新（保存/删除/改名共享同一刷新路径）。全部组件使用 UITheme 样式
     * （依赖工作区未提交的 UIManager 深色主题修复，深色主题下可读）。
     */
    private class TemplateManageDialog extends JDialog {

        private final JPanel listPanel = new JPanel();
        private final JLabel statusLabel = new JLabel(" ");

        TemplateManageDialog() {
            super(RuleWizardForm.this, "管理自定义模板", true);
            UITheme.apply();
            buildContent();
            pack();
            setSize(560, 420);
            setLocationRelativeTo(RuleWizardForm.this);
            refreshList();
        }

        private void buildContent() {
            JPanel root = new JPanel(new BorderLayout(8, 8));
            root.setBackground(UITheme.PANEL_BG);

            JLabel title = new JLabel("自定义模板管理（内置模板只读，不在本列表展示）");
            title.setFont(UITheme.TITLE_FONT);
            title.setForeground(UITheme.TEXT);
            root.add(title, BorderLayout.NORTH);

            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBackground(UITheme.PANEL_BG);
            JScrollPane scroll = new JScrollPane(listPanel);
            // 加大滚轮步长（与主表单一致）
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            root.add(scroll, BorderLayout.CENTER);

            statusLabel.setFont(UITheme.SMALL_FONT);
            statusLabel.setForeground(UITheme.MUTED);
            JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            statusRow.setBackground(UITheme.PANEL_BG);
            statusRow.add(statusLabel);
            root.add(statusRow, BorderLayout.SOUTH);

            getContentPane().add(root);
        }

        /** 刷新列表：仅列自定义模板；无自定义模板时显示空提示（不误导） */
        private void refreshList() {
            listPanel.removeAll();
            List<RuleTemplate> customs = RuleTemplateLibrary.loadAllCustom();
            if (customs.isEmpty()) {
                JLabel empty = new JLabel("暂无自定义模板");
                empty.setForeground(UITheme.MUTED);
                listPanel.add(empty);
            } else {
                for (RuleTemplate t : customs) {
                    listPanel.add(templateRow(t));
                }
            }
            listPanel.revalidate();
            listPanel.repaint();
        }

        /** 一行自定义模板：名称（分类）+ 描述 + 改名 + 删除 */
        private JPanel templateRow(RuleTemplate t) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            row.setBackground(UITheme.PANEL_BG);
            String category = t.getCategory() == null || t.getCategory().isBlank() ? "" : "（" + t.getCategory() + "）";
            JLabel nameLabel = new JLabel(t.getName() + category);
            nameLabel.setForeground(UITheme.TEXT);
            JLabel descLabel = new JLabel(t.getDescription() == null || t.getDescription().isBlank()
                    ? "" : " — " + t.getDescription());
            descLabel.setFont(UITheme.SMALL_FONT);
            descLabel.setForeground(UITheme.MUTED);
            JButton renameButton = new JButton("改名");
            UITheme.styleButton(renameButton);
            renameButton.addActionListener(e -> renameTemplate(t));
            JButton deleteButton = new JButton("删除");
            UITheme.styleButton(deleteButton);
            deleteButton.addActionListener(e -> deleteTemplate(t));
            row.add(nameLabel);
            row.add(descLabel);
            row.add(renameButton);
            row.add(deleteButton);
            return row;
        }

        /** 改名：弹窗输入新名称（非空、不与内置/其他自定义重名），改名不换 id */
        private void renameTemplate(RuleTemplate t) {
            String newName = javax.swing.JOptionPane.showInputDialog(this, "请输入新名称:", t.getName());
            if (newName == null) {
                return; // 取消
            }
            newName = newName.trim();
            if (newName.isEmpty()) {
                showStatus("名称不能为空", UITheme.ERROR);
                return;
            }
            if (RuleTemplateLibrary.renameTemplate(t.getId(), newName)) {
                showStatus("已改名为「" + newName + "」", UITheme.SUCCESS);
                refreshList();
                refreshTemplateCombo(); // 与表单下拉共享刷新路径
            } else {
                showStatus("改名失败：名称不能为空、与内置或其他自定义模板重名", UITheme.ERROR);
            }
        }

        /** 删除：确认后调用库层删除（内置 id 不可达，库层仍拒绝，双保险） */
        private void deleteTemplate(RuleTemplate t) {
            int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                    "确定删除自定义模板「" + t.getName() + "」？", "删除模板",
                    javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
            if (choice != javax.swing.JOptionPane.YES_OPTION) {
                return;
            }
            if (RuleTemplateLibrary.deleteTemplate(t.getId())) {
                showStatus("已删除「" + t.getName() + "」", UITheme.SUCCESS);
                refreshList();
                refreshTemplateCombo(); // 与表单下拉共享刷新路径
            } else {
                showStatus("删除失败（内置模板不可删除或模板不存在）", UITheme.ERROR);
            }
        }

        private void showStatus(String text, Color color) {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        }
    }

    /** 将已解析的规则填充到表单（主策略 = 规则自身即链第 1 步；链步骤 = strategyChain 中的后续步骤） */
    private void applyRuleToForm(MatchRule rule) {
        if (rule == null) {
            return;
        }
        // 主策略 = 规则自身（strategyChain 只存后续步骤，无"链首步=主策略"冗余）
        selectStrategy(rule.getStrategyType());
        patternsField.setText(joinList(rule.getPatterns()));
        excludeField.setText(joinList(rule.getExcludePatterns()));
        inheritCheck.setSelected(rule.isInheritToSubfolders());
        replacementsField.setText(joinMap(rule.getReplacements()));

        // 清空旧链步骤，按 strategyChain 重建（均为第 2 步起的后续步骤）
        chainRows.clear();
        chainPanel.removeAll();
        List<StrategyStep> steps = rule.getStrategyChain();
        if (steps != null) {
            for (StrategyStep step : steps) {
                addChainRow(step);
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
    private void addChainRow(StrategyStep step) {
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

        ChainRow(int index, StrategyStep step) {
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

        StrategyStep buildRule() {
            StrategyStep step = new StrategyStep();
            step.setStrategyType((String) typeCombo.getSelectedItem());
            step.setPatterns(parseList(patterns.getText()));
            step.setExcludePatterns(parseList(excludes.getText()));
            step.setReplacements(parseMap(replacements.getText()));
            return step;
        }
    }

    // ---------------- 套用模板前快照（切回"无模板"时还原用户手填） ----------------

    /** 策略链一行步骤的纯数据快照（脱离 Swing 组件） */
    private static final class ChainStepSnapshot {
        final String strategyType;
        final String patterns;
        final String excludes;
        final String replacements;

        ChainStepSnapshot(String strategyType, String patterns, String excludes, String replacements) {
            this.strategyType = strategyType;
            this.patterns = patterns;
            this.excludes = excludes;
            this.replacements = replacements;
        }
    }

    /**
     * 表单规则区快照：策略类型/patterns/excludePatterns/replacements/继承开关/链步骤。
     * capture 于每次真实套用模板前调用一次；restoreTo 用于切回"（不使用模板）"。
     */
    private static final class FormSnapshot {
        final int strategyIndex;                    // 主策略下拉索引
        final String patterns;
        final String excludes;
        final String replacements;
        final boolean inherit;
        final List<ChainStepSnapshot> chain = new ArrayList<>();

        static FormSnapshot capture(RuleWizardForm form) {
            FormSnapshot s = new FormSnapshot(form.strategyCombo.getSelectedIndex(),
                    form.patternsField.getText(), form.excludeField.getText(),
                    form.replacementsField.getText(), form.inheritCheck.isSelected());
            for (ChainRow row : form.chainRows) {
                s.chain.add(new ChainStepSnapshot(
                        (String) row.typeCombo.getSelectedItem(),
                        row.patterns.getText(), row.excludes.getText(), row.replacements.getText()));
            }
            return s;
        }

        FormSnapshot(int strategyIndex, String patterns, String excludes, String replacements, boolean inherit) {
            this.strategyIndex = strategyIndex;
            this.patterns = patterns;
            this.excludes = excludes;
            this.replacements = replacements;
            this.inherit = inherit;
        }

        void restoreTo(RuleWizardForm form) {
            if (strategyIndex >= 0 && strategyIndex < form.strategyCombo.getItemCount()) {
                form.strategyCombo.setSelectedIndex(strategyIndex);
            }
            form.patternsField.setText(patterns);
            form.excludeField.setText(excludes);
            form.replacementsField.setText(replacements);
            form.inheritCheck.setSelected(inherit);
            // 清空链并重建
            form.chainRows.clear();
            form.chainPanel.removeAll();
            for (ChainStepSnapshot step : chain) {
                form.addChainRowFromSnapshot(step);
            }
            form.rebuildChainPanel();
        }
    }

    /** 按快照重建一行链步骤（复用 addChainRow(StrategyStep) 的预填路径） */
    private void addChainRowFromSnapshot(ChainStepSnapshot step) {
        StrategyStep s = new StrategyStep();
        s.setStrategyType(step.strategyType);
        s.setPatterns(parseList(step.patterns));
        s.setExcludePatterns(parseList(step.excludes));
        s.setReplacements(parseMap(step.replacements));
        addChainRow(s);
    }

    // ---------------- 确定/取消 ----------------

    /**
     * 组装当前表单规则（策略类型/patterns/excludePatterns/replacements/继承开关/策略链步骤），
     * 不含作用目录/目标文件夹——模板只存规则，与内置模板语义一致。
     * 「确定生成」与「保存为模板」共用本方法（需求 §6.2，避免两份组装逻辑漂移）。
     *
     * @return 完整规则；策略类型未选择时返回 null（调用方负责提示）
     */
    private MatchRule buildRuleFromForm() {
        // 下拉项显示"类型（说明）"，但存入规则的必须是注册表标识（否则解析失败）
        int strategyIdx = strategyCombo.getSelectedIndex();
        String strategy = (strategyIdx >= 0 && strategyIdx < strategyTypes.size()) ? strategyTypes.get(strategyIdx) : null;
        if (strategy == null) {
            return null;
        }

        MatchRule rule = new MatchRule();
        rule.setStrategyType(strategy);
        rule.setPatterns(parseList(patternsField.getText()));
        rule.setExcludePatterns(parseList(excludeField.getText()));
        rule.setInheritToSubfolders(inheritCheck.isSelected());
        rule.setReplacements(parseMap(replacementsField.getText()));

        // 链只存"后续步骤"（第 1 步=主策略本身，无需拷贝入链，杜绝重复参数）
        if (!chainRows.isEmpty()) {
            List<StrategyStep> chain = new ArrayList<>();
            for (ChainRow row : chainRows) {
                chain.add(row.buildRule());
            }
            rule.setStrategyChain(chain);
        }
        return rule;
    }

    private void onOk() {
        Object folderSel = folderCombo.getSelectedItem();
        if (folderSel == null || folderMap.get(folderSel) == null) {
            showError("目标文件夹无效，请先确认作用目录存在且已加载文件夹列表");
            return;
        }
        Path targetDir = folderMap.get(folderSel);
        MatchRule rule = buildRuleFromForm();
        if (rule == null) {
            showError("请选择策略类型");
            return;
        }
        result = new Result(targetDir, rule);
        dispose();
    }

    private void showError(String message) {
        javax.swing.JOptionPane.showMessageDialog(this, message, "规则生成", javax.swing.JOptionPane.WARNING_MESSAGE);
    }

    // ---------------- 解析工具（与 RuleConfigWizard 共用 RuleInputParser，审查 L1 收敛） ----------------

    private static List<String> parseList(String input) {
        return RuleInputParser.parseList(input);
    }

    private static Map<String, String> parseMap(String input) {
        return RuleInputParser.parseMap(input);
    }
}
