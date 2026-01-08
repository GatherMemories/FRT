package com.awei.frt.service;

import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.utils.ConfigLoader;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * 文件替换服务（新版 - 使用核心框架）
 * 基于组合模式、策略模式和责任链模式实现
 */
public class FileReplaceServiceNew {
    
    private final Config config;
    private final Scanner scanner;

    public FileReplaceServiceNew(Config config, Scanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    /**
     * 执行文件替换操作
     * @return 处理结果
     */
    public ProcessingResult executeReplace() {
        try {
            System.out.println("🔄 开始执行文件替换操作...");
            
            // 构建操作上下文
            Path basePath = config.getBaseDirectory();
            Path targetPath = basePath.resolve(config.getTargetPath());
            Path backupPath = basePath.resolve(config.getBackupPath());
            
            OperationContext context = new OperationContext(basePath, targetPath, backupPath, scanner);
            
            // 构建更新目录的文件树
            Path updatePath = basePath.resolve(config.getUpdatePath());
            System.out.println("📂 扫描更新目录: " + updatePath);
            
            FileNode updateTree = FileTreeBuilder.buildTree(updatePath);
            
            // 打印文件树结构（调试用）
            System.out.println("📄 文件树结构:");
            FileTreeBuilder.printTree(updateTree, 0);
            System.out.println();
            
            // 创建替换策略
            OperationStrategy strategy = StrategyFactory.createStrategy(StrategyFactory.OperationType.REPLACE);
            
            // 执行处理
            System.out.println("🔄 开始处理文件...");
            System.out.println("-----------------------------------------");
            updateTree.process(strategy, null, context); // 初始规则为null，由规则继承机制决定
            System.out.println("-----------------------------------------");
            
            // 生成处理结果
            ProcessingResult result = new ProcessingResult();
            result.setSuccessCount(context.getSuccessCount());
            result.setSkipCount(context.getSkipCount());
            result.setErrorCount(context.getErrorCount());
            result.setSuccess(context.getErrorCount() == 0);
            
            // 打印统计信息
            context.printStatistics();
            
            System.out.println("✅ 文件替换操作完成！");
            return result;
            
        } catch (Exception e) {
            System.err.println("❌ 文件替换操作失败: " + e.getMessage());
            e.printStackTrace();
            
            ProcessingResult result = new ProcessingResult();
            result.setErrorCount(1);
            result.setSuccess(false);
            return result;
        }
    }

    /**
     * 执行文件新增操作
     * @return 处理结果
     */
    public ProcessingResult executeAdd() {
        try {
            System.out.println("🔄 开始执行文件新增操作...");
            
            // 构建操作上下文
            Path basePath = config.getBaseDirectory();
            Path targetPath = basePath.resolve(config.getTargetPath());
            Path backupPath = basePath.resolve(config.getBackupPath());
            
            OperationContext context = new OperationContext(basePath, targetPath, backupPath, scanner);
            
            // 构建更新目录的文件树
            Path updatePath = basePath.resolve(config.getUpdatePath());
            System.out.println("📂 扫描更新目录: " + updatePath);
            
            FileNode updateTree = FileTreeBuilder.buildTree(updatePath);
            
            // 打印文件树结构（调试用）
            System.out.println("📄 文件树结构:");
            FileTreeBuilder.printTree(updateTree, 0);
            System.out.println();
            
            // 创建新增策略
            OperationStrategy strategy = StrategyFactory.createStrategy(StrategyFactory.OperationType.ADD);
            
            // 执行处理
            System.out.println("🔄 开始处理文件...");
            System.out.println("-----------------------------------------");
            updateTree.process(strategy, null, context); // 初始规则为null，由规则继承机制决定
            System.out.println("-----------------------------------------");
            
            // 生成处理结果
            ProcessingResult result = new ProcessingResult();
            result.setSuccessCount(context.getSuccessCount());
            result.setSkipCount(context.getSkipCount());
            result.setErrorCount(context.getErrorCount());
            result.setSuccess(context.getErrorCount() == 0);
            
            // 打印统计信息
            context.printStatistics();
            
            System.out.println("✅ 文件新增操作完成！");
            return result;
            
        } catch (Exception e) {
            System.err.println("❌ 文件新增操作失败: " + e.getMessage());
            e.printStackTrace();
            
            ProcessingResult result = new ProcessingResult();
            result.setErrorCount(1);
            result.setSuccess(false);
            return result;
        }
    }

    /**
     * 执行文件删除操作
     * @return 处理结果
     */
    public ProcessingResult executeDelete() {
        try {
            System.out.println("🔄 开始执行文件删除操作...");
            
            // 构建操作上下文
            Path basePath = config.getBaseDirectory();
            Path targetPath = basePath.resolve(config.getTargetPath());
            Path backupPath = basePath.resolve(config.getBackupPath());
            
            OperationContext context = new OperationContext(basePath, targetPath, backupPath, scanner);
            
            // 构建删除目录的文件树
            Path deletePath = basePath.resolve("delete"); // 删除操作使用单独的目录
            System.out.println("📂 扫描删除目录: " + deletePath);
            
            FileNode deleteTree = FileTreeBuilder.buildTree(deletePath);
            
            // 打印文件树结构（调试用）
            System.out.println("📄 文件树结构:");
            FileTreeBuilder.printTree(deleteTree, 0);
            System.out.println();
            
            // 创建删除策略
            OperationStrategy strategy = StrategyFactory.createStrategy(StrategyFactory.OperationType.DELETE);
            
            // 执行处理
            System.out.println("🔄 开始处理文件...");
            System.out.println("-----------------------------------------");
            deleteTree.process(strategy, null, context); // 初始规则为null，由规则继承机制决定
            System.out.println("-----------------------------------------");
            
            // 生成处理结果
            ProcessingResult result = new ProcessingResult();
            result.setSuccessCount(context.getSuccessCount());
            result.setSkipCount(context.getSkipCount());
            result.setErrorCount(context.getErrorCount());
            result.setSuccess(context.getErrorCount() == 0);
            
            // 打印统计信息
            context.printStatistics();
            
            System.out.println("✅ 文件删除操作完成！");
            return result;
            
        } catch (Exception e) {
            System.err.println("❌ 文件删除操作失败: " + e.getMessage());
            e.printStackTrace();
            
            ProcessingResult result = new ProcessingResult();
            result.setErrorCount(1);
            result.setSuccess(false);
            return result;
        }
    }

    /**
     * 主执行方法 - 根据配置执行相应的操作
     */
    public ProcessingResult execute() {
        // 尝试加载配置
        Config loadedConfig = ConfigLoader.loadConfig();
        if (loadedConfig != null) {
            this.config.setBaseDirectory(loadedConfig.getBaseDirectory());
            // 使用加载的配置更新当前配置
        }
        
        // 根据需求执行相应的操作
        // 这里可以根据配置决定执行哪种操作
        return executeReplace(); // 默认执行替换操作
    }
}