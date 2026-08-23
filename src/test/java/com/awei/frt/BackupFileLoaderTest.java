package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

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

    /**
     * 回归：第一次初始化场景（目标目录为空 → 全是新增操作 → 备份目录无旧文件）。
     * 恢复纯 ADD 备份只需删除目标文件、不依赖备份文件——曾因"备份文件列表为空"
     * 被直接判失败，用户实测"第一次使用备份出现没有找到旧文件失败"。
     */
    @Test
    public void restoreAddOnlyBackupWorksWithoutBackupFiles() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path targetDir = Files.createDirectories(tempDir.resolve("THtest"));
            Path targetFile = targetDir.resolve("a.txt");
            Files.writeString(targetFile, "new content", StandardCharsets.UTF_8);

            ProcessingResult result = new ProcessingResult();
            OperationRecord record = new OperationRecord();
            record.setStrategyType("FileSameName");
            record.setOperationType(OperationContext.OPERATION_ADD);
            record.setTargetPath(targetFile);
            record.setSuccess(true);
            result.addOperationRecord(record);
            // 不创建任何备份文件（模拟纯新增无旧文件可备份）

            RestoreResult rr = BackupFileLoader.restoreFromResult(result, () -> "n");
            assertTrue(rr.isFullSuccess(), "纯新增备份恢复应成功（不依赖备份文件）");
            assertFalse(Files.exists(targetFile), "新增的目标文件应被恢复（删除）");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    /**
     * 回归：恢复 ADD 时目标文件已被用户修改（MD5 与操作记录不一致）→ 跳过删除，避免丢失改动
     */
    @Test
    public void restoreAddSkipsModifiedTarget() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path targetDir = Files.createDirectories(tempDir.resolve("THtest"));
            Path targetFile = targetDir.resolve("a.txt");
            Files.writeString(targetFile, "user modified content", StandardCharsets.UTF_8);

            ProcessingResult result = new ProcessingResult();
            OperationRecord record = new OperationRecord();
            record.setStrategyType("FileSameName");
            record.setOperationType(OperationContext.OPERATION_ADD);
            record.setTargetPath(targetFile);
            // 模拟"当时新增"的源文件 MD5 与当前目标内容不同（用户改过）
            record.setSourceFileSign("00000000000000000000000000000000");
            record.setSuccess(true);
            result.addOperationRecord(record);

            RestoreResult rr = BackupFileLoader.restoreFromResult(result, () -> "y"); // 回答"跳过"
            assertFalse(rr.isFullSuccess(), "目标被修改且用户选择跳过，不算全成功");
            assertTrue(Files.exists(targetFile), "被修改的文件应保留（用户选择跳过）");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    /**
     * 回归：恢复 ADD 时目标按记录名不存在，但父目录存在 MD5 相同的文件（被改名）——
     * 应检测并询问用户：y=跳过保留，回车=删除改名文件
     */
    @Test
    public void restoreAddDetectsRenamedFileAndAsks() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path targetDir = Files.createDirectories(tempDir.resolve("THtest"));
            Path targetFile = targetDir.resolve("a.txt"); // 记录名（不存在，被改名）
            Path renamedFile = targetDir.resolve("b.txt"); // 改名后的文件（内容相同）
            Files.writeString(renamedFile, "same content", StandardCharsets.UTF_8);
            String md5 = FileSignUtil.getFileMd5(renamedFile);

            ProcessingResult result = new ProcessingResult();
            OperationRecord record = new OperationRecord();
            record.setStrategyType("FileSameName");
            record.setOperationType(OperationContext.OPERATION_ADD);
            record.setTargetPath(targetFile);
            record.setSourceFileSign(md5); // 与改名文件 MD5 相同
            record.setSuccess(true);
            result.addOperationRecord(record);

            // 回答 y（跳过保留）→ 改名文件保留
            RestoreResult rrSkip = BackupFileLoader.restoreFromResult(result, () -> "y");
            assertFalse(rrSkip.isFullSuccess(), "发现改名文件且用户选择跳过，不算全成功");
            assertTrue(Files.exists(renamedFile), "选择跳过时改名文件应保留");

            // 回车（删除改名文件）→ b.txt 被删
            RestoreResult rrDel = BackupFileLoader.restoreFromResult(result, () -> "");
            assertTrue(rrDel.isFullSuccess(), "回车=删除改名文件，应全成功");
            assertFalse(Files.exists(renamedFile), "回车时应删除改名文件");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    /**
     * 备份数量淘汰：超过上限（20）自动删最旧；固定（pinned）的记录不受淘汰影响
     */
    @Test
    public void trimBackupRecordsKeepsNewestAndPinned() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            // 创建 21 条备份记录（时间递增）
            for (int i = 0; i < 21; i++) {
                ProcessingResult r = new ProcessingResult();
                r.setResultTime(LocalDateTime.of(2026, 1, 1, 0, i, 0));
                OperationRecord rec = new OperationRecord();
                rec.setStrategyType("Test");
                rec.setOperationType(OperationContext.OPERATION_ADD);
                rec.setTargetPath(Path.of("/tmp/f" + i));
                rec.setSuccess(true);
                r.addOperationRecord(rec);
                assertTrue(BackupFileLoader.saveOperationRecord(r), "应能保存备份记录 " + i);
            }

            Map<String, ProcessingResult> records = BackupFileLoader.getOperationRecordFiles();
            assertEquals(21, records.size());
            // 固定最旧的一条（时间倒序列表的末尾）
            List<String> names = new ArrayList<>(records.keySet());
            String oldest = names.get(names.size() - 1);
            assertTrue(BackupFileLoader.updatePinnedFlag(oldest, true), "应能固定最旧记录");

            // 淘汰到 20 条：固定记录保留，删除 1 条最旧的其它记录
            BackupFileLoader.trimBackupRecords(20);
            Map<String, ProcessingResult> after = BackupFileLoader.getOperationRecordFiles();
            assertEquals(20, after.size(), "淘汰后应剩 20 条");
            assertTrue(after.containsKey(oldest), "固定的最旧记录应保留（不受淘汰影响）");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    /**
     * 回归测试：备份文件丢失后再次更新应重新拷贝重建（原实现 containsKey 命中即跳过拷贝、
     * 不验证磁盘存在性——备份实体一旦丢失/被清理，后续更新永远不再落盘，恢复时提示"备份文件不存在"。
     * 用户实测：更新 → 删除 → 再更新 → 恢复，备份缺失）
     */
    @Test
    public void lostBackupFileIsRebuiltOnNextAdd() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            // 模拟被备份的目标文件（如 THtest/config/xxx）
            Path sourceFile = tempDir.resolve("target-file.txt");
            Files.writeString(sourceFile, "original-content-v1");

            // 1. 首次备份 → 落盘
            assertTrue(BackupFileLoader.addBackupFile(sourceFile), "首次备份应成功");
            String md5 = FileSignUtil.getFileMd5(sourceFile);
            Path backup1 = BackupFileLoader.getBackupFiles().get(md5);
            assertNotNull(backup1, "备份索引应含该文件");
            assertTrue(Files.exists(backup1), "首次备份文件应真实落盘");

            // 2. 模拟备份文件丢失（被清理残留/误删等）
            Files.deleteIfExists(backup1);
            assertFalse(Files.exists(backup1), "前置：备份文件已丢失");

            // 3. 再次更新同一文件 → 应重新拷贝重建备份（修复点）
            assertTrue(BackupFileLoader.addBackupFile(sourceFile), "再次备份应成功");
            Path backup2 = BackupFileLoader.getBackupFiles().get(md5);
            assertNotNull(backup2, "再备份后索引应仍指向该文件");
            assertTrue(Files.exists(backup2), "备份文件丢失后再次更新必须重建（否则恢复时找不到备份）");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    /**
     * 平铺存储验证：备份文件直接以原文件名平铺在 backup/ 下（不再镜像目录层级）。
     * 同名但内容不同（MD5 不同）→ 加短哈希后缀互不覆盖；同内容（MD5 相同）→ 去重合并为一份。
     */
    @Test
    public void backupFilesAreFlattenedSameNameDifferentContentGetsHashSuffix() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path dir1 = Files.createDirectories(tempDir.resolve("src/dir1"));
            Path dir2 = Files.createDirectories(tempDir.resolve("src/dir2"));
            Path f1 = dir1.resolve("config.txt");
            Files.writeString(f1, "content-A");
            Path f2 = dir2.resolve("config.txt");
            Files.writeString(f2, "content-B-different");

            assertTrue(BackupFileLoader.addBackupFile(f1), "第一个同名文件备份应成功");
            assertTrue(BackupFileLoader.addBackupFile(f2), "第二个同名文件备份应成功（不得覆盖第一个）");

            // 平铺：backup/ 下两个普通文件（无目录层级）
            java.util.List<Path> backups = new ArrayList<>();
            try (java.util.stream.Stream<Path> s = Files.list(ConfigLoader.getBackupPath())) {
                s.filter(Files::isRegularFile).forEach(backups::add);
            }
            assertEquals(2, backups.size(), "同名不同内容的两个备份应平铺保存且互不覆盖");

            String c1 = Files.readString(backups.get(0));
            String c2 = Files.readString(backups.get(1));
            assertTrue((c1.equals("content-A") && c2.equals("content-B-different"))
                            || (c1.equals("content-B-different") && c2.equals("content-A")),
                    "两个备份的内容都应完整保留");

            // 同内容再备份：MD5 去重，不新增文件
            assertTrue(BackupFileLoader.addBackupFile(f1), "同内容再备份应成功（去重合并）");
            long count;
            try (java.util.stream.Stream<Path> s = Files.list(ConfigLoader.getBackupPath())) {
                count = s.filter(Files::isRegularFile).count();
            }
            assertEquals(2, count, "同内容（相同 MD5）备份应去重，不新增文件");

            // 跨目录同名同内容（如 dir3/config.txt 内容与 f1 相同）：也应合并为一份，不新增
            Path dir3 = Files.createDirectories(tempDir.resolve("src/dir3"));
            Path f3 = dir3.resolve("config.txt");
            Files.writeString(f3, "content-A"); // 与 f1 内容相同（MD5 相同）
            assertTrue(BackupFileLoader.addBackupFile(f3), "跨目录同名同内容备份应成功（去重合并）");
            long countAfter;
            try (java.util.stream.Stream<Path> s = Files.list(ConfigLoader.getBackupPath())) {
                countAfter = s.filter(Files::isRegularFile).count();
            }
            assertEquals(2, countAfter, "跨目录同名同 MD5 应自动合并为一份，不新增备份文件");

            // 不同文件名但内容相同（如 config.txt 与 config (副本).md）：MD5 去重合并为一份，
            // 恢复时按 MD5 找到备份、拷贝到目标原名即可（备份文件名不影响恢复）
            Path dir4 = Files.createDirectories(tempDir.resolve("src/dir4"));
            Path f4 = dir4.resolve("other-name.txt");
            Files.writeString(f4, "content-A"); // 与 f1 内容相同，但文件名不同
            assertTrue(BackupFileLoader.addBackupFile(f4), "不同名同内容备份应成功（去重合并）");
            long countFinal;
            try (java.util.stream.Stream<Path> s = Files.list(ConfigLoader.getBackupPath())) {
                countFinal = s.filter(Files::isRegularFile).count();
            }
            assertEquals(2, countFinal, "不同名同内容（相同 MD5）应合并为一份，不新增备份文件");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }
}
