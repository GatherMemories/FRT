package com.awei.frt.service;

import com.awei.frt.util.BuildInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
    /** 整条查询（含证书回退多级尝试）的总时间预算：默认库失败后系统库/兜底最多再试约 5s */
    private static final int TOTAL_BUDGET_MS = 10000;

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
     * 查询最新发布（网络操作，连接+读取总预算不超过 {@link #TOTAL_BUDGET_MS}，默认约 10s）。
     * 任何异常返回 null。
     */
    public static ReleaseInfo fetchLatestRelease() {
        return fetchLatestRelease(latestReleaseApiUrl());
    }

    /**
     * 指定 API 地址查询最新发布（包内可见，供测试用不可达地址验证"失败返回 null"，
     * 不依赖真实网络）；apiUrl 为 null/空白时直接返回 null。失败降级语义与无参版本一致。
     */
    static ReleaseInfo fetchLatestRelease(String apiUrl) {
        return fetchLatestReleaseInternal(apiUrl, false);
    }

    /**
     * 启动时自动检查更新专用静默变体：与 {@link #fetchLatestRelease()} 同一套
     * 三层证书兜底（默认信任库 → Windows 系统证书库 → 绕过证书校验），但失败/降级时
     * <b>不输出任何日志</b>——logback CONSOLE appender 会把 logWarn 打到 System.out 再进 UI
     * 日志区，自动检查要求网络失败完全静默（排查信息仍由 FRTFrame 按文件日志路径记录）。
     * 手动「帮助 → 检查更新」继续用带日志的 {@link #fetchLatestRelease()}，行为与 v0.1.14 一致。
     */
    public static ReleaseInfo fetchLatestReleaseQuiet() {
        return fetchLatestReleaseInternal(latestReleaseApiUrl(), true);
    }

    /**
     * 三层证书兜底查询（私有实现，quiet 控制失败/降级时是否向 UI/控制台输出）：
     * 1) 默认 Java 信任库；
     * 2) 仅当证书校验失败（SSLHandshakeException 等）时，Windows 上回退系统证书库重试一次；
     * 3) 仍仅当证书校验失败时，最后兜底绕过证书校验（仅"检查更新"这一个只读请求）。
     *
     * 注意：证书兜底链只对"证书类异常"触发——DNS 失败/超时/连接拒绝等网络异常属于
     * 环境不可达，走 trust-all 也救不回来，直接快速失败返回 null（避免非证书故障
     * 误入"绕过证书校验"路径——该路径信任任意证书，一旦非证书故障也触发，
     * 会在网络异常时悄悄把连接切换到不校验证书的模式）。
     */
    private static ReleaseInfo fetchLatestReleaseInternal(String apiUrl, boolean quiet) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }
        long deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS;

        // 1) 默认 Java 信任库
        try {
            return trustedFetch(apiUrl, null, deadline);
        } catch (Exception e) {
            // 只有证书校验类失败才进入兜底链；网络类失败直接走"失败降级"（快速返回，不尝试 trust-all）
            if (!isCertificateException(e)) {
                logFailure(quiet, e);
                return null;
            }
            // 2) 证书校验失败：Windows 上回退系统证书库重试一次
            //（安全软件/代理 HTTPS 拦截时其证书通常已装入 Windows 信任库）
            SSLContext winTrust = windowsSystemTrustContext();
            if (winTrust != null) {
                try {
                    return trustedFetch(apiUrl, winTrust, deadline);
                } catch (Exception e2) {
                    if (!isCertificateException(e2)) {
                        logFailure(quiet, e2);
                        return null;
                    }
                    // 继续最后兜底
                }
            }
            // 3) 最后兜底：绕过证书校验（仅限"检查更新"读取版本号这一个只读请求；
            //    拦截证书既不在 Java 信任库也不在系统库时仍能工作，并明确记录日志提示环境风险）
            SSLContext trustAll = trustAllContext();
            if (trustAll != null && System.currentTimeMillis() < deadline) {
                try {
                    ReleaseInfo info = trustedFetch(apiUrl, trustAll, deadline);
                    if (info != null) {
                        // 环境风险提示：quiet（启动自动检查）也写文件日志，仅不打扰 UI/控制台
                        com.awei.frt.util.LoggerUtil.getInstance(null).logWarnFileOnly(
                                "[检查更新] 已绕过 HTTPS 证书校验获取最新版——你的网络环境可能拦截了 HTTPS"
                                        + "（安全软件/代理），请确认网络可信后再下载");
                        if (!quiet) {
                            com.awei.frt.util.LoggerUtil.logWarn("[检查更新] 已绕过 HTTPS 证书校验获取最新版——"
                                    + "你的网络环境可能拦截了 HTTPS（安全软件/代理），请确认网络可信后再下载");
                        }
                        return info;
                    }
                } catch (Exception ignored) {
                    // 网络层失败，兜底也无效
                }
            }
            // 证书兜底链全部失败：静默降级（非静默路径记录真实原因到日志，便于排查）
            logFailure(quiet, e);
            return null;
        }
    }

    /**
     * 执行请求并对返回结果做安全校验：
     * 1. htmlUrl 必须来自本仓库域（防止 trust-all 兜底路径下，被中间人篡改的
     *    "最新版"页面把用户诱导到钓鱼下载站——见 H3 审查项）；
     * 2. 校验不通过按失败处理（返回 null）。
     */
    private static ReleaseInfo trustedFetch(String apiUrl, SSLContext sslContext, long deadline) throws Exception {
        ReleaseInfo info = fetch(apiUrl, sslContext, deadline);
        if (info == null) {
            return null;
        }
        String htmlUrl = info.htmlUrl();
        String githubUrl = BuildInfo.GITHUB_URL;
        if (htmlUrl != null && !htmlUrl.isBlank()
                && githubUrl != null && !githubUrl.isBlank()) {
            // GitHub Releases 的 html_url 形如 https://github.com/<owner>/<repo>/releases/tag/<tag>
            // 校验：host 与仓库页一致、路径以 /<owner>/<repo> 开头
            if (!isSameRepositoryPage(htmlUrl, githubUrl)) {
                return null; // 来源可疑（非本仓库域名/路径），拒绝展示与打开
            }
        }
        return info;
    }

    /**
     * 判断页面 URL 是否属于本仓库（host 一致 + 路径前缀 /owner/repo）。
     * 纯函数，可测试。
     */
    static boolean isSameRepositoryPage(String pageUrl, String repoUrl) {
        if (pageUrl == null || repoUrl == null || pageUrl.isBlank() || repoUrl.isBlank()) {
            return false;
        }
        try {
            URI page = URI.create(pageUrl);
            URI repo = URI.create(repoUrl.trim());
            String pageHost = page.getHost();
            String repoHost = repo.getHost();
            if (pageHost == null || repoHost == null || !pageHost.equalsIgnoreCase(repoHost)) {
                return false;
            }
            String pagePath = normalizeRepoPath(page.getPath());
            String repoPath = normalizeRepoPath(repo.getPath());
            return pagePath != null && repoPath != null && pagePath.startsWith(repoPath + "/");
        } catch (Exception e) {
            return false;
        }
    }

    /** 归一化仓库路径：去首尾斜杠与 .git 后缀，小写比较（GitHub owner/repo 大小写不敏感） */
    private static String normalizeRepoPath(String path) {
        if (path == null) {
            return null;
        }
        String p = path.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.toLowerCase(java.util.Locale.ROOT).endsWith(".git")) {
            p = p.substring(0, p.length() - 4);
        }
        return p.isEmpty() ? null : p.toLowerCase(java.util.Locale.ROOT);
    }

    /** 判断异常是否为证书校验类（SSL 握手/证书路径）——只有这类异常才值得走证书兜底链 */
    private static boolean isCertificateException(Exception e) {
        return e instanceof javax.net.ssl.SSLHandshakeException
                || e instanceof javax.net.ssl.SSLPeerUnverifiedException
                || e instanceof javax.net.ssl.SSLException
                || (e.getCause() instanceof javax.net.ssl.SSLException);
    }

    /** 失败降级日志：quiet 时完全静默（UI 不打扰），否则输出真实原因 */
    private static void logFailure(boolean quiet, Exception e) {
        if (!quiet) {
            com.awei.frt.util.LoggerUtil.logWarn("[检查更新] 查询 GitHub 最新版失败: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 信任任意证书的 SSLContext（仅"检查更新"最后兜底用；正常网络不会走到这一步） */
    private static SSLContext trustAllContext() {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, null);
            return ctx;
        } catch (Exception e) {
            return null;
        }
    }

    /** 执行一次 HTTPS GET 并解析最新版信息；失败抛异常由调用方决定是否回退/降级。
     *  连接/读取超时按剩余总预算动态收缩（受 deadline 约束，避免多级回退串行累计超时） */
    private static ReleaseInfo fetch(String apiUrl, SSLContext sslContext, long deadline) throws Exception {
        int remaining = (int) Math.max(1000, deadline - System.currentTimeMillis());
        int connectTimeout = Math.min(CONNECT_TIMEOUT_MS, remaining);
        int readTimeout = Math.min(READ_TIMEOUT_MS, remaining);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            if (conn instanceof HttpsURLConnection https && sslContext != null) {
                https.setSSLSocketFactory(sslContext.getSocketFactory());
            }
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
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
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Windows 系统证书库 SSLContext（回退用）：信任 Windows-ROOT 信任库里的证书，
     * 用于安全软件/代理 HTTPS 拦截场景（其证书通常已装入系统信任库）。
     * 非 Windows 或初始化失败返回 null（保持默认单次尝试行为）。
     */
    private static SSLContext windowsSystemTrustContext() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return null;
        }
        try {
            KeyStore systemStore = KeyStore.getInstance("Windows-ROOT");
            systemStore.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(systemStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            return null;
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
