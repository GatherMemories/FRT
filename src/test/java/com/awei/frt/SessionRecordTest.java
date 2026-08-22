package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * session-current.json 增量写（P3）测试：
 * - 逐条追加（JSON Lines）后能完整恢复
 * - 旧格式（整文件 ProcessingResult）兼容读取
 */
class SessionRecordTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void isolateBackupPath() {
        // 会话记录隔离到临时目录，避免污染真实 testDic/backup/record
        TestSupport.isolateBackup(tempDir);
    }

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    @Test
    void appendAndLoadRoundTrip() {
        BackupFileLoader.clearSessionRecord();
        try {
            OperationRecord r1 = record(OperationContext.OPERATION_ADD, true);
            OperationRecord r2 = record(OperationContext.OPERATION_REPLACE, false);
            assertTrue(BackupFileLoader.appendSessionRecord(r1));
            assertTrue(BackupFileLoader.appendSessionRecord(r2));

            assertTrue(BackupFileLoader.hasSessionRecord(), "会话记录文件应存在");
            ProcessingResult loaded = BackupFileLoader.loadSessionRecord();
            assertNotNull(loaded, "应能恢复会话记录");
            List<OperationRecord> records = loaded.getOperationRecords();
            assertEquals(2, records.size(), "应恢复出 2 条操作记录");
            assertEquals(OperationContext.OPERATION_ADD, records.get(0).getOperationType());
            assertEquals(OperationContext.OPERATION_REPLACE, records.get(1).getOperationType());
            assertEquals(1, loaded.getSuccessCount());
            assertEquals(1, loaded.getErrorCount());
            assertTrue(records.get(0).isSuccess());
        } finally {
            BackupFileLoader.clearSessionRecord();
        }
    }

    @Test
    void legacyWholeFileFormatStillLoads() throws Exception {
        BackupFileLoader.clearSessionRecord();
        try {
            // 构造旧格式：整文件一个 ProcessingResult JSON
            ProcessingResult legacy = new ProcessingResult();
            OperationRecord record = record(OperationContext.OPERATION_DELETE, true);
            legacy.addOperationRecord(record);

            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 会话文件路径跟随隔离后的备份路径（record 目录可能不存在，先创建）
            Path sessionFile = ConfigLoader.getBackupPath().resolve("record").resolve("session-current.json");
            Files.createDirectories(sessionFile.getParent());
            Files.writeString(sessionFile, mapper.writeValueAsString(legacy), StandardCharsets.UTF_8);

            ProcessingResult loaded = BackupFileLoader.loadSessionRecord();
            assertNotNull(loaded, "旧格式会话记录应兼容读取");
            assertEquals(1, loaded.getOperationRecords().size());
            assertEquals(OperationContext.OPERATION_DELETE, loaded.getOperationRecords().get(0).getOperationType());
        } finally {
            BackupFileLoader.clearSessionRecord();
        }
    }

    @Test
    void noSessionReturnsNull() {
        BackupFileLoader.clearSessionRecord();
        assertNull(BackupFileLoader.loadSessionRecord(), "无会话记录时应返回 null");
    }

    @Test
    void pathWithChineseAndSpecialCharsRoundTripsAsPlainPath() {
        BackupFileLoader.clearSessionRecord();
        try {
            // 回归：中文 + 方括号/空格路径，序列化必须是普通路径而非 file:/... URI 编码
            Path p = Path.of("/home/aaa/桌面/项目/FRT/testDic/THtest/2-FZ[跨肩视角]ShoulderSurfing-Forge-1.20.1-4.12.0.jar");
            OperationRecord r = new OperationRecord();
            r.setStrategyType("Test");
            r.setOperationType(OperationContext.OPERATION_DELETE);
            r.setSuccess(true);
            r.setTargetPath(p);
            assertTrue(BackupFileLoader.appendSessionRecord(r));

            ProcessingResult loaded = BackupFileLoader.loadSessionRecord();
            assertNotNull(loaded);
            Path loadedPath = loaded.getOperationRecords().get(0).getTargetPath();
            assertNotNull(loadedPath);
            assertEquals(p.toString(), loadedPath.toString(),
                    "路径应原样往返，不应变成 file:/...%E6%A1%8C URI 编码");
        } finally {
            BackupFileLoader.clearSessionRecord();
        }
    }

    @Test
    void legacyUriFormatPathStillLoads() throws Exception {
        BackupFileLoader.clearSessionRecord();
        try {
            // 兼容旧版本：Path 曾被 Jackson 默认序列化成 file:/... URI 编码格式
            String legacy = "{\"strategyType\":\"Test\",\"operationType\":\"operation_delete\","
                    + "\"success\":true,"
                    + "\"targetPath\":\"file:/home/aaa/%E6%A1%8C%E9%9D%A2/FRT/2-FZ%5Btest%5D.jar\"}";
            Path sessionFile = ConfigLoader.getBackupPath().resolve("record").resolve("session-current.json");
            Files.createDirectories(sessionFile.getParent());
            Files.writeString(sessionFile, legacy, StandardCharsets.UTF_8);

            ProcessingResult loaded = BackupFileLoader.loadSessionRecord();
            assertNotNull(loaded, "旧 URI 格式应兼容读取");
            Path p = loaded.getOperationRecords().get(0).getTargetPath();
            assertNotNull(p);
            assertEquals("/home/aaa/桌面/FRT/2-FZ[test].jar", p.toString(),
                    "URI 编码路径应被解码为普通路径");
        } finally {
            BackupFileLoader.clearSessionRecord();
        }
    }

    private OperationRecord record(String type, boolean success) {
        OperationRecord r = new OperationRecord();
        r.setStrategyType("TestStrategy");
        r.setOperationType(type);
        r.setSuccess(success);
        if (!success) {
            r.setErrorMessage("模拟失败");
        }
        return r;
    }
}
