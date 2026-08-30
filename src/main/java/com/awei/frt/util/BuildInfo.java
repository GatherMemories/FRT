package com.awei.frt.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 构建信息（版本号 / 名称 / GitHub 仓库链接 / 构建时间）
 * <p>
 * 版本号唯一来源是 pom.xml 的 {@code <version>}：
 * Maven 打包时通过资源过滤把 ${project.version} 等占位符注入
 * {@code src/main/resources/build-info.properties}（见 pom.xml {@code <resources>}），
 * 因此升级版本只需改 pom.xml 后重新打包，界面/控制台显示自动更新，无需手动改代码。
 * <p>
 * 读取优先级：
 * <ol>
 *   <li>classpath 下的 build-info.properties（Maven 构建注入，打包后正常运行路径）；</li>
 *   <li>回退：直接解析工作目录下的 pom.xml（IDE 直接运行、未经过 Maven 过滤时兜底）；</li>
 *   <li>仍失败则用 "unknown" 占位，程序照常运行。</li>
 * </ol>
 */
public final class BuildInfo {

    public static final String NAME;
    public static final String ARTIFACT_ID;
    public static final String VERSION;
    public static final String GITHUB_URL;
    public static final String BUILD_TIME;

    private static final String RESOURCE = "/build-info.properties";
    private static final String UNKNOWN = "unknown";

    static {
        Properties p = loadFromClasspath();
        NAME = valueOf(p, "app.name", "FRT");
        ARTIFACT_ID = valueOf(p, "app.artifactId", "FRT");
        VERSION = valueOf(p, "app.version", fallbackVersionFromPom());
        GITHUB_URL = valueOf(p, "app.githubUrl", "");
        BUILD_TIME = valueOf(p, "app.buildTime", "");
    }

    private BuildInfo() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 完整展示名，如 "多层级文件夹更新工具 v0.1.1-SNAPSHOT"（供窗口标题/启动横幅使用） */
    public static String displayName() {
        return NAME + " v" + VERSION;
    }

    private static Properties loadFromClasspath() {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
            // 读取失败走回退逻辑，不阻塞启动
        }
        return p;
    }

    private static String valueOf(Properties p, String key, String fallback) {
        String v = p.getProperty(key);
        return (v == null || v.isBlank() || v.contains("${")) ? fallback : v.trim();
    }

    /**
     * 回退路径：classpath 没有过滤后的 build-info.properties 时（IDE 直接运行），
     * 直接读取工作目录的 pom.xml 解析版本号。
     */
    private static String fallbackVersionFromPom() {
        try {
            Path pom = Path.of("pom.xml");
            if (!Files.exists(pom)) {
                return UNKNOWN;
            }
            String content = Files.readString(pom, StandardCharsets.UTF_8);
            // 优先匹配本项目自身的 version（<artifactId>FRT</artifactId> 之后、可隔 <name> 的 <version>），
            // 避免误取 parent/plugin 依赖的版本号
            Matcher self = Pattern
                    .compile("<artifactId>\\s*FRT\\s*</artifactId>\\s*(?:<name>\\s*.*?\\s*</name>\\s*)?"
                            + "<version>\\s*([^<\\s]+)\\s*</version>")
                    .matcher(content);
            if (self.find()) {
                return self.group(1);
            }
            // 兜底：文件里第一个 <version>
            Matcher any = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>").matcher(content);
            return any.find() ? any.group(1) : UNKNOWN;
        } catch (IOException ignored) {
            return UNKNOWN;
        }
    }
}
