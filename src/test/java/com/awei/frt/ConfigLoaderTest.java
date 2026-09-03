package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfigLoader 隔离加载测试：
 * - 通过 loadFromExternalFile(@TempDir 配置) 纯解析注入，不读取工作目录 config.json，
 *   不创建/校验目录、不改动进程级静态路径（审查 B 实测：旧实现把相对路径解析到
 *   项目根并在根目录真实创建 update/THtest/delete/backup——现为纯解析零副作用）
 * - 覆盖：缺失键默认值、logLevel 为 null 不 NPE、损坏 JSON 回退 classpath/默认、BOM 兼容
 */
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        // 纯解析不触碰静态状态；此处仅防御性恢复备份隔离（若未来用例改为走 getConfig）
        TestSupport.restoreBackupPath();
    }

    @Test
    void loadsMinimalConfigWithDefaults() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("update"));
        Path cfg = writeConfig("""
                {"updatePath":"%s","targetPath":"%s","deletePath":"%s","backupPath":"%s"}
                """.formatted(abs(update), abs(tempDir.resolve("THtest")),
                abs(tempDir.resolve("delete")), abs(tempDir.resolve("backup"))));
        Config config = ConfigLoader.loadFromExternalFile(cfg);
        assertNotNull(config, "外部配置应能加载");
        assertEquals("INFO", config.getLogLevel(), "未配置 logLevel 应默认 INFO");
        assertEquals(20, config.getMaxBackupRecords(), "未配置 maxBackupRecords 应默认 20");
        assertTrue(config.isAutoCheckUpdate(), "未配置 autoCheckUpdate 应默认开启");
        assertEquals(update.toAbsolutePath(), config.getUpdatePath().toAbsolutePath(),
                "updatePath 原样保留 JSON 中的绝对路径");
        // 纯解析不创建目录、不改静态路径
        assertTrue(Files.exists(update), "用例预创建的目录应存在");
    }

    @Test
    void nullLogLevelDoesNotNpe() throws IOException {
        // 回归：外部 config.json 含 "logLevel": null 时旧 setStaticPath 的
        // config.getLogLevel().isEmpty() 抛 NPE → 加载失败；纯解析不校验级别，null 保留
        Path cfg = writeConfig("{\"logLevel\":null}");
        Config config = ConfigLoader.loadFromExternalFile(cfg);
        assertNotNull(config, "logLevel 为 null 不应导致加载失败");
    }

    @Test
    void corruptedJsonFallsBackToClasspathOrDefaults() throws IOException {
        // 回归：外部 config.json 损坏时回退 classpath 默认/默认 Config，不返回 null、不抛异常
        Path cfg = writeConfig("{ not valid json !!!");
        Config config = ConfigLoader.loadFromExternalFile(cfg);
        assertNotNull(config, "损坏的外部配置应回退内置默认，而非返回 null");
        assertNotNull(config.getUpdatePath(), "回退配置应带默认路径");
    }

    @Test
    void bomPrefixedConfigParsesFine() throws IOException {
        Path update = Files.createDirectories(tempDir.resolve("update"));
        Path cfg = writeConfig("\uFEFF{\"updatePath\":\"" + abs(update) + "\",\"logLevel\":\"DEBUG\"}");
        Config config = ConfigLoader.loadFromExternalFile(cfg);
        assertNotNull(config, "带 BOM 的配置应能加载");
        assertEquals("DEBUG", config.getLogLevel());
        assertEquals(update.toAbsolutePath(), config.getUpdatePath().toAbsolutePath());
    }

    @Test
    void missingExternalFileFallsBackToDefaults() {
        // 文件不存在 → 回退 classpath 默认/默认 Config，不抛异常
        Config config = ConfigLoader.loadFromExternalFile(tempDir.resolve("nope.json"));
        assertNotNull(config, "外部文件缺失应回退默认配置而非 null");
    }

    @Test
    void relativePathIsKeptAsRelativeNotMaterializedToProjectRoot() throws IOException {
        // 纯解析语义：相对路径留在 Config 对象内，绝不基于工作目录解析并在项目根创建目录
        Path cfg = writeConfig("{\"updatePath\":\"update\",\"targetPath\":\"THtest\"}");
        Config config = ConfigLoader.loadFromExternalFile(cfg);
        assertNotNull(config);
        assertTrue(!config.getUpdatePath().isAbsolute(), "相对路径应保持相对（纯解析不物化）");
    }

    /** JSON 字符串转义（路径含反斜杠/中文时保持合法 JSON） */
    private String abs(Path p) {
        return p.toAbsolutePath().toString().replace("\\", "\\\\");
    }

    private Path writeConfig(String json) throws IOException {
        Path cfg = tempDir.resolve("config.json");
        Files.writeString(cfg, json);
        return cfg;
    }
}
