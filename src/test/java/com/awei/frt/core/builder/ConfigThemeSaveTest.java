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
 * UI 主题持久化测试（AC-3，参照 LogFontSizeSaveTest 模式）：
 * - Config 默认浅色；setTheme("dark") → dark；未知值/null → 回退浅色
 * - saveThemeTo 合并写入 config.json：保留其他键，文件不存在时自动创建
 */
class ConfigThemeSaveTest {

    @TempDir
    Path tempDir;

    // ---------------- Config.getTheme/setTheme 语义 ----------------

    @Test
    void defaultThemeIsLight() {
        assertEquals(Config.THEME_LIGHT, new Config().getTheme());
    }

    @Test
    void setThemeDarkAndUnknownValuesFallbackToLight() {
        Config config = new Config();
        config.setTheme(Config.THEME_DARK);
        assertEquals(Config.THEME_DARK, config.getTheme());

        config.setTheme("xxx");
        assertEquals(Config.THEME_LIGHT, config.getTheme(), "未知主题值应回退浅色");

        config.setTheme(null);
        assertEquals(Config.THEME_LIGHT, config.getTheme(), "null 主题值应回退浅色");

        // 浅色显式设置
        config.setTheme(Config.THEME_LIGHT);
        assertEquals(Config.THEME_LIGHT, config.getTheme());
    }

    // ---------------- saveThemeTo 合并写入 ----------------

    @Test
    void saveMergesAndPreservesOtherKeys() throws Exception {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{\"logLevel\":\"DEBUG\",\"updatePath\":\"up\"}");
        ConfigLoader.saveThemeTo(configFile, Config.THEME_DARK);

        JsonNode node = new ObjectMapper().readTree(Files.readString(configFile));
        assertEquals("dark", node.get("theme").asText(), "theme 应被合并写入");
        assertEquals("DEBUG", node.get("logLevel").asText(), "其他键必须保留");
        assertEquals("up", node.get("updatePath").asText(), "其他键必须保留");
    }

    @Test
    void saveCreatesFileWhenMissing() throws Exception {
        Path configFile = tempDir.resolve("config.json"); // 不存在
        ConfigLoader.saveThemeTo(configFile, Config.THEME_DARK);

        assertTrue(Files.exists(configFile), "文件不存在时应自动创建");
        JsonNode node = new ObjectMapper().readTree(Files.readString(configFile));
        assertEquals("dark", node.get("theme").asText());
    }

    @Test
    void savedThemeRoundTripsThroughConfig() throws Exception {
        Path configFile = tempDir.resolve("config.json");
        ConfigLoader.saveThemeTo(configFile, Config.THEME_DARK);

        Config loaded = new ObjectMapper().readValue(Files.readString(configFile), Config.class);
        assertEquals(Config.THEME_DARK, loaded.getTheme(), "写出的 theme 应能被 Config 读回");
    }
}
