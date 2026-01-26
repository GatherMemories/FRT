package com.awei.frt.core.builder;

import com.awei.frt.model.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;

import java.lang.reflect.Type;
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

        return null;
    }

    /**
     * 从指定路径加载配置
     */
    private static Config loadFromPath(Path configPath) {
        try {
            String jsonContent = Files.readString(configPath);
            Config config = parseConfig(jsonContent);
        } catch (Exception e) {
            System.err.println("⚠️  加载配置失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 使用Gson解析配置JSON
     */
    private static Config parseConfig(String json) {
        try {
            GsonBuilder gsonBuilder = new GsonBuilder();

            // 注册Path类型的自定义反序列化器
            gsonBuilder.registerTypeAdapter(Path.class, new JsonDeserializer<Path>() {
                @Override
                public Path deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
                    if (jsonElement.isJsonPrimitive()) {
                        String pathString = jsonElement.getAsString();
                        if (pathString != null && !pathString.isEmpty()) {
                            return Paths.get(pathString);
                        }
                    }
                    // 如果JSON值为null或空字符串，返回null
                    return null;
                }
            });

            Gson gson = gsonBuilder.create();
            Config config = gson.fromJson(json, Config.class);

            // 配置检查
            if (config == null) {
                throw new IllegalArgumentException("配置文件内容为空");
            }

            // 检查目标目录是否存在（包括是否是文件夹）
            if (config.getTargetPath() == null
                    || config.getTargetPath().toString().isEmpty()
                    || !Files.isDirectory(config.getBaseDirectory().resolve(config.getTargetPath()).normalize())) {
                System.err.println("⚠️  配置错误: 目标目录不存在或不是文件夹（程序停止）");
                return null;
            }

            // 设置静态变量
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
     * 设置静态变量（配置的绝对路径）
     * @return
     */
    private static void setStaticPath(Config config) {
        if (config == null) {
            return;
        }

        targetPath = config.getBaseDirectory().resolve(config.getTargetPath()).normalize();
        updatePath = config.getBaseDirectory().resolve(config.getUpdatePath()).normalize();
        deletePath = config.getBaseDirectory().resolve(config.getDeletePath()).normalize();
        backupPath = config.getBaseDirectory().resolve(config.getBackupPath()).normalize();
        logsPath = config.getBaseDirectory().resolve(config.getLogLevel()).normalize();
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
