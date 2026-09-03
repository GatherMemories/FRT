package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.RuleTemplate;
import com.awei.frt.service.RuleConfigWizard;
import com.awei.frt.service.RuleTemplateLibrary;
import com.awei.frt.interaction.UserPrompter;
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
import java.util.List;

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
        // 恢复静态覆盖的模板文件路径（不泄漏到其他测试/真实路径）
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(null);
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

        // 脚本化输入：1=更新目录, 1=根目录, n=不套用模板（FR-2 新增询问，回车=n 等价）,
        // 1=FileSameName, patterns/exclude/inherit/replacements 均空,
        // y=配置策略链, 2=McMod, 步patterns=*.jar, 步exclude/replacements 空, 空=结束链,
        // 空=不保存为模板（FR-1 新增询问，回车=n 等价）, y=确认写入
        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "n", "1", "", "", "", "", "y", "2", "*.jar", "", "", "", "", "y"));
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

    @Test
    void consoleWizardAppliesTemplateAndSkipsParameterInput() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        // 把配置指向临时目录，隔离全局 Config 单例
        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));

        // 脚本化输入：1=更新目录, 1=根目录, y=套用模板, 1=模板编号(mc-mod-update),
        // 空=不保存为模板（FR-1 新增询问）, y=确认写入
        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter("1", "1", "y", "1", "", "y"));
        wizard.start();

        Path ruleFile = updateDir.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "套用模板后应直接生成 matching-rules.json");
        String json = Files.readString(ruleFile, StandardCharsets.UTF_8);

        // 写入内容与模板 rule 一致（McMod + onlyIfVersionChanged=true，跳过逐参数输入）
        assertTrue(json.contains("\"strategyType\" : \"McMod\""), "主策略应为模板的 McMod: " + json);
        assertTrue(json.contains("onlyIfVersionChanged"), "应含模板的 replacements: " + json);

        MatchRule loaded = MatchRuleLoader.fromJson(json);
        assertNotNull(loaded, "模板套用生成的规则应能被解析");
        assertEquals("McMod", loaded.getStrategyType());
        assertEquals("true", loaded.getReplacements().get("onlyIfVersionChanged"));
        assertTrue(loaded.getStrategyChain().isEmpty(), "单策略模板不应带链步骤");
    }

    @Test
    void consoleWizardInvalidTemplateNumberReasksAndCanCancel() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));

        // 脚本化输入：1=更新目录, 1=根目录, y=套用模板,
        // 99=无效编号（重新询问）, 0=取消回手动流程, 1=FileSameName, 其余参数空,
        // 空=不配置策略链, 空=不保存为模板（FR-1 新增询问）, y=确认写入
        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "y", "99", "0", "1", "", "", "", "", "", "", "y"));
        wizard.start();

        Path ruleFile = updateDir.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "无效编号取消后回手动流程仍应生成规则文件");
        String json = Files.readString(ruleFile, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"strategyType\" : \"FileSameName\""), "应回手动输入生成 FileSameName: " + json);
    }

    // ---------------- FR-1 自定义模板保存（AC-7/AC-8） ----------------

    @Test
    void consoleWizardSavesCustomTemplateAndAppliesItNextRun() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));
        // 隔离自定义模板文件：不写真实工作目录/用户主目录
        Path templatesFile = tempDir.resolve("templates").resolve("user-templates.json");
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(templatesFile);

        // 第一轮：手动输入 FileSameName → y 保存为「我的方案」→ 继续写入规则文件
        // 脚本：1=更新目录, 1=根目录, n=不套用模板, 1=FileSameName, 参数全空, 空=不配置链,
        //       y=保存为模板, 我的方案=名称, y=确认写入
        new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "n", "1", "", "", "", "", "", "y", "我的方案", "y")).start();

        // 自定义模板已落盘（与内置 rule-templates.json 同构）
        assertTrue(Files.exists(templatesFile), "保存的自定义模板文件应存在");
        List<RuleTemplate> customs = RuleTemplateLibrary.loadAllCustom();
        assertEquals(1, customs.size());
        assertEquals("我的方案", customs.get(0).getName());
        assertEquals("FileSameName", customs.get(0).getRule().getStrategyType());

        // 第二轮：套用自定义模板（编号 = 内置 7 + 1 = 8）→ 生成规则文件与保存时一致（AC-8.1/8.2）
        Path updateDir2 = Files.createDirectories(tempDir.resolve("update2"));
        config.setUpdatePath(Path.of("update2"));
        RuleConfigWizard wizard2 = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "y", "8", "", "y"));
        wizard2.start();

        Path ruleFile = updateDir2.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "套用自定义模板应生成规则文件");
        MatchRule loaded = MatchRuleLoader.fromJson(Files.readString(ruleFile, StandardCharsets.UTF_8));
        assertNotNull(loaded, "套用自定义模板生成的规则应可解析");
        assertEquals("FileSameName", loaded.getStrategyType());
        assertEquals(customs.get(0).getRule().getStrategyType(), loaded.getStrategyType());
        assertTrue(loaded.getStrategyChain().isEmpty());
    }

    @Test
    void consoleWizardDuplicateNameAsksOverwrite() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));
        Path templatesFile = tempDir.resolve("templates").resolve("user-templates.json");
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(templatesFile);

        // 预置一个同名自定义模板（经公开保存入口 + override 隔离）
        RuleTemplate existing = new RuleTemplate();
        existing.setId("custom-preset");
        existing.setName("我的方案");
        existing.setCategory("自定义");
        existing.setDescription("");
        existing.setRule(validRule());
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveTemplate(existing, false));

        // 脚本：手动输入 → y 保存 → 名称「我的方案」→ 覆盖询问 y → 确认写入
        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "n", "1", "", "", "", "", "", "y", "我的方案", "y", "y"));
        wizard.start();

        // 覆盖成功：仍只有一条，id 保持原 id（AC-7.4）
        List<RuleTemplate> customs = RuleTemplateLibrary.loadAllCustom();
        assertEquals(1, customs.size());
        assertEquals("custom-preset", customs.get(0).getId(), "覆盖保持原 id");
        Path ruleFile = updateDir.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "覆盖后仍应继续生成规则文件");
    }

    @Test
    void consoleWizardBuiltinNameRejectedAndSaveCancelled() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));
        Path templatesFile = tempDir.resolve("templates").resolve("user-templates.json");
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(templatesFile);

        // 脚本：手动输入 → y 保存 → 输入内置模板名 → [失败] 换名 → 回车取消 → 继续写入（AC-7.4）
        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "n", "1", "", "", "", "", "", "y",
                "Minecraft 模组更新（按版本跳过）", "", "y"));
        wizard.start();

        // 未保存任何自定义模板（内置重名拒绝 + 回车取消）
        assertTrue(RuleTemplateLibrary.loadAllCustom().isEmpty(), "内置重名应拒绝且不写文件");
        Path ruleFile = updateDir.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "取消保存后仍应继续生成规则文件");
    }

    @Test
    void consoleWizardSavesTemplateAfterApplyingBuiltin() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));
        Path templatesFile = tempDir.resolve("templates").resolve("user-templates.json");
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(templatesFile);

        // 脚本：1=更新目录, 1=根目录, y=套用模板, 1=内置模板(mc-mod-update),
        //       y=保存为模板, 套用后方案=名称, y=确认写入（AC-7.3：套用内置模板后同样出现保存询问）
        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "y", "1", "y", "套用后方案", "y"));
        wizard.start();

        // 套用后的规则已保存为自定义模板
        List<RuleTemplate> customs = RuleTemplateLibrary.loadAllCustom();
        assertEquals(1, customs.size());
        assertEquals("套用后方案", customs.get(0).getName());
        assertEquals("McMod", customs.get(0).getRule().getStrategyType(),
                "保存的规则应为套用的内置模板规则");
        Path ruleFile = updateDir.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "保存后仍应继续生成规则文件");
    }

    @Test
    void consoleWizardSaveIoFailureContinuesGeneration() throws IOException {
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));

        Config config = ConfigLoader.getConfig();
        saved = snapshot(config);
        config.setBaseDirectory(tempDir);
        config.setUpdatePath(Path.of("update"));
        // 模板路径被普通文件占住 → 保存 IO 失败（AC-7.6）
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "占位文件");
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(
                blocked.resolve("templates").resolve("user-templates.json"));

        RuleConfigWizard wizard = new RuleConfigWizard(config, scriptedPrompter(
                "1", "1", "n", "1", "", "", "", "", "", "y", "IO方案", "y"));
        wizard.start();

        // 保存失败但规则文件仍生成（不打断向导流程）
        Path ruleFile = updateDir.resolve("matching-rules.json");
        assertTrue(Files.exists(ruleFile), "保存失败不应打断规则生成");
        MatchRule loaded = MatchRuleLoader.fromJson(Files.readString(ruleFile, StandardCharsets.UTF_8));
        assertNotNull(loaded);
        assertEquals("FileSameName", loaded.getStrategyType());
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

    /** 合法规则（FileSameName 单策略），供预置自定义模板用 */
    private MatchRule validRule() {
        MatchRule rule = new MatchRule();
        rule.setStrategyType("FileSameName");
        rule.setPatterns(java.util.List.of("*.txt"));
        rule.setExcludePatterns(java.util.List.of("*.bak"));
        rule.setInheritToSubfolders(false);
        rule.setReplacements(new java.util.LinkedHashMap<>());
        rule.setStrategyChain(new java.util.ArrayList<>());
        return rule;
    }

    /** 按顺序返回预设答案的脚本化 prompter；答案耗尽后返回空串（等价回车） */
    private UserPrompter scriptedPrompter(String... answers) {
        Deque<String> queue = new ArrayDeque<>(Arrays.asList(answers));
        return () -> queue.isEmpty() ? "" : queue.pollFirst();
    }
}
