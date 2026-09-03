package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.utils.FileSignUtil;
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
        // 隔离到 @TempDir：不依赖工作区 backup 目录是否存在（无外部 config.json 的 CI 上，
        // getBackupPath() 指向不存在的 backup/ → 直接写会 NoSuchFileException）
        TestSupport.isolateBackup(tempDir);
        try {
            // 模拟 Windows 平台生成的历史备份记录 JSON（反斜杠路径）
            String json = "{\"resultTime\":\"2026-02-22T00:15:00\",\"successCount\":1,\"skipCount\":0,\"errorCount\":0,"
                    + "\"operationRecords\":[{\"strategyType\":\"McMod\",\"operationType\":\"operation_replace\","
                    + "\"sourcePath\":\"C:\\\\Users\\\\5454564546\\\\Desktop\\\\update\\\\mod.jar\","
                    + "\"targetPath\":\"C:\\\\Users\\\\5454564546\\\\Desktop\\\\THtest\\\\mod.jar\","
                    + "\"sourceFileSign\":\"abc\",\"targetFileSign\":\"def\",\"timestamp\":\"2026-02-22T00:15:00\","
                    + "\"success\":true,\"errorMessage\":null}],\"success\":true,\"resultPath\":null}";
            Path recordDir = Files.createDirectories(
                    ConfigLoader.getBackupPath().resolve("record"));
            Path recordFile = recordDir.resolve("backup-win-test.json");
            Files.writeString(recordFile, json);
            ProcessingResult result = BackupFileLoader.loadOperationRecord("backup-win-test.json");
            assertNotNull(result, "含 Windows 路径的历史记录应能加载");
            assertEquals(1, result.getOperationRecords().size());
            assertNotNull(result.getOperationRecords().get(0).getSourcePath(), "sourcePath 应被解析为 Path 对象");
            assertTrue(result.getOperationRecords().get(0).getSourcePath().toString().contains("Desktop"),
                    "sourcePath 应保留原路径内容");
        } finally {
            TestSupport.restoreBackupPath();
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
        // 用 @TempDir 子目录（自动清理），原实现 Files.createTempDirectory 在测试外残留 backup-index-test* 目录
        Path temp = Files.createDirectories(tempDir.resolve("index-check"));
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
     * 回归测试：同内容、不同文件名的两个文件（如 config.txt 与 config (副本).md 内容相同），
     * 备份第二个文件时 MD5 去重不能把索引改指到"磁盘上不存在的推导路径"，否则恢复时
     * findBackupFileBySignature 命中不存在的文件，报"备份文件不存在"（用户实测复现）。
     */
    @Test
    public void sameContentDifferentNameKeepsExistingBackupIndex() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path backupDir = ConfigLoader.getBackupPath();
            // 显式重置索引到隔离目录（空）：先建目录再 loadBackupFiles，确保静态索引被清空重建
            // （loadBackupFiles 仅在目录存在时才 clear，否则会残留同 JVM 其他测试的索引，造成顺序依赖）
            Files.createDirectories(backupDir);
            BackupFileLoader.loadBackupFiles(backupDir);
            assertTrue(BackupFileLoader.getBackupFiles().isEmpty(), "隔离目录初始索引应为空");

            byte[] content = "Root config.txt\n".getBytes(StandardCharsets.UTF_8);
            Path fileA = tempDir.resolve("config.txt");          // 先备份的文件
            Path fileB = tempDir.resolve("config (副本).md");    // 后备份的同内容文件
            Files.write(fileA, content);
            Files.write(fileB, content);

            // 第一次备份：索引应指向真实落盘的 backup/config.txt
            assertTrue(BackupFileLoader.addBackupFile(fileA));
            String md5 = FileSignUtil.getFileMd5(fileA);
            Path firstIndexed = BackupFileLoader.getBackupFiles().get(md5);
            assertNotNull(firstIndexed, "首次备份后索引应有该 MD5 条目");
            assertTrue(Files.exists(firstIndexed), "首次备份后索引必须指向真实存在的文件");
            assertEquals(backupDir.resolve("config.txt"), firstIndexed);

            // 第二次备份同内容文件（不同文件名）：去重后索引必须仍指向真实文件，
            // 不能改指到 backup/config (副本).md（磁盘上从未创建过该文件）
            assertTrue(BackupFileLoader.addBackupFile(fileB));
            Path secondIndexed = BackupFileLoader.getBackupFiles().get(md5);
            assertNotNull(secondIndexed, "去重后索引不应丢失");
            assertTrue(Files.exists(secondIndexed),
                    "MD5 去重后索引必须指向真实存在的备份文件（原缺陷：改指到不存在的推导路径，恢复时报备份文件不存在）");
            assertEquals(firstIndexed, secondIndexed, "同内容去重应复用同一备份文件，索引不应被改指");
        } finally {
            // 恢复真实备份索引，避免影响其他测试
            ConfigLoader.getConfig();
            Path realBackup = ConfigLoader.getBackupPath();
            if (realBackup != null) {
                BackupFileLoader.loadBackupFiles(realBackup);
            }
        }
    }

    /**
     * 端到端验证：两个内容相同（MD5 相同）但文件名不同的文件被替换后，
     * 恢复时各自按操作记录里的目标路径（原始文件名）正确复原，互不覆盖。
     * （备份内容按 MD5 共享一份，文件名来自 record.getTargetPath()，两者不冲突）
     */
    @Test
    public void restoreSameContentFilesWithDifferentNamesKeepsNames() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path backupDir = Files.createDirectories(ConfigLoader.getBackupPath());
            BackupFileLoader.loadBackupFiles(backupDir); // 清空重建隔离索引

            Path targetDir = Files.createDirectories(tempDir.resolve("THtest"));
            byte[] oldContent = "Root config.txt\n".getBytes(StandardCharsets.UTF_8);
            byte[] newContent = "Root config.txt v2\n".getBytes(StandardCharsets.UTF_8);

            // 目标目录：两个同内容的旧文件
            Path targetA = targetDir.resolve("config.txt");
            Path targetB = targetDir.resolve("config (副本).md");
            Files.write(targetA, oldContent);
            Files.write(targetB, oldContent);
            // 更新目录：同名同内容的新文件（内容不同 → 构成 REPLACE）
            Path sourceA = tempDir.resolve("update/config.txt");
            Path sourceB = tempDir.resolve("update/config (副本).md");
            Files.createDirectories(sourceA.getParent());
            Files.write(sourceA, newContent);
            Files.write(sourceB, newContent);

            // 模拟更新：备份旧目标（同内容 → 去重为 1 份备份）
            assertTrue(BackupFileLoader.addBackupFile(targetA));
            assertTrue(BackupFileLoader.addBackupFile(targetB));
            long backupCount;
            try (java.util.stream.Stream<Path> s = Files.list(backupDir)) {
                backupCount = s.filter(Files::isRegularFile).count();
            }
            assertEquals(1, backupCount, "同内容两个旧文件应去重为 1 份备份文件");

            // 构造含两条 REPLACE 的操作记录（各自带原始文件名）
            String oldMd5 = FileSignUtil.getFileMd5(targetA);
            ProcessingResult result = new ProcessingResult();
            for (int i = 0; i < 2; i++) {
                Path src = i == 0 ? sourceA : sourceB;
                Path tgt = i == 0 ? targetA : targetB;
                OperationRecord record = new OperationRecord();
                record.setStrategyType("FileSameName");
                record.setOperationType(OperationContext.OPERATION_REPLACE);
                record.setSourcePath(src);
                record.setTargetPath(tgt);
                record.setSourceFileSign(FileSignUtil.getFileMd5(src)); // 替换后新内容
                record.setTargetFileSign(oldMd5);                        // 替换前旧内容（备份索引 key）
                record.setTimestamp(LocalDateTime.now());
                record.setSuccess(true);
                result.addOperationRecord(record);
            }

            // 模拟更新后的目标状态（已被替换为新内容）
            Files.write(targetA, newContent);
            Files.write(targetB, newContent);

            // 执行恢复
            RestoreResult rr = BackupFileLoader.restoreFromResult(result, () -> "n");
            assertTrue(rr.isFullSuccess(), "两个同内容文件应全部恢复成功: " + rr.getFailureMessages());
            assertEquals(2, rr.getSuccessCount());
            // 关键断言：两个文件各自按原名复原，内容都为旧内容，且互不覆盖
            assertArrayEquals(oldContent, Files.readAllBytes(targetA), "config.txt 应按原名恢复旧内容");
            assertArrayEquals(oldContent, Files.readAllBytes(targetB), "config (副本).md 应按原名恢复旧内容");
            assertTrue(Files.exists(targetDir.resolve("config.txt")));
            assertTrue(Files.exists(targetDir.resolve("config (副本).md")));
        } finally {
            // 恢复真实备份索引，避免影响其他测试
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

    /**
     * 回归测试（审查 H1/支撑层高-1）：冷启动（进程启动后未加载过备份目录，静态索引为空）
     * 直接恢复含 REPLACE 的旧记录时，必须先自动加载备份索引，否则"找备份文件"全失败。
     * 修复前：backupFiles 静态缓存为空且无预加载 → 恢复 REPLACE/DELETE 必然失败。
     */
    @Test
    public void restoreReplaceAfterColdStartAutoLoadsBackupIndex() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            // 目标文件当前为"替换后新内容"
            Path targetDir = Files.createDirectories(tempDir.resolve("THtest"));
            Path targetFile = targetDir.resolve("a.txt");
            byte[] oldContent = "old-content".getBytes(StandardCharsets.UTF_8);
            byte[] newContent = "new-content".getBytes(StandardCharsets.UTF_8);
            Files.write(targetFile, newContent);

            // 备份目录里放一份旧内容备份（REPLACE 恢复按 MD5 查找）
            Path backupDir = Files.createDirectories(ConfigLoader.getBackupPath());
            Path backupFile = backupDir.resolve("a.txt.bak");
            Files.write(backupFile, oldContent);
            String oldMd5 = FileSignUtil.getFileMd5(backupFile);

            // 模拟冷启动：把静态索引重置为空（进程启动后未执行任何操作/加载——
            // loadBackupFiles(不存在的目录) 使索引重建为空；不能用 getBackupFiles()
            // 断言状态，它会自动扫描磁盘加载，那正是被测的预加载行为）
            BackupFileLoader.loadBackupFiles(tempDir.resolve("nonexistent-backup-dir"));

            ProcessingResult result = new ProcessingResult();
            OperationRecord record = new OperationRecord();
            record.setStrategyType("FileSameName");
            record.setOperationType(OperationContext.OPERATION_REPLACE);
            record.setTargetPath(targetFile);
            record.setSourceFileSign(FileSignUtil.getFileMd5(
                    Files.write(tempDir.resolve("new-src.txt"), newContent)));
            record.setTargetFileSign(oldMd5); // 替换前旧内容（备份索引 key）
            record.setTimestamp(LocalDateTime.now());
            record.setSuccess(true);
            result.addOperationRecord(record);

            RestoreResult rr = BackupFileLoader.restoreFromResult(result, () -> "n");
            assertTrue(rr.isFullSuccess(), "冷启动恢复 REPLACE 应成功（索引自动加载）: " + rr.getFailureMessages());
            assertArrayEquals(oldContent, Files.readAllBytes(targetFile), "目标应恢复为旧内容");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    /**
     * 回归测试（审查 core H1）：恢复中用户选择"跳过/保留"的记录不得进入回滚清单——
     * 否则后续失败触发回滚时，会重新覆盖/删除用户明确选择保留的文件（数据丢失路径）。
     * 三态语义：跳过（SKIPPED）既不计失败、也不参与回滚。
     */
    @Test
    public void skippedRecordsAreExcludedFromRollback() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            Path targetDir = Files.createDirectories(tempDir.resolve("THtest"));

            // ---- 记录 1（先处理，倒序所以放列表后面）：REPLACE，目标被用户改动 → 用户选择跳过 ----
            Path t1 = targetDir.resolve("keep.txt");
            byte[] t1Old = "v1-old".getBytes(StandardCharsets.UTF_8);
            byte[] t1New = "v1-new".getBytes(StandardCharsets.UTF_8);
            byte[] t1UserModified = "user-kept-v1".getBytes(StandardCharsets.UTF_8);
            // 目标当前内容 = 用户改过的版本（≠ 替换后的新内容 → 触发"是否跳过"询问）
            Files.write(t1, t1UserModified);
            // 备份目录放记录 1 的旧内容（供恢复查找，随后用户跳过）
            Path backupDir = Files.createDirectories(ConfigLoader.getBackupPath());
            Path bak1 = backupDir.resolve("keep.txt.bak");
            Files.write(bak1, t1Old);
            String t1OldMd5 = FileSignUtil.getFileMd5(bak1);
            // 显式重建索引指向隔离目录（避免同 JVM 其他测试残留索引指向别处）
            BackupFileLoader.loadBackupFiles(backupDir);

            OperationRecord rec1 = new OperationRecord();
            rec1.setStrategyType("FileSameName");
            rec1.setOperationType(OperationContext.OPERATION_REPLACE);
            rec1.setTargetPath(t1);
            rec1.setSourceFileSign(FileSignUtil.getFileMd5(
                    Files.write(tempDir.resolve("t1-new-src.txt"), t1New)));
            rec1.setTargetFileSign(t1OldMd5);
            rec1.setTimestamp(LocalDateTime.now());
            rec1.setSuccess(true);

            // ---- 记录 2（后处理，放列表前面）：REPLACE，备份文件缺失 → 恢复失败触发回滚询问 ----
            Path t2 = targetDir.resolve("broken.txt");
            byte[] t2New = "v2-new".getBytes(StandardCharsets.UTF_8);
            Files.write(t2, t2New);
            OperationRecord rec2 = new OperationRecord();
            rec2.setStrategyType("FileSameName");
            rec2.setOperationType(OperationContext.OPERATION_REPLACE);
            rec2.setTargetPath(t2);
            rec2.setSourceFileSign(FileSignUtil.getFileMd5(
                    Files.write(tempDir.resolve("t2-new-src.txt"), t2New)));
            rec2.setTargetFileSign("00000000000000000000000000000000"); // 无此备份 → 找不到
            rec2.setTimestamp(LocalDateTime.now());
            rec2.setSuccess(true);

            ProcessingResult result = new ProcessingResult();
            result.addOperationRecord(rec2); // 倒序遍历：rec1 先处理，rec2 后处理触发失败
            result.addOperationRecord(rec1);

            // 交互脚本：第一次 askSkipOrProceed 答 y（跳过 rec1），失败后回滚询问答 y（回滚）
            // 先给 rec1 的跳过提示 y，再给失败回滚提示 y
            java.util.ArrayDeque<String> answers = new java.util.ArrayDeque<>(List.of("y", "y"));
            RestoreResult rr = BackupFileLoader.restoreFromResult(result, answers::removeFirst);

            assertEquals(0, rr.getSuccessCount(), "两条记录都没真正恢复成功");
            assertEquals(1, rr.getFailureCount(), "rec2（备份缺失）应计失败");
            assertEquals(1, rr.getSkipCount(), "rec1（用户跳过保留）应计跳过而非失败");
            assertFalse(rr.isFullSuccess(), "有失败不应 full success");

            // 关键断言：rec1 用户选择保留的文件必须原封不动——
            // 若跳过项误入回滚清单，回滚会把 t1 重新覆盖为 t1New
            assertArrayEquals(t1UserModified, Files.readAllBytes(t1),
                    "跳过项不得被回滚覆盖（用户明确选择保留的文件）");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }
}
