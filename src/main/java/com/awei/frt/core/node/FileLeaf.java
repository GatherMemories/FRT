package com.awei.frt.core.node;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;

import java.nio.file.Files;
import java.nio.file.Path;

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
        if(localRuleIC == null || localRuleIC.getRuleChain() == null || localRuleIC.getRuleChain().getStrategyType() == null){
            return;
        }
        OperationStrategy strategy = StrategyFactory.createStrategy(localRuleIC.getRuleChain().getStrategyType());
        strategy.execute(this, context, operationType);
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

    /**
     * 检查是否是文件
     */
    public boolean isFile() {
        return Files.isRegularFile(path);
    }
}
