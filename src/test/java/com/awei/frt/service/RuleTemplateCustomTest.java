package com.awei.frt.service;

import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.RuleTemplate;
import com.awei.frt.model.StrategyStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义模板库测试（FR-1，AC-2/3/4/5）：
 * - 存储位置两级解析（包内钩子注入 @TempDir，不写真实工作目录）
 * - 保存：校验拒绝（INVALID_RULE）/ 写文件结构与内置同构 / 重名与覆盖（保持原 id）/ 内置重名拒绝
 * - 加载合并：loadAllCustom / loadAllMerged 顺序与数量 / findById 扩展（内置优先、自定义兜底）
 * - 删除/改名：自定义可删可改、内置 id 拒绝、不存在 id 返回 false
 * - 回退：主位置不可写 → 回退兜底位置；两级均失败 → IO_ERROR 不抛异常
 * - 审查 round 2：主位置存在但只读 → 加载/保存/删除/改名均走兜底且先前兜底模板不丢失；
 *   id 冲突 → 自动重新生成 id，不静默覆盖
 * - 容错：坏文件空集不崩溃、保存按空集重建、文件内重复 id 保留第一条
 */
class RuleTemplateCustomTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @AfterEach
    void clearOverride() {
        // 恢复生产路径解析与可写性探测，避免静态覆盖泄漏影响其他测试
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(null);
        RuleTemplateLibrary.setCustomTemplatesLocationForTesting(null, null);
        RuleTemplateLibrary.setCustomTemplatesWritableProbeForTesting(null);
    }

    // ---------------- AC-2 存储位置解析 ----------------

    @Test
    void resolveDefaultsToPrimaryUnderWorkingDir() {
        Path resolved = RuleTemplateLibrary.resolveCustomTemplatesFile(tempDir, tempDir);
        assertEquals(tempDir.resolve("templates").resolve("user-templates.json"), resolved,
                "两级均不存在 → 默认主位置 <工作目录>/templates/user-templates.json");
    }

    @Test
    void resolvePrefersPrimaryWhenPrimaryExists() throws Exception {
        Path primary = tempDir.resolve("templates").resolve("user-templates.json");
        Files.createDirectories(primary.getParent());
        Files.writeString(primary, "{\"templates\":[]}");
        assertEquals(primary, RuleTemplateLibrary.resolveCustomTemplatesFile(tempDir, tempDir));
    }

    @Test
    void resolvePrefersCreatablePrimaryOverExistingFallback() throws Exception {
        // 主位置文件不存在但目录可创建（正常可写场景）→ 仍选主位置（兜底仅在主位置不可用时使用）
        Path fallback = tempDir.resolve("home").resolve(".frt").resolve("templates").resolve("user-templates.json");
        Files.createDirectories(fallback.getParent());
        Files.writeString(fallback, "{\"templates\":[]}");
        assertEquals(tempDir.resolve("templates").resolve("user-templates.json"),
                RuleTemplateLibrary.resolveCustomTemplatesFile(tempDir, tempDir.resolve("home")));
    }

    @Test
    void resolveFallsBackWhenPrimaryUncreatable() throws Exception {
        // 主位置目录被普通文件占住（不可创建）→ 回退兜底位置
        Path work = tempDir.resolve("work");
        Files.createDirectories(work);
        Files.writeString(work.resolve("templates"), "占位文件"); // 主位置 templates/ 不可创建
        Path fallback = tempDir.resolve("home").resolve(".frt").resolve("templates").resolve("user-templates.json");
        Files.createDirectories(fallback.getParent());
        Files.writeString(fallback, "{\"templates\":[]}");
        assertEquals(fallback, RuleTemplateLibrary.resolveCustomTemplatesFile(work, tempDir.resolve("home")));
    }

    /**
     * 审查 round 2 修复项验收：主位置文件存在但只读 → 加载/保存/删除/改名均走兜底位置，
     * 且先前兜底模板不丢失（读侧与写侧一致）。
     * root 运行环境下 chmod 对 Files.isWritable 无效，故注入可写性探测模拟"主位置只读"。
     */
    @Test
    void readOnlyPrimaryRoutesLoadSaveDeleteRenameToFallbackKeepingPriorTemplates() throws Exception {
        Path work = tempDir.resolve("work");
        Path home = tempDir.resolve("home");
        Files.createDirectories(work.resolve("templates"));
        Path primary = work.resolve("templates").resolve("user-templates.json");
        Files.writeString(primary, "{\"templates\":[]}"); // 主位置文件存在
        Path fallback = home.resolve(".frt").resolve("templates").resolve("user-templates.json");
        Files.createDirectories(fallback.getParent());
        // 兜底位置预置模板（先前兜底数据）
        RuleTemplateLibrary.saveCustomTo(fallback, customTemplate("custom-fb1", "兜底方案一"), false);
        RuleTemplateLibrary.saveCustomTo(fallback, customTemplate("custom-fb2", "兜底方案二"), false);

        // 注入：两级定位指向临时目录 + 主位置文件/目录不可写（其余可写）
        RuleTemplateLibrary.setCustomTemplatesLocationForTesting(work, home);
        RuleTemplateLibrary.setCustomTemplatesWritableProbeForTesting(p ->
                !(p.equals(primary) || p.equals(primary.getParent())));
        try {
            // 读侧：生产入口解析与加载均走兜底位置
            assertEquals(fallback, RuleTemplateLibrary.getCustomTemplatesFile(),
                    "主位置存在但只读 → 读侧解析应回退兜底位置");
            List<RuleTemplate> initial = RuleTemplateLibrary.loadAllCustom();
            assertEquals(2, initial.size(), "加载应读兜底位置的先前模板");
            assertTrue(initial.stream().anyMatch(t -> t.getId().equals("custom-fb1")));

            // 保存（生产入口）：与兜底现有列表合并追加，先前兜底模板不丢失
            RuleTemplate added = customTemplate(RuleTemplateLibrary.generateCustomTemplateId(), "新增方案");
            assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveTemplate(added, false));
            List<RuleTemplate> afterSave = RuleTemplateLibrary.loadAllCustom();
            assertEquals(3, afterSave.size(), "保存应追加到兜底且不丢先前模板");
            assertTrue(afterSave.stream().anyMatch(t -> t.getId().equals("custom-fb1")));
            assertTrue(afterSave.stream().anyMatch(t -> t.getId().equals("custom-fb2")));
            assertTrue(afterSave.stream().anyMatch(t -> t.getName().equals("新增方案")));
            assertFalse(Files.readString(primary).contains("新增方案"), "主位置不应被写入");

            // 删除（生产入口）：走兜底位置
            assertTrue(RuleTemplateLibrary.deleteTemplate("custom-fb1"));
            List<RuleTemplate> afterDelete = RuleTemplateLibrary.loadAllCustom();
            assertEquals(2, afterDelete.size(), "删除应作用于兜底位置");
            assertFalse(afterDelete.stream().anyMatch(t -> t.getId().equals("custom-fb1")));
            assertTrue(afterDelete.stream().anyMatch(t -> t.getId().equals("custom-fb2")));

            // 改名（生产入口）：走兜底位置
            assertTrue(RuleTemplateLibrary.renameTemplate("custom-fb2", "兜底方案二改"));
            List<RuleTemplate> afterRename = RuleTemplateLibrary.loadAllCustom();
            assertTrue(afterRename.stream().anyMatch(t -> t.getName().equals("兜底方案二改")));
            assertFalse(Files.readString(primary).contains("兜底方案二改"), "改名不应作用于主位置");
        } finally {
            RuleTemplateLibrary.setCustomTemplatesWritableProbeForTesting(null);
            RuleTemplateLibrary.setCustomTemplatesLocationForTesting(null, null);
        }
    }

    // ---------------- AC-3 保存 ----------------

    @Test
    void saveThenLoadRoundTripsRuleWithChainEmpty() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplate t = customTemplate("custom-1", "我的方案");
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveCustomTo(file, t, false));
        assertTrue(Files.exists(file), "保存成功后文件应存在");

        // 顶层结构与内置 rule-templates.json 同构：{"templates":[...]}
        JsonNode root = MAPPER.readTree(Files.readString(file));
        assertTrue(root.has("templates") && root.get("templates").isArray());
        assertEquals(1, root.get("templates").size());
        // rule 与保存前一致（strategyChain 空数组显式保留，无字段漂移）
        JsonNode ruleNode = root.get("templates").get(0).get("rule");
        assertTrue(ruleNode.has("strategyChain") && ruleNode.get("strategyChain").isArray());
        assertEquals("FileSameName", ruleNode.get("strategyType").asText());

        // 读回字段一致，且 rule 可被 MatchRuleLoader 解析
        List<RuleTemplate> loaded = RuleTemplateLibrary.loadCustomFrom(file);
        assertEquals(1, loaded.size());
        RuleTemplate rt = loaded.get(0);
        assertEquals("custom-1", rt.getId());
        assertEquals("我的方案", rt.getName());
        assertEquals("自定义", rt.getCategory());
        assertEquals("测试模板", rt.getDescription());
        assertEquals("FileSameName", rt.getRule().getStrategyType());
        assertTrue(rt.getRule().getStrategyChain().isEmpty());
        assertNotNull(MatchRuleLoader.fromJson(MAPPER.writeValueAsString(rt.getRule())));
    }

    @Test
    void saveRejectsInvalidRuleWithoutWriting() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplate bad = customTemplate("custom-bad", "非法方案");
        MatchRule rule = validRule();
        rule.setStrategyType("NoSuchStrategy"); // 未注册策略类型
        bad.setRule(rule);
        assertEquals(RuleTemplateLibrary.SaveStatus.INVALID_RULE,
                RuleTemplateLibrary.saveCustomTo(file, bad, false));
        assertFalse(Files.exists(file), "校验失败不得写文件");
    }

    @Test
    void saveRejectsUnregisteredChainStep() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplate bad = customTemplate("custom-chain", "链方案");
        MatchRule rule = validRule();
        StrategyStep step = new StrategyStep();
        step.setStrategyType("NoSuchChainStrategy"); // 链步骤未注册
        step.setPatterns(new ArrayList<>());
        step.setExcludePatterns(new ArrayList<>());
        step.setReplacements(new LinkedHashMap<>());
        rule.setStrategyChain(List.of(step));
        bad.setRule(rule);
        assertEquals(RuleTemplateLibrary.SaveStatus.INVALID_RULE,
                RuleTemplateLibrary.saveCustomTo(file, bad, false));
        assertFalse(Files.exists(file), "链步骤未注册同样拒绝保存");
    }

    @Test
    void saveRejectsBlankNameAndId() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplate noName = customTemplate("custom-1", "  ");
        assertEquals(RuleTemplateLibrary.SaveStatus.INVALID_RULE,
                RuleTemplateLibrary.saveCustomTo(file, noName, false));
        RuleTemplate noId = customTemplate(null, "有效名");
        assertEquals(RuleTemplateLibrary.SaveStatus.INVALID_RULE,
                RuleTemplateLibrary.saveCustomTo(file, noId, false));
        RuleTemplate noRule = customTemplate("custom-2", "有效名");
        noRule.setRule(null);
        assertEquals(RuleTemplateLibrary.SaveStatus.INVALID_RULE,
                RuleTemplateLibrary.saveCustomTo(file, noRule, false));
        assertFalse(Files.exists(file));
    }

    @Test
    void duplicateNameReturnsStatusAndOverwriteKeepsOriginalId() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplate first = customTemplate("custom-1", "同名方案");
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveCustomTo(file, first, false));
        RuleTemplate second = customTemplate("custom-2", "同名方案");
        second.setDescription("新描述");
        // overwrite=false → DUPLICATE_NAME，不写
        assertEquals(RuleTemplateLibrary.SaveStatus.DUPLICATE_NAME,
                RuleTemplateLibrary.saveCustomTo(file, second, false));
        List<RuleTemplate> after = RuleTemplateLibrary.loadCustomFrom(file);
        assertEquals(1, after.size());
        assertEquals("custom-1", after.get(0).getId());
        // overwrite=true → 更新全部字段但保持原 id（引用不失效）
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS,
                RuleTemplateLibrary.saveCustomTo(file, second, true));
        after = RuleTemplateLibrary.loadCustomFrom(file);
        assertEquals(1, after.size());
        assertEquals("custom-1", after.get(0).getId(), "覆盖必须保持原 id");
        assertEquals("新描述", after.get(0).getDescription());
        assertEquals("同名方案", after.get(0).getName());
    }

    @Test
    void saveWithCollidingIdRegeneratesInsteadOfOverwriting() throws Exception {
        // 审查 round 2 修复项（§9.5）：传入 id 已存在于现有列表 → 自动重新生成 id 追加，
        // 绝不静默覆盖已有模板（"同 id 更新字段"语义已移除）
        Path file = userTemplatesFile();
        RuleTemplate t1 = customTemplate("custom-x", "方案A");
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveCustomTo(file, t1, false));
        RuleTemplate t2 = customTemplate("custom-x", "方案B"); // 同 id 冲突
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveCustomTo(file, t2, false));
        List<RuleTemplate> list = RuleTemplateLibrary.loadCustomFrom(file);
        assertEquals(2, list.size(), "id 冲突应重新生成 id 追加，不覆盖已有模板");
        assertEquals("custom-x", list.get(0).getId());
        assertEquals("方案A", list.get(0).getName(), "原模板字段不被覆盖");
        assertNotEquals("custom-x", list.get(1).getId(), "冲突后应使用新 id");
        assertTrue(list.get(1).getId().startsWith("custom-"), "新 id 保持 custom- 前缀");
        assertEquals("方案B", list.get(1).getName());
    }

    @Test
    void saveRejectsBuiltinName() throws Exception {
        Path file = userTemplatesFile();
        // 内置模板名：Minecraft 模组更新（按版本跳过）
        RuleTemplate t = customTemplate("custom-bn", "Minecraft 模组更新（按版本跳过）");
        assertEquals(RuleTemplateLibrary.SaveStatus.BUILTIN_NAME_CONFLICT,
                RuleTemplateLibrary.saveCustomTo(file, t, false));
        assertFalse(Files.exists(file), "内置重名拒绝，不写文件");
    }

    @Test
    void saveTemplateViaOverrideGeneratesUniqueCustomIds() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        RuleTemplate t1 = customTemplate(RuleTemplateLibrary.generateCustomTemplateId(), "方案一");
        RuleTemplate t2 = customTemplate(RuleTemplateLibrary.generateCustomTemplateId(), "方案二");
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveTemplate(t1, false));
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, RuleTemplateLibrary.saveTemplate(t2, false));
        List<RuleTemplate> list = RuleTemplateLibrary.loadAllCustom();
        assertEquals(2, list.size());
        assertNotEquals(t1.getId(), t2.getId(), "多次保存 id 不冲突");
        for (RuleTemplate t : list) {
            assertTrue(t.getId().startsWith("custom-"), "id 必须以 custom- 前缀: " + t.getId());
        }
        // 不与内置 id 冲突
        for (RuleTemplate builtin : RuleTemplateLibrary.loadAll()) {
            assertFalse(list.stream().anyMatch(c -> c.getId().equals(builtin.getId())));
        }
    }

    @Test
    void generateCustomTemplateIdIsUniqueAcrossCalls() {
        String a = RuleTemplateLibrary.generateCustomTemplateId();
        String b = RuleTemplateLibrary.generateCustomTemplateId();
        assertTrue(a.startsWith("custom-"));
        assertNotEquals(a, b);
    }

    // ---------------- AC-4 加载与合并 ----------------

    @Test
    void loadAllMergedIsBuiltinsThenCustoms() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS,
                RuleTemplateLibrary.saveTemplate(customTemplate("custom-1", "方案一"), false));
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS,
                RuleTemplateLibrary.saveTemplate(customTemplate("custom-2", "方案二"), false));
        List<RuleTemplate> merged = RuleTemplateLibrary.loadAllMerged();
        List<RuleTemplate> builtins = RuleTemplateLibrary.loadAll();
        assertEquals(builtins.size() + 2, merged.size(), "合并 = 内置 + 自定义");
        // 内置在前、自定义在后，顺序正确
        for (int i = 0; i < builtins.size(); i++) {
            assertEquals(builtins.get(i).getId(), merged.get(i).getId());
        }
        assertEquals("custom-1", merged.get(builtins.size()).getId());
        assertEquals("custom-2", merged.get(builtins.size() + 1).getId());
        // 无重复 id
        assertEquals(merged.size(), merged.stream().map(RuleTemplate::getId).distinct().count());
    }

    @Test
    void findByIdPrefersBuiltinThenCustom() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        RuleTemplateLibrary.saveTemplate(customTemplate("custom-1", "方案一"), false);
        // 内置优先仍命中
        assertNotNull(RuleTemplateLibrary.findById("mc-mod-update"));
        assertEquals("mc-mod-update", RuleTemplateLibrary.findById("mc-mod-update").getId());
        // 自定义 id 命中自定义
        RuleTemplate custom = RuleTemplateLibrary.findById("custom-1");
        assertNotNull(custom);
        assertEquals("方案一", custom.getName());
        // 未命中仍返回 null
        assertNull(RuleTemplateLibrary.findById("no-such-template"));
        assertNull(RuleTemplateLibrary.findById(null));
    }

    @Test
    void loadCustomFromMissingFileReturnsEmpty() {
        assertTrue(RuleTemplateLibrary.loadCustomFrom(tempDir.resolve("nope.json")).isEmpty(),
                "文件缺失 → 空列表，不抛异常");
    }

    @Test
    void corruptedCustomFileLoadsEmptyAndSaveRebuilds() throws Exception {
        Path file = userTemplatesFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ 这不是合法 JSON ");
        assertTrue(RuleTemplateLibrary.loadCustomFrom(file).isEmpty(), "坏文件 → 空集，不抛异常");
        // 保存按空集重建：坏文件不阻塞后续保存
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS,
                RuleTemplateLibrary.saveTemplate(customTemplate("custom-r", "重建方案"), false));
        List<RuleTemplate> list = RuleTemplateLibrary.loadAllCustom();
        assertEquals(1, list.size());
        assertEquals("重建方案", list.get(0).getName());
    }

    @Test
    void duplicateIdInCustomFileKeepsFirst() throws Exception {
        Path file = userTemplatesFile();
        Files.createDirectories(file.getParent());
        String json = """
                {
                  "templates": [
                    { "id": "custom-d", "name": "第一条", "category": "自定义", "description": "d",
                      "rule": { "strategyType": "FileSameName", "replacements": {}, "patterns": [],
                                "excludePatterns": [], "strategyChain": [], "inheritToSubfolders": false } },
                    { "id": "custom-d", "name": "第二条", "category": "自定义", "description": "d",
                      "rule": { "strategyType": "FileSameName", "replacements": {}, "patterns": [],
                                "excludePatterns": [], "strategyChain": [], "inheritToSubfolders": false } }
                  ]
                }
                """;
        Files.writeString(file, json);
        List<RuleTemplate> list = RuleTemplateLibrary.loadCustomFrom(file);
        assertEquals(1, list.size(), "重复 id 只保留第一条");
        assertEquals("第一条", list.get(0).getName());
    }

    // ---------------- AC-5 删除/改名与内置只读 ----------------

    @Test
    void deleteCustomTemplateRemovesAndWritesBack() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        RuleTemplateLibrary.saveTemplate(customTemplate("custom-1", "方案一"), false);
        RuleTemplateLibrary.saveTemplate(customTemplate("custom-2", "方案二"), false);
        assertTrue(RuleTemplateLibrary.deleteTemplate("custom-1"));
        List<RuleTemplate> list = RuleTemplateLibrary.loadAllCustom();
        assertEquals(1, list.size());
        assertEquals("custom-2", list.get(0).getId());
        assertFalse(Files.readString(file).contains("custom-1"), "删除后文件不再含该 id");
    }

    @Test
    void deleteBuiltinOrUnknownIdRejected() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        RuleTemplateLibrary.saveTemplate(customTemplate("custom-1", "方案一"), false);
        assertFalse(RuleTemplateLibrary.deleteTemplate("mc-mod-update"), "内置 id 拒绝删除");
        assertFalse(RuleTemplateLibrary.deleteTemplate("no-such-id"), "不存在 id 返回 false");
        List<RuleTemplate> list = RuleTemplateLibrary.loadAllCustom();
        assertEquals(1, list.size(), "拒绝删除时文件不变");
    }

    @Test
    void deleteCustomFromHookRejectsBuiltinId() throws Exception {
        Path file = userTemplatesFile();
        Files.createDirectories(file.getParent());
        String original = "{\"templates\":[]}";
        Files.writeString(file, original);
        assertFalse(RuleTemplateLibrary.deleteCustomFrom(file, "mc-mod-update"),
                "钩子层对内置 id 同样拒绝");
        assertEquals(original, Files.readString(file), "内置删除被拒后文件不变");
    }

    @Test
    void renameUpdatesNameKeepsId() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        RuleTemplateLibrary.saveTemplate(customTemplate("custom-1", "方案一"), false);
        assertTrue(RuleTemplateLibrary.renameTemplate("custom-1", "方案一改"));
        List<RuleTemplate> list = RuleTemplateLibrary.loadAllCustom();
        assertEquals(1, list.size());
        assertEquals("custom-1", list.get(0).getId(), "改名不换 id");
        assertEquals("方案一改", list.get(0).getName());
    }

    @Test
    void renameRejectsConflictsAndUnknown() throws Exception {
        Path file = userTemplatesFile();
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(file);
        RuleTemplateLibrary.saveTemplate(customTemplate("custom-1", "方案一"), false);
        RuleTemplateLibrary.saveTemplate(customTemplate("custom-2", "方案二"), false);
        // 改为内置名 → 拒绝
        assertFalse(RuleTemplateLibrary.renameTemplate("custom-1", "Minecraft 模组更新（按版本跳过）"));
        // 改为其他自定义名 → 拒绝
        assertFalse(RuleTemplateLibrary.renameTemplate("custom-1", "方案二"));
        // 空名 → 拒绝；内置 id → 拒绝；不存在 id → false
        assertFalse(RuleTemplateLibrary.renameTemplate("custom-1", "  "));
        assertFalse(RuleTemplateLibrary.renameTemplate("mc-mod-update", "新名"));
        assertFalse(RuleTemplateLibrary.renameTemplate("no-such", "新名"));
        // 原列表未被改动
        List<RuleTemplate> list = RuleTemplateLibrary.loadAllCustom();
        assertEquals("方案一", list.get(0).getName());
        assertEquals("方案二", list.get(1).getName());
    }

    @Test
    void renameCustomInHookWorksWithoutOverride() throws Exception {
        Path file = userTemplatesFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"templates":[{"id":"custom-h","name":"旧名","category":"自定义","description":"",
                  "rule":{"strategyType":"FileSameName","replacements":{},"patterns":[],
                          "excludePatterns":[],"strategyChain":[],"inheritToSubfolders":false}}]}
                """);
        assertTrue(RuleTemplateLibrary.renameCustomIn(file, "custom-h", "新名"));
        List<RuleTemplate> list = RuleTemplateLibrary.loadCustomFrom(file);
        assertEquals(1, list.size());
        assertEquals("新名", list.get(0).getName());
        assertEquals("custom-h", list.get(0).getId());
    }

    // ---------------- AC-2.3/2.4 两级回退与 IO 失败 ----------------

    @Test
    void saveFallsBackToHomeDirWhenPrimaryUnwritable() throws Exception {
        Path work = tempDir.resolve("work");
        Files.createDirectories(work);
        Files.writeString(work.resolve("templates"), "占位文件"); // 用普通文件占住 templates 路径
        Path home = tempDir.resolve("home");
        Files.createDirectories(home);
        RuleTemplateLibrary.SaveStatus status = RuleTemplateLibrary.saveCustomTo(work, home,
                List.of(customTemplate("custom-fb", "回退方案")));
        assertEquals(RuleTemplateLibrary.SaveStatus.SUCCESS, status);
        Path fallback = home.resolve(".frt").resolve("templates").resolve("user-templates.json");
        assertTrue(Files.exists(fallback), "主位置不可写时应回退用户主目录兜底位置");
        List<RuleTemplate> list = RuleTemplateLibrary.loadCustomFrom(fallback);
        assertEquals(1, list.size());
        assertEquals("回退方案", list.get(0).getName());
    }

    @Test
    void saveReturnsIoErrorWhenBothLocationsUnwritable() throws Exception {
        Path work = tempDir.resolve("work");
        Files.createDirectories(work);
        Files.writeString(work.resolve("templates"), "占位文件");
        Path home = tempDir.resolve("home");
        Files.createDirectories(home);
        Files.writeString(home.resolve(".frt"), "占位文件"); // 兜底位置也被文件占住
        RuleTemplateLibrary.SaveStatus status = RuleTemplateLibrary.saveCustomTo(work, home,
                List.of(customTemplate("custom-io", "失败方案")));
        assertEquals(RuleTemplateLibrary.SaveStatus.IO_ERROR, status, "两级均失败 → IO_ERROR 不抛异常");
    }

    @Test
    void saveTemplateIoErrorDoesNotThrow() throws Exception {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "占位文件"); // 用文件占住目录路径
        RuleTemplateLibrary.setCustomTemplatesFileForTesting(
                blocked.resolve("templates").resolve("user-templates.json"));
        RuleTemplateLibrary.SaveStatus status = RuleTemplateLibrary.saveTemplate(
                customTemplate("custom-io2", "IO方案"), false);
        assertEquals(RuleTemplateLibrary.SaveStatus.IO_ERROR, status);
    }

    // ---------------- 辅助 ----------------

    private Path userTemplatesFile() {
        return tempDir.resolve("templates").resolve("user-templates.json");
    }

    private RuleTemplate customTemplate(String id, String name) {
        RuleTemplate t = new RuleTemplate();
        t.setId(id);
        t.setName(name);
        t.setCategory("自定义");
        t.setDescription("测试模板");
        t.setRule(validRule());
        return t;
    }

    private MatchRule validRule() {
        MatchRule rule = new MatchRule();
        rule.setStrategyType("FileSameName");
        rule.setPatterns(List.of("*.txt"));
        rule.setExcludePatterns(List.of("*.bak"));
        rule.setInheritToSubfolders(false);
        rule.setReplacements(new LinkedHashMap<>());
        rule.setStrategyChain(new ArrayList<>());
        return rule;
    }
}
