package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.ui.ConsoleUserPrompter;
import com.awei.frt.ui.UserPrompter;
import com.awei.frt.util.LoggerUtil;

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
     * @param
     * @return 处理结果
     */
    public ProcessingResult updateExecute() {
        try {
            LoggerUtil.logInfo("[执行] 开始执行文件更新操作...");

            // 构建操作上下文
            Path basePath = config.getBaseDirectory();

            OperationContext context = new OperationContext(config);

            // 构建更新目录的文件树
            Path updatePath = basePath.resolve(config.getUpdatePath()).normalize();
            LoggerUtil.logInfo("[FOLDER] 扫描更新目录: " + updatePath);

            FileNode updateTree = FileTreeBuilder.buildTree(updatePath);
            // 打印文件树结构（调试用，仅控制台）
            System.out.println("[FILE] 文件树结构:");
            FileTreeBuilder.printTree(updateTree, 0);
            System.out.println();

            // 执行处理
            LoggerUtil.logInfo("[执行] 正在处理update文件夹...");
            System.out.println("-----------------------------------------");
            updateTree.process(null, context, updateTree.UPDATE_OPERATION);
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
