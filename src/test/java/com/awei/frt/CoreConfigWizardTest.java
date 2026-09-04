package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import com.awei.frt.service.CoreConfigWizard;
import com.awei.frt.interaction.UserPrompter;
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
 * - 保存成功后在 config.json 同目录记路径历史 sidecar（仅实际变更字段、最近优先去重；取消不记）
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

    // ---------------- 路径历史记录（config-history.json sidecar） ----------------

    @Test
    void successfulSaveRecordsActuallyChangedPathsToSidecar() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        // 四个键都给足"现值"，基准完全由文件决定（不依赖进程级单例残留值，测试顺序无关）；
        // 本次仅 updatePath 实际变更，其余字段写入值与现值相同（等价留空保留）
        Files.writeString(configFile,
                "{\"updatePath\": \"old-update\", \"targetPath\": \"THtest\", \"deletePath\": \"delete\", "
                        + "\"backupPath\": \"backup\", \"logLevel\": \"INFO\"}", StandardCharsets.UTF_8);
        Config config = configFor(tempDir);

        boolean ok = new CoreConfigWizard(config, prompter("y"), configFile)
                .writeFromValues(Paths.get("new-update"), Paths.get("THtest"),
                        Paths.get("delete"), Paths.get("backup"), "INFO");

        assertTrue(ok, "应成功写入");
        Path historyFile = tempDir.resolve("config-history.json");
        assertTrue(Files.exists(historyFile), "保存成功应产生同目录 sidecar 历史文件");
        String json = Files.readString(historyFile, StandardCharsets.UTF_8);
        assertTrue(json.contains("new-update"), "实际变更的 updatePath 应入史: " + json);
        assertFalse(json.contains("old-update"), "被替换掉的旧值不应入史: " + json);
        assertFalse(json.contains("THtest"), "值未变化的 targetPath 不应入史: " + json);
        assertFalse(json.contains("delete"), "留空保留语义的字段不应入史: " + json);
    }

    @Test
    void allValuesUnchangedLeavesNoHistoryFile() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        // 文件现值与本次写入值全部相同（等同表单全部留空保留），不应产生任何历史记录
        Files.writeString(configFile,
                "{\"updatePath\": \"update\", \"targetPath\": \"THtest\", \"deletePath\": \"delete\", "
                        + "\"backupPath\": \"backup\", \"logLevel\": \"INFO\"}", StandardCharsets.UTF_8);
        Config config = configFor(tempDir);

        boolean ok = new CoreConfigWizard(config, prompter("y"), configFile)
                .writeFromValues(Paths.get("update"), Paths.get("THtest"),
                        Paths.get("delete"), Paths.get("backup"), "INFO");

        assertTrue(ok, "留空保留也应正常保存成功");
        assertFalse(Files.exists(tempDir.resolve("config-history.json")),
                "值全部未变化时不应产生任何历史记录文件");
    }

    @Test
    void historyIsRecentFirstAndDedupedAcrossSaves() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
                "{\"updatePath\": \"u1\", \"targetPath\": \"THtest\", \"deletePath\": \"delete\", "
                        + "\"backupPath\": \"backup\", \"logLevel\": \"INFO\"}", StandardCharsets.UTF_8);
        Config config = configFor(tempDir);
        CoreConfigWizard wizard = new CoreConfigWizard(config, prompter("y"), configFile);

        assertTrue(wizard.writeFromValues(Paths.get("u2"), Paths.get("THtest"),
                Paths.get("delete"), Paths.get("backup"), "INFO"));
        // 第二次切回 u1：应去重置顶到最近使用位置
        assertTrue(wizard.writeFromValues(Paths.get("u1"), Paths.get("THtest"),
                Paths.get("delete"), Paths.get("backup"), "INFO"));

        String json = Files.readString(tempDir.resolve("config-history.json"), StandardCharsets.UTF_8);
        int first = json.indexOf("u1");
        int second = json.indexOf("u2");
        assertTrue(first >= 0 && second >= 0 && first < second,
                "最近一次使用的 u1 应排在 u2 之前: " + json);
    }

    @Test
    void cancelledWriteDoesNotCreateHistorySidecar() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{\"updatePath\": \"old\", \"logLevel\": \"INFO\"}",
                StandardCharsets.UTF_8);
        Config config = configFor(tempDir);

        boolean ok = new CoreConfigWizard(config, prompter("n"), configFile)
                .writeFromValues(Paths.get("new-update"), Paths.get("THtest"),
                        Paths.get("delete"), Paths.get("backup"), "INFO");

        assertFalse(ok, "取消后不应写入");
        assertFalse(Files.exists(tempDir.resolve("config-history.json")),
                "取消保存不应产生历史文件");
    }

    private UserPrompter prompter(String answer) {
        return () -> answer;
    }
}
