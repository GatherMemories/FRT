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
import java.util.Locale;
import java.util.Set;

/**
 * 配置加载器
 * 负责加载和解析配置文件
 */
public class ConfigLoader {

    // 加载好的配置文件（volatile：双检锁单例需要跨线程可见）
    private static volatile Config config;
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
            // 双检锁：UI 后台任务与主线程并发首访时只加载一次，
            // 避免重复解析/重复创建目录（原实现无同步，两个 SwingWorker 可能各 load 一次）
            synchronized (ConfigLoader.class) {
                if (config == null) {
                    config = resolveExternalOrFallback(getExternalConfigPath());
                }
            }
        }
        return config;
    }

    /**
     * 测试/工具用：从指定外部配置文件做<b>纯解析</b>（不 setStaticPath、不创建/校验目录、
     * 不改动进程级静态缓存与静态路径）——供 ConfigLoaderTest 注入 @TempDir 配置做加载语义
     * 断言，避免相对路径被解析到工作目录并在项目根真实创建 update/THtest/delete/backup
     * （审查 B 实测污染）。
     *
     * 三级回退与生产一致：外部文件可解析 → 用之；损坏/缺失 → classpath 默认 → new Config()。
     *
     * @param externalConfigFile 候选外部配置文件
     * @return 解析后的配置（最差回退默认 Config，正常路径下非 null）
     */
    public static Config loadFromExternalFile(Path externalConfigFile) {
        if (externalConfigFile != null && Files.exists(externalConfigFile)) {
            Config parsed = parseConfigOnly(readBomStripped(externalConfigFile));
            if (parsed != null) {
                return parsed;
            }
            logWarn("[警告] 外部 config.json 损坏或无法解析，已回退 classpath 默认（测试注入路径）");
        }
        Config resource = parseResourceOnly();
        return resource != null ? resource : new Config();
    }

    /** 读取文件并去除 UTF-8 BOM */
    private static String readBomStripped(Path file) {
        try {
            String content = Files.readString(file);
            if (content.startsWith("\uFEFF")) {
                return content.substring(1);
            }
            return content;
        } catch (IOException e) {
            logError("[警告] 读取配置失败: " + e.getMessage(), e);
            return null;
        }
    }

    /** 纯解析 JSON 为 Config（不 setStaticPath、不创建目录）；解析失败返回 null */
    private static Config parseConfigOnly(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Config.class);
        } catch (Exception e) {
            logError("[警告] 解析配置失败: " + e.getMessage(), e);
            return null;
        }
    }

    /** 纯解析 classpath 默认配置（不 setStaticPath）；失败返回 null */
    private static Config parseResourceOnly() {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("config.json")) {
            if (is != null) {
                String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (jsonContent.startsWith("\uFEFF")) {
                    jsonContent = jsonContent.substring(1);
                }
                return parseConfigOnly(jsonContent);
            }
        } catch (Exception e) {
            logError("[警告] 从 classpath 加载配置失败: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 测试用：清除进程级静态配置与路径缓存（隔离 getConfig/加载的目录自动创建副作用）。
     * 仅测试代码调用；生产流程不要使用。
     */
    public static void resetForTesting() {
        config = null;
        updatePath = null;
        targetPath = null;
        deletePath = null;
        backupPath = null;
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
        mergeWriteConfig(configPath, root -> root.put("logFontSize", size), "[警告] 保存日志字体大小失败");
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
        mergeWriteConfig(configPath, root -> root.put("theme", theme), "[警告] 保存主题设置失败");
    }

    /**
     * 保存启动时自动检查更新开关到外部 config.json（合并保留其他键，不弹确认、不打断 UI）。
     * 帮助菜单勾选项切换时调用；失败仅记日志，下次启动仍可用旧值。
     */
    public static void saveAutoCheckUpdate(boolean enabled) {
        saveAutoCheckUpdateTo(getExternalConfigPath(), enabled);
    }

    /**
     * 把 autoCheckUpdate 合并写入指定 config.json（包内可见，供测试传临时路径隔离污染）
     */
    static void saveAutoCheckUpdateTo(Path configPath, boolean enabled) {
        mergeWriteConfig(configPath, root -> root.put("autoCheckUpdate", enabled),
                "[警告] 保存启动时自动检查更新设置失败");
    }

    /**
     * 通用"读现有文件（去 BOM）→ 合并改键 → 临时文件原子写回"（原三份保存方法
     * saveLogFontSizeTo/saveThemeTo/saveAutoCheckUpdateTo 逐份复制 read-modify-write，
     * 并发交叉写会丢键——现统一收敛为单一入口，写回用 tmp + ATOMIC_MOVE（FAT32 等
     * 不支持时降级普通 move），崩溃/断电不留下半截 config.json）。
     * 文件不存在自动创建；失败仅记日志、不抛异常、不打断 UI。
     *
     * @param configPath 目标 config.json
     * @param mutator    对根对象执行键修改（read-modify-write 的 modify 部分）
     * @param failMsg    失败日志前缀（含 [警告] 标记）
     */
    private static void mergeWriteConfig(Path configPath, java.util.function.Consumer<ObjectNode> mutator,
                                         String failMsg) {
        Path tmpFile = null;
        try {
            ObjectNode root = objectMapper.createObjectNode();
            if (configPath != null && Files.exists(configPath)) {
                String text = Files.readString(configPath);
                if (text.startsWith("\uFEFF")) {
                    text = text.substring(1);
                }
                JsonNode existing = objectMapper.readTree(text);
                if (existing != null && existing.isObject()) {
                    root = (ObjectNode) existing;
                }
            }
            mutator.accept(root);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            if (configPath == null) {
                return;
            }
            // 同目录临时文件 + 原子移动；不支持 ATOMIC_MOVE 的文件系统降级普通 move
            tmpFile = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(tmpFile, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmpFile, configPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            tmpFile = null;
        } catch (Exception e) {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ignored) {
                }
            }
            LoggerUtil.logException(failMsg, e);
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
     * 加载配置（生产入口）：外部工作目录 config.json → classpath → 默认，三级回退。
     * 不修改静态 config 缓存（缓存赋值在 getConfig）。
     */
    private static Config loadConfig() {
        return resolveExternalOrFallback(getExternalConfigPath());
    }

    /**
     * 加载配置核心（外部候选路径参数化；供生产工作目录路径与测试注入路径共用）：
     * 外部文件存在且可解析 → 用之；否则告警并回退 classpath → 默认 Config。
     */
    private static Config resolveExternalOrFallback(Path externalConfig) {
        // 1. 尝试从外部路径加载（优先级最高；损坏/无法解析 → 告警并回退）
        if (externalConfig != null && Files.exists(externalConfig)) {
            logInfo("[信息] 从外部加载配置: " + externalConfig);
            Config external = loadFromPath(externalConfig);
            if (external != null) {
                return external;
            }
            // 外部配置存在但损坏/无法解析：明确告警并回退，而不是让程序无法启动
            logWarn("[警告] 外部 config.json 损坏或无法解析，已回退使用内置默认配置");
            logWarn("[提示] 可删除或修复 " + externalConfig + " 后重启恢复自定义配置");
        }

        // 2. 尝试从 classpath 加载（适用于打包后的 JAR）
        Config resourceConfig = loadResourceConfig();
        if (resourceConfig != null) {
            return resourceConfig;
        }

        // 3. 使用默认配置
        logInfo("[信息] 使用默认配置");
        Config defaultConfig = new Config();
        // 设置静态变量（包含文件夹验证和创建逻辑）
        setStaticPath(defaultConfig);
        return defaultConfig;
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

        // 日志级别：null/空回退 INFO，非法值回退 INFO（不抛异常，配置容错）
        String rawLevel = config.getLogLevel();
        String logLevel = (rawLevel == null || rawLevel.isBlank()) ? defaultLogLevel : rawLevel.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(logLevel)) {
            logWarn("[警告] 无效的日志级别: " + rawLevel + "，已回退为 INFO");
            logLevel = defaultLogLevel;
        }
        config.setLogLevel(logLevel);
        // 让配置的日志级别真正生效（logback 动态设置 com.awei.frt 包日志器级别）
        LoggerUtil.applyLogLevel(logLevel);

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
