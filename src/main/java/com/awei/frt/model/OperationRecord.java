package com.awei.frt.model;

import java.time.LocalDateTime;

/**
 * 操作记录模型
 * 记录每次文件操作的详细信息
 */
public class OperationRecord {
    public enum OperationType {
        ADD, REPLACE, DELETE, RESTORE
    }
    
    private String operationType;      // 操作类型：ADD, REPLACE, DELETE
    private String sourcePath;         // 源文件路径
    private String targetPath;         // 目标文件路径
    private LocalDateTime timestamp;   // 操作时间戳
    private boolean success;           // 操作是否成功
    private String errorMessage;       // 错误信息（如果操作失败）

    public OperationRecord() {
        this.timestamp = LocalDateTime.now();
    }

    public OperationRecord(String operationType, String sourcePath, String targetPath, 
                          boolean success, String errorMessage) {
        this.operationType = operationType;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.success = success;
        this.errorMessage = errorMessage;
        this.timestamp = LocalDateTime.now();
    }

    // Getter 和 Setter 方法
    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
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
                "operationType='" + operationType + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", timestamp=" + timestamp +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}