package com.awei.frt.service;

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
import com.awei.frt.ui.ConsoleUserPrompter;
import com.awei.frt.ui.UserPrompter;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 规则配置文件交互式向导
 * 显示目录文件结构图，让用户选择在哪一层生成 matching-rules.json，
 * 并按提示输入各参数（含数据类型/默认值提示），生成前预览、生成后自校验
 *
 * @Author: mou_ren
 */
public class RuleConfigWizard {

    private final Config config;
    private final UserPrompter prompter;
    private int folderCounter = 0; // 文件夹编号计数器

    public RuleConfigWizard(Config config, Scanner scanner) {
        this(config, new ConsoleUserPrompter(scanner));
    }

    public RuleConfigWizard(Config config, UserPrompter prompter) {
        this.config = config;
        this.prompter = prompter;
    }

    /**
     * 向导入口
     */
    public void start() {
        try {
            System.out.println("\n=========================================");
            System.out.println("[规则生成] 生成/编辑匹配规则配置文件 (matching-rules.json)");
            System.out.println("=========================================");
            System.out.println("[说明] 规则文件控制所在层文件夹的 增/删/改 操作");
            System.out.println("       查找优先级: matching-rules.json > replace.json > add.json > delete.json");
            System.out.println("       未配置规则或未开启继承的层将被跳过处理");
            System.out.println("-----------------------------------------");

            // 1. 选择作用目录（更新目录 / 删除目录）
            Path basePath = chooseBaseDirectory();
            if (basePath == null) {
                return;
            }

            // 2. 显示文件树并选择层级
            System.out.println("\n[FILE] 目录文件结构图 (" + basePath + "):");
            FileNode tree = FileTreeBuilder.buildTree(basePath);
            Map<Integer, Path> folderIndex = printIndexedTree(tree);
            if (folderIndex.isEmpty()) {
                System.out.println("[信息] 该目录没有文件夹可选");
                return;
            }

            System.out.print("\n[选择] 请选择要在哪一层生成规则文件 (输入文件夹编号, 0=返回): ");
            int choice;
            String choiceInput = readLine();
            if (choiceInput.isEmpty()) {
                System.out.println("[取消] 已返回主菜单");
                return;
            }
            try {
                choice = Integer.parseInt(choiceInput);
            } catch (NumberFormatException e) {
                System.out.println("[失败] 无效编号");
                return;
            }
            if (choice == 0) {
                System.out.println("[返回] 已返回主菜单");
                return;
            }
            Path targetDir = folderIndex.get(choice);
            if (targetDir == null) {
                System.out.println("[失败] 无效编号: " + choice);
                return;
            }
            String rel = basePath.relativize(targetDir).toString().replace('\\', '/');
            System.out.println("[已选] 生成位置: " + (rel.isEmpty() ? basePath.getFileName() : rel) + "/");

            // 3. 套用规则模板（可选，FR-2/FR-1 自定义模板）或逐参数输入规则
            MatchRule rule = askTemplateOrInput(targetDir);
            if (rule == null) {
                return;
            }

            // 3.5 保存为自定义模板（可选，FR-1）：手动输入与套用模板两条路径都覆盖，
            // 插在规则组装完成后、writeRuleFile 预览前；任何失败仅提示，不打断生成
            maybeSaveAsTemplate(rule);

            // 4. 生成并写入
            writeRuleFile(rule, targetDir);

        } catch (Exception e) {
            LoggerUtil.logException("向导执行出错", e);
        }
    }

    /**
     * 选择规则作用目录（更新目录 / 删除目录）
     * @return 目录绝对路径，返回主菜单返回 null
     */
    private Path chooseBaseDirectory() {
        System.out.println("[选择] 规则文件作用目录:");
        System.out.println("       1. 更新目录: " + config.getUpdatePath());
        System.out.println("       2. 删除目录: " + config.getDeletePath());
        System.out.print("请输入 (1-2, 回车/取消=返回): ");
        String input = readLine();
        if (input.isEmpty()) {
            System.out.println("[取消] 已返回主菜单");
            return null;
        }
        Path basePath;
        if (input.equals("2")) {
            basePath = config.getBaseDirectory().resolve(config.getDeletePath()).normalize();
        } else {
            basePath = config.getBaseDirectory().resolve(config.getUpdatePath()).normalize();
        }
        if (!Files.exists(basePath)) {
            System.out.println("[失败] 目录不存在: " + basePath);
            return null;
        }
        return basePath;
    }

