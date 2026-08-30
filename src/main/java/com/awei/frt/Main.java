package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;
import com.awei.frt.service.CoreConfigWizard;
import com.awei.frt.service.FileDeleteService;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.service.PluginCompiler;
import com.awei.frt.service.RestoreService;
import com.awei.frt.service.RuleConfigWizard;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.util.BuildInfo;
import com.awei.frt.util.LoggerUtil;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * 主程序入口
 * 多层级文件夹更新工具：控制台模式（默认）与图形界面模式（--ui）
 */
public class Main {

    public static void main(String[] args) {
        // Swing 文本抗锯齿：默认部分平台关闭导致文字锯齿感强，入口最先开启（UITheme 类加载时也会设置，双保险）
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // UI 模式入口：java -jar FRT.jar --ui（或 java -cp ... com.awei.frt.ui.MainUI）
        if (args.length > 0 && "--ui".equals(args[0])) {
            com.awei.frt.ui.MainUI.main(args);
            return;
        }

        LoggerUtil logger = null;// 日志工具类
        Scanner scanner = null;

        try {
            // 初始化日志系统
            logger = LoggerUtil.getInstance(null);

            // 加载配置
            Config config = ConfigLoader.getConfig();

            if (config == null) {
                // 配置加载失败，退出程序
                LoggerUtil.logError("[失败] 配置加载失败，请检查配置文件");
                System.exit(1);
                return;
            }

            LoggerUtil.logInfo("=========================================");
            LoggerUtil.logInfo(BuildInfo.displayName() + " 启动");
            if (BuildInfo.GITHUB_URL != null && !BuildInfo.GITHUB_URL.isBlank()) {
                LoggerUtil.logInfo("GitHub: " + BuildInfo.GITHUB_URL);
            }
            LoggerUtil.logInfo("=========================================");

            scanner = new Scanner(System.in);

            // 检测是否有未完成的操作会话（上次异常中断遗留），提示用户恢复
            checkInterruptedSession(scanner);

            // 残留备份过多时提醒清理（不影响启动）
            BackupFileLoader.warnOrphanBackupsIfNeeded();

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
                System.out.println("4. 规则生成（生成/编辑匹配规则配置文件）");
                System.out.println("5. 清理残留备份");
                System.out.println("6. 核心配置（设置目标/更新/删除/备份路径、日志级别）");
                System.out.println("7. 打包插件（把 plugins/ 目录的 .java 策略源码编译成 jar）");
                System.out.println("8. 退出");
                System.out.print("请输入选项 (1-8): ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        LoggerUtil.logInfo("[执行] 执行更新操作（增加、替换）...");
                        updateService.updateExecute();
                        break;
                    case "2":
                        LoggerUtil.logInfo("[删除] 执行删除操作...");
                        deleteService.deleteExecute();
                        break;
                    case "3":
                        LoggerUtil.logInfo("[执行] 执行恢复操作...");
                        restoreService.executeRestore();
                        break;
                    case "4":
                        LoggerUtil.logInfo("[规则生成] 生成/编辑匹配规则配置文件...");
                        new RuleConfigWizard(config, scanner).start();
                        break;
                    case "5":
                        LoggerUtil.logInfo("[清理] 执行残留备份文件清理...");
                        BackupFileLoader.cleanupOrphanBackupFiles(scanner);
                        break;
                    case "6":
                        LoggerUtil.logInfo("[配置] 执行核心配置编写向导...");
                        new CoreConfigWizard(config, scanner).start();
                        break;
                    case "7":
                        LoggerUtil.logInfo("[打包] 编译打包 plugins/ 目录的 .java 策略源码...");
                        PluginCompiler.CompileResult buildResult = PluginCompiler.compilePluginsToJar(Path.of("plugins"));
                        if (buildResult.isSuccess()) {
                            LoggerUtil.logInfo("[成功] " + buildResult.getMessage());
                        } else {
                            LoggerUtil.logErrorMsg("[失败] " + buildResult.getMessage());
                        }
                        break;
                    case "8":
                        LoggerUtil.logInfo("程序退出");
                        return;
                    default:
                        LoggerUtil.logWarn("[失败] 无效选项，请重新选择");
                        break;
                }

                System.out.println(""); // 空行分隔
            }

        } catch (Throwable e) {
            // 捕获 Exception 和 Error（如 NoClassDefFoundError），确保任何崩溃都有日志与提示
            // （LoggerUtil.logException 内部会自动初始化日志系统，logger 为 null 也能记录）
            LoggerUtil.logException("[失败] 程序执行失败", e);
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
            LoggerUtil.logWarn("[警告] 检测到未完成的操作会话（可能是上次异常中断导致）");
            System.out.println("=========================================");
            System.out.print("是否立即恢复该会话，将系统恢复到操作前的状态？(y/n): ");

            String choice = scanner.nextLine().trim().toLowerCase();
            if (!choice.equals("y") && !choice.equals("yes")) {
                LoggerUtil.logInfo("[信息] 已跳过，会话记录将保留（可稍后处理）");
                return;
            }

            ProcessingResult sessionResult = BackupFileLoader.loadSessionRecord();
            if (sessionResult == null) {
                LoggerUtil.logError("[失败] 会话记录加载失败");
                return;
            }

            LoggerUtil.logInfo("[执行] 开始执行恢复操作...");
            RestoreResult restoreResult = BackupFileLoader.restoreFromResult(sessionResult, scanner);

            LoggerUtil.logInfo("[STATS] 恢复结果统计: 成功 " + restoreResult.getSuccessCount()
                    + ", 失败 " + restoreResult.getFailureCount()
                    + ", 回滚 " + restoreResult.getRollbackCount());

            if (restoreResult.isFullSuccess()) {
                LoggerUtil.logInfo("[成功] 系统已成功恢复到操作前的状态");
                // 恢复成功，清除会话记录
                BackupFileLoader.clearSessionRecord();
            } else {
                LoggerUtil.logWarn("[警告] 恢复未完全成功，会话记录已保留，可再次尝试");
            }
        } catch (Exception e) {
            LoggerUtil.logException("会话恢复处理异常", e);
        }
    }
}
