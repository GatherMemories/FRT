package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.model.OperationRecord;

/**
 * 操作策略抽象基类（模板方法）
 * 统一"节点校验 → 节点过滤 → 按操作类型分派"流程，子类只需实现 add/replace/delete 三个钩子，
 * 消除各策略中重复的 null 校验、目录判断、操作类型判断（Arrays.stream anyMatch）等样板代码。
 *
 * 流程：
 * 1. null 校验
 * 2. accepts(node, context)：节点类型过滤 + 规则匹配（只调用一次）
 * 3. 按操作类型顺序调用 doAdd / doReplace / doDelete：
 *    - 钩子返回 true 表示"已处理该节点"
 *    - stopOnHandled()=true 的策略（如 FileSameName 单文件策略）处理成功后立即返回，不再执行后续钩子
 *    - stopOnHandled()=false 的策略（如 McMod 目录级策略）会执行所有启用的钩子（各钩子内部按 mod 分派）
 */
public abstract class AbstractOperationStrategy implements OperationStrategy {

    @Override
    public final void execute(FileNode node, OperationContext context, String[] operationType) {
        if (node == null || context == null || operationType == null) {
            return;
        }
        if (!accepts(node, context)) {
            return;
        }
        if (contains(operationType, OperationContext.OPERATION_ADD) && doAdd(node, context)) {
            if (stopOnHandled()) {
                return;
            }
        }
        if (contains(operationType, OperationContext.OPERATION_REPLACE) && doReplace(node, context)) {
            if (stopOnHandled()) {
                return;
            }
        }
        if (contains(operationType, OperationContext.OPERATION_DELETE) && doDelete(node, context)) {
            if (stopOnHandled()) {
                return;
            }
        }
    }

    /**
     * 节点是否被本策略接受（类型过滤 + 规则匹配）
     * 只调用一次，子类可覆盖
     */
    protected boolean accepts(FileNode node, OperationContext context) {
        return true;
    }

    /**
     * 单节点处理完成后是否立即停止（不再执行后续操作类型钩子）
     * 默认 true；目录级策略（一个目录内多文件分派）应返回 false
     */
    protected boolean stopOnHandled() {
        return true;
    }

    /**
     * 新增钩子（仅在操作类型含 ADD 时调用）
     * @return true=已处理该节点
     */
    protected abstract boolean doAdd(FileNode node, OperationContext context);

    /**
     * 替换钩子（仅在操作类型含 REPLACE 时调用）
     * @return true=已处理该节点
     */
    protected abstract boolean doReplace(FileNode node, OperationContext context);

    /**
     * 删除钩子（仅在操作类型含 DELETE 时调用）
     * @return true=已处理该节点
     */
    protected abstract boolean doDelete(FileNode node, OperationContext context);

    /**
     * 操作类型数组是否包含指定操作
     */
    protected static boolean contains(String[] operationType, String op) {
        if (operationType == null || op == null) {
            return false;
        }
        for (String type : operationType) {
            if (op.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建带策略类型标识的操作记录
     */
    protected OperationRecord newRecord(OperationContext context) {
        OperationRecord record = new OperationRecord();
        record.setStrategyType(getStrategyType());
        return record;
    }
}
