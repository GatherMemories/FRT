package com.awei.frt.core.builder;

import com.awei.frt.model.Config;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
     * 测试用：覆盖静态备份路径（隔离测试对真实 testDic/backup 的写入污染）
     * 仅测试代码调用；生产流程不要使用
     */
    public static void setBackupPathForTesting(Path path) {
        backupPath = path;
    }

    /**
     * 保存日志字体大小到外部 config.json（合并保留其他键，不弹确认、不打断 UI）。
     * UI 顶部 A-/A+ 按钮调整字体时调用；失败仅记日志，下次启动仍可用旧值。
     */
    public static void saveLogFontSize(int size) {
        saveLogFontSizeTo(getExternalConfigPath(), size);
    }

    /**
     * 把 logFontSize 合并写入指定 config.json（包内可见，供测试传临时路径隔离污染）
     */
    static void saveLogFontSizeTo(Path configPath, int size) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            if (Files.exists(configPath)) {
                String text = Files.readString(configPath);
                if (text.startsWith("\uFEFF")) {
                    text = text.substring(1);
                }
                JsonNode existing = objectMapper.readTree(text);
                if (existing != null && existing.isObject()) {
                    root = (ObjectNode) existing;
                }
            }
            root.put("logFontSize", size);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LoggerUtil.logException("[警告] 保存日志字体大小失败", e);
        }
    }

    /**
     * 保存 UI 主题到外部 config.json（合并保留其他键，不弹确认、不打断 UI）。
     * 视图菜单切换主题时调用；失败仅记日志，下次启动仍可用旧值。
     */
    public static void saveTheme(String theme) {
        saveThemeTo(getExternalConfigPath(), theme);
    }

    /**
     * 把 theme 合并写入指定 config.json（包内可见，供测试传临时路径隔离污染）
     */
    static void saveThemeTo(Path configPath, String theme) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            if (Files.exists(configPath)) {
                String text = Files.readString(configPath);
                if (text.startsWith("\uFEFF")) {
                    text = text.substring(1);
                }
                JsonNode existing = objectMapper.readTree(text);
                if (existing != null && existing.isObject()) {
                    root = (ObjectNode) existing;
                }
            }
            root.put("theme", theme);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LoggerUtil.logException("[警告] 保存主题设置失败", e);
        }
    }

    /**
     * 保存启动时自动检查更新开关到外部 config.json（合并保留其他键，不弹确认、不打断 UI）。
     * 帮助菜单勾选项切换时调用；失败仅记日志，下次启动仍可用旧值。
     */
    public static void saveAutoCheckUpdate(boolean enabled) {
        saveAutoCheckUpdateTo(getExternalConfigPath(), enabled);
    }

    /**
     * 把 autoCheckUpdate 合并写入指定 config.json（包内可见，供测试传临时路径隔离污染）；
     * 完全照抄 saveThemeTo 模式：读现有文件（去 BOM）→ 合并写键 → 美化输出写回，
     * 文件不存在自动创建，失败仅记日志、不抛异常、不打断 UI。
     */
    static void saveAutoCheckUpdateTo(Path configPath, boolean enabled) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            if (Files.exists(configPath)) {
                String text = Files.readString(configPath);
                if (text.startsWith("\uFEFF")) {
                    text = text.substring(1);
                }
                JsonNode existing = objectMapper.readTree(text);
                if (existing != null && existing.isObject()) {
                    root = (ObjectNode) existing;
                }
            }
            root.put("autoCheckUpdate", enabled);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LoggerUtil.logException("[警告] 保存启动时自动检查更新设置失败", e);
        }
    }

    /**
     * 记录信息级别日志
     */
    private static void logInfo(String message) {
        LoggerUtil.logInfo(message);
    }

    /**
     * 记录警告级别日志
     */
    private static void logWarn(String message) {
        LoggerUtil.logWarn(message);
    }

    /**
     * 记录错误级别日志
     */
    private static void logError(String message) {
        LoggerUtil.logError(message);
    }

    /**
     * 记录错误级别日志（带异常）
     */
    private static void logError(String message, Throwable throwable) {
        LoggerUtil.logException(message, throwable);
    }


    /**
     * 加载配置
     * 按优先级顺序查找配置文件：
     * 1. FRT项目根目录的config.json（外部配置，优先级最高）
     * 2. classpath 中的 config.json（打包在 JAR 内的默认配置）
     * 3. 使用默认配置
     */
    private static Config loadConfig() {
        // 1. 尝试从FRT项目根目录加载（外部配置，优先级最高）
        Path externalConfig = getExternalConfigPath();
        if (Files.exists(externalConfig)) {
            logInfo("[信息] 从外部加载配置: " + externalConfig);
            return loadFromPath(externalConfig);
        }

        // 2. 尝试从 classpath 加载（适用于打包后的 JAR）
        Config resourceConfig = loadResourceConfig();
        if (resourceConfig != null) {
            return resourceConfig;
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
     * 从 classpath 加载资源配置
     * 适用于打包成 JAR 后的资源读取
     */
    private static Config loadResourceConfig() {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("config.json")) {
            if (is != null) {
                String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // 去除UTF-8 BOM（如果有）
                if (jsonContent.startsWith("\uFEFF")) {
                    jsonContent = jsonContent.substring(1);
                }
                logInfo("[信息] 从 classpath 加载配置: config.json");
                return parseConfig(jsonContent);
            }
        } catch (Exception e) {
            logError("[警告] 从 classpath 加载配置失败: " + e.getMessage(), e);
        }
        return null;
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
                // 目录不存在：自动创建（发布包/新环境首次启动时 update/THtest/delete/backup 尚不存在，
                // 抛异常会导致启动失败——用户实测 Windows 上启动即报"目标目录不存在"）
                try {
                    Files.createDirectories(actualPath);
                    logInfo("[创建] " + folderName + "不存在，已自动创建: " + actualPath);
                } catch (IOException e) {
                    logError("[警告] 配置错误: " + folderName + "无法自动创建: " + actualPath + " - " + e.getMessage());
                    throw new IllegalArgumentException(folderName + "无法自动创建: " + actualPath);
                }
            } else if (!Files.isDirectory(actualPath)) {
                // 存在但不是文件夹
                logError("[警告] 配置错误: " + folderName + "不是有效文件夹: " + actualPath);
                throw new IllegalArgumentException(folderName + "不是有效文件夹: " + actualPath);
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
        String defaultLogLevel = "INFO";

        logInfo("[列表] 配置信息:");
        logInfo("   基准目录: " + config.getBaseDirectory());
        logInfo("   更新目录: " + config.getUpdatePath());
        logInfo("   删除目录: " + config.getDeletePath());
        logInfo("   目标目录: " + config.getTargetPath());
        logInfo("   备份目录: " + config.getBackupPath());
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

        config.setLogLevel(config.getLogLevel().isEmpty() ? defaultLogLevel : config.getLogLevel());

        // 将绝对路径转换为相对于基准目录的相对路径存储
        config.setTargetPath(Config.toRelativePath(targetPath, basePath));
        config.setUpdatePath(Config.toRelativePath(updatePath, basePath));
        config.setDeletePath(Config.toRelativePath(deletePath, basePath));
        config.setBackupPath(Config.toRelativePath(backupPath, basePath));

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

}
