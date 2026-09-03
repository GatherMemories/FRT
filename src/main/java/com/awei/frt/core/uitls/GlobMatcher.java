package com.awei.frt.core.uitls;

import java.util.List;

/**
 * @deprecated 包名拼写修正为 {@code com.awei.frt.core.utils}（原 uitls 系笔误）。
 * 本类仅保留旧包名作为<b>二进制兼容转发层</b>：早期版本发布的外部策略插件
 * （plugins/*.jar）编译时引用的 {@code com.awei.frt.core.uitls.GlobMatcher} 仍能加载执行；
 * 新代码请改用 {@link com.awei.frt.core.utils.GlobMatcher}。后续大版本可直接移除。
 */
@Deprecated
public final class GlobMatcher {

    private GlobMatcher() {
    }

    @Deprecated
    public static boolean matchesAny(String name, List<String> patterns, boolean caseSensitive) {
        return com.awei.frt.core.utils.GlobMatcher.matchesAny(name, patterns, caseSensitive);
    }

    @Deprecated
    public static boolean matches(String name, String pattern, boolean caseSensitive) {
        return com.awei.frt.core.utils.GlobMatcher.matches(name, pattern, caseSensitive);
    }
}
