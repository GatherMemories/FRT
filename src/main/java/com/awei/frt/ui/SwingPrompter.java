package com.awei.frt.ui;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Swing 交互实现：把服务打印的最后一行提示（经 System.out 重定向捕获）弹出对话框
 * - 提示含 (y/n) 时用确认对话框
 * - 其余用输入对话框；取消返回空串
 */
public class SwingPrompter implements UserPrompter {

    private final Component parent;
    private final PromptSource promptSource;

    public interface PromptSource {
        /** 最近一行提示文本（可为空串） */
        String lastPrompt();
    }

    public SwingPrompter(Component parent, PromptSource promptSource) {
        this.parent = parent;
        this.promptSource = promptSource;
    }

    @Override
    public String readLine() {
        final String[] result = new String[1];
        try {
            java.awt.EventQueue.invokeAndWait(() -> {
                String prompt = promptSource.lastPrompt();
                String safe = (prompt == null || prompt.isBlank()) ? "请输入:" : prompt;
                if (safe.contains("(y/n)")) {
                    int r = JOptionPane.showConfirmDialog(parent, safe, "FRT 确认",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    result[0] = r == JOptionPane.YES_OPTION ? "y" : "n";
                } else {
                    String input = JOptionPane.showInputDialog(parent, safe, "FRT 输入",
                            JOptionPane.QUESTION_MESSAGE);
                    result[0] = input == null ? "" : input.trim();
                }
            });
        } catch (Exception e) {
            result[0] = "";
        }
        return result[0] == null ? "" : result[0];
    }
}
