package com.awei.frt;

import com.awei.frt.model.Config;
import com.awei.frt.utils.ConfigLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

public class ConfigLoaderTest {
    
    @Test
    public void testConfigLoadingWithPathDeserialization() {
        System.out.println("Testing ConfigLoader with Gson Path deserialization fix...");
        
        // 加载配置
        Config config = ConfigLoader.loadConfig();
        
        // 验证配置不为null
        assertNotNull(config, "Config should not be null");
        
        if (config != null) {
            System.out.println("✅ Config loaded successfully!");
            System.out.println("  - Target Path: " + config.getTargetPath());
            System.out.println("  - Update Path: " + config.getUpdatePath());
            System.out.println("  - Delete Path: " + config.getDeletePath());
            System.out.println("  - Backup Path: " + config.getBackupPath());
            System.out.println("  - Log Path: " + config.getLogPath());
            
            // 验证路径字段是否正确反序列化为Path对象
            assertTrue(config.getTargetPath() instanceof Path, "Target path should be a Path instance");
            assertTrue(config.getUpdatePath() instanceof Path, "Update path should be a Path instance");
            assertTrue(config.getDeletePath() instanceof Path, "Delete path should be a Path instance");
            assertTrue(config.getBackupPath() instanceof Path, "Backup path should be a Path instance");
            assertTrue(config.getLogPath() instanceof Path, "Log path should be a Path instance");
            
            // 验证配置的其他属性
            assertTrue(config.isConfirmBeforeReplace(), "Confirm before replace should be true by default");
            assertTrue(config.isCreateBackup(), "Create backup should be true by default");
            assertEquals("INFO", config.getLogLevel(), "Log level should be INFO by default");
        }
    }
}