package com.awei.frt.core.uitls;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 通配符（glob）匹配工具
 * 统一"文件名匹配"逻辑，供规则校验与各策略复用（原 FileSameNameStrategy / MatchRuleLoader 各有
 * 一份重复且不严谨的实现：未转义 [ ] ( ) 等正则元字符）。
 *
 * 语义：
 * - 空/null 模式列表 = 匹配所有（白名单语义；黑名单场景调用方需先判空）
 * - 支持通配符 *（任意串）与 ?（单字符），其余字符按字面匹配（正则元字符自动转义）
 */
public final class GlobMatcher {

    private GlobMatcher() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 文件名是否匹配模式列表中的任意一个（白名单语义：空列表匹配所有）
     * @param name          文件名
     * @param patterns      匹配模式列表
     * @param caseSensitive 是否区分大小写
     * @return 是否匹配
     */
    public static boolean matchesAny(String name, List<String> patterns, boolean caseSensitive) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String n = caseSensitive ? name : name.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            if (pattern.isEmpty() || pattern.equals("*")) {
                return true;
            }
            String p = caseSensitive ? pattern : pattern.toLowerCase(Locale.ROOT);
            if (p.equals(n)) {
                return true;
            }
            if (Pattern.matches(toRegex(p), n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单个模式匹配
     */
    public static boolean matches(String name, String pattern, boolean caseSensitive) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("*")) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String n = caseSensitive ? name : name.toLowerCase(Locale.ROOT);
        String p = caseSensitive ? pattern : pattern.toLowerCase(Locale.ROOT);
        return p.equals(n) || Pattern.matches(toRegex(p), n);
    }

    /**
     * 把 glob 模式转为正则：* → .* ，? → . ，其余正则元字符全部转义（字面匹配）
     */
    private static String toRegex(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() + 8);
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> {
                    // 转义正则元字符：\.[]{}()+-^$| 等（. 在 glob 里也是字面量）
                    if ("\\.[]{}()+^$|".indexOf(c) >= 0) {
                        sb.append('\\');
                    }
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
