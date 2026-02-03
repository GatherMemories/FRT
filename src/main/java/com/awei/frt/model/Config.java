package com.awei.frt.model;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置模型
 * 存储系统运行所需的基本配置信息
 */
public class Config implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient Path baseDirectory;      // 基准目录（固定项目所在目录）
    private transient Path updatePath;         // 更新文件目录（默认：update）
    private Path targetPath;         // 目标目录（默认：THtest）
    private transient Path deletePath;         // 删除文件目录（默认：delete）
    private transient Path backupPath;         // 备份目录（默认：old）
    private transient Path logPath;            // 日志目录（默认：logs）
    private String logLevel;         // 日志级别（默认：INFO）

    public Config() {
        this.baseDirectory = Path.of(".").normalize().toAbsolutePath().getParent();
        this.updatePath = Path.of("update");
        this.targetPath = Path.of("THtest");
        this.deletePath = Path.of("delete");
        this.backupPath = Path.of("backup");
        this.logPath = Path.of("logs");
        this.logLevel = "INFO";

        // 默认创建目录
        try {
            Files.createDirectories(baseDirectory.resolve(updatePath));
            Files.createDirectories(baseDirectory.resolve(targetPath));
            Files.createDirectories(baseDirectory.resolve(deletePath));
            Files.createDirectories(baseDirectory.resolve(backupPath));
            Files.createDirectories(baseDirectory.resolve(logPath));
        } catch (IOException e) {
            e.printStackTrace();
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
