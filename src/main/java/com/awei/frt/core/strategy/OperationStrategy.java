package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;

/**
 * 操作策略接口（策略模式）
 * 定义了所有文件操作策略的公共接口
 */
public interface OperationStrategy {
    /**
     * 执行操作（增、删、改）
     * @param node 文件节点
     * @param context 操作上下文
     * @param operationType 操作类型（增、删、改--限制）
     */
    void execute(FileNode node, OperationContext context, String operationType);

}
