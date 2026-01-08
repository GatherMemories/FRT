package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;

/**
 * 操作策略接口（策略模式）
 * 定义了所有文件操作策略的公共接口
 */
public interface OperationStrategy {
    /**
     * 执行操作
     * @param node 要操作的节点
     * @param rule 操作规则
     * @param context 操作上下文
     */
    void execute(FileNode node, String rule, OperationContext context);
}