    /**
     * 打印带编号的文件夹树（文件不编号），返回 编号->路径 映射
     * @param root 根节点
     * @return 编号映射（LinkedHashMap 保持插入顺序）
     */
    private Map<Integer, Path> printIndexedTree(FileNode root) {
        Map<Integer, Path> folderIndex = new LinkedHashMap<>();
        folderCounter = 0;
        // 栈迭代打印（替代原 printNode/printChildren 相互递归，避免超深目录栈溢出）
        Deque<PrintTask> stack = new ArrayDeque<>();
        stack.push(new PrintTask(root, "", true, true, false));
        while (!stack.isEmpty()) {
            PrintTask task = stack.pop();
            if (task.isRoot) {
                if (task.node.isDirectory()) {
                    int index = ++folderCounter;
                    folderIndex.put(index, task.node.getPath());
                    String ruleFile = ruleFileAt(task.node.getPath());
                    System.out.println("[" + index + "] [+] " + task.node.getName() + "/"
                            + (ruleFile != null ? "  [规则: " + ruleFile + "]" : ""));
                    pushChildTasks((FolderNode) task.node, "", stack, ruleFile != null);
                } else {
                    System.out.println("[-] " + task.node.getName());
                }
                continue;
            }
            String connector = task.isLast ? "└── " : "├── ";
            if (task.node.isDirectory()) {
                int index = ++folderCounter;
                folderIndex.put(index, task.node.getPath());
                String ruleFile = ruleFileAt(task.node.getPath());
                System.out.println(task.prefix + connector + "[" + index + "] [+] " + task.node.getName() + "/"
                        + (ruleFile != null ? "  [规则: " + ruleFile + "]" : ""));
                pushChildTasks((FolderNode) task.node, task.prefix + (task.isLast ? "    " : "│   "), stack,
                        ruleFile != null);
            } else {
                // 文件行：若所在层有规则文件，标注"受规则影响"（便于预览该层配置的作用范围）
                String affected = task.affectedByRule ? "  [受规则影响]" : "";
                System.out.println(task.prefix + connector + "[-] " + task.node.getName() + affected);
            }
        }
        return folderIndex;
    }

    /**
     * 返回目录下已存在的规则文件名（按查找优先级），无则 null
     */
    private String ruleFileAt(Path dir) {
        if (dir == null) {
            return null;
        }
        for (String ruleType : RulesConstants.FileNames.ALL_RULE_FILES) {
            if (Files.exists(dir.resolve(ruleType))) {
                return ruleType;
            }
        }
        return null;
    }

