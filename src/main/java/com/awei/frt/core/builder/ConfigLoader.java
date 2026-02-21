package com.awei.frt.core.builder;

import com.awei.frt.model.Config;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
    // 日志消息缓存（用于在LoggerUtil初始化前记录日志）
    private static final List<String> logBuffer = new ArrayList<>();
    // 标记LoggerUtil是否已初始化
    private static volatile boolean loggerInitialized = false;
    // LoggerUtil实例（初始化后设置）
    private static LoggerUtil loggerInstance = null;

    // 私有构造函数，防止实例化
    private ConfigLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

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
     * 记录信息级别日志
     */
    private static void logInfo(String message) {
        if (loggerInitialized && loggerInstance != null) {
            loggerInstance.logInfo(message);
            return;
        }
        String formatted = "[INFO] " + message;
        logBuffer.add(formatted);
        // 在LoggerUtil初始化前不输出到控制台，避免重复
    }

    /**
     * 记录警告级别日志
     */
    private static void logWarn(String message) {
        if (loggerInitialized && loggerInstance != null) {
            loggerInstance.logWarn(message);
            return;
        }
        String formatted = "[WARN] " + message;
        logBuffer.add(formatted);
        // 在LoggerUtil初始化前不输出到控制台，避免重复
    }

    /**
     * 记录错误级别日志
     */
    private static void logError(String message) {
        if (loggerInitialized && loggerInstance != null) {
            loggerInstance.logError(message);
            return;
        }
        String formatted = "[ERROR] " + message;
        logBuffer.add(formatted);
        // 在LoggerUtil初始化前不输出到控制台，避免重复
    }

    /**
     * 记录错误级别日志（带异常）
     */
    private static void logError(String message, Throwable throwable) {
        if (loggerInitialized && loggerInstance != null) {
            loggerInstance.logError(message, throwable);
            return;
        }
        String formatted = "[ERROR] " + message;
        logBuffer.add(formatted);
        // 在LoggerUtil初始化前不输出到控制台，避免重复
        // 异常堆栈不缓冲，将在LoggerUtil初始化后记录
    }

    /**
     * 标记LoggerUtil已初始化，将缓冲日志写入LoggerUtil
     */
    public static void onLoggerInitialized(LoggerUtil logger) {
        if (logger == null) return;

        synchronized (logBuffer) {
            for (String logMessage : logBuffer) {
                // 解析日志级别和消息
                if (logMessage.startsWith("[INFO] ")) {
                    logger.logInfo(logMessage.substring(7));
                } else if (logMessage.startsWith("[WARN] ")) {
                    logger.logWarn(logMessage.substring(7));
                } else if (logMessage.startsWith("[ERROR] ")) {
                    logger.logError(logMessage.substring(8));
                } else {
                    logger.logInfo(logMessage);
                }
            }
            logBuffer.clear();
            loggerInstance = logger;
            loggerInitialized = true;
        }
    }
    /**
     * 加载配置
     * 按优先级顺序查找配置文件：
     * 1. FRT项目根目录的config.json
     * 2. resources目录下的config.json
     * 3. 使用默认配置
     */
    private static Config loadConfig() {
        // 1. 尝试从FRT项目根目录加载
        Path externalConfig = getExternalConfigPath();
        if (Files.exists(externalConfig)) {
            logInfo("[信息] 从外部加载配置: " + externalConfig);
            return loadFromPath(externalConfig);
        }

        // 2. 尝试从resources目录加载
        Path resourceConfig = getResourceConfigPath();
        if (resourceConfig != null && Files.exists(resourceConfig)) {
            logInfo("[信息] 从resources加载配置: " + resourceConfig);
            return loadFromPath(resourceConfig);
        }

        // 3. 使用默认配置
        logInfo("[信息] 使用默认配置");
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
            logError("[警告] 加载配置失败: " + e.getMessage(), e);
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
            logError("[警告] 解析配置失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取工作目录配置路径（与FRT项目根目录）
     */
    private static Path getExternalConfigPath() {
        // 获取当前工作目录
        Path currentDir = Paths.get(".").normalize().toAbsolutePath();

        return currentDir.resolve("config.json");
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
                logInfo("[成功] 使用默认" + folderName + ": " + actualPath);
            } catch (IOException e) {
                logError("[警告] 创建" + folderName + "失败: " + e.getMessage(), e);
            }
        } else {
            // 判断路径类型并处理
            if (Config.isAbsolutePath(configPath)) {
                // 绝对路径：直接使用
                actualPath = configPath.normalize();
                logInfo("[搜索] 检测到绝对路径: " + folderName + " = " + actualPath);
            } else {
                // 相对路径：基于基准目录解析
                actualPath = basePath.resolve(configPath).normalize();
                logInfo("[搜索] 检测到相对路径，转换为绝对路径: " + folderName + " = " + actualPath);
            }

            if (!Files.exists(actualPath)) {
                logError("[警告] 配置错误: " + folderName + "不存在: " + actualPath);
                throw new IllegalArgumentException(folderName + "不存在");
            } else if (!Files.isDirectory(actualPath)) {
                // 存在但不是文件夹
                logError("[警告] 配置错误: " + folderName + "不是有效文件夹: " + actualPath);
                throw new IllegalArgumentException(folderName + "不是有效文件夹");
            } else {
                logInfo("[成功] " + folderName + "有效: " + actualPath);
            }
        }

        return actualPath;
    }

    /**
     * 设置静态变量（配置的绝对路径）
     * 验证并确保所有文件夹存在，并将绝对路径转换为相对路径存储
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

        logInfo("[列表] 配置信息:");
        logInfo("   基准目录: " + config.getBaseDirectory());
        logInfo("   更新目录: " + config.getUpdatePath());
        logInfo("   删除目录: " + config.getDeletePath());
        logInfo("   目标目录: " + config.getTargetPath());
        logInfo("   备份目录: " + config.getBackupPath());
        logInfo("   日志目录: " + config.getLogPath());
        logInfo("   日志级别: " + config.getLogLevel());
        logInfo("");

        // 验证并设置各个文件夹路径，同时转换为相对路径存储
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

        // 将绝对路径转换为相对于基准目录的相对路径存储
        config.setTargetPath(Config.toRelativePath(targetPath, basePath));
        config.setUpdatePath(Config.toRelativePath(updatePath, basePath));
        config.setDeletePath(Config.toRelativePath(deletePath, basePath));
        config.setBackupPath(Config.toRelativePath(backupPath, basePath));
        config.setLogPath(Config.toRelativePath(logsPath, basePath));

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
