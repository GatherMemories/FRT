package com.awei.frt;

import com.awei.frt.model.Config;
import com.awei.frt.utils.ConfigLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目结构检测工具类（可直接运行）
 * 使用方法：直接运行main方法或通过IDE运行
 */
public class StructureChecker {
    
    public static void main(String[] args) {
        StructureChecker checker = new StructureChecker();
        checker.runFullCheck();
    }
    
    private Path projectRoot;
    private Path parentDirectory;
    private Config config;
    
    public void runFullCheck() {
        System.out.println("=".repeat(60));
        System.out.println("           FRT 项目结构完整性检测");
        System.out.println("=".repeat(60));
        
        // 初始化
        initPaths();
        loadConfig();
        
        // 执行各项检测
        checkProjectInfo();
        checkConfigFiles();
        checkRequiredDirectories();
        checkDirectoryPermissions();
        checkConfigContent();
        
        // 生成报告
        generateReport();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("检测完成！");
    }
    
    private void initPaths() {
        try {
            // 获取项目根目录
            Path currentPath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            
            if (currentPath.toString().contains("classes")) {
                projectRoot = currentPath.getParent().getParent();
            } else if (currentPath.toString().contains("target")) {
                projectRoot = currentPath.getParent();
            } else {
                projectRoot = Paths.get("").toAbsolutePath();
            }
            
            parentDirectory = projectRoot.getParent();
            
        } catch (Exception e) {
            projectRoot = Paths.get("").toAbsolutePath();
            parentDirectory = projectRoot.getParent();
        }
    }
    
    private void loadConfig() {
        config = ConfigLoader.loadConfig();
    }
    
    private void checkProjectInfo() {
        System.out.println("\n📍 项目基本信息:");
        System.out.println("   项目根目录: " + projectRoot);
        System.out.println("FRT同级目录: " + (parentDirectory != null ? parentDirectory : "无法获取"));
        System.out.println("当前工作目录: " + Paths.get("").toAbsolutePath());
    }
    
    private void checkConfigFiles() {
        System.out.println("\n📄 配置文件检测:");
        
        // 检查各级目录的config.json
        String[] locations = {"FRT同级目录", "项目根目录", "resources目录"};
        Path[] paths = {
            parentDirectory != null ? parentDirectory.resolve("config.json") : null,
            projectRoot.resolve("config.json"),
            projectRoot.resolve("src/main/resources/config.json")
        };
        
        for (int i = 0; i < locations.length; i++) {
            if (paths[i] != null) {
                boolean exists = Files.exists(paths[i]);
                String status = exists ? "✓ 存在" : "✗ 不存在";
                System.out.println("   " + locations[i] + ": " + status + " - " + paths[i]);
                
                if (exists) {
                    try {
                        long size = Files.size(paths[i]);
                        System.out.println("              └─ 大小: " + size + " 字节");
                    } catch (IOException e) {
                        System.out.println("              └─ 无法读取大小");
                    }
                }
            } else {
                System.out.println("   " + locations[i] + ": ✗ 路径无效");
            }
        }
    }
    
    private void checkRequiredDirectories() {
        System.out.println("\n📂 必需文件夹检测:");
        
        List<String> missingDirs = new ArrayList<>();
        
        // 按需求文档检测FRT同级目录的文件夹
        if (parentDirectory != null) {
            String[] requiredDirs = {"update", "delete", "old", "logs", "THtest"};
            for (String dir : requiredDirs) {
                Path dirPath = parentDirectory.resolve(dir);
                boolean exists = Files.exists(dirPath);
                System.out.println("   " + dir + ": " + (exists ? "✓ 存在" : "✗ 缺失"));
                
                if (!exists) {
                    missingDirs.add(dir);
                } else if (Files.isDirectory(dirPath)) {
                    try {
                        long count = Files.list(dirPath).count();
                        System.out.println("              └─ 包含 " + count + " 个项目");
                    } catch (IOException e) {
                        System.out.println("              └─ 无法读取内容");
                    }
                }
            }
        } else {
            System.out.println("   ✗ 无法访问同级目录");
        }
        
        // 检测配置文件中指定的路径
        if (config != null) {
            System.out.println("\n   配置文件中指定的路径:");
            checkConfigPath("目标路径", config.getTargetPath());
            checkConfigPath("更新路径", config.getUpdatePath());
            checkConfigPath("删除路径", config.getDeletePath());
            checkConfigPath("备份路径", config.getBackupPath());
            checkConfigPath("日志路径", config.getLogPath());
        }
    }
    
