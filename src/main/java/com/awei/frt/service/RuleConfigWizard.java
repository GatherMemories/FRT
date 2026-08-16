package com.awei.frt.service;

import com.awei.frt.constants.RulesConstants;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FolderNode;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final Scanner scanner;
    private int folderCounter = 0; // 文件夹编号计数器

    public RuleConfigWizard(Config config, Scanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    /**
     * 向导入口
     */
    public void start() {
        try {
            System.out.println("\n=========================================");
            System.out.println("[向导] 生成/编辑匹配规则配置文件 (matching-rules.json)");
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
            try {
                choice = Integer.parseInt(readLine());
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

            // 3. 输入规则参数
            MatchRule rule = inputRule(targetDir);
            if (rule == null) {
                return;
            }

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
        System.out.print("请输入 (1-2, 回车=1): ");
        String input = readLine();
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
        printNode(root, "", true, true, folderIndex);
        return folderIndex;
    }

    private void printNode(FileNode node, String prefix, boolean isLast, boolean isRoot, Map<Integer, Path> folderIndex) {
        if (isRoot) {
            if (node.isDirectory()) {
                int index = ++folderCounter;
                folderIndex.put(index, node.getPath());
                System.out.println("[" + index + "] [+] " + node.getName() + "/");
                printChildren((FolderNode) node, "", folderIndex);
            } else {
                System.out.println("[-] " + node.getName());
            }
            return;
        }
        String connector = isLast ? "└── " : "├── ";
        if (node.isDirectory()) {
            int index = ++folderCounter;
            folderIndex.put(index, node.getPath());
            System.out.println(prefix + connector + "[" + index + "] [+] " + node.getName() + "/");
            printChildren((FolderNode) node, prefix + (isLast ? "    " : "│   "), folderIndex);
        } else {
            System.out.println(prefix + connector + "[-] " + node.getName());
        }
    }

    private void printChildren(FolderNode folder, String childPrefix, Map<Integer, Path> folderIndex) {
        List<FileNode> children = folder.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printNode(children.get(i), childPrefix, i == children.size() - 1, false, folderIndex);
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

        // 1. strategyType（必填）
        System.out.println("[参数 1/5] strategyType — 策略类型");
        System.out.println("  类型: String    必填: 是    默认值: (无)");
        System.out.println("  可选值: 1=McMod        (Minecraft模组策略, 只处理jar文件, 按modId匹配)");
        System.out.println("          2=FileSameName (同名文件处理策略, 按文件名匹配, 支持通配符)");
        String strategyType = null;
        while (strategyType == null) {
            System.out.print("  请输入 (1-2, 或直接输入策略名, 0=取消): ");
            String input = readLine();
            if (input.equals("0")) {
                System.out.println("[取消] 已取消生成");
                return null;
            }
            if (input.equals("1") || input.equalsIgnoreCase("McMod")) {
                strategyType = "McMod";
            } else if (input.equals("2") || input.equalsIgnoreCase("FileSameName")) {
                strategyType = "FileSameName";
            } else {
                System.out.println("  [失败] 无效策略类型，请输入 1 或 2");
            }
        }
        System.out.println("  >> strategyType = \"" + strategyType + "\"");
        boolean mcMod = strategyType.equals("McMod");

        // 2. patterns
        System.out.println("\n[参数 2/5] patterns — 匹配文件模式 (白名单)");
        System.out.println("  类型: List<String>    必填: 否    默认值: 空列表 (匹配所有文件)");
        System.out.println("  说明: 只处理匹配的文件; 支持通配符 * 和 ?; 多个用英文逗号分隔"
                + (mcMod ? "   (当前策略 McMod 不生效, 可回车跳过)" : ""));
        System.out.print("  请输入 (回车=空): ");
        List<String> patterns = parseList(readLine());
        System.out.println("  >> patterns = " + patterns);

        // 3. excludePatterns
        System.out.println("\n[参数 3/5] excludePatterns — 排除文件模式 (黑名单)");
        System.out.println("  类型: List<String>    必填: 否    默认值: 空列表 (不排除任何文件)");
        System.out.println("  说明: 白名单通过后再排除; 支持通配符; 多个用英文逗号分隔"
                + (mcMod ? "   (当前策略 McMod 不生效, 可回车跳过)" : ""));
        System.out.print("  请输入 (回车=空): ");
        List<String> excludePatterns = parseList(readLine());
        System.out.println("  >> excludePatterns = " + excludePatterns);

        // 4. inheritToSubfolders
        System.out.println("\n[参数 4/5] inheritToSubfolders — 规则是否继承到子文件夹");
        System.out.println("  类型: Boolean    必填: 否    默认值: false");
        System.out.println("  说明: true 时, 子文件夹无本地规则则继承本层规则继续处理; false 则子层跳过");
        System.out.print("  请输入 (y/n, 回车=默认 false): ");
        boolean inherit = parseBoolean(readLine(), false);
        System.out.println("  >> inheritToSubfolders = " + inherit);

        // 5. replacements
        System.out.println("\n[参数 5/5] replacements — 策略扩展参数 (键值对)");
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
        return rule;
    }

    /**
     * 生成规则文件并写入（预览 + 确认 + 自校验）
     * @param rule 规则对象
     * @param targetDir 目标目录
     */
    private void writeRuleFile(MatchRule rule, Path targetDir) {
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
                System.out.println("[校验] 规则文件解析校验通过 ✓");
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
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }
}
