package com.awei.frt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * 操作记录模型
 */
public class OperationRecord {
    @JsonProperty("type")
    private OperationType type;
    
    @JsonProperty("sourcePath")
    private String sourcePath;
    
    @JsonProperty("targetPath")
    private String targetPath;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    public enum OperationType {
        REPLACE, DELETE, RESTORE
    }
    
    public OperationRecord() {}
    
    public OperationRecord(OperationType type, String sourcePath, String targetPath) {
        this.type = type;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.timestamp = LocalDateTime.now();
    }
    
    public OperationType getType() { return type; }
    public void setType(OperationType type) { this.type = type; }
    
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}