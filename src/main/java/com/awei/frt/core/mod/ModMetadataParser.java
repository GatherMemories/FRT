package com.awei.frt.core.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

/**
 * 模组元数据解析器（自研，替代第三方 BasicModInfoParser）
 *
 * <p>支持平台与元数据文件（按优先级检测，命中第一个即停止）：</p>
 * <ol>
 *   <li>NeoForge:   {@code META-INF/neoforge.mods.toml}（TOML，第三方库不支持，自研新增）</li>
 *   <li>Forge:      {@code META-INF/mods.toml}（TOML）</li>
 *   <li>Fabric:     {@code fabric.mod.json}（JSON）</li>
 *   <li>Quilt:      {@code quilt.mod.json}（JSON，字段在 quilt_loader 下）</li>
 *   <li>Forge 旧版: {@code mcmod.info}（JSON 数组）</li>
 * </ol>
 *
 * <p>版本占位符兜底（如 {@code ${file.jarVersion}}、{@code ${version}}、{@code @version@}，通常是
 * gradle 构建时未替换的模板）：</p>
 * <ol>
 *   <li>{@code META-INF/MANIFEST.MF} 的 {@code Implementation-Version}</li>
 *   <li>从 jar 文件名启发式提取</li>
 * </ol>
 */
public final class ModMetadataParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String NEOFORGE_MODS_TOML = "META-INF/neoforge.mods.toml";
    private static final String FORGE_MODS_TOML = "META-INF/mods.toml";
    private static final String FABRIC_MOD_JSON = "fabric.mod.json";
    private static final String QUILT_MOD_JSON = "quilt.mod.json";
    private static final String MCMOD_INFO = "mcmod.info";

    private ModMetadataParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析单个 jar 文件，返回其中所有模组信息（一个 jar 可能包含多个 mod，如 Forge 的多个 [[mods]]）。
     * 非模组 jar 或无支持元数据时返回空列表。
     *
     * @param jarPath 模组 jar 文件路径
     * @throws IOException 读取 jar 失败
     */
    public static List<ModInfo> parseJar(Path jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<ParsedMod> parsed = new ArrayList<>();

            if (jarFile.getEntry(NEOFORGE_MODS_TOML) != null) {
                parsed.addAll(parseModsToml(jarFile, NEOFORGE_MODS_TOML));
            } else if (jarFile.getEntry(FORGE_MODS_TOML) != null) {
                parsed.addAll(parseModsToml(jarFile, FORGE_MODS_TOML));
            } else if (jarFile.getEntry(FABRIC_MOD_JSON) != null) {
                parsed.addAll(parseSimpleJson(jarFile, FABRIC_MOD_JSON, false));
            } else if (jarFile.getEntry(QUILT_MOD_JSON) != null) {
                parsed.addAll(parseSimpleJson(jarFile, QUILT_MOD_JSON, true));
            } else if (jarFile.getEntry(MCMOD_INFO) != null) {
                parsed.addAll(parseMcmodInfo(jarFile));
            }

            if (parsed.isEmpty()) {
                return List.of();
            }

            // 版本占位符兜底：MANIFEST.MF -> 文件名
            String manifestVersion = readManifestVersion(jarFile);
            List<ModInfo> result = new ArrayList<>(parsed.size());
            for (ParsedMod m : parsed) {
                result.add(new ModInfo(m.id, m.name, resolveVersion(m.version, manifestVersion, jarPath),
                        m.description, jarPath));
            }
            return result;
        } catch (ZipException e) {
            // 损坏/非 zip 文件：视为无模组元数据（与 McModStrategy 的静默跳过语义一致）
            return List.of();
        }
    }

    // ---------------- TOML（NeoForge / Forge） ----------------

    private static List<ParsedMod> parseModsToml(JarFile jarFile, String entryName) throws IOException {
        List<ParsedMod> mods = new ArrayList<>();
        try (InputStream in = jarFile.getInputStream(jarFile.getEntry(entryName));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            TomlParseResult result = Toml.parse(reader);
            if (result.hasErrors()) {
                return mods; // TOML 解析失败：跳过该 jar
            }
            TomlArray modsArray = result.getArray("mods");
            if (modsArray == null) {
                return mods;
            }
            for (int i = 0; i < modsArray.size(); i++) {
                TomlTable modTable = modsArray.getTable(i);
                if (modTable == null) {
                    continue;
                }
                String modId = modTable.getString("modId");
                if (modId == null || modId.isBlank()) {
                    continue;
                }
                mods.add(new ParsedMod(modId,
                        modTable.getString("displayName"),
                        modTable.getString("version"),
                        modTable.getString("description")));
            }
        }
        return mods;
    }

    // ---------------- JSON（Fabric / Quilt） ----------------

    private static List<ParsedMod> parseSimpleJson(JarFile jarFile, String entryName, boolean quiltNested)
            throws IOException {
        List<ParsedMod> mods = new ArrayList<>();
        try (InputStream in = jarFile.getInputStream(jarFile.getEntry(entryName))) {
            JsonNode root = OBJECT_MAPPER.readTree(in);
            if (root == null || !root.isObject()) {
                return mods;
            }
            JsonNode node = quiltNested ? root.get("quilt_loader") : root;
            if (node == null || !node.isObject()) {
                return mods;
            }
            String id = text(node, "id");
            if (id == null || id.isBlank()) {
                return mods;
            }
            mods.add(new ParsedMod(id, text(node, "name"), text(node, "version"), text(node, "description")));
        }
        return mods;
    }

    // ---------------- JSON（Forge 旧版 mcmod.info） ----------------

    private static List<ParsedMod> parseMcmodInfo(JarFile jarFile) throws IOException {
        List<ParsedMod> mods = new ArrayList<>();
        try (InputStream in = jarFile.getInputStream(jarFile.getEntry(MCMOD_INFO))) {
            JsonNode root = OBJECT_MAPPER.readTree(in);
            if (root == null || !root.isArray()) {
                return mods;
            }
            for (JsonNode node : root) {
                String modid = text(node, "modid");
                if (modid == null || modid.isBlank()) {
                    continue;
                }
                mods.add(new ParsedMod(modid, text(node, "name"), text(node, "version"),
                        text(node, "description")));
            }
        }
        return mods;
    }

    // ---------------- 兜底 ----------------

    /**
     * 版本解析：原始版本有效则直接返回；否则依次尝试 MANIFEST.MF 与文件名。
     */
    private static String resolveVersion(String rawVersion, String manifestVersion, Path jarPath) {
        if (rawVersion != null && !rawVersion.isBlank() && !isPlaceholder(rawVersion)) {
            return rawVersion.trim();
        }
        if (manifestVersion != null && !manifestVersion.isBlank()) {
            return manifestVersion.trim();
        }
        String fileNameVersion = extractVersionFromFileName(jarPath);
        return fileNameVersion != null ? fileNameVersion : rawVersion;
    }

    /**
     * 判断版本是否为构建占位符（gradle 未替换的模板）。
     */
    private static boolean isPlaceholder(String version) {
        return version.contains("${") || version.contains("@");
    }

    /**
     * 读取 MANIFEST.MF 的 Implementation-Version；不存在时返回 null。
     */
    private static String readManifestVersion(JarFile jarFile) {
        try {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
            // MANIFEST 读取失败不影响主流程
        }
        return null;
    }

    /**
     * 从 jar 文件名启发式提取版本：取最后一个以数字开头的 "-" 或 "_" 分隔段。
     * 如 {@code appleskin-forge-mc1.20.1-2.5.1.jar} -> {@code 2.5.1}；无法识别时返回 null。
     */
    private static String extractVersionFromFileName(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        String[] parts = fileName.split("[-_]");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isEmpty() && Character.isDigit(part.charAt(0))) {
                return part;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 解析中间结构（版本可能在最后统一兜底，故先用可变对象暂存）。
     */
    private static final class ParsedMod {
        private final String id;
        private final String name;
        private final String version;
        private final String description;

        private ParsedMod(String id, String name, String version, String description) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
        }
    }
}
