package com.awei.frt.ui;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;

/**
 * Swing 交互实现（固定输入框版，无弹窗）：
 * 服务线程在 readLine() 处挂起等待；主窗口底部固定输入区（提示行 + 快捷按钮 + 输入框）
 * 由用户在窗口内输入/点击后提交，UI 不被打断，长内容始终在日志区可滚动查看。
 */
public class SwingPrompter implements UserPrompter {

    /** 提示来源：取走"自上次输入以来"的完整提示文本（消费式） */
    public interface PromptSource {
        String takePrompt();
    }

    /** 输入区控制（由主窗口实现，EDT 调用） */
    public interface InputPanel {
        /** 展示提示并启用输入（生成快捷按钮、聚焦输入框） */
        void showPrompt(String prompt);

        /** 服务结束：禁用输入区 */
        void resetInput();
    }

    private final PromptSource promptSource;
    private final InputPanel inputPanel;
    private CountDownLatch latch = new CountDownLatch(1);
    private volatile String pendingResult = "";

    public SwingPrompter(PromptSource promptSource, InputPanel inputPanel) {
        this.promptSource = promptSource;
        this.inputPanel = inputPanel;
    }

    @Override
    public String readLine() {
        String prompt = promptSource.takePrompt();
        String safe = (prompt == null || prompt.isBlank()) ? "请输入:" : prompt;
        latch = new CountDownLatch(1);
        pendingResult = "";
        SwingUtilities.invokeLater(() -> inputPanel.showPrompt(safe));
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
        return pendingResult;
    }

    /**
     * 提交用户输入（主窗口在输入框回车 / 快捷按钮 / 取消时调用，EDT 线程）
     */
    public void submit(String text) {
        pendingResult = text == null ? "" : text.trim();
        if (latch.getCount() > 0) {
            latch.countDown();
        }
    }
}
