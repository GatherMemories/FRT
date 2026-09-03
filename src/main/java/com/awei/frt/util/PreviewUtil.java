package com.awei.frt.util;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;

import java.nio.file.Path;
import java.util.List;

/**
 * 操作预览打印工具（更新/删除共用）
 * 把 dryRun 阶段收集的操作计划以 "[+] 新增 / [=] 替换 / [-] 删除" 形式列出，
 * 供用户在真正执行前二次确认。
 */
public final class PreviewUtil {

    private PreviewUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 打印操作预览
     * @param preview dryRun 阶段的结果
     * @param actionName 动作名（如 "更新" / "删除"）
     * @return 可执行的操作数（成功记录数）
     */
    public static int printPreview(ProcessingResult preview, String actionName) {
        if (preview == null) {
            return 0;
        }
        List<OperationRecord> records = preview.getOperationRecords();
        if (records == null || records.isEmpty()) {
            LoggerUtil.logInfo("[预览] 没有需要" + actionName + "的文件");
            return 0;
        }
        // 两遍统计：先数失败行，再逐行打印（标题与确认数字口径统一，只报"将执行"的可执行数；
        // 原实现标题按 records.size() 而确认按 planCount，有失败行时两个数字不一致易误解，审查 L9）
        int failedCount = 0;
        for (OperationRecord r : records) {
            if (!r.isSuccess()) {
                failedCount++;
            }
        }
        int planCount = records.size() - failedCount;
        System.out.println("\n[预览] 将执行以下 " + planCount + " 个" + actionName + "操作（预览模式：尚未执行任何操作，确认后才真正执行）:");
        System.out.println("-----------------------------------------");
        for (OperationRecord r : records) {
            if (!r.isSuccess()) {
                System.out.println(formatErrorLine(r));
                continue;
            }
            String op;
            switch (r.getOperationType()) {
                case OperationContext.OPERATION_ADD -> op = "[+] 新增";
                case OperationContext.OPERATION_REPLACE -> op = "[=] 替换";
                case OperationContext.OPERATION_DELETE -> op = "[-] 删除";
                default -> op = "[?] " + r.getOperationType();
            }
            Path target = r.getTargetPath();
            System.out.println("  " + op + ": " + (target != null ? target : r.getSourcePath()));
        }
        System.out.println("-----------------------------------------");
        if (failedCount > 0) {
            System.out.println("[提示] 另有 " + failedCount + " 行未找到目标文件（见上方 [!]），不纳入上述执行数");
        }
        return planCount;
    }

    /**
     * 格式化预览失败行（包内可见供测试）：明确告诉用户是哪个文件未找到，提示友好。
     * 输出示例：  [!] 未找到目标文件: /path/THtest/a.txt（文件不存在、或不是文件）
     */
    static String formatErrorLine(OperationRecord r) {
        String target = r.getTargetPath() != null ? r.getTargetPath().toString()
                : (r.getSourcePath() != null ? r.getSourcePath().toString() : "未知文件");
        return "  [!] 未找到目标文件: " + target + "（" + r.getErrorMessage() + "）";
    }
}
