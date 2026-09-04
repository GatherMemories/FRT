package com.awei.frt.service;

import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心配置"路径历史"存取（GUI 表单快捷切换用，体验数据，不属于正式 config.json）
 *
 * 存储位置：config.json 同目录的 sidecar 文件 config-history.json（跟随 config.json 的
 * 解析位置，测试传临时目录 config 时历史天然隔离在临时目录，零工作区污染）。
 * 结构：{"version":1,"fields":{"updatePath":["/a","update"],"targetPath":[...],...}}
 * 每个字段独立成列：最近使用在前、同字符串去重（相对/绝对路径按字符串原样区分，
 * 不做 normalize 合并）、每字段上限 {@link #MAX_ENTRIES_PER_FIELD} 条、超出淘汰最旧。
 *
 * 历史属体验数据：文件不存在=空历史；损坏/IO 异常一律容错降级为空历史并只记日志，
 * 绝不向调用方抛错（表单打开/保存路径均不得因历史失败而中断）。
 */
public class PathHistoryStore {

    /** 每个路径字段的历史条数上限（最近优先，超出淘汰最旧） */
    public static final int MAX_ENTRIES_PER_FIELD = 10;

    /** config.json 中四个路径字段的键名（与 CoreConfigWizard 写入的键一致） */
    public static final String FIELD_UPDATE_PATH = "updatePath";
    public static final String FIELD_TARGET_PATH = "targetPath";
    public static final String FIELD_DELETE_PATH = "deletePath";
    public static final String FIELD_BACKUP_PATH = "backupPath";

    /** sidecar 文件版本号（结构演进时用于迁移/忽略旧格式） */
    private static final int VERSION = 1;
    private static final String JSON_KEY_VERSION = "version";
    private static final String JSON_KEY_FIELDS = "fields";

    private final Path historyFile;
    /** 字段 → 历史列表（最近优先）；LinkedHashMap 保证序列化顺序稳定 */
    private final Map<String, List<String>> entries;
    private boolean dirty = false;

    /**
     * @param historyFile sidecar 历史文件路径（不存在视为空历史，不创建文件）
     */
    public PathHistoryStore(Path historyFile) {
        this.historyFile = historyFile;
        this.entries = loadTolerant(historyFile);
    }

    /**
     * 由 config.json 路径推导其同名 sidecar 历史文件：
     * config.json → config-history.json；其他文件名（如测试的 my.json）同样跟随，
     * 避免同一目录多份配置时互相串历史。
     */
    public static Path historyFileFor(Path configFile) {
        if (configFile == null || configFile.getFileName() == null) {
            return configFile;
        }
        String name = configFile.getFileName().toString();
        String base = name.endsWith(".json")
                ? name.substring(0, name.length() - ".json".length())
                : name;
        return configFile.resolveSibling(base + "-history.json");
    }

    /** 默认历史文件：与 CoreConfigWizard 默认写入位置（工作目录 config.json）同侧 */
    public static Path defaultHistoryFile() {
        return historyFileFor(Paths.get("config.json"));
    }

    /**
     * 读取某字段的历史（最近优先、已去重限量）。
     * 文件不存在/损坏/字段缺失一律返回空列表，不抛错。
     */
    public List<String> history(String field) {
        List<String> list = entries.get(field);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    /**
     * 记录一条历史：同字符串去重并置顶（最近优先），超上限淘汰最旧；
     * 值已位于首位时不重复入史。仅置脏标记，真正落盘由 {@link #saveIfDirty()} 完成。
     *
     * @return 是否发生了有效的新记录/置顶（false = 空值或已在首位）
     */
    public boolean record(String field, String value) {
        if (field == null || value == null || value.isEmpty()) {
            return false;
        }
        List<String> list = entries.computeIfAbsent(field, k -> new ArrayList<>());
        if (!list.isEmpty() && list.get(0).equals(value)) {
            return false; // 已在首位（等价于最近一次就是它），不重复入史
        }
        list.remove(value); // 同字符串去重（相对/绝对路径按原样比较，不做 normalize）
        list.add(0, value);
        while (list.size() > MAX_ENTRIES_PER_FIELD) {
            list.remove(list.size() - 1); // 淘汰最旧
        }
        dirty = true;
        return true;
    }

    /** 有变更时落盘（静默容错：IO 失败只记日志，不抛出、不影响调用方） */
    public void saveIfDirty() {
        if (!dirty || historyFile == null) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put(JSON_KEY_VERSION, VERSION);
            ObjectNode fieldsNode = root.putObject(JSON_KEY_FIELDS);
            entries.forEach((field, list) -> {
                if (list != null && !list.isEmpty()) {
                    ArrayNode arr = fieldsNode.putArray(field);
                    list.forEach(arr::add);
                }
            });
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(historyFile, json, StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            LoggerUtil.logWarn("保存核心配置路径历史失败（不影响本次配置保存）: " + brief(e));
        }
    }

    /** 是否有未落盘的变更（测试断言用） */
    public boolean isDirty() {
        return dirty;
    }

    // ---------------- 容错加载 ----------------

    /** 读取 sidecar：不存在 → 空；损坏/不可读 → 空历史并只记警告日志，绝不抛出 */
    private static Map<String, List<String>> loadTolerant(Path file) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (file == null || !Files.exists(file)) {
            return result;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1); // 去 BOM（与 config.json 读取一致）
            }
            JsonNode root = new ObjectMapper().readTree(text);
            JsonNode fields = root != null ? root.get(JSON_KEY_FIELDS) : null;
            if (fields != null && fields.isObject()) {
                fields.fields().forEachRemaining(e -> {
                    List<String> list = parseFieldArray(e.getValue());
                    if (!list.isEmpty()) {
                        result.put(e.getKey(), list);
                    }
                });
            }
        } catch (Exception e) {
            // readTree/readString 解析异常或字段类型不符：一律降级空历史，不弹错
            LoggerUtil.logWarn("读取核心配置路径历史失败，本次使用空历史: " + file + "（" + brief(e) + "）");
        }
        return result;
    }

    /** 把 JSON 数组解析为合法历史项：仅收文本、去空白空串、去重、限量（防御外部篡改） */
    private static List<String> parseFieldArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return list;
        }
        for (JsonNode item : node) {
            if (item == null || !item.isTextual()) {
                continue;
            }
            String value = item.asText();
            if (value.isEmpty() || list.contains(value)) {
                continue;
            }
            list.add(value);
            if (list.size() >= MAX_ENTRIES_PER_FIELD) {
                break;
            }
        }
        return list;
    }

    /** 简短异常描述（类名 + 消息），不刷堆栈 */
    private static String brief(Exception e) {
        return e.getClass().getSimpleName()
                + (e.getMessage() != null ? ": " + e.getMessage() : "");
    }
}
