package com.awei.frt;

import com.awei.frt.core.mod.ModInfo;
import com.awei.frt.core.mod.ModMetadataParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自研模组元数据解析器测试：
 * 1. 真实 Forge 1.20.1 模组 jar（testDic/update 下）
 * 2. 构造 jar 覆盖 Fabric / Quilt / 旧版 mcmod.info / NeoForge / 占位符兜底
 */
class ModMetadataParserTest {

    private static final Path UPDATE_DIR = Path.of("testDic/update");

    // ---------------- 真实 jar ----------------

    @Test
    void parseRealForgeModsToml() throws IOException {
        // litematica: META-INF/mods.toml 里是真实版本 0.15.0-dev
        ModInfo litematica = parseFirstJarMatching("litematica");
        assertNotNull(litematica, "应能解析 litematica");
        assertEquals("litematica", litematica.getId());
        assertEquals("0.15.0-dev", litematica.getVersion());
        assertNotNull(litematica.getName());
    }

    @Test
    void parseRealImBlocker() throws IOException {
        // IMBlocker: mods.toml 真实版本 5.4.6（文件名里的 1.20.4 是 mc 版本，不能误用）
        ModInfo imblocker = parseFirstJarMatching("IMBlocker");
        assertNotNull(imblocker, "应能解析 IMBlocker");
        assertEquals("imblocker", imblocker.getId());
        assertEquals("5.4.6", imblocker.getVersion());
    }

    @Test
    void placeholderVersionFallbackToManifest() throws IOException {
        // appleskin: mods.toml version=${file.jarVersion}（占位符）
        // 应兜底到 MANIFEST.MF 的 Implementation-Version: 2.5.1+mc1.20.1
        ModInfo appleskin = parseFirstJarMatching("appleskin");
        assertNotNull(appleskin, "应能解析 appleskin");
        assertEquals("appleskin", appleskin.getId());
        assertFalse(appleskin.getVersion().contains("${"), "版本不应残留占位符，实际: " + appleskin.getVersion());
        assertEquals("2.5.1+mc1.20.1", appleskin.getVersion());
    }

    @Test
    void noMetadataJarReturnsEmpty() throws IOException {
        // app.jar: 无任何 mod 元数据 -> 空列表（不报错）
        List<ModInfo> mods = ModMetadataParser.parseJar(UPDATE_DIR.resolve("app.jar"));
        assertTrue(mods.isEmpty());
    }

    // ---------------- 构造 jar：各格式 ----------------

