package com.awei.frt;

import com.awei.frt.model.Config;
import com.awei.frt.service.FileDeleteService;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.service.RestoreService;
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
                System.out.println("4. 退出");
                System.out.print("请输入选项 (1-4): ");

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
                        logger.logInfo("程序退出");
                        return;
                    default:
                        logger.logWarn("[失败] 无效选项，请重新选择");
                        break;
                }

                System.out.println(""); // 空行分隔
            }

        } catch (Exception e) {
            if (logger != null) {
                logger.logError("[失败] 程序执行失败: " + e.getMessage(), e);
            } else {
                System.err.println("[失败] 程序执行失败: " + e.getMessage());
                e.printStackTrace();
            }
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
}
