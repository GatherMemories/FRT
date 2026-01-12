package com.awei.frt.core.node;

import com.awei.frt.constants.RulesConstants;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.MatchRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件夹节点（组合模式中的复合节点）
 * 包含子节点（文件和子文件夹）
 */
public class FolderNode extends FileNode {
    private final List<FileNode> children;

    public FolderNode(Path path, String relativePath) {
        super(path, relativePath);
        this.children = new ArrayList<>();
    }

    @Override
    public void process(RuleInheritanceContext localRuleIC, OperationContext context, String operationType) {
        // 创建当前文件夹的规则上下文副本（本层变量）
        RuleInheritanceContext ruleContext = new RuleInheritanceContext(context.getRuleInheritanceContext());

        // 获取当前文件夹的有效规则
        MatchRule effectiveRule = ruleContext.getEffectiveRule(this.path);
        // 获取当前文件夹的策略类型
        OperationStrategy strategy = StrategyFactory.createStrategy(effectiveRule.getStrategyType());

        // 处理当前文件夹（如果需要）
        if (effectiveRule != null) {
            strategy.execute(this, context, operationType);
        }

        // 先处理文件再处理文件夹（暂存文件夹节点）
        List<FolderNode> pendingFolderNodeList = new ArrayList<>(20);

        // 处理子节点
        for (FileNode child : children) {
            if(child instanceof FolderNode){
                pendingFolderNodeList.add((FolderNode) child);
                continue;
            }
            // 为子节点创建新的规则继承上下文（是否继承规则）
            RuleInheritanceContext childRuleContext = ruleContext.createChildContext(child.getPath());
            context.setRuleInheritanceContext(childRuleContext);

            child.process(childRuleContext, context, operationType);
        }
        for (FolderNode child : pendingFolderNodeList){
            // 为子节点创建新的规则继承上下文（是否继承规则）
            RuleInheritanceContext childRuleContext = ruleContext.createChildContext(child.getPath());
            context.setRuleInheritanceContext(childRuleContext);
            child.process(childRuleContext, context, operationType);
        }
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    /**
     * 添加子节点
     */
    public void addChild(FileNode child) {
        children.add(child);
    }

    /**
     * 获取所有子节点
     */
    public List<FileNode> getChildren() {
        return new ArrayList<>(children);
    }

    /**
     * 获取子文件列表
     */
    public List<FileNode> getFileChildren() {
        return children.stream()
                .filter(child -> !child.isDirectory())
                .collect(Collectors.toList());
    }

    /**
     * 获取子目录列表
     */
    public List<FileNode> getDirectoryChildren() {
        return children.stream()
                .filter(FileNode::isDirectory)
                .collect(Collectors.toList());
    }

    /**
     * 检查是否是目录
     */
    public boolean isDirectoryType() {
        return Files.isDirectory(path);
    }

    /**
     * 递归构建子节点
     */
    public void buildChildren() {
        if (!isDirectoryType()) {
            return;
        }

        try (Stream<Path> stream = Files.list(path)) {
            List<Path> paths = stream.collect(Collectors.toList());

            for (Path childPath : paths) {
                String childRelativePath = relativePath.isEmpty() ?
                    childPath.getFileName().toString() :
                    relativePath + "/" + childPath.getFileName();

                // 跳过规则配置文件，但记录它们的存在
                String fileName = childPath.getFileName().toString();
                if (isRuleFile(fileName)) {
                    continue; // 规则文件不作为普通子节点添加，但会影响规则继承
                }

                if (Files.isDirectory(childPath)) {
                    FolderNode folderNode = new FolderNode(childPath, childRelativePath);
                    folderNode.buildChildren(); // 递归构建
                    addChild(folderNode);
                } else {
                    addChild(new FileLeaf(childPath, childRelativePath));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("构建文件夹节点失败: " + path, e);
        }
    }

    /**
     * 检查是否是规则文件
     */
    private boolean isRuleFile(String fileName) {
        String ruleType = RulesConstants.FileNames.MATCHING_RULES_JSON;
        if (fileName.equals(ruleType)) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "FolderNode[" + path + ", children: " + children.size() + "]";
    }
}
