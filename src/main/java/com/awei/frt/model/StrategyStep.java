package com.awei.frt.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略链步骤（扁平结构，仅单层）。
 * <p>
 * 只描述一个策略步骤自身——策略类型 + 匹配白名单 + 排除黑名单 + 扩展参数。
 * 不包含嵌套策略链、继承开关、文件路径，因此：
 * <ul>
 *   <li>无法出现"子策略链里再套子策略链"的无限层级；</li>
 *   <li>序列化出的 JSON 无冗余字段（无空 strategyChain、无 inheritToSubfolders）。</li>
 * </ul>
 * 策略链语义：MatchRule 自身是"第 1 步（主策略）"，其 strategyChain 只存放后续步骤（第 2 步起）。
 */
public class StrategyStep {
    private String strategyType;                // 策略类型（策略注册表中的标识，如 FileSameName/McMod）
    private Map<String, String> replacements;   // 策略扩展参数（键值对）
    private List<String> patterns;              // 匹配白名单（支持通配符）
    private List<String> excludePatterns;       // 排除黑名单（支持通配符）

    public StrategyStep() {
        this.replacements = new LinkedHashMap<>();
        this.patterns = new ArrayList<>();
        this.excludePatterns = new ArrayList<>();
    }

    /**
     * 深拷贝（列表与 Map 均复制，供 MatchRule.copy() 深拷贝策略链）。
     */
    public StrategyStep copy() {
        StrategyStep copy = new StrategyStep();
        copy.strategyType = this.strategyType;
        copy.replacements = this.replacements != null ? new LinkedHashMap<>(this.replacements) : new LinkedHashMap<>();
        copy.patterns = this.patterns != null ? new ArrayList<>(this.patterns) : new ArrayList<>();
        copy.excludePatterns = this.excludePatterns != null ? new ArrayList<>(this.excludePatterns) : new ArrayList<>();
        return copy;
    }

    /**
     * 转换为运行时使用的 MatchRule（执行链迭代时复用 MatchRule 接口）。
     * 链步骤不含继承开关/嵌套链，转换后这些字段取默认值。
     */
    public MatchRule toMatchRule() {
        MatchRule rule = new MatchRule();
        rule.setStrategyType(strategyType);
        rule.setPatterns(patterns);
        rule.setExcludePatterns(excludePatterns);
        rule.setReplacements(replacements);
        return rule;
    }

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
}
