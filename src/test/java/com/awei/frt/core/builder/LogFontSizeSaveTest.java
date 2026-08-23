package com.awei.frt.core.builder;

import com.awei.frt.model.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志字体大小（config.logFontSize + A-/A+ 持久化写入）测试：
 * - Config 默认 13，setter 限制在 10~24 可调范围
 * - saveLogFontSizeTo 合并写入 config.json：保留未管理键，文件不存在时自动创建
 */
class LogFontSizeSaveTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultLogFontSizeIs13() {
        assertEquals(13, new Config().getLogFontSize());
    }

    @Test
    void setterClampsToAdjustableRange() {
        Config config = new Config();
        config.setLogFontSize(5);
        assertEquals(Config.MIN_LOG_FONT_SIZE, config.getLogFontSize());
        config.setLogFontSize(99);
        assertEquals(Config.MAX_LOG_FONT_SIZE, config.getLogFontSize());
        config.setLogFontSize(16);
        assertEquals(16, config.getLogFontSize());
    }

    @Test
    void saveMergesAndPreservesOtherKeys() throws Exception {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{\"logLevel\":\"DEBUG\",\"updatePath\":\"up\"}");
        ConfigLoader.saveLogFontSizeTo(configFile, 18);

        JsonNode node = new ObjectMapper().readTree(Files.readString(configFile));
        assertEquals(18, node.get("logFontSize").asInt());
        assertEquals("DEBUG", node.get("logLevel").asText());
        assertEquals("up", node.get("updatePath").asText());
    }

    @Test
    void saveCreatesFileWhenMissing() throws Exception {
        Path configFile = tempDir.resolve("config.json"); // 不存在
        ConfigLoader.saveLogFontSizeTo(configFile, 15);

        assertTrue(Files.exists(configFile));
        JsonNode node = new ObjectMapper().readTree(Files.readString(configFile));
        assertEquals(15, node.get("logFontSize").asInt());
    }
}
