package com.awei.frt.core.builder;

import com.awei.frt.model.Config;
import com.awei.frt.model.RuleTemplate;
import com.awei.frt.service.RuleTemplateLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批次验收聚焦测试（类名与契约定向测试模式 *Template*UpdateCheck* 匹配）：
 * ①模板库：内置 rule-templates.json 可解析、全部模板 rule 经 MatchRuleLoader 校验合法（策略已注册）；
 * ②启动自动检查更新：开关默认开启、saveAutoCheckUpdateTo 合并持久化可读回（保留其他键）。
 * 细化用例见 RuleTemplateLibraryTest / AutoCheckUpdateSaveTest / FRTFrameAutoCheckTest。
 */
class RuleTemplateUpdateCheckTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void templateJsonParsesAndAllRulesAreValid() throws Exception {
        List<RuleTemplate> templates = RuleTemplateLibrary.loadAll();
        assertEquals(7, templates.size(), "内置模板应为 7 个");
        for (RuleTemplate t : templates) {
            String ruleJson = MAPPER.writeValueAsString(t.getRule());
            assertNotNull(MatchRuleLoader.fromJson(ruleJson),
                    "模板 " + t.getId() + " 的规则 JSON 必须可被程序正常加载");
        }
    }

    @Test
    void autoCheckUpdateSwitchDefaultsOnAndPersists() throws Exception {
        Config config = new Config();
        assertTrue(config.isAutoCheckUpdate(), "启动自动检查更新开关应默认开启（开箱即用）");

        // 合并持久化：写入 false，保留其他键，可被 Config 读回
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{\"logLevel\":\"INFO\"}");
        ConfigLoader.saveAutoCheckUpdateTo(configFile, false);

        Config loaded = MAPPER.readValue(Files.readString(configFile), Config.class);
        assertFalse(loaded.isAutoCheckUpdate(), "写入 false 后应能读回");
        assertEquals("INFO", loaded.getLogLevel(), "合并写入应保留其他键");
    }
}
