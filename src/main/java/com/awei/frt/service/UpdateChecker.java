package com.awei.frt.service;

import com.awei.frt.util.BuildInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * GitHub 最新版检查（便捷功能）
 * <p>
 * 调用 GitHub Releases API 查询本项目最新发布（如 v0.1.7），与当前 BuildInfo.VERSION
 * 比较是否更新。纯工具类：网络失败一律返回 null/false，绝不打断程序（离线/被墙环境静默降级）。
 * <p>
 * API 地址由 BuildInfo.GITHUB_URL 推导（github.com → api.github.com/repos），
 * 换仓库只需改 pom.xml 的 project.github.url 一处。
 */
public final class UpdateChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    /** 最新版信息（从 Releases API 解析） */
    public record ReleaseInfo(String tagName, String name, String publishedAt, String htmlUrl) {
    }

    private UpdateChecker() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 由 BuildInfo.GITHUB_URL 推导 Releases API 地址；GITHUB_URL 未配置时返回 null */
    public static String latestReleaseApiUrl() {
        return latestReleaseApiUrl(BuildInfo.GITHUB_URL);
    }

    /**
     * 由仓库主页 URL 推导 Releases API 地址（包内可见，供测试传任意地址）；
     * null/空白返回 null。语义与无参版本一致，只是把地址来源参数化。
     */
    static String latestReleaseApiUrl(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank()) {
            return null;
        }
        return githubUrl.replace("https://github.com/", "https://api.github.com/repos/") + "/releases/latest";
    }

    /**
     * 查询最新发布（网络操作，最多等 5s）。任何异常返回 null。
     */
    public static ReleaseInfo fetchLatestRelease() {
        return fetchLatestRelease(latestReleaseApiUrl());
    }

    /**
     * 指定 API 地址查询最新发布（包内可见，供测试用不可达地址验证"失败返回 null"，
     * 不依赖真实网络）；apiUrl 为 null/空白时直接返回 null。失败降级语义与无参版本一致。
     */
    static ReleaseInfo fetchLatestRelease(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "FRT/" + BuildInfo.VERSION);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                JsonNode root = MAPPER.readTree(in);
                String tag = text(root, "tag_name");
                String name = text(root, "name");
                String published = text(root, "published_at");
                String html = text(root, "html_url");
                if (tag == null) {
                    return null;
                }
                return new ReleaseInfo(tag, name, published, html);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * 判断最新发布 tag 是否比当前版本新（纯函数，可测试）：
     * 去掉 v 前缀与 -SNAPSHOT/-RELEASE 等后缀后按数字段逐段比较，
     * 如 v0.1.8 &gt; 0.1.7-SNAPSHOT；v0.1.7 与 0.1.7-SNAPSHOT 视为相同（开发中版本不算旧）。
     *
     * @param latestTag       GitHub 最新发布 tag（如 "v0.1.8"）
     * @param currentVersion  当前 BuildInfo.VERSION（如 "0.1.7-SNAPSHOT"）
     */
    public static boolean isNewer(String latestTag, String currentVersion) {
        int[] latest = versionSegments(latestTag);
        int[] current = currentVersion == null ? new int[0] : versionSegments(currentVersion);
        for (int i = 0; i < Math.max(latest.length, current.length); i++) {
            int l = i < latest.length ? latest[i] : 0;
            int c = i < current.length ? current[i] : 0;
            if (l != c) {
                return l > c;
            }
        }
        return false; // 完全相等 → 不算更新
    }

    /** 归一化版本串：去 v 前缀、取 '-' 之前的主版本部分，按非数字分隔拆段（每段非数字记 0） */
    static int[] versionSegments(String version) {
        if (version == null) {
            return new int[0];
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int dash = v.indexOf('-');
        if (dash >= 0) {
            v = v.substring(0, dash);
        }
        String[] parts = v.split("[^0-9]+");
        java.util.List<Integer> segs = new java.util.ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) {
                try {
                    segs.add(Integer.parseInt(p));
                } catch (NumberFormatException ignored) {
                    segs.add(0);
                }
            }
        }
        int[] out = new int[segs.size()];
        for (int i = 0; i < segs.size(); i++) {
            out[i] = segs.get(i);
        }
        return out;
    }
}
