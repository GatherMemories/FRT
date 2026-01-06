package com.awei.frt.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件操作工具类
 */
public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    
    /**
     * 递归获取目录下所有文件
     */
    public static List<Path> getAllFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                  .forEach(files::add);
        }
        return files;
    }
    
    /**
     * 复制文件
     */
    public static void copyFile(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("文件复制完成: {} -> {}", source, target);
    }
    
    /**
     * 递归删除目录
     */
    public static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> stream = Files.walk(directory)) {
                stream.sorted((a, b) -> b.compareTo(a))
                      .forEach(path -> {
                          try {
                              Files.delete(path);
                          } catch (IOException e) {
                              logger.warn("删除文件失败: {}", path, e);
                          }
                      });
            }
            logger.debug("目录删除完成: {}", directory);
        }
    }
    
    /**
     * 创建目录（如果不存在）
     */
    public static void createDirectoryIfNotExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            logger.debug("目录创建完成: {}", directory);
        }
    }
    
    /**
     * 获取相对路径
     */
    public static String getRelativePath(Path base, Path path) {
        return base.relativize(path).toString().replace('\\', '/');
    }
    
    /**
     * 检查文件名是否匹配模式
     */
    public static boolean matchesPattern(String fileName, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        
        return patterns.stream().anyMatch(pattern -> {
            // 简单的 glob 模式匹配
            String regex = pattern.replace(".", "\\.")
                                 .replace("*", ".*")
                                 .replace("?", ".");
            return fileName.matches(regex);
        });
    }
}