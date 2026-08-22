package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.core.strategy.ZipEntryContentStrategy;
import com.awei.frt.core.strategy.ZipEntryNameStrategy;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.OperationRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置压缩包策略测试：
 * - ZipEntryName：按 zip/jar 内部条目名命中
 * - ZipEntryContent：按 zip/jar 内条目文本内容（contentContains）命中
 * 命中判定通过 execute（命中→产生成功操作记录；未命中→无记录）
 */
class ZipStrategiesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    @Test
    void zipEntryNameMatchesInternalEntries() throws IOException {
        Path zip = createZip(Map.of("config/app.properties", "port=8080", "README.md", "hi"));

        assertTrue(matches(new ZipEntryNameStrategy(), zip, Map.of("patterns", "*.properties")),
                "含 .properties 条目的包应命中");
        assertFalse(matches(new ZipEntryNameStrategy(), zip, Map.of("patterns", "*.xml")),
                "无 .xml 条目的包不应命中");
        assertFalse(matches(new ZipEntryNameStrategy(), zip,
                        Map.of("patterns", "*.properties", "excludePatterns", "config/app.properties")),
                "被排除的条目不应导致命中");
    }

    @Test
    void zipEntryNameAddsZipToTarget() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path zip = createZip(Map.of("config/app.properties", "port=8080"));
        Path target = Files.createDirectories(tempDir.resolve("target"));

        Config config = ConfigLoader.getConfig();
        config.setTargetPath(target.toAbsolutePath());
        OperationContext ctx = new OperationContext(config);
        ctx.setRuleInheritanceContext(new RuleInheritanceContext(
                new MatchRule("ZipEntryName", Map.of(), List.of("*.properties"), List.of(), false, zip.getParent())));

        new ZipEntryNameStrategy().execute(leaf(zip), ctx, new String[]{OperationContext.OPERATION_ADD});

        assertTrue(Files.exists(target.resolve(zip.getFileName())), "命中的压缩包应被新增到目标目录");
        assertEquals(1, ctx.getProcessingResult().getSuccessCount());
    }

    @Test
    void zipEntryContentMatchesText() throws IOException {
        Path zip = createZip(Map.of("config/app.properties", "port=8080\nhost=localhost"));

        assertTrue(matches(new ZipEntryContentStrategy(), zip,
                        Map.of("patterns", "*.properties", "contentContains", "port=8080")),
                "内容包含关键词应命中");
        assertFalse(matches(new ZipEntryContentStrategy(), zip,
                        Map.of("patterns", "*.properties", "contentContains", "port=9090")),
                "内容不含关键词不应命中");
        assertFalse(matches(new ZipEntryContentStrategy(), zip, Map.of("patterns", "*.properties")),
                "未配置 contentContains 时策略不应命中");
        assertTrue(matches(new ZipEntryContentStrategy(), zip,
                        Map.of("patterns", "*.properties", "contentContains", "nothing, host=localhost")),
                "多个关键词中任一命中即可");
    }

    // ---------------- 辅助 ----------------

    private FileLeaf leaf(Path path) {
        return new FileLeaf(path, path.getFileName().toString());
    }

    /**
     * 执行一次新增并返回是否产生成功操作（命中）
     */
    private boolean matches(OperationStrategy strategy, Path zip, Map<String, String> replacements) throws IOException {
        TestSupport.isolateBackup(tempDir);
        Config config = ConfigLoader.getConfig();
        // 每次用独立目标目录（避免上次执行已复制同名文件导致 add 失败）
        config.setTargetPath(tempDir.resolve("target-" + System.nanoTime()).toAbsolutePath());
        OperationContext ctx = new OperationContext(config);
        MatchRule rule = new MatchRule();
        rule.setStrategyType("ZipEntryName");
        rule.setPatterns(split(replacements.get("patterns")));
        rule.setExcludePatterns(split(replacements.get("excludePatterns")));
        rule.setReplacements(replacements);
        ctx.setRuleInheritanceContext(new RuleInheritanceContext(rule));
        strategy.execute(leaf(zip), ctx, new String[]{OperationContext.OPERATION_ADD});
        return ctx.getProcessingResult().getOperationRecords().stream().anyMatch(OperationRecord::isSuccess);
    }

    private List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private Path createZip(Map<String, String> entries) throws IOException {
        Path zip = tempDir.resolve("sample-" + System.nanoTime() + ".zip");
        try (OutputStream os = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }
}
