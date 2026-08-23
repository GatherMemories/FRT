package com.awei.frt.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 快捷按钮生成逻辑测试（QuickOptions 独立封装后）：
 * - 恢复备份菜单必须生成 1..N、0、-1、取消 全部按钮
 * - 独立 0/-1 只按"独立选项"识别，避免 1-10 等数字被误判
 * - y/n 提示只生成 是/否/取消（覆盖全部真实 y/n 确认场景）
 */
class FRTFrameQuickOptionsTest {

    private static List<String> labels(String prompt) {
        return QuickOptions.build(prompt).stream()
                .map(QuickOptions.Option::label)
                .collect(Collectors.toList());
    }

    @Test
    void restoreMenuGeneratesAllSpecialButtons() {
        // 恢复备份菜单：0=返回, -1=删除, 1-3=恢复
        String prompt = "0. 返回主菜单\n-1. 删除备份记录\n1-3. 恢复备份记录\n"
                + "\n请输入选项 (0：返回, -1：删除, 1-3：恢复): ";
        assertEquals(List.of("1", "2", "3", "0", "-1", "取消"), labels(prompt));
    }

    @Test
    void restoreMenuWithMoreRecords() {
        String prompt = "0. 返回主菜单\n-1. 删除备份记录\n1-17. 恢复备份记录\n"
                + "\n请输入选项 (0：返回, -1：删除, 1-17：恢复): ";
        List<String> labels = labels(prompt);
        assertEquals(17, labels.indexOf("0")); // 1..17 后接 0
        assertEquals(18, labels.indexOf("-1"));
        assertEquals("取消", labels.get(labels.size() - 1));
    }

    @Test
    void rangeWithTenDoesNotFakeZeroOrMinusOne() {
        // 旧实现 prompt.contains("0") / contains("-1") 会在 1-10 时误生成 0 和 -1 按钮
        String prompt = "请输入要删除的备份记录编号，支持单个编号或范围 (如 3 或 1-5) (1-10): ";
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "取消"), labels(prompt));
    }

    @Test
    void versionLikeNumberDoesNotFakeZero() {
        assertEquals(List.of("取消"), labels("检测到 Java 1.8，请升级到 17 以上"));
    }

    @Test
    void yesNoPromptOnlyYesNoCancel() {
        assertEquals(List.of("是", "否", "取消"), labels("是否执行以上 3 个更新操作？(y/n): "));
    }

    @Test
    void optionKeywordFallback() {
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "取消"),
                labels("请选择要处理的选项（无编号范围）："));
    }

    @Test
    void folderNumberPromptUsesFallbackAndZero() {
        // 规则生成向导的文件夹编号提示：无 1-N 范围但有 "编号" → 1..9 + 0 + 取消
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "取消"),
                labels("请选择要在哪一层生成规则文件 (输入文件夹编号, 0=返回): "));
    }

    @Test
    void plainInputPromptOnlyCancel() {
        assertEquals(List.of("取消"), labels("请输入文件名: "));
    }

    // ---------------- y/n 确认全场景覆盖（避免"某处确认没有快捷按钮"） ----------------

    @Test
    void allYesNoConfirmPromptsGetYesNoButtons() {
        String[] prompts = {
                // 更新预览确认（FileUpdateServiceNew）
                "是否执行以上 8 个更新操作？(y/n): ",
                // 删除预览确认（FileDeleteService）
                "确认要执行以上 3 个删除操作吗？此操作不可逆！(y/n): ",
                // 规则生成写入确认（RuleConfigWizard.writeRuleFile）
                "[确认] 写入 matching-rules.json ? (y/n, 回车=n): ",
                // 核心配置保存确认（CoreConfigWizard）
                "[确认] 保存 config.json ? (y/n, 回车=n): ",
                // 恢复会话确认（Main）
                "是否立即恢复该会话，将系统恢复到操作前的状态？(y/n): ",
                // 恢复备份确认（RestoreService）
                "确认要从此备份恢复系统吗？(y/n): ",
                // 删除备份记录确认（RestoreService）
                "确认要删除这 2 个备份记录吗？此操作不可逆！(y/n): ",
                // 规则生成参数 4 继承开关（RuleConfigWizard.inputRule）
                "请输入 (y/n, 回车=默认 false): ",
                // 规则生成参数 6 策略链（RuleConfigWizard.inputRule）
                "是否配置策略链? (y/n, 回车=n): ",
        };
        for (String prompt : prompts) {
            assertEquals(List.of("是", "否", "取消"), labels(prompt),
                    "y/n 确认提示应生成 是/否/取消: " + prompt);
        }
    }

    @Test
    void backupFileNamesWithDigitsDoNotInflateRange() {
        // 回归：备份文件名含 "1-005358"（如 backup-20260301-005358.json）时，
        // 旧正则把文件名里的 "1-数字" 也当范围，恢复菜单被解析成 1-20（实测用户反馈）
        String prompt = "1. [backup-20260822-124014.json] 2026-08-22 12:40:14 | 成功:8 失败:0\n"
                + "2. [backup-20260822-113326.json] 2026-08-22 11:33:26 | 成功:8 失败:0\n"
                + "19. [backup-20260301-005358.json] 2026-03-01 00:53:58 | 成功:5 失败:0\n"
                + "-----------------------------------------\n"
                + "0. 返回主菜单\n-1. 删除备份记录\n1-19. 恢复备份记录\n"
                + "\n请输入选项 (0：返回, -1：删除, 1-19：恢复): ";
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
                        "13", "14", "15", "16", "17", "18", "19", "0", "-1", "取消"),
                labels(prompt), "文件名中的 1-数字 不应影响范围解析");
    }

    @Test
    void restoreOperationPromptGetsYPButtons() {
        // 恢复操作提示：y=恢复 / p=固定 / 取消（描述截断括号内容）
        String prompt = "\n操作：y=从此备份恢复, p=固定/取消固定（永久保留）, 其他=返回 (y/p/回车): ";
        assertEquals(List.of("从此备份恢复", "固定/取消固定", "取消"), labels(prompt),
                "恢复操作提示应生成 y/p 快捷按钮");
    }

    @Test
    void moreThanTwentyRecordsAreNotTruncated() {
        // 回归：恢复菜单 22 条记录时，旧上限 20 截断 → 21/22 无快捷按钮（用户实测多轮测试后暴露）
        String prompt = "0. 返回主菜单\n-1. 删除备份记录\n1-22. 恢复备份记录\n"
                + "\n请输入选项 (0：返回, -1：删除, 1-22：恢复): ";
        List<String> expected = new java.util.ArrayList<>();
        for (int i = 1; i <= 22; i++) {
            expected.add(String.valueOf(i));
        }
        expected.add("0");
        expected.add("-1");
        expected.add("取消");
        assertEquals(expected, labels(prompt), "数字按钮不应被 20 上限截断");
    }
}
