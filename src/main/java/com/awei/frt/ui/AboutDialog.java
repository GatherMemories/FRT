package com.awei.frt.ui;

import com.awei.frt.util.BuildInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * 关于对话框：程序名 + 版本号（自动取自 pom.xml）+ 构建时间 + 运行环境 + 许可证 + GitHub 链接。
 * 布局用 BoxLayout 纵向逐行堆叠（每行一个组件、居中对齐，不会互相挤压/重叠），
 * 字体层级：标题 14 加粗 / 版本 13 主题色 / 信息行 11 灰；颜色全部取 UITheme（跟随深浅主题）。
 */
public class AboutDialog extends JDialog {

    /** 内容区最大宽度：超长内容（如完整构建时间）不把对话框撑得过宽，BoxLayout 内只裁剪不重叠 */
    private static final int MAX_WIDTH = 460;

    public AboutDialog(JFrame owner) {
        super(owner, "关于", true);
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UITheme.PANEL_BG);
        content.setBorder(BorderFactory.createEmptyBorder(20, 28, 16, 28));

        // 标题 + 版本（版本取 pom.xml 注入值，与标题栏/状态栏同源）
        JLabel title = new JLabel("多层级文件夹更新工具");
        title.setFont(UITheme.TITLE_FONT);          // 14 加粗：最醒目
        title.setForeground(UITheme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(2));

        JLabel version = new JLabel("版本 " + BuildInfo.VERSION);
        version.setFont(UITheme.BASE_FONT);          // 13 主题色：次醒目
        version.setForeground(UITheme.PRIMARY);
        version.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(version);

        // 分隔线
        content.add(Box.createVerticalStrut(12));
        JSeparator sep = new JSeparator();
        sep.setForeground(UITheme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(sep);
        content.add(Box.createVerticalStrut(12));

        // 信息行（逐行堆叠，不重叠）
        addInfoRow(content, "构建时间", BuildInfo.BUILD_TIME.isBlank() ? "未知" : BuildInfo.BUILD_TIME);
        addInfoRow(content, "运行环境", "Java " + System.getProperty("java.version"));
        addInfoRow(content, "许可证", "MIT");

        // GitHub 链接（可点击打开仓库；显示地址去掉协议前缀，避免 HTML 长链接不换行撑宽）
        String repo = BuildInfo.GITHUB_URL;
        String display = (repo == null || repo.isBlank()) ? "（未配置）"
                : repo.replaceFirst("^https?://", "");
        LinkLabel github = new LinkLabel(display, repo);
        github.setFont(UITheme.SMALL_FONT);
        github.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createVerticalStrut(10));
        content.add(github);

        // 关闭按钮
        JButton close = new JButton("关闭");
        UITheme.styleButton(close);
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottom.setOpaque(false);
        bottom.add(close);
        content.add(Box.createVerticalStrut(14));
        content.add(bottom);

        setContentPane(content);
        setResizable(false);
        pack();
        // 超宽内容限制对话框宽度：BoxLayout 逐行布局只裁剪不重叠，高度仍按内容自适应
        if (getWidth() > MAX_WIDTH) {
            setSize(MAX_WIDTH, getHeight());
        }
        setLocationRelativeTo(owner);
    }

    /** 信息行：灰标签 + 正文值，一行一个 FlowLayout（居中对齐、不换行挤压） */
    private static void addInfoRow(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(UITheme.SMALL_FONT);               // 11 灰：标签弱化
        l.setForeground(UITheme.MUTED);
        JLabel v = new JLabel(value);
        v.setFont(UITheme.SMALL_FONT);
        v.setForeground(UITheme.TEXT);
        row.add(l);
        row.add(v);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        parent.add(row);
        parent.add(Box.createVerticalStrut(4));
    }
}
