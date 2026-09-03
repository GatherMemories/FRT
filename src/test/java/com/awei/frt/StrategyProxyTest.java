package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.core.strategy.StrategyProxy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.Config;
import com.awei.frt.model.OperationRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

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

        // 备份路径隔离到 JUnit 临时目录（@TempDir 自动清理），
        // 避免会话记录污染真实 testDic/backup 或固定 /tmp 路径跨构建残留
        TestSupport.isolateBackup(tempDir);
        try {
            Config config = ConfigLoader.getConfig();
            config.setTargetPath(Path.of("target-tmp"));
            OperationContext ctx = new OperationContext(config);
            FileLeaf leaf = new FileLeaf(Path.of("x.txt"), "x.txt");

            // 不抛异常（代理兜底）
            assertDoesNotThrow(() -> proxy.execute(leaf, ctx, new String[]{OperationContext.OPERATION_ADD}));

            // 失败被统计
            assertEquals(1, ctx.getProcessingResult().getErrorCount());
            assertFalse(ctx.getProcessingResult().isSuccess());
            OperationRecord failed = ctx.getProcessingResult().getOperationRecords().get(0);
            assertFalse(failed.isSuccess());
            // 审查 M6：失败记录应补齐操作类型与目标路径（原实现为半空壳，UI/恢复无法定位失败项）
            assertEquals(OperationContext.OPERATION_ADD, failed.getOperationType(),
                    "失败记录应带 operationType（来自 execute 的操作类型参数）");
            assertNotNull(failed.getTargetPath(), "失败记录应带目标路径（定位失败项）");
        } finally {
            TestSupport.restoreBackupPath();
        }
    }
}
