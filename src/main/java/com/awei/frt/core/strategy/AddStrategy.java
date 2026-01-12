package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.MatchRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 新增策略（策略模式实现）
 * 实现文件新增操作
 */
public class AddStrategy implements OperationStrategy {

    @Override
    public void execute(FileNode node, String rule, OperationContext context) {
        if (rule == null || rule.trim().isEmpty()) {
            context.skip(node.getRelativePath(), "无新增规则");
            return;
        }

        if (node.isDirectory()) {
            // 对于目录，可能需要处理目录级别的操作
            context.skip(node.getRelativePath(), "目录不直接处理新增操作");
            return;
        }

        // 确保是文件节点
        if (!(node instanceof FileLeaf)) {
            context.skip(node.getRelativePath(), "节点类型不支持新增操作");
            return;
        }

        FileLeaf fileLeaf = (FileLeaf) node;
        Path targetPath = context.getTargetPath(node.getRelativePath());

        try {
            // 解析规则
            MatchRule addRule = MatchRule.fromJson(rule);
            if (addRule == null) {
                context.skip(node.getRelativePath(), "规则解析失败");
                return;
            }

            // 检查文件是否匹配规则中的模式
            String fileName = targetPath.getFileName().toString();
            if (!addRule.matches(fileName)) {
                context.skip(node.getRelativePath(), "文件不匹配规则模式");
                return;
            }

            // 检查目标文件是否已存在
            if (Files.exists(targetPath)) {
                context.skip(node.getRelativePath(), "目标文件已存在");
                return;
            }

            // 确认操作
            if (!context.confirm("新增", fileLeaf.getPath(), targetPath)) {
                context.skip(node.getRelativePath(), "用户取消");
                return;
            }

            // 创建目标目录（如果不存在）
            Path targetDir = targetPath.getParent();
            if (targetDir != null && !Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // 复制文件到目标位置
            Files.copy(fileLeaf.getPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 是否备份且文件是新创建的，则记录操作
            if (!context.confirm("是否覆盖旧恢复点", fileLeaf.getPath(), targetPath)) {
                context.backup(targetPath);
            }

            context.recordSuccess("新增", fileLeaf.getPath(), targetPath);

        } catch (Exception e) {
            context.recordError(node.getRelativePath(), e);
        }
    }
}
