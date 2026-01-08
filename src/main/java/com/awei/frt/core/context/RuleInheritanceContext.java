package com.awei.frt.core.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则继承上下文（责任链模式）
 * 实现多层级规则继承机制
 * 子节点优先使用自己的规则，否则继承父节点规则
 */
public class RuleInheritanceContext {
    private final List<String> ruleChain;           // 规则链（存储规则内容）
    private final List<Path> rulePathChain;         // 规则路径链（存储规则文件路径）
    private final List<String> ruleTypeChain;       // 规则类型链（replace.json, add.json, delete.json）

    public RuleInheritanceContext() {
        this.ruleChain = new ArrayList<>();
        this.rulePathChain = new ArrayList<>();
        this.ruleTypeChain = new ArrayList<>();
    }
    
    public RuleInheritanceContext(RuleInheritanceContext other) {
        if (other != null) {
            this.ruleChain = new ArrayList<>(other.ruleChain);
            this.rulePathChain = new ArrayList<>(other.rulePathChain);
            this.ruleTypeChain = new ArrayList<>(other.ruleTypeChain);
        } else {
            this.ruleChain = new ArrayList<>();
            this.rulePathChain = new ArrayList<>();
            this.ruleTypeChain = new ArrayList<>();
        }
    }
    
    private RuleInheritanceContext(List<String> ruleChain, List<Path> rulePathChain, List<String> ruleTypeChain) {
        this.ruleChain = new ArrayList<>(ruleChain);
        this.rulePathChain = new ArrayList<>(rulePathChain);
        this.ruleTypeChain = new ArrayList<>(ruleTypeChain);
    }

    /**
     * 加载当前节点的本地规则
     * 按优先级顺序查找：replace.json -> add.json -> delete.json
     */
    private String loadLocalRule(Path nodePath) {
        // 按优先级顺序查找规则文件
        String[] ruleTypes = {"replace.json", "add.json", "delete.json"};
        
        for (String ruleType : ruleTypes) {
            Path ruleFile = nodePath.resolve(ruleType);
            if (Files.exists(ruleFile)) {
                try {
                    String rule = Files.readString(ruleFile);
                    System.out.println("📋 加载规则: " + ruleFile + " (类型: " + ruleType + ")");
                    return rule;
                } catch (Exception e) {
                    System.err.println("⚠️  加载规则失败: " + ruleFile + " - " + e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 获取当前节点的有效规则
     * 优先级：本地规则 > 父节点规则 > null
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
     * 如果子节点有自己的规则，则将其添加到规则链中
     * 否则继承父节点的规则
     */
    public RuleInheritanceContext createChildContext(Path childPath) {
        String childRule = loadLocalRule(childPath);

        List<String> newRuleChain = new ArrayList<>(ruleChain);
        List<Path> newRulePathChain = new ArrayList<>(rulePathChain);
        List<String> newRuleTypeChain = new ArrayList<>(ruleTypeChain);

        if (childRule != null) {
            newRuleChain.add(childRule);
            newRulePathChain.add(childPath);
            // 确定规则类型
            String ruleType = determineRuleType(childPath);
            newRuleTypeChain.add(ruleType);
            System.out.println("★ 子节点 " + childPath + " 有自己的规则，更新规则链");
        } else {
            System.out.println("→ 子节点 " + childPath + " 无规则，将继承父节点规则");
        }

        return new RuleInheritanceContext(newRuleChain, newRulePathChain, newRuleTypeChain);
    }

    /**
     * 确定节点的规则类型
     */
    private String determineRuleType(Path nodePath) {
        String[] ruleTypes = {"replace.json", "add.json", "delete.json"};
        
        for (String ruleType : ruleTypes) {
            Path ruleFile = nodePath.resolve(ruleType);
            if (Files.exists(ruleFile)) {
                return ruleType;
            }
        }
        return "none"; // 没有规则文件
    }

    /**
     * 检查是否有任何规则
     */
    public boolean hasAnyRule() {
        return !ruleChain.isEmpty();
    }

    /**
     * 获取规则深度（规则链长度）
     */
    public int getRuleDepth() {
        return ruleChain.size();
    }

    /**
     * 获取规则来源信息
     */
    public String getRuleSourceInfo() {
        if (ruleChain.isEmpty()) {
            return "无规则";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ruleChain.size(); i++) {
            sb.append("层级 ").append(i + 1).append(": ").append(rulePathChain.get(i))
              .append(" (").append(ruleTypeChain.get(i)).append(")");
            if (i < ruleChain.size() - 1) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }

    /**
     * 获取当前有效的规则类型
     */
    public String getCurrentRuleType() {
        if (ruleTypeChain.isEmpty()) {
            return "none";
        }
        return ruleTypeChain.get(ruleTypeChain.size() - 1);
    }

    /**
     * 获取规则链的副本
     */
    public List<String> getRuleChain() {
        return new ArrayList<>(ruleChain);
    }

    /**
     * 获取规则路径链的副本
     */
    public List<Path> getRulePathChain() {
        return new ArrayList<>(rulePathChain);
    }

    /**
     * 获取规则类型链的副本
     */
    public List<String> getRuleTypeChain() {
        return new ArrayList<>(ruleTypeChain);
    }
}