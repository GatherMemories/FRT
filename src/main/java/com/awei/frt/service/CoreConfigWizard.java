package com.awei.frt.service;

import com.awei.frt.model.Config;
import com.awei.frt.interaction.ConsoleUserPrompter;
import com.awei.frt.interaction.UserPrompter;
import com.awei.frt.util.LoggerUtil;
import com.awei.frt.util.RuleInputParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * 核心配置编写向导
 * 编辑 config.json：更新目录 / 目标目录 / 删除目录 / 备份目录 / 日志级别。
 * 控制台逐步输入 与 UI 表单（ConfigFormDialog）共用 writeFromValues：
 * 预览 → 确认 → 自动创建缺失目录 → 写入 → 自校验。
 * 写入采用"合并"策略：保留原文件中本向导不管理的键（baseDirectory / logPath 等），
 * 避免覆盖用户既有配置。修改在下次启动时生效。
 */
public class CoreConfigWizard {

    private static final List<String> LOG_LEVELS = List.of("INFO", "DEBUG", "WARN", "ERROR");

    private final Config config;
    private final UserPrompter prompter;
    private final Path configFile; // null = 工作目录 config.json（外部配置，优先级最高）

    public CoreConfigWizard(Config config, Scanner scanner) {
        this(config, new ConsoleUserPrompter(scanner), null);
    }

    public CoreConfigWizard(Config config, UserPrompter prompter) {
        this(config, prompter, null);
    }

    public CoreConfigWizard(Config config, UserPrompter prompter, Path configFile) {
        this.config = config;
        this.prompter = prompter;
        this.configFile = configFile;
    }

    /**
     * 控制台逐步向导入口
     */
    public void start() {
        try {
            System.out.println("\n=========================================");
            System.out.println("[核心配置] 核心配置编写向导 (config.json)");
            System.out.println("=========================================");
            System.out.println("[说明] 设置 更新/目标/删除/备份目录 与日志级别");
            System.out.println("       目录支持相对路径（基于基准目录）或绝对路径");
            System.out.println("       回车 = 保留当前值；修改在下次启动时生效");
            System.out.println("-----------------------------------------");
            System.out.println("[信息] 当前配置:");
            System.out.println("   基准目录: " + config.getBaseDirectory());
            System.out.println("   更新目录: " + config.getUpdatePath());
            System.out.println("   目标目录: " + config.getTargetPath());
            System.out.println("   删除目录: " + config.getDeletePath());
            System.out.println("   备份目录: " + config.getBackupPath());
            System.out.println("   日志级别: " + config.getLogLevel());
            System.out.println("-----------------------------------------");

            Path updatePath = inputPath("更新目录", config.getUpdatePath());
            Path targetPath = inputPath("目标目录", config.getTargetPath());
            Path deletePath = inputPath("删除目录", config.getDeletePath());
            Path backupPath = inputPath("备份目录", config.getBackupPath());
            String logLevel = inputLogLevel(config.getLogLevel());

            writeFromValues(updatePath, targetPath, deletePath, backupPath, logLevel);
        } catch (Exception e) {
            LoggerUtil.logException("核心配置向导执行出错", e);
        }
    }

