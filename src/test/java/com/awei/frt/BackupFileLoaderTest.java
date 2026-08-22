package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.ProcessingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BackupFileLoaderTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    @Test
    public void loadMissingRecordReturnsNull() {
        // 容错：加载不存在的备份记录应返回 null 而不是抛异常（原测试硬编码了 Windows 绝对路径，Linux 上形同虚设）
        ProcessingResult processingResult = BackupFileLoader.loadOperationRecord("backup-not-exist-00000000-000000");
        assertNull(processingResult, "加载不存在的备份记录应返回 null");
    }

    /**
     * 回归测试：更新/删除产生新备份后，getOperationRecordFiles 必须刷新看到新记录
     * （原实现缓存非空后不再重新加载，新备份在恢复菜单中查找不到——用户实测反馈）
     */
    @Test
    public void newBackupRecordAppearsOnRefresh() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path recordDir = ConfigLoader.getBackupPath().resolve("record");
            Files.createDirectories(recordDir);
            // 先放一条旧记录并加载（让缓存非空）
            Files.writeString(recordDir.resolve("backup-old.json"),
                    recordJson("2026-08-22T10:00:00"), StandardCharsets.UTF_8);
            assertTrue(BackupFileLoader.getOperationRecordFiles().containsKey("backup-old.json"),
                    "旧记录应能加载");

            // 模拟更新/删除后产生的新备份记录
            Files.writeString(recordDir.resolve("backup-new-test.json"),
                    recordJson("2026-08-22T13:00:00"), StandardCharsets.UTF_8);

            Map<String, ProcessingResult> refreshed = BackupFileLoader.getOperationRecordFiles();
            assertTrue(refreshed.containsKey("backup-new-test.json"),
                    "新备份记录应在再次获取时可见（备份功能列表需刷新，而非返回旧缓存）");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    private String recordJson(String resultTime) {
        return "{\"resultTime\":\"" + resultTime + "\",\"successCount\":1,\"skipCount\":0,\"errorCount\":0,"
                + "\"operationRecords\":[],\"success\":true,\"resultPath\":null}";
    }

    /**
     * 回归测试：Windows 上生成的历史备份记录（路径含反斜杠）应能正常反序列化，
     * 不再抛 Jackson "Bad escape"（默认 NioPathDeserializer 把路径当 URI 解析导致崩溃）
     */
    @Test
    public void loadWindowsPathRecordDoesNotThrow() throws IOException {
        // 模拟 Windows 平台生成的历史备份记录 JSON（反斜杠路径）
        String json = "{\"resultTime\":\"2026-02-22T00:15:00\",\"successCount\":1,\"skipCount\":0,\"errorCount\":0,"
                + "\"operationRecords\":[{\"strategyType\":\"McMod\",\"operationType\":\"operation_replace\","
                + "\"sourcePath\":\"C:\\\\Users\\\\5454564546\\\\Desktop\\\\update\\\\mod.jar\","
                + "\"targetPath\":\"C:\\\\Users\\\\5454564546\\\\Desktop\\\\THtest\\\\mod.jar\","
                + "\"sourceFileSign\":\"abc\",\"targetFileSign\":\"def\",\"timestamp\":\"2026-02-22T00:15:00\","
                + "\"success\":true,\"errorMessage\":null}],\"success\":true,\"resultPath\":null}";
        Path recordFile = ConfigLoader.getBackupPath().resolve("record").resolve("backup-win-test.json");
        Files.writeString(recordFile, json);
        try {
            ProcessingResult result = BackupFileLoader.loadOperationRecord("backup-win-test.json");
            assertNotNull(result, "含 Windows 路径的历史记录应能加载");
            assertEquals(1, result.getOperationRecords().size());
            assertNotNull(result.getOperationRecords().get(0).getSourcePath(), "sourcePath 应被解析为 Path 对象");
            assertTrue(result.getOperationRecords().get(0).getSourcePath().toString().contains("Desktop"),
                    "sourcePath 应保留原路径内容");
        } finally {
            Files.deleteIfExists(recordFile);
        }
    }

    /**
     * 回归测试：getBackupFiles 判空条件修复（原实现误检查 operationRecordFiles，
     * 且 loadBackupFiles 返回 null 时会把静态字段置 null 导致后续 NPE）
     */
    @Test
    public void testGetBackupFilesReturnsNotNullAndRepeatable() {
        Map<String, Path> backupFiles = BackupFileLoader.getBackupFiles();
        assertNotNull(backupFiles, "getBackupFiles 不应返回 null");

        // 重复调用不应抛异常（修复点：判空条件检查 backupFiles 本身）
        Map<String, Path> again = BackupFileLoader.getBackupFiles();
        assertNotNull(again, "重复调用 getBackupFiles 不应返回 null");
    }

    /**
     * 回归测试：备份文件索引不应包含 backup/record/ 下的记录 JSON（索引污染修复）
     */
    @Test
    public void loadBackupFilesExcludesRecordDir() throws IOException {
        Path temp = Files.createTempDirectory("backup-index-test");
        try {
            Path recordDir = Files.createDirectories(temp.resolve("record"));
            Path recordJson = recordDir.resolve("backup-20260101-000000.json");
            Files.writeString(recordJson, "{}");
            Path sessionJson = recordDir.resolve("session-current.json");
            Files.writeString(sessionJson, "{}");
            // 真正的备份文件
            Path realBackup = temp.resolve("some-file.bak");
            Files.writeString(realBackup, "content");

            Map<String, Path> files = BackupFileLoader.loadBackupFiles(temp);
            assertNotNull(files);
            assertTrue(files.containsValue(realBackup), "真实备份文件应入索引");
            assertFalse(files.containsValue(recordJson), "backup/record/*.json 不应入索引");
            assertFalse(files.containsValue(sessionJson), "会话临时 json 不应入索引");
        } finally {
            // 恢复真实备份索引，避免影响其他测试（先初始化 ConfigLoader，避免静态字段未初始化 NPE）
            ConfigLoader.getConfig();
            Path realBackup = ConfigLoader.getBackupPath();
            if (realBackup != null) {
                BackupFileLoader.loadBackupFiles(realBackup);
            }
        }
    }
}
