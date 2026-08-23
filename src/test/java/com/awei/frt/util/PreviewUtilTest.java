package com.awei.frt.util;

import com.awei.frt.model.OperationRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预览失败行格式化测试：未找到目标文件的提示必须包含具体文件路径（用户要求"也要让我知道是那个文件没找到"），
 * 并保留失败原因；target 为空时回退 sourcePath。
 */
class PreviewUtilTest {

    @Test
    void errorLineIncludesMissingTargetPath() {
        OperationRecord r = new OperationRecord();
        r.setSuccess(false);
        r.setErrorMessage("文件不存在、或不是文件");
        r.setTargetPath(Path.of("/path/THtest/a.txt"));

        String line = PreviewUtil.formatErrorLine(r);
        assertTrue(line.contains("未找到目标文件"), "提示应说明是目标文件未找到: " + line);
        assertTrue(line.contains("/path/THtest/a.txt"), "提示应包含未找到的文件路径: " + line);
        assertTrue(line.contains("文件不存在、或不是文件"), "提示应保留失败原因: " + line);
    }

    @Test
    void errorLineFallsBackToSourcePathWhenTargetNull() {
        OperationRecord r = new OperationRecord();
        r.setSuccess(false);
        r.setErrorMessage("源文件不存在");
        r.setSourcePath(Path.of("/path/delete/b.txt"));

        String line = PreviewUtil.formatErrorLine(r);
        assertTrue(line.contains("/path/delete/b.txt"), "target 为空时应回退 sourcePath 显示文件路径: " + line);
    }
}
