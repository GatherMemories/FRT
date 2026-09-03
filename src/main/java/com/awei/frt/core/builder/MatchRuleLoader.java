package com.awei.frt.core.builder;

import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.StrategyStep;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            if (json != null && json.startsWith("\uFEFF")) {
                json = json.substring(1);
            }
            // 使用 Jackson 直接反序列化为对象
            MatchRule rule = objectMapper.readValue(json, MatchRule.class);

            // 验证策略类型是否合法（注册表校验，取代旧枚举校验）
            if (rule.getStrategyType() != null && !StrategyFactory.isSupported(rule.getStrategyType())) {
                throw new IllegalArgumentException("策略类型不合法: " + rule.getStrategyType());
            }

            // 验证多策略组合链：每个步骤都必须注册
            if (rule.getStrategyChain() != null) {
                for (StrategyStep step : rule.getStrategyChain()) {
                    if (step == null || step.getStrategyType() == null
                            || !StrategyFactory.isSupported(step.getStrategyType())) {
                        throw new IllegalArgumentException("策略链步骤不合法: "
                                + (step == null ? "null" : step.getStrategyType()));
                    }
                }
            }

            return rule;
        } catch (IllegalArgumentException e) {
            // 策略类型验证失败
            LoggerUtil.logErrorMsg("解析规则失败: " + e.getMessage());
            return null;
        } catch (Exception e) {
            // JSON 解析失败
            LoggerUtil.logException("解析规则失败: " + e.getMessage(), e);
            return null;
        }
    }

    // 注：文件名匹配统一由 GlobMatcher 提供（策略内使用），不再保留本类中的重复实现
}
