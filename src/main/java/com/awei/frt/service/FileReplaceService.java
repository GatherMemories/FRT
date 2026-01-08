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
import java.util.ArrayList;
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
     * 获取基准目录（用于路径解析）
     * 优先使用配置中设置的基准目录，否则使用FRT项目根目录
     */
    private Path getBaseDirectory() {
        if (config.getBaseDirectory() != null) {
            return config.getBaseDirectory();
        }
        // 如果没有设置基准目录，使用FRT项目根目录
        return Paths.get("").toAbsolutePath().getParent();
    }
    
    /**
     * 格式化路径显示，去除冗余的 .. 和多余的分隔符
     */
    private String formatPath(Path path) {
        return path.normalize().toString();
    }
    
    /**
     * 获取友好的路径描述
     */
    private String getFriendlyPath(Path path, String description) {
        String formattedPath = formatPath(path);
        return description + ": " + formattedPath;
    }
    
    /**
     * 执行文件替换操作
     */
    public void executeReplace() {
        try {
            Path baseDirectory = getBaseDirectory();
            Path updatePath = baseDirectory.resolve(config.getUpdatePath()).normalize();
            Path targetPath = baseDirectory.resolve(config.getTargetPath()).normalize();
            
            System.out.println("\n=========================================");
            System.out.println("🔄 开始执行文件替换操作");
            System.out.println("=========================================");
            System.out.println(getFriendlyPath(baseDirectory, "📁 基准目录"));
            System.out.println(getFriendlyPath(updatePath, "📂 更新目录"));
            System.out.println(getFriendlyPath(targetPath, "🎯 目标目录"));
            System.out.println("=========================================");
            
            if (!Files.exists(updatePath)) {
                String errorMsg = "❌ 更新目录不存在: " + formatPath(updatePath);
                logger.warn("更新目录不存在: {}", updatePath);
                System.out.println(errorMsg);
                return;
            }
            
            if (!Files.exists(targetPath)) {
                String errorMsg = "❌ 目标目录不存在: " + formatPath(targetPath);
                logger.warn("目标目录不存在: {}", targetPath);
                System.out.println(errorMsg);
                return;
            }
            
            System.out.println("✅ 目录检查通过");
            System.out.println();
            
            System.out.println("📦 步骤 1/3: 创建备份...");
            boolean shouldBackup = checkAndCreateBackup();
            if (!shouldBackup) {
                System.out.println("❌ 操作已取消");
                return;
            }
            System.out.println("✅ 备份创建完成");
            System.out.println();
            
            System.out.println("📂 步骤 2/3: 扫描更新文件...");
            List<Path> updateFiles = FileUtils.getAllFiles(updatePath);
            System.out.println("📄 发现 " + updateFiles.size() + " 个文件");
            
            int processedCount = 0;
            int skippedCount = 0;
            
            System.out.println();
            System.out.println("🔄 步骤 3/3: 执行文件替换...");
            System.out.println("-----------------------------------------");
            
            for (Path updateFile : updateFiles) {
                // 跳过配置文件
                if (isConfigFile(updateFile.getFileName().toString())) {
                    logger.debug("跳过配置文件: {}", updateFile);
                    skippedCount++;
                    System.out.println("⏭️  跳过配置文件: " + updateFile.getFileName());
                    continue;
                }
                
                if (processReplaceFile(updateFile, updatePath, targetPath)) {
                    processedCount++;
                    System.out.println("✅ 处理完成: " + updateFile.getFileName());
                } else {
                    System.out.println("❌ 处理失败: " + updateFile.getFileName());
                }
            }
            
            System.out.println("-----------------------------------------");
            System.out.println("🎉 替换操作完成！");
            System.out.println("📊 处理统计:");
            System.out.println("   ✅ 成功处理: " + processedCount + " 个文件");
            if (skippedCount > 0) {
                System.out.println("   ⏭️  跳过文件: " + skippedCount + " 个文件");
            }
            logger.info("替换操作完成，共处理 {} 个文件，跳过 {} 个文件", processedCount, skippedCount);
            
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
        Path targetFile = targetBase.resolve(relativePath).normalize();
        
        // 检查是否有替换规则配置 - 必须有配置文件才能执行操作
        Path ruleFile = updateFile.getParent().resolve("replace.json");
        ReplaceRule rule = loadReplaceRule(ruleFile);
        
        if (rule == null) {
            // 没有配置文件，跳过该文件
            logger.debug("当前层没有 replace.json 配置文件，跳过: {}", updateFile);
            return false;
        }
        
        // 检查文件是否匹配规则
        if (!FileUtils.matchesPattern(updateFile.getFileName().toString(), rule.getPatterns())) {
            logger.debug("文件不匹配替换规则，跳过: {}", updateFile);
            return false;
        }
        
        // 检查文件是否在排除列表中
        if (FileUtils.matchesPattern(updateFile.getFileName().toString(), rule.getExcludePatterns())) {
            logger.debug("文件匹配排除规则，跳过: {}", updateFile);
            return false;
        }
        
        // 如果需要确认，提示用户
        if (rule.isConfirmBeforeReplace()) {
            System.out.printf("⚠️  确认操作 %s -> %s ? (y/n): ", 
                formatPath(updateFile), formatPath(targetFile));
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(confirm) && !"yes".equals(confirm)) {
                System.out.println("⏭️  跳过文件: " + updateFile.getFileName());
                return false;
            }
        }
        
        // 判断是新增还是替换
        boolean targetExists = Files.exists(targetFile);
        
        // 如果是替换操作，需要备份
        if (targetExists && rule.isBackup()) {
            backupFile(targetFile);
        }
        
        // 执行文件操作
        FileUtils.copyFile(updateFile, targetFile);
        
        // 根据操作类型记录
        if (targetExists) {
            recordOperation("REPLACE", 
                formatPath(updateFile), formatPath(targetFile));
            logger.debug("文件替换成功: {} -> {}", formatPath(updateFile), formatPath(targetFile));
        } else {
            recordOperation("ADD", 
                formatPath(updateFile), formatPath(targetFile));
            logger.debug("文件新增成功: {} -> {}", formatPath(updateFile), formatPath(targetFile));
        }
        
        return true;
    }
    
    /**
     * 执行文件删除操作
     */
    public void executeDelete() {
        try {
            Path baseDirectory = getBaseDirectory();
            Path deletePath = baseDirectory.resolve(config.getDeletePath()).normalize();
            Path targetPath = baseDirectory.resolve(config.getTargetPath()).normalize();
            
            System.out.println("\n=========================================");
            System.out.println("🗑️  开始执行文件删除操作");
            System.out.println("=========================================");
            System.out.println(getFriendlyPath(baseDirectory, "📁 基准目录"));
            System.out.println(getFriendlyPath(deletePath, "📂 删除配置目录"));
            System.out.println(getFriendlyPath(targetPath, "🎯 目标删除目录"));
            System.out.println("=========================================");
            
            if (!Files.exists(deletePath)) {
                String errorMsg = "❌ 删除目录不存在: " + formatPath(deletePath);
                logger.warn("删除目录不存在: {}", deletePath);
                System.out.println(errorMsg);
                return;
            }
            
            if (!Files.exists(targetPath)) {
                String errorMsg = "❌ 目标目录不存在: " + formatPath(targetPath);
                logger.warn("目标目录不存在: {}", targetPath);
                System.out.println(errorMsg);
                return;
            }
            
            System.out.println("✅ 目录检查通过");
            System.out.println();
            
            System.out.println("📦 步骤 1/3: 创建备份...");
            boolean shouldBackup = checkAndCreateBackup();
            if (!shouldBackup) {
                System.out.println("❌ 操作已取消");
                return;
            }
            System.out.println("✅ 备份创建完成");
            System.out.println();
            
            System.out.println("📂 步骤 2/3: 扫描删除文件...");
            List<Path> deleteFiles = FileUtils.getAllFiles(deletePath);
            System.out.println("📄 发现 " + deleteFiles.size() + " 个删除规则");
            
            int processedCount = 0;
            int skippedCount = 0;
            
            System.out.println();
            System.out.println("🔄 步骤 3/3: 执行文件删除...");
            System.out.println("-----------------------------------------");
            
            for (Path deleteFile : deleteFiles) {
                // 跳过配置文件
                if (isConfigFile(deleteFile.getFileName().toString())) {
                    logger.debug("跳过配置文件: {}", deleteFile);
                    skippedCount++;
                    System.out.println("⏭️  跳过配置文件: " + deleteFile.getFileName());
                    continue;
                }
                
                if (processDeleteFile(deleteFile, deletePath, targetPath)) {
                    processedCount++;
                    System.out.println("✅ 删除完成: " + deleteFile.getFileName());
                } else {
                    System.out.println("❌ 删除失败: " + deleteFile.getFileName());
                }
            }
            
            System.out.println("-----------------------------------------");
            System.out.println("🎉 删除操作完成！");
            System.out.println("📊 处理统计:");
            System.out.println("   ✅ 成功删除: " + processedCount + " 个文件");
            if (skippedCount > 0) {
                System.out.println("   ⏭️  跳过文件: " + skippedCount + " 个文件");
            }
            logger.info("删除操作完成，共处理 {} 个文件，跳过 {} 个文件", processedCount, skippedCount);
            
        } catch (Exception e) {
            logger.error("删除操作失败", e);
            System.err.println("❌ 删除操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理单个删除文件
     */
    private boolean processDeleteFile(Path deleteFile, Path deleteBase, Path targetBase) throws IOException {
        String relativePath = FileUtils.getRelativePath(deleteBase, deleteFile);
        Path targetFile = targetBase.resolve(relativePath).normalize();
        
        // 检查是否有删除规则配置 - 必须有配置文件才能执行操作
        Path ruleFile = deleteFile.getParent().resolve("delete.json");
        ReplaceRule rule = loadReplaceRule(ruleFile);
        
        if (rule == null) {
            // 没有配置文件，跳过该文件
            logger.debug("当前层没有 delete.json 配置文件，跳过: {}", deleteFile);
            return false;
        }
        
        // 检查文件是否匹配删除规则
        if (!FileUtils.matchesPattern(deleteFile.getFileName().toString(), rule.getPatterns())) {
            logger.debug("文件不匹配删除规则，跳过: {}", deleteFile);
            return false;
        }
        
        // 检查文件是否在排除列表中
        if (FileUtils.matchesPattern(deleteFile.getFileName().toString(), rule.getExcludePatterns())) {
            logger.debug("文件匹配排除规则，跳过: {}", deleteFile);
            return false;
        }
        
        if (!Files.exists(targetFile)) {
            logger.debug("目标文件不存在，跳过: {}", formatPath(targetFile));
            return false;
        }
        
        // 如果需要确认，提示用户
        if (rule.isConfirmBeforeReplace()) {
            System.out.printf("⚠️  确认删除文件 %s ? (y/n): ", formatPath(targetFile));
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(confirm) && !"yes".equals(confirm)) {
                System.out.println("⏭️  跳过删除: " + targetFile.getFileName());
                return false;
            }
        }
        
        // 备份原文件
        if (rule.isBackup()) {
            backupFile(targetFile);
        }
        
        // 执行删除
        Files.delete(targetFile);
        recordOperation("DELETE", formatPath(targetFile), null);
        
        logger.debug("文件删除成功: {}", formatPath(targetFile));
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
        Path backupPath = getBaseDirectory().resolve(config.getBackupPath()).normalize();
        
        System.out.println(getFriendlyPath(backupPath, "📁 备份目录"));
        
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
            System.out.print("⚠️  备份目录已存在文件，是否清空并创建新备份? (y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(confirm) && !"yes".equals(confirm)) {
                return false;
            }
        }
        
        try {
            if (Files.exists(backupPath)) {
                FileUtils.deleteRecursively(backupPath);
                System.out.println("🗑️  已清空旧备份目录");
            }
            FileUtils.createDirectoryIfNotExists(backupPath);
            System.out.println("✅ 备份目录准备就绪");
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
        Path backupPath = getBaseDirectory().resolve(config.getBackupPath()).normalize();
        Path backupFile = backupPath.resolve(targetFile.getFileName()).normalize();
        FileUtils.copyFile(targetFile, backupFile);
        logger.debug("文件备份完成: {} -> {}", formatPath(targetFile), formatPath(backupFile));
    }
    
    /**
     * 记录操作
     */
    private void recordOperation(String type, String sourcePath, String targetPath) {
        try {
            Path backupPath = getBaseDirectory().resolve(config.getBackupPath());
            Path operationsFile = backupPath.resolve("operations.json");
            
            // 创建操作记录
            OperationRecord record = new OperationRecord(type, sourcePath, targetPath, true, null);
            
            // 读取现有的操作记录（如果存在）
            List<OperationRecord> records = new ArrayList<>();
            if (Files.exists(operationsFile)) {
                String jsonContent = Files.readString(operationsFile);
                if (!jsonContent.trim().isEmpty()) {
                    records = objectMapper.readValue(jsonContent, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, OperationRecord.class));
                }
            }
            
            // 添加新的记录
            records.add(record);
            
            // 写入文件
            objectMapper.writeValue(operationsFile.toFile(), records);
            
            logger.debug("操作记录已保存: type={}, source={}, target={}", type, sourcePath, targetPath);
            
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