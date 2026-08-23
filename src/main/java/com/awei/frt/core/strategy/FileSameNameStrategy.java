package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.util.LoggerUtil;

import java.nio.file.Files;
import java.nio.file.Path;

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
     * 白名单/黑名单匹配（复用基类 matchesRules：空白名单=匹配所有；空黑名单=不排除；
     * caseSensitive=false 忽略大小写）
     */
    private boolean isMatch(FileNode node, OperationContext context) {
        boolean matched = matchesRules(node, context);
        if (!matched) {
            // 未命中：提示跳过原因（INFO，用户需要知道哪些文件被跳过；
            // 配合已去除的"处理/完成"配对日志，不会像早期那样逐文件刷屏）
            LoggerUtil.logInfo("忽略文件：" + node.getName());
        }
        return matched;
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
        // 预览模式不打"成功/失败"日志（计划已在预览列表展示），避免误以为已执行
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("+ " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true); // 处理成功：链中后续策略不再处理该节点
        }
        return true;
    }

    /**
     * 替换：目标层存在同名文件时才执行
     * 策略扩展参数：
     *   onlyIfContentSame=true 时，源与目标文件内容（MD5）相同则跳过替换（内容一致无需更新）
     */
    @Override
    protected boolean doReplace(FileNode node, OperationContext context) {
        Path targetFilePath = context.getTargetPath(node.getRelativePath());
        if (!Files.exists(targetFilePath)) {
            return false;
        }
        // 参数 onlyIfContentSame=true：源与目标 MD5 相同则跳过替换（内容一致无需写入）
        if (Boolean.parseBoolean(context.getRuleParam("onlyIfContentSame"))
                && isFileContentSame(node.getPath(), targetFilePath)) {
            LoggerUtil.logInfo("~ " + node.getName() + " 内容相同(MD5)，跳过替换");
            context.recordSkip();
            node.setHandled(true); // 内容已一致：链中后续策略无需再处理该文件
            return true;
        }
        OperationRecord record = newRecord(context);
        boolean ok = FileUtil.replaceFile(node.getPath(), targetFilePath, record, context.isDryRun());
        context.recordOperation(record);
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("= " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true);
        }
        return true;
    }

    /**
     * 判断源与目标文件内容是否完全相同（MD5 比较）
     * 任一文件不存在或计算失败时返回 false（保守：不确定就执行替换）
     */
    private boolean isFileContentSame(Path sourcePath, Path targetPath) {
        String sourceMd5 = FileSignUtil.getFileMd5(sourcePath);
        String targetMd5 = FileSignUtil.getFileMd5(targetPath);
        return sourceMd5 != null && sourceMd5.equals(targetMd5);
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
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("- " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true);
        }
        return true;
    }
}
