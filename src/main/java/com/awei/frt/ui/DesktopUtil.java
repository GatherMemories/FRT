package com.awei.frt.ui;

import com.awei.frt.util.LoggerUtil;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 桌面操作工具（便捷功能）：用系统默认方式打开目录 / 文件 / 网址。
 * 优先 Desktop API；Linux 无 Desktop 支持时回退 xdg-open；全部失败仅记日志不崩溃。
 */
public final class DesktopUtil {

    private DesktopUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 打开目录（目录不存在时先创建，方便首次使用直达） */
    public static boolean openDirectory(Path dir) {
        if (dir == null) {
            return false;
        }
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return openPath(dir);
        } catch (Exception e) {
            LoggerUtil.logException("[警告] 打开目录失败: " + dir, e);
            return false;
        }
    }

    /** 打开文件或目录 */
    public static boolean openPath(Path path) {
        if (path == null || !Files.exists(path)) {
            LoggerUtil.logWarn("[警告] 路径不存在，无法打开: " + path);
            return false;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop != null && desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(path.toFile());
                return true;
            }
            return openWithXdg(path);
        } catch (Exception e) {
            // 无桌面环境（headless）等：回退 xdg-open
            try {
                return openWithXdg(path);
            } catch (Exception e2) {
                LoggerUtil.logException("[警告] 打开失败: " + path, e2);
                return false;
            }
        }
    }

    /** 用系统默认浏览器打开网址 */
    public static boolean openUri(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI.create(url));
                return true;
            }
            return openWithXdg(Path.of(url));
        } catch (Exception e) {
            LoggerUtil.logException("[警告] 打开网址失败: " + url, e);
            return false;
        }
    }

    /** Linux 回退：xdg-open */
    private static boolean openWithXdg(Path path) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("xdg-open", path.toString()).start();
        return true;
    }
}
