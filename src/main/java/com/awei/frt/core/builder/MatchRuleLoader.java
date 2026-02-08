package com.awei.frt.core.builder;

import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.MatchRule;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @Author: mou_ren
 * @Date: 2026/1/18 10:02
 * 匹配规则加载器
 */
public class MatchRuleLoader {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 从JSON字符串解析规则
     */
    public static MatchRule fromJson(String json) {
        try {
            // 使用 Jackson 直接反序列化为对象
            MatchRule rule = objectMapper.readValue(json, MatchRule.class);

            // 验证策略类型是否合法
            if (rule.getStrategyType() != null) {
                if (StrategyFactory.StrategyType.getByValue(rule.getStrategyType()) == null) {
                    throw new IllegalArgumentException("策略类型不合法: " + rule.getStrategyType());
                }
            }

            return rule;
        } catch (IllegalArgumentException e) {
            // 策略类型验证失败
            System.err.println("解析规则失败: " + e.getMessage());
            return null;
        } catch (Exception e) {
            // JSON 解析失败
            System.err.println("解析规则失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 检查文件名是否匹配规则
     */
    public static boolean matches(String fileName, MatchRule rule) {
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
    private static boolean matchesPattern(String fileName, String pattern) {
        // 将通配符模式转换为正则表达式
        String regex = pattern.replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");

        return Pattern.matches(regex, fileName);
    }
}
