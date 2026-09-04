package com.awei.frt;

import com.awei.frt.service.PathHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心配置路径历史存取测试（纯文件逻辑，headless 可跑，@TempDir 隔离零污染）：
 * - sidecar 文件不存在 = 空历史、无记录不落盘
 * - 最近优先 / 同字符串去重置顶 / 每字段限量淘汰最旧 / 空值忽略
 * - 相对与绝对路径按字符串原样区分（不做 normalize 合并）
 * - 跨实例持久化 / 损坏文件容错降级空历史并可自愈覆盖
 * - 脏数据防御：非文本项忽略、重复项去重、超限截断
 * - sidecar 文件名推导跟随 config.json
 */
class PathHistoryStoreTest {

    @TempDir
    Path tempDir;

    private Path historyFile() {
        return tempDir.resolve("config-history.json");
    }

    @Test
    void missingFileYieldsEmptyHistoryAndNoWriteWithoutRecords() {
        PathHistoryStore store = new PathHistoryStore(historyFile());
        assertTrue(store.history(PathHistoryStore.FIELD_UPDATE_PATH).isEmpty(), "文件不存在应返回空历史");
        store.saveIfDirty(); // 无记录不落盘
        assertFalse(Files.exists(historyFile()), "无变更时 saveIfDirty 不应创建文件");
    }

    @Test
    void recordKeepsMostRecentFirstAndDeduplicatesOnTop() {
        PathHistoryStore store = new PathHistoryStore(historyFile());
        assertTrue(store.record(PathHistoryStore.FIELD_UPDATE_PATH, "a"));
        assertTrue(store.record(PathHistoryStore.FIELD_UPDATE_PATH, "b"));
        assertTrue(store.record(PathHistoryStore.FIELD_UPDATE_PATH, "c"));
        assertEquals(List.of("c", "b", "a"), store.history(PathHistoryStore.FIELD_UPDATE_PATH),
                "最近使用应排前");

        assertTrue(store.record(PathHistoryStore.FIELD_UPDATE_PATH, "a"), "重录旧值应去重置顶");
        assertEquals(List.of("a", "c", "b"), store.history(PathHistoryStore.FIELD_UPDATE_PATH));

        assertFalse(store.record(PathHistoryStore.FIELD_UPDATE_PATH, "a"), "已居首位再录应无变化");
        assertEquals(List.of("a", "c", "b"), store.history(PathHistoryStore.FIELD_UPDATE_PATH));
    }

    @Test
    void recordIgnoresBlankValuesAndKeepsDirtyFalse() {
        PathHistoryStore store = new PathHistoryStore(historyFile());
        assertFalse(store.record(PathHistoryStore.FIELD_UPDATE_PATH, null));
        assertFalse(store.record(PathHistoryStore.FIELD_UPDATE_PATH, ""));
        assertTrue(store.history(PathHistoryStore.FIELD_UPDATE_PATH).isEmpty());
        assertFalse(store.isDirty(), "空值不应置脏");
    }

    @Test
    void perFieldCapDropsOldestBeyondMax() {
        PathHistoryStore store = new PathHistoryStore(historyFile());
        int max = PathHistoryStore.MAX_ENTRIES_PER_FIELD;
        for (int i = 1; i <= max + 2; i++) {
            store.record(PathHistoryStore.FIELD_UPDATE_PATH, "p" + i);
        }
        List<String> history = store.history(PathHistoryStore.FIELD_UPDATE_PATH);
        assertEquals(max, history.size(), "超上限应截断到上限条数");
        assertEquals("p" + (max + 2), history.get(0), "最新应在首位");
        assertFalse(history.contains("p1"), "最早的 p1 应被淘汰");
        assertFalse(history.contains("p2"), "次早的 p2 应被淘汰");
    }

    @Test
    void relativeAndAbsoluteAndDotVariantsStayDistinct() {
        PathHistoryStore store = new PathHistoryStore(historyFile());
        store.record(PathHistoryStore.FIELD_UPDATE_PATH, "/abs/update");
        store.record(PathHistoryStore.FIELD_UPDATE_PATH, "abs/update");
        store.record(PathHistoryStore.FIELD_UPDATE_PATH, "./abs/update");
        List<String> history = store.history(PathHistoryStore.FIELD_UPDATE_PATH);
        assertEquals(3, history.size(), "相对/绝对/带 ./ 按字符串原样区分，不做 normalize 合并");
        assertTrue(history.contains("/abs/update"));
        assertTrue(history.contains("abs/update"));
        assertTrue(history.contains("./abs/update"));
    }

