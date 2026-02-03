package com.awei.frt.handler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 规则检查处理器
 * 检查文件路径是否存在对应的规则文件
 */
public class RuleCheckHandler extends AbstractFileHandler {
    private final Path rulePath;

    public RuleCheckHandler(Path rulePath) {
        this.rulePath = rulePath;
    }

    @Override
    protected boolean doHandle(Path filePath) {
        Path ruleFile = rulePath.resolve("replace.json");

        if (!Files.exists(ruleFile)) {
            System.out.println("⚠️  警告: 规则文件不存在 - " + ruleFile);
            return false; // 停止处理
        }

        System.out.println("✓ 规则文件存在 - " + ruleFile);
        return true; // 继续处理
    }
}
