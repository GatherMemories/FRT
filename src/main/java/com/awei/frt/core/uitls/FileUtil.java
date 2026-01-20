package com.awei.frt.core.uitls;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * 文件工具类
 * 提供基础的文件操作功能，包括复制、移动、删除等
 * 所有操作都会生成操作记录，并返回处理结果
 *
 * @Author: mou_ren
 * @Date: 2026/1/18 21:09
 */
public class FileUtil {

    /**
     * 根据 OperationRecord-->operationType->执行对应的文件操作
     *
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean executeOperation(OperationRecord record) {
        if (record == null) {
            return false;
        }

        String operationType = record.getOperationType();
        Path sourcePath = record.getSourcePath();
        Path targetPath = record.getTargetPath();

        switch (operationType) {
            case OperationContext.OPERATION_ADD:
                return addFile(sourcePath, targetPath, record);
            case OperationContext.OPERATION_DELETE:
                return deleteFile(sourcePath, record);
            case OperationContext.OPERATION_REPLACE:
                return replaceFile(sourcePath, targetPath, record);
            default:
                record.setSuccess(false);
                record.setErrorMessage("不支持的操作类型: " + operationType);
                return false;
        }
    }

    /**
     * 批量执行操作记录
     *
     * @param records 操作记录列表
     * @return 处理结果
     */
    public static ProcessingResult executeOperations(List<OperationRecord> records) {
        ProcessingResult result = new ProcessingResult();

        for (OperationRecord record : records) {
            boolean success = executeOperation(record);
            result.addOperationRecord(record);
        }

        return result;
    }

    /**
     * 增加文件
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @param record 操作记录 （从外传入，写入记录）
     * @return 是否成功
     */
    public static boolean addFile(Path sourcePath, Path targetPath, OperationRecord record) {
        try {
            if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源文件不存在");
                return false;
            }

            if (targetPath == null) {
                record.setSuccess(false);
                record.setErrorMessage("目标路径为空");
                return false;
            }

            // 判断目标路径文件是否存在，如果存在取消操作（因为不是新增操作）
            if (Files.isRegularFile(targetPath)) {
                record.setSuccess(false);
                record.setErrorMessage("目标文件已存在--新增操作失败");
                return false;
            }


            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            record.setOperationType(OperationContext.OPERATION_ADD);
            record.setSourcePath(sourcePath);
            record.setTargetPath(targetPath);
            record.setSourceFileSign(FileSignUtil.getFileMd5(sourcePath));
            record.setTargetFileSign(FileSignUtil.getFileMd5(targetPath));
            record.setSuccess(true);

            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("新增文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 替换文件
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean replaceFile(Path sourcePath, Path targetPath, OperationRecord record) {
        try {
            if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源文件不存在");
                return false;
            }

            if (targetPath == null || !Files.isRegularFile(targetPath)) {
                record.setSuccess(false);
                record.setErrorMessage("目标路径不存在");
                return false;
            }

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            record.setOperationType(OperationContext.OPERATION_REPLACE);
            record.setSourcePath(sourcePath);
            record.setTargetPath(targetPath);
            record.setSourceFileSign(FileSignUtil.getFileMd5(sourcePath));
            record.setTargetFileSign(FileSignUtil.getFileMd5(targetPath));
            record.setSuccess(true);

            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("替换文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除文件
     *
     * @param filePath 文件路径
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean deleteFile(Path filePath, OperationRecord record) {
        try {
            if (filePath == null || !Files.isRegularFile(filePath)) {
                record.setSuccess(false);
                record.setErrorMessage("文件不存在、或不是文件");
                return false;
            }

            Files.delete(filePath);

            record.setOperationType(OperationContext.OPERATION_DELETE);
            record.setSourcePath(filePath);
            record.setSourceFileSign(FileSignUtil.getFileMd5(filePath));
            record.setSuccess(true);
            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("删除文件失败: " + e.getMessage());
            return false;
        }
    }





    /**
     * 创建操作记录
     *
     * @param strategyType 策略类型
     * @param operationType 操作类型
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     * @return 操作记录
     */
    public static OperationRecord createRecord(String strategyType, String operationType,
                                                 Path sourcePath, Path targetPath) {
        OperationRecord record = new OperationRecord();
        record.setStrategyType(strategyType);
        record.setOperationType(operationType);
        record.setSourcePath(sourcePath);
        record.setTargetPath(targetPath);
        return record;
    }
}
