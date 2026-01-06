package com.awei.frt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.awei.frt.utils.ConfigLoader;

/**
 * 配置模型
 */
public class Config {
    @JsonProperty("targetPath")
    private String targetPath = "THtest";
    
    @JsonProperty("updatePath")
    private String updatePath = "update";
    
    @JsonProperty("deletePath")
    private String deletePath = "delete";
    
    @JsonProperty("backupPath")
    private String backupPath = "old";
    
    @JsonProperty("logPath")
    private String logPath = "logs";
    
    public Config() {}
    
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    
    public String getUpdatePath() { return updatePath; }
    public void setUpdatePath(String updatePath) { this.updatePath = updatePath; }
    
    public String getDeletePath() { return deletePath; }
    public void setDeletePath(String deletePath) { this.deletePath = deletePath; }
    
    public String getBackupPath() { return backupPath; }
    public void setBackupPath(String backupPath) { this.backupPath = backupPath; }
    
    public String getLogPath() { return logPath; }
    public void setLogPath(String logPath) { this.logPath = logPath; }
}