package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import com.awei.frt.service.CoreConfigWizard;
import com.awei.frt.ui.UserPrompter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心配置编写向导测试：
 * - 写入采用合并策略：保留 baseDirectory/logPath 等未管理键，更新已知键
 * - 取消（n）不写文件
 * - 缺失目录在确认后自动创建
 * - 非法日志级别回退 INFO
 */
class CoreConfigWizardTest {

    @TempDir
    Path tempDir;

    private Config saved;

    @AfterEach
    void restoreConfig() {
        if (saved != null) {
            Config c = ConfigLoader.getConfig();
            c.setBaseDirectory(saved.getBaseDirectory());
            c.setUpdatePath(saved.getUpdatePath());
            c.setTargetPath(saved.getTargetPath());
            c.setDeletePath(saved.getDeletePath());
            c.setBackupPath(saved.getBackupPath());
            c.setLogLevel(saved.getLogLevel());
        }
    }

    private Config configFor(Path base) {
        Config c = ConfigLoader.getConfig();
        saved = new Config();
        saved.setBaseDirectory(c.getBaseDirectory());
        saved.setUpdatePath(c.getUpdatePath());
        saved.setTargetPath(c.getTargetPath());
        saved.setDeletePath(c.getDeletePath());
        saved.setBackupPath(c.getBackupPath());
        saved.setLogLevel(c.getLogLevel());
        c.setBaseDirectory(base);
        return c;
    }

    @Test
    void writeMergesExistingKeysAndUpdatesValues() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
                "{\"baseDirectory\": \"X:/old\", \"logPath\": \"./logs\", \"updatePath\": \"old\", \"logLevel\": \"INFO\"}",
                StandardCharsets.UTF_8);
        Config config = configFor(tempDir);

        boolean ok = new CoreConfigWizard(config, prompter("y"), configFile)
                .writeFromValues(Paths.get("update"), Paths.get("THtest"),
                        Paths.get("delete"), Paths.get("backup"), "DEBUG");

        assertTrue(ok, "应成功写入");
        String json = Files.readString(configFile);
        assertTrue(json.contains("\"updatePath\" : \"update\""), "updatePath 应更新: " + json);
        assertTrue(json.contains("\"targetPath\" : \"THtest\""), "targetPath 应写入: " + json);
        assertTrue(json.contains("\"logLevel\" : \"DEBUG\""), "logLevel 应更新: " + json);
        assertTrue(json.contains("\"baseDirectory\" : \"X:/old\""), "baseDirectory 应保留: " + json);
        assertTrue(json.contains("\"logPath\" : \"./logs\""), "logPath 应保留: " + json);
    }

    @Test
    void cancelLeavesFileUntouched() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        String original = "{\"updatePath\": \"old\", \"logLevel\": \"INFO\"}";
        Files.writeString(configFile, original, StandardCharsets.UTF_8);
        Config config = configFor(tempDir);

        boolean ok = new CoreConfigWizard(config, prompter("n"), configFile)
                .writeFromValues(Paths.get("update"), Paths.get("THtest"),
                        Paths.get("delete"), Paths.get("backup"), "DEBUG");

        assertFalse(ok, "取消后不应写入");
        assertEquals(original, Files.readString(configFile), "文件应保持原样");
    }

    @Test
    void missingDirsAreCreatedOnWrite() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{}", StandardCharsets.UTF_8);
        Config config = configFor(tempDir);
        Path newTarget = tempDir.resolve("sub/deep/target");

        boolean ok = new CoreConfigWizard(config, prompter("y"), configFile)
                .writeFromValues(Paths.get("update"), tempDir.relativize(newTarget),
                        Paths.get("delete"), Paths.get("backup"), "INFO");

        assertTrue(ok);
        assertTrue(Files.isDirectory(newTarget), "确认后应自动创建缺失目录");
    }

    @Test
    void invalidLogLevelFallsBackToInfo() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{}", StandardCharsets.UTF_8);
        Config config = configFor(tempDir);

        new CoreConfigWizard(config, prompter("y"), configFile)
                .writeFromValues(Paths.get("update"), Paths.get("THtest"),
                        Paths.get("delete"), Paths.get("backup"), "FOO");

        String json = Files.readString(configFile);
        assertTrue(json.contains("\"logLevel\" : \"INFO\""), "非法日志级别应回退 INFO: " + json);
    }

    private UserPrompter prompter(String answer) {
        return () -> answer;
    }
}
