package com.awei.frt.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 匹配规则模型
 * 定义文件操作的规则配置
 */
@JsonIgnoreProperties({"path"})
public class MatchRule {
    private String strategyType;                // 策略类型（单策略时使用）
    private Map<String, String> replacements;   // 策略扩展参数（键值对）：供各策略读取自定义配置，如 {"onlyIfVersionChanged": "true"}
    private List<String> patterns;              // 匹配列表：定义哪些文件需要被处理（支持通配符）
    private List<String> excludePatterns;       // 排除列表：定义哪些文件需要被排除（支持通配符）
    private List<MatchRule> strategyChain;      // 多策略组合链（可选）：链中每个元素是一个策略步骤，
                                                // 依次执行，后续步骤只处理前序策略"剩余"的文件（未被处理标记的节点）
    private boolean inheritToSubfolders;        // 是否应用到子文件夹（子文件夹无规则才会生效，默认false）
    private transient Path path;                         // 文件位置

    public MatchRule() {
        this.replacements = new LinkedHashMap<>();
        this.patterns = new ArrayList<>();
        this.excludePatterns = new ArrayList<>();
        this.strategyChain = new ArrayList<>();
        this.inheritToSubfolders = false;
        this.path = null;
    }

    public MatchRule(String strategyType, Map<String, String> replacements, List<String> patterns, List<String> excludePatterns, boolean inheritToSubfolders, Path path) {
        this.strategyType = strategyType;
        this.replacements = replacements != null ? replacements : new LinkedHashMap<>();
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

    public Map<String, String> getReplacements() {
        return replacements;
    }

    public void setReplacements(Map<String, String> replacements) {
        this.replacements = replacements != null ? replacements : new LinkedHashMap<>();
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

    public List<MatchRule> getStrategyChain() {
        return strategyChain;
    }

    public void setStrategyChain(List<MatchRule> strategyChain) {
        this.strategyChain = strategyChain != null ? strategyChain : new ArrayList<>();
    }

    /**
     * 获取实际生效的策略步骤列表（展开嵌套链，带深度保护）：
     * - 配置了 strategyChain 时按链顺序展开
     * - 未配置时退化为 [this]（保持旧版单策略行为）
     * @return 有序策略步骤列表（至少含 strategyType 的规则）
     */
    public List<MatchRule> getEffectiveStrategies() {
        List<MatchRule> steps = new ArrayList<>();
        collectStrategies(this, steps, 0);
        return steps;
    }

    private static void collectStrategies(MatchRule rule, List<MatchRule> out, int depth) {
        if (rule == null || depth > 8) {
            return;
        }
        if (rule.strategyChain == null || rule.strategyChain.isEmpty()) {
            if (rule.strategyType != null && !rule.strategyType.isBlank()) {
                out.add(rule);
            }
            return;
        }
        for (MatchRule step : rule.strategyChain) {
            collectStrategies(step, out, depth + 1);
        }
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
