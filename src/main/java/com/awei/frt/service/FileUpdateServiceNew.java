package com.awei.frt.service;

import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;

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
            Path targetPath = basePath.resolve(config.getTargetPath());
            Path backupPath = basePath.resolve(config.getBackupPath());

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
            System.out.println("🔄 正在预处理update文件夹...");
            System.out.println("-----------------------------------------");
            updateTree.process(null, context, updateTree.UPDATE_OPERATION);
            System.out.println("-----------------------------------------");
            // 打印统计信息
            context.printStatistics();
            System.out.println("是否实际执行？y/n");
            if(scanner.nextLine().equals("n")){
                System.out.println("已取消 update操作");
                return null;
            }
            System.out.println("🔄 正在执行update文件夹...");
            System.out.println("-----------------------------------------");
            FileUtil.executeOperations(context.getProcessingResult().getOperationRecords());
            System.out.println("-----------------------------------------");


            System.out.println("✅ 文件替换操作完成！");
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
