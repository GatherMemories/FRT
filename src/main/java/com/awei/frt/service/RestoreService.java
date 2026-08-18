package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;
import com.awei.frt.ui.ConsoleUserPrompter;
import com.awei.frt.ui.UserPrompter;
import com.awei.frt.util.LoggerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 恢复服务
 * 用于从备份中恢复文件
 */
public class RestoreService {
    private final Config config;
    private final UserPrompter prompter;

    public RestoreService(Config config, Scanner scanner) {
        this(config, new ConsoleUserPrompter(scanner));
    }

    public RestoreService(Config config, UserPrompter prompter) {
        this.config = config;
        this.prompter = prompter;
    }

    /**
     * 执行恢复操作
     */
    public void executeRestore() {
        try {
            // 1. 加载所有操作记录
            Map<String, ProcessingResult> operationRecords = BackupFileLoader.getOperationRecordFiles();

            if (operationRecords == null || operationRecords.isEmpty()) {
                System.out.println("\n=========================================");
                System.out.println("[执行] 恢复操作");
                System.out.println("=========================================");
                LoggerUtil.logWarn("[失败] 没有找到可用的备份记录，请先执行更新操作以创建备份");
                return;
            }

            // 按时间排序备份记录（）
            List<String> fileNames = new ArrayList<>(operationRecords.keySet());

            // 循环菜单，允许用户选择多个备份进行恢复
            while (true) {
                System.out.println("\n=========================================");
                System.out.println("[执行] 恢复操作");
                System.out.println("=========================================");

                // 2. 显示可用备份列表（按时间倒序）
                System.out.println("\n[列表] 可用的备份记录 (按时间倒序):");
                System.out.println("-----------------------------------------");

                for (int i = 0; i < fileNames.size(); i++) {
                    String fileName = fileNames.get(i);
                    ProcessingResult result = operationRecords.get(fileName);
                    System.out.printf("%d. %s\n", (i + 1), formatBackupInfo(fileName, result));
                }
                System.out.println("-----------------------------------------");
                System.out.println("0. 返回主菜单");
                System.out.println("-1. 删除备份记录");
                System.out.println("1-" + fileNames.size() + ". 恢复备份记录");
                System.out.print("\n请输入选项 (0：返回, -1：删除, 1-" + fileNames.size() + "：恢复): ");

                // 3. 用户选择
                String choice = prompter.readLine();

                if (choice.equals("0")) {
                    System.out.println("[返回] 已返回主菜单");
                    return;
                }

                if (choice.equals("-1")) {
                    // 删除备份记录
                    System.out.print("\n请输入要删除的备份记录编号，支持单个编号或范围 (如 3 或 1-5) (1-" + fileNames.size() + "): ");
                    String deleteChoice = prompter.readLine();

                    try {
                        List<Integer> deleteIndexes = new ArrayList<>();

                        // 解析输入：可能是单个数字或范围
                        if (deleteChoice.contains("-")) {
                            // 范围删除，如 1-5
                            String[] range = deleteChoice.split("-");
                            if (range.length == 2) {
                                int start = Integer.parseInt(range[0].trim()) - 1;
                                int end = Integer.parseInt(range[1].trim()) - 1;

                                // 确保范围有效
                                if (start < 0 || end >= fileNames.size() || start > end) {
                                    System.out.println("[失败] 无效的范围");
                                    continue;
                                }

                                // 添加范围内的所有索引
                                for (int i = start; i <= end; i++) {
                                    deleteIndexes.add(i);
                                }
                            } else {
                                System.out.println("[失败] 无效的格式");
                                continue;
                            }
                        } else {
                            // 单个删除
                            int deleteIndex = Integer.parseInt(deleteChoice) - 1;
                            if (deleteIndex < 0 || deleteIndex >= fileNames.size()) {
                                System.out.println("[失败] 无效的选项");
                                continue;
                            }
                            deleteIndexes.add(deleteIndex);
                        }

                        // 显示要删除的备份列表
                        System.out.println("\n[FILE] 要删除的备份记录 (" + deleteIndexes.size() + "个):");
                        System.out.println("-----------------------------------------");
                        for (int i = 0; i < deleteIndexes.size(); i++) {
                            int index = deleteIndexes.get(i);
                            String fileName = fileNames.get(index);
                            ProcessingResult result = operationRecords.get(fileName);
                            System.out.printf("%d. [%s] %s | 成功:%d 失败:%d\n",
                                (i + 1), fileName,
                                result.getResultTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                                result.getSuccessCount(), result.getErrorCount());
                        }
                        System.out.println("-----------------------------------------");

                        // 确认删除
                        System.out.print("\n确认要删除这 " + deleteIndexes.size() + " 个备份记录吗？此操作不可逆！(y/n): ");
                        String confirmDelete = prompter.readLine().toLowerCase();

                        if (!confirmDelete.equals("y") && !confirmDelete.equals("yes")) {
                            LoggerUtil.logInfo("[信息] 已取消删除操作");
                            continue;
                        }

                        // 执行删除（从后往前删除，避免索引变化）
                        LoggerUtil.logInfo("[删除] 开始删除备份记录...");
                        int successCount = 0;
                        int failCount = 0;
                        for (int i = deleteIndexes.size() - 1; i >= 0; i--) {
                            int index = deleteIndexes.get(i);
                            String deleteFileName = fileNames.get(index);
                            boolean success = BackupFileLoader.deleteBackupRecord(deleteFileName);
                            if (success) {
                                successCount++;
                                operationRecords.remove(deleteFileName);
                            } else {
                                failCount++;
                            }
                            fileNames.remove(index);
                        }

                        LoggerUtil.logInfo("[成功] 备份记录删除完成: 成功 " + successCount + " 个, 失败 " + failCount + " 个");

                    } catch (NumberFormatException e) {
                        System.out.println("[失败] 无效的输入，请输入数字或范围格式(如 1-5)");
                    }
                    continue;
                }

                try {
                    int index = Integer.parseInt(choice) - 1;
                    if (index < 0 || index >= fileNames.size()) {
                        System.out.println("[失败] 无效的选项");
                        continue;
                    }

                    // 4. 获取选中的备份记录
                    String selectedFileName = fileNames.get(index);
                    ProcessingResult selectedResult = operationRecords.get(selectedFileName);

                    // 5. 显示详细信息
                    System.out.println("\n[FILE] 备份详细信息:");
                    System.out.println("-----------------------------------------");
                    System.out.println("文件名: " + selectedFileName);
                    System.out.println("时间: " + selectedResult.getResultTime()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    System.out.println("成功: " + selectedResult.getSuccessCount());
                    System.out.println("跳过: " + selectedResult.getSkipCount());
                    System.out.println("失败: " + selectedResult.getErrorCount());
                    System.out.println("总操作数: " + selectedResult.getOperationRecords().size());
                    System.out.println("-----------------------------------------");

                    // 显示操作列表
                    System.out.println("\n[列表] 操作详情:");
                    System.out.println("-----------------------------------------");
                    List<com.awei.frt.model.OperationRecord> records = selectedResult.getOperationRecords();
                    for (int i = 0; i < records.size(); i++) {
                        com.awei.frt.model.OperationRecord record = records.get(i);
                        String status = record.isSuccess() ? "[成功]" : "[失败]";
                        String opType = record.getOperationType();
                        String opTypeDisplay = switch (opType) {
                            case OperationContext.OPERATION_ADD -> "新增";
                            case OperationContext.OPERATION_REPLACE -> "更新";
                            case OperationContext.OPERATION_DELETE -> "删除";
                            default -> opType;
                        };
                        String timeStr = record.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                        System.out.printf("%d. %s %s %s | %s\n",
                            (i + 1),
                            status,
                            opTypeDisplay,
                            timeStr,
                            record.getTargetPath() != null ? record.getTargetPath().toString() : "N/A");
                    }
                    System.out.println("-----------------------------------------");

                    // 6. 确认恢复
                    System.out.print("\n确认要从此备份恢复系统吗？(y/n): ");
                    String confirm = prompter.readLine().toLowerCase();

                    if (!confirm.equals("y") && !confirm.equals("yes")) {
                        LoggerUtil.logInfo("[信息] 已取消恢复操作");
                        continue;
                    }

                    // 7. 执行恢复
                    LoggerUtil.logInfo("[执行] 开始执行恢复操作...");
                    RestoreResult restoreResult = BackupFileLoader.restoreFromResult(selectedResult, prompter);

                    // 8. 显示恢复结果
                    System.out.println("\n=========================================");
                    System.out.println("[STATS] 恢复结果统计");
                    System.out.println("=========================================");
                    LoggerUtil.logInfo("[STATS] 恢复时间: " + restoreResult.getRestoreTime()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            + ", 成功 " + restoreResult.getSuccessCount()
                            + ", 失败 " + restoreResult.getFailureCount()
                            + ", 回滚 " + restoreResult.getRollbackCount());

                    if (restoreResult.getFailureCount() > 0) {
                        System.out.println("\n失败信息:");
                        for (String msg : restoreResult.getFailureMessages()) {
                            System.out.println("  - " + msg);
                        }
                    }

                    System.out.println("-----------------------------------------");
                    if (restoreResult.isFullSuccess()) {
                        LoggerUtil.logInfo("[成功] 系统已成功恢复到操作前的状态");
                    } else if (restoreResult.getRollbackCount() > 0) {
                        LoggerUtil.logWarn("[警告] 系统已回滚，但可能处于部分恢复状态");
                    } else {
                        LoggerUtil.logError("[失败] 系统恢复失败，可能处于不一致状态");
                    }

                    // 按任意键继续
                    System.out.println("\n请按任意键继续...");
                    prompter.readLine();

                } catch (NumberFormatException e) {
                    System.out.println("[失败] 无效的输入，请输入数字");
                }
            }

        } catch (Exception e) {
            LoggerUtil.logException("恢复操作失败", e);
        }
    }

    /**
     * 格式化备份信息
     * @param fileName 文件名
     * @param result 处理结果
     * @return 格式化的字符串
     */
    private String formatBackupInfo(String fileName, ProcessingResult result) {
        LocalDateTime time = result.getResultTime();
        String timeStr = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("[%s] %s | 成功:%d 失败:%d", fileName, timeStr,
            result.getSuccessCount(), result.getErrorCount());
    }

}
