package com.awei.frt.service;

import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.RuleTemplate;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则模板库（FR-2）
 * <p>
 * 从 classpath 资源 rule-templates.json 加载内置规则模板（Minecraft 模组更新 / 资源包 /
 * 配置文件同步等场景），供 UI 规则生成向导（RuleWizardForm）与控制台向导（RuleConfigWizard）
 * 一键套用。模板为内置只读资源，懒加载后静态缓存；加载/校验失败一律静默回退为空列表，
 * 不抛异常、不崩溃，不影响手动填写流程。
 */
public final class RuleTemplateLibrary {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String RESOURCE = "/rule-templates.json";

    // 静态缓存：模板为内置只读资源，加载一次即可（懒加载，加载发生在向导打开时，非启动路径）
    private static volatile List<RuleTemplate> cached;

    private RuleTemplateLibrary() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 加载全部有效模板（classpath 资源）。
     * 逐条校验：模板 rule 必须能被 MatchRuleLoader.fromJson 解析（strategyType 与链步骤已注册），
     * 无效模板跳过并 logWarn，剩余有效模板照常返回；文件缺失/损坏返回空列表并 logWarn，
     * 不抛异常、不崩溃。
     *
     * @return 有效模板列表（不可修改视图）；加载失败返回空列表
     */
    public static List<RuleTemplate> loadAll() {
        List<RuleTemplate> local = cached;
        if (local == null) {
            synchronized (RuleTemplateLibrary.class) {
                local = cached;
                if (local == null) {
                    local = loadFromClasspath();
                    cached = local;
                }
            }
        }
        return local;
    }

    /**
     * 按模板 id 查找，未命中返回 null
     */
    public static RuleTemplate findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (RuleTemplate t : loadAll()) {
            if (id.equals(t.getId())) {
                return t;
            }
        }
        return null;
    }

    /**
     * 从 classpath 读取 rule-templates.json（IDE 运行与打包 jar 均可用 getResourceAsStream）
     */
    private static List<RuleTemplate> loadFromClasspath() {
        try (InputStream is = RuleTemplateLibrary.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                LoggerUtil.logWarn("[模板库] 未找到内置模板资源 " + RESOURCE + "，模板库为空");
                return Collections.emptyList();
            }
            return parse(is.readAllBytes());
        } catch (Exception e) {
            LoggerUtil.logWarn("[模板库] 读取内置模板失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从指定路径加载模板（包内可见，供测试注入坏文件/临时文件验证容错；
     * 参照 ConfigLoader.saveThemeTo(Path,...) 的包内钩子先例）
     */
    static List<RuleTemplate> loadFrom(Path jsonPath) {
        try {
            return parse(Files.readAllBytes(jsonPath));
        } catch (IOException e) {
            LoggerUtil.logWarn("[模板库] 读取模板文件失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析模板 JSON 字节并逐条校验（共享实现）：
     * 顶层结构 {"templates": [ {id,name,category,description,rule}, ... ]}；
     * rule 字段为 MatchRule 标准 JSON 形态。单条无效 → 跳过并 logWarn，不拖垮整体。
     */
    private static List<RuleTemplate> parse(byte[] bytes) {
        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            if (json.startsWith("\uFEFF")) {
                json = json.substring(1); // 去 UTF-8 BOM，兼容带 BOM 的编辑器保存
            }
            JsonNode root = MAPPER.readTree(json);
            JsonNode templatesNode = root == null ? null : root.get("templates");
            if (templatesNode == null || !templatesNode.isArray()) {
                LoggerUtil.logWarn("[模板库] 模板文件缺少 templates 数组，模板库为空");
                return Collections.emptyList();
            }
            List<RuleTemplate> result = new ArrayList<>();
            for (JsonNode node : templatesNode) {
                try {
                    RuleTemplate t = MAPPER.treeToValue(node, RuleTemplate.class);
                    if (t == null || t.getRule() == null || t.getId() == null || t.getId().isBlank()) {
                        LoggerUtil.logWarn("[模板库] 跳过无效模板项（缺少 id 或 rule）: "
                                + (t == null || t.getId() == null ? "未命名" : t.getId()));
                        continue;
                    }
                    // 逐条校验：rule 必须能被 MatchRuleLoader.fromJson 解析（策略注册表校验），
                    // 保证模板套用后生成的规则文件可被程序正常加载执行
                    String ruleJson = MAPPER.writeValueAsString(t.getRule());
                    if (MatchRuleLoader.fromJson(ruleJson) == null) {
                        LoggerUtil.logWarn("[模板库] 跳过无效模板 " + t.getId() + "：rule 无法通过规则解析校验");
                        continue;
                    }
                    result.add(t);
                } catch (Exception e) {
                    LoggerUtil.logWarn("[模板库] 跳过无效模板项: " + e.getMessage());
                }
            }
            if (result.isEmpty()) {
                LoggerUtil.logWarn("[模板库] 没有可用的有效模板");
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            LoggerUtil.logWarn("[模板库] 模板文件解析失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
