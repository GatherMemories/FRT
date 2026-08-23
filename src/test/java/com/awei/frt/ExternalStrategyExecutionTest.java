package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.factory.StrategyLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.testutil.ExtPluginSources;
import com.awei.frt.testutil.TestPluginBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部策略【执行】全面测试：
 * - ADD / REPLACE（含备份）/ DELETE 三种文件操作真实执行
 * - 模板钩子（AbstractOperationStrategy）与裸实现（OperationStrategy）两条编写路径
 * - 策略链语义：作为链中后续步骤处理"剩余文件"；处理成功后标记 handled 不被后续步骤重复处理
 * - replacements 扩展参数读取（context.getRuleParam）
 * - accepts() 过滤、异常兜底（StrategyProxy 记失败不中断）、dry-run 预览不落盘
 */
class ExternalStrategyExecutionTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void loadPlugins() throws Exception {
        Path jar = TestPluginBuilder.buildPluginJar(
                Files.createDirectories(tempDir.resolve("plugin")), "ext-exec-plugin.jar",
                ExtPluginSources.FULL, null);
        StrategyLoader.loadPluginJar(jar);
    }

    // ---------------- 执行用例 ----------------

    @Test
    void externalAddCopiesFilesToTarget() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("add/update"));
        Path target = Files.createDirectories(tempDir.resolve("add/target"));
        Path backup = Files.createDirectories(tempDir.resolve("add/backup"));
        Files.writeString(update.resolve("a.txt"), "aaa");
        Files.writeString(update.resolve("b.dat"), "bbb");
        writeRule(update, rule("ExtAddStrategy"));

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(2, ctx.getSuccessCount(), "外部策略应复制全部文件到目标");
        assertEquals("aaa", Files.readString(target.resolve("a.txt")));
        assertEquals("bbb", Files.readString(target.resolve("b.dat")));
        List<OperationRecord> records = ctx.getProcessingResult().getOperationRecords();
        assertEquals(2, records.size());
        for (OperationRecord r : records) {
            assertEquals("ExtAddStrategy", r.getStrategyType(), "操作记录应带外部策略类型");
            assertEquals(OperationContext.OPERATION_ADD, r.getOperationType());
        }
    }

    @Test
    void externalReplaceBacksUpAndOverwrites() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("rep/update"));
        Path target = Files.createDirectories(tempDir.resolve("rep/target"));
        Path backup = Files.createDirectories(tempDir.resolve("rep/backup"));
        Files.writeString(update.resolve("a.txt"), "new-content");
        Files.writeString(target.resolve("a.txt"), "old-content");
        writeRule(update, rule("ExtAddStrategy"));

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals("new-content", Files.readString(target.resolve("a.txt")), "REPLACE 应覆盖目标");
        assertEquals(1, ctx.getSuccessCount());
        OperationRecord record = ctx.getProcessingResult().getOperationRecords().get(0);
        assertEquals(OperationContext.OPERATION_REPLACE, record.getOperationType());
        // 备份体系：替换前的旧文件应被备份（含 record 会话记录）
        try (Stream<Path> files = Files.walk(backup)) {
            long backedUp = files.filter(Files::isRegularFile).count();
            assertTrue(backedUp > 0, "REPLACE 应产生备份文件，实际: " + backedUp);
        }
    }

    @Test
    void externalDeleteRemovesTargetFiles() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("del/update"));
        Path target = Files.createDirectories(tempDir.resolve("del/target"));
        Path backup = Files.createDirectories(tempDir.resolve("del/backup"));
        Files.writeString(update.resolve("a.txt"), "x"); // 树节点（规则文件会被跳过）
        Files.writeString(target.resolve("a.txt"), "to-delete");
        writeRule(update, rule("ExtAddStrategy"));

        OperationContext ctx = runDelete(update, target, backup);

        assertFalse(Files.exists(target.resolve("a.txt")), "DELETE 应删除目标文件");
        assertEquals(1, ctx.getSuccessCount());
        assertEquals(OperationContext.OPERATION_DELETE,
                ctx.getProcessingResult().getOperationRecords().get(0).getOperationType());
    }

    @Test
    void externalAsChainSecondStepProcessesRemainingFiles() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("chain1/update"));
        Path target = Files.createDirectories(tempDir.resolve("chain1/target"));
        Path backup = Files.createDirectories(tempDir.resolve("chain1/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        Files.writeString(update.resolve("b.json"), "{}");
        writeRule(update, """
            {
              "strategyType": "FileSameName",
              "patterns": ["*.txt"],
              "strategyChain": [
                {"strategyType": "ExtAddStrategy"}
              ],
              "inheritToSubfolders": false
            }
            """);

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(2, ctx.getSuccessCount(), "链应处理 txt（内置）+ json（外部策略）");
        assertTrue(Files.exists(target.resolve("a.txt")));
        assertTrue(Files.exists(target.resolve("b.json")));
        List<String> types = ctx.getProcessingResult().getOperationRecords().stream()
                .map(OperationRecord::getStrategyType).toList();
        assertTrue(types.contains("FileSameName") && types.contains("ExtAddStrategy"),
                "链中两个策略都应留下操作记录: " + types);
    }

    @Test
    void handledByExternalNotReprocessedByChain() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("chain2/update"));
        Path target = Files.createDirectories(tempDir.resolve("chain2/target"));
        Path backup = Files.createDirectories(tempDir.resolve("chain2/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        writeRule(update, """
            {
              "strategyType": "ExtAddStrategy",
              "strategyChain": [
                {"strategyType": "FileSameName", "patterns": ["*.txt"]}
              ],
              "inheritToSubfolders": false
            }
            """);

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(1, ctx.getSuccessCount(), "外部策略处理后，链中后续步骤不得重复处理");
        assertEquals(1, ctx.getProcessingResult().getOperationRecords().size());
    }

    @Test
    void externalReadsReplacementsParams() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("param/update"));
        Path target = Files.createDirectories(tempDir.resolve("param/target"));
        Path backup = Files.createDirectories(tempDir.resolve("param/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        writeRule(update, """
            {
              "strategyType": "ExtParamStrategy",
              "replacements": {"suffix": ".copy"},
              "inheritToSubfolders": false
            }
            """);

        OperationContext ctx = runUpdate(update, target, backup);

        assertTrue(Files.exists(target.resolve("a.txt.copy")), "外部策略应读到 replacements.suffix");
        assertFalse(Files.exists(target.resolve("a.txt")), "配置 suffix 时目标文件名应变化");
        assertEquals(1, ctx.getSuccessCount());
    }

    @Test
    void externalExceptionIsCaughtAndRecorded() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("boom/update"));
        Path target = Files.createDirectories(tempDir.resolve("boom/target"));
        Path backup = Files.createDirectories(tempDir.resolve("boom/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        Files.writeString(update.resolve("b.txt"), "b");
        writeRule(update, rule("ExtThrowStrategy"));

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(2, ctx.getErrorCount(), "外部策略抛异常应记失败记录，不中断整个流程");
        assertFalse(ctx.getProcessingResult().isSuccess());
        assertEquals(2, ctx.getProcessingResult().getOperationRecords().size());
        assertFalse(Files.exists(target.resolve("a.txt")), "失败操作不得写文件");
    }

    @Test
    void externalDryRunPlansWithoutTouching() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("dry/update"));
        Path target = Files.createDirectories(tempDir.resolve("dry/target"));
        Path backup = Files.createDirectories(tempDir.resolve("dry/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        writeRule(update, rule("ExtAddStrategy"));

        OperationContext ctx = runUpdate(update, target, backup, true);

        assertTrue(ctx.isDryRun());
        assertEquals(1, ctx.getSuccessCount(), "预览阶段按可执行计划计数");
        assertFalse(Files.exists(target.resolve("a.txt")), "预览不得真正写文件");
        assertFalse(Files.exists(backup.resolve("record/session-current.json")),
                "预览不得落盘会话记录");
    }

    @Test
    void externalAcceptsFilterOnlyDat() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("filter/update"));
        Path target = Files.createDirectories(tempDir.resolve("filter/target"));
        Path backup = Files.createDirectories(tempDir.resolve("filter/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        Files.writeString(update.resolve("b.dat"), "b");
        writeRule(update, rule("ExtFilterStrategy"));

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(1, ctx.getSuccessCount(), "过滤策略只处理 .dat");
        assertFalse(Files.exists(target.resolve("a.txt")));
        assertTrue(Files.exists(target.resolve("b.dat")));
    }

    @Test
    void plainExecuteExternalStrategyWorks() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("plain/update"));
        Path target = Files.createDirectories(tempDir.resolve("plain/target"));
        Path backup = Files.createDirectories(tempDir.resolve("plain/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        writeRule(update, rule("ExtPlainExecuteStrategy"));

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(1, ctx.getSuccessCount(), "裸实现 execute 的外部策略应正常执行");
        assertTrue(Files.exists(target.resolve("a.txt")));
    }

    // ---------------- matchesRules 封装（基类黑白名单） ----------------

    @Test
    void externalMatchesRulesHonorsPatterns() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("mr-patterns/update"));
        Path target = Files.createDirectories(tempDir.resolve("mr-patterns/target"));
        Path backup = Files.createDirectories(tempDir.resolve("mr-patterns/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        Files.writeString(update.resolve("b.dat"), "b");
        writeRule(update, """
            {
              "strategyType": "ExtRuleMatchStrategy",
              "patterns": ["*.txt"],
              "inheritToSubfolders": false
            }
            """);

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(1, ctx.getSuccessCount(), "matchesRules 应按 patterns 白名单过滤");
        assertTrue(Files.exists(target.resolve("a.txt")));
        assertFalse(Files.exists(target.resolve("b.dat")));
    }

    @Test
    void externalMatchesRulesHonorsExcludePatterns() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("mr-exclude/update"));
        Path target = Files.createDirectories(tempDir.resolve("mr-exclude/target"));
        Path backup = Files.createDirectories(tempDir.resolve("mr-exclude/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        Files.writeString(update.resolve("b.dat"), "b");
        writeRule(update, """
            {
              "strategyType": "ExtRuleMatchStrategy",
              "excludePatterns": ["*.dat"],
              "inheritToSubfolders": false
            }
            """);

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(1, ctx.getSuccessCount(), "matchesRules 应按黑名单排除");
        assertTrue(Files.exists(target.resolve("a.txt")));
        assertFalse(Files.exists(target.resolve("b.dat")));
    }

    @Test
    void externalMatchesRulesHonorsCaseInsensitive() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("mr-case/update"));
        Path target = Files.createDirectories(tempDir.resolve("mr-case/target"));
        Path backup = Files.createDirectories(tempDir.resolve("mr-case/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        writeRule(update, """
            {
              "strategyType": "ExtRuleMatchStrategy",
              "patterns": ["*.TXT"],
              "replacements": {"caseSensitive": "false"},
              "inheritToSubfolders": false
            }
            """);

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(1, ctx.getSuccessCount(), "caseSensitive=false 应忽略大小写");
        assertTrue(Files.exists(target.resolve("a.txt")));
    }

    @Test
    void externalMatchesRulesEmptyPatternsMatchesAll() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("mr-all/update"));
        Path target = Files.createDirectories(tempDir.resolve("mr-all/target"));
        Path backup = Files.createDirectories(tempDir.resolve("mr-all/backup"));
        Files.writeString(update.resolve("a.txt"), "a");
        Files.writeString(update.resolve("b.dat"), "b");
        writeRule(update, """
            {
              "strategyType": "ExtRuleMatchStrategy",
              "inheritToSubfolders": false
            }
            """);

        OperationContext ctx = runUpdate(update, target, backup);

        assertEquals(2, ctx.getSuccessCount(), "空白名单 = 匹配所有");
        assertTrue(Files.exists(target.resolve("a.txt")));
        assertTrue(Files.exists(target.resolve("b.dat")));
    }

    // ---------------- 辅助 ----------------

    private OperationContext runUpdate(Path updateDir, Path targetDir, Path backupDir) throws IOException {
        return run(updateDir, targetDir, backupDir, FileNode.UPDATE_OPERATION, false);
    }

    private OperationContext runUpdate(Path updateDir, Path targetDir, Path backupDir, boolean dryRun)
            throws IOException {
        return run(updateDir, targetDir, backupDir, FileNode.UPDATE_OPERATION, dryRun);
    }

    private OperationContext runDelete(Path updateDir, Path targetDir, Path backupDir) throws IOException {
        return run(updateDir, targetDir, backupDir, FileNode.DELETE_OPERATION, false);
    }

    private OperationContext run(Path updateDir, Path targetDir, Path backupDir,
                                 String[] operationType, boolean dryRun) throws IOException {
        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        // 隔离备份/会话记录写入：测试绝不触碰真实 testDic/backup
        ConfigLoader.setBackupPathForTesting(backupDir);
        OperationContext ctx = new OperationContext(config);
        ctx.setDryRun(dryRun);
        FileNode tree = FileTreeBuilder.buildTree(updateDir);
        tree.process(null, ctx, operationType);
        return ctx;
    }

    private void writeRule(Path updateDir, String json) throws IOException {
        Files.writeString(updateDir.resolve("matching-rules.json"), json, StandardCharsets.UTF_8);
    }

    private String rule(String strategyType) {
        return "{\n"
                + "  \"strategyType\": \"" + strategyType + "\",\n"
                + "  \"patterns\": [\"*\"],\n"
                + "  \"inheritToSubfolders\": false\n"
                + "}";
    }
}
