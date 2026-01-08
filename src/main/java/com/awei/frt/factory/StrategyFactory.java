package com.awei.frt.factory;

import com.awei.frt.core.strategy.AddStrategy;
import com.awei.frt.core.strategy.DeleteStrategy;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.core.strategy.ReplaceStrategy;

/**
 * 策略工厂（工厂模式）
 * 用于创建不同类型的文件操作策略
 */
public class StrategyFactory {
    
    public enum OperationType {
        REPLACE,
        ADD,
        DELETE
    }
    
    /**
     * 创建操作策略
     * @param type 操作类型
     * @return 对应的策略实例
     */
    public static OperationStrategy createStrategy(OperationType type) {
        switch (type) {
            case REPLACE:
                return new ReplaceStrategy();
            case ADD:
                return new AddStrategy();
            case DELETE:
                return new DeleteStrategy();
            default:
                throw new IllegalArgumentException("不支持的操作类型: " + type);
        }
    }
}