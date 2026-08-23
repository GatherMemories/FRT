package com.awei.frt.ui;

import org.junit.jupiter.api.Test;

import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志区按行首 [标记] 着色的映射测试：
 * 成功/失败/警告/交互提示/说明各自取对应的 LOG_* 颜色，标题分隔线加粗，
 * 无标记的普通行用默认文字色；行首带前导空白的标记同样识别。
 * 列表/选项信息颜色：编号选项行、选项提示、(y/n) 快捷键说明、>> 回显、预览操作类型、文件树、分隔线。
 * （静态方法不依赖窗口，可在无头环境运行）
 */
class LogAreaStyleTest {

    private static void assertColor(String line, Color color) {
        SimpleAttributeSet s = FRTFrame.styleForLine(line);
        assertEquals(color, StyleConstants.getForeground(s), "颜色不符: " + line.trim());
    }

    private static void assertAccent(String line) {
        assertColor(line, UITheme.LOG_ACCENT);
    }

    private static void assertHeading(String line) {
        assertColor(line, UITheme.LOG_HEADING);
    }

    private static void assertSuccess(String line) {
        assertColor(line, UITheme.LOG_SUCCESS);
    }

    private static void assertError(String line) {
        assertColor(line, UITheme.LOG_ERROR);
    }

    private static void assertWarn(String line) {
        assertColor(line, UITheme.LOG_WARN);
    }

    private static void assertMuted(String line) {
        assertColor(line, UITheme.LOG_MUTED);
    }

    private static void assertText(String line) {
        assertColor(line, UITheme.LOG_TEXT);
    }

    @Test
    void successLineIsGreen() {
        SimpleAttributeSet s = FRTFrame.styleForLine("[成功] 文件替换操作完成！\n");
        assertEquals(UITheme.LOG_SUCCESS, StyleConstants.getForeground(s));
        assertFalse(StyleConstants.isBold(s));
    }

    @Test
    void failureLineIsRed() {
        SimpleAttributeSet s = FRTFrame.styleForLine("[失败] 文件删除操作失败！\n");
        assertEquals(UITheme.LOG_ERROR, StyleConstants.getForeground(s));
    }

    @Test
    void warningLineIsAmber() {
        SimpleAttributeSet s = FRTFrame.styleForLine("[警告] 检测到未完成的操作会话\n");
        assertEquals(UITheme.LOG_WARN, StyleConstants.getForeground(s));
    }

    @Test
    void cancelAndSkipUseWarningColor() {
        assertEquals(UITheme.LOG_WARN, StyleConstants.getForeground(FRTFrame.styleForLine("[取消] 已返回主菜单\n")));
        assertEquals(UITheme.LOG_WARN, StyleConstants.getForeground(FRTFrame.styleForLine("[跳过] 跳过失败的操作\n")));
    }

    @Test
    void interactivePromptLineIsAccentBlue() {
        SimpleAttributeSet s = FRTFrame.styleForLine("[输入] 请选择操作:\n");
        assertEquals(UITheme.LOG_ACCENT, StyleConstants.getForeground(s));
    }

    @Test
    void explanatoryLineIsMuted() {
        SimpleAttributeSet s = FRTFrame.styleForLine("[说明] 规则文件控制所在层文件夹的 增/删/改 操作\n");
        assertEquals(UITheme.LOG_MUTED, StyleConstants.getForeground(s));
    }

    @Test
    void titleSeparatorIsBoldBlue() {
        SimpleAttributeSet s = FRTFrame.styleForLine("===== 更新完成: 成功 3，失败 0 =====\n");
        assertEquals(UITheme.LOG_TITLE, StyleConstants.getForeground(s));
        assertTrue(StyleConstants.isBold(s));
    }

    @Test
    void plainLineUsesDefaultTextColor() {
        SimpleAttributeSet s = FRTFrame.styleForLine("  文件名: backup-20260822.json\n");
        assertEquals(UITheme.LOG_TEXT, StyleConstants.getForeground(s));
        assertFalse(StyleConstants.isBold(s));
    }

    @Test
    void tagWithLeadingWhitespaceIsStillDetected() {
        // 树形结构图的缩进行（如 "  [链] 步骤1: ..."）也应识别标记
        SimpleAttributeSet s = FRTFrame.styleForLine("  [警告] 该目录已存在规则文件\n");
        assertEquals(UITheme.LOG_WARN, StyleConstants.getForeground(s));
    }

    @Test
    void unknownTagFallsBackToMuted() {
        SimpleAttributeSet s = FRTFrame.styleForLine("[新标记] 未识别的标记用次要颜色\n");
        assertEquals(UITheme.LOG_MUTED, StyleConstants.getForeground(s));
    }

    // ---------------- 用户必看/必操作信息（可选项列表、操作提示）醒目蓝 ----------------

    @Test
    void numberedOptionLinesAreAccentBlue() {
        assertAccent("1. 更新文件\n");
        assertAccent("0. 返回主菜单\n");
        assertAccent("-1. 删除备份记录\n");
        assertAccent("1-3. 恢复备份记录\n");
        assertAccent("8. 退出\n");
    }

