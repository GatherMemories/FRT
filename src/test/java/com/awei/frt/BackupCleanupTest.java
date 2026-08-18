package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 孤立备份文件清理（P2）测试：
 * - 被操作记录引用的备份文件不算孤立
 * - 未被任何记录引用的备份文件被识别为孤立并可删除
 */
class BackupCleanupTest {

    @TempDir
    Path tempDir;

    @Test
    void findOrphanBackupFilesOnlyReturnsUnreferenced() throws IOException {
        Path backup = Files.createDirectories(tempDir.resolve("backup"));
        Files.createDirectories(backup.resolve("record"));

        // 被记录引用的备份文件
        Path referenced = backup.resolve("referenced.bak");
        Files.writeString(referenced, "referenced-content");
        String refMd5 = FileSignUtil.getFileMd5(referenced);

        // 孤立备份文件（无任何记录引用）
        Path orphan = backup.resolve("orphan.bak");
        Files.writeString(orphan, "orphan-content");
        String orphanMd5 = FileSignUtil.getFileMd5(orphan);

        // 用临时目录填充备份索引
        BackupFileLoader.loadBackupFiles(backup);
        try {
            // 构造引用 referenced 的操作记录
            OperationRecord record = new OperationRecord();
            record.setSourceFileSign(refMd5);
            record.setTargetFileSign("unrelated-md5-00000000000000000000000000000000");
            ProcessingResult result = new ProcessingResult();
            result.setOperationRecords(List.of(record));

            List<Path> orphans = BackupFileLoader.findOrphanBackupFiles(Map.of("test-record", result));

            assertTrue(orphans.contains(orphan), "未被引用的备份文件应识别为孤立");
            assertFalse(orphans.contains(referenced), "被记录引用的备份文件不应算孤立");

            // 删除孤立文件生效
            assertTrue(BackupFileLoader.deleteBackupFile(orphan), "孤立备份文件应可删除");
            assertFalse(Files.exists(orphan), "孤立文件应已被删除");
            // 被引用的文件不受影响
            assertTrue(Files.exists(referenced));
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
