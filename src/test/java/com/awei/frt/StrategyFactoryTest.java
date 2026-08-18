package com.awei.frt;

import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略注册表测试：去枚举重构后，策略类型由策略类自报、工厂按注册表创建
 */
class StrategyFactoryTest {

    @Test
    void builtinStrategiesRegistered() {
        assertTrue(StrategyFactory.isSupported("FileSameName"));
        assertTrue(StrategyFactory.isSupported("McMod"));
        assertTrue(StrategyFactory.getSupportedTypes().containsAll(java.util.Set.of("FileSameName", "McMod")));
    }

    @Test
    void createStrategyReturnsTypedInstance() {
        OperationStrategy fs = StrategyFactory.createStrategy("FileSameName");
        assertEquals("FileSameName", fs.getStrategyType());
        OperationStrategy mc = StrategyFactory.createStrategy("McMod");
        assertEquals("McMod", mc.getStrategyType());
    }

    @Test
    void createStrategyIsCachedSingleton() {
        assertSame(StrategyFactory.createStrategy("McMod"), StrategyFactory.createStrategy("McMod"));
    }

    @Test
    void unknownTypeRejected() {
        assertFalse(StrategyFactory.isSupported("NoSuchStrategy"));
        assertThrows(IllegalArgumentException.class, () -> StrategyFactory.createStrategy("NoSuchStrategy"));
    }

    @Test
    void registerCustomStrategy() {
        StrategyFactory.register("TestDummy", TestDummyStrategy::new, "测试策略");
        try {
            assertTrue(StrategyFactory.isSupported("TestDummy"));
            OperationStrategy s = StrategyFactory.createStrategy("TestDummy");
            assertEquals("TestDummy", s.getStrategyType());
            assertSame(s, StrategyFactory.createStrategy("TestDummy"), "自定义策略也应走缓存");
        } finally {
            // 测试后不清理注册表（单测进程内无影响；注册表全局共享，重复注册同类型仅覆盖）
        }
    }

    /** 测试用最小策略实现 */
    static class TestDummyStrategy implements OperationStrategy {
        @Override
        public String getStrategyType() {
            return "TestDummy";
        }

        @Override
        public void execute(com.awei.frt.core.node.FileNode node,
                            com.awei.frt.core.context.OperationContext context,
                            String[] operationType) {
            // 无操作
        }
    }
}
