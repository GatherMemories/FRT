package com.awei.frt.core.builder;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.model.Config;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;
import com.awei.frt.model.RestoreResult;
import com.awei.frt.ui.ConsoleUserPrompter;
import com.awei.frt.ui.UserPrompter;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Author: mou_ren
 * @Date: 2026/1/24 22:17
 * @Description: 备份文件加载器
 */
public class BackupFileLoader {
    // 加载的备份文件列表
    private static Map<String, Path> backupFiles = new HashMap<>();
    // 加载的操作记录集文件列表
    private static Map<String, ProcessingResult> operationRecordFiles = new HashMap<>();
    // 未完成会话的临时记录文件名（操作过程中实时写入，异常中断后用于恢复）
    private static final String SESSION_RECORD_FILE = "session-current.json";

    /**
     * 备份体系共享 JSON 序列化器（线程安全可复用）：
     * - JSR310 时间支持 + 禁用时间戳
     * - 自定义 Path 序列化/反序列化：
     *   序列化输出普通路径字符串（Jackson 默认 NioPathSerializer 会输出 file:/... URI 编码格式，
     *   中文/特殊字符被 % 编码，恢复详情显示为乱码路径——用户实测反馈）
     *   反序列化直接 Paths.get(字符串)，兼容 Windows 历史记录里的反斜杠路径
     *   （Jackson 默认 NioPathDeserializer 按 URI 解析，遇到 "C:\Users\..." 会抛 Bad escape，
     *   导致 Windows 上生成的旧备份记录在恢复菜单中全部加载失败）；
     *   旧备份记录里存过 file:/... URI 编码路径，以 "file:" 开头时按 URI 解析以正确解码
     */
    private static final ObjectMapper BACKUP_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new SimpleModule()
                    .addSerializer(Path.class, new JsonSerializer<Path>() {
                        @Override
                        public void serialize(Path value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                            gen.writeString(value == null ? null : value.toString());
                        }
                    })
                    .addDeserializer(Path.class, new JsonDeserializer<Path>() {
                        @Override
                        public Path deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                            String pathString = p.getValueAsString();
                            if (pathString == null || pathString.isEmpty()) {
                                return null;
                            }
                            try {
                                // 兼容旧记录：历史上 Path 被序列化成 file:/... URI 编码格式
                                if (pathString.startsWith("file:")) {
                                    return java.nio.file.Paths.get(java.net.URI.create(pathString));
                                }
                                return java.nio.file.Paths.get(pathString);
                            } catch (Exception e) {
                                // 非法路径字符串不崩反序列化，恢复时按"文件不存在"处理
                                return null;
                            }
                        }
                    }));
    // 会话记录共享 JSON 序列化器（同 BACKUP_MAPPER，别名语义更清晰）
    private static final ObjectMapper SESSION_MAPPER = BACKUP_MAPPER;

    /**
     * 获取操作记录集文件列表（每次都重新扫描 record 目录，保证最新）：
     * 更新/删除操作产生新备份后，备份功能列表应能立即看到；
     * 原实现缓存非空后不再刷新，用户实测新备份在恢复菜单中查找不到。
     */
    public static Map<String, ProcessingResult> getOperationRecordFiles() {
        return loadOperationRecordsFiles();
    }


    // 获取备份文件列表
    public static Map<String, Path> getBackupFiles() {
        // 判空条件：检查 backupFiles 本身（原实现误检查了 operationRecordFiles）
        if (backupFiles == null || backupFiles.isEmpty()) {
            Config config = ConfigLoader.getConfig();
            if (config == null) {
                return null;
            }
            Path backupPath = ConfigLoader.getBackupPath();
            if (!Files.exists(backupPath)) {
                try {
                    Files.createDirectories(backupPath);
                } catch (IOException e) {
                    LoggerUtil.logException("创建备份目录失败", e);
                    return backupFiles;
                }
            }
            // 加载失败（返回 null）时保留原值，避免 backupFiles 被置 null 导致后续 NPE
            Map<String, Path> loaded = loadBackupFiles(backupPath);
            if (loaded != null) {
                backupFiles = loaded;
            }
        }
        return backupFiles;
    }

    /**
     * 加载备份文件列表
     * @param backupPath 备份目录路径
     */
    public static Map<String, Path>loadBackupFiles(Path backupPath) {
        if (Files.exists(backupPath)) {
            // 清空旧数据，避免重复加载
            backupFiles.clear();
            // 备份记录/会话文件所在的 record 子目录：这些是操作记录 JSON，不是被备份的文件，
            // 不应算进备份文件索引（否则会污染 MD5 索引并可能被误删/误恢复）
            Path recordDir = backupPath.resolve("record").normalize();
            try (Stream<Path> paths = Files.walk(backupPath)) {
                paths.filter(Files::isRegularFile) // 只保留文件
                        .filter(filePath -> !filePath.startsWith(recordDir)) // 排除记录目录
                        .forEach(filePath -> {
                            if (backupFiles == null) {
                                backupFiles = new HashMap<>();
                            }
                            String fileMd5 = FileSignUtil.getFileMd5(filePath); // 获取文件的MD5特征码
                            backupFiles.put(fileMd5, filePath);
                        });
            } catch (IOException e) {
                LoggerUtil.logException("加载备份文件列表失败", e);
                return null;
            }
        }
        return backupFiles;
    }

    /**
     * 增加备份文件
     * @param filePath 文件路径
     * @return 是否成功
     */
    public static boolean addBackupFile(Path filePath) {
        try {
            if (!Files.isRegularFile(filePath)) {
                LoggerUtil.logErrorMsg("备份文件失败: 不是有效文件");
                return false;
            }
            // 检查文件是否已存在于备份文件列表中（存在更改为新路径）
            String fileMd5 = FileSignUtil.getFileMd5(filePath);
            Path backupFilePath = getBackupFilePath(filePath, fileMd5);
            // 同内容已备份且备份文件仍在磁盘上：只更新索引，不重复拷贝（MD5 去重合并）。
            // 恢复时按 MD5 找到备份、拷贝到记录的目标路径（目标原名），备份文件名不影响恢复，
            // 因此不同文件名的同内容文件（如 config.txt 与 config (副本).md）共用一个备份即可。
            Path existing = backupFiles.get(fileMd5);
            if (existing != null && Files.isRegularFile(existing)) {
                backupFiles.put(fileMd5, backupFilePath);
                return true;
            }
            // 索引缺失或磁盘备份已丢失（被清理/误删等）：重新拷贝重建——
            // 若只看索引就跳过拷贝，备份文件丢失后后续更新永远不再落盘，恢复时会提示找不到备份
            // 备份文件（按相对路径镜像存储，避免不同目录下同名文件互相覆盖）
            Path parentDir = backupFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.copy(filePath, backupFilePath, StandardCopyOption.REPLACE_EXISTING);
            backupFiles.put(fileMd5, backupFilePath);
            return true;
        } catch (IOException e) {
            LoggerUtil.logException("备份文件失败", e);
            return false;
        }
    }

    /**
     * 计算备份文件路径：平铺存储（直接以原文件名放在 backup/ 下，便于备份目录一眼看清所有备份）。
     * 同名但内容不同（MD5 不同）的文件加短哈希后缀区分，避免互相覆盖；
     * 内容相同（MD5 相同）的文件由 addBackupFile 按 MD5 去重自动合并为一份。
     * （恢复/清理均按 MD5 索引查找，命名不影响；旧版层级备份仍在 backup/ 下会被递归扫描到，自动兼容）
     * @param filePath 原始文件路径
     * @param fileMd5 文件内容 MD5（用于同名不同内容时区分）
     * @return 备份文件路径
     */
    private static Path getBackupFilePath(Path filePath, String fileMd5) {
        Path backupPath = ConfigLoader.getBackupPath();
        if (backupPath == null || filePath == null) {
            return backupPath != null ? backupPath.resolve("unknown") : Path.of("unknown");
        }
        String name = filePath.getFileName().toString();
        Path candidate = backupPath.resolve(name).normalize();
        // 同名但内容不同：加短哈希后缀，避免互相覆盖
        if (Files.exists(candidate) && fileMd5 != null && !fileMd5.isEmpty()) {
            String existingMd5 = FileSignUtil.getFileMd5(candidate);
            if (!fileMd5.equals(existingMd5)) {
                String shortMd5 = fileMd5.length() > 8 ? fileMd5.substring(0, 8) : fileMd5;
                candidate = backupPath.resolve(name + "-" + shortMd5).normalize();
            }
        }
        return candidate;
    }

    /**
     * 删除备份文件
     * @param filePath 文件路径
     * @return 是否成功
     */
    public static boolean deleteBackupFile(Path filePath) {
        try {
            if (Files.isRegularFile(filePath)) {
                String fileMd5 = FileSignUtil.getFileMd5(filePath);
                Path indexedPath = backupFiles.get(fileMd5);
                if (indexedPath != null) {
                    Files.delete(indexedPath);
                    backupFiles.remove(fileMd5);
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            LoggerUtil.logException("删除备份文件失败", e);
            return false;
        }
    }




    /**
     * 储存操作记录集文件
     * @param record 操作记录
     * @return 是否成功
     */
    public static boolean saveOperationRecord(ProcessingResult record) {
        try {
            // 1. 检查record是否为null
            if (record == null || record.getOperationRecords() == null || record.getOperationRecords().isEmpty()) {
                LoggerUtil.logErrorMsg("保存操作记录失败: 记录对象为空");
                return false;
            }

            // 2. 检查备份路径是否可用
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath == null) {
                LoggerUtil.logErrorMsg("保存操作记录失败: 备份路径为空");
                return false;
            }

            // 3. 确保备份目录存在，不存在则创建
            backupPath = backupPath.resolve("record").normalize(); // 在备份目录下创建record子目录 (用来存放操作记录集文件)
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
            }

            // 4. 验证备份路径确实是目录
            if (!Files.isDirectory(backupPath)) {
                LoggerUtil.logErrorMsg("保存操作记录失败: 备份路径不是目录");
                return false;
            }

            // 5. 生成友好的备份文件名（backup-20260131-143045.json格式）
            String fileName = generateFriendlyFileName(record.getResultTime());

            // 6. 构建文件路径并规范化
            Path recordFilePath = backupPath.resolve(fileName + ".json").normalize();

            // 7. 验证文件路径在备份目录内（防止路径遍历攻击）
            if (!recordFilePath.startsWith(backupPath.normalize())) {
                LoggerUtil.logErrorMsg("保存操作记录失败: 文件路径非法");
                return false;
            }

            // 8. 检查父目录是否可写
            Path parentDir = recordFilePath.getParent();
            if (parentDir == null || !Files.isWritable(parentDir)) {
                LoggerUtil.logErrorMsg("保存操作记录失败: 父目录不可写");
                return false;
            }

            // 9. 使用临时文件进行原子性写入
            Path tempFilePath = recordFilePath.resolveSibling(fileName + ".json.tmp");
            try {
                // 9.1 先写入临时文件
                BACKUP_MAPPER.writeValue(tempFilePath.toFile(), record);

                // 9.2 写入成功后，原子性地重命名为目标文件
                Files.move(tempFilePath, recordFilePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);

                return true;
            } catch (Exception e) {
                // 9.3 发生异常，删除临时文件，确保不留下不完整文件
                try {
                    if (Files.exists(tempFilePath)) {
                        Files.deleteIfExists(tempFilePath);
                    }
                } catch (IOException deleteEx) {
                    LoggerUtil.logException("删除临时文件失败", deleteEx);
                }
                LoggerUtil.logException("保存操作记录失败", e);
                return false;
            }


        } catch (IOException e) {
            LoggerUtil.logException("保存操作记录失败", e);
            return false;
        } catch (Exception e) {
            LoggerUtil.logException("保存操作记录失败: 未知错误", e);
            return false;
        }
    }

    /**
     * 增量追加一条会话操作记录（每次操作后调用，P3 优化）
     * 写入临时文件 session-current.json（JSON Lines 格式：一行一条 OperationRecord），
     * 供异常中断后恢复；相比旧版"每次全量重写整个 ProcessingResult"，操作多时磁盘压力大幅下降，
     * 且崩溃时最多只丢失当前这一条正在写的记录。
     * @param record 刚完成的操作记录
     * @return 是否成功
     */
    public static boolean appendSessionRecord(OperationRecord record) {
        if (record == null) {
            return false;
        }
        try {
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath == null) {
                return false;
            }
            Path recordPath = backupPath.resolve("record").normalize();
            if (!Files.exists(recordPath)) {
                Files.createDirectories(recordPath);
            }
            Path sessionFile = recordPath.resolve(SESSION_RECORD_FILE).normalize();
            String line = SESSION_MAPPER.writeValueAsString(record) + System.lineSeparator();
            Files.writeString(sessionFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (Exception e) {
            LoggerUtil.logErrorMsg("实时保存操作记录失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 是否存在未完成的操作会话（上次操作异常中断遗留）
     * @return 是否存在
     */
    public static boolean hasSessionRecord() {
        Path sessionFile = getSessionRecordPath();
        return sessionFile != null && Files.exists(sessionFile);
    }

    /**
     * 加载未完成的操作会话记录（兼容两种格式）：
     * - 新格式（JSON Lines）：每行一条 OperationRecord，逐行读取组装 ProcessingResult
     * - 旧格式（整文件一个 ProcessingResult JSON）：含 "operationRecords" 字段时按整文件解析
     * @return 操作记录对象，不存在或加载失败返回null
     */
    public static ProcessingResult loadSessionRecord() {
        if (!hasSessionRecord()) {
            return null;
        }
        Path sessionFile = getSessionRecordPath();
        try {
            String content = Files.readString(sessionFile, StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                return null;
            }
            // 旧格式探测：整文件是 ProcessingResult（含 operationRecords 数组字段）
            if (content.contains("\"operationRecords\"")) {
                return SESSION_MAPPER.readValue(content, ProcessingResult.class);
            }
            // 新格式：逐行解析 OperationRecord
            ProcessingResult result = new ProcessingResult();
            String[] lines = content.split("\r?\n");
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                OperationRecord record = SESSION_MAPPER.readValue(line, OperationRecord.class);
                result.addOperationRecord(record);
            }
            return result;
        } catch (Exception e) {
            LoggerUtil.logException("加载会话记录失败: " + sessionFile, e);
            return null;
        }
    }

    /**
     * 清除会话记录（操作正常完成并正式保存记录后调用）
     */
    public static void clearSessionRecord() {
        Path sessionFile = getSessionRecordPath();
        if (sessionFile != null) {
            try {
                Files.deleteIfExists(sessionFile);
            } catch (IOException e) {
                LoggerUtil.logErrorMsg("清除会话记录失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取会话记录文件路径
     */
    private static Path getSessionRecordPath() {
        Path backupPath = ConfigLoader.getBackupPath();
        if (backupPath == null) {
            return null;
        }
        return backupPath.resolve("record").resolve(SESSION_RECORD_FILE).normalize();
    }

    /**
     * 生成友好的备份文件名（backup-20260131-143045.json格式）
     * @param resultTime 处理结果时间
     * @return 格式化的文件名（不含扩展名）
     */
    private static String generateFriendlyFileName(LocalDateTime resultTime) {
        LocalDateTime time = (resultTime != null) ? resultTime : LocalDateTime.now();
        String timestamp = time.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return "backup-" + timestamp;
    }

    /**
     * 从文件加载操作记录
     * @param fileName 文件名（不含扩展名）
     * @return 操作记录对象，加载失败返回null
     */
    public static ProcessingResult loadOperationRecord(String fileName) {
        try {
            // 1. 参数校验
            if (fileName == null || fileName.trim().isEmpty()) {
                LoggerUtil.logErrorMsg("加载操作记录失败: 文件名为空");
                return null;
            }

            // 2. 检查备份路径是否可用
            Path backupRecordPath = ConfigLoader.getBackupPath().resolve("record").normalize();
            if (backupRecordPath == null) {
                LoggerUtil.logErrorMsg("加载操作记录失败: 备份路径为空");
                return null;
            }

            // 3. 清理文件名
            String safeFileName = fileName.trim();
            Path recordFilePath = backupRecordPath.resolve(safeFileName).normalize();

            // 4. 验证文件路径在备份目录内
            if (!recordFilePath.startsWith(backupRecordPath.normalize())) {
                LoggerUtil.logErrorMsg("加载操作记录失败: 文件路径非法");
                return null;
            }

            // 5. 检查文件是否存在
            if (!Files.exists(recordFilePath)) {
                LoggerUtil.logErrorMsg("加载操作记录失败: 文件不存在 - " + safeFileName);
                return null;
            }

            // 6. 检查是否为常规文件
            if (!Files.isRegularFile(recordFilePath)) {
                LoggerUtil.logErrorMsg("加载操作记录失败: 不是常规文件 - " + safeFileName);
                return null;
            }

            // 7. 反序列化（BACKUP_MAPPER：JSR310 + 兼容 Windows 路径的自定义 Path 反序列化）
            return BACKUP_MAPPER.readValue(recordFilePath.toFile(), ProcessingResult.class);

        } catch (IOException e) {
            LoggerUtil.logException("加载操作记录失败", e);
            return null;
        } catch (Exception e) {
            LoggerUtil.logException("加载操作记录失败: 未知错误", e);
            return null;
        }
    }

    /**
     * 加载所有操作记录集文件
     * @return 操作记录映射表，key为文件名（不含扩展名），value为操作记录对象
     */
    public static Map<String, ProcessingResult> loadOperationRecordsFiles() {
        Map<String, ProcessingResult> results = new HashMap<>();

        try {
            // 1. 检查备份路径是否可用
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath == null) {
                LoggerUtil.logErrorMsg("加载操作记录集失败: 备份路径为空");
                return results;
            }

            // 2. 构建 record 子目录路径
            Path recordPath = backupPath.resolve("record").normalize();

            // 3. 检查目录是否存在
            if (!Files.exists(recordPath)) {
                LoggerUtil.logErrorMsg("加载操作记录集失败: 记录目录不存在 - " + recordPath);
                return results;
            }

            // 4. 检查是否为目录
            if (!Files.isDirectory(recordPath)) {
                LoggerUtil.logErrorMsg("加载操作记录集失败: 路径不是目录 - " + recordPath);
                return results;
            }

            // 5. 遍历目录下的所有 .json 文件
            try (Stream<Path> fileStream = Files.list(recordPath)) {
                List<Path> jsonFiles = fileStream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .filter(path -> !path.toString().endsWith(".json.tmp")) // 排除临时文件
                        .filter(path -> !path.getFileName().toString().equals(SESSION_RECORD_FILE)) // 排除会话临时文件
                        .toList();

                // 6. 加载每个文件
                for (Path filePath : jsonFiles) {
                    // 提取文件名（不含扩展名）
                    String fileName = filePath.getFileName().toString();

                    // 加载单个操作记录
                    ProcessingResult record = loadOperationRecord(fileName);

                    if (record != null) {
                        results.put(fileName, record);
                    } else {
                        LoggerUtil.logErrorMsg("加载操作记录集失败: 无法加载文件 - " + fileName);
                    }
                }
            }

            // 6.5 按时间排序（降序：最新的在前）
            results = results.entrySet().stream()
                    .sorted(Map.Entry.<String, ProcessingResult>comparingByValue(
                            Comparator.comparing(ProcessingResult::getResultTime).reversed()
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));


            // 7. 更新静态变量
            operationRecordFiles = results;

            return results;

        } catch (IOException e) {
            LoggerUtil.logException("加载操作记录集失败", e);
            return results;
        } catch (Exception e) {
            LoggerUtil.logException("加载操作记录集失败: 未知错误", e);
            return results;
        }
    }


    /**
     * 完成一次操作会话（服务层共用流程，消除 FileUpdateServiceNew/FileDeleteService 的重复代码）：
     * 1. 正式保存操作记录到 backup/record/
     * 2. 保存成功后清除实时会话记录（session-current.json）
     * 3. 若本次操作存在失败项，询问用户是否执行恢复操作
     *
     * @param processingResult 处理结果
     * @param scanner          用户输入
     * @return 正式备份是否保存成功
     */
    public static boolean finishOperationSession(ProcessingResult processingResult, Scanner scanner) {
        return finishOperationSession(processingResult, new ConsoleUserPrompter(scanner));
    }

    public static boolean finishOperationSession(ProcessingResult processingResult, UserPrompter prompter) {
        if (processingResult == null || processingResult.getSuccessCount() <= 0) {
            return false;
        }
        LoggerUtil.logInfo("[执行] 正在备份操作文件...");
        boolean backupSuccess = saveOperationRecord(processingResult);
        if (backupSuccess) {
            // 正式保存成功，清除实时会话记录
            clearSessionRecord();
            LoggerUtil.logInfo("[成功] 备份操作文件成功！");

            // 备份数量淘汰：超过上限自动删除最旧（固定 pinned 的记录除外）
            Config cfg = ConfigLoader.getConfig();
            trimBackupRecords(cfg != null ? cfg.getMaxBackupRecords() : 20);

            if (processingResult.getErrorCount() > 0) {
                LoggerUtil.logWarn("[警告] 检测到 " + processingResult.getErrorCount() + " 个文件处理失败");
                System.out.println("是否要执行恢复操作，将系统恢复到操作前的状态？(y/n)");

                String choice = prompter.readLine().toLowerCase();
                if (choice.equals("y") || choice.equals("yes")) {
                    LoggerUtil.logInfo("[执行] 开始执行恢复操作...");
                    RestoreResult restoreResult = restoreFromResult(processingResult, prompter);

                    // 打印恢复结果
                    LoggerUtil.logInfo("[STATS] 恢复结果统计: 成功 " + restoreResult.getSuccessCount()
                            + ", 失败 " + restoreResult.getFailureCount()
                            + ", 回滚 " + restoreResult.getRollbackCount());

                    if (restoreResult.isFullSuccess()) {
                        LoggerUtil.logInfo("[成功] 系统已成功恢复到操作前的状态");
                    } else if (restoreResult.getRollbackCount() > 0) {
                        LoggerUtil.logWarn("[警告] 系统已回滚，但可能处于部分恢复状态");
                    } else {
                        LoggerUtil.logError("[失败] 系统恢复失败，可能处于不一致状态");
                    }
                } else {
                    LoggerUtil.logInfo("[信息] 用户取消恢复操作");
                }
            }
        } else {
            LoggerUtil.logError("[失败] 备份操作文件失败！");
        }
        return backupSuccess;
    }

    /**
     * 根据 ProcessingResult 对象，进行文件恢复操作
     * @param result 处理结果对象
     * @param scanner 用于用户交互的 Scanner
     * @return 恢复结果
     */
    public static RestoreResult restoreFromResult(ProcessingResult result, Scanner scanner) {
        return restoreFromResult(result, new ConsoleUserPrompter(scanner));
    }

    public static RestoreResult restoreFromResult(ProcessingResult result, UserPrompter prompter) {
        RestoreResult restoreResult = new RestoreResult();

        try {
            // 1. 参数校验
            if (result == null) {
                LoggerUtil.logErrorMsg("恢复操作失败: 处理结果为空");
                restoreResult.incrementFailure("处理结果为空");
                return restoreResult;
            }

            List<OperationRecord> records = result.getOperationRecords();
            if (records == null || records.isEmpty()) {
                LoggerUtil.logErrorMsg("恢复操作失败: 操作记录列表为空");
                restoreResult.incrementFailure("操作记录列表为空");
                return restoreResult;
            }

            // 2. 不预检"备份文件列表为空"（纯新增的备份——如第一次初始化场景——
            //    恢复只需删除目标文件，不需要旧备份文件；REPLACE/DELETE 需要旧文件时
            //    由 restoreSingleRecord 按需查找，找不到才单条失败并可回滚）

            // 3. 记录已恢复的操作，用于回滚
            List<OperationRecord> restoredRecords = new ArrayList<>();

            // 4. 倒序遍历操作记录（后进先出）
            for (int i = records.size() - 1; i >= 0; i--) {
                OperationRecord record = records.get(i);

                // 只恢复成功的操作
                if (!record.isSuccess()) {
                    LoggerUtil.logInfo("[跳过] 跳过失败的操作: " + record.getOperationType() + " - " + record.getTargetPath());
                    continue;
                }

                LoggerUtil.logInfo("[执行] 恢复操作: " + record.getOperationType() + " - " + record.getTargetPath());

                // 恢复单个记录
                boolean success = restoreSingleRecord(record, restoreResult, prompter);

                if (success) {
                    restoredRecords.add(record);
                } else {
                    // 恢复失败，询问用户是否回滚
                    LoggerUtil.logError("[失败] 恢复失败: " + record.getTargetPath());
                    System.out.println("\n恢复过程中遇到失败，是否要回滚已恢复的操作？(y/n)");

                    String choice = prompter.readLine().toLowerCase();
                    if (choice.equals("y") || choice.equals("yes")) {
                        LoggerUtil.logInfo("[执行] 开始回滚已恢复的操作...");
                        rollbackRestoredOperations(restoredRecords, restoreResult);
                    }
                    return restoreResult;
                }
            }

            LoggerUtil.logInfo("[成功] 文件恢复完成！");
            LoggerUtil.logInfo("[STATS] 成功: " + restoreResult.getSuccessCount() + ", 失败: " + restoreResult.getFailureCount());

        } catch (Exception e) {
            LoggerUtil.logException("恢复操作失败", e);
            restoreResult.incrementFailure(e.getMessage());
        }

        return restoreResult;
    }

    /**
     * 恢复单个操作记录
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    /**
     * 恢复时目标内容与备份记录不一致：询问用户是否跳过该文件。
     * @param prompter 交互对象（null 时默认继续执行恢复动作）
     * @param reason 不一致原因描述
     * @param proceedAction 用户选择"不跳过"时的动作描述（如"仍删除"/"仍覆盖"）
     * @return true=跳过（保留当前内容），false=继续执行恢复动作
     */
    private static boolean askSkipOrProceed(UserPrompter prompter, String reason, String proceedAction) {
        System.out.println("[提示] " + reason);
        System.out.print("       是否跳过该文件（保留当前内容）？(y=跳过, 回车=" + proceedAction + "): ");
        if (prompter == null) {
            return false;
        }
        String choice = prompter.readLine().toLowerCase();
        return choice.equals("y") || choice.equals("yes");
    }

    /**
     * 在目录中查找 MD5 与指定签名匹配的文件（排除 excludePath）。
     * 用于恢复时检测"目标文件被改名"（内容相同但文件名不同）。
     * @return 匹配的文件路径，未找到返回 null
     */
    private static Path findFileWithMd5InDir(Path dir, String md5, Path excludePath) {
        if (dir == null || md5 == null || !Files.isDirectory(dir)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> !p.equals(excludePath))
                    .filter(p -> md5.equals(FileSignUtil.getFileMd5(p)))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            LoggerUtil.logException("扫描目录查找同名内容文件失败: " + dir, e);
            return null;
        }
    }

    private static boolean restoreSingleRecord(OperationRecord record, RestoreResult restoreResult, UserPrompter prompter) {
        try {
            String operationType = record.getOperationType();

            switch (operationType) {
                case OperationContext.OPERATION_ADD:
                    return restoreAddOperation(record, restoreResult, prompter);
                case OperationContext.OPERATION_REPLACE:
                    return restoreReplaceOperation(record, restoreResult, prompter);
                case OperationContext.OPERATION_DELETE:
                    return restoreDeleteOperation(record, restoreResult, prompter);
                default:
                    LoggerUtil.logErrorMsg("未知操作类型: " + operationType);
                    restoreResult.incrementFailure("未知操作类型: " + operationType);
                    return false;
            }
        } catch (Exception e) {
            LoggerUtil.logException("恢复单个记录失败", e);
            restoreResult.incrementFailure("恢复失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复 ADD 操作（删除新添加的文件）
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    private static boolean restoreAddOperation(OperationRecord record, RestoreResult restoreResult, UserPrompter prompter) {
        try {
            Path targetPath = record.getTargetPath();

            if (targetPath == null) {
                LoggerUtil.logErrorMsg("ADD 操作恢复失败: 目标路径为空");
                restoreResult.incrementFailure("目标路径为空");
                return false;
            }

            // 检查文件是否存在
            if (!Files.exists(targetPath)) {
                // 目标按记录名不存在：可能是被改名/移动——若父目录存在 MD5 相同的文件，
                // 提示用户（y=跳过保留，回车=删除该改名文件）；确无则视为已删除，无需操作
                String addedSign = record.getSourceFileSign();
                Path renamed = (addedSign != null && targetPath.getParent() != null)
                        ? findFileWithMd5InDir(targetPath.getParent(), addedSign, targetPath)
                        : null;
                if (renamed != null) {
                    String renamedRel = targetPath.getParent().relativize(renamed).toString();
                    if (askSkipOrProceed(prompter, "目标文件不存在，但发现内容相同（MD5 匹配）的文件（可能被改名）: "
                            + renamedRel, "删除该改名文件")) {
                        LoggerUtil.logWarn("[跳过] 用户选择保留，未删除改名文件: " + renamed);
                        restoreResult.incrementFailure("用户选择跳过改名文件: " + renamedRel);
                        return true;
                    }
                    Files.delete(renamed);
                    LoggerUtil.logInfo("[成功] 已删除改名文件: " + renamed);
                    restoreResult.incrementSuccess();
                    return true;
                }
                LoggerUtil.logInfo("[信息] 文件不存在，无需删除: " + targetPath);
                restoreResult.incrementSuccess();
                return true;
            }

            // MD5 校验：确认目标还是"当时新增的文件"（未被用户修改），避免误删改动
            String addedSign = record.getSourceFileSign();
            if (addedSign != null) {
                String currentMd5 = FileSignUtil.getFileMd5(targetPath);
                if (currentMd5 != null && !addedSign.equals(currentMd5)) {
                    // 内容不一致（可能被加载改写/用户修改）：询问用户是否跳过
                    if (askSkipOrProceed(prompter, "目标文件内容与备份记录不一致（可能被修改/加载改写）: "
                            + targetPath.getFileName(), "仍删除")) {
                        LoggerUtil.logWarn("[跳过] 用户选择保留，未删除: " + targetPath);
                        restoreResult.incrementFailure("用户选择跳过: " + targetPath.getFileName());
                        return true;
                    }
                }
            }

            // 删除文件
            Files.delete(targetPath);
            LoggerUtil.logInfo("[成功] 已删除文件: " + targetPath);
            restoreResult.incrementSuccess();
            return true;

        } catch (Exception e) {
            LoggerUtil.logException("ADD 操作恢复失败", e);
            restoreResult.incrementFailure("删除文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复 REPLACE 操作（恢复被替换的原文件）
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    private static boolean restoreReplaceOperation(OperationRecord record, RestoreResult restoreResult, UserPrompter prompter) {
        try {
            Path targetPath = record.getTargetPath();
            // 替换前目标文件的签名（即备份文件索引 key），用于查找被替换前的原文件
            String targetFileSign = record.getTargetFileSign();

            if (targetPath == null || targetFileSign == null) {
                LoggerUtil.logErrorMsg("REPLACE 操作恢复失败: 目标路径或文件签名为空");
                restoreResult.incrementFailure("目标路径或文件签名为空");
                return false;
            }

            // 通过替换前目标文件签名查找备份文件
            Path backupFile = findBackupFileBySignature(targetFileSign);
            if (backupFile == null) {
                LoggerUtil.logErrorMsg("REPLACE 操作恢复失败: 未找到备份文件 (MD5: " + targetFileSign + ")");
                restoreResult.incrementFailure("未找到备份文件");
                return false;
            }

            // 检查备份文件是否存在
            if (!Files.exists(backupFile)) {
                LoggerUtil.logErrorMsg("REPLACE 操作恢复失败: 备份文件不存在: " + backupFile);
                restoreResult.incrementFailure("备份文件不存在");
                return false;
            }

            // MD5 校验：确认目标还是"替换后的新内容"（未被用户修改），避免覆盖改动
            String replacedSign = record.getSourceFileSign(); // REPLACE 的源 = 替换后的新内容
            if (replacedSign != null && Files.exists(targetPath)) {
                String currentMd5 = FileSignUtil.getFileMd5(targetPath);
                if (currentMd5 != null && !replacedSign.equals(currentMd5)) {
                    // 内容不一致：询问用户是否跳过
                    if (askSkipOrProceed(prompter, "目标文件内容与备份记录不一致（可能被修改/加载改写）: "
                            + targetPath.getFileName(), "仍用备份覆盖")) {
                        LoggerUtil.logWarn("[跳过] 用户选择保留，未恢复: " + targetPath);
                        restoreResult.incrementFailure("用户选择跳过: " + targetPath.getFileName());
                        return true;
                    }
                }
            }

            // 确保目标目录存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 恢复文件（复制备份文件到目标位置）
            Files.copy(backupFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.logInfo("[成功] 已恢复文件: " + targetPath);
            restoreResult.incrementSuccess();
            return true;

        } catch (Exception e) {
            LoggerUtil.logException("REPLACE 操作恢复失败", e);
            restoreResult.incrementFailure("恢复文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复 DELETE 操作（恢复被删除的文件）
     * @param record 操作记录
     * @param restoreResult 恢复结果
     * @return 是否成功
     */
    private static boolean restoreDeleteOperation(OperationRecord record, RestoreResult restoreResult, UserPrompter prompter) {
        try {
            Path targetPath = record.getTargetPath();
            String targetFileSign = record.getTargetFileSign();

            if (targetPath == null || targetFileSign == null) {
                LoggerUtil.logErrorMsg("DELETE 操作恢复失败: 目标路径或目标文件签名为空");
                restoreResult.incrementFailure("目标路径或目标文件签名为空");
                return false;
            }

            // 检查文件是否已存在
            if (Files.exists(targetPath)) {
                // MD5 校验：已存在但内容与被删文件不同（用户重建/改过）→ 跳过，不覆盖
                String currentMd5 = FileSignUtil.getFileMd5(targetPath);
                if (currentMd5 != null && targetFileSign != null && !targetFileSign.equals(currentMd5)) {
                    // 目标已存在但内容不同：询问用户是否跳过
                    if (askSkipOrProceed(prompter, "目标文件已存在且内容与备份记录不一致: "
                            + targetPath.getFileName(), "仍从备份覆盖")) {
                        LoggerUtil.logWarn("[跳过] 用户选择保留，未恢复: " + targetPath);
                        restoreResult.incrementFailure("用户选择跳过: " + targetPath.getFileName());
                        return true;
                    }
                }
                LoggerUtil.logInfo("[信息] 文件已存在，无需恢复: " + targetPath);
                restoreResult.incrementSuccess();
                return true;
            }

            // 通过 MD5 查找备份文件
            Path backupFile = findBackupFileBySignature(targetFileSign);
            if (backupFile == null) {
                LoggerUtil.logErrorMsg("DELETE 操作恢复失败: 未找到备份文件 (MD5: " + targetFileSign + ")");
                restoreResult.incrementFailure("未找到备份文件");
                return false;
            }

            // 检查备份文件是否存在
            if (!Files.exists(backupFile)) {
                LoggerUtil.logErrorMsg("DELETE 操作恢复失败: 备份文件不存在: " + backupFile);
                restoreResult.incrementFailure("备份文件不存在");
                return false;
            }

            // 确保目标目录存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 恢复文件（复制备份文件到目标位置）
            Files.copy(backupFile, targetPath);
            LoggerUtil.logInfo("[成功] 已恢复文件: " + targetPath);
            restoreResult.incrementSuccess();
            return true;

        } catch (Exception e) {
            LoggerUtil.logException("DELETE 操作恢复失败", e);
            restoreResult.incrementFailure("恢复文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 通过 MD5 签名查找备份文件
     * @param md5 MD5 签名
     * @return 备份文件路径，未找到返回 null
     */
    private static Path findBackupFileBySignature(String md5) {
        if (md5 == null || md5.isEmpty()) {
            return null;
        }

        return backupFiles.get(md5);
    }

    /**
     * 回滚已恢复的操作
     * @param restoredRecords 已恢复的操作记录列表
     * @param restoreResult 恢复结果
     */
    private static void rollbackRestoredOperations(List<OperationRecord> restoredRecords, RestoreResult restoreResult) {
        if (restoredRecords == null || restoredRecords.isEmpty()) {
            LoggerUtil.logInfo("[信息] 没有需要回滚的操作");
            return;
        }

        LoggerUtil.logInfo("[执行] 开始回滚 " + restoredRecords.size() + " 个已恢复的操作...");

        // 对已恢复的操作按正序回滚（即重新执行原来的操作）
        for (OperationRecord record : restoredRecords) {
            try {
                String operationType = record.getOperationType();
                Path targetPath = record.getTargetPath();

                if (OperationContext.OPERATION_ADD.equals(operationType)) {
                    // 回滚 ADD 操作：重新添加文件
                    Path sourcePath = record.getSourcePath();
                    if (sourcePath != null && Files.exists(sourcePath)) {
                        Path parentDir = targetPath.getParent();
                        if (parentDir != null && !Files.exists(parentDir)) {
                            Files.createDirectories(parentDir);
                        }
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        LoggerUtil.logInfo("[成功] 已回滚 ADD 操作: " + targetPath);
                        restoreResult.incrementRollback();
                    }
                } else if (OperationContext.OPERATION_REPLACE.equals(operationType)) {
                    // 回滚 REPLACE 操作：重新执行替换
                    Path sourcePath = record.getSourcePath();
                    if (sourcePath != null && Files.exists(sourcePath)) {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        LoggerUtil.logInfo("[成功] 已回滚 REPLACE 操作: " + targetPath);
                        restoreResult.incrementRollback();
                    }
                } else if (OperationContext.OPERATION_DELETE.equals(operationType)) {
                    // 回滚 DELETE 操作：重新删除文件
                    if (Files.exists(targetPath)) {
                        Files.delete(targetPath);
                        LoggerUtil.logInfo("[成功] 已回滚 DELETE 操作: " + targetPath);
                        restoreResult.incrementRollback();
                    }
                }

            } catch (Exception e) {
                LoggerUtil.logException("回滚操作失败", e);
            }
        }

        LoggerUtil.logInfo("[成功] 回滚完成！");
    }


    /**
     * 查找孤立备份文件：备份索引中存在、但没有任何操作记录引用（sourceFileSign/targetFileSign）的文件。
     * 来源：记录被删但备份残留、手工放入 backup/ 的文件、异常中断残留。
     *
     * 注意：本方法只"查找"不删除；删除前请用 cleanupOrphanBackupFiles 的"无记录保护"
     * （没有任何操作记录可参照时，所有备份都会被视为孤立，直接删除是危险的）。
     *
     * @return 孤立备份文件列表（按路径排序），无则空列表
     */
    public static List<Path> findOrphanBackupFiles() {
        return findOrphanBackupFiles(getOperationRecordFiles());
    }

    /**
     * 查找孤立备份文件（可注入操作记录集合，便于测试）
     * @param operationRecords 操作记录集合（key 不限）
     * @return 孤立备份文件列表
     */
    public static List<Path> findOrphanBackupFiles(Map<String, ProcessingResult> operationRecords) {
        Map<String, Path> files = getBackupFiles();
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        // 收集所有操作记录引用的 MD5
        Set<String> usedMd5 = new HashSet<>();
        if (operationRecords != null) {
            for (ProcessingResult result : operationRecords.values()) {
                if (result == null || result.getOperationRecords() == null) {
                    continue;
                }
                for (OperationRecord record : result.getOperationRecords()) {
                    if (record.getSourceFileSign() != null && !record.getSourceFileSign().isEmpty()) {
                        usedMd5.add(record.getSourceFileSign());
                    }
                    if (record.getTargetFileSign() != null && !record.getTargetFileSign().isEmpty()) {
                        usedMd5.add(record.getTargetFileSign());
                    }
                }
            }
        }
        List<Path> orphans = new ArrayList<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            if (!usedMd5.contains(entry.getKey())) {
                orphans.add(entry.getValue());
            }
        }
        orphans.sort(Comparator.comparing(Path::toString));
        return orphans;
    }

    /**
     * 清理孤立备份文件（交互版）：列出孤儿列表，用户确认后逐个删除
     * @param scanner 用户输入
     * @return 实际删除的文件数
     */
    public static int cleanupOrphanBackupFiles(Scanner scanner) {
        return cleanupOrphanBackupFiles(new ConsoleUserPrompter(scanner));
    }

    /**
     * 残留备份提醒：检测到较多残留备份文件时打印提示（程序启动后调用）
     * 阈值：>= 5 个时提醒；任何异常静默（不影响启动）
     */
    public static void warnOrphanBackupsIfNeeded() {
        try {
            List<Path> orphans = findOrphanBackupFiles();
            if (orphans.size() >= 5) {
                LoggerUtil.logWarn("[提示] 检测到 " + orphans.size() + " 个残留备份文件（未被任何记录引用），"
                        + "可在「清理残留备份」中清除，避免备份目录膨胀");
            }
        } catch (Exception e) {
            // 提醒失败不影响启动
        }
    }

    public static int cleanupOrphanBackupFiles(UserPrompter prompter) {
        // 安全保护：没有任何操作记录可参照时，所有备份都会被视为"孤立"，直接删除会误删恢复所需文件
        Map<String, ProcessingResult> operationRecords = getOperationRecordFiles();
        if (operationRecords == null || operationRecords.isEmpty()) {
            LoggerUtil.logWarn("[警告] 没有可参照的备份记录，为防止误删备份文件，已跳过清理");
            LoggerUtil.logWarn("[提示] 请先执行一次更新/删除操作产生备份记录，或手动处理 backup/ 目录");
            return 0;
        }
        List<Path> orphans = findOrphanBackupFiles(operationRecords);
        if (orphans.isEmpty()) {
            LoggerUtil.logInfo("[信息] 没有发现残留备份文件");
            return 0;
        }
        System.out.println("\n[列表] 残留备份文件（未被任何备份记录引用）共 " + orphans.size() + " 个:");
        System.out.println("-----------------------------------------");
        for (int i = 0; i < orphans.size(); i++) {
            System.out.printf("%d. %s%n", i + 1, orphans.get(i));
        }
        System.out.println("-----------------------------------------");
        System.out.print("确认删除这些残留备份文件吗？此操作不可逆！(y/n): ");
        String choice = prompter.readLine().toLowerCase();
        if (!choice.equals("y") && !choice.equals("yes")) {
            LoggerUtil.logInfo("[信息] 已取消清理");
            return 0;
        }
        int deleted = 0;
        for (Path orphan : orphans) {
            if (deleteBackupFile(orphan)) {
                deleted++;
            }
        }
        LoggerUtil.logInfo("[成功] 已删除残留备份文件 " + deleted + "/" + orphans.size() + " 个");
        return deleted;
    }

    /**
     * 删除备份记录文件及其相关的备份文件
     * @param fileName 备份记录文件名（不含扩展名）
     * @return 是否成功
     */
    /**
     * 更新备份记录的固定标记并持久化（写回 backup-xxx.json）。
     * @param fileName 备份记录文件名（含 .json）
     * @param pinned 是否固定（固定后不受备份数量淘汰影响）
     */
    public static boolean updatePinnedFlag(String fileName, boolean pinned) {
        try {
            ProcessingResult result = operationRecordFiles.get(fileName);
            if (result == null) {
                return false;
            }
            result.setPinned(pinned);
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath == null) {
                return false;
            }
            Path recordFile = backupPath.resolve("record").resolve(fileName).normalize();
            if (!Files.exists(recordFile)) {
                return false;
            }
            Path temp = recordFile.resolveSibling(fileName + ".tmp");
            BACKUP_MAPPER.writeValue(temp.toFile(), result);
            Files.move(temp, recordFile, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LoggerUtil.logException("更新备份固定标记失败", e);
            return false;
        }
    }

    /**
     * 备份数量淘汰：超过上限时自动删除最旧的备份记录（跳过 pinned 固定记录）。
     * 删除记录时由 deleteBackupRecord 按引用计数清理无引用的备份实体文件。
     * @param max 保留上限（<=0 表示不淘汰）
     */
    public static void trimBackupRecords(int max) {
        if (max <= 0) {
            return;
        }
        Map<String, ProcessingResult> records = getOperationRecordFiles(); // 已按时间倒序
        if (records == null || records.size() <= max) {
            return;
        }
        List<Map.Entry<String, ProcessingResult>> ordered = new ArrayList<>(records.entrySet());
        int over = records.size() - max;
        for (int i = ordered.size() - 1; i >= 0 && over > 0; i--) {
            Map.Entry<String, ProcessingResult> e = ordered.get(i);
            if (e.getValue() != null && e.getValue().isPinned()) {
                continue; // 固定记录不淘汰
            }
            LoggerUtil.logInfo("[淘汰] 备份记录超过上限 " + max + " 个，自动删除最旧: " + e.getKey());
            if (deleteBackupRecord(e.getKey())) {
                over--;
            }
        }
    }

    public static boolean deleteBackupRecord(String fileName) {
        try {
            // 1. 参数校验
            if (fileName == null || fileName.trim().isEmpty()) {
                LoggerUtil.logErrorMsg("删除备份记录失败: 文件名为空");
                return false;
            }

            // 2. 确保操作记录已加载
            getOperationRecordFiles();

            // 3. 检查备份记录是否存在
            ProcessingResult result = operationRecordFiles.get(fileName);
            if (result == null) {
                LoggerUtil.logErrorMsg("删除备份记录失败: 备份记录不存在 - " + fileName);
                return false;
            }

            // 4. 收集该备份记录引用的所有备份文件MD5
            Set<String> usedMd5List = new HashSet<>();
            List<OperationRecord> records = result.getOperationRecords();
            if (records != null) {
                for (OperationRecord record : records) {
                    // 收集 sourceFileSign 和 targetFileSign
                    if (record.getSourceFileSign() != null && !record.getSourceFileSign().isEmpty()) {
                        usedMd5List.add(record.getSourceFileSign());
                    }
                    if (record.getTargetFileSign() != null && !record.getTargetFileSign().isEmpty()) {
                        usedMd5List.add(record.getTargetFileSign());
                    }
                }
            }

            // 5. 从 operationRecordFiles 中移除该记录
            operationRecordFiles.remove(fileName);

            // 6. 检查每个MD5是否还被其他备份记录引用
            getBackupFiles();
            for (String md5 : usedMd5List) {
                boolean isUsed = false;
                for (ProcessingResult otherResult : operationRecordFiles.values()) {
                    List<OperationRecord> otherRecords = otherResult.getOperationRecords();
                    if (otherRecords != null) {
                        for (OperationRecord record : otherRecords) {
                            if (md5.equals(record.getSourceFileSign()) || md5.equals(record.getTargetFileSign())) {
                                isUsed = true;
                                break;
                            }
                        }
                        if (isUsed) {
                            break;
                        }
                    }
                }

                // 如果没有被其他记录引用，删除对应的备份文件
                if (!isUsed) {
                    Path backupFilePath = backupFiles.get(md5);
                    if (backupFilePath != null && Files.exists(backupFilePath)) {
                        boolean deleted = deleteBackupFile(backupFilePath);
                        if (deleted) {
                            LoggerUtil.logInfo("[成功] 已删除未使用的备份文件: " + backupFilePath.getFileName());
                        }
                    }
                }
            }

            // 7. 删除备份记录文件
            Path backupPath = ConfigLoader.getBackupPath();
            if (backupPath != null) {
                Path recordPath = backupPath.resolve("record").resolve(fileName).normalize();
                if (Files.exists(recordPath)) {
                    Files.delete(recordPath);
                    LoggerUtil.logInfo("[成功] 已删除备份记录文件: " + fileName);
                    return true;
                }
            }

            LoggerUtil.logErrorMsg("删除备份记录文件失败: 文件不存在");
            // 恢复删除操作
            return false;

        } catch (Exception e) {
            LoggerUtil.logException("删除备份记录失败", e);
            return false;
        }
    }


}