    @Test
    void fabricModJson() throws IOException {
        String json = """
                {
                  "id": "fabric-example",
                  "version": "1.2.3",
                  "name": "Fabric Example Mod",
                  "description": "A test mod"
                }
                """;
        Path jar = createTestJar(Map.of("fabric.mod.json", json));
        try {
            List<ModInfo> mods = ModMetadataParser.parseJar(jar);
            assertEquals(1, mods.size());
            assertEquals("fabric-example", mods.get(0).getId());
            assertEquals("1.2.3", mods.get(0).getVersion());
            assertEquals("Fabric Example Mod", mods.get(0).getName());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    @Test
    void quiltModJson() throws IOException {
        String json = """
                {
                  "quilt_loader": {
                    "id": "quilt-example",
                    "version": "4.5.6",
                    "name": "Quilt Example Mod",
                    "description": "A quilt test mod"
                  }
                }
                """;
        Path jar = createTestJar(Map.of("quilt.mod.json", json));
        try {
            List<ModInfo> mods = ModMetadataParser.parseJar(jar);
            assertEquals(1, mods.size());
            assertEquals("quilt-example", mods.get(0).getId());
            assertEquals("4.5.6", mods.get(0).getVersion());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    @Test
    void legacyMcmodInfo() throws IOException {
        String json = """
                [
                  {"modid": "legacy-mod", "name": "Legacy Mod", "version": "0.9.0", "description": "old forge"},
                  {"modid": "legacy-mod-2", "name": "Legacy Mod 2", "version": "1.0.0", "description": ""}
                ]
                """;
        Path jar = createTestJar(Map.of("mcmod.info", json));
        try {
            List<ModInfo> mods = ModMetadataParser.parseJar(jar);
            assertEquals(2, mods.size());
            assertEquals("legacy-mod", mods.get(0).getId());
            assertEquals("0.9.0", mods.get(0).getVersion());
            assertEquals("legacy-mod-2", mods.get(1).getId());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    @Test
    void neoforgeModsToml() throws IOException {
        // NeoForge：第三方库不支持，自研新增支持
        String toml = """
                modLoader="javafml"
                loaderVersion="[70,)"

                [[mods]]
                modId="neoforge-example"
                version="7.8.9"
                displayName="NeoForge Example"
                description="a neoforge mod"
                """;
        Path jar = createTestJar(Map.of("META-INF/neoforge.mods.toml", toml));
        try {
            List<ModInfo> mods = ModMetadataParser.parseJar(jar);
            assertEquals(1, mods.size());
            assertEquals("neoforge-example", mods.get(0).getId());
            assertEquals("7.8.9", mods.get(0).getVersion());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    @Test
    void placeholderFallbackToFileName() throws IOException {
        // 构造：mods.toml 占位符 + 无 MANIFEST 版本 -> 兜底到文件名 mymod-3.2.1
        String toml = """
                modLoader="javafml"

                [[mods]]
                modId="file-name-mod"
                version="${file.jarVersion}"
                displayName="File Name Mod"
                """;
        Path jar = createTestJar(Map.of("META-INF/mods.toml", toml));
        Path renamed = jar.resolveSibling("my-awesome-mod-3.2.1.jar");
        Files.move(jar, renamed);
        try {
            List<ModInfo> mods = ModMetadataParser.parseJar(renamed);
            assertEquals(1, mods.size());
            assertEquals("file-name-mod", mods.get(0).getId());
            assertEquals("3.2.1", mods.get(0).getVersion(), "占位符应兜底到文件名版本");
        } finally {
            Files.deleteIfExists(renamed);
        }
    }

    @Test
    void multiModsToml() throws IOException {
        // 一个 jar 含多个 [[mods]]
        String toml = """
                modLoader="javafml"

                [[mods]]
                modId="mod-a"
                version="1.0.0"
                displayName="Mod A"

                [[mods]]
                modId="mod-b"
                version="2.0.0"
                displayName="Mod B"
                """;
        Path jar = createTestJar(Map.of("META-INF/mods.toml", toml));
        try {
            List<ModInfo> mods = ModMetadataParser.parseJar(jar);
            assertEquals(2, mods.size());
            assertEquals("mod-a", mods.get(0).getId());
            assertEquals("mod-b", mods.get(1).getId());
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    // ---------------- 辅助 ----------------

    /** 在 testDic/update 下找第一个文件名包含关键字 jar，解析并返回第一个 ModInfo */
    private ModInfo parseFirstJarMatching(String keyword) throws IOException {
        try (Stream<Path> files = Files.list(UPDATE_DIR)) {
            Path jar = files.filter(f -> f.getFileName().toString().contains(keyword)
                    && f.getFileName().toString().endsWith(".jar"))
                    .findFirst().orElse(null);
            assertNotNull(jar, "testDic/update 下应存在包含 " + keyword + " 的 jar");
            List<ModInfo> mods = ModMetadataParser.parseJar(jar);
            assertFalse(mods.isEmpty(), keyword + " 应解析出至少一个 mod");
            return mods.get(0);
        }
    }

    /** 构造一个只含指定 entry 的临时 jar */
    private Path createTestJar(Map<String, String> entries) throws IOException {
        Path jar = Files.createTempFile("mod-parser-test", ".jar");
        try (OutputStream os = Files.newOutputStream(jar);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return jar;
    }
}
