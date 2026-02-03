package com.awei.frt.handler;

import java.nio.file.Path;

/**
 * 抽象文件处理器
 * 实现责任链模式的基本逻辑
 */
public abstract class AbstractFileHandler implements FileHandler {
    protected FileHandler nextHandler;

    @Override
    public void setNext(FileHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    @Override
    public boolean handle(Path filePath) {
        // 执行当前处理器的逻辑
        boolean shouldContinue = doHandle(filePath);

        // 如果应该继续，且存在下一个处理器，则传递给下一个
        if (shouldContinue && nextHandler != null) {
            return nextHandler.handle(filePath);
        }

        return shouldContinue;
    }

    /**
     * 具体的处理逻辑
     * @param filePath 文件路径
     * @return true-继续处理链，false-停止处理
     */
    protected abstract boolean doHandle(Path filePath);
}
