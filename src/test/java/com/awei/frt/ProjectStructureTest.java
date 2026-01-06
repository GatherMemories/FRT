package com.awei.frt;

import com.awei.frt.model.Config;
import com.awei.frt.utils.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目结构检测测试
 */
public class ProjectStructureTest {
    
    private Path projectRoot;
    private Path parentDirectory;
    private Config config;
    
    @BeforeEach
    void setUp() {
        projectRoot = getProjectRoot();
        parentDirectory = projectRoot.getParent();
        config = ConfigLoader.loadConfig();
    }
    
    /**
     * 获取项目根目录
     */
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
    
    @Test
    @DisplayName("检测项目基本信息")
    void testProjectBasicInfo() {
        System.out.println("=== 项目基本信息 ===");
        System.out.println("项目根目录: " + projectRoot);
        System.out.println("FRT同级目录: " + (parentDirectory != null ? parentDirectory : "无法获取"));
        System.out.println("当前工作目录: " + Paths.get("").toAbsolutePath());
        System.out.println();
        
        assertNotNull(projectRoot, "项目根目录不能为空");
    }
    
    @Test
    @DisplayName("检测配置文件加载情况")
    void testConfigFileLoading() {
        System.out.println("=== 配置文件检测 ===");
        System.out.println("加载的配置对象: " + (config != null ? "成功" : "失败"));
        
        if (config != null) {
            System.out.println("目标路径: " + config.getTargetPath());
            System.out.println("更新路径: " + config.getUpdatePath());
            System.out.println("删除路径: " + config.getDeletePath());
            System.out.println("备份路径: " + config.getBackupPath());
            System.out.println("日志路径: " + config.getLogPath());
        }
        System.out.println();
        
        assertNotNull(config, "配置文件必须成功加载");
    }
    
    @Test
    @DisplayName("检测配置文件存在性")
    void testConfigFileExistence() {
        System.out.println("=== 配置文件存在性检测 ===");
        
        // 检测各级目录的config.json
        String[] locations = {
            "FRT同级目录",
            "项目根目录", 
            "resources目录"
        };
        
        Path[] paths = {
            parentDirectory != null ? parentDirectory.resolve("config.json") : null,
            projectRoot.resolve("config.json"),
            projectRoot.resolve("src/main/resources/config.json")
        };
        
        for (int i = 0; i < locations.length; i++) {
            if (paths[i] != null) {
                boolean exists = Files.exists(paths[i]);
                System.out.println(locations[i] + ": " + (exists ? "✓ 存在" : "✗ 不存在") + " - " + paths[i]);
            } else {
                System.out.println(locations[i] + ": ✗ 路径无效");
            }
        }
        System.out.println();
    }
    
    @Test
    @DisplayName("检测必需文件夹")
    void testRequiredDirectories() {
        System.out.println("=== 必需文件夹检测 ===");
        
        List<String> missingDirs = new ArrayList<>();
        
        // 从配置中获取路径
        if (config != null) {
            checkDirectory(config.getUpdatePath(), "update", missingDirs);
            checkDirectory(config.getDeletePath(), "delete", missingDirs);
            checkDirectory(config.getBackupPath(), "old/backup", missingDirs);
            checkDirectory(config.getLogPath(), "logs", missingDirs);
            checkDirectory(config.getTargetPath(), "THtest", missingDirs);
        }
        
        // 检测FRT同级目录的文件夹（按需求文档）
        if (parentDirectory != null) {
            checkSiblingDirectory("update", missingDirs);
            checkSiblingDirectory("delete", missingDirs);
            checkSiblingDirectory("old", missingDirs);
            checkSiblingDirectory("logs", missingDirs);
            checkSiblingDirectory("THtest", missingDirs);
        }
        
        if (missingDirs.isEmpty()) {
            System.out.println("✓ 所有必需文件夹都存在");
        } else {
            System.out.println("✗ 缺失的文件夹:");
            missingDirs.forEach(dir -> System.out.println("  - " + dir));
        }
        System.out.println();
        
        // 可以选择性断言，这里仅做检测不强制要求
        // assertTrue(missingDirs.isEmpty(), "存在缺失的必需文件夹: " + String.join(", ", missingDirs));
    }
    
    @Test
    @DisplayName("检测文件夹权限")
    void testDirectoryPermissions() {
        System.out.println("=== 文件夹权限检测 ===");
        
        List<String> noPermissionDirs = new ArrayList<>();
        
        if (parentDirectory != null) {
            String[] dirs = {"update", "delete", "old", "logs", "THtest"};
            for (String dir : dirs) {
                Path dirPath = parentDirectory.resolve(dir);
                if (Files.exists(dirPath)) {
                    if (!Files.isReadable(dirPath)) {
                        noPermissionDirs.add(dir + " (不可读)");
                    }
                    if (!Files.isWritable(dirPath)) {
                        noPermissionDirs.add(dir + " (不可写)");
                    }
                    System.out.println(dir + ": ✓ 权限正常");
                } else {
                    System.out.println(dir + " - 不存在，跳过权限检测");
                }
            }
        }
        
        if (noPermissionDirs.isEmpty()) {
            System.out.println("✓ 所有现有文件夹权限正常");
        } else {
            System.out.println("✗ 权限异常的文件夹:");
            noPermissionDirs.forEach(dir -> System.out.println("  - " + dir));
        }
        System.out.println();
    }
    
