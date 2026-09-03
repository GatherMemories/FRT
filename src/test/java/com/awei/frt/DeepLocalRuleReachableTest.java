package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.interaction.UserPrompter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试（审查 H4）：父层文件夹无规则（未开启继承）时，不得剪枝整棵子树——
 * 深层子目录自带的本地规则文件必须仍然可达并生效。
 *
 * 旧实现：prepareAndScheduleProcess 在"无有效规则"时直接 return，其下所有子目录
 * （哪怕自带 matching-rules.json）都不会被遍历 → 深层规则静默失效、文件永不同步。
 */
class DeepLocalRuleReachableTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    @Test
    void deepFolderWithOwnRuleIsProcessedEvenWhenAncestorsHaveNoRule() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        // 中间层目录无规则、不继承——旧实现会在此剪枝整棵子树
        Path middleDir = Files.createDirectories(updateDir.resolve("middle"));
        // 深层子目录自带本地规则
        Path deepDir = Files.createDirectories(middleDir.resolve("deep"));
        Files.writeString(deepDir.resolve("mod.dat"), "payload");
        Files.writeString(deepDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[\"*.dat\"]}",
                StandardCharsets.UTF_8);

        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Path deleteDir = Files.createDirectories(tempDir.resolve("delete"));

        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        config.setDeletePath(deleteDir.toAbsolutePath());

        new FileUpdateServiceNew(config, (UserPrompter) () -> "y").updateExecute();

        assertTrue(Files.exists(targetDir.resolve("middle/deep/mod.dat")),
                "深层目录自带本地规则时，其文件应被同步到目标目录（父层无规则不得剪枝子树）");
        assertEquals("payload", Files.readString(targetDir.resolve("middle/deep/mod.dat")));
    }
}
