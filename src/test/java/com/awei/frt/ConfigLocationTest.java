package com.awei.frt;

import com.awei.frt.model.Config;
import com.awei.frt.utils.ConfigLoader;

import java.io.IOException;
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
                    } catch (IOException e) {
                        System.out.println("      读取失败: " + e.getMessage());
                    }
                }
            } else {
                System.out.println("   " + locationNames[i] + ": ✗ 路径无效");
            }
            System.out.println();
        }
        
        // 实际加载配置
        System.out.println("⚙️  实际配置加载测试:");
        System.out.println("   调用 ConfigLoader.loadConfig()...");
        
        try {
            Config config = ConfigLoader.loadConfig();
            
            if (config != null) {
                System.out.println("   ✓ 配置加载成功");
                System.out.println("   配置内容:");
                System.out.println("      目标路径: " + config.getTargetPath());
                System.out.println("      更新路径: " + config.getUpdatePath());
                System.out.println("      删除路径: " + config.getDeletePath());
                System.out.println("      备份路径: " + config.getBackupPath());
                System.out.println("      日志路径: " + config.getLogPath());
                
                // 解析实际路径
                System.out.println("\n   路径解析结果:");
                resolvePath("目标路径", config.getTargetPath(), projectRoot);
                resolvePath("更新路径", config.getUpdatePath(), projectRoot);
                resolvePath("删除路径", config.getDeletePath(), projectRoot);
                resolvePath("备份路径", config.getBackupPath(), projectRoot);
                resolvePath("日志路径", config.getLogPath(), projectRoot);
                
            } else {
                System.out.println("   ✗ 配置加载失败");
            }
            
        } catch (Exception e) {
            System.out.println("   ✗ 配置加载异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("检测完成！");
    }
    
    private void resolvePath(String name, String configPath, Path projectRoot) {
        if (configPath == null || configPath.trim().isEmpty()) {
            System.out.println("      " + name + ": [未配置]");
            return;
        }
        
        Path resolved;
        if (Paths.get(configPath).isAbsolute()) {
            resolved = Paths.get(configPath).normalize();
        } else {
            resolved = projectRoot.resolve(configPath).normalize();
        }
        
        boolean exists = Files.exists(resolved);
        System.out.println("      " + name + ": " + configPath + " -> " + resolved + " " + (exists ? "✓" : "✗"));
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