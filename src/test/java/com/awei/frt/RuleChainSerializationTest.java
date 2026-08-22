package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.service.RuleConfigWizard;
import com.awei.frt.ui.UserPrompter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：多策略组合链组装时，主策略作为链步骤 1 必须是"拷贝"而非原对象引用，
 * 否则 rule.strategyChain 引用 rule 自身 → Jackson 序列化无限递归 StackOverflowError
 * （对应日志 "生成规则文件失败 ... Infinite recursion ... MatchRule[\"strategyChain\"]"）。
 */
class RuleChainSerializationTest {

    /** 与向导相同的组装方式：主策略拷贝入链 + 追加链步骤 */
    private static MatchRule buildRuleWithChain() {
        MatchRule rule = new MatchRule();
        rule.setStrategyType("FileSameName");
        rule.setPatterns(List.of("*.txt"));
        rule.setInheritToSubfolders(false);

        List<MatchRule> chain = new ArrayList<>();
        chain.add(rule.copy()); // 第 1 步 = 主策略（拷贝）
        MatchRule step2 = new MatchRule();
        step2.setStrategyType("ZipEntryName");
        step2.setPatterns(List.of("*.jar"));
        chain.add(step2);
        rule.setStrategyChain(chain);
        return rule;
    }

    @Test
    void chainStepOneMustNotReferenceTheRuleItself() {
        MatchRule rule = buildRuleWithChain();
        assertNotSame(rule, rule.getStrategyChain().get(0),
                "链步骤 1 引用规则自身会造成 Jackson 序列化无限递归");
        assertEquals("FileSameName", rule.getStrategyChain().get(0).getStrategyType());
    }

    @Test
    void serializeRuleWithChainSucceeds() throws Exception {
        MatchRule rule = buildRuleWithChain();
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(rule); // 修复前此处抛 StackOverflowError
        assertTrue(json.contains("FileSameName"));
        assertTrue(json.contains("ZipEntryName"));
        // 计算属性 effectiveStrategies 不应出现在 JSON 中（叶规则返回 [this]，序列化会无限递归）
        assertFalse(json.contains("effectiveStrategies"));
    }

    @Test
    void serializeSingleStrategyRuleSucceeds() throws Exception {
        MatchRule rule = new MatchRule();
        rule.setStrategyType("FileSameName");
        rule.setPatterns(List.of("*.txt"));
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(rule); // 无链时 getEffectiveStrategies 返回 [this]，同样不能序列化
        assertTrue(json.contains("FileSameName"));
        assertFalse(json.contains("effectiveStrategies"));
    }

    @Test
    void writeRuleFileEndToEndWithStrategyChain() throws IOException {
        Path base = Files.createTempDirectory("rule-chain-serialization");
        try {
            Path targetDir = Files.createDirectories(base.resolve("THtest"));
            MatchRule rule = buildRuleWithChain();

            Config config = ConfigLoader.getConfig();
            UserPrompter yesPrompter = () -> "y"; // 确认写入
            new RuleConfigWizard(config, yesPrompter).writeRuleFile(rule, targetDir);

            Path ruleFile = targetDir.resolve("matching-rules.json");
            assertTrue(Files.exists(ruleFile), "规则文件应已写入");

            String json = Files.readString(ruleFile);
            assertTrue(json.contains("FileSameName"));
            assertTrue(json.contains("ZipEntryName"));

            // 自校验 + 执行语义：链步骤按序展开
            MatchRule loaded = MatchRuleLoader.fromJson(json);
            assertNotNull(loaded);
            List<String> types = loaded.getEffectiveStrategies().stream()
                    .map(MatchRule::getStrategyType)
                    .toList();
            assertEquals(List.of("FileSameName", "ZipEntryName"), types);
        } finally {
            deleteRecursively(base);
        }
    }

    @Test
    void copyIsDeepAndIndependent() {
        MatchRule rule = buildRuleWithChain();
        MatchRule copy = rule.copy();
        // 修改拷贝不影响原对象
        copy.getStrategyChain().get(1).setStrategyType("McMod");
        copy.getPatterns().add("*.bak");
        copy.getReplacements().put("k", "v");
        assertEquals("ZipEntryName", rule.getStrategyChain().get(1).getStrategyType());
        assertEquals(1, rule.getPatterns().size());
        assertFalse(rule.getReplacements().containsKey("k"));
        // 嵌套链也被深拷贝（拷贝的链步骤与原件不是同一对象）
        assertNotSame(rule.getStrategyChain().get(0), copy.getStrategyChain().get(0));
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