    /**
     * 保存核心配置（控制台 / UI 表单共用）。
     * 保存成功（返回 true）后会把本次实际变更的路径静默记入 config.json 同目录的
     * config-history.json（GUI 表单历史下拉的数据源；失败仅记日志，不影响保存结果）。
     * @param updatePath 更新目录（空 = 保留原值）
     * @param targetPath 目标目录
     * @param deletePath 删除目录
     * @param backupPath 备份目录
     * @param logLevel   日志级别（INFO/DEBUG/WARN/ERROR；非法值回退 INFO）
     * @return 是否成功写入（取消或失败返回 false）
     */
    public boolean writeFromValues(Path updatePath, Path targetPath, Path deletePath,
                                   Path backupPath, String logLevel) {
        Path configPath = resolveConfigFile();
        try {
            String level = normalizeLogLevel(logLevel);
            // 1. 合并写入 JSON：保留本向导不管理的键
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            if (Files.exists(configPath)) {
                String existingText = Files.readString(configPath);
                if (existingText.startsWith("\uFEFF")) {
                    existingText = existingText.substring(1);
                }
                JsonNode existing = mapper.readTree(existingText);
                if (existing != null && existing.isObject()) {
                    root = (ObjectNode) existing;
                }
            }
            // 记录"写入前"各路径字段的旧值（文件现值优先，文件缺失该键时以内存配置为基准，
            // 与表单预填/留空保留的语义对齐），供保存成功后判断哪些路径发生了实际变更并入历史
            String prevUpdate = fieldBaseline(root, PathHistoryStore.FIELD_UPDATE_PATH,
                    config != null ? config.getUpdatePath() : null);
            String prevTarget = fieldBaseline(root, PathHistoryStore.FIELD_TARGET_PATH,
                    config != null ? config.getTargetPath() : null);
            String prevDelete = fieldBaseline(root, PathHistoryStore.FIELD_DELETE_PATH,
                    config != null ? config.getDeletePath() : null);
            String prevBackup = fieldBaseline(root, PathHistoryStore.FIELD_BACKUP_PATH,
                    config != null ? config.getBackupPath() : null);
            root.put("updatePath", updatePath == null ? null : updatePath.toString());
            root.put("targetPath", targetPath == null ? null : targetPath.toString());
            root.put("deletePath", deletePath == null ? null : deletePath.toString());
            root.put("backupPath", backupPath == null ? null : backupPath.toString());
            root.put("logLevel", level);

            // 2. 缺失目录提示（确认后自动创建）
            Set<Path> missing = collectMissingDirs(updatePath, targetPath, deletePath, backupPath);
            if (!missing.isEmpty()) {
                System.out.println("[警告] 以下目录不存在，确认后会自动创建:");
                for (Path p : missing) {
                    System.out.println("        - " + p);
                }
            }

            // 3. 预览
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            System.out.println("\n[预览] 即将保存的核心配置 (config.json):");
            System.out.println("-----------------------------------------");
            System.out.println(json);
            System.out.println("-----------------------------------------");

            // 4. 确认
            System.out.print("[确认] 保存 " + configPath.getFileName() + " ? (y/n, 回车=n): ");
            if (!parseBoolean(prompter.readLine(), false)) {
                System.out.println("[取消] 未写入任何文件");
                return false;
            }

            // 5. 创建缺失目录 + 写入（临时文件 + 原子移动，避免进程崩溃/断电留下半截 config.json；
            //    FAT32 等不支持 ATOMIC_MOVE 的文件系统降级为普通 move）
            for (Path p : missing) {
                Files.createDirectories(p);
                System.out.println("[创建] 自动创建目录: " + p);
            }
            atomicWrite(configPath, json);
            System.out.println("[成功] 已保存核心配置: " + configPath);

            // 6. 自校验（仅 JSON 解析，不做目录校验——避免新配置目录尚未就绪时报错）
            String readBack = Files.readString(configPath);
            JsonNode parsed = mapper.readTree(readBack);
            if (parsed != null && parsed.has("targetPath") && parsed.has("logLevel")) {
                System.out.println("[校验] 配置解析校验通过 [OK]");
            } else {
                System.out.println("[警告] 配置解析校验失败，请检查内容");
                return false;
            }
            // 日志级别即时生效（无需重启）；目录类修改仍需重启（进程内路径静态缓存）
            LoggerUtil.applyLogLevel(level);
            System.out.println("[提示] 目录修改将在下次启动时生效；日志级别已即时生效");
            // 6b. 保存成功后，把本次实际变更的路径记入历史（config.json 同目录 config-history.json，
            //     体验数据：失败只记日志、不影响已完成的保存；GUI 表单据此提供历史下拉快速切换）
            recordPathHistory(prevUpdate, prevTarget, prevDelete, prevBackup,
                    updatePath, targetPath, deletePath, backupPath);
            return true;
        } catch (IOException e) {
            LoggerUtil.logException("保存核心配置失败", e);
            return false;
        }
    }

    /**
     * 原子写文件：先写临时文件再 move（含 ATOMIC_MOVE 降级与非原子回退），
     * 成功后清理临时文件；失败抛出由调用方处理。
     */
    private static void atomicWrite(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        Files.deleteIfExists(temp);
    }

    // ---------------- 控制台输入 ----------------

    private Path inputPath(String label, Path current) {
        System.out.print("[输入] " + label + " [当前: " + current + "] (回车=保留, 相对/绝对路径均可): ");
        String input = prompter.readLine();
        if (input == null || input.trim().isEmpty()) {
            System.out.println("  >> " + label + " = " + current);
            return current;
        }
        Path path = Paths.get(input.trim());
        System.out.println("  >> " + label + " = " + path);
        return path;
    }