    private void checkConfigPath(String name, String configPath) {
        if (configPath == null || configPath.trim().isEmpty()) {
            System.out.println("   " + name + ": ⚠️ 配置为空");
            return;
        }
        
        Path resolvedPath = projectRoot.resolve(configPath);
        boolean exists = Files.exists(resolvedPath);
        System.out.println("   " + name + ": " + (exists ? "✓ 存在" : "✗ 缺失") + " - " + configPath);
    }
    
    private void checkDirectoryPermissions() {
        System.out.println("\n🔒 文件夹权限检测:");
        
        if (parentDirectory != null) {
            String[] dirs = {"update", "delete", "old", "logs", "THtest"};
            for (String dir : dirs) {
                Path dirPath = parentDirectory.resolve(dir);
                if (Files.exists(dirPath)) {
                    boolean readable = Files.isReadable(dirPath);
                    boolean writable = Files.isWritable(dirPath);
                    
                    if (readable && writable) {
                        System.out.println("   " + dir + ": ✓ 权限正常");
                    } else {
                        String issues = "";
                        if (!readable) issues += "不可读 ";
                        if (!writable) issues += "不可写 ";
                        System.out.println("   " + dir + ": ⚠️ " + issues.trim());
                    }
                } else {
                    System.out.println("   " + dir + " - 不存在，跳过权限检测");
                }
            }
        } else {
            System.out.println("   ✗ 无法访问同级目录");
        }
    }
    
    private void checkConfigContent() {
        System.out.println("\n📝 配置文件内容分析:");
        
        // 分析配置文件内容
        Path[] configPaths = {
            parentDirectory != null ? parentDirectory.resolve("config.json") : null,
            projectRoot.resolve("config.json")
        };
        
        String[] pathNames = {"FRT同级配置", "项目根目录配置"};
        
        for (int i = 0; i < configPaths.length; i++) {
            if (configPaths[i] != null && Files.exists(configPaths[i])) {
                try {
                    String content = Files.readString(configPaths[i]);
                    System.out.println("   " + pathNames[i] + ":");
                    System.out.println("              大小: " + content.length() + " 字符");
                    
                    // 检查关键字段
                    String[] fields = {"targetPath", "updatePath", "deletePath", "backupPath", "logPath"};
                    for (String field : fields) {
                        boolean contains = content.contains(field);
                        System.out.println("              " + field + ": " + (contains ? "✓" : "✗"));
                    }
                    
                } catch (IOException e) {
                    System.out.println("   " + pathNames[i] + ": ⚠️ 读取失败 - " + e.getMessage());
                }
            }
        }
    }
    
    private void generateReport() {
        System.out.println("\n📊 检测结果总结:");
        
        int totalItems = 0;
        int successItems = 0;
        int warningItems = 0;
        int errorItems = 0;
        
        // 统计配置文件
        Path[] configPaths = {
            parentDirectory != null ? parentDirectory.resolve("config.json") : null,
            projectRoot.resolve("config.json")
        };
        
        for (Path configPath : configPaths) {
            totalItems++;
            if (configPath != null && Files.exists(configPath)) {
                successItems++;
            } else {
                errorItems++;
            }
        }
        
        // 统计必需文件夹
        if (parentDirectory != null) {
            String[] requiredDirs = {"update", "delete", "old", "logs", "THtest"};
            for (String dir : requiredDirs) {
                totalItems++;
                Path dirPath = parentDirectory.resolve(dir);
                if (Files.exists(dirPath)) {
                    successItems++;
                    if (!Files.isWritable(dirPath)) {
                        warningItems++;
                    }
                } else {
                    errorItems++;
                }
            }
        }
        
        System.out.println("   总检测项: " + totalItems);
        System.out.println("   ✓ 正常: " + successItems);
        System.out.println("   ⚠️ 警告: " + warningItems);
        System.out.println("   ✗ 错误: " + errorItems);
        
        // 给出建议
        System.out.println("\n💡 建议:");
        if (errorItems > 0) {
            System.out.println("   - 存在缺失的必需文件夹，请按照需求文档创建");
        }
        if (warningItems > 0) {
            System.out.println("   - 部分文件夹权限异常，请检查读写权限");
        }
        if (successItems == totalItems) {
            System.out.println("   - 项目结构完整，可以正常运行程序");
        }
    }
}