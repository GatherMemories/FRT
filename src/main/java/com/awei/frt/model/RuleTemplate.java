package com.awei.frt.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 规则模板模型（FR-2 规则模板库）
 * <p>
 * 内置常见场景的规则模板（Minecraft 模组更新 / 资源包 / 配置文件同步等），
 * 定义在 classpath 资源 rule-templates.json 中，UI 表单与控制台向导均可一键套用。
 * {@code rule} 字段即 {@link MatchRule} 的标准 JSON 序列化形态（含 strategyChain 空数组、
 * inheritToSubfolders），保证"套用模板 → 表单 → 写回"无字段漂移。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleTemplate {

    /** 模板唯一标识（如 mc-mod-update），内置模板清单锁定 */
    private String id;
    /** 模板名称（中文，直接展示在 UI 下拉与控制台列表） */
    private String name;
    /** 模板分类（如 Minecraft / 配置同步 / 通用） */
    private String category;
    /** 模板描述（中文，说明适用场景与规则文件放置层级） */
    private String description;
    /** 模板规则（MatchRule 标准形态） */
    private MatchRule rule;

    public RuleTemplate() {
    }

    /**
     * 深拷贝模板（rule 用 MatchRule.copy()），供套用时保证模板资源不被表单修改污染
     */
    public RuleTemplate copy() {
        RuleTemplate copy = new RuleTemplate();
        copy.id = this.id;
        copy.name = this.name;
        copy.category = this.category;
        copy.description = this.description;
        copy.rule = this.rule == null ? null : this.rule.copy();
        return copy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MatchRule getRule() {
        return rule;
    }

    public void setRule(MatchRule rule) {
        this.rule = rule;
    }
}
