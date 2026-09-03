package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.ProgressCallback;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.utils.FileUtil;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.interaction.ConsoleUserPrompter;
import com.awei.frt.interaction.UserPrompter;
import com.awei.frt.util.LoggerUtil;
import com.awei.frt.util.PreviewUtil;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * 文件更新服务（新版 - 使用核心框架）
 * 基于组合模式、策略模式和责任链模式实现
 */
public class FileUpdateServiceNew {

    private final Config config;
    private final UserPrompter prompter;

    public FileUpdateServiceNew(Config config, Scanner scanner) {
        this(config, new ConsoleUserPrompter(scanner));
    }

    public FileUpdateServiceNew(Config config, UserPrompter prompter) {
        this.config = config;
        this.prompter = prompter;
    }

    /**
     * 执行文件更新操作（服务层）
     * @return 处理结果
     */
    public ProcessingResult updateExecute() {
        return updateExecute(null);
    }

    /**
     * 执行文件更新操作（服务层，带进度回调）
     * @param progress 进度回调（真实执行阶段逐文件上报；null 不上报）
     * @return 处理结果
     */
    public ProcessingResult updateExecute(ProgressCallback progress) {
        try {
            LoggerUtil.logInfo("[执行] 开始执行文件更新操作...");

            // 构建操作上下文
            Path basePath = config.getBaseDirectory();
            Path updatePath = basePath.resolve(config.getUpdatePath()).normalize();

            // ===== 预览阶段（dryRun）：只收集操作计划，不真正改动文件 =====
            LoggerUtil.logInfo("[FOLDER] 扫描更新目录: " + updatePath);
            OperationContext previewContext = new OperationContext(config);
            previewContext.setDryRun(true);
            FileNode previewTree = FileTreeBuilder.buildTree(updatePath);
            previewTree.process(null, previewContext, FileNode.UPDATE_OPERATION);
            ProcessingResult preview = previewContext.getProcessingResult();
            int planCount = PreviewUtil.printPreview(preview, "更新");
            if (planCount == 0) {
                LoggerUtil.logInfo("[信息] 没有需要更新的文件");
                return preview;
            }
            System.out.print("是否执行以上 " + planCount + " 个更新操作？(y/n): ");
            String choice = prompter.readLine().toLowerCase();
            if (!choice.equals("y") && !choice.equals("yes")) {
                LoggerUtil.logInfo("[信息] 用户取消更新操作（未执行任何操作）");
                preview.setCancelled(true); // 标记取消：预览的"成功数"只是计划，不是已执行
                return preview;
            }

            // ===== 真实执行阶段 =====
            OperationContext context = new OperationContext(config);
            FileNode updateTree = FileTreeBuilder.buildTree(updatePath);
            if (progress != null) {
                context.setProgressCallback(progress, FileTreeBuilder.countFiles(updateTree));
            }
            // 打印文件树结构（调试用，仅控制台）
            System.out.println("[FILE] 文件树结构:");
            FileTreeBuilder.printTree(updateTree, 0);
            System.out.println();

            // 执行处理
            LoggerUtil.logInfo("[执行] 正在处理update文件夹...");
            System.out.println("-----------------------------------------");
            updateTree.process(null, context, FileNode.UPDATE_OPERATION);
            System.out.println("-----------------------------------------");
            // 打印统计信息
            context.printStatistics();
            // 判断有处理失败的文件时，是否执行恢复操作（备份+恢复询问已提炼为公共方法）
            ProcessingResult processingResult = context.getProcessingResult();
            LoggerUtil.logInfo("[成功] 文件替换操作完成！");
            BackupFileLoader.finishOperationSession(processingResult, prompter);

            return context.getProcessingResult();
        } catch (Exception e) {
            LoggerUtil.logException("文件替换操作失败", e);

            ProcessingResult result = new ProcessingResult();
            result.setErrorCount(1);
            result.setSuccess(false);
            return result;
        }
    }

}
