package com.awei.frt.ui;

import com.awei.frt.interaction.UserPrompter;

import javax.swing.SwingUtilities;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Swing 交互实现（固定输入框版，无弹窗）：
 * 服务线程在 readLine() 处挂起等待；主窗口底部固定输入区（提示行 + 快捷按钮 + 输入框）
 * 由用户在窗口内输入/点击后提交，UI 不被打断，长内容始终在日志区可滚动查看。
 * <p>
 * 线程协议：readLine() 在服务线程调用并阻塞；submit() 由 EDT 调用。
 * 原实现用 CountDownLatch + volatile 握手存在竞态——EDT 若在服务线程重置 latch 之前
 * submit（提前 countDown），输入会污染下一轮或丢失。现改为 BlockingQueue：
 * 每次提交的文本入队，readLine 阻塞取队，天然消除"提前提交丢失/污染下一轮"竞态。
 */
public class SwingPrompter implements UserPrompter {

    /** 提示来源：取走"自上次输入以来"的完整提示文本（消费式） */
    public interface PromptSource {
        String takePrompt();

        /**
         * 若当前输出末尾没有换行（如 "…? (y/n): " 这类 print 提示），补一个换行，
         * 避免用户提交输入后，后续日志直接拼在提示行后面（终端有回车换行，日志区没有）
         */
        void ensureLineBreak();
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
    private final BlockingQueue<String> responses = new LinkedBlockingQueue<>();

    public SwingPrompter(PromptSource promptSource, InputPanel inputPanel) {
        this.promptSource = promptSource;
        this.inputPanel = inputPanel;
    }

    @Override
    public String readLine() {
        // 丢弃上一轮残留的未消费输入（如用户连按两次回车/取消），
        // 避免残留文本静默"回答"本轮提示（旧 latch 实现在每轮开始时重置 pendingResult 同效）
        responses.clear();
        String prompt = promptSource.takePrompt();
        String safe = (prompt == null || prompt.isBlank()) ? "请输入:" : prompt;
        SwingUtilities.invokeLater(() -> inputPanel.showPrompt(safe));
        try {
            // 阻塞等待用户提交（submit 在 EDT 入队）；中断（服务取消）时返回空串
            return responses.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    /**
     * 提交用户输入（主窗口在输入框回车 / 快捷按钮 / 取消时调用，EDT 线程）
     */
    public void submit(String text) {
        promptSource.ensureLineBreak(); // 提示行未换行时先补换行，避免与后续日志拼接同行
        responses.offer(text == null ? "" : text.trim());
    }

    /**
     * 丢弃尚未被消费的输入（服务线程取消/中断时清理，避免残留输入污染下一轮提示）
     */
    void clearPending() {
        responses.clear();
    }
}
