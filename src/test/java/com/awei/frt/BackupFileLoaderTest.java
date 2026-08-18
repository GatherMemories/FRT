package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BackupFileLoaderTest {

    @Test
    public void test() {

        Map<String, Path> backupFiles = BackupFileLoader.getBackupFiles();
        System.out.println(backupFiles);

        Path recordPath = Path.of("C:\\Users\\5454564546\\Desktop\\FRT\\backup\\record\\backup-20260131-184239.json");
        ProcessingResult processingResult = BackupFileLoader.loadOperationRecord(recordPath.getFileName().toString());
        System.out.println(processingResult);
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
            // 恢复真实备份索引，避免影响其他测试
            BackupFileLoader.loadBackupFiles(ConfigLoader.getBackupPath());
        }
    }
}
