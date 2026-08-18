package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;

/**
 * 操作策略接口（策略模式）
 * 定义了所有文件操作策略的公共接口
 */
public interface OperationStrategy {

    /**
     * 策略类型标识（注册表 key，也是规则文件 strategyType 字段的值）
     * 由每个策略类自行声明（取代旧的 StrategyFactory.StrategyType 枚举）
     * @return 策略类型字符串，如 "FileSameName" / "McMod"
     */
    String getStrategyType();

    /**
     * 策略说明（用于向导/菜单展示）
     * @return 中文说明
     */
    default String getDescription() {
        return "";
    }

    /**
     * 执行操作（增、删、改）
     * @param node 文件节点
     * @param context 操作上下文
     * @param operationType 操作类型（增、删、改--限制）
     */
    void execute(FileNode node, OperationContext context, String[] operationType);

}