    @Test
    void recordsValueExactlyAsGivenWithoutTrimming() {
        PathHistoryStore store = new PathHistoryStore(historyFile());
        store.record(PathHistoryStore.FIELD_UPDATE_PATH, " spaced path ");
        store.record(PathHistoryStore.FIELD_UPDATE_PATH, " spaced path ");
        List<String> history = store.history(PathHistoryStore.FIELD_UPDATE_PATH);
        assertEquals(1, history.size(), "原样去重（GUI 提交前已 trim，此处保证去重口径不额外变换）");
        assertEquals(" spaced path ", history.get(0));
    }

    @Test
    void fieldsAreIsolatedPerFieldKey() {
        PathHistoryStore store = new PathHistoryStore(historyFile());
        store.record(PathHistoryStore.FIELD_UPDATE_PATH, "only-update");
        store.record(PathHistoryStore.FIELD_BACKUP_PATH, "only-backup");
        assertEquals(List.of("only-update"), store.history(PathHistoryStore.FIELD_UPDATE_PATH));
        assertTrue(store.history(PathHistoryStore.FIELD_TARGET_PATH).isEmpty());
        assertTrue(store.history(PathHistoryStore.FIELD_DELETE_PATH).isEmpty());
        assertEquals(List.of("only-backup"), store.history(PathHistoryStore.FIELD_BACKUP_PATH));
    }

    @Test
    void persistAcrossInstancesAndWritesReadableJson() throws IOException {
        PathHistoryStore first = new PathHistoryStore(historyFile());
        first.record(PathHistoryStore.FIELD_UPDATE_PATH, "C:/mods/update");
        first.record(PathHistoryStore.FIELD_TARGET_PATH, "C:/game");
        first.saveIfDirty();
        assertFalse(first.isDirty(), "落盘成功后应清除脏标记");
        assertTrue(Files.exists(historyFile()));

        String json = Files.readString(historyFile(), StandardCharsets.UTF_8);
        assertTrue(json.contains("updatePath"), "JSON 应含字段键: " + json);
        assertTrue(json.contains("C:/mods/update"), "JSON 应含历史值: " + json);

        PathHistoryStore second = new PathHistoryStore(historyFile());
        assertEquals(List.of("C:/mods/update"), second.history(PathHistoryStore.FIELD_UPDATE_PATH));
        assertEquals(List.of("C:/game"), second.history(PathHistoryStore.FIELD_TARGET_PATH));
    }

    @Test
    void corruptFileFallsBackEmptyAndSelfHealsOnNextSave() throws IOException {
        Files.writeString(historyFile(), "{ not-valid-json {{{", StandardCharsets.UTF_8);
        PathHistoryStore store = new PathHistoryStore(historyFile());
        assertTrue(store.history(PathHistoryStore.FIELD_UPDATE_PATH).isEmpty(),
                "损坏文件应容错降级为空历史，不抛错");

        store.record(PathHistoryStore.FIELD_UPDATE_PATH, "recovered");
        store.saveIfDirty();
        PathHistoryStore reload = new PathHistoryStore(historyFile());
        assertEquals(List.of("recovered"), reload.history(PathHistoryStore.FIELD_UPDATE_PATH),
                "下一次保存应自愈覆盖损坏内容");
    }

    @Test
    void loadRejectsNonTextDedupesAndCapsDirtyData() throws IOException {
        // 手工构造脏 sidecar：非文本项应忽略、重复项去重、超限截断到上限
        String items = "\"v1\", 123, null, \"v1\", \"v2\", \"v3\", \"v4\", \"v5\", \"v6\", "
                + "\"v7\", \"v8\", \"v9\", \"v10\", \"v11\"";
        Files.writeString(historyFile(),
                "{\"version\":1,\"fields\":{\"updatePath\":[" + items + "]}}",
                StandardCharsets.UTF_8);
        PathHistoryStore store = new PathHistoryStore(historyFile());
        List<String> history = store.history(PathHistoryStore.FIELD_UPDATE_PATH);
        assertEquals(PathHistoryStore.MAX_ENTRIES_PER_FIELD, history.size(), "超限应截断到上限");
        assertEquals("v1", history.get(0), "按文件顺序保留");
        assertFalse(history.contains("123"), "非文本项应忽略");
        assertEquals(1, history.stream().filter("v1"::equals).count(), "重复项应去重");
    }

    @Test
    void historyFileForDerivesSidecarNameFollowingConfigFile() {
        assertEquals(Paths.get("config-history.json"),
                PathHistoryStore.historyFileFor(Paths.get("config.json")), "默认 config.json 推导");
        assertEquals(Paths.get("config-history.json"), PathHistoryStore.defaultHistoryFile());
        assertEquals(tempDir.resolve("my-history.json"),
                PathHistoryStore.historyFileFor(tempDir.resolve("my.json")), "自定义文件名跟随");
        assertEquals(tempDir.resolve("cfg-history.json"),
                PathHistoryStore.historyFileFor(tempDir.resolve("cfg")), "无 .json 后缀同样跟随");
    }
}