    /**
     * 将子节点倒序压栈，保证正序弹出（输出顺序与原递归实现一致）
     * @param parentHasRule 父目录是否含规则文件（决定直属文件是否标注"受规则影响"）
     */
    private void pushChildTasks(FolderNode folder, String childPrefix, Deque<PrintTask> stack, boolean parentHasRule) {
        List<FileNode> children = folder.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(new PrintTask(children.get(i), childPrefix, i == children.size() - 1, false, parentHasRule));
        }
    }

    /**
     * 打印任务（迭代遍历用）
     */
    private static class PrintTask {
        final FileNode node;
        final String prefix;
        final boolean isLast;
        final boolean isRoot;
        final boolean affectedByRule; // 文件是否处于含规则文件的层（受规则影响）

        PrintTask(FileNode node, String prefix, boolean isLast, boolean isRoot, boolean affectedByRule) {
            this.node = node;
            this.prefix = prefix;
            this.isLast = isLast;
            this.isRoot = isRoot;
            this.affectedByRule = affectedByRule;
        }
    }

    /**
     * 模板询问入口（FR-2/FR-1 自定义模板扩展）：选择层级后、逐参数输入前询问是否套用规则模板——
     * y → 列出合并列表（内置 1..N 原名 + 自定义 N+1.. 带 [自定义] 标记，编号连续）→
     * 合法编号 → 跳过逐参数输入直接返回模板规则（内置与自定义一视同仁，深拷贝）；
     * n/回车 → 走既有逐参数输入（输出与 v0.1.14 一致）；无效编号重新询问，0/回车=取消回手动流程；
     * 模板库加载失败 → 提示后回退手动流程，不崩溃。
     *
     * @param targetDir 目标目录（传给 inputRule 展示已有规则文件）
     * @return 规则对象（模板规则深拷贝或手动输入结果），取消返回 null
     */
    private MatchRule askTemplateOrInput(Path targetDir) {
        List<RuleTemplate> builtins = RuleTemplateLibrary.loadAll();
        List<RuleTemplate> customs = RuleTemplateLibrary.loadAllCustom();
        if (builtins.isEmpty() && customs.isEmpty()) {
            System.out.println("[信息] 模板库加载失败或为空，继续手动输入");
            return inputRule(targetDir);
        }
        System.out.print("\n[选择] 是否套用规则模板？(y/n, 回车=n): ");
        String answer = readLine();
        boolean yes = answer != null
                && (answer.trim().equalsIgnoreCase("y") || answer.trim().equalsIgnoreCase("yes"));
        if (!yes) {
            return inputRule(targetDir); // n/回车：走既有逐参数流程
        }
        // 合并列表：内置在前（原名），自定义在后（带 [自定义] 标记），编号连续
        List<RuleTemplate> merged = new ArrayList<>(builtins);
        merged.addAll(customs);
        System.out.println("\n[列表] 规则模板:");
        for (int i = 0; i < merged.size(); i++) {
            RuleTemplate t = merged.get(i);
            if (i < builtins.size()) {
                System.out.println("       " + (i + 1) + "=" + t.getName() + "[" + t.getCategory()
                        + "]（" + t.getDescription() + "）");
            } else {
                // 自定义模板行尾追加 [自定义] 标记（需求 §3.4）
                System.out.println("       " + (i + 1) + "=" + t.getName() + "[自定义]（"
                        + (t.getDescription() == null || t.getDescription().isBlank()
                                ? "自定义模板" : t.getDescription()) + "）");
            }
        }
        while (true) {
            System.out.print("\n[选择] 请输入模板编号 (0/回车=取消): ");
            String num = readLine();
            if (num == null || num.trim().isEmpty() || num.trim().equals("0")) {
                System.out.println("[返回] 未套用模板，继续手动输入");
                return inputRule(targetDir);
            }
            try {
                int idx = Integer.parseInt(num.trim());
                if (idx >= 1 && idx <= merged.size()) {
                    RuleTemplate t = idx <= builtins.size()
                            ? builtins.get(idx - 1)
                            : customs.get(idx - builtins.size() - 1);
                    System.out.println("[已选] 已套用模板「" + t.getName() + "」，跳过逐参数输入");
                    return t.getRule().copy(); // 深拷贝：模板资源不被后续修改污染
                }
            } catch (NumberFormatException ignored) {
                // 非数字输入按无效编号处理，重新询问
            }
            System.out.println("[失败] 无效模板编号");
        }
    }

    /**
     * 询问是否将当前规则保存为自定义模板（FR-1，v0.1.16，§3.6）：
     * 插在规则组装完成后、writeRuleFile 预览前（手动输入与套用模板两条路径都覆盖）。
     * <p>
     * n/回车直接继续（既有流程输出不变）；y → 保存前校验（rule 序列化 → MatchRuleLoader 非 null，
     * 与模板库加载校验同款）→ 输入名称（回车/空=取消）；与内置重名拒绝换名、与自定义重名
     * 询问覆盖；保存成功/失败均提示后继续生成流程，不打断、不崩溃。控制台不提供删除/改名
     * （由 GUI「管理自定义模板」入口覆盖，GUI/控制台共享同一存储文件）。
     */
    private void maybeSaveAsTemplate(MatchRule rule) {
        System.out.print("\n[选择] 是否将当前规则保存为自定义模板？(y/n, 回车=n): ");
        String answer = readLine();
        boolean yes = answer != null
                && (answer.trim().equalsIgnoreCase("y") || answer.trim().equalsIgnoreCase("yes"));
        if (!yes) {
            return; // n/回车：直接继续既有预览流程
        }
        // 保存前校验（§3.7）：rule 序列化 → MatchRuleLoader 非 null；不合法不写文件、不打断生成
        if (!RuleTemplateLibrary.isValidRule(rule)) {
            System.out.println("[失败] 当前规则不合法，无法保存为模板");
            return;
        }
        while (true) {
            System.out.print("[输入] 请输入模板名称 (回车=取消): ");
            String name = readLine();
            if (name == null || name.isBlank()) {
                System.out.println("[取消] 未保存模板");
                return;
            }
            String trimmed = name.trim();
            // 内置只读保护：与内置模板重名 → 拒绝换名（回车可随时取消，不会死循环）
            boolean builtinName = false;
            for (RuleTemplate t : RuleTemplateLibrary.loadAll()) {
                if (trimmed.equals(t.getName())) {
                    builtinName = true;
                    break;
                }
            }
            if (builtinName) {
                System.out.println("[失败] 与内置模板重名，请换名");
                continue;
            }
            RuleTemplate template = new RuleTemplate();
            template.setId(RuleTemplateLibrary.generateCustomTemplateId());
            template.setName(trimmed);
            template.setCategory("自定义"); // 控制台最小交互：分类默认"自定义"，描述留空
            template.setDescription("");
            template.setRule(rule);
            RuleTemplateLibrary.SaveStatus status = RuleTemplateLibrary.saveTemplate(template, false);
            if (status == RuleTemplateLibrary.SaveStatus.DUPLICATE_NAME) {
                // 与自定义模板重名：询问是否覆盖
                System.out.print("[选择] 已存在同名模板「" + trimmed + "」，是否覆盖？(y/n, 回车=n): ");
                if (parseBoolean(readLine(), false)) {
                    status = RuleTemplateLibrary.saveTemplate(template, true);
                } else {
                    System.out.println("[取消] 未保存模板");
                    return;
                }
            }
            if (status == RuleTemplateLibrary.SaveStatus.SUCCESS) {
                System.out.println("[成功] 已保存自定义模板「" + trimmed + "」");
            } else if (status == RuleTemplateLibrary.SaveStatus.BUILTIN_NAME_CONFLICT) {
                System.out.println("[失败] 与内置模板重名，请换名");
                continue;
            } else if (status == RuleTemplateLibrary.SaveStatus.INVALID_RULE) {
                System.out.println("[失败] 当前规则不合法，无法保存为模板");
            } else {
                System.out.println("[失败] 保存模板失败: 模板目录不可写或写入失败（详见日志）");
            }
            return;
        }
    }

    /**
     * 交互输入规则参数
     * @param targetDir 目标目录（用于显示已有规则文件）
     * @return 规则对象，取消返回 null
     */
    private MatchRule inputRule(Path targetDir) {
        // 提示已有规则文件
        Path existing = findExistingRuleFile(targetDir);
        if (existing != null) {
            System.out.println("\n[警告] 该目录已存在规则文件: " + existing.getFileName());
            System.out.println("       注意: matching-rules.json 查找优先级最高，生成后将优先生效");
        }

        System.out.println("\n[输入] 请按提示输入规则参数（回车使用默认值/跳过）:");
        System.out.println("-----------------------------------------");

        // 1. strategyType（必填）—— 可选列表来自策略注册表（内置 + 外部插件动态加载的策略）
        System.out.println("[参数 1/6] strategyType — 策略类型");
        System.out.println("  类型: String    必填: 是    默认值: (无)");
        System.out.println("  可选值: " + listStrategies());
        String strategyType = null;
        while (strategyType == null) {
            System.out.print("  请输入 (编号或策略名, 回车/0=取消): ");
            String input = readLine();
            if (input.isEmpty() || input.equals("0")) {
                System.out.println("[取消] 已取消生成");
                return null;
            }
            strategyType = resolveStrategyType(input);
            if (strategyType == null) {
                System.out.println("  [失败] 无效策略类型: " + input);
            }
        }
        System.out.println("  >> strategyType = \"" + strategyType + "\"");
        boolean mcMod = strategyType.equals("McMod");

        // 2. patterns
        System.out.println("\n[参数 2/6] patterns — 匹配文件模式 (白名单)");
        System.out.println("  类型: List<String>    必填: 否    默认值: 空列表 (匹配所有文件)");
        System.out.println("  说明: 只处理匹配的文件; 支持通配符 * 和 ?; 多个用英文逗号分隔"
                + (mcMod ? "   (当前策略 McMod 不生效, 可回车跳过)" : ""));
        System.out.print("  请输入 (回车=空): ");
        List<String> patterns = parseList(readLine());
        System.out.println("  >> patterns = " + patterns);

        // 3. excludePatterns
        System.out.println("\n[参数 3/6] excludePatterns — 排除文件模式 (黑名单)");
        System.out.println("  类型: List<String>    必填: 否    默认值: 空列表 (不排除任何文件)");
        System.out.println("  说明: 白名单通过后再排除; 支持通配符; 多个用英文逗号分隔"
                + (mcMod ? "   (当前策略 McMod 不生效, 可回车跳过)" : ""));
        System.out.print("  请输入 (回车=空): ");
        List<String> excludePatterns = parseList(readLine());
        System.out.println("  >> excludePatterns = " + excludePatterns);

        // 4. inheritToSubfolders
        System.out.println("\n[参数 4/6] inheritToSubfolders — 规则是否继承到子文件夹");
        System.out.println("  类型: Boolean    必填: 否    默认值: false");
        System.out.println("  说明: true 时, 子文件夹无本地规则则继承本层规则继续处理; false 则子层跳过");
        System.out.print("  请输入 (y/n, 回车=默认 false): ");
        boolean inherit = parseBoolean(readLine(), false);
        System.out.println("  >> inheritToSubfolders = " + inherit);

        // 5. replacements
        System.out.println("\n[参数 5/6] replacements — 策略扩展参数 (键值对)");
        System.out.println("  类型: Map<String,String>    必填: 否    默认值: 空");
        System.out.println("  说明: 给策略传自定义配置; 格式 key=value, 多个用英文逗号分隔");
        System.out.println("        示例: McMod: onlyIfVersionChanged=true | FileSameName: caseSensitive=false");
        System.out.print("  请输入 (回车=空): ");
        Map<String, String> replacements = parseMap(readLine());
        System.out.println("  >> replacements = " + replacements);

        // 组装规则对象
        MatchRule rule = new MatchRule();
        rule.setStrategyType(strategyType);
        rule.setPatterns(patterns);
        rule.setExcludePatterns(excludePatterns);
        rule.setInheritToSubfolders(inherit);
        rule.setReplacements(replacements);

        // 6. 多策略组合链（可选）：链中后续策略只处理前序策略"剩余"的文件
        System.out.println("\n[参数 6/6] 多策略组合链 (可选)");
        System.out.println("  说明: 配置策略链后, 本层按链顺序依次执行各策略,");
        System.out.println("        第 1 步 = 上面配置的主策略, 后续策略只处理前序策略未处理(剩余)的文件");
        System.out.print("  是否配置策略链? (y/n, 回车=n): ");
        if (parseBoolean(readLine(), false)) {
            List<StrategyStep> chain = new ArrayList<>();
            System.out.println("  [链] 步骤1: " + rule.getStrategyType() + " patterns=" + rule.getPatterns() + "（主策略）");
            while (true) {
                System.out.println("\n  [链] 新增步骤 (策略名留空或输入 0 结束):");
                System.out.println("        可选策略: " + listStrategies());
                System.out.print("        策略类型: ");
                String input = readLine();
                if (input.isEmpty() || input.equals("0")) {
                    break;
                }
                String stepType = resolveStrategyType(input);
                if (stepType == null) {
                    System.out.println("  [失败] 无效策略类型: " + input);
                    continue;
                }
                System.out.print("        patterns (逗号分隔, 回车=匹配所有): ");
                List<String> stepPatterns = parseList(readLine());
                System.out.print("        excludePatterns (逗号分隔, 回车=空): ");
                List<String> stepExcludes = parseList(readLine());
                System.out.print("        replacements (key=value, 逗号分隔, 回车=空): ");
                Map<String, String> stepReplacements = parseMap(readLine());
                StrategyStep step = new StrategyStep();
                step.setStrategyType(stepType);
                step.setPatterns(stepPatterns);
                step.setExcludePatterns(stepExcludes);
                step.setReplacements(stepReplacements);
                chain.add(step);
                System.out.println("  [链] 步骤" + (chain.size() + 1) + ": " + stepType + " patterns=" + stepPatterns);
            }
            if (!chain.isEmpty()) {
                rule.setStrategyChain(chain);
                System.out.println("  [链] 策略链共 " + (chain.size() + 1) + " 步");
            } else {
                System.out.println("  [链] 未新增步骤，保持单一策略");
            }
        }
        return rule;
    }

    /**
     * 生成规则文件并写入（预览 + 确认 + 自校验）
     * 供控制台向导（inputRule 组装规则后调用）与 UI 表单（RuleWizardForm 一次填完参数后调用）共用
     * @param rule 规则对象
     * @param targetDir 目标目录
     */
    public void writeRuleFile(MatchRule rule, Path targetDir) {
        try {
            Path ruleFile = targetDir.resolve(RulesConstants.FileNames.MATCHING_RULES_JSON).normalize();

            // 序列化为 JSON（美化输出）
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String json = mapper.writeValueAsString(rule);

            // 预览
            System.out.println("\n[预览] 生成的规则文件内容 (matching-rules.json):");
            System.out.println("-----------------------------------------");
            System.out.println(json);
            System.out.println("-----------------------------------------");

            // 确认写入
            System.out.print("[确认] 写入 " + ruleFile.getFileName() + " ? (y/n, 回车=n): ");
            if (!parseBoolean(readLine(), false)) {
                System.out.println("[取消] 未写入任何文件");
                return;
            }

            Files.writeString(ruleFile, json);
            System.out.println("[成功] 已生成规则文件: " + ruleFile);

            // 自校验：用解析器反读，确保格式正确
            String readBack = Files.readString(ruleFile);
            if (MatchRuleLoader.fromJson(readBack) != null) {
                System.out.println("[校验] 规则文件解析校验通过 [OK]");
            } else {
                System.out.println("[警告] 规则文件解析校验失败，请检查内容");
            }
        } catch (Exception e) {
            LoggerUtil.logException("生成规则文件失败", e);
        }
    }

    /**
     * 查找目录中已存在的规则文件（按查找优先级）
     */
    private Path findExistingRuleFile(Path targetDir) {
        for (String name : RulesConstants.FileNames.ALL_RULE_FILES) {
            Path f = targetDir.resolve(name);
            if (Files.exists(f)) {
                return f;
            }
        }
        return null;
    }

    /**
     * 列出策略注册表中所有可用策略（编号=类型(说明) 形式，供向导展示）
     */
    private String listStrategies() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String type : StrategyFactory.getSupportedTypes()) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(++i).append("=").append(type);
            String desc = StrategyFactory.getDescription(type);
            if (!desc.isEmpty()) {
                sb.append(" (").append(desc).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * 把用户输入解析为策略类型：直接策略名 或 注册表序号
     * @return 策略类型；无法解析返回 null
     */
    private String resolveStrategyType(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        if (StrategyFactory.isSupported(trimmed)) {
            return trimmed;
        }
        try {
            int idx = Integer.parseInt(trimmed);
            List<String> types = new ArrayList<>(StrategyFactory.getSupportedTypes());
            if (idx >= 1 && idx <= types.size()) {
                return types.get(idx - 1);
            }
        } catch (NumberFormatException ignored) {
            // 非数字输入，走策略名匹配
        }
        return null;
    }

    /**
     * 解析逗号分隔字符串为列表（去空白、去空项）
     */
    private List<String> parseList(String input) {
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

    /**
     * 解析键值对字符串为 Map（格式 key=value, 多个逗号分隔；忽略格式错误的项）
     */
    private Map<String, String> parseMap(String input) {
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
            System.out.println("  [警告] 忽略格式错误的参数项: " + trimmed + " (应为 key=value)");
        }
        return map;
    }

    /**
     * 解析布尔输入（y/yes/true=真, 其它=默认值）
     */
    private boolean parseBoolean(String input, boolean defaultValue) {
        if (input == null || input.trim().isEmpty()) {
            return defaultValue;
        }
        String lower = input.trim().toLowerCase();
        return lower.equals("y") || lower.equals("yes") || lower.equals("true");
    }

    /**
     * 读取一行输入
     */
    private String readLine() {
        return prompter.readLine();
    }
}
