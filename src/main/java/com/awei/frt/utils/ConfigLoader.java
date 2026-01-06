package com.awei.frt.utils;

import com.awei.frt.model.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置加载器
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String CONFIG_FILE = "config.json";
    
    /**
     * 获取项目根目录
     */
    private static Path getProjectRoot() {
        try {
            // 从当前类的位置向上找到项目根目录
            Path currentPath = Paths.get(ConfigLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            
            // 如果是 classes 目录，向上两级到项目根目录
            if (currentPath.toString().contains("classes")) {
                return currentPath.getParent().getParent();
            }
            
            // 如果是 target 目录，向上到项目根目录
            if (currentPath.toString().contains("target")) {
                return currentPath.getParent();
            }
            
            // 否则使用当前工作目录
            return Paths.get("").toAbsolutePath();
        } catch (Exception e) {
            logger.warn("无法确定项目根目录，使用当前目录", e);
            return Paths.get("").toAbsolutePath();
        }
    }
    
    /**
     * 获取 FRT 同级目录（父目录）
     * 例如: c:/Users/xxx/FRT/FRT -> c:/Users/xxx/FRT/
     */
    private static Path getParentDirectory() {
        Path projectRoot = getProjectRoot();
        Path parentPath = projectRoot.getParent();
        if (parentPath != null) {
            return parentPath;
        }
        logger.warn("无法获取父目录，使用项目根目录");
        return projectRoot;
    }
    
    /**
     * 从指定路径尝试加载配置文件
     */
    private static Config tryLoadConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            logger.debug("配置文件不存在: {}", configPath);
            return null;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            Config config = mapper.readValue(configPath.toFile(), Config.class);
            logger.info("配置文件加载成功: {}", configPath);
            return config;
        } catch (IOException e) {
            logger.warn("配置文件加载失败: {}", configPath, e);
            return null;
        }
    }
    
    /**
     * 从 resources 目录加载配置文件
     */
    private static Config loadConfigFromResources() {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                Config config = mapper.readValue(is, Config.class);
                logger.info("从 resources 加载配置文件成功: {}", CONFIG_FILE);
                return config;
            }
        } catch (Exception ignored) {
            logger.debug("从 resources 加载配置文件失败", ignored);
        }
        return null;
    }
    
    /**
     * 获取默认配置
     */
    private static Config getDefaultConfig() {
        Config config = new Config();
        logger.info("使用默认配置");
        return config;
    }
    
    /**
     * 加载配置文件
     * 优先级：FRT同级目录 > 项目根目录 > resources目录 > 默认配置
     */
    public static Config loadConfig() {
        // 1. 优先尝试从FRT同级目录加载配置
        Path parentConfigPath = getParentDirectory().resolve(CONFIG_FILE);
        Config config = tryLoadConfig(parentConfigPath);
        if (config != null) {
            logger.info("使用FRT同级目录配置: {}", parentConfigPath);
            return config;
        }
        
        // 2. 尝试从项目根目录加载配置
        Path projectConfigPath = getProjectRoot().resolve(CONFIG_FILE);
        config = tryLoadConfig(projectConfigPath);
        if (config != null) {
            logger.info("使用项目根目录配置: {}", projectConfigPath);
            return config;
        }
        
        // 3. 尝试从resources目录加载配置
        config = loadConfigFromResources();
        if (config != null) {
            logger.info("使用resources目录配置: {}", CONFIG_FILE);
            return config;
        }
        
        // 4. 使用默认配置
        logger.info("未找到任何配置文件，使用默认配置");
        return getDefaultConfig();
    }
}