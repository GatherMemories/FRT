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
    private List<String> replacements;          // 替换项：定义需要在文件中替换的旧值和新值（如需要给策略传值）
    private List<String> patterns;              // 匹配列表：定义哪些文件需要被处理（支持通配符）
    private List<String> excludePatterns;       // 排除列表：定义哪些文件需要被排除（支持通配符）
    private boolean inheritToSubfolders;        // 是否应用到子文件夹（子文件夹无规则才会生效，默认false）
    private Path path;                         // 文件位置

    public MatchRule() {
        this.replacements = new ArrayList<>();
        this.patterns = new ArrayList<>();
        this.excludePatterns = new ArrayList<>();
        this.inheritToSubfolders = false;
        this.path = null;
    }

    public MatchRule(String strategyType, List<String> replacements, List<String> patterns, List<String> excludePatterns, boolean inheritToSubfolders, Path path) {
        this.strategyType = strategyType;
        this.replacements = replacements;
        this.patterns = patterns;
        this.excludePatterns = excludePatterns;
        this.inheritToSubfolders = inheritToSubfolders;
        this.path = path;
    }



    // Getter 和 Setter 方法
    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public List<String> getReplacements() {
        return replacements;
    }

    public void setReplacements(List<String> replacements) {
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
