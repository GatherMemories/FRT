package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.service.FileDeleteService;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.ui.UserPrompter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 更新/删除预览（dryRun + 二次确认）测试：
 * - 取消（n）：预览后不执行任何文件操作
 * - 确认（y）：真正执行
 */
class PreviewTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    @Test
    void updatePreviewCancelDoesNothing() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = prepareUpdateDir();
        Path targetDir = prepareTargetDir();

        Config config = configFor(updateDir, targetDir, tempDir.resolve("delete"));
        ProcessingResult result = new FileUpdateServiceNew(config, prompter("n")).updateExecute();

        assertFalse(Files.exists(targetDir.resolve("a.txt")), "取消后不应写入目标文件");
        assertTrue(Files.exists(targetDir.resolve("b.txt")), "已有文件不受影响");
    }

    @Test
    void updatePreviewConfirmExecutes() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = prepareUpdateDir();
        Path targetDir = prepareTargetDir();

        Config config = configFor(updateDir, targetDir, tempDir.resolve("delete"));
        ProcessingResult result = new FileUpdateServiceNew(config, prompter("y")).updateExecute();

        assertTrue(Files.exists(targetDir.resolve("a.txt")), "确认后应写入目标文件");
        assertTrue(result.getSuccessCount() >= 1);
    }

    @Test
    void deletePreviewCancelDoesNothing() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = prepareUpdateDir();
        Path targetDir = prepareTargetDir();
        // 删除目录：规则匹配全部，目标 b.txt 将被计划删除
        Path deleteDir = Files.createDirectories(tempDir.resolve("delete"));
        Files.writeString(deleteDir.resolve("b.txt"), "placeholder");
        Files.writeString(deleteDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[],\"inheritToSubfolders\":false}",
                StandardCharsets.UTF_8);

        Config config = configFor(updateDir, targetDir, deleteDir);
        ProcessingResult result = new FileDeleteService(config, prompter("n")).deleteExecute();

        assertTrue(Files.exists(targetDir.resolve("b.txt")), "取消后不应删除目标文件");
    }

    @Test
    void deletePreviewConfirmExecutes() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = prepareUpdateDir();
        Path targetDir = prepareTargetDir();
        Path deleteDir = Files.createDirectories(tempDir.resolve("delete"));
        Files.writeString(deleteDir.resolve("b.txt"), "placeholder");
        Files.writeString(deleteDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[],\"inheritToSubfolders\":false}",
                StandardCharsets.UTF_8);

        Config config = configFor(updateDir, targetDir, deleteDir);
        ProcessingResult result = new FileDeleteService(config, prompter("y")).deleteExecute();

        assertFalse(Files.exists(targetDir.resolve("b.txt")), "确认后应删除目标文件");
    }

    // ---------------- 辅助 ----------------

    private Path prepareUpdateDir() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Files.writeString(updateDir.resolve("a.txt"), "hello");
        Files.writeString(updateDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[\"*.txt\"],\"inheritToSubfolders\":false}",
                StandardCharsets.UTF_8);
        return updateDir;
    }

    private Path prepareTargetDir() throws IOException {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(targetDir.resolve("b.txt"), "existing");
        return targetDir;
    }

    private Config configFor(Path updateDir, Path targetDir, Path deleteDir) {
        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        config.setDeletePath(deleteDir.toAbsolutePath());
        return config;
    }

    private UserPrompter prompter(String answer) {
        return () -> answer;
    }
}
