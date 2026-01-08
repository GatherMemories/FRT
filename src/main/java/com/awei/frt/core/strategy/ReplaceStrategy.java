package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.ReplaceRule;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 替换策略（策略模式实现）
 * 实现文件内容替换操作
 */
public class ReplaceStrategy implements OperationStrategy {
    
    @Override
    public void execute(FileNode node, String rule, OperationContext context) {
        if (rule == null || rule.trim().isEmpty()) {
            context.skip(node.getRelativePath(), "无替换规则");
            return;
        }

        if (node.isDirectory()) {
            // 对于目录，可能需要处理目录级别的操作
            context.skip(node.getRelativePath(), "目录不直接处理替换操作");
            return;
        }

        // 确保是文件节点
        if (!(node instanceof FileLeaf)) {
            context.skip(node.getRelativePath(), "节点类型不支持替换操作");
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
            ReplaceRule replaceRule = ReplaceRule.fromJson(rule);
            if (replaceRule == null) {
                context.skip(node.getRelativePath(), "规则解析失败");
                return;
            }

            // 检查文件是否匹配规则中的模式
            String fileName = targetPath.getFileName().toString();
            if (!replaceRule.matches(fileName)) {
                context.skip(node.getRelativePath(), "文件不匹配规则模式");
                return;
            }

            // 读取目标文件内容
            String content = Files.readString(targetPath);
            String originalContent = content;

            // 执行替换
            for (ReplaceRule.Replacement replacement : replaceRule.getReplacements()) {
                if (replacement.getOldValue() != null && replacement.getNewValue() != null) {
                    content = content.replace(replacement.getOldValue(), replacement.getNewValue());
                }
            }

            // 检查是否有变化
            if (content.equals(originalContent)) {
                context.skip(node.getRelativePath(), "内容无变化");
                return;
            }

            // 确认操作
            if (replaceRule.isConfirmBeforeReplace() && 
                !context.confirm("替换", fileLeaf.getPath(), targetPath)) {
                context.skip(node.getRelativePath(), "用户取消");
                return;
            }

            // 如果需要备份且尚未备份，则执行备份
            if (replaceRule.isBackup()) {
                context.backup(targetPath);
            }

            // 写入替换后的内容
            Files.writeString(targetPath, content);
            context.recordSuccess("替换", fileLeaf.getPath(), targetPath);

        } catch (Exception e) {
            context.recordError(node.getRelativePath(), e);
        }
    }
}