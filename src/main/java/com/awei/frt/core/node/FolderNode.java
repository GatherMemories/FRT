package com.awei.frt.core.node;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;
import com.awei.frt.core.strategy.OperationStrategy;

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
    public void process(OperationStrategy strategy, String rule, OperationContext context) {
        // 为当前文件夹创建规则继承上下文
        RuleInheritanceContext ruleContext = new RuleInheritanceContext(context.getRuleInheritanceContext());
        
        // 获取当前文件夹的有效规则
        String effectiveRule = ruleContext.getEffectiveRule(this.path);
        
        // 处理当前文件夹（如果需要）
        if (effectiveRule != null) {
            strategy.execute(this, effectiveRule, context);
        }
        
        // 处理子节点
        for (FileNode child : children) {
            // 为子节点创建新的规则继承上下文
            RuleInheritanceContext childRuleContext = ruleContext.createChildContext(child.getPath());
            context.setRuleInheritanceContext(childRuleContext);
            
            // 获取子节点的有效规则并处理
            String childRule = childRuleContext.getEffectiveRule(child.getPath());
            child.process(strategy, childRule, context);
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
        return fileName.equals("replace.json") || 
               fileName.equals("add.json") || 
               fileName.equals("delete.json");
    }

    @Override
    public String toString() {
        return "FolderNode[" + path + ", children: " + children.size() + "]";
    }
}