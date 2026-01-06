package com.awei.frt.test2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 测试实际加载的配置文件位置
 */
public class ConfigLocationTest {
    
    public static void main(String[] args) {
        ConfigLocationTest tester = new ConfigLocationTest();
        tester.testConfigLoading();
    }
    
    public void testConfigLoading() {
        System.out.println("=".repeat(60));
        System.out.println("           配置文件加载位置检测");
        System.out.println("=".repeat(60));
        
        // 获取项目根目录
        Path projectRoot = getProjectRoot();
        Path parentDirectory = projectRoot.getParent();
        
        System.out.println("📍 基础路径信息:");
        System.out.println("   项目根目录: " + projectRoot);
        System.out.println("   FRT同级目录: " + (parentDirectory != null ? parentDirectory : "无"));
        System.out.println("   当前工作目录: " + Paths.get("").toAbsolutePath());
        System.out.println();
        
        // 检查所有可能的配置文件位置
        System.out.println("📄 配置文件存在性检查:");
        
        Path[] configPaths = {
            parentDirectory != null ? parentDirectory.resolve("config.json") : null,
            projectRoot.resolve("config.json"),
            projectRoot.resolve("src/main/resources/config.json")
        };
        
        String[] locationNames = {"FRT同级目录", "项目根目录", "resources目录"};
        
        for (int i = 0; i < configPaths.length; i++) {
            if (configPaths[i] != null) {
                boolean exists = Files.exists(configPaths[i]);
                System.out.println("   " + locationNames[i] + ": " + (exists ? "✓ 存在" : "✗ 不存在"));
                System.out.println("      路径: " + configPaths[i]);
                
                if (exists) {
                    try {
                        long size = Files.size(configPaths[i]);
                        String content = Files.readString(configPaths[i]);
                        System.out.println("      大小: " + size + " 字节");
                        System.out.println("      内容预览: " + content.substring(0, Math.min(100, content.length())) + (content.length() > 100 ? "..." : ""));
                    } catch (Exception e) {
                        System.out.println("      读取失败: " + e.getMessage());
                    }
                }
            } else {
                System.out.println("   " + locationNames[i] + ": ✗ 路径无效");
            }
            System.out.println();
        }
        
        // 简单的配置解析测试
        System.out.println("⚙️  路径解析测试:");
        testPathResolution(parentDirectory, projectRoot);
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("检测完成！");
    }
    
    private void testPathResolution(Path parentDirectory, Path projectRoot) {
        // 模拟配置路径（基于当前实际配置）
        String[] configPaths = {
            "../THtest",
            "../update", 
            "../delete",
            "../old",
            "../logs"
        };
        
        String[] pathNames = {
            "目标路径",
            "更新路径",
            "删除路径",
            "备份路径",
            "日志路径"
        };
        
        for (int i = 0; i < configPaths.length; i++) {
            String configPath = configPaths[i];
            String pathName = pathNames[i];
            
            Path resolvedPath = projectRoot.resolve(configPath).normalize();
            boolean exists = Files.exists(resolvedPath);
            
            System.out.println("   " + pathName + ":");
            System.out.println("      配置: " + configPath);
            System.out.println("      解析: " + resolvedPath);
            System.out.println("      存在: " + (exists ? "✓" : "✗"));
            
            if (exists && Files.isDirectory(resolvedPath)) {
                try {
                    long count = Files.list(resolvedPath).count();
                    System.out.println("      内容: " + count + " 个项目");
                } catch (Exception e) {
                    System.out.println("      内容: 无法读取");
                }
            }
            System.out.println();
        }
    }
    
    private Path getProjectRoot() {
        try {
            Path currentPath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            
            if (currentPath.toString().contains("classes")) {
                return currentPath.getParent().getParent();
            }
            
            if (currentPath.toString().contains("target")) {
                return currentPath.getParent();
            }
            
            return Paths.get("").toAbsolutePath();
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }
}