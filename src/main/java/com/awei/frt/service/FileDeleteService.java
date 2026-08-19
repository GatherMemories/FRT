package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FolderNode;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.ui.ConsoleUserPrompter;
import com.awei.frt.ui.UserPrompter;
import com.awei.frt.util.LoggerUtil;
import com.awei.frt.util.PreviewUtil;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
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
                LoggerUtil.logInfo("[信息] 已取消删除操作");
                ProcessingResult cancelResult = new ProcessingResult();
                cancelResult.setSuccess(false);
                return cancelResult;
            }

            // ===== 真实执行阶段 =====
            OperationContext context = new OperationContext(config);
            FileNode deleteTree = FileTreeBuilder.buildTree(deletePath);
            // 打印文件树结构（仅控制台）
            System.out.println("[FILE] 文件树结构:");
            FileTreeBuilder.printTree(deleteTree, 0);
            System.out.println();
            LoggerUtil.logInfo("[FILE] 文件数量: " + countFiles(deleteTree));

            // 执行删除处理
            LoggerUtil.logInfo("[执行] 正在处理delete文件夹...");
            System.out.println("-----------------------------------------");
            deleteTree.process(null, context, FileNode.DELETE_OPERATION);
            System.out.println("-----------------------------------------");

            // 打印统计信息
            context.printStatistics();
            ProcessingResult processingResult = context.getProcessingResult();
            if(processingResult.getSuccessCount() > 0){
                LoggerUtil.logInfo("[成功] 文件删除操作完成！");
                // 备份操作记录 + 失败恢复询问（公共流程，见 BackupFileLoader.finishOperationSession）
                BackupFileLoader.finishOperationSession(processingResult, prompter);
            }else{
                LoggerUtil.logError("[失败] 文件删除操作失败！");
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

    /**
     * 统计文件数量（栈迭代，避免深目录递归栈溢出）
     * @param node 文件节点
     * @return 文件数量
     */
    private int countFiles(FileNode node) {
        if (node == null) {
            return 0;
        }
        int count = 0;
        Deque<FileNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            FileNode current = stack.pop();
            if (!current.isDirectory()) {
                count++;
            } else if (current instanceof FolderNode folderNode) {
                for (FileNode child : folderNode.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return count;
    }
}
