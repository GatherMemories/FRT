package com.awei.frt.core.builder;

import com.awei.frt.core.context.RuleInheritanceContextSimple;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FileNodeImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则链构建器（简化版）
 * 构建规则继承链，确定每个文件使用的规则
 */
public class RuleChainBuilder {
    private final com.awei.frt.core.node.RootNode rootNode;
    private final Map<String, String> ruleMap; // 文件路径 -> 规则

    public RuleChainBuilder(com.awei.frt.core.node.RootNode rootNode) {
        this.rootNode = rootNode;
        this.ruleMap = new HashMap<>();
    }

    /**
     * 构建规则链
     */
    public void build() {
        RuleInheritanceContextSimple context = new RuleInheritanceContextSimple();
        buildRuleForNode(rootNode, context);
    }

    /**
     * 递归为每个节点构建规则
     */
    private void buildRuleForNode(FileNode node, RuleInheritanceContextSimple context) {
        FileNodeImpl nodeImpl = (FileNodeImpl) node;

        if (nodeImpl.isFile()) {
            // 获取当前节点的有效规则
            String rule = context.getEffectiveRule(nodeImpl.getPath());
            if (rule != null) {
                ruleMap.put(node.getRelativePath(), rule);
            }
        } else if (nodeImpl.isDirectory()) {
            // 创建子节点的上下文
            RuleInheritanceContextSimple childContext = context.createChildContext(nodeImpl.getPath());

            // 处理子文件
            List<FileNode> children = nodeImpl.getChildren();
            for (FileNode child : children) {
                buildRuleForNode(child, childContext);
            }

            // 处理子目录
            List<FileNode> childDirs = nodeImpl.getChildDirectories();
            for (FileNode childDir : childDirs) {
                buildRuleForNode(childDir, childContext);
            }
        }
    }

    /**
     * 获取指定文件的规则
     */
    public String getRuleForFile(String relativePath) {
        return ruleMap.get(relativePath);
    }

    /**
     * 获取所有有规则的文件
     */
    public List<String> getFilesWithRules() {
        return new ArrayList<>(ruleMap.keySet());
    }

    /**
     * 检查文件是否有规则
     */
    public boolean hasRule(String relativePath) {
        return ruleMap.containsKey(relativePath);
    }

    /**
     * 获取规则映射
     */
    public Map<String, String> getRuleMap() {
        return new HashMap<>(ruleMap);
    }

    /**
     * 打印规则链信息
     */
    public void printRuleChain() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📋 规则继承链:");
        System.out.println("=".repeat(50));

        RuleInheritanceContextSimple context = new RuleInheritanceContextSimple();
        printRuleForNode(rootNode, context, 0);

        System.out.println("=".repeat(50));
        System.out.println("✓ 共有 " + ruleMap.size() + " 个文件应用了替换规则");
        System.out.println("=".repeat(50) + "\n");
    }

    /**
     * 递归打印节点的规则信息
     */
    private void printRuleForNode(FileNode node, RuleInheritanceContextSimple context, int indent) {
        FileNodeImpl nodeImpl = (FileNodeImpl) node;

        String prefix = "  ".repeat(indent);

        if (nodeImpl.isDirectory()) {
            String relativePath = node.getRelativePath().isEmpty() ? "/" : node.getRelativePath();
            String ruleInfo = context.hasAnyRule() ? "✓ 有规则" : "○ 无规则";

            System.out.println(prefix + "📁 " + relativePath + " " + ruleInfo);

            // 创建子节点的上下文
            RuleInheritanceContextSimple childContext = context.createChildContext(nodeImpl.getPath());

            // 处理子目录
            List<FileNode> childDirs = nodeImpl.getChildDirectories();
            for (FileNode childDir : childDirs) {
                printRuleForNode(childDir, childContext, indent + 1);
            }

            // 处理子文件
            List<FileNode> children = nodeImpl.getChildren();
            for (FileNode child : children) {
                printRuleForNode(child, childContext, indent + 1);
            }
        } else {
            // 文件
            String rule = context.getEffectiveRule(nodeImpl.getPath());
            if (rule != null) {
                System.out.println(prefix + "📄 " + node.getRelativePath() + " ✓ 继承规则");
            }
        }
    }
}
