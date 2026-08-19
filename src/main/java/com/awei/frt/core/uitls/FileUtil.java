package com.awei.frt.core.uitls;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import java.io.IOException;
import java.nio.file.*;

/**
 * 文件工具类
 * 提供基础的文件操作功能，包括复制、移动、删除等
 * 所有操作都会生成操作记录，并返回处理结果
 * 三个文件操作方法均支持 dryRun 预览模式：只校验并标记"可执行"，不真正改动文件/备份
 *
 * @Author: mou_ren
 * @Date: 2026/1/18 21:09
 */
public class FileUtil {

    /**
     * 增加文件
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @param record 操作记录 （从外传入，写入记录）
     * @return 是否成功
     */
    public static boolean addFile(Path sourcePath, Path targetPath, OperationRecord record) {
        return addFile(sourcePath, targetPath, record, false);
    }

    /**
     * 增加文件（支持预览模式）
     * @param dryRun true=只校验并标记可执行，不执行文件 IO
     */
    public static boolean addFile(Path sourcePath, Path targetPath, OperationRecord record, boolean dryRun) {
        try {
            record.setOperationType(OperationContext.OPERATION_ADD);
            record.setSourcePath(sourcePath);
            record.setTargetPath(targetPath);

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

            // 参数校验通过后再计算文件特征码（避免空参数NPE）
            record.setSourceFileSign(FileSignUtil.getFileMd5(sourcePath));
            record.setTargetFileSign(FileSignUtil.getFileMd5(targetPath));

            // 判断目标路径文件是否存在，如果存在取消操作（因为不是新增操作）
            if (Files.isRegularFile(targetPath)) {
                record.setSuccess(false);
                record.setErrorMessage("目标文件已存在--新增操作失败");
                return false;
            }
            if (dryRun) {
                // 预览模式：校验通过即视为可执行，不落盘
                record.setSuccess(true);
                return true;
            }
            // 确保目标父目录存在（update 中的子目录结构在目标侧可能不存在）
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            // 添加备份文件（新增不需要备份）
//            BackupFileLoader.addBackupFile(targetPath);

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
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
        return replaceFile(sourcePath, targetPath, record, false);
    }

    /**
     * 替换文件（支持预览模式）
     * @param dryRun true=只校验并标记可执行，不执行文件 IO / 备份
     */
    public static boolean replaceFile(Path sourcePath, Path targetPath, OperationRecord record, boolean dryRun) {
        try {
            record.setOperationType(OperationContext.OPERATION_REPLACE);
            record.setSourcePath(sourcePath);
            record.setTargetPath(targetPath);

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

            // 参数校验通过后再计算文件特征码（避免空参数NPE）
            record.setSourceFileSign(FileSignUtil.getFileMd5(sourcePath));
            record.setTargetFileSign(FileSignUtil.getFileMd5(targetPath));

            if (dryRun) {
                // 预览模式：校验通过即视为可执行，不落盘
                record.setSuccess(true);
                return true;
            }

            // 替换备份文件
            BackupFileLoader.addBackupFile(targetPath);

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
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
        return deleteFile(filePath, record, false);
    }

    /**
     * 删除文件（支持预览模式）
     * @param dryRun true=只校验并标记可执行，不执行文件 IO / 备份
     */
    public static boolean deleteFile(Path filePath, OperationRecord record, boolean dryRun) {
        try {
            record.setOperationType(OperationContext.OPERATION_DELETE);
            record.setSourcePath(filePath);
            record.setTargetPath(filePath);

            if (filePath == null || !Files.isRegularFile(filePath)) {
                record.setSuccess(false);
                record.setErrorMessage("文件不存在、或不是文件");
                return false;
            }

            // 参数校验通过后再计算文件特征码（避免空参数NPE）
            record.setSourceFileSign(FileSignUtil.getFileMd5(filePath));
            record.setTargetFileSign(FileSignUtil.getFileMd5(filePath));

            if (dryRun) {
                // 预览模式：校验通过即视为可执行，不落盘
                record.setSuccess(true);
                return true;
            }

            // 添加备份文件
            BackupFileLoader.addBackupFile(filePath);

            Files.delete(filePath);
            record.setSuccess(true);
            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("删除文件失败: " + e.getMessage());
            return false;
        }
    }

}