    private String inputLogLevel(String current) {
        System.out.print("[输入] 日志级别 [当前: " + current + "] (INFO/DEBUG/WARN/ERROR, 回车=保留): ");
        String input = prompter.readLine();
        if (input == null || input.trim().isEmpty()) {
            System.out.println("  >> 日志级别 = " + current);
            return current;
        }
        String level = normalizeLogLevel(input);
        System.out.println("  >> 日志级别 = " + level);
        return level;
    }

    // ---------------- 工具 ----------------

    private Path resolveConfigFile() {
        return configFile != null ? configFile : Paths.get("config.json");
    }

    /**
     * 字段"变更基准"：已解析 JSON 中的现值优先（外部编辑过文件也能如实对比）；
     * 文件缺失该键（首次写入/测试空文件）时退回内存配置值——内存配置正是表单预填与
     * "留空=保留"的来源，以此兜底可避免首次空保存把默认值误记入历史。
     */
    private static String fieldBaseline(ObjectNode root, String key, Path inMemory) {
        JsonNode node = root.get(key);
        if (node != null && !node.isNull() && node.isTextual()) {
            return node.asText();
        }
        return inMemory == null ? null : inMemory.toString();
    }

    /**
     * 保存成功后把实际写入且发生变更的路径记入历史（GUI 表单与控制台共用此记录点）。
     * 仅记录非空且与变更基准不同的字段：留空=保留（值未变）不会重复入史，去重/限量
     * 由 PathHistoryStore 处理。全程静默容错——目录不可写/IO 异常只记日志，不影响本次保存。
     */
    private void recordPathHistory(String prevUpdate, String prevTarget, String prevDelete,
                                   String prevBackup, Path updatePath, Path targetPath,
                                   Path deletePath, Path backupPath) {
        try {
            PathHistoryStore history = new PathHistoryStore(
                    PathHistoryStore.historyFileFor(resolveConfigFile()));
            recordChanged(history, PathHistoryStore.FIELD_UPDATE_PATH, updatePath, prevUpdate);
            recordChanged(history, PathHistoryStore.FIELD_TARGET_PATH, targetPath, prevTarget);
            recordChanged(history, PathHistoryStore.FIELD_DELETE_PATH, deletePath, prevDelete);
            recordChanged(history, PathHistoryStore.FIELD_BACKUP_PATH, backupPath, prevBackup);
            history.saveIfDirty();
        } catch (Exception e) {
            // 防御性兜底：历史属体验数据，任何意外都不得阻断核心配置保存
            LoggerUtil.logWarn("记录核心配置路径历史失败（不影响本次保存）: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }

    /** 变更的字段才入史；null/空串（未填写语义）直接跳过 */
    private static void recordChanged(PathHistoryStore history, String field,
                                      Path written, String previous) {
        if (written == null) {
            return;
        }
        String value = written.toString();
        if (value.isEmpty() || value.equals(previous)) {
            return; // 留空保留或值未变化：不入史，避免默认值/重复值刷屏
        }
        history.record(field, value);
    }

    /** 收集不存在的目录（相对路径基于基准目录解析），确认后由向导自动创建 */
    private Set<Path> collectMissingDirs(Path... paths) {
        Set<Path> missing = new LinkedHashSet<>();
        if (paths == null) {
            return missing;
        }
        Path base = config != null ? config.getBaseDirectory() : Paths.get(".");
        for (Path p : paths) {
            if (p == null || p.toString().isEmpty()) {
                continue;
            }
            Path resolved = Config.isAbsolutePath(p) ? p : base.resolve(p).normalize();
            if (!Files.isDirectory(resolved)) {
                missing.add(resolved);
            }
        }
        return missing;
    }

    /** 规范化日志级别：大写 + 白名单，非法值回退 INFO */
    private static String normalizeLogLevel(String level) {
        if (level == null || level.trim().isEmpty()) {
            return "INFO";
        }
        String upper = level.trim().toUpperCase();
        if (LOG_LEVELS.contains(upper)) {
            return upper;
        }
        LoggerUtil.logWarn("[警告] 无效的日志级别: " + level + "，已回退为 INFO");
        return "INFO";
    }

    private static boolean parseBoolean(String input, boolean defaultValue) {
        return RuleInputParser.parseBoolean(input, defaultValue);
    }
}
