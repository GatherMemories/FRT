package com.awei.frt.service;

import com.awei.frt.TestSupport;
import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.interaction.UserPrompter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private OperationRecord operationRecord(String path) {
        OperationRecord rec = new OperationRecord();
        rec.setStrategyType("Test");
        rec.setOperationType(OperationContext.OPERATION_ADD);
        rec.setTargetPath(Path.of("/tmp/" + path));
        rec.setSuccess(true);
        return rec;
    }

    // ---------- 端到端：同会话内固定/删除后列表实时刷新（用户实测场景） ----------

    /**
     * 回归测试：同一恢复会话内"固定 A → 删除 B → 固定 C（不是同一个）"，
     * 列表文字输出必须立即显示 [固定]，无需重新进入备份功能。
     * 根因：executeRestore 只在循环外加载一次记录，而 loadOperationRecordsFiles()
     * 每次调用都重建静态 map；固定/删除写盘后列表仍读旧 map 引用 → [固定] 不刷新。
     * 修复：每次循环重新加载。
     */
    @Test
    void pinDeletePinSameSessionRefreshesList() throws IOException {
        TestSupport.isolateBackup(tempDir);
        PrintStream originalOut = System.out;
        try {
            // 三条记录：A(10:00) B(11:00) C(12:00)，倒序显示 C, B, A
            ProcessingResult a = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 10, 0, 0));
            a.addOperationRecord(operationRecord("a"));
            ProcessingResult b = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 11, 0, 0));
            b.addOperationRecord(operationRecord("b"));
            ProcessingResult c = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 12, 0, 0));
            c.addOperationRecord(operationRecord("c"));
            assertTrue(BackupFileLoader.saveOperationRecord(a));
            assertTrue(BackupFileLoader.saveOperationRecord(b));
            assertTrue(BackupFileLoader.saveOperationRecord(c));

            // 交互序列：固定 C(1) → 删除 B(-1, 删 2) → 固定 A(2) → 空输入退出
            Queue<String> inputs = new ArrayDeque<>(List.of(
                    "1", "p",   // 固定 C（12:00 最新，倒序第 1 条）
                    "-1", "2", "y", // 删除 B（11:00，第 2 条）
                    "2", "p",   // 固定 A（删除后剩 C,A；A 为第 2 条）
                    ""          // 退出
            ));
            RestoreService service = new RestoreService(null, inputs::poll);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            service.executeRestore();
            System.setOut(originalOut);

            String output = buffer.toString(StandardCharsets.UTF_8);
            // B 的列表行（"[backup-...json] 时间 | ..."）应只出现 3 次：初始列表、固定C后列表、
            // 删除确认列表；删除后不再出现（旧 bug 会在删除后的列表继续显示，且 [固定] 不刷新）。
            long bCount = countOccurrences(output, "[backup-20260823-110000.json] 2026-08-23 11:00:00");
            assertEquals(3, bCount,
                    "B 删除后不应再出现在列表（初始列表+固定C后列表+删除确认应各 1 次）。实际列表次数 " + bCount + "，输出:\n" + output);
            // C 固定后，后续列表输出应含 [固定]（同会话内立即刷新）
            assertTrue(output.contains("backup-20260823-120000.json] [固定]"),
                    "固定 C 后列表应实时显示 [固定]，无需重新进入备份功能。实际输出:\n" + output);
            // A 固定后同样显示
            assertTrue(output.contains("backup-20260823-100000.json] [固定]"),
                    "固定 A 后列表应实时显示 [固定]。实际输出:\n" + output);
        } finally {
            System.setOut(originalOut);
            TestSupport.restoreBackupPath();
        }
    }

    // ---------- 删除固定备份：确认提示显著标注（不增加确认次数，用户拍板） ----------

    /**
     * 用户决策：删除备份时【不】增加二次确认，而是在第一次确认提示的文字里
     * 把已固定备份标注得显著、更友好。锁定行为：
     * - 删除列表里固定备份带 [固定] 标记
     * - 确认提示前有醒目的 >>> 警告 <<< 行，说明其中固定备份数量、删除不可恢复
     * - 确认仍只有一次（y 即删），普通备份删除不出现警告
     */
    @Test
    void deletePinnedShowsProminentWarningButSingleConfirm() throws IOException {
        TestSupport.isolateBackup(tempDir);
        PrintStream originalOut = System.out;
        try {
            // 两条记录：A(10:00) 固定，B(11:00) 不固定
            ProcessingResult a = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 10, 0, 0));
            a.addOperationRecord(operationRecord("a"));
            ProcessingResult b = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 11, 0, 0));
            b.addOperationRecord(operationRecord("b"));
            assertTrue(BackupFileLoader.saveOperationRecord(a));
            assertTrue(BackupFileLoader.saveOperationRecord(b));
            Map<String, ProcessingResult> records = BackupFileLoader.getOperationRecordFiles();
            String aName = records.keySet().stream().filter(n -> n.contains("100000")).findFirst().orElseThrow();
            assertTrue(BackupFileLoader.updatePinnedFlag(aName, true), "应能固定 A");

            // 倒序列表：1=B(11:00), 2=A(10:00 固定)
            // 删除 B(普通，第 1 条)：不应出现固定警告
            Queue<String> inputsB = new ArrayDeque<>(List.of("-1", "1", "y", ""));
            RestoreService serviceB = new RestoreService(null, inputsB::poll);
            ByteArrayOutputStream bufB = new ByteArrayOutputStream();
            System.setOut(new PrintStream(bufB, true, StandardCharsets.UTF_8));
            serviceB.executeRestore();
            String outB = bufB.toString(StandardCharsets.UTF_8);
            assertFalse(outB.contains(">>> 警告"), "删除普通备份不应出现固定警告");
            assertTrue(outB.contains("已删除备份记录文件: backup-20260823-110000.json"),
                    "普通备份一次 y 确认后应删除成功");

            // 删除 A(固定，删后只剩 A 为第 1 条)：确认提示应有显著警告；仍只需一次 y
            Queue<String> inputsA = new ArrayDeque<>(List.of("-1", "1", "y", ""));
            RestoreService serviceA = new RestoreService(null, inputsA::poll);
            ByteArrayOutputStream bufA = new ByteArrayOutputStream();
            System.setOut(new PrintStream(bufA, true, StandardCharsets.UTF_8));
            serviceA.executeRestore();
            String outA = bufA.toString(StandardCharsets.UTF_8);
            // 显著警告 + 列表行带 [固定] 标记
            assertTrue(outA.contains(">>> 警告"), "删除固定备份应出现显著警告");
            assertTrue(outA.contains("[backup-20260823-100000.json] [固定]"),
                    "删除列表中的固定备份应带 [固定] 标记");
            assertTrue(outA.contains("已删除备份记录文件: backup-20260823-100000.json"),
                    "固定备份在单次 y 确认后应删除成功（不增加确认次数）");
        } finally {
            System.setOut(originalOut);
            TestSupport.restoreBackupPath();
        }
    }

    /**
     * 删除固定备份时，警告行应包含固定数量与"不可恢复"提示（友好化文案）。
     */
    @Test
    void pinnedDeleteWarningContainsCountAndIrreversibleHint() throws IOException {
        TestSupport.isolateBackup(tempDir);
        PrintStream originalOut = System.out;
        try {
            ProcessingResult a = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 10, 0, 0));
            a.addOperationRecord(operationRecord("a"));
            ProcessingResult b = result(false, 1, 0, LocalDateTime.of(2026, 8, 23, 11, 0, 0));
            b.addOperationRecord(operationRecord("b"));
            assertTrue(BackupFileLoader.saveOperationRecord(a));
            assertTrue(BackupFileLoader.saveOperationRecord(b));
            Map<String, ProcessingResult> records = BackupFileLoader.getOperationRecordFiles();
            String aName = records.keySet().stream().filter(n -> n.contains("100000")).findFirst().orElseThrow();
            assertTrue(BackupFileLoader.updatePinnedFlag(aName, true));

            // 范围删除 1-2（包含固定+普通各 1 个）
            Queue<String> inputs = new ArrayDeque<>(List.of("-1", "1-2", "y", ""));
            RestoreService service = new RestoreService(null, inputs::poll);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            service.executeRestore();
            String out = buffer.toString(StandardCharsets.UTF_8);
            assertTrue(out.contains("包含 1 个已固定备份"), "警告应说明固定备份数量");
            assertTrue(out.contains("不可恢复"), "警告应说明固定备份删除后不可恢复");
        } finally {
            System.setOut(originalOut);
            TestSupport.restoreBackupPath();
        }
    }
}
