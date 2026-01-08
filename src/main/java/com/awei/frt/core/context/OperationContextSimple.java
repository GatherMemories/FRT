package com.awei.frt.core.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 操作上下文（简化版 - 不依赖 SLF4J）
 * 管理操作的状态和执行结果
 */
public class OperationContextSimple {
    private final Path basePath;
    private final Path targetBasePath;
    private final Path backupPath;
    private final Scanner scanner;

    private final List<String> records = new ArrayList<>();
    private int successCount = 0;
    private int skipCount = 0;
    private int errorCount = 0;

    public OperationContextSimple(Path basePath, Path targetBasePath, Path backupPath, Scanner scanner) {
        this.basePath = basePath;
        this.targetBasePath = targetBasePath;
        this.backupPath = backupPath;
        this.scanner = scanner;
    }

    public Path getTargetPath(String relativePath) {
        return targetBasePath.resolve(relativePath).normalize();
    }

    public void backup(Path targetPath) {
        try {
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
            }

            Path backupFile = backupPath.resolve(targetPath.getFileName()).normalize();
            Files.copy(targetPath, backupFile, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("📦 备份完成: " + targetPath);
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

    public Path getBasePath() {
        return basePath;
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
