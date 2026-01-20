package com.awei.frt.core.context;

import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 操作上下文
 * 管理操作的状态和执行结果
 */
public class OperationContext {
    private final Config config;
    private final Path basePath;              // 基准路径，用于计算相对路径
    private final Path targetBasePath;        // 目标基准路径，文件操作的目标目录
    private final Path backupPath;            // 备份路径，用于存储备份文件
    private final Scanner scanner;            // 输入扫描器，用于用户交互确认

    private final List<String> records = new ArrayList<>(); // 操作记录列表
    private int successCount = 0;             // 成功操作计数
    private int skipCount = 0;                // 跳过操作计数
    private int errorCount = 0;               // 错误操作计数

    private RuleInheritanceContext ruleInheritanceContext; // 规则继承上下文，管理规则继承关系
    private final ProcessingResult processingResult;       // 处理结果对象，汇总处理结果

    // 操作类型（用于 ProcessingResult-->OperationRecord-->operationType）
    public static final String OPERATION_RENAME = "operation_rename";
    public static final String OPERATION_ADD = "operation_add";
    public static final String OPERATION_REPLACE = "operation_replace";
    public static final String OPERATION_DELETE = "operation_delete";

    /**
     * 构造函数，初始化操作上下文
     * @param config 配置对象
     * @param scanner 输入扫描器
     */
    public OperationContext(Config config, Scanner scanner) {
        this.config = config;
        this.scanner = scanner;
        this.basePath = config.getBaseDirectory();
        this.targetBasePath = basePath.resolve(config.getTargetPath());
        this.backupPath = basePath.resolve(config.getBackupPath());
        this.ruleInheritanceContext = new RuleInheritanceContext(); // 初始化默认规则继承上下文
        this.processingResult = new ProcessingResult();
    }

    /**
     * 获取配置对象
     */
    public Config getConfig() {
        return config;
    }

    /**
     * 获取目标路径
     * @param relativePath 相对路径
     * @return 标准化的目标路径
     */
    public Path getTargetPath(String relativePath) {
        return targetBasePath.resolve(relativePath).normalize();
    }

    /**
     * 获取基准路径
     * @return 基准路径
     */
    public Path getBasePath() {
        return basePath;
    }

    /**
     * 备份指定路径的文件
     * @param targetPath 目标路径
     */
    public void backup(Path targetPath) {
        try {
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
            }

            // 创建备份文件名（添加时间戳或序列号以避免冲突）
            String fileName = targetPath.getFileName().toString();
            Path backupFile = backupPath.resolve(fileName).normalize();

            // 如果备份文件已存在，添加序号
            int counter = 1;
            while (Files.exists(backupFile)) {
                String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                String ext = fileName.substring(fileName.lastIndexOf('.'));
                backupFile = backupPath.resolve(nameWithoutExt + "_" + counter + ext).normalize();
                counter++;
            }

            Files.copy(targetPath, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("❌ 备份失败: " + targetPath + " - " + e.getMessage());
        }
    }

    /**
     * 确认操作
     * @param operation 操作类型
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     * @return 用户确认结果
     */
    public boolean confirm(String operation, Path sourcePath, Path targetPath) {
        if (targetPath != null) {
            System.out.printf("⚠️  确认 %s: %s -> %s ? (y/n): ",
                operation, sourcePath, targetPath);
        } else {
            System.out.printf("⚠️  确认 %s: %s ? (y/n): ",
                operation, sourcePath);
        }

        String input = scanner.nextLine().trim().toLowerCase();
        return "y".equals(input) || "yes".equals(input);
    }

    /**
     * 记录成功操作
     * @param type 操作类型
     * @param source 源路径
     * @param target 目标路径
     */
    public void recordSuccess(String type, Path source, Path target) {
        records.add(type + ": " + source + " -> " + target);
        successCount++;

        if (source != null && target != null) {
            System.out.printf("✅ %s成功: %s -> %s%n", type, source, target);
        } else if (target != null) {
            System.out.printf("✅ %s成功: %s%n", type, target);
        } else if (source != null) {
            System.out.printf("✅ %s成功: %s%n", type, source);
        }
    }

    /**
     * 记录跳过的操作
     * @param relativePath 相对路径
     * @param reason 跳过原因
     */
    public void skip(String relativePath, String reason) {
        skipCount++;
        System.out.printf("⏭️  跳过: %s (%s)%n", relativePath, reason);
    }

    /**
     * 记录错误操作
     * @param relativePath 相对路径
     * @param e 异常对象
     */
    public void recordError(String relativePath, Exception e) {
        errorCount++;
        System.err.printf("❌ 处理失败: %s (%s)%n",
            relativePath, e.getMessage());
    }

    /**
     * 获取操作记录列表
     * @return 操作记录列表
     */
    public List<String> getRecords() {
        return records;
    }

    /**
     * 打印处理统计信息
     */
    public void printStatistics() {
        System.out.println("-----------------------------------------");
        System.out.println("📊 处理统计:");
        System.out.println("   ✅ 成功处理: " + successCount + " 个文件");
        if (skipCount > 0) {
            System.out.println("   ⏭️  跳过文件: " + skipCount + " 个文件");
        }
        if (errorCount > 0) {
            System.out.println("   ❌ 处理失败: " + errorCount + " 个文件");
        }
        System.out.println("-----------------------------------------");
    }

    /**
     * 获取处理统计信息字符串
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return String.format("成功: %d, 跳过: %d, 失败: %d",
            successCount, skipCount, errorCount);
    }

    /**
     * 获取规则继承上下文
     * @return 规则继承上下文
     */
    public RuleInheritanceContext getRuleInheritanceContext() {
        return ruleInheritanceContext;
    }

    /**
     * 设置规则继承上下文
     * @param ruleInheritanceContext 规则继承上下文
     */
    public void setRuleInheritanceContext(RuleInheritanceContext ruleInheritanceContext) {
        this.ruleInheritanceContext = ruleInheritanceContext;
    }

    /**
     * 获取成功操作计数
     * @return 成功操作计数
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * 获取跳过操作计数
     * @return 跳过操作计数
     */
    public int getSkipCount() {
        return skipCount;
    }

    /**
     * 获取错误操作计数
     * @return 错误操作计数
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * 获取处理结果对象
     * @return 处理结果对象
     */
    public ProcessingResult getProcessingResult() {
        return processingResult;
    }

    /**
     * 获取相对路径
     * @param path 路径
     * @return 相对路径
     */
    public Path getRelativePath(Path path) {
        try {
            return basePath.relativize(path).normalize();
        } catch (Exception e) {
            // 如果无法相对化，则返回原始路径
            return path;
        }
    }
}
