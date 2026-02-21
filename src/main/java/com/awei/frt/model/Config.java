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
    private Path logPath;            // 日志目录（相对路径，默认：logs）
    private String logLevel;         // 日志级别（默认：INFO）

    public Config() {
        this.baseDirectory = Path.of(".").normalize().toAbsolutePath();
        this.updatePath = Path.of("update");
        this.targetPath = Path.of("THtest");
        this.deletePath = Path.of("delete");
        this.backupPath = Path.of("backup");
        this.logPath = Path.of("logs");
        this.logLevel = "INFO";
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

    public Path getLogPath() {
        return logPath;
    }

    public void setLogPath(Path logPath) {
        this.logPath = logPath;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public String toString() {
        return "Config{" +
                "baseDirectory=" + baseDirectory +
                ", updatePath=" + updatePath +
                ", targetPath=" + targetPath +
                ", backupPath=" + backupPath +
                ", logPath=" + logPath +
                ", logLevel='" + logLevel + '\'' +
                '}';
    }

}