    @Test
    void backupRecordListLineIsAccentBlue() {
        // 备份记录列表是用户要从 1-N 中选择的对象，必须醒目（灰字灰底难找）
        assertAccent("1. [backup-20260822-124014.json] 2026-08-22 12:40:14 | 成功:8 失败:0\n");
        assertAccent("19. [backup-20260301-005358.json] 2026-03-01 00:53:58 | 成功:5 失败:0\n");
    }

    @Test
    void optionPromptLinesAreAccentBlue() {
        assertAccent("请输入选项 (0：返回, -1：删除, 1-3：恢复): \n");
        assertAccent("操作：y=从此备份恢复, p=固定（永久保留）, 其他=返回 (y/p/回车): \n");
        assertAccent("是否执行以上 3 个更新操作？(y/n): \n");
        assertAccent("请输入 (y/n, 回车=默认 false): \n");
    }

    @Test
    void echoLinesAreMuted() {
        assertMuted("  >> strategyType = \"FileSameName\"\n");
        assertMuted("  >> 日志级别 = INFO\n");
    }

    @Test
    void previewOperationTypesAreColored() {
        // 只有重点动作上色：新增绿 / 删除橙 / 错误红；替换是常态操作保持中性
        assertSuccess("  [+] 新增: /path/THtest/app.jar\n");
        assertText("  [=] 替换: /path/THtest/config/app.properties\n");
        assertWarn("  [=] 删除: /path/THtest/old.jar\n");
        assertError("  [!] 文件不存在，无法处理\n");
    }

    @Test
    void fileTreeDirStaysNeutral() {
        // 文件树是查看性内容（不是用户要选择的对象），保持中性浅色
        assertText("[+] 模组/\n");
        assertText("  [+] 子文件夹/\n");
    }

    @Test
    void fileTreeFileUsesDefaultColor() {
        assertText("  [-] config\n");
        assertText("[-] server.properties\n");
    }

    @Test
    void structureSymbolsAreNotMarkers() {
        // [→] / [○] 是树形结构符号，不是语义标记，按普通结构行显示
        assertText("[→] 节点 / (使用本地规则: MyStrategy)\n");
        assertText("[○] 节点 lib (无规则: 跳过)\n");
    }

    @Test
    void separatorLinesAreMuted() {
        assertMuted("-----------------------------------------\n");
        assertMuted("---------\n");
    }

    // ---------------- 主次关系：装饰框灰、功能标题醒目 ----------------

    @Test
    void pureEqualsDecoratorFrameIsMuted() {
        // 向导标题框（一整行只有 =）是装饰，低调灰色，不与功能标题争亮度
        assertMuted("=========================================\n");
    }

    @Test
    void summaryLineWithContentStaysBoldBlue() {
        // ===== 文字 =====（等号+内容）是结果摘要，仍亮蓝加粗
        SimpleAttributeSet s = FRTFrame.styleForLine("===== 更新完成: 成功 3，失败 0 =====\n");
        assertEquals(UITheme.LOG_TITLE, StyleConstants.getForeground(s));
        assertTrue(StyleConstants.isBold(s));
    }

    @Test
    void functionTitleTagsAreBoldHeading() {
        assertHeading("[执行] 恢复操作\n");
        assertHeading("[列表] 可用的备份记录 (按时间倒序):\n");
        assertHeading("[预览] 将执行以下 8 个更新操作（预览模式：尚未执行任何操作，确认后才真正执行）:\n");
        assertHeading("[信息] 当前配置:\n");
        assertHeading("[校验] 规则文件解析校验通过 [OK]\n");
        // 功能标题加粗，与正文深灰明显分层
        assertTrue(StyleConstants.isBold(FRTFrame.styleForLine("[信息] 当前配置:\n")));
    }

    // ---------------- 行内分段：[固定] 紫色加粗、JSON 键名高亮 ----------------

    @Test
    void pinnedTokenGetsPinnedBoldSegment() {
        String line = "3. [backup-20260822-203823.json] [固定] 2026-08-22 20:38:23 | 成功:7 失败:0\n";
        java.util.List<FRTFrame.LineSegment> segments = FRTFrame.segmentStyledLine(line);

        FRTFrame.LineSegment pinned = segments.stream()
                .filter(seg -> seg.text().equals("[固定]"))
                .findFirst().orElseThrow();
        assertEquals(UITheme.LOG_PINNED, StyleConstants.getForeground(pinned.style()));
        assertTrue(StyleConstants.isBold(pinned.style()));

        // 基础段仍是蓝（编号选项行），保证列表颜色统一
        assertEquals(UITheme.LOG_ACCENT, StyleConstants.getForeground(segments.get(0).style()));
        assertFalse(StyleConstants.isBold(segments.get(0).style()));
    }

