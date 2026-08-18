package com.awei.frt.testplugin;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.strategy.OperationStrategy;

/**
 * 测试用外部策略插件（经 classpath META-INF/services SPI 加载）
 * 用于验证"外部策略动态加载"功能：玩家按规范编写策略类即可接入。
 */
public class ExternalPluginStrategy implements OperationStrategy {

    @Override
    public String getStrategyType() {
        return "ExternalPluginStrategy";
    }

    @Override
    public String getDescription() {
        return "测试外部插件策略（SPI 加载）";
    }

    @Override
    public void execute(FileNode node, OperationContext context, String[] operationType) {
        // 测试策略：不执行实际操作
    }
}
