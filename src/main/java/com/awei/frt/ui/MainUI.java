package com.awei.frt.ui;

import javax.swing.SwingUtilities;

/**
 * FRT Swing 图形界面入口
 * 启动方式：java -jar FRT.jar --ui（或 mvn exec:java -Dexec.args="--ui"）
 * 界面功能与控制台一致：更新/删除/恢复/规则向导/清理备份，交互确认用对话框。
 */
public class MainUI {

    public static void main(String[] args) {
        // Swing 文本抗锯齿（与 Main 入口一致）：直接运行 MainUI 时同样开启
        System.setProperty("awt.useSystemAAFontSettings",
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "lcd" : "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                new FRTFrame().setVisible(true);
            } catch (Throwable t) {
                // 用户可见提示保持友好简洁：简短异常类型+消息（完整堆栈只进 logs/frt.log）
                String brief = t.getClass().getSimpleName()
                        + (t.getMessage() != null ? ": " + t.getMessage() : "");
                System.err.println("[失败] 无法启动图形界面: " + brief);
                System.err.println("[提示] 请检查 config.json 中的 更新/目标/删除/备份 目录配置，");
                System.err.println("       或使用控制台模式运行：start-frt.bat --console");
            }
        });
    }
}
