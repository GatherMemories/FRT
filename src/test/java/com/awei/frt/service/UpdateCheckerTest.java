package com.awei.frt.service;

import com.awei.frt.util.BuildInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检查更新纯逻辑测试（AC-2）：
 * - latestReleaseApiUrl：github.com 主页 → api.github.com/repos 的 latest 接口；null/空白 → null
 * - isNewer 版本比较边界：v 前缀、-SNAPSHOT 后缀、多段数字、相等/更新/回退、null 与非数字段容错
 * - fetchLatestRelease 失败路径：不可达地址返回 null（不抛异常、不等待 5s 超时），不依赖真实网络
 * 语义锁定：v0.1.8 &gt; 0.1.7-SNAPSHOT（true）、v0.1.7 == 0.1.7-SNAPSHOT（false，开发中版本不算旧）。
 */
class UpdateCheckerTest {

    // ---------------- AC-2.1 latestReleaseApiUrl ----------------

    @Test
    void apiUrlDerivedFromGithubUrl() {
        assertEquals("https://api.github.com/repos/GatherMemories/FRT/releases/latest",
                UpdateChecker.latestReleaseApiUrl("https://github.com/GatherMemories/FRT"));
    }

    @Test
    void apiUrlIsNullForNullOrBlankUrl() {
        assertNull(UpdateChecker.latestReleaseApiUrl(null));
        assertNull(UpdateChecker.latestReleaseApiUrl(""));
        assertNull(UpdateChecker.latestReleaseApiUrl("   "));
        // 无参版本：GITHUB_URL 已配置时应能推导出 api 地址（与 pom.xml project.github.url 联动）
        if (BuildInfo.GITHUB_URL != null && !BuildInfo.GITHUB_URL.isBlank()) {
            assertNotNull(UpdateChecker.latestReleaseApiUrl());
        }
    }

    // ---------------- AC-2.2 语义锁定：v0.1.8 / v0.1.7 / v0.1.6 vs 0.1.7-SNAPSHOT ----------------

    @Test
    void newerReleaseBeatsCurrentSnapshot() {
        assertTrue(UpdateChecker.isNewer("v0.1.8", "0.1.7-SNAPSHOT"), "v0.1.8 应比 0.1.7-SNAPSHOT 新");
    }

    @Test
    void sameVersionReleaseNotNewerThanSnapshot() {
        assertFalse(UpdateChecker.isNewer("v0.1.7", "0.1.7-SNAPSHOT"), "v0.1.7 与 0.1.7-SNAPSHOT 视为相同，不算更新");
    }

    @Test
    void olderReleaseNotNewerThanSnapshot() {
        assertFalse(UpdateChecker.isNewer("v0.1.6", "0.1.7-SNAPSHOT"), "当前开发版比 v0.1.6 新");
    }

    // ---------------- AC-2.3 多段数字逐段比较（不按字符串序） ----------------

    @Test
    void multiDigitSegmentsCompareNumerically() {
        assertTrue(UpdateChecker.isNewer("v1.10", "v1.9"), "1.10 > 1.9");
        assertTrue(UpdateChecker.isNewer("v0.1.10", "v0.1.9"), "0.1.10 > 0.1.9");
        assertFalse(UpdateChecker.isNewer("v0.1.9", "v0.1.10"), "0.1.9 < 0.1.10");
        assertTrue(UpdateChecker.isNewer("v2.0.0", "v1.9.9"));
        assertFalse(UpdateChecker.isNewer("v1.9.9", "v2.0.0"));
    }

    @Test
    void missingSegmentCountsAsZero() {
        // 段数不同：缺段按 0 比较（v0.1 == 0.1.0，不算更新）
        assertFalse(UpdateChecker.isNewer("v0.1", "0.1.0"));
        assertTrue(UpdateChecker.isNewer("v0.1.1", "0.1"));
    }

    // ---------------- AC-2.4 边界容错：null / 非数字段不抛异常 ----------------

    @Test
    void nullArgumentsDoNotThrow() {
        assertDoesNotThrow(() -> UpdateChecker.isNewer(null, "0.1.7"));
        assertDoesNotThrow(() -> UpdateChecker.isNewer("v0.1.8", null));
        assertDoesNotThrow(() -> UpdateChecker.isNewer(null, null));
    }

    @Test
    void nullTagIsNotNewer() {
        // null tag → 空版本段 vs 当前段 → 不算更新（语义合理，不抛异常）
        assertFalse(UpdateChecker.isNewer(null, "0.1.7"));
    }

    @Test
    void nonNumericSegmentsTolerated() {
        // 非数字段记 0：v0.a.b → [0]，不抛异常、语义合理
        assertDoesNotThrow(() -> UpdateChecker.isNewer("v0.a.b", "0.1.7"));
        assertFalse(UpdateChecker.isNewer("v0.a.b", "0.1.7"));
        // 后缀剥离后正常比较（v0.1.8-beta 按 0.1.8 比较）
        assertTrue(UpdateChecker.isNewer("v0.1.8-beta", "0.1.7"));
    }

    // ---------------- 相等 / 回退 ----------------

    @Test
    void equalVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewer("v0.1.7", "0.1.7"));
        assertFalse(UpdateChecker.isNewer("0.1.7", "0.1.7"));
        assertFalse(UpdateChecker.isNewer("V0.1.7", "0.1.7"), "大写 V 前缀同样归一化");
        assertFalse(UpdateChecker.isNewer("0.1.7", "0.1.7-SNAPSHOT"));
    }

    @Test
    void olderReleaseIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("v0.1.6", "0.1.7"));
        assertFalse(UpdateChecker.isNewer("v0.1.5", "0.1.7-SNAPSHOT"));
    }

    // ---------------- AC-2.5 fetchLatestRelease 失败返回 null（不依赖真实网络） ----------------

    @Test
    void fetchFailureReturnsNullFast() {
        long start = System.currentTimeMillis();
        // 本机不可达端口：连接立即被拒 → null，不等待 5s 超时
        assertNull(UpdateChecker.fetchLatestRelease("http://127.0.0.1:1/repos/x/releases/latest"),
                "不可达地址应返回 null（失败静默降级）");
        assertNull(UpdateChecker.fetchLatestRelease(null));
        assertNull(UpdateChecker.fetchLatestRelease(""));
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 5000, "失败路径不应等待 5s 超时，实际 " + elapsed + "ms");
    }
}
