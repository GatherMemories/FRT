package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * 文件更新服务（新版 - 使用核心框架）
 * 基于组合模式、策略模式和责任链模式实现
 */
public class FileUpdateServiceNew {

    private final Config config;
    private final Scanner scanner;

    public FileUpdateServiceNew(Config config, Scanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    /**
     * 执行文件更新操作（服务层）
     * @param
     * @return 处理结果
     */
    public ProcessingResult updateExecute() {
        try {
            System.out.println("🔄 开始执行文件更新操作...");

            // 构建操作上下文
            Path basePath = config.getBaseDirectory();

            OperationContext context = new OperationContext(config, scanner);

            // 构建更新目录的文件树
            Path updatePath = basePath.resolve(config.getUpdatePath()).normalize();
            System.out.println("📂 扫描更新目录: " + updatePath);

            FileNode updateTree = FileTreeBuilder.buildTree(updatePath);
            // 打印文件树结构（调试用）
            System.out.println("📄 文件树结构:");
            FileTreeBuilder.printTree(updateTree, 0);
            System.out.println();

            // 执行处理
            System.out.println("🔄 正在处理update文件夹...");
            System.out.println("-----------------------------------------");
            updateTree.process(null, context, updateTree.UPDATE_OPERATION);
            System.out.println("-----------------------------------------");
            // 打印统计信息
            context.printStatistics();
            System.out.println("✅ 文件替换操作完成！");


            System.out.println("🔄 正在备份操作文件...");
            boolean backupSuccess = BackupFileLoader.saveOperationRecord(context.getProcessingResult());
            if (backupSuccess) {
                System.out.println("✅ 备份操作文件成功！");

                // 判断有处理失败的文件时，是否执行恢复操作
                ProcessingResult processingResult = context.getProcessingResult();
                if (processingResult.getErrorCount() > 0) {
                    System.out.println("\n⚠️  检测到 " + processingResult.getErrorCount() + " 个文件处理失败");
                    System.out.println("是否要执行恢复操作，将系统恢复到操作前的状态？(y/n)");

                    String choice = scanner.nextLine().trim().toLowerCase();
                    if (choice.equals("y") || choice.equals("yes")) {
                        System.out.println("\n🔄 开始执行恢复操作...");
                        RestoreResult restoreResult = BackupFileLoader.restoreFromResult(processingResult, scanner);

                        // 打印恢复结果
                        System.out.println("\n📊 恢复结果统计:");
                        System.out.println("   成功恢复: " + restoreResult.getSuccessCount());
                        System.out.println("   恢复失败: " + restoreResult.getFailureCount());
                        System.out.println("   回滚操作: " + restoreResult.getRollbackCount());

                        if (restoreResult.isFullSuccess()) {
                            System.out.println("✅ 系统已成功恢复到操作前的状态");
                        } else if (restoreResult.getRollbackCount() > 0) {
                            System.out.println("⚠️  系统已回滚，但可能处于部分恢复状态");
                        } else {
                            System.out.println("❌ 系统恢复失败，可能处于不一致状态");
                        }
                    } else {
                        System.out.println("ℹ️  用户取消恢复操作");
                    }
                }

            } else {
                System.err.println("❌ 备份操作文件失败！");
            }


            return context.getProcessingResult();
        } catch (Exception e) {
            System.err.println("❌ 文件替换操作失败: " + e.getMessage());
            e.printStackTrace();

            ProcessingResult result = new ProcessingResult();
            result.setErrorCount(1);
            result.setSuccess(false);
            return result;
        }
    }

}
