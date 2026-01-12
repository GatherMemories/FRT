package com.awei.frt;

import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.Config;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.service.RestoreService;
import com.awei.frt.utils.ConfigLoader;

import java.util.Scanner;

/**
 * 主程序入口
 * 演示多层级文件夹更新系统的使用
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("🚀 FRT - 多层级文件夹更新系统启动");
        System.out.println("=========================================");

        try {
            // 加载配置
            Config config = ConfigLoader.loadConfig();
            if (config == null) {
                System.err.println("❌ 配置加载失败，使用默认配置");
                config = new Config();
            }

            System.out.println("📋 配置信息:");
            System.out.println("   基准目录: " + config.getBaseDirectory());
            System.out.println("   更新目录: " + config.getUpdatePath());
            System.out.println("   目标目录: " + config.getTargetPath());
            System.out.println("   备份目录: " + config.getBackupPath());
            System.out.println();

            Scanner scanner = new Scanner(System.in);

            // 创建服务实例
            FileUpdateServiceNew updateService = new FileUpdateServiceNew(config, scanner);
            RestoreService restoreService = new RestoreService(config, scanner);

            // 显示菜单
            while (true) {
                System.out.println("=========================================");
                System.out.println("📋 请选择操作:");
                System.out.println("1. 更新");
                System.out.println("2. 执行恢复操作");
                System.out.println("3. 退出");
                System.out.print("请输入选项 (1-3): ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        System.out.println("\n🔄 执行更新操作（增、删、改）...");
                        updateService.updateExecute();
                        break;
                    case "2":
                        System.out.println("\n🔄 执行恢复操作...");
                        restoreService.executeRestore();
                        break;
                    case "3":
                        System.out.println("\n👋 程序退出，再见！");
                        return;
                    default:
                        System.out.println("\n❌ 无效选项，请重新选择");
                        break;
                }

                System.out.println(); // 空行分隔
            }

        } catch (Exception e) {
            System.err.println("❌ 程序执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
