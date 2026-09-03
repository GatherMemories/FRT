package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.core.context.ProgressCallback;
import com.awei.frt.model.Config;
import com.awei.frt.service.FileDeleteService;
import com.awei.frt.service.FileUpdateServiceNew;
import com.awei.frt.interaction.UserPrompter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务层进度回调测试：
 * - 更新/删除真实执行阶段逐文件上报，processed 最终 == 文件总数
 * - 未绑定回调时不报错（null 安全）
 */
class ProgressCallbackTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreBackupPath() {
        TestSupport.restoreBackupPath();
    }

    @Test
    void updateReportsProgressPerFile() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Files.writeString(updateDir.resolve("a1.txt"), "1");
        Files.writeString(updateDir.resolve("a2.txt"), "2");
        Files.createDirectories(updateDir.resolve("sub"));
        Files.writeString(updateDir.resolve("sub/a3.txt"), "3");
        // inheritToSubfolders=true：子目录继承根规则，sub/a3.txt 也会被处理
        Files.writeString(updateDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[\"*.txt\"],\"inheritToSubfolders\":true}",
                StandardCharsets.UTF_8);
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Path deleteDir = Files.createDirectories(tempDir.resolve("delete"));

        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        config.setDeletePath(deleteDir.toAbsolutePath());

        List<int[]> events = new ArrayList<>();
        List<String> currents = new ArrayList<>();
        ProgressCallback cb = (processed, total, current) -> {
            events.add(new int[]{processed, total});
            currents.add(current == null ? "" : current);
        };

        new FileUpdateServiceNew(config, prompter("y")).updateExecute(cb);

        assertEquals(3, events.size(), "应上报 3 个文件");
        assertEquals(3, events.get(events.size() - 1)[0], "processed 最终应等于文件总数");
        assertEquals(3, events.get(events.size() - 1)[1], "total 应等于文件总数");
        assertTrue(currents.stream().allMatch(s -> !s.isEmpty()), "应带当前文件相对路径");
        // 相对路径应包含 sub/ 层级
        assertTrue(currents.stream().anyMatch(s -> s.contains("sub")), "应包含子目录文件路径");
    }

    @Test
    void deleteReportsProgressPerFile() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(targetDir.resolve("b.txt"), "existing");
        Path deleteDir = Files.createDirectories(tempDir.resolve("delete"));
        Files.writeString(deleteDir.resolve("b.txt"), "placeholder");
        Files.writeString(deleteDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[],\"inheritToSubfolders\":false}",
                StandardCharsets.UTF_8);

        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        config.setDeletePath(deleteDir.toAbsolutePath());

        List<int[]> events = new ArrayList<>();
        new FileDeleteService(config, prompter("y")).deleteExecute(
                (processed, total, current) -> events.add(new int[]{processed, total}));

        assertEquals(1, events.size(), "应上报 1 个文件");
        assertEquals(1, events.get(0)[0]);
        assertEquals(1, events.get(0)[1]);
    }

    @Test
    void noCallbackIsSafe() throws IOException {
        TestSupport.isolateBackup(tempDir);
        Path updateDir = Files.createDirectories(tempDir.resolve("update"));
        Files.writeString(updateDir.resolve("a1.txt"), "1");
        Files.writeString(updateDir.resolve("matching-rules.json"),
                "{\"strategyType\":\"FileSameName\",\"patterns\":[\"*.txt\"],\"inheritToSubfolders\":false}",
                StandardCharsets.UTF_8);
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Path deleteDir = Files.createDirectories(tempDir.resolve("delete"));

        Config config = ConfigLoader.getConfig();
        config.setUpdatePath(updateDir.toAbsolutePath());
        config.setTargetPath(targetDir.toAbsolutePath());
        config.setDeletePath(deleteDir.toAbsolutePath());

        // 不带回调（走无参版本）不应抛异常
        new FileUpdateServiceNew(config, prompter("y")).updateExecute();
        assertTrue(Files.exists(targetDir.resolve("a1.txt")), "无回调时功能应正常执行");
    }

    private UserPrompter prompter(String answer) {
        return () -> answer;
    }
}
