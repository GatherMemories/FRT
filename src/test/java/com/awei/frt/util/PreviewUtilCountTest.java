package com.awei.frt.util;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预览打印计数测试（审查 L9 回归）：
 * 标题"将执行以下 N 个"与返回值必须口径一致——只统计可执行（成功）记录，
 * 失败行（未找到目标文件）单独提示、不混入执行数。
 */
class PreviewUtilCountTest {

    @Test
    void failedRowsExcludedFromHeaderCountAndReturn() {
        ProcessingResult preview = new ProcessingResult();
        // 一条成功 ADD + 一条失败记录
        OperationRecord ok = new OperationRecord();
        ok.setSuccess(true);
        ok.setOperationType(OperationContext.OPERATION_ADD);
        ok.setTargetPath(Path.of("/t/a.txt"));
        preview.addOperationRecord(ok);

        OperationRecord failed = new OperationRecord();
        failed.setSuccess(false);
        failed.setErrorMessage("文件不存在、或不是文件");
        failed.setTargetPath(Path.of("/t/missing.txt"));
        preview.addOperationRecord(failed);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(buf, true));
            int count = PreviewUtil.printPreview(preview, "更新");
            String out = buf.toString();

            assertEquals(1, count, "返回值只统计可执行记录");
            assertTrue(out.contains("将执行以下 1 个更新操作"), "标题执行数应排除失败行: " + out);
            assertTrue(out.contains("[+] 新增"), "成功行应正常列出: " + out);
            assertTrue(out.contains("另有 1 行未找到目标文件"), "失败行应单独提示数量: " + out);
        } finally {
            System.setOut(original);
        }
    }
}
