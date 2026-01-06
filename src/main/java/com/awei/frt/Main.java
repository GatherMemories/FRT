package com.awei.frt;

import com.awei.frt.service.FileReplaceService;
import com.awei.frt.service.RestoreService;
import com.awei.frt.utils.ConfigLoader;

import java.util.Scanner;

/**
 * FRT - 文件替换工具主程序
 */
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        try {
            System.out.println("========================================");
            System.out.println("       FRT - 文件替换工具");
            System.out.println("========================================");
            System.out.println();
            
            // 加载配置
            var config = ConfigLoader.loadConfig();
            System.out.println("配置加载完成:");
            System.out.println("  更新目录: " + config.getUpdatePath());
            System.out.println("  目标目录: " + config.getTargetPath());
            System.out.println("  删除目录: " + config.getDeletePath());
            System.out.println("  备份目录: " + config.getBackupPath());
            System.out.println();
            
            // 创建服务
            var replaceService = new FileReplaceService(config);
            var restoreService = new RestoreService(config);
            
            // 显示菜单
            while (true) {
                System.out.println("请选择操作:");
                System.out.println("1. 执行文件替换");
                System.out.println("2. 执行文件删除");
                System.out.println("3. 恢复备份");
                System.out.println("4. 退出");
                System.out.print("请输入选项 (1-4): ");
                
                String choice = scanner.nextLine().trim();
                System.out.println();
                
                switch (choice) {
                    case "1":
                        replaceService.executeReplace();
                        break;
                    case "2":
                        replaceService.executeDelete();
                        break;
                    case "3":
                        restoreService.executeRestore();
                        break;
                    case "4":
                        System.out.println("程序退出");
                        scanner.close();
                        return;
                    default:
                        System.out.println("无效选项，请重新输入");
                        break;
                }
                System.out.println();
            }
            
        } catch (Exception e) {
            System.err.println("程序启动失败: " + e.getMessage());
            e.printStackTrace();
            scanner.close();
            System.exit(1);
        }
    }
}