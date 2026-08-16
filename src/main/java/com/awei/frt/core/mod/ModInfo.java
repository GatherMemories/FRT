package com.awei.frt.core.mod;

import java.nio.file.Path;

/**
 * 模组元数据信息
 * 由 {@link ModMetadataParser} 从模组 jar 中解析得到
 */
public class ModInfo {

    private final String id;          // 模组唯一标识（modId）
    private final String name;        // 模组显示名称
    private final String version;     // 模组版本（占位符已解析）
    private final String description; // 模组描述
    private final Path path;          // 模组 jar 文件路径

    public ModInfo(String id, String name, String version, String description, Path path) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "ModInfo{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", path=" + path +
                '}';
    }
}
