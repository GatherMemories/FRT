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

    /**
     * 加载配置
     * 按优先级顺序查找配置文件：
     * 1. FRT项目根目录外部的config.json
     * 2. resources目录下的config.json
     * 3. 使用默认配置
     */
    public static Config loadConfig() {
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
        Config defaultConfig = new Config();
        // 设置基准目录为项目所在目录
        defaultConfig.setBaseDirectory(Paths.get(".").normalize().toAbsolutePath().getParent());
        return defaultConfig;
    }

    /**
     * 从指定路径加载配置
     */
    private static Config loadFromPath(Path configPath) {
        try {
            String jsonContent = Files.readString(configPath);
            Config config = parseConfig(jsonContent);
            if (config != null) {
                // 设置基准目录为项目所在目录
                config.setBaseDirectory(Paths.get(".").normalize().toAbsolutePath().getParent());
                return config;
            }
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

            // 如果config为null，创建一个新的默认配置
            if (config == null) {
                config = new Config();
            }

            return config;
        } catch (Exception e) {
            System.err.println("⚠️  解析配置失败: " + e.getMessage());
            e.printStackTrace(); // 添加堆栈跟踪以更好地诊断问题
            return null;
        }
    }

    /**
     * 获取外部配置路径（与FRT项目同级的目录）
     */
    private static Path getExternalConfigPath() {
        // 获取当前工作目录
        Path currentDir = Paths.get(".").normalize().toAbsolutePath();
        System.out.println("当前项目目录: " + currentDir);

        // 获取当前项目目录的父目录，即FRT项目目录
        Path parentDir = currentDir.getParent();
        System.out.println("FRT项目目录: " + parentDir);

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
}
