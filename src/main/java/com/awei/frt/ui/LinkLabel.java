package com.awei.frt.ui;

import javax.swing.JLabel;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

/**
 * 可点击超链接标签：显示为蓝色下划线链接，鼠标悬停变手型，点击用系统默认浏览器打开 URL。
 * 打开失败（无桌面环境/无浏览器等）时静默忽略，不影响程序运行。
 */
public class LinkLabel extends JLabel {

    private final String url;

    /**
     * @param text 链接显示文本（如 "v0.1.1-SNAPSHOT · GitHub"）
     * @param url  点击后打开的地址（如 "https://github.com/GatherMemories/FRT"）
     */
    public LinkLabel(String text, String url) {
        super("<html><a href='" + escape(url) + "'>" + escape(text) + "</a></html>");
        this.url = url;
        setToolTipText(url);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setForeground(UITheme.PRIMARY);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                open();
            }
        });
    }

    private void open() {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // 无桌面环境（headless）/浏览器不可用等：静默忽略，不影响程序
        }
    }

    /** HTML 属性与内容转义，防止特殊字符破坏链接渲染 */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
