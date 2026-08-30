package com.awei.frt.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置模型
 * 存储系统运行所需的基本配置信息
 */
@JsonIgnoreProperties({"baseDirectory"})
public class Config implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient Path baseDirectory;      // 基准目录（固定项目所在目录，绝对路径）
    private Path updatePath;         // 更新文件目录（相对路径，默认：update）
    private Path targetPath;         // 目标目录（相对路径，默认：THtest）
    private Path deletePath;         // 删除文件目录（相对路径，默认：delete）
    private Path backupPath;         // 备份目录（相对路径，默认：backup）
    private String logLevel;         // 日志级别（默认：INFO）
    private int maxBackupRecords = 20; // 备份记录保留上限（超出自动淘汰最旧，固定 pinned 除外；默认 20）
    private int logFontSize = 13;    // 日志区字体大小（UI 顶部 A-/A+ 按钮调整，持久化到 config.json；默认 13）
    private String theme = "light";  // UI 主题（light/dark，视图菜单切换，持久化到 config.json；默认浅色）
    private boolean autoCheckUpdate = true; // 启动时自动检查更新开关（帮助菜单切换，持久化到 config.json；默认开启）

    /** 主题取值 */
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    /** 日志区字体大小可调范围（UI A-/A+ 按钮） */
    public static final int MIN_LOG_FONT_SIZE = 10;
    public static final int MAX_LOG_FONT_SIZE = 24;

    public Config() {
        this.baseDirectory = Path.of(".").normalize().toAbsolutePath();
        this.updatePath = Path.of("update");
        this.targetPath = Path.of("THtest");
        this.deletePath = Path.of("delete");
        this.backupPath = Path.of("backup");
        this.logLevel = "INFO";
        this.maxBackupRecords = 20;
        this.logFontSize = 13;
    }

    /**
     * 判断路径是否为绝对路径
     * @param path 要判断的路径
     * @return true-绝对路径, false-相对路径
     */
    public static boolean isAbsolutePath(Path path) {
        if (path == null) {
            return false;
        }
        return path.isAbsolute();
    }

    /**
     * 判断路径字符串是否为绝对路径
     * @param pathString 路径字符串
     * @return true-绝对路径, false-相对路径
     */
    public static boolean isAbsolutePath(String pathString) {
        if (pathString == null || pathString.trim().isEmpty()) {
            return false;
        }
        try {
            Path path = Paths.get(pathString);
            return path.isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将绝对路径转换为相对于基准目录的相对路径
     * @param absolutePath 绝对路径
     * @param baseDir 基准目录
     * @return 相对路径
     */
    public static Path toRelativePath(Path absolutePath, Path baseDir) {
        if (absolutePath == null || baseDir == null) {
            return null;
        }

        if (!isAbsolutePath(absolutePath)) {
            return absolutePath; // 已经是相对路径
        }

        try {
            return baseDir.relativize(absolutePath);
        } catch (Exception e) {
            return absolutePath.getFileName(); // 如果转换失败，返回文件名
        }
    }

    /**
     * 将路径字符串转换为相对于基准目录的相对路径
     * @param pathString 路径字符串
     * @param baseDir 基准目录
     * @return 相对路径
     */
    public static Path toRelativePath(String pathString, Path baseDir) {
        if (pathString == null || pathString.trim().isEmpty() || baseDir == null) {
            return null;
        }

        try {
            Path path = Paths.get(pathString);
            return toRelativePath(path, baseDir);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将相对路径转换为基于基准目录的绝对路径
     * @param relativePath 相对路径
     * @param baseDir 基准目录
     * @return 绝对路径
     */
    public static Path resolveRelativePath(Path relativePath, Path baseDir) {
        if (relativePath == null || baseDir == null) {
            return null;
        }

        if (isAbsolutePath(relativePath)) {
            return relativePath.normalize();
        }

        return baseDir.resolve(relativePath).normalize();
    }

    /**
     * 将相对路径字符串转换为基于基准目录的绝对路径
     * @param pathString 路径字符串
     * @param baseDir 基准目录
     * @return 绝对路径
     */
    public static Path resolveRelativePath(String pathString, Path baseDir) {
        if (pathString == null || pathString.trim().isEmpty() || baseDir == null) {
            return null;
        }

        try {
            Path path = Paths.get(pathString);
            return resolveRelativePath(path, baseDir);
        } catch (Exception e) {
            return null;
        }
    }

    // Getter 和 Setter 方法
    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public Path getUpdatePath() {
        return updatePath;
    }

    public void setUpdatePath(Path updatePath) {
        this.updatePath = updatePath;
    }

    public Path getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(Path targetPath) {
        this.targetPath = targetPath;
    }

    public Path getDeletePath() {
        return deletePath;
    }

    public void setDeletePath(Path deletePath) {
        this.deletePath = deletePath;
    }

    public Path getBackupPath() {
        return backupPath;
    }

    public void setBackupPath(Path backupPath) {
        this.backupPath = backupPath;
    }

    public String getLogLevel() {
        return logLevel;
    }

    /**
     * 备份记录保留上限：超出自动淘汰最旧（固定 pinned 除外），默认 20
     */
    public int getMaxBackupRecords() {
        return maxBackupRecords;
    }

    public void setMaxBackupRecords(int maxBackupRecords) {
        this.maxBackupRecords = maxBackupRecords > 0 ? maxBackupRecords : 20;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * 日志区字体大小（默认 13），返回已限制在可调范围内（10~24）的值
     */
    public int getLogFontSize() {
        return logFontSize;
    }

    public void setLogFontSize(int logFontSize) {
        this.logFontSize = Math.max(MIN_LOG_FONT_SIZE, Math.min(MAX_LOG_FONT_SIZE, logFontSize));
    }

    /** UI 主题（light/dark），未知值回退浅色 */
    public String getTheme() {
        return THEME_DARK.equals(theme) ? THEME_DARK : THEME_LIGHT;
    }

    public void setTheme(String theme) {
        this.theme = THEME_DARK.equals(theme) ? THEME_DARK : THEME_LIGHT;
    }

    /** 启动时自动检查更新开关（默认 true；关闭后手动「帮助 → 检查更新」仍可用） */
    public boolean isAutoCheckUpdate() {
        return autoCheckUpdate;
    }

    public void setAutoCheckUpdate(boolean autoCheckUpdate) {
        this.autoCheckUpdate = autoCheckUpdate;
    }

    @Override
    public String toString() {
        return "Config{" +
                "baseDirectory=" + baseDirectory +
                ", updatePath=" + updatePath +
                ", targetPath=" + targetPath +
                ", backupPath=" + backupPath +
                ", logLevel='" + logLevel + '\'' +
                '}';
    }

}
