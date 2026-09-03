package com.awei.frt.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则文本解析工具测试（审查 L1 收敛后：向导/表单共用实现的边界锁定）
 */
class RuleInputParserTest {

    @Test
    void parseListSplitsTrimDropsEmpty() {
        assertEquals(List.of("a", "b", "c"), RuleInputParser.parseList(" a , b ,, c "));
        assertEquals(List.of(), RuleInputParser.parseList(""));
        assertEquals(List.of(), RuleInputParser.parseList(null));
        assertEquals(List.of("only"), RuleInputParser.parseList("only"));
    }

    @Test
    void parseMapSplitsKeyValueAndIgnoresBadItems() {
        Map<String, String> map = RuleInputParser.parseMap("a=1, b = 2 , bad-item , c=3");
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("b"));
        assertEquals("3", map.get("c"));
        assertEquals(3, map.size(), "格式错误项应被忽略");
    }

    @Test
    void parseMapEmptyOrNullReturnsEmpty() {
        assertTrue(RuleInputParser.parseMap("").isEmpty());
        assertTrue(RuleInputParser.parseMap(null).isEmpty());
    }

    @Test
    void parseBooleanRecognizesYesForms() {
        assertTrue(RuleInputParser.parseBoolean("y", false));
        assertTrue(RuleInputParser.parseBoolean("YES", false));
        assertTrue(RuleInputParser.parseBoolean("True", false));
        assertFalse(RuleInputParser.parseBoolean("n", true));
        assertFalse(RuleInputParser.parseBoolean("maybe", true));
        assertEquals(true, RuleInputParser.parseBoolean("", true), "空输入用默认值");
        assertEquals(false, RuleInputParser.parseBoolean(null, false));
    }
}
