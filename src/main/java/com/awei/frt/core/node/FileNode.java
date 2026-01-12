package com.awei.frt.core.node;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;

import java.nio.file.Path;

/**
 * 文件节点抽象基类（组合模式）
 * 定义了文件系统中所有节点的公共接口
 */
public abstract class FileNode {
    protected final Path path;              // 节点的完整路径
    protected final String relativePath;    // 节点的相对路径

    public static final String UPDATE_OPERATION = "update_operation"; //更新操作类型
    public static final String DELETE_OPERATION = "delete_operation"; //删除操作类型
    public static final String PREVIEW_OPERATION = "preview_operation"; //预览操作类型（还未实现--禁用增删改）

    public FileNode(Path path, String relativePath) {
        this.path = path;
        this.relativePath = relativePath;
    }

    /**
     * 处理当前节点
     * @param localRuleIC 本层规则继承上下文
     * @param context 操作上下文
     * @param operationType 操作类型（增、删、改--限制）
     */
    public abstract void process(RuleInheritanceContext localRuleIC, OperationContext context, String operationType);

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
