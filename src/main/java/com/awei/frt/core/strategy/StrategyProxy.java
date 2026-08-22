package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.util.LoggerUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 策略动态代理（待改进点 #5 落地）
 * 取代原先散落在 handler/ 下的责任链草案（AbstractFileHandler/LoggingHandler/RuleCheckHandler.txt）。
 * 由 StrategyFactory.createStrategy 统一包装，在策略执行外围自动完成：
 *   规则校验 → 执行日志（含耗时）→ 异常兜底（记日志 + 记失败统计，不中断整个更新）
 * 策略类本身不需要任何代理感知代码；对调用方（FolderNode/FileLeaf）完全透明。
 */
public class StrategyProxy implements InvocationHandler {

    private final OperationStrategy target;

    private StrategyProxy(OperationStrategy target) {
        this.target = target;
    }

    /**
     * 包装策略实例为代理对象
     */
    public static OperationStrategy wrap(OperationStrategy target) {
        return (OperationStrategy) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{OperationStrategy.class},
                new StrategyProxy(target));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 非 execute 方法（getStrategyType/getDescription/toString 等）直接透传
        if (!"execute".equals(method.getName())) {
            return method.invoke(target, args);
        }

        FileNode node = (FileNode) args[0];
        OperationContext context = (OperationContext) args[1];
        String rel = node == null ? "null" : node.getRelativePath();
        String type = target.getStrategyType();

        try {
            // 不打印"处理/完成"配对日志：链中每个策略对每个节点都会执行一次，
            // 大量 no-op（accepts 拒绝）会造成刷屏噪音；实质进度由策略内部
            // （+ 成功/= 成功/- 成功）与 UI 进度条体现。
            method.invoke(target, args);
        } catch (InvocationTargetException e) {
            // 目标方法抛出的异常在这里被拦截：记录日志与失败统计，不中断整个更新流程
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            handleError(type, rel, context, cause);
        } catch (Throwable t) {
            handleError(type, rel, context, t);
        }
        return null;
    }

    /**
     * 异常兜底：记日志 + 记失败操作记录（计入 errorCount，随备份/恢复体系走）
     */
    private void handleError(String type, String rel, OperationContext context, Throwable cause) {
        LoggerUtil.logException("[策略] " + type + " 执行异常: " + rel, cause);
        if (context != null) {
            OperationRecord record = new OperationRecord();
            record.setStrategyType(type);
            record.setErrorMessage(cause.toString());
            record.setSuccess(false);
            context.recordOperation(record);
        }
    }
}
