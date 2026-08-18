package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.FileTreeBuilder;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.Config;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多策略组合链（功能升级1）测试：
 * - 链中后续策略只处理前序策略"剩余"的文件
 * - 已处理（handled）的文件不会被重复处理
 * - 未配置链时保持旧版单策略行为
 */
class StrategyChainTest {

    @Test
    void chainProcessesRemainingFiles() throws IOException {
        Path base = Files.createTempDirectory("chain-test");
        try {
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            Files.writeString(updateDir.resolve("a.txt"), "a");
            Files.writeString(updateDir.resolve("b.json"), "{}");

            String ruleJson = """
                {
                  "strategyChain": [
                    {"strategyType": "FileSameName", "patterns": ["*.txt"]},
                    {"strategyType": "FileSameName", "patterns": ["*.json"]}
                  ],
                  "inheritToSubfolders": false
                }
                """;
            Files.writeString(updateDir.resolve("matching-rules.json"), ruleJson, StandardCharsets.UTF_8);

            OperationContext ctx = runUpdate(updateDir, targetDir);

            assertEquals(2, ctx.getProcessingResult().getSuccessCount(), "链应处理 txt 与 json 两个文件");
            assertTrue(Files.exists(targetDir.resolve("a.txt")));
            assertTrue(Files.exists(targetDir.resolve("b.json")));
        } finally {
            deleteRecursively(base);
        }
    }

    @Test
    void chainDoesNotDoubleProcessHandledFile() throws IOException {
        Path base = Files.createTempDirectory("chain-test2");
        try {
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            Files.writeString(updateDir.resolve("a.txt"), "a");

            // 两个步骤模式重叠：第二步不应重复处理已被第一步处理的 a.txt
            String ruleJson = """
                {
                  "strategyChain": [
                    {"strategyType": "FileSameName", "patterns": ["*.txt"]},
                    {"strategyType": "FileSameName", "patterns": ["*.txt"]}
                  ],
                  "inheritToSubfolders": false
                }
                """;
            Files.writeString(updateDir.resolve("matching-rules.json"), ruleJson, StandardCharsets.UTF_8);

            OperationContext ctx = runUpdate(updateDir, targetDir);

            assertEquals(1, ctx.getProcessingResult().getSuccessCount(), "同一文件不应被链重复处理");
            assertEquals(1, ctx.getProcessingResult().getOperationRecords().size());
        } finally {
            deleteRecursively(base);
        }
    }

    @Test
    void legacySingleStrategyStillWorks() throws IOException {
        Path base = Files.createTempDirectory("chain-test3");
        try {
            Path updateDir = Files.createDirectories(base.resolve("update"));
            Path targetDir = Files.createDirectories(base.resolve("target"));
            Files.writeString(updateDir.resolve("a.txt"), "a");
            Files.writeString(updateDir.resolve("b.json"), "{}");

            String ruleJson = """
                {
                  "strategyType": "FileSameName",
                  "patterns": ["*.txt"],
                  "inheritToSubfolders": false
                }
                """;
            Files.writeString(updateDir.resolve("matching-rules.json"), ruleJson, StandardCharsets.UTF_8);

            OperationContext ctx = runUpdate(updateDir, targetDir);

            assertEquals(1, ctx.getProcessingResult().getSuccessCount(), "旧版单策略只处理 *.txt");
            assertTrue(Files.exists(targetDir.resolve("a.txt")));
            assertFalse(Files.exists(targetDir.resolve("b.json")));
        } finally {
            deleteRecursively(base);
        }
    }

    // ---------------- 辅助 ----------------

    private OperationContext runUpdate(Path updateDir, Path targetDir) {
        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        OperationContext ctx = new OperationContext(config);

        FileNode tree = FileTreeBuilder.buildTree(updateDir);
        tree.process(null, ctx, FileNode.UPDATE_OPERATION);
        return ctx;
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
