package com.awei.frt.core.builder;

import com.awei.frt.model.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置加载器
 * 负责加载和解析配置文件
 */
public class ConfigLoader {

    // 加载好的配置文件
    private static Config config;
    // 更新文件夹（绝对路径）
    private static Path updatePath;
    // 目标文件夹（绝对路径）
    private static Path targetPath;
    // 删除文件夹（绝对路径）
    private static Path deletePath;
    // 备份文件夹（绝对路径）
    private static Path backupPath;
    // logs文件夹（绝对路径）
    private static Path logsPath;

    // Jackson ObjectMapper（线程安全，复用）
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new SimpleModule()
                    .addDeserializer(Path.class, new JsonDeserializer<Path>() {
                        @Override
                        public Path deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                            String pathString = p.getValueAsString();
                            if (pathString != null && !pathString.isEmpty()) {
                                return Paths.get(pathString);
                            }
                            return null;
                        }
                    }));

    public static Config getConfig() {
        if (config == null) {
            config = loadConfig();
        }
        return config;
    }
    /**
     * 加载配置
     * 按优先级顺序查找配置文件：
     * 1. FRT项目根目录外部的config.json
     * 2. resources目录下的config.json
     * 3. 使用默认配置
     */
    private static Config loadConfig() {
        // 1. 尝试从FRT项目根目录外部加载
        Path externalConfig = getExternalConfigPath();
        if (Files.exists(externalConfig)) {
            System.out.println("📋 从外部加载配置: " + externalConfig);
            return loadFromPath(externalConfig);
        }

        // 2. 尝试从resources目录加载
        Path resourceConfig = getResourceConfigPath();
        if (resourceConfig != null && Files.exists(resourceConfig)) {
            System.out.println("📋 从resources加载配置: " + resourceConfig);
            return loadFromPath(resourceConfig);
        }

        // 3. 使用默认配置
        System.out.println("📋 使用默认配置");
        config = new Config();
        // 设置静态变量（包含文件夹验证和创建逻辑）
        setStaticPath(config);
        return config;
    }

    /**
     * 从指定路径加载配置
     */
    private static Config loadFromPath(Path configPath) {
        try {
            String jsonContent = Files.readString(configPath);
            // 去除UTF-8 BOM（如果有）
            if (jsonContent.startsWith("\uFEFF")) {
                jsonContent = jsonContent.substring(1);
            }
            Config config = parseConfig(jsonContent);
            return config;
        } catch (Exception e) {
            System.err.println("⚠️  加载配置失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 使用Jackson解析配置JSON
     */
    private static Config parseConfig(String json) {
        try {
            Config config = objectMapper.readValue(json, Config.class);

            // 配置检查
            if (config == null) {
                throw new IllegalArgumentException("配置文件内容为空");
            }

            // 设置静态变量（包含文件夹验证和创建逻辑）
            setStaticPath(config);

            return config;
        } catch (Exception e) {
            System.err.println("⚠️  解析配置失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取外部配置路径（与FRT项目同级的目录）
     */
    private static Path getExternalConfigPath() {
        // 获取当前工作目录
        Path currentDir = Paths.get(".").normalize().toAbsolutePath();

        // 获取当前项目目录的父目录，即FRT项目目录
        Path parentDir = currentDir.getParent();

        // 如果获取失败，则回退到当前目录
        if (parentDir == null) {
            parentDir = currentDir;
            System.out.println("无法获取上级目录，使用当前目录: " + parentDir);
        }

        return parentDir.resolve("config.json");
    }

    /**
     * 获取资源目录配置路径
     */
    private static Path getResourceConfigPath() {
        try {
            // 尝试从classpath获取资源路径,Path.of()平台兼容性好
            return Path.of("src","main","resources","config.json");
        } catch (Exception e) {
            // 如果无法获取资源路径，返回null
            return null;
        }
    }

    /**
     * 验证并确保文件夹存在
     * @param basePath 基准目录
     * @param configPath 配置路径（可为空）
     * @param defaultPath 默认路径
     * @param folderName 文件夹名称（用于日志输出）
     * @return 实际使用的路径
     */
    private static Path validateAndEnsureDirectory(Path basePath, Path configPath,
                                                   Path defaultPath, String folderName) {
        Path actualPath;

        if (configPath == null || configPath.toString().isEmpty() || defaultPath.equals(configPath)) {
            // 使用默认值并创建文件夹
            actualPath = basePath.resolve(defaultPath).normalize();
            try {
                Files.createDirectories(actualPath);
                System.out.println("✅ 使用默认" + folderName + ": " + actualPath);
            } catch (IOException e) {
                System.err.println("⚠️  创建" + folderName + "失败: " + e.getMessage());
            }
        } else {
            // 验证配置的路径
            actualPath = basePath.resolve(configPath).normalize();
            if (!Files.exists(actualPath)) {
                System.err.println("⚠️  配置错误: " + folderName + "不存在: " + actualPath);
                throw new IllegalArgumentException(folderName + "不存在");
//                // 不存在则创建
//                try {
//                    Files.createDirectories(actualPath);
//                    System.out.println("✅ 创建" + folderName + ": " + actualPath);
//                } catch (IOException e) {
//                    System.err.println("⚠️  创建" + folderName + "失败: " + e.getMessage());
//                }
            } else if (!Files.isDirectory(actualPath)) {
                // 存在但不是文件夹
                System.err.println("⚠️  配置错误: " + folderName + "不是有效文件夹: " + actualPath);
                throw new IllegalArgumentException(folderName + "不是有效文件夹");
            } else {
                System.out.println("✅ " + folderName + "有效: " + actualPath);
            }
        }

        return actualPath;
    }

    /**
     * 设置静态变量（配置的绝对路径）
     * 验证并确保所有文件夹存在
     */
    private static void setStaticPath(Config config) {
        if (config == null) {
            return;
        }

        Path basePath = config.getBaseDirectory();

        // 定义默认值
        Path defaultTargetPath = Path.of("THtest");
        Path defaultUpdatePath = Path.of("update");
        Path defaultDeletePath = Path.of("delete");
        Path defaultBackupPath = Path.of("backup");
        Path defaultLogPath = Path.of("logs");
        String defaultLogLevel = "INFO";

        System.out.println("📋 配置信息:");
        System.out.println("   基准目录: " + config.getBaseDirectory());
        System.out.println("   更新目录: " + config.getUpdatePath());
        System.out.println("   删除目录: " + config.getDeletePath());
        System.out.println("   目标目录: " + config.getTargetPath());
        System.out.println("   备份目录: " + config.getBackupPath());
        System.out.println("   日志目录: " + config.getLogPath());
        System.out.println("   日志级别: " + config.getLogLevel());
        System.out.println();

        // 验证并设置各个文件夹路径
        targetPath = validateAndEnsureDirectory(basePath, config.getTargetPath(),
                                               defaultTargetPath, "目标目录");
        updatePath = validateAndEnsureDirectory(basePath, config.getUpdatePath(),
                                               defaultUpdatePath, "更新目录");
        deletePath = validateAndEnsureDirectory(basePath, config.getDeletePath(),
                                               defaultDeletePath, "删除目录");
        backupPath = validateAndEnsureDirectory(basePath, config.getBackupPath(),
                                               defaultBackupPath, "备份目录");
        logsPath = validateAndEnsureDirectory(basePath, config.getLogPath(),
                                             defaultLogPath, "日志目录");

        config.setLogLevel(config.getLogLevel().isEmpty() ? defaultLogLevel : config.getLogLevel());
        config.setTargetPath(targetPath.getFileName());
        config.setUpdatePath(updatePath.getFileName());
        config.setDeletePath(deletePath.getFileName());
        config.setBackupPath(backupPath.getFileName());
        config.setLogPath(logsPath.getFileName());
    }


    // 获取更新文件夹（绝对路径）
    public static Path getUpdatePath() {
        return updatePath;
    }

    // 获取目标文件夹（绝对路径）
    public static Path getTargetPath() {
        return targetPath;
    }

    // 获取删除文件夹（绝对路径）
    public static Path getDeletePath() {
        return deletePath;
    }

    // 获取备份文件夹（绝对路径）
    public static Path getBackupPath() {
        return backupPath;
    }

    // 获取logs文件夹（绝对路径）
    public static Path getLogsPath() {
        return logsPath;
    }

}
