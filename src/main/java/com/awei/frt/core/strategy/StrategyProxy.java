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
        // 第三个参数是操作类型数组（UPDATE_OPERATION/DELETE_OPERATION），
        // 供失败记录补齐 operationType（审查 M6：原失败记录无类型/路径，无法参与恢复/展示）
        String[] operationType = args.length > 2 && args[2] instanceof String[] arr ? arr : null;
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
            handleError(type, rel, node, context, operationType, cause);
        } catch (Throwable t) {
            handleError(type, rel, node, context, operationType, t);
        }
        return null;
    }

    /**
     * 异常兜底：记日志 + 记失败操作记录（计入 errorCount，随备份/恢复体系走）。
     * 失败记录补齐 operationType/targetPath：恢复流程只处理 isSuccess 的记录、
     * 失败记录会被跳过（不会触发"未知操作类型"或误恢复），但 UI/备份列表能看到
     * 失败项的类型与目标路径（审查 M6 原实现记录为半空壳）。
     */
    private void handleError(String type, String rel, FileNode node,
                             OperationContext context, String[] operationType, Throwable cause) {
        LoggerUtil.logException("[策略] " + type + " 执行异常: " + rel, cause);
        if (context != null) {
            OperationRecord record = new OperationRecord();
            record.setStrategyType(type);
            if (operationType != null && operationType.length > 0) {
                record.setOperationType(operationType[0]);
            }
            record.setErrorMessage(cause.toString());
            record.setSuccess(false);
            if (node != null && node.getPath() != null) {
                record.setTargetPath(node.getPath());
            }
            context.recordOperation(record);
        }
    }
}
