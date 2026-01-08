package com.awei.frt.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 替换规则模型
 * 定义文件操作的规则配置
 */
public class ReplaceRule {
    private List<Replacement> replacements;      // 替换内容列表：定义需要在文件中替换的旧值和新值
    private List<String> patterns;              // 匹配模式列表：定义哪些文件需要被处理（支持通配符）
    private List<String> excludePatterns;       // 排除模式列表：定义哪些文件需要被排除（支持通配符）
    private boolean backup;                     // 是否备份：操作前是否创建备份文件
    private boolean confirmBeforeReplace;       // 替换前确认：执行替换操作前是否需要用户确认

    public ReplaceRule() {
        this.replacements = new ArrayList<>();
        this.patterns = new ArrayList<>();
        this.excludePatterns = new ArrayList<>();
        this.backup = true;
        this.confirmBeforeReplace = false;
    }

    public ReplaceRule(List<Replacement> replacements, List<String> patterns, 
                      List<String> excludePatterns, boolean backup, boolean confirmBeforeReplace) {
        this.replacements = replacements != null ? replacements : new ArrayList<>();
        this.patterns = patterns != null ? patterns : new ArrayList<>();
        this.excludePatterns = excludePatterns != null ? excludePatterns : new ArrayList<>();
        this.backup = backup;
        this.confirmBeforeReplace = confirmBeforeReplace;
    }

    /**
     * 从JSON字符串解析规则
     */
    public static ReplaceRule fromJson(String json) {
        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
                
            ReplaceRule rule = new ReplaceRule();
                
            // 提取 replacements 数组
            if (jsonObject.has("replacements")) {
                JsonArray replacementsArray = jsonObject.getAsJsonArray("replacements");
                List<Replacement> replacements = new ArrayList<>();
                for (JsonElement element : replacementsArray) {
                    JsonObject obj = element.getAsJsonObject();
                    String oldValue = obj.get("oldValue").getAsString();
                    String newValue = obj.get("newValue").getAsString();
                    replacements.add(new Replacement(oldValue, newValue));
                }
                rule.setReplacements(replacements);
            }
                
            // 提取 patterns 数组
            if (jsonObject.has("patterns")) {
                JsonArray patternsArray = jsonObject.getAsJsonArray("patterns");
                List<String> patterns = new ArrayList<>();
                for (JsonElement element : patternsArray) {
                    patterns.add(element.getAsString());
                }
                rule.setPatterns(patterns);
            }
                
            // 提取 excludePatterns 数组
            if (jsonObject.has("excludePatterns")) {
                JsonArray excludePatternsArray = jsonObject.getAsJsonArray("excludePatterns");
                List<String> excludePatterns = new ArrayList<>();
                for (JsonElement element : excludePatternsArray) {
                    excludePatterns.add(element.getAsString());
                }
                rule.setExcludePatterns(excludePatterns);
            }
                
            // 提取 backup 布尔值
            if (jsonObject.has("backup")) {
                boolean backup = jsonObject.get("backup").getAsBoolean();
                rule.setBackup(backup);
            }
                
            // 提取 confirmBeforeReplace 布尔值
            if (jsonObject.has("confirmBeforeReplace")) {
                boolean confirm = jsonObject.get("confirmBeforeReplace").getAsBoolean();
                rule.setConfirmBeforeReplace(confirm);
            }
                
            return rule;
        } catch (Exception e) {
            System.err.println("解析规则失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 检查文件名是否匹配规则
     */
    public boolean matches(String fileName) {
        // 如果有排除模式，且匹配任一排除模式，则不匹配
        for (String excludePattern : excludePatterns) {
            if (matchesPattern(fileName, excludePattern)) {
                return false;
            }
        }
        
        // 如果没有指定模式，则匹配所有文件
        if (patterns.isEmpty()) {
            return true;
        }
        
        // 检查是否匹配任一模式
        for (String pattern : patterns) {
            if (matchesPattern(fileName, pattern)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查文件名是否匹配模式（支持通配符）
     */
    private boolean matchesPattern(String fileName, String pattern) {
        // 将通配符模式转换为正则表达式
        String regex = pattern.replace(".", "\\.")
                             .replace("*", ".*")
                             .replace("?", ".");
        
        return Pattern.matches(regex, fileName);
    }

    // Getter 和 Setter 方法
    public List<Replacement> getReplacements() {
        return replacements;
    }

    public void setReplacements(List<Replacement> replacements) {
        this.replacements = replacements != null ? replacements : new ArrayList<>();
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns != null ? patterns : new ArrayList<>();
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns != null ? excludePatterns : new ArrayList<>();
    }

    public boolean isBackup() {
        return backup;
    }

    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    public boolean isConfirmBeforeReplace() {
        return confirmBeforeReplace;
    }

    public void setConfirmBeforeReplace(boolean confirmBeforeReplace) {
        this.confirmBeforeReplace = confirmBeforeReplace;
    }

    /**
     * 替换项内部类
     */
    public static class Replacement {
        private String oldValue;      // 被替换的旧值
        private String newValue;      // 替换成的新值

        public Replacement() {}

        public Replacement(String oldValue, String newValue) {
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getOldValue() {
            return oldValue;
        }

        public void setOldValue(String oldValue) {
            this.oldValue = oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public void setNewValue(String newValue) {
            this.newValue = newValue;
        }
    }
}