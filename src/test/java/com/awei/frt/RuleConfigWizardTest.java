package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.service.RuleConfigWizard;
import com.awei.frt.ui.UserPrompter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制台规则生成向导（RuleConfigWizard）端到端测试：
 * 通过脚本化 UserPrompter 驱动完整交互流程，验证：
 * - 策略链只存"后续步骤"（第1步=主策略自身），不复制主策略入链
 * - 生成的 strategyType 为注册表标识（非显示名）
 * - 生成的 JSON 可被 MatchRuleLoader 反解析（此前 UI 显示名 bug 会导致解析失败）
 */
class RuleConfigWizardTest {

    @TempDir
    Path tempDir;

    private Config saved;

    @AfterEach
    void restoreConfig() {
        if (saved != null) {
            Config c = ConfigLoader.getConfig();
            c.setBaseDirectory(saved.getBaseDirectory());
            c.setUpdatePath(saved.getUpdatePath());
            c.setTargetPath(saved.getTargetPath());
            c.setDeletePath(saved.getDeletePath());
            c.setBackupPath(saved.getBackupPath());
            c.setLogLevel(saved.getLogLevel());
        }
    }

    @Test
    void consoleWizardWritesFlatChainWithBareStrategyTypes() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        // 把配置指向临时目录，隔离全局 Config 单例
        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));

        // 脚本化输入：1=更新目录, 1=根目录, 1=FileSameName, patterns/exclude/inherit/replacements 均空,
        // y=配置策略链, 2=McMod, 步patterns=*.jar, 步exclude/replacements 空, 空=结束链, y=确认写入
        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "1", "", "", "", "", "y", "2", "*.jar", "", "", "", "y"));
        wizard.start();

        Path ruleFile = updateDir.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "应生成 matching-rules.json");
        String json = Files.readString(ruleFile, StandardCharsets.UTF_8);

        // strategyType 必须是注册表标识，不能是"类型（说明）"显示名
        assertTrue(json.contains("\"strategyType\" : \"FileSameName\""), "主策略应为 FileSameName: " + json);
        assertFalse(json.contains("（"), "生成内容不应含显示名括号: " + json);

        // 主策略不应被复制进链（FileSameName 只出现一次）
        assertEquals(json.indexOf("FileSameName"), json.lastIndexOf("FileSameName"),
                "主策略不应复制进链: " + json);

        // 反解析校验（此前显示名 bug 会导致此处解析失败返回 null）
        MatchRule loaded = MatchRuleLoader.fromJson(json);
        assertNotNull(loaded, "生成的规则应能被解析");
        assertEquals("FileSameName", loaded.getStrategyType());
        assertEquals(1, loaded.getStrategyChain().size(), "链只存后续步骤，不含主策略");
        assertEquals("McMod", loaded.getStrategyChain().get(0).getStrategyType());
        assertEquals(java.util.List.of("*.jar"), loaded.getStrategyChain().get(0).getPatterns());

        // 链步骤内不应出现 inheritToSubfolders（扁平结构，只属于顶层）
        assertEquals(json.indexOf("inheritToSubfolders"), json.lastIndexOf("inheritToSubfolders"),
                "链步骤内不应携带 inheritToSubfolders: " + json);
    }

    // ---------------- 辅助 ----------------

    private Config snapshot(Config c) {
        Config s = new Config();
        s.setBaseDirectory(c.getBaseDirectory());
        s.setUpdatePath(c.getUpdatePath());
        s.setTargetPath(c.getTargetPath());
        s.setDeletePath(c.getDeletePath());
        s.setBackupPath(c.getBackupPath());
        s.setLogLevel(c.getLogLevel());
        return s;
    }

    /** 按顺序返回预设答案的脚本化 prompter；答案耗尽后返回空串（等价回车） */
    private UserPrompter scriptedPrompter(String... answers) {
        Deque<String> queue = new ArrayDeque<>(Arrays.asList(answers));
        return () -> queue.isEmpty() ? "" : queue.pollFirst();
    }
}
