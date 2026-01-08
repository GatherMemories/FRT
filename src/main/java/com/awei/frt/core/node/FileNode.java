package com.awei.frt.core.node;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.strategy.OperationStrategy;

import java.nio.file.Path;

/**
 * 文件节点抽象基类（组合模式）
 * 定义了文件系统中所有节点的公共接口
 */
public abstract class FileNode {
    protected final Path path;
    protected final String relativePath;

    public FileNode(Path path, String relativePath) {
        this.path = path;
        this.relativePath = relativePath;
    }

    /**
     * 处理当前节点
     */
    public abstract void process(OperationStrategy strategy, String rule, OperationContext context);

    /**
     * 获取节点路径
     */
    public Path getPath() {
        return path;
    }

    /**
     * 获取相对路径
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * 获取节点名称
     */
    public String getName() {
        return path.getFileName().toString();
    }

    /**
     * 检查是否是目录
     */
    public abstract boolean isDirectory();

    /**
     * 获取子节点数量
     */
    public abstract int getChildCount();
}