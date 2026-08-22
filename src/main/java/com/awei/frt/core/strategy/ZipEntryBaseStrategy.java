package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.util.LoggerUtil;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 压缩包策略基类（zip/jar）
 * 作用于压缩包文件本身：按压缩包内部条件命中后，与 FileSameName 相同语义
 * 执行目标同名路径的 新增/替换/删除。
 *
 * 子类只需实现 matchesZipContent（压缩包内部命中判定）。
 */
public abstract class ZipEntryBaseStrategy extends AbstractOperationStrategy {

    /**
     * 压缩包内部命中判定（子类实现）
     * @param zipPath 压缩包文件路径
     * @param context 操作上下文
     * @return 是否命中（决定该压缩包是否参与操作）
     */
    protected abstract boolean matchesZipContent(Path zipPath, OperationContext context);

    /**
     * 只处理 .zip / .jar 文件，且压缩包内部命中
     */
    @Override
    protected boolean accepts(FileNode node, OperationContext context) {
        if (node.isDirectory()) {
            return false;
        }
        String name = node.getName().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".zip") && !name.endsWith(".jar")) {
            return false;
        }
        return matchesZipContent(node.getPath(), context);
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
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("+ " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true);
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
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("= " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
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
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("- " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true);
        }
        return true;
    }
}
