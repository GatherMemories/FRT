package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;
import com.awei.frt.service.FileDeleteService;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.service.RestoreService;
import com.awei.frt.service.RuleConfigWizard;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.util.LoggerUtil;

import java.util.Scanner;

/**
 * 主程序入口
 * 演示多层级文件夹更新系统的使用
 */
public class Main {

    public static void main(String[] args) {
        LoggerUtil logger = null;// 日志工具类
        Scanner scanner = null;

        try {
            // 初始化日志系统
            logger = LoggerUtil.getInstance(null);

            // 加载配置
            Config config = ConfigLoader.getConfig();

            if (config == null) {
                // 配置加载失败，退出程序
                System.err.println("[失败] 配置加载失败，请检查配置文件");
                System.exit(1);
                return;
            }

            logger.logInfo("=========================================");
            logger.logInfo("FRT - 多层级文件夹更新系统启动");
            logger.logInfo("=========================================");

            scanner = new Scanner(System.in);

            // 检测是否有未完成的操作会话（上次异常中断遗留），提示用户恢复
            checkInterruptedSession(scanner);

            // 创建服务实例
            FileUpdateServiceNew updateService = new FileUpdateServiceNew(config, scanner);
            FileDeleteService deleteService = new FileDeleteService(config, scanner);
            RestoreService restoreService = new RestoreService(config, scanner);


            // 显示菜单
            while (true) {
                System.out.println("=========================================");
                System.out.println("[列表] 请选择操作:");
                System.out.println("1. 更新文件");
                System.out.println("2. 删除文件");
                System.out.println("3. 执行恢复操作");
                System.out.println("4. 生成/编辑匹配规则配置文件");
                System.out.println("5. 退出");
                System.out.print("请输入选项 (1-5): ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        logger.logInfo("[执行] 执行更新操作（增加、替换）...");
                        updateService.updateExecute();
                        break;
                    case "2":
                        logger.logInfo("[删除] 执行删除操作...");
                        deleteService.deleteExecute();
                        break;
                    case "3":
                        logger.logInfo("[执行] 执行恢复操作...");
                        restoreService.executeRestore();
                        break;
                    case "4":
                        logger.logInfo("[向导] 生成/编辑匹配规则配置文件...");
                        new RuleConfigWizard(config, scanner).start();
                        break;
                    case "5":
                        logger.logInfo("程序退出");
                        return;
                    default:
                        logger.logWarn("[失败] 无效选项，请重新选择");
                        break;
                }

                System.out.println(""); // 空行分隔
            }

        } catch (Throwable e) {
            // 捕获 Exception 和 Error（如 NoClassDefFoundError），确保任何崩溃都有日志与提示
            if (logger != null) {
                logger.logError("[失败] 程序执行失败: " + e, e);
            } else {
                System.err.println("[失败] 程序执行失败: " + e);
                e.printStackTrace();
            }
            System.err.println("[提示] 请查看日志 logs/frt.log 了解详细错误信息");
        } finally {
            // 确保资源正确释放
            if (logger != null) {
                logger.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * 检测并处理未完成的操作会话
     * 操作过程中异常中断会留下 session-current.json，这里提示用户恢复
     * @param scanner 输入扫描器
     */
    private static void checkInterruptedSession(Scanner scanner) {
        try {
            if (!BackupFileLoader.hasSessionRecord()) {
                return;
            }

            System.out.println("\n=========================================");
            System.out.println("[警告] 检测到未完成的操作会话（可能是上次异常中断导致）");
            System.out.println("=========================================");
            System.out.print("是否立即恢复该会话，将系统恢复到操作前的状态？(y/n): ");

            String choice = scanner.nextLine().trim().toLowerCase();
            if (!choice.equals("y") && !choice.equals("yes")) {
                System.out.println("[信息] 已跳过，会话记录将保留（可稍后处理）");
                return;
            }

            ProcessingResult sessionResult = BackupFileLoader.loadSessionRecord();
            if (sessionResult == null) {
                System.out.println("[失败] 会话记录加载失败");
                return;
            }

            System.out.println("\n[执行] 开始执行恢复操作...");
            RestoreResult restoreResult = BackupFileLoader.restoreFromResult(sessionResult, scanner);

            System.out.println("\n[STATS] 恢复结果统计:");
            System.out.println("   成功恢复: " + restoreResult.getSuccessCount());
            System.out.println("   恢复失败: " + restoreResult.getFailureCount());
            System.out.println("   回滚操作: " + restoreResult.getRollbackCount());

            if (restoreResult.isFullSuccess()) {
                System.out.println("[成功] 系统已成功恢复到操作前的状态");
                // 恢复成功，清除会话记录
                BackupFileLoader.clearSessionRecord();
            } else {
                System.out.println("[警告] 恢复未完全成功，会话记录已保留，可再次尝试");
            }
        } catch (Exception e) {
            System.err.println("[失败] 会话恢复处理异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
