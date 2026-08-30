package com.awei.frt.core.builder;

import com.awei.frt.model.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动时自动检查更新开关（config.autoCheckUpdate + 帮助菜单勾选项持久化写入）测试：
 * - Config 默认 true（开箱即用）；setAutoCheckUpdate 读写正确
 * - saveAutoCheckUpdateTo 合并写入 config.json：保留其他键，文件不存在时自动创建
 * - 保存失败（只读路径/IO 异常）不抛异常、仅记日志
 */
class AutoCheckUpdateSaveTest {

    @TempDir
    Path tempDir;

    // ---------------- Config.isAutoCheckUpdate/setAutoCheckUpdate 语义 ----------------

    @Test
    void defaultAutoCheckUpdateIsTrue() {
        assertTrue(new Config().isAutoCheckUpdate(), "启动自动检查更新应默认开启（开箱即用）");
    }

    @Test
    void setterRoundTrips() {
        Config config = new Config();
        config.setAutoCheckUpdate(false);
        assertFalse(config.isAutoCheckUpdate(), "关闭后应为 false");
        config.setAutoCheckUpdate(true);
        assertTrue(config.isAutoCheckUpdate(), "重新开启后应为 true");
    }

    @Test
    void missingKeyDefaultsToTrueWhenLoadedFromJson() throws Exception {
        // JSON 缺该键 → Jackson 走 Java 字段默认值 true（需求：读取容错）
        Config loaded = new ObjectMapper().readValue("{\"logLevel\":\"INFO\"}", Config.class);
        assertTrue(loaded.isAutoCheckUpdate());
    }

    // ---------------- saveAutoCheckUpdateTo 合并写入 ----------------

    @Test
    void saveMergesAndPreservesOtherKeys() throws Exception {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{\"logLevel\":\"DEBUG\",\"updatePath\":\"up\",\"theme\":\"dark\"}");
        ConfigLoader.saveAutoCheckUpdateTo(configFile, false);

        JsonNode node = new ObjectMapper().readTree(Files.readString(configFile));
        assertFalse(node.get("autoCheckUpdate").asBoolean(), "autoCheckUpdate=false 应被合并写入");
        assertEquals("DEBUG", node.get("logLevel").asText(), "其他键必须保留");
        assertEquals("up", node.get("updatePath").asText(), "其他键必须保留");
        assertEquals("dark", node.get("theme").asText(), "其他键必须保留");
    }

    @Test
    void saveCreatesFileWhenMissing() throws Exception {
        Path configFile = tempDir.resolve("config.json"); // 不存在
        ConfigLoader.saveAutoCheckUpdateTo(configFile, true);

        assertTrue(Files.exists(configFile), "文件不存在时应自动创建");
        JsonNode node = new ObjectMapper().readTree(Files.readString(configFile));
        assertTrue(node.get("autoCheckUpdate").asBoolean());
    }

    @Test
    void saveBomPrefixedFileIsMergedCorrectly() throws Exception {
        // 带 UTF-8 BOM 的现有文件（记事本等编辑器保存）：去 BOM 后合并，不残留 BOM 破坏 JSON
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "\uFEFF{\"logLevel\":\"INFO\"}");
        ConfigLoader.saveAutoCheckUpdateTo(configFile, false);

        JsonNode node = new ObjectMapper().readTree(Files.readString(configFile));
        assertFalse(node.get("autoCheckUpdate").asBoolean());
        assertEquals("INFO", node.get("logLevel").asText());
    }

    @Test
    void saveToUnwritablePathDoesNotThrow() {
        // 只读路径/IO 异常：不抛异常、仅记日志（AC-2.5），程序照常运行
        Path configFile = tempDir.resolve("no-such-dir").resolve("config.json");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> ConfigLoader.saveAutoCheckUpdateTo(configFile, false));
    }

    @Test
    void savedValueRoundTripsThroughConfig() throws Exception {
        Path configFile = tempDir.resolve("config.json");
        ConfigLoader.saveAutoCheckUpdateTo(configFile, false);

        Config loaded = new ObjectMapper().readValue(Files.readString(configFile), Config.class);
        assertFalse(loaded.isAutoCheckUpdate(), "写出的 autoCheckUpdate 应能被 Config 读回");
    }
}
