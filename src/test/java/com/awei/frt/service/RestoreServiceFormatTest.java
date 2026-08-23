package com.awei.frt.service;

import com.awei.frt.TestSupport;
import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 恢复菜单展示与固定（pinned）交互的回归测试：
 * 1. formatBackupInfo：固定标注 [固定] 必须显示在文件名括号【外】（曾因格式错误
 *    显示成 [backup.json [固定]]，用户实测发现）
 * 2. buildPinActionPrompt：p 提示必须按当前固定状态动态显示（固定中→取消固定，
 *    未固定→固定），避免用户误以为固定失效
 * 3. 删除其它备份记录后，已固定记录的 pinned 标记必须保留（用户曾报"删除一个备份
 *    后固定标注功能失效"，锁定该行为防回归）
 */
class RestoreServiceFormatTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    private ProcessingResult result(boolean pinned, int success, int error, LocalDateTime time) {
        ProcessingResult r = new ProcessingResult();
        r.setResultTime(time);
        r.setPinned(pinned);
        r.setSuccessCount(success);
        r.setErrorCount(error);
        return r;
    }

    // ---------- formatBackupInfo 展示格式 ----------

    @Test
    void pinnedMarkerIsOutsideFileNameBrackets() {
        RestoreService service = new RestoreService(null, () -> "");
        ProcessingResult r = result(true, 2, 0, LocalDateTime.of(2026, 8, 23, 13, 30, 7));
        String line = service.formatBackupInfo("backup-20260823-133007.json", r);
        // 关键：固定标注在文件名括号外，形如 "[backup-xxx.json] [固定] ..."
        assertEquals("[backup-20260823-133007.json] [固定] 2026-08-23 13:30:07 | 成功:2 失败:0", line);
        assertFalse(line.contains("[backup-20260823-133007.json [固定]]"),
                "固定标注不得嵌在文件名括号内（旧版格式 bug）");
    }

    @Test
    void unpinnedRecordHasNoMarker() {
        RestoreService service = new RestoreService(null, () -> "");
        String line = service.formatBackupInfo("backup-a.json", result(false, 1, 1, LocalDateTime.of(2026, 8, 23, 9, 0, 0)));
        assertEquals("[backup-a.json] 2026-08-23 09:00:00 | 成功:1 失败:1", line);
    }

    // ---------- p 提示动态文案 ----------

    @Test
    void pinPromptShowsUnpinWhenAlreadyPinned() {
        RestoreService service = new RestoreService(null, () -> "");
        String prompt = service.buildPinActionPrompt(result(true, 0, 0, LocalDateTime.now()));
        assertTrue(prompt.contains("p=取消固定（当前已固定）"),
                "已固定记录的提示应显示为取消固定，避免用户误操作：" + prompt);
    }

    @Test
    void pinPromptShowsPinWhenNotPinned() {
        RestoreService service = new RestoreService(null, () -> "");
        String prompt = service.buildPinActionPrompt(result(false, 0, 0, LocalDateTime.now()));
        assertTrue(prompt.contains("p=固定（永久保留）"),
                "未固定记录的提示应显示为固定：" + prompt);
    }

    // ---------- 删除其它备份后固定标记保留（用户实测场景） ----------

    /**
     * 回归测试：固定一条备份后，删除【其它】备份记录，已固定的备份
     * 在恢复列表中仍应显示 [固定] 标注（固定标记持久化在记录 JSON 中，
     * 删除其它记录不得影响它）。
     */
    @Test
    void deletingAnotherBackupKeepsPinnedFlag() throws IOException {
        TestSupport.isolateBackup(tempDir);
        try {
            // 建两条备份记录：first 固定，second 不固定
            ProcessingResult first = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 10, 0, 0));
            first.addOperationRecord(operationRecord("f1"));
            assertTrue(BackupFileLoader.saveOperationRecord(first), "应能保存第一条备份记录");
            ProcessingResult second = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 11, 0, 0));
            second.addOperationRecord(operationRecord("f2"));
            assertTrue(BackupFileLoader.saveOperationRecord(second), "应能保存第二条备份记录");

            Map<String, ProcessingResult> records = BackupFileLoader.getOperationRecordFiles();
            String firstName = records.keySet().stream()
                    .filter(n -> n.contains("100000"))
                    .findFirst().orElseThrow();
            String secondName = records.keySet().stream()
                    .filter(n -> !n.equals(firstName))
                    .findFirst().orElseThrow();

            // 固定第一条
            assertTrue(BackupFileLoader.updatePinnedFlag(firstName, true), "应能固定第一条备份");

            // 删除第二条（用户场景：删除一个备份）
            assertTrue(BackupFileLoader.deleteBackupRecord(secondName), "应能删除第二条备份记录");

            // 重新加载：第一条仍在且 pinned 标记保留
            Map<String, ProcessingResult> after = BackupFileLoader.getOperationRecordFiles();
            assertEquals(1, after.size(), "删除后应只剩一条备份记录");
            assertTrue(after.containsKey(firstName), "已固定的备份不应被删除");
            assertTrue(after.get(firstName).isPinned(), "已固定备份的 pinned 标记应保留");

            // 展示格式仍含 [固定]（括号外）
            RestoreService service = new RestoreService(null, () -> "");
            assertTrue(service.formatBackupInfo(firstName, after.get(firstName)).contains("[固定]"),
                    "删除其它备份后，固定标注仍应显示在列表中");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }

    private OperationRecord operationRecord(String path) {
        OperationRecord rec = new OperationRecord();
        rec.setStrategyType("Test");
        rec.setOperationType(OperationContext.OPERATION_ADD);
        rec.setTargetPath(Path.of("/tmp/" + path));
        rec.setSuccess(true);
        return rec;
    }
}
