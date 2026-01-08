package com.awei.frt.core.context;

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
    private final Path basePath;
    private final Path targetBasePath;
    private final Path backupPath;
    private final Scanner scanner;

    private final List<String> records = new ArrayList<>();
    private int successCount = 0;
    private int skipCount = 0;
    private int errorCount = 0;
    
    private RuleInheritanceContext ruleInheritanceContext;
    private final ProcessingResult processingResult;

    public OperationContext(Path basePath, Path targetBasePath, Path backupPath, Scanner scanner) {
        this.basePath = basePath;
        this.targetBasePath = targetBasePath;
        this.backupPath = backupPath;
        this.scanner = scanner;
        this.ruleInheritanceContext = new RuleInheritanceContext(); // 初始化默认规则继承上下文
        this.processingResult = new ProcessingResult();
    }

    public Path getTargetPath(String relativePath) {
        return targetBasePath.resolve(relativePath).normalize();
    }

    public Path getBasePath() {
        return basePath;
    }

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

    public void skip(String relativePath, String reason) {
        skipCount++;
        System.out.printf("⏭️  跳过: %s (%s)%n", relativePath, reason);
    }

    public void recordError(String relativePath, Exception e) {
        errorCount++;
        System.err.printf("❌ 处理失败: %s (%s)%n",
            relativePath, e.getMessage());
    }

    public List<String> getRecords() {
        return records;
    }

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

    public String getStatistics() {
        return String.format("成功: %d, 跳过: %d, 失败: %d",
            successCount, skipCount, errorCount);
    }

    public RuleInheritanceContext getRuleInheritanceContext() {
        return ruleInheritanceContext;
    }

    public void setRuleInheritanceContext(RuleInheritanceContext ruleInheritanceContext) {
        this.ruleInheritanceContext = ruleInheritanceContext;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public ProcessingResult getResult() {
        processingResult.setSuccessCount(successCount);
        processingResult.setSkipCount(skipCount);
        processingResult.setErrorCount(errorCount);
        processingResult.setSuccess(errorCount == 0);
        return processingResult;
    }

    public Path getRelativePath(Path path) {
        try {
            return basePath.relativize(path);
        } catch (Exception e) {
            // 如果无法相对化，则返回原始路径
            return path;
        }
    }
}