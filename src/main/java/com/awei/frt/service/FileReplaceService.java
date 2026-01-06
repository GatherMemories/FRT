package com.awei.frt.service;

import com.awei.frt.model.Config;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ReplaceRule;
import com.awei.frt.utils.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Scanner;

/**
 * 文件替换服务
 */
public class FileReplaceService {
    private static final Logger logger = LoggerFactory.getLogger(FileReplaceService.class);
    private final Config config;
    private final ObjectMapper objectMapper;
    private final Scanner scanner;
    
    public FileReplaceService(Config config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.scanner = new Scanner(System.in);
    }
    
    /**
     * 获取项目根目录
     */
    private Path getProjectRoot() {
        try {
            // 从当前类的位置向上找到项目根目录
            Path currentPath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            
            // 如果是 classes 目录，向上两级到项目根目录
            if (currentPath.toString().contains("classes")) {
                return currentPath.getParent().getParent();
            }
            
            // 如果是 target 目录，向上到项目根目录
            if (currentPath.toString().contains("target")) {
                return currentPath.getParent();
            }
            
            // 否则使用当前工作目录
            return Paths.get("").toAbsolutePath();
        } catch (Exception e) {
            logger.warn("无法确定项目根目录，使用当前目录", e);
            return Paths.get("").toAbsolutePath();
        }
    }
    
