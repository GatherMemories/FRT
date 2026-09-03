package com.awei.frt.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 规则/配置文本解析工具（审查 L1 收敛项）：
 * 原 parseList/parseMap/parseBoolean 在 RuleConfigWizard / RuleWizardForm / CoreConfigWizard
 * 三处各自复制一份，行为易分叉（如警告缩进不一致）——统一收口为静态工具，三处共用。
 */
public final class RuleInputParser {

    private RuleInputParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析逗号分隔字符串为列表（去空白、去空项）
     * @param input 形如 "a, b, c" 的输入；null/空白 → 空列表
     */
    public static List<String> parseList(String input) {
        List<String> list = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return list;
        }
        for (String item : input.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * 解析键值对字符串为 Map（格式 key=value，多个逗号分隔；格式错误的项忽略并打警告到 stdout）
     * @param input 形如 "a=1, b=2" 的输入；null/空白 → 空 Map
     */
    public static Map<String, String> parseMap(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        if (input == null || input.trim().isEmpty()) {
            return map;
        }
        for (String item : input.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (!key.isEmpty()) {
                    map.put(key, value);
                    continue;
                }
            }
            System.out.println("[警告] 忽略格式错误的参数项: " + trimmed + " (应为 key=value)");
        }
        return map;
    }

    /**
     * 解析布尔输入（y/yes/true 大小写不敏感 = 真，其余 = 默认值）
     * @param input 用户输入；null/空白 → 返回默认值
     * @param defaultValue 默认值
     */
    public static boolean parseBoolean(String input, boolean defaultValue) {
        if (input == null || input.trim().isEmpty()) {
            return defaultValue;
        }
        String lower = input.trim().toLowerCase(Locale.ROOT);
        return lower.equals("y") || lower.equals("yes") || lower.equals("true");
    }
}
