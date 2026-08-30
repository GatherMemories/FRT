package com.awei.frt.ui;

import com.awei.frt.service.UpdateChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 启动自动检查更新决策逻辑测试（AC-3，headless 可测部分）：
 * decideAutoCheck 三分支——info==null（网络失败/API 不可达/证书全失败）→ FAILED（UI 完全静默）；
 * 有新版 → NEW_VERSION（非侵入提示）；同版/更旧 → UP_TO_DATE（静默）。三分支均不抛异常。
 */
class FRTFrameAutoCheckTest {

    private static UpdateChecker.ReleaseInfo release(String tag) {
        return new UpdateChecker.ReleaseInfo(tag, "发布", "2026-08-30T00:00:00Z", "https://github.com/x/y/releases/tag/" + tag);
    }

    @Test
    void nullInfoMeansFailed() {
        assertEquals(FRTFrame.AutoCheckOutcome.FAILED,
                FRTFrame.decideAutoCheck(null, "0.1.7-SNAPSHOT"),
                "网络失败（info==null）→ 失败静默分支");
    }

    @Test
    void newerReleaseMeansNewVersion() {
        assertEquals(FRTFrame.AutoCheckOutcome.NEW_VERSION,
                FRTFrame.decideAutoCheck(release("v0.1.8"), "0.1.7-SNAPSHOT"),
                "发现新版 → 非侵入提示分支");
    }

    @Test
    void sameVersionMeansUpToDate() {
        assertEquals(FRTFrame.AutoCheckOutcome.UP_TO_DATE,
                FRTFrame.decideAutoCheck(release("v0.1.7"), "0.1.7-SNAPSHOT"),
                "同版本（开发中版本不算旧）→ 静默分支");
    }

    @Test
    void olderReleaseMeansUpToDate() {
        assertEquals(FRTFrame.AutoCheckOutcome.UP_TO_DATE,
                FRTFrame.decideAutoCheck(release("v0.1.6"), "0.1.7-SNAPSHOT"),
                "更旧版本 → 静默分支");
    }

    @Test
    void nullCurrentVersionDoesNotThrow() {
        // 边界容错：当前版本为空时按 0 段比较，不抛异常、语义合理
        assertEquals(FRTFrame.AutoCheckOutcome.NEW_VERSION,
                FRTFrame.decideAutoCheck(release("v0.1.1"), null));
        assertEquals(FRTFrame.AutoCheckOutcome.UP_TO_DATE,
                FRTFrame.decideAutoCheck(release("v0.0.0"), null));
    }
}
