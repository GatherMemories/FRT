package com.awei.frt;

import com.awei.frt.core.utils.GlobMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlobMatcher 通配符匹配测试：
 * - 空/空白名单语义
 * - * 与 ? 通配符
 * - 正则元字符字面匹配（[ ] ( ) 等不再被误当正则）
 * - 大小写敏感开关
 */
class GlobMatcherTest {

    @Test
    void emptyOrNullPatternsMatchAll() {
        assertTrue(GlobMatcher.matchesAny("anything.txt", null, true));
        assertTrue(GlobMatcher.matchesAny("anything.txt", List.of(), true));
    }

    @Test
    void exactAndWildcards() {
        assertTrue(GlobMatcher.matchesAny("config.json", List.of("config.json"), true));
        assertTrue(GlobMatcher.matchesAny("config.json", List.of("*.json"), true));
        assertTrue(GlobMatcher.matchesAny("a.txt", List.of("?.txt"), true));
        assertFalse(GlobMatcher.matchesAny("ab.txt", List.of("?.txt"), true));
        assertFalse(GlobMatcher.matchesAny("config.txt", List.of("*.json"), true));
        assertTrue(GlobMatcher.matchesAny("any.jar", List.of("*.jar"), true));
    }

    @Test
    void regexMetacharactersAreLiteral() {
        // 旧实现会把 [ ] ( ) 当正则，这里应字面匹配
        assertTrue(GlobMatcher.matchesAny("a[1].txt", List.of("a[1].txt"), true));
        assertFalse(GlobMatcher.matchesAny("a1.txt", List.of("a[1].txt"), true));
        assertTrue(GlobMatcher.matchesAny("config(1).json", List.of("config(1).json"), true));
        assertTrue(GlobMatcher.matchesAny("mod-1.2.3.jar", List.of("mod-1.2.3.jar"), true));
    }

    @Test
    void caseSensitivity() {
        assertTrue(GlobMatcher.matchesAny("Config.JSON", List.of("*.json"), false));
        assertFalse(GlobMatcher.matchesAny("Config.JSON", List.of("*.json"), true));
        assertTrue(GlobMatcher.matchesAny("Config.JSON", List.of("config.json"), false));
    }

    @Test
    void anyMatchSemantics() {
        assertTrue(GlobMatcher.matchesAny("b.txt", List.of("*.jar", "b.txt"), true));
        assertTrue(GlobMatcher.matchesAny("x.jar", List.of("*.jar", "b.txt"), true));
        assertFalse(GlobMatcher.matchesAny("x.zip", List.of("*.jar", "b.txt"), true));
    }
}
