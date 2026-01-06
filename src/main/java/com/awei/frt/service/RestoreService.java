package com.awei.frt.service;

import com.awei.frt.model.Config;
import com.awei.frt.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 恢复服务
 */
public class RestoreService {
    private static final Logger logger = LoggerFactory.getLogger(RestoreService.class);
    private final Config config;
    
    public RestoreService(Config config) {
        this.config = config;
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
     * 执行恢复操作
     */
    public void executeRestore() {
        try {
            Path projectRoot = getProjectRoot();
            Path backupPath = projectRoot.resolve(config.getBackupPath());
            Path targetPath = projectRoot.resolve(config.getTargetPath());
            
            if (!Files.exists(backupPath)) {
                logger.warn("备份目录不存在: {}", backupPath);
                System.out.println("备份目录不存在: " + backupPath);
                return;
            }
            
            if (!Files.exists(targetPath)) {
                logger.warn("目标目录不存在: {}", targetPath);
                System.out.println("目标目录不存在: " + targetPath);
                return;
            }
            
            System.out.println("正在恢复备份...");
            System.out.println("备份目录: " + backupPath);
            System.out.println("目标目录: " + targetPath);
            
            // TODO: 实现完整的恢复逻辑
            System.out.println("恢复功能待实现");
            logger.info("恢复操作完成");
            
        } catch (Exception e) {
            logger.error("恢复操作失败", e);
            System.err.println("恢复操作失败: " + e.getMessage());
        }
    }
}