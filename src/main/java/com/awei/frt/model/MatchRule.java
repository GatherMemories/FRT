package com.awei.frt.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 匹配规则模型
 * 定义文件操作的规则配置
 */
public class MatchRule {
    private String strategyType;                // 策略类型
    private List<Object> replacements;          // 替换项：定义需要在文件中替换的旧值和新值（如需要给策略传值）
    private List<String> patterns;              // 匹配列表：定义哪些文件需要被处理（支持通配符）
    private List<String> excludePatterns;       // 排除列表：定义哪些文件需要被排除（支持通配符）
    private boolean inheritToSubfolders;        // 是否应用到子文件夹（子文件夹无规则才会生效，默认false）
    private Path path;                          // 文件位置

    public MatchRule() {
        this.replacements = new ArrayList<>();
        this.patterns = new ArrayList<>();
        this.excludePatterns = new ArrayList<>();
        this.inheritToSubfolders = false;
        this.path = null;
    }

    public MatchRule(String strategyType, List<Object> replacements, List<String> patterns, List<String> excludePatterns, boolean inheritToSubfolders, Path path) {
        this.strategyType = strategyType;
        this.replacements = replacements;
        this.patterns = patterns;
        this.excludePatterns = excludePatterns;
        this.inheritToSubfolders = inheritToSubfolders;
        this.path = path;
    }

    /**
     * 从JSON字符串解析规则
     */
    public static MatchRule fromJson(String json) {
        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

            MatchRule rule = new MatchRule();

            // 提取 replacements 数组
            if (jsonObject.has("replacements")) {
                // TODO 数据结构和获取值 需要改进
                JsonArray replacementsArray = jsonObject.getAsJsonArray("replacements");
                rule.getReplacements().add(replacementsArray);
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
            if (jsonObject.has("inheritToSubfolders")) {
                boolean inheritToSubfolders = jsonObject.get("inheritToSubfolders").getAsBoolean();
                rule.setInheritToSubfolders(inheritToSubfolders);
            }

            // 提取 strategyType 字符串
            rule.setStrategyType(jsonObject.get("strategyType").getAsString());
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
    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public List<Object> getReplacements() {
        return replacements;
    }

    public void setReplacements(List<Object> replacements) {
        this.replacements = replacements;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public boolean isInheritToSubfolders() {
        return inheritToSubfolders;
    }

    public void setInheritToSubfolders(boolean inheritToSubfolders) {
        this.inheritToSubfolders = inheritToSubfolders;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }


}
