package com.awei.frt.ui;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Swing 交互实现（可滚动弹窗版）：
 * - 提示来源为"自上次输入以来的完整提示块"（结构树 / 选项列表 / 多行说明），
 *   弹窗内以只读多行文本 + 滚动条完整展示，解决长选项/结构树看不到的问题
 * - 提示含 (y/n) 时用"是/否"按钮确认
 * - 其余用"滚动提示 + 单行输入框"，确定返回输入、取消返回空串
 */
public class SwingPrompter implements UserPrompter {

    private final Component parent;
    private final PromptSource promptSource;

    public interface PromptSource {
        /** 取走完整提示文本（消费式：调用后清空缓冲） */
        String takePrompt();
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
                String prompt = promptSource.takePrompt();
                String safe = (prompt == null || prompt.isBlank()) ? "请输入:" : prompt;

                // 只读多行提示区（可滚动查看完整选项/结构树）
                JTextArea promptArea = new JTextArea(safe);
                promptArea.setEditable(false);
                promptArea.setLineWrap(true);
                promptArea.setWrapStyleWord(true);
                JScrollPane scroll = new JScrollPane(promptArea);
                scroll.setPreferredSize(new Dimension(620, 340));

                if (safe.contains("(y/n)")) {
                    // 确认类：是/否按钮
                    int r = JOptionPane.showConfirmDialog(parent, scroll, "FRT 确认",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    result[0] = r == JOptionPane.YES_OPTION ? "y" : "n";
                } else {
                    // 输入类：滚动提示 + 输入框
                    JTextField input = new JTextField(34);
                    JPanel panel = new JPanel(new BorderLayout(8, 8));
                    panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                    panel.add(scroll, BorderLayout.CENTER);
                    panel.add(input, BorderLayout.SOUTH);
                    int r = JOptionPane.showConfirmDialog(parent, panel, "FRT 输入",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                    result[0] = (r == JOptionPane.OK_OPTION) ? input.getText().trim() : "";
                }
            });
        } catch (Exception e) {
            result[0] = "";
        }
        return result[0] == null ? "" : result[0];
    }
}
