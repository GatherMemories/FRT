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
        // 准备：update 与 target 各放一份【内容完全相同】的构造 mod jar（不依赖 gitignore 的真实 jar）
        Path base = Files.createTempDirectory("mcmod-test-eq");
        try {
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            byte[] sameContent = "identical-mod-content".getBytes(StandardCharsets.UTF_8);
            createJar(updateDir, "mod.jar", Map.of(
                    "META-INF/mods.toml", MODS_TOML, "data.txt", new String(sameContent, StandardCharsets.UTF_8)));
            createJar(targetDir, "mod.jar", Map.of(
                    "META-INF/mods.toml", MODS_TOML, "data.txt", new String(sameContent, StandardCharsets.UTF_8)));

            OperationContext ctx = buildContext(updateDir, targetDir, Map.of("onlyIfContentSame", "true"));
            new McModStrategy().execute(buildNode(updateDir), ctx, new String[]{OperationContext.OPERATION_REPLACE});

            // 内容相同 → 跳过替换，不产生操作记录
            assertTrue(ctx.getProcessingResult().getOperationRecords().isEmpty(),
                    "MD5 相同时应跳过替换，实际产生了操作记录");
        } finally {
            deleteRecursively(base);
        }
    }

    /**
     * 回归测试（审查 core H2）：同 modId 升级时源/目标文件名不同（版本号变化），
     * 替换必须写回目标侧<b>原文件名</b>并覆盖旧文件——
     * 修复前按源文件名推导目标路径（目标侧通常不存在该文件）→ 替换失败
     * 或成功后旧 jar 残留 → 目标目录同 modId 双 jar（Minecraft 重复加载风险）。
     */
    @Test
    void replaceWritesToExistingTargetFileNameNoDuplicateJar() throws IOException {
        Path base = Files.createTempDirectory("mcmod-h2-dup");
        try {
            TestSupport.isolateBackup(base);
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            // 目标侧旧版本 jar（文件名含 1.0.0），update 侧新版本 jar（文件名含 2.0.0，同 modId）
            createJar(targetDir, "testmod-1.0.0.jar", Map.of(
                    "META-INF/mods.toml", MODS_TOML, "payload.txt", "old"));
            String newToml = MODS_TOML.replace("version=\"1.0.0\"", "version=\"2.0.0\"");
            createJar(updateDir, "testmod-2.0.0.jar", Map.of(
                    "META-INF/mods.toml", newToml, "payload.txt", "new"));

            OperationContext ctx = buildContext(updateDir, targetDir, Map.of());
            new McModStrategy().execute(buildNode(updateDir), ctx, new String[]{OperationContext.OPERATION_REPLACE});

            // 替换成功且只产生 1 条记录
            assertEquals(1, ctx.getProcessingResult().getOperationRecords().size(),
                    "同 modId 升级应产生一次替换记录");
            assertTrue(ctx.getProcessingResult().getOperationRecords().get(0).isSuccess(),
                    "替换应成功（写回目标侧原文件名，而非不存在的源文件名路径）");
            // 目标目录仍是单个 jar（无同 modId 双 jar 残留），内容已是新版
            try (Stream<Path> files = Files.list(targetDir)) {
                List<Path> jars = files.filter(f -> f.getFileName().toString().endsWith(".jar")).toList();
                assertEquals(1, jars.size(), "目标目录不得出现同 modId 双 jar");
            }
            assertTrue(ctx.getProcessingResult().getOperationRecords().get(0).getTargetPath()
                    .getFileName().toString().startsWith("testmod-1.0.0"),
                    "替换应写回目标侧原文件名: "
                            + ctx.getProcessingResult().getOperationRecords().get(0).getTargetPath());
        } finally {
            TestSupport.restoreBackupPath();
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
