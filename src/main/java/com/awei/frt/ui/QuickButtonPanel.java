package com.awei.frt.ui;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * 快捷按钮面板：FlowLayout 自动换行。
 * 宽度始终跟随外层视口（JViewport），高度按该宽度换行后的实际高度计算；
 * 由外层滚动面板决定最多显示多少行，超出部分可滚动查看，不会被窗口边缘裁掉。
 * 从 FRTFrame 独立出来，供主窗口与其他 UI 场景复用。
 */
public class QuickButtonPanel extends JPanel {
    private static final int HGAP = 4;
    private static final int VGAP = 2;

    public QuickButtonPanel() {
        super(new FlowLayout(FlowLayout.LEFT, HGAP, VGAP));
        setBackground(UITheme.PANEL_BG);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = getParent() != null ? getParent().getWidth() : 0;
        if (width <= 0) {
            width = 700; // 首次布局前按默认窗口宽度估算
        }
        return new Dimension(width, flowHeight(width));
    }

    /** 按 FlowLayout 相同的换行规则，计算在指定宽度下的实际总高度 */
    private int flowHeight(int width) {
        if (getComponentCount() == 0) {
            return 0;
        }
        int maxWidth = Math.max(width, 10);
        int x = 0;
        int y = 0;
        int rowH = 0;
        int maxY = 0;
        for (Component c : getComponents()) {
            Dimension p = c.getPreferredSize();
            if (x > 0 && x + p.width > maxWidth) {
                y += rowH + VGAP;
                x = 0;
                rowH = 0;
            }
            rowH = Math.max(rowH, p.height);
            x += p.width + HGAP;
            maxY = Math.max(maxY, y + rowH);
        }
        return maxY + VGAP * 2;
    }
}
