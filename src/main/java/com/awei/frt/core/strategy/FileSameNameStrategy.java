package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.core.uitls.GlobMatcher;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.util.LoggerUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件名称相同策略
 * 根据文件名进行匹配（统一走 GlobMatcher，支持通配符 * ? 且正则元字符安全），
 * 执行增、删、改操作。只处理文件节点（目录节点交给 McMod 等目录级策略）。
 */
public class FileSameNameStrategy extends AbstractOperationStrategy {

    @Override
    public String getStrategyType() {
        return "FileSameName";
    }

    @Override
    public String getDescription() {
        return "同名文件处理策略（按文件名匹配，支持通配符）";
    }

    /**
     * 只处理文件；目录节点直接跳过（不产生忽略日志）
     */
    @Override
    protected boolean accepts(FileNode node, OperationContext context) {
        if (node.isDirectory()) {
            return false;
        }
        return isMatch(node, context);
    }

    /**
     * 白名单/黑名单匹配
     * 空白名单 = 匹配所有；空黑名单 = 不排除任何文件
     */
    private boolean isMatch(FileNode node, OperationContext context) {
        String fileName = node.getName();
        // 策略扩展参数：caseSensitive=false 时文件名匹配忽略大小写（默认 true 区分大小写）
        boolean caseSensitive = !"false".equalsIgnoreCase(context.getRuleParam("caseSensitive"));

        List<String> patterns = context.getRuleInheritanceContext().getRuleChain().getPatterns();
        List<String> excludePatterns = context.getRuleInheritanceContext().getRuleChain().getExcludePatterns();

        if (!GlobMatcher.matchesAny(fileName, patterns, caseSensitive)) {
            LoggerUtil.logInfo("忽略文件：" + fileName);
            return false;
        }
        // 黑名单：空列表表示不排除任何文件（matchesAny 空列表返回 true 是"白名单匹配所有"语义）
        if (excludePatterns != null && !excludePatterns.isEmpty()
                && GlobMatcher.matchesAny(fileName, excludePatterns, caseSensitive)) {
            LoggerUtil.logInfo("忽略文件：" + fileName);
            return false;
        }
        return true;
    }

    /**
     * 新增：目标层没有该文件时才执行（已存在则交给 replace 钩子）
     */
    @Override
    protected boolean doAdd(FileNode node, OperationContext context) {
        Path targetFilePath = context.getTargetPath(node.getRelativePath());
        if (Files.exists(targetFilePath)) {
            return false;
        }
        OperationRecord record = newRecord(context);
        boolean ok = FileUtil.addFile(node.getPath(), targetFilePath, record, context.isDryRun());
        context.recordOperation(record);
        LoggerUtil.logInfo("+ " + node.getName() + " " + (ok ? "成功" : "失败"));
        if (ok) {
            node.setHandled(true); // 处理成功：链中后续策略不再处理该节点
        }
        return true;
    }

    /**
     * 替换：目标层存在同名文件时才执行
     */
    @Override
    protected boolean doReplace(FileNode node, OperationContext context) {
        Path targetFilePath = context.getTargetPath(node.getRelativePath());
        if (!Files.exists(targetFilePath)) {
            return false;
        }
        OperationRecord record = newRecord(context);
        boolean ok = FileUtil.replaceFile(node.getPath(), targetFilePath, record, context.isDryRun());
        context.recordOperation(record);
        LoggerUtil.logInfo("= " + node.getName() + " " + (ok ? "成功" : "失败"));
        if (ok) {
            node.setHandled(true);
        }
        return true;
    }

    /**
     * 删除：按同名路径删除目标文件（目标不存在时由 FileUtil 记录失败）
     */
    @Override
    protected boolean doDelete(FileNode node, OperationContext context) {
        Path targetFilePath = context.getTargetPath(node.getRelativePath());
        OperationRecord record = newRecord(context);
        boolean ok = FileUtil.deleteFile(targetFilePath, record, context.isDryRun());
        context.recordOperation(record);
        LoggerUtil.logInfo("- " + node.getName() + " " + (ok ? "成功" : "失败"));
        if (ok) {
            node.setHandled(true);
        }
        return true;
    }
}
