package com.awei.frt.core.builder;

import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.MatchRule;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

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
            // 去除 UTF-8 BOM（如果有），兼容记事本等编辑器保存的带BOM文件
            if (json != null && json.startsWith("﻿")) {
                json = json.substring(1);
            }
            // 使用 Jackson 直接反序列化为对象
            MatchRule rule = objectMapper.readValue(json, MatchRule.class);

            // 验证策略类型是否合法（注册表校验，取代旧枚举校验）
            if (rule.getStrategyType() != null && !StrategyFactory.isSupported(rule.getStrategyType())) {
                throw new IllegalArgumentException("策略类型不合法: " + rule.getStrategyType());
            }

            return rule;
        } catch (IllegalArgumentException e) {
            // 策略类型验证失败
            System.err.println("解析规则失败: " + e.getMessage());
            return null;
        } catch (Exception e) {
            // JSON 解析失败
            LoggerUtil.logException("解析规则失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查文件名是否匹配规则（统一走 GlobMatcher，正则元字符安全）
     */
    public static boolean matches(String fileName, MatchRule rule) {
        if (rule == null) {
            return false;
        }
        // 黑名单：匹配任一排除模式则不匹配（空列表 = 不排除任何文件）
        if (rule.getExcludePatterns() != null && !rule.getExcludePatterns().isEmpty()
                && com.awei.frt.core.uitls.GlobMatcher.matchesAny(fileName, rule.getExcludePatterns(), true)) {
            return false;
        }

        // 白名单：没有指定模式则匹配所有文件
        return com.awei.frt.core.uitls.GlobMatcher.matchesAny(fileName, rule.getPatterns(), true);
    }
}
