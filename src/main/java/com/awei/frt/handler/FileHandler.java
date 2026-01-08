package com.awei.frt.handler;

import java.nio.file.Path;

/**
 * 文件处理器接口
 * 责任链模式的基础接口
 */
public interface FileHandler {
    /**
     * 处理文件
     * @param filePath 文件路径
     * @return true-继续处理链，false-停止处理
     */
    boolean handle(Path filePath);

    /**
     * 设置下一个处理器
     * @param nextHandler 下一个处理器
     */
    void setNext(FileHandler nextHandler);
}
