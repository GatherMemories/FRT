package com.awei.frt.ui;

import com.awei.frt.util.BuildInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * 关于对话框（便捷功能）：程序名 + 版本号（自动取自 pom.xml）+ 构建时间 + GitHub 链接 + 运行环境。
 * 从 帮助 → 关于 打开；版本信息与状态栏/标题栏同源（BuildInfo）。
 */
public class AboutDialog extends JDialog {

    public AboutDialog(JFrame owner) {
        super(owner, "关于", true);
        UITheme.apply();
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(UITheme.PANEL_BG);
        content.setBorder(BorderFactory.createEmptyBorder(18, 24, 14, 24));

        // 标题区：程序名 + 版本
        JLabel title = new JLabel("多层级文件夹更新工具 " + BuildInfo.VERSION);
        title.setFont(UITheme.TITLE_FONT);
        title.setForeground(UITheme.TEXT);
        JLabel subtitle = new JLabel("多层级文件夹更新/删除/恢复工具（Java " + System.getProperty("java.version") + "）");
        subtitle.setFont(UITheme.SMALL_FONT);
        subtitle.setForeground(UITheme.MUTED);
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        header.setOpaque(false);
        header.add(title);
        JPanel header2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        header2.setOpaque(false);
        header2.add(subtitle);
        JPanel headerBox = new JPanel(new BorderLayout());
        headerBox.setOpaque(false);
        headerBox.add(header, BorderLayout.NORTH);
        headerBox.add(header2, BorderLayout.SOUTH);
        content.add(headerBox, BorderLayout.NORTH);

        // 信息区：构建时间 / 许可证 / GitHub 链接
        JPanel info = new JPanel(new BorderLayout(0, 4));
        info.setOpaque(false);
        JLabel build = new JLabel("构建时间: " + (BuildInfo.BUILD_TIME.isBlank() ? "未知" : BuildInfo.BUILD_TIME));
        build.setFont(UITheme.SMALL_FONT);
        build.setForeground(UITheme.MUTED);
        JLabel license = new JLabel("许可证: MIT");
        license.setFont(UITheme.SMALL_FONT);
        license.setForeground(UITheme.MUTED);
        LinkLabel github = new LinkLabel("GitHub 仓库: " + BuildInfo.GITHUB_URL, BuildInfo.GITHUB_URL);
        github.setFont(UITheme.SMALL_FONT);
        JPanel infoBox = new JPanel(new BorderLayout());
        infoBox.setOpaque(false);
        infoBox.add(build, BorderLayout.NORTH);
        infoBox.add(license, BorderLayout.CENTER);
        infoBox.add(github, BorderLayout.SOUTH);
        content.add(infoBox, BorderLayout.CENTER);

        // 底部：关闭按钮
        JButton close = new JButton("关闭");
        UITheme.styleButton(close);
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.setOpaque(false);
        bottom.add(close);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setResizable(false);
        pack();
        Dimension d = getPreferredSize();
        setSize(Math.max(d.width, 360), d.height);
        setLocationRelativeTo(owner);
    }
}
