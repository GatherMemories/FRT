package com.awei.frt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 替换规则模型
 */
public class ReplaceRule {
    @JsonProperty("patterns")
    private List<String> patterns;
    
    @JsonProperty("excludePatterns")
    private List<String> excludePatterns;
    
    @JsonProperty("backup")
    private boolean backup = true;
    
    @JsonProperty("confirmBeforeReplace")
    private boolean confirmBeforeReplace = false;
    
    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    
    public List<String> getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }
    
    public boolean isBackup() { return backup; }
    public void setBackup(boolean backup) { this.backup = backup; }
    
    public boolean isConfirmBeforeReplace() { return confirmBeforeReplace; }
    public void setConfirmBeforeReplace(boolean confirmBeforeReplace) { this.confirmBeforeReplace = confirmBeforeReplace; }
}