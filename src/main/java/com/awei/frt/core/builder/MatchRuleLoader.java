package com.awei.frt.core.builder;

import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.MatchRule;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @Author: mou_ren
 * @Date: 2026/1/18 10:02
 * 匹配规则加载器
 */
public class MatchRuleLoader {

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
                for(JsonElement replacement: replacementsArray){
                    rule.getReplacements().add(replacement.getAsString());
                }
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
            if(jsonObject.has("strategyType")){
                String strategyType = jsonObject.get("strategyType").getAsString();
                if(StrategyFactory.StrategyType.getByValue(strategyType) == null){
                    throw new IllegalArgumentException("策略类型不合法");
                }
                rule.setStrategyType(strategyType);
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
    public boolean matches(String fileName, MatchRule rule) {
        // 如果有排除模式，且匹配任一排除模式，则不匹配
        for (String excludePattern : rule.getExcludePatterns()) {
            if (matchesPattern(fileName, excludePattern)) {
                return false;
            }
        }

        // 如果没有指定模式，则匹配所有文件
        if (rule.getPatterns().isEmpty()) {
            return true;
        }

        // 检查是否匹配任一模式
        for (String pattern : rule.getPatterns()) {
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
}
