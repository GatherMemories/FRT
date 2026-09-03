package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.StrategyStep;
import com.awei.frt.service.RuleConfigWizard;
import com.awei.frt.interaction.UserPrompter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：多策略组合链的扁平化建模与序列化。
 * 背景：早期把主策略作为"链步骤1"拷贝进 strategyChain（List&lt;MatchRule&gt;），
 * 造成 ① 主策略参数在顶层与链中重复 ② 链步骤还能再套子链（无限层级）
 * ③ 曾因 self-reference 引发 Jackson 序列化 StackOverflowError。
 * 现改为：顶层即主策略（第1步），strategyChain 只存后续步骤（StrategyStep 扁平结构）。
 */
class RuleChainSerializationTest {

    /** 与向导相同的组装方式：主策略留在顶层，strategyChain 只存后续步骤 */
    private static MatchRule buildRuleWithChain() {
        MatchRule rule = new MatchRule();
        rule.setStrategyType("FileSameName");
        rule.setPatterns(List.of("*.txt"));
        rule.setInheritToSubfolders(false);

        StrategyStep step2 = new StrategyStep();
        step2.setStrategyType("ZipEntryName");
        step2.setPatterns(List.of("*.jar"));
        rule.setStrategyChain(List.of(step2));
        return rule;
    }

    @Test
    void chainStoresOnlyAdditionalStepsNotTheRuleItself() {
        MatchRule rule = buildRuleWithChain();
        // 链只存后续步骤，不包含主策略自身（杜绝 self-reference 与重复参数）
        assertEquals(1, rule.getStrategyChain().size());
        assertNotSame(rule, rule.getStrategyChain().get(0), "链步骤不应引用规则自身");
        assertEquals("ZipEntryName", rule.getStrategyChain().get(0).getStrategyType());

        // 生效步骤 = [主策略, 后续步骤]
        List<String> types = rule.getEffectiveStrategies().stream()
                .map(MatchRule::getStrategyType)
                .toList();
        assertEquals(List.of("FileSameName", "ZipEntryName"), types);
    }

    @Test
    void serializeRuleWithChainIsFlatAndNonRedundant() throws Exception {
        MatchRule rule = buildRuleWithChain();
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(rule);

        assertTrue(json.contains("FileSameName"));
        assertTrue(json.contains("ZipEntryName"));
        // 计算属性 effectiveStrategies 不应出现在 JSON 中（返回 [this] 会无限递归）
        assertFalse(json.contains("effectiveStrategies"));
        // 主策略只出现一次（不再复制进链）；后续步骤也只出现一次
        assertEquals(json.indexOf("FileSameName"), json.lastIndexOf("FileSameName"),
                "主策略不应在链中重复出现");
        assertEquals(json.indexOf("ZipEntryName"), json.lastIndexOf("ZipEntryName"));
        // 继承开关只属于顶层规则，链步骤内不应出现（扁平结构无冗余字段）
        assertEquals(json.indexOf("inheritToSubfolders"), json.lastIndexOf("inheritToSubfolders"),
                "链步骤内不应携带 inheritToSubfolders");
        // 链步骤内不应出现嵌套 strategyChain（只有顶层一个 strategyChain 键）
        assertEquals(json.indexOf("strategyChain"), json.lastIndexOf("strategyChain"),
                "链步骤内不应嵌套 strategyChain");
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

            // 自校验 + 执行语义：主策略为第1步，链步骤按序展开
            MatchRule loaded = MatchRuleLoader.fromJson(json);
            assertNotNull(loaded);
            assertEquals(1, loaded.getStrategyChain().size(), "链只存后续步骤，不含主策略");
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
        copy.getStrategyChain().get(0).setStrategyType("McMod");
        copy.getPatterns().add("*.bak");
        copy.getReplacements().put("k", "v");
        assertEquals("ZipEntryName", rule.getStrategyChain().get(0).getStrategyType());
        assertEquals(1, rule.getPatterns().size());
        assertFalse(rule.getReplacements().containsKey("k"));
        // 链步骤也被深拷贝（拷贝的链步骤与原件不是同一对象）
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
