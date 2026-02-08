package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.OperationRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: mou_ren
 * @Date: 2026/1/10 19:50
 * 文件名称相同策略
 * 根据文件名进行匹配，支持通配符过滤，执行增、删、改操作
 */
public class FileSameNameStrategy implements OperationStrategy {

    @Override
    public void execute(FileNode node, OperationContext context, String[] operationType) {
        if (node == null || context == null) {
            return;
        }

        // 如果是目录，不处理（文件节点会单独处理）
        if (node.isDirectory()) {
            return;
        }

        String fileName = node.getName();
        String strategyType = context.getRuleInheritanceContext().getRuleChain().getStrategyType();

        // 获取匹配规则
        List<String> patterns = context.getRuleInheritanceContext().getRuleChain().getPatterns();
        List<String> excludePatterns = context.getRuleInheritanceContext().getRuleChain().getExcludePatterns();

        // 检查文件是否匹配patterns（白名单）
        if (!matches(fileName, patterns)) {
            System.out.println("忽略文件：" + fileName);
            return;
        }
        // 检查文件是否被排除（黑名单）
        if (matches(fileName, excludePatterns)) {
            System.out.println("忽略文件：" + fileName);
            return;
        }

        // 构建目标文件路径（相对同名路径）
        Path targetFilePath = context.getTargetPath(node.getRelativePath());

        // 判断操作类型
        boolean addType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_ADD));
        boolean replaceType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_REPLACE));
        boolean deleteType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_DELETE));

        // 检查目标文件是否存在
        boolean targetFileExists = Files.exists(targetFilePath);

        // 创建操作记录对象，设置基础值
        OperationRecord operationRecord = new OperationRecord();
        operationRecord.setStrategyType(strategyType);

        // 如果目标层没有该文件，则新增
        if (addType && !targetFileExists) {
            boolean b = FileUtil.addFile(node.getPath(), targetFilePath, operationRecord);
            context.getProcessingResult().addOperationRecord(operationRecord);
            System.out.println("+ " + fileName + " " + (b ? "成功" : "失败"));
            return;
        }

        // 如果目标层有同名文件，则替换
        if (replaceType && targetFileExists) {
            boolean b = FileUtil.replaceFile(node.getPath(), targetFilePath, operationRecord);
            context.getProcessingResult().addOperationRecord(operationRecord);
            System.out.println("= " + fileName + " " + (b ? "成功" : "失败"));
            return;
        }

        // 删除操作
        if (deleteType) {
            boolean b = FileUtil.deleteFile(targetFilePath, operationRecord);
            context.getProcessingResult().addOperationRecord(operationRecord);
            System.out.println("- " + fileName + " " + (b ? "成功" : "失败"));
            return;
        }
    }

    /**
     * 检查文件名是否匹配模式（白名单）
     * @param fileName 文件名
     * @param patterns 匹配模式列表
     * @return 是否匹配
     */
    private boolean matches(String fileName, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        return patterns.stream().anyMatch(pattern -> {
            if (pattern.isEmpty()) {
                return true;
            }
            if (pattern.equals("*")) {
                return true;
            }
            if (pattern.equals(fileName)) {
                return true;
            }
            // 简单的 glob 模式匹配
            String regex = pattern.replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".");
            return fileName.matches(regex);
        });
    }

}