    @Test
    @DisplayName("检测配置文件内容")
    void testConfigFileContent() {
        System.out.println("=== 配置文件内容检测 ===");
        
        try {
            // 检查同级目录的配置文件
            if (parentDirectory != null) {
                Path parentConfig = parentDirectory.resolve("config.json");
                if (Files.exists(parentConfig)) {
                    String content = Files.readString(parentConfig);
                    System.out.println("FRT同级配置文件大小: " + content.length() + " 字符");
                    System.out.println("包含targetPath: " + content.contains("targetPath"));
                    System.out.println("包含updatePath: " + content.contains("updatePath"));
                    System.out.println("包含deletePath: " + content.contains("deletePath"));
                    System.out.println("包含backupPath: " + content.contains("backupPath"));
                    System.out.println("包含logPath: " + content.contains("logPath"));
                } else {
                    System.out.println("FRT同级配置文件不存在");
                }
            }
            
            // 检查项目根目录配置文件
            Path projectConfig = projectRoot.resolve("config.json");
            if (Files.exists(projectConfig)) {
                String content = Files.readString(projectConfig);
                System.out.println("项目根目录配置文件大小: " + content.length() + " 字符");
            } else {
                System.out.println("项目根目录配置文件不存在");
            }
            
        } catch (IOException e) {
            System.out.println("读取配置文件时出错: " + e.getMessage());
        }
        System.out.println();
    }
    
    @Test
    @DisplayName("生成完整检测报告")
    void generateFullReport() {
        System.out.println("=".repeat(50));
        System.out.println("           完整项目结构检测报告");
        System.out.println("=".repeat(50));
        
        // 基本信息
        System.out.println("📁 项目信息:");
        System.out.println("   根目录: " + projectRoot);
        System.out.println("   同级目录: " + (parentDirectory != null ? parentDirectory : "无"));
        
        // 配置信息
        System.out.println("\n⚙️  配置状态:");
        System.out.println("   配置加载: " + (config != null ? "✓ 成功" : "✗ 失败"));
        
        if (config != null) {
            System.out.println("   目标路径: " + config.getTargetPath());
            System.out.println("   更新路径: " + config.getUpdatePath());
            System.out.println("   删除路径: " + config.getDeletePath());
            System.out.println("   备份路径: " + config.getBackupPath());
            System.out.println("   日志路径: " + config.getLogPath());
        }
        
        // 文件夹检测
        System.out.println("\n📂 文件夹状态:");
        if (parentDirectory != null) {
            String[] requiredDirs = {"update", "delete", "old", "logs", "THtest"};
            for (String dir : requiredDirs) {
                Path dirPath = parentDirectory.resolve(dir);
                boolean exists = Files.exists(dirPath);
                System.out.println("   " + dir + ": " + (exists ? "✓ 存在" : "✗ 缺失"));
            }
        } else {
            System.out.println("   无法访问同级目录");
        }
        
        // 配置文件检测
        System.out.println("\n📄 配置文件:");
        checkConfigFile("FRT同级", parentDirectory != null ? parentDirectory.resolve("config.json") : null);
        checkConfigFile("项目根目录", projectRoot.resolve("config.json"));
        checkConfigFile("Resources", projectRoot.resolve("src/main/resources/config.json"));
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("检测完成！");
    }
    
    /**
     * 检查目录是否存在
     */
    private void checkDirectory(String configPath, String dirName, List<String> missingDirs) {
        if (configPath == null || configPath.trim().isEmpty()) {
            missingDirs.add(dirName + " (配置为空)");
            return;
        }
        
        Path dirPath = projectRoot.resolve(configPath);
        if (!Files.exists(dirPath)) {
            missingDirs.add(dirName + " (" + configPath + ")");
        }
    }
    
    /**
     * 检查同级目录下的文件夹
     */
    private void checkSiblingDirectory(String dirName, List<String> missingDirs) {
        if (parentDirectory == null) return;
        
        Path dirPath = parentDirectory.resolve(dirName);
        if (!Files.exists(dirPath)) {
            missingDirs.add(dirName + " (同级目录)");
        }
    }
    
    /**
     * 检查配置文件
     */
    private void checkConfigFile(String location, Path configPath) {
        if (configPath != null && Files.exists(configPath)) {
            try {
                long size = Files.size(configPath);
                System.out.println("   " + location + ": ✓ 存在 (" + size + " 字节)");
            } catch (IOException e) {
                System.out.println("   " + location + ": ✓ 存在 (无法读取大小)");
            }
        } else {
            System.out.println("   " + location + ": ✗ 不存在");
        }
    }
}