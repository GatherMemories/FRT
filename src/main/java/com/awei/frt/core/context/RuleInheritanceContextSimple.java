package com.awei.frt.core.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则继承上下文（简化版）
 * 管理规则的继承链
 */
public class RuleInheritanceContextSimple {
    private final List<String> ruleChain;  // 规则链（存储为JSON字符串）
    private final List<Path> rulePathChain;

    public RuleInheritanceContextSimple() {
        this.ruleChain = new ArrayList<>();
        this.rulePathChain = new ArrayList<>();
    }

    private RuleInheritanceContextSimple(List<String> ruleChain, List<Path> rulePathChain) {
        this.ruleChain = new ArrayList<>(ruleChain);
        this.rulePathChain = new ArrayList<>(rulePathChain);
    }

    /**
     * 加载当前节点的本地规则
     */
    private String loadLocalRule(Path nodePath) {
        Path ruleFile = nodePath.resolve("replace.json");
        if (Files.exists(ruleFile)) {
            try {
                String rule = Files.readString(ruleFile);
                System.out.println("📋 加载规则: " + ruleFile);
                return rule;
            } catch (Exception e) {
                System.err.println("⚠️  加载规则失败: " + ruleFile + " - " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 获取当前节点的有效规则
     */
    public String getEffectiveRule(Path currentNode) {
        // 优先使用当前节点的规则
        String localRule = loadLocalRule(currentNode);
        if (localRule != null) {
            System.out.println("✓ 节点 " + currentNode + " 使用本地规则");
            return localRule;
        }

        // 继承最近的父节点规则
        if (!ruleChain.isEmpty()) {
            String inheritedRule = ruleChain.get(ruleChain.size() - 1);
            Path inheritedFrom = rulePathChain.get(rulePathChain.size() - 1);
            System.out.println("→ 节点 " + currentNode + " 继承规则，来自 " + inheritedFrom);
            return inheritedRule;
        }

        // 没有规则
        System.out.println("○ 节点 " + currentNode + " 无可用规则");
        return null;
    }

    /**
     * 创建子节点的上下文
     */
    public RuleInheritanceContextSimple createChildContext(Path childPath) {
        String childRule = loadLocalRule(childPath);

        List<String> newRuleChain = new ArrayList<>(ruleChain);
        List<Path> newRulePathChain = new ArrayList<>(rulePathChain);

        if (childRule != null) {
            newRuleChain.add(childRule);
            newRulePathChain.add(childPath);
            System.out.println("★ 子节点 " + childPath + " 有自己的规则，更新规则链");
        } else {
            System.out.println("→ 子节点 " + childPath + " 无规则，将继承父节点规则");
        }

        return new RuleInheritanceContextSimple(newRuleChain, newRulePathChain);
    }

    public boolean hasAnyRule() {
        return !ruleChain.isEmpty();
    }

    public int getRuleDepth() {
        return ruleChain.size();
    }

    public String getRuleSourceInfo() {
        if (ruleChain.isEmpty()) {
            return "无规则";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ruleChain.size(); i++) {
            sb.append("层级 ").append(i + 1).append(": ").append(rulePathChain.get(i));
            if (i < ruleChain.size() - 1) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }
}
