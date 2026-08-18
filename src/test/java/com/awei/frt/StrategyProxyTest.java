package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.core.strategy.StrategyProxy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.Config;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态代理策略过滤器测试：
 * - 工厂返回的是代理对象
 * - 透传 getStrategyType
 * - 策略抛异常时：代理记录日志与失败统计，不向上抛（更新流程不中断）
 */
class StrategyProxyTest {

    @Test
    void factoryReturnsProxyInstance() {
        OperationStrategy s = StrategyFactory.createStrategy("McMod");
        assertTrue(Proxy.isProxyClass(s.getClass()), "工厂应返回代理对象");
        assertEquals("McMod", s.getStrategyType(), "代理应透传策略类型");
    }

    @Test
    void proxySwallowsExceptionAndRecordsError() {
        OperationStrategy failing = new OperationStrategy() {
            @Override
            public String getStrategyType() {
                return "Failing";
            }

            @Override
            public void execute(com.awei.frt.core.node.FileNode node,
                                OperationContext context,
                                String[] operationType) {
                throw new IllegalStateException("boom");
            }
        };

        OperationStrategy proxy = StrategyProxy.wrap(failing);

        Config config = ConfigLoader.getConfig();
        config.setTargetPath(Path.of("target-tmp"));
        OperationContext ctx = new OperationContext(config);
        FileLeaf leaf = new FileLeaf(Path.of("x.txt"), "x.txt");

        // 不抛异常（代理兜底）
        assertDoesNotThrow(() -> proxy.execute(leaf, ctx, new String[]{OperationContext.OPERATION_ADD}));

        // 失败被统计
        assertEquals(1, ctx.getProcessingResult().getErrorCount());
        assertFalse(ctx.getProcessingResult().isSuccess());
        assertFalse(ctx.getProcessingResult().getOperationRecords().get(0).isSuccess());
    }
}
