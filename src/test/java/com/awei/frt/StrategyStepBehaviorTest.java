package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.StrategyStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策略链扁平化后的行为覆盖测试（补足序列化测试未覆盖的路径）：
 * - 旧格式（无主策略、链=完整步骤列表）仍能展开
 * - 显示名 strategyType 被拒绝（用户报告的解析失败根因回归）
 * - 非法链步骤类型被拒绝
 * - StrategyStep 经 Jackson 往返后 replacements/patterns 保留
 * - 链步骤的 replacements 能传到执行（FileSameName + ZipEntryContent 组合）
 */
class StrategyStepBehaviorTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void oldFormatChainWithoutMainStrategyStillExpands() {
        MatchRule rule = new MatchRule(); // strategyType 为 null（旧"链=完整步骤列表"写法）
        StrategyStep s1 = new StrategyStep();
        s1.setStrategyType("FileSameName");
        s1.setPatterns(List.of("*.txt"));
        StrategyStep s2 = new StrategyStep();
        s2.setStrategyType("FileSameName");
        s2.setPatterns(List.of("*.json"));
        rule.setStrategyChain(List.of(s1, s2));

        List<String> types = rule.getEffectiveStrategies().stream()
                .map(MatchRule::getStrategyType)
                .toList();
        assertEquals(List.of("FileSameName", "FileSameName"), types,
                "无主策略时链步骤应作为完整列表展开");
    }

    @Test
    void displayNameStrategyTypeIsRejected() {
        // 用户报告的解析失败根因：strategyType 存了显示名而非注册表标识
        String json = """
                {"strategyType": "McMod（Minecraft 模组策略（按 modId 匹配 jar））", "patterns": ["*.jar"]}
                """;
        assertNull(MatchRuleLoader.fromJson(json), "显示名 strategyType 应被拒绝（返回 null）");
    }

    @Test
    void invalidChainStepTypeIsRejected() {
        String json = """
                {"strategyType": "FileSameName", "strategyChain": [{"strategyType": "NoSuchStrategy"}]}
                """;
        assertNull(MatchRuleLoader.fromJson(json), "非法链步骤类型应被拒绝（返回 null）");
    }

    @Test
    void strategyStepRoundTripsThroughJackson() throws IOException {
        MatchRule rule = new MatchRule();
        rule.setStrategyType("FileSameName");
        rule.setPatterns(List.of("*.md"));
        StrategyStep step = new StrategyStep();
        step.setStrategyType("ZipEntryContent");
        step.setPatterns(List.of("*.zip"));
        step.setExcludePatterns(List.of("*backup*"));
        step.setReplacements(Map.of("contentContains", "hello", "caseSensitive", "false"));
        rule.setStrategyChain(List.of(step));

        String json = MAPPER.writeValueAsString(rule);
        MatchRule loaded = MatchRuleLoader.fromJson(json);
        assertNotNull(loaded);
        assertEquals(1, loaded.getStrategyChain().size());
        StrategyStep loadedStep = loaded.getStrategyChain().get(0);
        assertEquals("ZipEntryContent", loadedStep.getStrategyType());
        assertEquals(List.of("*.zip"), loadedStep.getPatterns());
        assertEquals(List.of("*backup*"), loadedStep.getExcludePatterns());
        assertEquals(Map.of("contentContains", "hello", "caseSensitive", "false"), loadedStep.getReplacements());
    }

    @Test
    void chainStepReplacementsPropagateToExecution() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(updateDir.resolve("a.md"), "# readme");
        createZip(updateDir.resolve("b.zip"), Map.of("config/app.properties", "hello world"));

        // 主策略 FileSameName 处理 *.md；链步骤 ZipEntryContent 按内部条目名 *.properties + contentContains 命中 *.zip
        String ruleJson = """
                {
                  "strategyType": "FileSameName",
                  "patterns": ["*.md"],
                  "strategyChain": [
                    {"strategyType": "ZipEntryContent", "patterns": ["*.properties"],
                     "replacements": {"contentContains": "hello"}}
                  ],
                  "inheritToSubfolders": false
                }
                """;
        Files.writeString(updateDir.resolve("matching-rules.json"), ruleJson, StandardCharsets.UTF_8);

        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        OperationContext ctx = new OperationContext(config);
        FileNode tree = FileTreeBuilder.buildTree(updateDir);
        tree.process(null, ctx, FileNode.UPDATE_OPERATION);

        assertTrue(Files.exists(targetDir.resolve("a.md")), "主策略应处理 *.md");
        assertTrue(Files.exists(targetDir.resolve("b.zip")), "链步骤 replacements(contentContains) 应传达到执行");
        assertEquals(2, ctx.getProcessingResult().getSuccessCount(), "两个文件均应被链处理");
    }

    private void createZip(Path zip, Map<String, String> entries) throws IOException {
        try (OutputStream os = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }
}
