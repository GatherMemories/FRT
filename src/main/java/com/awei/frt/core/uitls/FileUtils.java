package com.awei.frt.core.uitls;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件工具类
 * 提供基础的文件操作功能，包括复制、移动、删除等
 * 所有操作都会生成操作记录，并返回处理结果
 *
 * @Author: mou_ren
 * @Date: 2026/1/18 21:09
 */
public class FileUtils {

    /**
     * 根据 OperationRecord 执行对应的文件操作
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
     * 增加文件（复制）
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean addFile(Path sourcePath, Path targetPath, OperationRecord record) {
        try {
            if (sourcePath == null || !Files.exists(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源文件不存在");
                return false;
            }

            if (targetPath == null) {
                record.setSuccess(false);
                record.setErrorMessage("目标路径为空");
                return false;
            }

            Path targetParent = targetPath.getParent();
            if (targetParent != null && !Files.exists(targetParent)) {
                Files.createDirectories(targetParent);
            }

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            record.setSourceFileSign(calculateFileSign(sourcePath));
            record.setTargetFileSign(calculateFileSign(targetPath));
            record.setSuccess(true);

            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("复制失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 增加目录（递归复制）
     *
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean addDirectory(Path sourcePath, Path targetPath, OperationRecord record) {
        try {
            if (sourcePath == null || !Files.exists(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源目录不存在");
                return false;
            }

            if (!Files.isDirectory(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源路径不是目录");
                return false;
            }

            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }

            final boolean[] success = {true};
            final List<String> errors = new ArrayList<>();

            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    Path relative = sourcePath.relativize(dir);
                    Path targetDir = targetPath.resolve(relative);

                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Path relative = sourcePath.relativize(file);
                    Path targetFile = targetPath.resolve(relative);

                    try {
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        success[0] = false;
                        errors.add(file + ": " + e.getMessage());
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            if (!success[0]) {
                record.setSuccess(false);
                record.setErrorMessage("部分文件复制失败: " + String.join("; ", errors));
                return false;
            }

            record.setSuccess(true);
            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("复制目录失败: " + e.getMessage());
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
            if (filePath == null || !Files.exists(filePath)) {
                record.setSuccess(false);
                record.setErrorMessage("文件不存在");
                return false;
            }

            record.setSourceFileSign(calculateFileSign(filePath));
            Files.delete(filePath);

            record.setSuccess(true);
            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("删除失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除目录（递归删除）
     *
     * @param directoryPath 目录路径
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean deleteDirectory(Path directoryPath, OperationRecord record) {
        try {
            if (directoryPath == null || !Files.exists(directoryPath)) {
                record.setSuccess(false);
                record.setErrorMessage("目录不存在");
                return false;
            }

            if (!Files.isDirectory(directoryPath)) {
                record.setSuccess(false);
                record.setErrorMessage("路径不是目录");
                return false;
            }

            Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            record.setSuccess(true);
            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("删除目录失败: " + e.getMessage());
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
            if (sourcePath == null || !Files.exists(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源文件不存在");
                return false;
            }

            if (targetPath == null) {
                record.setSuccess(false);
                record.setErrorMessage("目标路径为空");
                return false;
            }

            if (!Files.exists(targetPath)) {
                return addFile(sourcePath, targetPath, record);
            }

            Path targetParent = targetPath.getParent();
            if (targetParent != null && !Files.exists(targetParent)) {
                Files.createDirectories(targetParent);
            }

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            record.setSourceFileSign(calculateFileSign(sourcePath));
            record.setTargetFileSign(calculateFileSign(targetPath));
            record.setSuccess(true);

            return true;
        } catch (IOException e) {
            record.setSuccess(false);
            record.setErrorMessage("替换失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 替换目录（递归替换）
     *
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean replaceDirectory(Path sourcePath, Path targetPath, OperationRecord record) {
        try {
            if (sourcePath == null || !Files.exists(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源目录不存在");
                return false;
            }

            if (!Files.isDirectory(sourcePath)) {
                record.setSuccess(false);
                record.setErrorMessage("源路径不是目录");
                return false;
            }

            if (!Files.exists(targetPath)) {
                return addDirectory(sourcePath, targetPath, record);
            }

            if (!deleteDirectory(targetPath, record)) {
                return false;
            }

            return addDirectory(sourcePath, targetPath, record);
        } catch (Exception e) {
            record.setSuccess(false);
            record.setErrorMessage("替换目录失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 计算文件签名（MD5）
     *
     * @param filePath 文件路径
     * @return MD5 签名，失败返回 null
     */
    public static String calculateFileSign(Path filePath) {
        try {
            if (filePath == null || !Files.exists(filePath)) {
                return null;
            }

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] digest = md.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        }
    }

    /**
     * 计算文件签名（指定算法）
     *
     * @param filePath 文件路径
     * @param algorithm 算法名称（MD5, SHA-1, SHA-256）
     * @return 签名，失败返回 null
     */
    public static String calculateFileSign(Path filePath, String algorithm) {
        try {
            if (filePath == null || !Files.exists(filePath)) {
                return null;
            }

            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] digest = md.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
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
