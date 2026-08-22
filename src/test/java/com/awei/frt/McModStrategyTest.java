package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FolderNode;
import com.awei.frt.core.strategy.McModStrategy;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * McModStrategy 策略扩展参数测试：
 * - onlyIfContentSame=true：源与目标文件 MD5 相同则跳过替换（内容一致）
 * - 内容不同（同 modId 同版本重打包）时仍执行替换
 */
class McModStrategyTest {

    private static final Path UPDATE_DIR = Path.of("testDic/update");

    private static final String MODS_TOML = """
            modLoader="javafml"
            loaderVersion="[47,)"

            [[mods]]
            modId="testmod"
            version="1.0.0"
            displayName="Test Mod"
            """;

    @Test
    void onlyIfContentSameSkipsWhenMd5Equal() throws IOException {
        // 准备：update 与 target 各放一份【内容完全相同】的真实 mod jar
        Path base = Files.createTempDirectory("mcmod-test-eq");
        try {
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            Path srcJar = findTestJar("litematica");
            Files.copy(srcJar, updateDir.resolve("mod.jar"));
            Files.copy(srcJar, targetDir.resolve("mod.jar"));

            OperationContext ctx = buildContext(updateDir, targetDir, Map.of("onlyIfContentSame", "true"));
            new McModStrategy().execute(buildNode(updateDir), ctx, new String[]{OperationContext.OPERATION_REPLACE});

            // 内容相同 → 跳过替换，不产生操作记录
            assertTrue(ctx.getProcessingResult().getOperationRecords().isEmpty(),
                    "MD5 相同时应跳过替换，实际产生了操作记录");
        } finally {
            deleteRecursively(base);
        }
    }

    @Test
    void onlyIfContentSameReplacesWhenContentDiffers() throws IOException {
        // 准备：同 modId 同版本，但内容不同（模拟同版本重新打包）
        Path base = Files.createTempDirectory("mcmod-test-diff");
        try {
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            createJar(updateDir, "mod.jar", Map.of("META-INF/mods.toml", MODS_TOML, "a.txt", "AAA"));
            createJar(targetDir, "mod.jar", Map.of("META-INF/mods.toml", MODS_TOML, "b.txt", "BBB"));

            OperationContext ctx = buildContext(updateDir, targetDir, Map.of("onlyIfContentSame", "true"));
            new McModStrategy().execute(buildNode(updateDir), ctx, new String[]{OperationContext.OPERATION_REPLACE});

            // 内容不同 → 执行替换，产生 1 条操作记录
            assertEquals(1, ctx.getProcessingResult().getOperationRecords().size(),
                    "MD5 不同时应执行替换");
            assertTrue(ctx.getProcessingResult().getOperationRecords().get(0).isSuccess());
        } finally {
            deleteRecursively(base);
        }
    }

    @Test
    void chainFileSameNameDoesNotReprocessModJars() throws IOException {
        // 回归：McMod（目录级）+ 链 FileSameName（空 patterns 匹配所有）时，
        // mod.jar 只应被 McMod 按 modId 处理一次，文件级 FileSameName 不得再复制同一 jar
        Path base = Files.createTempDirectory("mcmod-chain-dedup");
        try {
            TestSupport.isolateBackup(base);
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            createJar(updateDir, "mod.jar", Map.of("META-INF/mods.toml", MODS_TOML));
            Files.writeString(updateDir.resolve("notes.txt"), "hi", StandardCharsets.UTF_8);

            String ruleJson = """
                    {
                      "strategyType": "McMod",
                      "patterns": ["*.jar"],
                      "strategyChain": [
                        {"strategyType": "FileSameName", "patterns": [], "excludePatterns": ["*.md"]}
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

            // 修复前：mod.jar 被 McMod 新增 + FileSameName 再替换（重复）；修复后只出现一次
            java.util.List<String> targets = ctx.getProcessingResult().getOperationRecords().stream()
                    .map(r -> r.getTargetPath() != null ? r.getTargetPath().getFileName().toString() : "?")
                    .toList();
            assertEquals(java.util.List.of("mod.jar", "notes.txt"), targets,
                    "mod.jar 不应被链中文件级策略重复处理");
        } finally {
            TestSupport.restoreBackupPath();
            deleteRecursively(base);
        }
    }

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    // ---------------- 辅助 ----------------

    private OperationContext buildContext(Path updateDir, Path targetDir, Map<String, String> replacements) throws IOException {
        // 备份路径隔离到临时目录，避免测试污染真实 testDic/backup
        TestSupport.isolateBackup(updateDir.getParent());
        Config config = ConfigLoader.getConfig();
        // 指向临时目录（绝对路径，OperationContext 构造时读取）
        config.setTargetPath(targetDir.toAbsolutePath());
        OperationContext ctx = new OperationContext(config);

        MatchRule rule = new MatchRule("McMod", replacements,
                List.of("*.jar"), List.of(), false, updateDir);
        ctx.setRuleInheritanceContext(new RuleInheritanceContext(rule));
        return ctx;
    }

    private FolderNode buildNode(Path updateDir) {
        FolderNode node = new FolderNode(updateDir, "");
        node.buildChildren();
        return node;
    }

    private Path findTestJar(String keyword) throws IOException {
        try (Stream<Path> files = Files.list(UPDATE_DIR)) {
            return files.filter(f -> f.getFileName().toString().contains(keyword)
                    && f.getFileName().toString().endsWith(".jar"))
                    .findFirst().orElseThrow(() -> new IOException("未找到测试 jar: " + keyword));
        }
    }

    private Path createJar(Path dir, String fileName, Map<String, String> entries) throws IOException {
        Path jar = dir.resolve(fileName);
        try (OutputStream os = Files.newOutputStream(jar);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return jar;
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
