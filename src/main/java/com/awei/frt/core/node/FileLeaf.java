package com.awei.frt.core.node;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.MatchRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件叶子节点（组合模式中的叶子节点）
 * 代表实际的文件，不包含子节点
 */
public class FileLeaf extends FileNode {
    public FileLeaf(Path path, String relativePath) {
        super(path, relativePath);
    }

    @Override
    public void process(RuleInheritanceContext localRuleIC, OperationContext context, String[] operationType) {
        if (localRuleIC == null || context == null || operationType == null) {
            return;
        }
        MatchRule rule = localRuleIC.getRuleChain();
        if (rule == null) {
            return;
        }
        // 多策略组合链：按序执行每个策略步骤，前序已处理的节点（handled）后续步骤跳过
        List<MatchRule> steps = rule.getEffectiveStrategies();
        if (steps.isEmpty()) {
            return;
        }
        MatchRule savedRule = localRuleIC.getRuleChain();
        try {
            for (MatchRule step : steps) {
                if (this.isHandled()) {
                    break;
                }
                localRuleIC.setRuleChain(step);
                StrategyFactory.createStrategy(step.getStrategyType()).execute(this, context, operationType);
            }
        } finally {
            localRuleIC.setRuleChain(savedRule);
        }
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    /**
     * 获取文件内容
     */
    public String getContent() {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + path, e);
        }
    }

    /**
     * 设置文件内容
     */
    public void setContent(String content) {
        try {
            Files.writeString(path, content);
        } catch (Exception e) {
            throw new RuntimeException("写入文件失败: " + path, e);
        }
    }


}
