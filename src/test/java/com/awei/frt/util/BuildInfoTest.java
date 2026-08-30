package com.awei.frt.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 构建信息与 pom.xml 自动同步验证：
 * 版本号/GitHub 链接必须等于 pom.xml 中的 <version> 与 project.github.url，
 * 保证"升级版本只改 pom.xml、界面自动更新"的机制不被破坏。
 */
class BuildInfoTest {

    private static String pomProperty(String pattern) throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile(pattern).matcher(pom);
        assertTrue(m.find(), "pom.xml 中未找到匹配: " + pattern);
        return m.group(1).trim();
    }

    @Test
    void versionMatchesPomXml() throws IOException {
        // 匹配本项目自身 version（<artifactId>FRT</artifactId> 之后、可隔 <name> 的 <version>），
        // 与 BuildInfo.fallbackVersionFromPom 的逻辑保持一致
        String pomVersion = pomProperty(
                "<artifactId>\\s*FRT\\s*</artifactId>\\s*(?:<name>\\s*.*?\\s*</name>\\s*)?"
                        + "<version>\\s*([^<\\s]+)\\s*</version>");
        assertEquals(pomVersion, BuildInfo.VERSION, "BuildInfo.VERSION 必须等于 pom.xml 的 <version>");
    }

    @Test
    void githubUrlMatchesPomXml() throws IOException {
        String pomUrl = pomProperty("<project\\.github\\.url>\\s*([^<\\s]+)\\s*</project\\.github\\.url>");
        assertEquals(pomUrl, BuildInfo.GITHUB_URL, "BuildInfo.GITHUB_URL 必须等于 pom.xml 的 project.github.url");
    }

    @Test
    void displayNameContainsVersion() {
        assertTrue(BuildInfo.displayName().contains(BuildInfo.VERSION));
        assertFalse(BuildInfo.VERSION.isBlank());
    }
}
