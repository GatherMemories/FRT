package com.awei.frt.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRTFrame 菜单结构测试（AC-5，headless 可运行：构造 JMenuBar 无需显示环境）：
 * buildMenuBar 以静态方法抽出，传 null frame 只构建骨架，测试逐项核对菜单结构与勾选状态。
 * - 三个顶级菜单：文件 / 视图 / 帮助
 * - 文件：打开目录 ▸ 更新/目标/删除/备份/日志目录 5 项、分隔线、退出
 * - 视图：主题 ▸ 浅色/深色（单选勾选，与 UITheme.isDark 一致）、日志字体 ▸ 缩小(A-)/放大(A+)
 * - 帮助：检查更新、启动时自动检查更新（JCheckBoxMenuItem 勾选项）、分隔线、关于
 */
class FRTFrameMenuTest {

    @Test
    void menuBarHasFileViewHelp() {
        JMenuBar bar = FRTFrame.buildMenuBar(null);
        assertEquals(3, bar.getMenuCount());
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            titles.add(bar.getMenu(i).getText());
        }
        assertArrayEquals(new String[]{"文件", "视图", "帮助"}, titles.toArray());
    }

    @Test
    void fileMenuHasOpenDirSubmenuAndExit() {
        JMenuBar bar = FRTFrame.buildMenuBar(null);
        JMenu file = bar.getMenu(0);

        // 打开目录子菜单 + 5 个目录项
        JMenu openDir = (JMenu) file.getMenuComponent(0);
        assertEquals("打开目录", openDir.getText());
        List<String> items = new ArrayList<>();
        for (int i = 0; i < openDir.getItemCount(); i++) {
            items.add(openDir.getItem(i).getText());
        }
        assertEquals(List.of("更新目录", "目标目录", "删除目录", "备份目录", "日志目录"), items,
                "打开目录子菜单应含 更新/目标/删除/备份/日志 5 项");

        // 分隔线 + 退出（最后一项）
        assertTrue(file.getMenuComponent(1) instanceof JPopupMenu.Separator, "打开目录后应有分隔线");
        JMenuItem exit = file.getItem(file.getItemCount() - 1);
        assertEquals("退出", exit.getText());
    }

    @Test
    void viewMenuHasThemeAndFontSubmenus() {
        JMenuBar bar = FRTFrame.buildMenuBar(null);
        JMenu view = bar.getMenu(1);

        // 主题 ▸ 浅色/深色：单选勾选，勾选状态与 UITheme.isDark 一致
        JMenu theme = (JMenu) view.getMenuComponent(0);
        assertEquals("主题", theme.getText());
        assertEquals(2, theme.getItemCount());
        JMenuItem light = theme.getItem(0);
        JMenuItem dark = theme.getItem(1);
        assertEquals("浅色", light.getText());
        assertEquals("深色", dark.getText());
        assertTrue(light instanceof JRadioButtonMenuItem, "主题项应为单选按钮（勾选标记）");
        assertTrue(dark instanceof JRadioButtonMenuItem);
        assertEquals(!UITheme.isDark(), ((JRadioButtonMenuItem) light).isSelected(),
                "浅色勾选状态应等于当前非深色");
        assertEquals(UITheme.isDark(), ((JRadioButtonMenuItem) dark).isSelected(),
                "深色勾选状态应等于当前 isDark()");
        // 单选互斥：同一 ButtonGroup 内只能选中一个
        assertTrue(((JRadioButtonMenuItem) light).isSelected() != ((JRadioButtonMenuItem) dark).isSelected());

        // 日志字体 ▸ 缩小(A-) / 放大(A+)
        JMenu font = (JMenu) view.getMenuComponent(1);
        assertEquals("日志字体", font.getText());
        assertEquals("缩小 (A-)", font.getItem(0).getText());
        assertEquals("放大 (A+)", font.getItem(1).getText());
    }

    @Test
    void helpMenuHasCheckUpdateAutoCheckAndAbout() {
        JMenuBar bar = FRTFrame.buildMenuBar(null);
        JMenu help = bar.getMenu(2);
        // 帮助菜单结构（v0.1.15）：检查更新 / 启动时自动检查更新（勾选项）/ 分隔线 / 关于
        assertEquals("检查更新", help.getItem(0).getText());
        JMenuItem autoCheck = help.getItem(1);
        assertEquals("启动时自动检查更新", autoCheck.getText());
        assertTrue(autoCheck instanceof JCheckBoxMenuItem, "开关项应为复选框菜单项");
        // 构建骨架（frame 为 null）时勾选状态取默认值 true（实现约定：见 buildMenuBar 注释）；
        // 构建真实 frame 时与 config.isAutoCheckUpdate() 同步（AC-4.2）
        assertTrue(((JCheckBoxMenuItem) autoCheck).isSelected(),
                "frame 为 null 时勾选状态应取默认开启（与实现约定一致）");
        assertTrue(help.getMenuComponent(2) instanceof JPopupMenu.Separator,
                "开关项与关于之间应有分隔线");
        assertEquals("关于", help.getItem(3).getText());
    }

    @Test
    void autoCheckItemFollowsConfigWhenFrameBuilt() {
        // 构建真实 frame 时勾选状态与 config.isAutoCheckUpdate() 同步（AC-4.2）：
        // headless 无法实例化 JFrame，直接验证 buildMenuBar 使用的勾选取值逻辑 initialAutoCheckState——
        // config 为 null（骨架）取默认开启；config 关闭/开启时如实跟随
        assertTrue(FRTFrame.initialAutoCheckState(null), "config 为 null 时取默认开启");
        com.awei.frt.model.Config off = new com.awei.frt.model.Config();
        off.setAutoCheckUpdate(false);
        assertFalse(FRTFrame.initialAutoCheckState(off), "config 关闭时勾选应为未选中");
        com.awei.frt.model.Config on = new com.awei.frt.model.Config();
        on.setAutoCheckUpdate(true);
        assertTrue(FRTFrame.initialAutoCheckState(on), "config 开启时勾选应为选中");
        // 骨架菜单实际展示状态与默认值一致（与 buildMenuBar 实现联动）
        JCheckBoxMenuItem autoCheck = (JCheckBoxMenuItem) FRTFrame.buildMenuBar(null).getMenu(2).getItem(1);
        assertEquals(FRTFrame.initialAutoCheckState(null), autoCheck.isSelected());
    }
}
