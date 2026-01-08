package com.awei.frt.service;

import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;

/**
 * 恢复服务
 * 用于从备份中恢复文件
 */
public class RestoreService {
    
    private final Config config;
    private final Scanner scanner;

    public RestoreService(Config config, Scanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    /**
     * 执行恢复操作
     * @return 处理结果
     */
    public ProcessingResult executeRestore() {
        try {
            System.out.println("🔄 开始执行恢复操作...");
            
            Path basePath = config.getBaseDirectory();
            Path backupPath = basePath.resolve(config.getBackupPath());
            Path targetPath = basePath.resolve(config.getTargetPath());
            
            // 检查备份目录是否存在
            if (!Files.exists(backupPath)) {
                System.out.println("⚠️  备份目录不存在: " + backupPath);
                System.out.println("💡  无法执行恢复操作");
                return createErrorResult("备份目录不存在");
            }
            
            // 确认恢复操作
            System.out.printf("⚠️  确认从 %s 恢复到 %s ? (y/n): ", backupPath, targetPath);
            String input = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(input) && !"yes".equals(input)) {
                System.out.println("⏭️  用户取消恢复操作");
                return createSkippedResult();
            }
            
            // 执行恢复操作
            int restoredCount = restoreFromBackup(backupPath, targetPath);
            
            System.out.println("✅ 恢复操作完成！");
            System.out.printf("📋 恢复了 %d 个文件%n", restoredCount);
            
            ProcessingResult result = new ProcessingResult();
            result.setSuccessCount(restoredCount);
            result.setSuccess(true);
            return result;
            
        } catch (Exception e) {
            System.err.println("❌ 恢复操作失败: " + e.getMessage());
            e.printStackTrace();
            
            return createErrorResult(e.getMessage());
        }
    }
    
    /**
     * 从备份目录恢复文件到目标目录
     */
    private int restoreFromBackup(Path backupPath, Path targetPath) throws IOException {
        int restoredCount = 0;
        
        // 遍历备份目录中的所有文件
        try (var files = Files.list(backupPath)) {
            for (Path backupFile : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(backupFile)) {
                    // 计算目标文件路径
                    Path targetFile = targetPath.resolve(backupFile.getFileName());
                    
                    // 确保目标目录存在
                    if (targetFile.getParent() != null) {
                        Files.createDirectories(targetFile.getParent());
                    }
                    
                    // 恢复文件
                    Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    System.out.printf("✅ 恢复文件: %s -> %s%n", backupFile, targetFile);
                    restoredCount++;
                } else if (Files.isDirectory(backupFile)) {
                    // 递归恢复子目录
                    Path targetSubDir = targetPath.resolve(backupFile.getFileName());
                    restoredCount += restoreFromBackup(backupFile, targetSubDir);
                }
            }
        }
        
        return restoredCount;
    }
    
    /**
     * 创建错误结果
     */
    private ProcessingResult createErrorResult(String errorMessage) {
        ProcessingResult result = new ProcessingResult();
        result.setErrorCount(1);
        result.setSuccess(false);
        return result;
    }
    
    /**
     * 创建跳过结果
     */
    private ProcessingResult createSkippedResult() {
        ProcessingResult result = new ProcessingResult();
        result.setSuccess(true);
        return result;
    }
}