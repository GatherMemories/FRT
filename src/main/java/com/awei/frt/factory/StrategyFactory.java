package com.awei.frt.factory;

import com.awei.frt.core.strategy.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略工厂（工厂模式）
 * 用于创建不同类型的文件操作策略
 */
public class StrategyFactory {
    // 策略映射表（策略缓存，单例）
    private static Map<String, OperationStrategy> strategyMap = new HashMap<>();
    // StrategyFactory单例
    private static volatile StrategyFactory instance = new StrategyFactory();

    public enum StrategyType {
        FILE_NAME("FileName", "文件名处理策略"),
        MC_MOD("McMod", "mcMod处理策略");

        private final String value;
        private final String description;

        StrategyType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        // 根据值获取枚举对象
        public static StrategyType getByValue(String value) {
            for (StrategyType type : values()) {
                if (type.getValue().equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 私有构造函数，防止实例化
     */
    private StrategyFactory() {
    }

    /**
     * 获取策略工厂单例
     * @return 策略工厂单例
     */
    public static StrategyFactory getInstance() {
        if(instance == null){
            synchronized (StrategyFactory.class) {
                if(instance == null){
                    instance = new StrategyFactory();
                }
            }
        }
        return instance;
    }

    /**
     * 创建匹配策略（单例）
     * @param type 操作类型
     * @return 对应的策略实例
     */
    public static OperationStrategy createStrategy(StrategyType type) {
        if(!strategyMap.containsKey(type.getValue())){
            synchronized (StrategyFactory.class) {
                if(!strategyMap.containsKey(type.getValue())){
                    OperationStrategy strategy = null;
                    switch (type) {
                        case MC_MOD:
                            strategy = new McModStrategy();
                            break;
                        case FILE_NAME:
                            strategy = new FileNameStrategy();
                            break;
                        default:
                            throw new IllegalArgumentException("不支持的匹配策略类型: " + type);
                    }
                    strategyMap.put(type.getValue(), strategy);
                }
            }
        }
        return strategyMap.get(type.getValue());
    }

    public static OperationStrategy createStrategy(String type) {
        StrategyType strategyType = StrategyType.getByValue(type);
        if (strategyType == null) {
            throw new IllegalArgumentException("不支持的匹配策略类型: " + type);
        }
        return createStrategy(strategyType);
    }

}
