package com.awei.frt.handler;

import java.nio.file.Path;

/**
 * 日志记录处理器
 * 记录文件处理过程中的日志信息
 */
public class LoggingHandler extends AbstractFileHandler {
    private final String logPrefix;

    public LoggingHandler(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    @Override
    protected boolean doHandle(Path filePath) {
        System.out.println(logPrefix + " 处理文件: " + filePath);
        System.out.println(logPrefix + " 文件大小: " + (filePath.toFile().length() / 1024.0) + " KB");
        return true; // 继续处理
    }
}
