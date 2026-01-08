package com.awei.frt.model;

import java.nio.file.Path;

/**
 * 配置模型
 * 存储系统运行所需的基本配置信息
 */
public class Config {
    private Path baseDirectory;      // 基准目录
    private Path updatePath;         // 更新文件目录（默认：update）
    private Path targetPath;         // 目标目录（默认：THtest）
    private Path deletePath;         // 删除文件目录（默认：delete）
    private Path backupPath;         // 备份目录（默认：old）
    private Path logPath;            // 日志目录（默认：logs）
    private boolean confirmBeforeReplace; // 是否在替换前确认（默认：true）
    private boolean createBackup;    // 是否创建备份（默认：true）
    private String logLevel;         // 日志级别（默认：INFO）

    public Config() {
        this.updatePath = Path.of("update");
        this.targetPath = Path.of("THtest");
        this.deletePath = Path.of("delete");
        this.backupPath = Path.of("old");
        this.logPath = Path.of("logs");
        this.confirmBeforeReplace = true;
        this.createBackup = true;
        this.logLevel = "INFO";
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

    public boolean isConfirmBeforeReplace() {
        return confirmBeforeReplace;
    }

    public void setConfirmBeforeReplace(boolean confirmBeforeReplace) {
        this.confirmBeforeReplace = confirmBeforeReplace;
    }

    public boolean isCreateBackup() {
        return createBackup;
    }

    public void setCreateBackup(boolean createBackup) {
        this.createBackup = createBackup;
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
                ", confirmBeforeReplace=" + confirmBeforeReplace +
                ", createBackup=" + createBackup +
                ", logLevel='" + logLevel + '\'' +
                '}';
    }
}