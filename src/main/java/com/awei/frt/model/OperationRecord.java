package com.awei.frt.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 操作记录模型
 * 记录每次文件操作的详细信息
 */
public class OperationRecord {


    private String strategyType;       // 策略类型
    private String operationType;      // 操作类型：ADD, REPLACE, DELETE
    private Path sourcePath;           // 源文件路径
    private Path targetPath;           // 目标文件路径
    private String sourceFileSign;     // 源文件特征（方便查找，常用md5,策略比对值）
    private String targetFileSign;     // 目标文件特征（方便查找，常用md5，策略比对值）
    private LocalDateTime timestamp;   // 操作时间戳
    private boolean success;           // 操作是否成功
    private String errorMessage;       // 错误信息（如果操作失败）

    public OperationRecord() {
        this.timestamp = LocalDateTime.now();
    }

    public OperationRecord(String strategyType, String operationType, Path sourcePath, Path targetPath, String sourceFileSign, String targetFileSign, boolean success, String errorMessage) {
        this.strategyType = strategyType;
        this.operationType = operationType;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.sourceFileSign = sourceFileSign;
        this.targetFileSign = targetFileSign;
        this.timestamp = LocalDateTime.now();
        this.success = success;
        this.errorMessage = errorMessage;
    }

    // Getter 和 Setter 方法
    public String getSourceFileSign() {
        return sourceFileSign;
    }

    public void setSourceFileSign(String sourceFileSign) {
        this.sourceFileSign = sourceFileSign;
    }

    public String getTargetFileSign() {
        return targetFileSign;
    }

    public void setTargetFileSign(String targetFileSign) {
        this.targetFileSign = targetFileSign;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Path getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(Path targetPath) {
        this.targetPath = targetPath;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "OperationRecord{" +
                "strategyType='" + strategyType + '\'' +
                ", operationType='" + operationType + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", timestamp=" + timestamp +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
