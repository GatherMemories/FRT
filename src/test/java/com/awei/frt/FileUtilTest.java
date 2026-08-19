package com.awei.frt;

import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.OperationRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileUtil 文件操作测试（原为空壳 main，补为真实 JUnit 测试）
 */
class FileUtilTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    @Test
    void addFileCopiesAndAutoCreatesParentDir() throws IOException {
        Path src = tempDir.resolve("src.txt");
        Path target = tempDir.resolve("sub").resolve("dst.txt"); // 父目录不存在，应自动创建
        Files.writeString(src, "hello");

        OperationRecord record = new OperationRecord();
        boolean ok = FileUtil.addFile(src, target, record);

        assertTrue(ok, "新增应成功");
        assertTrue(Files.exists(target), "目标文件应存在");
        assertEquals("hello", Files.readString(target));
        assertTrue(record.isSuccess());
    }

    @Test
    void addFileFailsWhenTargetExists() throws IOException {
        Path src = tempDir.resolve("src.txt");
        Path target = tempDir.resolve("dst.txt");
        Files.writeString(src, "hello");
        Files.writeString(target, "already");

        OperationRecord record = new OperationRecord();
        boolean ok = FileUtil.addFile(src, target, record);

        assertFalse(ok, "目标已存在时新增应失败");
        assertFalse(record.isSuccess());
    }

    @Test
    void deleteFileBacksUpAndDeletes() throws IOException {
        // 备份路径隔离到临时目录，避免测试污染真实 testDic/backup
        TestSupport.isolateBackup(tempDir);
        Path file = tempDir.resolve("del.txt");
        Files.writeString(file, "to delete");

        OperationRecord record = new OperationRecord();
        boolean ok = FileUtil.deleteFile(file, record);

        assertTrue(ok, "删除应成功");
        assertFalse(Files.exists(file), "文件应已被删除");
        assertTrue(record.isSuccess());
    }
}
