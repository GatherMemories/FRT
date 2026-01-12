package com.awei.frt.core.context;

import com.awei.frt.constants.RulesConstants;
import com.awei.frt.model.MatchRule;

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
    private final MatchRule ruleChain;           // 匹配规则（存储规则内容）

    public RuleInheritanceContext() {
        this.ruleChain = null;
    }

    public RuleInheritanceContext(RuleInheritanceContext other) {
        if (other != null) {
            this.ruleChain = other.ruleChain;
        } else {
            this.ruleChain = null;
        }
    }

    public RuleInheritanceContext(MatchRule ruleChain) {
        this.ruleChain = ruleChain;
    }

    public MatchRule getRuleChain() {
        return ruleChain;
    }

    /**
     * 加载当前节点的本地规则
     * 按优先级顺序查找：replace.json -> add.json -> delete.json
     * 使用 Gson 解析为 MatchRule 对象
     */
    private MatchRule loadLocalRule(Path nodePath) {
        // 按优先级顺序查找规则文件
        String[] ruleTypes = RulesConstants.FileNames.ALL_RULE_FILES;

        for (String ruleType : ruleTypes) {
            Path ruleFile = nodePath.resolve(ruleType);
            if (Files.exists(ruleFile)) {
                try {
                    String ruleJson = Files.readString(ruleFile);
                    MatchRule rule = MatchRule.fromJson(ruleJson);
                    rule.setPath(ruleFile); // 设置规则文件路径
                } catch (java.io.IOException e) {
                    System.err.println("⚠️  读取规则文件失败: " + ruleFile + " - " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("⚠️  解析规则失败: " + ruleFile + " - " + e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * 获取当前节点的有效规则
     * 优先级：本地规则 > 父节点规则 > null
     */
    public MatchRule getEffectiveRule(Path currentNode) {
        // 优先使用当前节点的规则
        MatchRule localRule = loadLocalRule(currentNode);
        if (localRule != null) {
            System.out.println("✓ 节点 " + currentNode + " (使用本地规则)");
            return localRule;
        }

        // 继承最近的父节点规则
        if (ruleChain != null && ruleChain.isInheritToSubfolders()) {
            MatchRule inheritedRule = ruleChain;
            System.out.println("→ 节点 " + currentNode + " (继承规则)");
            return inheritedRule;
        }

        // 没有规则
        System.out.println("○ 节点 " + currentNode + " (无可用规则)");
        return null;
    }

    /**
     * 创建子节点的上下文
     * 如果子节点有自己的规则，则将其添加到规则链中
     * 否则继承父节点的规则
     */
    public RuleInheritanceContext createChildContext(Path childPath) {
        MatchRule childRule = getEffectiveRule(childPath);

        return new RuleInheritanceContext(childRule);
    }

}