    /**
     * 执行文件替换操作
     */
    public void executeReplace() {
        try {
            Path projectRoot = getProjectRoot();
            Path updatePath = projectRoot.resolve(config.getUpdatePath());
            Path targetPath = projectRoot.resolve(config.getTargetPath());
            
            if (!Files.exists(updatePath)) {
                logger.warn("更新目录不存在: {}", updatePath);
                System.out.println("更新目录不存在: " + updatePath);
                return;
            }
            
            if (!Files.exists(targetPath)) {
                logger.warn("目标目录不存在: {}", targetPath);
                System.out.println("目标目录不存在: " + targetPath);
                return;
            }
            
            boolean shouldBackup = checkAndCreateBackup();
            if (!shouldBackup) {
                System.out.println("操作已取消");
                return;
            }
            
            List<Path> updateFiles = FileUtils.getAllFiles(updatePath);
            int processedCount = 0;
            
            for (Path updateFile : updateFiles) {
                // 跳过配置文件
                if (isConfigFile(updateFile.getFileName().toString())) {
                    logger.debug("跳过配置文件: {}", updateFile);
                    continue;
                }
                
                if (processReplaceFile(updateFile, updatePath, targetPath)) {
                    processedCount++;
                }
            }
            
            System.out.println("替换操作完成，共处理 " + processedCount + " 个文件");
            logger.info("替换操作完成，共处理 {} 个文件", processedCount);
            
        } catch (Exception e) {
            logger.error("替换操作失败", e);
            System.err.println("替换操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理单个替换文件
     */
    private boolean processReplaceFile(Path updateFile, Path updateBase, Path targetBase) throws IOException {
        String relativePath = FileUtils.getRelativePath(updateBase, updateFile);
        Path targetFile = targetBase.resolve(relativePath);
        
        // 检查是否有替换规则配置
        Path ruleFile = updateFile.getParent().resolve("replace.json");
        ReplaceRule rule = loadReplaceRule(ruleFile);
        
        if (rule != null && !FileUtils.matchesPattern(updateFile.getFileName().toString(), rule.getPatterns())) {
            logger.debug("文件不匹配替换规则，跳过: {}", updateFile);
            return false;
        }
        
        if (rule != null && FileUtils.matchesPattern(updateFile.getFileName().toString(), rule.getExcludePatterns())) {
            logger.debug("文件匹配排除规则，跳过: {}", updateFile);
            return false;
        }
        
        if (rule != null && rule.isConfirmBeforeReplace()) {
            System.out.printf("是否替换文件 %s -> %s ? (y/n): ", updateFile, targetFile);
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(confirm) && !"yes".equals(confirm)) {
                System.out.println("跳过文件: " + updateFile);
                return false;
            }
        }
        
        // 备份原文件
        if (rule != null && rule.isBackup() && Files.exists(targetFile)) {
            backupFile(targetFile);
        }
        
        // 执行替换
        FileUtils.copyFile(updateFile, targetFile);
        recordOperation(OperationRecord.OperationType.REPLACE, updateFile.toString(), targetFile.toString());
        
        System.out.println("已替换: " + relativePath);
        logger.info("文件替换成功: {} -> {}", updateFile, targetFile);
        return true;
    }
    
    /**
     * 执行文件删除操作
     */
    public void executeDelete() {
        try {
            Path projectRoot = getProjectRoot();
            Path deletePath = projectRoot.resolve(config.getDeletePath());
            Path targetPath = projectRoot.resolve(config.getTargetPath());
            
            if (!Files.exists(deletePath)) {
                logger.warn("删除目录不存在: {}", deletePath);
                System.out.println("删除目录不存在: " + deletePath);
                return;
            }
            
            if (!Files.exists(targetPath)) {
                logger.warn("目标目录不存在: {}", targetPath);
                System.out.println("目标目录不存在: " + targetPath);
                return;
            }
            
            boolean shouldBackup = checkAndCreateBackup();
            if (!shouldBackup) {
                System.out.println("操作已取消");
                return;
            }
            
            List<Path> deleteFiles = FileUtils.getAllFiles(deletePath);
            int processedCount = 0;
            
            for (Path deleteFile : deleteFiles) {
                // 跳过配置文件
                if (isConfigFile(deleteFile.getFileName().toString())) {
                    logger.debug("跳过配置文件: {}", deleteFile);
                    continue;
                }
                
                if (processDeleteFile(deleteFile, deletePath, targetPath)) {
                    processedCount++;
                }
            }
            
            System.out.println("删除操作完成，共处理 " + processedCount + " 个文件");
            logger.info("删除操作完成，共处理 {} 个文件", processedCount);
            
        } catch (Exception e) {
            logger.error("删除操作失败", e);
            System.err.println("删除操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理单个删除文件
     */
    private boolean processDeleteFile(Path deleteFile, Path deleteBase, Path targetBase) throws IOException {
        String relativePath = FileUtils.getRelativePath(deleteBase, deleteFile);
        Path targetFile = targetBase.resolve(relativePath);
        
        // 检查是否有删除规则配置
        Path ruleFile = deleteFile.getParent().resolve("delete.json");
        ReplaceRule rule = loadReplaceRule(ruleFile);
        
        if (rule != null && !FileUtils.matchesPattern(deleteFile.getFileName().toString(), rule.getPatterns())) {
            logger.debug("文件不匹配删除规则，跳过: {}", deleteFile);
            return false;
        }
        
        if (rule != null && FileUtils.matchesPattern(deleteFile.getFileName().toString(), rule.getExcludePatterns())) {
            logger.debug("文件匹配排除规则，跳过: {}", deleteFile);
            return false;
        }
        
        if (!Files.exists(targetFile)) {
            logger.debug("目标文件不存在，跳过: {}", targetFile);
            return false;
        }
        
        if (rule != null && rule.isConfirmBeforeReplace()) {
            System.out.printf("是否删除文件 %s ? (y/n): ", targetFile);
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(confirm) && !"yes".equals(confirm)) {
                System.out.println("跳过删除: " + targetFile);
                return false;
            }
        }
        
        // 备份原文件
        if (rule != null && rule.isBackup()) {
            backupFile(targetFile);
        }
        
        // 执行删除
        Files.delete(targetFile);
        recordOperation(OperationRecord.OperationType.DELETE, targetFile.toString(), null);
        
        System.out.println("已删除: " + relativePath);
        logger.info("文件删除成功: {}", targetFile);
        return true;
    }
    
    /**
     * 加载替换规则
     */
    private ReplaceRule loadReplaceRule(Path ruleFile) {
        if (!Files.exists(ruleFile)) {
            return null;
        }
        
        try {
            return objectMapper.readValue(ruleFile.toFile(), ReplaceRule.class);
        } catch (IOException e) {
            logger.warn("规则文件加载失败: {}", ruleFile, e);
            return null;
        }
    }
    
    /**
     * 检查并创建备份
     */
    private boolean checkAndCreateBackup() {
        Path backupPath = getProjectRoot().resolve(config.getBackupPath());
        
        // 检查备份目录是否为空
        boolean backupDirectoryHasFiles = false;
        if (Files.exists(backupPath)) {
            try (var stream = Files.list(backupPath)) {
                backupDirectoryHasFiles = stream.findAny().isPresent();
            } catch (IOException e) {
                logger.warn("检查备份目录失败，将重新创建: {}", backupPath, e);
            }
        }
        
        if (backupDirectoryHasFiles) {
            System.out.print("备份目录已存在，是否创建新的备份点? (y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(confirm) && !"yes".equals(confirm)) {
                return false;
            }
        }
        
        try {
            if (Files.exists(backupPath)) {
                FileUtils.deleteRecursively(backupPath);
            }
            FileUtils.createDirectoryIfNotExists(backupPath);
            return true;
        } catch (IOException e) {
            logger.error("创建备份目录失败", e);
            System.err.println("创建备份目录失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 备份文件
     */
    private void backupFile(Path targetFile) throws IOException {
        Path backupPath = getProjectRoot().resolve(config.getBackupPath());
        Path backupFile = backupPath.resolve(targetFile.getFileName());
        FileUtils.copyFile(targetFile, backupFile);
        logger.debug("文件备份完成: {} -> {}", targetFile, backupFile);
    }
    
    /**
     * 记录操作
     */
    private void recordOperation(OperationRecord.OperationType type, String sourcePath, String targetPath) {
        try {
            Path backupPath = getProjectRoot().resolve(config.getBackupPath());
            Path operationsFile = backupPath.resolve("operations.json");
            
            // TODO: 实现操作记录的持久化
            logger.debug("记录操作: type={}, source={}, target={}", type, sourcePath, targetPath);
            
        } catch (Exception e) {
            logger.warn("记录操作失败", e);
        }
    }
    
    /**
     * 检查是否为配置文件
     */
    private boolean isConfigFile(String fileName) {
        return "replace.json".equals(fileName) || 
               "delete.json".equals(fileName) || 
               "operations.json".equals(fileName);
    }
}