    @Test
    void lineWithoutPinnedTokenIsSingleSegment() {
        String line = "1. [backup-20260823-205321.json] 2026-08-23 20:53:21 | 成功:8 失败:0\n";
        java.util.List<FRTFrame.LineSegment> segments = FRTFrame.segmentStyledLine(line);
        assertEquals(1, segments.size());
        assertEquals(line, segments.get(0).text());
        assertEquals(UITheme.LOG_ACCENT, StyleConstants.getForeground(segments.get(0).style()));
    }

    @Test
    void pinnedTokenAtMultiplePositionsEachColored() {
        // 警告行里也可能出现 [固定]，每个 [固定] 都应紫色加粗
        String line = ">>> 警告：待删除的备份中包含 [固定] 记录，[固定] 永久保留\n";
        java.util.List<FRTFrame.LineSegment> segments = FRTFrame.segmentStyledLine(line);
        long pinnedCount = segments.stream().filter(seg -> seg.text().equals("[固定]")).count();
        assertEquals(2, pinnedCount);
        for (FRTFrame.LineSegment seg : segments) {
            if (seg.text().equals("[固定]")) {
                assertEquals(UITheme.LOG_PINNED, StyleConstants.getForeground(seg.style()));
                assertTrue(StyleConstants.isBold(seg.style()));
            }
        }
    }

    // ---------------- JSON 预览：键名高亮、数组值不误判 ----------------

    @Test
    void jsonKeyNamesAreHighlightedBlue() {
        String line = "  \"patterns\" : [ \"*.jar\" ],\n";
        java.util.List<FRTFrame.LineSegment> segments = FRTFrame.segmentStyledLine(line);
        FRTFrame.LineSegment key = segments.stream()
                .filter(seg -> seg.text().contains("patterns"))
                .findFirst().orElseThrow();
        assertEquals(UITheme.LOG_ACCENT, StyleConstants.getForeground(key.style()));
    }

    @Test
    void jsonArrayValueIsNotTaggedAsMarker() {
        // 回归：JSON 数组值 [ "*.jar" ] 曾被 tagOf 误判成标记导致整行变灰，现在应回到正文深灰
        assertColor("  \"excludePatterns\" : [ \"*backup*\" ],\n", UITheme.LOG_TEXT);
        assertColor("  \"patterns\" : [ \"*.jar\" ],\n", UITheme.LOG_TEXT);
    }

    @Test
    void jsonValueStaysBaseColor() {
        String line = "  \"excludePatterns\" : [ \"*backup*\" ],\n";
        java.util.List<FRTFrame.LineSegment> segments = FRTFrame.segmentStyledLine(line);
        FRTFrame.LineSegment val = segments.stream()
                .filter(seg -> seg.text().contains("*backup*"))
                .findFirst().orElseThrow();
        assertEquals(UITheme.LOG_TEXT, StyleConstants.getForeground(val.style()));
    }

    // ---------------- logback 时间戳前缀：前缀灰、内容按标记着色 ----------------

    @Test
    void logbackPrefixedMarkerLinesGetMarkerColor() {
        // logback CONSOLE 输出带 "21:20:05.718 INFO  " 前缀，[成功] 不在行首，标记识别必须跳过前缀
        assertColor("21:20:05.718 INFO  [成功] 备份目录有效: /path/backup\n", UITheme.LOG_SUCCESS);
        assertColor("21:20:05.718 WARN  [警告] 检测到未完成的操作会话\n", UITheme.LOG_WARN);
        assertColor("21:20:05.718 ERROR [失败] 备份操作文件失败！\n", UITheme.LOG_ERROR);
        assertColor("21:20:05.718 INFO  [信息] 从外部加载配置: /path/config.json\n", UITheme.LOG_HEADING);
    }

    @Test
    void logbackPrefixIsMutedAndContentIsColored() {
        String line = "21:20:05.718 INFO  [成功] 备份目录有效: /path/backup\n";
        java.util.List<FRTFrame.LineSegment> segments = FRTFrame.segmentStyledLine(line);
        // 第一段 = 时间戳+级别前缀，灰
        assertEquals("21:20:05.718 INFO  ", segments.get(0).text());
        assertEquals(UITheme.LOG_MUTED, StyleConstants.getForeground(segments.get(0).style()));
        // 剩余内容段 = [成功] 绿
        FRTFrame.LineSegment content = segments.stream()
                .filter(seg -> seg.text().contains("[成功]"))
                .findFirst().orElseThrow();
        assertEquals(UITheme.LOG_SUCCESS, StyleConstants.getForeground(content.style()));
    }

    @Test
    void plainLineWithoutTimestampPrefixStillSingleSegment() {
        // 无时间戳前缀的普通行保持原有分段行为（不分出前缀段）
        String line = "1. [backup-20260823-205321.json] 2026-08-23 20:53:21 | 成功:8 失败:0\n";
        java.util.List<FRTFrame.LineSegment> segments = FRTFrame.segmentStyledLine(line);
        assertEquals(1, segments.size());
        assertEquals(UITheme.LOG_ACCENT, StyleConstants.getForeground(segments.get(0).style()));
    }
}
