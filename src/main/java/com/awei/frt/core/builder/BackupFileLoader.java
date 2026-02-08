package com.awei.frt.core.builder;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.model.Config;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Author: mou_ren
 * @Date: 2026/1/24 22:17
 * @Description: 备份文件加载器
 */
public class BackupFileLoader {
    // 加载的备份文件列表
    private static Map<String, Path> backupFiles = new HashMap<>();
    // 加载的操作记录集文件列表
    private static Map<String, ProcessingResult> operationRecordFiles = new HashMap<>();

    // 获取操作记录集文件列表
    public static Map<String, ProcessingResult> getOperationRecordFiles() {
        if (operationRecordFiles == null || operationRecordFiles.isEmpty()) {
            // 如果缓存为空，则加载数据
            return loadOperationRecordsFiles();
        }
        // 返回缓存数据
        return operationRecordFiles;
    }


    // 获取备份文件列表
    public static Map<String, Path> getBackupFiles() {
        if (operationRecordFiles == null || backupFiles.isEmpty()) {
            Config config = ConfigLoader.getConfig();
            if (config == null) {
                return null;
            }
            Path backupPath = ConfigLoader.getBackupPath();
            if (!Files.exists(backupPath)) {
                try {
                    Files.createDirectories(backupPath);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("创建备份目录失败: " + e.getMessage());
                    return backupFiles;
                }
            }
            backupFiles = loadBackupFiles(backupPath);
        }
        return backupFiles;
    }

