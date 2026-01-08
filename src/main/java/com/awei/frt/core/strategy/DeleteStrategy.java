package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.ReplaceRule;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 删除策略（策略模式实现）
 * 实现文件删除操作
 */
public class DeleteStrategy implements OperationStrategy {
    
    @Override
    public void execute(FileNode node, String rule, OperationContext context) {
        if (rule == null || rule.trim().isEmpty()) {
            context.skip(node.getRelativePath(), "无删除规则");
            return;
        }

        if (node.isDirectory()) {
            // 对于目录，可能需要处理目录级别的操作
            context.skip(node.getRelativePath(), "目录不直接处理删除操作");
            return;
        }

        // 确保是文件节点
        if (!(node instanceof FileLeaf)) {
            context.skip(node.getRelativePath(), "节点类型不支持删除操作");
            return;
        }

        FileLeaf fileLeaf = (FileLeaf) node;
        Path targetPath = context.getTargetPath(node.getRelativePath());

        // 检查目标文件是否存在
        if (!Files.exists(targetPath)) {
            context.skip(node.getRelativePath(), "目标文件不存在");
            return;
        }

        try {
            // 解析规则
            ReplaceRule deleteRule = ReplaceRule.fromJson(rule);
            if (deleteRule == null) {
                context.skip(node.getRelativePath(), "规则解析失败");
                return;
            }

            // 检查文件是否匹配规则中的模式
            String fileName = targetPath.getFileName().toString();
            if (!deleteRule.matches(fileName)) {
                context.skip(node.getRelativePath(), "文件不匹配规则模式");
                return;
            }

            // 确认操作
            if (deleteRule.isConfirmBeforeReplace() && 
                !context.confirm("删除", fileLeaf.getPath(), targetPath)) {
                context.skip(node.getRelativePath(), "用户取消");
                return;
            }

            // 如果需要备份，则先备份
            if (deleteRule.isBackup()) {
                context.backup(targetPath);
            }

            // 删除文件
            Files.delete(targetPath);
            context.recordSuccess("删除", fileLeaf.getPath(), targetPath);

        } catch (Exception e) {
            context.recordError(node.getRelativePath(), e);
        }
    }
}