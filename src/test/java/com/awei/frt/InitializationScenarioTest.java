package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;
import com.awei.frt.service.FileUpdateServiceNew;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一次初始化场景端到端测试（用户实测反馈）：
 * 目标目录不存在 → 更新时自动创建 → 全部为新增操作（备份目录无旧文件）
 * → 恢复应成功（不依赖备份文件、不报"备份文件列表为空"）
 */
class InitializationScenarioTest {

    @TempDir
    Path tempDir;

    private Config saved;

    @AfterEach
    void restoreAll() {
        if (saved != null) {
            Config c = ConfigLoader.getConfig();
            c.setBaseDirectory(saved.getBaseDirectory());
            c.setUpdatePath(saved.getUpdatePath());
            c.setTargetPath(saved.getTargetPath());
            c.setDeletePath(saved.getDeletePath());
            c.setBackupPath(saved.getBackupPath());
        }
        TestSupport.restoreBackupPath();
    }

    @Test
    void firstInitializationUpdateCreatesTargetAndRestoreSucceeds() throws IOException {
        TestSupport.isolateBackup(tempDir);
        // 准备：update 目录有文件 + 规则，target/delete/backup 目录不存在（首次初始化）
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Files.writeString(updateDir.resolve("a.txt"), "hello", StandardCharsets.UTF_8);
        Files.writeString(updateDir.resolve("b.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(updateDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[]}", StandardCharsets.UTF_8);

        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));
        config.setTargetPath(Path.of("THtest")); // 不存在
        config.setDeletePath(Path.of("delete")); // 不存在
        config.setBackupPath(Path.of("backup")); // 不存在

        // 更新（目标目录不存在 → 应自动创建 + 全新增）
        ProcessingResult r = new FileUpdateServiceNew(config, () -> "y").updateExecute();
        assertTrue(Files.isDirectory(tempDir.resolve("THtest")), "目标目录应自动创建");
        assertTrue(Files.exists(tempDir.resolve("THtest/a.txt")), "a.txt 应被新增");
        assertTrue(Files.exists(tempDir.resolve("THtest/b.json")), "b.json 应被新增");
        assertTrue(r.getSuccessCount() >= 2, "应全部为新增");

        // 备份记录存在且为纯新增（备份目录无旧文件）
        Map<String, ProcessingResult> records = BackupFileLoader.getOperationRecordFiles();
        ProcessingResult latest = records.values().stream()
                .max(java.util.Comparator.comparing(ProcessingResult::getResultTime))
                .orElseThrow(() -> new AssertionError("应产生备份记录"));

        // 恢复应成功（纯新增回滚=删除目标文件，不依赖备份文件）
        RestoreResult rr = BackupFileLoader.restoreFromResult(latest, () -> "n");
        assertTrue(rr.isFullSuccess(), "纯新增备份恢复应成功，不报'备份文件列表为空'");
        assertFalse(Files.exists(tempDir.resolve("THtest/a.txt")), "新增文件应被恢复（删除）");
        assertFalse(Files.exists(tempDir.resolve("THtest/b.json")), "新增文件应被恢复（删除）");
    }

    private Config snapshot(Config c) {
        Config s = new Config();
        s.setBaseDirectory(c.getBaseDirectory());
        s.setUpdatePath(c.getUpdatePath());
        s.setTargetPath(c.getTargetPath());
        s.setDeletePath(c.getDeletePath());
        s.setBackupPath(c.getBackupPath());
        return s;
    }
}
