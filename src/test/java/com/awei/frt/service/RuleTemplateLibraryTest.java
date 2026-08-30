package com.awei.frt.service;

import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.RuleTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则模板库测试（FR-2，AC-5/AC-8）：
 * - classpath 真实模板（rule-templates.json）加载：7 个、id 唯一、字段齐全
 * - 每条模板的 rule 经 MatchRuleLoader.fromJson 解析非 null（strategyType/链步骤已注册）
 * - 模板字段语义与需求 §4.2 清单一致（T1~T7 关键字段逐项断言）
 * - 容错：文件缺失/JSON 损坏/含无效项 → 空列表或跳过无效项，不抛异常（经包内 loadFrom 钩子）
 * - findById 命中/未命中；copy() 深拷贝不共享可变引用
 */
class RuleTemplateLibraryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    // ---------------- AC-5.2/5.3 加载与校验 ----------------

    @Test
    void loadAllReturnsSevenValidTemplates() {
        List<RuleTemplate> templates = RuleTemplateLibrary.loadAll();
        assertEquals(7, templates.size(), "内置模板应为 7 个");
        Set<String> ids = new HashSet<>();
        for (RuleTemplate t : templates) {
            assertNotNull(t.getId(), "模板 id 不能为空");
            assertTrue(ids.add(t.getId()), "模板 id 必须唯一: " + t.getId());
            assertNotNull(t.getName(), "模板 name 不能为空");
            assertNotNull(t.getCategory(), "模板 category 不能为空");
            assertNotNull(t.getDescription(), "模板 description 不能为空");
        }
    }

    @Test
    void everyTemplateRuleParsesViaMatchRuleLoader() throws Exception {
        // AC-5.3：每条模板的 rule 必须能被 MatchRuleLoader.fromJson 解析（策略已注册、链步骤已注册）
        for (RuleTemplate t : RuleTemplateLibrary.loadAll()) {
            String ruleJson = MAPPER.writeValueAsString(t.getRule());
            assertNotNull(MatchRuleLoader.fromJson(ruleJson), "模板 " + t.getId() + " 的 rule 应可被程序正常加载");
        }
    }

    // ---------------- AC-8 模板内容正确性（与需求 §4.2 清单逐项核对） ----------------

    @Test
    void t1McModUpdateUsesVersionChanged() {
        MatchRule rule = template("mc-mod-update").getRule();
        assertEquals("McMod", rule.getStrategyType());
        assertEquals("true", rule.getReplacements().get("onlyIfVersionChanged"));
        assertTrue(rule.getPatterns().isEmpty(), "空白名单=匹配所有");
        assertTrue(rule.getStrategyChain().isEmpty());
        assertFalse(rule.isInheritToSubfolders());
    }

    @Test
    void t2McModUpdateContentUsesContentSame() {
        MatchRule rule = template("mc-mod-update-content").getRule();
        assertEquals("McMod", rule.getStrategyType());
        assertEquals("true", rule.getReplacements().get("onlyIfContentSame"));
    }

    @Test
    void t3McModPlusFilesHasChainStep() {
        RuleTemplate t = template("mc-mod-plus-files");
        MatchRule rule = t.getRule();
        assertEquals("McMod", rule.getStrategyType());
        assertEquals("true", rule.getReplacements().get("onlyIfVersionChanged"));
        assertEquals(1, rule.getStrategyChain().size(), "组合更新模板应含 1 个链步骤");
        assertEquals("FileSameName", rule.getStrategyChain().get(0).getStrategyType());
        assertEquals(List.of("*.bak", "*.tmp", "*~"), rule.getStrategyChain().get(0).getExcludePatterns());
    }

    @Test
    void t4ResourcepackSyncUsesZipEntryContent() {
        MatchRule rule = template("mc-resourcepack-sync").getRule();
        assertEquals("ZipEntryContent", rule.getStrategyType());
        assertEquals("pack_format", rule.getReplacements().get("contentContains"));
    }

    @Test
    void t5ConfigSyncUsesConfigExtensions() {
        MatchRule rule = template("config-sync").getRule();
        assertEquals("FileSameName", rule.getStrategyType());
        assertTrue(rule.getPatterns().contains("*.properties"));
        assertTrue(rule.getPatterns().contains("*.json"));
        assertTrue(rule.getPatterns().contains("*.toml"));
        assertTrue(rule.getExcludePatterns().contains("*.bak"));
        assertTrue(rule.getExcludePatterns().contains("*.old"));
    }

    @Test
    void t6AndT7GenericSyncSemantics() {
        MatchRule all = template("file-sync-all").getRule();
        assertEquals("FileSameName", all.getStrategyType());
        assertTrue(all.getPatterns().isEmpty(), "空白名单=全量匹配");
        assertFalse(all.getReplacements().containsKey("onlyIfContentSame"));

        MatchRule content = template("file-sync-content").getRule();
        assertEquals("FileSameName", content.getStrategyType());
        assertEquals("true", content.getReplacements().get("onlyIfContentSame"));
    }

    // ---------------- AC-5.4 容错：缺失/损坏/无效项 ----------------

    @Test
    void loadFromMissingFileReturnsEmpty() {
        assertTrue(RuleTemplateLibrary.loadFrom(tempDir.resolve("nope.json")).isEmpty(),
                "文件缺失 → 空列表，不抛异常");
    }

    @Test
    void loadFromBrokenJsonReturnsEmpty() throws Exception {
        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, "{ 这不是合法 JSON ");
        assertTrue(RuleTemplateLibrary.loadFrom(bad).isEmpty(), "JSON 损坏 → 空列表，不抛异常");
    }

    @Test
    void loadFromMissingTemplatesArrayReturnsEmpty() throws Exception {
        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, "{\"other\": []}");
        assertTrue(RuleTemplateLibrary.loadFrom(bad).isEmpty(), "缺 templates 数组 → 空列表");
    }

    @Test
    void loadFromSkipsInvalidTemplateEntry() throws Exception {
        // 含一个有效模板 + 一个 rule 策略未注册的无效模板：无效项跳过，有效项照常返回
        String json = """
                {
                  "templates": [
                    {
                      "id": "good",
                      "name": "有效模板",
                      "category": "测试",
                      "description": "d",
                      "rule": { "strategyType": "FileSameName", "replacements": {}, "patterns": [],
                                "excludePatterns": [], "strategyChain": [], "inheritToSubfolders": false }
                    },
                    {
                      "id": "bad",
                      "name": "无效模板",
                      "category": "测试",
                      "description": "d",
                      "rule": { "strategyType": "NoSuchStrategy", "replacements": {}, "patterns": [],
                                "excludePatterns": [], "strategyChain": [], "inheritToSubfolders": false }
                    }
                  ]
                }
                """;
        Path file = tempDir.resolve("templates.json");
        Files.writeString(file, json);
        List<RuleTemplate> result = RuleTemplateLibrary.loadFrom(file);
        assertEquals(1, result.size(), "无效策略模板应被跳过");
        assertEquals("good", result.get(0).getId());
    }

    @Test
    void loadFromSkipsEntryWithoutRule() throws Exception {
        String json = """
                {
                  "templates": [
                    { "id": "no-rule", "name": "无规则模板", "category": "测试", "description": "d" }
                  ]
                }
                """;
        Path file = tempDir.resolve("templates.json");
        Files.writeString(file, json);
        assertTrue(RuleTemplateLibrary.loadFrom(file).isEmpty(), "缺 rule 字段的模板项应被跳过");
    }

    // ---------------- findById / copy 深拷贝 ----------------

    @Test
    void findByIdHitsAndMisses() {
        assertNotNull(RuleTemplateLibrary.findById("mc-mod-update"));
        assertEquals("mc-mod-update", RuleTemplateLibrary.findById("mc-mod-update").getId());
        assertNull(RuleTemplateLibrary.findById("no-such-template"));
        assertNull(RuleTemplateLibrary.findById(null));
        assertNull(RuleTemplateLibrary.findById("  "));
    }

    @Test
    void copyIsDeepAndDoesNotShareRule() {
        RuleTemplate original = RuleTemplateLibrary.findById("mc-mod-plus-files");
        RuleTemplate copy = original.copy();
        assertNotSame(original, copy);
        assertNotSame(original.getRule(), copy.getRule(), "copy 的 rule 不应与原件共享引用");
        // 修改副本的 rule 不影响原件（策略链/列表/Map 均深拷贝）
        copy.getRule().getStrategyChain().get(0).getExcludePatterns().add("*.zzz");
        copy.getRule().getReplacements().put("extra", "1");
        assertFalse(original.getRule().getStrategyChain().get(0).getExcludePatterns().contains("*.zzz"),
                "副本链步骤列表修改不应泄漏到原件");
        assertFalse(original.getRule().getReplacements().containsKey("extra"),
                "副本 replacements 修改不应泄漏到原件");
        assertEquals("mc-mod-plus-files", copy.getId());
        assertEquals(original.getName(), copy.getName());
    }

    // ---------------- 辅助 ----------------

    private RuleTemplate template(String id) {
        RuleTemplate t = RuleTemplateLibrary.findById(id);
        assertNotNull(t, "模板应存在: " + id);
        return t;
    }
}
