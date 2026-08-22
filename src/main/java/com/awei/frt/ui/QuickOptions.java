package com.awei.frt.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 快捷按钮选项生成（纯逻辑，无 Swing 依赖，便于单元测试）。
 * <p>
 * 根据提示文本识别可点击的快捷选项：
 * <ul>
 *   <li>含 "(y/n)" → 是/否（覆盖全部 y/n 确认提示，如更新/删除预览确认、规则生成写入确认、核心配置保存确认、恢复确认）</li>
 *   <li>含 "1-N" 数字范围 → 1..N 数字按钮（上限 20）</li>
 *   <li>含 编号/选项 但无范围 → 1..9</li>
 *   <li>含独立选项 "0" / "-1" → 对应按钮（只匹配独立数字，避免 1-10 等误判）</li>
 *   <li>始终附 取消</li>
 * </ul>
 * 从 FRTFrame 独立出来，方便在其他 UI 场景（弹窗/表单）复用。
 */
public final class QuickOptions {

    /** 快捷按钮选项（显示名 -> 提交值） */
    public record Option(String label, String value) {
    }

    private static final int MAX_NUMERIC_BUTTONS = 20;
    /**
     * 数字范围 "1-N"：前导必须是非字母数字（排除 backup-20260301-005358.json 中
     * "1-005358" 这类文件名误判，实测曾把恢复菜单解析成 1-20）；后随不能是字母数字。
     * group(2) 为范围上限。
     */
    private static final Pattern OPTION_RANGE = Pattern.compile("(^|[^0-9A-Za-z])1\\s*-\\s*(\\d+)(?![0-9A-Za-z])");
    /** 独立选项 "0"（排除 10/20 等数字中的 0，也排除 1.0 版本号里的 0） */
    private static final Pattern OPTION_ZERO = Pattern.compile("(^|[^0-9.])0([^0-9]|$)");
    /** 独立选项 "-1"（排除 1-10 等范围里的 "-1"） */
    private static final Pattern OPTION_MINUS_ONE = Pattern.compile("(^|[^0-9.])-1([^0-9]|$)");

    private QuickOptions() {
    }

    /**
     * 由提示文本生成快捷按钮选项（显示名 -> 提交值）
     * @param prompt 等待输入前打印的全部提示文本（可为 null）
     */
    public static List<Option> build(String prompt) {
        List<Option> options = new ArrayList<>();
        // 注意用 "(y/n"（不含右括号）识别：真实提示既有 "(y/n): " 也有 "(y/n, 回车=n): "，
        // 后者 "(y/n" 后跟逗号，contains("(y/n)") 会漏掉 → 该确认就没有快捷按钮（实测踩坑）
        if (prompt != null && prompt.contains("(y/n")) {
            options.add(new Option("是", "y"));
            options.add(new Option("否", "n"));
        } else {
            int max = parseMaxOption(prompt);
            if (max == 0 && prompt != null && (prompt.contains("编号") || prompt.contains("选项"))) {
                max = 9;
            }
            if (max > 0) {
                int upper = Math.min(max, MAX_NUMERIC_BUTTONS);
                for (int i = 1; i <= upper; i++) {
                    options.add(new Option(String.valueOf(i), String.valueOf(i)));
                }
            }
            if (prompt != null && OPTION_ZERO.matcher(prompt).find()) {
                options.add(new Option("0", "0"));
            }
            if (prompt != null && OPTION_MINUS_ONE.matcher(prompt).find()) {
                options.add(new Option("-1", "-1"));
            }
        }
        options.add(new Option("取消", ""));
        return options;
    }

    /** 解析提示中的最大数字范围（"1-N"） */
    static int parseMaxOption(String prompt) {
        if (prompt == null) {
            return 0;
        }
        int max = 0;
        Matcher m = OPTION_RANGE.matcher(prompt);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(2));
                if (v > max) {
                    max = v;
                }
            } catch (NumberFormatException ignored) {
                // 忽略异常格式
            }
        }
        return max;
    }
}