    /**
     * 加载备份文件列表
     * @param backupPath 备份目录路径
     */
    public static Map<String, Path>loadBackupFiles(Path backupPath) {
        if (Files.exists(backupPath)) {
            // 清空旧数据，避免重复加载
            backupFiles.clear();
            try (Stream<Path> paths = Files.walk(backupPath)) {
                paths.filter(Files::isRegularFile) // 只保留文件
                        .forEach(filePath -> {
                            if (backupFiles == null) {
                                backupFiles = new HashMap<>();
                            }
                            String fileMd5 = FileSignUtil.getFileMd5(filePath); // 获取文件的MD5特征码
                            backupFiles.put(fileMd5, filePath);
                        });
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return backupFiles;
    }

    /**
     * 增加备份文件
     * @param filePath 文件路径
     * @return 是否成功
     */
    public static boolean addBackupFile(Path filePath) {
        try {
            if (!Files.isRegularFile(filePath)) {
                System.err.println("备份文件失败: 不是有效文件");
                return false;
            }
            // 检查文件是否已存在于备份文件列表中（存在更改为新路径）
            String fileMd5 = FileSignUtil.getFileMd5(filePath);
            Path backupFilePath = ConfigLoader.getBackupPath().resolve(filePath.getFileName()).normalize();
            if (backupFiles.containsKey(fileMd5)) {
                backupFiles.put(fileMd5, backupFilePath);
                return true;
            }

            // 备份文件
            Config config = ConfigLoader.getConfig();
            Files.copy(filePath, backupFilePath, StandardCopyOption.REPLACE_EXISTING);
            backupFiles.put(fileMd5, backupFilePath);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除备份文件
     * @param filePath 文件路径
     * @return 是否成功
     */
    public static boolean deleteBackupFile(Path filePath) {
        try {
            if (Files.isRegularFile(filePath)) {
                String fileMd5 = FileSignUtil.getFileMd5(filePath);
                if (backupFiles.containsKey(fileMd5)) {
                    Files.delete(backupFiles.get(fileMd5));
                    backupFiles.remove(fileMd5);
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }




    /**
     * 储存操作记录集文件
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean saveOperationRecord(ProcessingResult record) {
        try {
            // 1. 检查record是否为null
            if (record == null || record.getOperationRecords() == null || record.getOperationRecords().isEmpty()) {
                System.err.println("保存操作记录失败: 记录对象为空");
                return false;
            }

            // 2. 检查备份路径是否可用
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath == null) {
                System.err.println("保存操作记录失败: 备份路径为空");
                return false;
            }

            // 3. 确保备份目录存在，不存在则创建
            backupPath = backupPath.resolve("record").normalize(); // 在备份目录下创建record子目录 (用来存放操作记录集文件)
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
            }

            // 4. 验证备份路径确实是目录
            if (!Files.isDirectory(backupPath)) {
                System.err.println("保存操作记录失败: 备份路径不是目录");
                return false;
            }

            // 5. 生成友好的备份文件名（backup-20260131-143045.json格式）
            String fileName = generateFriendlyFileName(record.getResultTime());

            // 6. 构建文件路径并规范化
            Path recordFilePath = backupPath.resolve(fileName + ".json").normalize();

            // 7. 验证文件路径在备份目录内（防止路径遍历攻击）
            if (!recordFilePath.startsWith(backupPath.normalize())) {
                System.err.println("保存操作记录失败: 文件路径非法");
                return false;
            }

            // 8. 检查父目录是否可写
            Path parentDir = recordFilePath.getParent();
            if (parentDir == null || !Files.isWritable(parentDir)) {
                System.err.println("保存操作记录失败: 父目录不可写");
                return false;
            }

            // 9. 使用临时文件进行原子性写入
            Path tempFilePath = recordFilePath.resolveSibling(fileName + ".json.tmp");
            try {
                // 9.1 先写入临时文件
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                objectMapper.writeValue(tempFilePath.toFile(), record);

                // 9.2 写入成功后，原子性地重命名为目标文件
                Files.move(tempFilePath, recordFilePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);

                return true;
            } catch (Exception e) {
                // 9.3 发生异常，删除临时文件，确保不留下不完整文件
                try {
                    if (Files.exists(tempFilePath)) {
                        Files.deleteIfExists(tempFilePath);
                    }
                } catch (IOException deleteEx) {
                    System.err.println("删除临时文件失败: " + deleteEx.getMessage());
                }
                System.err.println("保存操作记录失败: " + e.getMessage());
                e.printStackTrace();
                return false;
            }


        } catch (IOException e) {
            System.err.println("保存操作记录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("保存操作记录失败: 未知错误 - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 生成友好的备份文件名（backup-20260131-143045.json格式）
     * @param resultTime 处理结果时间
     * @return 格式化的文件名（不含扩展名）
     */
    private static String generateFriendlyFileName(LocalDateTime resultTime) {
        LocalDateTime time = (resultTime != null) ? resultTime : LocalDateTime.now();
        String timestamp = time.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return "backup-" + timestamp;
    }

    /**
     * 从文件加载操作记录
     * @param fileName 文件名（不含扩展名）
     * @return 操作记录对象，加载失败返回null
     */
    public static ProcessingResult loadOperationRecord(String fileName) {
        try {
            // 1. 参数校验
            if (fileName == null || fileName.trim().isEmpty()) {
                System.err.println("加载操作记录失败: 文件名为空");
                return null;
            }

            // 2. 检查备份路径是否可用
            Path backupRecordPath = ConfigLoader.getBackupPath().resolve("record").normalize();
            if (backupRecordPath == null) {
                System.err.println("加载操作记录失败: 备份路径为空");
                return null;
            }

            // 3. 清理文件名
            String safeFileName = fileName.trim();
            Path recordFilePath = backupRecordPath.resolve(safeFileName).normalize();

            // 4. 验证文件路径在备份目录内
            if (!recordFilePath.startsWith(backupRecordPath.normalize())) {
                System.err.println("加载操作记录失败: 文件路径非法");
                return null;
            }

            // 5. 检查文件是否存在
            if (!Files.exists(recordFilePath)) {
                System.err.println("加载操作记录失败: 文件不存在 - " + safeFileName);
                return null;
            }

            // 6. 检查是否为常规文件
            if (!Files.isRegularFile(recordFilePath)) {
                System.err.println("加载操作记录失败: 不是常规文件 - " + safeFileName);
                return null;
            }

            // 7. 反序列化
            ObjectMapper objectMapper = new ObjectMapper();
            // 注册Java 8日期时间模块，支持LocalDateTime等类型的反序列化
            objectMapper.registerModule(new JavaTimeModule());
            return objectMapper.readValue(recordFilePath.toFile(), ProcessingResult.class);

        } catch (IOException e) {
            System.err.println("加载操作记录失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("加载操作记录失败: 未知错误 - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 加载所有操作记录集文件
     * @return 操作记录映射表，key为文件名（不含扩展名），value为操作记录对象
     */
    public static Map<String, ProcessingResult> loadOperationRecordsFiles() {
        Map<String, ProcessingResult> results = new HashMap<>();

        try {
            // 1. 检查备份路径是否可用
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath == null) {
                System.err.println("加载操作记录集失败: 备份路径为空");
                return results;
            }

            // 2. 构建 record 子目录路径
            Path recordPath = backupPath.resolve("record").normalize();

            // 3. 检查目录是否存在
            if (!Files.exists(recordPath)) {
                System.err.println("加载操作记录集失败: 记录目录不存在 - " + recordPath);
                return results;
            }

            // 4. 检查是否为目录
            if (!Files.isDirectory(recordPath)) {
                System.err.println("加载操作记录集失败: 路径不是目录 - " + recordPath);
                return results;
            }

            // 5. 遍历目录下的所有 .json 文件
            try (Stream<Path> fileStream = Files.list(recordPath)) {
                List<Path> jsonFiles = fileStream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .filter(path -> !path.toString().endsWith(".json.tmp")) // 排除临时文件
                        .toList();

                // 6. 加载每个文件
                for (Path filePath : jsonFiles) {
                    // 提取文件名（不含扩展名）
                    String fileName = filePath.getFileName().toString();

                    // 加载单个操作记录
                    ProcessingResult record = loadOperationRecord(fileName);

                    if (record != null) {
                        results.put(fileName, record);
                    } else {
                        System.err.println("加载操作记录集失败: 无法加载文件 - " + fileName);
                    }
                }
            }

            // 6.5 按时间排序（降序：最新的在前）
            results = results.entrySet().stream()
                    .sorted(Map.Entry.<String, ProcessingResult>comparingByValue(
                            Comparator.comparing(ProcessingResult::getResultTime).reversed()
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));


            // 7. 更新静态变量
            operationRecordFiles = results;

            return results;

        } catch (IOException e) {
            System.err.println("加载操作记录集失败: " + e.getMessage());
            e.printStackTrace();
            return results;
        } catch (Exception e) {
            System.err.println("加载操作记录集失败: 未知错误 - " + e.getMessage());
            e.printStackTrace();
            return results;
        }
    }


    /**
     * 根据 ProcessingResult 对象，进行文件恢复操作
     * @param result 处理结果对象
     * @param scanner 用于用户交互的 Scanner
     * @return 恢复结果
     */
    public static RestoreResult restoreFromResult(ProcessingResult result, Scanner scanner) {
        RestoreResult restoreResult = new RestoreResult();

        try {
            // 1. 参数校验
            if (result == null) {
                System.err.println("恢复操作失败: 处理结果为空");
                restoreResult.incrementFailure("处理结果为空");
                return restoreResult;
            }

            List<OperationRecord> records = result.getOperationRecords();
            if (records == null || records.isEmpty()) {
                System.err.println("恢复操作失败: 操作记录列表为空");
                restoreResult.incrementFailure("操作记录列表为空");
                return restoreResult;
            }

            // 2. 确保备份文件已加载
            getBackupFiles();
            if (backupFiles == null || backupFiles.isEmpty()) {
                System.err.println("恢复操作失败: 备份文件列表为空");
                restoreResult.incrementFailure("备份文件列表为空");
                return restoreResult;
            }

            // 3. 记录已恢复的操作，用于回滚
            List<OperationRecord> restoredRecords = new ArrayList<>();

            // 4. 倒序遍历操作记录（后进先出）
            for (int i = records.size() - 1; i >= 0; i--) {
                OperationRecord record = records.get(i);

                // 只恢复成功的操作
                if (!record.isSuccess()) {
                    System.out.println("⏭️  跳过失败的操作: " + record.getOperationType() + " - " + record.getTargetPath());
                    continue;
                }

                System.out.println("🔄 恢复操作: " + record.getOperationType() + " - " + record.getTargetPath());

                // 恢复单个记录
                boolean success = restoreSingleRecord(record, restoreResult);

                if (success) {
                    restoredRecords.add(record);
                } else {
                    // 恢复失败，询问用户是否回滚
                    System.err.println("❌ 恢复失败: " + record.getTargetPath());
                    System.out.println("\n恢复过程中遇到失败，是否要回滚已恢复的操作？(y/n)");

                    String choice = scanner.nextLine().trim().toLowerCase();
                    if (choice.equals("y") || choice.equals("yes")) {
                        System.out.println("🔄 开始回滚已恢复的操作...");
                        rollbackRestoredOperations(restoredRecords, restoreResult);
                    }
                    return restoreResult;
                }
            }

            System.out.println("\n✅ 文件恢复完成！");
            System.out.println("📊 成功: " + restoreResult.getSuccessCount());
            System.out.println("📊 失败: " + restoreResult.getFailureCount());

        } catch (Exception e) {
            System.err.println("恢复操作失败: " + e.getMessage());
            e.printStackTrace();
            restoreResult.incrementFailure(e.getMessage());
        }

        return restoreResult;
    }

    /**
     * 恢复单个操作记录
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    private static boolean restoreSingleRecord(OperationRecord record, RestoreResult restoreResult) {
        try {
            String operationType = record.getOperationType();

            switch (operationType) {
                case OperationContext.OPERATION_ADD:
                    return restoreAddOperation(record, restoreResult);
                case OperationContext.OPERATION_REPLACE:
                    return restoreReplaceOperation(record, restoreResult);
                case OperationContext.OPERATION_DELETE:
                    return restoreDeleteOperation(record, restoreResult);
                default:
                    System.err.println("未知操作类型: " + operationType);
                    restoreResult.incrementFailure("未知操作类型: " + operationType);
                    return false;
            }
        } catch (Exception e) {
            System.err.println("恢复单个记录失败: " + e.getMessage());
            e.printStackTrace();
            restoreResult.incrementFailure("恢复失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复 ADD 操作（删除新添加的文件）
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    private static boolean restoreAddOperation(OperationRecord record, RestoreResult restoreResult) {
        try {
            Path targetPath = record.getTargetPath();

            if (targetPath == null) {
                System.err.println("ADD 操作恢复失败: 目标路径为空");
                restoreResult.incrementFailure("目标路径为空");
                return false;
            }

            // 检查文件是否存在
            if (!Files.exists(targetPath)) {
                System.out.println("ℹ️  文件不存在，无需删除: " + targetPath);
                restoreResult.incrementSuccess();
                return true;
            }

            // 删除文件
            Files.delete(targetPath);
            System.out.println("✓ 已删除文件: " + targetPath);
            restoreResult.incrementSuccess();
            return true;

        } catch (Exception e) {
            System.err.println("ADD 操作恢复失败: " + e.getMessage());
            e.printStackTrace();
            restoreResult.incrementFailure("删除文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复 REPLACE 操作（恢复被替换的原文件）
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    private static boolean restoreReplaceOperation(OperationRecord record, RestoreResult restoreResult) {
        try {
            Path targetPath = record.getTargetPath();
            String sourceFileSign = record.getSourceFileSign();

            if (targetPath == null || sourceFileSign == null) {
                System.err.println("REPLACE 操作恢复失败: 目标路径或源文件签名为空");
                restoreResult.incrementFailure("目标路径或源文件签名为空");
                return false;
            }

            // 通过 MD5 查找备份文件
            Path backupFile = findBackupFileBySignature(sourceFileSign);
            if (backupFile == null) {
                System.err.println("REPLACE 操作恢复失败: 未找到备份文件 (MD5: " + sourceFileSign + ")");
                restoreResult.incrementFailure("未找到备份文件");
                return false;
            }

            // 检查备份文件是否存在
            if (!Files.exists(backupFile)) {
                System.err.println("REPLACE 操作恢复失败: 备份文件不存在: " + backupFile);
                restoreResult.incrementFailure("备份文件不存在");
                return false;
            }

            // 确保目标目录存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 恢复文件（复制备份文件到目标位置）
            Files.copy(backupFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("✓ 已恢复文件: " + targetPath);
            restoreResult.incrementSuccess();
            return true;

        } catch (Exception e) {
            System.err.println("REPLACE 操作恢复失败: " + e.getMessage());
            e.printStackTrace();
            restoreResult.incrementFailure("恢复文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复 DELETE 操作（恢复被删除的文件）
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    private static boolean restoreDeleteOperation(OperationRecord record, RestoreResult restoreResult) {
        try {
            Path targetPath = record.getTargetPath();
            String targetFileSign = record.getTargetFileSign();

            if (targetPath == null || targetFileSign == null) {
                System.err.println("DELETE 操作恢复失败: 目标路径或目标文件签名为空");
                restoreResult.incrementFailure("目标路径或目标文件签名为空");
                return false;
            }

            // 检查文件是否已存在
            if (Files.exists(targetPath)) {
                System.out.println("ℹ️  文件已存在，无需恢复: " + targetPath);
                restoreResult.incrementSuccess();
                return true;
            }

            // 通过 MD5 查找备份文件
            Path backupFile = findBackupFileBySignature(targetFileSign);
            if (backupFile == null) {
                System.err.println("DELETE 操作恢复失败: 未找到备份文件 (MD5: " + targetFileSign + ")");
                restoreResult.incrementFailure("未找到备份文件");
                return false;
            }

            // 检查备份文件是否存在
            if (!Files.exists(backupFile)) {
                System.err.println("DELETE 操作恢复失败: 备份文件不存在: " + backupFile);
                restoreResult.incrementFailure("备份文件不存在");
                return false;
            }

            // 确保目标目录存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 恢复文件（复制备份文件到目标位置）
            Files.copy(backupFile, targetPath);
            System.out.println("✓ 已恢复文件: " + targetPath);
            restoreResult.incrementSuccess();
            return true;

        } catch (Exception e) {
            System.err.println("DELETE 操作恢复失败: " + e.getMessage());
            e.printStackTrace();
            restoreResult.incrementFailure("恢复文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 通过 MD5 签名查找备份文件
     * @param md5 MD5 签名
     * @return 备份文件路径，未找到返回 null
     */
    private static Path findBackupFileBySignature(String md5) {
        if (md5 == null || md5.isEmpty()) {
            return null;
        }

        return backupFiles.get(md5);
    }

    /**
     * 回滚已恢复的操作
     * @param restoredRecords 已恢复的操作记录列表
     * @param restoreResult 恢复结果
     */
    private static void rollbackRestoredOperations(List<OperationRecord> restoredRecords, RestoreResult restoreResult) {
        if (restoredRecords == null || restoredRecords.isEmpty()) {
            System.out.println("ℹ️  没有需要回滚的操作");
            return;
        }

        System.out.println("🔄 开始回滚 " + restoredRecords.size() + " 个已恢复的操作...");

        // 对已恢复的操作按正序回滚（即重新执行原来的操作）
        for (OperationRecord record : restoredRecords) {
            try {
                String operationType = record.getOperationType();
                Path targetPath = record.getTargetPath();

                if (operationType.equals("ADD")) {
                    // 回滚 ADD 操作：重新添加文件
                    Path sourcePath = record.getSourcePath();
                    if (sourcePath != null && Files.exists(sourcePath)) {
                        Path parentDir = targetPath.getParent();
                        if (parentDir != null && !Files.exists(parentDir)) {
                            Files.createDirectories(parentDir);
                        }
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("✓ 已回滚 ADD 操作: " + targetPath);
                        restoreResult.incrementRollback();
                    }
                } else if (operationType.equals("REPLACE")) {
                    // 回滚 REPLACE 操作：重新执行替换
                    Path sourcePath = record.getSourcePath();
                    if (sourcePath != null && Files.exists(sourcePath)) {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("✓ 已回滚 REPLACE 操作: " + targetPath);
                        restoreResult.incrementRollback();
                    }
                } else if (operationType.equals("DELETE")) {
                    // 回滚 DELETE 操作：重新删除文件
                    if (Files.exists(targetPath)) {
                        Files.delete(targetPath);
                        System.out.println("✓ 已回滚 DELETE 操作: " + targetPath);
                        restoreResult.incrementRollback();
                    }
                }

            } catch (Exception e) {
                System.err.println("回滚操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("✅ 回滚完成！");
    }


    /**
     * 删除备份记录文件及其相关的备份文件
     * @param fileName 备份记录文件名（不含扩展名）
     * @return 是否成功
     */
    public static boolean deleteBackupRecord(String fileName) {
        try {
            // 1. 参数校验
            if (fileName == null || fileName.trim().isEmpty()) {
                System.err.println("删除备份记录失败: 文件名为空");
                return false;
            }

            // 2. 确保操作记录已加载
            getOperationRecordFiles();

            // 3. 检查备份记录是否存在
            ProcessingResult result = operationRecordFiles.get(fileName);
            if (result == null) {
                System.err.println("删除备份记录失败: 备份记录不存在 - " + fileName);
                return false;
            }

            // 4. 收集该备份记录引用的所有备份文件MD5
            Set<String> usedMd5List = new HashSet<>();
            List<OperationRecord> records = result.getOperationRecords();
            if (records != null) {
                for (OperationRecord record : records) {
                    // 收集 sourceFileSign 和 targetFileSign
                    if (record.getSourceFileSign() != null && !record.getSourceFileSign().isEmpty()) {
                        usedMd5List.add(record.getSourceFileSign());
                    }
                    if (record.getTargetFileSign() != null && !record.getTargetFileSign().isEmpty()) {
                        usedMd5List.add(record.getTargetFileSign());
                    }
                }
            }

            // 5. 从 operationRecordFiles 中移除该记录
            operationRecordFiles.remove(fileName);

            // 6. 检查每个MD5是否还被其他备份记录引用
            getBackupFiles();
            for (String md5 : usedMd5List) {
                boolean isUsed = false;
                for (ProcessingResult otherResult : operationRecordFiles.values()) {
                    List<OperationRecord> otherRecords = otherResult.getOperationRecords();
                    if (otherRecords != null) {
                        for (OperationRecord record : otherRecords) {
                            if (md5.equals(record.getSourceFileSign()) || md5.equals(record.getTargetFileSign())) {
                                isUsed = true;
                                break;
                            }
                        }
                        if (isUsed) {
                            break;
                        }
                    }
                }

                // 如果没有被其他记录引用，删除对应的备份文件
                if (!isUsed) {
                    Path backupFilePath = backupFiles.get(md5);
                    if (backupFilePath != null && Files.exists(backupFilePath)) {
                        boolean deleted = deleteBackupFile(backupFilePath);
                        if (deleted) {
                            System.out.println("✓ 已删除未使用的备份文件: " + backupFilePath.getFileName());
                        }
                    }
                }
            }

            // 7. 删除备份记录文件
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath != null) {
                Path recordPath = backupPath.resolve("record").resolve(fileName).normalize();
                if (Files.exists(recordPath)) {
                    Files.delete(recordPath);
                    System.out.println("✓ 已删除备份记录文件: " + fileName);
                    return true;
                }
            }

            System.err.println("删除备份记录文件失败: 文件不存在");
            // 恢复删除操作
            return false;

        } catch (Exception e) {
            System.err.println("删除备份记录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


}
