package com.awei.frt.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * 深拷贝规则对象（含嵌套策略链、列表与 Map 均复制，path 为 transient 不复制）。
     * 用于向导组装策略链时把主策略作为"步骤1"放入链中——必须拷贝而非引用原对象，
     * 否则 rule.strategyChain 会引用 rule 自身，Jackson 序列化时无限递归导致 StackOverflowError。
     * @return 字段值相同、无共享可变引用的新规则
     */
    public MatchRule copy() {
        MatchRule copy = new MatchRule();
        copy.strategyType = this.strategyType;
        copy.replacements = this.replacements != null ? new LinkedHashMap<>(this.replacements) : new LinkedHashMap<>();
        copy.patterns = this.patterns != null ? new ArrayList<>(this.patterns) : new ArrayList<>();
        copy.excludePatterns = this.excludePatterns != null ? new ArrayList<>(this.excludePatterns) : new ArrayList<>();
        copy.inheritToSubfolders = this.inheritToSubfolders;
        if (this.strategyChain != null) {
            copy.strategyChain = new ArrayList<>();
            for (MatchRule step : this.strategyChain) {
                copy.strategyChain.add(step == null ? null : step.copy());
            }
        }
        return copy;
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
     * 仅作运行时计算用，不参与 JSON 序列化：
     * 未配置链时本方法返回 [this]，若被序列化会与自身无限递归（StackOverflowError）。
     * @return 有序策略步骤列表（至少含 strategyType 的规则）
     */
    @JsonIgnore
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
