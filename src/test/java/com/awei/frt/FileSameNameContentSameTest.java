package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FileSameName 策略 onlyIfContentSame 参数测试：
 * - true 且源与目标内容（MD5）相同 → 跳过替换（计入跳过，不产生操作记录）
 * - true 且内容不同 → 正常替换
 * - 默认（false）即使内容相同也正常替换
 */
class FileSameNameContentSameTest {

    @TempDir
    Path tempDir;

    private Config saved;

    @AfterEach
    void restoreConfig() {
        if (saved != null) {
            Config c = ConfigLoader.getConfig();
            c.setUpdatePath(saved.getUpdatePath());
            c.setTargetPath(saved.getTargetPath());
        }
        // 恢复真实备份路径（本类直接 process 触发真实替换/备份，需隔离；审查 B-4 实测污染 testDic/backup）
        TestSupport.restoreBackupPath();
    }

    @Test
    void contentSameWithFlagSkipsReplace() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(updateDir.resolve("a.txt"), "same-content", StandardCharsets.UTF_8);
        Files.writeString(targetDir.resolve("a.txt"), "same-content", StandardCharsets.UTF_8);
        writeRule(updateDir, "true");

        OperationContext ctx = runUpdate(updateDir, targetDir);

        assertEquals(0, ctx.getSuccessCount(), "内容相同应跳过替换，不产生成功操作");
        assertEquals(1, ctx.getSkipCount(), "内容相同应计入跳过");
        assertEquals(0, ctx.getProcessingResult().getOperationRecords().size(), "跳过不应产生操作记录");
        assertEquals("same-content", Files.readString(targetDir.resolve("a.txt")), "目标文件应保持原样");
    }

    @Test
    void contentDifferentWithFlagReplaces() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(updateDir.resolve("a.txt"), "new-content", StandardCharsets.UTF_8);
        Files.writeString(targetDir.resolve("a.txt"), "old-content", StandardCharsets.UTF_8);
        writeRule(updateDir, "true");

        OperationContext ctx = runUpdate(updateDir, targetDir);

        assertEquals(1, ctx.getSuccessCount(), "内容不同应正常替换");
        assertEquals(0, ctx.getSkipCount());
        assertEquals("new-content", Files.readString(targetDir.resolve("a.txt")), "目标文件应更新为新内容");
    }

    @Test
    void contentSameWithoutFlagReplaces() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(updateDir.resolve("a.txt"), "same-content", StandardCharsets.UTF_8);
        Files.writeString(targetDir.resolve("a.txt"), "same-content", StandardCharsets.UTF_8);
        // 不配置 onlyIfContentSame（默认 false）：即使内容相同也执行替换
        Files.writeString(updateDir.resolve("matching-rules.json"),
                """
                {
                  "strategyType": "FileSameName",
                  "patterns": ["*.txt"],
                  "inheritToSubfolders": false
                }
                """, StandardCharsets.UTF_8);

        OperationContext ctx = runUpdate(updateDir, targetDir);

        assertEquals(1, ctx.getSuccessCount(), "默认行为应照常替换");
        assertEquals(0, ctx.getSkipCount());
    }

    // ---------------- 辅助 ----------------

    private void writeRule(Path updateDir, String onlyIfContentSame) throws IOException {
        Files.writeString(updateDir.resolve("matching-rules.json"),
                """
                {
                  "strategyType": "FileSameName",
                  "patterns": ["*.txt"],
                  "replacements": {"onlyIfContentSame": "%s"},
                  "inheritToSubfolders": false
                }
                """.formatted(onlyIfContentSame), StandardCharsets.UTF_8);
    }

    private OperationContext runUpdate(Path updateDir, Path targetDir) {
        // 真实替换会写备份与会话记录：备份路径隔离到 @TempDir，避免污染真实 testDic/backup
        TestSupport.isolateBackup(tempDir);
        Config config = ConfigLoader.getConfig();
        saved = new Config();
        saved.setUpdatePath(config.getUpdatePath());
        saved.setTargetPath(config.getTargetPath());
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        OperationContext ctx = new OperationContext(config);
        FileNode tree = FileTreeBuilder.buildTree(updateDir);
        tree.process(null, ctx, FileNode.UPDATE_OPERATION);
        return ctx;
    }
}
