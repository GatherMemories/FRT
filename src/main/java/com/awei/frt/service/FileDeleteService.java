package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.ProgressCallback;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.interaction.ConsoleUserPrompter;
import com.awei.frt.interaction.UserPrompter;
import com.awei.frt.util.LoggerUtil;
import com.awei.frt.util.PreviewUtil;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * 文件删除服务
 * 处理删除目录中的文件删除操作
 */
public class FileDeleteService {

    private final Config config;
    private final UserPrompter prompter;

    public FileDeleteService(Config config, Scanner scanner) {
        this(config, new ConsoleUserPrompter(scanner));
    }

    public FileDeleteService(Config config, UserPrompter prompter) {
        this.config = config;
        this.prompter = prompter;
    }

    /**
     * 执行文件删除操作
     * @return 处理结果
     */
    public ProcessingResult deleteExecute() {
        return deleteExecute(null);
    }

    /**
     * 执行文件删除操作（带进度回调）
     * @param progress 进度回调（真实执行阶段逐文件上报；null 不上报）
     * @return 处理结果
     */
    public ProcessingResult deleteExecute(ProgressCallback progress) {
        try {
            LoggerUtil.logInfo("[执行] 开始执行文件删除操作...");

            // 构建操作上下文
            Path basePath = config.getBaseDirectory();
            Path deletePath = basePath.resolve(config.getDeletePath()).normalize();
            LoggerUtil.logInfo("[FOLDER] 扫描删除目录: " + deletePath);

            // ===== 预览阶段（dryRun）：列出将被删除的文件 =====
            OperationContext previewContext = new OperationContext(config);
            previewContext.setDryRun(true);
            FileNode previewTree = FileTreeBuilder.buildTree(deletePath);
            previewTree.process(null, previewContext, FileNode.DELETE_OPERATION);
            ProcessingResult preview = previewContext.getProcessingResult();
            int planCount = PreviewUtil.printPreview(preview, "删除");
            if (planCount == 0) {
                LoggerUtil.logInfo("[信息] 删除目录中没有文件需要处理");
                return preview;
            }

            // 预览确认（替代原"确认要执行删除操作吗"二次确认，预览已列出具体文件）
            System.out.print("确认要执行以上 " + planCount + " 个删除操作吗？此操作不可逆！(y/n): ");
            String confirm = prompter.readLine().toLowerCase();
            if (!confirm.equals("y") && !confirm.equals("yes")) {
                LoggerUtil.logInfo("[信息] 已取消删除操作（未执行任何操作）");
                preview.setCancelled(true); // 标记取消：预览的"成功数"只是计划，不是已执行
                return preview;
            }

            // ===== 真实执行阶段 =====
            OperationContext context = new OperationContext(config);
            FileNode deleteTree = FileTreeBuilder.buildTree(deletePath);
            // 打印文件树结构（仅控制台）
            System.out.println("[FILE] 文件树结构:");
            FileTreeBuilder.printTree(deleteTree, 0);
            System.out.println();
            int totalFiles = FileTreeBuilder.countFiles(deleteTree);
            LoggerUtil.logInfo("[FILE] 文件数量: " + totalFiles);
            if (progress != null) {
                context.setProgressCallback(progress, totalFiles);
            }

            // 执行删除处理
            LoggerUtil.logInfo("[执行] 正在处理delete文件夹...");
            System.out.println("-----------------------------------------");
            deleteTree.process(null, context, FileNode.DELETE_OPERATION);
            System.out.println("-----------------------------------------");

            // 打印统计信息
            context.printStatistics();
            ProcessingResult processingResult = context.getProcessingResult();
            if (processingResult.getSuccessCount() > 0) {
                LoggerUtil.logInfo("[成功] 文件删除操作完成！");
                // 备份操作记录 + 失败恢复询问（公共流程，见 BackupFileLoader.finishOperationSession）
                BackupFileLoader.finishOperationSession(processingResult, prompter);
            } else if (processingResult.getErrorCount() > 0) {
                LoggerUtil.logError("[失败] 文件删除操作失败！");
            } else {
                // 成功 0 且失败 0：预览后文件被外部删除 / 全部被规则跳过——是"无需删除"而非失败
                LoggerUtil.logInfo("[信息] 没有文件需要删除（预览列出的文件可能已被外部删除或全部跳过）");
            }

            return context.getProcessingResult();
        } catch (Exception e) {
            LoggerUtil.logException("文件删除操作失败", e);

            ProcessingResult result = new ProcessingResult();
            result.setErrorCount(1);
            result.setSuccess(false);
            return result;
        }
    }

}
