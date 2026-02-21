package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FolderNode;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * 文件删除服务
 * 处理删除目录中的文件删除操作
 */
public class FileDeleteService {

    private final Config config;
    private final Scanner scanner;

    public FileDeleteService(Config config, Scanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    /**
     * 执行文件删除操作
     * @return 处理结果
     */
    public ProcessingResult deleteExecute() {
        try {
            System.out.println("[执行] 开始执行文件删除操作...");

            // 构建操作上下文
            Path basePath = config.getBaseDirectory();
            OperationContext context = new OperationContext(config, scanner);

            // 构建删除目录的文件树
            Path deletePath = basePath.resolve(config.getDeletePath()).normalize();
            System.out.println("[FOLDER] 扫描删除目录: " + deletePath);

            FileNode deleteTree = FileTreeBuilder.buildTree(deletePath);

            // 检查是否有文件要删除
            if (!hasFilesToDelete(deleteTree)) {
                System.out.println("[信息] 删除目录中没有文件需要处理");
                ProcessingResult emptyResult = new ProcessingResult();
                emptyResult.setSuccess(true);
                return emptyResult;
            }

            // 打印文件树结构
            System.out.println("[FILE] 文件树结构:");
            FileTreeBuilder.printTree(deleteTree, 0);
            System.out.println();
            System.out.println("[FILE] 文件数量: " + countFiles(deleteTree));

            // 二次确认
            System.out.println("-----------------------------------------");
            System.out.print("确认要执行删除操作吗？此操作不可逆！(y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();

            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("[信息] 已取消删除操作");
                ProcessingResult cancelResult = new ProcessingResult();
                cancelResult.setSuccess(false);
                return cancelResult;
            }

            // 执行删除处理
            System.out.println("\n[执行] 正在处理delete文件夹...");
            System.out.println("-----------------------------------------");
            deleteTree.process(null, context, FileNode.DELETE_OPERATION);
            System.out.println("-----------------------------------------");

            // 打印统计信息
            context.printStatistics();
            ProcessingResult processingResult = context.getProcessingResult();
            if(processingResult.getSuccessCount() > 0){
                System.out.println("[成功] 文件删除操作完成！");
            }else{
                System.out.println("[失败] 文件删除操作失败！");
            }

            if(processingResult.getSuccessCount() > 0){
                // 备份操作记录
                System.out.println("[执行] 正在备份操作文件...");
                boolean backupSuccess = BackupFileLoader.saveOperationRecord(context.getProcessingResult());
                if (backupSuccess) {
                    System.out.println("[成功] 备份操作文件成功！");

                    // 判断有处理失败的文件时，是否执行恢复操作

                    if (processingResult.getErrorCount() > 0 && processingResult.getSuccessCount() > 0) {
                        System.out.println("\n[警告] 检测到 " + processingResult.getErrorCount() + " 个文件处理失败");
                        System.out.println("是否要执行恢复操作，将系统恢复到操作前的状态？(y/n)");

                        String choice = scanner.nextLine().trim().toLowerCase();
                        if (choice.equals("y") || choice.equals("yes")) {
                            System.out.println("\n[执行] 开始执行恢复操作...");
                            RestoreResult restoreResult = BackupFileLoader.restoreFromResult(processingResult, scanner);

                            // 打印恢复结果
                            System.out.println("\n[STATS] 恢复结果统计:");
                            System.out.println("   成功恢复: " + restoreResult.getSuccessCount());
                            System.out.println("   恢复失败: " + restoreResult.getFailureCount());
                            System.out.println("   回滚操作: " + restoreResult.getRollbackCount());

                            if (restoreResult.isFullSuccess()) {
                                System.out.println("[成功] 系统已成功恢复到操作前的状态");
                            } else if (restoreResult.getRollbackCount() > 0) {
                                System.out.println("[警告] 系统已回滚，但可能处于部分恢复状态");
                            } else {
                                System.out.println("[失败] 系统恢复失败，可能处于不一致状态");
                            }
                        } else {
                            System.out.println("[信息] 用户取消恢复操作");
                        }
                    }

                } else {
                    System.err.println("[失败] 备份操作文件失败！");
                }
            }


            return context.getProcessingResult();
        } catch (Exception e) {
            System.err.println("[失败] 文件删除操作失败: " + e.getMessage());
            e.printStackTrace();

            ProcessingResult result = new ProcessingResult();
            result.setErrorCount(1);
            result.setSuccess(false);
            return result;
        }
    }

    /**
     * 检查文件树是否有文件要删除
     * @param node 文件节点
     * @return 是否有文件
     */
    private boolean hasFilesToDelete(FileNode node) {
        if (node == null) {
            return false;
        }
        if (!node.isDirectory()) {
            return true;
        }
        if (node.getChildCount() == 0) {
            return false;
        }
        if (node instanceof FolderNode folderNode) {
            for (FileNode child : folderNode.getChildren()) {
                if (hasFilesToDelete(child)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * 统计文件数量
     * @param node 文件节点
     * @return 文件数量
     */
    private int countFiles(FileNode node) {
        if (node == null) {
            return 0;
        }
        if (!node.isDirectory()) {
            return 1;
        }
        int count = 0;
        if (node instanceof FolderNode folderNode) {
            for (FileNode child : folderNode.getChildren()) {
                count += countFiles(child);
            }
        }
        return count;
    }
}
