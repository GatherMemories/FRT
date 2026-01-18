package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;

/**
 * @Author: mou_ren
 * @Date: 2026/1/10 19:50
 */
public class FileNameStrategy implements OperationStrategy {

    @Override
    public void execute(FileNode node, OperationContext context, String[] operationType) {

    }

    /**
     *  增加操作
     * @param node
     * @param rule
     * @param context
     */
    public void addExecute(FileNode node, String rule, OperationContext context) {

    }

    /**
     *  删除操作
     * @param node
     * @param rule
     * @param context
     */
    public void deleteExecute(FileNode node, String rule, OperationContext context) {

    }

    /**
     *  修改操作
     * @param node
     * @param rule
     * @param context
     */
    public void modifyExecute(FileNode node, String rule, OperationContext context) {

    }


}
