package com.awei.frt.ui;

import javax.swing.SwingUtilities;

/**
 * FRT Swing 图形界面入口
 * 启动方式：java -jar FRT.jar --ui（或 mvn exec:java -Dexec.args="--ui"）
 * 界面功能与控制台一致：更新/删除/恢复/规则向导/清理备份，交互确认用对话框。
 */
public class MainUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new FRTFrame().setVisible(true);
            } catch (Throwable t) {
                // 无图形环境（headless）时给出一致提示，避免静默失败
                System.err.println("[失败] 无法启动图形界面: " + t.getMessage());
                System.err.println("[提示] 请在有图形界面的环境运行，或使用控制台模式（不带 --ui 参数）");
            }
        });
    }
}
